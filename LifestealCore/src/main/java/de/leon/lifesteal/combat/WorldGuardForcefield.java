package de.leon.lifesteal.combat;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * @deprecated The native region implementation is NativeRegionForcefield.
 * This compatibility class remains so downstream source integrations do not
 * fail to compile, but it deliberately does not query external WorldGuard.
 */
@Deprecated
public final class WorldGuardForcefield implements Forcefield {
    public WorldGuardForcefield(Supplier<FileConfiguration> ignored) { }

    @Override public boolean isForcefield(Location location) { return false; }
    @Override public void update(Player player) { }
    @Override public void clear(Player player) { }
}
