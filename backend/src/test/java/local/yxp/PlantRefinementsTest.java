package local.yxp;

import java.util.*;
import org.junit.jupiter.api.Test;
import static local.yxp.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class PlantRefinementsTest {
    @Test
    void appliesRefinementsAtOpeningToTheirCorrectTargets() {
        var a = new Player("A", "甲", 0, 108, 194, 0, 0, 0, List.of(0,0,0), List.of(),
                Map.of("physique",86,"plantLife",7,"plantInjury",3,"plantReduceMaxHp",9,"plantChase",5));
        var b = new Player("B", "乙", 0, 60, 60, 0, 0, 0, List.of(0,0,0), List.of(), Map.of());
        var engine = new Engine(new Rules());
        var request = new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,b));
        var result = engine.simulate(request);
        var opening = result.events().stream().filter(e -> e.type().equals("PLANT_REFINEMENT")).toList();
        assertEquals(4, opening.size());
        var states = opening.getLast().states();
        assertEquals(110, states.getFirst().hp());
        assertEquals(201, states.getFirst().maxHp());
        assertEquals(51, states.getLast().maxHp());
        assertEquals(3, states.getLast().statuses().get("internalInjury"));
        assertTrue(result.events().stream().anyMatch(e -> e.turn()==1 && e.type().equals("CHASE")));
        assertEquals(4, engine.simulate(request).events().stream().filter(e -> e.type().equals("PLANT_REFINEMENT")).count());
    }
}
