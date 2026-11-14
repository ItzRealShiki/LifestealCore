package de.leon.lifesteal.gui;

import de.leon.lifesteal.fastplace.FastPlaceManager;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
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

        boolean breakEnabled = fastPlace.isFastBreakConfigured(viewer);
        boolean creative = viewer.getGameMode() == GameMode.CREATIVE;
        set(15, Items.of(breakEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "FastBreak", breakEnabled ? Msg.GOOD : Msg.BAD,
                breakEnabled ? (creative ? "Status: Enabled" : "Status: Enabled (Creative only)")
                        : "Status: Disabled",
                "Removes the delay between consecutive block breaks",
                "Works only while in Creative mode",
                "Requires the updated FastPlace Fabric mod"));

        set(13, Items.of(Material.BOOK, "Player Settings", Msg.NEUTRAL,
                "Personal settings are saved automatically",
                "Access: Builder.Core or Admin.Core"));
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
        if (!fastPlace.canUseSettings(player)) {
            Msg.err(player, "You do not have permission to change these settings");
            player.closeInventory();
            return;
        }

        if (event.getRawSlot() == 11) {
            boolean enabled = !fastPlace.isEnabled(player);
            if (!fastPlace.setEnabled(player, enabled)) {
                Msg.err(player, "You do not have permission to change this setting");
                return;
            }
            Msg.ok(player, "FastPlace {state}", "state", enabled ? "enabled" : "disabled");
            refresh();
        } else if (event.getRawSlot() == 15) {
            boolean enabled = !fastPlace.isFastBreakConfigured(player);
            if (!fastPlace.setFastBreakEnabled(player, enabled)) {
                Msg.err(player, "You do not have permission to change this setting");
                return;
            }
            Msg.ok(player, "FastBreak {state}", "state", enabled ? "enabled" : "disabled");
            if (enabled && player.getGameMode() != GameMode.CREATIVE) {
                Msg.info(player, "FastBreak will activate when you enter Creative mode");
            }
            refresh();
        }
    }
}
