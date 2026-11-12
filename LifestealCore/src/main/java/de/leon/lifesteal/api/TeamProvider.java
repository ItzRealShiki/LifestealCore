package de.leon.lifesteal.api;

import java.util.UUID;

/**
 * For a team plugin. Register it with Bukkit's ServicesManager:
 * <pre>
 * getServer().getServicesManager().register(TeamProvider.class, provider, this, ServicePriority.Normal);
 * </pre>
 * Players in the same team do not win or lose hearts when one kills the other,
 * and the team name is shown on the scoreboard (%team%).
 */
public interface TeamProvider {

    boolean sameTeam(UUID a, UUID b);

    /** Team name of a player or null. */
    default String teamName(UUID player) {
        return null;
    }
}
