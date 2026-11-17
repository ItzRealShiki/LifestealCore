package de.leon.lifesteal.koth;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.worldguard.WorldGuardKothFlags;
import de.leon.lifesteal.worldguard.RegionProtectionModule;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KothManager implements Listener {
    private static final long INTERVAL = 2L * 60L * 60L * 1000L;
    private static final long EVENT_LENGTH = 15L * 60L * 1000L;
    private static final long CAPTURE_LENGTH = 2L * 60L * 1000L;
    private final LifestealCore plugin;
    private final RegionProtectionModule regions;
    private final BossBar bar = BossBar.bossBar(Component.text("KOTH"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private BukkitTask task;
    private long nextStart;
    private long phaseEnds;
    private long captureStarted;
    private UUID capturing;
    private boolean active;
    private boolean overtime;

    public KothManager(LifestealCore plugin, RegionProtectionModule regions) {
        this.plugin = plugin;
        this.regions = regions;
        long saved = plugin.getConfig().getLong("koth.next-start", 0L);
        nextStart = saved > System.currentTimeMillis() ? saved : System.currentTimeMillis() + INTERVAL;
        plugin.getConfig().set("koth.next-start", nextStart);
        plugin.saveConfig();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) player.hideBossBar(bar);
        persistNextStart();
    }

    private void persistNextStart() {
        plugin.getConfig().set("koth.next-start", nextStart);
        plugin.saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active) event.getPlayer().showBossBar(bar);
    }

    private void begin(boolean announce) {
        active = true;
        overtime = false;
        captureStarted = 0L;
        capturing = null;
        phaseEnds = System.currentTimeMillis() + EVENT_LENGTH;
        if (announce) Msg.broadcast(Msg.Tone.GOOD, "KOTH PvP has started! Capture the point within 15 minutes.");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bar);
            if (plugin.settings() == null || plugin.settings().isEnabled(player, "koth-horn")) {
                player.playSound(player.getLocation(), "item.goat_horn.sound.5", 1.0f, 0.65f);
            }
        }
        updateBar();
    }

    private void beginOvertime() {
        overtime = true;
        phaseEnds = System.currentTimeMillis() + EVENT_LENGTH;
        captureStarted = 0L;
        capturing = null;
        Msg.broadcast(Msg.Tone.NEUTRAL, "KOTH overtime! Another 15 minutes to capture the point.");
        playEventSound(Sound.BLOCK_BEACON_ACTIVATE);
        updateBar();
    }

    private void finish(String message) {
        active = false;
        overtime = false;
        capturing = null;
        captureStarted = 0L;
        nextStart = System.currentTimeMillis() + INTERVAL;
        persistNextStart();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
        if (message != null && !message.isBlank()) Msg.broadcast(Msg.Tone.NEUTRAL, message);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (!active) {
            if (now >= nextStart) begin(true);
            return;
        }

        updateCapture(now);
        if (!active) return;
        if (now >= phaseEnds) {
            if (!overtime && anyPlayerInArena()) beginOvertime();
            else finish("KOTH has ended. The next event will start in 2 hours.");
            return;
        }
        updateBar();
    }

    private void updateCapture(long now) {
        List<Player> inside = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && WorldGuardKothFlags.hasFlagAt(player.getLocation(), WorldGuardKothFlags.CAPTURE)) inside.add(player);
        }
        if (inside.size() != 1) {
            capturing = null;
            captureStarted = 0L;
            return;
        }
        Player player = inside.get(0);
        if (capturing == null || !capturing.equals(player.getUniqueId())) {
            capturing = player.getUniqueId();
            captureStarted = now;
        }
        long elapsed = now - captureStarted;
        if (elapsed >= CAPTURE_LENGTH) reward(player);
    }

    private boolean anyPlayerInArena() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (WorldGuardKothFlags.hasFlagAt(player.getLocation(), WorldGuardKothFlags.PVP_ARENA)) return true;
        }
        return false;
    }

    private void reward(Player player) {
        ItemStack ingots = new ItemStack(Material.NETHERITE_INGOT, 2);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        giveOrDrop(player, ingots);
        giveOrDrop(player, sword);
        plugin.economy().addMoney(player.getUniqueId(), 10_000.0);
        Msg.broadcast(Msg.Tone.GOOD, "{player} captured KOTH and won 2 Netherite Ingots, a Diamond Sword and $10,000!",
                "player", player.getName());
        finish("KOTH has been captured!");
    }

    private void giveOrDrop(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private void updateBar() {
        long now = System.currentTimeMillis();
        long remaining = Math.max(0L, phaseEnds - now);
        float progress = Math.max(0f, Math.min(1f, remaining / (float) EVENT_LENGTH));
        String phase = overtime ? "ᴋᴏᴛʜ ᴏᴠᴇʀᴛɪᴍᴇ" : "ᴋᴏᴛʜ ᴘᴠᴘ";
        String captureText = capturing == null ? "ᴄᴀᴘᴛᴜʀᴇ: ᴡᴀɪᴛɪɴɢ" :
                "ᴄᴀᴘᴛᴜʀᴇ: " + Bukkit.getOfflinePlayer(capturing).getName() + "  "
                        + Math.min(120, Math.max(0, (int) ((now - captureStarted) / 1000))) + "/120s";
        bar.name(Component.text("✦ " + phase + "  •  ᴛɪᴍᴇ " + format(remaining) + "  •  " + captureText + " ✦"));
        bar.progress(progress);
        bar.color(overtime ? BossBar.Color.PURPLE : BossBar.Color.RED);
    }

    private void playEventSound(Sound sound) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private static String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public boolean isActive() { return active; }
    public boolean isOvertime() { return overtime; }
    public long remainingMillis() { return active ? Math.max(0L, phaseEnds - System.currentTimeMillis()) : 0L; }
    public long nextStartMillis() { return Math.max(0L, nextStart - System.currentTimeMillis()); }

    public void startFromAdmin() {
        if (!active) begin(true);
    }

    public void skipFromAdmin() {
        if (!active) begin(true);
        else if (!overtime) beginOvertime();
        else finish("KOTH was ended by an administrator.");
    }

    public void endFromAdmin() {
        if (active) finish("KOTH was ended by an administrator.");
    }

    public void overtimeFromAdmin() {
        if (active && !overtime) beginOvertime();
    }
}
