package cat.nyaa.survivors.service.spawner;

import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
import org.bukkit.Location;

import java.util.UUID;

/**
 * A planned spawn to execute on the main thread.
 * Created during async planning phase.
 */
public record SpawnPlan(
        UUID targetPlayerId,
        String worldName,
        Location spawnLocation,
        EnemyArchetypeConfig archetype,
        int enemyLevel
) {
    /**
     * Creates a defensive copy with cloned location.
     */
    public SpawnPlan {
        // Clone location to ensure thread safety
        spawnLocation = spawnLocation.clone();
    }
}
