package de.leon.simplelitematica.data;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SchematicFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("simplelitematica");
    private static final Path SCHEMATICS = ROOT.resolve("schematics");
    private static final Path ACTIVE = ROOT.resolve("active.txt");

    private SchematicFiles() {}

    public static Path directory() {
        ensureDirectories();
        return SCHEMATICS;
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(SCHEMATICS);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create schematic folder.", exception);
        }
    }

    public static List<Path> list() {
        ensureDirectories();
        try (var stream = Files.list(SCHEMATICS)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".slite"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    public static void setActive(String fileName) throws IOException {
        Path file = safeFile(fileName);
        if (!Files.isRegularFile(file)) throw new IOException("Schematic file does not exist.");
        Files.writeString(ACTIVE, file.getFileName().toString(), StandardCharsets.UTF_8);
    }

    public static String getActiveName() {
        try {
            if (Files.exists(ACTIVE)) return Files.readString(ACTIVE, StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {}
        return "";
    }

    public static SchematicData loadActive() throws IOException {
        String name = getActiveName();
        if (name.isBlank()) throw new IOException("No schematic selected. Open /liteschematics first.");
        return load(name);
    }

    public static SchematicData load(String fileName) throws IOException {
        Path file = safeFile(fileName);
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!"simplelitematica".equals(root.get("format").getAsString())) {
            throw new IOException("This is not a SimpleLitematica file.");
        }
        if (root.get("version").getAsInt() != 1) throw new IOException("Unsupported .slite version.");
        int sx = root.get("sizeX").getAsInt();
        int sy = root.get("sizeY").getAsInt();
        int sz = root.get("sizeZ").getAsInt();
        JsonArray array = root.getAsJsonArray("blocks");
        if (array.size() > 100_000) throw new IOException("Schematic contains too many blocks (limit: 100,000).");
        List<SchematicData.BlockEntry> blocks = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject block = element.getAsJsonObject();
            BlockState state = BlockState.CODEC.parse(JsonOps.INSTANCE, block.get("state"))
                    .getOrThrow(message -> new IllegalArgumentException("Invalid block state: " + message));
            blocks.add(new SchematicData.BlockEntry(
                    block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt(), state));
        }
        return new SchematicData(sx, sy, sz, blocks);
    }

    public static void save(String name, SchematicData data) throws IOException {
        String safeName = normalizeName(name);
        JsonObject root = new JsonObject();
        root.addProperty("format", "simplelitematica");
        root.addProperty("version", 1);
        root.addProperty("sizeX", data.sizeX());
        root.addProperty("sizeY", data.sizeY());
        root.addProperty("sizeZ", data.sizeZ());
        JsonArray blocks = new JsonArray();
        for (SchematicData.BlockEntry entry : data.blocks()) {
            JsonObject block = new JsonObject();
            block.addProperty("x", entry.x());
            block.addProperty("y", entry.y());
            block.addProperty("z", entry.z());
            block.add("state", BlockState.CODEC.encodeStart(JsonOps.INSTANCE, entry.state())
                    .getOrThrow(message -> new IllegalArgumentException("Could not encode block state: " + message)));
            blocks.add(block);
        }
        root.add("blocks", blocks);
        Path file = safeFile(safeName);
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        setActive(file.getFileName().toString());
    }

    public static void delete(String fileName) throws IOException {
        Path file = safeFile(fileName);
        Files.deleteIfExists(file);
        if (getActiveName().equals(file.getFileName().toString())) Files.deleteIfExists(ACTIVE);
    }

    private static Path safeFile(String fileName) throws IOException {
        ensureDirectories();
        String safe = normalizeName(fileName);
        Path file = SCHEMATICS.resolve(safe).normalize();
        if (!file.getParent().equals(SCHEMATICS.normalize())) throw new IOException("Invalid schematic filename.");
        return file;
    }

    private static String normalizeName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.endsWith(".slite")) clean = clean.substring(0, clean.length() - 6);
        clean = clean.replaceAll("[^a-zA-Z0-9 _-]", "_").trim();
        if (clean.isBlank()) clean = "Schematic";
        return clean + ".slite";
    }
}
