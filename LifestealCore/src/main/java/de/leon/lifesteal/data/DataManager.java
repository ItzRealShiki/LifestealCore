package de.leon.lifesteal.data;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.economy.Currency;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

public final class DataManager implements Listener {

    private final LifestealCore plugin;
    private final Database db;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private String ipSalt;

    private volatile Function<PlayerData, Component> loginBlocker;

    public DataManager(LifestealCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() throws SQLException {
        ipSalt = db.getState("ip_salt");
        if (ipSalt == null) {
            ipSalt = UUID.randomUUID().toString();
            db.setState("ip_salt", ipSalt);
        }
        long period = db.saveIntervalSeconds() * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, period, period);
    }

    public void setLoginBlocker(Function<PlayerData, Component> blocker) {
        this.loginBlocker = blocker;
    }

    public void setIpSalt(String salt) {
        this.ipSalt = salt;
        db.setState("ip_salt", salt);
    }

    public PlayerData get(Player player) {
        PlayerData data = cache.get(player.getUniqueId());
        if (data == null) {

            data = loadNow(player.getUniqueId(), player.getName(), addressOf(player));
        }
        return data;
    }

    public PlayerData cached(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    private static InetAddress addressOf(Player player) {
        var a = player.getAddress();
        return a == null ? null : a.getAddress();
    }

    public PlayerData loadNow(UUID uuid, String name, InetAddress address) {
        PlayerData existing = cache.get(uuid);
        if (existing != null) return existing;
        try {
            PlayerData data = db.sync(c -> load(c, uuid, name));
            if (address != null) data.setIpHash(hashIp(address.getHostAddress()));
            PlayerData previous = cache.putIfAbsent(uuid, data);
            return previous != null ? previous : data;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load data of " + name, e);
            PlayerData data = new PlayerData(uuid, name);
            data.load(name, startMoney(), startGems(), startHearts(), 0, false, false, 0, null, null, null);
            cache.put(uuid, data);
            return data;
        }
    }

    private double startMoney() {
        return plugin.getConfig().getDouble("economy.start-money", 1000);
    }

    private double startGems() {
        return plugin.getConfig().getDouble("economy.start-gems", 50);
    }

    private int startHearts() {
        return Math.max(1, plugin.getConfig().getInt("hearts.start", 10));
    }

    private PlayerData load(Connection c, UUID uuid, String name) throws SQLException {
        boolean fresh;
        int newbie = plugin.isModuleEnabled("combat") ? plugin.getConfig().getInt("protection.newbie-seconds", 10800) : 0;
        try (PreparedStatement ps = c.prepareStatement(db.insertIgnore() + " INTO " + db.t("players")
                + " (uuid, name, money, gems, hearts, protection_seconds, protection_enabled, last_seen)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setDouble(3, startMoney());
            ps.setDouble(4, startGems());
            ps.setInt(5, startHearts());
            ps.setInt(6, newbie);
            ps.setInt(7, newbie > 0 ? 1 : 0);
            ps.setLong(8, System.currentTimeMillis());
            fresh = ps.executeUpdate() > 0;
        }
        PlayerData data = new PlayerData(uuid, name);
        readInto(c, data);
        data.setFresh(fresh);
        data.setName(name);
        return data;
    }

    private void readInto(Connection c, PlayerData data) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name, money, gems, hearts, protection_seconds,"
                + " protection_enabled, low_granted, banned_until, ip_hash, boosters, passives FROM "
                + db.t("players") + " WHERE uuid=?")) {
            ps.setString(1, data.uuid().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.load(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getInt(4), rs.getInt(5),
                            rs.getInt(6) != 0, rs.getInt(7) != 0, rs.getLong(8), rs.getString(9),
                            rs.getString(10), rs.getString(11));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        PlayerData data = loadNow(event.getUniqueId(), event.getName(), event.getAddress());
        Function<PlayerData, Component> blocker = loginBlocker;
        if (blocker != null) {
            Component reason = blocker.apply(data);
            if (reason != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, reason);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID id = event.getUniqueId();
        if (Bukkit.getPlayer(id) == null) {
            PlayerData data = cache.remove(id);
            if (data != null && data.dirty()) save(List.of(data.snapshot()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = cache.remove(event.getPlayer().getUniqueId());
        if (data != null) save(List.of(data.snapshot()));
    }

    public void flush() {
        List<PlayerData.Snapshot> list = new ArrayList<>();
        for (PlayerData data : cache.values()) {
            if (data.dirty()) list.add(data.snapshot());
        }
        if (!list.isEmpty()) save(list);
    }

    public void saveAllNow() {
        List<PlayerData.Snapshot> list = new ArrayList<>();
        for (PlayerData data : cache.values()) list.add(data.snapshot());
        if (list.isEmpty()) return;
        try {
            db.sync(c -> {
                write(c, list);
                return null;
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data!", e);
        }
    }

    private void save(List<PlayerData.Snapshot> list) {
        db.run(c -> write(c, list));
    }

    private void write(Connection c, List<PlayerData.Snapshot> list) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + db.t("players") + " SET name=?,"
                + " money=money+?, gems=gems+?, hearts=?, protection_seconds=?, protection_enabled=?,"
                + " low_granted=?, banned_until=?, ip_hash=?, boosters=?, passives=?, last_seen=? WHERE uuid=?")) {
            for (PlayerData.Snapshot s : list) {
                ps.setString(1, s.name());
                ps.setDouble(2, s.moneyDelta());
                ps.setDouble(3, s.gemsDelta());
                ps.setInt(4, s.hearts());
                ps.setInt(5, s.protectionSeconds());
                ps.setInt(6, s.protectionEnabled() ? 1 : 0);
                ps.setInt(7, s.lowGranted() ? 1 : 0);
                ps.setLong(8, s.bannedUntil());
                ps.setString(9, s.ipHash());
                ps.setString(10, s.boosters());
                ps.setString(11, s.passives());
                ps.setLong(12, now);
                ps.setString(13, s.uuid().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public CompletableFuture<UUID> findUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return CompletableFuture.completedFuture(online.getUniqueId());
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM " + db.t("players")
                    + " WHERE name=?" + db.nocase() + " ORDER BY last_seen DESC LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? UUID.fromString(rs.getString(1)) : null;
                }
            }
        });
    }

    public CompletableFuture<PlayerData> peek(UUID uuid) {
        PlayerData online = cache.get(uuid);
        if (online != null) return CompletableFuture.completedFuture(online);
        return db.query(c -> {
            PlayerData data = new PlayerData(uuid, null);
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + db.t("players") + " WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                }
            }
            readInto(c, data);
            return data;
        });
    }

    public CompletableFuture<Boolean> changeOffline(UUID uuid, Currency currency, double amount, boolean requireEnough) {
        String col = currency == Currency.MONEY ? "money" : "gems";
        return db.query(c -> {
            String sql = "UPDATE " + db.t("players") + " SET " + col + "=" + col + "+? WHERE uuid=?"
                    + (requireEnough && amount < 0 ? " AND " + col + ">=?" : "");
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setString(2, uuid.toString());
                if (requireEnough && amount < 0) ps.setDouble(3, -amount);
                return ps.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> unbanOffline(UUID uuid, int returnHearts) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + db.t("players")
                    + " SET banned_until=0, hearts=CASE WHEN hearts<=0 THEN ? ELSE hearts END WHERE uuid=?")) {
                ps.setInt(1, returnHearts);
                ps.setString(2, uuid.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    public String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((ipSalt + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
