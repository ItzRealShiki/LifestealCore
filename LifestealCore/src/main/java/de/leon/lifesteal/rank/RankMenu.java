package de.leon.lifesteal.rank;

import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class RankMenu extends Menu {

    private static final int PREV = 45, NEXT = 53;

    private final RankManager manager;
    private final UUID target;
    private final String targetName;
    private final List<RankManager.Rank> ranks;
    private final Set<String> memberOf;
    private final boolean paged;
    private final int pageSize;
    private int page;
    private boolean busy;

    public RankMenu(RankManager manager, UUID target, String targetName, Set<String> memberOf) {
        this.manager = manager;
        this.target = target;
        this.targetName = targetName;
        this.memberOf = new HashSet<>(memberOf);
        this.ranks = manager.ranks();
        int count = ranks.size();
        int size;
        if (count <= 54) {
            size = Math.max(1, (count + 8) / 9) * 9;
            paged = false;
            pageSize = size;
        } else {
            size = 54;
            paged = true;
            pageSize = 45;
        }
        create(size, Component.text(Msg.sc("Rank"), NamedTextColor.DARK_GRAY)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(targetName, Msg.NEUTRAL)));
        show(0);
    }

    private int pages() {
        return Math.max(1, (ranks.size() + pageSize - 1) / pageSize);
    }

    private void show(int newPage) {
        page = Math.max(0, Math.min(newPage, pages() - 1));
        getInventory().clear();
        if (ranks.isEmpty()) {
            set(0, Items.of(Material.BARRIER, "No ranks found", Msg.BAD));
            return;
        }
        int start = page * pageSize;
        for (int i = 0; i < pageSize && start + i < ranks.size(); i++) set(i, rankItem(ranks.get(start + i)));
        if (paged) {
            if (page > 0) set(PREV, Items.of(Material.ARROW, "Previous page", Msg.NEUTRAL));
            if (page < pages() - 1) set(NEXT, Items.of(Material.ARROW, "Next page", Msg.NEUTRAL));
        }
    }

    private ItemStack rankItem(RankManager.Rank rank) {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.item("Left-click: set as rank", NamedTextColor.GRAY));
        lore.add(Msg.item("Shift + right-click: add rank", NamedTextColor.GRAY));
        boolean has = memberOf.contains(rank.name().toLowerCase(Locale.ROOT));
        if (has) {
            lore.add(Component.empty());
            lore.add(Msg.item("Player has this rank", Msg.GOOD));
        }
        ItemStack item = Items.of(rank.material(), Msg.item(rank.display(), rank.color()).decorate(TextDecoration.BOLD), lore);
        if (has) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(Player admin, InventoryClickEvent event) {
        if (!admin.hasPermission(RankManager.ADMIN)) {
            admin.closeInventory();
            return;
        }
        if (!isTop(event)) return;
        int slot = event.getRawSlot();
        if (paged) {
            if (slot == PREV && page > 0) {
                show(page - 1);
                return;
            }
            if (slot == NEXT && page < pages() - 1) {
                show(page + 1);
                return;
            }
        }
        int index = page * pageSize + slot;
        if (slot >= pageSize || index >= ranks.size() || busy) return;
        RankManager.Rank rank = ranks.get(index);
        String display = rank.display();

        if (event.getClick() == ClickType.LEFT) {
            busy = true;
            manager.setRank(target, rank.name())
                    .thenRun(() -> manager.sync(() -> {
                        busy = false;
                        memberOf.clear();
                        memberOf.add(rank.name().toLowerCase(Locale.ROOT));
                        show(page);
                        admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        Msg.ok(admin, "Set the rank of {p} to {r}", "p", targetName, "r", display);
                        notifyTarget(admin, "Your rank was set to {r}", display);
                    }))
                    .exceptionally(ex -> {
                        busy = false;
                        return manager.fail(admin, ex);
                    });
        } else if (event.getClick() == ClickType.SHIFT_RIGHT) {
            busy = true;
            manager.addRank(target, rank.name())
                    .thenAccept(added -> manager.sync(() -> {
                        busy = false;
                        if (!added) {
                            Msg.info(admin, "{p} already has the rank {r}", "p", targetName, "r", display);
                            return;
                        }
                        memberOf.add(rank.name().toLowerCase(Locale.ROOT));
                        show(page);
                        admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
                        Msg.ok(admin, "Added the rank {r} to {p}", "r", display, "p", targetName);
                        notifyTarget(admin, "You received the rank {r}", display);
                    }))
                    .exceptionally(ex -> {
                        busy = false;
                        return manager.fail(admin, ex);
                    });
        }
    }

    private void notifyTarget(Player admin, String template, String display) {
        Player player = Bukkit.getPlayer(target);
        if (player != null && !player.equals(admin)) Msg.ok(player, template, "r", display);
    }
}
