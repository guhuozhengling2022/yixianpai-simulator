package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class CareerEffectsTest {
 final Rules rules=new Rules();final Engine engine=new Engine(rules);
 Player p(String id,int card,Map<String,Integer> buffs){return new Player(id,id,0,500,600,50,0,0,Collections.nCopies(8,card),List.of(),buffs);}
 Player deck(Player p,int...ids){return new Player(p.id(),p.name(),0,p.hp(),p.maxHp(),p.anima(),p.defense(),p.swordIntent(),Arrays.stream(ids).boxed().toList(),p.talentIds(),p.buffs());}
 Result run(Player a,Player b,List<Integer> params){return engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(a),allowRepeatedSingleUseCards(b)),params));}
 Event event(Result r,int turn,String type){return r.events().stream().filter(e->e.turn()==turn&&e.type().equals(type)).findFirst().orElseThrow();}
 State end(Result r,int turn,int index){return event(r,turn,"TURN_END").states().get(index);}
 @Test void allStandaloneCareerRowsHaveADeclaredPath(){var cards=rules.cards.stream().filter(CareerEffects::isCareer).toList();assertEquals(418,cards.size());assertTrue(cards.stream().allMatch(c->c.supported()||c.preparationSupported()));}
 @Test void allCombatGradesExecuteIncludingResolvedRandomBranches(){
  int checked=0;for(var c:rules.cards)if(CareerEffects.isCareer(c)&&c.supported()){
   int param=switch(c.baseId()){case 3000001->c.param(0);case 6000004->c.defense();case 6000007,2000004,9000026->100;case 341,349,6000011->7000001;default->0;};
   var r=assertDoesNotThrow(()->run(p("A",c.id(),Map.of()),p("B",1000005,Map.of()),Collections.nCopies(1024,param)),"card="+c.id());
   assertEquals("COMPLETED",r.status());assertTrue(r.turns()<=64);for(var state:r.finalStates()){assertTrue(state.anima()>=0&&state.defense()>=0);assertTrue(state.hp()<=state.maxHp());}checked++;
  }assertEquals(353,checked);
 }
 @Test void talismanDamageDoesNotUseSwordOrWeakness(){var a=p("A",3000003,Map.of("weakness",3));a=new Player(a.id(),a.name(),0,500,600,50,0,100,a.cards(),List.of(),a.buffs());assertEquals(492,end(run(a,p("B",1000005,Map.of()),List.of()),1,1).hp());}
 @Test void repelCancelsNegativeLayersRatherThanWholeApplication(){var r=run(p("A",3000015,Map.of()),p("B",1000005,Map.of("repel",3)),List.of());var s=end(r,1,1);assertEquals(0,s.statuses().get("internalInjury"));assertEquals(1,s.statuses().get("vulnerability"));assertEquals(1,s.statuses().get("weakness"));}
 @Test void qinEffectsAreSymmetricAndPriorQinAddsOneToEachHit(){var a=deck(p("A",1000005,Map.of()),5000002,5000009,1000005,1000005,1000005,1000005,1000005,1000005);var r=run(a,p("B",1000005,Map.of()),List.of());assertEquals(14,end(r,1,0).defense());assertEquals(14,end(r,1,1).defense());assertTrue(event(r,3,"ATTACK").message().contains("无视防御"));assertEquals(12,end(r,3,1).defense());}
 @Test void chaseBlockingConsumesBindingAndActivatesVinePassive(){var r=run(p("A",3000013,Map.of("binding",2)),p("B",1000005,Map.of("vineBinding",2)),List.of());assertTrue(r.events().stream().anyMatch(e->e.type().equals("CHASE_BLOCKED")));var blocked=event(r,1,"CHASE_BLOCKED").states().getFirst();assertEquals(1,blocked.statuses().get("binding"));assertEquals(2,blocked.statuses().get("externalInjury"));}
 @Test void formationsSpendOneChargePerTrigger(){var r=run(p("A",8000001,Map.of()),p("B",1000005,Map.of()),List.of());assertEquals(1,end(r,1,0).statuses().get("thunderFormation"));assertEquals(496,end(r,1,1).hp());}
 @Test void shatterFormationChargesArePerHit(){var r=run(p("A",1000013,Map.of("shatterFormation",1)),p("B",1000005,Map.of()),List.of());var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(2,hits.size());assertEquals(494,hits.getFirst().states().get(1).hp());assertEquals(491,hits.getLast().states().get(1).hp());}
 @Test void fateOpeningsReplayWithoutPlayingOrConsumingTarget(){var a=deck(p("A",1000005,Map.of()),11000025,11000009,1000005,1000005,1000005,1000005,1000005,1000005);var r=run(a,p("B",1000005,Map.of()),List.of());assertEquals(50+rules.byId.get(11000025).anima()+rules.byId.get(11000009).param(0),end(r,1,0).anima());assertEquals(1,end(r,1,0).nextSlot());}
 @Test void missingResolvedRandomOutcomeRejects(){var error=assertThrows(Engine.Rejected.class,()->run(p("A",3000001,Map.of()),p("B",1000005,Map.of()),List.of()));assertEquals("MISSING_BATTLE_PARAMETERS",error.status);}
 @Test void refinementChangesResourcesOutsideCombat(){var prep=new CareerPreparationService(rules);var r=prep.resolve(new CareerPreparationService.Input(Rules.ID,2000006,List.of(),List.of(),List.of(),List.of()));assertEquals(1,r.resourceDeltas().get("permanentMaxHp"));assertTrue(r.nextBattleSelf().isEmpty());}
 @Test void upgradeTargetsAreExplicitAndCannotBeRepeated(){var prep=new CareerPreparationService(rules);var r=prep.resolve(new CareerPreparationService.Input(Rules.ID,2000015,List.of(1000001),List.of(),List.of(),List.of(0)));assertEquals(List.of(1010001),r.hand());assertThrows(Engine.Rejected.class,()->prep.resolve(new CareerPreparationService.Input(Rules.ID,2000015,List.of(1000001),List.of(),List.of(),List.of(0,0))));}
 @Test void dreamIceLotusDefenseTriggersOnActualLifeLoss(){var r=run(p("A",3000009,Map.of()),p("B",1000005,Map.of("snowLotus",1)),List.of());var s=end(r,1,1);assertEquals(492,s.hp());assertEquals(4,s.defense());assertEquals(0,s.statuses().get("snowLotus"));}
 @Test void hardBambooDealsDamageFromCurrentDefenseAtTurnEnd(){var r=run(p("A",9000027,Map.of("hardBamboo",1)),p("B",1000005,Map.of()),List.of());assertEquals(500-rules.byId.get(9000027).defense()/4,end(r,1,1).hp());}
 @Test void paintedRandomDefenseUsesExactlyOneResolvedRoll(){var r=run(p("A",6000004,Map.of()),p("B",1000005,Map.of()),Collections.nCopies(128,13));assertEquals(13,end(r,1,0).defense());}
 @Test void paintingCanCopyASupportedCardWithoutPayingItsFee(){var r=run(p("A",6000011,Map.of()),p("B",1000005,Map.of()),Collections.nCopies(128,1000025));assertEquals(50+Math.min(0,rules.byId.get(6000011).anima()),end(r,1,0).anima());}
 @Test void fortuneRoundsRoundPositiveGainsUp(){var r=run(p("A",1000005,Map.of("fortuneRounds",2)),p("B",1000005,Map.of()),List.of());assertEquals(7,end(r,1,0).defense());}
 @Test void dreamDiamondLimitsLifeAndCapLossIndependently(){var r=run(p("A",7000009,Map.of()),p("B",1000005,Map.of("dreamDiamond",2)),List.of());var s=end(r,1,1);assertEquals(499,s.hp());assertEquals(599,s.maxHp());}
 @Test void doomDowngradesOpposingSameSlotBeforeBattle(){var a=deck(p("A",1000005,Map.of()),11000018,1000005,1000005,1000005,1000005,1000005,1000005,1000005);var b=p("B",1010001,Map.of());var r=engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,b)));assertEquals(1000001,event(r,1,"TURN_START").states().get(1).runtimeCards().getFirst());}
 @Test void recipeRefinementReturnsOnlySupportedNextBattleFields(){var prep=new CareerPreparationService(rules);var r=prep.resolve(new CareerPreparationService.Input(Rules.ID,9000024,List.of(),List.of(),List.of(),List.of()));assertEquals(-35,r.nextBattleSelf().get("hp"));assertEquals(1,r.nextBattleSelf().get("forcedChases"));}
 @Test void reversalReflectsAttemptedHealingIncludingOverflow(){var a=p("A",2000001,Map.of("reverseRounds",2));a=new Player(a.id(),a.name(),0,600,600,50,0,0,a.cards(),List.of(),a.buffs());assertEquals(496,end(run(a,p("B",1000005,Map.of()),List.of()),1,1).hp());}
 @Test void fateMinimumAppliesAfterWeakness(){var r=run(p("A",1000004,Map.of("minimumAttackRounds",2,"weakness",3)),p("B",1000005,Map.of()),List.of());assertEquals(494,event(r,1,"ATTACK").states().get(1).hp());}
 @Test void allOutsideBattleGradesHaveExecutableHandlers(){var prep=new CareerPreparationService(rules);int count=0;for(var c:rules.cards)if(CareerEffects.isCareer(c)&&c.preparationSupported()){
  var hand=new ArrayList<Integer>();var outcomes=new ArrayList<Integer>();var targets=new ArrayList<Integer>();
  switch(c.baseId()){
   case 2000015 -> {for(int j=0;j<c.param(0);j++){hand.add(2000001);targets.add(j);}}
   case 384 -> {hand.add(2000001);targets.add(0);}
   case 6000009,6000015 -> outcomes.add(1010022);
   case 6000010 -> outcomes.add(2000007);
   case 386 -> {for(int j=0;j<c.param(0);j++)outcomes.add(1000041);}
   case 9000006 -> outcomes.add(9000018);
   case 9000025 -> {for(int j=0;j<c.param(2);j++)outcomes.add(1000056);}
   case 9000015 -> {for(int j=0;j<c.param(0);j++)outcomes.add(9000001);}
   case 350 -> outcomes.add(1000001);
   case 180 -> {for(int j=0;j<c.param(1);j++)outcomes.add(2020006);}
   default -> { }
  }
  var input=new CareerPreparationService.Input(Rules.ID,c.id(),hand,List.of(1000001),outcomes,targets);assertDoesNotThrow(()->prep.resolve(input),"outside="+c.id());count++;
 }assertEquals(73,count);}
}
