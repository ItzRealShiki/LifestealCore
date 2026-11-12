package de.leon.lifesteal.bounty;

import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BountyCommand implements TabExecutor {

    private final BountyManager m;

    public BountyCommand(BountyManager m) {
        this.m = m;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Player p = requirePlayer(sender);
            if (p != null) MenuListener.open(p, new BountyMenus.Main(m, p));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set", "add" -> {
                Player p = requirePlayer(sender);
                if (p == null) return true;
                if (args.length < 3) {
                    Msg.info(p, "Usage: /bounty set <player> <amount> [money|gems]");
                    return true;
                }
                OfflinePlayer target = findPlayer(args[1]);
                if (target == null) {
                    Msg.err(p, "That player could not be found");
                    m.plugin().sound(p, "error");
                    return true;
                }
                long amount = Num.parseLong(args[2]);
                if (amount <= 0) {
                    Msg.err(p, "Please enter a valid amount (e.g. 5000 or 5k)");
                    m.plugin().sound(p, "error");
                    return true;
                }
                Currency currency = Currency.MONEY;
                if (args.length >= 4) {
                    currency = Currency.parse(args[3]);
                    if (currency == null) {
                        Msg.err(p, "Invalid currency! Use money or gems");
                        return true;
                    }
                }
                m.place(p, target, currency, amount);
            }
            case "list" -> {
                Player p = requirePlayer(sender);
                if (p != null) MenuListener.open(p, new BountyMenus.Active(m, p));
            }
            case "info" -> {
                Player p = requirePlayer(sender);
                if (p == null) return true;
                if (args.length < 2) {
                    Msg.info(p, "Usage: /bounty info <player>");
                    return true;
                }
                Bounty b = m.findByName(args[1]);
                if (b == null) {
                    Msg.err(p, "There is no active bounty on {t}", "t", args[1]);
                    return true;
                }
                MenuListener.open(p, new BountyMenus.Detail(m, p, b.target(), null));
            }
            case "remove" -> {
                if (!sender.hasPermission("bounty.admin")) {
                    Msg.err(sender, "You do not have permission to do that");
                    return true;
                }
                if (args.length < 2) {
                    Msg.info(sender, "Usage: /bounty remove <player> [norefund]");
                    return true;
                }
                Bounty b = m.findByName(args[1]);
                if (b == null) {
                    Msg.err(sender, "There is no active bounty on {t}", "t", args[1]);
                    return true;
                }
                boolean refund = !(args.length >= 3 && args[2].equalsIgnoreCase("norefund"));
                m.adminRemove(b.target(), refund);
                Msg.ok(sender, "The bounty on {t} has been removed", "t", b.targetName());
            }
            default -> {
                Msg.info(sender, "/bounty - open the menu");
                Msg.info(sender, "/bounty set <player> <amount> [money|gems] - place a bounty");
                Msg.info(sender, "/bounty list - active bounties");
                Msg.info(sender, "/bounty info <player> - bounty details");
                if (sender.hasPermission("bounty.admin")) Msg.info(sender, "/bounty remove <player> [norefund] - remove a bounty");
            }
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msg.err(sender, "Only players can use this command");
            return null;
        }
        if (!p.hasPermission("bounty.use")) {
            Msg.err(p, "You do not have permission to do that");
            return null;
        }
        return p;
    }

    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(List.of("set", "list", "info", "help"));
            if (sender.hasPermission("bounty.admin")) options.add("remove");
        } else if (args.length == 2) {
            if (sub.equals("set") || sub.equals("add")) return EcoCommands.onlineNames(sender, args[1]);
            if (sub.equals("info") || sub.equals("remove")) options.addAll(m.targetNames());
        } else if (args.length == 3) {
            if (sub.equals("set") || sub.equals("add")) options.addAll(List.of("1000", "5000", "10k", "50k"));
            else if (sub.equals("remove")) options.add("norefund");
        } else if (args.length == 4 && (sub.equals("set") || sub.equals("add"))) {
            options.addAll(List.of("money", "gems"));
        }
        return EcoCommands.filter(options, args[args.length - 1]);
    }
}
