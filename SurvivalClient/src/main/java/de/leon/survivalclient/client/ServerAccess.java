package de.leon.survivalclient.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Local opt-in list. Add one server hostname per line to config/survivalclient-servers.txt.
 * This is a client preference, not proof that a server permits ESP; check server rules first.
 */
public final class ServerAccess {
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
            .resolve("survivalclient-servers.txt");
    private static final Set<String> ALLOWED = new LinkedHashSet<>();
    private static boolean loaded;

    private ServerAccess() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            Files.createDirectories(CONFIG.getParent());
            if (!Files.exists(CONFIG)) {
                Files.writeString(CONFIG,
                        "# One hostname per line. Only add servers whose rules explicitly allow this feature.\n"
                        + "# Example: play.example.net\n",
                        StandardCharsets.UTF_8);
            }
            for (String line : Files.readAllLines(CONFIG, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) {
                    ALLOWED.add(normalize(value));
                }
            }
        } catch (IOException e) {
            System.err.println("[SurvivalClient] Could not read server allow-list: " + e.getMessage());
        }
    }

    public static boolean isAllowed(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        // A local integrated server means the player is in singleplayer/LAN-hosted play.
        if (mc.getSingleplayerServer() != null) return true;
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null) return false;
        load();
        return ALLOWED.contains(normalize(server.ip));
    }

    private static String normalize(String address) {
        String value = address.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) return value.substring(1, end);
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) value = value.substring(0, colon);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
