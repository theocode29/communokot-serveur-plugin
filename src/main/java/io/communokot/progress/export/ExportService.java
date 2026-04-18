package io.communokot.progress.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class ExportService {
    private final Path dataFolder;
    private volatile Path snapshotPath;

    public ExportService(Path dataFolder, Path snapshotPath) {
        this.dataFolder = dataFolder;
        this.snapshotPath = snapshotPath;
    }

    public String buildSnapshotJson(Instant generatedAt, Map<UUID, Long> progressByPlayer) {
        Map<String, Long> sorted = new TreeMap<>();
        for (Map.Entry<UUID, Long> entry : progressByPlayer.entrySet()) {
            sorted.put(entry.getKey().toString(), Math.max(0L, entry.getValue()));
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generatedAt\": \"").append(generatedAt).append("\",\n");
        json.append("  \"players\": {");

        boolean first = true;
        for (Map.Entry<String, Long> entry : sorted.entrySet()) {
            if (first) {
                json.append("\n");
                first = false;
            } else {
                json.append(",\n");
            }
            json
                .append("    \"")
                .append(entry.getKey())
                .append("\": ")
                .append(entry.getValue());
        }

        if (!sorted.isEmpty()) {
            json.append('\n');
        }

        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

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
