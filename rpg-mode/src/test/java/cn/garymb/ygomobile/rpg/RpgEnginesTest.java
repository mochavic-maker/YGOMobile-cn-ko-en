package cn.garymb.ygomobile.rpg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import cn.garymb.ygomobile.rpg.engine.CardErrataEngine;
import cn.garymb.ygomobile.rpg.engine.CraftingEngine;
import cn.garymb.ygomobile.rpg.engine.DeckValidator;
import cn.garymb.ygomobile.rpg.engine.DuelRewardEngine;
import cn.garymb.ygomobile.rpg.engine.EventClaimEngine;
import cn.garymb.ygomobile.rpg.engine.PackOpeningEngine;
import cn.garymb.ygomobile.rpg.engine.QuickDuelSimulator;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgCardErrata;
import cn.garymb.ygomobile.rpg.model.OcgEvent;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

public class RpgEnginesTest {
    private static final int DARK_MAGICIAN = 46986414;  // Vol.1 UR
    private static final int DARK_HOLE = 53129443;      // Vol.1 SR
    private static final int TRAP_HOLE = 4206964;       // Vol.1 SR
    private static final int FISSURE = 66788016;        // Vol.1 R
    private static final int BLUE_EYES = 89631139;      // Starter Box UR
    private static final int RAIGEKI = 12580477;        // Starter Box SR
    private static final int MONSTER_REBORN = 83764718; // Vol.2 SR
    private static final int CELTIC_GUARDIAN = 91152256;// Starter Box pre-order promo SR
    private static final int POT_OF_GREED = 55144522;   // Vol.3 N
    private static final int EXODIA_HEAD = 33396948;    // Premium Pack UR
    private static final int BLACK_LUSTER = 5405694;    // Premium Pack 2 UR
    private static final int DE_SPELL = 19159413;       // EX x6
    private static final int SANGAN = 26202165;         // Vol.6 R
    private static final int WITCH = 78010363;          // Vol.6 R
    private static final int CCV = 57728570;            // DM2 Promo UR
    private static final int TIME_WIZARD = 71625222;    // PP1 UR
    private static final int GRACEFUL_CHARITY = 79571449;// Vol.5 SR
    private static final int CHAOS_EMPEROR = 82301904;  // Series 3 Unban Errata
    private static final int FIREWALL_DRAGON = 5043010; // Series 10 Unban Errata

    private RpgCatalog catalog;

    @Before
    public void setUp() throws Exception {
        File assetFile = new File("src/main/assets/rpg/ocg_demo_catalog.json");
        File errataFile = new File("src/main/assets/rpg/ocg_errata_history.json");
        try (FileInputStream fis = new FileInputStream(assetFile);
             FileInputStream efis = new FileInputStream(errataFile)) {
            catalog = RpgCatalogLoader.loadFromStreams(fis, efis);
        }
    }

    @Test
    public void catalogContainsFull1999OcgLineupWithAuthenticJpyPrices() {
        assertEquals("OCG", catalog.format);
        assertEquals("1999-02-04", catalog.minTimelineDate);
        assertEquals("1999-12-31", catalog.maxTimelineDate);
        assertEquals(20, catalog.packs.size());
        assertEquals(11, catalog.events.size());
        assertTrue("Full 1999 catalog should have 600+ unique OCG cards",
                catalog.getAllCardsMap().size() >= 600);

        // Authentic JPY pricing
        assertEquals(150, catalog.findPack("ocg_vol1").priceTokens);
        assertEquals(100, catalog.findPack("ocg_booster1").priceTokens);
        assertEquals(3, catalog.findPack("ocg_booster1").cardsPerPack);
        assertEquals(3480, catalog.findPack("ocg_starter_box_theater").priceTokens);
        assertEquals(3480, catalog.findPack("ocg_starter_box").priceTokens);
        assertEquals(50, catalog.findPack("ocg_starter_box").cards.size());
        assertEquals(300, catalog.findPack("ocg_le1_yugi").priceTokens);
        assertEquals(200, catalog.findPack("ocg_pp1").priceTokens);
        assertEquals(2000, catalog.findPack("ocg_ex_starter_box").priceTokens);
        assertEquals(102, catalog.findPack("ocg_ex_starter_box").cardsPerPack);
        assertEquals(500, catalog.findPack("ocg_pp2").priceTokens);

        assertEquals("黑魔术师", catalog.findCard(DARK_MAGICIAN).nameCn);
        assertEquals("青眼白龙", catalog.findCard(BLUE_EYES).nameCn);
        assertEquals("雷击", catalog.findCard(RAIGEKI).nameCn);
        assertEquals("死者苏生", catalog.findCard(MONSTER_REBORN).nameCn);
    }

    @Test
    public void timelineControls1999ProductsBanlistAndBounds() {
        assertEquals("1999-02-04", TimelineEngine.clampDate(catalog, "1998-01-01"));
        assertEquals("1999-12-31", TimelineEngine.clampDate(catalog, "2001-01-01"));

        // 1999-02-04: only Vol.1
        List<OcgPack> febPacks = TimelineEngine.getAvailablePacks(catalog, "1999-02-04");
        assertEquals(1, febPacks.size());
        assertEquals("ocg_vol1", febPacks.get(0).id);

        // 1999-03-01: Vol.1 + Booster 1
        assertEquals(2, TimelineEngine.getAvailablePacks(catalog, "1999-03-01").size());

        // 1999-03-18: Vol.1 + Booster 1 + Starter Box (Theatrical + Retail)
        assertEquals(4, TimelineEngine.getAvailablePacks(catalog, "1999-03-18").size());

        // 1999-03-27: + Vol.2
        assertEquals(5, TimelineEngine.getAvailablePacks(catalog, "1999-03-27").size());

        // 1999-12-31: all 20 products & all 11 events unlocked
        assertEquals(20, TimelineEngine.getAvailablePacks(catalog, "1999-12-31").size());
        assertEquals(11, TimelineEngine.getAvailableEvents(catalog, "1999-12-31").size());

        // 1999-02-04: unrestricted
        OcgBanlist febBan = TimelineEngine.getActiveBanlist(catalog, "1999-02-04");
        assertEquals(3, febBan.getMaxCopies(DARK_HOLE));
        assertEquals(3, febBan.getMaxCopies(TRAP_HOLE));
        assertEquals(3, febBan.getMaxCopies(RAIGEKI));

        // 1999-08-26: First OCG banlist (Dark Hole, Trap Hole, Raigeki limited to 1)
        OcgBanlist augBan = TimelineEngine.getActiveBanlist(catalog, "1999-08-26");
        assertEquals(1, augBan.getMaxCopies(DARK_HOLE));
        assertEquals(1, augBan.getMaxCopies(TRAP_HOLE));
        assertEquals(1, augBan.getMaxCopies(RAIGEKI));
        assertEquals(3, augBan.getMaxCopies(MONSTER_REBORN));
        assertEquals(3, augBan.getMaxCopies(POT_OF_GREED));
    }

    @Test
    public void packOpeningSupportsVolBoosterFixedStarterBoxAndExBox() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-02-04";
        PackOpeningEngine engine = new PackOpeningEngine(new Random(42L));

        // Cannot buy Booster 1 before 1999-03-01
        assertFalse(engine.openPacks(profile, catalog.findPack("ocg_booster1"), 1).success);

        // Open 5 packs of Vol.1 -> 25 cards, 750 tokens spent (150 x 5), guaranteed >= 1 R+
        PackOpeningEngine.PackOpenResult res = engine.openPacks(profile, catalog.findPack("ocg_vol1"), 5);
        assertTrue(res.success);
        assertEquals(25, res.pulledCards.size());
        assertEquals(750, res.tokensSpent);
        assertEquals(750, profile.tokens);
        boolean hasRareOrBetter = false;
        for (PackOpeningEngine.PulledCard pc : res.pulledCards) {
            if (pc.card.getRarityEnum().ordinal() >= 1) {
                hasRareOrBetter = true;
                break;
            }
        }
        assertTrue("5-pack pity must guarantee at least one R or higher card", hasRareOrBetter);

        // Advance to 1999-03-01 -> Booster 1 Carddass vending machine (100 tokens -> 3 OCG cards)
        profile.currentTimelineDate = "1999-03-01";
        PackOpeningEngine.PackOpenResult booRes = engine.openPacks(profile, catalog.findPack("ocg_booster1"), 2);
        assertTrue(booRes.success);
        assertEquals(6, booRes.pulledCards.size());
        assertEquals(200, booRes.tokensSpent);

        // Advance to 1999-03-18 -> buy Starter Box (fixed 50 cards, 3480 tokens)
        profile.currentTimelineDate = "1999-03-18";
        profile.tokens = 4000;
        PackOpeningEngine.PackOpenResult stRes = engine.openPacks(profile, catalog.findPack("ocg_starter_box"), 1);
        assertTrue(stRes.success);
        assertEquals(50, stRes.pulledCards.size());
        assertEquals(3480, stRes.tokensSpent);
        assertEquals(1, profile.getOwnedCount(BLUE_EYES));
        assertEquals(1, profile.getOwnedCount(RAIGEKI));

        // Advance to 1999-12-16 -> buy EX Starter Box (fixed 102 cards including 6x De-Spell, 2000 tokens)
        profile.currentTimelineDate = "1999-12-16";
        profile.tokens = 2500;
        PackOpeningEngine.PackOpenResult exRes = engine.openPacks(profile, catalog.findPack("ocg_ex_starter_box"), 1);
        assertTrue(exRes.success);
        assertEquals(102, exRes.pulledCards.size());
        assertEquals(2000, exRes.tokensSpent);
        assertTrue("EX Starter Box must grant 6 copies of De-Spell", profile.getOwnedCount(DE_SPELL) >= 6);
    }

    @Test
    public void specialPromoEventsEnforceDateWinRequirementAndClaimOnce() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-02-04";

        // Starter Box pre-order Celtic Guardian not unlocked on 1999-02-04
        OcgEvent preorder = catalog.findEvent("evt_1999_03_18_starter_preorder");
        assertNotNull(preorder);
        assertFalse(EventClaimEngine.claim(profile, preorder, new Random(1L)).success);

        // Advance to 1999-03-18 -> claim Celtic Guardian once
        profile.currentTimelineDate = "1999-03-18";
        EventClaimEngine.ClaimResult r1 = EventClaimEngine.claim(profile, preorder, new Random(1L));
        assertTrue(r1.success);
        assertEquals(1, r1.grantedCards.size());
        assertEquals(1, profile.getOwnedCount(CELTIC_GUARDIAN));
        // Second claim blocked
        assertFalse(EventClaimEngine.claim(profile, preorder, new Random(1L)).success);

        // DM2 GBC promo: RANDOM_PICK 3 distinct cards on 1999-07-08
        profile.currentTimelineDate = "1999-07-08";
        OcgEvent dm2 = catalog.findEvent("evt_1999_07_08_dm2_game");
        EventClaimEngine.ClaimResult rDm2 = EventClaimEngine.claim(profile, dm2, new Random(7L));
        assertTrue(rDm2.success);
        assertEquals(3, rDm2.grantedCards.size());

        // Tokyo Dome championship prizes require >= 1 win
        profile.currentTimelineDate = "1999-08-26";
        OcgEvent domePrizes = catalog.findEvent("evt_1999_08_26_tokyo_dome_prizes");
        assertFalse(EventClaimEngine.claim(profile, domePrizes, new Random(1L)).success);
        profile.wins = 1;
        EventClaimEngine.ClaimResult rDome = EventClaimEngine.claim(profile, domePrizes, new Random(1L));
        assertTrue(rDome.success);
        assertEquals(6, rDome.grantedCards.size());
    }

    @Test
    public void craftingAndDismantlingObeyTimelineAndUpdateDeck() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-02-04";
        profile.dust = 1000;

        // Cannot craft Vol.2 card on 1999-02-04
        assertFalse(CraftingEngine.craft(profile, catalog, MONSTER_REBORN, 1).success);

        // Can craft Vol.1 Dark Hole (SR = 240 dust)
        assertTrue(CraftingEngine.craft(profile, catalog, DARK_HOLE, 2).success);
        assertEquals(2, profile.getOwnedCount(DARK_HOLE));
        assertEquals(520, profile.dust);

        profile.activeDeckMain.add(DARK_HOLE);
        profile.activeDeckMain.add(DARK_HOLE);
        assertTrue(CraftingEngine.dismantle(profile, catalog, DARK_HOLE, 1).success);
        assertEquals(1, profile.getOwnedCount(DARK_HOLE));
        assertEquals(600, profile.dust);
        assertEquals(1, profile.activeDeckMain.size());

        // Advance to 1999-12-18 -> Premium Pack 2 Black Luster Soldier can be crafted
        profile.currentTimelineDate = "1999-12-18";
        assertTrue(CraftingEngine.craft(profile, catalog, BLACK_LUSTER, 1).success);
        assertEquals(1, profile.getOwnedCount(BLACK_LUSTER));
    }

    @Test
    public void deckValidatorEnforcesOwnershipTimelineAnd1999Banlist() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-03-27";

        for (RpgCard c : TimelineEngine.getReleasedCards(catalog, "1999-03-27")) {
            profile.addCard(c.id, 3);
        }

        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 3; i++) deck.add(DARK_HOLE);
        for (int i = 0; i < 3; i++) deck.add(TRAP_HOLE);
        for (int i = 0; i < 3; i++) deck.add(RAIGEKI);
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

        // Move BACK to 1999-02-04 -> Raigeki & Monster Reborn not yet released
        profile.currentTimelineDate = "1999-02-04";
        assertFalse(DeckValidator.validatePlayerDeck(profile, catalog).valid);

        // Move FORWARD to 1999-08-26 -> Dark Hole, Trap Hole, Raigeki exceed limit 1
        profile.currentTimelineDate = "1999-08-26";
        DeckValidator.ValidationResult augRes = DeckValidator.validatePlayerDeck(profile, catalog);
        assertFalse(augRes.valid);
        assertTrue(augRes.errors.size() >= 3);
    }

    @Test
    public void aiDecksStrictlyObeyFull1999TimelineAndBanlist() {
        List<AiOpponent> opps = AiDeckGenerator.getDemoOpponents();
        assertEquals(12, opps.size());
        String[] dates = {
                "1999-02-04", "1999-03-01", "1999-03-06", "1999-03-27", "1999-05-05",
                "1999-05-25", "1999-05-27", "1999-07-08", "1999-07-22",
                "1999-08-26", "1999-10-17", "1999-11-18", "1999-12-01",
                "1999-12-16", "1999-12-31"
        };

        for (String date : dates) {
            List<AiOpponent> avail = TimelineEngine.getAvailableOpponents(opps, date);
            assertFalse(avail.isEmpty());
            for (AiOpponent opp : avail) {
                AiDeckGenerator.GeneratedAiDeck aiDeck =
                        AiDeckGenerator.generateDeck(catalog, opp, date, new Random(123L));
                assertNotNull("Active EraDeckProfile must be selected", aiDeck.appliedProfile);
                assertTrue("Applied deck profile date must be <= currentDate",
                        aiDeck.appliedProfile.effectiveDate.compareTo(date) <= 0);
                assertEquals("AI main deck must be 40 cards for " + opp.id + " at " + date,
                        40, aiDeck.main.size());
                DeckValidator.ValidationResult res =
                        DeckValidator.validateDeck(aiDeck.main, aiDeck.extra, catalog, date, null);
                assertTrue("AI deck for " + opp.id + " (" + aiDeck.appliedProfile.deckName + ") at "
                        + date + " must be legal: " + res.errors, res.valid);
                // Verify progressive non-top-tier AIs do NOT run blowout staples like Raigeki / Dark Hole / Pot of Greed
                if ("ai_street_novice".equals(opp.id) || "ai_shop_regular".equals(opp.id) || "ai_weekend_challenger".equals(opp.id)) {
                    assertFalse(opp.id + " should not pack Raigeki", aiDeck.main.contains(RAIGEKI));
                    assertFalse(opp.id + " should not pack Dark Hole", aiDeck.main.contains(DARK_HOLE));
                    assertFalse(opp.id + " should not pack Pot of Greed", aiDeck.main.contains(POT_OF_GREED));
                }
            }
        }

        // Verify automatic deck switching along timeline for the SAME opponent (Kaiba & Exodia Master)
        AiOpponent kaiba = null;
        AiOpponent exodia = null;
        for (AiOpponent o : opps) {
            if ("ai_starter_kaiba".equals(o.id)) kaiba = o;
            if ("ai_exodia_master".equals(o.id)) exodia = o;
        }
        assertNotNull(kaiba);
        assertNotNull(exodia);

        // Kaiba auto-switches from No-Tribute Blue-Eyes (03-06) -> Expert Transition (05-05) -> High ATK (08-26) -> EX (12-16)
        assertEquals("1999-03-06", kaiba.getActiveDeckProfile("1999-03-18").effectiveDate);
        assertEquals("1999-05-05", kaiba.getActiveDeckProfile("1999-05-05").effectiveDate);
        assertEquals("1999-08-26", kaiba.getActiveDeckProfile("1999-08-26").effectiveDate);
        assertEquals("1999-12-16", kaiba.getActiveDeckProfile("1999-12-31").effectiveDate);

        // Exodia Master auto-switches from PP1 Hybrid (08-26) -> Vol.6 Pre-Errata Sangan/Witch (11-18) -> EX Last Will Loop (12-16)
        assertEquals("1999-08-26", exodia.getActiveDeckProfile("1999-09-23").effectiveDate);
        assertEquals("1999-11-18", exodia.getActiveDeckProfile("1999-11-18").effectiveDate);
        assertEquals("1999-12-16", exodia.getActiveDeckProfile("1999-12-19").effectiveDate);
    }

    @Test
    public void duelRewardAndRepositoryRoundTripWork() throws Exception {
        File tmpDir = Files.createTempDirectory("rpg_repo_test").toFile();
        RpgRepository repo = new RpgRepository(tmpDir);
        RpgProfile profile = repo.loadProfile();
        AiOpponent opp = AiDeckGenerator.getDemoOpponents().get(0);

        DuelRewardEngine.RewardSettlement win = DuelRewardEngine.settle(profile, opp, true, false);
        assertEquals(150, win.tokensEarned);
        assertEquals(1650, profile.tokens);
        assertEquals(1, profile.wins);

        profile.claimedEventIds.add("evt_1999_02_21_dm1_nationals");
        repo.saveProfile(profile);
        RpgProfile reloaded = repo.loadProfile();
        assertEquals(1650, reloaded.tokens);
        assertTrue(reloaded.claimedEventIds.contains("evt_1999_02_21_dm1_nationals"));
    }

    @Test
    public void timelineRuleVersionAutoSelects1999OfficialAndExpertRules() {
        OcgRuleSet rFeb = TimelineEngine.getActiveRuleSet(catalog, "1999-02-04");
        assertEquals("ocg_official_1999", rFeb.id);
        assertEquals(8000, rFeb.startLp);
        assertFalse(rFeb.tributeSummonEnabled);
        assertTrue(rFeb.spellTrapOncePerTurnLimit);
        assertFalse(rFeb.firstTurnDrawEnabled);
        assertTrue(rFeb.deckOutCompareLp);
        assertEquals(0, rFeb.requiredTributes(8));

        OcgRuleSet rMay = TimelineEngine.getActiveRuleSet(catalog, "1999-05-05");
        assertEquals("ocg_expert_1999", rMay.id);
        assertEquals(8000, rMay.startLp);
        assertTrue(rMay.tributeSummonEnabled);
        assertFalse(rMay.spellTrapOncePerTurnLimit);
        // Series 1 Expert Rule (1999.05 ~ 2000.04) still had no Turn 1 draw and compared LP on deck-out
        assertFalse(rMay.firstTurnDrawEnabled);
        assertTrue(rMay.deckOutCompareLp);
        assertEquals(1, rMay.requiredTributes(5));
        assertEquals(2, rMay.requiredTributes(8));

        OcgRuleSet rDec = TimelineEngine.getActiveRuleSet(catalog, "1999-12-31");
        assertEquals("ocg_expert_1999", rDec.id);
        assertFalse(rDec.firstTurnDrawEnabled);
    }

    @Test
    public void quickDuelSimulatorSupports1999RulesAndExodiaCheck() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-08-26";
        profile.tokens = 5000;
        PackOpeningEngine packEngine = new PackOpeningEngine(new Random(7L));
        packEngine.openPacks(profile, catalog.findPack("ocg_starter_box"), 1);
        DeckValidator.autoBuildLegalPlayerDeck(profile, catalog);

        AiOpponent opp = AiDeckGenerator.getDemoOpponents().get(4);
        AiDeckGenerator.GeneratedAiDeck aiDeck =
                AiDeckGenerator.generateDeck(catalog, opp, profile.currentTimelineDate, new Random(7L));
        QuickDuelSimulator.QuickDuelReport report =
                QuickDuelSimulator.simulate(
                        catalog, profile.activeDeckMain, aiDeck.main, opp,
                        profile.currentTimelineDate, new Random(99L));

        assertEquals("ocg_expert_1999", report.appliedRule.id);
        assertFalse(report.logs.isEmpty());
        assertTrue(report.turns >= 1);
        assertNotNull(catalog.findCard(EXODIA_HEAD));
    }

    @Test
    public void errataDatabaseCoversSeries1To13AndTimelineMilestones() {
        assertTrue("Full OCG errata DB should contain 100+ cards",
                catalog.getErrataRecords().size() >= 100);

        int count1999 = 0;
        for (OcgCardErrata rec : catalog.getErrataRecords()) {
            if (rec.in1999Catalog) count1999++;
        }
        assertTrue("1999 catalog should have 25+ cards with official errata records", count1999 >= 25);

        // 1999-12-20 (Sangan's first errata) must appear in timeline milestones
        List<String> milestones = TimelineEngine.getTimelineMilestones(catalog);
        assertTrue("1999-12-20 Sangan errata milestone must be included",
                milestones.contains("1999-12-20"));

        // Series 3 & Series 10 major errata cards present and resolve by date
        OcgCardErrata.Revision cedV1 = CardErrataEngine.getActiveRevision(catalog, CHAOS_EMPEROR, "2003-04-24");
        OcgCardErrata.Revision cedV2 = CardErrataEngine.getActiveRevision(catalog, CHAOS_EMPEROR, "2015-01-01");
        assertNotNull(cedV1);
        assertNotNull(cedV2);
        assertEquals(1, cedV1.versionIndex);
        assertTrue(cedV2.versionIndex >= 2);

        OcgCardErrata.Revision fwdV1 = CardErrataEngine.getActiveRevision(catalog, FIREWALL_DRAGON, "2017-04-15");
        OcgCardErrata.Revision fwdV2 = CardErrataEngine.getActiveRevision(catalog, FIREWALL_DRAGON, "2021-01-01");
        assertNotNull(fwdV1);
        assertNotNull(fwdV2);
        assertEquals(1, fwdV1.versionIndex);
        assertTrue(fwdV2.versionIndex >= 2);
    }

    @Test
    public void sanganWitchCcvTimeWizardAnd1999CardsAutoSwitchByTimeline() {
        // Sangan: 1999-11-18 (any GY) -> 1999-12-20 (field only) -> 2016-04-01 (HOPT + search lock)
        assertTrue(CardErrataEngine.hasFlag(catalog, SANGAN, "1999-11-18", "SANGAN_ANY_GY"));
        assertTrue(CardErrataEngine.hasFlag(catalog, SANGAN, "1999-12-19", "SANGAN_ANY_GY"));
        assertFalse(CardErrataEngine.hasFlag(catalog, SANGAN, "1999-12-20", "SANGAN_ANY_GY"));
        assertTrue(CardErrataEngine.hasFlag(catalog, SANGAN, "1999-12-20", "SANGAN_FIELD_ONLY"));
        assertFalse(CardErrataEngine.hasFlag(catalog, SANGAN, "1999-12-20", "HOPT"));
        assertTrue(CardErrataEngine.hasFlag(catalog, SANGAN, "2016-04-01", "HOPT"));
        assertTrue(CardErrataEngine.hasFlag(catalog, SANGAN, "2016-04-01", "SEARCH_LOCK"));

        // Witch of the Black Forest: 1999-11-18 (any GY, remains any GY through 1999-12-31!) -> 2000-04-20 -> 2017-04-01
        assertTrue(CardErrataEngine.hasFlag(catalog, WITCH, "1999-11-18", "WITCH_ANY_GY"));
        assertTrue(CardErrataEngine.hasFlag(catalog, WITCH, "1999-12-31", "WITCH_ANY_GY"));
        assertFalse(CardErrataEngine.hasFlag(catalog, WITCH, "2000-04-20", "WITCH_ANY_GY"));
        assertTrue(CardErrataEngine.hasFlag(catalog, WITCH, "2000-04-20", "WITCH_FIELD_ONLY"));
        assertTrue(CardErrataEngine.hasFlag(catalog, WITCH, "2017-04-01", "HOPT"));

        // Crush Card Virus: 1999-07-08 3-turn draw destroy -> 2015-01-01 nerfed
        assertTrue(CardErrataEngine.hasFlag(catalog, CCV, "1999-07-08", "CCV_ORIGINAL_3TURN_DRAW"));
        assertTrue(CardErrataEngine.hasFlag(catalog, CCV, "1999-12-31", "CCV_ORIGINAL_3TURN_DRAW"));
        assertFalse(CardErrataEngine.hasFlag(catalog, CCV, "2015-01-01", "CCV_ORIGINAL_3TURN_DRAW"));
        assertTrue(CardErrataEngine.hasFlag(catalog, CCV, "2015-01-01", "CCV_2015_NERFED"));

        // Time Wizard: 1999-08-26 no OPT -> 2000-09-21 OPT
        assertTrue(CardErrataEngine.hasFlag(catalog, TIME_WIZARD, "1999-08-26", "TIME_WIZARD_NO_OPT"));
        assertFalse(CardErrataEngine.hasFlag(catalog, TIME_WIZARD, "2000-09-21", "TIME_WIZARD_NO_OPT"));

        // Early 1999 text revisions: Fissure & Trap Hole change on 1999-05-05
        assertEquals(1, CardErrataEngine.getActiveRevision(catalog, FISSURE, "1999-02-04").versionIndex);
        assertEquals(2, CardErrataEngine.getActiveRevision(catalog, FISSURE, "1999-05-05").versionIndex);
        assertEquals(1, CardErrataEngine.getActiveRevision(catalog, TRAP_HOLE, "1999-02-04").versionIndex);
        assertEquals(2, CardErrataEngine.getActiveRevision(catalog, TRAP_HOLE, "1999-05-05").versionIndex);

        // Pre-errata script IDs required on 1999-12-31 include Sangan, Witch, CCV, Time Wizard
        List<Integer> preIds = CardErrataEngine.getRequiredPreErrataScriptIds(catalog, "1999-12-31");
        assertTrue(preIds.contains(SANGAN));
        assertTrue(preIds.contains(WITCH));
        assertTrue(preIds.contains(CCV));
        assertTrue(preIds.contains(TIME_WIZARD));
    }

    @Test
    public void quickDuelExecutesSangan1999PreErrataDiscardVsDec20FieldOnly() {
        // Construct a test deck rich in Graceful Charity + Sangan + low-ATK monsters
        List<Integer> comboDeck = new ArrayList<>();
        for (int i = 0; i < 15; i++) comboDeck.add(GRACEFUL_CHARITY);
        for (int i = 0; i < 15; i++) comboDeck.add(SANGAN);
        for (int i = 0; i < 10; i++) comboDeck.add(CELTIC_GUARDIAN);

        List<Integer> dummyAiDeck = new ArrayList<>();
        for (int i = 0; i < 40; i++) dummyAiDeck.add(DE_SPELL);

        AiOpponent opp = AiDeckGenerator.getDemoOpponents().get(5);

        // On 1999-12-19: Sangan v1 (any GY) MUST trigger when discarded by Graceful Charity!
        QuickDuelSimulator.QuickDuelReport beforeErrata = QuickDuelSimulator.simulate(
                catalog, comboDeck, dummyAiDeck, opp, "1999-12-19", new Random(42L));
        boolean triggeredFromHandBefore = false;
        for (String line : beforeErrata.logs) {
            if (line.contains("三眼怪") && line.contains("手卡/非场上送墓")) {
                triggeredFromHandBefore = true;
                break;
            }
        }
        assertTrue("On 1999-12-19 Sangan MUST trigger search when discarded from hand by Graceful Charity",
                triggeredFromHandBefore);

        // On 1999-12-20: Sangan v2 (field only) MUST NOT trigger when discarded from hand!
        QuickDuelSimulator.QuickDuelReport afterErrata = QuickDuelSimulator.simulate(
                catalog, comboDeck, dummyAiDeck, opp, "1999-12-20", new Random(42L));
        boolean triggeredFromHandAfter = false;
        for (String line : afterErrata.logs) {
            if (line.contains("三眼怪") && line.contains("手卡/非场上送墓")) {
                triggeredFromHandAfter = true;
                break;
            }
        }
        assertFalse("On 1999-12-20 Sangan MUST NOT trigger search when discarded from hand",
                triggeredFromHandAfter);
    }

    @Test
    public void tournamentEngineSupportsRandomBracketsAndChampionSettlement() {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-08-26";
        profile.tokens = 50000;
        PackOpeningEngine engine = new PackOpeningEngine(new Random(7L));
        engine.openPacks(profile, catalog.findPack("ocg_starter_box"), 1);
        engine.openPacks(profile, catalog.findPack("ocg_vol4"), 5);
        DeckValidator.autoBuildLegalPlayerDeck(profile, catalog);
        assertEquals(40, profile.activeDeckMain.size());

        // 1) 4-player Shop Cup: player wins Semi + Final -> Champion (+600 tokens)
        cn.garymb.ygomobile.rpg.engine.TournamentEngine.TournamentBracket b4 =
                cn.garymb.ygomobile.rpg.engine.TournamentEngine.createBracket(catalog, profile, 4, new Random(11L));
        assertEquals(4, b4.size);
        assertEquals(1, b4.rounds.size());
        assertEquals(2, b4.rounds.get(0).size());
        assertNotNull(b4.findPendingPlayerMatch());
        for (cn.garymb.ygomobile.rpg.engine.TournamentEngine.Match m : b4.rounds.get(0)) {
            assertEquals(40, m.p1.deckMain.size());
            assertEquals(40, m.p2.deckMain.size());
        }

        int tokensBefore = profile.tokens;
        cn.garymb.ygomobile.rpg.engine.TournamentEngine.recordPlayerMatchResult(
                catalog, b4, true, "半决赛获胜", null, new Random(12L));
        assertFalse(b4.finished);
        assertEquals(1, b4.currentRound);
        assertNotNull(b4.findPendingPlayerMatch());

        cn.garymb.ygomobile.rpg.engine.TournamentEngine.recordPlayerMatchResult(
                catalog, b4, true, "决赛夺冠", null, new Random(13L));
        assertTrue(b4.finished);
        assertTrue(b4.champion.isPlayer);
        assertEquals(600, b4.rewardTokens);
        int claimed = cn.garymb.ygomobile.rpg.engine.TournamentEngine.claimRewardIfFinished(profile, b4);
        assertEquals(600, claimed);
        assertEquals(tokensBefore + 600, profile.tokens);
        assertEquals(1, profile.tournamentWins);
        assertEquals(1, profile.tournamentsPlayed);
        // Idempotent reward claim
        assertEquals(0, cn.garymb.ygomobile.rpg.engine.TournamentEngine.claimRewardIfFinished(profile, b4));

        // 2) 8-player Grand Tournament: player loses round 1 -> simulateRemainingToChampion crowns an AI
        cn.garymb.ygomobile.rpg.engine.TournamentEngine.TournamentBracket b8 =
                cn.garymb.ygomobile.rpg.engine.TournamentEngine.createBracket(catalog, profile, 8, new Random(21L));
        assertEquals(8, b8.size);
        assertEquals(4, b8.rounds.get(0).size());
        cn.garymb.ygomobile.rpg.engine.TournamentEngine.recordPlayerMatchResult(
                catalog, b8, false, "首轮惜败", null, new Random(22L));
        assertTrue(b8.playerEliminated);
        assertFalse(b8.finished);
        cn.garymb.ygomobile.rpg.engine.TournamentEngine.simulateRemainingToChampion(
                catalog, b8, new Random(23L));
        assertTrue(b8.finished);
        assertNotNull(b8.champion);
        assertFalse(b8.champion.isPlayer);
        assertEquals(3, b8.rounds.size());
        assertEquals(120, b8.rewardTokens);
        assertEquals(120, cn.garymb.ygomobile.rpg.engine.TournamentEngine.claimRewardIfFinished(profile, b8));
        assertEquals(1, profile.tournamentWins);
        assertEquals(2, profile.tournamentsPlayed);
    }
}
