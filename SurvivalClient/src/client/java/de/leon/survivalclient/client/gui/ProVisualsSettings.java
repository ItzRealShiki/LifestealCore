package de.leon.survivalclient.client.gui;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persistent ProVisuals preferences. */
public final class ProVisualsSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("provisuals.properties");
    private static final Properties VALUES = new Properties();

    public static boolean musicBarEnabled = true;
    public static boolean musicBarMoveMode = false;
    public static int musicBarX = -1;
    public static int musicBarY = 8;
    public static float musicBarScale = 1.0f;

    private ProVisualsSettings() {}

    public static void load() {
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) { VALUES.load(in); }
            catch (IOException ignored) {}
        }
        musicBarEnabled = Boolean.parseBoolean(VALUES.getProperty("musicBar.enabled", "true"));
        musicBarX = parseInt("musicBar.x", -1);
        musicBarY = parseInt("musicBar.y", 8);
        try { musicBarScale = Float.parseFloat(VALUES.getProperty("musicBar.scale", "1.0")); }
        catch (NumberFormatException ignored) { musicBarScale = 1.0f; }
        musicBarScale = Math.max(0.75f, Math.min(1.5f, musicBarScale));
    }

    private static int parseInt(String key, int fallback) {
        try { return Integer.parseInt(VALUES.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    public static void save() {
        VALUES.setProperty("musicBar.enabled", Boolean.toString(musicBarEnabled));
        VALUES.setProperty("musicBar.x", Integer.toString(musicBarX));
        VALUES.setProperty("musicBar.y", Integer.toString(musicBarY));
        VALUES.setProperty("musicBar.scale", Float.toString(musicBarScale));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) { VALUES.store(out, "ProVisuals settings"); }
        } catch (IOException ignored) {}
    }
}
