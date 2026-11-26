package cn.garymb.ygomobile.rpg.ai;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Curated 1999 OCG card role & tactical value tables used by the pure-Java RPG AI.
 * Keeps card-specific knowledge declarative so future 2000+ era expansions can extend it cleanly.
 */
public final class RpgAiCardKnowledge {
    private RpgAiCardKnowledge() {}

    // ===== Draw / Search / Combo Enablers =====
    public static final int POT_OF_GREED = 55144522;           // 强欲之壶
    public static final int GRACEFUL_CHARITY = 79571449;       // 天使的施舍
    public static final int FUSION_SAGE = 26902560;            // 融合贤者
    public static final int LAST_WILL = 85602018;              // 遗言状
    public static final int POLYMERIZATION = 24094653;         // 融合
    public static final int MONSTER_REBORN = 83764718;         // 死者苏生
    public static final int SWORDS_OF_REVEALING_LIGHT = 72302403; // 光之护封剑

    // ===== Removal & Board Control Spells =====
    public static final int HARPIES_FEATHER_DUSTER = 18144506; // 鹰身女妖的羽毛扫
    public static final int HEAVY_STORM = 19613556;            // 大岚
    public static final int RAIGEKI = 12580477;                // 雷击
    public static final int DARK_HOLE = 53129443;              // 黑洞
    public static final int CHANGE_OF_HEART = 4031928;         // 心变
    public static final int FISSURE = 66788016;                // 地割
    public static final int TRIBUTE_TO_THE_DOOMED = 79759861;  // 对死者的供奉
    public static final int STOP_DEFENSE = 63102017;           // 『守备』封禁

    // ===== Burn & LP Spells =====
    public static final int OOKAZI = 19523799;                 // 昼夜的大火灾 (800)
    public static final int FINAL_FLAME = 73134081;            // 火刑 (600)
    public static final int HINOTAMA = 46130346;               // 火球 (500)
    public static final int RAIMEI = 56260110;                 // 雷鸣 (300)
    public static final int SPARKS = 76103675;                 // 火星 (200)
    public static final int BLUE_MEDICINE = 20871001;          // 蓝色药剂 (+400)
    public static final int RED_MEDICINE = 38199696;           // 红色药剂 (+500)
    public static final int GOBLINS_SECRET_REMEDY = 11868825;  // 哥布林的秘药 (+600)
    public static final int SOUL_OF_THE_PURE = 47852924;       // 天使的鲜血 (+800)
    public static final int DIAN_KETO = 84257639;              // 治疗之神 迪安·凯特 (+1000)

    // ===== Utility / Backrow / Combo Spells =====
    public static final int DE_SPELL = 19159413;               // 魔法除去 (Vol.2)
    public static final int REMOVE_TRAP = 51482758;            // 陷阱拆除 (Starter Box)
    public static final int FLUTE_OF_SUMMONING_DRAGON = 43973174; // 呼龙笛 (EX)

    // ===== Equip Spells =====
    public static final int SWORD_OF_DARK_DESTRUCTION = 37120512; // 暗之破神剑 (DARK +400/-200)
    public static final int ELFS_LIGHT = 39897277;             // 精灵之光 (LIGHT +400/-200)
    public static final int INVIGORATION = 98374133;           // 觉醒 (EARTH +400/-200)
    public static final int BURNING_SPEAR = 18937875;          // 灼热之枪 (FIRE +400/-200)
    public static final int STEEL_SHELL = 2370081;             // 钢甲壳 (WATER +400/-200)
    public static final int GUST_FAN = 55321970;               // 突风之扇 (WIND +400/-200)
    public static final int LEGENDARY_SWORD = 61854111;        // 传说之剑 (Warrior +300/+300)
    public static final int BEAST_FANGS = 46009906;            // 猛兽之齿 (Beast +300/+300)
    public static final int BOOK_OF_SECRET_ARTS = 91595718;    // 秘术之书 (Spellcaster +300/+300)
    public static final int DARK_ENERGY = 4614116;             // 暗能量 (Fiend +300/+300)
    public static final int VIOLET_CRYSTAL = 15052462;         // 紫水晶 (Zombie +300/+300)
    public static final int LASER_CANNON_ARMOR = 77007920;     // 激光炮机甲 (Insect +300/+300)
    public static final int VILE_GERMS = 39774685;             // 魔菌 (Plant +300/+300)
    public static final int POWER_OF_KAISHIN = 77027445;       // 波塞冬之力 (Aqua +300/+300)
    public static final int MACHINE_CONVERSION_FACTORY = 25769732; // 机械改造工厂 (Machine +300/+300)
    public static final int RAISE_BODY_HEAT = 51267887;        // 体温上升 (Dinosaur +300/+300)
    public static final int DRAGON_TREASURE = 1435851;         // 龙之秘宝 (Dragon +300/+300)
    public static final int SILVER_BOW_AND_ARROW = 1557499;    // 银之弓矢 (Fairy +300/+300)
    public static final int ELECTRO_WHIP = 37820550;           // 电击鞭 (Thunder +300/+300)
    public static final int MYSTICAL_MOON = 36607978;          // 魔性之月 (Beast-Warrior +300/+300)
    public static final int FOLLOW_WIND = 98252586;            // 随风翼 (Winged Beast +300/+300)
    public static final int SALAMANDRA = 32268901;             // 飞龙炎 (FIRE +700)
    public static final int INSECT_ARMOR_WITH_LASER_CANNON = 3492538; // 附火器的机甲铠 (Insect +700)
    public static final int BLACK_PENDANT = 65169794;          // 黑项坠 (+500)

    // ===== Traps =====
    public static final int TRAP_HOLE = 4206964;               // 落穴
    public static final int ACID_TRAP_HOLE = 41356845;         // 蓄积硫酸的落穴
    public static final int CRUSH_CARD_VIRUS = 57728570;       // 死之卡组破坏病毒
    public static final int KUNAI_WITH_CHAIN = 37390589;       // 附锁链的回力镖 (DM2 1999-07-08)
    public static final int CASTLE_WALLS = 44209392;           // 城壁 (Booster 3)
    public static final int JUST_DESSERTS = 24068492;          // 自业自得 (Booster 5)
    public static final int HORN_OF_HEAVEN = 98069388;         // 升天之角笛 (Vol.6)
    public static final int SOLEMN_JUDGMENT = 41420027;        // 神之宣告
    public static final int SEVEN_TOOLS = 3819470;             // 盗贼的七道具
    public static final int WABOKU = 12607053;                 // 和睦的使者
    public static final int METALMORPH = 68540058;             // 金属化·魔法反射装甲
    public static final int ROYAL_DECREE = 51452091;           // 王宫的通告
    public static final int TWO_PRONGED_ATTACK = 83887306;     // 夹击
    public static final int MIRROR_FORCE = 44095762;           // 神圣防护罩 -反射镜力-
    public static final int MAGIC_JAMMER = 77414722;           // 魔法干扰阵
    public static final int REINFORCEMENTS = 17814387;         // 援军

    // ===== Key Monsters =====
    public static final int EXODIA_HEAD = 33396948;            // 被封印的艾克佐迪亚
    public static final int EXODIA_RIGHT_ARM = 70903634;       // 被封印者的右腕
    public static final int EXODIA_LEFT_ARM = 7902349;         // 被封印者的左腕
    public static final int EXODIA_RIGHT_LEG = 8124921;        // 被封印者的右足
    public static final int EXODIA_LEFT_LEG = 44519536;        // 被封印者的左足

    public static final int SANGAN = 26202165;                 // 三眼怪
    public static final int WITCH_OF_BLACK_FOREST = 78010363;  // 黑森林的魔女
    public static final int MAGICIAN_OF_FAITH = 31560081;      // 圣魔术师
    public static final int MASK_OF_DARKNESS = 28933734;       // 暗之假面
    public static final int MAN_EATER_BUG = 54652250;          // 食人虫
    public static final int HANE_HANE = 7089711;               // 哈尼哈尼
    public static final int PENGUIN_SOLDIER = 93920745;        // 企鹅士兵
    public static final int SKELENGEL = 60694662;              // 骸骨天使
    public static final int ARMED_NINJA = 9076207;             // 青衣忍者
    public static final int REAPER_OF_THE_CARDS = 33066139;    // 猎卡死神 (Vol.3)
    public static final int TRAP_MASTER = 46461247;            // 陷阱大师 (Booster 5)
    public static final int MORPHING_JAR = 33508719;           // 变形壶
    public static final int NEEDLE_WORM = 81843628;            // 针虫
    public static final int WALL_OF_ILLUSION = 13945283;       // 幻影壁
    public static final int LORD_OF_D = 17985575;              // 龙之支配者 (EX)
    public static final int CANNON_SOLDIER = 11384280;         // 加农炮兵
    public static final int CYBER_STEIN = 69015963;            // 恶魔科学怪人
    public static final int TIME_WIZARD = 71625222;            // 时间魔术师
    public static final int MONSTER_EYE = 84133008;            // 眼球怪
    public static final int WICKED_WORM_BEAST = 6285791;       // 邪恶蠕虫兽

    public static final int BLUE_EYES_ULTIMATE = 23995346;     // 青眼究极龙
    public static final int BLACK_SKULL_DRAGON = 11901678;     // 暗黑魔龙
    public static final int BLUE_EYES = 89631139;              // 青眼白龙
    public static final int SUMMONED_SKULL = 70781052;         // 恶魔召唤
    public static final int DARK_MAGICIAN = 46986414;          // 黑魔术师
    public static final int GODDESS_THIRD_EYE = 53493204;      // 心眼之女神
    public static final int BEASTKING_SWAMPS = 99426834;       // 沼地的魔兽王
    public static final int VERSAGO = 50259460;                // 破坏神 瓦沙克

    public static final Set<Integer> EXODIA_PIECES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            EXODIA_HEAD, EXODIA_RIGHT_ARM, EXODIA_LEFT_ARM, EXODIA_RIGHT_LEG, EXODIA_LEFT_LEG
    )));

    public static final Set<Integer> FLIP_MONSTERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            MAGICIAN_OF_FAITH, MASK_OF_DARKNESS, MAN_EATER_BUG, HANE_HANE, PENGUIN_SOLDIER,
            SKELENGEL, ARMED_NINJA, REAPER_OF_THE_CARDS, TRAP_MASTER, MORPHING_JAR, NEEDLE_WORM
    )));

    public static final Set<Integer> SEARCHER_MONSTERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            SANGAN, WITCH_OF_BLACK_FOREST
    )));

    public static final Set<Integer> FUSION_SUBS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            GODDESS_THIRD_EYE, BEASTKING_SWAMPS, VERSAGO
    )));

    public static final Set<Integer> EQUIP_SPELLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            SWORD_OF_DARK_DESTRUCTION, ELFS_LIGHT, INVIGORATION, BURNING_SPEAR, STEEL_SHELL, GUST_FAN,
            LEGENDARY_SWORD, BEAST_FANGS, BOOK_OF_SECRET_ARTS, DARK_ENERGY, VIOLET_CRYSTAL,
            LASER_CANNON_ARMOR, VILE_GERMS, POWER_OF_KAISHIN, MACHINE_CONVERSION_FACTORY,
            RAISE_BODY_HEAT, DRAGON_TREASURE, SILVER_BOW_AND_ARROW, ELECTRO_WHIP, MYSTICAL_MOON,
            FOLLOW_WIND, SALAMANDRA, INSECT_ARMOR_WITH_LASER_CANNON, BLACK_PENDANT
    )));

    public static final Set<Integer> HIGH_THREAT_SPELLS_TO_NEGATE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            RAIGEKI, DARK_HOLE, HARPIES_FEATHER_DUSTER, HEAVY_STORM, MONSTER_REBORN,
            CHANGE_OF_HEART, POT_OF_GREED, GRACEFUL_CHARITY, LAST_WILL, POLYMERIZATION,
            TRIBUTE_TO_THE_DOOMED, SWORDS_OF_REVEALING_LIGHT, CRUSH_CARD_VIRUS, MIRROR_FORCE,
            SOLEMN_JUDGMENT, TRAP_HOLE, ROYAL_DECREE
    )));

    private static final Map<Integer, Integer> BURN_DAMAGE = new HashMap<>();
    private static final Map<Integer, Integer> SPELL_RECOVERY_PRIORITY = new HashMap<>();
    private static final Map<Integer, Integer> TRAP_RECOVERY_PRIORITY = new HashMap<>();

    static {
        BURN_DAMAGE.put(OOKAZI, 800);
        BURN_DAMAGE.put(FINAL_FLAME, 600);
        BURN_DAMAGE.put(HINOTAMA, 500);
        BURN_DAMAGE.put(RAIMEI, 300);
        BURN_DAMAGE.put(SPARKS, 200);

        // Magician of Faith priority
        SPELL_RECOVERY_PRIORITY.put(POT_OF_GREED, 100);
        SPELL_RECOVERY_PRIORITY.put(RAIGEKI, 95);
        SPELL_RECOVERY_PRIORITY.put(GRACEFUL_CHARITY, 92);
        SPELL_RECOVERY_PRIORITY.put(MONSTER_REBORN, 90);
        SPELL_RECOVERY_PRIORITY.put(CHANGE_OF_HEART, 88);
        SPELL_RECOVERY_PRIORITY.put(HARPIES_FEATHER_DUSTER, 84);
        SPELL_RECOVERY_PRIORITY.put(DARK_HOLE, 82);
        SPELL_RECOVERY_PRIORITY.put(LAST_WILL, 80);
        SPELL_RECOVERY_PRIORITY.put(SWORDS_OF_REVEALING_LIGHT, 75);
        SPELL_RECOVERY_PRIORITY.put(TRIBUTE_TO_THE_DOOMED, 70);
        SPELL_RECOVERY_PRIORITY.put(FISSURE, 68);
        SPELL_RECOVERY_PRIORITY.put(OOKAZI, 65);
        SPELL_RECOVERY_PRIORITY.put(FINAL_FLAME, 60);
        SPELL_RECOVERY_PRIORITY.put(POLYMERIZATION, 58);
        SPELL_RECOVERY_PRIORITY.put(HINOTAMA, 55);

        // Mask of Darkness priority
        TRAP_RECOVERY_PRIORITY.put(CRUSH_CARD_VIRUS, 100);
        TRAP_RECOVERY_PRIORITY.put(MIRROR_FORCE, 95);
        TRAP_RECOVERY_PRIORITY.put(SOLEMN_JUDGMENT, 90);
        TRAP_RECOVERY_PRIORITY.put(TRAP_HOLE, 85);
        TRAP_RECOVERY_PRIORITY.put(KUNAI_WITH_CHAIN, 83);
        TRAP_RECOVERY_PRIORITY.put(WABOKU, 80);
        TRAP_RECOVERY_PRIORITY.put(METALMORPH, 78);
        TRAP_RECOVERY_PRIORITY.put(HORN_OF_HEAVEN, 76);
        TRAP_RECOVERY_PRIORITY.put(ROYAL_DECREE, 72);
        TRAP_RECOVERY_PRIORITY.put(SEVEN_TOOLS, 70);
        TRAP_RECOVERY_PRIORITY.put(JUST_DESSERTS, 68);
        TRAP_RECOVERY_PRIORITY.put(ACID_TRAP_HOLE, 65);
    }

    public static boolean isExodiaPiece(int code) {
        return EXODIA_PIECES.contains(code);
    }

    public static boolean isFlipMonster(int code) {
        return FLIP_MONSTERS.contains(code);
    }

    public static boolean isSearcherMonster(int code) {
        return SEARCHER_MONSTERS.contains(code);
    }

    public static boolean isEquipSpell(int code) {
        return EQUIP_SPELLS.contains(code);
    }

    public static int getBurnDamage(int code) {
        Integer d = BURN_DAMAGE.get(code);
        return d != null ? d : 0;
    }

    public static boolean isHealSpell(int code) {
        return code == BLUE_MEDICINE || code == RED_MEDICINE
                || code == GOBLINS_SECRET_REMEDY || code == SOUL_OF_THE_PURE
                || code == DIAN_KETO;
    }

    public static int getSpellRecoveryScore(int code, String style, int oppLp) {
        int burn = getBurnDamage(code);
        if (burn > 0 && oppLp <= burn) return 200;
        if ("EXODIA_DRAW".equals(style)) {
            if (code == POT_OF_GREED) return 150;
            if (code == GRACEFUL_CHARITY) return 145;
            if (code == LAST_WILL) return 130;
            if (code == SWORDS_OF_REVEALING_LIGHT) return 120;
            if (code == MONSTER_REBORN) return 110;
        } else if ("BURN_WALL".equals(style)) {
            if (code == POT_OF_GREED) return 130;
            if (code == OOKAZI) return 120;
            if (code == SWORDS_OF_REVEALING_LIGHT) return 115;
            if (code == FINAL_FLAME) return 105;
        } else if ("FUSION_COMBO".equals(style)) {
            if (code == LAST_WILL) return 135;
            if (code == POLYMERIZATION) return 115;
        } else if ("MILL_MORPHING".equals(style)) {
            if (code == SWORDS_OF_REVEALING_LIGHT) return 145;
            if (code == DIAN_KETO || code == SOUL_OF_THE_PURE) return 130;
            if (code == POT_OF_GREED) return 120;
            if (code == GRACEFUL_CHARITY) return 115;
        }
        Integer base = SPELL_RECOVERY_PRIORITY.get(code);
        return base != null ? base : 30;
    }

    public static int getTrapRecoveryScore(int code, String style) {
        if ("DARK_VIRUS".equals(style) && code == CRUSH_CARD_VIRUS) return 150;
        if ("EXODIA_DRAW".equals(style) && code == WABOKU) return 120;
        if ("MILL_MORPHING".equals(style) && (code == WABOKU || code == SOLEMN_JUDGMENT)) return 130;
        if ("BURN_WALL".equals(style) && code == JUST_DESSERTS) return 115;
        Integer base = TRAP_RECOVERY_PRIORITY.get(code);
        return base != null ? base : 30;
    }

    /** Returns true if the given 1999 equip spell can legally equip a face-up monster with the given attribute/race. */
    public static boolean canEquipTarget(int equipCode, int attribute, int race) {
        // YGOPro attribute bitmasks: EARTH=0x1, WATER=0x2, FIRE=0x4, WIND=0x8, LIGHT=0x10, DARK=0x20
        // YGOPro race bitmasks: Warrior=0x1, Spellcaster=0x2, Fairy=0x4, Fiend=0x8, Zombie=0x10,
        // Machine=0x20, Aqua=0x40, Pyro=0x80, Rock=0x100, WingedBeast=0x200, Plant=0x400,
        // Insect=0x800, Thunder=0x1000, Dragon=0x2000, Beast=0x4000, BeastWarrior=0x8000, Dinosaur=0x10000
        switch (equipCode) {
            case SWORD_OF_DARK_DESTRUCTION:      return (attribute & 0x20) != 0;
            case ELFS_LIGHT:                     return (attribute & 0x10) != 0;
            case INVIGORATION:                   return (attribute & 0x01) != 0;
            case STEEL_SHELL:                    return (attribute & 0x02) != 0;
            case BURNING_SPEAR:
            case SALAMANDRA:                     return (attribute & 0x04) != 0;
            case GUST_FAN:                       return (attribute & 0x08) != 0;
            case LEGENDARY_SWORD:                return (race & 0x01) != 0;
            case BOOK_OF_SECRET_ARTS:            return (race & 0x02) != 0;
            case SILVER_BOW_AND_ARROW:           return (race & 0x04) != 0;
            case DARK_ENERGY:                    return (race & 0x08) != 0;
            case VIOLET_CRYSTAL:                 return (race & 0x10) != 0;
            case MACHINE_CONVERSION_FACTORY:     return (race & 0x20) != 0;
            case POWER_OF_KAISHIN:               return (race & 0x40) != 0;
            case FOLLOW_WIND:                    return (race & 0x200) != 0;
            case VILE_GERMS:                     return (race & 0x400) != 0;
            case LASER_CANNON_ARMOR:
            case INSECT_ARMOR_WITH_LASER_CANNON: return (race & 0x800) != 0;
            case ELECTRO_WHIP:                   return (race & 0x1000) != 0;
            case DRAGON_TREASURE:                return (race & 0x2000) != 0;
            case BEAST_FANGS:                    return (race & 0x4000) != 0;
            case MYSTICAL_MOON:                  return (race & 0x8000) != 0;
            case RAISE_BODY_HEAT:                return (race & 0x10000) != 0;
            default:                             return true;
        }
    }

    /** Returns ATK bonus granted by a known 1999 equip spell (used for summoning & combat planning). */
    public static int getEquipAtkBonus(int equipCode) {
        switch (equipCode) {
            case SALAMANDRA:
            case INSECT_ARMOR_WITH_LASER_CANNON: return 700;
            case BLACK_PENDANT: return 500;
            case SWORD_OF_DARK_DESTRUCTION:
            case ELFS_LIGHT:
            case INVIGORATION:
            case BURNING_SPEAR:
            case STEEL_SHELL:
            case GUST_FAN: return 400;
            case LEGENDARY_SWORD:
            case BOOK_OF_SECRET_ARTS:
            case DARK_ENERGY:
            case VIOLET_CRYSTAL:
            case VILE_GERMS:
            case LASER_CANNON_ARMOR:
            case POWER_OF_KAISHIN:
            case MACHINE_CONVERSION_FACTORY:
            case RAISE_BODY_HEAT:
            case DRAGON_TREASURE:
            case SILVER_BOW_AND_ARROW:
            case ELECTRO_WHIP:
            case MYSTICAL_MOON:
            case FOLLOW_WIND:
            case BEAST_FANGS: return 300;
            default: return 0;
        }
    }

    /**
     * Returns true if this Spell is low-opportunity-cost enough to Set face-down as a 1-card
     * Trap Hole bluff ("ブラフ伏せ") when we have 0 Traps set. High-value core Spells (Dark Hole,
     * Raigeki, Fissure, Monster Reborn, Change of Heart, Pot of Greed, etc.) are NEVER bluffed.
     */
    public static boolean isSafeBluffSpell(int code) {
        return isEquipSpell(code)
                || code == STOP_DEFENSE
                || code == DE_SPELL
                || code == REMOVE_TRAP
                || isHealSpell(code)
                || code == SPARKS;
    }
}
