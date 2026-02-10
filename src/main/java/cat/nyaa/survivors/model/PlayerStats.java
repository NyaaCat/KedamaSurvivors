package cat.nyaa.survivors.model;

/**
 * Persistent player statistics that are saved across sessions.
 * All fields are updated via StatsService methods that only write when values improve/accumulate.
 */
public class PlayerStats {

    // Time tracking (in seconds)
    private long totalRunTimeSeconds = 0;
    private long longestRunTimeSeconds = 0;
    private long shortestRunTimeSeconds = Long.MAX_VALUE;

    // Kill tracking
    private int totalKills = 0;
    private int longestKillStreak = 0;
    private int highestKillsInRun = 0;

    // Damage dealt tracking
    private double highestDamageDealt = 0;  // single hit
    private double totalDamageDealt = 0;

    // Damage taken tracking
    private double highestDamageTaken = 0;  // single hit
    private double totalDamageTaken = 0;

    // Level tracking
    private int highestPlayerLevel = 0;
    private int highestTeamLevel = 0;

    // Death/Run tracking
    private int totalDeaths = 0;
    private int mostDeathsInRun = 0;
    private int runCount = 0;
    private int failedRunCount = 0;

    // Segmented progression tracking
    private int totalBatteriesCompleted = 0;
    private int totalStageClears = 0;
    private int highestStageCleared = 0;
    private int campaignCompletions = 0;
    private long totalStageRewardCoins = 0;
    private long totalStageRewardPermaScore = 0;

    // ==================== Time Stats ====================

    public long getTotalRunTimeSeconds() {
        return totalRunTimeSeconds;
    }

    public void addRunTime(long seconds) {
        if (seconds > 0) {
            totalRunTimeSeconds += seconds;
        }
    }

    public long getLongestRunTimeSeconds() {
        return longestRunTimeSeconds;
    }

    public void updateLongestRunTime(long seconds) {
        if (seconds > longestRunTimeSeconds) {
            longestRunTimeSeconds = seconds;
        }
    }

    public long getShortestRunTimeSeconds() {
        return shortestRunTimeSeconds;
    }

    public void updateShortestRunTime(long seconds) {
        if (seconds > 0 && seconds < shortestRunTimeSeconds) {
            shortestRunTimeSeconds = seconds;
        }
    }

    // ==================== Kill Stats ====================

    public int getTotalKills() {
        return totalKills;
    }

    public void incrementTotalKills() {
        totalKills++;
    }

    public int getLongestKillStreak() {
        return longestKillStreak;
    }

    public void updateLongestKillStreak(int streak) {
        if (streak > longestKillStreak) {
            longestKillStreak = streak;
        }
    }

    public int getHighestKillsInRun() {
        return highestKillsInRun;
    }

    public void updateHighestKillsInRun(int kills) {
        if (kills > highestKillsInRun) {
            highestKillsInRun = kills;
        }
    }

    // ==================== Damage Dealt Stats ====================

    public double getHighestDamageDealt() {
        return highestDamageDealt;
    }

    public void updateHighestDamageDealt(double damage) {
        if (damage > highestDamageDealt) {
            highestDamageDealt = damage;
        }
    }

    public double getTotalDamageDealt() {
        return totalDamageDealt;
    }

    public void addDamageDealt(double damage) {
        if (damage > 0) {
            totalDamageDealt += damage;
        }
    }

    // ==================== Damage Taken Stats ====================

    public double getHighestDamageTaken() {
        return highestDamageTaken;
    }

    public void updateHighestDamageTaken(double damage) {
        if (damage > highestDamageTaken) {
            highestDamageTaken = damage;
        }
    }

    public double getTotalDamageTaken() {
        return totalDamageTaken;
    }

    public void addDamageTaken(double damage) {
        if (damage > 0) {
            totalDamageTaken += damage;
        }
    }

    // ==================== Level Stats ====================

    public int getHighestPlayerLevel() {
        return highestPlayerLevel;
    }

    public void updateHighestPlayerLevel(int level) {
        if (level > highestPlayerLevel) {
            highestPlayerLevel = level;
        }
    }

    public int getHighestTeamLevel() {
        return highestTeamLevel;
    }

    public void updateHighestTeamLevel(int level) {
        if (level > highestTeamLevel) {
            highestTeamLevel = level;
        }
    }

    // ==================== Death/Run Stats ====================

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void incrementTotalDeaths() {
        totalDeaths++;
    }

    public int getMostDeathsInRun() {
        return mostDeathsInRun;
    }

    public void updateMostDeathsInRun(int deaths) {
        if (deaths > mostDeathsInRun) {
            mostDeathsInRun = deaths;
        }
    }

    public int getRunCount() {
        return runCount;
    }

    public void incrementRunCount() {
        runCount++;
    }

    public int getFailedRunCount() {
        return failedRunCount;
    }

    public void incrementFailedRunCount() {
        failedRunCount++;
    }

    // ==================== Segmented Progression Stats ====================

    public int getTotalBatteriesCompleted() {
        return totalBatteriesCompleted;
    }

    public void incrementTotalBatteriesCompleted() {
        totalBatteriesCompleted++;
    }

    public int getTotalStageClears() {
        return totalStageClears;
    }

    public void incrementTotalStageClears() {
        totalStageClears++;
    }

    public int getHighestStageCleared() {
        return highestStageCleared;
    }

    public void updateHighestStageCleared(int stageIndexOneBased) {
        if (stageIndexOneBased > highestStageCleared) {
            highestStageCleared = stageIndexOneBased;
        }
    }

    public int getCampaignCompletions() {
        return campaignCompletions;
    }

    public void incrementCampaignCompletions() {
        campaignCompletions++;
    }

    public long getTotalStageRewardCoins() {
        return totalStageRewardCoins;
    }

    public void addStageRewardCoins(int coins) {
        if (coins > 0) {
            totalStageRewardCoins += coins;
        }
    }

    public long getTotalStageRewardPermaScore() {
        return totalStageRewardPermaScore;
    }

    public void addStageRewardPermaScore(int permaScore) {
        if (permaScore > 0) {
            totalStageRewardPermaScore += permaScore;
        }
    }

    // ==================== Utility ====================

    /**
     * Checks if the player has any recorded stats (i.e., has played at least one run).
     */
    public boolean hasStats() {
        return runCount > 0 || totalKills > 0 || totalDeaths > 0
                || totalStageClears > 0 || campaignCompletions > 0;
    }

    /**
     * Creates a copy of this stats object.
     */
    public PlayerStats copy() {
        PlayerStats copy = new PlayerStats();
        copy.totalRunTimeSeconds = this.totalRunTimeSeconds;
        copy.longestRunTimeSeconds = this.longestRunTimeSeconds;
        copy.shortestRunTimeSeconds = this.shortestRunTimeSeconds;
        copy.totalKills = this.totalKills;
        copy.longestKillStreak = this.longestKillStreak;
        copy.highestKillsInRun = this.highestKillsInRun;
        copy.highestDamageDealt = this.highestDamageDealt;
        copy.totalDamageDealt = this.totalDamageDealt;
        copy.highestDamageTaken = this.highestDamageTaken;
        copy.totalDamageTaken = this.totalDamageTaken;
        copy.highestPlayerLevel = this.highestPlayerLevel;
        copy.highestTeamLevel = this.highestTeamLevel;
        copy.totalDeaths = this.totalDeaths;
        copy.mostDeathsInRun = this.mostDeathsInRun;
        copy.runCount = this.runCount;
        copy.failedRunCount = this.failedRunCount;
        copy.totalBatteriesCompleted = this.totalBatteriesCompleted;
        copy.totalStageClears = this.totalStageClears;
        copy.highestStageCleared = this.highestStageCleared;
        copy.campaignCompletions = this.campaignCompletions;
        copy.totalStageRewardCoins = this.totalStageRewardCoins;
        copy.totalStageRewardPermaScore = this.totalStageRewardPermaScore;
        return copy;
    }

    // ==================== Direct Setters (for persistence loading) ====================

    public void setTotalRunTimeSeconds(long totalRunTimeSeconds) {
        this.totalRunTimeSeconds = totalRunTimeSeconds;
    }

    public void setLongestRunTimeSeconds(long longestRunTimeSeconds) {
        this.longestRunTimeSeconds = longestRunTimeSeconds;
    }

    public void setShortestRunTimeSeconds(long shortestRunTimeSeconds) {
        this.shortestRunTimeSeconds = shortestRunTimeSeconds;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
    }

    public void setLongestKillStreak(int longestKillStreak) {
        this.longestKillStreak = longestKillStreak;
    }

    public void setHighestKillsInRun(int highestKillsInRun) {
        this.highestKillsInRun = highestKillsInRun;
    }

    public void setHighestDamageDealt(double highestDamageDealt) {
        this.highestDamageDealt = highestDamageDealt;
    }

    public void setTotalDamageDealt(double totalDamageDealt) {
        this.totalDamageDealt = totalDamageDealt;
    }

    public void setHighestDamageTaken(double highestDamageTaken) {
        this.highestDamageTaken = highestDamageTaken;
    }

    public void setTotalDamageTaken(double totalDamageTaken) {
        this.totalDamageTaken = totalDamageTaken;
    }

    public void setHighestPlayerLevel(int highestPlayerLevel) {
        this.highestPlayerLevel = highestPlayerLevel;
    }

    public void setHighestTeamLevel(int highestTeamLevel) {
        this.highestTeamLevel = highestTeamLevel;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
    }

    public void setMostDeathsInRun(int mostDeathsInRun) {
        this.mostDeathsInRun = mostDeathsInRun;
    }

    public void setRunCount(int runCount) {
        this.runCount = runCount;
    }

    public void setFailedRunCount(int failedRunCount) {
        this.failedRunCount = failedRunCount;
    }

    public void setTotalBatteriesCompleted(int totalBatteriesCompleted) {
        this.totalBatteriesCompleted = totalBatteriesCompleted;
    }

    public void setTotalStageClears(int totalStageClears) {
        this.totalStageClears = totalStageClears;
    }

    public void setHighestStageCleared(int highestStageCleared) {
        this.highestStageCleared = highestStageCleared;
    }

    public void setCampaignCompletions(int campaignCompletions) {
        this.campaignCompletions = campaignCompletions;
    }

    public void setTotalStageRewardCoins(long totalStageRewardCoins) {
        this.totalStageRewardCoins = totalStageRewardCoins;
    }

    public void setTotalStageRewardPermaScore(long totalStageRewardPermaScore) {
        this.totalStageRewardPermaScore = totalStageRewardPermaScore;
    }
}
