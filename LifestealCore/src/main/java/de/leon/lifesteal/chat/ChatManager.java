package de.leon.lifesteal.chat;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.settings.SettingsManager;
import de.leon.lifesteal.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ChatManager implements Listener {
    private static final Pattern LINKS = Pattern.compile("(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|net|org|de|gg|io|me|tv|xyz|info|co|uk)(/|\\b))");
    private static final long FAST_WINDOW_MS = 3_000L;
    private static final long SPAM_LOCK_MS = 5_000L;
    private static final int FAST_MESSAGE_LIMIT = 5;

    private final LifestealCore plugin;
    private final SettingsManager settings;
    private final Map<UUID, UUID> replies = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> recentMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spamLockedUntil = new ConcurrentHashMap<>();

    public ChatManager(LifestealCore plugin, SettingsManager settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (containsLink(plain)) {
            event.setCancelled(true);
            notifyAsync(sender, "Links are not allowed in public chat");
            return;
        }
        if (isMostlyCaps(plain)) {
            event.setCancelled(true);
            notifyAsync(sender, "Please do not write messages in all caps");
            return;
        }
        if (isSpamLocked(sender, System.currentTimeMillis())) {
            event.setCancelled(true);
            notifyAsync(sender, "You sent 5 fast messages. Please wait 5 seconds before chatting again");
            return;
        }

        // Public Chat is a per-viewer filter. Server messages and private messages are unaffected.
        event.viewers().removeIf(viewer -> viewer instanceof Player recipient
                && !settings.isEnabled(recipient, "public-chat"));

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component prefix = luckPermsPrefix(source);
            return Component.text("» ", NamedTextColor.DARK_GRAY)
                    .append(prefix)
                    .append(Component.text(source.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                    .append(message);
        });
    }

    private boolean containsLink(String message) {
        return LINKS.matcher(message).find();
    }

    private boolean isMostlyCaps(String message) {
        if (message.length() < 8) return false;
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;
            if (Character.isUpperCase(c)) upper++;
        }
        return letters >= 6 && upper >= 6 && upper / (double) letters >= 0.70;
    }

    private boolean isSpamLocked(Player player, long now) {
        UUID id = player.getUniqueId();
        long lockedUntil = spamLockedUntil.getOrDefault(id, 0L);
        if (now < lockedUntil) return true;
        if (lockedUntil != 0) spamLockedUntil.remove(id, lockedUntil);

        Deque<Long> timestamps = recentMessages.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > FAST_WINDOW_MS) {
                timestamps.removeFirst();
            }
            timestamps.addLast(now);
            if (timestamps.size() >= FAST_MESSAGE_LIMIT) {
                timestamps.clear();
                spamLockedUntil.put(id, now + SPAM_LOCK_MS);
            }
        }
        return false;
    }

    private void notifyAsync(Player player, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) Msg.err(player, message);
        });
    }

    private Component luckPermsPrefix(Player player) {
        try {
            var registration = Bukkit.getServicesManager().getRegistration(net.luckperms.api.LuckPerms.class);
            if (registration == null) return Component.empty();
            String prefix = registration.getProvider().getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
            if (prefix == null || prefix.isBlank()) return Component.empty();
            return Msg.legacy(prefix.replace('§', '&')).append(Component.space());
        } catch (RuntimeException ignored) {
            return Component.empty();
        }
    }

    public TabExecutor messageCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use private messages");
                    return true;
                }
                if (args.length < 2) {
                    Msg.info(player, "Usage: /msg <player> <message>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !player.canSee(target)) {
                    Msg.err(player, "This player is not online");
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                sendPrivate(player, target, message);
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 ? onlineNames(sender, args[0]) : List.of();
            }
        };
    }

    public TabExecutor replyCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use private messages");
                    return true;
                }
                if (args.length == 0) {
                    Msg.info(player, "Usage: /r <message>");
                    return true;
                }
                UUID targetId = replies.get(player.getUniqueId());
                Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
                if (target == null) {
                    Msg.err(player, "There is nobody to reply to");
                    return true;
                }
                sendPrivate(player, target, String.join(" ", args));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return List.of();
            }
        };
    }

    private void sendPrivate(Player sender, Player target, String message) {
        if (!settings.isEnabled(target, "msg")) {
            Msg.err(sender, "This player is not accepting private messages");
            return;
        }
        replies.put(sender.getUniqueId(), target.getUniqueId());
        replies.put(target.getUniqueId(), sender.getUniqueId());
        Component content = Component.text(message, NamedTextColor.WHITE);
        Msg.raw(sender, Component.text("To ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.GRAY)).append(content));
        Msg.raw(target, Component.text("From ", NamedTextColor.GRAY)
                .append(Component.text(sender.getName(), NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.GRAY)).append(content));
    }

    public TabExecutor pingCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player viewer)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (args.length > 1) {
                    Msg.info(viewer, "Usage: /ping [player]");
                    return true;
                }
                Player target = args.length == 0 ? viewer : Bukkit.getPlayerExact(args[0]);
                if (target == null || !viewer.canSee(target)) {
                    Msg.err(viewer, "This player is not online");
                    return true;
                }
                if (target != viewer && settings.isEnabled(target, "hide-ping")
                        && !viewer.hasPermission("Admin.Core") && !viewer.hasPermission("Owner.Core")) {
                    Msg.info(viewer, "{p}'s ping is hidden", "p", target.getName());
                    return true;
                }
                Msg.info(viewer, "{p}'s ping: {ping} ms", "p", target.getName(), "ping", target.getPing());
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 ? onlineNames(sender, args[0]) : List.of();
            }
        };
    }

    public TabExecutor chatCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission("Admin.Core") && !sender.hasPermission("Owner.Core")) {
                    Msg.err(sender, "You do not have permission to do this");
                    return true;
                }
                if (args.length != 1 || !args[0].equalsIgnoreCase("clear")) {
                    Msg.info(sender, "Usage: /chat clear");
                    return true;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (int i = 0; i < 100; i++) player.sendMessage(Component.empty());
                    Msg.info(player, "The public chat was cleared by {p}", "p", sender.getName());
                }
                Bukkit.getConsoleSender().sendMessage("[LifestealCore] Public chat cleared by " + sender.getName());
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 ? List.of("clear").stream()
                        .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
            }
        };
    }

    private List<String> onlineNames(CommandSender sender, String prefix) {
        String start = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(start))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        replies.remove(id);
        replies.values().removeIf(id::equals);
        recentMessages.remove(id);
        spamLockedUntil.remove(id);
    }
}
