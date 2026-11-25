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
import cn.garymb.ygomobile.rpg.engine.CraftingEngine;
import cn.garymb.ygomobile.rpg.engine.DeckValidator;
import cn.garymb.ygomobile.rpg.engine.DuelRewardEngine;
import cn.garymb.ygomobile.rpg.engine.PackOpeningEngine;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgPack;
import cn.garymb.ygomobile.rpg.model.Rarity;
import cn.garymb.ygomobile.rpg.model.RpgCard;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;
import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Self-contained Android UI for the OCG Timeline RPG Mode:
 * - Tab 0: Booster Pack Shop & Pack Opening (OCG Vol.1 / Vol.2 demo)
 * - Tab 1: Card Collection & Dust Dismantle / Craft
 * - Tab 2: Ownership + Timeline + Banlist Restricted Deck Builder
 * - Tab 3: Timeline-Compliant AI Duel Launcher & Token Rewards
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
    private LinearLayout layoutMilestones;
    private LinearLayout layoutTabActions;
    private LinearLayout layoutContent;
    private TabLayout tabLayout;

    private int activeTab = 0;
    private boolean collectionOnlyOwned = false;

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

        tvTokens = findViewById(R.id.tv_rpg_tokens);
        tvDust = findViewById(R.id.tv_rpg_dust);
        tvTimelineDate = findViewById(R.id.tv_timeline_date);
        tvActiveBanlist = findViewById(R.id.tv_active_banlist);
        layoutMilestones = findViewById(R.id.layout_milestones);
        layoutTabActions = findViewById(R.id.layout_tab_actions);
        layoutContent = findViewById(R.id.layout_rpg_content);
        tabLayout = findViewById(R.id.tab_rpg_nav);

        findViewById(R.id.btn_rpg_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pick_date).setOnClickListener(v -> openDatePicker());

        tabLayout.addTab(tabLayout.newTab().setText("卡包商店"));
        tabLayout.addTab(tabLayout.newTab().setText("卡库/合成"));
        tabLayout.addTab(tabLayout.newTab().setText("RPG 组卡"));
        tabLayout.addTab(tabLayout.newTab().setText("AI 对战"));
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
        tvTokens.setText("代币: " + profile.tokens);
        tvDust.setText("尘埃: " + profile.dust);
        tvTimelineDate.setText("当前时间轴: " + profile.currentTimelineDate
                + "（已收集 " + profile.collection.size() + " 种 / 共 " + profile.getTotalOwnedCards() + " 张）");

        OcgBanlist banlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
        tvActiveBanlist.setText("当前禁限表: " + banlist.name + " — " + banlist.description);
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
                profile.currentTimelineDate = date;
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
            profile.currentTimelineDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            saveProfileSafe();
            refreshAll();
        }, y, m, d);
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
                renderCollectionTab();
                break;
            case 2:
                renderDeckTab();
                break;
            case 3:
                renderAiDuelTab();
                break;
        }
    }

    // ==================== TAB 0: SHOP & PACK OPENING ====================
    private void renderShopTab() {
        TextView hint = makeSubtleText("仅可购买发售日 ≤ " + profile.currentTimelineDate + " 的 OCG 卡包（每包 5 张，5 包保底 R 以上）");
        layoutTabActions.addView(hint);

        for (final OcgPack pack : catalog.packs) {
            boolean unlocked = TimelineEngine.isReleasedOnOrBefore(pack.releaseDate, profile.currentTimelineDate);
            LinearLayout card = makeCardContainer();

            TextView title = makeTitleText(pack.name + " [" + pack.code + "]");
            TextView meta = makeSubtleText("发售日: " + pack.releaseDate
                    + " · 收录 " + pack.cards.size() + " 种 · 单价 " + pack.priceTokens + " 代币"
                    + " · 已开 " + profile.packsOpened.getOrDefault(pack.id, 0) + " 包");
            TextView odds = makeSubtleText(pack.slotRules.description);

            card.addView(title);
            card.addView(meta);
            card.addView(odds);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, dp(8), 0, 0);

            if (unlocked) {
                Button b1 = makeActionButton("买 1 包 (" + pack.priceTokens + ")", "#D97706");
                b1.setOnClickListener(v -> doOpenPack(pack, 1));
                Button b5 = makeActionButton("买 5 包 (" + (pack.priceTokens * 5) + ")", "#0284C7");
                b5.setOnClickListener(v -> doOpenPack(pack, 5));
                btnRow.addView(b1);
                btnRow.addView(b5);
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
        for (PackOpeningEngine.PulledCard pc : res.pulledCards) {
            sb.append("[").append(pc.card.rarity).append("] ")
                    .append(pc.card.getDisplayName())
                    .append(pc.isNew ? "  ★NEW" : "  (已有 x" + pc.ownedAfter + ")")
                    .append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("开包结果 · " + pack.name + " x" + count)
                .setMessage(sb.toString())
                .setPositiveButton("收入卡库", null)
                .show();
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

            LinearLayout box = makeCardContainer();
            String header = "[" + rarity.code + "] " + card.getDisplayName()
                    + (limitBadge.isEmpty() ? "" : " 【" + limitBadge + "】")
                    + "  x" + owned;
            TextView tvTitle = makeTitleText(header);
            tvTitle.setTextColor(Color.parseColor(rarity.colorHex));
            box.addView(tvTitle);

            String stats = card.isMonster()
                    ? String.format(Locale.US, "Lv.%d %s/%s ATK %d / DEF %d (%s)",
                    card.level, card.attribute, card.race, card.atk, card.def, card.setCode)
                    : card.cardType + " (" + card.setCode + ")";
            box.addView(makeSubtleText(stats));

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
            box.addView(row);
            layoutContent.addView(box);
        }
    }

    // ==================== TAB 2: RPG DECK BUILDER ====================
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
            TextView tv = makeTitleText("[" + card.rarity + "] " + card.getDisplayName()
                    + (badge.isEmpty() ? "" : " 【" + badge + "】"));
            tv.setTextColor(Color.parseColor(card.getRarityEnum().colorHex));
            box.addView(tv);
            box.addView(makeSubtleText("已投入 " + inDeck + " / 上限 " + legalMax + "（拥有 " + owned + " 张）"));

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

    // ==================== TAB 3: AI DUEL ====================
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
                Button fightBtn = makeActionButton("发起 AI 决斗", "#DC2626");
                fightBtn.setOnClickListener(v -> launchAiDuel(opp));
                row.addView(fightBtn);
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

    private void launchAiDuel(AiOpponent opponent) {
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        if (!val.valid) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 卡组未通过校验")
                    .setMessage(android.text.TextUtils.join("\n", val.errors))
                    .setPositiveButton("去组卡", (d, w) -> {
                        TabLayout.Tab t = tabLayout.getTabAt(2);
                        if (t != null) t.select();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

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
                    aiDeck.banlist, profile.currentTimelineDate);
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
