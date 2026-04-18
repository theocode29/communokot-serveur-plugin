package io.communokot.progress.service;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProgressServiceTest {

    @Test
    void incrementStoneBreakShouldCreateAndIncrementPlayerProgress() {
        ProgressService service = new ProgressService();
        UUID playerId = UUID.randomUUID();

        long first = service.incrementStoneBreak(playerId);
        long second = service.incrementStoneBreak(playerId);

        Assertions.assertEquals(1L, first);
        Assertions.assertEquals(2L, second);

        Map<UUID, Long> snapshot = service.snapshotStoneBroken();
        Assertions.assertEquals(2L, snapshot.get(playerId));
        Assertions.assertEquals(1, service.trackedPlayers());
    }

    @Test
    void loadInitialProgressShouldHydrateSnapshot() {
        ProgressService service = new ProgressService();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        service.loadInitialProgress(Map.of(playerA, 12L, playerB, 4L));

        Map<UUID, Long> snapshot = service.snapshotStoneBroken();
        Assertions.assertEquals(12L, snapshot.get(playerA));
        Assertions.assertEquals(4L, snapshot.get(playerB));
        Assertions.assertEquals(2, service.trackedPlayers());
    }
}
