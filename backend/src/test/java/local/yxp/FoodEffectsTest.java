package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class FoodEffectsTest {
    final Rules rules=new Rules();
    Engine.Fighter f(Map<String,Integer> buffs){var p=new Player("A","A",0,100,1000,10,0,0,Collections.nCopies(8,0),List.of(),buffs,List.of());var f=new Engine.Fighter(p);f.rules=rules;var d=new Engine.Fighter(new Player("B","B",0,1000,1000,0,0,0,Collections.nCopies(8,0),List.of(),Map.of()));d.rules=rules;f.opponent=d;d.opponent=f;return f;}
    @Test void appetiteAndIndigestionModifyEachHealWithMinimumOne(){var f=f(Map.of("appetite",3,"indigestion",7));Engine.heal(new ArrayList<>(),f,2);assertEquals(101,f.hp);Engine.heal(new ArrayList<>(),f,10);assertEquals(107,f.hp);}
    @Test void beanPasteClearsBeforeHealing(){var f=f(Map.of("internalInjury",4,"indigestion",3));var c=rules.byId.get(91);FoodEffects.after(new ArrayList<>(),f,c);assertEquals(100+c.param(0)+7*c.param(1),f.hp);assertEquals(0,f.status("indigestion"));assertEquals(0,f.status("internalInjury"));}
    @Test void fruitConvertsOneEntireNegativeGainWithoutSpendingRepel(){var f=f(Map.of("fruitConversion",1,"repel",3));var ch=new ArrayList<Change>();Engine.addStatus(ch,f,"indigestion",4);assertEquals(4,f.status("recovery"));assertEquals(0,f.status("indigestion"));assertEquals(3,f.status("repel"));Engine.addStatus(ch,f,"weakness",2);assertEquals(1,f.status("repel"));}
    @Test void grainBonusOnlyAppliesToPositiveManaAndHealsBeforeGain(){var f=f(Map.of("grainMana",2,"grainHeal",3));Engine.addAnima(new ArrayList<>(),f,1);assertEquals(13,f.anima);assertEquals(103,f.hp);Engine.addAnima(new ArrayList<>(),f,-2);assertEquals(11,f.anima);assertEquals(103,f.hp);}
    @Test void sweetRecordUpdatesAfterEffect(){var f=f(Map.of());var c=rules.byId.get(86);FoodEffects.after(new ArrayList<>(),f,c);assertEquals(100+c.param(0),f.hp);FoodEffects.history(new ArrayList<>(),f,c);FoodEffects.after(new ArrayList<>(),f,c);assertEquals(100+2*c.param(0)+c.param(1),f.hp);}
    @Test void repeatedHealsEachReceiveAppetite(){var f=f(Map.of("appetite",2));var c=rules.byId.get(108);FoodEffects.after(new ArrayList<>(),f,c);assertEquals(100+c.param(0)*(c.param(1)+2),f.hp);}
    @Test void everyFoodGradeRunsIncludingExplicitRandomCleanse(){for(var c:rules.cards)if(c.effect().equals("FOOD")){var a=f(Map.of());var p=a.p;var deck=Collections.nCopies(8,c.id());var player=new Player(p.id(),p.name(),0,p.hp(),p.maxHp(),50,0,0,deck,List.of(),Map.of(),List.of());var result=new Engine(rules).simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(player),a.opponent.p),Collections.nCopies(256,-1)));assertEquals("COMPLETED",result.status(),"food "+c.id());}}
}
