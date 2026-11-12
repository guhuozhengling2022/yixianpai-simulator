package local.yxp;

import java.util.*;
import static local.yxp.Models.*;
import static local.yxp.Engine.*;

final class CardOutcomeInputs {
    private record Queue(int baseId, ArrayDeque<Integer> values) {}
    private final Map<Integer,Queue> slots = new HashMap<>();

    CardOutcomeInputs(Player player) {
        if (player.cardOutcomes() == null) return;
        for (var outcome : player.cardOutcomes())
            slots.put(outcome.slot(), new Queue(baseId(outcome.cardId()), new ArrayDeque<>(outcome.values())));
    }

    static void validate(Player player) {
        if (player.cardOutcomes() == null) return;
        var slots = new HashSet<Integer>();
        int count = 0;
        for (var outcome : player.cardOutcomes()) {
            require(outcome != null && outcome.slot() >= 0 && outcome.slot() < player.cards().size()
                    && slots.add(outcome.slot()), "指定结果的卡槽无效或重复");
            require(Objects.equals(player.cards().get(outcome.slot()), outcome.cardId()), "指定结果对应的卡牌已改变");
            require(outcome.values() != null && outcome.values().size() <= 2048, "卡槽指定结果最多2048项");
            count += outcome.values().size();
            require(count <= 2048, "每位玩家的指定结果最多2048项");
            for (var value : outcome.values()) require(value != null && value >= -1, "指定结果只接受-1或非负整数");
        }
    }

    private static int baseId(int id) { return id - id / 10000 % 100 * 10000; }

    private Queue queue(int slot, Integer cardId) {
        var entry = slots.get(slot);
        return entry != null && cardId != null && entry.baseId() == baseId(cardId) ? entry : null;
    }

    boolean hasNext(int slot, Integer cardId) {
        var entry = queue(slot, cardId);
        return entry != null && !entry.values().isEmpty();
    }

    Integer poll(int slot, Integer cardId) {
        var entry = queue(slot, cardId);
        return entry == null ? null : entry.values().poll();
    }
}
