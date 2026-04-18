package io.communokot.progress.export;

import io.communokot.progress.model.PlayerProgress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildSnapshotJsonShouldContainLastUpdateAndPlayerData() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

        PlayerProgress progress = new PlayerProgress("TestPlayer");
        progress.setDistanceWalkedCm(152340);
        progress.setPlayTicks(72000);
        progress.setMinedCount("stone", 120);
        progress.setMinedCount("diamond_ore", 1);
        progress.markAdvancement("minecraft:story/mine_stone");

        ExportService service = new ExportService(tempDir, Path.of("snapshots/test.json"));

        Instant generatedAt = Instant.parse("2026-04-18T12:00:00Z");
        String json = service.buildSnapshotJson(generatedAt, Map.of(uuid, progress));

        // lastUpdate is epoch seconds
        Assertions.assertTrue(json.contains("\"lastUpdate\": " + generatedAt.getEpochSecond()),
            "JSON should contain lastUpdate as epoch seconds");

        // Player identity
        Assertions.assertTrue(json.contains("\"name\": \"TestPlayer\""));

        // Mined
        Assertions.assertTrue(json.contains("\"stone\": 120"));
        Assertions.assertTrue(json.contains("\"diamond_ore\": 1"));

        // Distance and play time
        Assertions.assertTrue(json.contains("\"distance_walked_cm\": 152340"));
        Assertions.assertTrue(json.contains("\"play_ticks\": 72000"));

        // Advancement
        Assertions.assertTrue(json.contains("\"minecraft:story/mine_stone\": true"));
    }

    @Test
    void buildSnapshotJsonShouldSortPlayersbyUuid() {
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        PlayerProgress p1 = new PlayerProgress("First");
        PlayerProgress p2 = new PlayerProgress("Second");

        ExportService service = new ExportService(tempDir, Path.of("snapshots/test.json"));

        String json = service.buildSnapshotJson(
            Instant.parse("2026-04-18T12:00:00Z"),
            Map.of(higher, p2, lower, p1)
        );

        int firstKey = json.indexOf(lower.toString());
        int secondKey = json.indexOf(higher.toString());

        Assertions.assertTrue(firstKey > 0);
        Assertions.assertTrue(secondKey > firstKey,
            "Players should be sorted by UUID string");
    }

    @Test
    void writeSnapshotShouldCreateTargetFile() throws Exception {
        ExportService service = new ExportService(tempDir, Path.of("snapshots/progress.json"));
        String payload = "{\"lastUpdate\": 0, \"players\": {}}\n";

        Path output = service.writeSnapshot(payload);

        Assertions.assertTrue(Files.exists(output));
        Assertions.assertEquals(payload, Files.readString(output));
    }

    @Test
    void emptySnapshotShouldProduceValidJson() {
        ExportService service = new ExportService(tempDir, Path.of("snapshots/test.json"));

        String json = service.buildSnapshotJson(Instant.EPOCH, Map.of());

        Assertions.assertTrue(json.contains("\"lastUpdate\": 0"));
        Assertions.assertTrue(json.contains("\"players\": {}"));
    }
}
