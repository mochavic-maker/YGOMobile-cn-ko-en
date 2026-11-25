package cn.garymb.ygomobile.rpg.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent player save data for the OCG RPG progression mode.
 */
public class RpgProfile {
    /** 1500 tokens = 10 packs of Vol.1 at historical 150 JPY/pack. */
    public static final int DEFAULT_STARTING_TOKENS = 1500;
    public static final String DEFAULT_START_DATE = "1999-02-04";

    public int tokens = DEFAULT_STARTING_TOKENS;
    public int dust = 120;
    /** Player-controlled timeline date (YYYY-MM-DD). */
    public String currentTimelineDate = DEFAULT_START_DATE;

    /** Owned card counts keyed by 8-digit Konami passcode. */
    public Map<Integer, Integer> collection = new HashMap<>();
    /** Pack opening counts keyed by pack id. */
    public Map<String, Integer> packsOpened = new HashMap<>();
    /** Claimed historical special promo event IDs. */
    public Set<String> claimedEventIds = new LinkedHashSet<>();
    /** Consecutive packs opened without pulling R or higher. */
    public int packsSinceLastRare = 0;

    public static final int RECENT_MATCH_WINDOW = 20;

    public int wins = 0;
    public int losses = 0;
    public int draws = 0;
    public int tournamentWins = 0;
    public int tournamentsPlayed = 0;

    /**
     * Per-(timelineDate|opponentId) rolling list of up to 20 most recent duel outcomes ("W", "L", "D").
     */
    public Map<String, List<String>> recentMatchHistory = new HashMap<>();

    /** Active RPG deck (card passcodes). */
    public List<Integer> activeDeckMain = new ArrayList<>();
    public List<Integer> activeDeckExtra = new ArrayList<>();

    /** Multi-deck saved presets for easy switching and editing. */
    public List<SavedDeck> savedDecks = new ArrayList<>();
    /** Optional ID of the saved deck currently loaded in the editor. */
    public String activeDeckId = null;

    public static class SavedDeck {
        public String id;
        public String name;
        public List<Integer> main = new ArrayList<>();
        public List<Integer> extra = new ArrayList<>();
        public String savedAtDate;
        public long updatedAt;

        public SavedDeck() {
        }

        public SavedDeck(String id, String name, List<Integer> main, List<Integer> extra, String savedAtDate, long updatedAt) {
            this.id = id;
            this.name = name;
            this.main = main != null ? new ArrayList<>(main) : new ArrayList<>();
            this.extra = extra != null ? new ArrayList<>(extra) : new ArrayList<>();
            this.savedAtDate = savedAtDate;
            this.updatedAt = updatedAt;
        }
    }

    public SavedDeck findSavedDeck(String deckId) {
        if (savedDecks == null || deckId == null) return null;
        for (SavedDeck d : savedDecks) {
            if (d != null && deckId.equals(d.id)) return d;
        }
        return null;
    }

    /**
     * Saves the current active deck under the given name.
     * If a saved deck with the exact same trimmed name already exists, it is updated in place;
     * otherwise a new deck slot is created.
     */
    public SavedDeck saveCurrentDeck(String rawName) {
        if (savedDecks == null) {
            savedDecks = new ArrayList<>();
        }
        String name = rawName != null ? rawName.trim() : "";
        if (name.isEmpty()) {
            name = "我的卡组 " + (savedDecks.size() + 1);
        }
        long now = System.currentTimeMillis();
        for (SavedDeck existing : savedDecks) {
            if (existing != null && name.equals(existing.name)) {
                existing.main = new ArrayList<>(activeDeckMain != null ? activeDeckMain : new ArrayList<>());
                existing.extra = new ArrayList<>(activeDeckExtra != null ? activeDeckExtra : new ArrayList<>());
                existing.savedAtDate = currentTimelineDate;
                existing.updatedAt = now;
                activeDeckId = existing.id;
                return existing;
            }
        }
        String id = "deck_" + now + "_" + (savedDecks.size() + 1);
        SavedDeck created = new SavedDeck(
                id,
                name,
                activeDeckMain,
                activeDeckExtra,
                currentTimelineDate,
                now
        );
        savedDecks.add(created);
        activeDeckId = created.id;
        return created;
    }

    /** Overwrites a specific existing saved deck slot with the current active deck contents. */
    public boolean overwriteSavedDeck(String deckId) {
        SavedDeck target = findSavedDeck(deckId);
        if (target == null) return false;
        target.main = new ArrayList<>(activeDeckMain != null ? activeDeckMain : new ArrayList<>());
        target.extra = new ArrayList<>(activeDeckExtra != null ? activeDeckExtra : new ArrayList<>());
        target.savedAtDate = currentTimelineDate;
        target.updatedAt = System.currentTimeMillis();
        activeDeckId = target.id;
        return true;
    }

    /** Loads a saved deck into the active deck editor (defensive copy). */
    public boolean loadSavedDeck(String deckId) {
        SavedDeck target = findSavedDeck(deckId);
        if (target == null) return false;
        activeDeckMain = new ArrayList<>(target.main != null ? target.main : new ArrayList<>());
        activeDeckExtra = new ArrayList<>(target.extra != null ? target.extra : new ArrayList<>());
        activeDeckId = target.id;
        return true;
    }

    /** Deletes a saved deck slot without wiping the current active editing buffer. */
    public boolean deleteSavedDeck(String deckId) {
        if (savedDecks == null || deckId == null) return false;
        for (int i = 0; i < savedDecks.size(); i++) {
            SavedDeck d = savedDecks.get(i);
            if (d != null && deckId.equals(d.id)) {
                savedDecks.remove(i);
                if (deckId.equals(activeDeckId)) {
                    activeDeckId = null;
                }
                return true;
            }
        }
        return false;
    }

    public static class RecentMatchStats {
        public final int total;
        public final int wins;
        public final int losses;
        public final int draws;
        public final double winRatePercent;

        public RecentMatchStats(int total, int wins, int losses, int draws) {
            this.total = total;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.winRatePercent = total > 0 ? (wins * 100.0 / total) : 0.0;
        }
    }

    public static String recentHistoryKey(String timelineDate, String opponentId) {
        return (timelineDate != null ? timelineDate : DEFAULT_START_DATE) + "|" + (opponentId != null ? opponentId : "");
    }

    public void recordMatchOutcome(String timelineDate, String opponentId, boolean playerWon, boolean isDraw) {
        if (opponentId == null || opponentId.isEmpty()) return;
        if (recentMatchHistory == null) {
            recentMatchHistory = new HashMap<>();
        }
        String key = recentHistoryKey(timelineDate, opponentId);
        List<String> list = recentMatchHistory.get(key);
        if (list == null) {
            list = new ArrayList<>();
            recentMatchHistory.put(key, list);
        }
        list.add(isDraw ? "D" : (playerWon ? "W" : "L"));
        while (list.size() > RECENT_MATCH_WINDOW) {
            list.remove(0);
        }
    }

    public RecentMatchStats getRecentStats(String timelineDate, String opponentId) {
        if (recentMatchHistory == null || opponentId == null) {
            return new RecentMatchStats(0, 0, 0, 0);
        }
        List<String> list = recentMatchHistory.get(recentHistoryKey(timelineDate, opponentId));
        if (list == null || list.isEmpty()) {
            return new RecentMatchStats(0, 0, 0, 0);
        }
        int start = Math.max(0, list.size() - RECENT_MATCH_WINDOW);
        int w = 0, l = 0, d = 0;
        for (int i = start; i < list.size(); i++) {
            String r = list.get(i);
            if ("W".equals(r)) w++;
            else if ("L".equals(r)) l++;
            else if ("D".equals(r)) d++;
        }
        return new RecentMatchStats(w + l + d, w, l, d);
    }

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
