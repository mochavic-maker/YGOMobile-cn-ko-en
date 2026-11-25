package cn.garymb.ygomobile.rpg.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Pure-Java quick duel auto-resolver obeying the active timeline's OCG rule set
 * (tribute rules, once-per-turn spell/trap limit under 1999 Official Rule, first-turn draw, deck-out).
 */
public final class QuickDuelSimulator {
    private QuickDuelSimulator() {}

    public static class QuickDuelReport {
        public final boolean playerWon;
        public final boolean isDraw;
        public final int playerLp;
        public final int aiLp;
        public final int turns;
        public final OcgRuleSet appliedRule;
        public final List<String> logs;

        public QuickDuelReport(boolean playerWon, boolean isDraw, int playerLp, int aiLp,
                               int turns, OcgRuleSet appliedRule, List<String> logs) {
            this.playerWon = playerWon;
            this.isDraw = isDraw;
            this.playerLp = playerLp;
            this.aiLp = aiLp;
            this.turns = turns;
            this.appliedRule = appliedRule;
            this.logs = logs;
        }
    }

    private static class MonsterSlot {
        RpgCard card;
        int bonus;
        int atk() { return card.atk + bonus; }
    }

    private static class Side {
        int lp;
        List<Integer> deck;
        List<RpgCard> hand = new ArrayList<>();
        List<MonsterSlot> monsters = new ArrayList<>();
        List<RpgCard> gy = new ArrayList<>();
        int swordsTurns = 0;
        boolean hasTrapHole = false;

        Side(int startLp, List<Integer> deckIds, Random rng) {
            this.lp = startLp;
            this.deck = new ArrayList<>(deckIds);
            Collections.shuffle(this.deck, rng);
        }
    }

    public static QuickDuelReport simulate(RpgCatalog catalog,
                                           List<Integer> playerDeckIds,
                                           List<Integer> aiDeckIds,
                                           AiOpponent opponent,
                                           String currentDate,
                                           Random rng) {
        OcgRuleSet rule = TimelineEngine.getActiveRuleSet(catalog, currentDate);
        Side p = new Side(rule.startLp, playerDeckIds, rng);
        Side a = new Side(rule.startLp, aiDeckIds, rng);
        List<String> logs = new ArrayList<>();
        logs.add("快速决斗开始：你 vs " + opponent.name + "（规则：" + rule.name + "）");

        for (int i = 0; i < rule.startHand; i++) {
            draw(catalog, p);
            draw(catalog, a);
        }

        int turn = 1;
        boolean playerTurn = true;
        int maxTurns = 40;

        while (turn <= maxTurns && p.lp > 0 && a.lp > 0) {
            Side cur = playerTurn ? p : a;
            Side opp = playerTurn ? a : p;
            String curName = playerTurn ? "你" : opponent.name;

            if (turn > 1 || rule.firstTurnDrawEnabled) {
                if (!draw(catalog, cur)) {
                    logs.add("T" + turn + " " + curName + " 卡组抽干！");
                    if (rule.deckOutCompareLp) {
                        boolean pWin = p.lp > a.lp;
                        boolean draw = p.lp == a.lp;
                        return new QuickDuelReport(pWin, draw, p.lp, a.lp, turn, rule, logs);
                    }
                    return new QuickDuelReport(!playerTurn, false, p.lp, a.lp, turn, rule, logs);
                }
            }

            if (hasExodia(cur)) {
                logs.add("T" + turn + " " + curName + " 集齐「被封印的艾克佐迪亚」5张部件，特殊胜利！");
                return new QuickDuelReport(playerTurn, false, p.lp, a.lp, turn, rule, logs);
            }

            int spellsPlayed = 0;
            int trapsSet = 0;
            int spellLimit = rule.spellTrapOncePerTurnLimit ? 1 : 99;
            int trapLimit = rule.spellTrapOncePerTurnLimit ? 1 : 99;

            // Spells: Raigeki / Dark Hole / Fissure / Pot of Greed / Graceful Charity / Feather Duster / Change of Heart / Reborn / Swords / Burn / Heal
            for (int i = cur.hand.size() - 1; i >= 0 && spellsPlayed < spellLimit; i--) {
                if (i >= cur.hand.size()) continue;
                RpgCard c = cur.hand.get(i);
                if (c.id == 55144522) { // Pot of Greed
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    draw(catalog, cur);
                    draw(catalog, cur);
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「强欲之壶」抽 2 张卡！");
                    if (hasExodia(cur)) {
                        logs.add("T" + turn + " " + curName + " 集齐「被封印的艾克佐迪亚」5张部件，特殊胜利！");
                        return new QuickDuelReport(playerTurn, false, p.lp, a.lp, turn, rule, logs);
                    }
                } else if (c.id == 79571449) { // Graceful Charity
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    draw(catalog, cur);
                    draw(catalog, cur);
                    draw(catalog, cur);
                    for (int d = 0; d < 2 && !cur.hand.isEmpty(); d++) {
                        cur.gy.add(cur.hand.remove(cur.hand.size() - 1));
                    }
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「天使的施舍」抽 3 弃 2！");
                    if (hasExodia(cur)) {
                        logs.add("T" + turn + " " + curName + " 集齐「被封印的艾克佐迪亚」5张部件，特殊胜利！");
                        return new QuickDuelReport(playerTurn, false, p.lp, a.lp, turn, rule, logs);
                    }
                } else if (c.id == 12580477 && !opp.monsters.isEmpty()) { // Raigeki
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    for (MonsterSlot ms : opp.monsters) opp.gy.add(ms.card);
                    opp.monsters.clear();
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「雷击」摧毁对手全怪兽！");
                } else if (c.id == 18144506 && (opp.hasTrapHole || opp.swordsTurns > 0)) { // Harpie's Feather Duster
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    opp.hasTrapHole = false;
                    opp.swordsTurns = 0;
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「鹰身女妖的羽毛扫」清空对手后场！");
                } else if (c.id == 4031928 && !opp.monsters.isEmpty() && cur.monsters.size() < 5) { // Change of Heart
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    MonsterSlot bestOpp = opp.monsters.get(0);
                    for (MonsterSlot ms : opp.monsters) if (ms.atk() > bestOpp.atk()) bestOpp = ms;
                    opp.monsters.remove(bestOpp);
                    cur.monsters.add(bestOpp);
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「心变」夺取「" + bestOpp.card.getDisplayName() + "」！");
                } else if (c.id == 53129443 && !opp.monsters.isEmpty() && cur.monsters.isEmpty()) {
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    for (MonsterSlot ms : opp.monsters) opp.gy.add(ms.card);
                    opp.monsters.clear();
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「黑洞」清场！");
                } else if (c.id == 66788016 && !opp.monsters.isEmpty()) {
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    MonsterSlot weakest = opp.monsters.get(0);
                    for (MonsterSlot ms : opp.monsters) if (ms.atk() < weakest.atk()) weakest = ms;
                    opp.monsters.remove(weakest);
                    opp.gy.add(weakest.card);
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「地割」破坏了「" + weakest.card.getDisplayName() + "」");
                } else if (c.id == 83764718 && cur.monsters.size() < 5) {
                    RpgCard bestGy = pickStrongestGyMonster(cur, opp);
                    if (bestGy != null) {
                        cur.hand.remove(i);
                        cur.gy.add(c);
                        MonsterSlot ms = new MonsterSlot();
                        ms.card = bestGy;
                        cur.monsters.add(ms);
                        spellsPlayed++;
                        logs.add("T" + turn + " " + curName + " 发动「死者苏生」特召「" + bestGy.getDisplayName() + "」");
                    }
                } else if (c.id == 72302403 && cur.swordsTurns == 0 && !opp.monsters.isEmpty()) {
                    cur.hand.remove(i);
                    cur.swordsTurns = 3;
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「光之护封剑」(3回合)");
                } else if (c.id == 73134081 || c.id == 76103675 || c.id == 46130346 || c.id == 19523799 || c.id == 56260110) {
                    int dmg = c.id == 19523799 ? 800 : (c.id == 73134081 ? 600 : (c.id == 46130346 ? 500 : (c.id == 56260110 ? 300 : 200)));
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    opp.lp = Math.max(0, opp.lp - dmg);
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「" + c.getDisplayName() + "」造成 " + dmg + " 伤害");
                } else if (c.id == 11868825 || c.id == 38199696 || c.id == 84257639 || c.id == 20871001) {
                    int heal = c.id == 84257639 ? 1000 : (c.id == 11868825 ? 600 : (c.id == 38199696 ? 500 : 400));
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    cur.lp += heal;
                    spellsPlayed++;
                }
            }
            if (p.lp <= 0 || a.lp <= 0) break;

            // Normal Summon respecting active OcgRuleSet tribute rules
            int bestIdx = -1;
            int bestAtk = -1;
            int bestTribs = 0;
            for (int i = 0; i < cur.hand.size(); i++) {
                RpgCard c = cur.hand.get(i);
                if (!c.isMonster() || c.isExtraDeckCard()) continue;
                int need = rule.requiredTributes(c.level);
                if (need == 0 && cur.monsters.size() >= 5) continue;
                if (need > cur.monsters.size()) continue;
                if (c.atk > bestAtk) {
                    bestAtk = c.atk;
                    bestIdx = i;
                    bestTribs = need;
                }
            }
            if (bestIdx >= 0) {
                RpgCard summoned = cur.hand.remove(bestIdx);
                for (int t = 0; t < bestTribs; t++) {
                    MonsterSlot weakest = cur.monsters.get(0);
                    for (MonsterSlot ms : cur.monsters) if (ms.atk() < weakest.atk()) weakest = ms;
                    cur.monsters.remove(weakest);
                    cur.gy.add(weakest.card);
                }
                if (opp.hasTrapHole && summoned.atk >= 1000) {
                    opp.hasTrapHole = false;
                    cur.gy.add(summoned);
                    logs.add("T" + turn + " " + curName + " 召唤「" + summoned.getDisplayName() + "」被对手「落穴」破坏！");
                } else {
                    MonsterSlot ms = new MonsterSlot();
                    ms.card = summoned;
                    cur.monsters.add(ms);
                    logs.add("T" + turn + " " + curName + " 召唤「" + summoned.getDisplayName() + "」(ATK " + summoned.atk + ")");
                }
            }

            // Set Trap Hole if allowed
            if (!cur.hasTrapHole && trapsSet < trapLimit) {
                for (int i = 0; i < cur.hand.size(); i++) {
                    if (cur.hand.get(i).id == 4206964) {
                        cur.hand.remove(i);
                        cur.hasTrapHole = true;
                        trapsSet++;
                        break;
                    }
                }
            }

            // Battle Phase (turn > 1 and not blocked by Swords)
            if (turn > 1 && opp.swordsTurns <= 0) {
                for (MonsterSlot attacker : new ArrayList<>(cur.monsters)) {
                    if (opp.lp <= 0) break;
                    if (opp.monsters.isEmpty()) {
                        opp.lp = Math.max(0, opp.lp - attacker.atk());
                        logs.add("T" + turn + " 「" + attacker.card.getDisplayName() + "」直击 -" + attacker.atk() + " LP");
                    } else {
                        MonsterSlot target = null;
                        for (MonsterSlot om : opp.monsters) {
                            if (attacker.atk() > om.atk() && (target == null || om.atk() > target.atk())) {
                                target = om;
                            }
                        }
                        if (target != null) {
                            int dmg = attacker.atk() - target.atk();
                            opp.lp = Math.max(0, opp.lp - dmg);
                            opp.monsters.remove(target);
                            opp.gy.add(target.card);
                            logs.add("T" + turn + " 「" + attacker.card.getDisplayName() + "」击破「" + target.card.getDisplayName() + "」-" + dmg + " LP");
                        }
                    }
                }
            }

            if (opp.swordsTurns > 0) opp.swordsTurns--;
            turn++;
            playerTurn = !playerTurn;
        }

        boolean isDraw = p.lp == a.lp;
        boolean pWin = p.lp > a.lp;
        return new QuickDuelReport(pWin, isDraw, p.lp, a.lp, turn - 1, rule, logs);
    }

    private static boolean draw(RpgCatalog catalog, Side side) {
        if (side.deck.isEmpty()) return false;
        int id = side.deck.remove(side.deck.size() - 1);
        RpgCard c = catalog.findCard(id);
        if (c != null) side.hand.add(c);
        return true;
    }

    private static RpgCard pickStrongestGyMonster(Side cur, Side opp) {
        RpgCard best = null;
        Side owner = null;
        for (RpgCard c : cur.gy) {
            if (c.isMonster() && (best == null || c.atk > best.atk)) { best = c; owner = cur; }
        }
        for (RpgCard c : opp.gy) {
            if (c.isMonster() && (best == null || c.atk > best.atk)) { best = c; owner = opp; }
        }
        if (best != null && owner != null) owner.gy.remove(best);
        return best;
    }

    private static boolean hasExodia(Side side) {
        boolean h = false, la = false, ra = false, ll = false, rl = false;
        for (RpgCard c : side.hand) {
            if (c.id == 33396948) h = true;
            else if (c.id == 7902349) la = true;
            else if (c.id == 70903634) ra = true;
            else if (c.id == 44519536) ll = true;
            else if (c.id == 8124921) rl = true;
        }
        return h && la && ra && ll && rl;
    }
}
