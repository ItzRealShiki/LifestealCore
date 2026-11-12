package de.leon.lifesteal.gui;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuListener implements Listener {

    private static final long CLICK_DELAY_MS = 100;

    private final LifestealCore plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public MenuListener(LifestealCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public static Menu openMenu(Player player) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
        return holder instanceof Menu menu ? menu : null;
    }

    public static void open(Player player, Menu menu) {
        Bukkit.getScheduler().runTask(LifestealCore.get(), () -> {
            if (player.isOnline()) menu.open(player);
        });
    }

    public static void close(Player player) {
        Bukkit.getScheduler().runTask(LifestealCore.get(), () -> {
            if (openMenu(player) != null) player.closeInventory();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        if (!menu.allowsItems()) event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (!menu.allowsItems() && last != null && now - last < CLICK_DELAY_MS) return;
        lastClick.put(player.getUniqueId(), now);

        menu.onClick(player, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        if (event.getWhoClicked() instanceof Player player) {
            menu.onDrag(player, event);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu menu && event.getPlayer() instanceof Player player) {
            menu.onClose(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Menu m = openMenu(p);
            if (m != null && m.autoRefresh()) m.refresh();
        }
    }

    public static void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (openMenu(p) != null) p.closeInventory();
        }
    }
}
