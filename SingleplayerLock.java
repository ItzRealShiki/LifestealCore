package dev.spesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * Die Sperre: ESP ist nur erlaubt, wenn der Client in einer eigenen
 * Singleplayer-Welt (integrierter Server) ist. Auf jedem fremden Server,
 * bei Realms oder ohne Welt ist sie immer gesperrt.
 */
public final class SingleplayerLock {

    /** true: auch die eigene, per "Im LAN oeffnen" veroeffentlichte Welt ist erlaubt. */
    public static final boolean ALLOW_OWN_LAN = true;

    private SingleplayerLock() {}

    public static boolean isAllowed(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        if (mc.getCurrentServer() != null) return false;      // verbunden mit fremdem Server

        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return false;                      // kein integrierter Server

        return ALLOW_OWN_LAN || !server.isPublished();
    }
}
