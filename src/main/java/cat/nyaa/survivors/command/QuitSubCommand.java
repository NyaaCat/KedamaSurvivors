package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.RunService;
import cat.nyaa.survivors.service.StatsService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Handles /vrs quit command for leaving the current run.
 */
public class QuitSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final StateService state;
    private final RunService runService;

    // Modes where quitting is allowed
    private static final Set<PlayerMode> QUITTABLE_MODES = Set.of(
            PlayerMode.COUNTDOWN,
            PlayerMode.IN_RUN
    );

    public QuitSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.runService = plugin.getRunService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        Optional<PlayerState> playerStateOpt = state.getPlayer(player.getUniqueId());

        if (playerStateOpt.isEmpty()) {
            i18n.send(sender, "error.not_in_game");
            return;
        }

        PlayerState playerState = playerStateOpt.get();
        PlayerMode mode = playerState.getMode();

        // Check if in a quittable state
        if (!QUITTABLE_MODES.contains(mode)) {
            i18n.send(sender, "error.cannot_quit_now");
            return;
        }

        // Handle countdown quit (cancel countdown)
        if (mode == PlayerMode.COUNTDOWN) {
            handleCountdownQuit(player, playerState);
            return;
        }

        // Handle in-run quit
        if (mode == PlayerMode.IN_RUN) {
            handleRunQuit(player, playerState);
        }
    }

    private void handleCountdownQuit(Player player, PlayerState playerState) {
        // Set player as not ready
        playerState.setReady(false);
        playerState.setMode(PlayerMode.LOBBY);

        // Update team ready state
        state.getPlayerTeam(player.getUniqueId()).ifPresent(team -> {
            team.setReady(player.getUniqueId(), false);
        });

        // Cancel team countdown if active
        plugin.getReadyService().handleDisconnect(player.getUniqueId());

        i18n.send(player, "quit.cancelled_countdown");
    }

    private void handleRunQuit(Player player, PlayerState playerState) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());

        // Get the run
        if (playerState.getRunId() == null) {
            i18n.send(player, "error.not_in_run");
            return;
        }

        Optional<RunState> runOpt = state.getRun(playerState.getRunId());
        if (runOpt.isEmpty()) {
            i18n.send(player, "error.run_not_found");
            return;
        }

        RunState run = runOpt.get();

        // Mark player as leaving (triggers death-like handling)
        runService.handleLeave(player.getUniqueId(), run);

        // Finalize this player's run stats as a failure (quit).
        StatsService statsService = plugin.getStatsService();
        if (statsService != null) {
            statsService.recordRunFailure(player.getUniqueId());
        }

        // Apply quit cooldown
        long cooldownEnd = System.currentTimeMillis() +
                plugin.getConfigService().getQuitCooldownSeconds() * 1000L;
        playerState.setCooldownUntilMillis(cooldownEnd);
        playerState.setMode(PlayerMode.COOLDOWN);

        // Quit counts as player progression reset and team exit
        playerState.clearStarterSelections();
        playerState.setReady(false);
        playerState.setRunId(null);
        state.removePlayerFromTeam(player.getUniqueId());

        teamOpt.ifPresent(team -> {
            for (UUID memberId : team.getMembers()) {
                Player member = org.bukkit.Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    i18n.send(member, "team.member_left", "player", player.getName());
                }
            }
        });

        // Teleport to lobby
        org.bukkit.Location lobby = plugin.getConfigService().getLobbyLocation();
        player.teleportAsync(lobby).thenAccept(success -> {
            if (!success) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> player.teleport(lobby));
            }
        });

        // Remove VRS equipment
        plugin.getStarterService().removeAllVrsEquipment(player);

        // Don't remove scoreboard - let it auto-update to lobby mode

        i18n.send(player, "quit.left_run");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }
}
