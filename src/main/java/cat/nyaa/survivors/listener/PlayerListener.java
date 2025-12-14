package cat.nyaa.survivors.listener;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles player join/quit and basic lifecycle events.
 */
public class PlayerListener implements Listener {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;

    public PlayerListener(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Get or create player state
        PlayerState playerState = state.getOrCreatePlayer(playerId, player.getName());

        // Update name in case it changed
        playerState.setName(player.getName());

        // Check for disconnect grace period
        if (playerState.getMode() == PlayerMode.DISCONNECTED) {
            handleReconnect(player, playerState);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        // Handle based on current mode
        if (playerState.getMode() == PlayerMode.IN_RUN) {
            handleDisconnectInRun(player, playerState);
        } else if (playerState.getMode() == PlayerMode.COUNTDOWN) {
            handleDisconnectInCountdown(player, playerState);
        } else if (playerState.getMode() == PlayerMode.READY) {
            // Unready the player
            playerState.setReady(false);
            Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
            teamOpt.ifPresent(team -> team.setReady(playerId, false));
        }
    }

    private void handleReconnect(Player player, PlayerState playerState) {
        UUID playerId = player.getUniqueId();

        // Check if still within grace period
        if (!playerState.isWithinGracePeriod(config.getDisconnectGraceMs())) {
            // Grace period expired
            playerState.setMode(PlayerMode.LOBBY);
            playerState.resetRunState();
            i18n.send(player, "disconnect.grace_expired");
            return;
        }

        // Still in grace period - restore to run
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            team.markReconnected(playerId);

            Optional<RunState> runOpt = state.getTeamRun(team.getTeamId());
            if (runOpt.isPresent() && runOpt.get().isActive()) {
                // Restore to run
                playerState.setMode(PlayerMode.IN_RUN);
                playerState.setDisconnectedAtMillis(0);

                // Apply invulnerability
                long invulEnd = System.currentTimeMillis() + config.getRespawnInvulnerabilityMs();
                playerState.setInvulnerableUntilMillis(invulEnd);

                i18n.send(player, "disconnect.reconnected");

                // Notify team
                for (UUID memberId : team.getMembers()) {
                    if (!memberId.equals(playerId)) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null) {
                            i18n.send(member, "disconnect.teammate_reconnected", "player", player.getName());
                        }
                    }
                }

                // TODO: Teleport player back to run area
                return;
            }
        }

        // No active run to return to
        playerState.setMode(PlayerMode.LOBBY);
        playerState.resetRunState();
    }

    private void handleDisconnectInRun(Player player, PlayerState playerState) {
        UUID playerId = player.getUniqueId();

        // Mark as disconnected
        playerState.setMode(PlayerMode.DISCONNECTED);
        playerState.setDisconnectedAtMillis(System.currentTimeMillis());

        // Update team tracking
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            team.markDisconnected(playerId);

            if (config.isNotifyTeamOnDisconnect()) {
                for (UUID memberId : team.getMembers()) {
                    if (!memberId.equals(playerId)) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null) {
                            i18n.send(member, "disconnect.teammate_disconnected",
                                    "player", player.getName(),
                                    "seconds", config.getDisconnectGraceSeconds());
                        }
                    }
                }
            }

            // Check for team wipe
            Optional<RunState> runOpt = state.getTeamRun(team.getTeamId());
            if (runOpt.isPresent()) {
                RunState run = runOpt.get();
                if (team.isWiped(run.getAlivePlayers(), config.getDisconnectGraceMs())) {
                    handleTeamWipe(team, run);
                }
            }
        }
    }

    private void handleDisconnectInCountdown(Player player, PlayerState playerState) {
        UUID playerId = player.getUniqueId();

        // Cancel countdown for the team
        playerState.setMode(PlayerMode.LOBBY);
        playerState.setReady(false);

        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            team.setReady(playerId, false);

            // Reset all team members to READY state (not countdown)
            for (UUID memberId : team.getMembers()) {
                Optional<PlayerState> memberState = state.getPlayer(memberId);
                if (memberState.isPresent() && memberState.get().getMode() == PlayerMode.COUNTDOWN) {
                    memberState.get().setMode(PlayerMode.READY);
                }

                Player member = Bukkit.getPlayer(memberId);
                if (member != null && !memberId.equals(playerId)) {
                    i18n.send(member, "countdown.cancelled", "player", player.getName());
                }
            }
        }
    }

    private void handleTeamWipe(TeamState team, RunState run) {
        plugin.getLogger().info("Team " + team.getName() + " has been wiped!");

        // Notify surviving members (if any are still connected)
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                i18n.send(member, "run.team_wiped");
            }
        }

        // End the run
        state.endRun(run.getRunId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Optional<PlayerState> playerStateOpt = state.getPlayer(player.getUniqueId());
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        // Check invulnerability
        if (playerState.isInvulnerable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        if (playerState.getMode() != PlayerMode.IN_RUN) return;

        // Mark as dead in run
        Optional<RunState> runOpt = state.getPlayerRun(playerId);
        if (runOpt.isPresent()) {
            RunState run = runOpt.get();
            run.markDead(playerId);

            // Check for team wipe
            Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
            if (teamOpt.isPresent()) {
                TeamState team = teamOpt.get();
                if (team.isWiped(run.getAlivePlayers(), config.getDisconnectGraceMs())) {
                    handleTeamWipe(team, run);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        if (playerState.getMode() != PlayerMode.IN_RUN) return;

        Optional<RunState> runOpt = state.getPlayerRun(playerId);
        if (runOpt.isPresent()) {
            RunState run = runOpt.get();

            // Mark as alive again
            run.markAlive(playerId);

            // Apply respawn invulnerability
            long invulEnd = System.currentTimeMillis() + config.getRespawnInvulnerabilityMs();
            playerState.setInvulnerableUntilMillis(invulEnd);

            // Set respawn location
            org.bukkit.Location spawnPoint = run.getRandomSpawnPoint();
            if (spawnPoint != null) {
                event.setRespawnLocation(spawnPoint);
            }

            // Schedule invulnerability notification
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    i18n.send(player, "respawn.invulnerability_start",
                            "seconds", config.getRespawnInvulnerabilitySeconds());
                }
            }, 1L);
        }
    }
}
