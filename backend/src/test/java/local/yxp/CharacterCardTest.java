package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static local.yxp.Models.*;
class CharacterCardTest {
 final Rules rules=new Rules();final Engine engine=new Engine(rules);
 Player p(String id,List<Integer> deck,int mana,Map<String,Integer> buffs){return new Player(id,id,0,400,800,mana,0,0,deck,List.of(),buffs);}
 Result run(int card,int mana,Map<String,Integer> buffs){return engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(p("A",Collections.nCopies(8,card),mana,buffs)),p("B",Collections.nCopies(8,1000005),0,Map.of()))));}
 State end(Result r){return r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow().states().getFirst();}
 @Test void luJianxinUsesRecordedGrowthWithoutAddingEarthforgeTwice(){
  for(var buffs:List.of(Map.<String,Integer>of(),Map.of("embryoGrowth",12))){
   var a=new Player("A","陆剑心",1000005,60,60,0,0,0,List.of(19,0,0),
           List.of(92,10093),buffs,List.of(),List.of(new SeasonChoice(7012,null,null)),12);
   var b=p("B",List.of(0,0,0),0,Map.of());
   var result=engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,b)));
   var hit=result.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).findFirst().orElseThrow();
   assertEquals(400-7-3-buffs.getOrDefault("embryoGrowth",4),hit.states().getLast().hp());
  }
 }
 @Test void braceletAllGradesGrantDefenseGuardAndChase(){for(int id:List.of(16,10016,20016)){var s=end(run(id,10,Map.of()));assertEquals(18,s.defense());assertEquals(2*rules.byId.get(id).param(0),s.statuses().get("guard"));assertEquals(1,s.statuses().get("chaseCount"));assertEquals(8,s.anima());}}
 @Test void fanDoesNotGrantStarsWithoutMana(){var s=end(run(23,0,Map.of()));assertEquals(0,s.statuses().get("starPower"));assertEquals(1,s.statuses().get("fanDiscount"));assertEquals(0,s.statuses().get("chaseCount"));}
 @Test void fanThresholdUsesResultingStarsAndDiscountStacks(){var s=end(run(20023,1,Map.of()));assertEquals(6,s.statuses().get("starPower"));assertEquals(2,s.statuses().get("fanDiscount"));assertEquals(1,s.statuses().get("chaseCount"));}
 @Test void discountOnlyAppliesAtStarSlotAndNeverGeneratesMana(){for(int mask:List.of(0,1)){var s=end(run(16,0,Map.of("fanDiscount",3,"starMask",mask)));assertEquals(1,s.anima());assertEquals(mask==0?0:1,s.statuses().get("guard"));}}
}
