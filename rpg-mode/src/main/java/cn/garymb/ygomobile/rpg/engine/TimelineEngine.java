package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgCanonDuel;
import cn.garymb.ygomobile.rpg.model.OcgEvent;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Enforces the OCG timeline dimension across packs, promo events, cards, banlists,
 * rule versions, AI opponents, and canon duels (1999-02-04 ~ 1999-12-31).
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

    /** Returns ONLY OCG promo/giveaway events scheduled on the exact {@code currentDate}. */
    public static List<OcgEvent> getEventsOnDate(RpgCatalog catalog, String currentDate) {
        List<OcgEvent> matched = new ArrayList<>();
        if (catalog == null || catalog.events == null || currentDate == null || currentDate.isEmpty()) {
            return matched;
        }
        for (OcgEvent ev : catalog.events) {
            if (ev != null && currentDate.equals(ev.releaseDate)) {
                matched.add(ev);
            }
        }
        return matched;
    }

    /** Returns the sorted set of timeline dates that contain at least one OCG promo event. */
    public static Set<String> getEventDates(RpgCatalog catalog) {
        Set<String> dates = new TreeSet<>();
        if (catalog == null || catalog.events == null) return dates;
        for (OcgEvent ev : catalog.events) {
            if (ev != null && ev.releaseDate != null && !ev.releaseDate.isEmpty()) {
                dates.add(ev.releaseDate);
            }
        }
        return dates;
    }

    /** Returns all canon duels unlocked on or before currentDate. */
    public static List<OcgCanonDuel> getAvailableCanonDuels(RpgCatalog catalog, String currentDate) {
        List<OcgCanonDuel> available = new ArrayList<>();
        if (catalog == null || catalog.canonDuels == null) return available;
        for (OcgCanonDuel d : catalog.canonDuels) {
            if (d != null && isReleasedOnOrBefore(d.timelineDate, currentDate)) {
                available.add(d);
            }
        }
        return available;
    }

    /**
     * Returns ONLY canon duels scheduled for the exact {@code currentDate} (sorted ascending by
     * {@code originalDate}, then {@code id}), ensuring players challenge each canon duel strictly
     * within that era's card pool and keeping the Canon Duels list clutter-free in later years.
     */
    public static List<OcgCanonDuel> getCanonDuelsOnDate(RpgCatalog catalog, String currentDate) {
        List<OcgCanonDuel> matched = new ArrayList<>();
        if (catalog == null || catalog.canonDuels == null || currentDate == null || currentDate.isEmpty()) {
            return matched;
        }
        for (OcgCanonDuel d : catalog.canonDuels) {
            if (d != null && currentDate.equals(d.timelineDate)) {
                matched.add(d);
            }
        }
        Collections.sort(matched, (a, b) -> {
            int c1 = (a.originalDate != null ? a.originalDate : "")
                    .compareTo(b.originalDate != null ? b.originalDate : "");
            if (c1 != 0) return c1;
            return (a.id != null ? a.id : "").compareTo(b.id != null ? b.id : "");
        });
        return matched;
    }

    /** Returns the sorted set of timeline dates that contain at least one canon duel. */
    public static Set<String> getCanonDuelDates(RpgCatalog catalog) {
        Set<String> dates = new TreeSet<>();
        if (catalog == null || catalog.canonDuels == null) return dates;
        for (OcgCanonDuel d : catalog.canonDuels) {
            if (d != null && d.timelineDate != null && !d.timelineDate.isEmpty()) {
                dates.add(d.timelineDate);
            }
        }
        return dates;
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
            dates.addAll(CardErrataEngine.getErrataMilestonesInRange(catalog));
            if (catalog.canonDuels != null) {
                for (OcgCanonDuel cd : catalog.canonDuels) {
                    if (cd != null && cd.timelineDate != null && !cd.timelineDate.isEmpty()) {
                        dates.add(cd.timelineDate);
                    }
                }
            }
            if (catalog.maxTimelineDate != null && !catalog.maxTimelineDate.isEmpty()) {
                dates.add(catalog.maxTimelineDate);
            }
        }
        return new ArrayList<>(dates);
    }

    /**
     * Returns sorted distinct dates within [minTimelineDate, maxTimelineDate] that actually have
     * at least one OCG event (pack release, promo event, banlist change, rule shift, card errata,
     * AI opponent unlock/deck evolution, or canon duel). Excludes empty filler dates like year-end if nothing happened.
     */
    public static List<String> getTimelineEventDates(RpgCatalog catalog, List<AiOpponent> opponents) {
        TreeSet<String> dates = new TreeSet<>();
        if (catalog == null) return new ArrayList<>();
        String minD = catalog.minTimelineDate;
        String maxD = catalog.maxTimelineDate;
        if (catalog.packs != null) {
            for (OcgPack p : catalog.packs) {
                addIfInRange(dates, p != null ? p.releaseDate : null, minD, maxD);
            }
        }
        if (catalog.events != null) {
            for (OcgEvent e : catalog.events) {
                addIfInRange(dates, e != null ? e.releaseDate : null, minD, maxD);
            }
        }
        if (catalog.banlists != null) {
            for (OcgBanlist b : catalog.banlists) {
                addIfInRange(dates, b != null ? b.effectiveDate : null, minD, maxD);
            }
        }
        if (catalog.rules != null) {
            for (OcgRuleSet r : catalog.rules) {
                addIfInRange(dates, r != null ? r.effectiveDate : null, minD, maxD);
            }
        }
        for (String ed : CardErrataEngine.getErrataMilestonesInRange(catalog)) {
            addIfInRange(dates, ed, minD, maxD);
        }
        if (catalog.canonDuels != null) {
            for (OcgCanonDuel cd : catalog.canonDuels) {
                if (cd != null) addIfInRange(dates, cd.timelineDate, minD, maxD);
            }
        }
        if (opponents != null) {
            for (AiOpponent opp : opponents) {
                if (opp == null) continue;
                addIfInRange(dates, opp.unlockDate, minD, maxD);
                if (opp.deckEvolutions != null) {
                    for (AiOpponent.EraDeckProfile ep : opp.deckEvolutions) {
                        if (ep != null) addIfInRange(dates, ep.effectiveDate, minD, maxD);
                    }
                }
            }
        }
        return new ArrayList<>(dates);
    }

    private static void addIfInRange(TreeSet<String> out, String date, String minD, String maxD) {
        if (date == null || date.length() < 10) return;
        if (minD != null && !minD.isEmpty() && date.compareTo(minD) < 0) return;
        if (maxD != null && !maxD.isEmpty() && date.compareTo(maxD) > 0) return;
        out.add(date);
    }

    /** Returns the latest event date strictly earlier than {@code currentDate}, or {@code null} if none. */
    public static String getPreviousEventDate(List<String> eventDates, String currentDate) {
        if (eventDates == null || eventDates.isEmpty() || currentDate == null) return null;
        String prev = null;
        for (String d : eventDates) {
            if (d.compareTo(currentDate) < 0) {
                prev = d;
            } else {
                break;
            }
        }
        return prev;
    }

    /** Returns the earliest event date strictly later than {@code currentDate}, or {@code null} if none. */
    public static String getNextEventDate(List<String> eventDates, String currentDate) {
        if (eventDates == null || eventDates.isEmpty() || currentDate == null) return null;
        for (String d : eventDates) {
            if (d.compareTo(currentDate) > 0) {
                return d;
            }
        }
        return null;
    }

    /** Returns sorted distinct 4-digit years (e.g. "1999", "2000") that contain at least one event date. */
    public static List<String> getEventYears(List<String> eventDates) {
        TreeSet<String> years = new TreeSet<>();
        if (eventDates != null) {
            for (String d : eventDates) {
                if (d != null && d.length() >= 4) {
                    years.add(d.substring(0, 4));
                }
            }
        }
        return new ArrayList<>(years);
    }

    /** Returns sorted distinct 2-digit months (e.g. "02", "03") with events in {@code year}. */
    public static List<String> getEventMonthsForYear(List<String> eventDates, String year) {
        TreeSet<String> months = new TreeSet<>();
        if (eventDates != null && year != null && year.length() == 4) {
            String prefix = year + "-";
            for (String d : eventDates) {
                if (d != null && d.length() >= 7 && d.startsWith(prefix)) {
                    months.add(d.substring(5, 7));
                }
            }
        }
        return new ArrayList<>(months);
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

    public static class ErrataChange {
        public final cn.garymb.ygomobile.rpg.model.OcgCardErrata errata;
        public final cn.garymb.ygomobile.rpg.model.OcgCardErrata.Revision revision;
        public final cn.garymb.ygomobile.rpg.model.OcgCardErrata.Revision previousRevision;

        public ErrataChange(cn.garymb.ygomobile.rpg.model.OcgCardErrata errata,
                            cn.garymb.ygomobile.rpg.model.OcgCardErrata.Revision revision,
                            cn.garymb.ygomobile.rpg.model.OcgCardErrata.Revision previousRevision) {
            this.errata = errata;
            this.revision = revision;
            this.previousRevision = previousRevision;
        }
    }

    public static class AiChange {
        public final AiOpponent opponent;
        public final AiOpponent.EraDeckProfile newProfile;
        public final AiOpponent.EraDeckProfile previousProfile;
        public final boolean newlyUnlocked;

        public AiChange(AiOpponent opponent,
                        AiOpponent.EraDeckProfile newProfile,
                        AiOpponent.EraDeckProfile previousProfile,
                        boolean newlyUnlocked) {
            this.opponent = opponent;
            this.newProfile = newProfile;
            this.previousProfile = previousProfile;
            this.newlyUnlocked = newlyUnlocked;
        }
    }

    public static class TimelineDigest {
        public final String fromDate;
        public final String toDate;
        public final boolean isRange;
        public final List<OcgPack> newPacks = new ArrayList<>();
        public final List<OcgEvent> newEvents = new ArrayList<>();
        public final List<OcgRuleSet> ruleChanges = new ArrayList<>();
        public final List<OcgBanlist> banlistChanges = new ArrayList<>();
        public final List<ErrataChange> errataChanges = new ArrayList<>();
        public final List<AiChange> aiChanges = new ArrayList<>();
        public final List<OcgCanonDuel> newCanonDuels = new ArrayList<>();

        public TimelineDigest(String fromDate, String toDate, boolean isRange) {
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.isRange = isRange;
        }

        public int getTotalChangeCount() {
            return newPacks.size() + newEvents.size() + ruleChanges.size()
                    + banlistChanges.size() + errataChanges.size() + aiChanges.size()
                    + newCanonDuels.size();
        }

        public String getPeriodTitle() {
            if (isRange && fromDate != null && !fromDate.equals(toDate)) {
                return fromDate + " → " + toDate + " 区间历史变化";
            }
            return toDate + " 当日历史大事件";
        }

        public String buildShortSummary() {
            if (getTotalChangeCount() == 0) {
                return "📰 " + toDate + " 当日无新发售或规则变动（点击「当期动态」查看详情）";
            }
            List<String> parts = new ArrayList<>();
            if (!newPacks.isEmpty()) {
                String first = newPacks.get(0).name;
                parts.add("新商品×" + newPacks.size() + "(" + first + (newPacks.size() > 1 ? "等" : "") + ")");
            }
            if (!newEvents.isEmpty()) {
                parts.add("特典活动×" + newEvents.size());
            }
            if (!ruleChanges.isEmpty()) {
                parts.add("规则变更×" + ruleChanges.size());
            }
            if (!banlistChanges.isEmpty()) {
                parts.add("禁限表×" + banlistChanges.size());
            }
            if (!errataChanges.isEmpty()) {
                parts.add("效果勘误×" + errataChanges.size());
            }
            if (!aiChanges.isEmpty()) {
                parts.add("AI调整×" + aiChanges.size());
            }
            if (!newCanonDuels.isEmpty()) {
                parts.add("原著对决×" + newCanonDuels.size());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("📰 ");
            if (isRange && fromDate != null && !fromDate.equals(toDate)) {
                sb.append(fromDate).append("→").append(toDate).append("：");
            } else {
                sb.append(toDate).append("：");
            }
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(parts.get(i));
            }
            return sb.toString();
        }
    }

    /** Builds a digest of all historical events occurring exactly on {@code date}. */
    public static TimelineDigest buildDigestAtDate(RpgCatalog catalog, List<AiOpponent> opponents, String date) {
        return collectDigest(catalog, opponents, null, date, false);
    }

    /**
     * Builds a digest when moving the timeline from {@code fromDate} to {@code toDate}.
     * Moving forward aggregates all changes in (fromDate, toDate]; jumping backward or viewing
     * the same date shows all events occurring exactly on {@code toDate}.
     */
    public static TimelineDigest buildDigestForTransition(RpgCatalog catalog,
                                                          List<AiOpponent> opponents,
                                                          String fromDate,
                                                          String toDate) {
        if (fromDate != null && toDate != null && toDate.compareTo(fromDate) > 0) {
            return collectDigest(catalog, opponents, fromDate, toDate, true);
        }
        return collectDigest(catalog, opponents, null, toDate, false);
    }

    private static boolean dateMatches(String eventDate, String fromDate, String toDate, boolean isRange) {
        if (eventDate == null || eventDate.isEmpty() || toDate == null) return false;
        if (isRange && fromDate != null) {
            return eventDate.compareTo(fromDate) > 0 && eventDate.compareTo(toDate) <= 0;
        }
        return eventDate.equals(toDate);
    }

    private static TimelineDigest collectDigest(RpgCatalog catalog,
                                                List<AiOpponent> opponents,
                                                String fromDate,
                                                String toDate,
                                                boolean isRange) {
        TimelineDigest digest = new TimelineDigest(fromDate, toDate, isRange);
        if (catalog == null || toDate == null) return digest;

        if (catalog.packs != null) {
            for (OcgPack p : catalog.packs) {
                if (dateMatches(p.releaseDate, fromDate, toDate, isRange)) {
                    digest.newPacks.add(p);
                }
            }
        }
        if (catalog.events != null) {
            for (OcgEvent ev : catalog.events) {
                if (dateMatches(ev.releaseDate, fromDate, toDate, isRange)) {
                    digest.newEvents.add(ev);
                }
            }
        }
        if (catalog.rules != null) {
            for (OcgRuleSet r : catalog.rules) {
                if (dateMatches(r.effectiveDate, fromDate, toDate, isRange)) {
                    digest.ruleChanges.add(r);
                }
            }
        }
        if (catalog.banlists != null) {
            for (OcgBanlist b : catalog.banlists) {
                if (dateMatches(b.effectiveDate, fromDate, toDate, isRange)) {
                    digest.banlistChanges.add(b);
                }
            }
        }
        for (cn.garymb.ygomobile.rpg.model.OcgCardErrata err : catalog.getErrataRecords()) {
            if (!err.in1999Catalog || err.revisions == null) continue;
            for (int i = 1; i < err.revisions.size(); i++) {
                cn.garymb.ygomobile.rpg.model.OcgCardErrata.Revision rev = err.revisions.get(i);
                if (dateMatches(rev.effectiveDate, fromDate, toDate, isRange)) {
                    digest.errataChanges.add(new ErrataChange(err, rev, err.revisions.get(i - 1)));
                }
            }
        }
        if (catalog.canonDuels != null) {
            for (OcgCanonDuel cd : catalog.canonDuels) {
                if (cd != null && dateMatches(cd.timelineDate, fromDate, toDate, isRange)) {
                    digest.newCanonDuels.add(cd);
                }
            }
        }
        if (opponents != null) {
            for (AiOpponent opp : opponents) {
                boolean newlyUnlocked = dateMatches(opp.unlockDate, fromDate, toDate, isRange);
                AiOpponent.EraDeckProfile matchedEvo = null;
                AiOpponent.EraDeckProfile prevEvo = null;
                if (opp.deckEvolutions != null) {
                    for (int i = 0; i < opp.deckEvolutions.size(); i++) {
                        AiOpponent.EraDeckProfile evo = opp.deckEvolutions.get(i);
                        if (dateMatches(evo.effectiveDate, fromDate, toDate, isRange)) {
                            matchedEvo = evo;
                            if (i > 0) prevEvo = opp.deckEvolutions.get(i - 1);
                        }
                    }
                }
                if (newlyUnlocked || matchedEvo != null) {
                    AiOpponent.EraDeckProfile effective = matchedEvo != null
                            ? matchedEvo : opp.getActiveDeckProfile(toDate);
                    digest.aiChanges.add(new AiChange(opp, effective, prevEvo, newlyUnlocked));
                }
            }
        }
        return digest;
    }
}
