package cn.garymb.ygomobile.rpg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.rpg.data.RpgCatalogLoader;
import cn.garymb.ygomobile.rpg.data.RpgRepository;
import cn.garymb.ygomobile.rpg.engine.AiDeckGenerator;
import cn.garymb.ygomobile.rpg.engine.CraftingEngine;
import cn.garymb.ygomobile.rpg.engine.DeckValidator;
import cn.garymb.ygomobile.rpg.engine.DuelRewardEngine;
import cn.garymb.ygomobile.rpg.engine.PackOpeningEngine;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

public class RpgEnginesTest {
    private static final int DARK_MAGICIAN = 46986414; // Vol.1 UR
    private static final int DARK_HOLE = 53129443;     // Vol.1 SR
    private static final int TRAP_HOLE = 4206964;      // Vol.1 SR
    private static final int MONSTER_REBORN = 83764718; // Vol.2 SR

    private RpgCatalog catalog;

    @Before
    public void setUp() throws Exception {
        File assetFile = new File("src/main/assets/rpg/ocg_demo_catalog.json");
        try (FileInputStream fis = new FileInputStream(assetFile)) {
            catalog = RpgCatalogLoader.loadFromStream(fis);
        }
    }

    @Test
    public void catalogContainsFullOcgVol1AndVol2Data() {
        assertEquals("OCG", catalog.format);
        assertEquals(2, catalog.packs.size());
        assertEquals(40, catalog.findPack("ocg_vol1").cards.size());
        assertEquals(40, catalog.findPack("ocg_vol2").cards.size());
        assertEquals("黑魔术师", catalog.findCard(DARK_MAGICIAN).nameCn);
        assertEquals("死者苏生", catalog.findCard(MONSTER_REBORN).nameCn);
    }

    @Test
    public void timelineControlsPackAvailabilityAndActiveBanlist() {
        // 1999-02-04: only Vol.1, unrestricted
        List<OcgPack> febPacks = TimelineEngine.getAvailablePacks(catalog, "1999-02-04");
        assertEquals(1, febPacks.size());
        assertEquals("ocg_vol1", febPacks.get(0).id);
        OcgBanlist febBan = TimelineEngine.getActiveBanlist(catalog, "1999-02-04");
        assertEquals(3, febBan.getMaxCopies(DARK_HOLE));
        assertEquals(3, febBan.getMaxCopies(TRAP_HOLE));

        // 1999-03-27: Vol.1 + Vol.2 unlocked, still unrestricted
        List<OcgPack> marPacks = TimelineEngine.getAvailablePacks(catalog, "1999-03-27");
        assertEquals(2, marPacks.size());

        // 1999-08-26: First OCG banlist (Dark Hole & Trap Hole limited to 1)
        OcgBanlist augBan = TimelineEngine.getActiveBanlist(catalog, "1999-08-26");
        assertEquals(1, augBan.getMaxCopies(DARK_HOLE));
        assertEquals(1, augBan.getMaxCopies(TRAP_HOLE));
        assertEquals(3, augBan.getMaxCopies(MONSTER_REBORN));

        // 2000-04-01: Dark Hole stays 1, Trap Hole unlimited (3), Monster Reborn semi-limited (2)
        OcgBanlist apr2000Ban = TimelineEngine.getActiveBanlist(catalog, "2000-04-01");
        assertEquals(1, apr2000Ban.getMaxCopies(DARK_HOLE));
        assertEquals(3, apr2000Ban.getMaxCopies(TRAP_HOLE));
        assertEquals(2, apr2000Ban.getMaxCopies(MONSTER_REBORN));
    }

    @Test
    public void packOpeningRespectsTimelineTokensAndPity() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-02-04";
        PackOpeningEngine engine = new PackOpeningEngine(new Random(42L));

        // Cannot buy Vol.2 before 1999-03-27
        PackOpeningEngine.PackOpenResult blocked = engine.openPacks(profile, catalog.findPack("ocg_vol2"), 1);
        assertFalse(blocked.success);
        assertEquals(1200, profile.tokens);

        // Open 5 packs of Vol.1 -> 25 cards, 500 tokens spent, guaranteed >= 1 R+
        PackOpeningEngine.PackOpenResult res = engine.openPacks(profile, catalog.findPack("ocg_vol1"), 5);
        assertTrue(res.success);
        assertEquals(25, res.pulledCards.size());
        assertEquals(700, profile.tokens);
        assertEquals(25, profile.getTotalOwnedCards());
        boolean hasRareOrBetter = false;
        for (PackOpeningEngine.PulledCard pc : res.pulledCards) {
            if (pc.card.getRarityEnum().ordinal() >= 1) {
                hasRareOrBetter = true;
                break;
            }
        }
        assertTrue("5-pack pity must guarantee at least one R or higher card", hasRareOrBetter);
    }

    @Test
    public void craftingAndDismantlingObeyTimelineAndUpdateDeck() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-02-04";
        profile.dust = 1000;

        // Cannot craft Vol.2 card on 1999-02-04
        CraftingEngine.CraftResult blockedCraft = CraftingEngine.craft(profile, catalog, MONSTER_REBORN, 1);
        assertFalse(blockedCraft.success);

        // Can craft Vol.1 Dark Hole (SR = 240 dust)
        CraftingEngine.CraftResult craftOk = CraftingEngine.craft(profile, catalog, DARK_HOLE, 2);
        assertTrue(craftOk.success);
        assertEquals(2, profile.getOwnedCount(DARK_HOLE));
        assertEquals(520, profile.dust);

        // Put both in deck, then dismantle 1 -> deck should auto-trim to 1 copy
        profile.activeDeckMain.add(DARK_HOLE);
        profile.activeDeckMain.add(DARK_HOLE);
        CraftingEngine.CraftResult disOk = CraftingEngine.dismantle(profile, catalog, DARK_HOLE, 1);
        assertTrue(disOk.success);
        assertEquals(1, profile.getOwnedCount(DARK_HOLE));
        assertEquals(600, profile.dust);
        assertEquals(1, profile.activeDeckMain.size());

        // Advance timeline to 1999-03-27 -> now Monster Reborn can be crafted
        profile.currentTimelineDate = "1999-03-27";
        CraftingEngine.CraftResult craftVol2 = CraftingEngine.craft(profile, catalog, MONSTER_REBORN, 1);
        assertTrue(craftVol2.success);
        assertEquals(1, profile.getOwnedCount(MONSTER_REBORN));
    }

    @Test
    public void deckValidatorEnforcesOwnershipTimelineAndBanlist() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-03-27";

        // Give player 3 copies of every Vol.1 & Vol.2 card
        for (RpgCard c : TimelineEngine.getReleasedCards(catalog, "1999-03-27")) {
            profile.addCard(c.id, 3);
        }

        // Build a 40-card deck containing 3x Dark Hole, 3x Trap Hole, 3x Monster Reborn
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 3; i++) deck.add(DARK_HOLE);
        for (int i = 0; i < 3; i++) deck.add(TRAP_HOLE);
        for (int i = 0; i < 3; i++) deck.add(MONSTER_REBORN);
        for (RpgCard c : catalog.findPack("ocg_vol1").cards) {
            if (c.id == DARK_HOLE || c.id == TRAP_HOLE) continue;
            for (int i = 0; i < 3 && deck.size() < 40; i++) {
                deck.add(c.id);
            }
        }
        profile.activeDeckMain = deck;

        // Legal on 1999-03-27
        assertTrue(DeckValidator.validatePlayerDeck(profile, catalog).valid);

        // Move timeline BACK to 1999-02-04 -> Monster Reborn (Vol.2) becomes illegal
        profile.currentTimelineDate = "1999-02-04";
        assertFalse(DeckValidator.validatePlayerDeck(profile, catalog).valid);

        // Move timeline FORWARD to 1999-08-26 -> Dark Hole & Trap Hole exceed limit 1
        profile.currentTimelineDate = "1999-08-26";
        DeckValidator.ValidationResult augRes = DeckValidator.validatePlayerDeck(profile, catalog);
        assertFalse(augRes.valid);
        assertTrue(augRes.errors.size() >= 2);

        // Move timeline FORWARD to 2000-04-01 -> Dark Hole (>1) and Monster Reborn (>2) illegal, Trap Hole (3) legal
        profile.currentTimelineDate = "2000-04-01";
        DeckValidator.ValidationResult aprRes = DeckValidator.validatePlayerDeck(profile, catalog);
        assertFalse(aprRes.valid);
        boolean mentionsTrapHole = false;
        for (String err : aprRes.errors) {
            if (err.contains("落穴")) mentionsTrapHole = true;
        }
        assertFalse("Trap Hole was unlimited on 2000-04-01", mentionsTrapHole);
    }

    @Test
    public void aiDecksStrictlyObeyCurrentTimelineAndBanlist() {
        List<AiOpponent> opps = AiDeckGenerator.getDemoOpponents();
        String[] dates = {"1999-02-04", "1999-03-27", "1999-08-26", "2000-04-01"};

        for (String date : dates) {
            for (AiOpponent opp : TimelineEngine.getAvailableOpponents(opps, date)) {
                AiDeckGenerator.GeneratedAiDeck aiDeck =
                        AiDeckGenerator.generateDeck(catalog, opp, date, new Random(123L));
                assertEquals(40, aiDeck.main.size());
                DeckValidator.ValidationResult res =
                        DeckValidator.validateDeck(aiDeck.main, aiDeck.extra, catalog, date, null);
                assertTrue("AI deck for " + opp.id + " at " + date + " must be legal: " + res.errors,
                        res.valid);
            }
        }
    }

    @Test
    public void duelRewardAndRepositoryRoundTripWork() throws Exception {
        File tmpDir = Files.createTempDirectory("rpg_repo_test").toFile();
        RpgRepository repo = new RpgRepository(tmpDir);
        RpgProfile profile = repo.loadProfile();
        AiOpponent opp = AiDeckGenerator.getDemoOpponents().get(0);

        DuelRewardEngine.RewardSettlement win = DuelRewardEngine.settle(profile, opp, true, false);
        assertEquals(150, win.tokensEarned);
        assertEquals(1350, profile.tokens);
        assertEquals(1, profile.wins);

        repo.saveProfile(profile);
        RpgProfile reloaded = repo.loadProfile();
        assertEquals(1350, reloaded.tokens);
        assertEquals(1, reloaded.wins);
    }

    @Test
    public void timelineRuleVersionAutoSelectsLatestOcgEra() {
        // 1999-02-04: Official Rule (no tributes, 1 spell/trap per turn, no first-turn draw)
        cn.garymb.ygomobile.rpg.model.OcgRuleSet r1999Feb =
                TimelineEngine.getActiveRuleSet(catalog, "1999-02-04");
        assertFalse(r1999Feb.tributeSummonEnabled);
        assertTrue(r1999Feb.spellTrapOncePerTurnLimit);
        assertFalse(r1999Feb.firstTurnDrawEnabled);
        assertEquals(0, r1999Feb.requiredTributes(7));

        // 1999-05-05: Expert Rule (tribute summon introduced, first-turn draw enabled)
        cn.garymb.ygomobile.rpg.model.OcgRuleSet r1999May =
                TimelineEngine.getActiveRuleSet(catalog, "1999-05-05");
        assertTrue(r1999May.tributeSummonEnabled);
        assertFalse(r1999May.spellTrapOncePerTurnLimit);
        assertTrue(r1999May.firstTurnDrawEnabled);
        assertEquals(1, r1999May.requiredTributes(5));
        assertEquals(2, r1999May.requiredTributes(7));

        // 2014-03-21: Master Rule 3 (first-turn draw removed, duelRule = 3)
        cn.garymb.ygomobile.rpg.model.OcgRuleSet r2014 =
                TimelineEngine.getActiveRuleSet(catalog, "2014-03-21");
        assertFalse(r2014.firstTurnDrawEnabled);
        assertEquals(3, r2014.ygoproDuelRule);

        // 2020-04-01: Master Rule 5 (duelRule = 5)
        cn.garymb.ygomobile.rpg.model.OcgRuleSet r2020 =
                TimelineEngine.getActiveRuleSet(catalog, "2020-04-01");
        assertEquals(5, r2020.ygoproDuelRule);
    }

    @Test
    public void quickDuelSimulatorCompletesWithTimelineRules() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-03-27";
        PackOpeningEngine packEngine = new PackOpeningEngine(new Random(7L));
        packEngine.openPacks(profile, catalog.findPack("ocg_vol1"), 6);
        packEngine.openPacks(profile, catalog.findPack("ocg_vol2"), 6);
        DeckValidator.autoBuildLegalPlayerDeck(profile, catalog);

        AiOpponent opp = AiDeckGenerator.getDemoOpponents().get(0);
        AiDeckGenerator.GeneratedAiDeck aiDeck =
                AiDeckGenerator.generateDeck(catalog, opp, profile.currentTimelineDate, new Random(7L));
        cn.garymb.ygomobile.rpg.engine.QuickDuelSimulator.QuickDuelReport report =
                cn.garymb.ygomobile.rpg.engine.QuickDuelSimulator.simulate(
                        catalog, profile.activeDeckMain, aiDeck.main, opp,
                        profile.currentTimelineDate, new Random(99L));

        assertEquals("ocg_official_1999", report.appliedRule.id);
        assertFalse(report.logs.isEmpty());
        assertTrue(report.turns >= 1);
    }
}
