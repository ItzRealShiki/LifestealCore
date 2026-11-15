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
            new Entry("fastplace", "FastPlace", Material.GOLDEN_SHOVEL, "Requires the FastPlace Fabric mod"),
            new Entry("fastbreak", "FastBreak", Material.DIAMOND_PICKAXE, "Builder and staff only; Creative mode and mod required")
    );
    private static final List<Entry> ECONOMY = List.of(
            new Entry("hide-money", "Hide Money", Material.GOLD_INGOT, "Hide your money balance from other players"),
            new Entry("hide-gems", "Hide Gems", Material.DIAMOND, "Hide your gems balance from other players"),
            new Entry("pay-messages", "Pay Messages", Material.EMERALD, "Show incoming payment notifications"),
            new Entry("payments", "Payments", Material.SUNFLOWER, "Allow other players to pay you")
    );
    private static final List<Entry> SOCIAL = List.of(
            new Entry("tpa", "TPA", Material.ENDER_PEARL, "Allow incoming TPA and TPAHERE requests"),
            new Entry("bounty-messages", "Bounty Messages", Material.TARGET, "Show bounty notifications"),
            new Entry("public-chat", "Public Chat", Material.PAPER, "Show messages sent by players"),
            new Entry("msg", "Private Messages", Material.WRITABLE_BOOK, "Allow incoming private messages")
    );
    private static final List<Entry> INTERFACE = List.of(
            new Entry("hide-booster-scoreboard", "Hide Booster on Scoreboard", Material.BEACON, "Hide active booster lines on your scoreboard"),
            new Entry("prefixes-tab", "Prefixes in TAB", Material.NAME_TAG, "Show LuckPerms prefixes in the player list"),
            new Entry("hide-ping", "Hide Ping", Material.CLOCK, "Hide your ping from /ping and TAB displays")
    );

    private final SettingsManager settings;
    private final FastPlaceManager fastPlace;
    private Player viewer;
    private String page = "root";

    public SettingsMenu(SettingsManager settings, FastPlaceManager fastPlace) {
        this.settings = settings;
        this.fastPlace = fastPlace;
        create(54, Component.text("Settings"));
        refresh();
    }

    @Override
    public void refresh() {
        for (int slot = 0; slot < 54; slot++) {
            boolean edge = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            set(slot, Items.filler(edge ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        }
        if (viewer == null) return;
        if (page.equals("root")) renderRoot();
        else if (page.equals("admin")) renderAdmin();
        else renderCategory(page);
    }

    private void renderRoot() {
        set(20, Items.of(Material.IRON_SWORD, "Gameplay", Msg.NEUTRAL, "Mob Spawning, Nightvision and client features"));
        set(22, Items.of(Material.GOLD_INGOT, "Economy", Msg.NEUTRAL, "Balance privacy and payment preferences"));
        set(24, Items.of(Material.PLAYER_HEAD, "Social", Msg.NEUTRAL, "TPA, bounty notifications and chat"));
        set(30, Items.of(Material.CLOCK, "Interface", Msg.NEUTRAL, "Scoreboard and TAB preferences"));
        set(40, Items.of(Material.BOOK, "Member Settings", Msg.NEUTRAL, "Your personal settings are saved automatically"));
        if (isAdmin(viewer)) set(49, Items.of(Material.COMMAND_BLOCK, "Admin Settings", Msg.NEUTRAL, "Open staff-only settings"));
    }

    private void renderCategory(String category) {
        List<Entry> entries = switch (category) {
            case "gameplay" -> GAMEPLAY;
            case "economy" -> ECONOMY;
            case "social" -> SOCIAL;
            case "interface" -> INTERFACE;
            default -> List.of();
        };
        int[] slots = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
        for (int i = 0; i < entries.size() && i < slots.length; i++) renderEntry(slots[i], entries.get(i));
        set(49, Items.of(Material.ARROW, "Back", Msg.NEUTRAL, "Return to categories"));
    }

    private void renderAdmin() {
        if (!isAdmin(viewer)) {
            page = "root";
            renderRoot();
            return;
        }
        set(22, Items.of(fastPlace.isFastBreakConfigured(viewer) ? Material.LIME_DYE : Material.GRAY_DYE,
                "FastBreak", fastPlace.isFastBreakConfigured(viewer) ? Msg.GOOD : Msg.BAD,
                "Status: " + (fastPlace.isFastBreakConfigured(viewer) ? "Enabled" : "Disabled"),
                "Available to Builder.Core, Admin.Core and Owner.Core",
                "Creative mode and the updated FastPlace Fabric mod are required"));
        set(31, Items.of(Material.BOOK, "Admin Settings", Msg.NEUTRAL, "Staff-only settings are kept separate from member settings"));
        set(49, Items.of(Material.ARROW, "Back", Msg.NEUTRAL, "Return to categories"));
    }

    private void renderEntry(int slot, Entry entry) {
        boolean enabled = switch (entry.key()) {
            case "fastplace" -> fastPlace.isEnabled(viewer);
            case "fastbreak" -> fastPlace.isFastBreakConfigured(viewer);
            default -> settings.isEnabled(viewer, entry.key());
        };
        if (entry.key().equals("fastbreak") && !fastPlace.canUseFastBreak(viewer)) return;
        set(slot, Items.of(enabled ? entry.icon() : Material.GRAY_DYE, entry.name(),
                enabled ? Msg.GOOD : Msg.BAD,
                "Status: " + (enabled ? "Enabled" : "Disabled"),
                entry.description(), "Click to " + (enabled ? "disable" : "enable")));
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
                case 20 -> "gameplay";
                case 22 -> "economy";
                case 24 -> "social";
                case 30 -> "interface";
                case 49 -> isAdmin(player) ? "admin" : "root";
                default -> page;
            };
            refresh();
            return;
        }
        if (slot == 49) {
            page = "root";
            refresh();
            return;
        }
        if (page.equals("admin")) {
            if (!isAdmin(player)) {
                Msg.err(player, "You do not have permission to open admin settings");
                page = "root";
                refresh();
                return;
            }
            if (slot == 22) toggleFastBreak(player);
            refresh();
            return;
        }

        List<Entry> entries = switch (page) {
            case "gameplay" -> GAMEPLAY;
            case "economy" -> ECONOMY;
            case "social" -> SOCIAL;
            case "interface" -> INTERFACE;
            default -> List.of();
        };
        int[] slots = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            if (slots[i] != slot) continue;
            toggle(player, entries.get(i));
            break;
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
        if (entry.key().equals("fastbreak")) {
            if (!fastPlace.canUseFastBreak(player)) {
                Msg.err(player, "FastBreak is available to builders and staff only");
                return;
            }
            toggleFastBreak(player);
            return;
        }
        boolean enabled = settings.toggle(player, entry.key());
        Msg.ok(player, "{setting} {state}", "setting", Msg.sc(entry.name()), "state", enabled ? "enabled" : "disabled");
    }

    private void toggleFastBreak(Player player) {
        if (!fastPlace.canUseFastBreak(player)) {
            Msg.err(player, "You do not have permission to change FastBreak");
            return;
        }
        boolean enabled = !fastPlace.isFastBreakConfigured(player);
        fastPlace.setFastBreakEnabled(player, enabled);
        Msg.ok(player, "FastBreak {state}", "state", enabled ? "enabled" : "disabled");
        if (enabled && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            Msg.info(player, "FastBreak will activate when you enter Creative mode");
        }
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermission("Admin.Core") || player.hasPermission("Owner.Core");
    }
}
