package cn.garymb.ygomobile.rpg.bridge;

import android.content.Context;

import cn.garymb.ygomobile.rpg.data.RpgRepository;
import cn.garymb.ygomobile.rpg.engine.DuelRewardEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Tracks active RPG duel sessions so YGOMobile's EngineCallbackDelegate.onDuelResult
 * can settle RPG rewards without affecting normal free duels or LAN play.
 */
public final class RpgSessionManager {
    private static volatile RpgDuelHostBridge sHostBridge;
    private static volatile AiOpponent sActiveOpponent;
    private static volatile DuelRewardEngine.RewardSettlement sLastSettlement;

    private RpgSessionManager() {}

    public static void registerHostBridge(RpgDuelHostBridge bridge) {
        sHostBridge = bridge;
    }

    public static RpgDuelHostBridge getHostBridge() {
        return sHostBridge;
    }

    public static synchronized void beginRpgDuel(AiOpponent opponent) {
        sActiveOpponent = opponent;
        sLastSettlement = null;
    }

    public static synchronized boolean hasActiveRpgDuel() {
        return sActiveOpponent != null;
    }

    public static synchronized void cancelActiveRpgDuel() {
        sActiveOpponent = null;
    }

    /**
     * Called from EngineCallbackDelegate.onDuelResult when a duel finishes.
     * Consumes the active RPG duel session and persists token rewards.
     */
    public static synchronized DuelRewardEngine.RewardSettlement onDuelFinished(
            Context context, boolean playerWon, boolean isDraw) {
        if (sActiveOpponent == null) {
            return null;
        }
        AiOpponent opp = sActiveOpponent;
        sActiveOpponent = null;

        RpgRepository repo = new RpgRepository(context.getApplicationContext());
        RpgProfile profile = repo.loadProfile();
        DuelRewardEngine.RewardSettlement settlement =
                DuelRewardEngine.settle(profile, opp, playerWon, isDraw);
        try {
            repo.saveProfile(profile);
        } catch (Exception ignored) {
        }
        sLastSettlement = settlement;
        return settlement;
    }

    public static synchronized DuelRewardEngine.RewardSettlement consumeLastSettlement() {
        DuelRewardEngine.RewardSettlement s = sLastSettlement;
        sLastSettlement = null;
        return s;
    }
}
