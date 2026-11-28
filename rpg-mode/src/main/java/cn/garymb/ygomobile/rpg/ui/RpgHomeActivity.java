package cn.garymb.ygomobile.rpg.ui;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.garymb.ygomobile.rpg.R;
import cn.garymb.ygomobile.rpg.bridge.RpgDuelHostBridge;
import cn.garymb.ygomobile.rpg.bridge.RpgSessionManager;
import cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels;
import cn.garymb.ygomobile.rpg.ai.RpgAiStrategy;
import cn.garymb.ygomobile.rpg.data.RpgCatalogLoader;
import cn.garymb.ygomobile.rpg.data.RpgRepository;
import cn.garymb.ygomobile.rpg.engine.AiDeckGenerator;
import cn.garymb.ygomobile.rpg.engine.CanonDuelDeckBuilder;
import cn.garymb.ygomobile.rpg.engine.CardErrataEngine;
import cn.garymb.ygomobile.rpg.engine.CraftingEngine;
import cn.garymb.ygomobile.rpg.engine.DeckValidator;
import cn.garymb.ygomobile.rpg.engine.DuelRewardEngine;
import cn.garymb.ygomobile.rpg.engine.EventClaimEngine;
import cn.garymb.ygomobile.rpg.engine.PackOpeningEngine;
import cn.garymb.ygomobile.rpg.engine.RoundRobinLeagueEngine;
import cn.garymb.ygomobile.rpg.engine.TimelineEngine;
import cn.garymb.ygomobile.rpg.engine.TournamentEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgCanonDuel;
import cn.garymb.ygomobile.rpg.model.OcgCardErrata;
import cn.garymb.ygomobile.rpg.model.OcgEvent;
import cn.garymb.ygomobile.rpg.util.RpgDiagLog;
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
 * - Tab 8: Canon Manga/Anime/Movie Duel Recreation
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
    public static final int TAB_CANON_DUELS = 8;
    public static final int TAB_COUNT = 9;

    private static final String[] TAB_TITLES = {
            "卡包商店",
            "特典活动",
            "卡片合成",
            "RPG 组卡",
            "AI 对战",
            "🏆 锦标赛",
            "效果勘误",
            "⚔️ AI 对决",
            "🎬 对决还原"
    };

    private RpgCatalog catalog;
    private RpgRepository repository;
    private RpgProfile profile;
    private final PackOpeningEngine packEngine = new PackOpeningEngine();

    private ScrollView scrollRpgBody;
    private TextView tvTokens;
    private TextView tvDust;
    private TextView tvTimelineDate;
    private TextView tvTimelineDigestBanner;
    private Button btnTimelineDigest;
    private Button btnActiveBanlist;
    private TextView tvActiveBanlist;
    private TextView tvActiveRule;
    private Button btnPrevMilestone;
    private TextView tvCurrentMilestone;
    private Button btnNextMilestone;
    private List<String> timelineEventDates = new ArrayList<>();
    private LinearLayout layoutTabActions;
    private LinearLayout layoutContent;
    private RecyclerView rvNavTabs;
    private RpgNavTabAdapter navTabAdapter;
    private List<Integer> tabOrder = new ArrayList<>();

    private int activeTab = 0;
    private boolean collectionOnlyOwned = false;
    private String canonFilterMode = "ALL";
    private String showdownKeyA = "current";
    private String showdownKeyB = null;
    private RpgAiAutoDuelModels.BatchResult lastShowdownReport = null;
    private RoundRobinLeagueEngine.LeagueResult lastLeagueResult = null;
    private boolean ladderIncludeActiveDeck = true;
    private final java.util.Set<String> selectedLadderSavedDeckIds = new java.util.LinkedHashSet<>();
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

        RpgDiagLog.init(this);
        RpgDiagLog.log("Home", "onCreate intentTab=" + (getIntent() != null ? getIntent().getIntExtra(EXTRA_INITIAL_TAB, -1) : -1));

        scrollRpgBody = findViewById(R.id.scroll_rpg_body);
        tvTokens = findViewById(R.id.tv_rpg_tokens);
        tvDust = findViewById(R.id.tv_rpg_dust);
        tvTimelineDate = findViewById(R.id.tv_timeline_date);
        tvTimelineDigestBanner = findViewById(R.id.tv_timeline_digest_banner);
        btnTimelineDigest = findViewById(R.id.btn_timeline_digest);
        btnActiveBanlist = findViewById(R.id.btn_active_banlist);
        tvActiveBanlist = findViewById(R.id.tv_active_banlist);
        tvActiveRule = findViewById(R.id.tv_active_rule);
        btnPrevMilestone = findViewById(R.id.btn_prev_milestone);
        tvCurrentMilestone = findViewById(R.id.tv_current_milestone);
        btnNextMilestone = findViewById(R.id.btn_next_milestone);
        layoutTabActions = findViewById(R.id.layout_tab_actions);
        layoutContent = findViewById(R.id.layout_rpg_content);
        rvNavTabs = findViewById(R.id.rv_rpg_nav_tabs);

        timelineEventDates = TimelineEngine.getTimelineEventDates(catalog, AiDeckGenerator.getDemoOpponents());

        findViewById(R.id.btn_rpg_back).setOnClickListener(v -> {
            RpgDiagLog.log("Home", "btn_rpg_back -> finish()");
            finish();
        });
        View diagLogBtn = findViewById(R.id.btn_rpg_diag_log);
        if (diagLogBtn != null) {
            diagLogBtn.setOnClickListener(v -> openDiagLogDialog());
        }
        findViewById(R.id.btn_pick_date).setOnClickListener(v -> openDatePicker());
        tvCurrentMilestone.setOnClickListener(v -> openDatePicker());
        btnPrevMilestone.setOnClickListener(v -> {
            String prev = TimelineEngine.getPreviousEventDate(timelineEventDates, profile.currentTimelineDate);
            if (prev != null) selectTimelineDate(prev);
        });
        btnNextMilestone.setOnClickListener(v -> {
            String next = TimelineEngine.getNextEventDate(timelineEventDates, profile.currentTimelineDate);
            if (next != null) selectTimelineDate(next);
        });
        btnTimelineDigest.setOnClickListener(v -> openTimelineDigestDialog());
        tvTimelineDigestBanner.setOnClickListener(v -> openTimelineDigestDialog());
        btnActiveBanlist.setOnClickListener(v -> openActiveBanlistDialog());
        tvActiveBanlist.setOnClickListener(v -> openActiveBanlistDialog());

        tabOrder = profile.getNormalizedTabOrder(TAB_COUNT);
        activeTab = tabOrder.isEmpty() ? TAB_SHOP : tabOrder.get(0);
        setupDraggableNavTabs();

        RpgSessionManager.setCachedCatalog(catalog);
        currentTimelineDigest = TimelineEngine.buildDigestAtDate(
                catalog, AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        applyInitialTabFromIntent(getIntent());
        refreshAll();
    }

    private void openDiagLogDialog() {
        RpgDiagLog.init(this);
        final String logText = RpgDiagLog.getRecentLogsText(200);
        final String filePath = RpgDiagLog.getLogFilePath();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, dp(8), pad, dp(4));

        TextView pathTv = new TextView(this);
        pathTv.setText("日志文件：" + filePath);
        pathTv.setTextColor(Color.parseColor("#38BDF8"));
        pathTv.setTextSize(11f);
        pathTv.setPadding(0, 0, 0, dp(8));
        root.addView(pathTv);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(340));
        scroll.setLayoutParams(slp);
        scroll.setBackgroundColor(Color.parseColor("#0B1120"));

        TextView logTv = new TextView(this);
        logTv.setText(logText);
        logTv.setTextColor(Color.parseColor("#E2E8F0"));
        logTv.setTextSize(10.5f);
        logTv.setTypeface(android.graphics.Typeface.MONOSPACE);
        logTv.setTextIsSelectable(true);
        logTv.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(logTv);
        root.addView(scroll);

        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));

        new AlertDialog.Builder(this)
                .setTitle("📋 RPG 诊断日志（最近 200 条）")
                .setView(root)
                .setPositiveButton("一键复制", (d, w) -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        String payload = "=== RPG Diag Log ===\nPath: " + filePath + "\n\n" + logText;
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("rpg_debug_log", payload));
                        Toast.makeText(this, "已复制最近诊断日志到剪贴板，直接粘贴发送即可", Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("清空日志", (d, w) -> {
                    RpgDiagLog.clear();
                    Toast.makeText(this, "已清空诊断日志", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void setupDraggableNavTabs() {
        rvNavTabs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        navTabAdapter = new RpgNavTabAdapter();
        rvNavTabs.setAdapter(navTabAdapter);

        final boolean[] orderChanged = {false};
        ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION
                        || fromPos == toPos) {
                    return false;
                }
                if (fromPos < toPos) {
                    for (int i = fromPos; i < toPos; i++) {
                        Collections.swap(tabOrder, i, i + 1);
                    }
                } else {
                    for (int i = fromPos; i > toPos; i--) {
                        Collections.swap(tabOrder, i, i - 1);
                    }
                }
                navTabAdapter.notifyItemMoved(fromPos, toPos);
                orderChanged[0] = true;
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Swipe disabled
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder instanceof RpgNavTabAdapter.TabVH) {
                    orderChanged[0] = false;
                    if (scrollRpgBody != null) {
                        scrollRpgBody.requestDisallowInterceptTouchEvent(true);
                    }
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    ((RpgNavTabAdapter.TabVH) viewHolder).setDragging(true);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (scrollRpgBody != null) {
                    scrollRpgBody.requestDisallowInterceptTouchEvent(false);
                }
                if (viewHolder instanceof RpgNavTabAdapter.TabVH) {
                    ((RpgNavTabAdapter.TabVH) viewHolder).setDragging(false);
                }
                if (orderChanged[0]) {
                    orderChanged[0] = false;
                    profile.setTabOrder(tabOrder, TAB_COUNT);
                    saveProfileSafe();
                    Toast.makeText(RpgHomeActivity.this,
                            "✔ 已保存标签页顺序（长按标签可随时拖动调整）", Toast.LENGTH_SHORT).show();
                }
            }
        };
        new ItemTouchHelper(dragCallback).attachToRecyclerView(rvNavTabs);
    }

    private void selectTabById(int tabId) {
        if (tabId < 0 || tabId >= TAB_COUNT) return;
        activeTab = tabId;
        if (navTabAdapter != null) {
            navTabAdapter.notifyDataSetChanged();
        }
        int visualPos = tabOrder.indexOf(tabId);
        if (rvNavTabs != null && visualPos >= 0) {
            final int targetPos = visualPos;
            rvNavTabs.post(() -> rvNavTabs.smoothScrollToPosition(targetPos));
        }
        if (layoutTabActions != null && layoutContent != null) {
            renderCurrentTab();
        }
    }

    private class RpgNavTabAdapter extends RecyclerView.Adapter<RpgNavTabAdapter.TabVH> {
        @NonNull
        @Override
        public TabVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
            root.setMinimumWidth(dp(72));

            TextView tvLabel = new TextView(parent.getContext());
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f);
            tvLabel.setLayoutParams(textLp);
            tvLabel.setGravity(Gravity.CENTER);
            tvLabel.setPadding(dp(12), 0, dp(12), 0);
            tvLabel.setSingleLine(true);
            tvLabel.setTextSize(13f);
            root.addView(tvLabel);

            View indicator = new View(parent.getContext());
            LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            indicator.setLayoutParams(indLp);
            root.addView(indicator);

            return new TabVH(root, tvLabel, indicator);
        }

        @Override
        public void onBindViewHolder(@NonNull TabVH holder, int position) {
            final int tabId = tabOrder.get(position);
            boolean selected = (tabId == activeTab);
            holder.tvLabel.setText(tabId >= 0 && tabId < TAB_TITLES.length ? TAB_TITLES[tabId] : "");
            holder.tvLabel.setTextColor(Color.parseColor(selected ? "#F8FAFC" : "#94A3B8"));
            holder.tvLabel.setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            holder.indicator.setBackgroundColor(selected
                    ? Color.parseColor("#F59E0B") : Color.TRANSPARENT);
            holder.setDragging(false);
            holder.itemView.setOnClickListener(v -> selectTabById(tabId));
        }

        @Override
        public int getItemCount() {
            return tabOrder.size();
        }

        class TabVH extends RecyclerView.ViewHolder {
            final TextView tvLabel;
            final View indicator;

            TabVH(@NonNull View itemView, TextView tvLabel, View indicator) {
                super(itemView);
                this.tvLabel = tvLabel;
                this.indicator = indicator;
            }

            void setDragging(boolean dragging) {
                if (dragging) {
                    GradientDrawable dragBg = new GradientDrawable();
                    dragBg.setColor(Color.parseColor("#334155"));
                    dragBg.setCornerRadius(dp(6));
                    dragBg.setStroke(dp(1), Color.parseColor("#F59E0B"));
                    itemView.setBackground(dragBg);
                    itemView.setAlpha(0.95f);
                    itemView.setScaleX(1.06f);
                    itemView.setScaleY(1.06f);
                    itemView.setElevation(dp(6));
                } else {
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                    itemView.setAlpha(1.0f);
                    itemView.setScaleX(1.0f);
                    itemView.setScaleY(1.0f);
                    itemView.setElevation(0f);
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyInitialTabFromIntent(intent);
    }

    private void applyInitialTabFromIntent(Intent intent) {
        if (intent == null) return;
        int requested = intent.getIntExtra(EXTRA_INITIAL_TAB, -1);
        if (requested >= 0 && requested < TAB_COUNT) {
            selectTabById(requested);
            intent.removeExtra(EXTRA_INITIAL_TAB);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null) {
            profile = repository.loadProfile();
            tabOrder = profile.getNormalizedTabOrder(TAB_COUNT);
            if (navTabAdapter != null) {
                navTabAdapter.notifyDataSetChanged();
            }
        }

        // Sync back deck edits made in YGOMobile's native DeckEditor
        RpgSessionManager.EditedDeckResult editedDeck = RpgSessionManager.consumePendingEditedDeck();
        if (editedDeck != null && catalog != null) {
            profile.activeDeckMain = new ArrayList<>(editedDeck.main);
            profile.activeDeckExtra = new ArrayList<>(editedDeck.extra);
            saveProfileSafe();
            selectTabById(TAB_DECK);

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
            selectTabById(TAB_TOURNAMENT);
        }
        final RpgSessionManager.SavedLiveReplay liveReplay = RpgSessionManager.consumeLastLiveReplay();
        final RpgSessionManager.CanonDuelOutcome canonOutcome = RpgSessionManager.consumeLastCanonDuelOutcome();
        Runnable showSettlementPopup = null;
        if (settlement != null) {
            String extraMsg = (tournamentOutcome != null && activeBracket != null && activeBracket.finished)
                    ? ("\n\n🏆 锦标赛完赛：" + activeBracket.playerPlacementLabel
                    + "（额外奖金 +" + activeBracket.rewardTokens + " 代币）")
                    : "";
            String bodyMsg = "获得代币：+" + settlement.tokensEarned
                    + "\n当前代币余额：" + profile.tokens + extraMsg
                    + (liveReplay != null ? "\n\n（已保存本局录像，可直接点下方「回放」或「日志」）" : "");
            Runnable onReplay = liveReplay != null ? () -> launchLiveReplay(liveReplay) : null;
            Runnable onCopyLog = liveReplay != null ? () -> copyReplayFileLog(liveReplay.replayFilePath) : null;
            String titleStr = "RPG 决斗结算 · " + settlement.outcomeLabel;
            showSettlementPopup = () -> showDuelSettlementDialog(titleStr, bodyMsg, onReplay, onCopyLog);
        } else if (liveReplay != null) {
            String pInfo = (liveReplay.player0Name != null && liveReplay.player1Name != null)
                    ? (liveReplay.player0Name + " vs " + liveReplay.player1Name + "\n")
                    : "";
            showSettlementPopup = () -> showDuelSettlementDialog(
                    "🎬 对局录像已保存",
                    pInfo + "可直接点下方「回放」重温本局，或点「日志」一键复制双方策略、卡组与完整操作日志。",
                    () -> launchLiveReplay(liveReplay),
                    () -> copyReplayFileLog(liveReplay.replayFilePath));
        }

        OcgCanonDuel finishedCanonDuel = null;
        if (canonOutcome != null && catalog != null) {
            for (OcgCanonDuel cd : catalog.getCanonDuels()) {
                if (cd != null && canonOutcome.duelId != null && canonOutcome.duelId.equals(cd.id)) {
                    boolean hasOutro = (cd.outroSideAWin != null && !cd.outroSideAWin.isEmpty())
                            || (cd.outroSideBWin != null && !cd.outroSideBWin.isEmpty())
                            || (cd.outroDialogue != null && !cd.outroDialogue.isEmpty());
                    if (hasOutro || canonOutcome.isDraw) {
                        finishedCanonDuel = cd;
                    }
                    break;
                }
            }
        }
        if (finishedCanonDuel != null) {
            CanonStoryDialog.showOutcomeOutro(
                    this,
                    finishedCanonDuel,
                    canonOutcome.canonMode,
                    canonOutcome.playerWon,
                    canonOutcome.isDraw,
                    showSettlementPopup);
        } else if (showSettlementPopup != null) {
            showSettlementPopup.run();
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
        updateMilestoneStepper();
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

    private void updateMilestoneStepper() {
        if (tvCurrentMilestone == null || btnPrevMilestone == null || btnNextMilestone == null) return;
        String cur = profile.currentTimelineDate;
        int total = timelineEventDates.size();
        int idx = timelineEventDates.indexOf(cur);
        String label = idx >= 0
                ? ("📅 " + cur + " (" + (idx + 1) + "/" + total + ")")
                : ("📅 " + cur);
        tvCurrentMilestone.setText(label);

        GradientDrawable labelBg = new GradientDrawable();
        labelBg.setColor(Color.parseColor("#0F172A"));
        labelBg.setCornerRadius(dp(6));
        labelBg.setStroke(dp(1), Color.parseColor("#D97706"));
        tvCurrentMilestone.setBackground(labelBg);

        boolean hasPrev = TimelineEngine.getPreviousEventDate(timelineEventDates, cur) != null;
        boolean hasNext = TimelineEngine.getNextEventDate(timelineEventDates, cur) != null;
        btnPrevMilestone.setEnabled(hasPrev);
        btnPrevMilestone.setAlpha(hasPrev ? 1.0f : 0.4f);
        btnNextMilestone.setEnabled(hasNext);
        btnNextMilestone.setAlpha(hasNext ? 1.0f : 0.4f);
    }

    /**
     * Opens a custom calendar dialog with `<` [Month ▼] [Year ▼] `>` dropdown controls,
     * a Monday-first 7-column calendar grid, light-green circular badges for dates with OCG events,
     * special rose-pink `🎬` badges for dates with Canon Duels,
     * and strict click enforcement so ONLY event dates can be selected.
     */
    private void openDatePicker() {
        final List<AiOpponent> allOpponents = AiDeckGenerator.getDemoOpponents();
        if (timelineEventDates == null || timelineEventDates.isEmpty()) {
            timelineEventDates = TimelineEngine.getTimelineEventDates(catalog, allOpponents);
        }
        final java.util.Set<String> eventDateSet = new java.util.HashSet<>(timelineEventDates);
        final java.util.Set<String> canonDateSet = TimelineEngine.getCanonDuelDates(catalog);

        int minYear = 1999;
        int maxYear = 1999;
        try {
            if (catalog.minTimelineDate != null && catalog.minTimelineDate.length() >= 4) {
                minYear = Integer.parseInt(catalog.minTimelineDate.substring(0, 4));
            }
            if (catalog.maxTimelineDate != null && catalog.maxTimelineDate.length() >= 4) {
                maxYear = Integer.parseInt(catalog.maxTimelineDate.substring(0, 4));
            }
        } catch (Exception ignored) {}
        if (maxYear < minYear) maxYear = minYear;
        final int finalMinYear = minYear;
        final int finalMaxYear = maxYear;

        int initYear = finalMinYear;
        int initMonth = 2; // 1..12
        try {
            String[] parts = profile.currentTimelineDate.split("-");
            initYear = Integer.parseInt(parts[0]);
            initMonth = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        if (initYear < finalMinYear) initYear = finalMinYear;
        if (initYear > finalMaxYear) initYear = finalMaxYear;
        if (initMonth < 1 || initMonth > 12) initMonth = 1;

        final int[] curYearRef = new int[]{initYear};
        final int[] curMonthRef = new int[]{initMonth}; // 1..12
        final boolean[] updatingSpinners = new boolean[]{false};

        android.widget.ScrollView outerScroll = new android.widget.ScrollView(this);
        outerScroll.setBackgroundColor(Color.parseColor("#0F172A"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(10));
        outerScroll.addView(root);

        // ==================== TOP BAR: <  [Month ▼]  [Year ▼]  > ====================
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.setPadding(0, 0, 0, dp(10));

        final Button btnPrevMonth = new Button(this);
        btnPrevMonth.setText("‹");
        btnPrevMonth.setTextSize(18f);
        btnPrevMonth.setTextColor(Color.parseColor("#F8FAFC"));
        btnPrevMonth.setMinWidth(0);
        btnPrevMonth.setMinHeight(0);
        btnPrevMonth.setPadding(0, 0, 0, 0);
        GradientDrawable navBtnBg = new GradientDrawable();
        navBtnBg.setColor(Color.parseColor("#1E293B"));
        navBtnBg.setCornerRadius(dp(6));
        navBtnBg.setStroke(dp(1), Color.parseColor("#475569"));
        btnPrevMonth.setBackground(navBtnBg);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dp(34), dp(36));
        prevLp.setMarginEnd(dp(6));
        btnPrevMonth.setLayoutParams(prevLp);
        navRow.addView(btnPrevMonth);

        final android.widget.Spinner spMonth = new android.widget.Spinner(this);
        LinearLayout.LayoutParams spMonthLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        spMonthLp.setMarginEnd(dp(6));
        spMonth.setLayoutParams(spMonthLp);
        GradientDrawable spBg1 = new GradientDrawable();
        spBg1.setColor(Color.parseColor("#1E293B"));
        spBg1.setCornerRadius(dp(8));
        spBg1.setStroke(dp(1), Color.parseColor("#475569"));
        spMonth.setBackground(spBg1);
        navRow.addView(spMonth);

        final android.widget.Spinner spYear = new android.widget.Spinner(this);
        LinearLayout.LayoutParams spYearLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        spYearLp.setMarginEnd(dp(6));
        spYear.setLayoutParams(spYearLp);
        GradientDrawable spBg2 = new GradientDrawable();
        spBg2.setColor(Color.parseColor("#1E293B"));
        spBg2.setCornerRadius(dp(8));
        spBg2.setStroke(dp(1), Color.parseColor("#475569"));
        spYear.setBackground(spBg2);
        navRow.addView(spYear);

        final Button btnNextMonth = new Button(this);
        btnNextMonth.setText("›");
        btnNextMonth.setTextSize(18f);
        btnNextMonth.setTextColor(Color.parseColor("#F8FAFC"));
        btnNextMonth.setMinWidth(0);
        btnNextMonth.setMinHeight(0);
        btnNextMonth.setPadding(0, 0, 0, 0);
        GradientDrawable navBtnBg2 = new GradientDrawable();
        navBtnBg2.setColor(Color.parseColor("#1E293B"));
        navBtnBg2.setCornerRadius(dp(6));
        navBtnBg2.setStroke(dp(1), Color.parseColor("#475569"));
        btnNextMonth.setBackground(navBtnBg2);
        btnNextMonth.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(36)));
        navRow.addView(btnNextMonth);

        root.addView(navRow);

        // Populate Year dropdown (minYear .. maxYear)
        final List<Integer> yearValues = new ArrayList<>();
        final List<String> yearLabels = new ArrayList<>();
        for (int y = finalMinYear; y <= finalMaxYear; y++) {
            yearValues.add(y);
            yearLabels.add(y + "年 ▾");
        }
        android.widget.ArrayAdapter<String> yearAdapter = new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, yearLabels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.parseColor("#F8FAFC"));
                tv.setTextSize(13f);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(6), dp(4), dp(6), dp(4));
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                int y = yearValues.get(position);
                boolean isSel = (y == curYearRef[0]);
                tv.setBackgroundColor(Color.parseColor(isSel ? "#1E3A5F" : "#1E293B"));
                tv.setTextColor(Color.parseColor(isSel ? "#86EFAC" : "#F8FAFC"));
                tv.setText(y + "年" + (isSel ? "  ✓" : ""));
                tv.setTextSize(13.5f);
                tv.setPadding(dp(14), dp(10), dp(14), dp(10));
                return tv;
            }
        };
        spYear.setAdapter(yearAdapter);

        // Month dropdown labels dynamically mark months that contain OCG events & 🎬 Canon Duels
        final List<String> monthLabels = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthLabels.add(m + "月 ▾");
        }
        final android.widget.ArrayAdapter<String> monthAdapter = new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, monthLabels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.parseColor("#F8FAFC"));
                tv.setTextSize(13f);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(6), dp(4), dp(6), dp(4));
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                int m = position + 1;
                String ymPrefix = String.format(Locale.US, "%04d-%02d-", curYearRef[0], m);
                int evCount = 0;
                int canonCount = 0;
                for (String d : timelineEventDates) {
                    if (d.startsWith(ymPrefix)) {
                        evCount++;
                        if (canonDateSet.contains(d)) canonCount++;
                    }
                }
                boolean isSel = (m == curMonthRef[0]);
                tv.setBackgroundColor(Color.parseColor(isSel ? "#1E3A5F" : "#1E293B"));
                tv.setTextColor(Color.parseColor(canonCount > 0 ? "#FBCFE8" : (evCount > 0 ? "#86EFAC" : "#94A3B8")));
                String canonSuffix = canonCount > 0 ? " 🎬" : "";
                tv.setText(m + "月" + (evCount > 0 ? ("  ●(" + evCount + ")" + canonSuffix) : "  (无事件)") + (isSel ? "  ✓" : ""));
                tv.setTextSize(13f);
                tv.setPadding(dp(14), dp(10), dp(14), dp(10));
                return tv;
            }
        };
        spMonth.setAdapter(monthAdapter);

        // ==================== WEEKDAY HEADER: 一 二 三 四 五 六 日 ====================
        String[] weekHeaders = {"一", "二", "三", "四", "五", "六", "日"};
        LinearLayout weekRow = new LinearLayout(this);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        weekRow.setPadding(0, dp(2), 0, dp(6));
        for (String wh : weekHeaders) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setGravity(Gravity.CENTER);
            tv.setText(wh);
            tv.setTextColor(Color.parseColor("#94A3B8"));
            tv.setTextSize(12f);
            weekRow.addView(tv);
        }
        root.addView(weekRow);

        // ==================== CALENDAR MONTH GRID ====================
        final LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(gridContainer);

        // ==================== LEGEND & MONTH EVENTS PREVIEW ====================
        TextView tvLegend = makeSubtleText("🎬 粉框图标：含对决还原   🟢 浅绿圆：其他 OCG 事件   🟠 金框：当前日   ⚪ 灰字：无事件");
        tvLegend.setPadding(dp(2), dp(8), dp(2), dp(4));
        tvLegend.setTextSize(11f);
        tvLegend.setTextColor(Color.parseColor("#94A3B8"));
        root.addView(tvLegend);

        final TextView tvMonthSummary = makeSubtleText("");
        tvMonthSummary.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvMonthSummary.setTextSize(11.5f);
        GradientDrawable sumBg = new GradientDrawable();
        sumBg.setColor(Color.parseColor("#1E293B"));
        sumBg.setCornerRadius(dp(6));
        tvMonthSummary.setBackground(sumBg);
        root.addView(tvMonthSummary);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("📅 移动 OCG 时间点")
                .setView(outerScroll)
                .setNegativeButton("关闭", null)
                .create();

        final Runnable[] renderCalendarRef = new Runnable[1];
        renderCalendarRef[0] = () -> {
            int year = curYearRef[0];
            int month = curMonthRef[0]; // 1..12

            updatingSpinners[0] = true;
            int yPos = yearValues.indexOf(year);
            if (yPos >= 0 && spYear.getSelectedItemPosition() != yPos) {
                spYear.setSelection(yPos);
            }
            if (spMonth.getSelectedItemPosition() != month - 1) {
                spMonth.setSelection(month - 1);
            }
            updatingSpinners[0] = false;

            boolean canPrev = (year > finalMinYear) || (month > 1);
            boolean canNext = (year < finalMaxYear) || (month < 12);
            btnPrevMonth.setEnabled(canPrev);
            btnPrevMonth.setAlpha(canPrev ? 1.0f : 0.35f);
            btnNextMonth.setEnabled(canNext);
            btnNextMonth.setAlpha(canNext ? 1.0f : 0.35f);

            gridContainer.removeAllViews();

            Calendar cal = Calendar.getInstance(Locale.US);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1);
            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int dow = cal.get(Calendar.DAY_OF_WEEK); // SUNDAY=1 .. SATURDAY=7
            int leading = (dow + 5) % 7; // Monday-first offset: Mon=0 .. Sun=6

            Calendar prevCal = (Calendar) cal.clone();
            prevCal.add(Calendar.MONTH, -1);
            int prevMonthDays = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH);

            int totalCells = ((leading + daysInMonth + 6) / 7) * 7;
            int rows = totalCells / 7;
            int cellSize = dp(36);

            List<String> monthEvents = new ArrayList<>();

            for (int r = 0; r < rows; r++) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setPadding(0, dp(3), 0, dp(3));

                for (int c = 0; c < 7; c++) {
                    int cellIdx = r * 7 + c;
                    int dayNum = cellIdx - leading + 1;

                    LinearLayout cellBox = new LinearLayout(this);
                    cellBox.setOrientation(LinearLayout.VERTICAL);
                    cellBox.setGravity(Gravity.CENTER);
                    cellBox.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    if (dayNum < 1) {
                        // Overflow day from previous month
                        TextView dayTv = new TextView(this);
                        dayTv.setGravity(Gravity.CENTER);
                        dayTv.setLayoutParams(new LinearLayout.LayoutParams(cellSize, cellSize));
                        dayTv.setTextSize(13f);
                        dayTv.setText(String.valueOf(prevMonthDays + dayNum));
                        dayTv.setTextColor(Color.parseColor("#334155"));
                        cellBox.addView(dayTv);
                    } else if (dayNum > daysInMonth) {
                        // Overflow day from next month
                        TextView dayTv = new TextView(this);
                        dayTv.setGravity(Gravity.CENTER);
                        dayTv.setLayoutParams(new LinearLayout.LayoutParams(cellSize, cellSize));
                        dayTv.setTextSize(13f);
                        dayTv.setText(String.valueOf(dayNum - daysInMonth));
                        dayTv.setTextColor(Color.parseColor("#334155"));
                        cellBox.addView(dayTv);
                    } else {
                        final String dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, dayNum);
                        boolean hasEvent = eventDateSet.contains(dateStr);
                        boolean hasCanonDuel = canonDateSet.contains(dateStr);
                        boolean isCurrent = dateStr.equals(profile.currentTimelineDate);

                        if (hasEvent && hasCanonDuel) {
                            monthEvents.add(dateStr);
                            // Special rose-pink rounded badge with 🎬 icon for dates containing Canon Duels
                            LinearLayout canonBadge = new LinearLayout(this);
                            canonBadge.setOrientation(LinearLayout.VERTICAL);
                            canonBadge.setGravity(Gravity.CENTER);
                            canonBadge.setLayoutParams(new LinearLayout.LayoutParams(cellSize, cellSize));
                            GradientDrawable badgeBg = new GradientDrawable();
                            badgeBg.setShape(GradientDrawable.RECTANGLE);
                            badgeBg.setCornerRadius(dp(10));
                            if (isCurrent) {
                                badgeBg.setColor(Color.parseColor("#FBCFE8"));
                                badgeBg.setStroke(dp(2), Color.parseColor("#F59E0B"));
                            } else {
                                badgeBg.setColor(Color.parseColor("#FCE7F3"));
                                badgeBg.setStroke(dp(2), Color.parseColor("#EC4899"));
                            }
                            canonBadge.setBackground(badgeBg);

                            TextView numTv = new TextView(this);
                            numTv.setText(String.valueOf(dayNum));
                            numTv.setTextSize(12f);
                            numTv.setTypeface(null, android.graphics.Typeface.BOLD);
                            numTv.setTextColor(Color.parseColor("#831843"));
                            numTv.setIncludeFontPadding(false);
                            numTv.setGravity(Gravity.CENTER);
                            canonBadge.addView(numTv);

                            TextView iconTv = new TextView(this);
                            iconTv.setText("🎬");
                            iconTv.setTextSize(9.5f);
                            iconTv.setIncludeFontPadding(false);
                            iconTv.setGravity(Gravity.CENTER);
                            canonBadge.addView(iconTv);

                            cellBox.addView(canonBadge);
                            cellBox.setClickable(true);
                            cellBox.setFocusable(true);
                            cellBox.setOnClickListener(v -> {
                                dialog.dismiss();
                                selectTimelineDate(dateStr);
                            });
                        } else {
                            TextView dayTv = new TextView(this);
                            dayTv.setGravity(Gravity.CENTER);
                            dayTv.setLayoutParams(new LinearLayout.LayoutParams(cellSize, cellSize));
                            dayTv.setTextSize(13f);
                            dayTv.setText(String.valueOf(dayNum));
                            if (hasEvent) {
                                monthEvents.add(dateStr);
                                GradientDrawable circle = new GradientDrawable();
                                circle.setShape(GradientDrawable.OVAL);
                                if (isCurrent) {
                                    // Current active date: bright light-green fill + amber gold ring
                                    circle.setColor(Color.parseColor("#86EFAC"));
                                    circle.setStroke(dp(2), Color.parseColor("#F59E0B"));
                                    dayTv.setTextColor(Color.parseColor("#052E16"));
                                } else {
                                    // Event date: light-green circle
                                    circle.setColor(Color.parseColor("#BBF7D0"));
                                    circle.setStroke(dp(1), Color.parseColor("#22C55E"));
                                    dayTv.setTextColor(Color.parseColor("#052E16"));
                                }
                                dayTv.setTypeface(null, android.graphics.Typeface.BOLD);
                                dayTv.setBackground(circle);
                                cellBox.setClickable(true);
                                cellBox.setFocusable(true);
                                cellBox.setOnClickListener(v -> {
                                    dialog.dismiss();
                                    selectTimelineDate(dateStr);
                                });
                            } else if (isCurrent) {
                                // Legacy non-event saved date: faint amber outline only, non-clickable
                                GradientDrawable ring = new GradientDrawable();
                                ring.setShape(GradientDrawable.OVAL);
                                ring.setColor(Color.TRANSPARENT);
                                ring.setStroke(dp(1), Color.parseColor("#F59E0B"));
                                dayTv.setBackground(ring);
                                dayTv.setTextColor(Color.parseColor("#FDE68A"));
                            } else {
                                // Non-event date: muted gray, strictly non-clickable
                                dayTv.setTextColor(Color.parseColor("#64748B"));
                            }
                            cellBox.addView(dayTv);
                        }

                        // Subtle indicator dot under current date
                        View dot = new View(this);
                        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(4), dp(4));
                        dotLp.topMargin = dp(2);
                        dot.setLayoutParams(dotLp);
                        if (isCurrent) {
                            GradientDrawable dotBg = new GradientDrawable();
                            dotBg.setShape(GradientDrawable.OVAL);
                            dotBg.setColor(Color.parseColor("#F59E0B"));
                            dot.setBackground(dotBg);
                            dot.setVisibility(View.VISIBLE);
                        } else {
                            dot.setVisibility(View.INVISIBLE);
                        }
                        cellBox.addView(dot);
                    }
                    rowLayout.addView(cellBox);
                }
                gridContainer.addView(rowLayout);
            }

            if (monthEvents.isEmpty()) {
                tvMonthSummary.setText(year + "年" + month + "月无 OCG 历史事件，请通过上方下拉菜单切换至带绿点 ● 或 🎬 的月份。");
                tvMonthSummary.setTextColor(Color.parseColor("#94A3B8"));
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(year).append("年").append(month).append("月事件（").append(monthEvents.size()).append("日）：");
                for (int i = 0; i < monthEvents.size(); i++) {
                    String ed = monthEvents.get(i);
                    TimelineEngine.TimelineDigest d = TimelineEngine.buildDigestAtDate(catalog, allOpponents, ed);
                    String raw = d.buildShortSummary();
                    String pfx = "📰 " + ed + "：";
                    String clean = raw.startsWith(pfx) ? raw.substring(pfx.length()) : raw;
                    String bullet = canonDateSet.contains(ed) ? "\n🎬 " : "\n• ";
                    sb.append(bullet).append(ed.substring(5)).append("：").append(clean);
                }
                tvMonthSummary.setText(sb.toString());
                tvMonthSummary.setTextColor(Color.parseColor("#BBF7D0"));
            }
        };

        btnPrevMonth.setOnClickListener(v -> {
            if (curMonthRef[0] > 1) {
                curMonthRef[0]--;
            } else if (curYearRef[0] > finalMinYear) {
                curYearRef[0]--;
                curMonthRef[0] = 12;
            }
            renderCalendarRef[0].run();
        });

        btnNextMonth.setOnClickListener(v -> {
            if (curMonthRef[0] < 12) {
                curMonthRef[0]++;
            } else if (curYearRef[0] < finalMaxYear) {
                curYearRef[0]++;
                curMonthRef[0] = 1;
            }
            renderCalendarRef[0].run();
        });

        spYear.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinners[0]) return;
                curYearRef[0] = yearValues.get(position);
                renderCalendarRef[0].run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spMonth.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinners[0]) return;
                curMonthRef[0] = position + 1;
                renderCalendarRef[0].run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        renderCalendarRef[0].run();
        dialog.show();
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
            case 8:
                renderCanonDuelsTab();
                break;
        }
    }

    // ==================== TAB 0: SHOP & PACK OPENING ====================
    private void renderShopTab() {
        List<OcgPack> availPacks = new ArrayList<>(
                TimelineEngine.getAvailablePacks(catalog, profile.currentTimelineDate));
        java.util.Collections.reverse(availPacks);
        availPacks.sort((a, b) -> {
            String da = a.releaseDate != null ? a.releaseDate : "";
            String db = b.releaseDate != null ? b.releaseDate : "";
            return db.compareTo(da);
        });
        TextView hint = makeSubtleText("仅显示 " + profile.currentTimelineDate
                + " 已解锁的 " + availPacks.size() + " 款商品（按发售日降序 · 代币价 1:1 还原当年日元定价）");
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

            Rarity rarity = CraftingEngine.getCraftingRarity(catalog, card.id, profile.currentTimelineDate);
            int craftCost = CraftingEngine.getCraftCost(catalog, card.id, profile.currentTimelineDate);
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
        note.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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
        Button saveDeckBtn = makeActionButton("💾 保存卡组", "#0284C7");
        saveDeckBtn.setOnClickListener(v -> showSaveDeckDialog());
        Button loadDeckBtn = makeActionButton("📂 读取卡组", "#0D9488");
        loadDeckBtn.setOnClickListener(v -> showLoadDeckDialog());
        Button deleteDeckBtn = makeActionButton("🗑 删除卡组", "#B91C1C");
        deleteDeckBtn.setOnClickListener(v -> showDeleteDeckDialog());
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
        actionRow.addView(saveDeckBtn);
        actionRow.addView(loadDeckBtn);
        actionRow.addView(deleteDeckBtn);
        actionRow.addView(clearBtn);
        actionScroll.addView(actionRow);
        layoutTabActions.addView(actionScroll);

        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        LinearLayout statusCard = makeCardContainer();
        RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
        int savedCount = profile.savedDecks != null ? profile.savedDecks.size() : 0;
        TextView statusTitle = makeTitleText(
                "当前 RPG 卡组" + (activeSaved != null ? " [" + activeSaved.name + "]" : "")
                        + "：主卡组 " + profile.activeDeckMain.size()
                        + " 张 / 额外 " + profile.activeDeckExtra.size() + " 张 · "
                        + (val.valid ? "✔ 合规可出战" : "✘ 未通过校验"));
        statusTitle.setTextColor(Color.parseColor(val.valid ? "#34D399" : "#F87171"));
        statusCard.addView(statusTitle);
        statusCard.addView(makeSubtleText("💡 点击上方「🛠️ 原版卡组编辑器」即可可视化增删卡片（自动同步并校验）· 已保存槽位 " + savedCount + " 套，可通过上方菜单保存/读取/删除。"));
        if (!val.valid) {
            StringBuilder errs = new StringBuilder();
            for (String e : val.errors) {
                errs.append("• ").append(e).append("\n");
            }
            statusCard.addView(makeSubtleText(errs.toString().trim()));
        }
        layoutContent.addView(statusCard);

        // Reuse the same split-screen visual deck viewer used for AI opponent decks
        View deckVisualView = RpgPackVisualDialogs.createPlayerDeckEmbeddedView(this, catalog, profile);
        LinearLayout.LayoutParams dvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dvLp.bottomMargin = dp(10);
        deckVisualView.setLayoutParams(dvLp);
        layoutContent.addView(deckVisualView);
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
            bridge.openNativeDeckEditor(this, playerYdk.getAbsolutePath(), allowedCaps, banlist);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "导出卡组失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSaveDeckDialog() {
        final List<RpgProfile.SavedDeck> savedList = profile.savedDecks != null
                ? profile.savedDecks : java.util.Collections.emptyList();
        final RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
        final int nextIdx = savedList.size() + 1;

        // First-time save when no saved decks exist yet: keep simple direct input
        if (savedList.isEmpty()) {
            final EditText input = new EditText(this);
            input.setHint("例如：青眼强攻 / 封印大法师");
            input.setText("我的卡组 " + nextIdx);
            input.setSelection(input.getText().length());
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(16), dp(8), dp(16), 0);
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
            return;
        }

        // Multi-mode save dialog: pick an existing saved deck to overwrite (defaulting to last loaded/saved deck)
        // OR save as a new deck with custom name.
        final String[] selectedOverwriteId = new String[]{activeSaved != null ? activeSaved.id : null};

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(10));
        scroll.addView(body);

        TextView intro = makeSubtleText("选择要覆盖的已有卡组，或另存为新卡组"
                + (activeSaved != null ? ("（已默认选中上次读取的「" + activeSaved.name + "」）") : "") + "：");
        intro.setTextColor(Color.parseColor("#CBD5E1"));
        body.addView(intro);

        final List<android.widget.RadioButton> allRadios = new ArrayList<>();
        final Map<String, android.widget.RadioButton> overwriteRadios = new java.util.LinkedHashMap<>();
        final Map<String, LinearLayout> overwriteBoxes = new java.util.LinkedHashMap<>();
        final android.widget.RadioButton rbNew = new android.widget.RadioButton(this);
        final EditText inputNewName = new EditText(this);
        final AlertDialog[] dlgRef = new AlertDialog[1];

        final Runnable updateSelectionUi = () -> {
            String curTarget = selectedOverwriteId[0];
            for (Map.Entry<String, android.widget.RadioButton> e : overwriteRadios.entrySet()) {
                boolean sel = e.getKey().equals(curTarget);
                e.getValue().setChecked(sel);
                LinearLayout box = overwriteBoxes.get(e.getKey());
                if (box != null) {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.parseColor(sel ? "#1E3A5F" : "#1E293B"));
                    bg.setCornerRadius(dp(8));
                    bg.setStroke(dp(sel ? 2 : 1), Color.parseColor(sel ? "#F59E0B" : "#334155"));
                    box.setBackground(bg);
                }
            }
            boolean isNewMode = (curTarget == null);
            rbNew.setChecked(isNewMode);
            inputNewName.setAlpha(isNewMode ? 1.0f : 0.55f);
            if (!isNewMode) {
                inputNewName.clearFocus();
            }
            if (dlgRef[0] != null) {
                Button pos = dlgRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                if (pos != null) {
                    pos.setText(isNewMode ? "新建保存" : "覆盖保存");
                }
            }
        };

        // Section 1: Existing saved decks to overwrite
        TextView secExist = makeTitleText("覆盖已有卡组（" + savedList.size() + " 套）");
        secExist.setTextSize(13f);
        secExist.setTextColor(Color.parseColor("#FDE68A"));
        secExist.setPadding(0, dp(10), 0, dp(4));
        body.addView(secExist);

        for (final RpgProfile.SavedDeck sd : savedList) {
            if (sd == null || sd.id == null) continue;
            boolean isLastLoaded = sd.id.equals(profile.activeDeckId);
            int mSize = sd.main != null ? sd.main.size() : 0;
            int eSize = sd.extra != null ? sd.extra.size() : 0;

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(6);
            box.setLayoutParams(blp);

            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText((isLastLoaded ? "⭐ [上次读取] " : "") + sd.name);
            rb.setTextColor(Color.parseColor(isLastLoaded ? "#FDE68A" : "#F8FAFC"));
            rb.setTextSize(13f);
            rb.setOnClickListener(v -> {
                selectedOverwriteId[0] = sd.id;
                updateSelectionUi.run();
            });
            box.addView(rb);

            TextView tvMeta = makeSubtleText("主 " + mSize + " / 额外 " + eSize
                    + (sd.savedAtDate != null ? (" · 存自 " + sd.savedAtDate) : ""));
            tvMeta.setPadding(dp(28), dp(1), 0, 0);
            box.addView(tvMeta);

            box.setOnClickListener(v -> {
                selectedOverwriteId[0] = sd.id;
                updateSelectionUi.run();
            });

            allRadios.add(rb);
            overwriteRadios.put(sd.id, rb);
            overwriteBoxes.put(sd.id, box);
            body.addView(box);
        }

        // Section 2: Save as a brand-new deck
        TextView secNew = makeTitleText("另存为新卡组");
        secNew.setTextSize(13f);
        secNew.setTextColor(Color.parseColor("#38BDF8"));
        secNew.setPadding(0, dp(12), 0, dp(4));
        body.addView(secNew);

        LinearLayout newBox = new LinearLayout(this);
        newBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable newBg = new GradientDrawable();
        newBg.setColor(Color.parseColor("#1E293B"));
        newBg.setCornerRadius(dp(8));
        newBg.setStroke(dp(1), Color.parseColor("#334155"));
        newBox.setBackground(newBg);
        newBox.setPadding(dp(10), dp(8), dp(10), dp(10));

        rbNew.setText("➕ 输入新名称另存为新卡组");
        rbNew.setTextColor(Color.parseColor("#F8FAFC"));
        rbNew.setTextSize(13f);
        rbNew.setOnClickListener(v -> {
            selectedOverwriteId[0] = null;
            updateSelectionUi.run();
            inputNewName.requestFocus();
        });
        allRadios.add(rbNew);
        newBox.addView(rbNew);

        inputNewName.setHint("例如：青眼强攻 / 封印大法师（同名将覆盖）");
        inputNewName.setText("我的卡组 " + nextIdx);
        inputNewName.setTextColor(Color.parseColor("#F8FAFC"));
        inputNewName.setHintTextColor(Color.parseColor("#64748B"));
        inputNewName.setTextSize(13.5f);
        inputNewName.setSingleLine(true);
        LinearLayout.LayoutParams inpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inpLp.setMargins(dp(24), dp(4), dp(4), 0);
        inputNewName.setLayoutParams(inpLp);
        final boolean[] readyForFocusSwitch = new boolean[]{false};
        if (selectedOverwriteId[0] != null) {
            body.setFocusableInTouchMode(true);
            body.requestFocus();
        }
        inputNewName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && readyForFocusSwitch[0] && selectedOverwriteId[0] != null) {
                selectedOverwriteId[0] = null;
                updateSelectionUi.run();
            }
        });
        inputNewName.setOnClickListener(v -> {
            if (selectedOverwriteId[0] != null) {
                selectedOverwriteId[0] = null;
                updateSelectionUi.run();
            }
        });
        newBox.addView(inputNewName);
        body.addView(newBox);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("💾 保存当前卡组（主 " + profile.activeDeckMain.size() + " / 额外 " + profile.activeDeckExtra.size() + "）")
                .setView(scroll)
                .setPositiveButton(selectedOverwriteId[0] != null ? "覆盖保存" : "新建保存", (d, which) -> {
                    String targetId = selectedOverwriteId[0];
                    if (targetId != null) {
                        RpgProfile.SavedDeck target = profile.findSavedDeck(targetId);
                        String targetName = target != null ? target.name : "已存卡组";
                        if (profile.overwriteSavedDeck(targetId)) {
                            saveProfileSafe();
                            Toast.makeText(this, "已覆盖保存卡组「" + targetName + "」", Toast.LENGTH_SHORT).show();
                            refreshAll();
                        }
                    } else {
                        String name = inputNewName.getText().toString().trim();
                        RpgProfile.SavedDeck saved = profile.saveCurrentDeck(name);
                        saveProfileSafe();
                        Toast.makeText(this, "已保存卡组「" + saved.name + "」", Toast.LENGTH_SHORT).show();
                        refreshAll();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dlgRef[0] = dialog;
        dialog.show();
        if (selectedOverwriteId[0] != null) {
            inputNewName.clearFocus();
            body.requestFocus();
        }
        updateSelectionUi.run();
        body.post(() -> readyForFocusSwitch[0] = true);
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

    private void showDeleteDeckDialog() {
        List<RpgProfile.SavedDeck> savedList = profile.savedDecks;
        if (savedList == null || savedList.isEmpty()) {
            Toast.makeText(this, "暂无已保存卡组可删除", Toast.LENGTH_SHORT).show();
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
                .setTitle("🗑 选择要删除的已保存卡组（共 " + savedList.size() + " 套）")
                .setItems(labels, (dialog, which) -> {
                    final RpgProfile.SavedDeck sd = savedList.get(which);
                    if (sd == null) return;
                    new AlertDialog.Builder(this)
                            .setTitle("删除卡组")
                            .setMessage("确认删除已保存卡组「" + sd.name + "」？（不会清空当前编辑中的卡组）")
                            .setPositiveButton("删除", (dlg, w) -> {
                                if (profile.deleteSavedDeck(sd.id)) {
                                    saveProfileSafe();
                                    Toast.makeText(this, "已删除卡组「" + sd.name + "」", Toast.LENGTH_SHORT).show();
                                    refreshAll();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== TAB 4: AI DUEL ====================
    private void renderAiDuelTab() {
        List<AiOpponent> opponents = new ArrayList<>(TimelineEngine.getAvailableOpponents(
                AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate));
        final String curDate = profile.currentTimelineDate;
        Collections.sort(opponents, (a, b) -> {
            int cmp = Integer.compare(a.getWinRewardTokens(curDate), b.getWinRewardTokens(curDate));
            if (cmp != 0) return cmp;
            cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.RewardTriple ca =
                    cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve(a.id, curDate);
            cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.RewardTriple cb =
                    cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve(b.id, curDate);
            int ea = ca != null ? ca.benchmarkElo : 1500;
            int eb = cb != null ? cb.benchmarkElo : 1500;
            cmp = Integer.compare(ea, eb);
            if (cmp != 0) return cmp;
            return a.unlockDate.compareTo(b.unlockDate);
        });
        layoutTabActions.addView(makeSubtleText("战绩：" + profile.wins + " 胜 " + profile.losses + " 负 " + profile.draws
                + " 平 · 当前时间轴已解锁 " + opponents.size() + " 位对手（按当前环境实测天梯 Elo 与奖金由低到高排列）"));

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

            cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.RewardTriple cal =
                    cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve(opp.id, curDate);
            String benchPrefix = cal != null
                    ? String.format(Locale.US, "环境实测：[%s级 · Elo %d] · ", cal.getTierLabel(), cal.benchmarkElo)
                    : "";
            TextView rewardTv = makeSubtleText(benchPrefix + "奖励：胜 +" + opp.getWinRewardTokens(curDate)
                    + " / 平 +" + opp.getDrawRewardTokens(curDate)
                    + " / 负 +" + opp.getLossRewardTokens(curDate) + " 代币");
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
                        selectTabById(TAB_DECK);
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
            int w = opp.getWinRewardTokens(p.effectiveDate);
            int d = opp.getDrawRewardTokens(p.effectiveDate);
            int l = opp.getLossRewardTokens(p.effectiveDate);
            cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.RewardTriple cal =
                    cn.garymb.ygomobile.rpg.engine.EraBountyCalibrator.resolve(opp.id, p.effectiveDate);
            String eloInfo = cal != null ? ("[" + cal.getTierLabel() + "级 · Elo " + cal.benchmarkElo + "] ") : "";
            sb.append(isCur ? "▶ [当前使用] " : (unlocked ? "• [历史旧版] " : "🔒 [未来版本] "))
                    .append(p.effectiveDate).append(" ").append(p.deckName).append("\n")
                    .append("  ").append(eloInfo).append("首发期奖励：胜 +").append(w)
                    .append(" / 平 +").append(d).append(" / 负 +").append(l).append(" 代币\n")
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
                        selectTabById(TAB_DECK);
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
        final String playerStyle = RpgAiStrategy.resolveStyleForDeck(
                profile.activeDeckAiStyle, profile.activeDeckMain, profile.activeDeckExtra, activeRule);
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
                showDuelSettlementDialog(
                        "🏆 锦标赛赛果 · " + bracket.getRoundTitle(match.roundIndex),
                        msg,
                        report.replayFilePath != null ? () -> launchNative3dReplay(report, TAB_TOURNAMENT) : null,
                        () -> copyDuelReplayLog(report, profile.activeDeckMain, profile.activeDeckExtra, opp.deckMain, opp.deckExtra));
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
        Runnable onReplay = (m.replayReport != null && m.replayReport.replayFilePath != null)
                ? () -> launchNative3dReplay(m.replayReport, TAB_TOURNAMENT) : null;
        Runnable onCopy = m.replayReport != null
                ? () -> copyDuelReplayLog(
                m.replayReport,
                m.p1 != null ? m.p1.deckMain : null, m.p1 != null ? m.p1.deckExtra : null,
                m.p2 != null ? m.p2.deckMain : null, m.p2 != null ? m.p2.deckExtra : null)
                : null;
        showDuelSettlementDialog("📜 锦标赛战报", sb.toString().trim(), onReplay, onCopy);
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
                        selectTabById(TAB_DECK);
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
        final String playerStyle = RpgAiStrategy.resolveStyleForDeck(
                profile.activeDeckAiStyle, profile.activeDeckMain, profile.activeDeckExtra, activeRule);
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
                showDuelSettlementDialog(
                        "⚡ AI 托管快战 · " + settlement.outcomeLabel,
                        sb.toString().trim(),
                        report.replayFilePath != null ? () -> launchNative3dReplay(report, TAB_AI_DUEL) : null,
                        () -> copyDuelReplayLog(report, profile.activeDeckMain, profile.activeDeckExtra, aiDeck.main, aiDeck.extra));
            });
        }, "RpgQuickDuelOcg").start();
    }

    /**
     * Unified post-duel settlement dialog with a fixed-height scrollable summary body and a
     * guaranteed single horizontal button row (回放 | 日志 | 完成) — never stacks vertically or
     * requires scrolling to reach the buttons. Tapping「日志」copies without dismissing the dialog.
     */
    private void showDuelSettlementDialog(String title,
                                          String bodyText,
                                          final Runnable onReplay,
                                          final Runnable onCopyLog) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(dp(14), dp(12), dp(14), dp(12));

        ScrollView scroll = new ScrollView(this);
        int maxBodyHeight = (bodyText != null && bodyText.length() > 180)
                ? dp(240)
                : LinearLayout.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxBodyHeight);
        scroll.setLayoutParams(slp);

        TextView tvBody = new TextView(this);
        tvBody.setText(bodyText != null ? bodyText : "");
        tvBody.setTextColor(Color.parseColor("#E2E8F0"));
        tvBody.setTextSize(12.5f);
        tvBody.setLineSpacing(dp(2), 1.0f);
        tvBody.setPadding(dp(2), dp(2), dp(2), dp(4));
        scroll.addView(tvBody);
        root.addView(scroll);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(root)
                .create();

        Button btnReplay = null;
        if (onReplay != null) {
            btnReplay = makeActionButton("回放", "#0284C7");
            btnReplay.setOnClickListener(v -> {
                dlg.dismiss();
                onReplay.run();
            });
        }
        Button btnCopy = null;
        if (onCopyLog != null) {
            btnCopy = makeActionButton("日志", "#475569");
            btnCopy.setOnClickListener(v -> onCopyLog.run());
        }
        Button btnDone = makeActionButton("完成", "#059669");
        btnDone.setOnClickListener(v -> dlg.dismiss());

        root.addView(makeEqualWeightButtonRow(12, btnReplay, btnCopy, btnDone));
        dlg.show();
    }

    private void copyReplayFileLog(String replayFilePath) {
        if (replayFilePath == null || replayFilePath.isEmpty()) {
            Toast.makeText(this, "暂无可复制的对局录像文件", Toast.LENGTH_SHORT).show();
            return;
        }
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        String text = bridge != null ? bridge.buildReplayLogText(replayFilePath) : null;
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "无法解析本局录像日志（文件可能已被清理）", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("YGOMobile RPG Replay Log", text));
            Toast.makeText(this, "已复制对战回放日志到剪贴板（含双方策略、卡组与操作日志）", Toast.LENGTH_LONG).show();
        }
    }

    private void launchLiveReplay(RpgSessionManager.SavedLiveReplay liveReplay) {
        if (liveReplay == null || liveReplay.replayFilePath == null || liveReplay.replayFilePath.isEmpty()) {
            Toast.makeText(this, "本局暂无可播放的 3D 录像文件", Toast.LENGTH_SHORT).show();
            return;
        }
        File replayFile = new File(liveReplay.replayFilePath);
        if (!replayFile.exists() || replayFile.length() <= 0L) {
            Toast.makeText(this, "录像文件不存在或已被清理", Toast.LENGTH_LONG).show();
            return;
        }
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未连接到 YGOMobile 主程序", Toast.LENGTH_LONG).show();
            return;
        }
        RpgDiagLog.log("Home", "launchLiveReplay file=" + replayFile.getName()
                + " size=" + replayFile.length() + " returnTab=" + liveReplay.returnTab);
        bridge.startRpgReplay(this, liveReplay.replayFilePath,
                liveReplay.player0Name, liveReplay.player1Name, liveReplay.returnTab);
        finish();
    }

    /**
     * Copies the full duel log (strategies / manual mode, both Main+Extra decklists, and turn-by-turn
     * engine action log) to the system clipboard for AI strategy review and tuning.
     */
    private void copyDuelReplayLog(RpgAiAutoDuelModels.DuelResult report,
                                   List<Integer> mainA, List<Integer> extraA,
                                   List<Integer> mainB, List<Integer> extraB) {
        if (report == null) {
            Toast.makeText(this, "暂无可复制的对局日志", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = null;
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge != null && report.replayFilePath != null && !report.replayFilePath.isEmpty()) {
            text = bridge.buildReplayLogText(report.replayFilePath);
        }
        if (text == null || text.isEmpty()) {
            text = cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.formatFallbackFromDuelResult(
                    report, profile != null ? profile.currentTimelineDate : "",
                    mainA, extraA, mainB, extraB, catalog);
        }
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "生成对局日志失败", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("YGOMobile RPG Replay Log", text));
            Toast.makeText(this, "已复制对战回放日志到剪贴板（含双方策略、卡组与操作日志）", Toast.LENGTH_LONG).show();
        }
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
        File replayFile = new File(report.replayFilePath);
        if (!replayFile.exists() || replayFile.length() <= 0L) {
            Toast.makeText(this, "录像文件已被清理或不存在，请重新快战一局再回看", Toast.LENGTH_LONG).show();
            return;
        }
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未连接到 YGOMobile 主程序，请从游戏主菜单进入 RPG 模式", Toast.LENGTH_LONG).show();
            return;
        }
        String p0Name = report.sideAFirst ? report.sideAName : report.sideBName;
        String p1Name = report.sideAFirst ? report.sideBName : report.sideAName;
        RpgDiagLog.log("Home", "launchNative3dReplay file=" + replayFile.getName()
                + " size=" + replayFile.length() + " returnTab=" + returnTab + " -> startRpgReplay + finish()");
        bridge.startRpgReplay(this, report.replayFilePath, p0Name, p1Name, returnTab);
        finish();
    }

    // ==================== TAB 8: CANON DUEL RECREATION ====================
    private void renderCanonDuelsTab() {
        List<OcgCanonDuel> allCanon = catalog.getCanonDuels();
        List<OcgCanonDuel> todayDuels = TimelineEngine.getCanonDuelsOnDate(catalog, profile.currentTimelineDate);

        // Stack filter bar and summary note vertically inside a single container
        // (layoutTabActions is HORIZONTAL; adding a MATCH_PARENT view + TextView directly would
        // leave 0px width for the TextView, causing single-character wrapping and pushing content off-screen.)
        LinearLayout actionCol = new LinearLayout(this);
        actionCol.setOrientation(LinearLayout.VERTICAL);
        actionCol.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int totalAllModes = allCanon.size() * 4;
        int clearedAllModes = 0;
        for (OcgCanonDuel d : allCanon) {
            clearedAllModes += profile.getCanonDuelClearedCount(d.id);
        }
        int totalTodayModes = todayDuels.size() * 4;
        int clearedTodayModes = 0;
        for (OcgCanonDuel d : todayDuels) {
            clearedTodayModes += profile.getCanonDuelClearedCount(d.id);
        }

        if (!todayDuels.isEmpty()) {
            android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(this);
            filterScroll.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            filterScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout filterRow = new LinearLayout(this);
            filterRow.setOrientation(LinearLayout.HORIZONTAL);
            filterRow.setGravity(Gravity.CENTER_VERTICAL);

            String[][] filters = {
                    {"ALL", "当日全部 (" + todayDuels.size() + ")"},
                    {"MANGA", "原作漫画"},
                    {"SCREEN", "动画/剧场版"},
                    {"UNCLEARED", "未全通关"}
            };
            for (final String[] f : filters) {
                boolean active = f[0].equals(canonFilterMode);
                Button btn = makeActionButton(f[1], active ? "#DB2777" : "#334155");
                btn.setOnClickListener(v -> {
                    canonFilterMode = f[0];
                    renderCurrentTab();
                });
                filterRow.addView(btn);
            }
            filterScroll.addView(filterRow);
            actionCol.addView(filterScroll);
        }

        TextView note = makeSubtleText("🎬 当日（" + profile.currentTimelineDate + "）对决还原：" + todayDuels.size()
                + " 场（当日已通关 " + clearedTodayModes + "/" + totalTodayModes
                + " · 累计 " + clearedAllModes + "/" + totalAllModes + " 模式 · 仅限当日环境卡池挑战）");
        note.setPadding(0, todayDuels.isEmpty() ? 0 : dp(6), 0, 0);
        actionCol.addView(note);
        layoutTabActions.addView(actionCol);

        // Filter today's exact-date duels (already sorted ascending by originalDate)
        List<OcgCanonDuel> filtered = new ArrayList<>();
        for (OcgCanonDuel d : todayDuels) {
            if ("MANGA".equals(canonFilterMode) && !"MANGA".equals(d.mediaType)) continue;
            if ("SCREEN".equals(canonFilterMode) && "MANGA".equals(d.mediaType)) continue;
            if ("UNCLEARED".equals(canonFilterMode) && profile.getCanonDuelClearedCount(d.id) >= 4) continue;
            filtered.add(d);
        }

        if (filtered.isEmpty()) {
            LinearLayout empty = makeCardContainer();
            empty.addView(makeTitleText(todayDuels.isEmpty()
                    ? "📅 " + profile.currentTimelineDate + " 当日暂无对决还原"
                    : "当前筛选条件下暂无符合条件的当日对决还原"));
            if (todayDuels.isEmpty()) {
                empty.addView(makeSubtleText(
                        "为保证只能使用当时环境的卡牌公平挑战且避免后期列表过长，对决还原仅在对应解锁日当天开放挑战。\n"
                                + "点击顶部日历中带有 🎬 特殊粉框标记的日期，或直接点选下方对决还原日期前往："));

                Button openCalBtn = makeActionButton("📅 打开日历（查找 🎬 对决还原日期）", "#DB2777");
                LinearLayout.LayoutParams calLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                calLp.topMargin = dp(8);
                openCalBtn.setLayoutParams(calLp);
                openCalBtn.setOnClickListener(v -> openDatePicker());
                empty.addView(openCalBtn);

                // Quick-jump 2-column grid of all dates that host Canon Duels
                List<String> canonDates = new ArrayList<>(TimelineEngine.getCanonDuelDates(catalog));
                for (int i = 0; i < canonDates.size(); i += 2) {
                    String d1 = canonDates.get(i);
                    Button b1 = makeCanonDateJumpButton(d1);
                    if (i + 1 < canonDates.size()) {
                        String d2 = canonDates.get(i + 1);
                        Button b2 = makeCanonDateJumpButton(d2);
                        empty.addView(makeEqualWeightButtonRow(6, b1, b2));
                    } else {
                        LinearLayout singleRow = new LinearLayout(this);
                        singleRow.setOrientation(LinearLayout.HORIZONTAL);
                        singleRow.setPadding(0, dp(6), 0, 0);
                        b1.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        singleRow.addView(b1);
                        empty.addView(singleRow);
                    }
                }
            } else {
                empty.addView(makeSubtleText("切换上方筛选条件即可查看当日全部对决还原。"));
            }
            layoutContent.addView(empty);
            return;
        }

        for (final OcgCanonDuel duel : filtered) {
            if (duel == null || duel.sideA == null || duel.sideB == null) continue;
            final CanonDuelDeckBuilder.BuiltCanonDeck deckA =
                    CanonDuelDeckBuilder.buildDeck(catalog, duel, duel.sideA, duel.timelineDate);
            final CanonDuelDeckBuilder.BuiltCanonDeck deckB =
                    CanonDuelDeckBuilder.buildDeck(catalog, duel, duel.sideB, duel.timelineDate);

            boolean clearedRoleA = profile.isCanonDuelModeCleared(duel.id, RpgProfile.CANON_MODE_ROLE_A);
            boolean clearedRoleB = profile.isCanonDuelModeCleared(duel.id, RpgProfile.CANON_MODE_ROLE_B);
            boolean clearedOwnVsA = profile.isCanonDuelModeCleared(duel.id, RpgProfile.CANON_MODE_OWN_VS_A);
            boolean clearedOwnVsB = profile.isCanonDuelModeCleared(duel.id, RpgProfile.CANON_MODE_OWN_VS_B);
            int duelClearedCount = (clearedRoleA ? 1 : 0) + (clearedRoleB ? 1 : 0)
                    + (clearedOwnVsA ? 1 : 0) + (clearedOwnVsB ? 1 : 0);

            LinearLayout card = makeCardContainer();
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1E293B"));
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(2), Color.parseColor(duelClearedCount == 4 ? "#10B981" : "#EC4899"));
            card.setBackground(bg);

            // Title & Media/Date/Clear header
            String clearTag = duelClearedCount == 4 ? "  ✅ 全通关(4/4)"
                    : (duelClearedCount > 0 ? ("  🏅 " + duelClearedCount + "/4") : "");
            TextView tvHead = makeTitleText("[" + duel.getMediaLabelCn() + "] " + duel.title + clearTag);
            tvHead.setTextColor(Color.parseColor(duelClearedCount == 4 ? "#6EE7B7" : "#FBCFE8"));
            card.addView(tvHead);

            String dateLine = duel.episodeLabel + " · 原作日期 " + duel.originalDate
                    + " · 核心卡 OCG 解锁日 " + duel.timelineDate;
            TextView tvDate = makeSubtleText(dateLine);
            tvDate.setTextColor(Color.parseColor("#38BDF8"));
            card.addView(tvDate);

            if (duel.coreUnlockNote != null && !duel.coreUnlockNote.isEmpty()) {
                TextView tvCore = makeSubtleText("🔑 " + duel.coreUnlockNote);
                tvCore.setTextColor(Color.parseColor("#FDE68A"));
                card.addView(tvCore);
            }

            // Dual Avatar VS Strip
            LinearLayout vsRow = new LinearLayout(this);
            vsRow.setOrientation(LinearLayout.HORIZONTAL);
            vsRow.setGravity(Gravity.CENTER_VERTICAL);
            vsRow.setPadding(0, dp(8), 0, dp(6));

            // Side A column
            LinearLayout colA = new LinearLayout(this);
            colA.setOrientation(LinearLayout.HORIZONTAL);
            colA.setGravity(Gravity.CENTER_VERTICAL);
            colA.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            ImageView avA = new ImageView(this);
            LinearLayout.LayoutParams lpAvA = new LinearLayout.LayoutParams(dp(56), dp(56));
            lpAvA.setMarginEnd(dp(8));
            avA.setLayoutParams(lpAvA);
            avA.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avA.setPadding(dp(2), dp(2), dp(2), dp(2));
            avA.setBackground(RpgCardArtLoader.createAvatarPlaceholderDrawable());
            RpgCardArtLoader.loadOpponentAvatarInto(this, avA, duel.sideA.avatarId);
            colA.addView(avA);

            LinearLayout txtA = new LinearLayout(this);
            txtA.setOrientation(LinearLayout.VERTICAL);
            txtA.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView nameA = makeTitleText(duel.sideA.characterName);
            nameA.setTextSize(13f);
            nameA.setTextColor(Color.parseColor("#FDE68A"));
            txtA.addView(nameA);
            txtA.addView(makeSubtleText("原著" + deckA.canonMainCount + "张+补" + deckA.paddedMainCount + "张"
                    + (deckA.extra.isEmpty() ? "" : (" · 额外" + deckA.extra.size()))));
            colA.addView(txtA);
            vsRow.addView(colA);

            TextView tvVs = new TextView(this);
            tvVs.setText("VS");
            tvVs.setTextColor(Color.parseColor("#F43F5E"));
            tvVs.setTextSize(15f);
            tvVs.setTypeface(null, android.graphics.Typeface.BOLD);
            tvVs.setPadding(dp(6), 0, dp(6), 0);
            vsRow.addView(tvVs);

            // Side B column
            LinearLayout colB = new LinearLayout(this);
            colB.setOrientation(LinearLayout.HORIZONTAL);
            colB.setGravity(Gravity.CENTER_VERTICAL);
            colB.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout txtB = new LinearLayout(this);
            txtB.setOrientation(LinearLayout.VERTICAL);
            txtB.setGravity(Gravity.END);
            txtB.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView nameB = makeTitleText(duel.sideB.characterName);
            nameB.setTextSize(13f);
            nameB.setTextColor(Color.parseColor("#A5F3FC"));
            nameB.setGravity(Gravity.END);
            txtB.addView(nameB);
            TextView subB = makeSubtleText("原著" + deckB.canonMainCount + "张+补" + deckB.paddedMainCount + "张"
                    + (deckB.extra.isEmpty() ? "" : (" · 额外" + deckB.extra.size())));
            subB.setGravity(Gravity.END);
            txtB.addView(subB);
            colB.addView(txtB);

            ImageView avB = new ImageView(this);
            LinearLayout.LayoutParams lpAvB = new LinearLayout.LayoutParams(dp(56), dp(56));
            lpAvB.setMarginStart(dp(8));
            avB.setLayoutParams(lpAvB);
            avB.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avB.setPadding(dp(2), dp(2), dp(2), dp(2));
            avB.setBackground(RpgCardArtLoader.createAvatarPlaceholderDrawable());
            RpgCardArtLoader.loadOpponentAvatarInto(this, avB, duel.sideB.avatarId);
            colB.addView(avB);
            vsRow.addView(colB);

            card.addView(vsRow);

            if (duel.summary != null && !duel.summary.isEmpty()) {
                TextView sumTv = makeSubtleText(duel.summary);
                sumTv.setTextColor(Color.parseColor("#E2E8F0"));
                card.addView(sumTv);
            }

            // Canon signature cards preview
            TextView sigTv = makeSubtleText("• " + duel.sideA.characterName + " 原著用卡："
                    + formatCanonCardSample(duel.sideA) + "\n• " + duel.sideB.characterName + " 原著用卡："
                    + formatCanonCardSample(duel.sideB));
            sigTv.setPadding(0, dp(4), 0, 0);
            card.addView(sigTv);

            // Row 1: Role-play either side's reconstructed deck (with top-right success badge if cleared)
            Button playAsABtn = makeActionButton("🎭 扮演 " + duel.sideA.characterName, "#DC2626");
            playAsABtn.setOnClickListener(v ->
                    launchCanonDuelRoleplay(duel, deckA, deckB, RpgProfile.CANON_MODE_ROLE_A));

            Button playAsBBtn = makeActionButton("🎭 扮演 " + duel.sideB.characterName, "#7C3AED");
            playAsBBtn.setOnClickListener(v ->
                    launchCanonDuelRoleplay(duel, deckB, deckA, RpgProfile.CANON_MODE_ROLE_B));
            card.addView(makeBadgeableButtonRow(8, playAsABtn, clearedRoleA, playAsBBtn, clearedRoleB));

            // Row 2: Bring player's own RPG deck against either side (with top-right success badge if cleared)
            Button ownVsABtn = makeActionButton("🃏 自组挑战 " + duel.sideA.characterName, "#D97706");
            ownVsABtn.setOnClickListener(v ->
                    launchCanonDuelWithOwnDeck(duel, deckA, RpgProfile.CANON_MODE_OWN_VS_A));

            Button ownVsBBtn = makeActionButton("🃏 自组挑战 " + duel.sideB.characterName, "#059669");
            ownVsBBtn.setOnClickListener(v ->
                    launchCanonDuelWithOwnDeck(duel, deckB, RpgProfile.CANON_MODE_OWN_VS_B));
            card.addView(makeBadgeableButtonRow(6, ownVsABtn, clearedOwnVsA, ownVsBBtn, clearedOwnVsB));

            // Row 3: Inspect both reconstructed decks visually + Replay AVG Story Dialogue anytime
            Button viewABtn = makeActionButton("🃏 " + duel.sideA.characterName + " 卡组", "#0284C7");
            viewABtn.setOnClickListener(v -> RpgPackVisualDialogs.showCanonDeckVisualDialog(
                    this, catalog, profile, deckA, duel.episodeLabel));

            Button viewBBtn = makeActionButton("🃏 " + duel.sideB.characterName + " 卡组", "#475569");
            viewBBtn.setOnClickListener(v -> RpgPackVisualDialogs.showCanonDeckVisualDialog(
                    this, catalog, profile, deckB, duel.episodeLabel));

            boolean hasStory = (duel.introDialogue != null && !duel.introDialogue.isEmpty())
                    || (duel.outroSideAWin != null && !duel.outroSideAWin.isEmpty())
                    || (duel.outroSideBWin != null && !duel.outroSideBWin.isEmpty())
                    || (duel.outroDialogue != null && !duel.outroDialogue.isEmpty());
            if (hasStory) {
                Button storyBtn = makeActionButton("📖 剧情", "#B45309");
                storyBtn.setOnClickListener(v -> CanonStoryDialog.showFullStoryReplay(this, duel));
                card.addView(makeEqualWeightButtonRow(6, viewABtn, viewBBtn, storyBtn));
            } else {
                card.addView(makeEqualWeightButtonRow(6, viewABtn, viewBBtn));
            }

            layoutContent.addView(card);
        }
    }

    private Button makeCanonDateJumpButton(final String dateStr) {
        List<OcgCanonDuel> dayDuels = TimelineEngine.getCanonDuelsOnDate(catalog, dateStr);
        int totalModes = dayDuels.size() * 4;
        int clearedModes = 0;
        for (OcgCanonDuel d : dayDuels) {
            clearedModes += profile.getCanonDuelClearedCount(d.id);
        }
        boolean allCleared = totalModes > 0 && clearedModes >= totalModes;
        String label = "🎬 " + dateStr + " (" + dayDuels.size() + "场"
                + (allCleared ? " ✅" : (" · " + clearedModes + "/" + totalModes)) + ")";
        Button btn = makeActionButton(label, allCleared ? "#065F46" : "#334155");
        btn.setOnClickListener(v -> selectTimelineDate(dateStr));
        return btn;
    }

    private LinearLayout makeBadgeableButtonRow(int topPadDp,
                                                Button btn1, boolean btn1Success,
                                                Button btn2, boolean btn2Success) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, dp(topPadDp), 0, 0);

        Button[] btns = {btn1, btn2};
        boolean[] flags = {btn1Success, btn2Success};
        for (int i = 0; i < btns.length; i++) {
            android.widget.FrameLayout cell = new android.widget.FrameLayout(this);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < btns.length - 1) {
                cellLp.setMarginEnd(dp(8));
            }
            cell.setLayoutParams(cellLp);

            Button b = btns[i];
            android.widget.FrameLayout.LayoutParams bLp = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            bLp.topMargin = dp(4);
            b.setLayoutParams(bLp);
            b.setPadding(dp(8), 0, dp(8), 0);
            cell.addView(b);

            if (flags[i]) {
                TextView badge = new TextView(this);
                badge.setText("✔ 成功");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(9f);
                badge.setTypeface(null, android.graphics.Typeface.BOLD);
                badge.setPadding(dp(5), dp(1), dp(5), dp(1));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#10B981"));
                bg.setCornerRadius(dp(8));
                bg.setStroke(dp(1), Color.parseColor("#ECFDF5"));
                badge.setBackground(bg);
                badge.setElevation(dp(4));
                android.widget.FrameLayout.LayoutParams badgeLp = new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                badgeLp.setMargins(0, 0, dp(2), 0);
                badge.setLayoutParams(badgeLp);
                cell.addView(badge);
            }
            row.addView(cell);
        }
        return row;
    }

    private String formatCanonCardSample(OcgCanonDuel.DuelSide side) {
        if (side == null) return "";
        List<String> names = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
        if (side.canonExtraIds != null) {
            for (Integer id : side.canonExtraIds) {
                if (id != null && seen.add(id)) {
                    RpgCard c = catalog.findCardOrCanon(id);
                    names.add(c != null ? c.getDisplayName() : String.valueOf(id));
                }
            }
        }
        if (side.canonCardIds != null) {
            for (Integer id : side.canonCardIds) {
                if (names.size() >= 6) break;
                if (id != null && seen.add(id)) {
                    RpgCard c = catalog.findCardOrCanon(id);
                    names.add(c != null ? c.getDisplayName() : String.valueOf(id));
                }
            }
        }
        int totalUnique = seen.size();
        String joined = TextUtils.join("、", names);
        return (side.canonCardIds != null && side.canonCardIds.size() > names.size())
                ? (joined + " 等" + totalUnique + "种") : joined;
    }

    private AiOpponent buildCanonSyntheticOpponent(OcgCanonDuel duel, CanonDuelDeckBuilder.BuiltCanonDeck oppDeck) {
        AiOpponent.EraDeckProfile ep = new AiOpponent.EraDeckProfile(
                duel.timelineDate,
                oppDeck.deckName,
                duel.getMediaLabelCn(),
                duel.summary != null ? duel.summary : "",
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                oppDeck.fillStyle);
        return new AiOpponent(
                "canon_" + duel.id + "_" + oppDeck.characterId,
                oppDeck.characterName,
                duel.title,
                duel.timelineDate,
                3,
                260,
                120,
                80,
                duel.summary != null ? duel.summary : "",
                java.util.Collections.singletonList(ep));
    }

    private void launchCanonDuelRoleplay(OcgCanonDuel duel,
                                         CanonDuelDeckBuilder.BuiltCanonDeck playerCanonDeck,
                                         CanonDuelDeckBuilder.BuiltCanonDeck oppCanonDeck,
                                         String canonMode) {
        RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未连接到 YGOMobile 主程序，请从游戏主菜单进入 RPG 模式", Toast.LENGTH_LONG).show();
            return;
        }
        Runnable proceed = () -> {
            OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
            OcgBanlist activeBanlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
            AiOpponent syntheticOpp = buildCanonSyntheticOpponent(duel, oppCanonDeck);
            try {
                File playerYdk = DeckValidator.exportToYdk(
                        repository.getAiDeckYdkFile("canon_player_" + playerCanonDeck.characterId),
                        playerCanonDeck.main, playerCanonDeck.extra);
                File aiYdk = DeckValidator.exportToYdk(
                        repository.getAiDeckYdkFile("canon_opp_" + oppCanonDeck.characterId),
                        oppCanonDeck.main, oppCanonDeck.extra);

                RpgSessionManager.beginRpgCanonDuel(syntheticOpp, duel.id, canonMode, TAB_CANON_DUELS);
                bridge.startRpgCanonDuel(
                        this,
                        playerCanonDeck.characterName,
                        playerCanonDeck.avatarId,
                        playerYdk.getAbsolutePath(),
                        syntheticOpp,
                        oppCanonDeck.avatarId,
                        aiYdk.getAbsolutePath(),
                        activeBanlist,
                        activeRule,
                        profile.currentTimelineDate);
                finish();
            } catch (IOException e) {
                Toast.makeText(this, "导出还原卡组失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        CanonStoryDialog.showIntro(this, duel, proceed);
    }

    private void launchCanonDuelWithOwnDeck(OcgCanonDuel duel,
                                            CanonDuelDeckBuilder.BuiltCanonDeck oppCanonDeck,
                                            String canonMode) {
        if (duel != null && duel.timelineDate != null && !duel.timelineDate.equals(profile.currentTimelineDate)) {
            Toast.makeText(this, "请先切换至 " + duel.timelineDate + " 当日环境再挑战该场对决还原", Toast.LENGTH_LONG).show();
            return;
        }
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        if (!val.valid) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 自组卡组未通过校验")
                    .setMessage(TextUtils.join("\n", val.errors))
                    .setPositiveButton("去组卡", (d, w) -> {
                        selectTabById(TAB_DECK);
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
        Runnable proceed = () -> {
            OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
            OcgBanlist activeBanlist = TimelineEngine.getActiveBanlist(catalog, profile.currentTimelineDate);
            AiOpponent syntheticOpp = buildCanonSyntheticOpponent(duel, oppCanonDeck);
            try {
                File playerYdk = DeckValidator.exportToYdk(
                        repository.getPlayerDeckYdkFile(), profile.activeDeckMain, profile.activeDeckExtra);
                File aiYdk = DeckValidator.exportToYdk(
                        repository.getAiDeckYdkFile("canon_opp_" + oppCanonDeck.characterId),
                        oppCanonDeck.main, oppCanonDeck.extra);

                RpgSessionManager.beginRpgCanonDuel(syntheticOpp, duel.id, canonMode, TAB_CANON_DUELS);
                bridge.startRpgCanonDuel(
                        this,
                        null,
                        null,
                        playerYdk.getAbsolutePath(),
                        syntheticOpp,
                        oppCanonDeck.avatarId,
                        aiYdk.getAbsolutePath(),
                        activeBanlist,
                        activeRule,
                        profile.currentTimelineDate);
                finish();
            } catch (IOException e) {
                Toast.makeText(this, "导出卡组失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        CanonStoryDialog.showIntro(this, duel, proceed);
    }

    private void launchAiDuel(AiOpponent opponent) {
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        if (!val.valid) {
            new AlertDialog.Builder(this)
                    .setTitle("RPG 卡组未通过校验")
                    .setMessage(android.text.TextUtils.join("\n", val.errors))
                    .setPositiveButton("去组卡", (d, w) -> {
                        selectTabById(TAB_DECK);
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
        String curStyle = RpgAiStrategy.resolveStyleForDeck(
                profile.activeDeckAiStyle, profile.activeDeckMain, profile.activeDeckExtra, activeRule);
        String curStyleTag = (profile.activeDeckAiStyle != null && !profile.activeDeckAiStyle.trim().isEmpty())
                ? ("手动:" + RpgAiStrategy.getStyleDisplayName(curStyle))
                : ("自动:" + RpgAiStrategy.getStyleDisplayName(curStyle));
        list.add(new ShowdownDeckChoice(
                "current",
                "当前卡组",
                "[当前卡组] 当前编辑中的 RPG 卡组（主 " + profile.activeDeckMain.size()
                        + " / 额外 " + profile.activeDeckExtra.size() + " · AI策略 " + curStyleTag + "）",
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
                String st = RpgAiStrategy.resolveStyleForDeck(sd.aiStyleOverride, sd.main, sd.extra, activeRule);
                String stTag = (sd.aiStyleOverride != null && !sd.aiStyleOverride.trim().isEmpty())
                        ? ("手动:" + RpgAiStrategy.getStyleDisplayName(st))
                        : ("自动:" + RpgAiStrategy.getStyleDisplayName(st));
                list.add(new ShowdownDeckChoice(
                        "saved:" + sd.id,
                        "已保存卡组",
                        "[已保存] " + sd.name + "（主 " + m + " / 额外 " + e
                                + " · AI策略 " + stTag + "）",
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

            if (r.sampleLastResult != null) {
                Button sampleBtn = null;
                if (r.sampleLastResult.replayFilePath != null) {
                    sampleBtn = makeActionButton("🎬 3D回放样本(第" + r.totalGames + "局)", "#0284C7");
                    sampleBtn.setOnClickListener(v -> launchNative3dReplay(r.sampleLastResult, TAB_AI_SHOWDOWN));
                }
                Button copySampleLogBtn = makeActionButton("📋 复制样本日志", "#475569");
                copySampleLogBtn.setOnClickListener(v -> copyDuelReplayLog(
                        r.sampleLastResult, null, null, null, null));
                resCard.addView(makeEqualWeightButtonRow(8, sampleBtn, copySampleLogBtn));
            }
            layoutContent.addView(resCard);
        }

        // ===== Full-Environment AI Ladder: Swiss+Elo (O(N log N)) & Full Round-Robin =====
        List<AiOpponent> unlockedOpps = TimelineEngine.getAvailableOpponents(
                AiDeckGenerator.getDemoOpponents(), profile.currentTimelineDate);
        List<RoundRobinLeagueEngine.PlayerDeckEntry> selectedPlayerDecks = collectSelectedValidLadderPlayerDecks();
        int savedTotalCount = profile.savedDecks != null ? profile.savedDecks.size() : 0;
        int contestantCount = unlockedOpps.size() + selectedPlayerDecks.size();
        int swissRounds = RoundRobinLeagueEngine.recommendedSwissRounds(contestantCount);
        int swissGames = swissRounds * (contestantCount / 2) * RoundRobinLeagueEngine.DEFAULT_SWISS_GAMES_PER_PAIR;
        int rrPairs = contestantCount * (contestantCount - 1) / 2;
        int rrGames = rrPairs * RoundRobinLeagueEngine.DEFAULT_GAMES_PER_PAIR;

        OcgRuleSet ladderRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        String playerDeckSummary;
        if (selectedPlayerDecks.isEmpty()) {
            playerDeckSummary = "（未勾选合规自组卡组，仅 AI 参赛 · 可点下方选择自组卡组与AI策略）";
        } else {
            List<String> names = new ArrayList<>();
            for (RoundRobinLeagueEngine.PlayerDeckEntry e : selectedPlayerDecks) {
                String effSt = RpgAiStrategy.resolveStyleForDeck(e.aiStyleOverride, e.main, e.extra, ladderRule);
                String deckSuffix = (!e.deckName.isEmpty() && !"已存卡组".equals(e.deckName))
                        ? ("(" + e.deckName + ")") : "";
                names.add(e.name + deckSuffix + "[" + RpgAiStrategy.getStyleDisplayName(effSt) + "]");
            }
            playerDeckSummary = " + " + selectedPlayerDecks.size() + " 套自组【" + TextUtils.join("、", names) + "】";
        }

        LinearLayout leagueCard = makeCardContainer();
        TextView leagueTitle = makeTitleText("🏆 全环境天梯赛 · 瑞士轮积分 / 全量循环赛");
        leagueTitle.setTextColor(Color.parseColor("#FBBF24"));
        leagueCard.addView(leagueTitle);
        leagueCard.addView(makeSubtleText(String.format(Locale.US,
                "现环境（%s）已解锁 %d 位 AI%s → 共 %d 名选手。\n"
                        + "• 瑞士轮天梯（推荐）：全局最小权完美匹配撮合对手，%d 轮×每组 %d 局（1先1后消除先后手偏差，共 %d 局），广覆盖交手+相近名次加赛，用 Bradley-Terry 全局最大似然模型解算 Elo、误差 ±SE 与动态 S~D 评级！\n"
                        + "• 全量循环赛：所有选手两两打满 %d 局（%d 组 / %d 局），同步产出胜场榜与 Elo 积分。",
                profile.currentTimelineDate,
                unlockedOpps.size(),
                playerDeckSummary,
                contestantCount,
                swissRounds, RoundRobinLeagueEngine.DEFAULT_SWISS_GAMES_PER_PAIR, swissGames,
                RoundRobinLeagueEngine.DEFAULT_GAMES_PER_PAIR, rrPairs, rrGames)));

        Button selectDecksBtn = makeActionButton(
                "📋 选择参赛卡组与 AI 策略（已选 " + selectedPlayerDecks.size() + " 套自组 · 已存 " + savedTotalCount + " 套）",
                "#2563EB");
        LinearLayout.LayoutParams selLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        selLp.topMargin = dp(8);
        selectDecksBtn.setLayoutParams(selLp);
        selectDecksBtn.setOnClickListener(v -> openLadderDeckSelectorDialog());
        leagueCard.addView(selectDecksBtn);

        Button startSwissBtn = makeActionButton(
                "⚡ 瑞士轮天梯 (" + swissGames + "局)", "#059669");
        startSwissBtn.setOnClickListener(v -> startLeagueFlow(RoundRobinLeagueEngine.MODE_SWISS_ELO));

        Button startRrBtn = makeActionButton(
                "🏆 全量循环赛 (" + rrGames + "局)", "#D97706");
        startRrBtn.setOnClickListener(v -> startLeagueFlow(RoundRobinLeagueEngine.MODE_ROUND_ROBIN));

        leagueCard.addView(makeEqualWeightButtonRow(8, startSwissBtn, startRrBtn));
        layoutContent.addView(leagueCard);

        if (lastLeagueResult != null && !lastLeagueResult.standings.isEmpty()) {
            renderLeagueStandingsCard(lastLeagueResult);
        }
    }

    private boolean isSavedDeckIdenticalToActive(RpgProfile.SavedDeck saved) {
        if (saved == null || profile.activeDeckId == null || !profile.activeDeckId.equals(saved.id)) {
            return false;
        }
        List<Integer> sm = saved.main != null ? saved.main : java.util.Collections.emptyList();
        List<Integer> se = saved.extra != null ? saved.extra : java.util.Collections.emptyList();
        List<Integer> am = profile.activeDeckMain != null ? profile.activeDeckMain : java.util.Collections.emptyList();
        List<Integer> ae = profile.activeDeckExtra != null ? profile.activeDeckExtra : java.util.Collections.emptyList();
        return sm.equals(am) && se.equals(ae);
    }

    private List<RoundRobinLeagueEngine.PlayerDeckEntry> collectSelectedValidLadderPlayerDecks() {
        List<RoundRobinLeagueEngine.PlayerDeckEntry> entries = new ArrayList<>();
        if (ladderIncludeActiveDeck) {
            DeckValidator.ValidationResult activeVal = DeckValidator.validatePlayerDeck(profile, catalog);
            if (activeVal.valid) {
                RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
                String playerDeckLabel = activeSaved != null ? activeSaved.name : "当前编辑卡组";
                entries.add(new RoundRobinLeagueEngine.PlayerDeckEntry(
                        "player_deck",
                        "我的卡组",
                        playerDeckLabel,
                        profile.activeDeckMain,
                        profile.activeDeckExtra,
                        profile.activeDeckAiStyle));
            }
        }
        if (profile.savedDecks != null) {
            for (RpgProfile.SavedDeck saved : profile.savedDecks) {
                if (saved == null || saved.id == null || !selectedLadderSavedDeckIds.contains(saved.id)) continue;
                DeckValidator.ValidationResult val = DeckValidator.validateDeck(
                        saved.main, saved.extra, catalog, profile.currentTimelineDate, profile.collection);
                if (val.valid) {
                    String deckTitle = saved.name != null && !saved.name.trim().isEmpty()
                            ? saved.name.trim() : "已存卡组";
                    entries.add(new RoundRobinLeagueEngine.PlayerDeckEntry(
                            "player_saved_" + saved.id,
                            "自组 · " + deckTitle,
                            "已存卡组",
                            saved.main,
                            saved.extra,
                            saved.aiStyleOverride));
                }
            }
        }
        return entries;
    }

    private String formatAiStrategyButtonLabel(String styleOverride, String inferredStyle) {
        if (styleOverride != null && !styleOverride.trim().isEmpty()
                && RpgAiStrategy.SUPPORTED_STYLES.contains(styleOverride.trim())) {
            return "🧠 AI策略：手动指定 · " + RpgAiStrategy.getStyleDisplayName(styleOverride.trim()) + "  ▾";
        }
        return "🧠 AI策略：自动识别（" + RpgAiStrategy.getStyleDisplayName(inferredStyle) + "）  ▾";
    }

    private void openAiStylePickerDialog(String deckTitle,
                                         String currentOverride,
                                         String inferredStyle,
                                         final java.util.function.Consumer<String> onPicked) {
        final List<String> codes = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        codes.add(null);
        labels.add("🤖 自动识别（当前识别：" + RpgAiStrategy.getStyleDisplayName(inferredStyle) + "）");
        int checkedIdx = 0;
        String curNorm = currentOverride != null ? currentOverride.trim() : "";
        for (String styleCode : RpgAiStrategy.PLAYER_SELECTABLE_STYLES) {
            codes.add(styleCode);
            labels.add(RpgAiStrategy.getStyleDisplayName(styleCode) + "（" + styleCode + "）");
            if (styleCode.equals(curNorm)) {
                checkedIdx = codes.size() - 1;
            }
        }
        final int[] chosen = new int[]{checkedIdx};
        new AlertDialog.Builder(this)
                .setTitle("🧠 选择 AI 策略 · " + deckTitle)
                .setSingleChoiceItems(labels.toArray(new String[0]), checkedIdx, (d, which) -> {
                    chosen[0] = which;
                    if (onPicked != null && which >= 0 && which < codes.size()) {
                        onPicked.accept(codes.get(which));
                    }
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openLadderDeckSelectorDialog() {
        final OcgRuleSet activeRule = TimelineEngine.getActiveRuleSet(catalog, profile.currentTimelineDate);
        final DeckValidator.ValidationResult activeVal = DeckValidator.validatePlayerDeck(profile, catalog);
        final RpgProfile.SavedDeck activeSaved = profile.findSavedDeck(profile.activeDeckId);
        final String activeLabel = activeSaved != null ? activeSaved.name : "当前编辑卡组";

        final boolean[] tempIncludeActive = new boolean[]{ladderIncludeActiveDeck && activeVal.valid};
        final java.util.Set<String> tempSavedIds = new java.util.LinkedHashSet<>(selectedLadderSavedDeckIds);
        final String[] tempActiveStyle = new String[]{profile.activeDeckAiStyle};
        final Map<String, String> tempSavedStyles = new java.util.LinkedHashMap<>();
        if (profile.savedDecks != null) {
            for (RpgProfile.SavedDeck sd : profile.savedDecks) {
                if (sd != null && sd.id != null) {
                    tempSavedStyles.put(sd.id, sd.aiStyleOverride);
                }
            }
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(10));
        scroll.addView(body);

        TextView intro = makeSubtleText("勾选要加入 " + profile.currentTimelineDate
                + " 天梯排位的自组卡组，并可为每套卡组单独指定 AI 策略（保存后同步作用于天梯、1v1 对决与托管快战）：");
        intro.setTextColor(Color.parseColor("#CBD5E1"));
        body.addView(intro);

        final android.widget.CheckBox cbActive = new android.widget.CheckBox(this);
        final Map<String, android.widget.CheckBox> savedCheckboxes = new java.util.LinkedHashMap<>();
        final Map<String, Boolean> savedValidMap = new HashMap<>();
        final Map<String, Boolean> savedMirrorActiveMap = new HashMap<>();

        // Quick action row: 全选合规 / 仅当前 / 仅 AI
        Button btnSelectAllValid = makeActionButton("✅ 全选合规", "#059669");
        Button btnOnlyActive = makeActionButton("🃏 仅当前", "#2563EB");
        Button btnOnlyAi = makeActionButton("🤖 仅 AI", "#475569");
        body.addView(makeEqualWeightButtonRow(8, btnSelectAllValid, btnOnlyActive, btnOnlyAi));

        // Section 1: Current Active Deck
        TextView secActive = makeTitleText("当前卡组");
        secActive.setTextSize(13f);
        secActive.setTextColor(Color.parseColor("#FDE68A"));
        secActive.setPadding(0, dp(10), 0, dp(4));
        body.addView(secActive);

        LinearLayout activeBox = new LinearLayout(this);
        activeBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable actBg = new GradientDrawable();
        actBg.setColor(Color.parseColor("#1E293B"));
        actBg.setCornerRadius(dp(8));
        actBg.setStroke(dp(1), Color.parseColor(activeVal.valid ? "#3B82F6" : "#475569"));
        activeBox.setBackground(actBg);
        activeBox.setPadding(dp(10), dp(8), dp(10), dp(8));

        cbActive.setText("⭐ 我的当前卡组：" + activeLabel);
        cbActive.setTextColor(Color.parseColor(activeVal.valid ? "#F8FAFC" : "#64748B"));
        cbActive.setTextSize(13f);
        cbActive.setEnabled(activeVal.valid);
        cbActive.setChecked(tempIncludeActive[0]);
        cbActive.setOnCheckedChangeListener((btn, checked) -> tempIncludeActive[0] = checked);
        activeBox.addView(cbActive);

        final String activeInferred = RpgAiStrategy.inferStyleFromDeck(
                profile.activeDeckMain, profile.activeDeckExtra, activeRule);
        String activeSub;
        if (activeVal.valid) {
            activeSub = "主卡组 " + profile.activeDeckMain.size() + " 张"
                    + (profile.activeDeckExtra.isEmpty() ? "" : (" · 额外 " + profile.activeDeckExtra.size() + " 张"));
        } else {
            activeSub = "⚠ 未通过当期校验：" + (activeVal.errors.isEmpty() ? "卡组不合规" : activeVal.errors.get(0));
        }
        TextView tvActiveSub = makeSubtleText(activeSub);
        tvActiveSub.setTextColor(Color.parseColor(activeVal.valid ? "#86EFAC" : "#FCA5A5"));
        tvActiveSub.setPadding(dp(28), dp(2), 0, 0);
        activeBox.addView(tvActiveSub);

        if (activeVal.valid) {
            final Button btnActiveStyle = makeActionButton(
                    formatAiStrategyButtonLabel(tempActiveStyle[0], activeInferred),
                    tempActiveStyle[0] != null && !tempActiveStyle[0].isEmpty() ? "#7C3AED" : "#334155");
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
            slp.topMargin = dp(6);
            slp.leftMargin = dp(24);
            btnActiveStyle.setLayoutParams(slp);
            btnActiveStyle.setTextSize(11.5f);
            btnActiveStyle.setOnClickListener(v -> openAiStylePickerDialog(
                    activeLabel, tempActiveStyle[0], activeInferred, picked -> {
                        tempActiveStyle[0] = picked;
                        btnActiveStyle.setText(formatAiStrategyButtonLabel(picked, activeInferred));
                        GradientDrawable dBg = new GradientDrawable();
                        dBg.setColor(Color.parseColor(picked != null ? "#7C3AED" : "#334155"));
                        dBg.setCornerRadius(dp(6));
                        btnActiveStyle.setBackground(dBg);
                    }));
            activeBox.addView(btnActiveStyle);
        }
        body.addView(activeBox);

        // Section 2: Saved Decks
        int savedCount = profile.savedDecks != null ? profile.savedDecks.size() : 0;
        TextView secSaved = makeTitleText("已存储卡组（" + savedCount + " 套）");
        secSaved.setTextSize(13f);
        secSaved.setTextColor(Color.parseColor("#38BDF8"));
        secSaved.setPadding(0, dp(12), 0, dp(4));
        body.addView(secSaved);

        if (savedCount == 0) {
            TextView noSaved = makeSubtleText("暂无已存储卡组。在「🃏 组卡」页点击「💾 保存卡组」即可保存多套卡组并同时加入天梯排位！");
            noSaved.setPadding(dp(4), dp(4), dp(4), dp(6));
            body.addView(noSaved);
        } else {
            for (final RpgProfile.SavedDeck saved : profile.savedDecks) {
                if (saved == null || saved.id == null) continue;
                DeckValidator.ValidationResult sval = DeckValidator.validateDeck(
                        saved.main, saved.extra, catalog, profile.currentTimelineDate, profile.collection);
                boolean isMirror = isSavedDeckIdenticalToActive(saved);
                savedValidMap.put(saved.id, sval.valid);
                savedMirrorActiveMap.put(saved.id, isMirror);
                if (!sval.valid) {
                    tempSavedIds.remove(saved.id);
                }

                LinearLayout itemBox = new LinearLayout(this);
                itemBox.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setColor(Color.parseColor("#1E293B"));
                itemBg.setCornerRadius(dp(8));
                itemBg.setStroke(dp(1), Color.parseColor(sval.valid ? "#334155" : "#451A1A"));
                itemBox.setBackground(itemBg);
                itemBox.setPadding(dp(10), dp(8), dp(10), dp(8));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                ilp.topMargin = dp(6);
                itemBox.setLayoutParams(ilp);

                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                final String deckDisplayName = saved.name != null && !saved.name.trim().isEmpty()
                        ? saved.name.trim() : "未命名卡组";
                String title = deckDisplayName + (isMirror ? "（与当前卡组内容一致）" : "");
                cb.setText(title);
                cb.setTextColor(Color.parseColor(sval.valid ? "#F8FAFC" : "#64748B"));
                cb.setTextSize(13f);
                cb.setEnabled(sval.valid);
                cb.setChecked(sval.valid && tempSavedIds.contains(saved.id));
                cb.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) tempSavedIds.add(saved.id);
                    else tempSavedIds.remove(saved.id);
                });
                savedCheckboxes.put(saved.id, cb);
                itemBox.addView(cb);

                final String savedInferred = RpgAiStrategy.inferStyleFromDeck(saved.main, saved.extra, activeRule);
                String sub;
                if (sval.valid) {
                    int mSz = saved.main != null ? saved.main.size() : 0;
                    int eSz = saved.extra != null ? saved.extra.size() : 0;
                    sub = "主卡组 " + mSz + " 张"
                            + (eSz > 0 ? (" · 额外 " + eSz + " 张") : "")
                            + (saved.savedAtDate != null ? (" · 存自 " + saved.savedAtDate) : "");
                } else {
                    sub = "⚠ 当期不可参赛：" + (sval.errors.isEmpty() ? "卡组不合规" : sval.errors.get(0));
                }
                TextView tvSub = makeSubtleText(sub);
                tvSub.setTextColor(Color.parseColor(sval.valid ? "#93C5FD" : "#FCA5A5"));
                tvSub.setPadding(dp(28), dp(2), 0, 0);
                itemBox.addView(tvSub);

                if (sval.valid) {
                    String curOv = tempSavedStyles.get(saved.id);
                    final Button btnSavedStyle = makeActionButton(
                            formatAiStrategyButtonLabel(curOv, savedInferred),
                            curOv != null && !curOv.isEmpty() ? "#7C3AED" : "#334155");
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
                    slp.topMargin = dp(6);
                    slp.leftMargin = dp(24);
                    btnSavedStyle.setLayoutParams(slp);
                    btnSavedStyle.setTextSize(11.5f);
                    btnSavedStyle.setOnClickListener(v -> openAiStylePickerDialog(
                            deckDisplayName, tempSavedStyles.get(saved.id), savedInferred, picked -> {
                                tempSavedStyles.put(saved.id, picked);
                                btnSavedStyle.setText(formatAiStrategyButtonLabel(picked, savedInferred));
                                GradientDrawable dBg = new GradientDrawable();
                                dBg.setColor(Color.parseColor(picked != null ? "#7C3AED" : "#334155"));
                                dBg.setCornerRadius(dp(6));
                                btnSavedStyle.setBackground(dBg);
                            }));
                    itemBox.addView(btnSavedStyle);
                }
                body.addView(itemBox);
            }
        }

        btnSelectAllValid.setOnClickListener(v -> {
            if (activeVal.valid) {
                cbActive.setChecked(true);
            }
            for (Map.Entry<String, android.widget.CheckBox> e : savedCheckboxes.entrySet()) {
                String id = e.getKey();
                boolean valid = Boolean.TRUE.equals(savedValidMap.get(id));
                boolean isMirror = Boolean.TRUE.equals(savedMirrorActiveMap.get(id));
                // Avoid double-entering the exact same deck twice when active deck is already checked
                boolean shouldCheck = valid && !(activeVal.valid && isMirror);
                e.getValue().setChecked(shouldCheck);
            }
        });
        btnOnlyActive.setOnClickListener(v -> {
            if (activeVal.valid) cbActive.setChecked(true);
            for (android.widget.CheckBox cb : savedCheckboxes.values()) {
                cb.setChecked(false);
            }
        });
        btnOnlyAi.setOnClickListener(v -> {
            cbActive.setChecked(false);
            for (android.widget.CheckBox cb : savedCheckboxes.values()) {
                cb.setChecked(false);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("📋 选择参赛卡组与 AI 策略")
                .setView(scroll)
                .setPositiveButton("确定", (d, w) -> {
                    ladderIncludeActiveDeck = tempIncludeActive[0];
                    selectedLadderSavedDeckIds.clear();
                    selectedLadderSavedDeckIds.addAll(tempSavedIds);
                    profile.activeDeckAiStyle = tempActiveStyle[0];
                    if (profile.savedDecks != null) {
                        for (RpgProfile.SavedDeck sd : profile.savedDecks) {
                            if (sd != null && sd.id != null && tempSavedStyles.containsKey(sd.id)) {
                                sd.aiStyleOverride = tempSavedStyles.get(sd.id);
                            }
                        }
                    }
                    saveProfileSafe();
                    renderCurrentTab();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startLeagueFlow(final String mode) {
        final RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未注册 YGOMobile 决斗引擎桥接器", Toast.LENGTH_LONG).show();
            return;
        }
        List<RoundRobinLeagueEngine.PlayerDeckEntry> selectedPlayerDecks = collectSelectedValidLadderPlayerDecks();
        if (!selectedPlayerDecks.isEmpty()) {
            runLeague(mode, selectedPlayerDecks, null, 0);
            return;
        }
        // User explicitly unchecked everything or active deck is invalid: confirm before running AI-only
        DeckValidator.ValidationResult val = DeckValidator.validatePlayerDeck(profile, catalog);
        boolean hasSaved = profile.savedDecks != null && !profile.savedDecks.isEmpty();
        if (!ladderIncludeActiveDeck && selectedLadderSavedDeckIds.isEmpty()) {
            // User explicitly chose AI-only in the deck selector
            runLeague(mode, java.util.Collections.emptyList(), null, 0);
            return;
        }
        String errText = val.valid
                ? "当前未勾选任何合规自组卡组。"
                : TextUtils.join("\n", val.errors);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(val.valid ? "未勾选自组卡组" : "当前 RPG 卡组未通过校验")
                .setMessage(errText + "\n\n是否选择已存储卡组、先去组卡，或仅运行现环境所有 AI 之间的天梯赛？")
                .setPositiveButton("仅 AI 参赛", (d, w) -> runLeague(mode, java.util.Collections.emptyList(), null, 0))
                .setNegativeButton("取消", null);
        if (hasSaved) {
            b.setNeutralButton("选已存卡组", (d, w) -> openLadderDeckSelectorDialog());
        } else {
            b.setNeutralButton("去组卡", (d, w) -> selectTabById(TAB_DECK));
        }
        b.show();
    }

    private void appendExtraSwissRounds(final RoundRobinLeagueEngine.LeagueResult baseResult, final int extraRounds) {
        if (baseResult == null || baseResult.standings == null || baseResult.standings.size() < 2) return;
        runLeague(RoundRobinLeagueEngine.MODE_SWISS_ELO, java.util.Collections.emptyList(), baseResult, extraRounds);
    }

    private void runLeague(final String mode,
                           final List<RoundRobinLeagueEngine.PlayerDeckEntry> selectedPlayerDecks,
                           final RoundRobinLeagueEngine.LeagueResult appendBase,
                           final int extraSwissRounds) {
        final RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) {
            Toast.makeText(this, "未注册 YGOMobile 决斗引擎桥接器", Toast.LENGTH_LONG).show();
            return;
        }
        final boolean isSwiss = RoundRobinLeagueEngine.MODE_SWISS_ELO.equals(mode);
        final boolean isAppending = isSwiss && appendBase != null && extraSwissRounds > 0;
        final String curDate = isAppending ? appendBase.timelineDate : profile.currentTimelineDate;
        final OcgRuleSet activeRule = isAppending && appendBase.ruleSet != null
                ? appendBase.ruleSet
                : TimelineEngine.getActiveRuleSet(catalog, curDate);

        final List<RoundRobinLeagueEngine.Participant> participants;
        if (isAppending) {
            participants = new ArrayList<>(appendBase.standings);
        } else {
            long deckSeed = ("rpg_ladder_deck_" + curDate).hashCode() * 31L + 19990204L;
            participants = RoundRobinLeagueEngine.buildParticipants(
                    catalog, curDate, activeRule,
                    selectedPlayerDecks,
                    new Random(deckSeed));
        }

        if (participants.size() < 2) {
            Toast.makeText(this, "参赛选手不足 2 名，无法开启赛事", Toast.LENGTH_SHORT).show();
            return;
        }

        final int gamesPerPair = isSwiss
                ? RoundRobinLeagueEngine.DEFAULT_SWISS_GAMES_PER_PAIR
                : RoundRobinLeagueEngine.DEFAULT_GAMES_PER_PAIR;
        final int swissRoundsToRun = isAppending
                ? extraSwissRounds
                : RoundRobinLeagueEngine.recommendedSwissRounds(participants.size());
        final int totalPairsThisRun = isSwiss
                ? (swissRoundsToRun * (participants.size() / 2))
                : (participants.size() * (participants.size() - 1) / 2);
        final int totalGamesThisRun = totalPairsThisRun * gamesPerPair;
        final AtomicBoolean cancelFlag = new AtomicBoolean(false);
        final long startMs = System.currentTimeMillis();

        LinearLayout dlgBody = new LinearLayout(this);
        dlgBody.setOrientation(LinearLayout.VERTICAL);
        dlgBody.setPadding(dp(20), dp(14), dp(20), dp(10));

        final ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(Math.max(1, totalGamesThisRun));
        progressBar.setProgress(0);
        dlgBody.addView(progressBar);

        final TextView tvStatus = makeSubtleText(String.format(Locale.US,
                "准备就绪：%d 名选手 · 本次 %d 组对阵 · 共 %d 局\n正在启动真实 ocgcore 引擎…",
                participants.size(), totalPairsThisRun, totalGamesThisRun));
        tvStatus.setTextColor(Color.parseColor("#E2E8F0"));
        tvStatus.setPadding(0, dp(10), 0, 0);
        dlgBody.addView(tvStatus);

        String dlgTitle = isAppending
                ? ("⚡ 追加 " + extraSwissRounds + " 轮瑞士轮（提升积分精度）")
                : (isSwiss ? "⚡ 全环境瑞士轮天梯赛进行中" : "🏆 全环境 AI 循环赛进行中");
        final AlertDialog progDlg = new AlertDialog.Builder(this)
                .setTitle(dlgTitle)
                .setView(dlgBody)
                .setCancelable(false)
                .setNegativeButton("取消", (d, w) -> cancelFlag.set(true))
                .show();

        new Thread(() -> {
            RoundRobinLeagueEngine.ProgressListener listener = (doneGames, totGames, donePairs, totPairs, label) -> {
                final long sec = (System.currentTimeMillis() - startMs) / 1000L;
                runOnUiThread(() -> {
                    progressBar.setProgress(doneGames);
                    int pct = totGames > 0 ? (doneGames * 100 / totGames) : 0;
                    tvStatus.setText(String.format(Locale.US,
                            "进度：%d / %d 局（%d%%）· 第 %d / %d 组 · 已耗时 %ds\n当前对阵：%s",
                            doneGames, totGames, pct,
                            Math.min(donePairs + 1, totPairs), totPairs, sec, label));
                });
            };
            final RoundRobinLeagueEngine.LeagueResult result = isSwiss
                    ? RoundRobinLeagueEngine.runSwissLadder(
                            catalog, bridge, participants, activeRule, curDate,
                            swissRoundsToRun, gamesPerPair,
                            isAppending ? appendBase.roundsPlayed : 0,
                            isAppending ? appendBase.elapsedMs : 0L,
                            listener, cancelFlag, new Random())
                    : RoundRobinLeagueEngine.runRoundRobin(
                            catalog, bridge, participants, activeRule, curDate,
                            gamesPerPair, listener, cancelFlag, new Random());

            runOnUiThread(() -> {
                if (progDlg.isShowing()) progDlg.dismiss();
                if (result.completedGames > 0) {
                    lastLeagueResult = result;
                    String modeName = result.isSwissMode() ? "瑞士轮天梯" : "全量循环赛";
                    String msg = result.cancelled
                            ? (modeName + "已取消（累计 " + result.completedGames + " 局，展示当前排名）")
                            : String.format(Locale.US, "%s完成！累计 %d 局（耗时 %.1fs）",
                            modeName, result.completedGames, result.elapsedMs / 1000.0);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    renderCurrentTab();
                } else {
                    Toast.makeText(this, "赛事已取消", Toast.LENGTH_SHORT).show();
                }
            });
        }, "RpgAiLadderLeague").start();
    }

    private void renderLeagueStandingsCard(final RoundRobinLeagueEngine.LeagueResult res) {
        LinearLayout card = makeCardContainer();
        String headerText = res.isSwissMode()
                ? String.format(Locale.US,
                "⚡ 瑞士轮天梯积分榜（%s · %d名选手 · %d轮/%d局 · %.1fs）%s",
                res.timelineDate, res.standings.size(), res.roundsPlayed,
                res.completedGames, res.elapsedMs / 1000.0, res.cancelled ? " [已取消]" : "")
                : String.format(Locale.US,
                "🏅 全量循环赛排行榜（%s · %d名选手 · %d/%d局 · %.1fs）%s",
                res.timelineDate, res.standings.size(), res.completedGames,
                res.totalGames, res.elapsedMs / 1000.0, res.cancelled ? " [已取消]" : "");
        TextView title = makeTitleText(headerText);
        title.setTextColor(Color.parseColor("#38BDF8"));
        card.addView(title);
        card.addView(makeSubtleText(res.isSwissMode()
                ? "按 Bradley-Terry 全局天梯积分（Elo，基准 1500，自动计入对手强度 SOS）排名 · 点任意选手查对阵明细"
                : "按总胜场数降序排名（同时显示 Bradley-Terry Elo 积分与 S~D 评级 · 点任意选手查对阵明细）"));

        Map<String, RoundRobinLeagueEngine.Participant> byId = new HashMap<>();
        for (RoundRobinLeagueEngine.Participant p : res.standings) {
            byId.put(p.id, p);
        }

        for (int i = 0; i < res.standings.size(); i++) {
            final int rank = i + 1;
            final RoundRobinLeagueEngine.Participant p = res.standings.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(Color.parseColor(p.isPlayer ? "#1E3A5F" : "#0F172A"));
            rowBg.setCornerRadius(dp(8));
            rowBg.setStroke(dp(p.isPlayer ? 2 : 1),
                    Color.parseColor(p.isPlayer ? "#F59E0B" : "#334155"));
            row.setBackground(rowBg);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(6);
            row.setLayoutParams(rlp);

            String rankBadge;
            if (rank == 1) rankBadge = "🥇 #1";
            else if (rank == 2) rankBadge = "🥈 #2";
            else if (rank == 3) rankBadge = "🥉 #3";
            else rankBadge = "#" + rank;

            LinearLayout leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            leftCol.setGravity(Gravity.CENTER_HORIZONTAL);
            leftCol.setMinimumWidth(dp(50));
            TextView tvRank = new TextView(this);
            tvRank.setText(rankBadge);
            tvRank.setTextSize(12f);
            tvRank.setTypeface(null, android.graphics.Typeface.BOLD);
            tvRank.setTextColor(Color.parseColor(rank <= 3 ? "#FBBF24" : "#94A3B8"));
            leftCol.addView(tvRank);

            String tier = p.getTierLabel();
            TextView tvTier = new TextView(this);
            tvTier.setText(tier + "级");
            tvTier.setTextSize(10f);
            tvTier.setTypeface(null, android.graphics.Typeface.BOLD);
            String tierColor;
            if ("S".equals(tier)) tierColor = "#F43F5E";
            else if ("A".equals(tier)) tierColor = "#F59E0B";
            else if ("B".equals(tier)) tierColor = "#38BDF8";
            else if ("C".equals(tier)) tierColor = "#34D399";
            else tierColor = "#94A3B8";
            tvTier.setTextColor(Color.parseColor(tierColor));
            leftCol.addView(tvTier);
            row.addView(leftCol);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            infoLp.setMarginStart(dp(6));
            infoCol.setLayoutParams(infoLp);
            TextView tvName = makeTitleText((p.isPlayer ? "⭐ " : "") + p.name);
            tvName.setTextSize(13f);
            if (p.isPlayer) tvName.setTextColor(Color.parseColor("#FDE68A"));
            infoCol.addView(tvName);

            String sub = (!p.deckName.isEmpty() ? p.deckName + " · " : "")
                    + RpgAiStrategy.getStyleDisplayName(p.style);
            TextView tvSub = makeSubtleText(sub);
            tvSub.setTextSize(11f);
            infoCol.addView(tvSub);
            row.addView(infoCol);

            LinearLayout statCol = new LinearLayout(this);
            statCol.setOrientation(LinearLayout.VERTICAL);
            statCol.setGravity(Gravity.END);
            double wr = p.getWinRatePercent();
            TextView tvMainStat = new TextView(this);
            tvMainStat.setText(String.format(Locale.US, "Elo %d ±%d · %.1f%%",
                    p.eloRating, p.eloStdError, wr));
            tvMainStat.setTextSize(12f);
            tvMainStat.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMainStat.setTextColor(Color.parseColor(
                    p.eloRating >= 1580 ? "#34D399" : (p.eloRating >= 1450 ? "#FBBF24" : "#F87171")));
            statCol.addView(tvMainStat);

            String rec = p.wins + "胜" + p.losses + "负"
                    + (p.draws > 0 ? (p.draws + "平") : "")
                    + " · 对手" + p.avgOpponentElo;
            TextView tvRec = makeSubtleText(rec);
            tvRec.setTextSize(10.5f);
            statCol.addView(tvRec);
            row.addView(statCol);

            row.setOnClickListener(v -> showLeagueParticipantDetail(rank, p, byId));
            card.addView(row);
        }

        Button copyBtn = makeActionButton("📋 复制榜单", "#0284C7");
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("rpg_ai_ladder", res.formatPlainTextReport()));
                Toast.makeText(this, "已复制天梯排行榜到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });
        if (res.isSwissMode() && res.timelineDate.equals(profile.currentTimelineDate)) {
            int addRounds = RoundRobinLeagueEngine.DEFAULT_SWISS_APPEND_ROUNDS;
            int addGames = addRounds * (res.standings.size() / 2) * RoundRobinLeagueEngine.DEFAULT_SWISS_GAMES_PER_PAIR;
            Button moreSwissBtn = makeActionButton("➕ 追加" + addRounds + "轮 (+" + addGames + "局)", "#059669");
            moreSwissBtn.setOnClickListener(v -> appendExtraSwissRounds(res, addRounds));
            card.addView(makeEqualWeightButtonRow(10, moreSwissBtn, copyBtn));
        } else {
            Button rerunBtn = makeActionButton("🔄 再赛一轮", "#059669");
            rerunBtn.setOnClickListener(v -> startLeagueFlow(res.mode));
            card.addView(makeEqualWeightButtonRow(10, copyBtn, rerunBtn));
        }

        layoutContent.addView(card);
    }

    private void showLeagueParticipantDetail(int rank,
                                             RoundRobinLeagueEngine.Participant p,
                                             Map<String, RoundRobinLeagueEngine.Participant> byId) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "第 %d 名 · [%s级] 天梯积分 Elo %d ±%d（对手均分 SOS %d）\n",
                rank, p.getTierLabel(), p.eloRating, p.eloStdError, p.avgOpponentElo));
        sb.append(String.format(Locale.US,
                "总战绩：%d 胜 %d 负 %d 平（胜率 %.1f%% · 平均 %.1f 回合）\n",
                p.wins, p.losses, p.draws, p.getWinRatePercent(), p.getAvgTurns()));
        if (!p.deckName.isEmpty()) {
            sb.append("使用卡组：").append(p.deckName).append("\n");
        }
        sb.append("AI 策略：").append(RpgAiStrategy.getStyleDisplayName(p.style)).append("\n\n");
        sb.append("--- 交手战绩明细 ---\n");
        for (Map.Entry<String, int[]> e : p.headToHead.entrySet()) {
            RoundRobinLeagueEngine.Participant opp = byId.get(e.getKey());
            String oppName = opp != null
                    ? ((opp.isPlayer ? "⭐ " : "") + opp.name + " (Elo " + opp.eloRating + ")")
                    : e.getKey();
            int[] h = e.getValue();
            int tot = h[0] + h[1] + h[2];
            double rate = tot > 0 ? (h[0] * 100.0 / tot) : 0.0;
            sb.append(String.format(Locale.US, "• vs %s：%d胜 %d负%s (%.0f%%)\n",
                    oppName, h[0], h[1], h[2] > 0 ? (" " + h[2] + "平") : "", rate));
        }
        new AlertDialog.Builder(this)
                .setTitle((p.isPlayer ? "⭐ " : "") + p.name + " · 天梯战绩明细")
                .setMessage(sb.toString().trim())
                .setPositiveButton("知道了", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
