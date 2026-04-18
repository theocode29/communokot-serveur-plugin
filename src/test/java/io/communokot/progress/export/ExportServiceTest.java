package io.communokot.progress.export;

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
    void buildSnapshotJsonShouldBeDeterministicAndSorted() {
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        ExportService service = new ExportService(tempDir, Path.of("snapshots/test.json"));

        String json = service.buildSnapshotJson(
            Instant.parse("2026-04-18T12:00:00Z"),
            Map.of(higher, 9L, lower, 1L)
        );

        int firstKey = json.indexOf(lower.toString());
        int secondKey = json.indexOf(higher.toString());

        Assertions.assertTrue(firstKey > 0);
        Assertions.assertTrue(secondKey > firstKey);
        Assertions.assertTrue(json.contains("\"generatedAt\": \"2026-04-18T12:00:00Z\""));
    }

    @Test
    void writeSnapshotShouldCreateTargetFile() throws Exception {
        ExportService service = new ExportService(tempDir, Path.of("snapshots/stone-progress.json"));
        String payload = "{\n  \"generatedAt\": \"2026-04-18T12:00:00Z\",\n  \"players\": {}\n}\n";

        Path output = service.writeSnapshot(payload);

        Assertions.assertTrue(Files.exists(output));
        Assertions.assertEquals(payload, Files.readString(output));
    }
}
