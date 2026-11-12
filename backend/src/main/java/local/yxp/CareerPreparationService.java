package local.yxp;

import java.util.*;

/** Refinement/exchange/growth outside combat. Random choices are supplied resolved outcomes. */
public final class CareerPreparationService {
    public record Input(String rulesetId,int sourceCardId,List<Integer> hand,List<Integer> deck,List<Integer> outcomes,List<Integer> targetSlots){}
    public record Result(String status,String phase,List<Integer> hand,List<Integer> deck,List<Integer> gainedCards,Map<String,Integer> resourceDeltas,Map<String,Integer> nextBattleSelf,Map<String,Integer> nextBattleOpponent,String outcomeAuthority){}
    final Rules rules;
    public CareerPreparationService(Rules rules){this.rules=rules;}
    void require(boolean ok,String text){if(!ok)throw new Engine.Rejected("INVALID_INPUT",text);}
    List<Integer> list(List<Integer> input,int max){require(input!=null&&input.size()<=max,"hand/deck不能为空，手牌最多128项、卡组最多8项");require(input.stream().allMatch(id->id!=null&&rules.byId.containsKey(id)),"存在未知卡牌");return new ArrayList<>(input);}
    int upgrade(int id,int times){for(int i=0;i<times;i++){var c=rules.byId.get(id);if(c.noUpgrade()||!rules.byId.containsKey(id+10000))break;id+=10000;}return id;}
    public Result resolve(Input in){
        require(in!=null&&Rules.ID.equals(in.rulesetId()),"规则版本不匹配");var c=rules.byId.get(in.sourceCardId());require(c!=null&&CareerEffects.isCareer(c)&&c.preparationSupported(),"该牌不是副职战斗外结算牌");
        var hand=list(in.hand(),128);var deck=list(in.deck(),8);var outcomes=in.outcomes()==null?List.<Integer>of():List.copyOf(in.outcomes());var slots=in.targetSlots()==null?List.<Integer>of():List.copyOf(in.targetSlots());
        require(outcomes.stream().allMatch(id->id!=null&&rules.byId.containsKey(id)),"抽牌结果含未知牌");require(slots.stream().allMatch(i->i!=null&&i>=0&&i<hand.size()+deck.size()),"目标槽位按手牌后接卡组从0计数");require(new HashSet<>(slots).size()==slots.size(),"目标槽位不能重复");
        var resources=new LinkedHashMap<String,Integer>();var self=new LinkedHashMap<String,Integer>();var opponent=new LinkedHashMap<String,Integer>();var gained=new ArrayList<Integer>();int n=c.param(0),expected=0;String phase=c.cardType()==4?"EXCHANGE":c.cardType()==2?"REFINEMENT":"AFTER_BATTLE";
        switch(c.baseId()){
            case 2000006,2000014,9000014 -> resources.put("permanentMaxHp",n);
            case 2000009 -> resources.put("destiny",n);
            case 2000012 -> resources.put("experience",n);
            case 2000015,384 -> {int count=c.baseId()==384?1:n;require(slots.size()==count,"升级目标数量错误");for(int slot:slots){var target=rules.byId.get(slot<hand.size()?hand.get(slot):deck.get(slot-hand.size()));require(!target.noUpgrade()&&rules.byId.containsKey(target.id()+10000),"目标不可升级");if(c.baseId()==2000015)require(target.rarity()==0,"悟道丹仅能升级1级牌");else require(target.career()>0,"梦画龙点睛仅能升级副职牌");if(slot<hand.size())hand.set(slot,upgrade(target.id(),1));else deck.set(slot-hand.size(),upgrade(target.id(),1));}}
            case 6000002 -> resources.put("exchangeChances",n);
            case 6000009,6000015 -> expected=1;
            case 6000010 -> {resources.put("exchangeChances",n);expected=1;}
            case 386 -> {resources.put("experience",-n);expected=n;}
            case 9000001 -> {self.put("maxHp",n);self.put("hp",n);}
            case 9000002 -> opponent.put("normalDamage",n);
            case 9000004 -> self.put("recovery",n);
            case 9000006 -> {phase="ROUND_START_GROWTH";expected=1;}
            case 9000007 -> {for(int i=0;i<hand.size();i++)if(rules.byId.get(hand.get(i)).desc().contains("[成长]"))hand.set(i,upgrade(hand.get(i),n));}
            case 9000008 -> self.put("attackBonus",n);
            case 9000010,385 -> {self.put("speed",n);if(c.baseId()==385)self.put("deathBattleWillIfSecond",1);}
            case 9000012 -> opponent.put("internalInjury",n);
            case 9000016 -> self.put("defense",n);
            case 9000021 -> opponent.put("maxHp",-n);
            case 9000022 -> opponent.put("attackReduction",n);
            case 9000023 -> self.put("repel",n);
            case 9000024 -> {self.put("hp",-n);self.put("forcedChases",1);}
            case 9000025 -> {resources.put("destinyAtLeastOne",-n);expected=c.param(2);}
            case 9000015 -> {resources.put("experience",-n);expected=n;}
            case 350 -> {resources.put("experience",-1);expected=1;}
            case 180 -> expected=c.param(1);
            default -> throw new Engine.Rejected("UNSUPPORTED","尚无此牌的战斗外处理器");
        }
        require(outcomes.size()==expected,"需要恰好"+expected+"张已确定结果");
        if(c.baseId()==9000015)require(outcomes.stream().allMatch(id->rules.byId.get(id).career()==6),"灵田结果必须为灵植牌");
        if(c.baseId()==350)require(outcomes.stream().allMatch(id->deck.contains(id)&&rules.byId.get(id).rarity()==0&&!rules.byId.get(id).hidden()),"梦空间灵田须复制卡组1级常规牌");
        if(c.baseId()==180)require(outcomes.stream().allMatch(id->{var x=rules.byId.get(id);return (x.career()==1||x.career()==6)&&x.rarity()==2&&x.cardType()==2;}),"神炼鼎结果必须为3级灵植或炼丹炼化牌");
        gained.addAll(outcomes);return new Result("RESOLVED_OUTCOME_APPLIED",phase,List.copyOf(hand),List.copyOf(deck),List.copyOf(gained),Map.copyOf(resources),Map.copyOf(self),Map.copyOf(opponent),"USER_SUPPLIED");
    }
}
