package de.leon.lifesteal.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class Database {

    public enum Type { SQLITE, MYSQL }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private final Type type;
    private final String prefix;
    private final int saveInterval;
    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "database.yml");
        if (!file.exists()) plugin.saveResource("database.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        this.type = "mysql".equalsIgnoreCase(cfg.getString("type", "sqlite")) ? Type.MYSQL : Type.SQLITE;
        String p = cfg.getString("table-prefix", "lsc_");
        this.prefix = p == null || !p.matches("[A-Za-z0-9_]{0,16}") ? "lsc_" : p;
        this.saveInterval = Math.max(10, cfg.getInt("save-interval-seconds", 60));

        if (type == Type.MYSQL) {
            this.url = "jdbc:mysql://" + cfg.getString("mysql.host", "localhost") + ":" + cfg.getInt("mysql.port", 3306)
                    + "/" + cfg.getString("mysql.database", "lifesteal")
                    + "?useSSL=" + cfg.getBoolean("mysql.use-ssl", false)
                    + "&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true&rewriteBatchedStatements=true";
            this.user = cfg.getString("mysql.username", "root");
            this.password = cfg.getString("mysql.password", "");
        } else {
            File db = new File(plugin.getDataFolder(), cfg.getString("sqlite.file", "database.db"));
            this.url = "jdbc:sqlite:" + db.getAbsolutePath();
            this.user = null;
            this.password = null;
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LifestealCore-Database");
            t.setDaemon(true);
            return t;
        });
    }

    public Type type() {
        return type;
    }

    public String t(String name) {
        return prefix + name;
    }

    public int saveIntervalSeconds() {
        return saveInterval;
    }

    public String storageDescription() {
        if (type == Type.SQLITE) {
            File databaseFile = new File(url.substring("jdbc:sqlite:".length()));
            String relativePath = plugin.getDataFolder().toPath().relativize(databaseFile.toPath()).toString();
            return "plugins/" + plugin.getDataFolder().getName() + "/" + relativePath.replace('\\', '/');
        }
        String safeUrl = url.substring("jdbc:mysql://".length());
        int queryStart = safeUrl.indexOf('?');
        if (queryStart >= 0) safeUrl = safeUrl.substring(0, queryStart);
        return safeUrl;
    }

    private Connection connection() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed() && connection.isValid(2)) return connection;
            } catch (SQLException ignored) {

            }
            try {
                connection.close();
            } catch (SQLException ignored) {

            }
        }
        if (type == Type.MYSQL) {
            connection = DriverManager.getConnection(url, user, password);
        } else {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {

            }
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=OFF");
            }
        }
        return connection;
    }

    public <T> CompletableFuture<T> query(SqlFunction<T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fn.apply(connection());
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> run(SqlConsumer fn) {
        return CompletableFuture.runAsync(() -> {
            try {
                fn.accept(connection());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database error", e);
            }
        }, executor);
    }

    public <T> T sync(SqlFunction<T> fn) throws SQLException {
        try {
            return query(fn).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException ce && ce.getCause() != null) cause = ce.getCause();
            if (cause instanceof SQLException sql) throw sql;
            throw new SQLException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted", e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new SQLException("Database timeout", e);
        }
    }

    public void close() {
        executor.shutdown();
        boolean terminated = false;
        try {
            terminated = executor.awaitTermination(60, TimeUnit.SECONDS);
            if (!terminated) {
                plugin.getLogger().severe("Database queue is still busy after 60 seconds; requesting shutdown of pending tasks.");
                executor.shutdownNow();
                terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        if (!terminated) {
            plugin.getLogger().severe("Database worker did not stop cleanly. Leaving the connection open rather than closing it during a write.");
            return;
        }
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not close database connection cleanly: " + e.getMessage());
        }
    }

    public String insertIgnore() {
        return type == Type.SQLITE ? "INSERT OR IGNORE" : "INSERT IGNORE";
    }

    public String nocase() {
        return type == Type.SQLITE ? " COLLATE NOCASE" : "";
    }

    private String autoId() {
        return type == Type.SQLITE ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
    }

    private String longText() {
        return type == Type.SQLITE ? "TEXT" : "MEDIUMTEXT";
    }

    public void createTables() throws SQLException {
        sync(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("players") + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(16),"
                        + "money DOUBLE NOT NULL DEFAULT 0,"
                        + "gems DOUBLE NOT NULL DEFAULT 0,"
                        + "hearts INT NOT NULL DEFAULT 10,"
                        + "protection_seconds INT NOT NULL DEFAULT 0,"
                        + "protection_enabled INT NOT NULL DEFAULT 0,"
                        + "low_granted INT NOT NULL DEFAULT 0,"
                        + "banned_until BIGINT NOT NULL DEFAULT 0,"
                        + "ip_hash VARCHAR(64),"
                        + "boosters TEXT,"
                        + "passives TEXT,"
                        + "last_seen BIGINT NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("heart_history") + " ("
                        + "id " + autoId() + ","
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "origin VARCHAR(36) NOT NULL,"
                        + "origin_name VARCHAR(16),"
                        + "time BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("redeemed_hearts") + " ("
                        + "heart_id VARCHAR(36) NOT NULL PRIMARY KEY)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("transactions") + " ("
                        + "id " + autoId() + ","
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "currency VARCHAR(8) NOT NULL,"
                        + "type VARCHAR(24) NOT NULL,"
                        + "amount DOUBLE NOT NULL,"
                        + "balance DOUBLE NOT NULL,"
                        + "actor VARCHAR(32),"
                        + "time BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bounties") + " ("
                        + "target VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "target_name VARCHAR(16),"
                        + "created BIGINT NOT NULL,"
                        + "expires BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("bounty_contributions") + " ("
                        + "target VARCHAR(36) NOT NULL,"
                        + "placer VARCHAR(36) NOT NULL,"
                        + "name VARCHAR(16),"
                        + "money BIGINT NOT NULL DEFAULT 0,"
                        + "gems BIGINT NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (target, placer))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("cooldowns") + " ("
                        + "id VARCHAR(80) NOT NULL PRIMARY KEY,"
                        + "until_time BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("shop_items") + " ("
                        + "slot INT NOT NULL PRIMARY KEY,"
                        + "item " + longText() + " NOT NULL,"
                        + "money_price DOUBLE NOT NULL DEFAULT 0,"
                        + "gems_price DOUBLE NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("shop_categories") + " ("
                        + "category_key VARCHAR(64) NOT NULL PRIMARY KEY,"
                        + "slot INT NOT NULL UNIQUE,"
                        + "category_name VARCHAR(64) NOT NULL,"
                        + "item " + longText() + " NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("shop_category_items") + " ("
                        + "category_key VARCHAR(64) NOT NULL,"
                        + "slot INT NOT NULL,"
                        + "item " + longText() + " NOT NULL,"
                        + "money_price DOUBLE NOT NULL DEFAULT 0,"
                        + "gems_price DOUBLE NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY (category_key, slot))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("state") + " ("
                        + "state_key VARCHAR(64) NOT NULL PRIMARY KEY,"
                        + "state_value VARCHAR(255))");

                // Native WorldGuard storage. Kept separate from the legacy regions.yml
                // until the explicit, verified import migration is run.
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("regions") + " ("
                        + "region_id VARCHAR(64) NOT NULL PRIMARY KEY,"
                        + "world_uuid VARCHAR(36) NOT NULL,"
                        + "world_name VARCHAR(128) NOT NULL,"
                        + "owner_uuid VARCHAR(36) NOT NULL,"
                        + "min_x INT NOT NULL,min_y INT NOT NULL,min_z INT NOT NULL,"
                        + "max_x INT NOT NULL,max_y INT NOT NULL,max_z INT NOT NULL,"
                        + "priority INT NOT NULL DEFAULT 0,"
                        + "parent_id VARCHAR(64),"
                        + "created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("region_members") + " ("
                        + "region_id VARCHAR(64) NOT NULL,"
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "member_type VARCHAR(16) NOT NULL,"
                        + "added_by VARCHAR(36),added_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (region_id, player_uuid, member_type))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("region_flags") + " ("
                        + "region_id VARCHAR(64) NOT NULL,"
                        + "flag_key VARCHAR(96) NOT NULL,"
                        + "value_type VARCHAR(24) NOT NULL,"
                        + "flag_value " + longText() + " NOT NULL,"
                        + "updated_by VARCHAR(36),updated_at BIGINT NOT NULL,"
                        + "PRIMARY KEY (region_id, flag_key))");

                // Persistent WorldEdit history stores metadata in SQL and large snapshots
                // as managed files. Never store full block snapshots in a SQL text/blob column.
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("we_history") + " ("
                        + "history_id VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "world_uuid VARCHAR(36) NOT NULL,"
                        + "operation_type VARCHAR(32) NOT NULL,"
                        + "created_at BIGINT NOT NULL,"
                        + "block_count BIGINT NOT NULL DEFAULT 0,"
                        + "snapshot_path VARCHAR(512) NOT NULL,"
                        + "snapshot_sha256 VARCHAR(64) NOT NULL,"
                        + "status VARCHAR(24) NOT NULL,"
                        + "metadata " + longText() + ")");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("we_operations") + " ("
                        + "operation_id VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "history_id VARCHAR(36),"
                        + "player_uuid VARCHAR(36) NOT NULL,"
                        + "world_uuid VARCHAR(36) NOT NULL,"
                        + "operation_type VARCHAR(32) NOT NULL,"
                        + "status VARCHAR(24) NOT NULL,"
                        + "started_at BIGINT NOT NULL,"
                        + "finished_at BIGINT,"
                        + "affected_blocks BIGINT NOT NULL DEFAULT 0,"
                        + "error_message " + longText() + ")");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("we_schematics") + " ("
                        + "schematic_id VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "owner_uuid VARCHAR(36) NOT NULL,"
                        + "schematic_name VARCHAR(64) NOT NULL,"
                        + "format VARCHAR(24) NOT NULL,"
                        + "file_path VARCHAR(512) NOT NULL,"
                        + "file_sha256 VARCHAR(64) NOT NULL,"
                        + "size_bytes BIGINT NOT NULL DEFAULT 0,"
                        + "created_at BIGINT NOT NULL,"
                        + "UNIQUE (owner_uuid, schematic_name))");

                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("module_audit") + " ("
                        + "audit_id VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "module_name VARCHAR(24) NOT NULL,"
                        + "actor_uuid VARCHAR(36),"
                        + "action VARCHAR(64) NOT NULL,"
                        + "target VARCHAR(128),"
                        + "severity VARCHAR(16) NOT NULL,"
                        + "created_at BIGINT NOT NULL,"
                        + "details " + longText() + ")");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("block_history") + " ("
                        + "history_id VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "world_uuid VARCHAR(36) NOT NULL,"
                        + "x INT NOT NULL,y INT NOT NULL,z INT NOT NULL,"
                        + "actor_uuid VARCHAR(36),actor_name VARCHAR(16),"
                        + "action VARCHAR(24) NOT NULL,"
                        + "before_data " + longText() + " NOT NULL,"
                        + "after_data " + longText() + " NOT NULL,"
                        + "created_at BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + t("module_migrations") + " ("
                        + "migration_key VARCHAR(96) NOT NULL PRIMARY KEY,"
                        + "completed_at BIGINT NOT NULL,"
                        + "details " + longText() + ")");
            }
            index(c, "idx_" + prefix + "players_name", t("players"), "name");
            index(c, "idx_" + prefix + "regions_world", t("regions"), "world_uuid");
            index(c, "idx_" + prefix + "region_members_player", t("region_members"), "player_uuid");
            index(c, "idx_" + prefix + "we_history_player", t("we_history"), "player_uuid, created_at");
            index(c, "idx_" + prefix + "we_operations_player", t("we_operations"), "player_uuid, started_at");
            index(c, "idx_" + prefix + "module_audit_time", t("module_audit"), "module_name, created_at");
            index(c, "idx_" + prefix + "block_history_area", t("block_history"), "world_uuid, x, y, z, created_at");
            index(c, "idx_" + prefix + "block_history_actor", t("block_history"), "actor_uuid, created_at");
            index(c, "idx_" + prefix + "history_uuid", t("heart_history"), "uuid");
            index(c, "idx_" + prefix + "tx_uuid", t("transactions"), "uuid, currency, time");
            index(c, "idx_" + prefix + "tx_time", t("transactions"), "time");
            return null;
        });
    }

    private void index(Connection c, String name, String table, String columns) {
        String sql = (type == Type.SQLITE ? "CREATE INDEX IF NOT EXISTS " : "CREATE INDEX ")
                + name + " ON " + table + " (" + columns + ")";
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException ignored) {

        }
    }

    public String getState(String key) throws SQLException {
        return sync(c -> {
            try (var ps = c.prepareStatement("SELECT state_value FROM " + t("state") + " WHERE state_key=?")) {
                ps.setString(1, key);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    public void setStatesSync(java.util.Map<String, String> values) throws SQLException {
        if (values == null || values.isEmpty()) return;
        sync(c -> {
            try (var ps = c.prepareStatement("REPLACE INTO " + t("state") + " (state_key, state_value) VALUES (?, ?)")) {
                for (var entry : values.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    public void setState(String key, String value) {
        run(c -> {
            try (var ps = c.prepareStatement("REPLACE INTO " + t("state") + " (state_key, state_value) VALUES (?, ?)")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
        });
    }
}
