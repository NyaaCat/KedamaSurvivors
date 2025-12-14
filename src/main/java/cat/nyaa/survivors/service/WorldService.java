package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;
import java.util.concurrent.CompletableFuture;
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
     * Samples spawn points within the world bounds asynchronously.
     * @param worldConfig the world configuration
     * @param count number of spawn points to sample
     * @return CompletableFuture with list of valid spawn locations
     */
    public CompletableFuture<List<Location>> sampleSpawnPointsAsync(ConfigService.CombatWorldConfig worldConfig, int count) {
        World world = Bukkit.getWorld(worldConfig.name);
        if (world == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        int maxAttempts = config.getMaxSampleAttempts();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Generate all candidate coordinates upfront
        List<double[]> candidates = new ArrayList<>();
        for (int i = 0; i < count * maxAttempts; i++) {
            double x = random.nextDouble(worldConfig.minX, worldConfig.maxX);
            double z = random.nextDouble(worldConfig.minZ, worldConfig.maxZ);
            candidates.add(new double[]{x, z});
        }

        // Collect unique chunks that need loading
        Set<Long> chunksToLoad = new HashSet<>();
        for (double[] coord : candidates) {
            int chunkX = (int) Math.floor(coord[0]) >> 4;
            int chunkZ = (int) Math.floor(coord[1]) >> 4;
            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                chunksToLoad.add(chunkKey);
            }
        }

        // Load all chunks async, then find spawn points on main thread
        List<CompletableFuture<Void>> chunkFutures = new ArrayList<>();
        for (long chunkKey : chunksToLoad) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            chunkFutures.add(world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {}));
        }

        return CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .thenApplyAsync(v -> {
                    // Now all chunks are loaded, find spawn points on main thread
                    List<Location> spawnPoints = new ArrayList<>();
                    int candidateIndex = 0;

                    for (int i = 0; i < count && spawnPoints.size() < count && candidateIndex < candidates.size(); i++) {
                        for (int attempt = 0; attempt < maxAttempts && candidateIndex < candidates.size(); attempt++) {
                            double[] coord = candidates.get(candidateIndex++);
                            Location loc = findSafeLocation(world, coord[0], coord[1]);
                            if (loc != null) {
                                spawnPoints.add(loc);
                                break;
                            }
                        }
                    }

                    return spawnPoints;
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    /**
     * Samples spawn points within the world bounds (synchronous version).
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
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        // Ensure chunk is loaded before accessing blocks
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            world.getChunkAt(blockX >> 4, blockZ >> 4);
        }

        // Start from top and work down
        int startY = world.getMaxHeight() - 1;
        int minY = world.getMinHeight();

        // Find the highest solid block
        int safeY = -1;
        for (int y = startY; y > minY; y--) {
            Block block = world.getBlockAt(blockX, y, blockZ);
            Block above = world.getBlockAt(blockX, y + 1, blockZ);
            Block twoAbove = world.getBlockAt(blockX, y + 2, blockZ);

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
