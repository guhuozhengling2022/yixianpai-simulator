<script setup lang="ts">
import { computed, ref } from 'vue';
import type { SeasonChoice } from './SeasonTalentSlots.vue';
import DeckSolver from './DeckSolver.vue';

export type CachedSide = {
  uid:string; name:string; characterId:number; hp:number; maxHp?:number; realm:number; destiny:number;
  experience:number; career:number; physique:number; physiqueCap:number; cards:number[];
  hand:number[]; talents:number[]; seasonTalents:SeasonChoice[]; plantBuffs?:Record<string,number>; embryoGrowth?:number; deathBattleWill?:number;
};
export type CachedRound = { number:number; own:CachedSide; enemy:CachedSide; first:string; winner:string; destinyLoss:number };
type Layout = { cards:number[]; hand:number[]|null };
const props = defineProps<{
  cards:Map<number,{name:string;rarity:number}>;
  characters:{id:number;name:string}[];
  talents:{id:number;name:string}[];
  seasonTalents:{id:number;name:string}[];
  plantRefinements?:{key:string;name:string}[];
  importError:string;
  buildRequest:(round:CachedRound)=>{players:Layout[]};
}>();
const emit = defineEmits<{ importRound:[round:CachedRound, layout?:Layout] }>();
const code=ref(''), busy=ref(false), error=ref('');
const rounds=ref<CachedRound[]>([]), selected=ref(0);
const lossesOnly=ref(false);
const isLoss=(item:CachedRound)=>Boolean(item.enemy.uid && item.winner===item.enemy.uid && item.own.uid && item.own.uid!==item.enemy.uid);
const losses=computed(()=>rounds.value.filter(isLoss));
const visibleRounds=computed(()=>lossesOnly.value?losses.value:rounds.value);
const round=computed(()=>visibleRounds.value.find(item=>item.number===selected.value)??visibleRounds.value[0]);
const solver=computed(()=>{
  if(!round.value || !isLoss(round.value))return null;
  try{return {input:props.buildRequest(round.value)}}
  catch(cause){return {message:cause instanceof Error?cause.message:'该轮无法求解。'}}
});
const realms=['未知','炼气','筑基','金丹','元婴','化神','返虚'];
function cardName(id:number){
  const card=props.cards.get(id);
  return card ? `${card.name} · ${card.rarity+1}级` : `#${id}`;
}
function names(ids:number[], catalog:{id:number;name:string}[]){
  return ids.map(id=>catalog.find(item=>item.id===id)?.name ?? `#${id}`).join('、') || '无记录';
}
function playerName(uid:string){
  if(!uid || !round.value) return '无记录';
  return [round.value.own,round.value.enemy].find(side=>side.uid===uid)?.name || '无记录';
}
async function load(){
  busy.value=true;
  error.value='';
  rounds.value=[];
  try{
    const response=await fetch('/api/cached-battle?'+encodeURIComponent(code.value.trim()));
    const data=await response.json();
    if(!response.ok) throw new Error(data.message ?? '读取战绩失败');
    rounds.value=data.rounds;
    selected.value=data.selectedRound;
  }catch(cause){
    error.value=cause instanceof Error ? cause.message : '读取战绩失败';
  }finally{busy.value=false;}
}
</script>

<template>
  <section class="cached-battles">
    <h2>战绩码</h2>
    <form @submit.prevent="load">
      <label>战绩码 <input v-model.trim="code" required maxlength="13"></label>
      <button type="submit" :disabled="busy">{{busy?'读取中…':'从本机导入'}}</button>
    </form>
    <p v-if="error" role="alert">{{error}}</p>
    <button v-if="rounds.length" type="button" :aria-pressed="lossesOnly" @click="lossesOnly=!lossesOnly">
      {{lossesOnly?'显示全部轮次':`只看败局（${losses.length}）`}}
    </button>
    <p v-if="rounds.length && !visibleRounds.length">这份战绩没有可确认的败局。</p>
    <template v-if="round">
      <label>查看轮次
        <select :value="round.number" @change="selected=Number(($event.target as HTMLSelectElement).value)">
          <option v-for="item in visibleRounds" :key="item.number" :value="item.number">第 {{item.number}} 轮</option>
        </select>
      </label>
      <button type="button" @click="emit('importRound',round)">导入本轮双方到模拟器</button>
      <p v-if="importError" role="alert">{{importError}}</p>
      <p>先手 {{playerName(round.first)}} · 胜方 {{playerName(round.winner)}} · 命元伤害 {{round.destinyLoss}}</p>
      <DeckSolver v-if="solver?.input" :key="round.number" compact :input="solver.input" :card-names="cards"
        @apply="emit('importRound',round,$event)"/>
      <p v-else-if="solver?.message" role="status">{{solver.message}}</p>
      <div class="cached-sides">
        <article v-for="(side,index) in [round.own,round.enemy]" :key="index">
          <h3>{{index===0?'我方':'敌方'}} · {{side.name || '无记录'}}</h3>
          <p>{{characters.find(c=>c.id===side.characterId)?.name ?? '未知角色'}} · {{realms[side.realm] ?? '未知境界'}}</p>
          <p>开场生命 {{side.hp}} / {{side.maxHp ?? side.hp+side.physique}} · 命元 {{side.destiny}} · 修为 {{side.experience}} · 体魄 {{side.physique}} / {{side.physiqueCap}}</p>
          <h4>卡组</h4>
          <ol><li v-for="(id,slot) in side.cards" :key="slot">{{cardName(id)}}</li></ol>
          <p>手牌 {{side.hand.map(cardName).join('、') || '无记录'}}</p>
          <p>仙命 {{names(side.talents,talents)}}</p>
          <p v-if="side.embryoGrowth!==undefined">澄心剑胚累计加攻 +{{side.embryoGrowth}}</p>
          <p>修道者仙命 {{names(side.seasonTalents.map(choice=>choice.id),seasonTalents)}}</p>
          <p v-if="side.plantBuffs && Object.keys(side.plantBuffs).length">灵植炼化 {{Object.entries(side.plantBuffs).map(([key,value])=>`${plantRefinements?.find(plant=>plant.key===key)?.name??key} ${value}`).join('、')}}</p>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.cached-battles form { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:14px; }
.cached-battles select { margin:0 10px; }
.cached-sides { display:grid; grid-template-columns:repeat(auto-fit,minmax(245px,1fr)); gap:16px; }
.cached-sides article { padding:14px; border:1px solid #c4bca9; border-radius:8px; }
</style>
