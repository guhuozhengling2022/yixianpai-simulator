package local.yxp;
import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Audited battle-only portions of selected talents. Permanent round rewards are never reapplied. */
final class TalentEffects {
 static final Map<String,String> STATES=Map.of("talentAnimaChaseUsed","剑灵极影已触发","talentHexPending","灵卦自衍待结算","talentPhysiqueBudgetUsed","风灵锻躯已用额度","talentMirrorUsed","镜中玄影已触发","talentEclipse","星蚀待触发层数","talentCycleUpgrades","相生相成升级次数","flameRevive","焰魂归复次数","burningSeal","疾燃咒印次数","reviveBlocked","禁止复活","arrayEcho","灵阵回响次数");
 static boolean has(Fighter f,int id){return f.p.talentIds()!=null&&f.p.talentIds().contains(id);}
 static Rules.Talent talent(Fighter f,int id){return f.rules.talentById.get(id);}
 static int param(Fighter f,int id,int index){var t=talent(f,id);return t==null?0:t.param(index);}
 static List<Rules.Talent> selected(Fighter f){if(f.p.talentIds()==null||f.p.talentIds().isEmpty())return List.of();return f.p.talentIds().stream().map(f.rules.talentById::get).filter(Objects::nonNull).toList();}
 static void opening(List<Change> ch,Fighter f,Rules.Talent t){
  int b=t.id()%10000,n=t.param(0);var d=f.opponent;
  switch(b){
   case 3 -> addDefense(ch,f,n);
   case 13 -> addAnima(ch,f,n);
   case 16 -> addSword(ch,f,t.param(1));
   case 25 -> {int old=f.ignore;f.ignore+=n;change(ch,f,"ignoreDefense",old,f.ignore);}
   case 26,134 -> addStatus(ch,f,"guard",b==134?1:n);
   case 30 -> addStatus(ch,f,"hexagram",n);
   case 31 -> addStatus(ch,f,"starPower",t.param(1));
   case 220 -> {int count=0;for(int id:f.p.hand())if(f.isCloud(f.rules.byId.get(id)))count++;for(int id:f.cards)if(f.isCloud(f.rules.byId.get(id)))count++;addAnima(ch,f,Math.min(count/2,n));}
   case 52 -> {if(f.cards.length==8&&f.rules.byId.get(f.cards[7]).baseId()==0)f.cards[7]=11;}
   case 279 -> addStatus(ch,f,"arrayEcho",1);
   case 33,106 -> StarEffects.addStar(ch,f,6);
   case 70 -> addStatus(ch,f,"ragingCount",t.param(1));
   case 82 -> addStatus(ch,f,"activateNext",1);
   case 88 -> {activate(ch,f,"earthActivation");addDefense(ch,f,n);}
   case 99 -> {activate(ch,f,"waterActivation");addAnima(ch,f,n);}
   case 103 -> addStatus(ch,d,"talentEclipse",n);
   case 127 -> addStatus(ch,f,"talentCycleUpgrades",t.param(1));
   case 199 -> {
       ContextEffects.activateBottleCard(ch,f,"bottleFirst");
       if (SeasonTalentEffects.has(f,23058)) ContextEffects.activateBottleCard(ch,f,"bottleSecond");
   }
   case 203 -> activate(ch,f,"woodActivation");
   case 139 -> addStatus(ch,f,"flameRevive",1);
   case 140 -> addStatus(ch,f,"burningSeal",1);
   case 145 -> {addStatus(ch,f,"force",n);addStatus(ch,f,"forceCap",t.param(1));}
   case 147 -> addStatus(ch,f,"agility",n);
   case 150 -> {addStatus(ch,f,"internalInjury",n);addStatus(ch,f,"recovery",t.param(1));}
   case 171 -> {addStatus(ch,f,"attackBonus",t.param(1));addStatus(ch,f,"externalInjury",t.param(1));}
   case 176 -> heal(ch,f,ForgeEffects.div(f.status("physique"),t.param(1)));
   case 179 -> addStatus(ch,f,"underworld",t.param(1));
   case 183 -> ForgeEffects.physique(ch,f,n);
   case 248 -> {int old=f.cloud;f.cloud+=n;change(ch,f,"cloudChain",old,f.cloud);addStatus(ch,f,"cloudSea",n);}
   case 249 -> {for(int slot=0;slot<7;slot++)if((f.status("starMask")&(1<<slot))==0)StarEffects.addStar(ch,f,slot);}
   case 253 -> {activate(ch,f,"fireActivation");reduceLifeAndCap(ch,d,n);}
   case 254 -> {activate(ch,f,"metalActivation");addStatus(ch,f,"sharpness",n);}
   case 255 -> {activate(ch,f,"woodActivation");addStatus(ch,f,"attackBonus",n);}
   case 277 -> growCareer(ch,f,ElementalEffects.elements(f,f.rules)*n);
   default -> { }
  }
 }
 static void start(List<Change> ch,Fighter f){setStatus(ch,f,"talentHexPending",0);}
 static void end(List<Change> ch,Fighter f){if(has(f,66)&&f.turnAttacks==0)addDefense(ch,f,param(f,66,0));if(has(f,195)&&f.status("talentHexPending")>0){addStatus(ch,f,"hexagram",1);setStatus(ch,f,"talentHexPending",0);}}
 static void shortage(List<Change> ch,Fighter f,Rules.Card c){addAnima(ch,f,(has(f,142)?param(f,142,0):1)+c.chargeQi());if(has(f,142))addStatus(ch,f,"hexagram",param(f,142,1));}
 static Rules.Card echo(Fighter f,Rules.Card c){
  if(f.status("arrayEcho")==0||!c.name().contains("灵阵"))return null;
  var base=f.rules.byId.get(c.rarity()>0?c.baseId():c.id());
  if(base==null||!base.supported())throw new Rejected("UNSUPPORTED","灵阵回响的1级牌尚未支持："+c.baseId());return base;
 }
 static boolean seal(List<Change> ch,Fighter f,Rules.Card c,boolean virtual){
  if(virtual||f.status("burningSeal")==0||!c.name().contains("印"))return false;
  addStatus(ch,f,"burningSeal",-1);f.requestChase=true;f.consumed[f.slot]=true;return true;
 }
 static void before(List<Change> ch,Fighter f,Rules.Card c,boolean virtual){

  for(var t:selected(f)){int b=t.id()%10000,n=t.param(0);switch(b){
   case 15 -> {if(f.isCloud(c))addAnima(ch,f,1);}
   case 57 -> {if(f.slot==0&&!virtual){addDefense(ch,f,n);addStatus(ch,f,"moonRounds",1);}}
   case 60 -> {if(f.slot==0)modifyMaxHp(ch,f,n);}
   case 62 -> {if(c.name().contains("星弈"))addStatus(ch,f,"hexagram",n);}
   case 69 -> {if(f.isCloud(c))addDefense(ch,f,n);}
   case 101,102 -> {int element=ElementalEffects.element(c.name());if(ElementalEffects.generates(f,f.status("lastElement"),element)){
    if(b==101){addAnima(ch,f,n);growCareer(ch,f,t.param(1));}
    else switch(t.id()){case 102 -> addDefense(ch,f,n);case 10102 -> addAnima(ch,f,n);case 20102 -> growCareer(ch,f,n);case 30102 -> addStatus(ch,f,"sharpness",n);default -> throw new IllegalStateException("Unknown five cycle variant");}
   }}
   case 149 -> {if(f.slot==0&&!virtual){ForgeEffects.physique(ch,f,n);heal(ch,f,t.param(1));}}
   case 200 -> {if(ElementalEffects.element(c.name())==0)activate(ch,f,"woodActivation");}
   case 246 -> {if(f.isFormation(c))damage(ch,f.opponent,n);}
   case 263 -> {if(c.name().contains("灵印")){addAnima(ch,f,1);heal(ch,f,1);addDefense(ch,f,1);addStatus(ch,f,"sharpness",1);modifyMaxHp(ch,f.opponent,-1);}}
   default -> { }
  }}
 }
 static boolean ignore(Fighter f,Rules.Card c){return !f.afterCard&&has(f,250)&&c.name().contains("雷");}
 static boolean shatter(Fighter f,Rules.Card c){return !f.afterCard&&has(f,247)&&c.name().contains("灵剑");}
 static boolean forceWound(Fighter f){return has(f,67);}
 static void beforeAttack(List<Change> ch,Fighter f){if(has(f,68))addDefense(ch,f,param(f,68,0)+(f.def==0?param(f,68,1):0));if(has(f,67))modifyMaxHp(ch,f.opponent,-param(f,67,0));}
 static List<Integer> after(Fighter f,Rules.Card c){return has(f,173)&&c.physique()>0?List.of(param(f,173,1)):List.of();}
 static int supplementCost(List<Change> ch,Fighter f,int animaDelta){if(has(f,153)&&f.anima+animaDelta<0){int loss=-(f.anima+animaDelta)*param(f,153,0);if(loss<f.hp){costHp(ch,f,loss);return -f.anima;}}return animaDelta;}
 static void costHp(List<Change> ch,Fighter f,int value){int old=f.hp;f.hp-=value;change(ch,f,"hp",old,f.hp);}
 static void physique(List<Change> ch,Fighter f,int delta){if(delta>0&&has(f,184))addDefense(ch,f,Math.min(delta,param(f,184,1)));}
 static void forceChanged(List<Change> ch,Fighter f,int delta){if(delta!=0&&has(f,257))addDefense(ch,f,Math.abs(delta));}
 static int defenseGain(Fighter f,int delta){return delta>0&&has(f,38)?delta+param(f,38,0):delta;}
 static void healAttempt(List<Change> ch,Fighter f,int amount){if(amount>0&&has(f,64))addDefense(ch,f,param(f,64,0));}
 static int debuffGain(List<Change> ch,Fighter f,String key,int delta){
  if(delta>0&&CareerEffects.NEGATIVE.contains(key)&&!key.equals("attackReduction")&&!key.equals("underworld")&&f.status("talentEclipse")>0){int extra=f.status("talentEclipse");setStatus(ch,f,"talentEclipse",0);delta+=extra;}
  return delta>0&&key.equals("internalInjury")&&f.opponent!=null&&has(f.opponent,251)?delta+1:delta;
 }
 static void changed(List<Change> ch,Fighter f,String key,int old,int value){if(f.p.talentIds()==null||f.p.talentIds().isEmpty())return;if(++f.resolution.talentDepth>64){f.resolution.talentDepth--;throw new Rejected("RESOLUTION_LIMIT","仙命回调嵌套超过64层，无法有限结算该组合");}try{
  if(value==old)return;
  if(value>old&&key.equals("hexagram")){if(has(f,36))addAnima(ch,f,param(f,36,0));if(has(f,61)&&f.status("starPower")==0)addStatus(ch,f,"starPower",param(f,61,0));}
  if(value>old&&CareerEffects.NEGATIVE.contains(key)){
   if(has(f,177))heal(ch,f,(value-old)*param(f,177,0));
   if(!key.equals("underworld")&&has(f,256)&&f.status("talentMirrorUsed")==0){setStatus(ch,f,"talentMirrorUsed",1);int n=Math.min(value-old,param(f,256,0));f.resolution.enqueue(()->addStatus(ch,f.opponent,key,n));}
  }
  int e=Arrays.asList(ElementalEffects.ACTIVATION).indexOf(key);if(e>0&&value>old){
   for(var t:selected(f))if(t.id()%10000==79)switch(t.id()){case 79 -> addDefense(ch,f,t.param(0));case 10079 -> heal(ch,f,t.param(0));case 20079 -> addStatus(ch,f,"sharpness",t.param(0));case 30079 -> addAnima(ch,f,t.param(0));default -> { }}
   if(has(f,112)&&value==1)switch(e){case 1 -> addStatus(ch,f,"sharpness",4);case 2 -> addStatus(ch,f,"waterForce",2);case 3 -> addStatus(ch,f,"attackBonus",1);case 4 -> reduceLifeAndCap(ch,f.opponent,7);case 5 -> addDefense(ch,f,12);default -> { }}
   if(has(f,138))reduceLifeAndCap(ch,f.opponent,param(f,138,1)+(e==4?param(f,138,2):0));
   if(has(f,200)&&e==3)modifyMaxHp(ch,f,2);if(has(f,202)&&value==1)addAnima(ch,f,1);
  }
 }finally{f.resolution.talentDepth--;}
 }
 static void resources(List<Change> ch,Fighter f,String key,int before,int after){
  if(key.equals("hp")&&after<before&&!f.capClamping&&has(f,64))addDefense(ch,f,param(f,64,0));
  if(!key.equals("anima")||after<=before)return;int delta=after-before;
  if(has(f,47)&&f.status("talentAnimaChaseUsed")==0&&delta>=param(f,47,0)){setStatus(ch,f,"talentAnimaChaseUsed",1);f.requestChase=true;}
  if(has(f,195))setStatus(ch,f,"talentHexPending",1);
  if(has(f,261)){modifyMaxHp(ch,f,param(f,261,0));heal(ch,f,param(f,261,1));}
  if(has(f,267)){int gain=Math.min(delta,Math.max(0,param(f,267,0)-f.status("talentPhysiqueBudgetUsed")));addStatus(ch,f,"talentPhysiqueBudgetUsed",gain);ForgeEffects.physique(ch,f,gain);}
 }
}
