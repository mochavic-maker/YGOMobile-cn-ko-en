package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.Rarity;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Generates timeline-legal and OCG-banlist-compliant 40-card AI decks for WindBot (Deck=Lucky).
 * Both card pool and card copy limits strictly follow the player's current timeline date.
 */
public final class AiDeckGenerator {
    private AiDeckGenerator() {}

    public static class GeneratedAiDeck {
        public final List<Integer> main;
        public final List<Integer> extra;
        public final OcgBanlist banlist;

        public GeneratedAiDeck(List<Integer> main, List<Integer> extra, OcgBanlist banlist) {
            this.main = main;
            this.extra = extra;
            this.banlist = banlist;
        }
    }

    /** Built-in 1999 full-year OCG RPG opponents (1999-02-04 ~ 1999-12-31). */
    public static List<AiOpponent> getDemoOpponents() {
        return Arrays.asList(
                new AiOpponent(
                        "ai_street_novice",
                        "街边新人决斗者",
                        "Lv.1 · 仅使用 N/R 级凡骨与基础装备",
                        "1999-02-04",
                        1, 150, 80, 50,
                        "只使用当前时间轴已发售的 N/R 卡片，适合开局赚取 150 代币买包。"
                ),
                new AiOpponent(
                        "ai_vol1_yugi",
                        "初代暗游戏（Vol.1 构筑）",
                        "Lv.2 · 黑魔术师 & 盖亚 + 经典解场",
                        "1999-02-04",
                        2, 240, 110, 70,
                        "严格遵守当前时间轴的 OCG 卡池、禁限表与历史规则。"
                ),
                new AiOpponent(
                        "ai_starter_kaiba",
                        "社长·海马濑人（STARTER BOX）",
                        "Lv.3 · 青眼白龙 & 雷击 / 场地强攻",
                        "1999-03-18",
                        3, 350, 150, 90,
                        "STARTER BOX 发售解锁：在 1999.05 专家规则前可免祭品拍青眼白龙！"
                ),
                new AiOpponent(
                        "ai_vol3_expert",
                        "决斗王国精英（专家规则 · Vol.3）",
                        "Lv.4 · 真红眼 / 强欲之壶 / 反转效果",
                        "1999-05-27",
                        3, 450, 180, 110,
                        "专家规则与 Vol.3 效果怪兽上线后登场，擅用上级召唤与滤抽解场。"
                ),
                new AiOpponent(
                        "ai_tokyo_dome",
                        "东京巨蛋大会决赛选手（1999.08 限表）",
                        "Lv.5 · 恶魔召唤 / 天使的施舍 / 羽毛扫",
                        "1999-08-26",
                        3, 600, 240, 140,
                        "严格遵守 1999.08.26 史上首次限制表（黑洞/落穴/雷击各限 1）。"
                ),
                new AiOpponent(
                        "ai_1999_champion",
                        "1999 世纪末决斗王（EX & PP2 全卡池）",
                        "Lv.6 · 1999 全年 Good Stuff 终极构筑",
                        "1999-12-16",
                        3, 800, 320, 180,
                        "EX 双卡组盒发售解锁，集结 1999 年全 OCG 强卡，高额代币奖励。"
                )
        );
    }

    public static GeneratedAiDeck generateDeck(RpgCatalog catalog, AiOpponent opponent,
                                               String currentDate, Random random) {
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, currentDate);
        List<RpgCard> released = TimelineEngine.getReleasedCards(catalog, currentDate);

        // Filter candidates by AI difficulty tier (all still strictly <= currentDate)
        List<RpgCard> mainPool = new ArrayList<>();
        List<RpgCard> extraPool = new ArrayList<>();
        for (RpgCard c : released) {
            int limit = banlist.getMaxCopies(c.id);
            if (limit <= 0) continue;
            if (opponent.difficulty == 1 && c.getRarityEnum().ordinal() >= Rarity.SR.ordinal()) {
                // Lv.1 novice avoids SR/UR staples
                continue;
            }
            if (c.isExtraDeckCard()) {
                extraPool.add(c);
            } else {
                mainPool.add(c);
            }
        }
        if (mainPool.isEmpty()) {
            for (RpgCard c : released) {
                if (!c.isExtraDeckCard() && banlist.getMaxCopies(c.id) > 0) {
                    mainPool.add(c);
                }
            }
        }

        // Rank cards based on difficulty
        Collections.sort(mainPool, new Comparator<RpgCard>() {
            @Override
            public int compare(RpgCard a, RpgCard b) {
                if (opponent.difficulty == 1) {
                    // Beginner favors modest ATK monsters
                    return Integer.compare(DeckValidator.scoreCard(a), DeckValidator.scoreCard(b));
                }
                return Integer.compare(DeckValidator.scoreCard(b), DeckValidator.scoreCard(a));
            }
        });

        // Slight controlled randomness among top cards so each duel feels fresh
        if (random != null && mainPool.size() > 6) {
            int shuffleWindow = opponent.difficulty == 3 ? Math.min(18, mainPool.size()) : Math.min(26, mainPool.size());
            Collections.shuffle(mainPool.subList(0, shuffleWindow), random);
        }

        List<Integer> main = new ArrayList<>(40);
        int maxCopiesPref = (opponent.difficulty == 1) ? 2 : 3;
        for (RpgCard c : mainPool) {
            int legalMax = Math.min(maxCopiesPref, banlist.getMaxCopies(c.id));
            for (int i = 0; i < legalMax && main.size() < 40; i++) {
                main.add(c.id);
            }
            if (main.size() >= 40) break;
        }
        // Fallback fill if difficulty=1 pool with maxCopiesPref=2 had < 40
        if (main.size() < 40) {
            for (RpgCard c : mainPool) {
                int already = countOf(main, c.id);
                int legalMax = banlist.getMaxCopies(c.id);
                while (already < legalMax && main.size() < 40) {
                    main.add(c.id);
                    already++;
                }
                if (main.size() >= 40) break;
            }
        }

        List<Integer> extra = new ArrayList<>();
        for (RpgCard c : extraPool) {
            int legalMax = banlist.getMaxCopies(c.id);
            for (int i = 0; i < legalMax && extra.size() < 15; i++) {
                extra.add(c.id);
            }
        }

        return new GeneratedAiDeck(main, extra, banlist);
    }

    private static int countOf(List<Integer> list, int target) {
        int n = 0;
        for (Integer id : list) {
            if (id != null && id == target) n++;
        }
        return n;
    }
}
