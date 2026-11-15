package de.leon.lifesteal.shop;

import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopMenu extends Menu {
    private static final int PAGE_SIZE = 45;
    private static final int INVENTORY_SIZE = 54;
    private final ShopManager shop;
    private final int page;

    public ShopMenu(ShopManager shop) { this(shop, 0); }

    public ShopMenu(ShopManager shop, int page) {
        this.shop = shop;
        this.page = Math.max(0, Math.min(page, pageCount(shop) - 1));
        create(INVENTORY_SIZE, shop.title("Shop"));
        render();
    }

    private static int pageCount(ShopManager shop) {
        int max = shop.items().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        return Math.max(1, max / PAGE_SIZE + 1);
    }

    private void render() {
        getInventory().clear();
        int start = page * PAGE_SIZE;
        for (Map.Entry<Integer, ShopManager.ShopItem> entry : shop.items().entrySet()) {
            int slot = entry.getKey() - start;
            if (slot >= 0 && slot < PAGE_SIZE) set(slot, display(entry.getValue()));
        }
        int pages = pageCount(shop);
        set(45, navigation(Material.ARROW, "Previous page", page > 0 ? "click to go back" : "first page"));
        set(49, navigation(Material.PAPER, "Page " + (page + 1) + " / " + pages, "Shop items"));
        set(50, navigation(Material.ARROW, "Next page", page + 1 < pages ? "click to continue" : "last page"));
    }

    private static ItemStack display(ShopManager.ShopItem entry) {
        ItemStack item = entry.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            List<Component> old = meta.lore();
            if (old != null) lore.addAll(old);
            if (!lore.isEmpty()) lore.add(Component.empty());
            if (entry.moneyPrice() > 0) lore.add(Msg.item("Price: {p}", NamedTextColor.GRAY, "p",
                    Component.text(de.leon.lifesteal.util.Num.format(entry.moneyPrice()) + " " + Msg.sc("money"), NamedTextColor.YELLOW)));
            if (entry.gemsPrice() > 0) lore.add(Msg.item("Price: {p}", NamedTextColor.GRAY, "p",
                    Component.text(de.leon.lifesteal.util.Num.format(entry.gemsPrice()) + " " + Msg.sc("gems"), NamedTextColor.AQUA)));
            lore.add(Msg.item("Click to buy", Msg.GOOD));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack navigation(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(Msg.sc(name), NamedTextColor.WHITE));
        meta.lore(List.of(Component.text(Msg.sc(description), NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        int slot = event.getRawSlot();
        if (slot == 45) {
            if (page > 0) MenuListener.open(player, new ShopMenu(shop, page - 1));
            return;
        }
        if (slot == 50) {
            if (page + 1 < pageCount(shop)) MenuListener.open(player, new ShopMenu(shop, page + 1));
            return;
        }
        if (slot >= PAGE_SIZE) return;
        shop.buy(player, page * PAGE_SIZE + slot);
    }
}
