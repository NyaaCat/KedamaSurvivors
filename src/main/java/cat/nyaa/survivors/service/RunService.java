package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.*;
import cat.nyaa.survivors.util.TemplateEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Manages run lifecycle - creation, execution, and termination.
 */
public class RunService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;
    private final WorldService worldService;
    private final TemplateEngine templateEngine;

    public RunService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.worldService = plugin.getWorldService();
        this.templateEngine = plugin.getTemplateEngine();
    }

    /**
     * Starts a new run for a team asynchronously.
     * Uses async chunk loading to avoid server lag.
     */
    public CompletableFuture<RunState> startRunAsync(TeamState team) {
        // Select a random combat world
        ConfigService.CombatWorldConfig worldConfig = worldService.selectRandomWorld();
        if (worldConfig == null) {
            notifyTeam(team, "error.no_combat_worlds");
            resetTeamToLobby(team);
            return CompletableFuture.completedFuture(null);
        }

        // Create run state
        RunState run = state.createRun(team.getTeamId(), worldConfig.name);

        // Sample spawn points asynchronously
        int spawnCount = Math.max(team.getMemberCount() * 2, 5);

        return worldService.sampleSpawnPointsAsync(worldConfig, spawnCount)
                .thenApply(spawnPoints -> {
                    if (spawnPoints.isEmpty()) {
                        notifyTeam(team, "error.no_spawn_points");
                        state.endRun(run.getRunId());
                        resetTeamToLobby(team);
                        return null;
                    }

                    run.setSpawnPoints(spawnPoints);
                    run.start();

                    // Initialize player states
                    for (UUID memberId : team.getMembers()) {
                        initializePlayerForRun(memberId, run);
                    }

                    // Teleport players
                    teleportTeamToRun(team, run);

                    // Notify start
                    notifyTeam(team, "info.run_started", "world", worldConfig.displayName);

                    plugin.getLogger().info("Started run " + run.getRunId() + " for team " + team.getName() +
                            " in world " + worldConfig.name);

                    return run;
                });
    }

    /**
     * Starts a new run for a team (synchronous version).
     * @deprecated Use {@link #startRunAsync(TeamState)} for better performance.
     */
    @Deprecated
    public RunState startRun(TeamState team) {
        // Select a random combat world
        ConfigService.CombatWorldConfig worldConfig = worldService.selectRandomWorld();
        if (worldConfig == null) {
            notifyTeam(team, "error.no_combat_worlds");
            resetTeamToLobby(team);
            return null;
        }

        // Create run state
        RunState run = state.createRun(team.getTeamId(), worldConfig.name);

        // Sample spawn points
        int spawnCount = Math.max(team.getMemberCount() * 2, 5);
        List<Location> spawnPoints = worldService.sampleSpawnPoints(worldConfig, spawnCount);

        if (spawnPoints.isEmpty()) {
            notifyTeam(team, "error.no_spawn_points");
            state.endRun(run.getRunId());
            resetTeamToLobby(team);
            return null;
        }

        run.setSpawnPoints(spawnPoints);
        run.start();

        // Initialize player states
        for (UUID memberId : team.getMembers()) {
            initializePlayerForRun(memberId, run);
        }

        // Teleport players
        teleportTeamToRun(team, run);

        // Notify start
        notifyTeam(team, "info.run_started", "world", worldConfig.displayName);

        plugin.getLogger().info("Started run " + run.getRunId() + " for team " + team.getName() +
                " in world " + worldConfig.name);

        return run;
    }

    /**
     * Initializes a player's state for a new run.
     */
    private void initializePlayerForRun(UUID playerId, RunState run) {
        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        // Reset run-related state
        playerState.resetRunState();

        // Set initial XP required
        playerState.setXpRequired(config.getBaseXpRequired());

        // Set initial equipment from starters
        String weaponOptionId = playerState.getStarterWeaponOptionId();
        String helmetOptionId = playerState.getStarterHelmetOptionId();

        // Find starter configs
        for (var weapon : config.getStarterWeapons()) {
            if (weapon.optionId.equals(weaponOptionId)) {
                playerState.setWeaponGroup(weapon.group);
                playerState.setWeaponLevel(weapon.level);
                break;
            }
        }

        for (var helmet : config.getStarterHelmets()) {
            if (helmet.optionId.equals(helmetOptionId)) {
                playerState.setHelmetGroup(helmet.group);
                playerState.setHelmetLevel(helmet.level);
                break;
            }
        }

        // Set mode
        playerState.setMode(PlayerMode.IN_RUN);
        playerState.setRunId(run.getRunId());

        // Apply invulnerability
        long invulEnd = System.currentTimeMillis() + config.getRespawnInvulnerabilityMs();
        playerState.setInvulnerableUntilMillis(invulEnd);
    }

    /**
     * Teleports all team members to the run arena.
     */
    private void teleportTeamToRun(TeamState team, RunState run) {
        for (UUID memberId : team.getMembers()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player == null) continue;

            Location spawnPoint = run.getNextSpawnPoint();
            if (spawnPoint != null) {
                // Execute prep command if configured
                executeCommand(config.getPrepCommand(), player, spawnPoint);

                // Teleport
                player.teleport(spawnPoint);

                // Execute enter command if configured
                executeCommand(config.getEnterCommand(), player, spawnPoint);

                // Setup scoreboard
                plugin.getScoreboardService().setupSidebar(player);
            }
        }
    }

    /**
     * Ends a run normally or due to team wipe.
     */
    public void endRun(RunState run, EndReason reason) {
        if (run.isEnded()) return;

        run.end();

        UUID teamId = run.getTeamId();
        Optional<TeamState> teamOpt = state.getTeam(teamId);

        // Calculate rewards
        int totalKills = run.getTotalKills();
        int totalCoins = run.getTotalCoinsCollected();
        long duration = run.getElapsedSeconds();

        // Notify and teleport players back
        for (UUID memberId : run.getParticipants()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player == null) continue;

            // Remove scoreboard
            plugin.getScoreboardService().removeSidebar(player);

            // Send end message
            String msgKey = reason == EndReason.WIPE ? "run.ended_wipe" : "run.ended";
            i18n.send(player, msgKey,
                    "kills", totalKills,
                    "coins", totalCoins,
                    "time", run.getElapsedFormatted());

            // Apply cooldown if death-related
            if (reason == EndReason.WIPE || reason == EndReason.DEATH) {
                Optional<PlayerState> playerStateOpt = state.getPlayer(memberId);
                playerStateOpt.ifPresent(ps -> {
                    long cooldownEnd = System.currentTimeMillis() + config.getDeathCooldownMs();
                    ps.setCooldownUntilMillis(cooldownEnd);
                    ps.setMode(PlayerMode.COOLDOWN);
                });
            }
        }

        // Clean up state
        state.endRun(run.getRunId());

        // Reset team
        teamOpt.ifPresent(TeamState::resetForNewRun);

        // Save player states after run end
        PersistenceService persistence = plugin.getPersistenceService();
        if (persistence != null) {
            for (UUID memberId : run.getParticipants()) {
                persistence.savePlayerAsync(memberId);
            }
        }

        plugin.getLogger().info("Ended run " + run.getRunId() + " - Reason: " + reason +
                ", Kills: " + totalKills + ", Duration: " + duration + "s");
    }

    /**
     * Handles player death during a run.
     */
    public void handleDeath(Player player, RunState run) {
        UUID playerId = player.getUniqueId();
        run.markDead(playerId);

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        playerStateOpt.ifPresent(ps -> {
            // Reset equipment tracking (will get new on respawn based on level)
            // XP is preserved
        });

        // Check for team wipe
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            if (team.isWiped(run.getAlivePlayers(), config.getDisconnectGraceMs())) {
                endRun(run, EndReason.WIPE);
            }
        }
    }

    /**
     * Handles player respawn during a run.
     */
    public Location handleRespawn(Player player, RunState run) {
        UUID playerId = player.getUniqueId();
        run.markAlive(playerId);

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        playerStateOpt.ifPresent(ps -> {
            // Apply invulnerability
            long invulEnd = System.currentTimeMillis() + config.getRespawnInvulnerabilityMs();
            ps.setInvulnerableUntilMillis(invulEnd);
        });

        // Find respawn location (near alive teammate or spawn point)
        Location respawnLoc = findRespawnLocation(player, run);

        // Execute respawn command
        executeCommand(config.getRespawnCommand(), player, respawnLoc);

        return respawnLoc;
    }

    /**
     * Finds a respawn location for a player.
     */
    private Location findRespawnLocation(Player player, RunState run) {
        // Try to spawn near an alive teammate
        for (UUID aliveId : run.getAlivePlayers()) {
            if (aliveId.equals(player.getUniqueId())) continue;

            Player alive = Bukkit.getPlayer(aliveId);
            if (alive != null && alive.isOnline()) {
                Location nearTeammate = worldService.sampleSpawnNear(
                        alive.getLocation(),
                        config.getMinSpawnDistance(),
                        config.getMaxSpawnDistance()
                );
                if (nearTeammate != null) {
                    return nearTeammate;
                }
            }
        }

        // Fallback to random spawn point
        return run.getRandomSpawnPoint();
    }

    /**
     * Forces a run to end (admin command).
     */
    public void forceEnd(RunState run) {
        endRun(run, EndReason.FORCED);
    }

    /**
     * Handles a player leaving during a run.
     */
    public void handleLeave(UUID playerId, RunState run) {
        run.markDead(playerId);

        // Check for team wipe
        Optional<TeamState> teamOpt = state.getTeam(run.getTeamId());
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            if (team.isWiped(run.getAlivePlayers(), config.getDisconnectGraceMs())) {
                endRun(run, EndReason.WIPE);
            }
        }
    }

    /**
     * Executes a configured command with placeholders.
     */
    private void executeCommand(String command, Player player, Location location) {
        if (command == null || command.isEmpty()) return;

        templateEngine.context()
                .player(player)
                .location(location)
                .executeAsConsole(command);
    }

    private void notifyTeam(TeamState team, String key, Object... args) {
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                i18n.send(member, key, args);
            }
        }
    }

    private void resetTeamToLobby(TeamState team) {
        for (UUID memberId : team.getMembers()) {
            state.getPlayer(memberId).ifPresent(ps -> {
                ps.setMode(PlayerMode.LOBBY);
                ps.setReady(false);
            });
            team.setReady(memberId, false);
        }
    }

    /**
     * Reason for run ending.
     */
    public enum EndReason {
        NORMAL,     // Completed objectives (future)
        WIPE,       // All players dead
        DEATH,      // Single player death in solo
        FORCED,     // Admin forced end
        DISCONNECT  // All players disconnected
    }
}
