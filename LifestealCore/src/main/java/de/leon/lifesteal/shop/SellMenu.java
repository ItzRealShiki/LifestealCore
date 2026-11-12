package de.leon.lifesteal.shop;

import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** /sell: put items in, close the menu, get paid. Items without a price are given back. */
public final class SellMenu extends Menu {

    private final ShopManager shop;
    private boolean done;

    public SellMenu(ShopManager shop) {
        this.shop = shop;
        create(27, shop.title("Sell"));
    }

    @Override
    public boolean allowsItems() {
        return true;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        // items may be moved freely
    }

    @Override
    public void onClose(Player player) {
        if (done) return;
        done = true;
        double money = 0;
        double gems = 0;
        for (int i = 0; i < getInventory().getSize(); i++) {
            ItemStack item = getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;
            ShopManager.SellPrice price = shop.sellPrice(item.getType());
            if (price != null) {
                double total = price.price() * item.getAmount();
                if (price.currency() == Currency.GEMS) gems += total;
                else money += total;
            } else {
                ShopManager.give(player, item);
            }
        }
        getInventory().clear();

        if (money <= 0 && gems <= 0) {
            Msg.err(player, "No sellable items found");
            return;
        }
        if (shop.plugin().getConfig().getBoolean("boosters.boost-sell", true)) {
            money *= shop.eco().multiplier(player, Currency.MONEY);
            gems *= shop.eco().multiplier(player, Currency.GEMS);
        }
        if (money > 0) shop.eco().deposit(player.getUniqueId(), Currency.MONEY, money, "SHOP_SELL", "Sell");
        if (gems > 0) shop.eco().deposit(player.getUniqueId(), Currency.GEMS, gems, "SHOP_SELL", "Sell");
        Msg.ok(player, "You sold items for {money} money and {gems} gems",
                "money", Num.format(money), "gems", Num.format(gems));
        shop.plugin().sound(player, "sell");
    }
}
