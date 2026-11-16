package de.leon.lifesteal.shop;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.economy.EconomyManager;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

public final class ShopManager {

    public record Category(String key, int slot, String name, ItemStack item) { }
    public record ShopItem(ItemStack item, double moneyPrice, double gemsPrice) { }
    public record SellPrice(Currency currency, double price) { }

    private final LifestealCore plugin;
    private final Database db;
    private final EconomyManager eco;
    private final Map<Integer, Category> categories = new TreeMap<>();
    private final Map<String, Map<Integer, ShopItem>> categoryItems = new TreeMap<>();
    private final Map<Material, SellPrice> sellPrices = new EnumMap<>(Material.class);
    private final File fallbackFile;
    private boolean fileFallback;
    private boolean fallbackStorageHealthy = true;

    public ShopManager(LifestealCore plugin, Database db, EconomyManager eco) {
        this.plugin = plugin;
        this.db = db;
        this.eco = eco;
        this.fallbackFile = new File(plugin.getDataFolder(), "shop-data.yml");
    }

    public void load() throws SQLException {
        categories.clear();
        categoryItems.clear();
        try {
            db.sync(c -> {
                loadDatabase(c);
                return null;
            });
        } catch (SQLException ex) {
            fileFallback = true;
            plugin.getLogger().log(Level.SEVERE,
                    "Shop database storage is unavailable. Falling back to shop-data.yml; other plugin database features are unchanged.", ex);
            loadFile();
        }
        reloadSellPrices();
    }

    private void loadDatabase(java.sql.Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT category_key, slot, category_name, item FROM " + db.t("shop_categories"));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemStack icon = decode(rs.getString("item"));
                if (icon == null) continue;
                String key = rs.getString("category_key");
                categories.put(rs.getInt("slot"), new Category(key, rs.getInt("slot"),
                        rs.getString("category_name"), icon));
                categoryItems.put(key, new TreeMap<>());
            }
        }

        // Migrate legacy flat shop entries into a "General" category without deleting the old table.
        if (categories.isEmpty()) {
            Map<Integer, ShopItem> legacy = new TreeMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT slot, item, money_price, gems_price FROM " + db.t("shop_items"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = decode(rs.getString("item"));
                    if (item != null) legacy.put(rs.getInt("slot"),
                            new ShopItem(item, rs.getDouble("money_price"), rs.getDouble("gems_price")));
                }
            }
            String key = "legacy-general";
            ItemStack icon = categoryIcon(new ItemStack(Material.CHEST), "General");
            try (PreparedStatement ps = connection.prepareStatement("REPLACE INTO " + db.t("shop_categories")
                    + " (category_key, slot, category_name, item) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, key);
                ps.setInt(2, 0);
                ps.setString(3, "General");
                ps.setString(4, encode(icon));
                ps.executeUpdate();
            }
            categories.put(0, new Category(key, 0, "General", icon));
            Map<Integer, ShopItem> migrated = new TreeMap<>();
            if (legacy.isEmpty() && !"1".equals(readState(connection, "shop_seeded"))) {
                migrated.put(10, new ShopItem(new ItemStack(Material.DIAMOND_SWORD), 500, 5));
                migrated.put(12, new ShopItem(new ItemStack(Material.NETHERITE_INGOT), 1000, 10));
                writeLegacyCategoryItems(connection, key, migrated);
                writeState(connection, "shop_seeded", "1");
            } else {
                migrated.putAll(legacy);
                writeLegacyCategoryItems(connection, key, migrated);
            }
            categoryItems.put(key, migrated);
        } else {
            try (PreparedStatement ps = connection.prepareStatement("SELECT category_key, slot, item, money_price, gems_price FROM " + db.t("shop_category_items"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<Integer, ShopItem> entries = categoryItems.get(rs.getString("category_key"));
                    ItemStack item = decode(rs.getString("item"));
                    if (entries != null && item != null) entries.put(rs.getInt("slot"),
                            new ShopItem(item, rs.getDouble("money_price"), rs.getDouble("gems_price")));
                }
            }
        }
    }

    private String readState(java.sql.Connection connection, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT state_value FROM " + db.t("state") + " WHERE state_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private void writeState(java.sql.Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("REPLACE INTO " + db.t("state")
                + " (state_key, state_value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private void writeLegacyCategoryItems(java.sql.Connection connection, String key, Map<Integer, ShopItem> entries) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("REPLACE INTO " + db.t("shop_category_items")
                + " (category_key, slot, item, money_price, gems_price) VALUES (?, ?, ?, ?, ?)")) {
            for (Map.Entry<Integer, ShopItem> entry : entries.entrySet()) {
                ps.setString(1, key);
                ps.setInt(2, entry.getKey());
                ps.setString(3, encode(entry.getValue().item()));
                ps.setDouble(4, entry.getValue().moneyPrice());
                ps.setDouble(5, entry.getValue().gemsPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadFile() {
        categories.clear();
        categoryItems.clear();
        if (!fallbackFile.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(fallbackFile);
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = new File(fallbackFile.getParentFile(), "shop-data.yml.bak");
            if (backup.isFile()) {
                try {
                    yaml = new YamlConfiguration();
                    yaml.load(backup);
                    Files.copy(backup.toPath(), fallbackFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().warning("shop-data.yml was damaged; recovered shop data from shop-data.yml.bak.");
                } catch (IOException | InvalidConfigurationException backupError) {
                    fallbackStorageHealthy = false;
                    plugin.getLogger().log(Level.SEVERE, "Shop fallback and backup are invalid; preserving both files.", backupError);
                    return;
                }
            } else {
                fallbackStorageHealthy = false;
                plugin.getLogger().log(Level.SEVERE, "shop-data.yml is invalid and no backup exists; preserving the original file.", ex);
                return;
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection("categories");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String base = "categories." + key;
            int slot = yaml.getInt(base + ".slot");
            String name = yaml.getString(base + ".name", "Category");
            ItemStack icon = yaml.getItemStack(base + ".item");
            if (icon == null || icon.getType().isAir()) continue;
            categories.put(slot, new Category(key, slot, name, icon));
            Map<Integer, ShopItem> entries = new TreeMap<>();
            ConfigurationSection itemSection = yaml.getConfigurationSection("items." + key);
            if (itemSection != null) {
                for (String itemSlot : itemSection.getKeys(false)) {
                    int parsed;
                    try { parsed = Integer.parseInt(itemSlot); } catch (NumberFormatException ignored) { continue; }
                    ItemStack item = yaml.getItemStack("items." + key + "." + itemSlot + ".item");
                    if (item != null && !item.getType().isAir()) entries.put(parsed, new ShopItem(item,
                            yaml.getDouble("items." + key + "." + itemSlot + ".money"),
                            yaml.getDouble("items." + key + "." + itemSlot + ".gems")));
                }
            }
            categoryItems.put(key, entries);
        }
    }

    private void saveFile() {
        if (!fallbackStorageHealthy) {
            plugin.getLogger().severe("Shop fallback was not saved because its existing file could not be safely recovered.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Category category : categories.values()) {
            String base = "categories." + category.key();
            yaml.set(base + ".slot", category.slot());
            yaml.set(base + ".name", category.name());
            yaml.set(base + ".item", category.item());
            for (Map.Entry<Integer, ShopItem> entry : items(category.key()).entrySet()) {
                String itemPath = "items." + category.key() + "." + entry.getKey();
                yaml.set(itemPath + ".item", entry.getValue().item());
                yaml.set(itemPath + ".money", entry.getValue().moneyPrice());
                yaml.set(itemPath + ".gems", entry.getValue().gemsPrice());
            }
        }
        try {
            File parent = fallbackFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            File temporary = new File(parent, "shop-data.yml.tmp");
            File backup = new File(parent, "shop-data.yml.bak");
            yaml.save(temporary);
            YamlConfiguration verify = new YamlConfiguration();
            verify.load(temporary);
            if (fallbackFile.isFile() && fallbackFile.length() > 0) {
                Files.copy(fallbackFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary.toPath(), fallbackFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), fallbackFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not safely save fallback shop data to " + fallbackFile, ex);
            File temporary = new File(fallbackFile.getParentFile(), "shop-data.yml.tmp");
            if (temporary.exists()) temporary.delete();
        }
    }

    private void activateFileFallback(SQLException cause) {
        if (!fileFallback) {
            fileFallback = true;
            plugin.getLogger().log(Level.SEVERE,
                    "Shop database write failed. Shop data is now being saved to shop-data.yml.", cause);
        }
        saveFile();
    }

    public boolean usesFileFallback() { return fileFallback; }

    public Map<Integer, Category> categories() { return categories; }

    public Map<Integer, ShopItem> items(String categoryKey) {
        return categoryItems.computeIfAbsent(categoryKey, ignored -> new TreeMap<>());
    }

    public int nextCategorySlot() {
        int slot = 0;
        while (categories.containsKey(slot)) slot++;
        return slot;
    }

    public int nextItemSlot(String categoryKey) {
        int slot = 0;
        Map<Integer, ShopItem> entries = items(categoryKey);
        while (entries.containsKey(slot)) slot++;
        return slot;
    }

    public String addCategory(int slot, ItemStack source) {
        if (source == null || source.getType().isAir()) throw new IllegalArgumentException("Category icon cannot be empty");
        String key = UUID.randomUUID().toString().replace("-", "");
        String name = itemName(source);
        ItemStack icon = categoryIcon(source, name);
        Category category = new Category(key, slot, name, icon);
        categories.put(slot, category);
        categoryItems.put(key, new TreeMap<>());
        if (fileFallback) {
            saveFile();
        } else {
            try {
                db.sync(c -> {
                    try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("shop_categories")
                            + " (category_key, slot, category_name, item) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, key);
                        ps.setInt(2, slot);
                        ps.setString(3, name);
                        ps.setString(4, encode(icon));
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException ex) { activateFileFallback(ex); }
        }
        return key;
    }

    public void removeCategory(String key) {
        Category category = categories.values().stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
        if (category == null) return;
        categories.remove(category.slot());
        categoryItems.remove(key);
        if (fileFallback) { saveFile(); return; }
        try {
            db.sync(c -> {
                try (PreparedStatement items = c.prepareStatement("DELETE FROM " + db.t("shop_category_items") + " WHERE category_key=?");
                     PreparedStatement cat = c.prepareStatement("DELETE FROM " + db.t("shop_categories") + " WHERE category_key=?")) {
                    items.setString(1, key);
                    items.executeUpdate();
                    cat.setString(1, key);
                    cat.executeUpdate();
                }
                return null;
            });
        } catch (SQLException ex) { activateFileFallback(ex); }
    }

    public void setItem(String categoryKey, int slot, ItemStack item, double money, double gems) {
        if (!categoryItems.containsKey(categoryKey) || item == null || item.getType().isAir()) return;
        ShopItem entry = new ShopItem(item.clone(), Math.max(0, money), Math.max(0, gems));
        items(categoryKey).put(slot, entry);
        if (fileFallback) { saveFile(); return; }
        try {
            db.sync(c -> {
                try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("shop_category_items")
                        + " (category_key, slot, item, money_price, gems_price) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, categoryKey);
                    ps.setInt(2, slot);
                    ps.setString(3, encode(entry.item()));
                    ps.setDouble(4, entry.moneyPrice());
                    ps.setDouble(5, entry.gemsPrice());
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLException ex) { activateFileFallback(ex); }
    }

    public void removeItem(String categoryKey, int slot) {
        items(categoryKey).remove(slot);
        if (fileFallback) { saveFile(); return; }
        try {
            db.sync(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.t("shop_category_items")
                        + " WHERE category_key=? AND slot=?")) {
                    ps.setString(1, categoryKey);
                    ps.setInt(2, slot);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLException ex) { activateFileFallback(ex); }
    }

    private ItemStack categoryIcon(ItemStack source, String name) {
        ItemStack icon = source.clone();
        icon.setAmount(1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE));
            meta.lore(List.of(Component.text("Open the " + name + " Shop", NamedTextColor.GRAY)));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    public ItemStack categoryDisplay(Category category) {
        return categoryIcon(category.item(), category.name());
    }

    public void reloadSellPrices() {
        sellPrices.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sell-prices");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null) {
                plugin.getLogger().warning("Unknown material in sell-prices: " + key);
                continue;
            }
            Currency c = Currency.parse(sec.getString(key + ".currency", "money"));
            double price = sec.getDouble(key + ".price", 0);
            if (price > 0) sellPrices.put(m, new SellPrice(c == null ? Currency.MONEY : c, price));
        }
    }

    public SellPrice sellPrice(Material material) { return sellPrices.get(material); }
    public Map<Material, SellPrice> sellPrices() { return Map.copyOf(sellPrices); }
    public EconomyManager eco() { return eco; }
    public LifestealCore plugin() { return plugin; }

    public void setSellPrice(Material material, Currency currency, double price) {
        if (material == null || material.isAir() || !material.isItem()
                || currency == null || !(price > 0) || !Double.isFinite(price)) {
            throw new IllegalArgumentException("Invalid sell price");
        }
        String path = "sell-prices." + material.name();
        plugin.getConfig().set(path + ".currency", currency.key());
        plugin.getConfig().set(path + ".price", price);
        plugin.saveConfig();
        reloadSellPrices();
    }

    public void removeSellPrice(Material material) {
        if (material == null) return;
        plugin.getConfig().set("sell-prices." + material.name(), null);
        plugin.saveConfig();
        reloadSellPrices();
    }

    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public ItemStack decode(String data) {
        if (data == null || data.isBlank()) return null;
        try { return ItemStack.deserializeBytes(Base64.getDecoder().decode(data)); }
        catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not read a shop item", e);
            return null;
        }
    }

    public static String priceText(double money, double gems) {
        if (money > 0 && gems > 0) return Num.format(money) + " " + Msg.sc("money") + " + " + Num.format(gems) + " " + Msg.sc("gems");
        if (gems > 0) return Num.format(gems) + " " + Msg.sc("gems");
        return Num.format(money) + " " + Msg.sc("money");
    }

    public static String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
            if (!name.isBlank()) return name;
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public void buy(Player player, String categoryKey, int slot) {
        ShopItem entry = items(categoryKey).get(slot);
        if (entry == null) return;
        double money = entry.moneyPrice();
        double gems = entry.gemsPrice();
        if (money <= 0 && gems <= 0) {
            Msg.err(player, "This item has no price yet");
            plugin.sound(player, "error");
            return;
        }
        if (money > 0 && !eco.has(player, Currency.MONEY, money)) {
            Msg.err(player, "You do not have enough money");
            plugin.sound(player, "error");
            return;
        }
        if (gems > 0 && !eco.has(player, Currency.GEMS, gems)) {
            Msg.err(player, "You do not have enough gems");
            plugin.sound(player, "error");
            return;
        }
        String name = itemName(entry.item());
        if (money > 0 && !eco.withdraw(player.getUniqueId(), Currency.MONEY, money, "SHOP_BUY", name)) return;
        if (gems > 0 && !eco.withdraw(player.getUniqueId(), Currency.GEMS, gems, "SHOP_BUY", name)) {
            if (money > 0) eco.deposit(player.getUniqueId(), Currency.MONEY, money, "SHOP_BUY", "Refund");
            return;
        }
        give(player, entry.item().clone());
        Msg.ok(player, "You bought {item} for {price}", "item", name, "price", priceText(money, gems));
        plugin.sound(player, "success");
    }

    public static void give(Player player, ItemStack item) {
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    public TabExecutor shopCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (args.length > 0 && args[0].equalsIgnoreCase("editor")) {
                    if (!player.hasPermission(EcoCommands.ADMIN)) {
                        Msg.err(player, "You do not have permission to do this");
                        plugin.sound(player, "error");
                        return true;
                    }
                    new ShopEditorMenu(ShopManager.this).open(player);
                    return true;
                }
                new ShopMenu(ShopManager.this).open(player);
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 && sender.hasPermission(EcoCommands.ADMIN)
                        ? EcoCommands.filter(List.of("editor"), args[0]) : List.of();
            }
        };
    }

    public TabExecutor sellCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                new SellMenu(ShopManager.this).open(player);
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return List.of();
            }
        };
    }

    Component title(String text) {
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(Msg.sc(text), NamedTextColor.DARK_GRAY))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));
    }
}
