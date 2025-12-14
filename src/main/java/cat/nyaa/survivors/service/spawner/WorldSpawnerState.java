package cat.nyaa.survivors.service.spawner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-world spawner state tracking.
 * Thread-safe for concurrent access.
 */
public class WorldSpawnerState {

    private final String worldName;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger activeMobCount = new AtomicInteger(0);

    public WorldSpawnerState(String worldName) {
        this.worldName = worldName;
    }

    public String getWorldName() {
        return worldName;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void setPaused(boolean paused) {
        this.paused.set(paused);
    }

    public int getActiveMobCount() {
        return activeMobCount.get();
    }

    public void setActiveMobCount(int count) {
        activeMobCount.set(count);
    }

    public void incrementMobCount() {
        activeMobCount.incrementAndGet();
    }

    public void decrementMobCount() {
        activeMobCount.decrementAndGet();
    }
}
