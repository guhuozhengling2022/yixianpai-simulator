<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { preparePlayer } from './simulationInput';

type PreviewPlayer = {
  id: string;
  name: string;
  characterId: number;
  hp: number;
  maxHp: number;
  anima: number;
  defense: number;
  swordIntent: number;
  cards: number[];
  hand?: number[] | null;
  talentIds: number[];
  buffs: Record<string, number | string>;
};
type DamageRound = { round: number; damage: number; cumulative: number };
type Preview = { openingDamage: number; rounds: DamageRound[]; selfDefeated: boolean };

const props = defineProps<{
  active: boolean;
  rulesetId: string;
  player: PreviewPlayer;
}>();
const enabled = ref(false);
const loading = ref(false);
const result = ref<Preview>();
const error = ref('');

const payload = computed(() => {
  if (!enabled.value || !props.active) return '';
  return JSON.stringify({ rulesetId: props.rulesetId, player: preparePlayer(props.player) });
});

watch(payload, (body, _, cleanup) => {
  result.value = undefined;
  error.value = '';
  loading.value = Boolean(body);
  if (!body) return;

  const controller = new AbortController();
  const timer = window.setTimeout(async () => {
    try {
      const response = await fetch('/api/damage-preview', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
        signal: controller.signal,
      });
      const data = await response.json();
      if (response.status === 404) throw new Error('请关闭旧版后台并重启模拟器。');
      if (!response.ok) throw new Error(data.message ?? '伤害速算失败。');
      if (!controller.signal.aborted) result.value = data;
    } catch (cause) {
      if (!controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '伤害速算失败。';
    } finally {
      if (!controller.signal.aborted) loading.value = false;
    }
  }, 180);
  cleanup(() => {
    window.clearTimeout(timer);
    controller.abort();
  });
});
</script>

<template>
  <section class="damage-preview" aria-label="己方伤害速算">
    <label class="preview-toggle">
      <input v-model="enabled" type="checkbox"> 开启伤害速算
    </label>
    <template v-if="enabled">
      <p>按甲方当前配置打 8 回合木桩。木桩从 100 生命开始，耗尽后恢复。随机效果使用固定种子。</p>
      <p v-if="loading" role="status">正在计算…</p>
      <p v-else-if="error" role="alert">{{ error }}</p>
      <template v-else-if="result">
        <p v-if="result.openingDamage">开场伤害 {{ result.openingDamage }}，计入累计值。</p>
        <div class="preview-scroll">
          <table aria-label="每回合伤害与累计伤害">
            <thead><tr><th scope="col">回合</th><th v-for="row in result.rounds" :key="row.round" scope="col">{{ row.round }}</th></tr></thead>
            <tbody>
              <tr><th scope="row">本回合</th><td v-for="row in result.rounds" :key="row.round">{{ row.damage }}</td></tr>
              <tr><th scope="row">累计</th><td v-for="row in result.rounds" :key="row.round">{{ row.cumulative }}</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="result.selfDefeated">己方生命耗尽，速算在第 {{ result.rounds.length }} 回合结束。</p>
      </template>
    </template>
  </section>
</template>

<style scoped>
.damage-preview { margin: 10px 0; padding: 10px 12px; border: 1px solid #d5dfcd; border-radius: 6px; background: #f5f8f1; }
.preview-toggle { display: inline-flex; align-items: center; gap: 7px; font-size: 13px; font-weight: 600; }
.damage-preview p { margin: 6px 0; font-size: 12px; }
.damage-preview p[role="alert"] { color: #8f3d30; }
.preview-scroll { overflow-x: auto; }
table { border-collapse: collapse; width: 100%; font-size: 12px; font-variant-numeric: tabular-nums; }
th, td { padding: 4px 6px; text-align: right; white-space: nowrap; border-top: 1px solid #d5dfcd; }
th:first-child { text-align: left; }
tbody tr:last-child { color: #365c50; font-weight: 700; }
</style>
