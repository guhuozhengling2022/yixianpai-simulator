package local.yxp;
import java.util.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static local.yxp.Models.*;
class HandInputTest {
 final Rules rules=new Rules(); final Engine engine=new Engine(rules);
 Player p(String id,List<Integer> hand){return new Player(id,id,0,60,60,0,0,0,Collections.nCopies(8,1000005),List.of(),Map.of(),hand);}
 Request r(List<Integer> hand){return new Request(Rules.ID,"STANDARD","POST_OPENING","A",List.of(p("A",hand),p("B",null)));}
 @Test void unknownAndEmptyRemainDistinct(){var gson=new Gson();var a=gson.fromJson(gson.toJson(r(null)),Request.class);var b=gson.fromJson(gson.toJson(r(List.of())),Request.class);assertNull(a.players().getFirst().hand());assertEquals(List.of(),b.players().getFirst().hand());assertDoesNotThrow(()->engine.validate(a));assertDoesNotThrow(()->engine.validate(b));}
 @Test void duplicateUnsupportedCombatCardsCanStillBeStoredInHand(){var req=r(List.of(1,1,1000005));assertDoesNotThrow(()->engine.validate(req));assertEquals(List.of(1,1,1000005),req.players().getFirst().hand());}
 @Test void invalidUnknownAndOversizedHandAreRejected(){assertThrows(Engine.Rejected.class,()->engine.validate(r(List.of(99999999))));assertThrows(Engine.Rejected.class,()->engine.validate(r(Arrays.asList(1000005,null))));assertThrows(Engine.Rejected.class,()->engine.validate(r(Collections.nCopies(201,1000005))));}
 @Test void handIsDefensivelyCopiedAndPlayersAreIndependent(){var hand=new ArrayList<>(List.of(1000005));var req=r(hand);hand.clear();assertEquals(1,req.players().getFirst().hand().size());assertNull(req.players().get(1).hand());assertThrows(UnsupportedOperationException.class,()->req.players().getFirst().hand().clear());}
 Player cloud(List<Integer> hand){return new Player("A","A",0,60,60,0,0,0,Collections.nCopies(8,1000001),List.of(220),Map.of(),hand);}
 @Test void cloudOpeningRequiresExplicitHand(){var req=new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(cloud(null),p("B",null)));assertThrows(Engine.Rejected.class,()->engine.validate(req));}
 @Test void cloudOpeningCountsDuplicateHandCardsAndDeckAndCapsReward(){for(var hand:List.of(List.<Integer>of(),Collections.nCopies(20,1000001))){var req=new Request(Rules.ID,"STANDARD","PRE_OPENING","A",List.of(cloud(hand),p("B",null)));var result=engine.simulate(req);var state=result.events().stream().filter(e->e.type().equals("TALENT")).findFirst().orElseThrow().states().getFirst();assertEquals(Math.min((8+hand.size())/2,rules.talentById.get(220).param(0)),state.anima());assertEquals(hand,req.players().getFirst().hand());}}
}
