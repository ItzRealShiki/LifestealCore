package de.leon.lifesteal.combat;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CombatCommands {

    private static final String ADMIN = "Admin.Core";

    private final LifestealCore plugin;
    private final CombatManager combat;
    private final ProtectionManager protection;
    private final HeartManager hearts;

    public CombatCommands(LifestealCore plugin, CombatManager combat, ProtectionManager protection, HeartManager hearts) {
        this.plugin = plugin;
        this.combat = combat;
        this.protection = protection;
        this.hearts = hearts;
    }

    public TabExecutor combat() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission(ADMIN)) {
                    Msg.err(sender, "You don't have permission to do that");
                    return true;
                }
                if (args.length != 2) {
                    Msg.info(sender, "Usage: /combat <player> <seconds>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Msg.err(sender, "Player {p} is not online", "p", args[0]);
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    Msg.err(sender, "{v} is not a valid number", "v", args[1]);
                    return true;
                }
                if (seconds < 0) {
                    Msg.err(sender, "Seconds can't be negative");
                    return true;
                }
                if (seconds == 0) {
                    combat.untag(target);
                    Msg.ok(sender, "{p} is no longer in combat", "p", target.getName());
                    return true;
                }
                combat.tag(target, seconds, true);
                Msg.ok(sender, "{p} is now in combat for {s} seconds", "p", target.getName(), "s", seconds);
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (!sender.hasPermission(ADMIN)) return List.of();
                if (args.length == 1) return EcoCommands.onlineNames(sender, args[0]);
                if (args.length == 2) return EcoCommands.filter(List.of("0", "10", "20", "30", "60"), args[1]);
                return List.of();
            }
        };
    }

    public TabExecutor protection() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                String mode = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
                switch (mode) {
                    case "status" -> Msg.info(player, "Protection: {s} | Time left: {t}",
                            "s", Msg.sc(protection.isEnabled(player) ? "on" : "off"),
                            "t", Msg.sc(Msg.duration(protection.remainingSeconds(player))));
                    case "on" -> turnOn(player);
                    case "off" -> turnOff(player);
                    case "toggle" -> {
                        if (protection.isEnabled(player)) turnOff(player);
                        else turnOn(player);
                    }
                    default -> Msg.info(player, "Usage: /protection [on|off|toggle]");
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 ? EcoCommands.filter(List.of("on", "off", "toggle"), args[0]) : List.of();
            }
        };
    }

    private void turnOn(Player player) {
        if (protection.remainingSeconds(player) <= 0) {
            Msg.err(player, "You have no protection time left");
            return;
        }
        if (combat.isInCombat(player)) {
            Msg.err(player, "You can't enable protection while in combat");
            return;
        }
        if (combat.forcefield().isForcefield(player.getLocation())) {
            Msg.err(player, "You can't enable protection inside the PvP zone");
            return;
        }
        if (protection.isEnabled(player)) {
            Msg.info(player, "Your protection is already on");
            return;
        }
        protection.setEnabled(player, true);
        Msg.ok(player, "Protection enabled. Time left: {t} (the time only runs while it is on)",
                "t", Msg.sc(Msg.duration(protection.remainingSeconds(player))));
    }

    private void turnOff(Player player) {
        if (!protection.isEnabled(player)) {
            Msg.info(player, "Your protection is already off");
            return;
        }
        protection.setEnabled(player, false);
        Msg.info(player, "Protection disabled. Your remaining time is saved: {t}",
                "t", Msg.sc(Msg.duration(protection.remainingSeconds(player))));
    }

    public TabExecutor heart() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
                    unban(sender, args[1]);
                    return true;
                }
                if (!(sender instanceof Player viewer)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (args.length == 0 || !args[0].equalsIgnoreCase("check") || args.length > 2) {
                    Msg.info(viewer, "Usage: /heart check [player]");
                    return true;
                }
                if (args.length == 1) {
                    open(viewer, viewer.getUniqueId(), viewer.getName());
                    return true;
                }
                if (!viewer.hasPermission(ADMIN)) {
                    Msg.err(viewer, "You don't have permission to check other players");
                    return true;
                }
                String name = args[1];
                plugin.data().findUuid(name).thenAccept(uuid -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (uuid == null) Msg.err(viewer, "No data found for {p}", "p", name);
                    else open(viewer, uuid, name);
                }));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (args.length == 1) {
                    List<String> opts = new ArrayList<>(List.of("check"));
                    if (sender.hasPermission(ADMIN)) opts.add("unban");
                    return EcoCommands.filter(opts, args[0]);
                }
                if (args.length == 2 && sender.hasPermission(ADMIN)) return EcoCommands.onlineNames(sender, args[1]);
                return List.of();
            }
        };
    }

    private void open(Player viewer, UUID target, String name) {
        CompletableFuture<PlayerData> data = plugin.data().peek(target);
        data.thenCombine(hearts.history(target), (d, history) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (d == null) {
                    Msg.err(viewer, "No data found for {p}", "p", name);
                    return;
                }
                new HeartMenu(hearts, d.name() == null ? name : d.name(), d.hearts(), history).open(viewer);
            });
            return null;
        });
    }

    private void unban(CommandSender sender, String name) {
        if (!sender.hasPermission(ADMIN)) {
            Msg.err(sender, "You don't have permission to do that");
            return;
        }
        plugin.data().findUuid(name).thenAccept(uuid -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (uuid == null) {
                Msg.err(sender, "No data found for {p}", "p", name);
                return;
            }
            PlayerData online = plugin.data().cached(uuid);
            if (online != null) {
                online.setBannedUntil(0);
                if (online.hearts() <= 0) online.setHearts(hearts.returnHearts());
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) hearts.apply(p);
                Msg.ok(sender, "Ban of {p} removed. They have {n} hearts", "p", name, "n", online.hearts());
                return;
            }
            plugin.data().unbanOffline(uuid, hearts.returnHearts()).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (ok) Msg.ok(sender, "Ban of {p} removed", "p", name);
                else Msg.err(sender, "No data found for {p}", "p", name);
            }));
        }));
    }
}
