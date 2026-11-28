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
import cn.garymb.ygomobile.rpg.model.Rarity;
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
    public void craftingCostUsesLowestReleasedRarityAndMatchesUi() {
        final int TRIAL_OF_NIGHTMARE = 77827521; // 1999-03-06 N, 1999-06-21 SCR
        final int CELTIC_GUARDIAN = 91152256;    // 1999-03-18 SR, 1999-12-16 N

        // On 1999-03-18, Trial of Nightmare is N (30 dust), NOT future 1999-06-21 SCR (900 dust)
        assertEquals(Rarity.N, CraftingEngine.getCraftingRarity(catalog, TRIAL_OF_NIGHTMARE, "1999-03-18"));
        assertEquals(30, CraftingEngine.getCraftCost(catalog, TRIAL_OF_NIGHTMARE, "1999-03-18"));

        RpgProfile profile = new RpgProfile();
        profile.currentTimelineDate = "1999-03-18";
        profile.dust = 265;
        profile.addCard(TRIAL_OF_NIGHTMARE, 2);
        CraftingEngine.CraftResult r = CraftingEngine.craft(profile, catalog, TRIAL_OF_NIGHTMARE, 1);
        assertTrue("Crafting Trial of Nightmare with 265 dust on 1999-03-18 must succeed: " + r.message, r.success);
        assertEquals(-30, r.dustDelta);
        assertEquals(235, profile.dust);

        // Even after 1999-06-21 SCR collector reprint, lowest released rarity remains N (30 dust)
        assertEquals(Rarity.N, CraftingEngine.getCraftingRarity(catalog, TRIAL_OF_NIGHTMARE, "1999-06-21"));
        assertEquals(30, CraftingEngine.getCraftCost(catalog, TRIAL_OF_NIGHTMARE, "1999-06-21"));

        // Celtic Guardian debuts as SR (240 dust) on 1999-03-18, then drops to N (30 dust) on EX 1999-12-16
        assertEquals(Rarity.SR, CraftingEngine.getCraftingRarity(catalog, CELTIC_GUARDIAN, "1999-03-18"));
        assertEquals(240, CraftingEngine.getCraftCost(catalog, CELTIC_GUARDIAN, "1999-03-18"));
        assertEquals(Rarity.N, CraftingEngine.getCraftingRarity(catalog, CELTIC_GUARDIAN, "1999-12-16"));
        assertEquals(30, CraftingEngine.getCraftCost(catalog, CELTIC_GUARDIAN, "1999-12-16"));

        // Dark Hole debuts as SR (240 dust) in Vol.1, then drops to N (30 dust) on EX 1999-12-16
        assertEquals(Rarity.SR, CraftingEngine.getCraftingRarity(catalog, DARK_HOLE, "1999-02-04"));
        assertEquals(240, CraftingEngine.getCraftCost(catalog, DARK_HOLE, "1999-02-04"));
        assertEquals(Rarity.N, CraftingEngine.getCraftingRarity(catalog, DARK_HOLE, "1999-12-16"));
        assertEquals(30, CraftingEngine.getCraftCost(catalog, DARK_HOLE, "1999-12-16"));
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
                // Verify any AI deck packing Crush Card Virus (57728570) has enough DARK <= 1000 ATK tributes
                if (aiDeck.main.contains(57728570)) {
                    int darkVirusFodder = 0;
                    for (Integer cid : aiDeck.main) {
                        RpgCard card = catalog.findCard(cid);
                        if (card != null && card.isMonster()
                                && "DARK".equalsIgnoreCase(card.attribute)
                                && card.atk <= 1000) {
                            darkVirusFodder++;
                        }
                    }
                    assertTrue("AI deck with Crush Card Virus (" + opp.id + " at " + date
                                    + ") must have >= 6 DARK <= 1000 ATK tributes, got " + darkVirusFodder,
                            darkVirusFodder >= 6);
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

        // Verify rewards are strictly calibrated to real empirical ocgcore Elo across ALL 17 1999 eras!
        AiOpponent virus = null;
        AiOpponent flip = null;
        AiOpponent shop = null;
        AiOpponent yugi = null;
        for (AiOpponent o : opps) {
            if ("ai_virus_control".equals(o.id)) virus = o;
            if ("ai_flip_control".equals(o.id)) flip = o;
            if ("ai_shop_regular".equals(o.id)) shop = o;
            if ("ai_vol1_yugi".equals(o.id)) yugi = o;
        }
        assertNotNull(virus);
        assertNotNull(flip);
        assertNotNull(shop);
        assertNotNull(yugi);

        // Monotonicity proof across all 17 milestone dates: higher empirical Elo MUST yield >= win reward!
        for (String milestone : cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.getCalibratedMilestoneDates()) {
            List<AiOpponent> avail = TimelineEngine.getAvailableOpponents(opps, milestone);
            for (int i = 0; i < avail.size(); i++) {
                AiOpponent a = avail.get(i);
                cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.RewardTriple ca =
                        cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve(a.id, milestone);
                assertNotNull("Missing calibrated Elo for " + a.id + " @ " + milestone, ca);
                int winA = a.getWinRewardTokens(milestone);
                assertTrue("Win bounty must stay within [120, 300] at " + milestone + " for " + a.id + " (got " + winA + ")",
                        winA >= 120 && winA <= 300);
                for (int j = 0; j < avail.size(); j++) {
                    AiOpponent b = avail.get(j);
                    cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.RewardTriple cb =
                            cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve(b.id, milestone);
                    assertNotNull(cb);
                    if (ca.benchmarkElo > cb.benchmarkElo) {
                        assertTrue("Reward must be monotonic with Elo at " + milestone + ": "
                                        + a.id + "(" + ca.benchmarkElo + "->" + winA + ") vs "
                                        + b.id + "(" + cb.benchmarkElo + "->" + b.getWinRewardTokens(milestone) + ")",
                                winA >= b.getWinRewardTokens(milestone));
                    }
                }
            }
        }
        assertTrue("Flip Control (Elo 2073) must outpay Virus Control on 1999-07-08",
                flip.getWinRewardTokens("1999-07-08") > virus.getWinRewardTokens("1999-07-08"));
        assertTrue("Virus Control reward must increase as it upgrades to Mask of Darkness & Sangan/Witch",
                virus.getWinRewardTokens("1999-11-18") > virus.getWinRewardTokens("1999-07-22")
                        && virus.getWinRewardTokens("1999-07-22") > virus.getWinRewardTokens("1999-07-08"));
        assertTrue("Exodia reward must peak in 1999-11-18 pre-errata dark age above 1999-08-26 PP1 hybrid",
                exodia.getWinRewardTokens("1999-11-18") > exodia.getWinRewardTokens("1999-08-26"));
        assertTrue("On 1999-12-16, Yugi EX (Elo 1722) must outpay Exodia (Elo 1579) and Shop Regular (Elo 1043)",
                yugi.getWinRewardTokens("1999-12-16") > exodia.getWinRewardTokens("1999-12-16")
                        && exodia.getWinRewardTokens("1999-12-16") > shop.getWinRewardTokens("1999-12-16"));
    }

    @Test
    public void duelRewardAndRepositoryRoundTripWork() throws Exception {
        File tmpDir = Files.createTempDirectory("rpg_repo_test").toFile();
        RpgRepository repo = new RpgRepository(tmpDir);
        RpgProfile profile = repo.loadProfile();
        int initialTokens = profile.tokens;
        AiOpponent opp = AiDeckGenerator.getDemoOpponents().get(0);
        int expectedWin = opp.getWinRewardTokens(profile.currentTimelineDate);

        DuelRewardEngine.RewardSettlement win = DuelRewardEngine.settle(profile, opp, true, false);
        assertEquals(expectedWin, win.tokensEarned);
        assertEquals(initialTokens + expectedWin, profile.tokens);
        assertEquals(1, profile.wins);

        profile.claimedEventIds.add("evt_1999_02_21_dm1_nationals");
        repo.saveProfile(profile);
        RpgProfile reloaded = repo.loadProfile();
        assertEquals(initialTokens + expectedWin, reloaded.tokens);
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
        assertEquals("Should contain all 14 core-card-gated 1999 canon duels", 14, duels.size());

        File avatarsDir = new File("src/main/assets/rpg/avatars");
        List<String> eventDates = TimelineEngine.getTimelineEventDates(catalog, AiDeckGenerator.getDemoOpponents());

        // Exact core-card OCG first-release unlock dates must register as timeline events
        String[] expectedUnlockDates = {
                "1999-07-22", "1999-08-26", "1999-09-23",
                "1999-11-18", "1999-12-01", "1999-12-18"
        };
        for (String d : expectedUnlockDates) {
            assertTrue("Canon duel unlock date " + d + " must be registered as a timeline event date",
                    eventDates.contains(d));
            TimelineEngine.TimelineDigest digest = TimelineEngine.buildDigestAtDate(
                    catalog, AiDeckGenerator.getDemoOpponents(), d);
            assertFalse("TimelineDigest at " + d + " must include canon duel(s)",
                    digest.newCanonDuels.isEmpty());
        }

        // Verify progressive unlocking across 1999 (0 on Vol.1 launch day, all 14 by year-end)
        assertEquals("No canon duel should unlock prematurely on 1999-02-04",
                0, TimelineEngine.getAvailableCanonDuels(catalog, "1999-02-04").size());
        assertEquals("2 duels unlock on 1999-07-22 (Vol.4 Summoned Skull)",
                2, TimelineEngine.getAvailableCanonDuels(catalog, "1999-07-22").size());
        assertEquals("All 14 core-card-gated duels should unlock by 1999-12-31",
                14, TimelineEngine.getAvailableCanonDuels(catalog, "1999-12-31").size());

        // Verify exact-date-only query (used by Canon Duels tab so players only battle in that era's card pool)
        assertEquals(0, TimelineEngine.getCanonDuelsOnDate(catalog, "1999-02-04").size());
        assertEquals(2, TimelineEngine.getCanonDuelsOnDate(catalog, "1999-07-22").size());
        assertEquals("Later date without its own canon duel must NOT carry over earlier duels",
                0, TimelineEngine.getCanonDuelsOnDate(catalog, "1999-07-23").size());
        assertEquals(4, TimelineEngine.getCanonDuelsOnDate(catalog, "1999-11-18").size());
        assertEquals(2, TimelineEngine.getCanonDuelsOnDate(catalog, "1999-12-18").size());
        assertEquals("Year-end non-canon date must show 0 exact-day duels",
                0, TimelineEngine.getCanonDuelsOnDate(catalog, "1999-12-31").size());
        java.util.Set<String> canonDates = TimelineEngine.getCanonDuelDates(catalog);
        assertEquals(6, canonDates.size());
        for (String d : expectedUnlockDates) {
            assertTrue("Calendar canon date set must include " + d, canonDates.contains(d));
        }

        // Verify ascending order by originalDate in JSON, strict core-card OCG date matching,
        // zero unreleased/future card leakage, 40-card deck reconstruction, and bundled avatars
        String prevOrigDate = "";
        for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel duel : duels) {
            assertNotNull(duel.id);
            assertTrue("Original date must be <= 1999-12-31: " + duel.id,
                    duel.originalDate.compareTo("1999-12-31") <= 0);
            assertTrue("Canon duels must be sorted ascending by originalDate: " + duel.id,
                    duel.originalDate.compareTo(prevOrigDate) >= 0);
            prevOrigDate = duel.originalDate;

            assertNotNull("coreCardIds must be defined for " + duel.id, duel.coreCardIds);
            assertFalse("coreCardIds must be non-empty for " + duel.id, duel.coreCardIds.isEmpty());
            String maxCoreRelease = "";
            for (Integer coreId : duel.coreCardIds) {
                String relDate = catalog.getEarliestReleaseDate(coreId);
                assertNotNull("Core card " + coreId + " in " + duel.id + " must exist in 1999 OCG catalog", relDate);
                if (relDate.compareTo(maxCoreRelease) > 0) {
                    maxCoreRelease = relDate;
                }
            }
            assertEquals("duel.timelineDate must equal max first OCG release date of coreCardIds for " + duel.id,
                    maxCoreRelease, duel.timelineDate);

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
                // 100% of canon cards must be preserved (including non-core future cards via findCardOrCanon)
                for (Integer cid : side.canonCardIds) {
                    RpgCard meta = catalog.findCardOrCanon(cid);
                    assertNotNull("Missing card metadata for canon card " + cid, meta);
                    assertFalse("Main canon card must not be Extra Deck: " + cid, meta.isExtraDeckCard());
                    assertTrue("Canon card must be preserved in main deck: " + cid, built.main.contains(cid));
                }
                for (Integer eid : side.canonExtraIds) {
                    assertNotNull("Missing extra card metadata " + eid, catalog.findCardOrCanon(eid));
                    assertTrue("Canon extra card must be preserved: " + eid, built.extra.contains(eid));
                }
                assertFalse("canonCardIds should be populated for visual deck tagging", built.canonCardIds.isEmpty());
                for (Integer cid : side.canonCardIds) {
                    assertTrue("canonCardIds must include canon main card " + cid, built.canonCardIds.contains(cid));
                }
                for (Integer eid : side.canonExtraIds) {
                    assertTrue("canonCardIds must include canon extra card " + eid, built.canonCardIds.contains(eid));
                }
                // Main deck must never contain Extra Deck cards, and every padded filler card must be released <= duel.timelineDate
                for (Integer mid : built.main) {
                    RpgCard mc = catalog.findCardOrCanon(mid);
                    assertNotNull("Every built main card must have metadata: " + mid, mc);
                    assertFalse("Main deck must not contain Extra Deck card: " + mid, mc.isExtraDeckCard());
                    if (!built.canonCardIds.contains(mid)) {
                        assertTrue("Padded filler card " + mid + " must be released <= " + duel.timelineDate,
                                TimelineEngine.isCardReleased(catalog, mid, duel.timelineDate));
                    }
                }
            }
        }

        // Verify post-1999 non-core canon cards (e.g. Kuriboh 40640057, Mirror Force 44095762)
        // resolve via findCardOrCanon for Canon Duels, yet NEVER leak into 1999 shop/crafting pool
        assertNull("Kuriboh must NOT leak into 1999 OCG shop catalog", catalog.findCard(40640057));
        assertNotNull("Kuriboh must resolve via findCardOrCanon", catalog.findCardOrCanon(40640057));
        assertNull("Mirror Force must NOT leak into 1999 OCG shop catalog", catalog.findCard(44095762));
        assertNotNull("Mirror Force must resolve via findCardOrCanon", catalog.findCardOrCanon(44095762));
    }

    @Test
    public void profileCanonDuelClearTrackingWorks() {
        RpgProfile p = new RpgProfile();
        String duelId = "canon_1996_11_19_manga_yugi_kaiba_school";
        assertEquals(0, p.getCanonDuelClearedCount(duelId));
        assertFalse(p.isCanonDuelModeCleared(duelId, RpgProfile.CANON_MODE_ROLE_A));

        p.recordCanonDuelClear(duelId, RpgProfile.CANON_MODE_ROLE_A);
        p.recordCanonDuelClear(duelId, RpgProfile.CANON_MODE_ROLE_A); // duplicate idempotent
        assertTrue(p.isCanonDuelModeCleared(duelId, RpgProfile.CANON_MODE_ROLE_A));
        assertFalse(p.isCanonDuelModeCleared(duelId, RpgProfile.CANON_MODE_ROLE_B));
        assertEquals(1, p.getCanonDuelClearedCount(duelId));

        p.recordCanonDuelClear(duelId, RpgProfile.CANON_MODE_ROLE_B);
        p.recordCanonDuelClear(duelId, RpgProfile.CANON_MODE_OWN_VS_A);
        p.recordCanonDuelClear(duelId, RpgProfile.CANON_MODE_OWN_VS_B);
        assertEquals(4, p.getCanonDuelClearedCount(duelId));
    }

    @Test
    public void profileTabOrderNormalizationAndPersistenceWorks() {
        RpgProfile p = new RpgProfile();
        // Empty saved tabOrder defaults to [0, 1, ..., 8]
        List<Integer> def = p.getNormalizedTabOrder(9);
        assertEquals(9, def.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(Integer.valueOf(i), def.get(i));
        }

        // Reorder tabs (e.g., move Canon Duels 8 and AI Duel 4 to the front)
        p.setTabOrder(java.util.Arrays.asList(8, 4, 0, 1, 2, 3, 5, 6, 7), 9);
        List<Integer> custom = p.getNormalizedTabOrder(9);
        assertEquals(Integer.valueOf(8), custom.get(0));
        assertEquals(Integer.valueOf(4), custom.get(1));
        assertEquals(Integer.valueOf(0), custom.get(2));
        assertEquals(9, custom.size());

        // Sanitize duplicates, nulls, out-of-range IDs, and append any newly added tabs
        p.tabOrder = new java.util.ArrayList<>(java.util.Arrays.asList(8, 8, -1, 99, null, 3));
        List<Integer> sanitized = p.getNormalizedTabOrder(9);
        assertEquals(9, sanitized.size());
        assertEquals(Integer.valueOf(8), sanitized.get(0));
        assertEquals(Integer.valueOf(3), sanitized.get(1));
        assertEquals(Integer.valueOf(0), sanitized.get(2));
        assertEquals(Integer.valueOf(7), sanitized.get(8));
    }

    @Test
    public void roundRobinLeaguePairsAllUnlockedAisAndPlayerDeckAndSortsByWins() {
        String date = "1999-07-08";
        OcgRuleSet rule = TimelineEngine.getActiveRuleSet(catalog, date);
        AiOpponent sampleOpp = AiDeckGenerator.getDemoOpponents().get(0);
        AiDeckGenerator.GeneratedAiDeck playerDeck =
                AiDeckGenerator.generateDeck(catalog, sampleOpp, date, new Random(7L));

        List<cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant> parts =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.buildParticipants(
                        catalog, date, rule, true, "测试自组",
                        playerDeck.main, playerDeck.extra, new Random(42L));

        // 1999-07-08 unlocks 9 AI opponents + 1 player deck = 10 participants
        assertEquals(10, parts.size());
        assertTrue(parts.get(0).isPlayer);
        for (cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant p : parts) {
            assertEquals("Each contestant must have 40-card main deck", 40, p.main.size());
        }

        // Deterministic executor: higher id rank wins so standings order is predictable
        final java.util.Map<String, Integer> power = new java.util.HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            power.put(parts.get(i).id, i * 10);
        }
        power.put("player_deck", 999); // Player deck strongest -> must finish #1 with 100% win rate!

        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.LeagueResult res =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.runRoundRobin(
                        parts, rule, date, 5,
                        (a, b, r, d, aFirst, rng) -> {
                            boolean aWon = power.get(a.id) > power.get(b.id);
                            return new cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.DuelResult(
                                    aWon, false, aWon ? 8000 : 0, aWon ? 0 : 8000, 6, r,
                                    java.util.Collections.emptyList(), a.name, b.name, a.style, b.style, null);
                        },
                        null, null, new Random(99L));

        assertFalse(res.cancelled);
        assertEquals(45, res.totalPairs);
        assertEquals(45, res.completedPairs);
        assertEquals(225, res.totalGames);
        assertEquals(225, res.completedGames);
        assertEquals("player_deck", res.standings.get(0).id);
        assertEquals(45, res.standings.get(0).wins);
        assertEquals(100.0, res.standings.get(0).getWinRatePercent(), 0.01);

        // Standings must be monotonically non-increasing by wins
        for (int i = 1; i < res.standings.size(); i++) {
            assertTrue("Standings must be sorted by wins desc",
                    res.standings.get(i - 1).wins >= res.standings.get(i).wins);
            assertEquals("Every contestant plays (N-1)*5 = 45 games", 45, res.standings.get(i).gamesPlayed);
        }
        assertTrue(res.formatPlainTextReport().contains("全环境 AI 循环赛排行榜"));

        // Verify mid-run cancellation stops immediately
        List<cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant> cancelParts =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.buildParticipants(
                        catalog, date, rule, false, null, null, null, new Random(1L));
        java.util.concurrent.atomic.AtomicBoolean cancelFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.LeagueResult cancelledRes =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.runRoundRobin(
                        cancelParts, rule, date, 5,
                        (a, b, r, d, aFirst, rng) -> new cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.DuelResult(
                                true, false, 8000, 0, 5, r,
                                java.util.Collections.emptyList(), a.name, b.name, a.style, b.style, null),
                        (done, tot, doneP, totP, lbl) -> {
                            if (done >= 7) cancelFlag.set(true);
                        },
                        cancelFlag, new Random(2L));
        assertTrue(cancelledRes.cancelled);
        assertEquals(7, cancelledRes.completedGames);
    }

    @Test
    public void swissLadderAndBradleyTerryEloScaleAndRespectOpponentStrength() {
        // 1) Scaling verification: O(N log N) Swiss rounds at 2 games/pair (1 first + 1 second)
        assertEquals(2, cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.DEFAULT_SWISS_GAMES_PER_PAIR);
        assertEquals(4, cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.DEFAULT_SWISS_APPEND_ROUNDS);
        assertEquals(18, cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.recommendedSwissRounds(10));
        assertEquals(18, cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.recommendedSwissRounds(14));
        assertEquals(21, cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.recommendedSwissRounds(30));

        // 1b) Global Minimum-Weight Matching beats top-down greedy (never strands tail pairs in forced rematches)
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant pA =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("pA", "A", "", false, null, null, "GOOD_STUFF");
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant pB =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("pB", "B", "", false, null, null, "GOOD_STUFF");
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant pC =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("pC", "C", "", false, null, null, "GOOD_STUFF");
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant pD =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("pD", "D", "", false, null, null, "GOOD_STUFF");
        pA.eloRating = 1800; pB.eloRating = 1790; pC.eloRating = 1500; pD.eloRating = 1490;
        java.util.Map<String, Integer> meetMap = new java.util.HashMap<>();
        meetMap.put("pC|pD", 1); // C & D already met -> greedy pairs (A,B) forcing C vs D rematch!
        List<cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant[]> optPairs =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.pairRoundGloballyOptimal(
                        java.util.Arrays.asList(pA, pB, pC, pD), meetMap);
        assertEquals(2, optPairs.size());
        for (cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant[] pair : optPairs) {
            boolean isCD = ("pC".equals(pair[0].id) && "pD".equals(pair[1].id))
                    || ("pD".equals(pair[0].id) && "pC".equals(pair[1].id));
            assertFalse("Global min-weight matching MUST avoid forcing C vs D rematch when zero-rematch exists", isCD);
        }

        // 2) Full 1999-12-31 field (13 AIs + player = 14 contestants):
        //    18 rounds * 7 pairs * 2 games = 252 games (same budget as old 6*6, but 100% full opponent coverage + 5 close-Elo tie-breaker rounds!)
        String date = "1999-12-31";
        OcgRuleSet rule = TimelineEngine.getActiveRuleSet(catalog, date);
        AiDeckGenerator.GeneratedAiDeck playerDeck =
                AiDeckGenerator.generateDeck(catalog, AiDeckGenerator.getDemoOpponents().get(0), date, new Random(5L));
        List<cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant> parts =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.buildParticipants(
                        catalog, date, rule, true, "测试天梯", playerDeck.main, playerDeck.extra, new Random(11L));
        assertEquals(14, parts.size());

        final java.util.Map<String, Integer> trueRank = new java.util.HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            trueRank.put(parts.get(i).id, i * 100);
        }
        trueRank.put("player_deck", 9999);

        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.DuelExecutor exec =
                (a, b, r, d, aFirst, rng) -> {
                    boolean aWon = trueRank.get(a.id) > trueRank.get(b.id);
                    return new cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.DuelResult(
                            aWon, false, aWon ? 8000 : 0, aWon ? 0 : 8000, 7, r,
                            java.util.Collections.emptyList(), a.name, b.name, a.style, b.style, null);
                };

        int recRounds = cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.recommendedSwissRounds(parts.size());
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.LeagueResult swiss =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.runSwissLadder(
                        parts, rule, date, recRounds,
                        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.DEFAULT_SWISS_GAMES_PER_PAIR,
                        0, 0L, exec, null, null, new Random(77L));

        assertTrue(swiss.isSwissMode());
        assertEquals(18, swiss.roundsPlayed);
        assertEquals(252, swiss.completedGames);
        assertEquals("player_deck", swiss.standings.get(0).id);
        assertTrue("Top undefeated contestant must have Elo >= 1620 (S tier)",
                swiss.standings.get(0).eloRating >= 1620);
        assertEquals("S", swiss.standings.get(0).getTierLabel());
        assertTrue("Standings must be sorted by Elo descending",
                swiss.standings.get(0).eloRating > swiss.standings.get(swiss.standings.size() - 1).eloRating);
        // With 18 rounds of 2 games/pair + global min-weight matching on N=14, EVERY pair (including #8 vs #9!) MUST have met!
        for (cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant p : swiss.standings) {
            assertEquals("Every contestant in 14-player 18-round Swiss MUST face all 13 distinct opponents",
                    13, p.headToHead.size());
        }
        int midSeBefore = swiss.standings.get(7).eloStdError;
        assertTrue("Mid-field Elo standard error must be reasonable (>0 and <120)",
                midSeBefore > 0 && midSeBefore < 120);

        // 3) Append 4 more Swiss rounds incrementally -> accumulates from 252 -> 308 games
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.LeagueResult extended =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.runSwissLadder(
                        new java.util.ArrayList<>(swiss.standings), rule, date,
                        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.DEFAULT_SWISS_APPEND_ROUNDS,
                        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.DEFAULT_SWISS_GAMES_PER_PAIR,
                        swiss.roundsPlayed, swiss.elapsedMs, exec, null, null, new Random(88L));
        assertEquals(22, extended.roundsPlayed);
        assertEquals(308, extended.completedGames);
        assertEquals("player_deck", extended.standings.get(0).id);
        assertTrue("Appending rounds keeps positive Elo SE",
                extended.standings.get(7).eloStdError > 0);
        assertTrue(extended.formatPlainTextReport().contains("Bradley-Terry"));
        assertTrue(extended.formatPlainTextReport().contains("±"));

        // 3b) Multiple player decks (active + saved decks) can enter the same ladder with unique IDs & styles
        AiDeckGenerator.GeneratedAiDeck savedExodia =
                AiDeckGenerator.generateDeck(catalog, AiDeckGenerator.getDemoOpponents().get(10), date, new Random(6L));
        List<cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.PlayerDeckEntry> multiEntries = java.util.Arrays.asList(
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.PlayerDeckEntry(
                        "player_deck", "我的卡组", "当前编辑", playerDeck.main, playerDeck.extra),
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.PlayerDeckEntry(
                        "player_saved_1", "自组 · 老艾收藏", "已存卡组", savedExodia.main, savedExodia.extra),
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.PlayerDeckEntry(
                        "player_saved_2", "自组 · 凡骨二号", "已存卡组", playerDeck.main, playerDeck.extra)
        );
        List<cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant> multiParts =
                cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.buildParticipants(
                        catalog, date, rule, multiEntries, new Random(19L));
        assertEquals("13 AIs + 3 player decks = 16 contestants", 16, multiParts.size());
        int playerCount = 0;
        for (cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant mp : multiParts) {
            if (mp.isPlayer) playerCount++;
        }
        assertEquals(3, playerCount);
        assertEquals("player_deck", multiParts.get(0).id);
        assertEquals("player_saved_1", multiParts.get(1).id);
        assertEquals("EXODIA_DRAW", multiParts.get(1).style);
        assertEquals("player_saved_2", multiParts.get(2).id);

        // 4) Strength-of-Schedule proof: MidStrong (50% vs Boss & Mid) MUST outrank MidWeak (50% vs Mid & Dummy)
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant boss =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("boss", "Boss", "", false, null, null, "GOOD_STUFF");
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant midStrong =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("midS", "MidStrong", "", false, null, null, "GOOD_STUFF");
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant midBridge =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("midB", "MidBridge", "", false, null, null, "GOOD_STUFF");
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant midWeak =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("midW", "MidWeak", "", false, null, null, "GOOD_STUFF");
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant dummy =
                new cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant("dummy", "Dummy", "", false, null, null, "GOOD_STUFF");

        // Boss crushes MidBridge 5-0, edges MidStrong 3-2
        boss.headToHead.put("midB", new int[]{5, 0, 0}); boss.wins += 5; boss.gamesPlayed += 5;
        midBridge.headToHead.put("boss", new int[]{0, 5, 0}); midBridge.losses += 5; midBridge.gamesPlayed += 5;
        boss.headToHead.put("midS", new int[]{3, 2, 0}); boss.wins += 3; boss.losses += 2; boss.gamesPlayed += 5;
        midStrong.headToHead.put("boss", new int[]{2, 3, 0}); midStrong.wins += 2; midStrong.losses += 3; midStrong.gamesPlayed += 5;
        // MidStrong beats MidBridge 3-2 -> MidStrong total = 5-5 (50%)
        midStrong.headToHead.put("midB", new int[]{3, 2, 0}); midStrong.wins += 3; midStrong.losses += 2; midStrong.gamesPlayed += 5;
        midBridge.headToHead.put("midS", new int[]{2, 3, 0}); midBridge.wins += 2; midBridge.losses += 3; midBridge.gamesPlayed += 5;
        // MidBridge beats MidWeak 3-2, MidWeak beats Dummy 3-2 -> MidWeak total = 5-5 (50%)
        midBridge.headToHead.put("midW", new int[]{3, 2, 0}); midBridge.wins += 3; midBridge.losses += 2; midBridge.gamesPlayed += 5;
        midWeak.headToHead.put("midB", new int[]{2, 3, 0}); midWeak.wins += 2; midWeak.losses += 3; midWeak.gamesPlayed += 5;
        midWeak.headToHead.put("dummy", new int[]{3, 2, 0}); midWeak.wins += 3; midWeak.losses += 2; midWeak.gamesPlayed += 5;
        dummy.headToHead.put("midW", new int[]{2, 3, 0}); dummy.wins += 2; dummy.losses += 3; dummy.gamesPlayed += 5;
        // MidBridge crushes Dummy 5-0 so Boss >> MidStrong > MidBridge > MidWeak > Dummy
        midBridge.headToHead.put("dummy", new int[]{5, 0, 0}); midBridge.wins += 5; midBridge.gamesPlayed += 5;
        dummy.headToHead.put("midB", new int[]{0, 5, 0}); dummy.losses += 5; dummy.gamesPlayed += 5;

        List<cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant> sosList =
                java.util.Arrays.asList(boss, midStrong, midBridge, midWeak, dummy);
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.computeBradleyTerryElo(sosList);

        assertEquals("Both MidStrong and MidWeak have 50% raw win rate",
                midStrong.getWinRatePercent(), midWeak.getWinRatePercent(), 0.01);
        assertTrue("Bradley-Terry Elo must rank MidStrong (" + midStrong.eloRating
                        + ") strictly above MidWeak (" + midWeak.eloRating + ") due to stronger opponents!",
                midStrong.eloRating > midWeak.eloRating);
        assertTrue("MidStrong avgOpponentElo (" + midStrong.avgOpponentElo
                        + ") must exceed MidWeak avgOpponentElo (" + midWeak.avgOpponentElo + ")",
                midStrong.avgOpponentElo > midWeak.avgOpponentElo);

        // 5) Doubling games per pairing strictly shrinks Elo standard error (±SE)
        int seBefore2x = midStrong.eloStdError;
        for (cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.Participant p : sosList) {
            p.wins *= 2;
            p.losses *= 2;
            p.gamesPlayed *= 2;
            for (int[] h : p.headToHead.values()) {
                h[0] *= 2;
                h[1] *= 2;
            }
        }
        cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.computeBradleyTerryElo(sosList);
        assertTrue("Doubling games must strictly shrink Elo SE (" + midStrong.eloStdError
                        + " < " + seBefore2x + ")",
                midStrong.eloStdError < seBefore2x);
    }

    @Test
    public void historicalLuaBuilderGeneratesPreErrataCrushCardVirusAndSanganScripts() {
        OcgRuleSet rule1999 = TimelineEngine.getActiveRuleSet(catalog, "1999-07-08");
        String lua199907 = cn.garymb.ygomobile.rpg.engine.RpgHistoricalLuaBuilder.buildScript(rule1999, "1999-07-08");

        // 1999-07-08 MUST override c57728570 with 3-opponent-turn draw destruction and NO 2015 damage-0 nerf!
        assertTrue("Must define c57728570 table before newCard", lua199907.contains("c57728570={}"));
        assertTrue("Must register EVENT_DRAW 3-turn opponent check",
                lua199907.contains("e1:SetCode(EVENT_DRAW)")
                        && lua199907.contains("RESET_PHASE+PHASE_END+RESET_OPPO_TURN,3"));
        assertFalse("Must NOT contain 2015 nerfed EFFECT_CHANGE_DAMAGE zero-damage penalty",
                lua199907.contains("EFFECT_CHANGE_DAMAGE"));

        // 1999-11-18 ~ 1999-12-19: Sangan & Witch trigger from ANY zone (no LOCATION_ONFIELD condition)
        String luaPreErrataSangan = cn.garymb.ygomobile.rpg.engine.RpgHistoricalLuaBuilder.buildScript(rule1999, "1999-12-16");
        assertTrue(luaPreErrataSangan.contains("c26202165={}"));
        assertTrue(luaPreErrataSangan.contains("c78010363={}"));
        assertFalse("Pre-1999-12-20 Sangan/Witch must NOT restrict to LOCATION_ONFIELD",
                luaPreErrataSangan.contains("IsPreviousLocation(LOCATION_ONFIELD)"));

        // 1999-12-20+: Sangan & Witch restrict to LOCATION_ONFIELD, still without 2016/2017 HOPT/search lock
        String luaPost1stErrata = cn.garymb.ygomobile.rpg.engine.RpgHistoricalLuaBuilder.buildScript(rule1999, "1999-12-20");
        assertTrue("Post-1999-12-20 Sangan/Witch must check LOCATION_ONFIELD",
                luaPost1stErrata.contains("IsPreviousLocation(LOCATION_ONFIELD)"));
        assertFalse("1999-12-20 Sangan/Witch must NOT have 2016/2017 EFFECT_CANNOT_ACTIVATE lock",
                luaPost1stErrata.contains("EFFECT_CANNOT_ACTIVATE"));

        // 1999-07-08 also restores pre-2014 Catapult Turtle (no once-per-turn)
        assertTrue(lua199907.contains("c95727991={}"));

        // 2018-01-01+: no pre-errata overrides for CCV / Sangan / Witch / Catapult Turtle
        String luaModern = cn.garymb.ygomobile.rpg.engine.RpgHistoricalLuaBuilder.buildScript(null, "2018-01-01");
        assertFalse(luaModern.contains("c57728570={}"));
        assertFalse(luaModern.contains("c26202165={}"));
        assertFalse(luaModern.contains("c78010363={}"));
        assertFalse(luaModern.contains("c95727991={}"));
    }

    @Test
    public void allAiAndCanonDecksHaveZeroUnsummonableNomiOrDeadDependencyFillers() {
        List<AiOpponent> allOpps = AiDeckGenerator.getDemoOpponents();
        for (String date : cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.getCalibratedMilestoneDates()) {
            List<AiOpponent> unlocked = TimelineEngine.getAvailableOpponents(allOpps, date);
            for (AiOpponent opp : unlocked) {
                AiDeckGenerator.GeneratedAiDeck deck = AiDeckGenerator.generateDeck(
                        catalog, opp, date, new Random(1999L + date.hashCode() + opp.id.hashCode()));
                assertEquals(opp.id + " @ " + date + " must have 40 cards", 40, deck.main.size());
                String style = deck.appliedProfile != null ? deck.appliedProfile.fillStyle : "";
                java.util.Set<Integer> uniq = new java.util.HashSet<>(deck.main);
                for (int id : uniq) {
                    RpgCard c = catalog.findCard(id);
                    assertNotNull(c);
                    assertFalse(opp.id + " @ " + date + " must NOT contain Special-Summon-Only/Nomi monster " + c.nameCn,
                            AiDeckGenerator.isSpecialSummonOnlyMonster(c));
                    assertFalse(opp.id + " @ " + date + " must NOT contain Ritual Spell " + c.nameCn,
                            AiDeckGenerator.isRitualSpell(c));
                    if (id == 90219263) {
                        assertTrue(opp.id + " @ " + date + " has Elegant Egotist without Harpie Lady",
                                uniq.contains(76812113));
                    }
                    if (id == 63224564) {
                        assertTrue(opp.id + " @ " + date + " has Cyber Shield without Harpie Lady",
                                uniq.contains(76812113) || uniq.contains(12206212));
                    }
                    if (id == 40240595) {
                        assertTrue(opp.id + " @ " + date + " has Cocoon of Evolution without Petit Moth",
                                uniq.contains(58192742));
                    }
                    if ((id == 24094653 || id == 26902560) && deck.extra.isEmpty()) {
                        org.junit.Assert.fail(opp.id + " @ " + date + " leaked Fusion Spell " + c.nameCn + " with empty Extra Deck");
                    }
                    if ((id == 69015963 || id == 84133008) && !"FUSION_COMBO".equals(style)) {
                        org.junit.Assert.fail(opp.id + " @ " + date + " leaked Fusion-only card " + c.nameCn + " into non-Fusion deck");
                    }
                    String eqReq = AiDeckGenerator.getEquipTargetRequirement(id);
                    if (eqReq != null) {
                        int targets = AiDeckGenerator.countEquipCompatibleMonsters(eqReq, deck.main, catalog);
                        assertTrue(opp.id + " @ " + date + " has dead Equip " + c.nameCn
                                        + " with only " + targets + " valid targets (" + eqReq + ")",
                                targets >= 3);
                    }
                }
            }
        }

        // Also enforce zero Nomi / Ritual / dead Equip bricks in Canon Duel filler padding
        for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel duel : catalog.canonDuels) {
            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide side
                    : new cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide[]{duel.sideA, duel.sideB}) {
                cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.BuiltCanonDeck built =
                        cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.buildDeck(catalog, duel, side, null);
                for (int id : new java.util.HashSet<>(built.main)) {
                    if (built.canonCardIds.contains(id)) continue; // canon cards are preserved faithfully
                    RpgCard c = catalog.findCardOrCanon(id);
                    if (c == null) continue;
                    assertFalse("Canon filler for " + duel.id + "/" + side.characterName
                                    + " must NOT pad Nomi monster " + c.nameCn,
                            AiDeckGenerator.isSpecialSummonOnlyMonster(c));
                    assertFalse("Canon filler for " + duel.id + "/" + side.characterName
                                    + " must NOT pad Ritual Spell " + c.nameCn,
                            AiDeckGenerator.isRitualSpell(c));
                }
            }
        }
    }

    @Test
    public void canonCharacterFidelityAndCoreSynergyGuard() {
        String[] dates = {
                "1999-02-04", "1999-03-01", "1999-03-18", "1999-03-27",
                "1999-05-05", "1999-05-25", "1999-05-27", "1999-07-05",
                "1999-07-08", "1999-07-22", "1999-08-26", "1999-09-23",
                "1999-10-17", "1999-11-18", "1999-12-01", "1999-12-09",
                "1999-12-16", "1999-12-18"
        };
        List<AiOpponent> opponents = AiDeckGenerator.getDemoOpponents();

        // 1) Character-themed AI opponents MUST NEVER contain rival exclusive signature aces across any era
        for (String date : dates) {
            for (AiOpponent opp : opponents) {
                if (date.compareTo(opp.unlockDate) < 0) continue;
                String charId = cn.garymb.ygomobile.rpg.engine.CanonCharacterCardRegistry.resolveCharacterId(opp.id);
                if (charId == null) continue;
                AiDeckGenerator.GeneratedAiDeck deck = AiDeckGenerator.generateDeck(catalog, opp, date, new Random(42L));
                assertEquals(opp.id + " @ " + date + " must have 40 cards", 40, deck.main.size());
                for (int id : deck.main) {
                    RpgCard c = catalog.findCard(id);
                    String cardLabel = c != null ? c.nameCn : String.valueOf(id);
                    assertFalse(opp.id + " (" + charId + ") @ " + date
                                    + " must NOT include rival signature card " + cardLabel + " (" + id + ")",
                            cn.garymb.ygomobile.rpg.engine.CanonCharacterCardRegistry.isForbiddenRivalAce(charId, id));
                }
            }
        }

        // 2) All 14 Canon Duels (28 decks): padding MUST NEVER introduce rival signature aces
        //    AND must actively support key core-card combos (e.g., Petit Moth for Weevil, Labyrinth Tank mats for Paradox)
        for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel duel : catalog.canonDuels) {
            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide side
                    : new cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide[]{duel.sideA, duel.sideB}) {
                cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.BuiltCanonDeck built =
                        cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.buildDeck(catalog, duel, side, null);
                assertEquals(duel.id + "/" + side.characterName + " must have 40 cards", 40, built.main.size());
                for (int id : built.main) {
                    if (built.canonCardIds.contains(id)) continue; // explicit canonCardIds are always preserved 100%
                    RpgCard c = catalog.findCardOrCanon(id);
                    String cardLabel = c != null ? c.nameCn : String.valueOf(id);
                    assertFalse("Canon duel " + duel.id + "/" + side.characterId
                                    + " must NOT pad rival signature card " + cardLabel + " (" + id + ")",
                            cn.garymb.ygomobile.rpg.engine.CanonCharacterCardRegistry.isForbiddenRivalAce(side.characterId, id));
                }
                // Key core-card synergy checks:
                if ("canon_1998_02_10_manga_yugi_weevil".equals(duel.id) && "weevil".equals(side.characterId)) {
                    assertTrue("Weevil Cocoon deck should pad Petit Moth (58192742) to make Cocoon playable!",
                            built.main.contains(58192742));
                }
                if ("canon_1998_03_10_manga_yugi_mako".equals(duel.id) && "mako".equals(side.characterId)) {
                    assertTrue("Mako Ocean deck should include Umi (22702055) and Water support",
                            built.main.contains(22702055) && (built.main.contains(77027445) || built.main.contains(2370081)));
                }
                if ("canon_1998_04_28_manga_joey_rex".equals(duel.id) && "rex".equals(side.characterId)) {
                    assertTrue("Rex Dinosaur deck should prioritize Wasteland (23424603) and Raise Body Heat (51267887)",
                            built.main.contains(23424603) && built.main.contains(51267887));
                }
            }
        }
    }

    @Test
    public void canonDuelPaddingMatchesEventDateCardPoolStrength() {
        boolean checkedDeathTYugi = false;
        boolean checkedDeathTKaiba = false;
        for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel duel : catalog.canonDuels) {
            List<RpgCard> pool = TimelineEngine.getReleasedCards(catalog, duel.timelineDate);
            OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, duel.timelineDate);
            int eraTopLowerAtk = 1200;
            int eraTopLowerDef = 1300;
            for (RpgCard c : pool) {
                if (c == null || !c.isMonster() || c.isExtraDeckCard()) continue;
                if (banlist.getMaxCopies(c.id) <= 0) continue;
                if (c.level <= 4) {
                    if (c.atk > eraTopLowerAtk) eraTopLowerAtk = c.atk;
                    if (c.def > eraTopLowerDef) eraTopLowerDef = c.def;
                }
            }
            int eraAtkFloor = Math.max(800, eraTopLowerAtk - 400);
            int eraDefFloor = Math.max(1000, eraTopLowerDef - 300);

            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide side
                    : new cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide[]{duel.sideA, duel.sideB}) {
                cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.BuiltCanonDeck built =
                        cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.buildDeck(catalog, duel, side, duel.timelineDate);
                assertEquals(40, built.main.size());

                java.util.Set<Integer> charCanonPool =
                        cn.garymb.ygomobile.rpg.engine.CanonCharacterCardRegistry.getCharacterCanonPool(side.characterId, catalog);
                for (int id : built.main) {
                    if (built.canonCardIds.contains(id)) continue; // explicit canon cards preserved 100%
                    RpgCard c = catalog.findCardOrCanon(id);
                    assertNotNull(c);
                    if (!c.isMonster() || !"normal".equalsIgnoreCase(c.frameType)) continue;
                    boolean directSynergy = cn.garymb.ygomobile.rpg.engine.CanonCharacterCardRegistry
                            .isDirectMechanicalSynergy(c, side.canonCardIds, side.canonExtraIds);
                    if (directSynergy) continue; // direct mechanical combo piece (e.g. Petit Moth, Fusion material)

                    boolean isCharCanon = charCanonPool.contains(id);
                    if (eraTopLowerAtk >= 1400) {
                        assertFalse("Canon duel " + duel.id + " (" + duel.timelineDate + ") / " + side.characterName
                                        + " must NOT pad obsolete sub-1000 normal monster "
                                        + c.nameCn + " (" + c.atk + "/" + c.def + ") in a " + eraTopLowerAtk + " ATK era",
                                c.atk < 1000 && c.def < 1000);
                    }
                    if (!isCharCanon && eraTopLowerAtk >= 1800) {
                        boolean meetsEra = (c.level <= 4 && (c.atk >= eraAtkFloor || c.def >= eraDefFloor))
                                || (c.level >= 5 && (c.atk >= eraTopLowerAtk + 400 || c.def >= eraTopLowerDef));
                        assertTrue("Canon duel " + duel.id + " (" + duel.timelineDate + ") / " + side.characterName
                                        + " padded out-of-era non-canon normal monster " + c.nameCn
                                        + " (Lv" + c.level + " " + c.atk + "/" + c.def
                                        + ", expected ATK>=" + eraAtkFloor + " or DEF>=" + eraDefFloor + ")",
                                meetsEra);
                    }
                    if ("canon_1997_07_01_manga_yugi_kaiba_deatht".equals(duel.id)) {
                        assertTrue("Death-T 终战 (" + side.characterName + ") padded normal monster must match 1999-08-26 era strength: "
                                        + c.nameCn + " (" + c.atk + "/" + c.def + ")",
                                c.atk >= eraAtkFloor || c.def >= eraDefFloor);
                    }
                }

                if ("canon_1997_07_01_manga_yugi_kaiba_deatht".equals(duel.id)) {
                    assertEquals("1999-08-26", duel.timelineDate);
                    if ("seto_kaiba".equals(side.characterId)) {
                        checkedDeathTKaiba = true;
                        assertTrue("Death-T Kaiba (1999-08-26) should include Mechanicalchaser (7359741) or La Jinn (97590747)",
                                built.main.contains(7359741) || built.main.contains(97590747));
                    } else if ("yami_yugi".equals(side.characterId)) {
                        checkedDeathTYugi = true;
                        assertTrue("Death-T Yugi (1999-08-26) should include era-viable walls/flips/searchers",
                                built.main.contains(13039848) || built.main.contains(15025844)
                                        || built.main.contains(54652250) || built.main.contains(31560081)
                                        || built.main.contains(26202165));
                    }
                }
            }
        }
        assertTrue("Death-T Yugi & Kaiba must both be present and verified", checkedDeathTYugi && checkedDeathTKaiba);
    }

    @Test
    public void canonDuelsHaveLotdBlueprintsThemePureExodiaAndAvgDialogues() {
        int exodiaDecksChecked = 0;
        for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel duel : catalog.canonDuels) {
            // 1) AVG Dialogue scenes (Legacy of the Duelist style Intro + outcome-branching Outros)
            assertNotNull("Missing introDialogue for " + duel.id, duel.introDialogue);
            assertTrue("introDialogue must have >= 3 lines for " + duel.id, duel.introDialogue.size() >= 3);
            assertNotNull("Missing outroDialogue for " + duel.id, duel.outroDialogue);
            assertTrue("outroDialogue must have >= 2 lines for " + duel.id, duel.outroDialogue.size() >= 2);
            assertTrue("canonWinnerSide must be A or B for " + duel.id,
                    "A".equals(duel.canonWinnerSide) || "B".equals(duel.canonWinnerSide));
            if ("canon_1998_11_17_manga_yugi_kaiba_castle".equals(duel.id)
                    || "canon_1999_11_09_manga_joey_rarehunter".equals(duel.id)) {
                assertEquals("Canonical winner must be Side B for " + duel.id, "B", duel.canonWinnerSide);
            }
            assertNotNull("Missing outroSideAWin for " + duel.id, duel.outroSideAWin);
            assertTrue("outroSideAWin must have >= 2 lines for " + duel.id, duel.outroSideAWin.size() >= 2);
            assertNotNull("Missing outroSideBWin for " + duel.id, duel.outroSideBWin);
            assertTrue("outroSideBWin must have >= 2 lines for " + duel.id, duel.outroSideBWin.size() >= 2);

            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DialogueLine dl : duel.introDialogue) {
                assertTrue("Invalid speakerSide in intro of " + duel.id,
                        "A".equals(dl.speakerSide) || "B".equals(dl.speakerSide) || "NARRATOR".equals(dl.speakerSide));
                assertNotNull(dl.speakerName);
                assertFalse(dl.speakerName.trim().isEmpty());
                assertNotNull(dl.text);
                assertFalse(dl.text.trim().isEmpty());
            }

            List<cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DialogueLine> outroA = duel.getOutroForWinner("A");
            List<cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DialogueLine> outroB = duel.getOutroForWinner("B");
            StringBuilder textA = new StringBuilder();
            StringBuilder textB = new StringBuilder();
            boolean aBranchHasA = false, aBranchHasB = false;
            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DialogueLine dl : outroA) {
                assertTrue("Invalid speakerSide in outroSideAWin of " + duel.id,
                        "A".equals(dl.speakerSide) || "B".equals(dl.speakerSide) || "NARRATOR".equals(dl.speakerSide));
                assertNotNull(dl.speakerName);
                assertFalse(dl.speakerName.trim().isEmpty());
                assertNotNull(dl.text);
                assertFalse(dl.text.trim().isEmpty());
                if ("A".equals(dl.speakerSide)) aBranchHasA = true;
                if ("B".equals(dl.speakerSide)) aBranchHasB = true;
                textA.append(dl.speakerSide).append(':').append(dl.text).append('\n');
            }
            boolean bBranchHasA = false, bBranchHasB = false;
            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DialogueLine dl : outroB) {
                assertTrue("Invalid speakerSide in outroSideBWin of " + duel.id,
                        "A".equals(dl.speakerSide) || "B".equals(dl.speakerSide) || "NARRATOR".equals(dl.speakerSide));
                assertNotNull(dl.speakerName);
                assertFalse(dl.speakerName.trim().isEmpty());
                assertNotNull(dl.text);
                assertFalse(dl.text.trim().isEmpty());
                if ("A".equals(dl.speakerSide)) bBranchHasA = true;
                if ("B".equals(dl.speakerSide)) bBranchHasB = true;
                textB.append(dl.speakerSide).append(':').append(dl.text).append('\n');
            }
            assertTrue("outroSideAWin must include lines from both A and B for " + duel.id, aBranchHasA && aBranchHasB);
            assertTrue("outroSideBWin must include lines from both A and B for " + duel.id, bBranchHasA && bBranchHasB);
            assertFalse("Side A win and Side B win outros must be distinct for " + duel.id,
                    textA.toString().equals(textB.toString()));
            assertFalse("First lines of Side A win and Side B win must differ for " + duel.id,
                    outroA.get(0).text.equals(outroB.get(0).text));

            for (cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide side
                    : new cn.garymb.ygomobile.rpg.model.OcgCanonDuel.DuelSide[]{duel.sideA, duel.sideB}) {
                // 2) LotD blueprint label + event-date legality of lotdEraPaddingIds
                assertNotNull("Missing lotdDeckName for " + duel.id + "/" + side.characterId, side.lotdDeckName);
                assertFalse(side.lotdDeckName.trim().isEmpty());
                if (side.lotdEraPaddingIds != null) {
                    for (Integer pid : side.lotdEraPaddingIds) {
                        assertTrue("lotdEraPaddingId " + pid + " in " + duel.id + "/" + side.characterId
                                        + " must be released <= " + duel.timelineDate,
                                TimelineEngine.isCardReleased(catalog, pid, duel.timelineDate));
                        assertFalse("lotdEraPaddingId " + pid + " must not be rival ace",
                                cn.garymb.ygomobile.rpg.engine.CanonCharacterCardRegistry
                                        .isForbiddenRivalAce(side.characterId, pid));
                    }
                }

                // 3) Theme-pure EXODIA_DRAW guard: ZERO padded Field/Equip/self-discard cards!
                cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.BuiltCanonDeck built =
                        cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder.buildDeck(catalog, duel, side, duel.timelineDate);
                assertEquals(40, built.main.size());
                if ("EXODIA_DRAW".equals(side.fillStyle)) {
                    exodiaDecksChecked++;
                    for (int id : built.main) {
                        if (built.canonCardIds.contains(id)) continue;
                        RpgCard c = catalog.findCardOrCanon(id);
                        assertNotNull(c);
                        assertFalse("EXODIA_DRAW " + duel.id + "/" + side.characterName
                                        + " must NOT pad Field Spell " + c.nameCn,
                                AiDeckGenerator.isFieldSpell(c));
                        assertFalse("EXODIA_DRAW " + duel.id + "/" + side.characterName
                                        + " must NOT pad Equip Spell " + c.nameCn,
                                cn.garymb.ygomobile.rpg.ai.RpgAiCardKnowledge.EQUIP_SPELLS.contains(id)
                                        || AiDeckGenerator.getEquipTargetRequirement(id) != null);
                        assertFalse("EXODIA_DRAW " + duel.id + "/" + side.characterName
                                        + " must NOT pad self-discard card " + c.nameCn,
                                id == 72892473 || id == 33508719 || id == 79759861);
                    }
                    assertTrue("EXODIA_DRAW deck should include Pot of Greed (55144522)",
                            built.main.contains(55144522));
                    assertTrue("EXODIA_DRAW deck should include Swords of Revealing Light (72302403)",
                            built.main.contains(72302403));
                    assertTrue("EXODIA_DRAW deck should include Giant Soldier of Stone (13039848)",
                            built.main.contains(13039848));
                }
            }
        }
        assertTrue("Should validate all 3 EXODIA_DRAW canon decks", exodiaDecksChecked >= 3);
    }

    @Test
    public void dynamicJenksTieringSeparatesNaturalClustersAndLocksTies() {
        // 1) 1999-11-18 real field: Jenks Natural Breaks cleanly partitions S/A/B/C/D
        //    instead of wasting 3 cuts on the sparse bottom decks like naive Top-4-Gaps.
        int[] elos1118 = {1889, 1889, 1755, 1755, 1720, 1652, 1566, 1539, 1423, 1253, 1013, 548};
        String[] tiers1118 = cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.computeDynamicTiers(elos1118);
        assertEquals("S", tiers1118[0]);
        assertEquals("Tied 1889 decks MUST share S tier", "S", tiers1118[1]);
        assertEquals("A", tiers1118[2]);
        assertEquals("Tied 1755 decks MUST share A tier", "A", tiers1118[3]);
        assertEquals("A", tiers1118[5]); // 1652
        assertEquals("B", tiers1118[6]); // 1566
        assertEquals("B", tiers1118[8]); // 1423
        assertEquals("C", tiers1118[9]); // 1253
        assertEquals("D", tiers1118[10]); // 1013
        assertEquals("D", tiers1118[11]); // 548

        // 2) Small Vol.1 field (N=4): near-tied top 2 (1799 vs 1781, only 18 Elo apart) BOTH receive S!
        int[] elos0204 = {1799, 1781, 1278, 1142};
        String[] tiers0204 = cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine.computeDynamicTiers(elos0204);
        assertEquals("S", tiers0204[0]);
        assertEquals("Near-tied 1781 in 4-deck field should stay S alongside 1799", "S", tiers0204[1]);
        assertEquals("D", tiers0204[3]);

        // 3) EraBountyCalibrator uses the exact same dynamic tiering
        assertEquals("S", cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve("ai_exodia_master", "1999-11-18").getTierLabel());
        assertEquals("S", cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve("ai_tokyo_dome", "1999-11-18").getTierLabel());
        assertEquals("B", cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve("ai_equip_beat", "1999-11-18").getTierLabel());
        assertEquals("C", cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve("ai_weekend_challenger", "1999-11-18").getTierLabel());
        assertEquals("D", cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve("ai_shop_regular", "1999-11-18").getTierLabel());
    }
}
