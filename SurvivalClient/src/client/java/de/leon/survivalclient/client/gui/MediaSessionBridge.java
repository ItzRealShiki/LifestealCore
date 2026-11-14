package de.leon.survivalclient.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads Windows SMTC media metadata through a small PowerShell WinRT bridge. */
public final class MediaSessionBridge {
    private static volatile String title = "No media playing";
    private static volatile String artist = "Start music on your device";
    private static volatile long position;
    private static volatile long duration;
    private static volatile boolean available;
    private static volatile boolean playing;
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
        } catch (RuntimeException ignored) {}
    }

    public static String title() { return title; }
    public static String artist() { return artist; }
    public static long position() { return position; }
    public static long duration() { return duration; }
    public static boolean available() { return available; }
    public static boolean playing() { return playing; }

    public static synchronized void control(String command) {
        if (commands == null) return;
        try { commands.write(command); commands.newLine(); commands.flush(); }
        catch (IOException ignored) {}
    }
}
