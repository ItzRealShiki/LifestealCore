package de.leon.lifesteal.worldguard;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.*;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.gui.ChatInput;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;

public final class RegionFlagManager implements TabExecutor {
    private static final String ADMIN = "Admin.Core";
    private static final int PAGE_SIZE = 45;
    private final LifestealCore plugin;
    private final ChatInput chatInput;

    public RegionFlagManager(LifestealCore plugin, ChatInput chatInput) {
        this.plugin = plugin;
        this.chatInput = chatInput;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can use this command");
            return true;
        }
        if (!player.hasPermission(ADMIN) && !player.hasPermission("Owner.Core")) {
            Msg.err(player, "Admin.Core is required to edit region flags");
            return true;
        }
        World world = player.getWorld();
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
        if (manager == null) {
            Msg.err(player, "WorldGuard region data is unavailable in this world");
            return true;
        }
        if (args.length > 0) {
            ProtectedRegion region = manager.getRegion(args[0]);
            if (region == null) {
                Msg.err(player, "Region {r} was not found in this world", "r", args[0]);
                return true;
            }
            openFlags(player, manager, region, 0);
            return true;
        }
        List<ProtectedRegion> regions = new ArrayList<>();
        for (ProtectedRegion region : manager.getApplicableRegions(BlockVector3.at(
                player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()))) {
            regions.add(region);
        }
        if (regions.isEmpty()) {
            Msg.err(player, "No WorldGuard region was found at your location");
        } else if (regions.size() == 1) {
            openFlags(player, manager, regions.get(0), 0);
        } else {
            new RegionSelectMenu(player.getUniqueId(), manager, regions).open(player);
        }
        return true;
    }

    private void openFlags(Player player, RegionManager manager, ProtectedRegion region, int page) {
        new FlagMenu(player.getUniqueId(), manager, region, page).open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(ADMIN) && !sender.hasPermission("Owner.Core")) return List.of();
        if (!(sender instanceof Player player)) return List.of();
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.getWorld()));
        if (manager == null) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return manager.getRegions().keySet().stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted().toList();
    }

    private static String description(String name) {
        return switch (name) {
            case "pvp" -> "Controls player versus player combat in this region.";
            case "build" -> "Controls whether players may build, break, or interact.";
            case "block-break" -> "Controls whether blocks may be broken.";
            case "block-place" -> "Controls whether blocks may be placed.";
            case "interact" -> "Controls interactions with blocks and entities.";
            case "chest-access" -> "Controls access to chests and other containers.";
            case "entry" -> "Controls whether players may enter this region.";
            case "exit" -> "Controls whether players may leave this region.";
            case "use" -> "Controls use of doors, buttons, levers, and similar blocks.";
            case "mob-spawning" -> "Controls natural and manual mob spawning.";
            case "creeper-explosion" -> "Controls damage caused by creeper explosions.";
            case "other-explosion" -> "Controls damage caused by other explosions.";
            case "fire-spread" -> "Controls whether fire can spread.";
            case "greeting" -> "Message shown when a player enters this region.";
            case "farewell" -> "Message shown when a player leaves this region.";
            case "fall-damage" -> "Controls fall damage inside this region.";
            case "item-drop" -> "Controls whether players may drop items.";
            case "item-pickup" -> "Controls whether players may pick up items.";
            case "enderpearl" -> "Controls whether ender pearls may be used.";
            case "tnt" -> "Controls TNT ignition and explosions.";
            default -> "WorldGuard setting for this region. See the flag name for its exact purpose.";
        };
    }

    private final class RegionSelectMenu extends Menu {
        private final UUID viewer;
        private final RegionManager manager;
        private final List<ProtectedRegion> regions;

        RegionSelectMenu(UUID viewer, RegionManager manager, List<ProtectedRegion> regions) {
            this.viewer = viewer;
            this.manager = manager;
            this.regions = List.copyOf(regions);
            create(54, Component.text(Msg.sc("Select Region"), Msg.NEUTRAL));
            for (int i = 0; i < Math.min(PAGE_SIZE, regions.size()); i++) {
                ProtectedRegion region = regions.get(i);
                set(i, Items.of(Material.MAP, region.getId(), Msg.NEUTRAL,
                        "Click to edit this region's flags",
                        "Priority: " + region.getPriority()));
            }
            set(49, Items.of(Material.BARRIER, "Close", Msg.BAD));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            int slot = event.getRawSlot();
            if (slot == 49) { player.closeInventory(); return; }
            if (slot < 0 || slot >= Math.min(PAGE_SIZE, regions.size())) return;
            ProtectedRegion region = regions.get(slot);
            openFlags(player, manager, region, 0);
        }
    }

    private final class FlagMenu extends Menu {
        private final UUID viewer;
        private final RegionManager manager;
        private final ProtectedRegion region;
        private final int page;
        private final List<Flag<?>> flags;
        private boolean next;

        FlagMenu(UUID viewer, RegionManager manager, ProtectedRegion region, int page) {
            this.viewer = viewer;
            this.manager = manager;
            this.region = region;
            this.page = Math.max(0, page);
            this.flags = WorldGuard.getInstance().getFlagRegistry().getAll().stream()
                    .sorted(Comparator.comparing(Flag::getName)).toList();
            create(54, Component.text(Msg.sc("Region Flags: " + region.getId()), Msg.NEUTRAL));
            render();
        }

        private void render() {
            int start = page * PAGE_SIZE;
            int end = Math.min(flags.size(), start + PAGE_SIZE);
            next = end < flags.size();
            for (int i = start; i < end; i++) {
                Flag<?> flag = flags.get(i);
                Object value = region.getFlag(flag);
                Material material = flag instanceof StateFlag
                        ? (value == StateFlag.State.ALLOW ? Material.LIME_DYE : value == StateFlag.State.DENY ? Material.RED_DYE : Material.GRAY_DYE)
                        : Material.PAPER;
                String current = value == null ? "Not set (WorldGuard default)" : String.valueOf(value);
                set(i - start, Items.of(material, flag.getName(), Msg.NEUTRAL,
                        description(flag.getName()),
                        "Current value: " + current,
                        flag instanceof StateFlag ? "Click to cycle: Default / Allow / Deny" : "Click to enter a new value"));
            }
            set(45, Items.of(Material.ARROW, "Previous", Msg.NEUTRAL));
            set(49, Items.of(Material.BARRIER, "Close", Msg.BAD));
            if (next) set(53, Items.of(Material.ARROW, "Next", Msg.NEUTRAL));
            set(47, Items.of(Material.BOOK, "Region: " + region.getId(), Msg.NEUTRAL,
                    "All registered WorldGuard flags are listed",
                    "Unset flags use WorldGuard's default behavior"));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            if (!player.getUniqueId().equals(viewer) || !player.hasPermission(ADMIN) && !player.hasPermission("Owner.Core")) {
                player.closeInventory();
                return;
            }
            int slot = event.getRawSlot();
            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 45 && page > 0) { openFlags(player, manager, region, page - 1); return; }
            if (slot == 53 && next) { openFlags(player, manager, region, page + 1); return; }
            if (slot < 0 || slot >= PAGE_SIZE) return;
            int index = page * PAGE_SIZE + slot;
            if (index >= flags.size()) return;
            Flag<?> flag = flags.get(index);
            if (flag instanceof StateFlag stateFlag) {
                StateFlag.State current = region.getFlag(stateFlag);
                StateFlag.State updated = current == null ? StateFlag.State.ALLOW
                        : current == StateFlag.State.ALLOW ? StateFlag.State.DENY : null;
                setFlag(stateFlag, updated);
                Msg.ok(player, "Updated {f} to {v}", "f", flag.getName(), "v", updated == null ? "default" : updated.name().toLowerCase(Locale.ROOT));
                render();
                return;
            }
            if (flag instanceof StringFlag || flag instanceof IntegerFlag || flag instanceof DoubleFlag
                    || flag instanceof BooleanFlag || flag instanceof SetFlag) {
                Msg.info(player, "Enter a new value for {f}. Type cancel to stop.", "f", flag.getName());
                chatInput.request(player, input -> {
                    try {
                        Object value;
                        if (flag instanceof StringFlag) value = input;
                        else if (flag instanceof IntegerFlag) value = Integer.parseInt(input);
                        else if (flag instanceof DoubleFlag) value = Double.parseDouble(input);
                        else if (flag instanceof BooleanFlag) {
                            if (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                            value = Boolean.parseBoolean(input);
                        } else value = new LinkedHashSet<>(Arrays.asList(input.split(",")));
                        setFlag(flag, value);
                        Msg.ok(player, "Updated {f}", "f", flag.getName());
                        MenuListener.open(player, new FlagMenu(viewer, manager, region, page));
                    } catch (RuntimeException ex) {
                        Msg.err(player, "Invalid value for {f}", "f", flag.getName());
                        MenuListener.open(player, new FlagMenu(viewer, manager, region, page));
                    }
                });
                player.closeInventory();
            } else {
                Msg.info(player, "This flag type needs a specialized value. Use the WorldGuard flag command for this flag.");
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void setFlag(Flag flag, Object value) {
            region.setFlag(flag, value);
            try {
                manager.save();
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not save WorldGuard region " + region.getId() + ": " + ex.getMessage());
            }
        }
    }
}
