package cn.garymb.ygomobile.rpg.data;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import cn.garymb.ygomobile.rpg.model.OcgCanonDuel;
import cn.garymb.ygomobile.rpg.model.OcgErrataCatalog;
import cn.garymb.ygomobile.rpg.model.RpgCatalog;

/**
 * Loads the OCG RPG catalog JSON, official OCG card errata history JSON,
 * and canonical manga/anime/movie duel recreation JSON from assets or streams.
 */
public final class RpgCatalogLoader {
    public static final String ASSET_CATALOG_PATH = "rpg/ocg_demo_catalog.json";
    public static final String ASSET_ERRATA_PATH = "rpg/ocg_errata_history.json";
    public static final String ASSET_CANON_DUELS_PATH = "rpg/ocg_canon_duels.json";
    private static final Gson GSON = new Gson();

    private RpgCatalogLoader() {}

    public static RpgCatalog loadFromAssets(Context context) throws IOException {
        try (InputStream catalogIs = context.getAssets().open(ASSET_CATALOG_PATH)) {
            InputStream errataIs = null;
            InputStream canonIs = null;
            try {
                try {
                    errataIs = context.getAssets().open(ASSET_ERRATA_PATH);
                } catch (IOException ignored) {
                }
                try {
                    canonIs = context.getAssets().open(ASSET_CANON_DUELS_PATH);
                } catch (IOException ignored) {
                }
                return loadFromStreams(catalogIs, errataIs, canonIs);
            } finally {
                if (errataIs != null) {
                    try { errataIs.close(); } catch (IOException ignored) {}
                }
                if (canonIs != null) {
                    try { canonIs.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    public static RpgCatalog loadFromStream(InputStream is) throws IOException {
        return loadFromStreams(is, null, null);
    }

    public static RpgCatalog loadFromStreams(InputStream catalogIs, InputStream errataIs) throws IOException {
        return loadFromStreams(catalogIs, errataIs, null);
    }

    public static RpgCatalog loadFromStreams(InputStream catalogIs,
                                             InputStream errataIs,
                                             InputStream canonIs) throws IOException {
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
        if (canonIs != null) {
            try (InputStreamReader canonReader = new InputStreamReader(canonIs, StandardCharsets.UTF_8)) {
                OcgCanonDuel.CatalogRoot canonRoot = GSON.fromJson(canonReader, OcgCanonDuel.CatalogRoot.class);
                if (canonRoot != null) {
                    if (canonRoot.canonDuels != null) {
                        catalog.canonDuels = canonRoot.canonDuels;
                    }
                    if (canonRoot.canonCardCatalog != null) {
                        catalog.canonCardCatalog = canonRoot.canonCardCatalog;
                    }
                }
            }
        }
        catalog.buildIndexes();
        return catalog;
    }
}
