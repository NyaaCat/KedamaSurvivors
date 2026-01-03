package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.CombatWorldConfig;
import cat.nyaa.survivors.config.ConfigService.SpawnPointConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks player and mob counts near each spawn point.
 * Uses distributed tick checking (one spawn point per tick) for minimal performance impact.
 * Updates rankings asynchronously every second.
 */
public class SpawnLoadTracker {

    private static final String VRS_MOB_TAG = "vrs_mob";
    private static final double DEFAULT_TRACKING_RADIUS = 50.0;

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final AdminConfigService adminConfig;

    // Flattened list of all spawn points across all worlds
    private final List<SpawnPointEntry> allSpawnPoints = new ArrayList<>();
    private int currentIndex = 0;

    // Load data for each spawn point
    private final Map<SpawnPointEntry, SpawnPointLoad> loadData = new ConcurrentHashMap<>();

    // Cached rankings (updated asynchronously every second)
    private volatile List<SpawnPointLoad> globalRanking = new ArrayList<>();
    private final Map<String, List<SpawnPointLoad>> worldRankings = new ConcurrentHashMap<>();

    // Tasks
    private BukkitTask tickTask;
    private BukkitTask rankingTask;

    public SpawnLoadTracker(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.adminConfig = plugin.getAdminConfigService();
    }

    /**
     * Initializes the tracker and starts background tasks.
     */
    public void initialize() {
        rebuildSpawnPointList();
        startTicking();
        startRankingUpdater();
        plugin.getLogger().info("SpawnLoadTracker initialized with " + allSpawnPoints.size() + " spawn points");
    }

    /**
     * Rebuilds the flattened spawn point list from config.
     * Should be called after world configuration changes.
     */
    public void rebuildSpawnPointList() {
        allSpawnPoints.clear();
        loadData.clear();

        List<CombatWorldConfig> worlds = adminConfig.getCombatWorlds();
        for (CombatWorldConfig worldConfig : worlds) {
            if (!worldConfig.enabled) continue;

            for (SpawnPointConfig sp : worldConfig.spawnPoints) {
                double radius = sp.trackingRadius > 0 ? sp.trackingRadius : DEFAULT_TRACKING_RADIUS;
                allSpawnPoints.add(new SpawnPointEntry(worldConfig.name, sp, radius));
            }
        }

        currentIndex = 0;
    }

    /**
     * Starts the per-tick spawn point checking.
     */
    private void startTicking() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        // Run every tick on main thread
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, 1L, 1L);
    }

    /**
     * Starts the async ranking updater (every second).
     */
    private void startRankingUpdater() {
        if (rankingTask != null) {
            rankingTask.cancel();
        }

        // Run async every 20 ticks (1 second)
        rankingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateRankings, 20L, 20L);
    }

    /**
     * Called every tick to check one spawn point.
     */
    private void tickUpdate() {
        if (allSpawnPoints.isEmpty()) return;

        SpawnPointEntry entry = allSpawnPoints.get(currentIndex);
        currentIndex = (currentIndex + 1) % allSpawnPoints.size();

        World world = Bukkit.getWorld(entry.worldName());
        if (world == null) return;

        Location loc = entry.toLocation(world);
        int players = countPlayersNear(loc, entry.trackingRadius());
        int mobs = countVrsMobsNear(loc, entry.trackingRadius());

        loadData.put(entry, new SpawnPointLoad(entry, players, mobs));
    }

    /**
     * Updates global and per-world rankings asynchronously.
     */
    private void updateRankings() {
        // 1. Calculate global ranking
        List<SpawnPointLoad> global = new ArrayList<>(loadData.values());
        global.sort(Comparator.comparingInt(SpawnPointLoad::totalLoad));
        globalRanking = global;

        // 2. Calculate per-world rankings
        Map<String, List<SpawnPointLoad>> perWorld = new HashMap<>();
        for (SpawnPointLoad load : loadData.values()) {
            perWorld.computeIfAbsent(load.entry().worldName(), k -> new ArrayList<>())
                    .add(load);
        }
        for (Map.Entry<String, List<SpawnPointLoad>> e : perWorld.entrySet()) {
            e.getValue().sort(Comparator.comparingInt(SpawnPointLoad::totalLoad));
            worldRankings.put(e.getKey(), e.getValue());
        }
    }

    /**
     * Counts players near a location.
     */
    private int countPlayersNear(Location center, double radius) {
        if (center.getWorld() == null) return 0;

        return (int) center.getWorld().getNearbyEntities(center, radius, radius, radius)
                .stream()
                .filter(e -> e instanceof Player)
                .count();
    }

    /**
     * Counts VRS mobs (entities with vrs_mob tag) near a location.
     */
    private int countVrsMobsNear(Location center, double radius) {
        if (center.getWorld() == null) return 0;

        return (int) center.getWorld().getNearbyEntities(center, radius, radius, radius)
                .stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> e.getScoreboardTags().contains(VRS_MOB_TAG))
                .count();
    }

    /**
     * Selects the least loaded spawn point from a specific world.
     * Picks randomly from the top 3 least loaded.
     *
     * @param worldName the world name
     * @return a spawn point config, or null if none available
     */
    public SpawnPointConfig selectLeastLoaded(String worldName) {
        List<SpawnPointLoad> ranking = worldRankings.get(worldName);
        if (ranking == null || ranking.isEmpty()) {
            // Fallback to returning the first spawn point from config if no data yet
            return getFirstSpawnPointForWorld(worldName);
        }

        int topN = Math.min(3, ranking.size());
        return ranking.get(ThreadLocalRandom.current().nextInt(topN)).entry().spawnPoint();
    }

    /**
     * Selects the globally least loaded spawn point across all worlds.
     * Picks randomly from the top 3 least loaded.
     *
     * @return a spawn point entry with world info, or null if none available
     */
    public SpawnPointEntry selectGlobalLeastLoaded() {
        if (globalRanking.isEmpty()) {
            // Fallback to first spawn point from first enabled world
            if (!allSpawnPoints.isEmpty()) {
                return allSpawnPoints.get(0);
            }
            return null;
        }

        int topN = Math.min(3, globalRanking.size());
        return globalRanking.get(ThreadLocalRandom.current().nextInt(topN)).entry();
    }

    /**
     * Gets the first spawn point for a world (fallback when no tracking data).
     */
    private SpawnPointConfig getFirstSpawnPointForWorld(String worldName) {
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(worldName);
        if (worldOpt.isPresent() && !worldOpt.get().spawnPoints.isEmpty()) {
            return worldOpt.get().spawnPoints.get(0);
        }
        return null;
    }

    /**
     * Gets the current load for a specific spawn point.
     */
    public Optional<SpawnPointLoad> getLoad(String worldName, int spawnIndex) {
        for (Map.Entry<SpawnPointEntry, SpawnPointLoad> entry : loadData.entrySet()) {
            if (entry.getKey().worldName().equals(worldName)) {
                // Find by index
                List<SpawnPointLoad> worldLoads = worldRankings.get(worldName);
                if (worldLoads != null && spawnIndex < worldLoads.size()) {
                    return Optional.of(worldLoads.get(spawnIndex));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the global ranking list (read-only).
     */
    public List<SpawnPointLoad> getGlobalRanking() {
        return Collections.unmodifiableList(globalRanking);
    }

    /**
     * Gets the ranking for a specific world (read-only).
     */
    public List<SpawnPointLoad> getWorldRanking(String worldName) {
        List<SpawnPointLoad> ranking = worldRankings.get(worldName);
        return ranking != null ? Collections.unmodifiableList(ranking) : Collections.emptyList();
    }

    /**
     * Shuts down the tracker and cancels all tasks.
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (rankingTask != null) {
            rankingTask.cancel();
            rankingTask = null;
        }
        allSpawnPoints.clear();
        loadData.clear();
        globalRanking = new ArrayList<>();
        worldRankings.clear();
    }

    // ==================== Data Classes ====================

    /**
     * Represents a spawn point with its world context.
     */
    public record SpawnPointEntry(String worldName, SpawnPointConfig spawnPoint, double trackingRadius) {
        public Location toLocation(World world) {
            return new Location(world, spawnPoint.x, spawnPoint.y, spawnPoint.z,
                    spawnPoint.yaw != null ? spawnPoint.yaw : 0f,
                    spawnPoint.pitch != null ? spawnPoint.pitch : 0f);
        }
    }

    /**
     * Represents the load data for a spawn point.
     */
    public record SpawnPointLoad(SpawnPointEntry entry, int players, int mobs) {
        public int totalLoad() {
            return players + mobs;
        }
    }
}
