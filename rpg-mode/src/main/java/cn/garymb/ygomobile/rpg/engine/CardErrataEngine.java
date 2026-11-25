package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import cn.garymb.ygomobile.rpg.model.OcgCardErrata;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Resolves the exact historical OCG effect version for any card at a given timeline date.
 * Automatically picks the latest revision whose effectiveDate <= currentDate without manual toggling.
 */
public final class CardErrataEngine {
    private CardErrataEngine() {}

    /**
     * Returns the active Revision for cardId at currentDate, or null if the card has no errata record.
     * If currentDate is earlier than the first revision's date, falls back to the earliest revision.
     */
    public static OcgCardErrata.Revision getActiveRevision(RpgCatalog catalog, int cardId, String currentDate) {
        if (catalog == null) return null;
        OcgCardErrata rec = catalog.findErrata(cardId);
        return getActiveRevision(rec, currentDate);
    }

    public static OcgCardErrata.Revision getActiveRevision(OcgCardErrata rec, String currentDate) {
        if (rec == null || rec.revisions == null || rec.revisions.isEmpty()) return null;
        OcgCardErrata.Revision best = null;
        for (OcgCardErrata.Revision rev : rec.revisions) {
            if (rev.effectiveDate != null && currentDate != null
                    && rev.effectiveDate.compareTo(currentDate) <= 0) {
                if (best == null || rev.effectiveDate.compareTo(best.effectiveDate) >= 0) {
                    best = rev;
                }
            }
        }
        return best != null ? best : rec.revisions.get(0);
    }

    /**
     * Returns the timeline-resolved Chinese effect description for a card at currentDate.
     * Falls back to card.descCn when the card has no errata record.
     */
    public static String getEffectiveDescCn(RpgCatalog catalog, RpgCard card, String currentDate) {
        if (card == null) return "";
        OcgCardErrata.Revision rev = getActiveRevision(catalog, card.id, currentDate);
        if (rev != null && rev.descCn != null && !rev.descCn.isEmpty()) {
            return rev.descCn;
        }
        return card.descCn != null ? card.descCn : "";
    }

    /** Returns true if the active revision at currentDate carries the given mechanical flag. */
    public static boolean hasFlag(RpgCatalog catalog, int cardId, String currentDate, String flag) {
        OcgCardErrata.Revision rev = getActiveRevision(catalog, cardId, currentDate);
        return rev != null && rev.hasFlag(flag);
    }

    /**
     * Returns true when cardId has a newer official revision strictly after currentDate,
     * meaning its effect at currentDate is pre-errata (differs from modern OCG).
     */
    public static boolean isPreErrataAt(RpgCatalog catalog, int cardId, String currentDate) {
        if (catalog == null || currentDate == null) return false;
        OcgCardErrata rec = catalog.findErrata(cardId);
        if (rec == null || rec.revisions == null || rec.revisions.size() < 2) return false;
        OcgCardErrata.Revision latest = rec.revisions.get(rec.revisions.size() - 1);
        return latest.effectiveDate != null && latest.effectiveDate.compareTo(currentDate) > 0;
    }

    /** Returns a compact UI badge like "1999.11.18 初版" or "1999.12.20 第2版" when the card has errata history. */
    public static String getVersionBadge(RpgCatalog catalog, int cardId, String currentDate) {
        OcgCardErrata rec = catalog != null ? catalog.findErrata(cardId) : null;
        if (rec == null || rec.revisions == null || rec.revisions.isEmpty()) return "";
        OcgCardErrata.Revision active = getActiveRevision(rec, currentDate);
        if (active == null) return "";
        String shortDate = active.effectiveDate != null ? active.effectiveDate.replace("-", ".") : "";
        boolean isLatest = active.versionIndex >= rec.revisions.size();
        if (active.versionIndex == 1) {
            return shortDate + " 初版";
        }
        return shortDate + (isLatest ? " 现行版" : " 第" + active.versionIndex + "版");
    }

    /**
     * Returns all released card passcodes whose effect at currentDate differs from modern OCG.
     * YGOMobile's ocgcore loads expansions/script/c<passcode>.lua ahead of scripts.zip, so this list
     * tells the host bridge which pre-errata Lua scripts should be activated for the selected timeline date.
     */
    public static List<Integer> getRequiredPreErrataScriptIds(RpgCatalog catalog, String currentDate) {
        List<Integer> out = new ArrayList<>();
        if (catalog == null) return out;
        Set<Integer> released = TimelineEngine.getReleasedCardIds(catalog, currentDate);
        for (int id : released) {
            if (isPreErrataAt(catalog, id, currentDate)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * Collects within-catalog errata change dates (versionIndex >= 2) inside [minTimelineDate, maxTimelineDate]
     * so milestone dates such as 1999-12-20 (Sangan's 1st errata) appear on the timeline bar.
     */
    public static Set<String> getErrataMilestonesInRange(RpgCatalog catalog) {
        TreeSet<String> dates = new TreeSet<>();
        if (catalog == null || catalog.errataRecords == null) return dates;
        String minD = catalog.minTimelineDate != null ? catalog.minTimelineDate : "1999-02-04";
        String maxD = catalog.maxTimelineDate != null ? catalog.maxTimelineDate : "1999-12-31";
        for (OcgCardErrata rec : catalog.errataRecords) {
            if (!rec.in1999Catalog || rec.revisions == null) continue;
            for (OcgCardErrata.Revision rev : rec.revisions) {
                if (rev.versionIndex >= 2 && rev.effectiveDate != null
                        && rev.effectiveDate.compareTo(minD) >= 0
                        && rev.effectiveDate.compareTo(maxD) <= 0) {
                    dates.add(rev.effectiveDate);
                }
            }
        }
        return dates;
    }
}
