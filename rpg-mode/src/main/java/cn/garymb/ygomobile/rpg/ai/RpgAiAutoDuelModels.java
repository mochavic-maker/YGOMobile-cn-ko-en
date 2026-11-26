package cn.garymb.ygomobile.rpg.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Shared result, replay frame, and executor contracts for real ocgcore AI-vs-AI duels
 * driven by {@link RpgAiStrategy} on both sides (replaces legacy QuickDuelSimulator).
 */
public final class RpgAiAutoDuelModels {
    private RpgAiAutoDuelModels() {}

    public static class ReplayFrame {
        public final int stepIndex;
        public final int turn;
        public final String phase;
        public final String activeName;
        public final int sideALp;
        public final int sideBLp;
        public final int sideAHandCount;
        public final int sideBHandCount;
        public final int sideADeckCount;
        public final int sideBDeckCount;
        public final List<String> sideAMonsters;
        public final List<String> sideBMonsters;
        public final List<String> newLogs;

        public ReplayFrame(int stepIndex, int turn, String phase, String activeName,
                           int sideALp, int sideBLp,
                           int sideAHandCount, int sideBHandCount,
                           int sideADeckCount, int sideBDeckCount,
                           List<String> sideAMonsters, List<String> sideBMonsters,
                           List<String> newLogs) {
            this.stepIndex = stepIndex;
            this.turn = turn;
            this.phase = phase;
            this.activeName = activeName;
            this.sideALp = sideALp;
            this.sideBLp = sideBLp;
            this.sideAHandCount = sideAHandCount;
            this.sideBHandCount = sideBHandCount;
            this.sideADeckCount = sideADeckCount;
            this.sideBDeckCount = sideBDeckCount;
            this.sideAMonsters = sideAMonsters != null ? sideAMonsters : new ArrayList<String>();
            this.sideBMonsters = sideBMonsters != null ? sideBMonsters : new ArrayList<String>();
            this.newLogs = newLogs != null ? newLogs : new ArrayList<String>();
        }
    }

    public static class DuelResult {
        public final boolean sideAWon;
        public final boolean isDraw;
        public final int sideALp;
        public final int sideBLp;
        public final int turns;
        public final OcgRuleSet appliedRule;
        public final List<String> logs;
        public final String sideAName;
        public final String sideBName;
        public final String sideAStyle;
        public final String sideBStyle;
        public final List<ReplayFrame> replayFrames;
        /** Absolute path to recorded V2 .yrp replay file for native 3D YGOMobile ReplayPlayer playback (may be null). */
        public final String replayFilePath;

        public DuelResult(boolean sideAWon, boolean isDraw, int sideALp, int sideBLp,
                          int turns, OcgRuleSet appliedRule, List<String> logs,
                          String sideAName, String sideBName,
                          String sideAStyle, String sideBStyle,
                          List<ReplayFrame> replayFrames) {
            this(sideAWon, isDraw, sideALp, sideBLp, turns, appliedRule, logs,
                    sideAName, sideBName, sideAStyle, sideBStyle, replayFrames, null);
        }

        public DuelResult(boolean sideAWon, boolean isDraw, int sideALp, int sideBLp,
                          int turns, OcgRuleSet appliedRule, List<String> logs,
                          String sideAName, String sideBName,
                          String sideAStyle, String sideBStyle,
                          List<ReplayFrame> replayFrames,
                          String replayFilePath) {
            this.sideAWon = sideAWon;
            this.isDraw = isDraw;
            this.sideALp = sideALp;
            this.sideBLp = sideBLp;
            this.turns = turns;
            this.appliedRule = appliedRule;
            this.logs = logs != null ? logs : new ArrayList<String>();
            this.sideAName = sideAName;
            this.sideBName = sideBName;
            this.sideAStyle = sideAStyle;
            this.sideBStyle = sideBStyle;
            this.replayFrames = replayFrames != null ? replayFrames : new ArrayList<ReplayFrame>();
            this.replayFilePath = replayFilePath;
        }
    }

    public static class BatchResult {
        public final String sideAName;
        public final String sideBName;
        public final String sideAStyle;
        public final String sideBStyle;
        public final String timelineDate;
        public final OcgRuleSet appliedRule;
        public final int totalGames;
        public final int sideAWins;
        public final int sideBWins;
        public final int draws;
        public final int sideAFirstGames;
        public final int sideAFirstWins;
        public final int sideASecondGames;
        public final int sideASecondWins;
        public final double avgTurns;
        public final long elapsedMs;
        public final DuelResult sampleLastResult;

        public BatchResult(String sideAName, String sideBName,
                           String sideAStyle, String sideBStyle,
                           String timelineDate, OcgRuleSet appliedRule,
                           int totalGames, int sideAWins, int sideBWins, int draws,
                           int sideAFirstGames, int sideAFirstWins,
                           int sideASecondGames, int sideASecondWins,
                           double avgTurns, long elapsedMs,
                           DuelResult sampleLastResult) {
            this.sideAName = sideAName;
            this.sideBName = sideBName;
            this.sideAStyle = sideAStyle;
            this.sideBStyle = sideBStyle;
            this.timelineDate = timelineDate;
            this.appliedRule = appliedRule;
            this.totalGames = totalGames;
            this.sideAWins = sideAWins;
            this.sideBWins = sideBWins;
            this.draws = draws;
            this.sideAFirstGames = sideAFirstGames;
            this.sideAFirstWins = sideAFirstWins;
            this.sideASecondGames = sideASecondGames;
            this.sideASecondWins = sideASecondWins;
            this.avgTurns = avgTurns;
            this.elapsedMs = elapsedMs;
            this.sampleLastResult = sampleLastResult;
        }

        public double getSideAWinRate() {
            return totalGames > 0 ? (sideAWins * 100.0 / totalGames) : 0.0;
        }

        public double getSideBWinRate() {
            return totalGames > 0 ? (sideBWins * 100.0 / totalGames) : 0.0;
        }

        public double getSideAFirstWinRate() {
            return sideAFirstGames > 0 ? (sideAFirstWins * 100.0 / sideAFirstGames) : 0.0;
        }

        public double getSideASecondWinRate() {
            return sideASecondGames > 0 ? (sideASecondWins * 100.0 / sideASecondGames) : 0.0;
        }
    }

    public interface ProgressListener {
        void onProgress(int completed, int total, int sideAWins, int sideBWins, int draws);
    }

    /**
     * Abstraction for running a single real ocgcore AI-vs-AI match.
     * Production implementation runs native OcgDuelEngine via RpgOcgHeadlessDuelRunner;
     * JVM unit tests can provide a deterministic stub.
     */
    public interface AutoDuelExecutor {
        DuelResult runSingleDuel(RpgCatalog catalog,
                                 String sideAName, List<Integer> mainA, List<Integer> extraA, String styleA,
                                 String sideBName, List<Integer> mainB, List<Integer> extraB, String styleB,
                                 OcgRuleSet ruleSet, String currentDate,
                                 boolean sideAFirst, boolean recordReplay, Random rng);
    }
}
