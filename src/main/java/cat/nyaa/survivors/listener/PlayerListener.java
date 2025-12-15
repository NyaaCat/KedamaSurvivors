package cat.nyaa.survivors.listener;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.DeathService;
import cat.nyaa.survivors.service.PersistenceService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    private final DeathService death;
    private final PersistenceService persistence;

    public PlayerListener(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.death = plugin.getDeathService();
        this.persistence = plugin.getPersistenceService();
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

        // Validate player state - fix inconsistencies
        validateAndFixPlayerState(player, playerState);

        // Setup scoreboard for all players (always visible)
        if (config.isScoreboardEnabled()) {
            plugin.getScoreboardService().setupSidebar(player);
        }
    }

    /**
     * Validates player state on join and fixes inconsistencies.
     * Handles cases like:
     * - Player in combat world but not IN_RUN mode
     * - Player in COUNTDOWN/READY mode but no active countdown
     * - Player in IN_RUN mode but run ended
     */
    private void validateAndFixPlayerState(Player player, PlayerState playerState) {
        UUID playerId = player.getUniqueId();
        PlayerMode mode = playerState.getMode();
        String worldName = player.getWorld().getName();

        // Check if player is in a combat world
        boolean inCombatWorld = plugin.getWorldService().getWorldConfig(worldName).isPresent();

        // Case 1: Player in combat world but not IN_RUN
        if (inCombatWorld && mode != PlayerMode.IN_RUN && mode != PlayerMode.DISCONNECTED) {
            plugin.getLogger().info("Player " + player.getName() + " in combat world '" + worldName +
                "' with mode " + mode + " - teleporting to lobby");
            teleportToLobbyDelayed(player, playerState);
            return;
        }

        // Case 2: Player claims to be IN_RUN but has no active run
        if (mode == PlayerMode.IN_RUN) {
            Optional<RunState> runOpt = state.getPlayerRun(playerId);
            if (runOpt.isEmpty() || !runOpt.get().isActive()) {
                plugin.getLogger().info("Player " + player.getName() + " in IN_RUN mode but no active run - resetting to LOBBY");
                playerState.setMode(PlayerMode.LOBBY);
                playerState.resetRunState();
                if (inCombatWorld) {
                    teleportToLobbyDelayed(player, playerState);
                }
                return;
            }
        }

        // Case 3: Player in COUNTDOWN or READY mode - reset to LOBBY
        if (mode == PlayerMode.COUNTDOWN || mode == PlayerMode.READY) {
            plugin.getLogger().info("Player " + player.getName() + " in " + mode + " mode on join - resetting to LOBBY");
            playerState.setMode(PlayerMode.LOBBY);
            playerState.setReady(false);

            // Also update team ready state
            Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
            teamOpt.ifPresent(team -> team.setReady(playerId, false));

            if (inCombatWorld) {
                teleportToLobbyDelayed(player, playerState);
            }
        }
    }

    /**
     * Teleports player to lobby after a short delay (allows chunks to load).
     */
    private void teleportToLobbyDelayed(Player player, PlayerState playerState) {
        // Reset state
        playerState.setMode(PlayerMode.LOBBY);
        playerState.resetRunState();
        playerState.setReady(false);

        // Teleport after short delay to allow world to load
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Location lobby = config.getLobbyLocation();
            player.teleportAsync(lobby).thenAccept(success -> {
                if (!success) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.teleport(lobby));
                }
            });
            i18n.send(player, "info.returned_to_lobby");
        }, 20L); // 1 second delay
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

        // Save player state asynchronously if configured
        if (config.isSaveOnQuit() && persistence != null) {
            persistence.savePlayerAsync(playerId);
        }
    }

    private void handleReconnect(Player player, PlayerState playerState) {
        UUID playerId = player.getUniqueId();

        // Check if still within grace period
        if (!playerState.isWithinGracePeriod(config.getDisconnectGraceMs())) {
            // Grace period expired
            playerState.setMode(PlayerMode.LOBBY);
            playerState.resetRunState();
            state.markReconnected(playerId);  // Remove from disconnected tracking
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
                RunState run = runOpt.get();

                // Restore to run
                playerState.setMode(PlayerMode.IN_RUN);
                playerState.setDisconnectedAtMillis(0);

                // Remove from disconnected tracking
                state.markReconnected(playerId);

                // Apply invulnerability
                long invulEnd = System.currentTimeMillis() + config.getRespawnInvulnerabilityMs();
                playerState.setInvulnerableUntilMillis(invulEnd);

                // Teleport player back to run area
                Location spawnPoint = run.getRandomSpawnPoint();
                if (spawnPoint != null) {
                    player.teleport(spawnPoint);
                }

                // Setup scoreboard
                plugin.getScoreboardService().setupSidebar(player);

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

                return;
            }
        }

        // No active run to return to
        playerState.setMode(PlayerMode.LOBBY);
        playerState.resetRunState();
        state.markReconnected(playerId);  // Remove from disconnected tracking
    }

    private void handleDisconnectInRun(Player player, PlayerState playerState) {
        UUID playerId = player.getUniqueId();

        // Mark as disconnected
        playerState.setMode(PlayerMode.DISCONNECTED);
        playerState.setDisconnectedAtMillis(System.currentTimeMillis());

        // Track in disconnected set for efficient grace period checking
        state.markDisconnected(playerId);

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Delegate to DeathService for full handling
        death.handleDeath(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        // Delegate respawn handling to DeathService
        Location respawnLoc = death.handleRespawn(player, playerState);
        if (respawnLoc != null) {
            event.setRespawnLocation(respawnLoc);
        }
    }
}
