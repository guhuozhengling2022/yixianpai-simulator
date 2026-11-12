package local.yxp;
import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;
final class StanceEffects {
    static final Map<String,String> STATES=Map.of("stance","架势（1拳，2棍）","parry","拆招");
    static int stance(Fighter f){int n=f.status("stance");if(n!=1&&n!=2)throw new Rejected("INVALID_INPUT","架势牌需要明确架势：buffs.stance=1（拳）或2（棍）");return n;}
    static void switchStance(List<Change> ch,Fighter f){
        int next=stance(f)==1?2:1;
        setStatus(ch,f,"stance",next);
        if(SeasonTalentEffects.has(f,7090))addStatus(ch,f,next==1?"force":"attackBonus",SeasonTalentEffects.param(f,7090,next-1));
        if(SeasonTalentEffects.has(f,7113))damage(ch,f.opponent,SeasonTalentEffects.param(f,7113,0));
    }
    static void before(List<Change> ch,Fighter f,Rules.Card c){if(!c.effect().equals("STANCE"))return;int s=stance(f);if(c.baseId()==222){addStatus(ch,f,"agility",c.param(0));if(s==1)addStatus(ch,f,"agility",c.param(1));}}
    static List<Integer> attacks(Fighter f,Rules.Card c,List<Integer> normal){if(!c.effect().equals("STANCE"))return normal;int s=stance(f);return switch(c.baseId()){
        case 220 -> s==2?Collections.nCopies(Math.max(1,c.attackCount()),c.attack()+f.status("physique")/c.param(2)):List.of();
        case 222 -> s==2?normal:List.of();
        default -> normal;
    };}
    static List<Integer> followups(Fighter f,Rules.Card c){return c.effect().equals("STANCE")&&c.baseId()==219&&stance(f)==2?Collections.nCopies(Math.max(1,c.attackCount()),1+f.status("physique")/c.param(1)):List.of();}
    static void after(List<Change> ch,Fighter f,Rules.Card c){if(!c.effect().equals("STANCE"))return;int s=stance(f);switch(c.baseId()){
        case 219 -> {if(s==1)addDefense(ch,f,f.status("physique")/c.param(0));switchStance(ch,f);}
        case 220 -> {if(s==1){addStatus(ch,f,"force",c.param(0));heal(ch,f,f.status("physique")/c.param(1));}switchStance(ch,f);}
        case 221 -> setStatus(ch,f,"parry",1);
        case 222 -> switchStance(ch,f);
        case 419 -> {var d=f.opponent;addStatus(ch,f,"internalInjury",c.param(0));addStatus(ch,d,"internalInjury",c.param(0));addStatus(ch,f,"weakness",c.param(1));addStatus(ch,d,"weakness",c.param(1));int n=c.param(2)*StarEffects.debuffs(f);if(s==1)heal(ch,f,n);else damage(ch,d,n);switchStance(ch,f);}
        default -> { }
    }}
    static int reduction(Fighter f){return f.status("parry")>0&&f.status("stance")==1?f.rules.byId.get(221).param(0):0;}
    static void attacked(List<Change> ch,Fighter f){if(f.status("parry")>0&&f.status("stance")==2)damage(ch,f,f.opponent,f.rules.byId.get(221).param(1));}
}
