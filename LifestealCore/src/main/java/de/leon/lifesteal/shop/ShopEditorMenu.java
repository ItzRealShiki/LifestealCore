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
    private final int page;

    public ShopEditorMenu(ShopManager shop) { this(shop, 0); }

    public ShopEditorMenu(ShopManager shop, int page) {
        this.shop = shop;
        this.page = Math.max(0, Math.min(page, pageCount(shop) - 1));
        create(INVENTORY_SIZE, shop.title("Shop Editor"));
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
            if (slot < 0 || slot >= PAGE_SIZE) continue;
            ShopManager.ShopItem value = entry.getValue();
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
            set(slot, item);
        }
        int pages = pageCount(shop);
        set(45, nav(Material.ARROW, "Previous page", page > 0 ? "go to previous page" : "first page"));
        set(49, nav(Material.PAPER, "Page " + (page + 1) + " / " + pages, "Shift-click an inventory item to add it"));
        set(50, nav(Material.ARROW, "Next page", page + 1 < pages ? "go to next page" : "last page"));
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
        ItemStack current = event.getCurrentItem();
        boolean top = isTop(event);

        if (!top) {
            if (event.isShiftClick() && current != null && !current.getType().isAir()) {
                int free = 0;
                while (shop.items().containsKey(free)) free++;
                shop.setItem(free, current, 0, 0);
                event.setCurrentItem(null);
                added(player, free / PAGE_SIZE);
            }
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 45) {
            if (page > 0) MenuListener.open(player, new ShopEditorMenu(shop, page - 1));
            return;
        }
        if (slot == 50) {
            if (page + 1 < pageCount(shop)) MenuListener.open(player, new ShopEditorMenu(shop, page + 1));
            return;
        }
        if (slot >= PAGE_SIZE) return;

        int globalSlot = page * PAGE_SIZE + slot;
        ShopManager.ShopItem existing = shop.items().get(globalSlot);
        if (event.getClick() == ClickType.RIGHT) {
            if (existing != null) {
                shop.removeItem(globalSlot);
                Msg.err(player, "The item has been removed from the shop");
                shop.plugin().sound(player, "success");
                render();
            }
            return;
        }
        if (event.getClick() != ClickType.LEFT) return;

        ItemStack cursor = player.getItemOnCursor();
        if (existing == null && cursor != null && !cursor.getType().isAir()) {
            shop.setItem(globalSlot, cursor, 0, 0);
            player.setItemOnCursor(null);
            added(player, page);
        } else if (existing != null) {
            player.closeInventory();
            Msg.info(player, "Type the new price in chat, e.g. '500 money', '10 gems' or '500 money 10 gems' ({c} to cancel)",
                    "c", shop.plugin().chatInput().cancelWord());
            shop.plugin().chatInput().request(player, input -> setPrice(player, globalSlot, input));
        }
    }

    private void added(Player player, int targetPage) {
        Msg.ok(player, "Item added. Left-click it to set the price");
        shop.plugin().sound(player, "success");
        MenuListener.open(player, new ShopEditorMenu(shop, targetPage));
    }

    private void setPrice(Player player, int slot, String input) {
        ShopManager.ShopItem entry = shop.items().get(slot);
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
        shop.setItem(slot, entry.item(), money, gems);
        Msg.ok(player, "Price updated: {p}", "p", ShopManager.priceText(money, gems));
        shop.plugin().sound(player, "success");
        MenuListener.open(player, new ShopEditorMenu(shop, slot / PAGE_SIZE));
    }
}
