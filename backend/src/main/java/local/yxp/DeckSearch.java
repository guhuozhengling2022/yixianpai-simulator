package local.yxp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static local.yxp.Models.Player;
import static local.yxp.Models.Request;

/** Tries legal arrangements against the same opponent using the normal battle resolver. */
final class DeckSearch {
    private static final int EXACT_LIMIT = 384;
    private static final int CANDIDATE_LIMIT = 2500;
    private static final int SHORTLIST_SIZE = 8;
    private static final int RESULT_LIMIT = 5;
    private static final int SCREEN_SAMPLES = 6;
    private static final int FINAL_SAMPLES = 24;
    private static final long SCREEN_TIME = Duration.ofSeconds(4).toNanos();
    private static final long TOTAL_TIME = Duration.ofSeconds(5).toNanos();
    private static final long PROGRESS_INTERVAL = Duration.ofMillis(150).toNanos();
    private static final long[] SEEDS = new SplittableRandom(19037).longs(FINAL_SAMPLES).toArray();

    record Candidate(List<Integer> cards, List<Integer> hand, int wins, int samples, boolean random,
                     Double actionsOnWin, Double hpOnWin, double averageHpMargin) {
        double winRate() { return (double) wins / samples; }
    }

    record Report(int evaluated, int skipped, int unsupportedHandCards, boolean exhaustive, boolean refining,
                  Candidate baseline, List<Candidate> winners, String skippedReason) {}

    private static final Comparator<Candidate> BEST_FIRST = Comparator
            .comparingDouble(Candidate::winRate).reversed()
            .thenComparing(candidate -> candidate.wins() > 0 && candidate.random())
            .thenComparingDouble(candidate -> candidate.actionsOnWin() == null
                    ? Double.POSITIVE_INFINITY : candidate.actionsOnWin())
            .thenComparingDouble(candidate -> candidate.hpOnWin() == null
                    ? Double.POSITIVE_INFINITY : -candidate.hpOnWin())
            .thenComparingDouble(candidate -> -candidate.averageHpMargin());

    private final Rules rules;
    private final Engine engine;

    DeckSearch(Rules rules, Engine engine) {
        this.rules = rules;
        this.engine = engine;
    }

    void validate(Request request) {
        engine.validate(request);
        if (!"PRE_OPENING".equals(request.initializationStage())) {
            throw new Engine.Rejected("INVALID_INPUT", "卡组求解需要开场前配置。");
        }
        if (request.battleParams() != null && !request.battleParams().isEmpty()) {
            throw new Engine.Rejected("INVALID_INPUT", "卡组求解不能复用固定的战斗参数带。");
        }
    }

    Report search(Request request, BooleanSupplier cancelled, Consumer<Report> onProgress) {
        return new Search(request, cancelled, onProgress).run();
    }

    private final class Search {
        private final Request request;
        private final Player own;
        private final List<Integer> original;
        private final BooleanSupplier cancelled;
        private final Consumer<Report> onProgress;
        private final Map<Integer, Integer> inventory;
        private final int[] ids;
        private final int[] counts;
        private final int unsupportedHandCards;
        private final Set<List<Integer>> seen = new HashSet<>();
        private final ArrayList<Candidate> best = new ArrayList<>();
        private final SplittableRandom layoutRandom = new SplittableRandom(13649);
        private final long started = System.nanoTime();
        private long lastPublished = started;
        private Candidate baseline;
        private boolean exhaustive;
        private boolean refining;
        private int evaluated;
        private int skipped;
        private String skippedReason;

        Search(Request request, BooleanSupplier cancelled, Consumer<Report> onProgress) {
            this.request = request;
            this.own = request.players().getFirst();
            this.original = List.copyOf(own.cards());
            this.cancelled = cancelled;
            this.onProgress = onProgress;
            this.inventory = new TreeMap<>(countCards(original));
            int excluded = 0;
            if (own.hand() != null) {
                for (int cardId : own.hand()) {
                    if (cardId == 0) continue;
                    if (rules.byId.get(cardId).supported()) inventory.merge(cardId, 1, Integer::sum);
                    else excluded++;
                }
                // An empty field slot plays the normal attack, without adding a card to the hand.
                inventory.put(0, original.size());
            }
            unsupportedHandCards = excluded;
            ids = inventory.keySet().stream().mapToInt(Integer::intValue).toArray();
            counts = inventory.values().stream().mapToInt(Integer::intValue).toArray();
        }

        Report run() {
            onProgress.accept(report());
            try {
                visit(original);
                if (countArrangements(counts.clone(), original.size(), EXACT_LIMIT + 1) <= EXACT_LIMIT) {
                    enumerate(new int[original.size()], 0, counts.clone());
                    exhaustive = true;
                } else {
                    explore();
                }
            } catch (CancellationException stop) {
                // Keep all fully evaluated candidates when the search window ends.
            }
            refine();
            return report();
        }

        private boolean screenStopped() {
            return cancelled.getAsBoolean() || System.nanoTime() - started >= SCREEN_TIME;
        }

        private boolean totalStopped() {
            return cancelled.getAsBoolean() || System.nanoTime() - started >= TOTAL_TIME;
        }

        private boolean visit(List<Integer> cards) {
            if (screenStopped() || seen.size() >= CANDIDATE_LIMIT) throw new CancellationException();
            if (!seen.add(cards)) return false;
            try {
                var candidate = evaluate(cards, SCREEN_SAMPLES, this::screenStopped);
                if (cards.equals(original)) baseline = candidate;
                evaluated++;
                best.add(candidate);
                best.sort(BEST_FIRST);
                if (best.size() > SHORTLIST_SIZE) best.removeLast();
            } catch (Engine.Rejected rejected) {
                skipped++;
                rememberFailure(rejected);
            }
            long now = System.nanoTime();
            if (now - lastPublished >= PROGRESS_INTERVAL) {
                onProgress.accept(report());
                lastPublished = now;
            }
            return true;
        }

        private void enumerate(int[] layout, int slot, int[] remaining) {
            if (slot == layout.length) {
                visit(asDeck(layout));
                return;
            }
            for (int i = 0; i < ids.length; i++) {
                if (remaining[i] == 0) continue;
                layout[slot] = ids[i];
                remaining[i]--;
                enumerate(layout, slot + 1, remaining);
                remaining[i]++;
            }
        }

        private void explore() {
            var expanded = new HashSet<List<Integer>>();
            int stalled = 0;
            while (stalled < 8) {
                int before = seen.size();
                var parent = best.stream().map(Candidate::cards).filter(cards -> !expanded.contains(cards)).findFirst();
                if (parent.isPresent()) {
                    var cards = parent.get();
                    expanded.add(cards);
                    neighbors(cards);
                }
                for (int i = 0; i < 6; i++) visit(randomDeck());
                stalled = before == seen.size() ? stalled + 1 : 0;
            }
        }

        private void neighbors(List<Integer> cards) {
            for (int a = 0; a < cards.size(); a++) {
                for (int b = a + 1; b < cards.size(); b++) {
                    if (cards.get(a).equals(cards.get(b))) continue;
                    var swap = new ArrayList<>(cards);
                    swap.set(a, cards.get(b));
                    swap.set(b, cards.get(a));
                    visit(List.copyOf(swap));
                }
            }
            var onBoard = countCards(cards);
            int start = layoutRandom.nextInt(ids.length);
            int substitutions = 0;
            for (int offset = 0; offset < ids.length; offset++) {
                int id = ids[(start + offset) % ids.length];
                if (onBoard.getOrDefault(id, 0) >= inventory.get(id)) continue;
                for (int slot = 0; slot < cards.size(); slot++) {
                    if (cards.get(slot) == id) continue;
                    var replacement = new ArrayList<>(cards);
                    replacement.set(slot, id);
                    if (visit(List.copyOf(replacement)) && ++substitutions >= 96) return;
                }
            }
        }

        private List<Integer> randomDeck() {
            int[] remaining = counts.clone();
            int available = inventory.values().stream().mapToInt(Integer::intValue).sum();
            var cards = new ArrayList<Integer>(original.size());
            for (int slot = 0; slot < original.size(); slot++) {
                int draw = layoutRandom.nextInt(available--);
                for (int i = 0; i < ids.length; i++) {
                    if (draw < remaining[i]) {
                        cards.add(ids[i]);
                        remaining[i]--;
                        break;
                    }
                    draw -= remaining[i];
                }
            }
            return List.copyOf(cards);
        }

        private Candidate evaluate(List<Integer> cards, int sampleLimit, BooleanSupplier stop) {
            engine.validateDeckLimit(own, cards);
            var hand = remainingHand(own, cards);
            if (hand != null && hand.size() > 200) {
                throw new Engine.Rejected("INVALID_INPUT", "下阵后手牌超过模拟器的200张上限。");
            }
            var outcomes = own.cardOutcomes() == null ? null : own.cardOutcomes().stream()
                    .filter(entry -> entry.slot() < cards.size() && cards.get(entry.slot()) == entry.cardId()).toList();
            var player = new Player(own.id(), own.name(), own.characterId(), own.hp(), own.maxHp(), own.anima(),
                    own.defense(), own.swordIntent(), cards, own.talentIds(), own.buffs(), hand,
                    own.seasonTalents(), own.battleRound(), outcomes, own.singleUseCardLimit());
            var duel = new Request(request.rulesetId(), request.mode(), request.initializationStage(),
                    request.firstActorId(), List.of(player, request.players().get(1)));
            var first = engine.sampleBattle(duel, SEEDS[0], stop);
            int samples = first.randomUsed() ? sampleLimit : 1;
            int wins = 0;
            int winningActions = 0;
            long winningHp = 0;
            long hpMargin = 0;
            for (int i = 0; i < samples; i++) {
                var sample = i == 0 ? first : engine.sampleBattle(duel, SEEDS[i], stop);
                if (sample.won()) {
                    wins++;
                    winningActions += sample.actions();
                    winningHp += sample.ownHp();
                }
                hpMargin += (long) sample.ownHp() - sample.enemyHp();
            }
            return new Candidate(cards, hand, wins, samples, first.randomUsed(),
                    wins == 0 ? null : (double) winningActions / wins,
                    wins == 0 ? null : (double) winningHp / wins,
                    (double) hpMargin / samples);
        }

        private void refine() {
            if (totalStopped() || (best.stream().noneMatch(Candidate::random)
                    && (baseline == null || !baseline.random()))) return;
            refining = true;
            onProgress.accept(report());
            var confirmed = new ArrayList<Candidate>();
            var rejected = new HashSet<List<Integer>>();
            Candidate checkedBaseline = baseline;
            try {
                for (var candidate : best) {
                    var result = confirm(candidate, rejected);
                    if (result != null) confirmed.add(result);
                    if (candidate.cards().equals(original)) checkedBaseline = result;
                }
                if (baseline != null && best.stream().noneMatch(c -> c.cards().equals(original))) {
                    checkedBaseline = confirm(baseline, rejected);
                }
                best.clear();
                best.addAll(confirmed);
                best.sort(BEST_FIRST);
                baseline = checkedBaseline;
                evaluated -= rejected.size();
                skipped += rejected.size();
            } catch (CancellationException stop) {
                // A partially refined shortlist would mix two sample budgets.
            } finally {
                refining = false;
            }
        }

        private Candidate confirm(Candidate candidate, Set<List<Integer>> rejected) {
            if (!candidate.random()) return candidate;
            try {
                return evaluate(candidate.cards(), FINAL_SAMPLES, this::totalStopped);
            } catch (Engine.Rejected failure) {
                rejected.add(candidate.cards());
                rememberFailure(failure);
                return null;
            }
        }

        private void rememberFailure(Engine.Rejected rejected) {
            if (skippedReason == null) skippedReason = rejected.getMessage();
        }

        private Report report() {
            return new Report(evaluated, skipped, unsupportedHandCards, exhaustive, refining, baseline,
                    best.stream().filter(c -> c.wins() > 0).limit(RESULT_LIMIT).toList(), skippedReason);
        }
    }

    private static int countArrangements(int[] remaining, int slots, int limit) {
        if (slots == 0) return 1;
        int total = 0;
        for (int i = 0; i < remaining.length && total < limit; i++) {
            if (remaining[i] == 0) continue;
            remaining[i]--;
            total += countArrangements(remaining, slots - 1, limit - total);
            remaining[i]++;
        }
        return total;
    }

    private static List<Integer> asDeck(int[] layout) {
        return java.util.Arrays.stream(layout).boxed().toList();
    }

    private static Map<Integer, Integer> countCards(List<Integer> cards) {
        var counts = new HashMap<Integer, Integer>();
        for (int id : cards) counts.merge(id, 1, Integer::sum);
        return counts;
    }

    static List<Integer> remainingHand(Player own, List<Integer> nextDeck) {
        if (own.hand() == null) return null;
        var oldCounts = countCards(own.cards());
        var nextCounts = countCards(nextDeck);
        var takeFromHand = new HashMap<Integer, Integer>();
        nextCounts.forEach((id, count) -> takeFromHand.put(id, count - oldCounts.getOrDefault(id, 0)));
        var hand = new ArrayList<Integer>();
        for (int id : own.hand()) {
            if (id != 0 && takeFromHand.getOrDefault(id, 0) > 0) takeFromHand.merge(id, -1, Integer::sum);
            else hand.add(id);
        }
        for (int id : own.cards()) {
            if (id == 0) continue;
            if (nextCounts.getOrDefault(id, 0) > 0) nextCounts.merge(id, -1, Integer::sum);
            else hand.add(id);
        }
        return List.copyOf(hand);
    }
}
