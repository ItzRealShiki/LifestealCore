package de.leon.lifesteal.diagnostics;

import de.leon.lifesteal.LifestealCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerControlCommand implements TabExecutor {
    private final LifestealCore core;
    private final AtomicBoolean operationPending = new AtomicBoolean(false);

    public ServerControlCommand(LifestealCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("Owner.Core")) {
            sender.sendMessage(Component.text("You do not have permission to do this.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(Component.text("Usage: /" + label, NamedTextColor.YELLOW));
            return true;
        }
        boolean restart = command.getName().equalsIgnoreCase("restart");
        if (!operationPending.compareAndSet(false, true)) {
            sender.sendMessage(Component.text("A server shutdown operation is already running.", NamedTextColor.RED));
            return true;
        }

        try {
            // LifestealCore saves its database-backed state in onDisable().
            // Avoid saving it twice here; only flush vanilla world/player data before shutdown.
            boolean saved = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:save-all flush");
            if (!saved) {
                operationPending.set(false);
                sender.sendMessage(Component.text("Save failed: the vanilla save-all command could not be executed. Operation cancelled.", NamedTextColor.RED));
                core.getLogger().severe("Server restart/stop cancelled because minecraft:save-all flush could not be executed.");
                return true;
            }

            Bukkit.broadcast(Component.text(
                    restart ? "Server is restarting. All available data has been saved." : "Server is stopping. All available data has been saved.",
                    NamedTextColor.YELLOW));

            if (restart) {
                core.getServer().restart();
            } else {
                core.getServer().shutdown();
            }
        } catch (Throwable error) {
            operationPending.set(false);
            core.getLogger().log(java.util.logging.Level.SEVERE, "Could not safely " + (restart ? "restart" : "stop") + " the server.", error);
            sender.sendMessage(Component.text("Operation cancelled because saving failed. Check the server console.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
