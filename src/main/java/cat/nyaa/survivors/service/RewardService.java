package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
import cat.nyaa.survivors.config.ConfigService.SoundConfig;
import cat.nyaa.survivors.economy.EconomyService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles XP, coin, and perma-score rewards for kills.
 * Manages XP hold logic and nearby player sharing.
 */
public class RewardService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;

    public RewardService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    /**
     * Processes rewards for killing a VRS mob.
     * Backward-compatible overload without mobId.
     *
     * @param killer      The player who got the kill
     * @param archetypeId The archetype of the killed mob
     * @param enemyLevel  The level of the killed mob
     * @param deathLoc    Where the mob died (for nearby sharing)
     */
    public void processKillReward(Player killer, String archetypeId, int enemyLevel, Location deathLoc) {
        processKillReward(killer, archetypeId, enemyLevel, deathLoc, null);
    }

    /**
     * Processes rewards for killing a VRS mob.
     *
     * @param killer      The player who got the kill
     * @param archetypeId The archetype of the killed mob
     * @param enemyLevel  The level of the killed mob
     * @param deathLoc    Where the mob died (for nearby sharing)
     * @param mobId       The UUID of the killed mob (for damage contribution tracking)
     */
    public void processKillReward(Player killer, String archetypeId, int enemyLevel,
                                   Location deathLoc, UUID mobId) {
        Optional<PlayerState> killerStateOpt = state.getPlayer(killer.getUniqueId());
        if (killerStateOpt.isEmpty()) return;

        PlayerState killerState = killerStateOpt.get();
        if (killerState.getMode() != PlayerMode.IN_RUN) return;

        // Get archetype config
        EnemyArchetypeConfig archetype = config.getEnemyArchetypes().get(archetypeId);
        if (archetype == null) {
            // Use default values
            archetype = createDefaultArchetype();
        }

        // Chance-based reward calculation (no level scaling)
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Roll for each reward type independently
        int baseXpReward = random.nextDouble() < archetype.xpChance ? archetype.xpAmount : 0;
        int baseCoinReward = random.nextDouble() < archetype.coinChance ? archetype.coinAmount : 0;
        int basePermaScoreReward = random.nextDouble() < archetype.permaScoreChance ? archetype.permaScoreAmount : 0;

        // Apply score multiplier if enabled
        final int xpReward;
        final int coinReward;
        final int permaScoreReward;
        if (config.isScoreMultiplierEnabled()) {
            int multiplier = config.getScoreMultiplier();
            xpReward = baseXpReward * multiplier;
            coinReward = baseCoinReward * multiplier;
            permaScoreReward = config.isScoreMultiplierAffectsPerma()
                    ? basePermaScoreReward * multiplier
                    : basePermaScoreReward;
        } else {
            xpReward = baseXpReward;
            coinReward = baseCoinReward;
            permaScoreReward = basePermaScoreReward;
        }

        // Debug logging
        if (config.isVerbose()) {
            plugin.getLogger().info("Kill reward: archetype=" + archetypeId + " xp=" + xpReward + " coins=" + coinReward);
        }

        // 1. Process damage contribution rewards FIRST (excludes killer)
        if (config.isDamageContributionEnabled() && xpReward > 0 && mobId != null) {
            processContributorRewards(killer, xpReward, mobId);
        }

        // 2. Award XP to killer (only if rolled)
        if (xpReward > 0) {
            awardXp(killer, killerState, xpReward, false);
        }

        // Award coins to killer (only if rolled)
        if (coinReward > 0) {
            awardCoin(killer, coinReward);
        }

        // Award perma-score (only if rolled)
        if (permaScoreReward > 0) {
            awardPermaScore(killer, killerState, permaScoreReward);
        }

        // 3. Share XP with nearby players (existing proximity-based sharing)
        if (config.isXpShareEnabled() && xpReward > 0) {
            shareXpWithNearby(killer, killerState, xpReward, deathLoc);
        }

        // Update run statistics
        Optional<RunState> runOpt = state.getPlayerRun(killer.getUniqueId());
        runOpt.ifPresent(run -> {
            run.incrementKills();
            run.addXpEarned(xpReward);
            run.addCoinEarned(coinReward);
        });

        // Record kill in stats service
        StatsService statsService = plugin.getStatsService();
        if (statsService != null) {
            statsService.recordKill(killer.getUniqueId());
        }

        // Notify action bar of kill (for streak message on flush)
        if ("ACTIONBAR".equals(config.getRewardDisplayMode())) {
            ActionBarRewardService actionBar = plugin.getActionBarRewardService();
            if (actionBar != null) {
                actionBar.addKill(killer);
            }
        }
    }

    /**
     * Awards XP to all players who contributed damage to a mob.
     * The killer is excluded (they get full rewards separately).
     * Only XP is shared, not coins or perma-score.
     * This works independently of the proximity-based xpShare system.
     *
     * @param killer   The player who killed the mob (excluded from contribution rewards)
     * @param baseXp   The base XP reward of the mob
     * @param mobId    The UUID of the killed mob
     */
    private void processContributorRewards(Player killer, int baseXp, UUID mobId) {
        DamageContributionService contributionService = plugin.getDamageContributionService();
        if (contributionService == null) return;

        Map<UUID, Double> contributors = contributionService.getContributors(mobId);
        if (contributors.isEmpty()) {
            return;
        }

        double sharePercent = config.getDamageContributionPercent();
        int sharedXp = (int) (baseXp * sharePercent);

        // Only award if at least 1 XP
        if (sharedXp <= 0) {
            contributionService.clearMob(mobId);
            return;
        }

        UUID killerId = killer.getUniqueId();

        for (UUID contributorId : contributors.keySet()) {
            // Skip the killer - they get full rewards
            if (contributorId.equals(killerId)) continue;

            // Get the contributor player
            Player contributor = Bukkit.getPlayer(contributorId);
            if (contributor == null || !contributor.isOnline()) continue;

            // Check if contributor is in an active run
            Optional<PlayerState> contributorStateOpt = state.getPlayer(contributorId);
            if (contributorStateOpt.isEmpty()) continue;

            PlayerState contributorState = contributorStateOpt.get();
            if (contributorState.getMode() != PlayerMode.IN_RUN) continue;

            // Award the contribution XP (marked as shared for notification purposes)
            awardXp(contributor, contributorState, sharedXp, true);

            if (config.isVerbose()) {
                plugin.getLogger().info("Damage contribution XP: " + contributor.getName() +
                        " received " + sharedXp + " XP for damaging mob " + mobId);
            }
        }

        // Clean up tracking for this mob
        contributionService.clearMob(mobId);
    }

    /**
     * Awards XP to a player, handling hold logic and upgrade triggers.
     */
    public void awardXp(Player player, PlayerState playerState, int amount, boolean isShared) {
        awardXpInternal(player, playerState, amount, isShared, true);
    }

    /**
     * Internal XP award method with notification control.
     *
     * @param notify If true, notifies player of XP gain via action bar/chat
     */
    private void awardXpInternal(Player player, PlayerState playerState, int amount, boolean isShared, boolean notify) {
        if (amount <= 0) return;

        // Check if at max level
        if (playerState.isAtMaxLevel()) {
            applyOverflowXp(player, playerState, amount);
            return;
        }

        // If upgrade is pending, buffer the XP but still notify
        if (playerState.isUpgradePending()) {
            playerState.setXpHeld(playerState.getXpHeld() + amount);
            // Still notify so player sees XP on action bar
            if (notify) {
                notifyXpGained(player, amount, isShared);
            }
            return;
        }

        // Apply XP to progress
        int newProgress = playerState.getXpProgress() + amount;
        int required = playerState.getXpRequired();

        if (newProgress >= required) {
            // Level up triggered
            int overflow = newProgress - required;
            playerState.setXpProgress(required);
            playerState.setXpHeld(playerState.getXpHeld() + overflow);
            playerState.setUpgradePending(true);

            // Show upgrade prompt
            showUpgradePrompt(player, playerState);
        } else {
            playerState.setXpProgress(newProgress);
        }

        // Notify player of XP gain
        if (notify) {
            notifyXpGained(player, amount, isShared);
        }

        // Update sidebar
        plugin.getScoreboardService().updatePlayerSidebar(player);
    }

    /**
     * Notifies player of XP gain using configured display mode.
     */
    private void notifyXpGained(Player player, int amount, boolean isShared) {
        if ("ACTIONBAR".equals(config.getRewardDisplayMode())) {
            ActionBarRewardService actionBar = plugin.getActionBarRewardService();
            if (actionBar != null) {
                actionBar.addXp(player, amount, isShared);
            }
        } else {
            // CHAT mode: send individual messages
            if (!isShared) {
                i18n.send(player, "info.xp_gained", "amount", amount);
            } else {
                i18n.send(player, "info.xp_shared", "amount", amount);
            }
        }
    }

    /**
     * Awards coins to a player using the configured economy mode.
     */
    public void awardCoin(Player player, int amount) {
        if (amount <= 0) return;

        EconomyService economy = plugin.getEconomyService();
        economy.add(player, amount, "kill_reward");

        notifyCoinGained(player, amount);
    }

    /**
     * Notifies player of coin gain using configured display mode.
     */
    private void notifyCoinGained(Player player, int amount) {
        if ("ACTIONBAR".equals(config.getRewardDisplayMode())) {
            ActionBarRewardService actionBar = plugin.getActionBarRewardService();
            if (actionBar != null) {
                actionBar.addCoins(player, amount);
            }
        } else {
            // CHAT mode: send individual messages
            i18n.send(player, "info.coin_gained", "amount", amount);
        }
    }

    /**
     * Awards perma-score to a player and updates the scoreboard.
     */
    public void awardPermaScore(Player player, PlayerState playerState, int amount) {
        if (amount <= 0) return;

        playerState.setPermaScore(playerState.getPermaScore() + amount);

        // Update scoreboard
        plugin.getScoreboardService().updatePermaScore(player, playerState.getPermaScore());

        notifyPermaScoreGained(player, amount);
    }

    /**
     * Notifies player of perma-score gain using configured display mode.
     */
    private void notifyPermaScoreGained(Player player, int amount) {
        if ("ACTIONBAR".equals(config.getRewardDisplayMode())) {
            ActionBarRewardService actionBar = plugin.getActionBarRewardService();
            if (actionBar != null) {
                actionBar.addPermaScore(player, amount);
            }
        } else {
            // CHAT mode: send individual messages
            i18n.send(player, "info.perma_score_gained", "amount", amount);
        }
    }

    /**
     * Handles overflow XP when at max level.
     * Shows XP notifications, updates scoreboard progress, and increments run level on conversion.
     */
    private void applyOverflowXp(Player player, PlayerState playerState, int amount) {
        if (!config.isOverflowEnabled()) return;

        // Notify XP gain (show on action bar like normal XP)
        notifyXpGained(player, amount, false);

        int xpPerScore = config.getOverflowXpPerPermaScore();
        int accumulated = playerState.getOverflowXpAccumulated() + amount;

        // Set xpRequired to overflow threshold for scoreboard bar calculation
        playerState.setXpRequired(xpPerScore);

        // Convert overflow XP to perma-score when threshold reached
        while (accumulated >= xpPerScore) {
            accumulated -= xpPerScore;
            playerState.setPermaScore(playerState.getPermaScore() + 1);

            // Increment run level on each overflow conversion (like a "level up")
            playerState.setRunLevel(playerState.getRunLevel() + 1);

            // Update scoreboard perma-score
            plugin.getScoreboardService().updatePermaScore(player, playerState.getPermaScore());

            if (config.isOverflowNotifyPlayer()) {
                i18n.send(player, "info.overflow_convert", "amount", 1);
            }
        }

        playerState.setOverflowXpAccumulated(accumulated);

        // Update xpProgress for scoreboard bar display
        playerState.setXpProgress(accumulated);

        // Update sidebar to reflect new progress
        plugin.getScoreboardService().updatePlayerSidebar(player);
    }

    /**
     * Shares XP with nearby players in the same run.
     */
    private void shareXpWithNearby(Player killer, PlayerState killerState, int baseXp, Location deathLoc) {
        double shareRadius = config.getXpShareRadius();
        double sharePercent = config.getXpSharePercent();
        int sharedAmount = (int) (baseXp * sharePercent);

        if (sharedAmount <= 0) return;

        UUID killerRunId = killerState.getRunId();
        if (killerRunId == null) return;

        // Find nearby players in the same run
        Collection<Player> nearbyPlayers = deathLoc.getWorld().getNearbyPlayers(
                deathLoc, shareRadius, shareRadius, shareRadius);

        for (Player nearby : nearbyPlayers) {
            if (nearby.equals(killer)) continue;

            Optional<PlayerState> nearbyStateOpt = state.getPlayer(nearby.getUniqueId());
            if (nearbyStateOpt.isEmpty()) continue;

            PlayerState nearbyState = nearbyStateOpt.get();

            // Must be in the same run
            if (nearbyState.getMode() != PlayerMode.IN_RUN) continue;
            if (!killerRunId.equals(nearbyState.getRunId())) continue;

            awardXp(nearby, nearbyState, sharedAmount, true);
        }
    }

    /**
     * Shows the upgrade selection prompt to a player.
     * Uses chat-based clickable messages instead of GUI.
     */
    private void showUpgradePrompt(Player player, PlayerState playerState) {
        UpgradeService upgradeService = plugin.getUpgradeService();
        if (upgradeService == null) return;

        // Check if both weapon AND helmet are at max level
        if (playerState.isWeaponAtMax() && playerState.isHelmetAtMax()) {
            // Instant perma-score award, no prompt needed
            int reward = config.getUpgradeBothMaxPermaReward();
            String permaName = config.getPermaScoreDisplayName();

            playerState.setPermaScore(playerState.getPermaScore() + reward);
            plugin.getScoreboardService().updatePermaScore(player, playerState.getPermaScore());
            i18n.send(player, "upgrade.both_max_instant", "amount", reward, "perma_name", permaName);

            // Resolve held XP immediately (no pending state)
            playerState.setUpgradePending(false);
            resolveHeldXp(player, playerState);
            return;
        }

        // Set upgrade deadline
        long deadline = System.currentTimeMillis() + config.getUpgradeTimeoutMs();
        playerState.setUpgradeDeadlineMillis(deadline);

        // Determine suggested upgrade
        String suggested = upgradeService.determineSuggestedUpgrade(playerState);
        playerState.setSuggestedUpgrade(suggested);

        // Always send initial prompt via CHAT (clickable messages)
        // Reminders will use the configured displayMode (CHAT or SCOREBOARD)
        upgradeService.sendUpgradePrompt(player, playerState, false);

        // Play upgrade available sound
        SoundConfig sound = config.getSoundUpgradeAvailable();
        if (sound != null) {
            sound.play(player);
        }

        // Update scoreboard to show countdown
        plugin.getScoreboardService().updatePlayerSidebar(player);
    }

    /**
     * Creates a coin ItemStack using EconomyService.
     */
    private ItemStack createCoinItem(int amount) {
        EconomyService economy = plugin.getEconomyService();
        return economy.createCoinItem(amount);
    }

    /**
     * Creates a default archetype config for fallback.
     */
    private EnemyArchetypeConfig createDefaultArchetype() {
        EnemyArchetypeConfig arch = new EnemyArchetypeConfig();
        arch.archetypeId = "default";
        arch.enemyType = "minecraft:zombie";
        arch.weight = 1.0;
        arch.minSpawnLevel = 1;
        // New chance-based format
        arch.xpAmount = 10;
        arch.xpChance = 1.0;
        arch.coinAmount = 1;
        arch.coinChance = 1.0;
        arch.permaScoreAmount = 1;
        arch.permaScoreChance = 0.01;
        return arch;
    }

    /**
     * Calculates the XP required for the next level.
     */
    public int calculateXpRequired(int playerLevel) {
        int base = config.getBaseXpRequired();
        int perLevel = config.getXpPerLevelIncrease();
        double multiplier = config.getXpMultiplierPerLevel();

        return (int) (base + (playerLevel * perLevel) * Math.pow(multiplier, playerLevel));
    }

    /**
     * Resolves held XP after an upgrade choice, potentially triggering chained upgrades.
     */
    public void resolveHeldXp(Player player, PlayerState playerState) {
        playerState.setUpgradePending(false);
        playerState.setXpProgress(0);

        // Increment run level (player leveled up by completing XP bar)
        playerState.setRunLevel(playerState.getRunLevel() + 1);

        // Recalculate XP required based on new run level
        playerState.setXpRequired(calculateXpRequired(playerState.getRunLevel()));

        // Apply held XP (skip notification since it was already shown when earned)
        int held = playerState.getXpHeld();
        playerState.setXpHeld(0);

        if (held > 0) {
            awardXpInternal(player, playerState, held, false, false);
        }

        // Update sidebar
        plugin.getScoreboardService().updatePlayerSidebar(player);
    }

    /**
     * Attempts to deliver pending rewards to a player.
     */
    public void deliverPendingRewards(Player player) {
        Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
        if (stateOpt.isEmpty()) return;

        PlayerState playerState = stateOpt.get();
        List<ItemStack> pending = playerState.getPendingRewards();
        if (pending.isEmpty()) return;

        List<ItemStack> stillPending = new ArrayList<>();

        for (ItemStack item : pending) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            if (!overflow.isEmpty()) {
                stillPending.addAll(overflow.values());
            }
        }

        playerState.clearPendingRewards();
        for (ItemStack item : stillPending) {
            playerState.addPendingReward(item);
        }

        int delivered = pending.size() - stillPending.size();
        if (delivered > 0) {
            i18n.send(player, "info.rewards_delivered", "count", delivered);
        }
    }
}
