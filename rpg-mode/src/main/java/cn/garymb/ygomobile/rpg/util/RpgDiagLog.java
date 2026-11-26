package cn.garymb.ygomobile.rpg.util;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight rolling diagnostic logger for RPG Mode host transitions, native 3D replay,
 * Deck Editor, and MainMenuDialog interception.
 *
 * <p>Writes to a bounded in-memory ring buffer AND appends to {@code ygocore/log/rpg_debug.log}
 * (preferring public external storage {@code /sdcard/ygocore/log/rpg_debug.log} when writable,
 * falling back to app-private {@code externalFilesDir/ygocore/log/rpg_debug.log} and cache).
 * Safe to invoke from JVM unit tests where Android runtime stubs may throw.</p>
 */
public final class RpgDiagLog {
    private static final String LOGCAT_TAG = "RpgDiag";
    private static final String LOG_FILE_NAME = "rpg_debug.log";
    private static final int MAX_RING_LINES = 400;
    private static final long MAX_FILE_BYTES = 192L * 1024L;
    private static final long KEEP_FILE_BYTES = 96L * 1024L;

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> RING = new ArrayDeque<>(MAX_RING_LINES + 16);
    private static volatile File sLogFile = null;
    private static volatile boolean sInitialized = false;

    private RpgDiagLog() {}

    /**
     * Initializes the on-disk log file location and preloads recent lines so diagnostics survive
     * Activity recreation or process restart. Safe to call repeatedly.
     */
    public static void init(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (sInitialized && sLogFile != null) return;
            Context appCtx = context.getApplicationContext();
            if (appCtx == null) appCtx = context;
            File resolved = resolveWritableLogFile(appCtx);
            if (resolved != null) {
                sLogFile = resolved;
                if (RING.isEmpty() && resolved.exists() && resolved.length() > 0L) {
                    preloadTailLocked(resolved);
                }
            }
            sInitialized = true;
        }
    }

    /** Logs a single timestamped diagnostic event to ring buffer, logcat, and rolling file. */
    public static void log(String tag, String message) {
        String ts = formatNow();
        String safeTag = (tag != null && !tag.isEmpty()) ? tag : "RPG";
        String safeMsg = message != null ? message : "";
        String line = ts + " [" + safeTag + "] " + safeMsg;
        appendLine(line);
    }

    /**
     * Logs a diagnostic event along with a compact caller stack trail (up to {@code maxFrames}
     * frames) so unexpected {@code MainMenuDialog.showMainMenu()} or lifecycle triggers can be
     * pinpointed immediately from user logs.
     */
    public static void logWithCallerStack(String tag, String message, int maxFrames) {
        String stackSummary = captureCompactStack(Math.max(1, Math.min(maxFrames, 8)));
        log(tag, message + (stackSummary.isEmpty() ? "" : (" | caller=" + stackSummary)));
    }

    /** Returns up to {@code maxLines} most recent log lines joined by newline. */
    public static String getRecentLogsText(int maxLines) {
        synchronized (LOCK) {
            if (RING.isEmpty()) {
                return "(暂无 RPG 诊断日志)";
            }
            int limit = maxLines > 0 ? maxLines : MAX_RING_LINES;
            int total = RING.size();
            int skip = Math.max(0, total - limit);
            StringBuilder sb = new StringBuilder();
            int idx = 0;
            for (String line : RING) {
                if (idx++ >= skip) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(line);
                }
            }
            return sb.toString();
        }
    }

    /** Returns the resolved absolute path of {@code rpg_debug.log}, or a friendly hint if unavailable. */
    public static String getLogFilePath() {
        File f = sLogFile;
        return f != null ? f.getAbsolutePath() : "仅内存缓存（未挂载存储目录）";
    }

    /** Clears both the in-memory ring buffer and the on-disk log file. */
    public static void clear() {
        synchronized (LOCK) {
            RING.clear();
            File f = sLogFile;
            if (f != null && f.exists()) {
                try (FileOutputStream fos = new FileOutputStream(f, false)) {
                    fos.write(new byte[0]);
                } catch (Throwable ignored) {
                }
            }
        }
        log("Diag", "Log cleared by user");
    }

    private static void appendLine(String line) {
        try {
            android.util.Log.i(LOGCAT_TAG, line);
        } catch (Throwable ignored) {
            // Expected in plain JVM unit tests where android.util.Log is stubbed.
        }
        synchronized (LOCK) {
            while (RING.size() >= MAX_RING_LINES) {
                RING.removeFirst();
            }
            RING.addLast(line);
            File f = sLogFile;
            if (f != null) {
                appendToFileLocked(f, line);
            }
        }
    }

    private static void appendToFileLocked(File file, String line) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                trimFileLocked(file);
            }
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void trimFileLocked(File file) {
        try {
            long len = file.length();
            if (len <= KEEP_FILE_BYTES) return;
            long skip = len - KEEP_FILE_BYTES;
            byte[] tail;
            try (FileInputStream fis = new FileInputStream(file)) {
                long skipped = fis.skip(skip);
                int remaining = (int) Math.max(0L, len - skipped);
                tail = new byte[remaining];
                int read = 0;
                while (read < remaining) {
                    int n = fis.read(tail, read, remaining - read);
                    if (n <= 0) break;
                    read += n;
                }
                int start = 0;
                for (int i = 0; i < read; i++) {
                    if (tail[i] == '\n') {
                        start = i + 1;
                        break;
                    }
                }
                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    if (read > start) {
                        fos.write(tail, start, read - start);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void preloadTailLocked(File file) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                while (RING.size() >= MAX_RING_LINES) {
                    RING.removeFirst();
                }
                RING.addLast(line);
            }
        } catch (Throwable ignored) {
        }
    }

    private static File resolveWritableLogFile(Context context) {
        List<File> dirs = new ArrayList<>(3);
        try {
            File extRoot = Environment.getExternalStorageDirectory();
            if (extRoot != null) {
                dirs.add(new File(extRoot, "ygocore/log"));
            }
        } catch (Throwable ignored) {
        }
        try {
            File appExt = context.getExternalFilesDir("ygocore/log");
            if (appExt != null) dirs.add(appExt);
        } catch (Throwable ignored) {
        }
        try {
            File cache = context.getCacheDir();
            if (cache != null) dirs.add(new File(cache, "ygocore/log"));
        } catch (Throwable ignored) {
        }

        for (File dir : dirs) {
            if (dir == null) continue;
            try {
                if (!dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }
                if (dir.isDirectory()) {
                    File target = new File(dir, LOG_FILE_NAME);
                    try (FileOutputStream fos = new FileOutputStream(target, true)) {
                        fos.flush();
                    }
                    return target;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String captureCompactStack(int maxFrames) {
        try {
            StackTraceElement[] frames = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            int added = 0;
            for (StackTraceElement el : frames) {
                String cls = el.getClassName();
                if (cls == null) continue;
                if (cls.startsWith("java.lang.Thread")
                        || cls.startsWith("dalvik.system.")
                        || cls.equals(RpgDiagLog.class.getName())
                        || cls.endsWith(".RpgIntegration")) {
                    continue;
                }
                String simple = cls;
                int dot = cls.lastIndexOf('.');
                if (dot >= 0 && dot + 1 < cls.length()) {
                    simple = cls.substring(dot + 1);
                }
                if (sb.length() > 0) sb.append(" <- ");
                sb.append(simple).append('.').append(el.getMethodName())
                        .append(':').append(el.getLineNumber());
                if (++added >= maxFrames) break;
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String formatNow() {
        try {
            return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (Throwable ignored) {
            return "00-00 00:00:00.000";
        }
    }
}
