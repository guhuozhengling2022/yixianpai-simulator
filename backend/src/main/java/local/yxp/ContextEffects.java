package local.yxp;
import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Explicit snapshots for effects which need more than health and a deck. */
final class ContextEffects {
    static final Map<String,String> STATES=Map.ofEntries(Map.entry("realm","境界（1炼气至6返虚）"),Map.entry("destiny","命元快照"),Map.entry("destinySpent","本场扣除命元"),Map.entry("destinyGained","本场增加命元"),Map.entry("career","副职业编号"),Map.entry("bottleFirst","五行玉瓶第一格卡牌ID"),Map.entry("randomMinimum","策马扬鞭剩余次数"),Map.entry("crystalDefense","结晶鲛珠"),Map.entry("purpleStars","紫芒星爆"),Map.entry("horseSelfDamage","走马观花自身伤害"),Map.entry("horseEnemyDamage","走马观花对方伤害"));
    static final Set<String> BOTTLE_SLOTS = Set.of("bottleFirst", "bottleSecond");
    static int realm(Fighter f){int n=f.status("realm");if(n<1||n>6)throw new Rejected("INVALID_INPUT","此效果需要境界快照：buffs.realm=1至6");return n;}
    static int destiny(Fighter f){if(f.destiny==null)throw new Rejected("INVALID_INPUT","此效果需要双方命元快照：buffs.destiny");return f.destiny;}
    static void destiny(List<Change> ch,Fighter f,int delta){if(delta==0)return;addStatus(ch,f,delta>0?"destinyGained":"destinySpent",Math.abs(delta));if(f.destiny!=null){int old=f.destiny;f.destiny+=delta;ch.add(new Change(f.p.id(),"destiny",old,f.destiny));}}
    static void start(List<Change> ch,Fighter f){if(f.status("horseSelfDamage")>0){int choice=nextParameter(f,f.horseOutcomeSlot,f.horseOutcomeCard);if(choice<0||choice>1)throw new Rejected("INVALID_INPUT","走马观花参数为0（伤敌）或1（伤己）");if(choice==0)damage(ch,f.opponent,f.status("horseEnemyDamage"));else damage(ch,f,f,f.status("horseSelfDamage"));}}
    static void hit(List<Change> ch,Fighter f,int loss){if(loss>0&&f.status("crystalDefense")>0){addDefense(ch,f,loss/2*f.status("crystalDefense"));}}
    static void mirrorHand(List<Change> ch,Fighter f){var next=f.rules.byId.get(f.cards[StarEffects.next(f,f.slot)]);int sect=0;for(var character:f.rules.characters)if(((Number)character.get("id")).intValue()==f.p.characterId())sect=((Number)character.get("sect")).intValue();if((next.sect()>0&&next.sect()==sect)||(next.career()>0&&next.career()==f.status("career"))){if(f.hand==null)throw new Rejected("INVALID_INPUT","镜像鲛珠需要明确手牌；空手牌请传[]");int copy=next.baseId();if(!f.rules.byId.containsKey(copy))throw new Rejected("INVALID_INPUT","未找到镜像鲛珠的1级复制");int old=f.hand.size();f.hand.add(copy);ch.add(new Change(f.p.id(),"handCount",old,f.hand.size()));addStatus(ch,f,"experience",-1);}}
    static Integer handSustain(Fighter f){if(f.hand==null)throw new Rejected("INVALID_INPUT","万象碱水粽需要明确手牌；空手牌请传[]");for(int i=f.hand.size()-1;i>=0;i--){var c=f.rules.byId.get(f.hand.get(i));if(c.cardType()==3)return c.id();}return null;}
    static void activateBottleCard(List<Change> changes, Fighter fighter, String slot) {
        var card = fighter.rules.byId.get(fighter.status(slot));
        if (card == null) return;
        for (int element : new int[]{1, 3, 2, 4, 5}) {
            if (card.name().contains(ElementalEffects.NAMES[element])) {
                activate(changes, fighter, ElementalEffects.activationKey(element));
            }
        }
    }
}
