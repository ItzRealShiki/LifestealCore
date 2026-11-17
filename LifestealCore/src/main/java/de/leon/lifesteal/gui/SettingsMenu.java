package de.leon.lifesteal.gui;

import de.leon.lifesteal.fastplace.FastPlaceManager;
import de.leon.lifesteal.settings.SettingsManager;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class SettingsMenu extends Menu {
    private record Entry(String key, String name, Material icon, String description) {}

    private static final List<Entry> GAMEPLAY = List.of(
            new Entry("mob-spawning", "Mob Spawning", Material.ZOMBIE_SPAWN_EGG, "Disable natural mob spawns around you"),
            new Entry("nightvision", "Nightvision", Material.SPYGLASS, "Keep permanent night vision active"),
            new Entry("fastplace", "FastPlace", Material.GOLDEN_SHOVEL, "Requires the FastPlace Fabric mod")
    );
    private static final List<Entry> ECONOMY = List.of(
            new Entry("hide-money", "Hide Money", Material.GOLD_INGOT, "Hide your money from other players"),
            new Entry("hide-gems", "Hide Gems", Material.DIAMOND, "Hide your gems from other players"),
            new Entry("pay-messages", "Pay Messages", Material.EMERALD, "Show incoming payment notifications"),
            new Entry("payments", "Payments", Material.SUNFLOWER, "Allow other players to pay you")
    );
    private static final List<Entry> SOCIAL = List.of(
            new Entry("tpa", "TPA", Material.ENDER_PEARL, "Allow incoming teleport requests"),
            new Entry("bounty-messages", "Bounty Messages", Material.TARGET, "Show bounty notifications"),
            new Entry("public-chat", "Public Chat", Material.PAPER, "Show messages sent by players"),
            new Entry("msg", "Private Messages", Material.WRITABLE_BOOK, "Allow incoming private messages")
    );
    private static final List<Entry> INTERFACE = List.of(
            new Entry("hide-booster-scoreboard", "Hide Booster", Material.BEACON, "Hide booster lines on your scoreboard"),
            new Entry("prefixes-tab", "Prefixes in TAB", Material.NAME_TAG, "Show LuckPerms prefixes in TAB"),
            new Entry("hide-ping", "Hide Ping", Material.CLOCK, "Hide your ping from other players"),
            new Entry("koth-horn", "KOTH Goat Horn", Material.GOAT_HORN, "Play the KOTH start horn for you"),
            new Entry("region-deny-actionbar", "Region Deny Actionbar", Material.BELL, "Show a message when a region flag blocks your action"),
            new Entry("region-deny-sound", "Region Deny Sound", Material.NOTE_BLOCK, "Play a sound when a region flag blocks your action")
    );

    private static final int[] ENTRY_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    private final SettingsManager settings;
    private final FastPlaceManager fastPlace;
    private Player viewer;
    private String page = "root";

    public SettingsMenu(SettingsManager settings, FastPlaceManager fastPlace) {
        this.settings = settings;
        this.fastPlace = fastPlace;
        create(27, Component.text("Settings"));
        refresh();
    }

    @Override
    public void refresh() {
        for (int slot = 0; slot < 27; slot++) {
            boolean footer = slot >= 18;
            set(slot, Items.filler(footer ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        }
        if (viewer == null) return;
        switch (page) {
            case "root" -> renderRoot();
            case "admin" -> renderAdmin();
            default -> renderCategory(page);
        }
    }

    private void renderRoot() {
        set(10, Items.of(Material.IRON_SWORD, "Gameplay", Msg.NEUTRAL, "Movement and client features"));
        set(12, Items.of(Material.GOLD_INGOT, "Economy", Msg.NEUTRAL, "Balance privacy and payments"));
        set(14, Items.of(Material.PLAYER_HEAD, "Social", Msg.NEUTRAL, "Chat and player interactions"));
        set(16, Items.of(Material.CLOCK, "Interface", Msg.NEUTRAL, "Scoreboard and TAB preferences"));
        if (canSeeAdminSettings(viewer)) {
            set(22, Items.of(Material.COMMAND_BLOCK, "Admin Settings", Msg.NEUTRAL, "Builder and staff features"));
        }
    }

    private void renderCategory(String category) {
        List<Entry> entries = entries(category);
        for (int i = 0; i < entries.size() && i < ENTRY_SLOTS.length; i++) {
            renderEntry(ENTRY_SLOTS[i], entries.get(i));
        }
        set(22, Items.of(Material.ARROW, "Back", Msg.NEUTRAL, "Return to settings"));
    }

    private void renderAdmin() {
        if (!canSeeAdminSettings(viewer)) {
            page = "root";
            renderRoot();
            return;
        }
        boolean enabled = fastPlace.isFastBreakConfigured(viewer);
        set(13, Items.of(enabled ? Material.LIME_DYE : Material.GRAY_DYE, "FastBreak",
                enabled ? Msg.GOOD : Msg.BAD,
                "Status: " + (enabled ? "Enabled" : "Disabled"),
                "Builder and staff only",
                "Creative mode and the FastPlace Fabric mod are required"));
        set(22, Items.of(Material.ARROW, "Back", Msg.NEUTRAL, "Return to settings"));
    }

    private void renderEntry(int slot, Entry entry) {
        boolean enabled = entry.key().equals("fastplace")
                ? fastPlace.isEnabled(viewer)
                : settings.isEnabled(viewer, entry.key());
        set(slot, Items.of(enabled ? entry.icon() : Material.GRAY_DYE, entry.name(),
                enabled ? Msg.GOOD : Msg.BAD,
                "Status: " + (enabled ? "Enabled" : "Disabled"),
                entry.description(),
                "Click to " + (enabled ? "disable" : "enable")));
    }

    @Override
    public void open(Player player) {
        viewer = player;
        page = "root";
        refresh();
        super.open(player);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!isTop(event)) return;
        int slot = event.getRawSlot();
        if (page.equals("root")) {
            page = switch (slot) {
                case 10 -> "gameplay";
                case 12 -> "economy";
                case 14 -> "social";
                case 16 -> "interface";
                case 22 -> canSeeAdminSettings(player) ? "admin" : "root";
                default -> page;
            };
            refresh();
            return;
        }
        if (slot == 22) {
            page = "root";
            refresh();
            return;
        }
        if (page.equals("admin")) {
            if (!canSeeAdminSettings(player)) {
                Msg.err(player, "You do not have permission to open admin settings");
                page = "root";
                refresh();
                return;
            }
            if (slot == 13) toggleFastBreak(player);
            refresh();
            return;
        }

        List<Entry> entries = entries(page);
        for (int i = 0; i < entries.size() && i < ENTRY_SLOTS.length; i++) {
            if (ENTRY_SLOTS[i] == slot) {
                toggle(player, entries.get(i));
                break;
            }
        }
        refresh();
    }

    private void toggle(Player player, Entry entry) {
        if (entry.key().equals("fastplace")) {
            boolean enabled = !fastPlace.isEnabled(player);
            fastPlace.setEnabled(player, enabled);
            Msg.ok(player, "FastPlace {state}", "state", enabled ? "enabled" : "disabled");
            return;
        }
        boolean enabled = settings.toggle(player, entry.key());
        Msg.ok(player, "{setting} {state}", "setting", Msg.sc(entry.name()),
                "state", enabled ? "enabled" : "disabled");
    }

    private void toggleFastBreak(Player player) {
        if (!fastPlace.canUseFastBreak(player)) {
            Msg.err(player, "FastBreak is available to builders and staff only");
            return;
        }
        boolean enabled = !fastPlace.isFastBreakConfigured(player);
        fastPlace.setFastBreakEnabled(player, enabled);
        Msg.ok(player, "FastBreak {state}", "state", enabled ? "enabled" : "disabled");
        if (enabled && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            Msg.info(player, "FastBreak will activate when you enter Creative mode");
        }
    }

    private static List<Entry> entries(String category) {
        return switch (category) {
            case "gameplay" -> GAMEPLAY;
            case "economy" -> ECONOMY;
            case "social" -> SOCIAL;
            case "interface" -> INTERFACE;
            default -> List.of();
        };
    }

    private static boolean canSeeAdminSettings(Player player) {
        return player.hasPermission("Builder.Core")
                || player.hasPermission("Admin.Core")
                || player.hasPermission("Owner.Core");
    }
}
