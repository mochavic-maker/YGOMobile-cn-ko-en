package cn.garymb.ygomobile.rpg;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Collections;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.rpg.bridge.RpgDuelHostBridge;
import cn.garymb.ygomobile.rpg.bridge.RpgSessionManager;
import cn.garymb.ygomobile.rpg.engine.DuelRewardEngine;
import cn.garymb.ygomobile.rpg.model.AiOpponent;
import cn.garymb.ygomobile.rpg.model.OcgBanlist;
import cn.garymb.ygomobile.rpg.model.OcgRuleSet;
import cn.garymb.ygomobile.rpg.ui.RpgHomeActivity;
import cn.garymb.ygomobile.ui.dialogs.lobby.PlayerWaitingDialog;

/**
 * Thin integration bridge between YGOMobile (mobile/) and the isolated :rpg-mode module.
 * Keeping all wiring in this dedicated new file ensures upstream YGOMobile merges
 * touch only two 1-line hook calls (MainMenuDialog & EngineCallbackDelegate).
 */
public final class RpgIntegration {
    private static final String RPG_BUTTON_TAG = "btn_menu_rpg_chronicle";

    private RpgIntegration() {}

    /**
     * Dynamically injects the "OCG RPG 模式" button into MainMenuDialog without modifying
     * popup_window_main_menu.xml, preventing layout XML merge conflicts with upstream.
     */
    public static void attachMainMenuButton(final YGOProActivity activity, View mainMenuRoot) {
        if (activity == null || mainMenuRoot == null) return;
        registerBridge(activity);

        LinearLayout container = mainMenuRoot.findViewById(R.id.layout_menu_buttons);
        if (container == null || container.findViewWithTag(RPG_BUTTON_TAG) != null) {
            return;
        }

        LinearLayout btn = new LinearLayout(activity);
        btn.setTag(RPG_BUTTON_TAG);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setBackgroundResource(R.drawable.stitlebar);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(2 * activity.getResources().getDisplayMetrics().density);
        btn.setLayoutParams(lp);

        TextView subtitle = new TextView(activity);
        subtitle.setText("OCG 编年史 Demo");
        subtitle.setTextColor(activity.getResources().getColor(R.color.white));
        subtitle.setTextSize(10f);
        subtitle.setPadding(Math.round(20 * activity.getResources().getDisplayMetrics().density), 0, 0, 0);

        TextView title = new TextView(activity);
        title.setText("RPG 模式");
        title.setTextColor(activity.getResources().getColor(R.color.white));
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        title.setLayoutParams(titleLp);

        btn.addView(subtitle);
        btn.addView(title);
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(activity, RpgHomeActivity.class);
            activity.startActivity(intent);
        });

        // Insert right below "单人游戏" (index 2)
        int insertIndex = Math.min(2, container.getChildCount());
        container.addView(btn, insertIndex);
    }

    /**
     * Hook invoked from EngineCallbackDelegate.onDuelResult(winner, reason).
     * Only settles rewards when the duel was initiated by RPG Mode, and schedules
     * automatic navigation back to RPG Mode's opponent/tournament screen once the duel closes.
     */
    public static void onDuelResult(final YGOProActivity activity, int winner, boolean selfWon) {
        if (activity == null) {
            return;
        }
        if (!RpgSessionManager.hasActiveRpgDuel()) {
            if (sActiveRpgDuelRuleSet != null) {
                sActiveRpgDuelRuleSet = null;
                cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.stopActive();
                activity.runOnUiThread(() -> scheduleReturnToRpg(activity, 7, null));
            }
            return;
        }
        sActiveRpgDuelRuleSet = null;
        cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.stopActive();
        boolean isDraw = (winner == 2);
        DuelRewardEngine.RewardSettlement settlement =
                RpgSessionManager.onDuelFinished(activity.getApplicationContext(), selfWon, isDraw);
        final int targetTab = RpgSessionManager.isLastDuelTournament()
                ? RpgHomeActivity.TAB_TOURNAMENT
                : RpgHomeActivity.TAB_AI_DUEL;
        if (settlement != null) {
            activity.runOnUiThread(() -> {
                Toast.makeText(
                        activity,
                        "RPG 结算：" + settlement.outcomeLabel + "，获得 +" + settlement.tokensEarned + " 代币",
                        Toast.LENGTH_LONG).show();
                scheduleReturnToRpg(activity, targetTab, null);
            });
        }
    }

    /**
     * Copies an RPG .ydk deck into YGOMobile's native deck/RPG/ directory and updates
     * AppsSettings.setLastDeckPath so PlayerWaitingDialog and DeckEditorManager load it.
     */
    private static File syncYdkToNativeDeckDir(String srcYdkPath) {
        File rpgDeckDir = new File(AppsSettings.get().getDeckDir(), "RPG");
        if (!rpgDeckDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            rpgDeckDir.mkdirs();
        }
        File destFile = new File(rpgDeckDir, "rpg_player_active.ydk");
        if (srcYdkPath != null && !srcYdkPath.isEmpty()) {
            File srcFile = new File(srcYdkPath);
            if (srcFile.exists()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(srcFile);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        String syncedPath = destFile.getAbsolutePath();
        AppsSettings.get().setLastDeckPath(syncedPath);
        AppsSettings.get().saveSettings("lastdeckpath", syncedPath);
        AppsSettings.get().saveSettings("lastdeck", "rpg_player_active");
        return destFile;
    }

    private static volatile java.util.Map<Integer, Integer> sRpgDeckAllowedCaps = null;
    private static volatile java.util.Map<Integer, Integer> sRpgDeckCurrentCounts = null;
    private static volatile OcgRuleSet sActiveRpgDuelRuleSet = null;

    /**
     * Writes historical OCG rule overrides (e.g. 1999-02-04 no-tribute summon/set, no Turn 1 draw,
     * no 6-card End Phase hand limit) to {@code <resourcePath>/rpg_rule_override.lua}.
     */
    public static boolean writeRuleOverrideScript(OcgRuleSet rule) {
        if (rule == null) return false;
        StringBuilder lua = new StringBuilder();
        lua.append("-- Auto-generated OCG Timeline RPG rule override: ").append(rule.id).append("\n");
        if (!rule.tributeSummonEnabled) {
            // 1999.02.04 公式ルール: Level 5+ monsters require 0 tributes to Normal Summon or Normal Set
            lua.append("local e1=Effect.GlobalEffect()\n");
            lua.append("e1:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e1:SetCode(EFFECT_DECREASE_TRIBUTE)\n");
            lua.append("e1:SetProperty(EFFECT_FLAG_IGNORE_IMMUNE)\n");
            lua.append("e1:SetTargetRange(LOCATION_HAND,LOCATION_HAND)\n");
            lua.append("e1:SetValue(0x20002)\n");
            lua.append("Duel.RegisterEffect(e1,0)\n");

            lua.append("local e2=Effect.GlobalEffect()\n");
            lua.append("e2:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e2:SetCode(EFFECT_DECREASE_TRIBUTE_SET)\n");
            lua.append("e2:SetProperty(EFFECT_FLAG_IGNORE_IMMUNE)\n");
            lua.append("e2:SetTargetRange(LOCATION_HAND,LOCATION_HAND)\n");
            lua.append("e2:SetValue(0x20002)\n");
            lua.append("Duel.RegisterEffect(e2,0)\n");
        }
        if (!rule.firstTurnDrawEnabled) {
            // 1999 Series 1 Official & Expert Rules: Turn 1 player does not draw in Draw Phase
            lua.append("local e3=Effect.GlobalEffect()\n");
            lua.append("e3:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e3:SetProperty(EFFECT_FLAG_PLAYER_TARGET)\n");
            lua.append("e3:SetCode(EFFECT_DRAW_COUNT)\n");
            lua.append("e3:SetTargetRange(1,1)\n");
            lua.append("e3:SetCondition(function(e) return Duel.GetTurnCount()==1 end)\n");
            lua.append("e3:SetValue(0)\n");
            lua.append("Duel.RegisterEffect(e3,0)\n");
        }
        if (rule.deckOutCompareLp) {
            // 1999 Series 1 rules had no 6-card hand size limit in End Phase
            lua.append("local e4=Effect.GlobalEffect()\n");
            lua.append("e4:SetType(EFFECT_TYPE_FIELD)\n");
            lua.append("e4:SetProperty(EFFECT_FLAG_PLAYER_TARGET)\n");
            lua.append("e4:SetCode(EFFECT_HAND_LIMIT)\n");
            lua.append("e4:SetTargetRange(1,1)\n");
            lua.append("e4:SetValue(100)\n");
            lua.append("Duel.RegisterEffect(e4,0)\n");
        }

        File ruleFile = new File(AppsSettings.get().getResourcePath(), "rpg_rule_override.lua");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(ruleFile)) {
            fos.write(lua.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            android.util.Log.e("RpgIntegration", "Failed to write rpg_rule_override.lua", e);
            return false;
        }
    }

    /**
     * Hook called from ServerDuelBase (and ServerDuel undo rebuild) right before OcgDuelEngine.startDuel().
     * Injects historical OCG rule overrides directly into ocgcore via Lua GlobalEffects.
     */
    public static void applyActiveRpgRulesToDuel(long pduel) {
        OcgRuleSet rule = sActiveRpgDuelRuleSet;
        if (pduel == 0L || rule == null) return;
        if (!writeRuleOverrideScript(rule)) return;
        int res = cn.garymb.ygomobile.engine.OcgDuelEngine.preloadScript(pduel, "./rpg_rule_override.lua");
        android.util.Log.i("RpgIntegration", "Preloaded RPG rule override (" + rule.id
                + ", tribute=" + rule.tributeSummonEnabled
                + ", t1Draw=" + rule.firstTurnDrawEnabled + ") -> res=" + res);
    }

    /**
     * Hook called from CardSearcherManagerBase.executeFilterAsync():
     * In RPG deck edit mode, hides cards the player does not own, cards unreleased on the current
     * timeline date, forbidden cards, and cards that have already reached their owned/banlist cap in the deck.
     */
    public static boolean isCardAllowedInDeckSearch(int cardCode) {
        java.util.Map<Integer, Integer> caps = sRpgDeckAllowedCaps;
        if (caps == null) return true;
        Integer cap = caps.get(cardCode);
        if (cap == null || cap <= 0) return false;
        java.util.Map<Integer, Integer> counts = sRpgDeckCurrentCounts;
        int inDeck = counts != null ? counts.getOrDefault(cardCode, 0) : 0;
        return inDeck < cap;
    }

    /**
     * Hook called from DeckEditorManagerBase.checkLimit():
     * Prevents adding unowned, unreleased, or over-owned/over-banlist copies in RPG deck edit mode.
     */
    public static boolean canAddCardInRpgDeckEditor(int cardCode, cn.garymb.ygomobile.bean.DeckInfo currentDeck) {
        java.util.Map<Integer, Integer> caps = sRpgDeckAllowedCaps;
        if (caps == null) return true;
        Integer cap = caps.get(cardCode);
        if (cap == null || cap <= 0) return false;
        int count = 0;
        if (currentDeck != null) {
            if (currentDeck.mainCards != null) {
                for (ocgcore.data.Card c : currentDeck.mainCards) if (c != null && c.Code == cardCode) count++;
            }
            if (currentDeck.extraCards != null) {
                for (ocgcore.data.Card c : currentDeck.extraCards) if (c != null && c.Code == cardCode) count++;
            }
            if (currentDeck.sideCards != null) {
                for (ocgcore.data.Card c : currentDeck.sideCards) if (c != null && c.Code == cardCode) count++;
            }
        }
        return count < cap;
    }

    /**
     * Hook called from DeckEditorManagerBase.notifyDeckChanged():
     * Recomputes current in-deck copy counts and refreshes the search list so maxed-out cards
     * vanish immediately and removed cards reappear immediately.
     */
    public static void onDeckEditorDeckChanged(cn.garymb.ygomobile.bean.DeckInfo currentDeck,
                                               cn.garymb.ygomobile.game.deck.CardSearcherManagerBase searcher) {
        if (sRpgDeckAllowedCaps == null) return;
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        if (currentDeck != null) {
            if (currentDeck.mainCards != null) {
                for (ocgcore.data.Card c : currentDeck.mainCards) {
                    if (c != null) counts.put(c.Code, counts.getOrDefault(c.Code, 0) + 1);
                }
            }
            if (currentDeck.extraCards != null) {
                for (ocgcore.data.Card c : currentDeck.extraCards) {
                    if (c != null) counts.put(c.Code, counts.getOrDefault(c.Code, 0) + 1);
                }
            }
            if (currentDeck.sideCards != null) {
                for (ocgcore.data.Card c : currentDeck.sideCards) {
                    if (c != null) counts.put(c.Code, counts.getOrDefault(c.Code, 0) + 1);
                }
            }
        }
        sRpgDeckCurrentCounts = counts;
        if (searcher != null) {
            searcher.refreshRpgSearch();
        }
    }

    private static java.util.Map<Integer, Integer> countCardsInYdk(File ydkFile) {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        if (ydkFile == null || !ydkFile.exists()) return counts;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(ydkFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                try {
                    int code = Integer.parseInt(line);
                    counts.put(code, counts.getOrDefault(code, 0) + 1);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return counts;
    }

    /**
     * Watches for YGOProActivity's MainMenuDialog to reappear (after duel end or native deck editor exit),
     * optionally parses the edited .ydk back into RpgSessionManager, and reopens RpgHomeActivity on targetTab.
     */
    private static void scheduleReturnToRpg(final YGOProActivity activity,
                                            final int targetTab,
                                            final File editedYdkToSyncBack) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        final View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor == null) return;

        final long deadlineMs = System.currentTimeMillis() + 600_000L; // up to 10 min for deck editing / replay save
        final boolean[] sawMenuHidden = new boolean[]{!activity.getMainMenuDialog().isShowing()};
        final Runnable watcher = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    sRpgDeckAllowedCaps = null;
                    sRpgDeckCurrentCounts = null;
                    return;
                }
                if (System.currentTimeMillis() > deadlineMs) {
                    sRpgDeckAllowedCaps = null;
                    sRpgDeckCurrentCounts = null;
                    return;
                }

                boolean menuShowing = activity.getMainMenuDialog() != null
                        && activity.getMainMenuDialog().isShowing();
                if (!menuShowing) {
                    sawMenuHidden[0] = true;
                    decor.postDelayed(this, 250L);
                    return;
                }
                if (!sawMenuHidden[0]) {
                    decor.postDelayed(this, 250L);
                    return;
                }

                // MainMenuDialog just reappeared after duel end or exiting native DeckEditor!
                sRpgDeckAllowedCaps = null;
                sRpgDeckCurrentCounts = null;
                if (editedYdkToSyncBack != null && editedYdkToSyncBack.exists()) {
                    parseAndPublishEditedDeck(editedYdkToSyncBack);
                }
                if (editedYdkToSyncBack == null && targetTab == RpgHomeActivity.TAB_AI_DUEL) {
                    // Restore lastdeckpath to player's RPG deck if we just exited readonly AI deck viewer
                    File playerRpgDeck = new File(new File(AppsSettings.get().getDeckDir(), "RPG"), "rpg_player_active.ydk");
                    if (playerRpgDeck.exists()) {
                        String p = playerRpgDeck.getAbsolutePath();
                        AppsSettings.get().setLastDeckPath(p);
                        AppsSettings.get().saveSettings("lastdeckpath", p);
                        AppsSettings.get().saveSettings("lastdeck", "rpg_player_active");
                    }
                }
                Intent intent = new Intent(activity, RpgHomeActivity.class);
                intent.putExtra(RpgHomeActivity.EXTRA_INITIAL_TAB, targetTab);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
            }
        };
        decor.postDelayed(watcher, 500L);
    }

    private static void parseAndPublishEditedDeck(File ydkFile) {
        java.util.List<Integer> main = new java.util.ArrayList<>();
        java.util.List<Integer> extra = new java.util.ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(ydkFile))) {
            String line;
            int section = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("#main")) {
                    section = 1;
                    continue;
                }
                if (line.equalsIgnoreCase("#extra")) {
                    section = 2;
                    continue;
                }
                if (line.equalsIgnoreCase("!side")) {
                    section = 3;
                    continue;
                }
                if (line.startsWith("#")) continue;
                try {
                    int code = Integer.parseInt(line);
                    if (section == 1) main.add(code);
                    else if (section == 2) extra.add(code);
                } catch (NumberFormatException ignored) {
                }
            }
            RpgSessionManager.setPendingEditedDeck(main, extra);
        } catch (Exception ignored) {
        }
    }

    private static void registerBridge(final YGOProActivity activity) {
        RpgSessionManager.registerHostBridge(new RpgDuelHostBridge() {
            @Override
            public void startRpgAiDuel(Context context,
                                       AiOpponent opponent,
                                       String playerDeckYdkPath,
                                       String aiDeckYdkPath,
                                       OcgBanlist activeBanlist,
                                       OcgRuleSet activeRuleSet,
                                       String currentTimelineDate) {
                activity.runOnUiThread(() -> {
                    // Record active historical OCG rule set so ServerDuelBase injects matching Lua rule overrides
                    sActiveRpgDuelRuleSet = activeRuleSet;

                    // Sync player's validated RPG .ydk into native deck/RPG/ and register via setLastDeckPath
                    // so PlayerWaitingDialog loads the player's actual RPG deck instead of the default sample deck.
                    syncYdkToNativeDeckDir(playerDeckYdkPath);

                    if (activeRuleSet != null) {
                        String ruleSummary = activeRuleSet.name
                                + (activeRuleSet.tributeSummonEnabled ? "（上级需祭品" : "（上级免祭品")
                                + (activeRuleSet.firstTurnDrawEnabled ? " · 先攻抽卡）" : " · 先攻不抽卡）");
                        Toast.makeText(activity, "已应用 RPG 规则：" + ruleSummary, Toast.LENGTH_LONG).show();
                    }

                    GameEngine engine = activity.getEngine();
                    engine.setBotMode(true);
                    engine.setPlayerName(Constants.PlayerName);

                    // Automatically apply the latest OCG rule version at currentTimelineDate
                    int duelMode = 0; // Single duel
                    int duelRule = activeRuleSet != null ? activeRuleSet.ygoproDuelRule : 1;
                    int startLp = activeRuleSet != null ? activeRuleSet.startLp : 8000;
                    int startHand = activeRuleSet != null ? activeRuleSet.startHand : 5;
                    engine.startLocalServerWithSettings(
                            0, 5, duelMode, duelRule,
                            true, false,
                            startLp, startHand, 1, 0,
                            "OCG RPG [" + currentTimelineDate + "]", "");
                    engine.gameLflist = 0;
                    engine.gameRule = 5;
                    engine.gameMode = duelMode;
                    engine.field.dInfo.duelRule = duelRule;
                    engine.gameNoCheckDeck = 1;
                    engine.gameNoShuffleDeck = 0;
                    engine.gameStartLp = startLp;
                    engine.gameStartHand = startHand;
                    engine.gameDrawCount = 1;
                    engine.gameTimeLimit = 0;
                    engine.hasJoinRoomInfoCache = true;

                    PlayerWaitingDialog.showPlayerWaitingForBotHost(activity);

                    // Launch dedicated pure-Java 1999 OCG archetype-aware AI client directly
                    // against the local LanGameServer (replaces generic WindBot Deck=Lucky).
                    cn.garymb.ygomobile.rpg.model.RpgCatalog catalog = RpgSessionManager.getCachedCatalog();
                    if (catalog == null) {
                        try {
                            catalog = cn.garymb.ygomobile.rpg.data.RpgCatalogLoader
                                    .loadFromAssets(activity.getApplicationContext());
                            RpgSessionManager.setCachedCatalog(catalog);
                        } catch (Exception ignored) {
                        }
                    }
                    cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.launchAsync(
                            "127.0.0.1",
                            7911,
                            Constants.PRO_VERSION,
                            opponent,
                            aiDeckYdkPath,
                            catalog,
                            activeRuleSet,
                            currentTimelineDate);
                });
            }

            @Override
            public void openNativeDeckEditor(Context context,
                                             String playerDeckYdkPath,
                                             java.util.Map<Integer, Integer> allowedCardCaps) {
                activity.runOnUiThread(() -> {
                    File syncedFile = syncYdkToNativeDeckDir(playerDeckYdkPath);
                    sRpgDeckAllowedCaps = allowedCardCaps != null
                            ? new java.util.HashMap<>(allowedCardCaps)
                            : new java.util.HashMap<>();
                    sRpgDeckCurrentCounts = countCardsInYdk(syncedFile);
                    activity.showDeckEditorView();
                    // Load the exact RPG deck & filter search panel to owned & still-available RPG cards
                    View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
                    if (decor != null) {
                        decor.postDelayed(() -> {
                            try {
                                java.lang.reflect.Field f = cn.garymb.ygomobile.YGOProActivityBase.class
                                        .getDeclaredField("deckEditorManager");
                                f.setAccessible(true);
                                Object mgr = f.get(activity);
                                if (mgr instanceof cn.garymb.ygomobile.game.deck.DeckEditorManager) {
                                    cn.garymb.ygomobile.game.deck.DeckEditorManager dem =
                                            (cn.garymb.ygomobile.game.deck.DeckEditorManager) mgr;
                                    dem.openDeckForRpg(syncedFile.getAbsolutePath(), "rpg_player_active", "RPG");
                                    cn.garymb.ygomobile.game.deck.CardSearcherManager searcher =
                                            dem.getCardSearcherManager();
                                    if (searcher != null) searcher.refreshRpgSearch();
                                }
                            } catch (Throwable ignored) {
                            }
                        }, 150L);
                    }
                    Toast.makeText(activity,
                            "RPG 组卡限制已启用：仅显示已拥有且未达上限的卡片，保存并退出后自动同步",
                            Toast.LENGTH_LONG).show();
                    scheduleReturnToRpg(activity, RpgHomeActivity.TAB_DECK, syncedFile);
                });
            }

            @Override
            public void openReadonlyAiDeckViewer(Context context,
                                                 String aiDeckYdkPath,
                                                 String displayTitle) {
                activity.runOnUiThread(() -> {
                    // Place the preview file under aiDeckDir so DeckHistory.isReadonlyDeck()
                    // automatically locks the native DeckEditor into full read-only mode.
                    File aiDir = new File(AppsSettings.get().getAiDeckDir());
                    if (!aiDir.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        aiDir.mkdirs();
                    }
                    File previewFile = new File(aiDir, "RPG_AI_preview.ydk");
                    if (aiDeckYdkPath != null && !aiDeckYdkPath.isEmpty()) {
                        File src = new File(aiDeckYdkPath);
                        if (src.exists()) {
                            try (java.io.FileInputStream in = new java.io.FileInputStream(src);
                                 java.io.FileOutputStream out = new java.io.FileOutputStream(previewFile)) {
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = in.read(buf)) > 0) {
                                    out.write(buf, 0, len);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    // Empty allowed caps double-locks card search and card addition
                    sRpgDeckAllowedCaps = Collections.emptyMap();
                    sRpgDeckCurrentCounts = Collections.emptyMap();

                    final String label = (displayTitle != null && !displayTitle.isEmpty())
                            ? displayTitle : "AI 卡组";
                    activity.showDeckEditorView();
                    View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
                    if (decor != null) {
                        decor.postDelayed(() -> {
                            try {
                                java.lang.reflect.Field f = cn.garymb.ygomobile.YGOProActivityBase.class
                                        .getDeclaredField("deckEditorManager");
                                f.setAccessible(true);
                                Object mgr = f.get(activity);
                                if (mgr instanceof cn.garymb.ygomobile.game.deck.DeckEditorManager) {
                                    cn.garymb.ygomobile.game.deck.DeckEditorManager dem =
                                            (cn.garymb.ygomobile.game.deck.DeckEditorManager) mgr;
                                    dem.openDeckForRpg(previewFile.getAbsolutePath(), label, "AI卡组(只读)");
                                    cn.garymb.ygomobile.game.deck.CardSearcherManager searcher =
                                            dem.getCardSearcherManager();
                                    if (searcher != null) searcher.refreshRpgSearch();
                                }
                            } catch (Throwable ignored) {
                            }
                        }, 150L);
                    }
                    Toast.makeText(activity,
                            "只读查看 AI 卡组：" + label + "（不可修改，点「退出编辑」返回选对手页）",
                            Toast.LENGTH_LONG).show();
                    scheduleReturnToRpg(activity, RpgHomeActivity.TAB_AI_DUEL, null);
                });
            }

            @Override
            public byte[] loadCardImageBytes(int cardId) {
                return cn.garymb.ygomobile.loader.ImageLoader.findCardImageData(cardId);
            }

            @Override
            public cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.DuelResult runHeadlessOcgDuel(
                    cn.garymb.ygomobile.rpg.model.RpgCatalog catalog,
                    String sideAName, java.util.List<Integer> mainA, java.util.List<Integer> extraA, String styleA,
                    String sideBName, java.util.List<Integer> mainB, java.util.List<Integer> extraB, String styleB,
                    OcgRuleSet activeRuleSet,
                    String currentTimelineDate,
                    boolean sideAFirst,
                    boolean recordReplay,
                    java.util.Random rng) {
                return RpgOcgHeadlessDuelRunner.runSingleDuel(
                        catalog,
                        sideAName, mainA, extraA, styleA,
                        sideBName, mainB, extraB, styleB,
                        activeRuleSet, currentTimelineDate,
                        sideAFirst, recordReplay, rng);
            }

            @Override
            public cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.BatchResult runHeadlessOcgBatch(
                    cn.garymb.ygomobile.rpg.model.RpgCatalog catalog,
                    String sideAName, java.util.List<Integer> mainA, java.util.List<Integer> extraA, String styleA,
                    String sideBName, java.util.List<Integer> mainB, java.util.List<Integer> extraB, String styleB,
                    OcgRuleSet activeRuleSet,
                    String currentTimelineDate,
                    int gameCount,
                    cn.garymb.ygomobile.rpg.ai.RpgAiAutoDuelModels.ProgressListener progressListener,
                    java.util.Random rng) {
                return RpgOcgHeadlessDuelRunner.runBatch(
                        catalog,
                        sideAName, mainA, extraA, styleA,
                        sideBName, mainB, extraB, styleB,
                        activeRuleSet, currentTimelineDate,
                        gameCount, progressListener, rng);
            }

            @Override
            public void startRpgAiSpectateDuel(
                    Context context,
                    String sideAName, java.util.List<Integer> mainA, java.util.List<Integer> extraA, String styleA,
                    String sideBName, java.util.List<Integer> mainB, java.util.List<Integer> extraB, String styleB,
                    OcgRuleSet activeRuleSet,
                    String currentTimelineDate) {
                activity.runOnUiThread(() -> {
                    sActiveRpgDuelRuleSet = activeRuleSet;
                    GameEngine engine = activity.getEngine();
                    engine.setBotMode(true);
                    engine.setPlayerName("观战席·" + Constants.PlayerName);

                    int duelMode = 0;
                    int duelRule = activeRuleSet != null ? activeRuleSet.ygoproDuelRule : 1;
                    int startLp = activeRuleSet != null ? activeRuleSet.startLp : 8000;
                    int startHand = activeRuleSet != null ? activeRuleSet.startHand : 5;
                    engine.startLocalServerWithSettings(
                            0, 5, duelMode, duelRule,
                            true, false,
                            startLp, startHand, 1, 0,
                            "OCG AI观战 [" + currentTimelineDate + "]", "");
                    engine.gameLflist = 0;
                    engine.gameRule = 5;
                    engine.gameMode = duelMode;
                    engine.field.dInfo.duelRule = duelRule;
                    engine.gameNoCheckDeck = 1;
                    engine.gameNoShuffleDeck = 0;
                    engine.gameStartLp = startLp;
                    engine.gameStartHand = startHand;
                    engine.gameDrawCount = 1;
                    engine.gameTimeLimit = 0;
                    engine.hasJoinRoomInfoCache = true;

                    PlayerWaitingDialog.showPlayerWaitingForBotHost(activity);

                    cn.garymb.ygomobile.rpg.model.RpgCatalog catalog = RpgSessionManager.getCachedCatalog();
                    if (catalog == null) {
                        try {
                            catalog = cn.garymb.ygomobile.rpg.data.RpgCatalogLoader
                                    .loadFromAssets(activity.getApplicationContext());
                            RpgSessionManager.setCachedCatalog(catalog);
                        } catch (Exception ignored) {
                        }
                    }
                    final cn.garymb.ygomobile.rpg.model.RpgCatalog finalCatalog = catalog;
                    final String effA = (styleA != null && !styleA.isEmpty())
                            ? styleA : cn.garymb.ygomobile.rpg.ai.RpgAiStrategy.inferStyleFromDeck(mainA, extraA, activeRuleSet);
                    final String effB = (styleB != null && !styleB.isEmpty())
                            ? styleB : cn.garymb.ygomobile.rpg.ai.RpgAiStrategy.inferStyleFromDeck(mainB, extraB, activeRuleSet);

                    // Move host to observer seat once connected, then launch Seat 0 (A) & Seat 1 (B) AIs
                    Thread spectateStarter = new Thread(() -> {
                        long deadline = System.currentTimeMillis() + 6000L;
                        while (System.currentTimeMillis() < deadline) {
                            if (engine.getClient() != null && engine.getClient().isConnected()) {
                                break;
                            }
                            try { Thread.sleep(40L); } catch (InterruptedException ignored) { return; }
                        }
                        try { Thread.sleep(200L); } catch (InterruptedException ignored) { return; }
                        if (engine.getClient() != null && engine.getClient().isConnected()) {
                            engine.getClient().sendToObserver();
                        }
                        try { Thread.sleep(150L); } catch (InterruptedException ignored) { return; }
                        cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.launchSpectatePairAsync(
                                "127.0.0.1", 7911, Constants.PRO_VERSION,
                                sideAName, mainA, extraA, effA,
                                sideBName, mainB, extraB, effB,
                                finalCatalog, activeRuleSet, currentTimelineDate);
                    }, "RpgAiSpectateLauncher");
                    spectateStarter.setDaemon(true);
                    spectateStarter.start();

                    Toast.makeText(activity,
                            "已切至观战席：等待两位 OCG 专属 AI 就座后点击「开始决斗」即可 3D 观战！",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
