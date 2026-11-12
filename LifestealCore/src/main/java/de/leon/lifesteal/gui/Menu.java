package de.leon.lifesteal.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Base for every menu of the plugin. Each menu is its own InventoryHolder, so menus are
 * recognized safely (not by their title) and one listener handles all of them.
 */
public abstract class Menu implements InventoryHolder {

    private Inventory inventory;

    protected final void create(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    /** True if the click was inside this menu (not in the player's own inventory). */
    protected boolean isTop(InventoryClickEvent event) {
        return event.getRawSlot() >= 0 && event.getRawSlot() < inventory.getSize();
    }

    /** Players may put items into this menu (sell menu). Default: everything is locked. */
    public boolean allowsItems() {
        return false;
    }

    /**
     * Click inside the menu or in the player's own inventory while the menu is open.
     * For locked menus the event is already cancelled.
     */
    public abstract void onClick(Player player, InventoryClickEvent event);

    public void onDrag(Player player, InventoryDragEvent event) {
        if (!allowsItems()) event.setCancelled(true);
    }

    public void onClose(Player player) {
    }

    /** Redrawn every second while open (countdowns). */
    public boolean autoRefresh() {
        return false;
    }

    public void refresh() {
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
