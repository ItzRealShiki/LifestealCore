package de.leon.lifesteal.diagnostics;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.api.TeamProvider;
import de.leon.lifesteal.booster.BoosterType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

public final class LeonDiagnostics implements Listener, org.bukkit.command.TabExecutor {
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final LifestealCore core;
    private final LeonConsoleLog logCapture;
    private final LeonUpdateChecker updates;
    private final LeonUpdateDownloader downloader;

    private enum Screen { MAIN, PLUGIN, RELOAD_CONFIRM, MODULES, MODULE, BOOSTERS, BOOSTER_DETAIL, UPDATES, UPDATE, DEPENDENCIES, CONSOLE, LOG_DETAIL }

    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;
        private final Screen screen;
        private final int page;
        private final String selected;
        private final String filter;
        private final Map<Integer, String> actions = new HashMap<>();
        private MenuHolder(Screen screen, int page, String selected, String filter) {
            this.screen = screen; this.page = page; this.selected = selected; this.filter = filter;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    public LeonDiagnostics(LifestealCore core) {
        this.core = core;
        this.logCapture = new LeonConsoleLog();
        this.updates = new LeonUpdateChecker(core);
        this.downloader = new LeonUpdateDownloader(core);
        Bukkit.getPluginManager().registerEvents(this, core);
        updates.checkAll();
        Bukkit.getScheduler().runTaskTimer(core, updates::checkAll, 20L * 60L * 10L, 20L * 60L * 60L * 6L);
    }

    public void shutdown() { logCapture.close(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("Owner.Core")) {
            sender.sendMessage(text("you do not have permission", NamedTextColor.RED));
            return true;
        }
        if (command.getName().equalsIgnoreCase("plugin") && args.length > 0) {
            if (!args[0].equalsIgnoreCase("reload") || args.length != 2) {
                sender.sendMessage(text("usage: /pl reload <plugin>", NamedTextColor.YELLOW));
                return true;
            }
            Plugin target = Bukkit.getPluginManager().getPlugin(args[1]);
            LeonPluginOperations.Result result = LeonPluginOperations.reload(core, target);
            sender.sendMessage(text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("this command can only be used in-game", NamedTextColor.RED));
            return true;
        }
        if (command.getName().equalsIgnoreCase("console-error")) openConsole(player, 0, "ALL");
        else openMain(player, 0);
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("Owner.Core")) {
            player.closeInventory();
            player.sendMessage(text("you do not have permission", NamedTextColor.RED));
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        String[] parts = action.split(":", 2);
        String type = parts[0];
        String value = parts.length > 1 ? parts[1] : "";
        switch (type) {
            case "plugin" -> openPlugin(player, value);
            case "main" -> openMain(player, Math.max(0, Integer.parseInt(value)));
            case "updates" -> openUpdates(player, 0);
            case "modules" -> openModules(player);
            case "module" -> openModule(player, value);
            case "boosters" -> openBoosters(player);
            case "booster" -> openBoosterDetail(player, value);
            case "dependencies" -> openDependencies(player, holder.selected);
            case "update" -> openUpdate(player, value);
            case "reload-confirm" -> openReloadConfirm(player, value);
            case "reload" -> { LeonPluginOperations.Result result = LeonPluginOperations.reload(core, Bukkit.getPluginManager().getPlugin(value)); player.sendMessage(text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED)); openPlugin(player, value); }
            case "download-update" -> downloadUpdate(player, value);
            case "console" -> openConsole(player, Math.max(0, Integer.parseInt(value)), holder.filter);
            case "filter" -> openConsole(player, 0, value);
            case "log" -> openLogDetail(player, Long.parseLong(value), 0);
            case "logpage" -> openLogDetail(player, Long.parseLong(holder.selected), Math.max(0, Integer.parseInt(value)));
            case "refresh" -> {
                updates.checkAll();
                if (holder.screen == Screen.CONSOLE) openConsole(player, holder.page, holder.filter);
                else if (holder.screen == Screen.PLUGIN) openPlugin(player, holder.selected);
                else if (holder.screen == Screen.UPDATES) openUpdates(player, holder.page);
                else openMain(player, holder.page);
            }
            case "back" -> {
                switch (holder.screen) {
                    case PLUGIN, MODULES, UPDATES -> openMain(player, 0);
                    case MODULE, BOOSTERS -> openModules(player);
                    case BOOSTER_DETAIL -> openBoosters(player);
                    case UPDATE, DEPENDENCIES, RELOAD_CONFIRM -> openPlugin(player, holder.selected);
                    case LOG_DETAIL -> openConsole(player, 0, holder.filter);
                    default -> openMain(player, 0);
                }
            }
            case "close" -> player.closeInventory();
            case "url" -> {
                if (!value.isBlank()) player.sendMessage(Component.text("ᴏᴘᴇɴ: ").append(Component.text(value, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(value))));
            }
            default -> { }
        }
    }

    private void openMain(Player player, int page) {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        java.util.Arrays.sort(plugins, Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        int pages = Math.max(1, (plugins.length + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, pages - 1);
        MenuHolder holder = holder(Screen.MAIN, current, "", "ALL");
        Inventory inv = create(holder, "ᴘʟᴜɢɪɴ ᴍᴀɴᴀɢᴇᴍᴇɴᴛ");
        int start = current * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, plugins.length); i++) {
            Plugin plugin = plugins[i];
            Status status = pluginStatus(plugin);
            List<Component> lore = new ArrayList<>();
            lore.add(text("version: " + plugin.getPluginMeta().getVersion(), NamedTextColor.GRAY));
            lore.add(text(status.label, status.color));
            LeonUpdateChecker.Update update = updates.get(plugin.getName());
            if (update != null && update.updateAvailable()) lore.add(text("latest: " + update.latest(), NamedTextColor.YELLOW));
            lore.add(text("click for details", NamedTextColor.DARK_GRAY));
            set(inv, i - start, dye(status), plugin.getName(), lore);
            holder.actions.put(i - start, "plugin:" + plugin.getName());
        }
        set(inv, 45, Material.ARROW, "ᴘʀᴇᴠɪᴏᴜs", List.of());
        holder.actions.put(45, "main:" + Math.max(0, current - 1));
        set(inv, 46, Material.CLOCK, "ᴘᴀɢᴇ " + (current + 1) + " / " + pages, List.of());
        set(inv, 47, Material.ARROW, "ɴᴇxᴛ", List.of());
        holder.actions.put(47, "main:" + Math.min(pages - 1, current + 1));
        set(inv, 48, Material.CLOCK, "ᴜᴘᴅᴀᴛᴇ ᴄᴇɴᴛᴇʀ", List.of(text("check plugin releases", NamedTextColor.GRAY)));
        holder.actions.put(48, "updates");
        set(inv, 49, Material.BOOK, "ʟɪғᴇsᴛᴇᴀʟᴄᴏʀᴇ ᴍᴏᴅᴜʟᴇs", List.of(text("view module health", NamedTextColor.GRAY)));
        holder.actions.put(49, "modules");
        set(inv, 50, Material.REDSTONE, "ᴄᴏɴsᴏʟᴇ ᴇʀʀᴏʀs", List.of(text("warnings and errors", NamedTextColor.GRAY)));
        holder.actions.put(50, "console:0");
        set(inv, 51, Material.COMPASS, "ʀᴇғʀᴇsʜ", List.of(text("refresh status and update checks", NamedTextColor.GRAY)));
        holder.actions.put(51, "refresh");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of());
        holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void openPlugin(Player player, String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null) { openMain(player, 0); return; }
        MenuHolder holder = holder(Screen.PLUGIN, 0, plugin.getName(), "ALL");
        Inventory inv = create(holder, "ᴘʟᴜɢɪɴ: " + plugin.getName());
        Status status = pluginStatus(plugin);
        set(inv, 20, dye(status), plugin.getName(), List.of(
                text("version: " + plugin.getPluginMeta().getVersion(), NamedTextColor.GRAY),
                text(status.label, status.color)));
        set(inv, 22, Material.BOOK, "ᴅᴇᴘᴇɴᴅᴇɴᴄɪᴇs", List.of(text("required and optional plugins", NamedTextColor.GRAY)));
        holder.actions.put(22, "dependencies:" + plugin.getName());
        LeonUpdateChecker.Update update = updates.get(plugin.getName());
        if (update != null && update.updateAvailable()) {
            set(inv, 24, Material.ORANGE_DYE, "ᴜᴘᴅᴀᴛᴇ ᴀᴠᴀɪʟᴀʙʟᴇ", List.of(
                    text("installed: " + update.installed(), NamedTextColor.GRAY),
                    text("latest: " + update.latest(), NamedTextColor.YELLOW),
                    text("priority: " + update.priority().name(), priorityColor(update.priority()))));
            holder.actions.put(24, "update:" + plugin.getName());
        } else if (update != null) {
            set(inv, 24, Material.LIME_DYE, "ᴜᴘ ᴛᴏ ᴅᴀᴛᴇ", List.of(text("no newer release detected", NamedTextColor.GRAY)));
        } else {
            set(inv, 24, Material.GRAY_DYE, "ᴜᴘᴅᴀᴛᴇ ɴᴏᴛ ᴄʜᴇᴄᴋᴇᴅ", List.of(text("no release source configured or check unavailable", NamedTextColor.GRAY)));
        }
        if (plugin == core) {
            set(inv, 31, Material.CHEST, "ᴍᴏᴅᴜʟᴇ ᴅɪᴀɢɴᴏsᴛɪᴄs", List.of(text("inspect LifestealCore modules", NamedTextColor.GRAY)));
            holder.actions.put(31, "modules");
        } else {
            List<String> dependents = LeonPluginOperations.hardDependents(plugin);
            List<Component> reloadLore = new ArrayList<>();
            reloadLore.add(text("disable and enable through PluginManager", NamedTextColor.GRAY));
            reloadLore.add(text("not a full classloader unload", NamedTextColor.YELLOW));
            if (!dependents.isEmpty()) reloadLore.add(text("blocked: " + String.join(", ", dependents), NamedTextColor.RED));
            else reloadLore.add(text("may leave tasks or resources behind", NamedTextColor.YELLOW));
            set(inv, 30, dependents.isEmpty() ? Material.ORANGE_DYE : Material.BARRIER, "ʀᴇʟᴏᴀᴅ ᴘʟᴜɢɪɴ", reloadLore);
            if (dependents.isEmpty()) holder.actions.put(30, "reload-confirm:" + plugin.getName());
        }
        set(inv, 49, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(49, "back");
        set(inv, 52, Material.COMPASS, "ʀᴇғʀᴇsʜ", List.of()); holder.actions.put(52, "refresh");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void openModules(Player player) {
        MenuHolder holder = holder(Screen.MODULES, 0, "", "ALL");
        Inventory inv = create(holder, "ʟɪғᴇsᴛᴇᴀʟᴄᴏʀᴇ ᴍᴏᴅᴜʟᴇs");
        String[][] modules = {
                {"economy", "ᴇᴄᴏɴᴏᴍʏ"}, {"shop", "sʜᴏᴘ"}, {"boosters", "ʙᴏᴏsᴛᴇʀs"},
                {"combat", "ᴄᴏᴍʙᴀᴛ / ʜᴇᴀʀᴛs"}, {"bounty", "ʙᴏᴜɴᴛʏ"}, {"scoreboard", "sᴄᴏʀᴇʙᴏᴀʀᴅ"},
                {"ranks", "ʀᴀɴᴋs"}, {"moderation", "ᴍᴏᴅᴇʀᴀᴛɪᴏɴ"}, {"itemedit", "ɪᴛᴇᴍ ᴇᴅɪᴛ"}, {"manage", "ᴍᴀɴᴀɢᴇ"}, {"team", "ᴛᴇᴀᴍ ᴀᴘɪ"}
        };
        int slot = 10;
        for (String[] module : modules) {
            Status status = moduleStatus(module[0]);
            set(inv, slot, dye(status), module[1], List.of(text(status.label, status.color), text("click for details", NamedTextColor.DARK_GRAY)));
            holder.actions.put(slot, "module:" + module[0]);
            slot += slot % 9 == 7 ? 3 : 1;
        }
        set(inv, 49, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(49, "back");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private record ModuleCommand(String command, String usage, String description) { }

    private void openModule(Player player, String key) {
        MenuHolder holder = holder(Screen.MODULE, 0, key, "ALL");
        Inventory inv = create(holder, "ᴍᴏᴅᴜʟᴇ: " + key);
        Status status = moduleStatus(key);
        set(inv, 4, dye(status), key, List.of(text("status: " + status.label, status.color)));
        set(inv, 13, Material.BOOK, "ᴍᴏᴅᴜʟᴇ ᴅᴇsᴄʀɪᴘᴛɪᴏɴ", List.of(text(moduleExplanation(key), NamedTextColor.GRAY)));

        List<ModuleCommand> commands = moduleCommands(key);
        int[] slots = {19, 21, 23, 25, 28, 30, 32, 34, 37, 39, 41, 43};
        if (commands.isEmpty()) {
            set(inv, 31, Material.GRAY_DYE, "ɴᴏ ᴅɪʀᴇᴄᴛ ᴄᴏᴍᴍᴀɴᴅs", List.of(
                    text(key.equalsIgnoreCase("scoreboard") ? "the scoreboard runs automatically" :
                            key.equalsIgnoreCase("team") ? "this module exposes an api service, not commands" :
                                    "this module has no standalone commands", NamedTextColor.GRAY)));
        } else {
            for (int i = 0; i < commands.size() && i < slots.length; i++) {
                ModuleCommand info = commands.get(i);
                set(inv, slots[i], Material.COMMAND_BLOCK, LeonSmallCaps.of(info.command()), List.of(
                        text("ᴜsᴀɢᴇ: " + info.usage(), NamedTextColor.YELLOW),
                        text("ᴘᴇʀᴍɪssɪᴏɴ: " + commandPermission(info.command()), NamedTextColor.AQUA),
                        text(info.description(), NamedTextColor.GRAY)));
            }
        }

        if (key.equalsIgnoreCase("team")) {
            RegisteredServiceProvider<TeamProvider> service = Bukkit.getServicesManager().getRegistration(TeamProvider.class);
            set(inv, 46, service == null ? Material.RED_DYE : Material.LIME_DYE, "ᴛᴇᴀᴍ ᴘʀᴏᴠɪᴅᴇʀ",
                    List.of(text(service == null ? "no TeamProvider registered" : "provider: " + service.getPlugin().getName(), service == null ? NamedTextColor.RED : NamedTextColor.GREEN)));
        }
        if (key.equalsIgnoreCase("boosters")) {
            set(inv, 46, Material.BREWING_STAND, "ʙᴏᴏsᴛᴇʀ ᴛʏᴘᴇs", List.of(text("view booster categories and effects", NamedTextColor.GRAY)));
            holder.actions.put(46, "boosters");
        }
        set(inv, 49, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(49, "back");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private List<ModuleCommand> moduleCommands(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "economy" -> List.of(
                    new ModuleCommand("/pay", "/pay <money|gems> <player> <amount>", "Send money or gems to another player."),
                    new ModuleCommand("/bal", "/bal [player]", "View your own or another player's balance."),
                    new ModuleCommand("/eco", "/eco <money|gems> <give|remove|check> <player> [amount]", "Administer balances or check an account. Requires Admin.Core."));
            case "shop" -> List.of(
                    new ModuleCommand("/shop", "/shop [editor]", "Open the item shop; admins can open the editor."),
                    new ModuleCommand("/sell", "/sell", "Open the menu for selling supported items."));
            case "boosters" -> List.of(
                    new ModuleCommand("/booster", "/booster [gui|give <player> <booster> [amount]]", "Open the booster menu or give a booster."),
                    new ModuleCommand("/sbooster", "/sbooster skip", "Skip the server booster timer. Requires Admin.Core."));
            case "combat" -> List.of(
                    new ModuleCommand("/combat", "/combat <player> <seconds>", "Put a player into combat for the specified duration. Requires Admin.Core."),
                    new ModuleCommand("/protection", "/protection [on|off|toggle]", "Enable, disable, or toggle personal protection."),
                    new ModuleCommand("/heart", "/heart <check [player] | unban <player>>", "Check heart-related player data or remove a heart ban."));
            case "bounty" -> List.of(
                    new ModuleCommand("/bounty", "/bounty", "Open the bounty menu and place or manage bounties."));
            case "ranks" -> List.of(
                    new ModuleCommand("/rank", "/rank <editor|player> [player]", "Open the rank editor or view a player's rank menu. Requires Admin.Core."),
                    new ModuleCommand("/ranks", "/ranks editor", "Open the rank-priority editor. Requires Admin.Core."));
            case "moderation" -> List.of(
                    new ModuleCommand("/gm", "/gm <mode> [player]", "Change your or another player's game mode. Requires Admin.Core."),
                    new ModuleCommand("/tp", "/tp <player> [player]", "Teleport to a player or teleport one player to another."),
                    new ModuleCommand("/support", "/support [on|off|toggle]", "Toggle support mode. Requires Support.Core."),
                    new ModuleCommand("/godmode", "/godmode [on|off|toggle]", "Toggle invulnerability. Requires Admin.Core."),
                    new ModuleCommand("/request", "/request", "Send a support request to the staff team."));
            case "itemedit" -> List.of(
                    new ModuleCommand("/itemedit", "/itemedit", "Edit supported properties of the item in your hand."));
            case "manage" -> List.of(
                    new ModuleCommand("/stats", "/stats [player]", "View your own or another player's statistics."),
                    new ModuleCommand("/stats editor", "/stats <player> editor", "Edit money, gems, hearts, kills, deaths, playtime and experience. Admin only."),
                    new ModuleCommand("/invsee", "/invsee <player>", "View a player's inventory. Admin can edit; Support is read-only."),
                    new ModuleCommand("/endersee", "/endersee <player>", "View a player's ender chest. Admin can edit; Support is read-only."));
            default -> List.of();
        };
    }

    private String commandPermission(String command) {
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "/stats" -> "none for viewing";
            case "/stats editor" -> "Admin.Core";
            case "/invsee", "/endersee" -> "Admin.Core (edit) / Support.Core (view)";
            case "/eco", "/combat", "/heart", "/rank", "/ranks" -> "Admin.Core";
            case "/gm", "/godmode" -> "Admin.Core";
            case "/tp" -> "Admin.Core / Support.Core";
            case "/support" -> "Support.Core";
            case "/sbooster" -> "Admin.Core";
            case "/booster" -> "Admin.Core for give; none for menu";
            case "/shop" -> "Admin.Core for editor; none for shop";
            case "/itemedit" -> "Admin.Core";
            case "/bounty" -> "Admin.Core for removal; bounty.use for players";
            default -> "none";
        };
    }

    private void openBoosters(Player player) {
        MenuHolder holder = holder(Screen.BOOSTERS, 0, "boosters", "ALL");
        Inventory inv = create(holder, "ʟɪғᴛᴇsᴛᴇᴀʟᴄᴏʀᴇ: ʙᴏᴏsᴛᴇʀs");
        int slot = 0;
        for (BoosterType booster : BoosterType.values()) {
            boolean available = core.boosters() != null;
            set(inv, slot, available ? Material.LIME_DYE : Material.RED_DYE, booster.getDisplayName(), List.of(
                    text("category: " + booster.getCategory().getPrefix(), NamedTextColor.GRAY),
                    text(available ? "registered in booster module" : "booster module unavailable", available ? NamedTextColor.GREEN : NamedTextColor.RED),
                    text("click for details", NamedTextColor.DARK_GRAY)));
            holder.actions.put(slot, "booster:" + booster.name());
            slot++;
        }
        set(inv, 49, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(49, "back");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void openBoosterDetail(Player player, String key) {
        BoosterType booster;
        try { booster = BoosterType.valueOf(key); }
        catch (IllegalArgumentException ex) { openBoosters(player); return; }
        boolean available = core.boosters() != null;
        MenuHolder holder = holder(Screen.BOOSTER_DETAIL, 0, key, "ALL");
        Inventory inv = create(holder, "ʙᴏᴏsᴛᴇʀ: " + booster.getDisplayName());
        set(inv, 20, available ? Material.LIME_DYE : Material.RED_DYE, booster.getDisplayName(), List.of(
                text(available ? "module available" : "module unavailable", available ? NamedTextColor.GREEN : NamedTextColor.RED),
                text("category: " + booster.getCategory().getPrefix(), NamedTextColor.GRAY),
                text("rarity: " + booster.getRarity().getName(), NamedTextColor.GRAY)));
        set(inv, 22, Material.PAPER, "ᴇғғᴇᴄᴛ ᴅᴇᴛᴀɪʟs", List.of(
                text("money multiplier: x" + booster.getMoneyMultiplier(), NamedTextColor.GRAY),
                text("gems multiplier: x" + booster.getGemsMultiplier(), NamedTextColor.GRAY),
                text("amplifier: " + booster.getAmplifier(), NamedTextColor.GRAY)));
        set(inv, 49, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(49, "back");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void openUpdates(Player player, int page) {
        List<LeonUpdateChecker.Update> available = updates.all().stream().filter(LeonUpdateChecker.Update::updateAvailable).toList();
        MenuHolder holder = holder(Screen.UPDATES, page, "", "ALL");
        Inventory inv = create(holder, "ᴜᴘᴅᴀᴛᴇ ᴄᴇɴᴛᴇʀ");
        int pages = Math.max(1, (available.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, pages - 1);
        holder = holder(Screen.UPDATES, current, "", "ALL");
        inv = create(holder, "ᴜᴘᴅᴀᴛᴇ ᴄᴇɴᴛᴇʀ");
        int start = current * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, available.size()); i++) {
            LeonUpdateChecker.Update update = available.get(i);
            NamedTextColor color = priorityColor(update.priority());
            set(inv, i - start, Material.ORANGE_DYE, update.plugin(), List.of(
                    text("installed: " + update.installed(), NamedTextColor.GRAY),
                    text("latest: " + update.latest(), NamedTextColor.YELLOW),
                    text("priority: " + update.priority().name(), color), text("click for release notes", NamedTextColor.DARK_GRAY)));
            holder.actions.put(i - start, "update:" + update.plugin());
        }
        if (available.isEmpty()) set(inv, 22, Material.LIME_DYE, "ɴᴏ ᴜᴘᴅᴀᴛᴇs ғᴏᴜɴᴅ", List.of(text("no newer configured release detected", NamedTextColor.GRAY)));
        navigation(inv, holder, current, pages, "updates");
        set(inv, 49, Material.CLOCK, "ʟᴀsᴛ ᴄʜᴇᴄᴋ", List.of(text(updates.lastCheck(), NamedTextColor.GRAY)));
        set(inv, 52, Material.COMPASS, "ʀᴇғʀᴇsʜ", List.of()); holder.actions.put(52, "refresh");
        set(inv, 53, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(53, "back");
        player.openInventory(inv);
    }

    private void openUpdate(Player player, String pluginName) {
        LeonUpdateChecker.Update update = updates.get(pluginName);
        if (update == null) { openUpdates(player, 0); return; }
        MenuHolder holder = holder(Screen.UPDATE, 0, pluginName, "ALL");
        Inventory inv = create(holder, "ᴜᴘᴅᴀᴛᴇ: " + pluginName);
        set(inv, 20, Material.ORANGE_DYE, "ᴠᴇʀsɪᴏɴs", List.of(text("installed: " + update.installed(), NamedTextColor.GRAY), text("latest: " + update.latest(), NamedTextColor.YELLOW)));
        set(inv, 22, Material.PAPER, "ᴘʀɪᴏʀɪᴛʏ: " + update.priority().name(), List.of(text("estimated from release notes", priorityColor(update.priority()))));
        List<Component> notes = new ArrayList<>();
        String[] lines = update.changelog().replace('\r', ' ').split("\n");
        for (int i = 0; i < Math.min(8, lines.length); i++) if (!lines[i].isBlank()) notes.add(text(trim(lines[i], 70), NamedTextColor.GRAY));
        if (notes.isEmpty()) notes.add(text("no release notes", NamedTextColor.GRAY));
        set(inv, 24, Material.WRITABLE_BOOK, "ᴄʜᴀɴɢᴇʟᴏɢ", notes);
        if (!update.url().isBlank()) { set(inv, 31, Material.COMPASS, "ᴏᴘᴇɴ ʀᴇʟᴇᴀsᴇ ᴘᴀɢᴇ", List.of(text(update.url(), NamedTextColor.AQUA))); holder.actions.put(31, "url:" + update.url()); }
        if (!update.downloadUrl().isBlank()) {
            set(inv, 33, Material.EMERALD, "ᴅᴏᴡɴʟᴏᴀᴅ ᴜᴘᴅᴀᴛᴇ", List.of(
                    text("asset: " + update.assetName(), NamedTextColor.GRAY),
                    text("verified plugin identity before staging", NamedTextColor.GREEN),
                    text("restart required to activate", NamedTextColor.YELLOW)));
            holder.actions.put(33, "download-update:" + pluginName);
        } else {
            set(inv, 33, Material.BARRIER, "ɴᴏ ᴅᴏᴡɴʟᴏᴀᴅᴀʙʟᴇ ᴊᴀʀ", List.of(text("release has no matching JAR asset", NamedTextColor.RED)));
        }
        set(inv, 49, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(49, "back");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void openReloadConfirm(Player player, String pluginName) {
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null || target == core) { openMain(player, 0); return; }
        List<String> dependents = LeonPluginOperations.hardDependents(target);
        MenuHolder holder = holder(Screen.RELOAD_CONFIRM, 0, pluginName, "ALL");
        Inventory inv = create(holder, "ᴄᴏɴғɪʀᴍ ᴘʟᴜɢɪɴ ʀᴇʟᴏᴀᴅ");
        set(inv, 22, Material.ORANGE_DYE, "ʀᴇʟᴏᴀᴅ: " + pluginName, List.of(
                text("plugin will be disabled and enabled", NamedTextColor.GRAY),
                text("not a complete classloader unload", NamedTextColor.YELLOW),
                text("temporary lag or broken resources possible", NamedTextColor.YELLOW),
                text("server restart remains safer", NamedTextColor.RED)));
        if (!dependents.isEmpty()) {
            set(inv, 31, Material.BARRIER, "ʀᴇʟᴏᴀᴅ ʙʟᴏᴄᴋᴇᴅ", List.of(text(String.join(", ", dependents), NamedTextColor.RED)));
        } else {
            set(inv, 29, Material.LIME_DYE, "ᴄᴏɴғɪʀᴍ ʀᴇʟᴏᴀᴅ", List.of(text("perform plugin disable / enable", NamedTextColor.YELLOW)));
            holder.actions.put(29, "reload:" + pluginName);
        }
        set(inv, 33, Material.ARROW, "ᴄᴀɴᴄᴇʟ", List.of()); holder.actions.put(33, "back");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void downloadUpdate(Player player, String pluginName) {
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        LeonUpdateChecker.Update update = updates.get(pluginName);
        if (target == null || update == null) { openUpdates(player, 0); return; }
        player.sendMessage(text("Downloading and validating " + pluginName + "...", NamedTextColor.YELLOW));
        downloader.stage(target, update, result -> {
            player.sendMessage(text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (player.isOnline()) openUpdate(player, pluginName);
        });
    }

    private void openDependencies(Player player, String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) { openMain(player, 0); return; }
        MenuHolder holder = holder(Screen.DEPENDENCIES, 0, pluginName, "ALL");
        Inventory inv = create(holder, "ᴅᴇᴘᴇɴᴅᴇɴᴄɪᴇs: " + pluginName);
        int slot = 10;
        List<String> hard = plugin.getDescription().getDepend();
        List<String> soft = plugin.getDescription().getSoftDepend();
        if (hard.isEmpty() && soft.isEmpty()) set(inv, 22, Material.LIME_DYE, "ɴᴏ ᴅᴇᴘᴇɴᴅᴇɴᴄɪᴇs", List.of());
        for (String dependency : hard) {
            boolean ok = Bukkit.getPluginManager().isPluginEnabled(dependency);
            set(inv, slot++, ok ? Material.LIME_DYE : Material.RED_DYE, dependency,
                    List.of(text(ok ? "required dependency found" : "required dependency missing", ok ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
        for (String dependency : soft) {
            boolean ok = Bukkit.getPluginManager().isPluginEnabled(dependency);
            set(inv, slot++, ok ? Material.LIME_DYE : Material.ORANGE_DYE, dependency,
                    List.of(text(ok ? "optional dependency found" : "optional dependency not installed", ok ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        }
        set(inv, 49, Material.ARROW, "ʙᴀᴄᴋ", List.of()); holder.actions.put(49, "back");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void openConsole(Player player, int page, String filter) {
        List<LeonConsoleLog.Entry> entries = filteredLogs(filter);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, pages - 1);
        MenuHolder holder = holder(Screen.CONSOLE, current, "", filter);
        Inventory inv = create(holder, "ᴄᴏɴsᴏʟᴇ ᴇʀʀᴏʀs");
        int start = current * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, entries.size()); i++) {
            LeonConsoleLog.Entry entry = entries.get(i);
            boolean error = entry.kind() == LeonConsoleLog.Kind.ERROR;
            set(inv, i - start, error ? Material.RED_DYE : Material.ORANGE_DYE,
                    trim(entry.source(), 28), List.of(text(trim(entry.message(), 65), NamedTextColor.GRAY),
                            text(TIME.format(entry.time()), NamedTextColor.DARK_GRAY), text(error ? "error" : "warning", error ? NamedTextColor.RED : NamedTextColor.YELLOW)));
            holder.actions.put(i - start, "log:" + entry.id());
        }
        if (entries.isEmpty()) set(inv, 22, Material.LIME_DYE, "ɴᴏ ᴇʀʀᴏʀs ᴏʀ ᴡᴀʀɴɪɴɢs", List.of(text("no captured entries", NamedTextColor.GRAY)));
        set(inv, 45, Material.ARROW, "ᴘʀᴇᴠɪᴏᴜs", List.of()); holder.actions.put(45, "console:" + Math.max(0, current - 1));
        set(inv, 46, Material.PAPER, "ᴀʟʟ", List.of(text("entries: " + filteredLogs("ALL").size(), NamedTextColor.GRAY)));
        holder.actions.put(46, "filter:ALL");
        set(inv, 47, Material.RED_DYE, "ᴇʀʀᴏʀs", List.of(text("entries: " + filteredLogs("ERRORS").size(), NamedTextColor.GRAY)));
        holder.actions.put(47, "filter:ERRORS");
        set(inv, 48, Material.ORANGE_DYE, "ᴡᴀʀɴɪɴɢs", List.of(text("entries: " + filteredLogs("WARNINGS").size(), NamedTextColor.GRAY)));
        holder.actions.put(48, "filter:WARNINGS");
        set(inv, 49, Material.CLOCK, "ᴘᴀɢᴇ " + (current + 1) + " / " + pages, List.of(text("captured entries: " + entries.size(), NamedTextColor.GRAY)));
        set(inv, 50, Material.ARROW, "ɴᴇxᴛ", List.of()); holder.actions.put(50, "console:" + Math.min(pages - 1, current + 1));
        set(inv, 52, Material.COMPASS, "ʀᴇғʀᴇsʜ", List.of()); holder.actions.put(52, "refresh");
        set(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of()); holder.actions.put(53, "close");
        player.openInventory(inv);
    }

    private void openLogDetail(Player player, long id, int page) {
        LeonConsoleLog.Entry entry = LeonConsoleLog.find(id);
        if (entry == null) { openConsole(player, 0, "ALL"); return; }
        MenuHolder holder = holder(Screen.LOG_DETAIL, page, Long.toString(id), entry.kind().name().equals("ERROR") ? "ERRORS" : "WARNINGS");
        Inventory inv = create(holder, "ᴄᴏɴsᴏʟᴇ ᴅᴇᴛᴀɪʟs");
        set(inv, 4, entry.kind() == LeonConsoleLog.Kind.ERROR ? Material.RED_DYE : Material.ORANGE_DYE,
                entry.kind().name(), List.of(text("source: " + trim(entry.source(), 55), NamedTextColor.GRAY),
                        text("time: " + TIME.format(entry.time()), NamedTextColor.GRAY), text(trim(entry.message(), 90), NamedTextColor.WHITE)));
        List<String> traceLines = new ArrayList<>();
        if (!entry.stacktrace().isBlank()) traceLines.addAll(List.of(entry.stacktrace().split("\n")));
        else traceLines.add("No stacktrace was attached to this log record.");
        int detailPageSize = 36;
        int pages = Math.max(1, (traceLines.size() + detailPageSize - 1) / detailPageSize);
        int current = Math.min(page, pages - 1);
        holder = holder(Screen.LOG_DETAIL, current, Long.toString(id), entry.kind().name().equals("ERROR") ? "ERRORS" : "WARNINGS");
        inv = create(holder, "ᴄᴏɴsᴏʟᴇ ᴅᴇᴛᴀɪʟs");
        set(inv, 4, entry.kind() == LeonConsoleLog.Kind.ERROR ? Material.RED_DYE : Material.ORANGE_DYE,
                entry.kind().name(), List.of(text("source: " + trim(entry.source(), 55), NamedTextColor.GRAY),
                        text("time: " + TIME.format(entry.time()), NamedTextColor.GRAY), text(trim(entry.message(), 90), NamedTextColor.WHITE)));
        int start = current * detailPageSize;
        for (int i = start; i < Math.min(start + detailPageSize, traceLines.size()); i++) {
            set(inv, 9 + (i - start), Material.PAPER, trim(traceLines.get(i), 80), List.of(text("log detail line " + (i + 1), NamedTextColor.DARK_GRAY)));
        }
        set(inv, 45, Material.ARROW, "ᴘʀᴇᴠɪᴏᴜs", List.of()); holder.actions.put(45, "logpage:" + Math.max(0, current - 1));
        set(inv, 49, Material.CLOCK, "ᴘᴀɢᴇ " + (current + 1) + " / " + pages, List.of());
        set(inv, 50, Material.ARROW, "ɴᴇxᴛ", List.of()); holder.actions.put(50, "logpage:" + Math.min(pages - 1, current + 1));
        set(inv, 53, Material.BARRIER, "ʙᴀᴄᴋ", List.of()); holder.actions.put(53, "back");
        player.openInventory(inv);
    }

    private List<LeonConsoleLog.Entry> filteredLogs(String filter) {
        List<LeonConsoleLog.Entry> all = LeonConsoleLog.snapshot();
        if ("ERRORS".equals(filter)) all.removeIf(e -> e.kind() != LeonConsoleLog.Kind.ERROR);
        else if ("WARNINGS".equals(filter)) all.removeIf(e -> e.kind() != LeonConsoleLog.Kind.WARNING);
        all.sort(Comparator.comparing(LeonConsoleLog.Entry::time).reversed());
        return all;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("Owner.Core")) return List.of();
        if (command.getName().equalsIgnoreCase("plugin") && args.length == 1) {
            return List.of("reload").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (command.getName().equalsIgnoreCase("plugin") && args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(Bukkit.getPluginManager().getPlugins())
                    .map(Plugin::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return List.of();
    }

    private Status pluginStatus(Plugin plugin) {
        if (!plugin.isEnabled()) return new Status("disabled", NamedTextColor.RED, Material.RED_DYE);
        LeonUpdateChecker.Update update = updates.get(plugin.getName());
        if (update != null && update.updateAvailable()) return new Status("update available", NamedTextColor.YELLOW, Material.ORANGE_DYE);
        for (String optional : plugin.getDescription().getSoftDepend()) {
            if (!Bukkit.getPluginManager().isPluginEnabled(optional)) return new Status("optional dependency missing", NamedTextColor.YELLOW, Material.ORANGE_DYE);
        }
        return new Status("no detected issue", NamedTextColor.GREEN, Material.LIME_DYE);
    }

    private Status moduleStatus(String key) {
        boolean configured = core.isModuleEnabled(key);
        if (key.equals("economy")) return core.economy() != null
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status("not initialized", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("shop")) return core.shop() != null
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status(configured ? "not initialized" : "disabled in config", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("scoreboard")) return core.board() != null
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status(configured ? "not initialized" : "disabled in config", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("moderation")) return core.moderationLoaded()
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status(configured ? "not initialized" : "disabled in config", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("itemedit")) return core.itemEditLoaded()
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status(configured ? "not initialized" : "disabled in config", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("ranks")) {
            if (!configured) return new Status("disabled in config", NamedTextColor.RED, Material.RED_DYE);
            if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return new Status("LuckPerms missing", NamedTextColor.YELLOW, Material.ORANGE_DYE);
            return core.ranksLoaded() ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                    : new Status("not initialized", NamedTextColor.RED, Material.RED_DYE);
        }
        if (key.equals("boosters")) return core.boosters() != null
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status("not initialized or disabled", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("combat")) return core.combat() != null
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status("not initialized or disabled", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("bounty")) return core.bounty() != null
                ? new Status("available", NamedTextColor.GREEN, Material.LIME_DYE)
                : new Status("not initialized or disabled", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("team")) {
            RegisteredServiceProvider<TeamProvider> service = Bukkit.getServicesManager().getRegistration(TeamProvider.class);
            return service == null ? new Status("TeamProvider not registered", NamedTextColor.YELLOW, Material.ORANGE_DYE)
                    : new Status("provider registered", NamedTextColor.GREEN, Material.LIME_DYE);
        }
        if (!configured) return new Status("disabled in config", NamedTextColor.RED, Material.RED_DYE);
        if (key.equals("ranks") && !Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))
            return new Status("LuckPerms missing", NamedTextColor.YELLOW, Material.ORANGE_DYE);
        return new Status("enabled in config", NamedTextColor.GREEN, Material.LIME_DYE);
    }

    private String moduleExplanation(String key) {
        return switch (key) {
            case "manage" -> "Player statistics, inventory and ender chest administration. Admin edits; Support views only.";
            case "team" -> "Requires a registered TeamProvider service from a compatible team plugin.";
            case "ranks" -> "Rank commands require the LuckPerms plugin and the ranks module.";
            case "shop" -> "Shop and sell menus are loaded from the server configuration.";
            case "boosters" -> "Contains normal, passive and server booster types.";
            case "economy" -> "Core money and gems manager.";
            default -> "Status is based on module configuration and available runtime components.";
        };
    }

    private void navigation(Inventory inv, MenuHolder holder, int current, int pages, String target) {
        set(inv, 45, Material.ARROW, "ᴘʀᴇᴠɪᴏᴜs", List.of());
        holder.actions.put(45, target + ":" + Math.max(0, current - 1));
        set(inv, 48, Material.CLOCK, "ᴘᴀɢᴇ " + (current + 1) + " / " + pages, List.of());
        set(inv, 50, Material.ARROW, "ɴᴇxᴛ", List.of());
        holder.actions.put(50, target + ":" + Math.min(pages - 1, current + 1));
    }

    private MenuHolder holder(Screen screen, int page, String selected, String filter) {
        return new MenuHolder(screen, page, selected, filter);
    }

    private Inventory create(MenuHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text(title, NamedTextColor.WHITE));
        holder.inventory = inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);
        return inventory;
    }

    private void set(Inventory inventory, int slot, Material material, String name, List<Component> lore) {
        if (slot < 0 || slot >= SIZE) return;
        inventory.setItem(slot, item(material, name, lore));
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name, NamedTextColor.WHITE));
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(LeonSmallCaps.of(Objects.toString(value, "")), color);
    }

    private Material dye(Status status) { return status.material; }
    private NamedTextColor priorityColor(LeonUpdateChecker.Priority priority) {
        return switch (priority) {
            case CRITICAL -> NamedTextColor.RED;
            case IMPORTANT -> NamedTextColor.GOLD;
            case OPTIONAL -> NamedTextColor.GREEN;
        };
    }
    private String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }
    private record Status(String label, NamedTextColor color, Material material) { }
}
