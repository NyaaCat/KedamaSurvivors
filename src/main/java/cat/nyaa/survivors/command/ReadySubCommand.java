package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles /vrs ready command to toggle ready state.
 */
public class ReadySubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final StateService state;

    public ReadySubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        PlayerState playerState = state.getOrCreatePlayer(player.getUniqueId(), player.getName());

        // Must be in lobby
        if (playerState.getMode() != PlayerMode.LOBBY) {
            i18n.send(sender, "error.not_in_lobby");
            return;
        }

        // Must be in a team
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            i18n.send(sender, "error.not_in_team");
            return;
        }

        // Must have selected starters
        if (!playerState.hasSelectedStarters()) {
            i18n.send(sender, "error.select_starters_first");
            return;
        }

        TeamState team = teamOpt.get();

        // Toggle ready state
        boolean wasReady = playerState.isReady();
        boolean isNowReady = !wasReady;

        playerState.setReady(isNowReady);
        team.setReady(player.getUniqueId(), isNowReady);

        if (isNowReady) {
            i18n.send(sender, "ready.now_ready");
            notifyTeam(team, player, true);

            // Check if all team members are ready
            if (team.isAllReady()) {
                triggerCountdown(team);
            }
        } else {
            i18n.send(sender, "ready.no_longer_ready");
            notifyTeam(team, player, false);
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

    private void triggerCountdown(TeamState team) {
        // Notify all team members
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                i18n.send(member, "ready.all_ready");

                // Update player mode
                Optional<PlayerState> playerState = state.getPlayer(memberId);
                playerState.ifPresent(ps -> ps.setMode(PlayerMode.COUNTDOWN));
            }
        }

        // TODO: Start actual countdown task via ReadyService
        plugin.getLogger().info("Team " + team.getName() + " is ready! Starting countdown...");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }
}
