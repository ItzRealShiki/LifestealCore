package de.leon.lifesteal.gui;

import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class Items {

    private Items() {
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material m = Material.matchMaterial(name.trim());
        return (m == null || m.isAir() || !m.isItem()) ? fallback : m;
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack of(Material material, String name, TextColor color, String... lore) {
        List<Component> lines = new java.util.ArrayList<>();
        for (String l : lore) lines.add(Msg.item(l, net.kyori.adventure.text.format.NamedTextColor.GRAY));
        return of(material, Msg.item(name, color), lines);
    }

    public static ItemStack filler(Material material) {
        return of(material, Component.text(" "), List.of());
    }

    public static ItemStack head(OfflinePlayer owner, Component name, List<Component> lore) {
        ItemStack item = of(Material.PLAYER_HEAD, name, lore);
        if (owner != null && item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            item.setItemMeta(skull);
        }
        return item;
    }
}
