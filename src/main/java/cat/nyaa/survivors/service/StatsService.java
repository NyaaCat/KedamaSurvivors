package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.PlayerStats;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking player statistics.
 * Maintains transient run-session data and updates persistent PlayerStats.
 * Leaves persistence to PersistenceService.
 */
public class StatsService {

    private final KedamaSurvivorsPlugin plugin;
    private final StateService state;

    // Transient run data - cleared when run ends
    private final Map<UUID, Long> runStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> runKillCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> runDeathCounts = new ConcurrentHashMap<>();

    public StatsService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.state = plugin.getStateService();
    }

    // ==================== Kill Tracking ====================

    /**
     * Records a kill for a player. Increments total kills.
     *
     * @param playerId The player who got the kill
     */
    public void recordKill(UUID playerId) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();

        // Increment total kills
        stats.incrementTotalKills();

        // Increment run kill count
        runKillCounts.merge(playerId, 1, Integer::sum);
    }

    /**
     * Records a kill streak from an aggregation window.
     * Updates the persistent longest kill streak if this beats the record.
     *
     * @param playerId The player
     * @param streak   The kill streak count from the aggregation window
     */
    public void recordKillStreak(UUID playerId, int streak) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();
        stats.updateLongestKillStreak(streak);
    }

    // ==================== Damage Tracking ====================

    /**
     * Records damage dealt by a player.
     *
     * @param playerId The player who dealt damage
     * @param amount   The damage amount
     */
    public void recordDamageDealt(UUID playerId, double amount) {
        if (amount <= 0) return;

        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();

        // Update highest single-hit damage
        stats.updateHighestDamageDealt(amount);

        // Add to total damage dealt
        stats.addDamageDealt(amount);
    }

    /**
     * Records damage taken by a player.
     *
     * @param playerId The player who took damage
     * @param amount   The damage amount
     */
    public void recordDamageTaken(UUID playerId, double amount) {
        if (amount <= 0) return;

        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();

        // Update highest single-hit damage taken
        stats.updateHighestDamageTaken(amount);

        // Add to total damage taken
        stats.addDamageTaken(amount);
    }

    // ==================== Death Tracking ====================

    /**
     * Records a death for a player. Increments total deaths.
     *
     * @param playerId The player who died
     */
    public void recordDeath(UUID playerId) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();

        // Increment total deaths
        stats.incrementTotalDeaths();

        // Increment run death count
        runDeathCounts.merge(playerId, 1, Integer::sum);
    }

    // ==================== Run Lifecycle ====================

    /**
     * Records the start of a run for a player.
     *
     * @param playerId The player starting the run
     */
    public void recordRunStart(UUID playerId) {
        runStartTimes.put(playerId, System.currentTimeMillis());
        runKillCounts.put(playerId, 0);
        runDeathCounts.put(playerId, 0);
    }

    /**
     * Records the end of a run for a player. Finalizes run stats.
     * Returns false if there is no active tracked run for this player.
     */
    public boolean recordRunEnd(UUID playerId) {
        return finalizeRun(playerId, false);
    }

    /**
     * Finalizes a tracked run as failed (quit/disconnect wipe/failure cases).
     * Returns false if there is no active tracked run for this player.
     */
    public boolean recordRunFailure(UUID playerId) {
        return finalizeRun(playerId, true);
    }

    private boolean finalizeRun(UUID playerId, boolean failed) {
        Long startTime = runStartTimes.get(playerId);
        if (startTime == null) {
            // Already finalized (or never started) - avoid double counting.
            return false;
        }

        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) {
            clearTransientData(playerId);
            return false;
        }

        PlayerStats stats = stateOpt.get().getStats();

        long durationSeconds = Math.max(0L, (System.currentTimeMillis() - startTime) / 1000L);
        stats.addRunTime(durationSeconds);
        stats.updateLongestRunTime(durationSeconds);
        stats.updateShortestRunTime(durationSeconds);

        int killsThisRun = runKillCounts.getOrDefault(playerId, 0);
        stats.updateHighestKillsInRun(killsThisRun);

        int deathsThisRun = runDeathCounts.getOrDefault(playerId, 0);
        stats.updateMostDeathsInRun(deathsThisRun);

        stats.incrementRunCount();
        if (failed) {
            stats.incrementFailedRunCount();
        }

        clearTransientData(playerId);
        return true;
    }

    // ==================== Level Tracking ====================

    /**
     * Records a level up for a player.
     *
     * @param playerId The player who leveled up
     * @param newLevel The new level
     */
    public void recordLevelUp(UUID playerId, int newLevel) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();
        stats.updateHighestPlayerLevel(newLevel);
    }

    /**
     * Records the team's overall level for a player.
     *
     * @param playerId  The player
     * @param teamLevel The team's combined level
     */
    public void recordTeamLevel(UUID playerId, int teamLevel) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();
        stats.updateHighestTeamLevel(teamLevel);
    }

    // ==================== Segmented Progression Tracking ====================

    /**
     * Records completion of one battery objective by a player.
     */
    public void recordBatteryCompleted(UUID playerId) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;
        stateOpt.get().getStats().incrementTotalBatteriesCompleted();
    }

    /**
     * Records stage clear stats and stage-clear reward totals.
     *
     * @param stageIndexOneBased 1-based stage index for user-facing progression
     */
    public void recordStageClear(UUID playerId, int stageIndexOneBased, int rewardCoins, int rewardPermaScore) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();
        stats.incrementTotalStageClears();
        stats.updateHighestStageCleared(stageIndexOneBased);
        stats.addStageRewardCoins(rewardCoins);
        stats.addStageRewardPermaScore(rewardPermaScore);
    }

    /**
     * Records reward totals granted by progression systems (e.g. final bonus).
     */
    public void recordStageReward(UUID playerId, int rewardCoins, int rewardPermaScore) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;

        PlayerStats stats = stateOpt.get().getStats();
        stats.addStageRewardCoins(rewardCoins);
        stats.addStageRewardPermaScore(rewardPermaScore);
    }

    /**
     * Records one full campaign completion (final stage clear).
     */
    public void recordCampaignCompletion(UUID playerId) {
        Optional<PlayerState> stateOpt = state.getPlayer(playerId);
        if (stateOpt.isEmpty()) return;
        stateOpt.get().getStats().incrementCampaignCompletions();
    }

    // ==================== Utility ====================

    /**
     * Clears transient run data for a player.
     */
    public void clearTransientData(UUID playerId) {
        runStartTimes.remove(playerId);
        runKillCounts.remove(playerId);
        runDeathCounts.remove(playerId);
    }

    /**
     * Clears all transient data (e.g., on plugin disable).
     */
    public void clearAllTransientData() {
        runStartTimes.clear();
        runKillCounts.clear();
        runDeathCounts.clear();
    }

    /**
     * Gets the current run kill count for a player.
     */
    public int getRunKillCount(UUID playerId) {
        return runKillCounts.getOrDefault(playerId, 0);
    }

    /**
     * Gets the current run death count for a player.
     */
    public int getRunDeathCount(UUID playerId) {
        return runDeathCounts.getOrDefault(playerId, 0);
    }

    /**
     * Gets the run start time for a player.
     */
    public Long getRunStartTime(UUID playerId) {
        return runStartTimes.get(playerId);
    }

    /**
     * Checks if a player is currently in a tracked run.
     */
    public boolean isInRun(UUID playerId) {
        return runStartTimes.containsKey(playerId);
    }
}
