package de.leon.lifesteal.settings;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SettingsManager implements Listener {
    private static final Map<String, Boolean> DEFAULTS = Map.ofEntries(
            Map.entry("mob-spawning", false),
            Map.entry("hide-money", false),
            Map.entry("hide-gems", false),
            Map.entry("pay-messages", true),
            Map.entry("payments", true),
            Map.entry("tpa", true),
            Map.entry("bounty-messages", true),
            Map.entry("public-chat", true),
            Map.entry("msg", true),
            Map.entry("hide-booster-scoreboard", false),
            Map.entry("nightvision", false),
            Map.entry("prefixes-tab", true),
            Map.entry("hide-ping", false)
    );

    private final LifestealCore plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private boolean storageHealthy = true;
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public SettingsManager(LifestealCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
        this.yaml = loadYamlWithBackup();
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String rawId : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    ConfigurationSection section = players.getConfigurationSection(rawId);
                    if (section == null) continue;
                    Map<String, Boolean> values = new ConcurrentHashMap<>();
                    for (String key : section.getKeys(false)) values.put(key, section.getBoolean(key));
                    cache.put(id, values);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid player UUID in settings.yml: " + rawId);
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) applyNightVision(player);
    }

    private YamlConfiguration loadYamlWithBackup() {
        YamlConfiguration loaded = new YamlConfiguration();
        if (!file.isFile()) return loaded;
        try {
            loaded.load(file);
            return loaded;
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = new File(file.getParentFile(), "settings.yml.bak");
            if (backup.isFile()) {
                try {
                    YamlConfiguration recovered = new YamlConfiguration();
                    recovered.load(backup);
                    Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().warning("settings.yml was damaged; recovered player settings from settings.yml.bak.");
                    return recovered;
                } catch (IOException | InvalidConfigurationException backupError) {
                    storageHealthy = false;
                    plugin.getLogger().log(Level.SEVERE, "settings.yml and its backup are invalid; preserving both files.", backupError);
                    return loaded;
                }
            }
            storageHealthy = false;
            plugin.getLogger().log(Level.SEVERE, "settings.yml is invalid and no backup exists; preserving the original file.", ex);
            return loaded;
        }
    }

    public boolean isEnabled(Player player, String key) {
        return player != null && isEnabled(player.getUniqueId(), key);
    }

    public boolean isEnabled(UUID uuid, String key) {
        Map<String, Boolean> values = cache.get(uuid);
        if (values != null && values.containsKey(key)) return values.get(key);
        return DEFAULTS.getOrDefault(key, false);
    }

    public void setEnabled(Player player, String key, boolean enabled) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        cache.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>()).put(key, enabled);
        yaml.set("players." + id + "." + key, enabled);
        save();
        if (key.equals("nightvision")) applyNightVision(player);
    }

    public boolean toggle(Player player, String key) {
        boolean enabled = !isEnabled(player, key);
        setEnabled(player, key, enabled);
        return enabled;
    }

    public Set<String> keys() {
        return DEFAULTS.keySet();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyNightVision(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> applyNightVision(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep cached settings for offline lookups such as /bal <player>.
    }

    private void applyNightVision(Player player) {
        if (isEnabled(player, "nightvision")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
        } else if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        double radius = Math.max(1, plugin.getConfig().getDouble("settings.mob-spawning-radius", 32.0));
        double radiusSquared = radius * radius;
        boolean optedOutPlayerNearby = false;
        boolean optedInPlayerNearby = false;
        for (Player player : event.getLocation().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(event.getLocation()) > radiusSquared) continue;
            if (isEnabled(player, "mob-spawning")) optedOutPlayerNearby = true;
            else optedInPlayerNearby = true;
        }
        // A nearby player who allows mob spawning takes priority over players who disabled it.
        if (optedOutPlayerNearby && !optedInPlayerNearby) event.setCancelled(true);
    }

    private void save() {
        if (!storageHealthy) {
            plugin.getLogger().severe("Player settings were not saved because the existing settings file could not be safely recovered.");
            return;
        }
        File temporary = new File(file.getParentFile(), "settings.yml.tmp");
        File backup = new File(file.getParentFile(), "settings.yml.bak");
        try {
            yaml.save(temporary);
            YamlConfiguration verify = new YamlConfiguration();
            verify.load(temporary);
            if (file.isFile() && file.length() > 0) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not safely save player settings", e);
            if (temporary.exists()) temporary.delete();
        }
    }
}
