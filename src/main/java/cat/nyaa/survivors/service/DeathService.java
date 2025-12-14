package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles player death, respawn logic, and death penalties.
 */
public class DeathService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;

    public DeathService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    /**
     * Processes player death event.
     * Called from PlayerListener on PlayerDeathEvent.
     *
     * @param event The death event
     * @return true if the death was handled (player was in run)
     */
    public boolean handleDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return false;

        PlayerState playerState = playerStateOpt.get();
        if (playerState.getMode() != PlayerMode.IN_RUN) return false;

        // Clear drops to prevent item loss
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Mark as dead in run
        Optional<RunState> runOpt = state.getPlayerRun(playerId);
        if (runOpt.isEmpty()) return false;

        RunState run = runOpt.get();
        run.markDead(playerId);

        // Check for team respawn anchor
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            Optional<PlayerState> anchorOpt = findRespawnAnchor(team, playerState, run);

            if (anchorOpt.isPresent()) {
                // Team respawn available
                scheduleTeamRespawn(player, playerState, anchorOpt.get(), run);
                return true;
            }
        }

        // No respawn available - apply death penalty
        applyDeathPenalty(player, playerState, run);

        // Check for team wipe
        if (teamOpt.isPresent()) {
            checkTeamWipe(teamOpt.get(), run);
        }

        return true;
    }

    /**
     * Finds a living teammate to respawn near.
     */
    public Optional<PlayerState> findRespawnAnchor(TeamState team, PlayerState dying, RunState run) {
        Set<UUID> alivePlayers = run.getAlivePlayers();

        return team.getMembers().stream()
                .filter(uuid -> !uuid.equals(dying.getUuid()))
                .filter(alivePlayers::contains)
                .map(state::getPlayer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(ps -> ps.getMode() == PlayerMode.IN_RUN)
                .filter(ps -> Bukkit.getPlayer(ps.getUuid()) != null)
                .filter(ps -> Bukkit.getPlayer(ps.getUuid()).isOnline())
                .findFirst();
    }

    /**
     * Schedules respawn near a teammate.
     */
    private void scheduleTeamRespawn(Player player, PlayerState playerState,
                                     PlayerState anchor, RunState run) {
        Player anchorPlayer = Bukkit.getPlayer(anchor.getUuid());
        if (anchorPlayer == null || !anchorPlayer.isOnline()) {
            // Anchor went offline - apply death penalty instead
            applyDeathPenalty(player, playerState, run);
            return;
        }

        // Schedule respawn after short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Force respawn if still dead
            if (player.isDead()) {
                player.spigot().respawn();
            }

            // Teleport to anchor location
            Location respawnLoc = findSafeLocationNear(anchorPlayer.getLocation());
            player.teleport(respawnLoc);

            // Mark as alive again
            run.markAlive(player.getUniqueId());

            // Apply invulnerability
            applyRespawnInvulnerability(player, playerState);

            i18n.send(player, "info.respawned_to_team", "player", anchorPlayer.getName());

        }, 20L); // 1 second delay
    }

    /**
     * Applies respawn invulnerability and visual effect.
     */
    public void applyRespawnInvulnerability(Player player, PlayerState playerState) {
        long invulMs = config.getRespawnInvulnerabilityMs();
        playerState.setInvulnerableUntilMillis(System.currentTimeMillis() + invulMs);

        // Visual effect
        int ticks = (int) (invulMs / 50);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                ticks,
                0,
                false,
                false
        ));

        i18n.send(player, "respawn.invulnerability_start",
                "seconds", config.getRespawnInvulnerabilitySeconds());
    }

    /**
     * Applies death penalty: clears equipment, resets XP, applies cooldown.
     */
    public void applyDeathPenalty(Player player, PlayerState playerState, RunState run) {
        // Clear VRS equipment only (keep other items like coins)
        clearVrsEquipment(player);

        // Reset run state (XP, levels)
        playerState.resetRunState();

        // Set cooldown
        playerState.setCooldownUntilMillis(System.currentTimeMillis() + config.getDeathCooldownMs());
        playerState.setMode(PlayerMode.COOLDOWN);

        // Remove from run participants
        run.removeParticipant(player.getUniqueId());

        // Schedule respawn and teleport to prep area
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            if (player.isDead()) {
                player.spigot().respawn();
            }

            // Teleport to prep area
            teleportToPrep(player);

            i18n.send(player, "info.died_cooldown",
                    "seconds", config.getDeathCooldownSeconds());

        }, 20L);

        i18n.send(player, "info.died");
    }

    /**
     * Clears VRS equipment from a player's inventory.
     */
    private void clearVrsEquipment(Player player) {
        StarterService starter = plugin.getStarterService();

        // Clear main hand if VRS weapon
        if (starter.isVrsEquipment(player.getInventory().getItemInMainHand(), "weapon")) {
            player.getInventory().setItemInMainHand(null);
        }

        // Clear helmet if VRS helmet
        if (starter.isVrsEquipment(player.getInventory().getHelmet(), "helmet")) {
            player.getInventory().setHelmet(null);
        }
    }

    /**
     * Teleports player to the prep area.
     */
    private void teleportToPrep(Player player) {
        String prepCommand = config.getPrepCommand();
        if (prepCommand != null && !prepCommand.isEmpty()) {
            // Use configured command
            String expanded = prepCommand.replace("${player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expanded);
        } else {
            // Fallback to world spawn
            World world = Bukkit.getWorlds().get(0);
            player.teleport(world.getSpawnLocation());
        }
    }

    /**
     * Finds a safe location near the anchor point.
     */
    private Location findSafeLocationNear(Location center) {
        World world = center.getWorld();
        if (world == null) return center;

        // Try random offsets
        for (int attempts = 0; attempts < 10; attempts++) {
            double offsetX = ThreadLocalRandom.current().nextDouble(-3, 3);
            double offsetZ = ThreadLocalRandom.current().nextDouble(-3, 3);

            Location candidate = center.clone().add(offsetX, 0, offsetZ);
            candidate.setY(world.getHighestBlockYAt(candidate) + 1);

            if (isSafeLocation(candidate)) {
                return candidate;
            }
        }

        // Fallback to original location
        return center.clone().add(0, 1, 0);
    }

    /**
     * Checks if a location is safe for teleportation.
     */
    private boolean isSafeLocation(Location loc) {
        if (loc.getWorld() == null) return false;

        // Check for solid ground
        Location below = loc.clone().subtract(0, 1, 0);
        if (!below.getBlock().getType().isSolid()) return false;

        // Check for air at feet and head level
        if (!loc.getBlock().isPassable()) return false;
        if (!loc.clone().add(0, 1, 0).getBlock().isPassable()) return false;

        return true;
    }

    /**
     * Checks if the team has been wiped (all members dead or disconnected).
     */
    public void checkTeamWipe(TeamState team, RunState run) {
        if (isTeamWiped(team, run)) {
            handleTeamWipe(team, run);
        }
    }

    /**
     * Determines if all team members are dead.
     */
    public boolean isTeamWiped(TeamState team, RunState run) {
        Set<UUID> aliveInRun = run.getAlivePlayers();

        for (UUID memberId : team.getMembers()) {
            if (aliveInRun.contains(memberId)) {
                // Check if the player is actually in the run and online
                Optional<PlayerState> memberState = state.getPlayer(memberId);
                if (memberState.isPresent() && memberState.get().getMode() == PlayerMode.IN_RUN) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        return false; // At least one alive member
                    }
                }
            }
        }

        return true; // All wiped
    }

    /**
     * Handles team wipe - ends the run for all members.
     */
    private void handleTeamWipe(TeamState team, RunState run) {
        plugin.getLogger().info("Team " + team.getName() + " has been wiped!");

        // Notify all team members
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                i18n.send(member, "run.team_wiped");
            }
        }

        // End the run
        plugin.getRunService().endRun(run, RunService.EndReason.WIPE);
    }

    /**
     * Handles respawn event for in-run players.
     * Sets the respawn location based on game state.
     *
     * @param player The player respawning
     * @param playerState The player's state
     * @return The respawn location, or null to use default
     */
    public Location handleRespawn(Player player, PlayerState playerState) {
        if (playerState.getMode() != PlayerMode.IN_RUN) {
            return null;
        }

        Optional<RunState> runOpt = state.getPlayerRun(player.getUniqueId());
        if (runOpt.isEmpty()) {
            return null;
        }

        RunState run = runOpt.get();

        // Apply respawn invulnerability
        applyRespawnInvulnerability(player, playerState);

        // Return spawn point from run
        return run.getRandomSpawnPoint();
    }
}
