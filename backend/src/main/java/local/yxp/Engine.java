package local.yxp;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import static local.yxp.Models.*;

/** Pure, request-local simulation. Only the audited rule subset is accepted. */
public final class Engine {
    private static final int PREVIEW_ROUNDS = 8;
    private static final int TARGET_HP = 100;
    private final Rules rules;
    public Engine(Rules rules) { this.rules = rules; }
    public static final class Rejected extends RuntimeException {
        public final String status;
        public Rejected(String status, String message) { super(message); this.status=status; }
    }
    static void require(boolean condition, String message) {
        if(!condition) throw new Rejected("INVALID_INPUT",message);
    }
    public void validate(Request r) {
        require(r!=null,"请求不能为空");
        require(Rules.ID.equals(r.rulesetId()),"规则版本不匹配");
        require("STANDARD".equals(r.mode()),"当前只支持 STANDARD 模式");
        require("POST_OPENING".equals(r.initializationStage())||"PRE_OPENING".equals(r.initializationStage()),"初始化阶段必须为 PRE_OPENING 或 POST_OPENING");
        require(r.players()!=null && r.players().size()==2,"必须填写两位玩家");
        require(r.battleParams()==null||r.battleParams().size()<=2048,"battleParams最多2048项");
        if(r.battleParams()!=null)for(var value:r.battleParams())require(value!=null&&value>=-1,"battleParams只接受-1或非负整数");
        var ids = new HashSet<String>();
        for(var p:r.players()) {
            require(p!=null && p.id()!=null && p.id().matches("[A-Za-z0-9_-]{1,32}"),"玩家ID格式错误");
            require(ids.add(p.id()),"玩家ID必须唯一");
            require(p.name()!=null && !p.name().isBlank() && p.name().length()<=40,"玩家名称需为1至40字符");
            require(p.characterId()==0 || rules.characters.stream().anyMatch(c -> ((Number)c.get("id")).intValue()==p.characterId()),"未知角色ID");
            require(p.maxHp()>=1 && p.maxHp()<=10000 && p.hp()>=1 && p.hp()<=p.maxHp(),"生命必须在1至生命上限之间，上限不超过10000");
            require(p.anima()>=0 && p.anima()<=10000 && p.defense()>=0 && p.defense()<=10000 && p.swordIntent()>=0 && p.swordIntent()<=10000,"灵气、防御和剑意范围为0至10000");
            if(p.hand()!=null){require(p.hand().size()<=200,"手牌最多200张");for(var handId:p.hand())require(handId!=null&&rules.byId.containsKey(handId),"未知手牌ID："+handId);}
            var selectedTalents=new HashSet<Integer>();var embryoChoices=new HashSet<Integer>();
            for(var id:p.talentIds()==null?List.<Integer>of():p.talentIds()) {
                if (id != null && rules.outsideBattleTalentIds.contains(id)) continue;
                if(id==null || !rules.talentById.containsKey(id)) throw new Rejected("UNSUPPORTED","尚未支持仙命："+id);
                require(selectedTalents.add(id),"仙命不能重复选择");if(id%10000>=93&&id%10000<=96)require(embryoChoices.add(id%10000),"同一阶段只能选择一种澄心锻造");
                var talent=rules.talentById.get(id);
                require(talent.charId()==0 || p.characterId()==0 || talent.charId()==p.characterId(),"仙命与角色不匹配："+talent.name());
                require(p.characterId()==0||talent.sect()==0||rules.characters.stream().anyMatch(c->((Number)c.get("id")).intValue()==p.characterId()&&((Number)c.get("sect")).intValue()==talent.sect()),"仙命与角色门派不匹配："+talent.name());
                if(id==220)require(p.hand()!=null,"兴云布雨需要明确提供手牌；空手牌请传[]");
                require(!"POST_OPENING".equals(r.initializationStage()) || !talent.openingOnly(),"开场型仙命请使用开场前模式；开场后快照应将其收益直接计入属性");
            }
            if(p.buffs()!=null) for(var entry:p.buffs().entrySet()) {
                if(!Rules.STATUS_NAMES.containsKey(entry.getKey()) && !Set.of("cloudChain","ignoreDefense").contains(entry.getKey()) && !PlantRefinements.isInput(entry.getKey()) && !entry.getKey().equals("speed") && !ContextEffects.BOTTLE_SLOTS.contains(entry.getKey())) throw new Rejected("UNSUPPORTED","尚未支持状态："+entry.getKey());
                if (ContextEffects.BOTTLE_SLOTS.contains(entry.getKey()))
                    require(entry.getValue()!=null && rules.byId.containsKey(entry.getValue()), "未知玉瓶卡牌ID：" + entry.getValue());
                else require(entry.getValue()!=null && entry.getValue()>=0 && entry.getValue()<=10000,"状态层数范围为0至10000");
                require(!Set.of("usedSlotMask","starMask").contains(entry.getKey())||entry.getValue()<=255,"usedSlotMask范围0至255");
                require(!entry.getKey().equals("reverseDirection")||entry.getValue()<=1,"reverseDirection只接受0或1");
                require(!entry.getKey().equals("realm")||entry.getValue()<=6,"realm范围0至6");
                require(!entry.getKey().equals("stance")||entry.getValue()<=2,"stance范围0至2");
                require(!entry.getKey().equals("career")||entry.getValue()<=7,"career范围0至7");
                require(!entry.getKey().equals("lastElement")||entry.getValue()<=5,"lastElement只接受0至5");
            }
            require(!"PRE_OPENING".equals(r.initializationStage()) || p.buffs()==null || p.buffs().entrySet().stream().allMatch(e->Set.of("realm","destiny","career","bottleFirst","bottleSecond","stance","embryoGrowth","experience","starMask","reverseDirection","physique","physiqueCap","forceCap","speed","deathBattleWill").contains(e.getKey())||PlantRefinements.isInput(e.getKey())||e.getValue()==0),"开场前模式不接受已有战斗状态，请改用开场后快照");
            require(p.cards()!=null && p.cards().size()>=3 && p.cards().size()<=8,"卡组需要3至8张卡牌");
            SeasonTalentEffects.validate(rules, p, r.initializationStage());
            CardOutcomeInputs.validate(p);
            for(var id:p.cards()) {
                require(id!=null && rules.byId.containsKey(id),"卡牌ID不存在："+id);
                var card=rules.byId.get(id);
                if(!card.supported()) throw new Rejected("UNSUPPORTED","尚未支持卡牌："+card.name()+"（"+id+"）");
            }
            validateDeckLimit(p, p.cards());
        }
        require("AUTO".equals(r.firstActorId()) || ids.contains(r.firstActorId()),"必须指定自动判定或有效的先手玩家");
    }
    void validateDeckLimit(Player player, List<Integer> cards) {
        int limit = player.singleUseCardLimit() == null ? automaticSingleUseCardLimit(player) : player.singleUseCardLimit();
        require(limit >= 0 && limit <= 8, "持续与消耗牌上限需为0至8");
        long count = cards.stream().map(rules.byId::get).filter(Rules.Card::countsTowardSingleUseLimit).count();
        require(count <= limit, player.name() + "的持续与消耗牌合计为" + count + "张，上限为" + limit + "张");
    }
    private int automaticSingleUseCardLimit(Player player) {
        boolean extraSlot = SeasonTalentEffects.choices(player).stream()
                .anyMatch(choice -> choice != null && choice.id() == 2015 && !Boolean.TRUE.equals(choice.disabled()));
        return 2 + (extraSlot ? rules.seasonById.get(2015).param(1) : 0);
    }
    static final class Resolution {
        long[] previewDamage;
        BooleanSupplier stopRequested;
        boolean randomUsed;
        List<Event> log;Fighter active;Fighter[] fighters;int turn,parameterIndex,talentDepth,cardExecutions,attackExecutions;boolean trace=true;
        final java.util.Random random=new java.util.Random();
        final ArrayDeque<Integer> parameters=new ArrayDeque<>();
        final ArrayDeque<Runnable> pending=new ArrayDeque<>();boolean draining;
        void checkStop() {
            if (stopRequested != null && stopRequested.getAsBoolean()) throw new CancellationException();
        }
        void enqueue(Runnable effect){pending.add(effect);if(draining)return;draining=true;try{int resolved=0;while(!pending.isEmpty()){if(++resolved>4096)throw new Rejected("RESOLUTION_LIMIT","持续效果队列超过4096次，无法有限结算该组合");pending.remove().run();}}finally{draining=false;}}
    }
    static final class Fighter {
        final Set<Integer> copyingCareer=new HashSet<>();final boolean[] skipSlots=new boolean[8];boolean careerIgnore;StarEffects.Frame starFrame;ForgeEffects.Frame forgeFrame;int jumpSlot=-1;Fighter opponent;Resolution resolution;Rules rules;Integer activeCard;boolean capClamping;final Player p;final List<Integer> hand;Integer destiny; final int[] cards; final boolean[] consumed;final boolean[] used=new boolean[8]; final Map<String,Integer> statuses=new HashMap<>(); int hp,maxHp,anima,def,sword,slot,cloud,ignore,woundDamage,specialAttack,woundedHits,pendingAttack,mirrorDepth,chainSearch=-1,turnAttacks,chasesThisTurn;boolean embryoCloud,embryoRaging;boolean ignoreGuard,afterCard;boolean requestChase,refundSword,currentUniversal;
        final Map<Integer,SeasonChoice> season = new TreeMap<>();
        final Set<Integer> seasonUsed = new HashSet<>();
        final Map<Integer,Integer> seasonUses = new HashMap<>();
        final Map<Integer,Integer> seasonTurns = new HashMap<>();
        final CardOutcomeInputs cardOutcomeInputs;
        int horseOutcomeSlot = -1;
        Integer horseOutcomeCard;
        Fighter(Player p) {this.p=p;cardOutcomeInputs=new CardOutcomeInputs(p);hand=p.hand()==null?null:new ArrayList<>(p.hand());destiny=p.buffs()!=null&&p.buffs().containsKey("destiny")?p.buffs().get("destiny"):null;hp=p.hp();maxHp=p.maxHp();anima=p.anima();def=p.defense();sword=p.swordIntent();cloud=p.buffs()==null?0:p.buffs().getOrDefault("cloudChain",0);ignore=p.buffs()==null?0:p.buffs().getOrDefault("ignoreDefense",0);cards=p.cards().stream().mapToInt(Integer::intValue).toArray();consumed=new boolean[cards.length];for(int i=0;i<8;i++)used[i]=(status("usedSlotMask")&(1<<i))!=0;statuses.put("previousTurnHp",p.buffs()==null?p.hp():p.buffs().getOrDefault("previousTurnHp",p.hp()));statuses.put("turnStartHp",p.hp());}
        boolean practiceTarget;
        boolean hasBase(int base){return !practiceTarget&&Arrays.stream(cards).anyMatch(id->id-id/10000%100*10000==base);}
        boolean isCloud(Rules.Card c){return embryoCloud||(c.baseId()==19&&TalentEffects.has(this,20096))||c.name().contains("云剑")||(status("cloudRagingBridge")>0&&c.name().contains("狂剑"))||c.baseId()==261||currentUniversal||(status("cloudArrayAlias")>0&&c.career()==5);}
        boolean isRaging(Rules.Card c){return (SeasonTalentEffects.has(this,7049)&&c.career()==3)
                ||(SeasonTalentEffects.has(this,7068)&&SeasonTalentEffects.contains(c,"击伤"))||embryoRaging||(c.baseId()==19&&TalentEffects.has(this,10096))||c.baseId()==331||c.baseId()==401||(status("highRaging")>0&&c.rarity()>=1)||c.name().contains("狂剑")||(status("cloudRagingBridge")>0&&(c.name().contains("云剑")||currentUniversal))||c.baseId()==261||currentUniversal;}
        boolean isFormation(Rules.Card c){return c.name().contains("剑阵")||(hasBase(312)&&isRaging(c)&&!c.hidden());}
        boolean isSword(Rules.Card c){return c.name().contains("剑")||isCloud(c)||isRaging(c)||isFormation(c);}
        boolean metalActive(){return ElementalEffects.active(this,1);}
        boolean waterActive(){return ElementalEffects.active(this,2);}
        boolean woodActive(){return ElementalEffects.active(this,3);}
        boolean fireActive(){return ElementalEffects.active(this,4);}
        boolean earthActive(){return ElementalEffects.active(this,5);}
        boolean alwaysCloud(){return status("dragonHorse")>0||SeasonTalentEffects.has(this,11001)||(p.talentIds()!=null&&p.talentIds().contains(14));}
        int status(String key){return statuses.getOrDefault(key,p.buffs()==null?0:p.buffs().getOrDefault(key,0));}
        State state(){var skipped=new ArrayList<Integer>();for(int i=0;i<consumed.length;i++)if(consumed[i])skipped.add(i);var effects=new TreeMap<String,Integer>();for(String key:Rules.STATUS_NAMES.keySet())effects.put(key,status(key));return new State(p.id(),p.name(),hp,maxHp,anima,def,sword,slot,cloud,ignore,List.copyOf(skipped),Arrays.stream(cards).boxed().toList(),Map.copyOf(effects),hand==null?null:List.copyOf(hand),destiny);}
    }
    private static List<State> states(Fighter[] f){return Arrays.stream(f).map(Fighter::state).toList();}
    static void change(List<Change> changes, Fighter f,String key,int before,int after){
        if (f.practiceTarget && key.equals("hp") && after < before) {
            f.resolution.previewDamage[(f.resolution.turn + 1) / 2] += (long) before - after;
        }
        ForgeEffects.resourceChanged(changes,f,key,before,after);TalentEffects.resources(changes,f,key,before,after);AdditionalMechanics.resources(changes,f,key,before,after);FoodEffects.gained(changes,f,key,before,after);
        if(before!=after) changes.add(new Change(f.p.id(),key,before,after));
        if(key.equals("defense")&&after<before){
            int lost=before-after;addStatus(changes,f,"defenseLost",lost);
            if(f.resolution!=null)f.resolution.enqueue(()->{
                if(f.status("cliff")>0){addStatus(changes,f,"cliff",-1);damage(changes,f.opponent,lost);}
                if(f.status("restoreDefense")>0){addStatus(changes,f,"restoreDefense",-1);addDefense(changes,f,lost);}
            });
        }
        if(key.equals("hp")&&after<before&&!f.capClamping){if(f.status("snowLotus")>0){addStatus(changes,f,"snowLotus",-1);addDefense(changes,f,before-after);}if(f.status("lossAttackFormation")>0){addStatus(changes,f,"lossAttackFormation",-1);addStatus(changes,f,"attackBonus",1);}if(f.status("ominousLoss")>0){addStatus(changes,f,"ominousLoss",-1);addStatus(changes,f,"externalInjury",1);}}
        if(key.equals("defense")&&after>before&&f.status("reverseRounds")>0&&f.resolution!=null){int reflected=(after-before)/2;f.resolution.enqueue(()->damage(changes,f.opponent,reflected));}
        if(key.equals("anima")&&after>before){int gain=after-before;heal(changes,f,gain*f.status("animaHeal"));heal(changes,f,gain*f.status("resourceHeal"));addDefense(changes,f,gain*f.status("resourceDefense"));addStatus(changes,f,"hexagram",gain*f.status("animaToHexagram"));if(f.status("animaToStarLimit")>0){int amount=Math.min(gain,f.status("animaToStarLimit"));addStatus(changes,f,"animaToStarLimit",-amount);addStatus(changes,f,"starPower",amount);}}
        if(key.equals("cloudChain")&&after>before)addStatus(changes,f,"cloudTotal",after-before);
        if(key.equals("anima")&&after>before&&f.status("gainChase")>0){addStatus(changes,f,"gainChase",-1);f.requestChase=true;}
        if(key.equals("anima")&&after>before)addStatus(changes,f,"animaGained",after-before);
        if(key.equals("defense")&&after>before)addStatus(changes,f,"defenseGained",after-before);
        if(key.equals("swordIntent")&&after>before)addDefense(changes,f,(after-before)*f.status("swordDefenseGain"));
        if(key.equals("hp")&&after<before&&!f.capClamping){addStatus(changes,f,"hpLost",before-after);if(f.status("lossToNextDefense")>0)addStatus(changes,f,"nextDefense",(before-after+f.rules.byId.get(7040086).param(1)-1)/f.rules.byId.get(7040086).param(1));}
        if(key.equals("defense")&&after<before&&f.status("lossToNextDefense")>0)addStatus(changes,f,"nextDefense",(before-after)/f.rules.byId.get(7040086).param(0));
        if(key.equals("anima")&&after!=before)addDefense(changes,f,Math.abs(after-before)*f.status(after>before?"animaDefenseGain":"animaDefenseLoss"));
    }
    static void emit(List<Event> log,Fighter[] f,int turn,Fighter actor,Fighter target,Integer card,String type,String message,List<Change> changes){
        if(actor.resolution!=null&&!actor.resolution.trace&&!type.equals("BATTLE_END"))return;
        log.add(new Event(log.size()+1,turn,actor.p.id(),target==null?null:target.p.id(),card,type,message,List.copyOf(changes),states(f)));
    }
    public Result simulate(Request request) {return simulate(request,true);}
    Result simulateForValidation(Request request){return simulate(request,false);}
    private Result simulate(Request request,boolean trace) {
        return simulate(request, trace, null);
    }

    record DamageRound(int round, long damage, long cumulative) {}
    record DamagePreview(long openingDamage, List<DamageRound> rounds, boolean selfDefeated) {}

    DamagePreview previewDamage(String rulesetId, Player own) {
        String targetId = "practice".equals(own.id()) ? "practice_target" : "practice";
        var target = new Player(targetId, "木桩", 0, TARGET_HP, TARGET_HP, 0, 0, 0,
                Collections.nCopies(8, 0), List.of(), Map.of(), List.of());
        var request = new Request(rulesetId, "STANDARD", "PRE_OPENING", own.id(), List.of(own, target));
        long[] damage = new long[PREVIEW_ROUNDS + 1];
        Result result = simulate(request, false, damage);
        var rounds = new ArrayList<DamageRound>();
        long cumulative = damage[0];
        for (int round = 1; round <= (result.turns() + 1) / 2; round++) {
            cumulative += damage[round];
            rounds.add(new DamageRound(round, damage[round], cumulative));
        }
        return new DamagePreview(damage[0], List.copyOf(rounds), result.finalStates().getFirst().hp() <= 0);
    }

    private Result simulate(Request request, boolean trace, long[] previewDamage) {
        validate(request);
        var resolution=new Resolution();resolution.trace=trace;
        resolution.previewDamage=previewDamage;
        if (previewDamage != null) resolution.random.setSeed(0L);
        var end = resolveBattle(request, resolution);
        var f = resolution.fighters;
        var log = resolution.log;
        var winner = end.winner();
        int turn = resolution.turn;
        String reason = end.reason();
        for(var fighter:f)for(int id:fighter.cards){var card=rules.byId.get(id);if(card.effect().equals("ELEMENTAL_FIELD"))emit(log,f,turn,fighter,null,id,"POST_BATTLE_PLAN","五行灵田：战斗后修为-"+card.param(0)+"并抽"+card.param(0)+"张1级五行牌；抽牌结果通过战斗外接口应用",List.of());}
        emit(log,f,turn,winner,null,null,"BATTLE_END",winner.p.name()+"获胜；"+(reason.equals("DEATH")?"生命耗尽结算":"达到64次行动上限，按剩余生命结算")+(f[0].hp==f[1].hp?"；同血时先手胜":""),List.of());
        return new Result("COMPLETED",winner.p.id(),reason,Rules.ID,turn,states(f),List.copyOf(log));
    }

    record DuelSample(boolean won, int actions, int ownHp, int enemyHp, boolean randomUsed) {}
    private record BattleEnd(Fighter winner, String reason) {}

    // The search validates the input once and constructs each candidate from the same inventory.
    DuelSample sampleBattle(Request request, long seed, BooleanSupplier stopRequested) {
        var resolution = new Resolution();
        resolution.trace = false;
        resolution.stopRequested = stopRequested;
        resolution.random.setSeed(seed);
        var end = resolveBattle(request, resolution);
        var own = resolution.fighters[0];
        var enemy = resolution.fighters[1];
        return new DuelSample(end.winner() == own, resolution.turn, own.hp, enemy.hp, resolution.randomUsed);
    }

    private BattleEnd resolveBattle(Request request, Resolution resolution) {
        resolution.checkStop();
        Fighter[] f=request.players().stream().map(Fighter::new).toArray(Fighter[]::new);
        if (resolution.previewDamage != null) f[1].practiceTarget = true;
        if(request.battleParams()!=null)resolution.parameters.addAll(request.battleParams());for(int i=0;i<2;i++){f[i].opponent=f[1-i];f[i].resolution=resolution;f[i].rules=rules;ForgeEffects.initialize(f[i]);SeasonTalentEffects.configure(f[i]);}
        int scoreA=Initiative.score(f[0]), scoreB=Initiative.score(f[1]);
        boolean scoresKnown=Initiative.known(f[0].p) && Initiative.known(f[1].p);
        boolean tied=scoresKnown && scoreA==scoreB;
        boolean automatic="AUTO".equals(request.firstActorId());
        int first;
        if (automatic && (!scoresKnown || tied)) {
            resolution.randomUsed=true;
            first=resolution.random.nextInt(2);
        } else if (automatic) first=scoreA>scoreB?0:1;
        else first=f[0].p.id().equals(request.firstActorId())?0:1;
        var log=new ArrayList<Event>();resolution.log=log;resolution.fighters=f;
        emit(log,f,0,f[first],null,null,"BATTLE_START",request.initializationStage().equals("PRE_OPENING")?"开场前状态已载入，结算已选择且支持的仙命":"开场后快照已载入，不重复执行开场收益",List.of());
        if (automatic) emit(log,f,0,f[first],null,null,"INITIATIVE",
                scoresKnown ? "修为＋速度：" + scoreA + " / " + scoreB + (tied ? "，同速随机先手" : "")
                        : "修为未填全，随机决定先手",List.of());
        if (tied && request.initializationStage().equals("PRE_OPENING") && f[1-first].status("deathBattleWill")==0) {
            var changes = new ArrayList<Change>();
            addStatus(changes,f[1-first],"deathBattleWill",1);
            emit(log,f,0,f[1-first],null,null,"DEATH_BATTLE_GRANT","同速后手获得死战之志",changes);
        }
        if (request.initializationStage().equals("PRE_OPENING") && (SeasonTalentEffects.has(f[0], 1009) || SeasonTalentEffects.has(f[1], 1009))) {
            int slots = rules.seasonById.get(1009).param(0);
            for (var fighter : f) SeasonTalentEffects.upgradeRear(fighter, slots);
            emit(log, f, 0, f[first], null, null, "SEASON_TALENT", rules.seasonById.get(1009).name(), List.of());
        }
        if(request.initializationStage().equals("PRE_OPENING")) for(int i=0;i<2;i++) {
            var src=f[(first+i)%2];
            if (SeasonTalentEffects.has(src, 27023)) {
                var changes = new ArrayList<Change>();
                setStatus(changes, src, "stance", 1);
                emit(log, f, 0, src, null, null, "SEASON_TALENT", rules.seasonById.get(27023).name(), changes);
            }
            if (SeasonTalentEffects.has(src, 7049)) {
                var changes = new ArrayList<Change>();
                setStatus(changes, src, "career", 3);
                emit(log, f, 0, src, null, null, "SEASON_TALENT", rules.seasonById.get(7049).name(), changes);
            }
            for(var id:src.p.talentIds()==null?List.<Integer>of():src.p.talentIds()) {
                var talent=rules.talentById.get(id);
                if (talent == null) continue;
                var changes=new ArrayList<Change>();
                TalentEffects.opening(changes,src,talent);
                emit(log,f,0,src,null,null,"TALENT",talent.name()+"生效："+talent.description(),changes);
            }
            for (var plant : PlantRefinements.values()) {
                int amount = src.status(plant.key);
                if (amount <= 0) continue;
                var changes = new ArrayList<Change>();
                plant.apply(changes, src, amount);
                String detail = plant == PlantRefinements.SPEED ? "（速度已由指定先手体现）" : "";
                emit(log, f, 0, src, null, plant.cardId, "PLANT_REFINEMENT",
                        rules.byId.get(plant.cardId).name() + "炼化生效" + detail, changes);
            }
            for (int id : SeasonTalentEffects.OPENING_ORDER) {
                if (!src.season.containsKey(id)) continue;
                var changes = new ArrayList<Change>();
                int[] originalCards = src.cards.clone();
                SeasonTalentEffects.opening(changes, src, id);
                if (!changes.isEmpty() || !Arrays.equals(originalCards, src.cards)) emit(log, f, 0, src, null, null, "SEASON_TALENT", rules.seasonById.get(id).name(), changes);
            }
            if (i == 1 && SeasonTalentEffects.has(src, 7110)) {
                var changes = new ArrayList<Change>();
                addStatus(changes, src, "nextPosterior", 1);
                emit(log, f, 0, src, null, null, "SEASON_TALENT", rules.seasonById.get(7110).name(), changes);
            }
            for(int slot=0;slot<src.cards.length;slot++){var c=rules.byId.get(src.cards[slot]);if(c.effect().equals("DREAM_RAGING_ZERO")&&c.level()>=4)src.statuses.merge("ragingUpgrades",1,Integer::sum);var opening=new ArrayList<Change>();triggerOpening(opening,src,c,slot);if(!opening.isEmpty())emit(log,f,0,src,null,c.id(),"CARD_OPENING","结算卡牌开局效果",opening);}
        }
        for(var fighter:f){ForgeEffects.initialize(fighter);CareerEffects.passive(fighter);var changes=new ArrayList<Change>();StarEffects.initialize(changes,fighter);if(!changes.isEmpty())emit(log,f,0,fighter,null,null,"STAR_PASSIVE","相邻梦斗转星移建立星位",changes);}if(f[1-first].status("deathBattleWillIfSecond")>0)f[1-first].statuses.put("deathBattleWill",1);
        int turn=0;
        int turnLimit = resolution.previewDamage == null ? 64 : PREVIEW_ROUNDS * 2;
        for(;turn<turnLimit;) {
            resolution.checkStop();
            turn++;resolution.turn=turn;
            Fighter src=f[(first+turn-1)%2],dst=f[(first+turn)%2];
            resolution.active=src;src.requestChase=false;src.turnAttacks=0;src.chasesThisTurn=0;
            var changes=new ArrayList<Change>();
            StarEffects.start(changes,src);ForgeEffects.start(changes,src);TalentEffects.start(changes,src);ExtraCardEffects.start(changes,src);AdditionalMechanics.start(changes,src);
            setStatus(changes,src,"lossToNextDefense",0);
            setStatus(changes,src,"turnStartHp",src.hp);
            setStatus(changes,src,"beastTriggered",0);
            int old=src.def;if(src.status("moonRounds")==0)src.def=src.status("turtleDefense")>0?src.def-(src.def+4)/5:src.def/2;else addStatus(changes,src,"moonRounds",-1);change(changes,src,"defense",old,src.def);if(src.cards.length>0&&Arrays.stream(src.cards).mapToObj(rules.byId::get).anyMatch(c->c.baseId()==10000083&&c.level()>=4))heal(changes,src,old-src.def);
            SeasonTalentEffects.start(changes,src);setStatus(changes,src,"parry",0);CreatureEffects.start(changes,src);ContextEffects.start(changes,src);
            for(String key:List.of("iron","waterHide"))if(src.status(key)>0)addStatus(changes,src,key,-1);
            if(src.status("doubleWaterPending")>0){int force=src.status("waterForce")*src.status("doubleWaterPending"),anima=src.anima*src.status("doubleWaterPending");addStatus(changes,src,"temporaryWater",force);addStatus(changes,src,"temporaryAnima",anima);addStatus(changes,src,"waterForce",force);addAnima(changes,src,anima);setStatus(changes,src,"doubleWaterPending",0);}
            if(src.status("defensePerTurn")>0)addDefense(changes,src,src.status("defensePerTurn"));
            if(src.status("swordPerTurn")>0)addSword(changes,src,src.status("swordPerTurn"));
            if(src.status("halfAnima")>0){int half=src.status("halfAnima")+src.status("halfAnimaRemainder");addAnima(changes,src,half/2);setStatus(changes,src,"halfAnimaRemainder",half%2);}
            if(src.status("nextDefense")>0){addDefense(changes,src,src.status("nextDefense"));setStatus(changes,src,"nextDefense",0);}
            CareerEffects.start(changes,src);
            emit(log,f,turn,src,null,null,"TURN_START","行动开始；自身防御衰减",changes);
            if(src.status("injuryExtra")>0){changes=new ArrayList<>();ForgeEffects.extraInjury(changes,src);emit(log,f,turn,src,null,null,"EXTRA_INTERNAL_INJURY","额外内伤结算",changes);if(isDead(src))break;}
            if(src.status("internalInjury")>0){changes=new ArrayList<>();int loss=src.status("internalInjury")*(1+src.status("seasonDoubleRecovery"));if(src.status("guard")>0){setStatus(changes,src,"guard",src.status("guard")-1);loss=0;}loss=careerLifeLoss(changes,src,loss);old=src.hp;src.hp-=loss;change(changes,src,"hp",old,src.hp);emit(log,f,turn,src,null,null,"INTERNAL_INJURY","行动开始结算内伤，扣血"+loss,changes);if(isDead(src))break;}
            int additionalActions=0;
            boolean stunned=src.status("skipTurns")>0;
            if(stunned){changes=new ArrayList<>();addStatus(changes,src,"skipTurns",-1);emit(log,f,turn,src,null,null,"ACTION_SKIPPED","无法行动，跳过出牌",changes);}
            while(!stunned && !src.practiceTarget) {
                var card=selectCard(log,f,turn,src);
                boolean conditionalChase=("METAL_SHUTTLE".equals(card.effect())&&src.metalActive()) || ("METAL_WAVE".equals(card.effect())&&(src.metalActive()||src.waterActive())) || ("WOOD_WILLOW".equals(card.effect())&&src.woodActive()) || ElementalEffects.chase(src,card) || CloudEffects.chase(src,card);
                boolean played=playCard(log,f,turn,src,dst,card);
                int agilityCost = SeasonTalentEffects.has(src,7065) ? SeasonTalentEffects.param(src,7065,0) : 10;
                boolean chase=card.actionAgain() || conditionalChase || src.requestChase || src.status("agility")>=agilityCost || src.status("allChase")>0||src.status("rabbitChaseCost")>0||(card.baseId()==0&&src.status("basicChase")>0);
                if(!played || deaths(src,dst) || !chase) break;
                changes=new ArrayList<>();
                if(CareerEffects.blocksChase(changes,src)){emit(log,f,turn,src,null,card.id(),"CHASE_BLOCKED","再次行动被困缚或琴曲阻止",changes);break;}
                if(src.opponent.status("lockFormation")>0){addStatus(changes,src.opponent,"lockFormation",-1);damage(changes,src,8);emit(log,f,turn,src.opponent,src,card.id(),"LOCK_FORMATION","八门金锁阵在追击前造成伤害",changes);if(isDead(src))break;}
                if(src.opponent.status("vineDrain")>0){changes=new ArrayList<>();loseHp(changes,src,src.opponent.status("vineDrain"));heal(changes,src.opponent,src.opponent.status("vineDrain"));emit(log,f,turn,src.opponent,src,card.id(),"VINE_DRAIN","噬仙古藤在对方追击前吸血",changes);if(isDead(src))break;}
                if(additionalActions>=AdditionalMechanics.chaseLimit(src)) {
                    emit(log,f,turn,src,null,card.id(),"CHASE_LIMIT","已达到本次行动追加"+AdditionalMechanics.chaseLimit(src)+"次的上限",List.of());
                    break;
                }
                changes=new ArrayList<>();if(src.status("rabbitChaseCost")>0){loseHp(changes,src,src.status("rabbitChaseCost"));emit(log,f,turn,src,null,card.id(),"CHASE_COST","影枭兔再次行动前失血",changes);if(deaths(src,dst))break;changes=new ArrayList<>();}if(!card.actionAgain()&&!conditionalChase&&!src.requestChase&&src.status("allChase")==0&&src.status("rabbitChaseCost")==0&&!(card.baseId()==0&&src.status("basicChase")>0))addStatus(changes,src,"agility",-agilityCost);
                addStatus(changes,src,"chaseCount",1);
                if (SeasonTalentEffects.has(src, 7112)) heal(changes, src, SeasonTalentEffects.param(src, 7112, 1));
                if(src.status("chaseSharpness")>0)addStatus(changes,src,"sharpness",src.status("chaseSharpness"));
                if(src.status("chaseFireDefense")>0){reduceLifeAndCap(changes,dst,src.status("chaseFireDefense"));addDefense(changes,src,src.status("chaseFireDefense"));}
                if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"CHASE_STATE","结算身法与再次行动记录",changes);
                additionalActions++;src.chasesThisTurn++;
                emit(log,f,turn,src,null,card.id(),"CHASE","再次行动：继续下一张牌，不重复结算行动开始效果",List.of());
                src.requestChase=false;
            }
            if(deaths(src,dst)) break;
            if(src.status("waterForce")>0){changes=new ArrayList<>();damage(changes,dst,src.status("waterForce"));if(src.status("waterTwiceRounds")>0)damage(changes,dst,src.status("waterForce"));emit(log,f,turn,src,dst,null,"WATER_FORCE","水势造成普通伤害",changes);}
            changes=new ArrayList<>();
            if(src.status("waterDefenseLimit")>0&&src.status("waterForce")>0)addDefense(changes,src,src.status("waterDefenseUnlimited")>0?src.status("waterForce"):Math.min(src.status("waterDefenseLimit"),src.status("waterForce")));
            if(src.status("temporaryWater")>0||src.status("temporaryAnima")>0){addStatus(changes,src,"waterForce",-src.status("temporaryWater"));addAnima(changes,src,-src.status("temporaryAnima"));setStatus(changes,src,"temporaryWater",0);setStatus(changes,src,"temporaryAnima",0);}
            if(src.status("ragingPerTurn")>0)addStatus(changes,src,"ragingCount",src.status("ragingPerTurn"));
            for(String key:List.of("weakness","vulnerability","noHealing","springRounds","waterTwiceRounds","healToSharpRounds","sharpToFireRounds"))if(src.status(key)>0)setStatus(changes,src,key,src.status(key)-1);
            addStatus(changes,src,"repel",src.status("raccoonRepel"));TalentEffects.end(changes,src);ForgeEffects.end(changes,src);CareerEffects.end(changes,src);SeasonTalentEffects.end(changes,src);StarEffects.end(changes,src);AdditionalMechanics.end(changes,src);if(src.status("deathBattleActive")>0)setStatus(changes,src,"deathBattleActive",0);
            setStatus(changes,src,"previousTurnHp",src.status("turnStartHp"));
            emit(log,f,turn,src,null,null,"TURN_END","行动结束；虚弱与破绽各衰减1层",changes);
            if(deaths(src,dst))break;
        }
        int delta=Integer.compare(f[0].hp,f[1].hp);
        Fighter winner=delta==0?f[first]:f[delta>0?0:1];
        String reason=f[0].hp<=0||f[1].hp<=0?"DEATH":"TURN_LIMIT";
        return new BattleEnd(winner, reason);
    }
    static boolean triggerOpening(List<Change> ch,Fighter f,Rules.Card c,int slot){
        if(!CareerEffects.openingCard(c))return false;
        switch(c.baseId()){
            case 372->{for(int i=1;i<=5;i++)activate(ch,f,ElementalEffects.activationKey(i));}
            case 389->{StarEffects.addStar(ch,f,StarEffects.previous(f,slot));StarEffects.addStar(ch,f,StarEffects.next(f,slot));}
            case 446->addStatus(ch,f.opponent,"weakness",c.param(0));
            case 447->{ElementalEffects.activateCard(ch,f,f.rules.byId.get(f.cards[StarEffects.previous(f,slot)]));ElementalEffects.activateCard(ch,f,f.rules.byId.get(f.cards[StarEffects.next(f,slot)]));}
            case 55->{activate(ch,f,"waterActivation");activate(ch,f,"woodActivation");}
            case 56->{activate(ch,f,"fireActivation");activate(ch,f,"earthActivation");}
            case 57->{activate(ch,f,"earthActivation");activate(ch,f,"metalActivation");}
            case 58->{activate(ch,f,"metalActivation");activate(ch,f,"waterActivation");}
            case 315->{activate(ch,f,"fireActivation");activate(ch,f,"metalActivation");}
            case 203->{addAnima(ch,f,c.param(1));addStatus(ch,f,"waterForce",c.param(2));}
            case 7000079->addAnima(ch,f,c.param(1));
            case 1000086->addStatus(ch,f,"cloudSea",c.param(1));
            case 1000101->{if(f.anima==0)addAnima(ch,f,c.param(0));}
            default->{ }
        }
        CareerEffects.opening(ch,f,c,slot);return true;
    }
    private Rules.Card selectCard(List<Event> log,Fighter[] f,int turn,Fighter src) {
        int skipped=0;
        while(skipped<src.cards.length&&src.status("skipCostCards")>0&&!src.consumed[src.slot]&&rules.byId.get(src.cards[src.slot]).hpCost()>0){var changes=new ArrayList<Change>();var c=rules.byId.get(src.cards[src.slot]);ForgeEffects.cost(changes,src,c);damage(changes,src.opponent,c.hpCost()*src.status("skipCostDamage"));addStatus(changes,src,"skipCostCards",-1);src.slot=StarEffects.next(src,src.slot);skipped++;emit(log,f,turn,src,null,c.id(),"FORGE_SKIP","瞬影击跳过耗生命牌并结算费用",changes);}
        while(skipped<src.cards.length&&src.status("skipUpcoming")>0){var changes=new ArrayList<Change>();int id=src.cards[src.slot];addStatus(changes,src,"skipUpcoming",-1);src.slot=StarEffects.next(src,src.slot);skipped++;emit(log,f,turn,src,null,id,"STAR_SKIP","星弈断跳过下一槽",changes);}
        for(int i=0;i<src.cards.length&&src.status("reincarnation")>0&&(src.slot+1==src.status("reincarnationSlot1")||src.slot+1==src.status("reincarnationSlot2"));i++){var changes=new ArrayList<Change>();var c=rules.byId.get(src.cards[src.slot]);triggerOpening(changes,src,c,src.slot);addStatus(changes,src,"reincarnation",-1);src.slot=StarEffects.next(src,src.slot);emit(log,f,turn,src,null,c.id(),"OPENING_REPLAY","命运轮回跳过并触发开局",changes);}

        while(skipped<src.cards.length&&(src.skipSlots[src.slot]||((src.slot>=6)&&!src.used[src.slot]&&Set.of(350,9000015).contains(rules.byId.get(src.cards[src.slot]).baseId())))){int slot=src.slot;src.skipSlots[slot]=false;src.used[slot]=true;src.slot=StarEffects.next(src,slot);skipped++;emit(log,f,turn,src,null,src.cards[slot],"CARD_SKIP","灵田首次使用或开局效果跳过槽位",List.of());}
        while((src.consumed[src.slot]||(src.slot==7&&src.cards[src.slot]-src.cards[src.slot]/10000%100*10000==202)) && skipped<src.cards.length) {
            int slot=src.slot,cardId=src.cards[slot];src.consumed[slot]=true;src.slot=StarEffects.next(src,slot);skipped++;
            emit(log,f,turn,src,null,cardId,"CARD_SKIP","跳过已消耗的第"+(slot+1)+"槽",List.of());
        }
        if(src.consumed[src.slot]) {
            src.cards[src.slot]=0;src.consumed[src.slot]=false;
            emit(log,f,turn,src,null,0,"FALLBACK_ATTACK","全部卡牌已消耗，第"+(src.slot+1)+"槽转为普通攻击",List.of());
        }
        for(int i=0;i<8&&(src.hasBase(99000214)||src.opponent.hasBase(99000214))&&src.slot<2&&rules.byId.get(src.cards[src.slot]).cardType()==3;i++){int id=src.cards[src.slot];src.slot=StarEffects.next(src,src.slot);emit(log,f,turn,src,null,id,"CARD_SKIP","无名白鹿跳过前两格持续牌",List.of());}
        if(rules.byId.get(src.cards[src.slot]).baseId()==322&&!src.opponent.practiceTarget){int target=src.opponent.cards[src.slot];src.cards[src.slot]=target;emit(log,f,turn,src,src.opponent,target,"CARD_TRANSFORM","逍遥复刻永久替换为对方同格牌",List.of());}
        if(src.status("dogTransform")>0){var ch=new ArrayList<Change>();addStatus(ch,src,"dogTransform",-1);src.cards[src.slot]=0;emit(log,f,turn,src,null,0,"CARD_TRANSFORM","幽冥虚魂犬将本槽永久替换为普通攻击",ch);}
        return rules.byId.get(src.cards[src.slot]);
    }
    private boolean playCard(List<Event> log,Fighter[] f,int turn,Fighter src,Fighter dst,Rules.Card card) {
        return playCard(log,f,turn,src,dst,card,false,0);
    }
    private boolean playCard(List<Event> log,Fighter[] f,int turn,Fighter src,Fighter dst,Rules.Card card,boolean virtual,int depth) {return playCard(log,f,turn,src,dst,card,virtual,depth,false);}
    private boolean playCard(List<Event> log,Fighter[] f,int turn,Fighter src,Fighter dst,Rules.Card card,boolean virtual,int depth,boolean bodyOnly) {return playCard(log,f,turn,src,dst,card,virtual,depth,bodyOnly,virtual);}
    private boolean playCard(List<Event> log,Fighter[] f,int turn,Fighter src,Fighter dst,Rules.Card card,boolean virtual,int depth,boolean bodyOnly,boolean temporaryEffect) {
        src.resolution.checkStop();
        if(virtual&&card.baseId()==322&&!dst.practiceTarget){var target=rules.byId.get(dst.cards[src.slot]);if(target.baseId()!=322){boolean result=playCard(log,f,turn,src,dst,target,true,depth+1,bodyOnly,temporaryEffect);if(target.actionAgain())src.requestChase=true;return result;}}
        if(card.effect().equals("MIRROR_TIME")&&src.mirrorDepth>0){src.requestChase=true;emit(log,f,turn,src,null,card.id(),"COPY_GUARD","瑶光镜阻止循环复制",List.of());return true;}
        if(++src.resolution.cardExecutions>4096)throw new Rejected("RESOLUTION_LIMIT","本场卡牌效果结算超过4096次，复制或追击链无法在限额内结束");
        if(depth>32)throw new Rejected("RESOLUTION_LIMIT","卡牌嵌套超过32层");
        ForgeEffects.Frame previousForgeFrame=src.forgeFrame;src.forgeFrame=new ForgeEffects.Frame(src,card);
        StarEffects.Frame previousStarFrame=src.starFrame;src.starFrame=new StarEffects.Frame(src,card);
        Integer previousCard=src.activeCard;src.activeCard=card.id();boolean previousCareerIgnore=src.careerIgnore;boolean previousAfterCard=src.afterCard;src.afterCard=false;boolean previousIgnoreGuard=src.ignoreGuard;src.ignoreGuard=card.effect().equals("MOUNTAIN_SWORD");
        int originalSlot=src.slot;boolean consumeAfter=src.status("consumeNext")>0;boolean previousEmbryoCloud=src.embryoCloud,previousEmbryoRaging=src.embryoRaging;src.embryoCloud=src.status("embryoNextCloud")>0;src.embryoRaging=src.status("embryoNextRaging")>0;boolean previousUniversal=src.currentUniversal;src.currentUniversal=src.status("universalNext")>0;
        boolean cloudRepeat=!virtual&&src.isCloud(card)&&src.status("nextCloudRepeat")>0,thunderRepeat=!virtual&&card.name().contains("雷")&&src.status("nextThunderRepeat")>0;
        boolean forgeRepeat=!virtual&&ForgeEffects.isFist(src,card)&&src.status("fistRepeat")>0;boolean starRepeat=!virtual&&src.status("repeatNextCard")>0;boolean repeat=!virtual&&src.isRaging(card)&&src.status("doubleRaging")>0;boolean repeatElement=!virtual&&(card.name().contains("火灵")||card.name().contains("土灵"))&&src.status("doubleFireEarth")>0;
        if(!bodyOnly)emit(log,f,turn,src,null,card.id(),"CARD_ATTEMPT","尝试使用第"+(src.slot+1)+"槽「"+card.name()+"」",List.of());
        var changes=new ArrayList<Change>();
        if(!virtual){
            var upgradeChanges=new ArrayList<Change>();
            var changed = SeasonTalentEffects.changeCardLevel(upgradeChanges, src, card);
            if (changed.id() != card.id()) {
                card = changed;
                src.cards[originalSlot] = card.id();
                emit(log, f, turn, src, null, card.id(), "SEASON_TALENT", "刻纹调整卡牌等级", upgradeChanges);
                upgradeChanges = new ArrayList<>();
            }
            if(src.status("downgradeCharges")>0){addStatus(upgradeChanges,src,"downgradeCharges",-1);if(card.rarity()>0&&!card.noUpgrade()&&rules.byId.containsKey(card.id()-10000)){card=rules.byId.get(card.id()-10000);src.cards[originalSlot]=card.id();}else{reduceLifeAndCap(upgradeChanges,src,9);growCareer(upgradeChanges,dst,9);}}
            for(String key:List.of("temporaryUpgrades","permanentUpgrades"))if(src.status(key)>0&&!card.noUpgrade()&&rules.byId.containsKey(card.id()+10000)&&rules.byId.get(card.id()+10000).supported()){card=rules.byId.get(card.id()+10000);src.cards[originalSlot]=card.id();addStatus(upgradeChanges,src,key,-1);break;}
            if(src.status("foodUpgrade")>0){addStatus(upgradeChanges,src,"foodUpgrade",-1);var upgraded=card.noUpgrade()?null:rules.byId.get(card.id()+10000);if(upgraded!=null&&upgraded.supported()){card=upgraded;src.cards[originalSlot]=card.id();}else addStatus(upgradeChanges,src,"appetite",2);}
            if(!upgradeChanges.isEmpty())emit(log,f,turn,src,null,card.id(),"CAREER_PRE_CARD","结算降级与升级效果",upgradeChanges);
        }
        if(!bodyOnly&&src.status("talentCycleUpgrades")>0&&!card.noUpgrade()&&ElementalEffects.generates(src,src.status("lastElement"),ElementalEffects.element(card.name()))){
            var upgraded=rules.byId.get(card.id()+10000);
            if(upgraded!=null&&upgraded.supported()){var cycleChanges=new ArrayList<Change>();addStatus(cycleChanges,src,"talentCycleUpgrades",-1);card=upgraded;if(!virtual)src.cards[originalSlot]=card.id();emit(log,f,turn,src,null,card.id(),"TALENT_UPGRADE","相生相成升级本张五行牌",cycleChanges);}
        }
        if(!virtual&&ForgeEffects.isBasic(card)&&src.status("basicUpgrade")>0){var prior=rules.byId.get(src.cards[StarEffects.previous(src,src.slot)]);var upgraded=!prior.noUpgrade()?rules.byId.get(prior.id()+10000):null;card=upgraded!=null&&upgraded.supported()?upgraded:prior;src.cards[originalSlot]=card.id();addStatus(changes,src,"basicUpgrade",-1);src.forgeFrame=new ForgeEffects.Frame(src,card);}
        if(!virtual&&src.jumpSlot>=0)src.jumpSlot=-1;
        int animaDelta=virtual?0:Math.min(0,card.anima());
        if (animaDelta < 0 && SeasonTalentEffects.has(src,7051) && SeasonTalentEffects.contains(card,"再次行动")) animaDelta++;
        if(!virtual&&src.status("nextCostReduction")>0){animaDelta=Math.min(0,animaDelta+src.status("nextCostReduction"));setStatus(changes,src,"nextCostReduction",0);}
        if(StarEffects.isPosterior(card)&&animaDelta<0)animaDelta=Math.min(0,animaDelta+src.status("posteriorDiscount"));
        if(card.baseId()==108)animaDelta=Math.min(0,animaDelta+FoodEffects.buffCount(src));
        if(card.baseId()==10000029)animaDelta=Math.min(0,animaDelta+StarEffects.debuffs(src));
        if("DEFENSE_FREE".equals(card.effect()) && src.def>0) animaDelta=0;
        if(Set.of("WATER_WAVE","PHANTOM_WATER_WAVE").contains(card.effect()) && src.waterActive())animaDelta=0;
        if(Set.of("CHASE_WIND","CHASE_SHADOW").contains(card.effect())&&CloudEffects.linked(src))animaDelta=0;
        if(Set.of("ELEMENTAL_STRIKE","EXTREME_ELEMENTAL_STRIKE").contains(card.effect())&&animaDelta<0)animaDelta=Math.min(0,animaDelta+ElementalEffects.elements(src,rules));
        if(card.name().contains("灵剑")&&animaDelta<0)animaDelta=Math.min(0,animaDelta+src.status("spiritDiscount"));
        if(animaDelta<0&&StarEffects.starred(src))animaDelta=Math.min(0,animaDelta+src.status("fanDiscount"));
        int old;
        animaDelta=TalentEffects.supplementCost(changes,src,animaDelta);
        if(src.anima+animaDelta<0&&src.status("swordQi")>=-(src.anima+animaDelta)){addStatus(changes,src,"swordQi",src.anima+animaDelta);animaDelta=-src.anima;}
        if(animaDelta<0&&src.status("purpleStars")>0){int n=Math.min(-animaDelta,src.status("starPower"));addStatus(changes,src,"starPower",-n);animaDelta+=n;}
        if (src.anima + animaDelta < 0 && SeasonTalentEffects.has(src,7115)
                && src.status("underworld") >= -(src.anima + animaDelta)) {
            addStatus(changes, src, "underworld", src.anima + animaDelta);
            animaDelta = -src.anima;
        }
        if(src.anima+animaDelta<0) {
            src.embryoCloud=previousEmbryoCloud;src.embryoRaging=previousEmbryoRaging;src.currentUniversal=previousUniversal;src.ignoreGuard=previousIgnoreGuard;src.afterCard=previousAfterCard;src.activeCard=previousCard;src.careerIgnore=previousCareerIgnore;src.starFrame=previousStarFrame;src.forgeFrame=previousForgeFrame;
            TalentEffects.shortage(changes,src,card);
            emit(log,f,turn,src,null,card.id(),"ANIMA_SHORTAGE","灵气不足，结算"+(TalentEffects.has(src,142)?"以逸待劳及储灵收益":"储灵收益")+"；卡槽不前进",changes);
            return false;
        }
        addAnima(changes,src,animaDelta);
        if(!virtual)ForgeEffects.cost(changes,src,card);
        if (!bodyOnly && !src.season.isEmpty()) {
            var seasonChanges = new ArrayList<Change>();
            SeasonTalentEffects.before(seasonChanges, src, card, temporaryEffect);
            if (!seasonChanges.isEmpty()) emit(log, f, turn, src, null, card.id(), "SEASON_TALENT", "修道者仙命", seasonChanges);
        }
        if(forgeRepeat)addStatus(changes,src,"fistRepeat",-1);
        if(starRepeat)addStatus(changes,src,"repeatNextCard",-1);
        if(cloudRepeat)addStatus(changes,src,"nextCloudRepeat",-1);if(thunderRepeat)addStatus(changes,src,"nextThunderRepeat",-1);
        emit(log,f,turn,src,null,card.id(),bodyOnly?"CARD_REPEAT":"CARD_PLAY",bodyOnly?"梅开二度重复本体效果":"使用第"+(src.slot+1)+"槽「"+card.name()+"」",changes);
        if(!virtual&&src.status("ragingUpgrades")>0&&card.rarity()==0&&!card.noUpgrade()&&src.isRaging(card)&&src.hp>8&&rules.byId.containsKey(card.id()+10000)){
            changes=new ArrayList<>();loseHp(changes,src,8);addStatus(changes,src,"ragingUpgrades",-1);card=rules.byId.get(card.id()+10000);src.cards[originalSlot]=card.id();emit(log,f,turn,src,null,card.id(),"CARD_UPGRADE","梦狂剑零式升级本张狂剑",changes);
        }
        if(!virtual&&!bodyOnly){var echoed=TalentEffects.echo(src,card);if(echoed!=null){var echoChanges=new ArrayList<Change>();addStatus(echoChanges,src,"arrayEcho",-1);emit(log,f,turn,src,null,echoed.id(),"TALENT_ECHO","灵阵回响先施放1级效果；卡槽保持当前等级",echoChanges);playCard(log,f,turn,src,dst,echoed,true,depth+1,false,false);src.slot=originalSlot;}}
        if(repeat){var consumed=new ArrayList<Change>();addStatus(consumed,src,"doubleRaging",-1);emit(log,f,turn,src,null,card.id(),"REPEAT_READY","双龙准备重复狂剑效果",consumed);}
        if(!bodyOnly){
        if(src.status("nextCloudChase")>0&&src.isCloud(card)){changes=new ArrayList<>();addStatus(changes,src,"nextCloudChase",-1);src.requestChase=true;emit(log,f,turn,src,null,card.id(),"CARD_CHASE","下张云剑追加再次行动",changes);}
        if(src.status("swordChaseCount")>0&&src.isSword(card)&&!src.requestChase&&src.chasesThisTurn==0){changes=new ArrayList<>();addStatus(changes,src,"swordChaseCount",-1);src.requestChase=true;emit(log,f,turn,src,null,card.id(),"CARD_CHASE","剑牌追加再次行动",changes);}
        if(src.embryoCloud){changes=new ArrayList<>();addStatus(changes,src,"embryoNextCloud",-1);emit(log,f,turn,src,null,card.id(),"CARD_ALIAS","云剑之心将本张视作云剑",changes);}
        if(src.embryoRaging){changes=new ArrayList<>();addStatus(changes,src,"embryoNextRaging",-1);emit(log,f,turn,src,null,card.id(),"CARD_ALIAS","狂剑之心将本张视作狂剑",changes);}
        if(src.currentUniversal){changes=new ArrayList<>();addStatus(changes,src,"universalNext",-1);emit(log,f,turn,src,null,card.id(),"CARD_ALIAS","本张视为云剑和狂剑",changes);}
        if(repeatElement){changes=new ArrayList<>();addStatus(changes,src,"doubleFireEarth",-1);emit(log,f,turn,src,null,card.id(),"REPEAT_READY","聚炎准备重复火土效果",changes);}
        }
        int attacksBefore=src.status("totalAttacks");
        if(!bodyOnly){src.woundDamage=0;src.woundedHits=0;src.refundSword=false;}
        if(!bodyOnly){
        if(src.status("activateNext")>0&&ElementalEffects.element(card.name())>0){changes=new ArrayList<>();activate(changes,src,ElementalEffects.activationKey(ElementalEffects.element(card.name())));addStatus(changes,src,"activateNext",-1);emit(log,f,turn,src,null,card.id(),"ACTIVATION","浑天印激活本张五行",changes);}
        if(src.isCloud(card)&&src.status("catCloudAnima")>0){changes=new ArrayList<>();addAnima(changes,src,src.status("catCloudAnima"));emit(log,f,turn,src,null,card.id(),"FORMATION_TRIGGER","灵猫踏云增加灵气",changes);}
        if(src.isCloud(card)&&src.status("cloudHeal")>0){changes=new ArrayList<>();heal(changes,src,src.status("cloudHeal"));emit(log,f,turn,src,null,card.id(),"FORMATION_TRIGGER","柔心在云剑效果前恢复生命",changes);}
                if(card.name().contains("木灵")&&src.status("woodHealByAttack")>0){changes=new ArrayList<>();heal(changes,src,src.status("attackBonus"));emit(log,f,turn,src,null,card.id(),"FORMATION_TRIGGER","木灵天罡阵治疗",changes);}
        if(src.status("metalFormation")>0 && card.name().contains("金灵")) {
            changes=new ArrayList<>();setStatus(changes,src,"sharpness",src.status("sharpness")+src.status("metalFormation"));
            emit(log,f,turn,src,null,card.id(),"FORMATION_TRIGGER","金灵阵在卡牌效果前增加锋锐",changes);
        }
        if(src.status("waterFormation")>0 && card.name().contains("水灵")){changes=new ArrayList<>();addAnima(changes,src,src.status("waterFormation"));emit(log,f,turn,src,null,card.id(),"FORMATION_TRIGGER","水灵阵在卡牌效果前增加灵气",changes);}
        if(src.status("woodFormation")>0 && card.name().contains("木灵")){
            changes=new ArrayList<>();heal(changes,src,src.status("woodFormation"));
            emit(log,f,turn,src,null,card.id(),"FORMATION_TRIGGER","木灵阵在卡牌效果前恢复生命",changes);
        }
        changes=new ArrayList<>();
        if(src.status("fireFormation")>0&&card.name().contains("火灵")){reduceLifeAndCap(changes,dst,src.status("fireFormation"));emit(log,f,turn,src,dst,card.id(),"FORMATION_TRIGGER","火灵阵减少生命及上限",changes);}
        if(src.status("earthFormation")>0&&card.name().contains("土灵")){changes=new ArrayList<>();addDefense(changes,src,src.status("earthFormation"));emit(log,f,turn,src,null,card.id(),"FORMATION_TRIGGER","土灵阵增加防御",changes);}
        }
        changes=new ArrayList<>();if(!bodyOnly&&TalentEffects.seal(changes,src,card,temporaryEffect)){emit(log,f,turn,src,null,card.id(),"TALENT_SEAL","疾燃咒印：本张印牌获得再次行动，第"+(originalSlot+1)+"槽本场耗尽",changes);changes=new ArrayList<>();}if(!bodyOnly){AdditionalMechanics.before(changes,src,card);FoodEffects.before(changes,src,card);TalentEffects.before(changes,src,card,temporaryEffect);ForgeEffects.globalBefore(changes,src,card);}ForgeEffects.before(changes,src,card);ElementalEffects.before(changes,src,dst,card,rules);CloudEffects.before(changes,src,card);DreamEffects.before(changes,src,dst,card,rules);SpecialEffects.before(changes,src,dst,card);ExtraCardEffects.prepare(changes,src,card);StanceEffects.before(changes,src,card);if(card.baseId()==99000215)addStatus(changes,src,"internalInjury",1);StarEffects.before(changes,src,card,!bodyOnly);
        if(!changes.isEmpty())emit(log,f,turn,src,dst,card.id(),"CARD_PRE_EFFECT","结算攻击前效果",changes);
        if(!bodyOnly&&src.status("branchDrain")>0){changes=new ArrayList<>();int value=1+src.status("attackBonus");loseHp(changes,dst,value);heal(changes,src,value);addStatus(changes,src,"branchDrain",-1);emit(log,f,turn,src,dst,card.id(),"DRAIN","散枝叶吸取生命",changes);}
        if(card.effect().equals("REMAINING_CLOUD")){src.specialAttack=nextParameter(src);if(src.specialAttack<0)throw new Rejected("INVALID_INPUT","残云封天剑的追加攻击参数必须非负");}
        boolean cloudActive=src.cloud>0||src.alwaysCloud();
        var attacks=new ArrayList<Integer>();
        if(card.attack()>0) for(int hit=0;hit<Math.max(1,card.attackCount());hit++) attacks.add(card.attack()+("WATER_SURGE".equals(card.effect())&&src.waterActive()?src.status("waterForce"):0));
        if("CLOUD_EXTRA_ATTACK".equals(card.effect()) && cloudActive) attacks.add(card.param(0));
        if("CLOUD_ONLY_ATTACK".equals(card.effect()) && cloudActive) attacks.add(card.param(0));
        attacks=new ArrayList<>(SpecialEffects.attacks(src,card,DreamEffects.attacks(src,dst,card,CloudEffects.attacks(src,card,ElementalEffects.attacks(src,dst,card,attacks,rules),rules),rules),rules));
        changes=new ArrayList<>();attacks=new ArrayList<>(ExtraCardEffects.attacks(changes,src,card,attacks));if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"CARD_PLAN","结算专属牌攻击条件",changes);
        int consumedSword=attacks.isEmpty()||(card.effect().equals("SWIFT_SWORD")&&!src.afterCard)?0:src.sword;
        attacks=new ArrayList<>(CreatureEffects.attacks(src,card,CareerEffects.attacks(src,card,attacks)));
        attacks=new ArrayList<>(SwordEmbryoEffects.attacks(src,card,StanceEffects.attacks(src,card,ForgeEffects.attacks(src,card,attacks))));
        changes=new ArrayList<>();attacks=new ArrayList<>(StarEffects.attacks(changes,src,card,attacks));if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"STAR_PLAN","结算七星攻击与随机参数",changes);
        changes=new ArrayList<>();ExtraCardEffects.before(changes,src,card);if(!bodyOnly)CareerEffects.before(changes,src,card,temporaryEffect);if(!changes.isEmpty()){emit(log,f,turn,src,null,card.id(),"CAREER_BEFORE","副职出牌前效果",changes);changes=new ArrayList<>();}
        consumedSword=attacks.isEmpty()||(card.effect().equals("SWIFT_SWORD")&&!src.afterCard)?0:src.sword;
        boolean wounded=false;int hit=0;
        for(int value:attacks) wounded|=attack(log,f,turn,src,dst,card,value,++hit);
        for(int value:StanceEffects.followups(src,card))wounded|=attack(log,f,turn,src,dst,card,value,++hit);
        if("WOUND_EXTRA_ATTACK".equals(card.effect()) && wounded) attack(log,f,turn,src,dst,card,card.param(0),++hit);
        changes=new ArrayList<>();
        addAnima(changes,src,(card.sect()==2||card.sect()==4)?0:Set.of("CREATURE","FOOD","EXTRA","CLOUD_DOT","DRAW_ANIMA","MIRROR_FORMATION","DAWN_CLOUD","PIVOT_FORMATION","DREAM_MIXED_BREAK").contains(card.effect())?0:Math.max(0,card.anima()));
        addDefense(changes,src,card.sect()==2||card.sect()==4||card.baseId()==6000004?0:Set.of("EXTRA","CLOUD_DOT","DRAW_ANIMA","MIRROR_FORMATION","DAWN_CLOUD","PIVOT_FORMATION","DREAM_MIXED_BREAK").contains(card.effect())?0:card.defense());
        old=src.sword;src.sword+=card.swordIntent();change(changes,src,"swordIntent",old,src.sword);
        if("CLOUD_DEFENSE".equals(card.effect()) && cloudActive){addDefense(changes,src,card.param(0));}
        if("WOUND_SWORD".equals(card.effect()) && wounded){old=src.sword;src.sword+=card.param(0);change(changes,src,"swordIntent",old,src.sword);}
        if("GAIN_IGNORE".equals(card.effect())){old=src.ignore;src.ignore+=card.param(0);change(changes,src,"ignoreDefense",old,src.ignore);}
        if("WOOD_SEAL".equals(card.effect())){heal(changes,src,card.param(0));activate(changes,src,"woodActivation");}
        if("WOOD_BUD".equals(card.effect())&&src.woodActive())heal(changes,src,card.param(0));
        if("WOOD_REVIVE".equals(card.effect())&&src.woodActive())heal(changes,src,card.param(0)+src.status("attackBonus")*card.param(1));
        if("WOOD_SHADOW".equals(card.effect())&&src.woodActive())setStatus(changes,src,"attackBonus",src.status("attackBonus")+card.param(0));
        if("WOOD_FORMATION".equals(card.effect())){modifyMaxHp(changes,src,card.param(0));setStatus(changes,src,"woodFormation",src.status("woodFormation")+card.param(1));}
        if("WOOD_FRAGRANCE".equals(card.effect())){heal(changes,src,card.param(0));if(src.woodActive())setStatus(changes,src,"attackBonus",src.status("attackBonus")+src.anima/card.param(1));}
        if("WOOD_WILLOW".equals(card.effect())){setStatus(changes,src,"attackBonus",src.status("attackBonus")+card.param(0));heal(changes,src,card.param(1));}
        if("WOOD_SECRET".equals(card.effect())){setStatus(changes,src,"attackBonus",src.status("attackBonus")+card.param(0));modifyMaxHp(changes,src,card.param(1));heal(changes,src,card.param(1));activate(changes,src,"woodActivation");}
        if("ACTIVATE_WATER".equals(card.effect()))activate(changes,src,"waterActivation");
        if("WATER_FORMATION".equals(card.effect()))setStatus(changes,src,"waterFormation",src.status("waterFormation")+card.param(0));
        if(Set.of("WATER_WAVE","WATER_ABSORB").contains(card.effect()) || ("WATER_TIDE".equals(card.effect())&&src.waterActive()))setStatus(changes,src,"waterForce",src.status("waterForce")+card.param(0));
        if("WATER_RISE".equals(card.effect())&&src.waterActive())setStatus(changes,src,"waterForce",src.status("waterForce")+src.anima/card.param(0));
        if("WATER_RISE".equals(card.effect()) || ("WATER_ABSORB".equals(card.effect())&&src.waterActive())){int gain=card.param(1)+("WATER_ABSORB".equals(card.effect())?src.status("waterForce"):0);modifyMaxHp(changes,src,gain);heal(changes,src,gain);}
        if("GAIN_ATTACK".equals(card.effect()))setStatus(changes,src,"attackBonus",src.status("attackBonus")+card.param(0));
        if("ACTIVATE_METAL".equals(card.effect()))activate(changes,src,"metalActivation");
        if("METAL_STORE".equals(card.effect()))setStatus(changes,src,"sharpness",src.status("sharpness")+card.param(0)+(src.metalActive()?src.def/card.param(1):0));
        if("METAL_EDGE".equals(card.effect())&&src.metalActive())setStatus(changes,src,"sharpness",src.status("sharpness")+src.woundDamage/2);
        if("METAL_WAVE".equals(card.effect()))setStatus(changes,src,"sharpness",src.status("sharpness")+card.param(0));
        if("METAL_FORMATION".equals(card.effect())) {
            setStatus(changes,src,"sharpness",src.status("sharpness")+card.param(0));
            setStatus(changes,src,"metalFormation",src.status("metalFormation")+card.param(1));
        }
        if("METAL_NEEDLE".equals(card.effect())&&src.metalActive())setStatus(changes,src,"sharpness",src.status("sharpness")+card.param(0));
        if("GAIN_GUARD".equals(card.effect()))setStatus(changes,src,"guard",src.status("guard")+card.param(0));
        for(String key:List.of("weakness","vulnerability","internalInjury"))if(card.effect().equals("APPLY_"+key))setStatus(changes,dst,key,dst.status(key)+card.param(0));
        if("HEAL_MAX".equals(card.effect())){modifyMaxHp(changes,src,card.param(0));}
        if(Set.of("HEAL","HEAL_ANIMA","HEAL_MAX").contains(card.effect())) {
            int heal=card.param(0)+("HEAL_ANIMA".equals(card.effect())?src.anima*card.param(1):0);
            heal(changes,src,heal);
        }
        if(card.effect().equals("CHAR_BRACELET"))addStatus(changes,src,"guard",card.param(0));
        if(card.effect().equals("CHAR_STAR_FAN")){if(src.anima>=1)addStatus(changes,src,"starPower",card.param(0));if(src.status("starPower")>=card.param(1))src.requestChase=true;addStatus(changes,src,"fanDiscount",1);}
        if(card.effect().equals("STAR_SOUL")){loseHp(changes,dst,card.param(0));addStatus(changes,dst,"skipTurns",1);}
        StanceEffects.after(changes,src,card);CreatureEffects.after(changes,src,card);FoodEffects.after(changes,src,card);ExtraCardEffects.after(changes,src,dst,card);ElementalEffects.after(changes,src,dst,card,rules);CloudEffects.after(changes,src,dst,card,rules);DreamEffects.after(changes,src,dst,card,rules);SpecialEffects.after(changes,src,dst,card,rules);CareerEffects.after(changes,src,card);StarEffects.after(changes,src,card);ForgeEffects.after(changes,src,card);
        if(!changes.isEmpty()) emit(log,f,turn,src,null,card.id(),"CARD_EFFECT","结算卡牌增益",changes);
        if(SwordEmbryoEffects.applies(card)){
            changes=new ArrayList<>();SwordEmbryoEffects.defense(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"EMBRYO_DEFENSE","澄心剑锋加防",changes);
            for(int value:SwordEmbryoEffects.first(src,card)){consumedSword=src.sword;attack(log,f,turn,src,dst,card,value,++hit);}
            changes=new ArrayList<>();SwordEmbryoEffects.gains(changes,src,card);var extra=SwordEmbryoEffects.last(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"EMBRYO_TRAITS","结算剑纹及凝铸",changes);
            for(int value:extra){consumedSword=src.sword;attack(log,f,turn,src,dst,card,value,++hit);}
            changes=new ArrayList<>();SwordEmbryoEffects.aliases(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"EMBRYO_HEART","剑心准备下张牌类型",changes);
        }
        changes=new ArrayList<>();var forgeFollowups=ForgeEffects.followups(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"FORGE_FOLLOWUP","锻玄本体追加效果",changes);for(int value:forgeFollowups){consumedSword=src.sword;attack(log,f,turn,src,dst,card,value,++hit);}
        if(card.baseId()==10000065)for(int i=0;i<card.param(0);i++){int id=nextParameter(src);var copy=rules.byId.get(id);if(copy==null||copy.sect()!=4||copy.rarity()!=0||copy.noUpgrade()||!copy.name().contains("崩拳")||copy.baseId()==10000065)throw new Rejected("INVALID_INPUT","无尽崩绝参数须为1级崩拳ID");playCard(log,f,turn,src,dst,copy,true,depth+1);src.slot=originalSlot;}
        changes=new ArrayList<>();var starFollowups=StarEffects.followups(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,dst,card.id(),"STAR_FOLLOWUP","七星本体追加效果",changes);for(int value:starFollowups){consumedSword=src.sword;attack(log,f,turn,src,dst,card,value,++hit);}
        for(int i=0;i<ExtraCardEffects.copies(card);i++){
            var copy=ExtraCardEffects.selected(src,card,nextParameter(src));if(copy==null)continue;
            playCard(log,f,turn,src,dst,copy,true,depth+1);src.slot=originalSlot;if(copy.actionAgain())src.requestChase=true;
            if(card.baseId()==360&&(copy.baseId()==4000006||copy.baseId()==10000017)&&dst.hp>card.param(1)){changes=new ArrayList<>();setHp(changes,dst,card.param(1));emit(log,f,turn,src,dst,card.id(),"CARD_EFFECT","蛛丝马迹触发探马特效",changes);}
        }
        if(card.baseId()==99000216&&!dst.practiceTarget&&!src.copyingCareer.contains(99000216)){
            src.copyingCareer.add(99000216);try{var copy=rules.byId.get(dst.cards[originalSlot]);if(!copy.supported())throw new Rejected("UNSUPPORTED","幻羽鹦复制的卡牌尚未支持");playCard(log,f,turn,src,dst,copy,true,depth+1);if(copy.actionAgain())src.requestChase=true;}finally{src.copyingCareer.remove(99000216);src.slot=originalSlot;}
        }
        if((card.baseId()==60||card.baseId()==390||card.baseId()==395)&&!src.copyingCareer.contains(card.baseId())){
            src.copyingCareer.add(card.baseId());try{Integer copyId;if(card.baseId()==60)copyId=src.cards[StarEffects.previous(src,originalSlot)];else if(card.baseId()==390)copyId=src.status("bottleFirst");else copyId=ContextEffects.handSustain(src);if(copyId!=null){var copy=rules.byId.get(copyId);if(copy==null||!copy.supported())throw new Rejected("UNSUPPORTED","复制目标尚未支持："+copyId);int count=card.baseId()==60?card.param(1):1;for(int i=0;i<count;i++){playCard(log,f,turn,src,dst,copy,true,depth+1);src.slot=originalSlot;if(copy.actionAgain())src.requestChase=true;}}}finally{src.copyingCareer.remove(card.baseId());src.slot=originalSlot;}
        }
        var generatedCards=new ArrayList<>(ElementalEffects.generated(src,card,rules));generatedCards.addAll(DreamEffects.generated(src,card));generatedCards.addAll(SpecialEffects.generated(src,card));
        if(card.effect().equals("MIRROR_TIME"))src.mirrorDepth++;
        for(int generated:generatedCards){
            var copied=rules.byId.get(generated);if(copied==null||!copied.supported())throw new Rejected("UNSUPPORTED","参数指定的临时卡牌尚未支持："+generated);
            if(card.effect().equals("DREAM_MIXED_FORMATION")&&card.level()>3){changes=new ArrayList<>();ElementalEffects.activateCard(changes,src,copied);emit(log,f,turn,src,null,copied.id(),"ACTIVATION","梦混元无极阵激活临时卡牌",changes);}
            playCard(log,f,turn,src,dst,copied,true,depth+1);if(copied.actionAgain())src.requestChase=true;
            if(card.effect().equals("PHANTOM_DRAW")&&copied.level()<=4)src.pendingAttack=card.attack()+src.anima/card.param(1);
        }
        if(card.effect().equals("MIRROR_TIME"))src.mirrorDepth--;
        if(!src.copyingCareer.contains(card.baseId())){
            var careerCopies=CareerEffects.copies(src,card);src.copyingCareer.add(card.baseId());
            try{for(var copy:careerCopies){var copied=rules.byId.get(copy.id());if(copied==null||!copied.supported())throw new Rejected("UNSUPPORTED","副职临时卡牌尚未支持："+copy.id());src.slot=copy.slot();playCard(log,f,turn,src,dst,copied,true,depth+1);src.slot=originalSlot;if(copied.actionAgain())src.requestChase=true;}}
            finally{src.copyingCareer.remove(card.baseId());src.slot=originalSlot;}
        }
        if(card.effect().equals("ELEMENTAL_FLAG")){
            int selected=nextParameter(src);if(selected!=-1){var copy=rules.byId.get(selected);if(copy==null||!copy.supported()||!copy.name().endsWith("阵"))throw new Rejected("INVALID_INPUT","阵旗第一个参数必须为已支持五行灵阵或-1");playCard(log,f,turn,src,dst,copy,true,depth+1);if(copy.actionAgain())src.requestChase=true;}
            if(originalSlot%2==0){changes=new ArrayList<>();loseHp(changes,src,card.param(1));src.requestChase=true;emit(log,f,turn,src,null,card.id(),"FLAG_COST","阵旗奇数格生命消耗",changes);}
            else {selected=nextParameter(src);if(selected!=-1){var copy=rules.byId.get(selected);if(copy==null||!copy.supported()||!copy.name().endsWith("印"))throw new Rejected("INVALID_INPUT","阵旗第二个参数必须为已支持五行灵印或-1");playCard(log,f,turn,src,dst,copy,true,depth+1);if(copy.actionAgain())src.requestChase=true;}}
        }
        src.requestChase|=SpecialEffects.chase(src,card,rules)||ElementalEffects.chase(src,card)||CloudEffects.chase(src,card)||("METAL_SHUTTLE".equals(card.effect())&&src.metalActive())||("METAL_WAVE".equals(card.effect())&&(src.metalActive()||src.waterActive()))||("WOOD_WILLOW".equals(card.effect())&&src.woodActive());
        if(card.effect().equals("CHAIN_FORMATION")){
            int search=src.chainSearch<0?originalSlot:src.chainSearch;
            for(int previous=search-1;previous>=0;previous--){
                var copied=rules.byId.get(src.cards[previous]);
                if(src.isFormation(copied)){int previousSearch=src.chainSearch;src.chainSearch=previous;playCard(log,f,turn,src,dst,copied,true,depth+1);src.chainSearch=previousSearch;src.slot=originalSlot;if(copied.actionAgain())src.requestChase=true;break;}
            }
        }
        if(src.pendingAttack>0){int bonus=src.pendingAttack;src.pendingAttack=0;consumedSword=src.sword;attack(log,f,turn,src,dst,card,bonus,++hit);}
        if(bodyOnly){src.embryoCloud=previousEmbryoCloud;src.embryoRaging=previousEmbryoRaging;src.currentUniversal=previousUniversal;src.ignoreGuard=previousIgnoreGuard;src.afterCard=previousAfterCard;src.activeCard=previousCard;src.careerIgnore=previousCareerIgnore;src.starFrame=previousStarFrame;src.forgeFrame=previousForgeFrame;src.slot=originalSlot;return true;}
        if(starRepeat||forgeRepeat){playCard(log,f,turn,src,dst,card,true,depth+1,true);src.slot=originalSlot;}
        if (!virtual && !bodyOnly) {
            var repeated = SeasonTalentEffects.repeatAtBaseLevel(src, card);
            if (repeated != null) {
                emit(log, f, turn, src, null, repeated.id(), "SEASON_TALENT", "双生刻纹重复1级效果", List.of());
                playCard(log, f, turn, src, dst, repeated, true, depth + 1, true);
                src.slot = originalSlot;
            }
        }
        src.afterCard=true;src.ignoreGuard=false;src.careerIgnore=false;
        changes=new ArrayList<>();var forgeAttacks=new ArrayList<>(ForgeEffects.globalAfter(changes,src,card));forgeAttacks.addAll(TalentEffects.after(src,card));forgeAttacks.addAll(AdditionalMechanics.extraAttacks(src));if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"FORGE_AFTER","锻玄崩拳传递与耗血收益",changes);for(int value:forgeAttacks){consumedSword=src.sword;attack(log,f,turn,src,dst,card,value,++hit);}
        changes=new ArrayList<>();var globalStarAttacks=StarEffects.globalAfter(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"STAR_AFTER","七星持续与星位效果",changes);for(int value:globalStarAttacks){consumedSword=src.sword;attack(log,f,turn,src,dst,card,value,++hit);}
        if(!virtual&&src.isFormation(card)&&src.status("formationAttack")>0){consumedSword=src.sword;attack(log,f,turn,src,dst,card,src.status("formationAttack"),++hit);}
        if(src.status("beastFormation")>0&&src.status("beastTriggered")==0&&(src.isFormation(card)||card.name().contains("灵剑"))){changes=new ArrayList<>();setStatus(changes,src,"beastTriggered",1);damage(changes,dst,src.anima*src.status("beastFormation"));emit(log,f,turn,src,dst,card.id(),"BEAST_FORMATION","百兽灵剑阵本回合首次触发",changes);}
        if (card.baseId() == 19 && SeasonTalentEffects.has(src,7071) && src.anima > 0) {
            changes = new ArrayList<>();
            int anima = src.anima;
            addAnima(changes, src, -anima);
            addStatus(changes, src, "swordQi", anima);
            emit(log, f, turn, src, null, card.id(), "SEASON_TALENT", "剑气掌控", changes);
        }
        if(src.status("swordQi")>0&&(card.name().contains("灵剑")||(src.hasBase(1000075)&&src.isSword(card))||(SeasonTalentEffects.has(src,7067)&&src.isCloud(card))||(card.baseId()==19&&SeasonTalentEffects.has(src,7071)))){changes=new ArrayList<>();damage(changes,dst,src.status("swordQi"));emit(log,f,turn,src,dst,card.id(),"SWORD_QI","剑气造成普通伤害",changes);}
        if(src.isCloud(card)&&src.status("cloudAfterAttack")>0){consumedSword=src.sword;attack(log,f,turn,src,dst,card,src.status("cloudAfterAttack"),++hit);}
        if(!virtual&&card.attack()>0){int extra=0;changes=new ArrayList<>();if(src.status("extraAttackFormation")>0){addStatus(changes,src,"extraAttackFormation",-1);extra+=5;}if(src.status("observeAttack")>0){extra+=src.status("observeAttack");setStatus(changes,src,"observeAttack",0);}if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"CAREER_EXTRA","结算副职追加攻击次数",changes);if(extra>0){consumedSword=src.sword;attack(log,f,turn,src,dst,card,extra,++hit);}}
        changes=new ArrayList<>();StarEffects.globalGains(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"STAR_GAINS","星弈追加星位与非星位收益",changes);
        changes=new ArrayList<>();old=src.sword;if(consumedSword>0&&src.status("keepSwordCards")>0){addStatus(changes,src,"keepSwordCards",-1);}else {
            if(!src.refundSword)src.sword-=consumedSword;
            if(consumedSword>0&&src.status("swordToAnima")>0){addStatus(changes,src,"swordToAnima",-1);addAnima(changes,src,consumedSword);}
        }change(changes,src,"swordIntent",old,src.sword);
        if(!changes.isEmpty()) emit(log,f,turn,src,null,card.id(),"SWORD_CONSUMED","卡牌结算后消耗本次使用的剑意",changes);
        if(!virtual)for(int adjacent:new int[]{Math.floorMod(originalSlot-1,src.cards.length),(originalSlot+1)%src.cards.length}){
            var neighbor=rules.byId.get(src.cards[adjacent]);
            if(neighbor.effect().equals("DREAM_DOT")){
                if(neighbor.level()<=3){changes=new ArrayList<>();damage(changes,dst,neighbor.param(0));emit(log,f,turn,src,dst,neighbor.id(),"ADJACENT_DAMAGE","相邻梦云剑点星伤害",changes);}
                else {int sword=src.sword;attack(log,f,turn,src,dst,neighbor,neighbor.param(0)+src.anima,1);changes=new ArrayList<>();addSword(changes,src,-sword);if(!changes.isEmpty())emit(log,f,turn,src,null,neighbor.id(),"SWORD_CONSUMED","相邻追加攻击消耗剑意",changes);}
            }
        }
        changes=new ArrayList<>();old=src.cloud;boolean cloudCard=src.isCloud(card);if(!cloudCard&&src.status("cloudSea")>0){addStatus(changes,src,"cloudSea",-1);if(SeasonTalentEffects.has(src,7010))addAnima(changes,src,1);if(SeasonTalentEffects.has(src,7132))addDefense(changes,src,SeasonTalentEffects.param(src,7132,0));src.cloud++;}else src.cloud=cloudCard?src.cloud+1:(src.alwaysCloud()?src.cloud:0);change(changes,src,"cloudChain",old,src.cloud);
        if(!changes.isEmpty()) emit(log,f,turn,src,null,card.id(),"CLOUD_CHAIN",src.cloud==0?"使用非云剑牌，连云中断":"云剑结算，连云计数增加",changes);
        changes=new ArrayList<>();
        if(src.isRaging(card))addStatus(changes,src,"ragingCount",1);
        FoodEffects.history(changes,src,card);addStatus(changes,src,"cardsUsed",1);
        if(ElementalEffects.element(card.name())>0)addStatus(changes,src,"elementalCardsUsed",1);
        if(src.isFormation(card))addStatus(changes,src,"swordFormationCount",1);
        if(src.isSword(card))addStatus(changes,src,"swordCount",1);
        if(src.isCloud(card)&&src.status("cloudStep")>0)addStatus(changes,src,"attackBonus",src.status("cloudStep"));
        if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"CARD_HISTORY","更新使用记录与持续效果",changes);
        if(!virtual&&(card.cardType()==1 || card.cardType()==3 || consumeAfter)) {
            src.consumed[src.slot]=true;if(consumeAfter){changes=new ArrayList<>();addStatus(changes,src,"consumeNext",-1);}
            emit(log,f,turn,src,null,card.id(),"CARD_CONSUMED","第"+(src.slot+1)+"槽已消耗，本场后续跳过",List.of());
        }
        changes=new ArrayList<>();
        setStatus(changes,src,"lastElement",card.name().contains("金灵")?1:card.name().contains("水灵")?2:card.name().contains("木灵")?3:card.name().contains("火灵")?4:card.name().contains("土灵")?5:0);
        if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"ELEMENT_CHAIN","更新上一张成功使用的五行牌",changes);
        if(src.status("elementalNoAttack")>0&&ElementalEffects.element(card.name())>0&&src.turnAttacks==0&&src.chasesThisTurn==0&&!src.requestChase&&!card.actionAgain()){changes=new ArrayList<>();addStatus(changes,src,"elementalNoAttack",-1);src.requestChase=true;emit(log,f,turn,src,null,card.id(),"NO_ATTACK_CHASE","天髓诀未攻击追击",changes);}

        for(int copy=0;copy<(repeat?1:0)+(repeatElement?1:0)+(cloudRepeat?1:0)+(thunderRepeat?1:0);copy++){src.slot=originalSlot;playCard(log,f,turn,src,dst,card,true,depth+1);}
        changes=new ArrayList<>();AdditionalMechanics.afterUse(changes,src,card);FoodEffects.afterUse(changes,src,card);CareerEffects.recordUse(changes,src,card);StarEffects.record(changes,src,card);if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"CAREER_RECORD","副职使用记录与追加行动",changes);
        src.used[originalSlot]=true;changes=new ArrayList<>();setStatus(changes,src,"usedSlotMask",src.status("usedSlotMask")|(1<<originalSlot));
        if(!changes.isEmpty())emit(log,f,turn,src,null,card.id(),"SLOT_USED","记录已使用槽位",changes);
        src.embryoCloud=previousEmbryoCloud;src.embryoRaging=previousEmbryoRaging;src.currentUniversal=previousUniversal;src.ignoreGuard=previousIgnoreGuard;src.afterCard=previousAfterCard;src.activeCard=previousCard;src.careerIgnore=previousCareerIgnore;src.starFrame=previousStarFrame;src.forgeFrame=previousForgeFrame;
        src.slot=virtual?originalSlot:src.jumpSlot>=0?src.jumpSlot:StarEffects.advance(src,originalSlot,src.status("nextSkip"),src.status("nextBack"));
        if(!virtual){src.statuses.put("nextSkip",0);src.statuses.put("nextBack",0);src.jumpSlot=-1;}
        src.careerIgnore=previousCareerIgnore;
        return true;
    }
    private static boolean attack(List<Event> log,Fighter[] fighters,int turn,Fighter src,Fighter dst,Rules.Card card,int base,int hit) {
        src.resolution.checkStop();
        if(++src.resolution.attackExecutions>65536)throw new Rejected("RESOLUTION_LIMIT","本场攻击结算超过65536次");
        var changes=new ArrayList<Change>();
        AdditionalMechanics.beforeAttack(changes,src);TalentEffects.beforeAttack(changes,src);
        if (SeasonTalentEffects.has(dst,3004) && dst.seasonUsed.add(3004)) addStatus(changes,dst,"waterHide",SeasonTalentEffects.param(dst,3004,0));
        if (src.status("seasonInjuryOnAttack") > 0) {
            int injury = src.status("seasonInjuryOnAttack");
            setStatus(changes, src, "seasonInjuryOnAttack", 0);
            addStatus(changes, dst, "internalInjury", injury);
        }
        if(src.status("woodThorns")>0){loseHp(changes,dst,src.status("woodThorns"));heal(changes,src,src.status("woodThorns"));}
        if(src.status("springRounds")>0){modifyMaxHp(changes,src,2);heal(changes,src,2);}
        if(src.status("attackDrain")>0){addStatus(changes,src,"attackDrain",-1);loseHp(changes,dst,1);heal(changes,src,1);}
        if(src.status("attackLifeSong")>0)loseHp(changes,src,src.status("attackLifeSong"));
        int raw=base+((card.effect().equals("SWIFT_SWORD")&&!src.afterCard)?0:src.sword)+src.status("attackBonus");if(src.status("doubleNextAttack")>0){raw+=((card.effect().equals("SWIFT_SWORD")&&!src.afterCard)?0:src.sword)+src.status("attackBonus");addStatus(changes,src,"doubleNextAttack",-1);}
        raw+=src.status("nextAttackBonus");setStatus(changes,src,"nextAttackBonus",0);
        raw=ForgeEffects.rawAttack(changes,src,card,raw);
        if(src.hp*2<src.maxHp)raw+=src.status("wolfAttack");
        raw+=StarEffects.starAttack(src,card)+AdditionalMechanics.attackBonus(src,card)+SeasonTalentEffects.attackBonus(src,card);
        raw+=src.status("attackSong")+(card.baseId()==0?src.status("freeAttackSong"):0);raw=Math.max(1,raw-(src.forgeFrame!=null&&src.forgeFrame.debuffAsForce&&!src.afterCard?0:src.status("attackReduction")));
        if(src.status("shatterFormation")>0){raw+=3;addStatus(changes,src,"nextShatter",1);addStatus(changes,src,"shatterFormation",-1);}
        if(src.status("leafBlade")>0)addStatus(changes,src,"nextShatter",1);
        if(dst.status("reduceAttackCharges")>0){raw=Math.max(1,raw-6);addStatus(changes,dst,"reduceAttackCharges",-1);}
        if(dst.status("iron")>0)raw=Math.max(1,raw-5);
        raw=StarEffects.rawMultiplier(src,card,raw);
        int damage=Math.max(1,raw*(100+(src.forgeFrame==null?0:src.forgeFrame.forcePercent)+(!src.afterCard&&card.name().contains("雷")?src.status("thunderPercent"):0)+(dst.status("vulnerability")>0?(dst.status("sealDebuffPercent")>0?dst.status("sealDebuffPercent"):40):0)-(src.status("weakness")>0&&!(src.forgeFrame!=null&&src.forgeFrame.debuffAsForce&&!src.afterCard)&&!(card.effect().equals("ONE_SWORD")&&!src.afterCard)?(src.status("sealDebuffPercent")>0?src.status("sealDebuffPercent"):40):0)-StanceEffects.reduction(dst)-(dst.status("waterHide")>0?40:0)-(dst.status("reflectRounds")>0?25:0))/100);if(src.status("minimumAttackRounds")>0)damage=Math.max(6,damage);int reflectedAttack=dst.status("reflectRounds")>0?(raw*25+99)/100:0;boolean ignore=ExtraCardEffects.ignore(src,card)||TalentEffects.ignore(src,card)||(!src.afterCard&&src.forgeFrame!=null&&src.forgeFrame.ignore)||src.careerIgnore||src.ignore>0||(!src.afterCard&&Set.of("MOUNTAIN_SWORD","PHANTOM_METAL_NEEDLE").contains(card.effect()));
        if(ignore&&src.ignore>0){int old=src.ignore;src.ignore--;change(changes,src,"ignoreDefense",old,src.ignore);}
        if(!ignore&&((!src.afterCard&&(Set.of("WOOD_PATROL","MIXED_BREAK","EXTREME_WOOD_PATROL").contains(card.effect())||(card.effect().equals("CLOUD_RIFT")&&CloudEffects.linked(src))||card.effect().equals("HEAVEN_EDGE")))||(!src.afterCard&&src.forgeFrame!=null&&src.forgeFrame.shatter)||TalentEffects.shatter(src,card)||src.status("nextShatter")>0)&&dst.def>0)damage=damage*2>=dst.def?damage+(dst.def+1)/2:damage*2;
        if(src.status("nextShatter")>0)addStatus(changes,src,"nextShatter",-1);
        int blocked=ignore?0:Math.min(dst.def,damage);
        int old=dst.def;dst.def-=blocked;change(changes,dst,"defense",old,dst.def);
        int loss=damage-blocked;int woundBonus=0;boolean qualifies=src.status("dragonHorse")>0||TalentEffects.forceWound(src)||DreamEffects.forceWound(src,rulesFor(src))||(loss>0&&dst.status("guard")==0);
        if(qualifies) {
            loss+=dst.status("externalInjury");
            if(!ExtraCardEffects.suppressSharp(src,card)&&src.status("sharpness")>0 && (!"METAL_SHUTTLE".equals(card.effect())||src.afterCard)) {
                int sharp=src.status("sharpness");loss+=sharp;
                int returned="METAL_RETURN".equals(card.effect())&&src.metalActive()&&!src.afterCard?(sharp*card.param(0)+99)/100:0;
                if(src.status("sharpnessFullRefund")>0){returned+=sharp;addStatus(changes,src,"sharpnessFullRefund",-1);}
                setStatus(changes,src,"sharpness",0);if(card.baseId()==423&&src.waterActive()&&!src.afterCard){addStatus(changes,src,"waterForce",sharp);modifyMaxHp(changes,src,sharp);heal(changes,src,sharp);}if(returned>0)addStatus(changes,src,"sharpness",returned);
            }
        }
        woundBonus=loss-(damage-blocked);
        if(loss>0&&dst.status("guard")>0&&!src.ignoreGuard){setStatus(changes,dst,"guard",dst.status("guard")-1);loss=0;}
        if("METAL_GIANT".equals(card.effect())&&src.metalActive()&&qualifies&&!src.afterCard)loss*=2;
        loss=careerLifeLoss(changes,dst,loss);old=dst.hp;dst.hp-=loss;change(changes,dst,"hp",old,dst.hp);
        if(src.status("woundDefenseAttacks")>0){addStatus(changes,src,"woundDefenseAttacks",-1);if(loss>0)addDefense(changes,src,loss);}
        if(src.status("springAttacks")>0){addStatus(changes,src,"springAttacks",-1);if(loss>0)addStatus(changes,src,"waterForce",loss/5);}
        if(src.status("dustStep")>0){addStatus(changes,src,"dustStep",-1);if(loss>0)addDefense(changes,src,loss/3);}
        src.woundDamage+=loss;if(loss>0)addStatus(changes,src,"woundTotal",loss);if(qualifies)src.woundedHits++;src.turnAttacks++;
        addStatus(changes,src,"totalAttacks",1);
        if(loss>0&&src.isRaging(card)&&src.status("ragingLifesteal")>0&&!src.afterCard)heal(changes,src,loss*src.status("ragingLifesteal")/100);
        if(reflectedAttack>0)loseHp(changes,src,reflectedAttack);StarEffects.afterAttack(changes,src,dst);ForgeEffects.afterHit(changes,src,card,loss);ExtraCardEffects.hit(changes,src,card);CreatureEffects.afterHit(changes,src,card,loss);ContextEffects.hit(changes,src,loss);StanceEffects.attacked(changes,dst);
        emit(log,fighters,turn,src,dst,card.id(),"ATTACK","第"+hit+"击：攻击"+damage+(ignore?"（无视防御）":"")+"，防御吸收"+blocked+(woundBonus>0?"，击伤追加"+woundBonus:"")+"，扣血"+loss,changes);
        return qualifies;
    }
    static void damage(List<Change> changes,Fighter dst,int value){damage(changes,dst.opponent,dst,value);}
    static void damage(List<Change> changes,Fighter source,Fighter dst,int value){
        if(value<=0)return;if(StanceEffects.reduction(dst)>0)value=Math.max(1,value*(100-StanceEffects.reduction(dst))/100);if(dst.status("iron")>0)value=Math.max(1,value-5);if(dst.status("reflectRounds")>0){int reflected=(value*25+99)/100;value=Math.max(1,value*75/100);loseHp(changes,source,reflected);}
        int blocked=Math.min(dst.def,value);addDefense(changes,dst,-blocked);loseHp(changes,dst,value-blocked);
    }
    static Rules rulesFor(Fighter src){return src.rules;}
    /** Both fighters must finish resurrection checks even when the first remains dead. */
    static boolean deaths(Fighter first,Fighter second){
        for(int pass=0;pass<4096;pass++){
            isDead(first);isDead(second);
            if(!canRevive(first)&&!canRevive(second)){boolean firstDead=isDead(first);boolean secondDead=isDead(second);if(first.status("deathBattleActive")>0&&first.hp<=second.hp||second.status("deathBattleActive")>0&&second.hp<=first.hp)return false;return firstDead||secondDead;}
        }
        throw new Rejected("RESOLUTION_LIMIT","双方复活检查超过4096轮，无法有限结算该组合");
    }
    static boolean canRevive(Fighter f){return f.hp<=0&&f.status("reviveBlocked")==0&&(f.status("flameRevive")>0||f.status("reviveWheel")>0||f.status("phoenixRevive")>0);}
    static boolean isDead(Fighter f){
        if(f.hp>0)return false;
        if (f.practiceTarget) {
            f.hp = f.maxHp = TARGET_HP;
            return false;
        }
        if(f.status("reviveBlocked")==0){
            if(f.status("flameRevive")>0){
                var ch=new ArrayList<Change>();int value=f.rules.talentById.get(139).param(1),oldHp=f.hp,oldMax=f.maxHp;
                // SetMaxHp/SetHp in the source are assignments, not healing or loss callbacks.
                f.maxHp=value;f.hp=value;ch.add(new Change(f.p.id(),"maxHp",oldMax,value));ch.add(new Change(f.p.id(),"hp",oldHp,value));
                setStatus(ch,f,"flameRevive",0);emit(f.resolution.log,f.resolution.fighters,f.resolution.turn,f,null,null,"RESURRECTION","焰魂归复直接重设生命及上限",ch);
            }
            if(f.hp<=0&&f.status("phoenixRevive")>0){var ch=new ArrayList<Change>();int amount=f.status("phoenixRevive");modifyMaxHp(ch,f,amount);heal(ch,f,amount-f.hp,true);setStatus(ch,f,"phoenixRevive",0);emit(f.resolution.log,f.resolution.fighters,f.resolution.turn,f,null,99000207,"RESURRECTION","浴火凤凰增加生命上限并尝试复活",ch);}
            if(f.hp<=0&&f.status("reviveWheel")>0){var ch=new ArrayList<Change>();if(f.maxHp<=0)modifyMaxHp(ch,f,1);heal(ch,f,f.rules.byId.get(172).param(0)-f.hp,true);addStatus(ch,f,"reviveWheel",-1);emit(f.resolution.log,f.resolution.fighters,f.resolution.turn,f,null,172,"RESURRECTION","九天流光轮尝试复活",ch);}
        }
        if(f.hp>0)return false;
        if(f.status("deathBattleWill")>0){var ch=new ArrayList<Change>();setStatus(ch,f,"deathBattleWill",0);setStatus(ch,f,"deathBattleActive",1);emit(f.resolution.log,f.resolution.fighters,f.resolution.turn,f,null,null,"DEATH_BATTLE","死战之志在复活检查后生效",ch);}
        return f.status("deathBattleActive")==0;
    }
    static void setHp(List<Change> changes,Fighter f,int value){int old=f.hp;f.hp=Math.min(f.maxHp,value);f.capClamping=true;change(changes,f,"hp",old,f.hp);f.capClamping=false;}
    static void growCareer(List<Change> changes,Fighter f,int amount){modifyMaxHp(changes,f,amount);heal(changes,f,amount);}
    static int careerLifeLoss(List<Change> ch,Fighter f,int amount){if(amount<=0)return amount;if(f.status("invertLifeLoss")>0){addStatus(ch,f,"invertLifeLoss",-1);heal(ch,f,amount);return 0;}if(f.status("dreamDiamond")>0)amount=1;if(f.status("leafShield")>0){int absorb=Math.min(f.def,amount/2);addDefense(ch,f,-absorb);amount-=absorb;}return amount;}
    static void addAnima(List<Change> changes,Fighter f,int delta){delta=SeasonTalentEffects.animaGain(changes,f,delta);delta=FoodEffects.anima(changes,f,delta);if(delta>0&&f.status("craneManaMultiplier")>0)delta*=f.status("craneManaMultiplier");if(delta>0&&f.status("halveAnimaFirst")>0&&f.status("ownTurns")==1)delta/=2;if(delta>0&&TalentEffects.has(f,204)&&!SeasonTalentEffects.has(f,17071)){ForgeEffects.physique(changes,f,delta);return;}int before=f.anima;f.anima=Math.max(0,f.anima+delta);change(changes,f,"anima",before,f.anima);}
    static void addSword(List<Change> changes,Fighter f,int delta){int before=f.sword;f.sword=Math.max(0,f.sword+delta);change(changes,f,"swordIntent",before,f.sword);}
    static void addStatus(List<Change> changes,Fighter f,String key,int delta){setStatus(changes,f,key,Math.max(0,f.status(key)+delta));}
    static void activate(List<Change> changes,Fighter f,String key){
        addStatus(changes,f,key,1);
        if(f.hasBase(372))damage(changes,f.opponent,f.rules.byId.get(372).param(0));
        if(f.status("activationDamage")>0)damage(changes,f.opponent,f.status("activationDamage"));
        if(f.status("mixedChase")>0&&!f.requestChase&&f.chasesThisTurn==0){addStatus(changes,f,"mixedChase",-1);f.requestChase=true;}
    }
    static boolean hasParameter(Fighter src) {
        return src.cardOutcomeInputs.hasNext(src.slot, src.activeCard) || !src.resolution.parameters.isEmpty();
    }
    static int nextParameter(Fighter src) { return nextParameter(src, src.slot, src.activeCard); }
    static int nextParameter(Fighter src, int slot, Integer cardId) {
        Integer chosen = src.cardOutcomeInputs.poll(slot, cardId);
        if (chosen == null) chosen = src.resolution.parameters.poll();
        if (chosen == null) {
            var card = cardId == null ? null : src.rules.byId.get(cardId);
            String label = card == null ? "本效果" : "第" + (slot + 1) + "槽「" + card.name() + "」";
            throw new Rejected("MISSING_BATTLE_PARAMETERS", src.p.name() + "的" + label + "缺少指定结果，请在对应卡槽补填");
        }
        int index = ++src.resolution.parameterIndex;
        emit(src.resolution.log, src.resolution.fighters, src.resolution.turn, src, null, cardId,
                "BATTLE_PARAMETER", "读取指定结果 #" + index + "：" + chosen, List.of());
        return chosen;
    }
    static void addDefense(List<Change> changes,Fighter f,int delta){if(delta>0)delta+=f.status("ringGain")+f.status("fusedRing")+(SeasonTalentEffects.has(f,7133)?1:0);delta=TalentEffects.defenseGain(f,delta);if(delta>0&&f.status("noDefense")>0)return;if(delta>0&&f.status("fortuneRounds")>0)delta=(delta*140+99)/100;int before=f.def;f.def=Math.max(0,f.def+delta);change(changes,f,"defense",before,f.def);}
    static void loseHp(List<Change> changes,Fighter f,int amount){if(amount<=0)return;if(f.status("guard")>0&&(f.opponent==null||!f.opponent.ignoreGuard)){addStatus(changes,f,"guard",-1);return;}amount=careerLifeLoss(changes,f,amount);int before=f.hp;f.hp-=amount;change(changes,f,"hp",before,f.hp);}
    static void modifyMaxHp(List<Change> changes,Fighter f,int delta){if(delta>0&&SeasonTalentEffects.has(f,7076))delta++;if(delta<0&&f.opponent!=null)delta-=f.opponent.status("fusedRing");if(delta>0&&f.status("fortuneRounds")>0)delta=(delta*140+99)/100;if(delta<0&&f.status("dreamDiamond")>0)delta=-1;if(delta==0)return;if(delta<0){addStatus(changes,f,"maxHpLost",-delta);addStatus(changes,f,"maxHpLossCount",1);}int before=f.maxHp;f.maxHp=Math.max(0,f.maxHp+delta);change(changes,f,"maxHp",before,f.maxHp);before=f.hp;f.hp=Math.min(f.hp,f.maxHp);f.capClamping=true;change(changes,f,"hp",before,f.hp);f.capClamping=false;}
    static void reduceLifeAndCap(List<Change> changes,Fighter f,int amount){loseHp(changes,f,amount);modifyMaxHp(changes,f,-amount);}
    static void heal(List<Change> changes,Fighter fighter,int amount){heal(changes,fighter,amount,false);}
    static void heal(List<Change> changes,Fighter fighter,int amount,boolean revive){
        revive=revive&&fighter.status("reviveBlocked")==0;
        if (amount > 0 && SeasonTalentEffects.has(fighter,7076)) amount++;
        amount=FoodEffects.healing(fighter,amount);
        if(amount>0&&fighter.status("fortuneRounds")>0)amount=(amount*140+99)/100;
        if(amount<=0||(!revive&&fighter.hp<=0&&fighter.status("deathBattleActive")==0)||fighter.status("noHealing")>0)return;
        AdditionalMechanics.resources(changes,fighter,"healingAttempt",0,amount);TalentEffects.healAttempt(changes,fighter,amount);ForgeEffects.resourceChanged(changes,fighter,"healingAttempt",0,amount);
        if(fighter.status("healToSharpRounds")>0){addStatus(changes,fighter,"sharpness",amount);return;}
        int healingCap=TalentEffects.selected(fighter).stream().filter(t->t.talentId()==120).mapToInt(t->t.param(0)).sum();if(healingCap>0)modifyMaxHp(changes,fighter,healingCap);
        if(fighter.status("gainChase")>0){addStatus(changes,fighter,"gainChase",-1);fighter.requestChase=true;}addStatus(changes,fighter,"healingTotal",amount);addStatus(changes,fighter,"turnHealing",amount);int overflow=Math.max(0,fighter.hp+amount-fighter.maxHp);if(overflow>0&&fighter.status("overflowGrowth")>0)modifyMaxHp(changes,fighter,overflow);int before=fighter.hp;fighter.hp=Math.min(fighter.maxHp,fighter.hp+amount);change(changes,fighter,"hp",before,fighter.hp);if(overflow>0&&fighter.status("overflowDamageRounds")>0)damage(changes,fighter.opponent,overflow);if(fighter.status("reverseRounds")>0&&fighter.resolution!=null){int reflection=amount/2;fighter.resolution.enqueue(()->damage(changes,fighter.opponent,reflection));}
    }
    static void setStatus(List<Change> changes,Fighter f,String key,int value){
        int old=f.status(key);
        if(FoodEffects.convert(changes,f,key,value-old))return;
        value = old + SeasonTalentEffects.statusGain(changes, f, key, value - old);
        if(key.equals("sharpness")&&value>old)value+=f.status("ringGain");
        int forceDelta=key.equals("force")?value-old:0,forceOverflow=key.equals("force")?Math.max(0,value-f.status("forceCap")):0;
        if(value>old&&(key.equals("internalInjury")||key.equals("externalInjury"))){value+=f.status("snakeShadow");if(key.equals("internalInjury")&&f.status("injuryAmplifyRounds")>0)value+=2;}
        if(value>old)value=old+TalentEffects.debuffGain(changes,f,key,value-old);
        if(value>old&&CareerEffects.NEGATIVE.contains(key)&&f.status("repel")>0){int blocked=Math.min(value-old,f.status("repel"));addStatus(changes,f,"repel",-blocked);value-=blocked;}
        if(key.equals("attackBonus")&&value>old&&f.status("attackToThorns")>0){int convert=Math.min(value-old,f.status("attackToThorns"));addStatus(changes,f,"woodThorns",convert);value-=convert;}
        if(key.equals("sharpness")&&value>old&&f.status("sharpToFireRounds")>0){reduceLifeAndCap(changes,f.opponent,(value-old)*2);value=old;}
        if(key.equals("force")){if(value>old)addStatus(changes,f,"forceGained",value-old);value=Math.min(value,f.status("forceCap"));}
        f.statuses.put(key,value);change(changes,f,key,old,value);
        ForgeEffects.changed(changes,f,key,old,value);TalentEffects.changed(changes,f,key,old,value);
        if(key.equals("force")){TalentEffects.forceChanged(changes,f,forceDelta);addDefense(changes,f,forceOverflow);AdditionalMechanics.force(changes,f,forceDelta,forceOverflow);}
        StarEffects.statusChanged(changes,f,key,old,value);FoodEffects.gained(changes,f,key,old,value);AdditionalMechanics.status(changes,f,key,old,value);SeasonTalentEffects.statusChanged(changes,f,key,old,value);
        if(value>old&&key.equals("sharpness"))addStatus(changes,f,"sharpnessGained",value-old);
        if(value>old&&key.equals("waterForce"))addStatus(changes,f,"waterForceGained",value-old);
    }
}
