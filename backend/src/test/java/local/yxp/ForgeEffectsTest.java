package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class ForgeEffectsTest {
 final Rules rules=new Rules();final Engine engine=new Engine(rules);
 Player p(String id,int card,Map<String,Integer> buffs){return new Player(id,id,0,400,800,30,0,0,Collections.nCopies(8,card),List.of(),buffs);}
 Player deck(Player p,int...ids){return new Player(p.id(),p.name(),0,p.hp(),p.maxHp(),p.anima(),p.defense(),p.swordIntent(),Arrays.stream(ids).boxed().toList(),List.of(),p.buffs());}
 Result run(Player a,Player b,Integer...params){return engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(a),allowRepeatedSingleUseCards(b)),Arrays.asList(params)));}
 Event event(Result r,int turn,String type){return r.events().stream().filter(e->e.turn()==turn&&e.type().equals(type)).findFirst().orElseThrow();}
 State end(Result r,int turn,int who){return event(r,turn,"TURN_END").states().get(who);}
 @Test void completeCatalogHasOnlyThreeRefinementVariantsOutsideBattle(){var cards=rules.cards.stream().filter(c->c.sect()==4).toList();assertEquals(437,cards.size());assertEquals(133,cards.stream().map(Rules.Card::baseId).distinct().count());assertEquals(434,cards.stream().filter(Rules.Card::supported).count());assertTrue(cards.stream().allMatch(c->c.supported()||c.preparationSupported()));}
 @Test void allCombatGradesExecuteBothEmptyAndResourceRichSnapshots(){int count=0;for(var c:rules.cards)if(c.sect()==4&&c.supported())for(boolean active:new boolean[]{false,true}){
  var buffs=active?Map.of("physique",35,"physiqueCap",30,"force",3,"forceCap",8,"agility",2,"internalInjury",1,"vulnerability",2,"healingTotal",20,"hpLost",40):Map.<String,Integer>of("physiqueCap",30);
  int param=c.baseId()==10000065?10000001:c.baseId()==10000089?0:-1;var tape=Collections.nCopies(2048,param);
  var req=new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(p("A",c.id(),buffs)),p("B",1000005,Map.of())),tape);
  var r=assertDoesNotThrow(()->engine.simulateForValidation(req),c.id()+" active="+active);assertEquals("COMPLETED",r.status());for(var s:r.finalStates()){assertTrue(s.hp()<=s.maxHp(),c.id()+" hp "+s);assertTrue(s.defense()>=0&&s.anima()>=0,c.id()+" resource "+s);assertTrue(s.statuses().values().stream().allMatch(v->v>=0),c.id()+" statuses "+s);}count++;
 }assertEquals(868,count);}
 @Test void physiqueRaisesLifeCapAndOverflowHeals(){var r=run(p("A",10000004,Map.of("physique",10,"physiqueCap",10)),p("B",1000005,Map.of()));var s=end(r,1,0);assertEquals(801,s.maxHp());assertEquals(401,s.hp());assertEquals(11,s.statuses().get("physique"));assertEquals(1,s.statuses().get("physiqueGained"));}
 @Test void belowCapPhysiqueDoesNotHeal(){var s=end(run(p("A",10000004,Map.of("physiqueCap",20)),p("B",1000005,Map.of())),1,0);assertEquals(400,s.hp());assertEquals(801,s.maxHp());}
 @Test void forceIsCappedAndConsumedPerHit(){var r=run(p("A",10000018,Map.of("force",3)),p("B",1000005,Map.of()));var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(2,hits.size());assertEquals(2,hits.get(0).states().getFirst().statuses().get("force"));assertEquals(1,hits.get(1).states().getFirst().statuses().get("force"));}
 @Test void forceMultiplierAddsToVulnerabilityRatherThanMultiplyingIt(){var r=run(p("A",10000005,Map.of("force",3)),p("B",1000005,Map.of("vulnerability",1)));assertEquals(383,event(r,1,"ATTACK").states().get(1).hp());}
 @Test void dreamForcePassiveUsesFlatAttack(){var a=deck(p("A",1000005,Map.of("force",3)),10000005,10000084,1000005,1000005,1000005,1000005,1000005,1000005);assertEquals(387,event(run(a,p("B",1000005,Map.of())),1,"ATTACK").states().get(1).hp());}
 @Test void fistAttackPassesAcrossNonFistAndIsConsumedByNextFist(){var a=deck(p("A",1000005,Map.of("physiqueCap",100)),10000001,10000004,10000001,1000005,1000005,1000005,1000005,1000005);var r=run(a,p("B",1000005,Map.of()));assertEquals(2,end(r,3,0).statuses().get("fistAttack"));assertEquals(2,end(r,5,0).statuses().get("fistAttack"));assertTrue(event(r,5,"ATTACK").message().contains("攻击11"));}
 @Test void lianBengRetainsInheritedFistAttack(){var a=deck(p("A",1000005,Map.of()),10000035,10000001,1000005,1000005,1000005,1000005,1000005,1000005);a=new Player(a.id(),a.name(),0,a.hp(),a.maxHp(),a.anima(),0,0,a.cards(),List.of(),Map.of("fistAttack",4));var r=run(a,p("B",1000005,Map.of()));assertEquals(4,end(r,1,0).statuses().get("fistAttack"));assertTrue(event(r,3,"ATTACK").message().contains("攻击13"));assertEquals(2,end(r,3,0).statuses().get("fistAttack"));}
 @Test void physiqueDiscountCanMakeHpCostZero(){var s=end(run(p("A",10000005,Map.of("physique",10,"physiqueCap",20)),p("B",1000005,Map.of())),1,0);assertEquals(400,s.hp());assertEquals(0,s.statuses().get("hpLost"));}
 @Test void hpCostDoesNotConsumeGuard(){var s=end(run(p("A",10000001,Map.of("guard",1)),p("B",1000005,Map.of())),1,0);assertEquals(397,s.hp());assertEquals(1,s.statuses().get("guard"));}
 @Test void doubleShadowRepeatsBodyWithoutSecondCostOrSecondRecord(){var r=run(p("A",10000001,Map.of("fistRepeat",1)),p("B",1000005,Map.of()));var s=end(r,1,0);assertEquals(397,s.hp());assertEquals(1,s.statuses().get("cardsUsed"));assertEquals(1,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_REPEAT")).count());}
 @Test void missingResolvedRandomSelectionIsRejected(){var e=assertThrows(Engine.Rejected.class,()->run(p("A",10000009,Map.of()),p("B",1000005,Map.of())));assertEquals("MISSING_BATTLE_PARAMETERS",e.status);}
 @Test void invalidEndlessFistCardCannotExecuteArbitraryCard(){var e=assertThrows(Engine.Rejected.class,()->run(p("A",10000065,Map.of()),p("B",1000005,Map.of()),7000001));assertEquals("INVALID_INPUT",e.status);}
 @Test void repeatAndFullTraceHaveSameResult(){var req=new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(p("A",10000035,Map.of("fistAttack",3,"fistRepeat",1)),p("B",7000002,Map.of())));assertEquals(engine.simulate(req).finalStates(),engine.simulateForValidation(req).finalStates());}

 @Test void forceGainHistoryIncludesOverflow(){var s=end(run(p("A",10000010,Map.of("force",6)),p("B",1000005,Map.of())),1,0);assertEquals(6,s.statuses().get("force"));assertEquals(2,s.statuses().get("forceGained"));}
 @Test void dreamHighForceRaisesCapWithoutHealing(){var s=end(run(p("A",10040084,Map.of()),p("B",1000005,Map.of())),1,0);assertEquals(11,s.statuses().get("forceCap"));assertEquals(8,s.statuses().get("force"));assertEquals(400,s.hp());}
 @Test void dreamHighForgeUsesAnimaForAllThreeGains(){var s=end(run(p("A",10030072,Map.of("physiqueCap",100)),p("B",1000005,Map.of())),1,0);assertEquals(31,s.statuses().get("physique"));assertEquals(831,s.maxHp());assertEquals(32,s.defense());}
 @Test void dreamHighNeighborAttackBonusBelongsToPreviousSlot(){var a=deck(p("A",1000005,Map.of("attackReduction",4,"physiqueCap",100)),10000005,10030077,1000005,1000005,1000005,1000005,1000005,1000005);assertTrue(event(run(a,p("B",1000005,Map.of())),1,"ATTACK").message().contains("攻击8"));}
 @Test void highDreamLianBengKeepsInheritedEffects(){var s=end(run(p("A",10030082,Map.of("fistAttack",3)),p("B",1000005,Map.of())),1,0);assertEquals(3,s.statuses().get("fistAttack"));}
 @Test void highDreamFistGrowsOnEachWoundedHit(){var s=end(run(p("A",10030087,Map.of("physiqueCap",100)),p("B",1000005,Map.of())),1,0);assertEquals(4,s.statuses().get("physique"));assertEquals(1,s.statuses().get("dreamFistGrowth"));}
 @Test void highDreamDefenseDecayHealsBeforeCard(){var a=deck(p("A",1000005,Map.of()),1000005,10030083,1000005,1000005,1000005,1000005,1000005,1000005);a=new Player(a.id(),a.name(),0,400,800,0,20,0,a.cards(),List.of(),a.buffs());assertEquals(410,event(run(a,p("B",1000005,Map.of())),1,"TURN_START").states().getFirst().hp());}
 @Test void extraInjuryLethalityPreventsCardExecution(){var a=new Player("A","A",0,2,100,0,0,0,Collections.nCopies(8,1000005),List.of(),Map.of("injuryExtra",2,"internalInjury",3));var r=run(a,p("B",1000005,Map.of()));assertEquals(1,r.turns());assertEquals("B",r.winnerId());assertFalse(r.events().stream().anyMatch(e->e.type().equals("CARD_PLAY")));}
 @Test void healingAndDefenseCanTriggerHighDreamDamage(){var a=deck(p("A",1000005,Map.of("defenseDamagePermanent",50)),10000023,1000005,1000005,1000005,1000005,1000005,1000005,1000005);assertEquals(398,end(run(a,p("B",1000005,Map.of())),1,1).hp());}
 @Test void costLossBypassesDamageReduction(){var s=end(run(p("A",10000001,Map.of("dreamDiamond",2)),p("B",1000005,Map.of())),1,0);assertEquals(397,s.hp());}
 @Test void refinementAllGradesReturnPersistentAttributeDeltas(){var service=new PreparationService(rules);for(int id:new int[]{10000058,10010058,10020058}){var c=rules.byId.get(id);var r=service.resolve(new PreparationService.Input(Rules.ID,id,List.of(),List.of()));assertEquals(c.physique(),r.physiqueDelta());assertEquals(c.physique(),r.physiqueCapDelta());assertEquals(c.physique(),r.maxHpDelta());assertEquals("REFINEMENT",r.phase());}}
 @Test void refinementIsRejectedInCombat(){var e=assertThrows(Engine.Rejected.class,()->run(p("A",10000058,Map.of()),p("B",1000005,Map.of())));assertEquals("UNSUPPORTED",e.status);}
 @Test void fistLifeStealRoundsDown(){var r=run(p("A",144,Map.of()),p("B",1000005,Map.of()));assertEquals(401,end(r,1,0).hp());}
}
