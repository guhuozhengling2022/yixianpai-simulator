package local.yxp;
import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;
/** Lu Jianxin's sword embryo and the sword-pattern / spirit-casting inheritance. */
final class SwordEmbryoEffects {
    static final Map<String,String> STATES=Map.of("embryoGrowth","澄心剑胚局外累计加攻","embryoNextCloud","云剑之心下张云剑","embryoNextRaging","狂剑之心下张狂剑");
    static boolean has(Fighter f,int id){return TalentEffects.has(f,id);}
    static int p(Fighter f,int id,int index){return f.rules.talentById.get(id).param(index);}
    static boolean applies(Rules.Card c){return c.baseId()==19||c.baseId()==126;}
    static int bonus(Fighter f,Rules.Card c){return c.baseId()==19&&has(f,30096)?1:0;}
    static List<Integer> attacks(Fighter f,Rules.Card c,List<Integer> normal){
        if(c.baseId()!=19)return normal;
        // The cached client value already includes the effects of the chosen forging path.
        boolean hasSnapshot = f.p.buffs() != null && f.p.buffs().containsKey("embryoGrowth");
        int growth = f.status("embryoGrowth");
        if (!hasSnapshot && SeasonTalentEffects.has(f, 7012)) growth = SeasonTalentEffects.param(f, 7012, 0);
        int attack=c.attack()+growth+bonus(f,c);
        if(has(f,10093))attack+=p(f,10093,0);
        if(has(f,20093))attack-=p(f,20093,0);
        if(has(f,30094))attack-=p(f,30094,1);
        if(has(f,10096))attack+=p(f,10096,1);
        if(has(f,20096))attack-=p(f,20096,1);
        return attack>0?List.of(attack):List.of();
    }
    static void defense(List<Change> ch,Fighter f,Rules.Card c){if(c.baseId()==19&&has(f,20093))addDefense(ch,f,p(f,20093,1)+bonus(f,c));}
    static List<Integer> first(Fighter f,Rules.Card c){return applies(c)&&has(f,10094)?List.of(p(f,10094,0)+bonus(f,c)):List.of();}
    static void gains(List<Change> ch,Fighter f,Rules.Card c){if(!applies(c))return;int extra=bonus(f,c);
        if(has(f,20094))addStatus(ch,f,"attackBonus",p(f,20094,0)+extra);
        if(has(f,30094))addSword(ch,f,p(f,30094,0)+extra);
        if(has(f,10095))addAnima(ch,f,p(f,10095,0)+extra);
        if(has(f,20095)&&f.anima>0)addDefense(ch,f,(p(f,20095,0)+extra)*f.anima);
    }
    static List<Integer> last(List<Change> ch,Fighter f,Rules.Card c){if(!applies(c)||!has(f,30095))return List.of();int extra=bonus(f,c);if(f.anima<1+extra)return List.of();addAnima(ch,f,-1-extra);return Collections.nCopies(2+extra,p(f,30095,0)+extra);}
    static void aliases(List<Change> ch,Fighter f,Rules.Card c){if(c.baseId()!=19)return;if(has(f,10096))addStatus(ch,f,"embryoNextRaging",p(f,10096,0));if(has(f,20096))addStatus(ch,f,"embryoNextCloud",p(f,20096,0));}
}
