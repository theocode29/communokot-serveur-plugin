package io.communokot.progress.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds all tracked stats for a single player. Thread-safe for concurrent reads/writes
 * from event handlers and the snapshot scheduler.
 */
public final class PlayerProgress {
    private volatile String name;
    private final AtomicLong distanceWalkedCm;
    private final AtomicLong playTicks;
    private final ConcurrentHashMap<String, Long> mined;
    private final ConcurrentHashMap<String, Boolean> advancements;

    public PlayerProgress(String name) {
        this.name = name;
        this.distanceWalkedCm = new AtomicLong(0L);
        this.playTicks = new AtomicLong(0L);
        this.mined = new ConcurrentHashMap<>();
        this.advancements = new ConcurrentHashMap<>();
    }

    // ── Identity ────────────────────────────────────────────────────────

    public String name() {
        return name;
    }

    public void updateName(String name) {
        this.name = name;
    }

    // ── Distance ────────────────────────────────────────────────────────

    public long distanceWalkedCm() {
        return distanceWalkedCm.get();
    }

    public void setDistanceWalkedCm(long value) {
        distanceWalkedCm.set(Math.max(0L, value));
    }

    // ── Play time ───────────────────────────────────────────────────────

    public long playTicks() {
        return playTicks.get();
    }

    public void setPlayTicks(long value) {
        playTicks.set(Math.max(0L, value));
    }

    // ── Mined blocks ────────────────────────────────────────────────────

    public Map<String, Long> mined() {
        return Collections.unmodifiableMap(mined);
    }

    public void setMinedCount(String blockKey, long count) {
        mined.put(blockKey, Math.max(0L, count));
    }

    // ── Advancements ────────────────────────────────────────────────────

    public Set<String> completedAdvancements() {
        return Collections.unmodifiableSet(advancements.keySet());
    }

    public void markAdvancement(String advancementKey) {
        advancements.put(advancementKey, Boolean.TRUE);
    }

    public boolean hasAdvancement(String advancementKey) {
        return advancements.containsKey(advancementKey);
    }
}
