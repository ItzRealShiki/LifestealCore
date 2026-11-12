package de.leon.lifesteal.booster;

import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /booster: own boosters. Passive = toggle, normal = start as server booster. */
public final class BoosterMenu extends Menu {

    private final BoosterManager manager;
    private final Player viewer;
    private final Map<Integer, BoosterType> slots = new HashMap<>();

    public BoosterMenu(BoosterManager manager, Player viewer) {
        this.manager = manager;
        this.viewer = viewer;
        create(27, Component.text(Msg.sc("Booster inventory"), NamedTextColor.DARK_GRAY));
        render();
    }

    private void render() {
        getInventory().clear();
        slots.clear();
        PlayerData data = manager.plugin().data().get(viewer);
        int slot = 0;
        for (BoosterType type : BoosterType.values()) {
            int count = data.boosterCount(type);
            boolean active = data.hasPassive(type);
            if ((count <= 0 && !active) || slot >= 27) continue;

            List<Component> lore = new ArrayList<>();
            lore.add(Msg.item("Rarity: {r}", NamedTextColor.GRAY, "r",
                    Component.text(Msg.sc(type.getRarity().getName()), type.getRarity().getColor())));
            lore.add(Msg.item("Amount: {n}", NamedTextColor.GRAY, "n", count));
            if (type.getCategory() == BoosterType.Category.PASSIVE) {
                lore.add(active ? Msg.item("Active (click to disable)", Msg.GOOD)
                        : Msg.item("Inactive (click to enable)", Msg.BAD));
            } else {
                lore.add(Msg.item("Click to activate for the whole server", Msg.NEUTRAL));
            }
            ItemStack item = Items.of(type.getDyeMaterial(),
                    Msg.item(type.getCategory().getPrefix() + ": " + type.getDisplayName(), type.getRarity().getColor()), lore);
            item.setAmount(Math.max(1, Math.min(64, count)));
            slots.put(slot, type);
            set(slot++, item);
        }
        ItemStack glass = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) if (getInventory().getItem(i) == null) set(i, glass);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        BoosterType type = slots.get(event.getRawSlot());
        if (type == null) return;
        if (type.getCategory() == BoosterType.Category.PASSIVE) {
            manager.togglePassive(player, type);
        } else {
            manager.useNormal(player, type);
        }
        render();
    }
}
