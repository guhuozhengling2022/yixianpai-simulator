package local.yxp;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static local.yxp.Models.Player;
import static local.yxp.Models.Request;
import static org.junit.jupiter.api.Assertions.*;

class DeckSearchTest {
    private final Rules rules = new Rules();
    private final Engine engine = new Engine(rules);
    private final DeckSearch search = new DeckSearch(rules, engine);

    private Player player(String id, int hp, List<Integer> cards, List<Integer> hand) {
        return new Player(id, id, 0, hp, hp, 0, 0, 0, cards, List.of(), Map.of(), hand);
    }

    private DeckSearch.Report search(Player own, Player enemy, String first) {
        var request = new Request(Rules.ID, "STANDARD", "PRE_OPENING", first, List.of(own, enemy));
        search.validate(request);
        return search.search(request, () -> false, report -> {});
    }

    @Test
    void findsAWinningCardInHandWithoutChangingTheOpponentOrFirstActor() {
        var own = player("A", 3, List.of(0, 0, 0), List.of(20000));
        var enemy = player("B", 9, List.of(0, 0, 0), List.of());
        var report = search(own, enemy, "A");
        assertTrue(report.exhaustive());
        assertEquals(4, report.evaluated());
        assertEquals(0, report.baseline().wins());
        assertEquals(1, report.winners().size());
        var winner = report.winners().getFirst();
        assertEquals(List.of(20000, 0, 0), winner.cards());
        assertEquals(List.of(), winner.hand());
        assertEquals(1.0, winner.actionsOnWin(), 0.0);
        assertTrue(search(own, enemy, "B").winners().isEmpty());
        assertEquals(List.of(20000), own.hand());
        assertEquals(List.of(0, 0, 0), enemy.cards());
    }

    @Test
    void leftoverHandPreservesUnselectedCopiesAndNeverAddsEmptySlots() {
        var own = player("A", 60, List.of(10000, 0, 10000),
                List.of(20000, 4000001, 20000, 1000001));
        assertEquals(List.of(4000001, 1000001, 10000),
                DeckSearch.remainingHand(own, List.of(20000, 10000, 20000)));
        assertNull(DeckSearch.remainingHand(player("A", 60, List.of(0, 0, 0), null), List.of(0, 0, 0)));
    }

    @Test
    void repeatsTheSameRandomSamplesWhenComparingArrangements() {
        var own = player("A", 3, List.of(4000004, 0, 0), List.of());
        var enemy = player("B", 6, List.of(0, 0, 0), List.of());
        var first = search(own, enemy, "A");
        var second = search(own, enemy, "A");
        assertTrue(first.baseline().random());
        assertEquals(24, first.baseline().samples());
        assertTrue(first.baseline().wins() > 0 && first.baseline().wins() < first.baseline().samples());
        assertEquals(first.baseline(), second.baseline());
        assertEquals(first.winners(), second.winners());
    }
}
