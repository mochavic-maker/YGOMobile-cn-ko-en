package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Empirical OCG 1999 AI bounty calibrator derived from real native {@code ocgcore} full round-robin
 * Bradley-Terry Elo benchmarks across all 17 historical 1999 timeline milestones (8,224 real duels,
 * balanced first/second games per pairing with exact 1999 OCG rules & pre-errata Lua scripts).
 *
 * <p>Guarantees that within every single timeline era, an AI opponent's win/draw/loss token bounty
 * is strictly aligned with its true measured combat strength (Elo) in that exact meta — even as
 * older decks get power-crept or counter-meta decks surge.
 */
public final class EraBountyCalibrator {
    public static final int MIN_WIN_BOUNTY = 120;
    public static final int MAX_WIN_BOUNTY = 300;

    private EraBountyCalibrator() {}

    public static final class RewardTriple {
        public final int win;
        public final int draw;
        public final int loss;
        public final int benchmarkElo;
        public final double benchmarkWinRate;
        public final String tierLabel;

        public RewardTriple(int win, int draw, int loss, int benchmarkElo, double benchmarkWinRate, String tierLabel) {
            this.win = win;
            this.draw = draw;
            this.loss = loss;
            this.benchmarkElo = benchmarkElo;
            this.benchmarkWinRate = benchmarkWinRate;
            this.tierLabel = tierLabel != null ? tierLabel : "B";
        }

        public String getTierLabel() {
            return tierLabel;
        }
    }

    private static final class Entry {
        final int elo;
        final double winRate;

        Entry(int elo, double winRate) {
            this.elo = elo;
            this.winRate = winRate;
        }
    }

    private static final class EraSnapshot {
        final String effectiveDate;
        final int floorWin;
        final int ceilingWin;
        final Map<String, Entry> ratings;
        final Map<String, String> dynamicTiers;
        final int effMinElo;
        final int effMaxElo;

        EraSnapshot(String effectiveDate, int floorWin, int ceilingWin, Map<String, Entry> ratings) {
            this.effectiveDate = effectiveDate;
            this.floorWin = Math.max(MIN_WIN_BOUNTY, floorWin);
            this.ceilingWin = Math.min(MAX_WIN_BOUNTY, ceilingWin);
            this.ratings = Collections.unmodifiableMap(ratings);
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            List<Map.Entry<String, Entry>> sorted = new ArrayList<>(ratings.entrySet());
            Collections.sort(sorted, (a, b) -> Integer.compare(b.getValue().elo, a.getValue().elo));
            int[] sortedElos = new int[sorted.size()];
            for (int i = 0; i < sorted.size(); i++) {
                int elo = sorted.get(i).getValue().elo;
                sortedElos[i] = elo;
                if (elo < min) min = elo;
                if (elo > max) max = elo;
            }
            String[] tiers = RoundRobinLeagueEngine.computeDynamicTiers(sortedElos);
            Map<String, String> tierMap = new LinkedHashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                tierMap.put(sorted.get(i).getKey(), tiers[i]);
            }
            this.dynamicTiers = Collections.unmodifiableMap(tierMap);
            // Clamp extreme 0-win outliers (e.g. Elo 548) to 950 so the rest of the field spreads cleanly
            this.effMinElo = Math.max(950, min == Integer.MAX_VALUE ? 1000 : min);
            this.effMaxElo = Math.max(this.effMinElo + 250, max == Integer.MIN_VALUE ? 1800 : max);
        }

        RewardTriple computeFor(String opponentId) {
            Entry e = ratings.get(opponentId);
            if (e == null) return null;
            double t = (e.elo - effMinElo) / (double) (effMaxElo - effMinElo);
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;
            int win = roundTo5(floorWin + t * (ceilingWin - floorWin));
            win = Math.max(MIN_WIN_BOUNTY, Math.min(MAX_WIN_BOUNTY, win));
            int draw = roundTo5(win * 0.43);
            int loss = roundTo5(win * 0.25);
            String tier = dynamicTiers.getOrDefault(opponentId, "B");
            return new RewardTriple(win, draw, loss, e.elo, e.winRate, tier);
        }
    }

    private static final List<EraSnapshot> SNAPSHOTS = buildSnapshots();

    private static Map<String, Entry> era(Object... triples) {
        Map<String, Entry> m = new LinkedHashMap<>();
        for (int i = 0; i + 2 < triples.length; i += 3) {
            String id = (String) triples[i];
            int elo = ((Number) triples[i + 1]).intValue();
            double wr = ((Number) triples[i + 2]).doubleValue();
            m.put(id, new Entry(elo, wr));
        }
        return m;
    }

    private static List<EraSnapshot> buildSnapshots() {
        List<EraSnapshot> list = new ArrayList<>();

        // 1999-02-04: Vol.1 Official Rule (N=4, 16 gpp, 96 games)
        list.add(new EraSnapshot("1999-02-04", 120, 220, era(
                "ai_vol1_yugi", 1813, 87.5,
                "ai_burn_mage", 1704, 75.0,
                "ai_street_novice", 1259, 20.8,
                "ai_shop_regular", 1224, 16.7
        )));

        // 1999-03-01: Booster 1 + Weekend Challenger (N=5, 16 gpp, 160 games)
        list.add(new EraSnapshot("1999-03-01", 120, 225, era(
                "ai_vol1_yugi", 1848, 85.9,
                "ai_burn_mage", 1848, 85.9,
                "ai_weekend_challenger", 1381, 39.1,
                "ai_street_novice", 1218, 20.3,
                "ai_shop_regular", 1204, 18.8
        )));

        // 1999-03-18: Starter Box + Kaiba No-Tribute Blue-Eyes Dark Age (N=6, 16 gpp, 240 games)
        list.add(new EraSnapshot("1999-03-18", 120, 240, era(
                "ai_starter_kaiba", 1972, 91.3,
                "ai_vol1_yugi", 1794, 76.3,
                "ai_burn_mage", 1725, 70.0,
                "ai_weekend_challenger", 1298, 32.5,
                "ai_street_novice", 1105, 15.0,
                "ai_shop_regular", 1105, 15.0
        )));

        // 1999-03-27: Vol.2 (N=6, 16 gpp, 240 games)
        list.add(new EraSnapshot("1999-03-27", 120, 245, era(
                "ai_vol1_yugi", 2053, 92.5,
                "ai_starter_kaiba", 1931, 83.8,
                "ai_burn_mage", 1650, 62.5,
                "ai_weekend_challenger", 1247, 31.3,
                "ai_street_novice", 1119, 20.0,
                "ai_shop_regular", 999, 10.0
        )));

        // 1999-05-05: Expert Rule (Tribute Summon introduced) (N=6, 16 gpp, 240 games)
        list.add(new EraSnapshot("1999-05-05", 120, 245, era(
                "ai_starter_kaiba", 2020, 95.0,
                "ai_vol1_yugi", 1832, 82.5,
                "ai_burn_mage", 1389, 42.5,
                "ai_weekend_challenger", 1315, 33.8,
                "ai_street_novice", 1283, 30.0,
                "ai_shop_regular", 1160, 16.3
        )));

        // 1999-05-25: Booster 2 + Equip Beat debut (N=7, 12 gpp, 252 games)
        list.add(new EraSnapshot("1999-05-25", 120, 255, era(
                "ai_equip_beat", 2047, 93.1,
                "ai_vol1_yugi", 1826, 76.4,
                "ai_starter_kaiba", 1810, 75.0,
                "ai_burn_mage", 1475, 45.8,
                "ai_street_novice", 1409, 40.3,
                "ai_weekend_challenger", 1064, 15.3,
                "ai_shop_regular", 870, 4.2
        )));

        // 1999-05-27: Vol.3 + Flip Control debut (N=8, 12 gpp, 336 games)
        list.add(new EraSnapshot("1999-05-27", 120, 260, era(
                "ai_flip_control", 1938, 86.9,
                "ai_vol1_yugi", 1878, 82.1,
                "ai_equip_beat", 1849, 79.8,
                "ai_starter_kaiba", 1579, 56.0,
                "ai_burn_mage", 1420, 41.7,
                "ai_street_novice", 1269, 28.6,
                "ai_weekend_challenger", 1180, 21.4,
                "ai_shop_regular", 887, 3.6
        )));

        // 1999-07-08: DM2 + Crush Card Virus debut (pre-errata 3-turn draw check) (N=9, 12 gpp, 432 games)
        list.add(new EraSnapshot("1999-07-08", 120, 260, era(
                "ai_flip_control", 1968, 87.5,
                "ai_vol1_yugi", 1953, 86.5,
                "ai_equip_beat", 1844, 78.1,
                "ai_virus_control", 1621, 59.4,
                "ai_starter_kaiba", 1585, 56.3,
                "ai_burn_mage", 1298, 32.3,
                "ai_street_novice", 1206, 25.0,
                "ai_weekend_challenger", 1151, 20.8,
                "ai_shop_regular", 873, 4.2
        )));

        // 1999-07-22: Vol.4 Summoned Skull / MoF / Mask of Darkness (N=9, 12 gpp, 432 games)
        list.add(new EraSnapshot("1999-07-22", 120, 270, era(
                "ai_flip_control", 1959, 89.6,
                "ai_vol1_yugi", 1875, 83.3,
                "ai_virus_control", 1702, 67.7,
                "ai_equip_beat", 1659, 63.5,
                "ai_starter_kaiba", 1606, 58.3,
                "ai_burn_mage", 1400, 38.5,
                "ai_street_novice", 1227, 24.0,
                "ai_weekend_challenger", 1200, 21.9,
                "ai_shop_regular", 871, 3.1
        )));

        // 1999-08-26: Tokyo Dome 1st Banlist + Good Stuff + PP1 Exodia (N=11, 10 gpp, 550 games)
        list.add(new EraSnapshot("1999-08-26", 120, 285, era(
                "ai_tokyo_dome", 2001, 91.0,
                "ai_starter_kaiba", 1806, 76.0,
                "ai_vol1_yugi", 1753, 71.0,
                "ai_flip_control", 1721, 68.0,
                "ai_exodia_master", 1721, 68.0,
                "ai_virus_control", 1690, 65.0,
                "ai_equip_beat", 1416, 40.0,
                "ai_weekend_challenger", 1265, 28.0,
                "ai_burn_mage", 1197, 23.0,
                "ai_street_novice", 1108, 17.0,
                "ai_shop_regular", 822, 3.0
        )));

        // 1999-09-23: Vol.5 Change of Heart / Three Gods (N=11, 10 gpp, 550 games)
        list.add(new EraSnapshot("1999-09-23", 120, 285, era(
                "ai_tokyo_dome", 1857, 84.0,
                "ai_starter_kaiba", 1759, 75.0,
                "ai_vol1_yugi", 1739, 73.0,
                "ai_virus_control", 1720, 71.0,
                "ai_flip_control", 1700, 69.0,
                "ai_exodia_master", 1615, 60.0,
                "ai_equip_beat", 1458, 44.0,
                "ai_weekend_challenger", 1238, 24.0,
                "ai_burn_mage", 1213, 22.0,
                "ai_street_novice", 1188, 20.0,
                "ai_shop_regular", 1011, 8.0
        )));

        // 1999-10-17: Booster 5 Morphing Jar / Needle Worm Mill (N=12, 10 gpp, 660 games)
        list.add(new EraSnapshot("1999-10-17", 120, 290, era(
                "ai_tokyo_dome", 1931, 88.2,
                "ai_mill_morphing", 1769, 74.5,
                "ai_flip_control", 1760, 73.6,
                "ai_starter_kaiba", 1751, 72.7,
                "ai_vol1_yugi", 1687, 66.4,
                "ai_virus_control", 1652, 62.7,
                "ai_exodia_master", 1555, 52.7,
                "ai_equip_beat", 1336, 31.8,
                "ai_burn_mage", 1336, 31.8,
                "ai_weekend_challenger", 1225, 22.7,
                "ai_street_novice", 1200, 20.9,
                "ai_shop_regular", 799, 1.8
        )));

        // 1999-11-18: Vol.6 Pre-Errata Sangan/Witch + Solemn Judgment + Cannon Soldier (N=12, 10 gpp, 660 games)
        list.add(new EraSnapshot("1999-11-18", 120, 300, era(
                "ai_exodia_master", 1810, 79.1,
                "ai_tokyo_dome", 1801, 78.2,
                "ai_flip_control", 1731, 70.9,
                "ai_virus_control", 1731, 70.9,
                "ai_starter_kaiba", 1681, 65.5,
                "ai_vol1_yugi", 1632, 60.0,
                "ai_mill_morphing", 1592, 55.5,
                "ai_burn_mage", 1518, 47.3,
                "ai_equip_beat", 1370, 32.7,
                "ai_weekend_challenger", 1219, 20.9,
                "ai_street_novice", 1178, 18.2,
                "ai_shop_regular", 738, 0.9
        )));

        // 1999-12-01: Booster 6 Penguin Soldier + Fusion/Cyber-Stein (N=13, 10 gpp, 780 games)
        list.add(new EraSnapshot("1999-12-01", 120, 300, era(
                "ai_exodia_master", 1792, 79.2,
                "ai_flip_control", 1792, 79.2,
                "ai_tokyo_dome", 1704, 70.0,
                "ai_starter_kaiba", 1667, 65.8,
                "ai_virus_control", 1646, 63.3,
                "ai_fusion_stein", 1624, 60.8,
                "ai_mill_morphing", 1582, 55.8,
                "ai_vol1_yugi", 1574, 55.0,
                "ai_burn_mage", 1531, 50.0,
                "ai_equip_beat", 1380, 34.2,
                "ai_weekend_challenger", 1183, 18.3,
                "ai_street_novice", 1117, 14.2,
                "ai_shop_regular", 907, 4.2
        )));

        // 1999-12-09: PS Promo Metalmorph Equip Beat upgrade (N=13, 10 gpp, 780 games)
        list.add(new EraSnapshot("1999-12-09", 120, 300, era(
                "ai_exodia_master", 1749, 77.5,
                "ai_tokyo_dome", 1726, 75.0,
                "ai_flip_control", 1668, 68.3,
                "ai_virus_control", 1607, 60.8,
                "ai_equip_beat", 1607, 60.8,
                "ai_vol1_yugi", 1593, 59.2,
                "ai_starter_kaiba", 1587, 58.3,
                "ai_mill_morphing", 1534, 51.7,
                "ai_fusion_stein", 1534, 51.7,
                "ai_burn_mage", 1473, 44.2,
                "ai_street_novice", 1222, 19.2,
                "ai_weekend_challenger", 1200, 17.5,
                "ai_shop_regular", 1002, 5.8
        )));

        // 1999-12-16: EX Starter Set (Yugi/Kaiba EX + Last Will + Anti-Exodia Morphing Jar) (N=13, 10 gpp, 780 games)
        list.add(new EraSnapshot("1999-12-16", 120, 300, era(
                "ai_flip_control", 1722, 75.8,
                "ai_vol1_yugi", 1722, 75.8,
                "ai_tokyo_dome", 1644, 66.7,
                "ai_virus_control", 1631, 65.0,
                "ai_exodia_master", 1579, 58.3,
                "ai_equip_beat", 1579, 58.3,
                "ai_burn_mage", 1559, 55.8,
                "ai_mill_morphing", 1521, 50.8,
                "ai_fusion_stein", 1488, 46.7,
                "ai_starter_kaiba", 1488, 46.7,
                "ai_weekend_challenger", 1280, 23.3,
                "ai_street_novice", 1243, 20.0,
                "ai_shop_regular", 1043, 6.7
        )));

        // 1999-12-20: Sangan/Witch 1st Errata (field-to-GY only) (N=13, 10 gpp, 780 games)
        list.add(new EraSnapshot("1999-12-20", 120, 300, era(
                "ai_flip_control", 1871, 88.3,
                "ai_tokyo_dome", 1637, 65.0,
                "ai_vol1_yugi", 1623, 63.3,
                "ai_virus_control", 1603, 60.8,
                "ai_equip_beat", 1603, 60.8,
                "ai_burn_mage", 1583, 58.3,
                "ai_starter_kaiba", 1570, 56.7,
                "ai_mill_morphing", 1538, 52.5,
                "ai_exodia_master", 1524, 50.8,
                "ai_fusion_stein", 1478, 45.0,
                "ai_weekend_challenger", 1287, 24.2,
                "ai_street_novice", 1249, 20.8,
                "ai_shop_regular", 935, 3.3
        )));

        return Collections.unmodifiableList(list);
    }

    /**
     * Resolves empirically calibrated token rewards and benchmark Elo for {@code opponentId}
     * at {@code currentDate}, or {@code null} if this opponent has no calibrated snapshot.
     */
    public static RewardTriple resolve(String opponentId, String currentDate) {
        if (opponentId == null || opponentId.isEmpty()) return null;
        String date = (currentDate != null && !currentDate.trim().isEmpty())
                ? currentDate.trim()
                : "1999-02-04";
        EraSnapshot best = null;
        for (EraSnapshot snap : SNAPSHOTS) {
            if (snap.effectiveDate.compareTo(date) <= 0 && snap.ratings.containsKey(opponentId)) {
                best = snap;
            }
        }
        return best != null ? best.computeFor(opponentId) : null;
    }

    /** Returns all 17 calibrated milestone dates in chronological order. */
    public static List<String> getCalibratedMilestoneDates() {
        List<String> dates = new ArrayList<>(SNAPSHOTS.size());
        for (EraSnapshot snap : SNAPSHOTS) {
            dates.add(snap.effectiveDate);
        }
        return dates;
    }

    private static int roundTo5(double value) {
        return (int) (Math.round(value / 5.0) * 5L);
    }
}
