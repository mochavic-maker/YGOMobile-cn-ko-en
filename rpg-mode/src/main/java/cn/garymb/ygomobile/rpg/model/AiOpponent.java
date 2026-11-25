package cn.garymb.ygomobile.rpg.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Timeline-aware AI opponent with multiple historical deck evolutions (modeled on yugioh-history.com Series 1).
 * When the timeline moves, getActiveDeckProfile(currentDate) automatically switches the opponent's active deck.
 */
public class AiOpponent {
    public final String id;
    public final String name;
    public final String title;
    public final String unlockDate;
    public final int difficulty; // 1 = Beginner, 2 = Standard, 3 = Ace, 4 = Boss
    public final int winRewardTokens;
    public final int drawRewardTokens;
    public final int lossRewardTokens;
    public final String strategyHint;
    public final List<EraDeckProfile> deckEvolutions;

    public static class EraDeckProfile {
        public final String effectiveDate;
        public final String deckName;
        public final String eraTag;
        public final String description;
        public final Map<Integer, Integer> coreMain;
        public final Map<Integer, Integer> coreExtra;
        public final String fillStyle;

        public EraDeckProfile(String effectiveDate, String deckName, String eraTag, String description,
                              Map<Integer, Integer> coreMain, Map<Integer, Integer> coreExtra, String fillStyle) {
            this.effectiveDate = effectiveDate;
            this.deckName = deckName;
            this.eraTag = eraTag;
            this.description = description;
            this.coreMain = coreMain != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(coreMain))
                    : Collections.emptyMap();
            this.coreExtra = coreExtra != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(coreExtra))
                    : Collections.emptyMap();
            this.fillStyle = fillStyle != null ? fillStyle : "GOOD_STUFF";
        }
    }

    public AiOpponent(String id, String name, String title, String unlockDate,
                      int difficulty, int winRewardTokens, int drawRewardTokens,
                      int lossRewardTokens, String strategyHint,
                      List<EraDeckProfile> deckEvolutions) {
        this.id = id;
        this.name = name;
        this.title = title;
        this.unlockDate = unlockDate;
        this.difficulty = difficulty;
        this.winRewardTokens = winRewardTokens;
        this.drawRewardTokens = drawRewardTokens;
        this.lossRewardTokens = lossRewardTokens;
        this.strategyHint = strategyHint;
        this.deckEvolutions = deckEvolutions != null
                ? Collections.unmodifiableList(deckEvolutions)
                : Collections.emptyList();
    }

    /** Automatically picks the latest historical deck evolution whose effectiveDate <= currentDate. */
    public EraDeckProfile getActiveDeckProfile(String currentDate) {
        if (deckEvolutions == null || deckEvolutions.isEmpty()) return null;
        EraDeckProfile best = null;
        for (EraDeckProfile p : deckEvolutions) {
            if (currentDate != null && p.effectiveDate != null && p.effectiveDate.compareTo(currentDate) <= 0) {
                if (best == null || p.effectiveDate.compareTo(best.effectiveDate) >= 0) {
                    best = p;
                }
            }
        }
        return best != null ? best : deckEvolutions.get(0);
    }
}
