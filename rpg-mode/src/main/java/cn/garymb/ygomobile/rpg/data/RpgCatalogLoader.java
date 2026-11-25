package cn.garymb.ygomobile.rpg.data;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Loads the OCG RPG catalog JSON from Android assets or any InputStream (for tests).
 */
public final class RpgCatalogLoader {
    public static final String ASSET_CATALOG_PATH = "rpg/ocg_demo_catalog.json";
    private static final Gson GSON = new Gson();

    private RpgCatalogLoader() {}

    public static RpgCatalog loadFromAssets(Context context) throws IOException {
        try (InputStream is = context.getAssets().open(ASSET_CATALOG_PATH)) {
            return loadFromStream(is);
        }
    }

    public static RpgCatalog loadFromStream(InputStream is) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            RpgCatalog catalog = GSON.fromJson(reader, RpgCatalog.class);
            if (catalog == null) {
                throw new IOException("Empty RPG catalog JSON");
            }
            catalog.buildIndexes();
            return catalog;
        }
    }
}
