package de.leon.lifesteal.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.Location;

import java.util.logging.Logger;

/**
 * Registers LifestealCore's KOTH flags in the real WorldGuard registry.
 * This makes them available through the normal /rg flag command.
 */
public final class WorldGuardKothFlags {
    public static final String PVP_ARENA_NAME = "koth-pvp-arena";
    public static final String CAPTURE_NAME = "koth-capture";
    public static StateFlag PVP_ARENA;
    public static StateFlag CAPTURE;

    private WorldGuardKothFlags() { }

    public static void registerFlags(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        PVP_ARENA = register(registry, PVP_ARENA_NAME, logger);
        CAPTURE = register(registry, CAPTURE_NAME, logger);
    }

    private static StateFlag register(FlagRegistry registry, String name, Logger logger) {
        try {
            StateFlag flag = new StateFlag(name, false);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException ex) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag stateFlag) return stateFlag;
            logger.warning("WorldGuard flag '" + name + "' already exists with an incompatible type.");
            return null;
        } catch (IllegalStateException ex) {
            logger.warning("Could not register WorldGuard flag '" + name + "': " + ex.getMessage());
            return null;
        }
    }

    public static boolean hasFlagAt(Location location, StateFlag flag) {
        if (location == null || location.getWorld() == null || flag == null) return false;
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .queryState(BukkitAdapter.adapt(location), null, flag) == StateFlag.State.ALLOW;
    }
}
