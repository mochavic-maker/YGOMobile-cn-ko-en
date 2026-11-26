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
    public String version = "1999-full-v1";
    public String minTimelineDate = "1999-02-04";
    public String maxTimelineDate = "1999-12-31";
    public CraftingRules craftingRules = new CraftingRules();
    public List<OcgRuleSet> rules = new ArrayList<>();
    public List<OcgBanlist> banlists = new ArrayList<>();
    public List<OcgPack> packs = new ArrayList<>();
    public List<OcgEvent> events = new ArrayList<>();
    public List<OcgCardErrata> errataRecords = new ArrayList<>();
    public List<OcgCanonDuel> canonDuels = new ArrayList<>();
    public List<RpgCard> canonCardCatalog = new ArrayList<>();

    private transient Map<Integer, RpgCard> cardById;
    private transient Map<Integer, RpgCard> canonCardById;
    private transient Map<Integer, String> earliestReleaseByCardId;
    private transient Map<Integer, List<CardPrinting>> printingsByCardId;
    private transient Map<Integer, OcgCardErrata> errataByCardId;

    private static final class CardPrinting {
        final String releaseDate;
        final Rarity rarity;

        CardPrinting(String releaseDate, Rarity rarity) {
            this.releaseDate = releaseDate;
            this.rarity = rarity;
        }
    }

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
        canonCardById = new LinkedHashMap<>();
        earliestReleaseByCardId = new HashMap<>();
        printingsByCardId = new HashMap<>();
        errataByCardId = new LinkedHashMap<>();
        if (packs != null) {
            for (OcgPack pack : packs) {
                indexCardList(pack.cards, pack.releaseDate);
            }
        }
        if (events != null) {
            for (OcgEvent ev : events) {
                indexCardList(ev.rewardCards, ev.releaseDate);
            }
        }
        if (canonCardCatalog != null) {
            for (RpgCard c : canonCardCatalog) {
                if (c != null && c.id > 0 && !canonCardById.containsKey(c.id)) {
                    canonCardById.put(c.id, c);
                }
            }
        }
        if (errataRecords != null) {
            for (OcgCardErrata rec : errataRecords) {
                if (rec != null && rec.cardId > 0) {
                    errataByCardId.put(rec.cardId, rec);
                }
            }
        }
    }

    private void indexCardList(List<RpgCard> list, String releaseDate) {
        if (list == null) return;
        for (RpgCard card : list) {
            String prevDate = earliestReleaseByCardId.get(card.id);
            boolean isEarlier = prevDate == null || (releaseDate != null && releaseDate.compareTo(prevDate) < 0);
            if (!cardById.containsKey(card.id) || isEarlier) {
                cardById.put(card.id, card);
            }
            if (isEarlier) {
                earliestReleaseByCardId.put(card.id, releaseDate);
            }
            List<CardPrinting> printings = printingsByCardId.get(card.id);
            if (printings == null) {
                printings = new ArrayList<>();
                printingsByCardId.put(card.id, printings);
            }
            printings.add(new CardPrinting(releaseDate, card.getRarityEnum()));
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

    /** Looks up a card in the OCG catalog first, then falls back to canon-duel exclusive card metadata. */
    public RpgCard findCardOrCanon(int cardId) {
        ensureIndexed();
        RpgCard c = cardById.get(cardId);
        if (c != null) return c;
        return canonCardById != null ? canonCardById.get(cardId) : null;
    }

    public Map<Integer, RpgCard> getAllCardsMap() {
        ensureIndexed();
        return Collections.unmodifiableMap(cardById);
    }

    public String getEarliestReleaseDate(int cardId) {
        ensureIndexed();
        return earliestReleaseByCardId.get(cardId);
    }

    /**
     * Returns the effective rarity of a card at the given OCG timeline date:
     * the lowest rarity among all printings released on or before {@code timelineDate}
     * (so future collector SCR promos never inflate crafting cost, while cheaper reprints
     * like EX Starter Box N printings naturally reduce cost once released).
     */
    public Rarity getEffectiveRarity(int cardId, String timelineDate) {
        ensureIndexed();
        List<CardPrinting> printings = printingsByCardId.get(cardId);
        if (printings == null || printings.isEmpty()) {
            return Rarity.N;
        }
        Rarity lowestReleased = null;
        Rarity lowestOverall = null;
        boolean filterByDate = timelineDate != null && !timelineDate.isEmpty();
        for (CardPrinting p : printings) {
            if (p == null || p.rarity == null) continue;
            if (lowestOverall == null || p.rarity.ordinal() < lowestOverall.ordinal()) {
                lowestOverall = p.rarity;
            }
            if (!filterByDate || (p.releaseDate != null && p.releaseDate.compareTo(timelineDate) <= 0)) {
                if (lowestReleased == null || p.rarity.ordinal() < lowestReleased.ordinal()) {
                    lowestReleased = p.rarity;
                }
            }
        }
        if (lowestReleased != null) return lowestReleased;
        return lowestOverall != null ? lowestOverall : Rarity.N;
    }

    public Rarity getCardRarity(int cardId) {
        return getEffectiveRarity(cardId, null);
    }

    public OcgCardErrata findErrata(int cardId) {
        ensureIndexed();
        return errataByCardId != null ? errataByCardId.get(cardId) : null;
    }

    public List<OcgCardErrata> getErrataRecords() {
        return errataRecords != null ? Collections.unmodifiableList(errataRecords) : Collections.emptyList();
    }

    public List<OcgCanonDuel> getCanonDuels() {
        return canonDuels != null ? Collections.unmodifiableList(canonDuels) : Collections.emptyList();
    }

    public OcgCanonDuel findCanonDuel(String duelId) {
        if (canonDuels == null || duelId == null) return null;
        for (OcgCanonDuel d : canonDuels) {
            if (d != null && duelId.equals(d.id)) return d;
        }
        return null;
    }

    public OcgPack findPack(String packId) {
        if (packs == null || packId == null) return null;
        for (OcgPack p : packs) {
            if (packId.equals(p.id)) return p;
        }
        return null;
    }

    public OcgEvent findEvent(String eventId) {
        if (events == null || eventId == null) return null;
        for (OcgEvent ev : events) {
            if (eventId.equals(ev.id)) return ev;
        }
        return null;
    }
}
