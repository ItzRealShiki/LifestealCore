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

    public TabExecutor adminCommand() {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
                if (sender instanceof Player player) MenuListener.open(player, new Ranks(0));
                else Msg.err(sender, "Use /ranks in-game");
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 && admin(sender)
                        ? de.leon.lifesteal.economy.EcoCommands.filter(List.of("editor"), args[0]) : List.of();
            }
        };
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
            set(10, item(Material.COMMAND_BLOCK, "Plugin Status", GREEN, "Plugins and modules"));
            set(11, item(Material.REDSTONE, "Console Errors", RED, "Errors and warnings"));
            set(13, item(Material.WRITABLE_BOOK, "Audit-Logs", GOLD, "Admin actions"));
            set(15, item(Material.PLAYER_HEAD, "Player History", AQUA, "Select a player"));
            set(16, item(Material.EMERALD, "Economy", GREEN, "Money and gems"));
            set(28, item(Material.ENCHANTED_BOOK, "Permissions", PURPLE, "LuckPerms groups"));
            set(30, item(Material.NETHER_STAR, "Rank Priorities", GOLD, "TAB order"));
            set(32, item(Material.REPEATER, "Core Settings", AQUA, "Reload and save"));
            if (playerOwnerHint()) set(34, item(Material.ENDER_CHEST, "Critical Requests", RED, "Review log deletions"));
            set(49, item(Material.BARRIER, "✖ Close", RED));
        }
        private boolean playerOwnerHint() { return true; }
        @Override void click(Player player, int slot, boolean right) {
            switch (slot) {
                case 10 -> player.performCommand("plugin");
                case 11 -> player.performCommand("console-error");
                case 13 -> MenuListener.open(player, new Logs(null, 0));
                case 15 -> askPlayerHistory(player);
                case 16 -> MenuListener.open(player, new EconomyMenu());
                case 28 -> MenuListener.open(player, new Groups(0));
                case 30 -> MenuListener.open(player, new Ranks(0));
                case 32 -> MenuListener.open(player, new Settings());
                case 34 -> {
                    if (player.hasPermission(OWNER)) MenuListener.open(player, new Requests(0));
                    else Msg.err(player, "Only Owner.Core can review critical requests");
                }
                case 49 -> player.closeInventory();
                default -> { }
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
                if (playerOwner()) set(53, item(Material.ENDER_CHEST, "Deletion Requests", RED));
            }));
        }
        private boolean playerOwner() { return true; }
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
            reviewRequest(player, requests.get(slot), !right);
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
        return prefix == null ? "" : prefix;
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
                    set(i, item(Material.NETHER_STAR, "#" + position + " " + group, NamedTextColor.YELLOW,
                            "Priority: " + priority, "Left-click: -1", "Right-click: +1",
                            "Lower priority number = higher rank"));
                }
            }));
        }
        @Override void click(Player player, int slot, boolean right) {
            if (slot == 49) goHome(player);
            else if (slot == 45 && page > 0) MenuListener.open(player, new Ranks(page - 1));
            else if (slot == 50 && (page + 1) * 45 < groups.size()) MenuListener.open(player, new Ranks(page + 1));
            else if (slot >= 0 && slot < 45 && page * 45 + slot < groups.size()) {
                String group = groups.get(page * 45 + slot);
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

    private final class Settings extends Gui {
        Settings() {
            super("CORE SETTINGS");
            set(20, item(Material.CLOCK, "Reload config", AQUA, "config.yml"));
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
