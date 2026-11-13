package de.leon.lifesteal.combat;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.api.TeamProvider;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatListener implements Listener {

    private static final String ADMIN = "Admin.Core";

    private record Attack(UUID attacker, long time) {
    }

    private final LifestealCore plugin;
    private final CombatManager combat;
    private final Forcefield forcefield;
    private final ProtectionManager protection;
    private final HeartManager hearts;
    private final AltChecker alt;
    private final Map<UUID, Attack> lastAttack = new ConcurrentHashMap<>();

    public CombatListener(LifestealCore plugin, CombatManager combat, ProtectionManager protection,
                          HeartManager hearts, AltChecker alt) {
        this.plugin = plugin;
        this.combat = combat;
        this.forcefield = combat.forcefield();
        this.protection = protection;
        this.hearts = hearts;
        this.alt = alt;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    public Component loginBlock(PlayerData data) {
        long left = data.bannedUntil() - System.currentTimeMillis();
        if (data.bannedUntil() > 0 && left > 0) return banMessage(left / 1000L + 1);
        return null;
    }

    private Component banMessage(long secondsLeft) {
        return Component.text()
                .append(Msg.bad("You lost all your hearts"))
                .appendNewline()
                .append(Msg.neutral("You can come back in {t} with {n} hearts",
                        "t", Msg.sc(Msg.duration(secondsLeft)), "n", hearts.returnHearts()))
                .build();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data().get(player);
        if (data.fresh()) {
            int newbie = data.protectionSeconds();
            hearts.apply(player);
            if (newbie > 0) {
                Msg.ok(player, "Welcome! You have {t} of newbie protection. Use /protection off to disable it, your remaining time is saved",
                        "t", Msg.sc(Msg.duration(newbie)));
            }
            return;
        }

        if (data.bannedUntil() > 0 || data.hearts() <= 0) {
            int back = hearts.returnHearts();
            data.setBannedUntil(0);
            data.setHearts(back);
            hearts.apply(player);
            var attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) player.setHealth(attr.getValue());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Msg.title(player, Msg.good("Welcome back"), Msg.good("You got {n} hearts", "n", back), 4000);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                Msg.ok(player, "Your ban is over. You have {n} hearts again", "n", back);
            }, 40L);
        } else {
            hearts.apply(player);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) hearts.apply(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtectedDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (protection.isProtected(attacker)) {
            event.setCancelled(true);
            Msg.actionBar(attacker, Msg.Tone.BAD, "You are protected and can't attack players. Use /protection off");
            return;
        }
        if (protection.isProtected(victim)) {
            event.setCancelled(true);
            Msg.actionBar(attacker, Msg.Tone.BAD, "{p} is protected", "p", victim.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        int seconds = combat.defaultSeconds();
        combat.tag(victim, seconds, false);
        combat.tag(attacker, seconds, false);
        lastAttack.put(victim.getUniqueId(), new Attack(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    private UUID recentAttacker(UUID victim) {
        Attack attack = lastAttack.get(victim);
        if (attack == null) return null;
        long window = (cfg().getInt("combat.seconds", 20) + cfg().getInt("combat.return-seconds", 10) + 10) * 1000L;
        return System.currentTimeMillis() - attack.time() <= window ? attack.attacker() : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombat(player)) {
            lastAttack.remove(player.getUniqueId());
            return;
        }
        combat.removeSilently(player);
        event.quitMessage(Msg.bad("{p} logged out during combat and died", "p", player.getName()));
        combat.markForcedDeath(player.getUniqueId());
        player.setHealth(0.0);
    }

    private boolean sameTeam(UUID a, UUID b) {
        RegisteredServiceProvider<TeamProvider> reg = Bukkit.getServicesManager().getRegistration(TeamProvider.class);
        return reg != null && reg.getProvider().sameTeam(a, b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID id = victim.getUniqueId();
        combat.removeSilently(victim);

        boolean forced = combat.consumeForcedDeath(id);
        Player killer = victim.getKiller();
        UUID opponent = killer != null ? killer.getUniqueId() : (forced ? recentAttacker(id) : null);
        boolean pvp = killer != null || forced;
        lastAttack.remove(id);

        if (!pvp && cfg().getBoolean("hearts.lose-only-on-pvp", true)) {
            Msg.info(victim, "You died without a player kill and lost no heart");
            return;
        }
        if (opponent != null) {
            Player other = Bukkit.getPlayer(opponent);
            if (cfg().getBoolean("team-protection.enabled", true) && sameTeam(id, opponent)) {
                notifyBoth(victim, other, "Same team: no heart was lost or won");
                return;
            }
            if (alt.enabled() && !victim.hasPermission(AltChecker.BYPASS_PERMISSION) && alt.sameIp(id, opponent)) {
                notifyBoth(victim, other, "Same IP: no heart was lost or won");
                return;
            }
        }

        if (killer != null && plugin.killEffects() != null) plugin.killEffects().playKill(killer, victim);

        PlayerData data = plugin.data().get(victim);
        int current = data.hearts();
        if (current <= 0) return;
        int now = current - 1;
        data.setHearts(now);

        Item drop = victim.getWorld().dropItemNaturally(victim.getLocation(), hearts.createHeart(id, victim.getName()));
        drop.setInvulnerable(true);
        drop.setUnlimitedLifetime(true);

        if (now <= 0) {
            banVictim(victim, data);
            return;
        }
        Msg.err(victim, "You lost a heart. You now have {n} hearts", "n", now);

        int threshold = cfg().getInt("protection.low-hearts-threshold", 5);
        if (now <= threshold && !data.lowGranted()) {
            int seconds = cfg().getInt("protection.low-hearts-seconds", 10800);
            protection.grant(data, seconds);
            data.setLowGranted(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null) return;
                Msg.title(p, Msg.neutral("{n} hearts left", "n", threshold),
                        Msg.good("You got {t} of protection", "t", Msg.sc(Msg.duration(seconds))), 4000);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
                Msg.ok(p, "You are down to {n} hearts and received {t} of protection. It is NOT active yet, use /protection on",
                        "n", threshold, "t", Msg.sc(Msg.duration(seconds)));
            }, 60L);
        }
    }

    private void banVictim(Player victim, PlayerData data) {
        UUID id = victim.getUniqueId();
        int hours = Math.max(1, cfg().getInt("ban.hours", 24));
        data.setBannedUntil(System.currentTimeMillis() + hours * 3_600_000L);
        Msg.broadcast(Msg.Tone.BAD, "{p} lost all hearts and is eliminated for {h}h", "p", victim.getName(), "h", hours);
        Component message = banMessage(hours * 3600L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.kick(message);
        }, 5L);
    }

    private void notifyBoth(Player victim, Player other, String text) {
        Msg.info(victim, text);
        if (other != null) Msg.info(other, text);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        HeartManager.HeartInfo info = hearts.read(event.getItem());
        if (info != null) {
            event.setCancelled(true);
            hearts.redeem(event.getPlayer(), info, alt);
            return;
        }
        rocketCheck(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (hearts.read(event.getItemInHand()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combat.isInCombat(player) || player.hasPermission(ADMIN)) return;
        event.setCancelled(true);
        Msg.err(player, "You can't use commands while in combat");
    }

    private void rocketCheck(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) return;
        Player player = event.getPlayer();
        if (!player.isGliding() || !combat.isInCombat(player)) return;
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        Msg.actionBar(player, Msg.Tone.BAD, "Firework rockets are disabled in combat");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Player player = event.getPlayer();
        boolean inCombat = combat.isInCombat(player);
        boolean isProtected = !inCombat && protection.isProtected(player);
        if (!inCombat && !isProtected) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        boolean fromZone = forcefield.isForcefield(from);
        boolean toZone = forcefield.isForcefield(to);
        if (isProtected && !fromZone && toZone) {
            event.setCancelled(true);
            Msg.actionBar(player, Msg.Tone.BAD, "You can't enter the PvP zone while protected. Use /protection off");
            return;
        }
        if (inCombat && fromZone && !toZone) {
            event.setCancelled(true);
            Msg.actionBar(player, Msg.Tone.BAD, "You can't leave the zone while in combat");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN || cause == PlayerTeleportEvent.TeleportCause.COMMAND) return;
        Player player = event.getPlayer();
        boolean inCombat = combat.isInCombat(player);
        boolean isProtected = !inCombat && protection.isProtected(player);
        if (!inCombat && !isProtected) return;

        boolean fromZone = forcefield.isForcefield(event.getFrom());
        boolean toZone = forcefield.isForcefield(event.getTo());
        if (isProtected && !fromZone && toZone) {
            event.setCancelled(true);
            Msg.actionBar(player, Msg.Tone.BAD, "You can't enter the PvP zone while protected. Use /protection off");
            return;
        }
        if (inCombat && fromZone && !toZone) {
            event.setCancelled(true);
            Msg.actionBar(player, Msg.Tone.BAD, "You can't leave the zone while in combat");
        }
    }
}
