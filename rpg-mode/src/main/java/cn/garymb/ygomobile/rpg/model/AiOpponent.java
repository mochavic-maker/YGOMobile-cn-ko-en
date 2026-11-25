package cn.garymb.ygomobile.rpg.model;

/**
 * Timeline-aware AI opponent definition.
 */
public class AiOpponent {
    public final String id;
    public final String name;
    public final String title;
    public final String unlockDate;
    public final int difficulty; // 1 = Beginner, 2 = Standard, 3 = Ace
    public final int winRewardTokens;
    public final int drawRewardTokens;
    public final int lossRewardTokens;
    public final String strategyHint;

    public AiOpponent(String id, String name, String title, String unlockDate,
                      int difficulty, int winRewardTokens, int drawRewardTokens,
                      int lossRewardTokens, String strategyHint) {
        this.id = id;
        this.name = name;
        this.title = title;
        this.unlockDate = unlockDate;
        this.difficulty = difficulty;
        this.winRewardTokens = winRewardTokens;
        this.drawRewardTokens = drawRewardTokens;
        this.lossRewardTokens = lossRewardTokens;
        this.strategyHint = strategyHint;
    }
}
