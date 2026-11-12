package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Seven Stars sect, reconstructed from card bodies and shared combat callbacks. */
final class StarEffects {
    static final Map<String,String> STATES=new LinkedHashMap<>();
    static {
        String[][] names={{"fanDiscount","星月折扇星位减耗"},{"hexagram","卦象"},{"starPower","星力"},{"starMask","星位掩码"},{"reverseDirection","反转出牌方向"},{"hexagramTriggered","卦象触发次数"},{"hexagramLost","累计失去卦象"},{"hexagramGained","累计获得卦象"},{"animaToHexagram","灵卦术"},{"animaHeal","静气心法"},{"hexagramDamage","六爻绝阵"},{"resourceDefense","梦两仪加防"},{"resourceHeal","梦两仪治疗"},{"overflowGrowth","生机绽放"},{"overflowDamageRounds","灵元外放回合"},{"reflectAttack","反震心法"},{"reflectRounds","梦反震回合"},{"injuryOnBothAttack","摘花飞叶"},{"soulInjuryCharges","伤魂咒阵次数"},{"attackInjuryRounds","攻击施内伤回合"},{"otherDebuffInjury","幻流水无情"},{"injuryAmplifyRounds","幻落花有意回合"},{"snakeShadow","蛇影"},{"posteriorAlways","先发制人"},{"nextPosterior","下次后招直接触发"},{"posteriorUsed","已使用后招牌次数"},{"posteriorExtraAttack","黄雀在后追加攻击"},{"posteriorBirdAttack","灵雀在后追加攻击"},{"posteriorDiscount","后招牌灵气减耗"},{"starAfterAttack","幻斗转追加攻击"},{"starAfterNext","星弈后增加星位"},{"animaToStarLimit","加灵气转星力剩余额度"},{"nonStarGains","梦星罗非星位次数"},{"nonStarPermanent","梦星罗非星位常驻"},{"nonStarTriggered","本回合梦星罗已触发"},{"starLinkCharges","星弈连次数"},{"snakeCardsUsed","已使用蛇牌次数"},{"dreamWoodUses","梦枯木次数"},{"dreamHexRefund","梦御雷每回合返卦"},{"hexRefundUsed","本回合返卦已触发"},{"reviveWheel","九天流光轮复活次数"},{"invertLifeLoss","移花接木次数"},{"repeatNextCard","梅开二度"},{"springThunder","春雷霹雳曲伤害"},{"dualFormation","两仪金刚阵次数"},{"phantomGrowth","幻生机绽放"},{"skipUpcoming","下次出牌前跳过次数"},{"turnHealing","本回合累计治疗"},{"thunderPercent","雷霆心法百分比"}};
        for(var n:names)STATES.put(n[0],n[1]);
    }
    static final class Frame {
        final boolean posterior;final int startingHexTriggers;int rolledDefense=-1;boolean genericHexApplied;
        Frame(Fighter f,Rules.Card c){posterior=f.used[f.slot]||f.status("posteriorAlways")>0||(isPosterior(c)&&f.status("nextPosterior")>0);startingHexTriggers=f.status("hexagramTriggered");}
    }
    static boolean isStar(Rules.Card c){return c.sect()==2;}
    static boolean isPosterior(Rules.Card c){return c.desc().contains("[后招]：");}
    static boolean posterior(Fighter f){return f.starFrame!=null&&f.starFrame.posterior;}
    static boolean starred(Fighter f){return f.status("dragonHorse")>0||(f.status("starMask")&(1<<f.slot))!=0;}
    static int direction(Fighter f){return f.status("reverseDirection")>0?-1:1;}
    static int next(Fighter f,int slot){return Math.floorMod(slot+direction(f),f.cards.length);}
    static int previous(Fighter f,int slot){return Math.floorMod(slot-direction(f),f.cards.length);}
    static int advance(Fighter f,int slot,int skip,int back){
        if(back>0){for(int i=0;i<Math.min(8,back);i++){slot=previous(f,slot);for(int j=0;j<7&&f.consumed[slot];j++)slot=previous(f,slot);}return slot;}
        slot=next(f,slot);for(int i=0;i<Math.min(8,skip);i++){for(int j=0;j<7&&f.consumed[slot];j++)slot=next(f,slot);slot=next(f,slot);}return slot;
    }
    static int debuffs(Fighter f){int total=0;for(String key:CareerEffects.NEGATIVE)total+=f.status(key);return total;}
    static void addStar(List<Change> ch,Fighter f,int slot){int bit=1<<slot;if((f.status("starMask")&bit)!=0)addAnima(ch,f,1);else setStatus(ch,f,"starMask",f.status("starMask")|bit);}
    static void nextStars(List<Change> ch,Fighter f,int count){int slot=f.slot;for(int i=0;i<Math.min(8,count);i++){slot=next(f,slot);addStar(ch,f,slot);}}
    static void reverse(List<Change> ch,Fighter f){setStatus(ch,f,"reverseDirection",f.status("reverseDirection")==0?1:0);}
    static boolean guaranteed(Fighter f){return f.status("purpleStars")>0&&f.status("starPower")>0||f.status("hexagram")>0;}
    static void consumeGuarantee(List<Change> ch,Fighter f){
        if(f.status("purpleStars")>0&&f.status("starPower")>0)addStatus(ch,f,"starPower",-1);
        else {addStatus(ch,f,"hexagram",-1);addStatus(ch,f,"hexagramTriggered",1);}
    }
    static int randomValue(Fighter f,int min,int max){
        if(hasParameter(f)){int value=nextParameter(f);if(value<min||value>max)throw new Rejected("INVALID_INPUT","七星随机结果需在"+min+"至"+max+"之间");return value;}
        f.resolution.randomUsed = true;
        int value=f.resolution.random.nextInt(max-min+1)+min;
        emit(f.resolution.log,f.resolution.fighters,f.resolution.turn,f,null,f.activeCard,"RANDOM_RESULT","七星随机结果 ["+min+", "+max+"]："+value,List.of());return value;
    }
    static int roll(List<Change> ch,Fighter f,int min,int max){
        if(guaranteed(f)){consumeGuarantee(ch,f);return max;}
        return randomValue(f,min,max);
    }
    static boolean chance(List<Change> ch,Fighter f,int percent){
        if(guaranteed(f)){consumeGuarantee(ch,f);return true;}
        return randomValue(f,0,99)<percent;
    }
    static void initialize(List<Change> ch,Fighter f){for(int slot=0;slot<f.cards.length;slot++){var c=f.rules.byId.get(f.cards[slot]);if(c.baseId()==4000079){for(int adjacent:new int[]{previous(f,slot),next(f,slot)}){int bit=1<<adjacent;if((f.status("starMask")&bit)==0)setStatus(ch,f,"starMask",f.status("starMask")|bit);}}}}
    static void before(List<Change> ch,Fighter f,Rules.Card c,boolean global){
        if(global&&isPosterior(c)&&!f.used[f.slot]&&f.status("posteriorAlways")==0&&f.status("nextPosterior")>0)addStatus(ch,f,"nextPosterior",-1);
        if(global&&c.name().contains("星弈")&&f.status("starLinkCharges")>0){addStatus(ch,f,"starLinkCharges",-1);if(starred(f))addStatus(ch,f,"starPower",1);else{addStar(ch,f,f.slot);heal(ch,f,f.rules.byId.get(4000104).param(1));}}
        if(!isStar(c))return;
        switch(c.baseId()){
            case 4000031 -> {if(chance(ch,f,c.param(0)))addStatus(ch,f.opponent,"vulnerability",c.param(1));}
            case 4000056 -> addStatus(ch,f.opponent,"internalInjury",c.param(0));
            case 4000096 -> addStatus(ch,f,"attackInjuryRounds",c.param(0));
            case 4000097 -> {if(!posterior(f))growCareer(ch,f,c.param(0));}
            case 4000108 -> addAnima(ch,f,c.anima());
            default -> { }
        }
    }
    static List<Integer> attacks(List<Change> ch,Fighter f,Rules.Card c,List<Integer> original){
        if(!isStar(c))return original;int b=c.baseId(),n=c.param(0);var values=new ArrayList<>(original);boolean star=starred(f),post=posterior(f);
        if(c.randomAttack()>0){values.clear();for(int i=0;i<Math.max(1,c.attackCount());i++){boolean hex=f.status("hexagram")>0;values.add(roll(ch,f,c.attack(),c.randomAttack()));if(b==4000063&&hex)addAnima(ch,f,1);}}
        switch(b){
            case 53 -> {if(star)values.replaceAll(v->v+n);if(post)values.add(c.attack()+(star?n:0));}
            case 195 -> {if(!post)values.clear();}
            case 4000010,4000029 -> {if(star)values.add(n);}
            case 4000042 -> values.replaceAll(v->v+f.status("healingTotal")/Math.max(1,n));
            case 4000033 -> {values.clear();if(post)values.add(n);}
            case 4000046 -> {values.clear();for(int i=0;i<5;i++)if(chance(ch,f,n))values.add(c.param(1));}
            case 4000056 -> {values.clear();for(int i=0;i<Math.min(999,debuffs(f.opponent));i++)values.add(1);}
            case 4000071 -> values.replaceAll(v->v+f.status("hexagram"));
            case 4000073 -> {int count=1;for(int i=0;i<f.cards.length;i++)if(i!=f.slot&&(f.rules.byId.get(f.cards[i]).name().contains("雷")||f.rules.byId.get(f.cards[i]).name().contains("卦")))count++;int bonus=count*n;values.replaceAll(v->v+bonus);}
            case 4000076 -> {values.clear();if(post){int bonus=c.level()>3&&f.slot>7-c.param(2)?c.param(3):0;for(int i=0;i<c.param(1);i++)values.add(n+bonus);}}
            case 4000097 -> {if(!post)values.clear();else values.replaceAll(v->v+f.status("healingTotal")/Math.max(1,c.param(1)));}
            case 4000108 -> {f.starFrame.rolledDefense=roll(ch,f,c.defense(),c.randomDefense());values.clear();if(f.rules.byId.get(f.cards[previous(f,f.slot)]).name().contains("雷")||f.rules.byId.get(f.cards[next(f,f.slot)]).name().contains("雷"))values.add(f.starFrame.rolledDefense);}
            default -> { }
        }
        return values;
    }
    static List<Integer> followups(List<Change> ch,Fighter f,Rules.Card c){
        if(!isStar(c))return List.of();var values=new ArrayList<Integer>();boolean post=posterior(f);
        switch(c.baseId()){
            case 4000061 -> {if(chance(ch,f,c.param(0)))values.add(c.param(1));}
            case 4000043 -> {if(post)values.add(c.param(1));}
            case 4000073 -> {if(c.level()>3&&chance(ch,f,10)){int count=1;for(int i=0;i<f.cards.length;i++)if(i!=f.slot&&(f.rules.byId.get(f.cards[i]).name().contains("雷")||f.rules.byId.get(f.cards[i]).name().contains("卦")))count++;values.add(c.attack()+count*c.param(0));}}
            case 4000075 -> {int times=c.level()<=3?(f.status("posteriorUsed")>0?1:0):Math.min(999,f.status("posteriorUsed"));for(int i=0;i<times;i++)values.add(c.param(0));}
            case 4000096 -> {if(chance(ch,f,10))addStatus(ch,f.opponent,"attackReduction",c.param(2));if(chance(ch,f,10)){values.add(c.param(1));values.add(c.param(1));}}
            case 4000101 -> {if(post)for(int i=0;i<Math.max(1,c.attackCount());i++)values.add(c.attack());}
            case 4000102 -> {if(chance(ch,f,1))values.add(c.param(0));}
            default -> { }
        }
        return values;
    }
    static void after(List<Change> ch,Fighter f,Rules.Card c){
        if(!isStar(c))return;Fighter d=f.opponent;int n=c.param(0),b=c.baseId();boolean star=starred(f),post=posterior(f);
        // Most shared numeric fields are card-body gains, never a second fee.
        if(b!=4000108&&b!=4000106&&b!=4000067&&b!=4000040&&b!=4000099&&b!=4000070&&b!=320&&c.anima()>0)addAnima(ch,f,c.anima());
        if(b!=4000095&&b!=4000108&&c.randomDefense()==0&&c.defense()>0)addDefense(ch,f,c.defense());
        if(c.randomDefense()>0&&b!=4000108)addDefense(ch,f,roll(ch,f,c.defense(),c.randomDefense()));
        if(c.hexagram()>0&&b!=4000070&&b!=4000040&&b!=4000099&&b!=4000109)addStatus(ch,f,"hexagram",c.hexagram());
        switch(b){
            case 51 -> addStatus(ch,f,"animaToHexagram",1);
            case 52 -> addStatus(ch,f,"injuryOnBothAttack",1);
            case 54 -> {if(debuffs(d)>0)f.requestChase=true;if(post)addStatus(ch,d,"internalInjury",n);}
            case 142,4000023,4000015,4000090 -> nextStars(ch,f,n);
            case 152 -> {int amount=f.anima*n+f.status("starPower")*c.param(1);addAnima(ch,f,-f.anima);setStatus(ch,f,"starPower",0);growCareer(ch,f,amount);if(post)addAnima(ch,f,c.param(2));if(f.status("healingTotal")>0)f.requestChase=true;}
            case 164 -> {for(int i=0;i<n;i++){if(!chance(ch,f,10))damage(ch,f,f,c.param(1));damage(ch,d,c.param(2));}}
            case 168 -> {reverse(ch,f);if(chance(ch,f,10))f.requestChase=true;}
            case 172 -> addStatus(ch,f,"reviveWheel",1);
            case 190 -> {addStatus(ch,f,"posteriorBirdAttack",n);addStatus(ch,f,"posteriorDiscount",c.param(1));}
            case 191 -> {addStatus(ch,d,"internalInjury",n);heal(ch,f,c.param(1));int hex=f.status("hexagram");addStatus(ch,f,"hexagram",-hex);addStatus(ch,d,"internalInjury",hex);addStatus(ch,d,"weakness",hex);}
            case 192 -> {growCareer(ch,f,n);CareerEffects.both(ch,f,"springThunder",c.param(1));}
            case 193 -> {int count=roll(ch,f,n,c.param(1));for(int i=0;i<count;i++){String key=!hasParameter(f)?CareerEffects.DEBUFF_IDS.get(new int[]{100,101,102,103,104,105,367,393}[randomValue(f,0,7)]):CareerEffects.selectedDebuff(f);if(key!=null)addStatus(ch,d,key,1);}if(debuffs(d)>=c.param(2))f.requestChase=true;}
            case 194 -> addStatus(ch,f,"dualFormation",n);
            case 195 -> {if(!f.used[f.slot])addStatus(ch,d,"binding",n);addStatus(ch,d,"externalInjury",c.param(1));}
            case 196 -> {if(star){int slot=f.slot;for(int i=0;i<n;i++){slot=next(f,slot);if(f.rules.byId.get(f.cards[slot]).name().contains("星弈")){int skipped=next(f,f.slot);for(int j=0;j<f.cards.length&&skipped!=slot;j++){Engine.triggerOpening(ch,f,f.rules.byId.get(f.cards[skipped]),f.slot);skipped=next(f,skipped);}f.jumpSlot=slot;break;}}}}
            case 265 -> {int loss=Math.max(0,d.maxHp-d.hp)+n+f.anima*c.param(1)+f.status("hexagram")*c.param(2);addAnima(ch,f,-f.anima);setStatus(ch,f,"hexagram",0);modifyMaxHp(ch,d,-loss);}
            case 266 -> {addStatus(ch,d,"internalInjury",n);addStatus(ch,d,"otherDebuffInjury",c.param(1));}
            case 268 -> {addStatus(ch,f,"starPower",n);addStatus(ch,f,"animaToStarLimit",c.param(1));}
            case 269 -> {nextStars(ch,f,n);addStatus(ch,f,"starAfterNext",1);addStatus(ch,f,"starAfterAttack",c.param(1));}
            case 289 -> {addStatus(ch,d,"internalInjury",n);addStatus(ch,d,"injuryAmplifyRounds",c.param(1));}
            case 294 -> {addStatus(ch,f,"starPower",n);if(!star)f.requestChase=true;}
            case 298 -> {addStatus(ch,f,"recovery",n);int amount=c.param(1)+f.status("recovery");modifyMaxHp(ch,f,amount);if(post)heal(ch,f,amount);}
            case 307 -> addStatus(ch,f,"temporaryUpgrades",1+f.status("hexagram")/Math.max(1,n));
            case 320 -> {addAnima(ch,f,Math.max(0,c.anima()));int anima=f.anima,hex=f.status("hexagram"),amount=(anima+hex)*n;addDefense(ch,f,amount);heal(ch,f,amount);addAnima(ch,f,-anima);addStatus(ch,f,"hexagram",anima-hex);addAnima(ch,f,hex);}
            case 324 -> {growCareer(ch,f,n);addStatus(ch,f,"phantomGrowth",c.param(1));}
            case 4000008,4000024,4000054,4000079 -> {addStatus(ch,f,"starPower",n);if(b==4000054&&post)f.requestChase=true;}
            case 4000009 -> {if(star)addAnima(ch,f,n);}
            case 4000011 -> {if(star)addDefense(ch,f,n);}
            case 53,4000010,4000029,4000001,4000002,4000003,4000004,4000005,4000006,4000030,4000055,4000071,4000031,4000061,4000063 -> { }
            case 4000013 -> {if(post)heal(ch,f,n);}
            case 4000016 -> growCareer(ch,f,n);
            case 4000017 -> {modifyMaxHp(ch,f,n);heal(ch,f,roll(ch,f,n,c.param(1)));}
            case 4000018 -> {if(post)heal(ch,f,n);}
            case 4000019 -> modifyMaxHp(ch,d,-roll(ch,f,n,c.param(1)));
            case 4000020 -> {if(chance(ch,f,n))addStatus(ch,d,"weakness",c.param(1));}
            case 4000021 -> addStatus(ch,d,"internalInjury",n);
            case 4000022 -> {if(debuffs(d)>0)addStatus(ch,d,"internalInjury",n);}
            case 4000025 -> {addAnima(ch,f,n);addAnima(ch,d,n);}
            case 4000026 -> {if(f.status("hexagram")>=n)f.requestChase=true;}
            case 4000027 -> addStatus(ch,f,"animaHeal",n);
            case 4000028 -> {if(star)addAnima(ch,d,-n);}
            case 4000032 -> {nextStars(ch,f,c.param(1));addStatus(ch,f,"starPower",n);}
            case 4000033 -> {if(post)f.requestChase=true;}
            case 4000034 -> reduceLifeAndCap(ch,d,n);
            case 4000035 -> {if(chance(ch,f,n))f.requestChase=true;}
            case 4000036 -> {heal(ch,f,n);if(post)addStatus(ch,f,"guard",c.param(1));}
            case 4000037 -> {growCareer(ch,f,c.param(1));addStatus(ch,f,"reflectAttack",n);}
            case 4000038 -> {if(star)f.requestChase=true;}
            case 4000039 -> {if(star)addStatus(ch,d,"weakness",n);}
            case 4000040,4000099 -> {addAnima(ch,f,Math.max(0,c.anima()));addStatus(ch,f,"hexagram",c.hexagram());addDefense(ch,f,f.status("hexagram")*n);heal(ch,f,f.status("hexagram")*c.param(1));}
            case 4000041 -> {heal(ch,f,n);addStatus(ch,f,"repeatNextCard",1);}
            case 4000042 -> { }
            case 4000043 -> heal(ch,f,n);
            case 4000044 -> {if(star)addStatus(ch,d,"skipUpcoming",n);}
            case 4000045 -> {setStatus(ch,f,"starMask",255);addStatus(ch,f,"starPower",n+(f.slot==0?1:0));}
            case 4000046 -> { }
            case 4000047 -> {int amount=(f.anima+f.status("hexagram"))*n;setStatus(ch,f,"hexagram",0);addAnima(ch,f,-f.anima);heal(ch,f,amount);}
            case 4000048 -> {setStatus(ch,f,"overflowGrowth",1);growCareer(ch,f,n);}
            case 4000049 -> setStatus(ch,f,"posteriorAlways",1);
            case 4000050 -> damage(ch,d,n+Math.max(0,f.maxHp-d.maxHp)/Math.max(1,c.param(1)));
            case 4000051 -> { }
            case 4000052 -> addStatus(ch,f,"invertLifeLoss",n);
            case 4000053 -> {addStatus(ch,d,"weakness",roll(ch,f,0,n));addStatus(ch,d,"vulnerability",roll(ch,f,0,c.param(1)));}
            case 4000056 -> { }
            case 4000057 -> {int rate=chance(ch,f,1)?2:1;growCareer(ch,f,n*rate);heal(ch,f,(c.param(1)-n)*rate);}
            case 4000058 -> {if(f.status("hexagram")==8){setHp(ch,d,n);}}
            case 4000059 -> {addStatus(ch,d,"internalInjury",n);loseHp(ch,d,d.status("internalInjury"));}
            case 4000060 -> {modifyMaxHp(ch,f,n);if(post)heal(ch,f,c.param(1));}
            case 4000062 -> {int hex=f.status("hexagram");setStatus(ch,f,"hexagram",0);addStatus(ch,f,"starPower",hex);addAnima(ch,f,hex);heal(ch,f,hex*n);}
            case 4000064 -> addStatus(ch,f,"thunderPercent",n);
            case 4000065 -> {int count=f.status("starPower");setStatus(ch,f,"starPower",0);for(int i=0;i<Math.min(999,count);i++)damage(ch,d,n);}
            case 4000066 -> {reverse(ch,f);addStatus(ch,f,"posteriorExtraAttack",n);}
            case 4000067 -> {int hex=f.status("hexagram"),power=f.status("starPower");if(f.anima>=hex&&f.anima>=power)addAnima(ch,f,n);else if(hex>=power)addStatus(ch,f,"hexagram",n);else addStatus(ch,f,"starPower",n);addAnima(ch,f,Math.max(0,c.anima()));}
            case 4000068 -> addStatus(ch,f,"hexagram",Math.min(n,f.status("hexagramTriggered")));
            case 4000069 -> {if(star)addDefense(ch,f,f.status("starPower")*n);}
            case 4000070 -> {int hex=f.status("hexagram");setStatus(ch,f,"hexagram",0);addAnima(ch,f,Math.max(0,c.anima()));if(c.level()<4)growCareer(ch,f,n);else{addStatus(ch,f,"hexagram",n);nextStars(ch,f,hex);}addStatus(ch,f,"starPower",hex);}
            case 4000072 -> {addStatus(ch,f,"resourceDefense",n);if(c.level()>=3)addStatus(ch,f,"resourceHeal",c.param(1));if(c.param(2)>0)modifyMaxHp(ch,f,c.param(2));}
            case 4000073 -> { }
            case 4000074 -> {if(c.level()<=3){addStatus(ch,d,"internalInjury",n);if(post)heal(ch,f,c.param(1));}else{if(post)heal(ch,f,c.param(1));addStatus(ch,d,"internalInjury",f.status("healingTotal")/Math.max(1,c.param(2)));}}
            case 4000075 -> {if(!f.used[f.slot]&&c.level()<=4||c.level()==5)reverse(ch,f);}
            case 4000076 -> { }
            case 4000078 -> {addStatus(ch,f,"reflectRounds",c.param(1));growCareer(ch,f,n);}
            case 4000080 -> {int loss=n+f.status("hexagram")*c.param(1);setStatus(ch,f,"hexagram",0);loseHp(ch,d,loss);}
            case 4000081 -> {if(c.level()>=4&&f.status("hexagram")>n)f.requestChase=true;}
            case 4000082 -> {if(c.level()<=3){addStatus(ch,d,"internalInjury",n);if(c.level()==3)addStatus(ch,d,"weakness",n);}else addStatus(ch,d,"snakeShadow",n);growCareer(ch,f,c.param(1));if(f.status("snakeCardsUsed")>0)addStatus(ch,f,"agility",10);}
            case 4000083 -> {modifyMaxHp(ch,f,n);heal(ch,f,c.level()<=3?c.param(1):c.param(1)*f.anima+c.param(2)*f.status("starPower"));}
            case 4000084 -> {if(c.level()<=4)addStatus(ch,f,"nonStarGains",n);else addStatus(ch,f,"nonStarPermanent",1);}
            case 4000085 -> {int count=n+(post?c.param(1):0);for(int i=0;i<count;i++){int id=!hasParameter(f)?new int[]{100,101,105}[randomValue(f,0,2)]:nextParameter(f);String key=CareerEffects.DEBUFF_IDS.get(id);if(!Set.of(100,101,105).contains(id))throw new Rejected("INVALID_INPUT","梦白蛇参数只接受100/101/105");addStatus(ch,d,key,1);}}
            case 4000086 -> { }
            case 4000087 -> addStatus(ch,f,"dreamWoodUses",1);
            case 4000088 -> {if(c.level()<=4)addStatus(ch,f,"hexagram",f.status("hexagramLost"));else addStatus(ch,f,"dreamHexRefund",1);}
            case 4000089 -> {if(star){addStatus(ch,d,"internalInjury",n);if(c.level()>3)addStatus(ch,f,"starPower",c.param(1));}}
            case 4000091 -> {growCareer(ch,f,roll(ch,f,n,c.param(1)));addStatus(ch,f,"hexagram",f.status("hexagramTriggered")-f.starFrame.startingHexTriggers);}
            case 4000092 -> addStatus(ch,f,"soulInjuryCharges",n);
            case 4000093 -> {if(star)growCareer(ch,f,c.param(1)+f.status("starPower")*c.param(2));addStatus(ch,f,"starPower",n);}
            case 4000094 -> {if(f.anima>d.anima)addStatus(ch,d,"vulnerability",n);if(star&&f.status("starPower")>=c.param(1))f.requestChase=true;}
            case 4000095 -> {addStatus(ch,d,"internalInjury",n);addDefense(ch,f,Math.min(c.param(3),c.defense()+debuffs(d)*c.param(1)));if(f.def>=c.param(2))f.requestChase=true;}
            case 4000096 -> { }
            case 4000097 -> {if(chance(ch,f,c.param(2)))f.requestChase=true;}
            case 4000098 -> {modifyMaxHp(ch,f,c.param(1));addStatus(ch,f,"animaHeal",n);}
            case 4000100 -> addStatus(ch,f,"hexagramDamage",n);
            case 4000014 -> addStatus(ch,f,"hexagramDamage",Math.max(0,n-(f.status("hexagramDamage")>0?c.param(1):0)));
            case 4000101 -> addStatus(ch,f,"attackBonus",n);
            case 4000102 -> addStatus(ch,f,"repel",c.param(1));
            case 4000103 -> {heal(ch,f,n);if(!f.used[f.slot])addStatus(ch,f,"nextBack",1);if(post)addDefense(ch,f,f.status("turnHealing")/Math.max(1,c.param(1)));}
            case 4000104 -> addStatus(ch,f,"starLinkCharges",n);
            case 4000105 -> {addStatus(ch,d,"internalInjury",n);addStatus(ch,d,"snakeShadow",c.param(1));}
            case 4000106 -> {
                addStatus(ch,f,"overflowDamageRounds",c.param(1));
                addAnima(ch,f,c.anima());
                heal(ch,f,n+f.anima);
            }
            case 4000107 -> {if(f.slot%2==0)addStatus(ch,f,"starPower",n);else{int count=Math.min(c.param(1),f.status("starPower"));addStatus(ch,f,"starPower",-count);addStatus(ch,f,"guard",count);}}
            case 4000108 -> {if(f.starFrame.rolledDefense>=0&&!f.rules.byId.get(f.cards[previous(f,f.slot)]).name().contains("雷")&&!f.rules.byId.get(f.cards[next(f,f.slot)]).name().contains("雷"))addDefense(ch,f,f.starFrame.rolledDefense);if(chance(ch,f,10))f.requestChase=true;}
            case 4000109 -> {addStatus(ch,f,"hexagram",c.hexagram()+(debuffs(d)>0?n:0));addStatus(ch,d,f.slot%2==0?"internalInjury":"externalInjury",c.param(f.slot%2==0?1:2));}
            default -> throw new Rejected("UNSUPPORTED","缺少七星效果处理："+c.id());
        }
    }
    static List<Integer> globalAfter(List<Change> ch,Fighter f,Rules.Card c){
        var attacks=new ArrayList<Integer>();
        if(isPosterior(c)){if(f.status("posteriorExtraAttack")>0)attacks.add(f.status("posteriorExtraAttack"));if(f.status("posteriorBirdAttack")>0)attacks.add(f.status("posteriorBirdAttack"));}
        if(c.name().contains("星弈")&&f.status("starAfterAttack")>0)attacks.add(f.status("starAfterAttack"));
        return attacks;
    }
    static void globalGains(List<Change> ch,Fighter f,Rules.Card c){
        if(c.name().contains("星弈")&&f.status("starAfterNext")>0)addStar(ch,f,next(f,f.slot));
        if(!starred(f)){
            if(f.status("nonStarGains")>0){addStatus(ch,f,"nonStarGains",-1);addAnima(ch,f,1);addStatus(ch,f,"starPower",1);}
            if(f.status("nonStarPermanent")>0&&f.status("nonStarTriggered")==0){setStatus(ch,f,"nonStarTriggered",1);addAnima(ch,f,f.status("nonStarPermanent"));addStatus(ch,f,"starPower",f.status("nonStarPermanent"));}
        }
    }
    static void record(List<Change> ch,Fighter f,Rules.Card c){if(isPosterior(c))addStatus(ch,f,"posteriorUsed",1);if(c.name().contains("蛇"))addStatus(ch,f,"snakeCardsUsed",1);}
    static void start(List<Change> ch,Fighter f){setStatus(ch,f,"hexRefundUsed",0);setStatus(ch,f,"turnHealing",0);for(String key:List.of("reflectRounds","overflowDamageRounds","attackInjuryRounds"))if(f.status(key)>0)addStatus(ch,f,key,-1);}
    static void end(List<Change> ch,Fighter f){setStatus(ch,f,"nonStarTriggered",0);if(f.status("phantomGrowth")>0){growCareer(ch,f,f.status("phantomGrowth"));loseHp(ch,f,f.status("phantomGrowth"));}CareerEffects.trigger(ch,f,"dualFormation",()->{int amount=f.anima+2*f.status("hexagram");addDefense(ch,f,amount);heal(ch,f,amount);});if(f.status("springThunder")>0&&chance(ch,f,10))damage(ch,f.opponent,f.status("springThunder"));if(f.status("injuryAmplifyRounds")>0)addStatus(ch,f,"injuryAmplifyRounds",-1);}
    static int starAttack(Fighter f,Rules.Card c){return starred(f)?f.status("starPower")*(c.baseId()==4000051&&!f.afterCard?c.param(0):1):0;}
    static int rawMultiplier(Fighter f,Rules.Card c,int raw){if(c.baseId()==4000087&&!f.afterCard&&f.status("dreamWoodUses")>0){int exponent=c.level()==5?f.status("dreamWoodUses"):1;return exponent>=32?0:raw*(1<<exponent);}return raw;}
    static void afterAttack(List<Change> ch,Fighter f,Fighter d){
        if(d.status("reflectAttack")>0)f.resolution.enqueue(()->damage(ch,f,d.status("reflectAttack")));
        if(f.status("injuryOnBothAttack")>0)addStatus(ch,d,"internalInjury",f.status("injuryOnBothAttack"));
        if(d.status("injuryOnBothAttack")>0)addStatus(ch,f,"internalInjury",d.status("injuryOnBothAttack"));
        for(Fighter owner:List.of(f,d))if(owner.status("soulInjuryCharges")>0){addStatus(ch,owner,"soulInjuryCharges",-1);addStatus(ch,owner.opponent,"internalInjury",1);}
        if(f.status("attackInjuryRounds")>0)addStatus(ch,d,"internalInjury",1);
    }
    static void statusChanged(List<Change> ch,Fighter f,String key,int before,int after){
        int delta=after-before;if(delta==0)return;
        if(key.equals("hexagram")){
            if(delta>0){addStatus(ch,f,"hexagramGained",delta);if(f.status("hexagramDamage")>0)directDamage(ch,f.opponent,delta*f.status("hexagramDamage"));addDefense(ch,f,delta*f.status("resourceDefense"));heal(ch,f,delta*f.status("resourceHeal"));if(TalentEffects.has(f,135))heal(ch,f,delta);}
            else{addStatus(ch,f,"hexagramLost",-delta);if(f.status("dreamHexRefund")>0&&f.status("hexRefundUsed")==0){setStatus(ch,f,"hexRefundUsed",1);addStatus(ch,f,"hexagram",-delta);}}
        }
        if(delta>0&&CareerEffects.NEGATIVE.contains(key)&&!key.equals("internalInjury")&&!key.equals("underworld")&&f.status("otherDebuffInjury")>0)addStatus(ch,f,"internalInjury",delta*f.status("otherDebuffInjury"));
    }
    static void directDamage(List<Change> ch,Fighter target,int amount){if(amount<=0)return;if(target.status("iron")>0)amount=Math.max(1,amount-5);if(target.status("reflectRounds")>0){int reflected=(amount*25+99)/100;amount=Math.max(1,amount*75/100);loseHp(ch,target.opponent,reflected);}loseHp(ch,target,amount);}
}
