package de.leon.lifesteal.admin;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
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
            Msg.err(sender, "Owner.Core is required to use this command");
            return;
        }

        String[] args = raw.trim().replaceFirst("^/", "").split("\\s+");
        String root = args[0];
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        boolean grant = root.toLowerCase(Locale.ROOT).equals("op");
        if (args.length != 2 || !args[1].matches("[A-Za-z0-9_]{1,16}")) {
            Msg.info(sender, "Usage: /{c} <player>", "c", grant ? "op" : "deop");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            Msg.err(sender, "Player not found");
            return;
        }
        if (target.isOp() == grant) {
            Msg.info(sender, grant ? "{p} is already OP" : "{p} is not OP", "p", target.getName());
            return;
        }

        target.setOp(grant);
        Player actor = sender instanceof Player player ? player : null;
        if (plugin.adminManager() != null) {
            plugin.adminManager().log("OP", actor, target.getName(),
                    grant ? "Granted operator status" : "Revoked operator status",
                    "Bukkit OP=" + grant + "; LuckPerms groups and permissions unchanged");
        }
        Msg.ok(sender, grant ? "Granted OP to {p}. Rank permissions were not changed." : "Removed OP from {p}. Rank permissions were not changed.", "p", target.getName());
    }
}
