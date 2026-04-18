package io.communokot.progress.db;

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

public final class DatabaseService {
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS player_progress (
            uuid TEXT PRIMARY KEY,
            stone_broken INTEGER NOT NULL,
            updated_at TEXT NOT NULL
        )
        """;

    private static final String SELECT_ALL_SQL = "SELECT uuid, stone_broken FROM player_progress";

    private static final String UPSERT_SQL = """
        INSERT INTO player_progress (uuid, stone_broken, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            stone_broken = excluded.stone_broken,
            updated_at = excluded.updated_at
        """;

    private final JavaPlugin plugin;
    private final Path databasePath;
    private Connection connection;

    public DatabaseService(JavaPlugin plugin, Path databasePath) {
        this.plugin = plugin;
        this.databasePath = databasePath;
    }

    public synchronized void start() throws Exception {
        Path resolvedPath = resolvePath();
        Files.createDirectories(resolvedPath.getParent());

        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + resolvedPath.toAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE_SQL);
        }
    }

    public synchronized Map<UUID, Long> loadStoneProgress() {
        ensureStarted();

        Map<UUID, Long> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                String uuidRaw = rs.getString("uuid");
                long stoneBroken = rs.getLong("stone_broken");
                try {
                    result.put(UUID.fromString(uuidRaw), Math.max(0L, stoneBroken));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().log(Level.WARNING, "Ignoring invalid UUID in database: " + uuidRaw, exception);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load player progress from SQLite", exception);
        }

        return result;
    }

    public synchronized void saveStoneProgress(Map<UUID, Long> snapshot, Instant updatedAt) {
        ensureStarted();

        String updatedAtIso = updatedAt.toString();

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
                    statement.setString(1, entry.getKey().toString());
                    statement.setLong(2, Math.max(0L, entry.getValue()));
                    statement.setString(3, updatedAtIso);
                    statement.addBatch();
                }
                statement.executeBatch();
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

    public Path configuredPath() {
        return databasePath;
    }

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
