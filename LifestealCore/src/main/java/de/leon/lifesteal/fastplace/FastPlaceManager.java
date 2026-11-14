package de.leon.lifesteal.fastplace;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Server bridge for the optional FastPlace Fabric client mod. */
public final class FastPlaceManager implements PluginMessageListener, Listener {
    public static final String HELLO_CHANNEL = "fastplace:hello";
    public static final String STATE_CHANNEL = "fastplace:state";
    public static final String FASTBREAK_STATE_CHANNEL = "fastplace:fastbreak_state";

    private final LifestealCore plugin;
    private final Set<UUID> moddedPlayers = new HashSet<>();
    private final File file;
    private final YamlConfiguration settings;
    private final BukkitTask permissionSyncTask;

    public FastPlaceManager(LifestealCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-settings.yml");
        this.settings = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, STATE_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, FASTBREAK_STATE_CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Re-check permission changes while a player stays online.
        this.permissionSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : Set.copyOf(moddedPlayers)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) sendFastBreakState(player);
            }
        }, 20L, 20L);
    }

    public boolean canUseSettings(Player player) {
        return player.hasPermission("Builder.Core") || player.hasPermission("Admin.Core");
    }

    public boolean isEnabled(Player player) {
        return settings.getBoolean(path(player, "fastplace"), false);
    }

    public boolean setEnabled(Player player, boolean enabled) {
        if (!canUseSettings(player)) return false;
        settings.set(path(player, "fastplace"), enabled);
        save();
        if (moddedPlayers.contains(player.getUniqueId())) sendState(player);
        return true;
    }

    public boolean isFastBreakConfigured(Player player) {
        return settings.getBoolean(path(player, "fastbreak"), false);
    }

    public boolean isFastBreakActive(Player player) {
        return canUseSettings(player)
                && player.getGameMode() == GameMode.CREATIVE
                && isFastBreakConfigured(player);
    }

    public boolean setFastBreakEnabled(Player player, boolean enabled) {
        if (!canUseSettings(player)) return false;
        settings.set(path(player, "fastbreak"), enabled);
        save();
        if (moddedPlayers.contains(player.getUniqueId())) sendFastBreakState(player);
        return true;
    }

    private String path(Player player, String setting) {
        return "players." + player.getUniqueId() + "." + setting;
    }

    private void save() {
        try {
            settings.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player settings", e);
        }
    }

    private void sendState(Player player) {
        player.sendPluginMessage(plugin, STATE_CHANNEL,
                new byte[]{(byte) (isEnabled(player) && canUseSettings(player) ? 1 : 0)});
        sendFastBreakState(player);
    }

    private void sendFastBreakState(Player player) {
        player.sendPluginMessage(plugin, FASTBREAK_STATE_CHANNEL,
                new byte[]{(byte) (isFastBreakActive(player) ? 1 : 0)});
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!HELLO_CHANNEL.equals(channel) || message.length != 0) return;
        moddedPlayers.add(player.getUniqueId());
        sendState(player);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!moddedPlayers.contains(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) sendFastBreakState(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        moddedPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        permissionSyncTask.cancel();
        moddedPlayers.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, STATE_CHANNEL);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, FASTBREAK_STATE_CHANNEL);
    }
}
