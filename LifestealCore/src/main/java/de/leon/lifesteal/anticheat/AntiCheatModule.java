package de.leon.lifesteal.anticheat;

import de.leon.lifesteal.LifestealCore;

/**
 * Reserved module boundary for a future anti-cheat implementation.
 * No checks, detections, punishments, or packet analysis are active.
 */
public final class AntiCheatModule {
    private final LifestealCore plugin;

    public AntiCheatModule(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public LifestealCore plugin() {
        return plugin;
    }
}
