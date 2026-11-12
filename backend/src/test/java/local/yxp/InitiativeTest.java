package local.yxp;

import java.util.*;
import org.junit.jupiter.api.Test;
import static local.yxp.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class InitiativeTest {
    private final Engine engine = new Engine(new Rules());

    private Player player(String id, int hp, int card, Map<String,Integer> buffs, SeasonChoice... season) {
        return new Player(id,id,0,hp,60,0,0,0,List.of(card,0,0),List.of(),buffs,List.of(),List.of(season),17);
    }

    private Result fight(String first, Player a, Player b) {
        return engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING",first,List.of(a,b)));
    }

    @Test
    void addsSeasonSpeedAndStillHonorsAnExplicitFirstActor() {
        var a=player("A",60,0,Map.of("experience",10));
        var b=player("B",60,0,Map.of("experience",8),new SeasonChoice(3005,null,null));
        assertEquals("B",fight("AUTO",a,b).events().getFirst().actorId());
        assertEquals("A",fight("A",a,b).events().getFirst().actorId());
    }

    @Test
    void tiedSecondPlayerCanPlayAndHealFromNegativeLife() {
        var a=player("A",60,0,Map.of("experience",10));
        var b=player("B",1,2000001,Map.of("experience",10));
        var events=fight("A",a,b).events();
        assertTrue(events.stream().anyMatch(e -> e.type().equals("DEATH_BATTLE_GRANT") && e.actorId().equals("B")));
        assertTrue(events.stream().anyMatch(e -> e.type().equals("DEATH_BATTLE") && e.actorId().equals("B")));
        var end=events.stream().filter(e -> e.type().equals("TURN_END") && e.turn()==2).findFirst().orElseThrow();
        assertTrue(end.states().getLast().hp()>0);
        assertEquals(0,end.states().getLast().statuses().getOrDefault("deathBattleActive",0));
    }
}
