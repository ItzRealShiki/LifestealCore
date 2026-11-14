package de.leon.lifesteal.admin;

import de.leon.lifesteal.LifestealCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

public final class OwnerOpCommands implements Listener {
    private final LifestealCore plugin;

    public OwnerOpCommands(LifestealCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (!isManagedCommand(raw)) return;
        event.setCancelled(true);
        execute(event.getPlayer(), raw);
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        String raw = event.getCommand();
        if (!isManagedCommand(raw)) return;
        event.setCancelled(true);
        execute(event.getSender(), raw);
    }

    private boolean isManagedCommand(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String first = raw.trim().replaceFirst("^/", "").split("\\s+", 2)[0];
        if (first.contains(":")) first = first.substring(first.indexOf(':') + 1);
        return first.equalsIgnoreCase("op") || first.equalsIgnoreCase("deop");
    }

    private void execute(CommandSender sender, String raw) {
        if (!sender.hasPermission("Owner.Core")) {
            sender.sendMessage(Component.text("Owner.Core is required to use this command.", NamedTextColor.RED));
            return;
        }

        String[] args = raw.trim().replaceFirst("^/", "").split("\\s+");
        String root = args[0];
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        boolean grant = root.toLowerCase(Locale.ROOT).equals("op");
        if (args.length != 2 || !args[1].matches("[A-Za-z0-9_]{1,16}")) {
            sender.sendMessage(Component.text("Usage: /" + (grant ? "op" : "deop") + " <player>", NamedTextColor.YELLOW));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        if (target.isOp() == grant) {
            sender.sendMessage(Component.text(
                    target.getName() + (grant ? " is already OP." : " is not OP."),
                    NamedTextColor.YELLOW));
            return;
        }

        target.setOp(grant);
        Player actor = sender instanceof Player player ? player : null;
        if (plugin.adminManager() != null) {
            plugin.adminManager().log("OP", actor, target.getName(),
                    grant ? "Granted operator status" : "Revoked operator status",
                    "Bukkit OP=" + grant + "; LuckPerms groups and permissions unchanged");
        }
        sender.sendMessage(Component.text(
                (grant ? "Granted OP to " : "Removed OP from ") + target.getName()
                        + ". Rank permissions were not changed.",
                NamedTextColor.GREEN));
    }
}
