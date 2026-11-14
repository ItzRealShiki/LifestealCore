package de.leon.lifesteal.spawn;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnManager implements Listener, TabExecutor {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final LifestealCore plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SpawnManager(LifestealCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(joinLeaveMessage(event.getPlayer(), " joined the server", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(joinLeaveMessage(event.getPlayer(), " left the server", NamedTextColor.RED));
    }

    private Component joinLeaveMessage(Player player, String suffix) {
        return prefix(player).append(Component.text(Msg.sc(player.getName() + suffix), NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component prefix(Player player) {
        try {
            var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider == null) return Component.empty();
            String value = provider.getProvider().getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
            if (value == null || value.isBlank()) return Component.empty();
            return LEGACY.deserialize(value.replace('§', '&')).decoration(TextDecoration.ITALIC, false);
        } catch (RuntimeException ignored) {
            return Component.empty();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!sender.hasPermission("Admin.Core")) {
                Msg.err(sender, "You do not have permission to do this");
                return true;
            }
            if (!(sender instanceof Player player)) {
                Msg.err(sender, "Only players can set the spawn");
                return true;
            }
            Location location = player.getLocation();
            FileConfiguration config = plugin.getConfig();
            config.set("spawn.world", location.getWorld().getName());
            config.set("spawn.x", location.getX());
            config.set("spawn.y", location.getY());
            config.set("spawn.z", location.getZ());
            config.set("spawn.yaw", location.getYaw());
            config.set("spawn.pitch", location.getPitch());
            plugin.saveConfig();
            Msg.ok(sender, "Spawn location has been saved");
            return true;
        }

        if (args.length > 1) {
            Msg.info(sender, "Usage: /spawn [player]");
            return true;
        }
        Location spawn = getSpawn();
        if (spawn == null) {
            Msg.err(sender, "Spawn has not been set yet");
            return true;
        }
        if (args.length == 1) {
            if (!sender.hasPermission("Admin.Core")) {
                Msg.err(sender, "You do not have permission to do this");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Msg.err(sender, "Player {p} is not online", "p", args[0]);
                return true;
            }
            target.teleportAsync(spawn).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) Msg.ok(sender, "Teleported {p} to spawn", "p", target.getName());
                else Msg.err(sender, "Could not teleport {p}", "p", target.getName());
            }));
            return true;
        }
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can use /spawn without a target");
            return true;
        }
        long now = System.currentTimeMillis();
        long next = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < next) {
            Msg.err(sender, "Please wait {s}s before using spawn again", "s", String.format(Locale.ROOT, "%.1f", (next - now) / 1000.0));
            return true;
        }
        cooldowns.put(player.getUniqueId(), now + 5000L);
        player.teleportAsync(spawn).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) Msg.ok(player, "You have been teleported to spawn");
            else {
                cooldowns.remove(player.getUniqueId());
                Msg.err(player, "Could not teleport you to spawn");
            }
        }));
        return true;
    }

    private Location getSpawn() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("spawn.world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, config.getDouble("spawn.x"), config.getDouble("spawn.y"),
                config.getDouble("spawn.z"), (float) config.getDouble("spawn.yaw"),
                (float) config.getDouble("spawn.pitch"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("spawn") && args.length == 1 && sender.hasPermission("Admin.Core")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        return List.of();
    }
}
