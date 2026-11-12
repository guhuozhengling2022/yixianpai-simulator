package local.yxp;

import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}
    public record SeasonChoice(int id, Integer slot, Boolean disabled) {}
    public record CardOutcome(int slot, int cardId, List<Integer> values) {}
    public record Player(String id, String name, int characterId, int hp, int maxHp,
                         int anima, int defense, int swordIntent, List<Integer> cards,
                         List<Integer> talentIds, Map<String,Integer> buffs, List<Integer> hand,
                         List<SeasonChoice> seasonTalents, Integer battleRound, List<CardOutcome> cardOutcomes,
                         Integer singleUseCardLimit) {
        public Player { if(hand!=null) hand=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(hand)); }
        public Player(String id,String name,int characterId,int hp,int maxHp,int anima,int defense,int swordIntent,List<Integer> cards,List<Integer> talentIds,Map<String,Integer> buffs,List<Integer> hand,List<SeasonChoice> seasonTalents,Integer battleRound,List<CardOutcome> cardOutcomes){this(id,name,characterId,hp,maxHp,anima,defense,swordIntent,cards,talentIds,buffs,hand,seasonTalents,battleRound,cardOutcomes,null);}
        public Player(String id,String name,int characterId,int hp,int maxHp,int anima,int defense,int swordIntent,List<Integer> cards,List<Integer> talentIds,Map<String,Integer> buffs,List<Integer> hand,List<SeasonChoice> seasonTalents,Integer battleRound){this(id,name,characterId,hp,maxHp,anima,defense,swordIntent,cards,talentIds,buffs,hand,seasonTalents,battleRound,null);}
        public Player(String id,String name,int characterId,int hp,int maxHp,int anima,int defense,int swordIntent,List<Integer> cards,List<Integer> talentIds,Map<String,Integer> buffs,List<Integer> hand){this(id,name,characterId,hp,maxHp,anima,defense,swordIntent,cards,talentIds,buffs,hand,null,null);}
        public Player(String id,String name,int characterId,int hp,int maxHp,int anima,int defense,int swordIntent,List<Integer> cards,List<Integer> talentIds,Map<String,Integer> buffs){this(id,name,characterId,hp,maxHp,anima,defense,swordIntent,cards,talentIds,buffs,null);}
    }
    public record Request(String rulesetId, String mode, String initializationStage,
                          String firstActorId, List<Player> players,List<Integer> battleParams) {
        public Request(String rulesetId,String mode,String initializationStage,String firstActorId,List<Player> players){this(rulesetId,mode,initializationStage,firstActorId,players,List.of());}
    }
    public record Change(String playerId, String attribute, int before, int after) {}
    public record State(String id, String name, int hp, int maxHp, int anima, int defense,
                        int swordIntent, int nextSlot, int cloudChain, int ignoreDefense,
                        List<Integer> consumedSlots, List<Integer> runtimeCards, Map<String,Integer> statuses,List<Integer> runtimeHand,Integer destiny) {}
    public record Event(int seq, int turn, String actorId, String targetId, Integer cardId,
                        String type, String message, List<Change> changes, List<State> states) {}
    public record Result(String status, String winnerId, String endReason, String rulesetId,
                         int turns, List<State> finalStates, List<Event> events) {}
}
