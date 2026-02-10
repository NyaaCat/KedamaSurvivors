package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.merchant.MerchantItemPool;
import cat.nyaa.survivors.merchant.WeightedShopItem;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
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
        applyChargeCompletePlayerSuppression(run, battery, player.getUniqueId());
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
            battery.chargedAtMillis = System.currentTimeMillis();
            notifyRunPlayers(run, "info.battery_charged");
            onBatteryCharged(run, battery);
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

    private List<UUID> getOnlineRunParticipants(RunState run) {
        List<UUID> participants = new ArrayList<>();
        for (UUID participantId : run.getParticipants()) {
            Optional<PlayerState> psOpt = state.getPlayer(participantId);
            if (psOpt.isEmpty() || psOpt.get().getMode() != PlayerMode.IN_RUN) continue;

            Player p = Bukkit.getPlayer(participantId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(run.getWorldName())) continue;
            participants.add(participantId);
        }
        return participants;
    }

    private void onBatteryCharged(RunState run, BatteryInstance battery) {
        applyChargeCompleteSpawnSuppression(run, battery.location);
        triggerChargeCompleteRewardBurst(run, battery.location);
        triggerChargeCompleteEffects(run, battery.location);
    }

    private void applyChargeCompleteSpawnSuppression(RunState run, Location center) {
        ConfigService cfg = plugin.getConfigService();
        if (!cfg.isBatteryChargeCompleteSpawnSuppressionEnabled()) {
            return;
        }

        long durationMs = Math.max(0, cfg.getBatteryChargeCompleteSpawnSuppressionSeconds()) * 1000L;
        if (durationMs <= 0L) {
            return;
        }

        SpawnerService spawner = plugin.getSpawnerService();
        if (spawner == null) {
            return;
        }

        double radius = Math.max(0.1, cfg.getBatteryChargeCompleteSpawnSuppressionRadius());
        spawner.addSuppressionZone(run.getRunId(), center, radius, durationMs);
    }

    private void applyChargeCompletePlayerSuppression(RunState run, BatteryInstance battery, UUID playerId) {
        if (playerId == null || battery == null || !battery.charged) {
            return;
        }

        ConfigService cfg = plugin.getConfigService();
        if (!cfg.isBatteryChargeCompleteSpawnSuppressionEnabled() || !cfg.isBatteryChargeCompleteSuppressParticipants()) {
            return;
        }

        long durationMs = Math.max(0, cfg.getBatteryChargeCompleteSpawnSuppressionSeconds()) * 1000L;
        if (durationMs <= 0L) {
            return;
        }

        SpawnerService spawner = plugin.getSpawnerService();
        if (spawner == null) {
            return;
        }

        long chargedAt = battery.chargedAtMillis > 0L ? battery.chargedAtMillis : System.currentTimeMillis();
        long elapsed = Math.max(0L, System.currentTimeMillis() - chargedAt);
        long remaining = durationMs - elapsed;
        if (remaining <= 0L) {
            return;
        }

        spawner.suppressPlayersForRun(run.getRunId(), List.of(playerId), remaining);
    }

    private void triggerChargeCompleteRewardBurst(RunState run, Location center) {
        ConfigService cfg = plugin.getConfigService();
        if (!cfg.isBatteryChargeCompleteRewardBurstEnabled()) {
            return;
        }

        MerchantService merchantService = plugin.getMerchantService();
        if (merchantService == null) {
            return;
        }

        Optional<MerchantItemPool> poolOpt = resolveRewardPool();
        if (poolOpt.isEmpty()) {
            return;
        }
        MerchantItemPool pool = poolOpt.get();
        if (pool.isEmpty()) {
            return;
        }

        int participants = getOnlineRunParticipants(run).size();
        if (participants <= 0) {
            return;
        }
        int totalDrops = Math.max(0, cfg.getBatteryChargeCompleteRewardBaseCount()) * participants;
        if (totalDrops <= 0) {
            return;
        }

        int burstTicks = Math.max(1, cfg.getBatteryChargeCompleteRewardBurstTicks());
        int lifetimeSeconds = cfg.getBatteryChargeCompleteRewardLifetimeSeconds();
        double scatterRadius = Math.max(0.5,
                Math.min(cfg.getBatteryChargeRadius(), cfg.getBatteryChargeCompleteRewardScatterRadius()));

        int perTick = totalDrops / burstTicks;
        int remainder = totalDrops % burstTicks;

        for (int tick = 0; tick < burstTicks; tick++) {
            int count = perTick + (tick < remainder ? 1 : 0);
            if (count <= 0) continue;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!run.isActive()) return;
                spawnRewardDropBatch(run, center, pool, count, scatterRadius, lifetimeSeconds, merchantService);
            }, tick);
        }
    }

    private Optional<MerchantItemPool> resolveRewardPool() {
        AdminConfigService adminConfig = plugin.getAdminConfigService();
        if (adminConfig == null) {
            return Optional.empty();
        }

        String poolId = plugin.getConfigService().getBatteryChargeCompleteRewardPoolId();
        if (poolId == null || poolId.isBlank()) {
            poolId = plugin.getConfigService().getWanderingMerchantPoolId();
        }

        if (poolId != null && !poolId.isBlank()) {
            Optional<MerchantItemPool> poolOpt = adminConfig.getMerchantPool(poolId.trim());
            if (poolOpt.isPresent()) {
                return poolOpt;
            }
        }

        return adminConfig.getRandomMerchantPool();
    }

    private void spawnRewardDropBatch(RunState run, Location center, MerchantItemPool pool, int count,
                                      double scatterRadius, int lifetimeSeconds, MerchantService merchantService) {
        for (int i = 0; i < count; i++) {
            WeightedShopItem selected = pool.selectSingle();
            if (selected == null) continue;

            Location spawnLoc = sampleRewardDropLocation(center, scatterRadius);
            if (spawnLoc == null || spawnLoc.getWorld() == null) continue;

            if (merchantService.spawnFreePickupForRun(
                    run.getRunId(),
                    spawnLoc,
                    selected.getItemTemplateId(),
                    lifetimeSeconds
            ) != null) {
                spawnLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                        spawnLoc.clone().add(0, 1.0, 0), 8, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    private Location sampleRewardDropLocation(Location center, double radius) {
        Location sampled = plugin.getWorldService().sampleSpawnNear(center, 0.5, Math.max(0.5, radius));
        if (sampled != null) {
            return sampled.clone().add(0, 0.05, 0);
        }
        return center.clone().add(0, 0.05, 0);
    }

    private void triggerChargeCompleteEffects(RunState run, Location center) {
        if (center.getWorld() == null) return;

        ConfigService cfg = plugin.getConfigService();
        if (cfg.isBatteryChargeCompleteFireworkEnabled()) {
            spawnChargeCompleteFirework(center);
        }

        ConfigService.SoundConfig sound = cfg.getBatteryChargeCompleteSound();
        if (sound != null && sound.sound() != null && !sound.sound().isEmpty()) {
            for (UUID participantId : run.getParticipants()) {
                Player p = Bukkit.getPlayer(participantId);
                if (p == null || !p.isOnline()) continue;
                if (!p.getWorld().equals(center.getWorld())) continue;
                p.playSound(center, sound.sound(), sound.volume(), sound.pitch());
            }
        }
    }

    private void spawnChargeCompleteFirework(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        Firework firework = world.spawn(center.clone().add(0, 0.5, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.setPower(0);
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BURST)
                    .withColor(Color.AQUA, Color.LIME)
                    .withFade(Color.WHITE)
                    .flicker(true)
                    .trail(true)
                    .build());
            fw.setFireworkMeta(meta);
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (firework.isValid()) {
                firework.detonate();
            }
        }, 1L);
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
        private long chargedAtMillis;
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
