package cat.nyaa.survivors.task;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import cat.nyaa.survivors.service.UpgradeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Periodic task that handles upgrade reminders and auto-selection.
 * Runs every second to:
 * 1. Check if upgrade deadline expired -> auto-select
 * 2. Send chat reminders at configurable intervals
 */
public class UpgradeReminderTask implements Runnable {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final UpgradeService upgrade;

    // Track last reminder time per player to avoid spam
    private final Map<UUID, Long> lastReminderTime = new HashMap<>();

    private int taskId = -1;

    public UpgradeReminderTask(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.upgrade = plugin.getUpgradeService();
    }

    /**
     * Starts the upgrade reminder task.
     */
    public void start() {
        if (taskId != -1) {
            return; // Already running
        }

        // Run every second (20 ticks)
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L).getTaskId();
        plugin.getLogger().info("UpgradeReminderTask started");
    }

    /**
     * Stops the upgrade reminder task.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        lastReminderTime.clear();
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        int reminderIntervalMs = config.getUpgradeReminderIntervalSeconds() * 1000;

        for (PlayerState playerState : state.getAllPlayers()) {
            // Only process players in run with pending upgrade
            if (playerState.getMode() != PlayerMode.IN_RUN) {
                // Clean up tracking if player left run
                lastReminderTime.remove(playerState.getUuid());
                continue;
            }

            if (!playerState.isUpgradePending()) {
                // Clean up tracking if upgrade resolved
                lastReminderTime.remove(playerState.getUuid());
                continue;
            }

            // Skip if both at max level (instant reward case, no reminders needed)
            if (playerState.isWeaponAtMax() && playerState.isHelmetAtMax()) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerState.getUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }

            long deadline = playerState.getUpgradeDeadlineMillis();

            // Check if deadline expired -> auto-select
            if (deadline > 0 && now >= deadline) {
                upgrade.processAutoUpgrade(player, playerState);
                lastReminderTime.remove(playerState.getUuid());
                continue;
            }

            // Check if it's time for a reminder
            Long lastReminder = lastReminderTime.get(playerState.getUuid());
            if (lastReminder == null || (now - lastReminder) >= reminderIntervalMs) {
                // Send reminder
                upgrade.sendUpgradePrompt(player, playerState, true);
                lastReminderTime.put(playerState.getUuid(), now);
            }
        }
    }

    /**
     * Clears the reminder tracking for a player.
     * Called when player's upgrade is resolved or they disconnect.
     */
    public void clearTracking(UUID playerId) {
        lastReminderTime.remove(playerId);
    }
}
