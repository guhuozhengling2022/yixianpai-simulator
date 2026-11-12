package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Standalone career cards. Resolved random values use the same global parameter tape. */
final class CareerEffects {
    static final List<String> NEGATIVE=List.of("internalInjury","weakness","vulnerability","attackReduction","binding","externalInjury","indigestion","underworld");
    static final Map<Integer,String> DEBUFF_IDS=Map.of(100,"internalInjury",101,"weakness",102,"vulnerability",103,"attackReduction",104,"binding",105,"externalInjury",393,"indigestion",367,"underworld");
    static final Map<String,String> STATES=new LinkedHashMap<>();
    static {
        String[][] names={{"deathBattleWill","死战之志"},{"deathBattleActive","死战不倒"},{"deathBattleWillIfSecond","后手死战之志"}, {"repel","辟邪"},{"binding","困缚"},{"attackReduction","减攻"},{"qinUsed","已使用琴师牌次数"},{"sustainUsed","已使用持续牌次数"},{"freeAttackSong","逍遥曲"},{"attackSong","狂舞曲"},{"illusionSong","幻音曲"},{"injurySong","断肠曲"},{"springSong","回春曲"},{"attackLifeSong","幽绪乱心曲"},{"noChaseSong","天音困仙曲"},{"animaLossSong","玄冥回春曲减生命"},{"mercySong","慈念曲命元减伤"},{"forcedChases","梦狂舞曲次数"},{"unstoppableChase","梦转弦不受阻次数"},{"dreamChaseBlock","梦困仙前几回合"},{"halveAnimaFirst","首回合获得灵气减半"},{"ownTurns","自身回合数"},{"thunderFormation","引雷阵次数"},{"shatterFormation","碎杀阵次数"},{"turtleFormation","龟甲阵次数"},{"poisonFormation","邪蛊阵次数"},{"animaFormation","聚灵阵次数"},{"extraAttackFormation","周天剑阵次数"},{"powerFormation","天罡聚力阵次数"},{"lockFormation","八门金锁阵次数"},{"confusionFormation","万花迷魂阵次数"},{"diamondFormation","不动金刚阵次数"},{"nextCostReduction","下张牌灵气减耗"},{"nextCardChase","运笔如飞次数"},{"temporaryUpgrades","画龙点睛次数"},{"permanentUpgrades","长生印升级次数"},{"lossAttackFormation","玄冥聚力阵次数"},{"nextSkip","待跳过槽位数"},{"nextBack","待回退槽位数"},{"observeAttack","察体追加攻击"},{"ominousLoss","凶象失血施外伤次数"},{"avoidOmen","天运避凶次数"},{"seekOmen","天运趋吉次数"},{"minimumAttackRounds","万事如意回合"},{"reduceAttackCharges","诸事不宜次数"},{"fortuneRounds","顺应回合"},{"reverseRounds","逆施回合"},{"reincarnation","命运轮回次数"},{"reincarnationSlot1","命运轮回首槽"},{"reincarnationSlot2","命运轮回次槽"},{"downgradeCharges","幽冥化生壶次数"},{"doubleLifeTurns","补天石生命翻倍次数"},{"dreamDiamond","梦金刚减损回合"},{"noDefense","无法加防回合"},{"dreamFlying","梦飞云丹"},{"dreamReturn","梦大还丹"},{"leafShield","叶盾花"},{"leafBlade","叶刃花"},{"hardBamboo","硬枝竹"},{"snowLotus","雪莲回合末回防"},{"vineBinding","古藤追加困缚"},{"vineDrain","古藤追加吸血"},{"recovery","恢复"}};
        for(var n:names)STATES.put(n[0],n[1]);
    }
    static boolean isCareer(Rules.Card c){return c.career()>0&&c.sect()==0;}
    static void both(List<Change> ch,Fighter f,String key,int n){addStatus(ch,f,key,n);addStatus(ch,f.opponent,key,n);}
    static void grow(List<Change> ch,Fighter f,int n){modifyMaxHp(ch,f,n);heal(ch,f,n);}
    static void cleanse(List<Change> ch,Fighter f,int amount){for(String key:NEGATIVE)addStatus(ch,f,key,-amount);}
    static String selectedDebuff(Fighter f){int id=nextParameter(f);String key=DEBUFF_IDS.get(id);if(id==-1)return null;if(key==null)throw new Rejected("INVALID_INPUT","负面状态参数须为原始BuffType ID：100至105、367（冥）、393（食滞），或-1");return key;}
    static int parameter(Fighter f,int min,int max){int value=nextParameter(f);if(value<min||value>max)throw new Rejected("INVALID_INPUT","随机结果参数范围："+min+"至"+max);return value;}
    static boolean openingCard(Rules.Card c){return Set.of(372,389,446,447,55,56,57,58,181,203,315,369,11000001,11000005,11000009,11000013,11000014,11000018,11000022,11000023,11000024,1000101,7000079).contains(c.baseId())||(c.baseId()==1000086&&c.level()>=4);}
    static void opening(List<Change> ch,Fighter f,Rules.Card c,int slot){
        Fighter d=f.opponent;
        switch(c.baseId()){
            case 181 -> addStatus(ch,d,"downgradeCharges",2);
            case 11000001 -> loseHp(ch,d,c.param(1));
            case 11000005 -> grow(ch,f,c.param(1));
            case 11000009 -> addAnima(ch,f,c.param(0));
            case 11000013 -> addStatus(ch,f,"repel",c.param(2));
            case 11000014 -> addDefense(ch,f,c.param(2));
            case 11000022 -> addStatus(ch,f,"nextShatter",c.param(1));
            case 11000023 -> {loseHp(ch,f,c.param(1));loseHp(ch,d,c.param(1));}
            case 11000024 -> addStatus(ch,d,"internalInjury",c.param(1));
            case 11000018,369 -> {var target=f.rules.byId.get(d.cards[slot]);if(target.rarity()>0&&!target.noUpgrade()&&f.rules.byId.containsKey(target.id()-10000))d.cards[slot]-=10000;else if(c.baseId()==369)d.skipSlots[slot]=true;else damage(ch,d,c.param(1));}
            default -> { }
        }
    }
    static void passive(Fighter f){for(int id:f.cards){var c=f.rules.byId.get(id);if(c.baseId()==348){f.statuses.merge("dreamChaseBlock",c.param(0),Math::max);f.opponent.statuses.merge("dreamChaseBlock",c.param(0),Math::max);}if(c.baseId()==378){f.statuses.put("halveAnimaFirst",1);f.opponent.statuses.put("halveAnimaFirst",1);}}}
    static void start(List<Change> ch,Fighter f){
        addStatus(ch,f,"ownTurns",1);for(String key:List.of("minimumAttackRounds","fortuneRounds","reverseRounds"))if(f.status(key)>0)addStatus(ch,f,key,-1);
        if(f.status("doubleLifeTurns")>0){addStatus(ch,f,"doubleLifeTurns",-1);heal(ch,f,f.hp);}
        if(f.status("illusionSong")>0){loseHp(ch,f,f.status("illusionSong"));addDefense(ch,f,f.status("illusionSong"));}
        if(f.status("injurySong")>0)addStatus(ch,f,"internalInjury",f.status("injurySong"));
        if(f.status("dreamFlying")>0){if(f.opponent.def>0){int old=f.ignore;f.ignore+=f.status("dreamFlying");change(ch,f,"ignoreDefense",old,f.ignore);}else addAnima(ch,f,f.status("dreamFlying"));}
        if(f.status("dreamReturn")>0){if(f.maxHp<f.opponent.maxHp)modifyMaxHp(ch,f,f.status("dreamReturn"));if(f.hp<f.opponent.hp)heal(ch,f,f.status("dreamReturn"));}
        if(f.status("recovery")>0)heal(ch,f,f.status("recovery")*(1+f.status("seasonDoubleRecovery")));
        trigger(ch,f,"poisonFormation",()->addStatus(ch,f.opponent,"internalInjury",1));
        trigger(ch,f,"animaFormation",()->addAnima(ch,f,2));
        trigger(ch,f,"powerFormation",()->addStatus(ch,f,"attackBonus",1));
    }
    static void trigger(List<Change> ch,Fighter f,String key,Runnable action){if(f.status(key)>0){action.run();addStatus(ch,f,key,-1);}}
    static void end(List<Change> ch,Fighter f){
        if(f.status("springSong")>0)grow(ch,f,f.status("springSong"));
        if(f.status("animaLossSong")>0)loseHp(ch,f,f.status("animaLossSong"));
        trigger(ch,f,"thunderFormation",()->damage(ch,f.opponent,4));
        trigger(ch,f,"turtleFormation",()->addDefense(ch,f,3));
        trigger(ch,f,"diamondFormation",()->{addDefense(ch,f,7);if(f.chasesThisTurn==0)grow(ch,f,7);});
        trigger(ch,f,"confusionFormation",()->{addStatus(ch,f.opponent,"attackReduction",1);int n=f.opponent.status("attackReduction");loseHp(ch,f.opponent,n);heal(ch,f,n);});
        if(f.status("hardBamboo")>0)damage(ch,f.opponent,f.def/4*f.status("hardBamboo"));
        for(String key:List.of("binding","dreamDiamond","noDefense"))if(f.status(key)>0)addStatus(ch,f,key,-1);
    }
    static boolean blocksChase(List<Change> ch,Fighter f){
        if(f.status("dragon")>0||SeasonTalentEffects.has(f,28001))return false;
        boolean blocked=f.status("binding")>0||f.status("noChaseSong")>0||f.status("ownTurns")<=f.status("dreamChaseBlock");
        if(f.status("unstoppableChase")>0){addStatus(ch,f,"unstoppableChase",-1);return false;}if(f.status("binding")>0){addStatus(ch,f,"binding",-1);addStatus(ch,f,"externalInjury",f.opponent.status("vineBinding"));}return blocked;
    }
    static void before(List<Change> ch,Fighter f,Rules.Card c,boolean virtual){
        if(!virtual&&f.status("forcedChases")>0&&!f.requestChase&&f.chasesThisTurn==0){addStatus(ch,f,"forcedChases",-1);f.requestChase=true;}
        if(!virtual&&f.status("nextCardChase")>0){addStatus(ch,f,"nextCardChase",-1);f.requestChase=true;}
        if(!isCareer(c))return;
        if(c.baseId()==5000001||c.baseId()==5000009){f.careerIgnore=true;}
        if(c.baseId()==9000005&&(f.anima>0||f.opponent.anima>0))addAnima(ch,f,c.param(0));
    }
    static List<Integer> attacks(Fighter f,Rules.Card c,List<Integer> input){
        var attacks=new ArrayList<>(input);
        if((c.baseId()==5000001||c.baseId()==5000009)&&f.status("qinUsed")>0)attacks.replaceAll(v->v+1);
        if(c.baseId()==8000003&&f.status("sustainUsed")>0)attacks.add(c.param(0));
        return attacks;
    }
    static void after(List<Change> ch,Fighter f,Rules.Card c){
        if(!isCareer(c))return;Fighter d=f.opponent;int n=c.param(0);
        switch(c.baseId()){
            case 156 -> {grow(ch,f,n);int v=f.status("hpLost")/Math.max(1,c.param(1));modifyMaxHp(ch,f,v);heal(ch,f,v,true);}
            case 157 -> {int loss=(f.hp*n+99)/100;loseHp(ch,f,loss);reduceLifeAndCap(ch,d,loss+c.param(1));}
            case 158 -> {both(ch,f,"springSong",n);both(ch,f,"animaLossSong",c.param(1));}
            case 159 -> {for(int i=0;i<n;i++)switch(parameter(f,0,2)){case 0->{loseHp(ch,d,6);heal(ch,f,6);}case 1->addStatus(ch,d,"weakness",2);case 2->addStatus(ch,f,"guard",1);}}
            case 160 -> {addStatus(ch,f,"attackBonus",n);addStatus(ch,f,"lossAttackFormation",c.param(1));}
            case 161 -> {heal(ch,f,n,true);addStatus(ch,f,"nextDefense",f.status("hpLost")/Math.max(1,c.param(1)));}
            case 162 -> {int delta=f.status("previousTurnHp")-f.hp+n;if(delta>=0)heal(ch,f,delta,true);else loseHp(ch,f,-delta);addStatus(ch,f,"nextBack",1);}
            case 175 -> {grow(ch,f,n);addStatus(ch,f,"doubleLifeTurns",c.param(1));}
            case 176 -> {addAnima(ch,d,-(d.anima+1)/2);modifyMaxHp(ch,d,-(d.maxHp+1)/2);loseHp(ch,d,(d.hp+1)/2);addStatus(ch,d,"binding",n);}
            case 178 -> {modifyMaxHp(ch,f,n);addStatus(ch,f,"permanentUpgrades",c.param(1));}
            case 180 -> grow(ch,f,n);
            case 181 -> addStatus(ch,d,"downgradeCharges",n);
            case 333 -> addStatus(ch,f,"guard",n+(!f.used[f.slot]&&c.level()==5?1:0));
            case 334 -> {grow(ch,f,n);addStatus(ch,f,"skipTurns",1);addStatus(ch,f,"dreamDiamond",f.status("dreamDiamond")>0?1:2);}
            case 335 -> {loseHp(ch,f,n);if(!f.used[f.slot])d.slot=StarEffects.previous(d,d.slot);if(d.status("cardsUsed")>0)f.requestChase=true;}
            case 340 -> both(ch,f,"forcedChases",n);
            case 342 -> {cleanse(ch,f,n);addStatus(ch,f,"repel",c.param(1));}
            case 343 -> {addStatus(ch,d,"externalInjury",n);addStatus(ch,f,"nextShatter",c.param(1));if(!f.used[f.slot])addStatus(ch,d,"binding",c.param(2));}
            case 344 -> addStatus(ch,f,"dreamFlying",n);
            case 345 -> {grow(ch,f,c.param(1));addStatus(ch,f,"dreamReturn",n);}
            case 346 -> {addStatus(ch,f,"unstoppableChase",1);if(f.status("cardsUsed")>0)f.requestChase=true;}
            case 348 -> loseHp(ch,f,c.param(1));
            case 349 -> {if(c.level()>=4)grow(ch,f,n);else heal(ch,f,n);}
            case 350,9000006,9000015 -> { }
            case 351 -> {for(int i=0;i<c.param(1);i++)loseHp(ch,d,n);addStatus(ch,d,"noHealing",c.param(2));addStatus(ch,d,"noDefense",c.param(2));}
            case 369 -> addStatus(ch,d,"internalInjury",n);
            case 378 -> {addAnima(ch,f,-c.param(1));addAnima(ch,d,-c.param(1));}
            case 379 -> {if(d.hp<=c.param(1)){setHp(ch,d,-100);}else damage(ch,d,n);}
            case 387 -> addStatus(ch,d,"weakness",n);
            case 2000004 -> {for(int i=0;i<n;i++){String key=selectedDebuff(f);if(key!=null)addStatus(ch,f,key,-c.param(1));}}
            case 3000001 -> damage(ch,d,StarEffects.roll(ch,f,n,c.param(1)));
            case 3000003,3000004 -> damage(ch,d,n);
            case 3000005 -> reduceLifeAndCap(ch,d,n);
            case 3000006 -> cleanse(ch,f,n);
            case 3000007 -> addAnima(ch,d,-n);
            case 3000009 -> {for(int i=0;i<n;i++)loseHp(ch,d,c.param(1));}
            case 3000010 -> addDefense(ch,f,Math.min(c.param(1),(f.anima-Math.max(0,c.anima()))*n));
            case 3000014 -> {loseHp(ch,d,(d.hp*n+99)/100);modifyMaxHp(ch,d,-(d.maxHp*n+99)/100);addAnima(ch,d,-c.param(1));}
            case 3000015 -> {addStatus(ch,d,"internalInjury",n);addStatus(ch,d,"vulnerability",c.param(1));addStatus(ch,d,"weakness",c.param(2));}
            case 5000002 -> {addDefense(ch,f,n);addDefense(ch,d,n);}
            case 5000003 -> both(ch,f,"freeAttackSong",n);
            case 5000004 -> {addAnima(ch,f,n);addAnima(ch,d,n);}
            case 5000005 -> both(ch,f,"illusionSong",n);
            case 5000006 -> {if(f.status("mercySong")==0)addStatus(ch,f,"mercySong",n);if(d.status("mercySong")==0)addStatus(ch,d,"mercySong",n);}
            case 5000007 -> both(ch,f,"injurySong",n);
            case 5000008 -> both(ch,f,"attackSong",n);
            case 5000010 -> both(ch,f,"springSong",n);
            case 5000011 -> {addAnima(ch,f,-n);addAnima(ch,d,-n);}
            case 5000012 -> {for(String key:NEGATIVE)addStatus(ch,d,key,f.status(key));}
            case 5000013 -> both(ch,f,"attackLifeSong",n);
            case 5000014 -> {if(f.status("qinUsed")>0||f.rules.byId.get(f.cards[StarEffects.next(f,f.slot)]).career()==3)f.requestChase=true;}
            case 5000015 -> both(ch,f,"noChaseSong",1);
            case 6000003 -> addAnima(ch,f,n);
            case 6000004 -> addDefense(ch,f,StarEffects.roll(ch,f,c.defense(),c.param(1)));
            case 6000005 -> modifyMaxHp(ch,f,n);
            case 6000006 -> addDefense(ch,d,n);
            case 6000007 -> {String key=selectedDebuff(f);if(key!=null)addStatus(ch,d,key,n);}
            case 6000008 -> addStatus(ch,f,"nextCostReduction",n);
            case 6000012 -> addStatus(ch,f,"nextCardChase",1);
            case 6000013 -> addStatus(ch,f,"temporaryUpgrades",n);
            case 6000014 -> {switch(parameter(f,0,2)){case 0->addDefense(ch,f,n);case 1->heal(ch,f,c.param(1));case 2->addStatus(ch,f,"guard",c.param(2));}}
            case 8000001 -> addStatus(ch,f,"thunderFormation",n);
            case 8000002 -> addStatus(ch,f,"shatterFormation",n);
            case 8000004 -> addStatus(ch,f,"turtleFormation",n);
            case 8000005 -> addStatus(ch,f,"poisonFormation",n);
            case 8000006 -> {if(f.status("sustainUsed")>0)modifyMaxHp(ch,f,c.param(1));heal(ch,f,n);}
            case 8000007 -> addStatus(ch,f,"animaFormation",n);
            case 8000008 -> addStatus(ch,f,"extraAttackFormation",n);
            case 8000009 -> {addStatus(ch,f,"repel",n);if(f.status("sustainUsed")>0)addDefense(ch,f,c.param(1));}
            case 8000010 -> {damage(ch,d,c.param(2));addStatus(ch,f,"lockFormation",n);}
            case 8000011 -> addStatus(ch,f,"powerFormation",n);
            case 8000012 -> grow(ch,f,c.param(1));
            case 8000013 -> addStatus(ch,f,"confusionFormation",n);
            case 8000014 -> {addStatus(ch,f,"nextSkip",n);if(f.status("sustainUsed")>0)f.requestChase=true;}
            case 8000016 -> addStatus(ch,f,"diamondFormation",n);
            case 9000009 -> setStatus(ch,f,"leafShield",n);
            case 9000011 -> {addStatus(ch,d,"binding",n);addStatus(ch,f,"vineBinding",c.param(1));}
            case 9000013 -> {heal(ch,f,n);addStatus(ch,f,"snowLotus",c.param(1));}
            case 9000017 -> {int extra=Math.max(0,n-d.anima)*c.param(1);addAnima(ch,d,-n);damage(ch,d,extra);}
            case 9000018 -> setStatus(ch,f,"leafBlade",n);
            case 9000019 -> {loseHp(ch,d,n);heal(ch,f,n);addStatus(ch,f,"vineDrain",c.param(1));}
            case 9000020 -> {for(int i=0;i<c.param(1);i++)loseHp(ch,f,Math.max(0,Math.min(n,f.hp-1)));addStatus(ch,f,"guard",c.param(1));}
            case 9000026 -> {String key=selectedDebuff(f);if(key!=null)addStatus(ch,f,key,-n);if(c.rarity()>=2&&NEGATIVE.stream().noneMatch(k->f.status(k)>0))f.requestChase=true;}
            case 9000027 -> addStatus(ch,f,"hardBamboo",n);
            case 11000001 -> loseHp(ch,d,n);
            case 11000005 -> heal(ch,f,n);
            case 11000007 -> addStatus(ch,f,"avoidOmen",n);
            case 11000008 -> addStatus(ch,f,"seekOmen",n);
            case 11000011 -> addStatus(ch,f,"nextSkip",n);
            case 11000012 -> {heal(ch,f,n);addStatus(ch,f,"nextBack",c.param(1));}
            case 11000013 -> addStatus(ch,f,"minimumAttackRounds",c.param(1));
            case 11000014 -> addStatus(ch,f,"reduceAttackCharges",n);
            case 11000018,11000024 -> addStatus(ch,d,"internalInjury",n);
            case 11000019 -> addStatus(ch,f,"fortuneRounds",c.param(1));
            case 11000020 -> addStatus(ch,f,"reverseRounds",c.param(1));
            case 11000021 -> {grow(ch,f,n);addStatus(ch,f,"reincarnation",c.param(1));setStatus(ch,f,"reincarnationSlot1",c.param(2));setStatus(ch,f,"reincarnationSlot2",c.param(3));}
            case 11000022 -> addStatus(ch,f,"observeAttack",n);
            case 11000023 -> addStatus(ch,d,"ominousLoss",n);
            case 11000025,11000026 -> {if(c.baseId()==11000026)heal(ch,f,c.param(2));int count=0;int direction=c.baseId()==11000025?1:-1;for(int slot=f.slot+direction;slot>=0&&slot<f.cards.length&&count<n;slot+=direction){var target=f.rules.byId.get(f.cards[slot]);if(openingCard(target)){for(int repeat=0;repeat<c.param(1);repeat++)Engine.triggerOpening(ch,f,target,f.slot);count++;}}}
            default -> { }
        }
    }
    record Copy(int id,int slot){}
    static List<Copy> copies(Fighter f,Rules.Card c){
        int b=c.baseId();var result=new ArrayList<Copy>();
        if(Set.of(341,349,6000011).contains(b)){int id=nextParameter(f);if(id>=0)result.add(new Copy(id,f.slot));}
        if(b==177&&!f.opponent.practiceTarget){if(f.copyingCareer.contains(177))return result;int slot=f.opponent.slot;for(int i=0;i<f.opponent.cards.length&&f.opponent.consumed[slot];i++)slot=StarEffects.next(f.opponent,slot);result.add(new Copy(f.opponent.cards[slot],f.slot));f.opponent.slot=StarEffects.next(f.opponent,slot);}
        if(b==179&&!f.copyingCareer.contains(179)){for(int i=0;i<=f.slot;i++)if(f.used[i])result.add(new Copy(f.cards[i],i));}
        if(b==8000012){var first=f.rules.byId.get(f.cards[0]);if(first.cardType()==3){int id=first.id();for(int i=0;i<c.param(0)&&!first.noUpgrade()&&f.rules.byId.containsKey(id+10000);i++)id+=10000;result.add(new Copy(id,f.slot));}}
        return result;
    }
    static void recordUse(List<Change> ch,Fighter f,Rules.Card c){
        if(f.status("avoidOmen")>0&&!openingCard(c)){addDefense(ch,f,3);heal(ch,f,2);addStatus(ch,f,"avoidOmen",-1);}
        if(f.status("seekOmen")>0&&openingCard(c)){Engine.triggerOpening(ch,f,c,f.slot);damage(ch,f.opponent,3);addStatus(ch,f,"seekOmen",-1);}
if(c.career()==3)addStatus(ch,f,"qinUsed",1);if(c.cardType()==3)addStatus(ch,f,"sustainUsed",1);}
}
