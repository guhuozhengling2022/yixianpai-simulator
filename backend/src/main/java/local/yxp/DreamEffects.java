package local.yxp;

import java.util.*;
import static local.yxp.Engine.*;
import static local.yxp.Models.*;

/** Dream cards branch on cultivation level, not merely the visible rarity label. */
final class DreamEffects {
    static boolean forceWound(Fighter src,Rules rules){
        if(rules==null||src.afterCard)return false;
        for(int slot:new int[]{StarEffects.previous(src,src.slot),StarEffects.next(src,src.slot)}){var card=rules.byId.get(src.cards[slot]);if(card.effect().equals("DREAM_INJECT")&&card.level()>=3)return true;}return false;
    }
    static int base(Rules.Card c){return c.baseId();}
    static void before(List<Change> changes,Fighter src,Fighter dst,Rules.Card c,Rules rules){
        if(src.isFormation(c)&&src.status("formationCloudSea")>0)addStatus(changes,src,"cloudSea",src.status("formationCloudSea"));
        if(src.isSword(c)&&src.status("swordQiPerSword")>0)addStatus(changes,src,"swordQi",src.status("swordQiPerSword"));
        if(c.name().contains("灵印")||c.name().contains("灵阵")){
            if(src.status("sealAgilityPermanent")>0)addStatus(changes,src,"agility",10);
            if(src.status("sealAgilityCount")>0){addStatus(changes,src,"sealAgilityCount",-1);addStatus(changes,src,"agility",10);}
        }
        if(ElementalEffects.element(c.name())>0)for(int slot:new int[]{StarEffects.previous(src,src.slot),StarEffects.next(src,src.slot)})if(rules.byId.get(src.cards[slot]).effect().equals("DREAM_HUN"))activate(changes,src,ElementalEffects.activationKey(ElementalEffects.element(c.name())));
        if(c.effect().equals("DREAM_MIXED_BREAK"))addAnima(changes,src,c.anima());
    }
    static List<Integer> attacks(Fighter src,Fighter dst,Rules.Card c,List<Integer> initial,Rules rules){
        var values=new ArrayList<Integer>(initial);int bonus=switch(c.effect()){
            case "DREAM_RETURN" -> c.level()>=3?src.status("swordCount")*c.param(1):0;
            case "DREAM_FIRE_SCORCH" -> dst.status("maxHpLossCount")*c.param(0);
            case "DREAM_WOOD_BUD" -> src.status("healingTotal")==0?0:c.level()<=2?1:src.status("healingTotal")/c.param(0);
            case "DREAM_WATER_SURGE" -> src.anima+src.status("waterForce");
            default -> 0;
        };
        for(int i=0;i<values.size();i++)values.set(i,values.get(i)+bonus);
        if(c.effect().equals("DREAM_CLOUD_ANIMA")){
            int extra=c.level()<=3?(src.anima>0?1:0):src.anima/(CloudEffects.linked(src)?c.param(2):c.param(0));
            values.clear();for(int i=0;i<Math.min(999,Math.max(1,c.attackCount())+extra);i++)values.add(c.attack());
        }
        if(c.effect().equals("DREAM_FIVE_STAB")&&c.level()>=4)values.clear();
        if(c.effect().equals("DREAM_MIXED_BREAK")){
            values.clear();int kinds=ElementalEffects.elements(src,rules);
            if(c.level()<=4){for(int i=0;i<Math.max(1,c.attackCount());i++)values.add(c.attack());for(int i=0;i<Math.min(kinds,c.param(1));i++)values.add(c.param(0));}
            else for(int i=0;i<Math.min(999,Math.max(1,c.attackCount())+kinds);i++)values.add(c.attack()+c.param(0)*src.status("elementalCardsUsed"));
        }
        return values;
    }
    static void after(List<Change> changes,Fighter src,Fighter dst,Rules.Card c,Rules rules){
        switch(c.effect()){
            case "DREAM_INJECT" -> addStatus(changes,src,c.level()<=3?"nextAttackBonus":"attackBonus",c.param(0));
            case "DREAM_CLOUD_SWORD" -> {addStatus(changes,src,"cloudSea",c.param(0));if(c.level()==5)addSword(changes,src,c.param(1)*(1+src.status("cloudTotal")));else if(CloudEffects.linked(src))addSword(changes,src,c.param(1)*(c.level()<=2?1:src.cloud));}
            case "DREAM_FANG" -> src.refundSword=true;
            case "DREAM_CLOUD_DANCE" -> {if(c.level()>=4)addStatus(changes,src,"ragingCount",src.sword/c.param(0)+(c.level()==5?1:0));}
            case "DREAM_RAGING_FIRST" -> {modifyMaxHp(changes,src,c.param(0));int amount=src.status("ragingCount")*c.param(1);loseHp(changes,dst,amount);heal(changes,src,amount);}
            case "DREAM_BIRDS" -> addStatus(changes,src,"swordQi",c.level()<=3?c.param(0):src.status("animaGained")/c.param(0));
            case "DREAM_ALL_SPIRIT" -> {addStatus(changes,src,"swordQi",c.param(0));if(c.level()>3){int amount=src.anima;addAnima(changes,src,-amount);addStatus(changes,src,"swordQi",amount);}}
            case "DREAM_RAGING_ZERO" -> {heal(changes,src,c.param(0));heal(changes,src,src.status("ragingCount")*c.param(1));if(c.level()>=4)addSword(changes,src,src.status("ragingCount"));}
            case "DREAM_RHINO" -> {addStatus(changes,src,"animaDefenseGain",c.param(0));addStatus(changes,src,"swordDefenseGain",c.param(1));}
            case "DREAM_CLOUD_EARTH" -> {addStatus(changes,src,"cloudSea",c.param(2));if(c.level()>=4)addStatus(changes,src,"moonRounds",c.param(1));int count=c.level()==5?src.status("cloudTotal"):src.cloud;addDefense(changes,src,count*c.param(0));}
            case "DREAM_RETURN" -> {if(c.level()>=3)addDefense(changes,src,src.status("swordCount")*c.param(1));}
            case "DREAM_SKY_FORMATION" -> {damage(changes,dst,c.param(0)+(c.level()<=3?CloudEffects.formations(src,rules):src.status("swordFormationCount"))*c.param(1));if(c.level()>=4&&CloudEffects.formations(src,rules)>=c.param(2))src.requestChase=true;}
            case "DREAM_PENG" -> {if(c.level()>=4&&src.anima>c.anima())addStatus(changes,src,"agility",10);}
            case "DREAM_EARTH_SWORD" -> {if(src.woundDamage>0)addStatus(changes,dst,"internalInjury",c.level()<=4?Math.min(c.param(0),src.woundDamage/c.param(1)):src.woundDamage/c.param(1));}
            case "DREAM_SOFT" -> {addStatus(changes,src,"defensePerTurn",c.param(0));if(c.level()>=4)addStatus(changes,src,"formationCloudSea",1);if(c.level()==5)addStatus(changes,src,"moonRounds",c.param(1));}
            case "DREAM_INTENT" -> {if(c.level()>=4){addStatus(changes,src,"doubleNextAttack",1);if(src.sword>=c.param(0)||src.status("attackBonus")>=c.param(0))src.requestChase=true;}}
            case "DREAM_DRAW" -> addStatus(changes,src,c.level()<=3?"swordQi":"swordQiPerSword",c.param(0));
            case "DREAM_RAGING_SECOND" -> {heal(changes,src,c.param(0));addStatus(changes,src,c.level()<=3?"ragingCount":"ragingPerTurn",c.param(1));}
            case "DREAM_METAL_FORMATION" -> {activate(changes,src,"metalActivation");addStatus(changes,src,"sharpness",c.param(0));if(c.level()>3)addStatus(changes,src,"chaseSharpness",c.param(1));}
            case "DREAM_HUN" -> {if(c.level()<5)damage(changes,dst,c.param(0));}
            case "DREAM_WATER_WAVE" -> {if(c.param(1)>0){modifyMaxHp(changes,src,c.param(1));heal(changes,src,c.param(1));}addStatus(changes,src,"waterForce",c.param(0));if(c.level()>=4)addStatus(changes,src,"doubleWaterPending",1);}
            case "DREAM_FIVE_STAB" -> addDefense(changes,src,(c.level()<=3?ElementalEffects.elements(src,rules):src.status("elementalCardsUsed"))*c.param(0));
            case "DREAM_WATER_SPRING" -> addStatus(changes,src,"waterForce",src.status("woundTotal")/c.param(0));
            case "DREAM_WOOD_WILLOW" -> {heal(changes,src,c.param(0));if(c.level()<=3)addStatus(changes,src,"nextAttackBonus",src.status("waterForce"));else{addStatus(changes,src,"attackBonus",Math.min(c.param(2),src.status("waterForce")/c.param(1)));if(src.status("waterForce")>0||src.status("attackBonus")>0)src.requestChase=true;}}
            case "DREAM_WOOD_FORMATION" -> {activate(changes,src,"woodActivation");if(c.level()<=4)addStatus(changes,src,"attackDrain",c.param(0));if(c.level()>=4)addStatus(changes,src,"woodThorns",c.param(1));if(c.level()==5){modifyMaxHp(changes,src,c.param(2));addStatus(changes,src,"attackToThorns",c.param(0));}}
            case "DREAM_FIRE_FLASH" -> {reduceLifeAndCap(changes,dst,c.param(0)+src.def/c.param(1));if(c.level()>=4&&src.status("defenseGained")>0)src.requestChase=true;}
            case "DREAM_METAL_IRON" -> {int extra=src.status("sharpnessGained")/c.param(0)+src.status("waterForceGained")/c.param(1);addDefense(changes,src,extra);if(c.level()>=4)addStatus(changes,src,"nextDefense",c.defense()+extra);}
            case "DREAM_METAL_EDGE" -> {if(src.woundDamage>0)addStatus(changes,src,"waterForce",c.level()<=3?c.param(0):src.woundDamage/c.param(0));}
            case "DREAM_EARTH_WALL" -> {if(c.level()<=3)addStatus(changes,src,"nextDefense",c.param(0));else addStatus(changes,src,"lossToNextDefense",1);}
            case "DREAM_TIAN_SUI" -> {modifyMaxHp(changes,src,c.param(0));heal(changes,src,c.param(0));addStatus(changes,src,c.level()<=4?"sealAgilityCount":"sealAgilityPermanent",c.level()<=4?c.param(1):1);}
            case "DREAM_FIRE_COLLECT" -> {reduceLifeAndCap(changes,dst,c.param(0)+c.param(1)*src.anima);if(!src.used[src.slot]){addStatus(changes,src,"consumeNext",1);addStatus(changes,dst,"consumeNext",1);}}
            case "DREAM_FIRE_FORMATION" -> {activate(changes,src,"fireActivation");reduceLifeAndCap(changes,dst,c.param(0));if(c.level()>=4)addStatus(changes,src,"chaseFireDefense",c.param(1));}
            case "DREAM_EARTH_CLIFF" -> {int amount=(src.def+1)/2;addDefense(changes,src,-amount);damage(changes,dst,amount);if(c.level()>=4){modifyMaxHp(changes,src,amount);heal(changes,src,amount);}}
            case "DREAM_WATER_SURGE" -> {if(c.level()>=4&&src.woundDamage>0){int amount=src.woundDamage/c.param(0);modifyMaxHp(changes,src,amount);heal(changes,src,amount);}}
            default -> { }
        }
    }
    static List<Integer> generated(Fighter src,Rules.Card c){
        if((c.effect().equals("DREAM_HUN")&&c.level()==5)||c.effect().equals("DREAM_MIXED_FORMATION")){int id=nextParameter(src);return id<0?List.of():List.of(id);}
        return List.of();
    }
}
