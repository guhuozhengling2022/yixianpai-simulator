<script setup lang="ts">
import {onUnmounted,ref} from 'vue';
import type {SeasonTalent} from './SeasonTalentSlots.vue';
const props=defineProps<{cards:{id:number;name:string;rarity:number;supported:boolean}[];characters:{id:number;name:string}[];talents:{id:number;name:string;supported?:boolean;isBattleTalent?:boolean}[];seasonTalents?:SeasonTalent[];side?:number}>();
const emit=defineEmits<{apply:[value:any[]]}>();
const open=ref(false),busy=ref(false),error=ref(''),preview=ref(''),text=ref('');
const rows=ref<any[]>([]);
const connected=ref(false);
const canReadWindow=typeof navigator.mediaDevices?.getDisplayMedia==='function';
let stream:MediaStream|undefined;
let video:HTMLVideoElement|undefined;
let disposed=false;

function stopReading(){
 const previous=stream;
 stream=undefined;
 connected.value=false;
 if(video){video.pause();video.srcObject=null;video=undefined;}
 previous?.getTracks().forEach(track=>track.stop());
}

async function recognize(file:Blob){
 if(preview.value)URL.revokeObjectURL(preview.value);
 preview.value='';
 if(file.size>12*1024*1024)throw Error('截图不能超过12MB');
 preview.value=URL.createObjectURL(file);
 const response=await fetch('/api/screenshot-import?mode='+(props.side===undefined?'record':'game'),{method:'POST',body:file});
 const data=await response.json();
 if(!response.ok)throw Error(data.message??'识别失败');
 text.value=data.lines.map((line:any)=>line.text).join('\n');
 rows.value=data.rows.map((row:any,index:number)=>({
  ...row,side:props.side??index,hand:row.hand.join(', '),handConfirmed:false,talentsConfirmed:true,seasonConfirmed:false,seasonIds:Array.from({length:3},(_,slot)=>row.seasonTalents?.find((item:{slot:number})=>item.slot===slot)?.id??null),
  maxHp:row.hp,talentIds:row.icons.map((icon:any)=>icon.id??null),characterId:row.characterId??0
 }));
}

async function loadImage(getImage:()=>Promise<Blob>, fromWindow=false){
 if(busy.value)return;
 busy.value=true;
 rows.value=[];
 text.value='';
 error.value='';
 if(!fromWindow)open.value=true;
 try{
  const image=await getImage();
  if(disposed)return;
  open.value=true;
  await recognize(image);
 }catch(cause){
  if(disposed)return;
  open.value=true;
  error.value=cause instanceof Error?cause.message:String(cause);
 }finally{busy.value=false;}
}

async function choose(event:Event){
 const input=event.target as HTMLInputElement;
 const file=input.files?.[0];
 input.value='';
 if(file)await loadImage(async()=>file);
}

async function gameWindowImage(){
 if(!stream){
  let selected:MediaStream;
  try{selected=await navigator.mediaDevices.getDisplayMedia({video:{displaySurface:'window'},audio:false});}
  catch(cause){
   if(cause instanceof DOMException&&cause.name==='NotAllowedError')throw Error('未选择游戏窗口。请重试，并在「窗口」里选择《弈仙牌》。');
   throw cause;
  }
  const track=selected.getVideoTracks()[0];
  if(disposed||!track||track.getSettings().displaySurface!=='window'){
   selected.getTracks().forEach(item=>item.stop());
   throw Error('请在共享选择器的「窗口」中选择《弈仙牌》。');
  }
  stream=selected;
  track.addEventListener('ended',()=>{if(stream===selected)stopReading();},{once:true});
  video=document.createElement('video');
  video.muted=true;
  video.playsInline=true;
  video.srcObject=selected;
  try{
   const playing=video.play();
   await new Promise<void>((resolve,reject)=>{
    const timeout=window.setTimeout(()=>reject(Error('游戏窗口暂时没有画面，请恢复游戏窗口后重试。')),5000);
    playing.then(()=>{window.clearTimeout(timeout);resolve();},cause=>{window.clearTimeout(timeout);reject(cause);});
   });
  }catch(cause){stopReading();throw cause;}
  connected.value=true;
 }
 if(!video||video.readyState<2||!video.videoWidth||!video.videoHeight||stream?.getVideoTracks()[0]?.muted){
  throw Error('游戏窗口暂时没有画面，请恢复游戏窗口后重试。');
 }
 const canvas=document.createElement('canvas');
 const scale=Math.min(1,2560/video.videoWidth,1440/video.videoHeight);
 canvas.width=Math.round(video.videoWidth*scale);
 canvas.height=Math.round(video.videoHeight*scale);
 const context=canvas.getContext('2d');
 if(!context)throw Error('无法读取游戏画面。');
 context.drawImage(video,0,0,canvas.width,canvas.height);
 return new Promise<Blob>((resolve,reject)=>{
  canvas.toBlob(blob=>blob?resolve(blob):reject(Error('无法读取游戏画面。')),'image/jpeg',0.95);
 });
}

function readGameWindow(){
 void loadImage(gameWindowImage,true);
}

onUnmounted(()=>{
 disposed=true;
 stopReading();
 if(preview.value)URL.revokeObjectURL(preview.value);
});
function apply(){for(const r of rows.value){if(r.handConfirmed&&r.hand.trim().split(/[,，\s]+/).filter(Boolean).length>200){error.value="手牌最多支持200张";return;}for(const key of ["hp","maxHp","destiny","experience"]){const v=r[key];if(v!==null&&v!==undefined&&v!==""&&(!Number.isInteger(v)||v<0||v>10000)){error.value="生命、命元和修为请填写0至10000的整数，未知时留空";return;}}if(typeof r.hp==="number"&&typeof r.maxHp==="number"&&r.maxHp<r.hp){error.value="生命上限不能低于当前生命";return;}if(r.handConfirmed&&r.hand.trim()&&r.hand.trim().split(/[,，\s]+/).filter(Boolean).some((v:string)=>!props.cards.some(c=>c.id===Number(v)))){error.value='手牌请填写有效卡牌ID，以空格或逗号分隔';return;}}emit('apply',rows.value);open.value=false;}
</script>
<template><label class="screenshot-button">{{side===undefined?'导入战绩截图':'导入游戏截图'}}<input type="file" accept="image/png,image/jpeg" style="display:none" @change="choose"></label><template v-if="side!==undefined && canReadWindow"><button type="button" class="window-button" :disabled="busy" @click="readGameWindow">读取游戏窗口</button><button v-if="connected" type="button" class="window-button" @click="stopReading">停止共享</button></template><div v-if="open" class="import-overlay"><section class="import-dialog"><button type="button" @click="open=false" style="float:right">关闭</button><h2>截图识别与校正</h2><p>所有识别结果均为待核对草稿。卡牌同时参考文字和本地皮肤模板，仙命按炼气到化神排列；无法确定时保留为空，不移动卡槽。识别不会自动改变先手。</p><p v-if="busy">正在进行本地中文文字识别…</p><p v-if="error" role="alert">{{error}}</p><img v-if="preview" :src="preview" alt="待导入截图" style="width:100%;max-height:360px;object-fit:contain"><section v-for="r in rows" :key="r.side"><h3>{{r.side===0?'甲方':'乙方'}}</h3><p v-for="w in r.warnings" :key="w" class="warning">{{w}}</p><label>角色<select v-model.number="r.characterId"><option :value="0">未确定 / 保留原角色</option><option v-for="c in characters" :key="c.id" :value="c.id">{{c.name}}</option></select></label><div v-for="(icon,index) in r.icons" :key="index"><label>{{['炼气','筑基','金丹','元婴','化神'][Number(index)]}}仙命<select v-model="r.talentIds[index]"><option :value="null">未确定 / 不导入此项</option><option v-for="t in talents" :key="t.id" :value="t.id">{{t.name}} #{{t.id}}{{t.supported?'':t.isBattleTalent===false?'（局外仙命）':'（尚未支持模拟）'}}</option></select></label><small>图标候选：{{icon.candidates.map((c:any)=>`${c.name} ${Math.round(c.confidence*100)}%`).join(' / ')}}</small></div><label><input type="checkbox" v-model="r.talentsConfirmed">导入以上仙命（默认开启；取消勾选则保留原仙命）</label><fieldset v-if="side!==undefined && seasonTalents?.length"><legend>修道者仙命</legend>
<label v-for="(_,slot) in r.seasonIds" :key="slot">第 {{Number(slot)+1}} 个
<select v-model="r.seasonIds[slot]"><option :value="null">留空</option><option v-for="t in seasonTalents" :key="t.id" :value="t.id">{{t.name}}{{t.battle&&!t.supported?'（暂未支持战斗模拟）':''}}</option></select>
<small v-if="r.seasonTalents?.find((item:any)=>item.slot===slot)">识别文字 {{r.seasonTalents.find((item:any)=>item.slot===slot).text}}</small>
</label>
<label><input type="checkbox" v-model="r.seasonConfirmed">已核对，替换该方修道者仙命</label>
<p>需要指定卡槽或禁用状态的仙命，导入后在首页补填。</p>
</fieldset><label>境界<select v-model.number="r.realm"><option :value="null">未确定</option><option v-for="(n,i) in ['炼气','筑基','金丹','元婴','化神','返虚']" :key="i" :value="i+1">{{n}}</option></select></label><label>生命<input v-model.number="r.hp" type="number" min="1"></label><label>生命上限（请核对）<input v-model.number="r.maxHp" type="number" min="1"></label><label>命元<input v-model.number="r.destiny" type="number"></label><label>修为<input v-model.number="r.experience" type="number" min="0"></label><div v-for="(_,slot) in r.cards" :key="slot"><label>第{{slot+1}}槽<select v-model="r.cards[slot]"><option :value="null">未识别 / 保留原卡</option><option v-for="c in cards" :key="c.id" :value="c.id">{{c.name}} {{c.rarity+1}}级 #{{c.id}}{{c.supported?'':'（模拟未支持）'}}</option></select></label></div><label v-if="side!==undefined && r.handConfirmed">手牌ID（重复卡保留重复ID，可手动补齐）<textarea v-model="r.hand" placeholder="按原图顺序填写，重复卡牌可重复填写ID"></textarea></label><p v-if="side!==undefined">已识别手牌：{{r.hand.trim().split(/[,，\s]+/).filter(Boolean).map((id:string)=>cards.find(c=>c.id===Number(id))?.name??id).join("、") || "无可靠候选，请手动补齐"}}</p><label v-if="side!==undefined">手牌导入方式<select v-model="r.handConfirmed"><option :value="false">不导入，保留原手牌</option><option :value="true">导入并替换原手牌（空列表会清空原手牌）</option></select></label><details><summary>卡名识别候选与相似度（非准确率）</summary><p v-for="(d,i) in r.details" :key="i">{{d.kind==='deck'?'卡槽':'手牌'}} {{d.slot+1}}：{{d.text}} · {{Math.round(d.confidence*100)}}% · {{d.candidates.map((id:number)=>cards.find(c=>c.id===id)?.name??id).join(' / ')}}</p></details></section><details><summary>原始OCR文字</summary><pre style="white-space:pre-wrap">{{text}}</pre></details><button v-if="rows.length" type="button" @click="apply">确认导入已核对的信息</button></section></div></template>
<style scoped>.window-button{margin:0 0 6px 8px;padding:8px 12px;border:1px solid #a58f60;border-radius:6px}.screenshot-button{display:inline-block;cursor:pointer;padding:8px 12px;border:1px solid #a58f60;border-radius:6px}.import-overlay{position:fixed;inset:0;background:#0009;z-index:1000;display:grid;place-items:center}.import-dialog{background:#faf6ed;color:#272822;width:min(950px,95vw);max-height:92vh;overflow:auto;padding:24px;border-radius:12px}.import-dialog label{display:block;margin:8px 0}.import-dialog select,.import-dialog input,.import-dialog textarea{max-width:100%;padding:5px}.import-dialog select{width:75%}</style>




