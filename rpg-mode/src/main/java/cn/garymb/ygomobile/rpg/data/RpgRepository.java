package cn.garymb.ygomobile.rpg.data;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import cn.garymb.ygomobile.rpg.model.RpgProfile;

/**
 * Persists player RPG save data and exported .ydk decks into app-private storage.
 */
public class RpgRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File rootDir;
    private final File profileFile;
    private final File decksDir;

    public RpgRepository(Context context) {
        this(new File(context.getFilesDir(), "rpg_ocg"));
    }

    public RpgRepository(File rootDir) {
        this.rootDir = rootDir;
        this.profileFile = new File(rootDir, "profile.json");
        this.decksDir = new File(rootDir, "decks");
    }

    public RpgProfile loadProfile() {
        if (!profileFile.exists()) {
            return new RpgProfile();
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(profileFile), StandardCharsets.UTF_8)) {
            RpgProfile loaded = GSON.fromJson(reader, RpgProfile.class);
            return loaded != null ? loaded : new RpgProfile();
        } catch (Exception e) {
            return new RpgProfile();
        }
    }

    public void saveProfile(RpgProfile profile) throws IOException {
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        File tmp = new File(rootDir, "profile.json.tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            GSON.toJson(profile, writer);
        }
        if (profileFile.exists() && !profileFile.delete()) {
            // fallback overwrite
        }
        if (!tmp.renameTo(profileFile)) {
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(profileFile), StandardCharsets.UTF_8)) {
                GSON.toJson(profile, writer);
            }
            tmp.delete();
        }
    }

    public File getPlayerDeckYdkFile() {
        if (!decksDir.exists()) decksDir.mkdirs();
        return new File(decksDir, "rpg_player_active.ydk");
    }

    public File getAiDeckYdkFile(String opponentId) {
        if (!decksDir.exists()) decksDir.mkdirs();
        return new File(decksDir, "rpg_ai_" + opponentId + ".ydk");
    }
}
