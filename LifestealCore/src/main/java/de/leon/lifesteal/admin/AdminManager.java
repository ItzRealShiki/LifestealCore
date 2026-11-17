package de.leon.lifesteal.admin;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class AdminManager {
    private static final String ADMIN = "Admin.Core";
    private static final String OWNER = "Owner.Core";
    private static final TextColor AQUA = Msg.NEUTRAL;
    private static final TextColor PURPLE = NamedTextColor.YELLOW;
    private static final TextColor GREEN = Msg.GOOD;
    private static final TextColor RED = Msg.BAD;
    private static final TextColor GOLD = NamedTextColor.YELLOW;

    private final LifestealCore plugin;
    private final Database db;
    private final LuckPerms lp;

    public AdminManager(LifestealCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        this.lp = provider == null ? null : provider.getProvider();
        try {
            db.sync(c -> {
                try (Statement s = c.createStatement()) {
                    String id = db.type() == Database.Type.SQLITE
                            ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
                    s.executeUpdate("CREATE TABLE IF NOT EXISTS " + db.t("admin_logs")
                            + " (id " + id + ",category VARCHAR(24),actor_name VARCHAR(16),target_name VARCHAR(16),action VARCHAR(80),details TEXT,created BIGINT)");
                    s.executeUpdate("CREATE TABLE IF NOT EXISTS " + db.t("admin_log_delete_requests")
                            + " (id " + id + ",log_id BIGINT,requester_name VARCHAR(16),status VARCHAR(16),reviewer_name VARCHAR(16),created BIGINT,reviewed BIGINT DEFAULT 0)");
                    s.executeUpdate("CREATE TABLE IF NOT EXISTS " + db.t("rank_priorities")
                            + " (group_name VARCHAR(64) PRIMARY KEY,priority INT NOT NULL DEFAULT 100)");
                    s.executeUpdate("CREATE TABLE IF NOT EXISTS " + db.t("sell_logs")
                            + " (id " + id + ",player_name VARCHAR(16) NOT NULL,material VARCHAR(64) NOT NULL,item_amount INT NOT NULL,currency VARCHAR(16) NOT NULL,total DOUBLE NOT NULL,created BIGINT NOT NULL)");
                }
                return null;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Admin schema failed: " + e.getMessage());
        }
    }

    private void owners(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(OWNER)) Msg.raw(player, Msg.neutral("Admin: {m}", "m", message));
        }
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN) || sender.hasPermission(OWNER)) return true;
        Msg.err(sender, "You don't have permission to do that");
        return false;
    }

    public void log(String category, Player actor, String target, String action, String details) {
        db.run(c -> {
            try (PreparedStatement q = c.prepareStatement("INSERT INTO " + db.t("admin_logs")
                    + " (category,actor_name,target_name,action,details,created) VALUES (?,?,?,?,?,?)")) {
                q.setString(1, category);
                q.setString(2, actor == null ? "CONSOLE" : actor.getName());
                q.setString(3, target);
                q.setString(4, action);
                q.setString(5, details);
                q.setLong(6, System.currentTimeMillis());
                q.executeUpdate();
            }
        });
        owners("Admin: " + action + (target == null ? "" : " → " + target));
    }

    public void logSell(Player player, org.bukkit.Material material, int amount, Currency currency, double total) {
        if (player == null || material == null || amount <= 0 || currency == null
                || !(total > 0) || !Double.isFinite(total)) return;
        db.run(c -> {
            try (PreparedStatement q = c.prepareStatement("INSERT INTO " + db.t("sell_logs")
                    + " (player_name,material,item_amount,currency,total,created) VALUES (?,?,?,?,?,?)")) {
                q.setString(1, player.getName());
                q.setString(2, material.name());
                q.setInt(3, amount);
                q.setString(4, currency.key());
                q.setDouble(5, total);
                q.setLong(6, System.currentTimeMillis());
                q.executeUpdate();
            }
        });
    }

    public TabExecutor adminCommand() {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("koth")) {
                    if (!admin(sender)) return true;
                    if (args.length > 1 && args[1].equalsIgnoreCase("skip") && plugin.koth() != null) {
                        plugin.koth().skipFromAdmin(sender);
                    } else Msg.info(sender, "Usage: /admin koth skip");
                    return true;
                }
                if (args.length > 0 && (args[0].equalsIgnoreCase("approve") || args[0].equalsIgnoreCase("reject"))) {
                    if (!sender.hasPermission(OWNER)) { Msg.err(sender, "Owner.Core required"); return true; }
                    if (sender instanceof Player player) MenuListener.open(player, new Requests(0));
                    else Msg.info(sender, "Open /admin in-game to review requests.");
                    return true;
                }
                if (!admin(sender)) return true;
                if (!(sender instanceof Player player)) { Msg.err(sender, "Use /admin in-game"); return true; }
                if (args.length == 0) MenuListener.open(player, new Home());
                else if (args[0].equalsIgnoreCase("logs")) MenuListener.open(player, new Logs(null, 0));
                else if (args[0].equalsIgnoreCase("check")) {
                    if (args.length > 1) openPlayerHistory(player, args[1]);
                    else askPlayerHistory(player);
                } else MenuListener.open(player, new Home());
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (!sender.hasPermission(ADMIN) && !sender.hasPermission(OWNER)) return List.of();
                if (args.length == 1) return de.leon.lifesteal.economy.EcoCommands.filter(List.of("logs", "check", "approve", "reject"), args[0]);
                if (args.length == 2 && args[0].equalsIgnoreCase("check"))
                    return de.leon.lifesteal.economy.EcoCommands.onlineNames(sender, args[1]);
                return List.of();
            }
        };
    }

    public TabExecutor permissionCommand() {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player player)) { Msg.err(sender, "Use /permission in-game"); return true; }
                if (args.length == 1 && lp != null) {
                    Group group = lp.getGroupManager().getGroup(args[0]);
                    if (group != null) MenuListener.open(player, new GroupPermissions(group.getName(), 0));
                    else MenuListener.open(player, new Groups(0));
                } else MenuListener.open(player, new Groups(0));
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (args.length == 1 && lp != null && admin(sender))
                    return de.leon.lifesteal.economy.EcoCommands.filter(
                            lp.getGroupManager().getLoadedGroups().stream().map(Group::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(), args[0]);
                return List.of();
            }
        };
    }

    public TabExecutor ranksCommand() {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Use /ranks in-game");
                    return true;
                }
                if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("editor"))) {
                    MenuListener.open(player, new Ranks(0));
                    return true;
                }
                if (args.length == 1 && player.hasPermission(OWNER)
                        && args[0].equalsIgnoreCase(player.getName())) {
                    openSelfRankSelection(player);
                    return true;
                }
                Msg.info(player, "Usage: /ranks [editor|your-username]");
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (args.length != 1) return List.of();
                List<String> options = new ArrayList<>();
                if (admin(sender)) options.add("editor");
                if (sender instanceof Player player && player.hasPermission(OWNER)) options.add(player.getName());
                return de.leon.lifesteal.economy.EcoCommands.filter(options, args[0]);
            }
        };
    }

    private void openSelfRankSelection(Player player) {
        if (lp == null) {
            Msg.err(player, "LuckPerms is unavailable");
            return;
        }
        db.query(c -> {
            Map<String, Integer> priorities = new HashMap<>();
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT group_name,priority FROM " + db.t("rank_priorities"));
                 ResultSet r = q.executeQuery()) {
                while (r.next()) priorities.put(r.getString(1).toLowerCase(Locale.ROOT), r.getInt(2));
            }
            return priorities;
        }).thenAccept(priorities -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission(OWNER)) return;
            List<Group> groups = new ArrayList<>(lp.getGroupManager().getLoadedGroups());
            groups.sort(Comparator
                    .comparingInt((Group group) -> priorities.getOrDefault(group.getName().toLowerCase(Locale.ROOT), 100))
                    .thenComparing(Group::getName, String.CASE_INSENSITIVE_ORDER));
            MenuListener.open(player, new SelfRankSelection(player.getUniqueId(), groups, 0));
        })).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> Msg.err(player, "Could not load ranks: " + error.getMessage()));
            return null;
        });
    }

    private final class SelfRankSelection extends Gui {
        private static final int PAGE_SIZE = 45;
        private final UUID ownerId;
        private final List<Group> groups;
        private final int page;

        SelfRankSelection(UUID ownerId, List<Group> groups, int page) {
            super("SET YOUR OWN RANK");
            this.ownerId = ownerId;
            this.groups = groups;
            this.page = Math.max(0, Math.min(page, Math.max(0, (groups.size() - 1) / PAGE_SIZE)));
            int start = this.page * PAGE_SIZE;
            String current = lp.getUserManager().getUser(ownerId) == null ? ""
                    : lp.getUserManager().getUser(ownerId).getPrimaryGroup();
            for (int i = start; i < Math.min(start + PAGE_SIZE, groups.size()); i++) {
                Group group = groups.get(i);
                Material icon = Material.NAME_TAG;
                ItemStack rankItem = item(icon, group.getFriendlyName(), NamedTextColor.YELLOW,
                        "Group: " + group.getName(),
                        group.getName().equalsIgnoreCase(current) ? "Current rank" : "Click to set this rank");
                if (group.getName().equalsIgnoreCase(current)) {
                    ItemMeta meta = rankItem.getItemMeta();
                    meta.setEnchantmentGlintOverride(true);
                    rankItem.setItemMeta(meta);
                }
                set(i - start, rankItem);
            }
            if (this.page > 0) set(45, item(Material.ARROW, "Previous page", AQUA));
            set(49, item(Material.BARRIER, "Close", RED));
            if ((this.page + 1) * PAGE_SIZE < groups.size()) set(50, item(Material.ARROW, "Next page", AQUA));
        }

        @Override void click(Player player, int slot, boolean right) {
            if (!player.getUniqueId().equals(ownerId) || !player.hasPermission(OWNER)) {
                player.closeInventory();
                return;
            }
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            if (slot == 45 && page > 0) {
                MenuListener.open(player, new SelfRankSelection(ownerId, groups, page - 1));
                return;
            }
            if (slot == 50 && (page + 1) * PAGE_SIZE < groups.size()) {
                MenuListener.open(player, new SelfRankSelection(ownerId, groups, page + 1));
                return;
            }
            int index = page * PAGE_SIZE + slot;
            if (slot < 0 || slot >= PAGE_SIZE || index >= groups.size()) return;
            Group selected = groups.get(index);
            player.closeInventory();
            lp.getUserManager().modifyUser(ownerId, user -> {
                user.data().clear(net.luckperms.api.node.NodeType.INHERITANCE::matches);
                user.data().add(net.luckperms.api.node.types.InheritanceNode.builder(selected.getName()).build());
                user.setPrimaryGroup(selected.getName());
            }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                log("RANKS", player, player.getName(), "Changed own rank", "Group=" + selected.getName());
                Msg.ok(player, "Your rank is now {rank}", "rank", selected.getFriendlyName());
            })).exceptionally(error -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) Msg.err(player, "Could not set rank: " + error.getMessage());
                });
                return null;
            });
        }
    }

    private ItemStack item(Material material, String title, TextColor titleColor, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(Msg.sc(title), titleColor));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(Msg.sc(line), Msg.NEUTRAL));
        if (!lines.isEmpty()) meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private abstract class Gui extends Menu {
        Gui(String title) {
            create(54, Component.text("✦  " + Msg.sc(title), AQUA));
            ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
            for (int i = 0; i < 54; i++) set(i, filler);
            for (int i = 45; i < 54; i++) set(i, item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY));
        }
        @Override public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            if (!player.hasPermission(ADMIN) && !player.hasPermission(OWNER)) { player.closeInventory(); return; }
            if (event.isLeftClick() || event.isRightClick()) click(player, event.getRawSlot(), event.isRightClick());
        }
        abstract void click(Player player, int slot, boolean right);
        void closeButton() { set(49, item(Material.BARRIER, "✖ Close", RED)); }
        void backButton() { set(45, item(Material.ARROW, "← Back", AQUA)); }
        void goHome(Player player) { MenuListener.open(player, new Home()); }
    }

    private final class Home extends Gui {
        Home() {
            super("ADMIN CONTROL");
            set(11, item(Material.REDSTONE_LAMP, "System", AQUA, "Plugins, console and core settings"));
            set(13, item(Material.WRITABLE_BOOK, "Logs", GOLD, "Audit, sell and player history"));
            set(15, item(Material.ANVIL, "Management", GREEN, "Economy, permissions and ranks"));
            set(49, item(Material.BARRIER, "✖ Close", RED));
        }
        @Override void click(Player player, int slot, boolean right) {
            switch (slot) {
                case 11 -> MenuListener.open(player, new CategoryMenu("SYSTEM"));
                case 13 -> MenuListener.open(player, new CategoryMenu("LOGS"));
                case 15 -> MenuListener.open(player, new CategoryMenu("MANAGEMENT"));
                case 49 -> player.closeInventory();
                default -> { }
            }
        }
    }

    private final class CategoryMenu extends Gui {
        private final String category;
        CategoryMenu(String category) {
            super(category + " CATEGORY");
            this.category = category;
            switch (category) {
                case "SYSTEM" -> {
                    set(20, item(Material.COMMAND_BLOCK, "Plugin Status", GREEN, "Plugins and modules"));
                    set(22, item(Material.REDSTONE, "Console Errors", RED, "Errors and warnings"));
                    set(24, item(Material.REPEATER, "Core Settings", AQUA, "Reload and save"));
                    set(26, item(Material.IRON_SWORD, "KOTH Control", RED, "Skip current phase or start the next KOTH"));\n                    set(28, item(Material.BEACON, "Skip Server Booster", AQUA, "Skip the current server booster and trigger the next one"));
                }
                case "LOGS" -> {
                    set(19, item(Material.WRITABLE_BOOK, "Audit Logs", GOLD, "Administrative actions"));
                    set(21, item(Material.GOLD_INGOT, "Sell Logs", GREEN, "Player item sales"));
                    set(23, item(Material.PLAYER_HEAD, "Player History", AQUA, "Search a player's history"));
                    set(25, item(Material.ENDER_CHEST, "Critical Requests", RED, "Owner review required"));
                    set(29, item(Material.BRICKS, "WorldEdit Audit", GOLD, "Persistent block edit history"));
                    set(31, item(Material.SHIELD, "WorldGuard Audit", AQUA, "Blocked PvP events"));
                }
                case "MANAGEMENT" -> {
                    set(19, item(Material.EMERALD, "Economy", GREEN, "Money and gems"));
                    if (plugin.shop() != null) set(21, item(Material.GOLD_INGOT, "Sell Prices", GREEN, "Configure item prices and currencies"));
                    set(23, item(Material.ENCHANTED_BOOK, "Permissions", PURPLE, "LuckPerms groups"));
                    set(25, item(Material.NETHER_STAR, "Rank Priorities", GOLD, "TAB order"));
                }
                default -> { }
            }
            backButton();
            closeButton();
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 45) { goHome(player); return; }
            if (slot == 49) { player.closeInventory(); return; }
            if (category.equals("SYSTEM")) {
                if (slot == 20) player.performCommand("plugin");
                else if (slot == 22) player.performCommand("console-error");
                else if (slot == 24) MenuListener.open(player, new Settings());
                else if (slot == 26) player.performCommand("admin koth skip");\n                else if (slot == 28) player.performCommand("sbooster skip");
            } else if (category.equals("LOGS")) {
                if (slot == 19) MenuListener.open(player, new Logs(null, 0));
                else if (slot == 21) MenuListener.open(player, new SellLogs(0));
                else if (slot == 23) askPlayerHistory(player);
                else if (slot == 25) {
                    if (player.hasPermission(OWNER)) MenuListener.open(player, new Requests(0));
                    else Msg.err(player, "Only Owner.Core can review critical requests");
                } else if (slot == 29) player.performCommand("audit worldedit");
                else if (slot == 31) player.performCommand("audit worldguard");
            } else if (category.equals("MANAGEMENT")) {
                if (slot == 19) MenuListener.open(player, new EconomyMenu());
                else if (slot == 21 && plugin.shop() != null) MenuListener.open(player, new SellPrices(0));
                else if (slot == 23) MenuListener.open(player, new Groups(0));
                else if (slot == 25) MenuListener.open(player, new Ranks(0));
            }
        }
    }

    private void askPlayerHistory(Player player) {
        player.closeInventory();
        Msg.info(player, "Enter a player name in chat. Type cancel to abort.");
        plugin.chatInput().request(player, name -> openPlayerHistory(player, name));
    }

    private void openPlayerHistory(Player player, String name) {
        MenuListener.open(player, new Logs(name, 0));
    }

    private record LogEntry(long id, String category, String actor, String target, String action, String details) { }

    private final class Logs extends Gui {
        private final String target;
        private final int page;
        private List<LogEntry> entries = List.of();
        Logs(String target, int page) {
            super(target == null ? "AUDIT-LOGS" : "PLAYER HISTORY: " + target);
            this.target = target;
            this.page = Math.max(0, page);
            load();
        }
        private void load() {
            db.query(c -> {
                List<LogEntry> list = new ArrayList<>();
                String sql = "SELECT id,category,actor_name,target_name,action,details FROM " + db.t("admin_logs")
                        + (target == null ? "" : " WHERE target_name=? COLLATE NOCASE")
                        + " ORDER BY id DESC LIMIT 45 OFFSET ?";
                try (PreparedStatement q = c.prepareStatement(sql)) {
                    int index = 1;
                    if (target != null) q.setString(index++, target);
                    q.setInt(index, page * 45);
                    try (ResultSet r = q.executeQuery()) {
                        while (r.next()) list.add(new LogEntry(r.getLong(1), r.getString(2), r.getString(3),
                                r.getString(4), r.getString(5), r.getString(6)));
                    }
                }
                return list;
            }).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                entries = list;
                for (int i = 0; i < list.size(); i++) {
                    LogEntry entry = list.get(i);
                    TextColor color = entry.action().startsWith("APPROVED") ? RED : GOLD;
                    set(i, item(Material.PAPER, "#" + entry.id() + "  " + entry.action(), color,
                            "Category: " + entry.category(), "By: " + entry.actor(),
                            "Target: " + Objects.toString(entry.target(), "—"),
                            Objects.toString(entry.details(), ""), "Right-click: request deletion"));
                }
                set(45, item(Material.ARROW, "← Previous", AQUA));
                set(48, item(Material.EMERALD, "Search players", GREEN));
                set(49, item(Material.BARRIER, "Close", RED));
                set(50, item(Material.ARROW, "Next →", AQUA));
                if (Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.hasPermission(OWNER))) set(53, item(Material.ENDER_CHEST, "Deletion Requests", RED));
            }));
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 49) { goHome(player); return; }
            if (slot == 45 && page > 0) { MenuListener.open(player, new Logs(target, page - 1)); return; }
            if (slot == 50 && entries.size() == 45) { MenuListener.open(player, new Logs(target, page + 1)); return; }
            if (slot == 48) { askPlayerHistory(player); return; }
            if (slot == 53 && player.hasPermission(OWNER)) { MenuListener.open(player, new Requests(0)); return; }
            if (slot < 0 || slot >= entries.size() || !right) return;
            LogEntry entry = entries.get(slot);
            if (entry.action().startsWith("APPROVED LOG DELETION")) { Msg.err(player, "Audit entries are protected"); return; }
            if (Bukkit.getOnlinePlayers().stream().noneMatch(p -> p.hasPermission(OWNER))) {
                Msg.err(player, "No Owner.Core member is online – request unavailable"); return;
            }
            db.run(c -> {
                try (PreparedStatement q = c.prepareStatement("INSERT INTO " + db.t("admin_log_delete_requests")
                        + " (log_id,requester_name,status,created) VALUES (?,?,'PENDING',?)", Statement.RETURN_GENERATED_KEYS)) {
                    q.setLong(1, entry.id()); q.setString(2, player.getName()); q.setLong(3, System.currentTimeMillis());
                    q.executeUpdate();
                    try (ResultSet keys = q.getGeneratedKeys()) {
                        long requestId = keys.next() ? keys.getLong(1) : -1;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Msg.ok(player, "Deletion request #" + requestId + " sent");
                            owners("VERY CRITICAL: " + player.getName() + " requested deletion of log #" + entry.id() + " . Request #" + requestId);
                        });
                    }
                }
            });
        }
    }

    private record SellLogEntry(long id, String player, String material, int amount,
                                 String currency, double total, long created) { }

    private final class SellLogs extends Gui {
        private final int page;
        private List<SellLogEntry> entries = List.of();
        SellLogs(int page) {
            super("SELL LOGS");
            this.page = Math.max(0, page);
            load();
        }
        private void load() {
            db.query(c -> {
                List<SellLogEntry> list = new ArrayList<>();
                try (PreparedStatement q = c.prepareStatement("SELECT id,player_name,material,item_amount,currency,total,created FROM "
                        + db.t("sell_logs") + " ORDER BY id DESC LIMIT 45 OFFSET ?")) {
                    q.setInt(1, page * 45);
                    try (ResultSet r = q.executeQuery()) {
                        while (r.next()) list.add(new SellLogEntry(r.getLong(1), r.getString(2), r.getString(3),
                                r.getInt(4), r.getString(5), r.getDouble(6), r.getLong(7)));
                    }
                }
                return list;
            }).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                entries = list;
                java.time.format.DateTimeFormatter format =
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
                for (int i = 0; i < list.size(); i++) {
                    SellLogEntry entry = list.get(i);
                    String date = java.time.Instant.ofEpochMilli(entry.created())
                            .atZone(java.time.ZoneId.systemDefault()).format(format);
                    set(i, item(Material.PAPER, "#" + entry.id() + "  " + entry.player(), GOLD,
                            "Item: " + entry.material(), "Amount: " + entry.amount(),
                            "Payout: " + Num.format(entry.total()) + " " + entry.currency(),
                            "Date: " + date));
                }
                set(45, item(Material.ARROW, "← Previous", AQUA));
                set(49, item(Material.BARRIER, "Back", RED));
                set(50, item(Material.ARROW, "Next →", AQUA));
            }));
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 49) { MenuListener.open(player, new CategoryMenu("LOGS")); return; }
            if (slot == 45 && page > 0) { MenuListener.open(player, new SellLogs(page - 1)); return; }
            if (slot == 50 && entries.size() == 45) MenuListener.open(player, new SellLogs(page + 1));
        }
    }

    private record DeleteRequest(long id, long logId, String requester, long created) { }

    private final class Requests extends Gui {
        private final int page;
        private List<DeleteRequest> requests = List.of();
        Requests(int page) {
            super("CRITICAL DELETION REQUESTS");
            this.page = Math.max(0, page);
            if (!hasOwnerAccess()) return;
            load();
        }
        private boolean hasOwnerAccess() { return true; }
        private void load() {
            db.query(c -> {
                List<DeleteRequest> list = new ArrayList<>();
                try (PreparedStatement q = c.prepareStatement("SELECT id,log_id,requester_name,created FROM "
                        + db.t("admin_log_delete_requests") + " WHERE status='PENDING' ORDER BY id DESC LIMIT 45 OFFSET ?")) {
                    q.setInt(1, page * 45);
                    try (ResultSet r = q.executeQuery()) {
                        while (r.next()) list.add(new DeleteRequest(r.getLong(1), r.getLong(2), r.getString(3), r.getLong(4)));
                    }
                }
                return list;
            }).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                requests = list;
                for (int i = 0; i < list.size(); i++) {
                    DeleteRequest request = list.get(i);
                    set(i, item(Material.TNT, "Request #" + request.id(), RED,
                            "Log entry: #" + request.logId(), "Requested by: " + request.requester(),
                            "Left-click: approve", "Right-click: reject"));
                }
                set(45, item(Material.ARROW, "← Previous", AQUA));
                set(49, item(Material.BARRIER, "Back", RED));
                set(50, item(Material.ARROW, "Next →", AQUA));
            }));
        }
        @Override void click(Player player, int slot, boolean right) {
            if (!player.hasPermission(OWNER)) { player.closeInventory(); return; }
            if (slot == 49) { MenuListener.open(player, new Home()); return; }
            if (slot == 45 && page > 0) { MenuListener.open(player, new Requests(page - 1)); return; }
            if (slot == 50 && requests.size() == 45) { MenuListener.open(player, new Requests(page + 1)); return; }
            if (slot < 0 || slot >= requests.size()) return;
            if (!right) MenuListener.open(player, new ConfirmLogDeletion(player.getUniqueId(), requests.get(slot)));
            else reviewRequest(player, requests.get(slot), false);
        }
    }

    private final class ConfirmLogDeletion extends Gui {
        private final UUID owner;
        private final DeleteRequest request;
        ConfirmLogDeletion(UUID owner, DeleteRequest request) {
            super("VERY CRITICAL • DELETE LOG");
            this.owner=owner; this.request=request;
            set(20,item(Material.TNT,"CONFIRM DELETION",RED,"Log entry #"+request.logId(),"This action cannot be reversed","Only Owner.Core can confirm"));
            set(24,item(Material.LIME_DYE,"Cancel",GREEN)); backButton(); closeButton();
        }
        @Override void click(Player player,int slot,boolean right) {
            if(!player.getUniqueId().equals(owner)||!player.hasPermission(OWNER)){player.closeInventory();return;}
            if(slot==20)reviewRequest(player,request,true);
            else if(slot==24||slot==45)MenuListener.open(player,new Requests(0));
            else if(slot==49)player.closeInventory();
        }
    }

    private void reviewRequest(Player reviewer, DeleteRequest request, boolean approve) {
        db.run(c -> {
            boolean pending;
            try (PreparedStatement q = c.prepareStatement("UPDATE " + db.t("admin_log_delete_requests")
                    + " SET status=?,reviewer_name=?,reviewed=? WHERE id=? AND status='PENDING'")) {
                q.setString(1, approve ? "APPROVED" : "REJECTED");
                q.setString(2, reviewer.getName());
                q.setLong(3, System.currentTimeMillis());
                q.setLong(4, request.id());
                pending = q.executeUpdate() == 1;
            }
            if (pending && approve) {
                try (PreparedStatement q = c.prepareStatement("DELETE FROM " + db.t("admin_logs") + " WHERE id=?")) {
                    q.setLong(1, request.logId()); q.executeUpdate();
                }
                try (PreparedStatement q = c.prepareStatement("INSERT INTO " + db.t("admin_logs")
                        + " (category,actor_name,action,details,created) VALUES ('ADMIN',?,'APPROVED LOG DELETION',?,?)")) {
                    q.setString(1, reviewer.getName());
                    q.setString(2, "Log #" + request.logId() + " removed after owner approval #" + request.id());
                    q.setLong(3, System.currentTimeMillis()); q.executeUpdate();
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (pending) Msg.ok(reviewer, approve ? "Deletion approved – audit record saved" : "Request rejected");
                else Msg.err(reviewer, "Request has already been processed");
                MenuListener.open(reviewer, new Requests(pageOfRequests(request)));
            });
        });
    }
    private int pageOfRequests(DeleteRequest ignored) { return 0; }

    private final class EconomyMenu extends Gui {
        EconomyMenu() {
            super("ECONOMY");
            set(20, item(Material.GOLD_INGOT, "Money", GOLD, "Give, remove, or check"));
            set(24, item(Material.AMETHYST_SHARD, "Gems", PURPLE, "Give, remove, or check"));
            backButton(); closeButton();
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 45) goHome(player);
            else if (slot == 49) player.closeInventory();
            else if (slot == 20) MenuListener.open(player, new EconomyActions(Currency.MONEY));
            else if (slot == 24) MenuListener.open(player, new EconomyActions(Currency.GEMS));
        }
    }

    private final class EconomyActions extends Gui {
        private final Currency currency;
        EconomyActions(Currency currency) {
            super(currency == Currency.MONEY ? "MONEY MANAGEMENT" : "GEMS MANAGEMENT");
            this.currency = currency;
            set(20, item(Material.LIME_DYE, "Give", GREEN, "Add amount"));
            set(22, item(Material.RED_DYE, "Remove", RED, "Subtract amount"));
            set(24, item(Material.SPYGLASS, "Balance", AQUA, "View player balance"));
            backButton(); closeButton();
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 45) MenuListener.open(player, new EconomyMenu());
            else if (slot == 49) player.closeInventory();
            else if (slot == 20) economyInput(player, currency, "give");
            else if (slot == 22) economyInput(player, currency, "remove");
            else if (slot == 24) economyInput(player, currency, "check");
        }
    }

    private void economyInput(Player admin, Currency currency, String action) {
        admin.closeInventory();
        Msg.info(admin, "Enter a player name. Type cancel to abort.");
        plugin.chatInput().request(admin, name -> plugin.data().findUuid(name).thenAccept(uuid ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (uuid == null) { Msg.err(admin, "Player not found"); return; }
                    if (action.equals("check")) {
                        double balance = plugin.economy().balance(uuid, currency);
                        Msg.info(admin, name + " • " + currency.key() + ": " + Num.format(balance));
                        log("ECONOMY", admin, name, "Checked balance", currency.key());
                        return;
                    }
                    Msg.info(admin, "Enter to amount (e.g. 1.5k). Type cancel to abort.");
                    plugin.chatInput().request(admin, raw -> {
                        double amount = Num.parse(raw);
                        if (!(amount > 0) || !Double.isFinite(amount)) { Msg.err(admin, "Invalid amount"); return; }
                        if (action.equals("give")) {
                            plugin.economy().deposit(uuid, currency, amount, "ADMIN_GIVE", admin.getName());
                            log("ECONOMY", admin, name, "Gave " + Num.format(amount), currency.key());
                            Msg.ok(admin, Num.format(amount) + " " + currency.key() + " to " + name + " given");
                        } else {
                            plugin.economy().withdrawAsync(uuid, currency, amount, "ADMIN_REMOVE", admin.getName())
                                    .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (ok) {
                                            log("ECONOMY", admin, name, "Removed " + Num.format(amount), currency.key());
                                            Msg.ok(admin, Num.format(amount) + " " + currency.key() + " from " + name + " removed");
                                        } else Msg.err(admin, "Insufficient funds");
                                    }));
                        }
                    });
                })));
    }

    private final class Groups extends Gui {
        private final int page;
        private final List<Group> groups;
        Groups(int page) {
            super("LUCKPERMS GROUPS");
            this.page = Math.max(0, page);
            this.groups = lp == null ? List.of() : lp.getGroupManager().getLoadedGroups().stream()
                    .sorted(Comparator.comparing(Group::getName, String.CASE_INSENSITIVE_ORDER)).toList();
            if (lp == null) set(22, item(Material.BARRIER, "LuckPerms is unavailable", RED));
            int start = this.page * 45;
            for (int i = start; i < Math.min(start + 45, groups.size()); i++) {
                Group group = groups.get(i);
                String prefix = groupPrefix(group);
                String inherited = inheritedGroups(group);
                set(i - start, item(Material.NAME_TAG, group.getName(), NamedTextColor.YELLOW,
                        "Prefix: " + (prefix.isBlank() ? "None" : prefix),
                        "Inherits: " + (inherited.isBlank() ? "None" : inherited),
                        "Click to open"));
            }
            set(45, item(Material.ARROW, "← Previous", AQUA));
            set(49, item(Material.BARRIER, "Back", RED));
            set(50, item(Material.ARROW, "Next →", AQUA));
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 49) goHome(player);
            else if (slot == 45 && page > 0) MenuListener.open(player, new Groups(page - 1));
            else if (slot == 50 && (page + 1) * 45 < groups.size()) MenuListener.open(player, new Groups(page + 1));
            else if (slot >= 0 && slot < 45 && page * 45 + slot < groups.size())
                MenuListener.open(player, new GroupPermissions(groups.get(page * 45 + slot).getName(), 0));
        }
    }

    private final class GroupPermissions extends Gui {
        private final String groupName;
        private final int page;
        private List<String> nodes = List.of();
        GroupPermissions(String groupName, int page) {
            super("PERMISSIONS: " + groupName);
            this.groupName = groupName;
            this.page = Math.max(0, page);
            load();
        }
        private void load() {
            if (lp == null) return;
            Group group = lp.getGroupManager().getGroup(groupName);
            if (group == null) {
                set(22, item(Material.BARRIER, "Group not found", RED));
                set(49, item(Material.BARRIER, "Back", RED));
                return;
            }
            String prefix = groupPrefix(group);
            String inherited = inheritedGroups(group);
            set(4, item(Material.NAME_TAG, group.getName(), NamedTextColor.YELLOW,
                    "Prefix: " + (prefix.isBlank() ? "None" : prefix),
                    "Inherited groups: " + (inherited.isBlank() ? "None" : inherited)));
            nodes = group.getNodes().stream().filter(n -> n instanceof PermissionNode)
                    .map(n -> ((PermissionNode) n).getPermission()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            for (int i = 0; i < Math.min(45, Math.max(0, nodes.size() - this.page * 45)); i++) {
                String node = nodes.get(this.page * 45 + i);
                set(i, item(Material.REDSTONE, node, RED, "Click to remove"));
            }
            set(45, item(Material.ARROW, "← Previous", AQUA));
            set(47, item(Material.LIME_DYE, "Add permission", GREEN));
            set(49, item(Material.BARRIER, "Back", RED));
            set(50, item(Material.ARROW, "Next →", AQUA));
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 49) MenuListener.open(player, new Groups(0));
            else if (slot == 45 && page > 0) MenuListener.open(player, new GroupPermissions(groupName, page - 1));
            else if (slot == 50 && (page + 1) * 45 < nodes.size()) MenuListener.open(player, new GroupPermissions(groupName, page + 1));
            else if (slot == 47) {
                player.closeInventory();
                Msg.info(player, "Enter a permission node. Type cancel to abort.");
                plugin.chatInput().request(player, node -> changePermission(player, groupName, node, true));
            } else if (slot >= 0 && slot < 45 && page * 45 + slot < nodes.size()) {
                String node = nodes.get(page * 45 + slot);
                MenuListener.open(player, new ConfirmPermission(groupName, node));
            }
        }
    }

    private final class ConfirmPermission extends Gui {
        private final String group;
        private final String node;
        ConfirmPermission(String group, String node) {
            super("REMOVE PERMISSION");
            this.group = group; this.node = node;
            set(22, item(Material.PAPER, node, GOLD, "Group: " + group));
            set(20, item(Material.RED_DYE, "Remove", RED, "The permission will be removed from this group"));
            set(24, item(Material.LIME_DYE, "Keep", GREEN));
            backButton(); closeButton();
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 20) changePermission(player, group, node, false);
            else if (slot == 24 || slot == 45) MenuListener.open(player, new GroupPermissions(group, 0));
            else if (slot == 49) player.closeInventory();
        }
    }

    private String groupPrefix(Group group) {
        String prefix = group.getCachedData().getMetaData(net.luckperms.api.query.QueryOptions.nonContextual()).getPrefix();
        return prefix == null ? "" : prefix.replaceAll("(?i)&#[0-9a-f]{6}|[&§][0-9a-fk-orx]", "");
    }

    private String inheritedGroups(Group group) {
        return group.getNodes().stream()
                .filter(node -> node instanceof net.luckperms.api.node.types.InheritanceNode)
                .map(node -> ((net.luckperms.api.node.types.InheritanceNode) node).getGroupName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private void changePermission(Player actor, String group, String node, boolean add) {
        if (lp == null || node.isBlank() || node.length() > 200) { Msg.err(actor, "Invalid permission"); return; }
        lp.getGroupManager().modifyGroup(group, data -> {
            PermissionNode permission = PermissionNode.builder(node).build();
            if (add) data.data().add(permission); else data.data().remove(permission);
        }).thenRun(() -> {
            log("PERMISSIONS", actor, null, (add ? "Added " : "Removed ") + node, "Group=" + group);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Msg.ok(actor, "Permission " + (add ? "added" : "removed"));
                MenuListener.open(actor, new GroupPermissions(group, 0));
            });
        }).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> Msg.err(actor, "LuckPerms: " + error.getMessage()));
            return null;
        });
    }

    private final class Ranks extends Gui {
        private final int page;
        private final List<String> groups;
        private boolean editIcons;
        Ranks(int page) {
            super("RANK PRIORITIES");
            this.page = Math.max(0, page);
            this.groups = lp == null ? new ArrayList<>() : new ArrayList<>(lp.getGroupManager().getLoadedGroups().stream()
                    .map(Group::getName).toList());
            if (lp == null) set(22, item(Material.BARRIER, "LuckPerms is unavailable", RED));
            load();
        }
        private void load() {
            db.query(c -> {
                Map<String, Integer> values = new HashMap<>();
                try (PreparedStatement q = c.prepareStatement("SELECT group_name,priority FROM " + db.t("rank_priorities"));
                     ResultSet r = q.executeQuery()) {
                    while (r.next()) values.put(r.getString(1), r.getInt(2));
                }
                return values;
            }).thenAccept(values -> Bukkit.getScheduler().runTask(plugin, () -> {
                groups.sort(Comparator.comparingInt((String name) -> values.getOrDefault(name, 100))
                        .thenComparing(String.CASE_INSENSITIVE_ORDER));
                for (int i = 0; i < Math.min(45, Math.max(0, groups.size() - page * 45)); i++) {
                    String group = groups.get(page * 45 + i);
                    int priority = values.getOrDefault(group, 100);
                    int position = page * 45 + i + 1;
                    ItemStack icon = plugin.getConfig().getItemStack("rank-icons." + group);
                    if (icon == null || icon.getType().isAir()) icon = new ItemStack(Material.NETHER_STAR);
                    else icon = icon.clone();
                    ItemMeta meta = icon.getItemMeta();
                    List<Component> lore = new ArrayList<>();
                    lore.add(Msg.item("Priority: " + priority, AQUA));
                    if (editIcons) {
                        lore.add(Msg.item("Click to set icon from your main hand", GREEN));
                        lore.add(Msg.item("Hold an item, or hold air to reset", NamedTextColor.GRAY));
                    } else {
                        lore.add(Msg.item("Left-click: priority -1", GREEN));
                        lore.add(Msg.item("Right-click: priority +1", AQUA));
                    }
                    meta.displayName(Component.text("#" + position + " " + group, NamedTextColor.YELLOW));
                    meta.lore(lore);
                    icon.setItemMeta(meta);
                    set(i, icon);
                }
                set(48, item(editIcons ? Material.LIME_DYE : Material.ITEM_FRAME,
                        editIcons ? "ICON EDIT MODE: ON" : "ICON EDIT MODE: OFF",
                        editIcons ? GREEN : AQUA,
                        "Toggle custom rank icons",
                        "Hold your chosen item in your main hand",
                        "Then click the rank you want to change"));
            }));
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 49) goHome(player);
            else if (slot == 45 && page > 0) MenuListener.open(player, new Ranks(page - 1));
            else if (slot == 50 && (page + 1) * 45 < groups.size()) MenuListener.open(player, new Ranks(page + 1));
            else if (slot == 48) {
                editIcons = !editIcons;
                load();
            }
            else if (slot >= 0 && slot < 45 && page * 45 + slot < groups.size()) {
                String group = groups.get(page * 45 + slot);
                if (editIcons) {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (held == null || held.getType().isAir()) {
                        plugin.getConfig().set("rank-icons." + group, null);
                        Msg.ok(player, "Reset the icon for rank {r}", "r", group);
                        log("RANKS", player, null, "Reset rank icon", "Group=" + group);
                    } else {
                        ItemStack icon = held.clone();
                        icon.setAmount(1);
                        plugin.getConfig().set("rank-icons." + group, icon);
                        Msg.ok(player, "Updated the icon for rank {r}", "r", group);
                        log("RANKS", player, null, "Changed rank icon", "Group=" + group + ", item=" + icon.getType().name());
                    }
                    plugin.saveConfig();
                    load();
                    return;
                }
                db.run(c -> {
                    int old = 100;
                    try (PreparedStatement q = c.prepareStatement("SELECT priority FROM " + db.t("rank_priorities") + " WHERE group_name=?")) {
                        q.setString(1, group);
                        try (ResultSet r = q.executeQuery()) { if (r.next()) old = r.getInt(1); }
                    }
                    int value = Math.max(1, Math.min(10000, old + (right ? 1 : -1)));
                    String sql = db.type() == Database.Type.SQLITE
                            ? "INSERT INTO " + db.t("rank_priorities") + " (group_name,priority) VALUES (?,?) ON CONFLICT(group_name) DO UPDATE SET priority=excluded.priority"
                            : "INSERT INTO " + db.t("rank_priorities") + " (group_name,priority) VALUES (?,?) ON DUPLICATE KEY UPDATE priority=VALUES(priority)";
                    try (PreparedStatement q = c.prepareStatement(sql)) { q.setString(1, group); q.setInt(2, value); q.executeUpdate(); }
                });
                log("RANKS", player, null, "Changed TAB priority", "Group=" + group + ", direction=" + (right ? "+1" : "-1"));
                MenuListener.open(player, new Ranks(page));
            }
        }
    }

    private final class SellPrices extends Gui {
        private final int page;
        private final List<Material> materials;

        SellPrices(int page) {
            super("SELL PRICE MANAGEMENT");
            this.page = Math.max(0, page);
            this.materials = plugin.shop() == null ? List.of()
                    : plugin.shop().sellPrices().keySet().stream()
                    .sorted(Comparator.comparing(Material::name)).toList();

            int start = this.page * 45;
            for (int i = start; i < Math.min(start + 45, materials.size()); i++) {
                Material material = materials.get(i);
                var price = plugin.shop().sellPrice(material);
                ItemStack icon = new ItemStack(material);
                ItemMeta meta = icon.getItemMeta();
                meta.displayName(Component.text(Msg.sc(material.name().replace('_', ' ')), NamedTextColor.YELLOW));
                meta.lore(List.of(
                        Msg.item("Price: {price} {currency}", AQUA,
                                "price", Num.format(price.price()), "currency", price.currency().display()),
                        Msg.item("Left-click: edit price", GREEN),
                        Msg.item("Right-click: remove item", RED)
                ));
                icon.setItemMeta(meta);
                set(i - start, icon);
            }
            set(47, item(Material.LIME_DYE, "Add Item", GREEN, "Add a material and set its price"));
            if (this.page > 0) set(45, item(Material.ARROW, "Previous", AQUA));
            else backButton();
            if (start + 45 < materials.size()) set(50, item(Material.ARROW, "Next", AQUA));
            closeButton();
        }

        @Override void click(Player player, int slot, boolean right) {
            if (slot == 45) {
                if (page > 0) MenuListener.open(player, new SellPrices(page - 1));
                else goHome(player);
                return;
            }
            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 50 && (page + 1) * 45 < materials.size()) {
                MenuListener.open(player, new SellPrices(page + 1));
                return;
            }
            if (slot == 47) { addSellMaterial(player); return; }

            int index = page * 45 + slot;
            if (slot < 0 || slot >= 45 || index >= materials.size()) return;
            Material material = materials.get(index);
            if (right) {
                plugin.shop().removeSellPrice(material);
                log("SELL", player, null, "Removed sell price", material.name());
                Msg.ok(player, "Sell price removed for {item}", "item", material.name());
                MenuListener.open(player, new SellPrices(page));
            } else {
                editSellPrice(player, material);
            }
        }
    }

    private void addSellMaterial(Player player) {
        player.closeInventory();
        Msg.info(player, "Enter a material name, e.g. DIAMOND or EMERALD. Type cancel to abort.");
        plugin.chatInput().request(player, rawMaterial -> {
            Material material = Material.matchMaterial(rawMaterial.trim());
            if (material == null || material.isAir() || !material.isItem()) {
                Msg.err(player, "Unknown or invalid item material");
                MenuListener.open(player, new SellPrices(0));
                return;
            }
            editSellPrice(player, material);
        });
    }

    private void editSellPrice(Player player, Material material) {
        player.closeInventory();
        Msg.info(player, "Enter the price and currency, e.g. 500 money or 50 gems. Type cancel to abort.");
        plugin.chatInput().request(player, input -> {
            String[] parts = input.trim().split("\\s+");
            if (parts.length != 2) {
                Msg.err(player, "Invalid price. Example: 500 money or 50 gems");
                MenuListener.open(player, new SellPrices(0));
                return;
            }
            double amount = Num.parse(parts[0]);
            Currency currency = Currency.parse(parts[1]);
            if (!(amount > 0) || !Double.isFinite(amount) || currency == null) {
                Msg.err(player, "Invalid price. Example: 500 money or 50 gems");
                MenuListener.open(player, new SellPrices(0));
                return;
            }
            plugin.shop().setSellPrice(material, currency, amount);
            log("SELL", player, null, "Updated sell price",
                    material.name() + "=" + Num.format(amount) + " " + currency.key());
            Msg.ok(player, "Sell price updated: {item} = {price} {currency}",
                    "item", material.name(), "price", Num.format(amount), "currency", currency.display());
            MenuListener.open(player, new SellPrices(0));
        });
    }

    private final class Settings extends Gui {
        Settings() {
            super("CORE SETTINGS");
            set(20, item(Material.CLOCK, "Reload config", AQUA, "config.yml"));
            set(22, item(db.type() == Database.Type.SQLITE ? Material.BARREL : Material.CHEST,
                    "Storage: " + db.type().name(), GREEN,
                    db.type() == Database.Type.SQLITE ? "Local file storage active" : "Remote database active",
                    "Location: " + db.storageDescription(),
                    "Save interval: " + db.saveIntervalSeconds() + " seconds",
                    "Read-only status"));
            set(24, item(Material.EMERALD_BLOCK, "Save data", GREEN, "Player data and economy"));
            backButton(); closeButton();
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 45) goHome(player);
            else if (slot == 49) player.closeInventory();
            else if (slot == 20) {
                plugin.reloadConfig();
                Msg.load(plugin.getConfig());
                if (plugin.shop() != null) plugin.shop().reloadSellPrices();
                if (plugin.board() != null) plugin.board().reload();
                log("SETTINGS", player, null, "Reloaded config", "config.yml");
                Msg.ok(player, "Config reloaded");
                MenuListener.open(player, new Settings());
            } else if (slot == 24) {
                plugin.data().flush();
                plugin.economy().flushLog();
                log("SETTINGS", player, null, "Saved data", "Manual save");
                Msg.ok(player, "Save started");
            }
        }
    }
}
