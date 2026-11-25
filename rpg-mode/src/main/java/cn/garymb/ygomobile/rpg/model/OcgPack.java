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
    /** ISO-8601 date string YYYY-MM-DD (OCG Japan release date). */
    public String releaseDate;
    public int priceTokens;
    public int cardsPerPack = 5;
    public SlotRules slotRules = new SlotRules();
    public List<RpgCard> cards = new ArrayList<>();

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
