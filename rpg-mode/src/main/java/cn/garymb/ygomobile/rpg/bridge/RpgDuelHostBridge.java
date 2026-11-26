package cn.garymb.ygomobile.rpg.bridge;

import android.content.Context;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;

/**
 * Host app bridge implemented by mobile/ (keeps :rpg-mode decoupled from YGOMobile internals).
 */
public interface RpgDuelHostBridge {
    /**
     * Launches a local WindBot duel using the player's validated RPG .ydk deck,
     * timeline-compliant AI .ydk deck, and the latest OCG rule version at currentTimelineDate.
     */
    void startRpgAiDuel(Context context,
                        AiOpponent opponent,
                        String playerDeckYdkPath,
                        String aiDeckYdkPath,
                        OcgBanlist activeBanlist,
                        OcgRuleSet activeRuleSet,
                        String currentTimelineDate);

    /**
     * Opens YGOMobile's built-in visual Deck Editor loaded with the player's current RPG .ydk deck,
     * restricted to {@code allowedCardCaps} (cardId -> max legal copies = min(owned, banlistMax)).
     * When the user exits the native deck editor, the edited .ydk is parsed back into RpgSessionManager
     * and RpgHomeActivity is reopened on the Deck tab.
     */
    default void openNativeDeckEditor(Context context,
                                      String playerDeckYdkPath,
                                      java.util.Map<Integer, Integer> allowedCardCaps) {
    }

    /**
     * Opens YGOMobile's built-in visual Deck Editor in strict read-only mode to inspect
     * an AI opponent's deck, then returns to RPG Mode's AI Duel tab upon exit.
     */
    default void openReadonlyAiDeckViewer(Context context,
                                          String aiDeckYdkPath,
                                          String displayTitle) {
    }

    /**
     * Loads raw card artwork image bytes (JPG/PNG) for the given YGOPro card passcode
     * from YGOMobile's pics.zip / expansions / pics directory. Must be called off the main thread.
     */
    default byte[] loadCardImageBytes(int cardId) {
        return null;
    }

    /**
     * Runs a single real ocgcore AI-vs-AI duel in-process using {@code RpgAiStrategy} on both sides
     * and the active historical {@link OcgRuleSet}. Must be called off the main thread.
     */
    default cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.DuelResult runHeadlessOcgDuel(
            cn.garymb.ygomobile.rpg.model.RpgCatalog catalog,
            String sideAName, java.util.List<Integer> mainA, java.util.List<Integer> extraA, String styleA,
            String sideBName, java.util.List<Integer> mainB, java.util.List<Integer> extraB, String styleB,
            OcgRuleSet activeRuleSet,
            String currentTimelineDate,
            boolean sideAFirst,
            boolean recordReplay,
            java.util.Random rng) {
        return null;
    }

    /**
     * Runs {@code gameCount} real ocgcore AI-vs-AI duels (alternating first/second 50/50)
     * using {@code RpgAiStrategy} on both sides. Must be called off the main thread.
     */
    default cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.BatchResult runHeadlessOcgBatch(
            cn.garymb.ygomobile.rpg.model.RpgCatalog catalog,
            String sideAName, java.util.List<Integer> mainA, java.util.List<Integer> extraA, String styleA,
            String sideBName, java.util.List<Integer> mainB, java.util.List<Integer> extraB, String styleB,
            OcgRuleSet activeRuleSet,
            String currentTimelineDate,
            int gameCount,
            cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.ProgressListener progressListener,
            java.util.Random rng) {
        return null;
    }

    /**
     * Starts a live 3D spectate duel in YGOMobile: the local host moves to observer, and two
     * dedicated {@code RpgAiDuelClient} bots join Seat 0 (Side A) and Seat 1 (Side B).
     */
    default void startRpgAiSpectateDuel(
            Context context,
            String sideAName, java.util.List<Integer> mainA, java.util.List<Integer> extraA, String styleA,
            String sideBName, java.util.List<Integer> mainB, java.util.List<Integer> extraB, String styleB,
            OcgRuleSet activeRuleSet,
            String currentTimelineDate) {
    }
}
