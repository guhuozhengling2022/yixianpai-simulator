package local.yxp;

import static local.yxp.BattleTestDecks.allowRepeatedSingleUseCards;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;

class FactionVariantTest {
    final Rules rules=new Rules();final Engine engine=new Engine(rules);
    @Test void everyAcceptedDeterministicVariantRunsWithAndWithoutDependencies(){
        int checked=0;
        for(var card:rules.cards)if(card.supported()&&!card.requiresBattleParams()&&!card.requiresContext()){
            for(boolean active:new boolean[]{false,true}){
                var buffs=active?Map.of("metalActivation",1,"waterActivation",1,"woodActivation",1,"fireActivation",1,"earthActivation",1,"healingTotal",20,"defenseLost",20,"ragingCount",2):Map.<String,Integer>of();
                var a=new Player("A","A",0,200,300,50,20,3,Collections.nCopies(8,card.id()),List.of(),buffs,List.of());
                var b=new Player("B","B",0,300,300,0,0,0,Collections.nCopies(8,1000005),List.of(),Map.of());
                var request=new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(allowRepeatedSingleUseCards(a),b));
                var result=assertDoesNotThrow(()->engine.simulate(request),card.id()+" active="+active);
                assertTrue(result.turns()>=1&&result.turns()<=64,card.id()+"");
                for(var state:result.finalStates())assertTrue(state.defense()>=0&&state.anima()>=0&&state.hp()<=state.maxHp(),card.id()+" "+state);
                checked++;
            }
        }
        assertTrue(checked>=1700,"all grades must be covered");
    }
}
