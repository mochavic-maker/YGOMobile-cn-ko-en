package cn.garymb.ygomobile.rpg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.rpg.ai.RpgAiCardKnowledge;
import cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient;
import cn.garymb.ygomobile.rpg.ai.RpgAiDuelState;
import cn.garymb.ygomobile.rpg.ai.RpgAiStrategy;
import cn.garymb.ygomobile.rpg.data.RpgCatalogLoader;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

public class RpgAiStrategyTest {

    private RpgCatalog catalog;
    private OcgRuleSet noTributeRule;
    private OcgRuleSet tributeRule;

    @Before
    public void setUp() throws Exception {
        File catFile = new File("src/main/assets/rpg/ocg_demo_catalog.json");
        if (!catFile.exists()) {
            catFile = new File("rpg-mode/src/main/assets/rpg/ocg_demo_catalog.json");
        }
        try (FileInputStream fis = new FileInputStream(catFile)) {
            catalog = RpgCatalogLoader.loadFromStream(fis);
        }
        noTributeRule = TimelineEngine.getActiveRuleSet(catalog, "1999-02-04");
        tributeRule = TimelineEngine.getActiveRuleSet(catalog, "1999-08-26");
    }

    private RpgAiDuelState.CardSlot placeMonster(RpgAiDuelState state, int localPlayer, int seq,
                                                 int code, int pos, int atk, int def) {
        RpgAiDuelState.CardSlot s = new RpgAiDuelState.CardSlot();
        s.code = code;
        s.controller = localPlayer;
        s.owner = localPlayer;
        s.location = RpgAiDuelState.LOC_MZONE;
        s.sequence = seq;
        s.position = pos;
        s.atk = atk;
        s.def = def;
        s.type = RpgAiDuelState.TYPE_MONSTER;
        state.enrichFromCatalog(s);
        state.mzone[localPlayer][seq] = s;
        return s;
    }

    private void addHandCard(RpgAiDuelState state, int code) {
        RpgAiDuelState.CardSlot s = new RpgAiDuelState.CardSlot();
        s.code = code;
        s.controller = 0;
        s.owner = 0;
        s.location = RpgAiDuelState.LOC_HAND;
        s.sequence = state.hand[0].size();
        s.position = RpgAiDuelState.POS_FACEUP_ATTACK;
        state.enrichFromCatalog(s);
        state.hand[0].add(s);
    }

    @Test
    public void darkHoleAlwaysUsedBeforeSummoningAndNeverBlowsUpOwnAdvantage() {
        // Case 1: Empty AI board, opponent has Blue-Eyes (3000), AI has Dark Hole + Dark Magician in hand.
        // AI MUST activate Dark Hole (opcode 5) FIRST, NOT Normal Summon first!
        RpgAiDuelState state = new RpgAiDuelState(catalog, noTributeRule, "1999-03-06");
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.turnPlayer = 0;
        placeMonster(state, 1, 2, RpgAiCardKnowledge.BLUE_EYES, RpgAiDuelState.POS_FACEUP_ATTACK, 3000, 2500);
        addHandCard(state, RpgAiCardKnowledge.DARK_HOLE);
        addHandCard(state, RpgAiCardKnowledge.DARK_MAGICIAN);

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "NO_TRIBUTE_BEAT");
        RpgAiStrategy.IdleCmdPrompt prompt = new RpgAiStrategy.IdleCmdPrompt();
        prompt.summonable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_MAGICIAN, 1, 0, RpgAiDuelState.LOC_HAND, 1, 0, 0, 0));
        prompt.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_HOLE, 1, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        prompt.canBattle = true;
        prompt.canEnd = true;

        int resp1 = strategy.chooseIdleCmd(prompt);
        assertEquals("Should activate Dark Hole before summoning monster!", (0 << 16) + 5, resp1);

        // Case 2: AI ALREADY controls Blue-Eyes (3000) and opponent only has a 1200 ATK monster (or empty board).
        // AI MUST NOT activate Dark Hole and blow up its own Blue-Eyes!
        state.reset();
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.turnPlayer = 0;
        placeMonster(state, 0, 2, RpgAiCardKnowledge.BLUE_EYES, RpgAiDuelState.POS_FACEUP_ATTACK, 3000, 2500);
        placeMonster(state, 1, 2, 76184692, RpgAiDuelState.POS_FACEUP_ATTACK, 1200, 1000);
        addHandCard(state, RpgAiCardKnowledge.DARK_HOLE);

        RpgAiStrategy.IdleCmdPrompt prompt2 = new RpgAiStrategy.IdleCmdPrompt();
        prompt2.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_HOLE, 1, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        prompt2.canBattle = true;
        prompt2.canEnd = true;

        int resp2 = strategy.chooseIdleCmd(prompt2);
        assertEquals("Should enter Battle Phase instead of self-destructing Blue-Eyes with Dark Hole!", 6, resp2);
    }

    @Test
    public void potOfGreedAlwaysRunsFirstAndNoTributeSummonsBlueEyes() {
        RpgAiDuelState state = new RpgAiDuelState(catalog, noTributeRule, "1999-03-06");
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.turnPlayer = 0;
        addHandCard(state, RpgAiCardKnowledge.POT_OF_GREED);
        addHandCard(state, RpgAiCardKnowledge.BLUE_EYES);
        addHandCard(state, 76184692); // Hitotsu-Me Giant 1200

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "NO_TRIBUTE_BEAT");
        RpgAiStrategy.IdleCmdPrompt p1 = new RpgAiStrategy.IdleCmdPrompt();
        p1.summonable.add(new RpgAiStrategy.CmdEntry(76184692, 1, 0, RpgAiDuelState.LOC_HAND, 2, 0, 0, 0));
        p1.summonable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.BLUE_EYES, 1, 0, RpgAiDuelState.LOC_HAND, 1, 0, 0, 1));
        p1.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.POT_OF_GREED, 1, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p1.canBattle = true;
        p1.canEnd = true;

        assertEquals("Pot of Greed should activate first", (0 << 16) + 5, strategy.chooseIdleCmd(p1));

        // After Pot of Greed resolves, AI picks Blue-Eyes (index 1) over Hitotsu-Me Giant (index 0)
        p1.activatable.clear();
        assertEquals("Under 1999-02-04 no-tribute rule, should summon Blue-Eyes", 1 << 16, strategy.chooseIdleCmd(p1));
    }

    @Test
    public void flipMonstersSetFaceDownAndManEaterBugNeverFlipsOnEmptyOpponentBoard() {
        RpgAiDuelState state = new RpgAiDuelState(catalog, tributeRule, "1999-07-22");
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.turnPlayer = 0;
        addHandCard(state, RpgAiCardKnowledge.MAN_EATER_BUG);

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "FLIP_CONTROL");
        RpgAiStrategy.IdleCmdPrompt p1 = new RpgAiStrategy.IdleCmdPrompt();
        p1.summonable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.MAN_EATER_BUG, 1, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p1.msetable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.MAN_EATER_BUG, 1, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p1.canEnd = true;

        assertEquals("Man-Eater Bug should be Normal Set, not summoned in ATK", (0 << 16) + 3, strategy.chooseIdleCmd(p1));

        // Next turn: Man-Eater Bug is face-down on AI field, opponent has NO monsters -> MUST NOT flip!
        placeMonster(state, 0, 2, RpgAiCardKnowledge.MAN_EATER_BUG, RpgAiDuelState.POS_FACEDOWN_DEFENSE, 450, 600);
        RpgAiStrategy.IdleCmdPrompt p2 = new RpgAiStrategy.IdleCmdPrompt();
        p2.reposable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.MAN_EATER_BUG, 1, 0, RpgAiDuelState.LOC_MZONE, 2, 0, 0, 0));
        p2.canEnd = true;

        assertEquals("Must NOT flip Man-Eater Bug when opponent has 0 monsters", 7, strategy.chooseIdleCmd(p2));

        // Once opponent summons a monster -> DO flip Man-Eater Bug!
        placeMonster(state, 1, 2, RpgAiCardKnowledge.SUMMONED_SKULL, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 1200);
        assertEquals("Should flip Man-Eater Bug when opponent has a monster", (0 << 16) + 2, strategy.chooseIdleCmd(p2));
    }

    @Test
    public void exodiaProtectsUniquePiecesAndSearchesMissingPieces() {
        RpgAiDuelState state = new RpgAiDuelState(catalog, tributeRule, "1999-11-18");
        state.myProtocolPlayer = 1;
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.turnPlayer = 0;
        addHandCard(state, RpgAiCardKnowledge.EXODIA_HEAD);
        addHandCard(state, RpgAiCardKnowledge.EXODIA_RIGHT_ARM);
        addHandCard(state, RpgAiCardKnowledge.SANGAN);

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "EXODIA_DRAW");

        // Graceful Charity discard (min=2, max=2): candidates are Head(0), RightArm(1), Sangan(2), Duplicate RightArm(3)
        addHandCard(state, RpgAiCardKnowledge.EXODIA_RIGHT_ARM);
        state.lastSelectHint = 501;
        state.lastActivatingCode = RpgAiCardKnowledge.GRACEFUL_CHARITY;
        ByteBuffer discardBuf = ByteBuffer.allocate(5 + 4 * 8).order(ByteOrder.LITTLE_ENDIAN);
        discardBuf.put((byte) 1).put((byte) 0).put((byte) 2).put((byte) 2).put((byte) 4);
        int[] handCodes = new int[]{
                RpgAiCardKnowledge.EXODIA_HEAD,
                RpgAiCardKnowledge.EXODIA_RIGHT_ARM,
                RpgAiCardKnowledge.SANGAN,
                RpgAiCardKnowledge.EXODIA_RIGHT_ARM
        };
        for (int i = 0; i < 4; i++) {
            discardBuf.putInt(handCodes[i]).put((byte) 1).put((byte) RpgAiDuelState.LOC_HAND).put((byte) i).put((byte) 0);
        }
        discardBuf.flip();
        byte[] discardResp = strategy.chooseSelectCard(discardBuf);
        assertEquals(3, discardResp.length);
        assertEquals(2, discardResp[0]);
        // Must NEVER discard unique Exodia Head (index 0)
        assertNotEquals(0, discardResp[1] & 0xFF);
        assertNotEquals(0, discardResp[2] & 0xFF);

        // Sangan deck search: candidates = Magician of Faith(0), Exodia Left Leg(1 - MISSING!), Exodia Head(2 - already owned)
        state.lastActivatingCode = RpgAiCardKnowledge.SANGAN;
        ByteBuffer searchBuf = ByteBuffer.allocate(5 + 3 * 8).order(ByteOrder.LITTLE_ENDIAN);
        searchBuf.put((byte) 1).put((byte) 0).put((byte) 1).put((byte) 1).put((byte) 3);
        int[] deckCodes = new int[]{
                RpgAiCardKnowledge.MAGICIAN_OF_FAITH,
                RpgAiCardKnowledge.EXODIA_LEFT_LEG,
                RpgAiCardKnowledge.EXODIA_HEAD
        };
        for (int i = 0; i < 3; i++) {
            searchBuf.putInt(deckCodes[i]).put((byte) 1).put((byte) RpgAiDuelState.LOC_DECK).put((byte) i).put((byte) 0);
        }
        searchBuf.flip();
        byte[] searchResp = strategy.chooseSelectCard(searchBuf);
        assertEquals("Sangan in Exodia deck must search missing Exodia piece (index 1)", 1, searchResp[1] & 0xFF);
    }

    @Test
    public void battlePhaseNeverSuicidesWeakMonsterIntoStrongerMonster() {
        RpgAiDuelState state = new RpgAiDuelState(catalog, tributeRule, "1999-08-26");
        state.phase = RpgAiDuelState.PHASE_BATTLE_STEP;
        state.turnPlayer = 0;
        placeMonster(state, 0, 2, 76184692, RpgAiDuelState.POS_FACEUP_ATTACK, 1200, 1000);
        placeMonster(state, 1, 2, RpgAiCardKnowledge.SUMMONED_SKULL, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 1200);

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "GOOD_STUFF");
        RpgAiStrategy.BattleCmdPrompt bp = new RpgAiStrategy.BattleCmdPrompt();
        bp.attackable.add(new RpgAiStrategy.CmdEntry(76184692, 1, 0, RpgAiDuelState.LOC_MZONE, 2, 0, 0, 0));
        bp.canMain2 = true;
        bp.canEnd = true;

        int resp = strategy.chooseBattleCmd(bp);
        assertTrue("1200 ATK monster must NOT suicide into 2500 ATK Summoned Skull (should go M2 or EP)",
                resp == 2 || resp == 3);
    }

    @Test
    public void solemnJudgmentNegatesRaigekiNotSparksAndWabokuWaitsForBattle() {
        RpgAiDuelState state = new RpgAiDuelState(catalog, tributeRule, "1999-11-18");
        state.myProtocolPlayer = 1;
        state.turnPlayer = 1; // opponent's turn
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.lp[0] = 8000;

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "GOOD_STUFF");

        // Opponent plays Sparks -> Solemn Judgment should PASS (-1)!
        state.currentChain.clear();
        state.currentChain.add(new RpgAiDuelState.ChainLink(RpgAiCardKnowledge.SPARKS, 1, RpgAiDuelState.LOC_SZONE, 0, 0));
        assertEquals(-1, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.SOLEMN_JUDGMENT)));

        // Opponent plays Raigeki -> Solemn Judgment SHOULD ACTIVATE (0)!
        state.currentChain.clear();
        state.currentChain.add(new RpgAiDuelState.ChainLink(RpgAiCardKnowledge.RAIGEKI, 1, RpgAiDuelState.LOC_SZONE, 0, 0));
        assertEquals(0, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.SOLEMN_JUDGMENT)));

        // Opponent in Main Phase 1 (not attacking) -> Waboku should PASS (-1)!
        state.currentChain.clear();
        assertEquals(-1, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.WABOKU)));

        // Opponent attacks in Battle Phase -> Waboku SHOULD ACTIVATE (0)!
        state.phase = RpgAiDuelState.PHASE_BATTLE_STEP;
        state.lastAttackerLocalCtrl = 1;
        assertEquals(0, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.WABOKU)));
    }

    @Test
    public void doesNotDoubleChainSecondTrapHoleOrDuplicateBattleTrap() {
        RpgAiDuelState state = new RpgAiDuelState(catalog, noTributeRule, "1999-02-04");
        state.myProtocolPlayer = 1;
        state.turnPlayer = 1; // opponent's turn
        state.phase = RpgAiDuelState.PHASE_MAIN1;

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "NO_TRIBUTE_BEAT");

        // Link 1: opponent summons a 1200 ATK monster -> first Trap Hole activates (0)
        state.currentChain.clear();
        assertEquals("First Trap Hole should activate on empty chain",
                0, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Link 2: our first Trap Hole is ALREADY on the chain (un-negated) -> second Trap Hole MUST PASS (-1)!
        state.currentChain.add(new RpgAiDuelState.ChainLink(
                RpgAiCardKnowledge.TRAP_HOLE, 0, RpgAiDuelState.LOC_SZONE, 0, 0));
        assertEquals("Second Trap Hole must NOT chain onto our own un-negated Trap Hole!",
                -1, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Link 3: if opponent chains Royal Decree to negate our first Trap Hole -> backup Trap Hole IS allowed (resolves before Decree!)
        state.currentChain.add(new RpgAiDuelState.ChainLink(
                RpgAiCardKnowledge.ROYAL_DECREE, 1, RpgAiDuelState.LOC_SZONE, 1, 0));
        assertEquals("Backup Trap Hole may chain if opponent tries to negate our Link 1 with Royal Decree",
                0, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Battle Phase: never stack Waboku or a 2nd Mirror Force onto our own un-negated Mirror Force!
        state.phase = RpgAiDuelState.PHASE_BATTLE_STEP;
        state.lastAttackerLocalCtrl = 1;
        state.currentChain.clear();
        state.currentChain.add(new RpgAiDuelState.ChainLink(
                RpgAiCardKnowledge.MIRROR_FORCE, 0, RpgAiDuelState.LOC_SZONE, 0, 0));
        assertEquals("Must NOT chain 2nd Mirror Force onto our own Mirror Force",
                -1, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.MIRROR_FORCE)));
        assertEquals("Must NOT chain Waboku onto our own un-negated Mirror Force",
                -1, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.WABOKU)));
    }

    @Test
    public void vol1EraCalibrationSummonsMammothGraveyardInAttackAndScalesAcrossEras() {
        final int MAMMOTH_GRAVEYARD = 40374923; // Vol.1 1200 ATK / 800 DEF, Lv3

        RpgAiDuelState vol1State = new RpgAiDuelState(catalog, noTributeRule, "1999-02-04");
        RpgAiDuelState late99State = new RpgAiDuelState(catalog, tributeRule, "1999-12-16");

        // Dynamic era benchmarks should automatically scale as the card pool grows
        assertTrue("Vol.1 estimated face-down DEF should be <= 900, got " + vol1State.eraEstimatedFaceDownDef,
                vol1State.eraEstimatedFaceDownDef <= 900);
        assertEquals("Vol.1 top Lv<=4 ATK should be 1200", 1200, vol1State.eraTopLowerAtk);
        assertTrue("Late-1999 top Lv<=4 ATK (" + late99State.eraTopLowerAtk
                + ") should exceed Vol.1 (" + vol1State.eraTopLowerAtk + ")",
                late99State.eraTopLowerAtk > vol1State.eraTopLowerAtk);
        assertTrue("Late-1999 wall DEF threshold (" + late99State.eraWallDefThreshold
                + ") should exceed Vol.1 (" + vol1State.eraWallDefThreshold + ")",
                late99State.eraWallDefThreshold > vol1State.eraWallDefThreshold);

        RpgAiStrategy strategy = new RpgAiStrategy(vol1State, null, "NO_TRIBUTE_BEAT");

        // Case 1: Empty opponent board on Turn 1 -> MUST Normal Summon Mammoth Graveyard in ATK (0), NOT Set (3)
        vol1State.turnCount = 1;
        vol1State.turnPlayer = 0;
        vol1State.phase = RpgAiDuelState.PHASE_MAIN1;
        addHandCard(vol1State, MAMMOTH_GRAVEYARD);

        RpgAiStrategy.IdleCmdPrompt p = new RpgAiStrategy.IdleCmdPrompt();
        p.summonable.add(new RpgAiStrategy.CmdEntry(MAMMOTH_GRAVEYARD, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p.msetable.add(new RpgAiStrategy.CmdEntry(MAMMOTH_GRAVEYARD, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p.canBattle = true;
        p.canEnd = true;

        assertEquals("Vol.1 Mammoth Graveyard must be Normal Summoned in ATK on empty board",
                0, strategy.chooseIdleCmd(p));

        // Case 2: Board advantage (AI controls Dark Magician 2500) + opponent has a Vol.1 face-down monster -> MUST Summon in ATK
        vol1State.turnCount = 3;
        placeMonster(vol1State, 0, 2, RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 2100);
        placeMonster(vol1State, 1, 2, 0, RpgAiDuelState.POS_FACEDOWN_DEFENSE, 0, 0);
        assertEquals("Vol.1 Mammoth Graveyard must be Normal Summoned in ATK when ahead on board",
                0, strategy.chooseIdleCmd(p));

        // Case 3: Empty AI board vs opponent's Vol.1 face-down monster -> 1200 ATK easily beats Vol.1 Set DEF (~700)
        vol1State.mzone[0][2] = null;
        assertEquals("Vol.1 Mammoth Graveyard must be Normal Summoned in ATK against a Vol.1 face-down",
                0, strategy.chooseIdleCmd(p));

        // Case 4: Battle Phase — Mammoth Graveyard (1200 ATK) should attack a Vol.1 face-down monster
        vol1State.phase = RpgAiDuelState.PHASE_BATTLE_STEP;
        placeMonster(vol1State, 0, 2, MAMMOTH_GRAVEYARD, RpgAiDuelState.POS_FACEUP_ATTACK, 1200, 800);
        RpgAiStrategy.BattleCmdPrompt bp = new RpgAiStrategy.BattleCmdPrompt();
        bp.attackable.add(new RpgAiStrategy.CmdEntry(MAMMOTH_GRAVEYARD, 0, 0, RpgAiDuelState.LOC_MZONE, 2, 0, 0, 0));
        bp.canMain2 = true;
        bp.canEnd = true;
        assertEquals("Vol.1 Mammoth Graveyard (1200 ATK) should attack into a Vol.1 face-down monster",
                (0 << 16) + 1, strategy.chooseBattleCmd(bp));
    }

    @Test
    public void trapHoleHoldsForBossWhenBoardOrHandHandlesLowerSummonAndFiresOnBossOrThreat() {
        final int MAMMOTH_GRAVEYARD = 40374923; // 1200/800 Lv3
        final int GAIA = 6368038;               // 2300/2100 Lv7

        RpgAiDuelState vol1State = new RpgAiDuelState(catalog, noTributeRule, "1999-02-04");
        vol1State.myProtocolPlayer = 1;
        vol1State.turnPlayer = 1;
        vol1State.phase = RpgAiDuelState.PHASE_MAIN1;
        vol1State.lp[0] = 8000;
        RpgAiStrategy strategy = new RpgAiStrategy(vol1State, null, "NO_TRIBUTE_BEAT");

        // Case 1: AI already controls Dark Magician (2500) -> opponent summons Mammoth (1200) -> HOLD Trap Hole (-1)!
        placeMonster(vol1State, 0, 2, RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 2100);
        simulateOppSummon(vol1State, MAMMOTH_GRAVEYARD, 2, 1200, 800);
        assertEquals("Should HOLD Trap Hole against 1200 Mammoth when we already control 2500 Dark Magician",
                -1, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Case 2: AI board empty, healthy LP (8000), holds Dark Magician (2500) in hand -> opponent summons Mammoth (1200) -> HOLD (-1)!
        vol1State.mzone[0][2] = null;
        vol1State.mzone[1][2] = null;
        vol1State.hand[0].clear();
        addHandCard(vol1State, RpgAiCardKnowledge.DARK_MAGICIAN);
        simulateOppSummon(vol1State, MAMMOTH_GRAVEYARD, 2, 1200, 800);
        assertEquals("Should HOLD Trap Hole against 1200 Mammoth when holding 2500 Dark Magician at 8000 LP",
                -1, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Case 3: Opponent summons Gaia (2300) or Dark Magician (2500) -> ALWAYS FIRE Trap Hole (0)!
        vol1State.mzone[1][2] = null;
        placeMonster(vol1State, 0, 2, RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 2100);
        simulateOppSummon(vol1State, GAIA, 2, 2300, 2100);
        assertEquals("Should FIRE Trap Hole immediately on boss Gaia (2300)",
                0, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Case 4: AI board empty & NO stronger monster in hand -> opponent summons Mammoth (1200) -> FIRE (0)!
        vol1State.mzone[0][2] = null;
        vol1State.mzone[1][2] = null;
        vol1State.hand[0].clear();
        addHandCard(vol1State, 9159938); // 800 ATK monster
        simulateOppSummon(vol1State, MAMMOTH_GRAVEYARD, 2, 1200, 800);
        assertEquals("Should FIRE Trap Hole on 1200 Mammoth when we have no stronger field/hand answer",
                0, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Case 5: AI holds Dark Magician in hand, but LP is low (2500) -> opponent summons Mammoth (1200) -> FIRE (0)!
        vol1State.mzone[1][2] = null;
        vol1State.hand[0].clear();
        addHandCard(vol1State, RpgAiCardKnowledge.DARK_MAGICIAN);
        vol1State.lp[0] = 2500;
        simulateOppSummon(vol1State, MAMMOTH_GRAVEYARD, 2, 1200, 800);
        assertEquals("Should FIRE Trap Hole on 1200 Mammoth when LP is low (2500)",
                0, strategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        // Case 6: Late-1999 Tribute Era — HOLD Trap Hole on Sangan (1000) to avoid free search, FIRE on Cannon Soldier (1400)!
        RpgAiDuelState lateState = new RpgAiDuelState(catalog, tributeRule, "1999-11-18");
        lateState.myProtocolPlayer = 1;
        lateState.turnPlayer = 1;
        lateState.phase = RpgAiDuelState.PHASE_MAIN1;
        lateState.lp[0] = 8000;
        RpgAiStrategy lateStrategy = new RpgAiStrategy(lateState, null, "GOOD_STUFF");

        simulateOppSummon(lateState, RpgAiCardKnowledge.SANGAN, 2, 1000, 600);
        assertEquals("Should HOLD Trap Hole on Sangan at healthy LP to avoid triggering opponent's search",
                -1, lateStrategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));

        lateState.mzone[1][2] = null;
        placeMonster(lateState, 0, 2, RpgAiCardKnowledge.BLUE_EYES, RpgAiDuelState.POS_FACEUP_ATTACK, 3000, 2500);
        simulateOppSummon(lateState, RpgAiCardKnowledge.CANNON_SOLDIER, 2, 1400, 1300);
        assertEquals("Should ALWAYS FIRE Trap Hole on Cannon Soldier even when controlling Blue-Eyes",
                0, lateStrategy.chooseChain(buildSingleChainPrompt(RpgAiCardKnowledge.TRAP_HOLE)));
    }

    @Test
    public void removalDisciplineHoldsFissureAndDarkHoleWhenMonsterCanBeatOver() {
        final int MAMMOTH = 40374923; // 1200/800
        final int GAIA = 6368038;     // 2300/2100

        // Case 1: Vol.1 — Opponent has ONLY 1200 Mammoth, AI holds Dark Magician (2500) + Fissure + Dark Hole.
        // AI MUST summon Dark Magician and beat over Mammoth in battle instead of wasting Fissure or Dark Hole!
        RpgAiDuelState state = new RpgAiDuelState(catalog, noTributeRule, "1999-02-04");
        state.turnCount = 3;
        state.turnPlayer = 0;
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.lp[1] = 8000;
        placeMonster(state, 1, 2, MAMMOTH, RpgAiDuelState.POS_FACEUP_ATTACK, 1200, 800);
        addHandCard(state, RpgAiCardKnowledge.DARK_MAGICIAN);
        addHandCard(state, RpgAiCardKnowledge.FISSURE);
        addHandCard(state, RpgAiCardKnowledge.DARK_HOLE);

        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "NO_TRIBUTE_BEAT");
        RpgAiStrategy.IdleCmdPrompt p1 = new RpgAiStrategy.IdleCmdPrompt();
        p1.summonable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_MAGICIAN, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p1.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.FISSURE, 0, 0, RpgAiDuelState.LOC_HAND, 1, 0, 0, 0));
        p1.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_HOLE, 0, 0, RpgAiDuelState.LOC_HAND, 2, 0, 0, 1));
        p1.canBattle = true;
        p1.canEnd = true;

        assertEquals("Should summon Dark Magician (0) instead of wasting Fissure/Dark Hole on 1200 Mammoth",
                0, strategy.chooseIdleCmd(p1));

        // Case 2: Opponent has Dark Magician (2500) + Mammoth (1200); AI has Gaia (2300) on field + Fissure.
        // Fissure destroys the LOWEST-ATK face-up monster (1200 Mammoth), which Gaia already beats!
        // AI should enter Battle Phase (6) first to kill Mammoth, then fire Fissure in Main 2 on isolated Dark Magician!
        state.reset();
        state.turnCount = 5;
        state.turnPlayer = 0;
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        placeMonster(state, 0, 2, GAIA, RpgAiDuelState.POS_FACEUP_ATTACK, 2300, 2100);
        placeMonster(state, 1, 1, RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 2100);
        placeMonster(state, 1, 2, MAMMOTH, RpgAiDuelState.POS_FACEUP_ATTACK, 1200, 800);
        addHandCard(state, RpgAiCardKnowledge.FISSURE);

        RpgAiStrategy.IdleCmdPrompt p2 = new RpgAiStrategy.IdleCmdPrompt();
        p2.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.FISSURE, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p2.canBattle = true;
        p2.canEnd = true;
        assertEquals("Should enter Battle Phase (6) to run over Mammoth instead of wasting Fissure on it",
                6, strategy.chooseIdleCmd(p2));

        // After Gaia destroys Mammoth in battle -> Main 2, only Dark Magician (2500) remains -> NOW fire Fissure!
        state.mzone[1][2] = null;
        state.phase = RpgAiDuelState.PHASE_MAIN2;
        p2.canBattle = false;
        assertEquals("In Main 2 with isolated Dark Magician (2500), MUST fire Fissure!",
                (0 << 16) + 5, strategy.chooseIdleCmd(p2));

        // Case 3: Immediate lethal — opponent has 1200 Mammoth and 2500 LP, AI has Dark Magician + Fissure -> fire Fissure for game!
        state.reset();
        state.turnCount = 7;
        state.turnPlayer = 0;
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.lp[1] = 2500;
        placeMonster(state, 1, 2, MAMMOTH, RpgAiDuelState.POS_FACEUP_ATTACK, 1200, 800);
        addHandCard(state, RpgAiCardKnowledge.DARK_MAGICIAN);
        addHandCard(state, RpgAiCardKnowledge.FISSURE);
        RpgAiStrategy.IdleCmdPrompt p3 = new RpgAiStrategy.IdleCmdPrompt();
        p3.summonable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_MAGICIAN, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p3.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.FISSURE, 0, 0, RpgAiDuelState.LOC_HAND, 1, 0, 0, 0));
        p3.canBattle = true;
        p3.canEnd = true;
        assertEquals("Should fire Fissure when clearing sole blocker secures immediate lethal!",
                (0 << 16) + 5, strategy.chooseIdleCmd(p3));
    }

    @Test
    public void trapHoleBaitingSummonsLowerBeaterBeforeBossAgainstSetBackrow() {
        final int MAMMOTH = 40374923; // 1200/800
        final int GAIA = 6368038;     // 2300/2100

        RpgAiDuelState state = new RpgAiDuelState(catalog, noTributeRule, "1999-02-04");
        state.turnCount = 3;
        state.turnPlayer = 0;
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.lp[0] = 8000;
        state.lp[1] = 8000;
        addHandCard(state, RpgAiCardKnowledge.DARK_MAGICIAN);
        addHandCard(state, MAMMOTH);
        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "NO_TRIBUTE_BEAT");

        RpgAiStrategy.IdleCmdPrompt p = new RpgAiStrategy.IdleCmdPrompt();
        p.summonable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_MAGICIAN, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p.summonable.add(new RpgAiStrategy.CmdEntry(MAMMOTH, 0, 0, RpgAiDuelState.LOC_HAND, 1, 0, 0, 1));
        p.canBattle = true;
        p.canEnd = true;

        // Case 1: Opponent has NO set backrow -> summon Dark Magician (index 0) directly!
        assertEquals("With 0 opponent backrow, summon Dark Magician directly",
                0 << 16, strategy.chooseIdleCmd(p));

        // Case 2: Opponent has 1 face-down backrow (potential Trap Hole!) -> summon Mammoth (index 1) FIRST to bait!
        placeSetBackrow(state, 1, 2);
        assertEquals("Against opponent's face-down backrow, summon 1200 Mammoth (index 1) first to bait Trap Hole!",
                1 << 16, strategy.chooseIdleCmd(p));

        // Case 3: Opponent ALSO has face-up Gaia (2300) -> 1200 bait can't handle Gaia -> summon Dark Magician (index 0)!
        placeMonster(state, 1, 2, GAIA, RpgAiDuelState.POS_FACEUP_ATTACK, 2300, 2100);
        assertEquals("When opponent controls face-up 2300 Gaia, summon Dark Magician (index 0) instead of weak bait",
                0 << 16, strategy.chooseIdleCmd(p));
    }

    @Test
    public void equipSpellDisciplineAndBluffSettingFollows1999Metagame() {
        final int MAMMOTH = 40374923; // 1200/800

        RpgAiDuelState state = new RpgAiDuelState(catalog, noTributeRule, "1999-02-04");
        state.turnCount = 3;
        state.turnPlayer = 0;
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        state.lp[0] = 8000;
        state.lp[1] = 8000;
        RpgAiStrategy strategy = new RpgAiStrategy(state, null, "NO_TRIBUTE_BEAT");

        // Equip discipline: Dark Magician (2500) on field vs opponent's 1200 Mammoth + Book of Secret Arts (+300) in hand.
        // Already beats Mammoth without equip -> DO NOT risk 2-for-1! Enter Battle Phase (6) instead!
        placeMonster(state, 0, 2, RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 2100);
        placeMonster(state, 1, 2, MAMMOTH, RpgAiDuelState.POS_FACEUP_ATTACK, 1200, 800);
        addHandCard(state, RpgAiCardKnowledge.BOOK_OF_SECRET_ARTS);

        RpgAiStrategy.IdleCmdPrompt p1 = new RpgAiStrategy.IdleCmdPrompt();
        p1.activatable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.BOOK_OF_SECRET_ARTS, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        p1.canBattle = true;
        p1.canEnd = true;
        assertEquals("Should NOT equip Book of Secret Arts when Dark Magician already beats 1200 Mammoth",
                6, strategy.chooseIdleCmd(p1));

        // Now opponent has Dark Magician (2500) -> tie! Book of Secret Arts flips 2500 vs 2500 into 2800 win -> EQUIP!
        placeMonster(state, 1, 2, RpgAiCardKnowledge.DARK_MAGICIAN, RpgAiDuelState.POS_FACEUP_ATTACK, 2500, 2100);
        assertEquals("Should equip Book of Secret Arts to break 2500 vs 2500 tie!",
                (0 << 16) + 5, strategy.chooseIdleCmd(p1));

        // Bluff Setting: Turn 1, 0 backrow, no real Traps, holding Dark Hole (core spell) + Power of Kaishin (dead equip).
        // MUST bluff-set Power of Kaishin (index 1, +4) to threaten Trap Hole, NEVER Dark Hole!
        state.reset();
        state.turnCount = 1;
        state.turnPlayer = 0;
        state.phase = RpgAiDuelState.PHASE_MAIN1;
        addHandCard(state, RpgAiCardKnowledge.DARK_HOLE);
        addHandCard(state, RpgAiCardKnowledge.POWER_OF_KAISHIN);
        RpgAiStrategy.IdleCmdPrompt pBluff = new RpgAiStrategy.IdleCmdPrompt();
        pBluff.ssetable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.DARK_HOLE, 0, 0, RpgAiDuelState.LOC_HAND, 0, 0, 0, 0));
        pBluff.ssetable.add(new RpgAiStrategy.CmdEntry(RpgAiCardKnowledge.POWER_OF_KAISHIN, 0, 0, RpgAiDuelState.LOC_HAND, 1, 0, 0, 1));
        pBluff.canBattle = false;
        pBluff.canEnd = true;
        assertEquals("Should bluff-set dead Power of Kaishin (index 1) to fake Trap Hole, never Dark Hole",
                (1 << 16) + 4, strategy.chooseIdleCmd(pBluff));

        // Once AI already has 1 set backrow -> do NOT set a 2nd bluff!
        placeSetBackrow(state, 0, 2);
        assertEquals("Should NOT set a 2nd bluff spell when already holding 1 face-down backrow",
                7, strategy.chooseIdleCmd(pBluff));
    }

    private void placeSetBackrow(RpgAiDuelState state, int localPlayer, int seq) {
        RpgAiDuelState.CardSlot s = new RpgAiDuelState.CardSlot();
        s.code = 0;
        s.controller = localPlayer;
        s.owner = localPlayer;
        s.location = RpgAiDuelState.LOC_SZONE;
        s.sequence = seq;
        s.position = RpgAiDuelState.POS_FACEDOWN_DEFENSE;
        state.szone[localPlayer][seq] = s;
    }

    private void simulateOppSummon(RpgAiDuelState state, int code, int seq, int atk, int def) {
        placeMonster(state, 1, seq, code, RpgAiDuelState.POS_FACEUP_ATTACK, atk, def);
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(code)
                .put((byte) state.protoPlayer(1))
                .put((byte) RpgAiDuelState.LOC_MZONE)
                .put((byte) seq)
                .put((byte) RpgAiDuelState.POS_FACEUP_ATTACK);
        b.flip();
        state.onSummoning(b, true);
    }

    private ByteBuffer buildSingleChainPrompt(int trapCode) {
        ByteBuffer b = ByteBuffer.allocate(11 + 14).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 1).put((byte) 1).put((byte) 0).putInt(0).putInt(0);
        b.put((byte) 0).put((byte) 0).putInt(trapCode)
                .put((byte) 1).put((byte) RpgAiDuelState.LOC_SZONE).put((byte) 0).put((byte) 0)
                .putInt(0);
        b.flip();
        return b;
    }

    @Test
    public void allAiOpponentDeckStylesHaveDedicatedStrategySupport() {
        List<cn.garymb.ygomobile.rpg.model.AiOpponent> opponents =
                cn.garymb.ygomobile.rpg.engine.AiDeckGenerator.getDemoOpponents();
        assertTrue("Expected built-in AI opponents", !opponents.isEmpty());
        for (cn.garymb.ygomobile.rpg.model.AiOpponent opp : opponents) {
            assertTrue("Opponent " + opp.id + " must define at least one EraDeckProfile",
                    opp.deckEvolutions != null && !opp.deckEvolutions.isEmpty());
            for (cn.garymb.ygomobile.rpg.model.AiOpponent.EraDeckProfile prof : opp.deckEvolutions) {
                assertTrue(
                        "AI opponent '" + opp.id + "' era deck '" + prof.deckName
                                + "' uses unregistered fillStyle='" + prof.fillStyle
                                + "'. Register its key cards in RpgAiCardKnowledge, add dedicated"
                                + " tactics in RpgAiStrategy, and add it to RpgAiStrategy.SUPPORTED_STYLES!",
                        RpgAiStrategy.SUPPORTED_STYLES.contains(prof.fillStyle)
                );
            }
        }
    }

    @Test
    public void inferStyleFromDeckClassifiesCustomPlayerDecksAccurately() {
        List<Integer> exodia = Arrays.asList(
                RpgAiCardKnowledge.EXODIA_HEAD, RpgAiCardKnowledge.EXODIA_LEFT_ARM,
                RpgAiCardKnowledge.EXODIA_RIGHT_ARM, RpgAiCardKnowledge.EXODIA_LEFT_LEG,
                RpgAiCardKnowledge.EXODIA_RIGHT_LEG, RpgAiCardKnowledge.POT_OF_GREED);
        assertEquals("EXODIA_DRAW", RpgAiStrategy.inferStyleFromDeck(exodia, Collections.emptyList(), tributeRule));

        List<Integer> virus = Arrays.asList(RpgAiCardKnowledge.CRUSH_CARD_VIRUS, RpgAiCardKnowledge.SANGAN);
        assertEquals("DARK_VIRUS", RpgAiStrategy.inferStyleFromDeck(virus, Collections.emptyList(), tributeRule));

        List<Integer> fusion = Arrays.asList(RpgAiCardKnowledge.CYBER_STEIN, RpgAiCardKnowledge.POLYMERIZATION);
        assertEquals("FUSION_COMBO", RpgAiStrategy.inferStyleFromDeck(
                fusion, Collections.singletonList(RpgAiCardKnowledge.BLUE_EYES_ULTIMATE), tributeRule));

        List<Integer> burn = Arrays.asList(RpgAiCardKnowledge.CANNON_SOLDIER, RpgAiCardKnowledge.OOKAZI);
        assertEquals("BURN_WALL", RpgAiStrategy.inferStyleFromDeck(burn, Collections.emptyList(), tributeRule));

        List<Integer> beat = Arrays.asList(RpgAiCardKnowledge.BLUE_EYES, RpgAiCardKnowledge.DARK_MAGICIAN);
        assertEquals("NO_TRIBUTE_BEAT", RpgAiStrategy.inferStyleFromDeck(beat, Collections.emptyList(), noTributeRule));
        assertEquals("GOOD_STUFF", RpgAiStrategy.inferStyleFromDeck(beat, Collections.emptyList(), tributeRule));
    }

    @Test
    public void endToEndTcpProtocolHandshakeAndIdleDecisionRoundTrip() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            List<Integer> main = Arrays.asList(RpgAiCardKnowledge.BLUE_EYES, RpgAiCardKnowledge.DARK_HOLE);
            RpgAiDuelClient client = new RpgAiDuelClient(
                    "127.0.0.1", port, 0x1360, null, "NO_TRIBUTE_BEAT",
                    main, Collections.emptyList(), Collections.emptyList(),
                    catalog, noTributeRule, "1999-03-06", 0L);
            client.start();

            try (Socket accepted = server.accept()) {
                accepted.setSoTimeout(3000);
                InputStream in = accepted.getInputStream();
                OutputStream out = accepted.getOutputStream();

                // 1. Expect CTOS_PLAYER_INFO & CTOS_JOIN_GAME
                byte[] p1 = readPacket(in);
                assertEquals(RpgAiDuelClient.CTOS_PLAYER_INFO, p1[0] & 0xFF);
                byte[] p2 = readPacket(in);
                assertEquals(RpgAiDuelClient.CTOS_JOIN_GAME, p2[0] & 0xFF);

                // 2. Send STOC_JOIN_GAME -> expect CTOS_UPDATE_DECK & CTOS_HS_READY
                sendPacket(out, RpgAiDuelClient.STOC_JOIN_GAME, new byte[20]);
                byte[] p3 = readPacket(in);
                assertEquals(RpgAiDuelClient.CTOS_UPDATE_DECK, p3[0] & 0xFF);
                byte[] p4 = readPacket(in);
                assertEquals(RpgAiDuelClient.CTOS_HS_READY, p4[0] & 0xFF);

                // 3. Send STOC_SELECT_HAND -> expect CTOS_HAND_RESULT in 1..3
                sendPacket(out, RpgAiDuelClient.STOC_SELECT_HAND, new byte[0]);
                byte[] p5 = readPacket(in);
                assertEquals(RpgAiDuelClient.CTOS_HAND_RESULT, p5[0] & 0xFF);
                int handChoice = p5[1] & 0xFF;
                assertTrue(handChoice >= 1 && handChoice <= 3);

                // 4. Send STOC_SELECT_TP -> expect CTOS_TP_RESULT (NO_TRIBUTE_BEAT prefers going 2nd = 0)
                sendPacket(out, RpgAiDuelClient.STOC_SELECT_TP, new byte[0]);
                byte[] p6 = readPacket(in);
                assertEquals(RpgAiDuelClient.CTOS_TP_RESULT, p6[0] & 0xFF);
                assertEquals(0, p6[1] & 0xFF);

                // 5. Send MSG_START (AI is player 1) + MSG_SELECT_IDLECMD (summon Blue-Eyes)
                ByteBuffer startMsg = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
                startMsg.put((byte) 4).put((byte) 1).put((byte) 1)
                        .putInt(8000).putInt(8000)
                        .putShort((short) 40).putShort((short) 0)
                        .putShort((short) 40).putShort((short) 0);
                sendPacket(out, RpgAiDuelClient.STOC_GAME_MSG, startMsg.array());

                ByteBuffer idleMsg = ByteBuffer.allocate(1 + 1 + 1 + 7 + 4 + 1 + 3).order(ByteOrder.LITTLE_ENDIAN);
                idleMsg.put((byte) 11) // MSG_SELECT_IDLECMD
                        .put((byte) 1) // selecting player
                        .put((byte) 1) // summonable count = 1
                        .putInt(RpgAiCardKnowledge.BLUE_EYES).put((byte) 1).put((byte) RpgAiDuelState.LOC_HAND).put((byte) 0)
                        .put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0) // sp, repos, mset, sset
                        .put((byte) 0) // activatable
                        .put((byte) 1).put((byte) 1).put((byte) 0); // canBattle, canEnd, canShuffle
                sendPacket(out, RpgAiDuelClient.STOC_GAME_MSG, idleMsg.array());

                byte[] respPkt = readPacket(in);
                assertEquals(RpgAiDuelClient.CTOS_RESPONSE, respPkt[0] & 0xFF);
                int respInt = ByteBuffer.wrap(respPkt, 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                assertEquals("AI should Normal Summon Blue-Eyes (index 0 << 16)", 0, respInt);

                sendPacket(out, RpgAiDuelClient.STOC_DUEL_END, new byte[0]);
            } finally {
                client.stop();
            }
        }
    }

    private static void sendPacket(OutputStream out, int proto, byte[] body) throws Exception {
        int len = 1 + body.length;
        ByteBuffer b = ByteBuffer.allocate(2 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) len);
        b.put((byte) proto);
        b.put(body);
        out.write(b.array());
        out.flush();
    }

    private static byte[] readPacket(InputStream in) throws Exception {
        byte[] hdr = new byte[2];
        int n = 0;
        while (n < 2) {
            int r = in.read(hdr, n, 2 - n);
            if (r < 0) throw new IllegalStateException("EOF reading header");
            n += r;
        }
        int len = (hdr[0] & 0xFF) | ((hdr[1] & 0xFF) << 8);
        byte[] buf = new byte[len];
        n = 0;
        while (n < len) {
            int r = in.read(buf, n, len - n);
            if (r < 0) throw new IllegalStateException("EOF reading body");
            n += r;
        }
        return buf;
    }
}
