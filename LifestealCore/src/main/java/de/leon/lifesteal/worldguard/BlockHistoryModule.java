package de.leon.lifesteal.worldguard;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BlockHistoryModule implements Listener, TabExecutor {
    private record Change(String id, int x, int y, int z, String before, String after, long time) { }
    private record Pending(UUID world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                           List<Change> changes, long expiresAt, String regionId) { }

    private final LifestealCore plugin;
    private final RegionProtectionModule regions;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Set<String> restoringBlocks = new HashSet<>();
    private final Map<UUID, String> openedContainers = new HashMap<>();

    public BlockHistoryModule(LifestealCore plugin, RegionProtectionModule regions) {
        this.plugin = plugin;
        this.regions = regions;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var command = plugin.getCommand("blocklog");
        if (command != null) command.setExecutor(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (restoringBlocks.contains(key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()))) return;
        String before = snapshot(block.getState());
        String after = snapshotAir();
        record(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                event.getPlayer().getUniqueId(), event.getPlayer().getName(), "BREAK", before, after);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (restoringBlocks.contains(key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()))) return;
        String before = snapshot(event.getBlockReplacedState());
        String after = snapshot(block.getState());
        record(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                event.getPlayer().getUniqueId(), event.getPlayer().getName(), "PLACE", before, after);
    }

    private String snapshotAir() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("block-data", Material.AIR.createBlockData().getAsString());
        return Base64.getEncoder().encodeToString(data.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String key(UUID world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private String snapshot(BlockState state) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("block-data", state.getBlockData().getAsString());
        if (state instanceof Container container) {
            data.set("container", Arrays.asList(container.getInventory().getContents()));
        }
        return Base64.getEncoder().encodeToString(data.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void restore(Block block, String payload) throws InvalidConfigurationException {
        String yamlText = new String(Base64.getDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        YamlConfiguration data = new YamlConfiguration();
        data.loadFromString(yamlText);
        String blockData = data.getString("block-data");
        if (blockData == null) throw new InvalidConfigurationException("Missing block-data");
        block.setBlockData(Bukkit.createBlockData(blockData), false);
        BlockState state = block.getState();
        if (state instanceof Container container && data.isList("container")) {
            List<?> raw = data.getList("container");
            ItemStack[] contents = new ItemStack[container.getInventory().getSize()];
            for (int i = 0; i < Math.min(contents.length, raw.size()); i++) {
                Object value = raw.get(i);
                if (value instanceof ItemStack item) contents[i] = item;
            }
            container.getInventory().setContents(contents);
            state.update(true, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (restoringBlocks.contains(key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()))) continue;
            record(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), null, "Environment",
                    "EXPLOSION", snapshot(block.getState()), snapshotAir());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (restoringBlocks.contains(key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()))) continue;
            record(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), null, "Environment",
                    "EXPLOSION", snapshot(block.getState()), snapshotAir());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof BlockState state)) return;
        if (!(state instanceof Container)) return;
        openedContainers.put(player.getUniqueId(), snapshot(state));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String before = openedContainers.remove(player.getUniqueId());
        if (before == null || !(event.getInventory().getHolder() instanceof BlockState state)
                || !(state instanceof Container)) return;
        String after = snapshot(state);
        if (before.equals(after)) return;
        Location location = state.getLocation();
        record(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                player.getUniqueId(), player.getName(), "CONTAINER", before, after);
    }

    private void record(UUID world, int x, int y, int z, UUID actor, String actorName,
                        String action, String before, String after) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        plugin.database().run(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + plugin.database().t("block_history")
                            + " (history_id,world_uuid,x,y,z,actor_uuid,actor_name,action,before_data,after_data,created_at)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, id);
                statement.setString(2, world.toString());
                statement.setInt(3, x); statement.setInt(4, y); statement.setInt(5, z);
                statement.setString(6, actor == null ? null : actor.toString());
                statement.setString(7, actorName);
                statement.setString(8, action);
                statement.setString(9, before);
                statement.setString(10, after);
                statement.setLong(11, now);
                statement.executeUpdate();
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !(player.hasPermission("Admin.Core") || player.hasPermission("Owner.Core"))) {
            Msg.err(sender, "Admin.Core required");
            return true;
        }
        if (args.length == 0) {
            Msg.info(player, "Usage: /blocklog preview <region> <minutes> [player] | /blocklog rollback confirm");
            return true;
        }
        if (args[0].equalsIgnoreCase("rollback") && args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
            confirm(player);
            return true;
        }
        if (!args[0].equalsIgnoreCase("preview") || args.length < 3 || args.length > 4) {
            Msg.info(player, "Usage: /blocklog preview <region> <minutes> [player] | /blocklog rollback confirm");
            return true;
        }
        RegionProtectionModule.RegionBounds bounds = regions.regionBounds(args[1]);
        if (bounds == null) {
            Msg.err(player, "Region not found");
            return true;
        }
        long minutes;
        try { minutes = Long.parseLong(args[2]); }
        catch (NumberFormatException invalid) { Msg.err(player, "Minutes must be a number"); return true; }
        if (minutes < 1 || minutes > 10080) {
            Msg.err(player, "Minutes must be between 1 and 10080");
            return true;
        }
        UUID actor = null;
        if (args.length == 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                Msg.err(player, "Player not found in server history");
                return true;
            }
            actor = target.getUniqueId();
        }
        final UUID actorFilter = actor;
        final long since = System.currentTimeMillis() - minutes * 60_000L;
        final int max = Math.max(1, Math.min(100000, plugin.getConfig().getInt("history.max-rollback-blocks", 20000)));
        Msg.info(player, "Searching block history for region {region}...", "region", bounds.id());
        plugin.database().query(connection -> {
            List<Change> changes = new ArrayList<>();
            String sql = "SELECT history_id,x,y,z,before_data,after_data,created_at FROM "
                    + plugin.database().t("block_history")
                    + " WHERE world_uuid=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? AND created_at>=?"
                    + (actorFilter == null ? "" : " AND actor_uuid=?")
                    + " ORDER BY created_at DESC LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i = 1;
                statement.setString(i++, bounds.world().toString());
                statement.setInt(i++, bounds.minX()); statement.setInt(i++, bounds.maxX());
                statement.setInt(i++, bounds.minY()); statement.setInt(i++, bounds.maxY());
                statement.setInt(i++, bounds.minZ()); statement.setInt(i++, bounds.maxZ());
                statement.setLong(i++, since);
                if (actorFilter != null) statement.setString(i++, actorFilter.toString());
                statement.setInt(i, max);
                try (ResultSet result = statement.executeQuery()) {
                    Set<String> seen = new HashSet<>();
                    while (result.next()) {
                        int x = result.getInt("x"), y = result.getInt("y"), z = result.getInt("z");
                        if (!seen.add(x + ":" + y + ":" + z)) continue;
                        changes.add(new Change(result.getString("history_id"), x, y, z,
                                result.getString("before_data"), result.getString("after_data"), result.getLong("created_at")));
                    }
                }
            }
            return changes;
        }).whenComplete((changes, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                plugin.getLogger().warning("Block history lookup failed: " + failure.getMessage());
                Msg.err(player, "Block history lookup failed. Check the server console.");
                return;
            }
            if (changes.isEmpty()) {
                Msg.info(player, "No recorded changes found for that region and time range.");
                return;
            }
            pending.put(player.getUniqueId(), new Pending(bounds.world(), bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ(), List.copyOf(changes),
                    System.currentTimeMillis() + 60_000L, bounds.id()));
            Msg.ok(player, "Preview: {count} unique blocks can be restored in region {region}.",
                    "count", changes.size(), "region", bounds.id());
            Msg.info(player, "Review the target region, then run /blocklog rollback confirm within 60 seconds.");
            if (changes.size() >= max) Msg.err(player, "The result reached the safety limit ({limit}); narrow the time range before rollback.", "limit", max);
        }));
        return true;
    }

    private void confirm(Player player) {
        Pending job = pending.remove(player.getUniqueId());
        if (job == null || job.expiresAt() < System.currentTimeMillis()) {
            Msg.err(player, "No active rollback preview. Run /blocklog preview first.");
            return;
        }
        World world = Bukkit.getWorld(job.world());
        if (world == null) {
            Msg.err(player, "The region world is not currently loaded.");
            return;
        }
        int max = Math.max(1, Math.min(100000, plugin.getConfig().getInt("history.max-rollback-blocks", 20000)));
        if (job.changes().size() > max) {
            Msg.err(player, "Rollback exceeds the configured safety limit.");
            return;
        }
        Msg.info(player, "Restoring {count} blocks in batches...", "count", job.changes().size());
        List<Change> changes = job.changes();
        final int[] index = {0};
        final int[] restored = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int processed = 0;
            while (index[0] < changes.size() && processed < 250) {
                Change change = changes.get(index[0]++);
                Block block = world.getBlockAt(change.x(), change.y(), change.z());
                String current = snapshot(block.getState());
                String coordinate = key(world.getUID(), change.x(), change.y(), change.z());
                restoringBlocks.add(coordinate);
                try {
                    restore(block, change.before());
                    record(world.getUID(), change.x(), change.y(), change.z(), player.getUniqueId(),
                            player.getName(), "ROLLBACK", current, change.before());
                    restored[0]++;
                } catch (Exception error) {
                    plugin.getLogger().warning("Could not restore block at " + change.x() + "," + change.y() + "," + change.z()
                            + ": " + error.getMessage());
                } finally { restoringBlocks.remove(coordinate); }
                processed++;
            }
            if (index[0] >= changes.size()) {
                task.cancel();
                Msg.ok(player, "Rollback finished: {count} blocks restored in region {region}.",
                        "count", restored[0], "region", job.regionId());
            }
        }, 1L, 1L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("preview", "rollback").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("rollback")) return List.of("confirm");
        return List.of();
    }
}
