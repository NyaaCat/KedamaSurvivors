package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.SoundConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

        // Keep inventory on death - we'll only clear VRS equipment in applyDeathPenalty
        event.setKeepInventory(true);
        event.setKeepLevel(false);  // Still reset XP level
        event.setDroppedExp(0);

        // Mark as dead in run
        Optional<RunState> runOpt = state.getPlayerRun(playerId);
        if (runOpt.isEmpty()) return false;

        RunState run = runOpt.get();
        run.markDead(playerId);

        // Always apply death penalty - player must re-prepare to rejoin
        applyDeathPenalty(player, playerState, run);

        // Check for team wipe
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
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
        // Play death sound
        SoundConfig deathSound = config.getSoundDeath();
        if (deathSound != null) {
            deathSound.play(player);
        }

        // Clear VRS equipment only (keep other items like coins)
        clearVrsEquipment(player);

        // Reset run state (XP, levels)
        playerState.resetRunState();

        // Set cooldown
        playerState.setCooldownUntilMillis(System.currentTimeMillis() + config.getDeathCooldownMs());
        playerState.setMode(PlayerMode.COOLDOWN);

        // Remove from run participants
        run.removeParticipant(player.getUniqueId());

        // Check if teammates are still alive (for appropriate message)
        boolean hasLivingTeammates = run.getAliveCount() > 0;

        // Schedule respawn and teleport to prep area
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            if (player.isDead()) {
                player.spigot().respawn();
            }

            // Teleport to prep area
            teleportToPrep(player);

            // Send appropriate message based on whether teammates are still alive
            if (hasLivingTeammates) {
                i18n.send(player, "info.died_can_rejoin",
                        "seconds", config.getDeathCooldownSeconds());
            } else {
                i18n.send(player, "info.died_cooldown",
                        "seconds", config.getDeathCooldownSeconds());
            }

        }, 20L);

        i18n.send(player, "info.died");
    }

    /**
     * Clears all VRS equipment from a player's inventory.
     */
    private void clearVrsEquipment(Player player) {
        StarterService starter = plugin.getStarterService();
        var inventory = player.getInventory();

        // Clear helmet slot if VRS helmet
        if (starter.isVrsEquipment(inventory.getHelmet(), "helmet")) {
            inventory.setHelmet(null);
        }

        // Scan entire inventory for VRS weapons (player may have moved it)
        for (int i = 0; i < inventory.getSize(); i++) {
            var item = inventory.getItem(i);
            if (starter.isVrsEquipment(item, "weapon")) {
                inventory.setItem(i, null);
            }
        }
    }

    /**
     * Teleports player to the lobby area.
     */
    private void teleportToPrep(Player player) {
        Location lobby = config.getLobbyLocation();
        player.teleportAsync(lobby).thenAccept(success -> {
            if (!success) {
                // Fallback to sync teleport if async fails
                Bukkit.getScheduler().runTask(plugin, () -> player.teleport(lobby));
            }
        });
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

        // Cancel any active countdown to prevent race conditions where
        // a player in COUNTDOWN mode would start a new run after team wipe
        plugin.getReadyService().cancelCountdown(team.getTeamId());

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
