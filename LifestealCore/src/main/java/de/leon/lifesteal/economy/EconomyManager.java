package de.leon.lifesteal.economy;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class EconomyManager {

    public record Transaction(Currency currency, String type, double amount, double balance, String actor, long time) {
    }

    public record Page(List<Transaction> entries, int total) {
    }

    private record LogRow(UUID uuid, Currency currency, String type, double amount, double balance, String actor, long time) {
    }

    private final LifestealCore plugin;
    private final Database db;
    private final List<LogRow> pendingLog = new ArrayList<>();
    private final org.bukkit.scheduler.BukkitTask flushTask;

    public EconomyManager(LifestealCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        this.flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushLog, 200L, 200L);
    }

    public void shutdown() {
        flushTask.cancel();
        flushLog();
    }

    public double getMoney(UUID uuid) {
        return balance(uuid, Currency.MONEY);
    }

    public void addMoney(UUID uuid, double amount) {
        deposit(uuid, Currency.MONEY, amount, "PLUGIN", "Plugin");
    }

    public boolean removeMoney(UUID uuid, double amount) {
        return withdraw(uuid, Currency.MONEY, amount, "PLUGIN", "Plugin");
    }

    public double getGems(UUID uuid) {
        return balance(uuid, Currency.GEMS);
    }

    public void addGems(UUID uuid, double amount) {
        deposit(uuid, Currency.GEMS, amount, "PLUGIN", "Plugin");
    }

    public boolean removeGems(UUID uuid, double amount) {
        return withdraw(uuid, Currency.GEMS, amount, "PLUGIN", "Plugin");
    }

    public double balance(UUID uuid, Currency currency) {
        PlayerData data = plugin.data().cached(uuid);
        if (data != null) return data.balance(currency);
        try {
            PlayerData offline = plugin.data().peek(uuid).get(5, TimeUnit.SECONDS);
            return offline == null ? 0 : offline.balance(currency);
        } catch (Exception e) {
            return 0;
        }
    }

    public double balance(Player player, Currency currency) {
        return plugin.data().get(player).balance(currency);
    }

    public boolean has(Player player, Currency currency, double amount) {
        return amount > 0 && balance(player, currency) + 1e-9 >= amount;
    }

    public void deposit(UUID uuid, Currency currency, double amount, String type, String actor) {
        if (!(amount > 0) || !Double.isFinite(amount)) return;
        PlayerData data = plugin.data().cached(uuid);
        if (data != null) {
            data.change(currency, amount);
            log(uuid, currency, type, amount, data.balance(currency), actor);
        } else {
            plugin.data().changeOffline(uuid, currency, amount, false);
            log(uuid, currency, type, amount, -1, actor);
        }
    }

    public boolean withdraw(UUID uuid, Currency currency, double amount, String type, String actor) {
        if (!(amount > 0) || !Double.isFinite(amount)) return false;
        PlayerData data = plugin.data().cached(uuid);
        if (data != null) {
            if (!data.take(currency, amount)) return false;
            log(uuid, currency, type, -amount, data.balance(currency), actor);
            return true;
        }
        try {
            boolean ok = plugin.data().changeOffline(uuid, currency, -amount, true).get(5, TimeUnit.SECONDS);
            if (ok) log(uuid, currency, type, -amount, -1, actor);
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    public CompletableFuture<Boolean> withdrawAsync(UUID uuid, Currency currency, double amount, String type, String actor) {
        PlayerData data = plugin.data().cached(uuid);
        if (data != null) {
            return CompletableFuture.completedFuture(withdraw(uuid, currency, amount, type, actor));
        }
        return plugin.data().changeOffline(uuid, currency, -amount, true).thenApply(ok -> {
            if (ok) Bukkit.getScheduler().runTask(plugin, () -> log(uuid, currency, type, -amount, -1, actor));
            return ok;
        });
    }

    public double multiplier(Player player, Currency currency) {
        return plugin.boosters() == null ? 1.0 : plugin.boosters().multiplier(player, currency);
    }

    private void log(UUID uuid, Currency currency, String type, double amount, double balance, String actor) {
        if (!plugin.getConfig().getBoolean("economy.log-transactions", true)) return;
        synchronized (pendingLog) {
            pendingLog.add(new LogRow(uuid, currency, type, amount, balance, actor, System.currentTimeMillis()));
        }
    }

    public void flushLog() {
        List<LogRow> rows;
        synchronized (pendingLog) {
            if (pendingLog.isEmpty()) return;
            rows = new ArrayList<>(pendingLog);
            pendingLog.clear();
        }
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + db.t("transactions")
                    + " (uuid, currency, type, amount, balance, actor, time) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (LogRow r : rows) {
                    ps.setString(1, r.uuid().toString());
                    ps.setString(2, r.currency().key());
                    ps.setString(3, r.type());
                    ps.setDouble(4, r.amount());
                    ps.setDouble(5, r.balance());
                    ps.setString(6, r.actor() == null ? "" : r.actor().length() > 32 ? r.actor().substring(0, 32) : r.actor());
                    ps.setLong(7, r.time());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public CompletableFuture<Page> statement(UUID uuid, Currency currency, int page, int pageSize) {
        flushLog();
        return db.query(c -> {
            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + db.t("transactions")
                    + " WHERE uuid=? AND currency=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, currency.key());
                try (ResultSet rs = ps.executeQuery()) {
                    total = rs.next() ? rs.getInt(1) : 0;
                }
            }
            List<Transaction> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT type, amount, balance, actor, time FROM "
                    + db.t("transactions") + " WHERE uuid=? AND currency=? ORDER BY time DESC, id DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, currency.key());
                ps.setInt(3, pageSize);
                ps.setInt(4, Math.max(0, page) * pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Transaction(currency, rs.getString(1), rs.getDouble(2), rs.getDouble(3),
                                rs.getString(4), rs.getLong(5)));
                    }
                }
            }
            return new Page(list, total);
        });
    }

    public void pruneLog() {
        int days = plugin.getConfig().getInt("economy.log-keep-days", 30);
        if (days <= 0) return;
        long before = System.currentTimeMillis() - days * 86_400_000L;
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.t("transactions") + " WHERE time<?")) {
                ps.setLong(1, before);
                int removed = ps.executeUpdate();
                if (removed > 0) plugin.getLogger().info("Removed " + removed + " old statement entries.");
            }
        });
    }

    void logError(String what, SQLException e) {
        plugin.getLogger().log(Level.SEVERE, what, e);
    }
}
