package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels;
import cn.garymb.ygomobile.rpg.ai.RpgAiStrategy;
import cn.garymb.ygomobile.rpg.bridge.RpgDuelHostBridge;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Full-environment AI ladder & benchmark engine supporting two modes:
 * <ol>
 *   <li><b>Swiss-System + Bradley-Terry Elo Ladder ({@link #MODE_SWISS_ELO})</b>:
 *       Scales as {@code O(N log N)} instead of {@code O(N^2)} by running Swiss rounds
 *       (pairing adjacent-rated decks each round) with {@code 3} games per pairing, and solving
 *       global Bayesian Bradley-Terry MLE ratings (order-independent, strength-of-schedule aware).
 *       Also supports appending additional Swiss rounds incrementally to tighten confidence.</li>
 *   <li><b>Full Round-Robin ({@link #MODE_ROUND_ROBIN})</b>:
 *       Every unlocked AI opponent (plus optionally the player's current RPG deck) plays every
 *       other contestant for {@code gamesPerPair} (default 5) real ocgcore headless duels,
 *       likewise scored with both Bradley-Terry Elo and raw wins/win-rate.</li>
 * </ol>
 */
public final class RoundRobinLeagueEngine {
    public static final int DEFAULT_GAMES_PER_PAIR = 5;
    public static final int DEFAULT_SWISS_GAMES_PER_PAIR = 3;

    public static final String MODE_ROUND_ROBIN = "ROUND_ROBIN";
    public static final String MODE_SWISS_ELO = "SWISS_ELO";

    private static final double BASE_ELO = 1500.0;
    private static final double ELO_SCALE = 400.0;
    /** Virtual Bayesian prior against a 1500-Elo average anchor (prevents infinity on 100%/0% records). */
    private static final double PRIOR_GAMES = 1.0;
    private static final double PRIOR_WINS = 0.5;
    private static final int BT_ITERATIONS = 80;

    private RoundRobinLeagueEngine() {}

    public interface DuelExecutor {
        RpgAiAutoDuelModels.DuelResult run(
                Participant sideA,
                Participant sideB,
                OcgRuleSet ruleSet,
                String currentDate,
                boolean sideAFirst,
                Random rng);
    }

    public interface ProgressListener {
        void onProgress(int completedGames, int totalGames,
                        int completedPairs, int totalPairs,
                        String currentMatchupLabel);
    }

    public static final class Participant {
        public final String id;
        public final String name;
        public final String deckName;
        public final boolean isPlayer;
        public final List<Integer> main;
        public final List<Integer> extra;
        public final String style;

        public int wins;
        public int losses;
        public int draws;
        public int gamesPlayed;
        public long totalTurns;
        public int byeCount;

        /** Global Bayesian Bradley-Terry Elo rating (centered at 1500). */
        public int eloRating = 1500;
        /** Average Elo rating of all opponents faced (weighted by games played). */
        public int avgOpponentElo = 1500;

        /** Map from opponent Participant.id -> int[]{wins, losses, draws} against that opponent. */
        public final Map<String, int[]> headToHead = new LinkedHashMap<>();

        public Participant(String id, String name, String deckName, boolean isPlayer,
                           List<Integer> main, List<Integer> extra, String style) {
            this.id = id;
            this.name = name;
            this.deckName = deckName != null ? deckName : "";
            this.isPlayer = isPlayer;
            this.main = main != null ? new ArrayList<>(main) : new ArrayList<>();
            this.extra = extra != null ? new ArrayList<>(extra) : new ArrayList<>();
            this.style = style != null ? style : "GOOD_STUFF";
        }

        public double getWinRatePercent() {
            return gamesPlayed > 0 ? (wins * 100.0 / gamesPlayed) : 0.0;
        }

        public double getAvgTurns() {
            return gamesPlayed > 0 ? (totalTurns * 1.0 / gamesPlayed) : 0.0;
        }

        public String getTierLabel() {
            if (eloRating >= 1620) return "S";
            if (eloRating >= 1540) return "A";
            if (eloRating >= 1460) return "B";
            if (eloRating >= 1380) return "C";
            return "D";
        }

        void recordGame(String oppId, boolean won, boolean isDraw, int turns) {
            gamesPlayed++;
            totalTurns += Math.max(0, turns);
            int[] h2h = headToHead.get(oppId);
            if (h2h == null) {
                h2h = new int[3];
                headToHead.put(oppId, h2h);
            }
            if (isDraw) {
                draws++;
                h2h[2]++;
            } else if (won) {
                wins++;
                h2h[0]++;
            } else {
                losses++;
                h2h[1]++;
            }
        }
    }

    public static final class LeagueResult {
        public final String mode;
        public final String timelineDate;
        public final OcgRuleSet ruleSet;
        public final int roundsPlayed;
        public final int gamesPerPair;
        public final int totalPairs;
        public final int completedPairs;
        public final int totalGames;
        public final int completedGames;
        public final boolean cancelled;
        public final long elapsedMs;
        public final List<Participant> standings;

        public LeagueResult(String mode, String timelineDate, OcgRuleSet ruleSet,
                            int roundsPlayed, int gamesPerPair,
                            int totalPairs, int completedPairs,
                            int totalGames, int completedGames,
                            boolean cancelled, long elapsedMs,
                            List<Participant> standings) {
            this.mode = mode != null ? mode : MODE_ROUND_ROBIN;
            this.timelineDate = timelineDate;
            this.ruleSet = ruleSet;
            this.roundsPlayed = roundsPlayed;
            this.gamesPerPair = gamesPerPair;
            this.totalPairs = totalPairs;
            this.completedPairs = completedPairs;
            this.totalGames = totalGames;
            this.completedGames = completedGames;
            this.cancelled = cancelled;
            this.elapsedMs = elapsedMs;
            this.standings = standings;
        }

        public boolean isSwissMode() {
            return MODE_SWISS_ELO.equals(mode);
        }

        public String formatPlainTextReport() {
            StringBuilder sb = new StringBuilder();
            sb.append(isSwissMode()
                    ? "【全环境瑞士轮 · Bradley-Terry 天梯积分榜】\n"
                    : "【全环境 AI 循环赛排行榜】\n");
            sb.append("时间轴：").append(timelineDate)
                    .append(" · 规则：").append(ruleSet != null ? ruleSet.name : "")
                    .append("\n");
            if (isSwissMode()) {
                sb.append("参赛选手：").append(standings.size())
                        .append(" 名 · 瑞士轮 ").append(roundsPlayed).append(" 轮（每组 ")
                        .append(gamesPerPair).append(" 局）· 累计 ")
                        .append(completedGames).append(" 局");
            } else {
                sb.append("参赛选手：").append(standings.size())
                        .append(" 名 · 每对 ").append(gamesPerPair).append(" 局 · 完成 ")
                        .append(completedGames).append("/").append(totalGames).append(" 局");
            }
            sb.append(String.format(Locale.US, "（耗时 %.1fs）", elapsedMs / 1000.0));
            if (cancelled) sb.append(" [已中途取消]");
            sb.append("\n\n");
            for (int i = 0; i < standings.size(); i++) {
                Participant p = standings.get(i);
                sb.append(String.format(Locale.US,
                        "#%d [%s级 · Elo %d] %s%s — %d胜 %d负 %d平 (胜率 %.1f%% · 对手均分 %d)",
                        i + 1,
                        p.getTierLabel(),
                        p.eloRating,
                        p.isPlayer ? "★ " : "",
                        p.name,
                        p.wins, p.losses, p.draws,
                        p.getWinRatePercent(),
                        p.avgOpponentElo));
                if (!p.deckName.isEmpty()) {
                    sb.append(" · ").append(p.deckName);
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }
    }

    /**
     * Recommends the optimal number of Swiss rounds for {@code participantCount} contestants:
     * {@code ceil(log2(N)) + 2}, clamped to {@code [1, max(1, N - 1)]} (and at least 5 when N >= 6).
     */
    public static int recommendedSwissRounds(int participantCount) {
        if (participantCount <= 1) return 0;
        if (participantCount == 2) return 1;
        int ceilLog2 = 32 - Integer.numberOfLeadingZeros(participantCount - 1);
        int target = Math.max(5, ceilLog2 + 2);
        return Math.min(participantCount - 1, target);
    }

    /**
     * Builds the list of participants for the active timeline date:
     * all unlocked AI opponents (each using its active era deck) + optionally the player's deck.
     */
    public static List<Participant> buildParticipants(
            RpgCatalog catalog,
            String currentDate,
            OcgRuleSet ruleSet,
            boolean includePlayer,
            String playerDeckLabel,
            List<Integer> playerMain,
            List<Integer> playerExtra,
            Random deckRng) {
        Random rng = deckRng != null ? deckRng : new Random();
        List<Participant> list = new ArrayList<>();

        if (includePlayer && playerMain != null && !playerMain.isEmpty()) {
            String playerStyle = RpgAiStrategy.inferStyleFromDeck(playerMain, playerExtra, ruleSet);
            String label = (playerDeckLabel != null && !playerDeckLabel.trim().isEmpty())
                    ? playerDeckLabel.trim()
                    : "当前自组卡组";
            list.add(new Participant(
                    "player_deck",
                    "我的卡组",
                    label,
                    true,
                    playerMain,
                    playerExtra,
                    playerStyle));
        }

        List<AiOpponent> unlocked = TimelineEngine.getAvailableOpponents(
                AiDeckGenerator.getDemoOpponents(), currentDate);
        for (AiOpponent opp : unlocked) {
            AiDeckGenerator.GeneratedAiDeck gen = AiDeckGenerator.generateDeck(
                    catalog, opp, currentDate, rng);
            String aiStyle = (gen.appliedProfile != null && gen.appliedProfile.fillStyle != null)
                    ? gen.appliedProfile.fillStyle
                    : RpgAiStrategy.inferStyleFromDeck(gen.main, gen.extra, ruleSet);
            String deckName = gen.appliedProfile != null ? gen.appliedProfile.deckName : opp.title;
            list.add(new Participant(
                    opp.id,
                    opp.name,
                    deckName,
                    false,
                    gen.main,
                    gen.extra,
                    aiStyle));
        }
        return list;
    }

    /**
     * Runs a Swiss-System Bradley-Terry Elo ladder on the real in-process ocgcore headless runner.
     * Can also be called on an already-played participant list (by passing {@code priorRoundsPlayed}
     * and {@code priorElapsedMs}) to append extra Swiss rounds and tighten Elo confidence!
     */
    public static LeagueResult runSwissLadder(
            final RpgCatalog catalog,
            final RpgDuelHostBridge bridge,
            List<Participant> participants,
            OcgRuleSet ruleSet,
            String currentDate,
            int roundsToPlay,
            int gamesPerPair,
            int priorRoundsPlayed,
            long priorElapsedMs,
            ProgressListener progressListener,
            AtomicBoolean cancelFlag,
            Random rng) {
        DuelExecutor executor = (a, b, rule, date, aFirst, random) -> bridge.runHeadlessOcgDuel(
                catalog,
                a.name, a.main, a.extra, a.style,
                b.name, b.main, b.extra, b.style,
                rule, date, aFirst, false, random);
        return runSwissLadder(participants, ruleSet, currentDate, roundsToPlay, gamesPerPair,
                priorRoundsPlayed, priorElapsedMs, executor, progressListener, cancelFlag, rng);
    }

    /**
     * Pure-Java Swiss-System Bradley-Terry Elo runner (injectable {@link DuelExecutor} for testing).
     */
    public static LeagueResult runSwissLadder(
            List<Participant> participants,
            OcgRuleSet ruleSet,
            String currentDate,
            int roundsToPlay,
            int gamesPerPair,
            int priorRoundsPlayed,
            long priorElapsedMs,
            DuelExecutor executor,
            ProgressListener progressListener,
            AtomicBoolean cancelFlag,
            Random rng) {
        Random random = rng != null ? rng : new Random();
        int gpp = Math.max(1, gamesPerPair);
        int n = participants != null ? participants.size() : 0;
        int rounds = Math.max(1, roundsToPlay);
        int pairsPerRound = n / 2;
        int newTotalPairs = rounds * pairsPerRound;
        int newTotalGames = newTotalPairs * gpp;
        long t0 = System.currentTimeMillis();

        // Reconstruct pair meeting counts from existing headToHead so appending rounds avoids rematches
        Map<String, Integer> pairMeetCount = new HashMap<>();
        int priorGames = 0;
        int priorPairs = 0;
        if (participants != null) {
            for (int i = 0; i < n; i++) {
                Participant a = participants.get(i);
                for (int j = i + 1; j < n; j++) {
                    Participant b = participants.get(j);
                    int[] h = a.headToHead.get(b.id);
                    if (h != null) {
                        int gCount = h[0] + h[1] + h[2];
                        if (gCount > 0) {
                            priorGames += gCount;
                            int meetings = Math.max(1, (gCount + gpp - 1) / gpp);
                            priorPairs += meetings;
                            pairMeetCount.put(pairKey(a.id, b.id), meetings);
                        }
                    }
                }
            }
        }

        int newCompletedPairs = 0;
        int newCompletedGames = 0;
        int roundsCompletedThisRun = 0;
        boolean cancelled = false;

        outer:
        for (int r = 0; r < rounds; r++) {
            if (cancelFlag != null && cancelFlag.get()) {
                cancelled = true;
                break;
            }
            int globalRoundNum = priorRoundsPlayed + r + 1;
            List<Participant> ordered = new ArrayList<>(participants);
            if (globalRoundNum == 1 && priorGames == 0) {
                Collections.shuffle(ordered, random);
            } else {
                computeBradleyTerryElo(ordered);
                // Add tiny tie-break jitter among identical ratings so repeated runs don't lock pairings
                final Map<String, Integer> jitter = new HashMap<>();
                for (Participant p : ordered) jitter.put(p.id, random.nextInt(1000));
                Collections.sort(ordered, (p1, p2) -> {
                    int cmp = Integer.compare(p2.eloRating, p1.eloRating);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(p2.wins, p1.wins);
                    if (cmp != 0) return cmp;
                    return Integer.compare(jitter.get(p1.id), jitter.get(p2.id));
                });
            }

            // Handle odd N: assign bye to lowest-ranked contestant with fewest prior byes
            if (ordered.size() % 2 == 1) {
                int byeIdx = ordered.size() - 1;
                int minByes = ordered.get(byeIdx).byeCount;
                for (int i = ordered.size() - 2; i >= 0; i--) {
                    if (ordered.get(i).byeCount < minByes) {
                        minByes = ordered.get(i).byeCount;
                        byeIdx = i;
                    }
                }
                ordered.get(byeIdx).byeCount++;
                ordered.remove(byeIdx);
            }

            // Greedy Swiss pairing: pair top unpaired player with closest-ranked least-repeated opponent
            boolean[] used = new boolean[ordered.size()];
            List<Participant[]> roundPairs = new ArrayList<>(ordered.size() / 2);
            for (int i = 0; i < ordered.size(); i++) {
                if (used[i]) continue;
                Participant a = ordered.get(i);
                int bestJ = -1;
                int bestMeetings = Integer.MAX_VALUE;
                int bestRankGap = Integer.MAX_VALUE;
                for (int j = i + 1; j < ordered.size(); j++) {
                    if (used[j]) continue;
                    Participant b = ordered.get(j);
                    int meets = pairMeetCount.getOrDefault(pairKey(a.id, b.id), 0);
                    int gap = j - i;
                    if (meets < bestMeetings || (meets == bestMeetings && gap < bestRankGap)) {
                        bestMeetings = meets;
                        bestRankGap = gap;
                        bestJ = j;
                    }
                }
                if (bestJ >= 0) {
                    used[i] = true;
                    used[bestJ] = true;
                    Participant b = ordered.get(bestJ);
                    roundPairs.add(new Participant[]{a, b});
                    String pk = pairKey(a.id, b.id);
                    pairMeetCount.put(pk, pairMeetCount.getOrDefault(pk, 0) + 1);
                }
            }

            for (Participant[] pair : roundPairs) {
                Participant a = pair[0];
                Participant b = pair[1];
                String label = "第" + globalRoundNum + "轮 · " + a.name + " vs " + b.name;
                for (int g = 0; g < gpp; g++) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        cancelled = true;
                        break outer;
                    }
                    boolean aFirst = pickFirstPlayer(g, gpp, random);
                    RpgAiAutoDuelModels.DuelResult res = executor.run(
                            a, b, ruleSet, currentDate, aFirst, random);
                    recordDuelOutcome(a, b, res);
                    newCompletedGames++;
                    if (progressListener != null) {
                        progressListener.onProgress(
                                newCompletedGames, newTotalGames,
                                newCompletedPairs, newTotalPairs,
                                label + " (" + (g + 1) + "/" + gpp + ")");
                    }
                }
                newCompletedPairs++;
            }
            roundsCompletedThisRun++;
        }

        List<Participant> ranked = new ArrayList<>(participants != null ? participants : Collections.emptyList());
        computeBradleyTerryElo(ranked);
        sortStandings(ranked, true);

        long totalElapsed = priorElapsedMs + (System.currentTimeMillis() - t0);
        return new LeagueResult(
                MODE_SWISS_ELO,
                currentDate, ruleSet,
                priorRoundsPlayed + roundsCompletedThisRun,
                gpp,
                priorPairs + newTotalPairs,
                priorPairs + newCompletedPairs,
                priorGames + newTotalGames,
                priorGames + newCompletedGames,
                cancelled, totalElapsed, ranked);
    }

    /**
     * Runs the full round-robin league using the real in-process ocgcore headless runner on {@code bridge}.
     * Must be called off the main thread.
     */
    public static LeagueResult runRoundRobin(
            final RpgCatalog catalog,
            final RpgDuelHostBridge bridge,
            List<Participant> participants,
            OcgRuleSet ruleSet,
            String currentDate,
            int gamesPerPair,
            ProgressListener progressListener,
            AtomicBoolean cancelFlag,
            Random rng) {
        DuelExecutor executor = (a, b, rule, date, aFirst, random) -> bridge.runHeadlessOcgDuel(
                catalog,
                a.name, a.main, a.extra, a.style,
                b.name, b.main, b.extra, b.style,
                rule, date, aFirst, false, random);
        return runRoundRobin(participants, ruleSet, currentDate, gamesPerPair,
                executor, progressListener, cancelFlag, rng);
    }

    /**
     * Core pure-Java full round-robin runner (injectable {@link DuelExecutor} for deterministic testing).
     */
    public static LeagueResult runRoundRobin(
            List<Participant> participants,
            OcgRuleSet ruleSet,
            String currentDate,
            int gamesPerPair,
            DuelExecutor executor,
            ProgressListener progressListener,
            AtomicBoolean cancelFlag,
            Random rng) {
        Random random = rng != null ? rng : new Random();
        int gpp = Math.max(1, gamesPerPair);
        int n = participants != null ? participants.size() : 0;
        int totalPairs = n * (n - 1) / 2;
        int totalGames = totalPairs * gpp;
        long t0 = System.currentTimeMillis();

        int completedPairs = 0;
        int completedGames = 0;
        boolean cancelled = false;

        outer:
        for (int i = 0; i < n; i++) {
            Participant a = participants.get(i);
            for (int j = i + 1; j < n; j++) {
                Participant b = participants.get(j);
                String pairLabel = a.name + " vs " + b.name;
                for (int g = 0; g < gpp; g++) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        cancelled = true;
                        break outer;
                    }
                    boolean aFirst = pickFirstPlayer(g, gpp, random);
                    RpgAiAutoDuelModels.DuelResult res = executor.run(
                            a, b, ruleSet, currentDate, aFirst, random);
                    recordDuelOutcome(a, b, res);
                    completedGames++;
                    if (progressListener != null) {
                        progressListener.onProgress(
                                completedGames, totalGames,
                                completedPairs, totalPairs,
                                pairLabel + " (" + (g + 1) + "/" + gpp + ")");
                    }
                }
                completedPairs++;
            }
        }

        List<Participant> ranked = new ArrayList<>(participants != null ? participants : Collections.emptyList());
        computeBradleyTerryElo(ranked);
        sortStandings(ranked, false);

        long elapsed = System.currentTimeMillis() - t0;
        return new LeagueResult(
                MODE_ROUND_ROBIN,
                currentDate, ruleSet,
                Math.max(0, n - 1),
                gpp,
                totalPairs, completedPairs,
                totalGames, completedGames,
                cancelled, elapsed, ranked);
    }

    /**
     * Global Bayesian Bradley-Terry Maximum Likelihood Estimator (Hunter MM algorithm).
     * Order-independent, strength-of-schedule aware, and guaranteed to converge smoothly even
     * on undefeated (100%) or winless (0%) records thanks to a gentle 1500-Elo anchor prior.
     */
    public static void computeBradleyTerryElo(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) return;
        int n = participants.size();
        Map<String, Integer> idxById = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            idxById.put(participants.get(i).id, i);
        }

        double[] w = new double[n];
        double[][] games = new double[n][n];
        for (int i = 0; i < n; i++) {
            Participant p = participants.get(i);
            w[i] = p.wins + 0.5 * p.draws + PRIOR_WINS;
            for (Map.Entry<String, int[]> e : p.headToHead.entrySet()) {
                Integer j = idxById.get(e.getKey());
                if (j != null && j != i) {
                    int[] h = e.getValue();
                    games[i][j] = h[0] + h[1] + h[2];
                }
            }
        }

        double[] s = new double[n];
        for (int i = 0; i < n; i++) s[i] = 1.0;

        for (int iter = 0; iter < BT_ITERATIONS; iter++) {
            double[] next = new double[n];
            for (int i = 0; i < n; i++) {
                double denom = PRIOR_GAMES / (s[i] + 1.0);
                for (int j = 0; j < n; j++) {
                    if (games[i][j] > 0.0) {
                        denom += games[i][j] / (s[i] + s[j]);
                    }
                }
                next[i] = denom > 1e-12 ? (w[i] / denom) : 1.0;
            }
            // Keep geometric mean centered at 1.0 (Elo 1500)
            double logSum = 0.0;
            for (int i = 0; i < n; i++) {
                logSum += Math.log(Math.max(1e-9, next[i]));
            }
            double scale = Math.exp(-logSum / n);
            for (int i = 0; i < n; i++) {
                s[i] = Math.max(1e-6, next[i] * scale);
            }
        }

        for (int i = 0; i < n; i++) {
            Participant p = participants.get(i);
            double elo = BASE_ELO + ELO_SCALE * Math.log10(s[i]);
            p.eloRating = (int) Math.round(elo);
        }

        // Compute weighted average opponent Elo (Strength of Schedule / 对手均分)
        for (int i = 0; i < n; i++) {
            Participant p = participants.get(i);
            double sumElo = 0.0;
            double sumGames = 0.0;
            for (int j = 0; j < n; j++) {
                if (games[i][j] > 0.0) {
                    sumElo += games[i][j] * participants.get(j).eloRating;
                    sumGames += games[i][j];
                }
            }
            p.avgOpponentElo = sumGames > 0.0 ? (int) Math.round(sumElo / sumGames) : 1500;
        }
    }

    private static void sortStandings(List<Participant> list, final boolean primaryByElo) {
        Collections.sort(list, new Comparator<Participant>() {
            @Override
            public int compare(Participant p1, Participant p2) {
                int cmp;
                if (primaryByElo) {
                    cmp = Integer.compare(p2.eloRating, p1.eloRating);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(p2.wins, p1.wins);
                    if (cmp != 0) return cmp;
                } else {
                    cmp = Integer.compare(p2.wins, p1.wins);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(p2.eloRating, p1.eloRating);
                    if (cmp != 0) return cmp;
                }
                cmp = Double.compare(p2.getWinRatePercent(), p1.getWinRatePercent());
                if (cmp != 0) return cmp;
                cmp = Integer.compare(p2.draws, p1.draws);
                if (cmp != 0) return cmp;
                int[] h2h = p1.headToHead.get(p2.id);
                if (h2h != null && h2h[0] != h2h[1]) {
                    return Integer.compare(h2h[1], h2h[0]);
                }
                return p1.name.compareTo(p2.name);
            }
        });
    }

    private static boolean pickFirstPlayer(int gameIndex, int gamesPerPair, Random random) {
        if (gamesPerPair % 2 == 1 && gameIndex == gamesPerPair - 1) {
            return random.nextBoolean();
        }
        return (gameIndex % 2 == 0);
    }

    private static void recordDuelOutcome(Participant a, Participant b, RpgAiAutoDuelModels.DuelResult res) {
        if (res == null) {
            a.recordGame(b.id, false, true, 0);
            b.recordGame(a.id, false, true, 0);
        } else {
            a.recordGame(b.id, res.sideAWon, res.isDraw, res.turns);
            b.recordGame(a.id, !res.isDraw && !res.sideAWon, res.isDraw, res.turns);
        }
    }

    private static String pairKey(String idA, String idB) {
        return idA.compareTo(idB) <= 0 ? (idA + "|" + idB) : (idB + "|" + idA);
    }
}
