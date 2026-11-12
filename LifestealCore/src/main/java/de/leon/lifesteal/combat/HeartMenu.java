package de.leon.lifesteal.combat;

import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** /heart check: redeemed hearts, 45 per page. */
public final class HeartMenu extends Menu {

    private static final int PAGE_SIZE = 45, PREV = 45, INFO = 49, NEXT = 53;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final HeartManager hearts;
    private final List<HeartManager.HeartRecord> history;
    private final int currentHearts;
    private int page;

    public HeartMenu(HeartManager hearts, String targetName, int currentHearts, List<HeartManager.HeartRecord> history) {
        this.hearts = hearts;
        this.history = history;
        this.currentHearts = currentHearts;
        create(54, Component.text(Msg.sc("Hearts"), NamedTextColor.DARK_GRAY)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(targetName, Msg.NEUTRAL))
                .append(Component.text(" (" + history.size() + ")", NamedTextColor.DARK_GRAY)));
        show(0);
    }

    private int pages() {
        return Math.max(1, (history.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void show(int newPage) {
        page = Math.max(0, Math.min(newPage, pages() - 1));
        getInventory().clear();
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < history.size(); i++) {
            HeartManager.HeartRecord record = history.get(start + i);
            int number = history.size() - (start + i);
            ItemStack item = hearts.visualHeart();
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Msg.item("Heart #{n}", Msg.BAD, "n", number));
            meta.lore(List.of(
                    Msg.item("From: {n}", Msg.NEUTRAL, "n", record.originName() == null ? "?" : record.originName()),
                    Msg.item("Redeemed: {d}", NamedTextColor.GRAY, "d", DATE.format(Instant.ofEpochMilli(record.time())))));
            item.setItemMeta(meta);
            set(i, item);
        }
        if (page > 0) set(PREV, Items.of(Material.ARROW, "Previous page", Msg.NEUTRAL));
        if (page < pages() - 1) set(NEXT, Items.of(Material.ARROW, "Next page", Msg.NEUTRAL));
        set(INFO, Items.of(Material.BOOK, Msg.item("Heart statement", Msg.NEUTRAL), List.of(
                Msg.item("Redeemed hearts: {n}", NamedTextColor.GRAY, "n", history.size()),
                Msg.item("Current hearts: {n}", NamedTextColor.GRAY, "n", currentHearts),
                Msg.item("Page: {p}/{t}", NamedTextColor.GRAY, "p", page + 1, "t", pages()))));
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        int slot = event.getRawSlot();
        if (slot == PREV && page > 0) show(page - 1);
        else if (slot == NEXT && page < pages() - 1) show(page + 1);
    }
}
