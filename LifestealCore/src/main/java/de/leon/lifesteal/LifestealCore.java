package de.leon.lifesteal;

import de.leon.lifesteal.admin.AdminManager;
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
import de.leon.lifesteal.diagnostics.LeonDiagnostics;
import de.leon.lifesteal.mod.ModerationManager;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

/**
 * LifestealCore: economy, shop, boosters, scoreboard, combat, hearts, ranks, moderation,
 * item editor and bounties in ONE plugin with ONE database.
 */
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
    private AdminManager adminManager;
    private boolean ranksLoaded;
    private boolean moderationLoaded;
    private boolean itemEditLoaded;

    public static LifestealCore get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        // the WorldGuard flag must be registered before WorldGuard loads its regions
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
        saveDefaultConfig();
        Msg.load(getConfig());

        // ---------------------------------------------------------- database
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

        // players that are already online (e.g. after a reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            var address = p.getAddress();
            data.loadNow(p.getUniqueId(), p.getName(), address == null ? null : address.getAddress());
        }

        listen(new MenuListener(this));
        chatInput = new ChatInput(this);
        listen(chatInput);

        // ---------------------------------------------------------- economy (always on)
        economy = new EconomyManager(this, database);
        economy.pruneLog();
        EcoCommands eco = new EcoCommands(this, economy);
        command("pay", eco.pay());
        command("bal", eco.bal());
        command("eco", eco.eco());

        // ---------------------------------------------------------- modules
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
            RankManager ranks = RankManager.create(this);
            if (ranks != null) {
                command("rank", ranks.command());
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

        if (isModuleEnabled("itemedit")) {
            ItemEditManager itemEdit = new ItemEditManager(this);
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
        diagnostics = new LeonDiagnostics(this);
        command("plugin", diagnostics);
        command("console-error", diagnostics);
        getLogger().info("LifestealCore enabled (" + database.type().name().toLowerCase() + " database).");
    }

    @Override
    public void onDisable() {
        if (diagnostics != null) diagnostics.shutdown();
        MenuListener.closeAll(); // the sell menu pays out on close
        if (combat != null) combat.shutdown();
        if (protection != null) protection.stop();
        if (boosters != null) boosters.stop();
        if (board != null) board.stop();
        if (economy != null) economy.flushLog();
        if (data != null) data.saveAllNow();
        if (database != null) database.close();
    }

    // ------------------------------------------------------------------ helpers

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
        cmd.setTabCompleter(executor);
    }

    /** Commands of a module that is switched off. */
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

    /** Plays a sound from config.yml (sounds.<key>). */
    public void sound(Player player, String key) {
        if (player == null) return;
        String sound = getConfig().getString("sounds." + key, "");
        if (sound == null || sound.isBlank()) return;
        try {
            player.playSound(player.getLocation(), sound.toLowerCase(java.util.Locale.ROOT), 1f, 1f);
        } catch (RuntimeException ignored) {
            // invalid sound name in the config
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
                    Msg.load(getConfig());
                    if (shop != null) shop.reloadSellPrices();
                    if (board != null) board.reload();
                    Msg.ok(sender, "Config reloaded (database.yml and modules need a restart)");
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

    // ------------------------------------------------------------------ access for the modules

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

    /** Same method name as in the old EcoExtension, so other plugins (e.g. Jobs) keep working. */
    public EconomyManager getEconomyManager() {
        return economy;
    }

    /** null if the booster module is off. */
    public BoosterManager boosters() {
        return boosters;
    }

    /** null if the combat module is off. */
    public CombatManager combat() {
        return combat;
    }

    /** null if the bounty module is off. */
    public BountyManager bounty() {
        return bounty;
    }

    public ShopManager shop() { return shop; }
    public BoardManager board() { return board; }
    public boolean ranksLoaded() { return ranksLoaded; }
    public boolean moderationLoaded() { return moderationLoaded; }
    public boolean itemEditLoaded() { return itemEditLoaded; }
}
