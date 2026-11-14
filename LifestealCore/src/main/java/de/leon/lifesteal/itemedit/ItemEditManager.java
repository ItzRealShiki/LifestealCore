package de.leon.lifesteal.itemedit;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.gui.Items;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.util.Msg;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ItemEditManager implements Listener {

    private static final String PERMISSION = "Admin.Core";

    private static final NamespacedKey RIGHT_CMD = NamespacedKey.fromString("itemedit:right_click_cmd");
    private static final NamespacedKey RIGHT_EFFECT = NamespacedKey.fromString("itemedit:right_click_effect");
    private static final NamespacedKey LEFT_CMD = NamespacedKey.fromString("itemedit:left_click_cmd");
    private static final NamespacedKey LEFT_EFFECT = NamespacedKey.fromString("itemedit:left_click_effect");
    private static final NamespacedKey SLOT_TYPE = NamespacedKey.fromString("itemedit:slot_type");
    private static final NamespacedKey SLOT_CMD = NamespacedKey.fromString("itemedit:slot_cmd");
    private static final NamespacedKey SLOT_EFFECT = NamespacedKey.fromString("itemedit:slot_effect");

    private final LifestealCore plugin;
    private long slotPeriod = 20L;
    private org.bukkit.scheduler.BukkitTask task;

    public ItemEditManager(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        slotPeriod = Math.max(1, plugin.getConfig().getLong("itemedit.slot-check-seconds", 1)) * 20L;
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSlots, slotPeriod, slotPeriod);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private static Registry<Enchantment> enchantments() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    private static Registry<PotionEffectType> effects() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
    }

    private static Component format(String text) {
        return Msg.legacy(Msg.sc(text));
    }

    public TabExecutor command() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (!player.hasPermission(PERMISSION)) {
                    Msg.err(player, "You do not have permission to do this");
                    return true;
                }
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    Msg.err(player, "You must hold an item in your hand");
                    return true;
                }
                if (args.length == 0) {
                    new EditMenu().open(player);
                    return true;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "enchant" -> enchant(player, item, args);
                    case "name" -> name(player, item, args);
                    case "lore" -> lore(player, item, args);
                    case "hide" -> hide(player, item, args);
                    case "action" -> action(player, item, args);
                    default -> Msg.err(player, "Unknown subcommand. Use /it to open the menu");
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (!sender.hasPermission(PERMISSION)) return List.of();
                return EcoCommands.filter(tab(args), args[args.length - 1]);
            }
        };
    }

    private void enchant(Player player, ItemStack item, String[] args) {
        if (args.length < 3) {
            Msg.info(player, "Usage: /it enchant <enchantment> <level 1-255>");
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(args[1].toLowerCase(Locale.ROOT));
        Enchantment enchantment = key == null ? null : enchantments().get(key);
        if (enchantment == null) {
            Msg.err(player, "Invalid enchantment name");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            Msg.err(player, "Invalid number");
            return;
        }
        if (level < 1 || level > 255) {
            Msg.err(player, "Level must be between 1 and 255");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        Msg.ok(player, "Enchantment added");
    }

    private void name(Player player, ItemStack item, String[] args) {
        if (args.length < 2) {
            Msg.info(player, "Usage: /it name <name>");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(format(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
        item.setItemMeta(meta);
        Msg.ok(player, "Item name updated");
    }

    private void lore(Player player, ItemStack item, String[] args) {
        if (args.length < 3) {
            Msg.info(player, "Usage: /it lore <add|set> [row] <text>");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("add")) {
            lore.add(format(String.join(" ", Arrays.copyOfRange(args, 2, args.length))));
            meta.lore(lore);
            item.setItemMeta(meta);
            Msg.ok(player, "Lore line added");
        } else if (mode.equals("set")) {
            if (args.length < 4) {
                Msg.info(player, "Usage: /it lore set <row> <text>");
                return;
            }
            int index;
            try {
                index = Integer.parseInt(args[2]) - 1;
            } catch (NumberFormatException e) {
                Msg.err(player, "Invalid row number");
                return;
            }
            if (index < 0 || index > 50) {
                Msg.err(player, "Row must be between 1 and 51");
                return;
            }
            while (lore.size() <= index) lore.add(Component.empty());
            lore.set(index, format(String.join(" ", Arrays.copyOfRange(args, 3, args.length))));
            meta.lore(lore);
            item.setItemMeta(meta);
            Msg.ok(player, "Lore line {n} updated", "n", index + 1);
        } else {
            Msg.info(player, "Usage: /it lore <add|set> ...");
        }
    }

    private void hide(Player player, ItemStack item, String[] args) {
        if (args.length < 2) {
            Msg.info(player, "Usage: /it hide <enchants|lore|name|all>");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "enchants" -> {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                Msg.ok(player, "Enchantments hidden");
            }
            case "lore" -> {
                meta.lore(new ArrayList<>());
                Msg.ok(player, "Lore cleared");
            }
            case "name" -> {
                meta.displayName(Component.text(" "));
                Msg.ok(player, "Name hidden");
            }
            case "all" -> {
                meta.addItemFlags(ItemFlag.values());
                Msg.ok(player, "All attributes and flags hidden");
            }
            default -> {
                Msg.err(player, "Invalid hide option");
                return;
            }
        }
        item.setItemMeta(meta);
    }

    private void action(Player player, ItemStack item, String[] args) {
        if (args.length < 3) {
            Msg.info(player, "Usage: /it action <right|left|slot> ...");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String target = args[1].toLowerCase(Locale.ROOT);

        if (target.equals("right") || target.equals("left")) {
            String type = args[2].toLowerCase(Locale.ROOT);
            if (!type.equals("command") && !type.equals("effect")) {
                Msg.err(player, "Choose command or effect");
                return;
            }
            if (args.length < 4) {
                Msg.info(player, "Usage: /it action {t} <command|effect> <value>", "t", target);
                return;
            }
            NamespacedKey key = target.equals("right")
                    ? (type.equals("command") ? RIGHT_CMD : RIGHT_EFFECT)
                    : (type.equals("command") ? LEFT_CMD : LEFT_EFFECT);
            String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            pdc.set(key, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
            Msg.ok(player, "{t}-click {k} set to: {v}", "t", Msg.sc(target), "k", Msg.sc(type), "v", value);
        } else if (target.equals("slot")) {
            if (args.length < 5) {
                Msg.info(player, "Usage: /it action slot <slot> <command|effect> <value>");
                return;
            }
            String slot = args[2].toLowerCase(Locale.ROOT);
            String type = args[3].toLowerCase(Locale.ROOT);
            String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            if (type.equals("command")) pdc.set(SLOT_CMD, PersistentDataType.STRING, value);
            else if (type.equals("effect")) pdc.set(SLOT_EFFECT, PersistentDataType.STRING, value);
            else {
                Msg.err(player, "Choose command or effect");
                return;
            }
            pdc.set(SLOT_TYPE, PersistentDataType.STRING, slot);
            item.setItemMeta(meta);
            Msg.ok(player, "Slot ({s}) {k} set to: {v}", "s", Msg.sc(slot), "k", Msg.sc(type), "v", value);
        } else {
            Msg.err(player, "Invalid action type");
        }
    }

    private List<String> tab(String[] args) {
        if (args.length == 1) return List.of("enchant", "name", "lore", "hide", "action");
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "hide" -> List.of("enchants", "lore", "name", "all");
                case "lore" -> List.of("add", "set");
                case "action" -> List.of("right", "left", "slot");
                case "enchant" -> {
                    List<String> list = new ArrayList<>();
                    for (Enchantment e : enchantments()) list.add(e.getKey().getKey());
                    yield list;
                }
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (sub.equals("enchant")) return List.of("1", "5", "10", "255");
            if (sub.equals("action")) {
                return args[1].equalsIgnoreCase("slot")
                        ? List.of("helmet", "chestplate", "leggings", "boots", "offhand", "mainhand")
                        : List.of("command", "effect");
            }
        }
        if (sub.equals("action")) {
            boolean slot = args[1].equalsIgnoreCase("slot");
            if (args.length == 4 && slot) return List.of("command", "effect");
            boolean effect = (args.length == 4 && !slot && args[2].equalsIgnoreCase("effect"))
                    || (args.length == 5 && slot && args[3].equalsIgnoreCase("effect"));
            if (effect) {
                List<String> list = new ArrayList<>();
                for (PotionEffectType t : effects()) list.add(t.getKey().getKey());
                return list;
            }
        }
        return List.of();
    }

    private void applyEffect(Player player, String effect, int defaultTicks, boolean slotEffect) {
        try {
            String[] parts = effect.trim().split("\\s+");
            NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
            PotionEffectType type = key == null ? null : effects().get(key);
            if (type == null) return;
            int duration = parts.length > 1 ? Integer.parseInt(parts[1]) * 20 : defaultTicks;
            int amplifier = parts.length > 2 ? Math.max(0, Integer.parseInt(parts[2]) - 1) : 0;
            PotionEffect current = player.getPotionEffect(type);

            if (slotEffect && current != null && current.getAmplifier() == amplifier
                    && current.getDuration() > slotPeriod + 10) return;
            player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, true));
        } catch (RuntimeException ignored) {

        }
    }

    private static void runCommand(Player player, String cmd) {
        player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        var pdc = item.getPersistentDataContainer();
        Action action = event.getAction();
        Player player = event.getPlayer();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        String cmd = pdc.get(right ? RIGHT_CMD : LEFT_CMD, PersistentDataType.STRING);
        String eff = pdc.get(right ? RIGHT_EFFECT : LEFT_EFFECT, PersistentDataType.STRING);
        if (cmd != null) runCommand(player, cmd);
        if (eff != null) applyEffect(player, eff, 200, false);
    }

    private void checkSlots() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            check(player, inv.getHelmet(), "helmet");
            check(player, inv.getChestplate(), "chestplate");
            check(player, inv.getLeggings(), "leggings");
            check(player, inv.getBoots(), "boots");
            check(player, inv.getItemInOffHand(), "offhand");
            check(player, inv.getItemInMainHand(), "mainhand");
        }
    }

    private void check(Player player, ItemStack item, String slot) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        var pdc = item.getPersistentDataContainer();
        String required = pdc.get(SLOT_TYPE, PersistentDataType.STRING);
        if (required == null || !required.equalsIgnoreCase(slot)) return;
        String eff = pdc.get(SLOT_EFFECT, PersistentDataType.STRING);
        if (eff != null) applyEffect(player, eff, (int) slotPeriod * 2 + 40, true);
        String cmd = pdc.get(SLOT_CMD, PersistentDataType.STRING);
        if (cmd != null) runCommand(player, cmd);
    }

    private static final class EditMenu extends Menu {

        EditMenu() {
            create(27, Component.text(Msg.sc("ItemEdit menu"), NamedTextColor.DARK_GRAY));
            set(10, Items.of(Material.ENCHANTED_BOOK, "Enchantments", Msg.GOOD, "Click for info on /it enchant"));
            set(11, Items.of(Material.NAME_TAG, "Item name", Msg.GOOD, "Click for info on /it name"));
            set(13, Items.of(Material.WRITABLE_BOOK, "Item lore", Msg.GOOD, "Click for info on /it lore"));
            set(15, Items.of(Material.BARRIER, "Hide attributes", Msg.GOOD, "Click for info on /it hide"));
            set(16, Items.of(Material.COMPASS, "Item actions", Msg.GOOD, "Click for info on /it action"));
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            String usage = switch (event.getRawSlot()) {
                case 10 -> "Use: /it enchant <enchantment> <level>";
                case 11 -> "Use: /it name <name>";
                case 13 -> "Use: /it lore <add|set> [row] <text>";
                case 15 -> "Use: /it hide <enchants|lore|name|all>";
                case 16 -> "Use: /it action <right|left|slot> ...";
                default -> null;
            };
            if (usage == null) return;
            player.closeInventory();
            Msg.info(player, usage);
        }
    }
}
