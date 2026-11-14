package dev.spesp;

import net.minecraft.client.Minecraft;

public final class EspState {

    public static boolean enabled = false;

    private EspState() {}

    /** Einzige Abfrage, die Mixins und Renderer benutzen duerfen. */
    public static boolean isActive() {
        return enabled && SingleplayerLock.isAllowed(Minecraft.getInstance());
    }
}
