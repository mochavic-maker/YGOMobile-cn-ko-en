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
        //    Priority order: (a) character's own canon cards in this era, (b) cards strongly
        //    synergistic with the duel's core cards, (c) race/attribute theme matches, (d) era staples.
        //    Rival characters' exclusive signature aces are strictly excluded from padding.
        final List<Integer> coreMain = new ArrayList<>(main);
        final String charId = side.characterId;
        final Set<Integer> charCanonPool = CanonCharacterCardRegistry.getCharacterCanonPool(charId, catalog);
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

        final String style = side.fillStyle != null ? side.fillStyle : "GOOD_STUFF";
        final boolean hasExtraFusions = !extra.isEmpty();

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

        // Guarantee Polymerization support if Extra Deck has fusions and deck lacks it
        if (hasExtraFusions && copies.getOrDefault(RpgAiCardKnowledge.POLYMERIZATION, 0) < 2) {
            int added = tryAddFromPool(RpgAiCardKnowledge.POLYMERIZATION, 2, poolDate, catalog, banlist, main, copies);
            curSpellsTraps += added;
        }
        // For Exodia decks, prioritize draw/search helpers from the current pool (respecting rival exclusion)
        if ("EXODIA_DRAW".equals(style)) {
            int[] exodiaHelpers = {
                    RpgAiCardKnowledge.POT_OF_GREED, RpgAiCardKnowledge.GRACEFUL_CHARITY,
                    RpgAiCardKnowledge.SANGAN, RpgAiCardKnowledge.WITCH_OF_BLACK_FOREST,
                    RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT, RpgAiCardKnowledge.DARK_HOLE,
                    RpgAiCardKnowledge.SKELENGEL, RpgAiCardKnowledge.WALL_OF_ILLUSION
            };
            for (int hid : exodiaHelpers) {
                if (main.size() >= TARGET_MAIN_SIZE) break;
                if (CanonCharacterCardRegistry.isForbiddenRivalAce(charId, hid)) continue;
                int added = tryAddFromPool(hid, 2, poolDate, catalog, banlist, main, copies);
                if (added > 0) {
                    RpgCard hc = catalog.findCardOrCanon(hid);
                    if (hc != null && hc.isMonster()) curMonsters += added;
                    else curSpellsTraps += added;
                }
            }
        }

        // Step A: Calibrate event-date era power benchmarks directly from the pool released <= poolDate
        // so non-canon/non-synergy filler cards match the event date's actual metagame strength
        // (e.g. 1800-1900 ATK beaters / 2000 DEF walls in 1999-08-26, never obsolete sub-1000 vanillas!).
        int eraTopLowerAtk = 1200;
        int eraTopLowerDef = 1300;
        for (RpgCard c : pool) {
            if (c == null || !c.isMonster() || c.isExtraDeckCard()) continue;
            if (banlist.getMaxCopies(c.id) <= 0) continue;
            if (c.level <= 4) {
                if (c.atk > eraTopLowerAtk) eraTopLowerAtk = c.atk;
                if (c.def > eraTopLowerDef) eraTopLowerDef = c.def;
            }
        }
        final int finalEraTopLowerAtk = eraTopLowerAtk;
        final int finalEraTopLowerDef = eraTopLowerDef;

        // Collect & score monster candidates first (excluding rival aces & unsafe Nomi/Ritual bricks)
        List<RpgCard> monsters = new ArrayList<>();
        for (RpgCard c : pool) {
            if (c == null || !c.isMonster() || c.isExtraDeckCard()) continue;
            if (RpgAiCardKnowledge.EXODIA_PIECES.contains(c.id)) continue;
            if (CanonCharacterCardRegistry.isForbiddenRivalAce(charId, c.id)) continue;
            if (AiDeckGenerator.isUnsafeFillerCard(c, main, extra, catalog, style)) continue;
            if (banlist.getMaxCopies(c.id) <= 0) continue;
            monsters.add(c);
        }
        Collections.sort(monsters, (a, b) -> {
            int sa = scoreMonster(a, style, prefRaces, prefAttrs, tributeEnabled,
                    finalEraTopLowerAtk, finalEraTopLowerDef, charCanonPool, coreMain, extra, catalog);
            int sb = scoreMonster(b, style, prefRaces, prefAttrs, tributeEnabled,
                    finalEraTopLowerAtk, finalEraTopLowerDef, charCanonPool, coreMain, extra, catalog);
            if (sb != sa) return Integer.compare(sb, sa);
            return Integer.compare(a.id, b.id);
        });

        // Fill monsters up to targetMonsters (max 2 copies of filler monsters for anime character variety)
        for (RpgCard m : monsters) {
            if (main.size() >= TARGET_MAIN_SIZE || curMonsters >= targetMonsters) break;
            if (AiDeckGenerator.isUnsafeFillerCard(m, main, extra, catalog, style)) continue;
            int legalCap = Math.min(banlist.getMaxCopies(m.id), 2);
            int cur = copies.getOrDefault(m.id, 0);
            int add = Math.min(legalCap - cur, Math.min(targetMonsters - curMonsters, TARGET_MAIN_SIZE - main.size()));
            for (int i = 0; i < add; i++) {
                main.add(m.id);
                copies.put(m.id, copies.getOrDefault(m.id, 0) + 1);
                curMonsters++;
            }
        }

        // Step B: Collect & score spell/trap candidates AFTER monsters are populated so equip-target
        //         rationality checks accurately see the character's full monster lineup.
        List<RpgCard> spellTraps = new ArrayList<>();
        for (RpgCard c : pool) {
            if (c == null || c.isMonster() || c.isExtraDeckCard()) continue;
            if (CanonCharacterCardRegistry.isForbiddenRivalAce(charId, c.id)) continue;
            if (AiDeckGenerator.isUnsafeFillerCard(c, main, extra, catalog, style)) continue;
            if (banlist.getMaxCopies(c.id) <= 0) continue;
            spellTraps.add(c);
        }
        Collections.sort(spellTraps, (a, b) -> {
            int sa = scoreSpellTrap(a, style, prefRaces, prefAttrs, hasExtraFusions, charCanonPool, main, extra, catalog);
            int sb = scoreSpellTrap(b, style, prefRaces, prefAttrs, hasExtraFusions, charCanonPool, main, extra, catalog);
            if (sb != sa) return Integer.compare(sb, sa);
            return Integer.compare(a.id, b.id);
        });

        // Fill spells/traps up to targetSpellsTraps (max 2 copies of filler spells/traps for variety)
        for (RpgCard st : spellTraps) {
            if (main.size() >= TARGET_MAIN_SIZE || curSpellsTraps >= targetSpellsTraps) break;
            if (AiDeckGenerator.isUnsafeFillerCard(st, main, extra, catalog, style)) continue;
            int legalCap = Math.min(banlist.getMaxCopies(st.id), 2);
            int cur = copies.getOrDefault(st.id, 0);
            int add = Math.min(legalCap - cur, Math.min(targetSpellsTraps - curSpellsTraps, TARGET_MAIN_SIZE - main.size()));
            for (int i = 0; i < add; i++) {
                main.add(st.id);
                copies.put(st.id, copies.getOrDefault(st.id, 0) + 1);
                curSpellsTraps++;
            }
        }

        // Top up any remaining slots (if either bucket fell short) with monsters then spells/traps (cap 2)
        if (main.size() < TARGET_MAIN_SIZE) {
            for (RpgCard m : monsters) {
                if (main.size() >= TARGET_MAIN_SIZE) break;
                if (AiDeckGenerator.isUnsafeFillerCard(m, main, extra, catalog, style)) continue;
                int legalCap = Math.min(banlist.getMaxCopies(m.id), 2);
                int cur = copies.getOrDefault(m.id, 0);
                while (cur < legalCap && main.size() < TARGET_MAIN_SIZE) {
                    main.add(m.id);
                    cur++;
                    copies.put(m.id, cur);
                }
            }
        }
        if (main.size() < TARGET_MAIN_SIZE) {
            for (RpgCard st : spellTraps) {
                if (main.size() >= TARGET_MAIN_SIZE) break;
                if (AiDeckGenerator.isUnsafeFillerCard(st, main, extra, catalog, style)) continue;
                int legalCap = Math.min(banlist.getMaxCopies(st.id), 2);
                int cur = copies.getOrDefault(st.id, 0);
                while (cur < legalCap && main.size() < TARGET_MAIN_SIZE) {
                    main.add(st.id);
                    cur++;
                    copies.put(st.id, cur);
                }
            }
        }

        // Fallback pass: allow up to legal max (3 copies) if an early card pool still needs slots
        if (main.size() < TARGET_MAIN_SIZE) {
            for (RpgCard m : monsters) {
                if (main.size() >= TARGET_MAIN_SIZE) break;
                if (AiDeckGenerator.isUnsafeFillerCard(m, main, extra, catalog, style)) continue;
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
                if (AiDeckGenerator.isUnsafeFillerCard(st, main, extra, catalog, style)) continue;
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

    private static int tryAddFromPool(int cardId, int desiredTotal, String poolDate,
                                      RpgCatalog catalog, OcgBanlist banlist,
                                      List<Integer> main, Map<Integer, Integer> copies) {
        if (!TimelineEngine.isCardReleased(catalog, cardId, poolDate)) return 0;
        int cap = Math.min(desiredTotal, banlist.getMaxCopies(cardId));
        int cur = copies.getOrDefault(cardId, 0);
        int added = 0;
        while (cur < cap && main.size() < TARGET_MAIN_SIZE) {
            main.add(cardId);
            cur++;
            added++;
            copies.put(cardId, cur);
        }
        return added;
    }

    /**
     * Scores a candidate padding monster in strict 3-tier priority:
     * 1) Authentic character canon cards from the event date's card pool (that remain era-viable or have real effects),
     * 2) Cards directly synergistic with the duel's canon core cards (fusion materials, combo pieces, virus fodder),
     * 3) Era-competitive cards calibrated to the event date's card pool strength (avoiding obsolete sub-era vanillas).
     */
    private static int scoreMonster(RpgCard c, String style,
                                    Set<String> prefRaces, Set<String> prefAttrs,
                                    boolean tributeEnabled,
                                    int eraTopLowerAtk, int eraTopLowerDef,
                                    Set<Integer> charCanonPool,
                                    List<Integer> coreMain, List<Integer> coreExtra,
                                    RpgCatalog catalog) {
        int score = 0;
        int atk = Math.max(0, c.atk);
        int def = Math.max(0, c.def);
        boolean isPlainNormal = "normal".equalsIgnoreCase(c.frameType);
        boolean isUtilityEffect = RpgAiCardKnowledge.SEARCHER_MONSTERS.contains(c.id)
                || RpgAiCardKnowledge.FLIP_MONSTERS.contains(c.id)
                || c.id == RpgAiCardKnowledge.WALL_OF_ILLUSION
                || c.id == RpgAiCardKnowledge.CANNON_SOLDIER
                || c.id == RpgAiCardKnowledge.CYBER_STEIN
                || c.id == RpgAiCardKnowledge.LORD_OF_D
                || RpgAiCardKnowledge.FUSION_SUBS.contains(c.id);

        // Event-date era power thresholds (automatically scales from 1999-02-04 Vol.1 to 1999-08-26+ & 2000+)
        int eraAtkFloor = Math.max(800, eraTopLowerAtk - 400);
        int eraDefFloor = Math.max(1000, eraTopLowerDef - 300);
        int eraTributeAtkFloor = eraTopLowerAtk + 400;

        boolean meetsEraStats;
        if (c.level <= 4) {
            meetsEraStats = (atk >= eraAtkFloor) || (def >= eraDefFloor);
        } else if (tributeEnabled) {
            meetsEraStats = (atk >= eraTributeAtkFloor) || (def >= Math.max(2000, eraTopLowerDef));
        } else {
            meetsEraStats = (atk >= eraAtkFloor) || (def >= eraDefFloor);
        }

        boolean inCharPool = charCanonPool != null && charCanonPool.contains(c.id);
        int synergyBonus = CanonCharacterCardRegistry.getCoreSynergyBonus(c, coreMain, coreExtra, catalog);
        boolean hasDirectSynergy = CanonCharacterCardRegistry.isDirectMechanicalSynergy(c, coreMain, coreExtra);

        // 1) Authentic character canon cards: top priority when effect/synergistic or era-viable;
        //    obsolete early-manga weak vanillas get scaled down so later-era decks pick strong character cards first.
        if (inCharPool) {
            if (!isPlainNormal || meetsEraStats || hasDirectSynergy) {
                score += 1350;
            } else if (Math.max(atk, def) >= Math.max(1100, eraAtkFloor - 250)) {
                score += 250;
            } else {
                // Outdated sub-1000 character vanilla in a 1800+ era -> defer behind era-viable cards
                score -= 900;
            }
        }

        // 2) Direct mechanical / thematic synergy with this duel's canon core cards
        score += synergyBonus;

        // 3) Hard obsolescence guard: NEVER pad non-canon, non-synergy, out-of-era plain normal monsters
        //    (e.g. sub-1000..1400 vanillas in an 1800-1900 ATK environment)!
        if (!inCharPool && !hasDirectSynergy && isPlainNormal && !meetsEraStats) {
            score -= 2600;
        }

        // 4) Theme race & attribute affinity — only boosts era-viable / effect / synergistic cards
        //    so a weak matching-race vanilla can NEVER beat an era-competitive beater or wall!
        String race = c.race != null ? c.race.toLowerCase() : "";
        String attr = c.attribute != null ? c.attribute.toLowerCase() : "";
        if (meetsEraStats || !isPlainNormal || hasDirectSynergy) {
            if (prefRaces.contains(race)) score += 320;
            if (prefAttrs.contains(attr)) score += 140;
        }

        // 5) Style- & era-calibrated combat/role value
        if ("EXODIA_DRAW".equals(style) || "FLIP_CONTROL".equals(style)) {
            if (RpgAiCardKnowledge.SEARCHER_MONSTERS.contains(c.id)) {
                score += 1600;
            } else if (RpgAiCardKnowledge.FLIP_MONSTERS.contains(c.id)) {
                score += 1200;
            } else if (c.id == RpgAiCardKnowledge.WALL_OF_ILLUSION) {
                score += 1100;
            } else if (c.level <= 4 && def >= eraDefFloor) {
                score += 850 + def / 3;
            } else if (c.level <= 4 && atk >= eraAtkFloor) {
                score += 400 + atk / 3;
            } else if (isPlainNormal) {
                score -= 1400;
            } else {
                score += Math.max(def, atk) / 4;
            }
        } else {
            if (isUtilityEffect) {
                score += 650;
            }
            if (c.level <= 4) {
                score += (int) (atk * 0.85) + def / 6;
                if (atk >= eraTopLowerAtk - 100) {
                    score += 250;
                } else if (def >= eraTopLowerDef) {
                    score += 350;
                }
            } else if (tributeEnabled) {
                if (atk >= eraTributeAtkFloor) {
                    score += atk / 2 - (c.level >= 7 ? 280 : 80);
                } else if (!hasDirectSynergy) {
                    // Weak tribute brick (e.g. Lv5 1500-1700 ATK in an 1850 Lv4 environment)
                    score -= 1300;
                }
            } else {
                score += (int) (atk * 0.85) + def / 6;
            }
        }
        if ("DARK_VIRUS".equals(style) && "dark".equals(attr) && atk <= 1000
                && (!isPlainNormal || def >= 1400)) {
            score += 650;
        }
        if ("FUSION_COMBO".equals(style) && RpgAiCardKnowledge.FUSION_SUBS.contains(c.id)) {
            score += 750;
        }
        return score;
    }

    private static int scoreSpellTrap(RpgCard c, String style,
                                      Set<String> prefRaces, Set<String> prefAttrs,
                                      boolean hasExtraFusions,
                                      Set<Integer> charCanonPool,
                                      List<Integer> curMain, List<Integer> coreExtra,
                                      RpgCatalog catalog) {
        int id = c.id;
        int score = 100;
        // 1) Highest priority: spells/traps authentically used by this character in the original work
        if (charCanonPool != null && charCanonPool.contains(id)) {
            score += 1500;
        }
        // 2) Direct mechanical / thematic synergy with this duel's canon core & monster lineup
        score += CanonCharacterCardRegistry.getCoreSynergyBonus(c, curMain, coreExtra, catalog);

        // 3) Universal era staples matching event-date power level
        if (id == RpgAiCardKnowledge.POT_OF_GREED) score += 980;
        else if (id == RpgAiCardKnowledge.GRACEFUL_CHARITY) score += 950;
        else if (id == RpgAiCardKnowledge.MONSTER_REBORN) score += 940;
        else if (id == RpgAiCardKnowledge.RAIGEKI) score += 920;
        else if (id == RpgAiCardKnowledge.DARK_HOLE) score += 900;
        else if (id == RpgAiCardKnowledge.CHANGE_OF_HEART) score += 880;
        else if (id == RpgAiCardKnowledge.HARPIES_FEATHER_DUSTER) score += 860;
        else if (id == RpgAiCardKnowledge.MIRROR_FORCE) score += 880;
        else if (id == RpgAiCardKnowledge.TRAP_HOLE) score += 840;
        else if (id == RpgAiCardKnowledge.FISSURE) score += 820;
        else if (id == RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT) score += 820;
        else if (id == RpgAiCardKnowledge.KUNAI_WITH_CHAIN) score += 760;
        else if (id == RpgAiCardKnowledge.WABOKU) score += 760;
        else if (id == RpgAiCardKnowledge.HEAVY_STORM) score += 700;
        else if (id == RpgAiCardKnowledge.SOLEMN_JUDGMENT) score += 720;
        else if (id == RpgAiCardKnowledge.TRIBUTE_TO_THE_DOOMED) score += 700;
        else if (id == RpgAiCardKnowledge.REINFORCEMENTS) score += 620;
        else if (id == RpgAiCardKnowledge.DE_SPELL || id == RpgAiCardKnowledge.REMOVE_TRAP) score += 480;

        if ("EXODIA_DRAW".equals(style)) {
            if (id == RpgAiCardKnowledge.POT_OF_GREED || id == RpgAiCardKnowledge.GRACEFUL_CHARITY
                    || id == RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT
                    || id == RpgAiCardKnowledge.WABOKU || id == RpgAiCardKnowledge.MIRROR_FORCE
                    || id == RpgAiCardKnowledge.DARK_HOLE || id == RpgAiCardKnowledge.RAIGEKI
                    || id == RpgAiCardKnowledge.SOLEMN_JUDGMENT) {
                score += 350;
            }
        }

        if (hasExtraFusions && (id == RpgAiCardKnowledge.POLYMERIZATION || id == RpgAiCardKnowledge.FUSION_SAGE)) {
            score += 950;
        }
        // Theme-matched equips & field spells
        if (id == RpgAiCardKnowledge.DARK_ENERGY && prefRaces.contains("fiend")) score += 520;
        if (id == RpgAiCardKnowledge.BOOK_OF_SECRET_ARTS && prefRaces.contains("spellcaster")) score += 520;
        if (id == RpgAiCardKnowledge.LEGENDARY_SWORD && prefRaces.contains("warrior")) score += 520;
        if (id == RpgAiCardKnowledge.DRAGON_TREASURE && prefRaces.contains("dragon")) score += 520;
        if (id == RpgAiCardKnowledge.FOLLOW_WIND && prefRaces.contains("winged beast")) score += 520;
        if (id == RpgAiCardKnowledge.LASER_CANNON_ARMOR && prefRaces.contains("insect")) score += 520;
        if (id == RpgAiCardKnowledge.INSECT_ARMOR_WITH_LASER_CANNON && prefRaces.contains("insect")) score += 580;
        if (id == RpgAiCardKnowledge.RAISE_BODY_HEAT && prefRaces.contains("dinosaur")) score += 520;
        if (id == RpgAiCardKnowledge.VIOLET_CRYSTAL && prefRaces.contains("zombie")) score += 520;
        if (id == RpgAiCardKnowledge.MACHINE_CONVERSION_FACTORY && prefRaces.contains("machine")) score += 520;
        if (id == RpgAiCardKnowledge.POWER_OF_KAISHIN && prefRaces.contains("aqua")) score += 520;
        if (id == RpgAiCardKnowledge.SWORD_OF_DARK_DESTRUCTION && prefAttrs.contains("dark")) score += 420;
        if (id == RpgAiCardKnowledge.ELFS_LIGHT && prefAttrs.contains("light")) score += 420;
        if (id == RpgAiCardKnowledge.INVIGORATION && prefAttrs.contains("earth")) score += 420;
        if (id == RpgAiCardKnowledge.GUST_FAN && prefAttrs.contains("wind")) score += 420;
        if (id == RpgAiCardKnowledge.STEEL_SHELL && prefAttrs.contains("water")) score += 420;
        if (id == RpgAiCardKnowledge.BLACK_PENDANT) score += 560;

        // Penalize obsolete low-impact burn/heal fillers in non-Burn/non-Mill decks
        if (!"BURN_WALL".equals(style) && !"MILL_MORPHING".equals(style)
                && (RpgAiCardKnowledge.getBurnDamage(id) > 0 || RpgAiCardKnowledge.isHealSpell(id))) {
            score -= 320;
        }
        if ("EQUIP_LIGHT_EARTH".equals(style) && RpgAiCardKnowledge.EQUIP_SPELLS.contains(id)) {
            score += 260;
        }
        return score;
    }
}
