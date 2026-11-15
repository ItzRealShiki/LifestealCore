package de.leon.lifesteal.ban;

import de.leon.lifesteal.LifestealCore;

/**
 * Reserved module boundary for the future ban system.
 * No ban commands, punishments, persistence, or chat-mute behavior are implemented yet.
 */
public final class BanModule {
    private final LifestealCore plugin;

    public BanModule(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public LifestealCore plugin() {
        return plugin;
    }
}
