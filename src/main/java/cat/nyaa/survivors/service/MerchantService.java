package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.RunState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns and manages merchant villagers in combat worlds.
 * Merchants spawn periodically and despawn after a configured lifetime.
 */
public class MerchantService {

    private static final String MERCHANT_TAG = "vrs_merchant";

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final I18nService i18n;

    // Track merchants per run (runId -> set of merchant entity UUIDs)
    private final Map<UUID, Set<UUID>> runMerchants = new ConcurrentHashMap<>();

    // Track merchant spawn times for despawn logic
    private final Map<UUID, Long> merchantSpawnTimes = new ConcurrentHashMap<>();

    // Merchant spawn task per run
    private final Map<UUID, Integer> runSpawnTasks = new ConcurrentHashMap<>();

    // Merchant despawn checker task
    private int despawnTaskId = -1;

    public MerchantService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.i18n = plugin.getI18nService();
    }

    /**
     * Starts the merchant service (despawn checker).
     */
    public void start() {
        if (!config.isMerchantsEnabled()) {
            return;
        }

        // Start despawn checker every second
        despawnTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::checkDespawns, 20, 20).getTaskId();
        plugin.getLogger().info("Merchant service started");
    }

    /**
     * Stops the merchant service.
     */
    public void stop() {
        if (despawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(despawnTaskId);
            despawnTaskId = -1;
        }

        // Cancel all spawn tasks
        for (int taskId : runSpawnTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        runSpawnTasks.clear();

        // Clear all merchants
        clearAllMerchants();
    }

    /**
     * Starts merchant spawning for a run.
     */
    public void startForRun(RunState run) {
        if (!config.isMerchantsEnabled()) {
            return;
        }

        UUID runId = run.getRunId();
        if (runSpawnTasks.containsKey(runId)) {
            return; // Already started
        }

        // Initialize merchant set for this run
        runMerchants.put(runId, ConcurrentHashMap.newKeySet());

        // Schedule periodic merchant spawning
        int intervalTicks = config.getMerchantSpawnInterval() * 20;
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Optional<RunState> runOpt = state.getRun(runId);
            runOpt.ifPresent(this::trySpawnMerchant);
        }, intervalTicks, intervalTicks).getTaskId();

        runSpawnTasks.put(runId, taskId);
    }

    /**
     * Stops merchant spawning for a run and clears its merchants.
     */
    public void stopForRun(UUID runId) {
        // Cancel spawn task
        Integer taskId = runSpawnTasks.remove(runId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // Clear merchants for this run
        clearMerchants(runId);
        runMerchants.remove(runId);
    }

    /**
     * Attempts to spawn a merchant for a run.
     */
    private void trySpawnMerchant(RunState run) {
        if (!run.isActive()) return;

        // Find a suitable spawn location
        Location spawnLoc = sampleMerchantLocation(run);
        if (spawnLoc == null) {
            plugin.getLogger().warning("Could not find valid merchant spawn location for run: " + run.getRunId());
            return;
        }

        // Spawn the merchant
        Villager merchant = spawnMerchant(spawnLoc);
        if (merchant == null) return;

        // Track the merchant
        UUID merchantId = merchant.getUniqueId();
        Set<UUID> merchants = runMerchants.get(run.getRunId());
        if (merchants != null) {
            merchants.add(merchantId);
        }
        merchantSpawnTimes.put(merchantId, System.currentTimeMillis());

        // Notify players
        notifyRunPlayers(run, "success.merchant_spawned");

        plugin.getLogger().info("Spawned merchant at " + formatLocation(spawnLoc) + " for run " + run.getRunId());
    }

    /**
     * Spawns a merchant villager at the given location.
     */
    private Villager spawnMerchant(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        Villager villager = world.spawn(location, Villager.class, v -> {
            v.setCustomName(config.getCoinDisplayName()); // Use coin name as merchant name for now
            v.setCustomNameVisible(true);
            v.setInvulnerable(true);
            v.setAI(false);
            v.setProfession(Villager.Profession.NONE);
            v.addScoreboardTag(MERCHANT_TAG);
        });

        return villager;
    }

    /**
     * Samples a location for merchant spawning near players.
     */
    private Location sampleMerchantLocation(RunState run) {
        List<UUID> alivePlayers = new ArrayList<>(run.getAlivePlayers());
        if (alivePlayers.isEmpty()) return null;

        // Pick a random alive player
        UUID targetId = alivePlayers.get(ThreadLocalRandom.current().nextInt(alivePlayers.size()));
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) return null;

        Location playerLoc = target.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return null;

        double minDist = config.getMerchantMinDistance();
        double maxDist = config.getMerchantMaxDistance();

        // Try to find a valid location
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;
            int y = world.getHighestBlockYAt((int) x, (int) z);

            Location candidate = new Location(world, x, y + 1, z);

            if (isValidMerchantLocation(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Checks if a location is valid for merchant spawning.
     */
    private boolean isValidMerchantLocation(Location loc) {
        if (loc.getWorld() == null) return false;

        // Check passable at feet and head
        if (!loc.getBlock().isPassable()) return false;
        if (!loc.clone().add(0, 1, 0).getBlock().isPassable()) return false;

        // Check solid ground
        if (!loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) return false;

        return true;
    }

    /**
     * Checks for merchants that should be despawned.
     */
    private void checkDespawns() {
        long now = System.currentTimeMillis();
        long lifetimeMs = config.getMerchantLifetime() * 1000L;

        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : merchantSpawnTimes.entrySet()) {
            UUID merchantId = entry.getKey();
            long spawnTime = entry.getValue();

            if (now - spawnTime >= lifetimeMs) {
                toRemove.add(merchantId);
            }
        }

        for (UUID merchantId : toRemove) {
            despawnMerchant(merchantId);
        }
    }

    /**
     * Despawns a single merchant.
     */
    private void despawnMerchant(UUID merchantId) {
        merchantSpawnTimes.remove(merchantId);

        // Find and remove from run tracking
        for (Set<UUID> merchants : runMerchants.values()) {
            merchants.remove(merchantId);
        }

        // Remove entity
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(merchantId)) {
                    entity.remove();
                    return;
                }
            }
        }
    }

    /**
     * Clears all merchants for a specific run.
     */
    public void clearMerchants(UUID runId) {
        Set<UUID> merchants = runMerchants.get(runId);
        if (merchants == null) return;

        int count = 0;
        for (UUID merchantId : merchants) {
            merchantSpawnTimes.remove(merchantId);
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(merchantId)) {
                        entity.remove();
                        count++;
                    }
                }
            }
        }
        merchants.clear();

        if (count > 0) {
            plugin.getLogger().info("Cleared " + count + " merchants for run " + runId);
        }
    }

    /**
     * Clears all merchants globally.
     */
    public void clearAllMerchants() {
        int count = 0;

        // Clear tracked merchants
        for (UUID merchantId : merchantSpawnTimes.keySet()) {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(merchantId)) {
                        entity.remove();
                        count++;
                    }
                }
            }
        }
        merchantSpawnTimes.clear();

        // Clear from run tracking
        for (Set<UUID> merchants : runMerchants.values()) {
            merchants.clear();
        }

        // Also clear any untracked merchants (by tag)
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(MERCHANT_TAG)) {
                    entity.remove();
                    count++;
                }
            }
        }

        if (count > 0) {
            plugin.getLogger().info("Cleared " + count + " merchants globally");
        }
    }

    /**
     * Spawns a merchant manually (admin command).
     */
    public Villager spawnMerchantAt(Location location) {
        return spawnMerchant(location);
    }

    /**
     * Gets count of active merchants for a run.
     */
    public int getMerchantCount(UUID runId) {
        Set<UUID> merchants = runMerchants.get(runId);
        return merchants != null ? merchants.size() : 0;
    }

    /**
     * Checks if an entity is a VRS merchant.
     */
    public boolean isMerchant(Entity entity) {
        return entity.getScoreboardTags().contains(MERCHANT_TAG);
    }

    private void notifyRunPlayers(RunState run, String messageKey) {
        for (UUID playerId : run.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                i18n.send(player, messageKey);
            }
        }
    }

    private String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }
}
