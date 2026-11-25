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
}
