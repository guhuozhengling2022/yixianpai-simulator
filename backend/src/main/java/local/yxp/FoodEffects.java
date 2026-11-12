package local.yxp;
import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Zongzi card bodies and their shared healing/resource callbacks. */
final class FoodEffects {
    static final Map<String,String> STATES=Map.ofEntries(
        Map.entry("appetite","食欲"),Map.entry("indigestion","食滞"),Map.entry("sweetUsed","已用甜粽"),Map.entry("savoryUsed","已用咸粽"),
        Map.entry("sweetMana","碱水粽甜"),Map.entry("savoryHeal","碱水粽咸"),Map.entry("fruitConversion","鲜果宝粽"),
        Map.entry("grainMana","八宝杂粮粽灵气"),Map.entry("grainHeal","八宝杂粮粽生命"),Map.entry("foodUpgrade","咸蛋黄粽升级"),Map.entry("hamHeal","火腿肉粽"),Map.entry("healedChase","天髓珍馐粽"));
    static int buffCount(Fighter f){return f.status("internalInjury")+f.status("recovery")+f.status("appetite")+f.status("indigestion");}
    static int healing(Fighter f,int n){return n>0?Math.max(1,n+f.status("appetite")-f.status("indigestion")):n;}
    static int anima(List<Change> ch,Fighter f,int n){if(n>0&&f.status("grainMana")>0){heal(ch,f,f.status("grainHeal"));return n+f.status("grainMana");}return n;}
    static void gained(List<Change> ch,Fighter f,String key,int old,int value){if(value>old&&f.status("hamHeal")>0&&(key.equals("anima")||CareerEffects.NEGATIVE.contains(key)))heal(ch,f,(value-old)*f.status("hamHeal"));}
    static void afterUse(List<Change> ch,Fighter f,Rules.Card c){if(f.status("healedChase")>0&&f.status("turnHealing")>0&&!f.requestChase&&!c.actionAgain()&&f.chasesThisTurn==0){addStatus(ch,f,"healedChase",-1);f.requestChase=true;}}
    static boolean convert(List<Change> ch,Fighter f,String key,int delta){
        if(delta>0&&!key.equals("underworld")&&CareerEffects.NEGATIVE.contains(key)&&f.status("fruitConversion")>0){addStatus(ch,f,"fruitConversion",-1);addStatus(ch,f,"recovery",delta);return true;}return false;
    }
    static void before(List<Change> ch,Fighter f,Rules.Card c){if(c.subcategory()==8)addAnima(ch,f,f.status("sweetMana"));if(c.subcategory()==9)heal(ch,f,f.status("savoryHeal"));}
    static void history(List<Change> ch,Fighter f,Rules.Card c){if(c.subcategory()==8)addStatus(ch,f,"sweetUsed",1);if(c.subcategory()==9)addStatus(ch,f,"savoryUsed",1);}
    static boolean spend(List<Change> ch,Fighter f){if(f.anima<1)return false;addAnima(ch,f,-1);return true;}
    static void after(List<Change> ch,Fighter f,Rules.Card c){
        if(!c.effect().equals("FOOD"))return;
        int n=c.param(0);
        switch(c.baseId()){
            case 83,113 -> heal(ch,f,n);
            case 84 -> {addAnima(ch,f,c.anima());heal(ch,f,n);}
            case 85 -> {heal(ch,f,n);addStatus(ch,f,"recovery",c.param(1));}
            case 86 -> heal(ch,f,n+f.status("sweetUsed")*c.param(1));
            case 87 -> {addAnima(ch,f,c.anima());heal(ch,f,f.anima);}
            case 88 -> {addAnima(ch,f,c.anima());heal(ch,f,n+f.anima*c.param(1));addStatus(ch,f,"indigestion",c.param(2));}
            case 89 -> {heal(ch,f,n);addStatus(ch,f,"sweetMana",c.param(1));addStatus(ch,f,"savoryHeal",c.param(2));}
            case 90 -> heal(ch,f,n+(buffCount(f)+f.anima)*c.param(1));
            case 91 -> {int count=f.status("internalInjury")+f.status("indigestion");setStatus(ch,f,"internalInjury",0);setStatus(ch,f,"indigestion",0);heal(ch,f,n+count*c.param(1));}
            case 92 -> {heal(ch,f,n);if(f.status("sweetUsed")>0){heal(ch,f,c.param(1)*f.status("sweetUsed"));addAnima(ch,f,c.param(2)*f.status("sweetUsed"));}}
            case 93 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"indigestion",n);}
            case 94 -> {heal(ch,f,n);addStatus(ch,f,"fruitConversion",c.param(1));}
            case 95 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"grainMana",n);addStatus(ch,f,"grainHeal",c.param(1));}
            case 96 -> {int mana=f.anima;addAnima(ch,f,-mana);heal(ch,f,n+c.param(1)*mana);}
            case 97 -> {addStatus(ch,f,"recovery",n);heal(ch,f,c.param(1)+(f.status("recovery")+f.status("appetite"))*c.param(2));}
            case 98 -> {addAnima(ch,f,c.anima());heal(ch,f,n);addStatus(ch,f,"internalInjury",c.param(1));}
            case 99 -> {heal(ch,f,n);if(spend(ch,f))heal(ch,f,c.param(1));}
            case 100 -> {heal(ch,f,n);String key=CareerEffects.selectedDebuff(f);if(key!=null)addStatus(ch,f,key,-c.param(1));}
            case 101 -> {addStatus(ch,f,"appetite",n);heal(ch,f,c.param(1));}
            case 102 -> {addStatus(ch,f,"internalInjury",n);heal(ch,f,c.param(1));heal(ch,f,c.param(1));if(spend(ch,f))heal(ch,f,c.param(2));}
            case 103 -> {addAnima(ch,f,c.anima());heal(ch,f,n);addStatus(ch,f,"repel",c.param(1));}
            case 104 -> {heal(ch,f,n);addStatus(ch,f,"internalInjury",c.param(1));}
            case 105 -> {if(spend(ch,f)){String key=CareerEffects.selectedDebuff(f);if(key!=null){addStatus(ch,f,key,-1);addStatus(ch,f,"appetite",1);}}for(int i=0;i<n;i++)heal(ch,f,c.param(1));}
            case 106 -> {addAnima(ch,f,c.anima());heal(ch,f,n);addStatus(ch,f,"appetite",c.param(1));addStatus(ch,f,"internalInjury",c.param(1));}
            case 107 -> {heal(ch,f,n);addStatus(ch,f,"foodUpgrade",c.param(1));}
            case 108 -> {for(int i=0;i<n;i++)heal(ch,f,c.param(1));}
            case 109 -> {heal(ch,f,n);addStatus(ch,f,"appetite",c.param(1));addStatus(ch,f,"skipUpcoming",c.param(2));}
            case 110 -> {for(int i=0;i<n;i++)heal(ch,f,c.param(1)+(StarEffects.debuffs(f)+f.status("appetite"))*c.param(2));}
            case 111 -> {heal(ch,f,n);addStatus(ch,f,"repeatNextCard",1);}
            case 112 -> {addStatus(ch,f,"internalInjury",n);int count=StarEffects.debuffs(f)/c.param(1);addAnima(ch,f,count);heal(ch,f,count*c.param(2));}
            case 301 -> addStatus(ch,f,"healedChase",n);
            case 302 -> addStatus(ch,f,"appetite",n);
            case 303 -> {addAnima(ch,f,c.anima());heal(ch,f,c.param(1));addStatus(ch,f,"recovery",f.anima/n);}
            case 306 -> {heal(ch,f,c.param(1));addStatus(ch,f,"hamHeal",n);}
            case 393 -> {int total=StarEffects.debuffs(f);for(String key:CareerEffects.NEGATIVE)setStatus(ch,f,key,0);heal(ch,f,total*n);addStatus(ch,f,"recovery",total*c.param(1));}
            case 394 -> {int count=f.status("savoryUsed");for(int i=0;i<count;i++)heal(ch,f,n);}
            case 397 -> {addStatus(ch,f,"agility",n);heal(ch,f,c.param(1)+f.anima*c.param(2));}
            case 398 -> {addAnima(ch,f,c.anima());heal(ch,f,c.param(1));int count=f.anima/n;addStatus(ch,f,"appetite",count);addStatus(ch,f,"internalInjury",count);}
            default -> throw new IllegalStateException("Unaudited food card "+c.id());
        }
    }
}
