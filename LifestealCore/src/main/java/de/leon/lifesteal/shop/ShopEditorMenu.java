package de.leon.lifesteal.shop;

import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopEditorMenu extends Menu {

    private final ShopManager shop;

    public ShopEditorMenu(ShopManager shop) {
        this.shop = shop;
        create(shop.size(), shop.title("Shop Editor"));
        render();
    }

    private void render() {
        getInventory().clear();
        for (Map.Entry<Integer, ShopManager.ShopItem> e : shop.items().entrySet()) {
            if (e.getKey() >= shop.size()) continue;
            ShopManager.ShopItem entry = e.getValue();
            ItemStack item = entry.item().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                List<Component> old = meta.lore();
                if (old != null) lore.addAll(old);
                lore.add(Component.empty());
                lore.add(Msg.item("Stack: {n}", NamedTextColor.GRAY, "n", item.getAmount()));
                lore.add(Msg.item("Money price: {n}", NamedTextColor.GRAY, "n", Num.format(entry.moneyPrice())));
                lore.add(Msg.item("Gems price: {n}", NamedTextColor.GRAY, "n", Num.format(entry.gemsPrice())));
                if (entry.moneyPrice() <= 0 && entry.gemsPrice() <= 0) lore.add(Msg.item("Price not set", Msg.BAD));
                lore.add(Msg.item("Left-click: change price", Msg.GOOD));
                lore.add(Msg.item("Right-click: remove item", Msg.BAD));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            set(e.getKey(), item);
        }
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
                int free = -1;
                for (int i = 0; i < shop.size(); i++) {
                    if (!shop.items().containsKey(i)) {
                        free = i;
                        break;
                    }
                }
                if (free == -1) {
                    Msg.err(player, "There is no free slot left in the shop");
                    return;
                }
                shop.setItem(free, current, 0, 0);
                event.setCurrentItem(null);
                added(player);
            }
            return;
        }

        int slot = event.getRawSlot();
        ShopManager.ShopItem existing = shop.items().get(slot);
        if (event.getClick() == ClickType.RIGHT) {
            if (existing != null) {
                shop.removeItem(slot);
                Msg.err(player, "The item has been removed from the shop");
                shop.plugin().sound(player, "success");
                render();
            }
            return;
        }
        if (event.getClick() != ClickType.LEFT) return;

        ItemStack cursor = player.getItemOnCursor();
        if (existing == null && !cursor.getType().isAir()) {
            shop.setItem(slot, cursor, 0, 0);
            player.setItemOnCursor(null);
            added(player);
        } else if (existing != null) {
            player.closeInventory();
            Msg.info(player, "Type the new price in chat, e.g. '500 money', '10 gems' or '500 money 10 gems' ({c} to cancel)",
                    "c", shop.plugin().chatInput().cancelWord());
            shop.plugin().chatInput().request(player, input -> setPrice(player, slot, input));
        }
    }

    private void added(Player player) {
        Msg.ok(player, "Item added. Left-click it to set the price");
        shop.plugin().sound(player, "success");
        render();
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
        MenuListener.open(player, new ShopEditorMenu(shop));
    }
}
