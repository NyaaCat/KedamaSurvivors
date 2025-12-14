package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles /vrs admin commands for server administration.
 */
public class AdminSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final StateService state;

    public AdminSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showAdminHelp(sender);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "status" -> showStatus(sender);
            case "endrun" -> handleEndRun(sender, args);
            case "forcestart" -> handleForceStart(sender, args);
            case "kick" -> handleKick(sender, args);
            case "reset" -> handleReset(sender, args);
            case "setperma" -> handleSetPerma(sender, args);
            default -> i18n.send(sender, "error.unknown_command", "command", action);
        }
    }

    private void showAdminHelp(CommandSender sender) {
        i18n.send(sender, "admin.help.header");
        i18n.send(sender, "admin.help.status");
        i18n.send(sender, "admin.help.endrun");
        i18n.send(sender, "admin.help.forcestart");
        i18n.send(sender, "admin.help.kick");
        i18n.send(sender, "admin.help.reset");
        i18n.send(sender, "admin.help.setperma");
    }

    private void showStatus(CommandSender sender) {
        int playerCount = state.getAllPlayers().size();
        int teamCount = state.getActiveTeamCount();
        int runCount = state.getActiveRunCount();
        int inRunCount = state.getPlayersInRunCount();

        i18n.send(sender, "admin.status.header");
        i18n.send(sender, "admin.status.players", "count", playerCount);
        i18n.send(sender, "admin.status.teams", "count", teamCount);
        i18n.send(sender, "admin.status.runs", "count", runCount);
        i18n.send(sender, "admin.status.in_run", "count", inRunCount);
    }

    private void handleEndRun(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // End all runs or specify team
            if (state.getActiveRunCount() == 0) {
                i18n.send(sender, "admin.no_active_runs");
                return;
            }

            // End all runs
            for (RunState run : state.getAllRuns()) {
                if (run.isActive()) {
                    state.endRun(run.getRunId());
                }
            }
            i18n.send(sender, "admin.all_runs_ended");
            return;
        }

        String teamName = args[1];
        Optional<TeamState> teamOpt = state.findTeamByName(teamName);

        if (teamOpt.isEmpty()) {
            i18n.send(sender, "error.team_not_found", "team", teamName);
            return;
        }

        TeamState team = teamOpt.get();
        Optional<RunState> runOpt = state.getTeamRun(team.getTeamId());

        if (runOpt.isEmpty()) {
            i18n.send(sender, "admin.team_not_in_run", "team", teamName);
            return;
        }

        state.endRun(runOpt.get().getRunId());
        i18n.send(sender, "admin.run_ended", "team", teamName);
    }

    private void handleForceStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "admin.specify_team");
            return;
        }

        String teamName = args[1];
        Optional<TeamState> teamOpt = state.findTeamByName(teamName);

        if (teamOpt.isEmpty()) {
            i18n.send(sender, "error.team_not_found", "team", teamName);
            return;
        }

        TeamState team = teamOpt.get();

        if (state.getTeamRun(team.getTeamId()).isPresent()) {
            i18n.send(sender, "admin.team_already_in_run", "team", teamName);
            return;
        }

        // TODO: Trigger run start via ReadyService
        i18n.send(sender, "admin.force_start", "team", teamName);
        plugin.getLogger().info("Admin force-started run for team: " + teamName);
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "error.specify_player");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        Optional<PlayerState> playerState = state.getPlayer(target.getUniqueId());
        if (playerState.isEmpty()) {
            i18n.send(sender, "admin.player_not_in_game", "player", playerName);
            return;
        }

        // Remove from team
        state.removePlayerFromTeam(target.getUniqueId());

        // Reset player state
        playerState.get().resetAll();

        i18n.send(sender, "admin.player_kicked", "player", playerName);
        i18n.send(target, "admin.you_were_kicked");
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "error.specify_player");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        Optional<PlayerState> playerState = state.getPlayer(target.getUniqueId());
        if (playerState.isEmpty()) {
            i18n.send(sender, "admin.player_not_in_game", "player", playerName);
            return;
        }

        playerState.get().resetAll();
        i18n.send(sender, "admin.player_reset", "player", playerName);
    }

    private void handleSetPerma(CommandSender sender, String[] args) {
        if (args.length < 3) {
            i18n.send(sender, "admin.setperma_usage");
            return;
        }

        String playerName = args[1];
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[2]);
            return;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        PlayerState playerState = state.getOrCreatePlayer(target.getUniqueId(), target.getName());
        playerState.setPermaScore(amount);

        // TODO: Update scoreboard

        i18n.send(sender, "admin.perma_set", "player", playerName, "amount", amount);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("status", "endrun", "forcestart", "kick", "reset", "setperma")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (List.of("kick", "reset", "setperma").contains(action)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
            } else if (List.of("endrun", "forcestart").contains(action)) {
                for (TeamState team : state.getAllTeams()) {
                    if (team.getName().toLowerCase().startsWith(partial)) {
                        completions.add(team.getName());
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin";
    }
}
