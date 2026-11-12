package local.yxp;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static local.yxp.Models.Player;
import static org.junit.jupiter.api.Assertions.*;

class DamagePreviewTest {
    private final Engine engine = new Engine(new Rules());

    private Player player(List<Integer> cards, int swordIntent) {
        return new Player("A", "甲", 0, 1, 60, 10, 0, swordIntent,
                cards, List.of(), Map.of(), List.of());
    }

    @Test
    void inertTargetDoesNotAttackAndItsDefeatDoesNotEndThePreview() {
        var preview = engine.previewDamage(Rules.ID, player(List.of(0, 0, 0), 120));
        assertEquals(8, preview.rounds().size());
        assertFalse(preview.selfDefeated());
        assertEquals(123, preview.rounds().getFirst().damage());
        assertEquals(144, preview.rounds().getLast().cumulative());
    }

    @Test
    void targetTurnStillResolvesInjuryFromThePlayer() {
        var preview = engine.previewDamage(Rules.ID, player(List.of(61, 0, 0), 0));
        assertEquals(2, preview.rounds().getFirst().damage());
        assertEquals(5, preview.rounds().get(1).damage());
        assertEquals(7, preview.rounds().get(1).cumulative());
    }

    @Test
    void targetSlotsCannotSupplyCardsForCopyEffects() {
        var preview = engine.previewDamage(Rules.ID, player(List.of(322, 177, 0), 0));
        assertEquals(0, preview.rounds().getFirst().damage());
        assertEquals(3, preview.rounds().get(1).damage());
    }
}
