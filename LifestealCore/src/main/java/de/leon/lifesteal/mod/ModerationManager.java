package de.leon.lifesteal.mod;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerationManager implements Listener {

    private static final String ADMIN = "Admin.Core";
    private static final String MOD = "Mod.Core";
    private static final String SUPPORT = "Support.Core";

    private final LifestealCore plugin;
    private final Set<UUID> supportMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> godMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> requests = ConcurrentHashMap.newKeySet();

    public ModerationManager(LifestealCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && godMode.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        requests.remove(id);
        supportMode.remove(id);
        godMode.remove(id);
    }

    private static Boolean parseState(String[] args) {
        if (args.length == 0) return null;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static List<String> stateTab(String[] args) {
        return args.length == 1 ? EcoCommands.filter(List.of("on", "off", "toggle"), args[0]) : List.of();
    }

    private static GameMode parseMode(String label, String[] args) {
        switch (label.toLowerCase(Locale.ROOT)) {
            case "gms" -> { return GameMode.SURVIVAL; }
            case "gmc" -> { return GameMode.CREATIVE; }
            case "gmsp" -> { return GameMode.SPECTATOR; }
            case "gma" -> { return GameMode.ADVENTURE; }
            default -> { }
        }
        if (args.length == 0) return null;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    public TabExecutor gamemode() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission(MOD)) {
                    Msg.err(sender, "You do not have permission to do this");
                    return true;
                }
                GameMode mode = parseMode(label, args);
                if (mode == null) {
                    Msg.info(sender, "Usage: /gm <survival|creative|adventure|spectator> [player]");
                    return true;
                }

                boolean shortcut = !label.equalsIgnoreCase("gm");
                String targetName = shortcut ? (args.length > 0 ? args[0] : null) : (args.length > 1 ? args[1] : null);
                Player target;
                if (targetName != null) {
                    target = Bukkit.getPlayerExact(targetName);
                } else {
                    target = sender instanceof Player p ? p : null;
                }
                if (target == null) {
                    Msg.err(sender, "This player was not found");
                    return true;
                }
                target.setGameMode(mode);
                String modeName = Msg.sc(mode.name().toLowerCase(Locale.ROOT));
                Msg.ok(target, "Your game mode was changed to {m}", "m", modeName);
                plugin.sound(target, "success");
                if (sender != target) Msg.ok(sender, "Changed the game mode of {p} to {m}", "p", target.getName(), "m", modeName);
                Msg.staff(ADMIN, Msg.Tone.NEUTRAL, "[Admin] {a} changed the game mode of {p} to {m}",
                        "a", sender.getName(), "p", target.getName(), "m", modeName);
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                boolean shortcut = !alias.equalsIgnoreCase("gm");
                if (!shortcut && args.length == 1) return EcoCommands.filter(List.of("survival", "creative", "adventure", "spectator"), args[0]);
                if ((shortcut && args.length == 1) || (!shortcut && args.length == 2)) {
                    return sender.hasPermission(MOD) ? EcoCommands.onlineNames(sender, args[args.length - 1]) : List.of();
                }
                return List.of();
            }
        };
    }

    public TabExecutor teleport() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                boolean admin = player.hasPermission(ADMIN);
                if (!admin && !player.hasPermission(SUPPORT)) {
                    Msg.err(player, "You do not have permission to do this");
                    return true;
                }
                if (!admin && !supportMode.contains(player.getUniqueId())) {
                    Msg.err(player, "You must be in support mode to use /tp. Use /support");
                    plugin.sound(player, "error");
                    return true;
                }
                if (args.length == 1) {
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        Msg.err(player, "This player was not found");
                        return true;
                    }
                    player.teleport(target.getLocation());
                    Msg.ok(player, "You were teleported to {p}", "p", target.getName());
                    plugin.sound(player, "success");
                } else if (args.length >= 2) {
                    Player p1 = Bukkit.getPlayerExact(args[0]);
                    Player p2 = Bukkit.getPlayerExact(args[1]);
                    if (p1 == null || p2 == null) {
                        Msg.err(player, "This player was not found");
                        return true;
                    }
                    p1.teleport(p2.getLocation());
                    Msg.ok(player, "You teleported {a} to {b}", "a", p1.getName(), "b", p2.getName());
                    plugin.sound(player, "success");
                } else {
                    Msg.info(player, "Usage: /tp <player> [player]");
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length <= 2 && (sender.hasPermission(ADMIN) || sender.hasPermission(SUPPORT))
                        ? EcoCommands.onlineNames(sender, args[args.length - 1]) : List.of();
            }
        };
    }

    public TabExecutor support() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (!player.hasPermission(SUPPORT)) {
                    Msg.err(player, "You do not have permission to do this");
                    return true;
                }
                Boolean state = parseState(args);
                boolean on = state != null ? state : !supportMode.contains(player.getUniqueId());
                if (on) {
                    supportMode.add(player.getUniqueId());
                    godMode.add(player.getUniqueId());
                    Msg.ok(player, "You are now in support mode (god mode active)");
                    plugin.sound(player, "success");
                } else {
                    supportMode.remove(player.getUniqueId());
                    godMode.remove(player.getUniqueId());
                    Msg.info(player, "You left support mode");
                    plugin.sound(player, "error");
                }
                Msg.staff(ADMIN, Msg.Tone.NEUTRAL, "[Admin] {p} turned support mode {s}",
                        "p", player.getName(), "s", Msg.sc(on ? "on" : "off"));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return stateTab(args);
            }
        };
    }

    public TabExecutor godmode() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (!player.hasPermission(MOD)) {
                    Msg.err(player, "You do not have permission to do this");
                    return true;
                }
                Boolean state = parseState(args);
                boolean on = state != null ? state : !godMode.contains(player.getUniqueId());
                if (on) {
                    godMode.add(player.getUniqueId());
                    Msg.ok(player, "God mode enabled");
                    plugin.sound(player, "success");
                } else {
                    godMode.remove(player.getUniqueId());
                    Msg.info(player, "God mode disabled");
                    plugin.sound(player, "error");
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return stateTab(args);
            }
        };
    }

    public TabExecutor request() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (args.length >= 2) {
                    handleAnswer(player, args[0], args[1]);
                    return true;
                }
                if (!requests.add(player.getUniqueId())) {
                    Msg.err(player, "You already have an open support request");
                    return true;
                }
                Msg.ok(player, "Your support request was sent to the team");
                plugin.sound(player, "success");

                Component buttons = Msg.neutral("[Support] {p} needs help!", "p", player.getName())
                        .append(Component.text(" "))
                        .append(Msg.good("[Teleport]").clickEvent(ClickEvent.runCommand("/request accept " + player.getName())))
                        .append(Component.text(" "))
                        .append(Msg.bad("[Deny]").clickEvent(ClickEvent.runCommand("/request deny " + player.getName())));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission(SUPPORT)) {
                        Msg.raw(p, buttons);
                        plugin.sound(p, "alert");
                    }
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return List.of();
            }
        };
    }

    private void handleAnswer(Player staff, String action, String targetName) {
        if (!staff.hasPermission(SUPPORT)) {
            Msg.err(staff, "You do not have permission to do this");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !requests.contains(target.getUniqueId())) {
            Msg.err(staff, "There is no open request from this player");
            return;
        }
        if (action.equalsIgnoreCase("accept")) {
            if (!requests.remove(target.getUniqueId())) {
                Msg.err(staff, "This request was already handled");
                return;
            }
            staff.teleport(target.getLocation());
            Msg.ok(staff, "You accepted the request of {p}", "p", target.getName());
            Msg.ok(target, "{p} accepted your support request", "p", staff.getName());
            plugin.sound(staff, "success");
            Msg.staff(ADMIN, Msg.Tone.GOOD, "[Admin] {s} accepted the request of {p}", "s", staff.getName(), "p", target.getName());
        } else if (action.equalsIgnoreCase("deny")) {
            if (!requests.remove(target.getUniqueId())) {
                Msg.err(staff, "This request was already handled");
                return;
            }
            Msg.err(staff, "You denied the request");
            Msg.err(target, "Your support request was denied");
            plugin.sound(staff, "error");
            Msg.staff(ADMIN, Msg.Tone.BAD, "[Admin] {s} denied the request of {p}", "s", staff.getName(), "p", target.getName());
        }
    }
}
