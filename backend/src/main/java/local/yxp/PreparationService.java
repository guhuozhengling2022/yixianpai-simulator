package local.yxp;

import java.util.*;

/** Apply already resolved draw outcomes; server RNG/pool selection is not guessed. */
public final class PreparationService {
    public record Input(String rulesetId,int sourceCardId,List<Integer> deck,List<Integer> outcomes) {}
    public record Result(String status,String phase,int sourceCardId,int experienceDelta,
                         List<Integer> gainedCards,String preferredElement,String outcomeAuthority,int physiqueDelta,int physiqueCapDelta,int maxHpDelta) {}
    private final Rules rules;
    public PreparationService(Rules rules){this.rules=rules;}
    public Result resolve(Input input){
        if(input==null||!Rules.ID.equals(input.rulesetId()))throw new Engine.Rejected("INVALID_INPUT","规则版本不匹配");
        var source=rules.byId.get(input.sourceCardId());
        int base=source==null?-1:source.baseId();
        if(base!=7000109&&base!=202&&base!=10000058)throw new Engine.Rejected("UNSUPPORTED","该卡不属于当前战斗外结算接口");
        if(input.deck()==null||input.deck().size()>8||input.deck().stream().anyMatch(id->id==null||!rules.byId.containsKey(id)))throw new Engine.Rejected("INVALID_INPUT","deck需要0至8张已知卡牌");
        if(base==10000058){if(input.outcomes()==null||!input.outcomes().isEmpty())throw new Engine.Rejected("INVALID_INPUT","灵草药浴不需要抽牌参数");return new Result("RESOLVED_OUTCOME_APPLIED","REFINEMENT",source.id(),0,List.of(),"无","STATIC_CONFIG",source.physique(),source.physique(),source.physique());}
        int count=base==7000109?1:source.param(0);
        if(input.outcomes()==null||input.outcomes().size()!=count)throw new Engine.Rejected("INVALID_INPUT","需要恰好"+count+"个已确定的抽牌结果");
        for(var id:input.outcomes()){
            var card=id==null?null:rules.byId.get(id);
            if(card==null||ElementalEffects.element(card.name())==0||(base==202&&card.rarity()!=0))throw new Engine.Rejected("INVALID_INPUT","抽牌结果必须为五行牌，五行灵田只能抽1级牌");
        }
        int preferred=input.deck().isEmpty()?0:ElementalEffects.element(rules.byId.get(input.deck().getFirst()).name());
        return new Result("RESOLVED_OUTCOME_APPLIED",base==7000109?"PREPARATION":"AFTER_BATTLE",source.id(),base==202?-count:0,List.copyOf(input.outcomes()),ElementalEffects.NAMES[preferred],"USER_SUPPLIED",0,0,0);
    }
}
