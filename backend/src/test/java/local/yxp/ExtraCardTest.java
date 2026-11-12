package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;

class ExtraCardTest {
    final Rules rules=new Rules();
    Engine.Fighter fighter(int card,Map<String,Integer> buffs){
        var f=new Engine.Fighter(new Player("A","A",0,400,800,20,11,3,Collections.nCopies(8,card),List.of(),buffs));
        f.rules=rules;var d=new Engine.Fighter(new Player("B","B",0,400,800,5,0,0,Collections.nCopies(8,0),List.of(),Map.of()));d.rules=rules;f.opponent=d;d.opponent=f;return f;
    }
    @Test void starExplosionPlansBeforeConsumingOneStar(){for(int id:List.of(10,10010,20010)){var f=fighter(id,Map.of("starPower",2));var c=rules.byId.get(id);assertEquals(List.of(c.attack()+c.param(0)),ExtraCardEffects.attacks(new ArrayList<>(),f,c,List.of(c.attack())));ExtraCardEffects.before(new ArrayList<>(),f,c);assertEquals(1,f.status("starPower"));}}
    @Test void mountainCollapseRemovesCeilingHalfBeforeAttack(){var f=fighter(21,Map.of("earthActivation",1));var c=rules.byId.get(21);assertEquals(List.of(c.attack()+6*c.param(0)),ExtraCardEffects.attacks(new ArrayList<>(),f,c,List.of(c.attack())));assertEquals(5,f.def);}
    @Test void metalStrengthPreservesSharpOnlyForItsBody(){var f=fighter(18,Map.of("metalActivation",1,"sharpness",4));var c=rules.byId.get(18);assertEquals(List.of(c.attack()+4*c.param(0)),ExtraCardEffects.attacks(new ArrayList<>(),f,c,List.of(c.attack())));assertTrue(ExtraCardEffects.suppressSharp(f,c));f.afterCard=true;assertFalse(ExtraCardEffects.suppressSharp(f,c));}
    @Test void actualActivationsAreRequiredByMixedElementCard(){var f=fighter(14,Map.of("allElements",1));var c=rules.byId.get(14);ExtraCardEffects.after(new ArrayList<>(),f,f.opponent,c);assertEquals(0,f.status("guard"));for(int i=1;i<=5;i++)f.statuses.put(ElementalEffects.activationKey(i),1);ExtraCardEffects.after(new ArrayList<>(),f,f.opponent,c);assertEquals(c.param(1),f.status("guard"));}
    @Test void turnStartContinuousEffectsStack(){var f=fighter(0,Map.of("rainWaterPerTurn",2,"plateHexPerTurn",1,"heavenPlate",2,"jadeAnimaPerTurn",3,"growthAttackPerTurn",4));ExtraCardEffects.start(new ArrayList<>(),f);assertEquals(23,f.anima);assertEquals(3,f.status("hexagram"));assertEquals(2,f.status("starPower"));assertEquals(2,f.status("waterForce"));assertEquals(4,f.status("attackBonus"));}
    @Test void rainConvertsOnlyItsCappedRecoveryWhenLinked(){var f=fighter(15,Map.of("recovery",10));f.cloud=1;var c=rules.byId.get(15);ExtraCardEffects.after(new ArrayList<>(),f,f.opponent,c);assertEquals(10+c.param(0)-c.param(1),f.status("recovery"));assertEquals(c.param(1),f.status("attackBonus"));}
    @Test void dragonClawAppliesInjuryAfterEachBodyHit(){var f=fighter(76,Map.of());var c=rules.byId.get(76);ExtraCardEffects.hit(new ArrayList<>(),f,c);ExtraCardEffects.hit(new ArrayList<>(),f,c);assertEquals(2*c.param(0),f.opponent.status("externalInjury"));f.afterCard=true;ExtraCardEffects.hit(new ArrayList<>(),f,c);assertEquals(2*c.param(0),f.opponent.status("externalInjury"));}
    @Test void catSwordRequiresExplicitHandAndCapsAdditionalAttacks(){var f=fighter(9,Map.of());var c=rules.byId.get(9);assertThrows(Engine.Rejected.class,()->ExtraCardEffects.attacks(new ArrayList<>(),f,c,List.of(c.attack())));for(int n:List.of(0,1,20)){var p=f.p;var h=new Engine.Fighter(new Player(p.id(),p.name(),0,400,800,20,0,0,p.cards(),List.of(),Map.of(),Collections.nCopies(n,0)));h.rules=rules;h.opponent=f.opponent;assertEquals(c.attackCount()+Math.min(n,c.param(0)),ExtraCardEffects.attacks(new ArrayList<>(),h,c,List.of(c.attack())).size());}}
    @Test void extraCardsCompleteEndToEndWithoutMissingHandlers(){for(var c:rules.cards)if(c.effect().equals("EXTRA")&&c.baseId()!=9&&!c.requiresBattleParams()&&!c.requiresContext()){var a=fighter(c.id(),Map.of());var p=a.p;var player=new Player(p.id(),p.name(),0,p.hp(),p.maxHp(),p.anima(),p.defense(),p.swordIntent(),p.cards(),List.of(),Map.of(),List.of());var r=new Engine(rules).simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(player),a.opponent.p)));assertEquals("COMPLETED",r.status(),"card "+c.id());}}
}

