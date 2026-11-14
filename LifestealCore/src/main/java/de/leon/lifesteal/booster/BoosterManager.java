package de.leon.lifesteal.booster;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;
import de.leon.lifesteal.data.PlayerData;
import de.leon.lifesteal.economy.Currency;
import de.leon.lifesteal.economy.EcoCommands;
import de.leon.lifesteal.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class BoosterManager implements Listener {

    private final LifestealCore plugin;
    private final Database db;
    private final Random random = new Random();
    private org.bukkit.scheduler.BukkitTask task;
    private final BossBar bossBar;
    private BoosterType activeServer;
    private long serverExpires;
    private long nextAuto;
    private long serverStarted;

    public BoosterManager(LifestealCore plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        this.bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    }

    private long intervalMillis() {
        return Math.max(1, plugin.getConfig().getLong("boosters.auto-interval-minutes", 120)) * 60_000L;
    }

    private int durationMinutes() {
        return Math.max(1, plugin.getConfig().getInt("boosters.duration-minutes", 15));
    }

    public void start() {
        long now = System.currentTimeMillis();
        try {
            activeServer = BoosterType.parse(db.getState("booster_active"));
            serverExpires = parseLong(db.getState("booster_expires"));
            serverStarted = parseLong(db.getState("booster_started"));
            nextAuto = parseLong(db.getState("booster_next"));
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load the server booster state: " + e.getMessage());
        }
        if (activeServer != null && serverExpires <= now) activeServer = null;
        if (nextAuto <= 0) nextAuto = now + intervalMillis();
        applyRandomTick();
        if (plugin.getConfig().getBoolean("boosters.bossbar", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bossBar);
        }
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        saveState();
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
        if (activeServer == BoosterType.S_RANDOM_TICK_X10) setRandomTick(normalTick());
    }

    private static long parseLong(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void saveState() {
        db.setState("booster_active", activeServer == null ? "" : activeServer.name());
        db.setState("booster_expires", String.valueOf(serverExpires));
        db.setState("booster_started", String.valueOf(serverStarted));
        db.setState("booster_next", String.valueOf(nextAuto));
    }

    public BoosterType activeServerBooster() {
        return activeServer != null && System.currentTimeMillis() < serverExpires ? activeServer : null;
    }

    public void activateServer(BoosterType type, int minutes) {
        long now = System.currentTimeMillis();
        activeServer = type;
        serverStarted = now;
        serverExpires = now + minutes * 60_000L;
        applyRandomTick();
        saveState();
        Msg.broadcast(Msg.Tone.GOOD, "Server booster {b} has been activated!", "b", Msg.sc(type.getDisplayName()));
        tickBossBar(now);
    }

    private void endServer(long now) {
        boolean wasTick = activeServer == BoosterType.S_RANDOM_TICK_X10;
        activeServer = null;
        serverExpires = 0;
        nextAuto = now + intervalMillis();
        if (wasTick) setRandomTick(normalTick());
        saveState();
        Msg.broadcast(Msg.Tone.NEUTRAL, "The server booster has ended");
    }

    public void skip() {
        if (activeServerBooster() != null) endServer(System.currentTimeMillis());
        triggerRandom();
    }

    private void triggerRandom() {
        List<BoosterType> server = new ArrayList<>();
        for (BoosterType t : BoosterType.values()) if (t.getCategory() == BoosterType.Category.SERVER) server.add(t);
        activateServer(server.get(random.nextInt(server.size())), durationMinutes());
    }

    private int normalTick() {
        return plugin.getConfig().getInt("boosters.random-tick-normal", 3);
    }

    private void applyRandomTick() {
        setRandomTick(activeServerBooster() == BoosterType.S_RANDOM_TICK_X10
                ? plugin.getConfig().getInt("boosters.random-tick-boosted", 30) : normalTick());
    }

    @SuppressWarnings("unchecked")
    private void setRandomTick(int value) {
        GameRule<?> rule = GameRule.getByName("randomTickSpeed");
        if (rule == null) rule = GameRule.getByName("random_tick_speed");
        if (rule == null || rule.getType() != Integer.class) return;
        for (World w : Bukkit.getWorlds()) w.setGameRule((GameRule<Integer>) rule, value);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (activeServer != null && now >= serverExpires) endServer(now);
        if (activeServer == null && now >= nextAuto) triggerRandom();

        BoosterType server = activeServerBooster();
        if (server != null) {
            long left = (serverExpires - now + 999) / 1000;
            if (left <= 5 && left > 0) {
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            }
        }
        tickBossBar(now);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data().cached(player.getUniqueId());
            if (data != null) {
                for (BoosterType type : data.passives()) {
                    PotionEffectType effect = effectOf(type);
                    if (effect != null) ensure(player, effect, Math.max(0, type.getAmplifier() - 1));
                }
            }
            if (server == BoosterType.HASTE_X3) ensure(player, PotionEffectType.HASTE, 2);
        }
    }

    private void tickBossBar(long now) {
        if (!plugin.getConfig().getBoolean("boosters.bossbar", true)) return;
        BoosterType server = activeServerBooster();
        if (server != null) {
            long total = Math.max(1, serverExpires - serverStarted);
            long left = Math.max(0, serverExpires - now);
            bossBar.name(Msg.good("Server booster active: {b} ({t})",
                    "b", Msg.sc(server.getDisplayName()), "t", Msg.time(left)));
            bossBar.color(BossBar.Color.GREEN);
            bossBar.progress(clamp(left / (float) total));
        } else {
            long left = Math.max(0, nextAuto - now);
            bossBar.name(Msg.neutral("Next server booster in: {t}", "t", Msg.time(left)));
            bossBar.color(BossBar.Color.PURPLE);
            bossBar.progress(clamp(left / (float) intervalMillis()));
        }
    }

    private static float clamp(float f) {
        return Math.max(0f, Math.min(1f, f));
    }

    private static PotionEffectType effectOf(BoosterType type) {
        return switch (type) {
            case P_STRENGTH_X1, P_STRENGTH_X2 -> PotionEffectType.STRENGTH;
            case P_SPEED_X1, P_SPEED_X2 -> PotionEffectType.SPEED;
            case P_FIRE_RES -> PotionEffectType.FIRE_RESISTANCE;
            default -> null;
        };
    }

    private static void ensure(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier) return;
        if (current == null || current.getAmplifier() != amplifier || (current.getDuration() >= 0 && current.getDuration() < 60)) {
            player.addPotionEffect(new PotionEffect(type, 140, amplifier, false, false));
        }
    }

    public void togglePassive(Player player, BoosterType type) {
        PlayerData data = plugin.data().get(player);
        if (data.hasPassive(type)) {
            data.setPassive(type, false);
            PotionEffectType effect = effectOf(type);
            if (effect != null) player.removePotionEffect(effect);
            Msg.err(player, "Passive booster {b} disabled", "b", Msg.sc(type.getDisplayName()));
            return;
        }
        if (data.boosterCount(type) <= 0) {
            Msg.err(player, "You do not own this booster");
            return;
        }
        int max = plugin.getConfig().getInt("boosters.max-passive", 2);
        if (data.passives().size() >= max) {
            Msg.err(player, "You can only have {n} passive boosters active at the same time", "n", max);
            return;
        }
        data.setPassive(type, true);
        Msg.ok(player, "Passive booster {b} activated", "b", Msg.sc(type.getDisplayName()));
    }

    public void useNormal(Player player, BoosterType type) {
        if (activeServerBooster() != null) {
            Msg.err(player, "A server booster is already active");
            return;
        }
        if (!plugin.data().get(player).takeBooster(type, 1)) {
            Msg.err(player, "You do not own this booster");
            return;
        }
        activateServer(type, durationMinutes());
    }

    public double multiplier(Player player, Currency currency) {
        double m = 1.0;
        BoosterType server = activeServerBooster();
        if (server != null) m *= currency == Currency.MONEY ? server.getMoneyMultiplier() : server.getGemsMultiplier();
        PlayerData data = plugin.data().cached(player.getUniqueId());
        if (data != null) {
            for (BoosterType t : data.passives()) {
                m *= currency == Currency.MONEY ? t.getMoneyMultiplier() : t.getGemsMultiplier();
            }
        }
        return m;
    }

    public List<String> activeNames(Player player) {
        Set<String> result = new LinkedHashSet<>();
        PlayerData data = plugin.data().cached(player.getUniqueId());
        if (data != null) for (BoosterType t : data.passives()) result.add(Msg.sc(t.getDisplayName()));
        BoosterType server = activeServerBooster();
        if (server != null) result.add(Msg.sc(server.getDisplayName()));
        return new ArrayList<>(result);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("boosters.bossbar", true)) event.getPlayer().showBossBar(bossBar);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0 && activeServerBooster() == BoosterType.S_XP_X3) {
            event.setAmount(event.getAmount() * 3);
        }
    }

    public TabExecutor boosterCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
                    if (!sender.hasPermission("Admin.Core")) {
                        Msg.err(sender, "You do not have permission to do this");
                        return true;
                    }
                    if (args.length < 3) {
                        Msg.info(sender, "Usage: /booster give <player> <booster> [amount]");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        Msg.err(sender, "This player is not online");
                        return true;
                    }
                    BoosterType type = BoosterType.parse(args[2]);
                    if (type == null) {
                        Msg.err(sender, "Invalid booster type");
                        return true;
                    }
                    int amount = 1;
                    if (args.length >= 4) {
                        try {
                            amount = Math.max(1, Math.min(1000, Integer.parseInt(args[3])));
                        } catch (NumberFormatException e) {
                            Msg.err(sender, "Invalid amount");
                            return true;
                        }
                    }
                    plugin.data().get(target).addBooster(type, amount);
                    Msg.ok(sender, "Gave {n}x {b} to {p}", "n", amount, "b", Msg.sc(type.getDisplayName()), "p", target.getName());
                    Msg.ok(target, "You received a booster: {n}x {b}", "n", amount, "b", Msg.sc(type.getDisplayName()));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    Msg.err(sender, "Only players can use this command");
                    return true;
                }
                new BoosterMenu(BoosterManager.this, player).open(player);
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                boolean admin = sender.hasPermission("Admin.Core");
                if (args.length == 1) return EcoCommands.filter(admin ? List.of("gui", "give") : List.of("gui"), args[0]);
                if (!admin || !args[0].equalsIgnoreCase("give")) return List.of();
                if (args.length == 2) return EcoCommands.onlineNames(sender, args[1]);
                if (args.length == 3) return EcoCommands.filter(Arrays.stream(BoosterType.values()).map(Enum::name).toList(), args[2]);
                if (args.length == 4) return EcoCommands.filter(List.of("1", "5", "10"), args[3]);
                return List.of();
            }
        };
    }

    public TabExecutor serverBoosterCommand() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission("Admin.Core")) {
                    Msg.err(sender, "You do not have permission to do this");
                    return true;
                }
                if (args.length > 0 && args[0].equalsIgnoreCase("skip")) {
                    skip();
                    Msg.ok(sender, "Skipped the timer and activated a server booster");
                    return true;
                }
                Msg.info(sender, "Usage: /sbooster skip");
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return args.length == 1 && sender.hasPermission("Admin.Core") ? EcoCommands.filter(List.of("skip"), args[0]) : List.of();
            }
        };
    }

    LifestealCore plugin() {
        return plugin;
    }
}
