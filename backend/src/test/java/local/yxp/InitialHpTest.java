package local.yxp;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class InitialHpTest {
 @Test void manuallyEnteredMaxHpIsPreservedWithoutFillingHp(){
  var a=new Player("A","A",0,60,145,0,0,0,Collections.nCopies(8,0),List.of(),Map.of("physique",20));
  var b=new Player("B","B",0,60,60,0,0,0,Collections.nCopies(8,0),List.of(),Map.of());
  for(String stage:List.of("PRE_OPENING","POST_OPENING")){
   var r=new Engine(new Rules()).simulate(new Request(Rules.ID,"STANDARD",stage,"A",List.of(a,b),List.of()));
   var start=r.events().getFirst().states().getFirst();
   assertEquals(60,start.hp());assertEquals(145,start.maxHp());
  }
 }
}
