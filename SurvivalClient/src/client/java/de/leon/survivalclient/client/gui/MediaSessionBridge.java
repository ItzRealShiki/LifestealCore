package de.leon.survivalclient.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

/** Reads Windows SMTC media metadata and album artwork through a PowerShell WinRT bridge. */
public final class MediaSessionBridge {
    private static final Identifier COVER_TEXTURE_ID = Identifier.fromNamespaceAndPath("survivalclient", "music_cover");
    private static volatile String title = "No media playing";
    private static volatile String artist = "Start music on your device";
    private static volatile long position;
    private static volatile long duration;
    private static volatile boolean available;
    private static volatile boolean playing;
    private static volatile boolean coverReady;
    private static volatile int coverWidth = 1;
    private static volatile int coverHeight = 1;
    private static String lastCoverPayload;
    private static BufferedWriter commands;
    private static boolean started;

    private MediaSessionBridge() {}

    public static synchronized void start() {
        if (started || !System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        started = true;
        Thread worker = new Thread(MediaSessionBridge::run, "ProVisuals-MediaSession");
        worker.setDaemon(true);
        worker.start();
    }

    private static void run() {
        try {
            Path script = FabricLoader.getInstance().getConfigDir().resolve("provisuals/MediaSessionBridge.ps1");
            Files.createDirectories(script.getParent());
            try (InputStream in = MediaSessionBridge.class.getResourceAsStream("/assets/survivalclient/media/MediaSessionBridge.ps1")) {
                if (in == null) throw new IOException("Media bridge resource missing");
                Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            }
            Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                    .redirectErrorStream(false).start();
            commands = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            Thread errors = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    while (reader.readLine() != null) { /* keep stderr drained */ }
                } catch (IOException ignored) {}
            }, "ProVisuals-MediaSession-Errors");
            errors.setDaemon(true);
            errors.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) update(line);
            }
        } catch (Exception ignored) {
            available = false;
            title = "Media session unavailable";
            artist = "Windows media access could not start";
            updateCover("");
        }
    }

    private static void update(String json) {
        try {
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            available = data.has("available") && data.get("available").getAsBoolean();
            title = available ? data.get("title").getAsString() : "No media playing";
            artist = available ? data.get("artist").getAsString() : "Play music in a supported app";
            position = data.has("position") ? Math.max(0, data.get("position").getAsLong()) : 0;
            duration = data.has("duration") ? Math.max(0, data.get("duration").getAsLong()) : 0;
            playing = data.has("playing") && data.get("playing").getAsBoolean();
            if (data.has("cover")) updateCover(data.get("cover").getAsString());
        } catch (RuntimeException ignored) {}
    }

    private static void updateCover(String encoded) {
        if (encoded == null || encoded.equals(lastCoverPayload)) return;
        lastCoverPayload = encoded;
        Minecraft minecraft = Minecraft.getInstance();
        if (encoded.isEmpty()) {
            minecraft.execute(() -> coverReady = false);
            return;
        }

        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        minecraft.execute(() -> {
            try {
                var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                if (decoded == null || decoded.getWidth() < 1 || decoded.getHeight() < 1) {
                    coverReady = false;
                    return;
                }
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(decoded, "png", png);
                NativeImage image = NativeImage.read(png.toByteArray());
                int width = image.getWidth();
                int height = image.getHeight();
                DynamicTexture texture = new DynamicTexture(() -> "provisuals_music_cover", image);
                minecraft.getTextureManager().register(COVER_TEXTURE_ID, texture);
                coverWidth = width;
                coverHeight = height;
                coverReady = true;
            } catch (Exception ignored) {
                coverReady = false;
            }
        });
    }

    public static String title() { return title; }
    public static String artist() { return artist; }
    public static long position() { return position; }
    public static long duration() { return duration; }
    public static boolean available() { return available; }
    public static boolean playing() { return playing; }
    public static boolean coverReady() { return coverReady; }
    public static Identifier coverTextureId() { return COVER_TEXTURE_ID; }
    public static int coverWidth() { return coverWidth; }
    public static int coverHeight() { return coverHeight; }

    public static synchronized void control(String command) {
        if (commands == null) return;
        try { commands.write(command); commands.newLine(); commands.flush(); }
        catch (IOException ignored) {}
    }
}
