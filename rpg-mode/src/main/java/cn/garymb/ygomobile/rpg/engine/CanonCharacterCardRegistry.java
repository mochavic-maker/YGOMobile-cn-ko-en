package cn.garymb.ygomobile.rpg.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.rpg.ai.RpgAiCardKnowledge;
import cn.garymb.ygomobile.rpg.model.OcgCanonDuel;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Authoritative character-card affinity, rival-ace exclusion, and core-card synergy registry
 * shared by both {@link CanonDuelDeckBuilder} and {@link AiDeckGenerator}.
 *
 * <p>Guarantees two deck-building invariants:
 * <ol>
 *   <li>For manga/anime characters (both Canon Duels and character-based AI opponents such as
 *       {@code ai_vol1_yugi}, {@code ai_starter_kaiba}, {@code ai_weekend_challenger}),
 *       filler/padding slots strongly prioritize cards authentically used by that character
 *       within the current timeline era OR cards directly synergistic with the deck's core cards.</li>
 *   <li>Rival characters' exclusive signature aces (e.g. Blue-Eyes / La Jinn / Mechanicalchaser /
 *       Crush Card Virus in Yugi; Dark Magician / Gaia / Curse of Dragon / Summoned Skull /
 *       Red-Eyes in Kaiba) are NEVER padded into another character's deck. (Explicit
 *       {@code canonCardIds} defined in {@code ocg_canon_duels.json} are still preserved 100%.)</li>
 * </ol>
 */
public final class CanonCharacterCardRegistry {

    private CanonCharacterCardRegistry() {}

    public static final String CHAR_YAMI_YUGI = "yami_yugi";
    public static final String CHAR_SETO_KAIBA = "seto_kaiba";
    public static final String CHAR_JOEY = "joey";
    public static final String CHAR_WEEVIL = "weevil";
    public static final String CHAR_MAI = "mai";
    public static final String CHAR_MAKO = "mako";
    public static final String CHAR_REX = "rex";
    public static final String CHAR_PARADOX = "paradox";
    public static final String CHAR_RARE_HUNTER = "rare_hunter";

    private static Set<Integer> setOf(Integer... ids) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(ids)));
    }

    /** Exclusive iconic aces per character — never padded into rival characters' decks. */
    public static final Set<Integer> YUGI_EXCLUSIVE_ACES = setOf(
            46986414, // 黑魔术师
            6368038,  // 暗黑骑士 盖亚
            28279543, // 诅咒之龙
            70781052, // 恶魔召唤
            91152256, // 精灵剑士
            15025844, // 圣精灵
            5405694,  // 混沌战士
            55761792, // 混沌的仪式
            66889139, // 龙骑士 盖亚
            87796900, // 守城的翼龙
            50930991, // 魔法剑士 尼奥 (EX Yugi)
            40374923  // 猛犸的墓场
    );

    public static final Set<Integer> KAIBA_EXCLUSIVE_ACES = setOf(
            89631139, // 青眼白龙
            23995346, // 青眼究极龙
            97590747, // 灯之魔精
            7359741,  // 机械猎手
            30113682, // 审判者
            5053103,  // 牛头人
            68516705, // 人马兽
            50005633, // 复仇的潜行剑士
            26378150, // 粗暴帝王
            17985575, // 龙之支配者
            43973174, // 呼龙笛
            24611934, // 强化石像怪
            57728570, // 死之卡组破坏病毒
            6285791,  // 邪恶蠕虫兽
            24311372  // 恶魔兽灵
    );

    public static final Set<Integer> JOEY_EXCLUSIVE_ACES = setOf(
            74677422, // 真红眼黑龙
            71625222, // 时间魔术师
            88819587, // 宝贝龙
            41462083, // 千年龙
            45231177, // 炎之剑士
            48305365, // 巨斧袭击者
            37390589, // 附锁链的回力镖
            44287299, // 传说的剑豪 正树
            14977074  // 加鲁撒斯
    );

    public static final Set<Integer> MAI_EXCLUSIVE_ACES = setOf(
            76812113, // 鹰身女郎
            12206212, // 鹰身女郎三姐妹
            52040216, // 鹰身女妖的宠物龙
            90219263, // 万华镜-华丽的分身-
            63224564  // 电子紧身衣
    );

    public static final Set<Integer> PARADOX_EXCLUSIVE_ACES = setOf(
            25955164, // 雷魔神-桑迦
            62340868, // 风魔神-修迦
            98434877, // 水魔神-斯迦
            25833572  // 门之守护神
    );

    public static final Set<Integer> WEEVIL_EXCLUSIVE_ACES = setOf(
            58192742, // 飞蛾宝宝
            40240595, // 进化之茧
            87756343, // 飞蛾幼虫
            14141448, // 大飞蛾
            48579379  // 究极完全态大飞蛾
    );

    public static final Set<Integer> MAKO_EXCLUSIVE_ACES = setOf(
            76634149, // 海龙神
            58831685  // 海鳞蛇
    );

    public static final Set<Integer> REX_EXCLUSIVE_ACES = setOf(
            94119974, // 双头恐龙王
            38289717, // 贪尸龙
            66516792  // 邪恶骑士龙
    );

    /** Curated 1999 OCG card pools authentically used by each manga/anime character (including EX boxes). */
    private static final Map<String, Set<Integer>> CURATED_CHARACTER_POOLS;

    static {
        Map<String, Set<Integer>> map = new HashMap<>();

        // 暗游戏 (Manga + Toei + DM + EX Yugi authentic canon cards only; excludes non-canon pack fillers)
        map.put(CHAR_YAMI_YUGI, setOf(
                // Monsters
                46986414, 6368038, 28279543, 70781052, 91152256, 15025844, 13039848,
                40374923, 90357090, 75889523, 41392891, 32452818, 87796900, 67724379,
                32274490, 87564352, 53829412, 69669405, 5405694, 50930991, 13945283,
                87557188, 86325596, 13723605, 54652250, 7089711,
                31560081, 26202165, 78010363,
                16972957, 66672569, 91939608, 31122090,
                // Spells & Traps
                72302403, 83764718, 53129443, 4206964, 66788016, 91595718, 59197169,
                36607978, 37120512, 24094653, 4031928, 55144522, 79571449, 12607053,
                85602018, 19159413, 51482758, 50045299, 77622396, 61854111
        ));

        // 海马濑人 (Manga + Toei + DM + EX Kaiba authentic canon cards only; excludes non-canon Starter/Booster fillers)
        map.put(CHAR_SETO_KAIBA, setOf(
                // Monsters
                89631139, 76184692, 15303296, 24611934, 5053103, 68516705, 50005633,
                97590747, 26378150, 30113682, 6285791, 24311372, 7359741, 17985575,
                47060154, 73481154, 21263083, 10202894,
                54098121, 46461247, 98795934, 72299832, 28933734, 72520073,
                26202165, 85639257, 80770678,
                // Spells & Traps
                4614116, 57728570, 63102017, 43973174, 12580477, 24094653, 1435851,
                50913601, 83887306, 83764718, 53129443, 66788016, 4206964, 55144522,
                79571449, 4031928, 80604091, 17814387, 44209392, 24068492, 19159413,
                51482758, 98374133
        ));

        // 城之内克也 (Manga + DM Warrior/Dragon/Gamble authentic canon cards only)
        map.put(CHAR_JOEY, setOf(
                // Monsters
                74677422, 48305365, 88819587, 71625222, 14977074, 1184620, 44287299,
                34460851, 15480588, 49791927, 48365709, 16899564,
                55291359, 64271667, 67494157,
                // Spells & Traps
                37390589, 32268901, 61854111, 86318356, 24094653, 1435851, 17814387,
                44209392, 68540058, 46009906, 66788016, 83764718, 4206964, 72302403
        ));

        // 昆虫羽蛾 (authentic canon insect cards)
        map.put(CHAR_WEEVIL, setOf(
                58192742, 40240595, 87756343, 14141448, 48579379, 89091579, 88979991,
                52584282, 15367030, 41762634, 68928540, 80141480, 53606874, 33413638,
                28470714, 54652250, 81843628,
                16229315, 51228280,
                87430998, 77007920, 3492538, 98374133
        ));

        // 孔雀舞 (authentic Harpie / Amazoness / Winged Beast era cards)
        map.put(CHAR_MAI, setOf(
                76812113, 12206212, 52040216, 41396436, 10202894, 75582395, 47319141,
                77998771,
                90219263, 63224564, 18144506, 98252586, 37820550, 55321970, 50913601
        ));

        // 梶木渔太 (authentic Ocean / Water canon cards)
        map.put(CHAR_MAKO, setOf(
                76634149, 13429800, 77456781, 58831685, 23659124, 85326399, 44073668,
                81386177, 33178416, 29402771,
                8201910, 36904469, 85639257,
                22702055, 77027445, 2370081
        ));

        // 恐龙龙崎 (authentic Dinosaur / Dragon canon cards)
        map.put(CHAR_REX, setOf(
                74677422, 66516792, 94119974, 38289717, 13069066, 1784619,
                67494157,
                23424603, 51267887, 1435851, 98374133
        ));

        // 迷宫兄弟
        map.put(CHAR_PARADOX, setOf(
                25955164, 62340868, 98434877, 25833572, 51228280, 30778711, 11384280,
                8471389, 77827521, 73051941, 73481154, 46864967, 85639257, 80770678,
                24094653, 72302403, 59197169, 23424603, 83764718
        ));

        // 古鲁斯·稀罕猎人
        map.put(CHAR_RARE_HUNTER, setOf(
                33396948, 70903634, 7902349, 8124921, 44519536,
                26202165, 78010363, 31560081, 60694662, 54652250, 7089711,
                85639257, 80770678, 13945283,
                55144522, 79571449, 72302403, 53129443, 83764718, 4031928, 79759861, 12607053
        ));

        CURATED_CHARACTER_POOLS = Collections.unmodifiableMap(map);
    }

    /**
     * Maps either a Canon Duel {@code characterId} or a character-themed {@code AiOpponent.id}
     * to its canonical character ID, or returns {@code null} for generic/metagame AI opponents.
     */
    public static String resolveCharacterId(String opponentOrCharId) {
        if (opponentOrCharId == null || opponentOrCharId.isEmpty()) return null;
        if ("ai_vol1_yugi".equals(opponentOrCharId)) return CHAR_YAMI_YUGI;
        if ("ai_starter_kaiba".equals(opponentOrCharId)) return CHAR_SETO_KAIBA;
        if ("ai_weekend_challenger".equals(opponentOrCharId)) return CHAR_JOEY;
        if (CURATED_CHARACTER_POOLS.containsKey(opponentOrCharId)) return opponentOrCharId;
        return null;
    }

    /**
     * Returns the full set of authentic canon cards for {@code charId} (combining curated 1999
     * manga/anime/EX pools + dynamically harvested {@code canonCardIds} from {@code catalog.canonDuels},
     * minus any rival-exclusive aces).
     */
    public static Set<Integer> getCharacterCanonPool(String charId, RpgCatalog catalog) {
        String resolved = resolveCharacterId(charId);
        if (resolved == null) return Collections.emptySet();
        Set<Integer> result = new HashSet<>();
        Set<Integer> curated = CURATED_CHARACTER_POOLS.get(resolved);
        if (curated != null) result.addAll(curated);
        if (catalog != null && catalog.canonDuels != null) {
            for (OcgCanonDuel duel : catalog.canonDuels) {
                harvestSide(duel.sideA, resolved, result);
                harvestSide(duel.sideB, resolved, result);
            }
        }
        return result;
    }

    private static void harvestSide(OcgCanonDuel.DuelSide side, String resolvedCharId, Set<Integer> out) {
        if (side == null || !resolvedCharId.equals(side.characterId) || side.canonCardIds == null) return;
        for (Integer cid : side.canonCardIds) {
            if (cid != null && cid > 0 && !isForbiddenRivalAce(resolvedCharId, cid)) {
                out.add(cid);
            }
        }
    }

    /**
     * Returns true if {@code cardId} is a rival character's exclusive signature ace that MUST NOT
     * be padded as filler or included in {@code charId}'s AI deck.
     */
    public static boolean isForbiddenRivalAce(String charId, int cardId) {
        String resolved = resolveCharacterId(charId);
        if (resolved == null) return false;

        if (!CHAR_YAMI_YUGI.equals(resolved) && YUGI_EXCLUSIVE_ACES.contains(cardId)) {
            // Rare Hunter is allowed Mystical Elf wall only if needed, never Yugi's boss aces
            if (CHAR_RARE_HUNTER.equals(resolved) && cardId == 15025844) {
                return false;
            }
            return true;
        }
        if (!CHAR_SETO_KAIBA.equals(resolved) && KAIBA_EXCLUSIVE_ACES.contains(cardId)) {
            return true;
        }
        if (!CHAR_JOEY.equals(resolved) && JOEY_EXCLUSIVE_ACES.contains(cardId)) {
            // Rex owns Red-Eyes before losing it to Joey (already in Rex's canonCardIds)
            if (CHAR_REX.equals(resolved) && cardId == 74677422) {
                return false;
            }
            return true;
        }
        if (!CHAR_MAI.equals(resolved) && MAI_EXCLUSIVE_ACES.contains(cardId)) {
            return true;
        }
        if (!CHAR_PARADOX.equals(resolved) && PARADOX_EXCLUSIVE_ACES.contains(cardId)) {
            return true;
        }
        if (!CHAR_WEEVIL.equals(resolved) && WEEVIL_EXCLUSIVE_ACES.contains(cardId)) {
            return true;
        }
        if (!CHAR_MAKO.equals(resolved) && MAKO_EXCLUSIVE_ACES.contains(cardId)) {
            return true;
        }
        if (!CHAR_REX.equals(resolved) && REX_EXCLUSIVE_ACES.contains(cardId)) {
            return true;
        }

        // Additional cross-character cleanliness guards:
        // - Yugi & Joey never pad generic meta beater Gemini Elf (69140098) or Kaiba's Cyclops (76184692)
        if ((CHAR_YAMI_YUGI.equals(resolved) || CHAR_JOEY.equals(resolved))
                && (cardId == 69140098 || cardId == 76184692 || cardId == 15303296)) {
            return true;
        }
        // - Kaiba never pads Yugi's Giant Soldier of Stone (13039848)
        if (CHAR_SETO_KAIBA.equals(resolved) && cardId == 13039848) {
            return true;
        }
        return false;
    }

    /**
     * Returns true ONLY when {@code candidate} has a direct mechanical combo/material synergy with
     * {@code coreMain} or {@code coreExtra} (e.g., Fusion materials for an Extra Deck boss, Petit Moth
     * for Cocoon/Great Moth, functional/high-DEF DARK <=1000 fodder for Crush Card Virus).
     * Plain race/attribute overlap does NOT qualify — preventing obsolete weak vanillas from bypassing
     * event-date era-strength guards.
     */
    public static boolean isDirectMechanicalSynergy(RpgCard candidate,
                                                    Collection<Integer> coreMain,
                                                    Collection<Integer> coreExtra) {
        if (candidate == null) return false;
        Set<Integer> mainSet = coreMain != null ? new HashSet<>(coreMain) : Collections.emptySet();
        Set<Integer> extraSet = coreExtra != null ? new HashSet<>(coreExtra) : Collections.emptySet();
        int id = candidate.id;

        if (!extraSet.isEmpty()) {
            if (id == RpgAiCardKnowledge.POLYMERIZATION || id == RpgAiCardKnowledge.FUSION_SAGE) return true;
            if (extraSet.contains(45231177) && (id == 44287299 || id == 34460851)) return true; // 炎之剑士
            if (extraSet.contains(41462083) && (id == 88819587 || id == 71625222)) return true; // 千年龙
            if (extraSet.contains(66889139) && (id == 6368038 || id == 28279543)) return true;  // 龙骑士盖亚
            if (extraSet.contains(90660762) && (id == 74677422 || id == 64271667)) return true; // 流星黑龙
            if (extraSet.contains(11901678) && (id == 74677422 || id == 70781052)) return true; // 暗黑魔龙
            if (extraSet.contains(99551425) && (id == 11384280 || id == 8471389)) return true;  // 迷宫的魔战车
            if (extraSet.contains(94905343) && (id == 5053103 || id == 68516705)) return true;  // 牛头人马兽
            if (extraSet.contains(23995346) && id == 89631139) return true;                     // 青眼究极龙
        }
        if ((mainSet.contains(40240595) || mainSet.contains(14141448) || mainSet.contains(48579379))
                && id == 58192742) {
            return true; // 飞蛾宝宝 for 进化之茧 / 大飞蛾
        }
        if (mainSet.contains(17985575) && id == 43973174) return true; // 呼龙笛
        if ((mainSet.contains(76812113) || mainSet.contains(12206212))
                && (id == 90219263 || id == 63224564 || id == 98252586)) {
            return true; // Harpie direct support
        }
        if (mainSet.contains(57728570)
                && candidate.isMonster()
                && "DARK".equalsIgnoreCase(candidate.attribute)
                && candidate.atk <= 1000
                && !RpgAiCardKnowledge.EXODIA_PIECES.contains(id)
                && (!"normal".equalsIgnoreCase(candidate.frameType) || candidate.def >= 1400)) {
            return true; // Functional/wall DARK <=1000 Crush Card Virus fodder
        }
        return false;
    }

    /**
     * Computes a thematic & mechanical synergy bonus for adding {@code candidate} into a deck
     * that already contains {@code coreMain} and {@code coreExtra}. Used by both
     * {@link CanonDuelDeckBuilder} and {@link AiDeckGenerator} so filler cards actively support
     * the deck's signature core instead of grabbing unrelated high-ATK beaters.
     */
    public static int getCoreSynergyBonus(RpgCard candidate,
                                          Collection<Integer> coreMain,
                                          Collection<Integer> coreExtra,
                                          RpgCatalog catalog) {
        if (candidate == null) return 0;
        Set<Integer> mainSet = coreMain != null ? new HashSet<>(coreMain) : Collections.emptySet();
        Set<Integer> extraSet = coreExtra != null ? new HashSet<>(coreExtra) : Collections.emptySet();
        int id = candidate.id;
        int bonus = 0;

        // 1) Fusion Extra Deck support (Polymerization, Fusion Sage, and missing named materials)
        if (!extraSet.isEmpty()) {
            if (id == RpgAiCardKnowledge.POLYMERIZATION) bonus += 950;
            else if (id == RpgAiCardKnowledge.FUSION_SAGE) bonus += 800;

            if (extraSet.contains(45231177) && (id == 44287299 || id == 34460851)) bonus += 1050; // 炎之剑士 materials
            if (extraSet.contains(41462083) && (id == 88819587 || id == 71625222)) bonus += 1050; // 千年龙 materials
            if (extraSet.contains(66889139) && (id == 6368038 || id == 28279543)) bonus += 1050;  // 龙骑士盖亚 materials
            if (extraSet.contains(90660762) && (id == 74677422 || id == 64271667)) bonus += 1050; // 流星黑龙 materials
            if (extraSet.contains(11901678) && (id == 74677422 || id == 70781052)) bonus += 1050; // 暗黑魔龙 materials
            if (extraSet.contains(99551425) && (id == 11384280 || id == 8471389)) bonus += 1150;  // 迷宫的魔战车 materials
            if (extraSet.contains(94905343) && (id == 5053103 || id == 68516705)) bonus += 1050;  // 牛头人马兽 materials
            if (extraSet.contains(23995346) && id == 89631139) bonus += 1100;                     // 青眼究极龙 material
        }

        // 2) Named combo dependencies in coreMain
        if ((mainSet.contains(40240595) || mainSet.contains(14141448) || mainSet.contains(48579379))
                && id == 58192742) {
            bonus += 1400; // 飞蛾宝宝 for 进化之茧 / 大飞蛾!
        }
        if (mainSet.contains(76812113) || mainSet.contains(12206212)) {
            if (id == 90219263 || id == 63224564 || id == 98252586 || id == 18144506 || id == 50913601) {
                bonus += 950; // Harpie support
            }
        }
        if (mainSet.contains(17985575) && id == 43973174) {
            bonus += 1050; // 呼龙笛 for 龙之支配者
        }
        if ((mainSet.contains(89631139) || mainSet.contains(74677422))
                && (id == 1435851 || id == 50913601)) {
            bonus += 600; // 龙之秘宝 / 山 for Dragon aces
        }
        if ((mainSet.contains(22702055) || mainSet.contains(18605135))
                && (id == 22702055 || id == 77027445 || id == 2370081)) {
            bonus += 900; // 海 / 波塞冬之力 / 钢甲壳 for Ocean core
        }
        if (mainSet.contains(87430998) && (id == 87430998 || id == 77007920 || id == 3492538)) {
            bonus += 850; // 森 / Insect equips
        }
        if (mainSet.contains(23424603) && (id == 23424603 || id == 51267887)) {
            bonus += 850; // 荒野 / 体温上升
        }
        if ((mainSet.contains(46986414) || mainSet.contains(70781052))
                && (id == 59197169 || id == 91595718 || id == 37120512)) {
            bonus += 600; // 暗 / 秘术之书 / 暗之破神剑
        }
        if (mainSet.contains(57728570)
                && candidate.isMonster()
                && "DARK".equalsIgnoreCase(candidate.attribute)
                && candidate.atk <= 1000
                && !RpgAiCardKnowledge.EXODIA_PIECES.contains(id)) {
            boolean strongVirusFodder = !"normal".equalsIgnoreCase(candidate.frameType)
                    || candidate.def >= 1400;
            if (strongVirusFodder) {
                bonus += 950; // Functional/wall DARK <=1000 tribute media (Sangan, Witch, Mask, Wall of Illusion, etc.)
            }
        }

        // 3) High-level boss tribute-fodder / stall support (e.g. Gate Guardian pieces, Chaos Soldier, 2-tribute dragons)
        if (catalog != null && !mainSet.isEmpty()) {
            int highLevelCoreCount = 0;
            Map<String, Integer> raceCounts = new HashMap<>();
            Map<String, Integer> attrCounts = new HashMap<>();
            for (Integer mid : coreMain) {
                if (mid == null) continue;
                RpgCard mc = catalog.findCardOrCanon(mid);
                if (mc == null || !mc.isMonster()) continue;
                if (mc.level >= 7) highLevelCoreCount++;
                if (mc.race != null && !mc.race.isEmpty()) {
                    String rk = mc.race.toLowerCase();
                    raceCounts.put(rk, raceCounts.getOrDefault(rk, 0) + 1);
                }
                if (mc.attribute != null && !mc.attribute.isEmpty()) {
                    String ak = mc.attribute.toLowerCase();
                    attrCounts.put(ak, attrCounts.getOrDefault(ak, 0) + 1);
                }
            }
            if (highLevelCoreCount >= 2) {
                if (id == RpgAiCardKnowledge.SWORDS_OF_REVEALING_LIGHT
                        || id == RpgAiCardKnowledge.MONSTER_REBORN) {
                    bonus += 550;
                } else if (candidate.isMonster() && candidate.level <= 4 && candidate.def >= 1500) {
                    bonus += 350;
                }
            }

            // 4) Predominant core race / attribute synergy (only for effect or combat-viable monsters)
            if (candidate.isMonster()) {
                boolean isWeakPlainNormal = "normal".equalsIgnoreCase(candidate.frameType)
                        && candidate.atk < 1400 && candidate.def < 1600;
                if (!isWeakPlainNormal) {
                    String cRace = candidate.race != null ? candidate.race.toLowerCase() : "";
                    String cAttr = candidate.attribute != null ? candidate.attribute.toLowerCase() : "";
                    if (raceCounts.getOrDefault(cRace, 0) >= 3) bonus += 220;
                    if (attrCounts.getOrDefault(cAttr, 0) >= 4) bonus += 100;
                }
            } else {
                // Equip spells matching the predominant core race/attribute
                String req = AiDeckGenerator.getEquipTargetRequirement(id);
                if (req != null) {
                    int targets = AiDeckGenerator.countEquipCompatibleMonsters(req,
                            coreMain instanceof java.util.List ? (java.util.List<Integer>) coreMain : new java.util.ArrayList<>(coreMain),
                            catalog);
                    if (targets >= 5) bonus += 520;
                    else if (targets >= 4) bonus += 320;
                }
            }
        }

        return bonus;
    }
}
