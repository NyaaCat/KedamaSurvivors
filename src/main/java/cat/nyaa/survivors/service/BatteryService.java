package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles stage battery objective flow:
 * spawn -> charge -> full team interact -> objective progress.
 */
public class BatteryService {

    private static final String BATTERY_TAG = "vrs_battery";
    private static final String BATTERY_UUID_TAG_PREFIX = "vrs_battery_uuid:";

    private final KedamaSurvivorsPlugin plugin;
    private final StateService state;

    // Runtime states
    private final Map<UUID, RunBatteryState> runStates = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> runSpawnTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> runUpdateTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BatteryInstance> batteriesById = new ConcurrentHashMap<>();

    public BatteryService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.state = plugin.getStateService();
    }

    public void start() {
        // No global task; tasks are bound to runs.
    }

    public void stop() {
        for (UUID runId : new ArrayList<>(runStates.keySet())) {
            stopForRun(runId);
        }
    }

    /**
     * Rebinds active run tasks to apply latest battery config at runtime.
     * Keeps existing battery progress/instances when battery feature remains enabled.
     */
    public void reloadRuntimeConfig() {
        List<UUID> runIds = new ArrayList<>(runStates.keySet());
        for (UUID runId : runIds) {
            Optional<RunState> runOpt = state.getRun(runId);
            if (runOpt.isEmpty() || !runOpt.get().isActive()) {
                stopForRun(runId);
                continue;
            }

            Integer spawnTaskId = runSpawnTasks.remove(runId);
            if (spawnTaskId != null) {
                Bukkit.getScheduler().cancelTask(spawnTaskId);
            }
            Integer updateTaskId = runUpdateTasks.remove(runId);
            if (updateTaskId != null) {
                Bukkit.getScheduler().cancelTask(updateTaskId);
            }

            if (!plugin.getConfigService().isBatteryEnabled()) {
                stopForRun(runId);
                continue;
            }

            int spawnIntervalTicks = Math.max(20, plugin.getConfigService().getBatterySpawnIntervalSeconds() * 20);
            int updateTicks = Math.max(1, plugin.getConfigService().getBatteryProgressUpdateTicks());

            int newSpawnTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> trySpawnBattery(runId, false),
                    spawnIntervalTicks, spawnIntervalTicks).getTaskId();
            runSpawnTasks.put(runId, newSpawnTaskId);

            int newUpdateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateRunBattery(runId),
                    updateTicks, updateTicks).getTaskId();
            runUpdateTasks.put(runId, newUpdateTaskId);
        }
    }

    public void startForRun(RunState run) {
        if (!plugin.getConfigService().isBatteryEnabled()) return;

        UUID runId = run.getRunId();
        stopForRun(runId);

        RunBatteryState runState = new RunBatteryState(runId);
        runStates.put(runId, runState);

        int spawnIntervalTicks = Math.max(20, plugin.getConfigService().getBatterySpawnIntervalSeconds() * 20);
        int updateTicks = Math.max(1, plugin.getConfigService().getBatteryProgressUpdateTicks());

        int spawnTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> trySpawnBattery(runId, false),
                spawnIntervalTicks, spawnIntervalTicks).getTaskId();
        runSpawnTasks.put(runId, spawnTaskId);

        int updateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateRunBattery(runId),
                updateTicks, updateTicks).getTaskId();
        runUpdateTasks.put(runId, updateTaskId);
    }

    public void stopForRun(UUID runId) {
        Integer spawnTaskId = runSpawnTasks.remove(runId);
        if (spawnTaskId != null) {
            Bukkit.getScheduler().cancelTask(spawnTaskId);
        }

        Integer updateTaskId = runUpdateTasks.remove(runId);
        if (updateTaskId != null) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
        }

        RunBatteryState runState = runStates.remove(runId);
        if (runState != null && runState.activeBatteryId != null) {
            removeBattery(runState.activeBatteryId);
        }
    }

    public void handleInteract(Player player, UUID batteryId) {
        BatteryInstance battery = batteriesById.get(batteryId);
        if (battery == null) return;

        Optional<RunState> runOpt = state.getRun(battery.runId);
        if (runOpt.isEmpty() || !runOpt.get().isActive()) return;
        RunState run = runOpt.get();

        Optional<PlayerState> psOpt = state.getPlayer(player.getUniqueId());
        if (psOpt.isEmpty() || psOpt.get().getMode() != PlayerMode.IN_RUN) return;
        if (!run.isParticipant(player.getUniqueId())) return;

        if (!battery.charged) {
            plugin.getI18nService().send(player, "info.battery_not_ready");
            return;
        }

        battery.interactedPlayers.add(player.getUniqueId());
        plugin.getI18nService().send(player, "info.battery_interacted");

        int requiredCount = getRequiredInteractCount(run);
        int currentCount = battery.interactedPlayers.size();
        updateBatteryName(battery, true, 0, 0, currentCount, requiredCount);

        if (currentCount < requiredCount) {
            return;
        }

        removeBattery(battery.batteryId);
        RunBatteryState runState = runStates.get(run.getRunId());
        if (runState != null) {
            runState.activeBatteryId = null;
        }

        plugin.getRunService().completeBatteryObjective(run);

        if (run.isActive()) {
            trySpawnBattery(run.getRunId(), true);
        }
    }

    private void trySpawnBattery(UUID runId, boolean force) {
        Optional<RunState> runOpt = state.getRun(runId);
        if (runOpt.isEmpty()) {
            stopForRun(runId);
            return;
        }

        RunState run = runOpt.get();
        if (!run.isActive()) return;

        RunBatteryState runState = runStates.get(runId);
        if (runState == null || runState.activeBatteryId != null) return;
        if (run.isStageObjectiveComplete()) return;

        if (!force) {
            double chance = plugin.getConfigService().getBatterySpawnChance();
            if (ThreadLocalRandom.current().nextDouble() > chance) {
                return;
            }
        }

        Location spawnLoc = sampleBatteryLocation(run);
        if (spawnLoc == null) return;

        BatteryInstance battery = spawnBattery(run, spawnLoc);
        if (battery == null) return;

        runState.activeBatteryId = battery.batteryId;
        batteriesById.put(battery.batteryId, battery);

        notifyRunPlayers(run, "info.battery_spawned");
    }

    private void updateRunBattery(UUID runId) {
        RunBatteryState runState = runStates.get(runId);
        if (runState == null || runState.activeBatteryId == null) return;

        Optional<RunState> runOpt = state.getRun(runId);
        if (runOpt.isEmpty() || !runOpt.get().isActive()) {
            stopForRun(runId);
            return;
        }
        RunState run = runOpt.get();

        BatteryInstance battery = batteriesById.get(runState.activeBatteryId);
        if (battery == null) {
            runState.activeBatteryId = null;
            return;
        }

        if (!battery.isValid()) {
            removeBattery(battery.batteryId);
            runState.activeBatteryId = null;
            return;
        }

        double radius = plugin.getConfigService().getBatteryChargeRadius();
        int playersInRange = countPlayersInRange(run, battery.location, radius);
        int enemiesInRange = countEnemiesInRange(battery.location, radius);

        long now = System.currentTimeMillis();
        double elapsedSeconds;
        if (battery.lastUpdateMillis == 0L) {
            elapsedSeconds = plugin.getConfigService().getBatteryProgressUpdateTicks() / 20.0;
        } else {
            elapsedSeconds = Math.max(0.05, (now - battery.lastUpdateMillis) / 1000.0);
        }
        battery.lastUpdateMillis = now;

        boolean charging = false;
        if (!battery.charged && enemiesInRange == 0 && playersInRange > 0) {
            charging = true;

            double speed = plugin.getConfigService().getBatteryBaseChargePercentPerSecond();
            if (playersInRange > 1) {
                speed += (playersInRange - 1) * plugin.getConfigService().getBatteryExtraPlayerChargePercentPerSecond();
            }

            battery.progress = Math.min(100.0, battery.progress + speed * elapsedSeconds);

            if (plugin.getConfigService().isBatterySurgeEnabled()) {
                long surgeIntervalMs = Math.max(1, plugin.getConfigService().getBatterySurgeIntervalSeconds()) * 1000L;
                if (now - runState.lastSurgeMillis >= surgeIntervalMs) {
                    runState.lastSurgeMillis = now;
                    plugin.getSpawnerService().spawnSurgeAround(run, battery.location,
                            plugin.getConfigService().getBatterySurgeMobCount());
                }
            }
        }

        if (!battery.charged && battery.progress >= 100.0) {
            battery.progress = 100.0;
            battery.charged = true;
            notifyRunPlayers(run, "info.battery_charged");
        }

        int requiredCount = getRequiredInteractCount(run);
        int currentCount = battery.interactedPlayers.size();
        updateBatteryName(battery, charging, playersInRange, enemiesInRange, currentCount, requiredCount);

        if (plugin.getConfigService().isBatteryShowRangeParticles()) {
            spawnRangeParticles(battery.location, radius, battery.charged);
        }
    }

    private BatteryInstance spawnBattery(RunState run, Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        UUID batteryId = UUID.randomUUID();
        ItemStack headItem = new ItemStack(plugin.getConfigService().getBatteryDisplayMaterial());
        ItemMeta meta = headItem.getItemMeta();
        if (meta != null) {
            if (plugin.getConfigService().getBatteryDisplayCustomModelData() > 0) {
                meta.setCustomModelData(plugin.getConfigService().getBatteryDisplayCustomModelData());
            }
            headItem.setItemMeta(meta);
        }

        ArmorStand stand = world.spawn(location, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setCanPickupItems(false);
            as.setMarker(false);
            as.setCustomNameVisible(true);
            as.getEquipment().setHelmet(headItem);
            as.addScoreboardTag(BATTERY_TAG);
            as.addScoreboardTag(BATTERY_UUID_TAG_PREFIX + batteryId);
        });

        BatteryInstance battery = new BatteryInstance(batteryId, run.getRunId(), stand, location.clone());
        updateBatteryName(battery, false, 0, 0, 0, getRequiredInteractCount(run));
        return battery;
    }

    private void removeBattery(UUID batteryId) {
        BatteryInstance battery = batteriesById.remove(batteryId);
        if (battery == null) return;

        if (battery.stand != null && battery.stand.isValid()) {
            battery.stand.remove();
        }
    }

    private Location sampleBatteryLocation(RunState run) {
        List<Player> candidates = new ArrayList<>();
        for (UUID participantId : run.getParticipants()) {
            Player p = Bukkit.getPlayer(participantId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equals(run.getWorldName())) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return null;

        Collections.shuffle(candidates);
        double minDist = plugin.getConfigService().getBatteryMinDistanceFromPlayers();
        double maxDist = plugin.getConfigService().getBatteryMaxDistanceFromPlayers();
        int maxTries = Math.max(8, plugin.getConfigService().getMaxSampleAttempts() * 2);

        for (Player anchor : candidates) {
            for (int i = 0; i < maxTries; i++) {
                Location candidate = plugin.getWorldService().sampleSpawnNear(anchor.getLocation(), minDist, maxDist);
                if (candidate == null) continue;
                if (isFarEnoughFromParticipants(run, candidate, minDist * 0.7)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private boolean isFarEnoughFromParticipants(RunState run, Location location, double minDistance) {
        double minDistSq = minDistance * minDistance;
        for (UUID participantId : run.getParticipants()) {
            Player p = Bukkit.getPlayer(participantId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().equals(location.getWorld())) continue;
            if (p.getLocation().distanceSquared(location) < minDistSq) {
                return false;
            }
        }
        return true;
    }

    private int countPlayersInRange(RunState run, Location center, double radius) {
        double radiusSq = radius * radius;
        int count = 0;
        for (UUID participantId : run.getParticipants()) {
            Optional<PlayerState> psOpt = state.getPlayer(participantId);
            if (psOpt.isEmpty() || psOpt.get().getMode() != PlayerMode.IN_RUN) continue;

            Player p = Bukkit.getPlayer(participantId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().equals(center.getWorld())) continue;
            if (p.getLocation().distanceSquared(center) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    private int countEnemiesInRange(Location center, double radius) {
        if (center.getWorld() == null) return 0;

        return (int) center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(entity -> plugin.getSpawnerService().isVrsMob(entity))
                .count();
    }

    private int getRequiredInteractCount(RunState run) {
        int count = 0;
        for (UUID participantId : run.getParticipants()) {
            Optional<PlayerState> psOpt = state.getPlayer(participantId);
            if (psOpt.isPresent() && psOpt.get().getMode() == PlayerMode.IN_RUN) {
                Player p = Bukkit.getPlayer(participantId);
                if (p != null && p.isOnline()) {
                    count++;
                }
            }
        }
        return Math.max(1, count);
    }

    private void updateBatteryName(BatteryInstance battery, boolean charging, int players, int enemies,
                                   int interacted, int requiredInteract) {
        if (battery.stand == null || !battery.stand.isValid()) return;

        String statusLine;
        if (battery.charged) {
            statusLine = "§a已充满";
        } else if (enemies > 0) {
            statusLine = "§c受干扰";
        } else if (charging) {
            statusLine = "§b充能中";
        } else {
            statusLine = "§7待机";
        }

        String percentText = String.format("%.0f%%", battery.progress);
        String bar = buildProgressBar(battery.progress, battery.charged ? "§a" : (enemies > 0 ? "§c" : "§b"));

        String interactText = battery.charged
                ? (" §7交互§f " + interacted + "/" + requiredInteract)
                : "";

        String name = plugin.getConfigService().getBatteryDisplayName()
                + " §8| " + statusLine
                + " §8| " + bar + " §f" + percentText
                + interactText;

        battery.stand.setCustomName(name);
    }

    private String buildProgressBar(double percent, String color) {
        int total = 20;
        int filled = (int) Math.round((percent / 100.0) * total);
        filled = Math.max(0, Math.min(total, filled));

        StringBuilder sb = new StringBuilder();
        sb.append(color).append("[");
        for (int i = 0; i < total; i++) {
            if (i < filled) {
                sb.append("#");
            } else {
                sb.append("§8-");
                if (i < total - 1) {
                    sb.append(color);
                }
            }
        }
        sb.append(color).append("]");
        return sb.toString();
    }

    private void spawnRangeParticles(Location center, double radius, boolean charged) {
        World world = center.getWorld();
        if (world == null) return;

        Particle particle = charged ? Particle.HAPPY_VILLAGER : Particle.END_ROD;
        int points = 18;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            world.spawnParticle(particle, x, center.getY() + 0.15, z, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private void notifyRunPlayers(RunState run, String key, Object... args) {
        Optional<TeamState> teamOpt = state.getTeam(run.getTeamId());
        if (teamOpt.isEmpty()) return;

        for (UUID memberId : teamOpt.get().getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
                plugin.getI18nService().send(p, key, args);
            }
        }
    }

    public static UUID extractBatteryId(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(BATTERY_UUID_TAG_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(BATTERY_UUID_TAG_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static class RunBatteryState {
        private final UUID runId;
        private UUID activeBatteryId;
        private long lastSurgeMillis;

        private RunBatteryState(UUID runId) {
            this.runId = runId;
        }
    }

    private static class BatteryInstance {
        private final UUID batteryId;
        private final UUID runId;
        private final ArmorStand stand;
        private final Location location;
        private final Set<UUID> interactedPlayers = ConcurrentHashMap.newKeySet();

        private double progress;
        private boolean charged;
        private long lastUpdateMillis;

        private BatteryInstance(UUID batteryId, UUID runId, ArmorStand stand, Location location) {
            this.batteryId = batteryId;
            this.runId = runId;
            this.stand = stand;
            this.location = location;
        }

        private boolean isValid() {
            return stand != null && stand.isValid() && !stand.isDead();
        }
    }
}
