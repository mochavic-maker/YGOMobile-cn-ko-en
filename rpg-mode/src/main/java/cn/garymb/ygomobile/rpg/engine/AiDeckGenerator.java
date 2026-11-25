package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.Rarity;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Generates timeline-legal and OCG-banlist-compliant 40-card AI decks modeled on
 * yugioh-history.com Series 1 (1999) metagame chronicles.
 * Each AI opponent carries multiple historical deck evolutions and automatically switches
 * to the appropriate era deck for the player's selected timeline date.
 */
public final class AiDeckGenerator {
    private AiDeckGenerator() {}

    public static class GeneratedAiDeck {
        public final List<Integer> main;
        public final List<Integer> extra;
        public final OcgBanlist banlist;
        public final AiOpponent.EraDeckProfile appliedProfile;

        public GeneratedAiDeck(List<Integer> main, List<Integer> extra,
                               OcgBanlist banlist, AiOpponent.EraDeckProfile appliedProfile) {
            this.main = main;
            this.extra = extra;
            this.banlist = banlist;
            this.appliedProfile = appliedProfile;
        }
    }

    private static Map<Integer, Integer> recipe(int... pairs) {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    /**
     * Built-in 12 OCG 1999 AI opponents (1999-02-04 ~ 1999-12-31) with multi-era deck evolutions:
     * includes 3 progressive non-meta/mid-tier opponents (Novice, Shop Regular, Weekend Challenger)
     * plus 9 iconic metagame opponents grounded in yugioh-history.com's Series 1 chronicle.
     */
    public static List<AiOpponent> getDemoOpponents() {
        return Arrays.asList(
                // 1. Street Novice (1999-02-04): evolves N/R Vol.1 -> Booster 2 Lower Beat -> Booster 5 Machine King
                new AiOpponent(
                        "ai_street_novice",
                        "街边新人决斗者",
                        "Lv.1 · 平民凡骨进化线",
                        "1999-02-04",
                        1, 150, 80, 50,
                        "随时间轴从 Vol.1 凡骨进化到 Booster 2 高攻下级、Booster 5 平民机械王，不带顶级神卡，适合稳步赚代币。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【Vol.1 初学者凡骨】", "1999.02 入门",
                                        "仅用 Vol.1 低罕贵凡骨与基础装备/回复卡。",
                                        recipe(76184692, 3, 40374923, 3, 90357090, 3, 61854111, 2, 46009906, 2, 38199696, 2, 76103675, 2),
                                        null, "NOVICE_NR"),
                                new AiOpponent.EraDeckProfile("1999-05-25", "【Booster 2 下级凡骨重击】", "1999.05 专家规则入门",
                                        "适应祭品规则，换装神圣人偶(1600)、吸血跳蚤(1500)、豌豆战士(1400)等高攻下级。",
                                        recipe(91939608, 3, 41762634, 3, 1784619, 3, 84990171, 3, 91152256, 2, 66788016, 2, 39897277, 2, 98374133, 2),
                                        null, "NOVICE_NR"),
                                new AiOpponent.EraDeckProfile("1999-10-17", "【Booster 5 平民机械王】", "1999.10 机械王轴",
                                        "yugioh-history 提及的高性价比 N 卡「机械王」(实质 2300+ 打点) 搭配岩石巨兵与基础解场。",
                                        recipe(46700124, 3, 13039848, 3, 91939608, 3, 41762634, 3, 66788016, 2, 72302403, 1),
                                        null, "NOVICE_NR")
                        )
                ),

                // 2. Card Shop Regular (1999-02-04): progressive non-meta casual decks without Raigeki/Dark Hole/Pot/Trap Hole
                new AiOpponent(
                        "ai_shop_regular",
                        "卡牌屋常客·阿健",
                        "Lv.1 · 校园非顶级娱乐流",
                        "1999-02-04",
                        1, 180, 90, 60,
                        "刻意避开黑洞、雷击、强欲、落穴等顶级泛用神卡，用中游凡骨与趣味装备陪你平滑度过开荒期。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【Vol.1 校园杂牌兽战】", "1999.02 休闲开荒",
                                        "独眼巨人、银牙狼、猛犸的墓场搭配猛兽之齿/传说之剑，无黑洞无落穴，前期最稳练手对手。",
                                        recipe(76184692, 3, 90357090, 3, 40374923, 3, 9159938, 3, 15401633, 3, 90963488, 3, 46009906, 2, 61854111, 2, 38199696, 3),
                                        null, "CASUAL_MID"),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【Vol.2 奇趣下级混编】", "1999.03 休闲升级",
                                        "换装炭烧战士(1500)、荒野盗龙(1500)、混沌男巫、奇迹鸟、大炮鸟，稳健推进但绝不超模。",
                                        recipe(54541900, 3, 1784619, 3, 41544074, 3, 59036972, 3, 72842870, 3, 31339260, 3, 70681994, 3, 61854111, 2, 38199696, 2),
                                        null, "CASUAL_MID"),
                                new AiOpponent.EraDeckProfile("1999-05-27", "【Vol.3 中游凡骨休闲组】", "1999.05 休闲过渡",
                                        "天空猎手(1550)、珍鱼(1500)、死亡撒旦(1400)配『守备』封禁，适合中前期刷代币攒卡。",
                                        recipe(10202894, 3, 80516007, 3, 36304921, 3, 71407486, 3, 32452818, 3, 98818516, 3, 63102017, 2, 66788016, 2),
                                        null, "CASUAL_MID"),
                                new AiOpponent.EraDeckProfile("1999-09-23", "【Vol.5 校园中坚混打】", "1999.09 中游娱乐",
                                        "苍白兽、兵主部、响女、铠武者僵尸搭配 1 祭品粗暴帝王，体验纯正校园桌游对决。",
                                        recipe(21263083, 3, 2118022, 3, 64501875, 3, 20277860, 3, 76446915, 3, 99261403, 3, 26378150, 2, 66788016, 2),
                                        null, "CASUAL_MID")
                        )
                ),

                // 3. Weekend Shop Challenger (1999-03-01): Joey-style mid-tier warrior/dragon/gamble progression
                new AiOpponent(
                        "ai_weekend_challenger",
                        "周末店赛挑战者·热血城之内风",
                        "Lv.2 · 战士/龙/赌博中游流",
                        "1999-03-01",
                        2, 220, 100, 65,
                        "定位介于新手与环境霸主之间的中坚对手：主打战士/真红眼/时间魔术师情怀轴，不堆砌顶级禁限神卡。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-03-01", "【Booster 1 街头战士＆兽族】", "1999.03 中游起步",
                                        "独眼巨人、银牙狼、石像怪、电气小子与基础装备＋单张地割，作为挑战暗游戏前的练兵。",
                                        recipe(76184692, 3, 90357090, 3, 15303296, 3, 27324313, 3, 88643173, 3, 756652, 3, 61854111, 2, 46009906, 2, 66788016, 1, 38199696, 2),
                                        null, "MID_TIER_THEME"),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【Vol.2 精灵剑士＆火焰战士】", "1999.03 店赛中坚",
                                        "精灵剑士(1400)、炭烧战士(1500)、荒野盗龙(1500)、大炮鸟，搭配传说之剑与地割稳扎稳打。",
                                        recipe(91152256, 3, 54541900, 3, 1784619, 3, 70681994, 3, 72842870, 3, 37421579, 3, 61854111, 2, 66788016, 2, 38199696, 2),
                                        null, "MID_TIER_THEME"),
                                new AiOpponent.EraDeckProfile("1999-05-27", "【Vol.3 真红眼＆岩石巨兵热血轴】", "1999.05 情怀中游",
                                        "真红眼黑龙(2400)搭配花狼、暗杀者、岩石巨兵与天空猎手，攻势爽快但留足反制窗口。",
                                        recipe(74677422, 2, 95952802, 2, 48365709, 2, 13039848, 2, 10202894, 3, 80516007, 3, 91152256, 3, 66788016, 2, 72302403, 1),
                                        null, "MID_TIER_THEME"),
                                new AiOpponent.EraDeckProfile("1999-08-26", "【巨蛋后 巨斧＆时间魔术师赌博流】", "1999.08~12 热血赌博",
                                        "巨斧袭击者(1700)、真红眼黑龙、时间魔术师、邪恶蠕虫兽与宝贝龙(11.18起)，充满戏剧性的中坚对手！",
                                        recipe(48305365, 3, 74677422, 2, 71625222, 2, 88819587, 2, 6285791, 3, 91152256, 3, 77998771, 2, 66788016, 2, 4206964, 1, 72302403, 1),
                                        null, "MID_TIER_THEME")
                        )
                ),

                // 4. Classic Yugi (1999-02-04): No-Tribute DM -> Vol.2 Reborn -> Expert Flip -> Summoned Skull -> EX Duel King
                new AiOpponent(
                        "ai_vol1_yugi",
                        "初代暗游戏",
                        "Lv.2 · 主角卡组编年史",
                        "1999-02-04",
                        2, 240, 110, 70,
                        "完整经历初代暗游戏卡组演变：免祭品黑魔术师 → 护封剑死苏 → 专家规则反转 → 恶魔召唤 → EX决斗王。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【免祭品最上级 · 黑魔术师＆盖亚】", "1999.02 公式规则",
                                        "利用初代公式规则无祭品特性直接拍出黑魔术师(2500)与暗黑骑士盖亚(2300)，配满编黑洞/落穴/地割。",
                                        recipe(46986414, 3, 6368038, 3, 76184692, 3, 53129443, 3, 4206964, 3, 66788016, 3, 91595718, 2, 61854111, 2),
                                        null, "NO_TRIBUTE_BEAT"),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【Vol.2 护封剑·死者苏生轴】", "1999.03 Vol.2强化",
                                        "加入诅咒之龙、圣精灵(2000守)、光之护封剑与死者苏生，巩固攻防。",
                                        recipe(46986414, 3, 6368038, 3, 28279543, 3, 15025844, 3, 85639257, 3, 83764718, 3, 72302403, 3, 53129443, 3, 4206964, 3, 66788016, 3),
                                        null, "NO_TRIBUTE_BEAT"),
                                new AiOpponent.EraDeckProfile("1999-05-27", "【专家规则 · 岩石巨兵＆食人虫】", "1999.05 专家规则转型",
                                        "切换为 1 祭品+强力下级/反转构筑：岩石巨兵、食人虫、哈尼哈尼、精灵剑士、强欲之壶与诅咒之龙。",
                                        recipe(28279543, 2, 46986414, 1, 91939608, 3, 91152256, 3, 13039848, 3, 54652250, 3, 7089711, 2, 55144522, 3, 83764718, 3, 72302403, 3, 4206964, 3, 66788016, 3),
                                        null, "LOWER_BEAT"),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【Vol.4 恶魔召唤＆圣魔术师】", "1999.07 上级革命",
                                        "换装 1 祭品 2500 打点王者「恶魔召唤」与「圣魔术师」，保留经典黑魔术师王牌。",
                                        recipe(70781052, 3, 46986414, 1, 31560081, 3, 54652250, 3, 13039848, 3, 91939608, 3, 55144522, 3, 83764718, 3, 12580477, 3, 72302403, 2, 4206964, 3),
                                        null, "GOOD_STUFF"),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 决斗王 · 三眼/幻影壁/黑魔】", "1999.12 世纪末",
                                        "融合 EX 新卡幻影壁、和睦的使者与三眼怪、天使的施舍、心变，构筑 1999 年底决斗王卡组。",
                                        recipe(70781052, 3, 46986414, 1, 69140098, 3, 13945283, 3, 26202165, 3, 78010363, 2, 31560081, 3, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 12607053, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF")
                        )
                ),

                // 3. Burn & Direct Damage Mage (1999-02-04): Vol.1 Burn -> Fire Wall -> Big Burn -> Cannon Soldier Last Will
                new AiOpponent(
                        "ai_burn_mage",
                        "火焰直伤术士",
                        "Lv.2 · 魔法烧血与射出流",
                        "1999-02-04",
                        2, 260, 120, 75,
                        "专精直接伤害与高守壁垒，11~12月进化为 yugioh-history 第19回记载的「加农炮兵＋遗言状射出烧血」！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【Vol.1 初代火星直伤】", "1999.02 初期烧血",
                                        "火星直伤配合黑洞/落穴/地割清场与红色药剂续航。",
                                        recipe(76103675, 3, 38199696, 3, 53129443, 3, 4206964, 3, 66788016, 3, 46986414, 2, 6368038, 2),
                                        null, "BURN_WALL"),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【火刑＆火球 护封剑壁垒】", "1999.03 烧血成型",
                                        "火刑(600)、火球(500)、雷鸣(300) 搭配圣精灵/水魔道士 2000 守备与光之护封剑拖延斩杀。",
                                        recipe(73134081, 3, 46130346, 3, 56260110, 3, 72302403, 3, 15025844, 3, 85639257, 3, 80770678, 3, 11868825, 2, 4206964, 3, 53129443, 3),
                                        null, "BURN_WALL"),
                                new AiOpponent.EraDeckProfile("1999-05-25", "【昼夜大火灾 铁壁烧血】", "1999.05 800直伤",
                                        "加入昼夜的大火灾(800)与岩石巨兵/食人虫/强欲之壶，一边守备一边高速削血。",
                                        recipe(19523799, 3, 73134081, 3, 46130346, 3, 55144522, 3, 72302403, 3, 13039848, 3, 15025844, 3, 54652250, 3, 31560081, 3, 4206964, 3),
                                        null, "BURN_WALL"),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【加农炮兵·遗言状 射出烧血】", "1999.11~12 射出流",
                                        "yugioh-history 第19回记载的「加农炮兵＋三眼怪/黑森林魔女＋遗言状(12.16)」连锁射出直伤！",
                                        recipe(11384280, 3, 26202165, 3, 78010363, 3, 85602018, 3, 31560081, 3, 55144522, 3, 79571449, 3, 19523799, 3, 73134081, 3, 72302403, 3, 12607053, 3, 83764718, 3, 53129443, 1),
                                        null, "BURN_WALL")
                        )
                ),

                // 4. Seto Kaiba (1999-03-06): No-Tribute Blue-Eyes Dark Age -> Expert Transition -> La Jinn/Mechanicalchaser -> EX Kaiba
                new AiOpponent(
                        "ai_starter_kaiba",
                        "社长·海马濑人",
                        "Lv.3 · 青眼与高打点压制",
                        "1999-03-06",
                        3, 350, 150, 90,
                        "3~4月再现 yugioh-history「生け贄無し最上級」暗黑期（免祭品3青眼+3雷击），5月后自动转型高打点压制流！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-03-06", "【生け贄無し最上級 · 青眼雷击】", "1999.03~04 暗黑期顶配",
                                        "完全复刻 yugioh-history《生け贄無し最上級》：公式规则免祭品直拍 3 青眼白龙＋3 黑魔＋3 盖亚＋3 雷击＋3 黑洞！",
                                        recipe(89631139, 3, 46986414, 3, 6368038, 3, 28279543, 3, 85639257, 3, 1784619, 3, 12580477, 3, 83764718, 3, 66788016, 3, 72302403, 3, 53129443, 3, 4206964, 3, 83887306, 1),
                                        null, "NO_TRIBUTE_BEAT"),
                                new AiOpponent.EraDeckProfile("1999-05-05", "【专家规则 · 青眼下级过渡】", "1999.05 祭品制适应",
                                        "5月5日专家规则取消免祭品，海马立刻缩减青眼至 2 张，改以高守壁垒与高攻下级垫场＋死者苏生出青眼。",
                                        recipe(89631139, 2, 28279543, 2, 91939608, 3, 41762634, 3, 1784619, 3, 85639257, 3, 15025844, 3, 12580477, 3, 83764718, 3, 72302403, 3, 66788016, 3, 53129443, 3, 4206964, 3),
                                        null, "LOWER_BEAT"),
                                new AiOpponent.EraDeckProfile("1999-08-26", "【海马高打点 · 弃墓苏生青眼】", "1999.08 巨蛋高速强攻",
                                        "双生精灵(1900)、机械猎手(1850)、灯之魔精(1800) 全面提速，配合天使的施舍弃青眼＋死者苏生速出 3000 打点！",
                                        recipe(89631139, 2, 70781052, 2, 69140098, 3, 7359741, 3, 97590747, 3, 55144522, 3, 79571449, 3, 83764718, 3, 18144506, 2, 66788016, 3, 72302403, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF"),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 海马社长 · 死毒压制】", "1999.12 EX完全体",
                                        "集结灯之魔精、机械猎手、双生精灵、青眼白龙与 1999 原版死之卡组破坏病毒，兼顾打点与手牌压制。",
                                        recipe(89631139, 2, 70781052, 2, 69140098, 3, 7359741, 3, 97590747, 3, 26202165, 3, 57728570, 2, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 18144506, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        recipe(23995346, 1), "GOOD_STUFF")
                        )
                ),

                // 5. Equip Beat Pioneer (1999-05-25): yugioh-history #5 First Concept Deck【装備ビート（属性軸）】
                new AiOpponent(
                        "ai_equip_beat",
                        "装备重击流先驱",
                        "Lv.3 · OCG首个概念卡组【装备Beat】",
                        "1999-05-25",
                        3, 380, 160, 95,
                        "还原 yugioh-history 第5回「OCG首个概念卡组【装備ビート（属性軸）】」，以属性装备魔法免祭品制造上级打点！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-05-25", "【光·地双属性 装备Beat】", "1999.05 Booster 2首创",
                                        "神圣人偶(1600)+精灵之光→2000，吸血跳蚤/荒野盗龙(1500)+觉醒→1900，无祭品压制上级怪兽。",
                                        recipe(91939608, 3, 41762634, 3, 1784619, 3, 84990171, 3, 91152256, 3, 15025844, 2, 80770678, 2, 39897277, 3, 98374133, 3, 12580477, 3, 53129443, 2, 66788016, 3, 83764718, 3, 4206964, 3),
                                        null, "EQUIP_LIGHT_EARTH"),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【暗·光强化 恶魔装备Beat】", "1999.07 Vol.4升级",
                                        "引入恶魔召唤＋暗之破神剑(2900打点)与圣魔术师回收强欲之壶/装备卡，火力倍增。",
                                        recipe(70781052, 3, 91939608, 3, 41762634, 3, 1784619, 3, 31560081, 3, 37120512, 3, 39897277, 3, 98374133, 2, 55144522, 3, 83764718, 3, 12580477, 3, 4206964, 3),
                                        null, "EQUIP_LIGHT_EARTH"),
                                new AiOpponent.EraDeckProfile("1999-12-09", "【金属化·高攻下级 装备Beat】", "1999.12 PS特典强化",
                                        "加入双生精灵(1900)、机械猎手(1850)、恶魔兽灵与 PS 特典「金属化·魔法反射装甲」，全员打点突破天际。",
                                        recipe(69140098, 3, 7359741, 3, 97590747, 3, 70781052, 2, 24311372, 1, 68540058, 3, 37120512, 3, 98374133, 2, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 18144506, 2, 12580477, 1, 53129443, 1),
                                        null, "EQUIP_LIGHT_EARTH")
                        )
                ),

                // 6. Flip Control Master (1999-05-27): yugioh-history #6/#10/#15【グッドスタッフ（リバース軸）】&【メタモルポット】
                new AiOpponent(
                        "ai_flip_control",
                        "反转控制大师",
                        "Lv.3 · 反转轴 Good Stuff",
                        "1999-05-27",
                        3, 440, 180, 110,
                        "还原 yugioh-history 第6/10/15回「反转轴 Good Stuff」：从 Vol.3 食人虫/哈尼哈尼进化至圣魔术师、变形壶与企鹅士兵。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-05-27", "【Vol.3 反转轴 Good Stuff】", "1999.05 反转时代开启",
                                        "以食人虫(破坏)、哈尼哈尼(弹手)、骸骨天使(抽1) 搭配光之护封剑与岩石巨兵进行心理博弈。",
                                        recipe(54652250, 3, 7089711, 3, 60694662, 3, 9076207, 2, 13039848, 3, 91939608, 3, 28279543, 2, 55144522, 3, 72302403, 3, 83764718, 3, 12580477, 3, 53129443, 2, 4206964, 3),
                                        null, "FLIP_CONTROL"),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【圣魔术师＆恶魔召唤 赚卡反转】", "1999.07 圣魔术师降临",
                                        "圣魔术师满编 3 张循环回收强欲之壶与解场魔法，配合恶魔召唤稳步扩大卡差。",
                                        recipe(31560081, 3, 54652250, 3, 7089711, 2, 60694662, 2, 70781052, 3, 13039848, 3, 91939608, 3, 55144522, 3, 83764718, 3, 72302403, 3, 12580477, 3, 53129443, 2, 4206964, 3),
                                        null, "FLIP_CONTROL"),
                                new AiOpponent.EraDeckProfile("1999-10-17", "【变形壶·针虫·通告 反制轴】", "1999.10 Booster 5",
                                        "yugioh-history 第15回主角「变形壶」与「针虫」登场，手牌清空重抓 5 张＋王宫的通告封锁陷阱。",
                                        recipe(33508719, 3, 81843628, 3, 31560081, 3, 54652250, 3, 70781052, 3, 69140098, 3, 51452091, 2, 55144522, 3, 79571449, 3, 4031928, 3, 79759861, 2, 83764718, 3, 72302403, 2, 12580477, 1, 53129443, 1),
                                        null, "FLIP_CONTROL"),
                                new AiOpponent.EraDeckProfile("1999-12-01", "【企鹅士兵·幻影壁 终极弹手控】", "1999.12 弹手控制完全体",
                                        "企鹅士兵(弹2)＋幻影壁(被攻弹手)＋变形壶(针对艾克佐迪亚)＋三眼怪/和睦的使者。",
                                        recipe(93920745, 3, 13945283, 3, 33508719, 3, 31560081, 3, 54652250, 2, 26202165, 3, 70781052, 2, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 72302403, 2, 12607053, 3, 12580477, 1, 53129443, 1),
                                        null, "FLIP_CONTROL")
                        )
                ),

                // 7. Crush Card Virus Dark Control (1999-07-08): yugioh-history 8~11月【死のデッキ破壊ウイルス】
                new AiOpponent(
                        "ai_virus_control",
                        "死毒暗控猎手",
                        "Lv.3 · 【死之卡组破坏病毒】专精",
                        "1999-07-08",
                        3, 500, 210, 125,
                        "还原 yugioh-history 环境年表 8~11 月【死のデッキ破壊ウイルス】：以暗之假面/三眼怪为媒体引爆 1999 原版 3 回合病毒！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-07-08", "【DM2 初代死毒暗属性】", "1999.07 病毒降临",
                                        "围绕 DM2 特典「死之卡组破坏病毒」(1999原版摧毁后续3回合抽卡) 与低攻暗属性怪兽构筑。",
                                        recipe(57728570, 3, 41356845, 2, 18144506, 2, 55144522, 3, 83764718, 3, 12580477, 3, 53129443, 2, 72302403, 3, 4206964, 3, 36304921, 3, 74677422, 2, 53375573, 3),
                                        null, "DARK_VIRUS"),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【暗之假面 · 死毒循环控】", "1999.07~08 暗假面循环",
                                        "yugioh-history 第10回实录：暗之假面(暗/ATK 900) 既是死毒祭品又能反转回收死毒，配恶魔召唤收割！",
                                        recipe(57728570, 3, 28933734, 3, 70781052, 3, 31560081, 3, 54652250, 3, 55144522, 3, 83764718, 3, 18144506, 2, 72302403, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "DARK_VIRUS"),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【三眼怪/魔女 · 死毒完全体】", "1999.11 Vol.6终极暗控",
                                        "三眼怪(暗/ATK 1000) 献祭发动死毒后还能触发检索！配合机械猎手、天使的施舍、神之宣告彻底封锁对手。",
                                        recipe(57728570, 3, 26202165, 3, 78010363, 3, 28933734, 3, 7359741, 3, 70781052, 3, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 18144506, 2, 41420027, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "DARK_VIRUS")
                        )
                ),

                // 8. Tokyo Dome Finalist (1999-08-26): yugioh-history 8~12月【グッドスタッフ（竞技主流）】
                new AiOpponent(
                        "ai_tokyo_dome",
                        "东京巨蛋大会决赛选手",
                        "Lv.4 · 竞技主流【Good Stuff】",
                        "1999-08-26",
                        4, 600, 240, 140,
                        "代表 1999 下半年最稳定的竞技主流 Good Stuff，随 Vol.5/Vol.6/EX 持续更新至「抗艾克佐迪亚变形壶构筑」。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-08-26", "【东京巨蛋 首次限表 Good Stuff】", "1999.08 巨蛋竞技顶配",
                                        "严守 1999.08.26 首份限制表（黑洞/落穴/雷击各限1），以双生精灵(1900)/机械猎手(1850)/恶魔召唤＋施舍/强欲主宰场面。",
                                        recipe(69140098, 3, 7359741, 3, 97590747, 3, 70781052, 3, 31560081, 3, 54652250, 3, 55144522, 3, 79571449, 3, 83764718, 3, 18144506, 3, 72302403, 2, 66788016, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF"),
                                new AiOpponent.EraDeckProfile("1999-09-23", "【Vol.5 心变·三魔神 Good Stuff】", "1999.09 Vol.5强化",
                                        "满编心变(夺取献祭恶魔召唤)与对死者的供奉，外挂 1999 原版三魔神(伤害归0) 强化场面压制。",
                                        recipe(69140098, 3, 7359741, 3, 97590747, 3, 70781052, 3, 25955164, 1, 98434877, 1, 31560081, 3, 54652250, 2, 55144522, 3, 79571449, 3, 4031928, 3, 79759861, 2, 83764718, 3, 18144506, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF"),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【Vol.6 双检索·神宣 Good Stuff】", "1999.11 反击时代",
                                        "加入 Vol.6 三眼怪、黑森林魔女与反击陷阱「神之宣告」「盗贼的七道具」，兼顾续航与反制。",
                                        recipe(69140098, 3, 7359741, 3, 70781052, 3, 26202165, 3, 78010363, 3, 31560081, 3, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 18144506, 2, 41420027, 3, 3819470, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF"),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【变形壶对策 · 99年末 Good Stuff】", "1999.12 抗暗黑期",
                                        "yugioh-history 12月年表实录：为对抗艾克佐迪亚满编变形壶与神之宣告，搭配幻影壁、和睦的使者与强攻主力。",
                                        recipe(69140098, 3, 7359741, 3, 70781052, 3, 13945283, 3, 33508719, 3, 26202165, 3, 78010363, 2, 31560081, 2, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 41420027, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF")
                        )
                ),

                // 9. Exodia Master (1999-08-26): yugioh-history【エクゾディア】（第1期）exact historical recipes!
                new AiOpponent(
                        "ai_exodia_master",
                        "被封印的艾克佐迪亚使",
                        "Lv.4 · 【第1期 艾克佐迪亚】暗黑期霸主",
                        "1999-08-26",
                        4, 700, 280, 160,
                        "严格复刻 yugioh-history《エクゾディア（第1期）》三阶段构筑：9月混轴 → 11.18勘误前三眼魔女 → 12.16遗言状暗黑期完全体！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-08-26", "【PP1 初代混轴艾克佐迪亚】", "1999.08~10 黎明期",
                                        "PP1 集齐 5 部件！尚未出现三眼怪/魔女，依靠强欲之壶、天使的施舍、骸骨天使与护封剑滤抽。",
                                        recipe(33396948, 2, 7902349, 2, 70903634, 2, 44519536, 2, 8124921, 2, 60694662, 3, 31560081, 3, 13039848, 3, 15025844, 2, 55144522, 3, 79571449, 3, 72302403, 3, 83764718, 3, 54652250, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "EXODIA_DRAW"),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【Vol.6 勘误前三眼/魔女 艾克佐迪亚】", "1999.11.18 顶峰登顶",
                                        "复刻 yugioh-history 1999.11.18 原文配方：天使的施舍弃掉原版三眼怪/黑森林魔女直接检索部件，横扫整个环境！",
                                        recipe(26202165, 3, 78010363, 3, 31560081, 3, 60694662, 2, 33396948, 2, 7902349, 2, 70903634, 2, 44519536, 2, 8124921, 2, 55144522, 3, 83764718, 3, 79571449, 3, 72302403, 3, 4031928, 2, 79759861, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "EXODIA_DRAW"),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 遗言状循环 · 世纪末暗黑期完全体】", "1999.12.16 暗黑期极点",
                                        "复刻 yugioh-history 1999.12.16 原文配方：3 三眼＋3 魔女＋3 遗言状＋加农炮兵＋变形壶＋3 神宣，1999 最强禁断构筑！",
                                        recipe(26202165, 3, 78010363, 3, 31560081, 3, 33396948, 2, 7902349, 2, 70903634, 2, 44519536, 2, 8124921, 2, 11384280, 1, 33508719, 1, 55144522, 3, 83764718, 3, 79759861, 3, 79571449, 3, 85602018, 3, 53129443, 1, 41420027, 3),
                                        null, "EXODIA_DRAW")
                        )
                ),

                // 10. Fusion & Cyber-Stein Master (1999-12-01): yugioh-history #17【融合召喚（第1期）】+ Cyber-Stein
                new AiOpponent(
                        "ai_fusion_stein",
                        "融合＆恶魔科学怪人大师",
                        "Lv.4 · 【第1期 融合召唤＆科学怪人】",
                        "1999-12-01",
                        4, 750, 300, 175,
                        "还原 yugioh-history 第17回「融合召唤（第1期）」与 Booster 6 恶魔科学怪人：融合代用素材＋暗黑魔龙(3200)＋青眼究极龙(4500)！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-12-01", "【Booster 6 正规融合＆科学怪人】", "1999.12.01 融合体系诞生",
                                        "三眼怪/黑森林魔女＋心眼之女神/沼地魔兽王/破坏神瓦沙克＋融合贤者＋恶魔科学怪人，直出暗黑魔龙与青眼究极龙！",
                                        recipe(26202165, 3, 78010363, 3, 53493204, 3, 99426834, 2, 50259460, 2, 69015963, 3, 31560081, 3, 70781052, 2, 84133008, 1, 55144522, 3, 4031928, 3, 83764718, 3, 79571449, 3, 24094653, 3, 26902560, 2, 12580477, 1),
                                        recipe(23995346, 3, 11901678, 3, 56907389, 3), "FUSION_COMBO"),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 世纪末 遗言状·科学怪人奇袭】", "1999.12.16 终极奇袭",
                                        "加入遗言状(从卡组直接特召恶魔科学怪人!)、和睦的使者与神之宣告，极速展开青眼究极龙(4500)斩杀！",
                                        recipe(26202165, 3, 78010363, 3, 69015963, 3, 53493204, 2, 99426834, 2, 70781052, 2, 13945283, 2, 31560081, 2, 55144522, 3, 79571449, 3, 85602018, 3, 4031928, 3, 83764718, 3, 24094653, 2, 12607053, 2, 41420027, 2),
                                        recipe(23995346, 3, 11901678, 3, 56907389, 3), "FUSION_COMBO")
                        )
                )
        );
    }

    public static GeneratedAiDeck generateDeck(RpgCatalog catalog, AiOpponent opponent,
                                               String currentDate, Random random) {
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, currentDate);
        OcgRuleSet ruleSet = TimelineEngine.getActiveRuleSet(catalog, currentDate);
        List<RpgCard> released = TimelineEngine.getReleasedCards(catalog, currentDate);
        AiOpponent.EraDeckProfile profile = opponent.getActiveDeckProfile(currentDate);
        String style = profile != null ? profile.fillStyle : "GOOD_STUFF";

        List<Integer> main = new ArrayList<>(40);
        List<Integer> extra = new ArrayList<>();

        // 1) Add core historical deck cards from the active EraDeckProfile (clamped by timeline & banlist)
        if (profile != null) {
            for (Map.Entry<Integer, Integer> entry : profile.coreMain.entrySet()) {
                int id = entry.getKey();
                int want = entry.getValue();
                if (!TimelineEngine.isCardReleased(catalog, id, currentDate)) continue;
                RpgCard c = catalog.findCard(id);
                if (c == null || c.isExtraDeckCard()) continue;
                int legalMax = banlist.getMaxCopies(id);
                int already = countOf(main, id);
                int toAdd = Math.min(want, Math.max(0, legalMax - already));
                for (int i = 0; i < toAdd && main.size() < 40; i++) {
                    main.add(id);
                }
            }
            for (Map.Entry<Integer, Integer> entry : profile.coreExtra.entrySet()) {
                int id = entry.getKey();
                int want = entry.getValue();
                if (!TimelineEngine.isCardReleased(catalog, id, currentDate)) continue;
                RpgCard c = catalog.findCard(id);
                if (c == null || !c.isExtraDeckCard()) continue;
                int legalMax = banlist.getMaxCopies(id);
                int toAdd = Math.min(want, legalMax);
                for (int i = 0; i < toAdd && extra.size() < 15; i++) {
                    extra.add(id);
                }
            }
        }

        // 2) Fill remaining slots up to 40 using archetype-aware scoring (strictly <= currentDate & banlist)
        if (main.size() < 40) {
            boolean isNonTopTier = "NOVICE_NR".equals(style)
                    || "CASUAL_MID".equals(style)
                    || "MID_TIER_THEME".equals(style);
            List<RpgCard> fillPool = new ArrayList<>();
            for (RpgCard c : released) {
                if (c.isExtraDeckCard() || "ritual".equalsIgnoreCase(c.frameType)) continue;
                if (banlist.getMaxCopies(c.id) <= 0) continue;
                if (isNonTopTier && isTopTierStaple(c.id)) continue;
                if (("NOVICE_NR".equals(style) || "CASUAL_MID".equals(style))
                        && c.getRarityEnum().ordinal() >= Rarity.SR.ordinal()) continue;
                if ("MID_TIER_THEME".equals(style) && c.getRarityEnum().ordinal() >= Rarity.UR.ordinal()) continue;
                fillPool.add(c);
            }
            if (fillPool.isEmpty()) {
                for (RpgCard c : released) {
                    if (!c.isExtraDeckCard() && banlist.getMaxCopies(c.id) > 0) fillPool.add(c);
                }
            }

            Collections.sort(fillPool, new Comparator<RpgCard>() {
                @Override
                public int compare(RpgCard a, RpgCard b) {
                    return Integer.compare(scoreForStyle(b, style, ruleSet), scoreForStyle(a, style, ruleSet));
                }
            });

            if (random != null && fillPool.size() > 8) {
                int window = Math.min(12, fillPool.size());
                Collections.shuffle(fillPool.subList(0, window), random);
            }

            int prefCopies = ("NOVICE_NR".equals(style) || "CASUAL_MID".equals(style)) ? 2 : 3;
            for (RpgCard c : fillPool) {
                int already = countOf(main, c.id);
                int legalMax = Math.min(prefCopies, banlist.getMaxCopies(c.id));
                while (already < legalMax && main.size() < 40) {
                    main.add(c.id);
                    already++;
                }
                if (main.size() >= 40) break;
            }
            if (main.size() < 40) {
                for (RpgCard c : fillPool) {
                    int already = countOf(main, c.id);
                    int legalMax = banlist.getMaxCopies(c.id);
                    while (already < legalMax && main.size() < 40) {
                        main.add(c.id);
                        already++;
                    }
                    if (main.size() >= 40) break;
                }
            }
        }

        return new GeneratedAiDeck(main, extra, banlist, profile);
    }

    /** Top-tier 1999 blowout staples withheld from novice/casual/mid-tier filler pools. */
    private static boolean isTopTierStaple(int cardId) {
        return cardId == 12580477 // 雷击
                || cardId == 53129443 // 黑洞
                || cardId == 55144522 // 强欲之壶
                || cardId == 79571449 // 天使的施舍
                || cardId == 83764718 // 死者苏生
                || cardId == 4031928  // 心变
                || cardId == 44095762 // 神圣防护罩-反射镜力-
                || cardId == 57728570 // 死之卡组破坏病毒
                || cardId == 18144506 // 鹰身女妖的羽毛扫
                || cardId == 41420027 // 神之宣告
                || cardId == 4206964  // 落穴
                || cardId == 69140098 // 双生精灵
                || cardId == 7359741; // 机械猎手
    }

    private static int scoreForStyle(RpgCard c, String style, OcgRuleSet rule) {
        int base = DeckValidator.scoreCard(c);
        if ("NO_TRIBUTE_BEAT".equals(style)) {
            return c.isMonster() ? (c.atk * 2 + c.def / 2) : base;
        }
        if ("NOVICE_NR".equals(style)) {
            int pen = (rule != null && rule.tributeSummonEnabled && c.level >= 5) ? 900 : 0;
            return c.isMonster() ? (Math.max(c.atk, c.def) - pen) : 1100;
        }
        if ("CASUAL_MID".equals(style)) {
            if (!c.isMonster()) return 950;
            int stat = Math.max(c.atk, c.def);
            // Prefer mid-low 900~1450 monsters instead of top beaters
            int distPenalty = Math.abs(stat - 1250);
            int highLevelPen = (c.level >= 5) ? 600 : 0;
            return 1600 - distPenalty - highLevelPen;
        }
        if ("MID_TIER_THEME".equals(style)) {
            if (!c.isMonster()) return 1150;
            int stat = Math.max(c.atk, c.def);
            int distPenalty = Math.abs(stat - 1500);
            int highLevelPen = (rule != null && rule.tributeSummonEnabled && c.level >= 7) ? 700 : 0;
            return 1900 - distPenalty - highLevelPen;
        }
        if ("EQUIP_LIGHT_EARTH".equals(style)) {
            int bonus = ("LIGHT".equalsIgnoreCase(c.attribute) || "EARTH".equalsIgnoreCase(c.attribute)) ? 600 : 0;
            int pen = (c.level >= 5) ? 500 : 0;
            return base + bonus - pen;
        }
        if ("DARK_VIRUS".equals(style)) {
            int bonus = "DARK".equalsIgnoreCase(c.attribute) ? (c.atk <= 1000 ? 700 : 450) : 0;
            return base + bonus;
        }
        if ("BURN_WALL".equals(style)) {
            int bonus = c.isMonster() ? (c.def >= 1500 ? 600 : -200) : 500;
            return base + bonus;
        }
        if ("FLIP_CONTROL".equals(style)) {
            int bonus = (c.descCn != null && c.descCn.contains("反转")) ? 900 : (c.def >= 1800 ? 400 : 0);
            return base + bonus;
        }
        if ("EXODIA_DRAW".equals(style)) {
            int bonus = c.isMonster() ? (c.def >= 1500 ? 500 : -300) : 600;
            return base + bonus;
        }
        // LOWER_BEAT / GOOD_STUFF / FUSION_COMBO
        int pen = (rule != null && rule.tributeSummonEnabled) ? (c.level >= 7 ? 700 : (c.level >= 5 ? 250 : 0)) : 0;
        return base - pen;
    }

    private static int countOf(List<Integer> list, int target) {
        int n = 0;
        for (Integer id : list) {
            if (id != null && id == target) n++;
        }
        return n;
    }
}
