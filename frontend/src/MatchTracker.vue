<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';

export type TrackedCard = { name?: string; grade?: number; realm?: number; cardId?: number };
export type TrackedPlayer = {
  username?: string; character?: string; sect?: string; realm?: number;
  destiny?: number; destinyDelta?: number; baseMaxHp?: number;
  experienceAtRefresh?: number; physiqueAtRefresh?: number; physiqueCapAtRefresh?: number;
  opponent?: string; cards: TrackedCard[];
};
type Round = { number: number; players: TrackedPlayer[] };
type Snapshot = { status: string; matchId?: string; updatedAt?: string; rounds: Round[] };

const props = defineProps<{ active: boolean; importError?: string }>();
const emit = defineEmits<{ importPair: [own: TrackedPlayer, enemy: TrackedPlayer, round: number] }>();
const snapshot = ref<Snapshot>();
const roundChoice = ref('latest');
const playerChoice = ref('');
const self = ref('');
const openedGame = ref(false);
const error = ref('');
const realms = ['', '炼气', '筑基', '金丹', '元婴', '化神', '返虚'];
let pending: AbortController | undefined;

function signature(player: TrackedPlayer) {
  return JSON.stringify([player.username, player.character, player.sect]);
}

const history = computed(() => {
  const rounds = snapshot.value?.rounds ?? [];
  const ambiguous = new Set<string>();
  for (const round of rounds) {
    const seen = new Set<string>();
    for (const player of round.players) {
      const key = signature(player);
      if (seen.has(key)) ambiguous.add(key);
      seen.add(key);
    }
  }
  return rounds.map(round => ({
    number: round.number,
    players: round.players.map((player, index) => {
      const key = signature(player);
      const separate = ambiguous.has(key) || !player.username;
      const label = [player.username || '未知玩家', player.character].filter(Boolean).join(' · ');
      return {
        ...player,
        key: separate ? JSON.stringify([key, round.number, index]) : key,
        label: separate ? `${label}（第${round.number}轮第${index + 1}位）` : label,
      };
    }),
  }));
});

const roster = computed(() => {
  const players = new Map<string, string>();
  for (const round of history.value) {
    for (const player of round.players) players.set(player.key, player.label);
  }
  return [...players.entries()];
});
const latest = computed(() => history.value.at(-1)?.number ?? 0);
function ownInRound(number: number) {
  return history.value.find(round => round.number === number)?.players.find(player => player.key === self.value);
}

function importOpponent(number: number, enemy: TrackedPlayer & { key: string }) {
  const own = ownInRound(number);
  if (own && own.key !== enemy.key) emit('importPair', own, enemy, number);
}
const visibleRounds = computed(() => {
  const rounds = history.value.filter(round =>
    roundChoice.value === 'all' ||
    round.number === (roundChoice.value === 'latest' ? latest.value : Number(roundChoice.value)));
  return rounds.map(round => ({
    ...round,
    players: round.players.filter(player => !playerChoice.value || player.key === playerChoice.value),
  }));
});
const status = computed(() => {
  switch (snapshot.value?.status) {
    case 'READY': return `已记录 ${history.value.length} 轮 · 最新第 ${latest.value} 轮`;
    case 'FIRST_ROUND': return '等待首轮结算后写入卡组';
    case 'UPDATING': return '游戏正在更新对局日志';
    case 'UNREADABLE': return '暂时无法读取游戏日志';
    case 'MISSING': return '游戏还没有创建 BattleLog.json。请进入对局并完成一轮结算。';
    case 'EMPTY': return '游戏日志 BattleLog.json 目前为空。请完成一轮结算，等游戏写入记录。';
    case 'UNRECOGNIZED': return '游戏日志的格式无法识别，暂时无法读取新的轮次。';
    case 'WAITING': return '等待游戏写入对局日志';
    default: return '正在读取本地对局日志';
  }
});

async function refresh() {
  if (pending) return;
  const request = new AbortController();
  pending = request;
  try {
    const response = await fetch('/api/match-tracker', { signal: request.signal });
    if (!response.ok) throw new Error('无法读取对局记录，请确认已启动新版模拟器。');
    const next: Snapshot = await response.json();
    if (next.matchId !== snapshot.value?.matchId) {
      roundChoice.value = 'latest';
      playerChoice.value = '';
      self.value = '';
    }
    snapshot.value = next;
    error.value = '';
  } catch {
    if (!request.signal.aborted) error.value = '无法读取对局记录，请确认已启动新版模拟器。';
  } finally {
    if (pending === request) pending = undefined;
  }
}

watch(() => props.active, (active, _, cleanup) => {
  if (!active) return;
  void refresh();
  const timer = window.setInterval(refresh, 2000);
  window.addEventListener('focus', refresh);
  cleanup(() => {
    window.clearInterval(timer);
    window.removeEventListener('focus', refresh);
    pending?.abort();
    pending = undefined;
  });
}, { immediate: true });
onUnmounted(() => pending?.abort());
</script>

<template>
  <section class="tracker" aria-label="对局追踪">
    <div class="tracker-heading">
      <h2>对局追踪</h2>
      <a class="primary" href="steam://rungameid/1948800" @click="openedGame = true">打开游戏并追踪</a>
    </div>
    <p>在游戏里选择模式并开始匹配。每轮结算后，游戏写出的卡组会自动出现在这里。</p>
    <p v-if="openedGame" class="muted">如果浏览器询问是否打开 Steam，请允许打开。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else role="status">{{ status }}</p>
    <p v-if="importError" role="alert">{{ importError }}</p>
    <template v-if="history.length">
      <p class="muted">
        最近一份本地对局日志 · {{ snapshot?.updatedAt ? new Date(snapshot.updatedAt).toLocaleString() : '时间未知' }}
      </p>
      <p v-if="history.length < latest" class="muted">部分早期轮次没有记录，列表只显示已读取的轮次。</p>
      <div class="tracker-filters">
        <label>玩家
          <select v-model="playerChoice">
            <option value="">全部玩家</option>
            <option v-for="[key, name] in roster" :key="key" :value="key">{{ name }}</option>
          </select>
        </label>
        <label>轮次
          <select v-model="roundChoice">
            <option value="latest">最新轮次</option>
            <option value="all">全部已记录轮次</option>
            <option v-for="round in history" :key="round.number" :value="String(round.number)">第 {{ round.number }} 轮</option>
          </select>
        </label>
        <label>标记我方
          <select v-model="self">
            <option value="">未标记</option>
            <option v-for="[key, name] in roster" :key="key" :value="key">{{ name }}</option>
          </select>
        </label>
      </div>
      <p v-if="!self" class="muted">先在「标记我方」选择自己，再从对手记录导入同一轮的双方配置。</p>
      <section v-for="round in visibleRounds" :key="round.number" class="tracker-round">
        <h3>第 {{ round.number }} 轮</h3>
        <p v-if="!round.players.length" class="muted">这名玩家在该轮没有记录。</p>
        <article v-for="player in round.players" :key="player.key" class="tracker-player">
          <h4>{{ player.label }} <span v-if="player.key === self">我方</span></h4>
          <p>
            {{ [player.sect, realms[player.realm ?? 0], player.destiny == null ? '' : `命元 ${player.destiny}`,
              player.baseMaxHp == null ? '' : `基础生命 ${player.baseMaxHp}（未计体魄）`].filter(Boolean).join(' · ') }}
          </p>
          <p v-if="player.opponent" class="muted">当轮对手 {{ player.opponent }}</p>
          <p v-if="player.experienceAtRefresh != null || (player.physiqueAtRefresh ?? 0) > 0" class="muted">
            刷新时
            <span v-if="player.experienceAtRefresh != null">修为 {{ player.experienceAtRefresh }}</span>
            <span v-if="(player.physiqueAtRefresh ?? 0) > 0"> · 体魄 {{ player.physiqueAtRefresh }} / {{ player.physiqueCapAtRefresh ?? '未知' }}</span>
          </p>
          <ol v-if="player.cards.length" class="tracker-cards">
            <li v-for="(card, index) in player.cards" :key="index">
              <span>{{ index + 1 }}.</span> {{ card.name || '未知卡牌' }} <small v-if="card.grade">{{ card.grade }}级</small>
            </li>
          </ol>
          <p v-else class="muted">该轮卡组未记录。</p>
          <button v-if="self && player.key !== self" type="button" class="tracker-import"
            :disabled="!ownInRound(round.number)" @click="importOpponent(round.number, player)">
            {{ ownInRound(round.number) ? '导入双方到模拟器' : '该轮缺少我方记录' }}
          </button>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped>
.tracker { padding: clamp(16px, 3vw, 30px); background: #faf7ee; border: 1px solid #c8bca2; border-radius: 12px; }
.tracker-heading, .tracker-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.tracker-heading { justify-content: space-between; }
.tracker-heading h2, .tracker-player h4 { margin: 0; }
.tracker-heading a { text-decoration: none; display: inline-flex; }
.tracker-filters { margin: 20px 0; }
.tracker-filters label { display: flex; gap: 8px; align-items: center; max-width: 100%; }
.tracker-filters select { min-width: 0; padding: 8px; }
.tracker-round { margin-top: 20px; }
.tracker-player { padding: 14px; margin: 10px 0; border: 1px solid #d4c9b2; border-radius: 8px; background: #fffdf7; }
.tracker-player h4 span { color: #365c50; margin-left: 8px; font-size: .85em; }
.tracker-player p { margin: 5px 0; }
.tracker-cards { display: flex; flex-wrap: wrap; gap: 8px; list-style: none; padding: 0; margin: 10px 0 0; }
.tracker-cards li { padding: 6px 9px; border-radius: 5px; background: #f1ebdc; }
.tracker-cards span, .tracker-cards small { color: #665e4d; }
.tracker-import { margin-top: 10px; }
</style>
