package cn.garymb.ygomobile.rpg.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.garymb.ygomobile.rpg.bridge.RpgDuelHostBridge;
import cn.garymb.ygomobile.rpg.bridge.RpgSessionManager;

/**
 * Lightweight asynchronous card artwork loader for :rpg-mode.
 * Fetches card image bytes through RpgDuelHostBridge (backed by YGOMobile's ImageLoader / pics.zip)
 * and caches decoded Bitmaps in an LruCache to keep Pack Simulator, Card Pool, and Deck UI snappy.
 */
public final class RpgCardArtLoader {

    private static final int CACHE_SIZE_KB = Math.min(20 * 1024, (int) (Runtime.getRuntime().maxMemory() / 1024 / 8));
    private static final LruCache<Integer, Bitmap> sCache = new LruCache<Integer, Bitmap>(CACHE_SIZE_KB) {
        @Override
        protected int sizeOf(Integer key, Bitmap bitmap) {
            return bitmap == null ? 0 : (bitmap.getByteCount() / 1024);
        }
    };

    private static final ExecutorService sExecutor = Executors.newFixedThreadPool(4);
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private RpgCardArtLoader() {}

    /**
     * Loads the card artwork for {@code cardId} into {@code target} asynchronously.
     * Uses {@code targetWidthPx} to downsample large textures cleanly.
     */
    public static void loadInto(final ImageView target, final int cardId, final int targetWidthPx) {
        if (target == null) return;
        target.setTag(cardId);

        Bitmap cached = sCache.get(cardId);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageDrawable(createCardBackDrawable());

        final RpgDuelHostBridge bridge = RpgSessionManager.getHostBridge();
        if (bridge == null) return;

        sExecutor.execute(() -> {
            byte[] data = null;
            try {
                data = bridge.loadCardImageBytes(cardId);
            } catch (Throwable ignored) {
            }
            if (data == null || data.length == 0) return;

            final Bitmap bmp = decodeSampled(data, Math.max(120, targetWidthPx));
            if (bmp == null) return;

            sCache.put(cardId, bmp);
            sMainHandler.post(() -> {
                Object tag = target.getTag();
                if (tag instanceof Integer && ((Integer) tag) == cardId) {
                    target.setImageBitmap(bmp);
                }
            });
        });
    }

    /**
     * Creates a stylized Yu-Gi-Oh! card-back drawable for unflipped / loading cards.
     */
    public static GradientDrawable createCardBackDrawable() {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#5C3A21"),
                        Color.parseColor("#3B2314"),
                        Color.parseColor("#1E120B")
                });
        bg.setCornerRadius(10f);
        bg.setStroke(3, Color.parseColor("#B47B38"));
        return bg;
    }

    private static Bitmap decodeSampled(byte[] data, int reqWidth) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);

            int inSampleSize = 1;
            if (opts.outWidth > reqWidth) {
                int halfW = opts.outWidth / 2;
                while ((halfW / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = Math.max(1, inSampleSize);
            decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, decodeOpts);
        } catch (Throwable t) {
            return null;
        }
    }
}
