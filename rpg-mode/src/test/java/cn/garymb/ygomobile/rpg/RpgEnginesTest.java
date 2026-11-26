package cn.garymb.ygomobile.rpg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.engine.TournamentEngine;
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
        File canonFile = new File("src/main/assets/rpg/ocg_canon_duels.json");
        try (FileInputStream fis = new FileInputStream(assetFile);
             FileInputStream efis = new FileInputStream(errataFile);
             FileInputStream cfis = new FileInputStream(canonFile)) {
            catalog = RpgCatalogLoader.loadFromStreams(fis, efis, cfis);
        }
    }

    @Test
    public void catalogContainsFull1999OcgLineupWithAuthenticJpyPrices() {
        assertEquals("OCG", catalog.format);
        assertEquals("1999-02-04", catalog.minTimelineDate);
        assertEquals("1999-12-31", catalog.maxTimelineDate);
        assertEquals(19, catalog.packs.size());
        assertEquals(12, catalog.events.size());
        assertTrue("Full 1999 catalog should have 600+ unique OCG cards",
                catalog.getAllCardsMap().size() >= 600);

        // Authentic JPY pricing (variant Starter Box Theater removed; only retail STARTER BOX kept)
        assertEquals(150, catalog.findPack("ocg_vol1").priceTokens);
        assertEquals(100, catalog.findPack("ocg_booster1").priceTokens);
        assertEquals(3, catalog.findPack("ocg_booster1").cardsPerPack);
        assertNull(catalog.findPack("ocg_starter_box_theater"));
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

        // 1999-03-18: Vol.1 + Booster 1 + Starter Box (retail only)
        assertEquals(3, TimelineEngine.getAvailablePacks(catalog, "1999-03-18").size());

        // 1999-03-27: + Vol.2
        assertEquals(4, TimelineEngine.getAvailablePacks(catalog, "1999-03-27").size());

        // 1999-12-31: all 19 products & all 12 events unlocked
        assertEquals(19, TimelineEngine.getAvailablePacks(catalog, "1999-12-31").size());
        assertEquals(12, TimelineEngine.getAvailableEvents(catalog, "1999-12-31").size());

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
        // Copies beyond 3 must be automatically dismantled into dust.
        profile.currentTimelineDate = "1999-12-16";
        profile.tokens = 2500;
        int dustBeforeEx = profile.dust;
        PackOpeningEngine.PackOpenResult exRes = engine.openPacks(
                profile, catalog.findPack("ocg_ex_starter_box"), 1, catalog.craftingRules);
        assertTrue(exRes.success);
        assertEquals(102, exRes.pulledCards.size());
        assertEquals(2000, exRes.tokensSpent);
        assertEquals("EX Starter Box De-Spell must cap at 3 owned copies", 3, profile.getOwnedCount(DE_SPELL));
        assertTrue("Surplus copies beyond 3 must auto-dismantle into dust",
                exRes.autoDismantledCount >= 3 && exRes.autoDismantledDust > 0);
        assertEquals(dustBeforeEx + exRes.autoDismantledDust, profile.dust);
        for (Integer count : profile.collection.values()) {
            assertTrue("No card in collection may exceed 3 copies after pack purchase", count <= 3);
        }
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
        profile.dust = 2000;

        // Cannot craft Vol.2 card on 1999-02-04
        assertFalse(CraftingEngine.craft(profile, catalog, MONSTER_REBORN, 1).success);

        // Can craft Vol.1 Dark Hole (SR = 240 dust)
        assertTrue(CraftingEngine.craft(profile, catalog, DARK_HOLE, 2).success);
        assertEquals(2, profile.getOwnedCount(DARK_HOLE));
        assertEquals(1520, profile.dust);

        // Cannot craft beyond 3 owned copies
        assertTrue(CraftingEngine.craft(profile, catalog, DARK_HOLE, 1).success);
        assertEquals(3, profile.getOwnedCount(DARK_HOLE));
        assertFalse("Crafting beyond 3 copies must be blocked",
                CraftingEngine.craft(profile, catalog, DARK_HOLE, 1).success);

        profile.activeDeckMain.add(DARK_HOLE);
        profile.activeDeckMain.add(DARK_HOLE);
        assertTrue(CraftingEngine.dismantle(profile, catalog, DARK_HOLE, 2).success);
        assertEquals(1, profile.getOwnedCount(DARK_HOLE));
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
        assertEquals(13, opps.size());
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

        // Verify automatic deck switching along timeline for the SAME opponent (Kaiba, Exodia Master & Mill Morphing)
        AiOpponent kaiba = null;
        AiOpponent exodia = null;
        AiOpponent millMorphing = null;
        for (AiOpponent o : opps) {
            if ("ai_starter_kaiba".equals(o.id)) kaiba = o;
            if ("ai_exodia_master".equals(o.id)) exodia = o;
            if ("ai_mill_morphing".equals(o.id)) millMorphing = o;
        }
        assertNotNull(kaiba);
        assertNotNull(exodia);
        assertNotNull(millMorphing);

        // Kaiba auto-switches from No-Tribute Blue-Eyes (03-18) -> Expert Transition (05-05) -> High ATK (08-26) -> EX (12-16)
        assertEquals("1999-03-18", kaiba.getActiveDeckProfile("1999-03-18").effectiveDate);
        assertEquals("1999-05-05", kaiba.getActiveDeckProfile("1999-05-05").effectiveDate);
        assertEquals("1999-08-26", kaiba.getActiveDeckProfile("1999-08-26").effectiveDate);
        assertEquals("1999-12-16", kaiba.getActiveDeckProfile("1999-12-31").effectiveDate);

        // Exodia Master auto-switches from PP1 Hybrid (08-26) -> Vol.6 Pre-Errata Sangan/Witch (11-18) -> EX Last Will Loop (12-16)
        assertEquals("1999-08-26", exodia.getActiveDeckProfile("1999-09-23").effectiveDate);
        assertEquals("1999-11-18", exodia.getActiveDeckProfile("1999-11-18").effectiveDate);
        assertEquals("1999-12-16", exodia.getActiveDeckProfile("1999-12-19").effectiveDate);

        // Mill Morphing auto-switches from Booster 5 Deck Destruction (10-17) -> EX Anti-Exodia Morphing Jar (12-16)
        assertEquals("1999-10-17", millMorphing.getActiveDeckProfile("1999-11-01").effectiveDate);
        assertEquals("1999-12-16", millMorphing.getActiveDeckProfile("1999-12-31").effectiveDate);
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
        assertEquals("ocg_expert_1999", TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate).id);
        assertEquals(40, aiDeck.main.size());
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
    public void tournamentEngineSupportsRandomBracketsAndChampionSettlement() {
        TournamentEngine.setAutoDuelExecutorForTest(
                (cat, nameA, mainA, extraA, styleA, nameB, mainB, extraB, styleB, rule, date, aFirst, rec, rng) -> {
                    assertNotNull(styleA);
                    assertNotNull(styleB);
                    List<cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.ReplayFrame> frames = new ArrayList<>();
                    frames.add(new cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.ReplayFrame(
                            0, 1, "开局", nameA, 8000, 8000, 5, 5, 35, 35, null, null, null));
                    frames.add(new cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.ReplayFrame(
                            1, 5, "决胜", nameA, 5500, 0, 3, 2, 30, 30, null, null, null));
                    return new cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.DuelResult(
                            true, false, 5500, 0, 5, rule, java.util.Collections.singletonList("真引擎测试对局"),
                            nameA, nameB, styleA, styleB, frames);
                });
        try {
            RpgProfile profile = new RpgProfile();
            profile.currentTimelineDate = "1999-08-26";
            profile.tokens = 50000;
            PackOpeningEngine engine = new PackOpeningEngine(new Random(7L));
            engine.openPacks(profile, catalog.findPack("ocg_starter_box"), 1);
            engine.openPacks(profile, catalog.findPack("ocg_vol4"), 5);
            DeckValidator.autoBuildLegalPlayerDeck(profile, catalog);
            assertEquals(40, profile.activeDeckMain.size());

            // 1) 4-player Shop Cup: player wins Semi + Final -> Champion (+600 tokens)
            TournamentEngine.TournamentBracket b4 =
                    TournamentEngine.createBracket(catalog, profile, 4, new Random(11L));
            assertEquals(4, b4.size);
            assertEquals(1, b4.rounds.size());
            assertEquals(2, b4.rounds.get(0).size());
            assertNotNull(b4.findPendingPlayerMatch());
            for (TournamentEngine.Match m : b4.rounds.get(0)) {
                assertEquals(40, m.p1.deckMain.size());
                assertEquals(40, m.p2.deckMain.size());
                assertNotNull(m.p1.fillStyle);
                assertNotNull(m.p2.fillStyle);
            }

            int tokensBefore = profile.tokens;
            TournamentEngine.recordPlayerMatchResult(
                    catalog, b4, true, "半决赛获胜", null, new Random(12L));
            assertFalse(b4.finished);
            assertEquals(1, b4.currentRound);
            assertNotNull(b4.findPendingPlayerMatch());

            TournamentEngine.recordPlayerMatchResult(
                    catalog, b4, true, "决赛夺冠", null, new Random(13L));
            assertTrue(b4.finished);
            assertTrue(b4.champion.isPlayer);
            assertEquals(600, b4.rewardTokens);
            int claimed = TournamentEngine.claimRewardIfFinished(profile, b4);
            assertEquals(600, claimed);
            assertEquals(tokensBefore + 600, profile.tokens);
            assertEquals(1, profile.tournamentWins);
            assertEquals(1, profile.tournamentsPlayed);
            assertEquals(0, TournamentEngine.claimRewardIfFinished(profile, b4));

            // 2) 8-player Grand Tournament: player loses round 1 -> simulateRemainingToChampion crowns an AI
            TournamentEngine.TournamentBracket b8 =
                    TournamentEngine.createBracket(catalog, profile, 8, new Random(21L));
            assertEquals(8, b8.size);
            assertEquals(4, b8.rounds.get(0).size());
            TournamentEngine.recordPlayerMatchResult(
                    catalog, b8, false, "首轮惜败", null, new Random(22L));
            assertTrue(b8.playerEliminated);
            assertFalse(b8.finished);
            TournamentEngine.simulateRemainingToChampion(
                    catalog, b8, new Random(23L));
            assertTrue(b8.finished);
            assertNotNull(b8.champion);
            assertFalse(b8.champion.isPlayer);
            assertEquals(3, b8.rounds.size());
            assertEquals(120, b8.rewardTokens);
            assertEquals(120, TournamentEngine.claimRewardIfFinished(profile, b8));
            assertEquals(1, profile.tournamentWins);
            assertEquals(2, profile.tournamentsPlayed);

            for (TournamentEngine.Match m : b8.rounds.get(0)) {
                if (!m.involvesPlayer()) {
                    assertNotNull("AI match should have replayReport", m.replayReport);
                    assertTrue("AI match replayReport should have >= 2 frames", m.replayReport.replayFrames.size() >= 2);
                    assertEquals(8000, m.replayReport.replayFrames.get(0).sideALp);
                    assertEquals(8000, m.replayReport.replayFrames.get(0).sideBLp);
                }
            }
        } finally {
            TournamentEngine.setAutoDuelExecutorForTest(null);
        }
    }

    @Test
    public void recentTwentyMatchWinRateTracksRollingWindowPerDateAndOpponent() throws Exception {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-02-04";
        List<AiOpponent> opponents = AiDeckGenerator.getDemoOpponents();
        AiOpponent opp0 = opponents.get(0);
        AiOpponent opp1 = opponents.get(1);

        // Initially 0/20
        RpgProfile.RecentMatchStats empty = profile.getRecentStats("1999-02-04", opp0.id);
        assertEquals(0, empty.total);
        assertEquals(0.0, empty.winRatePercent, 0.001);

        // Settle 10 losses followed by 20 wins on 1999-02-04 vs opp0 -> oldest 10 losses must roll out
        for (int i = 0; i < 10; i++) {
            DuelRewardEngine.settle(profile, opp0, false, false);
        }
        for (int i = 0; i < 20; i++) {
            DuelRewardEngine.settle(profile, opp0, true, false);
        }
        RpgProfile.RecentMatchStats allWins = profile.getRecentStats("1999-02-04", opp0.id);
        assertEquals(20, allWins.total);
        assertEquals(20, allWins.wins);
        assertEquals(0, allWins.losses);
        assertEquals(0, allWins.draws);
        assertEquals(100.0, allWins.winRatePercent, 0.001);

        // Add 1 loss and 1 draw -> rolling window keeps last 20 (18W, 1L, 1D => 90.0%)
        DuelRewardEngine.settle(profile, opp0, false, false);
        DuelRewardEngine.settle(profile, opp0, false, true);
        RpgProfile.RecentMatchStats mixed = profile.getRecentStats("1999-02-04", opp0.id);
        assertEquals(20, mixed.total);
        assertEquals(18, mixed.wins);
        assertEquals(1, mixed.losses);
        assertEquals(1, mixed.draws);
        assertEquals(90.0, mixed.winRatePercent, 0.001);

        // Strict isolation across timeline dates and opponents
        assertEquals(0, profile.getRecentStats("1999-03-27", opp0.id).total);
        assertEquals(0, profile.getRecentStats("1999-02-04", opp1.id).total);

        // Record separate match at a later timeline date
        profile.currentTimelineDate = "1999-05-25";
        DuelRewardEngine.settle(profile, opp0, true, false);
        DuelRewardEngine.settle(profile, opp0, false, false);
        RpgProfile.RecentMatchStats mayStats = profile.getRecentStats("1999-05-25", opp0.id);
        assertEquals(2, mayStats.total);
        assertEquals(1, mayStats.wins);
        assertEquals(1, mayStats.losses);
        assertEquals(50.0, mayStats.winRatePercent, 0.001);
        // Earlier date stats remain untouched
        assertEquals(20, profile.getRecentStats("1999-02-04", opp0.id).total);

        // Verify RpgRepository save/load round-trip preserves recentMatchHistory
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("rpg_recent_winrate_test").toFile();
        try {
            cn.garymb.ygomobile.rpg.data.RpgRepository repo = new cn.garymb.ygomobile.rpg.data.RpgRepository(tempDir);
            repo.saveProfile(profile);
            RpgProfile reloaded = repo.loadProfile();
            RpgProfile.RecentMatchStats febReloaded = reloaded.getRecentStats("1999-02-04", opp0.id);
            assertEquals(20, febReloaded.total);
            assertEquals(18, febReloaded.wins);
            assertEquals(1, febReloaded.losses);
            assertEquals(1, febReloaded.draws);
            assertEquals(90.0, febReloaded.winRatePercent, 0.001);
            assertEquals(50.0, reloaded.getRecentStats("1999-05-25", opp0.id).winRatePercent, 0.001);
        } finally {
            for (java.io.File f : tempDir.listFiles() != null ? tempDir.listFiles() : new java.io.File[0]) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void multiDeckSaveLoadOverwriteDeleteAndPersistence() throws Exception {
        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-03-27";
        profile.activeDeckMain = new ArrayList<>(java.util.Arrays.asList(89631139, 46986414, 53129443));
        profile.activeDeckExtra = new ArrayList<>(java.util.Arrays.asList(23995346));

        // 1. Save deck A
        RpgProfile.SavedDeck deckA = profile.saveCurrentDeck("青眼强攻");
        assertNotNull(deckA);
        assertEquals("青眼强攻", deckA.name);
        assertEquals(3, deckA.main.size());
        assertEquals(1, deckA.extra.size());
        assertEquals("1999-03-27", deckA.savedAtDate);
        assertEquals(deckA.id, profile.activeDeckId);
        assertEquals(1, profile.savedDecks.size());

        // 2. Edit active deck -> must NOT mutate saved deck A (defensive copy)
        profile.activeDeckMain.clear();
        profile.activeDeckMain.add(33396948);
        profile.activeDeckExtra.clear();
        assertEquals(3, profile.findSavedDeck(deckA.id).main.size());

        // 3. Save deck B under a different name
        profile.currentTimelineDate = "1999-05-25";
        RpgProfile.SavedDeck deckB = profile.saveCurrentDeck("封印大法师");
        assertEquals(2, profile.savedDecks.size());
        assertEquals(deckB.id, profile.activeDeckId);
        assertEquals(1, deckB.main.size());
        assertEquals(0, deckB.extra.size());

        // 4. Load deck A -> restores A's cards into active editor
        assertTrue(profile.loadSavedDeck(deckA.id));
        assertEquals(deckA.id, profile.activeDeckId);
        assertEquals(3, profile.activeDeckMain.size());
        assertEquals(1, profile.activeDeckExtra.size());
        assertEquals(Integer.valueOf(89631139), profile.activeDeckMain.get(0));

        // 5. Overwrite deck B with current active deck (or save with same name)
        profile.activeDeckMain.add(55144522);
        assertTrue(profile.overwriteSavedDeck(deckB.id));
        assertEquals(4, profile.findSavedDeck(deckB.id).main.size());
        assertEquals(deckB.id, profile.activeDeckId);

        // Saving with an existing name updates in-place instead of creating a duplicate
        RpgProfile.SavedDeck updatedA = profile.saveCurrentDeck("  青眼强攻  ");
        assertEquals(deckA.id, updatedA.id);
        assertEquals(2, profile.savedDecks.size());
        assertEquals(4, updatedA.main.size());

        // 6. Verify RpgRepository persistence round-trip
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("rpg_saved_decks_test").toFile();
        try {
            cn.garymb.ygomobile.rpg.data.RpgRepository repo = new cn.garymb.ygomobile.rpg.data.RpgRepository(tempDir);
            repo.saveProfile(profile);
            RpgProfile reloaded = repo.loadProfile();
            assertEquals(2, reloaded.savedDecks.size());
            assertEquals(deckA.id, reloaded.activeDeckId);
            assertEquals("青眼强攻", reloaded.findSavedDeck(deckA.id).name);
            assertEquals(4, reloaded.findSavedDeck(deckA.id).main.size());
            assertEquals("封印大法师", reloaded.findSavedDeck(deckB.id).name);

            // 7. Delete deck A -> removes slot & clears activeDeckId, keeps editing buffer intact
            assertTrue(reloaded.deleteSavedDeck(deckA.id));
            assertEquals(1, reloaded.savedDecks.size());
            assertNull(reloaded.activeDeckId);
            assertNull(reloaded.findSavedDeck(deckA.id));
            assertEquals(4, reloaded.activeDeckMain.size());
            assertFalse(reloaded.deleteSavedDeck("non_existent_id"));
        } finally {
            for (java.io.File f : tempDir.listFiles() != null ? tempDir.listFiles() : new java.io.File[0]) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void timelineDigestTracksAllHistoricalChanges() {
        List<AiOpponent> opps = AiDeckGenerator.getDemoOpponents();

        // 1999-02-04: Vol.1, official rule, initial AI opponents
        TimelineEngine.TimelineDigest d1 = TimelineEngine.buildDigestAtDate(catalog, opps, "1999-02-04");
        assertEquals(1, d1.newPacks.size());
        assertEquals("ocg_vol1", d1.newPacks.get(0).id);
        assertEquals(1, d1.ruleChanges.size());
        assertFalse(d1.aiChanges.isEmpty());

        // 1999-08-26: Tokyo Dome PP1 + Booster 4 + promo events + 1999 first banlist + AI evolutions
        TimelineEngine.TimelineDigest dDome = TimelineEngine.buildDigestAtDate(catalog, opps, "1999-08-26");
        assertTrue(dDome.newPacks.size() >= 2);
        assertFalse(dDome.newEvents.isEmpty());
        assertFalse(dDome.banlistChanges.isEmpty());
        assertFalse(dDome.aiChanges.isEmpty());

        // 1999-12-20: Sangan official errata revision
        TimelineEngine.TimelineDigest dErrata = TimelineEngine.buildDigestAtDate(catalog, opps, "1999-12-20");
        assertFalse("1999-12-20 must surface Sangan errata", dErrata.errataChanges.isEmpty());
        assertEquals(26202165, dErrata.errataChanges.get(0).errata.cardId);

        // Forward multi-month transition 1999-03-27 -> 1999-05-27 aggregates Expert Rule + Booster 2 + Vol.3 + AI changes
        TimelineEngine.TimelineDigest range = TimelineEngine.buildDigestForTransition(
                catalog, opps, "1999-03-27", "1999-05-27");
        assertTrue(range.isRange);
        assertEquals(1, range.ruleChanges.size());
        assertEquals("ocg_expert_1999", range.ruleChanges.get(0).id);
        assertTrue(range.newPacks.size() >= 2);
        assertFalse(range.aiChanges.isEmpty());
        assertTrue(range.buildShortSummary().contains("规则变更"));

        // Backward transition 1999-08-26 -> 1999-05-05 shows exact events at 1999-05-05
        TimelineEngine.TimelineDigest back = TimelineEngine.buildDigestForTransition(
                catalog, opps, "1999-08-26", "1999-05-05");
        assertFalse(back.isRange);
        assertEquals(1, back.ruleChanges.size());
    }

    @Test
    public void timelineEventDatesStepperAndYearMonthHelpersWork() {
        List<AiOpponent> opps = AiDeckGenerator.getDemoOpponents();
        List<String> eventDates = TimelineEngine.getTimelineEventDates(catalog, opps);
        assertFalse(eventDates.isEmpty());
        assertEquals("1999-02-04", eventDates.get(0));
        // Empty filler date 1999-12-31 has no OCG events and must NOT appear in event-only picker
        assertFalse("1999-12-31 has no events and should not be selectable", eventDates.contains("1999-12-31"));
        assertTrue("1999-12-20 Sangan errata must be selectable", eventDates.contains("1999-12-20"));

        // Every returned event date must actually have >= 1 real historical change
        for (String d : eventDates) {
            TimelineEngine.TimelineDigest digest = TimelineEngine.buildDigestAtDate(catalog, opps, d);
            assertTrue("Event date " + d + " must have at least 1 change", digest.getTotalChangeCount() > 0);
        }

        // Prev / Next stepper navigation
        assertNull(TimelineEngine.getPreviousEventDate(eventDates, eventDates.get(0)));
        assertEquals(eventDates.get(1), TimelineEngine.getNextEventDate(eventDates, eventDates.get(0)));
        assertEquals(eventDates.get(0), TimelineEngine.getPreviousEventDate(eventDates, eventDates.get(1)));
        assertNull(TimelineEngine.getNextEventDate(eventDates, eventDates.get(eventDates.size() - 1)));
        // Even if an old save sits on a non-event date (e.g., 1999-12-31), prev cleanly lands on the latest event
        assertEquals(eventDates.get(eventDates.size() - 1),
                TimelineEngine.getPreviousEventDate(eventDates, "1999-12-31"));

        // Year & Month grouping helpers
        List<String> years = TimelineEngine.getEventYears(eventDates);
        assertEquals(1, years.size());
        assertEquals("1999", years.get(0));
        List<String> months = TimelineEngine.getEventMonthsForYear(eventDates, "1999");
        assertTrue(months.contains("02"));
        assertTrue(months.contains("03"));
        assertTrue(months.contains("08"));
        assertTrue(months.contains("12"));
        assertFalse("January 1999 had no OCG events", months.contains("01"));
        assertFalse("April 1999 had no OCG events", months.contains("04"));
    }

    @Test
    public void canonDuels1999LoadReconstruct40CardsHaveAvatarsAndRegisterTimelineDates() {
        List<cn.garymb.ygomobile.rpg.model.OcgCanonDuel> duels = catalog.getCanonDuels();
        assertEquals("Should contain all 21 curated 1996-1999 canon duels", 21, duels.size());

        File avatarsDir = new File("src/main/assets/rpg/avatars");
        List<String> eventDates = TimelineEngine.getTimelineEventDates(catalog, AiDeckGenerator.getDemoOpponents());

        // Exact 1999 canon dates must appear as selectable calendar event dates
        String[] expected1999Dates = {"1999-03-02", "1999-03-06", "1999-03-30", "1999-06-08", "1999-11-09"};
        for (String d : expected1999Dates) {
            assertTrue("Canon duel date " + d + " must be registered as a timeline event date",
                    eventDates.contains(d));
            TimelineEngine.TimelineDigest digest = TimelineEngine.buildDigestAtDate(
                    catalog, AiDeckGenerator.getDemoOpponents(), d);
            assertFalse("TimelineDigest at " + d + " must include canon duel",
                    digest.newCanonDuels.isEmpty());
        }

        // Verify progressive unlocking across 1999
        int atStart = TimelineEngine.getAvailableCanonDuels(catalog, "1999-02-04").size();
        int atYearEnd = TimelineEngine.getAvailableCanonDuels(catalog, "1999-12-31").size();
        assertEquals("16 pre-OCG duels should unlock on 1999-02-04", 16, atStart);
        assertEquals("All 21 duels should unlock by 1999-12-31", 21, atYearEnd);

        // Verify every canon duel reconstructs 40-card decks retaining 100% of canon cards,
        // has bundled character avatars, valid AI fillStyle, and zero shop leakage
        for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel duel : duels) {
            assertNotNull(duel.id);
            assertTrue("Original date must be <= 1999-12-31: " + duel.id,
                    duel.originalDate.compareTo("1999-12-31") <= 0);
            assertNotNull(duel.sideA);
            assertNotNull(duel.sideB);

            cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide[] sides = {duel.sideA, duel.sideB};
            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide side : sides) {
                assertTrue("AI fillStyle must be registered in SUPPORTED_STYLES: " + side.fillStyle,
                        cn.garymb.ygomobile.rpg.ai.RpgAiStrategy.SUPPORTED_STYLES.contains(side.fillStyle));
                File avatarFile = new File(avatarsDir, side.avatarId + ".webp");
                assertTrue("Missing bundled character avatar: " + avatarFile.getPath(),
                        avatarFile.exists() && avatarFile.length() > 1000);

                cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.BuiltCanonDeck built =
                        cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.buildDeck(catalog, duel, side, duel.timelineDate);
                assertEquals("Reconstructed canon deck must be exactly 40 cards: " + duel.id + "/" + side.characterName,
                        40, built.main.size());
                // Every canon main card must be preserved
                for (Integer cid : side.canonCardIds) {
                    RpgCard meta = catalog.findCardOrCanon(cid);
                    assertNotNull("Missing card metadata for canon card " + cid, meta);
                    if (meta.isExtraDeckCard()) {
                        assertTrue("Extra deck card should reside in extra: " + cid, built.extra.contains(cid));
                    } else {
                        assertTrue("Canon card must be preserved in main deck: " + cid, built.main.contains(cid));
                    }
                }
                for (Integer eid : side.canonExtraIds) {
                    assertNotNull("Missing extra card metadata " + eid, catalog.findCardOrCanon(eid));
                    assertTrue("Canon extra card must be preserved: " + eid, built.extra.contains(eid));
                }
                // Main deck must never contain Extra Deck cards
                for (Integer mid : built.main) {
                    RpgCard mc = catalog.findCardOrCanon(mid);
                    assertNotNull("Every built main card must have metadata: " + mid, mc);
                    assertFalse("Main deck must not contain Extra Deck card: " + mid, mc.isExtraDeckCard());
                }
            }
        }

        // Verify post-1999 canon-only cards (e.g. Toon World 15259703) NEVER leak into 1999 shop/crafting pool
        assertNull("Toon World must NOT leak into 1999 OCG shop catalog", catalog.findCard(15259703));
        assertNotNull("Toon World must resolve via findCardOrCanon", catalog.findCardOrCanon(15259703));
    }
}
