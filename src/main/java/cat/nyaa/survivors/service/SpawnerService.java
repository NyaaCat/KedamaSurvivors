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
import cat.nyaa.survivors.util.LineOfSightChecker;
import cat.nyaa.survivors.util.TemplateEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Collections;
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

    /**
     * Temporary per-player spawn suppression state.
     */
    private record PlayerSuppression(UUID runId, long expiresAtMillis) {}

    /**
     * Temporary area-based spawn suppression state.
     */
    private record SpawnSuppressionZone(UUID runId, String worldName, Location center, double radius, long expiresAtMillis) {}

    // Main loop task ID
    private int taskId = -1;

    // Random for spawn calculations
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    // Pending spawn plans that couldn't be executed due to per-tick limits
    private final List<SpawnPlan> pendingPlans = new ArrayList<>();

    // Temporary spawn suppression (battery charge complete safe window)
    private final Map<UUID, PlayerSuppression> suppressedPlayers = new ConcurrentHashMap<>();
    private final List<SpawnSuppressionZone> suppressionZones = Collections.synchronizedList(new ArrayList<>());

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

        suppressedPlayers.clear();
        synchronized (suppressionZones) {
            suppressionZones.clear();
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
     * Temporarily suppresses spawning for specific players in a run.
     */
    public void suppressPlayersForRun(UUID runId, Collection<UUID> playerIds, long durationMs) {
        if (runId == null || playerIds == null || playerIds.isEmpty() || durationMs <= 0L) {
            return;
        }

        long expiresAt = System.currentTimeMillis() + durationMs;
        for (UUID playerId : playerIds) {
            if (playerId == null) continue;
            suppressedPlayers.merge(playerId, new PlayerSuppression(runId, expiresAt), (oldValue, newValue) -> {
                if (!Objects.equals(oldValue.runId(), runId)) {
                    return newValue;
                }
                return oldValue.expiresAtMillis() >= newValue.expiresAtMillis() ? oldValue : newValue;
            });
        }
    }

    /**
     * Temporarily suppresses spawning inside a circular area.
     * If runId is null, suppression is global for all runs in that world.
     */
    public void addSuppressionZone(UUID runId, Location center, double radius, long durationMs) {
        if (center == null || center.getWorld() == null || durationMs <= 0L || radius <= 0.0) {
            return;
        }

        long expiresAt = System.currentTimeMillis() + durationMs;
        suppressionZones.add(new SpawnSuppressionZone(
                runId,
                center.getWorld().getName(),
                center.clone(),
                radius,
                expiresAt
        ));
    }

    /**
     * Temporarily suppresses spawning inside a circular area for all runs.
     */
    public void addGlobalSuppressionZone(Location center, double radius, long durationMs) {
        addSuppressionZone(null, center, radius, durationMs);
    }

    /**
     * Clears all temporary suppression entries for a run.
     */
    public void clearSuppressionForRun(UUID runId) {
        if (runId == null) return;
        suppressedPlayers.entrySet().removeIf(entry -> Objects.equals(entry.getValue().runId(), runId));
        synchronized (suppressionZones) {
            suppressionZones.removeIf(zone -> Objects.equals(zone.runId(), runId));
        }
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
        cleanupSuppressionState(System.currentTimeMillis());

        // First, execute any pending plans from previous tick
        if (!pendingPlans.isEmpty()) {
            // Filter out stale plans (player offline, left run, or too far from spawn location)
            List<SpawnPlan> validPlans = filterValidPlans(pendingPlans);
            pendingPlans.clear();

            if (!validPlans.isEmpty()) {
                executeSpawnPlans(validPlans);
                // If we still have pending plans after execution, wait for next tick
                if (!pendingPlans.isEmpty()) {
                    return;
                }
            }
        }

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
     * Filters pending plans to remove stale entries.
     * A plan is stale if the target player is offline, not in a run, or too far from spawn location.
     */
    private List<SpawnPlan> filterValidPlans(List<SpawnPlan> plans) {
        double maxDistance = config.getMaxSpawnDistance() * 2; // Allow some buffer for player movement
        long now = System.currentTimeMillis();

        return plans.stream().filter(plan -> {
            Player player = Bukkit.getPlayer(plan.targetPlayerId());
            if (player == null || !player.isOnline()) return false;

            // Check player is still in the correct world
            if (!player.getWorld().getName().equals(plan.worldName())) return false;

            // Check player is still in a run
            Optional<PlayerState> playerStateOpt = state.getPlayer(plan.targetPlayerId());
            if (playerStateOpt.isEmpty() || playerStateOpt.get().getMode() != PlayerMode.IN_RUN) return false;
            UUID runId = playerStateOpt.get().getRunId();
            if (runId != null && isPlayerSuppressed(runId, plan.targetPlayerId(), now)) return false;
            if (isLocationSuppressed(runId, plan.spawnLocation(), now)) return false;

            // Check player hasn't moved too far from the planned spawn location
            if (player.getLocation().distance(plan.spawnLocation()) > maxDistance) return false;

            return true;
        }).toList();
    }

    /**
     * Phase A: Collect spawn contexts from all active runs.
     * Must run on main thread.
     */
    private List<SpawnContext> collectSpawnContexts() {
        // Clear mob count cache for this tick
        mobCountCache.clear();

        List<SpawnContext> contexts = new ArrayList<>();
        long now = System.currentTimeMillis();

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
                if (isPlayerSuppressed(run.getRunId(), playerId, now)) continue;

                // Calculate nearby mob count
                int nearbyMobs = getMobCountNear(player.getLocation(), config.getMobCountRadius());

                // Calculate average team level
                double avgLevel = calculateAverageLevel(run, player.getLocation());

                // Count nearby players
                int nearbyPlayers = countNearbyPlayers(player.getLocation(), config.getLevelSamplingRadius(), run);

                // Create LOS checker for spawn radius (captures ChunkSnapshots on main thread)
                LineOfSightChecker losChecker = null;
                if (config.isLosValidationEnabled()) {
                    losChecker = LineOfSightChecker.createForRadius(
                            player.getLocation(),
                            config.getMaxSpawnDistance()
                    );
                }

                SpawnContext context = new SpawnContext(
                        playerId,
                        run.getRunId(),
                        worldName,
                        player.getLocation(),
                        playerState.getRunLevel(),  // Use runLevel instead of equipment-based level
                        avgLevel,
                        nearbyPlayers,
                        nearbyMobs,
                        run.getElapsedSeconds(),
                        run.getStageStartEnemyLevel(),
                        losChecker
                );

                contexts.add(context);
            }
        }

        return contexts;
    }

    /**
     * Phase B: Plan spawns based on collected contexts.
     * Runs on async thread.
     * Uses round-robin interleaving to ensure fair distribution across players.
     */
    private List<SpawnPlan> planSpawns(List<SpawnContext> contexts) {
        // Collect plans per player for round-robin interleaving
        List<List<SpawnPlan>> plansPerPlayer = new ArrayList<>();

        for (SpawnContext ctx : contexts) {
            int targetMobs = calculateTargetMobsForLevel(ctx.averageTeamLevel());
            int toSpawn = Math.min(
                    targetMobs - ctx.nearbyMobCount(),
                    config.getMaxSpawnsPerPlayerPerTick()
            );

            if (toSpawn <= 0) {
                plansPerPlayer.add(Collections.emptyList());
                continue;
            }

            // Calculate enemy level FIRST (needed for archetype selection)
            int enemyLevel = calculateEnemyLevel(ctx);

            // Debug: log level calculation details
            if (config.isVerbose()) {
                plugin.getLogger().info("[SpawnDebug] avgTeamLevel=" + ctx.averageTeamLevel() +
                        ", nearbyPlayers=" + ctx.nearbyPlayerCount() +
                        ", runDuration=" + ctx.runDurationSeconds() + "s" +
                        ", calculatedEnemyLevel=" + enemyLevel +
                        ", world=" + ctx.worldName());
            }

            List<SpawnPlan> playerPlans = new ArrayList<>();
            for (int i = 0; i < toSpawn; i++) {
                // Select archetype based on current level and world (level + world gated selection)
                EnemyArchetypeConfig archetype = selectArchetype(enemyLevel, ctx.worldName());
                if (archetype == null) {
                    // No archetypes available at this level/world - stop trying
                    break;
                }

                // Sample spawn location with LOS validation
                Location spawnLoc = sampleSpawnLocation(ctx.runId(), ctx.playerLocation(), ctx.losChecker());
                if (spawnLoc == null) continue;

                playerPlans.add(new SpawnPlan(
                        ctx.playerId(),
                        ctx.worldName(),
                        spawnLoc,
                        archetype,
                        enemyLevel
                ));
            }
            plansPerPlayer.add(playerPlans);
        }

        // Round-robin interleave: take 1 from each player, then 2nd from each, etc.
        return interleaveRoundRobin(plansPerPlayer);
    }

    /**
     * Interleaves plans from multiple players in round-robin fashion.
     * Ensures fair distribution when maxSpawnsPerTick limits total spawns.
     */
    private List<SpawnPlan> interleaveRoundRobin(List<List<SpawnPlan>> plansPerPlayer) {
        List<SpawnPlan> result = new ArrayList<>();
        int maxSize = plansPerPlayer.stream().mapToInt(List::size).max().orElse(0);

        for (int i = 0; i < maxSize; i++) {
            for (List<SpawnPlan> playerPlans : plansPerPlayer) {
                if (i < playerPlans.size()) {
                    result.add(playerPlans.get(i));
                }
            }
        }

        return result;
    }

    /**
     * Phase C: Execute spawn plans on main thread.
     * Must run on main thread.
     * Remaining plans that couldn't be executed are stored in pendingPlans for next tick.
     */
    private void executeSpawnPlans(List<SpawnPlan> plans) {
        int commandsThisTick = 0;
        int spawnsThisTick = 0;
        int plansExecuted = 0;

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

                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    commandsThisTick++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to execute spawn command: " + cmd, e);
                }
            }

            spawnsThisTick++;
            plansExecuted++;
        }

        // Store remaining plans for next tick
        pendingPlans.clear();
        if (plansExecuted < plans.size()) {
            pendingPlans.addAll(plans.subList(plansExecuted, plans.size()));
        }

        if (config.isVerbose() && spawnsThisTick > 0) {
            plugin.getLogger().info("Spawned " + spawnsThisTick + " entities using " + commandsThisTick + " commands"
                    + (pendingPlans.isEmpty() ? "" : ", " + pendingPlans.size() + " pending"));
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
        int minLevel = Math.max(config.getMinEnemyLevel(), ctx.minEnemyLevel());
        return Math.max(minLevel, Math.min(config.getMaxEnemyLevel(), result));
    }

    /**
     * Calculate target mobs per player based on team average run level.
     * Formula: floor(base + (level - 1) * increasePerLevel), capped at max.
     */
    private int calculateTargetMobsForLevel(double avgTeamLevel) {
        int base = config.getTargetMobsPerPlayer();
        double increase = config.getTargetMobsPerPlayerIncreasePerLevel();
        int max = config.getTargetMobsPerPlayerMax();

        int target = (int) Math.floor(base + (avgTeamLevel - 1) * increase);
        return Math.min(target, max);
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
                plugin.getLogger().info("[SpawnDebug] No archetypes available at level " + currentLevel + " in world " + worldName);
            }
            return null;
        }

        // Debug: log archetype selection
        if (config.isVerbose()) {
            plugin.getLogger().info("[SpawnDebug] Selecting archetype: level=" + currentLevel +
                    ", world=" + worldName +
                    ", totalArchetypes=" + allArchetypes.size() +
                    ", eligible=" + eligible.size() +
                    ", eligibleIds=" + eligible.stream()
                            .map(a -> a.archetypeId + "(minLvl:" + a.minSpawnLevel + ")")
                            .toList());
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
     * Mobs spawn within the configured vertical range of the player's Y level.
     *
     * @param playerLoc The player's location
     * @param losChecker LOS checker for validating spawn positions (may be null if disabled)
     * @return A valid spawn location, or null if none found
     */
    private Location sampleSpawnLocation(UUID runId, Location playerLoc, LineOfSightChecker losChecker) {
        World world = playerLoc.getWorld();
        if (world == null) return null;

        double minDist = config.getMinSpawnDistance();
        double maxDist = config.getMaxSpawnDistance();
        int maxAttempts = config.getMaxSampleAttempts();
        int verticalRange = config.getSpawnVerticalRange();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Random angle and distance
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            // Search for safe Y near player's Y level (not highest block)
            Location candidate = findSafeYNearPlayer(world, x, z, playerLoc.getBlockY(), verticalRange);

            if (candidate != null) {
                // Check line-of-sight to player if LOS validation is enabled
                if (losChecker != null && !losChecker.hasLineOfSight(candidate, playerLoc)) {
                    continue; // Blocked, try another location
                }
                if (isLocationSuppressed(runId, candidate, System.currentTimeMillis())) {
                    continue; // Suppressed zone, try another location
                }
                return candidate;
            }
        }

        return null;
    }

    /**
     * Finds a safe spawn Y within vertical range of the player's Y level.
     * Searches from player level outward, prioritizing same level.
     */
    private Location findSafeYNearPlayer(World world, double x, double z, int playerY, int verticalRange) {
        for (int yOffset = 0; yOffset <= verticalRange; yOffset++) {
            // Try at/above player level first
            Location above = trySpawnAt(world, x, playerY + yOffset, z);
            if (above != null) return above;

            // Try below player level (skip if yOffset == 0 to avoid duplicate)
            if (yOffset > 0) {
                Location below = trySpawnAt(world, x, playerY - yOffset, z);
                if (below != null) return below;
            }
        }
        return null;
    }

    /**
     * Attempts to create a spawn location at a specific Y level.
     * Returns the location if safe, null otherwise.
     */
    private Location trySpawnAt(World world, double x, int y, double z) {
        Location loc = new Location(world, x, y, z);
        if (isSafeSpawnLocation(loc)) {
            return loc;
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
     * Spawns an immediate surge around a center location.
     * Used by battery objective pressure waves.
     */
    public void spawnSurgeAround(RunState run, Location center, int mobCount) {
        if (run == null || center == null || center.getWorld() == null) return;
        if (!run.isActive()) return;
        if (mobCount <= 0) return;

        int commandsExecuted = 0;
        int maxCommands = config.getMaxCommandsPerTick();
        int level = Math.max(config.getMinEnemyLevel(), run.getStageStartEnemyLevel());

        for (int i = 0; i < mobCount; i++) {
            if (commandsExecuted >= maxCommands) break;

            EnemyArchetypeConfig archetype = selectArchetype(level, run.getWorldName());
            if (archetype == null) break;

            Location spawnLoc = sampleSurgeLocation(center, 12.0, 26.0);
            if (spawnLoc == null) continue;

            commandsExecuted += executeArchetypeSpawnCommands(
                    run, archetype, spawnLoc, level, maxCommands - commandsExecuted, "surge"
            );
        }
    }

    /**
     * Spawns one stage-configured battery activation boss around the battery center.
     * Uses a random archetype from the provided stage list.
     *
     * @return true if at least one spawn command was executed
     */
    public boolean spawnStageBatteryActivationBoss(RunState run, Location center, List<String> stageArchetypeIds) {
        if (run == null || center == null || center.getWorld() == null) return false;
        if (!run.isActive()) return false;
        if (stageArchetypeIds == null || stageArchetypeIds.isEmpty()) return false;

        List<EnemyArchetypeConfig> candidates = resolveStageBossCandidates(stageArchetypeIds, run.getWorldName());
        if (candidates.isEmpty()) {
            if (config.isVerbose()) {
                plugin.getLogger().info("[SpawnDebug] No valid stage battery boss archetypes for stage="
                        + run.getStageGroupId() + ", world=" + run.getWorldName()
                        + ", configured=" + stageArchetypeIds);
            }
            return false;
        }

        EnemyArchetypeConfig selected = candidates.get(random.nextInt(candidates.size()));
        int level = Math.max(config.getMinEnemyLevel(), run.getStageStartEnemyLevel());
        level = Math.max(level, selected.minSpawnLevel);

        double minDist = Math.max(2.5, config.getBatteryChargeRadius() * 0.6);
        double maxDist = Math.max(minDist + 1.5, config.getBatteryChargeRadius() * 1.35);
        Location spawnLoc = sampleSurgeLocation(center, minDist, maxDist);
        if (spawnLoc == null) {
            spawnLoc = center.clone();
        }

        int maxCommands = Math.max(1, config.getMaxCommandsPerTick());
        int commands = executeArchetypeSpawnCommands(run, selected, spawnLoc, level, maxCommands, "battery_activation_boss");
        return commands > 0;
    }

    private List<EnemyArchetypeConfig> resolveStageBossCandidates(List<String> stageArchetypeIds, String worldName) {
        Map<String, EnemyArchetypeConfig> all = config.getEnemyArchetypes();
        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnemyArchetypeConfig> candidates = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (String rawId : stageArchetypeIds) {
            if (rawId == null) continue;
            String archetypeId = rawId.trim();
            if (archetypeId.isEmpty()) continue;

            String normalized = archetypeId.toLowerCase(Locale.ROOT);
            if (!dedupe.add(normalized)) continue;

            EnemyArchetypeConfig archetype = all.get(archetypeId);
            if (archetype == null) continue;
            if (!archetype.isAllowedInWorld(worldName)) continue;
            candidates.add(archetype);
        }
        return candidates;
    }

    private int executeArchetypeSpawnCommands(RunState run, EnemyArchetypeConfig archetype, Location spawnLoc,
                                              int level, int maxCommands, String source) {
        if (run == null || archetype == null || spawnLoc == null || maxCommands <= 0) {
            return 0;
        }

        int executed = 0;
        for (String cmdTemplate : archetype.spawnCommands) {
            if (executed >= maxCommands) break;

            Map<String, Object> context = new HashMap<>();
            context.put("sx", spawnLoc.getBlockX());
            context.put("sy", spawnLoc.getBlockY());
            context.put("sz", spawnLoc.getBlockZ());
            context.put("runWorld", run.getWorldName());
            context.put("enemyLevel", level);
            context.put("enemyType", archetype.enemyType);
            context.put("archetypeId", archetype.archetypeId);

            String cmd = templateEngine.expand(cmdTemplate, context);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                executed++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to execute " + source + " spawn command: " + cmd, e);
            }
        }

        return executed;
    }

    private Location sampleSurgeLocation(Location center, double minDist, double maxDist) {
        World world = center.getWorld();
        if (world == null) return null;

        int maxAttempts = config.getMaxSampleAttempts();
        int verticalRange = config.getSpawnVerticalRange();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;

            Location candidate = findSafeYNearPlayer(world, x, z, center.getBlockY(), verticalRange);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private void cleanupSuppressionState(long now) {
        suppressedPlayers.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        synchronized (suppressionZones) {
            suppressionZones.removeIf(zone -> zone.expiresAtMillis() <= now);
        }
    }

    private boolean isPlayerSuppressed(UUID runId, UUID playerId, long now) {
        PlayerSuppression suppression = suppressedPlayers.get(playerId);
        if (suppression == null) {
            return false;
        }
        if (suppression.expiresAtMillis() <= now) {
            suppressedPlayers.remove(playerId, suppression);
            return false;
        }
        return Objects.equals(suppression.runId(), runId);
    }

    private boolean isLocationSuppressed(UUID runId, Location location, long now) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        String worldName = location.getWorld().getName();
        double x = location.getX();
        double z = location.getZ();

        synchronized (suppressionZones) {
            Iterator<SpawnSuppressionZone> it = suppressionZones.iterator();
            while (it.hasNext()) {
                SpawnSuppressionZone zone = it.next();
                if (zone.expiresAtMillis() <= now) {
                    it.remove();
                    continue;
                }
                UUID zoneRunId = zone.runId();
                if (zoneRunId != null && !Objects.equals(zoneRunId, runId)) continue;
                if (!zone.worldName().equalsIgnoreCase(worldName)) continue;

                double dx = x - zone.center().getX();
                double dz = z - zone.center().getZ();
                double radius = zone.radius();
                if ((dx * dx + dz * dz) <= radius * radius) {
                    return true;
                }
            }
        }

        return false;
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
