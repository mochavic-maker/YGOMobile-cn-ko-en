package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent player save data for the OCG RPG progression mode.
 */
public class RpgProfile {
    public static final int DEFAULT_STARTING_TOKENS = 1200;
    public static final String DEFAULT_START_DATE = "1999-02-04";

    public int tokens = DEFAULT_STARTING_TOKENS;
    public int dust = 120;
    /** Player-controlled timeline date (YYYY-MM-DD). */
    public String currentTimelineDate = DEFAULT_START_DATE;

    /** Owned card counts keyed by 8-digit Konami passcode. */
    public Map<Integer, Integer> collection = new HashMap<>();
    /** Pack opening counts keyed by pack id. */
    public Map<String, Integer> packsOpened = new HashMap<>();
    /** Consecutive packs opened without pulling R or higher. */
    public int packsSinceLastRare = 0;

    public int wins = 0;
    public int losses = 0;
    public int draws = 0;

    /** Active RPG deck (card passcodes). */
    public List<Integer> activeDeckMain = new ArrayList<>();
    public List<Integer> activeDeckExtra = new ArrayList<>();

    public int getOwnedCount(int cardId) {
        Integer count = collection.get(cardId);
        return count != null ? count : 0;
    }

    public void addCard(int cardId, int delta) {
        int next = Math.max(0, getOwnedCount(cardId) + delta);
        if (next == 0) {
            collection.remove(cardId);
        } else {
            collection.put(cardId, next);
        }
    }

    public int getTotalOwnedCards() {
        int sum = 0;
        for (Integer c : collection.values()) {
            if (c != null) sum += c;
        }
        return sum;
    }
}
