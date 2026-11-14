package de.leon.survivalclient.client.gui;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local ProVisuals preferences. */
public final class ProVisualsSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("provisuals.properties");
    private static final Properties VALUES = new Properties();

    public enum ColorMode { DEFAULT, CUSTOM, RAINBOW, CHROMA }

    public static boolean chestEsp;
    public static boolean xray;
    public static boolean mobEsp;
    public static boolean playerEsp;
    public static boolean itemEsp;
    public static String displayName = "";
    public static ColorMode colorMode = ColorMode.DEFAULT;
    public static int customColor = 0xFFA875F2;

    private ProVisualsSettings() {}

    public static void load() {
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                VALUES.load(in);
            } catch (IOException ignored) {
            }
        }
        chestEsp = Boolean.parseBoolean(VALUES.getProperty("esp.chest", "false"));
        xray = Boolean.parseBoolean(VALUES.getProperty("visuals.xray", "false"));
        mobEsp = Boolean.parseBoolean(VALUES.getProperty("esp.mob", "false"));
        playerEsp = Boolean.parseBoolean(VALUES.getProperty("esp.player", "false"));
        itemEsp = Boolean.parseBoolean(VALUES.getProperty("esp.item", "false"));
        displayName = VALUES.getProperty("displayName", "");
        try {
            colorMode = ColorMode.valueOf(VALUES.getProperty("colorMode", "DEFAULT"));
        } catch (IllegalArgumentException ignored) {
            colorMode = ColorMode.DEFAULT;
        }
        try {
            customColor = (int) Long.parseLong(VALUES.getProperty("customColor", "FFA875F2"), 16);
        } catch (NumberFormatException ignored) {
            customColor = 0xFFA875F2;
        }
    }

    public static void save() {
        VALUES.setProperty("esp.chest", Boolean.toString(chestEsp));
        VALUES.setProperty("visuals.xray", Boolean.toString(xray));
        VALUES.setProperty("esp.mob", Boolean.toString(mobEsp));
        VALUES.setProperty("esp.player", Boolean.toString(playerEsp));
        VALUES.setProperty("esp.item", Boolean.toString(itemEsp));
        VALUES.setProperty("displayName", displayName);
        VALUES.setProperty("colorMode", colorMode.name());
        VALUES.setProperty("customColor", String.format("%08X", customColor));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                VALUES.store(out, "ProVisuals settings");
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean isEnabled(String module) {
        return switch (module) {
            case "ChestESP" -> chestEsp;
            case "X-Ray" -> xray;
            case "MobESP" -> mobEsp;
            case "PlayerESP" -> playerEsp;
            case "ItemESP" -> itemEsp;
            default -> false;
        };
    }

    public static void toggle(String module) {
        switch (module) {
            case "ChestESP" -> chestEsp = !chestEsp;
            case "X-Ray" -> xray = !xray;
            case "MobESP" -> mobEsp = !mobEsp;
            case "PlayerESP" -> playerEsp = !playerEsp;
            case "ItemESP" -> itemEsp = !itemEsp;
        }
        save();
    }
}
