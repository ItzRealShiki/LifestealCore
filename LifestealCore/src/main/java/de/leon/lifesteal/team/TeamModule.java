package de.leon.lifesteal.team;

import de.leon.lifesteal.LifestealCore;

/**
 * Reserved module boundary for the future player-team system.
 * This is only a structural placeholder; it does not create or manage teams.
 */
public final class TeamModule {
    private final LifestealCore plugin;

    public TeamModule(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public LifestealCore plugin() {
        return plugin;
    }
}
