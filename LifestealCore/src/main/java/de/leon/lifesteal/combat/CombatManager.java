package de.leon.lifesteal.combat;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Combat tag, arena binding and the return timer. Only players in combat are looked at. */
public final class CombatManager {

    private static final Title.Times COUNTDOWN_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(100));

    private final LifestealCore plugin;
    private final Forcefield forcefield;
    private final Map<UUID, Long> combatEnd = new HashMap<>();
    /** Players who were inside an arena while in combat. */
    private final Set<UUID> bound = new HashSet<>();
    /** Players who left the arena: end of their return deadline. */
    private final Map<UUID, Long> returnEnd = new HashMap<>();
    private final Map<UUID, Integer> lastShownSecond = new HashMap<>();
    /** Deaths by combat logging / leaving the zone count as PvP deaths. */
    private final Set<UUID> forcedDeaths = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private long lastTick = System.currentTimeMillis();

    public CombatManager(LifestealCore plugin, Forcefield forcefield) {
        this.plugin = plugin;
        this.forcefield = forcefield;
    }

    public void start() {
        lastTick = System.currentTimeMillis();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        // nobody dies to combat logging while the server shuts down
        for (UUID uuid : combatEnd.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) forcefield.clear(p);
        }
        combatEnd.clear();
        bound.clear();
        returnEnd.clear();
        lastShownSecond.clear();
    }

    public Forcefield forcefield() {
        return forcefield;
    }

    public int defaultSeconds() {
        return Math.max(1, plugin.getConfig().getInt("combat.seconds", 20));
    }

    private int returnSeconds() {
        return Math.max(1, plugin.getConfig().getInt("combat.return-seconds", 10));
    }

    public boolean isInCombat(Player player) {
        UUID id = player.getUniqueId();
        Long end = combatEnd.get(id);
        // combat is frozen while the return timer runs
        return end != null && (end > System.currentTimeMillis() || returnEnd.containsKey(id));
    }

    /** notify = always send the message, otherwise only when entering combat. */
    public void tag(Player player, int seconds, boolean notify) {
        boolean was = isInCombat(player);
        combatEnd.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        if (!was || notify) Msg.info(player, "You are now in combat. If you log out, you die!");
    }

    public void untag(Player player) {
        if (combatEnd.remove(player.getUniqueId()) != null) {
            forget(player);
            notifyLeft(player);
        }
    }

    /** Removes combat without a message (death, logout). */
    public void removeSilently(Player player) {
        combatEnd.remove(player.getUniqueId());
        forget(player);
    }

    private void forget(Player player) {
        UUID id = player.getUniqueId();
        bound.remove(id);
        returnEnd.remove(id);
        lastShownSecond.remove(id);
        forcefield.clear(player);
    }

    private void notifyLeft(Player player) {
        player.sendActionBar(Msg.good("You are no longer in combat"));
        Msg.ok(player, "You are no longer in combat. You can log out safely");
    }

    private void tick() {
        if (combatEnd.isEmpty()) {
            lastTick = System.currentTimeMillis();
            return;
        }
        long now = System.currentTimeMillis();
        long delta = now - lastTick;
        lastTick = now;

        List<Player> toKill = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> it = combatEnd.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID id = entry.getKey();
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                it.remove();
                bound.remove(id);
                returnEnd.remove(id);
                lastShownSecond.remove(id);
                continue;
            }

            if (handleArena(player, now)) {
                it.remove();
                toKill.add(player);
                continue;
            }

            // the combat timer is frozen while the return timer runs
            if (returnEnd.containsKey(id)) entry.setValue(entry.getValue() + delta);

            long left = entry.getValue() - now;
            if (left <= 0) {
                it.remove();
                forget(player);
                notifyLeft(player);
                continue;
            }

            if (!returnEnd.containsKey(id)) {
                int seconds = (int) Math.ceil(left / 1000.0);
                Msg.actionBar(player, Msg.Tone.NEUTRAL, "Combat: {s}s", "s", seconds);
            }
            forcefield.update(player);
        }

        for (Player player : toKill) killForLeaving(player);
    }

    /** Returns true if the return deadline has passed. */
    private boolean handleArena(Player player, long now) {
        UUID id = player.getUniqueId();
        boolean inside = forcefield.isForcefield(player.getLocation());

        if (inside) {
            bound.add(id);
            if (returnEnd.remove(id) != null) {
                lastShownSecond.remove(id);
                Msg.title(player, Msg.good("Back in the zone"), Msg.good("You are safe"), 1500);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                player.sendActionBar(Msg.good("You are back in the zone"));
            }
            return false;
        }

        if (!bound.contains(id)) return false;

        Long end = returnEnd.get(id);
        if (end == null) {
            end = now + returnSeconds() * 1000L;
            returnEnd.put(id, end);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.8f);
            Msg.err(player, "You left the zone! Return within {s} seconds or you die!", "s", returnSeconds());
        }

        long left = end - now;
        if (left <= 0) return true;

        int seconds = (int) Math.ceil(left / 1000.0);
        Integer last = lastShownSecond.get(id);
        if (last == null || last != seconds) {
            lastShownSecond.put(id, seconds);
            player.showTitle(Title.title(Msg.bad("{s}", "s", seconds), Msg.bad("Return to the zone!"), COUNTDOWN_TIMES));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, seconds <= 3 ? 2.0f : 1.0f);
        }
        Msg.actionBar(player, Msg.Tone.BAD, "Return to the zone: {s}s", "s", seconds);
        return false;
    }

    private void killForLeaving(Player player) {
        forget(player);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
        Msg.title(player, Msg.bad("Time's up"), Msg.bad("You left the zone"), 2000);
        Msg.broadcast(Msg.Tone.BAD, "{p} died for leaving the zone during combat", "p", player.getName());
        markForcedDeath(player.getUniqueId());
        player.setHealth(0.0);
    }

    public void markForcedDeath(UUID id) {
        forcedDeaths.add(id);
    }

    public boolean consumeForcedDeath(UUID id) {
        return forcedDeaths.remove(id);
    }
}
