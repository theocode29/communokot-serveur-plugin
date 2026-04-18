package io.communokot.progress.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.communokot.progress.model.PlayerProgress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Builds the global JSON snapshot and writes it to disk atomically.
 *
 * <p>Output format:
 * <pre>{@code
 * {
 *   "lastUpdate": 1713456000,
 *   "players": {
 *     "uuid": {
 *       "name": "Pseudo",
 *       "advancements": { "minecraft:story/mine_stone": true },
 *       "mined": { "stone": 120, "coal_ore": 14 },
 *       "distance_walked_cm": 152340,
 *       "play_ticks": 72000
 *     }
 *   }
 * }
 * }</pre>
 */
public final class ExportService {
    private final Path dataFolder;
    private volatile Path snapshotPath;
    private final Gson gson;

    public ExportService(Path dataFolder, Path snapshotPath) {
        this.dataFolder = dataFolder;
        this.snapshotPath = snapshotPath;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    /**
     * Build the full JSON string from the current progress cache.
     */
    public String buildSnapshotJson(Instant generatedAt, Map<UUID, PlayerProgress> progressByPlayer) {
        // Root object
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("lastUpdate", generatedAt.getEpochSecond());

        // Players map, sorted by UUID string for deterministic output
        Map<String, Object> players = new TreeMap<>();

        for (Map.Entry<UUID, PlayerProgress> entry : progressByPlayer.entrySet()) {
            String uuid = entry.getKey().toString();
            PlayerProgress p = entry.getValue();

            Map<String, Object> playerData = new LinkedHashMap<>();
            playerData.put("name", p.name());

            // Advancements
            Map<String, Boolean> advancements = new TreeMap<>();
            for (String key : p.completedAdvancements()) {
                advancements.put(key, true);
            }
            playerData.put("advancements", advancements);

            // Mined blocks (sorted for stable output)
            playerData.put("mined", new TreeMap<>(p.mined()));

            // Distance and play time
            playerData.put("distance_walked_cm", p.distanceWalkedCm());
            playerData.put("play_ticks", p.playTicks());

            players.put(uuid, playerData);
        }

        root.put("players", players);

        return gson.toJson(root) + "\n";
    }

    /**
     * Atomically write the JSON to the configured snapshot path.
     */
    public Path writeSnapshot(String json) throws IOException {
        Path destination = resolveSnapshotPath();
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(tempFile, json, StandardCharsets.UTF_8);

        try {
            Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        return destination;
    }

    public void updateSnapshotPath(Path snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public Path currentSnapshotPath() {
        return snapshotPath;
    }

    private Path resolveSnapshotPath() {
        if (snapshotPath.isAbsolute()) {
            return snapshotPath;
        }
        return dataFolder.resolve(snapshotPath);
    }
}
