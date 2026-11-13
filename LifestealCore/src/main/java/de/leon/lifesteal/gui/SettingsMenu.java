package de.leon.lifesteal.gui;

import de.leon.lifesteal.fastplace.FastPlaceManager;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class SettingsMenu extends Menu {
    private final FastPlaceManager fastPlace;
    private Player viewer;

    public SettingsMenu(FastPlaceManager fastPlace) {
        this.fastPlace = fastPlace;
        create(27, Component.text("Settings"));
        refresh();
    }

    @Override
    public void refresh() {
        for (int slot = 0; slot < 27; slot++) {
            boolean border = slot < 9 || slot > 17 || slot % 9 == 0 || slot % 9 == 8;
            set(slot, Items.filler(border ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        }
        if (viewer == null) return;
        boolean enabled = fastPlace.isEnabled(viewer);
        set(11, Items.of(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "FastPlace", enabled ? Msg.GOOD : Msg.BAD,
                enabled ? "Status: Enabled" : "Status: Disabled",
                "Click to " + (enabled ? "disable" : "enable") + " FastPlace",
                "Requires the FastPlace Fabric mod"));
        set(13, Items.of(Material.BOOK, "Player Settings", Msg.NEUTRAL,
                "Personal settings are saved automatically",
                "More options will be added here"));
    }

    @Override
    public void open(Player player) {
        this.viewer = player;
        refresh();
        super.open(player);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        if (event.getRawSlot() == 11) {
            boolean enabled = !fastPlace.isEnabled(player);
            fastPlace.setEnabled(player, enabled);
            Msg.ok(player, "FastPlace {state}", "state", enabled ? "enabled" : "disabled");
            refresh();
        }
    }
}
