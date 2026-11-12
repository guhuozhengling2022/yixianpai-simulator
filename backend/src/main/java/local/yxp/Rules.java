package local.yxp;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Rules {
    public static final Map<String,String> STATUS_NAMES=careerStatuses(Map.ofEntries(Map.entry("beastFormation","百兽灵剑阵"),Map.entry("beastTriggered","本回合百兽已触发"),Map.entry("gainChase","下次加生命或灵气追击"),Map.entry("allChase","所有牌再次行动"),Map.entry("cloudAfterAttack","云剑周天追加攻击"),Map.entry("woodHealByAttack","木灵天罡治疗"),Map.entry("nextShatter","下次碎防攻击次数"),Map.entry("woundDefenseAttacks","下次击伤转防次数"),Map.entry("nextCloudChase","下张云剑追击"),Map.entry("doubleFireEarth","下张火土重复"),Map.entry("waterTwiceRounds","水势双结算回合"),Map.entry("previousTurnHp","上回合开始生命"),Map.entry("turnStartHp","本回合开始生命"),Map.entry("swordToAnima","耗剑意转灵气次数"),Map.entry("healToSharpRounds","治疗转锋锐回合"),Map.entry("waterDefenseLimit","回合末水势加防上限"),Map.entry("waterDefenseUnlimited","水势加防无上限"),Map.entry("sharpToFireRounds","锋锐转燃烧回合"),Map.entry("swordChaseCount","剑牌再次行动次数"),Map.entry("sharpnessFullRefund","下次锋锐全返还"),Map.entry("cardsUsed","累计使用卡牌次数"),Map.entry("ragingUpgrades","下次1级狂剑升级次数"),Map.entry("cloudRagingBridge","云狂互认"),Map.entry("cloudArrayAlias","阵法师牌视为云剑"),Map.entry("universalNext","下张视为云剑和狂剑"),Map.entry("nextAttackBonus","下次攻击额外攻击"),Map.entry("doubleNextAttack","下次双倍剑意加攻"),Map.entry("cloudTotal","累计云剑次数"),Map.entry("elementalCardsUsed","累计五行牌次数"),Map.entry("animaGained","累计获得灵气"),Map.entry("sharpnessGained","累计获得锋锐"),Map.entry("waterForceGained","累计获得水势"),Map.entry("defenseGained","累计获得防御"),Map.entry("hpLost","累计损失生命"),Map.entry("woundTotal","累计击伤"),Map.entry("swordDefenseGain","每加剑意加防"),Map.entry("defensePerTurn","每回合防御"),Map.entry("formationCloudSea","每用剑阵加云海"),Map.entry("swordQiPerSword","每用剑牌加剑气"),Map.entry("ragingPerTurn","回合末狂剑计数"),Map.entry("chaseSharpness","追击加锋锐"),Map.entry("chaseFireDefense","追击加防并减生命及上限"),Map.entry("doubleWaterPending","下回合水势灵气临时翻倍"),Map.entry("temporaryWater","临时水势"),Map.entry("temporaryAnima","临时灵气"),Map.entry("attackDrain","攻击吸血次数"),Map.entry("attackToThorns","加攻转木刺上限"),Map.entry("lossToNextDefense","本回合损失换下回合防御"),Map.entry("sealAgilityCount","灵印灵阵加身法次数"),Map.entry("sealAgilityPermanent","灵印灵阵常驻身法"),Map.entry("consumeNext","使用下张牌后消耗"),Map.entry("woodThorns","木刺"),Map.entry("activateNext","下次五行牌先激活次数"),Map.entry("activationDamage","激活五行伤害"),Map.entry("mixedChase","混元无极阵次数"),Map.entry("elementalNoAttack","五行天髓诀次数"),Map.entry("swordQi","剑气"),Map.entry("usedSlotMask","已使用槽位掩码"),Map.entry("cliff","断崖次数"),Map.entry("restoreDefense","合八荒次数"),Map.entry("skipTurns","无法行动回合"),Map.entry("agility","身法"),Map.entry("chaseCount","已再次行动次数"),Map.entry("noHealing","禁止治疗回合"),Map.entry("springRounds","春风拂回合"),Map.entry("springAttacks","泉涌攻击次数"),Map.entry("dustStep","逐尘步"),Map.entry("branchDrain","散枝叶次数"),Map.entry("cloudHeal","柔心治疗"),Map.entry("cloudStep","步月登云"),Map.entry("cloudSea","云海"),Map.entry("ragingCount","已使用狂剑次数"),Map.entry("swordFormationCount","已使用剑阵次数"),Map.entry("swordCount","已使用剑牌次数"),Map.entry("spiritDiscount","灵剑费用减免"),Map.entry("halfAnima","聚灵心法"),Map.entry("halfAnimaRemainder","半点灵气"),Map.entry("swordPerTurn","每回合剑意"),Map.entry("moonRounds","水月剩余回合"),Map.entry("keepSwordCards","剑意流转"),Map.entry("formationAttack","溟空剑阵追加攻击"),Map.entry("ragingLifesteal","狂剑恢复百分比"),Map.entry("doubleRaging","双龙"),Map.entry("totalAttacks","累计攻击次数"),Map.entry("experience","修为"),Map.entry("animaDefenseGain","每加灵气加防"),Map.entry("animaDefenseLoss","每失灵气加防"),Map.entry("guard","护体"),Map.entry("weakness","虚弱"),Map.entry("vulnerability","破绽"),Map.entry("internalInjury","内伤"),Map.entry("attackBonus","加攻"),Map.entry("sharpness","锋锐"),Map.entry("externalInjury","外伤"),Map.entry("metalActivation","金灵激活"),Map.entry("metalFormation","金灵阵"),Map.entry("waterActivation","水灵激活"),Map.entry("waterForce","水势"),Map.entry("waterFormation","水灵阵"),Map.entry("woodActivation","木灵激活"),Map.entry("woodFormation","木灵阵"),Map.entry("fireActivation","火灵激活"),Map.entry("earthActivation","土灵激活"),Map.entry("fireFormation","火灵阵"),Map.entry("earthFormation","土灵阵"),Map.entry("healingTotal","累计治疗量（含溢出）"),Map.entry("defenseLost","累计减少防御"),Map.entry("maxHpLost","累计减少生命上限"),Map.entry("maxHpLossCount","减少生命上限次数"),Map.entry("nextDefense","下回合防御"),Map.entry("iron","铁骨剩余回合"),Map.entry("waterHide","潜遁剩余回合"),Map.entry("lastElement","上张五行（0无/1金/2水/3木/4火/5土）")));
    private static Map<String,String> careerStatuses(Map<String,String> base){var map=new LinkedHashMap<>(base);map.putAll(CareerEffects.STATES);map.putAll(StarEffects.STATES);map.putAll(ForgeEffects.STATES);map.putAll(TalentEffects.STATES);map.putAll(ExtraCardEffects.STATES);map.putAll(FoodEffects.STATES);map.putAll(AdditionalMechanics.STATES);map.putAll(CreatureEffects.STATES);map.putAll(ContextEffects.STATES);map.putAll(StanceEffects.STATES);map.putAll(SwordEmbryoEffects.STATES);map.putAll(SeasonTalentEffects.STATES);return Collections.unmodifiableMap(map);}
    public static final String ID = "local-snapshot-v15";
    public record Card(int id, String name, String desc, int rarity, int level, int attack,
                       int attackCount, int anima, int defense, int swordIntent,
                       boolean supported, String supportReason, String effect, List<Integer> params,
                       int cardType, boolean actionAgain,boolean requiresBattleParams,boolean hidden,int career,int chargeQi,int hpCost,boolean preparationSupported,int sect,boolean obsolete,int variant,boolean noUpgrade,int hexagram,int randomAttack,int randomDefense,int physique,int subcategory,boolean requiresContext,List<String> contextRequirements,String sourceConfidence) {
        public int baseId(){return id-id/10000%100*10000;}
        boolean countsTowardSingleUseLimit() { return cardType == 1 || cardType == 3; }
        public Card {contextRequirements=contextRequirements==null?List.of():List.copyOf(contextRequirements);params=params==null?List.of():List.copyOf(params);}
        public int param(int index) { return params!=null && params.size()>index ? params.get(index) : 0; }
    }
    public record Talent(int id,String name,int charId,String effect,int value,int talentId,int sect,int level,List<Integer> params,boolean openingOnly,String description) {
        public Talent {params=params==null?List.of():List.copyOf(params);}
        int param(int index){return index<params.size()?params.get(index):0;}
    }
    public final List<Card> cards;
    public final List<Map<String,Object>> characters;
    public final Map<Integer,Card> byId;
    public final List<Talent> talents;
    public final List<Map<String,Object>> talentCatalog;
    public final Map<Integer,Talent> talentById;
    public record SeasonTalent(int id, String name, String description, List<Integer> params,
                               int effectRound, boolean battle, boolean slotRequired, boolean toggleable,
                               boolean available, int requiredCharacter) {
        int param(int index) { return index < params.size() ? params.get(index) : 0; }
        public boolean supported() { return SeasonTalentEffects.SUPPORTED.contains(id); }
    }
    public final List<SeasonTalent> seasonTalents;
    public final Map<Integer,SeasonTalent> seasonById;
    public final Set<Integer> outsideBattleTalentIds;
    public Rules() {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/rules.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var gson = new Gson();
            cards = List.of(gson.fromJson(root.get("cards"), Card[].class));
            talents = List.of(gson.fromJson(root.get("talents"), Talent[].class));
            talentCatalog=new ArrayList<>();for(var item:root.getAsJsonArray("talentCatalog"))talentCatalog.add(gson.fromJson(item,new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType()));
            var talentIndex = new HashMap<Integer,Talent>();
            talents.forEach(t -> talentIndex.put(t.id(),t));
            talentById = Map.copyOf(talentIndex);
            var outside = new HashSet<Integer>();
            for (var talent : talentCatalog) {
                int id = ((Number) talent.get("id")).intValue();
                if (Boolean.FALSE.equals(talent.get("isBattleTalent")) && !talentById.containsKey(id)) outside.add(id);
            }
            outsideBattleTalentIds = Set.copyOf(outside);
            try (var seasonReader = new InputStreamReader(Objects.requireNonNull(
                    getClass().getResourceAsStream("/season-talents.json")), StandardCharsets.UTF_8)) {
                seasonTalents = List.of(gson.fromJson(seasonReader, SeasonTalent[].class));
            }
            var seasonIndex = new HashMap<Integer,SeasonTalent>();
            seasonTalents.forEach(t -> seasonIndex.put(t.id(), t));
            seasonById = Map.copyOf(seasonIndex);
            characters = new ArrayList<>();
            for (var c : root.getAsJsonArray("characters")) {
                characters.add(gson.fromJson(c,new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType()));
            }
            var index = new HashMap<Integer,Card>();
            cards.forEach(c -> { if(index.put(c.id(),c)!=null) throw new IllegalStateException("重复卡牌ID"); });
            byId = Map.copyOf(index);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
