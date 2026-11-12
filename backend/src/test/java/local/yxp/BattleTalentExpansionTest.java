package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;
import java.util.*;
import org.junit.jupiter.api.Test;
import static local.yxp.Models.*;
import static local.yxp.Engine.*;
import static org.junit.jupiter.api.Assertions.*;
class BattleTalentExpansionTest {
 final Rules rules=new Rules();final Engine engine=new Engine(rules);
 Player p(String id,int card,List<Integer> ts,Map<String,Integer> bs){return new Player(id,id,0,400,800,10,0,0,Collections.nCopies(8,card),ts,bs);}
 Player cards(Player p,List<Integer> cards){return new Player(p.id(),p.name(),p.characterId(),p.hp(),p.maxHp(),p.anima(),p.defense(),p.swordIntent(),cards,p.talentIds(),p.buffs());}
 Result run(String stage,Player a){return engine.simulate(new Request(Rules.ID,"STANDARD",stage,"A",List.of(allowRepeatedSingleUseCards(a),p("B",1000005,List.of(),Map.of()))));}
 State end(Result r,int turn,int who){return r.events().stream().filter(e->e.turn()==turn&&e.type().equals("TURN_END")).findFirst().orElseThrow().states().get(who);}
 State opened(Result r){return r.events().stream().filter(e->e.type().equals("TALENT")).reduce((a,b)->b).orElseThrow().states().getFirst();}
 Fighter[] pair(List<Integer> ts,Map<String,Integer> bs){var a=new Fighter(p("A",1000005,ts,bs));var b=new Fighter(p("B",1000005,List.of(),Map.of()));var res=new Resolution();res.log=new ArrayList<>();res.fighters=new Fighter[]{a,b};for(var f:res.fighters){f.rules=rules;f.resolution=res;}a.opponent=b;b.opponent=a;return res.fighters;}
 @Test void seventhSoulReplacesOnlyEighthBasicSlotAtAnyBasicGrade(){for(int id:List.of(0,10000,20000)){var a=p("A",id,List.of(52),Map.of());var s=opened(run("PRE_OPENING",a));assertEquals(11,s.runtimeCards().get(7));for(int i=0;i<7;i++)assertEquals(id,s.runtimeCards().get(i));}}
 @Test void soulDoesNotReplaceOtherCardsOrFallBackToFirstSlot(){var a=p("A",1000005,List.of(52),Map.of());var ds=new ArrayList<>(a.cards());ds.set(0,0);assertEquals(ds,opened(run("PRE_OPENING",cards(a,ds))).runtimeCards());}
 @Test void soulCardAllGradesUseDirectLifeLossAndSkip(){for(int id:List.of(11,10011,20011)){var r=run("POST_OPENING",p("A",id,List.of(),Map.of()));var s=end(r,1,1);assertEquals(400-rules.byId.get(id).param(0),s.hp());assertEquals(800,s.maxHp());assertEquals(1,s.statuses().get("skipTurns"));assertFalse(r.events().stream().anyMatch(e->e.turn()==2&&e.type().equals("CARD_PLAY")));}}
 @Test void soulBypassesDefenseButGuardStillBlocksLifeLoss(){for(int guard:List.of(0,1)){var b=new Player("B","B",0,400,800,10,100,0,Collections.nCopies(8,1000005),List.of(),Map.of("guard",guard));var r=engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(p("A",11,List.of(),Map.of()),b)));var s=end(r,1,1);assertEquals(guard==0?396:400,s.hp());assertEquals(100,s.defense());assertEquals(1,s.statuses().get("skipTurns"));}}
 @Test void soulIsNotAnAttackAndDoesNotConsumeSword(){var a=new Player("A","A",0,400,800,10,0,9,Collections.nCopies(8,11),List.of(),Map.of());var r=run("POST_OPENING",a);assertEquals(9,end(r,1,0).swordIntent());assertEquals(0,end(r,1,0).statuses().get("totalAttacks"));}
 @Test void exhaustionTalentAddsManaAndHexWithChargeQiOnFailedPlay(){var a=new Player("A","A",0,400,800,0,0,0,Collections.nCopies(8,171),List.of(142),Map.of());var r=run("POST_OPENING",a);var e=r.events().stream().filter(x->x.turn()==1&&x.type().equals("ANIMA_SHORTAGE")).findFirst().orElseThrow();assertEquals(8,e.states().getFirst().anima());assertEquals(3,e.states().getFirst().statuses().get("hexagram"));assertEquals(0,e.states().getFirst().nextSlot());assertFalse(r.events().stream().anyMatch(x->x.turn()==1&&x.type().equals("CARD_PLAY")));}
 @Test void failedPlayHexTriggersOtherOwnedTalents(){var a=new Player("A","A",0,400,800,0,0,0,Collections.nCopies(8,171),List.of(142,61),Map.of());assertEquals(1,end(run("POST_OPENING",a),1,0).statuses().get("starPower"));}
 @Test void noExtraExhaustionRewardOnSuccessfulPlay(){var r=run("POST_OPENING",p("A",1000005,List.of(142),Map.of()));assertEquals(11,end(r,1,0).anima());assertEquals(0,end(r,1,0).statuses().get("hexagram"));}
 @Test void exhaustionRewardAlsoWorksBeforeOpening(){var a=new Player("A","A",0,400,800,0,0,0,Collections.nCopies(8,171),List.of(142),Map.of());assertEquals(3,end(run("PRE_OPENING",a),1,0).statuses().get("hexagram"));}
 @Test void unionEnablesEveryElementWithoutActivationCountersOrExplosion(){var fs=pair(List.of(278,138),Map.of());var a=fs[0];assertTrue(a.metalActive()&&a.waterActive()&&a.woodActive()&&a.fireActive()&&a.earthActive());assertEquals(0,ElementalEffects.activeCount(a));assertEquals(400,fs[1].hp);}
 @Test void unionCountsFivePlusMixedSpiritExtras(){var a=pair(List.of(278),Map.of())[0];Arrays.fill(a.cards,1000005);assertEquals(5,ElementalEffects.elements(a,rules));a.cards[0]=7000101;assertEquals(5+rules.byId.get(7000101).param(0),ElementalEffects.elements(a,rules));}
 @Test void unionEnablesSingleElementHeavenMarrowException(){var a=pair(List.of(278),Map.of())[0];ElementalEffects.after(new ArrayList<>(),a,a.opponent,rules.byId.get(7000073),rules);assertEquals(rules.byId.get(7000073).param(0),a.status("elementalNoAttack"));}
 @Test void unionDoesNotCreateOneOrTwoElementChaseForLifeDestroyer(){var a=pair(List.of(278),Map.of())[0];assertFalse(SpecialEffects.chase(a,rules.byId.get(165),rules));}
 @Test void unionCountsFiveForExistingOpeningGrowth(){var s=opened(run("PRE_OPENING",p("A",1000005,List.of(278,277),Map.of())));assertEquals(800+5*rules.talentById.get(277).param(0),s.maxHp());}
 @Test void echoDowngradesBonusToFirstGradeAndRestoresPhysicalGrade(){var r=run("POST_OPENING",p("A",7020016,List.of(),Map.of("arrayEcho",1)));var s=end(r,1,0);assertEquals(4,s.statuses().get("metalFormation"));assertEquals(rules.byId.get(7000016).param(0)+rules.byId.get(7020016).param(0)+rules.byId.get(7000016).param(1),s.statuses().get("sharpness"));assertEquals(7020016,s.runtimeCards().getFirst());assertEquals(0,s.statuses().get("arrayEcho"));var ids=r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).map(Event::cardId).toList();assertEquals(List.of(7020016,7000016),ids);}
 @Test void firstGradeArrayStillEchoesAndOnlyOneChargeIsSpent(){var s=end(run("POST_OPENING",p("A",7000016,List.of(),Map.of("arrayEcho",2))),1,0);assertEquals(2,s.statuses().get("metalFormation"));assertEquals(1,s.statuses().get("arrayEcho"));}
 @Test void echoOpeningGrantsOneCharge(){assertEquals(1,opened(run("PRE_OPENING",p("A",1000005,List.of(279),Map.of()))).statuses().get("arrayEcho"));}
 @Test void echoDoesNotTriggerOnOtherArraysOrNonArrays(){var s=end(run("POST_OPENING",p("A",1000018,List.of(),Map.of("arrayEcho",1))),1,0);assertEquals(1,s.statuses().get("arrayEcho"));}
 @Test void echoRunsNonTemporaryBeforeCardCallbacksTwice(){var s=end(run("POST_OPENING",p("A",7020016,List.of(149),Map.of("arrayEcho",1,"physiqueCap",100))),1,0);assertEquals(2*rules.talentById.get(149).param(0),s.statuses().get("physique"));}
 @Test void echoSlotConsumesOnceAndOtherSlotsStayUnconsumed(){var s=end(run("POST_OPENING",p("A",7020016,List.of(),Map.of("arrayEcho",1))),1,0);assertEquals(List.of(0),s.consumedSlots());}
 @Test void echoedWaterFormationAlsoTriggersBeforeOriginalEffect(){var s=end(run("POST_OPENING",p("A",7020029,List.of(),Map.of("arrayEcho",1))),1,0);assertEquals(10+rules.byId.get(7000029).anima()+rules.byId.get(7020029).anima()+rules.byId.get(7000029).param(0),s.anima());}
 @Test void echoAndUnionDetailedAndCompactPathsAgree(){var req=new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(p("A",7020016,List.of(278,149),Map.of("arrayEcho",1,"physiqueCap",100))),p("B",1000005,List.of(),Map.of())));assertEquals(engine.simulate(req).finalStates(),engine.simulateForValidation(req).finalStates());}
}
