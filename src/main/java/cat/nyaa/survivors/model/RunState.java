package cat.nyaa.survivors.model;

import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory representation of an active game run.
 * Thread-safe for concurrent access.
 */
public class RunState {

    // Identity
    private final UUID runId;
    private final UUID teamId;
    private final String worldName;

    // Status
    private volatile RunStatus status = RunStatus.STARTING;

    // Timing
    private final long startedAtMillis;
    private volatile long endedAtMillis;

    // Players in this run (subset of team members who joined)
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> deathCounts = new ConcurrentHashMap<>();

    // Spawn management
    private final List<Location> spawnPoints = Collections.synchronizedList(new ArrayList<>());
    private volatile int spawnIndex = 0;

    // Statistics
    private volatile int totalKills;
    private volatile int totalCoinsCollected;
    private volatile int totalXpCollected;
    private volatile int waveNumber;

    // Enemy tracking
    private final Set<UUID> activeEnemies = ConcurrentHashMap.newKeySet();

    public RunState(UUID runId, UUID teamId, String worldName) {
        this.runId = runId;
        this.teamId = teamId;
        this.worldName = worldName;
        this.startedAtMillis = System.currentTimeMillis();
    }

    // ==================== Participant Management ====================

    public void addParticipant(UUID playerId) {
        participants.add(playerId);
        alivePlayers.add(playerId);
        deathCounts.putIfAbsent(playerId, 0);
    }

    public void removeParticipant(UUID playerId) {
        participants.remove(playerId);
        alivePlayers.remove(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(new HashSet<>(participants));
    }

    public int getParticipantCount() {
        return participants.size();
    }

    // ==================== Life/Death Tracking ====================

    public void markDead(UUID playerId) {
        alivePlayers.remove(playerId);
        deathCounts.merge(playerId, 1, Integer::sum);
    }

    public void markAlive(UUID playerId) {
        if (participants.contains(playerId)) {
            alivePlayers.add(playerId);
        }
    }

    public boolean isAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    public Set<UUID> getAlivePlayers() {
        return Collections.unmodifiableSet(new HashSet<>(alivePlayers));
    }

    public int getAliveCount() {
        return alivePlayers.size();
    }

    public int getDeathCount(UUID playerId) {
        return deathCounts.getOrDefault(playerId, 0);
    }

    public boolean isTeamWiped() {
        return alivePlayers.isEmpty();
    }

    // ==================== Spawn Point Management ====================

    public void addSpawnPoint(Location location) {
        spawnPoints.add(location.clone());
    }

    public void setSpawnPoints(Collection<Location> locations) {
        spawnPoints.clear();
        locations.forEach(loc -> spawnPoints.add(loc.clone()));
    }

    /**
     * Gets the next spawn point in round-robin fashion.
     */
    public Location getNextSpawnPoint() {
        if (spawnPoints.isEmpty()) return null;

        synchronized (spawnPoints) {
            Location spawn = spawnPoints.get(spawnIndex % spawnPoints.size());
            spawnIndex++;
            return spawn.clone();
        }
    }

    /**
     * Gets a random spawn point.
     */
    public Location getRandomSpawnPoint() {
        if (spawnPoints.isEmpty()) return null;

        synchronized (spawnPoints) {
            int index = (int) (Math.random() * spawnPoints.size());
            return spawnPoints.get(index).clone();
        }
    }

    public List<Location> getSpawnPoints() {
        synchronized (spawnPoints) {
            List<Location> copy = new ArrayList<>();
            spawnPoints.forEach(loc -> copy.add(loc.clone()));
            return copy;
        }
    }

    // ==================== Enemy Tracking ====================

    public void addEnemy(UUID entityId) {
        activeEnemies.add(entityId);
    }

    public void removeEnemy(UUID entityId) {
        activeEnemies.remove(entityId);
    }

    public boolean isEnemy(UUID entityId) {
        return activeEnemies.contains(entityId);
    }

    public int getActiveEnemyCount() {
        return activeEnemies.size();
    }

    public Set<UUID> getActiveEnemies() {
        return Collections.unmodifiableSet(new HashSet<>(activeEnemies));
    }

    public void clearEnemies() {
        activeEnemies.clear();
    }

    // ==================== Statistics ====================

    public void incrementKills() {
        totalKills++;
    }

    public void addKills(int count) {
        totalKills += count;
    }

    public void addCoins(int amount) {
        totalCoinsCollected += amount;
    }

    public void addXp(int amount) {
        totalXpCollected += amount;
    }

    public void addXpEarned(int amount) {
        totalXpCollected += amount;
    }

    public void addCoinEarned(int amount) {
        totalCoinsCollected += amount;
    }

    public void incrementWave() {
        waveNumber++;
    }

    // ==================== Level Calculation ====================

    /**
     * Calculates the average player level for spawn scaling.
     * @param playerLevelProvider function to get a player's level by UUID
     * @return average level of participants
     */
    public double getAveragePlayerLevel(java.util.function.ToIntFunction<UUID> playerLevelProvider) {
        if (participants.isEmpty()) return 0;

        int totalLevel = 0;
        for (UUID playerId : participants) {
            totalLevel += playerLevelProvider.applyAsInt(playerId);
        }
        return (double) totalLevel / participants.size();
    }

    /**
     * Gets the elapsed time in seconds since run started.
     */
    public long getElapsedSeconds() {
        long endTime = endedAtMillis > 0 ? endedAtMillis : System.currentTimeMillis();
        return (endTime - startedAtMillis) / 1000;
    }

    /**
     * Gets the elapsed time formatted as MM:SS.
     */
    public String getElapsedFormatted() {
        long seconds = getElapsedSeconds();
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // ==================== Status Management ====================

    public void start() {
        this.status = RunStatus.ACTIVE;
    }

    public void end() {
        this.status = RunStatus.ENDING;
        this.endedAtMillis = System.currentTimeMillis();
    }

    public void complete() {
        this.status = RunStatus.COMPLETED;
        if (endedAtMillis == 0) {
            this.endedAtMillis = System.currentTimeMillis();
        }
    }

    public boolean isActive() {
        return status == RunStatus.ACTIVE;
    }

    public boolean isEnded() {
        return status == RunStatus.ENDING || status == RunStatus.COMPLETED;
    }

    // ==================== Getters ====================

    public UUID getRunId() { return runId; }
    public UUID getTeamId() { return teamId; }
    public String getWorldName() { return worldName; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public long getStartedAtMillis() { return startedAtMillis; }
    public long getEndedAtMillis() { return endedAtMillis; }

    public int getTotalKills() { return totalKills; }
    public int getTotalCoinsCollected() { return totalCoinsCollected; }
    public int getTotalXpCollected() { return totalXpCollected; }
    public int getWaveNumber() { return waveNumber; }
}
