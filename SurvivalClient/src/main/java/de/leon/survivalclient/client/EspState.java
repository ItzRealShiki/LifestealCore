package de.leon.survivalclient.client;

import net.minecraft.client.Minecraft;

public final class EspState {
    public static boolean enabled = false;

    private EspState() {}

    public static boolean isActive() {
        return enabled && ServerAccess.isAllowed(Minecraft.getInstance());
    }
}
