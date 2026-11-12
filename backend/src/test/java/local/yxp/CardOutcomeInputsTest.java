package local.yxp;

import java.util.*;
import org.junit.jupiter.api.Test;
import static local.yxp.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class CardOutcomeInputsTest {
    @Test
    void eachSideAndSlotConsumesItsOwnResults() {
        int card = 6000014;
        var a = player("A", List.of(card, card, 0), List.of(
                new CardOutcome(0, card, Collections.nCopies(32,0)),
                new CardOutcome(1, card, Collections.nCopies(32,2))));
        var b = player("B", List.of(card, 0, 0), List.of(new CardOutcome(0, card, Collections.nCopies(32,1))));
        var engine = new Engine(new Rules());
        var result = engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,b)));
        var events = result.events().stream().filter(e -> e.type().equals("BATTLE_PARAMETER")).limit(3).toList();
        assertEquals(List.of("A","B","A"), events.stream().map(Event::actorId).toList());
        assertEquals(List.of("0","1","2"), events.stream().map(e -> e.message().substring(e.message().length()-1)).toList());
    }

    private Player player(String id, List<Integer> cards, List<CardOutcome> outcomes) {
        return new Player(id,id,0,120,120,10,0,0,cards,List.of(),Map.of(),List.of(),null,null,outcomes);
    }
}
