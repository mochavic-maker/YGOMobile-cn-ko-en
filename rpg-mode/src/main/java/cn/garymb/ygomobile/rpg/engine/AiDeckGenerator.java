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

    /** Built-in OCG Series 1 (Vol.1 & Vol.2) demo opponents. */
    public static List<AiOpponent> getDemoOpponents() {
        return Arrays.asList(
                new AiOpponent(
                        "ai_street_novice",
                        "街边新人决斗者",
                        "Lv.1 · 仅使用 N/R 级凡骨与基础装备",
                        "1999-02-04",
                        1, 150, 80, 50,
                        "只使用当前时间轴已发售的 N/R 卡片，适合开局凑代币。"
                ),
                new AiOpponent(
                        "ai_vol1_yugi",
                        "初代暗游戏（Vol.1 构筑）",
                        "Lv.2 · 黑魔术师 & 盖亚 + 经典解场",
                        "1999-02-04",
                        2, 220, 100, 60,
                        "严格遵守当前时间轴的 OCG 卡池与禁限表（如 1999.08 后黑洞/落穴限 1）。"
                ),
                new AiOpponent(
                        "ai_vol2_ace",
                        "决斗王国精英（Vol.1+Vol.2）",
                        "Lv.3 · 死者苏生 / 护封剑 / 诅咒之龙",
                        "1999-03-27",
                        3, 300, 120, 80,
                        "解锁 Vol.2 后登场，融合 Vol.1+Vol.2 高稀有主力，并严格遵守最新禁限表。"
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
