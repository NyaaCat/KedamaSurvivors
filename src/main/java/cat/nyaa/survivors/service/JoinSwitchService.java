package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controls global game entry and handles grace eject for maintenance mode.
 * When join is disabled, players in active runs are ejected after a grace period.
 */
public class JoinSwitchService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final I18nService i18n;

    // Tracks players in grace eject state with their eject timestamp
    private final Map<UUID, Long> graceEjectPlayers = new ConcurrentHashMap<>();

    // Task ID for grace eject countdown
    private int graceTaskId = -1;

    public JoinSwitchService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.i18n = plugin.getI18nService();
    }

    /**
     * Checks if joining is enabled.
     */
    public boolean isJoinEnabled() {
        return config.isJoinEnabled();
    }

    /**
     * Sets whether joining is enabled.
     * When disabled, initiates grace eject for all in-run players.
     */
    public void setJoinEnabled(boolean enabled) {
        config.setJoinEnabled(enabled);

        if (!enabled) {
            // Start grace eject for all in-run players
            initiateGraceEjectAll();
        } else {
            // Cancel any pending grace ejects
            cancelGraceEjectAll();
        }
    }

    /**
     * Initiates grace eject for all players currently in runs.
     */
    private void initiateGraceEjectAll() {
        int count = 0;
        long ejectTime = System.currentTimeMillis() + (config.getGraceEjectSeconds() * 1000L);

        for (PlayerState playerState : state.getAllPlayers()) {
            if (playerState.getMode() == PlayerMode.IN_RUN) {
                initiateGraceEject(playerState, ejectTime);
                count++;
            }
        }

        if (count > 0 && graceTaskId == -1) {
            // Start the grace eject checker
            startGraceEjectChecker();
        }

        plugin.getLogger().info("Initiated grace eject for " + count + " players");
    }

    /**
     * Initiates grace eject for a specific player.
     */
    private void initiateGraceEject(PlayerState playerState, long ejectTime) {
        UUID playerId = playerState.getUuid();
        graceEjectPlayers.put(playerId, ejectTime);
        playerState.setMode(PlayerMode.GRACE_EJECT);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            i18n.send(player, "info.grace_eject", "seconds", config.getGraceEjectSeconds());
        }
    }

    /**
     * Cancels all pending grace ejects.
     */
    private void cancelGraceEjectAll() {
        for (UUID playerId : graceEjectPlayers.keySet()) {
            Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
            playerStateOpt.ifPresent(ps -> {
                if (ps.getMode() == PlayerMode.GRACE_EJECT) {
                    ps.setMode(PlayerMode.IN_RUN);
                }
            });
        }
        graceEjectPlayers.clear();

        // Stop the checker if running
        stopGraceEjectChecker();
    }

    /**
     * Starts the grace eject checker task.
     */
    private void startGraceEjectChecker() {
        if (graceTaskId != -1) return;

        int interval = config.getGraceWarningInterval() * 20; // Convert seconds to ticks
        graceTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::checkGraceEjects, 20, interval).getTaskId();
    }

    /**
     * Stops the grace eject checker task.
     */
    private void stopGraceEjectChecker() {
        if (graceTaskId != -1) {
            Bukkit.getScheduler().cancelTask(graceTaskId);
            graceTaskId = -1;
        }
    }

    /**
     * Checks for expired grace ejects and sends warnings.
     */
    private void checkGraceEjects() {
        long now = System.currentTimeMillis();
        Set<UUID> toEject = new HashSet<>();

        for (Map.Entry<UUID, Long> entry : graceEjectPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            long ejectTime = entry.getValue();
            int secondsRemaining = (int) ((ejectTime - now) / 1000);

            if (now >= ejectTime) {
                toEject.add(playerId);
            } else {
                // Send warning if player is online
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    i18n.sendActionBar(player, "actionbar.grace_eject", "seconds", secondsRemaining);
                }
            }
        }

        // Execute ejects
        for (UUID playerId : toEject) {
            executeGraceEject(playerId);
        }

        // Stop checker if no more players
        if (graceEjectPlayers.isEmpty()) {
            stopGraceEjectChecker();
        }
    }

    /**
     * Executes the grace eject for a player.
     */
    private void executeGraceEject(UUID playerId) {
        graceEjectPlayers.remove(playerId);

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        // Reset player to lobby state (not death penalty - this is maintenance)
        playerState.setMode(PlayerMode.LOBBY);
        playerState.setRunId(null);

        // Clear run-related state
        playerState.resetRunState();

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            // Execute prep command to teleport back to lobby
            plugin.getTemplateEngine().context()
                    .player(player)
                    .executeAsConsole(config.getPrepCommand());

            // Don't remove scoreboard - let it auto-update to lobby mode

            i18n.send(player, "info.ejected_maintenance");
        }

        plugin.getLogger().info("Grace ejected player: " + playerState.getName());
    }

    /**
     * Handles a player attempting to join (used for permission check).
     * @return true if player can join, false if joining is disabled
     */
    public boolean canJoin(Player player) {
        if (config.isJoinEnabled()) {
            return true;
        }

        i18n.send(player, "info.join_disabled");
        return false;
    }

    /**
     * Gets count of players in grace eject state.
     */
    public int getGraceEjectCount() {
        return graceEjectPlayers.size();
    }

    /**
     * Checks if a player is in grace eject state.
     */
    public boolean isInGraceEject(UUID playerId) {
        return graceEjectPlayers.containsKey(playerId);
    }

    /**
     * Gets remaining grace time in seconds for a player.
     * @return remaining seconds, or -1 if not in grace eject
     */
    public int getRemainingGraceSeconds(UUID playerId) {
        Long ejectTime = graceEjectPlayers.get(playerId);
        if (ejectTime == null) return -1;

        long remaining = ejectTime - System.currentTimeMillis();
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    /**
     * Cleans up on plugin disable.
     */
    public void shutdown() {
        stopGraceEjectChecker();
        graceEjectPlayers.clear();
    }
}
