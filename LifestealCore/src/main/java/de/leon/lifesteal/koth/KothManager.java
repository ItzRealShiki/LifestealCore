package de.leon.lifesteal.koth;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KothManager implements TabExecutor {
    private static final long INTERVAL = 2L * 60L * 60L * 1000L;
    private static final long EVENT_LENGTH = 15L * 60L * 1000L;
    private static final long CAPTURE_LENGTH = 2L * 60L * 1000L;
    private final LifestealCore plugin;
    private final RegionProtectionModule regions;
    private final BossBar bar = BossBar.bossBar(Component.text("KOTH"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
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
                player.playSound(player.getLocation(), "item.goat_horn.sound.0", 1.0f, 0.65f);
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
        updateBar();
    }

    private void finish(String message) {
        active = false;
        overtime = false;
        capturing = null;
        captureStarted = 0L;
        nextStart = System.currentTimeMillis() + INTERVAL;
        persistNextStart();
        for (Player player : Bukkit.getOnlinePlayers()) player.hideBossBar(bar);
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
            if (regions.hasFlagAt(player.getLocation(), "koth-capture")) inside.add(player);
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
            if (regions.hasFlagAt(player.getLocation(), "koth-pvp-arena")) return true;
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
        String phase = overtime ? "KOTH OVERTIME" : "KOTH PVP";
        String captureText = capturing == null ? "No capture" :
                "Capturing: " + Bukkit.getOfflinePlayer(capturing).getName() + " (" +
                        Math.min(120, Math.max(0, (int) ((now - captureStarted) / 1000))) + "/120s)";
        bar.name(Component.text(phase + " | " + format(remaining) + " | " + captureText));
        bar.progress(progress);
        bar.color(overtime ? BossBar.Color.PURPLE : BossBar.Color.RED);
    }

    private static String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("Admin.Core") || sender.hasPermission("Owner.Core");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAdmin(sender)) {
            Msg.err(sender, "Admin.Core required");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            Msg.info(sender, active ? (overtime ? "KOTH overtime is active." : "KOTH is active.") :
                    "Next KOTH starts in " + format(nextStart - System.currentTimeMillis()) + ".");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (active) Msg.err(sender, "KOTH is already active");
                else begin(true);
            }
            case "skip" -> {
                if (active && !overtime && anyPlayerInArena()) beginOvertime();
                else if (active) finish("KOTH was skipped by an administrator.");
                else begin(true);
                Msg.ok(sender, "KOTH timer updated");
            }
            default -> Msg.info(sender, "Usage: /koth [status|start|skip]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdmin(sender) || args.length != 1) return List.of();
        return List.of("status", "start", "skip").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase())).toList();
    }
}
