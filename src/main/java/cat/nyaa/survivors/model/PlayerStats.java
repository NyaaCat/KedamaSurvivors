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

    // ==================== Utility ====================

    /**
     * Checks if the player has any recorded stats (i.e., has played at least one run).
     */
    public boolean hasStats() {
        return runCount > 0 || totalKills > 0 || totalDeaths > 0;
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
}
