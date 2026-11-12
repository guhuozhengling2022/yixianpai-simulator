package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class StarEffectsTest {
 final Rules rules=new Rules();final Engine engine=new Engine(rules);
 Player p(String id,int card,Map<String,Integer> buffs){return new Player(id,id,0,300,500,50,0,0,Collections.nCopies(8,card),List.of(),buffs);}
 Player deck(Player p,int...ids){return new Player(p.id(),p.name(),0,p.hp(),p.maxHp(),p.anima(),p.defense(),p.swordIntent(),Arrays.stream(ids).boxed().toList(),p.talentIds(),p.buffs());}
 Result run(Player a,Player b,Integer... params){return engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(a),allowRepeatedSingleUseCards(b)),Arrays.asList(params)));}
 Event event(Result r,int turn,String type){return r.events().stream().filter(e->e.turn()==turn&&e.type().equals(type)).findFirst().orElseThrow();}
 State end(Result r,int turn,int i){return event(r,turn,"TURN_END").states().get(i);}
 List<Integer> tape(Rules.Card c){var pattern=new ArrayList<Integer>();int b=c.baseId();
  if(b==4000053)pattern.add(0);
  else if(b==193){pattern.add(c.param(0));for(int i=0;i<c.param(0);i++)pattern.add(100);}
  else if(b==4000085)pattern.add(100);
  else if(b==4000108){pattern.add(c.defense());pattern.add(99);}
  else {if(c.randomAttack()>0)for(int i=0;i<Math.max(1,c.attackCount());i++)pattern.add(c.attack());if(c.randomDefense()>0)pattern.add(c.defense());if(b==4000091)pattern.add(c.param(0));if(b==4000102)pattern.add(99);if(pattern.isEmpty())pattern.add(Set.of(4000017,4000019).contains(b)?c.param(0):99);}
  var result=new ArrayList<Integer>();for(int i=0;i<1024;i++)result.add(pattern.get(i%pattern.size()));return result;
 }
 @Test void completeStarCatalogIsAccepted(){var cards=rules.cards.stream().filter(c->c.sect()==2).toList();assertEquals(432,cards.size());assertEquals(132,cards.stream().map(Rules.Card::baseId).distinct().count());assertTrue(cards.stream().allMatch(Rules.Card::supported));}
 @Test void allGradesRunWithAndWithoutStarHexAndPosteriorDependencies(){int count=0;for(var c:rules.cards)if(c.sect()==2)for(boolean active:new boolean[]{false,true}){
  var buffs=active?Map.of("starMask",255,"starPower",2,"hexagram",3,"posteriorAlways",1,"healingTotal",20,"hexagramLost",2):Map.<String,Integer>of();
  var req=new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(p("A",c.id(),buffs)),p("B",1000005,Map.of())),tape(c));var result=assertDoesNotThrow(()->engine.simulateForValidation(req),c.id()+" active="+active);
  assertEquals("COMPLETED",result.status());for(var s:result.finalStates()){assertTrue(s.defense()>=0&&s.anima()>=0);assertTrue(s.hp()<=s.maxHp());assertTrue(s.statuses().values().stream().allMatch(n->n>=0));}count++;
 }assertEquals(864,count);}
 @Test void compactValidationUsesIdenticalRulesAndFinalStates(){var req=new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(p("A",4000093,Map.of("starMask",255,"starPower",2)),p("B",7000042,Map.of("metalActivation",1))));var full=engine.simulate(req);var compact=engine.simulateForValidation(req);assertEquals(full.finalStates(),compact.finalStates());assertEquals(full.turns(),compact.turns());assertEquals(full.winnerId(),compact.winnerId());assertEquals("BATTLE_END",compact.events().getLast().type());}
 @Test void starPowerAppliesToAnyAttackInTheMarkedPhysicalSlot(){var r=run(p("A",1000004,Map.of("starMask",1,"starPower",3)),p("B",1000005,Map.of()));assertEquals(293,event(r,1,"ATTACK").states().get(1).hp());}
 @Test void duplicateStarPositionGrantsOneAnima(){var r=run(p("A",4000023,Map.of("starMask",2)),p("B",1000005,Map.of()));assertEquals(51,end(r,1,0).anima());assertEquals(2,end(r,1,0).statuses().get("starMask"));}
 @Test void posteriorStartsOnSecondUseOfTheSameSlot(){var a=deck(p("A",1000005,Map.of()),4000033,1000005,1000005,1000005,1000005,1000005,1000005,1000005);var r=run(a,p("B",1000005,Map.of()));assertFalse(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("ATTACK")));assertTrue(r.events().stream().anyMatch(e->e.turn()==17&&e.type().equals("ATTACK")));}
 @Test void firstStrikeEnablesPosteriorBeforeFirstUse(){var r=run(p("A",4000033,Map.of("posteriorAlways",1)),p("B",1000005,Map.of()));assertTrue(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("ATTACK")));assertTrue(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("CHASE")));}
 @Test void reverseDirectionMovesToPreviousPhysicalSlot(){var a=deck(p("A",1000005,Map.of()),4000066,1000005,1000005,1000005,1000005,1000005,1000005,1000004);var r=run(a,p("B",1000005,Map.of()));assertEquals(7,end(r,1,0).nextSlot());assertEquals(1000004,event(r,3,"CARD_PLAY").cardId());}
 @Test void randomRollConsumesOneHexagramAndKeepsTheProvidedValue(){var a=deck(p("A",1000005,Map.of("hexagram",2)),4000004,1000005,1000005,1000005,1000005,1000005,1000005,1000005);var r=run(a,p("B",1000005,Map.of()),4,4,4,4);var s=end(r,1,0);assertEquals(1,s.statuses().get("hexagram"));assertEquals(1,s.statuses().get("hexagramTriggered"));assertEquals(1,s.statuses().get("hexagramLost"));assertEquals(296,event(r,1,"ATTACK").states().get(1).hp());}
 @Test void missingRandomValueRejectsInsteadOfInventingARoll(){var error=assertThrows(Engine.Rejected.class,()->run(p("A",4000004,Map.of()),p("B",1000005,Map.of())));assertEquals("MISSING_BATTLE_PARAMETERS",error.status);}
 @Test void hexagramDamageBypassesDefenseButNotGuard(){var r=run(p("A",4000001,Map.of("hexagramDamage",3)),p("B",1000005,Map.of("guard",1)));assertEquals(300,end(r,1,1).hp());assertEquals(0,end(r,1,1).statuses().get("guard"));}
 @Test void starMovementRespectsReverseDirection(){var r=run(p("A",4000023,Map.of("reverseDirection",1)),p("B",1000005,Map.of()));assertEquals(128,end(r,1,0).statuses().get("starMask"));assertEquals(7,end(r,1,0).nextSlot());}
 @Test void resourceGainCascadesIntoHexagramDefenseAndHealing(){var r=run(p("A",4000001,Map.of("animaToHexagram",1,"resourceDefense",1,"resourceHeal",1)),p("B",1000005,Map.of()));int n=rules.byId.get(4000001).hexagram()+rules.byId.get(4000001).anima();assertEquals(n,end(r,1,0).statuses().get("hexagram"));assertTrue(end(r,1,0).defense()>=n);}
 @Test void overflowGrowthActuallyRaisesTheCap(){var a=p("A",2000001,Map.of("overflowGrowth",1));a=new Player(a.id(),a.name(),0,500,500,50,0,0,a.cards(),List.of(),a.buffs());var s=end(run(a,p("B",1000005,Map.of())),1,0);assertEquals(508,s.hp());assertEquals(508,s.maxHp());}
 @Test void lifeLossInversionDoesNotRecordLoss(){var r=run(p("A",3000009,Map.of()),p("B",1000005,Map.of("invertLifeLoss",1)));var s=end(r,1,1);assertEquals(300,s.hp());assertEquals(4,s.statuses().get("hpLost"));assertEquals(4,s.statuses().get("healingTotal"));}
 @Test void injuryAndExternalInjuryAreAmplifiedBySnakeShadow(){var r=run(p("A",3000015,Map.of()),p("B",1000005,Map.of("snakeShadow",2)));assertEquals(4,end(r,1,1).statuses().get("internalInjury"));}
 @Test void starJumpSkipsAndReplaysOpenings(){var a=deck(p("A",1000005,Map.of("starMask",1)),196,11000009,4000009,1000005,1000005,1000005,1000005,1000005);var r=run(a,p("B",1000005,Map.of()));assertEquals(2,event(r,1,"CHASE").states().getFirst().nextSlot());}
 @Test void revivalIsLoggedAndLimitedByCharges(){var a=new Player("A","A",0,1,100,0,0,0,Collections.nCopies(8,1000005),List.of(),Map.of("reviveWheel",1,"internalInjury",2));var r=run(a,p("B",1000005,Map.of()));assertEquals(1,r.events().stream().filter(e->e.type().equals("RESURRECTION")).count());assertEquals(64,event(r,1,"RESURRECTION").states().getFirst().hp());}
 @Test void mereMentionOfPosteriorDoesNotMakeAQualifiedCard(){assertFalse(StarEffects.isPosterior(rules.byId.get(4000049)));assertTrue(StarEffects.isPosterior(rules.byId.get(4000013)));}
 @Test void jumpBackMeansThePreviousCardRatherThanRepeatingTheCurrentSlot(){var a=deck(p("A",1000005,Map.of()),4000103,1000005,1000005,1000005,1000005,1000005,1000005,1000004);var r=run(a,p("B",1000005,Map.of()));assertEquals(7,end(r,1,0).nextSlot());}
 @Test void dynamicallyGeneratedAttacksConsumeSwordOnceAndInactivePosteriorKeepsIt(){var a=p("A",4000033,Map.of("posteriorAlways",1));a=new Player(a.id(),a.name(),0,a.hp(),a.maxHp(),a.anima(),0,2,a.cards(),List.of(),a.buffs());assertEquals(0,end(run(a,p("B",1000005,Map.of())),1,0).swordIntent());var b=new Player(a.id(),a.name(),0,a.hp(),a.maxHp(),a.anima(),0,2,a.cards(),List.of(),Map.of());assertEquals(2,end(run(b,p("B",1000005,Map.of())),1,0).swordIntent());}
 @Test void neutralAndPositiveStatusesAreNotCountedOrCleansedAsDebuffs(){var f=new Engine.Fighter(p("A",1000005,Map.of("snakeShadow",2,"noHealing",2,"skipTurns",1)));assertEquals(0,StarEffects.debuffs(f));assertFalse(CareerEffects.NEGATIVE.contains("noHealing"));assertFalse(CareerEffects.NEGATIVE.contains("snakeShadow"));}
 @Test void repeatExecutesTheBodyTwiceButRecordsOneCardAndConsumesSwordOnce(){var a=p("A",1000004,Map.of("repeatNextCard",1));a=new Player(a.id(),a.name(),0,a.hp(),a.maxHp(),a.anima(),0,2,a.cards(),List.of(),a.buffs());var r=run(a,p("B",1000005,Map.of()));assertEquals(288,end(r,1,1).hp());assertEquals(0,end(r,1,0).swordIntent());assertEquals(1,end(r,1,0).statuses().get("cardsUsed"));assertEquals(1,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_REPEAT")).count());}
 @Test void settingLifeDoesNotPretendToBeNormalLifeLoss(){var r=run(p("A",4000058,Map.of("hexagram",8)),p("B",1000005,Map.of()));var s=end(r,1,1);assertEquals(8,s.hp());assertEquals(0,s.statuses().get("hpLost"));}
 @Test void revivalIsAHealingAttemptAndCanBeBlocked(){var a=new Player("A","A",0,1,100,0,0,0,Collections.nCopies(8,1000005),List.of(),Map.of("reviveWheel",1,"internalInjury",2,"noHealing",1));var r=run(a,p("B",1000005,Map.of()));assertEquals("DEATH",r.endReason());assertEquals(1,r.turns());assertEquals(-1,r.finalStates().getFirst().hp());}
 @Test void dreamStarPermanentGainIsOncePerTurnAndUsesTheStackCount(){var r=run(p("A",3000013,Map.of("nonStarPermanent",2)),p("B",1000005,Map.of()));assertEquals(2,end(r,1,0).statuses().get("starPower"));}
 @Test void dreamReflectReductionIsAdditiveWithVulnerability(){var r=run(p("A",1000013,Map.of()),p("B",1000005,Map.of("reflectRounds",2,"vulnerability",1)));assertEquals(297,event(r,1,"ATTACK").states().get(1).hp());}
 @Test void dreamReflectDoesNotReduceOrdinaryDamageBelowOne(){var r=run(p("A",3000003,Map.of()),p("B",1000005,Map.of("reflectRounds",2)));assertEquals(294,end(r,1,1).hp());}
}
