package local.yxp;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class StarRandomTest {
 Engine.Fighter fighter(){var p=new Player("A","A",0,100,100,0,0,0,List.of(0,0,0),List.of(),Map.of());var f=new Engine.Fighter(p);f.resolution=new Engine.Resolution();f.resolution.trace=false;f.rules=new Rules();return f;}
 @Test void seededUniformRollsStayInBoundsAndVary(){var f=fighter();f.resolution.random.setSeed(42);var seen=new HashSet<Integer>();for(int i=0;i<1000;i++){int n=StarEffects.roll(new ArrayList<>(),f,3,9);assertTrue(n>=3&&n<=9);seen.add(n);}assertEquals(7,seen.size());}
 @Test void hexagramGuaranteesMaxAndProbabilitySuccess(){var f=fighter();f.statuses.put("hexagram",2);assertEquals(9,StarEffects.roll(new ArrayList<>(),f,3,9));assertTrue(StarEffects.chance(new ArrayList<>(),f,1));assertEquals(0,f.status("hexagram"));assertEquals(2,f.status("hexagramTriggered"));}
 @Test void probabilityBoundaryAndSeededFrequency(){var f=fighter();f.resolution.random.setSeed(12);assertFalse(StarEffects.chance(new ArrayList<>(),f,0));assertTrue(StarEffects.chance(new ArrayList<>(),f,100));int hits=0;for(int i=0;i<10000;i++)if(StarEffects.chance(new ArrayList<>(),f,30))hits++;assertTrue(hits>2700&&hits<3300);}
}
