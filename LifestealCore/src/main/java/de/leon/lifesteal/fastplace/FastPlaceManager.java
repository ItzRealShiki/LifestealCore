package de.leon.lifesteal.fastplace;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Server bridge for FastPlace and native server-side FastBreak settings. */
public final class FastPlaceManager implements PluginMessageListener, Listener {
    public static final String HELLO_CHANNEL = "fastplace:hello";
    public static final String STATE_CHANNEL = "fastplace:state";
    private static final String BUILDER = "Builder.Core";

    private final LifestealCore plugin;
    private final Set<UUID> moddedPlayers = new HashSet<>();
    private final File file;
    private final YamlConfiguration settings;
    private final NamespacedKey fastBreakKey;
    private final BukkitTask fastBreakTask;

    public FastPlaceManager(LifestealCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-settings.yml");
        this.settings = YamlConfiguration.loadConfiguration(file);
        this.fastBreakKey = new NamespacedKey(plugin, "builder_fastbreak");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, STATE_CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.fastBreakTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) refreshFastBreak(player);
        }, 20L, 40L);
    }

    public boolean isEnabled(Player player) {
        return settings.getBoolean("players." + player.getUniqueId() + ".fastplace", false);
    }

    public void setEnabled(Player player, boolean enabled) {
        settings.set("players." + player.getUniqueId() + ".fastplace", enabled);
        save();
        if (moddedPlayers.contains(player.getUniqueId())) sendState(player);
    }

    public boolean isFastBreakEnabled(Player player) {
        return settings.getBoolean("players." + player.getUniqueId() + ".fastbreak", false);
    }

    public void setFastBreakEnabled(Player player, boolean enabled) {
        settings.set("players." + player.getUniqueId() + ".fastbreak", enabled);
        save();
        refreshFastBreak(player);
    }

    private void refreshFastBreak(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(fastBreakKey);
        if (!isFastBreakEnabled(player) || !player.hasPermission(BUILDER)) return;
        double multiplier = Math.max(1.0, Math.min(10.0, plugin.getConfig().getDouble("fastbreak.multiplier", 5.0)));
        if (multiplier <= 1.0) return;
        attribute.addModifier(new AttributeModifier(fastBreakKey, multiplier - 1.0, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void save() {
        try {
            settings.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player settings", e);
        }
    }

    private void sendState(Player player) {
        player.sendPluginMessage(plugin, STATE_CHANNEL, new byte[]{(byte) (isEnabled(player) ? 1 : 0)});
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!HELLO_CHANNEL.equals(channel) || message.length != 0) return;
        moddedPlayers.add(player.getUniqueId());
        sendState(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshFastBreak(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        moddedPlayers.remove(event.getPlayer().getUniqueId());
        AttributeInstance attribute = event.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute != null) attribute.removeModifier(fastBreakKey);
    }

    public void shutdown() {
        fastBreakTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
            if (attribute != null) attribute.removeModifier(fastBreakKey);
        }
        moddedPlayers.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, STATE_CHANNEL);
    }
}
