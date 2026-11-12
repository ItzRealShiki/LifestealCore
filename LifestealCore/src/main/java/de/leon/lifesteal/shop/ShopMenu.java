package de.leon.lifesteal.shop;

import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopMenu extends Menu {

    private final ShopManager shop;

    public ShopMenu(ShopManager shop) {
        this.shop = shop;
        create(shop.size(), shop.title("Shop"));
        for (Map.Entry<Integer, ShopManager.ShopItem> e : shop.items().entrySet()) {
            if (e.getKey() >= shop.size()) continue;
            set(e.getKey(), display(e.getValue()));
        }
    }

    private static ItemStack display(ShopManager.ShopItem entry) {
        ItemStack item = entry.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            List<Component> old = meta.lore();
            if (old != null) lore.addAll(old);
            if (!lore.isEmpty()) lore.add(Component.empty());
            if (entry.moneyPrice() > 0) {
                lore.add(Msg.item("Price: {p}", NamedTextColor.GRAY, "p",
                        Component.text(de.leon.lifesteal.util.Num.format(entry.moneyPrice()) + " " + Msg.sc("money"), NamedTextColor.YELLOW)));
            }
            if (entry.gemsPrice() > 0) {
                lore.add(Msg.item("Price: {p}", NamedTextColor.GRAY, "p",
                        Component.text(de.leon.lifesteal.util.Num.format(entry.gemsPrice()) + " " + Msg.sc("gems"), NamedTextColor.AQUA)));
            }
            lore.add(Msg.item("Click to buy", Msg.GOOD));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        shop.buy(player, event.getRawSlot());
    }
}
