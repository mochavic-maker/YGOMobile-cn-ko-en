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
 * Pure-Java quick duel auto-resolver obeying both:
 * 1) the active timeline's OCG rule set (Official Rule vs Expert Rule), and
 * 2) the active timeline's OCG card errata version (e.g. Sangan 1999-11-18 any-GY vs 1999-12-20 field-only,
 *    Witch of the Black Forest 1999-11-18 any-GY, Crush Card Virus 1999 3-turn draw virus, Last Will 1999).
 */
public final class QuickDuelSimulator {
    private static final int CARD_SANGAN = 26202165;
    private static final int CARD_WITCH = 78010363;
    private static final int CARD_CCV = 57728570;
    private static final int CARD_LAST_WILL = 85602018;

    private QuickDuelSimulator() {}

    public static class ReplayFrame {
        public final int stepIndex;
        public final int turn;
        public final String phase;
        public final String activeName;
        public final int sideALp;
        public final int sideBLp;
        public final int sideAHandCount;
        public final int sideBHandCount;
        public final int sideADeckCount;
        public final int sideBDeckCount;
        public final List<String> sideAMonsters;
        public final List<String> sideBMonsters;
        public final List<String> newLogs;

        public ReplayFrame(int stepIndex, int turn, String phase, String activeName,
                           int sideALp, int sideBLp,
                           int sideAHandCount, int sideBHandCount,
                           int sideADeckCount, int sideBDeckCount,
                           List<String> sideAMonsters, List<String> sideBMonsters,
                           List<String> newLogs) {
            this.stepIndex = stepIndex;
            this.turn = turn;
            this.phase = phase;
            this.activeName = activeName;
            this.sideALp = sideALp;
            this.sideBLp = sideBLp;
            this.sideAHandCount = sideAHandCount;
            this.sideBHandCount = sideBHandCount;
            this.sideADeckCount = sideADeckCount;
            this.sideBDeckCount = sideBDeckCount;
            this.sideAMonsters = sideAMonsters;
            this.sideBMonsters = sideBMonsters;
            this.newLogs = newLogs;
        }
    }

    public static class QuickDuelReport {
        public final boolean playerWon;
        public final boolean isDraw;
        public final int playerLp;
        public final int aiLp;
        public final int turns;
        public final OcgRuleSet appliedRule;
        public final List<String> logs;
        public final String sideAName;
        public final String sideBName;
        public final List<ReplayFrame> replayFrames;

        public QuickDuelReport(boolean playerWon, boolean isDraw, int playerLp, int aiLp,
                               int turns, OcgRuleSet appliedRule, List<String> logs) {
            this(playerWon, isDraw, playerLp, aiLp, turns, appliedRule, logs, "你", "对手", new ArrayList<ReplayFrame>());
        }

        public QuickDuelReport(boolean playerWon, boolean isDraw, int playerLp, int aiLp,
                               int turns, OcgRuleSet appliedRule, List<String> logs,
                               String sideAName, String sideBName, List<ReplayFrame> replayFrames) {
            this.playerWon = playerWon;
            this.isDraw = isDraw;
            this.playerLp = playerLp;
            this.aiLp = aiLp;
            this.turns = turns;
            this.appliedRule = appliedRule;
            this.logs = logs;
            this.sideAName = sideAName;
            this.sideBName = sideBName;
            this.replayFrames = replayFrames != null ? replayFrames : new ArrayList<ReplayFrame>();
        }
    }

    private static List<String> describeMonsters(Side s) {
        List<String> out = new ArrayList<>();
        for (MonsterSlot ms : s.monsters) {
            out.add(ms.card.getDisplayName() + "(" + ms.atk() + ")");
        }
        return out;
    }

    private static int captureFrame(List<ReplayFrame> frames, List<String> logs, int lastLogCursor,
                                    int turn, String phase, String activeName, Side p, Side a, boolean force) {
        if (!force && logs.size() <= lastLogCursor) return lastLogCursor;
        List<String> delta = new ArrayList<>();
        for (int i = lastLogCursor; i < logs.size(); i++) {
            delta.add(logs.get(i));
        }
        frames.add(new ReplayFrame(
                frames.size(), turn, phase, activeName,
                p.lp, a.lp,
                p.hand.size(), a.hand.size(),
                p.deck.size(), a.deck.size(),
                describeMonsters(p), describeMonsters(a),
                delta
        ));
        return logs.size();
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
        int ccvInfectedTurns = 0;
        boolean hasTrapHole = false;
        boolean hasCcvSet = false;
        boolean lastWillActive = false;
        boolean sanganUsedThisTurn = false;
        boolean witchUsedThisTurn = false;
        boolean oppNoDamageThisTurn = false;

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
        return simulateBetweenDecks(catalog, "你", playerDeckIds, opponent.name, aiDeckIds, currentDate, rng);
    }

    public static QuickDuelReport simulateBetweenDecks(RpgCatalog catalog,
                                                       String sideAName,
                                                       List<Integer> deckAIds,
                                                       String sideBName,
                                                       List<Integer> deckBIds,
                                                       String currentDate,
                                                       Random rng) {
        OcgRuleSet rule = TimelineEngine.getActiveRuleSet(catalog, currentDate);
        Side p = new Side(rule.startLp, deckAIds, rng);
        Side a = new Side(rule.startLp, deckBIds, rng);
        List<String> logs = new ArrayList<>();
        List<ReplayFrame> frames = new ArrayList<>();
        int logCursor = 0;
        logs.add("快速决斗开始：" + sideAName + " vs " + sideBName + "（时间轴：" + currentDate + " · 规则：" + rule.name + "）");

        for (int i = 0; i < rule.startHand; i++) {
            draw(catalog, p);
            draw(catalog, a);
        }

        int turn = 1;
        boolean playerTurn = rng.nextBoolean();
        logs.add("🪙 抛硬币决定先手：" + (playerTurn ? sideAName + "先攻" : sideBName + "先攻"));
        logCursor = captureFrame(frames, logs, logCursor, 1, "开局", playerTurn ? sideAName : sideBName, p, a, true);
        int maxTurns = 40;

        while (turn <= maxTurns && p.lp > 0 && a.lp > 0) {
            Side cur = playerTurn ? p : a;
            Side opp = playerTurn ? a : p;
            String curName = playerTurn ? sideAName : sideBName;
            String oppName = playerTurn ? sideBName : sideAName;
            cur.sanganUsedThisTurn = false;
            cur.witchUsedThisTurn = false;
            cur.lastWillActive = false;
            cur.oppNoDamageThisTurn = false;

            if (turn > 1 || rule.firstTurnDrawEnabled) {
                RpgCard drawn = draw(catalog, cur);
                if (drawn == null) {
                    logs.add("T" + turn + " " + curName + " 卡组抽干！");
                    captureFrame(frames, logs, logCursor, turn, "决胜", curName, p, a, true);
                    if (rule.deckOutCompareLp) {
                        boolean pWin = p.lp > a.lp;
                        boolean draw = p.lp == a.lp;
                        return new QuickDuelReport(pWin, draw, p.lp, a.lp, turn, rule, logs, sideAName, sideBName, frames);
                    }
                    return new QuickDuelReport(!playerTurn, false, p.lp, a.lp, turn, rule, logs, sideAName, sideBName, frames);
                }
                // 1999 pre-errata Crush Card Virus 3-turn draw destruction
                if (cur.ccvInfectedTurns > 0) {
                    if (drawn.isMonster() && drawn.atk >= 1500) {
                        cur.hand.remove(drawn);
                        sendToGy(catalog, cur, drawn, false, currentDate, turn, curName, logs);
                        logs.add("T" + turn + " 「死之卡组破坏病毒」[1999初版] 破坏了 " + curName + " 抽到的「" + drawn.getDisplayName() + "」！");
                    }
                    cur.ccvInfectedTurns--;
                }
            }

            if (hasExodia(cur)) {
                logs.add("T" + turn + " " + curName + " 集齐「被封印的艾克佐迪亚」5张部件，特殊胜利！");
                captureFrame(frames, logs, logCursor, turn, "特殊胜利", curName, p, a, true);
                return new QuickDuelReport(playerTurn, false, p.lp, a.lp, turn, rule, logs, sideAName, sideBName, frames);
            }

            int spellsPlayed = 0;
            int trapsSet = 0;
            int spellLimit = rule.spellTrapOncePerTurnLimit ? 1 : 99;
            int trapLimit = rule.spellTrapOncePerTurnLimit ? 1 : 99;

            // Spells phase
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
                        captureFrame(frames, logs, logCursor, turn, "特殊胜利", curName, p, a, true);
                        return new QuickDuelReport(playerTurn, false, p.lp, a.lp, turn, rule, logs, sideAName, sideBName, frames);
                    }
                } else if (c.id == 79571449) { // Graceful Charity (synergizes with 1999 pre-errata Sangan / Witch!)
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    draw(catalog, cur);
                    draw(catalog, cur);
                    draw(catalog, cur);
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「天使的施舍」抽 3 弃 2！");
                    for (int d = 0; d < 2 && !cur.hand.isEmpty(); d++) {
                        int discardIdx = pickDiscardIndex(catalog, cur.hand, currentDate);
                        RpgCard discarded = cur.hand.remove(discardIdx);
                        sendToGy(catalog, cur, discarded, false, currentDate, turn, curName, logs);
                    }
                    if (hasExodia(cur)) {
                        logs.add("T" + turn + " " + curName + " 集齐「被封印的艾克佐迪亚」5张部件，特殊胜利！");
                        captureFrame(frames, logs, logCursor, turn, "特殊胜利", curName, p, a, true);
                        return new QuickDuelReport(playerTurn, false, p.lp, a.lp, turn, rule, logs, sideAName, sideBName, frames);
                    }
                } else if (c.id == CARD_LAST_WILL) { // Last Will (EX 1999-12-16)
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    cur.lastWillActive = true;
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「遗言状」["
                            + CardErrataEngine.getVersionBadge(catalog, CARD_LAST_WILL, currentDate) + "]！");
                } else if (c.id == 12580477 && !opp.monsters.isEmpty()) { // Raigeki
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    List<MonsterSlot> destroyed = new ArrayList<>(opp.monsters);
                    opp.monsters.clear();
                    for (MonsterSlot ms : destroyed) {
                        sendToGy(catalog, opp, ms.card, true, currentDate, turn, oppName, logs);
                    }
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「雷击」摧毁对手全怪兽！");
                } else if (c.id == 18144506 && (opp.hasTrapHole || opp.hasCcvSet || opp.swordsTurns > 0)) { // Feather Duster
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    opp.hasTrapHole = false;
                    opp.hasCcvSet = false;
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
                } else if (c.id == 53129443 && !opp.monsters.isEmpty() && cur.monsters.isEmpty()) { // Dark Hole
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    List<MonsterSlot> destroyed = new ArrayList<>(opp.monsters);
                    opp.monsters.clear();
                    for (MonsterSlot ms : destroyed) {
                        sendToGy(catalog, opp, ms.card, true, currentDate, turn, oppName, logs);
                    }
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「黑洞」清场！");
                } else if (c.id == 66788016 && !opp.monsters.isEmpty()) { // Fissure
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    MonsterSlot weakest = opp.monsters.get(0);
                    for (MonsterSlot ms : opp.monsters) if (ms.atk() < weakest.atk()) weakest = ms;
                    opp.monsters.remove(weakest);
                    sendToGy(catalog, opp, weakest.card, true, currentDate, turn, oppName, logs);
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「地割」破坏了「" + weakest.card.getDisplayName() + "」");
                } else if (c.id == 83764718 && cur.monsters.size() < 5) { // Monster Reborn
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
                } else if (c.id == 72302403 && cur.swordsTurns == 0 && !opp.monsters.isEmpty()) { // Swords
                    cur.hand.remove(i);
                    cur.swordsTurns = 3;
                    spellsPlayed++;
                    logs.add("T" + turn + " " + curName + " 发动「光之护封剑」(3回合)");
                } else if (c.id == 73134081 || c.id == 76103675 || c.id == 46130346 || c.id == 19523799 || c.id == 56260110) {
                    int dmg = c.id == 19523799 ? 800 : (c.id == 73134081 ? 600 : (c.id == 46130346 ? 500 : (c.id == 56260110 ? 300 : 200)));
                    cur.hand.remove(i);
                    cur.gy.add(c);
                    if (!cur.oppNoDamageThisTurn) {
                        opp.lp = Math.max(0, opp.lp - dmg);
                    }
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
            logCursor = captureFrame(frames, logs, logCursor, turn, "MP1 魔法", curName, p, a, false);
            if (p.lp <= 0 || a.lp <= 0) break;

            // Trigger set Crush Card Virus if cur has a DARK monster ATK <= 1000 on field
            if (cur.hasCcvSet) {
                MonsterSlot fodder = findCcvFodder(cur);
                if (fodder != null) {
                    cur.hasCcvSet = false;
                    cur.monsters.remove(fodder);
                    sendToGy(catalog, cur, fodder.card, true, currentDate, turn, curName, logs);
                    boolean is1999 = CardErrataEngine.hasFlag(catalog, CARD_CCV, currentDate, "CCV_ORIGINAL_3TURN_DRAW");
                    int destroyedCnt = 0;
                    for (int k = opp.monsters.size() - 1; k >= 0; k--) {
                        if (opp.monsters.get(k).atk() >= 1500) {
                            RpgCard rm = opp.monsters.remove(k).card;
                            sendToGy(catalog, opp, rm, true, currentDate, turn, oppName, logs);
                            destroyedCnt++;
                        }
                    }
                    for (int k = opp.hand.size() - 1; k >= 0; k--) {
                        RpgCard hc = opp.hand.get(k);
                        if (hc.isMonster() && hc.atk >= 1500) {
                            opp.hand.remove(k);
                            sendToGy(catalog, opp, hc, false, currentDate, turn, oppName, logs);
                            destroyedCnt++;
                        }
                    }
                    if (is1999) {
                        opp.ccvInfectedTurns = 3;
                        logs.add("T" + turn + " " + curName + " 发动「死之卡组破坏病毒」[1999初版] 破坏对手 " + destroyedCnt + " 只怪兽，并封锁其后续 3 回合抽卡！");
                    } else {
                        cur.oppNoDamageThisTurn = true;
                        logs.add("T" + turn + " " + curName + " 发动「死之卡组破坏病毒」[2015改版] 破坏对手 " + destroyedCnt + " 只怪兽（本回合对手不受伤害）");
                    }
                    logCursor = captureFrame(frames, logs, logCursor, turn, "MP1 陷阱", curName, p, a, false);
                }
            }

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
                    sendToGy(catalog, cur, weakest.card, true, currentDate, turn, curName, logs);
                }
                if (opp.hasTrapHole && summoned.atk >= 1000) {
                    opp.hasTrapHole = false;
                    sendToGy(catalog, cur, summoned, true, currentDate, turn, curName, logs);
                    logs.add("T" + turn + " " + curName + " 召唤「" + summoned.getDisplayName() + "」被对手「落穴」破坏！");
                } else {
                    MonsterSlot ms = new MonsterSlot();
                    ms.card = summoned;
                    cur.monsters.add(ms);
                    logs.add("T" + turn + " " + curName + " 召唤「" + summoned.getDisplayName() + "」(ATK " + summoned.atk + ")");
                }
                logCursor = captureFrame(frames, logs, logCursor, turn, "MP1 召唤", curName, p, a, false);
            }

            // Set Trap Hole or Crush Card Virus if allowed
            if (trapsSet < trapLimit) {
                for (int i = 0; i < cur.hand.size() && trapsSet < trapLimit; i++) {
                    int id = cur.hand.get(i).id;
                    if (id == 4206964 && !cur.hasTrapHole) {
                        cur.hand.remove(i);
                        cur.hasTrapHole = true;
                        trapsSet++;
                        break;
                    } else if (id == CARD_CCV && !cur.hasCcvSet) {
                        cur.hand.remove(i);
                        cur.hasCcvSet = true;
                        trapsSet++;
                        break;
                    }
                }
            }

            if (hasExodia(cur)) {
                logs.add("T" + turn + " " + curName + " 集齐「被封印的艾克佐迪亚」5张部件，特殊胜利！");
                captureFrame(frames, logs, logCursor, turn, "特殊胜利", curName, p, a, true);
                return new QuickDuelReport(playerTurn, false, p.lp, a.lp, turn, rule, logs, sideAName, sideBName, frames);
            }

            // Battle Phase (turn > 1 and not blocked by Swords)
            if (turn > 1 && opp.swordsTurns <= 0) {
                for (MonsterSlot attacker : new ArrayList<>(cur.monsters)) {
                    if (opp.lp <= 0) break;
                    if (opp.monsters.isEmpty()) {
                        if (!cur.oppNoDamageThisTurn) {
                            opp.lp = Math.max(0, opp.lp - attacker.atk());
                        }
                        logs.add("T" + turn + " 「" + attacker.card.getDisplayName() + "」直击 -" + attacker.atk() + " LP");
                        logCursor = captureFrame(frames, logs, logCursor, turn, "BP 战斗", curName, p, a, false);
                    } else {
                        MonsterSlot target = null;
                        for (MonsterSlot om : opp.monsters) {
                            if (attacker.atk() > om.atk() && (target == null || om.atk() > target.atk())) {
                                target = om;
                            }
                        }
                        if (target != null) {
                            int dmg = attacker.atk() - target.atk();
                            if (!cur.oppNoDamageThisTurn) {
                                opp.lp = Math.max(0, opp.lp - dmg);
                            }
                            opp.monsters.remove(target);
                            sendToGy(catalog, opp, target.card, true, currentDate, turn, oppName, logs);
                            logs.add("T" + turn + " 「" + attacker.card.getDisplayName() + "」击破「" + target.card.getDisplayName() + "」-" + dmg + " LP");
                            logCursor = captureFrame(frames, logs, logCursor, turn, "BP 战斗", curName, p, a, false);
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
        logs.add("🏁 决斗结束：" + (isDraw ? "平局" : (pWin ? sideAName + " 获胜！" : sideBName + " 获胜！"))
                + "（LP " + p.lp + " : " + a.lp + "）");
        captureFrame(frames, logs, logCursor, Math.max(1, turn - 1), "终局", isDraw ? "平局" : (pWin ? sideAName : sideBName), p, a, true);
        return new QuickDuelReport(pWin, isDraw, p.lp, a.lp, turn - 1, rule, logs, sideAName, sideBName, frames);
    }

    /**
     * Handles sending a card to GY and triggering timeline-accurate errata effects:
     * - Sangan (26202165): 1999-11-18 triggers from ANY zone; 1999-12-20 triggers from FIELD only; 2016-04-01 HOPT.
     * - Witch (78010363): 1999-11-18 triggers from ANY zone; 2000-04-20 triggers from FIELD only; 2017-04-01 HOPT.
     * - Last Will (85602018): 1999-12-16 summons ATK<=1500 from deck when a monster hits GY.
     */
    private static void sendToGy(RpgCatalog catalog, Side owner, RpgCard card, boolean fromField,
                                 String currentDate, int turn, String ownerName, List<String> logs) {
        if (card == null) return;
        owner.gy.add(card);

        if (card.id == CARD_SANGAN) {
            boolean anyGy = CardErrataEngine.hasFlag(catalog, CARD_SANGAN, currentDate, "SANGAN_ANY_GY");
            boolean hopt = CardErrataEngine.hasFlag(catalog, CARD_SANGAN, currentDate, "HOPT");
            if ((fromField || anyGy) && (!hopt || !owner.sanganUsedThisTurn)) {
                owner.sanganUsedThisTurn = true;
                RpgCard found = searchDeckMonster(catalog, owner, true, 1500);
                if (found != null) {
                    owner.hand.add(found);
                    String badge = CardErrataEngine.getVersionBadge(catalog, CARD_SANGAN, currentDate);
                    logs.add("T" + turn + " " + ownerName + " 触发「三眼怪」[" + badge
                            + (fromField ? "·场上送墓" : "·手卡/非场上送墓") + "] 检索了「" + found.getDisplayName() + "」！");
                }
            }
        } else if (card.id == CARD_WITCH) {
            boolean anyGy = CardErrataEngine.hasFlag(catalog, CARD_WITCH, currentDate, "WITCH_ANY_GY");
            boolean hopt = CardErrataEngine.hasFlag(catalog, CARD_WITCH, currentDate, "HOPT");
            if ((fromField || anyGy) && (!hopt || !owner.witchUsedThisTurn)) {
                owner.witchUsedThisTurn = true;
                RpgCard found = searchDeckMonster(catalog, owner, false, 1500);
                if (found != null) {
                    owner.hand.add(found);
                    String badge = CardErrataEngine.getVersionBadge(catalog, CARD_WITCH, currentDate);
                    logs.add("T" + turn + " " + ownerName + " 触发「黑森林的魔女」[" + badge
                            + (fromField ? "·场上送墓" : "·手卡/非场上送墓") + "] 检索了「" + found.getDisplayName() + "」！");
                }
            }
        }

        if (card.isMonster() && owner.lastWillActive && owner.monsters.size() < 5) {
            boolean broad = CardErrataEngine.hasFlag(catalog, CARD_LAST_WILL, currentDate, "LAST_WILL_1999_BROAD");
            if (fromField || broad) {
                owner.lastWillActive = false;
                RpgCard sp = searchDeckMonster(catalog, owner, true, 1500);
                if (sp != null) {
                    MonsterSlot ms = new MonsterSlot();
                    ms.card = sp;
                    owner.monsters.add(ms);
                    logs.add("T" + turn + " " + ownerName + " 「遗言状」特召了「" + sp.getDisplayName() + "」(ATK " + sp.atk + ")！");
                }
            }
        }
    }

    private static int pickDiscardIndex(RpgCatalog catalog, List<RpgCard> hand, String currentDate) {
        // Under 1999 pre-errata, discarding Sangan/Witch from hand triggers their search!
        boolean sanganAny = CardErrataEngine.hasFlag(catalog, CARD_SANGAN, currentDate, "SANGAN_ANY_GY");
        boolean witchAny = CardErrataEngine.hasFlag(catalog, CARD_WITCH, currentDate, "WITCH_ANY_GY");
        for (int i = 0; i < hand.size(); i++) {
            int id = hand.get(i).id;
            if (id == CARD_SANGAN && sanganAny) return i;
            if (id == CARD_WITCH && witchAny) return i;
        }
        return hand.size() - 1;
    }

    private static RpgCard searchDeckMonster(RpgCatalog catalog, Side side, boolean byAtk, int maxStat) {
        int bestIdx = -1;
        int bestScore = -1;
        for (int i = 0; i < side.deck.size(); i++) {
            RpgCard c = catalog.findCard(side.deck.get(i));
            if (c == null || !c.isMonster() || c.isExtraDeckCard()) continue;
            int stat = byAtk ? c.atk : c.def;
            if (stat <= maxStat) {
                int score = isExodiaPiece(c.id) ? 10000 : c.atk;
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = i;
                }
            }
        }
        if (bestIdx >= 0) {
            int id = side.deck.remove(bestIdx);
            return catalog.findCard(id);
        }
        return null;
    }

    private static MonsterSlot findCcvFodder(Side side) {
        for (MonsterSlot ms : side.monsters) {
            if (ms.card != null && "DARK".equalsIgnoreCase(ms.card.attribute) && ms.atk() <= 1000) {
                return ms;
            }
        }
        return null;
    }

    private static RpgCard draw(RpgCatalog catalog, Side side) {
        if (side.deck.isEmpty()) return null;
        int id = side.deck.remove(side.deck.size() - 1);
        RpgCard c = catalog.findCard(id);
        if (c != null) side.hand.add(c);
        return c;
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

    private static boolean isExodiaPiece(int id) {
        return id == 33396948 || id == 7902349 || id == 70903634 || id == 44519536 || id == 8124921;
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
