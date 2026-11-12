package local.yxp;

import static local.yxp.Models.Player;

final class BattleTestDecks {
    private BattleTestDecks() {}

    // Mechanic fixtures deliberately repeat the same card in every slot.
    static Player allowRepeatedSingleUseCards(Player player) {
        return new Player(player.id(), player.name(), player.characterId(), player.hp(), player.maxHp(),
                player.anima(), player.defense(), player.swordIntent(), player.cards(), player.talentIds(),
                player.buffs(), player.hand(), player.seasonTalents(), player.battleRound(), player.cardOutcomes(), 8);
    }
}
