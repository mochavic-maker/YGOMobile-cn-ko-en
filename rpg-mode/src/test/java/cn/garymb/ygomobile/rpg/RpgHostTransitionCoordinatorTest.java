package cn.garymb.ygomobile.rpg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import cn.garymb.ygomobile.rpg.bridge.RpgHostTransitionCoordinator;

/**
 * Deterministic unit tests guarding the three native-host transition scenes most prone to
 * bouncing back to YGOMobile's MainMenuDialog:
 * <ol>
 *   <li>Manual live duel / 3D spectate (手打)</li>
 *   <li>Native 3D replay playback after Quick Duel / Tournament (3D 回放)</li>
 *   <li>Native Deck Editor / read-only AI deck viewer (调用原版卡组编辑器)</li>
 * </ol>
 */
public class RpgHostTransitionCoordinatorTest {

    /** Virtual-clock HostSurface simulating YGOProActivity + MainMenuDialog + Android Looper. */
    private static final class FakeHostSurface implements RpgHostTransitionCoordinator.HostSurface {
        private static final class ScheduledTask {
            final long runAtMs;
            final long seq;
            final Runnable runnable;

            ScheduledTask(long runAtMs, long seq, Runnable runnable) {
                this.runAtMs = runAtMs;
                this.seq = seq;
                this.runnable = runnable;
            }
        }

        private long nowMs = 1_000L;
        private long seqCounter = 0L;
        private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>(
                Comparator.<ScheduledTask>comparingLong(t -> t.runAtMs).thenComparingLong(t -> t.seq));

        boolean alive = true;
        boolean foregroundFocused = false;
        boolean mainMenuShowing = true;
        boolean waitingDialogShowing = false;
        boolean replayDialogShowing = false;
        boolean engineConnected = false;
        boolean engineDisconnectedOrIdle = false;
        boolean duelActiveOrStarted = false;
        boolean replaySessionActive = false;
        boolean gameUiVisible = false;
        boolean deckEditorVisible = false;

        /** When > 0, simulates MIUI/HyperOS/ColorOS silently dropping showWaitingDialog() PopupWindow. */
        int dropWaitingDialogShowsRemaining = 0;

        int hideMainMenuCalls = 0;
        int stopAiCalls = 0;
        int disconnectCalls = 0;
        int showWaitingCalls = 0;
        int enterReplayUiCalls = 0;
        int showDeckEditorCalls = 0;
        final List<String> trace = new ArrayList<>();

        /** Simulates MainMenuDialog.showMainMenu() side effects (hides game UI and wipes RPG overrides). */
        final AtomicBoolean rpgRuleOverrideActive = new AtomicBoolean(false);
        final AtomicBoolean rpgAvatarOverrideActive = new AtomicBoolean(false);

        void simulateStrayMainMenuRestore() {
            mainMenuShowing = true;
            gameUiVisible = false;
            deckEditorVisible = false;
            rpgRuleOverrideActive.set(false);
            rpgAvatarOverrideActive.set(false);
            trace.add("stray_main_menu_shown");
        }

        void advanceBy(long deltaMs) {
            long target = nowMs + deltaMs;
            while (!queue.isEmpty() && queue.peek().runAtMs <= target) {
                ScheduledTask next = queue.poll();
                nowMs = next.runAtMs;
                next.runnable.run();
            }
            nowMs = target;
        }

        @Override public boolean isAlive() { return alive; }
        @Override public boolean isForegroundFocused() { return foregroundFocused; }
        @Override public boolean isMainMenuShowing() { return mainMenuShowing; }

        @Override
        public void hideMainMenu() {
            hideMainMenuCalls++;
            mainMenuShowing = false;
            trace.add("hide_main_menu");
        }

        @Override
        public void dismissStaleWaitingDialogSilently() {
            if (waitingDialogShowing) {
                waitingDialogShowing = false;
                trace.add("dismiss_stale_waiting");
            }
        }

        @Override
        public void dismissStaleReplayDialogSilently() {
            if (replayDialogShowing) {
                replayDialogShowing = false;
                trace.add("dismiss_stale_replay");
            }
        }

        @Override
        public void stopActiveAiClients() {
            stopAiCalls++;
            trace.add("stop_ai");
        }

        @Override public boolean isEngineConnected() { return engineConnected; }

        @Override
        public void disconnectStaleEngine() {
            disconnectCalls++;
            engineConnected = false;
            // Real ConnectionManager.disconnect() posts async DISCONNECTED -> returnToLanMain -> MainMenu!
            postDelayed(this::simulateStrayMainMenuRestore, 10L);
            trace.add("disconnect_stale_engine");
        }

        @Override public boolean isEngineDisconnectedOrIdle() { return engineDisconnectedOrIdle; }
        @Override public boolean isDuelActiveOrStarted() { return duelActiveOrStarted; }
        @Override public boolean isWaitingDialogShowing() { return waitingDialogShowing; }

        @Override
        public void showWaitingDialog() {
            showWaitingCalls++;
            if (dropWaitingDialogShowsRemaining > 0) {
                dropWaitingDialogShowsRemaining--;
                waitingDialogShowing = false;
                trace.add("show_waiting_dropped");
            } else {
                waitingDialogShowing = true;
                trace.add("show_waiting_ok");
            }
        }

        @Override public boolean isReplaySessionActive() { return replaySessionActive; }
        @Override public boolean isGameUiVisible() { return gameUiVisible; }

        @Override
        public void enterReplayUi() {
            enterReplayUiCalls++;
            mainMenuShowing = false;
            gameUiVisible = true;
            trace.add("enter_replay_ui");
        }

        @Override public boolean isDeckEditorVisible() { return deckEditorVisible; }

        @Override
        public void showDeckEditor() {
            showDeckEditorCalls++;
            mainMenuShowing = false;
            deckEditorVisible = true;
            trace.add("show_deck_editor");
        }

        @Override
        public void postDelayed(Runnable action, long delayMs) {
            queue.add(new ScheduledTask(nowMs + Math.max(0L, delayMs), seqCounter++, action));
        }

        @Override public long nowMs() { return nowMs; }
    }

    // ==================== SCENE 1: MANUAL DUEL (手打) ====================

    @Test
    public void manualDuelWaitsForForegroundHidesMainMenuAndDrainsStaleConnection() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.mainMenuShowing = true;
        host.foregroundFocused = false; // RpgHomeActivity is still on top when user taps 手打
        host.engineConnected = true;    // Stale connection from previous session

        AtomicBoolean duelStarted = new AtomicBoolean(false);
        coordinator.launchWaitingRoomScene(
                host,
                () -> {
                    duelStarted.set(true);
                    host.rpgRuleOverrideActive.set(true);
                    host.rpgAvatarOverrideActive.set(true);
                    host.showWaitingDialog();
                },
                () -> {
                    host.rpgRuleOverrideActive.set(true);
                    host.rpgAvatarOverrideActive.set(true);
                });

        // Immediately cleaned up & MainMenu hidden, but duel MUST NOT launch while unfocused
        assertEquals(1, host.stopAiCalls);
        assertEquals(1, host.disconnectCalls);
        assertFalse(host.mainMenuShowing);
        assertFalse("Must wait for YGOProActivity foreground focus", duelStarted.get());

        // 10ms later stale disconnect triggers stray MainMenu restore; 150ms later RpgHomeActivity finishes
        host.advanceBy(150L);
        assertFalse("Still waiting for focus before launching server/waiting room", duelStarted.get());
        assertFalse("Poller must re-hide stray MainMenu while waiting for focus", host.mainMenuShowing);

        // YGOProActivity regains foreground focus
        host.foregroundFocused = true;
        host.advanceBy(200L);

        assertTrue("Manual duel must start once foreground focused", duelStarted.get());
        assertTrue("PlayerWaitingDialog must be visible", host.waitingDialogShowing);
        assertFalse("MainMenuDialog must stay hidden", host.mainMenuShowing);
        assertTrue("RPG rule override must remain intact", host.rpgRuleOverrideActive.get());
        assertTrue("RPG avatar override must remain intact", host.rpgAvatarOverrideActive.get());
    }

    @Test
    public void manualDuelSelfHealsWhenPopupWindowDroppedAndMainMenuReappears() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.foregroundFocused = true;
        host.dropWaitingDialogShowsRemaining = 1; // Simulate MIUI/HyperOS silently dropping first PopupWindow

        coordinator.launchWaitingRoomScene(
                host,
                () -> {
                    host.rpgRuleOverrideActive.set(true);
                    host.rpgAvatarOverrideActive.set(true);
                    host.showWaitingDialog();
                    // Simulate stray MainMenuDialog restore right after dropped show
                    host.simulateStrayMainMenuRestore();
                },
                () -> {
                    host.rpgRuleOverrideActive.set(true);
                    host.rpgAvatarOverrideActive.set(true);
                });

        // Advance past focus + settle + 240ms post-launch safeguard + 80ms seat-name restore
        host.advanceBy(600L);

        assertEquals("Waiting dialog must have retried once after silent drop", 2, host.showWaitingCalls);
        assertTrue("PlayerWaitingDialog must recover and be visible", host.waitingDialogShowing);
        assertFalse("MainMenuDialog must be hidden again by safeguard", host.mainMenuShowing);
        assertTrue("Rule override wiped by stray menu must be restored", host.rpgRuleOverrideActive.get());
        assertTrue("Avatar override wiped by stray menu must be restored", host.rpgAvatarOverrideActive.get());
    }

    // ==================== SCENE 2: 3D REPLAY (快速对决后 3D 回放) ====================

    @Test
    public void replay3dWaitsForForegroundHidesMainMenuAndRecoversFromStrayMenuRestore() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.mainMenuShowing = true;
        host.foregroundFocused = false; // Quick Duel AlertDialog + RpgHomeActivity still finishing
        host.replayDialogShowing = true;

        AtomicBoolean loadAndPlayCalled = new AtomicBoolean(false);
        coordinator.launchReplayScene(
                host,
                () -> {
                    host.enterReplayUi();
                    host.replaySessionActive = true;
                    loadAndPlayCalled.set(true);
                    // Simulate race where MainMenuDialog reappears during Activity transition & hides GameUI
                    host.simulateStrayMainMenuRestore();
                },
                () -> host.rpgAvatarOverrideActive.set(true));

        assertFalse("Replay must not start before foreground focus", loadAndPlayCalled.get());
        assertFalse("Stale ReplayModeDialog must be dismissed silently", host.replayDialogShowing);

        // YGOProActivity regains focus after RpgHomeActivity finishes
        host.advanceBy(100L);
        host.foregroundFocused = true;
        host.advanceBy(500L);

        assertTrue("Replay playback must have launched", loadAndPlayCalled.get());
        assertFalse("MainMenuDialog must NOT cover 3D replay", host.mainMenuShowing);
        assertTrue("GameUI must be restored by 240ms safeguard", host.gameUiVisible);
        assertTrue("Replay avatar overrides must remain intact", host.rpgAvatarOverrideActive.get());
        assertEquals("enterReplayUi called initially + once for self-heal", 2, host.enterReplayUiCalls);
    }

    @Test
    public void replay3dCancelsStaleReturnWatcherFromEarlierSession() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.mainMenuShowing = false;
        host.foregroundFocused = true;

        AtomicInteger staleReturnCalls = new AtomicInteger(0);
        // Simulate an active return watcher from an earlier duel/deck-editor session
        coordinator.scheduleReturnToRpg(host, staleReturnCalls::incrementAndGet);

        // Before that watcher expires, user launches a 3D replay
        coordinator.launchReplayScene(
                host,
                () -> {
                    host.enterReplayUi();
                    host.replaySessionActive = true;
                },
                () -> host.rpgAvatarOverrideActive.set(true));

        host.advanceBy(2_000L);
        assertEquals("Stale return watcher must be cancelled by new replay generation", 0, staleReturnCalls.get());
        assertTrue(host.gameUiVisible);
        assertFalse(host.mainMenuShowing);
    }

    // ==================== SCENE 3: NATIVE DECK EDITOR (调用原版卡组编辑器) ====================

    @Test
    public void nativeDeckEditorWaitsForForegroundSelfHealsAndReturnsToRpgOnExit() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.mainMenuShowing = true;
        host.foregroundFocused = false;

        AtomicInteger deckLoadCalls = new AtomicInteger(0);
        AtomicInteger returnToRpgCalls = new AtomicInteger(0);

        coordinator.launchDeckEditorScene(
                host,
                () -> {
                    host.showDeckEditor();
                    // Simulate stray MainMenuDialog restore racing with DeckEditor open
                    host.simulateStrayMainMenuRestore();
                },
                deckLoadCalls::incrementAndGet,
                returnToRpgCalls::incrementAndGet);

        // Not opened yet while RpgHomeActivity still on top
        assertFalse(host.deckEditorVisible);
        assertEquals(0, deckLoadCalls.get());

        // YGOProActivity returns to foreground
        host.advanceBy(100L);
        host.foregroundFocused = true;
        host.advanceBy(600L);

        assertTrue("DeckEditor must recover from stray menu and stay visible", host.deckEditorVisible);
        assertFalse("MainMenuDialog must stay hidden while editing deck", host.mainMenuShowing);
        assertTrue("Deck file must be loaded into native editor", deckLoadCalls.get() >= 1);
        assertEquals("Must NOT return to RPG while still editing deck", 0, returnToRpgCalls.get());

        // User taps '退出编辑' -> native DeckEditor closes and MainMenuDialog legitimately reappears
        host.deckEditorVisible = false;
        host.mainMenuShowing = true;
        host.advanceBy(600L);

        assertEquals("Exiting DeckEditor must return to RPG Mode exactly once", 1, returnToRpgCalls.get());
        host.advanceBy(1_000L);
        assertEquals("Return callback must not fire twice", 1, returnToRpgCalls.get());
    }

    // ==================== FULL CROSS-SCENE CHAIN REGRESSION ====================

    @Test
    public void chainedDeckEditorThenManualDuelThenQuickDuel3dReplayNeverBouncesToMainMenu() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        AtomicInteger returnCalls = new AtomicInteger(0);

        // 1) Deck Editor -> edit -> exit back to RPG
        host.foregroundFocused = true;
        coordinator.launchDeckEditorScene(host, host::showDeckEditor, () -> {}, returnCalls::incrementAndGet);
        host.advanceBy(700L);
        assertTrue(host.deckEditorVisible);
        assertFalse(host.mainMenuShowing);

        host.deckEditorVisible = false;
        host.mainMenuShowing = true;
        host.advanceBy(600L);
        assertEquals(1, returnCalls.get());

        // 2) Manual Duel -> finishes -> returns to RPG
        host.foregroundFocused = false;
        coordinator.launchWaitingRoomScene(
                host,
                () -> {
                    host.rpgRuleOverrideActive.set(true);
                    host.showWaitingDialog();
                },
                () -> host.rpgRuleOverrideActive.set(true));
        host.advanceBy(80L);
        host.foregroundFocused = true;
        host.advanceBy(400L);
        assertTrue(host.waitingDialogShowing);
        assertFalse(host.mainMenuShowing);
        assertTrue(host.rpgRuleOverrideActive.get());

        // Duel ends -> MainMenuDialog shown -> return watcher triggers -> back in RPG
        host.waitingDialogShowing = false;
        host.duelActiveOrStarted = false;
        host.mainMenuShowing = false;
        coordinator.scheduleReturnToRpg(host, returnCalls::incrementAndGet);
        host.advanceBy(600L);
        host.mainMenuShowing = true;
        host.advanceBy(300L);
        assertEquals(2, returnCalls.get());

        // 3) Quick Duel -> 3D Replay (with MainMenu still open underneath RpgHomeActivity!)
        host.foregroundFocused = false;
        host.mainMenuShowing = true;
        coordinator.launchReplayScene(
                host,
                () -> {
                    host.enterReplayUi();
                    host.replaySessionActive = true;
                },
                () -> host.rpgAvatarOverrideActive.set(true));
        host.advanceBy(120L);
        host.foregroundFocused = true;
        host.advanceBy(400L);

        assertTrue("3D Replay GameUI must be visible", host.gameUiVisible);
        assertFalse("MainMenuDialog must stay hidden during 3D Replay", host.mainMenuShowing);
        assertTrue("Replay avatars must stay active", host.rpgAvatarOverrideActive.get());
        assertEquals("No spurious return-to-RPG trigger during 3D replay", 2, returnCalls.get());
    }

    // ==================== INTERMITTENT DECK EDITOR MAIN-MENU BOUNCE GUARDS ====================

    @Test
    public void deckEditorBlocksStrayMainMenuWhileTransitioningAndRecoversAtSecondCheckpoint() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.mainMenuShowing = true;
        host.foregroundFocused = false;

        AtomicInteger deckLoadCalls = new AtomicInteger(0);
        AtomicInteger returnCalls = new AtomicInteger(0);

        coordinator.launchDeckEditorScene(
                host,
                host::showDeckEditor,
                deckLoadCalls::incrementAndGet,
                returnCalls::incrementAndGet);

        // During foreground transition, any stray MainMenuDialog.showMainMenu() MUST be blocked
        assertTrue("MainMenu must be blocked while DeckEditor transition is in flight",
                coordinator.shouldBlockMainMenu(host));

        host.advanceBy(80L);
        host.foregroundFocused = true;
        host.advanceBy(350L); // Open at +170ms, 240ms check at +410ms (now at +430ms), before 480ms check at +650ms
        assertTrue(host.deckEditorVisible);
        assertFalse(host.mainMenuShowing);

        // Even while actively editing, stray MainMenuDialog.showMainMenu() MUST be blocked
        assertTrue("MainMenu must be blocked while DeckEditor is visibly open",
                coordinator.shouldBlockMainMenu(host));

        // Simulate late stray disconnect/race between 240ms first check and 480ms second check
        host.simulateStrayMainMenuRestore();
        assertFalse(host.deckEditorVisible);
        assertTrue(host.mainMenuShowing);

        // Advance past 480ms second checkpoint -> must recover DeckEditor and hide MainMenu!
        host.advanceBy(300L);
        assertTrue("480ms second checkpoint must restore DeckEditor after late stray teardown",
                host.deckEditorVisible);
        assertFalse("480ms second checkpoint must hide stray MainMenu", host.mainMenuShowing);
        assertEquals("Stray teardown must not prematurely exit to RPG", 0, returnCalls.get());
    }

    @Test
    public void deckEditorExitAfter30MinutesReturnsImmediatelyViaOnMainMenuShown() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.foregroundFocused = true;

        AtomicInteger returnCalls = new AtomicInteger(0);
        coordinator.launchDeckEditorScene(host, host::showDeckEditor, () -> {}, returnCalls::incrementAndGet);
        host.advanceBy(1_000L);
        assertTrue(host.deckEditorVisible);

        // Player spends 30 minutes (> old 10-minute timeout) carefully editing their deck
        host.advanceBy(1_800_000L);
        assertTrue("DeckEditor still open after 30 minutes", host.deckEditorVisible);
        assertTrue("MainMenu still blocked while DeckEditor is open", coordinator.shouldBlockMainMenu(host));
        assertEquals(0, returnCalls.get());

        // Player taps '退出编辑': DeckEditorViewHost.onExitEditor calls hide() -> restoreMainMenu()
        host.deckEditorVisible = false;
        assertFalse("MainMenu must NOT be blocked once DeckEditor legitimately hides",
                coordinator.shouldBlockMainMenu(host));
        host.mainMenuShowing = true;
        boolean handled = coordinator.onMainMenuShown(host);

        assertTrue("onMainMenuShown must synchronously handle return to RPG Mode", handled);
        assertEquals("Must immediately return to RPG Mode even after 30 min editing", 1, returnCalls.get());
        host.advanceBy(1_000L);
        assertEquals("Must not fire return callback twice", 1, returnCalls.get());
    }

    @Test
    public void deckEditorEarlyLegitimateExitIsNotReopenedBySecondCheckpoint() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.foregroundFocused = true;

        AtomicInteger returnCalls = new AtomicInteger(0);
        coordinator.launchDeckEditorScene(host, host::showDeckEditor, () -> {}, returnCalls::incrementAndGet);
        host.advanceBy(400L); // Open at +120ms, 240ms check at +360ms, before 480ms check at +600ms
        assertTrue(host.deckEditorVisible);

        // Player immediately exits DeckEditor at 400ms
        host.deckEditorVisible = false;
        host.mainMenuShowing = true;
        assertTrue(coordinator.onMainMenuShown(host));
        assertEquals(1, returnCalls.get());

        // Advance past 480ms second checkpoint -> must NOT reopen DeckEditor!
        host.advanceBy(500L);
        assertFalse("480ms checkpoint must be cancelled after legitimate exit", host.deckEditorVisible);
        assertEquals(1, returnCalls.get());
    }

    @Test
    public void replay3dKeepsMainMenuBlockedThroughoutStartBodyBeforeReplayFlagFlipsTrue() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.foregroundFocused = true;
        host.mainMenuShowing = true;

        AtomicBoolean blockedMidLaunch = new AtomicBoolean(false);
        coordinator.launchReplayScene(
                host,
                () -> {
                    // Simulate enterReplayUI() executing BEFORE player.loadAndPlay() sets State.LOADING
                    host.enterReplayUi();
                    assertFalse(host.replaySessionActive);
                    blockedMidLaunch.set(coordinator.shouldBlockMainMenu(host));
                    host.replaySessionActive = true;
                },
                () -> host.rpgAvatarOverrideActive.set(true));

        host.advanceBy(200L);
        assertTrue("MainMenu must remain synchronously blocked during startReplayBody before isReplaySessionActive flips true",
                blockedMidLaunch.get());
        assertTrue("MainMenu must remain blocked while replay is active", coordinator.shouldBlockMainMenu(host));
        assertTrue(host.gameUiVisible);
        assertFalse(host.mainMenuShowing);
    }

    @Test
    public void replay3dPendingHostRecreateBlocksFreshOnCreateMainMenuAndHandsOffCleanly() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        // 1) Old YGOProActivity was destroyed in background after several Quick Duels
        coordinator.markPendingHostRecreate(RpgHostTransitionCoordinator.ActiveScene.REPLAY);

        // 2) Fresh YGOProActivity starts onCreate() -> getMainMenuDialog().showMainMenu()
        FakeHostSurface recreatedHost = new FakeHostSurface();
        recreatedHost.foregroundFocused = false;
        recreatedHost.mainMenuShowing = false;
        assertTrue("Fresh host onCreate() MainMenuDialog.showMainMenu() must be blocked while replay handoff is pending",
                coordinator.shouldBlockMainMenu(recreatedHost));

        // 3) attachMainMenuButton consumes pending replay task -> launches replay on recreated host
        AtomicBoolean replayLaunched = new AtomicBoolean(false);
        coordinator.launchReplayScene(
                recreatedHost,
                () -> {
                    recreatedHost.enterReplayUi();
                    recreatedHost.replaySessionActive = true;
                    replayLaunched.set(true);
                },
                () -> recreatedHost.rpgAvatarOverrideActive.set(true));

        // 4) Recreated host gains foreground focus
        recreatedHost.advanceBy(100L);
        recreatedHost.foregroundFocused = true;
        recreatedHost.advanceBy(200L);

        assertTrue("3D replay must launch seamlessly on recreated YGOProActivity", replayLaunched.get());
        assertTrue(recreatedHost.gameUiVisible);
        assertFalse("MainMenuDialog must never appear on recreated host", recreatedHost.mainMenuShowing);
        assertTrue(recreatedHost.rpgAvatarOverrideActive.get());
    }

    @Test
    public void replay3dRecoversFromLateStrayMenuAt700msViaThirdCheckpoint() {
        RpgHostTransitionCoordinator coordinator = new RpgHostTransitionCoordinator();
        FakeHostSurface host = new FakeHostSurface();
        host.foregroundFocused = true;

        coordinator.launchReplayScene(
                host,
                () -> {
                    host.enterReplayUi();
                    host.replaySessionActive = true;
                },
                () -> host.rpgAvatarOverrideActive.set(true));

        // Advance past 240ms and 480ms checkpoints (launch at +120ms, 480ms check at +600ms)
        host.advanceBy(700L);
        assertTrue(host.gameUiVisible);

        // Late stray menu restore occurs at +700ms (after 480ms check, before 900ms third check at +1020ms)
        host.simulateStrayMainMenuRestore();
        assertFalse(host.gameUiVisible);
        assertTrue(host.mainMenuShowing);

        host.advanceBy(400L);
        assertTrue("900ms third checkpoint must restore 3D replay UI after late stray menu", host.gameUiVisible);
        assertFalse("900ms third checkpoint must re-hide stray MainMenu", host.mainMenuShowing);
        assertTrue("Avatar override must be restored", host.rpgAvatarOverrideActive.get());
    }
}
