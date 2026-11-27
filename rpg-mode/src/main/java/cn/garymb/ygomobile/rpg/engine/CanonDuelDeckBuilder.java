package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.rpg.ai.RpgAiCardKnowledge;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgCanonDuel;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Reconstructs playable 40-card canon decks for both sides of an {@link OcgCanonDuel}:
 * - Preserves 100% of every canon card used by that character in the episode/chapters.
 * - Pads remaining slots up to 40 using thematically compatible cards from the OCG card pool
 *   available at the duel's timeline date (matching race, attribute, fillStyle, and era rules).
 */
public final class CanonDuelDeckBuilder {

    public static final int TARGET_MAIN_SIZE = 40;

    private CanonDuelDeckBuilder() {}

    public static class BuiltCanonDeck {
        public final String duelId;
        public final String characterId;
        public final String characterName;
        public final String avatarId;
        public final String deckName;
        public final String fillStyle;
        public final List<Integer> main;
        public final List<Integer> extra;
        public final Set<Integer> canonCardIds;
        public final int canonMainCount;
        public final int paddedMainCount;
        public final String poolDate;

        public BuiltCanonDeck(String duelId,
                              String characterId,
                              String characterName,
                              String avatarId,
                              String deckName,
                              String fillStyle,
                              List<Integer> main,
                              List<Integer> extra,
                              Set<Integer> canonCardIds,
                              int canonMainCount,
                              int paddedMainCount,
                              String poolDate) {
            this.duelId = duelId;
            this.characterId = characterId;
            this.characterName = characterName;
            this.avatarId = avatarId;
            this.deckName = deckName;
            this.fillStyle = fillStyle;
            this.main = Collections.unmodifiableList(main);
            this.extra = Collections.unmodifiableList(extra);
            this.canonCardIds = canonCardIds != null
                    ? Collections.unmodifiableSet(new HashSet<>(canonCardIds))
                    : Collections.emptySet();
            this.canonMainCount = canonMainCount;
            this.paddedMainCount = paddedMainCount;
            this.poolDate = poolDate;
        }
    }

    /**
     * Reconstructs a deterministic 40-card deck for {@code side} using the OCG card pool at {@code poolDate}
     * (defaults to {@code duel.timelineDate} if null/empty).
     */
    public static BuiltCanonDeck buildDeck(RpgCatalog catalog,
                                           OcgCanonDuel duel,
                                           OcgCanonDuel.DuelSide side,
                                           String explicitPoolDate) {
        String poolDate = (explicitPoolDate != null && !explicitPoolDate.isEmpty())
                ? TimelineEngine.clampDate(catalog, explicitPoolDate)
                : TimelineEngine.clampDate(catalog, duel != null ? duel.timelineDate : "1999-02-04");

        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        Set<Integer> canonCardIds = new HashSet<>();
        Map<Integer, Integer> copies = new HashMap<>();

        // 1. Preserve 100% of canon cards used in the original work
        if (side != null && side.canonCardIds != null) {
            for (Integer cid : side.canonCardIds) {
                if (cid == null || cid <= 0) continue;
                canonCardIds.add(cid);
                RpgCard meta = catalog != null ? catalog.findCardOrCanon(cid) : null;
                if (meta != null && meta.isExtraDeckCard()) {
                    if (extra.size() < 15) {
                        extra.add(cid);
                    }
                    continue;
                }
                main.add(cid);
                copies.put(cid, copies.getOrDefault(cid, 0) + 1);
            }
        }
        if (side != null && side.canonExtraIds != null) {
            for (Integer eid : side.canonExtraIds) {
                if (eid == null || eid <= 0) continue;
                canonCardIds.add(eid);
                if (extra.size() < 15) {
                    extra.add(eid);
                }
            }
        }

        int canonMainCount = main.size();
        if (catalog == null || side == null || main.size() >= TARGET_MAIN_SIZE) {
            return new BuiltCanonDeck(
                    duel != null ? duel.id : "",
                    side != null ? side.characterId : "",
                    side != null ? side.characterName : "",
                    side != null ? side.avatarId : "",
                    side != null ? side.deckName : "",
                    side != null && side.fillStyle != null ? side.fillStyle : "GOOD_STUFF",
                    main, extra, canonCardIds, canonMainCount, Math.max(0, main.size() - canonMainCount), poolDate);
        }

        // 2. Pad remaining slots up to 40 from OCG cards released on or before poolDate
        List<RpgCard> pool = TimelineEngine.getReleasedCards(catalog, poolDate);
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, poolDate);
        OcgRuleSet ruleSet = TimelineEngine.getActiveRuleSet(catalog, poolDate);
        boolean tributeEnabled = ruleSet != null && ruleSet.tributeSummonEnabled;

        Set<String> prefRaces = new HashSet<>();
        if (side.preferredRaces != null) {
            for (String r : side.preferredRaces) {
                if (r != null) prefRaces.add(r.toLowerCase());
            }
        }
        Set<String> prefAttrs = new HashSet<>();
        if (side.preferredAttributes != null) {
            for (String a : side.preferredAttributes) {
                if (a != null) prefAttrs.add(a.toLowerCase());
            }
        }

        // Separate available Main Deck candidates into spells/traps vs monsters
        final String style = side.fillStyle != null ? side.fillStyle : "GOOD_STUFF";
        List<RpgCard> spellTraps = new ArrayList<>();
        List<RpgCard> monsters = new ArrayList<>();
        for (RpgCard c : pool) {
            if (c == null || c.isExtraDeckCard()) continue;
            if (RpgAiCardKnowledge.EXODIA_PIECES.contains(c.id)) continue; // never pad random Exodia pieces
            if (AiDeckGenerator.isUnsafeFillerCard(c, main, extra, catalog, style)) continue;
            int maxLegal = banlist.getMaxCopies(c.id);
            if (maxLegal <= 0) continue;
            if (c.isMonster()) {
                monsters.add(c);
            } else {
                spellTraps.add(c);
            }
        }

        // Sort candidates deterministically by theme affinity + era strength
        final boolean hasExtraFusions = !extra.isEmpty();
        Collections.sort(spellTraps, (a, b) -> {
            int sa = scoreSpellTrap(a, style, prefRaces, prefAttrs, hasExtraFusions);
            int sb = scoreSpellTrap(b, style, prefRaces, prefAttrs, hasExtraFusions);
            if (sb != sa) return Integer.compare(sb, sa);
            return Integer.compare(a.id, b.id);
        });
        Collections.sort(monsters, (a, b) -> {
            int sa = scoreMonster(a, style, prefRaces, prefAttrs, tributeEnabled);
            int sb = scoreMonster(b, style, prefRaces, prefAttrs, tributeEnabled);
            if (sb != sa) return Integer.compare(sb, sa);
            return Integer.compare(a.id, b.id);
        });

        // Compute current monster vs spell/trap counts in canon core
        int curMonsters = 0;
        int curSpellsTraps = 0;
        for (int cid : main) {
            RpgCard c = catalog.findCardOrCanon(cid);
            if (c != null && c.isMonster()) curMonsters++;
            else curSpellsTraps++;
        }

        int targetSpellsTraps = "EXODIA_DRAW".equals(style) ? 18
                : ("BURN_WALL".equals(style) ? 16 : 14);
        int targetMonsters = TARGET_MAIN_SIZE - targetSpellsTraps;

        // First guarantee Polymerization support if Extra Deck has fusions and deck lacks it
        if (hasExtraFusions && copies.getOrDefault(RpgAiCardKnowledge.POLYMERIZATION, 0) < 2) {
            tryAddFromPool(RpgAiCardKnowledge.POLYMERIZATION, 2, poolDate, catalog, banlist, main, copies);
        }
        // For Exodia decks, prioritize draw/search staples from the current pool
        if ("EXODIA_DRAW".equals(style)) {
            int[] exodiaHelpers = {
                    RpgAiCardKnowledge.POT_OF_GREED, RpgAiCardKnowledge.GRACEFUL_CHARITY,
                    RpgAiCardKnowledge.SANGAN, RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST,
                    RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT, RpgAiCardKnowledge.DARK_HOLE,
                    RpgAiCardKnowledge.SKELENGEL, RpgAiCardKnowledge.WALL_OF_ILLUSION
            };
            for (int hid : exodiaHelpers) {
                if (main.size() >= TARGET_MAIN_SIZE) break;
                tryAddFromPool(hid, 2, poolDate, catalog, banlist, main, copies);
            }
        }

        // Fill spells/traps up to targetSpellsTraps (max 2 copies of filler spells/traps for variety)
        for (RpgCard st : spellTraps) {
            if (main.size() >= TARGET_MAIN_SIZE) break;
            if (curSpellsTraps >= targetSpellsTraps) break;
            int legalCap = Math.min(banlist.getMaxCopies(st.id), 2);
            int cur = copies.getOrDefault(st.id, 0);
            int add = Math.min(legalCap - cur, Math.min(targetSpellsTraps - curSpellsTraps, TARGET_MAIN_SIZE - main.size()));
            for (int i = 0; i < add; i++) {
                main.add(st.id);
                copies.put(st.id, copies.getOrDefault(st.id, 0) + 1);
                curSpellsTraps++;
            }
        }

        // Fill monsters up to TARGET_MAIN_SIZE (max 2 copies of filler monsters for anime character flavor)
        for (RpgCard m : monsters) {
            if (main.size() >= TARGET_MAIN_SIZE) break;
            int legalCap = Math.min(banlist.getMaxCopies(m.id), 2);
            int cur = copies.getOrDefault(m.id, 0);
            int add = Math.min(legalCap - cur, TARGET_MAIN_SIZE - main.size());
            for (int i = 0; i < add; i++) {
                main.add(m.id);
                copies.put(m.id, copies.getOrDefault(m.id, 0) + 1);
                curMonsters++;
            }
        }

        // Fallback pass: allow up to legal max (3 copies) if early card pool (e.g. Vol.1) still needs slots
        if (main.size() < TARGET_MAIN_SIZE) {
            for (RpgCard m : monsters) {
                if (main.size() >= TARGET_MAIN_SIZE) break;
                int legalCap = banlist.getMaxCopies(m.id);
                int cur = copies.getOrDefault(m.id, 0);
                while (cur < legalCap && main.size() < TARGET_MAIN_SIZE) {
                    main.add(m.id);
                    cur++;
                    copies.put(m.id, cur);
                }
            }
            for (RpgCard st : spellTraps) {
                if (main.size() >= TARGET_MAIN_SIZE) break;
                int legalCap = banlist.getMaxCopies(st.id);
                int cur = copies.getOrDefault(st.id, 0);
                while (cur < legalCap && main.size() < TARGET_MAIN_SIZE) {
                    main.add(st.id);
                    cur++;
                    copies.put(st.id, cur);
                }
            }
        }

        return new BuiltCanonDeck(
                duel.id,
                side.characterId,
                side.characterName,
                side.avatarId,
                side.deckName,
                style,
                main,
                extra,
                canonCardIds,
                canonMainCount,
                Math.max(0, main.size() - canonMainCount),
                poolDate);
    }

    private static void tryAddFromPool(int cardId, int desiredTotal, String poolDate,
                                       RpgCatalog catalog, OcgBanlist banlist,
                                       List<Integer> main, Map<Integer, Integer> copies) {
        if (!TimelineEngine.isCardReleased(catalog, cardId, poolDate)) return;
        int cap = Math.min(desiredTotal, banlist.getMaxCopies(cardId));
        int cur = copies.getOrDefault(cardId, 0);
        while (cur < cap && main.size() < TARGET_MAIN_SIZE) {
            main.add(cardId);
            cur++;
            copies.put(cardId, cur);
        }
    }

    private static int scoreMonster(RpgCard c, String style,
                                    Set<String> prefRaces, Set<String> prefAttrs,
                                    boolean tributeEnabled) {
        int score = 0;
        String race = c.race != null ? c.race.toLowerCase() : "";
        String attr = c.attribute != null ? c.attribute.toLowerCase() : "";
        if (prefRaces.contains(race)) score += 900;
        if (prefAttrs.contains(attr)) score += 350;

        int atk = Math.max(0, c.atk);
        int def = Math.max(0, c.def);
        if ("EXODIA_DRAW".equals(style) || "FLIP_CONTROL".equals(style)) {
            if (RpgAiCardKnowledge.SEARCHER_MONSTERS.contains(c.id)) score += 1200;
            if (RpgAiCardKnowledge.FLIP_MONSTERS.contains(c.id)) score += 800;
            score += Math.max(def, atk) / 4;
        } else {
            if (tributeEnabled && c.level >= 5) {
                score -= (c.level >= 7 ? 500 : 250);
            }
            if (c.level <= 4) {
                score += atk / 2 + def / 8;
            } else {
                score += atk / 3;
            }
        }
        if ("DARK_VIRUS".equals(style) && "dark".equals(attr) && atk <= 1000 && def >= 1200) {
            score += 450;
        }
        if ("FUSION_COMBO".equals(style) && RpgAiCardKnowledge.FUSION_SUBS.contains(c.id)) {
            score += 700;
        }
        return score;
    }

    private static int scoreSpellTrap(RpgCard c, String style,
                                      Set<String> prefRaces, Set<String> prefAttrs,
                                      boolean hasExtraFusions) {
        int id = c.id;
        int score = 100;
        // Universal era staples
        if (id == RpgAiCardKnowledge.POT_OF_GREED) score += 950;
        else if (id == RpgAiCardKnowledge.GRACEFUL_CHARITY) score += 900;
        else if (id == RpgAiCardKnowledge.MONSTER_REBORN) score += 880;
        else if (id == RpgAiCardKnowledge.RAIGEKI) score += 870;
        else if (id == RpgAiCardKnowledge.DARK_HOLE) score += 850;
        else if (id == RpgAiCardKnowledge.CHANGE_OF_HEART) score += 840;
        else if (id == RpgAiCardKnowledge.HARPIES_FEATHER_DUSTER) score += 820;
        else if (id == RpgAiCardKnowledge.MIRROR_FORCE) score += 820;
        else if (id == RpgAiCardKnowledge.TRAP_HOLE) score += 800;
        else if (id == RpgAiCardKnowledge.FISSURE) score += 790;
        else if (id == RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT) score += 760;
        else if (id == RpgAiCardKnowledge.KUNAI_WITH_CHAIN) score += 730;
        else if (id == RpgAiCardKnowledge.WABOKU) score += 700;
        else if (id == RpgAiCardKnowledge.HEAVY_STORM) score += 690;
        else if (id == RpgAiCardKnowledge.SOLEMN_JUDGMENT) score += 660;
        else if (id == RpgAiCardKnowledge.TRIBUTE_TO_THE_DOOMED) score += 650;
        else if (id == RpgAiCardKnowledge.REINFORCEMENTS) score += 620;
        else if (id == RpgAiCardKnowledge.DE_SPELL || id == RpgAiCardKnowledge.REMOVE_TRAP) score += 450;

        if (hasExtraFusions && (id == RpgAiCardKnowledge.POLYMERIZATION || id == RpgAiCardKnowledge.FUSION_SAGE)) {
            score += 850;
        }
        // Theme-matched equips & field spells
        if (id == RpgAiCardKnowledge.DARK_ENERGY && prefRaces.contains("fiend")) score += 600;
        if (id == RpgAiCardKnowledge.BOOK_OF_SECRET_ARTS && prefRaces.contains("spellcaster")) score += 600;
        if (id == RpgAiCardKnowledge.LEGENDARY_SWORD && prefRaces.contains("warrior")) score += 600;
        if (id == RpgAiCardKnowledge.DRAGON_TREASURE && prefRaces.contains("dragon")) score += 600;
        if (id == RpgAiCardKnowledge.FOLLOW_WIND && prefRaces.contains("winged beast")) score += 600;
        if (id == RpgAiCardKnowledge.LASER_CANNON_ARMOR && prefRaces.contains("insect")) score += 600;
        if (id == RpgAiCardKnowledge.INSECT_ARMOR_WITH_LASER_CANNON && prefRaces.contains("insect")) score += 650;
        if (id == RpgAiCardKnowledge.RAISE_BODY_HEAT && prefRaces.contains("dinosaur")) score += 600;
        if (id == RpgAiCardKnowledge.VIOLET_CRYSTAL && prefRaces.contains("zombie")) score += 600;
        if (id == RpgAiCardKnowledge.MACHINE_CONVERSION_FACTORY && prefRaces.contains("machine")) score += 600;
        if (id == RpgAiCardKnowledge.POWER_OF_KAISHIN && prefRaces.contains("aqua")) score += 600;
        if (id == RpgAiCardKnowledge.SWORD_OF_DARK_DESTRUCTION && prefAttrs.contains("dark")) score += 520;
        if (id == RpgAiCardKnowledge.ELFS_LIGHT && prefAttrs.contains("light")) score += 520;
        if (id == RpgAiCardKnowledge.INVIGORATION && prefAttrs.contains("earth")) score += 520;
        if (id == RpgAiCardKnowledge.GUST_FAN && prefAttrs.contains("wind")) score += 520;
        if (id == RpgAiCardKnowledge.STEEL_SHELL && prefAttrs.contains("water")) score += 520;
        if (id == RpgAiCardKnowledge.BLACK_PENDANT) score += 560;

        // Avoid weak LP-only cards unless early Vol.1 pool has nothing else
        if (id == RpgAiCardKnowledge.SPARKS || id == RpgAiCardKnowledge.RAIMEI
                || id == RpgAiCardKnowledge.BLUE_MEDICINE || id == RpgAiCardKnowledge.RED_MEDICINE) {
            score -= 40;
        }
        if ("EQUIP_LIGHT_EARTH".equals(style) && RpgAiCardKnowledge.EQUIP_SPELLS.contains(id)) {
            score += 220;
        }
        return score;
    }
}
