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
}
