package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Element-specific effects; common arithmetic and lifecycle stay in Engine. */
final class ElementalEffects {
    static final String[] NAMES={"","金灵","水灵","木灵","火灵","土灵"};
    static final String[] ACTIVATION={"","metalActivation","waterActivation","woodActivation","fireActivation","earthActivation"};
    static int element(String name){for(int i=1;i<=5;i++)if(name.contains(NAMES[i]))return i;return 0;}
    static String activationKey(int element){return ACTIVATION[element];}
    static boolean generates(int previous,int next){return previous>0&&next==previous%5+1;}
    static boolean generates(Fighter src,int previous,int next){return generates(previous,next)||(TalentEffects.has(src,137)&&previous>0&&next>0&&((previous==4)!=(next==4)));}
    static boolean active(Fighter src,int element){int previous=src.status("lastElement");return src.status("dragonHorse")>0||TalentEffects.has(src,278)||src.status(ACTIVATION[element])>0||previous==element||generates(src,previous,element);}
    static int activeCount(Fighter src){int result=0;for(int i=1;i<=5;i++)if(src.status(ACTIVATION[i])>0)result++;return result;}
    static int elements(Fighter src,Rules rules){
        var names=new HashSet<Integer>();int extra=0;
        for(int id:src.cards){var c=rules.byId.get(id);int element=element(c.name());if(element>0)names.add(element);if(c.effect().equals("MIXED_SPIRIT"))extra+=c.param(0);}
        return (TalentEffects.has(src,278)?5:names.size())+extra;
    }
    static void activateCard(List<Change> changes,Fighter src,Rules.Card card){int element=element(card.name());if(element>0)activate(changes,src,ACTIVATION[element]);}
    static void before(List<Change> changes,Fighter src,Fighter dst,Rules.Card c,Rules rules){
        if(c.effect().equals("EXTREME_WOOD_PATROL"))addStatus(changes,src,"woodThorns",c.param(0));
        if(c.effect().equals("WOOD_SPRING")&&src.woodActive())addStatus(changes,src,"springRounds",c.param(1));
        if(c.effect().equals("WATER_RAIN")){
            addStatus(changes,src,"waterForce",c.param(0));modifyMaxHp(changes,src,c.param(1));heal(changes,src,c.param(1));
        }
        if(c.effect().equals("WOOD_BURN")){
            if(src.woodActive())heal(changes,src,c.param(0));
            src.specialAttack=0;
            if(src.fireActive()){
                src.specialAttack=(src.hp+1)/2;
                loseHp(changes,src,src.specialAttack);
            }
        }
    }
    static List<Integer> attacks(Fighter src,Fighter dst,Rules.Card c,List<Integer> values,Rules rules){
        int extra=switch(c.effect()){
            case "FIVE_STAB" -> activeCount(src)>0?c.param(0):0;
            case "MIXED_BREAK" -> elements(src,rules)*c.param(0);
            case "ELEMENTAL_STRIKE" -> src.anima;
            case "EXTREME_ELEMENTAL_STRIKE" -> src.anima*c.param(0);
            case "EARTH_SHATTER" -> src.earthActive()&&(src.def>0||dst.def>0)?c.param(0):0;
            case "EARTH_SAND" -> src.earthActive()?src.status("defenseLost")/c.param(0):0;
            case "FIRE_BURN" -> src.fireActive()?dst.status("maxHpLost")/c.param(0):0;
            default -> 0;
        };
        var result=new ArrayList<Integer>();for(int value:values)result.add(value+extra);
        if(c.effect().equals("WOOD_VINES")&&src.woodActive())for(int i=0;i<Math.min(c.param(1),src.status("healingTotal")/c.param(0));i++)result.add(c.attack());
        if(Set.of("FIRE_SPARK","FIRE_RED").contains(c.effect())&&src.fireActive())result.add(c.param(0));
        if(c.effect().equals("WOOD_BURN")){result.clear();if(src.fireActive())result.add(c.param(1)+src.specialAttack);}
        if(c.effect().equals("WATER_RAIN")){result.clear();if(src.waterActive())result.add(c.attack()+src.status("waterForce"));if(src.woodActive())result.add(c.attack()+src.anima);}
        if(c.effect().equals("ELEMENTAL_BURST")){result.clear();for(boolean active:new boolean[]{src.woodActive(),src.fireActive(),src.earthActive(),src.metalActive(),src.waterActive()})if(active)result.add(c.attack());}
        return result;
    }
    static void after(List<Change> changes,Fighter src,Fighter dst,Rules.Card c,Rules rules){
        switch(c.effect()){
            case "FIRE_SEAL" -> {reduceLifeAndCap(changes,dst,c.param(0));activate(changes,src,"fireActivation");}
            case "FIRE_COLLECT" -> {if(src.fireActive())reduceLifeAndCap(changes,dst,c.param(0));}
            case "FIRE_FORMATION" -> {reduceLifeAndCap(changes,dst,c.param(0));addStatus(changes,src,"fireFormation",c.param(1));}
            case "FIRE_BLAST", "FIRE_EXTREME_BLAST" -> {addStatus(changes,src,"attackBonus",c.param(0));if(src.fireActive())reduceLifeAndCap(changes,dst,c.param(1)+src.status("attackBonus"));}
            case "FIRE_FLASH" -> modifyMaxHp(changes,dst,-c.param(0));
            case "FIRE_SCORCH" -> {if(src.fireActive())modifyMaxHp(changes,dst,-src.woundDamage*c.param(0));}
            case "FIRE_WILDFIRE" -> {if(src.fireActive())modifyMaxHp(changes,dst,-Math.max(0,dst.maxHp-dst.hp+c.param(0)));}
            case "FIRE_SECRET" -> {reduceLifeAndCap(changes,dst,c.param(0));activate(changes,src,"fireActivation");}
            case "EARTH_SEAL" -> activate(changes,src,"earthActivation");
            case "EARTH_FORMATION" -> addStatus(changes,src,"earthFormation",c.param(0));
            case "EARTH_MOUNTAINS" -> {if(src.earthActive())addStatus(changes,src,"nextDefense",c.param(0));}
            case "EARTH_DUST" -> {if(src.earthActive()){int value=Math.abs(src.maxHp-dst.maxHp)/c.param(0);addDefense(changes,src,value);addStatus(changes,src,"nextDefense",value);}}
            case "EARTH_WALL" -> {if(src.earthActive())addDefense(changes,src,src.status("defenseLost")/c.param(0));}
            case "EARTH_QUAKE" -> {if(src.earthActive()){int old=dst.anima;dst.anima/=2;change(changes,dst,"anima",old,dst.anima);old=dst.def;dst.def/=2;change(changes,dst,"defense",old,dst.def);}}
            case "EARTH_SECRET" -> {addStatus(changes,src,"nextDefense",c.param(0));activate(changes,src,"earthActivation");}
            case "METAL_PIERCE" -> {if(src.metalActive()){int old=src.ignore;src.ignore+=c.param(0);change(changes,src,"ignoreDefense",old,src.ignore);}}
            case "METAL_IRON" -> {if(src.metalActive())addStatus(changes,src,"iron",c.param(1));}
            case "METAL_SECRET" -> {addStatus(changes,src,"sharpness",c.param(0));activate(changes,src,"metalActivation");}
            case "WATER_NURTURE" -> {if(src.waterActive())heal(changes,src,c.param(0));}
            case "WATER_WOOD" -> {if(src.waterActive()){int old=src.anima;src.anima+=c.param(0);change(changes,src,"anima",old,src.anima);}if(src.woodActive())heal(changes,src,src.anima*c.param(1));}
            case "METAL_WATER" -> {if(src.metalActive())addStatus(changes,src,"sharpness",c.param(0));if(src.waterActive()){int value=(src.status("sharpness")+1)/2;addStatus(changes,src,"sharpness",-value);addStatus(changes,src,"waterForce",value);}}
            case "EARTH_METAL" -> {if(src.earthActive()){addDefense(changes,src,c.param(0));int value=src.def;addDefense(changes,src,-value);addDefense(changes,src,value);}if(src.metalActive())addStatus(changes,src,"sharpness",src.status("defenseLost")/c.param(1));}
            case "FIRE_EARTH" -> {if(src.fireActive())reduceLifeAndCap(changes,dst,c.param(0));if(src.earthActive())addDefense(changes,src,c.param(1)+dst.status("maxHpLost")/2);}
            case "WATER_HIDE" -> {modifyMaxHp(changes,src,c.param(0));heal(changes,src,c.param(0));if(src.waterActive())addStatus(changes,src,"waterHide",c.param(1));}
            case "WATER_SECRET" -> {addStatus(changes,src,"waterForce",c.param(0));activate(changes,src,"waterActivation");}
            case "WOOD_THORN" -> {if(src.woodActive())heal(changes,src,src.woundDamage/c.param(0));}
            case "EARTH_CLIFF" -> {if(src.earthActive())addStatus(changes,src,"cliff",c.param(0));}
            case "EARTH_RESTORE" -> {if(src.earthActive())addStatus(changes,src,"restoreDefense",c.param(0));}
            case "METAL_SILENCE" -> {if(src.metalActive())addStatus(changes,dst,"noHealing",c.param(0));}
            case "WATER_SPRING" -> {if(src.waterActive())addStatus(changes,src,"springAttacks",c.param(0));}
            case "WATER_DRAGON" -> {addStatus(changes,src,"waterForce",c.param(0));if(src.waterActive())addStatus(changes,dst,"skipTurns",1);}
            case "WOOD_BRANCH" -> {if(src.woodActive())addStatus(changes,src,"branchDrain",c.param(0));}
            case "WATER_WIND" -> {addStatus(changes,src,"waterForce",c.param(0));modifyMaxHp(changes,src,c.param(1));heal(changes,src,c.param(1));if(src.waterActive())addStatus(changes,src,"agility",Math.min(c.param(2),(src.anima+src.status("waterForce"))*2));}
            case "FIRE_MERIDIANS" -> {int gain=Math.max(0,c.param(0)-src.status("attackBonus"));addStatus(changes,src,"attackBonus",gain);modifyMaxHp(changes,src,-gain*c.param(1));if(src.fireActive())reduceLifeAndCap(changes,dst,src.status("attackBonus")*c.param(2));}
            case "FIRE_ASH" -> reduceLifeAndCap(changes,dst,c.param(0)+(src.fireActive()?src.status("attackBonus")*c.param(1):0)+(src.earthActive()?src.def/c.param(2):0)+(src.metalActive()?src.status("sharpness")/c.param(3):0));
            case "EARTH_STEP" -> {addStatus(changes,src,"dustStep",1);if(src.earthActive())addStatus(changes,src,"agility",c.param(0));}
            case "HUN_SEAL" -> addStatus(changes,src,"activateNext",c.param(0));
            case "MIXED_GUARD" -> {if(activeCount(src)>=2){modifyMaxHp(changes,src,c.param(0));heal(changes,src,c.param(0));}}
            case "MIXED_FORMATION" -> addStatus(changes,src,"mixedChase",elements(src,rules)+c.param(0));
            case "ELEMENTAL_NO_ATTACK" -> {if(TalentEffects.has(src,278)||elements(src,rules)==1)addStatus(changes,src,"elementalNoAttack",c.param(0));}
            case "ACTIVATION_DAMAGE" -> {damage(changes,dst,c.param(0));addStatus(changes,src,"activationDamage",c.param(1));}
            case "EXTREME_ACTIVATION_DAMAGE" -> addStatus(changes,src,"activationDamage",c.param(0));
            case "EXTREME_WATER_FORMATION" -> addStatus(changes,src,"waterFormation",c.param(0));
            case "EXTREME_METAL_FORMATION" -> {activate(changes,src,"metalActivation");addStatus(changes,src,"metalFormation",c.param(0));}
            case "MIXED_SPIRIT" -> {addAnima(changes,src,elements(src,rules));activateCard(changes,src,rules.byId.get(src.cards[StarEffects.previous(src,src.slot)]));activateCard(changes,src,rules.byId.get(src.cards[StarEffects.next(src,src.slot)]));}
            case "TWIN_SEAL" -> {var first=rules.byId.get(src.cards[StarEffects.next(src,src.slot)]);var second=rules.byId.get(src.cards[StarEffects.next(src,StarEffects.next(src,src.slot))]);activateCard(changes,src,first);activateCard(changes,src,second);if(generates(src,element(first.name()),element(second.name())))addDefense(changes,src,c.param(1));}
            case "ELEMENTAL_FLOW" -> {var previous=rules.byId.get(src.cards[StarEffects.previous(src,src.slot)]);var next=rules.byId.get(src.cards[StarEffects.next(src,src.slot)]);if(generates(src,element(previous.name()),element(next.name())))activateCard(changes,src,next);}
            default -> { }
        }
    }
    static List<Integer> generated(Fighter src,Rules.Card c,Rules rules){
        if(c.effect().equals("ELEMENTAL_BLOSSOM"))return List.of(7000003+c.rarity()*10000,7000009+c.rarity()*10000,7000011+c.rarity()*10000,7000001+c.rarity()*10000,7000006+c.rarity()*10000);
        if(c.effect().equals("ELEMENTAL_FLOW")){
            var previous=rules.byId.get(src.cards[StarEffects.previous(src,src.slot)]);var next=rules.byId.get(src.cards[StarEffects.next(src,src.slot)]);
            if(generates(src,element(previous.name()),element(next.name())))return List.of(next.rarity()>c.rarity()?next.baseId()+c.rarity()*10000:next.id());
        }

        return List.of();
    }
    static boolean chase(Fighter src,Rules.Card card){
        return switch(card.effect()){
            case "FIRE_FLASH" -> src.fireActive();
            case "EARTH_CHASE" -> src.earthActive();
            case "WOOD_PATROL", "EXTREME_WOOD_PATROL" -> src.woodActive()&&src.status("healingTotal")>=card.param(0);
            case "MIXED_ESCAPE" -> activeCount(src)>=2;
            default -> false;
        };
    }
}
