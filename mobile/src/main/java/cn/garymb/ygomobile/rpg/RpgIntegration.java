package cn.garymb.ygomobile.rpg;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
     * Only settles rewards when the duel was initiated by RPG Mode.
     */
    public static void onDuelResult(final YGOProActivity activity, int winner, boolean selfWon) {
        if (activity == null || !RpgSessionManager.hasActiveRpgDuel()) {
            return;
        }
        boolean isDraw = (winner == 2);
        DuelRewardEngine.RewardSettlement settlement =
                RpgSessionManager.onDuelFinished(activity.getApplicationContext(), selfWon, isDraw);
        if (settlement != null) {
            activity.runOnUiThread(() -> Toast.makeText(
                    activity,
                    "RPG 结算：" + settlement.outcomeLabel + "，获得 +" + settlement.tokensEarned + " 代币",
                    Toast.LENGTH_LONG).show());
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
                                       String currentTimelineDate) {
                activity.runOnUiThread(() -> {
                    // Point player's active deck to the validated RPG .ydk
                    AppsSettings.get().saveSettings("lastdeckpath", playerDeckYdkPath);
                    AppsSettings.get().saveSettings("lastdeck", "rpg_player_active");

                    GameEngine engine = activity.getEngine();
                    engine.setBotMode(true);
                    engine.setPlayerName(Constants.PlayerName);

                    // Launch local server (noCheckDeck=true because :rpg-mode already enforced
                    // exact historical OCG banlist + timeline + ownership for both player and AI)
                    int duelMode = 0; // Single duel
                    int duelRule = 5; // Master Rule 2020
                    engine.startLocalServerWithSettings(
                            0, 5, duelMode, duelRule,
                            true, false,
                            8000, 5, 1, 0,
                            "OCG RPG [" + currentTimelineDate + "]", "");
                    engine.gameLflist = 0;
                    engine.gameRule = 5;
                    engine.gameMode = duelMode;
                    engine.field.dInfo.duelRule = duelRule;
                    engine.gameNoCheckDeck = 1;
                    engine.gameNoShuffleDeck = 0;
                    engine.gameStartLp = 8000;
                    engine.gameStartHand = 5;
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
        });
    }
}
