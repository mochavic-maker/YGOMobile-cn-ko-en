package cn.garymb.ygomobile.rpg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels;
import cn.garymb.ygomobile.rpg.ai.RpgAiCardKnowledge;
import cn.garymb.ygomobile.rpg.ai.RpgAiDuelState;
import cn.garymb.ygomobile.rpg.data.RpgCatalogLoader;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter;

public class RpgReplayLogFormatterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private RpgCatalog catalog;
    private OcgRuleSet ruleSet;

    @Before
    public void setUp() throws Exception {
        File catFile = new File("src/main/assets/rpg/ocg_demo_catalog.json");
        if (!catFile.exists()) {
            catFile = new File("rpg-mode/src/main/assets/rpg/ocg_demo_catalog.json");
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(catFile)) {
            catalog = RpgCatalogLoader.loadFromStream(fis);
        }
        ruleSet = TimelineEngine.getActiveRuleSet(catalog, "1999-11-18");
    }

    @Test
    public void sidecarMetaRoundTripsRenameAndDelete() throws Exception {
        File yrp = tmp.newFile("match1.yrp");
        RpgReplayLogFormatter.ReplayMeta meta = new RpgReplayLogFormatter.ReplayMeta();
        meta.source = "RPG_MANUAL";
        meta.timelineDate = "1999-11-18";
        meta.ruleName = ruleSet.name;
        meta.putPlayerControl("玩家A", "手动");
        meta.putPlayerControl("海马濑人", RpgReplayLogFormatter.formatAiStrategyLabel("GOOD_STUFF"));

        assertTrue(RpgReplayLogFormatter.saveSidecar(yrp, meta));
        RpgReplayLogFormatter.ReplayMeta loaded = RpgReplayLogFormatter.loadSidecar(yrp);
        assertNotNull(loaded);
        assertEquals("RPG_MANUAL", loaded.source);
        assertEquals("1999-11-18", loaded.timelineDate);
        assertEquals("手动", loaded.findControlForPlayer("玩家A"));
        assertTrue(loaded.findControlForPlayer("海马濑人").contains("GOOD_STUFF"));

        File renamed = new File(tmp.getRoot(), "match_renamed.yrp");
        RpgReplayLogFormatter.renameSidecar(yrp, renamed);
        assertFalse(RpgReplayLogFormatter.sidecarFileFor(yrp).exists());
        assertNotNull(RpgReplayLogFormatter.loadSidecar(renamed));

        RpgReplayLogFormatter.deleteSidecar(renamed);
        assertFalse(RpgReplayLogFormatter.sidecarFileFor(renamed).exists());
    }

    @Test
    public void formatsStrategiesDecksAndDetailedActionFrames() {
        RpgReplayLogFormatter.ReplayMeta meta = new RpgReplayLogFormatter.ReplayMeta();
        meta.source = "RPG_MANUAL";
        meta.timelineDate = "1999-11-18";
        meta.ruleName = ruleSet.name;
        meta.putPlayerControl("决斗者", "手动");
        meta.putPlayerControl("海马濑人", RpgReplayLogFormatter.formatAiStrategyLabel("GOOD_STUFF"));

        List<List<Integer>> mainDecks = Arrays.asList(
                Arrays.asList(RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiCardKnowledge.DARK_MAGICIAN,
                        RpgAiCardKnowledge.POT_OF_GREED, RpgAiCardKnowledge.MAN_EATER_BUG),
                Arrays.asList(RpgAiCardKnowledge.BLUE_EYES, RpgAiCardKnowledge.BLUE_EYES,
                        RpgAiCardKnowledge.TRAP_HOLE, RpgAiCardKnowledge.FISSURE)
        );
        List<List<Integer>> extraDecks = Arrays.asList(
                Collections.<Integer>emptyList(),
                Collections.singletonList(RpgAiCardKnowledge.BLUE_EYES_ULTIMATE)
        );

        List<byte[]> frames = new ArrayList<>();
        // Opening hand draw for P0
        frames.add(buildDrawFrame(0, RpgAiCardKnowledge.POT_OF_GREED, RpgAiCardKnowledge.MAN_EATER_BUG));
        // T1 P0
        frames.add(new byte[]{(byte) RpgReplayLogFormatter.MSG_NEW_TURN, 0});
        frames.add(buildPhaseFrame(RpgAiDuelState.PHASE_MAIN1));
        // Move Man-Eater Bug from hand -> MZone[2] facedown, then MSG_SET with code=0
        frames.add(buildMoveFrame(RpgAiCardKnowledge.MAN_EATER_BUG,
                0, RpgAiDuelState.LOC_HAND, 1, RpgAiDuelState.POS_FACEUP_ATTACK,
                0, RpgAiDuelState.LOC_MZONE, 2, RpgAiDuelState.POS_FACEDOWN_DEFENSE, 0));
        frames.add(buildSetFrame(0, 0, RpgAiDuelState.LOC_MZONE, 2, RpgAiDuelState.POS_FACEDOWN_DEFENSE));
        // Activate Pot of Greed
        frames.add(buildChainingFrame(RpgAiCardKnowledge.POT_OF_GREED, 0, RpgAiDuelState.LOC_SZONE, 0, 1));
        // T2 P1 summons Blue-Eyes, attacks P0's face-down Man-Eater Bug, then deals damage & wins
        frames.add(new byte[]{(byte) RpgReplayLogFormatter.MSG_NEW_TURN, 1});
        frames.add(buildPhaseFrame(RpgAiDuelState.PHASE_MAIN1));
        frames.add(buildSummonFrame(RpgReplayLogFormatter.MSG_SUMMONING,
                RpgAiCardKnowledge.BLUE_EYES, 1, 2, RpgAiDuelState.POS_FACEUP_ATTACK));
        frames.add(buildPhaseFrame(RpgAiDuelState.PHASE_BATTLE_STEP));
        frames.add(buildAttackFrame(1, 2, 0, RpgAiDuelState.LOC_MZONE, 2));
        frames.add(buildMoveFrame(RpgAiCardKnowledge.MAN_EATER_BUG,
                0, RpgAiDuelState.LOC_MZONE, 2, RpgAiDuelState.POS_FACEUP_DEFENSE,
                0, RpgAiDuelState.LOC_GRAVE, 0, RpgAiDuelState.POS_FACEUP_ATTACK, 0x21));
        frames.add(buildDamageFrame(0, 8000));
        frames.add(new byte[]{(byte) RpgReplayLogFormatter.MSG_WIN, 1, 1});

        String log = RpgReplayLogFormatter.formatReplay(
                "test_match.yrp", 1700000000000L,
                Arrays.asList("决斗者", "海马濑人"),
                8000, 5, 1,
                mainDecks, extraDecks,
                frames, meta,
                RpgReplayLogFormatter.catalogResolver(catalog));

        assertTrue("Header should show Manual for player", log.contains("先攻(P0)：决斗者 — 手动"));
        assertTrue("Header should show AI strategy for opponent", log.contains("后攻(P1)：海马濑人 — AI · Good Stuff 竞技强攻 (GOOD_STUFF)"));
        assertTrue("Main deck should group duplicates with ×2", log.contains("×2"));
        assertTrue("Extra deck should include Blue-Eyes Ultimate", log.contains("额外："));
        assertTrue("Opening hand should be printed", log.contains("[开局手牌] 决斗者"));
        assertTrue("Face-down set should resolve card name from preceding MSG_MOVE",
                log.contains("盖放「") && log.contains("怪兽 M3"));
        assertTrue("Activation chain should be printed", log.contains("发动「") && log.contains("(C1"));
        assertTrue("Attack should be printed with both monster names", log.contains("攻击 决斗者「"));
        assertTrue("Battle destruction should be logged", log.contains("战斗破坏送墓"));
        assertTrue("Damage with updated LP should be logged", log.contains("受到 8000 伤害 (LP 0:8000)"));
        assertTrue("Win line should be logged", log.contains("终局：海马濑人 获胜（基本分归零）"));
    }

    @Test
    public void fallbackFormatterOutputsDecksStrategiesAndSummaryLogs() {
        RpgAiAutoDuelModels.DuelResult res = new RpgAiAutoDuelModels.DuelResult(
                true, false, 6400, 0, 5, ruleSet,
                Arrays.asList("T1 玩家 发动「强欲之壶」", "T5 玩家 获胜"),
                "你", "海马濑人", "GOOD_STUFF", "FLIP_CONTROL",
                Collections.<RpgAiAutoDuelModels.ReplayFrame>emptyList(), null, true);

        String text = RpgReplayLogFormatter.formatFallbackFromDuelResult(
                res, "1999-11-18",
                Arrays.asList(RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiCardKnowledge.POT_OF_GREED),
                Collections.<Integer>emptyList(),
                Arrays.asList(RpgAiCardKnowledge.MAN_EATER_BUG, RpgAiCardKnowledge.TRAP_HOLE),
                Collections.<Integer>emptyList(),
                catalog);
        assertTrue(text.contains("GOOD_STUFF"));
        assertTrue(text.contains("FLIP_CONTROL"));
        assertTrue(text.contains("【双方卡组】"));
        assertTrue(text.contains("T1 玩家 发动「强欲之壶」"));
    }

    private static byte[] buildPhaseFrame(int phase) {
        ByteBuffer b = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) RpgReplayLogFormatter.MSG_NEW_PHASE).putShort((short) phase);
        return b.array();
    }

    private static byte[] buildDrawFrame(int player, int... codes) {
        ByteBuffer b = ByteBuffer.allocate(3 + codes.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) RpgReplayLogFormatter.MSG_DRAW).put((byte) player).put((byte) codes.length);
        for (int c : codes) b.putInt(c);
        return b.array();
    }

    private static byte[] buildMoveFrame(int code, int oc, int ol, int os, int op,
                                         int nc, int nl, int ns, int np, int reason) {
        ByteBuffer b = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) RpgReplayLogFormatter.MSG_MOVE).putInt(code)
                .put((byte) oc).put((byte) ol).put((byte) os).put((byte) op)
                .put((byte) nc).put((byte) nl).put((byte) ns).put((byte) np)
                .putInt(reason);
        return b.array();
    }

    private static byte[] buildSetFrame(int code, int ctrl, int loc, int seq, int pos) {
        ByteBuffer b = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) RpgReplayLogFormatter.MSG_SET).putInt(code)
                .put((byte) ctrl).put((byte) loc).put((byte) seq).put((byte) pos);
        return b.array();
    }

    private static byte[] buildSummonFrame(int opcode, int code, int ctrl, int seq, int pos) {
        ByteBuffer b = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) opcode).putInt(code)
                .put((byte) ctrl).put((byte) RpgAiDuelState.LOC_MZONE).put((byte) seq).put((byte) pos);
        return b.array();
    }

    private static byte[] buildChainingFrame(int code, int ctrl, int loc, int seq, int chainNum) {
        ByteBuffer b = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) RpgReplayLogFormatter.MSG_CHAINING).putInt(code)
                .put((byte) ctrl).put((byte) loc).put((byte) seq).put((byte) 0)
                .put((byte) ctrl).put((byte) loc).put((byte) seq)
                .putInt(0).put((byte) chainNum);
        return b.array();
    }

    private static byte[] buildAttackFrame(int ac, int aseq, int dc, int dloc, int dseq) {
        ByteBuffer b = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) RpgReplayLogFormatter.MSG_ATTACK)
                .put((byte) ac).put((byte) RpgAiDuelState.LOC_MZONE).put((byte) aseq).put((byte) 0)
                .put((byte) dc).put((byte) dloc).put((byte) dseq).put((byte) 0);
        return b.array();
    }

    private static byte[] buildDamageFrame(int p, int amt) {
        ByteBuffer b = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) RpgReplayLogFormatter.MSG_DAMAGE).put((byte) p).putInt(amt);
        return b.array();
    }
}
