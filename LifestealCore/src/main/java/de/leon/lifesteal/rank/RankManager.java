package de.leon.lifesteal.rank;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /rank: all LuckPerms groups in a menu. Groups are read live, new groups show up automatically.
 * This class is only loaded when LuckPerms is installed.
 */
public final class RankManager {

    public static final String ADMIN = "Admin.Core";

    public record Rank(String name, String display, TextColor color, Material material, int weight) {
    }

    private static final Pattern COLOR_CODE = Pattern.compile(
            "&#([0-9a-fA-F]{6})|&[xX]((?:&[0-9a-fA-F]){6})|&([0-9a-fA-F])");

    private static final NamedTextColor[] LEGACY = {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
    };

    private static final Map<NamedTextColor, Material> WOOL = Map.ofEntries(
            Map.entry(NamedTextColor.BLACK, Material.BLACK_WOOL),
            Map.entry(NamedTextColor.DARK_BLUE, Material.BLUE_WOOL),
            Map.entry(NamedTextColor.DARK_GREEN, Material.GREEN_WOOL),
            Map.entry(NamedTextColor.DARK_AQUA, Material.CYAN_WOOL),
            Map.entry(NamedTextColor.DARK_RED, Material.BROWN_WOOL),
            Map.entry(NamedTextColor.DARK_PURPLE, Material.PURPLE_WOOL),
            Map.entry(NamedTextColor.GOLD, Material.ORANGE_WOOL),
            Map.entry(NamedTextColor.GRAY, Material.LIGHT_GRAY_WOOL),
            Map.entry(NamedTextColor.DARK_GRAY, Material.GRAY_WOOL),
            Map.entry(NamedTextColor.BLUE, Material.BLUE_WOOL),
            Map.entry(NamedTextColor.GREEN, Material.LIME_WOOL),
            Map.entry(NamedTextColor.AQUA, Material.LIGHT_BLUE_WOOL),
            Map.entry(NamedTextColor.RED, Material.RED_WOOL),
            Map.entry(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_WOOL),
            Map.entry(NamedTextColor.YELLOW, Material.YELLOW_WOOL),
            Map.entry(NamedTextColor.WHITE, Material.WHITE_WOOL));

    private final LifestealCore plugin;
    private final LuckPerms luckPerms;

    private RankManager(LifestealCore plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    /** Returns null if LuckPerms is not available. */
    public static RankManager create(LifestealCore plugin) {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        return provider == null ? null : new RankManager(plugin, provider.getProvider());
    }

    LifestealCore plugin() {
        return plugin;
    }

    /** All loaded groups, highest weight first. */
    public List<Rank> ranks() {
        List<Rank> list = new ArrayList<>();
        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            TextColor color = colorOf(group);
            list.add(new Rank(group.getName(), group.getFriendlyName(), color,
                    WOOL.getOrDefault(NamedTextColor.nearestTo(color), Material.WHITE_WOOL), group.getWeight().orElse(0)));
        }
        list.sort(Comparator.comparingInt(Rank::weight).reversed()
                .thenComparing(Rank::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private TextColor colorOf(Group group) {
        String override = plugin.getConfig().getString("rank-colors." + group.getName());
        if (override != null && !override.isBlank()) {
            String v = override.trim();
            TextColor parsed = v.startsWith("#") ? TextColor.fromHexString(v) : NamedTextColor.NAMES.value(v.toLowerCase(Locale.ROOT));
            if (parsed != null) return parsed;
        }
        String prefix = group.getCachedData().getMetaData(QueryOptions.nonContextual()).getPrefix();
        TextColor fromPrefix = colorFromPrefix(prefix);
        return fromPrefix != null ? fromPrefix : NamedTextColor.WHITE;
    }

    private static TextColor colorFromPrefix(String prefix) {
        if (prefix == null) return null;
        Matcher m = COLOR_CODE.matcher(prefix.replace('§', '&'));
        if (!m.find()) return null;
        if (m.group(1) != null) return TextColor.fromHexString("#" + m.group(1));
        if (m.group(2) != null) return TextColor.fromHexString("#" + m.group(2).replace("&", ""));
        return LEGACY[Character.digit(m.group(3).charAt(0), 16)];
    }

    public CompletableFuture<UUID> resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return CompletableFuture.completedFuture(online.getUniqueId());
        return luckPerms.getUserManager().lookupUniqueId(name);
    }

    /** Lower-case names of the groups the user directly has. */
    public CompletableFuture<Set<String>> memberOf(UUID uuid) {
        return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
            Set<String> groups = new HashSet<>();
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                groups.add(node.getGroupName().toLowerCase(Locale.ROOT));
            }
            return groups;
        });
    }

    /** Removes all groups of the user and sets this one. */
    public CompletableFuture<Void> setRank(UUID uuid, String group) {
        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            user.data().clear(NodeType.INHERITANCE::matches);
            user.data().add(InheritanceNode.builder(group).build());
            user.setPrimaryGroup(group);
        });
    }

    /** Adds the group. Completes with false if the user already had it. */
    public CompletableFuture<Boolean> addRank(UUID uuid, String group) {
        AtomicBoolean added = new AtomicBoolean(false);
        return luckPerms.getUserManager().modifyUser(uuid, user ->
                added.set(user.data().add(InheritanceNode.builder(group).build()).wasSuccessful())
        ).thenApply(v -> added.get());
    }

    void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    // ------------------------------------------------------------------ /rank

    public TabExecutor command() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player admin)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (!admin.hasPermission(ADMIN)) {
                    Msg.err(admin, "You don't have permission to do that");
                    return true;
                }
                if (args.length == 1 && args[0].equalsIgnoreCase("editor")) {
                    admin.performCommand("ranks editor");
                    return true;
                }
                if (args.length != 2 || !args[0].equalsIgnoreCase("player")) {
                    Msg.info(admin, "Usage: /rank <editor|player> [player]");
                    return true;
                }
                String input = args[1];
                resolveUuid(input).thenAccept(uuid -> {
                    if (uuid == null) {
                        sync(() -> Msg.err(admin, "Player {p} was not found", "p", input));
                        return;
                    }
                    memberOf(uuid).thenAccept(groups -> sync(() -> {
                        if (admin.isOnline()) MenuListener.open(admin, new RankMenu(RankManager.this, uuid, input, groups));
                    })).exceptionally(ex -> fail(admin, ex));
                }).exceptionally(ex -> fail(admin, ex));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 && sender.hasPermission(ADMIN) ? EcoCommands.onlineNames(sender, args[0]) : List.of();
            }
        };
    }

    Void fail(Player admin, Throwable ex) {
        plugin.getLogger().severe("Rank action failed: " + ex);
        sync(() -> Msg.err(admin, "Something went wrong, check the console"));
        return null;
    }
}
