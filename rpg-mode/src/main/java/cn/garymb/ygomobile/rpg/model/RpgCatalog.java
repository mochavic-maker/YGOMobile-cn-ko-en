package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root OCG RPG catalog loaded from JSON (packs, historical banlists, crafting dust table).
 */
public class RpgCatalog {
    public String format = "OCG";
    public String version = "1.0-demo";
    public CraftingRules craftingRules = new CraftingRules();
    public List<OcgRuleSet> rules = new ArrayList<>();
    public List<OcgBanlist> banlists = new ArrayList<>();
    public List<OcgPack> packs = new ArrayList<>();

    private transient Map<Integer, RpgCard> cardById;
    private transient Map<Integer, String> earliestReleaseByCardId;
    private transient Map<Integer, Rarity> highestRarityByCardId;

    public static class CraftingRules {
        public Map<String, Integer> dismantleDust = new HashMap<>();
        public Map<String, Integer> craftCostDust = new HashMap<>();

        public int getDismantleDust(Rarity r) {
            Integer v = dismantleDust != null ? dismantleDust.get(r.code) : null;
            return v != null ? v : r.defaultDismantleDust;
        }

        public int getCraftCostDust(Rarity r) {
            Integer v = craftCostDust != null ? craftCostDust.get(r.code) : null;
            return v != null ? v : r.defaultCraftCostDust;
        }
    }

    public void buildIndexes() {
        cardById = new LinkedHashMap<>();
        earliestReleaseByCardId = new HashMap<>();
        highestRarityByCardId = new HashMap<>();
        if (packs == null) return;
        for (OcgPack pack : packs) {
            if (pack.cards == null) continue;
            for (RpgCard card : pack.cards) {
                if (!cardById.containsKey(card.id)) {
                    cardById.put(card.id, card);
                }
                String prevDate = earliestReleaseByCardId.get(card.id);
                if (prevDate == null || (pack.releaseDate != null && pack.releaseDate.compareTo(prevDate) < 0)) {
                    earliestReleaseByCardId.put(card.id, pack.releaseDate);
                }
                Rarity cur = card.getRarityEnum();
                Rarity prevR = highestRarityByCardId.get(card.id);
                if (prevR == null || cur.ordinal() > prevR.ordinal()) {
                    highestRarityByCardId.put(card.id, cur);
                }
            }
        }
    }

    private void ensureIndexed() {
        if (cardById == null) {
            buildIndexes();
        }
    }

    public RpgCard findCard(int cardId) {
        ensureIndexed();
        return cardById.get(cardId);
    }

    public Map<Integer, RpgCard> getAllCardsMap() {
        ensureIndexed();
        return Collections.unmodifiableMap(cardById);
    }

    public String getEarliestReleaseDate(int cardId) {
        ensureIndexed();
        return earliestReleaseByCardId.get(cardId);
    }

    public Rarity getCardRarity(int cardId) {
        ensureIndexed();
        Rarity r = highestRarityByCardId.get(cardId);
        return r != null ? r : Rarity.N;
    }

    public OcgPack findPack(String packId) {
        if (packs == null || packId == null) return null;
        for (OcgPack p : packs) {
            if (packId.equals(p.id)) return p;
        }
        return null;
    }
}
