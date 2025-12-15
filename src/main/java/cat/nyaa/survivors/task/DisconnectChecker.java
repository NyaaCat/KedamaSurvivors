package cat.nyaa.survivors.task;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.DeathService;
import cat.nyaa.survivors.service.RunService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Periodic task that checks for expired disconnect grace periods.
 * When a player's grace period expires, applies death penalty and checks for team wipe.
 */
public class DisconnectChecker implements Runnable {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final DeathService deathService;
    private final RunService runService;
    private final I18nService i18n;

    private int taskId = -1;

    public DisconnectChecker(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.deathService = plugin.getDeathService();
        this.runService = plugin.getRunService();
        this.i18n = plugin.getI18nService();
    }

    /**
     * Starts the periodic disconnect check task.
     */
    public void start() {
        if (taskId != -1) {
            return; // Already running
        }

        int interval = config.getDisconnectCheckIntervalTicks();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this, interval, interval).getTaskId();
        plugin.getLogger().info("Disconnect checker started with interval: " + interval + " ticks");
    }

    /**
     * Stops the disconnect check task.
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
        long graceMs = config.getDisconnectGraceMs();

        // Check only tracked disconnected players (more efficient than iterating all)
        for (UUID playerId : state.getDisconnectedPlayers()) {
            Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
            if (playerStateOpt.isEmpty()) continue;

            PlayerState playerState = playerStateOpt.get();

            // Verify still in DISCONNECTED mode
            if (playerState.getMode() != PlayerMode.DISCONNECTED) {
                continue;
            }

            // Check if grace period has expired
            long disconnectedAt = playerState.getDisconnectedAtMillis();
            if (disconnectedAt > 0 && (now - disconnectedAt) >= graceMs) {
                handleGraceExpired(playerState);
            }
        }
    }

    /**
     * Handles when a player's disconnect grace period has expired.
     */
    private void handleGraceExpired(PlayerState playerState) {
        UUID playerId = playerState.getUuid();

        plugin.getLogger().info("Disconnect grace expired for player: " + playerState.getName());

        // Notify team if configured
        if (config.isNotifyGraceExpired()) {
            notifyTeam(playerState, "disconnect.grace_expired");
        }

        // Reset player state (death penalty without full equipment clear since they're offline)
        playerState.setXpProgress(0);
        playerState.setXpHeld(0);
        playerState.setUpgradePending(false);
        playerState.setWeaponGroup(null);
        playerState.setWeaponLevel(0);
        playerState.setHelmetGroup(null);
        playerState.setHelmetLevel(0);
        playerState.setOverflowXpAccumulated(0);

        // Set cooldown
        long cooldownEnd = System.currentTimeMillis() + config.getDeathCooldownMs();
        playerState.setCooldownUntilMillis(cooldownEnd);
        playerState.setMode(PlayerMode.COOLDOWN);

        // Clear disconnect tracking
        playerState.setDisconnectedAtMillis(0);
        state.markReconnected(playerId);  // Remove from disconnected set

        // Remove from team disconnect tracking
        UUID teamId = playerState.getTeamId();
        if (teamId != null) {
            Optional<TeamState> teamOpt = state.getTeam(teamId);
            teamOpt.ifPresent(team -> {
                // Mark as reconnected clears the disconnect tracking
                team.markReconnected(playerId);
            });
        }

        // Remove from run
        UUID runId = playerState.getRunId();
        if (runId != null) {
            Optional<RunState> runOpt = state.getRun(runId);
            runOpt.ifPresent(run -> {
                run.markDead(playerId);
                run.removeParticipant(playerId);

                // Check for team wipe
                checkTeamWipe(run, teamId);
            });
        }

        playerState.setRunId(null);
    }

    /**
     * Checks if a team has been wiped (all members dead or disconnected).
     */
    private void checkTeamWipe(RunState run, UUID teamId) {
        if (teamId == null) return;

        Optional<TeamState> teamOpt = state.getTeam(teamId);
        if (teamOpt.isEmpty()) return;

        TeamState team = teamOpt.get();

        // Check if any team members are still alive in the run
        boolean anyAlive = run.getAlivePlayers().stream()
                .map(state::getPlayer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(ps -> ps.getMode() == PlayerMode.IN_RUN);

        if (!anyAlive) {
            // Team wipe - end the run
            plugin.getLogger().info("Team wipe detected for team: " + team.getName());
            runService.endRun(run, RunService.EndReason.WIPE);
        }
    }

    /**
     * Notifies team members about a player's disconnect grace expiry.
     */
    private void notifyTeam(PlayerState disconnectedPlayer, String messageKey) {
        UUID teamId = disconnectedPlayer.getTeamId();
        if (teamId == null) return;

        Optional<TeamState> teamOpt = state.getTeam(teamId);
        if (teamOpt.isEmpty()) return;

        TeamState team = teamOpt.get();

        for (UUID memberId : team.getMembers()) {
            if (memberId.equals(disconnectedPlayer.getUuid())) continue;

            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                i18n.send(member, messageKey, "player", disconnectedPlayer.getName());
            }
        }
    }
}
