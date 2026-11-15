package de.leon.lifesteal.teleport;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.gui.Menu;
import de.leon.lifesteal.gui.MenuListener;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TeleportManager implements Listener {
    private enum RequestType { TO_PLAYER, HERE }
    private record Request(UUID requester, UUID target, RequestType type, long expiresAt) {}

    private static final long REQUEST_LIFETIME_MS = 60_000L;
    private static final long REQUEST_COOLDOWN_MS = 3_000L;
    private static final long RTP_COOLDOWN_MS = 60_000L;

    private final LifestealCore plugin;
    private final Map<UUID, LinkedHashMap<UUID, Request>> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> outgoing = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rtpCooldowns = new ConcurrentHashMap<>();

    public TeleportManager(LifestealCore plugin) {
        this.plugin = plugin;
    }

    public TabExecutor randomTeleportCommand() {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                new DimensionMenu().open(player);
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return List.of();
            }
        };
    }

    public TabExecutor tpaCommand() {
        return requestCommand(RequestType.TO_PLAYER);
    }

    public TabExecutor tpahereCommand() {
        return requestCommand(RequestType.HERE);
    }

    private TabExecutor requestCommand(RequestType type) {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (args.length == 0) {
                    new PlayerSelectMenu(player, type).open(player);
                    return true;
                }
                if (args.length != 1) {
                    Msg.info(player, type == RequestType.HERE ? "Usage: /tpahere [player]" : "Usage: /tpa [player]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Msg.err(player, "Player {p} is not online", "p", args[0]);
                    return true;
                }
                sendRequest(player, target, type);
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 ? onlineNames(sender, args[0]) : List.of();
            }
        };
    }

    public TabExecutor acceptCommand() {
        return answerCommand(true);
    }

    public TabExecutor denyCommand() {
        return answerCommand(false);
    }

    private TabExecutor answerCommand(boolean accept) {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                if (args.length == 0) {
                    new IncomingMenu(player).open(player);
                    return true;
                }
                if (args.length != 1) {
                    Msg.info(player, accept ? "Usage: /tpaccept [player]" : "Usage: /tpdeny [player]");
                    return true;
                }
                Player requester = Bukkit.getPlayerExact(args[0]);
                if (requester == null) {
                    Msg.err(player, "This player is not online");
                    return true;
                }
                Request request = findRequest(player.getUniqueId(), requester.getUniqueId());
                if (request == null) {
                    Msg.err(player, "You do not have a pending request from this player");
                    return true;
                }
                if (accept) acceptRequest(player, request);
                else denyRequest(player, request);
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                if (!(sender instanceof Player player) || args.length != 1) return List.of();
                return pendingRequests(player.getUniqueId()).stream()
                        .map(r -> Bukkit.getPlayer(r.requester()))
                        .filter(p -> p != null && p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
        };
    }

    public TabExecutor cancelCommand() {
        return new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                UUID targetId = outgoing.get(player.getUniqueId());
                if (targetId == null) {
                    Msg.err(player, "You do not have an outgoing teleport request");
                    return true;
                }
                new OutgoingMenu(player, targetId).open(player);
                return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return List.of();
            }
        };
    }

    private void sendRequest(Player requester, Player target, RequestType type) {
        for (UUID targetId : new ArrayList<>(incoming.keySet())) pendingRequests(targetId);
        if (requester.equals(target)) {
            Msg.err(requester, "You cannot send a teleport request to yourself");
            return;
        }
        long now = System.currentTimeMillis();
        long next = requestCooldowns.getOrDefault(requester.getUniqueId(), 0L);
        if (now < next) {
            Msg.err(requester, "Please wait {s}s before sending another request",
                    "s", String.format(Locale.ROOT, "%.1f", (next - now) / 1000.0));
            return;
        }
        if (outgoing.containsKey(requester.getUniqueId())) {
            Msg.err(requester, "You already have an outgoing request. Use /tpcancel first");
            return;
        }
        Request request = new Request(requester.getUniqueId(), target.getUniqueId(), type, now + REQUEST_LIFETIME_MS);
        incoming.computeIfAbsent(target.getUniqueId(), ignored -> new LinkedHashMap<>()).put(requester.getUniqueId(), request);
        outgoing.put(requester.getUniqueId(), target.getUniqueId());
        requestCooldowns.put(requester.getUniqueId(), now + REQUEST_COOLDOWN_MS);
        Msg.ok(requester, type == RequestType.HERE
                ? "Teleport-here request sent to {p}" : "Teleport request sent to {p}", "p", target.getName());
        Msg.info(target, "{p} {a} you. Open /tpaccept to respond",
                "p", requester.getName(),
                "a", type == RequestType.HERE ? "wants you to teleport to them" : "wants to teleport to you");
        plugin.sound(requester, "success");
        plugin.sound(target, "alert");
    }

    private List<Request> pendingRequests(UUID target) {
        LinkedHashMap<UUID, Request> requests = incoming.get(target);
        if (requests == null) return List.of();
        long now = System.currentTimeMillis();
        requests.values().removeIf(request -> {
            if (request.expiresAt() > now) return false;
            outgoing.remove(request.requester(), target);
            return true;
        });
        if (requests.isEmpty()) {
            incoming.remove(target);
            return List.of();
        }
        return requests.values().stream().sorted(Comparator.comparingLong(Request::expiresAt)).toList();
    }

    private Request findRequest(UUID target, UUID requester) {
        return pendingRequests(target).stream().filter(r -> r.requester().equals(requester)).findFirst().orElse(null);
    }

    private void removeRequest(Request request) {
        LinkedHashMap<UUID, Request> requests = incoming.get(request.target());
        if (requests != null) {
            requests.remove(request.requester());
            if (requests.isEmpty()) incoming.remove(request.target());
        }
        outgoing.remove(request.requester(), request.target());
    }

    private void acceptRequest(Player target, Request request) {
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            removeRequest(request);
            Msg.err(target, "The requester is no longer online");
            return;
        }
        removeRequest(request);
        Player moving = request.type() == RequestType.TO_PLAYER ? requester : target;
        Player destination = request.type() == RequestType.TO_PLAYER ? target : requester;
        Msg.info(target, "Teleport request accepted");
        Msg.info(requester, "{p} accepted your teleport request", "p", target.getName());
        moving.teleportAsync(destination.getLocation()).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) {
                Msg.ok(moving, "Teleport completed");
                plugin.sound(moving, "success");
            } else {
                Msg.err(moving, "Teleport failed");
                Msg.err(destination, "Teleport failed");
            }
        }));
    }

    private void denyRequest(Player target, Request request) {
        Player requester = Bukkit.getPlayer(request.requester());
        removeRequest(request);
        Msg.info(target, "Teleport request denied");
        if (requester != null) Msg.err(requester, "{p} denied your teleport request", "p", target.getName());
        plugin.sound(target, "error");
    }

    private List<String> onlineNames(CommandSender sender, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(target -> !(sender instanceof Player viewer) || viewer.canSee(target))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void randomTeleport(Player player, World.Environment environment) {
        long now = System.currentTimeMillis();
        long next = rtpCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < next) {
            Msg.err(player, "Please wait {s}s before using random teleport again",
                    "s", String.format(Locale.ROOT, "%.0f", (next - now) / 1000.0));
            return;
        }
        World world = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == environment).findFirst().orElse(null);
        if (world == null) {
            Msg.err(player, "That dimension is not available on this server");
            return;
        }
        rtpCooldowns.put(player.getUniqueId(), now + RTP_COOLDOWN_MS);
        int radius = Math.max(256, plugin.getConfig().getInt("random-teleport.radius", 5000));
        Location borderCenter = world.getWorldBorder().getCenter();
        double borderRadius = Math.max(0, world.getWorldBorder().getSize() / 2.0 - 16);
        double usableRadius = Math.min(radius, borderRadius);
        if (usableRadius < 32) {
            Msg.err(player, "The world border is too small for random teleport");
            rtpCooldowns.remove(player.getUniqueId());
            return;
        }
        Msg.info(player, "Finding a safe location in {w}...", "w", worldName(environment));
        attemptRandomLocation(player, world, environment, borderCenter.getX(), borderCenter.getZ(), usableRadius, 0);
    }

    private void attemptRandomLocation(Player player, World world, World.Environment environment,
                                        double centerX, double centerZ, double radius, int attempt) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                rtpCooldowns.remove(player.getUniqueId());
                return;
            }
            if (attempt >= 60) {
                rtpCooldowns.remove(player.getUniqueId());
                Msg.err(player, "A safe random location could not be found. Please try again");
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int x = (int) Math.floor(centerX + random.nextDouble(-radius, radius));
            int z = (int) Math.floor(centerZ + random.nextDouble(-radius, radius));
            if (Math.hypot(x - centerX, z - centerZ) > radius) {
                attemptRandomLocation(player, world, environment, centerX, centerZ, radius, attempt + 1);
                return;
            }
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) {
                attemptRandomLocation(player, world, environment, centerX, centerZ, radius, attempt + 1);
                return;
            }
            Block floor = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            Material ground = floor.getType();
            boolean safe = ground.isSolid() && feet.isPassable() && head.isPassable()
                    && ground != Material.MAGMA_BLOCK && ground != Material.CACTUS
                    && ground != Material.CAMPFIRE && ground != Material.SOUL_CAMPFIRE
                    && ground != Material.POWDER_SNOW && ground != Material.SWEET_BERRY_BUSH
                    && ground != Material.FIRE && ground != Material.SOUL_FIRE && ground != Material.LAVA
                    && !feet.isLiquid() && !head.isLiquid();
            if (!safe) {
                attemptRandomLocation(player, world, environment, centerX, centerZ, radius, attempt + 1);
                return;
            }
            Location destination = new Location(world, x + 0.5, y + 1, z + 0.5, random.nextFloat() * 360f, 0f);
            player.teleportAsync(destination).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    Msg.ok(player, "You have been randomly teleported to {w}", "w", worldName(environment));
                    plugin.sound(player, "success");
                } else {
                    rtpCooldowns.remove(player.getUniqueId());
                    Msg.err(player, "Random teleport failed");
                }
            }));
        }, 1L);
    }

    private String worldName(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "World";
        };
    }

    private static Component gradient(String text, int from, int to) {
        Component result = Component.empty();
        int count = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            double t = (double) i / count;
            int r = (int) (((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
            int g = (int) (((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
            int b = (int) ((from & 255) * (1 - t) + (to & 255) * t);
            result = result.append(Component.text(String.valueOf(text.charAt(i)), TextColor.color(r, g, b)));
        }
        return result;
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private final class DimensionMenu extends Menu {
        DimensionMenu() {
            create(27, gradient("Random Teleport", 0x7CFC00, 0x006400));
            set(11, icon(Material.GRASS_BLOCK, gradient("Overworld", 0x7CFC00, 0x006400),
                    List.of(Component.text(Msg.sc("Click to teleport"), TextColor.color(0xAAAAAA)))));
            set(13, icon(Material.NETHERRACK, gradient("Nether", 0xFF8C00, 0xFF0000),
                    List.of(Component.text(Msg.sc("Click to teleport"), TextColor.color(0xAAAAAA)))));
            set(15, icon(Material.END_STONE, gradient("End", 0xFFFF00, 0xFFFFFF),
                    List.of(Component.text(Msg.sc("Click to teleport"), TextColor.color(0xAAAAAA)))));
        }
        @Override public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            switch (event.getRawSlot()) {
                case 11 -> randomTeleport(player, World.Environment.NORMAL);
                case 13 -> randomTeleport(player, World.Environment.NETHER);
                case 15 -> randomTeleport(player, World.Environment.THE_END);
                default -> { return; }
            }
            MenuListener.close(player);
        }
    }

    private final class PlayerSelectMenu extends Menu {
        private final Player viewer;
        private final RequestType type;
        private final List<UUID> targets;
        PlayerSelectMenu(Player viewer, RequestType type) {
            this.viewer = viewer;
            this.type = type;
            this.targets = Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(viewer) && viewer.canSee(p))
                    .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(Player::getUniqueId).toList();
            create(54, Component.text(Msg.sc(type == RequestType.HERE ? "Teleport Here - Select Player" : "Teleport To - Select Player")));
            for (int i = 0; i < Math.min(45, targets.size()); i++) {
                Player target = Bukkit.getPlayer(targets.get(i));
                if (target != null) set(i, icon(Material.PLAYER_HEAD, Component.text(Msg.sc(target.getName())),
                        List.of(Component.text(Msg.sc("Click to send request"), TextColor.color(0x55FF55)))));
            }
            set(49, icon(Material.BARRIER, Component.text(Msg.sc("Close")), List.of()));
        }
        @Override public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            int slot = event.getRawSlot();
            if (slot == 49) { MenuListener.close(player); return; }
            if (slot < 0 || slot >= 45 || slot >= targets.size()) return;
            Player target = Bukkit.getPlayer(targets.get(slot));
            if (target == null) { Msg.err(player, "That player is no longer online"); return; }
            sendRequest(player, target, type);
            MenuListener.close(player);
        }
    }

    private final class IncomingMenu extends Menu {
        private final List<Request> requests;
        IncomingMenu(Player viewer) {
            this.requests = new ArrayList<>(pendingRequests(viewer.getUniqueId()));
            create(54, Component.text(Msg.sc("Teleport Requests")));
            for (int i = 0; i < Math.min(45, requests.size()); i++) {
                Request request = requests.get(i);
                Player requester = Bukkit.getPlayer(request.requester());
                if (requester == null) continue;
                String action = request.type() == RequestType.HERE ? "wants you to teleport to them" : "wants to teleport to you";
                set(i, icon(Material.PLAYER_HEAD, Component.text(Msg.sc(requester.getName())),
                        List.of(Component.text(Msg.sc(action), TextColor.color(0xAAAAAA)),
                                Component.text(Msg.sc("Left click: accept"), TextColor.color(0x55FF55)),
                                Component.text(Msg.sc("Right click: deny"), TextColor.color(0xFF5555)))));
            }
            set(49, icon(Material.BARRIER, Component.text(Msg.sc("Close")), List.of()));
        }
        @Override public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            int slot = event.getRawSlot();
            if (slot == 49) { MenuListener.close(player); return; }
            if (slot < 0 || slot >= 45 || slot >= requests.size()) return;
            Request request = requests.get(slot);
            if (findRequest(player.getUniqueId(), request.requester()) == null) {
                Msg.err(player, "That request has expired");
                new IncomingMenu(player).open(player);
                return;
            }
            if (event.isRightClick()) denyRequest(player, request);
            else acceptRequest(player, request);
            new IncomingMenu(player).open(player);
        }
    }

    private final class OutgoingMenu extends Menu {
        private final UUID targetId;
        OutgoingMenu(Player viewer, UUID targetId) {
            this.targetId = targetId;
            create(27, Component.text(Msg.sc("Outgoing Request")));
            Player target = Bukkit.getPlayer(targetId);
            set(13, icon(Material.PAPER, Component.text(Msg.sc(target == null ? "Pending request" : "To " + target.getName())),
                    List.of(Component.text(Msg.sc("Click to cancel this request"), TextColor.color(0xFF5555)))));
            set(15, icon(Material.BARRIER, Component.text(Msg.sc("Close")), List.of()));
        }
        @Override public void onClick(Player player, InventoryClickEvent event) {
            if (!isTop(event)) return;
            if (event.getRawSlot() == 13) {
                LinkedHashMap<UUID, Request> requests = incoming.get(targetId);
                if (requests != null) {
                    Request request = requests.get(player.getUniqueId());
                    if (request != null) removeRequest(request);
                }
                Msg.info(player, "Your teleport request was cancelled");
                MenuListener.close(player);
            } else if (event.getRawSlot() == 15) MenuListener.close(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        UUID targetId = outgoing.remove(id);
        if (targetId != null) {
            LinkedHashMap<UUID, Request> requests = incoming.get(targetId);
            if (requests != null) {
                requests.remove(id);
                if (requests.isEmpty()) incoming.remove(targetId);
            }
        }
        LinkedHashMap<UUID, Request> own = incoming.remove(id);
        if (own != null) for (Request request : own.values()) outgoing.remove(request.requester(), id);
        requestCooldowns.remove(id);
        rtpCooldowns.remove(id);
    }
}
