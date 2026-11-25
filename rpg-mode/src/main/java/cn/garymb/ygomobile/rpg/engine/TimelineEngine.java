package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgEvent;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Enforces the OCG timeline dimension across packs, promo events, cards, banlists,
 * rule versions, and AI opponents (1999-02-04 ~ 1999-12-31).
 * All date comparisons use ISO-8601 strings (YYYY-MM-DD), which sort lexicographically.
 */
public final class TimelineEngine {
    private TimelineEngine() {}

    /** Clamps a YYYY-MM-DD date string into the catalog's supported timeline range. */
    public static String clampDate(RpgCatalog catalog, String date) {
        String minD = (catalog != null && catalog.minTimelineDate != null) ? catalog.minTimelineDate : "1999-02-04";
        String maxD = (catalog != null && catalog.maxTimelineDate != null) ? catalog.maxTimelineDate : "1999-12-31";
        if (date == null || date.length() != 10) return minD;
        if (date.compareTo(minD) < 0) return minD;
        if (date.compareTo(maxD) > 0) return maxD;
        return date;
    }

    /** Returns true if releaseDate is on or before currentDate. */
    public static boolean isReleasedOnOrBefore(String releaseDate, String currentDate) {
        if (releaseDate == null || currentDate == null) return false;
        return releaseDate.compareTo(currentDate) <= 0;
    }

    /** Returns all OCG packs/boxes released on or before currentDate. */
    public static List<OcgPack> getAvailablePacks(RpgCatalog catalog, String currentDate) {
        List<OcgPack> available = new ArrayList<>();
        if (catalog == null || catalog.packs == null) return available;
        for (OcgPack pack : catalog.packs) {
            if (isReleasedOnOrBefore(pack.releaseDate, currentDate)) {
                available.add(pack);
            }
        }
        return available;
    }

    /** Returns all OCG promo/giveaway events unlocked on or before currentDate. */
    public static List<OcgEvent> getAvailableEvents(RpgCatalog catalog, String currentDate) {
        List<OcgEvent> available = new ArrayList<>();
        if (catalog == null || catalog.events == null) return available;
        for (OcgEvent ev : catalog.events) {
            if (isReleasedOnOrBefore(ev.releaseDate, currentDate)) {
                available.add(ev);
            }
        }
        return available;
    }

    /** Returns the latest OCG rule set whose effectiveDate <= currentDate (strictly timeline-driven). */
    public static OcgRuleSet getActiveRuleSet(RpgCatalog catalog, String currentDate) {
        OcgRuleSet best = null;
        if (catalog != null && catalog.rules != null) {
            for (OcgRuleSet r : catalog.rules) {
                if (isReleasedOnOrBefore(r.effectiveDate, currentDate)) {
                    if (best == null || r.effectiveDate.compareTo(best.effectiveDate) > 0) {
                        best = r;
                    }
                }
            }
        }
        if (best != null) return best;
        OcgRuleSet fallback = new OcgRuleSet();
        fallback.id = "ocg_official_1999";
        fallback.name = "1999.02.04 初代官方规则（公式ルール）";
        fallback.effectiveDate = "1999-02-04";
        fallback.description = "上级怪兽免祭品；每回合魔法/陷阱各限 1 张；先攻不抽卡。";
        fallback.tributeSummonEnabled = false;
        fallback.spellTrapOncePerTurnLimit = true;
        fallback.firstTurnDrawEnabled = false;
        fallback.deckOutCompareLp = true;
        fallback.ygoproDuelRule = 1;
        return fallback;
    }

    /** Returns the latest OCG banlist whose effectiveDate <= currentDate. */
    public static OcgBanlist getActiveBanlist(RpgCatalog catalog, String currentDate) {
        OcgBanlist best = null;
        if (catalog != null && catalog.banlists != null) {
            for (OcgBanlist list : catalog.banlists) {
                if (isReleasedOnOrBefore(list.effectiveDate, currentDate)) {
                    if (best == null || list.effectiveDate.compareTo(best.effectiveDate) > 0) {
                        best = list;
                    }
                }
            }
        }
        if (best != null) return best;
        OcgBanlist fallback = new OcgBanlist();
        fallback.id = "ocg_default_unrestricted";
        fallback.name = "无禁限（初始环境）";
        fallback.effectiveDate = "1999-02-04";
        fallback.description = "尚未生效任何禁限卡表，所有已发售卡最多 3 张。";
        return fallback;
    }

    /** Returns all unique card passcodes released on or before currentDate (packs + promo events). */
    public static Set<Integer> getReleasedCardIds(RpgCatalog catalog, String currentDate) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (OcgPack pack : getAvailablePacks(catalog, currentDate)) {
            if (pack.cards == null) continue;
            for (RpgCard c : pack.cards) {
                ids.add(c.id);
            }
        }
        for (OcgEvent ev : getAvailableEvents(catalog, currentDate)) {
            if (ev.rewardCards == null) continue;
            for (RpgCard c : ev.rewardCards) {
                ids.add(c.id);
            }
        }
        return ids;
    }

    /** Returns all unique released RpgCard entries on or before currentDate. */
    public static List<RpgCard> getReleasedCards(RpgCatalog catalog, String currentDate) {
        Set<Integer> ids = getReleasedCardIds(catalog, currentDate);
        List<RpgCard> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            RpgCard c = catalog.findCard(id);
            if (c != null) out.add(c);
        }
        return out;
    }

    public static boolean isCardReleased(RpgCatalog catalog, int cardId, String currentDate) {
        String earliest = catalog.getEarliestReleaseDate(cardId);
        return earliest != null && isReleasedOnOrBefore(earliest, currentDate);
    }

    /**
     * Maximum legal copies for a card at currentDate:
     * 0 if not yet released at currentDate; otherwise active OCG banlist limit (0/1/2/3).
     */
    public static int getMaxLegalCopies(RpgCatalog catalog, int cardId, String currentDate) {
        if (!isCardReleased(catalog, cardId, currentDate)) {
            return 0;
        }
        return getActiveBanlist(catalog, currentDate).getMaxCopies(cardId);
    }

    /** Returns sorted distinct milestone dates (pack releases + promo events + banlists + rule shifts + year-end). */
    public static List<String> getTimelineMilestones(RpgCatalog catalog) {
        TreeSet<String> dates = new TreeSet<>();
        if (catalog != null) {
            if (catalog.packs != null) {
                for (OcgPack p : catalog.packs) {
                    if (p.releaseDate != null && !p.releaseDate.isEmpty()) dates.add(p.releaseDate);
                }
            }
            if (catalog.events != null) {
                for (OcgEvent e : catalog.events) {
                    if (e.releaseDate != null && !e.releaseDate.isEmpty()) dates.add(e.releaseDate);
                }
            }
            if (catalog.banlists != null) {
                for (OcgBanlist b : catalog.banlists) {
                    if (b.effectiveDate != null && !b.effectiveDate.isEmpty()) dates.add(b.effectiveDate);
                }
            }
            if (catalog.rules != null) {
                for (OcgRuleSet r : catalog.rules) {
                    if (r.effectiveDate != null && !r.effectiveDate.isEmpty()) dates.add(r.effectiveDate);
                }
            }
            if (catalog.maxTimelineDate != null && !catalog.maxTimelineDate.isEmpty()) {
                dates.add(catalog.maxTimelineDate);
            }
        }
        return new ArrayList<>(dates);
    }

    /** Filters AI opponents unlocked on or before currentDate. */
    public static List<AiOpponent> getAvailableOpponents(List<AiOpponent> all, String currentDate) {
        List<AiOpponent> out = new ArrayList<>();
        if (all == null) return out;
        for (AiOpponent opp : all) {
            if (isReleasedOnOrBefore(opp.unlockDate, currentDate)) {
                out.add(opp);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
