package de.leon.lifesteal.combat;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Protection time per player. It only counts down while protection is ON and the player is online.
 * Switching off keeps the remaining time.
 */
public final class ProtectionManager {

    private final LifestealCore plugin;
    private BukkitTask task;

    public ProtectionManager(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private PlayerData data(Player player) {
        return plugin.data().get(player);
    }

    public int remainingSeconds(Player player) {
        return data(player).protectionSeconds();
    }

    public boolean isEnabled(Player player) {
        return data(player).protectionEnabled();
    }

    public boolean isProtected(Player player) {
        PlayerData d = plugin.data().cached(player.getUniqueId());
        return d != null && d.protectionEnabled() && d.protectionSeconds() > 0;
    }

    public void setEnabled(Player player, boolean enabled) {
        data(player).setProtectionEnabled(enabled);
    }

    /** Adds protection time. Does NOT switch protection on. */
    public void grant(PlayerData d, int seconds) {
        if (d.protectionSeconds() <= 0) d.setProtectionEnabled(false);
        d.setProtectionSeconds(d.protectionSeconds() + seconds);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().cached(player.getUniqueId());
            if (d == null || !d.protectionEnabled()) continue;
            int left = d.protectionSeconds() - 1;
            if (left <= 0) {
                d.setProtectionSeconds(0);
                d.setProtectionEnabled(false);
                Msg.err(player, "Your protection has ended");
            } else {
                d.setProtectionSeconds(left);
            }
        }
    }
}
