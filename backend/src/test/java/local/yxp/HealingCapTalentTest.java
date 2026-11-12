package local.yxp;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class HealingCapTalentTest {
 Engine.Fighter fighter(int id){var f=new Engine.Fighter(new Player("A","A",0,60,60,0,0,0,List.of(0,0,0),List.of(id),Map.of()));f.rules=new Rules();f.resolution=new Engine.Resolution();f.resolution.trace=false;f.opponent=new Engine.Fighter(new Player("B","B",0,60,60,0,0,0,List.of(0,0,0),List.of(),Map.of()));f.opponent.rules=f.rules;f.opponent.resolution=f.resolution;f.opponent.opponent=f;return f;}
 @Test void allRealmsGrowBeforeHealingEvenAtFullHp(){for(int id:List.of(120,10120,20120,30120)){var f=fighter(id);int n=f.rules.talentById.get(id).param(0);var changes=new ArrayList<Change>();Engine.heal(changes,f,2);assertEquals(60+n,f.maxHp);assertEquals(62,f.hp);assertTrue(java.util.stream.IntStream.range(0,changes.size()).filter(i->changes.get(i).attribute().equals("maxHp")).findFirst().orElseThrow()<java.util.stream.IntStream.range(0,changes.size()).filter(i->changes.get(i).attribute().equals("hp")).findFirst().orElseThrow());Engine.heal(changes,f,1);assertEquals(60+2*n,f.maxHp);assertEquals(63,f.hp);}}
 @Test void zeroBlockedAndConvertedHealingDoNotGrowCap(){var f=fighter(120);var ch=new ArrayList<Change>();Engine.heal(ch,f,0);assertEquals(60,f.maxHp);f.statuses.put("noHealing",1);Engine.heal(ch,f,3);assertEquals(60,f.maxHp);f.statuses.remove("noHealing");f.statuses.put("healToSharpRounds",1);Engine.heal(ch,f,3);assertEquals(60,f.maxHp);}
 @Test void openingDoesNotGrantPermanentMaxHp(){var f=fighter(120);TalentEffects.opening(new ArrayList<>(),f,f.rules.talentById.get(120));assertEquals(60,f.maxHp);assertEquals(60,f.hp);}
}


