package de.leon.lifesteal.fastplace;

import de.leon.lifesteal.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class FastPlaceCommand implements TabExecutor {

    private final FastPlaceManager manager;

    public FastPlaceCommand(FastPlaceManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can use this command");
            return true;
        }

        if (args.length != 1) {
            Msg.info(player, "Usage: /fastplace <on|off>");
            return true;
        }

        if (args[0].equalsIgnoreCase("on")) {
            manager.setEnabled(player, true);
            Msg.ok(player, "FastPlace enabled");
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            manager.setEnabled(player, false);
            Msg.ok(player, "FastPlace disabled");
            return true;
        }

        Msg.info(player, "Usage: /fastplace <on|off>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("on", "off").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }
}
