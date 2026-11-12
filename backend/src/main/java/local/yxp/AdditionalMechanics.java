package local.yxp;
import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

final class AdditionalMechanics {
    static final Map<String,String> STATES=Map.ofEntries(
        Map.entry("underworld","冥"),Map.entry("fistDebuffAttack","冥夜"),Map.entry("injuryRecoveryPerTurn","冥心入玄"),
        Map.entry("ringGain","锟铻金环"),Map.entry("forceDamage","盛气凌人"),Map.entry("dragon","龙"),
        Map.entry("nextAttackWeak","背孤"),Map.entry("nextAttackVulnerable","击虚"),Map.entry("gourdChase","天髓葫芦"),
        Map.entry("fistLifeRefund","崩拳返玄"),Map.entry("healingCount","加生命次数"),Map.entry("defenseGainCount","加防次数"),Map.entry("sharpnessGainCount","加锋锐次数"),Map.entry("animaGainCount","加灵气次数"),Map.entry("waterGainCount","加水势次数"),
        Map.entry("resourceFanDamage","星月六爻扇"),Map.entry("fusedRing","锟铻熔火环"),Map.entry("yinDebuffDamage","阴符绝阵"),Map.entry("sealDebuffPercent","阴符玉简负面效果百分比"),Map.entry("birdSwordQi","百鸟曳影诀"),Map.entry("agilityDamage","冯虚御风"),Map.entry("highRaging","狂剑云寂"),Map.entry("temporaryChaseLimit","本回合额外再次行动次数"),Map.entry("inverseMana","混元逆克灵气"),Map.entry("inverseDefense","混元逆克防御"),Map.entry("inverseChase","天髓逆克诀"),Map.entry("nightStep","冥夜迷踪步"),Map.entry("nightStepUsed","本回合冥夜已触发"),Map.entry("extraCardAttack","灵玄不息"),Map.entry("nextCloudRepeat","浮云连环步"),Map.entry("nextThunderRepeat","雷闪二度"),Map.entry("dragonHorse","龙马精神"),Map.entry("basicChase","逍遥古琴普通攻击再次行动"));
    static void resources(List<Change> ch,Fighter f,String key,int old,int value){
        if(key.equals("hp")&&value<old&&!f.capClamping&&f.status("nightStep")>0&&f.status("nightStepUsed")==0){setStatus(ch,f,"nightStepUsed",1);addAnima(ch,f,f.status("nightStep"));addStatus(ch,f,"agility",f.status("nightStep"));}
        if(value>old){if(key.equals("healingAttempt"))addStatus(ch,f,"healingCount",1);if(key.equals("defense"))addStatus(ch,f,"defenseGainCount",1);if(key.equals("anima")){addStatus(ch,f,"animaGainCount",1);int n=(value-old)*f.status("resourceFanDamage");if(n>0)queue(f,()->damage(ch,f.opponent,n));}}
        if(key.equals("anima")&&value<old&&f.status("birdSwordQi")>0)addStatus(ch,f,"swordQi",(old-value)*f.status("birdSwordQi"));
    }
    static void queue(Fighter f,Runnable effect){if(f.resolution!=null)f.resolution.enqueue(effect);else effect.run();}
    static void status(List<Change> ch,Fighter f,String key,int old,int value){
        if(key.equals("hexagram")&&value==0&&f.status("dragonHorse")>0)addStatus(ch,f,"hexagram",1);
        if(value!=old&&CareerEffects.NEGATIVE.contains(key)&&f.hasBase(415))ForgeEffects.physique(ch,f,Math.abs(value-old));
        if(key.equals("starPower")&&value<old&&f.status("purpleStars")>0)addStatus(ch,f,"attackBonus",old-value);
        if(key.equals("underworld")&&old!=value){int n=Math.abs(value-old)*3;if(f.p.characterId()==4000003||SeasonTalentEffects.has(f,27021))heal(ch,f,n);else loseHp(ch,f,n);}
        if(value>old){
            if(key.equals("sharpness"))addStatus(ch,f,"sharpnessGainCount",1);
            if(key.equals("waterForce"))addStatus(ch,f,"waterGainCount",1);
            if(key.equals("hexagram")||key.equals("starPower")){int n=(value-old)*f.status("resourceFanDamage");if(n>0)queue(f,()->damage(ch,f.opponent,n));}
            if(CareerEffects.NEGATIVE.contains(key)&&!key.equals("underworld")){int n=(value-old)*f.status("yinDebuffDamage");if(n>0)queue(f,()->damage(ch,f.opponent,f,n));}
            if(key.equals("agility")){if(f.hasBase(391))addDefense(ch,f,value-old);if(f.status("agilityDamage")>0){int n=(value-old)*f.status("agilityDamage");queue(f,()->damage(ch,f.opponent,n));}}
        }
    }
    static void start(List<Change> ch,Fighter f){setStatus(ch,f,"temporaryChaseLimit",0);int n=f.status("injuryRecoveryPerTurn");addStatus(ch,f,"internalInjury",n);addStatus(ch,f,"recovery",n);}
    static void force(List<Change> ch,Fighter f,int delta,int overflow){int amount=(Math.max(0,-delta)+overflow)*f.status("forceDamage");if(amount>0){if(f.resolution!=null)f.resolution.enqueue(()->damage(ch,f.opponent,amount));else damage(ch,f.opponent,amount);}}
    static void beforeAttack(List<Change> ch,Fighter f){
        var dst=f.opponent;if(dst.status("nextAttackWeak")>0){addStatus(ch,dst,"nextAttackWeak",-1);addStatus(ch,f,"weakness",1);}
        if(f.status("nextAttackVulnerable")>0){addStatus(ch,f,"nextAttackVulnerable",-1);addStatus(ch,dst,"vulnerability",1);}
    }
    static int attackBonus(Fighter f,Rules.Card c){return !f.afterCard&&ForgeEffects.isFist(f,c)?Math.min(f.status("fistDebuffAttack"),StarEffects.debuffs(f)):0;}
    static void afterUse(List<Change> ch,Fighter f,Rules.Card c){
        int element=ElementalEffects.element(c.name());
        if(element>0&&f.status("inverseChase")>0&&controls(f.status("lastElement"),element))f.requestChase=true;
        if(element>0&&f.status(ElementalEffects.activationKey(element))>0&&f.status("gourdChase")>0&&!c.actionAgain()&&!f.requestChase&&f.chasesThisTurn<1+f.status("dragon")){addStatus(ch,f,"gourdChase",-1);f.requestChase=true;}
    }
    static boolean controls(int left,int right){return left>0&&right==((left+1)%5+1);}
    static void before(List<Change> ch,Fighter f,Rules.Card c){int current=ElementalEffects.element(c.name()),next=ElementalEffects.element(f.rules.byId.get(f.cards[StarEffects.next(f,f.slot)]).name());if(f.status("inverseMana")>0&&controls(current,next)){activate(ch,f,ElementalEffects.activationKey(next));addAnima(ch,f,f.status("inverseMana"));addDefense(ch,f,f.status("inverseDefense"));}}
    static void end(List<Change> ch,Fighter f){setStatus(ch,f,"nightStepUsed",0);setStatus(ch,f.opponent,"nightStepUsed",0);if(f.hasBase(441))damage(ch,f.opponent,f.def/4);}
    static List<Integer> extraAttacks(Fighter f){return f.status("extraCardAttack")>0?List.of(f.status("extraCardAttack")+StarEffects.debuffs(f)/2):List.of();}
    static int chaseLimit(Fighter f){return 1+f.status("dragon")+f.status("temporaryChaseLimit");}
}
