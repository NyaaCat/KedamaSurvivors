package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages combat worlds and spawn location sampling.
 */
public class WorldService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;

    // Enabled worlds cache
    private final Set<String> enabledWorlds = new HashSet<>();

    public WorldService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        refreshEnabledWorlds();
    }

    /**
     * Refreshes the list of enabled worlds from config.
     */
    public void refreshEnabledWorlds() {
        enabledWorlds.clear();
        for (var worldConfig : config.getCombatWorlds()) {
            if (worldConfig.enabled) {
                enabledWorlds.add(worldConfig.name);
            }
        }
        plugin.getLogger().info("Enabled combat worlds: " + enabledWorlds);
    }

    /**
     * Checks if a world is enabled for combat.
     */
    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains(worldName);
    }

    /**
     * Enables a world for combat.
     */
    public void enableWorld(String worldName) {
        enabledWorlds.add(worldName);
    }

    /**
     * Disables a world for combat.
     */
    public void disableWorld(String worldName) {
        enabledWorlds.remove(worldName);
    }

    /**
     * Gets all enabled world names.
     */
    public Set<String> getEnabledWorlds() {
        return Collections.unmodifiableSet(enabledWorlds);
    }

    /**
     * Selects a random combat world based on weights.
     * @return the selected world config, or null if none available
     */
    public ConfigService.CombatWorldConfig selectRandomWorld() {
        List<ConfigService.CombatWorldConfig> available = new ArrayList<>();
        double totalWeight = 0;

        for (var worldConfig : config.getCombatWorlds()) {
            if (worldConfig.enabled && enabledWorlds.contains(worldConfig.name)) {
                World world = Bukkit.getWorld(worldConfig.name);
                if (world != null) {
                    available.add(worldConfig);
                    totalWeight += worldConfig.weight;
                }
            }
        }

        if (available.isEmpty() || totalWeight <= 0) {
            return null;
        }

        // Weighted random selection
        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;

        for (var worldConfig : available) {
            cumulative += worldConfig.weight;
            if (random < cumulative) {
                return worldConfig;
            }
        }

        // Fallback to last (shouldn't happen)
        return available.get(available.size() - 1);
    }

    /**
     * Samples spawn points within the world bounds.
     * @param worldConfig the world configuration
     * @param count number of spawn points to sample
     * @return list of valid spawn locations
     */
    public List<Location> sampleSpawnPoints(ConfigService.CombatWorldConfig worldConfig, int count) {
        World world = Bukkit.getWorld(worldConfig.name);
        if (world == null) {
            return Collections.emptyList();
        }

        List<Location> spawnPoints = new ArrayList<>();
        int maxAttempts = config.getMaxSampleAttempts();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count && spawnPoints.size() < count; i++) {
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                // Random X,Z within bounds
                double x = random.nextDouble(worldConfig.minX, worldConfig.maxX);
                double z = random.nextDouble(worldConfig.minZ, worldConfig.maxZ);

                // Find safe Y
                Location loc = findSafeLocation(world, x, z);
                if (loc != null) {
                    spawnPoints.add(loc);
                    break;
                }
            }
        }

        return spawnPoints;
    }

    /**
     * Samples a single spawn point near a reference location.
     * @param reference the reference location
     * @param minDistance minimum distance from reference
     * @param maxDistance maximum distance from reference
     * @return safe spawn location, or null if none found
     */
    public Location sampleSpawnNear(Location reference, double minDistance, double maxDistance) {
        World world = reference.getWorld();
        if (world == null) return null;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int maxAttempts = config.getMaxSampleAttempts();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Random angle and distance
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble(minDistance, maxDistance);

            double x = reference.getX() + Math.cos(angle) * distance;
            double z = reference.getZ() + Math.sin(angle) * distance;

            // Check if within world bounds
            if (!isWithinBounds(world.getName(), x, z)) {
                continue;
            }

            Location loc = findSafeLocation(world, x, z);
            if (loc != null) {
                return loc;
            }
        }

        return null;
    }

    /**
     * Checks if coordinates are within the configured bounds for a world.
     */
    public boolean isWithinBounds(String worldName, double x, double z) {
        for (var worldConfig : config.getCombatWorlds()) {
            if (worldConfig.name.equals(worldName)) {
                return x >= worldConfig.minX && x <= worldConfig.maxX
                        && z >= worldConfig.minZ && z <= worldConfig.maxZ;
            }
        }
        return false;
    }

    /**
     * Finds a safe spawn location at the given X,Z coordinates.
     * @return safe location or null if not found
     */
    public Location findSafeLocation(World world, double x, double z) {
        // Start from top and work down
        int startY = world.getMaxHeight() - 1;
        int minY = world.getMinHeight();

        // Find the highest solid block
        int safeY = -1;
        for (int y = startY; y > minY; y--) {
            Block block = world.getBlockAt((int) x, y, (int) z);
            Block above = world.getBlockAt((int) x, y + 1, (int) z);
            Block twoAbove = world.getBlockAt((int) x, y + 2, (int) z);

            if (isSolidGround(block) && isPassable(above) && isPassable(twoAbove)) {
                safeY = y + 1;
                break;
            }
        }

        if (safeY < 0) {
            return null;
        }

        // Center on block
        return new Location(world, x, safeY, z);
    }

    /**
     * Checks if a block is solid ground suitable for standing.
     */
    private boolean isSolidGround(Block block) {
        return block.getType().isSolid() && !block.isLiquid();
    }

    /**
     * Checks if a block is passable (air, flowers, grass, etc).
     */
    private boolean isPassable(Block block) {
        return block.isPassable() && !block.isLiquid();
    }

    /**
     * Gets the world configuration by name.
     */
    public Optional<ConfigService.CombatWorldConfig> getWorldConfig(String worldName) {
        for (var worldConfig : config.getCombatWorlds()) {
            if (worldConfig.name.equals(worldName)) {
                return Optional.of(worldConfig);
            }
        }
        return Optional.empty();
    }

    /**
     * Validates that a world exists and is configured.
     */
    public boolean isValidCombatWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        return getWorldConfig(worldName).isPresent();
    }
}
