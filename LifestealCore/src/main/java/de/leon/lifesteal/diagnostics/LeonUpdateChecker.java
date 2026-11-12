package de.leon.lifesteal.diagnostics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LeonUpdateChecker {
    public enum Priority { CRITICAL, IMPORTANT, OPTIONAL }
    public record Update(String plugin, String installed, String latest, Priority priority,
                         String changelog, String url, boolean updateAvailable) { }

    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern BODY = Pattern.compile("\\\"body\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern HTML = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, String> repositories = new LinkedHashMap<>();
    private final Map<String, Update> results = new ConcurrentHashMap<>();
    private volatile String lastCheck = "not checked";

    public LeonUpdateChecker(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;

        repositories.put("ViaVersion", "ViaVersion/ViaVersion");
        repositories.put("ViaBackwards", "ViaVersion/ViaBackwards");
        repositories.put("ViaRewind", "ViaVersion/ViaRewind");
        repositories.put("LuckPerms", "LuckPerms/LuckPerms");
        repositories.put("WorldGuard", "EngineHub/WorldGuard");
        repositories.put("PlaceholderAPI", "PlaceholderAPI/PlaceholderAPI");
        repositories.put("ProtocolLib", "dmulloy2/ProtocolLib");
        loadCustomRepositories();
    }

    private void loadCustomRepositories() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        java.io.File file = new java.io.File(plugin.getDataFolder(), "update-checks.yml");
        if (!file.exists()) plugin.saveResource("update-checks.yml", false);
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String name : yaml.getConfigurationSection("repositories") == null ? List.<String>of() : yaml.getConfigurationSection("repositories").getKeys(false)) {
            String repo = yaml.getString("repositories." + name, "");
            if (repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) repositories.put(name, repo);
        }
    }

    public void checkAll() {
        lastCheck = java.time.LocalDateTime.now().toString().replace('T', ' ');
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            String repo = repositories.get(installed.getName());
            if (repo == null) continue;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> check(installed, repo));
        }
    }

    private void check(Plugin installed, String repository) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repository + "/releases/latest"))
                    .timeout(Duration.ofSeconds(8)).header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LifestealCore-Leon-UpdateChecker").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;
            String json = response.body();
            String tag = capture(TAG, json);
            if (tag == null || tag.isBlank()) return;
            String body = unescape(capture(BODY, json));
            String url = capture(HTML, json);
            String current = installed.getPluginMeta().getVersion();
            boolean available = compareVersions(tag, current) > 0;
            Priority priority = classify(body);
            Update update = new Update(installed.getName(), current, tag, priority,
                    body == null ? "No release notes provided." : body, url == null ? "" : url, available);
            results.put(installed.getName().toLowerCase(Locale.ROOT), update);
        } catch (Exception ex) {
            plugin.getLogger().fine("Update check failed for " + installed.getName() + ": " + ex.getMessage());
        }
    }

    private static String capture(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input == null ? "" : input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unescape(String value) {
        if (value == null) return null;
        return value.replace("\\r", "").replace("\\n", "\n").replace("\\t", " ").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Priority classify(String notes) {
        String text = notes == null ? "" : notes.toLowerCase(Locale.ROOT);
        if (text.matches("(?s).*(security|vulnerability|cve-|exploit|remote code execution|\\brce\\b).*")) return Priority.CRITICAL;
        if (text.matches("(?s).*(fix|bug|crash|compatib|support|broken|regression).*")) return Priority.IMPORTANT;
        return Priority.OPTIONAL;
    }

    private static int compareVersions(String first, String second) {
        String a = first.replaceFirst("^[vV]", "").split("[-+]", 2)[0];
        String b = second.replaceFirst("^[vV]", "").split("[-+]", 2)[0];
        String[] aa = a.split("\\."), bb = b.split("\\.");
        for (int i = 0; i < Math.max(aa.length, bb.length); i++) {
            long av = i < aa.length ? leadingNumber(aa[i]) : 0;
            long bv = i < bb.length ? leadingNumber(bb[i]) : 0;
            if (av != bv) return Long.compare(av, bv);
        }
        return 0;
    }

    private static long leadingNumber(String part) {
        Matcher m = Pattern.compile("^\\d+").matcher(part);
        if (!m.find()) return 0;
        try { return Long.parseLong(m.group()); } catch (NumberFormatException ignored) { return 0; }
    }

    public Update get(String pluginName) { return results.get(pluginName.toLowerCase(Locale.ROOT)); }
    public List<Update> all() {
        List<Update> list = new ArrayList<>(results.values());
        list.sort((a, b) -> Boolean.compare(b.updateAvailable(), a.updateAvailable()));
        return Collections.unmodifiableList(list);
    }
    public String lastCheck() { return lastCheck; }
}
