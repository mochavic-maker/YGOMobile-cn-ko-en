package cn.garymb.ygomobile.rpg;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.game.replay.ReplayMessageSlicer;
import cn.garymb.ygomobile.network.server.EngineMessage;
import cn.garymb.ygomobile.network.server.YrpWriter;
import cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels;
import cn.garymb.ygomobile.rpg.ai.RpgAiDuelState;
import cn.garymb.ygomobile.rpg.ai.RpgAiStrategy;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Runs 100% real native {@link OcgDuelEngine} (ocgcore C++ + real Lua card scripts +
 * historical RPG Lua rule overrides) duels completely in-process with TWO dedicated
 * {@link RpgAiStrategy} instances playing against each other.
 * Replaces the legacy approximate QuickDuelSimulator so every fast duel, tournament
 * match, and 100-game win-rate test reflects true ocgcore rules and real AI tactics.
 */
public final class RpgOcgHeadlessDuelRunner {
    private static final String TAG = "RpgOcgHeadless";
    private static final Object ENGINE_LOCK = new Object();

    private static final int RESPONSE_BUF_LEN = 256;
    private static final int MAX_PROCESS_STEPS = 5000;
    private static final int MAX_TURNS = 100;

    private static final int REFRESH_MZONE_FLAG = 0x881fff;
    private static final int REFRESH_SZONE_FLAG = 0x681fff;
    private static final int REFRESH_HAND_FLAG  = 0x681fff;

    // Engine message types
    private static final int MSG_RETRY                = 1;
    private static final int MSG_HINT                 = 2;
    private static final int MSG_WIN                  = 5;
    private static final int MSG_SELECT_BATTLECMD     = 10;
    private static final int MSG_SELECT_IDLECMD       = 11;
    private static final int MSG_SELECT_EFFECTYN      = 12;
    private static final int MSG_SELECT_YESNO         = 13;
    private static final int MSG_SELECT_OPTION        = 14;
    private static final int MSG_SELECT_CARD          = 15;
    private static final int MSG_SELECT_CHAIN         = 16;
    private static final int MSG_SELECT_PLACE         = 18;
    private static final int MSG_SELECT_POSITION      = 19;
    private static final int MSG_SELECT_TRIBUTE       = 20;
    private static final int MSG_SELECT_COUNTER       = 22;
    private static final int MSG_SELECT_SUM           = 23;
    private static final int MSG_SELECT_DISFIELD      = 24;
    private static final int MSG_SORT_CARD            = 25;
    private static final int MSG_SELECT_UNSELECT_CARD = 26;
    private static final int MSG_SHUFFLE_HAND         = 33;
    private static final int MSG_NEW_TURN             = 40;
    private static final int MSG_NEW_PHASE            = 41;
    private static final int MSG_MOVE                 = 50;
    private static final int MSG_POS_CHANGE           = 53;
    private static final int MSG_SET                  = 54;
    private static final int MSG_SWAP                 = 55;
    private static final int MSG_SUMMONING            = 60;
    private static final int MSG_SPSUMMONING          = 62;
    private static final int MSG_FLIPSUMMONING        = 64;
    private static final int MSG_CHAINING             = 70;
    private static final int MSG_CHAIN_END            = 74;
    private static final int MSG_DRAW                 = 90;
    private static final int MSG_DAMAGE               = 91;
    private static final int MSG_RECOVER              = 92;
    private static final int MSG_LPUPDATE             = 94;
    private static final int MSG_PAY_LPCOST           = 100;
    private static final int MSG_ATTACK               = 110;
    private static final int MSG_ROCK_PAPER_SCISSORS  = 132;
    private static final int MSG_ANNOUNCE_RACE        = 140;
    private static final int MSG_ANNOUNCE_ATTRIB      = 141;
    private static final int MSG_ANNOUNCE_CARD        = 142;
    private static final int MSG_ANNOUNCE_NUMBER      = 143;

    private RpgOcgHeadlessDuelRunner() {}

    public static RpgAiAutoDuelModels.BatchResult runBatch(
            RpgCatalog catalog,
            String sideAName, List<Integer> mainA, List<Integer> extraA, String styleA,
            String sideBName, List<Integer> mainB, List<Integer> extraB, String styleB,
            OcgRuleSet ruleSet,
            String currentDate,
            int gameCount,
            RpgAiAutoDuelModels.ProgressListener progressListener,
            Random rng) {
        int total = Math.max(1, gameCount);
        Random random = rng != null ? rng : new Random();
        String effStyleA = (styleA != null && !styleA.isEmpty())
                ? styleA : RpgAiStrategy.inferStyleFromDeck(mainA, extraA, ruleSet);
        String effStyleB = (styleB != null && !styleB.isEmpty())
                ? styleB : RpgAiStrategy.inferStyleFromDeck(mainB, extraB, ruleSet);

        long t0 = System.currentTimeMillis();
        int winsA = 0, winsB = 0, draws = 0;
        int aFirstGames = 0, aFirstWins = 0;
        int aSecondGames = 0, aSecondWins = 0;
        long turnSum = 0;
        RpgAiAutoDuelModels.DuelResult lastResult = null;

        for (int i = 0; i < total; i++) {
            boolean aFirst = (i % 2 == 0);
            boolean recordReplay = (i == total - 1);
            RpgAiAutoDuelModels.DuelResult res = runSingleDuel(
                    catalog,
                    sideAName, mainA, extraA, effStyleA,
                    sideBName, mainB, extraB, effStyleB,
                    ruleSet, currentDate, aFirst, recordReplay, random);
            turnSum += res.turns;
            if (aFirst) aFirstGames++;
            else aSecondGames++;
            if (res.isDraw) {
                draws++;
            } else if (res.sideAWon) {
                winsA++;
                if (aFirst) aFirstWins++;
                else aSecondWins++;
            } else {
                winsB++;
            }
            if (recordReplay) lastResult = res;
            if (progressListener != null) {
                progressListener.onProgress(i + 1, total, winsA, winsB, draws);
            }
        }
        long elapsed = System.currentTimeMillis() - t0;
        double avgTurns = total > 0 ? (turnSum * 1.0 / total) : 0.0;
        return new RpgAiAutoDuelModels.BatchResult(
                sideAName, sideBName, effStyleA, effStyleB,
                currentDate, ruleSet,
                total, winsA, winsB, draws,
                aFirstGames, aFirstWins, aSecondGames, aSecondWins,
                avgTurns, elapsed, lastResult);
    }

    public static RpgAiAutoDuelModels.DuelResult runSingleDuel(
            RpgCatalog catalog,
            String sideAName, List<Integer> mainA, List<Integer> extraA, String styleA,
            String sideBName, List<Integer> mainB, List<Integer> extraB, String styleB,
            OcgRuleSet ruleSet,
            String currentDate,
            boolean sideAFirst,
            boolean recordReplay,
            Random rng) {
        Random random = rng != null ? rng : new Random();
        String effStyleA = (styleA != null && !styleA.isEmpty())
                ? styleA : RpgAiStrategy.inferStyleFromDeck(mainA, extraA, ruleSet);
        String effStyleB = (styleB != null && !styleB.isEmpty())
                ? styleB : RpgAiStrategy.inferStyleFromDeck(mainB, extraB, ruleSet);

        List<String> logs = new ArrayList<>();
        List<RpgAiAutoDuelModels.ReplayFrame> frames = recordReplay ? new ArrayList<>() : null;

        synchronized (ENGINE_LOCK) {
            if (!OcgDuelEngine.isAvailable() || !NativeScriptBootstrap.ensureEngineReady()) {
                logs.add("ocgcore 决斗引擎尚未就绪，无法运行真实对局。");
                return new RpgAiAutoDuelModels.DuelResult(
                        false, true, 8000, 8000, 0, ruleSet, logs,
                        sideAName, sideBName, effStyleA, effStyleB, frames);
            }

            int startLp = ruleSet != null ? ruleSet.startLp : 8000;
            int startHand = ruleSet != null ? ruleSet.startHand : 5;
            int duelRule = ruleSet != null ? ruleSet.ygoproDuelRule : 1;

            int[] seed = new int[8];
            for (int i = 0; i < 8; i++) seed[i] = random.nextInt();

            long pduel = OcgDuelEngine.createDuelV2(seed);
            if (pduel == 0L) {
                logs.add("创建 ocgcore 决斗实例失败。");
                return new RpgAiAutoDuelModels.DuelResult(
                        false, true, startLp, startLp, 0, ruleSet, logs,
                        sideAName, sideBName, effStyleA, effStyleB, frames);
            }

            try {
                OcgDuelEngine.setPlayerInfo(pduel, 0, startLp, startHand, 1);
                OcgDuelEngine.setPlayerInfo(pduel, 1, startLp, startHand, 1);

                // Seat 0 = first player, Seat 1 = second player
                List<Integer> p0Main = new ArrayList<>(sideAFirst ? safeList(mainA) : safeList(mainB));
                List<Integer> p0Extra = new ArrayList<>(sideAFirst ? safeList(extraA) : safeList(extraB));
                List<Integer> p1Main = new ArrayList<>(sideAFirst ? safeList(mainB) : safeList(mainA));
                List<Integer> p1Extra = new ArrayList<>(sideAFirst ? safeList(extraB) : safeList(extraA));
                Collections.shuffle(p0Main, random);
                Collections.shuffle(p1Main, random);

                String[] names = new String[]{
                        sideAFirst ? sideAName : sideBName,
                        sideAFirst ? sideBName : sideAName
                };
                int opt = (duelRule << 16);

                YrpWriter yrp = null;
                if (recordReplay) {
                    int startTime = (int) (System.currentTimeMillis() / 1000L);
                    yrp = new YrpWriter(seed, Constants.PRO_VERSION, YrpWriter.REPLAY_UNIFORM, startTime);
                    yrp.writeName(names[0] != null ? names[0] : "Player1");
                    yrp.writeName(names[1] != null ? names[1] : "Player2");
                    yrp.writeInt32(startLp);
                    yrp.writeInt32(startHand);
                    yrp.writeInt32(1);
                    yrp.writeInt32(opt);
                }

                loadZone(pduel, p0Main, 0, OcgDuelEngine.LOCATION_DECK, yrp);
                loadZone(pduel, p0Extra, 0, OcgDuelEngine.LOCATION_EXTRA, yrp);
                loadZone(pduel, p1Main, 1, OcgDuelEngine.LOCATION_DECK, yrp);
                loadZone(pduel, p1Extra, 1, OcgDuelEngine.LOCATION_EXTRA, yrp);

                // Inject exact 1999 historical OCG Lua rule overrides
                if (ruleSet != null && RpgIntegration.writeRuleOverrideScript(ruleSet)) {
                    OcgDuelEngine.preloadScript(pduel, "./rpg_rule_override.lua");
                }

                if (yrp != null) {
                    byte[] startBuf = new byte[19];
                    ByteBuffer sb = ByteBuffer.wrap(startBuf).order(ByteOrder.LITTLE_ENDIAN);
                    sb.put((byte) EngineMessage.MSG_START);
                    sb.put((byte) 0);
                    sb.put((byte) duelRule);
                    sb.putInt(startLp);
                    sb.putInt(startLp);
                    sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 0, OcgDuelEngine.LOCATION_DECK));
                    sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 0, OcgDuelEngine.LOCATION_EXTRA));
                    sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 1, OcgDuelEngine.LOCATION_DECK));
                    sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 1, OcgDuelEngine.LOCATION_EXTRA));
                    yrp.writeMessage(startBuf, startBuf.length);
                }

                OcgDuelEngine.startDuel(pduel, opt);

                RpgAiDuelState[] states = new RpgAiDuelState[]{
                        new RpgAiDuelState(catalog, ruleSet, currentDate),
                        new RpgAiDuelState(catalog, ruleSet, currentDate)
                };
                RpgAiStrategy[] strategies = new RpgAiStrategy[]{
                        new RpgAiStrategy(states[0], null, sideAFirst ? effStyleA : effStyleB),
                        new RpgAiStrategy(states[1], null, sideAFirst ? effStyleB : effStyleA)
                };

                // Initialize both AI states with MSG_START
                initStateStart(states[0], 0, duelRule, startLp, p0Main.size(), p0Extra.size(), p1Main.size(), p1Extra.size());
                initStateStart(states[1], 1, duelRule, startLp, p0Main.size(), p0Extra.size(), p1Main.size(), p1Extra.size());

                int pSideA = sideAFirst ? 0 : 1;
                int pSideB = 1 - pSideA;

                if (recordReplay) {
                    logs.add("【真实 ocgcore 引擎】" + (ruleSet != null ? ruleSet.name : "OCG 1999"));
                    logs.add("先攻：" + names[0] + "（" + RpgAiStrategy.getStyleDisplayName(strategies[0].getStyle())
                            + "） vs 后攻：" + names[1] + "（" + RpgAiStrategy.getStyleDisplayName(strategies[1].getStyle()) + "）");
                }

                ReplayMessageSlicer.ZoneBlocks zones = (player, loc) ->
                        OcgDuelEngine.queryFieldCount(pduel, player, loc);

                int winnerProto = -1; // 0, 1, or 2 (draw)
                int winReason = 0;
                boolean recordedWinFrame = false;
                int turnCount = 0;
                int curTurnProto = 0;
                String curPhaseLabel = "开局";
                int lastLogCursor = 0;
                int lastYrpRespLen = 0;
                byte[][] lastQuestion = new byte[2][];
                int[] repeatCount = new int[2];

                for (int step = 0; step < MAX_PROCESS_STEPS && winnerProto < 0; step++) {
                    int result = OcgDuelEngine.process(pduel);
                    int len = result & OcgDuelEngine.PROCESSOR_BUFFER_LEN;
                    int flag = result & OcgDuelEngine.PROCESSOR_FLAG;

                    if (len > 0) {
                        byte[] raw = OcgDuelEngine.getMessage(pduel);
                        if (raw != null && raw.length > 0) {
                            ByteBuffer stream = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                            while (stream.hasRemaining() && winnerProto < 0) {
                                int msgType = stream.get() & 0xFF;
                                int bodyStart = stream.position();
                                if (!ReplayMessageSlicer.slice(msgType, stream, zones)) {
                                    break;
                                }
                                int bodyEnd = stream.position();
                                byte[] body = Arrays.copyOfRange(raw, bodyStart, bodyEnd);

                                if (msgType == MSG_WIN) {
                                    winnerProto = body.length >= 1 ? (body[0] & 0xFF) : 2;
                                    winReason = body.length >= 2 ? (body[1] & 0xFF) : 0;
                                    if (yrp != null) {
                                        byte[] winFrame = buildFullFrame(msgType, body);
                                        yrp.writeMessage(winFrame, winFrame.length);
                                        recordedWinFrame = true;
                                    }
                                    if (recordReplay) {
                                        String winText = (winnerProto <= 1)
                                                ? (names[winnerProto] + " 获胜（" + describeWinReason(winReason) + "）！")
                                                : "双方平局！";
                                        logs.add("T" + turnCount + " " + winText);
                                        lastLogCursor = captureFrame(frames, logs, lastLogCursor,
                                                turnCount, "决胜", names[curTurnProto & 1],
                                                states[pSideA], states[pSideB], true);
                                    }
                                    break;
                                }

                                if (msgType == MSG_NEW_TURN && body.length >= 1) {
                                    curTurnProto = body[0] & 1;
                                    turnCount++;
                                    if (turnCount > MAX_TURNS) {
                                        // Safety cap for stall stalemates
                                        int lpA = states[pSideA].lp[0];
                                        int lpB = states[pSideB].lp[0];
                                        winnerProto = (lpA == lpB) ? 2 : (lpA > lpB ? pSideA : pSideB);
                                        if (recordReplay) logs.add("达到最大回合上限（" + MAX_TURNS + "），按剩余 LP 裁定胜负。");
                                        break;
                                    }
                                    if (yrp != null) {
                                        recordAllZonesToYrp(pduel, yrp);
                                    }
                                    if (recordReplay) {
                                        logs.add("━━ 第 " + turnCount + " 回合 · " + names[curTurnProto] + " ━━");
                                    }
                                } else if (msgType == MSG_NEW_PHASE && body.length >= 2) {
                                    int ph = (body[0] & 0xFF) | ((body[1] & 0xFF) << 8);
                                    curPhaseLabel = phaseLabel(ph);
                                    if (recordReplay && (ph == RpgAiDuelState.PHASE_BATTLE_START
                                            || ph == RpgAiDuelState.PHASE_MAIN2
                                            || ph == RpgAiDuelState.PHASE_END)) {
                                        refreshAllVisibleForPlayer(pduel, states[pSideA], pSideA);
                                        refreshAllVisibleForPlayer(pduel, states[pSideB], pSideB);
                                        lastLogCursor = captureFrame(frames, logs, lastLogCursor,
                                                turnCount, curPhaseLabel, names[curTurnProto],
                                                states[pSideA], states[pSideB], false);
                                    }
                                }

                                // Record authoritative game frame to V2 .yrp MSG stream for real 3D replay
                                if (yrp != null && !ReplayMessageSlicer.isNoFeed(msgType) && msgType != MSG_RETRY) {
                                    byte[] fullFrame = buildFullFrame(msgType, body);
                                    if (msgType == MSG_MOVE && fullFrame.length >= 13) {
                                        int cl = fullFrame[1 + 9] & 0xFF;
                                        if ((cl & EngineMessage.LOCATION_ONFIELD) != 0) {
                                            EngineMessage.stripRevealFlag(fullFrame, 1 + 8);
                                        }
                                    }
                                    yrp.writeMessage(fullFrame, fullFrame.length);
                                    if (msgType == MSG_NEW_PHASE
                                            || msgType == MSG_CHAIN_END
                                            || msgType == EngineMessage.MSG_SUMMONED
                                            || msgType == EngineMessage.MSG_SPSUMMONED
                                            || msgType == EngineMessage.MSG_FLIPSUMMONED
                                            || msgType == EngineMessage.MSG_EQUIP) {
                                        recordAllZonesToYrp(pduel, yrp);
                                    } else if (msgType == MSG_SHUFFLE_HAND && body.length >= 1) {
                                        recordZoneToYrp(pduel, yrp, body[0] & 1, OcgDuelEngine.LOCATION_HAND, 0x181fff);
                                    } else if (msgType == EngineMessage.MSG_SWAP_GRAVE_DECK && body.length >= 1) {
                                        recordZoneToYrp(pduel, yrp, body[0] & 1, OcgDuelEngine.LOCATION_GRAVE, 0x81fff);
                                    }
                                }

                                // Apply state updates to both AI views (fair hidden-card masking)
                                applyStateMessage(msgType, body, states);

                                // Record human-readable logs when capturing replay
                                if (recordReplay) {
                                    appendReplayLog(catalog, msgType, body, names, turnCount, logs);
                                }

                                // Handle decision prompts
                                if (isQuestionMessage(msgType)) {
                                    int responder = extractResponder(msgType, body);
                                    if (responder < 0 || responder > 1) responder = 0;

                                    if (msgType == MSG_SELECT_IDLECMD || msgType == MSG_SELECT_BATTLECMD) {
                                        refreshAllVisibleForPlayer(pduel, states[responder], responder);
                                    }

                                    byte[] questionSig = new byte[1 + body.length];
                                    questionSig[0] = (byte) msgType;
                                    System.arraycopy(body, 0, questionSig, 1, body.length);

                                    boolean useFallback = false;
                                    if (lastQuestion[responder] != null && Arrays.equals(lastQuestion[responder], questionSig)) {
                                        repeatCount[responder]++;
                                        useFallback = repeatCount[responder] >= 1;
                                    } else {
                                        lastQuestion[responder] = questionSig;
                                        repeatCount[responder] = 0;
                                    }

                                    byte[] resp = computeResponse(msgType, body, strategies[responder], random, useFallback);
                                    if (yrp != null) {
                                        lastYrpRespLen = yrp.writeResponse(resp, resp.length);
                                    }
                                    OcgDuelEngine.setResponseB(pduel, pad256(resp));
                                } else if (msgType == MSG_RETRY) {
                                    if (yrp != null && lastYrpRespLen > 0) {
                                        yrp.removeData(lastYrpRespLen);
                                        lastYrpRespLen = 0;
                                    }
                                    OcgDuelEngine.setResponseB(pduel, pad256(RpgAiStrategy.intBytes(-1)));
                                }
                            }
                        }
                    }

                    if (flag == OcgDuelEngine.PROCESSOR_END) {
                        break;
                    }
                }

                int lpA = states[pSideA].lp[0];
                int lpB = states[pSideB].lp[0];
                boolean isDraw;
                boolean sideAWon;
                if (winnerProto == 0 || winnerProto == 1) {
                    isDraw = false;
                    sideAWon = (winnerProto == pSideA);
                } else {
                    isDraw = true;
                    sideAWon = false;
                }
                // Respect 1999 Series 1 deck-out LP comparison if ocgcore ended by deck-out
                if (ruleSet != null && ruleSet.deckOutCompareLp && winReason == 2 && lpA != lpB) {
                    isDraw = false;
                    sideAWon = lpA > lpB;
                }

                String savedReplayPath = null;
                if (yrp != null) {
                    if (!recordedWinFrame) {
                        int finalWinProto = isDraw ? 2 : (sideAWon ? pSideA : pSideB);
                        byte[] synthWin = new byte[]{(byte) MSG_WIN, (byte) finalWinProto, (byte) winReason};
                        yrp.writeMessage(synthWin, synthWin.length);
                    }
                    savedReplayPath = saveYrpReplayFile(yrp);
                }

                if (recordReplay) {
                    captureFrame(frames, logs, lastLogCursor,
                            Math.max(1, turnCount), "结束", names[curTurnProto & 1],
                            states[pSideA], states[pSideB], true);
                }
                return new RpgAiAutoDuelModels.DuelResult(
                        sideAWon, isDraw, lpA, lpB, Math.max(1, turnCount),
                        ruleSet, logs, sideAName, sideBName, effStyleA, effStyleB, frames, savedReplayPath, sideAFirst);
            } catch (Throwable t) {
                Log.e(TAG, "Headless ocgcore duel error", t);
                logs.add("引擎异常：" + t.getMessage());
                return new RpgAiAutoDuelModels.DuelResult(
                        false, true, startLp, startLp, 0, ruleSet, logs,
                        sideAName, sideBName, effStyleA, effStyleB, frames, null, sideAFirst);
            } finally {
                OcgDuelEngine.endDuel(pduel);
            }
        }
    }

    private static final AtomicInteger sReplaySeq = new AtomicInteger(0);

    private static void loadZone(long pduel, List<Integer> cards, int player, int location, YrpWriter yrp) {
        if (cards == null) {
            if (yrp != null) yrp.writeInt32(0);
            return;
        }
        List<Integer> valid = new ArrayList<>();
        for (Integer code : cards) {
            if (code != null && code > 0) valid.add(code);
        }
        if (yrp != null) yrp.writeInt32(valid.size());
        for (int i = valid.size() - 1; i >= 0; i--) {
            int code = valid.get(i);
            OcgDuelEngine.newCard(pduel, code, player, player, location, 0, OcgDuelEngine.POS_FACEDOWN_DEFENSE);
            if (yrp != null) yrp.writeInt32(code);
        }
    }

    private static byte[] buildFullFrame(int msgType, byte[] body) {
        byte[] full = new byte[1 + body.length];
        full[0] = (byte) msgType;
        System.arraycopy(body, 0, full, 1, body.length);
        return full;
    }

    private static void recordAllZonesToYrp(long pduel, YrpWriter yrp) {
        recordZoneToYrp(pduel, yrp, 0, OcgDuelEngine.LOCATION_MZONE, REFRESH_MZONE_FLAG);
        recordZoneToYrp(pduel, yrp, 1, OcgDuelEngine.LOCATION_MZONE, REFRESH_MZONE_FLAG);
        recordZoneToYrp(pduel, yrp, 0, OcgDuelEngine.LOCATION_SZONE, REFRESH_SZONE_FLAG);
        recordZoneToYrp(pduel, yrp, 1, OcgDuelEngine.LOCATION_SZONE, REFRESH_SZONE_FLAG);
        recordZoneToYrp(pduel, yrp, 0, OcgDuelEngine.LOCATION_HAND, REFRESH_HAND_FLAG);
        recordZoneToYrp(pduel, yrp, 1, OcgDuelEngine.LOCATION_HAND, REFRESH_HAND_FLAG);
    }

    private static void recordZoneToYrp(long pduel, YrpWriter yrp, int player, int location, int flag) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel, player, location, flag, 0);
        if (blocks == null || blocks.length == 0) return;
        if (location == OcgDuelEngine.LOCATION_MZONE || location == OcgDuelEngine.LOCATION_SZONE) {
            int qpos = 0;
            while (qpos + 4 <= blocks.length) {
                int clen = (blocks[qpos] & 0xFF)
                        | ((blocks[qpos + 1] & 0xFF) << 8)
                        | ((blocks[qpos + 2] & 0xFF) << 16)
                        | ((blocks[qpos + 3] & 0xFF) << 24);
                qpos += 4;
                if (clen <= 4) continue;
                if (qpos + 12 <= blocks.length) {
                    EngineMessage.stripRevealFlag(blocks, qpos + 8);
                }
                qpos += clen - 4;
            }
        }
        byte[] frame = new byte[3 + blocks.length];
        frame[0] = (byte) EngineMessage.MSG_UPDATE_DATA;
        frame[1] = (byte) player;
        frame[2] = (byte) location;
        System.arraycopy(blocks, 0, frame, 3, blocks.length);
        yrp.writeMessage(frame, frame.length);
    }

    private static String saveYrpReplayFile(YrpWriter yrp) {
        try {
            byte[] yrpBytes = yrp.build();
            if (yrpBytes == null || yrpBytes.length == 0) return null;
            File dir = new File(AppsSettings.get().getReplayDir(), "RPG");
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            // Keep latest 25 RPG replay files to avoid unbounded disk growth
            File[] existing = dir.listFiles((d, n) -> n.endsWith(".yrp"));
            if (existing != null && existing.length >= 25) {
                Arrays.sort(existing, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < existing.length - 24; i++) {
                    //noinspection ResultOfMethodCallIgnored
                    existing[i].delete();
                }
            }
            String fileName = "rpg_" + System.currentTimeMillis() + "_" + sReplaySeq.incrementAndGet() + ".yrp";
            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(yrpBytes);
            }
            return outFile.getAbsolutePath();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to save RPG .yrp replay file", t);
            return null;
        }
    }

    private static void initStateStart(RpgAiDuelState st, int myProto, int duelRule, int lp,
                                       int d0, int e0, int d1, int e1) {
        ByteBuffer b = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) myProto).put((byte) duelRule)
                .putInt(lp).putInt(lp)
                .putShort((short) d0).putShort((short) e0)
                .putShort((short) d1).putShort((short) e1);
        b.flip();
        st.onStart(b);
    }

    /**
     * Refreshes MZone, SZone, and Hand directly from ocgcore for {@code responder} ONLY,
     * strictly masking the opponent's hand and opponent's face-down cards so AI never cheats.
     */
    private static void refreshAllVisibleForPlayer(long pduel, RpgAiDuelState st, int responderProto) {
        int oppProto = 1 - responderProto;
        feedQueryZone(pduel, st, responderProto, OcgDuelEngine.LOCATION_MZONE, REFRESH_MZONE_FLAG, false);
        feedQueryZone(pduel, st, oppProto,       OcgDuelEngine.LOCATION_MZONE, REFRESH_MZONE_FLAG, true);
        feedQueryZone(pduel, st, responderProto, OcgDuelEngine.LOCATION_SZONE, REFRESH_SZONE_FLAG, false);
        feedQueryZone(pduel, st, oppProto,       OcgDuelEngine.LOCATION_SZONE, REFRESH_SZONE_FLAG, true);
        feedQueryZone(pduel, st, responderProto, OcgDuelEngine.LOCATION_HAND,  REFRESH_HAND_FLAG,  false);
        feedQueryZone(pduel, st, oppProto,       OcgDuelEngine.LOCATION_HAND,  REFRESH_HAND_FLAG,  true);
    }

    private static void feedQueryZone(long pduel, RpgAiDuelState st, int targetProto,
                                      int location, int flag, boolean maskHidden) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel, targetProto, location, flag, 0);
        if (blocks == null || blocks.length == 0) return;
        byte[] copy = Arrays.copyOf(blocks, blocks.length);
        if (maskHidden) {
            maskHiddenQueryBlocks(copy, location == OcgDuelEngine.LOCATION_HAND);
        }
        ByteBuffer buf = ByteBuffer.allocate(2 + copy.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) targetProto);
        buf.put((byte) location);
        buf.put(copy);
        buf.flip();
        st.onUpdateData(buf);
    }

    private static void maskHiddenQueryBlocks(byte[] blocks, boolean isHand) {
        int pos = 0;
        while (pos + 4 <= blocks.length) {
            int clen = (blocks[pos] & 0xFF)
                    | ((blocks[pos + 1] & 0xFF) << 8)
                    | ((blocks[pos + 2] & 0xFF) << 16)
                    | ((blocks[pos + 3] & 0xFF) << 24);
            if (clen < 4 || pos + clen > blocks.length) break;
            if (clen > 8 && pos + 16 <= blocks.length) {
                int position = blocks[pos + 15] & 0xFF;
                boolean hide = isHand
                        ? ((position & 0x05) == 0)
                        : ((position & 0x0A) != 0 && (position & 0x80) == 0);
                if (hide) {
                    Arrays.fill(blocks, pos + 4, pos + clen, (byte) 0);
                }
            }
            pos += clen;
        }
    }

    private static void applyStateMessage(int msgType, byte[] body, RpgAiDuelState[] states) {
        for (int p = 0; p < 2; p++) {
            ByteBuffer b = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
            switch (msgType) {
                case MSG_HINT:
                    states[p].onHint(b);
                    break;
                case MSG_NEW_TURN:
                    states[p].onNewTurn(b);
                    break;
                case MSG_NEW_PHASE:
                    states[p].onNewPhase(b);
                    break;
                case MSG_DAMAGE:
                    states[p].onDamage(b);
                    break;
                case MSG_RECOVER:
                    states[p].onRecover(b);
                    break;
                case MSG_PAY_LPCOST:
                    states[p].onPayLpCost(b);
                    break;
                case MSG_LPUPDATE:
                    states[p].onLpUpdate(b);
                    break;
                case MSG_DRAW: {
                    // Mask drawn card codes for the opponent
                    if (body.length >= 2 && (body[0] & 1) != p) {
                        byte[] masked = Arrays.copyOf(body, body.length);
                        Arrays.fill(masked, 2, masked.length, (byte) 0);
                        states[p].onDraw(ByteBuffer.wrap(masked).order(ByteOrder.LITTLE_ENDIAN));
                    } else {
                        states[p].onDraw(b);
                    }
                    break;
                }
                case MSG_SHUFFLE_HAND: {
                    if (body.length >= 2 && (body[0] & 1) == p) {
                        states[p].onShuffleHand(b);
                    }
                    break;
                }
                case MSG_MOVE:
                    states[p].onMove(b);
                    break;
                case MSG_POS_CHANGE:
                    states[p].onPosChange(b);
                    break;
                case MSG_SET:
                    states[p].onSet(b);
                    break;
                case MSG_SWAP:
                    states[p].onSwap(b);
                    break;
                case MSG_SUMMONING:
                    states[p].onSummoning(b, true);
                    break;
                case MSG_SPSUMMONING:
                case MSG_FLIPSUMMONING:
                    states[p].onSummoning(b, false);
                    break;
                case MSG_CHAINING:
                    states[p].onChaining(b);
                    break;
                case MSG_CHAIN_END:
                    states[p].onChainEnd();
                    break;
                case MSG_ATTACK:
                    states[p].onAttack(b);
                    break;
                default:
                    break;
            }
        }
    }

    private static int extractResponder(int msgType, byte[] body) {
        if (body == null || body.length == 0) return 0;
        if (msgType == MSG_SELECT_SUM) {
            return body.length >= 2 ? (body[1] & 1) : 0;
        }
        return body[0] & 1;
    }

    private static boolean isQuestionMessage(int msgType) {
        return (msgType >= MSG_SELECT_BATTLECMD && msgType <= MSG_SELECT_UNSELECT_CARD)
                || msgType == MSG_ROCK_PAPER_SCISSORS
                || (msgType >= MSG_ANNOUNCE_RACE && msgType <= MSG_ANNOUNCE_NUMBER);
    }

    private static byte[] computeResponse(int msgType, byte[] body, RpgAiStrategy strategy,
                                          Random random, boolean useFallback) {
        ByteBuffer b = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        switch (msgType) {
            case MSG_SELECT_IDLECMD: {
                RpgAiStrategy.IdleCmdPrompt prompt = strategy.parseIdleCmd(b);
                int r = useFallback
                        ? (prompt.canEnd ? 7 : (prompt.canBattle ? 6 : 7))
                        : strategy.chooseIdleCmd(prompt);
                return RpgAiStrategy.intBytes(r);
            }
            case MSG_SELECT_BATTLECMD: {
                RpgAiStrategy.BattleCmdPrompt prompt = strategy.parseBattleCmd(b);
                int r = useFallback
                        ? (prompt.canEnd ? 3 : (prompt.canMain2 ? 2 : 3))
                        : strategy.chooseBattleCmd(prompt);
                return RpgAiStrategy.intBytes(r);
            }
            case MSG_SELECT_CHAIN:
                return RpgAiStrategy.intBytes(useFallback ? fallbackChain(body) : strategy.chooseChain(b));
            case MSG_SELECT_EFFECTYN:
                return RpgAiStrategy.intBytes(useFallback ? 0 : strategy.chooseEffectYn(b));
            case MSG_SELECT_YESNO:
                return RpgAiStrategy.intBytes(useFallback ? 0 : strategy.chooseYesNo(b));
            case MSG_SELECT_OPTION:
                return RpgAiStrategy.intBytes(strategy.chooseOption(b));
            case MSG_SELECT_CARD:
                return useFallback ? fallbackSelectCard(body) : strategy.chooseSelectCard(b);
            case MSG_SELECT_TRIBUTE:
                return useFallback ? fallbackSelectCard(body) : strategy.chooseSelectTribute(b);
            case MSG_SELECT_POSITION:
                return RpgAiStrategy.intBytes(strategy.choosePosition(b));
            case MSG_SELECT_PLACE:
            case MSG_SELECT_DISFIELD:
                return strategy.choosePlace(b);
            case MSG_SELECT_SUM:
                return strategy.chooseSelectSum(b);
            case MSG_SELECT_UNSELECT_CARD:
                return strategy.chooseUnselectCard(b);
            case MSG_SORT_CARD:
                return strategy.chooseSortCard(b);
            case MSG_SELECT_COUNTER:
                return strategy.chooseCounter(b);
            case MSG_ROCK_PAPER_SCISSORS:
                return RpgAiStrategy.intBytes(1 + random.nextInt(3));
            case MSG_ANNOUNCE_RACE:
            case MSG_ANNOUNCE_ATTRIB:
                return RpgAiStrategy.intBytes(strategy.chooseAnnounceBitmask(b));
            case MSG_ANNOUNCE_NUMBER:
                return RpgAiStrategy.intBytes(strategy.chooseAnnounceNumber(b));
            case MSG_ANNOUNCE_CARD:
                return RpgAiStrategy.intBytes(strategy.chooseAnnounceCard(b));
            default:
                return RpgAiStrategy.intBytes(0);
        }
    }

    private static int fallbackChain(byte[] body) {
        if (body.length < 11) return -1;
        int count = body[1] & 0xFF;
        int off = 11;
        for (int i = 0; i < count && off + 14 <= body.length; i++) {
            if ((body[off + 1] & 0xFF) != 0) return i;
            off += 14;
        }
        return -1;
    }

    private static byte[] fallbackSelectCard(byte[] body) {
        if (body.length < 5) return RpgAiStrategy.intBytes(0);
        boolean cancelable = (body[1] & 0xFF) != 0;
        int min = body[2] & 0xFF;
        int count = body[4] & 0xFF;
        if (cancelable) return RpgAiStrategy.intBytes(-1);
        int take = Math.min(min, count);
        byte[] resp = new byte[1 + take];
        resp[0] = (byte) take;
        for (int i = 0; i < take; i++) resp[1 + i] = (byte) i;
        return resp;
    }

    private static byte[] pad256(byte[] src) {
        byte[] out = new byte[RESPONSE_BUF_LEN];
        if (src != null) {
            System.arraycopy(src, 0, out, 0, Math.min(src.length, RESPONSE_BUF_LEN - 1));
        }
        return out;
    }

    private static void appendReplayLog(RpgCatalog catalog, int msgType, byte[] body,
                                        String[] names, int turn, List<String> logs) {
        if (logs.size() > 160) return;
        ByteBuffer b = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        switch (msgType) {
            case MSG_SUMMONING:
            case MSG_SPSUMMONING:
            case MSG_FLIPSUMMONING: {
                if (body.length < 8) return;
                int code = b.getInt();
                int p = b.get() & 1;
                String verb = msgType == MSG_SUMMONING ? "通常召唤"
                        : (msgType == MSG_SPSUMMONING ? "特殊召唤" : "反转召唤");
                logs.add("T" + turn + " " + names[p] + " " + verb + "「" + cardName(catalog, code) + "」");
                break;
            }
            case MSG_CHAINING: {
                if (body.length < 16) return;
                int code = b.getInt();
                b.position(9); // cc
                int p = b.get() & 1;
                logs.add("T" + turn + " " + names[p] + " 发动「" + cardName(catalog, code) + "」");
                break;
            }
            case MSG_DAMAGE: {
                if (body.length < 5) return;
                int p = b.get() & 1;
                int amt = b.getInt();
                logs.add("T" + turn + " " + names[p] + " 受到 " + amt + " 点伤害");
                break;
            }
            case MSG_RECOVER: {
                if (body.length < 5) return;
                int p = b.get() & 1;
                int amt = b.getInt();
                logs.add("T" + turn + " " + names[p] + " 回复 " + amt + " 点基本分");
                break;
            }
            case MSG_PAY_LPCOST: {
                if (body.length < 5) return;
                int p = b.get() & 1;
                int amt = b.getInt();
                logs.add("T" + turn + " " + names[p] + " 支付 " + amt + " 基本分");
                break;
            }
            default:
                break;
        }
    }

    private static int captureFrame(List<RpgAiAutoDuelModels.ReplayFrame> frames,
                                    List<String> logs, int lastLogCursor,
                                    int turn, String phase, String activeName,
                                    RpgAiDuelState stA, RpgAiDuelState stB, boolean force) {
        if (frames == null) return logs.size();
        if (!force && logs.size() <= lastLogCursor) return lastLogCursor;
        List<String> delta = new ArrayList<>();
        for (int i = lastLogCursor; i < logs.size(); i++) delta.add(logs.get(i));
        frames.add(new RpgAiAutoDuelModels.ReplayFrame(
                frames.size(), turn, phase, activeName,
                stA.lp[0], stB.lp[0],
                stA.hand[0].size(), stB.hand[0].size(),
                stA.deckCount[0], stB.deckCount[0],
                describeMonsters(stA), describeMonsters(stB),
                delta));
        return logs.size();
    }

    private static List<String> describeMonsters(RpgAiDuelState st) {
        List<String> out = new ArrayList<>();
        for (RpgAiDuelState.CardSlot m : st.getMonsters(0)) {
            if (m.isFaceDown()) {
                out.add("里侧守备(" + cardName(st.catalog, m.code) + ")");
            } else {
                String stat = m.isAttackPos() ? ("攻" + m.atk) : ("守" + m.def);
                out.add(cardName(st.catalog, m.code) + "(" + stat + ")");
            }
        }
        return out;
    }

    private static String cardName(RpgCatalog catalog, int code) {
        if (catalog != null && code > 0) {
            RpgCard c = catalog.findCard(code);
            if (c != null) return c.getDisplayName();
        }
        return "#" + code;
    }

    private static String phaseLabel(int ph) {
        switch (ph) {
            case RpgAiDuelState.PHASE_DRAW:         return "抽卡阶段";
            case RpgAiDuelState.PHASE_STANDBY:      return "准备阶段";
            case RpgAiDuelState.PHASE_MAIN1:        return "主阶段1";
            case RpgAiDuelState.PHASE_BATTLE_START:
            case RpgAiDuelState.PHASE_BATTLE_STEP:
            case RpgAiDuelState.PHASE_BATTLE:       return "战斗阶段";
            case RpgAiDuelState.PHASE_MAIN2:        return "主阶段2";
            case RpgAiDuelState.PHASE_END:          return "结束阶段";
            default:                                return "进行中";
        }
    }

    private static String describeWinReason(int reason) {
        switch (reason) {
            case 1: return "基本分归零";
            case 2: return "卡组抽干";
            case 16: return "艾克佐迪亚特殊胜利";
            default: return "特殊胜利/裁定";
        }
    }

    private static List<Integer> safeList(List<Integer> list) {
        return list != null ? list : Collections.emptyList();
    }
}
