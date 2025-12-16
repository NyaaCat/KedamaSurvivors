package cat.nyaa.survivors.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-memory representation of a player's game state.
 * Thread-safe for reads, modifications should be synchronized externally.
 */
public class PlayerState {

    // Identity
    private final UUID uuid;
    private volatile String name;

    // State Machine
    private volatile PlayerMode mode = PlayerMode.LOBBY;

    // Team & Run
    private volatile UUID teamId;
    private volatile UUID runId;
    private volatile UUID lastTeamId;

    // Timing
    private volatile long cooldownUntilMillis;
    private volatile long disconnectedAtMillis;
    private volatile long invulnerableUntilMillis;

    // Ready State
    private volatile boolean ready;

    // Starter Selections (before run)
    private volatile String starterWeaponOptionId;
    private volatile String starterHelmetOptionId;

    // Equipment Tracking (authoritative during run)
    private volatile String weaponGroup;
    private volatile int weaponLevel;
    private volatile String helmetGroup;
    private volatile int helmetLevel;

    // XP System
    private volatile int xpProgress;
    private volatile int xpHeld;
    private volatile int xpRequired = 100;
    private volatile boolean upgradePending;

    // Upgrade selection (chat-based)
    private volatile long upgradeDeadlineMillis;
    private volatile String suggestedUpgrade;  // "power" or "defense"

    // Overflow (when at max level)
    private volatile int overflowXpAccumulated;

    // Economy
    private volatile int permaScore;
    private volatile int coinsEarned;  // Track coins earned this run for scoreboard
    private volatile int balance;  // Internal economy balance (for INTERNAL economy mode)

    // Pending Rewards (thread-safe list)
    private final List<ItemStack> pendingRewards = new ArrayList<>();

    // Max level tracking
    private volatile boolean weaponAtMax;
    private volatile boolean helmetAtMax;

    // Player progression level (increments each upgrade, reset per run)
    private volatile int runLevel = 1;

    // Persistent player statistics
    private final PlayerStats stats = new PlayerStats();

    public PlayerState(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    // ==================== Computed Properties ====================

    /**
     * Calculates the player's effective level based on equipment.
     */
    public int getPlayerLevel() {
        return weaponLevel + helmetLevel;
    }

    /**
     * Checks if both weapon and helmet are at max level.
     */
    public boolean isAtMaxLevel() {
        return weaponAtMax && helmetAtMax;
    }

    /**
     * Checks if the player is within the disconnect grace period.
     */
    public boolean isWithinGracePeriod(long graceMillis) {
        if (disconnectedAtMillis == 0) return false;
        return System.currentTimeMillis() - disconnectedAtMillis < graceMillis;
    }

    /**
     * Checks if the player is currently invulnerable.
     */
    public boolean isInvulnerable() {
        return invulnerableUntilMillis > System.currentTimeMillis();
    }

    /**
     * Checks if the player is on cooldown.
     */
    public boolean isOnCooldown() {
        return cooldownUntilMillis > System.currentTimeMillis();
    }

    /**
     * Gets remaining seconds until upgrade auto-selection.
     * Returns 0 if no upgrade is pending or deadline has passed.
     */
    public int getUpgradeRemainingSeconds() {
        if (upgradeDeadlineMillis == 0) return 0;
        long remaining = upgradeDeadlineMillis - System.currentTimeMillis();
        return Math.max(0, (int) (remaining / 1000));
    }

    /**
     * Gets remaining cooldown time in seconds.
     */
    public long getCooldownRemainingSeconds() {
        long remaining = cooldownUntilMillis - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    /**
     * Checks if player has selected both starter weapon and helmet.
     */
    public boolean hasSelectedStarters() {
        return starterWeaponOptionId != null && starterHelmetOptionId != null;
    }

    // ==================== State Management ====================

    /**
     * Resets run-related state (called on death or run end).
     */
    public void resetRunState() {
        xpProgress = 0;
        xpHeld = 0;
        upgradePending = false;
        upgradeDeadlineMillis = 0;
        suggestedUpgrade = null;
        overflowXpAccumulated = 0;
        weaponGroup = null;
        weaponLevel = 0;
        helmetGroup = null;
        helmetLevel = 0;
        weaponAtMax = false;
        helmetAtMax = false;
        runLevel = 1;  // Reset to 1 at start of each run
        coinsEarned = 0;  // Reset coins earned
        runId = null;
        // Reset starter selections so player can re-select after death
        starterWeaponOptionId = null;
        starterHelmetOptionId = null;
        ready = false;
    }

    /**
     * Resets all state for a fresh start.
     */
    public void resetAll() {
        resetRunState();
        mode = PlayerMode.LOBBY;
        ready = false;
        starterWeaponOptionId = null;
        starterHelmetOptionId = null;
        cooldownUntilMillis = 0;
        disconnectedAtMillis = 0;
        invulnerableUntilMillis = 0;
        synchronized (pendingRewards) {
            pendingRewards.clear();
        }
    }

    // ==================== Pending Rewards ====================

    public void addPendingReward(ItemStack item) {
        synchronized (pendingRewards) {
            pendingRewards.add(item.clone());
        }
    }

    public List<ItemStack> getPendingRewards() {
        synchronized (pendingRewards) {
            return new ArrayList<>(pendingRewards);
        }
    }

    public void clearPendingRewards() {
        synchronized (pendingRewards) {
            pendingRewards.clear();
        }
    }

    public void removePendingReward(ItemStack item) {
        synchronized (pendingRewards) {
            pendingRewards.remove(item);
        }
    }

    public int getPendingRewardCount() {
        synchronized (pendingRewards) {
            return pendingRewards.size();
        }
    }

    // ==================== Getters and Setters ====================

    public UUID getUuid() { return uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PlayerMode getMode() { return mode; }
    public void setMode(PlayerMode mode) { this.mode = mode; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public UUID getLastTeamId() { return lastTeamId; }
    public void setLastTeamId(UUID lastTeamId) { this.lastTeamId = lastTeamId; }

    public long getCooldownUntilMillis() { return cooldownUntilMillis; }
    public void setCooldownUntilMillis(long cooldownUntilMillis) { this.cooldownUntilMillis = cooldownUntilMillis; }

    public long getDisconnectedAtMillis() { return disconnectedAtMillis; }
    public void setDisconnectedAtMillis(long disconnectedAtMillis) { this.disconnectedAtMillis = disconnectedAtMillis; }

    public long getInvulnerableUntilMillis() { return invulnerableUntilMillis; }
    public void setInvulnerableUntilMillis(long invulnerableUntilMillis) { this.invulnerableUntilMillis = invulnerableUntilMillis; }

    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    public String getStarterWeaponOptionId() { return starterWeaponOptionId; }
    public void setStarterWeaponOptionId(String starterWeaponOptionId) { this.starterWeaponOptionId = starterWeaponOptionId; }

    public String getStarterHelmetOptionId() { return starterHelmetOptionId; }
    public void setStarterHelmetOptionId(String starterHelmetOptionId) { this.starterHelmetOptionId = starterHelmetOptionId; }

    public String getWeaponGroup() { return weaponGroup; }
    public void setWeaponGroup(String weaponGroup) { this.weaponGroup = weaponGroup; }

    public int getWeaponLevel() { return weaponLevel; }
    public void setWeaponLevel(int weaponLevel) { this.weaponLevel = weaponLevel; }

    public String getHelmetGroup() { return helmetGroup; }
    public void setHelmetGroup(String helmetGroup) { this.helmetGroup = helmetGroup; }

    public int getHelmetLevel() { return helmetLevel; }
    public void setHelmetLevel(int helmetLevel) { this.helmetLevel = helmetLevel; }

    public int getXpProgress() { return xpProgress; }
    public void setXpProgress(int xpProgress) { this.xpProgress = xpProgress; }

    public int getXpHeld() { return xpHeld; }
    public void setXpHeld(int xpHeld) { this.xpHeld = xpHeld; }

    public int getXpRequired() { return xpRequired; }
    public void setXpRequired(int xpRequired) { this.xpRequired = xpRequired; }

    public boolean isUpgradePending() { return upgradePending; }
    public void setUpgradePending(boolean upgradePending) { this.upgradePending = upgradePending; }

    public long getUpgradeDeadlineMillis() { return upgradeDeadlineMillis; }
    public void setUpgradeDeadlineMillis(long upgradeDeadlineMillis) { this.upgradeDeadlineMillis = upgradeDeadlineMillis; }

    public String getSuggestedUpgrade() { return suggestedUpgrade; }
    public void setSuggestedUpgrade(String suggestedUpgrade) { this.suggestedUpgrade = suggestedUpgrade; }

    public int getOverflowXpAccumulated() { return overflowXpAccumulated; }
    public void setOverflowXpAccumulated(int overflowXpAccumulated) { this.overflowXpAccumulated = overflowXpAccumulated; }

    public int getPermaScore() { return permaScore; }
    public void setPermaScore(int permaScore) { this.permaScore = permaScore; }

    public boolean isWeaponAtMax() { return weaponAtMax; }
    public void setWeaponAtMax(boolean weaponAtMax) { this.weaponAtMax = weaponAtMax; }

    public boolean isHelmetAtMax() { return helmetAtMax; }
    public void setHelmetAtMax(boolean helmetAtMax) { this.helmetAtMax = helmetAtMax; }

    public int getRunLevel() { return runLevel; }
    public void setRunLevel(int runLevel) { this.runLevel = runLevel; }

    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }

    public PlayerStats getStats() { return stats; }
}
