package cn.garymb.ygomobile.rpg.engine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Validates RPG decks against:
 * 1) Card size rules (Main 40..60, Extra 0..15)
 * 2) Timeline release restriction (only cards released <= currentTimelineDate)
 * 3) Ownership restriction (cannot exceed player's pulled/crafted copies)
 * 4) Latest OCG banlist effective at currentTimelineDate (0/1/2/3 copies)
 */
public final class DeckValidator {
    public static final int MIN_MAIN_DECK = 40;
    public static final int MAX_MAIN_DECK = 60;
    public static final int MAX_EXTRA_DECK = 15;

    private DeckValidator() {}

    public static class ValidationResult {
        public final boolean valid;
        public final List<String> errors;
        public final OcgBanlist appliedBanlist;

        public ValidationResult(boolean valid, List<String> errors, OcgBanlist appliedBanlist) {
            this.valid = valid;
            this.errors = errors;
            this.appliedBanlist = appliedBanlist;
        }
    }

    public static ValidationResult validatePlayerDeck(RpgProfile profile, RpgCatalog catalog) {
        return validateDeck(profile.activeDeckMain, profile.activeDeckExtra,
                catalog, profile.currentTimelineDate, profile.collection);
    }

    /**
     * Validates a deck (pass ownershipMap = null for AI decks that only obey timeline + banlist).
     */
    public static ValidationResult validateDeck(List<Integer> main, List<Integer> extra,
                                                RpgCatalog catalog, String currentDate,
                                                Map<Integer, Integer> ownershipMap) {
        List<String> errors = new ArrayList<>();
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, currentDate);

        int mainSize = main != null ? main.size() : 0;
        int extraSize = extra != null ? extra.size() : 0;
        if (mainSize < MIN_MAIN_DECK || mainSize > MAX_MAIN_DECK) {
            errors.add("主卡组必须为 40~60 张（当前 " + mainSize + " 张）");
        }
        if (extraSize > MAX_EXTRA_DECK) {
            errors.add("额外卡组不能超过 15 张（当前 " + extraSize + " 张）");
        }

        Map<Integer, Integer> counts = new HashMap<>();
        if (main != null) {
            for (Integer id : main) {
                if (id == null) continue;
                counts.put(id, counts.getOrDefault(id, 0) + 1);
            }
        }
        if (extra != null) {
            for (Integer id : extra) {
                if (id == null) continue;
                counts.put(id, counts.getOrDefault(id, 0) + 1);
            }
        }

        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            int cardId = e.getKey();
            int used = e.getValue();
            RpgCard card = catalog.findCard(cardId);
            String label = card != null ? card.getDisplayName() : String.valueOf(cardId);

            if (!TimelineEngine.isCardReleased(catalog, cardId, currentDate)) {
                errors.add(label + " 尚未在 " + currentDate + " 发售");
                continue;
            }
            int banMax = banlist.getMaxCopies(cardId);
            if (used > banMax) {
                errors.add(label + " 超出禁限表上限（" + banlist.name + " 允许 " + banMax + " 张，已投入 " + used + " 张）");
            }
            if (ownershipMap != null) {
                int owned = ownershipMap.getOrDefault(cardId, 0);
                if (used > owned) {
                    errors.add(label + " 超出拥有数量（拥有 " + owned + " 张，已投入 " + used + " 张）");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, banlist);
    }

    /**
     * Auto-builds a 40-card deck from the player's legally owned & released cards,
     * prioritizing strong spells/traps and highest-ATK/DEF monsters.
     */
    public static int autoBuildLegalPlayerDeck(RpgProfile profile, RpgCatalog catalog) {
        String date = profile.currentTimelineDate;
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, date);
        List<RpgCard> releasedOwned = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : profile.collection.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            if (!TimelineEngine.isCardReleased(catalog, e.getKey(), date)) continue;
            RpgCard card = catalog.findCard(e.getKey());
            if (card != null) releasedOwned.add(card);
        }

        Collections.sort(releasedOwned, new Comparator<RpgCard>() {
            @Override
            public int compare(RpgCard a, RpgCard b) {
                return Integer.compare(scoreCard(b), scoreCard(a));
            }
        });

        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        for (RpgCard c : releasedOwned) {
            int max = Math.min(profile.getOwnedCount(c.id), banlist.getMaxCopies(c.id));
            if (max <= 0) continue;
            if (c.isExtraDeckCard()) {
                for (int i = 0; i < max && extra.size() < MAX_EXTRA_DECK; i++) {
                    extra.add(c.id);
                }
            } else {
                for (int i = 0; i < max && main.size() < MIN_MAIN_DECK; i++) {
                    main.add(c.id);
                }
            }
        }
        profile.activeDeckMain = main;
        profile.activeDeckExtra = extra;
        return main.size();
    }

    /** Exports standard YGOPro .ydk file readable by both YGOMobile and WindBot. */
    public static File exportToYdk(File targetFile, List<Integer> main, List<Integer> extra) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            w.write("#created by YGOMobile OCG RPG Mode\n");
            w.write("#main\n");
            if (main != null) {
                for (Integer id : main) {
                    w.write(String.valueOf(id));
                    w.write('\n');
                }
            }
            w.write("#extra\n");
            if (extra != null) {
                for (Integer id : extra) {
                    w.write(String.valueOf(id));
                    w.write('\n');
                }
            }
            w.write("!side\n");
        }
        return targetFile;
    }

    static int scoreCard(RpgCard c) {
        if (c == null) return 0;
        int rarityBonus = c.getRarityEnum().ordinal() * 400;
        if (!c.isMonster()) {
            // Prioritize key staples like Dark Hole, Monster Reborn, Swords, Fissure, Trap Hole
            return 1600 + rarityBonus;
        }
        int stat = Math.max(c.atk, c.def);
        int tributePenalty = (c.level >= 7) ? 300 : (c.level >= 5 ? 150 : 0);
        return stat + rarityBonus - tributePenalty;
    }
}
