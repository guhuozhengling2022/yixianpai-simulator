<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue';

type Layout = { cards: number[]; hand: number[] | null };
type Candidate = {
  cards: number[];
  hand?: number[];
  wins: number;
  samples: number;
  random: boolean;
  actionsOnWin?: number;
  hpOnWin?: number;
};
type Report = {
  evaluated: number;
  skipped: number;
  unsupportedHandCards: number;
  exhaustive: boolean;
  refining: boolean;
  baseline?: Candidate;
  winners: Candidate[];
  skippedReason?: string;
};
type Job = {
  id: string;
  status: 'RUNNING' | 'CANCELLING' | 'COMPLETE' | 'CANCELLED' | 'ERROR';
  report?: Report;
  message?: string;
};

const props = defineProps<{
  input: { players: Layout[] };
  cardNames: Map<number, { name: string; rarity: number }>;
  compact?: boolean;
}>();
const emit = defineEmits<{ apply: [layout: Layout] }>();
const job = ref<Job>();
const starting = ref(false);
const cancelling = ref(false);
const error = ref('');
const captured = ref('');
const lastApplied = ref('');
let timer: number | undefined;
let polling: AbortController | undefined;
let disposed = false;

const active = (view: Job) => view.status === 'RUNNING' || view.status === 'CANCELLING';
const running = computed(() => job.value != null && active(job.value));
const report = computed(() => job.value?.report);
const winners = computed(() => report.value?.winners.slice(0, props.compact ? 1 : undefined) ?? []);
const current = computed(() => JSON.stringify(props.input));
const stale = computed(() => !!job.value && current.value !== captured.value && current.value !== lastApplied.value);
const handUnknown = computed(() => props.input.players[0]?.hand == null);
const formatNumber = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 });

function schedulePoll(delay = 250) {
  if (!disposed && running.value) timer = window.setTimeout(() => void updateJob(), delay);
}

async function startSearch() {
  if (starting.value || running.value) return;
  starting.value = true;
  error.value = '';
  job.value = undefined;
  captured.value = current.value;
  lastApplied.value = '';
  try {
    const response = await fetch('/api/deck-search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: captured.value,
    });
    const data = await response.json();
    if (response.status === 404) throw new Error('请关闭旧版后台并重启模拟器。');
    if (!response.ok) throw new Error(data.message ?? '无法开始求解。');
    if (disposed) { cancelDetached(data); return; }
    job.value = data;
    schedulePoll();
  } catch (cause) {
    if (!disposed) error.value = cause instanceof Error ? cause.message : '无法开始求解。';
  } finally {
    starting.value = false;
  }
}

async function updateJob(cancel = false) {
  if (!job.value || !active(job.value)) return;
  window.clearTimeout(timer);
  polling?.abort();
  const controller = new AbortController();
  polling = controller;
  if (cancel) cancelling.value = true;
  let nextDelay = 250;
  try {
    const response = await fetch(`/api/deck-search/${encodeURIComponent(job.value.id)}`, {
      method: cancel ? 'DELETE' : 'GET',
      signal: controller.signal,
    });
    const data = await response.json();
    if (controller.signal.aborted) return;
    if (response.status === 404) {
      job.value = { ...job.value, status: 'ERROR', message: data.message };
    } else if (!response.ok) {
      throw new Error(data.message ?? '无法更新求解进度。');
    } else {
      job.value = data;
    }
    error.value = '';
  } catch (cause) {
    if (controller.signal.aborted) return;
    error.value = cause instanceof Error ? cause.message : '无法更新求解进度。';
    nextDelay = 800;
  } finally {
    if (polling === controller) {
      polling = undefined;
      cancelling.value = false;
      schedulePoll(nextDelay);
    }
  }
}

function apply(candidate: Candidate) {
  if (stale.value || running.value) return;
  const input = JSON.parse(current.value);
  const layout: Layout = { cards: [...candidate.cards], hand: candidate.hand ? [...candidate.hand] : null };
  Object.assign(input.players[0], layout);
  lastApplied.value = JSON.stringify(input);
  emit('apply', layout);
}

function cardLabel(id: number) {
  const card = props.cardNames.get(id);
  return card ? `${card.name} · ${card.rarity + 1}级` : `卡牌 #${id}`;
}

function outcome(candidate: Candidate) {
  const score = candidate.random
    ? `${candidate.wins}/${candidate.samples} 胜 · 样本胜率 ${formatNumber.format(candidate.wins / candidate.samples * 100)}%`
    : candidate.wins ? '胜' : '负';
  if (candidate.actionsOnWin == null || candidate.hpOnWin == null) return score;
  const actions = formatNumber.format(candidate.actionsOnWin);
  const hp = formatNumber.format(candidate.hpOnWin);
  return candidate.random
    ? `${score} · 胜局平均 ${actions} 动 · 平均余血 ${hp}`
    : `${score} · ${actions} 动结束 · 余血 ${hp}`;
}

function cancelDetached(view?:Job) {
  if (view && active(view))
    void fetch(`/api/deck-search/${encodeURIComponent(view.id)}`, {method:'DELETE'}).catch(()=>{});
}

onUnmounted(() => {
  disposed = true;
  window.clearTimeout(timer);
  polling?.abort();
  cancelDetached(job.value);
});
</script>

<template>
  <section class="deck-solver" aria-label="卡组求解">
    <div class="solver-heading">
      <h2 v-if="!compact">卡组求解</h2>
      <div class="solver-actions">
        <button type="button" :disabled="starting || running" @click="startSearch">{{compact?'求解本轮败局':'找能赢的摆法'}}</button>
        <button v-if="running" type="button" :disabled="cancelling || job?.status === 'CANCELLING'"
          @click="updateJob(true)">取消求解</button>
      </div>
    </div>
    <p class="muted">固定乙方和先手，按甲方当前槽数从场上和手牌选牌。下阵牌放回手牌，空槽按普通攻击计算。</p>
    <p v-if="handUnknown" class="muted">甲方手牌未提供，本次只重排场上的牌。</p>
    <p v-if="error || job?.message" role="alert">{{ error || job?.message }}</p>
    <p v-if="starting" role="status">正在启动求解…</p>
    <template v-if="job">
      <p role="status">
        <span v-if="job.status === 'CANCELLING'">正在取消</span>
        <span v-else-if="running">{{ report?.refining ? '正在增加样本复验候选' : '正在搜索' }}</span>
        <span v-else-if="job.status === 'CANCELLED'">已取消，保留已找到的结果</span>
        <span v-else-if="job.status === 'COMPLETE'">{{ report?.exhaustive ? '已枚举所有可用摆法' : '限时搜索结束' }}</span>
        <span v-if="report"> · 已结算 {{ report.evaluated }} 种<span v-if="report.skipped"> · 跳过 {{ report.skipped }} 种</span></span>
      </p>
      <template v-if="report">
        <p v-if="report.baseline">当前摆法（求解开始时） · {{ outcome(report.baseline) }}</p>
        <p v-if="report.unsupportedHandCards" class="muted">有 {{ report.unsupportedHandCards }} 张暂不支持模拟的手牌保留在手中。</p>
        <p v-if="report.skippedReason" class="muted">跳过原因示例 · {{ report.skippedReason }}</p>
        <p v-if="stale" role="status">对局配置已变化，请重新求解后应用。</p>
        <p v-if="!running && !report.winners.length">本次未找到胜局。</p>
        <ol v-else-if="report.winners.length" class="solver-results">
          <li v-for="candidate in winners" :key="candidate.cards.join(',')">
            <div class="candidate-heading">
              <strong>{{ outcome(candidate) }}</strong>
              <button type="button" :disabled="stale || running" @click="apply(candidate)">{{compact?'导入双方并应用摆法':'应用到甲方'}}</button>
            </div>
            <ol class="candidate-cards">
              <li v-for="(id, slot) in candidate.cards" :key="slot">{{ cardLabel(id) }}</li>
            </ol>
          </li>
        </ol>
      </template>
    </template>
  </section>
</template>

<style scoped>
.deck-solver { margin-top: 16px; padding: 14px 16px; border: 1px solid #cdd9c5; border-radius: 8px; background: #f5f8f0; }
.solver-heading, .solver-actions, .candidate-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.solver-heading, .candidate-heading { justify-content: space-between; }
.solver-heading h2 { font-size: 18px; margin: 0; }
.deck-solver button { padding: 6px 12px; border: 1px solid #9cac95; border-radius: 5px; background: #fbfdf7; color: #334d39; cursor: pointer; }
.deck-solver button:disabled { opacity: .55; cursor: default; }
.deck-solver p { margin: 7px 0; font-size: 13px; }
.deck-solver [role="alert"] { color: #8f3d30; }
.solver-results { margin: 10px 0 0; padding-left: 20px; }
.solver-results > li { margin: 8px 0; padding: 8px; border: 1px solid #d8e1d0; border-radius: 5px; }
.candidate-cards { display: flex; flex-wrap: wrap; gap: 5px 24px; margin: 8px 0 0; padding-left: 20px; font-size: 12px; }
</style>
