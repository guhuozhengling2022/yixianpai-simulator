package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Character, secret and phantom card effects from the two requested sects. */
final class SpecialEffects {
    static final Set<String> NEGATIVE=Set.copyOf(CareerEffects.NEGATIVE);
    static void before(List<Change> changes,Fighter src,Fighter dst,Rules.Card c){ }
    static List<Integer> attacks(Fighter src,Rules.Card c,List<Integer> initial,Rules rules){
        var values=new ArrayList<Integer>(initial);int bonus=switch(c.effect()){
            case "METAL_METEOR" -> src.status("defenseLost")/c.param(0);
            case "LIFE_DESTROYER" -> src.status("hpLost")/c.param(0);
            case "HEAVEN_EDGE" -> src.sword*(c.param(0)-1)+src.status("attackBonus")*(c.param(1)-1);
            case "CLOUD_WIND" -> ElementalEffects.elements(src,rules)*c.param(0);
            default -> 0;
        };
        for(int i=0;i<values.size();i++)values.set(i,values.get(i)+bonus);
        if(c.effect().equals("PHANTOM_DRAW"))values.clear();
        if(c.effect().equals("PHANTOM_NO_EDGE")){values.clear();if(CloudEffects.linked(src))values.add(c.attack()+src.status("totalAttacks")*c.param(0));}
        return values;
    }
    static void after(List<Change> changes,Fighter src,Fighter dst,Rules.Card c,Rules rules){
        switch(c.effect()){
            case "SUNSET_CLOUD" -> {if(CloudEffects.linked(src))addSword(changes,src,src.cloud*c.param(0));}
            case "DRAGON_FORMATION" -> addSword(changes,src,src.def/c.param(0));
            case "BEAST_FORMATION" -> addStatus(changes,src,"beastFormation",1);
            case "CLOUD_RAGING_BRIDGE" -> {addStatus(changes,src,"cloudRagingBridge",1);addStatus(changes,src,"ragingCount",c.param(0));}
            case "WILD_SEAL" -> addStatus(changes,src,"gainChase",1);
            case "LAVA_SEAL" -> reduceLifeAndCap(changes,dst,c.param(0)+src.def/c.param(1));
            case "ROCK_SEAL" -> addDefense(changes,src,src.status("cardsUsed")*c.param(0));
            case "WAVE_SEAL" -> addStatus(changes,src,"sharpness",src.anima*c.param(0));
            case "STAR_CLOUD" -> {if(CloudEffects.linked(src))addDefense(changes,src,src.anima*c.param(0));}
            case "STAR_RAGING" -> {int count=src.status("ragingCount");addAnima(changes,src,count);addDefense(changes,src,count*c.param(0));addSword(changes,src,count);}
            case "STAR_HUN", "PHANTOM_HUN" -> {
                var types=new HashSet<Integer>();for(int i=1;i<=c.param(0);i++){var target=rules.byId.get(src.cards[(src.slot+i)%src.cards.length]);int element=ElementalEffects.element(target.name());if(element>0){types.add(element);activate(changes,src,ElementalEffects.activationKey(element));}}
                if(c.effect().equals("STAR_HUN")&&types.size()==1)addAnima(changes,src,1);
            }
            case "LIFE_INTENT" -> addSword(changes,src,src.status("hpLost")/c.param(0));
            case "MIRROR_TIME" -> {for(int i=1;i<=5;i++)activate(changes,src,ElementalEffects.activationKey(i));src.requestChase=true;}
            case "LIVING_INTENT" -> {modifyMaxHp(changes,src,c.param(1));heal(changes,src,src.sword*c.param(0));}
            case "EMPTY_CLOUD" -> {if(CloudEffects.linked(src))addStatus(changes,dst,"weakness",c.param(0));}
            case "ANIMA_SONG" -> addStatus(changes,src,"animaDefenseGain",c.param(0));
            case "RANDOM_RAGING" -> heal(changes,src,c.param(1));
            case "CLOUD_CYCLE" -> {addStatus(changes,src,"cloudArrayAlias",1);addStatus(changes,src,"cloudAfterAttack",c.param(0));}
            case "SPIRIT_SUNFLOWER" -> {int count=(int)Arrays.stream(src.cards).filter(id->rules.byId.get(id).name().contains("灵剑")).count();addAnima(changes,src,Math.min(c.param(0),count));}
            case "HEAVEN_EARTH" -> {addStatus(changes,src,"nextShatter",c.param(0));addStatus(changes,src,"woundDefenseAttacks",c.param(0));}
            case "ELEMENTAL_PROTECTION" -> {addStatus(changes,src,"guard",c.param(0)+(src.used[src.slot]?0:1));ElementalEffects.activateCard(changes,src,rules.byId.get(src.cards[StarEffects.next(src,src.slot)]));}
            case "FIRE_RADIANCE" -> {addStatus(changes,src,"attackBonus",c.param(0));if(src.fireActive())for(var fighter:List.of(src,dst))for(String key:NEGATIVE)addStatus(changes,fighter,key,-c.param(1));}
            case "WILD_DANCE" -> {addStatus(changes,src,"attackBonus",c.param(0));addStatus(changes,dst,"attackBonus",c.param(0));setStatus(changes,src,"allChase",1);setStatus(changes,dst,"allChase",1);}
            case "HUN_BRUSH" -> {var next=rules.byId.get(src.cards[StarEffects.next(src,src.slot)]);if(ElementalEffects.element(next.name())>0){ElementalEffects.activateCard(changes,src,next);src.requestChase=true;}}
            case "WOOD_HEALING_FORMATION" -> {addStatus(changes,src,"attackBonus",c.param(0));addStatus(changes,src,"woodHealByAttack",1);}
            case "WATER_FLOW" -> addStatus(changes,src,"waterForce",c.param(0));
            case "PHANTOM_DARK" -> {int amount=src.anima*c.param(0);if(c.rarity()!=2)amount=Math.min(c.param(2),amount);addDefense(changes,src,amount);int spirits=(int)Arrays.stream(src.cards).filter(id->rules.byId.get(id).name().contains("灵剑")).count();if(spirits>=c.param(1))src.requestChase=true;}
            case "PHANTOM_LIGHT" -> addStatus(changes,src,"universalNext",1);
            case "PHANTOM_EXPLORE" -> {int old=src.cloud;src.cloud+=c.param(0)-1;change(changes,src,"cloudChain",old,src.cloud);addStatus(changes,src,"nextCloudChase",1);}
            case "PHANTOM_FIRE_COLLECT" -> {reduceLifeAndCap(changes,dst,c.param(1));if(src.fireActive()&&src.anima>=c.param(0)){addAnima(changes,src,-c.param(0));addStatus(changes,src,"doubleFireEarth",1);}}
            case "PHANTOM_WATER_WAVE" -> {addStatus(changes,src,"waterForce",c.param(0));addStatus(changes,src,"waterTwiceRounds",c.param(1));}
            case "PHANTOM_WOOD_REVIVE" -> {heal(changes,src,Math.min(c.param(1),Math.max(0,src.status("previousTurnHp")-src.hp)),true);if(src.woodActive()&&src.status("healingTotal")>0)addStatus(changes,src,"attackBonus",c.param(0));}
            case "PHANTOM_METAL_NEEDLE" -> {addStatus(changes,src,"sharpness",c.param(0));if(src.metalActive())addStatus(changes,src,"sharpnessFullRefund",1);}
            case "PHANTOM_FLOW" -> {var previous=rules.byId.get(src.cards[StarEffects.previous(src,src.slot)]);var next=rules.byId.get(src.cards[StarEffects.next(src,src.slot)]);int left=ElementalEffects.element(previous.name()),right=ElementalEffects.element(next.name());if(ElementalEffects.generates(src,left,right)||ElementalEffects.generates(src,right,left)){ElementalEffects.activateCard(changes,src,previous);ElementalEffects.activateCard(changes,src,next);src.requestChase=true;}int times=0;for(int i=1;i<=5;i++)times+=src.status(ElementalEffects.activationKey(i));addDefense(changes,src,times*c.param(0));}
            case "PHANTOM_EARTH_MOUNTAINS" -> {int amount=(src.def+1)/2;addDefense(changes,src,-amount);damage(changes,dst,amount);if(src.earthActive())addStatus(changes,src,"nextDefense",amount);}
            case "PHANTOM_RHINO" -> addStatus(changes,src,"swordToAnima",c.param(0));
            case "PHANTOM_FANG" -> {src.refundSword=true;addSword(changes,src,c.param(0));}
            case "PHANTOM_CLOUD_DANCE" -> addSword(changes,src,src.status("cardsUsed"));
            case "PHANTOM_RAGING_DEFENSE" -> addStatus(changes,src,"nextDefense",src.status("swordFormationCount")*c.param(0));
            case "PHANTOM_METAL_SEAL" -> {activate(changes,src,"metalActivation");activate(changes,src,"woodActivation");addStatus(changes,src,"healToSharpRounds",c.param(0));addStatus(changes,src,"sharpness",c.param(1));}
            case "PHANTOM_EARTH_FORMATION" -> {activate(changes,src,"earthActivation");activate(changes,src,"waterActivation");addStatus(changes,src,"waterDefenseLimit",c.param(1));if(c.rarity()==2)addStatus(changes,src,"waterDefenseUnlimited",1);addStatus(changes,src,"waterForce",c.param(0));}
            case "PHANTOM_DEFENSE_ANIMA" -> addAnima(changes,src,src.def/c.param(0));
            case "PHANTOM_FIRE_SEAL" -> {reduceLifeAndCap(changes,dst,c.param(1));addStatus(changes,src,"sharpToFireRounds",c.param(0));}
            case "PHANTOM_DILIGENT" -> addStatus(changes,src,"swordChaseCount",c.param(src.status("experience")>=c.param(1)?2:0));
            case "EARTH_ROCKFALL" -> {if(src.earthActive()){int value=c.param(0)+src.def;damage(changes,dst,value);damage(changes,src,value);}}
            default -> { }
        }
    }
    static List<Integer> generated(Fighter src,Rules.Card c){
        if(c.effect().equals("MIRROR_TIME"))return List.of(src.cards[StarEffects.next(src,src.slot)]);
        if(c.effect().equals("PHANTOM_DRAW")){int id=nextParameter(src);return id<0?List.of():List.of(id);}
        if(c.effect().equals("RANDOM_RAGING")){var result=new ArrayList<Integer>();for(int i=0;i<c.param(0);i++){int id=nextParameter(src);if(id>=0)result.add(id);}return result;}
        return List.of();
    }
    static boolean chase(Fighter src,Rules.Card c,Rules rules){return switch(c.effect()){
        case "METAL_METEOR" -> src.metalActive();
        case "LIFE_DESTROYER" -> ElementalEffects.elements(src,rules)<=2;
        default -> false;
    };}
}
