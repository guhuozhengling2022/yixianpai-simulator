<script setup lang="ts">
import { computed, ref } from 'vue';

export type SeasonChoice = { id: number; slot?: number; disabled?: boolean };
export type SeasonTalent = {
  id: number;
  name: string;
  description: string;
  effectRound: number;
  battle: boolean;
  supported: boolean;
  slotRequired: boolean;
  toggleable: boolean;
  available?: boolean;
  requiredCharacter?: number;
};

const props = defineProps<{
  modelValue?: SeasonChoice[];
  talents: SeasonTalent[];
  deckSize: number;
  characterId: number;
}>();
const emit = defineEmits<{ 'update:modelValue': [choices: SeasonChoice[]] }>();
const search = ref('');
const choices = computed(() => props.modelValue ?? []);
const catalog = computed(() => new Map(props.talents.map(talent => [talent.id, talent])));
const matches = computed(() => {
  const query = search.value.trim();
  return props.talents.filter(talent =>
    talent.available !== false && (!talent.requiredCharacter || talent.requiredCharacter === props.characterId) &&
    (!query || talent.name.includes(query) || String(talent.id) === query) &&
    !choices.value.some(choice => choice.id === talent.id),
  ).sort((a, b) => Number(b.supported) - Number(a.supported) || Number(a.battle) - Number(b.battle));
});

function label(talent: SeasonTalent) {
  if (!talent.battle) return '局外仙命';
  return talent.supported ? '支持战斗模拟' : '暂未支持战斗模拟';
}

function add(event: Event) {
  const select = event.target as HTMLSelectElement;
  const talent = catalog.value.get(Number(select.value));
  if (!talent || choices.value.length >= 3 || (talent.battle && !talent.supported)) return;
  emit('update:modelValue', [...choices.value, { id: talent.id }]);
  select.value = '';
  search.value = '';
}

function update(index: number, patch: Partial<SeasonChoice>) {
  emit('update:modelValue', choices.value.map((choice, i) => i === index ? { ...choice, ...patch } : choice));
}

function changeSlot(index: number, event: Event) {
  update(index, { slot: Number((event.target as HTMLSelectElement).value) || undefined });
}

function changeDisabled(index: number, event: Event) {
  update(index, { disabled: (event.target as HTMLInputElement).checked });
}
</script>

<template>
  <details class="season-fates" :open="choices.length > 0 || undefined">
    <summary>修道者仙命 · {{ choices.length }}/3</summary>
    <div v-for="(choice, index) in choices" :key="choice.id" class="season-choice">
      <strong>{{ catalog.get(choice.id)?.name ?? `未知仙命 #${choice.id}` }}</strong>
      <button type="button" @click="emit('update:modelValue', choices.filter((_, i) => i !== index))">移除</button>
      <template v-if="catalog.has(choice.id)">
        <small>{{ label(catalog.get(choice.id)!) }}</small>
        <p>{{ catalog.get(choice.id)?.description }}</p>
        <label v-if="catalog.get(choice.id)?.slotRequired">
          指定卡槽
          <select :value="choice.slot ?? ''" @change="changeSlot(index, $event)">
            <option value="">请选择</option>
            <option v-for="slot in deckSize" :key="slot" :value="slot">第 {{ slot }} 槽</option>
          </select>
        </label>
        <label v-if="catalog.get(choice.id)?.toggleable">
          <input type="checkbox" :checked="choice.disabled" @change="changeDisabled(index, $event)">
          已在游戏中禁用
        </label>
      </template>
    </div>
    <template v-if="choices.length < 3">
      <input v-model="search" aria-label="搜索修道者仙命" placeholder="搜索修道者仙命">
      <select aria-label="添加修道者仙命" @change="add">
        <option value="">添加修道者仙命</option>
        <option v-for="talent in matches" :key="talent.id" :value="talent.id" :disabled="talent.battle && !talent.supported">
          {{ talent.name }} · {{ label(talent) }}
        </option>
      </select>
    </template>
  </details>
</template>

<style scoped>
.season-fates { margin: 12px 0; padding: 10px; border: 1px solid #c4bca9; border-radius: 8px; }
.season-fates summary { cursor: pointer; font-weight: 600; }
.season-fates > input, .season-fates > select { margin-top: 8px; width: 100%; }
.season-choice { margin-top: 8px; padding: 8px; background: #fffbf2; border-radius: 6px; }
.season-choice button { float: right; }
.season-choice small, .season-choice label { display: block; }
.season-choice p { white-space: pre-line; margin: 6px 0; font-size: .9em; }
</style>
