package local.yxp;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;

class DeckLimitAndBottleTest {
    private final Engine engine = new Engine(new Rules());

    private Player player(String id, List<Integer> cards, Integer limit, List<SeasonChoice> choices) {
        return new Player(id, id, 0, 80, 80, 20, 0, 0, cards, List.of(), Map.of(),
                List.of(17, 17, 17), choices, null, null, limit);
    }

    private Result simulate(Player a, Player b) {
        return engine.simulate(new Request(Rules.ID, "STANDARD", "PRE_OPENING", "A", List.of(a, b)));
    }

    @Test void deckLimitCountsBothTypesPerSideAndAllowsAnOverride() {
        var ordinary = player("A", List.of(2000003, 17, 0), null, List.of());
        var mixed = List.of(2000003, 17, 17);
        assertThrows(Engine.Rejected.class, () -> simulate(ordinary, player("B", mixed, null, List.of())));
        assertDoesNotThrow(() -> simulate(ordinary, player("B", mixed, 3, List.of())));
        var extraSlot = List.of(new SeasonChoice(2015, null, null));
        assertDoesNotThrow(() -> simulate(ordinary, player("B", mixed, null, extraSlot)));
        assertThrows(Engine.Rejected.class, () -> simulate(ordinary, player("B", mixed, 2, extraSlot)));
    }

    @Test void bottleActivatesSelectedElementsAndForgottenDreamUsesFirstCard() {
        var a = new Player("A", "祁忘忧", 3000006, 80, 80, 20, 0, 0, List.of(390, 0, 0),
                List.of(199, 200, 201, 202, 203), Map.of("bottleFirst", 7000018, "bottleSecond", 198),
                List.of(), List.of(new SeasonChoice(23058, null, null)), null);
        var result = simulate(a, player("B", List.of(0, 0, 0), null, List.of()));
        var opening = result.events().stream().filter(event -> event.type().equals("TALENT")
                && event.message().startsWith("五行玉瓶")).findFirst().orElseThrow();
        var state = opening.states().getFirst();
        assertEquals(1, state.statuses().get("woodActivation"));
        assertEquals(1, state.statuses().get("fireActivation"));
        assertEquals(82, state.maxHp());
        assertEquals(22, state.anima());
        assertTrue(result.events().stream().anyMatch(event -> Integer.valueOf(7000018).equals(event.cardId())));
    }
}
