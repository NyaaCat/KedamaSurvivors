package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.SoundConfig;
import cat.nyaa.survivors.i18n.I18nService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages reward aggregation and action bar display.
 * Stacks consecutive rewards within a configurable time window.
 * Only used in ACTIONBAR display mode - CHAT mode bypasses this service.
 */
public class ActionBarRewardService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;

    // Pending rewards per player
    private final Map<UUID, PendingRewards> pendingRewards = new ConcurrentHashMap<>();

    // Scheduled flush tasks per player
    private final Map<UUID, BukkitTask> flushTasks = new ConcurrentHashMap<>();

    public ActionBarRewardService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
    }

    /**
     * Adds XP reward to the pending stack.
     */
    public void addXp(Player player, int amount, boolean isShared) {
        if (amount <= 0) return;

        PendingRewards rewards = getOrCreatePending(player.getUniqueId());
        if (isShared) {
            rewards.sharedXp += amount;
        } else {
            rewards.xp += amount;
        }

        scheduleFlush(player);
        playSound(player, config.getSoundXpGained());
    }

    /**
     * Adds coin reward to the pending stack.
     */
    public void addCoins(Player player, int amount) {
        if (amount <= 0) return;

        PendingRewards rewards = getOrCreatePending(player.getUniqueId());
        rewards.coins += amount;

        scheduleFlush(player);
        playSound(player, config.getSoundCoinGained());
    }

    /**
     * Adds perma-score reward to the pending stack.
     */
    public void addPermaScore(Player player, int amount) {
        if (amount <= 0) return;

        PendingRewards rewards = getOrCreatePending(player.getUniqueId());
        rewards.permaScore += amount;

        scheduleFlush(player);
        playSound(player, config.getSoundPermaScoreGained());
    }

    /**
     * Records a kill for display on flush.
     * Called from RewardService when a kill is made.
     *
     * @param player The player who got the kill
     */
    public void addKill(Player player) {
        PendingRewards rewards = getOrCreatePending(player.getUniqueId());
        rewards.killCount++;

        // Reschedule flush to extend the window
        scheduleFlush(player);
    }

    /**
     * Forces immediate display of pending rewards (e.g., on disconnect).
     */
    public void flushNow(UUID playerId) {
        cancelFlushTask(playerId);
        PendingRewards rewards = pendingRewards.remove(playerId);
        if (rewards != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendActionBar(player, rewards);
            }
        }
    }

    /**
     * Clears pending rewards for a player (e.g., on run end).
     */
    public void clear(UUID playerId) {
        cancelFlushTask(playerId);
        pendingRewards.remove(playerId);
    }

    /**
     * Stops all pending tasks (for shutdown).
     */
    public void stop() {
        for (BukkitTask task : flushTasks.values()) {
            task.cancel();
        }
        flushTasks.clear();
        pendingRewards.clear();
    }

    // ==================== Internal Methods ====================

    private PendingRewards getOrCreatePending(UUID playerId) {
        return pendingRewards.computeIfAbsent(playerId, k -> new PendingRewards());
    }

    private void scheduleFlush(Player player) {
        UUID playerId = player.getUniqueId();

        // Cancel existing task
        cancelFlushTask(playerId);

        // Schedule new flush task
        long delayTicks = config.getRewardStackingTimeoutSeconds() * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            flushTasks.remove(playerId);
            PendingRewards rewards = pendingRewards.remove(playerId);
            if (rewards != null) {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    // Don't call sendActionBar here - it was already shown immediately
                    // Only handle kill streak on flush

                    int kills = rewards.killCount;
                    if (kills >= 2) {
                        // Notify StatsService to potentially update record
                        StatsService statsService = plugin.getStatsService();
                        if (statsService != null) {
                            statsService.recordKillStreak(playerId, kills);
                        }

                        // Show kill streak message immediately (rewards already faded)
                        sendKillStreakMessage(p, kills);
                    }
                }
            }
        }, delayTicks);

        flushTasks.put(playerId, task);

        // Also update action bar immediately with current totals
        PendingRewards current = pendingRewards.get(playerId);
        if (current != null) {
            sendActionBar(player, current);
        }
    }

    private void cancelFlushTask(UUID playerId) {
        BukkitTask task = flushTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void sendActionBar(Player player, PendingRewards rewards) {
        int totalXp = rewards.xp + rewards.sharedXp;
        int coins = rewards.coins;
        int perma = rewards.permaScore;

        if (totalXp <= 0 && coins <= 0 && perma <= 0) {
            return; // Nothing to display
        }

        // Build the message by concatenating non-zero reward parts
        String separator = i18n.get("actionbar.reward_separator");
        List<String> parts = new ArrayList<>(3);

        if (totalXp > 0) {
            parts.add(i18n.get("actionbar.reward_xp", "amount", totalXp));
        }
        if (coins > 0) {
            parts.add(i18n.get("actionbar.reward_coins", "amount", coins));
        }
        if (perma > 0) {
            parts.add(i18n.get("actionbar.reward_perma", "amount", perma));
        }

        String message = String.join(separator, parts);
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
    }

    private void playSound(Player player, SoundConfig sound) {
        if (sound != null) {
            sound.play(player);
        }
    }

    /**
     * Gets the language key for a kill streak count.
     *
     * @param streak The current kill streak
     * @return The language key for the streak message
     */
    private String getStreakKey(int streak) {
        return switch (streak) {
            case 2 -> "killstreak.double";
            case 3 -> "killstreak.triple";
            case 4 -> "killstreak.quadra";
            case 5 -> "killstreak.penta";
            case 6 -> "killstreak.spree";
            case 7 -> "killstreak.unstoppable";
            case 8 -> "killstreak.godlike";
            case 9 -> "killstreak.legendary";
            default -> "killstreak.generic"; // 10+ kills
        };
    }

    /**
     * Sends a kill streak message to the player.
     * Called after the reward display flush if streak >= 2.
     */
    private void sendKillStreakMessage(Player player, int streak) {
        if (streak < 2) return;

        String key = getStreakKey(streak);
        String message = i18n.get(key, "count", streak);
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
    }

    // ==================== Inner Class ====================

    private static class PendingRewards {
        int xp = 0;
        int sharedXp = 0;
        int coins = 0;
        int permaScore = 0;
        int killCount = 0;  // Number of kills in this aggregation window
    }
}
