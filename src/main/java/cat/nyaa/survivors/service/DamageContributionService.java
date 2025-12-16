package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player damage contributions to VRS mobs for shared XP rewards.
 * Data is transient (in-memory only) and cleaned up on mob death or removal.
 * <p>
 * This service works independently of the proximity-based XP sharing system.
 * Players can receive rewards from both systems if they qualify for both.
 */
public class DamageContributionService {

    private final KedamaSurvivorsPlugin plugin;

    // Main tracking structure: mobUUID -> (playerUUID -> totalDamage)
    private final Map<UUID, Map<UUID, Double>> mobDamageMap = new ConcurrentHashMap<>();

    public DamageContributionService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Records damage dealt by a player to a mob.
     * Damage is accumulated per player per mob.
     *
     * @param mobId    The UUID of the damaged mob
     * @param playerId The UUID of the player who dealt damage
     * @param damage   The damage amount (final damage after armor)
     */
    public void recordDamage(UUID mobId, UUID playerId, double damage) {
        if (damage <= 0) return;

        mobDamageMap
                .computeIfAbsent(mobId, k -> new ConcurrentHashMap<>())
                .merge(playerId, damage, Double::sum);
    }

    /**
     * Gets all damage contributors for a mob.
     *
     * @param mobId The UUID of the mob
     * @return Map of playerUUID -> totalDamage, or empty map if none
     */
    public Map<UUID, Double> getContributors(UUID mobId) {
        return mobDamageMap.getOrDefault(mobId, Collections.emptyMap());
    }

    /**
     * Clears damage tracking for a specific mob.
     * Called when mob dies or is removed from the world.
     *
     * @param mobId The UUID of the mob to clear
     */
    public void clearMob(UUID mobId) {
        mobDamageMap.remove(mobId);
    }

    /**
     * Clears all tracked data. Called on plugin disable.
     */
    public void clearAll() {
        mobDamageMap.clear();
    }

    /**
     * Gets the number of mobs currently being tracked.
     * Useful for debugging/performance monitoring.
     *
     * @return The number of mobs with tracked damage
     */
    public int getTrackedMobCount() {
        return mobDamageMap.size();
    }
}
