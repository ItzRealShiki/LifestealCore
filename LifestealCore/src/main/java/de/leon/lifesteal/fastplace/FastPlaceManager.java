package de.leon.lifesteal.fastplace;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

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

    private final LifestealCore plugin;
    private final Set<UUID> moddedPlayers = new HashSet<>();
    private final File file;
    private final YamlConfiguration settings;

    public FastPlaceManager(LifestealCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-settings.yml");
        this.settings = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, STATE_CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isEnabled(Player player) {
        return settings.getBoolean("players." + player.getUniqueId() + ".fastplace", false);
    }

    public void setEnabled(Player player, boolean enabled) {
        settings.set("players." + player.getUniqueId() + ".fastplace", enabled);
        save();
        if (moddedPlayers.contains(player.getUniqueId())) sendState(player);
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
    public void onQuit(PlayerQuitEvent event) {
        moddedPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        moddedPlayers.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, STATE_CHANNEL);
    }
}
