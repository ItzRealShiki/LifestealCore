package de.leon.lifesteal.fastplace;

import de.leon.lifesteal.gui.SettingsMenu;
import de.leon.lifesteal.settings.SettingsManager;
import de.leon.lifesteal.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public final class SettingsCommand implements TabExecutor {
    private final SettingsManager settings;
    private final FastPlaceManager fastPlace;

    public SettingsCommand(SettingsManager settings, FastPlaceManager fastPlace) {
        this.settings = settings;
        this.fastPlace = fastPlace;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can use this command");
            return true;
        }
        if (args.length != 0) {
            Msg.info(player, "Usage: /settings");
            return true;
        }
        new SettingsMenu(settings, fastPlace).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
