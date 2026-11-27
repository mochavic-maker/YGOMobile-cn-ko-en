package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * Runs a full-environment round-robin AI league where every unlocked AI opponent at the active
 * timeline date (plus optionally the player's current RPG deck piloted by AI) plays every other
 * contestant for {@code gamesPerPair} (default 5) real ocgcore headless duels, then ranks all
 * contestants by total wins (and win rate).
 */
public final class RoundRobinLeagueEngine {
    public static final int DEFAULT_GAMES_PER_PAIR = 5;

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
        public final String timelineDate;
        public final OcgRuleSet ruleSet;
        public final int gamesPerPair;
        public final int totalPairs;
        public final int completedPairs;
        public final int totalGames;
        public final int completedGames;
        public final boolean cancelled;
        public final long elapsedMs;
        public final List<Participant> standings;

        public LeagueResult(String timelineDate, OcgRuleSet ruleSet, int gamesPerPair,
                            int totalPairs, int completedPairs,
                            int totalGames, int completedGames,
                            boolean cancelled, long elapsedMs,
                            List<Participant> standings) {
            this.timelineDate = timelineDate;
            this.ruleSet = ruleSet;
            this.gamesPerPair = gamesPerPair;
            this.totalPairs = totalPairs;
            this.completedPairs = completedPairs;
            this.totalGames = totalGames;
            this.completedGames = completedGames;
            this.cancelled = cancelled;
            this.elapsedMs = elapsedMs;
            this.standings = standings;
        }

        public String formatPlainTextReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("【全环境 AI 循环赛排行榜】\n");
            sb.append("时间轴：").append(timelineDate)
                    .append(" · 规则：").append(ruleSet != null ? ruleSet.name : "")
                    .append("\n");
            sb.append("参赛选手：").append(standings.size())
                    .append(" 名 · 每对 ").append(gamesPerPair).append(" 局 · 完成 ")
                    .append(completedGames).append("/").append(totalGames).append(" 局")
                    .append(String.format(Locale.US, "（耗时 %.1fs）", elapsedMs / 1000.0));
            if (cancelled) sb.append(" [已中途取消]");
            sb.append("\n\n");
            for (int i = 0; i < standings.size(); i++) {
                Participant p = standings.get(i);
                sb.append(String.format(Locale.US, "#%d %s%s — %d胜 %d负 %d平 (胜率 %.1f%%)",
                        i + 1,
                        p.isPlayer ? "★ " : "",
                        p.name,
                        p.wins, p.losses, p.draws,
                        p.getWinRatePercent()));
                if (!p.deckName.isEmpty()) {
                    sb.append(" · ").append(p.deckName);
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }
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
     * Runs the round-robin league using the real in-process ocgcore headless runner on {@code bridge}.
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
     * Core pure-Java round-robin runner (injectable {@link DuelExecutor} for deterministic testing).
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
                    // For odd gamesPerPair (e.g. 5): alternate 0,1,2,3 fairly (2 first, 2 second),
                    // and randomize the final deciding game!
                    boolean aFirst;
                    if (gpp % 2 == 1 && g == gpp - 1) {
                        aFirst = random.nextBoolean();
                    } else {
                        aFirst = (g % 2 == 0);
                    }
                    RpgAiAutoDuelModels.DuelResult res = executor.run(
                            a, b, ruleSet, currentDate, aFirst, random);
                    if (res == null) {
                        a.recordGame(b.id, false, true, 0);
                        b.recordGame(a.id, false, true, 0);
                    } else {
                        a.recordGame(b.id, res.sideAWon, res.isDraw, res.turns);
                        b.recordGame(a.id, !res.isDraw && !res.sideAWon, res.isDraw, res.turns);
                    }
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
        Collections.sort(ranked, new Comparator<Participant>() {
            @Override
            public int compare(Participant p1, Participant p2) {
                // 1) Primary: total wins descending
                int cmp = Integer.compare(p2.wins, p1.wins);
                if (cmp != 0) return cmp;
                // 2) Win rate descending (matters if cancelled mid-run)
                cmp = Double.compare(p2.getWinRatePercent(), p1.getWinRatePercent());
                if (cmp != 0) return cmp;
                // 3) Draws descending
                cmp = Integer.compare(p2.draws, p1.draws);
                if (cmp != 0) return cmp;
                // 4) Direct head-to-head advantage
                int[] h2h = p1.headToHead.get(p2.id);
                if (h2h != null && h2h[0] != h2h[1]) {
                    return Integer.compare(h2h[1], h2h[0]);
                }
                // 5) Stable name order
                return p1.name.compareTo(p2.name);
            }
        });

        long elapsed = System.currentTimeMillis() - t0;
        return new LeagueResult(
                currentDate, ruleSet, gpp,
                totalPairs, completedPairs,
                totalGames, completedGames,
                cancelled, elapsed, ranked);
    }
}
