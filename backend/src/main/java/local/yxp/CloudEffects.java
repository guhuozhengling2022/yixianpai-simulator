package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

final class CloudEffects {
    static boolean linked(Fighter f){return f.cloud>0||f.alwaysCloud();}
    static int formations(Fighter f,Rules rules){return (int)Arrays.stream(f.cards).filter(id->f.isFormation(rules.byId.get(id))).count();}
    static void before(List<Change> changes,Fighter src,Rules.Card c){
        if(c.effect().equals("CLOUD_MOON")){int value=c.param(0)+Math.min(c.param(2),(src.sword+src.status("attackBonus"))*c.param(1));modifyMaxHp(changes,src,value);heal(changes,src,value);}
        if(c.effect().equals("RAGING_RED"))addStatus(changes,src,"attackBonus",Math.min(c.param(0),src.status("ragingCount")));
        if(c.effect().equals("PIVOT_FORMATION")){addAnima(changes,src,c.anima());addDefense(changes,src,c.defense());}
        if(Set.of("CLOUD_DOT","DRAW_ANIMA","MIRROR_FORMATION","DAWN_CLOUD").contains(c.effect())){
            if(c.anima()>0)addAnima(changes,src,c.anima());
            if(c.defense()>0)addDefense(changes,src,c.defense());
        }
        if(c.effect().equals("SPIRIT_ALL")){src.specialAttack=src.anima*c.param(0);addAnima(changes,src,-src.anima);}
    }
    static List<Integer> attacks(Fighter src,Rules.Card c,List<Integer> values,Rules rules){
        var result=new ArrayList<Integer>(values);
        int extra=switch(c.effect()){
            case "RAGING_FIRST" -> src.status("ragingCount")*c.param(0);
            case "WHITE_SPIRIT" -> src.anima*c.param(0);
            case "SPIRIT_ALL" -> src.specialAttack;
            case "DILIGENT_SWORD" -> src.status("experience")/c.param(0);
            case "ONE_SWORD" -> src.sword*(c.param(0)-1);
            case "COMBINED_FORMATION" -> (formations(src,rules)-1)*c.param(0);
            case "CLOUD_RIFT" -> src.sword+src.status("attackBonus")+(linked(src)?c.param(0):0);
            case "THOUSAND_FORMATION" -> src.def/c.param(0);
            case "REMAINING_CLOUD" -> src.specialAttack;
            default -> 0;
        };
        if(extra!=0)for(int i=0;i<result.size();i++)result.set(i,result.get(i)+extra);
        switch(c.effect()){
            case "DRAW_ANIMA" -> {result.clear();if(src.anima>c.anima())for(int i=0;i<c.param(1);i++)result.add(c.param(0));}
            case "CLOUD_DOT" -> {result.clear();result.add(src.anima);}
            case "MIRROR_FORMATION" -> {result.clear();if(src.def>0)result.add(src.def);}
            case "DAWN_CLOUD" -> {result.clear();if(linked(src))for(int i=0;i<c.param(1);i++)result.add(c.param(0));}
            case "RAGING_SECOND" -> {result.clear();for(int i=0;i<Math.min(999,1+src.status("ragingCount"));i++)result.add(c.attack());}
            case "SECRET_CLOUD" -> {result.clear();for(int i=0;i<Math.min(999,1+src.anima);i++)result.add(c.attack()*(linked(src)?2:1));}
            case "CLOUD_MOON" -> {if(!linked(src))result.clear();}
            case "PIVOT_FORMATION" -> {result.clear();result.add(c.attack()+src.anima/c.param(0));result.add(c.attack()+src.def/c.param(1));}
            default -> { }
        }
        return result;
    }
    static void after(List<Change> changes,Fighter src,Fighter dst,Rules.Card c,Rules rules){
        switch(c.effect()){
            case "RETURN_DEFENSE" -> {if(src.woundedHits>0)addStatus(changes,src,"nextDefense",c.param(0));}
            case "CLOUD_SWORD" -> {if(linked(src))addSword(changes,src,c.param(0));}
            case "CLOUD_HEAL" -> {if(linked(src))heal(changes,src,c.param(0));}
            case "DARK_SPIRIT" -> addDefense(changes,src,src.anima*c.param(0));
            case "SWORD_REFUND" -> {if(src.woundedHits>0)src.refundSword=true;}
            case "BREAK_ANIMA" -> {if(src.woundedHits>0)addAnima(changes,dst,-c.param(0));}
            case "SOFT_CLOUD" -> addStatus(changes,src,"cloudHeal",c.param(0));
            case "CLOUD_IGNORE" -> {if(linked(src)){int old=src.ignore;src.ignore+=c.param(0);change(changes,src,"ignoreDefense",old,src.ignore);}}
            case "EARTH_SWORD" -> {if(src.woundDamage>0)addDefense(changes,src,src.woundDamage);}
            case "SPIRIT_WOUND" -> {if(src.woundDamage>0)addAnima(changes,src,src.woundDamage);}
            case "CONVERT_SWORD" -> {addAnima(changes,src,src.sword);addSword(changes,src,-src.sword);}
            case "HALF_ANIMA" -> addStatus(changes,src,"halfAnima",c.param(0));
            case "RAGING_DEFENSE" -> addDefense(changes,src,src.status("ragingCount")*c.defense());
            case "CLOUD_ATTACK" -> {if(linked(src))addStatus(changes,src,"attackBonus",c.param(0));}
            case "KEEP_SWORD" -> addStatus(changes,src,"keepSwordCards",1);
            case "MOON_FORMATION" -> addStatus(changes,src,"moonRounds",c.param(0));
            case "INSPIRED_SWORD" -> addSword(changes,src,Math.min(src.anima,c.param(src.woundedHits>0?1:0)));
            case "CLOUD_DRAGON" -> {if(linked(src))addDefense(changes,src,c.param(0));if(src.woundedHits>0)src.requestChase=true;}
            case "CLOUD_WAVE" -> {if(linked(src))addDefense(changes,src,src.cloud*c.param(0));}
            case "FLYING_SPIRIT" -> addAnima(changes,src,src.woundedHits);
            case "SWORD_MULTIPLY" -> addSword(changes,src,src.sword*c.param(0)/100);
            case "FORMATION_ATTACK" -> addStatus(changes,src,"formationAttack",c.param(0));
            case "SWORD_PER_TURN" -> addStatus(changes,src,"swordPerTurn",c.param(0));
            case "GIANT_APE" -> addStatus(changes,src,"attackBonus",c.param(0));
            case "STEP_CLOUD" -> addStatus(changes,src,"cloudStep",c.param(0));
            case "DOUBLE_RAGING" -> addStatus(changes,src,"doubleRaging",1);
            case "COMBINED_FORMATION" -> addDefense(changes,src,(formations(src,rules)-1)*c.param(1));
            case "CLOUD_ANIMA" -> {if(linked(src))addAnima(changes,src,src.cloud);}
            case "SPIRIT_DISCOUNT" -> addStatus(changes,src,"spiritDiscount",c.param(0));
            case "RAGING_LIFESTEAL" -> addStatus(changes,src,"ragingLifesteal",c.param(0));
            case "ANIMA_DEFENSE" -> {addStatus(changes,src,"animaDefenseGain",c.param(0));addStatus(changes,src,"animaDefenseLoss",c.param(1));}
            case "DAN_SPIRIT" -> {if(src.anima>0){addAnima(changes,src,-1);src.pendingAttack=c.param(0);}}
            case "LEOPARD_SPIRIT" -> {addStatus(changes,src,"swordQi",c.param(0));addStatus(changes,src,"agility",Math.min(c.param(2),(src.anima+src.status("swordQi"))*c.param(1)));}
            case "SWIFT_SWORD" -> {if(!src.used[src.slot])addSword(changes,src,c.param(0));if(src.sword>0)src.requestChase=true;}
            case "SHADOW_BARRIER" -> addDefense(changes,src,src.status("totalAttacks")*c.param(0));
            case "EXTREME_SOFT" -> {modifyMaxHp(changes,src,c.param(0));addStatus(changes,src,"cloudHeal",c.param(1));}
            case "EXTREME_DARK" -> addDefense(changes,src,src.anima*c.param(0));
            case "CHASE_WIND" -> {addStatus(changes,src,"cloudSea",c.param(0));int old=src.cloud;src.cloud+=c.param(1)-1;change(changes,src,"cloudChain",old,src.cloud);}
            case "EXTREME_CONVERT" -> {int value=Math.min(src.sword,c.param(0));addAnima(changes,src,value);addSword(changes,src,-value);}
            case "CHASE_SHADOW" -> {addStatus(changes,src,"attackBonus",c.param(0));if(src.status("chaseCount")>0)src.requestChase=true;}
            case "ELEPHANT_SPIRIT" -> addStatus(changes,src,"attackBonus",c.param(0));
            case "SPIRIT_SEA" -> {addStatus(changes,src,"cloudSea",c.param(0));if(linked(src)){int old=src.cloud;src.cloud+=c.param(1)-1;change(changes,src,"cloudChain",old,src.cloud);}}
            case "FLOATING_CLOUD" -> {addStatus(changes,src,"cloudSea",c.param(0));if(src.slot%2==0){int value=c.param(1)+src.anima*c.param(3),actual=Math.min(value,src.maxHp-src.hp);heal(changes,src,actual);addDefense(changes,src,value-actual);}else addStatus(changes,src,"agility",c.param(2));}
            case "SHADOW_LIGHT" -> addDefense(changes,src,Math.min(c.param(1),(src.anima+src.sword)*c.param(0)));
            default -> { }
        }
    }
    static boolean chase(Fighter src,Rules.Card c){return switch(c.effect()){
        case "CLOUD_CHASE" -> linked(src);
        case "PENG_SPIRIT" -> src.anima>c.param(0);
        default -> false;
    };}
}
