package de.leon.lifesteal.gui;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInput implements Listener {

    private record Pending(Consumer<String> callback, BukkitTask timeout) {
    }

    private final LifestealCore plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public String cancelWord() {
        return plugin.getConfig().getString("chat-input.cancel-word", "cancel");
    }

    public void request(Player player, Consumer<String> callback) {
        UUID id = player.getUniqueId();
        cancel(id);
        long seconds = Math.max(5, plugin.getConfig().getLong("chat-input.timeout-seconds", 30));
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(id) != null && player.isOnline()) {
                Msg.err(player, "You ran out of time to type something");
            }
        }, seconds * 20L);
        pending.put(id, new Pending(callback, timeout));
    }

    public void cancel(UUID id) {
        Pending p = pending.remove(id);
        if (p != null) p.timeout().cancel();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String cancelWord = cancelWord();

        Bukkit.getScheduler().runTask(plugin, () -> {
            p.timeout().cancel();
            if (!player.isOnline()) return;
            if (message.equalsIgnoreCase(cancelWord) || message.equalsIgnoreCase("cancel")) {
                Msg.err(player, "Input cancelled");
                return;
            }
            p.callback().accept(message);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }
}
