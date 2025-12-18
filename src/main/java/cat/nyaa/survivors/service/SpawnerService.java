package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.service.spawner.SpawnContext;
import cat.nyaa.survivors.service.spawner.SpawnPlan;
import cat.nyaa.survivors.service.spawner.WorldSpawnerState;
import cat.nyaa.survivors.util.TemplateEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Manages enemy spawning in active runs using a 3-phase architecture:
 * - Phase A (Main Thread): Collect spawn contexts from active runs
 * - Phase B (Async): Calculate spawn plans (positions, archetypes, levels)
 * - Phase C (Main Thread): Execute spawn commands via template engine
 */
public class SpawnerService {

    private static final String VRS_MOB_TAG = "vrs_mob";
    private static final String VRS_LEVEL_TAG_PREFIX = "vrs_lvl_";

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final TemplateEngine templateEngine;

    // Per-world spawner state
    private final Map<String, WorldSpawnerState> worldStates = new ConcurrentHashMap<>();

    // Cache for nearby mob counts (cleared each spawn tick)
    private final Map<MobCountCacheKey, Integer> mobCountCache = new HashMap<>();

    // Async executor for Phase B
    private final ExecutorService asyncExecutor;

    /**
     * Cache key for mob count queries, based on chunk coordinates.
     */
    private record MobCountCacheKey(String worldName, int chunkX, int chunkZ) {}

    // Main loop task ID
    private int taskId = -1;

    // Random for spawn calculations
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public SpawnerService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.templateEngine = plugin.getTemplateEngine();

        // Single-threaded executor for spawn planning
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VRS-SpawnPlanner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the spawn loop.
     */
    public void start() {
        if (taskId != -1) {
            return; // Already running
        }

        if (!config.isSpawningEnabled()) {
            plugin.getLogger().info("Spawning is disabled in config");
            return;
        }

        int interval = config.getSpawnTickInterval();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::executeSpawnTick, interval, interval).getTaskId();
        plugin.getLogger().info("Spawner service started with interval: " + interval + " ticks");
    }

    /**
     * Stops the spawn loop.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        plugin.getLogger().info("Spawner service stopped");
    }

    /**
     * Pauses spawning for a specific world.
     */
    public void pause(String worldName) {
        getOrCreateWorldState(worldName).setPaused(true);
    }

    /**
     * Resumes spawning for a specific world.
     */
    public void resume(String worldName) {
        getOrCreateWorldState(worldName).setPaused(false);
    }

    /**
     * Checks if spawning is paused for a world.
     */
    public boolean isPaused(String worldName) {
        WorldSpawnerState worldState = worldStates.get(worldName);
        return worldState != null && worldState.isPaused();
    }

    /**
     * Gets the count of active VRS mobs in a world.
     */
    public int getActiveMobCount(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return 0;

        return (int) world.getEntities().stream()
                .filter(e -> e.getScoreboardTags().contains(VRS_MOB_TAG))
                .count();
    }

    /**
     * Gets the count of VRS mobs near a location.
     * Uses chunk-based caching to avoid repeated expensive getNearbyEntities calls.
     */
    public int getMobCountNear(Location location, double radius) {
        if (location.getWorld() == null) return 0;

        // Create cache key based on chunk coordinates
        String worldName = location.getWorld().getName();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        MobCountCacheKey key = new MobCountCacheKey(worldName, chunkX, chunkZ);

        // Check cache first
        Integer cached = mobCountCache.get(key);
        if (cached != null) {
            return cached;
        }

        // Cache miss - perform actual query
        int count = (int) location.getWorld()
                .getNearbyEntities(location, radius, radius, radius)
                .stream()
                .filter(e -> e.getScoreboardTags().contains(VRS_MOB_TAG))
                .count();

        // Store in cache
        mobCountCache.put(key, count);
        return count;
    }

    /**
     * Main spawn tick - coordinates the 3-phase spawn loop.
     */
    private void executeSpawnTick() {
        if (!config.isSpawningEnabled()) return;

        // Phase A: Collect spawn contexts on main thread
        List<SpawnContext> contexts = collectSpawnContexts();

        if (contexts.isEmpty()) return;

        // Phase B: Async spawn planning
        asyncExecutor.submit(() -> {
            try {
                List<SpawnPlan> plans = planSpawns(contexts);

                if (!plans.isEmpty()) {
                    // Phase C: Execute on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> executeSpawnPlans(plans));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during spawn planning", e);
            }
        });
    }

    /**
     * Phase A: Collect spawn contexts from all active runs.
     * Must run on main thread.
     */
    private List<SpawnContext> collectSpawnContexts() {
        // Clear mob count cache for this tick
        mobCountCache.clear();

        List<SpawnContext> contexts = new ArrayList<>();

        for (RunState run : state.getActiveRuns()) {
            String worldName = run.getWorldName();

            // Skip paused worlds
            if (isPaused(worldName)) continue;

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            // Collect context for each participant
            for (UUID playerId : run.getParticipants()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) continue;
                if (!player.getWorld().getName().equals(worldName)) continue;

                Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
                if (playerStateOpt.isEmpty()) continue;

                PlayerState playerState = playerStateOpt.get();
                if (playerState.getMode() != PlayerMode.IN_RUN) continue;

                // Calculate nearby mob count
                int nearbyMobs = getMobCountNear(player.getLocation(), config.getMobCountRadius());

                // Calculate average team level
                double avgLevel = calculateAverageLevel(run, player.getLocation());

                // Count nearby players
                int nearbyPlayers = countNearbyPlayers(player.getLocation(), config.getLevelSamplingRadius(), run);

                SpawnContext context = new SpawnContext(
                        playerId,
                        run.getRunId(),
                        worldName,
                        player.getLocation(),
                        playerState.getRunLevel(),  // Use runLevel instead of equipment-based level
                        avgLevel,
                        nearbyPlayers,
                        nearbyMobs,
                        run.getElapsedSeconds()
                );

                contexts.add(context);
            }
        }

        return contexts;
    }

    /**
     * Phase B: Plan spawns based on collected contexts.
     * Runs on async thread.
     */
    private List<SpawnPlan> planSpawns(List<SpawnContext> contexts) {
        List<SpawnPlan> plans = new ArrayList<>();

        for (SpawnContext ctx : contexts) {
            int targetMobs = config.getTargetMobsPerPlayer();
            int toSpawn = Math.min(
                    targetMobs - ctx.nearbyMobCount(),
                    config.getMaxSpawnsPerPlayerPerTick()
            );

            if (toSpawn <= 0) continue;

            // Calculate enemy level FIRST (needed for archetype selection)
            int enemyLevel = calculateEnemyLevel(ctx);

            for (int i = 0; i < toSpawn; i++) {
                // Select archetype based on current level and world (level + world gated selection)
                EnemyArchetypeConfig archetype = selectArchetype(enemyLevel, ctx.worldName());
                if (archetype == null) {
                    // No archetypes available at this level/world - stop trying
                    break;
                }

                // Sample spawn location
                Location spawnLoc = sampleSpawnLocation(ctx.playerLocation());
                if (spawnLoc == null) continue;

                plans.add(new SpawnPlan(
                        ctx.playerId(),
                        ctx.worldName(),
                        spawnLoc,
                        archetype,
                        enemyLevel
                ));
            }
        }

        return plans;
    }

    /**
     * Phase C: Execute spawn plans on main thread.
     * Must run on main thread.
     */
    private void executeSpawnPlans(List<SpawnPlan> plans) {
        int commandsThisTick = 0;
        int spawnsThisTick = 0;

        int maxCommands = config.getMaxCommandsPerTick();
        int maxSpawns = config.getMaxSpawnsPerTick();

        for (SpawnPlan plan : plans) {
            if (commandsThisTick >= maxCommands) break;
            if (spawnsThisTick >= maxSpawns) break;

            // Execute spawn commands for this archetype
            for (String cmdTemplate : plan.archetype().spawnCommands) {
                if (commandsThisTick >= maxCommands) break;

                Location loc = plan.spawnLocation();
                Map<String, Object> context = new HashMap<>();
                context.put("sx", loc.getBlockX());
                context.put("sy", loc.getBlockY());
                context.put("sz", loc.getBlockZ());
                context.put("runWorld", plan.worldName());
                context.put("enemyLevel", plan.enemyLevel());
                context.put("enemyType", plan.archetype().enemyType);
                context.put("archetypeId", plan.archetype().archetypeId);

                String cmd = templateEngine.expand(cmdTemplate, context);

                // Auto-wrap with world context if not already specified
                // This ensures summon commands execute in the correct world
                if (!cmd.toLowerCase().startsWith("execute in ")) {
                    cmd = "execute in " + plan.worldName() + " run " + cmd;
                }

                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    commandsThisTick++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to execute spawn command: " + cmd, e);
                }
            }

            spawnsThisTick++;
        }

        if (config.isVerbose() && spawnsThisTick > 0) {
            plugin.getLogger().info("Spawned " + spawnsThisTick + " entities using " + commandsThisTick + " commands");
        }
    }

    /**
     * Calculates the enemy level based on context.
     */
    private int calculateEnemyLevel(SpawnContext ctx) {
        double level = ctx.averageTeamLevel() * config.getAvgLevelMultiplier()
                + ctx.nearbyPlayerCount() * config.getPlayerCountMultiplier()
                + config.getLevelOffset();

        // Time scaling
        if (config.isTimeScalingEnabled()) {
            long timeSteps = ctx.runDurationSeconds() / config.getTimeStepSeconds();
            level += timeSteps * config.getLevelPerTimeStep();
        }

        // Clamp to configured bounds
        int result = (int) Math.round(level);
        return Math.max(config.getMinEnemyLevel(), Math.min(config.getMaxEnemyLevel(), result));
    }

    /**
     * Selects a random archetype using weighted selection.
     * Only includes archetypes where minSpawnLevel <= currentLevel and the world is allowed.
     *
     * @param currentLevel The calculated enemy level for this spawn
     * @param worldName The world name where the mob will spawn
     * @return Selected archetype, or null if no archetypes available
     */
    private EnemyArchetypeConfig selectArchetype(int currentLevel, String worldName) {
        Map<String, EnemyArchetypeConfig> allArchetypes = config.getEnemyArchetypes();
        if (allArchetypes.isEmpty()) return null;

        // Filter archetypes by minSpawnLevel and allowed worlds
        List<EnemyArchetypeConfig> eligible = allArchetypes.values().stream()
                .filter(a -> a.minSpawnLevel <= currentLevel)
                .filter(a -> a.isAllowedInWorld(worldName))
                .toList();

        if (eligible.isEmpty()) {
            if (config.isVerbose()) {
                plugin.getLogger().fine("No archetypes available at level " + currentLevel + " in world " + worldName);
            }
            return null;
        }

        // Calculate total weight from eligible archetypes only
        double totalWeight = eligible.stream()
                .mapToDouble(a -> a.weight)
                .sum();

        if (totalWeight <= 0) return null;

        // Weighted random selection from filtered pool
        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;

        for (EnemyArchetypeConfig archetype : eligible) {
            cumulative += archetype.weight;
            if (random < cumulative) {
                return archetype;
            }
        }

        // Fallback to first eligible archetype
        return eligible.get(0);
    }

    /**
     * Samples a spawn location near the player.
     */
    private Location sampleSpawnLocation(Location playerLoc) {
        World world = playerLoc.getWorld();
        if (world == null) return null;

        double minDist = config.getMinSpawnDistance();
        double maxDist = config.getMaxSpawnDistance();
        int maxAttempts = config.getMaxSampleAttempts();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Random angle and distance
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            // Find safe Y
            int y = world.getHighestBlockYAt((int) x, (int) z);
            Location candidate = new Location(world, x, y + 1, z);

            if (isSafeSpawnLocation(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Checks if a location is safe for spawning.
     */
    private boolean isSafeSpawnLocation(Location loc) {
        if (loc.getWorld() == null) return false;

        // Check that feet and head are passable
        if (!loc.getBlock().isPassable()) return false;
        if (!loc.clone().add(0, 1, 0).getBlock().isPassable()) return false;

        // Check that ground is solid
        if (!loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) return false;

        return true;
    }

    /**
     * Calculates average player run level in the run near a location.
     */
    private double calculateAverageLevel(RunState run, Location center) {
        double radius = config.getLevelSamplingRadius();
        int totalLevel = 0;
        int count = 0;

        for (UUID playerId : run.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            // Skip players in different worlds (e.g., dead players teleported to lobby)
            Location playerLoc = player.getLocation();
            if (!playerLoc.getWorld().equals(center.getWorld())) continue;

            if (playerLoc.distanceSquared(center) <= radius * radius) {
                Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
                if (playerStateOpt.isPresent()) {
                    totalLevel += playerStateOpt.get().getRunLevel();  // Use runLevel
                    count++;
                }
            }
        }

        // Default to 1.0 if no players found (runLevel starts at 1)
        return count > 0 ? (double) totalLevel / count : 1.0;
    }

    /**
     * Counts nearby players in the same run.
     */
    private int countNearbyPlayers(Location center, double radius, RunState run) {
        int count = 0;

        for (UUID playerId : run.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            // Skip players in different worlds (e.g., dead players teleported to lobby)
            Location playerLoc = player.getLocation();
            if (!playerLoc.getWorld().equals(center.getWorld())) continue;

            if (playerLoc.distanceSquared(center) <= radius * radius) {
                count++;
            }
        }

        return count;
    }

    /**
     * Parses enemy level from entity tags.
     */
    public int parseEnemyLevel(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(VRS_LEVEL_TAG_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(VRS_LEVEL_TAG_PREFIX.length()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 1; // Default level
    }

    /**
     * Checks if an entity is a VRS mob.
     */
    public boolean isVrsMob(Entity entity) {
        return entity.getScoreboardTags().contains(VRS_MOB_TAG);
    }

    private WorldSpawnerState getOrCreateWorldState(String worldName) {
        return worldStates.computeIfAbsent(worldName, WorldSpawnerState::new);
    }
}
