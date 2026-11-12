package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.Change;

enum PlantRefinements {
    RETURN(10008,9000001,"plantLife"),
    DAMAGE(10009,9000002,"plantDamage"),
    RECOVERY(10010,9000004,"plantRecovery"),
    ATTACK(10011,9000008,"plantAttack"),
    SPEED(10012,9000010,"plantSpeed"),
    INJURY(10013,9000012,"plantInjury"),
    DEFENSE(10014,9000016,"plantDefense"),
    REDUCE_MAX_HP(10017,9000021,"plantReduceMaxHp"),
    REDUCE_ATTACK(10018,9000022,"plantReduceAttack"),
    REPEL(10019,9000023,"plantRepel"),
    CHASE(10020,9000024,"plantChase"),
    DREAM_SPEED(0,385,"plantDreamSpeed");

    final int buffId;
    final int cardId;
    final String key;

    PlantRefinements(int buffId, int cardId, String key) {
        this.buffId = buffId;
        this.cardId = cardId;
        this.key = key;
    }

    static boolean isInput(String key) {
        return Arrays.stream(values()).anyMatch(plant -> plant.key.equals(key));
    }

    static Map<String,Integer> fromCache(Map<Integer,Integer> buffs) {
        var result = new LinkedHashMap<String,Integer>();
        for (var plant : values()) {
            if (plant.buffId == 0) continue;
            int amount = buffs.getOrDefault(plant.buffId, 0);
            if (amount > 0) result.put(plant.key, amount);
        }
        return result;
    }

    void apply(List<Change> changes, Fighter self, int amount) {
        var opponent = self.opponent;
        switch (this) {
            case RETURN -> growCareer(changes, self, amount);
            case DAMAGE -> damage(changes, self, opponent, amount);
            case RECOVERY -> addStatus(changes, self, "recovery", amount);
            case ATTACK -> addStatus(changes, self, "attackBonus", amount);
            case SPEED -> { } // The simulator takes the resolved first actor from the input.
            case DREAM_SPEED -> addStatus(changes, self, "deathBattleWillIfSecond", 1);
            case INJURY -> addStatus(changes, opponent, "internalInjury", amount);
            case DEFENSE -> addDefense(changes, self, amount);
            case REDUCE_MAX_HP -> modifyMaxHp(changes, opponent, -amount);
            case REDUCE_ATTACK -> addStatus(changes, opponent, "attackReduction", amount);
            case REPEL -> addStatus(changes, self, "repel", amount);
            case CHASE -> {
                loseHp(changes, self, amount);
                addStatus(changes, self, "forcedChases", 1);
            }
        }
    }
}
