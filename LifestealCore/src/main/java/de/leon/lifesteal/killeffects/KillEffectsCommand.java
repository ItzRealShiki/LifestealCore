package de.leon.lifesteal.killeffects;

import de.leon.lifesteal.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public final class KillEffectsCommand implements TabExecutor {
    private final KillEffectManager manager;
    public KillEffectsCommand(KillEffectManager manager) { this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can use this command");
            return true;
        }
        new KillEffectsMenu(manager).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
