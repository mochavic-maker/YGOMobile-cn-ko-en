package cn.garymb.ygomobile.rpg.engine;

import java.util.Iterator;
import java.util.List;

import cn.garymb.ygomobile.rpg.model.Rarity;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Handles card dismantling (card -> dust) and crafting (dust -> card),
 * enforcing timeline availability and keeping active decks consistent with ownership.
 */
public final class CraftingEngine {
    private CraftingEngine() {}

    public static class CraftResult {
        public final boolean success;
        public final String message;
        public final int dustDelta;
        public final int dustAfter;

        private CraftResult(boolean success, String message, int dustDelta, int dustAfter) {
            this.success = success;
            this.message = message;
            this.dustDelta = dustDelta;
            this.dustAfter = dustAfter;
        }

        public static CraftResult ok(String msg, int delta, int after) {
            return new CraftResult(true, msg, delta, after);
        }

        public static CraftResult error(String msg, int after) {
            return new CraftResult(false, msg, 0, after);
        }
    }

    /** Dismantles `quantity` copies of a card into crafting dust. */
    public static CraftResult dismantle(RpgProfile profile, RpgCatalog catalog, int cardId, int quantity) {
        if (profile == null || catalog == null || quantity <= 0) {
            return CraftResult.error("参数无效", profile != null ? profile.dust : 0);
        }
        int owned = profile.getOwnedCount(cardId);
        if (owned < quantity) {
            return CraftResult.error("拥有数量不足（当前拥有 " + owned + " 张）", profile.dust);
        }
        Rarity rarity = catalog.getCardRarity(cardId);
        int gain = catalog.craftingRules.getDismantleDust(rarity) * quantity;
        profile.addCard(cardId, -quantity);
        profile.dust += gain;

        // Trim active deck if it now references more copies than owned
        int remainingOwned = profile.getOwnedCount(cardId);
        trimDeckToMaxCopies(profile.activeDeckMain, cardId, remainingOwned);
        trimDeckToMaxCopies(profile.activeDeckExtra, cardId, remainingOwned);

        RpgCard card = catalog.findCard(cardId);
        String name = card != null ? card.getDisplayName() : String.valueOf(cardId);
        return CraftResult.ok("分解 " + name + " x" + quantity + "，获得 " + gain + " 尘埃", gain, profile.dust);
    }

    /** Dismantles all surplus copies beyond 3 for every card in the collection. */
    public static CraftResult dismantleAllSurplus(RpgProfile profile, RpgCatalog catalog) {
        int totalCards = 0;
        int totalGain = 0;
        for (Integer cardId : new java.util.ArrayList<>(profile.collection.keySet())) {
            int owned = profile.getOwnedCount(cardId);
            if (owned > 3) {
                int surplus = owned - 3;
                CraftResult r = dismantle(profile, catalog, cardId, surplus);
                if (r.success) {
                    totalCards += surplus;
                    totalGain += r.dustDelta;
                }
            }
        }
        if (totalCards == 0) {
            return CraftResult.error("没有超过 3 张的多余卡片可分解", profile.dust);
        }
        return CraftResult.ok("一键分解 " + totalCards + " 张多余卡，共获得 " + totalGain + " 尘埃",
                totalGain, profile.dust);
    }

    /** Crafts `quantity` copies of a released card using dust. */
    public static CraftResult craft(RpgProfile profile, RpgCatalog catalog, int cardId, int quantity) {
        if (profile == null || catalog == null || quantity <= 0) {
            return CraftResult.error("参数无效", profile != null ? profile.dust : 0);
        }
        RpgCard card = catalog.findCard(cardId);
        if (card == null) {
            return CraftResult.error("卡库中不存在该卡片: " + cardId, profile.dust);
        }
        if (!TimelineEngine.isCardReleased(catalog, cardId, profile.currentTimelineDate)) {
            String releaseDate = catalog.getEarliestReleaseDate(cardId);
            return CraftResult.error(
                    card.getDisplayName() + " 发售于 " + releaseDate + "，当前时间轴不可合成",
                    profile.dust);
        }
        int owned = profile.getOwnedCount(cardId);
        if (owned + quantity > PackOpeningEngine.MAX_OWNED_COPIES) {
            return CraftResult.error(
                    card.getDisplayName() + " 已拥有 " + owned + " 张，最多合成至 3 张",
                    profile.dust);
        }
        Rarity rarity = catalog.getCardRarity(cardId);
        int cost = catalog.craftingRules.getCraftCostDust(rarity) * quantity;
        if (profile.dust < cost) {
            return CraftResult.error("尘埃不足，需要 " + cost + "，当前仅有 " + profile.dust, profile.dust);
        }
        profile.dust -= cost;
        profile.addCard(cardId, quantity);
        return CraftResult.ok("合成 " + card.getDisplayName() + " x" + quantity + "，消耗 " + cost + " 尘埃",
                -cost, profile.dust);
    }

    private static void trimDeckToMaxCopies(List<Integer> deck, int cardId, int maxAllowed) {
        if (deck == null) return;
        int seen = 0;
        Iterator<Integer> it = deck.iterator();
        while (it.hasNext()) {
            Integer id = it.next();
            if (id != null && id == cardId) {
                seen++;
                if (seen > maxAllowed) {
                    it.remove();
                }
            }
        }
    }
}
