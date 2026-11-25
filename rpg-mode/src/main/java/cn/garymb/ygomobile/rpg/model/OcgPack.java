package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OCG booster pack definition with release date, price, slot rules, and card pool.
 */
public class OcgPack {
    public String id;
    public String code;
    public String name;
    public String nameJp;
    /** BOOSTER_PACK | VENDING_MACHINE | STARTER_BOX | LIMITED_PACK | LIMITED_FIXED */
    public String productType = "BOOSTER_PACK";
    /** True for pre-constructed Starter Boxes & fixed limited packs (no RNG). */
    public boolean fixedContents = false;
    public String description;
    /** ISO-8601 date string YYYY-MM-DD (OCG Japan release date). */
    public String releaseDate;
    /** Token price directly matching historical Japanese Yen (JPY) retail price. */
    public int priceTokens;
    public int cardsPerPack = 5;
    public SlotRules slotRules = new SlotRules();
    public List<RpgCard> cards = new ArrayList<>();

    public String getProductTypeLabelCn() {
        if (productType == null) return "常规补充包";
        switch (productType) {
            case "VENDING_MACHINE": return "Carddass 贩卖机";
            case "STARTER_BOX": return "固定预组盒";
            case "LIMITED_PACK": return "限定抽卡包";
            case "LIMITED_FIXED": return "限定固定包";
            default: return "常规补充包";
        }
    }

    public static class SlotRules {
        public String description;
        public int guaranteedCommonSlots = 4;
        public Map<String, Integer> fifthSlotWeights = new HashMap<>();
        public int pityRareEveryPacks = 5;
    }

    public List<RpgCard> getCardsByRarity(Rarity target) {
        List<RpgCard> list = new ArrayList<>();
        if (cards == null) return list;
        for (RpgCard card : cards) {
            if (card.getRarityEnum() == target) {
                list.add(card);
            }
        }
        return list;
    }
}
