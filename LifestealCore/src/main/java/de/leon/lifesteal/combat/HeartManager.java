package de.leon.lifesteal.combat;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class HeartManager {

    public record HeartInfo(UUID id, UUID origin, String originName) {
    }

    public record HeartRecord(UUID origin, String originName, long time) {
    }

    private static final NamespacedKey KEY_ID = NamespacedKey.fromString("combatplugin:heart_id");
    private static final NamespacedKey KEY_ORIGIN = NamespacedKey.fromString("combatplugin:heart_origin");
    private static final NamespacedKey KEY_ORIGIN_NAME = NamespacedKey.fromString("combatplugin:heart_origin_name");

    private final LifestealCore plugin;
    private final Database db;

    private final Set<UUID> redeeming = ConcurrentHashMap.newKeySet();

    public HeartManager(LifestealCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public int startHearts() {
        return Math.max(1, cfg().getInt("hearts.start", 10));
    }

    public int maxHearts() {
        return Math.max(1, cfg().getInt("hearts.max", 20));
    }

    public int returnHearts() {
        return Math.max(1, Math.min(maxHearts(), cfg().getInt("hearts.return-hearts", 5)));
    }

    public int hearts(Player player) {
        return plugin.data().get(player).hearts();
    }

    public void apply(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        int count = hearts(player);
        if (count <= 0) return;
        double max = count * 2.0;
        if (attr.getBaseValue() != max) attr.setBaseValue(max);
        if (player.getHealth() > attr.getValue()) player.setHealth(attr.getValue());
    }

    public ItemStack visualHeart() {
        String texture = cfg().getString("hearts.item.texture", "");
        boolean head = texture != null && !texture.isBlank();
        Material material = Material.matchMaterial(cfg().getString("hearts.item.material", "RED_DYE"));
        if (material == null) material = Material.RED_DYE;
        if (head) material = Material.PLAYER_HEAD;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item("Heart", Msg.BAD));
        if (head && meta instanceof SkullMeta skull) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture.trim()));
            skull.setPlayerProfile(profile);
        }
        String model = cfg().getString("hearts.item.item-model", "");
        if (model != null && !model.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(model.trim());
            if (key != null) meta.setItemModel(key);
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createHeart(UUID origin, String originName) {
        ItemStack item = visualHeart();
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Msg.item("From: {n}", Msg.NEUTRAL, "n", originName),
                Msg.item("Right-click to gain a heart", Msg.GOOD)));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_ID, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(KEY_ORIGIN, PersistentDataType.STRING, origin.toString());
        pdc.set(KEY_ORIGIN_NAME, PersistentDataType.STRING, originName);
        item.setItemMeta(meta);
        return item;
    }

    public HeartInfo read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(KEY_ID, PersistentDataType.STRING);
        String origin = pdc.get(KEY_ORIGIN, PersistentDataType.STRING);
        String originName = pdc.get(KEY_ORIGIN_NAME, PersistentDataType.STRING);
        if (id == null || origin == null) return null;
        try {
            return new HeartInfo(UUID.fromString(id), UUID.fromString(origin), originName == null ? "Unknown" : originName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void redeem(Player player, HeartInfo info, AltChecker alt) {
        UUID uid = player.getUniqueId();
        if (!redeeming.add(uid)) return;

        boolean checkAlt = alt.enabled() && !info.origin().equals(uid) && !player.hasPermission(AltChecker.BYPASS_PERMISSION);
        if (checkAlt && alt.sameIp(info.origin(), uid)) {
            redeeming.remove(uid);
            Msg.err(player, "You can't redeem hearts of a player with the same IP as you");
            return;
        }
        if (hearts(player) >= maxHearts()) {
            redeeming.remove(uid);
            Msg.err(player, "You already have the maximum of {n} hearts", "n", maxHearts());
            return;
        }
        String myHash = checkAlt ? plugin.data().get(player).ipHash() : null;

        tryRedeem(info, myHash).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            redeeming.remove(uid);
            if (error != null) {
                plugin.getLogger().warning("Heart redeem failed: " + error.getMessage());
                if (player.isOnline()) Msg.err(player, "Something went wrong, please try again");
                return;
            }
            if (!player.isOnline()) {
                if (result == REDEEM_OK) unmarkRedeemed(info.id());
                return;
            }
            if (result == REDEEM_SAME_IP) {
                Msg.err(player, "You can't redeem hearts of a player with the same IP as you");
                return;
            }
            if (result == REDEEM_USED) {
                removeHeartItem(player, info.id());
                Msg.err(player, "This heart was already redeemed and is worthless");
                return;
            }
            PlayerData data = plugin.data().get(player);
            int current = data.hearts();
            if (current >= maxHearts() || !removeHeartItem(player, info.id())) {
                unmarkRedeemed(info.id());
                if (current >= maxHearts()) Msg.err(player, "You already have the maximum of {n} hearts", "n", maxHearts());
                return;
            }
            data.setHearts(current + 1);
            addHistory(uid, info.origin(), info.originName());
            apply(player);
            AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) player.setHealth(Math.min(attr.getValue(), player.getHealth() + 2.0));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            Msg.ok(player, "You gained a heart! You now have {n} hearts", "n", current + 1);
        }));
    }

    private boolean removeHeartItem(Player player, UUID heartId) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            HeartInfo info = read(contents[i]);
            if (info != null && info.id().equals(heartId)) {
                ItemStack stack = contents[i];
                inv.setItem(i, stack.getAmount() <= 1 ? null : stack.asQuantity(stack.getAmount() - 1));
                return true;
            }
        }
        return false;
    }

    private static final int REDEEM_OK = 0, REDEEM_USED = 1, REDEEM_SAME_IP = 2;

    private CompletableFuture<Integer> tryRedeem(HeartInfo info, String myHash) {
        return db.query(c -> {
            if (myHash != null) {
                try (PreparedStatement ps = c.prepareStatement("SELECT ip_hash FROM " + db.t("players") + " WHERE uuid=?")) {
                    ps.setString(1, info.origin().toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && myHash.equals(rs.getString(1))) return REDEEM_SAME_IP;
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(db.insertIgnore() + " INTO " + db.t("redeemed_hearts")
                    + " (heart_id) VALUES (?)")) {
                ps.setString(1, info.id().toString());
                return ps.executeUpdate() > 0 ? REDEEM_OK : REDEEM_USED;
            }
        });
    }

    private void unmarkRedeemed(UUID heartId) {
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.t("redeemed_hearts") + " WHERE heart_id=?")) {
                ps.setString(1, heartId.toString());
                ps.executeUpdate();
            }
        });
    }

    private void addHistory(UUID uuid, UUID origin, String originName) {
        long now = System.currentTimeMillis();
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + db.t("heart_history")
                    + " (uuid, origin, origin_name, time) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, origin.toString());
                ps.setString(3, originName);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<HeartRecord>> history(UUID uuid) {
        return db.query(c -> {
            List<HeartRecord> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT origin, origin_name, time FROM " + db.t("heart_history")
                    + " WHERE uuid=? ORDER BY time DESC, id DESC")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            list.add(new HeartRecord(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                        } catch (IllegalArgumentException ignored) {

                        }
                    }
                }
            }
            return list;
        });
    }
}
