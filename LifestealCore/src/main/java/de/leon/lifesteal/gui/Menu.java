package de.leon.lifesteal.gui;

import net.kyori.adventure.text.Component;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class Menu implements InventoryHolder {

    private Inventory inventory;

    protected final void create(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, Msg.smallCaps(title));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    protected boolean isTop(InventoryClickEvent event) {
        return event.getRawSlot() >= 0 && event.getRawSlot() < inventory.getSize();
    }

    public boolean allowsItems() {
        return false;
    }

    public abstract void onClick(Player player, InventoryClickEvent event);

    public void onDrag(Player player, InventoryDragEvent event) {
        if (!allowsItems()) event.setCancelled(true);
    }

    public void onClose(Player player) {
    }

    public boolean autoRefresh() {
        return false;
    }

    public void refresh() {
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
