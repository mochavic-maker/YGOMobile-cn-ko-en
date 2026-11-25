package cn.garymb.ygomobile.rpg.data;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import cn.garymb.ygomobile.rpg.model.OcgErrataCatalog;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Loads the OCG RPG catalog JSON and official OCG card errata history JSON from assets or streams.
 */
public final class RpgCatalogLoader {
    public static final String ASSET_CATALOG_PATH = "rpg/ocg_demo_catalog.json";
    public static final String ASSET_ERRATA_PATH = "rpg/ocg_errata_history.json";
    private static final Gson GSON = new Gson();

    private RpgCatalogLoader() {}

    public static RpgCatalog loadFromAssets(Context context) throws IOException {
        try (InputStream catalogIs = context.getAssets().open(ASSET_CATALOG_PATH)) {
            InputStream errataIs = null;
            try {
                errataIs = context.getAssets().open(ASSET_ERRATA_PATH);
                return loadFromStreams(catalogIs, errataIs);
            } catch (IOException ignored) {
                return loadFromStream(catalogIs);
            } finally {
                if (errataIs != null) {
                    try { errataIs.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    public static RpgCatalog loadFromStream(InputStream is) throws IOException {
        return loadFromStreams(is, null);
    }

    public static RpgCatalog loadFromStreams(InputStream catalogIs, InputStream errataIs) throws IOException {
        RpgCatalog catalog;
        try (InputStreamReader reader = new InputStreamReader(catalogIs, StandardCharsets.UTF_8)) {
            catalog = GSON.fromJson(reader, RpgCatalog.class);
            if (catalog == null) {
                throw new IOException("Empty RPG catalog JSON");
            }
        }
        if (errataIs != null) {
            try (InputStreamReader errReader = new InputStreamReader(errataIs, StandardCharsets.UTF_8)) {
                OcgErrataCatalog errataCatalog = GSON.fromJson(errReader, OcgErrataCatalog.class);
                if (errataCatalog != null && errataCatalog.records != null) {
                    catalog.errataRecords = errataCatalog.records;
                }
            }
        }
        catalog.buildIndexes();
        return catalog;
    }
}
