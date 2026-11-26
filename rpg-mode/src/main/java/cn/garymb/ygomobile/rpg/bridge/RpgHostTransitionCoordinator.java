package cn.garymb.ygomobile.rpg.bridge;

import java.util.concurrent.atomic.AtomicLong;

import cn.garymb.ygomobile.rpg.util.RpgDiagLog;

/**
 * Coordinates foreground Activity transitions between {@code RpgHomeActivity} and {@code YGOProActivity}
 * for the three native-host scenes prone to bouncing back to {@code MainMenuDialog}:
 * <ol>
 *   <li>Live manual duel / 3D spectate ({@link #launchWaitingRoomScene})</li>
 *   <li>Native 3D replay playback ({@link #launchReplayScene})</li>
 *   <li>Native Deck Editor / read-only AI deck viewer ({@link #launchDeckEditorScene})</li>
 * </ol>
 *
 * <p>Provides synchronous {@link #shouldBlockMainMenu(HostSurface)} and
 * {@link #onMainMenuShown(HostSurface)} hooks so stray {@code MainMenuDialog.showMainMenu()} calls
 * (e.g., deferred PopupWindow retries or stale dismiss listeners) can never appear on top of an
 * active Deck Editor / 3D Replay / Waiting Room, while legitimate editor exit returns immediately
 * to RPG Mode even after long editing sessions (&gt; 10 minutes).</p>
 */
public final class RpgHostTransitionCoordinator {

    public static final long FOCUS_POLL_INTERVAL_MS = 50L;
    public static final long FOCUS_WAIT_TIMEOUT_MS = 1200L;
    public static final long SETTLE_FRAME_DELAY_MS = 60L;
    public static final long DECK_LOAD_DELAY_MS = 150L;
    public static final long POST_LAUNCH_VERIFY_DELAY_MS = 240L;
    public static final long POST_LAUNCH_SECOND_VERIFY_DELAY_MS = 480L;
    public static final long POST_LAUNCH_THIRD_VERIFY_DELAY_MS = 900L;
    public static final long RETURN_WATCHER_START_DELAY_MS = 550L;
    public static final long RETURN_WATCHER_POLL_MS = 250L;

    public enum ActiveScene {
        NONE,
        WAITING_ROOM,
        REPLAY,
        DECK_EDITOR
    }

    /** Abstraction over {@code YGOProActivity} and its dialogs/engine for production & unit tests. */
    public interface HostSurface {
        boolean isAlive();
        boolean isForegroundFocused();
        boolean isMainMenuShowing();
        void hideMainMenu();
        void dismissStaleWaitingDialogSilently();
        void dismissStaleReplayDialogSilently();
        void stopActiveAiClients();
        boolean isEngineConnected();
        void disconnectStaleEngine();
        boolean isEngineDisconnectedOrIdle();
        boolean isDuelActiveOrStarted();
        boolean isWaitingDialogShowing();
        void showWaitingDialog();
        boolean isReplaySessionActive();
        boolean isGameUiVisible();
        void enterReplayUi();
        boolean isDeckEditorVisible();
        void showDeckEditor();
        void postDelayed(Runnable action, long delayMs);
        long nowMs();
    }

    private final AtomicLong transitionGen = new AtomicLong(0L);
    private volatile ActiveScene activeScene = ActiveScene.NONE;
    private volatile boolean transitionInProgress = false;
    private volatile long activeReturnGen = -1L;
    private volatile Runnable activeReturnCallback = null;

    /** Returns the current transition generation token (incremented on every new scene launch). */
    public long getCurrentGeneration() {
        return transitionGen.get();
    }

    public ActiveScene getActiveScene() {
        return activeScene;
    }

    /**
     * Marks an RPG scene launch as pending host recreation (e.g., when Android destroyed
     * {@code YGOProActivity} in the background while {@code RpgHomeActivity} was foreground).
     * Ensures {@link #shouldBlockMainMenu(HostSurface)} blocks {@code MainMenuDialog.showMainMenu()}
     * inside the recreated host's {@code onCreate()} until the pending scene hands off.
     */
    public long markPendingHostRecreate(ActiveScene scene) {
        long gen = beginSceneTransition(scene != null ? scene : ActiveScene.NONE);
        RpgDiagLog.log("Coord", "markPendingHostRecreate scene=" + activeScene + " gen=" + gen);
        return gen;
    }

    /** Cancels all pending foreground waits, safeguards, and return watchers. */
    public long invalidatePendingTransitions() {
        transitionInProgress = false;
        activeScene = ActiveScene.NONE;
        activeReturnGen = -1L;
        activeReturnCallback = null;
        long gen = transitionGen.incrementAndGet();
        RpgDiagLog.log("Coord", "invalidatePendingTransitions -> gen=" + gen);
        return gen;
    }

    /**
     * Synchronous guard invoked by {@code MainMenuDialog.showMainMenu()} (and its deferred 1-frame
     * retry lambda) to block stray MainMenuDialog popups while an RPG scene transition is in flight
     * or while the user is actively inside Deck Editor, 3D Replay, or Waiting Room.
     */
    public boolean shouldBlockMainMenu(final HostSurface host) {
        if (host == null || !host.isAlive()) return false;
        if (transitionInProgress) {
            return true;
        }
        ActiveScene scene = activeScene;
        if (scene == ActiveScene.DECK_EDITOR && host.isDeckEditorVisible()) {
            return true;
        }
        if (scene == ActiveScene.REPLAY && host.isReplaySessionActive()) {
            return true;
        }
        if (scene == ActiveScene.WAITING_ROOM && host.isWaitingDialogShowing()) {
            return true;
        }
        return false;
    }

    /**
     * Synchronous hook invoked when {@code MainMenuDialog.showMainMenu()} actually executes.
     * <ul>
     *   <li>If an RPG scene is still visible (stray menu popup), immediately hides MainMenuDialog
     *       and returns {@code true} (consumed/suppressed).</li>
     *   <li>If the user legitimately exited an RPG Deck Editor or finished an RPG duel
     *       ({@code activeReturnCallback != null} and scene is no longer visible), immediately
     *       consumes the return callback (even after &gt;10 min editing!) and returns {@code true}.</li>
     *   <li>Otherwise resets {@code activeScene} to {@code NONE} and returns {@code false}
     *       (caller should clear RPG duel rule/avatar overrides).</li>
     * </ul>
     */
    public boolean onMainMenuShown(final HostSurface host) {
        if (host == null || !host.isAlive()) return false;
        if (shouldBlockMainMenu(host)) {
            RpgDiagLog.log("Coord", "onMainMenuShown SUPPRESSED scene=" + activeScene
                    + " inProgress=" + transitionInProgress + " gen=" + transitionGen.get());
            host.hideMainMenu();
            return true;
        }
        final long rGen = activeReturnGen;
        final Runnable rCb = activeReturnCallback;
        if (rCb != null && rGen == transitionGen.get()) {
            boolean stillInDeckEditor = activeScene == ActiveScene.DECK_EDITOR && host.isDeckEditorVisible();
            boolean stillInReplay = activeScene == ActiveScene.REPLAY && host.isReplaySessionActive();
            if (!stillInDeckEditor && !stillInReplay) {
                if (transitionGen.compareAndSet(rGen, rGen + 1)) {
                    RpgDiagLog.log("Coord", "onMainMenuShown -> trigger returnToRpg gen=" + rGen
                            + " prevScene=" + activeScene);
                    activeScene = ActiveScene.NONE;
                    activeReturnGen = -1L;
                    activeReturnCallback = null;
                    rCb.run();
                    return true;
                }
            }
        }
        RpgDiagLog.log("Coord", "onMainMenuShown -> normal menu prevScene=" + activeScene
                + " gen=" + transitionGen.get() + " hasReturnCb=" + (rCb != null));
        activeScene = ActiveScene.NONE;
        return false;
    }

    /**
     * Scene 1: Live manual duel (AI duel / Tournament manual / Canon duel) or 3D spectate.
     */
    public long launchWaitingRoomScene(final HostSurface host,
                                       final Runnable startSceneBody,
                                       final Runnable restoreOverridesAndSeatName) {
        if (host == null || !host.isAlive()) return -1L;
        final long gen = beginSceneTransition(ActiveScene.WAITING_ROOM);
        RpgDiagLog.log("Coord", "launchWaitingRoomScene gen=" + gen
                + " focused=" + host.isForegroundFocused() + " menu=" + host.isMainMenuShowing());
        prepareCleanHost(host);
        waitForForegroundThenRun(host, gen, () -> {
            if (!isCurrentAndAlive(host, gen)) return;
            RpgDiagLog.log("Coord", "launchWaitingRoomScene body start gen=" + gen);
            try {
                host.hideMainMenu();
                if (startSceneBody != null) {
                    startSceneBody.run();
                }
            } finally {
                if (transitionGen.get() == gen) {
                    transitionInProgress = false;
                }
            }
            Runnable verifyWaiting = () -> {
                if (!isCurrentAndAlive(host, gen)) return;
                if (host.isEngineDisconnectedOrIdle() || host.isDuelActiveOrStarted()) {
                    return;
                }
                boolean menu = host.isMainMenuShowing();
                boolean wait = host.isWaitingDialogShowing();
                if (menu || !wait) {
                    RpgDiagLog.log("Coord", "verifyWaiting HEAL gen=" + gen + " menu=" + menu + " wait=" + wait);
                }
                if (menu) {
                    host.hideMainMenu();
                }
                if (restoreOverridesAndSeatName != null) {
                    restoreOverridesAndSeatName.run();
                }
                if (!wait) {
                    host.showWaitingDialog();
                    if (restoreOverridesAndSeatName != null) {
                        host.postDelayed(() -> {
                            if (isCurrentAndAlive(host, gen) && host.isWaitingDialogShowing()) {
                                restoreOverridesAndSeatName.run();
                            }
                        }, 80L);
                    }
                }
            };
            host.postDelayed(verifyWaiting, POST_LAUNCH_VERIFY_DELAY_MS);
            host.postDelayed(verifyWaiting, POST_LAUNCH_SECOND_VERIFY_DELAY_MS);
            host.postDelayed(verifyWaiting, POST_LAUNCH_THIRD_VERIFY_DELAY_MS);
        });
        return gen;
    }

    /**
     * Scene 2: Native 3D replay playback (Quick Duel / Tournament / AI Showdown replay).
     */
    public long launchReplayScene(final HostSurface host,
                                  final Runnable startReplayBody,
                                  final Runnable restoreAvatarOverrides) {
        if (host == null || !host.isAlive()) return -1L;
        final long gen = beginSceneTransition(ActiveScene.REPLAY);
        RpgDiagLog.log("Coord", "launchReplayScene gen=" + gen
                + " focused=" + host.isForegroundFocused() + " menu=" + host.isMainMenuShowing());
        prepareCleanHost(host);
        waitForForegroundThenRun(host, gen, () -> {
            if (!isCurrentAndAlive(host, gen)) return;
            RpgDiagLog.log("Coord", "launchReplayScene body start gen=" + gen);
            try {
                host.hideMainMenu();
                if (restoreAvatarOverrides != null) {
                    restoreAvatarOverrides.run();
                }
                if (startReplayBody != null) {
                    startReplayBody.run();
                }
            } finally {
                if (transitionGen.get() == gen) {
                    transitionInProgress = false;
                }
            }
            RpgDiagLog.log("Coord", "launchReplayScene body done gen=" + gen
                    + " replayActive=" + host.isReplaySessionActive()
                    + " gameUi=" + host.isGameUiVisible()
                    + " menu=" + host.isMainMenuShowing());
            Runnable verifyReplay = () -> {
                if (!isCurrentAndAlive(host, gen)) return;
                boolean replayActive = host.isReplaySessionActive();
                boolean menu = host.isMainMenuShowing();
                boolean gameUi = host.isGameUiVisible();
                if (!replayActive) {
                    RpgDiagLog.log("Coord", "verifyReplay skip gen=" + gen
                            + " replayActive=false menu=" + menu + " gameUi=" + gameUi);
                    return;
                }
                if (menu || !gameUi) {
                    RpgDiagLog.log("Coord", "verifyReplay HEAL gen=" + gen
                            + " menu=" + menu + " gameUi=" + gameUi);
                }
                if (menu) {
                    host.hideMainMenu();
                }
                if (restoreAvatarOverrides != null) {
                    restoreAvatarOverrides.run();
                }
                if (!gameUi) {
                    host.enterReplayUi();
                }
            };
            host.postDelayed(verifyReplay, POST_LAUNCH_VERIFY_DELAY_MS);
            host.postDelayed(verifyReplay, POST_LAUNCH_SECOND_VERIFY_DELAY_MS);
            host.postDelayed(verifyReplay, POST_LAUNCH_THIRD_VERIFY_DELAY_MS);
        });
        return gen;
    }

    /**
     * Scene 3: Native Deck Editor (player RPG deck editing or read-only AI deck preview).
     */
    public long launchDeckEditorScene(final HostSurface host,
                                      final Runnable openEditorBody,
                                      final Runnable loadDeckIntoEditorBody,
                                      final Runnable onReturnToRpgMenu) {
        if (host == null || !host.isAlive()) return -1L;
        final long gen = beginSceneTransition(ActiveScene.DECK_EDITOR);
        RpgDiagLog.log("Coord", "launchDeckEditorScene gen=" + gen
                + " focused=" + host.isForegroundFocused() + " menu=" + host.isMainMenuShowing());
        prepareCleanHost(host);
        waitForForegroundThenRun(host, gen, () -> {
            if (!isCurrentAndAlive(host, gen)) return;
            RpgDiagLog.log("Coord", "launchDeckEditorScene body start gen=" + gen);
            try {
                host.hideMainMenu();
                if (openEditorBody != null) {
                    openEditorBody.run();
                }
            } finally {
                if (transitionGen.get() == gen) {
                    transitionInProgress = false;
                }
            }
            if (loadDeckIntoEditorBody != null) {
                host.postDelayed(() -> {
                    if (!isCurrentAndAlive(host, gen)) return;
                    loadDeckIntoEditorBody.run();
                }, DECK_LOAD_DELAY_MS);
            }
            Runnable verifyDeckEditor = () -> {
                if (!isCurrentAndAlive(host, gen)) return;
                boolean menu = host.isMainMenuShowing();
                boolean deckVis = host.isDeckEditorVisible();
                if (menu || !deckVis) {
                    RpgDiagLog.log("Coord", "verifyDeckEditor HEAL gen=" + gen
                            + " menu=" + menu + " deckVis=" + deckVis);
                }
                if (menu) {
                    host.hideMainMenu();
                }
                if (!deckVis) {
                    host.showDeckEditor();
                    if (loadDeckIntoEditorBody != null) {
                        host.postDelayed(() -> {
                            if (isCurrentAndAlive(host, gen)) {
                                loadDeckIntoEditorBody.run();
                            }
                        }, DECK_LOAD_DELAY_MS);
                    }
                }
            };
            host.postDelayed(verifyDeckEditor, POST_LAUNCH_VERIFY_DELAY_MS);
            host.postDelayed(verifyDeckEditor, POST_LAUNCH_SECOND_VERIFY_DELAY_MS);
            host.postDelayed(verifyDeckEditor, POST_LAUNCH_THIRD_VERIFY_DELAY_MS);
            if (onReturnToRpgMenu != null) {
                armReturnToRpg(host, gen, onReturnToRpgMenu, true);
            }
        });
        return gen;
    }

    /**
     * Arms a generation-guarded return handler (triggered both synchronously via
     * {@link #onMainMenuShown(HostSurface)} and via fallback polling) once MainMenuDialog
     * legitimately reappears after duel end or Deck Editor exit.
     */
    public long scheduleReturnToRpg(final HostSurface host, final Runnable onReturnToRpgMenu) {
        if (host == null || !host.isAlive() || onReturnToRpgMenu == null) return -1L;
        transitionInProgress = false;
        activeScene = ActiveScene.NONE;
        final long gen = transitionGen.incrementAndGet();
        RpgDiagLog.log("Coord", "scheduleReturnToRpg gen=" + gen + " menu=" + host.isMainMenuShowing());
        armReturnToRpg(host, gen, onReturnToRpgMenu, !host.isMainMenuShowing());
        return gen;
    }

    private long beginSceneTransition(ActiveScene scene) {
        activeReturnGen = -1L;
        activeReturnCallback = null;
        activeScene = scene;
        transitionInProgress = true;
        return transitionGen.incrementAndGet();
    }

    private void armReturnToRpg(final HostSurface host,
                                final long gen,
                                final Runnable onReturnToRpgMenu,
                                final boolean initiallyHidden) {
        activeReturnGen = gen;
        activeReturnCallback = onReturnToRpgMenu;
        final boolean[] sawMenuHidden = new boolean[]{initiallyHidden};
        final Runnable watcher = new Runnable() {
            @Override
            public void run() {
                if (!isCurrentAndAlive(host, gen)) return;
                // If Deck Editor is still visibly open, a showing MainMenuDialog is ALWAYS a stray
                // popup rather than an exit — suppress it and keep editing!
                if (activeScene == ActiveScene.DECK_EDITOR && host.isDeckEditorVisible()) {
                    if (host.isMainMenuShowing()) {
                        RpgDiagLog.log("Coord", "returnWatcher suppressed stray menu during DECK_EDITOR gen=" + gen);
                        host.hideMainMenu();
                    }
                    sawMenuHidden[0] = true;
                    host.postDelayed(this, RETURN_WATCHER_POLL_MS);
                    return;
                }
                boolean menuShowing = host.isMainMenuShowing();
                if (!menuShowing) {
                    sawMenuHidden[0] = true;
                    host.postDelayed(this, RETURN_WATCHER_POLL_MS);
                    return;
                }
                if (!sawMenuHidden[0]) {
                    host.postDelayed(this, RETURN_WATCHER_POLL_MS);
                    return;
                }
                if (transitionGen.compareAndSet(gen, gen + 1)) {
                    RpgDiagLog.log("Coord", "returnWatcher firing returnToRpg gen=" + gen);
                    activeScene = ActiveScene.NONE;
                    activeReturnGen = -1L;
                    activeReturnCallback = null;
                    onReturnToRpgMenu.run();
                }
            }
        };
        host.postDelayed(watcher, RETURN_WATCHER_START_DELAY_MS);
    }

    private void prepareCleanHost(final HostSurface host) {
        host.stopActiveAiClients();
        host.dismissStaleWaitingDialogSilently();
        host.dismissStaleReplayDialogSilently();
        if (host.isEngineConnected()) {
            host.disconnectStaleEngine();
        }
        host.hideMainMenu();
    }

    private void waitForForegroundThenRun(final HostSurface host, final long gen, final Runnable task) {
        final long startMs = host.nowMs();
        final long deadlineMs = startMs + FOCUS_WAIT_TIMEOUT_MS;
        final Runnable poll = new Runnable() {
            @Override
            public void run() {
                if (!isCurrentAndAlive(host, gen)) {
                    RpgDiagLog.log("Coord", "waitForForeground aborted gen=" + gen
                            + " curGen=" + transitionGen.get()
                            + " alive=" + (host != null && host.isAlive()));
                    return;
                }
                if (host.isMainMenuShowing()) {
                    host.hideMainMenu();
                }
                boolean focused = host.isForegroundFocused();
                if (!focused && host.nowMs() < deadlineMs) {
                    host.postDelayed(this, FOCUS_POLL_INTERVAL_MS);
                    return;
                }
                long elapsed = host.nowMs() - startMs;
                RpgDiagLog.log("Coord", "waitForForeground ready gen=" + gen
                        + " focused=" + focused + " elapsedMs=" + elapsed);
                host.postDelayed(() -> {
                    if (isCurrentAndAlive(host, gen)) {
                        task.run();
                    }
                }, SETTLE_FRAME_DELAY_MS);
            }
        };
        host.postDelayed(poll, SETTLE_FRAME_DELAY_MS);
    }

    private boolean isCurrentAndAlive(final HostSurface host, final long gen) {
        return transitionGen.get() == gen && host != null && host.isAlive();
    }
}
