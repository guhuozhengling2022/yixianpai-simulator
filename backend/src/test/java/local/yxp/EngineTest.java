package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;

class EngineTest {

    private Player injured(Player a,int hp){return new Player(a.id(),a.name(),a.characterId(),hp,a.maxHp(),a.anima(),a.defense(),a.swordIntent(),a.cards(),a.talentIds(),a.buffs());}
    @Test void woodSealHealsWithoutRaisingCapAndActivates(){
        var e=endAt(engine.simulate(request(injured(p("A",100,0,0,0,7000003),98),p("B",100,0,0,0,1000005))),1);
        assertEquals(100,e.states().getFirst().hp());assertEquals(100,e.states().getFirst().maxHp());assertEquals(1,e.states().getFirst().anima());assertEquals(1,e.states().getFirst().statuses().get("woodActivation"));assertEquals(3,e.states().getFirst().statuses().get("lastElement"));
    }
    @Test void waterGeneratesWoodButMetalDoesNot(){
        for(int prior:List.of(1,2,3)){
            var a=injured(p("A",100,1,0,0,7000004),50);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("lastElement",prior));
            var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
            var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(2,hits.size());assertEquals(50,hits.getLast().states().getFirst().hp());
            assertEquals(prior==1?50:55,endAt(r,1).states().getFirst().hp());
        }
    }
    @Test void woodShadowAddsAttackAfterAllHits(){
        var a=p("A",100,1,0,0,7000017);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("woodActivation",1,"attackBonus",2));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(90,hits.getLast().states().get(1).hp());assertEquals(2,hits.getLast().states().getFirst().statuses().get("attackBonus"));assertEquals(3,endAt(r,1).states().getFirst().statuses().get("attackBonus"));
    }
    @Test void reviveUsesAttackBonusOnlyWhenWoodConditionIsMet(){
        for(int active:List.of(0,1)){
            var a=injured(p("A",100,0,0,0,7000018),50);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("woodActivation",active,"attackBonus",4));
            var e=endAt(engine.simulate(request(a,p("B",100,0,0,0,1000005))),1);assertEquals(active==0?50:57,e.states().getFirst().hp());assertEquals(2,e.states().getFirst().anima());
        }
    }
    @Test void woodFormationRaisesCapWithoutHealingUntilNextCard(){
        var a=injured(p("A",100,0,0,0,7000036),50);
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        assertEquals(110,endAt(r,1).states().getFirst().maxHp());assertEquals(50,endAt(r,1).states().getFirst().hp());assertEquals(52,endAt(r,3).states().getFirst().hp());assertEquals(120,endAt(r,3).states().getFirst().maxHp());assertEquals(4,endAt(r,3).states().getFirst().statuses().get("woodFormation"));assertEquals(List.of(0,1),endAt(r,3).states().getFirst().consumedSlots());
    }
    @Test void woodFormationDoesNotTriggerIfCardCannotPay(){
        var a=injured(p("A",100,0,0,0,7000004),50);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("woodFormation",2));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));assertEquals(50,endAt(r,1).states().getFirst().hp());assertFalse(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("FORMATION_TRIGGER")));
    }
    @Test void fragranceCountsItsOwnAnimaAndFloorsDivision(){
        var a=injured(p("A",100,2,0,0,7000061),50);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("woodActivation",1));
        var e=endAt(engine.simulate(request(a,p("B",100,0,0,0,1000005))),1);assertEquals(4,e.states().getFirst().anima());assertEquals(1,e.states().getFirst().statuses().get("attackBonus"));assertEquals(52,e.states().getFirst().hp());
    }
    @Test void willowChasesUsingWaterChainAndNextCardUsesNewAttackBonus(){
        var a=injured(p("A",100,2,0,0,7000043),50);a=withCardsAndAbilities(a,List.of(7000043,7000004,1000005,1000005,1000005,1000005,1000005,1000005),List.of(),Map.of("lastElement",2));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));assertEquals(2,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());assertEquals(92,endAt(r,1).states().get(1).hp());assertEquals(58,endAt(r,1).states().getFirst().hp());assertFalse(rules.byId.get(7000043).actionAgain());
        r=engine.simulate(request(p("A",100,2,0,0,7000043),p("B",100,0,0,0,1000005)));assertEquals(1,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());
    }
    @Test void higherLevelSecretAlsoRaisesCapAndHeals(){
        var a=injured(p("A",100,0,0,0,7020068),50);
        var e=endAt(engine.simulate(request(a,p("B",100,0,0,0,1000005))),1);assertEquals(108,e.states().getFirst().maxHp());assertEquals(58,e.states().getFirst().hp());assertEquals(2,e.states().getFirst().statuses().get("attackBonus"));assertEquals(1,e.states().getFirst().statuses().get("woodActivation"));
    }
    @Test void woodActivationDoesNotImplyWaterAndHigherGradesKeepHitCounts(){
        var a=p("A",100,0,0,0,7000019);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("lastElement",3,"woodActivation",1));
        var e=endAt(engine.simulate(request(a,p("B",100,0,0,0,1000005))),1);assertEquals(1,e.states().getFirst().anima());assertEquals(0,e.states().getFirst().statuses().get("waterForce"));
        var r=engine.simulate(request(p("A",100,1,0,0,7020004),p("B",100,0,0,0,1000005)));assertEquals(4,r.events().stream().filter(x->x.turn()==1&&x.type().equals("ATTACK")).count());
    }


    private Event endAt(Result r,int turn){return r.events().stream().filter(e->e.turn()==turn&&e.type().equals("TURN_END")).findFirst().orElseThrow();}
    @Test void metalGeneratesWaterWithoutPermanentActivation(){
        var a=p("A",1000,10,0,0,7000002);
        a=withCardsAndAbilities(a,List.of(7000002,7000007,1000005,7000007,1000005,1000005,1000005,1000005),List.of(),Map.of());
        var r=engine.simulate(request(a,p("B",1000,0,0,0,1000005)));
        assertEquals(1,endAt(r,3).states().getFirst().statuses().get("waterForce"));
        assertEquals(0,endAt(r,3).states().getFirst().statuses().get("waterActivation"));
        assertEquals(1,endAt(r,7).states().getFirst().statuses().get("waterForce"));
        assertTrue(r.events().stream().anyMatch(e->e.turn()==5&&e.type().equals("ELEMENT_CHAIN")&&e.changes().stream().anyMatch(c->c.attribute().equals("lastElement")&&c.after()==0)));
    }
    @Test void consecutiveWaterWaivesCostButShortagePreservesPriorElement(){
        var a=p("A",100,0,0,0,7000019);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("lastElement",2));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        assertEquals(0,endAt(r,1).states().getFirst().anima());assertEquals(2,endAt(r,1).states().getFirst().statuses().get("waterForce"));
        a=p("A",100,0,0,0,7000007);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("lastElement",1,"waterFormation",1));
        r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        assertEquals(1,endAt(r,1).states().getFirst().anima());assertEquals(1,endAt(r,1).states().getFirst().statuses().get("lastElement"));
        assertFalse(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("FORMATION_TRIGGER")));
        assertEquals(1,endAt(r,5).states().getFirst().anima());
    }
    @Test void waterForceIsNonAttackDamageAndPersists(){
        var a=p("A",100,0,0,30,1000005);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("waterForce",5,"sharpness",9,"attackBonus",8,"weakness",1));
        var b=p("B",100,0,3,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("vulnerability",1,"externalInjury",7));
        var e=endAt(engine.simulate(request(a,b)),1);
        assertEquals(98,e.states().get(1).hp());assertEquals(0,e.states().get(1).defense());
        assertEquals(9,e.states().getFirst().statuses().get("sharpness"));assertEquals(30,e.states().getFirst().swordIntent());assertEquals(5,e.states().getFirst().statuses().get("waterForce"));
    }
    @Test void waterForceRespectsGuardAndLethalStopsNextActor(){
        var a=p("A",100,0,0,0,1000005);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("waterForce",5));
        var b=p("B",4,0,0,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("guard",1));
        var r=engine.simulate(request(a,b));assertEquals(4,endAt(r,1).states().get(1).hp());assertEquals(0,endAt(r,1).states().get(1).statuses().get("guard"));
        r=engine.simulate(request(a,p("B",4,0,0,0,1000005)));assertEquals(1,r.turns());assertEquals("A",r.winnerId());
        assertFalse(r.events().stream().anyMatch(e->e.actorId().equals("B")));
    }
    @Test void waterRiseUsesAnimaAfterFormationAndItsOwnGain(){
        var a=p("A",100,3,0,0,7000030);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("waterActivation",1,"waterFormation",1));
        var e=endAt(engine.simulate(request(a,p("B",100,0,0,0,1000005))),1);
        assertEquals(6,e.states().getFirst().anima());assertEquals(3,e.states().getFirst().statuses().get("waterForce"));assertEquals(102,e.states().getFirst().maxHp());
    }
    @Test void absorbUsesWaterForceIncludingNewGain(){
        var a=p("A",100,2,0,0,7000057);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("lastElement",2,"waterForce",2));
        var e=endAt(engine.simulate(request(a,p("B",100,0,0,0,1000005))),1);
        assertEquals(5,e.states().getFirst().statuses().get("waterForce"));assertEquals(111,e.states().getFirst().maxHp());assertEquals(111,e.states().getFirst().hp());
    }
    @Test void waterSurgeAddsForceToAttackAndThenDealsEndDamage(){
        var a=p("A",100,1,0,0,7000037);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("lastElement",2,"waterForce",4));
        var e=endAt(engine.simulate(request(a,p("B",100,0,0,0,1000005))),1);assertEquals(82,e.states().get(1).hp());
    }
    @Test void waterFormationConsumesSlotAndTriggersOnlyOnLaterCards(){
        var r=engine.simulate(request(p("A",100,0,0,0,7000029),p("B",100,0,0,0,1000005)));
        assertEquals(2,endAt(r,1).states().getFirst().anima());assertEquals(5,endAt(r,3).states().getFirst().anima());assertEquals(2,endAt(r,3).states().getFirst().statuses().get("waterFormation"));
        assertEquals(List.of(0,1),endAt(r,3).states().getFirst().consumedSlots());
    }
    @Test void waterActivationEnablesMetalWaveChase(){
        var a=p("A",100,0,0,0,7000116);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("waterActivation",1));
        assertEquals(2,engine.simulate(request(a,p("B",100,0,0,0,1000005))).events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());
    }

    @Test void metalReturnRefundsSharpnessAfterEachHitWithCeiling(){
        var a=p("A",100,0,0,0,7000099);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("sharpness",5,"metalActivation",1));
        var hits=engine.simulate(request(a,p("B",100,0,0,0,1000005))).events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();
        assertEquals(89,hits.get(0).states().get(1).hp());assertEquals(3,hits.get(0).states().get(0).statuses().get("sharpness"));
        assertEquals(80,hits.get(1).states().get(1).hp());assertEquals(2,hits.get(1).states().get(0).statuses().get("sharpness"));
    }
    @Test void metalEdgeUsesTotalWoundIncludingSharpnessAndExternalInjury(){
        var a=p("A",100,0,0,0,7000026);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("sharpness",4,"metalActivation",1));
        var b=p("B",100,0,0,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("externalInjury",2));
        var end=engine.simulate(request(a,b)).events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();
        assertEquals(88,end.states().get(1).hp());assertEquals(6,end.states().get(0).statuses().get("sharpness"));
    }
    @Test void metalShuttlePreservesSharpnessAndConditionalChaseCannotLeakBetweenRequests(){
        var a=p("A",100,0,0,0,7000034);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("sharpness",5,"metalActivation",1));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(2,hits.size());
        assertEquals(92,hits.getLast().states().get(1).hp());assertEquals(5,hits.getLast().states().get(0).statuses().get("sharpness"));
        r=engine.simulate(request(p("A",100,0,0,0,7000034),p("B",100,0,0,0,1000005)));
        assertEquals(1,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());assertFalse(rules.byId.get(7000034).actionAgain());
    }
    @Test void metalFormationTriggersBeforeCardWithoutNeedingActivation(){
        var a=p("A",100,0,0,0,7000002);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("metalFormation",3));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var hit=r.events().stream().filter(e->e.type().equals("ATTACK")).findFirst().orElseThrow();assertEquals(91,hit.states().get(1).hp());
        assertTrue(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("FORMATION_TRIGGER")));
    }
    @Test void sustainedFormationStacksAndItsSlotIsConsumed(){
        var r=engine.simulate(request(p("A",1000,0,0,0,7000016),p("B",1000,0,0,0,1000005)));
        var first=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();
        assertEquals(1,first.states().get(0).statuses().get("metalFormation"));assertEquals(4,first.states().get(0).statuses().get("sharpness"));
        var second=r.events().stream().filter(e->e.turn()==3&&e.type().equals("TURN_END")).findFirst().orElseThrow();
        assertEquals(2,second.states().get(0).statuses().get("metalFormation"));assertEquals(9,second.states().get(0).statuses().get("sharpness"));assertEquals(2,second.states().get(0).consumedSlots().size());
    }
    @Test void metalWaveAddsResourcesAndOnlyChasesWhenMetalIsActive(){
        var a=p("A",100,0,0,0,7000116);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("metalActivation",1));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();assertEquals(2,end.states().get(0).anima());assertEquals(4,end.states().get(0).statuses().get("sharpness"));
        r=engine.simulate(request(p("A",100,0,0,0,7000116),p("B",100,0,0,0,1000005)));
        assertFalse(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("CHASE")));
    }
    @Test void allDeterministicBaseCardPairsTerminateOrReportResolutionLimit(){
        var representatives=rules.cards.stream().filter(c->c.supported()&&c.variant()==0&&!c.requiresBattleParams()&&!c.requiresContext()).toList();
        assertTrue(representatives.size()>=100);
        for(var a:representatives)for(var b:representatives){
            Models.Result r;try{r=engine.simulateForValidation(request(p("A",100,50,0,0,a.id()),p("B",100,50,0,0,b.id())));}catch(Engine.Rejected e){assertEquals("RESOLUTION_LIMIT",e.status,a.id()+" vs "+b.id());continue;}
            String pair=a.id()+" vs "+b.id();assertTrue(r.turns()>=1&&r.turns()<=64,pair);assertEquals("BATTLE_END",r.events().getLast().type(),pair);
            for(var state:r.finalStates()){
                assertTrue(state.defense()>=0&&state.anima()>=0&&state.hp()<=state.maxHp(),pair);
                assertTrue(state.statuses().values().stream().allMatch(v->v>=0),pair);
            }
        }
    }
    @Test void metalStoreUsesDefenseAfterDecayAndAfterCardDefenseGain(){
        var a=p("A",100,0,11,0,7000025);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("metalActivation",1));
        var e=engine.simulate(request(a,p("B",100,0,0,0,1000005))).events().stream().filter(x->x.turn()==1&&x.type().equals("CARD_EFFECT")).findFirst().orElseThrow();
        assertEquals(9,e.states().get(0).defense());assertEquals(6,e.states().get(0).statuses().get("sharpness"));
    }
    @Test void metalStoreStillGainsBaseSharpnessWithoutActivation(){
        var e=engine.simulate(request(p("A",100,0,100,0,7000025),p("B",100,0,0,0,1000005))).events().stream().filter(x->x.turn()==1&&x.type().equals("CARD_EFFECT")).findFirst().orElseThrow();
        assertEquals(54,e.states().get(0).defense());assertEquals(4,e.states().get(0).statuses().get("sharpness"));
    }
    @Test void exposedStatusNamesMatchValidationAndSnapshotKeys(){
        for(String key:Rules.STATUS_NAMES.keySet()){
            var a=p("A",100,0,0,0,1000005);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of(key,1));
            var r=engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(a,p("B",100,0,0,0,1000005)),Collections.nCopies(128,99)));assertTrue(r.events().getFirst().states().getFirst().statuses().containsKey(key));
        }
    }
    @Test void metalNeedleGainsSharpnessOnlyAfterItsAttackAndOnlyWhenActivated(){
        var a=p("A",100,0,0,0,7000002);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("metalActivation",1));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var hit=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).findFirst().orElseThrow();assertEquals(94,hit.states().get(1).hp());
        var end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();assertEquals(2,end.states().get(0).statuses().get("sharpness"));
        r=engine.simulate(request(p("A",100,0,0,0,7000002),p("B",100,0,0,0,1000005)));
        end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();assertEquals(0,end.states().get(0).statuses().get("sharpness"));
    }
    @Test void metalSealActivatesAfterAttackAndActivationIsNotConsumed(){
        var r=engine.simulate(request(p("A",100,0,0,0,7000001),p("B",100,0,0,0,1000005)));
        var hit=r.events().stream().filter(e->e.type().equals("ATTACK")).findFirst().orElseThrow();assertEquals(0,hit.states().get(0).statuses().get("metalActivation"));
        var end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();assertEquals(1,end.states().get(0).statuses().get("metalActivation"));
    }
    @Test void sharpnessWaitsForWoundAndIsConsumedByFirstQualifyingHit(){
        var a=p("A",100,0,0,0,1000013);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("sharpness",5));
        var r=engine.simulate(request(a,p("B",100,0,3,0,1000005)));
        var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();
        assertEquals(100,hits.get(0).states().get(1).hp());assertEquals(5,hits.get(0).states().get(0).statuses().get("sharpness"));
        assertEquals(92,hits.get(1).states().get(1).hp());assertEquals(0,hits.get(1).states().get(0).statuses().get("sharpness"));
    }
    @Test void guardPreservesSharpnessAndPreventsExternalInjuryBonus(){
        var a=p("A",100,0,0,0,1000013);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("sharpness",5));
        var b=p("B",100,0,0,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("guard",1,"externalInjury",2));
        var hits=engine.simulate(request(a,b)).events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();
        assertEquals(100,hits.get(0).states().get(1).hp());assertEquals(5,hits.get(0).states().get(0).statuses().get("sharpness"));
        assertEquals(90,hits.get(1).states().get(1).hp());assertEquals(2,hits.get(1).states().get(1).statuses().get("externalInjury"));
    }
    @Test void externalInjuryAndSharpnessAreAddedAfterWeaknessAndDefense(){
        var a=p("A",100,0,0,0,1000001);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("sharpness",5,"weakness",1));
        var b=p("B",100,0,1,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("externalInjury",2));
        var hit=engine.simulate(request(a,b)).events().stream().filter(e->e.type().equals("ATTACK")).findFirst().orElseThrow();
        assertEquals(91,hit.states().get(1).hp());
    }
    @Test void attackBonusPersistsAcrossHitsAndCardsWhileSwordIsConsumed(){
        var a=p("A",1000,0,0,2,1000013);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("attackBonus",4));
        var r=engine.simulate(request(a,p("B",1000,0,0,0,1000005)));
        var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();
        assertEquals(982,hits.getLast().states().get(1).hp());
        var end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();
        assertEquals(4,end.states().get(0).statuses().get("attackBonus"));assertEquals(0,end.states().get(0).swordIntent());
    }
    @Test void attackBonusIsIncludedBeforeWeaknessPercentage(){
        var a=p("A",100,0,0,0,1000001);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("attackBonus",3,"weakness",1));
        var hit=engine.simulate(request(a,p("B",100,0,0,0,1000005))).events().stream().filter(e->e.type().equals("ATTACK")).findFirst().orElseThrow();assertEquals(95,hit.states().get(1).hp());
    }
    @Test void strengthElixirAddsPersistentBonusAndIsConsumed(){
        var r=engine.simulate(request(p("A",1000,0,0,0,2000008),p("B",1000,0,0,0,1000005)));
        var effect=r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_EFFECT")).findFirst().orElseThrow();
        assertEquals(rules.byId.get(2000008).param(0),effect.states().get(0).statuses().get("attackBonus"));
        assertTrue(r.events().stream().anyMatch(e->e.turn()==1&&e.type().equals("CARD_CONSUMED")));
    }
    @Test void guardAbsorbsInternalInjuryWithoutReducingInjuryOrDefense(){
        var a=p("A",100,0,10,0,1000005);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("guard",1,"internalInjury",4));
        var e=engine.simulate(request(a,p("B",100,0,0,0,1000005))).events().stream().filter(x->x.type().equals("INTERNAL_INJURY")).findFirst().orElseThrow();
        assertEquals(100,e.states().get(0).hp());assertEquals(5,e.states().get(0).defense());assertEquals(0,e.states().get(0).statuses().get("guard"));assertEquals(4,e.states().get(0).statuses().get("internalInjury"));
    }
    @Test void fullyBlockedAttackDoesNotConsumeGuard(){
        var b=p("B",100,0,100,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("guard",1));
        var e=engine.simulate(request(p("A",100,0,0,0,1000001),b)).events().stream().filter(x->x.type().equals("ATTACK")).findFirst().orElseThrow();
        assertEquals(1,e.states().get(1).statuses().get("guard"));assertEquals(100,e.states().get(1).hp());
    }
    @Test void weaknessDecaysOnceForWholeChaseTurn(){
        var a=p("A",1000,30,0,0,1000036);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("weakness",2));
        var r=engine.simulate(request(a,p("B",1000,0,0,0,1000005)));
        var attacks=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();assertEquals(2,attacks.size());
        assertEquals(2,attacks.get(1).states().get(0).statuses().get("weakness"));
        var end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();assertEquals(1,end.states().get(0).statuses().get("weakness"));
    }
    @Test void newGuardTalentIsAppliedOnlyDuringOpening(){
        var a=p("A",100,0,0,0,1000005);a=withCardsAndAbilities(a,a.cards(),List.of(26),Map.of());
        var r=engine.simulate(new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(a,p("B",100,0,0,0,1000001))));
        var opening=r.events().stream().filter(e->e.type().equals("TALENT")).findFirst().orElseThrow();assertEquals(1,opening.states().get(0).statuses().get("guard"));
    }
    @Test void weaknessAndVulnerabilityCancelBeforeIntegerRounding(){
        var a=p("A",100,0,0,0,1000001);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("weakness",2));
        var b=p("B",100,0,0,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("vulnerability",2));
        var hit=engine.simulate(request(a,b)).events().stream().filter(e->e.type().equals("ATTACK")).findFirst().orElseThrow();
        assertEquals(94,hit.states().get(1).hp());
    }
    @Test void guardBlocksOnlyFirstUnblockedHitAndWoundTrigger(){
        var b=p("B",100,0,0,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(),Map.of("guard",1));
        var r=engine.simulate(request(p("A",100,5,0,0,1000009),b));
        assertEquals(1,r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).count());
        var hit=r.events().stream().filter(e->e.type().equals("ATTACK")).findFirst().orElseThrow();assertEquals(100,hit.states().get(1).hp());assertEquals(0,hit.states().get(1).statuses().get("guard"));
    }
    @Test void lethalInternalInjuryPreventsCardExecutionAndIgnoresDefense(){
        var a=p("A",3,0,100,0,1000001);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("internalInjury",4));
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));assertEquals("B",r.winnerId());assertEquals(1,r.turns());assertFalse(r.events().stream().anyMatch(e->e.type().equals("CARD_PLAY")));
    }
    final Rules rules=new Rules(); final Engine engine=new Engine(rules);
    Player p(String id,int hp,int anima,int def,int sword,int card){return new Player(id,id,0,hp,hp,anima,def,sword,Collections.nCopies(8,card),List.of(),Map.of(),List.of());}
    Request request(Player a,Player b){return new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(a),allowRepeatedSingleUseCards(b)));}
    @Test void manaShortageRetainsSlotAndRetriesNextTurn(){
        var r=engine.simulate(request(p("A",100,0,0,0,1000037),p("B",100,0,0,0,1000005)));
        var shortage=r.events().stream().filter(e->e.type().equals("ANIMA_SHORTAGE")).findFirst().orElseThrow();
        assertEquals(1,shortage.states().getFirst().anima());assertEquals(0,shortage.states().getFirst().nextSlot());
        assertTrue(r.events().stream().anyMatch(e->e.turn()==3&&e.type().equals("CARD_PLAY")&&e.actorId().equals("A")));
    }
    @Test void oddDefenseDecaysAtOwnersTurnOnly(){
        var r=engine.simulate(request(p("A",100,0,5,0,1000005),p("B",100,0,9,0,1000005)));
        var start=r.events().get(1);assertEquals(2,start.states().get(0).defense());assertEquals(9,start.states().get(1).defense());
    }
    @Test void swordAppliesToEachHitAndIsConsumedAfterCard(){
        var r=engine.simulate(request(p("A",100,0,0,2,1000013),p("B",100,0,0,0,1000005)));
        var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();
        assertEquals(2,hits.size());assertEquals(90,hits.getLast().states().get(1).hp());
        var consumed=r.events().stream().filter(e->e.type().equals("SWORD_CONSUMED")).findFirst().orElseThrow();assertEquals(0,consumed.states().getFirst().swordIntent());
    }
    @Test void turnLimitTiesGoToFirstActorAndLogsAreDeterministic(){
        var q=request(p("A",100,0,0,0,1000005),p("B",100,0,0,0,1000005));
        var r=engine.simulate(q);assertEquals(64,r.turns());assertEquals("TURN_LIMIT",r.endReason());assertEquals("A",r.winnerId());assertEquals(r,engine.simulate(q));
    }
    @Test void armorAbsorbsBeforeLife(){
        var r=engine.simulate(request(p("A",100,0,0,0,1000001),p("B",100,0,4,0,1000005)));
        var hit=r.events().stream().filter(e->e.type().equals("ATTACK")).findFirst().orElseThrow();assertEquals(98,hit.states().get(1).hp());assertEquals(0,hit.states().get(1).defense());
    }
    @Test void unsupportedCardsAndTalentsAreRejected(){
        assertEquals("UNSUPPORTED",assertThrows(Engine.Rejected.class,()->engine.simulate(request(p("A",100,0,0,0,10000058),p("B",100,0,0,0,1000001)))).status);
        var a=p("A",100,0,0,0,1000001);var talented=new Player(a.id(),a.name(),0,100,100,0,0,0,a.cards(),List.of(1),Map.of());
        assertThrows(Engine.Rejected.class,()->engine.simulate(request(talented,p("B",100,0,0,0,1000001))));
    }
    @Test void invalidNumbersAndSlotsAreRejected(){
        assertThrows(Engine.Rejected.class,()->engine.simulate(request(p("A",0,0,0,0,1000001),p("B",100,0,0,0,1000001))));
        var bad=new Player("A","A",0,100,100,0,0,0,List.of(1000001),List.of(),Map.of());assertThrows(Engine.Rejected.class,()->engine.simulate(request(bad,p("B",100,0,0,0,1000001))));
    }
    Player withCardsAndAbilities(Player p,List<Integer> cards,List<Integer> talents,Map<String,Integer> buffs){return new Player(p.id(),p.name(),p.characterId(),p.hp(),p.maxHp(),p.anima(),p.defense(),p.swordIntent(),cards,talents,buffs);}
    @Test void consecutiveCloudCardsAddSeparateAttackAndNonCloudBreaksChain(){
        var a=withCardsAndAbilities(p("A",100,0,0,0,1000001),List.of(1000001,1000002,1000004,1000002,1000001,1000001,1000001,1000001),List.of(),Map.of());
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        assertEquals(2,r.events().stream().filter(e->e.turn()==3&&e.type().equals("ATTACK")).count());
        assertEquals(1,r.events().stream().filter(e->e.turn()==7&&e.type().equals("ATTACK")).count());
    }
    @Test void cloudOnlyAttackDoesNothingWithoutChainButTalentEnablesIt(){
        var a=p("A",100,0,0,0,1000014);var b=p("B",100,0,0,0,1000005);
        assertEquals(0,engine.simulate(request(a,b)).events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).count());
        var talented=withCardsAndAbilities(a,a.cards(),List.of(14),Map.of());
        assertEquals(1,engine.simulate(request(talented,b)).events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).count());
    }
    @Test void ignoreDefenseIsConsumedPerHitEvenWhenDefenseIsZero(){
        var a=p("A",100,0,0,0,1000013);a=withCardsAndAbilities(a,a.cards(),List.of(),Map.of("ignoreDefense",1));
        var r=engine.simulate(request(a,p("B",100,0,10,0,1000005)));
        var hits=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).toList();
        assertEquals(97,hits.get(0).states().get(1).hp());assertEquals(10,hits.get(0).states().get(1).defense());
        assertEquals(97,hits.get(1).states().get(1).hp());assertEquals(7,hits.get(1).states().get(1).defense());
        assertEquals(0,hits.get(0).states().get(0).ignoreDefense());
    }
    @Test void woundExtraAttackRequiresActualHpDamage(){
        var a=p("A",100,5,0,0,1000009);
        assertEquals(1,engine.simulate(request(a,p("B",100,0,99,0,1000005))).events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).count());
        assertEquals(2,engine.simulate(request(a,p("B",100,0,0,0,1000005))).events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).count());
    }
    @Test void swordGainedOnWoundSurvivesConsumptionOfOldSword(){
        var r=engine.simulate(request(p("A",100,5,0,2,1000011),p("B",100,0,0,0,1000005)));
        var end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();
        assertEquals(3,end.states().get(0).swordIntent());
    }
    @Test void openingTalentsRunOnceInFirstActorOrderAndAreRejectedPostOpening(){
        var a=p("A",100,0,0,0,1000005);a=withCardsAndAbilities(a,a.cards(),List.of(13),Map.of());
        var b=p("B",100,0,0,0,1000005);b=withCardsAndAbilities(b,b.cards(),List.of(3),Map.of());
        var q=new Request(Rules.ID,"STANDARD","PRE_OPENING","B",List.of(a,b));var r=engine.simulate(q);
        var opening=r.events().stream().filter(e->e.type().equals("TALENT")).toList();
        assertEquals(2,opening.size());assertEquals("B",opening.get(0).actorId());assertEquals(8,opening.get(0).states().get(1).defense());
        assertEquals(1,opening.get(1).states().get(0).anima());
        assertThrows(Engine.Rejected.class,()->engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","B",q.players())));
    }
    @Test void chaseKeepsSameTurnAndDoesNotDecayDefenseTwice(){
        var a=p("A",100,20,8,0,1000036);
        a=withCardsAndAbilities(a,List.of(1000036,1000001,1000001,1000001,1000001,1000001,1000001,1000001),List.of(),Map.of());
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        assertEquals(2,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());
        assertEquals(1,r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_START")).count());
        var end=r.events().stream().filter(e->e.turn()==1&&e.type().equals("TURN_END")).findFirst().orElseThrow();
        assertEquals(14,end.states().get(0).defense());assertEquals(2,end.states().get(0).nextSlot());
    }
    @Test void consecutiveChaseCardsRespectOneAdditionalActionQuota(){
        var r=engine.simulate(request(p("A",1000,20,0,0,1000036),p("B",1000,0,0,0,1000005)));
        assertEquals(2,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());
        assertEquals(1,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CHASE_LIMIT")).count());
    }
    @Test void defenseFreeCostIsCheckedAfterDecayAndChaseShortageRetainsNextSlot(){
        var a=p("A",100,0,2,0,1000025);
        a=withCardsAndAbilities(a,List.of(1000025,1000036,1000001,1000001,1000001,1000001,1000001,1000001),List.of(),Map.of());
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var shortage=r.events().stream().filter(e->e.turn()==1&&e.type().equals("ANIMA_SHORTAGE")).findFirst().orElseThrow();
        assertEquals(1,shortage.states().get(0).anima());assertEquals(1,shortage.states().get(0).nextSlot());assertEquals(9,shortage.states().get(0).defense());
        var noDefense=engine.simulate(request(p("A",100,0,1,0,1000025),p("B",100,0,0,0,1000005)));
        assertEquals(0,noDefense.events().stream().filter(e->e.turn()==1&&e.type().equals("CHASE")).count());
    }
    @Test void consumablesAreTrackedPerSlotAndAllConsumedFallsBackToNormalAttack(){
        var r=engine.simulate(request(p("A",1000,0,0,0,2000003),p("B",1000,0,0,0,1000005)));
        assertEquals(8,r.events().stream().filter(e->e.actorId().equals("A")&&e.type().equals("CARD_CONSUMED")).count());
        var fallback=r.events().stream().filter(e->e.type().equals("FALLBACK_ATTACK")).findFirst().orElseThrow();
        assertEquals(17,fallback.turn());assertEquals(0,fallback.states().get(0).runtimeCards().get(0));
        assertEquals(7,fallback.states().get(0).consumedSlots().size());
        assertTrue(r.events().stream().anyMatch(e->e.turn()==17&&e.type().equals("ATTACK")&&e.cardId()==0));
    }
    @Test void healingIsCappedAndMaximumLifeElixirRaisesCapBeforeHealing(){
        var a=p("A",100,0,0,0,2000001);
        a=new Player(a.id(),a.name(),0,95,100,0,0,0,a.cards(),List.of(),Map.of());
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var effect=r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_EFFECT")).findFirst().orElseThrow();assertEquals(100,effect.states().get(0).hp());
        r=engine.simulate(request(p("A",100,0,0,0,2000011),p("B",100,0,0,0,1000005)));
        effect=r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_EFFECT")).findFirst().orElseThrow();
        assertEquals(113,effect.states().get(0).maxHp());assertEquals(113,effect.states().get(0).hp());
    }
    @Test void consumedChaseCardCanContinueButCannotBeReusedNextCycle(){
        var a=p("A",1000,0,0,0,3000013);
        a=withCardsAndAbilities(a,List.of(3000013,1000005,1000005,1000005,1000005,1000005,1000005,1000005),List.of(),Map.of());
        var r=engine.simulate(request(a,p("B",1000,0,0,0,1000005)));
        assertEquals(1,r.events().stream().filter(e->e.cardId()!=null&&e.cardId()==3000013&&e.type().equals("CARD_PLAY")).count());
        assertEquals(2,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());
        assertTrue(r.events().stream().anyMatch(e->e.type().equals("CARD_SKIP")));
    }
    @Test void healingElixirScalesWithRemainingAnima(){
        var a=p("A",100,5,0,0,2000007);
        a=new Player(a.id(),a.name(),0,50,100,5,0,0,a.cards(),List.of(),Map.of());
        var r=engine.simulate(request(a,p("B",100,0,0,0,1000005)));
        var effect=r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_EFFECT")).findFirst().orElseThrow();
        assertEquals(67,effect.states().get(0).hp());assertEquals(5,effect.states().get(0).anima());
    }
    @Test void lethalChaseCardEndsBattleBeforeAnotherCardIsPlayed(){
        var r=engine.simulate(request(p("A",100,3,0,0,1000036),p("B",5,0,0,0,1000005)));
        assertEquals(1,r.turns());assertEquals("DEATH",r.endReason());
        assertEquals(1,r.events().stream().filter(e->e.type().equals("CARD_PLAY")).count());
        assertFalse(r.events().stream().anyMatch(e->e.type().equals("CHASE")));
    }
}
