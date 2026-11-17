package de.leon.lifesteal.koth;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import de.leon.lifesteal.worldguard.RegionProtectionModule;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private TextDisplay rewardHologram;
    private final Set<UUID> actionBarViewers = new HashSet<>();

    public KothManager(LifestealCore plugin, RegionProtectionModule regions) {
        this.plugin = plugin;
        this.regions = regions;
        long now = System.currentTimeMillis();
        long saved = plugin.getConfig().getLong("koth.next-start", 0L);
        nextStart = saved > 0L ? saved : now + INTERVAL;
        active = plugin.getConfig().getBoolean("koth.active", false);
        phaseEnds = plugin.getConfig().getLong("koth.phase-ends", 0L);
        overtime = plugin.getConfig().getBoolean("koth.overtime", false);
        // Capture progress is intentionally not restored across restarts.
        capturing = null;
        captureStarted = 0L;
        if (active && phaseEnds <= 0L) {
            active = false;
            overtime = false;
            nextStart = now + INTERVAL;
        }
        persistState();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (active) {
            spawnRewardHologram();
            for (Player player : Bukkit.getOnlinePlayers()) player.showBossBar(bar);
            updateBar();
        }
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        removeRewardHologram();
        for (Player player : Bukkit.getOnlinePlayers()) player.hideBossBar(bar);
        persistState();
    }

    private void persistState() {
        plugin.getConfig().set("koth.next-start", nextStart);
        plugin.getConfig().set("koth.active", active);
        plugin.getConfig().set("koth.phase-ends", active ? phaseEnds : 0L);
        plugin.getConfig().set("koth.overtime", active && overtime);
        plugin.saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active) {
            event.getPlayer().showBossBar(bar);
            updateBar();
        }
    }

    private void begin(boolean announce) {
        active = true;
        overtime = false;
        captureStarted = 0L;
        capturing = null;
        phaseEnds = System.currentTimeMillis() + EVENT_LENGTH;
        persistState();
        spawnRewardHologram();
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
        persistState();
        captureStarted = 0L;
        capturing = null;
        Msg.broadcast(Msg.Tone.NEUTRAL, "KOTH overtime! Another 15 minutes to capture the point.");
        playEventSound(Sound.BLOCK_BEACON_ACTIVATE);
        updateBar();
    }

    private void finish(String message) {
        active = false;
        overtime = false;
        removeRewardHologram();
        capturing = null;
        captureStarted = 0L;
        nextStart = System.currentTimeMillis() + INTERVAL;
        persistState();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
            player.sendActionBar(Component.empty());
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
        actionBarViewers.clear();
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
                    && regions.hasFlagAt(player.getLocation(), "KOTH_CAPTURE")) inside.add(player);
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
            if (regions.hasFlagAt(player.getLocation(), "KOTH_PVP_ARENA")) return true;
        }
        return false;
    }

    private ItemStack kothReward() {
        Material material=Material.matchMaterial(plugin.getConfig().getString("koth.reward-material","NETHERITE_INGOT"));
        if(material==null||!material.isItem())material=Material.NETHERITE_INGOT;
        return new ItemStack(material,1);
    }

    private void spawnRewardHologram() {
        removeRewardHologram();
        var capture = regions.kothRegions().stream()
                .filter(RegionProtectionModule.KothRegionInfo::capture)
                .findFirst().orElse(null);
        if (capture == null || capture.teleportLocation() == null
                || capture.teleportLocation().getWorld() == null) return;

        Location location = capture.teleportLocation().clone().add(0.0, 1.6, 0.0);
        rewardHologram = location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.setText(hologramComponent(System.currentTimeMillis()));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setLineWidth(320);
            display.setPersistent(false);
            display.setInvulnerable(true);
        });
    }

    private void removeRewardHologram() {
        if (rewardHologram != null) {
            if (rewardHologram.isValid()) rewardHologram.remove();
            rewardHologram = null;
        }
    }

    private void reward(Player player) {
        ItemStack reward=kothReward();
        giveOrDrop(player,reward);
        Msg.broadcast(Msg.Tone.GOOD, "{player} captured KOTH and won 1x {reward}!",
                "player", player.getName(),"reward",reward.getType().name().replace('_',' '));
        finish("KOTH has been captured!");
    }

    private void giveOrDrop(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private String captureName() {
        if (capturing == null) return "ᴡᴀɪᴛɪɴɢ";
        String name = Bukkit.getOfflinePlayer(capturing).getName();
        return Msg.sc(name == null || name.isBlank() ? "Unknown" : name);
    }

    private long captureRemaining(long now) {
        if (capturing == null || captureStarted <= 0L) return -1L;
        return Math.max(0L, CAPTURE_LENGTH - (now - captureStarted));
    }

    private Component captureComponent(long now) {
        if (capturing == null) {
            return Component.text("WARTET AUF CAPTURE", NamedTextColor.GRAY);
        }
        String name = Bukkit.getOfflinePlayer(capturing).getName();
        return Component.text(name == null || name.isBlank() ? "Unknown" : name, NamedTextColor.AQUA)
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(format(captureRemaining(now)), NamedTextColor.GREEN));
    }

    private Component statusComponent(long now) {
        return Component.text("KOTH ", NamedTextColor.GOLD)
                .append(Component.text(format(Math.max(0L, phaseEnds - now)), NamedTextColor.YELLOW))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("CAPTURE ", NamedTextColor.LIGHT_PURPLE))
                .append(captureComponent(now));
    }

    public String scoreboardStatus() {
        if (!active) {
            long remainingMinutes = Math.max(0L, (nextStart - System.currentTimeMillis() + 59_999L) / 60_000L);
            long hours = remainingMinutes / 60L;
            long minutes = remainingMinutes % 60L;
            return Msg.sc(hours + "h " + minutes + "min");
        }
        long now = System.currentTimeMillis();
        return format(Math.max(0L, phaseEnds - now)) + " - " + Msg.sc("Capture")
                + ": " + captureName() + " (" + (captureRemaining(now) < 0L
                ? "waiting" : format(captureRemaining(now))) + ")";
    }

    private Component hologramComponent(long now) {
        ItemStack reward = kothReward();
        Component heading = Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text(overtime ? "KOTH OVERTIME" : "KOTH", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("  " + format(Math.max(0L, phaseEnds - now)), NamedTextColor.YELLOW))
                .append(Component.text(" ✦", NamedTextColor.GOLD));
        Component rewardLine = Component.text("REWARD  ", NamedTextColor.GRAY)
                .append(Component.text("1x " + reward.getType().name().replace('_', ' '), NamedTextColor.AQUA));
        Component captureLine = Component.text("CAPTURING  ", NamedTextColor.LIGHT_PURPLE)
                .append(captureComponent(now));
        return heading.append(Component.newline()).append(captureLine)
                .append(Component.newline()).append(rewardLine);
    }

    private void updateBar() {
        long now = System.currentTimeMillis();
        long remaining = Math.max(0L, phaseEnds - now);
        float progress = Math.max(0f, Math.min(1f, remaining / (float) EVENT_LENGTH));
        Component status = statusComponent(now);
        bar.name(status);
        bar.progress(progress);
        bar.color(overtime ? BossBar.Color.PURPLE : BossBar.Color.RED);

        if (rewardHologram != null && rewardHologram.isValid()) {
            rewardHologram.text(hologramComponent(now));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean inArena = !player.isDead()
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && regions.hasFlagAt(player.getLocation(), "KOTH_PVP_ARENA");
            if (inArena) {
                player.sendActionBar(status);
                actionBarViewers.add(player.getUniqueId());
            } else if (actionBarViewers.remove(player.getUniqueId())) {
                player.sendActionBar(Component.empty());
            }
        }
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
