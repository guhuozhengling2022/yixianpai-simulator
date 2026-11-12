package local.yxp;

import static local.yxp.Models.Player;

final class Initiative {
    static boolean known(Player player) {
        return player.buffs() != null && player.buffs().containsKey("experience");
    }

    static int score(Engine.Fighter fighter) {
        int speed = fighter.status("speed") + fighter.status("plantSpeed")
                + (fighter.status("plantDreamSpeed") > 0 ? 3 : 0);
        if (SeasonTalentEffects.choices(fighter.p).stream().anyMatch(choice -> choice != null && choice.id() == 3005))
            speed += fighter.rules.seasonById.get(3005).param(0);
        if (SeasonTalentEffects.choices(fighter.p).stream().anyMatch(choice -> choice != null && choice.id() == 29001)
                && fighter.hand != null) speed += fighter.hand.size();
        if (TalentEffects.has(fighter, 178) && fighter.p.battleRound() != null && fighter.p.battleRound() % 2 == 0)
            speed += 3;
        if (fighter.p.talentIds() != null && fighter.p.talentIds().stream().anyMatch(id -> id % 10000 == 128)
                && ElementalEffects.elements(fighter, fighter.rules) == 1) {
            for (int id : fighter.p.talentIds()) {
                if (id % 10000 == 128) speed += fighter.rules.talentById.get(id).param(0);
            }
        }
        return fighter.status("experience") + speed;
    }
}
