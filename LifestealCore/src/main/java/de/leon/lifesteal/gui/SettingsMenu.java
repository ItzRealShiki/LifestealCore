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
        create(27, Component.text("Player Settings"));
        refresh();
    }

    @Override
    public void refresh() {
        for (int slot = 0; slot < 27; slot++) {
            boolean border = slot < 9 || slot > 17 || slot % 9 == 0 || slot % 9 == 8;
            set(slot, Items.filler(border ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        }
        if (viewer == null) return;
        boolean placeEnabled = fastPlace.isEnabled(viewer);
        set(11, Items.of(placeEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "FastPlace", placeEnabled ? Msg.GOOD : Msg.BAD,
                placeEnabled ? "Status: Enabled" : "Status: Disabled",
                "Click to " + (placeEnabled ? "disable" : "enable") + " FastPlace",
                "Requires the FastPlace Fabric mod"));
        set(13, Items.of(Material.BOOK, "Player Settings", Msg.NEUTRAL,
                "Personal settings are saved automatically"));
        set(15, Items.of(Material.DIAMOND_PICKAXE, "Creative Mode", Msg.NEUTRAL,
                "Vanilla Creative mode already",
                "breaks blocks instantly.",
                "No FastBreak setting is needed."));
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
