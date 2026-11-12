package local.yxp;

import java.util.*;
import org.junit.jupiter.api.Test;
import static local.yxp.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class SeasonTalentEffectsTest {
    private final Rules rules = new Rules();
    private final Engine engine = new Engine(rules);

    private Player player(String id, int character, int hp, List<Integer> talents, Integer round, SeasonChoice... season) {
        return new Player(id, id, character, hp, 60, 0, 0, 0, List.of(0, 0, 0),
                talents, Map.of("destiny", 50, "experience", 10), List.of(), List.of(season), round);
    }

    private Result fight(Player a, Player b) {
        return engine.simulate(new Request(Rules.ID, "STANDARD", "PRE_OPENING", a.id(), List.of(a, b)));
    }

    private Player opponent() {
        return player("B", 0, 55, List.of(), null);
    }

    @Test
    void dragonIntimidationCanBeDisabled() {
        for (boolean disabled : List.of(false, true)) {
            var a = player("A", 0, 40, List.of(), 17, new SeasonChoice(3001, null, disabled));
            var firstTurn = fight(a, opponent()).events().stream()
                    .filter(e -> e.type().equals("TURN_START")).findFirst().orElseThrow();
            assertEquals(disabled ? 0 : 1, state(firstTurn, "B").statuses().getOrDefault("attackReduction", 0));
        }
    }

    @Test
    void twinInscriptionRepeatsTheBaseLevelWithoutChangingTheDeck() {
        var a = new Player("A", "A", 0, 60, 60, 5, 0, 0,
                List.of(1010001, 0, 0), List.of(), Map.of(), List.of(), List.of(new SeasonChoice(5009, 1, null)), 17);
        var events = fight(a, opponent()).events();
        var repeat = events.stream().filter(e -> e.turn() == 1 && e.message().equals("双生刻纹重复1级效果"))
                .findFirst().orElseThrow();
        assertEquals(1000001, repeat.cardId());
        assertEquals(1010001, state(repeat, "A").runtimeCards().getFirst());
        assertTrue(events.stream().anyMatch(e -> e.turn() == 1 && e.type().equals("ATTACK") && Objects.equals(e.cardId(), 1000001)));
    }

    @Test
    void ignoresSkippedSeasonChoiceInOlderImports() {
        var a = player("A", 0, 40, List.of(), 17, new SeasonChoice(13, null, null),
                new SeasonChoice(9006, null, null));
        assertEquals("COMPLETED", fight(a, opponent()).status());
    }

    private static State state(Event event, String player) {
        return event.states().stream().filter(s -> s.id().equals(player)).findFirst().orElseThrow();
    }

    @Test
    void yeCanKeepOutsideTalentsAndStartsWithUnderworld() {
        var a = player("A", 4000003, 50, List.of(178, 179, 180, 181, 182), null);
        var result = fight(a, opponent());
        var opening = result.events().stream().filter(e -> e.type().equals("TALENT")).toList();
        assertEquals(1, opening.size());
        var state = state(opening.getFirst(), "A");
        assertEquals(1, state.statuses().get("underworld"));
        assertEquals(53, state.hp());
        assertEquals(0, state.statuses().getOrDefault("attackReduction", 0));
        assertTrue(rules.outsideBattleTalentIds.containsAll(List.of(27, 45, 178, 180, 181, 182)));
    }

    @Test
    void selectedSeasonEffectsAndOutsideChoiceAreResolvedTogether() {
        var a = player("A", 0, 45, List.of(), 13, new SeasonChoice(3005, null, null),
                new SeasonChoice(1001, null, false), new SeasonChoice(13008, 1, null));
        var result = fight(a, opponent());
        var song = result.events().stream().filter(e -> e.message().equals("天灵之音")).findFirst().orElseThrow();
        assertEquals(2, state(song, "A").anima());
        assertEquals(2, state(song, "B").anima());
        var firstPlay = result.events().stream().filter(e -> e.actorId().equals("A") && e.turn() == 1
                && e.type().equals("SEASON_TALENT")).findFirst().orElseThrow();
        assertEquals(2, state(firstPlay, "A").statuses().get("guard"));
        var nextTurn = result.events().stream().filter(e -> e.turn() == 3 && e.type().equals("TURN_START")).findFirst().orElseThrow();
        assertEquals(0, state(nextTurn, "A").statuses().getOrDefault("guard", 0));
    }

    @Test
    void roundThresholdAndSwitchAreRespected() {
        for (int round : List.of(12, 13)) {
            var a = player("A", 0, 19, List.of(), round, new SeasonChoice(7064, null, null),
                    new SeasonChoice(1004, null, true));
            var result = fight(a, opponent());
            var start = result.events().stream().filter(e -> e.type().equals("TURN_START")).findFirst().orElseThrow();
            assertEquals(round == 13 ? 1 : 0, state(start, "A").statuses().getOrDefault("guard", 0));
            assertEquals(0, state(start, "A").statuses().getOrDefault("internalInjury", 0));
        }
    }

    @Test
    void underworldResonanceWorksForOtherCharacters() {
        var a = player("A", 0, 40, List.of(), 13, new SeasonChoice(27021, null, null),
                new SeasonChoice(7105, null, null));
        var event = fight(a, opponent()).events().stream().filter(e -> e.message().equals("天衍-入冥")).findFirst().orElseThrow();
        assertEquals(46, state(event, "A").hp());
        assertEquals(2, state(event, "A").statuses().get("underworld"));
    }

    @Test
    void knownButUnsupportedBattleEffectsAreRejected() {
        var a = player("A", 0, 40, List.of(), 13, new SeasonChoice(1010, null, null));
        var rejected = assertThrows(Engine.Rejected.class, () -> fight(a, opponent()));
        assertEquals("UNSUPPORTED", rejected.status);
        assertTrue(rejected.getMessage().contains("弱化之音"));
    }

    @Test
    void supportedCatalogEntriesRunThroughTheDuelEndpoint() {
        for (int id : SeasonTalentEffects.SUPPORTED) {
            var talent = rules.seasonById.get(id);
            assertNotNull(talent, "Missing strategy " + id);
            assertTrue(talent.battle(), talent.name());
            var a = player("A", 0, 40, List.of(), 13, new SeasonChoice(id, talent.slotRequired() ? 1 : null, null));
            assertDoesNotThrow(() -> fight(a, opponent()), talent.name());
        }
    }
}
