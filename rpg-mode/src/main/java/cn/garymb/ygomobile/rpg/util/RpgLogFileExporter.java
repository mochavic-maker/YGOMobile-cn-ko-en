package cn.garymb.ygomobile.rpg.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Exports formatted RPG/YGOMobile duel replay logs directly into the device's public
 * {@code Download/} folder as a fixed UTF-8 file {@code ygomobile_duel_log.txt}, overwriting
 * the previous export in-place so the user never accumulates stale log files.
 */
public final class RpgLogFileExporter {

    public static final String FIXED_LOG_FILENAME = "ygomobile_duel_log.txt";

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
     * Saves {@code logContent} to {@code Download/ygomobile_duel_log.txt}, overwriting any
     * previously exported log file in-place.
     */
    public static ExportResult saveDuelLogToDownloads(Context context, String logContent) {
        if (context == null || logContent == null || logContent.isEmpty()) {
            return ExportResult.fail("日志内容为空");
        }
        byte[] bytes = logContent.getBytes(StandardCharsets.UTF_8);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ExportResult ms = writeViaMediaStoreDownloads(context, FIXED_LOG_FILENAME, bytes);
            if (ms.success) {
                return ms;
            }
            // Fall through to legacy/app-downloads fallback if MediaStore insert fails on custom ROMs
            ExportResult legacy = writeLegacyDownloadFile(context, FIXED_LOG_FILENAME, bytes);
            return legacy.success ? legacy : ms;
        }
        return writeLegacyDownloadFile(context, FIXED_LOG_FILENAME, bytes);
    }

    private static ExportResult writeViaMediaStoreDownloads(Context context, String fileName, byte[] bytes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ExportResult.fail("API < 29");
        }
        ContentResolver resolver = context.getContentResolver();
        if (resolver == null) {
            return ExportResult.fail("无法访问系统存储服务");
        }

        // Remove any prior app-owned ygomobile_duel_log.txt in Download/ so MediaStore never
        // auto-suffixes "(1)", "(2)" and the freshly written file always floats to top by DATE_ADDED.
        Uri existingUri = findExistingDownloadUri(resolver, fileName);
        if (existingUri != null) {
            boolean deleted = false;
            try {
                deleted = resolver.delete(existingUri, null, null) > 0;
            } catch (Throwable ignored) {
            }
            if (!deleted) {
                // Fallback: truncate and overwrite existing URI in-place
                try (OutputStream os = resolver.openOutputStream(existingUri, "wt")) {
                    if (os != null) {
                        os.write(bytes);
                        os.flush();
                        return ExportResult.ok("Download/" + fileName);
                    }
                } catch (Throwable ignored) {
                }
            }
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
            try (OutputStream os = resolver.openOutputStream(itemUri, "wt")) {
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

    private static Uri findExistingDownloadUri(ContentResolver resolver, String fileName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        String[] projection = new String[]{MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND ("
                + MediaStore.Downloads.RELATIVE_PATH + "=? OR "
                + MediaStore.Downloads.RELATIVE_PATH + "=?)";
        String[] args = new String[]{
                fileName,
                Environment.DIRECTORY_DOWNLOADS + "/",
                Environment.DIRECTORY_DOWNLOADS
        };
        try (Cursor c = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static ExportResult writeLegacyDownloadFile(Context context, String fileName, byte[] bytes) {
        try {
            File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                File out = new File(publicDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(out, false)) {
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
                try (FileOutputStream fos = new FileOutputStream(out, false)) {
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
