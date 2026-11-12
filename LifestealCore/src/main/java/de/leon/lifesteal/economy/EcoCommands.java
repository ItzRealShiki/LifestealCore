package de.leon.lifesteal.economy;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EcoCommands {

    public static final String ADMIN = "Admin.Core";

    private final LifestealCore plugin;
    private final EconomyManager eco;

    public EcoCommands(LifestealCore plugin, EconomyManager eco) {
        this.plugin = plugin;
        this.eco = eco;
    }

    public TabExecutor pay() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (args.length < 3) {
                    Msg.info(player, "Usage: /pay <money|gems> <player> <amount>");
                    return true;
                }
                Currency currency = Currency.parse(args[0]);
                if (currency == null) {
                    Msg.err(player, "Invalid currency! Use money or gems");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !player.canSee(target)) {
                    Msg.err(player, "This player is not online");
                    return true;
                }
                if (target.equals(player)) {
                    Msg.err(player, "You cannot pay yourself");
                    return true;
                }
                double amount = Num.parse(args[2]);
                if (amount <= 0) {
                    Msg.err(player, "Please enter a valid amount (e.g. 500, 1.5k, 2m)");
                    return true;
                }
                if (!eco.withdraw(player.getUniqueId(), currency, amount, "PAY_OUT", target.getName())) {
                    Msg.err(player, "You do not have enough {cur}", "cur", Msg.sc(currency.key()));
                    plugin.sound(player, "error");
                    return true;
                }
                eco.deposit(target.getUniqueId(), currency, amount, "PAY_IN", player.getName());
                String formatted = Num.format(amount) + " " + Msg.sc(currency.key());
                Msg.ok(player, "You sent {amount} to {target}", "amount", formatted, "target", target.getName());
                Msg.ok(target, "You received {amount} from {sender}", "amount", formatted, "sender", player.getName());
                plugin.sound(player, "success");
                plugin.sound(target, "success");
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (args.length == 1) return filter(List.of("money", "gems"), args[0]);
                if (args.length == 2) return onlineNames(sender, args[1]);
                if (args.length == 3) return filter(List.of("100", "1k", "10k", "1m"), args[2]);
                return List.of();
            }
        };
    }

    public TabExecutor bal() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length == 0) {
                    if (!(sender instanceof Player player)) {
                        Msg.info(sender, "Usage: /bal <player>");
                        return true;
                    }
                    PlayerData data = plugin.data().get(player);
                    sendBalance(player, null, data.balance(Currency.MONEY), data.balance(Currency.GEMS));
                    return true;
                }
                String name = args[0];
                plugin.data().findUuid(name)
                        .thenCompose(uuid -> uuid == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
                                : plugin.data().peek(uuid))
                        .thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (data == null) {
                                Msg.err(sender, "This player was not found");
                                return;
                            }
                            sendBalance(sender, data.name() == null ? name : data.name(),
                                    data.balance(Currency.MONEY), data.balance(Currency.GEMS));
                        }));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 ? onlineNames(sender, args[0]) : List.of();
            }
        };
    }

    private void sendBalance(CommandSender recipient, String targetName, double money, double gems) {
        String moneyText = Num.format(money);
        String gemsText = Num.format(gems);
        int longestValue = Math.max(moneyText.length(), gemsText.length());
        // Client chat width is not exposed to server plugins; adapt the separator to the values.
        int lineLength = Math.max(8, Math.min(24, longestValue + 6));
        Component header = Component.text("━".repeat(lineLength), NamedTextColor.DARK_GRAY)
                .append(Component.text(" lifesteal ", NamedTextColor.GOLD))
                .append(Component.text("━".repeat(lineLength), NamedTextColor.DARK_GRAY));
        recipient.sendMessage(header);
        if (targetName != null) {
            recipient.sendMessage(Component.text("Player: ", NamedTextColor.GRAY)
                    .append(Component.text(targetName, NamedTextColor.YELLOW)));
        }
        recipient.sendMessage(Component.text("Money: ", NamedTextColor.GRAY)
                .append(Component.text(moneyText, NamedTextColor.GREEN)));
        recipient.sendMessage(Component.text("Gems: ", NamedTextColor.GRAY)
                .append(Component.text(gemsText, NamedTextColor.AQUA)));
    }

    public TabExecutor eco() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission(ADMIN)) {
                    Msg.err(sender, "You do not have permission to do this");
                    return true;
                }
                if (args.length < 3) {
                    Msg.info(sender, "Usage: /eco <money|gems> <give|remove|check> <player> [amount]");
                    return true;
                }
                Currency currency = Currency.parse(args[0]);
                if (currency == null) {
                    Msg.err(sender, "Invalid currency! Use money or gems");
                    return true;
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                if (!action.equals("give") && !action.equals("remove") && !action.equals("check")) {
                    Msg.err(sender, "Action must be give, remove or check");
                    return true;
                }
                double amount;
                if (action.equals("check")) {
                    if (!(sender instanceof Player)) {
                        Msg.err(sender, "Only players can open the statement");
                        return true;
                    }
                    amount = 0;
                } else {
                    if (args.length < 4) {
                        Msg.info(sender, "Usage: /eco {cur} {action} {player} <amount>",
                                "cur", currency.key(), "action", action, "player", args[2]);
                        return true;
                    }
                    amount = Num.parse(args[3]);
                    if (amount <= 0) {
                        Msg.err(sender, "Invalid amount. Examples: 150, 1.5k, 2m");
                        return true;
                    }
                }
                String name = args[2];
                plugin.data().findUuid(name).thenAccept(uuid -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (uuid == null) {
                        Msg.err(sender, "This player was not found");
                        return;
                    }
                    switch (action) {
                        case "check" -> {
                            if (sender instanceof Player viewer && viewer.isOnline()) {
                                new StatementMenu(plugin, eco, uuid, name, currency).show(viewer, 0);
                            }
                        }
                        case "give" -> {
                            eco.deposit(uuid, currency, amount, "ADMIN_GIVE", sender.getName());
                            Msg.ok(sender, "Added {amount} {cur} to {target}",
                                    "amount", Num.format(amount), "cur", Msg.sc(currency.key()), "target", name);
                            notifyTarget(uuid, currency);
                        }
                        default -> eco.withdrawAsync(uuid, currency, amount, "ADMIN_REMOVE", sender.getName())
                                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (ok) {
                                        Msg.ok(sender, "Removed {amount} {cur} from {target}",
                                                "amount", Num.format(amount), "cur", Msg.sc(currency.key()), "target", name);
                                        notifyTarget(uuid, currency);
                                    } else {
                                        Msg.err(sender, "{target} does not have enough {cur}",
                                                "target", name, "cur", Msg.sc(currency.key()));
                                    }
                                }));
                    }
                }));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (!sender.hasPermission(ADMIN)) return List.of();
                if (args.length == 1) return filter(List.of("money", "gems"), args[0]);
                if (args.length == 2) return filter(List.of("give", "remove", "check"), args[1]);
                if (args.length == 3) return onlineNames(sender, args[2]);
                if (args.length == 4 && !args[1].equalsIgnoreCase("check")) {
                    return filter(List.of("100", "1k", "1.5k", "1m"), args[3]);
                }
                return List.of();
            }
        };
    }

    private void notifyTarget(UUID uuid, Currency currency) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            Msg.info(online, "Your {cur} balance was changed by an admin. New balance: {bal}",
                    "cur", Msg.sc(currency.key()), "bal", Num.format(eco.balance(online, currency)));
        }
    }

    public static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        return out;
    }

    public static List<String> onlineNames(CommandSender sender, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player sp && !sp.canSee(p)) continue;
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(t)) out.add(p.getName());
        }
        return out;
    }

    static void reopen(Player player, de.leon.lifesteal.gui.Menu menu) {
        MenuListener.open(player, menu);
    }
}
