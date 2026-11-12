package local.yxp;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class DeckLengthTest {
 Player player(String id,int n,int card){return new Player(id,id,0,500,500,10,0,0,Collections.nCopies(n,card),List.of(),Map.of());}
 @Test void independentLengthsCycleInBothDirectionsAndOpeningStages(){
  var engine=new Engine(new Rules());
  for(int a=3;a<=8;a++)for(int b=3;b<=8;b++)for(String stage:List.of("PRE_OPENING","POST_OPENING")){
   var r=engine.simulate(new Request(Rules.ID,"STANDARD",stage,"A",List.of(player("A",a,0),player("B",b,0)),List.of()));
   for(var e:r.events())for(int i=0;i<2;i++){
    var s=e.states().get(i);int n=i==0?a:b;
    assertEquals(n,s.runtimeCards().size());assertTrue(s.nextSlot()>=0&&s.nextSlot()<n);
   }
  }
 }
 @Test void shortDeckNeighborsAndReverseWrap(){
  var f=new Engine.Fighter(player("A",3,0));
  assertEquals(0,StarEffects.next(f,2));assertEquals(2,StarEffects.previous(f,0));
  f.statuses.put("reverseDirection",1);assertEquals(2,StarEffects.next(f,0));assertEquals(0,StarEffects.previous(f,2));
 }
 @Test void outsideRangeRejected(){
  for(int n:List.of(2,9))assertThrows(Engine.Rejected.class,()->new Engine(new Rules()).simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(player("A",n,0),player("B",3,0)),List.of())));
 }
}
