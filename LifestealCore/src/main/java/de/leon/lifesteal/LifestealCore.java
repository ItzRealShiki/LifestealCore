package de.leon.lifesteal;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

import de.leon.lifesteal.admin.AdminManager;
import de.leon.lifesteal.admin.OwnerOpCommands;
import de.leon.lifesteal.board.BoardManager;
import de.leon.lifesteal.booster.BoosterManager;
import de.leon.lifesteal.bounty.BountyCommand;
import de.leon.lifesteal.bounty.BountyManager;
import de.leon.lifesteal.combat.AltChecker;
import de.leon.lifesteal.combat.CombatCommands;
import de.leon.lifesteal.combat.CombatListener;
import de.leon.lifesteal.combat.CombatManager;
import de.leon.lifesteal.combat.Forcefield;
import de.leon.lifesteal.combat.HeartManager;
import de.leon.lifesteal.combat.ProtectionManager;
import de.leon.lifesteal.combat.WorldGuardForcefield;
import de.leon.lifesteal.data.DataManager;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.data.Migration;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.economy.EconomyManager;
import de.leon.lifesteal.gui.ChatInput;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.itemedit.ItemEditManager;
import de.leon.lifesteal.fastplace.SettingsCommand;
import de.leon.lifesteal.fastplace.FastPlaceManager;
import de.leon.lifesteal.diagnostics.LeonDiagnostics;
import de.leon.lifesteal.diagnostics.LagMonitor;
import de.leon.lifesteal.diagnostics.ServerControlCommand;
import de.leon.lifesteal.mod.ModerationManager;
import de.leon.lifesteal.manage.ManageManager;
import de.leon.lifesteal.rank.RankManager;
import de.leon.lifesteal.shop.ShopManager;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public final class LifestealCore extends JavaPlugin {

    private static LifestealCore instance;

    private boolean worldGuardFlag;
    private Database database;
    private DataManager data;
    private ChatInput chatInput;
    private EconomyManager economy;
    private ShopManager shop;
    private BoosterManager boosters;
    private BoardManager board;
    private CombatManager combat;
    private ProtectionManager protection;
    private BountyManager bounty;
    private LeonDiagnostics diagnostics;
    private LagMonitor lagMonitor;
    private AdminManager adminManager;
    private boolean ranksLoaded;
    private RankManager rankManager;
    private boolean moderationLoaded;
    private boolean itemEditLoaded;
    private ItemEditManager itemEdit;
    private FastPlaceManager fastPlace;
    private ManageManager manage;

    public static LifestealCore get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false).debug(false);
        PacketEvents.getAPI().load();

        if (getConfig().getBoolean("modules.combat", true) && getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardForcefield.registerFlag(getLogger());
                worldGuardFlag = WorldGuardForcefield.ready();
            } catch (Throwable t) {
                getLogger().warning("WorldGuard found, but the arena flag could not be registered: " + t);
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        PacketEvents.getAPI().init();
        saveDefaultConfig();
        Msg.load(getConfig());

        database = new Database(this);
        try {
            database.createTables();
            data = new DataManager(this, database);
            adminManager = new AdminManager(this, database);
            data.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not connect to the database! Check database.yml. Disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Migration.run(this, database, data);
        listen(data);

        fastPlace = new FastPlaceManager(this);
        command("settings", new SettingsCommand(fastPlace));

        for (Player p : Bukkit.getOnlinePlayers()) {
            var address = p.getAddress();
            data.loadNow(p.getUniqueId(), p.getName(), address == null ? null : address.getAddress());
        }

        listen(new MenuListener(this));
        chatInput = new ChatInput(this);
        listen(chatInput);

        economy = new EconomyManager(this, database);
        economy.pruneLog();
        EcoCommands eco = new EcoCommands(this, economy);
        command("pay", eco.pay());
        command("bal", eco.bal());
        command("eco", eco.eco());

        if (isModuleEnabled("shop")) {
            shop = new ShopManager(this, database, economy);
            try {
                shop.load();
                command("shop", shop.shopCommand());
                command("sell", shop.sellCommand());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Shop could not be loaded", e);
                shop = null;
                disabled("shop", "sell");
            }
        } else {
            disabled("shop", "sell");
        }

        if (isModuleEnabled("boosters")) {
            boosters = new BoosterManager(this, database);
            boosters.start();
            listen(boosters);
            command("booster", boosters.boosterCommand());
            command("sbooster", boosters.serverBoosterCommand());
        } else {
            disabled("booster", "sbooster");
        }

        if (isModuleEnabled("combat")) {
            Forcefield forcefield = Forcefield.NONE;
            if (worldGuardFlag) {
                forcefield = new WorldGuardForcefield(this::getConfig);
            } else {
                getLogger().info("WorldGuard not found: combat works, but there are no arenas (forcefield).");
            }
            combat = new CombatManager(this, forcefield);
            protection = new ProtectionManager(this);
            HeartManager hearts = new HeartManager(this, database);
            CombatListener listener = new CombatListener(this, combat, protection, hearts, new AltChecker(this));
            data.setLoginBlocker(listener::loginBlock);
            listen(listener);
            CombatCommands cmds = new CombatCommands(this, combat, protection, hearts);
            command("combat", cmds.combat());
            command("protection", cmds.protection());
            command("heart", cmds.heart());
            combat.start();
            protection.start();
            for (Player p : Bukkit.getOnlinePlayers()) hearts.apply(p);
        } else {
            disabled("combat", "protection", "heart");
        }

        if (isModuleEnabled("bounty")) {
            bounty = new BountyManager(this, database, economy);
            try {
                bounty.start();
                listen(bounty);
                command("bounty", new BountyCommand(bounty));
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Bounties could not be loaded", e);
                bounty = null;
                disabled("bounty");
            }
        } else {
            disabled("bounty");
        }

        if (isModuleEnabled("scoreboard")) {
            board = new BoardManager(this);
            listen(board);
            board.start();
        }

        if (isModuleEnabled("ranks") && getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            rankManager = RankManager.create(this);
            if (rankManager != null) {
                command("rank", rankManager.command());
                ranksLoaded = true;
            } else disabled("rank");
        } else {
            if (isModuleEnabled("ranks")) getLogger().info("LuckPerms not found: /rank is disabled.");
            disabled("rank");
        }

        if (isModuleEnabled("moderation")) {
            ModerationManager mod = new ModerationManager(this);
            listen(mod);
            command("gm", mod.gamemode());
            command("tp", mod.teleport());
            command("support", mod.support());
            command("godmode", mod.godmode());
            command("request", mod.request());
            moderationLoaded = true;
        } else {
            disabled("gm", "tp", "support", "godmode", "request");
        }

        if (isModuleEnabled("manage")) {
            manage = new ManageManager(this);
            listen(manage);
            command("stats", manage);
            command("invsee", manage);
            command("endersee", manage);
        } else {
            disabled("stats", "invsee", "endersee");
        }

        if (isModuleEnabled("itemedit")) {
            itemEdit = new ItemEditManager(this);
            listen(itemEdit);
            itemEdit.start();
            command("itemedit", itemEdit.command());
            itemEditLoaded = true;
        } else {
            disabled("itemedit");
        }

        command("lsc", adminCommand());
        command("admin", adminManager.adminCommand());
        command("permission", adminManager.permissionCommand());
        command("perm", adminManager.permissionCommand());
        command("ranks", adminManager.ranksCommand());
        listen(new OwnerOpCommands(this));
        diagnostics = new LeonDiagnostics(this);
        lagMonitor = new LagMonitor(this);
        lagMonitor.start();
        listen(lagMonitor);
        command("lag", lagMonitor);
        command("plugin", diagnostics);
        command("console-error", diagnostics);
        ServerControlCommand serverControl = new ServerControlCommand(this);
        command("restart", serverControl);
        command("stop", serverControl);
        getLogger().info("LifestealCore enabled (" + database.type().name().toLowerCase() + " database).");
    }

    @Override
    public void onDisable() {
        if (diagnostics != null) diagnostics.shutdown();
        if (manage != null) manage.shutdown();
        if (lagMonitor != null) lagMonitor.shutdown();
        if (fastPlace != null) fastPlace.shutdown();
        MenuListener.closeAll();
        if (combat != null) combat.shutdown();
        if (protection != null) protection.stop();
        if (boosters != null) boosters.stop();
        if (bounty != null) bounty.stop();
        if (itemEdit != null) itemEdit.stop();
        if (board != null) board.stop();
        if (economy != null) economy.shutdown();
        if (data != null) data.saveAllNow();
        if (database != null) database.close();
        PacketEvents.getAPI().terminate();
    }

    public boolean isModuleEnabled(String module) {
        return getConfig().getBoolean("modules." + module, true);
    }

    private void listen(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void command(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command /" + name + " is missing in plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter((sender, command, alias, args) -> {
            List<String> own = executor.onTabComplete(sender, command, alias, args);
            if (own != null && !own.isEmpty()) return own;
            return complete(sender, name, args);
        });
    }

    private void disabled(String... names) {
        for (String name : names) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) continue;
            cmd.setExecutor((sender, command, label, args) -> {
                Msg.err(sender, "This feature is disabled on this server");
                return true;
            });
            cmd.setTabCompleter((sender, command, label, args) -> List.of());
        }
    }

    public void sound(Player player, String key) {
        if (player == null) return;
        String sound = getConfig().getString("sounds." + key, "");
        if (sound == null || sound.isBlank()) return;
        try {
            player.playSound(player.getLocation(), sound.toLowerCase(java.util.Locale.ROOT), 1f, 1f);
        } catch (RuntimeException ignored) {

        }
    }

    private TabExecutor adminCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission("Admin.Core")) {
                    Msg.err(sender, "You do not have permission to do this");
                    return true;
                }
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    String module = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
                    if (module.equals("all")) {
                        boolean ok = true;
                        for (String name : List.of("messages", "shop", "boosters", "scoreboard", "bounty", "itemedit", "ranks", "manage")) {
                            ok &= reloadModule(sender, name, false);
                        }
                        Msg.info(sender, ok ? "Alle sicher reloadbaren Module wurden neu geladen." : "Reload abgeschlossen; Details stehen in der Konsole.");
                    } else {
                        boolean ok = reloadModule(sender, module, true);
                        if (ok) Msg.ok(sender, "Reload abgeschlossen: {m}", "m", module);
                    }
                    return true;
                }
                if (args.length > 0 && args[0].equalsIgnoreCase("save")) {
                    data.flush();
                    economy.flushLog();
                    Msg.ok(sender, "All data is being saved");
                    return true;
                }
                Msg.info(sender, "LifestealCore {v} | database: {d} | players in memory: {n}",
                        "v", getPluginMeta().getVersion(), "d", database.type().name().toLowerCase(),
                        "n", Bukkit.getOnlinePlayers().size());
                Msg.info(sender, "Usage: /lsc <reload|save>");
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 && sender.hasPermission("Admin.Core")
                        ? EcoCommands.filter(List.of("reload", "save", "info"), args[0]) : List.of();
            }
        };
    }


    private boolean reloadModule(CommandSender sender, String module, boolean report) {
        try {
            switch (module) {
                case "messages" -> Msg.load(getConfig());
                case "shop" -> {
                    if (shop != null) shop = null;
                    if (isModuleEnabled("shop")) {
                        ShopManager next = new ShopManager(this, database, economy);
                        next.load();
                        shop = next;
                        command("shop", shop.shopCommand());
                        command("sell", shop.sellCommand());
                    } else {
                        disabled("shop", "sell");
                    }
                }
                case "boosters" -> {
                    if (boosters != null) {
                        boosters.stop();
                        HandlerList.unregisterAll(boosters);
                        boosters = null;
                    }
                    if (isModuleEnabled("boosters")) {
                        boosters = new BoosterManager(this, database);
                        boosters.start();
                        listen(boosters);
                        command("booster", boosters.boosterCommand());
                        command("sbooster", boosters.serverBoosterCommand());
                    } else disabled("booster", "sbooster");
                }
                case "scoreboard" -> {
                    if (board != null) {
                        board.stop();
                        HandlerList.unregisterAll(board);
                        board = null;
                    }
                    if (isModuleEnabled("scoreboard")) {
                        board = new BoardManager(this);
                        listen(board);
                        board.start();
                    }
                }
                case "bounty" -> {
                    if (bounty != null) {
                        bounty.stop();
                        HandlerList.unregisterAll(bounty);
                        bounty = null;
                    }
                    if (isModuleEnabled("bounty")) {
                        bounty = new BountyManager(this, database, economy);
                        bounty.start();
                        listen(bounty);
                        command("bounty", new BountyCommand(bounty));
                    } else disabled("bounty");
                }
                case "itemedit" -> {
                    if (itemEdit != null) {
                        itemEdit.stop();
                        HandlerList.unregisterAll(itemEdit);
                        itemEdit = null;
                    }
                    itemEditLoaded = false;
                    if (isModuleEnabled("itemedit")) {
                        itemEdit = new ItemEditManager(this);
                        listen(itemEdit);
                        itemEdit.start();
                        command("itemedit", itemEdit.command());
                        itemEditLoaded = true;
                    } else disabled("itemedit");
                }
                case "ranks" -> {
                    ranksLoaded = false;
                    if (isModuleEnabled("ranks") && getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                        rankManager = RankManager.create(this);
                        if (rankManager != null) {
                            command("rank", rankManager.command());
                            ranksLoaded = true;
                        } else disabled("rank");
                    } else disabled("rank");
                }
                case "manage" -> {
                    if (manage != null) {
                        manage.shutdown();
                        HandlerList.unregisterAll(manage);
                        manage = null;
                    }
                    if (isModuleEnabled("manage")) {
                        manage = new ManageManager(this);
                        listen(manage);
                        command("stats", manage);
                        command("invsee", manage);
                        command("endersee", manage);
                    } else disabled("stats", "invsee", "endersee");
                }
                case "economy" -> {
                    // EconomyManager is shared by pay, shop, boosters and bounties.
                    // Replacing it live would invalidate those references; its settings are read dynamically.
                }
                case "combat", "moderation", "database" -> {
                    if (report) Msg.err(sender, "{m} benötigt einen Server-Neustart, damit keine laufenden Zustände verloren gehen.", "m", module);
                    return false;
                }
                default -> {
                    if (report) Msg.err(sender, "Unbekanntes Modul. Nutze: messages, shop, boosters, scoreboard, bounty, itemedit, ranks, economy, combat, moderation, manage, database, all");
                    return false;
                }
            }
            if (report) Msg.ok(sender, "Modul {m} wurde sauber neu geladen.", "m", module);
            return true;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Could not reload module " + module, ex);
            if (report) Msg.err(sender, "Reload von {m} fehlgeschlagen. Siehe Konsole.", "m", module);
            return false;
        }
    }

    private List<String> complete(CommandSender sender, String command, String[] args) {
        if (args.length == 0) return List.of();
        String current = args[args.length - 1];
        List<String> options = List.of();
        switch (command.toLowerCase(Locale.ROOT)) {
            case "lsc" -> { if (args.length == 1) options = List.of("reload", "save", "info"); else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) options = List.of("all", "messages", "shop", "boosters", "scoreboard", "bounty", "itemedit", "ranks", "economy", "combat", "moderation", "manage", "database"); }
            case "admin" -> {
                if (args.length == 1) options = List.of("logs", "check", "approve", "reject");
                else if (args.length == 2 && args[0].equalsIgnoreCase("check")) return playerNames(sender, current);
            }
            case "rank" -> {
                if (args.length == 1) options = List.of("editor", "player");
                else if (args.length == 2 && args[0].equalsIgnoreCase("player")) return playerNames(sender, current);
            }
            case "ranks" -> { if (args.length == 1) options = List.of("editor"); }
            case "permission", "perm" -> { if (args.length == 1) options = luckPermsGroups(); }
            case "pay" -> {
                if (args.length == 1) options = List.of("money", "gems");
                else if (args.length == 2) return playerNames(sender, current);
            }
            case "bal" -> { if (args.length == 1) return playerNames(sender, current); }
            case "eco" -> {
                if (args.length == 1) options = List.of("money", "gems");
                else if (args.length == 2) options = List.of("give", "remove", "check");
                else if (args.length == 3) return playerNames(sender, current);
                else if (args.length == 4 && !args[1].equalsIgnoreCase("check")) options = List.of("100", "1k", "1.5k", "1m");
            }
            case "booster" -> {
                if (args.length == 1) options = List.of("gui", "give");
                else if (args.length == 2 && args[0].equalsIgnoreCase("give")) return playerNames(sender, current);
            }
            case "sbooster" -> { if (args.length == 1) options = List.of("skip"); }
            case "combat" -> { if (args.length == 1) return playerNames(sender, current); }
            case "gm" -> {
                if (args.length == 1) options = List.of("survival", "creative", "adventure", "spectator");
                else if (args.length == 2) return playerNames(sender, current);
            }
            case "tp" -> { if (args.length <= 2) return playerNames(sender, current); }
            case "heart" -> {
                if (args.length == 1) options = List.of("check", "unban");
                else if (args.length == 2) return playerNames(sender, current);
            }
            case "support", "godmode", "protection" -> {
                if (args.length == 1) options = List.of("on", "off", "toggle");
            }
            case "request" -> {
                if (args.length == 1) options = List.of("accept", "deny");
                else if (args.length == 2) return playerNames(sender, current);
            }
            case "shop" -> { if (args.length == 1) options = List.of("editor"); }
            default -> { }
        }
        String prefix = current.toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> playerNames(CommandSender sender, String current) {
        String prefix = current.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> luckPermsGroups() {
        var provider = getServer().getServicesManager().getRegistration(net.luckperms.api.LuckPerms.class);
        if (provider == null) return List.of();
        return provider.getProvider().getGroupManager().getLoadedGroups().stream()
                .map(net.luckperms.api.model.group.Group::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public AdminManager adminManager() { return adminManager; }

    public Database database() { return database; }

    public DataManager data() {
        return data;
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    public EconomyManager economy() {
        return economy;
    }

    public EconomyManager getEconomyManager() {
        return economy;
    }

    public BoosterManager boosters() {
        return boosters;
    }

    public CombatManager combat() {
        return combat;
    }

    public BountyManager bounty() {
        return bounty;
    }

    public ShopManager shop() { return shop; }
    public BoardManager board() { return board; }
    public boolean ranksLoaded() { return ranksLoaded; }
    public boolean moderationLoaded() { return moderationLoaded; }
    public boolean itemEditLoaded() { return itemEditLoaded; }
}
