package de.leon.lifesteal.combat;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.regions.RegionType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * WorldGuard flag "combat-forcefield": regions with "allow" are arenas that players in combat
 * cannot leave. The border is shown with red glass (only for that player).
 * This class is only loaded when WorldGuard is installed.
 */
public final class WorldGuardForcefield implements Forcefield {

    public static final String FLAG_NAME = "combat-forcefield";
    private static StateFlag flag;

    private static final BlockData GLASS = Material.RED_STAINED_GLASS.createBlockData();

    private record Pos(int x, int y, int z) {
    }

    private static final class Shown {
        private final UUID world;
        private final Set<Pos> blocks = new HashSet<>();

        private Shown(UUID world) {
            this.world = world;
        }
    }

    private final Supplier<FileConfiguration> config;
    private final Map<UUID, Shown> shown = new HashMap<>();

    public WorldGuardForcefield(Supplier<FileConfiguration> config) {
        this.config = config;
    }

    /** Must run in onLoad (before WorldGuard loads its regions). */
    public static void registerFlag(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag f = new StateFlag(FLAG_NAME, false);
            registry.register(f);
            flag = f;
        } catch (FlagConflictException ex) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag stateFlag) {
                flag = stateFlag;
            } else {
                logger.warning("The flag " + FLAG_NAME + " is already registered with another type!");
            }
        } catch (IllegalStateException ex) {
            logger.warning("Could not register the flag " + FLAG_NAME + ": " + ex.getMessage());
        }
    }

    public static boolean ready() {
        return flag != null;
    }

    private RegionContainer container() {
        return WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    @Override
    public boolean isForcefield(Location location) {
        if (flag == null || location == null || location.getWorld() == null) return false;
        RegionQuery query = container().createQuery();
        return query.queryState(BukkitAdapter.adapt(location), null, flag) == StateFlag.State.ALLOW;
    }

    @Override
    public void update(Player player) {
        if (flag == null) return;
        World world = player.getWorld();
        Location loc = player.getLocation();

        // the forcefield is only shown to players inside the zone (they want to get out)
        if (!isForcefield(loc)) {
            clear(player);
            return;
        }
        RegionManager regionManager = container().get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            clear(player);
            return;
        }

        int radius = Math.max(2, config.get().getInt("combat.forcefield.radius", 3));
        int vertical = Math.max(1, config.get().getInt("combat.forcefield.vertical", 4));
        int px = loc.getBlockX();
        int py = loc.getBlockY();
        int pz = loc.getBlockZ();
        int minY = Math.max(world.getMinHeight(), py - vertical);
        int maxY = Math.min(world.getMaxHeight() - 1, py + vertical);

        ProtectedCuboidRegion area = new ProtectedCuboidRegion("combat_scan",
                BlockVector3.at(px - radius, minY, pz - radius),
                BlockVector3.at(px + radius, maxY, pz + radius));

        List<ProtectedRegion> regions = new ArrayList<>();
        for (ProtectedRegion region : regionManager.getApplicableRegions(area)) {
            if (region.getType() != RegionType.GLOBAL && region.getFlag(flag) == StateFlag.State.ALLOW) {
                regions.add(region);
            }
        }

        Set<Pos> wanted = new HashSet<>();
        if (!regions.isEmpty()) {
            for (int x = px - radius; x <= px + radius; x++) {
                for (int z = pz - radius; z <= pz + radius; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        if (!inAny(regions, x, y, z)) continue;
                        boolean border = !inAny(regions, x + 1, y, z) || !inAny(regions, x - 1, y, z)
                                || !inAny(regions, x, y, z + 1) || !inAny(regions, x, y, z - 1);
                        if (border && world.getBlockAt(x, y, z).getType().isAir()) {
                            wanted.add(new Pos(x, y, z));
                        }
                    }
                }
            }
        }

        Shown current = shown.get(player.getUniqueId());
        if (current == null || !current.world.equals(world.getUID())) {
            current = new Shown(world.getUID());
            shown.put(player.getUniqueId(), current);
        }

        Iterator<Pos> it = current.blocks.iterator();
        while (it.hasNext()) {
            Pos pos = it.next();
            if (!wanted.contains(pos)) {
                restore(player, world, pos);
                it.remove();
            }
        }
        for (Pos pos : wanted) {
            if (current.blocks.add(pos)) {
                player.sendBlockChange(new Location(world, pos.x(), pos.y(), pos.z()), GLASS);
            }
        }
    }

    @Override
    public void clear(Player player) {
        Shown current = shown.remove(player.getUniqueId());
        if (current == null) return;
        World world = Bukkit.getWorld(current.world);
        if (world == null || !player.isOnline() || !player.getWorld().equals(world)) return;
        for (Pos pos : current.blocks) restore(player, world, pos);
    }

    private void restore(Player player, World world, Pos pos) {
        player.sendBlockChange(new Location(world, pos.x(), pos.y(), pos.z()),
                world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData());
    }

    private boolean inAny(List<ProtectedRegion> regions, int x, int y, int z) {
        for (ProtectedRegion region : regions) {
            if (region.contains(x, y, z)) return true;
        }
        return false;
    }
}
