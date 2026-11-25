package cn.garymb.ygomobile.rpg.engine;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Settles token rewards after an RPG AI duel concludes.
 */
public final class DuelRewardEngine {
    private DuelRewardEngine() {}

    public static class RewardSettlement {
        public final String outcomeLabel;
        public final int tokensEarned;
        public final int totalTokensAfter;

        public RewardSettlement(String outcomeLabel, int tokensEarned, int totalTokensAfter) {
            this.outcomeLabel = outcomeLabel;
            this.tokensEarned = tokensEarned;
            this.totalTokensAfter = totalTokensAfter;
        }
    }

    public static RewardSettlement settle(RpgProfile profile, AiOpponent opponent,
                                          boolean playerWon, boolean isDraw) {
        int earned;
        String label;
        if (isDraw) {
            earned = opponent.drawRewardTokens;
            profile.draws++;
            label = "平局";
        } else if (playerWon) {
            earned = opponent.winRewardTokens;
            profile.wins++;
            label = "胜利";
        } else {
            earned = opponent.lossRewardTokens;
            profile.losses++;
            label = "败北（参与奖励）";
        }
        profile.tokens += earned;
        return new RewardSettlement(label, earned, profile.tokens);
    }
}
