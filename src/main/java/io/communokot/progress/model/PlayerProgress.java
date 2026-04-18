package io.communokot.progress.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerProgress {
    private final AtomicLong stoneBroken;
    private final AtomicLong updatedAtEpochMillis;

    public PlayerProgress(long initialStoneBroken, long updatedAtEpochMillis) {
        this.stoneBroken = new AtomicLong(Math.max(0, initialStoneBroken));
        this.updatedAtEpochMillis = new AtomicLong(Math.max(0, updatedAtEpochMillis));
    }

    public long incrementStoneBroken() {
        updatedAtEpochMillis.set(Instant.now().toEpochMilli());
        return stoneBroken.incrementAndGet();
    }

    public long stoneBroken() {
        return stoneBroken.get();
    }

    public long updatedAtEpochMillis() {
        return updatedAtEpochMillis.get();
    }
}
