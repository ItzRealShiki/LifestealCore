package de.leon.lifesteal.diagnostics;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LagMonitor implements Listener, TabExecutor {

    private static final String TITLE = "LifestealCore | Lag Monitor";
    private final LifestealCore plugin;
    private BukkitTask sampleTask;
    private Sample latest;
    private Sample previous;

    private record Sample(long time, double tps, long usedMemory, long maxMemory,
                          int players, int chunks, int tasks) { }

    private static final class MonitorHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public LagMonitor(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (sampleTask != null) sampleTask.cancel();
        takeSample();
        sampleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::takeSample, 100L, 100L);
    }

    public void shutdown() {
        if (sampleTask != null) {
            sampleTask.cancel();
            sampleTask = null;
        }
    }

    private void takeSample() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        double[] tpsValues = Bukkit.getTPS();
        double tps = tpsValues.length == 0 ? 20.0 : Math.min(20.0, tpsValues[0]);
        int chunks = 0;
        for (World world : Bukkit.getWorlds()) chunks += world.getLoadedChunks().length;
        Sample next = new Sample(System.currentTimeMillis(), tps, used, max,
                Bukkit.getOnlinePlayers().size(), chunks, Bukkit.getScheduler().getPendingTasks().size());
        previous = latest;
        latest = next;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MonitorHolder) refreshMetrics(top);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("Owner.Core")) {
            Msg.err(sender, "Owner.Core is required");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            Msg.info(sender, "[Lag Monitor] {s}", "s", statusLine());
            for (String warning : warnings()) Msg.err(sender, warning);
            if (warnings().isEmpty()) Msg.info(sender, "No unusual values detected");
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("gui") && !args[0].equalsIgnoreCase("refresh")) {
            Msg.info(sender, "Usage: /lag [gui|status|refresh]");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can open the GUI. Use /lag status instead.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("refresh")) takeSample();
        open(player);
        return true;
    }

    private void open(Player player) {
        if (latest == null) takeSample();
        MonitorHolder holder = new MonitorHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Msg.smallCaps(net.kyori.adventure.text.Component.text(TITLE)));
        holder.inventory = inventory;

        refreshMetrics(inventory);
        player.openInventory(inventory);
    }

    private void refreshMetrics(Inventory inventory) {
        if (latest == null) return;
        put(inventory, 10, Material.CLOCK, "§eServer TPS", valueTps(),
                "§7Warning when TPS stays low.");
        put(inventory, 11, Material.REDSTONE, "§eMemory", valueMemory(),
                "§7Used heap memory compared to the maximum.");
        put(inventory, 12, Material.PLAYER_HEAD, "§ePlayers", String.valueOf(latest.players()),
                "§7Players currently connected.");
        put(inventory, 13, Material.ENDER_PEARL, "§eLoaded Chunks", String.valueOf(latest.chunks()),
                "§7Chunks currently loaded across all worlds.");
        put(inventory, 14, Material.HOPPER, "§eScheduler Tasks", String.valueOf(latest.tasks()),
                "§7Currently registered Bukkit tasks.");
        put(inventory, 15, Material.COMPARATOR, "§eWarnings", warnings().isEmpty() ? "§aNone detected" : "§c" + warnings().size() + " Hinweis(e)",
                "§7Compares measurements with the previous sample.",
                "§7" + (warnings().isEmpty() ? "No thresholds exceeded." : "Details: /lag status"));
        put(inventory, 22, Material.LIME_DYE, "§aRefresh Measurements",
                "§7Last sample: " + age(), "§eClick to refresh");
        put(inventory, 26, Material.BARRIER, "§cClose", "§7Close this menu");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MonitorHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 22) {
            takeSample();
            open(player);
        } else if (event.getRawSlot() == 26) {
            player.closeInventory();
        }
    }

    private static void put(Inventory inventory, int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Msg.sc(name));
        meta.setLore(List.of(lore).stream().map(Msg::sc).toList());
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private String valueTps() {
        return String.format(Locale.US, "%.2f", latest.tps());
    }

    private String valueMemory() {
        return formatMb(latest.usedMemory()) + " / " + formatMb(latest.maxMemory())
                + " (" + String.format(Locale.US, "%.1f", latest.usedMemory() * 100.0 / Math.max(1, latest.maxMemory())) + "%)";
    }

    private String statusLine() {
        return "TPS " + valueTps() + " | RAM " + valueMemory() + " | Players " + latest.players()
                + " | Chunks " + latest.chunks() + " | Tasks " + latest.tasks();
    }

    private List<String> warnings() {
        List<String> result = new ArrayList<>();
        if (latest == null) return result;
        if (latest.tps() < 18.0) result.add("Low TPS: " + String.format(Locale.US, "%.2f", latest.tps()));
        double memoryPercent = latest.usedMemory() * 100.0 / Math.max(1, latest.maxMemory());
        if (memoryPercent >= 85.0) result.add("High heap usage: " + String.format(Locale.US, "%.1f%%", memoryPercent));
        if (previous != null) {
            if (latest.tps() < previous.tps() - 1.5) result.add("TPS dropped significantly since the previous sample.");
            if (latest.chunks() - previous.chunks() >= Math.max(100, previous.chunks() / 5))
                result.add("Loaded chunks increased significantly: +" + (latest.chunks() - previous.chunks()));
            if (latest.tasks() - previous.tasks() >= Math.max(10, previous.tasks() / 4))
                result.add("Scheduler tasks increased significantly: +" + (latest.tasks() - previous.tasks()));
            if (latest.usedMemory() - previous.usedMemory() > Math.max(128L * 1024 * 1024, latest.maxMemory() / 10))
                result.add("Memory usage increased significantly since the previous sample.");
        }
        return result;
    }

    private String age() {
        return Math.max(0, (System.currentTimeMillis() - latest.time()) / 1000) + "s";
    }

    private static String formatMb(long bytes) {
        return String.format(Locale.US, "%.0f MB", bytes / 1024.0 / 1024.0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("Owner.Core") || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("gui", "status", "refresh").stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
