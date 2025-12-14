package cat.nyaa.survivors.task;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Periodic task that displays cooldown remaining in the actionbar
 * for players in COOLDOWN mode.
 */
public class CooldownDisplay implements Runnable {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final I18nService i18n;

    private int taskId = -1;

    public CooldownDisplay(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.i18n = plugin.getI18nService();
    }

    /**
     * Starts the cooldown display task.
     */
    public void start() {
        if (taskId != -1) {
            return; // Already running
        }

        if (!config.isShowCooldownBar()) {
            return; // Disabled in config
        }

        int interval = config.getDisplayUpdateTicks();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this, interval, interval).getTaskId();
        plugin.getLogger().info("Cooldown display started with interval: " + interval + " ticks");
    }

    /**
     * Stops the cooldown display task.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (PlayerState playerState : state.getAllPlayers()) {
            if (playerState.getMode() != PlayerMode.COOLDOWN) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerState.getUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }

            long cooldownEnd = playerState.getCooldownUntilMillis();
            int secondsRemaining = (int) Math.max(0, (cooldownEnd - now) / 1000);

            if (secondsRemaining > 0) {
                // Display cooldown in actionbar
                i18n.sendActionBar(player, "actionbar.cooldown", "seconds", secondsRemaining);
            } else {
                // Cooldown expired - transition to LOBBY mode
                playerState.setMode(PlayerMode.LOBBY);
                playerState.setCooldownUntilMillis(0);
                i18n.send(player, "info.cooldown_expired");
            }
        }
    }
}
