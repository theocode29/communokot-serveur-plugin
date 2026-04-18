package io.communokot.progress.service;

import io.communokot.progress.model.PlayerProgress;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProgressServiceTest {

    @Test
    void loadFromDatabaseShouldHydrateSnapshot() {
        ProgressService service = new ProgressService();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        PlayerProgress progressA = new PlayerProgress("Alice");
        progressA.setDistanceWalkedCm(5000);
        progressA.setPlayTicks(1200);
        progressA.setMinedCount("stone", 42);

        PlayerProgress progressB = new PlayerProgress("Bob");
        progressB.setDistanceWalkedCm(100);
        progressB.markAdvancement("minecraft:story/root");

        service.loadFromDatabase(Map.of(playerA, progressA, playerB, progressB));

        Map<UUID, PlayerProgress> snapshot = service.snapshot();
        Assertions.assertEquals(2, service.trackedPlayers());
        Assertions.assertEquals("Alice", snapshot.get(playerA).name());
        Assertions.assertEquals(5000, snapshot.get(playerA).distanceWalkedCm());
        Assertions.assertEquals(42, snapshot.get(playerA).mined().get("stone"));
        Assertions.assertTrue(snapshot.get(playerB).hasAdvancement("minecraft:story/root"));
    }

    @Test
    void recordAdvancementShouldCreatePlayerIfNeeded() {
        ProgressService service = new ProgressService();
        UUID playerId = UUID.randomUUID();

        service.recordAdvancement(playerId, "Charlie", "minecraft:story/mine_stone");

        Map<UUID, PlayerProgress> snapshot = service.snapshot();
        Assertions.assertEquals(1, service.trackedPlayers());
        Assertions.assertEquals("Charlie", snapshot.get(playerId).name());
        Assertions.assertTrue(snapshot.get(playerId).hasAdvancement("minecraft:story/mine_stone"));
    }

    @Test
    void recordAdvancementShouldNotDuplicateExistingPlayer() {
        ProgressService service = new ProgressService();
        UUID playerId = UUID.randomUUID();

        PlayerProgress existing = new PlayerProgress("Delta");
        existing.setMinedCount("iron_ore", 10);
        service.loadFromDatabase(Map.of(playerId, existing));

        service.recordAdvancement(playerId, "Delta", "minecraft:nether/find_fortress");

        Map<UUID, PlayerProgress> snapshot = service.snapshot();
        Assertions.assertEquals(1, service.trackedPlayers());
        Assertions.assertEquals(10, snapshot.get(playerId).mined().get("iron_ore"));
        Assertions.assertTrue(snapshot.get(playerId).hasAdvancement("minecraft:nether/find_fortress"));
    }

    @Test
    void trackedBlockKeysShouldReturnSortedList() {
        var keys = ProgressService.trackedBlockKeys();
        Assertions.assertFalse(keys.isEmpty());

        // Verify sorted
        for (int i = 1; i < keys.size(); i++) {
            Assertions.assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0,
                "trackedBlockKeys should be sorted: " + keys.get(i - 1) + " vs " + keys.get(i));
        }
    }
}
