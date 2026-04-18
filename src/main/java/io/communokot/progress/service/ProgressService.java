package io.communokot.progress.service;

import io.communokot.progress.model.PlayerProgress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressService {
    private final ConcurrentHashMap<UUID, PlayerProgress> progressByPlayer = new ConcurrentHashMap<>();

    public void loadInitialProgress(Map<UUID, Long> initialProgress) {
        long now = Instant.now().toEpochMilli();
        for (Map.Entry<UUID, Long> entry : initialProgress.entrySet()) {
            progressByPlayer.put(entry.getKey(), new PlayerProgress(entry.getValue(), now));
        }
    }

    public long incrementStoneBreak(UUID playerId) {
        PlayerProgress progress = progressByPlayer.computeIfAbsent(
            playerId,
            ignored -> new PlayerProgress(0L, Instant.now().toEpochMilli())
        );
        return progress.incrementStoneBroken();
    }

    public Map<UUID, Long> snapshotStoneBroken() {
        Map<UUID, Long> snapshot = new HashMap<>(progressByPlayer.size());
        for (Map.Entry<UUID, PlayerProgress> entry : progressByPlayer.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().stoneBroken());
        }
        return snapshot;
    }

    public int trackedPlayers() {
        return progressByPlayer.size();
    }
}
