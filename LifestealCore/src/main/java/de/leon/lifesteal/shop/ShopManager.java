package de.leon.lifesteal.shop;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.economy.EconomyManager;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.util.Num;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

public final class ShopManager {

    public record ShopItem(ItemStack item, double moneyPrice, double gemsPrice) {
    }

    public record SellPrice(Currency currency, double price) {
    }

    private final LifestealCore plugin;
    private final Database db;
    private final EconomyManager eco;
    private final Map<Integer, ShopItem> items = new TreeMap<>();
    private final Map<Material, SellPrice> sellPrices = new EnumMap<>(Material.class);

    public ShopManager(LifestealCore plugin, Database db, EconomyManager eco) {
        this.plugin = plugin;
        this.db = db;
        this.eco = eco;
    }

    public void load() throws SQLException {
        items.clear();
        db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT slot, item, money_price, gems_price FROM " + db.t("shop_items"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack item = decode(rs.getString(2));
                    if (item != null) items.put(rs.getInt(1), new ShopItem(item, rs.getDouble(3), rs.getDouble(4)));
                }
            }
            return null;
        });
        if (items.isEmpty() && db.getState("shop_seeded") == null) {

            setItem(10, new ItemStack(Material.DIAMOND_SWORD), 500, 5);
            setItem(12, new ItemStack(Material.NETHERITE_INGOT), 1000, 10);
            db.setState("shop_seeded", "1");
        }
        reloadSellPrices();
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

    public int size() {
        return Math.max(1, Math.min(6, plugin.getConfig().getInt("shop.rows", 3))) * 9;
    }

    public Map<Integer, ShopItem> items() {
        return items;
    }

    public SellPrice sellPrice(Material material) {
        return sellPrices.get(material);
    }

    public EconomyManager eco() {
        return eco;
    }

    public LifestealCore plugin() {
        return plugin;
    }

    public void setItem(int slot, ItemStack item, double money, double gems) {
        ShopItem entry = new ShopItem(item.clone(), Math.max(0, money), Math.max(0, gems));
        items.put(slot, entry);
        String data = encode(entry.item());
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + db.t("shop_items")
                    + " (slot, item, money_price, gems_price) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, slot);
                ps.setString(2, data);
                ps.setDouble(3, entry.moneyPrice());
                ps.setDouble(4, entry.gemsPrice());
                ps.executeUpdate();
            }
        });
    }

    public void removeItem(int slot) {
        items.remove(slot);
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.t("shop_items") + " WHERE slot=?")) {
                ps.setInt(1, slot);
                ps.executeUpdate();
            }
        });
    }

    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public ItemStack decode(String data) {
        if (data == null || data.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (Exception e) {
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
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return Msg.sc(item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    public void buy(Player player, int slot) {
        ShopItem entry = items.get(slot);
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
        return Component.text("[", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                .append(Component.text(Msg.sc(text), net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                .append(Component.text("]", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
    }
}
