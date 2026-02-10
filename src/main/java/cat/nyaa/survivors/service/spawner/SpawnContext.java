package cat.nyaa.survivors.service.spawner;

import cat.nyaa.survivors.util.LineOfSightChecker;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Immutable snapshot of player state for async spawn planning.
 * Collected on main thread, used in async phase.
 */
public record SpawnContext(
        UUID playerId,
        UUID runId,
        String worldName,
        Location playerLocation,
        int playerLevel,
        double averageTeamLevel,
        int nearbyPlayerCount,
        int nearbyMobCount,
        long runDurationSeconds,
        int minEnemyLevel,
        LineOfSightChecker losChecker
) {
    /**
     * Creates a defensive copy with cloned location.
     */
    public SpawnContext {
        // Clone location to ensure thread safety
        playerLocation = playerLocation.clone();
    }
}
