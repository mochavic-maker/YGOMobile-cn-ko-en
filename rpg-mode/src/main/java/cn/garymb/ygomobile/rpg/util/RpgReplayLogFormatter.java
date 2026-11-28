package cn.garymb.ygomobile.rpg.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels;
import cn.garymb.ygomobile.rpg.ai.RpgAiDuelState;
import cn.garymb.ygomobile.rpg.ai.RpgAiStrategy;
import cn.garymb.ygomobile.rpg.engine.AiDeckGenerator;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Formats a recorded duel replay (.yrp + optional .rpgmeta.json sidecar) into a concise,
 * structured text log containing both players' control mode / AI strategy, full Main & Extra
 * decklists, and turn-by-turn duel action log — designed for one-tap clipboard export when
 * auditing and tuning {@link RpgAiStrategy}.
 */
public final class RpgReplayLogFormatter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SIDECAR_SUFFIX = ".rpgmeta.json";

    // Engine message opcodes (matching ocgcore / EngineMessage)
    public static final int MSG_START = 4;
    public static final int MSG_WIN = 5;
    public static final int MSG_UPDATE_DATA = 6;
    public static final int MSG_NEW_TURN = 40;
    public static final int MSG_NEW_PHASE = 41;
    public static final int MSG_MOVE = 50;
    public static final int MSG_POS_CHANGE = 53;
    public static final int MSG_SET = 54;
    public static final int MSG_SWAP = 55;
    public static final int MSG_SUMMONING = 60;
    public static final int MSG_SPSUMMONING = 62;
    public static final int MSG_FLIPSUMMONING = 64;
    public static final int MSG_CHAINING = 70;
    public static final int MSG_CHAIN_END = 74;
    public static final int MSG_CHAIN_NEGATED = 75;
    public static final int MSG_CHAIN_DISABLED = 76;
    public static final int MSG_DRAW = 90;
    public static final int MSG_DAMAGE = 91;
    public static final int MSG_RECOVER = 92;
    public static final int MSG_EQUIP = 93;
    public static final int MSG_LPUPDATE = 94;
    public static final int MSG_PAY_LPCOST = 100;
    public static final int MSG_ATTACK = 110;
    public static final int MSG_BATTLE = 111;
    public static final int MSG_ATTACK_DISABLED = 112;
    public static final int MSG_TOSS_COIN = 130;

    // ocgcore move reason bitflags
    private static final int REASON_DESTROY = 0x1;
    private static final int REASON_RELEASE = 0x2;
    private static final int REASON_MATERIAL = 0x8;
    private static final int REASON_BATTLE = 0x20;
    private static final int REASON_EFFECT = 0x40;
    private static final int REASON_COST = 0x80;
    private static final int REASON_RULE = 0x400;
    private static final int REASON_DISCARD = 0x4000;

    private RpgReplayLogFormatter() {}

    public interface CardNameResolver {
        String resolveName(int code);
    }

    /**
     * Sidecar metadata saved beside a .yrp replay file so that control modes
     * (Manual vs specific RPG AI Strategy) and OCG timeline rule context survive
     * after the duel finishes.
     */
    public static class ReplayMeta {
        public String source = "";
        public String timelineDate = "";
        public String ruleName = "";
        public Map<String, String> playerControls = new LinkedHashMap<>();

        public void putPlayerControl(String playerName, String controlLabel) {
            if (playerName == null || playerName.trim().isEmpty()) return;
            if (playerControls == null) playerControls = new LinkedHashMap<>();
            playerControls.put(playerName.trim(), controlLabel != null ? controlLabel : "");
        }

        public String findControlForPlayer(String playerName) {
            if (playerControls == null || playerName == null) return null;
            String trimmed = playerName.trim();
            String exact = playerControls.get(trimmed);
            if (exact != null && !exact.isEmpty()) return exact;
            for (Map.Entry<String, String> e : playerControls.entrySet()) {
                if (e.getKey() != null && (trimmed.startsWith(e.getKey()) || e.getKey().startsWith(trimmed))) {
                    if (e.getValue() != null && !e.getValue().isEmpty()) {
                        return e.getValue();
                    }
                }
            }
            return null;
        }
    }

    public static String formatAiStrategyLabel(String styleCode) {
        String code = (styleCode == null || styleCode.trim().isEmpty()) ? "GOOD_STUFF" : styleCode.trim();
        return "AI · " + RpgAiStrategy.getStyleDisplayName(code) + " (" + code + ")";
    }

    public static File sidecarFileFor(File yrpFile) {
        if (yrpFile == null) return null;
        return new File(yrpFile.getParentFile(), yrpFile.getName() + SIDECAR_SUFFIX);
    }

    public static boolean saveSidecar(File yrpFile, ReplayMeta meta) {
        if (yrpFile == null || meta == null) return false;
        File sidecar = sidecarFileFor(yrpFile);
        try (OutputStreamWriter osw = new OutputStreamWriter(
                new FileOutputStream(sidecar), StandardCharsets.UTF_8)) {
            GSON.toJson(meta, osw);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ReplayMeta loadSidecar(File yrpFile) {
        if (yrpFile == null) return null;
        File sidecar = sidecarFileFor(yrpFile);
        if (sidecar == null || !sidecar.exists() || sidecar.length() <= 0L) return null;
        try (InputStreamReader isr = new InputStreamReader(
                new FileInputStream(sidecar), StandardCharsets.UTF_8)) {
            return GSON.fromJson(isr, ReplayMeta.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void deleteSidecar(File yrpFile) {
        File sidecar = sidecarFileFor(yrpFile);
        if (sidecar != null && sidecar.exists()) {
            //noinspection ResultOfMethodCallIgnored
            sidecar.delete();
        }
    }

    public static void renameSidecar(File oldYrpFile, File newYrpFile) {
        File oldSidecar = sidecarFileFor(oldYrpFile);
        File newSidecar = sidecarFileFor(newYrpFile);
        if (oldSidecar != null && oldSidecar.exists() && newSidecar != null) {
            //noinspection ResultOfMethodCallIgnored
            oldSidecar.renameTo(newSidecar);
        }
    }

    public static CardNameResolver catalogResolver(final RpgCatalog catalog) {
        return code -> {
            if (catalog != null && code > 0) {
                RpgCard c = catalog.findCard(code);
                if (c != null) return c.getDisplayName();
            }
            return "#" + code;
        };
    }

    /**
     * Formats complete replay information (meta, strategies, decks, and detailed turn-by-turn
     * engine action log) into clipboard-ready text.
     */
    public static String formatReplay(String fileName,
                                      long timestampMs,
                                      List<String> playerNames,
                                      int startLp,
                                      int startHand,
                                      int drawCount,
                                      List<List<Integer>> mainDecks,
                                      List<List<Integer>> extraDecks,
                                      List<byte[]> msgFrames,
                                      ReplayMeta meta,
                                      CardNameResolver resolver) {
        CardNameResolver nameFn = resolver != null ? resolver : code -> "#" + code;
        String p0 = (playerNames != null && playerNames.size() > 0 && playerNames.get(0) != null && !playerNames.get(0).isEmpty())
                ? playerNames.get(0) : "先攻玩家";
        String p1 = (playerNames != null && playerNames.size() > 1 && playerNames.get(1) != null && !playerNames.get(1).isEmpty())
                ? playerNames.get(1) : "后攻玩家";
        String[] names = new String[]{p0, p1};

        String ctrl0 = resolveControlLabel(p0, meta);
        String ctrl1 = resolveControlLabel(p1, meta);

        StringBuilder sb = new StringBuilder(4096);
        sb.append("========== YGOMobile 对战回放日志 ==========\n");
        if (fileName != null && !fileName.isEmpty()) {
            sb.append("录像文件：").append(fileName);
            if (timestampMs > 0L) {
                sb.append("（").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(new Date(timestampMs))).append("）");
            }
            sb.append("\n");
        }
        if (meta != null && meta.source != null && !meta.source.isEmpty()) {
            sb.append("对局来源：").append(describeSource(meta.source)).append("\n");
        }
        if (meta != null && meta.timelineDate != null && !meta.timelineDate.isEmpty()) {
            sb.append("环境时间：").append(meta.timelineDate);
            if (meta.ruleName != null && !meta.ruleName.isEmpty()) {
                sb.append(" · ").append(meta.ruleName);
            }
            sb.append("\n");
        }
        sb.append("规则参数：起始LP ").append(startLp > 0 ? startLp : 8000)
                .append(" | 起始手牌 ").append(startHand > 0 ? startHand : 5)
                .append(" | 每回合抽卡 ").append(drawCount > 0 ? drawCount : 1).append("\n");
        sb.append("先攻(P0)：").append(p0).append(" — ").append(ctrl0).append("\n");
        sb.append("后攻(P1)：").append(p1).append(" — ").append(ctrl1).append("\n");

        // Decklists
        sb.append("\n【双方卡组】\n");
        appendDeckBlock(sb, "先攻 " + p0,
                mainDecks != null && mainDecks.size() > 0 ? mainDecks.get(0) : null,
                extraDecks != null && extraDecks.size() > 0 ? extraDecks.get(0) : null,
                nameFn);
        appendDeckBlock(sb, "后攻 " + p1,
                mainDecks != null && mainDecks.size() > 1 ? mainDecks.get(1) : null,
                extraDecks != null && extraDecks.size() > 1 ? extraDecks.get(1) : null,
                nameFn);

        // Detailed operation log decoded from V2 msgFrames
        sb.append("\n【对战操作日志】\n");
        if (msgFrames == null || msgFrames.isEmpty()) {
            sb.append("（该录像为旧格式，不含逐帧操作消息流，无法还原每步操作；新录制的对局均支持完整操作日志）\n");
        } else {
            appendActionLogFromFrames(sb, names, startLp > 0 ? startLp : 8000, msgFrames, nameFn);
        }
        sb.append("============================================");
        return sb.toString();
    }

    /**
     * Fallback formatter when a headless {@link RpgAiAutoDuelModels.DuelResult} is available
     * but its .yrp file was not recorded or has already been pruned.
     */
    public static String formatFallbackFromDuelResult(RpgAiAutoDuelModels.DuelResult report,
                                                      String timelineDate,
                                                      List<Integer> mainA, List<Integer> extraA,
                                                      List<Integer> mainB, List<Integer> extraB,
                                                      RpgCatalog catalog) {
        if (report == null) return "";
        CardNameResolver nameFn = catalogResolver(catalog);
        String p0 = report.sideAFirst ? report.sideAName : report.sideBName;
        String p1 = report.sideAFirst ? report.sideBName : report.sideAName;
        String st0 = report.sideAFirst ? report.sideAStyle : report.sideBStyle;
        String st1 = report.sideAFirst ? report.sideBStyle : report.sideAStyle;
        List<Integer> m0 = report.sideAFirst ? mainA : mainB;
        List<Integer> e0 = report.sideAFirst ? extraA : extraB;
        List<Integer> m1 = report.sideAFirst ? mainB : mainA;
        List<Integer> e1 = report.sideAFirst ? extraB : extraA;

        StringBuilder sb = new StringBuilder(2048);
        sb.append("========== YGOMobile 对战回放日志 ==========\n");
        sb.append("对局来源：RPG AI 托管实战（摘要战报）\n");
        if (timelineDate != null && !timelineDate.isEmpty()) {
            sb.append("环境时间：").append(timelineDate);
            if (report.appliedRule != null && report.appliedRule.name != null) {
                sb.append(" · ").append(report.appliedRule.name);
            }
            sb.append("\n");
        }
        sb.append("先攻(P0)：").append(p0).append(" — ").append(formatAiStrategyLabel(st0)).append("\n");
        sb.append("后攻(P1)：").append(p1).append(" — ").append(formatAiStrategyLabel(st1)).append("\n");
        sb.append("终局结果：")
                .append(report.isDraw ? "平局" : ((report.sideAWon ? report.sideAName : report.sideBName) + " 胜"))
                .append(" · 共 ").append(report.turns).append(" 回合 · LP ")
                .append(report.sideAName).append(" ").append(report.sideALp)
                .append(" : ").append(report.sideBLp).append(" ").append(report.sideBName).append("\n");

        if ((m0 != null && !m0.isEmpty()) || (m1 != null && !m1.isEmpty())) {
            sb.append("\n【双方卡组】\n");
            appendDeckBlock(sb, "先攻 " + p0, m0, e0, nameFn);
            appendDeckBlock(sb, "后攻 " + p1, m1, e1, nameFn);
        }

        sb.append("\n【对战操作日志】\n");
        if (report.logs != null && !report.logs.isEmpty()) {
            for (String line : report.logs) {
                sb.append(line).append("\n");
            }
        } else {
            sb.append("（无详细日志）\n");
        }
        sb.append("============================================");
        return sb.toString();
    }

    private static String resolveControlLabel(String playerName, ReplayMeta meta) {
        if (meta != null) {
            String fromMeta = meta.findControlForPlayer(playerName);
            if (fromMeta != null && !fromMeta.isEmpty()) {
                return fromMeta;
            }
        }
        if (playerName != null && !playerName.trim().isEmpty()) {
            String trimmed = playerName.trim();
            for (AiOpponent opp : AiDeckGenerator.getDemoOpponents()) {
                if (opp != null && opp.name != null && !opp.name.isEmpty()
                        && (trimmed.equals(opp.name) || trimmed.startsWith(opp.name))) {
                    return "AI（未记录具体策略 · " + opp.name + "）";
                }
            }
        }
        return "手动 / 未记录策略";
    }

    private static String describeSource(String source) {
        switch (source) {
            case "RPG_MANUAL":   return "RPG 模式 · 玩家手动对战 AI";
            case "RPG_CANON":    return "RPG 模式 · 原著对决还原（手动）";
            case "RPG_SPECTATE": return "RPG 模式 · 3D AI 观战";
            case "RPG_HEADLESS": return "RPG 模式 · AI 托管 / 样本对局";
            default:             return source;
        }
    }

    private static void appendDeckBlock(StringBuilder sb, String title,
                                        List<Integer> main, List<Integer> extra,
                                        CardNameResolver resolver) {
        int mainSize = main != null ? main.size() : 0;
        int extraSize = extra != null ? extra.size() : 0;
        sb.append("[").append(title).append("] 主卡组(").append(mainSize)
                .append(") / 额外(").append(extraSize).append(")\n");
        sb.append("  主卡组：").append(formatCardListGrouped(main, resolver)).append("\n");
        if (extraSize > 0) {
            sb.append("  额外：").append(formatCardListGrouped(extra, resolver)).append("\n");
        }
    }

    private static String formatCardListGrouped(List<Integer> cards, CardNameResolver resolver) {
        if (cards == null || cards.isEmpty()) return "（无）";
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Integer idObj : cards) {
            if (idObj == null || idObj <= 0) continue;
            counts.put(idObj, (counts.containsKey(idObj) ? counts.get(idObj) : 0) + 1);
        }
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(counts.entrySet());
        Collections.sort(entries, (a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getKey(), b.getKey());
        });
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) out.append(", ");
            int code = entries.get(i).getKey();
            int cnt = entries.get(i).getValue();
            out.append(resolver.resolveName(code));
            if (cnt > 1) out.append("×").append(cnt);
        }
        return out.toString();
    }

    private static final class TrackedSlot {
        int code;
        int pos;
        int atk;
        int def;
    }

    private static void appendActionLogFromFrames(StringBuilder sb,
                                                  String[] names,
                                                  int startLp,
                                                  List<byte[]> frames,
                                                  CardNameResolver resolver) {
        int[] lp = new int[]{startLp, startLp};
        TrackedSlot[][] mzone = new TrackedSlot[2][7];
        TrackedSlot[][] szone = new TrackedSlot[2][8];
        int turn = 0;
        String phaseTag = "开局";
        int chainCount = 0;
        boolean sawWin = false;

        for (byte[] frame : frames) {
            if (frame == null || frame.length == 0) continue;
            int msgType = frame[0] & 0xFF;
            if (frame.length == 1 && msgType != MSG_CHAIN_END && msgType != MSG_ATTACK_DISABLED) {
                continue;
            }
            ByteBuffer b = ByteBuffer.wrap(frame, 1, frame.length - 1).order(ByteOrder.LITTLE_ENDIAN);

            switch (msgType) {
                case MSG_START: {
                    if (b.remaining() >= 10) {
                        b.get(); // playerType
                        b.get(); // duelRule
                        lp[0] = b.getInt();
                        lp[1] = b.getInt();
                    }
                    break;
                }
                case MSG_NEW_TURN: {
                    if (b.remaining() >= 1) {
                        int tp = b.get() & 1;
                        turn++;
                        phaseTag = "DP";
                        chainCount = 0;
                        sb.append("━━ T").append(turn).append(" · ").append(names[tp])
                                .append(" (LP ").append(lp[0]).append(":").append(lp[1]).append(") ━━\n");
                    }
                    break;
                }
                case MSG_NEW_PHASE: {
                    if (b.remaining() >= 2) {
                        int ph = b.getShort() & 0xFFFF;
                        phaseTag = shortPhaseTag(ph);
                        chainCount = 0;
                    }
                    break;
                }
                case MSG_DRAW: {
                    if (b.remaining() >= 2) {
                        int p = b.get() & 1;
                        int count = b.get() & 0xFF;
                        List<String> drawn = new ArrayList<>();
                        for (int i = 0; i < count && b.remaining() >= 4; i++) {
                            int code = b.getInt() & 0x7FFFFFFF;
                            drawn.add(code > 0 ? resolver.resolveName(code) : "未知卡");
                        }
                        if (turn == 0) {
                            sb.append("[开局手牌] ").append(names[p]).append("：")
                                    .append(join(drawn, "、")).append("\n");
                        } else {
                            sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                    .append(names[p]).append(" 抽卡(").append(count).append(")：")
                                    .append(join(drawn, "、")).append("\n");
                        }
                    }
                    break;
                }
                case MSG_SUMMONING:
                case MSG_SPSUMMONING:
                case MSG_FLIPSUMMONING: {
                    if (b.remaining() >= 8) {
                        int code = b.getInt();
                        int p = b.get() & 1;
                        int loc = b.get() & 0xFF;
                        int seq = b.get() & 0xFF;
                        int pos = b.get() & 0xFF;
                        if (loc == RpgAiDuelState.LOC_MZONE && seq < 7) {
                            TrackedSlot slot = mzone[p][seq];
                            if (slot == null) {
                                slot = new TrackedSlot();
                                mzone[p][seq] = slot;
                            }
                            if (code > 0) slot.code = code;
                            slot.pos = pos;
                        }
                        String verb = msgType == MSG_SUMMONING ? "通常召唤"
                                : (msgType == MSG_SPSUMMONING ? "特殊召唤" : "反转召唤");
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append(names[p]).append(" ").append(verb).append("「")
                                .append(resolver.resolveName(code)).append("」(")
                                .append(posLabel(pos)).append(", M").append(seq + 1).append(")\n");
                    }
                    break;
                }
                case MSG_SET: {
                    if (b.remaining() >= 8) {
                        int code = b.getInt();
                        int p = b.get() & 1;
                        int loc = b.get() & 0xFF;
                        int seq = b.get() & 0xFF;
                        int pos = b.get() & 0xFF;
                        TrackedSlot existing = getSlot(mzone, szone, p, loc, seq);
                        int resolvedCode = code > 0 ? code : (existing != null ? existing.code : 0);
                        if (existing != null) {
                            if (resolvedCode > 0) existing.code = resolvedCode;
                            existing.pos = pos;
                        }
                        String zoneTag = (loc == RpgAiDuelState.LOC_MZONE)
                                ? ("怪兽 M" + (seq + 1)) : ("魔陷 S" + (seq + 1));
                        String cardStr = resolvedCode > 0 ? ("「" + resolver.resolveName(resolvedCode) + "」") : "里侧卡";
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append(names[p]).append(" 盖放").append(cardStr)
                                .append(" (").append(zoneTag).append(")\n");
                    }
                    break;
                }
                case MSG_POS_CHANGE: {
                    if (b.remaining() >= 9) {
                        int code = b.getInt();
                        int p = b.get() & 1;
                        int loc = b.get() & 0xFF;
                        int seq = b.get() & 0xFF;
                        int oldPos = b.get() & 0xFF;
                        int newPos = b.get() & 0xFF;
                        TrackedSlot existing = getSlot(mzone, szone, p, loc, seq);
                        int resolvedCode = code > 0 ? code : (existing != null ? existing.code : 0);
                        if (existing != null) {
                            if (resolvedCode > 0) existing.code = resolvedCode;
                            existing.pos = newPos;
                        }
                        if (loc == RpgAiDuelState.LOC_MZONE && oldPos != newPos) {
                            sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                    .append(names[p]).append(" 表示形式变更：「")
                                    .append(resolver.resolveName(resolvedCode)).append("」")
                                    .append(posLabel(oldPos)).append("→").append(posLabel(newPos))
                                    .append(" (M").append(seq + 1).append(")\n");
                        }
                    }
                    break;
                }
                case MSG_CHAINING: {
                    if (b.remaining() >= 16) {
                        int code = b.getInt();
                        b.get(); // pcc
                        b.get(); // pcl
                        b.get(); // pcs
                        b.get(); // subs
                        int cc = b.get() & 1;
                        int cl = b.get() & 0xFF;
                        int cs = b.get() & 0xFF;
                        b.getInt(); // desc
                        int ct = b.get() & 0xFF;
                        chainCount = ct > 0 ? ct : (chainCount + 1);
                        String locTag = shortLocTag(cl, cs);
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append(names[cc]).append(chainCount > 1 ? " 连锁发动" : " 发动")
                                .append("「").append(resolver.resolveName(code)).append("」(C")
                                .append(chainCount).append(locTag.isEmpty() ? "" : (", " + locTag)).append(")\n");
                    }
                    break;
                }
                case MSG_CHAIN_NEGATED:
                case MSG_CHAIN_DISABLED: {
                    if (b.remaining() >= 1) {
                        int ct = b.get() & 0xFF;
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append("连锁 C").append(ct)
                                .append(msgType == MSG_CHAIN_NEGATED ? " 发动被无效\n" : " 效果被无效\n");
                    }
                    break;
                }
                case MSG_CHAIN_END: {
                    chainCount = 0;
                    break;
                }
                case MSG_EQUIP: {
                    if (b.remaining() >= 8) {
                        int eCtrl = b.get() & 1;
                        int eLoc = b.get() & 0xFF;
                        int eSeq = b.get() & 0xFF;
                        b.get();
                        int tCtrl = b.get() & 1;
                        int tLoc = b.get() & 0xFF;
                        int tSeq = b.get() & 0xFF;
                        b.get();
                        TrackedSlot eqSlot = getSlot(mzone, szone, eCtrl, eLoc, eSeq);
                        TrackedSlot tgSlot = getSlot(mzone, szone, tCtrl, tLoc, tSeq);
                        String eqName = (eqSlot != null && eqSlot.code > 0) ? resolver.resolveName(eqSlot.code) : "装备卡";
                        String tgName = (tgSlot != null && tgSlot.code > 0) ? resolver.resolveName(tgSlot.code) : "怪兽";
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] 装备「")
                                .append(eqName).append("」→ ").append(names[tCtrl]).append("「")
                                .append(tgName).append("」\n");
                    }
                    break;
                }
                case MSG_ATTACK: {
                    if (b.remaining() >= 8) {
                        int aCtrl = b.get() & 1;
                        int aLoc = b.get() & 0xFF;
                        int aSeq = b.get() & 0xFF;
                        b.get();
                        int dCtrl = b.get() & 1;
                        int dLoc = b.get() & 0xFF;
                        int dSeq = b.get() & 0xFF;
                        b.get();
                        TrackedSlot att = getSlot(mzone, szone, aCtrl, aLoc, aSeq);
                        String attName = (att != null && att.code > 0) ? resolver.resolveName(att.code) : ("M" + (aSeq + 1));
                        if (dLoc == 0) {
                            sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                    .append(names[aCtrl]).append("「").append(attName)
                                    .append("」直接攻击 ").append(names[1 - aCtrl]).append("\n");
                        } else {
                            TrackedSlot def = getSlot(mzone, szone, dCtrl, dLoc, dSeq);
                            String defName = (def != null && def.code > 0) ? resolver.resolveName(def.code) : ("M" + (dSeq + 1));
                            String defPos = def != null ? posLabel(def.pos) : "";
                            sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                    .append(names[aCtrl]).append("「").append(attName)
                                    .append("」攻击 ").append(names[dCtrl]).append("「").append(defName)
                                    .append("」").append(defPos.isEmpty() ? "" : "(" + defPos + ")").append("\n");
                        }
                    }
                    break;
                }
                case MSG_ATTACK_DISABLED: {
                    sb.append("T").append(turn).append("[").append(phaseTag).append("] 攻击被无效\n");
                    break;
                }
                case MSG_TOSS_COIN: {
                    if (b.remaining() >= 2) {
                        int p = b.get() & 1;
                        int cnt = b.get() & 0xFF;
                        List<String> res = new ArrayList<>();
                        for (int i = 0; i < cnt && b.remaining() >= 1; i++) {
                            res.add((b.get() & 0xFF) != 0 ? "正" : "反");
                        }
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append(names[p]).append(" 掷硬币：").append(join(res, "/")).append("\n");
                    }
                    break;
                }
                case MSG_MOVE: {
                    if (b.remaining() >= 16) {
                        int code = b.getInt();
                        int oldCtrl = b.get() & 1;
                        int oldLoc = b.get() & 0xFF;
                        int oldSeq = b.get() & 0xFF;
                        b.get(); // oldPos
                        int newCtrl = b.get() & 1;
                        int newLoc = b.get() & 0xFF;
                        int newSeq = b.get() & 0xFF;
                        int newPos = b.get() & 0xFF;
                        int reason = b.getInt();

                        TrackedSlot moved = removeSlot(mzone, szone, oldCtrl, oldLoc, oldSeq);
                        int resolvedCode = code > 0 ? code : (moved != null ? moved.code : 0);
                        if (newLoc == RpgAiDuelState.LOC_MZONE && newSeq < 7) {
                            TrackedSlot slot = moved != null ? moved : new TrackedSlot();
                            if (resolvedCode > 0) slot.code = resolvedCode;
                            slot.pos = newPos;
                            mzone[newCtrl][newSeq] = slot;
                        } else if (newLoc == RpgAiDuelState.LOC_SZONE && newSeq < 8) {
                            TrackedSlot slot = moved != null ? moved : new TrackedSlot();
                            if (resolvedCode > 0) slot.code = resolvedCode;
                            slot.pos = newPos;
                            szone[newCtrl][newSeq] = slot;
                        }

                        // Log only high-signal moves (control switch, destruction, tribute, discard, search/recovery, bounce)
                        if (turn > 0 && resolvedCode > 0) {
                            String cName = resolver.resolveName(resolvedCode);
                            if (oldLoc == RpgAiDuelState.LOC_MZONE && newLoc == RpgAiDuelState.LOC_MZONE && oldCtrl != newCtrl) {
                                sb.append("T").append(turn).append("[").append(phaseTag).append("] 「")
                                        .append(cName).append("」控制权转移（").append(names[oldCtrl])
                                        .append("→").append(names[newCtrl]).append("）\n");
                            } else if ((oldLoc == RpgAiDuelState.LOC_MZONE || oldLoc == RpgAiDuelState.LOC_SZONE)
                                    && (newLoc == RpgAiDuelState.LOC_GRAVE || newLoc == RpgAiDuelState.LOC_REMOVED)) {
                                String dest = newLoc == RpgAiDuelState.LOC_REMOVED ? "除外" : "送墓";
                                if ((reason & REASON_BATTLE) != 0) {
                                    sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                            .append(names[oldCtrl]).append("「").append(cName).append("」战斗破坏").append(dest).append("\n");
                                } else if ((reason & REASON_DESTROY) != 0) {
                                    sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                            .append(names[oldCtrl]).append("「").append(cName).append("」效果破坏").append(dest).append("\n");
                                } else if ((reason & REASON_RELEASE) != 0) {
                                    sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                            .append(names[oldCtrl]).append(" 解放「").append(cName).append("」\n");
                                } else if ((reason & REASON_MATERIAL) != 0) {
                                    sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                            .append(names[oldCtrl]).append(" 素材送墓「").append(cName).append("」\n");
                                } else if ((reason & REASON_RULE) == 0 && oldLoc == RpgAiDuelState.LOC_MZONE) {
                                    sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                            .append(names[oldCtrl]).append("「").append(cName).append("」离场").append(dest).append("\n");
                                }
                            } else if (oldLoc == RpgAiDuelState.LOC_HAND
                                    && (newLoc == RpgAiDuelState.LOC_GRAVE || newLoc == RpgAiDuelState.LOC_REMOVED)
                                    && (reason & (REASON_DISCARD | REASON_COST | REASON_EFFECT | REASON_MATERIAL)) != 0) {
                                sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                        .append(names[oldCtrl]).append(" 丢弃/送墓手牌「").append(cName).append("」\n");
                            } else if (newLoc == RpgAiDuelState.LOC_HAND
                                    && (oldLoc == RpgAiDuelState.LOC_DECK || oldLoc == RpgAiDuelState.LOC_GRAVE || oldLoc == RpgAiDuelState.LOC_MZONE)) {
                                String fromTag = oldLoc == RpgAiDuelState.LOC_DECK ? "从卡组检索"
                                        : (oldLoc == RpgAiDuelState.LOC_GRAVE ? "从墓地回收" : "弹回手牌");
                                sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                        .append(names[newCtrl]).append(" ").append(fromTag).append("「").append(cName).append("」\n");
                            }
                        }
                    }
                    break;
                }
                case MSG_SWAP: {
                    if (b.remaining() >= 16) {
                        b.getInt();
                        int c1 = b.get() & 1, l1 = b.get() & 0xFF, s1 = b.get() & 0xFF;
                        b.get();
                        b.getInt();
                        int c2 = b.get() & 1, l2 = b.get() & 0xFF, s2 = b.get() & 0xFF;
                        b.get();
                        if (l1 == RpgAiDuelState.LOC_MZONE && l2 == RpgAiDuelState.LOC_MZONE && s1 < 7 && s2 < 7) {
                            TrackedSlot tmp = mzone[c1][s1];
                            mzone[c1][s1] = mzone[c2][s2];
                            mzone[c2][s2] = tmp;
                        }
                    }
                    break;
                }
                case MSG_UPDATE_DATA: {
                    applyZoneSnapshot(b, mzone, szone);
                    break;
                }
                case MSG_DAMAGE: {
                    if (b.remaining() >= 5) {
                        int p = b.get() & 1;
                        int amt = b.getInt();
                        lp[p] = Math.max(0, lp[p] - amt);
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append(names[p]).append(" 受到 ").append(amt)
                                .append(" 伤害 (LP ").append(lp[0]).append(":").append(lp[1]).append(")\n");
                    }
                    break;
                }
                case MSG_RECOVER: {
                    if (b.remaining() >= 5) {
                        int p = b.get() & 1;
                        int amt = b.getInt();
                        lp[p] += amt;
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append(names[p]).append(" 回复 ").append(amt)
                                .append(" LP (LP ").append(lp[0]).append(":").append(lp[1]).append(")\n");
                    }
                    break;
                }
                case MSG_PAY_LPCOST: {
                    if (b.remaining() >= 5) {
                        int p = b.get() & 1;
                        int amt = b.getInt();
                        lp[p] = Math.max(0, lp[p] - amt);
                        sb.append("T").append(turn).append("[").append(phaseTag).append("] ")
                                .append(names[p]).append(" 支付 ").append(amt)
                                .append(" LP (LP ").append(lp[0]).append(":").append(lp[1]).append(")\n");
                    }
                    break;
                }
                case MSG_LPUPDATE: {
                    if (b.remaining() >= 5) {
                        int p = b.get() & 1;
                        lp[p] = b.getInt();
                    }
                    break;
                }
                case MSG_WIN: {
                    if (b.remaining() >= 2) {
                        int winner = b.get() & 0xFF;
                        int reason = b.get() & 0xFF;
                        sawWin = true;
                        if (winner == 0 || winner == 1) {
                            sb.append("━━ 终局：").append(names[winner]).append(" 获胜（")
                                    .append(describeWinReason(reason)).append("）· 最终 LP ")
                                    .append(lp[0]).append(":").append(lp[1])
                                    .append(" · 共 ").append(turn).append(" 回合 ━━\n");
                        } else {
                            sb.append("━━ 终局：双方平局 · 最终 LP ")
                                    .append(lp[0]).append(":").append(lp[1])
                                    .append(" · 共 ").append(turn).append(" 回合 ━━\n");
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
        if (!sawWin) {
            sb.append("━━ 记录结束 · 最终 LP ").append(lp[0]).append(":").append(lp[1])
                    .append(" · 共 ").append(turn).append(" 回合 ━━\n");
        }
    }

    private static void applyZoneSnapshot(ByteBuffer b, TrackedSlot[][] mzone, TrackedSlot[][] szone) {
        if (b.remaining() < 2) return;
        int p = b.get() & 1;
        int loc = b.get() & 0xFF;
        if (loc != RpgAiDuelState.LOC_MZONE && loc != RpgAiDuelState.LOC_SZONE) return;
        TrackedSlot[] arr = (loc == RpgAiDuelState.LOC_MZONE) ? mzone[p] : szone[p];
        int seq = 0;
        while (b.remaining() >= 4 && seq < arr.length) {
            int clen = b.getInt();
            if (clen <= 4) {
                arr[seq] = null;
                seq++;
                continue;
            }
            int payloadLen = clen - 4;
            if (payloadLen > b.remaining()) break;
            int start = b.position();
            int flag = payloadLen >= 4 ? b.getInt() : 0;
            TrackedSlot slot = arr[seq] != null ? arr[seq] : new TrackedSlot();
            if ((flag & 0x1) != 0 && b.position() + 4 <= start + payloadLen) {
                int code = b.getInt();
                if (code > 0) slot.code = code;
            }
            if ((flag & 0x2) != 0 && b.position() + 4 <= start + payloadLen) {
                int posWord = b.getInt();
                slot.pos = (posWord >> 24) & 0xFF;
            }
            arr[seq] = slot;
            b.position(start + payloadLen);
            seq++;
        }
    }

    private static TrackedSlot getSlot(TrackedSlot[][] mzone, TrackedSlot[][] szone,
                                       int p, int loc, int seq) {
        if (p < 0 || p > 1 || seq < 0) return null;
        if (loc == RpgAiDuelState.LOC_MZONE && seq < 7) return mzone[p][seq];
        if (loc == RpgAiDuelState.LOC_SZONE && seq < 8) return szone[p][seq];
        return null;
    }

    private static TrackedSlot removeSlot(TrackedSlot[][] mzone, TrackedSlot[][] szone,
                                          int p, int loc, int seq) {
        if (p < 0 || p > 1 || seq < 0) return null;
        if (loc == RpgAiDuelState.LOC_MZONE && seq < 7) {
            TrackedSlot s = mzone[p][seq];
            mzone[p][seq] = null;
            return s;
        }
        if (loc == RpgAiDuelState.LOC_SZONE && seq < 8) {
            TrackedSlot s = szone[p][seq];
            szone[p][seq] = null;
            return s;
        }
        return null;
    }

    private static String shortPhaseTag(int ph) {
        switch (ph) {
            case RpgAiDuelState.PHASE_DRAW:         return "DP";
            case RpgAiDuelState.PHASE_STANDBY:      return "SP";
            case RpgAiDuelState.PHASE_MAIN1:        return "M1";
            case RpgAiDuelState.PHASE_BATTLE_START:
            case RpgAiDuelState.PHASE_BATTLE_STEP:
            case RpgAiDuelState.PHASE_BATTLE:       return "BP";
            case RpgAiDuelState.PHASE_MAIN2:        return "M2";
            case RpgAiDuelState.PHASE_END:          return "EP";
            default:                                return "P";
        }
    }

    private static String posLabel(int pos) {
        if ((pos & RpgAiDuelState.POS_FACEUP_ATTACK) != 0) return "表攻";
        if ((pos & RpgAiDuelState.POS_FACEDOWN_ATTACK) != 0) return "里攻";
        if ((pos & RpgAiDuelState.POS_FACEUP_DEFENSE) != 0) return "表守";
        if ((pos & RpgAiDuelState.POS_FACEDOWN_DEFENSE) != 0) return "里守";
        return "";
    }

    private static String shortLocTag(int loc, int seq) {
        switch (loc) {
            case RpgAiDuelState.LOC_HAND:    return "手牌";
            case RpgAiDuelState.LOC_MZONE:   return "M" + (seq + 1);
            case RpgAiDuelState.LOC_SZONE:   return (seq == 5) ? "场地" : ("S" + (seq + 1));
            case RpgAiDuelState.LOC_GRAVE:   return "墓地";
            case RpgAiDuelState.LOC_REMOVED: return "除外";
            case RpgAiDuelState.LOC_DECK:    return "卡组";
            case RpgAiDuelState.LOC_EXTRA:   return "额外";
            default:                         return "";
        }
    }

    private static String describeWinReason(int reason) {
        switch (reason) {
            case 0:  return "对方投降";
            case 1:  return "基本分归零";
            case 2:  return "卡组抽干";
            case 3:  return "操作超时";
            case 4:  return "断线";
            case 16: return "艾克佐迪亚特殊胜利";
            default: return "特殊胜利/裁定(" + reason + ")";
        }
    }

    private static String join(List<String> list, String sep) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
