package de.leon.lifesteal.shop;

import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SellMenu extends Menu {

    private static final int SELL_BUTTON_SLOT = 49;
    private static final int DEPOSIT_SLOTS = 45;

    private final ShopManager shop;

    public SellMenu(ShopManager shop) {
        this.shop = shop;
        create(54, shop.title("Sell"));
        render();
    }

    private void render() {
        // The first 45 slots are intentionally empty deposit slots.
        // The bottom row stays empty except for the Sell All button.
        for (int slot = DEPOSIT_SLOTS; slot < 54; slot++) {
            if (slot != SELL_BUTTON_SLOT) getInventory().setItem(slot, null);
        }

        ItemStack sellAll = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta meta = sellAll.getItemMeta();
        meta.displayName(Msg.item("SELL ALL", Msg.GOOD));
        meta.lore(List.of(
                Msg.item("Sell only the items placed in this menu", Msg.NEUTRAL),
                Msg.item("Items in your player inventory are never sold", Msg.NEUTRAL)
        ));
        sellAll.setItemMeta(meta);
        set(SELL_BUTTON_SLOT, sellAll);
    }

    @Override
    public boolean allowsItems() {
        return true;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        ClickType click = event.getClick();

        // Shift-click behaves like a normal inventory transfer, but only the 45
        // deposit slots may receive items (the Sell All control is never a target).
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null) return;
            if (event.getClickedInventory().equals(player.getInventory())) {
                ItemStack source=event.getCurrentItem();
                if(source==null||source.getType().isAir())return;
                ItemStack moving=source.clone();
                addToDeposit(moving);
                if(moving.getAmount()<=0)event.setCurrentItem(null);
                else event.setCurrentItem(moving);
            } else if (event.getClickedInventory().equals(getInventory()) && rawSlot>=0 && rawSlot<DEPOSIT_SLOTS) {
                ItemStack source=event.getCurrentItem();
                if(source==null||source.getType().isAir())return;
                Map<Integer,ItemStack> overflow=player.getInventory().addItem(source.clone());
                int left=overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                if(left<=0)event.setCurrentItem(null);
                else {ItemStack remainder=source.clone();remainder.setAmount(left);event.setCurrentItem(remainder);}
            }
            return;
        }

        // Prevent actions that could interact with the Sell All control or duplicate
        // deposited stacks through double-click / hotbar collection.
        boolean ordinaryClick = click == ClickType.LEFT || click == ClickType.RIGHT;
        if (!ordinaryClick || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.HOTBAR_SWAP
                || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot >= 0 && rawSlot < getInventory().getSize()) {
            // Only slots 0-44 accept items. All other top slots are controls/empty.
            if (rawSlot == SELL_BUTTON_SLOT) {
                event.setCancelled(true);
                if (event.getCursor() == null || event.getCursor().getType().isAir()) {
                    sellAll(player);
                }
                return;
            }
            if (rawSlot >= DEPOSIT_SLOTS) {
                event.setCancelled(true);
            }
            return;
        }

        // Normal left/right clicks in the player's own inventory are allowed,
        // so the player can pick up an item and place it into a deposit slot.
    }

    @Override
    public void onDrag(Player player, InventoryDragEvent event) {
        // Dragging is allowed only when every affected slot is a deposit slot.
        // This prevents dragging into the bottom control row or player inventory.
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= DEPOSIT_SLOTS) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void addToDeposit(ItemStack moving) {
        for(int slot=0;slot<DEPOSIT_SLOTS&&moving.getAmount()>0;slot++){
            ItemStack current=getInventory().getItem(slot);
            if(current==null||current.getType().isAir()||!current.isSimilar(moving))continue;
            int space=current.getMaxStackSize()-current.getAmount();
            if(space<=0)continue;
            int amount=Math.min(space,moving.getAmount());
            current.setAmount(current.getAmount()+amount);
            moving.setAmount(moving.getAmount()-amount);
        }
        for(int slot=0;slot<DEPOSIT_SLOTS&&moving.getAmount()>0;slot++){
            ItemStack current=getInventory().getItem(slot);
            if(current!=null&&!current.getType().isAir())continue;
            int amount=Math.min(moving.getMaxStackSize(),moving.getAmount());
            ItemStack placed=moving.clone();placed.setAmount(amount);getInventory().setItem(slot,placed);
            moving.setAmount(moving.getAmount()-amount);
        }
    }

    private void sellAll(Player player) {
        double money = 0;
        double gems = 0;
        int soldAmount = 0;
        Map<Material, Integer> soldItems = new HashMap<>();
        Map<Currency, Double> totals = new EnumMap<>(Currency.class);

        // Read and remove items exclusively from the Sell GUI's deposit area.
        // The player's normal inventory is never accessed here.
        for (int slot = 0; slot < DEPOSIT_SLOTS; slot++) {
            ItemStack stack = getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;

            ShopManager.SellPrice price = shop.sellPrice(stack.getType());
            if (price == null) continue; // Keep items without a configured sell price.

            int amount = stack.getAmount();
            double total = price.price() * amount;
            if (!(total > 0) || !Double.isFinite(total)) continue;

            soldAmount += amount;
            soldItems.merge(stack.getType(), amount, Integer::sum);
            totals.merge(price.currency(), total, Double::sum);
            getInventory().setItem(slot, null);
        }

        if (soldAmount == 0) {
            Msg.err(player, "There are no sellable items in this menu");
            shop.plugin().sound(player, "error");
            return;
        }

        if (shop.plugin().getConfig().getBoolean("boosters.boost-sell", true)) {
            for (Currency currency : Currency.values()) {
                Double amount = totals.get(currency);
                if (amount == null) continue;
                totals.put(currency, amount * shop.eco().multiplier(player, currency));
            }
        }

        money = totals.getOrDefault(Currency.MONEY, 0.0);
        gems = totals.getOrDefault(Currency.GEMS, 0.0);

        if (money > 0 && Double.isFinite(money)) {
            shop.eco().deposit(player.getUniqueId(), Currency.MONEY, money, "SHOP_SELL", "Sell GUI");
        }
        if (gems > 0 && Double.isFinite(gems)) {
            shop.eco().deposit(player.getUniqueId(), Currency.GEMS, gems, "SHOP_SELL", "Sell GUI");
        }

        if (shop.plugin().adminManager() != null) {
            for (Map.Entry<Material, Integer> entry : soldItems.entrySet()) {
                ShopManager.SellPrice price = shop.sellPrice(entry.getKey());
                if (price == null) continue;
                double itemTotal = price.price() * entry.getValue();
                if (shop.plugin().getConfig().getBoolean("boosters.boost-sell", true)) {
                    itemTotal *= shop.eco().multiplier(player, price.currency());
                }
                shop.plugin().adminManager().logSell(player, entry.getKey(), entry.getValue(),
                        price.currency(), itemTotal);
            }
        }

        Msg.ok(player, "Sold {amount} items for {money} money and {gems} gems",
                "amount", soldAmount, "money", Num.format(money), "gems", Num.format(gems));
        shop.plugin().sound(player, "sell");
        // Keep the GUI open. Sold slots are already empty; unsellable items remain.
    }

    @Override
    public void onClose(Player player) {
        // Return every remaining deposited item when the GUI is closed.
        for (int slot = 0; slot < DEPOSIT_SLOTS; slot++) {
            ItemStack stack = getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;

            getInventory().setItem(slot, null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack.clone());
            for (ItemStack leftover : overflow.values()) {
                Item dropped = player.getWorld().dropItem(player.getLocation(), leftover);
                dropped.setOwner(player.getUniqueId());
                dropped.setPickupDelay(40);
            }
        }
    }
}
