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
    private final String categoryKey;
    private final int page;

    public ShopMenu(ShopManager shop) { this(shop, null, 0); }
    public ShopMenu(ShopManager shop, int page) { this(shop, null, page); }

    public ShopMenu(ShopManager shop, String categoryKey, int page) {
        this.shop = shop;
        this.categoryKey = categoryKey;
        int pages = pageCount(shop, categoryKey);
        this.page = Math.max(0, Math.min(page, pages - 1));
        create(INVENTORY_SIZE, shop.title(categoryKey == null ? "Shop Categories" : categoryName(shop, categoryKey) + " Shop"));
        render();
    }

    private static String categoryName(ShopManager shop, String key) {
        return shop.categories().values().stream().filter(c -> c.key().equals(key))
                .map(ShopManager.Category::name).findFirst().orElse("Shop");
    }

    private static int pageCount(ShopManager shop, String key) {
        if (key == null) return Math.max(1, (shop.categories().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        return Math.max(1, (shop.items(key).size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void render() {
        getInventory().clear();
        int start = page * PAGE_SIZE;
        if (categoryKey == null) {
            int index = 0;
            for (ShopManager.Category category : shop.categories().values()) {
                if (index >= start && index < start + PAGE_SIZE) {
                    set(index - start, shop.categoryDisplay(category));
                }
                index++;
            }
        } else {
            int index = 0;
            for (Map.Entry<Integer, ShopManager.ShopItem> entry : shop.items(categoryKey).entrySet()) {
                if (index >= start && index < start + PAGE_SIZE) set(index - start, display(entry.getValue()));
                index++;
            }
        }
        int pages = pageCount(shop, categoryKey);
        set(45, navigation(Material.ARROW, categoryKey == null ? "Previous page" : "Back to categories",
                categoryKey == null ? (page > 0 ? "go to previous page" : "first page") : "return to categories"));
        set(49, navigation(Material.PAPER, "Page " + (page + 1) + " / " + pages,
                categoryKey == null ? "Shop categories" : "Shop items"));
        set(50, navigation(Material.ARROW, "Next page", page + 1 < pages ? "go to next page" : "last page"));
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
            if (categoryKey != null) MenuListener.open(player, new ShopMenu(shop));
            else if (page > 0) MenuListener.open(player, new ShopMenu(shop, page - 1));
            return;
        }
        if (slot == 50) {
            if (page + 1 < pageCount(shop, categoryKey)) MenuListener.open(player, new ShopMenu(shop, categoryKey, page + 1));
            return;
        }
        if (slot >= PAGE_SIZE) return;

        if (categoryKey == null) {
            int index = page * PAGE_SIZE + slot;
            ShopManager.Category category = shop.categories().values().stream().skip(index).findFirst().orElse(null);
            if (category != null) MenuListener.open(player, new ShopMenu(shop, category.key(), 0));
            return;
        }

        ShopManager.ShopItem entry = shop.items(categoryKey).values().stream()
                .skip((long) page * PAGE_SIZE + slot).findFirst().orElse(null);
        if (entry != null) {
            int itemSlot = shop.items(categoryKey).entrySet().stream()
                    .skip((long) page * PAGE_SIZE + slot).map(Map.Entry::getKey).findFirst().orElse(-1);
            if (itemSlot >= 0) shop.buy(player, categoryKey, itemSlot);
        }
    }
}
