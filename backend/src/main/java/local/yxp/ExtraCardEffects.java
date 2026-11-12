package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Additional character cards; each branch follows the matching Card_<base>.cs. */
final class ExtraCardEffects {
    static final Set<Integer> RANDOM_COPIES=Set.of(64,352,353,354,355,358,359,360,361,362,363,364,365);
    static int copies(Rules.Card c){return c.effect().equals("EXTRA")&&RANDOM_COPIES.contains(c.baseId())?c.param(0):0;}
    static Rules.Card selected(Fighter f,Rules.Card c,int id){
        if(id==-1&&c.baseId()!=64)return null;
        var selected=f.rules.byId.get(id);
        if(selected==null||!selected.supported())throw new Rejected("UNSUPPORTED","随机参数指定的卡牌尚未支持："+id);
        // Parameters are recorded outcomes, not a locally invented random card pool.
        if(f.status("randomMinimum")>0){int minimum=Math.max(1,ContextEffects.realm(f)-1);if(selected.level()<minimum)throw new Rejected("INVALID_INPUT","策马扬鞭参数卡牌低于允许境界");addStatus(new ArrayList<>(),f,"randomMinimum",-1);}
        return selected;
    }
    static final Map<String,String> STATES=Map.of("rainWaterPerTurn","春雨水势","catCloudAnima","灵猫踏云","plateHexPerTurn","无极卦盘","jadeAnimaPerTurn","聚灵鲛珠","heavenPlate","天元无极盘","growthAttackPerTurn","万物生长");
    static int activeCount(Fighter f){int n=0;for(int i=1;i<=5;i++)if(f.status(ElementalEffects.activationKey(i))>0)n++;return n;}
    static void start(List<Change> ch,Fighter f){
        addAnima(ch,f,f.status("jadeAnimaPerTurn"));
        addStatus(ch,f,"hexagram",f.status("plateHexPerTurn"));
        addStatus(ch,f,"hexagram",f.status("heavenPlate"));
        addStatus(ch,f,"starPower",f.status("heavenPlate"));
        addStatus(ch,f,"waterForce",f.status("rainWaterPerTurn"));
        addStatus(ch,f,"attackBonus",f.status("growthAttackPerTurn"));
    }
    static List<Integer> attacks(List<Change> ch,Fighter f, Rules.Card c, List<Integer> original) {
        if (!c.effect().equals("EXTRA")) return original;
        int bonus = switch (c.baseId()) {
            case 3 -> CloudEffects.linked(f) ? f.cloud*c.param(0) : 0;
            case 5 -> StarEffects.debuffs(f.opponent)*c.param(0);
            case 10 -> f.status("starPower")>0 ? c.param(0) : 0;
            case 40 -> f.opponent.status("internalInjury")*c.param(0);
            case 130 -> f.status("starPower")*c.param(0);
            case 382 -> f.anima;
            case 127 -> f.status("healingTotal")/c.param(0);
            case 373 -> f.status("healingCount")*c.param(0);
            case 374 -> f.opponent.status("maxHpLossCount")*c.param(0);
            case 375 -> f.status("defenseGainCount")*c.param(0);
            case 376 -> f.status("sharpnessGainCount")*c.param(0);
            case 377 -> (f.status("animaGainCount")+f.status("waterGainCount"))*c.param(0);
            case 413 -> f.anima/2;
            case 43 -> ContextEffects.destiny(f)<ContextEffects.destiny(f.opponent)?c.param(0):0;
            case 138 -> f.status("force")*c.param(1);
            case 99000107 -> f.opponent.def/c.param(0);
            case 99000108 -> f.hp<c.param(0)?c.attack():0;
            case 18 -> f.metalActive()?f.status("sharpness")*c.param(0):0;
            case 21 -> {int n=f.earthActive()?(f.def+1)/2:0;addDefense(ch,f,-n);yield n*c.param(0);}
            default -> 0;
        };
        if(c.baseId()==9){if(f.hand==null)throw new Rejected("INVALID_INPUT","灵猫乱剑需要明确提供手牌；空手牌请传[]");return Collections.nCopies(Math.max(1,c.attackCount())+Math.min(c.param(0),f.hand.size()),c.attack());}
        if(c.baseId()==77)return Collections.nCopies(Math.max(1,c.attackCount())+f.opponent.status("externalInjury"),c.attack());
        if(c.baseId()==367){var rolls=new ArrayList<Integer>();for(int i=0;i<Math.max(1,c.attackCount());i++)rolls.add(StarEffects.roll(ch,f,c.attack(),c.randomAttack()));return rolls;}
        if(c.baseId()==368){var values=new ArrayList<>(original);if(CareerEffects.parameter(f,0,99)<c.param(0))values.add(c.param(1));return values;}
        if(c.baseId()==125)return Collections.nCopies(Math.max(1,c.attackCount())+f.status("ragingCount"),c.attack());
        if(c.baseId()==371||c.baseId()==372){int n=0;for(int i=1;i<=5;i++)n+=f.status(ElementalEffects.activationKey(i));return Collections.nCopies(Math.max(1,c.attackCount())+n,c.attack());}
        if(c.baseId()==456)return Collections.nCopies(Math.max(1,c.attackCount())+(StarEffects.debuffs(f.opponent)>0?1:0),c.attack());
        if(c.baseId()==123)return Collections.nCopies(Math.max(1,c.attackCount())+(CloudEffects.linked(f)?f.cloud:0),c.attack());
        if(c.baseId()==213){int count=(int)Arrays.stream(f.cards).filter(id->f.isFormation(f.rules.byId.get(id))).count();return Collections.nCopies(Math.max(1,c.attackCount())+Math.min(c.param(0),count),c.attack());}
        return original.stream().map(n -> n+bonus).toList();
    }
    static void before(List<Change> ch,Fighter f,Rules.Card c) {
        // The attack plan must observe the star before it is consumed.
        if(c.effect().equals("EXTRA")&&c.baseId()==10&&f.status("starPower")>0)
            addStatus(ch,f,"starPower",-1);
    }
    static void prepare(List<Change> ch,Fighter f,Rules.Card c){if(!c.effect().equals("EXTRA"))return;switch(c.baseId()){
        case 382 -> addAnima(ch,f,f.status("physique")/c.param(0));
        case 311 -> {int n=f.sword;addSword(ch,f,-n);addStatus(ch,f,"attackBonus",n);}
        case 413 -> activate(ch,f,"fireActivation");
        case 383 -> ContextEffects.realm(f);
        case 99000105 -> ContextEffects.destiny(f.opponent);
        case 138 -> {int types=0;for(String key:CareerEffects.NEGATIVE)if(f.status(key)>0)types++;addStatus(ch,f,"force",types*c.param(0));}
        case 82 -> {ForgeEffects.physique(ch,f,c.physique());int count=0;for(int i=0;i<f.status("physique")/c.param(0);i++){String key=CareerEffects.selectedDebuff(f);if(key==null)break;addStatus(ch,f,key,-1);count++;}addStatus(ch,f,"attackBonus",count);}
        default -> { }
    }}
    static boolean ignore(Fighter f,Rules.Card c){return c.effect().equals("EXTRA")&&!f.afterCard&&(c.baseId()==8||c.baseId()==125||c.baseId()==424);}
    static boolean suppressSharp(Fighter f,Rules.Card c){return c.effect().equals("EXTRA")&&c.baseId()==18&&f.metalActive()&&!f.afterCard;}
    static void hit(List<Change> ch,Fighter f,Rules.Card c){if(c.effect().equals("EXTRA")&&!f.afterCard){if(c.baseId()==76)addStatus(ch,f.opponent,"externalInjury",c.param(0));if(c.baseId()==99000110)addStatus(ch,f.opponent,"vulnerability",1);}}
    static void after(List<Change> ch,Fighter f,Fighter dst,Rules.Card c) {
        if(!c.effect().equals("EXTRA"))return;
        if(!Set.of(37,215,438).contains(c.baseId()))addDefense(ch,f,c.defense());
        switch(c.baseId()) {
            case 2 -> {if(f.woundedHits>0)addStatus(ch,dst,"externalInjury",c.param(0));if(f.status("ragingCount")>0)f.requestChase=true;}
            case 7 -> {addStatus(ch,f,"hexagram",c.hexagram());loseHp(ch,dst,c.param(0));addAnima(ch,dst,-c.param(1));}
            case 6 -> addStatus(ch,dst,"internalInjury",StarEffects.roll(ch,f,c.param(0),c.param(1)));
            case 436 -> {for(int i=0;i<c.param(0);i++)addStatus(ch,dst,"internalInjury",StarEffects.roll(ch,f,c.param(1),c.param(2)));}
            case 12 -> {addAnima(ch,f,c.anima());if(c.rarity()==0){if(StarEffects.posterior(f))f.requestChase=true;}else{loseHp(ch,f,c.param(1));if(StarEffects.posterior(f))heal(ch,f,c.param(0));}}
            case 13 -> {heal(ch,f,c.param(0));addAnima(ch,f,c.anima());if(activeCount(f)>0)f.requestChase=true;}
            case 14 -> {if(activeCount(f)==5){addStatus(ch,f,"guard",c.param(1));addStatus(ch,f,"attackBonus",c.param(0));}}
            case 15 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"recovery",c.param(0));if(CloudEffects.linked(f)){int n=Math.min(f.status("recovery"),c.param(1));addStatus(ch,f,"recovery",-n);addStatus(ch,f,"attackBonus",n);}}
            case 17 -> {modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addStatus(ch,f,"rainWaterPerTurn",c.param(1));}
            case 20 -> {addAnima(ch,f,c.anima());activate(ch,f,"woodActivation");}
            case 22 -> {StarEffects.nextStars(ch,f,1);addStatus(ch,f,"starPower",c.param(0));addAnima(ch,f,c.anima());modifyMaxHp(ch,f,c.param(1));heal(ch,f,c.param(1));addStatus(ch,f,"hexagram",c.hexagram());}
            case 26 -> {addStatus(ch,f,"catCloudAnima",1);addAnima(ch,f,c.anima());}
            case 28 -> {addStatus(ch,f,"plateHexPerTurn",1);addStatus(ch,f,"hexagram",c.param(0));}
            case 29 -> {addAnima(ch,f,c.anima());addStatus(ch,dst,"vulnerability",c.param(0));for(int i=0;i<f.cards.length;i++)if(i!=f.slot&&f.rules.byId.get(f.cards[i]).name().contains("雷"))f.requestChase=true;}
            case 36 -> {if(CloudEffects.linked(f))addStatus(ch,dst,"vulnerability",c.param(0));}
            case 37 -> addDefense(ch,f,c.defense()+f.sword*c.param(0));
            case 39 -> addStatus(ch,dst,"internalInjury",c.param(0));
            case 44 -> addStatus(ch,f,"agility",c.param(0));
            case 45 -> {heal(ch,f,c.param(0));ForgeEffects.physique(ch,f,c.physique());}
            case 63 -> {modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addStatus(ch,dst,"weakness",c.param(1));}
            case 68 -> {addStatus(ch,dst,"vulnerability",c.param(1));addStatus(ch,f,"guard",c.param(0));}
            case 65 -> {addStatus(ch,f,"jadeAnimaPerTurn",c.param(0));addAnima(ch,f,c.anima());}
            case 71 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"externalInjury",c.param(0));}
            case 72 -> addStatus(ch,f,"internalInjury",c.param(0));
            case 80 -> addStatus(ch,f,"repel",c.param(0));
            case 424 -> addStatus(ch,f,"agility",c.param(0));
            case 425 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"agility",c.param(0));if(StarEffects.posterior(f))heal(ch,f,c.param(1));}
            case 428 -> {addStatus(ch,f,"waterForce",c.param(0));addStatus(ch,f,"rainWaterPerTurn",c.param(1));}
            case 440 -> {addAnima(ch,f,c.anima());ForgeEffects.physique(ch,f,c.physique());addStatus(ch,f,"agility",c.param(0));}
            case 381 -> {addAnima(ch,f,c.anima());ForgeEffects.physique(ch,f,c.physique());addStatus(ch,f,"agility",c.param(0));}
            case 137 -> {addStatus(ch,f,"force",c.param(1));ForgeEffects.physique(ch,f,c.physique());heal(ch,f,c.param(0));addStatus(ch,f,"agility",c.param(2));}
            case 437 -> ForgeEffects.physique(ch,f,c.physique()+f.status("hpLost")*c.param(0)/100);
            case 359 -> addStatus(ch,f,"agility",c.param(1));
            case 329 -> setHp(ch,dst,1);
            case 24 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"nextPosterior",c.param(0));}
            case 30 -> addStatus(ch,f,"activationDamage",c.param(0));
            case 43 -> {if(f.hp<dst.hp)heal(ch,f,c.param(1));if(f.status("experience")<dst.status("experience"))addStatus(ch,f,"agility",c.param(2));}
            case 60 -> {if(!f.copyingCareer.contains(60))ContextEffects.destiny(ch,f,-c.param(0));}
            case 61 -> {ContextEffects.destiny(ch,f,-c.param(0));addStatus(ch,dst,"internalInjury",c.param(1));addStatus(ch,dst,"binding",c.param(2));}
            case 62 -> {ContextEffects.destiny(ch,f,-c.param(0));addStatus(ch,f,"attackBonus",c.param(1));addStatus(ch,f,"externalInjury",c.param(2));}
            case 66 -> addStatus(ch,f,"crystalDefense",1);
            case 67 -> ContextEffects.mirrorHand(ch,f);
            case 69 -> {ContextEffects.destiny(ch,f,-c.param(0));addStatus(ch,f,"experience",c.param(1));}
            case 356 -> {f.horseOutcomeSlot=f.slot;f.horseOutcomeCard=c.id();addStatus(ch,f,"horseSelfDamage",c.param(0));addStatus(ch,f,"horseEnemyDamage",c.param(1));}
            case 383 -> {int realm=ContextEffects.realm(f);ForgeEffects.physique(ch,f,c.physique());addStatus(ch,f,"agility",c.param(0));damage(ch,dst,(f.status("agility")/2)*(realm>=5?2:1));}
            case 390 -> {if(!f.copyingCareer.contains(390)){modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));}}
            case 395 -> {heal(ch,f,c.param(0));addStatus(ch,f,"agility",c.param(1));}
            case 417 -> {addAnima(ch,f,c.anima());int count=0;for(int i=0;i<f.status("physique")/c.param(0);i++){String key=CareerEffects.selectedDebuff(f);if(key==null)break;addStatus(ch,f,key,-1);count++;}if(count>0)addAnima(ch,f,count*2);ForgeEffects.physique(ch,f,f.anima);}
            case 422 -> {addStatus(ch,f,"starPower",c.param(0));addStatus(ch,f,"purpleStars",1);}
            case 423,432 -> addAnima(ch,f,c.anima());
            case 433,99000101 -> {heal(ch,f,c.param(0));ContextEffects.destiny(ch,f,c.param(1));}
            case 99000105 -> {ContextEffects.destiny(ch,dst,-c.param(0));if(ContextEffects.destiny(dst)<=0){setHp(ch,dst,0);setStatus(ch,dst,"reviveBlocked",1);}}
            case 99000113 -> {CareerEffects.both(ch,f,"freeAttackSong",c.param(0));CareerEffects.both(ch,f,"basicChase",1);}
            case 122 -> {if(f.woundedHits>0)addStatus(ch,f,"temporaryUpgrades",c.param(0));if(CloudEffects.linked(f))f.requestChase=true;}
            case 125 -> addStatus(ch,f,"ragingCount",1);
            case 126 -> {int n=(int)Arrays.stream(f.cards).filter(id->f.isFormation(f.rules.byId.get(id))).count();if(n>=c.param(0))f.requestChase=true;}
            case 129 -> {addStatus(ch,dst,"internalInjury",c.param(0));for(int i=0;i<c.param(1);i++)if(StarEffects.roll(ch,f,0,99)<c.param(2)&&dst.status("internalInjury")>0)loseHp(ch,dst,dst.status("internalInjury"));}
            case 131 -> {addStatus(ch,f,"starPower",c.param(1));addStatus(ch,f,"resourceFanDamage",c.param(0));}
            case 133 -> {addStatus(ch,f,"inverseMana",c.param(0));addStatus(ch,f,"inverseDefense",c.param(1));}
            case 135 -> {activate(ch,f,"fireActivation");activate(ch,f,"earthActivation");addStatus(ch,f,"fusedRing",c.param(0));}
            case 139 -> {addStatus(ch,f,"underworld",c.param(0));addStatus(ch,f,"agility",c.param(1));addStatus(ch,f,"nightStep",1);}
            case 140 -> {heal(ch,f,c.param(0));addStatus(ch,f,"internalInjury",c.param(1));addStatus(ch,f,"recovery",c.param(1));addStatus(ch,f,"fistLifeRefund",1);}
            case 214 -> {modifyMaxHp(ch,f,c.param(3));heal(ch,f,c.param(3));addStatus(ch,dst,"weakness",c.param(0));addStatus(ch,dst,"vulnerability",c.param(1));setStatus(ch,dst,"sealDebuffPercent",c.param(2));}
            case 215 -> {addAnima(ch,f,c.anima());if(c.rarity()==0)heal(ch,f,c.param(0));else if(c.rarity()==1)addDefense(ch,f,c.defense());else if(c.rarity()==2){addStatus(ch,dst,"internalInjury",f.anima);addAnima(ch,f,-(f.anima+1)/2);}}
            case 331 -> addStatus(ch,f,"universalNext",c.param(CloudEffects.linked(f)?1:0));
            case 357 -> {ContextEffects.realm(f);addStatus(ch,f,"randomMinimum",c.param(0));}
            case 370 -> {if(f.status("physique")<f.status("physiqueCap"))ForgeEffects.physique(ch,f,f.status("physiqueCap")-f.status("physique"));if(f.status("hexagram")==0)addStatus(ch,f,"hexagram",c.param(0));addStatus(ch,f,"dragonHorse",c.param(0));}
            case 373 -> {if(f.woodActive()&&f.woundDamage>0)heal(ch,f,f.woundDamage/c.param(1));}
            case 374 -> {if(f.fireActive()&&f.woundDamage>0){int n=f.woundDamage/c.param(1);modifyMaxHp(ch,dst,-n);loseHp(ch,dst,n);}}
            case 375 -> {if(f.earthActive()&&f.woundDamage>0)addDefense(ch,f,f.woundDamage/c.param(1));}
            case 376 -> {if(f.metalActive()&&f.woundDamage>0)addStatus(ch,f,"sharpness",f.woundDamage/c.param(1));}
            case 377 -> {if(f.waterActive()&&f.woundDamage>0)addStatus(ch,f,"waterForce",f.woundDamage/c.param(1));}
            case 388 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"birdSwordQi",1);}
            case 389 -> {addAnima(ch,f,c.anima());if(StarEffects.starred(f)){addStatus(ch,dst,"weakness",c.param(0));addStatus(ch,dst,"vulnerability",c.param(1));}}
            case 391 -> addStatus(ch,f,"agility",c.param(0));
            case 392 -> {heal(ch,f,c.param(0));addStatus(ch,f,"agility",c.param(1));addStatus(ch,f,"agilityDamage",1);}
            case 400 -> {modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addStatus(ch,f,"highRaging",1);}
            case 401 -> {if(f.woundedHits>0){addStatus(ch,dst,"externalInjury",c.param(0));f.requestChase=true;}}
            case 402 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"cloudSea",c.param(0));if(f.slot%2==0)addStatus(ch,f,"nextCloudRepeat",1);else addStatus(ch,f,"agility",c.param(1));}
            case 403 -> {modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addStatus(ch,f,"cloudAfterAttack",c.param(1));}
            case 405 -> {addAnima(ch,f,c.anima());modifyMaxHp(ch,f,c.param(0));heal(ch,f,f.status("starPower")+f.anima);if(StarEffects.posterior(f)||StarEffects.starred(f))f.requestChase=true;}
            case 407 -> {addStatus(ch,f,"hexagram",c.hexagram());addStatus(ch,f,"nextThunderRepeat",1);}
            case 413 -> {if(f.woundDamage>0)modifyMaxHp(ch,dst,-f.woundDamage*c.param(0));}
            case 415 -> addStatus(ch,f,"underworld",c.param(0));
            case 416 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"agility",c.param(1));addStatus(ch,f,"extraCardAttack",c.param(0));}
            case 418 -> {addStatus(ch,f,"agility",c.param(0));addStatus(ch,f,"forceDamage",c.param(1));}
            case 429 -> {addStatus(ch,dst,"weakness",c.param(0));addStatus(ch,dst,"yinDebuffDamage",c.param(1));}
            case 430 -> damage(ch,dst,c.param(0));
            case 431 -> modifyMaxHp(ch,f,c.param(0));
            case 434 -> heal(ch,f,c.param(0));
            case 435 -> addStatus(ch,f,"temporaryChaseLimit",1);
            case 441 -> activate(ch,f,"earthActivation");
            case 446 -> {if(StarEffects.posterior(f)){addStatus(ch,dst,"weakness",1);f.requestChase=true;}}
            case 447 -> {addAnima(ch,f,c.anima());int n=ElementalEffects.elements(f,f.rules)*c.param(0);modifyMaxHp(ch,f,n);heal(ch,f,n);}
            case 448 -> addStatus(ch,f,"temporaryUpgrades",c.param(0));
            case 455 -> {addAnima(ch,f,c.anima());var types=new HashSet<Integer>();for(int id:f.cards){int e=ElementalEffects.element(f.rules.byId.get(id).name());if(e>0)types.add(e);}for(int e:types)activate(ch,f,ElementalEffects.activationKey(e));if(types.size()==2)addStatus(ch,f,"inverseChase",1);}
            case 456 -> {if(StarEffects.starred(f))addStatus(ch,dst,"binding",c.param(0));}
            case 32,438 -> {addAnima(ch,f,c.anima());if(c.baseId()==438)addDefense(ch,f,c.defense());if(c.baseId()==438)addStatus(ch,f,"agility",c.param(0));int left=ElementalEffects.element(f.rules.byId.get(f.cards[StarEffects.previous(f,f.slot)]).name()),right=ElementalEffects.element(f.rules.byId.get(f.cards[StarEffects.next(f,f.slot)]).name());if(left>0&&right==((left+1)%5+1)){activate(ch,f,ElementalEffects.activationKey(right));if(c.baseId()==32)f.requestChase=true;}}
            case 38,426 -> {activate(ch,f,"earthActivation");activate(ch,f,"metalActivation");addStatus(ch,f,"ringGain",c.param(0));}
            case 41 -> {addStatus(ch,f,"repel",c.param(0));addStatus(ch,dst,"skipUpcoming",1);}
            case 70 -> {addStatus(ch,f,"force",c.param(0));addStatus(ch,f,"forceDamage",c.param(1));}
            case 73 -> {addStatus(ch,f,"underworld",c.param(0));for(int i=0;i<c.param(1);i++){String key=CareerEffects.selectedDebuff(f);if(key!=null)addStatus(ch,f,key,1);}ForgeEffects.physique(ch,f,Math.min(c.param(2),c.physique()+StarEffects.debuffs(f)));}
            case 74 -> {addStatus(ch,f,"underworld",c.param(0));addStatus(ch,f,"fistDebuffAttack",c.param(1));}
            case 75 -> {ForgeEffects.physique(ch,f,c.physique());addStatus(ch,f,"underworld",c.param(0));addStatus(ch,f,"agility",c.param(1));}
            case 79 -> {addStatus(ch,f,"attackBonus",c.param(0));addStatus(ch,f,"guard",c.param(1));addStatus(ch,f,"dragon",c.param(2));}
            case 81,439 -> {ForgeEffects.physique(ch,f,c.physique());if(c.baseId()==439){addStatus(ch,f,"injuryRecoveryPerTurn",c.param(0));addStatus(ch,f,"agility",c.param(1));}else addStatus(ch,f,"injuryRecoveryPerTurn",c.param(1));}
            case 123 -> {if(CloudEffects.linked(f))addAnima(ch,f,f.cloud);}
            case 124 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"guard",c.param(1));addStatus(ch,f,"spiritDiscount",c.param(0));}
            case 127 -> {addAnima(ch,f,c.anima());if(StarEffects.posterior(f)){if(c.rarity()==0)f.requestChase=true;else heal(ch,f,c.param(1));}}
            case 132 -> {addAnima(ch,f,c.anima());modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addStatus(ch,f,"gourdChase",c.param(1));}
            case 213 -> {if(f.woundedHits>0)addDefense(ch,f,f.woundedHits*c.param(1));}
            case 216 -> addStatus(ch,f,"nextAttackWeak",1);
            case 217 -> addStatus(ch,f,"nextAttackVulnerable",1);
            case 218 -> {int amount=c.param(0)+f.status("woodActivation")*2;modifyMaxHp(ch,f,amount);heal(ch,f,amount);addStatus(ch,f,"guard",c.param(1));addStatus(ch,dst,"guard",c.param(1));CareerEffects.cleanse(ch,f,c.param(2));CareerEffects.cleanse(ch,dst,c.param(2));addStatus(ch,f,"skipTurns",1);addStatus(ch,dst,"skipTurns",1);}
            case 128 -> {StarEffects.nextStars(ch,f,c.param(0));addStatus(ch,f,"heavenPlate",1);}
            case 130 -> addStatus(ch,f,"starPower",-(f.status("starPower")+1)/2);
            case 134 -> {modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addStatus(ch,f,"growthAttackPerTurn",c.param(1));if(f.woodActive())f.requestChase=true;}
            case 136 -> {activate(ch,f,"waterActivation");activate(ch,f,"fireActivation");addStatus(ch,f,"waterForce",c.param(0));}
            case 258 -> {modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addAnima(ch,f,c.anima());if(activeCount(f)>0)f.requestChase=true;}
            case 280 -> addStatus(ch,dst,"skipUpcoming",c.param(0));
            case 281 -> addStatus(ch,f,"guard",c.param(0));
            case 282 -> addStatus(ch,dst,"internalInjury",c.param(0));
            case 283 -> {addStatus(ch,dst,"weakness",c.param(0));addStatus(ch,dst,"vulnerability",c.param(1));}
            case 330 -> {damage(ch,dst,c.param(0));addStatus(ch,dst,"internalInjury",c.param(1));addStatus(ch,dst,"externalInjury",c.param(1));}
            case 332 -> {addAnima(ch,f,c.anima());damage(ch,dst,f.anima*c.param(0));addStatus(ch,f,"nextDefense",f.anima*c.param(1));}
            case 399 -> heal(ch,f,f.maxHp*c.param(0)/100);
            case 99000100 -> addDefense(ch,f,f.def);
            case 99000102 -> {modifyMaxHp(ch,dst,-c.param(0));loseHp(ch,dst,c.param(0));addStatus(ch,dst,"internalInjury",c.param(1));}
            case 99000103 -> {damage(ch,dst,c.param(0));addStatus(ch,dst,"weakness",c.param(1));}
            case 99000104 -> {setStatus(ch,dst,"guard",0);addDefense(ch,dst,-dst.def);damage(ch,dst,c.param(0));}
            case 99000106 -> addStatus(ch,f,"guard",c.param(0));
            case 99000109 -> {modifyMaxHp(ch,f,c.param(0));heal(ch,f,c.param(0));addStatus(ch,f,"recovery",c.param(1));}
            case 99000111 -> {damage(ch,dst,c.param(0));addStatus(ch,dst,"skipTurns",c.param(1));}
            case 99000112 -> {loseHp(ch,f,(f.hp*c.param(0)+99)/100);addStatus(ch,f,"attackBonus",c.param(1));}
            case 99000114 -> {if(f.woundedHits>0)addStatus(ch,dst,"binding",c.param(0));}
            default -> { }
        }
    }
}
