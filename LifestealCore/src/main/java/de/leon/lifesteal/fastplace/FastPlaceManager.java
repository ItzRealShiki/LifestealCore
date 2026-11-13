package de.leon.lifesteal.fastplace;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative bridge for the FastPlace Fabric client mod.
 *
 * Protocol:
 * - fastplace:hello (client -> server): empty payload
 * - fastplace:state (server -> client): one byte, 0 = disabled, 1 = enabled
 */
public final class FastPlaceManager implements PluginMessageListener, Listener {

    public static final String HELLO_CHANNEL = "fastplace:hello";
    public static final String STATE_CHANNEL = "fastplace:state";

    private final LifestealCore plugin;
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private final Set<UUID> moddedPlayers = new HashSet<>();

    public FastPlaceManager(LifestealCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, STATE_CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    public void setEnabled(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        if (enabled) {
            enabledPlayers.add(uuid);
        } else {
            enabledPlayers.remove(uuid);
        }

        if (moddedPlayers.contains(uuid)) {
            sendState(player);
        }
    }

    private void sendState(Player player) {
        byte value = (byte) (isEnabled(player) ? 1 : 0);
        player.sendPluginMessage(plugin, STATE_CHANNEL, new byte[]{value});
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!HELLO_CHANNEL.equals(channel) || message.length != 0) {
            return;
        }

        moddedPlayers.add(player.getUniqueId());
        sendState(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        enabledPlayers.remove(uuid);
        moddedPlayers.remove(uuid);
    }

    public void shutdown() {
        enabledPlayers.clear();
        moddedPlayers.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, STATE_CHANNEL);
    }
}
