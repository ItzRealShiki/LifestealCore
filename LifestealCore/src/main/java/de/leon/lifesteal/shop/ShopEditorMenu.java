package de.leon.lifesteal.shop;

import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopEditorMenu extends Menu {
    private static final int PAGE_SIZE = 45;
    private static final int INVENTORY_SIZE = 54;

    private final ShopManager shop;
    private final String categoryKey;
    private final int page;

    public ShopEditorMenu(ShopManager shop) { this(shop, null, 0); }

    public ShopEditorMenu(ShopManager shop, String categoryKey, int page) {
        this.shop = shop;
        this.categoryKey = categoryKey;
        int pages = pageCount(shop, categoryKey);
        this.page = Math.max(0, Math.min(page, pages - 1));
        create(INVENTORY_SIZE, shop.title(categoryKey == null ? "Shop Categories Editor" : categoryName() + " Editor"));
        render();
    }

    private String categoryName() {
        return shop.categories().values().stream().filter(c -> c.key().equals(categoryKey))
                .map(ShopManager.Category::name).findFirst().orElse("Shop");
    }

    private static int pageCount(ShopManager shop, String key) {
        int size = key == null ? shop.categories().size() : shop.items(key).size();
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void render() {
        getInventory().clear();
        int start = page * PAGE_SIZE;
        if (categoryKey == null) {
            List<ShopManager.Category> entries = new ArrayList<>(shop.categories().values());
            for (int i = start; i < Math.min(entries.size(), start + PAGE_SIZE); i++) {
                set(i - start, shop.categoryDisplay(entries.get(i)));
            }
        } else {
            List<Map.Entry<Integer, ShopManager.ShopItem>> entries = new ArrayList<>(shop.items(categoryKey).entrySet());
            for (int i = start; i < Math.min(entries.size(), start + PAGE_SIZE); i++) {
                set(i - start, editorDisplay(entries.get(i).getValue()));
            }
        }

        int pages = pageCount(shop, categoryKey);
        set(45, nav(Material.ARROW, categoryKey == null ? "Previous page" : "Back to categories",
                categoryKey == null ? (page > 0 ? "go to previous page" : "first page") : "return to categories"));
        set(49, nav(Material.PAPER, "Page " + (page + 1) + " / " + pages,
                categoryKey == null ? "Shift-click an inventory item to add a category" : "Shift-click an inventory item to add a shop item"));
        set(50, nav(Material.ARROW, "Next page", page + 1 < pages ? "go to next page" : "last page"));
    }

    private ItemStack editorDisplay(ShopManager.ShopItem value) {
        ItemStack item = value.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            List<Component> old = meta.lore();
            if (old != null) lore.addAll(old);
            lore.add(Component.empty());
            lore.add(Msg.item("Stack: {n}", NamedTextColor.GRAY, "n", item.getAmount()));
            lore.add(Msg.item("Money price: {n}", NamedTextColor.GRAY, "n", Num.format(value.moneyPrice())));
            lore.add(Msg.item("Gems price: {n}", NamedTextColor.GRAY, "n", Num.format(value.gemsPrice())));
            if (value.moneyPrice() <= 0 && value.gemsPrice() <= 0) lore.add(Msg.item("Price not set", Msg.BAD));
            lore.add(Msg.item("Left-click: change price", Msg.GOOD));
            lore.add(Msg.item("Right-click: remove item", Msg.BAD));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack nav(Material material, String name, String loreText) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(Msg.sc(name), NamedTextColor.WHITE));
        meta.lore(List.of(Component.text(Msg.sc(loreText), NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!player.hasPermission(EcoCommands.ADMIN)) {
            player.closeInventory();
            return;
        }

        boolean top = isTop(event);
        ItemStack current = event.getCurrentItem();
        if (!top) {
            if (event.isShiftClick() && current != null && !current.getType().isAir()) {
                if (categoryKey == null) {
                    int slot = shop.nextCategorySlot();
                    shop.addCategory(slot, current);
                    event.setCurrentItem(null);
                    Msg.ok(player, "Category {name} created", "name", ShopManager.itemName(current));
                    MenuListener.open(player, new ShopEditorMenu(shop, null, Math.max(0, (shop.categories().size() - 1) / PAGE_SIZE)));
                } else {
                    int slot = shop.nextItemSlot(categoryKey);
                    shop.setItem(categoryKey, slot, current, 0, 0);
                    event.setCurrentItem(null);
                    Msg.ok(player, "Item added. Left-click it to set the price");
                    MenuListener.open(player, new ShopEditorMenu(shop, categoryKey,
                            Math.max(0, (shop.items(categoryKey).size() - 1) / PAGE_SIZE)));
                }
                shop.plugin().sound(player, "success");
            }
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 45) {
            if (categoryKey != null) MenuListener.open(player, new ShopEditorMenu(shop));
            else if (page > 0) MenuListener.open(player, new ShopEditorMenu(shop, null, page - 1));
            return;
        }
        if (slot == 50) {
            if (page + 1 < pageCount(shop, categoryKey)) {
                MenuListener.open(player, new ShopEditorMenu(shop, categoryKey, page + 1));
            }
            return;
        }
        if (slot >= PAGE_SIZE) return;

        ItemStack cursor = player.getItemOnCursor();
        if (categoryKey == null) {
            List<ShopManager.Category> entries = new ArrayList<>(shop.categories().values());
            int index = page * PAGE_SIZE + slot;
            if (index < entries.size()) {
                ShopManager.Category category = entries.get(index);
                MenuListener.open(player, new ShopEditorMenu(shop, category.key(), 0));
            } else if (cursor != null && !cursor.getType().isAir()) {
                int newSlot = shop.nextCategorySlot();
                shop.addCategory(newSlot, cursor);
                player.setItemOnCursor(null);
                Msg.ok(player, "Category {name} created", "name", ShopManager.itemName(cursor));
                shop.plugin().sound(player, "success");
                MenuListener.open(player, new ShopEditorMenu(shop, null,
                        Math.max(0, (shop.categories().size() - 1) / PAGE_SIZE)));
            }
            return;
        }

        List<Map.Entry<Integer, ShopManager.ShopItem>> entries = new ArrayList<>(shop.items(categoryKey).entrySet());
        int index = page * PAGE_SIZE + slot;
        if (index >= entries.size()) {
            if (cursor != null && !cursor.getType().isAir()) {
                int newSlot = shop.nextItemSlot(categoryKey);
                shop.setItem(categoryKey, newSlot, cursor, 0, 0);
                player.setItemOnCursor(null);
                Msg.ok(player, "Item added. Left-click it to set the price");
                shop.plugin().sound(player, "success");
                MenuListener.open(player, new ShopEditorMenu(shop, categoryKey,
                        Math.max(0, (shop.items(categoryKey).size() - 1) / PAGE_SIZE)));
            }
            return;
        }

        Map.Entry<Integer, ShopManager.ShopItem> selected = entries.get(index);
        int itemSlot = selected.getKey();
        if (event.getClick() == ClickType.RIGHT) {
            shop.removeItem(categoryKey, itemSlot);
            Msg.err(player, "The item has been removed from the shop");
            shop.plugin().sound(player, "success");
            MenuListener.open(player, new ShopEditorMenu(shop, categoryKey, page));
            return;
        }
        if (event.getClick() != ClickType.LEFT) return;

        player.closeInventory();
        Msg.info(player, "Type the new price in chat, e.g. '500 money', '10 gems' or '500 money 10 gems' ({c} to cancel)",
                "c", shop.plugin().chatInput().cancelWord());
        shop.plugin().chatInput().request(player, input -> setPrice(player, itemSlot, input));
    }

    private void setPrice(Player player, int slot, String input) {
        ShopManager.ShopItem entry = shop.items(categoryKey).get(slot);
        if (entry == null) {
            Msg.err(player, "This shop item does not exist anymore");
            return;
        }
        double money = 0;
        double gems = 0;
        String[] parts = input.trim().split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            double value = Num.parse(parts[i]);
            if (value <= 0) continue;
            String type = i + 1 < parts.length ? parts[i + 1].toLowerCase(java.util.Locale.ROOT) : "money";
            if (type.startsWith("gem")) gems = value;
            else money = value;
        }
        if (money <= 0 && gems <= 0) {
            Msg.err(player, "Invalid price. Example: 500 money 10 gems");
            shop.plugin().sound(player, "error");
            return;
        }
        shop.setItem(categoryKey, slot, entry.item(), money, gems);
        Msg.ok(player, "Price updated: {p}", "p", ShopManager.priceText(money, gems));
        shop.plugin().sound(player, "success");
        MenuListener.open(player, new ShopEditorMenu(shop, categoryKey, page));
    }
}
