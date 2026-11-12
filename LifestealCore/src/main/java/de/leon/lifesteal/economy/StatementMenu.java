package de.leon.lifesteal.economy;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StatementMenu extends Menu {

    private static final int PAGE_SIZE = 45, PREV = 45, INFO = 49, NEXT = 53;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final LifestealCore plugin;
    private final EconomyManager eco;
    private final UUID target;
    private final String targetName;
    private final Currency currency;
    private int page;
    private int pages = 1;
    private boolean loading;

    public StatementMenu(LifestealCore plugin, EconomyManager eco, UUID target, String targetName, Currency currency) {
        this.plugin = plugin;
        this.eco = eco;
        this.target = target;
        this.targetName = targetName;
        this.currency = currency;
        create(54, Component.text(Msg.sc("Statement"), NamedTextColor.DARK_GRAY)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(targetName + " (" + currency.key() + ")", Msg.NEUTRAL)));
    }

    public void show(Player viewer, int newPage) {
        if (loading) return;
        loading = true;
        eco.statement(target, currency, newPage, PAGE_SIZE).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loading = false;
                    if (!viewer.isOnline() || !viewer.hasPermission(EcoCommands.ADMIN)) return;
                    page = Math.max(0, newPage);
                    pages = Math.max(1, (result.total() + PAGE_SIZE - 1) / PAGE_SIZE);
                    render(result);
                    if (getInventory().getViewers().contains(viewer)) return;
                    open(viewer);
                })).exceptionally(ex -> {
            loading = false;
            plugin.getLogger().warning("Statement could not be loaded: " + ex.getMessage());
            return null;
        });
    }

    private void render(EconomyManager.Page result) {
        getInventory().clear();
        int slot = 0;
        for (EconomyManager.Transaction t : result.entries()) {
            boolean in = t.amount() >= 0;
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.item("Type: " + readable(t.type()), NamedTextColor.GRAY));
            if (t.balance() >= 0) lore.add(Msg.item("Balance after: {b}", NamedTextColor.GRAY, "b", Num.format(t.balance())));
            lore.add(Msg.item("By: {a}", NamedTextColor.GRAY, "a", t.actor() == null || t.actor().isEmpty() ? "-" : t.actor()));
            lore.add(Msg.item("Date: {d}", NamedTextColor.GRAY, "d", DATE.format(Instant.ofEpochMilli(t.time()))));
            set(slot++, Items.of(in ? Material.LIME_DYE : Material.RED_DYE,
                    Msg.item((in ? "+" : "-") + "{a} " + currency.key(), in ? Msg.GOOD : Msg.BAD,
                            "a", Num.format(Math.abs(t.amount()))), lore));
        }
        if (page > 0) set(PREV, Items.of(Material.ARROW, "Previous page", Msg.NEUTRAL));
        if (page < pages - 1) set(NEXT, Items.of(Material.ARROW, "Next page", Msg.NEUTRAL));
        set(INFO, Items.of(Material.BOOK, Msg.item("Entries: {n}", Msg.NEUTRAL, "n", result.total()),
                List.of(Msg.item("Page: {p}/{t}", NamedTextColor.GRAY, "p", page + 1, "t", pages))));
    }

    private static String readable(String type) {
        return switch (type) {
            case "ADMIN_GIVE" -> "Admin credit";
            case "ADMIN_REMOVE" -> "Admin debit";
            case "SHOP_BUY" -> "Shop purchase";
            case "SHOP_SELL" -> "Sold items";
            case "PAY_IN" -> "Payment received";
            case "PAY_OUT" -> "Payment sent";
            case "BOUNTY_PLACE" -> "Bounty placed";
            case "BOUNTY_CLAIM" -> "Bounty claimed";
            case "BOUNTY_REFUND" -> "Bounty refund";
            case "PLUGIN" -> "Other plugin";
            default -> type.toLowerCase().replace('_', ' ');
        };
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!player.hasPermission(EcoCommands.ADMIN)) {
            player.closeInventory();
            return;
        }
        if (!isTop(event)) return;
        int slot = event.getRawSlot();
        if (slot == PREV && page > 0) show(player, page - 1);
        else if (slot == NEXT && page < pages - 1) show(player, page + 1);
    }
}
