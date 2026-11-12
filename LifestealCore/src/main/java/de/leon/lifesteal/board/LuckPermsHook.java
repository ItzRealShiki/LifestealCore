package de.leon.lifesteal.board;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;

/** Isolated so the plugin still works when LuckPerms is not installed. */
final class LuckPermsHook {

    private final LuckPerms luckPerms = LuckPermsProvider.get();

    private CachedMetaData meta(Player player) {
        return luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
    }

    String prefix(Player player) {
        return meta(player).getPrefix();
    }

    String primaryGroup(Player player) {
        return meta(player).getPrimaryGroup();
    }
}
