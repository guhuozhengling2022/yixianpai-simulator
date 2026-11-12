package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;

import org.junit.jupiter.api.Test;
import java.util.*;
import static local.yxp.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleInteractionsTest {
    final FactionMechanicsTest f=new FactionMechanicsTest();
    Player deck(Player p,int... cards){return new Player(p.id(),p.name(),p.characterId(),p.hp(),p.maxHp(),p.anima(),p.defense(),p.swordIntent(),Arrays.stream(cards).boxed().toList(),p.talentIds(),p.buffs());}
    Result tape(Player a,Player b,Integer... values){return f.engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(a,b),List.of(values)));}
    @Test void allRequestedFactionVariantsHaveACombatOrOutsideBattlePath(){
        for(int sect:List.of(1,3)){
            var cards=f.rules.cards.stream().filter(c->c.sect()==sect).toList();assertEquals(sect==1?438:454,cards.size());
            assertTrue(cards.stream().allMatch(c->c.supported()||c.preparationSupported()));
        }
        assertTrue(f.rules.byId.get(293).supported());assertTrue(f.rules.byId.get(293).obsolete());assertFalse(f.rules.byId.get(7000109).supported());assertTrue(f.rules.byId.get(7000109).preparationSupported());
    }
    @Test void tapeIsSharedByBothPlayersInExecutionOrder(){
        var a=deck(f.p("A",1000005,300,300,10,0,0,Map.of()),1000052,1000005,1000005,1000005,1000005,1000005,1000005,1000005);
        var b=deck(f.p("B",1000005,300,300,10,0,0,Map.of()),1000052,1000005,1000005,1000005,1000005,1000005,1000005,1000005);
        var result=tape(a,b,10,20,0,0,0,0,0,0);assertEquals(282,f.event(result,1,"ATTACK").states().get(1).hp());assertEquals(272,f.event(result,2,"ATTACK").states().getFirst().hp());
    }
    @Test void absentTapeIsNotReplacedByGuessedHandOrRandomValues(){
        var error=assertThrows(Engine.Rejected.class,()->f.run(f.a(1000052,Map.of()),f.b()));assertEquals("MISSING_BATTLE_PARAMETERS",error.status);
    }
    @Test void elementalFlowCopiesWithoutFeesAndThenClearsItsOwnElement(){
        var a=deck(f.p("A",1000005,100,100,3,0,0,Map.of()),7000001,7000067,7000007,1000005,1000005,1000005,1000005,1000005);
        var r=f.run(a,f.b());var s=f.end(r,3,0);assertEquals(3,s.anima());assertEquals(1,s.statuses().get("waterActivation"));assertEquals(1,s.statuses().get("waterForce"));assertEquals(0,s.statuses().get("lastElement"));
    }
    @Test void nestedChainFormationsSearchLeftWithoutMovingThePhysicalSlot(){
        var a=deck(f.p("A",1000005,1000,1000,0,0,0,Map.of()),1000046,1000064,1000064,1000005,1000005,1000005,1000005,1000005);
        var r=f.run(a,f.p("B",1000005,1000,1000,0,0,0,Map.of()));var state=f.end(r,5,0);assertEquals(7,state.defense());assertEquals(6,state.statuses().get("swordFormationCount"));assertEquals(3,state.nextSlot());
    }
    @Test void timeMirrorStopsRecursiveCopying(){
        var r=f.run(f.p("A",173,1000,1000,50,0,0,Map.of()),f.p("B",1000005,1000,1000,0,0,0,Map.of()));
        assertEquals(2,r.events().stream().filter(e->e.turn()==1&&e.type().equals("COPY_GUARD")).count());assertEquals(2,f.end(r,1,0).statuses().get("metalActivation"));assertEquals(48,f.end(r,1,0).anima());
    }
    @Test void fireEarthRepeatDoesNotPayTwice(){
        var r=f.run(f.a(7000009,Map.of("doubleFireEarth",1)),f.b());assertEquals(2,f.end(r,1,0).statuses().get("fireActivation"));assertEquals(12,f.end(r,1,0).anima());assertEquals(94,f.end(r,1,1).maxHp());
    }
    @Test void mountainSwordChargesAndIgnoresDefenseAndGuard(){
        var r=f.run(f.p("A",171,1000,1000,0,0,0,Map.of()),f.p("B",1000005,1000,1000,0,100,0,Map.of("guard",1)));
        assertEquals(6,f.end(r,1,0).anima());var hit=f.event(r,5,"ATTACK");assertEquals(880,hit.states().get(1).hp());assertEquals(1,hit.states().get(1).statuses().get("guard"));assertEquals(32,hit.states().get(1).defense());
    }
    @Test void hpCostBypassesGuardAndContributesToLifeHistory(){
        var r=f.run(f.p("A",163,100,100,0,0,0,Map.of("guard",2)),f.b());var s=f.end(r,1,0);assertEquals(94,s.hp());assertEquals(2,s.statuses().get("guard"));assertEquals(6,s.statuses().get("hpLost"));assertEquals(f.rules.byId.get(163).swordIntent()+6/f.rules.byId.get(163).param(0),s.swordIntent());
    }
    @Test void firstUseIsRememberedBySlotAcrossCycles(){
        var a=deck(f.p("A",1000005,1000,1000,0,0,0,Map.of()),1000094,1000005,1000005,1000005,1000005,1000005,1000005,1000005);
        var r=f.run(a,f.p("B",1000005,1000,1000,0,0,0,Map.of()));assertEquals(1,f.end(r,15,0).swordIntent());
    }
    @Test void cloudSeaPreservingANonCloudCardAlsoIncrementsCloudHistory(){
        var r=f.run(f.a(1000005,Map.of("cloudSea",1,"cloudChain",2,"cloudTotal",2)),f.b());var s=f.end(r,1,0);assertEquals(3,s.cloudChain());assertEquals(3,s.statuses().get("cloudTotal"));assertEquals(0,s.statuses().get("cloudSea"));
    }
    @Test void temporaryWaterDoublingIsRemovedAtOwnTurnEnd(){
        var r=f.run(f.p("A",1000005,1000,1000,3,0,0,Map.of("waterForce",2,"doubleWaterPending",1)),f.p("B",1000005,1000,1000,0,0,0,Map.of()));
        var start=f.event(r,1,"TURN_START").states().getFirst();assertEquals(6,start.anima());assertEquals(4,start.statuses().get("waterForce"));var end=f.end(r,1,0);assertEquals(4,end.anima());assertEquals(2,end.statuses().get("waterForce"));assertEquals(0,end.statuses().get("temporaryWater"));
    }
    @Test void nextAttackBonusesDoNotRepeatAcrossMultiHitCard(){
        var r=f.run(f.p("A",1000013,100,100,0,0,2,Map.of("nextAttackBonus",5,"doubleNextAttack",1,"attackBonus",1)),f.b());
        var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(86,hits.getFirst().states().get(1).hp());assertEquals(80,hits.getLast().states().get(1).hp());
    }
    @Test void sharpnessRefundIsCountedAsNewSharpnessGain(){
        var r=f.run(f.a(7000099,Map.of("sharpness",5,"metalActivation",1)),f.b());assertEquals(5,f.end(r,1,0).statuses().get("sharpnessGained"));assertEquals(2,f.end(r,1,0).statuses().get("sharpness"));
    }
    @Test void attackGainCanConvertIntoWoodThorns(){
        var r=f.run(allowRepeatedSingleUseCards(f.a(2000008,Map.of("attackToThorns",4))),f.b());assertEquals(0,f.end(r,1,0).statuses().get("attackBonus"));assertEquals(f.rules.byId.get(2000008).param(0),f.end(r,1,0).statuses().get("woodThorns"));
    }
    @Test void healingCanConvertIntoSharpnessWithoutHealingHistory(){
        var r=f.run(f.p("A",7000003,50,100,0,0,0,Map.of("healToSharpRounds",2)),f.b());assertEquals(50,f.end(r,1,0).hp());assertEquals(4,f.end(r,1,0).statuses().get("sharpness"));assertEquals(0,f.end(r,1,0).statuses().get("healingTotal"));
    }
    @Test void gainingSharpnessCanBurnLifeAndCapInstead(){
        var r=f.run(f.a(7000071,Map.of("sharpToFireRounds",2)),f.b());assertEquals(0,f.end(r,1,0).statuses().get("sharpness"));assertEquals(84,f.end(r,1,1).maxHp());
    }
    @Test void passiveRagingUpgradeConsumesOneOpeningGrant(){
        var a=deck(f.p("A",1000005,100,100,0,0,0,Map.of()),1000022,1030076,1000005,1000005,1000005,1000005,1000005,1000005);
        var r=f.engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,f.b())));assertEquals(1010022,f.end(r,1,0).runtimeCards().getFirst());assertEquals(92,f.end(r,1,0).hp());assertEquals(0,f.end(r,1,0).statuses().get("ragingUpgrades"));
    }
    @Test void fieldSkipsTheLastSlotAndPublishesPostBattlePlan(){
        var a=deck(f.a(1000005,Map.of()),1000005,1000005,1000005,1000005,1000005,1000005,1000005,202);
        var r=f.run(a,f.b());assertTrue(r.events().stream().anyMatch(e->e.type().equals("CARD_SKIP")&&Objects.equals(202,e.cardId())));assertEquals(1,r.events().stream().filter(e->e.type().equals("POST_BATTLE_PLAN")).count());assertEquals("BATTLE_END",r.events().getLast().type());
    }
    @Test void preparationAppliesProvidedOutcomeWithoutInventingRng(){
        var service=new PreparationService(f.rules);var r=service.resolve(new PreparationService.Input(Rules.ID,7000109,List.of(7000001),List.of(7000002)));
        assertEquals("PREPARATION",r.phase());assertEquals("USER_SUPPLIED",r.outcomeAuthority());assertEquals("金灵",r.preferredElement());assertEquals(List.of(7000002),r.gainedCards());
    }
    @Test void fieldDrawRequiresFirstGradeElementCards(){
        var service=new PreparationService(f.rules);var r=service.resolve(new PreparationService.Input(Rules.ID,202,List.of(202),List.of(7000003)));assertEquals(-1,r.experienceDelta());assertEquals("AFTER_BATTLE",r.phase());
        assertThrows(Engine.Rejected.class,()->service.resolve(new PreparationService.Input(Rules.ID,202,List.of(202),List.of(7010003))));assertThrows(Engine.Rejected.class,()->service.resolve(new PreparationService.Input(Rules.ID,7000109,List.of(),List.of(1000001))));
    }
    @Test void forcedWoundAllowsSharpnessThroughFullDefense(){
        var a=deck(f.p("A",1000005,100,100,1,0,0,Map.of("sharpness",5)),1000021,1020067,1000005,1000005,1000005,1000005,1000005,1000005);
        var r=f.run(a,f.p("B",1000005,100,100,0,100,0,Map.of()));var first=f.event(r,1,"ATTACK");assertEquals(95,first.states().get(1).hp());assertEquals(0,first.states().getFirst().statuses().get("sharpness"));
    }
    @Test void forcedWoundCanRefundSwordEvenWhenGuardPreventsHpLoss(){
        var a=deck(f.p("A",1000005,100,100,1,0,2,Map.of("sharpness",5)),1000021,1020067,1000005,1000005,1000005,1000005,1000005,1000005);
        var r=f.run(a,f.p("B",1000005,100,100,0,100,0,Map.of("guard",1)));assertEquals(100,f.end(r,1,1).hp());assertEquals(2,f.end(r,1,0).swordIntent());assertEquals(0,f.end(r,1,0).statuses().get("sharpness"));
    }
    @Test void fullRefundAndMetalReturnRefundStack(){
        var r=f.run(f.a(7000099,Map.of("metalActivation",1,"sharpness",5,"sharpnessFullRefund",1)),f.b());var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(8,hits.getFirst().states().getFirst().statuses().get("sharpness"));assertEquals(5,hits.getLast().states().getFirst().statuses().get("sharpness"));
    }
    @Test void phantomDrawDoesNotAttackWhenNoCardWasSelected(){
        var params=new Integer[64];Arrays.fill(params,-1);var r=tape(f.a(262,Map.of()),f.b(),params);assertFalse(r.events().stream().anyMatch(e->e.type().equals("ATTACK")));
    }
    @Test void elementalChaseChargesAreNotSpentAfterTheTurnChaseLimit(){
        var a=deck(f.a(7000001,Map.of("mixedChase",5)),7000001,7000009,1000005,1000005,1000005,1000005,1000005,1000005);
        var r=f.run(a,f.b());assertEquals(4,f.end(r,1,0).statuses().get("mixedChase"));
    }
    @Test void dreamVariantCodeIsNotItsUpgradeRarity(){
        var c=f.rules.byId.get(1040086);assertEquals(4,c.variant());assertEquals(0,c.rarity());assertEquals(5,c.level());assertTrue(c.noUpgrade());assertEquals(1000086,c.baseId());
        var r=f.run(f.a(1000073,Map.of("ragingUpgrades",1)),f.b());assertEquals(1000073,f.end(r,1,0).runtimeCards().getFirst());assertEquals(1,f.end(r,1,0).statuses().get("ragingUpgrades"));
    }
    @Test void elementalFlowDoesNotDowngradeDreamRealmByItsIdCode(){
        var a=deck(f.a(1000005,Map.of()),7000001,7000067,7040076,1000005,1000005,1000005,1000005,1000005);
        var r=f.run(a,f.p("B",1000005,1000,1000,0,0,0,Map.of()));assertTrue(r.events().stream().anyMatch(e->e.turn()==3&&e.type().equals("CARD_PLAY")&&Objects.equals(e.cardId(),7040076)));
    }
    @Test void openingTalentsPrecedeConditionalCardOpenings(){
        var a=deck(f.p("A",1000005,500,500,0,0,0,Map.of("experience",12)),1000101,1000005,1000005,1000005,1000005,1000005,1000005,1000005);
        a=new Player(a.id(),a.name(),0,a.hp(),a.maxHp(),a.anima(),a.defense(),a.swordIntent(),a.cards(),List.of(13),a.buffs());
        var r=f.engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,f.b())));
        assertEquals(1,f.event(r,1,"TURN_START").states().getFirst().anima());
        assertEquals(12,r.events().getFirst().states().getFirst().statuses().get("experience"));
    }
    @Test void twinSealActivationIsAnOpeningEffect(){
        var a=deck(f.a(1000005,Map.of()),55,1000005,1000005,1000005,1000005,1000005,1000005,1000005);
        var r=f.engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,f.b())));
        var s=f.event(r,1,"TURN_START").states().getFirst();assertEquals(1,s.statuses().get("waterActivation"));assertEquals(1,s.statuses().get("woodActivation"));
        assertEquals(0,f.event(f.run(a,f.b()),1,"TURN_START").states().getFirst().statuses().get("waterActivation"));
    }
    @Test void mixedSpiritActivatesAdjacentSlotsWhenCast(){
        var a=deck(f.a(1000005,Map.of()),7000101,7000009,1000005,1000005,1000005,1000005,1000005,7000001);
        var r=f.run(a,f.b());var initial=f.event(r,1,"TURN_START").states().getFirst();assertEquals(0,initial.statuses().get("fireActivation"));
        var s=f.end(r,1,0);assertEquals(1,s.statuses().get("fireActivation"));assertEquals(1,s.statuses().get("metalActivation"));
    }
}
