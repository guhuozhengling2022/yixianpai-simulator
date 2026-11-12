package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Duan Xuan card bodies and shared callbacks, restored from the supplied snapshot. */
final class ForgeEffects {
 static final Map<String,String> STATES=new LinkedHashMap<>();
 static {
  String[][] rows={{"physique","体魄"},{"physiqueCap","体魄上限"},{"physiqueGained","本场获得体魄"},{"force","气势"},{"forceCap","气势上限"},{"forceGained","累计获得气势"},{"forceLost","累计失去气势"},{"forceRefill","气势不绝"},{"allFist","崩天步"},{"fistAttack","崩拳戳加攻"},{"fistDefense","崩拳封加防"},{"fistRefund","崩拳弹回血次数"},{"fistForce","崩拳撼气势"},{"fistInjury","崩拳缠外伤"},{"fistShatter","崩拳突碎防"},{"fistTransfer","崩拳截脉次数"},{"fistDoubleForce","崩拳寸劲"},{"fistAgility","崩拳闪击身法"},{"fistShock","崩拳惊触"},{"fistFollowup","梦崩拳连崩追加攻"},{"fistFollowupCount","梦连崩追加次数"},{"fistDreamCount","梦连崩追加2攻次数"},{"fistChanAttack","幻崩拳缠追加攻"},{"fistLifeSteal","崩拳夺星"},{"fistTiger","崩拳伏虎"},{"fistVulnerability","崩拳飞肘破绽"},{"fistCostDamage","崩拳碎骨"},{"fistRepeat","崩拳双影"},{"nextPhysique","下张牌体魄"},{"boneCharges","锻骨攻击次数"},{"boneAttack","锻骨加攻"},{"bonePhysique","锻骨加体魄"},{"nextPhysiqueAttack","星辰锻骨下次加攻"},{"forceBeforeAttack","气若悬河次数"},{"forceEveryAttack","梦气若悬河"},{"costForce","崩灭心法"},{"costCardsUsed","已用耗生命牌数"},{"costDefenseCharges","梦崩拳弹次数"},{"costDefenseAlways","梦崩拳弹常驻"},{"costAgility","血影遁术"},{"overflowPhysiqueHeal","幻锻骨加生命"},{"overflowPhysiqueDamage","幻锻骨伤害"},{"animaAttackCards","幻气沉丹田次数"},{"poisonHeal","百毒不侵治疗上限"},{"basicExtra","醉卧逍遥攻击次数"},{"basicGrowth","返璞归真"},{"lossResourceAnima","灵玄失血加灵气"},{"lossResourceAgility","灵玄失血加身法"},{"lossResourceUsed","本回合灵玄已触发"},{"dreamDebuff","梦灵玄每回合次数"},{"dreamDebuffUsed","本回合梦灵玄次数"},{"dreamDebuffHigh","梦灵玄化神"},{"debuffReflect","玄域雪莲次数"},{"forceFormation","势灵阵次数"},{"forceFormationGain","势灵阵收益"},{"drunkStance","醉拳架势"},{"defenseDamage","梦岿然伤害"},{"defenseDamagePermanent","梦岿然每次减伤害"},{"defenseDamageUsed","本回合梦岿然已触发"},{"injuryExtra","下回合内伤额外结算"},{"skipCostCards","瞬影击次数"},{"skipCostDamage","瞬影击伤害倍率"},{"returnGu","天机返蛊"},{"returnGuAttack","返蛊每层加攻"},{"basicUpgrade","逍遥连音曲"},{"dreamFistGrowth","梦锻拳下张牌击伤加体魄"},{"destinyAxe","裂穹开天斧命元标记"}};
  for(var r:rows)STATES.put(r[0],r[1]);
 }
 static final class Frame {
  final boolean fist,retain;final Map<String,Integer> carry=new HashMap<>();int bonus,forceMultiplier=1,hits,growth;boolean ignore,shatter,debuffAsForce;int forcePercent;
  Frame(Fighter f,Rules.Card c){fist=isFist(f,c);retain=c.baseId()==10000035||c.baseId()==10000082&&c.level()>=4;growth=f.status("dreamFistGrowth");}
 }
 static boolean isFist(Fighter f,Rules.Card c){return c.name().contains("崩拳")||f.status("allFist")>0||c.baseId()==10000087||c.baseId()==10000080||f.rules!=null&&(f.rules.byId.get(f.cards[StarEffects.previous(f,f.slot)]).baseId()==10000080||f.rules.byId.get(f.cards[StarEffects.next(f,f.slot)]).baseId()==10000080&&f.rules.byId.get(f.cards[StarEffects.next(f,f.slot)]).level()>=4);}
 static boolean isBasic(Rules.Card c){return c.baseId()==0;}
 static int div(int n,int d){return n/Math.max(1,d);}
 static int spend(List<Change> ch,Fighter f,int max){int n=Math.min(f.anima,max);addAnima(ch,f,-n);return n;}
 static void physique(List<Change> ch,Fighter f,int n){
  if(n==0)return;int old=f.status("physique");addStatus(ch,f,"physique",n);if(n>0)addStatus(ch,f,"physiqueGained",n);TalentEffects.physique(ch,f,n);modifyMaxHp(ch,f,n);
  if(n>0&&SeasonTalentEffects.has(f,7042)&&SeasonTalentEffects.firstThisTurn(f,7042))addAnima(ch,f,1);
  int overflow=Math.min(Math.max(0,n),Math.max(0,old+n-f.status("physiqueCap")));
  if(overflow>0){heal(ch,f,overflow*(1+f.status("overflowPhysiqueHeal")));damage(ch,f.opponent,overflow*f.status("overflowPhysiqueDamage"));}
 }
 static void initialize(Fighter f){f.statuses.putIfAbsent("forceCap",f.p.buffs()==null?6:f.p.buffs().getOrDefault("forceCap",6));}
 static void cost(List<Change> ch,Fighter f,Rules.Card c){
  int n=c.hpCost();if(n<=0)return;if(c.baseId()==10000005)n=Math.max(0,n-f.status("physique")/2);
  // HP costs bypass guard, but share normal life-loss history and reduction callbacks.
  if(TalentEffects.has(f,174))modifyMaxHp(ch,f,-n);else TalentEffects.costHp(ch,f,n);
  boolean fist=isFist(f,c);if(fist&&f.status("fistLifeRefund")>0)heal(ch,f,c.hpCost());boolean keep=c.baseId()==10000035;
  if(f.status("costAgility")>0){addStatus(ch,f,"costAgility",-1);addStatus(ch,f,"agility",c.hpCost());}
  if(fist&&f.status("fistRefund")>0){heal(ch,f,c.hpCost());if(!keep)addStatus(ch,f,"fistRefund",-1);}
  if(c.baseId()==10000093||fist&&f.status("fistCostDamage")>0){damage(ch,f.opponent,c.hpCost());if(fist&&f.status("fistCostDamage")>0&&!keep)addStatus(ch,f,"fistCostDamage",-1);}
 }
 static void transfer(List<Change> ch,Fighter from,Fighter to,int n){for(int i=0;i<n;i++){String k=CareerEffects.selectedDebuff(from);if(k!=null&&from.status(k)>0){addStatus(ch,from,k,-1);addStatus(ch,to,k,1);}}}
 static void cleanseRandom(List<Change> ch,Fighter f,int n,boolean batch){if(batch){String k=CareerEffects.selectedDebuff(f);if(k!=null)addStatus(ch,f,k,-n);}else for(int i=0;i<n;i++){String k=CareerEffects.selectedDebuff(f);if(k!=null)addStatus(ch,f,k,-1);}}
 static void globalBefore(List<Change> ch,Fighter f,Rules.Card c){
  var x=f.forgeFrame;
  if(c.hpCost()>0)addStatus(ch,f,"force",f.status("costForce"));
  if(f.status("nextPhysique")>0){int n=f.status("nextPhysique");setStatus(ch,f,"nextPhysique",0);physique(ch,f,n);}
  if(x.fist){
   for(String k:List.of("fistAttack","fistDefense","fistForce","fistInjury","fistShatter","fistTransfer","fistDoubleForce","fistAgility","fistShock","fistFollowup","fistFollowupCount","fistDreamCount","fistChanAttack","fistLifeSteal","fistTiger","fistVulnerability")){
    int n=f.status(k);x.carry.put(k,n);if(!x.retain&&!Set.of("fistAttack","fistLifeSteal").contains(k))setStatus(ch,f,k,0);
   }
   addDefense(ch,f,x.carry.get("fistDefense"));addStatus(ch,f,"force",x.carry.get("fistForce"));addStatus(ch,f.opponent,"externalInjury",x.carry.get("fistInjury"));addStatus(ch,f.opponent,"vulnerability",x.carry.get("fistVulnerability"));addStatus(ch,f,"agility",x.carry.get("fistAgility"));
   x.shatter=x.carry.get("fistShatter")>0;x.forceMultiplier=x.carry.get("fistDoubleForce")>0?2:1;
   transfer(ch,f,f.opponent,x.carry.get("fistTransfer"));
  }
  if(isBasic(c)&&f.status("basicGrowth")>0)addStatus(ch,f,"agility",f.status("basicGrowth"));
 }
 static void before(List<Change> ch,Fighter f,Rules.Card c){
  if(c.sect()!=4)return;var x=f.forgeFrame;var d=f.opponent;int b=c.baseId(),n=c.param(0);
  switch(b){
   case 170 -> {x.bonus=f.status("force")*n+f.anima*c.param(1)+f.status("agility")*c.param(2);setStatus(ch,f,"force",0);addAnima(ch,f,-f.anima);setStatus(ch,f,"agility",0);}
   case 297 -> {addAnima(ch,f,c.anima());int total=StarEffects.debuffs(d);for(String k:CareerEffects.NEGATIVE)setStatus(ch,d,k,0);addStatus(ch,d,"externalInjury",total);}
   case 319 -> addStatus(ch,d,"externalInjury",n);
   case 10000015,10000093 -> x.shatter=true;
   case 10000019,10000101 -> x.ignore=true;
   case 10000022 -> {if(spend(ch,f,1)>0){x.bonus=n;x.shatter=true;}}
   case 10000024 -> transfer(ch,f,d,n);
   case 10000025 -> x.forceMultiplier=2;
   case 10000030 -> {CareerEffects.both(ch,f,"internalInjury",n);CareerEffects.both(ch,f,"externalInjury",c.param(1));}
   case 10000033 -> {int paid=spend(ch,f,n);addStatus(ch,f,"force",paid*c.param(1));x.bonus=paid*c.param(2);}
   case 10000068 -> {for(String k:CareerEffects.NEGATIVE)if(f.status(k)>0)addStatus(ch,d,k,n);x.debuffAsForce=true;}
   case 10000079 -> {if(c.level()>=4)addStatus(ch,f,"force",div(f.status("physique"),n));}
   case 10000078 -> {if(c.level()>=4&&StarEffects.debuffs(f)>0)addStatus(ch,f,"force",n);}
   case 10000094 -> addStatus(ch,f,"force",f.status("costCardsUsed")*n);
   case 10000097 -> {int paid=spend(ch,f,n);x.bonus=paid*c.param(1);addDefense(ch,f,paid*c.param(2));}
   case 10000099 -> physique(ch,f,c.physique());
   case 10000103 -> {addStatus(ch,f,"forceCap",n);addStatus(ch,f,"force",n);}
   case 10000104 -> {if(f.slot%2==0)physique(ch,f,c.physique());else addStatus(ch,f,"force",n);}
   case 10000107 -> {int hp=c.param(0)+f.status("attackBonus")-f.status("attackReduction");int q=f.status("force");if(f.hasBase(10000084))hp+=q;else hp=hp*(100+q*10)/100;if(q>0)addStatus(ch,f,"force",-1);heal(ch,f,Math.max(1,hp));}
   default -> { }
  }
 }
 static List<Integer> attacks(Fighter f,Rules.Card c,List<Integer> original){
  if(c.sect()!=4){if(isBasic(c)){var v=new ArrayList<>(original);for(int i=0;i<Math.min(999,f.status("basicExtra"));i++)v.add(c.attack());return v;}return original;}
  int b=c.baseId(),n=c.param(0),bonus=f.forgeFrame.bonus,count=Math.max(1,c.attackCount());
  switch(b){
   case 166 -> count+=div(f.status("hpLost"),n);
   case 275 -> count+=div(f.status("healingTotal"),n);
   case 339 -> {for(int id:f.cards)if(isBasic(f.rules.byId.get(id)))count++;}
   case 10000011 -> bonus+=f.status("force")*n;
   case 10000028 -> bonus+=Math.min(c.param(1),div(f.status("physique"),n));
   case 10000047 -> {bonus+=div(f.status("physiqueGained"),n);count+=div(f.status("physique"),c.param(1));}
   case 10000067 -> bonus+=div(f.maxHp,n);
   case 10000072 -> {if(c.level()>=4)bonus+=f.anima;}
   case 10000074 -> {int amount=c.level()<=4?(f.status("agility")>0||f.status("force")>0?n:0):div(f.status("agility"),n);bonus+=amount;f.forgeFrame.bonus=amount;}
   case 10000077 -> {if(c.level()<=3&&StarEffects.debuffs(f)>0)bonus++;else if(c.level()>=4)bonus+=Math.min(c.param(0),StarEffects.debuffs(f));}
   case 10000078 -> bonus+=f.status("forceGained");
   case 10000079 -> bonus+=div(f.status("physiqueGained"),c.param(1));
   case 10000081 -> {if(c.level()>=5)count+=div(f.def,n);}
   case 10000092 -> bonus+=f.anima/2;
   case 10000094 -> bonus+=f.status("force");
   case 10000099 -> bonus+=div(f.status("physique"),n);
   case 10000103 -> bonus+=Math.min(c.param(1),f.anima+f.status("force"));
   case 10000104 -> bonus+=div(f.status("physique"),c.param(1));
   case 10000107 -> bonus+=div(f.status("healingTotal"),c.param(1));
   case 10000109 -> {return List.of(c.attack()+f.anima,c.attack()+StarEffects.debuffs(f));}
   default -> { }
  }
  var v=new ArrayList<Integer>();if(c.attack()>0)for(int i=0;i<Math.min(999,count);i++)v.add(c.attack()+bonus);return v;
 }
 static int rawAttack(List<Change> ch,Fighter f,Rules.Card c,int raw){
  var x=f.forgeFrame;if(x==null)return raw;
  if(!f.afterCard)x.hits++;
  if(!f.afterCard&&f.rules!=null){var next=f.rules.byId.get(f.cards[StarEffects.next(f,f.slot)]);if(next.baseId()==10000077&&next.level()>=4)raw+=div(StarEffects.debuffs(f),next.param(0));}
  if(f.status("returnGu")>0){addStatus(ch,f,"returnGu",-1);int n=StarEffects.debuffs(f.opponent);for(String k:CareerEffects.NEGATIVE){int v=f.opponent.status(k);setStatus(ch,f.opponent,k,0);addStatus(ch,f,k,v);}raw+=n*f.status("returnGuAttack");}
  if(!f.afterCard){
   if(x.fist)raw+=x.carry.getOrDefault("fistAttack",0);
   if(isBasic(c))raw+=f.status("basicGrowth");
   if(f.status("boneCharges")>0){addStatus(ch,f,"boneCharges",-1);raw+=f.status("boneAttack");physique(ch,f,f.status("bonePhysique"));}
   raw+=f.status("nextPhysiqueAttack");setStatus(ch,f,"nextPhysiqueAttack",0);
   if(c.baseId()==339){physique(ch,f,c.param(0));addStatus(ch,f,"agility",c.param(1));}
  }
  if(f.status("forceBeforeAttack")>0){addStatus(ch,f,"forceBeforeAttack",-1);addStatus(ch,f,"force",1);}addStatus(ch,f,"force",f.status("forceEveryAttack"));
  int force=f.status("force"),mult=f.afterCard?1:x.forceMultiplier;int other=x.debuffAsForce&&!f.afterCard?StarEffects.debuffs(f):0;
  x.forcePercent=0;
  if(f.hasBase(10000084))raw+=(force+other)*mult+(!f.afterCard&&f.status("animaAttackCards")>0?f.anima*mult:0);
  else x.forcePercent=(force+other)*10*mult+(!f.afterCard&&f.status("animaAttackCards")>0?f.anima*10:0);
  if(force>0)addStatus(ch,f,"force",-1);
  return raw;
 }
 static void afterHit(List<Change> ch,Fighter f,Rules.Card c,int loss){
  var x=f.forgeFrame;if(x==null||f.afterCard)return;
  if(loss>0&&(c.baseId()==144||x.fist&&x.carry.getOrDefault("fistLifeSteal",0)>0))heal(ch,f,loss*60/100);
  if(c.id()==10030087||c.id()==10040087||x.growth>0){if(loss>0)physique(ch,f,loss*40/100);}
  if(c.baseId()==10000109)heal(ch,f,div(loss,c.param(0)));
 }
 static void after(List<Change> ch,Fighter f,Rules.Card c){
  if(c.sect()!=4)return;Fighter d=f.opponent;int b=c.baseId(),n=c.param(0);var x=f.forgeFrame;
  if(c.anima()>0&&!Set.of(205,297,336,10000006,10000042,10000073,10000091,10000096,10000108).contains(b))addAnima(ch,f,c.anima());
  if(c.defense()>0&&!Set.of(291,337,10000021,10000031,10000072,10000075,10000083,10000088).contains(b))addDefense(ch,f,c.defense());
  if(c.physique()>0&&!Set.of(205,207,339,10000006,10000016,10000027,10000048,10000066,10000072,10000075,10000086,10000087,10000090,10000096,10000099,10000100,10000104).contains(b))physique(ch,f,c.physique());
  switch(b){
   case 144 -> addStatus(ch,f,"fistLifeSteal",1);
   case 154 -> {addStatus(ch,f,"force",n);addStatus(ch,f,"nextPhysiqueAttack",Math.min(c.param(2),div(f.status("physique"),c.param(1))));}
   case 166,170,275,339,10000004,10000005,10000017,10000028,10000029,10000047,10000067,10000068,10000078,10000097,10000099,10000103,10000104,10000107,10000109 -> {
    if(b==10000017)addDefense(ch,f,Math.min(c.param(1),div(f.status("physique"),n)));
   }
   case 174 -> {addStatus(ch,d,"noHealing",c.param(1));if(f.woundedHits>0)addStatus(ch,d,"destinyAxe",n);}
   case 204 -> {int old=f.ignore;f.ignore+=n;change(ch,f,"ignoreDefense",old,f.ignore);addStatus(ch,f,"agility",c.param(1));}
   case 205 -> {addAnima(ch,f,c.anima());int extra=Math.min(c.param(1),div(f.anima,n));physique(ch,f,c.physique());physique(ch,f,extra);}
   case 206 -> {addStatus(ch,f,"agility",n);CareerEffects.both(ch,f,"basicUpgrade",1);addStatus(ch,f,"qinUsed",1);}
   case 207 -> {physique(ch,f,c.physique());addStatus(ch,f,"physiqueCap",n);addStatus(ch,f,"attackBonus",c.param(1));}
   case 208 -> {addStatus(ch,f,"forceCap",c.param(2));addStatus(ch,f,"forceFormation",n);setStatus(ch,f,"forceFormationGain",c.param(1));}
   case 209 -> {heal(ch,f,c.param(1));addStatus(ch,f,"debuffReflect",n);}
   case 210 -> {addStatus(ch,f,"returnGu",1);setStatus(ch,f,"returnGuAttack",n);}
   case 277 -> {heal(ch,f,c.param(2));addStatus(ch,f,"overflowPhysiqueHeal",n);addStatus(ch,f,"overflowPhysiqueDamage",c.param(1));}
   case 278 -> {heal(ch,f,n);addStatus(ch,f,"animaAttackCards",c.param(1));}
   case 291 -> {addStatus(ch,f,"forceCap",n);addStatus(ch,f,"force",n);addDefense(ch,f,c.defense()+f.status("force")*c.param(1));heal(ch,f,f.status("agility")*c.param(2));setStatus(ch,f,"agility",Math.min(5,f.status("agility")));}
   case 296 -> {CareerEffects.both(ch,f,"internalInjury",n);CareerEffects.both(ch,f,"externalInjury",c.param(1));CareerEffects.both(ch,f,"weakness",c.param(2));}
   case 297,10000015,10000022,10000030,10000033 -> {if(b==10000015)addStatus(ch,f,"fistShatter",n);}
   case 308 -> addStatus(ch,f,"fistAttack",div(f.status("physique"),n));
   case 310 -> {addStatus(ch,f,"agility",c.param(2));int before=StarEffects.debuffs(f);cleanseRandom(ch,f,n,false);int diff=before-StarEffects.debuffs(f);addStatus(ch,f,"agility",diff*c.param(1));addStatus(ch,f,"force",diff);}
   case 318 -> {CareerEffects.both(ch,f,"internalInjury",n);CareerEffects.both(ch,f,"injuryExtra",div(f.status("physique"),c.param(1)));}
   case 319 -> addStatus(ch,f,"fistChanAttack",c.attack());
   case 336 -> {addStatus(ch,f,"attackBonus",n);addStatus(ch,f,"attackReduction",n);addAnima(ch,f,c.anima());addStatus(ch,f,"agility",StarEffects.debuffs(f));}
   case 337 -> {damage(ch,d,f.def);addStatus(ch,f,"fistTiger",1);addDefense(ch,f,c.defense());}
   case 338 -> {addStatus(ch,f,"agility",n);addStatus(ch,f,"skipCostCards",c.param(1));setStatus(ch,f,"skipCostDamage",c.param(2));}
   case 10000001 -> addStatus(ch,f,"fistAttack",n);
   case 10000002 -> addStatus(ch,f,"fistDefense",c.defense());
   case 10000003 -> addStatus(ch,f,"fistRefund",n);
   case 10000006 -> {physique(ch,f,c.physique());addAnima(ch,f,c.anima());heal(ch,f,n);}
   case 10000007 -> addStatus(ch,f,"internalInjury",n);
   case 10000008 -> addStatus(ch,f,"externalInjury",n);
   case 10000009 -> cleanseRandom(ch,f,n,true);
   case 10000010 -> addStatus(ch,f,"force",n);
   case 10000011 -> {if(spend(ch,f,1)>0)addStatus(ch,f,"force",c.param(1));}
   case 10000012 -> { }
   case 10000013 -> addStatus(ch,f,"fistForce",n);
   case 10000014 -> addStatus(ch,f,"fistInjury",n);
   case 10000016 -> {if(f.status("physique")>=n)addDefense(ch,f,c.param(1));physique(ch,f,c.physique());}
   case 10000018 -> addStatus(ch,f,"vulnerability",n);
   case 10000019 -> addStatus(ch,f,"weakness",n);
   case 10000020 -> {addStatus(ch,f,"force",n);addStatus(ch,f,"forceBeforeAttack",c.param(1));}
   case 10000021 -> {addStatus(ch,f,"force",n);if(spend(ch,f,1)>0)heal(ch,f,c.param(1));addDefense(ch,f,c.defense());}
   case 10000023 -> addStatus(ch,f,"repel",n);
   case 10000024 -> addStatus(ch,f,"fistTransfer",n);
   case 10000025 -> addStatus(ch,f,"fistDoubleForce",1);
   case 10000026,10000098 -> {addStatus(ch,f,"agility",n);setStatus(ch,f,"allFist",1);}
   case 10000027 -> {heal(ch,f,n);physique(ch,f,c.physique());addStatus(ch,f,"boneCharges",c.param(1));setStatus(ch,f,"boneAttack",f.rules.byId.get(10000027).param(2));setStatus(ch,f,"bonePhysique",1);}
   case 10000031 -> {addStatus(ch,f,"binding",n);addDefense(ch,f,c.defense()+StarEffects.debuffs(f)*c.param(1));}
   case 10000032 -> heal(ch,f,n);
   case 10000034 -> {addStatus(ch,f,"force",n);addStatus(ch,f,"agility",c.param(1));}
   case 10000035 -> { }
   case 10000036 -> {addStatus(ch,f,"fistAttack",n);addStatus(ch,f,"fistDefense",n);}
   case 10000037 -> heal(ch,f,f.maxHp*n/100);
   case 10000038 -> {heal(ch,f,c.param(2));addStatus(ch,f,"agility",Math.min(c.param(1),div(f.status("physique"),n)));}
   case 10000039 -> {addStatus(ch,f,"attackReduction",n);addStatus(ch,f,"agility",c.param(1));}
   case 10000040 -> {int drain=n+Math.min(n,StarEffects.debuffs(f));loseHp(ch,d,drain);heal(ch,f,drain);}
   case 10000041 -> {CareerEffects.both(ch,f,"internalInjury",n);int gain=div(StarEffects.debuffs(f),c.param(1));addAnima(ch,f,gain);heal(ch,f,gain);}
   case 10000042 -> {addAnima(ch,f,c.anima());addStatus(ch,f,"force",f.anima);addStatus(ch,f,"agility",n);}
   case 10000043 -> {int force=f.status("force");setStatus(ch,f,"force",0);addDefense(ch,f,force*n);damage(ch,d,force*c.param(1));addStatus(ch,f,"agility",c.param(2));}
   case 10000044 -> {addStatus(ch,f,"force",n);addStatus(ch,f,"costForce",c.param(1));}
   case 10000045 -> {addStatus(ch,f,"agility",n);addStatus(ch,f,"fistAgility",n);}
   case 10000046 -> addStatus(ch,f,"fistShock",n);
   case 10000048 -> {physique(ch,f,c.physique());if(f.status("physique")>=n)addStatus(ch,f,"attackBonus",c.param(1));if(f.status("physique")>=c.param(2))addStatus(ch,f,"agility",c.param(3));}
   case 10000050 -> {addStatus(ch,f,"agility",n);addStatus(ch,f,"lossResourceAnima",c.param(1));addStatus(ch,f,"lossResourceAgility",c.param(2));}
   case 10000051 -> {addStatus(ch,f,"forceCap",n);addStatus(ch,f,"force",c.param(1));}
   case 10000052 -> {heal(ch,f,n);addStatus(ch,f,"agility",c.param(1)+f.status("force")*c.param(2));}
   case 10000053 -> {addStatus(ch,f,"forceRefill",n);if(f.status("force")==0)addStatus(ch,f,"force",f.status("forceRefill"));}
   case 10000054 -> {modifyMaxHp(ch,f,-n);modifyMaxHp(ch,d,-n);}
   case 10000055 -> {growCareer(ch,f,n);addStatus(ch,f,"poisonHeal",c.param(1));}
   case 10000056 -> {addStatus(ch,f,"agility",n);addStatus(ch,f,"basicExtra",c.param(1));}
   case 10000057 -> {int gain=div(f.status("physique"),n);addStatus(ch,f,"attackBonus",gain);addStatus(ch,f,"internalInjury",gain);}
   case 10000060 -> addStatus(ch,f,"fistRepeat",1);
   case 10000061 -> {addStatus(ch,f,"force",c.param(1));addStatus(ch,f,"guard",c.param(1));addStatus(ch,f,"attackBonus",c.param(1));modifyMaxHp(ch,f,-((f.maxHp*n+99)/100));}
   case 10000062 -> {addStatus(ch,f,"agility",n);int total=StarEffects.debuffs(f);for(String k:CareerEffects.NEGATIVE)setStatus(ch,f,k,0);physique(ch,f,total*c.param(1));heal(ch,f,total*c.param(1));}
   case 10000064 -> {for(String k:List.of("internalInjury","vulnerability","weakness","binding","externalInjury"))CareerEffects.both(ch,f,k,n);}
   case 10000065 -> { }
   case 10000066 -> {addStatus(ch,f,"basicGrowth",div(f.status("physique"),n));physique(ch,f,c.physique());}
   case 10000069 -> {heal(ch,f,n);addStatus(ch,f,"dreamDebuff",c.level()<=3?1:2);if(c.level()==5)setStatus(ch,f,"dreamDebuffHigh",1);}
   case 10000070 -> {heal(ch,f,n);CareerEffects.both(ch,f,"internalInjury",c.level()<=3?c.param(1):div(f.status("physique"),c.param(1)));}
   case 10000071 -> addStatus(ch,f,"fistAttack",c.level()<3?n:div(f.status("defenseGained"),n));
   case 10000072 -> {addDefense(ch,f,c.defense()+(c.level()>=4?f.anima:0));physique(ch,f,c.physique()+(c.level()>=4?f.anima:0));}
   case 10000073 -> {addStatus(ch,f,"agility",c.param(1)+(c.level()>=4?f.anima:0));addAnima(ch,f,c.anima());heal(ch,f,n);}
   case 10000074 -> addStatus(ch,f,"fistAttack",x.bonus);
   case 10000075 -> {physique(ch,f,c.physique());addDefense(ch,f,c.level()<=3?c.defense():f.maxHp*n/100);}
   case 10000076 -> {if(c.level()<=4)addStatus(ch,f,"costDefenseCharges",n);else addStatus(ch,f,"costDefenseAlways",1);}
   case 10000077 -> {if(c.level()>=4)addStatus(ch,f,"agility",c.param(1));}
   case 10000079 -> { }
   case 10000080 -> addStatus(ch,f,"agility",n);
   case 10000081 -> { }
   case 10000082 -> {if(c.level()<=3){addStatus(ch,f,"fistFollowup",n);}}
   case 10000083 -> addDefense(ch,f,c.defense()+StarEffects.debuffs(f)*n);
   case 10000084 -> {if(c.level()<=3)heal(ch,f,c.param(1));else addStatus(ch,f,"forceCap",c.param(1));addStatus(ch,f,"force",n);}
   case 10000085 -> {addStatus(ch,f,"force",c.param(1));if(c.level()<=4)addStatus(ch,f,"forceBeforeAttack",n);else addStatus(ch,f,"forceEveryAttack",1);}
   case 10000086 -> {int q=f.status("force");if(c.level()<=3){if(q>0||f.anima>0){physique(ch,f,n);heal(ch,f,c.param(1));}}else{addStatus(ch,f,"agility",c.param(3));if(q>0){setStatus(ch,f,"force",0);physique(ch,f,q*c.param(1));heal(ch,f,q*c.param(2));}}}
   case 10000087 -> {if(c.level()<=3){physique(ch,f,c.physique());addStatus(ch,f,"nextPhysique",c.physique());}else addStatus(ch,f,"dreamFistGrowth",1);}
   case 10000088 -> {addDefense(ch,f,c.defense());if(c.level()<=3)addStatus(ch,f,"defenseDamage",c.param(1));else addStatus(ch,f,"defenseDamagePermanent",c.param(1));}
   case 10000089 -> {int extra=c.level()<=3?0:CareerEffects.parameter(f,0,c.param(1));addStatus(ch,f,"fistDreamCount",n+extra);}
   case 10000090 -> {addStatus(ch,f,"vulnerability",c.param(1));physique(ch,f,c.physique());heal(ch,f,n);if(c.rarity()>=2||f.status("drunkStance")==0)addStatus(ch,f,"drunkStance",c.param(2));}
   case 10000091 -> {growCareer(ch,f,(f.anima+StarEffects.debuffs(f))*c.param(1));addAnima(ch,f,c.anima());addStatus(ch,f,"internalInjury",n);}
   case 10000092 -> {int paid=spend(ch,f,c.param(1));addStatus(ch,f,"agility",paid*c.param(2));cleanseRandom(ch,f,paid*c.param(3),false);}
   case 10000093 -> addStatus(ch,f,"fistCostDamage",1);
   case 10000094 -> { }
   case 10000095 -> {heal(ch,f,n);addStatus(ch,f,"agility",c.param(1));addStatus(ch,f,"costAgility",1);}
   case 10000096 -> {physique(ch,f,c.physique()+div(f.anima,n));addAnima(ch,f,c.anima());}
   case 10000100 -> {addStatus(ch,f,"boneCharges",n);setStatus(ch,f,"boneAttack",f.rules.byId.get(10000027).param(2));setStatus(ch,f,"bonePhysique",1);addStatus(ch,f,"agility",c.param(2));physique(ch,f,c.physique());}
   case 10000101 -> {CareerEffects.both(ch,f,"weakness",n);}
   case 10000102,10000110 -> addStatus(ch,f,"attackBonus",n);
   case 10000105 -> {addStatus(ch,f,"agility",n);addStatus(ch,f,"fistVulnerability",c.param(1));}
   case 10000106 -> {addStatus(ch,f,"attackBonus",n);int paid=spend(ch,f,c.param(1)),q=Math.min(c.param(1)-paid,f.status("force"));addStatus(ch,f,"force",-q);addStatus(ch,f,"agility",(paid+q)*c.param(2));}
   case 10000108 -> {addAnima(ch,f,c.anima());heal(ch,f,c.param(2));addStatus(ch,f,"internalInjury",n);addStatus(ch,f,"agility",Math.min(c.param(1),f.anima+StarEffects.debuffs(f)));}
   default -> throw new Rejected("UNSUPPORTED","锻玄卡牌尚无效果分支："+c.id());
  }
 }
 static List<Integer> followups(List<Change> ch,Fighter f,Rules.Card c){
  var list=new ArrayList<Integer>();if(c.baseId()==10000012&&spend(ch,f,1)>0)list.add(c.param(0));
  if(c.baseId()==10000081&&c.level()<=4&&f.status("defenseGained")>0)for(int i=0;i<c.param(1);i++)list.add(c.param(0));return list;
 }
 static List<Integer> globalAfter(List<Change> ch,Fighter f,Rules.Card c){
  var list=new ArrayList<Integer>();var x=f.forgeFrame;if(x==null)return list;
  if(x.fist){int shock=x.carry.getOrDefault("fistShock",0);if(shock>0)list.add(shock+div(f.status("hpLost"),f.rules.byId.get(10000046).param(1)));for(String key:List.of("fistFollowup","fistChanAttack")){int a=x.carry.getOrDefault(key,0);if(a>0){list.add(a);list.add(a);}}for(int i=0;i<Math.min(999,x.carry.getOrDefault("fistDreamCount",0));i++)list.add(2);if(x.carry.getOrDefault("fistTiger",0)>0)damage(ch,f.opponent,f.def);
   if(!x.retain&&x.hits>0){addStatus(ch,f,"fistAttack",-x.carry.getOrDefault("fistAttack",0));if(x.carry.getOrDefault("fistLifeSteal",0)>0)addStatus(ch,f,"fistLifeSteal",-1);}
  }
  if(c.hpCost()>0){if(f.status("costDefenseAlways")>0)addDefense(ch,f,c.hpCost());if(f.status("costDefenseCharges")>0){addStatus(ch,f,"costDefenseCharges",-1);addDefense(ch,f,c.hpCost());}addStatus(ch,f,"costCardsUsed",1);}
  if(x.hits>0&&x.growth>0)addStatus(ch,f,"dreamFistGrowth",-1);
  if(isBasic(c))setStatus(ch,f,"basicExtra",0);
  if(f.status("animaAttackCards")>0&&c.baseId()!=278)addStatus(ch,f,"animaAttackCards",-1);
  return list;
 }
 static void start(List<Change> ch,Fighter f){
  setStatus(ch,f,"lossResourceUsed",0);setStatus(ch,f,"dreamDebuffUsed",0);setStatus(ch,f,"defenseDamageUsed",0);
  if(f.status("forceFormation")>0){addStatus(ch,f,"forceFormation",-1);int n=f.status("forceFormationGain");addAnima(ch,f,n);addStatus(ch,f,"force",n);}
  
 }
 static void extraInjury(List<Change> ch,Fighter f){int extra=f.status("injuryExtra");setStatus(ch,f,"injuryExtra",0);for(int i=0;i<Math.min(999,extra)&&f.hp>0;i++)loseHp(ch,f,f.status("internalInjury"));}
 static void end(List<Change> ch,Fighter f){heal(ch,f,Math.min(f.status("poisonHeal"),StarEffects.debuffs(f)));}
 static void changed(List<Change> ch,Fighter f,String key,int old,int value){
  if(value==old)return;
  if(key.equals("force")){if(value<old)addStatus(ch,f,"forceLost",old-value);if(value==0&&f.status("forceRefill")>0)addStatus(ch,f,"force",f.status("forceRefill"));}
  if(key.equals("vulnerability")&&value<old)addStatus(ch,f,"attackBonus",(old-value)*f.status("drunkStance"));
  if(value>old&&CareerEffects.NEGATIVE.contains(key)&&f.resolution!=null){
   if(f.status("debuffReflect")>0){addStatus(ch,f,"debuffReflect",-1);int n=value-old;f.resolution.enqueue(()->addStatus(ch,f.opponent,key,n));}
   if(f.resolution.turn>0&&f.status("dreamDebuff")>f.status("dreamDebuffUsed")&&f.status("ownTurns")>0&&f==f.resolution.active){addStatus(ch,f,"dreamDebuffUsed",1);int n=f.status("dreamDebuffHigh")>0?Math.min(2,value-old):1;f.resolution.enqueue(()->addStatus(ch,f.opponent,key,n));}
  }
 }
 static void resourceChanged(List<Change> ch,Fighter f,String key,int old,int value){
  if(key.equals("hp")&&value<old&&!f.capClamping&&f.status("lossResourceUsed")==0&&f.resolution!=null&&f.resolution.active==f){setStatus(ch,f,"lossResourceUsed",1);addAnima(ch,f,f.status("lossResourceAnima"));addStatus(ch,f,"agility",f.status("lossResourceAgility"));}
  if(value>old&&f.resolution!=null&&(key.equals("defense")||key.equals("healingAttempt"))){int n=key.equals("defense")?f.status("defenseDamage"):0;if(f.status("defenseDamagePermanent")>0)n+=(value-old)*f.status("defenseDamagePermanent")/100;if(n>0){int v=n;f.resolution.enqueue(()->damage(ch,f.opponent,v));}}
 }
}
