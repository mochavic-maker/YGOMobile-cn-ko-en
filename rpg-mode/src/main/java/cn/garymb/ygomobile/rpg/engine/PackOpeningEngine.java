package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.Rarity;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Simulates OCG booster pack opening with slot-based rarity odds and pity protection.
 */
public class PackOpeningEngine {

    public static class PulledCard {
        public final RpgCard card;
        public final boolean isNew;
        public final int ownedAfter;

        public PulledCard(RpgCard card, boolean isNew, int ownedAfter) {
            this.card = card;
            this.isNew = isNew;
            this.ownedAfter = ownedAfter;
        }
    }

    public static class PackOpenResult {
        public final boolean success;
        public final String errorMessage;
        public final List<PulledCard> pulledCards;
        public final int tokensSpent;
        public final int tokensRemaining;

        private PackOpenResult(boolean success, String errorMessage,
                               List<PulledCard> pulledCards, int tokensSpent, int tokensRemaining) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.pulledCards = pulledCards;
            this.tokensSpent = tokensSpent;
            this.tokensRemaining = tokensRemaining;
        }

        public static PackOpenResult error(String msg, int tokensRemaining) {
            return new PackOpenResult(false, msg, new ArrayList<>(), 0, tokensRemaining);
        }

        public static PackOpenResult ok(List<PulledCard> pulled, int spent, int remaining) {
            return new PackOpenResult(true, null, pulled, spent, remaining);
        }
    }

    private final Random random;

    public PackOpeningEngine() {
        this(new Random());
    }

    public PackOpeningEngine(Random random) {
        this.random = random;
    }

    public PackOpenResult openPacks(RpgProfile profile, OcgPack pack, int packCount) {
        if (profile == null || pack == null || packCount <= 0) {
            return PackOpenResult.error("参数无效", profile != null ? profile.tokens : 0);
        }
        if (!TimelineEngine.isReleasedOnOrBefore(pack.releaseDate, profile.currentTimelineDate)) {
            return PackOpenResult.error(
                    "该卡包发售于 " + pack.releaseDate + "，晚于当前时间轴 " + profile.currentTimelineDate,
                    profile.tokens);
        }
        int totalCost = pack.priceTokens * packCount;
        if (profile.tokens < totalCost) {
            return PackOpenResult.error("代币不足，需要 " + totalCost + "，当前仅有 " + profile.tokens,
                    profile.tokens);
        }

        List<RpgCard> commons = pack.getCardsByRarity(Rarity.N);
        List<RpgCard> rares = pack.getCardsByRarity(Rarity.R);
        List<RpgCard> supers = pack.getCardsByRarity(Rarity.SR);
        List<RpgCard> ultras = pack.getCardsByRarity(Rarity.UR);
        List<RpgCard> secrets = pack.getCardsByRarity(Rarity.SCR);
        if (pack.cards == null || pack.cards.isEmpty()) {
            return PackOpenResult.error("卡包卡池为空", profile.tokens);
        }

        profile.tokens -= totalCost;
        List<PulledCard> allPulled = new ArrayList<>(packCount * Math.max(1, pack.cardsPerPack));

        for (int p = 0; p < packCount; p++) {
            if (pack.fixedContents) {
                for (RpgCard c : pack.cards) {
                    int qty = Math.max(1, c.quantity);
                    for (int q = 0; q < qty; q++) {
                        allPulled.add(recordPull(profile, c));
                    }
                }
            } else {
                Set<Integer> usedInPack = new HashSet<>();
                int commonSlots = Math.min(pack.slotRules.guaranteedCommonSlots, pack.cardsPerPack);

                // Common slots (e.g., 4 in Vol., 2 in Carddass Booster, 0 in Premium Pack)
                for (int i = 0; i < commonSlots; i++) {
                    RpgCard c = pickFromPool(commons.isEmpty() ? pack.cards : commons, usedInPack);
                    allPulled.add(recordPull(profile, c));
                }

                // Weighted rare/foil slot(s)
                int extraSlots = Math.max(0, pack.cardsPerPack - commonSlots);
                for (int i = 0; i < extraSlots; i++) {
                    boolean pityHit = pack.slotRules.pityRareEveryPacks > 0
                            && (profile.packsSinceLastRare + 1) >= pack.slotRules.pityRareEveryPacks;
                    Rarity rolled = rollSlotRarity(pack.slotRules.fifthSlotWeights, pityHit);
                    List<RpgCard> pool = poolForRarity(rolled, commons, rares, supers, ultras, secrets, pack.cards);
                    RpgCard c = pickFromPool(pool, usedInPack);
                    if (pack.slotRules.pityRareEveryPacks > 0) {
                        if (c.getRarityEnum().ordinal() >= Rarity.R.ordinal()) {
                            profile.packsSinceLastRare = 0;
                        } else {
                            profile.packsSinceLastRare++;
                        }
                    }
                    allPulled.add(recordPull(profile, c));
                }
            }

            Integer opened = profile.packsOpened.get(pack.id);
            profile.packsOpened.put(pack.id, (opened == null ? 0 : opened) + 1);
        }

        return PackOpenResult.ok(allPulled, totalCost, profile.tokens);
    }

    private PulledCard recordPull(RpgProfile profile, RpgCard card) {
        int before = profile.getOwnedCount(card.id);
        profile.addCard(card.id, 1);
        return new PulledCard(card, before == 0, before + 1);
    }

    private Rarity rollSlotRarity(Map<String, Integer> weights, boolean forceAtLeastRare) {
        int wN = forceAtLeastRare ? 0 : weightOf(weights, "N", 74);
        int wR = weightOf(weights, "R", 16);
        int wSR = weightOf(weights, "SR", 7);
        int wUR = weightOf(weights, "UR", 3);
        int wSCR = weightOf(weights, "SCR", 0);
        int total = wN + wR + wSR + wUR + wSCR;
        if (total <= 0) return forceAtLeastRare ? Rarity.R : Rarity.N;

        int roll = random.nextInt(total);
        if (roll < wSCR) return Rarity.SCR;
        roll -= wSCR;
        if (roll < wUR) return Rarity.UR;
        roll -= wUR;
        if (roll < wSR) return Rarity.SR;
        roll -= wSR;
        if (roll < wR) return Rarity.R;
        return Rarity.N;
    }

    private int weightOf(Map<String, Integer> weights, String key, int defVal) {
        if (weights == null || !weights.containsKey(key) || weights.get(key) == null) return defVal;
        return Math.max(0, weights.get(key));
    }

    private List<RpgCard> poolForRarity(Rarity r, List<RpgCard> n, List<RpgCard> rare,
                                        List<RpgCard> sr, List<RpgCard> ur, List<RpgCard> scr,
                                        List<RpgCard> fallback) {
        switch (r) {
            case SCR: return !scr.isEmpty() ? scr : (!ur.isEmpty() ? ur : fallback);
            case UR:  return !ur.isEmpty() ? ur : (!sr.isEmpty() ? sr : fallback);
            case SR:  return !sr.isEmpty() ? sr : (!rare.isEmpty() ? rare : fallback);
            case R:   return !rare.isEmpty() ? rare : fallback;
            default:  return !n.isEmpty() ? n : fallback;
        }
    }

    private RpgCard pickFromPool(List<RpgCard> pool, Set<Integer> usedInPack) {
        List<RpgCard> fresh = new ArrayList<>();
        for (RpgCard c : pool) {
            if (!usedInPack.contains(c.id)) {
                fresh.add(c);
            }
        }
        List<RpgCard> candidates = fresh.isEmpty() ? pool : fresh;
        RpgCard chosen = candidates.get(random.nextInt(candidates.size()));
        usedInPack.add(chosen.id);
        return chosen;
    }
}
