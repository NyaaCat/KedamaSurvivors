package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
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

import java.util.*;
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
     *
     * @param killer      The player who got the kill
     * @param archetypeId The archetype of the killed mob
     * @param enemyLevel  The level of the killed mob
     * @param deathLoc    Where the mob died (for nearby sharing)
     */
    public void processKillReward(Player killer, String archetypeId, int enemyLevel, Location deathLoc) {
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

        // Calculate rewards
        int xpReward = archetype.xpBase + (enemyLevel * archetype.xpPerLevel);
        int coinReward = archetype.coinBase + (enemyLevel * archetype.coinPerLevel);
        double permaScoreChance = archetype.permaScoreChance;

        // Award XP to killer
        awardXp(killer, killerState, xpReward, false);

        // Award coins to killer
        if (coinReward > 0) {
            awardCoin(killer, coinReward);
        }

        // Check for perma-score drop
        if (ThreadLocalRandom.current().nextDouble() < permaScoreChance) {
            awardPermaScore(killer, killerState, 1);
        }

        // Share XP with nearby players
        if (config.isXpShareEnabled()) {
            shareXpWithNearby(killer, killerState, xpReward, deathLoc);
        }

        // Update run statistics
        Optional<RunState> runOpt = state.getPlayerRun(killer.getUniqueId());
        runOpt.ifPresent(run -> {
            run.incrementKills();
            run.addXpEarned(xpReward);
            run.addCoinEarned(coinReward);
        });
    }

    /**
     * Awards XP to a player, handling hold logic and upgrade triggers.
     */
    public void awardXp(Player player, PlayerState playerState, int amount, boolean isShared) {
        if (amount <= 0) return;

        // Check if at max level
        if (playerState.isAtMaxLevel()) {
            applyOverflowXp(player, playerState, amount);
            return;
        }

        // If upgrade is pending, buffer the XP
        if (playerState.isUpgradePending()) {
            playerState.setXpHeld(playerState.getXpHeld() + amount);
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
        if (!isShared) {
            i18n.send(player, "info.xp_gained", "amount", amount);
        } else {
            i18n.send(player, "info.xp_shared", "amount", amount);
        }

        // Update sidebar
        plugin.getScoreboardService().updatePlayerSidebar(player);
    }

    /**
     * Awards coins (vanilla items) to a player.
     */
    public void awardCoin(Player player, int amount) {
        if (amount <= 0) return;

        ItemStack coin = createCoinItem(amount);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(coin);

        if (!overflow.isEmpty()) {
            // Add to pending rewards
            Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
            stateOpt.ifPresent(ps -> {
                for (ItemStack item : overflow.values()) {
                    ps.addPendingReward(item);
                }
                i18n.send(player, "info.reward_overflow", "count", overflow.size());
            });
        }

        i18n.send(player, "info.coin_gained", "amount", amount);
    }

    /**
     * Awards perma-score to a player and updates the scoreboard.
     */
    public void awardPermaScore(Player player, PlayerState playerState, int amount) {
        if (amount <= 0) return;

        playerState.setPermaScore(playerState.getPermaScore() + amount);

        // Update scoreboard
        plugin.getScoreboardService().updatePermaScore(player, playerState.getPermaScore());

        i18n.send(player, "info.perma_score_gained", "amount", amount);
    }

    /**
     * Handles overflow XP when at max level.
     */
    private void applyOverflowXp(Player player, PlayerState playerState, int amount) {
        if (!config.isOverflowEnabled()) return;

        int accumulated = playerState.getOverflowXpAccumulated() + amount;
        int xpPerScore = config.getOverflowXpPerPermaScore();

        while (accumulated >= xpPerScore) {
            accumulated -= xpPerScore;
            playerState.setPermaScore(playerState.getPermaScore() + 1);

            // Update scoreboard
            plugin.getScoreboardService().updatePermaScore(player, playerState.getPermaScore());

            if (config.isOverflowNotifyPlayer()) {
                i18n.send(player, "info.overflow_convert", "amount", 1);
            }
        }

        playerState.setOverflowXpAccumulated(accumulated);
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
     */
    private void showUpgradePrompt(Player player, PlayerState playerState) {
        i18n.send(player, "info.upgrade_available");

        // Open upgrade GUI
        UpgradeService upgradeService = plugin.getUpgradeService();
        if (upgradeService != null) {
            upgradeService.showUpgradeGui(player);
        }
    }

    /**
     * Creates a coin ItemStack.
     */
    private ItemStack createCoinItem(int amount) {
        ItemStack coin = new ItemStack(config.getCoinMaterial(), amount);
        ItemMeta meta = coin.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(config.getCoinDisplayName()));
            if (config.getCoinCustomModelData() > 0) {
                meta.setCustomModelData(config.getCoinCustomModelData());
            }
            coin.setItemMeta(meta);
        }
        return coin;
    }

    /**
     * Creates a default archetype config for fallback.
     */
    private EnemyArchetypeConfig createDefaultArchetype() {
        EnemyArchetypeConfig arch = new EnemyArchetypeConfig();
        arch.archetypeId = "default";
        arch.enemyType = "minecraft:zombie";
        arch.weight = 1.0;
        arch.xpBase = 10;
        arch.xpPerLevel = 5;
        arch.coinBase = 1;
        arch.coinPerLevel = 1;
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

        // Recalculate XP required for next level
        playerState.setXpRequired(calculateXpRequired(playerState.getPlayerLevel()));

        // Apply held XP
        int held = playerState.getXpHeld();
        playerState.setXpHeld(0);

        if (held > 0) {
            awardXp(player, playerState, held, false);
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
