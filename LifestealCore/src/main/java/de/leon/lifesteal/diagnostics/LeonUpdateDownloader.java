package de.leon.lifesteal.diagnostics;

import de.leon.lifesteal.LifestealCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.File;
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
                core.getLogger().log(Level.WARNING, "Could not stage plugin update for " + target.getName(), ex);
                result = new Result(false, "Download failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            Result completed = result;
            Bukkit.getScheduler().runTask(core, () -> callback.accept(completed));
        });
    }

    private Result downloadAndStage(Plugin target, LeonUpdateChecker.Update update) throws Exception {
        if (target == null || update == null || !update.updateAvailable()) return new Result(false, "No applicable update found.");
        if (update.assetId() == null || update.assetId().isBlank() || update.repository() == null || !update.repository().equalsIgnoreCase("Vextex123/LifestealCore")) return new Result(false, "No valid private GitHub release asset is configured.");
        String token = readGitHubToken();
        if (token.isBlank()) return new Result(false, "Private GitHub token missing. Add it to plugins/LifestealCore/github-token.txt.");
        URI uri = URI.create("https://api.github.com/repos/" + update.repository() + "/releases/assets/" + update.assetId());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost(uri.getHost())) {
            return new Result(false, "Download blocked: release asset host is not trusted.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2))
                .header("User-Agent", "LifestealCore-PluginUpdater")
                .header("Accept", "application/octet-stream")
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
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
            if (!target.getName().equalsIgnoreCase(jarPluginName)) return new Result(false, "JAR identity mismatch: expected " + target.getName() + ", got " + jarPluginName + ".");
            if (jarVersion.isBlank()) return new Result(false, "Downloaded JAR has no version in plugin.yml.");

            Path installed = Path.of(target.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(installed) || !installed.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return new Result(false, "Installed plugin is not loaded from a regular JAR file.");
            }
            Path pluginsDir = core.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
            if (pluginsDir == null || !installed.startsWith(pluginsDir)) return new Result(false, "Installed JAR is outside the server plugins directory.");
            Path backupDir = core.getDataFolder().toPath().resolve("update-backups");
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backup = backupDir.resolve(target.getName() + "-" + update.installed() + "-" + stamp + ".jar");
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
            return new Result(true, "Verified " + jarPluginName + " " + jarVersion + ". Backup saved; update staged for the next server startup. A restart is required.");
        } finally {
            Files.deleteIfExists(jarPath);
        }
    }

    private String readGitHubToken() throws Exception {
        Path tokenFile = core.getDataFolder().toPath().resolve("github-token.txt");
        if (!Files.isRegularFile(tokenFile)) return "";
        return Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
    }

    private boolean allowedHost(String host) {
        if (host == null) return false;
        return host.equalsIgnoreCase("api.github.com")
                || host.equalsIgnoreCase("github.com")
                || host.equalsIgnoreCase("release-assets.githubusercontent.com")
                || host.equalsIgnoreCase("objects.githubusercontent.com");
    }

    public record Result(boolean success, String message) { }
}
