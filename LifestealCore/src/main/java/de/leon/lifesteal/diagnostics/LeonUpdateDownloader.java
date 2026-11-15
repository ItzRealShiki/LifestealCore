package de.leon.lifesteal.diagnostics;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class LeonUpdateDownloader {
    private static final long MAX_BYTES = 128L * 1024L * 1024L;
    private static final String PRIVATE_REPOSITORY = "Vextex123/LifestealCore";
    private final LifestealCore core;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public LeonUpdateDownloader(LifestealCore core) { this.core = core; }

    public void stage(Plugin target, LeonUpdateChecker.Update update, java.util.function.Consumer<Result> callback) {
        CompletableFuture.runAsync(() -> {
            Result result;
            try { result = downloadAndStage(target, update); }
            catch (Exception ex) {
                core.getLogger().log(Level.WARNING, "Could not stage plugin update for " + (target == null ? "unknown" : target.getName()), ex);
                result = new Result(false, "Download failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            Result completed = result;
            Bukkit.getScheduler().runTask(core, () -> callback.accept(completed));
        });
    }

    private Result downloadAndStage(Plugin target, LeonUpdateChecker.Update update) throws Exception {
        if (target == null || update == null || !update.updateAvailable()) return new Result(false, "No applicable update found.");
        if (update.downloadUrl() == null || update.downloadUrl().isBlank()) return new Result(false, "The release has no matching JAR download.");
        boolean privateRelease = PRIVATE_REPOSITORY.equalsIgnoreCase(update.repository());
        URI uri;
        HttpRequest.Builder request;
        if (privateRelease) {
            if (update.assetId() == null || update.assetId().isBlank()) return new Result(false, "Private release asset ID is missing.");
            String token = readGitHubToken();
            if (token.isBlank()) return new Result(false, "Private GitHub token missing. Set updater.github-token in config.yml.");
            uri = URI.create("https://api.github.com/repos/" + update.repository() + "/releases/assets/" + update.assetId());
            request = HttpRequest.newBuilder(uri).header("Accept", "application/octet-stream")
                    .header("Authorization", "Bearer " + token);
        } else {
            uri = URI.create(update.downloadUrl());
            request = HttpRequest.newBuilder(uri);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost(uri.getHost())) {
            return new Result(false, "Download blocked: release URL host is not trusted.");
        }
        HttpRequest httpRequest = request.timeout(Duration.ofMinutes(2))
                .header("User-Agent", "LifestealCore-PluginUpdater").GET().build();
        HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) return new Result(false, "Download server returned HTTP " + response.statusCode() + ".");
        if (response.body().length == 0 || response.body().length > MAX_BYTES) return new Result(false, "Downloaded file has an invalid size.");
        URI finalUri = response.uri();
        if (!"https".equalsIgnoreCase(finalUri.getScheme()) || !allowedHost(finalUri.getHost())) {
            return new Result(false, "Download redirect was blocked because its host is not trusted.");
        }

        Path jarPath = Files.createTempFile("lsc-plugin-check-", ".jar");
        try {
            Files.write(jarPath, response.body());
            String jarPluginName;
            String jarVersion;
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                var entry = jar.getJarEntry("plugin.yml");
                if (entry == null) return new Result(false, "Downloaded JAR has no plugin.yml.");
                try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                    YamlConfiguration metadata = YamlConfiguration.loadConfiguration(reader);
                    jarPluginName = metadata.getString("name", "");
                    jarVersion = metadata.getString("version", "");
                }
            }
            if (!target.getName().equalsIgnoreCase(jarPluginName)) {
                return new Result(false, "JAR identity mismatch: expected " + target.getName() + ", got " + jarPluginName + ".");
            }
            if (jarVersion.isBlank()) return new Result(false, "Downloaded JAR has no version in plugin.yml.");
            if (!isNewer(jarVersion, target.getPluginMeta().getVersion())) {
                return new Result(false, "Downloaded JAR version " + jarVersion + " is not newer than the installed version " + target.getPluginMeta().getVersion() + ".");
            }

            Path installed = Path.of(target.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(installed) || !installed.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return new Result(false, "Installed plugin is not loaded from a regular JAR file.");
            }
            Path pluginsDir = core.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
            if (pluginsDir == null || !installed.startsWith(pluginsDir)) return new Result(false, "Installed JAR is outside the server plugins directory.");

            Path backupDir = core.getDataFolder().toPath().resolve("update-backups");
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backup = backupDir.resolve(target.getName() + "-" + safeFilePart(target.getPluginMeta().getVersion()) + "-" + stamp + ".jar");
            Files.copy(installed, backup, StandardCopyOption.COPY_ATTRIBUTES);

            Path updateDir = pluginsDir.resolve("update");
            Files.createDirectories(updateDir);
            Path staged = updateDir.resolve(installed.getFileName().toString());
            Path temp = updateDir.resolve(installed.getFileName().toString() + ".part");
            Files.copy(jarPath, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, staged, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            savePending(target.getName(), jarVersion, installed.getFileName().toString(), update.repository());
            return new Result(true, "Verified " + jarPluginName + " " + jarVersion + ". Backup saved and update staged. It is NOT active yet; restart the server. The version will be checked after startup.");
        } finally {
            Files.deleteIfExists(jarPath);
        }
    }

    private void savePending(String pluginName, String expectedVersion, String jarName, String repository) throws Exception {
        Path file = pendingFile();
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        String key = "updates." + pluginName;
        yaml.set(key + ".expected-version", expectedVersion);
        yaml.set(key + ".jar", jarName);
        yaml.set(key + ".repository", repository);
        yaml.set(key + ".staged-at", LocalDateTime.now().toString());
        yaml.set(key + ".status", "WAITING_FOR_RESTART");
        yaml.save(file.toFile());
    }

    public void verifyPending() {
        Path file = pendingFile();
        if (!Files.isRegularFile(file)) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var section = yaml.getConfigurationSection("updates");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            String expected = yaml.getString("updates." + name + ".expected-version", "");
            Plugin loaded = Bukkit.getPluginManager().getPlugin(name);
            String actual = loaded == null ? "NOT_LOADED" : loaded.getPluginMeta().getVersion();
            if (loaded != null && expected.equals(actual)) {
                core.getLogger().info("Plugin update confirmed: " + name + " is now running version " + actual + ".");
                yaml.set("updates." + name, null);
            } else {
                String reason = loaded == null
                        ? "plugin was not loaded after restart"
                        : "expected version " + expected + " but server loaded " + actual;
                yaml.set("updates." + name + ".status", "NOT_APPLIED");
                yaml.set("updates." + name + ".last-check", LocalDateTime.now().toString());
                yaml.set("updates." + name + ".actual-version", actual);
                core.getLogger().severe("Plugin update NOT applied: " + name + " — " + reason + ". Check the plugins/update folder and server startup log. The previous JAR backup is in plugins/LifestealCore/update-backups.");
            }
        }
        try {
            if (yaml.getConfigurationSection("updates") == null || yaml.getConfigurationSection("updates").getKeys(false).isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                yaml.save(file.toFile());
            }
        } catch (Exception ex) {
            core.getLogger().log(Level.WARNING, "Could not save plugin update verification state.", ex);
        }
    }

    private Path pendingFile() { return core.getDataFolder().toPath().resolve("update-pending.yml"); }

    private boolean isNewer(String candidate, String installed) {
        return compareVersions(candidate, installed) > 0;
    }

    private int compareVersions(String first, String second) {
        String[] a = first.replaceFirst("^[vV]", "").split("[-+]", 2)[0].split("\\.");
        String[] b = second.replaceFirst("^[vV]", "").split("[-+]", 2)[0].split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            long av = i < a.length ? leadingNumber(a[i]) : 0;
            long bv = i < b.length ? leadingNumber(b[i]) : 0;
            if (av != bv) return Long.compare(av, bv);
        }
        return 0;
    }

    private long leadingNumber(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\d+").matcher(value);
        if (!matcher.find()) return 0;
        try { return Long.parseLong(matcher.group()); } catch (NumberFormatException ignored) { return 0; }
    }

    private String safeFilePart(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private String readGitHubToken() {
        String token = core.getConfig().getString("updater.github-token", "");
        return token == null ? "" : token.trim();
    }

    private boolean allowedHost(String host) {
        if (host == null) return false;
        return host.equalsIgnoreCase("api.github.com")
                || host.equalsIgnoreCase("github.com")
                || host.equalsIgnoreCase("release-assets.githubusercontent.com")
                || host.equalsIgnoreCase("objects.githubusercontent.com")
                || host.equalsIgnoreCase("github-releases.githubusercontent.com");
    }

    public record Result(boolean success, String message) { }
}
