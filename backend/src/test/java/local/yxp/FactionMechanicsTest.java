package local.yxp;

import org.junit.jupiter.api.Test;
import java.util.*;
import static local.yxp.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class FactionMechanicsTest {
    final Rules rules=new Rules();final Engine engine=new Engine(rules);
    Player p(String id,int card,int hp,int max,int anima,int defense,int sword,Map<String,Integer> buffs){return new Player(id,id,0,hp,max,anima,defense,sword,Collections.nCopies(8,card),List.of(),buffs);}
    Player a(int card,Map<String,Integer> buffs){return p("A",card,100,100,10,0,0,buffs);}
    Player b(){return p("B",1000005,100,100,0,0,0,Map.of());}
    Result run(Player a,Player b){return engine.simulate(new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(a,b)));}
    Event event(Result result,int turn,String type){return result.events().stream().filter(e->e.turn()==turn&&e.type().equals(type)).findFirst().orElseThrow();}
    State end(Result r,int turn,int player){return event(r,turn,"TURN_END").states().get(player);}
    @Test void allFiveNaturalLinksAndOnlyForwardLinksAreActive(){
        for(int prior=1;prior<=5;prior++){
            var f=new Engine.Fighter(a(1000005,Map.of("lastElement",prior)));
            boolean[] flags={f.metalActive(),f.waterActive(),f.woodActive(),f.fireActive(),f.earthActive()};
            assertTrue(flags[prior-1]);assertTrue(flags[prior%5]);assertEquals(2,java.util.stream.IntStream.range(0,5).filter(i->flags[i]).count());
        }
    }
    @Test void fireSealBypassesDefenseAndCapLossStillClampsGuardedHp(){
        var r=run(a(7000009,Map.of()),p("B",1000005,100,100,0,30,0,Map.of("guard",1)));
        var s=end(r,1,1);assertEquals(97,s.maxHp());assertEquals(97,s.hp());assertEquals(30,s.defense());assertEquals(0,s.statuses().get("guard"));assertEquals(3,s.statuses().get("maxHpLost"));
    }
    @Test void capCanReachZeroAndEndsBattle(){
        var r=run(a(7000069,Map.of()),p("B",1000005,4,4,0,0,0,Map.of()));assertEquals(1,r.turns());assertEquals(0,r.finalStates().get(1).maxHp());assertEquals("DEATH",r.endReason());
    }
    @Test void fireBlastUsesNewAttackBonus(){
        var r=run(a(7000032,Map.of("fireActivation",1,"attackBonus",2)),b());assertEquals(90,end(r,1,1).hp());assertEquals(3,end(r,1,0).statuses().get("attackBonus"));
    }
    @Test void fireFlashOnlyLowersCapAndChaseRequiresFire(){
        var r=run(a(7000038,Map.of()),p("B",1000005,50,100,0,0,0,Map.of()));assertEquals(50,end(r,1,1).hp());assertEquals(96,end(r,1,1).maxHp());
        r=run(a(7000038,Map.of("lastElement",3)),b());assertEquals(2,r.events().stream().filter(e->e.turn()==1&&e.type().equals("CARD_PLAY")).count());
    }
    @Test void healingHistoryCountsOverflow(){
        var r=run(a(7000003,Map.of()),b());assertEquals(100,end(r,1,0).hp());assertEquals(4,end(r,1,0).statuses().get("healingTotal"));
    }
    @Test void vineHitsUseHistoricalHealingWithConfiguredCap(){
        var r=run(a(7000064,Map.of("woodActivation",1,"healingTotal",100)),b());assertEquals(5,r.events().stream().filter(e->e.turn()==1&&e.type().equals("ATTACK")).count());
    }
    @Test void thornHealingUsesWholeCardWoundThenFloorsOnce(){
        var r=run(p("A",7000027,50,100,1,0,0,Map.of("woodActivation",1)),b());assertEquals(54,end(r,1,0).hp());
    }
    @Test void naturalEarthDefenseBonusUsesDefenseAfterDecay(){
        var r=run(p("A",7000013,100,100,0,2,0,Map.of("lastElement",4)),b());assertEquals(91,end(r,1,1).hp());
    }
    @Test void nextDefenseArrivesAfterDecayAndOnlyOnce(){
        var r=run(a(7000024,Map.of("earthActivation",1)),b());var s=event(r,3,"TURN_START").states().getFirst();assertEquals(4,s.defense());assertEquals(0,s.statuses().get("nextDefense"));
    }
    @Test void cliffAndRestoreTriggerOnDecayAndDoNotRecurse(){
        var r=run(p("A",1000005,100,100,0,8,0,Map.of("cliff",1,"restoreDefense",1)),b());var start=event(r,1,"TURN_START");assertEquals(8,start.states().getFirst().defense());assertEquals(96,start.states().get(1).hp());assertEquals(4,start.states().getFirst().statuses().get("defenseLost"));assertEquals(0,start.states().getFirst().statuses().get("restoreDefense"));
    }
    @Test void ironAppliesBeforePercentageAndBeforeDefense(){
        var r=run(p("A",1000007,100,100,1,0,0,Map.of("weakness",1)),p("B",1000005,100,100,0,1,0,Map.of("iron",2)));assertEquals(98,end(r,1,1).hp());
    }
    @Test void waterDamageAlsoRespectsIron(){
        var r=run(a(1000005,Map.of("waterForce",8)),p("B",1000005,100,100,0,0,0,Map.of("iron",2)));assertEquals(97,end(r,1,1).hp());
    }
    @Test void blockedHealingDoesNotIncrementHealingHistory(){
        var r=run(p("A",7000003,50,100,0,0,0,Map.of("noHealing",1)),b());assertEquals(50,end(r,1,0).hp());assertEquals(0,end(r,1,0).statuses().get("healingTotal"));
    }
    @Test void springHealsBeforeEachOfItsOwnAttacks(){
        var r=run(a(7000097,Map.of("woodActivation",1)),b());assertEquals(104,end(r,1,0).maxHp());assertEquals(104,end(r,1,0).hp());
    }
    @Test void spiritDiscountAndSwordQiCanTogetherPayCost(){
        var r=run(p("A",1000019,100,100,0,0,0,Map.of("spiritDiscount",1,"swordQi",2)),b());assertEquals(83,end(r,1,1).hp());assertEquals(1,end(r,1,0).statuses().get("swordQi"));
    }
    @Test void dotAddsItsOwnAnimaBeforeAttack(){var r=run(p("A",1000027,100,100,3,0,0,Map.of()),b());assertEquals(95,end(r,1,1).hp());assertEquals(5,end(r,1,0).anima());}
    @Test void drawAddsAnimaBeforeCheckingThreshold(){var r=run(p("A",1000024,100,100,1,0,0,Map.of()),b());assertEquals(94,end(r,1,1).hp());assertEquals(3,end(r,1,0).anima());}
    @Test void mirrorUsesNewlyAddedDefense(){var r=run(p("A",1000046,100,100,0,10,0,Map.of()),b());assertEquals(92,end(r,1,1).hp());assertEquals(8,end(r,1,0).defense());}
    @Test void swordRefundRequiresWoundAndKeepsExactUsedSword(){
        var r=run(p("A",1000021,100,100,1,0,5,Map.of()),b());assertEquals(5,end(r,1,0).swordIntent());
        r=run(p("A",1000021,100,100,1,0,5,Map.of()),p("B",1000005,100,100,0,100,0,Map.of()));assertEquals(0,end(r,1,0).swordIntent());
    }
    @Test void doubleRagingRepeatsWithoutFeeAndUsesUpdatedRagingCount(){
        var r=run(a(1000022,Map.of("doubleRaging",1,"ragingCount",2)),b());assertEquals(80,end(r,1,1).hp());assertEquals(4,end(r,1,0).statuses().get("ragingCount"));assertEquals(0,end(r,1,0).statuses().get("doubleRaging"));
    }
    @Test void doubleRagingCannotBeConsumedByUnaffordableCard(){
        var r=run(p("A",1000007,100,100,0,0,0,Map.of("doubleRaging",1)),b());assertEquals(1,end(r,1,0).statuses().get("doubleRaging"));
    }
    @Test void oneSwordMultipliesIntentAndIgnoresWeakness(){var r=run(p("A",1000056,100,100,0,0,2,Map.of("weakness",1)),b());assertEquals(86,end(r,1,1).hp());}
    @Test void halfAnimaCarriesRemainderAcrossOwnTurns(){var r=run(p("A",1000005,100,100,0,0,0,Map.of("halfAnima",1)),b());assertEquals(0,event(r,1,"TURN_START").states().getFirst().anima());assertEquals(2,event(r,3,"TURN_START").states().getFirst().anima());}
    @Test void swiftSwordPreservesIntentAndFirstUseIsPerSlot(){var r=run(p("A",1000094,100,100,0,0,2,Map.of()),b());assertEquals(92,end(r,1,1).hp());assertEquals(4,end(r,1,0).swordIntent());}
}
