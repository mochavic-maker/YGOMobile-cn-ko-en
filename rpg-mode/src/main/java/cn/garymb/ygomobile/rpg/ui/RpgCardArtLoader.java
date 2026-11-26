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

    private static final LruCache<String, Bitmap> sPackCoverCache = new LruCache<String, Bitmap>(4 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap == null ? 0 : (bitmap.getByteCount() / 1024);
        }
    };

    /**
     * Loads the authentic 1999 OCG pack/box/vending cover image from assets/rpg/packs/<packId>.webp.
     */
    public static void loadPackCoverInto(final android.content.Context context,
                                         final ImageView target,
                                         final String packId) {
        if (context == null || target == null || packId == null || packId.isEmpty()) return;
        target.setTag("pack:" + packId);

        Bitmap cached = sPackCoverCache.get(packId);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageDrawable(createCardBackDrawable());
        final android.content.Context appCtx = context.getApplicationContext();

        sExecutor.execute(() -> {
            Bitmap bmp = null;
            try (java.io.InputStream is = appCtx.getAssets().open("rpg/packs/" + packId + ".webp")) {
                bmp = BitmapFactory.decodeStream(is);
            } catch (Throwable ignored) {
            }
            if (bmp == null) return;

            sPackCoverCache.put(packId, bmp);
            final Bitmap finalBmp = bmp;
            sMainHandler.post(() -> {
                Object tag = target.getTag();
                if (("pack:" + packId).equals(tag)) {
                    target.setImageBitmap(finalBmp);
                }
            });
        });
    }

    private static final LruCache<String, Bitmap> sAvatarCache = new LruCache<String, Bitmap>(4 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap == null ? 0 : (bitmap.getByteCount() / 1024);
        }
    };

    /**
     * Loads a Yu-Gi-Oh! anime-style AI opponent portrait from assets/rpg/avatars/<opponentId>.webp.
     */
    public static void loadOpponentAvatarInto(final android.content.Context context,
                                              final ImageView target,
                                              final String opponentId) {
        if (context == null || target == null || opponentId == null || opponentId.isEmpty()) return;
        target.setTag("avatar:" + opponentId);

        Bitmap cached = sAvatarCache.get(opponentId);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageDrawable(createAvatarPlaceholderDrawable());
        final android.content.Context appCtx = context.getApplicationContext();

        sExecutor.execute(() -> {
            Bitmap bmp = null;
            try (java.io.InputStream is = appCtx.getAssets().open("rpg/avatars/" + opponentId + ".webp")) {
                bmp = BitmapFactory.decodeStream(is);
            } catch (Throwable ignored) {
            }
            if (bmp == null) return;

            sAvatarCache.put(opponentId, bmp);
            final Bitmap finalBmp = bmp;
            sMainHandler.post(() -> {
                Object tag = target.getTag();
                if (("avatar:" + opponentId).equals(tag)) {
                    target.setImageBitmap(finalBmp);
                }
            });
        });
    }

    /**
     * Synchronously returns the cached (or decoded from assets) Yu-Gi-Oh!-style portrait Bitmap
     * for an RPG AI opponent. Used by YGOMobile's live duel UI ({@code TextureLoader.getAvatar}).
     */
    public static Bitmap loadOpponentAvatarSync(android.content.Context context, String opponentId) {
        if (context == null || opponentId == null || opponentId.isEmpty()) return null;
        Bitmap cached = sAvatarCache.get(opponentId);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        try (java.io.InputStream is = context.getApplicationContext().getAssets()
                .open("rpg/avatars/" + opponentId + ".webp")) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null) {
                sAvatarCache.put(opponentId, bmp);
            }
            return bmp;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static GradientDrawable createAvatarPlaceholderDrawable() {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#1E293B"),
                        Color.parseColor("#0F172A")
                });
        bg.setCornerRadius(12f);
        bg.setStroke(2, Color.parseColor("#D4AF37"));
        return bg;
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
