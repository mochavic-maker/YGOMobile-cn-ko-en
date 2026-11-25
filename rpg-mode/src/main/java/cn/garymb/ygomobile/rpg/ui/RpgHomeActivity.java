package cn.garymb.ygomobile.rpg.ui;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
import cn.garymb.ygomobile.rpg.model.Rarity;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Self-contained Android UI for the 1999 OCG Timeline RPG Mode (1999-02-04 ~ 1999-12-31):
 * - Tab 0: Shop (Vol.1-6 / Booster 1-6 / STARTER BOX / EX / LE1 / PP1-2 at authentic JPY prices)
 * - Tab 1: 1999 Special Promo & Event Card Giveaways
 * - Tab 2: Card Collection & Dust Dismantle / Craft (with timeline-resolved errata text)
 * - Tab 3: Ownership + Timeline + Banlist Restricted Deck Builder
 * - Tab 4: Timeline-Compliant AI Duel Launcher (Manual WindBot & Quick Duel) & Token Rewards
 * - Tab 5: OCG Official Card Errata History Archive (Series 1 ~ Series 13)
 */
public class RpgHomeActivity extends AppCompatActivity {

    private RpgCatalog catalog;
    private RpgRepository repository;
    private RpgProfile profile;
    private final PackOpeningEngine packEngine = new PackOpeningEngine();

    private TextView tvTokens;
    private TextView tvDust;
    private TextView tvTimelineDate;
    private TextView tvActiveBanlist;
    private TextView tvActiveRule;
    private LinearLayout layoutMilestones;
    private LinearLayout layoutTabActions;
    private LinearLayout layoutContent;
    private TabLayout tabLayout;

    private int activeTab = 0;
    private boolean collectionOnlyOwned = false;
    private boolean errataOnly1999 = true;

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

        tvTokens = findViewById(R.id.tv_rpg_tokens);
        tvDust = findViewById(R.id.tv_rpg_dust);
        tvTimelineDate = findViewById(R.id.tv_timeline_date);
        tvActiveBanlist = findViewById(R.id.tv_active_banlist);
        tvActiveRule = findViewById(R.id.tv_active_rule);
        layoutMilestones = findViewById(R.id.layout_milestones);
        layoutTabActions = findViewById(R.id.layout_tab_actions);
        layoutContent = findViewById(R.id.layout_rpg_content);
        tabLayout = findViewById(R.id.tab_rpg_nav);

        findViewById(R.id.btn_rpg_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pick_date).setOnClickListener(v -> openDatePicker());

        tabLayout.addTab(tabLayout.newTab().setText("卡包商店"));
        tabLayout.addTab(tabLayout.newTab().setText("特典活动"));
        tabLayout.addTab(tabLayout.newTab().setText("卡库/合成"));
        tabLayout.addTab(tabLayout.newTab().setText("RPG 组卡"));
        tabLayout.addTab(tabLayout.newTab().setText("AI 对战"));
        tabLayout.addTab(tabLayout.newTab().setText("效果勘误"));
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

        renderMilestones();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null) {
            profile = repository.loadProfile();
        }
        DuelRewardEngine.RewardSettlement settlement = RpgSessionManager.consumeLastSettlement();
        if (settlement != null) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 决斗结算 · " + settlement.outcomeLabel)
                    .setMessage("获得代币：+" + settlement.tokensEarned
                            + "\n当前代币余额：" + settlement.totalTokensAfter)
                    .setPositiveButton("收下奖励", null)
                    .show();
        }
        if (catalog != null) {
            refreshAll();
        }
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
        int preErrataCnt = CardErrataEngine.getRequiredPreErrataScriptIds(catalog, profile.currentTimelineDate).size();
        tvTimelineDate.setText("当前时间轴: " + profile.currentTimelineDate
                + "（已收集 " + profile.collection.size()
                + " 种 / " + profile.getTotalOwnedCards() + " 张 · 当前生效旧版勘误卡 " + preErrataCnt + " 张）");

        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        tvActiveBanlist.setText("当前禁限表: " + banlist.name + " — " + banlist.description);
        OcgRuleSet ruleSet = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        tvActiveRule.setText("当前规则（随时间轴自动锁定）: " + ruleSet.name + " — " + ruleSet.description);
        renderCurrentTab();
    }

    private void renderMilestones() {
        layoutMilestones.removeAllViews();
        List<String> milestones = TimelineEngine.getTimelineMilestones(catalog);
        for (final String date : milestones) {
            Button btn = new Button(this);
            btn.setText(date);
            btn.setTextSize(11f);
            btn.setTextColor(Color.WHITE);
            btn.setAllCaps(false);
            btn.setBackgroundColor(Color.parseColor("#334155"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
            lp.setMarginEnd(dp(6));
            btn.setLayoutParams(lp);
            btn.setPadding(dp(10), 0, dp(10), 0);
            btn.setOnClickListener(v -> {
                profile.currentTimelineDate = TimelineEngine.clampDate(catalog, date);
                saveProfileSafe();
                refreshAll();
            });
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
            profile.currentTimelineDate = TimelineEngine.clampDate(catalog, picked);
            saveProfileSafe();
            refreshAll();
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
                renderErrataTab();
                break;
        }
    }

    // ==================== TAB 0: SHOP & PACK OPENING ====================
    private void renderShopTab() {
        TextView hint = makeSubtleText("仅可购买发售日 ≤ " + profile.currentTimelineDate
                + " 的 1999 OCG 商品（代币价 1:1 还原当年日元定价）");
        layoutTabActions.addView(hint);

        for (final OcgPack pack : catalog.packs) {
            boolean unlocked = TimelineEngine.isReleasedOnOrBefore(pack.releaseDate, profile.currentTimelineDate);
            LinearLayout card = makeCardContainer();

            TextView title = makeTitleText("[" + pack.getProductTypeLabelCn() + "] " + pack.name);
            String unit = pack.fixedContents ? "盒/套" : ("VENDING_MACHINE".equals(pack.productType) ? "次" : "包");
            TextView meta = makeSubtleText("发售日: " + pack.releaseDate
                    + " · 收录 " + pack.cards.size() + " 种 · " + pack.cardsPerPack + " 张/" + unit
                    + " · 定价 " + pack.priceTokens + " 代币（原价 ¥" + pack.priceTokens + "）"
                    + " · 已购 " + profile.packsOpened.getOrDefault(pack.id, 0) + " " + unit);
            TextView odds = makeSubtleText(pack.description != null ? pack.description : pack.slotRules.description);

            card.addView(title);
            card.addView(meta);
            card.addView(odds);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, dp(8), 0, 0);

            if (unlocked) {
                if (pack.fixedContents) {
                    Button buyBox = makeActionButton("购买整套 (" + pack.priceTokens + " 代币)", "#059669");
                    buyBox.setOnClickListener(v -> doOpenPack(pack, 1));
                    btnRow.addView(buyBox);
                } else {
                    Button b1 = makeActionButton("买 1 " + unit + " (" + pack.priceTokens + ")", "#D97706");
                    b1.setOnClickListener(v -> doOpenPack(pack, 1));
                    Button b5 = makeActionButton("买 5 " + unit + " (" + (pack.priceTokens * 5) + ")", "#0284C7");
                    b5.setOnClickListener(v -> doOpenPack(pack, 5));
                    btnRow.addView(b1);
                    btnRow.addView(b5);
                }
            } else {
                TextView locked = new TextView(this);
                locked.setText("未到发售日（请将时间轴移动至 " + pack.releaseDate + " 或之后）");
                locked.setTextColor(Color.parseColor("#F87171"));
                locked.setTextSize(12f);
                btnRow.addView(locked);
            }

            card.addView(btnRow);
            layoutContent.addView(card);
        }
    }

    private void doOpenPack(OcgPack pack, int count) {
        PackOpeningEngine.PackOpenResult res = packEngine.openPacks(profile, pack, count);
        if (!res.success) {
            Toast.makeText(this, res.errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        saveProfileSafe();
        refreshAll();

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (PackOpeningEngine.PulledCard pc : res.pulledCards) {
            if (shown < 30) {
                sb.append("[").append(pc.card.rarity).append("] ")
                        .append(pc.card.getDisplayName())
                        .append(pc.isNew ? "  ★NEW" : "  (已有 x" + pc.ownedAfter + ")")
                        .append("\n");
            }
            shown++;
        }
        if (shown > 30) {
            sb.append("… 其余 ").append(shown - 30).append(" 张卡片也已全部收入卡库。");
        }
        new AlertDialog.Builder(this)
                .setTitle((pack.fixedContents ? "购入完成 · " : "开包结果 · ") + pack.name + " x" + count)
                .setMessage(sb.toString())
                .setPositiveButton("收入卡库", null)
                .show();
    }

    // ==================== TAB 1: 1999 SPECIAL PROMO EVENTS ====================
    private void renderEventsTab() {
        TextView hint = makeSubtleText("1999 年真实 OCG 期间特典/大会/攻略本单卡赠送活动（时间轴到达活动开启日后可免费领取）");
        layoutTabActions.addView(hint);

        for (final OcgEvent ev : catalog.events) {
            boolean unlocked = TimelineEngine.isReleasedOnOrBefore(ev.releaseDate, profile.currentTimelineDate);
            boolean claimed = profile.claimedEventIds != null && profile.claimedEventIds.contains(ev.id);

            LinearLayout card = makeCardContainer();
            card.addView(makeTitleText(ev.name + (claimed ? " 【已领取】" : "")));
            card.addView(makeSubtleText("活动日期: " + ev.releaseDate
                    + (ev.endDate != null && !ev.endDate.equals(ev.releaseDate) ? " ~ " + ev.endDate : "")
                    + (ev.requiredWins > 0 ? " · 需累计胜场 ≥ " + ev.requiredWins : " · 免费领取")));
            card.addView(makeSubtleText(ev.description));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, 0);

            if (claimed) {
                TextView done = makeSubtleText("✓ 特典已收入卡库");
                done.setTextColor(Color.parseColor("#34D399"));
                row.addView(done);
            } else if (!unlocked) {
                TextView locked = makeSubtleText("未到活动日期（时间轴 ≥ " + ev.releaseDate + " 开启）");
                locked.setTextColor(Color.parseColor("#F87171"));
                row.addView(locked);
            } else {
                Button btn = makeActionButton("领取特典单卡", "#7C3AED");
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
                    new AlertDialog.Builder(this)
                            .setTitle("特典领取成功 · " + ev.name)
                            .setMessage(sb.toString())
                            .setPositiveButton("收下特典", null)
                            .show();
                });
                row.addView(btn);
            }
            card.addView(row);
            layoutContent.addView(card);
        }
    }

    // ==================== TAB 1: COLLECTION & CRAFTING ====================
    private void renderCollectionTab() {
        Button toggleBtn = makeActionButton(collectionOnlyOwned ? "筛选: 仅看已拥有" : "筛选: 全部已发售", "#334155");
        toggleBtn.setOnClickListener(v -> {
            collectionOnlyOwned = !collectionOnlyOwned;
            renderCurrentTab();
        });
        Button bulkBtn = makeActionButton("一键分解多余(>3张)", "#7C3AED");
        bulkBtn.setOnClickListener(v -> {
            CraftingEngine.CraftResult r = CraftingEngine.dismantleAllSurplus(profile, catalog);
            Toast.makeText(this, r.message, Toast.LENGTH_SHORT).show();
            if (r.success) {
                saveProfileSafe();
                refreshAll();
            }
        });
        layoutTabActions.addView(toggleBtn);
        layoutTabActions.addView(bulkBtn);

        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        List<RpgCard> released = TimelineEngine.getReleasedCards(catalog, profile.currentTimelineDate);

        for (final RpgCard card : released) {
            int owned = profile.getOwnedCount(card.id);
            if (collectionOnlyOwned && owned <= 0) continue;

            Rarity rarity = card.getRarityEnum();
            int dismantleGain = catalog.craftingRules.getDismantleDust(rarity);
            int craftCost = catalog.craftingRules.getCraftCostDust(rarity);
            String limitBadge = banlist.getLimitBadge(card.id);
            String errataBadge = CardErrataEngine.getVersionBadge(catalog, card.id, profile.currentTimelineDate);
            final OcgCardErrata errataRec = catalog.findErrata(card.id);

            LinearLayout box = makeCardContainer();
            String header = "[" + rarity.code + "] " + card.getDisplayName()
                    + (limitBadge.isEmpty() ? "" : " 【" + limitBadge + "】")
                    + (errataBadge.isEmpty() ? "" : " ⟪" + errataBadge + "⟫")
                    + "  x" + owned;
            TextView tvTitle = makeTitleText(header);
            tvTitle.setTextColor(Color.parseColor(rarity.colorHex));
            box.addView(tvTitle);

            String stats = card.isMonster()
                    ? String.format(Locale.US, "Lv.%d %s/%s ATK %d / DEF %d (%s)",
                    card.level, card.attribute, card.race, card.atk, card.def, card.setCode)
                    : card.cardType + " (" + card.setCode + ")";
            box.addView(makeSubtleText(stats));

            String effectiveDesc = CardErrataEngine.getEffectiveDescCn(catalog, card, profile.currentTimelineDate);
            if (!effectiveDesc.isEmpty()) {
                TextView tvDesc = makeSubtleText(effectiveDesc);
                if (!errataBadge.isEmpty()) {
                    tvDesc.setTextColor(Color.parseColor("#FDE68A"));
                }
                box.addView(tvDesc);
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(6), 0, 0);

            Button craftBtn = makeActionButton("合成 (-" + craftCost + "尘)", "#0284C7");
            craftBtn.setOnClickListener(v -> {
                CraftingEngine.CraftResult r = CraftingEngine.craft(profile, catalog, card.id, 1);
                Toast.makeText(this, r.message, Toast.LENGTH_SHORT).show();
                if (r.success) {
                    saveProfileSafe();
                    refreshAll();
                }
            });
            row.addView(craftBtn);

            if (owned > 0) {
                Button disBtn = makeActionButton("分解 (+" + dismantleGain + "尘)", "#B91C1C");
                disBtn.setOnClickListener(v -> {
                    CraftingEngine.CraftResult r = CraftingEngine.dismantle(profile, catalog, card.id, 1);
                    Toast.makeText(this, r.message, Toast.LENGTH_SHORT).show();
                    if (r.success) {
                        saveProfileSafe();
                        refreshAll();
                    }
                });
                row.addView(disBtn);
            }
            if (errataRec != null) {
                Button histBtn = makeActionButton("勘误史(" + errataRec.revisions.size() + ")", "#7C3AED");
                histBtn.setOnClickListener(v -> showErrataHistoryDialog(errataRec));
                row.addView(histBtn);
            }
            box.addView(row);
            layoutContent.addView(box);
        }
    }

    // ==================== TAB 3: RPG DECK BUILDER ====================
    private void renderDeckTab() {
        Button autoBtn = makeActionButton("智能组卡 (40张)", "#059669");
        autoBtn.setOnClickListener(v -> {
            int built = DeckValidator.autoBuildLegalPlayerDeck(profile, catalog);
            saveProfileSafe();
            Toast.makeText(this, "已按当前时间轴与卡库自动构筑 " + built + " 张主卡组", Toast.LENGTH_SHORT).show();
            refreshAll();
        });
        Button clearBtn = makeActionButton("清空卡组", "#475569");
        clearBtn.setOnClickListener(v -> {
            profile.activeDeckMain.clear();
            profile.activeDeckExtra.clear();
            saveProfileSafe();
            refreshAll();
        });
        layoutTabActions.addView(autoBtn);
        layoutTabActions.addView(clearBtn);

        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        LinearLayout statusCard = makeCardContainer();
        TextView statusTitle = makeTitleText(
                "当前 RPG 卡组：主卡组 " + profile.activeDeckMain.size()
                        + " 张 / 额外 " + profile.activeDeckExtra.size() + " 张 · "
                        + (val.valid ? "✔ 合规可出战" : "✘ 未通过校验"));
        statusTitle.setTextColor(Color.parseColor(val.valid ? "#34D399" : "#F87171"));
        statusCard.addView(statusTitle);
        if (!val.valid) {
            StringBuilder errs = new StringBuilder();
            for (String e : val.errors) {
                errs.append("• ").append(e).append("\n");
            }
            statusCard.addView(makeSubtleText(errs.toString().trim()));
        }
        layoutContent.addView(statusCard);

        Map<Integer, Integer> inDeckCounts = countDeckCards();
        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        List<RpgCard> released = TimelineEngine.getReleasedCards(catalog, profile.currentTimelineDate);

        for (final RpgCard card : released) {
            int owned = profile.getOwnedCount(card.id);
            int inDeck = inDeckCounts.getOrDefault(card.id, 0);
            if (owned <= 0 && inDeck <= 0) continue;

            int legalMax = Math.min(owned, banlist.getMaxCopies(card.id));
            LinearLayout box = makeCardContainer();
            String badge = banlist.getLimitBadge(card.id);
            String errataBadge = CardErrataEngine.getVersionBadge(catalog, card.id, profile.currentTimelineDate);
            TextView tv = makeTitleText("[" + card.rarity + "] " + card.getDisplayName()
                    + (badge.isEmpty() ? "" : " 【" + badge + "】")
                    + (errataBadge.isEmpty() ? "" : " ⟪" + errataBadge + "⟫"));
            tv.setTextColor(Color.parseColor(card.getRarityEnum().colorHex));
            box.addView(tv);
            box.addView(makeSubtleText("已投入 " + inDeck + " / 上限 " + legalMax + "（拥有 " + owned + " 张）"));
            if (!errataBadge.isEmpty()) {
                TextView tvErrDesc = makeSubtleText(CardErrataEngine.getEffectiveDescCn(catalog, card, profile.currentTimelineDate));
                tvErrDesc.setTextColor(Color.parseColor("#FDE68A"));
                box.addView(tvErrDesc);
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
            box.addView(row);
            layoutContent.addView(box);
        }
    }

    // ==================== TAB 4: AI DUEL ====================
    private void renderAiDuelTab() {
        layoutTabActions.addView(makeSubtleText("战绩：" + profile.wins + " 胜 " + profile.losses + " 负 " + profile.draws + " 平 · AI 卡组严格遵守当前时间轴与禁限表"));

        List<AiOpponent> opponents = AiDeckGenerator.getDemoOpponents();
        for (final AiOpponent opp : opponents) {
            boolean unlocked = TimelineEngine.isReleasedOnOrBefore(opp.unlockDate, profile.currentTimelineDate);
            LinearLayout box = makeCardContainer();
            box.addView(makeTitleText(opp.name + "  (" + opp.title + ")"));
            box.addView(makeSubtleText(opp.strategyHint));
            box.addView(makeSubtleText("奖励：胜 +" + opp.winRewardTokens + " / 平 +" + opp.drawRewardTokens + " / 负 +" + opp.lossRewardTokens + " 代币"));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, 0);

            if (unlocked) {
                Button fightBtn = makeActionButton("亲自对战 (WindBot)", "#DC2626");
                fightBtn.setOnClickListener(v -> launchAiDuel(opp));
                row.addView(fightBtn);

                Button quickBtn = makeActionButton("⚡ 快速决斗", "#D97706");
                quickBtn.setOnClickListener(v -> runQuickDuel(opp));
                row.addView(quickBtn);
            } else {
                TextView locked = new TextView(this);
                locked.setText("未解锁（需时间轴 ≥ " + opp.unlockDate + "）");
                locked.setTextColor(Color.parseColor("#F87171"));
                locked.setTextSize(12f);
                row.addView(locked);
            }
            box.addView(row);
            layoutContent.addView(box);
        }
    }

    // ==================== TAB 5: OFFICIAL OCG ERRATA ARCHIVE ====================
    private void renderErrataTab() {
        int totalErrata = catalog.getErrataRecords().size();
        int cnt1999 = 0;
        for (OcgCardErrata r : catalog.getErrataRecords()) {
            if (r.in1999Catalog) cnt1999++;
        }

        Button scopeBtn = makeActionButton(
                errataOnly1999 ? "范围: 仅1999卡池 (" + cnt1999 + "张)" : "范围: 全OCG历次勘误 (" + totalErrata + "张)",
                "#7C3AED");
        scopeBtn.setOnClickListener(v -> {
            errataOnly1999 = !errataOnly1999;
            renderCurrentTab();
        });
        layoutTabActions.addView(scopeBtn);

        TextView info = makeSubtleText("随当前时间轴 (" + profile.currentTimelineDate + ") 自动切换卡片生效版本，点击任意卡片可查看完整历次修改史。");
        layoutContent.addView(info);

        for (final OcgCardErrata rec : catalog.getErrataRecords()) {
            if (errataOnly1999 && !rec.in1999Catalog) continue;
            OcgCardErrata.Revision active = CardErrataEngine.getActiveRevision(rec, profile.currentTimelineDate);
            boolean preErrata = CardErrataEngine.isPreErrataAt(catalog, rec.cardId, profile.currentTimelineDate);

            LinearLayout box = makeCardContainer();
            String badge = active != null ? ("⟪" + active.effectiveDate.replace("-", ".") + " v" + active.versionIndex + "/" + rec.revisions.size() + "⟫") : "";
            TextView tvTitle = makeTitleText(rec.getDisplayName() + "  (" + rec.nameJp + ")  " + badge);
            tvTitle.setTextColor(Color.parseColor(preErrata ? "#FBBF24" : "#34D399"));
            box.addView(tvTitle);
            box.addView(makeSubtleText(rec.era + " · " + rec.getCategoryLabelCn()
                    + " · 共 " + rec.revisions.size() + " 个版本"
                    + (rec.in1999Catalog ? " · ★1999卡池收录" : " · 后续世代存档")));
            if (active != null) {
                box.addView(makeSubtleText("当前生效: " + active.descCn));
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(6), 0, 0);
            Button detailBtn = makeActionButton("查看全部 " + rec.revisions.size() + " 次变更对比", "#334155");
            detailBtn.setOnClickListener(v -> showErrataHistoryDialog(rec));
            row.addView(detailBtn);
            box.addView(row);
            layoutContent.addView(box);
        }
    }

    private void showErrataHistoryDialog(OcgCardErrata rec) {
        OcgCardErrata.Revision active = CardErrataEngine.getActiveRevision(rec, profile.currentTimelineDate);
        StringBuilder sb = new StringBuilder();
        sb.append("日文名：").append(rec.nameJp).append("\n")
                .append("分类：").append(rec.era).append(" · ").append(rec.getCategoryLabelCn()).append("\n")
                .append("当前时间轴：").append(profile.currentTimelineDate).append("\n\n");
        for (OcgCardErrata.Revision rev : rec.revisions) {
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

    private void runQuickDuel(AiOpponent opponent) {
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
        AiDeckGenerator.GeneratedAiDeck aiDeck = AiDeckGenerator.generateDeck(
                catalog, opponent, profile.currentTimelineDate, new Random());
        QuickDuelSimulator.QuickDuelReport report = QuickDuelSimulator.simulate(
                catalog, profile.activeDeckMain, aiDeck.main, opponent,
                profile.currentTimelineDate, new Random());
        DuelRewardEngine.RewardSettlement settlement =
                DuelRewardEngine.settle(profile, opponent, report.playerWon, report.isDraw);
        saveProfileSafe();
        refreshAll();

        StringBuilder sb = new StringBuilder();
        sb.append("规则：").append(report.appliedRule.name).append("\n")
                .append("结果：").append(settlement.outcomeLabel)
                .append("（我方 LP ").append(report.playerLp)
                .append(" vs 对手 LP ").append(report.aiLp)
                .append("，共 ").append(report.turns).append(" 回合）\n")
                .append("获得代币：+").append(settlement.tokensEarned).append("\n\n")
                .append("--- 战报摘录 ---\n");
        int limit = Math.min(report.logs.size(), 18);
        for (int i = 0; i < limit; i++) {
            sb.append(report.logs.get(i)).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("⚡ 快速决斗结算 · " + settlement.outcomeLabel)
                .setMessage(sb.toString())
                .setPositiveButton("收下代币", null)
                .show();
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
        b.setBackgroundColor(Color.parseColor(hexBg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
        lp.setMarginEnd(dp(8));
        b.setLayoutParams(lp);
        b.setPadding(dp(12), 0, dp(12), 0);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
