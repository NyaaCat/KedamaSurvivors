package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages player ready state and team countdown.
 */
public class ReadyService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;

    // Active countdowns per team
    private final Map<UUID, CountdownTask> activeCountdowns = new HashMap<>();

    public ReadyService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    /**
     * Toggles ready state for a player.
     * @return true if player is now ready, false if unready
     */
    public boolean toggleReady(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerState playerState = state.getOrCreatePlayer(playerId, player.getName());

        // Validate eligibility
        if (!canReady(player, playerState)) {
            return false;
        }

        boolean wasReady = playerState.isReady();
        boolean isNowReady = !wasReady;

        playerState.setReady(isNowReady);

        // Notify the player themselves
        if (isNowReady) {
            i18n.send(player, "ready.now_ready");
        } else {
            i18n.send(player, "ready.no_longer_ready");
        }

        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            team.setReady(playerId, isNowReady);

            if (isNowReady) {
                notifyTeam(team, player, true);
                checkAndStartCountdown(team);
            } else {
                notifyTeam(team, player, false);
                cancelCountdown(team.getTeamId());
            }
        }

        return isNowReady;
    }

    /**
     * Checks if a player can become ready.
     */
    public boolean canReady(Player player, PlayerState playerState) {
        // Must be in lobby or cooldown mode (cooldown = after death, can rejoin)
        if (playerState.getMode() != PlayerMode.LOBBY && playerState.getMode() != PlayerMode.COOLDOWN) {
            i18n.send(player, "error.not_in_lobby");
            return false;
        }

        // Must not be on cooldown - always enforced, no bypass
        if (playerState.isOnCooldown()) {
            i18n.send(player, "error.on_cooldown", "seconds", playerState.getCooldownRemainingSeconds());
            return false;
        }

        // Must be in a team
        if (!state.isInTeam(player.getUniqueId())) {
            i18n.send(player, "error.not_in_team");
            return false;
        }

        // Must have selected starters
        if (!playerState.hasSelectedStarters()) {
            i18n.send(player, "error.select_starters_first");
            return false;
        }

        // Global join must be enabled
        if (!config.isJoinEnabled()) {
            i18n.send(player, "error.join_disabled");
            return false;
        }

        return true;
    }

    /**
     * Checks if all team members are ready and starts countdown if so.
     */
    private void checkAndStartCountdown(TeamState team) {
        if (!team.isAllReady()) {
            return;
        }

        // Already counting down?
        if (activeCountdowns.containsKey(team.getTeamId())) {
            return;
        }

        startCountdown(team);
    }

    /**
     * Starts countdown for a team.
     */
    public void startCountdown(TeamState team) {
        UUID teamId = team.getTeamId();

        // Cancel any existing countdown
        cancelCountdown(teamId);

        // Set only eligible members to COUNTDOWN mode (not IN_RUN, not on cooldown)
        for (UUID memberId : team.getMembers()) {
            state.getPlayer(memberId).ifPresent(ps -> {
                // Don't change mode for players already in run or still on cooldown
                if (ps.getMode() != PlayerMode.IN_RUN && !ps.isOnCooldown()) {
                    ps.setMode(PlayerMode.COUNTDOWN);
                }
            });
        }

        // Notify only players in countdown (not those in run or on cooldown)
        for (UUID memberId : team.getMembers()) {
            state.getPlayer(memberId).ifPresent(ps -> {
                if (ps.getMode() == PlayerMode.COUNTDOWN) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) {
                        i18n.send(member, "ready.all_ready");
                    }
                }
            });
        }

        // Start countdown task
        int seconds = config.getCountdownSeconds();
        CountdownTask task = new CountdownTask(team, seconds);
        activeCountdowns.put(teamId, task);
        task.start();
    }

    /**
     * Cancels countdown for a team.
     */
    public void cancelCountdown(UUID teamId) {
        CountdownTask task = activeCountdowns.remove(teamId);
        if (task != null) {
            task.cancel();

            // Reset members to READY mode (or LOBBY if not ready)
            state.getTeam(teamId).ifPresent(team -> {
                for (UUID memberId : team.getMembers()) {
                    state.getPlayer(memberId).ifPresent(ps -> {
                        if (ps.getMode() == PlayerMode.COUNTDOWN) {
                            ps.setMode(ps.isReady() ? PlayerMode.READY : PlayerMode.LOBBY);
                        }
                    });
                }
            });
        }
    }

    /**
     * Forces a team to start immediately (admin command).
     */
    public void forceStart(TeamState team) {
        cancelCountdown(team.getTeamId());

        // Set all members as ready
        for (UUID memberId : team.getMembers()) {
            state.getPlayer(memberId).ifPresent(ps -> {
                ps.setReady(true);
                ps.setMode(PlayerMode.COUNTDOWN);
            });
            team.setReady(memberId, true);
        }

        // Start with 0-second countdown (immediate)
        CountdownTask task = new CountdownTask(team, 0);
        activeCountdowns.put(team.getTeamId(), task);
        task.start();
    }

    /**
     * Handles player disconnect during countdown.
     */
    public void handleDisconnect(UUID playerId) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            if (activeCountdowns.containsKey(team.getTeamId())) {
                cancelCountdown(team.getTeamId());

                // Notify team
                for (UUID memberId : team.getMembers()) {
                    if (!memberId.equals(playerId)) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null) {
                            i18n.send(member, "countdown.cancelled_disconnect");
                        }
                    }
                }
            }
        }
    }

    private void notifyTeam(TeamState team, Player readyPlayer, boolean isReady) {
        String msgKey = isReady ? "ready.teammate_ready" : "ready.teammate_unready";

        for (UUID memberId : team.getMembers()) {
            if (!memberId.equals(readyPlayer.getUniqueId())) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    i18n.send(member, msgKey, "player", readyPlayer.getName());
                }
            }
        }
    }

    /**
     * Clears all countdowns (for shutdown).
     */
    public void clearAll() {
        for (CountdownTask task : activeCountdowns.values()) {
            task.cancel();
        }
        activeCountdowns.clear();
    }

    /**
     * Checks if a team is currently counting down.
     */
    public boolean isCountingDown(UUID teamId) {
        return activeCountdowns.containsKey(teamId);
    }

    /**
     * Gets remaining countdown seconds for a team.
     */
    public int getRemainingSeconds(UUID teamId) {
        CountdownTask task = activeCountdowns.get(teamId);
        return task != null ? task.remaining : 0;
    }

    // ==================== Countdown Task ====================

    private class CountdownTask implements Runnable {
        private final TeamState team;
        private int remaining;
        private BukkitTask bukkitTask;

        CountdownTask(TeamState team, int seconds) {
            this.team = team;
            this.remaining = seconds;
        }

        void start() {
            if (remaining <= 0) {
                // Immediate start
                onComplete();
            } else {
                // Schedule repeating task
                bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 20L);
            }
        }

        void cancel() {
            if (bukkitTask != null) {
                bukkitTask.cancel();
                bukkitTask = null;
            }
        }

        @Override
        public void run() {
            if (remaining <= 0) {
                cancel();
                onComplete();
                return;
            }

            // Send countdown message only to players in COUNTDOWN mode
            for (UUID memberId : team.getMembers()) {
                Optional<PlayerState> memberStateOpt = state.getPlayer(memberId);
                if (memberStateOpt.isEmpty()) continue;

                PlayerState memberState = memberStateOpt.get();
                // Only send feedback to players who are counting down (not IN_RUN or COOLDOWN)
                if (memberState.getMode() != PlayerMode.COUNTDOWN) continue;

                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    sendCountdownFeedback(member, remaining);
                }
            }

            remaining--;
        }

        private void sendCountdownFeedback(Player player, int seconds) {
            // Actionbar
            if (config.isShowActionBar()) {
                i18n.sendActionBar(player, "countdown.actionbar", "seconds", seconds);
            }

            // Title on specific seconds
            if (config.isShowTitle() && (seconds <= 3 || seconds == 5 || seconds == 10)) {
                i18n.sendTitle(player, "countdown.title", "countdown.subtitle",
                        5, 15, 5, "seconds", seconds);
            }

            // Sound
            Sound sound = config.getCountdownSound();
            if (sound != null) {
                float pitch = config.getCountdownSoundPitch();
                player.playSound(player.getLocation(), sound, 1.0f, pitch);
            }
        }

        private void onComplete() {
            activeCountdowns.remove(team.getTeamId());

            // Safety check: Verify at least one team member is still in COUNTDOWN mode
            // If countdown was cancelled (e.g., team wipe), no players will be in COUNTDOWN
            boolean hasEligiblePlayers = team.getMembers().stream()
                .map(state::getPlayer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(ps -> ps.getMode() == PlayerMode.COUNTDOWN);

            if (!hasEligiblePlayers) {
                plugin.getLogger().info("Countdown completed but no eligible players in COUNTDOWN mode - aborting run start");
                return;
            }

            // Check if team already has an active run (players rejoining)
            Optional<RunState> existingRunOpt = state.getTeamRun(team.getTeamId());
            if (existingRunOpt.isPresent() && existingRunOpt.get().isActive()) {
                // Rejoin players to existing run
                handleRejoinToRun(existingRunOpt.get());
                return;
            }

            // Additional check: If run exists but is ENDING, don't start new run
            if (existingRunOpt.isPresent() && existingRunOpt.get().isEnded()) {
                plugin.getLogger().info("Run is ending, not starting new run");
                resetCountdownPlayersToLobby();
                return;
            }

            // Notify completion
            for (UUID memberId : team.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    i18n.send(member, "countdown.starting");

                    // Play teleport sound
                    Sound teleportSound = config.getTeleportSound();
                    if (teleportSound != null) {
                        member.playSound(member.getLocation(), teleportSound, 1.0f, 1.0f);
                    }
                }
            }

            // Start the run asynchronously (async chunk loading)
            plugin.getRunService().startRunAsync(team);
        }

        private void handleRejoinToRun(RunState run) {
            // Find ready players who need to rejoin
            for (UUID memberId : team.getMembers()) {
                Optional<PlayerState> playerStateOpt = state.getPlayer(memberId);
                if (playerStateOpt.isEmpty()) continue;

                PlayerState playerState = playerStateOpt.get();

                // Only rejoin players who are in COUNTDOWN mode (properly set to rejoin)
                // This filters out IN_RUN players and COOLDOWN players who weren't eligible
                if (playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady()) {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player != null && player.isOnline()) {
                        plugin.getRunService().rejoinPlayerToRun(player, playerState, run);
                    }
                }
            }
        }

        /**
         * Resets players in COUNTDOWN mode back to LOBBY.
         * Called when countdown completes but run is ending (team wipe scenario).
         */
        private void resetCountdownPlayersToLobby() {
            for (UUID memberId : team.getMembers()) {
                state.getPlayer(memberId).ifPresent(ps -> {
                    if (ps.getMode() == PlayerMode.COUNTDOWN) {
                        ps.setMode(PlayerMode.LOBBY);
                        ps.setReady(false);
                    }
                });
            }
        }
    }
}
