package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.SoundConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.*;
import cat.nyaa.survivors.util.TemplateEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

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
    private final MerchantService merchantService;

    public RunService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.worldService = plugin.getWorldService();
        this.templateEngine = plugin.getTemplateEngine();
        this.merchantService = plugin.getMerchantService();
    }

    /**
     * Starts a new run for a team asynchronously.
     * Uses Paper's teleportAsync for async chunk loading.
     */
    public CompletableFuture<RunState> startRunAsync(TeamState team) {
        // Select a random combat world
        ConfigService.CombatWorldConfig worldConfig = worldService.selectRandomWorld();
        if (worldConfig == null) {
            notifyTeam(team, "error.no_combat_worlds");
            resetTeamToLobby(team);
            return CompletableFuture.completedFuture(null);
        }

        // Check if world has spawn points configured
        if (!worldConfig.hasSpawnPoints()) {
            plugin.getLogger().warning("No spawn points configured for world: " + worldConfig.name);
            notifyTeam(team, "error.no_spawn_points");
            resetTeamToLobby(team);
            return CompletableFuture.completedFuture(null);
        }

        World world = Bukkit.getWorld(worldConfig.name);
        if (world == null) {
            plugin.getLogger().warning("World not loaded: " + worldConfig.name);
            notifyTeam(team, "error.no_combat_worlds");
            resetTeamToLobby(team);
            return CompletableFuture.completedFuture(null);
        }

        // Create run state
        RunState run = state.createRun(team.getTeamId(), worldConfig.name);
        run.start();

        // Initialize player states
        for (UUID memberId : team.getMembers()) {
            initializePlayerForRun(memberId, run);
        }

        // Teleport players using Paper async teleport
        teleportTeamToRunAsync(team, run, worldConfig, world);

        // Start merchant spawning for this run
        if (merchantService != null) {
            merchantService.startForRun(run);
        }

        // Notify start and play run start sound
        notifyTeam(team, "info.run_started", "world", worldConfig.displayName);
        playRunStartSound(team);

        plugin.getLogger().info("Started run " + run.getRunId() + " for team " + team.getName() +
                " in world " + worldConfig.name);

        return CompletableFuture.completedFuture(run);
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

        // Check if world has spawn points configured
        if (!worldConfig.hasSpawnPoints()) {
            plugin.getLogger().warning("No spawn points configured for world: " + worldConfig.name);
            notifyTeam(team, "error.no_spawn_points");
            resetTeamToLobby(team);
            return null;
        }

        World world = Bukkit.getWorld(worldConfig.name);
        if (world == null) {
            plugin.getLogger().warning("World not loaded: " + worldConfig.name);
            notifyTeam(team, "error.no_combat_worlds");
            resetTeamToLobby(team);
            return null;
        }

        // Create run state
        RunState run = state.createRun(team.getTeamId(), worldConfig.name);
        run.start();

        // Initialize player states
        for (UUID memberId : team.getMembers()) {
            initializePlayerForRun(memberId, run);
        }

        // Teleport players using Paper async teleport
        teleportTeamToRunAsync(team, run, worldConfig, world);

        // Start merchant spawning for this run
        if (merchantService != null) {
            merchantService.startForRun(run);
        }

        // Notify start and play run start sound
        notifyTeam(team, "info.run_started", "world", worldConfig.displayName);
        playRunStartSound(team);

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

        // Reset run-related state BUT preserve starter selections (they were just made!)
        // Note: We don't call resetRunState() here because it clears starterWeaponOptionId/starterHelmetOptionId
        // which we need to look up the weapon/helmet group. This mirrors initializePlayerForRejoin().
        playerState.setXpProgress(0);
        playerState.setXpHeld(0);
        playerState.setUpgradePending(false);
        playerState.setUpgradeDeadlineMillis(0);
        playerState.setSuggestedUpgrade(null);
        playerState.setOverflowXpAccumulated(0);
        playerState.setWeaponAtMax(false);
        playerState.setHelmetAtMax(false);
        playerState.setRunLevel(1);
        // Note: coinsEarned is not reset here as there's no setter (will be reset via resetRunState on run end)
        playerState.setRunId(null);
        playerState.setReady(false);

        // Set initial XP required
        playerState.setXpRequired(config.getBaseXpRequired());

        // Set initial equipment from starters
        String weaponOptionId = playerState.getStarterWeaponOptionId();
        String helmetOptionId = playerState.getStarterHelmetOptionId();

        // Find starter configs
        boolean foundWeapon = false;
        for (var weapon : config.getStarterWeapons()) {
            if (weapon.optionId.equals(weaponOptionId)) {
                foundWeapon = true;
                if (weapon.group == null || weapon.group.isEmpty()) {
                    plugin.getLogger().warning("Starter weapon '" + weapon.optionId +
                        "' has no group configured! Upgrades will fail.");
                } else {
                    plugin.getLogger().info("Found starter weapon: optionId=" + weapon.optionId +
                        ", group=" + weapon.group + ", level=" + weapon.level);
                }
                playerState.setWeaponGroup(weapon.group);
                playerState.setWeaponLevel(weapon.level);
                break;
            }
        }
        if (!foundWeapon && weaponOptionId != null) {
            plugin.getLogger().warning("Could not find starter weapon config for optionId=" + weaponOptionId +
                ". Available: " + config.getStarterWeapons().stream()
                    .map(w -> w.optionId).toList());
        }

        boolean foundHelmet = false;
        for (var helmet : config.getStarterHelmets()) {
            if (helmet.optionId.equals(helmetOptionId)) {
                foundHelmet = true;
                if (helmet.group == null || helmet.group.isEmpty()) {
                    plugin.getLogger().warning("Starter helmet '" + helmet.optionId +
                        "' has no group configured! Upgrades will fail.");
                } else {
                    plugin.getLogger().info("Found starter helmet: optionId=" + helmet.optionId +
                        ", group=" + helmet.group + ", level=" + helmet.level);
                }
                playerState.setHelmetGroup(helmet.group);
                playerState.setHelmetLevel(helmet.level);
                break;
            }
        }
        if (!foundHelmet && helmetOptionId != null) {
            plugin.getLogger().warning("Could not find starter helmet config for optionId=" + helmetOptionId +
                ". Available: " + config.getStarterHelmets().stream()
                    .map(h -> h.optionId).toList());
        }

        // Set mode
        playerState.setMode(PlayerMode.IN_RUN);
        playerState.setRunId(run.getRunId());

        // Apply invulnerability
        long invulEnd = System.currentTimeMillis() + config.getRespawnInvulnerabilityMs();
        playerState.setInvulnerableUntilMillis(invulEnd);

        // Record run start in stats service
        StatsService statsService = plugin.getStatsService();
        if (statsService != null) {
            statsService.recordRunStart(playerId);
        }
    }

    /**
     * Teleports all team members to the run arena using Paper's async teleport API.
     * This allows async chunk loading for smoother teleportation.
     * All team members spawn at the same base spawn point with small offsets.
     */
    private void teleportTeamToRunAsync(TeamState team, RunState run,
                                         ConfigService.CombatWorldConfig worldConfig, World world) {
        // Select ONE spawn point for the entire team
        Location teamSpawnPoint = worldConfig.getRandomSpawnPoint(world);
        if (teamSpawnPoint == null) {
            plugin.getLogger().severe("No spawn point available for team " + team.getName());
            notifyTeam(team, "error.no_spawn_points");
            return;
        }

        // Store the team's base spawn point in run state for respawns
        run.addSpawnPoint(teamSpawnPoint);

        for (UUID memberId : team.getMembers()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player == null) continue;

            Optional<PlayerState> playerStateOpt = state.getPlayer(memberId);
            if (playerStateOpt.isEmpty()) continue;

            // Sanitize inventory before teleport to prevent item duplication
            sanitizePlayerInventory(player, playerStateOpt.get());

            // Apply random offset to prevent players stacking on exact same location
            Location playerSpawnPoint = applySpawnOffset(teamSpawnPoint);

            // Use Paper's async teleport API for async chunk loading
            final Location finalSpawnPoint = playerSpawnPoint;
            player.teleportAsync(playerSpawnPoint).thenAccept(success -> {
                // All post-teleport actions run on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        // Execute enter command if configured (optional hooks only)
                        executeCommand(config.getEnterCommand(), player, finalSpawnPoint);
                    } else {
                        // Fallback: try sync teleport if async failed
                        plugin.getLogger().warning("Async teleport failed for " + player.getName() + ", trying sync");
                        player.teleport(finalSpawnPoint);
                    }

                    // Show world title with display name
                    plugin.getI18nService().sendWorldTitle(player, worldConfig.displayName);

                    // Starter equipment is already granted in GUI selection - just setup scoreboard
                    plugin.getScoreboardService().setupSidebar(player);

                    plugin.getLogger().info("Teleported " + player.getName() + " to " +
                            finalSpawnPoint.getWorld().getName() + " at " +
                            String.format("%.1f, %.1f, %.1f", finalSpawnPoint.getX(),
                                    finalSpawnPoint.getY(), finalSpawnPoint.getZ()));
                });
            });
        }
    }

    /**
     * Rejoins a player to an existing run after death/respawn.
     * Teleports them to a living teammate.
     */
    public void rejoinPlayerToRun(Player player, PlayerState playerState, RunState run) {
        UUID playerId = player.getUniqueId();

        // Find a living teammate to spawn near
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.not_in_team");
            return;
        }

        TeamState team = teamOpt.get();
        Optional<PlayerState> anchorOpt = plugin.getDeathService().findRespawnAnchor(team, playerState, run);

        if (anchorOpt.isEmpty()) {
            // No living teammates - can't rejoin
            i18n.send(player, "error.no_teammates_alive");
            // Reset player state back to LOBBY since rejoin failed
            playerState.setMode(PlayerMode.LOBBY);
            playerState.setReady(false);
            return;
        }

        PlayerState anchor = anchorOpt.get();
        Player anchorPlayer = Bukkit.getPlayer(anchor.getUuid());

        if (anchorPlayer == null || !anchorPlayer.isOnline()) {
            i18n.send(player, "error.no_teammates_alive");
            // Reset player state back to LOBBY since rejoin failed
            playerState.setMode(PlayerMode.LOBBY);
            playerState.setReady(false);
            return;
        }

        // Clear cooldown since they're rejoining
        playerState.setCooldownUntilMillis(0);
        playerState.setReady(false);

        // Reinitialize player for run (resets xp/level but keeps starter selections)
        initializePlayerForRejoin(playerId, run);

        // Add player back to run
        run.addParticipant(playerId);
        run.markAlive(playerId);

        // Teleport to anchor location
        Location anchorLoc = anchorPlayer.getLocation();
        Location spawnLoc = findSafeLocationNear(anchorLoc);

        player.teleportAsync(spawnLoc).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!success) {
                    player.teleport(spawnLoc);
                }

                // Grant starter equipment
                plugin.getStarterService().grantStarterEquipment(player, playerState);

                // Setup scoreboard
                plugin.getScoreboardService().setupSidebar(player);

                // Apply respawn invulnerability
                plugin.getDeathService().applyRespawnInvulnerability(player, playerState);

                // Notify
                i18n.send(player, "info.rejoined_run", "player", anchorPlayer.getName());

                // Notify teammates
                for (UUID memberId : team.getMembers()) {
                    if (!memberId.equals(playerId)) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            i18n.send(member, "info.teammate_rejoined", "player", player.getName());
                        }
                    }
                }

                // Play teleport sound
                if (config.getTeleportSound() != null) {
                    player.playSound(player.getLocation(), config.getTeleportSound(), 1.0f, 1.0f);
                }

                plugin.getLogger().info("Player " + player.getName() + " rejoined run " + run.getRunId());
            });
        });
    }

    /**
     * Initializes a player's state for rejoining a run (after death).
     * Similar to initializePlayerForRun but preserves some state.
     */
    private void initializePlayerForRejoin(UUID playerId, RunState run) {
        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        // Reset run-related state (XP, levels, etc.)
        playerState.setXpProgress(0);
        playerState.setXpHeld(0);
        playerState.setUpgradePending(false);
        playerState.setUpgradeDeadlineMillis(0);
        playerState.setSuggestedUpgrade(null);
        playerState.setOverflowXpAccumulated(0);
        playerState.setWeaponAtMax(false);
        playerState.setHelmetAtMax(false);
        playerState.setRunLevel(1);
        // Note: coinsEarned is not reset here as there's no setter

        // Set initial XP required
        playerState.setXpRequired(config.getBaseXpRequired());

        // Restore equipment from starters (these were selected before rejoining)
        String weaponOptionId = playerState.getStarterWeaponOptionId();
        String helmetOptionId = playerState.getStarterHelmetOptionId();

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
     * Finds a safe location near a center point.
     */
    private Location findSafeLocationNear(Location center) {
        if (center.getWorld() == null) return center;

        for (int attempts = 0; attempts < 10; attempts++) {
            double offsetX = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-3, 3);
            double offsetZ = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-3, 3);

            Location candidate = center.clone().add(offsetX, 0, offsetZ);
            // Preserve Y from center (teammate location) - don't use getHighestBlockYAt

            if (isSafeLocation(candidate)) {
                return candidate;
            }
        }

        return center.clone().add(0, 1, 0);
    }

    /**
     * Checks if a location is safe for teleportation.
     */
    private boolean isSafeLocation(Location loc) {
        if (loc.getWorld() == null) return false;

        Location below = loc.clone().subtract(0, 1, 0);
        if (!below.getBlock().getType().isSolid()) return false;

        if (!loc.getBlock().isPassable()) return false;
        if (!loc.clone().add(0, 1, 0).getBlock().isPassable()) return false;

        return true;
    }

    /**
     * Applies a random offset to a spawn location to prevent players stacking.
     * @param base The base spawn location
     * @return A new location with small random offset applied
     */
    private Location applySpawnOffset(Location base) {
        if (base == null || base.getWorld() == null) return base;

        double offsetRange = config.getTeamSpawnOffsetRange();
        if (offsetRange <= 0) {
            return base.clone();
        }

        double offsetX = ThreadLocalRandom.current().nextDouble(-offsetRange, offsetRange);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-offsetRange, offsetRange);

        Location offset = base.clone().add(offsetX, 0, offsetZ);
        // Preserve Y from configured spawn point (don't use getHighestBlockYAt which causes rooftop spawning)

        // Preserve yaw and pitch from base location
        offset.setYaw(base.getYaw());
        offset.setPitch(base.getPitch());

        // Ensure safe location
        if (!isSafeLocation(offset)) {
            // Fall back to base location if offset is unsafe
            return base.clone();
        }

        return offset;
    }

    /**
     * Sanitizes player inventory before entering combat.
     * Removes all VRS equipment and re-grants selected starters to prevent duplication.
     */
    private void sanitizePlayerInventory(Player player, PlayerState playerState) {
        StarterService starter = plugin.getStarterService();

        // Remove ALL existing VRS equipment from player inventory
        starter.removeAllVrsEquipment(player);

        // Validate and clear ender chest
        int enderChestRemoved = starter.validateAndClearEnderChest(player);
        if (enderChestRemoved > 0) {
            plugin.getLogger().info("Removed " + enderChestRemoved +
                " VRS items from " + player.getName() + "'s ender chest");
            // Send warning message to player
            plugin.getI18nService().send(player, "warning.ender_chest_cleared",
                "count", String.valueOf(enderChestRemoved));
        }

        // Re-grant the selected starter equipment fresh
        starter.grantStarterEquipment(player, playerState);

        plugin.getLogger().info("Sanitized inventory for " + player.getName() + " before combat teleport");
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

        // Record run end for all participants
        StatsService statsService = plugin.getStatsService();

        // Notify and teleport players back
        for (UUID memberId : run.getParticipants()) {
            // Record run end in stats service
            if (statsService != null) {
                statsService.recordRunEnd(memberId);
            }

            Player player = Bukkit.getPlayer(memberId);
            if (player == null) continue;

            // Don't remove scoreboard - let it auto-update to lobby mode

            // Send end message
            String msgKey = reason == EndReason.WIPE ? "run.ended_wipe" : "run.ended";
            i18n.send(player, msgKey,
                    "kills", totalKills,
                    "coins", totalCoins,
                    "time", run.getElapsedFormatted());

            // Apply cooldown and clear starter selections
            Optional<PlayerState> playerStateOpt = state.getPlayer(memberId);
            playerStateOpt.ifPresent(ps -> {
                // Clear starter selections so player must re-select next run
                ps.setStarterWeaponOptionId(null);
                ps.setStarterHelmetOptionId(null);

                if (reason == EndReason.WIPE || reason == EndReason.DEATH) {
                    long cooldownEnd = System.currentTimeMillis() + config.getDeathCooldownMs();
                    ps.setCooldownUntilMillis(cooldownEnd);
                    ps.setMode(PlayerMode.COOLDOWN);
                } else {
                    ps.setMode(PlayerMode.LOBBY);
                }
            });
        }

        // Clean up state
        state.endRun(run.getRunId());

        // Stop merchant spawning for this run
        if (merchantService != null) {
            merchantService.stopForRun(run.getRunId());
        }

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

    private void playRunStartSound(TeamState team) {
        SoundConfig sound = config.getSoundRunStart();
        if (sound == null) return;

        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                sound.play(member);
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
