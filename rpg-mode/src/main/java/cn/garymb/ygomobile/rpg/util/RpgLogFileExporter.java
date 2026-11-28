package cn.garymb.ygomobile.rpg.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Exports formatted RPG/YGOMobile duel replay logs directly into the device's public
 * {@code Download/} folder as UTF-8 {@code .txt} files (using {@link MediaStore.Downloads}
 * on Android 10+ so no runtime storage permission is required, with direct file fallback on API <= 28).
 */
public final class RpgLogFileExporter {

    private RpgLogFileExporter() {}

    public static final class ExportResult {
        public final boolean success;
        public final String displayPath;
        public final String errorMessage;

        private ExportResult(boolean success, String displayPath, String errorMessage) {
            this.success = success;
            this.displayPath = displayPath;
            this.errorMessage = errorMessage;
        }

        public static ExportResult ok(String displayPath) {
            return new ExportResult(true, displayPath, null);
        }

        public static ExportResult fail(String errorMessage) {
            return new ExportResult(false, null, errorMessage);
        }
    }

    /**
     * Saves {@code logContent} as {@code ygomobile_duel_log_yyyyMMdd_HHmmss.txt} inside
     * the system {@code Download/} directory.
     */
    public static ExportResult saveDuelLogToDownloads(Context context, String logContent) {
        if (context == null || logContent == null || logContent.isEmpty()) {
            return ExportResult.fail("日志内容为空");
        }
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "ygomobile_duel_log_" + ts + ".txt";
        byte[] bytes = logContent.getBytes(StandardCharsets.UTF_8);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ExportResult ms = writeViaMediaStoreDownloads(context, fileName, bytes);
            if (ms.success) {
                return ms;
            }
            // Fall through to legacy/app-downloads fallback if MediaStore insert fails on custom ROMs
            ExportResult legacy = writeLegacyDownloadFile(context, fileName, bytes);
            return legacy.success ? legacy : ms;
        }
        return writeLegacyDownloadFile(context, fileName, bytes);
    }

    private static ExportResult writeViaMediaStoreDownloads(Context context, String fileName, byte[] bytes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ExportResult.fail("API < 29");
        }
        ContentResolver resolver = context.getContentResolver();
        if (resolver == null) {
            return ExportResult.fail("无法访问系统存储服务");
        }
        Uri itemUri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (itemUri == null) {
                return ExportResult.fail("创建 Download 文件失败");
            }
            try (OutputStream os = resolver.openOutputStream(itemUri)) {
                if (os == null) {
                    throw new java.io.IOException("无法打开输出流");
                }
                os.write(bytes);
                os.flush();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(itemUri, done, null, null);
            return ExportResult.ok("Download/" + fileName);
        } catch (Throwable e) {
            if (itemUri != null) {
                try {
                    resolver.delete(itemUri, null, null);
                } catch (Throwable ignored) {
                }
            }
            return ExportResult.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("deprecation")
    private static ExportResult writeLegacyDownloadFile(Context context, String fileName, byte[] bytes) {
        try {
            File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                File out = new File(publicDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                    fos.flush();
                }
                return ExportResult.ok("Download/" + fileName);
            }
        } catch (Throwable ignored) {
        }
        try {
            File appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (appDir != null && (appDir.exists() || appDir.mkdirs())) {
                File out = new File(appDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                    fos.flush();
                }
                return ExportResult.ok(out.getAbsolutePath());
            }
        } catch (Throwable e) {
            return ExportResult.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return ExportResult.fail("无法写入 Download 目录");
    }
}
