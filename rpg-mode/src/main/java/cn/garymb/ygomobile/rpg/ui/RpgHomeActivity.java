package cn.garymb.ygomobile.rpg.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import cn.garymb.ygomobile.rpg.R;
import cn.garymb.ygomobile.rpg.bridge.RpgDuelHostBridge;
import cn.garymb.ygomobile.rpg.bridge.RpgSessionManager;
import cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels;
import cn.garymb.ygomobile.rpg.ai.RpgAiStrategy;
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

/**
 * Self-contained Android UI for the 1999 OCG Timeline RPG Mode (1999-02-04 ~ 1999-12-31):
 * - Tab 0: Shop (Vol.1-6 / Booster 1-6 / STARTER BOX / EX / LE1 / PP1-2 at authentic JPY prices)
 * - Tab 1: 1999 Special Promo & Event Card Giveaways
 * - Tab 2: Card Collection & Dust Craft (auto-dismantle excess >3 copies)
 * - Tab 3: Ownership + Timeline + Banlist Restricted Deck Builder (with native DeckEditor reuse)
 * - Tab 4: Timeline-Compliant AI Duel Launcher (Manual & Real-ocgcore AI Auto-Duel) & Token Rewards
 * - Tab 5: OCG Timeline Tournament (4-player / 8-player randomized elimination bracket)
 * - Tab 6: OCG Official Card Errata History Archive (Series 1 ~ Series 13)
 * - Tab 7: Real ocgcore AI-vs-AI Showdown (Headless Batch Win-Rate + Live 3D Spectate)
 */
public class RpgHomeActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_TAB = "extra_initial_tab";
    public static final int TAB_SHOP = 0;
    public static final int TAB_EVENTS = 1;
    public static final int TAB_COLLECTION = 2;
    public static final int TAB_DECK = 3;
    public static final int TAB_AI_DUEL = 4;
    public static final int TAB_TOURNAMENT = 5;
    public static final int TAB_ERRATA = 6;
    public static final int TAB_AI_SHOWDOWN = 7;

    private RpgCatalog catalog;
    private RpgRepository repository;
    private RpgProfile profile;
    private final PackOpeningEngine packEngine = new PackOpeningEngine();

    private TextView tvTokens;
    private TextView tvDust;
    private TextView tvTimelineDate;
    private TextView tvTimelineDigestBanner;
    private Button btnTimelineDigest;
    private Button btnActiveBanlist;
    private TextView tvActiveBanlist;
    private TextView tvActiveRule;
    private LinearLayout layoutMilestones;
    private LinearLayout layoutTabActions;
    private LinearLayout layoutContent;
    private TabLayout tabLayout;

    private int activeTab = 0;
    private boolean collectionOnlyOwned = false;
    private String showdownKeyA = "current";
    private String showdownKeyB = null;
    private RpgAiAutoDuelModels.BatchResult lastShowdownReport = null;
    private TimelineEngine.TimelineDigest currentTimelineDigest = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rpg_home);

        repository = new RpgRepository(this);
        profile = repository.loadProfile();
        try {
            catalog = RpgCatalogLoader.loadFromAssets(this);
        } catch (IOException e) {
            Toast.makeText(this, "加载 OCG RPG 数据失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        profile.currentTimelineDate = TimelineEngine.clampDate(catalog, profile.currentTimelineDate);
        CraftingEngine.CraftResult legacySurplus = CraftingEngine.dismantleAllSurplus(profile, catalog);
        if (legacySurplus.success) {
            saveProfileSafe();
            Toast.makeText(this, "旧存档多余卡(>3张)已自动分解：" + legacySurplus.message, Toast.LENGTH_LONG).show();
        }

        tvTokens = findViewById(R.id.tv_rpg_tokens);
        tvDust = findViewById(R.id.tv_rpg_dust);
        tvTimelineDate = findViewById(R.id.tv_timeline_date);
        tvTimelineDigestBanner = findViewById(R.id.tv_timeline_digest_banner);
        btnTimelineDigest = findViewById(R.id.btn_timeline_digest);
        btnActiveBanlist = findViewById(R.id.btn_active_banlist);
        tvActiveBanlist = findViewById(R.id.tv_active_banlist);
        tvActiveRule = findViewById(R.id.tv_active_rule);
        layoutMilestones = findViewById(R.id.layout_milestones);
        layoutTabActions = findViewById(R.id.layout_tab_actions);
        layoutContent = findViewById(R.id.layout_rpg_content);
        tabLayout = findViewById(R.id.tab_rpg_nav);

        findViewById(R.id.btn_rpg_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pick_date).setOnClickListener(v -> openDatePicker());
        btnTimelineDigest.setOnClickListener(v -> openTimelineDigestDialog());
        tvTimelineDigestBanner.setOnClickListener(v -> openTimelineDigestDialog());
        btnActiveBanlist.setOnClickListener(v -> openActiveBanlistDialog());
        tvActiveBanlist.setOnClickListener(v -> openActiveBanlistDialog());

        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabLayout.addTab(tabLayout.newTab().setText("卡包商店"));
        tabLayout.addTab(tabLayout.newTab().setText("特典活动"));
        tabLayout.addTab(tabLayout.newTab().setText("卡片合成"));
        tabLayout.addTab(tabLayout.newTab().setText("RPG 组卡"));
        tabLayout.addTab(tabLayout.newTab().setText("AI 对战"));
        tabLayout.addTab(tabLayout.newTab().setText("🏆 锦标赛"));
        tabLayout.addTab(tabLayout.newTab().setText("效果勘误"));
        tabLayout.addTab(tabLayout.newTab().setText("⚔️ AI 对决"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                activeTab = tab.getPosition();
                renderCurrentTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                renderCurrentTab();
            }
        });

        RpgSessionManager.setCachedCatalog(catalog);
        currentTimelineDigest = TimelineEngine.buildDigestAtDate(
                catalog, AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        applyInitialTabFromIntent(getIntent());
        renderMilestones();
        refreshAll();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyInitialTabFromIntent(intent);
    }

    private void applyInitialTabFromIntent(Intent intent) {
        if (intent == null || tabLayout == null) return;
        int requested = intent.getIntExtra(EXTRA_INITIAL_TAB, -1);
        if (requested >= 0 && requested < tabLayout.getTabCount()) {
            activeTab = requested;
            TabLayout.Tab tab = tabLayout.getTabAt(requested);
            if (tab != null) {
                tab.select();
            }
            intent.removeExtra(EXTRA_INITIAL_TAB);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null) {
            profile = repository.loadProfile();
        }

        // Sync back deck edits made in YGOMobile's native DeckEditor
        RpgSessionManager.EditedDeckResult editedDeck = RpgSessionManager.consumePendingEditedDeck();
        if (editedDeck != null && catalog != null) {
            profile.activeDeckMain = new ArrayList<>(editedDeck.main);
            profile.activeDeckExtra = new ArrayList<>(editedDeck.extra);
            saveProfileSafe();
            TabLayout.Tab deckTab = tabLayout.getTabAt(TAB_DECK);
            if (deckTab != null) deckTab.select();

            DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
            if (val.valid) {
                Toast.makeText(this,
                        "✔ 已同步原版卡组编辑器修改（主 " + profile.activeDeckMain.size()
                                + " / 额外 " + profile.activeDeckExtra.size() + "，合规可出战）",
                        Toast.LENGTH_LONG).show();
            } else {
                StringBuilder errs = new StringBuilder();
                int shown = 0;
                for (String e : val.errors) {
                    if (shown++ < 8) errs.append("• ").append(e).append("\n");
                }
                if (val.errors.size() > 8) {
                    errs.append("… 其余 ").append(val.errors.size() - 8).append(" 项问题");
                }
                new AlertDialog.Builder(this)
                        .setTitle("已导入编辑器卡组 · 存在 RPG 规则冲突")
                        .setMessage("当前卡组已同步，但存在未拥有/未发售/超限卡片：\n\n" + errs.toString().trim())
                        .setPositiveButton("一键剔除不合规卡", (d, w) -> {
                            int removed = stripIllegalCardsFromActiveDeck();
                            saveProfileSafe();
                            Toast.makeText(this, "已自动剔除 " + removed + " 张不合规卡片", Toast.LENGTH_SHORT).show();
                            refreshAll();
                        })
                        .setNegativeButton("保留并手动调整", null)
                        .show();
            }
        }

        DuelRewardEngine.RewardSettlement settlement = RpgSessionManager.consumeLastSettlement();
        Boolean tournamentOutcome = RpgSessionManager.consumeTournamentDuelOutcome();
        TournamentEngine.TournamentBracket activeBracket = RpgSessionManager.getActiveTournament();
        if (tournamentOutcome != null && activeBracket != null && catalog != null) {
            TournamentEngine.recordPlayerMatchResult(
                    catalog,
                    activeBracket,
                    tournamentOutcome,
                    tournamentOutcome ? "你通过手打晋级！" : "手打落败止步本轮",
                    null,
                    new Random()
            );
            int prize = TournamentEngine.claimRewardIfFinished(profile, activeBracket);
            if (prize > 0) {
                saveProfileSafe();
            }
            TabLayout.Tab tTab = tabLayout.getTabAt(TAB_TOURNAMENT);
            if (tTab != null) tTab.select();
        }
        if (settlement != null) {
            String extraMsg = (tournamentOutcome != null && activeBracket != null && activeBracket.finished)
                    ? ("\n\n🏆 锦标赛完赛：" + activeBracket.playerPlacementLabel
                    + "（额外奖金 +" + activeBracket.rewardTokens + " 代币）")
                    : "";
            new AlertDialog.Builder(this)
                    .setTitle("RPG 决斗结算 · " + settlement.outcomeLabel)
                    .setMessage("获得代币：+" + settlement.tokensEarned
                            + "\n当前代币余额：" + profile.tokens + extraMsg)
                    .setPositiveButton("收下奖励", null)
                    .show();
        }
        if (catalog != null) {
            refreshAll();
        }
    }

    private int stripIllegalCardsFromActiveDeck() {
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        java.util.Set<Integer> releasedIds = new java.util.HashSet<>();
        for (RpgCard rc : TimelineEngine.getReleasedCards(catalog, profile.currentTimelineDate)) {
            releasedIds.add(rc.id);
        }
        Map<Integer, Integer> used = new HashMap<>();
        int removed = 0;

        List<Integer> cleanMain = new ArrayList<>();
        for (Integer id : profile.activeDeckMain) {
            if (id == null) continue;
            RpgCard c = catalog.findCard(id);
            if (c == null || !releasedIds.contains(id) || c.isExtraDeckCard()) {
                removed++;
                continue;
            }
            int cap = Math.min(profile.getOwnedCount(id), banlist.getMaxCopies(id));
            int cur = used.getOrDefault(id, 0);
            if (cur < cap && cleanMain.size() < DeckValidator.MAX_MAIN_DECK) {
                cleanMain.add(id);
                used.put(id, cur + 1);
            } else {
                removed++;
            }
        }

        List<Integer> cleanExtra = new ArrayList<>();
        for (Integer id : profile.activeDeckExtra) {
            if (id == null) continue;
            RpgCard c = catalog.findCard(id);
            if (c == null || !releasedIds.contains(id) || !c.isExtraDeckCard()) {
                removed++;
                continue;
            }
            int cap = Math.min(profile.getOwnedCount(id), banlist.getMaxCopies(id));
            int cur = used.getOrDefault(id, 0);
            if (cur < cap && cleanExtra.size() < DeckValidator.MAX_EXTRA_DECK) {
                cleanExtra.add(id);
                used.put(id, cur + 1);
            } else {
                removed++;
            }
        }

        profile.activeDeckMain = cleanMain;
        profile.activeDeckExtra = cleanExtra;
        return removed;
    }

    private void saveProfileSafe() {
        try {
            repository.saveProfile(profile);
        } catch (IOException e) {
            Toast.makeText(this, "保存存档失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAll() {
        profile.currentTimelineDate = TimelineEngine.clampDate(catalog, profile.currentTimelineDate);
        tvTokens.setText("代币: " + profile.tokens);
        tvDust.setText("尘埃: " + profile.dust);
        List<OcgPack> availPacks = TimelineEngine.getAvailablePacks(catalog, profile.currentTimelineDate);
        List<RpgCard> relCards = TimelineEngine.getReleasedCards(catalog, profile.currentTimelineDate);
        int ownedRelCards = 0;
        for (RpgCard c : relCards) {
            if (profile.getOwnedCount(c.id) > 0) ownedRelCards++;
        }
        int openedAvailPacks = 0;
        for (OcgPack p : availPacks) {
            if (profile.packsOpened.getOrDefault(p.id, 0) > 0) openedAvailPacks++;
        }
        int preErrataCnt = CardErrataEngine.getRequiredPreErrataScriptIds(catalog, profile.currentTimelineDate).size();
        tvTimelineDate.setText("当前时间轴: " + profile.currentTimelineDate
                + "\n已收集卡片: " + ownedRelCards + " / " + relCards.size() + " 种"
                + "\n已购商品: " + openedAvailPacks + " / " + availPacks.size() + " 款"
                + " · 旧版勘误卡: " + preErrataCnt + " 张");

        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        int banCount = (banlist.forbidden != null ? banlist.forbidden.size() : 0)
                + (banlist.limited != null ? banlist.limited.size() : 0)
                + (banlist.semiLimited != null ? banlist.semiLimited.size() : 0);
        tvActiveBanlist.setText("当前禁限表: " + banlist.name + " — " + banlist.description);
        if (btnActiveBanlist != null) {
            btnActiveBanlist.setText(banCount > 0 ? ("🚫 当期禁限表(" + banCount + ")") : "🚫 当期禁限表(无)");
            btnActiveBanlist.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor(banCount > 0 ? "#B91C1C" : "#334155")));
        }
        OcgRuleSet ruleSet = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        tvActiveRule.setText("当前规则（随时间轴自动锁定）: " + ruleSet.name + " — " + ruleSet.description);
        updateTimelineDigestBanner();
        highlightActiveMilestone();
        renderCurrentTab();
    }

    private void openActiveBanlistDialog() {
        if (catalog == null) return;
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        RpgPackVisualDialogs.showBanlistVisualDialog(this, catalog, profile, banlist);
    }

    private void updateTimelineDigestBanner() {
        if (currentTimelineDigest == null
                || !profile.currentTimelineDate.equals(currentTimelineDigest.toDate)) {
            currentTimelineDigest = TimelineEngine.buildDigestAtDate(
                    catalog, AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        }
        int total = currentTimelineDigest.getTotalChangeCount();
        if (tvTimelineDigestBanner != null) {
            tvTimelineDigestBanner.setText(currentTimelineDigest.buildShortSummary()
                    + (total > 0 ? "  ▸ 点击看详情" : ""));
            tvTimelineDigestBanner.setTextColor(Color.parseColor(total > 0 ? "#FDE68A" : "#94A3B8"));
        }
        if (btnTimelineDigest != null) {
            btnTimelineDigest.setText(total > 0 ? ("📰 当期动态(" + total + ")") : "📰 当期动态");
            btnTimelineDigest.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor(total > 0 ? "#D97706" : "#334155")));
        }
    }

    private void openTimelineDigestDialog() {
        if (catalog == null) return;
        if (currentTimelineDigest == null
                || !profile.currentTimelineDate.equals(currentTimelineDigest.toDate)) {
            currentTimelineDigest = TimelineEngine.buildDigestAtDate(
                    catalog, AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        }
        RpgTimelineDigestDialog.show(this, catalog, profile, currentTimelineDigest);
    }

    private void selectTimelineDate(String targetDate) {
        String prevDate = profile.currentTimelineDate;
        String clamped = TimelineEngine.clampDate(catalog, targetDate);
        profile.currentTimelineDate = clamped;
        currentTimelineDigest = TimelineEngine.buildDigestForTransition(
                catalog, AiDeckGenerator.getDemoOpponents(), prevDate, clamped);
        saveProfileSafe();
        refreshAll();
    }

    private void highlightActiveMilestone() {
        if (layoutMilestones == null) return;
        for (int i = 0; i < layoutMilestones.getChildCount(); i++) {
            View child = layoutMilestones.getChildAt(i);
            if (child instanceof Button) {
                boolean active = profile.currentTimelineDate.equals(child.getTag());
                child.setBackgroundColor(Color.parseColor(active ? "#D97706" : "#334155"));
            }
        }
    }

    private void renderMilestones() {
        layoutMilestones.removeAllViews();
        List<String> milestones = TimelineEngine.getTimelineMilestones(catalog);
        for (final String date : milestones) {
            Button btn = new Button(this);
            btn.setTag(date);
            btn.setText(date);
            btn.setTextSize(11f);
            btn.setTextColor(Color.WHITE);
            btn.setAllCaps(false);
            btn.setBackgroundColor(Color.parseColor(
                    date.equals(profile.currentTimelineDate) ? "#D97706" : "#334155"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
            lp.setMarginEnd(dp(6));
            btn.setLayoutParams(lp);
            btn.setPadding(dp(10), 0, dp(10), 0);
            btn.setOnClickListener(v -> selectTimelineDate(date));
            layoutMilestones.addView(btn);
        }
    }

    private void openDatePicker() {
        int y = 1999, m = 1, d = 4;
        try {
            String[] parts = profile.currentTimelineDate.split("-");
            y = Integer.parseInt(parts[0]);
            m = Integer.parseInt(parts[1]) - 1;
            d = Integer.parseInt(parts[2]);
        } catch (Exception ignored) {}

        DatePickerDialog dlg = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String picked = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            selectTimelineDate(picked);
        }, y, m, d);
        Calendar minCal = Calendar.getInstance();
        minCal.set(1999, Calendar.FEBRUARY, 4, 0, 0, 0);
        Calendar maxCal = Calendar.getInstance();
        maxCal.set(1999, Calendar.DECEMBER, 31, 23, 59, 59);
        dlg.getDatePicker().setMinDate(minCal.getTimeInMillis());
        dlg.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
        dlg.show();
    }

    private void renderCurrentTab() {
        layoutTabActions.removeAllViews();
        layoutContent.removeAllViews();
        switch (activeTab) {
            case 0:
                renderShopTab();
                break;
            case 1:
                renderEventsTab();
                break;
            case 2:
                renderCollectionTab();
                break;
            case 3:
                renderDeckTab();
                break;
            case 4:
                renderAiDuelTab();
                break;
            case 5:
                renderTournamentTab();
                break;
            case 6:
                renderErrataTab();
                break;
            case 7:
                renderAiShowdownTab();
                break;
        }
    }

    // ==================== TAB 0: SHOP & PACK OPENING ====================
    private void renderShopTab() {
        List<OcgPack> availPacks = TimelineEngine.getAvailablePacks(catalog, profile.currentTimelineDate);
        TextView hint = makeSubtleText("仅显示 " + profile.currentTimelineDate
                + " 已解锁的 " + availPacks.size() + " 款商品（代币价 1:1 还原当年日元定价）");
        layoutTabActions.addView(hint);

        for (final OcgPack pack : availPacks) {
            LinearLayout card = makeCardContainer();

            java.util.Set<Integer> seenIds = new java.util.LinkedHashSet<>();
            int unlockedInPack = 0;
            int playset3InPack = 0;
            for (RpgCard c : pack.cards) {
                if (!seenIds.add(c.id)) continue;
                int own = profile.getOwnedCount(c.id);
                if (own >= 1) unlockedInPack++;
                if (own >= 3) playset3InPack++;
            }
            int totalInPack = seenIds.size();

            // Top row: authentic pack/box cover art on left + product info on right
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            ImageView ivCover = new ImageView(this);
            LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(dp(78), dp(116));
            coverLp.setMarginEnd(dp(12));
            ivCover.setLayoutParams(coverLp);
            ivCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable coverBg = new GradientDrawable();
            coverBg.setColor(Color.parseColor("#0F172A"));
            coverBg.setCornerRadius(dp(6));
            coverBg.setStroke(dp(1), Color.parseColor("#334155"));
            ivCover.setBackground(coverBg);
            ivCover.setPadding(dp(3), dp(3), dp(3), dp(3));
            RpgCardArtLoader.loadPackCoverInto(this, ivCover, pack.id);
            ivCover.setOnClickListener(v -> showPackPoolDialog(pack));
            topRow.addView(ivCover);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = makeTitleText("[" + pack.getProductTypeLabelCn() + "] " + pack.name);
            String unit = pack.fixedContents ? "盒/套" : ("VENDING_MACHINE".equals(pack.productType) ? "次" : "包");
            TextView meta = makeSubtleText("发售日: " + pack.releaseDate
                    + " · " + pack.cardsPerPack + " 张/" + unit
                    + " · 定价 " + pack.priceTokens + " 代币（原价 ¥" + pack.priceTokens + "）"
                    + " · 已购 " + profile.packsOpened.getOrDefault(pack.id, 0) + " " + unit);
            TextView progress = makeSubtleText("收集进度：已解锁 " + unlockedInPack + " / " + totalInPack
                    + " 种 · 已拥有 ≥3 张：" + playset3InPack + " / " + totalInPack + " 种");
            progress.setTextColor(Color.parseColor("#38BDF8"));
            TextView odds = makeSubtleText(pack.description != null ? pack.description : pack.slotRules.description);

            infoCol.addView(title);
            infoCol.addView(meta);
            infoCol.addView(progress);
            infoCol.addView(odds);
            topRow.addView(infoCol);
            card.addView(topRow);

            Button poolBtn = makeActionButton("查看卡池", "#475569");
            poolBtn.setOnClickListener(v -> showPackPoolDialog(pack));

            if (pack.fixedContents) {
                Button buyBox = makeActionButton("购买整套 (" + pack.priceTokens + ")", "#059669");
                buyBox.setOnClickListener(v -> doOpenPack(pack, 1));
                card.addView(makeEqualWeightButtonRow(8, buyBox, poolBtn));
            } else {
                Button b1 = makeActionButton("买 1 " + unit + " (" + pack.priceTokens + ")", "#D97706");
                b1.setOnClickListener(v -> doOpenPack(pack, 1));
                Button b5 = makeActionButton("买 5 " + unit + " (" + (pack.priceTokens * 5) + ")", "#0284C7");
                b5.setOnClickListener(v -> doOpenPack(pack, 5));
                card.addView(makeEqualWeightButtonRow(8, b1, b5, poolBtn));
            }
            layoutContent.addView(card);
        }
    }

    private void showPackPoolDialog(OcgPack pack) {
        RpgPackVisualDialogs.showPackPoolVisualDialog(this, catalog, profile, pack);
    }

    private void doOpenPack(OcgPack pack, int count) {
        PackOpeningEngine.PackOpenResult res = packEngine.openPacks(profile, pack, count, catalog.craftingRules);
        if (!res.success) {
            Toast.makeText(this, res.errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        saveProfileSafe();
        refreshAll();
        RpgPackVisualDialogs.showPackOpeningSimulator(this, catalog, profile, pack, count, res, this::doOpenPack);
    }

    // ==================== TAB 1: 1999 SPECIAL PROMO EVENTS ====================
    private void renderEventsTab() {
        String curDate = profile.currentTimelineDate;
        List<OcgEvent> visibleEvents = new ArrayList<>();
        if (catalog.events != null) {
            for (OcgEvent ev : catalog.events) {
                if (ev != null && TimelineEngine.isReleasedOnOrBefore(ev.releaseDate, curDate)) {
                    visibleEvents.add(ev);
                }
            }
        }
        // Sort descending by releaseDate (newest event first)
        visibleEvents.sort((a, b) -> {
            String da = a.releaseDate != null ? a.releaseDate : "";
            String db = b.releaseDate != null ? b.releaseDate : "";
            return db.compareTo(da);
        });

        TextView hint = makeSubtleText("截至 " + curDate + " 已开启 " + visibleEvents.size()
                + " 项 OCG 特典/大会/攻略本赠送活动（按时间降序）");
        layoutTabActions.addView(hint);

        if (visibleEvents.isEmpty()) {
            LinearLayout emptyCard = makeCardContainer();
            emptyCard.addView(makeTitleText("📅 截至 " + curDate + " 暂无已开启的特典活动"));
            emptyCard.addView(makeSubtleText("推进时间轴即可解锁对应日期的真实 OCG 期间特典与大会纪念卡活动。"));
            layoutContent.addView(emptyCard);
            return;
        }

        for (final OcgEvent ev : visibleEvents) {
            boolean claimed = profile.claimedEventIds != null && profile.claimedEventIds.contains(ev.id);
            int rewardCount = ev.rewardCards != null ? ev.rewardCards.size() : 0;
            String modeNote = ("RANDOM_PICK".equalsIgnoreCase(ev.mode) && ev.randomPickCount > 0)
                    ? (" · 随机抽 " + ev.randomPickCount + " 张（共 " + rewardCount + " 种）")
                    : (" · 含 " + rewardCount + " 张特典卡");

            LinearLayout card = makeCardContainer();
            card.addView(makeTitleText(ev.name + (claimed ? " 【已领取】" : "")));
            card.addView(makeSubtleText("活动日期: " + ev.releaseDate
                    + (ev.endDate != null && !ev.endDate.equals(ev.releaseDate) ? " ~ " + ev.endDate : "")
                    + (ev.requiredWins > 0 ? " · 需累计胜场 ≥ " + ev.requiredWins : " · 免费领取")
                    + modeNote));
            card.addView(makeSubtleText(ev.description));

            // Compact bottom row: status or claim action on the left, small "查看卡池" button pinned on the right
            LinearLayout bottomRow = new LinearLayout(this);
            bottomRow.setOrientation(LinearLayout.HORIZONTAL);
            bottomRow.setGravity(Gravity.CENTER_VERTICAL);
            bottomRow.setPadding(0, dp(6), 0, 0);

            Button poolBtn = makeActionButton("查看卡池", "#475569");
            poolBtn.setTextSize(11f);
            LinearLayout.LayoutParams poolLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(26));
            poolLp.setMarginStart(dp(8));
            poolBtn.setLayoutParams(poolLp);
            poolBtn.setPadding(dp(10), 0, dp(10), 0);
            poolBtn.setOnClickListener(v -> RpgPackVisualDialogs.showEventPoolVisualDialog(this, catalog, profile, ev));

            if (claimed) {
                TextView done = makeSubtleText("✓ 特典已收入卡库");
                done.setTextColor(Color.parseColor("#34D399"));
                done.setPadding(0, 0, 0, 0);
                done.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                bottomRow.addView(done);
                bottomRow.addView(poolBtn);
            } else {
                Button btn = makeActionButton("🎁 领取特典单卡", "#7C3AED");
                btn.setTextSize(11.5f);
                LinearLayout.LayoutParams claimLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
                btn.setLayoutParams(claimLp);
                btn.setPadding(dp(12), 0, dp(12), 0);
                btn.setOnClickListener(v -> {
                    EventClaimEngine.ClaimResult r = EventClaimEngine.claim(profile, ev, new Random());
                    if (!r.success) {
                        Toast.makeText(this, r.errorMessage, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveProfileSafe();
                    refreshAll();
                    StringBuilder sb = new StringBuilder();
                    for (RpgCard c : r.grantedCards) {
                        sb.append("[").append(c.rarity).append("] ").append(c.getDisplayName()).append(" x1\n");
                    }
                    if (r.autoDismantledCount > 0) {
                        sb.append("\n超出 3 张的 ").append(r.autoDismantledCount)
                                .append(" 张已自动分解为 +").append(r.autoDismantledDust).append(" 尘埃");
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("特典领取成功 · " + ev.name)
                            .setMessage(sb.toString())
                            .setPositiveButton("收下特典", null)
                            .show();
                });
                bottomRow.addView(btn);
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                bottomRow.addView(spacer);
                bottomRow.addView(poolBtn);
            }
            card.addView(bottomRow);
            layoutContent.addView(card);
        }
    }

    // ==================== TAB 2: CARD CRAFTING (CRAFT-ONLY, HIDES >= 3 OWNED) ====================
    private void renderCollectionTab() {
        Button toggleBtn = makeActionButton(
                collectionOnlyOwned ? "筛选: 仅看未拥有(0张)" : "筛选: 全部未满3张", "#334155");
        toggleBtn.setOnClickListener(v -> {
            collectionOnlyOwned = !collectionOnlyOwned;
            renderCurrentTab();
        });
        layoutTabActions.addView(toggleBtn);

        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        List<RpgCard> released = TimelineEngine.getReleasedCards(catalog, profile.currentTimelineDate);

        int hiddenFullCount = 0;
        int shownCount = 0;

        for (final RpgCard card : released) {
            int owned = profile.getOwnedCount(card.id);
            if (owned >= PackOpeningEngine.MAX_OWNED_COPIES) {
                hiddenFullCount++;
                continue;
            }
            if (collectionOnlyOwned && owned > 0) continue;
            shownCount++;

            Rarity rarity = card.getRarityEnum();
            int craftCost = catalog.craftingRules.getCraftCostDust(rarity);
            String limitBadge = banlist.getLimitBadge(card.id);
            String errataBadge = CardErrataEngine.getVersionBadge(catalog, card.id, profile.currentTimelineDate);
            final OcgCardErrata errataRec = catalog.findErrata(card.id);

            LinearLayout box = makeCardContainer();
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);

            ImageView thumb = new ImageView(this);
            LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(52), dp(76));
            thumbLp.setMarginEnd(dp(10));
            thumb.setLayoutParams(thumbLp);
            thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumb.setAlpha(owned > 0 ? 1.0f : 0.65f);
            RpgCardArtLoader.loadInto(thumb, card.id, dp(52));
            thumb.setOnClickListener(v -> RpgPackVisualDialogs.showCardDetailDialog(this, catalog, profile, card));
            box.addView(thumb);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            String header = "[" + rarity.code + "] " + card.getDisplayName()
                    + (limitBadge.isEmpty() ? "" : " 【" + limitBadge + "】")
                    + (errataBadge.isEmpty() ? "" : " ⟪" + errataBadge + "⟫")
                    + "  (" + owned + "/3)";
            TextView tvTitle = makeTitleText(header);
            tvTitle.setTextColor(Color.parseColor(rarity.colorHex));
            infoCol.addView(tvTitle);

            String stats = card.isMonster()
                    ? String.format(Locale.US, "Lv.%d %s/%s ATK %d / DEF %d (%s)",
                    card.level, card.attribute, card.race, card.atk, card.def, card.setCode)
                    : card.cardType + " (" + card.setCode + ")";
            infoCol.addView(makeSubtleText(stats));

            String effectiveDesc = CardErrataEngine.getEffectiveDescCn(catalog, card, profile.currentTimelineDate);
            if (!effectiveDesc.isEmpty()) {
                TextView tvDesc = makeSubtleText(effectiveDesc);
                if (!errataBadge.isEmpty()) {
                    tvDesc.setTextColor(Color.parseColor("#FDE68A"));
                }
                infoCol.addView(tvDesc);
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(6), 0, 0);

            Button craftBtn = makeActionButton("合成 (-" + craftCost + "尘)",
                    profile.dust >= craftCost ? "#0284C7" : "#475569");
            craftBtn.setOnClickListener(v -> {
                CraftingEngine.CraftResult r = CraftingEngine.craft(profile, catalog, card.id, 1);
                Toast.makeText(this, r.message, Toast.LENGTH_SHORT).show();
                if (r.success) {
                    saveProfileSafe();
                    refreshAll();
                }
            });
            row.addView(craftBtn);

            if (errataRec != null) {
                Button histBtn = makeActionButton("勘误史(" + errataRec.revisions.size() + ")", "#7C3AED");
                histBtn.setOnClickListener(v -> showErrataHistoryDialog(errataRec));
                row.addView(histBtn);
            }
            infoCol.addView(row);
            box.addView(infoCol);
            layoutContent.addView(box);
        }

        TextView note = makeSubtleText("抽包/购入超 3 张将自动分解为尘埃（不支持手动分解）· 已自动隐藏 "
                + hiddenFullCount + " 种满 3 张的卡片 · 待补齐 " + shownCount + " 种");
        layoutTabActions.addView(note);

        if (shownCount == 0) {
            LinearLayout empty = makeCardContainer();
            empty.addView(makeTitleText("🎉 当前时间轴全部已发售卡片均已集齐 3 张！"));
            layoutContent.addView(empty);
        }
    }

    // ==================== TAB 3: RPG DECK BUILDER ====================
    private void renderDeckTab() {
        Button nativeEditorBtn = makeActionButton("🛠️ 原版卡组编辑器", "#7C3AED");
        nativeEditorBtn.setOnClickListener(v -> launchNativeDeckEditor());
        Button autoBtn = makeActionButton("智能组卡 (40张)", "#059669");
        autoBtn.setOnClickListener(v -> {
            int built = DeckValidator.autoBuildLegalPlayerDeck(profile, catalog);
            saveProfileSafe();
            Toast.makeText(this, "已按当前时间轴与卡库自动构筑 " + built + " 张主卡组", Toast.LENGTH_SHORT).show();
            refreshAll();
        });
        Button saveDeckBtn = makeActionButton("💾 保存卡组", "#0284C7");
        saveDeckBtn.setOnClickListener(v -> showSaveDeckDialog());
        Button loadDeckBtn = makeActionButton("📂 读取卡组", "#0D9488");
        loadDeckBtn.setOnClickListener(v -> showLoadDeckDialog());
        Button clearBtn = makeActionButton("清空卡组", "#475569");
        clearBtn.setOnClickListener(v -> {
            profile.activeDeckMain.clear();
            profile.activeDeckExtra.clear();
            saveProfileSafe();
            refreshAll();
        });

        android.widget.HorizontalScrollView actionScroll = new android.widget.HorizontalScrollView(this);
        actionScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        actionScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.addView(nativeEditorBtn);
        actionRow.addView(autoBtn);
        actionRow.addView(saveDeckBtn);
        actionRow.addView(loadDeckBtn);
        actionRow.addView(clearBtn);
        actionScroll.addView(actionRow);
        layoutTabActions.addView(actionScroll);

        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        LinearLayout statusCard = makeCardContainer();
        RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
        TextView statusTitle = makeTitleText(
                "当前 RPG 卡组" + (activeSaved != null ? " [" + activeSaved.name + "]" : "")
                        + "：主卡组 " + profile.activeDeckMain.size()
                        + " 张 / 额外 " + profile.activeDeckExtra.size() + " 张 · "
                        + (val.valid ? "✔ 合规可出战" : "✘ 未通过校验"));
        statusTitle.setTextColor(Color.parseColor(val.valid ? "#34D399" : "#F87171"));
        statusCard.addView(statusTitle);
        statusCard.addView(makeSubtleText("💡 点击上方「🛠️ 原版卡组编辑器」可直接使用 YGOMobile 原版可视化卡组编辑器组卡，保存并退出后自动同步回 RPG 模式并校验合规性。"));
        if (!val.valid) {
            StringBuilder errs = new StringBuilder();
            for (String e : val.errors) {
                errs.append("• ").append(e).append("\n");
            }
            statusCard.addView(makeSubtleText(errs.toString().trim()));
        }
        layoutContent.addView(statusCard);

        // Multi-deck saved slots section
        LinearLayout savedSection = makeCardContainer();
        int savedCount = profile.savedDecks != null ? profile.savedDecks.size() : 0;
        TextView savedHeader = makeTitleText("📚 多卡组槽位管理（已保存 " + savedCount + " 套）");
        savedHeader.setTextColor(Color.parseColor("#38BDF8"));
        savedSection.addView(savedHeader);
        if (savedCount == 0) {
            savedSection.addView(makeSubtleText("暂无已保存卡组。点击上方「💾 保存卡组」可命名保存当前构筑，随时读取、覆盖或删除。"));
        } else {
            for (final RpgProfile.SavedDeck sd : profile.savedDecks) {
                if (sd == null) continue;
                boolean isCur = sd.id != null && sd.id.equals(profile.activeDeckId);
                int mSize = sd.main != null ? sd.main.size() : 0;
                int eSize = sd.extra != null ? sd.extra.size() : 0;
                TextView nameTv = makeSubtleText((isCur ? "▶ [当前] " : "• ") + sd.name
                        + "（主 " + mSize + " / 额外 " + eSize
                        + (sd.savedAtDate != null ? " · 存自 " + sd.savedAtDate : "") + "）");
                nameTv.setTextColor(Color.parseColor(isCur ? "#34D399" : "#E2E8F0"));
                savedSection.addView(nameTv);

                LinearLayout dRow = new LinearLayout(this);
                dRow.setOrientation(LinearLayout.HORIZONTAL);
                dRow.setPadding(0, dp(4), 0, dp(8));

                Button loadBtn = makeActionButton("📂 读取", "#059669");
                loadBtn.setOnClickListener(v -> {
                    if (profile.loadSavedDeck(sd.id)) {
                        saveProfileSafe();
                        Toast.makeText(this, "已读取卡组「" + sd.name + "」", Toast.LENGTH_SHORT).show();
                        refreshAll();
                    }
                });
                dRow.addView(loadBtn);

                Button overwriteBtn = makeActionButton("💾 覆盖", "#0284C7");
                overwriteBtn.setOnClickListener(v -> {
                    if (profile.overwriteSavedDeck(sd.id)) {
                        saveProfileSafe();
                        Toast.makeText(this, "已用当前构筑覆盖「" + sd.name + "」", Toast.LENGTH_SHORT).show();
                        refreshAll();
                    }
                });
                dRow.addView(overwriteBtn);

                Button delBtn = makeActionButton("🗑 删除", "#B91C1C");
                delBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("删除卡组")
                        .setMessage("确认删除已保存卡组「" + sd.name + "」？（不会清空当前编辑中的卡组）")
                        .setPositiveButton("删除", (dlg, which) -> {
                            if (profile.deleteSavedDeck(sd.id)) {
                                saveProfileSafe();
                                Toast.makeText(this, "已删除卡组「" + sd.name + "」", Toast.LENGTH_SHORT).show();
                                refreshAll();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show());
                dRow.addView(delBtn);

                savedSection.addView(dRow);
            }
        }
        layoutContent.addView(savedSection);

        Map<Integer, Integer> inDeckCounts = countDeckCards();
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        List<RpgCard> released = TimelineEngine.getReleasedCards(catalog, profile.currentTimelineDate);

        for (final RpgCard card : released) {
            int owned = profile.getOwnedCount(card.id);
            int inDeck = inDeckCounts.getOrDefault(card.id, 0);
            if (owned <= 0 && inDeck <= 0) continue;

            int legalMax = Math.min(owned, banlist.getMaxCopies(card.id));
            LinearLayout box = makeCardContainer();
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);

            ImageView thumb = new ImageView(this);
            LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(52), dp(76));
            thumbLp.setMarginEnd(dp(10));
            thumb.setLayoutParams(thumbLp);
            thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            RpgCardArtLoader.loadInto(thumb, card.id, dp(52));
            thumb.setOnClickListener(v -> RpgPackVisualDialogs.showCardDetailDialog(this, catalog, profile, card));
            box.addView(thumb);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            String badge = banlist.getLimitBadge(card.id);
            String errataBadge = CardErrataEngine.getVersionBadge(catalog, card.id, profile.currentTimelineDate);
            TextView tv = makeTitleText("[" + card.rarity + "] " + card.getDisplayName()
                    + (badge.isEmpty() ? "" : " 【" + badge + "】")
                    + (errataBadge.isEmpty() ? "" : " ⟪" + errataBadge + "⟫"));
            tv.setTextColor(Color.parseColor(card.getRarityEnum().colorHex));
            infoCol.addView(tv);
            infoCol.addView(makeSubtleText("已投入 " + inDeck + " / 上限 " + legalMax + "（拥有 " + owned + " 张）"));
            if (!errataBadge.isEmpty()) {
                TextView tvErrDesc = makeSubtleText(CardErrataEngine.getEffectiveDescCn(catalog, card, profile.currentTimelineDate));
                tvErrDesc.setTextColor(Color.parseColor("#FDE68A"));
                infoCol.addView(tvErrDesc);
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(6), 0, 0);

            Button addBtn = makeActionButton("+ 投入", "#0284C7");
            addBtn.setEnabled(inDeck < legalMax);
            addBtn.setOnClickListener(v -> {
                if (card.isExtraDeckCard()) {
                    if (profile.activeDeckExtra.size() < DeckValidator.MAX_EXTRA_DECK) {
                        profile.activeDeckExtra.add(card.id);
                    }
                } else {
                    if (profile.activeDeckMain.size() < DeckValidator.MAX_MAIN_DECK) {
                        profile.activeDeckMain.add(card.id);
                    }
                }
                saveProfileSafe();
                refreshAll();
            });

            Button rmBtn = makeActionButton("- 移出", "#64748B");
            rmBtn.setEnabled(inDeck > 0);
            rmBtn.setOnClickListener(v -> {
                if (!profile.activeDeckMain.remove(Integer.valueOf(card.id))) {
                    profile.activeDeckExtra.remove(Integer.valueOf(card.id));
                }
                saveProfileSafe();
                refreshAll();
            });

            row.addView(addBtn);
            row.addView(rmBtn);
            infoCol.addView(row);
            box.addView(infoCol);
            layoutContent.addView(box);
        }
    }

    private void launchNativeDeckEditor() {
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未连接到 YGOMobile 主程序，请从游戏主菜单进入 RPG 模式", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            File playerYdk = DeckValidator.exportToYdk(
                    repository.getPlayerDeckYdkFile(), profile.activeDeckMain, profile.activeDeckExtra);
            OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
            Map<Integer, Integer> allowedCaps = new HashMap<>();
            for (RpgCard c : TimelineEngine.getReleasedCards(catalog, profile.currentTimelineDate)) {
                int owned = profile.getOwnedCount(c.id);
                if (owned <= 0) continue;
                int cap = Math.min(owned, banlist.getMaxCopies(c.id));
                if (cap > 0) {
                    allowedCaps.put(c.id, cap);
                }
            }
            bridge.openNativeDeckEditor(this, playerYdk.getAbsolutePath(), allowedCaps);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "导出卡组失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSaveDeckDialog() {
        final EditText input = new EditText(this);
        RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
        int nextIdx = (profile.savedDecks != null ? profile.savedDecks.size() : 0) + 1;
        input.setHint("例如：青眼强攻 / 封印大法师（同名将覆盖）");
        input.setText(activeSaved != null ? activeSaved.name : ("我的卡组 " + nextIdx));
        input.setSelection(input.getText().length());
        int pad = dp(16);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, dp(8), pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("💾 保存当前卡组（主 " + profile.activeDeckMain.size() + " / 额外 " + profile.activeDeckExtra.size() + "）")
                .setView(container)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    RpgProfile.SavedDeck saved = profile.saveCurrentDeck(name);
                    saveProfileSafe();
                    Toast.makeText(this, "已保存卡组「" + saved.name + "」", Toast.LENGTH_SHORT).show();
                    refreshAll();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLoadDeckDialog() {
        List<RpgProfile.SavedDeck> savedList = profile.savedDecks;
        if (savedList == null || savedList.isEmpty()) {
            Toast.makeText(this, "暂无已保存卡组，请先点击「💾 保存卡组」", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[savedList.size()];
        for (int i = 0; i < savedList.size(); i++) {
            RpgProfile.SavedDeck sd = savedList.get(i);
            boolean isCur = sd.id != null && sd.id.equals(profile.activeDeckId);
            int mSize = sd.main != null ? sd.main.size() : 0;
            int eSize = sd.extra != null ? sd.extra.size() : 0;
            labels[i] = (isCur ? "▶ [当前] " : "") + sd.name
                    + "（主 " + mSize + " / 额外 " + eSize
                    + (sd.savedAtDate != null ? " · " + sd.savedAtDate : "") + "）";
        }
        new AlertDialog.Builder(this)
                .setTitle("📂 读取已保存卡组（共 " + savedList.size() + " 套）")
                .setItems(labels, (dialog, which) -> {
                    RpgProfile.SavedDeck sd = savedList.get(which);
                    if (sd != null && profile.loadSavedDeck(sd.id)) {
                        saveProfileSafe();
                        Toast.makeText(this, "已读取卡组「" + sd.name + "」", Toast.LENGTH_SHORT).show();
                        refreshAll();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== TAB 4: AI DUEL ====================
    private void renderAiDuelTab() {
        List<AiOpponent> opponents = TimelineEngine.getAvailableOpponents(
                AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        layoutTabActions.addView(makeSubtleText("战绩：" + profile.wins + " 胜 " + profile.losses + " 负 " + profile.draws
                + " 平 · 当前时间轴已解锁 " + opponents.size() + " 位对手（随时间轴自动切换对应年代卡组）"));

        for (final AiOpponent opp : opponents) {
            AiOpponent.EraDeckProfile activeDeck = opp.getActiveDeckProfile(profile.currentTimelineDate);
            LinearLayout box = makeCardContainer();

            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            ImageView avatarView = new ImageView(this);
            LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(68), dp(68));
            avLp.setMarginEnd(dp(10));
            avatarView.setLayoutParams(avLp);
            avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatarView.setPadding(dp(2), dp(2), dp(2), dp(2));
            avatarView.setBackground(RpgCardArtLoader.createAvatarPlaceholderDrawable());
            RpgCardArtLoader.loadOpponentAvatarInto(this, avatarView, opp.id);
            headerRow.addView(avatarView);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            infoCol.addView(makeTitleText(opp.name + "  (" + opp.title + ")"));
            if (activeDeck != null) {
                TextView deckTv = makeSubtleText("当前自动使用卡组：" + activeDeck.deckName + " [" + activeDeck.eraTag + "]");
                deckTv.setTextColor(Color.parseColor("#38BDF8"));
                infoCol.addView(deckTv);
                infoCol.addView(makeSubtleText(activeDeck.description));
            } else {
                infoCol.addView(makeSubtleText(opp.strategyHint));
            }
            headerRow.addView(infoCol);
            box.addView(headerRow);

            TextView rewardTv = makeSubtleText("奖励：胜 +" + opp.winRewardTokens + " / 平 +" + opp.drawRewardTokens + " / 负 +" + opp.lossRewardTokens + " 代币");
            rewardTv.setPadding(0, dp(6), 0, 0);
            box.addView(rewardTv);

            RpgProfile.RecentMatchStats rs = profile.getRecentStats(profile.currentTimelineDate, opp.id);
            TextView wrTv;
            if (rs.total == 0) {
                wrTv = makeSubtleText("当前时间点近20局胜率：暂无交手记录 (0/20)");
                wrTv.setTextColor(Color.parseColor("#94A3B8"));
            } else {
                String drawPart = rs.draws > 0 ? (" " + rs.draws + "平") : "";
                wrTv = makeSubtleText(String.format(Locale.US,
                        "当前时间点近20局胜率：%.1f%%（%d胜 %d负%s / 共%d局）",
                        rs.winRatePercent, rs.wins, rs.losses, drawPart, rs.total));
                wrTv.setTextColor(Color.parseColor(
                        rs.winRatePercent >= 60.0 ? "#34D399"
                                : (rs.winRatePercent >= 40.0 ? "#FBBF24" : "#F87171")));
            }
            box.addView(wrTv);

            Button fightBtn = makeActionButton("🪙 亲自对战", "#DC2626");
            fightBtn.setOnClickListener(v -> launchAiDuel(opp));

            Button quickBtn = makeActionButton("⚡ AI托管快战", "#D97706");
            quickBtn.setOnClickListener(v -> runQuickDuel(opp));

            Button spectateBtn = makeActionButton("👁️ 3D观战", "#7C3AED");
            spectateBtn.setOnClickListener(v -> launchAiSpectateDuel(opp));

            box.addView(makeEqualWeightButtonRow(8, fightBtn, quickBtn, spectateBtn));

            Button viewDeckBtn = makeActionButton("🃏 查看卡组", "#0284C7");
            viewDeckBtn.setOnClickListener(v -> openAiDeckReadonlyViewer(opp));

            if (opp.deckEvolutions != null && opp.deckEvolutions.size() > 1) {
                Button evoBtn = makeActionButton("📜 演变史(" + opp.deckEvolutions.size() + "套)", "#475569");
                evoBtn.setOnClickListener(v -> showAiEvolutionDialog(opp));
                box.addView(makeEqualWeightButtonRow(6, viewDeckBtn, evoBtn));
            } else {
                box.addView(makeEqualWeightButtonRow(6, viewDeckBtn));
            }
            layoutContent.addView(box);
        }
    }

    private void launchAiSpectateDuel(AiOpponent opponent) {
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        if (!val.valid) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 卡组未通过校验")
                    .setMessage(android.text.TextUtils.join("\n", val.errors))
                    .setPositiveButton("去组卡", (d, w) -> {
                        TabLayout.Tab t = tabLayout.getTabAt(TAB_DECK);
                        if (t != null) t.select();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未连接到 YGOMobile 主程序，请从游戏主菜单进入 RPG 模式", Toast.LENGTH_LONG).show();
            return;
        }
        OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        AiDeckGenerator.GeneratedAiDeck aiDeck = AiDeckGenerator.generateDeck(
                catalog, opponent, profile.currentTimelineDate, new Random());
        String playerStyle = RpgAiStrategy.inferStyleFromDeck(
                profile.activeDeckMain, profile.activeDeckExtra, activeRule);
        String aiStyle = (aiDeck.appliedProfile != null && aiDeck.appliedProfile.fillStyle != null)
                ? aiDeck.appliedProfile.fillStyle
                : RpgAiStrategy.inferStyleFromDeck(aiDeck.main, aiDeck.extra, activeRule);
        bridge.startRpgAiSpectateDuel(
                this,
                "我方AI(" + RpgAiStrategy.getStyleDisplayName(playerStyle) + ")",
                profile.activeDeckMain, profile.activeDeckExtra, playerStyle,
                opponent.name, aiDeck.main, aiDeck.extra, aiStyle,
                activeRule, profile.currentTimelineDate, TAB_AI_DUEL);
        finish();
    }

    private void openAiDeckReadonlyViewer(AiOpponent opponent) {
        long stableSeed = (profile.currentTimelineDate + "|" + opponent.id).hashCode();
        AiDeckGenerator.GeneratedAiDeck aiDeck = AiDeckGenerator.generateDeck(
                catalog, opponent, profile.currentTimelineDate, new Random(stableSeed));
        RpgPackVisualDialogs.showAiDeckVisualDialog(this, catalog, profile, opponent, aiDeck);
    }

    private void showAiEvolutionDialog(AiOpponent opp) {
        AiOpponent.EraDeckProfile active = opp.getActiveDeckProfile(profile.currentTimelineDate);
        StringBuilder sb = new StringBuilder();
        sb.append("当前时间轴：").append(profile.currentTimelineDate).append("\n\n");
        for (AiOpponent.EraDeckProfile p : opp.deckEvolutions) {
            boolean isCur = active != null && active.effectiveDate.equals(p.effectiveDate);
            boolean unlocked = TimelineEngine.isReleasedOnOrBefore(p.effectiveDate, profile.currentTimelineDate);
            sb.append(isCur ? "▶ [当前使用] " : (unlocked ? "• [历史旧版] " : "🔒 [未来版本] "))
                    .append(p.effectiveDate).append(" ").append(p.deckName).append("\n")
                    .append("  ").append(p.description).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("AI 卡组演变史 · " + opp.name)
                .setMessage(sb.toString().trim())
                .setPositiveButton("知道了", null)
                .show();
    }

    // ==================== TAB 5: OCG TIMELINE TOURNAMENT ====================
    private void renderTournamentTab() {
        List<AiOpponent> unlocked = TimelineEngine.getAvailableOpponents(
                AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        layoutTabActions.addView(makeSubtleText("🏆 锦标赛战绩：" + profile.tournamentWins + " 冠 / "
                + profile.tournamentsPlayed + " 参赛 · 当前时间点开放 " + unlocked.size() + " 名 AI 对手"));

        TournamentEngine.TournamentBracket bracket = RpgSessionManager.getActiveTournament();
        if (bracket == null || !bracket.timelineDate.equals(profile.currentTimelineDate)) {
            LinearLayout startBox = makeCardContainer();
            startBox.addView(makeTitleText("随机抽签淘汰锦标赛（当前环境：" + profile.currentTimelineDate + "）"));
            startBox.addView(makeSubtleText("你将与当前时间轴已解锁的 AI 对手完全随机分组对战，所有选手均严格遵守当前时间轴的规则、禁限表与勘误版本！"));
            startBox.addView(makeSubtleText("• 4 人卡牌屋店赛（2 轮）：冠军 +600 / 亚军 +250 / 参与 +100 代币\n"
                    + "• 8 人地区锦标赛（3 轮）：冠军 +1500 / 亚军 +650 / 四强 +300 / 参与 +120 代币"));
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, dp(10), 0, 0);
            Button b4 = makeActionButton("🏅 开启 4 人店赛", "#059669");
            b4.setOnClickListener(v -> startNewTournament(4));
            btnRow.addView(b4);
            Button b8 = makeActionButton("🏆 开启 8 人锦标赛", "#D97706");
            b8.setOnClickListener(v -> startNewTournament(8));
            btnRow.addView(b8);
            startBox.addView(btnRow);
            layoutContent.addView(startBox);
            return;
        }

        // Active / finished tournament header
        LinearLayout head = makeCardContainer();
        head.addView(makeTitleText(bracket.tournamentName + (bracket.finished ? " — 已完赛" : " — 进行中")));
        if (bracket.finished) {
            TextView champTv = makeSubtleText("👑 本届冠军：" + (bracket.champion != null ? bracket.champion.displayName : "未知")
                    + " · 你的成绩：" + bracket.playerPlacementLabel + "（奖金 +" + bracket.rewardTokens + " 代币）");
            champTv.setTextColor(Color.parseColor("#FBBF24"));
            head.addView(champTv);
        } else if (bracket.playerEliminated) {
            head.addView(makeSubtleText("你已止步本轮，可点击下方按钮模拟剩余 AI 淘汰赛决出本届冠军并领取奖金。"));
        } else {
            TournamentEngine.Match pm = bracket.findPendingPlayerMatch();
            if (pm != null && pm.getPlayerOpponent() != null) {
                TournamentEngine.Participant opp = pm.getPlayerOpponent();
                TextView nextTv = makeSubtleText("▶ 你的下一场（" + bracket.getRoundTitle(bracket.currentRound)
                        + "）：vs " + opp.displayName + " " + opp.activeDeckName);
                nextTv.setTextColor(Color.parseColor("#38BDF8"));
                head.addView(nextTv);
            }
        }

        Button bReset = makeActionButton(bracket.finished ? "开启新一届" : "放弃本届", "#475569");
        bReset.setOnClickListener(v -> {
            RpgSessionManager.setActiveTournament(null);
            renderCurrentTab();
        });

        TournamentEngine.Match pendingPlayer = bracket.findPendingPlayerMatch();
        if (pendingPlayer != null) {
            Button bManual = makeActionButton("🪙 亲自手打参赛", "#DC2626");
            bManual.setOnClickListener(v -> launchTournamentMatchManual(pendingPlayer));

            Button bQuick = makeActionButton("⚡ AI托管参赛", "#D97706");
            bQuick.setOnClickListener(v -> playTournamentMatchQuick(bracket, pendingPlayer));

            head.addView(makeEqualWeightButtonRow(8, bManual, bQuick));
            head.addView(makeEqualWeightButtonRow(6, bReset));
        } else if (!bracket.finished && bracket.playerEliminated) {
            Button bSimRest = makeActionButton("⏭ 真引擎决出本届冠军", "#2563EB");
            bSimRest.setOnClickListener(v -> {
                AlertDialog waitDlg = new AlertDialog.Builder(this)
                        .setTitle("正在运行真实 ocgcore 锦标赛对决…")
                        .setMessage("双方均由专属 OCG AI 策略自动对战，请稍候…")
                        .setCancelable(false)
                        .show();
                new Thread(() -> {
                    TournamentEngine.simulateRemainingToChampion(catalog, bracket, new Random());
                    runOnUiThread(() -> {
                        if (waitDlg.isShowing()) waitDlg.dismiss();
                        int prize = TournamentEngine.claimRewardIfFinished(profile, bracket);
                        if (prize > 0) saveProfileSafe();
                        refreshAll();
                        Toast.makeText(this, "冠军诞生：" + (bracket.champion != null ? bracket.champion.displayName : "")
                                + "！已发放 +" + prize + " 代币", Toast.LENGTH_LONG).show();
                    });
                }, "RpgTournamentSim").start();
            });
            head.addView(makeEqualWeightButtonRow(8, bSimRest, bReset));
        } else {
            head.addView(makeEqualWeightButtonRow(8, bReset));
        }
        layoutContent.addView(head);

        // Render every round & match card
        for (int r = 0; r < bracket.rounds.size(); r++) {
            TextView rHeader = makeTitleText("—— " + bracket.getRoundTitle(r) + " ——");
            rHeader.setTextColor(Color.parseColor("#F59E0B"));
            rHeader.setPadding(0, dp(6), 0, dp(4));
            layoutContent.addView(rHeader);

            for (final TournamentEngine.Match m : bracket.rounds.get(r)) {
                LinearLayout mBox = makeCardContainer();
                String p1Label = (m.winner == m.p1 ? "✅ " : "") + m.p1.displayName
                        + (m.p1.isPlayer ? "" : " " + m.p1.activeDeckName);
                String p2Label = (m.winner == m.p2 ? "✅ " : "") + m.p2.displayName
                        + (m.p2.isPlayer ? "" : " " + m.p2.activeDeckName);
                mBox.addView(makeTitleText(p1Label + "  VS  " + p2Label));
                if (m.finished) {
                    TextView sumTv = makeSubtleText("结果：" + m.summary);
                    sumTv.setTextColor(Color.parseColor("#34D399"));
                    mBox.addView(sumTv);
                    if (m.logs != null && !m.logs.isEmpty()) {
                        Button replayBtn = null;
                        if (m.replayReport != null && m.replayReport.replayFilePath != null) {
                            replayBtn = makeActionButton("🎬 3D回放", "#0284C7");
                            replayBtn.setOnClickListener(v -> launchNative3dReplay(m.replayReport, TAB_TOURNAMENT));
                        }
                        Button logBtn = makeActionButton("📜 文字战报", "#334155");
                        logBtn.setOnClickListener(v -> showTournamentLogDialog(m));
                        mBox.addView(makeEqualWeightButtonRow(6, replayBtn, logBtn));
                    }
                } else {
                    mBox.addView(makeSubtleText(m.involvesPlayer() ? "⏳ 等待你的对局结果（同轮 AI 比赛将同步结算）" : "⏳ 同轮次 AI 对阵，待本轮推进"));
                }
                layoutContent.addView(mBox);
            }
        }
    }

    private void startNewTournament(int size) {
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        if (!val.valid) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 卡组未通过校验")
                    .setMessage(android.text.TextUtils.join("\n", val.errors))
                    .setPositiveButton("去组卡", (d, w) -> {
                        TabLayout.Tab t = tabLayout.getTabAt(3);
                        if (t != null) t.select();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        TournamentEngine.TournamentBracket bracket = TournamentEngine.createBracket(
                catalog, profile, size, new Random());
        RpgSessionManager.setActiveTournament(bracket);
        renderCurrentTab();
        Toast.makeText(this, "已随机抽签生成 " + size + " 人锦标赛对阵表！", Toast.LENGTH_SHORT).show();
    }

    private void playTournamentMatchQuick(TournamentEngine.TournamentBracket bracket,
                                          TournamentEngine.Match match) {
        final TournamentEngine.Participant opp = match.getPlayerOpponent();
        if (opp == null) return;
        final RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未注册 YGOMobile 决斗引擎桥接器", Toast.LENGTH_LONG).show();
            return;
        }
        final OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, bracket.timelineDate);
        final String playerStyle = RpgAiStrategy.inferStyleFromDeck(
                profile.activeDeckMain, profile.activeDeckExtra, activeRule);
        final String oppStyle = (opp.fillStyle != null && !opp.fillStyle.isEmpty())
                ? opp.fillStyle
                : RpgAiStrategy.inferStyleFromDeck(opp.deckMain, opp.deckExtra, activeRule);

        final AlertDialog waitDlg = new AlertDialog.Builder(this)
                .setTitle("⚡ 真实 ocgcore 锦标赛托管对战中…")
                .setMessage("我方策略：" + RpgAiStrategy.getStyleDisplayName(playerStyle)
                        + "\n对手策略：" + RpgAiStrategy.getStyleDisplayName(oppStyle))
                .setCancelable(false)
                .show();
        new Thread(() -> {
            Random rng = new Random();
            final RpgAiAutoDuelModels.DuelResult report = bridge.runHeadlessOcgDuel(
                    catalog,
                    "你", profile.activeDeckMain, profile.activeDeckExtra, playerStyle,
                    opp.displayName, opp.deckMain, opp.deckExtra, oppStyle,
                    activeRule, bracket.timelineDate, rng.nextBoolean(), true, rng);
            if (report == null) {
                runOnUiThread(() -> {
                    if (waitDlg.isShowing()) waitDlg.dismiss();
                    Toast.makeText(this, "ocgcore 引擎未就绪，请稍后再试", Toast.LENGTH_LONG).show();
                });
                return;
            }
            boolean pWin = !report.isDraw ? report.sideAWon : (report.sideALp >= report.sideBLp);
            String summary = (pWin ? "你胜" : opp.displayName + "胜")
                    + " · T" + report.turns + " · LP " + report.sideALp + ":" + report.sideBLp;
            TournamentEngine.recordPlayerMatchResult(catalog, bracket, pWin, summary, report.logs, report, rng);
            runOnUiThread(() -> {
                if (waitDlg.isShowing()) waitDlg.dismiss();
                if (opp.opponent != null) {
                    DuelRewardEngine.settle(profile, opp.opponent, pWin, false);
                }
                int prize = TournamentEngine.claimRewardIfFinished(profile, bracket);
                saveProfileSafe();
                refreshAll();

                String msg = "本场对阵：你（" + RpgAiStrategy.getStyleDisplayName(playerStyle)
                        + "） vs " + opp.displayName + " " + opp.activeDeckName + "\n"
                        + "结果：" + summary + "\n"
                        + (bracket.finished
                        ? ("\n🏆 锦标赛结束！" + bracket.playerPlacementLabel + " · 额外奖金 +" + prize + " 代币")
                        : (pWin ? "\n✅ 你已晋级下一轮！同轮 AI 比赛已按真引擎同步结算。" : "\n❌ 你止步本轮，可点击真引擎决出冠军并领奖。"));
                new AlertDialog.Builder(this)
                        .setTitle("🏆 锦标赛赛果 · " + bracket.getRoundTitle(match.roundIndex))
                        .setMessage(msg)
                        .setNeutralButton("🎬 3D回放本局", (d, w) -> launchNative3dReplay(report, TAB_TOURNAMENT))
                        .setPositiveButton("查看对阵表", null)
                        .show();
            });
        }, "RpgTourneyQuickMatch").start();
    }

    private void launchTournamentMatchManual(TournamentEngine.Match match) {
        TournamentEngine.Participant opp = match.getPlayerOpponent();
        if (opp == null || opp.opponent == null) return;
        OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        try {
            File playerYdk = DeckValidator.exportToYdk(
                    repository.getPlayerDeckYdkFile(), profile.activeDeckMain, profile.activeDeckExtra);
            File aiYdk = DeckValidator.exportToYdk(
                    repository.getAiDeckYdkFile(opp.opponent.id), opp.deckMain, opp.deckExtra);
            RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
            if (bridge == null) {
                Toast.makeText(this, "未注册 YGOMobile 决斗桥接器", Toast.LENGTH_LONG).show();
                return;
            }
            RpgSessionManager.beginRpgDuel(opp.opponent, true);
            bridge.startRpgAiDuel(this, opp.opponent,
                    playerYdk.getAbsolutePath(), aiYdk.getAbsolutePath(),
                    banlist, activeRule, profile.currentTimelineDate);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "导出卡组失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showTournamentLogDialog(TournamentEngine.Match m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.p1.displayName).append(" vs ").append(m.p2.displayName).append("\n")
                .append("结果：").append(m.summary).append("\n\n");
        int limit = Math.min(m.logs.size(), 25);
        for (int i = 0; i < limit; i++) {
            sb.append(m.logs.get(i)).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("📜 锦标赛战报")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    // ==================== TAB 6: OFFICIAL OCG ERRATA ARCHIVE ====================
    private void renderErrataTab() {
        String curDate = profile.currentTimelineDate;
        java.util.TreeMap<String, List<TimelineEngine.ErrataChange>> groupsByDate =
                new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        int totalChanges = 0;

        for (OcgCardErrata rec : catalog.getErrataRecords()) {
            if (rec == null || rec.revisions == null || rec.revisions.size() < 2) continue;
            for (int i = 1; i < rec.revisions.size(); i++) {
                OcgCardErrata.Revision rev = rec.revisions.get(i);
                if (rev == null || rev.versionIndex < 2 || rev.effectiveDate == null) continue;
                if (rev.effectiveDate.compareTo(curDate) <= 0) {
                    List<TimelineEngine.ErrataChange> list = groupsByDate.get(rev.effectiveDate);
                    if (list == null) {
                        list = new ArrayList<>();
                        groupsByDate.put(rev.effectiveDate, list);
                    }
                    list.add(new TimelineEngine.ErrataChange(rec, rev, rec.revisions.get(i - 1)));
                    totalChanges++;
                }
            }
        }

        if (groupsByDate.isEmpty()) {
            layoutTabActions.addView(makeSubtleText(
                    "📜 截至 " + curDate + " 尚未发生官方卡片效果勘误"));
            LinearLayout emptyCard = makeCardContainer();
            emptyCard.addView(makeTitleText("📅 截至 " + curDate + " 暂无已生效的效果勘误"));
            emptyCard.addView(makeSubtleText(
                    "本页仅展示当前时间点及之前已实施的官方效果勘误（按日期降序分组，不剧透未来版本）。\n"
                            + "将时间轴推进至 1999-05-05（地割/落穴/光之护封剑修正）或 1999-12-20（三眼怪初次勘误）后即可查看对应勘误记录。"));
            layoutContent.addView(emptyCard);
            return;
        }

        layoutTabActions.addView(makeSubtleText(
                "📜 截至 " + curDate + " 共 " + groupsByDate.size() + " 个勘误日期 · "
                        + totalChanges + " 条效果变更（按日期降序）"));

        for (Map.Entry<String, List<TimelineEngine.ErrataChange>> entry : groupsByDate.entrySet()) {
            String date = entry.getKey();
            List<TimelineEngine.ErrataChange> items = entry.getValue();

            LinearLayout dateCard = makeCardContainer();
            TextView tvDateHeader = makeTitleText("📅 " + date + "  ·  " + items.size() + " 张卡片效果勘误");
            tvDateHeader.setTextColor(Color.parseColor("#FBBF24"));
            tvDateHeader.setTextSize(15f);
            dateCard.addView(tvDateHeader);

            for (final TimelineEngine.ErrataChange ch : items) {
                View divider = new View(this);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                dlp.setMargins(0, dp(8), 0, dp(8));
                divider.setLayoutParams(dlp);
                divider.setBackgroundColor(Color.parseColor("#334155"));
                dateCard.addView(divider);

                LinearLayout itemHeader = new LinearLayout(this);
                itemHeader.setOrientation(LinearLayout.HORIZONTAL);
                itemHeader.setGravity(Gravity.CENTER_VERTICAL);

                ImageView ivArt = new ImageView(this);
                LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(44), dp(64));
                artLp.setMarginEnd(dp(10));
                ivArt.setLayoutParams(artLp);
                ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                RpgCardArtLoader.loadInto(ivArt, ch.errata.cardId, dp(44));
                final RpgCard catalogCard = catalog.findCard(ch.errata.cardId);
                if (catalogCard != null) {
                    ivArt.setOnClickListener(v ->
                            RpgPackVisualDialogs.showCardDetailDialog(this, catalog, profile, catalogCard));
                }
                itemHeader.addView(ivArt);

                LinearLayout infoCol = new LinearLayout(this);
                infoCol.setOrientation(LinearLayout.VERTICAL);
                infoCol.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                String verRange = (ch.previousRevision != null
                        ? ("v" + ch.previousRevision.versionIndex + " → ") : "")
                        + "v" + ch.revision.versionIndex;
                TextView tvCardTitle = makeTitleText(
                        ch.errata.getDisplayName() + "  (" + ch.errata.nameJp + ")  [" + verRange + "]");
                tvCardTitle.setTextColor(Color.parseColor("#F8FAFC"));
                infoCol.addView(tvCardTitle);

                infoCol.addView(makeSubtleText(
                        ch.errata.getCategoryLabelCn()
                                + (ch.revision.sourceLabel != null && !ch.revision.sourceLabel.isEmpty()
                                ? " · " + ch.revision.sourceLabel : "")));

                if (ch.revision.summary != null && !ch.revision.summary.isEmpty()) {
                    TextView tvSum = makeSubtleText("变更要点：" + ch.revision.summary);
                    tvSum.setTextColor(Color.parseColor("#38BDF8"));
                    infoCol.addView(tvSum);
                }
                itemHeader.addView(infoCol);
                dateCard.addView(itemHeader);

                if (ch.previousRevision != null && ch.previousRevision.descCn != null
                        && !ch.previousRevision.descCn.isEmpty()) {
                    TextView tvBefore = makeSubtleText(
                            "修改前 (v" + ch.previousRevision.versionIndex + " · "
                                    + ch.previousRevision.effectiveDate + ")：" + ch.previousRevision.descCn);
                    tvBefore.setTextColor(Color.parseColor("#94A3B8"));
                    tvBefore.setPadding(0, dp(4), 0, 0);
                    dateCard.addView(tvBefore);
                }

                if (ch.revision.descCn != null && !ch.revision.descCn.isEmpty()) {
                    TextView tvAfter = makeSubtleText(
                            "修改后 (v" + ch.revision.versionIndex + " · "
                                    + ch.revision.effectiveDate + ")：" + ch.revision.descCn);
                    tvAfter.setTextColor(Color.parseColor("#E2E8F0"));
                    tvAfter.setPadding(0, dp(3), 0, 0);
                    dateCard.addView(tvAfter);
                }

                Button detailBtn = makeActionButton("查看该卡历史版本", "#334155");
                detailBtn.setOnClickListener(v -> showErrataHistoryDialog(ch.errata));
                dateCard.addView(makeEqualWeightButtonRow(6, detailBtn));
            }

            layoutContent.addView(dateCard);
        }
    }

    private void showErrataHistoryDialog(OcgCardErrata rec) {
        String curDate = profile.currentTimelineDate;
        OcgCardErrata.Revision active = CardErrataEngine.getActiveRevision(rec, curDate);
        List<OcgCardErrata.Revision> visibleRevs = new ArrayList<>();
        if (rec.revisions != null) {
            for (OcgCardErrata.Revision rev : rec.revisions) {
                if (rev.effectiveDate != null && rev.effectiveDate.compareTo(curDate) <= 0) {
                    visibleRevs.add(rev);
                }
            }
            if (visibleRevs.isEmpty() && !rec.revisions.isEmpty()) {
                visibleRevs.add(rec.revisions.get(0));
            }
        }
        // Descending chronological order (newest revision first)
        java.util.Collections.reverse(visibleRevs);

        StringBuilder sb = new StringBuilder();
        sb.append("日文名：").append(rec.nameJp).append("\n")
                .append("分类：").append(rec.era).append(" · ").append(rec.getCategoryLabelCn()).append("\n")
                .append("当前时间轴：").append(curDate).append("（仅显示截至当前时间点的版本）\n\n");
        for (OcgCardErrata.Revision rev : visibleRevs) {
            boolean isCurrent = active != null && rev.versionIndex == active.versionIndex;
            sb.append(isCurrent ? "▶ [当前生效] " : "• ")
                    .append("v").append(rev.versionIndex).append(" (").append(rev.effectiveDate).append(") ")
                    .append(rev.sourceLabel).append("\n")
                    .append("  变更要点：").append(rev.summary).append("\n")
                    .append("  效果文本：").append(rev.descCn).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("官方勘误档案 · " + rec.getDisplayName())
                .setMessage(sb.toString().trim())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void runQuickDuel(final AiOpponent opponent) {
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        if (!val.valid) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 卡组未通过校验")
                    .setMessage(android.text.TextUtils.join("\n", val.errors))
                    .setPositiveButton("去组卡", (d, w) -> {
                        TabLayout.Tab t = tabLayout.getTabAt(3);
                        if (t != null) t.select();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        final RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未注册 YGOMobile 决斗引擎桥接器", Toast.LENGTH_LONG).show();
            return;
        }
        final OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        final AiDeckGenerator.GeneratedAiDeck aiDeck = AiDeckGenerator.generateDeck(
                catalog, opponent, profile.currentTimelineDate, new Random());
        final String playerStyle = RpgAiStrategy.inferStyleFromDeck(
                profile.activeDeckMain, profile.activeDeckExtra, activeRule);
        final String aiStyle = (aiDeck.appliedProfile != null && aiDeck.appliedProfile.fillStyle != null)
                ? aiDeck.appliedProfile.fillStyle
                : RpgAiStrategy.inferStyleFromDeck(aiDeck.main, aiDeck.extra, activeRule);

        final AlertDialog waitDlg = new AlertDialog.Builder(this)
                .setTitle("⚡ 真实 ocgcore AI 托管快战中…")
                .setMessage("我方自选卡组 AI：" + RpgAiStrategy.getStyleDisplayName(playerStyle)
                        + "\n对手卡组 AI：" + RpgAiStrategy.getStyleDisplayName(aiStyle))
                .setCancelable(false)
                .show();

        new Thread(() -> {
            Random rng = new Random();
            final RpgAiAutoDuelModels.DuelResult report = bridge.runHeadlessOcgDuel(
                    catalog,
                    "你", profile.activeDeckMain, profile.activeDeckExtra, playerStyle,
                    opponent.name, aiDeck.main, aiDeck.extra, aiStyle,
                    activeRule, profile.currentTimelineDate, rng.nextBoolean(), true, rng);
            runOnUiThread(() -> {
                if (waitDlg.isShowing()) waitDlg.dismiss();
                if (report == null) {
                    Toast.makeText(this, "ocgcore 引擎未就绪，请稍后再试", Toast.LENGTH_LONG).show();
                    return;
                }
                DuelRewardEngine.RewardSettlement settlement =
                        DuelRewardEngine.settle(profile, opponent, report.sideAWon, report.isDraw);
                saveProfileSafe();
                refreshAll();

                StringBuilder sb = new StringBuilder();
                sb.append("引擎：真实 ocgcore + 专属 OCG AI\n")
                        .append("我方 AI 策略：").append(RpgAiStrategy.getStyleDisplayName(report.sideAStyle)).append("\n")
                        .append("对手 AI 策略：").append(RpgAiStrategy.getStyleDisplayName(report.sideBStyle)).append("\n")
                        .append("规则：").append(report.appliedRule != null ? report.appliedRule.name : "").append("\n")
                        .append("结果：").append(settlement.outcomeLabel)
                        .append("（我方 LP ").append(report.sideALp)
                        .append(" vs 对手 LP ").append(report.sideBLp)
                        .append("，共 ").append(report.turns).append(" 回合）\n")
                        .append("获得代币：+").append(settlement.tokensEarned).append("\n\n")
                        .append("--- 真实 ocgcore 战报摘录 ---\n");
                int limit = Math.min(report.logs.size(), 18);
                for (int i = 0; i < limit; i++) {
                    sb.append(report.logs.get(i)).append("\n");
                }
                new AlertDialog.Builder(this)
                        .setTitle("⚡ AI 托管快战结算 · " + settlement.outcomeLabel)
                        .setMessage(sb.toString())
                        .setNeutralButton("🎬 3D回放本局", (d, w) -> launchNative3dReplay(report, TAB_AI_DUEL))
                        .setPositiveButton("收下代币", null)
                        .show();
            });
        }, "RpgQuickDuelOcg").start();
    }

    /**
     * Launches YGOMobile's real 3D duel replay screen to play back the recorded V2 .yrp file
     * generated by the real ocgcore engine, then returns to {@code returnTab} in RPG Mode.
     */
    private void launchNative3dReplay(final RpgAiAutoDuelModels.DuelResult report, final int returnTab) {
        if (report == null || report.replayFilePath == null || report.replayFilePath.isEmpty()) {
            Toast.makeText(this, "本局暂无可播放的 3D 录像文件", Toast.LENGTH_SHORT).show();
            return;
        }
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未连接到 YGOMobile 主程序，请从游戏主菜单进入 RPG 模式", Toast.LENGTH_LONG).show();
            return;
        }
        String p0Name = report.sideAFirst ? report.sideAName : report.sideBName;
        String p1Name = report.sideAFirst ? report.sideBName : report.sideAName;
        bridge.startRpgReplay(this, report.replayFilePath, p0Name, p1Name, returnTab);
        finish();
    }

    private void launchAiDuel(AiOpponent opponent) {
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        if (!val.valid) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 卡组未通过校验")
                    .setMessage(android.text.TextUtils.join("\n", val.errors))
                    .setPositiveButton("去组卡", (d, w) -> {
                        TabLayout.Tab t = tabLayout.getTabAt(3);
                        if (t != null) t.select();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        AiDeckGenerator.GeneratedAiDeck aiDeck = AiDeckGenerator.generateDeck(
                catalog, opponent, profile.currentTimelineDate, new Random());
        try {
            File playerYdk = DeckValidator.exportToYdk(
                    repository.getPlayerDeckYdkFile(), profile.activeDeckMain, profile.activeDeckExtra);
            File aiYdk = DeckValidator.exportToYdk(
                    repository.getAiDeckYdkFile(opponent.id), aiDeck.main, aiDeck.extra);

            RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
            if (bridge == null) {
                Toast.makeText(this, "未注册 YGOMobile 决斗桥接器", Toast.LENGTH_LONG).show();
                return;
            }
            RpgSessionManager.beginRpgDuel(opponent);
            bridge.startRpgAiDuel(this, opponent,
                    playerYdk.getAbsolutePath(), aiYdk.getAbsolutePath(),
                    aiDeck.banlist, activeRule, profile.currentTimelineDate);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "导出卡组失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Map<Integer, Integer> countDeckCards() {
        Map<Integer, Integer> map = new HashMap<>();
        for (Integer id : profile.activeDeckMain) {
            map.put(id, map.getOrDefault(id, 0) + 1);
        }
        for (Integer id : profile.activeDeckExtra) {
            map.put(id, map.getOrDefault(id, 0) + 1);
        }
        return map;
    }

    private LinearLayout makeCardContainer() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#334155"));
        ll.setBackground(bg);
        ll.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        ll.setLayoutParams(lp);
        return ll;
    }

    private TextView makeTitleText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#F8FAFC"));
        tv.setTextSize(14f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private TextView makeSubtleText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(12f);
        tv.setPadding(0, dp(2), 0, 0);
        return tv;
    }

    private Button makeActionButton(String label, String hexBg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexBg));
        bg.setCornerRadius(dp(6));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMarginEnd(dp(8));
        b.setLayoutParams(lp);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        return b;
    }

    private LinearLayout makeEqualWeightButtonRow(int topPadDp, Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, dp(topPadDp), 0, 0);
        List<Button> nonNull = new ArrayList<>();
        for (Button b : buttons) {
            if (b != null) nonNull.add(b);
        }
        for (int i = 0; i < nonNull.size(); i++) {
            Button b = nonNull.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            if (i < nonNull.size() - 1) {
                lp.setMarginEnd(dp(8));
            }
            b.setLayoutParams(lp);
            b.setPadding(dp(8), 0, dp(8), 0);
            row.addView(b);
        }
        return row;
    }

    private static class ShowdownDeckChoice {
        final String key;
        final String group;
        final String label;
        final String shortName;
        final String fillStyle;
        final List<Integer> main;
        final List<Integer> extra;

        ShowdownDeckChoice(String key, String group, String label, String shortName,
                           String fillStyle, List<Integer> main, List<Integer> extra) {
            this.key = key;
            this.group = group;
            this.label = label;
            this.shortName = shortName;
            this.fillStyle = fillStyle;
            this.main = main != null ? new ArrayList<>(main) : new ArrayList<>();
            this.extra = extra != null ? new ArrayList<>(extra) : new ArrayList<>();
        }
    }

    private List<ShowdownDeckChoice> buildShowdownChoices() {
        List<ShowdownDeckChoice> list = new ArrayList<>();
        OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        String curStyle = RpgAiStrategy.inferStyleFromDeck(profile.activeDeckMain, profile.activeDeckExtra, activeRule);
        list.add(new ShowdownDeckChoice(
                "current",
                "当前卡组",
                "[当前卡组] 当前编辑中的 RPG 卡组（主 " + profile.activeDeckMain.size()
                        + " / 额外 " + profile.activeDeckExtra.size() + " · AI策略:" + RpgAiStrategy.getStyleDisplayName(curStyle) + "）",
                "玩家自构AI",
                curStyle,
                profile.activeDeckMain,
                profile.activeDeckExtra
        ));
        if (profile.savedDecks != null) {
            for (RpgProfile.SavedDeck sd : profile.savedDecks) {
                if (sd == null) continue;
                int m = sd.main != null ? sd.main.size() : 0;
                int e = sd.extra != null ? sd.extra.size() : 0;
                String st = RpgAiStrategy.inferStyleFromDeck(sd.main, sd.extra, activeRule);
                list.add(new ShowdownDeckChoice(
                        "saved:" + sd.id,
                        "已保存卡组",
                        "[已保存] " + sd.name + "（主 " + m + " / 额外 " + e
                                + " · AI策略:" + RpgAiStrategy.getStyleDisplayName(st) + "）",
                        sd.name,
                        st,
                        sd.main,
                        sd.extra
                ));
            }
        }
        List<AiOpponent> opps = TimelineEngine.getAvailableOpponents(
                AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        for (AiOpponent opp : opps) {
            AiDeckGenerator.GeneratedAiDeck gen = AiDeckGenerator.generateDeck(
                    catalog, opp, profile.currentTimelineDate, new Random(opp.id.hashCode()));
            String eraName = gen.appliedProfile != null ? (" · " + gen.appliedProfile.deckName) : "";
            String st = (gen.appliedProfile != null && gen.appliedProfile.fillStyle != null)
                    ? gen.appliedProfile.fillStyle
                    : RpgAiStrategy.inferStyleFromDeck(gen.main, gen.extra, activeRule);
            list.add(new ShowdownDeckChoice(
                    "ai:" + opp.id,
                    "当前时间点 AI 卡组",
                    "[AI] " + opp.name + eraName + "（主 " + gen.main.size()
                            + " · 策略:" + RpgAiStrategy.getStyleDisplayName(st) + "）",
                    opp.name,
                    st,
                    gen.main,
                    gen.extra
            ));
        }
        return list;
    }

    private ShowdownDeckChoice findShowdownChoice(List<ShowdownDeckChoice> list, String key, int fallbackIdx) {
        if (key != null) {
            for (ShowdownDeckChoice c : list) {
                if (key.equals(c.key)) return c;
            }
        }
        if (list.isEmpty()) return null;
        return list.get(Math.min(fallbackIdx, list.size() - 1));
    }

    private void showShowdownPickerDialog(final boolean isSideA, final List<ShowdownDeckChoice> choices) {
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            labels[i] = choices.get(i).label;
        }
        new AlertDialog.Builder(this)
                .setTitle(isSideA ? "选择 A 方卡组" : "选择 B 方卡组")
                .setItems(labels, (dlg, which) -> {
                    if (isSideA) showdownKeyA = choices.get(which).key;
                    else showdownKeyB = choices.get(which).key;
                    renderCurrentTab();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void runRealOcgShowdownBatch(final ShowdownDeckChoice choiceA,
                                         final ShowdownDeckChoice choiceB,
                                         final int gameCount) {
        if (choiceA == null || choiceB == null) return;
        if (choiceA.main.size() < 5 || choiceB.main.size() < 5) {
            Toast.makeText(this, "双方主卡组均需至少 5 张卡片才能开始对战", Toast.LENGTH_LONG).show();
            return;
        }
        final RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未注册 YGOMobile 决斗引擎桥接器", Toast.LENGTH_LONG).show();
            return;
        }
        final OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        final TextView tvProg = makeSubtleText("正在初始化真实 ocgcore 引擎…\n进度：0 / " + gameCount);
        tvProg.setPadding(dp(20), dp(14), dp(20), dp(10));
        tvProg.setTextColor(Color.parseColor("#E2E8F0"));
        final AlertDialog progDlg = new AlertDialog.Builder(this)
                .setTitle("⚔️ 真实 ocgcore " + gameCount + " 局 AI 对决")
                .setView(tvProg)
                .setCancelable(false)
                .show();

        new Thread(() -> {
            final RpgAiAutoDuelModels.BatchResult res = bridge.runHeadlessOcgBatch(
                    catalog,
                    choiceA.label, choiceA.main, choiceA.extra, choiceA.fillStyle,
                    choiceB.label, choiceB.main, choiceB.extra, choiceB.fillStyle,
                    activeRule,
                    profile.currentTimelineDate,
                    gameCount,
                    (done, total, wA, wB, dr) -> runOnUiThread(() -> tvProg.setText(
                            "真实 ocgcore 引擎对战中（双方均为 OCG 专属 AI）\n"
                                    + "进度：" + done + " / " + total + " 局\n"
                                    + "当前比分：A 胜 " + wA + " · B 胜 " + wB + " · 平 " + dr)),
                    new Random());
            runOnUiThread(() -> {
                if (progDlg.isShowing()) progDlg.dismiss();
                if (res == null) {
                    Toast.makeText(this, "ocgcore 引擎未就绪，请稍后再试", Toast.LENGTH_LONG).show();
                    return;
                }
                lastShowdownReport = res;
                Toast.makeText(this,
                        "已完成 " + res.totalGames + " 局真实 ocgcore AI 对决（耗时 " + res.elapsedMs + "ms）",
                        Toast.LENGTH_SHORT).show();
                renderCurrentTab();
            });
        }, "RpgOcgShowdownBatch").start();
    }

    private LinearLayout makeShowdownSideRow(String prefix, ShowdownDeckChoice choice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(4);
        row.setLayoutParams(rlp);

        if (choice != null && choice.key != null && choice.key.startsWith("ai:")) {
            String oppId = choice.key.substring(3);
            ImageView av = new ImageView(this);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(40), dp(40));
            alp.setMarginEnd(dp(8));
            av.setLayoutParams(alp);
            av.setScaleType(ImageView.ScaleType.CENTER_CROP);
            av.setPadding(dp(2), dp(2), dp(2), dp(2));
            av.setBackground(RpgCardArtLoader.createAvatarPlaceholderDrawable());
            RpgCardArtLoader.loadOpponentAvatarInto(this, av, oppId);
            row.addView(av);
        }

        TextView tv = makeSubtleText(prefix + (choice != null ? choice.label : "未选择"));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);
        return row;
    }

    // ==================== TAB 7: REAL OCGCORE AI SHOWDOWN ====================
    private void renderAiShowdownTab() {
        List<ShowdownDeckChoice> choices = buildShowdownChoices();
        final ShowdownDeckChoice choiceA = findShowdownChoice(choices, showdownKeyA, 0);
        final ShowdownDeckChoice choiceB = findShowdownChoice(choices, showdownKeyB, choices.size() > 1 ? choices.size() - 1 : 0);
        if (choiceA != null) showdownKeyA = choiceA.key;
        if (choiceB != null) showdownKeyB = choiceB.key;

        layoutTabActions.addView(makeSubtleText("⚔️ 真实 ocgcore 引擎 AI 对决 · 双方均由专属 OCG AI 策略操控（自选卡组自动识别流派），支持批量测胜率或 3D 实况观战"));

        LinearLayout pickerCard = makeCardContainer();
        pickerCard.addView(makeTitleText("选择对战双方卡组（时间轴：" + profile.currentTimelineDate + "）"));
        pickerCard.addView(makeShowdownSideRow("A 方：", choiceA));
        pickerCard.addView(makeShowdownSideRow("B 方：", choiceB));

        Button pickABtn = makeActionButton("选择 A 方", "#0284C7");
        pickABtn.setOnClickListener(v -> showShowdownPickerDialog(true, choices));

        Button pickBBtn = makeActionButton("选择 B 方", "#7C3AED");
        pickBBtn.setOnClickListener(v -> showShowdownPickerDialog(false, choices));

        Button swapBtn = makeActionButton("🔄 互换", "#475569");
        swapBtn.setOnClickListener(v -> {
            String tmp = showdownKeyA;
            showdownKeyA = showdownKeyB;
            showdownKeyB = tmp;
            renderCurrentTab();
        });
        pickerCard.addView(makeEqualWeightButtonRow(8, pickABtn, pickBBtn, swapBtn));

        Button run20Btn = makeActionButton("⚡ 20 局真引擎", "#059669");
        run20Btn.setOnClickListener(v -> runRealOcgShowdownBatch(choiceA, choiceB, 20));

        Button run100Btn = makeActionButton("🔥 100 局真引擎", "#D97706");
        run100Btn.setOnClickListener(v -> runRealOcgShowdownBatch(choiceA, choiceB, 100));

        Button spectateBtn = makeActionButton("👁️ 3D实况观战", "#DC2626");
        spectateBtn.setOnClickListener(v -> {
            if (choiceA == null || choiceB == null) return;
            if (choiceA.main.size() < 5 || choiceB.main.size() < 5) {
                Toast.makeText(this, "双方主卡组均需至少 5 张卡片才能开始观战", Toast.LENGTH_LONG).show();
                return;
            }
            RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
            if (bridge == null) {
                Toast.makeText(this, "未注册 YGOMobile 决斗桥接器", Toast.LENGTH_LONG).show();
                return;
            }
            OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
            bridge.startRpgAiSpectateDuel(
                    this,
                    choiceA.shortName, choiceA.main, choiceA.extra, choiceA.fillStyle,
                    choiceB.shortName, choiceB.main, choiceB.extra, choiceB.fillStyle,
                    activeRule,
                    profile.currentTimelineDate,
                    TAB_AI_SHOWDOWN);
            finish();
        });

        pickerCard.addView(makeEqualWeightButtonRow(8, run20Btn, run100Btn));
        pickerCard.addView(makeEqualWeightButtonRow(6, spectateBtn));
        layoutContent.addView(pickerCard);

        if (lastShowdownReport != null) {
            RpgAiAutoDuelModels.BatchResult r = lastShowdownReport;
            LinearLayout resCard = makeCardContainer();
            TextView resTitle = makeTitleText(String.format(Locale.US,
                    "📊 %d 局真实 ocgcore 对决结果（%s · %s）",
                    r.totalGames, r.timelineDate, r.appliedRule != null ? r.appliedRule.name : ""));
            resTitle.setTextColor(Color.parseColor("#38BDF8"));
            resCard.addView(resTitle);

            TextView aTv = makeSubtleText(String.format(Locale.US,
                    "A 方胜率：%.1f%%（%d 胜 / %d 局 · AI策略:%s）— %s",
                    r.getSideAWinRate(), r.sideAWins, r.totalGames,
                    RpgAiStrategy.getStyleDisplayName(r.sideAStyle), r.sideAName));
            aTv.setTextColor(Color.parseColor("#34D399"));
            resCard.addView(aTv);

            TextView bTv = makeSubtleText(String.format(Locale.US,
                    "B 方胜率：%.1f%%（%d 胜 / %d 局 · AI策略:%s）— %s",
                    r.getSideBWinRate(), r.sideBWins, r.totalGames,
                    RpgAiStrategy.getStyleDisplayName(r.sideBStyle), r.sideBName));
            bTv.setTextColor(Color.parseColor("#F87171"));
            resCard.addView(bTv);

            resCard.addView(makeSubtleText(String.format(Locale.US,
                    "平局：%d 局 · 平均回合数：%.1f · 耗时：%d ms",
                    r.draws, r.avgTurns, r.elapsedMs)));
            resCard.addView(makeSubtleText(String.format(Locale.US,
                    "先后攻拆分（50/50 交替）：A 先攻胜率 %.1f%%（%d/%d） · A 后攻胜率 %.1f%%（%d/%d）",
                    r.getSideAFirstWinRate(), r.sideAFirstWins, r.sideAFirstGames,
                    r.getSideASecondWinRate(), r.sideASecondWins, r.sideASecondGames)));
            resCard.addView(makeSubtleText("说明：由真实 ocgcore 引擎 + 专属 OCG AI 实战演算，不计入战绩也不发放代币。"));

            if (r.sampleLastResult != null && r.sampleLastResult.replayFilePath != null) {
                Button sampleBtn = makeActionButton("🎬 3D回放样本对局(第" + r.totalGames + "局)", "#0284C7");
                sampleBtn.setOnClickListener(v -> launchNative3dReplay(r.sampleLastResult, TAB_AI_SHOWDOWN));
                resCard.addView(makeEqualWeightButtonRow(8, sampleBtn));
            }
            layoutContent.addView(resCard);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
