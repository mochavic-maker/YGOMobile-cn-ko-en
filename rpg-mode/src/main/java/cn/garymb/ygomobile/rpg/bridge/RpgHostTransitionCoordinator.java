package cn.garymb.ygomobile.rpg.bridge;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates foreground Activity transitions between {@code RpgHomeActivity} and {@code YGOProActivity}
 * for the three native-host scenes prone to bouncing back to {@code MainMenuDialog}:
 * <ol>
 *   <li>Live manual duel / 3D spectate ({@link #launchWaitingRoomScene})</li>
 *   <li>Native 3D replay playback ({@link #launchReplayScene})</li>
 *   <li>Native Deck Editor / read-only AI deck viewer ({@link #launchDeckEditorScene})</li>
 * </ol>
 *
 * <p>Kept free of Android framework dependencies so the exact production transition state machine,
 * stale-watcher cancellation, and post-launch self-healing safeguards can be verified directly
 * by fast JVM unit tests.</p>
 */
public final class RpgHostTransitionCoordinator {

    public static final long FOCUS_POLL_INTERVAL_MS = 50L;
    public static final long FOCUS_WAIT_TIMEOUT_MS = 1200L;
    public static final long SETTLE_FRAME_DELAY_MS = 60L;
    public static final long DECK_LOAD_DELAY_MS = 150L;
    public static final long POST_LAUNCH_VERIFY_DELAY_MS = 240L;
    public static final long RETURN_WATCHER_START_DELAY_MS = 500L;
    public static final long RETURN_WATCHER_POLL_MS = 250L;
    public static final long RETURN_WATCHER_TIMEOUT_MS = 600_000L;

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

    /** Returns the current transition generation token (incremented on every new scene launch). */
    public long getCurrentGeneration() {
        return transitionGen.get();
    }

    /** Cancels all pending foreground waits, safeguards, and return watchers. */
    public long invalidatePendingTransitions() {
        return transitionGen.incrementAndGet();
    }

    /**
     * Scene 1: Live manual duel (AI duel / Tournament manual / Canon duel) or 3D spectate.
     * Cleans stale AI/connection/dialogs, hides MainMenuDialog, waits for foreground window focus,
     * runs {@code startSceneBody}, then verifies after 240ms that MainMenuDialog stays hidden
     * and PlayerWaitingDialog actually appeared (retrying once if transient WindowToken dropped it).
     */
    public long launchWaitingRoomScene(final HostSurface host,
                                       final Runnable startSceneBody,
                                       final Runnable restoreOverridesAndSeatName) {
        if (host == null || !host.isAlive()) return -1L;
        final long gen = transitionGen.incrementAndGet();
        prepareCleanHost(host);
        waitForForegroundThenRun(host, gen, () -> {
            if (!isCurrentAndAlive(host, gen)) return;
            host.hideMainMenu();
            if (startSceneBody != null) {
                startSceneBody.run();
            }
            host.postDelayed(() -> {
                if (!isCurrentAndAlive(host, gen)) return;
                if (host.isEngineDisconnectedOrIdle() || host.isDuelActiveOrStarted()) {
                    return;
                }
                if (host.isMainMenuShowing()) {
                    host.hideMainMenu();
                }
                if (restoreOverridesAndSeatName != null) {
                    restoreOverridesAndSeatName.run();
                }
                if (!host.isWaitingDialogShowing()) {
                    host.showWaitingDialog();
                    if (restoreOverridesAndSeatName != null) {
                        host.postDelayed(() -> {
                            if (isCurrentAndAlive(host, gen) && host.isWaitingDialogShowing()) {
                                restoreOverridesAndSeatName.run();
                            }
                        }, 80L);
                    }
                }
            }, POST_LAUNCH_VERIFY_DELAY_MS);
        });
        return gen;
    }

    /**
     * Scene 2: Native 3D replay playback (Quick Duel / Tournament / AI Showdown replay).
     * Cleans stale AI/connection/dialogs, hides MainMenuDialog, waits for foreground window focus,
     * executes {@code startReplayBody}, then verifies after 240ms that MainMenuDialog stays hidden
     * and GameUI stays visible while the replay session is active.
     */
    public long launchReplayScene(final HostSurface host,
                                  final Runnable startReplayBody,
                                  final Runnable restoreAvatarOverrides) {
        if (host == null || !host.isAlive()) return -1L;
        final long gen = transitionGen.incrementAndGet();
        prepareCleanHost(host);
        waitForForegroundThenRun(host, gen, () -> {
            if (!isCurrentAndAlive(host, gen)) return;
            host.hideMainMenu();
            if (restoreAvatarOverrides != null) {
                restoreAvatarOverrides.run();
            }
            if (startReplayBody != null) {
                startReplayBody.run();
            }
            host.postDelayed(() -> {
                if (!isCurrentAndAlive(host, gen)) return;
                if (!host.isReplaySessionActive()) return;
                if (host.isMainMenuShowing()) {
                    host.hideMainMenu();
                }
                if (restoreAvatarOverrides != null) {
                    restoreAvatarOverrides.run();
                }
                if (!host.isGameUiVisible()) {
                    host.enterReplayUi();
                }
            }, POST_LAUNCH_VERIFY_DELAY_MS);
        });
        return gen;
    }

    /**
     * Scene 3: Native Deck Editor (player RPG deck editing or read-only AI deck preview).
     * Cleans stale dialogs, hides MainMenuDialog, waits for foreground window focus, opens the
     * Deck Editor, loads the target .ydk after 150ms, verifies visibility at 240ms (recovering
     * if a stray menu restore covered the editor), and arms a generation-guarded return watcher.
     */
    public long launchDeckEditorScene(final HostSurface host,
                                      final Runnable openEditorBody,
                                      final Runnable loadDeckIntoEditorBody,
                                      final Runnable onReturnToRpgMenu) {
        if (host == null || !host.isAlive()) return -1L;
        final long gen = transitionGen.incrementAndGet();
        prepareCleanHost(host);
        waitForForegroundThenRun(host, gen, () -> {
            if (!isCurrentAndAlive(host, gen)) return;
            host.hideMainMenu();
            if (openEditorBody != null) {
                openEditorBody.run();
            }
            if (loadDeckIntoEditorBody != null) {
                host.postDelayed(() -> {
                    if (!isCurrentAndAlive(host, gen)) return;
                    loadDeckIntoEditorBody.run();
                }, DECK_LOAD_DELAY_MS);
            }
            host.postDelayed(() -> {
                if (!isCurrentAndAlive(host, gen)) return;
                if (host.isMainMenuShowing()) {
                    host.hideMainMenu();
                }
                if (!host.isDeckEditorVisible()) {
                    host.showDeckEditor();
                    if (loadDeckIntoEditorBody != null) {
                        host.postDelayed(() -> {
                            if (isCurrentAndAlive(host, gen)) {
                                loadDeckIntoEditorBody.run();
                            }
                        }, DECK_LOAD_DELAY_MS);
                    }
                }
            }, POST_LAUNCH_VERIFY_DELAY_MS);
            if (onReturnToRpgMenu != null) {
                scheduleReturnWhenMainMenuReappears(host, gen, onReturnToRpgMenu);
            }
        });
        return gen;
    }

    /**
     * Arms a generation-guarded watcher that invokes {@code onReturnToRpgMenu} once MainMenuDialog
     * transitions from hidden -> visible (e.g. after a duel ends or the player exits Deck Editor).
     * Automatically cancels if any newer RPG scene launch occurs.
     */
    public long scheduleReturnToRpg(final HostSurface host, final Runnable onReturnToRpgMenu) {
        if (host == null || !host.isAlive() || onReturnToRpgMenu == null) return -1L;
        final long gen = transitionGen.incrementAndGet();
        scheduleReturnWhenMainMenuReappears(host, gen, onReturnToRpgMenu);
        return gen;
    }

    private void scheduleReturnWhenMainMenuReappears(final HostSurface host,
                                                     final long gen,
                                                     final Runnable onReturnToRpgMenu) {
        final long deadlineMs = host.nowMs() + RETURN_WATCHER_TIMEOUT_MS;
        final boolean[] sawMenuHidden = new boolean[]{!host.isMainMenuShowing()};
        final Runnable watcher = new Runnable() {
            @Override
            public void run() {
                if (!isCurrentAndAlive(host, gen)) return;
                if (host.nowMs() > deadlineMs) return;
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
                // Consume this generation so duplicate callbacks cannot fire twice
                if (transitionGen.compareAndSet(gen, gen + 1)) {
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
        final long deadlineMs = host.nowMs() + FOCUS_WAIT_TIMEOUT_MS;
        final Runnable poll = new Runnable() {
            @Override
            public void run() {
                if (!isCurrentAndAlive(host, gen)) return;
                if (host.isMainMenuShowing()) {
                    host.hideMainMenu();
                }
                if (!host.isForegroundFocused() && host.nowMs() < deadlineMs) {
                    host.postDelayed(this, FOCUS_POLL_INTERVAL_MS);
                    return;
                }
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
