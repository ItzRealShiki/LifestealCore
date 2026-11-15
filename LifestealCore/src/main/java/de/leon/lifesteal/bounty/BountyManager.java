package de.leon.lifesteal.bounty;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.economy.EconomyManager;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BountyManager implements Listener {

    private final LifestealCore plugin;
    private final Database db;
    private final EconomyManager eco;
    private final Map<UUID, Bounty> bounties = new HashMap<>();

    private final Map<UUID, Long> placeCooldowns = new HashMap<>();

    private final Map<String, Long> claimCooldowns = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask task;

    public BountyManager(LifestealCore plugin, Database db, EconomyManager eco) {
        this.plugin = plugin;
        this.db = db;
        this.eco = eco;
    }

    LifestealCore plugin() {
        return plugin;
    }

    EconomyManager eco() {
        return eco;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void start() throws SQLException {
        load();
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public Bounty get(UUID target) {
        return bounties.get(target);
    }

    public int count() {
        return bounties.size();
    }

    public List<Bounty> sorted() {
        List<Bounty> list = new ArrayList<>(bounties.values());
        list.sort((a, b) -> a.money() != b.money() ? Long.compare(b.money(), a.money()) : Long.compare(b.gems(), a.gems()));
        return list;
    }

    public Bounty findByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && bounties.containsKey(online.getUniqueId())) return bounties.get(online.getUniqueId());
        for (Bounty b : bounties.values()) if (b.targetName().equalsIgnoreCase(name)) return b;
        return null;
    }

    public List<String> targetNames() {
        List<String> names = new ArrayList<>();
        for (Bounty b : bounties.values()) names.add(b.targetName());
        return names;
    }

    public long cooldownLeft(UUID placer) {
        Long until = placeCooldowns.get(placer);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public long minAmount(Currency c) {
        return Math.max(1, cfg().getLong("bounty.currencies." + c.key() + ".min-amount", c == Currency.MONEY ? 100 : 1));
    }

    public long maxAmount(Currency c) {
        return Math.max(minAmount(c), cfg().getLong("bounty.currencies." + c.key() + ".max-amount",
                c == Currency.MONEY ? 1_000_000 : 10_000));
    }

    public List<Long> steps(Currency c) {
        List<Long> steps = new ArrayList<>();
        for (Long v : cfg().getLongList("bounty.currencies." + c.key() + ".steps")) {
            if (v != null && v > 0 && steps.size() < 3) steps.add(v);
        }
        if (steps.isEmpty()) steps.add(c == Currency.MONEY ? 100L : 1L);
        return steps;
    }

    public static String format(Currency currency, long amount) {
        return currency == Currency.MONEY ? Num.format(amount) + "$" : Num.format(amount) + " " + Msg.sc("gems");
    }

    public static String reward(long money, long gems) {
        List<String> parts = new ArrayList<>();
        if (money > 0) parts.add(format(Currency.MONEY, money));
        if (gems > 0) parts.add(format(Currency.GEMS, gems));
        return parts.isEmpty() ? format(Currency.MONEY, 0) : String.join(" + ", parts);
    }

    public static String time(long millis) {
        return Msg.time(millis);
    }

    public boolean place(Player placer, OfflinePlayer target, Currency currency, long amount) {
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            return fail(placer, "That player could not be found");
        }
        UUID targetId = target.getUniqueId();
        if (targetId.equals(placer.getUniqueId())) return fail(placer, "You cannot place a bounty on yourself");
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.hasPermission("bounty.exempt")) {
            return fail(placer, "You cannot place a bounty on this player");
        }

        long min = minAmount(currency);
        long max = maxAmount(currency);
        if (amount < min) return fail(placer, "The bounty must be at least {v}", "v", format(currency, min));
        if (amount > max) return fail(placer, "The bounty cannot be higher than {v}", "v", format(currency, max));

        Bounty existing = bounties.get(targetId);
        String targetName = target.getName() != null ? target.getName() : (existing != null ? existing.targetName() : "Unknown");
        if (existing != null && amount > max - existing.total(currency)) {
            long left = Math.max(0, max - existing.total(currency));
            return fail(placer, "You can only add {v} more to the bounty on {t}", "v", format(currency, left), "t", targetName);
        }
        if (!placer.hasPermission("bounty.bypass.cooldown")) {
            long left = cooldownLeft(placer.getUniqueId());
            if (left > 0) return fail(placer, "You must wait {t} before placing another bounty", "t", time(left));
        }
        int limit = cfg().getInt("bounty.max-active-per-player", 3);
        boolean contributor = existing != null && existing.hasContributor(placer.getUniqueId());
        if (limit > 0 && !contributor) {
            long active = bounties.values().stream().filter(b -> b.hasContributor(placer.getUniqueId())).count();
            if (active >= limit) return fail(placer, "You can only have {n} active bounties at once", "n", limit);
        }

        if (!eco.withdraw(placer.getUniqueId(), currency, amount, "BOUNTY_PLACE", targetName)) {
            return fail(placer, "You do not have enough {c}! (Balance: {b})",
                    "c", Msg.sc(currency.key()), "b", format(currency, (long) eco.balance(placer, currency)));
        }

        long now = System.currentTimeMillis();
        long duration = Math.max(1, cfg().getLong("bounty.duration-minutes", 1440)) * 60_000L;
        boolean isNew = existing == null;
        Bounty bounty = existing;
        if (bounty == null) {
            bounty = new Bounty(targetId, targetName, now, now + duration);
            bounties.put(targetId, bounty);
        } else {
            bounty.setTargetName(targetName);
            if (cfg().getBoolean("bounty.extend-duration-on-increase", false)) bounty.setExpiresAt(now + duration);
        }
        bounty.add(placer.getUniqueId(), placer.getName(), currency, amount);

        long cooldown = Math.max(0, cfg().getLong("bounty.cooldown-seconds", 1800)) * 1000L;
        if (cooldown > 0) {
            placeCooldowns.put(placer.getUniqueId(), now + cooldown);
            saveCooldown("bp:" + placer.getUniqueId(), now + cooldown);
        }
        saveBounty(bounty);

        String amountText = format(currency, amount);
        String total = reward(bounty.money(), bounty.gems());
        boolean broadcast = cfg().getBoolean("bounty.broadcast-new", true);
        if (isNew) {
            Msg.ok(placer, "You have placed a bounty of {a} on {t}", "a", amountText, "t", targetName);
            if (broadcast) broadcastBounty(Msg.Tone.NEUTRAL, "A new bounty has been placed! {p} is offering {a} for {t}",
                    "p", placer.getName(), "a", amountText, "t", targetName);
        } else {
            Msg.ok(placer, "You have increased the bounty on {t} by {a} (total: {s})", "t", targetName, "a", amountText, "s", total);
            if (broadcast) broadcastBounty(Msg.Tone.NEUTRAL, "The bounty on {t} has been increased to {s}", "t", targetName, "s", total);
        }
        Msg.actionBar(placer, Msg.Tone.NEUTRAL, "Bounty on {t}: {s}", "t", targetName, "s", total);
        plugin.sound(placer, "success");

        if (onlineTarget != null && (plugin.settings() == null || plugin.settings().isEnabled(onlineTarget, "bounty-messages"))) {
            Msg.err(onlineTarget, "There is now a bounty of {s} on your head! Watch your back!", "s", total);
            Msg.title(onlineTarget, Msg.bad("You are wanted!"), Msg.neutral("Bounty on you: {s}", "s", total), 2500);
            onlineTarget.playSound(onlineTarget.getLocation(), "block.bell.use", 1f, 1f);
        }
        refreshMenus();
        return true;
    }


    private void broadcastBounty(Msg.Tone tone, String template, Object... placeholders) {
        Component message = Msg.of(tone, template, placeholders);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.settings() == null || plugin.settings().isEnabled(player, "bounty-messages")) {
                Msg.raw(player, message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(Msg.smallCaps(message));
    }

    private boolean fail(Player player, String template, Object... ph) {
        Msg.err(player, template, ph);
        plugin.sound(player, "error");
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        handleKill(victim, victim.getKiller());
    }

    private void handleKill(Player victim, Player killer) {
        Bounty bounty = bounties.get(victim.getUniqueId());
        if (bounty == null || killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        if (cfg().getStringList("bounty.claim.disabled-worlds").contains(victim.getWorld().getName())) return;

        if (!cfg().getBoolean("bounty.claim.contributors-can-claim", false) && bounty.hasContributor(killer.getUniqueId())) {
            fail(killer, "You cannot claim a bounty you have contributed to");
            return;
        }
        if (cfg().getBoolean("bounty.claim.block-same-ip", true) && sameIp(killer, victim)) {
            fail(killer, "This bounty cannot be claimed (same IP)");
            plugin.getLogger().warning("Bounty claim blocked (same IP): " + killer.getName() + " -> " + victim.getName());
            return;
        }
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = claimCooldowns.get(key);
        if (until != null && until > now) {
            fail(killer, "You can claim a bounty on this player again in {t}", "t", time(until - now));
            return;
        }

        int tax = Math.max(0, Math.min(100, cfg().getInt("bounty.claim.tax-percent", 0)));
        long money = bounty.money() - (bounty.money() * tax / 100);
        long gems = bounty.gems() - (bounty.gems() * tax / 100);

        bounties.remove(victim.getUniqueId());
        deleteBounty(victim.getUniqueId());
        eco.deposit(killer.getUniqueId(), Currency.MONEY, money, "BOUNTY_CLAIM", victim.getName());
        eco.deposit(killer.getUniqueId(), Currency.GEMS, gems, "BOUNTY_CLAIM", victim.getName());

        long cooldown = Math.max(0, cfg().getLong("bounty.claim.same-target-cooldown-minutes", 30)) * 60_000L;
        if (cooldown > 0) {
            claimCooldowns.put(key, now + cooldown);
            saveCooldown("bc:" + key, now + cooldown);
        }

        String amount = reward(money, gems);
        Msg.ok(killer, "You have claimed the bounty on {t} and received {a}", "t", victim.getName(), "a", amount);
        Msg.title(killer, Msg.good("Bounty claimed!"), Msg.good("You received {a}", "a", amount), 2500);
        killer.playSound(killer.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        if (plugin.settings() == null || plugin.settings().isEnabled(victim, "bounty-messages")) {
            Msg.err(victim, "{k} has claimed the bounty on your head", "k", killer.getName());
        }
        if (cfg().getBoolean("bounty.broadcast-claim", true)) {
            broadcastBounty(Msg.Tone.NEUTRAL, "{k} has killed {t} and claimed the bounty of {a}",
                    "k", killer.getName(), "t", victim.getName(), "a", amount);
        }
        refreshMenus();
    }

    private static boolean sameIp(Player a, Player b) {
        InetSocketAddress x = a.getAddress();
        InetSocketAddress y = b.getAddress();
        if (x == null || y == null) return false;
        InetAddress ax = x.getAddress();
        InetAddress ay = y.getAddress();
        return ax != null && ax.equals(ay);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        placeCooldowns.values().removeIf(t -> t <= now);
        claimCooldowns.values().removeIf(t -> t <= now);
        if (bounties.isEmpty()) return;

        List<Bounty> expired = new ArrayList<>();
        bounties.values().removeIf(b -> {
            if (b.expiresAt() <= now) {
                expired.add(b);
                return true;
            }
            return false;
        });
        if (expired.isEmpty()) return;

        int percent = Math.max(0, Math.min(100, cfg().getInt("bounty.refund-percent", 100)));
        for (Bounty b : expired) {
            deleteBounty(b.target());
            refund(b, percent, false);
            Player target = Bukkit.getPlayer(b.target());
            if (target != null && (plugin.settings() == null || plugin.settings().isEnabled(target, "bounty-messages"))) {
                Msg.ok(target, "The bounty on your head has expired");
            }
            if (cfg().getBoolean("bounty.broadcast-expire", false)) {
                broadcastBounty(Msg.Tone.NEUTRAL, "The bounty on {t} has expired", "t", b.targetName());
            }
        }
        refreshMenus();
    }

    private void refund(Bounty b, int percent, boolean byAdmin) {
        for (Bounty.Contribution c : b.contributions()) {
            long money = c.money() * percent / 100;
            long gems = c.gems() * percent / 100;
            if (money <= 0 && gems <= 0) continue;
            eco.deposit(c.placer(), Currency.MONEY, money, "BOUNTY_REFUND", b.targetName());
            eco.deposit(c.placer(), Currency.GEMS, gems, "BOUNTY_REFUND", b.targetName());
            Player online = Bukkit.getPlayer(c.placer());
            if (online != null) {
                Msg.info(online, byAdmin ? "Your bounty on {t} was removed by an admin. You have been refunded {a}"
                                : "Your bounty on {t} has expired. You have been refunded {a}",
                        "t", b.targetName(), "a", reward(money, gems));
            }
        }
    }

    public boolean adminRemove(UUID target, boolean refund) {
        Bounty b = bounties.remove(target);
        if (b == null) return false;
        deleteBounty(target);
        if (refund) refund(b, 100, true);
        refreshMenus();
        return true;
    }

    private void refreshMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (MenuListener.openMenu(p) instanceof BountyMenus.Base menu) menu.refresh();
        }
    }

    private void saveBounty(Bounty b) {
        String target = b.target().toString();
        String name = b.targetName();
        long created = b.createdAt();
        long expires = b.expiresAt();
        List<Bounty.Contribution> list = b.contributions();
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("bounties")
                    + " (target, target_name, created, expires) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, target);
                ps.setString(2, name);
                ps.setLong(3, created);
                ps.setLong(4, expires);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("bounty_contributions")
                    + " (target, placer, name, money, gems) VALUES (?, ?, ?, ?, ?)")) {
                for (Bounty.Contribution con : list) {
                    ps.setString(1, target);
                    ps.setString(2, con.placer().toString());
                    ps.setString(3, con.name());
                    ps.setLong(4, con.money());
                    ps.setLong(5, con.gems());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    private void deleteBounty(UUID target) {
        String id = target.toString();
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.t("bounty_contributions") + " WHERE target=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.t("bounties") + " WHERE target=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        });
    }

    private void saveCooldown(String id, long until) {
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("cooldowns") + " (id, until_time) VALUES (?, ?)")) {
                ps.setString(1, id);
                ps.setLong(2, until);
                ps.executeUpdate();
            }
        });
    }

    private void load() throws SQLException {
        bounties.clear();
        placeCooldowns.clear();
        claimCooldowns.clear();
        long now = System.currentTimeMillis();
        db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT target, target_name, created, expires FROM " + db.t("bounties"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID target = UUID.fromString(rs.getString(1));
                        bounties.put(target, new Bounty(target, rs.getString(2), rs.getLong(3), rs.getLong(4)));
                    } catch (IllegalArgumentException ignored) {

                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT target, placer, name, money, gems FROM " + db.t("bounty_contributions"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        Bounty b = bounties.get(UUID.fromString(rs.getString(1)));
                        if (b == null) continue;
                        UUID placer = UUID.fromString(rs.getString(2));
                        if (rs.getLong(4) > 0) b.add(placer, rs.getString(3), Currency.MONEY, rs.getLong(4));
                        if (rs.getLong(5) > 0) b.add(placer, rs.getString(3), Currency.GEMS, rs.getLong(5));
                    } catch (IllegalArgumentException ignored) {

                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.t("cooldowns") + " WHERE until_time<?")) {
                ps.setLong(1, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT id, until_time FROM " + db.t("cooldowns"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    try {
                        if (id.startsWith("bp:")) placeCooldowns.put(UUID.fromString(id.substring(3)), rs.getLong(2));
                        else if (id.startsWith("bc:")) claimCooldowns.put(id.substring(3), rs.getLong(2));
                    } catch (IllegalArgumentException ignored) {

                    }
                }
            }
            return null;
        });
        bounties.values().removeIf(Bounty::isEmpty);
        plugin.getLogger().info(bounties.size() + " active bounties loaded.");
    }
}
