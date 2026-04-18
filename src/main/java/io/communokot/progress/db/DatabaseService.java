package io.communokot.progress.db;

import io.communokot.progress.model.PlayerProgress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SQLite persistence layer.
 *
 * <p>Schema (v2):
 * <ul>
 *   <li>{@code player_stats_v2} – identity, distance, play ticks</li>
 *   <li>{@code player_mined_v2} – per-block mined counts</li>
 *   <li>{@code player_advancements_v2} – completed advancement keys</li>
 * </ul>
 */
public final class DatabaseService {

    // ── DDL ─────────────────────────────────────────────────────────────

    private static final String CREATE_STATS_TABLE = """
        CREATE TABLE IF NOT EXISTS player_stats_v2 (
            uuid                TEXT PRIMARY KEY,
            name                TEXT NOT NULL,
            distance_walked_cm  INTEGER NOT NULL DEFAULT 0,
            play_ticks          INTEGER NOT NULL DEFAULT 0,
            updated_at          TEXT NOT NULL
        )
        """;

    private static final String CREATE_MINED_TABLE = """
        CREATE TABLE IF NOT EXISTS player_mined_v2 (
            uuid      TEXT NOT NULL,
            block_key TEXT NOT NULL,
            count     INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (uuid, block_key)
        )
        """;

    private static final String CREATE_ADVANCEMENTS_TABLE = """
        CREATE TABLE IF NOT EXISTS player_advancements_v2 (
            uuid            TEXT NOT NULL,
            advancement_key TEXT NOT NULL,
            PRIMARY KEY (uuid, advancement_key)
        )
        """;

    // ── DML ─────────────────────────────────────────────────────────────

    private static final String SELECT_ALL_STATS =
        "SELECT uuid, name, distance_walked_cm, play_ticks FROM player_stats_v2";

    private static final String SELECT_ALL_MINED =
        "SELECT uuid, block_key, count FROM player_mined_v2";

    private static final String SELECT_ALL_ADVANCEMENTS =
        "SELECT uuid, advancement_key FROM player_advancements_v2";

    private static final String UPSERT_STATS = """
        INSERT INTO player_stats_v2 (uuid, name, distance_walked_cm, play_ticks, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            name               = excluded.name,
            distance_walked_cm = excluded.distance_walked_cm,
            play_ticks         = excluded.play_ticks,
            updated_at         = excluded.updated_at
        """;

    private static final String UPSERT_MINED = """
        INSERT INTO player_mined_v2 (uuid, block_key, count)
        VALUES (?, ?, ?)
        ON CONFLICT(uuid, block_key) DO UPDATE SET
            count = excluded.count
        """;

    private static final String UPSERT_ADVANCEMENT = """
        INSERT OR IGNORE INTO player_advancements_v2 (uuid, advancement_key)
        VALUES (?, ?)
        """;

    // ── Fields ──────────────────────────────────────────────────────────

    private final JavaPlugin plugin;
    private final Path databasePath;
    private Connection connection;

    public DatabaseService(JavaPlugin plugin, Path databasePath) {
        this.plugin = plugin;
        this.databasePath = databasePath;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    public synchronized void start() throws Exception {
        Path resolvedPath = resolvePath();
        Files.createDirectories(resolvedPath.getParent());

        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + resolvedPath.toAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_STATS_TABLE);
            statement.execute(CREATE_MINED_TABLE);
            statement.execute(CREATE_ADVANCEMENTS_TABLE);
        }
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to close SQLite connection cleanly", exception);
        } finally {
            connection = null;
        }
    }

    // ── Load ────────────────────────────────────────────────────────────

    /**
     * Load all persisted player progress from the three v2 tables.
     */
    public synchronized Map<UUID, PlayerProgress> loadAllProgress() {
        ensureStarted();

        Map<UUID, PlayerProgress> result = new HashMap<>();

        // 1. Core stats
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_ALL_STATS);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String uuidRaw = rs.getString("uuid");
                try {
                    UUID uuid = UUID.fromString(uuidRaw);
                    PlayerProgress progress = new PlayerProgress(rs.getString("name"));
                    progress.setDistanceWalkedCm(rs.getLong("distance_walked_cm"));
                    progress.setPlayTicks(rs.getLong("play_ticks"));
                    result.put(uuid, progress);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().log(Level.WARNING, "Ignoring invalid UUID in player_stats_v2: " + uuidRaw, ex);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load player stats from SQLite", exception);
        }

        // 2. Mined blocks
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_ALL_MINED);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String uuidRaw = rs.getString("uuid");
                try {
                    UUID uuid = UUID.fromString(uuidRaw);
                    PlayerProgress progress = result.get(uuid);
                    if (progress != null) {
                        progress.setMinedCount(rs.getString("block_key"), rs.getLong("count"));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Already warned above
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load mined data from SQLite", exception);
        }

        // 3. Advancements
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_ALL_ADVANCEMENTS);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String uuidRaw = rs.getString("uuid");
                try {
                    UUID uuid = UUID.fromString(uuidRaw);
                    PlayerProgress progress = result.get(uuid);
                    if (progress != null) {
                        progress.markAdvancement(rs.getString("advancement_key"));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Already warned above
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load advancements from SQLite", exception);
        }

        return result;
    }

    // ── Save ────────────────────────────────────────────────────────────

    /**
     * Persist the full player progress snapshot into SQLite (all three tables).
     */
    public synchronized void saveAllProgress(Map<UUID, PlayerProgress> snapshot, Instant updatedAt) {
        ensureStarted();

        String updatedAtIso = updatedAt.toString();

        try {
            connection.setAutoCommit(false);

            // 1. Core stats
            try (PreparedStatement stmt = connection.prepareStatement(UPSERT_STATS)) {
                for (Map.Entry<UUID, PlayerProgress> entry : snapshot.entrySet()) {
                    PlayerProgress p = entry.getValue();
                    stmt.setString(1, entry.getKey().toString());
                    stmt.setString(2, p.name());
                    stmt.setLong(3, p.distanceWalkedCm());
                    stmt.setLong(4, p.playTicks());
                    stmt.setString(5, updatedAtIso);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            // 2. Mined blocks
            try (PreparedStatement stmt = connection.prepareStatement(UPSERT_MINED)) {
                for (Map.Entry<UUID, PlayerProgress> entry : snapshot.entrySet()) {
                    String uuid = entry.getKey().toString();
                    for (Map.Entry<String, Long> mined : entry.getValue().mined().entrySet()) {
                        stmt.setString(1, uuid);
                        stmt.setString(2, mined.getKey());
                        stmt.setLong(3, mined.getValue());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }

            // 3. Advancements
            try (PreparedStatement stmt = connection.prepareStatement(UPSERT_ADVANCEMENT)) {
                for (Map.Entry<UUID, PlayerProgress> entry : snapshot.entrySet()) {
                    String uuid = entry.getKey().toString();
                    for (String advancement : entry.getValue().completedAdvancements()) {
                        stmt.setString(1, uuid);
                        stmt.setString(2, advancement);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }

            connection.commit();
        } catch (SQLException exception) {
            tryRollback();
            throw new IllegalStateException("Unable to persist player progress in SQLite", exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // Ignore auto-commit reset failures during cleanup.
            }
        }
    }

    // ── Accessors ───────────────────────────────────────────────────────

    public Path configuredPath() {
        return databasePath;
    }

    // ── Internal ────────────────────────────────────────────────────────

    private void ensureStarted() {
        if (connection == null) {
            throw new IllegalStateException("DatabaseService is not started");
        }
    }

    private Path resolvePath() {
        if (databasePath.isAbsolute()) {
            return databasePath;
        }
        return plugin.getDataFolder().toPath().resolve(databasePath);
    }

    private void tryRollback() {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            plugin.getLogger().log(Level.WARNING, "SQLite rollback failed", rollbackException);
        }
    }
}
