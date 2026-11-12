<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ card: {id:number; randomAttack?:number; randomDefense?:number}; values?:number[] }>();
const emit = defineEmits<{ update:[values:number[]] }>();
const base = computed(() => props.card.id - Math.floor(props.card.id / 10000) % 100 * 10000);
const hint = computed(() => {
  switch (base.value) {
    case 356: return '每次触发填 0（伤敌）或 1（伤己）。';
    case 368: return '每次判定填 0 至 99。';
    case 159: return '每次触发填 0（吸血）、1（虚弱）或 2（护体）。';
    case 6000014: return '填 0（加防）、1（回血）或 2（护体）。';
    case 4000085: return '每层负面状态填 100（内伤）、101（虚弱）或 105（外伤）。';
    case 193: return '先填随机层数，再按顺序填每层的负面状态 ID。100 内伤，101 虚弱，102 破绽，103 减攻，104 困缚，105 外伤，367 冥，393 食滞。';
    case 73: case 82: case 100: case 105: case 417: case 2000004: case 6000007: case 9000026:
      return '填负面状态 ID。100 内伤，101 虚弱，102 破绽，103 减攻，104 困缚，105 外伤，367 冥，393 食滞；可跳过时填 -1。';
    case 341: case 349: case 6000011: case 10000065:
      return '填要复制的卡牌 ID，可在图鉴查看。允许跳过的效果可填 -1。';
  }
  if (props.card.randomAttack || props.card.randomDefense) return '按卡面范围填写实际随机出的攻或防。';
  return '按卡牌效果填写实际数值。抽牌或复制效果填写卡牌 ID。';
});
function update(event:Event){
  const field=event.target as HTMLTextAreaElement;
  const items=field.value.trim().split(/[,，\s]+/).filter(Boolean);
  const values=items.map(Number);
  const invalid=items.length>2048 || items.some((item,i)=>!(/^(?:-1|\d+)$/.test(item)) || values[i]!>2147483647);
  field.setCustomValidity(invalid?'请输入 -1 或非负整数，每项用空格、逗号或换行分隔。':'');
  if(!invalid)emit('update',values);
}
</script>

<template>
  <details class="outcome-editor">
    <summary>指定结果<span v-if="values?.length"> · {{values.length}} 项</span></summary>
    <label>按触发顺序填写
      <textarea :value="values?.join(', ') ?? ''" rows="2" placeholder="用逗号、空格或换行分隔"
        @input="($event.target as HTMLTextAreaElement).setCustomValidity('')" @change="update"></textarea>
    </label>
    <small>{{hint}} 多次触发时依次取值。</small>
  </details>
</template>

<style scoped>
.outcome-editor { margin-top:6px; }
.outcome-editor summary { cursor:pointer; }
.outcome-editor textarea { display:block; width:100%; }
</style>
