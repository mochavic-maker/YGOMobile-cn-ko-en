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
import cn.garymb.ygomobile.rpg.util.RpgDiagLog;
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
        RpgDiagLog.init(activity);
        RpgDiagLog.log("Host", "attachMainMenuButton act=@" + Integer.toHexString(System.identityHashCode(activity))
                + " hasPendingTask=" + (sPendingHostTask != null));
        sHostActivity = activity;
        sAppContext = activity.getApplicationContext();
        registerBridge(activity);
        consumePendingHostTaskIfAny(activity);

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
                sActiveRpgDuelTimelineDate = null;
                final int backTab = sSpectateReturnTab;
                cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.stopActive();
                activity.runOnUiThread(() -> scheduleReturnToRpg(activity, backTab, null));
            }
            return;
        }
        sActiveRpgDuelRuleSet = null;
        sActiveRpgDuelTimelineDate = null;
        cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.stopActive();
        boolean isDraw = (winner == 2);
        DuelRewardEngine.RewardSettlement settlement =
                RpgSessionManager.onDuelFinished(activity.getApplicationContext(), selfWon, isDraw);
        final int targetTab = RpgSessionManager.getLastDuelReturnTab();
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

    private static final android.os.Handler sMainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static volatile YGOProActivity sHostActivity = null;
    private static volatile java.util.function.Consumer<YGOProActivity> sPendingHostTask = null;
    private static volatile int sActiveRpgReplayReturnTab = -1;
    private static volatile boolean sRpgReplayEndDialogShowing = false;
    private static volatile java.util.Map<Integer, Integer> sRpgDeckAllowedCaps = null;
    private static volatile java.util.Map<Integer, Integer> sRpgDeckCurrentCounts = null;
    private static volatile ocgcore.data.LimitList sRpgDeckLimitList = null;
    private static volatile OcgRuleSet sActiveRpgDuelRuleSet = null;
    private static volatile String sActiveRpgDuelTimelineDate = null;
    private static volatile int sSpectateReturnTab = RpgHomeActivity.TAB_AI_SHOWDOWN;
    private static volatile Context sAppContext = null;
    private static volatile String sActiveDuelPlayerAvatarId = null;
    private static volatile String sActiveDuelOpponentAvatarId = null;
    private static volatile cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.ReplayMeta sLastRpgDuelMeta = null;
    private static volatile boolean sActiveRpgLiveDuelSession = false;

    public static boolean isActiveRpgLiveDuelSession() {
        return sActiveRpgLiveDuelSession;
    }

    public static void clearActiveRpgLiveDuelSession() {
        sActiveRpgLiveDuelSession = false;
        sLastRpgDuelMeta = null;
    }

    /**
     * Called by {@link cn.garymb.ygomobile.DuelEndReplayHandler} right after a .yrp file is saved.
     * Persists RPG strategy / manual mode & era metadata as a lightweight sidecar JSON file beside
     * the replay so the user can later one-tap copy full AI-debugging logs from ReplayModeDialog.
     */
    public static void onReplayFileSaved(File yrpFile) {
        cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.ReplayMeta meta = sLastRpgDuelMeta;
        if (yrpFile == null || meta == null) return;
        cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.saveSidecar(yrpFile, meta);

        String p0 = null;
        String p1 = null;
        try {
            cn.garymb.ygomobile.game.replay.ReplayReader.ReplayData data =
                    cn.garymb.ygomobile.game.replay.ReplayReader.loadReplay(yrpFile.getAbsolutePath());
            if (data != null && data.playerNames != null) {
                if (data.playerNames.size() > 0) p0 = data.playerNames.get(0);
                if (data.playerNames.size() > 1) p1 = data.playerNames.get(1);
            }
        } catch (Throwable ignored) {
        }
        int returnTab = "RPG_SPECTATE".equals(meta.source)
                ? sSpectateReturnTab
                : RpgSessionManager.getLastDuelReturnTab();
        RpgSessionManager.setLastLiveReplay(yrpFile.getAbsolutePath(), p0, p1, returnTab);
    }

    /**
     * Parses a recorded .yrp file (plus optional .rpgmeta.json sidecar) into a structured duel log
     * containing both players' AI strategies / manual mode, complete Main/Extra decklists, and
     * detailed turn-by-turn engine action log.
     */
    public static String buildReplayLogText(File yrpFile) {
        if (yrpFile == null || !yrpFile.exists()) return null;
        cn.garymb.ygomobile.game.replay.ReplayReader.ReplayData data =
                cn.garymb.ygomobile.game.replay.ReplayReader.loadReplay(yrpFile.getAbsolutePath());
        if (data == null) return null;

        java.util.List<java.util.List<Integer>> mainDecks = new java.util.ArrayList<>();
        java.util.List<java.util.List<Integer>> extraDecks = new java.util.ArrayList<>();
        if (data.decks != null) {
            for (cn.garymb.ygomobile.game.replay.ReplayReader.DeckInfo d : data.decks) {
                mainDecks.add(d != null ? d.main : Collections.emptyList());
                extraDecks.add(d != null ? d.extra : Collections.emptyList());
            }
        }
        cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.ReplayMeta meta =
                cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.loadSidecar(yrpFile);
        final cn.garymb.ygomobile.rpg.model.RpgCatalog catalog = RpgSessionManager.getCachedCatalog();
        cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.CardNameResolver resolver = code -> {
            if (catalog != null && code > 0) {
                cn.garymb.ygomobile.rpg.model.RpgCard rc = catalog.findCard(code);
                if (rc != null) return rc.getDisplayName();
            }
            try {
                ocgcore.data.Card c = ocgcore.DataManager.get().getCardManager().getCard(code);
                if (c != null && c.Name != null && !c.Name.isEmpty()) {
                    return c.Name;
                }
            } catch (Throwable ignored) {
            }
            return "#" + code;
        };
        return cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.formatReplay(
                yrpFile.getName(),
                yrpFile.lastModified(),
                data.playerNames,
                data.params.startLp,
                data.params.startHand,
                data.params.drawCount,
                mainDecks,
                extraDecks,
                data.msgFrames,
                meta,
                resolver);
    }

    /**
     * Formats and exports a .yrp replay's full strategy/deck/action log as a .txt file
     * into the device Download/ directory for convenient file sharing.
     */
    public static boolean copyReplayLogToClipboard(Context context, String replayFilePath) {
        if (context == null || replayFilePath == null || replayFilePath.isEmpty()) return false;
        String logText = buildReplayLogText(new File(replayFilePath));
        if (logText == null || logText.isEmpty()) {
            Toast.makeText(context, "无法解析该录像的日志信息", Toast.LENGTH_SHORT).show();
            return false;
        }
        cn.garymb.ygomobile.rpg.util.RpgLogFileExporter.ExportResult res =
                cn.garymb.ygomobile.rpg.util.RpgLogFileExporter.saveDuelLogToDownloads(context, logText);
        if (res.success) {
            Toast.makeText(context, "已保存对战日志至 " + res.displayPath, Toast.LENGTH_LONG).show();
            return true;
        }
        Toast.makeText(context, "保存日志失败：" + res.errorMessage, Toast.LENGTH_LONG).show();
        return false;
    }

    private static void dispatchOnHost(Context callerContext,
                                       cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.ActiveScene scene,
                                       java.util.function.Consumer<YGOProActivity> task) {
        if (task == null) return;
        YGOProActivity host = sHostActivity;
        boolean alive = host != null && !host.isFinishing() && !host.isDestroyed();
        RpgDiagLog.log("Host", "dispatchOnHost scene=" + scene + " hostAlive=" + alive
                + (host != null ? (" act=@" + Integer.toHexString(System.identityHashCode(host))
                + " finishing=" + host.isFinishing() + " destroyed=" + host.isDestroyed()) : " host=null"));
        if (alive) {
            sPendingHostTask = null;
            host.runOnUiThread(() -> {
                if (host.isFinishing() || host.isDestroyed()) {
                    RpgDiagLog.log("Host", "dispatchOnHost host died before UI run -> queueForRecreatedHost scene=" + scene);
                    queueForRecreatedHost(callerContext, scene, task);
                    return;
                }
                task.accept(host);
            });
            return;
        }
        queueForRecreatedHost(callerContext, scene, task);
    }

    private static void queueForRecreatedHost(Context callerContext,
                                              cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.ActiveScene scene,
                                              java.util.function.Consumer<YGOProActivity> task) {
        RpgDiagLog.log("Host", "queueForRecreatedHost scene=" + scene);
        sPendingHostTask = task;
        sTransitionCoordinator.markPendingHostRecreate(scene);
        Context launchCtx = callerContext != null ? callerContext : sAppContext;
        if (launchCtx != null) {
            Intent intent = new Intent(launchCtx, YGOProActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (!(launchCtx instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            try {
                launchCtx.startActivity(intent);
            } catch (Throwable t) {
                RpgDiagLog.log("Host", "queueForRecreatedHost startActivity failed: " + t);
            }
        }
    }

    private static void consumePendingHostTaskIfAny(final YGOProActivity activity) {
        java.util.function.Consumer<YGOProActivity> pending = sPendingHostTask;
        if (pending != null && activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            RpgDiagLog.log("Host", "consumePendingHostTaskIfAny executing on act=@"
                    + Integer.toHexString(System.identityHashCode(activity)));
            sPendingHostTask = null;
            pending.accept(activity);
        }
    }

    /**
     * Hook called from {@link cn.garymb.ygomobile.YGOProActivityBase#quitReplay()}:
     * when the user exits an RPG 3D replay early via the replay control bar's Quit button,
     * returns directly to the originating RPG Mode tab instead of falling through to native
     * ReplayModeDialog / MainMenuDialog.
     */
    public static boolean handleRpgReplayQuit(final YGOProActivity activity) {
        int returnTab = sActiveRpgReplayReturnTab;
        if (returnTab < 0 || activity == null) {
            return false;
        }
        RpgDiagLog.log("Replay", "handleRpgReplayQuit -> returnTab=" + returnTab);
        returnToRpgFromReplay(activity, returnTab);
        return true;
    }

    private static void returnToRpgFromReplay(final YGOProActivity activity, final int returnTab) {
        RpgDiagLog.log("Replay", "returnToRpgFromReplay tab=" + returnTab);
        sActiveRpgReplayReturnTab = -1;
        sRpgReplayEndDialogShowing = false;
        sTransitionCoordinator.invalidatePendingTransitions();
        sActiveDuelPlayerAvatarId = null;
        sActiveDuelOpponentAvatarId = null;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        cn.garymb.ygomobile.game.replay.ReplayPlayer player = activity.getReplayPlayer();
        if (player != null) {
            player.detachListener();
            player.stop();
        }
        activity.dismissDuelDialogsForExit();
        if (activity.getCardDetailPanel() != null) {
            activity.getCardDetailPanel().hideReplayControls();
            activity.getCardDetailPanel().closeGameButtons();
        }
        activity.hideGameUI();
        activity.setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
        if (activity.getSoundManager() != null) {
            activity.getSoundManager().playBGM(cn.garymb.ygomobile.audio.SoundManager.BGM.MENU);
        }
        Intent back = new Intent(activity, RpgHomeActivity.class);
        back.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        back.putExtra(RpgHomeActivity.EXTRA_INITIAL_TAB, returnTab);
        activity.startActivity(back);
    }

    /**
     * Hook called from {@code DeckEditorManagerBase.refreshLimitList()} and
     * {@code DeckEditorManagerMid.setupRecyclerViews()}: returns the selected RPG timeline's
     * historical OCG {@link ocgcore.data.LimitList} while editing an RPG deck, or {@code null}
     * outside RPG Mode so normal YGOMobile uses the user's configured banlist.
     */
    public static ocgcore.data.LimitList getRpgDeckLimitListOverride() {
        return sRpgDeckLimitList;
    }

    private static ocgcore.data.LimitList buildRpgLimitList(OcgBanlist banlist) {
        String name = (banlist != null && banlist.name != null && !banlist.name.isEmpty())
                ? banlist.name : "OCG RPG 无禁限";
        ocgcore.data.LimitList ll = new ocgcore.data.LimitList(name);
        if (banlist != null) {
            if (banlist.forbidden != null) {
                for (Integer id : banlist.forbidden) {
                    if (id != null && id > 0) ll.addForbidden(id);
                }
            }
            if (banlist.limited != null) {
                for (Integer id : banlist.limited) {
                    if (id != null && id > 0) ll.addLimit(id);
                }
            }
            if (banlist.semiLimited != null) {
                for (Integer id : banlist.semiLimited) {
                    if (id != null && id > 0) ll.addSemiLimit(id);
                }
            }
        }
        return ll;
    }

    /**
     * Hook called from {@link cn.garymb.ygomobile.render.field.TextureLoader#getAvatar(boolean)}:
     * returns the active RPG AI opponent's portrait Bitmap during live RPG duels / 3D spectates / 3D replays,
     * or {@code null} to fall back to YGOMobile's standard {@code me.jpg} / {@code opponent.jpg}.
     */
    public static android.graphics.Bitmap getActiveDuelAvatarOverride(boolean isMe) {
        String oppId = isMe ? sActiveDuelPlayerAvatarId : sActiveDuelOpponentAvatarId;
        Context ctx = sAppContext;
        if (oppId == null || oppId.isEmpty() || ctx == null) {
            return null;
        }
        return cn.garymb.ygomobile.rpg.ui.RpgCardArtLoader.loadOpponentAvatarSync(ctx, oppId);
    }

    /**
     * Hook called from {@link cn.garymb.ygomobile.ui.dialogs.lobby.MainMenuDialog#showMainMenu()}
     * (and its deferred 1-frame retry): blocks stray MainMenuDialog popups while an RPG scene
     * transition is in flight or while RPG Deck Editor / 3D Replay / Waiting Room is actively open.
     */
    public static boolean shouldBlockMainMenu(YGOProActivity activity) {
        if (activity == null) return false;
        boolean blocked = sTransitionCoordinator.shouldBlockMainMenu(asHostSurface(activity));
        RpgDiagLog.logWithCallerStack("Menu",
                "shouldBlockMainMenu=" + blocked
                        + " scene=" + sTransitionCoordinator.getActiveScene()
                        + " gen=" + sTransitionCoordinator.getCurrentGeneration()
                        + " replayActive=" + (activity.isReplaySessionActive() || sRpgReplayEndDialogShowing),
                4);
        return blocked;
    }

    /**
     * Hook called from {@link cn.garymb.ygomobile.ui.dialogs.lobby.MainMenuDialog#showMainMenu()}:
     * suppresses stray menu popups, immediately returns to RPG Mode on legitimate Deck Editor/duel
     * exit (even after long editing sessions), and clears RPG overrides when returning to normal menu.
     */
    public static void onMainMenuShown(YGOProActivity activity) {
        RpgDiagLog.logWithCallerStack("Menu",
                "onMainMenuShown scene=" + sTransitionCoordinator.getActiveScene()
                        + " gen=" + sTransitionCoordinator.getCurrentGeneration(),
                4);
        if (activity != null && sTransitionCoordinator.onMainMenuShown(asHostSurface(activity))) {
            return;
        }
        onMainMenuShown();
    }

    public static void onMainMenuShown() {
        sActiveDuelPlayerAvatarId = null;
        sActiveDuelOpponentAvatarId = null;
        sActiveRpgDuelRuleSet = null;
        sActiveRpgDuelTimelineDate = null;
        sRpgDeckLimitList = null;
        sActiveRpgReplayReturnTab = -1;
        sRpgReplayEndDialogShowing = false;
    }

    private static String resolveOpponentIdByName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) return null;
        String trimmed = displayName.trim();
        for (AiOpponent opp : cn.garymb.ygomobile.rpg.engine.AiDeckGenerator.getDemoOpponents()) {
            if (opp != null && opp.name != null && !opp.name.isEmpty()) {
                if (trimmed.equals(opp.name) || trimmed.startsWith(opp.name)) {
                    return opp.id;
                }
            }
        }
        return null;
    }

    /**
     * Writes historical OCG rule overrides and pre-errata card Lua scripts (e.g. 1999 Crush Card Virus,
     * pre-errata Sangan & Witch of the Black Forest) to {@code <resourcePath>/rpg_rule_override.lua}.
     */
    public static boolean writeRuleOverrideScript(OcgRuleSet rule) {
        return writeRuleOverrideScript(rule, sActiveRpgDuelTimelineDate);
    }

    public static boolean writeRuleOverrideScript(OcgRuleSet rule, String timelineDate) {
        if (rule == null && (timelineDate == null || timelineDate.trim().isEmpty())) return false;
        String lua = cn.garymb.ygomobile.rpg.engine.RpgHistoricalLuaBuilder.buildScript(rule, timelineDate);
        File ruleFile = new File(AppsSettings.get().getResourcePath(), "rpg_rule_override.lua");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(ruleFile)) {
            fos.write(lua.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            android.util.Log.e("RpgIntegration", "Failed to write rpg_rule_override.lua", e);
            return false;
        }
    }

    /**
     * Hook called from ServerDuelBase (and ServerDuel undo rebuild) BEFORE loadDeckToEngine/newCard.
     * Injects historical OCG rule overrides AND pre-errata card tables (e.g. 1999 Crush Card Virus)
     * directly into ocgcore's per-duel Lua state without polluting expansions/script/ or normal duels.
     */
    public static void applyActiveRpgRulesToDuel(long pduel) {
        OcgRuleSet rule = sActiveRpgDuelRuleSet;
        String date = sActiveRpgDuelTimelineDate;
        if (pduel == 0L || (rule == null && date == null)) return;
        if (!writeRuleOverrideScript(rule, date)) return;
        int res = cn.garymb.ygomobile.engine.OcgDuelEngine.preloadScript(pduel, "./rpg_rule_override.lua");
        android.util.Log.i("RpgIntegration", "Preloaded RPG rule & pre-errata override ("
                + (rule != null ? rule.id : "null") + " @ " + date
                + ", tribute=" + (rule != null && rule.tributeSummonEnabled)
                + ", t1Draw=" + (rule != null && rule.firstTurnDrawEnabled) + ") -> res=" + res);
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
     * Returns true while the native deck editor is open for an RPG deck session.
     */
    public static boolean isRpgDeckEditorActive() {
        return sRpgDeckAllowedCaps != null;
    }

    /**
     * Hook called from DeckEditorManagerBase.notifyDeckChanged():
     * Recomputes current in-deck copy counts and refreshes the search list (preserving scroll
     * position!) only when at least one card crossed its cap visibility boundary.
     */
    public static void onDeckEditorDeckChanged(cn.garymb.ygomobile.bean.DeckInfo currentDeck,
                                               cn.garymb.ygomobile.game.deck.CardSearcherManagerBase searcher) {
        java.util.Map<Integer, Integer> caps = sRpgDeckAllowedCaps;
        if (caps == null) return;
        java.util.Map<Integer, Integer> prevCounts = sRpgDeckCurrentCounts;
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
        boolean visibilityChanged = (prevCounts == null);
        if (!visibilityChanged) {
            java.util.Set<Integer> affected = new java.util.HashSet<>(prevCounts.keySet());
            affected.addAll(counts.keySet());
            for (Integer code : affected) {
                Integer capObj = caps.get(code);
                if (capObj == null || capObj <= 0) continue;
                int cap = capObj;
                boolean wasVisible = prevCounts.getOrDefault(code, 0) < cap;
                boolean nowVisible = counts.getOrDefault(code, 0) < cap;
                if (wasVisible != nowVisible) {
                    visibilityChanged = true;
                    break;
                }
            }
        }
        sRpgDeckCurrentCounts = counts;
        if (searcher != null && visibilityChanged) {
            searcher.refreshRpgSearchPreservingScroll();
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

    private static final cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator sTransitionCoordinator =
            new cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator();

    private static cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.HostSurface asHostSurface(
            final YGOProActivity act) {
        return new cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.HostSurface() {
            @Override
            public boolean isAlive() {
                return act != null && !act.isFinishing() && !act.isDestroyed();
            }

            @Override
            public boolean isForegroundFocused() {
                View decor = act.getWindow() != null ? act.getWindow().getDecorView() : null;
                return act.hasWindowFocus() && decor != null && decor.getWindowToken() != null;
            }

            @Override
            public boolean isMainMenuShowing() {
                return act.getMainMenuDialog() != null && act.getMainMenuDialog().isShowing();
            }

            @Override
            public void hideMainMenu() {
                if (act.getMainMenuDialog() != null) {
                    act.getMainMenuDialog().hideMainMenu();
                }
            }

            @Override
            public void dismissStaleWaitingDialogSilently() {
                if (act.getPlayerWaitingDialog() != null) {
                    try {
                        act.getPlayerWaitingDialog().setOnDismissListener(null);
                        act.getPlayerWaitingDialog().dismiss();
                    } catch (Throwable ignored) {
                    }
                    act.setPlayerWaitingDialog(null);
                }
            }

            @Override
            public void dismissStaleReplayDialogSilently() {
                cn.garymb.ygomobile.ui.dialogs.replay.ReplayModeDialog showing = act.getReplayModeDialog();
                if (showing != null && showing.isShowing()) {
                    try {
                        showing.setOnDismissListener(null);
                        showing.dismiss();
                    } catch (Throwable ignored) {
                    }
                }
            }

            @Override
            public void stopActiveAiClients() {
                cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.stopActive();
            }

            @Override
            public boolean isEngineConnected() {
                GameEngine engine = act.getEngine();
                if (engine == null) return false;
                if (engine.getClient() != null && engine.getClient().isConnected()) return true;
                GameEngine.GameState st = engine.getState();
                return st != null && st != GameEngine.GameState.IDLE
                        && st != GameEngine.GameState.DISCONNECTED
                        && st != GameEngine.GameState.DUEL_END;
            }

            @Override
            public void disconnectStaleEngine() {
                GameEngine engine = act.getEngine();
                if (engine != null) {
                    engine.disconnect();
                }
            }

            @Override
            public boolean isEngineDisconnectedOrIdle() {
                GameEngine engine = act.getEngine();
                if (engine == null) return true;
                GameEngine.GameState st = engine.getState();
                return st == GameEngine.GameState.DISCONNECTED || st == GameEngine.GameState.IDLE;
            }

            @Override
            public boolean isDuelActiveOrStarted() {
                GameEngine engine = act.getEngine();
                return engine != null && (engine.getState() == GameEngine.GameState.DUELING || engine.isStarted());
            }

            @Override
            public boolean isWaitingDialogShowing() {
                return act.getPlayerWaitingDialog() != null && act.getPlayerWaitingDialog().isShowing();
            }

            @Override
            public void showWaitingDialog() {
                boolean autoStart = sActiveRpgLiveDuelSession
                        && (sLastRpgDuelMeta == null || !"RPG_SPECTATE".equals(sLastRpgDuelMeta.source));
                PlayerWaitingDialog.showPlayerWaitingForBotHost(act, autoStart);
            }

            @Override
            public boolean isReplaySessionActive() {
                return act.isReplaySessionActive() || sRpgReplayEndDialogShowing;
            }

            @Override
            public boolean isGameUiVisible() {
                View v = act.findViewById(R.id.layout_game_right);
                return v != null && v.getVisibility() == View.VISIBLE;
            }

            @Override
            public void enterReplayUi() {
                act.enterReplayUI();
            }

            @Override
            public boolean isDeckEditorVisible() {
                View v = act.findViewById(R.id.layout_deck_editor);
                return v != null && v.getVisibility() == View.VISIBLE;
            }

            @Override
            public void showDeckEditor() {
                act.showDeckEditorView();
            }

            @Override
            public void postDelayed(Runnable action, long delayMs) {
                sMainHandler.postDelayed(action, Math.max(0L, delayMs));
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }
        };
    }

    private static Runnable buildReturnToRpgAction(final YGOProActivity activity,
                                                   final int targetTab,
                                                   final File editedYdkToSyncBack) {
        return () -> {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            sRpgDeckAllowedCaps = null;
            sRpgDeckCurrentCounts = null;
            sRpgDeckLimitList = null;
            if (editedYdkToSyncBack != null && editedYdkToSyncBack.exists()) {
                parseAndPublishEditedDeck(editedYdkToSyncBack);
            }
            if (editedYdkToSyncBack == null && targetTab == RpgHomeActivity.TAB_AI_DUEL) {
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
        };
    }

    /**
     * Watches for YGOProActivity's MainMenuDialog to reappear (after duel end or native deck editor exit),
     * optionally parses the edited .ydk back into RpgSessionManager, and reopens RpgHomeActivity on targetTab.
     * Automatically cancelled if any new RPG scene launches in the meantime.
     */
    private static void scheduleReturnToRpg(final YGOProActivity activity,
                                            final int targetTab,
                                            final File editedYdkToSyncBack) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        sTransitionCoordinator.scheduleReturnToRpg(
                asHostSurface(activity),
                buildReturnToRpgAction(activity, targetTab, editedYdkToSyncBack));
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
        if (activity != null) {
            sAppContext = activity.getApplicationContext();
        }
        RpgSessionManager.registerHostBridge(new RpgDuelHostBridge() {
            @Override
            public void startRpgAiDuel(Context context,
                                       AiOpponent opponent,
                                       String playerDeckYdkPath,
                                       String aiDeckYdkPath,
                                       OcgBanlist activeBanlist,
                                       OcgRuleSet activeRuleSet,
                                       String currentTimelineDate) {
                launchLiveRpgDuel(context, Constants.PlayerName, null, playerDeckYdkPath,
                        opponent, (opponent != null) ? opponent.id : null, aiDeckYdkPath,
                        activeRuleSet, currentTimelineDate, "OCG RPG [" + currentTimelineDate + "]");
            }

            @Override
            public void startRpgCanonDuel(Context context,
                                          String playerDisplayName,
                                          String playerAvatarId,
                                          String playerDeckYdkPath,
                                          AiOpponent opponent,
                                          String opponentAvatarId,
                                          String aiDeckYdkPath,
                                          OcgBanlist activeBanlist,
                                          OcgRuleSet activeRuleSet,
                                          String currentTimelineDate) {
                String pName = (playerDisplayName != null && !playerDisplayName.trim().isEmpty())
                        ? playerDisplayName.trim() : Constants.PlayerName;
                launchLiveRpgDuel(context, pName, playerAvatarId, playerDeckYdkPath,
                        opponent, opponentAvatarId, aiDeckYdkPath,
                        activeRuleSet, currentTimelineDate, "原著对决还原 [" + currentTimelineDate + "]");
            }

            private void launchLiveRpgDuel(final Context callerContext,
                                           final String playerName,
                                           final String playerAvatarId,
                                           final String playerDeckYdkPath,
                                           final AiOpponent opponent,
                                           final String opponentAvatarId,
                                           final String aiDeckYdkPath,
                                           final OcgRuleSet activeRuleSet,
                                           final String currentTimelineDate,
                                           final String roomTitle) {
                dispatchOnHost(callerContext,
                        cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.ActiveScene.WAITING_ROOM,
                        act -> {
                    sAppContext = act.getApplicationContext();
                    sTransitionCoordinator.launchWaitingRoomScene(
                            asHostSurface(act),
                            () -> {
                                sAppContext = act.getApplicationContext();
                                sActiveRpgDuelRuleSet = activeRuleSet;
                                sActiveRpgDuelTimelineDate = currentTimelineDate;
                                sActiveDuelPlayerAvatarId = playerAvatarId;
                                sActiveDuelOpponentAvatarId = opponentAvatarId;

                                String aiStyleCode = "GOOD_STUFF";
                                if (opponent != null) {
                                    AiOpponent.EraDeckProfile prof = opponent.getActiveDeckProfile(currentTimelineDate);
                                    if (prof != null && prof.fillStyle != null && !prof.fillStyle.isEmpty()) {
                                        aiStyleCode = prof.fillStyle;
                                    }
                                }
                                cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.ReplayMeta liveMeta =
                                        new cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.ReplayMeta();
                                liveMeta.source = (roomTitle != null && roomTitle.contains("原著"))
                                        ? "RPG_CANON" : "RPG_MANUAL";
                                liveMeta.timelineDate = currentTimelineDate != null ? currentTimelineDate : "";
                                liveMeta.ruleName = (activeRuleSet != null && activeRuleSet.name != null)
                                        ? activeRuleSet.name : "";
                                liveMeta.putPlayerControl(playerName, "手动");
                                String oppDisplay = (opponent != null && opponent.name != null && !opponent.name.isEmpty())
                                        ? opponent.name : "OCG RPG AI";
                                liveMeta.putPlayerControl(oppDisplay,
                                        cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.formatAiStrategyLabel(aiStyleCode));
                                sLastRpgDuelMeta = liveMeta;
                                sActiveRpgLiveDuelSession = true;

                                syncYdkToNativeDeckDir(playerDeckYdkPath);

                                if (activeRuleSet != null) {
                                    String ruleSummary = activeRuleSet.name
                                            + (activeRuleSet.tributeSummonEnabled ? "（上级需祭品" : "（上级免祭品")
                                            + (activeRuleSet.firstTurnDrawEnabled ? " · 先攻抽卡）" : " · 先攻不抽卡）");
                                    Toast.makeText(act, "已应用 RPG 规则：" + ruleSummary, Toast.LENGTH_LONG).show();
                                }

                                GameEngine engine = act.getEngine();
                                engine.setBotMode(true);
                                engine.setPlayerName(playerName);

                                int duelMode = 0; // Single duel
                                int duelRule = activeRuleSet != null ? activeRuleSet.ygoproDuelRule : 1;
                                int startLp = activeRuleSet != null ? activeRuleSet.startLp : 8000;
                                int startHand = activeRuleSet != null ? activeRuleSet.startHand : 5;
                                engine.startLocalServerWithSettings(
                                        0, 5, duelMode, duelRule,
                                        true, false,
                                        startLp, startHand, 1, 0,
                                        roomTitle, "");
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

                                PlayerWaitingDialog.showPlayerWaitingForBotHost(act, true);

                                cn.garymb.ygomobile.rpg.model.RpgCatalog catalog = RpgSessionManager.getCachedCatalog();
                                if (catalog == null) {
                                    try {
                                        catalog = cn.garymb.ygomobile.rpg.data.RpgCatalogLoader
                                                .loadFromAssets(act.getApplicationContext());
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
                            },
                            () -> {
                                sActiveRpgDuelRuleSet = activeRuleSet;
                                sActiveRpgDuelTimelineDate = currentTimelineDate;
                                sActiveDuelPlayerAvatarId = playerAvatarId;
                                sActiveDuelOpponentAvatarId = opponentAvatarId;
                                PlayerWaitingDialog cur = act.getPlayerWaitingDialog();
                                if (cur != null && cur.isShowing()) {
                                    if (playerName != null && !playerName.isEmpty()) {
                                        cur.setPlayerName(0, playerName);
                                    }
                                    cur.setAutoReadyAndStart(true);
                                }
                            });
                });
            }

            @Override
            public void openNativeDeckEditor(Context context,
                                             String playerDeckYdkPath,
                                             java.util.Map<Integer, Integer> allowedCardCaps,
                                             OcgBanlist activeBanlist) {
                dispatchOnHost(context,
                        cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.ActiveScene.DECK_EDITOR,
                        hostAct -> {
                    final File syncedFile = syncYdkToNativeDeckDir(playerDeckYdkPath);
                    final java.util.Map<Integer, Integer> capsCopy = allowedCardCaps != null
                            ? new java.util.HashMap<>(allowedCardCaps)
                            : new java.util.HashMap<>();
                    final java.util.Map<Integer, Integer> countsCopy = countCardsInYdk(syncedFile);
                    final ocgcore.data.LimitList timelineLimit = buildRpgLimitList(activeBanlist);
                    final String banlistLabel = timelineLimit.getName();
                    sTransitionCoordinator.launchDeckEditorScene(
                            asHostSurface(hostAct),
                            () -> {
                                sRpgDeckAllowedCaps = capsCopy;
                                sRpgDeckCurrentCounts = countsCopy;
                                sRpgDeckLimitList = timelineLimit;
                                hostAct.showDeckEditorView();
                                Toast.makeText(hostAct,
                                        "RPG 组卡限制已启用（禁限表：" + banlistLabel + "）：保存并退出后自动同步",
                                        Toast.LENGTH_LONG).show();
                            },
                            () -> openRpgDeckInNativeEditor(hostAct, syncedFile.getAbsolutePath(), "rpg_player_active", "RPG"),
                            buildReturnToRpgAction(hostAct, RpgHomeActivity.TAB_DECK, syncedFile));
                });
            }

            @Override
            public void openReadonlyAiDeckViewer(Context context,
                                                 String aiDeckYdkPath,
                                                 String displayTitle,
                                                 OcgBanlist activeBanlist) {
                dispatchOnHost(context,
                        cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.ActiveScene.DECK_EDITOR,
                        hostAct -> {
                    // Place the preview file under aiDeckDir so DeckHistory.isReadonlyDeck()
                    // automatically locks the native DeckEditor into full read-only mode.
                    File aiDir = new File(AppsSettings.get().getAiDeckDir());
                    if (!aiDir.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        aiDir.mkdirs();
                    }
                    final File previewFile = new File(aiDir, "RPG_AI_preview.ydk");
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
                    final String label = (displayTitle != null && !displayTitle.isEmpty())
                            ? displayTitle : "AI 卡组";
                    final ocgcore.data.LimitList timelineLimit = buildRpgLimitList(activeBanlist);
                    sTransitionCoordinator.launchDeckEditorScene(
                            asHostSurface(hostAct),
                            () -> {
                                // Empty allowed caps double-locks card search and card addition
                                sRpgDeckAllowedCaps = Collections.emptyMap();
                                sRpgDeckCurrentCounts = Collections.emptyMap();
                                sRpgDeckLimitList = timelineLimit;
                                hostAct.showDeckEditorView();
                                Toast.makeText(hostAct,
                                        "只读查看 AI 卡组：" + label + "（不可修改，点「退出编辑」返回选对手页）",
                                        Toast.LENGTH_LONG).show();
                            },
                            () -> openRpgDeckInNativeEditor(hostAct, previewFile.getAbsolutePath(), label, "AI卡组(只读)"),
                            buildReturnToRpgAction(hostAct, RpgHomeActivity.TAB_AI_DUEL, null));
                });
            }

            private void openRpgDeckInNativeEditor(YGOProActivity act, String path, String deckName, String category) {
                try {
                    java.lang.reflect.Field f = cn.garymb.ygomobile.YGOProActivityBase.class
                            .getDeclaredField("deckEditorManager");
                    f.setAccessible(true);
                    Object mgr = f.get(act);
                    if (mgr instanceof cn.garymb.ygomobile.game.deck.DeckEditorManager) {
                        cn.garymb.ygomobile.game.deck.DeckEditorManager dem =
                                (cn.garymb.ygomobile.game.deck.DeckEditorManager) mgr;
                        dem.refreshLimitList();
                        dem.openDeckForRpg(path, deckName, category);
                        cn.garymb.ygomobile.game.deck.CardSearcherManager searcher =
                                dem.getCardSearcherManager();
                        if (searcher != null) searcher.refreshRpgSearch();
                    }
                } catch (Throwable ignored) {
                }
            }

            private final java.util.Set<Integer> failedRemotePics =
                    java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

            @Override
            public byte[] loadCardImageBytes(int cardId) {
                byte[] local = cn.garymb.ygomobile.loader.ImageLoader.findCardImageData(cardId);
                if (local != null && local.length > 0) {
                    return local;
                }
                if (cardId <= 0 || failedRemotePics.contains(cardId)) {
                    return null;
                }
                java.net.HttpURLConnection conn = null;
                try {
                    String urlStr = cn.garymb.ygomobile.utils.YGOUtil.getCardImageDetailUrl(cardId);
                    conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(5000);
                    conn.setInstanceFollowRedirects(true);
                    if (conn.getResponseCode() == 200) {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        try (java.io.InputStream in = conn.getInputStream()) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                baos.write(buf, 0, n);
                            }
                        }
                        byte[] bytes = baos.toByteArray();
                        if (bytes.length > 512) {
                            try {
                                File picDir = new File(AppsSettings.get().getCardImagePath());
                                if (!picDir.exists()) {
                                    //noinspection ResultOfMethodCallIgnored
                                    picDir.mkdirs();
                                }
                                File outFile = new File(picDir, cardId + Constants.IMAGE_URL_EX);
                                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                                    fos.write(bytes);
                                }
                            } catch (Throwable ignored) {
                            }
                            return bytes;
                        }
                    }
                    failedRemotePics.add(cardId);
                } catch (Throwable ignored) {
                    failedRemotePics.add(cardId);
                } finally {
                    if (conn != null) conn.disconnect();
                }
                return null;
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
                    String currentTimelineDate,
                    int returnTab) {
                dispatchOnHost(context,
                        cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.ActiveScene.WAITING_ROOM,
                        hostAct -> {
                    sAppContext = hostAct.getApplicationContext();
                    final String avatarA = resolveOpponentIdByName(sideAName);
                    final String avatarB = resolveOpponentIdByName(sideBName);
                    final String spectateName = "观战席·" + Constants.PlayerName;
                    sTransitionCoordinator.launchWaitingRoomScene(
                            asHostSurface(hostAct),
                            () -> {
                                sAppContext = hostAct.getApplicationContext();
                                sActiveRpgDuelRuleSet = activeRuleSet;
                                sActiveRpgDuelTimelineDate = currentTimelineDate;
                                sSpectateReturnTab = returnTab;
                                sActiveDuelPlayerAvatarId = avatarA;
                                sActiveDuelOpponentAvatarId = avatarB;
                                GameEngine engine = hostAct.getEngine();
                                engine.setBotMode(true);
                                engine.setPlayerName(spectateName);

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

                                PlayerWaitingDialog.showPlayerWaitingForBotHost(hostAct);

                                cn.garymb.ygomobile.rpg.model.RpgCatalog catalog = RpgSessionManager.getCachedCatalog();
                                if (catalog == null) {
                                    try {
                                        catalog = cn.garymb.ygomobile.rpg.data.RpgCatalogLoader
                                                .loadFromAssets(hostAct.getApplicationContext());
                                        RpgSessionManager.setCachedCatalog(catalog);
                                    } catch (Exception ignored) {
                                    }
                                }
                                final cn.garymb.ygomobile.rpg.model.RpgCatalog finalCatalog = catalog;
                                final String effA = (styleA != null && !styleA.isEmpty())
                                        ? styleA : cn.garymb.ygomobile.rpg.ai.RpgAiStrategy.inferStyleFromDeck(mainA, extraA, activeRuleSet);
                                final String effB = (styleB != null && !styleB.isEmpty())
                                        ? styleB : cn.garymb.ygomobile.rpg.ai.RpgAiStrategy.inferStyleFromDeck(mainB, extraB, activeRuleSet);

                                cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.ReplayMeta specMeta =
                                        new cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.ReplayMeta();
                                specMeta.source = "RPG_SPECTATE";
                                specMeta.timelineDate = currentTimelineDate != null ? currentTimelineDate : "";
                                specMeta.ruleName = (activeRuleSet != null && activeRuleSet.name != null)
                                        ? activeRuleSet.name : "";
                                specMeta.putPlayerControl(sideAName,
                                        cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.formatAiStrategyLabel(effA));
                                specMeta.putPlayerControl(sideBName,
                                        cn.garymb.ygomobile.rpg.util.RpgReplayLogFormatter.formatAiStrategyLabel(effB));
                                sLastRpgDuelMeta = specMeta;
                                sActiveRpgLiveDuelSession = true;

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

                                Toast.makeText(hostAct,
                                        "已切至观战席：等待两位 OCG 专属 AI 就座后点击「开始决斗」即可 3D 观战！",
                                        Toast.LENGTH_LONG).show();
                            },
                            () -> {
                                sActiveRpgDuelRuleSet = activeRuleSet;
                                sActiveRpgDuelTimelineDate = currentTimelineDate;
                                sActiveDuelPlayerAvatarId = avatarA;
                                sActiveDuelOpponentAvatarId = avatarB;
                                PlayerWaitingDialog cur = hostAct.getPlayerWaitingDialog();
                                if (cur != null && cur.isShowing()) {
                                    cur.setPlayerName(0, spectateName);
                                }
                            });
                });
            }

            @Override
            public void startRpgReplay(Context context, String replayFilePath, int returnTab) {
                startRpgReplay(context, replayFilePath, null, null, returnTab);
            }

            @Override
            public void startRpgReplay(Context context, String replayFilePath,
                                       String player0Name, String player1Name, int returnTab) {
                if (replayFilePath == null || replayFilePath.isEmpty()) {
                    RpgDiagLog.log("Replay", "startRpgReplay aborted: empty path");
                    if (context != null) {
                        sMainHandler.post(() -> Toast.makeText(context, "录像文件不存在", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }
                File f = new File(replayFilePath);
                if (!f.exists()) {
                    RpgDiagLog.log("Replay", "startRpgReplay aborted: missing file " + replayFilePath);
                    if (context != null) {
                        sMainHandler.post(() -> Toast.makeText(context, "录像文件已丢失：" + replayFilePath, Toast.LENGTH_SHORT).show());
                    }
                    return;
                }
                RpgDiagLog.log("Replay", "startRpgReplay file=" + f.getName() + " size=" + f.length()
                        + " returnTab=" + returnTab + " p0=" + player0Name + " p1=" + player1Name);
                final String p0Avatar = resolveOpponentIdByName(player0Name);
                final String p1Avatar = resolveOpponentIdByName(player1Name);
                dispatchOnHost(context,
                        cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator.ActiveScene.REPLAY,
                        hostAct -> {
                    sAppContext = hostAct.getApplicationContext();
                    sActiveRpgReplayReturnTab = returnTab;
                    sRpgReplayEndDialogShowing = false;
                    sTransitionCoordinator.launchReplayScene(
                            asHostSurface(hostAct),
                            () -> launchNative3dReplayForRpg(hostAct, replayFilePath, returnTab),
                            () -> {
                                sAppContext = hostAct.getApplicationContext();
                                sActiveDuelPlayerAvatarId = p0Avatar;
                                sActiveDuelOpponentAvatarId = p1Avatar;
                            });
                });
            }

            @Override
            public String buildReplayLogText(String replayFilePath) {
                if (replayFilePath == null || replayFilePath.isEmpty()) return null;
                return RpgIntegration.buildReplayLogText(new File(replayFilePath));
            }
        });
    }

    /**
     * Plays a recorded RPG V2 .yrp file inside YGOMobile's native 3D duel screen via {@link cn.garymb.ygomobile.game.replay.ReplayPlayer},
     * returning cleanly to {@code returnTab} in {@link RpgHomeActivity} once finished or exited early.
     */
    private static void launchNative3dReplayForRpg(final YGOProActivity activity,
                                                   final String replayPath,
                                                   final int returnTab) {
        final cn.garymb.ygomobile.game.replay.ReplayPlayer player = activity.getReplayPlayer();
        RpgDiagLog.log("Replay", "launchNative3dReplayForRpg playerReady=" + (player != null)
                + " file=" + new File(replayPath).getName());
        if (player == null) {
            Toast.makeText(activity, "3D 回放播放器尚未就绪，已返回 RPG 模式", Toast.LENGTH_SHORT).show();
            returnToRpgFromReplay(activity, returnTab);
            return;
        }
        sActiveRpgReplayReturnTab = returnTab;
        sRpgReplayEndDialogShowing = false;
        cn.garymb.ygomobile.ui.dialogs.replay.ReplayModeDialog showing = activity.getReplayModeDialog();
        if (showing != null && showing.isShowing()) {
            showing.setOnDismissListener(null);
            showing.dismiss();
        }
        player.detachListener();
        activity.getFieldCtl().resetField();
        activity.enterReplayUI();
        final java.util.concurrent.atomic.AtomicBoolean endDlgShown = new java.util.concurrent.atomic.AtomicBoolean(false);

        player.setListener(new cn.garymb.ygomobile.game.replay.ReplayPlayer.Listener() {
            @Override
            public void onReplayStateChanged(cn.garymb.ygomobile.game.replay.ReplayPlayer.State state) {
                RpgDiagLog.log("Replay", "state=" + state + " step=" + player.getCurrentStep()
                        + (player.getLastErrorMessage() != null ? (" err=" + player.getLastErrorMessage()) : ""));
                activity.runOnUiThread(() -> {
                    switch (state) {
                        case PLAYING:
                            if (activity.getMainMenuDialog() != null && activity.getMainMenuDialog().isShowing()) {
                                RpgDiagLog.log("Replay", "PLAYING hid lingering MainMenuDialog");
                                activity.getMainMenuDialog().hideMainMenu();
                            }
                            activity.getCardDetailPanel().showReplayControls();
                            activity.getCardDetailPanel().updateReplayButtonStates(false);
                            activity.updateChatUIVisibility();
                            break;
                        case PAUSED:
                            activity.getCardDetailPanel().updateReplayButtonStates(true);
                            break;
                        case FINISHED:
                        case ERROR:
                            activity.getCardDetailPanel().hideReplayControls();
                            activity.getCardDetailPanel().closeGameButtons();
                            activity.dismissDuelDialogsForExit();
                            if (endDlgShown.compareAndSet(false, true)) {
                                sRpgReplayEndDialogShowing = true;
                                String err = player.getLastErrorMessage();
                                String msg = (state == cn.garymb.ygomobile.game.replay.ReplayPlayer.State.ERROR && err != null)
                                        ? ("回放异常：" + err)
                                        : ("RPG 对局 3D 回放结束（共 " + Math.max(0, player.getCurrentStep()) + " 步）");
                                cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog dlg =
                                        new cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog(activity);
                                dlg.setTitle(state == cn.garymb.ygomobile.game.replay.ReplayPlayer.State.ERROR ? "回放异常" : "3D 回放结束")
                                        .setMessage(msg)
                                        .setType(cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog.TYPE_YES_NO)
                                        .setPositiveButtonText("返回 RPG 模式")
                                        .setPositiveButton(v -> returnToRpgFromReplay(activity, returnTab))
                                        .setNegativeButtonText("导出日志并返回")
                                        .setNegativeButton(v -> {
                                            copyReplayLogToClipboard(activity, replayPath);
                                            returnToRpgFromReplay(activity, returnTab);
                                        })
                                        .setCenterInView(activity.findViewById(R.id.layout_game_right))
                                        .setCancelable(false)
                                        .show();
                            }
                            break;
                        default:
                            break;
                    }
                });
            }

            @Override
            public void onReplayHintMessage(String hint) {
            }

            @Override
            public void onReplayFinished(int winner, int reason) {
                RpgDiagLog.log("Replay", "onReplayFinished winner=" + winner + " reason=" + reason);
                activity.runOnUiThread(() -> activity.showReplayResult(winner, reason));
            }
        });
        player.loadAndPlay(replayPath, 1);
        RpgDiagLog.log("Replay", "loadAndPlay called; isReplaySessionActive=" + activity.isReplaySessionActive());
    }
}
