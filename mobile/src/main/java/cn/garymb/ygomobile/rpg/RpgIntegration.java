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
                final int backTab = sSpectateReturnTab;
                cn.garymb.ygomobile.rpg.ai.RpgAiDuelClient.stopActive();
                activity.runOnUiThread(() -> scheduleReturnToRpg(activity, backTab, null));
            }
            return;
        }
        sActiveRpgDuelRuleSet = null;
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

    private static volatile java.util.Map<Integer, Integer> sRpgDeckAllowedCaps = null;
    private static volatile java.util.Map<Integer, Integer> sRpgDeckCurrentCounts = null;
    private static volatile ocgcore.data.LimitList sRpgDeckLimitList = null;
    private static volatile OcgRuleSet sActiveRpgDuelRuleSet = null;
    private static volatile int sSpectateReturnTab = RpgHomeActivity.TAB_AI_SHOWDOWN;
    private static volatile Context sAppContext = null;
    private static volatile String sActiveDuelPlayerAvatarId = null;
    private static volatile String sActiveDuelOpponentAvatarId = null;

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
        return sTransitionCoordinator.shouldBlockMainMenu(asHostSurface(activity));
    }

    /**
     * Hook called from {@link cn.garymb.ygomobile.ui.dialogs.lobby.MainMenuDialog#showMainMenu()}:
     * suppresses stray menu popups, immediately returns to RPG Mode on legitimate Deck Editor/duel
     * exit (even after long editing sessions), and clears RPG overrides when returning to normal menu.
     */
    public static void onMainMenuShown(YGOProActivity activity) {
        if (activity != null && sTransitionCoordinator.onMainMenuShown(asHostSurface(activity))) {
            return;
        }
        onMainMenuShown();
    }

    public static void onMainMenuShown() {
        sActiveDuelPlayerAvatarId = null;
        sActiveDuelOpponentAvatarId = null;
        sActiveRpgDuelRuleSet = null;
        sRpgDeckLimitList = null;
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
                PlayerWaitingDialog.showPlayerWaitingForBotHost(act);
            }

            @Override
            public boolean isReplaySessionActive() {
                return act.isReplaySessionActive();
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
                View decor = act.getWindow() != null ? act.getWindow().getDecorView() : null;
                if (decor != null) {
                    decor.postDelayed(action, delayMs);
                } else {
                    act.runOnUiThread(action);
                }
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
                launchLiveRpgDuel(activity, Constants.PlayerName, null, playerDeckYdkPath,
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
                launchLiveRpgDuel(activity, pName, playerAvatarId, playerDeckYdkPath,
                        opponent, opponentAvatarId, aiDeckYdkPath,
                        activeRuleSet, currentTimelineDate, "原著对决还原 [" + currentTimelineDate + "]");
            }

            private void launchLiveRpgDuel(final YGOProActivity act,
                                           final String playerName,
                                           final String playerAvatarId,
                                           final String playerDeckYdkPath,
                                           final AiOpponent opponent,
                                           final String opponentAvatarId,
                                           final String aiDeckYdkPath,
                                           final OcgRuleSet activeRuleSet,
                                           final String currentTimelineDate,
                                           final String roomTitle) {
                if (act == null || act.isFinishing() || act.isDestroyed()) return;
                act.runOnUiThread(() -> {
                    sAppContext = act.getApplicationContext();
                    sTransitionCoordinator.launchWaitingRoomScene(
                            asHostSurface(act),
                            () -> {
                                sAppContext = act.getApplicationContext();
                                sActiveRpgDuelRuleSet = activeRuleSet;
                                sActiveDuelPlayerAvatarId = playerAvatarId;
                                sActiveDuelOpponentAvatarId = opponentAvatarId;

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

                                PlayerWaitingDialog.showPlayerWaitingForBotHost(act);

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
                                sActiveDuelPlayerAvatarId = playerAvatarId;
                                sActiveDuelOpponentAvatarId = opponentAvatarId;
                                PlayerWaitingDialog cur = act.getPlayerWaitingDialog();
                                if (cur != null && cur.isShowing() && playerName != null && !playerName.isEmpty()) {
                                    cur.setPlayerName(0, playerName);
                                }
                            });
                });
            }

            @Override
            public void openNativeDeckEditor(Context context,
                                             String playerDeckYdkPath,
                                             java.util.Map<Integer, Integer> allowedCardCaps,
                                             OcgBanlist activeBanlist) {
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                activity.runOnUiThread(() -> {
                    final File syncedFile = syncYdkToNativeDeckDir(playerDeckYdkPath);
                    final java.util.Map<Integer, Integer> capsCopy = allowedCardCaps != null
                            ? new java.util.HashMap<>(allowedCardCaps)
                            : new java.util.HashMap<>();
                    final java.util.Map<Integer, Integer> countsCopy = countCardsInYdk(syncedFile);
                    final ocgcore.data.LimitList timelineLimit = buildRpgLimitList(activeBanlist);
                    final String banlistLabel = timelineLimit.getName();
                    sTransitionCoordinator.launchDeckEditorScene(
                            asHostSurface(activity),
                            () -> {
                                sRpgDeckAllowedCaps = capsCopy;
                                sRpgDeckCurrentCounts = countsCopy;
                                sRpgDeckLimitList = timelineLimit;
                                activity.showDeckEditorView();
                                Toast.makeText(activity,
                                        "RPG 组卡限制已启用（禁限表：" + banlistLabel + "）：保存并退出后自动同步",
                                        Toast.LENGTH_LONG).show();
                            },
                            () -> openRpgDeckInNativeEditor(activity, syncedFile.getAbsolutePath(), "rpg_player_active", "RPG"),
                            buildReturnToRpgAction(activity, RpgHomeActivity.TAB_DECK, syncedFile));
                });
            }

            @Override
            public void openReadonlyAiDeckViewer(Context context,
                                                 String aiDeckYdkPath,
                                                 String displayTitle,
                                                 OcgBanlist activeBanlist) {
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                activity.runOnUiThread(() -> {
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
                            asHostSurface(activity),
                            () -> {
                                // Empty allowed caps double-locks card search and card addition
                                sRpgDeckAllowedCaps = Collections.emptyMap();
                                sRpgDeckCurrentCounts = Collections.emptyMap();
                                sRpgDeckLimitList = timelineLimit;
                                activity.showDeckEditorView();
                                Toast.makeText(activity,
                                        "只读查看 AI 卡组：" + label + "（不可修改，点「退出编辑」返回选对手页）",
                                        Toast.LENGTH_LONG).show();
                            },
                            () -> openRpgDeckInNativeEditor(activity, previewFile.getAbsolutePath(), label, "AI卡组(只读)"),
                            buildReturnToRpgAction(activity, RpgHomeActivity.TAB_AI_DUEL, null));
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
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                activity.runOnUiThread(() -> {
                    sAppContext = activity.getApplicationContext();
                    final String avatarA = resolveOpponentIdByName(sideAName);
                    final String avatarB = resolveOpponentIdByName(sideBName);
                    final String spectateName = "观战席·" + Constants.PlayerName;
                    sTransitionCoordinator.launchWaitingRoomScene(
                            asHostSurface(activity),
                            () -> {
                                sAppContext = activity.getApplicationContext();
                                sActiveRpgDuelRuleSet = activeRuleSet;
                                sSpectateReturnTab = returnTab;
                                sActiveDuelPlayerAvatarId = avatarA;
                                sActiveDuelOpponentAvatarId = avatarB;
                                GameEngine engine = activity.getEngine();
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
                            },
                            () -> {
                                sActiveRpgDuelRuleSet = activeRuleSet;
                                sActiveDuelPlayerAvatarId = avatarA;
                                sActiveDuelOpponentAvatarId = avatarB;
                                PlayerWaitingDialog cur = activity.getPlayerWaitingDialog();
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
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                if (replayFilePath == null || replayFilePath.isEmpty()) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "录像文件不存在", Toast.LENGTH_SHORT).show());
                    return;
                }
                File f = new File(replayFilePath);
                if (!f.exists()) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "录像文件已丢失：" + replayFilePath, Toast.LENGTH_SHORT).show());
                    return;
                }
                final String p0Avatar = resolveOpponentIdByName(player0Name);
                final String p1Avatar = resolveOpponentIdByName(player1Name);
                activity.runOnUiThread(() -> {
                    sAppContext = activity.getApplicationContext();
                    sTransitionCoordinator.launchReplayScene(
                            asHostSurface(activity),
                            () -> launchNative3dReplayForRpg(activity, replayFilePath, returnTab),
                            () -> {
                                sAppContext = activity.getApplicationContext();
                                sActiveDuelPlayerAvatarId = p0Avatar;
                                sActiveDuelOpponentAvatarId = p1Avatar;
                            });
                });
            }
        });
    }

    /**
     * Plays a recorded RPG V2 .yrp file inside YGOMobile's native 3D duel screen via {@link cn.garymb.ygomobile.game.replay.ReplayPlayer},
     * returning cleanly to {@code returnTab} in {@link RpgHomeActivity} once finished.
     */
    private static void launchNative3dReplayForRpg(final YGOProActivity activity,
                                                   final String replayPath,
                                                   final int returnTab) {
        final cn.garymb.ygomobile.game.replay.ReplayPlayer player = activity.getReplayPlayer();
        if (player == null) {
            Toast.makeText(activity, "3D 回放播放器尚未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
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
                activity.runOnUiThread(() -> {
                    switch (state) {
                        case PLAYING:
                            if (activity.getMainMenuDialog() != null && activity.getMainMenuDialog().isShowing()) {
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
                                String err = player.getLastErrorMessage();
                                String msg = (state == cn.garymb.ygomobile.game.replay.ReplayPlayer.State.ERROR && err != null)
                                        ? ("回放异常：" + err)
                                        : ("RPG 对局 3D 回放结束（共 " + Math.max(0, player.getCurrentStep()) + " 步）");
                                cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog dlg =
                                        new cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog(activity);
                                dlg.setTitle(state == cn.garymb.ygomobile.game.replay.ReplayPlayer.State.ERROR ? "回放异常" : "3D 回放结束")
                                        .setMessage(msg)
                                        .setType(cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog.TYPE_MESSAGE)
                                        .setPositiveButtonText("返回 RPG 模式")
                                        .setPositiveButton(v -> {
                                            sTransitionCoordinator.invalidatePendingTransitions();
                                            sActiveDuelPlayerAvatarId = null;
                                            sActiveDuelOpponentAvatarId = null;
                                            player.detachListener();
                                            player.stop();
                                            activity.dismissDuelDialogsForExit();
                                            activity.getCardDetailPanel().hideReplayControls();
                                            activity.hideGameUI();
                                            activity.setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
                                            activity.getSoundManager().playBGM(cn.garymb.ygomobile.audio.SoundManager.BGM.MENU);
                                            Intent back = new Intent(activity, RpgHomeActivity.class);
                                            back.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            back.putExtra(RpgHomeActivity.EXTRA_INITIAL_TAB, returnTab);
                                            activity.startActivity(back);
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
                activity.runOnUiThread(() -> activity.showReplayResult(winner, reason));
            }
        });
        player.loadAndPlay(replayPath, 1);
    }
}
