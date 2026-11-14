package de.leon.lifesteal.manage;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Base64;

public final class ManageManager implements Listener, org.bukkit.command.TabExecutor {
    private static final String ADMIN = "Admin.Core";
    private static final String SUPPORT = "Support.Core";
    private static final int SIZE = 54;
    private final LifestealCore plugin;
    private final Map<UUID, Menu> menus = new ConcurrentHashMap<>();
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final BukkitTask snapshotTask;

    private enum Screen { STATS, EDITOR, INVENTORY, ENDER }
    private record Menu(Screen screen, UUID target, boolean editable) implements InventoryHolder {
        private static final Map<Menu, Inventory> INVENTORIES = new ConcurrentHashMap<>();
        @Override public Inventory getInventory() { return INVENTORIES.get(this); }
    }
    private record View(UUID target, boolean ender, boolean editable) { }

    public ManageManager(LifestealCore plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "manage_gui");
        try {
            plugin.database().sync(c -> {
                try (var st = c.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS " + plugin.database().t("manage_snapshots") + " ("
                            + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16), kills INT NOT NULL DEFAULT 0,"
                            + "deaths INT NOT NULL DEFAULT 0, playtime BIGINT NOT NULL DEFAULT 0, xp INT NOT NULL DEFAULT 0,"
                            + "level INT NOT NULL DEFAULT 0, progress DOUBLE NOT NULL DEFAULT 0, inventory TEXT, ender TEXT,"
                            + "pending INT NOT NULL DEFAULT 0, updated BIGINT NOT NULL DEFAULT 0)");
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create Manage storage", e);
        }
        this.snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::savePlayer), 1200L, 1200L);
    }

    public void shutdown() {
        snapshotTask.cancel();
        Bukkit.getOnlinePlayers().forEach(this::savePlayer);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        if (name.equals("stats")) {
            if (args.length == 0) { openStats(viewer, viewer.getUniqueId(), false); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("editor")) {
                if (!viewer.hasPermission(ADMIN)) { Msg.err(viewer, "You do not have permission to edit player data"); return true; }
                openStats(viewer, viewer.getUniqueId(), true);
                return true;
            }
            String targetName = args[0];
            boolean editor = args.length > 1 && args[1].equalsIgnoreCase("editor");
            if (editor && !viewer.hasPermission(ADMIN)) { Msg.err(viewer, "You do not have permission to edit player data"); return true; }
            plugin.data().findUuid(targetName).thenAccept(uuid -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (uuid == null) { Msg.err(viewer, "Player {p} was not found", "p", targetName); return; }
                openStats(viewer, uuid, editor);
            }));
            return true;
        }
        if (!viewer.hasPermission(ADMIN) && !viewer.hasPermission(SUPPORT)) {
            Msg.err(viewer, "You do not have permission to use this command");
            return true;
        }
        if (args.length != 1) { Msg.info(viewer, "Usage: /" + name + " <player>"); return true; }
        plugin.data().findUuid(args[0]).thenAccept(uuid -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) return;
            if (uuid == null) { Msg.err(viewer, "Player {p} was not found", "p", args[0]); return; }
            openPlayerInventory(viewer, uuid, name.equals("endersee"));
        }));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
        }
        if (command.getName().equalsIgnoreCase("stats") && args.length == 2 && sender.hasPermission(ADMIN)) return List.of("editor");
        return List.of();
    }

    private void openStats(Player viewer, UUID targetId, boolean editor) {
        Player target = Bukkit.getPlayer(targetId);
        plugin.data().peek(targetId).thenCombine(readSnapshot(targetId), (data, snapshot) -> new Object[]{data, snapshot})
                .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) return;
                    PlayerData data = (PlayerData) result[0];
                    Snapshot snap = (Snapshot) result[1];
                    if (data == null) { Msg.err(viewer, "No saved player data was found"); return; }
                    int kills = target == null ? snap.kills : target.getStatistic(Statistic.PLAYER_KILLS);
                    int deaths = target == null ? snap.deaths : target.getStatistic(Statistic.DEATHS);
                    long playtime = target == null ? snap.playtime : target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
                    int xp = target == null ? snap.xp : target.getTotalExperience();
                    int level = target == null ? snap.level : target.getLevel();
                    Menu menu = new Menu(editor ? Screen.EDITOR : Screen.STATS, targetId, editor);
                    Inventory inv = Bukkit.createInventory(menu, 27, "ᴘʟᴀʏᴇʀ sᴛᴀᴛs: " + safeName(data.name()));
                    Menu.INVENTORIES.put(menu, inv);
                    item(inv, 10, Material.GOLD_INGOT, "ᴍᴏɴᴇʏ", List.of(fmt(data.balance(Currency.MONEY))));
                    item(inv, 11, Material.EMERALD, "ɢᴇᴍs", List.of(fmt(data.balance(Currency.GEMS))));
                    item(inv, 12, Material.RED_DYE, "ʜᴇᴀʀᴛs", List.of(String.valueOf(data.hearts())));
                    item(inv, 13, Material.IRON_SWORD, "ᴋɪʟʟs", List.of(String.valueOf(kills)));
                    item(inv, 14, Material.SKELETON_SKULL, "ᴅᴇᴀᴛʜs", List.of(String.valueOf(deaths)));
                    item(inv, 15, Material.CLOCK, "ᴘʟᴀʏᴛɪᴍᴇ", List.of(formatTime(playtime)));
                    item(inv, 16, Material.EXPERIENCE_BOTTLE, "ᴇxᴘ", List.of("Total: " + xp, "Level: " + level));
                    if (editor) {
                        item(inv, 18, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ᴍᴏɴᴇʏ", List.of("Click to enter a new value"));
                        item(inv, 19, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ɢᴇᴍs", List.of("Click to enter a new value"));
                        item(inv, 20, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ʜᴇᴀʀᴛs", List.of("Click to enter a new value"));
                        item(inv, 21, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ᴋɪʟʟs", List.of("Click to enter a new value"));
                        item(inv, 22, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ᴅᴇᴀᴛʜs", List.of("Click to enter a new value"));
                        item(inv, 23, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ᴘʟᴀʏᴛɪᴍᴇ", List.of("Value in seconds"));
                        item(inv, 24, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ᴇxᴘ", List.of("Total experience points"));
                        item(inv, 25, Material.WRITABLE_BOOK, "ᴇᴅɪᴛ ʟᴇᴠᴇʟ", List.of("Player level"));
                    }
                    menus.put(viewer.getUniqueId(), menu);
                    viewer.openInventory(inv);
                }));
    }

    private void openPlayerInventory(Player viewer, UUID targetId, boolean ender) {
        boolean editable = viewer.hasPermission(ADMIN);
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            ItemStack[] contents = ender ? target.getEnderChest().getContents() : target.getInventory().getStorageContents();
            openInventoryEditor(viewer, targetId, ender, editable, contents);
            return;
        }
        readSnapshot(targetId).thenAccept(snapshot -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) return;
            ItemStack[] contents = deserialize(ender ? snapshot.ender : snapshot.inventory);
            openInventoryEditor(viewer, targetId, ender, editable, contents);
        }));
    }

    private void openInventoryEditor(Player viewer, UUID targetId, boolean ender, boolean editable, ItemStack[] contents) {
        Screen screen = ender ? Screen.ENDER : Screen.INVENTORY;
        Menu menu = new Menu(screen, targetId, editable);
        Inventory inv = Bukkit.createInventory(menu, SIZE, (ender ? "ᴇɴᴅᴇʀ ᴄʜᴇsᴛ: " : "ɪɴᴠᴇɴᴛᴏʀʏ: ") + targetName(targetId));
        Menu.INVENTORIES.put(menu, inv);
        int limit = ender ? 27 : Math.min(36, contents.length);
        for (int i = 0; i < limit; i++) if (i < contents.length && contents[i] != null) inv.setItem(i, contents[i].clone());
        if (!editable) item(inv, 49, Material.BARRIER, "ʀᴇᴀᴅ-ᴏɴʟʏ", List.of("Permission: Support.Core"));
        else item(inv, 49, Material.LIME_DYE, "ᴇᴅɪᴛᴀʙʟᴇ", List.of("Changes are saved when you close this menu"));
        item(inv, 53, Material.BARRIER, "ᴄʟᴏsᴇ", List.of());
        menus.put(viewer.getUniqueId(), menu);
        viewer.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (menu.screen == Screen.STATS || menu.screen == Screen.EDITOR) {
            if (menu.screen != Screen.EDITOR || !viewer.hasPermission(ADMIN)) return;
            String field = switch (slot) {
                case 18 -> "money"; case 19 -> "gems"; case 20 -> "hearts"; case 21 -> "kills";
                case 22 -> "deaths"; case 23 -> "playtime"; case 24 -> "xp"; case 25 -> "level"; default -> null;
            };
            if (field != null) {
                viewer.closeInventory();
                Msg.info(viewer, "Enter the new {f} value in chat. Type cancel to abort.", "f", field);
                plugin.chatInput().request(viewer, input -> editField(viewer, menu.target, field, input));
            }
            return;
        }
        if (!menu.editable || !viewer.hasPermission(ADMIN)) return;
        if (slot == 53) { viewer.closeInventory(); return; }
        if (slot < 0 || slot >= 45) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        Inventory top = event.getView().getTopInventory();
        if (event.getClick().isLeftClick()) top.setItem(slot, cursor == null || cursor.getType().isAir() ? null : cursor.clone());
        else if (event.getClick().isRightClick() && current != null) {
            if (cursor == null || cursor.getType().isAir()) event.getView().setCursor(current.clone());
            top.setItem(slot, null);
        }
        event.setCursor(null);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        menus.remove(viewer.getUniqueId());
        if (menu.screen != Screen.INVENTORY && menu.screen != Screen.ENDER) return;
        if (!menu.editable || !viewer.hasPermission(ADMIN)) return;
        ItemStack[] contents = event.getView().getTopInventory().getContents();
        ItemStack[] compact = Arrays.copyOf(contents, menu.screen == Screen.ENDER ? 27 : 36);
        String encoded = serialize(compact);
        saveInventory(menu.target, menu.screen == Screen.ENDER, encoded);
        Player target = Bukkit.getPlayer(menu.target);
        if (target != null) {
            if (menu.screen == Screen.ENDER) target.getEnderChest().setContents(Arrays.copyOf(compact, 27));
            else target.getInventory().setStorageContents(Arrays.copyOf(compact, 36));
            target.saveData();
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        savePlayer(p);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> applyPending(p));
    }

    private void editField(Player actor, UUID id, String field, String raw) {
        try {
            if (field.equals("money") || field.equals("gems")) {
                double value = Double.parseDouble(raw.replace(",", "."));
                if (!Double.isFinite(value) || value < 0) throw new NumberFormatException();
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    PlayerData d = plugin.data().get(online);
                    Currency currency = field.equals("money") ? Currency.MONEY : Currency.GEMS;
                    d.change(currency, value - d.balance(currency));
                } else {
                    String col = field;
                    plugin.database().run(c -> { try (PreparedStatement ps = c.prepareStatement("UPDATE " + plugin.database().t("players") + " SET " + col + "=? WHERE uuid=?")) { ps.setDouble(1, value); ps.setString(2, id.toString()); ps.executeUpdate(); } });
                }
            } else if (field.equals("hearts")) {
                int value = bounded(raw, 0, 1000);
                Player online = Bukkit.getPlayer(id);
                if (online != null) { plugin.data().get(online).setHearts(value); if (plugin.combat() != null) { var attr = online.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH); if (attr != null) { attr.setBaseValue(Math.max(2, value * 2.0)); online.setHealth(Math.min(online.getHealth(), attr.getValue())); } } }
                else updateColumn(id, "hearts", value);
            } else if (field.equals("kills") || field.equals("deaths")) {
                int value = bounded(raw, 0, 2000000000);
                Player online = Bukkit.getPlayer(id);
                if (online != null) online.setStatistic(field.equals("kills") ? Statistic.PLAYER_KILLS : Statistic.DEATHS, value);
                updateSnapshotNumber(id, field, value);
            } else if (field.equals("playtime")) {
                long value = Long.parseLong(raw);
                if (value < 0 || value > Integer.MAX_VALUE) throw new NumberFormatException();
                Player online = Bukkit.getPlayer(id);
                if (online != null) online.setStatistic(Statistic.PLAY_ONE_MINUTE, (int) Math.min(Integer.MAX_VALUE, value * 20L));
                updateSnapshotNumber(id, field, value);
            } else if (field.equals("xp") || field.equals("level")) {
                int value = bounded(raw, 0, 2000000000);
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    if (field.equals("xp")) online.setTotalExperience(value);
                    else online.setLevel(value);
                }
                updateSnapshotNumber(id, field, value);
            }
            Msg.ok(actor, "Updated {f} for {p}", "f", field, "p", targetName(id));
            openStats(actor, id, true);
        } catch (NumberFormatException ex) {
            Msg.err(actor, "Invalid value. Enter a valid non-negative number.");
        }
    }

    private void updateColumn(UUID id, String column, int value) {
        plugin.database().run(c -> { try (PreparedStatement ps = c.prepareStatement("UPDATE " + plugin.database().t("players") + " SET " + column + "=? WHERE uuid=?")) { ps.setInt(1, value); ps.setString(2, id.toString()); ps.executeUpdate(); } });
    }

    private void updateSnapshotNumber(UUID id, String field, long value) {
        String col = switch (field) { case "kills" -> "kills"; case "deaths" -> "deaths"; case "playtime" -> "playtime"; case "xp" -> "xp"; case "level" -> "level"; default -> null; };
        if (col == null) return;
        plugin.database().run(c -> { try (PreparedStatement ps = c.prepareStatement("UPDATE " + plugin.database().t("manage_snapshots") + " SET " + col + "=?,pending=1 WHERE uuid=?")) { ps.setLong(1, value); ps.setString(2, id.toString()); ps.executeUpdate(); } });
    }

    private void savePlayer(Player p) {
        String inv = serialize(p.getInventory().getStorageContents());
        String ender = serialize(p.getEnderChest().getContents());
        int kills = p.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = p.getStatistic(Statistic.DEATHS);
        long playtime = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        int xp = p.getTotalExperience(), level = p.getLevel();
        double progress = p.getExp();
        plugin.database().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + plugin.database().t("manage_snapshots")
                    + " (uuid,name,kills,deaths,playtime,xp,level,progress,inventory,ender,pending,updated) VALUES (?,?,?,?,?,?,?,?,?,?,0,?)")) {
                ps.setString(1, p.getUniqueId().toString()); ps.setString(2, p.getName());
                ps.setInt(3, kills); ps.setInt(4, deaths); ps.setLong(5, playtime); ps.setInt(6, xp);
                ps.setInt(7, level); ps.setDouble(8, progress); ps.setString(9, inv); ps.setString(10, ender);
                ps.setLong(11, System.currentTimeMillis()); ps.executeUpdate();
            }
        });
    }

    private CompletableFuture<Snapshot> readSnapshot(UUID id) {
        return plugin.database().query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT kills,deaths,playtime,xp,level,progress,inventory,ender FROM " + plugin.database().t("manage_snapshots") + " WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return new Snapshot();
                    Snapshot s = new Snapshot(); s.kills=rs.getInt(1); s.deaths=rs.getInt(2); s.playtime=rs.getLong(3);
                    s.xp=rs.getInt(4); s.level=rs.getInt(5); s.progress=rs.getDouble(6); s.inventory=rs.getString(7); s.ender=rs.getString(8); return s;
                }
            }
        });
    }

    private void saveInventory(UUID id, boolean ender, String encoded) {
        String col = ender ? "ender" : "inventory";
        plugin.database().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + plugin.database().t("manage_snapshots") + " SET " + col + "=?,pending=1,updated=? WHERE uuid=?")) {
                ps.setString(1, encoded); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, id.toString()); ps.executeUpdate();
            }
        });
    }

    private void applyPending(Player p) {
        readSnapshot(p.getUniqueId()).thenAccept(s -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (s.inventory == null && s.ender == null) return;
            // Offline editor snapshots are only applied when the corresponding snapshot is marked pending.
            plugin.database().query(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT pending FROM " + plugin.database().t("manage_snapshots") + " WHERE uuid=?")) {
                    ps.setString(1, p.getUniqueId().toString()); try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) != 0; }
                }
            }).thenAccept(pending -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!pending || !p.isOnline()) return;
                ItemStack[] inv = deserialize(s.inventory), ender = deserialize(s.ender);
                if (inv != null) p.getInventory().setStorageContents(Arrays.copyOf(inv, 36));
                if (ender != null) p.getEnderChest().setContents(Arrays.copyOf(ender, 27));
                p.setTotalExperience(s.xp); p.setLevel(s.level); p.setExp((float) Math.max(0, Math.min(1, s.progress)));
                p.setStatistic(Statistic.PLAYER_KILLS, s.kills); p.setStatistic(Statistic.DEATHS, s.deaths);
                p.setStatistic(Statistic.PLAY_ONE_MINUTE, (int) Math.min(Integer.MAX_VALUE, s.playtime * 20L));
                p.saveData();
                plugin.database().run(c -> { try (PreparedStatement ps = c.prepareStatement("UPDATE " + plugin.database().t("manage_snapshots") + " SET pending=0 WHERE uuid=?")) { ps.setString(1, p.getUniqueId().toString()); ps.executeUpdate(); } });
            }));
        }));
    }

    private static String serialize(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(items.length); for (ItemStack item : items) out.writeObject(item);
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) { throw new IllegalStateException("Could not serialize inventory", e); }
    }
    private static ItemStack[] deserialize(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(encoded)); BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            int length = in.readInt(); if (length < 0 || length > 128) return null;
            ItemStack[] result = new ItemStack[length]; for (int i=0;i<length;i++) result[i]=(ItemStack) in.readObject(); return result;
        } catch (Exception e) { return null; }
    }

    private static void item(Inventory inv, int slot, Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("§f" + name); meta.setLore(lore.stream().map(s -> "§7" + s).toList()); stack.setItemMeta(meta); inv.setItem(slot, stack);
    }
    private static String fmt(double n) { return String.format(Locale.US, "%,.2f", n); }
    private static String formatTime(long seconds) { long h=seconds/3600; return (h/24)+"d "+(h%24)+"h "+((seconds%3600)/60)+"m"; }
    private static int bounded(String raw, int min, int max) { int v=Integer.parseInt(raw); if(v<min||v>max) throw new NumberFormatException(); return v; }
    private String targetName(UUID id) { Player p=Bukkit.getPlayer(id); return p==null?id.toString().substring(0,8):p.getName(); }
    private static String safeName(String name) { return name == null ? "unknown" : name; }
    private static final class Snapshot { int kills=0,deaths=0,xp=0,level=0; long playtime=0; double progress=0; String inventory,ender; }
}
