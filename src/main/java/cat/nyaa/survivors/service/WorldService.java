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
    private final StateService state;

    // Enabled worlds cache
    private final Set<String> enabledWorlds = new HashSet<>();

    public WorldService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
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
     * Selects a combat world using load-aware distribution:
     * 1) Prefer worlds with zero in-run players.
     * 2) If all worlds have players, distribute by spawn-point capacity and player load.
     */
    public ConfigService.CombatWorldConfig selectRandomWorld() {
        List<ConfigService.CombatWorldConfig> available = new ArrayList<>();

        for (var worldConfig : config.getCombatWorlds()) {
            if (worldConfig.enabled && enabledWorlds.contains(worldConfig.name)) {
                World world = Bukkit.getWorld(worldConfig.name);
                if (world != null) {
                    available.add(worldConfig);
                }
            }
        }

        if (available.isEmpty()) {
            return null;
        }

        return selectDistributedWorld(available);
    }

    /**
     * Selects a random world within a stage group.
     * If the group has no valid world entries, falls back to global random selection.
     */
    public ConfigService.CombatWorldConfig selectRandomWorldForStage(ConfigService.StageGroupConfig stageGroup) {
        if (stageGroup == null || stageGroup.worldNames == null || stageGroup.worldNames.isEmpty()) {
            return selectRandomWorld();
        }

        List<ConfigService.CombatWorldConfig> available = new ArrayList<>();

        for (String worldName : stageGroup.worldNames) {
            Optional<ConfigService.CombatWorldConfig> configOpt = getWorldConfig(worldName);
            if (configOpt.isEmpty()) continue;

            ConfigService.CombatWorldConfig worldConfig = configOpt.get();
            if (!worldConfig.enabled || !enabledWorlds.contains(worldConfig.name)) continue;

            World world = Bukkit.getWorld(worldConfig.name);
            if (world == null) continue;

            available.add(worldConfig);
        }

        if (available.isEmpty()) {
            return selectRandomWorld();
        }

        return selectDistributedWorld(available);
    }

    private ConfigService.CombatWorldConfig selectDistributedWorld(List<ConfigService.CombatWorldConfig> worlds) {
        List<WorldLoadMetric> metrics = new ArrayList<>();
        for (ConfigService.CombatWorldConfig world : worlds) {
            int inRunPlayers = countPlayersInActiveRuns(world.name);
            int spawnPoints = Math.max(1, world.spawnPoints != null ? world.spawnPoints.size() : 0);
            double baseWeight = Math.max(0.0001, world.weight);
            metrics.add(new WorldLoadMetric(world, inRunPlayers, spawnPoints, baseWeight));
        }

        int selectedIndex = selectDistributedIndex(metrics, ThreadLocalRandom.current());
        if (selectedIndex < 0 || selectedIndex >= metrics.size()) {
            return worlds.get(ThreadLocalRandom.current().nextInt(worlds.size()));
        }
        return metrics.get(selectedIndex).world();
    }

    private int countPlayersInActiveRuns(String worldName) {
        if (state == null) {
            return 0;
        }

        int count = 0;
        for (var run : state.getActiveRuns()) {
            if (worldName.equalsIgnoreCase(run.getWorldName())) {
                count += run.getParticipantCount();
            }
        }
        return count;
    }

    static int selectDistributedIndex(List<WorldLoadMetric> metrics, Random random) {
        if (metrics == null || metrics.isEmpty()) {
            return -1;
        }

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < metrics.size(); i++) {
            if (metrics.get(i).inRunPlayers() == 0) {
                candidates.add(i);
            }
        }

        // If no empty world exists, use all worlds.
        if (candidates.isEmpty()) {
            for (int i = 0; i < metrics.size(); i++) {
                candidates.add(i);
            }
        }

        double totalScore = 0.0;
        double[] scores = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            WorldLoadMetric m = metrics.get(candidates.get(i));
            double capacityRatio = (double) m.spawnPointCount() / (double) (m.inRunPlayers() + 1);
            double score = Math.max(0.0001, capacityRatio * m.baseWeight());
            scores[i] = score;
            totalScore += score;
        }

        if (totalScore <= 0.0) {
            return candidates.get(0);
        }

        double roll = random.nextDouble() * totalScore;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += scores[i];
            if (roll <= cumulative) {
                return candidates.get(i);
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    static record WorldLoadMetric(
            ConfigService.CombatWorldConfig world,
            int inRunPlayers,
            int spawnPointCount,
            double baseWeight
    ) {}


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
