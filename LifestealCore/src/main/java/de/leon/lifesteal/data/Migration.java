package de.leon.lifesteal.data;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

public final class Migration {

    private Migration() {
    }

    public static void run(LifestealCore plugin, Database db, DataManager data) {
        try {
            if (db.getState("migrated_v1") != null) return;
            File plugins = plugin.getDataFolder().getParentFile();
            File combat = new File(plugins, "CombatPlugin/data.yml");
            File bounty = new File(plugins, "Bounty/bounties.yml");
            File shop = new File(plugins, "EcoExtension/shop.yml");

            if (combat.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(combat);
                String salt = y.getString("ip-salt");
                if (salt != null) data.setIpSalt(salt);
                int n = db.sync(c -> importCombat(plugin, db, c, y));
                plugin.getLogger().info("Imported " + n + " players from CombatPlugin/data.yml");
            }
            if (bounty.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(bounty);
                int n = db.sync(c -> importBounties(db, c, y));
                plugin.getLogger().info("Imported " + n + " bounties from Bounty/bounties.yml");
            }
            if (shop.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(shop);
                int n = db.sync(c -> importShop(db, c, y));
                if (n > 0) db.setState("shop_seeded", "1");
                plugin.getLogger().info("Imported " + n + " shop items from EcoExtension/shop.yml");
            }
            db.setState("migrated_v1", String.valueOf(System.currentTimeMillis()));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Import of old plugin data failed (will try again next start)", e);
        }
    }

    private static int importCombat(LifestealCore plugin, Database db, Connection c, YamlConfiguration y) throws SQLException {
        ConfigurationSection players = y.getConfigurationSection("players");
        int count = 0;
        double money = plugin.getConfig().getDouble("economy.start-money", 1000);
        double gems = plugin.getConfig().getDouble("economy.start-gems", 50);
        if (players != null) {
            try (PreparedStatement ps = c.prepareStatement(db.insertIgnore() + " INTO " + db.t("players")
                    + " (uuid, name, money, gems, hearts, protection_seconds, protection_enabled, low_granted, banned_until, ip_hash, last_seen)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement hs = c.prepareStatement("INSERT INTO " + db.t("heart_history")
                         + " (uuid, origin, origin_name, time) VALUES (?, ?, ?, ?)")) {
                for (String key : players.getKeys(false)) {
                    try {
                        UUID.fromString(key);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    ConfigurationSection p = players.getConfigurationSection(key);
                    if (p == null) continue;
                    ps.setString(1, key);
                    ps.setString(2, p.getString("name"));
                    ps.setDouble(3, money);
                    ps.setDouble(4, gems);
                    ps.setInt(5, p.getInt("hearts", plugin.getConfig().getInt("hearts.start", 10)));
                    ps.setInt(6, p.getInt("protection-seconds", 0));
                    ps.setInt(7, p.getBoolean("protection-enabled", false) ? 1 : 0);
                    ps.setInt(8, p.getBoolean("five-granted", false) ? 1 : 0);
                    ps.setLong(9, p.getLong("banned-until", 0));
                    ps.setString(10, p.getString("ip-hash"));
                    ps.setLong(11, System.currentTimeMillis());
                    ps.addBatch();
                    for (String raw : p.getStringList("history")) {
                        String[] parts = raw.split("\\|", 3);
                        if (parts.length != 3) continue;
                        try {
                            hs.setString(1, key);
                            hs.setString(2, UUID.fromString(parts[0]).toString());
                            hs.setString(3, parts[1]);
                            hs.setLong(4, Long.parseLong(parts[2]));
                            hs.addBatch();
                        } catch (IllegalArgumentException ignored) {

                        }
                    }
                    count++;
                }
                ps.executeBatch();
                hs.executeBatch();
            }
        }
        try (PreparedStatement ps = c.prepareStatement(db.insertIgnore() + " INTO " + db.t("redeemed_hearts") + " (heart_id) VALUES (?)")) {
            for (String id : y.getStringList("redeemed")) {
                ps.setString(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return count;
    }

    private static int importBounties(Database db, Connection c, YamlConfiguration y) throws SQLException {
        ConfigurationSection sec = y.getConfigurationSection("bounties");
        int count = 0;
        if (sec != null) {
            try (PreparedStatement bs = c.prepareStatement("REPLACE INTO " + db.t("bounties")
                    + " (target, target_name, created, expires) VALUES (?, ?, ?, ?)");
                 PreparedStatement cs = c.prepareStatement("REPLACE INTO " + db.t("bounty_contributions")
                         + " (target, placer, name, money, gems) VALUES (?, ?, ?, ?, ?)")) {
                for (String key : sec.getKeys(false)) {
                    try {
                        UUID.fromString(key);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    bs.setString(1, key);
                    bs.setString(2, sec.getString(key + ".name", "Unknown"));
                    bs.setLong(3, sec.getLong(key + ".created"));
                    bs.setLong(4, sec.getLong(key + ".expires"));
                    bs.addBatch();
                    ConfigurationSection con = sec.getConfigurationSection(key + ".contributions");
                    if (con != null) {
                        for (String placer : con.getKeys(false)) {
                            cs.setString(1, key);
                            cs.setString(2, placer);
                            cs.setString(3, con.getString(placer + ".name", "?"));
                            cs.setLong(4, con.getLong(placer + ".money", con.getLong(placer + ".amount", 0)));
                            cs.setLong(5, con.getLong(placer + ".gems", 0));
                            cs.addBatch();
                        }
                    }
                    count++;
                }
                bs.executeBatch();
                cs.executeBatch();
            }
        }
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("cooldowns") + " (id, until_time) VALUES (?, ?)")) {
            ConfigurationSection place = y.getConfigurationSection("cooldowns.place");
            if (place != null) {
                for (String k : place.getKeys(false)) {
                    ps.setString(1, "bp:" + k);
                    ps.setLong(2, place.getLong(k));
                    ps.addBatch();
                }
            }
            ConfigurationSection claim = y.getConfigurationSection("cooldowns.claim");
            if (claim != null) {
                for (String k : claim.getKeys(false)) {
                    ps.setString(1, "bc:" + k.replace('_', ':'));
                    ps.setLong(2, claim.getLong(k));
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
        return count;
    }

    private static int importShop(Database db, Connection c, YamlConfiguration y) throws SQLException {
        ConfigurationSection sec = y.getConfigurationSection("shop-items");
        if (sec == null) return 0;
        int count = 0;
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("shop_items")
                + " (slot, item, money_price, gems_price) VALUES (?, ?, ?, ?)")) {
            for (String key : sec.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                String data = sec.getString(key + ".item-data");
                if (data == null || data.isBlank()) {

                    org.bukkit.Material m = org.bukkit.Material.matchMaterial(sec.getString(key + ".material", ""));
                    if (m == null) continue;
                    int amount = Math.max(1, Math.min(m.getMaxStackSize(), sec.getInt(key + ".amount", 1)));
                    data = Base64.getEncoder().encodeToString(new org.bukkit.inventory.ItemStack(m, amount).serializeAsBytes());
                }
                ps.setInt(1, slot);
                ps.setString(2, data);
                ps.setDouble(3, sec.getDouble(key + ".money-price", 0));
                ps.setDouble(4, sec.getDouble(key + ".gems-price", 0));
                ps.addBatch();
                count++;
            }
            ps.executeBatch();
        }
        return count;
    }
}
