package cn.garymb.ygomobile.rpg.bridge;

import android.content.Context;

import cn.garymb.ygomobile.rpg.data.RpgRepository;
import cn.garymb.ygomobile.rpg.engine.DuelRewardEngine;
import cn.garymb.ygomobile.rpg.engine.TournamentEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Tracks active RPG duel sessions & tournament brackets so YGOMobile's
 * EngineCallbackDelegate.onDuelResult can settle RPG & tournament rewards cleanly.
 */
public final class RpgSessionManager {
    private static volatile RpgDuelHostBridge sHostBridge;
    private static volatile cn.garymb.ygomobile.rpg.model.RpgCatalog sCachedCatalog;
    private static volatile AiOpponent sActiveOpponent;
    private static volatile boolean sActiveDuelIsTournament;
    private static volatile int sActiveDuelReturnTab = 4;
    private static volatile String sActiveCanonDuelId;
    private static volatile String sActiveCanonDuelMode;
    private static volatile boolean sLastDuelWasTournament;
    private static volatile int sLastDuelReturnTab = 4;
    private static volatile boolean sLastDuelPlayerWon;
    private static volatile DuelRewardEngine.RewardSettlement sLastSettlement;
    private static volatile TournamentEngine.TournamentBracket sActiveTournament;

    private RpgSessionManager() {}

    public static void registerHostBridge(RpgDuelHostBridge bridge) {
        sHostBridge = bridge;
    }

    public static RpgDuelHostBridge getHostBridge() {
        return sHostBridge;
    }

    public static void setCachedCatalog(cn.garymb.ygomobile.rpg.model.RpgCatalog catalog) {
        sCachedCatalog = catalog;
    }

    public static cn.garymb.ygomobile.rpg.model.RpgCatalog getCachedCatalog() {
        return sCachedCatalog;
    }

    public static synchronized void setActiveTournament(TournamentEngine.TournamentBracket bracket) {
        sActiveTournament = bracket;
    }

    public static synchronized TournamentEngine.TournamentBracket getActiveTournament() {
        return sActiveTournament;
    }

    public static synchronized void beginRpgDuel(AiOpponent opponent) {
        beginRpgDuel(opponent, false, 4);
    }

    public static synchronized void beginRpgDuel(AiOpponent opponent, boolean isTournamentMatch) {
        beginRpgDuel(opponent, isTournamentMatch, isTournamentMatch ? 5 : 4);
    }

    public static synchronized void beginRpgDuel(AiOpponent opponent, boolean isTournamentMatch, int returnTab) {
        sActiveOpponent = opponent;
        sActiveDuelIsTournament = isTournamentMatch;
        sActiveDuelReturnTab = returnTab;
        sActiveCanonDuelId = null;
        sActiveCanonDuelMode = null;
        sLastSettlement = null;
        sLastDuelWasTournament = false;
    }

    public static synchronized void beginRpgCanonDuel(AiOpponent opponent,
                                                      String canonDuelId,
                                                      String canonMode,
                                                      int returnTab) {
        sActiveOpponent = opponent;
        sActiveDuelIsTournament = false;
        sActiveDuelReturnTab = returnTab;
        sActiveCanonDuelId = canonDuelId;
        sActiveCanonDuelMode = canonMode;
        sLastSettlement = null;
        sLastDuelWasTournament = false;
    }

    public static synchronized boolean hasActiveRpgDuel() {
        return sActiveOpponent != null;
    }

    public static synchronized void cancelActiveRpgDuel() {
        sActiveOpponent = null;
        sActiveDuelIsTournament = false;
        sActiveDuelReturnTab = 4;
        sActiveCanonDuelId = null;
        sActiveCanonDuelMode = null;
    }

    /**
     * Called from EngineCallbackDelegate.onDuelResult when a duel finishes.
     * Consumes the active RPG duel session, persists token rewards, and records canon duel clear badges.
     */
    public static synchronized DuelRewardEngine.RewardSettlement onDuelFinished(
            Context context, boolean playerWon, boolean isDraw) {
        if (sActiveOpponent == null) {
            return null;
        }
        AiOpponent opp = sActiveOpponent;
        boolean wasTournament = sActiveDuelIsTournament;
        int returnTab = sActiveDuelReturnTab;
        String canonDuelId = sActiveCanonDuelId;
        String canonDuelMode = sActiveCanonDuelMode;
        sActiveOpponent = null;
        sActiveDuelIsTournament = false;
        sActiveDuelReturnTab = 4;
        sActiveCanonDuelId = null;
        sActiveCanonDuelMode = null;

        RpgRepository repo = new RpgRepository(context.getApplicationContext());
        RpgProfile profile = repo.loadProfile();
        DuelRewardEngine.RewardSettlement settlement =
                DuelRewardEngine.settle(profile, opp, playerWon, isDraw);
        if (playerWon && !isDraw && canonDuelId != null && canonDuelMode != null) {
            profile.recordCanonDuelClear(canonDuelId, canonDuelMode);
        }
        try {
            repo.saveProfile(profile);
        } catch (Exception ignored) {
        }
        sLastSettlement = settlement;
        sLastDuelWasTournament = wasTournament;
        sLastDuelReturnTab = returnTab;
        sLastDuelPlayerWon = playerWon;
        return settlement;
    }

    public static class EditedDeckResult {
        public final java.util.List<Integer> main;
        public final java.util.List<Integer> extra;

        public EditedDeckResult(java.util.List<Integer> main, java.util.List<Integer> extra) {
            this.main = main != null ? new java.util.ArrayList<>(main) : new java.util.ArrayList<>();
            this.extra = extra != null ? new java.util.ArrayList<>(extra) : new java.util.ArrayList<>();
        }
    }

    private static volatile EditedDeckResult sPendingEditedDeck;

    public static synchronized boolean isLastDuelTournament() {
        return sLastDuelWasTournament;
    }

    public static synchronized int getLastDuelReturnTab() {
        return sLastDuelReturnTab;
    }

    public static synchronized void setPendingEditedDeck(
            java.util.List<Integer> main, java.util.List<Integer> extra) {
        sPendingEditedDeck = new EditedDeckResult(main, extra);
    }

    public static synchronized EditedDeckResult consumePendingEditedDeck() {
        EditedDeckResult res = sPendingEditedDeck;
        sPendingEditedDeck = null;
        return res;
    }

    public static synchronized DuelRewardEngine.RewardSettlement consumeLastSettlement() {
        DuelRewardEngine.RewardSettlement s = sLastSettlement;
        sLastSettlement = null;
        return s;
    }

    public static synchronized Boolean consumeTournamentDuelOutcome() {
        if (!sLastDuelWasTournament) return null;
        sLastDuelWasTournament = false;
        return sLastDuelPlayerWon;
    }
}
