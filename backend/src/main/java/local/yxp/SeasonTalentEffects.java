package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

final class SeasonTalentEffects {
    static final Set<Integer> SUPPORTED = Set.of(
            1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 3001, 3002, 3003, 3004, 3011, 3012,
            5001, 5004, 5009, 7002, 7005, 7010, 7012, 7017, 7022, 7026, 7035, 7036, 7037, 7042, 7048, 7049,
            7050, 7051, 7052, 7054, 7056, 7057, 7058, 7059, 7060, 7063, 7064, 7065, 7066, 7067, 7068, 7069,
            7071, 7076, 7077, 7078, 7079, 7080, 7083, 7084, 7087, 7088, 7089, 7090, 7091, 7092,
            7094, 7095, 7096, 7097, 7098, 7100, 7103, 7105, 7109, 7110, 7112, 7113, 7115, 7118,
            7122, 7123, 7128, 7129, 7130, 7132, 7133, 7135, 7136, 7138, 11001, 11002, 11004,
            13001, 13002, 13003, 13004, 13005, 13007, 13008, 13009, 13010, 13011, 13013, 13014, 13015, 13016, 13017, 13019,
            15018, 17071, 23045, 23058, 23069, 23079, 27021, 27023, 28001);
    static final List<Integer> OPENING_ORDER = List.of(
            1001, 3002, 1002, 1003, 7096, 3011, 1004, 7128, 7129, 7135, 7136, 11002, 11004, 15018,
            1005, 7005, 1006, 7036, 7054, 7058, 7059, 7063, 7067, 7069, 7077, 7078, 7083, 7087,
            7094, 7100, 7103, 7109, 7112, 7122, 7123, 7105, 1007, 7066, 1008, 7098, 3001, 7095);
    static final Map<String,String> STATES = Map.of(
            "seasonTemporaryGuard", "刻纹临时护体", "seasonFirstAnima", "龙吟聚灵",
            "seasonFirstAgility", "风灵展翼", "seasonInjuryGains", "灯焰催心",
            "seasonHexGains", "无尽卦衍", "seasonAnimaGrowth", "无忧灵酿",
            "seasonFirstForce", "气盖山河", "seasonInjuryOnAttack", "淬毒刻纹", "seasonDoubleRecovery", "枯荣往复",
            "seasonDowngrade", "厄劫刻纹");

    private SeasonTalentEffects() {}

    static List<SeasonChoice> choices(Player player) {
        return player.seasonTalents() == null ? List.of()
                : player.seasonTalents().stream().filter(choice -> choice == null || !isSkippedChoice(choice.id())).toList();
    }

    static boolean isSkippedChoice(int id) {
        return id >= 0 && id < 1000; // Client XianMoModule.IsSkipStrategy.
    }

    static void validate(Rules rules, Player player, String stage) {
        var choices = choices(player);
        require(choices.size() <= 3, "每位玩家最多选择3个修道者仙命");
        if (player.battleRound() != null) require(player.battleRound() > 0 && player.battleRound() <= 100, "对局轮次范围为1至100");
        var ids = new HashSet<Integer>();
        for (var choice : choices) {
            require(choice != null && ids.add(choice.id()), "修道者仙命不能留空或重复");
            var talent = rules.seasonById.get(choice.id());
            require(talent != null, "未知修道者仙命：" + choice.id());
            if (!talent.battle()) continue;
            if (!talent.supported()) throw new Rejected("UNSUPPORTED", "尚未支持修道者仙命：" + talent.name());
            require(!"POST_OPENING".equals(stage), "修道者仙命需要开场前状态");
            require(!Boolean.TRUE.equals(choice.disabled()) || talent.toggleable(), talent.name() + "不支持禁用");
            if (talent.slotRequired()) require(choice.slot() != null && choice.slot() > 0 && choice.slot() <= player.cards().size(), talent.name() + "需要指定卡槽");
            if (talent.effectRound() > 0 && talent.id() != 28001)
                require(player.battleRound() != null, talent.name() + "需要填写当前对局轮次");
            if (talent.effectRound() > 0 && player.battleRound() != null && player.battleRound() < talent.effectRound()
                    && talent.id() != 3012 && talent.id() != 28001) continue;
            if (talent.id() == 7069 || talent.id() == 15018) require(player.hand() != null, talent.name() + "需要明确手牌，确认没有手牌时请选择空手牌");
            if (talent.id() == 7123) require(player.buffs() != null && player.buffs().containsKey("destiny"), talent.name() + "需要填写命元");
        }
    }

    static void configure(Fighter fighter) {
        for (var choice : choices(fighter.p)) {
            var talent = fighter.rules.seasonById.get(choice.id());
            if (talent.battle() && talent.supported()) fighter.season.put(choice.id(), choice);
        }
    }

    static boolean has(Fighter f, int id) {
        var choice = f.season.get(id);
        if (choice == null || Boolean.TRUE.equals(choice.disabled())) return false;
        int round = f.rules.seasonById.get(id).effectRound();
        return id == 3012 || id == 28001 || round == 0 || f.p.battleRound() != null && f.p.battleRound() >= round;
    }

    static int param(Fighter f, int id, int index) {
        return f.rules.seasonById.get(id).param(index);
    }

    static void upgradeRear(Fighter f, int slots) {
        for (int i = Math.max(0, f.cards.length - slots); i < f.cards.length; i++) {
            var card = f.rules.byId.get(f.cards[i]);
            if (card.hidden() || card.noUpgrade() || card.rarity() >= 2) continue;
            var upgraded = f.rules.byId.get(card.id() + (2 - card.rarity()) * 10000);
            if (upgraded != null && upgraded.supported()) f.cards[i] = upgraded.id();
        }
    }

    static Rules.Card changeCardLevel(List<Change> ch, Fighter f, Rules.Card card) {
        if (f.status("seasonDowngrade") > 0 && card.rarity() >= 1 && card.baseId() != 19) {
            var downgrade = f.rules.byId.get(card.id() - 10000);
            if (downgrade != null && downgrade.supported()) {
                addStatus(ch, f, "seasonDowngrade", -1);
                card = downgrade;
            }
        }
        if (has(f, 13003) && !f.used[f.slot] && slot(f, 13003) && !card.hidden() && !card.noUpgrade() && card.rarity() < 2) {
            var upgrade = f.rules.byId.get(card.id() + 10000);
            if (upgrade != null && upgrade.supported()) card = upgrade;
        }
        return card;
    }

    static void opening(List<Change> ch, Fighter f, int id) {
        if (!has(f, id) && !(id == 7036 && f.season.containsKey(id))) return;
        int n = param(f, id, 0), m = param(f, id, 1);
        Fighter d = f.opponent;
        switch (id) {
            case 1001 -> { addAnima(ch, f, n); addAnima(ch, d, n); }
            case 1002 -> CareerEffects.both(ch, f, "attackBonus", n);
            case 1003 -> { growCareer(ch, f, f.hp * n / 100); growCareer(ch, d, d.hp * n / 100); }
            case 1004 -> CareerEffects.both(ch, f, "internalInjury", n);
            case 1005 -> CareerEffects.both(ch, f, "dreamChaseBlock", n);
            case 1006 -> { reduceLifeAndCap(ch, f, f.hp * n / 100); reduceLifeAndCap(ch, d, d.hp * n / 100); }
            case 1007 -> CareerEffects.both(ch, f, "temporaryUpgrades", n);
            case 1008 -> CareerEffects.both(ch, f, "attackLifeSong", n);
            case 3001 -> addStatus(ch, d, "attackReduction", n);
            case 3002 -> addStatus(ch, f, "guard", n);
            case 3011 -> addStatus(ch, f, "seasonFirstAnima", n);
            case 7005 -> {
                int before = f.cloud;
                f.cloud += n;
                change(ch, f, "cloudChain", before, f.cloud);
                addStatus(ch, f, "cloudSea", m);
            }
            case 7036 -> { if (has(f, id)) addStatus(ch, f, "repel", n); heal(ch, f, m); }
            case 7054, 7058, 7100, 7122, 7128, 7129 -> upgradeFirst(f, id);
            case 7059 -> addStatus(ch, f, "seasonFirstAgility", n);
            case 7063 -> addStatus(ch, d, "seasonInjuryGains", n);
            case 7066 -> { activate(ch, f, "woodActivation"); addAnima(ch, f, n); }
            case 7067 -> addStatus(ch, f, "swordQi", n);
            case 7069 -> {
                long clouds = Arrays.stream(f.cards).mapToObj(f.rules.byId::get).filter(f::isCloud).count()
                        + f.hand.stream().map(f.rules.byId::get).filter(f::isCloud).count();
                addAnima(ch, f, Math.min(n, (int) clouds / 2));
            }
            case 7077 -> addStatus(ch, f, "seasonHexGains", n);
            case 7078 -> {
                if (!d.practiceTarget && (f.p.buffs() == null || d.p.buffs() == null
                        || !f.p.buffs().containsKey("experience") || !d.p.buffs().containsKey("experience")))
                    throw new Rejected("INVALID_INPUT", "灯灵星耀需要填写双方修为");
                addStatus(ch, f, "starPower", 1 + (!d.practiceTarget && f.status("experience") - d.status("experience") >= n ? 1 : 0));
            }
            case 7083 -> growCareer(ch, f, n * ElementalEffects.elements(f, f.rules));
            case 7087 -> addStatus(ch, f, "seasonFirstForce", n);
            case 7094 -> { addStatus(ch, f, "attackBonus", n); addStatus(ch, f, "internalInjury", m); }
            case 7095 -> { activate(ch, f, "earthActivation"); addDefense(ch, f, n); }
            case 7096 -> { activate(ch, f, "metalActivation"); addStatus(ch, f, "sharpness", n); }
            case 7098 -> { activate(ch, f, "fireActivation"); reduceLifeAndCap(ch, d, n); }
            case 7103 -> growCareer(ch, f, f.hp / n);
            case 7105 -> addStatus(ch, f, "underworld", n);
            case 7109 -> addStatus(ch, f, "seasonAnimaGrowth", m);
            case 7112 -> modifyMaxHp(ch, f, n);
            case 7123 -> { if (f.destiny <= n) growCareer(ch, f, f.hp * m / 100); }
            case 7136 -> addStatus(ch, f, "ragingCount", 1);
            case 7135 -> {
                setStatus(ch, f, "seasonDoubleRecovery", 1);
                setStatus(ch, d, "seasonDoubleRecovery", 1);
            }
            case 11002 -> {
                addStatus(ch, f, "hexagram", 1);
                setStatus(ch, f, "starMask", f.status("starMask") | (((1 << f.cards.length) - 1) & ~3));
            }
            case 11004 -> ForgeEffects.physique(ch, f, param(f, id, 2));
            case 15018 -> { if (!f.hand.isEmpty()) growCareer(ch, f, f.hp * n / 100); }
        }
    }

    private static void upgradeFirst(Fighter f, int id) {
        for (int i = 0; i < f.cards.length; i++) {
            var card = f.rules.byId.get(f.cards[i]);
            if (card.noUpgrade()) continue;
            boolean eligible = switch (id) {
                case 7054 -> card.name().contains("雷") || contains(card, "再次行动");
                case 7058 -> card.name().contains("木灵");
                case 7100 -> card.name().contains("火灵");
                case 7122 -> card.name().contains("水灵");
                case 7128 -> card.name().contains("星弈") || card.name().contains("卦");
                case 7129 -> contains(card, "灵气") || contains(card, "卦象");
                default -> false;
            };
            if (!eligible) continue;
            var upgraded = f.rules.byId.get(card.id() + 10000);
            if (upgraded != null && upgraded.supported() && upgraded.rarity() <= 2) f.cards[i] = upgraded.id();
            break;
        }
    }

    static boolean contains(Rules.Card card, String keyword) {
        return card.desc() != null && card.desc().contains(keyword);
    }

    static void start(List<Change> ch, Fighter f) {
        int temporary = f.status("seasonTemporaryGuard");
        if (temporary > 0) {
            setStatus(ch, f, "seasonTemporaryGuard", 0);
            addStatus(ch, f, "guard", -temporary);
        }
        if (has(f, 3012)) addDefense(ch, f, param(f, 3012, f.p.battleRound() >= f.rules.seasonById.get(3012).effectRound() ? 1 : 0));
        if (has(f, 7064) && f.hp <= param(f, 7064, 0) && f.seasonUsed.add(7064)) addStatus(ch, f, "guard", 1);
    }

    static void end(List<Change> ch, Fighter f) {
        if (has(f, 7002) && f.turnAttacks == 0) addSword(ch, f, param(f, 7002, 0));
        if (has(f, 7138)) addDefense(ch, f, f.status("waterForce") / param(f, 7138, 0));
    }

    static void before(List<Change> ch, Fighter f, Rules.Card card, boolean virtual) {
        String name = card.name();
        if (has(f, 5001) && !virtual && !card.hidden() && card.level() == 1 && slot(f, 5001)) f.requestChase = true;
        if (has(f, 5004) && card.baseId() == 0) addStatus(ch, f, "agility", param(f, 5004, 0));
        if (has(f, 7010) && f.isCloud(card)) addStatus(ch, f, "cloudSea", 1);
        if (has(f, 7017) && f.isFormation(card) && f.seasonUsed.add(7017)) {
            addDefense(ch, f, param(f, 7017, 1));
            addStatus(ch, f, "guard", param(f, 7017, 2));
        }
        if (has(f, 7022) && f.slot == 7) addStatus(ch, f.opponent, "internalInjury", param(f, 7022, 0));
        if (has(f, 7026) && name.contains("金灵")) addAnima(ch, f, param(f, 7026, 0));
        if (has(f, 7026) && name.contains("水灵")) addStatus(ch, f, "sharpness", param(f, 7026, 1));
        if (has(f, 7037) && card.hpCost() > 0) damage(ch, f.opponent, card.hpCost() * param(f, 7037, 0) / 100);
        if (has(f, 7050) && f.isCloud(card)) addStatus(ch, f, "moonRounds", 1);
        if (has(f, 7052) && f.isRaging(card)) addAnima(ch, f, 1);
        if (has(f, 7056) && name.contains("星弈") && takeUse(f, 7056, param(f, 7056, 0)))
            StarEffects.addStar(ch, f, StarEffects.next(f, f.slot));
        if (has(f, 7057) && name.contains("卦") && !f.used[f.slot]) addStatus(ch, f.opponent, "weakness", 1);
        if (has(f, 7060) && card.baseId() == 381) addAnima(ch, f, 1);
        if (has(f, 7079) && StarEffects.starred(f) && takeUse(f, 7079, param(f, 7079, 0)))
            addStatus(ch, f.opponent, "internalInjury", 1);
        if (has(f, 7089) && name.contains("掌")) addStatus(ch, f, "agility", param(f, 7089, 0));
        if (has(f, 7097) && name.contains("土灵")) addStatus(ch, f, "sharpness", param(f, 7097, 0));
        if (has(f, 7118) && card.hpCost() > 0 && firstThisTurn(f, 7118)) {
            addAnima(ch, f, 1);
            heal(ch, f, 3);
        }
        if (card.baseId() == 19) {
            if (has(f, 23045)) addAnima(ch, f, 1);
            if (has(f, 7130)) {
                addStatus(ch, f, "cloudSea", param(f, 7130, 0));
                int old = f.cloud;
                f.cloud += param(f, 7130, 1);
                change(ch, f, "cloudChain", old, f.cloud);
            }
        }
        if (has(f, 7132) && f.isFormation(card)) addStatus(ch, f, "cloudSea", 1);
        if (has(f, 23079) && card.baseId() == 22) temporaryGuard(ch, f, param(f, 23079, 0));
        if (!virtual && !f.used[f.slot]) {
            for (var id : f.season.keySet()) {
                if (f.rules.seasonById.get(id).slotRequired() && id != 5001 && slot(f, id) && has(f, id))
                    inscription(ch, f, card, id);
            }
        }
    }

    static boolean firstThisTurn(Fighter f, int id) {
        int turn = f.resolution.turn;
        return !Objects.equals(f.seasonTurns.put(id, turn), turn);
    }

    private static boolean takeUse(Fighter f, int id, int limit) {
        int count = f.seasonUses.getOrDefault(id, 0);
        if (count >= limit) return false;
        f.seasonUses.put(id, count + 1);
        return true;
    }

    private static boolean slot(Fighter f, int id) {
        return Objects.equals(f.season.get(id).slot(), f.slot + 1);
    }

    static Rules.Card repeatAtBaseLevel(Fighter f, Rules.Card card) {
        if (!has(f, 5009) || !slot(f, 5009) || card.level() > 2 || card.hidden()) return null;
        var repeat = f.rules.byId.get(card.baseId());
        if (repeat == null || !repeat.supported()) throw new Rejected("UNSUPPORTED", "双生刻纹的1级卡牌尚未支持：" + card.name());
        return repeat;
    }

    private static void inscription(List<Change> ch, Fighter f, Rules.Card card, int id) {
        int n = param(f, id, 0), m = param(f, id, 1);
        Fighter d = f.opponent;
        switch (id) {
            case 13001 -> addStatus(ch, f, "seasonInjuryOnAttack", n);
            case 13002 -> {
                for (int i = 0; i < m; i++) {
                    var debuffs = CareerEffects.NEGATIVE.stream().filter(key -> f.status(key) > 0).toList();
                    if (debuffs.isEmpty()) {
                        heal(ch, f, (m - i) * n);
                        break;
                    }
                    addStatus(ch, f, debuffs.get(StarEffects.randomValue(f, 0, debuffs.size() - 1)), -1);
                }
            }
            case 13004 -> growCareer(ch, f, f.status("hpLost") * n / 100);
            case 13005 -> addDefense(ch, f, n * ElementalEffects.elements(f, f.rules));
            case 13007 -> addStatus(ch, d, "seasonDowngrade", 1);
            case 13008 -> temporaryGuard(ch, f, n);
            case 13009 -> { addDefense(ch, f, n); addStatus(ch, f, "moonRounds", m); }
            case 13010 -> {
                int slot = f.slot;
                for (int i = 0; i < n; i++) {
                    slot = StarEffects.next(f, slot);
                    StarEffects.addStar(ch, f, slot);
                }
            }
            case 13011 -> {
                switch (ElementalEffects.element(card.name())) {
                    case 1 -> addStatus(ch, f, "sharpness", n);
                    case 2, 3 -> addAnima(ch, f, m);
                    case 4, 5 -> addDefense(ch, f, param(f, id, 2));
                }
            }
            case 13013 -> { addStatus(ch, f, "hexagram", n); growCareer(ch, f, f.status("hexagram") * m); }
            case 13014 -> { ForgeEffects.physique(ch, f, n); addDefense(ch, f, f.status("physique") / m); }
            case 13015 -> reduceLifeAndCap(ch, d, n);
            case 13016 -> { addSword(ch, f, n); damage(ch, d, f.sword / m); }
            case 13017 -> { addStatus(ch, d, "internalInjury", n); addDefense(ch, f, StarEffects.debuffs(d)); }
            case 13019 -> heal(ch, f, n + StarEffects.debuffs(f));
        }
    }

    private static void temporaryGuard(List<Change> ch, Fighter f, int amount) {
        addStatus(ch, f, "guard", amount);
        addStatus(ch, f, "seasonTemporaryGuard", amount);
    }

    static int animaGain(List<Change> ch, Fighter f, int delta) {
        if (delta <= 0) return delta;
        int bonus = f.status("seasonFirstAnima"), agility = f.status("seasonFirstAgility");
        if (bonus > 0) setStatus(ch, f, "seasonFirstAnima", 0);
        if (agility > 0) {
            setStatus(ch, f, "seasonFirstAgility", 0);
            addStatus(ch, f, "agility", agility);
        }
        if (f.status("seasonAnimaGrowth") > 0) {
            addStatus(ch, f, "seasonAnimaGrowth", -1);
            growCareer(ch, f, param(f, 7109, 0));
        }
        return delta + bonus;
    }

    static int statusGain(List<Change> ch, Fighter f, String key, int delta) {
        if (delta <= 0) return delta;
        switch (key) {
            case "attackBonus" -> {
                if (has(f, 7035)) { addStatus(ch, f, "woodThorns", 1); delta--; }
            }
            case "starPower" -> {
                if (has(f, 23069)) { addStatus(ch, f.opponent, "internalInjury", delta); return 0; }
            }
            case "internalInjury" -> {
                if (f.status("seasonInjuryGains") > 0) { addStatus(ch, f, "seasonInjuryGains", -1); delta++; }
            }
            case "hexagram" -> {
                if (f.status("seasonHexGains") > 0) { addStatus(ch, f, "seasonHexGains", -1); delta++; }
            }
            case "force" -> {
                delta += f.status("seasonFirstForce");
                if (f.status("seasonFirstForce") > 0) setStatus(ch, f, "seasonFirstForce", 0);
            }
        }
        return delta;
    }

    static void statusChanged(List<Change> ch, Fighter f, String key, int old, int value) {
        if (key.equals("guard") && old > value && f.status("seasonTemporaryGuard") > 0)
            addStatus(ch, f, "seasonTemporaryGuard", value - old);
        if (value > old && f.opponent != null && has(f.opponent, 7080)
                && (key.equals("internalInjury") || key.equals("weakness") || key.equals("vulnerability")))
            addAnima(ch, f.opponent, 1);
        if (value > old && key.equals("agility") && has(f, 7091) && takeUse(f, 7091, param(f, 7091, 0)))
            ForgeEffects.physique(ch, f, 1);
        if (value > old && key.endsWith("Activation")) {
            if (has(f, 7048) && old > 0 && firstThisTurn(f, 7048)) addAnima(ch, f, 1);
            if (has(f, 7084) && key.equals("waterActivation")) activate(ch, f, "earthActivation");
            if (has(f, 7084) && key.equals("earthActivation")) addDefense(ch, f, param(f, 7084, 0));
        }
    }

    static int attackBonus(Fighter f, Rules.Card card) {
        int bonus = 0;
        if (has(f, 3003) && f.activeCard != null && !f.afterCard && f.slot >= param(f, 3003, 0)) bonus += param(f, 3003, 1);
        if (has(f, 7088) && f.activeCard != null && !f.afterCard && f.slot == 7) bonus += StarEffects.debuffs(f) / param(f, 7088, 0);
        if (has(f, 7092) && !f.afterCard && ForgeEffects.isFist(f, card)) bonus += Math.min(param(f, 7092, 1), f.anima / param(f, 7092, 0));
        return bonus;
    }
}
