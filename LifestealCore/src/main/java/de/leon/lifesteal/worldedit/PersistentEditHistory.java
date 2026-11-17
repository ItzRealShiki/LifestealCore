package de.leon.lifesteal.worldedit;

import de.leon.lifesteal.LifestealCore;
import de.leon.lifesteal.data.Database;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistent WorldEdit history. Snapshot payloads live in compressed files;
 * SQL stores searchable metadata and the integrity checksum.
 */
public final class PersistentEditHistory {
    private static final int FORMAT_VERSION = 1;

    public record Change(int x, int y, int z, String before, String after) { }

    public record History(String id, UUID worldId, String operation, String relativePath,
                          String checksum, int blockCount, String status) { }

    private final LifestealCore plugin;
    private final Database database;
    private final Path root;

    public PersistentEditHistory(LifestealCore plugin) {
        this.plugin = plugin;
        this.database = plugin.database();
        this.root = plugin.getDataFolder().toPath().resolve("worldedit").resolve("history")
                .toAbsolutePath().normalize();
    }

    public History save(UUID playerId, UUID worldId, String operation, List<Change> changes)
            throws IOException, SQLException {
        if (changes == null || changes.isEmpty()) throw new IllegalArgumentException("Snapshot is empty");
        String id = UUID.randomUUID().toString();
        Path playerDir = root.resolve(playerId.toString()).normalize();
        if (!playerDir.startsWith(root)) throw new IOException("Invalid history path");
        Files.createDirectories(playerDir);
        Path target = playerDir.resolve(id + ".lscundo");
        Path temporary = playerDir.resolve(id + ".tmp");

        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(FORMAT_VERSION);
                out.writeInt(changes.size());
                for (Change change : changes) {
                    out.writeInt(change.x());
                    out.writeInt(change.y());
                    out.writeInt(change.z());
                    out.writeUTF(change.before());
                    out.writeUTF(change.after());
                }
            }
            moveAtomically(temporary, target);
            String checksum = sha256(target);
            String relative = plugin.getDataFolder().toPath().toAbsolutePath().normalize()
                    .relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/');
            long now = System.currentTimeMillis();
            database.sync(connection -> {
                try (PreparedStatement supersede = connection.prepareStatement(
                    "UPDATE " + database.t("we_history") + " SET status='SUPERSEDED' WHERE player_uuid=? AND world_uuid=? AND status='UNDONE'")) {
                supersede.setString(1, playerId.toString());
                supersede.setString(2, worldId.toString());
                supersede.executeUpdate();
            }
            String sql = "INSERT INTO " + database.t("we_history")
                        + " (history_id,player_uuid,world_uuid,operation_type,created_at,block_count,"
                        + "snapshot_path,snapshot_sha256,status,metadata) VALUES (?,?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, id);
                    statement.setString(2, playerId.toString());
                    statement.setString(3, worldId.toString());
                    statement.setString(4, operation);
                    statement.setLong(5, now);
                    statement.setLong(6, changes.size());
                    statement.setString(7, relative);
                    statement.setString(8, checksum);
                    statement.setString(9, "APPLIED");
                    statement.setString(10, "{\"format\":1}");
                    statement.executeUpdate();
                }
                return null;
            });
            return new History(id, worldId, operation, relative, checksum, changes.size(), "APPLIED");
        } catch (IOException | SQLException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    public History latest(UUID playerId, UUID worldId, String status) throws SQLException {
        return database.sync(connection -> {
            String sql = "SELECT history_id,world_uuid,operation_type,snapshot_path,snapshot_sha256,"
                    + "block_count,status FROM " + database.t("we_history")
                    + " WHERE player_uuid=? AND world_uuid=? AND status=? ORDER BY created_at " + (status.equals("UNDONE") ? "ASC" : "DESC") + " LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, worldId.toString());
                statement.setString(3, status);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return null;
                    return new History(result.getString("history_id"),
                            UUID.fromString(result.getString("world_uuid")),
                            result.getString("operation_type"), result.getString("snapshot_path"),
                            result.getString("snapshot_sha256"), result.getInt("block_count"),
                            result.getString("status"));
                }
            }
        });
    }

    public List<Change> read(History history) throws IOException {
        Path file = plugin.getDataFolder().toPath().resolve(history.relativePath()).toAbsolutePath().normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IOException("History snapshot is missing or outside the history directory");
        }
        String actual = sha256(file);
        if (!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                history.checksum().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("History snapshot checksum mismatch");
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(file))))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) throw new IOException("Unsupported history format: " + version);
            int count = in.readInt();
            if (count < 0 || count > 5_000_000 || count != history.blockCount()) {
                throw new IOException("Invalid history entry count");
            }
            List<Change> changes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                changes.add(new Change(in.readInt(), in.readInt(), in.readInt(), in.readUTF(), in.readUTF()));
            }
            if (in.read() != -1) throw new IOException("Unexpected data at the end of the snapshot");
            return List.copyOf(changes);
        }
    }

    public void setStatus(String historyId, String status) throws SQLException {
        if (!status.equals("APPLIED") && !status.equals("UNDONE")) {
            throw new IllegalArgumentException("Invalid history status");
        }
        database.sync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + database.t("we_history") + " SET status=? WHERE history_id=?")) {
                statement.setString(1, status);
                statement.setString(2, historyId);
                if (statement.executeUpdate() != 1) throw new SQLException("History entry no longer exists");
            }
            return null;
        });
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
