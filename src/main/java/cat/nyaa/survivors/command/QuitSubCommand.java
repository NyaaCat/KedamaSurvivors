package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.service.RunService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

        // Apply quit cooldown
        long cooldownEnd = System.currentTimeMillis() +
                plugin.getConfigService().getQuitCooldownSeconds() * 1000L;
        playerState.setCooldownUntilMillis(cooldownEnd);
        playerState.setMode(PlayerMode.COOLDOWN);

        // Remove VRS equipment
        plugin.getStarterService().removeAllVrsEquipment(player);

        // Remove scoreboard
        plugin.getScoreboardService().removeSidebar(player);

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
