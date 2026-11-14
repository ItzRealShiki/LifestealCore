package de.leon.lifesteal.util;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adds visible online player names to common private-message command completions.
 * A snapshot is refreshed synchronously so the async Paper completion event never
 * iterates Bukkit's live player collections off-thread.
 */
public final class PlayerNameTabCompleter implements Listener {

    private static final Set<String> TARGET_FIRST_COMMANDS = Set.of(
            "msg", "message", "tell", "w", "whisper", "m", "t", "pm", "dm",
            "emsg", "etell"
    );

    private final JavaPlugin plugin;
    private volatile List<String> allNames = List.of();
    private volatile Map<UUID, List<String>> visibleNames = Map.of();
    private final BukkitTask refreshTask;

    public PlayerNameTabCompleter(JavaPlugin plugin) {
        this.plugin = plugin;
        refreshSnapshot();
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSnapshot, 20L, 20L);
    }

    private void refreshSnapshot() {
        List<String> names = new ArrayList<>();
        Map<UUID, List<String>> perViewer = new HashMap<>();
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player target : online) names.add(target.getName());

        for (Player viewer : online) {
            List<String> visible = new ArrayList<>();
            for (Player target : online) {
                if (viewer.canSee(target)) visible.add(target.getName());
            }
            perViewer.put(viewer.getUniqueId(), List.copyOf(visible));
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);
        allNames = List.copyOf(names);
        visibleNames = Map.copyOf(perViewer);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!event.isCommand()) return;
        CompletionRequest request = parse(event.getBuffer());
        if (request == null) return;

        List<String> names = namesFor(event.getSender());
        List<String> matches = filter(names, request.prefix());
        if (matches.isEmpty()) return;

        // Preserve completions supplied by EssentialsX or another command plugin.
        LinkedHashSet<String> combined = new LinkedHashSet<>(event.getCompletions());
        combined.addAll(matches);
        event.setCompletions(new ArrayList<>(combined));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSyncTabComplete(TabCompleteEvent event) {
        CompletionRequest request = parse(event.getBuffer());
        if (request == null) return;

        List<String> matches = filter(namesFor(event.getSender()), request.prefix());
        if (matches.isEmpty()) return;

        LinkedHashSet<String> combined = new LinkedHashSet<>(event.getCompletions());
        combined.addAll(matches);
        event.setCompletions(new ArrayList<>(combined));
    }

    private List<String> namesFor(CommandSender sender) {
        if (sender instanceof Player player) {
            return visibleNames.getOrDefault(player.getUniqueId(), List.of());
        }
        return allNames;
    }

    private static List<String> filter(List<String> names, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static CompletionRequest parse(String buffer) {
        if (buffer == null || buffer.length() < 2 || buffer.charAt(0) != '/') return null;

        String commandLine = buffer.substring(1);
        int firstSpace = commandLine.indexOf(' ');
        if (firstSpace < 0) return null; // Still completing the command name.

        String label = commandLine.substring(0, firstSpace);
        int namespace = label.lastIndexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        if (!TARGET_FIRST_COMMANDS.contains(label.toLowerCase(Locale.ROOT))) return null;

        String arguments = commandLine.substring(firstSpace + 1);
        // Only the first argument is the recipient. Once a recipient is followed
        // by a space, subsequent words are message text and must not be completed.
        if (arguments.indexOf(' ') >= 0) return null;
        return new CompletionRequest(arguments);
    }

    public void shutdown() {
        refreshTask.cancel();
        allNames = List.of();
        visibleNames = Map.of();
    }

    private record CompletionRequest(String prefix) {
    }
}
