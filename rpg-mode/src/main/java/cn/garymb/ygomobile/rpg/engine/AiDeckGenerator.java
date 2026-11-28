package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import cn.garymb.ygomobile.rpg.ai.RpgAiCardKnowledge;
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
     * Built-in 10 OCG 1999 AI opponents (1999-02-04 ~ 1999-12-31) with multi-era deck evolutions:
     * includes 3 progressive non-meta/mid-tier opponents (Novice, Shop Regular, Weekend Challenger)
     * plus 7 iconic metagame opponents grounded in yugioh-history.com's Series 1 chronicle.
     *
     * <p><b>MANDATORY RULE WHEN ADDING NEW AI OPPONENTS OR NEW ERA DECKS:</b>
     * Every new {@link AiOpponent} or new {@link AiOpponent.EraDeckProfile} MUST also receive
     * dedicated pure-Java AI strategy support (never rely on generic blind card play):
     * <ol>
     *   <li>Register new signature cards/roles/combos in {@code RpgAiCardKnowledge};</li>
     *   <li>Register any new {@code fillStyle} in {@code RpgAiStrategy.SUPPORTED_STYLES} and
     *       implement dedicated turn-order, play-order, targeting, discard, search, and chain
     *       tactics in {@code RpgAiStrategy} (and {@code RpgAiDuelState} if new state is needed);</li>
     *   <li>Add tactical unit tests in {@code RpgAiStrategyTest} and run {@code :rpg-mode:testDebugUnitTest}.</li>
     * </ol>
     */
    public static List<AiOpponent> getDemoOpponents() {
        return Arrays.asList(
                // 1. Street Novice (1999-02-04): evolves N/R Vol.1 -> Booster 2 Lower Beat -> Booster 5 Machine King
                new AiOpponent(
                        "ai_street_novice",
                        "街边新人决斗者",
                        "Lv.1 · 平民凡骨进化线",
                        "1999-02-04",
                        1, 150, 70, 45,
                        "随时间轴从 Vol.1 凡骨进化到 Booster 2 高攻下级、Booster 5 平民机械王，不带顶级神卡，适合稳步赚代币。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【Vol.1 初学者凡骨】", "1999.02 入门",
                                        "仅用 Vol.1 低罕贵凡骨与基础装备/回复卡。",
                                        recipe(76184692, 3, 40374923, 3, 90357090, 3, 15401633, 3, 64511793, 2, 61854111, 2, 46009906, 2, 38199696, 2, 76103675, 2),
                                        null, "NOVICE_NR", 150, 70, 45),
                                new AiOpponent.EraDeckProfile("1999-05-25", "【Booster 2 下级凡骨重击】", "1999.05 专家规则入门",
                                        "适应祭品规则，换装神圣人偶(1600)、吸血跳蚤(1500)、豌豆战士(1400)等高攻下级。",
                                        recipe(91939608, 3, 41762634, 3, 1784619, 3, 84990171, 3, 91152256, 2, 66788016, 2, 39897277, 2, 98374133, 2),
                                        null, "NOVICE_NR", 160, 75, 50),
                                new AiOpponent.EraDeckProfile("1999-10-17", "【Booster 5 平民机械王】", "1999.10 机械王轴",
                                        "yugioh-history 提及的高性价比 N 卡「机械王」(实质 2300+ 打点) 搭配岩石巨兵与基础解场。",
                                        recipe(46700124, 3, 13039848, 3, 91939608, 3, 41762634, 3, 66788016, 2, 72302403, 1),
                                        null, "NOVICE_NR", 180, 85, 55)
                        )
                ),

                // 2. Card Shop Regular (1999-02-04): progressive non-meta casual decks without Raigeki/Dark Hole/Pot/Trap Hole
                new AiOpponent(
                        "ai_shop_regular",
                        "卡牌屋常客·阿健",
                        "Lv.1 · 校园非顶级娱乐流",
                        "1999-02-04",
                        1, 180, 85, 55,
                        "刻意避开黑洞、雷击、强欲、落穴等顶级泛用神卡，用中游凡骨与趣味装备陪你平滑度过开荒期。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【Vol.1 校园杂牌兽战】", "1999.02 休闲开荒",
                                        "独眼巨人、银牙狼、猛犸的墓场搭配猛兽之齿/传说之剑，无黑洞无落穴，前期最稳练手对手。",
                                        recipe(76184692, 3, 90357090, 3, 40374923, 3, 9159938, 3, 15401633, 3, 90963488, 3, 46009906, 2, 61854111, 2, 38199696, 3),
                                        null, "CASUAL_MID", 180, 85, 55),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【Vol.2 奇趣下级混编】", "1999.03 休闲升级",
                                        "换装荒野盗龙(1500)、大炮鸟、魔人泰拉、恍惚的人鱼、镜子幻象鸟、烟之王配暗能量，稳健推进但绝不超模。",
                                        recipe(1784619, 3, 72842870, 3, 63308047, 3, 75376965, 3, 10859908, 3, 2863439, 3, 84686841, 3, 4614116, 2, 38199696, 2),
                                        null, "CASUAL_MID", 190, 90, 60),
                                new AiOpponent.EraDeckProfile("1999-05-27", "【Vol.3 中游凡骨休闲组】", "1999.05 休闲过渡",
                                        "天空猎手(1550)、死亡撒旦(1400)、炎之魔神、下级龙配『守备』封禁，适合中前期刷代币攒卡。",
                                        recipe(10202894, 3, 55444629, 3, 36304921, 3, 71407486, 3, 32452818, 3, 98818516, 3, 63102017, 2, 66788016, 2),
                                        null, "CASUAL_MID", 200, 95, 60),
                                new AiOpponent.EraDeckProfile("1999-09-23", "【Vol.5 校园中坚混打】", "1999.09 中游娱乐",
                                        "苍白兽、兵主部、响女、铠武者僵尸搭配 1 祭品粗暴帝王，体验纯正校园桌游对决。",
                                        recipe(21263083, 3, 2118022, 3, 64501875, 3, 20277860, 3, 76446915, 3, 99261403, 3, 26378150, 2, 66788016, 2),
                                        null, "CASUAL_MID", 220, 100, 65)
                        )
                ),

                // 3. Weekend Shop Challenger (1999-03-01): Joey-style mid-tier warrior/dragon/gamble progression
                new AiOpponent(
                        "ai_weekend_challenger",
                        "周末店赛挑战者·热血城之内风",
                        "Lv.2 · 战士/龙/赌博中游流",
                        "1999-03-01",
                        2, 220, 100, 65,
                        "定位介于新手与环境霸主之间的中坚对手：主打城之内原著战士/炎之剑士/真红眼/时间魔术师情怀轴，不混用游戏与海马专属王牌。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-03-01", "【Booster 1 街头战士＆雏龙】", "1999.03 中游起步",
                                        "格斗战士阿提米特、女夜魔战士、多隆、紫炎影武者、魔头邪龙与传说之剑，作为挑战暗游戏前的练兵。",
                                        recipe(55550921, 3, 78556320, 3, 756652, 3, 15401633, 3, 64511793, 3, 2957055, 3, 76704943, 3, 90357090, 3, 61854111, 3, 46009906, 2, 66788016, 1, 38199696, 2),
                                        null, "MID_TIER_THEME", 220, 100, 65),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【Vol.2 炎之剑士＆正树战士轴】", "1999.03 店赛中坚",
                                        "传说的剑豪正树＋火焰操纵者正规融合「炎之剑士」！搭配格斗战士阿提米特、女夜魔战士、草原与传说之剑。",
                                        recipe(44287299, 3, 34460851, 3, 55550921, 3, 78556320, 3, 756652, 3, 1784619, 3, 72842870, 3, 2957055, 3, 24094653, 2, 61854111, 3, 86318356, 2, 66788016, 2, 38199696, 2),
                                        recipe(45231177, 2), "MID_TIER_THEME", 240, 110, 70),
                                new AiOpponent.EraDeckProfile("1999-05-27", "【Vol.3 真红眼＆热血战士轴】", "1999.05 情怀中游",
                                        "真红眼黑龙(2400)率领炎之剑士素材正树＆火焰操纵者、暗杀者、女夜魔骑士与下级龙，攻势爽快且原著味十足。",
                                        recipe(74677422, 2, 48365709, 2, 55291359, 2, 44287299, 3, 34460851, 2, 55444629, 3, 10202894, 3, 1784619, 3, 1435851, 2, 61854111, 2, 24094653, 2, 86318356, 2, 66788016, 2, 72302403, 1),
                                        recipe(45231177, 2), "MID_TIER_THEME", 260, 115, 75),
                                new AiOpponent.EraDeckProfile("1999-08-26", "【巨蛋后 真红眼·巨斧＆时间魔术师赌博流】", "1999.08~12 热血赌博",
                                        "巨斧袭击者(1700)、真红眼黑龙、魔物狩人、铠蜥蜴、老虎斧战士、时间魔术师＋宝贝龙(融合千年龙)与附锁链的回力镖！",
                                        recipe(48305365, 3, 74677422, 2, 71625222, 2, 88819587, 2, 1184620, 3, 15480588, 3, 49791927, 3, 14977074, 2, 44287299, 2, 34460851, 2, 37390589, 3, 17814387, 2, 24094653, 2, 61854111, 2, 66788016, 2, 72302403, 1),
                                        recipe(41462083, 2, 45231177, 2), "MID_TIER_THEME", 280, 125, 80)
                        )
                ),

                // 4. Classic Yugi (1999-02-04): No-Tribute DM -> Vol.2 Reborn -> Expert Flip -> Summoned Skull -> EX Duel King
                new AiOpponent(
                        "ai_vol1_yugi",
                        "初代暗游戏",
                        "Lv.3 · 主角卡组编年史",
                        "1999-02-04",
                        3, 380, 160, 95,
                        "严格还原初代暗游戏原著与 EX 游戏卡组编年史：黑魔术师/盖亚 → 诅咒之龙/圣精灵/护封剑 → 岩石巨兵/食人虫 → 恶魔召唤 → EX 魔法剑士尼奥＆幻影壁！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【免祭品最上级 · 黑魔术师＆盖亚】", "1999.02 公式规则",
                                        "利用初代公式规则直拍黑魔术师(2500)与暗黑骑士盖亚(2300)，搭配猛犸的墓场、银牙狼、恶魔海狸、黑魔族的幕帘与秘术之书。",
                                        recipe(46986414, 3, 6368038, 3, 40374923, 3, 90357090, 3, 75889523, 2, 22026707, 3, 90963488, 3, 15507080, 3, 15401633, 3, 53129443, 3, 4206964, 3, 66788016, 3, 91595718, 3, 61854111, 2),
                                        null, "NO_TRIBUTE_BEAT", 380, 160, 95),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【Vol.2 护封剑·死者苏生轴】", "1999.03 Vol.2强化",
                                        "加入诅咒之龙、圣精灵(2000守)、精灵剑士、光之护封剑、死者苏生与场地「暗」，纯正主角王牌阵！",
                                        recipe(46986414, 3, 6368038, 3, 28279543, 3, 15025844, 3, 91152256, 3, 40374923, 3, 90357090, 2, 63308047, 3, 83764718, 3, 72302403, 3, 53129443, 3, 4206964, 3, 66788016, 3, 59197169, 2),
                                        null, "NO_TRIBUTE_BEAT", 420, 180, 105),
                                new AiOpponent.EraDeckProfile("1999-05-27", "【专家规则 · 岩石巨兵＆食人虫】", "1999.05 专家规则转型",
                                        "切换为 1 祭品+强力下级/反转构筑：诅咒之龙、黑魔术师、岩石巨兵、圣精灵、精灵剑士、路易斯、神圣人偶、食人虫与哈尼哈尼。",
                                        recipe(28279543, 2, 46986414, 1, 91939608, 3, 91152256, 3, 13039848, 3, 15025844, 3, 32452818, 2, 54652250, 3, 7089711, 2, 55144522, 3, 83764718, 3, 72302403, 3, 53129443, 3, 4206964, 3, 66788016, 3),
                                        null, "LOWER_BEAT", 400, 170, 100),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【Vol.4 恶魔召唤＆守城翼龙】", "1999.07 上级革命",
                                        "换装 1 祭品 2500 打点主角王牌「恶魔召唤」与守城的翼龙、古代精灵、圣魔术师，绝不混用海马专属怪兽。",
                                        recipe(70781052, 3, 46986414, 1, 87796900, 3, 93221206, 3, 91939608, 3, 91152256, 2, 13039848, 3, 31560081, 3, 54652250, 3, 55144522, 3, 83764718, 3, 12580477, 2, 53129443, 2, 72302403, 2, 66788016, 2, 4206964, 2),
                                        null, "GOOD_STUFF", 500, 215, 125),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 决斗王 · 魔法剑士尼奥/幻影壁/黑魔】", "1999.12 EX游戏盒",
                                        "全面采用 EX 游戏盒王牌：恶魔召唤、黑魔术师、魔法剑士尼奥(1700)、食人宝石箱、邪剑男爵、幻影壁、和睦的使者与三眼/魔女。",
                                        recipe(70781052, 3, 46986414, 1, 50930991, 3, 13723605, 2, 86325596, 2, 13945283, 3, 26202165, 3, 78010363, 2, 31560081, 3, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 12607053, 2, 72302403, 1, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF", 620, 260, 155)
                        )
                ),

                // 5. Burn & Direct Damage Mage (1999-02-04): Vol.1 Burn -> Fire Wall -> Big Burn -> Cannon Soldier Last Will
                new AiOpponent(
                        "ai_burn_mage",
                        "火焰直伤术士",
                        "Lv.2 · 魔法烧血与射出流",
                        "1999-02-04",
                        2, 240, 110, 70,
                        "专精直接伤害与高守壁垒，11~12月进化为 yugioh-history 第19回记载的「加农炮兵＋遗言状射出烧血」！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-02-04", "【Vol.1 初代火星直伤】", "1999.02 初期烧血",
                                        "火星直伤配合黑洞/落穴/地割清场与红色药剂续航。",
                                        recipe(76103675, 3, 38199696, 3, 53129443, 3, 4206964, 3, 66788016, 3, 46986414, 2, 6368038, 2),
                                        null, "BURN_WALL", 240, 110, 70),
                                new AiOpponent.EraDeckProfile("1999-03-27", "【火刑＆火球 护封剑壁垒】", "1999.03 烧血成型",
                                        "火刑(600)、火球(500)、雷鸣(300) 搭配圣精灵/水魔道士 2000 守备与光之护封剑拖延斩杀。",
                                        recipe(73134081, 3, 46130346, 3, 56260110, 3, 72302403, 3, 15025844, 3, 85639257, 3, 80770678, 3, 11868825, 2, 4206964, 3, 53129443, 3),
                                        null, "BURN_WALL", 280, 125, 80),
                                new AiOpponent.EraDeckProfile("1999-05-25", "【昼夜大火灾 铁壁烧血】", "1999.05 800直伤",
                                        "加入昼夜的大火灾(800)与岩石巨兵/食人虫/强欲之壶，一边守备一边高速削血。",
                                        recipe(19523799, 3, 73134081, 3, 46130346, 3, 55144522, 3, 72302403, 3, 13039848, 3, 15025844, 3, 54652250, 3, 31560081, 3, 4206964, 3),
                                        null, "BURN_WALL", 320, 140, 90),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【加农炮兵·遗言状 射出烧血】", "1999.11~12 射出流",
                                        "yugioh-history 第19回记载的「加农炮兵＋三眼怪/黑森林魔女＋遗言状(12.16)」连锁射出直伤！",
                                        recipe(11384280, 3, 26202165, 3, 78010363, 3, 85602018, 3, 31560081, 3, 55144522, 3, 79571449, 3, 19523799, 3, 73134081, 3, 72302403, 3, 12607053, 3, 83764718, 3, 53129443, 1),
                                        null, "BURN_WALL", 520, 220, 130)
                        )
                ),

                // 6. Seto Kaiba (1999-03-18): No-Tribute Blue-Eyes Dark Age -> Expert Transition -> La Jinn/Mechanicalchaser -> EX Kaiba
                new AiOpponent(
                        "ai_starter_kaiba",
                        "社长·海马濑人",
                        "Lv.3 · 青眼与高打点压制",
                        "1999-03-18",
                        3, 480, 205, 120,
                        "严格还原海马濑人原著与 EX 海马卡组：免祭品 3 青眼雷击 → 机械巨兵/魔加农过渡 → 灯之魔精/机械猎手/粗暴帝王 → EX 龙之支配者·呼龙笛青眼＆死毒！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-03-18", "【生け贄無し最上級 · 青眼雷击】", "1999.03~04 暗黑期顶配",
                                        "以海马专属 3 青眼白龙、独眼巨人、石像怪搭配 Starter Box 龙/恶魔战力与 3 雷击＋3 黑洞，绝不混用暗游戏王牌！",
                                        recipe(89631139, 3, 76184692, 3, 15303296, 3, 84686841, 3, 2863439, 3, 85639257, 3, 2957055, 3, 76704943, 2, 22910685, 2, 12580477, 3, 53129443, 3, 66788016, 3, 4206964, 3, 83764718, 3, 4614116, 2, 83887306, 2),
                                        null, "NO_TRIBUTE_BEAT", 480, 205, 120),
                                new AiOpponent.EraDeckProfile("1999-05-05", "【专家规则 · 青眼与高打点过渡】", "1999.05 祭品制适应",
                                        "5月5日专家规则开启后缩减青眼至 2 张，以机械巨兵、魔加农、狂战士、破坏的石人、独眼巨人、石像怪与『守备』封禁压制！",
                                        recipe(89631139, 2, 72299832, 2, 98795934, 2, 47060154, 3, 73481154, 3, 76184692, 3, 15303296, 3, 85639257, 3, 80770678, 3, 1784619, 3, 12580477, 3, 83764718, 3, 63102017, 2, 66788016, 3, 53129443, 2, 4206964, 3),
                                        null, "LOWER_BEAT", 480, 205, 120),
                                new AiOpponent.EraDeckProfile("1999-08-26", "【海马高打点 · 魔精/猎手/粗暴帝王】", "1999.08 巨蛋高速强攻",
                                        "灯之魔精(1800)、机械猎手(1850)、粗暴帝王(1800)、邪恶蠕虫兽、狂战士与『守备』封禁全面提速，天使的施舍弃青眼＋死苏速出 3000 打点！",
                                        recipe(89631139, 2, 26378150, 2, 97590747, 3, 7359741, 3, 6285791, 2, 47060154, 3, 73481154, 2, 69140098, 3, 55144522, 3, 79571449, 3, 83764718, 3, 63102017, 2, 18144506, 2, 66788016, 3, 72302403, 1, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF", 600, 250, 150),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 海马社长 · 呼龙笛青眼＆死毒】", "1999.12 EX海马盒",
                                        "集结 EX 海马全员：青眼白龙、审判者、灯之魔精、机械猎手、牛头人、强化石像怪、龙之支配者＋呼龙笛与 1999 原版死之卡组破坏病毒！",
                                        recipe(89631139, 2, 30113682, 2, 97590747, 3, 7359741, 3, 5053103, 3, 24611934, 2, 17985575, 2, 43973174, 2, 26202165, 3, 28933734, 2, 15303296, 1, 57728570, 2, 55144522, 3, 79571449, 3, 4031928, 2, 83764718, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        recipe(23995346, 1), "GOOD_STUFF", 660, 275, 165)
                        )
                ),

                // 7. Equip Beat Pioneer (1999-05-25): yugioh-history #5 First Concept Deck【装備ビート（属性軸）】
                new AiOpponent(
                        "ai_equip_beat",
                        "装备重击流先驱",
                        "Lv.3 · OCG首个概念卡组【装备Beat】",
                        "1999-05-25",
                        3, 440, 190, 110,
                        "还原 yugioh-history 第5回「OCG首个概念卡组【装備ビート（属性軸）】」，以属性装备魔法免祭品制造上级打点！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-05-25", "【光·地双属性 装备Beat】", "1999.05 Booster 2首创",
                                        "神圣人偶(1600)+精灵之光→2000，吸血跳蚤/荒野盗龙(1500)+觉醒→1900，无祭品压制上级怪兽。",
                                        recipe(91939608, 3, 41762634, 3, 1784619, 3, 84990171, 3, 91152256, 3, 15025844, 2, 80770678, 2, 39897277, 3, 98374133, 3, 12580477, 3, 53129443, 2, 66788016, 3, 83764718, 3, 4206964, 3),
                                        null, "EQUIP_LIGHT_EARTH", 440, 190, 110),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【暗·光强化 恶魔装备Beat】", "1999.07 Vol.4升级",
                                        "引入恶魔召唤＋暗之破神剑(2900打点)与圣魔术师回收强欲之壶/装备卡，火力倍增。",
                                        recipe(70781052, 3, 91939608, 3, 41762634, 3, 1784619, 3, 31560081, 3, 37120512, 3, 39897277, 3, 98374133, 2, 55144522, 3, 83764718, 3, 12580477, 3, 4206964, 3),
                                        null, "EQUIP_LIGHT_EARTH", 520, 220, 130),
                                new AiOpponent.EraDeckProfile("1999-12-09", "【金属化·高攻下级 装备Beat】", "1999.12 PS特典强化",
                                        "加入双生精灵(1900)、机械猎手(1850)、恶魔兽灵与 PS 特典「金属化·魔法反射装甲」，全员打点突破天际。",
                                        recipe(69140098, 3, 7359741, 3, 97590747, 3, 70781052, 2, 24311372, 1, 68540058, 3, 37120512, 3, 98374133, 2, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 18144506, 2, 12580477, 1, 53129443, 1),
                                        null, "EQUIP_LIGHT_EARTH", 620, 260, 155)
                        )
                ),

                // 8. Flip Control Master (1999-05-27): yugioh-history #6/#10/#15【グッドスタッフ（リバース軸）】&【メタモルポット】
                new AiOpponent(
                        "ai_flip_control",
                        "反转控制大师",
                        "Lv.3 · 反转轴 Good Stuff",
                        "1999-05-27",
                        3, 420, 180, 105,
                        "还原 yugioh-history 第6/10/15回「反转轴 Good Stuff」：从 Vol.3 食人虫/哈尼哈尼进化至圣魔术师、变形壶与企鹅士兵。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-05-27", "【Vol.3 反转轴 Good Stuff】", "1999.05 反转时代开启",
                                        "以食人虫(破坏)、哈尼哈尼(弹手)、骸骨天使(抽1) 搭配光之护封剑与岩石巨兵进行心理博弈。",
                                        recipe(54652250, 3, 7089711, 3, 60694662, 3, 9076207, 2, 13039848, 3, 91939608, 3, 28279543, 2, 55144522, 3, 72302403, 3, 83764718, 3, 12580477, 3, 53129443, 2, 4206964, 3),
                                        null, "FLIP_CONTROL", 420, 180, 105),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【圣魔术师＆恶魔召唤 赚卡反转】", "1999.07 圣魔术师降临",
                                        "圣魔术师满编 3 张循环回收强欲之壶与解场魔法，配合恶魔召唤稳步扩大卡差。",
                                        recipe(31560081, 3, 54652250, 3, 7089711, 2, 60694662, 2, 70781052, 3, 13039848, 3, 91939608, 3, 55144522, 3, 83764718, 3, 72302403, 3, 12580477, 3, 53129443, 2, 4206964, 3),
                                        null, "FLIP_CONTROL", 520, 220, 130),
                                new AiOpponent.EraDeckProfile("1999-10-17", "【变形壶·针虫·通告 反制轴】", "1999.10 Booster 5",
                                        "yugioh-history 第15回主角「变形壶」与「针虫」登场，手牌清空重抓 5 张＋王宫的通告封锁陷阱。",
                                        recipe(33508719, 3, 81843628, 3, 31560081, 3, 54652250, 3, 70781052, 3, 69140098, 3, 51452091, 2, 55144522, 3, 79571449, 3, 4031928, 3, 79759861, 2, 83764718, 3, 72302403, 2, 12580477, 1, 53129443, 1),
                                        null, "FLIP_CONTROL", 580, 245, 145),
                                new AiOpponent.EraDeckProfile("1999-12-01", "【企鹅士兵·幻影壁 终极弹手控】", "1999.12 弹手控制完全体",
                                        "企鹅士兵(弹2)＋幻影壁(被攻弹手)＋变形壶(针对艾克佐迪亚)＋三眼怪/和睦的使者。",
                                        recipe(93920745, 3, 13945283, 3, 33508719, 3, 31560081, 3, 54652250, 2, 26202165, 3, 70781052, 2, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 72302403, 2, 12607053, 3, 12580477, 1, 53129443, 1),
                                        null, "FLIP_CONTROL", 620, 260, 155)
                        )
                ),

                // 9. Crush Card Virus Dark Control (1999-07-08): yugioh-history 8~11月【死のデッキ破壊ウイルス】
                new AiOpponent(
                        "ai_virus_control",
                        "死毒暗控猎手",
                        "Lv.3 · 【死之卡组破坏病毒】专精",
                        "1999-07-08",
                        3, 360, 155, 95,
                        "还原 yugioh-history 环境年表 8~11 月【死のデッキ破壊ウイルス】：以暗之艺术家/暗之假面/三眼怪为媒体引爆 1999 原版 3 回合病毒！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-07-08", "【DM2 初代死毒暗属性】", "1999.07 病毒降临",
                                        "围绕 DM2 特典「死之卡组破坏病毒」(1999原版摧毁后续3回合抽卡) 与暗之艺术家(600/1400)、骷髅寺院(900/1300)、恶魔蜗牛(700/1300) 等低攻暗属性媒体构筑。",
                                        recipe(57728570, 3, 72520073, 3, 732302, 3, 98075147, 3, 47060154, 3, 36304921, 3, 28279543, 2, 74677422, 1, 41356845, 2, 18144506, 2, 55144522, 3, 83764718, 3, 72302403, 2, 12580477, 2, 53129443, 2, 4206964, 3),
                                        null, "DARK_VIRUS", 360, 155, 95),
                                new AiOpponent.EraDeckProfile("1999-07-22", "【暗之假面 · 死毒循环控】", "1999.07~08 暗假面循环",
                                        "yugioh-history 第10回实录：暗之假面(暗/ATK 900) 既是死毒祭品又能反转回收死毒，搭暗之艺术家/骷髅寺院稳定触发并配恶魔召唤收割！",
                                        recipe(57728570, 3, 28933734, 3, 72520073, 3, 732302, 3, 70781052, 3, 47060154, 3, 31560081, 3, 54652250, 2, 55144522, 3, 83764718, 3, 18144506, 2, 72302403, 3, 41356845, 2, 12580477, 1, 53129443, 1, 4206964, 2),
                                        null, "DARK_VIRUS", 480, 205, 120),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【三眼怪/魔女 · 死毒完全体】", "1999.11 Vol.6终极暗控",
                                        "三眼怪(暗/ATK 1000) 献祭发动死毒后还能触发检索！配合机械猎手、天使的施舍、神之宣告彻底封锁对手。",
                                        recipe(57728570, 3, 26202165, 3, 78010363, 3, 28933734, 3, 7359741, 3, 70781052, 3, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 18144506, 2, 41420027, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "DARK_VIRUS", 600, 250, 150)
                        )
                ),

                // 10. Tokyo Dome Finalist (1999-08-26): yugioh-history 8~12月【グッドスタッフ（竞技主流）】
                new AiOpponent(
                        "ai_tokyo_dome",
                        "东京巨蛋大会决赛选手",
                        "Lv.4 · 竞技主流【Good Stuff】",
                        "1999-08-26",
                        4, 620, 260, 155,
                        "代表 1999 下半年最稳定的竞技主流 Good Stuff，随 Vol.5/Vol.6/EX 持续更新至「抗艾克佐迪亚变形壶构筑」。",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-08-26", "【东京巨蛋 首次限表 Good Stuff】", "1999.08 巨蛋竞技顶配",
                                        "严守 1999.08.26 首份限制表（黑洞/落穴/雷击各限1），以双生精灵(1900)/机械猎手(1850)/恶魔召唤＋施舍/强欲主宰场面。",
                                        recipe(69140098, 3, 7359741, 3, 97590747, 3, 70781052, 3, 31560081, 3, 54652250, 3, 55144522, 3, 79571449, 3, 83764718, 3, 18144506, 3, 72302403, 2, 66788016, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF", 620, 260, 155),
                                new AiOpponent.EraDeckProfile("1999-09-23", "【Vol.5 心变·三魔神 Good Stuff】", "1999.09 Vol.5强化",
                                        "满编心变(夺取献祭恶魔召唤)与对死者的供奉，外挂 1999 原版三魔神(伤害归0) 强化场面压制。",
                                        recipe(69140098, 3, 7359741, 3, 97590747, 3, 70781052, 3, 25955164, 1, 98434877, 1, 31560081, 3, 54652250, 2, 55144522, 3, 79571449, 3, 4031928, 3, 79759861, 2, 83764718, 3, 18144506, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF", 640, 270, 160),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【Vol.6 双检索·神宣 Good Stuff】", "1999.11 反击时代",
                                        "加入 Vol.6 三眼怪、黑森林魔女与反击陷阱「神之宣告」「盗贼的七道具」，兼顾续航与反制。",
                                        recipe(69140098, 3, 7359741, 3, 70781052, 3, 26202165, 3, 78010363, 3, 31560081, 3, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 18144506, 2, 41420027, 3, 3819470, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF", 660, 275, 165),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【变形壶对策 · 99年末 Good Stuff】", "1999.12 抗暗黑期",
                                        "yugioh-history 12月年表实录：为对抗艾克佐迪亚满编变形壶与神之宣告，搭配幻影壁、和睦的使者与强攻主力。",
                                        recipe(69140098, 3, 7359741, 3, 70781052, 3, 13945283, 3, 33508719, 3, 26202165, 3, 78010363, 2, 31560081, 2, 55144522, 3, 79571449, 3, 4031928, 3, 83764718, 3, 41420027, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "GOOD_STUFF", 680, 285, 170)
                        )
                ),

                // 11. Exodia Master (1999-08-26): yugioh-history【エクゾディア】（第1期）exact historical recipes!
                new AiOpponent(
                        "ai_exodia_master",
                        "被封印的艾克佐迪亚使",
                        "Lv.4 · 【第1期 艾克佐迪亚】暗黑期霸主",
                        "1999-08-26",
                        4, 520, 220, 130,
                        "严格复刻 yugioh-history《エクゾディア（第1期）》三阶段构筑：9月混轴 → 11.18勘误前三眼魔女 → 12.16遗言状暗黑期完全体！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-08-26", "【PP1 初代混轴艾克佐迪亚】", "1999.08~10 黎明期",
                                        "PP1 集齐 5 部件！尚未出现三眼怪/魔女，依靠强欲之壶、天使的施舍、骸骨天使与护封剑滤抽。",
                                        recipe(33396948, 2, 7902349, 2, 70903634, 2, 44519536, 2, 8124921, 2, 60694662, 3, 31560081, 3, 13039848, 3, 15025844, 2, 55144522, 3, 79571449, 3, 72302403, 3, 83764718, 3, 54652250, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "EXODIA_DRAW", 520, 220, 130),
                                new AiOpponent.EraDeckProfile("1999-11-18", "【Vol.6 勘误前三眼/魔女 艾克佐迪亚】", "1999.11.18 顶峰登顶",
                                        "复刻 yugioh-history 1999.11.18 原文配方：天使的施舍弃掉原版三眼怪/黑森林魔女直接检索部件，横扫整个环境！",
                                        recipe(26202165, 3, 78010363, 3, 31560081, 3, 60694662, 2, 33396948, 2, 7902349, 2, 70903634, 2, 44519536, 2, 8124921, 2, 55144522, 3, 83764718, 3, 79571449, 3, 72302403, 3, 4031928, 2, 79759861, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "EXODIA_DRAW", 720, 300, 180),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 遗言状循环 · 世纪末暗黑期完全体】", "1999.12.16 暗黑期极点",
                                        "复刻 yugioh-history 1999.12.16 原文配方：3 三眼＋3 魔女＋3 遗言状＋加农炮兵＋变形壶＋3 神宣，1999 最强禁断构筑！",
                                        recipe(26202165, 3, 78010363, 3, 31560081, 3, 33396948, 2, 7902349, 2, 70903634, 2, 44519536, 2, 8124921, 2, 11384280, 1, 33508719, 1, 55144522, 3, 83764718, 3, 79759861, 3, 79571449, 3, 85602018, 3, 53129443, 1, 41420027, 3),
                                        null, "EXODIA_DRAW", 760, 320, 190)
                        )
                ),

                // 12. Deck Destruction & Anti-Exodia Morphing Jar (1999-10-17): yugioh-history #15【デッキ破壊】& 12月【メタモルポット】
                new AiOpponent(
                        "ai_mill_morphing",
                        "变形壶·卡组破坏使",
                        "Lv.4 · 【第1期 卡组破坏＆抗艾克佐迪亚变形壶】",
                        "1999-10-17",
                        4, 580, 245, 145,
                        "还原 yugioh-history 第15回【デッキ破壊】(10月)与12月年表【メタモルポット】：以针虫/变形壶高速削库丢手，并用高守壁垒与回血稳赢第1期抽干比血规则！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-10-17", "【Booster 5 针虫·变形壶 卡组破坏轴】", "1999.10 卡组破坏初现",
                                        "yugioh-history 第15回记载的 Booster 5 针虫+变形壶削库构筑，配合圣精灵/岩石巨兵/护封剑/天使的鲜血维持血量优势。",
                                        recipe(81843628, 3, 33508719, 3, 31560081, 3, 28933734, 2, 13039848, 3, 15025844, 3, 54652250, 3, 55144522, 3, 79571449, 3, 72302403, 3, 47852924, 3, 51452091, 2, 83764718, 3, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "MILL_MORPHING", 580, 245, 145),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 抗艾克佐迪亚 · 变形壶特化】", "1999.12 变形壶对策轴",
                                        "还原 yugioh-history 1999年12月年表【メタモルポット】：满编变形壶、针虫、幻影壁、三眼怪、和睦的使者、治疗之神与神之宣告，专克手牌积攒流派！",
                                        recipe(33508719, 3, 81843628, 3, 13945283, 3, 26202165, 3, 31560081, 3, 28933734, 2, 93920745, 2, 55144522, 3, 79571449, 3, 72302403, 3, 84257639, 2, 83764718, 2, 12607053, 3, 41420027, 2, 12580477, 1, 53129443, 1, 4206964, 1),
                                        null, "MILL_MORPHING", 640, 270, 160)
                        )
                ),

                // 13. Fusion & Cyber-Stein Master (1999-12-01): yugioh-history #17【融合召喚（第1期）】+ Cyber-Stein
                new AiOpponent(
                        "ai_fusion_stein",
                        "融合＆恶魔科学怪人大师",
                        "Lv.4 · 【第1期 融合召唤＆科学怪人】",
                        "1999-12-01",
                        4, 660, 275, 165,
                        "还原 yugioh-history 第17回「融合召唤（第1期）」与 Booster 6 恶魔科学怪人：融合代用素材＋暗黑魔龙(3200)＋青眼究极龙(4500)！",
                        Arrays.asList(
                                new AiOpponent.EraDeckProfile("1999-12-01", "【Booster 6 正规融合＆科学怪人】", "1999.12.01 融合体系诞生",
                                        "三眼怪/黑森林魔女＋心眼之女神/沼地魔兽王/破坏神瓦沙克＋融合贤者＋恶魔科学怪人，直出暗黑魔龙与青眼究极龙！",
                                        recipe(26202165, 3, 78010363, 3, 53493204, 3, 99426834, 2, 50259460, 2, 69015963, 3, 31560081, 3, 70781052, 2, 84133008, 1, 55144522, 3, 4031928, 3, 83764718, 3, 79571449, 3, 24094653, 3, 26902560, 2, 12580477, 1),
                                        recipe(23995346, 3, 11901678, 3, 56907389, 3), "FUSION_COMBO", 660, 275, 165),
                                new AiOpponent.EraDeckProfile("1999-12-16", "【EX 世纪末 遗言状·科学怪人奇袭】", "1999.12.16 终极奇袭",
                                        "加入遗言状(从卡组直接特召恶魔科学怪人!)、和睦的使者与神之宣告，极速展开青眼究极龙(4500)斩杀！",
                                        recipe(26202165, 3, 78010363, 3, 69015963, 3, 53493204, 2, 99426834, 2, 70781052, 2, 13945283, 2, 31560081, 2, 55144522, 3, 79571449, 3, 85602018, 3, 4031928, 3, 83764718, 3, 24094653, 2, 12607053, 2, 41420027, 2),
                                        recipe(23995346, 3, 11901678, 3, 56907389, 3), "FUSION_COMBO", 720, 300, 180)
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

        final String charId = CanonCharacterCardRegistry.resolveCharacterId(opponent.id);
        final Set<Integer> charCanonPool = CanonCharacterCardRegistry.getCharacterCanonPool(charId, catalog);

        // 1b) Structural safeguard: any AI deck containing Crush Card Virus (57728570) MUST have enough
        //     DARK monsters with ATK <= 1000 (excluding Exodia pieces) to actually activate it!
        if (countOf(main, 57728570) > 0 && main.size() < 40) {
            int darkVirusTributes = 0;
            for (Integer id : main) {
                RpgCard c = id != null ? catalog.findCard(id) : null;
                if (isDarkVirusTributeMonster(c)) darkVirusTributes++;
            }
            if (darkVirusTributes < 6) {
                List<RpgCard> virusPool = new ArrayList<>();
                for (RpgCard c : released) {
                    if (isDarkVirusTributeMonster(c)
                            && banlist.getMaxCopies(c.id) > 0
                            && !CanonCharacterCardRegistry.isForbiddenRivalAce(charId, c.id)) {
                        virusPool.add(c);
                    }
                }
                Collections.sort(virusPool, new Comparator<RpgCard>() {
                    @Override
                    public int compare(RpgCard a, RpgCard b) {
                        int sa = scoreDarkVirusTribute(a) + (charCanonPool.contains(a.id) ? 600 : 0);
                        int sb = scoreDarkVirusTribute(b) + (charCanonPool.contains(b.id) ? 600 : 0);
                        return Integer.compare(sb, sa);
                    }
                });
                for (RpgCard c : virusPool) {
                    int already = countOf(main, c.id);
                    int legalMax = banlist.getMaxCopies(c.id);
                    while (already < legalMax && darkVirusTributes < 6 && main.size() < 40) {
                        main.add(c.id);
                        already++;
                        darkVirusTributes++;
                    }
                    if (darkVirusTributes >= 6 || main.size() >= 40) break;
                }
            }
        }

        // 2) Fill remaining slots up to 40 using character-fidelity + core-synergy + archetype scoring
        if (main.size() < 40) {
            final List<Integer> coreSnapshot = new ArrayList<>(main);
            boolean isNonTopTier = "NOVICE_NR".equals(style)
                    || "CASUAL_MID".equals(style)
                    || "MID_TIER_THEME".equals(style);
            List<RpgCard> fillPool = new ArrayList<>();
            for (RpgCard c : released) {
                if (banlist.getMaxCopies(c.id) <= 0) continue;
                if (CanonCharacterCardRegistry.isForbiddenRivalAce(charId, c.id)) continue;
                if (isUnsafeFillerCard(c, main, extra, catalog, style)) continue;
                if (isNonTopTier && isTopTierStaple(c.id)) continue;
                if (("NOVICE_NR".equals(style) || "CASUAL_MID".equals(style))
                        && c.getRarityEnum().ordinal() >= Rarity.SR.ordinal()) continue;
                if ("MID_TIER_THEME".equals(style) && c.getRarityEnum().ordinal() >= Rarity.UR.ordinal()) continue;
                fillPool.add(c);
            }
            if (fillPool.isEmpty()) {
                for (RpgCard c : released) {
                    if (!CanonCharacterCardRegistry.isForbiddenRivalAce(charId, c.id)
                            && !isUnsafeFillerCard(c, main, extra, catalog, style)
                            && banlist.getMaxCopies(c.id) > 0) {
                        fillPool.add(c);
                    }
                }
            }

            Collections.sort(fillPool, new Comparator<RpgCard>() {
                @Override
                public int compare(RpgCard a, RpgCard b) {
                    int sa = scoreForStyle(a, style, ruleSet, charId, charCanonPool, coreSnapshot, extra, catalog);
                    int sb = scoreForStyle(b, style, ruleSet, charId, charCanonPool, coreSnapshot, extra, catalog);
                    if (sb != sa) return Integer.compare(sb, sa);
                    return Integer.compare(a.id, b.id);
                }
            });

            if (random != null && fillPool.size() > 6) {
                if (charId != null) {
                    // For canon-character AIs, only shuffle within the leading character-authentic/top band
                    // so character-canon cards never get displaced by unrelated generic cards.
                    int topScore = scoreForStyle(fillPool.get(0), style, ruleSet, charId, charCanonPool, coreSnapshot, extra, catalog);
                    int bandEnd = 1;
                    while (bandEnd < Math.min(8, fillPool.size())) {
                        int sc = scoreForStyle(fillPool.get(bandEnd), style, ruleSet, charId, charCanonPool, coreSnapshot, extra, catalog);
                        if (topScore - sc > 450) break;
                        bandEnd++;
                    }
                    if (bandEnd > 2) {
                        Collections.shuffle(fillPool.subList(0, bandEnd), random);
                    }
                } else {
                    int window = Math.min(12, fillPool.size());
                    Collections.shuffle(fillPool.subList(0, window), random);
                }
            }

            int prefCopies = ("NOVICE_NR".equals(style) || "CASUAL_MID".equals(style)) ? 2 : 3;
            for (RpgCard c : fillPool) {
                if (isUnsafeFillerCard(c, main, extra, catalog, style)) continue;
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
                    if (isUnsafeFillerCard(c, main, extra, catalog, style)) continue;
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

    /**
     * Returns true if {@code c} is a Special-Summon-Only ("Nomi") monster, Ritual monster,
     * or Extra Deck monster that CANNOT be Normal Summoned/Set as generic deck filler
     * (e.g., Perfectly Ultimate Great Moth, Great Moth, Harpie Lady Sisters, Gate Guardian,
     * Metalzoa, Red-Eyes Black Metal Dragon, Larvae Moth, or future Series 2+ Nomi monsters).
     */
    public static boolean isSpecialSummonOnlyMonster(RpgCard c) {
        if (c == null) return true;
        if (c.isExtraDeckCard() || "ritual".equalsIgnoreCase(c.frameType)) return true;
        int id = c.id;
        if (id == 12206212    // 鹰身女郎三姐妹
                || id == 14141448 // 大飞蛾
                || id == 48579379 // 究极完全态大飞蛾
                || id == 87756343 // 飞蛾幼虫
                || id == 25833572 // 门之守护神
                || id == 50705071 // 钢铁恶魔兽灵
                || id == 64335804 // 真红眼黑钢龙
        ) {
            return true;
        }
        if (c.isMonster() && c.descCn != null) {
            return c.descCn.contains("不能通常召唤")
                    || c.descCn.contains("作祭品来特殊召唤")
                    || c.descCn.contains("解放的场合可以特殊召唤")
                    || c.descCn.contains("特殊召唤上场");
        }
        return false;
    }

    /** Returns true if {@code c} is a Ritual Spell (requires a specific Ritual Monster in deck). */
    public static boolean isRitualSpell(RpgCard c) {
        if (c == null || c.isMonster()) return false;
        int id = c.id;
        if (id == 31066283    // 骷髅骑手的复活
                || id == 54539105 // 狮子的仪式
                || id == 55761792 // 混沌的仪式
                || id == 81756897 // 杰拉的仪式
                || id == 81933259 // 恶魔镜的仪式
        ) {
            return true;
        }
        if (c.descCn != null) {
            return c.descCn.contains("降临必需")
                    || c.descCn.contains("降临必要")
                    || c.descCn.contains("仪式召唤");
        }
        return false;
    }

    /** Returns true if {@code c} is a Field Spell (e.g. Yami, Forest, Umi, Wasteland, Mountain, Sogen). */
    public static boolean isFieldSpell(RpgCard c) {
        if (c == null || c.isMonster()) return false;
        int id = c.id;
        if (id == 59197169    // 暗
                || id == 23424603 // 荒野
                || id == 50913601 // 山
                || id == 87430998 // 森
                || id == 22702055 // 海
                || id == 86318356 // 草原
        ) {
            return true;
        }
        return c.race != null && "Field".equalsIgnoreCase(c.race);
    }

    /**
     * Shared rationality guard used by both {@link AiDeckGenerator} and {@link CanonDuelDeckBuilder}
     * to guarantee that filler slots NEVER contain unplayable Nomi monsters, unmatched Ritual/Fusion
     * cards, named-partner bricks, Exodia leaks, unsupported Crush Card Virus, dead Equip Spells,
     * or off-theme Equip/Field/self-discard cards in Exodia / Burn / Mill strategies.
     */
    public static boolean isUnsafeFillerCard(RpgCard c,
                                             List<Integer> curMain,
                                             List<Integer> curExtra,
                                             RpgCatalog catalog,
                                             String style) {
        if (c == null) return true;
        if (isSpecialSummonOnlyMonster(c)) return true;
        if (isRitualSpell(c)) return true;
        int id = c.id;

        // Never pad Exodia pieces into non-Exodia decks
        if (isExodiaPieceId(id) && !"EXODIA_DRAW".equals(style)) return true;

        // Strategy-coherence guard: Exodia decks must NEVER pad off-theme Equip Spells, Field Spells,
        // or hand-wiping self-discard cards (Card Destruction / Morphing Jar / Tribute to the Doomed)
        if ("EXODIA_DRAW".equals(style)) {
            if (isFieldSpell(c)) return true;
            if (RpgAiCardKnowledge.EQUIP_SPELLS.contains(id) || getEquipTargetRequirement(id) != null) {
                return true;
            }
            if (id == 72892473    // 手牌抹杀 (discards Exodia pieces!)
                    || id == 33508719 // 变形壶 (discards Exodia pieces!)
                    || id == 79759861 // 对死者的供奉 (discards hand!)
            ) {
                return true;
            }
        }
        if (("BURN_WALL".equals(style) || "MILL_MORPHING".equals(style))
                && (RpgAiCardKnowledge.EQUIP_SPELLS.contains(id) || getEquipTargetRequirement(id) != null)) {
            return true;
        }

        // Never pad Fusion spells / fusion searchers / Cyber-Stein / fusion substitutes into non-Fusion decks
        boolean hasExtra = curExtra != null && !curExtra.isEmpty();
        boolean allowFusionComboFiller = "FUSION_COMBO".equals(style) && hasExtra;
        if (!hasExtra && (id == 24094653 // 融合
                || id == 26902560 // 融合贤者
        )) {
            return true;
        }
        if (!allowFusionComboFiller && (id == 69015963 // 恶魔科学怪人
                || id == 84133008 // 眼球怪
                || id == 53493204 // 心眼之女神
                || id == 30451366 // 幻想绵羊
                || id == 50259460 // 破坏神 瓦沙克
                || id == 99426834 // 沼地的魔兽王
        )) {
            return true;
        }

        // Named-partner & narrow combo dependencies
        if (id == 90219263 && countOf(curMain, 76812113) == 0) return true; // 万华镜 needs 鹰身女郎
        if (id == 63224564 && countOf(curMain, 76812113) == 0 && countOf(curMain, 12206212) == 0) return true; // 电子紧身衣
        if (id == 43973174 && countOf(curMain, 17985575) == 0) return true; // 呼龙笛 needs 龙之支配者
        if (id == 40240595 && countOf(curMain, 58192742) == 0) return true; // 进化之茧 needs 飞蛾宝宝
        if (id == 55763552) return true; // 壶魔人 (only counters Dragon Capture Jar)
        // Ultra-narrow 1-card anti-staple bricks (White Hole, Anti Raigeki, Call of the Grave/Darkness)
        if (id == 42364257 || id == 43487744 || id == 16970158 || id == 78637313) return true;

        // Never pad Crush Card Virus unless the deck already has >= 6 DARK <=1000 tributes
        if (id == 57728570) {
            int darkTributes = 0;
            if (curMain != null && catalog != null) {
                for (Integer mid : curMain) {
                    RpgCard mc = mid != null ? catalog.findCardOrCanon(mid) : null;
                    if (isDarkVirusTributeMonster(mc)) darkTributes++;
                }
            }
            if (darkTributes < 6) return true;
        }

        // Never pad Race/Attribute-restricted Equip Spells unless the deck already has >= 4 valid targets!
        String equipReq = getEquipTargetRequirement(id);
        if (equipReq != null) {
            int targets = countEquipCompatibleMonsters(equipReq, curMain, catalog);
            if (targets < 4) return true;
        }

        return false;
    }

    /**
     * Returns {@code "RACE:<race>"} or {@code "ATTR:<attr>"} for Race/Attribute-restricted Equip Spells,
     * or {@code null} for universal equips (e.g. Black Pendant) / non-equips.
     */
    public static String getEquipTargetRequirement(int cardId) {
        switch (cardId) {
            case 1435851:  return "RACE:Dragon";        // 龙之秘宝
            case 1557499:  return "RACE:Fairy";         // 银之弓矢
            case 4614116:  return "RACE:Fiend";         // 暗能量
            case 15052462: return "RACE:Zombie";        // 紫水晶
            case 25769732: return "RACE:Machine";       // 机械改造工厂
            case 36607978: return "RACE:Beast-Warrior"; // 魔性之月
            case 37820550: return "RACE:Thunder";       // 电击鞭
            case 39774685: return "RACE:Plant";         // 魔菌
            case 46009906: return "RACE:Beast";         // 猛兽之齿
            case 51267887: return "RACE:Dinosaur";      // 体温上升
            case 61854111: return "RACE:Warrior";       // 传说之剑
            case 77007920: return "RACE:Insect";        // 镭射炮机甲铠
            case 3492538:  return "RACE:Insect";        // 附火器的机甲铠
            case 77027445: return "RACE:Aqua";          // 波塞冬之力
            case 91595718: return "RACE:Spellcaster";   // 秘术之书
            case 98252586: return "RACE:Winged Beast";  // 随风翼
            case 2370081:  return "ATTR:WATER";         // 钢甲壳
            case 18937875: return "ATTR:FIRE";          // 灼热之枪
            case 32268901: return "ATTR:FIRE";          // 飞龙炎
            case 37120512: return "ATTR:DARK";          // 暗之破神剑
            case 39897277: return "ATTR:LIGHT";         // 精灵之光
            case 82878489: return "ATTR:LIGHT";         // 光之城
            case 55321970: return "ATTR:WIND";          // 突风之扇
            case 98374133: return "ATTR:EARTH";         // 觉醒
            default:       return null;
        }
    }

    public static int countEquipCompatibleMonsters(String equipReq, List<Integer> main, RpgCatalog catalog) {
        if (equipReq == null || main == null || catalog == null) return 0;
        int colon = equipReq.indexOf(':');
        if (colon <= 0) return 0;
        String kind = equipReq.substring(0, colon);
        String val = equipReq.substring(colon + 1);
        int count = 0;
        for (Integer mid : main) {
            if (mid == null) continue;
            RpgCard mc = catalog.findCardOrCanon(mid);
            if (mc == null || !mc.isMonster()) continue;
            if ("RACE".equals(kind) && val.equalsIgnoreCase(mc.race)) count++;
            else if ("ATTR".equals(kind) && val.equalsIgnoreCase(mc.attribute)) count++;
        }
        return count;
    }

    private static boolean isExodiaPieceId(int id) {
        return id == 33396948 || id == 70903634 || id == 7902349 || id == 8124921 || id == 44519536;
    }

    private static boolean isDarkVirusTributeMonster(RpgCard c) {
        return c != null && c.isMonster() && !isSpecialSummonOnlyMonster(c)
                && "DARK".equalsIgnoreCase(c.attribute)
                && c.atk <= 1000
                && !isExodiaPieceId(c.id);
    }

    private static int scoreDarkVirusTribute(RpgCard c) {
        int score = c.def + c.atk / 2;
        if (c.id == 26202165 || c.id == 28933734) score += 1200; // 三眼怪 / 暗之假面
        else if (c.descCn != null && c.descCn.contains("反转")) score += 500;
        if (c.level >= 5) score -= 800;
        return score;
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

    private static int scoreForStyle(RpgCard c, String style, OcgRuleSet rule,
                                     String charId, Set<Integer> charCanonPool,
                                     List<Integer> coreMain, List<Integer> coreExtra,
                                     RpgCatalog catalog) {
        int characterAndSynergyBonus = 0;
        if (charId != null && charCanonPool != null && charCanonPool.contains(c.id)) {
            characterAndSynergyBonus += 1400;
        }
        characterAndSynergyBonus += CanonCharacterCardRegistry.getCoreSynergyBonus(c, coreMain, coreExtra, catalog);

        int base = DeckValidator.scoreCard(c);
        if ("NO_TRIBUTE_BEAT".equals(style)) {
            int raw = c.isMonster() ? (c.atk * 2 + c.def / 2) : base;
            return raw + characterAndSynergyBonus;
        }
        if ("NOVICE_NR".equals(style)) {
            int pen = (rule != null && rule.tributeSummonEnabled && c.level >= 5) ? 900 : 0;
            int raw = c.isMonster() ? (Math.max(c.atk, c.def) - pen) : 1100;
            return raw + characterAndSynergyBonus;
        }
        if ("CASUAL_MID".equals(style)) {
            if (!c.isMonster()) return 950 + characterAndSynergyBonus;
            int stat = Math.max(c.atk, c.def);
            // Prefer mid-low 900~1450 monsters instead of top beaters
            int distPenalty = Math.abs(stat - 1250);
            int highLevelPen = (c.level >= 5) ? 600 : 0;
            return 1600 - distPenalty - highLevelPen + characterAndSynergyBonus;
        }
        if ("MID_TIER_THEME".equals(style)) {
            if (!c.isMonster()) return 1150 + characterAndSynergyBonus;
            int stat = Math.max(c.atk, c.def);
            int distPenalty = Math.abs(stat - 1500);
            int highLevelPen = (rule != null && rule.tributeSummonEnabled && c.level >= 7) ? 700 : 0;
            return 1900 - distPenalty - highLevelPen + characterAndSynergyBonus;
        }
        if ("EQUIP_LIGHT_EARTH".equals(style)) {
            int bonus = ("LIGHT".equalsIgnoreCase(c.attribute) || "EARTH".equalsIgnoreCase(c.attribute)) ? 600 : 0;
            int pen = (c.level >= 5) ? 500 : 0;
            return base + bonus - pen + characterAndSynergyBonus;
        }
        if ("DARK_VIRUS".equals(style)) {
            if (isExodiaPieceId(c.id)) return -5000;
            if (isDarkVirusTributeMonster(c)) {
                return 2200 + scoreDarkVirusTribute(c) + characterAndSynergyBonus;
            }
            int bonus = "DARK".equalsIgnoreCase(c.attribute) ? 450 : 0;
            int pen = (rule != null && rule.tributeSummonEnabled) ? (c.level >= 7 ? 700 : (c.level >= 5 ? 250 : 0)) : 0;
            return base + bonus - pen + characterAndSynergyBonus;
        }
        if ("BURN_WALL".equals(style)) {
            int bonus = c.isMonster() ? (c.def >= 1500 ? 600 : -200) : 500;
            return base + bonus + characterAndSynergyBonus;
        }
        if ("FLIP_CONTROL".equals(style)) {
            int bonus = (c.descCn != null && c.descCn.contains("反转")) ? 900 : (c.def >= 1800 ? 400 : 0);
            return base + bonus + characterAndSynergyBonus;
        }
        if ("MILL_MORPHING".equals(style)) {
            if (c.id == 81843628 || c.id == 33508719) return base + 1200 + characterAndSynergyBonus;
            int bonus = (c.descCn != null && c.descCn.contains("反转")) ? 800
                    : (c.isMonster() ? (c.def >= 1800 ? 600 : -200) : 450);
            return base + bonus + characterAndSynergyBonus;
        }
        if ("EXODIA_DRAW".equals(style)) {
            int bonus = c.isMonster() ? (c.def >= 1500 ? 500 : -300) : 600;
            return base + bonus + characterAndSynergyBonus;
        }
        // LOWER_BEAT / GOOD_STUFF / FUSION_COMBO
        int pen = (rule != null && rule.tributeSummonEnabled) ? (c.level >= 7 ? 700 : (c.level >= 5 ? 250 : 0)) : 0;
        return base - pen + characterAndSynergyBonus;
    }

    private static int countOf(List<Integer> list, int target) {
        int n = 0;
        for (Integer id : list) {
            if (id != null && id == target) n++;
        }
        return n;
    }
}
