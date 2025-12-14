package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.ReadyService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Handles /vrs ready command to toggle ready state.
 */
public class ReadySubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final StateService state;
    private final ReadyService readyService;
    private final Random random = new Random();

    public ReadySubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.readyService = plugin.getReadyService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        // Ensure player state exists
        state.getOrCreatePlayer(playerId, player.getName());

        // Handle "solo" argument - create solo team if not in one
        if (args.length > 0 && args[0].equalsIgnoreCase("solo")) {
            if (!state.isInTeam(playerId)) {
                TeamState team = createSoloTeam(player);
                i18n.send(player, "success.solo_team_created", "name", team.getName());
            }
        }

        // Check if in team, show solo hint if not
        if (!state.isInTeam(playerId)) {
            i18n.send(player, "error.not_in_team_solo_hint");
            return;
        }

        // Delegate to ReadyService for validation and toggle
        // ReadyService.toggleReady() handles all validation and sends appropriate messages
        readyService.toggleReady(player);
    }

    /**
     * Creates a solo team for a player with a unique name.
     * Team name format: Team_{PlayerName}_{random 4 digits}
     */
    private TeamState createSoloTeam(Player player) {
        String baseName = "Team_" + player.getName() + "_";
        String teamName;

        // Generate unique name with 4-digit suffix
        do {
            teamName = baseName + String.format("%04d", random.nextInt(10000));
        } while (state.findTeamByName(teamName).isPresent());

        return state.createTeam(teamName, player.getUniqueId());
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("solo".startsWith(partial)) {
                return Collections.singletonList("solo");
            }
        }
        return Collections.emptyList();
    }
}
