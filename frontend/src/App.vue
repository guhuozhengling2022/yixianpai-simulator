<script setup lang="ts">
import BattleFeedback from './BattleFeedback.vue';
import DamagePreview from './DamagePreview.vue';
import DeckSolver from './DeckSolver.vue';
import TalentSlots from './TalentSlots.vue';
import CachedBattlePage, { type CachedRound, type CachedSide } from './CachedBattlePage.vue';
import SeasonTalentSlots, { type SeasonChoice, type SeasonTalent } from './SeasonTalentSlots.vue';
import FavoriteDecks from './FavoriteDecks.vue';
import MatchTracker, { type TrackedPlayer } from './MatchTracker.vue';
import CardAtlas from './CardAtlas.vue';
import ScreenshotImport from './ScreenshotImport.vue';
import { preparePlayer, type CardOutcome } from './simulationInput';
import CardOutcomeEditor from './CardOutcomeEditor.vue';
import { computed, onMounted, ref, watch } from 'vue';
type Card={id:number;name:string;desc:string;rarity:number;level:number;variant:number;anima:number;supported:boolean;supportReason:string;cardType:number;career:number;actionAgain:boolean;requiresBattleParams:boolean;requiresContext:boolean;contextRequirements:string[];sourceConfidence:string;preparationSupported:boolean;sect:number;obsolete:boolean;hidden:boolean;chargeQi:number;hpCost:number;randomAttack?:number;randomDefense?:number};
type Talent={isBattleTalent?:boolean;id:number;talentId:number;name:string;charId:number;sect:number;level:number;effect:string;value:number;openingOnly:boolean;description:string;supported?:boolean;supportReason?:string};
type Player={id:string;name:string;characterId:number;hp:number;maxHp:number;anima:number;defense:number;swordIntent:number;cards:number[];hand?:number[]|null;talentIds:number[];seasonTalents?:SeasonChoice[];singleUseCardLimit?:number|null;battleRound?:number;cardOutcomes?:CardOutcome[];buffs:Record<string,number>};
type State={id:string;name:string;hp:number;maxHp:number;anima:number;defense:number;swordIntent:number;nextSlot:number;cloudChain:number;ignoreDefense:number;consumedSlots:number[];runtimeCards:number[];statuses:Record<string,number>;runtimeHand?:number[];destiny?:number};
type Event={seq:number;turn:number;actorId:string;targetId?:string;cardId?:number;type:string;message:string;changes:{playerId:string;attribute:string;before:number;after:number}[];states:State[]};
type Result={status:string;winnerId:string;endReason:string;turns:number;finalStates:State[];events:Event[]};
type Request={rulesetId:string;mode:string;initializationStage:string;firstActorId:string;players:Player[];battleParams?:number[]};
function applyScreenshot(rows:any[]){for(const r of rows){const p=request.value.players[r.side];while(p.cards.length<8)p.cards.push(0);for(let i=0;i<8;i++)if(r.cards[i]!==null)p.cards[i]=r.cards[i];if(typeof r.hp==='number'&&r.hp>0){p.hp=r.hp;p.maxHp=Math.max(r.maxHp??r.hp,r.hp);}if(typeof r.destiny==='number')p.buffs.destiny=r.destiny;if(typeof r.experience==='number')p.buffs.experience=r.experience;if(r.characterId)p.characterId=r.characterId;if(r.realm)p.buffs.realm=r.realm;if(r.talentsConfirmed)p.talentIds=[...new Set<number>(r.talentIds.filter(Number.isInteger))];if(r.seasonConfirmed)p.seasonTalents=[...new Set<number>(r.seasonIds.filter(Number.isInteger))].map(id=>({id}));if(r.handConfirmed)p.hand=r.hand.trim()?r.hand.trim().split(/[,，\s]+/).filter(Boolean).map(Number):[];}reset();}

const definitions=ref<{rulesetId:string;cards:Card[];characters:{id:number;name:string;sect:number;talents:number[];ygTalents:number[]}[];talents:Talent[];talentCatalog:Talent[];seasonTalents:SeasonTalent[];cardOutcomeInputs?:boolean;plantRefinements?:{key:string;name:string;cardId:number}[];talentSupportedCount:number;supportedCount:number;coverage?:{cloud:{total:number;combat:number;covered:number};five:{total:number;combat:number;covered:number};star?:{total:number;combat:number;covered:number};forge?:{total:number;combat:number;covered:number}};statusDefinitions:Record<string,string>}>();
const makePlayer=(id:string,name:string,cards:number[]):Player=>({id,name,characterId:0,hp:60,maxHp:60,anima:0,defense:0,swordIntent:0,cards,talentIds:[],singleUseCardLimit:null,buffs:{}});
const request=ref<Request>({rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'AUTO',players:[makePlayer('A','青岚',[1000017,1000013,1000005,1000001,1000004,1000010,1000012,1000037]),makePlayer('B','赤霄',[1000005,1000012,1000031,1000013,1000004,1000001,1000037,1000010])]});
const activePage=ref("首页");
function updateBattleRound(event: globalThis.Event) {
  const value = Number((event.target as HTMLInputElement).value) || undefined;
  for (const player of request.value.players) player.battleRound = value;
  reset();
}
const solverInput = computed(() => ({
  ...request.value,
  initializationStage: 'PRE_OPENING',
  battleParams: [],
  players: request.value.players.map(player => ({
    ...preparePlayer(player),
    hand: player.hand ?? null,
  })),
}));

function applySolvedDeck(layout: { cards: number[]; hand: number[] | null }) {
  const own = request.value.players[0];
  if (!own || layout.cards.length !== own.cards.length) return;
  own.cards = layout.cards;
  own.hand = layout.hand;
  reset();
}
const trackedImportError = ref('');
const trackedImportNotice = ref('');

const cachedImportError=ref('');
function buildCachedRequest(round:CachedRound){
  const rules=definitions.value;
  if(!rules)throw new Error('请等待规则加载后重试。');
  function convert(side:CachedSide,id:'A'|'B'):Player & {hand:number[]|null}{
    if(!side.uid || side.hp<1 || side.cards.length<3 || side.cards.length>8 || side.cards.some(card=>!cardById.value.has(card))){
      throw new Error('该轮有一方缺少可用的卡组或生命记录。');
    }
    const name=(side.name || (id==='A'?'我方':'敌方')).slice(0,40);
    const player={...makePlayer(id,name,[...side.cards]),hand:side.hand.length?[...side.hand]:null};
    player.hp=side.hp;
    player.maxHp=side.maxHp??side.hp+side.physique;
    player.characterId=rules!.characters.some(c=>c.id===side.characterId)?side.characterId:0;
    player.buffs={realm:side.realm,destiny:Math.max(0,side.destiny),experience:side.experience,career:side.career,physique:side.physique,...side.plantBuffs};
    if(side.physiqueCap>0)player.buffs.physiqueCap=side.physiqueCap;
    if(side.deathBattleWill)player.buffs.deathBattleWill=side.deathBattleWill;
    if(side.embryoGrowth!==undefined)player.buffs.embryoGrowth=side.embryoGrowth;
    player.talentIds=[...new Set(side.talents.filter(id=>id>0))];
    player.seasonTalents=side.seasonTalents.map(choice=>({...choice}));
    if(round.number>0)player.battleRound=round.number;
    return player;
  }
  const players=[convert(round.own,'A'),convert(round.enemy,'B')];
  const firstActorId=round.first && round.first===round.enemy.uid?'B':round.first && round.first===round.own.uid?'A':'AUTO';
  return {rulesetId:rules.rulesetId,mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId,players};
}
function importCachedRound(round:CachedRound,layout?:{cards:number[];hand:number[]|null}){
  cachedImportError.value='';
  try{
    const input=buildCachedRequest(round);
    if(layout)Object.assign(input.players[0]!,layout);
    request.value=input;
    reset();
    trackedImportNotice.value=`已导入战绩第 ${round.number} 轮${layout?'及求解摆法':''}。`+(input.firstActorId==='AUTO'?' 战绩缺少先手记录，将自动判定。':' 已采用战绩先手，可手动调整。');
    activePage.value='首页';
  }catch(cause){
    cachedImportError.value=cause instanceof Error?cause.message:'该轮导入失败';
  }
}
function importTrackedPair(own: TrackedPlayer, enemy: TrackedPlayer, round: number) {
  trackedImportError.value = '';
  if (!definitions.value) {
    trackedImportError.value = '请等待规则加载后重试。';
    return;
  }
  const rules = definitions.value;
  function convert(record: TrackedPlayer, id: 'A' | 'B') {
    const name = (record.username || (id === 'A' ? '我方' : '对手')).slice(0, 40);
    if (record.cards.length < 3 || record.cards.length > 8) {
      throw new Error(`${name}的卡组记录不完整，无法导入。`);
    }
    const cards = record.cards.map(slot => {
      if (slot.cardId != null && cardById.value.has(slot.cardId)) return slot.cardId;
      const candidates = rules.cards.filter(card => card.name === slot.name &&
        card.level === slot.realm && card.rarity + 1 === slot.grade);
      const visible = candidates.filter(card => !card.hidden && !card.obsolete);
      if (visible.length === 1) return visible[0]!.id;
      if (slot.name === '普通攻击' && slot.grade != null) {
        const canonical = (slot.grade - 1) * 10000;
        if (candidates.some(card => card.id === canonical)) return canonical;
      }
      throw new Error(`${name}的「${slot.name ?? '未知卡牌'}」无法确定，双方配置未修改。`);
    });
    const player = makePlayer(id, name, cards);
    player.characterId = rules.characters.find(character => character.name === record.character)?.id ?? 0;
    player.hand = null;
    player.battleRound = round;
    if (record.baseMaxHp != null && record.baseMaxHp > 0) {
      player.hp = player.maxHp = record.baseMaxHp;
    }
    if (record.realm != null && record.realm >= 1 && record.realm <= 6) player.buffs.realm = record.realm;
    if (record.destiny != null && record.destiny >= 0) player.buffs.destiny = record.destiny;
    return player;
  }
  try {
    const players = [convert(own, 'A'), convert(enemy, 'B')];
    request.value = { rulesetId: rules.rulesetId, mode: 'STANDARD', initializationStage: 'PRE_OPENING',
      firstActorId: 'AUTO', players };
    reset();
    trackedImportNotice.value = `已导入第 ${round} 轮的双方配置。请核对生命与体魄、先手，并补齐手牌和仙命。`;
    activePage.value = '首页';
  } catch (cause) {
    trackedImportError.value = cause instanceof Error ? cause.message : '无法导入该轮记录。';
  }
}
function applyFavorite(side:number,cards:number[]){const p=request.value.players[side];p.cards=[...cards];p.buffs.starMask=(p.buffs.starMask??0)&((1<<cards.length)-1);reset();}
const feedbackRun=ref<any>(null);
function restoreFeedback(input:Request){if(input.rulesetId!==definitions.value?.rulesetId){error.value="反馈规则版本与当前版本不同，无法恢复";return;}if(input.players.some(p=>p.cards.some(id=>!cardById.value.has(id)))){error.value="反馈包含当前版本未知卡牌";return;}request.value=JSON.parse(JSON.stringify(input));for(const player of request.value.players)player.talentIds=player.talentIds.filter(id=>id!=null);request.value.initializationStage="PRE_OPENING";activePage.value="首页";reset();}
const result=ref<Result>();const busy=ref(false);const error=ref('');const loading=ref(true);const dirty=ref(false);
const actorFilter=ref('ALL');const detail=ref(false);const search=ref('');const selectedEvent=ref<number>();const importInput=ref<HTMLInputElement>();
const careerNames:Record<number,string>={1:'炼丹师',2:'符咒师',3:'琴师',4:'画师',5:'阵法师',6:'灵植师',7:'命理师'};
const preparationLabels:Record<string,string>={permanentMaxHp:'永久生命上限',destiny:'命元',destinyAtLeastOne:'命元（最低留1）',experience:'修为',exchangeChances:'换牌机会',maxHp:'生命上限',hp:'生命',speed:'速度',defense:'防御',normalDamage:'开局普通伤害',deathBattleWillIfSecond:'后手时获得死战之志'};
function preparationLabel(key:string){return preparationLabels[key]??definitions.value?.statusDefinitions[key]??key}
const careerPrepSource=ref(2000006);const careerHand=ref('');const careerOutcomes=ref('');const careerTargets=ref('');const careerPrepResult=ref<{phase:string;hand:number[];deck:number[];gainedCards:number[];resourceDeltas:Record<string,number>;nextBattleSelf:Record<string,number>;nextBattleOpponent:Record<string,number>}>();const careerPrepError=ref('');
async function resolveCareerPreparation(){careerPrepError.value='';careerPrepResult.value=undefined;try{const response=await fetch('/api/career-preparations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({rulesetId:definitions.value?.rulesetId,sourceCardId:careerPrepSource.value,hand:integerList(careerHand.value),deck:request.value.players[0].cards,outcomes:integerList(careerOutcomes.value),targetSlots:integerList(careerTargets.value)})});const data=await response.json();if(!response.ok)throw new Error(data.message);careerPrepResult.value=data;}catch(e){careerPrepError.value=e instanceof Error?e.message:String(e)}}
function addHand(p:Player,id:number){p.hand??=[];if(p.hand.length<200)p.hand.push(id)}
function moveToHand(player: Player, slot: number, event: MouseEvent) {
  const card = player.cards[slot];
  if (card == null || card === 0 || (player.hand?.length ?? 0) >= 200) return;
  addHand(player, card);
  player.cards[slot] = 0;
  const handEditor = (event.currentTarget as HTMLButtonElement).closest('.player-panel')
    ?.querySelector<HTMLDetailsElement>('.hand-editor');
  if (handEditor) handEditor.open = true;
}

function playFromHand(player: Player, index: number, event: globalThis.Event) {
  const select = event.currentTarget as HTMLSelectElement;
  if (select.value === '') return;
  const slot = Number(select.value);
  select.selectedIndex = 0;
  const card = player.hand?.[index];
  const onBoard = player.cards[slot];
  if (card == null || onBoard == null) return;
  player.cards[slot] = card;
  if (onBoard === 0) player.hand!.splice(index, 1);
  else player.hand![index] = onBoard;
}
const inputNotes=computed(()=>{const notes:string[]=[];for(const p of request.value.players){if(p.talentIds.includes(220)&&p.hand==null)notes.push(`${p.name}：兴云布雨必须提供手牌，确认没有手牌时请选择空手牌。`);if(p.hand==null)notes.push(`${p.name}：手牌未提供，不等于没有手牌。`);if(p.characterId!==0&&!p.talentIds.length)notes.push(`${p.name}：已选角色但未选择仙命，角色不会自动授予能力。`);for(const id of p.cards){const c=definitions.value?.cards.find(c=>c.id===id);if(c&&!c.supported)notes.push(`${p.name}：${c.name}尚未支持战斗结算。`);if(c?.sourceConfidence==='DESCRIPTION')notes.push(`${p.name}：${c.name}依据卡面描述恢复，尚待逐动校准。`);for(const key of c?.contextRequirements??[]){if(key==='hand'&&p.hand==null)notes.push(`${p.name}：${c?.name}需要明确手牌。`);else if(key==='destiny'&&p.buffs.destiny===undefined)notes.push(`${p.name}：${c?.name}需要命元快照。`);else if(key==='opponentDestiny'&&request.value.players.find(other=>other.id!==p.id)?.buffs.destiny===undefined)notes.push(`${p.name}：${c?.name}需要对方命元快照。`);else if(['realm','stance'].includes(key)&&!p.buffs[key]&&!(key==='stance'&&p.seasonTalents?.some(t=>t.id===27023)))notes.push(`${p.name}：${c?.name}需要填写${key==='realm'?'境界':'架势'}。`);}if(c?.requiresBattleParams)notes.push(`${p.name}：${c.name}有可指定的效果，可在对应卡槽填写结果。`);}}return [...new Set(notes)]});
const talentSearch=ref('');const showUnsupportedTalents=ref(false);
function embryoChoiceCompatible(p:Player,t:Talent){return t.effect!=="EMBRYO_TRAIT"||!p.talentIds.some(id=>id!==t.id&&id%10000===t.id%10000)}
function talentCompatible(p:Player,t:Talent){const c=definitions.value?.characters.find(c=>c.id===p.characterId);return (!t.charId||!p.characterId||t.charId===p.characterId)&&(!t.sect||!c||t.sect===c.sect)}
function availableTalents(p:Player){const source=showUnsupportedTalents.value?definitions.value?.talentCatalog:definitions.value?.talents;const selected=(definitions.value?.talentCatalog??[]).filter(t=>p.talentIds.includes(t.id));return [...new Map([...(source??[]),...selected].map(t=>[t.id,t])).values()].filter(t=>p.talentIds.includes(t.id)||(talentCompatible(p,t)&&(t.name.includes(talentSearch.value)||String(t.id).includes(talentSearch.value))));}
function characterChanged(p:Player){p.talentIds=p.talentIds.filter(id=>{const t=definitions.value?.talentCatalog.find(t=>t.id===id);return t&&talentCompatible(p,t)});}
const statusSearch=ref('');const showZero=ref(false);
const preparationSource=ref(7000109);const preparationOutcomes=ref('7000001');const preparationResult=ref<{phase:string;experienceDelta:number;gainedCards:number[];preferredElement:string;physiqueDelta:number;physiqueCapDelta:number;maxHpDelta:number}>();const preparationError=ref('');
const supported=computed(()=>definitions.value?.cards.filter(c=>c.supported)??[]);
function gradeLabel(c:Card){return c.name.startsWith('梦')?(['','炼气','筑基','金丹','元婴','化神','返虚'][c.level]??`境界${c.level}`):`${c.rarity+1}级`}
function statusKeys(){return Object.keys(definitions.value?.statusDefinitions??{}).sort().filter(key=>key.includes(statusSearch.value)||(definitions.value?.statusDefinitions[key]??'').includes(statusSearch.value))}
function visibleStatuses(state:State){return Object.entries(state.statuses).filter(([key,value])=>showZero.value||value!==0||key==='lastElement')}
function integerList(text:string){if(!text.trim())return [];const tokens=text.trim().split(/[\s,，]+/);if(tokens.length>2048||tokens.some(token=>!/^(-1|\d+)$/.test(token)||!Number.isSafeInteger(Number(token))||Number(token)>2147483647))throw new Error('参数需为-1或非负整数，以逗号或空格分隔，最多2048项');return tokens.map(Number)}
async function resolvePreparation(){preparationError.value='';preparationResult.value=undefined;try{const outcomes=integerList(preparationOutcomes.value);const response=await fetch('/api/preparations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({rulesetId:definitions.value?.rulesetId,sourceCardId:preparationSource.value,deck:request.value.players[0].cards,outcomes})});const data=await response.json();if(!response.ok)throw new Error(data.message);preparationResult.value=data;}catch(e){preparationError.value=e instanceof Error?e.message:String(e)}}
const cardById=computed(()=>new Map(definitions.value?.cards.map(c=>[c.id,c])??[]));
function automaticSingleUseCardLimit(player: Player) {
  return player.seasonTalents?.some(t => t.id === 2015 && !t.disabled) ? 3 : 2;
}
function setSingleUseCardLimit(player: Player, event: globalThis.Event) {
  const value = (event.target as HTMLSelectElement).value;
  player.singleUseCardLimit = value === 'auto' ? null : Number(value);
  reset();
}
function singleUseCardCount(player: Player) {
  return player.cards.filter(id => [1, 3].includes(cardById.value.get(id)?.cardType ?? 0)).length;
}

const inspection=computed(()=>definitions.value?.cards.filter(c=>c.name.includes(search.value)||String(c.id).includes(search.value)).slice(0,40)??[]);
const groups=computed(()=>{
 const map=new Map<number,Event[]>();
 for(const e of result.value?.events??[]){
  if(actorFilter.value!=='ALL'&&e.actorId!==actorFilter.value&&e.targetId!==actorFilter.value)continue;
  if(!detail.value&&['CARD_ATTEMPT','TURN_END','SWORD_CONSUMED','CLOUD_CHAIN'].includes(e.type))continue;
  if(!map.has(e.turn))map.set(e.turn,[]);map.get(e.turn)!.push(e);
 }
 return [...map.entries()];
});
const winner=computed(()=>result.value?.finalStates.find(p=>p.id===result.value?.winnerId));
const selected=computed(()=>result.value?.events.find(e=>e.seq===selectedEvent.value));
const attributes:Record<string,string>={metalActivation:'金灵激活',sharpness:'锋锐',externalInjury:'外伤',attackBonus:'加攻',guard:'护体',weakness:'虚弱',vulnerability:'破绽',internalInjury:'内伤',hp:'生命',maxHp:'生命上限',defense:'防御',anima:'灵气',swordIntent:'剑意',cloudChain:'连云计数',ignoreDefense:'无视防御'};
const fields=[{key:'hp',label:'当前生命',min:1},{key:'maxHp',label:'生命上限',min:1},{key:'anima',label:'灵气',min:0},{key:'defense',label:'防御',min:0},{key:'swordIntent',label:'剑意',min:0}] as const;
watch(request,()=>{if(result.value)dirty.value=true},{deep:true});
async function load(){loading.value=true;error.value='';try{const r=await fetch('/api/definitions');if(!r.ok)throw new Error('规则数据加载失败');definitions.value=await r.json();}catch(e){error.value=String(e)}finally{loading.value=false}}
onMounted(load);
function setCardOutcome(player:Player,slot:number,values:number[]){
  const entries=(player.cardOutcomes??[]).filter(entry=>entry.slot!==slot);
  if(values.length)entries.push({slot,cardId:player.cards[slot]!,values});
  player.cardOutcomes=entries;
}
function resizeDeck(p:Player,event:globalThis.Event){const n=Number((event.target as HTMLSelectElement).value);p.cards=p.cards.slice(0,n);while(p.cards.length<n)p.cards.push(0);p.buffs.starMask=(p.buffs.starMask??0)&((1<<n)-1);reset();}
function move(p:Player,index:number,delta:number){const next=index+delta;if(next<0||next>=p.cards.length)return;[p.cards[index],p.cards[next]]=[p.cards[next]!,p.cards[index]!];p.cardOutcomes=p.cardOutcomes?.map(entry=>({...entry,slot:entry.slot===index?next:entry.slot===next?index:entry.slot}));}
async function simulate(){request.value.initializationStage="PRE_OPENING";request.value.battleParams=[];for(const p of request.value.players)for(const [key,value] of Object.entries(p.buffs??{}))if(String(value)===''){if(['realm','destiny','career','stance','bottleFirst','bottleSecond','experience','embryoGrowth'].includes(key))delete p.buffs[key];else p.buffs[key]=0;}busy.value=true;error.value='';const payload={...request.value,players:request.value.players.map(preparePlayer)};const submitted=JSON.stringify(payload);const run={input:JSON.parse(JSON.stringify(request.value)),submittedInput:JSON.parse(submitted),startedAt:new Date().toISOString(),result:null as any,failure:""};feedbackRun.value=run;try{const r=await fetch('/api/simulations',{method:'POST',headers:{'Content-Type':'application/json'},body:submitted});const data=await r.json();if(!r.ok)throw new Error(data.message??'模拟失败');run.result=JSON.parse(JSON.stringify(data));feedbackRun.value=run;result.value=data;dirty.value=false;selectedEvent.value=undefined;actorFilter.value='ALL';}catch(e){error.value=e instanceof Error?e.message:String(e);run.failure=error.value;feedbackRun.value=run;}finally{busy.value=false}}
function download(value:unknown,name:string){const u=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download=name;a.click();URL.revokeObjectURL(u)}
async function importJson(e:globalThis.Event){const input=e.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;try{if(file.size>65536)throw new Error('输入文件不得超过64KB');const v=JSON.parse(await file.text());if(!Array.isArray(v.players)||v.players.length!==2||v.players.some((p:Player)=>!p||!Array.isArray(p.cards)||(p.cards.length<3||p.cards.length>8)))throw new Error('需要两位玩家，每人3至8个卡槽');for(const p of v.players){if(p.talentIds!=null&&!Array.isArray(p.talentIds))throw new Error('talentIds 必须为数组');if(p.buffs!=null&&(typeof p.buffs!=='object'||Array.isArray(p.buffs)))throw new Error('buffs 必须为对象');if(p.hand!=null&&(!Array.isArray(p.hand)||p.hand.length>200||p.hand.some((id:unknown)=>!Number.isInteger(id)||!definitions.value?.cards.some(c=>c.id===id))))throw new Error('手牌应为最多200个已知卡牌ID');p.talentIds??=[];p.buffs??={};}v.initializationStage="PRE_OPENING";request.value=v;result.value=undefined;error.value='';}catch(e){error.value=e instanceof Error?e.message:'导入失败'}input.value=''}
function updatePhysique(p:Player,event:globalThis.Event){const value=Number((event.target as HTMLInputElement).value);if(!Number.isInteger(value)||value<0||value>10000)return;const old=p.buffs.physique??0;p.buffs.physique=value;p.maxHp=Math.max(p.hp,Math.min(10000,p.maxHp+value-old));}
function reset(){result.value=undefined;dirty.value=false;error.value='';selectedEvent.value=undefined}
function cloudExample(){
 const a=makePlayer('A','牧逸风',[1000014,1000006,1000002,1000003,1000009,1000011,1000007,1000013]);a.characterId=1000001;a.talentIds=[14];
 const b=makePlayer('B','守势试炼',[1000005,1000012,1000031,1000003,1000001,1000005,1000013,1000007]);b.talentIds=[3,13];
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function chaseExample(){
 const a=makePlayer('A','逐风',[3000013,1000036,2000002,2000005,1000025,1000013,2000011,1000001]);a.anima=3;a.hp=45;a.maxHp=70;
 const b=makePlayer('B','丹心',[2000003,2000001,2000007,2000010,1000005,1000013,2000011,1000007]);b.hp=50;b.maxHp=70;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function fanExample(){
 const a=makePlayer('A','吴策',[20023,4000107,4000038,4000044,10016,4000029,4000038,4000001]);a.characterId=2000005;a.hp=150;a.maxHp=150;a.anima=3;a.buffs={starMask:255};
 const b=makePlayer('B','剑宗对照',[1000001,1000013,1000005,1000037,1000004,1000010,1000012,1000001]);b.hp=150;b.maxHp=150;b.anima=3;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function arrayEchoExample(){
 const a=makePlayer('A','杜伶鸳',[7020016,7020029,7020036,7000073,7000095,7000066,7000072,7000071]);a.characterId=3000002;a.hp=150;a.maxHp=150;a.anima=2;a.talentIds=[278,279,277];
 const b=makePlayer('B','吴策',[4000060,4000010,4000023,4000038,4000026,4000051,4000045,4000001]);b.characterId=2000005;b.hp=180;b.maxHp=180;b.anima=0;b.talentIds=[142];b.buffs={starMask:36};
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function soulExample(){
 const a=makePlayer('A','姜袭明',[4000001,4000009,4000023,4000010,4000026,4000038,4000051,20000]);a.characterId=2000004;a.hp=150;a.maxHp=150;a.anima=2;a.talentIds=[52,61];a.buffs={starMask:36};
 const b=makePlayer('B','吴策',[4000060,4000010,4000023,4000038,4000026,4000051,4000045,4000001]);b.characterId=2000005;b.hp=150;b.maxHp=150;b.anima=0;b.talentIds=[142];b.buffs={starMask:36};
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function revivalSealExample(){
 const a=makePlayer('A','南宫生',[7000001,7000009,7000006,7000003,7000011,7000068,7000069,7000072]);a.characterId=3000005;a.hp=12;a.maxHp=60;a.anima=3;a.talentIds=[139,140,138];
 const b=makePlayer('B','炎雪',[1000013,1000001,1000031,1000012,1000001,1000013,1000037,1000001]);b.characterId=1000002;b.hp=80;b.maxHp=80;b.anima=3;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function fireTalentExample(){
 const a=makePlayer('A','南宫生',[7000009,7000001,7000067,7000006,7000110,7000003,7000011,7000009]);a.characterId=3000005;a.hp=180;a.maxHp=180;a.anima=5;a.talentIds=[137,138,30127];
 const b=makePlayer('B','吴策',[4000021,4000009,4000023,4000010,4000026,4000038,4000051,4000045]);b.characterId=2000005;b.hp=180;b.maxHp=180;b.anima=5;b.talentIds=[106,20103];b.buffs={starMask:36};
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function talentExample(){
 const a=makePlayer('A','姜袭明',[4000001,4000009,4000023,4000010,4000026,4000038,4000051,4000045]);a.characterId=2000004;a.hp=180;a.maxHp=180;a.anima=3;a.talentIds=[30,61,62];a.buffs={starMask:36};
 const b=makePlayer('B','小布',[10000006,10000018,10000029,10000023,10000039,10000040,10000041,10000062]);b.characterId=4000001;b.hp=180;b.maxHp=180;b.anima=3;b.talentIds=[149,153,177];b.buffs={physique:20,physiqueCap:30,forceCap:6};
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function forgeExample(){
 const a=makePlayer('A','锻玄崩拳',[10000044,10000001,10000002,10000013,10000025,10000035,10000045,10000046]);a.hp=180;a.maxHp=180;a.anima=5;a.buffs={physique:20,physiqueCap:30,force:2,forceCap:6};
 const b=makePlayer('B','七星试炼',[4000008,4000023,4000009,4000010,4000038,4000045,4000051,4000026]);b.hp=180;b.maxHp=180;b.anima=5;b.buffs={starMask:36};
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function forgeGrowthExample(){
 const a=makePlayer('A','锻玄体魄',[10000006,10000027,10000037,10000048,10000047,10000051,10000042,10000052]);a.hp=150;a.maxHp=180;a.anima=5;a.buffs={physique:25,physiqueCap:30,forceCap:6};
 const b=makePlayer('B','五行试炼',[7000006,7000036,7000017,7000018,7000061,7000043,7000004,7000068]);b.hp=180;b.maxHp=180;b.anima=5;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function starExample(){
 const a=makePlayer('A','七星布局',[4000008,4000023,4000009,4000010,4000038,4000045,4000051,4000026]);a.hp=160;a.maxHp=160;a.anima=5;a.buffs={starMask:36};
 const b=makePlayer('B','五行试炼',[7000006,7000036,7000017,7000018,7000061,7000043,7000004,7000068]);b.hp=160;b.maxHp=160;b.anima=5;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function starInjuryExample(){
 const a=makePlayer('A','咒阵蛇影',[4000021,4000022,4000056,4000059,4000092,4000105,4000037,4000095]);a.hp=150;a.maxHp=170;a.anima=6;a.buffs={starMask:36};
 const b=makePlayer('B','剑宗试炼',[1000017,1000013,1000005,1000001,1000004,1000010,1000012,1000037]);b.hp=150;b.maxHp=170;b.anima=6;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function starReplayExample(){
 const a=makePlayer('A','卦象雷法',[4000001,4000004,4000002,4000004,4000026,4000004,4000003,4000052]);a.hp=100;a.maxHp=100;a.anima=5;a.buffs={starMask:36};
 const b=makePlayer('B','回放试炼',Array(8).fill(1000005));b.hp=100;b.maxHp=100;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b],battleParams:Array(128).fill(2)};reset();
}
function hasStar(p:Player,index:number){return Boolean((p.buffs.starMask??0)&(1<<index))}
function setStar(p:Player,index:number){p.buffs.starMask=(p.buffs.starMask??0)^(1<<index)}
function careerExample(){
 const a=makePlayer('A','琴阵合奏',[5000002,8000002,5000009,8000008,8000003,5000010,5000014,11000013]);a.hp=140;a.maxHp=160;a.anima=5;
 const b=makePlayer('B','符画推演',[3000003,6000008,3000014,6000013,3000009,11000014,11000012,3000015]);b.hp=140;b.maxHp=160;b.anima=5;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function advancedExample(){
 const a=makePlayer('A','剑阵狂澜',[1000026,1000065,1000041,1000033,1000059,1000066,1000022,1000035]);a.hp=120;a.maxHp=120;a.anima=3;a.swordIntent=3;
 const b=makePlayer('B','五行交锋',[7000009,7000031,7000023,7000062,7000053,7000052,7000095,7000042]);b.hp=120;b.maxHp=120;b.anima=6;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function dreamExample(){
 const a=makePlayer('A','梦云剑',[1030083,1040077,1040084,1040070,1040086,1000041,1000025,1040080]);a.hp=140;a.maxHp=140;a.anima=4;
 const b=makePlayer('B','梦五行',[7040090,7040081,7040076,7040083,7040084,7040085,7040088,7040103]);b.hp=140;b.maxHp=140;b.anima=4;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function parameterExample(){
 const a=makePlayer('A','参数回放',[1000052,1000001,1000002,1000003,1000004,1000005,1000006,1000007]);a.hp=80;a.maxHp=80;a.anima=3;
 const b=makePlayer('B','守势试炼',[1000005,1000005,1000005,1000005,1000005,1000005,1000005,1000005]);b.hp=80;b.maxHp=80;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b],battleParams:Array(32).fill(16)};reset();
}
function woodExample(){
 const a=makePlayer('A','青木',[7000006,7000036,7000017,7000018,7000061,7000043,7000004,7000068]);a.hp=70;a.maxHp=100;a.anima=3;
 const b=makePlayer('B','金锋',[7000001,7000016,7000025,7000026,7000034,7000099,7000116,7000002]);b.hp=100;b.maxHp=100;b.anima=3;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function waterExample(){
 const a=makePlayer('A','沧澜',[7000002,7000007,7000029,7000019,7000030,7000037,7000057,7000116]);a.hp=100;a.maxHp=100;a.anima=4;
 const b=makePlayer('B','金锋',[7000001,7000016,7000025,7000026,7000034,7000099,7000116,7000002]);b.hp=100;b.maxHp=100;b.anima=3;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
function metalExample(){
 const a=makePlayer('A','金锋',[7000001,7000016,7000025,7000026,7000034,7000099,7000116,7000002]);a.hp=100;a.maxHp=100;a.anima=3;
 const b=makePlayer('B','符丹试炼',[2000013,3000011,3000012,3000008,2000008,1000013,2000011,1000007]);b.hp=100;b.maxHp=100;
 request.value={rulesetId:'local-snapshot-v15',mode:'STANDARD',initializationStage:'PRE_OPENING',firstActorId:'A',players:[a,b]};reset();
}
</script>

<template>
 <div class="app-shell">
  <header><a class="brand" href="/" aria-label="斗法推演首页"><span class="seal">弈</span><span>弈仙<span class="brand-light"> / 斗法推演</span><small>YI XIAN · BATTLE LAB</small></span></a><span class="version"><i></i> 本地规则 · v0.15</span></header>
  <main>
   <FavoriteDecks :players="request.players" :card-names="cardById" :active-tab="activePage" @tab="activePage=$event" @apply="applyFavorite"/>
   <MatchTracker v-show="activePage==='对局追踪'" :active="activePage==='对局追踪'" :import-error="trackedImportError" @import-pair="importTrackedPair"/>
   <CachedBattlePage v-if="definitions" v-show="activePage==='战绩码'" :cards="cardById" :characters="definitions.characters" :talents="definitions.talentCatalog" :season-talents="definitions.seasonTalents" :plant-refinements="definitions.plantRefinements" :import-error="cachedImportError" :build-request="buildCachedRequest" @import-round="importCachedRound"/>
   <div v-show="activePage==='首页'">
   <p v-if="trackedImportNotice" role="status">{{trackedImportNotice}} <button type="button" @click="trackedImportNotice=''">关闭</button></p>
   <div v-if="error && !definitions" role="alert" class="error">{{error}} <button @click="load">重试加载</button></div>
   <div v-if="loading" class="loading">正在读取本地规则…</div>
   <template v-else-if="definitions">
    <form @submit.prevent="simulate">
     <ScreenshotImport :cards="definitions.cards" :characters="definitions.characters" :talents="definitions.talentCatalog" @apply="applyScreenshot"/><label v-if="definitions.seasonTalents?.length" class="battle-round">对局轮次 <input :value="request.players[0]?.battleRound??''" type="number" min="1" max="100" placeholder="按实战填写" @change="updateBattleRound"></label><label class="first-choice"><input v-model="request.firstActorId" type="radio" value="AUTO" name="first"> 自动判定先手</label><div class="players">
      <section v-for="(p,side) in request.players" :key="side" class="player-panel" :class="side===0?'jade':'copper'">
       <div class="player-heading"><span class="player-token">{{side===0?'甲':'乙'}}</span><div><span class="eyebrow">{{side===0?'PLAYER A':'PLAYER B'}}</span><input v-model="p.name" class="name-input" aria-label="玩家名称" required maxlength="40"></div><label class="first-choice"><input v-model="request.firstActorId" type="radio" :value="p.id" name="first"> 先手</label></div>
       <ScreenshotImport :cards="definitions.cards" :characters="definitions.characters" :talents="definitions.talentCatalog" :season-talents="definitions.seasonTalents" :side="side" @apply="applyScreenshot"/><label class="character-label">角色 <span>能力需在下方显式选择</span><select v-model.number="p.characterId" @change="characterChanged(p)"><option :value="0">自定义快照</option><option v-for="c in definitions.characters" :key="c.id" :value="c.id">{{c.name}}</option></select></label>
       <div class="attributes"><label v-for="field in fields" :key="field.key">{{field.label}}<input v-model.number="p[field.key]" type="number" :min="field.min" max="10000" required></label></div>
       <details class="advanced"><summary>战斗上下文 <span>境界、命元与架势</span></summary><div class="buff-inputs"><label>境界<select v-model.number="p.buffs.realm" aria-label="境界"><option :value="0">未提供</option><option v-for="(name,i) in ['炼气','筑基','金丹','元婴','化神','返虚']" :key="i" :value="i+1">{{name}}</option></select></label><label>修为<input v-model.number="p.buffs.experience" type="number" min="0" max="10000" placeholder="未提供" aria-label="修为"></label><label>额外速度<input v-model.number="p.buffs.speed" type="number" min="0" max="10000" placeholder="未自动计入的速度"></label><label><input type="checkbox" :checked="(p.buffs.deathBattleWill??0)>0" @change="p.buffs.deathBattleWill=($event.target as HTMLInputElement).checked?1:0">开场已有死战之志</label><label>命元<input v-model.number="p.buffs.destiny" type="number" min="0" max="10000" placeholder="未提供" aria-label="命元"></label><label>架势<select v-model.number="p.buffs.stance" aria-label="架势"><option :value="0">未提供</option><option :value="1">拳</option><option :value="2">棍</option></select></label><label>副职业<select v-model.number="p.buffs.career" aria-label="角色副职业"><option :value="0">未提供</option><option v-for="(name,id) in careerNames" :key="id" :value="Number(id)">{{name}}</option></select></label><label v-if="p.talentIds.includes(199) || p.cards.some(id=>cardById.get(id)?.name==='五行忘忧梦')">玉瓶首格<select v-model.number="p.buffs.bottleFirst" aria-label="玉瓶首牌"><option :value="0">空格</option><option v-for="c in supported" :key="c.id" :value="c.id">{{c.name}} · {{gradeLabel(c)}} #{{c.id}}</option></select></label><label v-if="p.talentIds.includes(199) && p.seasonTalents?.some(t=>t.id===23058&&!t.disabled)">玉瓶第二格<select v-model.number="p.buffs.bottleSecond" aria-label="玉瓶第二格"><option :value="0">空格</option><option v-for="c in supported" :key="c.id" :value="c.id">{{c.name}} · {{gradeLabel(c)}} #{{c.id}}</option></select></label></div><p class="muted">仅填写截图或回放中已知的信息。命元未提供时，只记录增减；比较命元或直接斩杀的牌需要双方相关快照。手牌变动可在事件快照中查看。</p></details>
       <p class="muted">生命上限填写含体魄的最终值，可直接修改；调整体魄会按差值同步上限，不补当前生命。导入快照不重复叠加体魄。修改后请重新推演。</p><div class="buff-inputs"><label v-if="p.characterId===1000005 || p.cards.includes(19)">澄心剑胚累计加攻<input v-model.number="p.buffs.embryoGrowth" type="number" min="0" max="10000" placeholder="填写铸剑累计值，含地火锻心"></label><label>体魄<input :value="p.buffs.physique??0" @change="updatePhysique(p,$event)" type="number" min="0" max="10000" placeholder="0"></label><label>体魄上限<input v-model.number="p.buffs.physiqueCap" type="number" min="0" max="10000" placeholder="按快照填写"></label><label>气势<input v-model.number="p.buffs.force" type="number" min="0" max="10000" placeholder="0"></label><label>气势上限<input v-model.number="p.buffs.forceCap" type="number" min="0" max="10000" placeholder="默认6"></label></div><div class="star-setup"><span>星位</span><label v-for="index in p.cards.length" :key="index"><input type="checkbox" :checked="hasStar(p,index-1)" @change="setStar(p,index-1)">{{index}}</label><label class="direction-toggle"><input type="checkbox" :checked="(p.buffs.reverseDirection??0)>0" @change="p.buffs.reverseDirection=(p.buffs.reverseDirection??0)>0?0:1">反向出牌</label><small>按快照填写；未选表示无星位。示例已选第3、6槽。</small></div><TalentSlots v-model="p.talentIds" :character-id="p.characterId" :characters="definitions.characters" :talents="definitions.talentCatalog"/>
       <SeasonTalentSlots v-if="definitions.seasonTalents?.length" v-model="p.seasonTalents" :talents="definitions.seasonTalents" :deck-size="p.cards.length" :character-id="p.characterId"/>
       <details v-if="definitions.plantRefinements?.length" class="hand-editor">
        <summary>灵植炼化</summary>
        <p class="muted">填写本场生效的炼化数值。归元草会在开场增加生命及上限，飞枭灵芝的速度由上方先手选项体现。梦·飞枭灵芝已炼化时填 1。</p>
        <div class="buff-inputs"><label v-for="plant in definitions.plantRefinements" :key="plant.key">{{plant.name}}<input type="number" min="0" max="10000" v-model.number="p.buffs[plant.key]" placeholder="0"></label></div>
       </details>
       <details class="hand-editor">
        <summary>手牌 · {{p.hand==null?'未提供':`${p.hand.length} 张`}}</summary>
        <button type="button" @click="p.hand=[]">确认空手牌 / 清空</button>
        <button type="button" @click="p.hand=null">标记未提供</button>
        <CardAtlas v-if="(p.hand?.length??0)<200" :cards="definitions.cards" :model-value="0"
          button-label="从图鉴添加手牌" @update:model-value="addHand(p,$event)"/>
        <ul>
         <li v-for="(id,hi) in p.hand??[]" :key="hi">
          <select :aria-label="`${p.name}手牌${hi+1}`" v-model.number="p.hand![hi]">
           <option v-for="c in definitions.cards.filter(c=>c.id===id||c.name===cardById.get(id)?.name)" :key="c.id" :value="c.id">{{c.name}} · {{gradeLabel(c)}}</option>
          </select>
          <select :aria-label="`第${hi+1}张手牌上阵到`" @change="playFromHand(p,hi,$event)">
           <option value="">上阵到…</option>
           <option v-for="(slotId,slot) in p.cards" :key="slot" :value="slot">
            第 {{slot+1}} 槽 · {{slotId===0?'空槽':cardById.get(slotId)?.name??slotId}}
           </option>
          </select>
          <button type="button" @click="p.hand!.splice(hi,1)">删除</button>
         </li>
        </ul>
       </details>
       <div class="deck-heading"><h3>出牌序列</h3><label>卡组长度<select :value="p.cards.length" @change="resizeDeck(p,$event)"><option v-for="n in [3,4,5,6,7,8]" :key="n" :value="n">{{n}}张</option></select></label><label>持续与消耗牌上限<select :value="p.singleUseCardLimit??'auto'" @change="setSingleUseCardLimit(p,$event)"><option value="auto">自动（{{automaticSingleUseCardLimit(p)}}张）</option><option v-for="n in 9" :key="n-1" :value="n-1">{{n-1}}张</option></select></label><span>持续与消耗牌 {{singleUseCardCount(p)}} / {{p.singleUseCardLimit??automaticSingleUseCardLimit(p)}} 张</span></div><p v-if="singleUseCardCount(p)>(p.singleUseCardLimit??automaticSingleUseCardLimit(p))" role="status">持续与消耗牌超出上限，请调整卡组或上限。</p>
       <DamagePreview v-if="side===0" :active="activePage==='首页'" :ruleset-id="request.rulesetId"
         :player="p"/>
       <ol class="deck"><li v-for="(_,index) in p.cards" :key="index"><span class="slot-number">{{String(index+1).padStart(2,'0')}}</span><div class="card-input"><CardAtlas :cards="definitions.cards" :model-value="p.cards[index]!" @update:model-value="p.cards[index]=$event"/><small><span v-if="cardById.get(p.cards[index])?.cardType===1||cardById.get(p.cards[index])?.cardType===3" class="mechanic-tag">{{cardById.get(p.cards[index])?.cardType===3?'持续（一次）':'消耗'}}</span><span v-if="cardById.get(p.cards[index])?.actionAgain" class="mechanic-tag">追击</span><span v-if="(cardById.get(p.cards[index])?.anima??0)&lt;0">耗灵气 {{-(cardById.get(p.cards[index])?.anima??0)}} · </span>{{cardById.get(p.cards[index])?.desc.replaceAll('\n',' · ')??'未知卡牌'}}</small><CardOutcomeEditor v-if="definitions.cardOutcomeInputs && cardById.get(p.cards[index])?.requiresBattleParams" :key="p.cards[index]" :card="cardById.get(p.cards[index])!" :values="p.cardOutcomes?.find(entry=>entry.slot===index&&entry.cardId===p.cards[index])?.values" @update="setCardOutcome(p,index,$event)"/></div><div class="reorder"><button type="button" :disabled="p.cards[index]===0 || (p.hand?.length??0)>=200" :aria-label="`第${index+1}槽移到手牌`" @click="moveToHand(p,index,$event)">移到手牌</button><button type="button" :disabled="index===0" :aria-label="`第${index+1}槽上移`" @click="move(p,index,-1)">↑</button><button type="button" :disabled="index===p.cards.length-1" :aria-label="`第${index+1}槽下移`" @click="move(p,index,1)">↓</button></div></li></ol>
      </section>
     </div>
     <DeckSolver :input="solverInput" :card-names="cardById" @apply="applySolvedDeck"/>
     <div class="run-bar"><p><span class="dot"></span> 七星随机效果逐次掷取，结果记录于斗法日志</p><button class="primary" type="submit" :disabled="busy">{{busy?'正在推演…':'开始斗法推演'}} <span>→</span></button></div>
     <div v-if="error" role="alert" class="error">{{error}}</div>
    </form>
    <BattleFeedback :run="feedbackRun" @restore="restoreFeedback"/><section class="battle-output"><div class="toolbar"><div><span class="section-index">02</span><h2>斗法记录</h2><span v-if="dirty" class="stale">配置已修改，请重新推演</span></div><div v-if="result" class="toolbar-actions"><button @click="download(result,'battle-result.json')">导出日志</button><button @click="reset">清空结果</button></div></div>
     <div v-if="!result" class="empty-state"><div class="empty-glyph">弈</div><h3>静候落牌</h3><p>设定双方状态与卡组，开始一次斗法推演。<br>每次攻击、资源变化和结算都会记录在这里。</p></div>
     <template v-else><div class="result-banner"><div><span class="eyebrow">BATTLE RESULT</span><h2>{{winner?.name}} <em>胜</em></h2><p>{{result.endReason==='DEATH'?'生命耗尽结算':'达到行动上限，按剩余生命判定'}} · {{result.turns}} 次行动 · {{result.events.length}} 条事件</p></div><div class="final-states"><div v-for="s in result.finalStates" :key="s.id"><strong>{{s.name}}</strong><span>生命 {{s.hp}} / {{s.maxHp}}</span><small>灵气 {{s.anima}} · 防御 {{s.defense}} · 剑意 {{s.swordIntent}}</small></div></div></div>
      <div class="log-controls"><div><button :class="{active:actorFilter==='ALL'}" @click="actorFilter='ALL'">全部</button><button v-for="p in result.finalStates" :key="p.id" :class="{active:actorFilter===p.id}" @click="actorFilter=p.id">{{p.name}}</button></div><label><input v-model="detail" type="checkbox"> 完整事件</label></div>
      <div class="log-layout"><div class="log-scroll"><section v-for="[turn,events] in groups" :key="turn" class="turn-group"><h3>{{turn===0?'战斗准备':`行动 ${String(turn).padStart(2,'0')}`}}</h3><button v-for="event in events" :key="event.seq" class="event" :class="{selected:selectedEvent===event.seq,attack:event.type==='ATTACK'}" @click="selectedEvent=event.seq"><span class="event-seq">{{String(event.seq).padStart(3,'0')}}</span><div><span class="event-actor">{{result.finalStates.find(p=>p.id===event.actorId)?.name}}</span><span>{{event.message}}</span><small v-if="event.changes.length">{{event.changes.map(c=>`${definitions?.statusDefinitions[c.attribute]??attributes[c.attribute]??c.attribute} ${c.before} → ${c.after}`).join(' / ')}}</small></div><span class="event-arrow">↗</span></button></section></div><aside class="snapshot"><label class="snapshot-toggle"><input v-model="showZero" type="checkbox"> 显示零值状态</label><p class="eyebrow">STATE SNAPSHOT</p><h3>{{selected?`事件 ${selected.seq} 之后`:'查看事件快照'}}</h3><p v-if="!selected" class="muted">点击左侧任意事件，查看结算后的双方状态。</p><div v-for="s in selected?.states??result.finalStates" :key="s.id" class="snapshot-player"><strong>{{s.name}}</strong><dl><div><dt>生命</dt><dd>{{s.hp}} / {{s.maxHp}}</dd></div><div><dt>灵气 / 防御</dt><dd>{{s.anima}} / {{s.defense}}</dd></div><div><dt>剑意</dt><dd>{{s.swordIntent}}</dd></div><div v-if="s.destiny!==undefined"><dt>命元</dt><dd>{{s.destiny}}</dd></div><div v-if="s.runtimeHand"><dt>手牌</dt><dd>{{s.runtimeHand.length}} 张</dd></div><div><dt>下一候选槽</dt><dd>{{s.nextSlot+1}}</dd></div><div><dt>连云计数</dt><dd>{{s.cloudChain}}</dd></div><div><dt>无视防御次数</dt><dd>{{s.ignoreDefense}}</dd></div><div><dt>已消耗槽</dt><dd>{{s.consumedSlots.length?s.consumedSlots.map(i=>i+1).join(", "):"无"}}</dd></div><div v-for="[key,value] in visibleStatuses(s)" :key="key"><dt>{{definitions.statusDefinitions[key]??attributes[key]??key}}</dt><dd>{{value}}</dd></div></dl><p v-if="s.runtimeHand?.length" class="muted">手牌：{{s.runtimeHand.map(id=>cardById.get(id)?.name??id).join("、")}}</p><ol class="runtime-deck"><li v-for="(id,index) in s.runtimeCards" :key="index" :class="{spent:s.consumedSlots.includes(index),next:index===s.nextSlot}"><span>{{index+1}}</span>{{cardById.get(id)?.name??id}}<small v-if="s.consumedSlots.includes(index)">已消耗</small></li></ol></div></aside></div>
     </template>
    </section>
   </template>
   <footer><span>弈仙斗法推演 · 本地实验版本</span><span>基于本地资源静态分析 / 非官方模拟器</span></footer>
   </div>
  </main>
 </div>
</template>





