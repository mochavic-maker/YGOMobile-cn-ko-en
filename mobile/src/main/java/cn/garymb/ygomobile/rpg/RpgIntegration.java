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
        if (activity == null || !RpgSessionManager.hasActiveRpgDuel()) {
            return;
        }
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

        final long deadlineMs = System.currentTimeMillis() + 180_000L; // up to 3 min for editing / replay save
        final boolean[] sawMenuHidden = new boolean[]{!activity.getMainMenuDialog().isShowing()};
        final Runnable watcher = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (System.currentTimeMillis() > deadlineMs) return;

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
                if (editedYdkToSyncBack != null && editedYdkToSyncBack.exists()) {
                    parseAndPublishEditedDeck(editedYdkToSyncBack);
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
                    // Sync player's validated RPG .ydk into native deck/RPG/ and register via setLastDeckPath
                    // so PlayerWaitingDialog loads the player's actual RPG deck instead of the default sample deck.
                    syncYdkToNativeDeckDir(playerDeckYdkPath);

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

                    // WindBot Deck=Lucky supports arbitrary DeckFile=.ydk strictly constrained
                    // to the player's current OCG timeline & latest OCG banlist.
                    String botCommand = "Name='" + opponent.name + "' Deck=Lucky Dialog=gugugu.zh-CN";
                    engine.launchWindBot("127.0.0.1", 7911,
                            Collections.singletonList(botCommand),
                            Collections.singletonList(aiDeckYdkPath));
                });
            }

            @Override
            public void openNativeDeckEditor(Context context, String playerDeckYdkPath) {
                activity.runOnUiThread(() -> {
                    File syncedFile = syncYdkToNativeDeckDir(playerDeckYdkPath);
                    activity.showDeckEditorView();
                    Toast.makeText(activity,
                            "已载入当前 RPG 卡组，编辑并点击「保存」后退出即可自动同步回 RPG 模式",
                            Toast.LENGTH_LONG).show();
                    scheduleReturnToRpg(activity, RpgHomeActivity.TAB_DECK, syncedFile);
                });
            }

            @Override
            public byte[] loadCardImageBytes(int cardId) {
                return cn.garymb.ygomobile.loader.ImageLoader.findCardImageData(cardId);
            }
        });
    }
}
