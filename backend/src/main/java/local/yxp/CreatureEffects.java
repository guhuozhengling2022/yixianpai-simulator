package local.yxp;
import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Treasure-creature effects used by the local special-mode card table. */
final class CreatureEffects {
    static final Map<String,String> STATES=Map.ofEntries(
        Map.entry("eagleDamage","破空雕"),Map.entry("batHealLimit","暗星蝙蝠"),Map.entry("turtleDefense","地玄龟"),
        Map.entry("craneManaMultiplier","七彩灵鹤"),Map.entry("beastDrain","吞天赤眼兽"),Map.entry("beastManaDrain","噬灵虚兽"),
        Map.entry("phoenixRevive","浴火凤凰"),Map.entry("catHealing","胖仙狸"),Map.entry("wolfAttack","孤夜狼"),
        Map.entry("rabbitChaseCost","影枭兔"),Map.entry("dogTransform","幽冥虚魂犬"),Map.entry("raccoonRepel","三尾讙"));
    static void start(List<Change> ch,Fighter f){
        int mana=Math.min(f.opponent.anima,f.status("beastManaDrain"));addAnima(ch,f.opponent,-mana);addAnima(ch,f,mana);
        heal(ch,f,f.status("catHealing"));damage(ch,f.opponent,f.status("eagleDamage"));
        loseHp(ch,f.opponent,f.status("beastDrain"));heal(ch,f,f.status("beastDrain"));addDefense(ch,f,f.status("turtleDefense"));
    }
    static void afterHit(List<Change> ch,Fighter f,Rules.Card c,int loss){
        if(loss>0&&loss<=f.status("batHealLimit"))heal(ch,f,loss);
        if(!f.afterCard&&c.baseId()==99000215&&loss>0)addStatus(ch,f.opponent,"internalInjury",c.param(0)+f.status("internalInjury"));
    }
    static void after(List<Change> ch,Fighter f,Rules.Card c){
        if(!c.effect().equals("CREATURE"))return;
        int n=c.param(0);
        switch(c.baseId()){
            case 99000200 -> {damage(ch,f.opponent,n);addStatus(ch,f,"eagleDamage",c.param(1));}
            case 99000201 -> {if(n>f.status("batHealLimit"))setStatus(ch,f,"batHealLimit",n);}
            case 99000202 -> addStatus(ch,f,"turtleDefense",n);
            case 99000203 -> loseHp(ch,f,c.param(1));
            case 99000204 -> {setStatus(ch,f,"craneManaMultiplier",n);addAnima(ch,f,c.anima());}
            case 99000205 -> addStatus(ch,f,"beastDrain",n);
            case 99000206 -> addStatus(ch,f,"beastManaDrain",n);
            case 99000207 -> addStatus(ch,f,"phoenixRevive",n);
            case 99000208 -> {heal(ch,f,n);addStatus(ch,f,"catHealing",c.param(1));}
            case 99000209 -> addStatus(ch,f,"wolfAttack",n);
            case 99000210 -> setStatus(ch,f,"rabbitChaseCost",n);
            case 99000211 -> {damage(ch,f.opponent,n);addStatus(ch,f.opponent,"dogTransform",1);}
            case 99000212 -> addStatus(ch,f,"raccoonRepel",c.param(1));
            case 99000214 -> addAnima(ch,f,c.anima());
            case 99000216 -> {if(!f.copyingCareer.contains(c.baseId()))heal(ch,f,n);}
            default -> { }
        }
    }
    static List<Integer> attacks(Fighter f,Rules.Card c,List<Integer> values){
        if(c.baseId()!=99000213)return values;
        var levels=new HashSet<Integer>();for(int i=0;i<f.cards.length;i++)if(i!=f.slot){int level=f.rules.byId.get(f.cards[i]).level();if(level>=1&&level<=5)levels.add(level);}
        return Collections.nCopies(c.attackCount()+levels.size()+1,c.attack());
    }
}
