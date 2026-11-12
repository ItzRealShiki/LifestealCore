package de.leon.lifesteal.shop;

import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SellMenu extends Menu {

    private final ShopManager shop;
    private final Map<Integer, Material> displayed = new HashMap<>();

    public SellMenu(ShopManager shop) {
        this.shop = shop;
        create(54, shop.title("Sell"));
        render();
    }

    private void render() {
        getInventory().clear();
        displayed.clear();

        List<Map.Entry<Material, ShopManager.SellPrice>> entries =
                new ArrayList<>(shop.sellPrices().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().name()));

        int slot = 0;
        for (Map.Entry<Material, ShopManager.SellPrice> entry : entries) {
            if (slot >= 45) break;
            Material material = entry.getKey();
            ShopManager.SellPrice price = entry.getValue();
            ItemStack icon = new ItemStack(material);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(Msg.sc(material.name().replace('_', ' ')), NamedTextColor.YELLOW));
            meta.lore(List.of(
                    Msg.item("Price: {price} {currency}", Msg.NEUTRAL,
                            "price", Num.format(price.price()), "currency", price.currency().display()),
                    Msg.item("Click to sell all of this item", Msg.GOOD)
            ));
            icon.setItemMeta(meta);
            set(slot, icon);
            displayed.put(slot, material);
            slot++;
        }

        ItemStack sellAll = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta meta = sellAll.getItemMeta();
        meta.displayName(Msg.item("Sell All", Msg.GOOD));
        meta.lore(List.of(Msg.item("Sell all eligible items in your inventory", Msg.NEUTRAL)));
        sellAll.setItemMeta(meta);
        set(49, sellAll);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        int slot = event.getRawSlot();
        if (slot == 49) {
            sellAll(player);
            return;
        }
        Material material = displayed.get(slot);
        if (material != null) sellMaterial(player, material);
    }

    private void sellMaterial(Player player, Material material) {
        ShopManager.SellPrice price = shop.sellPrice(material);
        if (price == null) {
            render();
            return;
        }
        int amount = removeMatching(player, material);
        if (amount <= 0) {
            Msg.err(player, "You don't have any sellable {item}", "item",
                    material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
            shop.plugin().sound(player, "error");
            return;
        }

        double total = price.price() * amount;
        if (shop.plugin().getConfig().getBoolean("boosters.boost-sell", true)) {
            total *= shop.eco().multiplier(player, price.currency());
        }
        if (Double.isFinite(total) && total > 0) {
            shop.eco().deposit(player.getUniqueId(), price.currency(), total, "SHOP_SELL", material.name());
            if (shop.plugin().adminManager() != null)
                shop.plugin().adminManager().logSell(player, material, amount, price.currency(), total);
            Msg.ok(player, "Sold {amount}x {item} for {price} {currency}",
                    "amount", amount, "item", material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '),
                    "price", Num.format(total), "currency", price.currency().display());
            shop.plugin().sound(player, "sell");
        }
        render();
    }

    private void sellAll(Player player) {
        double money = 0;
        double gems = 0;
        int sold = 0;
        Map<Material, Integer> soldItems = new HashMap<>();

        for (Map.Entry<Material, ShopManager.SellPrice> entry : shop.sellPrices().entrySet()) {
            int amount = removeMatching(player, entry.getKey());
            if (amount <= 0) continue;
            sold += amount;
            soldItems.put(entry.getKey(), amount);
            double total = entry.getValue().price() * amount;
            if (entry.getValue().currency() == Currency.GEMS) gems += total;
            else money += total;
        }

        if (sold == 0) {
            Msg.err(player, "You don't have any sellable items");
            shop.plugin().sound(player, "error");
            return;
        }

        if (shop.plugin().getConfig().getBoolean("boosters.boost-sell", true)) {
            money *= shop.eco().multiplier(player, Currency.MONEY);
            gems *= shop.eco().multiplier(player, Currency.GEMS);
        }
        if (money > 0 && Double.isFinite(money)) {
            shop.eco().deposit(player.getUniqueId(), Currency.MONEY, money, "SHOP_SELL", "Sell All");
        }
        if (gems > 0 && Double.isFinite(gems)) {
            shop.eco().deposit(player.getUniqueId(), Currency.GEMS, gems, "SHOP_SELL", "Sell All");
        }
        if (shop.plugin().adminManager() != null) {
            for (Map.Entry<Material, Integer> soldItem : soldItems.entrySet()) {
                ShopManager.SellPrice price = shop.sellPrice(soldItem.getKey());
                if (price == null) continue;
                double itemTotal = price.price() * soldItem.getValue();
                if (shop.plugin().getConfig().getBoolean("boosters.boost-sell", true))
                    itemTotal *= shop.eco().multiplier(player, price.currency());
                shop.plugin().adminManager().logSell(player, soldItem.getKey(), soldItem.getValue(), price.currency(), itemTotal);
            }
        }
        Msg.ok(player, "Sold {amount} items for {money} money and {gems} gems",
                "amount", sold, "money", Num.format(money), "gems", Num.format(gems));
        shop.plugin().sound(player, "sell");
        render();
    }

    private int removeMatching(Player player, Material material) {
        int amount = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material || !isPlainItem(stack)) continue;
            amount += stack.getAmount();
            contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
        return amount;
    }

    private boolean isPlainItem(ItemStack stack) {
        if (!stack.hasItemMeta()) return true;
        ItemMeta meta = stack.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && !meta.hasEnchants()
                && !meta.hasCustomModelData()
                && meta.getPersistentDataContainer().getKeys().isEmpty();
    }
}
