package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles /vrs team commands for team management.
 */
public class TeamSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;
    private final StateService state;

    public TeamSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        PlayerState playerState = state.getOrCreatePlayer(player.getUniqueId(), player.getName());

        if (args.length == 0) {
            showTeamStatus(player, playerState);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "create" -> handleCreate(player, playerState, args);
            case "invite" -> handleInvite(player, playerState, args);
            case "accept" -> handleAccept(player, playerState, args);
            case "decline" -> handleDecline(player, playerState, args);
            case "leave" -> handleLeave(player, playerState);
            case "kick" -> handleKick(player, playerState, args);
            case "disband" -> handleDisband(player, playerState);
            case "transfer" -> handleTransfer(player, playerState, args);
            case "list" -> handleList(player);
            default -> i18n.send(sender, "error.unknown_command", "command", action);
        }
    }

    private void handleCreate(Player player, PlayerState playerState, String[] args) {
        // Check if already in a team
        if (state.isInTeam(player.getUniqueId())) {
            i18n.send(player, "error.already_in_team");
            return;
        }

        // Check if in lobby
        if (playerState.getMode() != PlayerMode.LOBBY) {
            i18n.send(player, "error.not_in_lobby");
            return;
        }

        String teamName = args.length > 1 ? args[1] : player.getName() + "'s Team";

        // Check for duplicate names
        if (state.findTeamByName(teamName).isPresent()) {
            i18n.send(player, "error.team_name_taken", "name", teamName);
            return;
        }

        TeamState team = state.createTeam(teamName, player.getUniqueId());
        i18n.send(player, "team.created", "name", teamName);
    }

    private void handleInvite(Player player, PlayerState playerState, String[] args) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.not_in_team");
            return;
        }

        TeamState team = teamOpt.get();

        // Check if leader
        if (!team.isLeader(player.getUniqueId())) {
            i18n.send(player, "error.not_leader");
            return;
        }

        if (args.length < 2) {
            i18n.send(player, "error.specify_player");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            i18n.send(player, "error.player_not_found", "player", targetName);
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            i18n.send(player, "error.cannot_invite_self");
            return;
        }

        // Check team size
        if (team.getMemberCount() >= config.getMaxTeamSize()) {
            i18n.send(player, "error.team_full");
            return;
        }

        // Check if already in a team
        if (state.isInTeam(target.getUniqueId())) {
            i18n.send(player, "error.player_in_team", "player", target.getName());
            return;
        }

        // Add invite with expiry
        long expiryMillis = System.currentTimeMillis() + (config.getInviteExpirySeconds() * 1000L);
        team.addInvite(target.getUniqueId(), expiryMillis);

        i18n.send(player, "team.invite_sent", "player", target.getName());
        i18n.sendClickable(target, "team.invite_received",
                "/vrs team accept " + team.getName(),
                "inviter", player.getName(),
                "team", team.getName());
    }

    private void handleAccept(Player player, PlayerState playerState, String[] args) {
        if (state.isInTeam(player.getUniqueId())) {
            i18n.send(player, "error.already_in_team");
            return;
        }

        if (args.length < 2) {
            i18n.send(player, "error.specify_team");
            return;
        }

        String teamName = args[1];
        Optional<TeamState> teamOpt = state.findTeamByName(teamName);

        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.team_not_found", "team", teamName);
            return;
        }

        TeamState team = teamOpt.get();

        if (!team.hasInvite(player.getUniqueId())) {
            i18n.send(player, "error.no_invite");
            return;
        }

        // Check team size again
        if (team.getMemberCount() >= config.getMaxTeamSize()) {
            i18n.send(player, "error.team_full");
            return;
        }

        team.removeInvite(player.getUniqueId());
        state.addPlayerToTeam(player.getUniqueId(), team.getTeamId());

        i18n.send(player, "team.joined", "team", team.getName());

        // Notify team members
        for (UUID memberId : team.getMembers()) {
            if (!memberId.equals(player.getUniqueId())) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    i18n.send(member, "team.member_joined", "player", player.getName());
                }
            }
        }
    }

    private void handleDecline(Player player, PlayerState playerState, String[] args) {
        if (args.length < 2) {
            i18n.send(player, "error.specify_team");
            return;
        }

        String teamName = args[1];
        Optional<TeamState> teamOpt = state.findTeamByName(teamName);

        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.team_not_found", "team", teamName);
            return;
        }

        TeamState team = teamOpt.get();
        team.removeInvite(player.getUniqueId());
        i18n.send(player, "team.invite_declined", "team", teamName);
    }

    private void handleLeave(Player player, PlayerState playerState) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.not_in_team");
            return;
        }

        TeamState team = teamOpt.get();

        // Check if in run
        if (playerState.getMode() == PlayerMode.IN_RUN) {
            i18n.send(player, "error.cannot_leave_in_run");
            return;
        }

        String teamName = team.getName();
        state.removePlayerFromTeam(player.getUniqueId());

        i18n.send(player, "team.left", "team", teamName);

        // Notify remaining members
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                i18n.send(member, "team.member_left", "player", player.getName());
            }
        }
    }

    private void handleKick(Player player, PlayerState playerState, String[] args) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.not_in_team");
            return;
        }

        TeamState team = teamOpt.get();

        if (!team.isLeader(player.getUniqueId())) {
            i18n.send(player, "error.not_leader");
            return;
        }

        if (args.length < 2) {
            i18n.send(player, "error.specify_player");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        UUID targetId = target != null ? target.getUniqueId() : null;

        // Try to find by name in team if player is offline
        if (targetId == null) {
            for (UUID memberId : team.getMembers()) {
                Optional<PlayerState> memberState = state.getPlayer(memberId);
                if (memberState.isPresent() && memberState.get().getName().equalsIgnoreCase(targetName)) {
                    targetId = memberId;
                    break;
                }
            }
        }

        if (targetId == null || !team.isMember(targetId)) {
            i18n.send(player, "error.player_not_in_team", "player", targetName);
            return;
        }

        if (targetId.equals(player.getUniqueId())) {
            i18n.send(player, "error.cannot_kick_self");
            return;
        }

        state.removePlayerFromTeam(targetId);
        i18n.send(player, "team.kicked", "player", targetName);

        if (target != null) {
            i18n.send(target, "team.was_kicked", "team", team.getName());
        }
    }

    private void handleDisband(Player player, PlayerState playerState) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.not_in_team");
            return;
        }

        TeamState team = teamOpt.get();

        if (!team.isLeader(player.getUniqueId())) {
            i18n.send(player, "error.not_leader");
            return;
        }

        // Check if in run
        if (state.getTeamRun(team.getTeamId()).isPresent()) {
            i18n.send(player, "error.cannot_disband_in_run");
            return;
        }

        String teamName = team.getName();
        Set<UUID> members = team.getMembers();

        state.disbandTeam(team.getTeamId());

        // Notify all members
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                i18n.send(member, "team.disbanded", "team", teamName);
            }
        }
    }

    private void handleTransfer(Player player, PlayerState playerState, String[] args) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            i18n.send(player, "error.not_in_team");
            return;
        }

        TeamState team = teamOpt.get();

        if (!team.isLeader(player.getUniqueId())) {
            i18n.send(player, "error.not_leader");
            return;
        }

        if (args.length < 2) {
            i18n.send(player, "error.specify_player");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            i18n.send(player, "error.player_not_found", "player", targetName);
            return;
        }

        if (!team.isMember(target.getUniqueId())) {
            i18n.send(player, "error.player_not_in_team", "player", targetName);
            return;
        }

        team.transferLeadership(target.getUniqueId());

        i18n.send(player, "team.leadership_transferred", "player", target.getName());
        i18n.send(target, "team.became_leader", "team", team.getName());
    }

    private void handleList(Player player) {
        Collection<TeamState> teams = state.getAllTeams();

        if (teams.isEmpty()) {
            i18n.send(player, "team.no_teams");
            return;
        }

        i18n.send(player, "team.list_header");
        for (TeamState team : teams) {
            i18n.send(player, "team.list_entry",
                    "name", team.getName(),
                    "count", team.getMemberCount(),
                    "max", config.getMaxTeamSize());
        }
    }

    private void showTeamStatus(Player player, PlayerState playerState) {
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());

        if (teamOpt.isEmpty()) {
            i18n.send(player, "team.not_in_team");
            i18n.send(player, "team.help");
            return;
        }

        TeamState team = teamOpt.get();
        i18n.send(player, "team.status_header", "name", team.getName());

        for (UUID memberId : team.getMembers()) {
            Optional<PlayerState> memberState = state.getPlayer(memberId);
            String name = memberState.map(PlayerState::getName).orElse("Unknown");
            boolean isLeader = team.isLeader(memberId);
            boolean isReady = team.isReady(memberId);

            String status = isLeader ? i18n.get("team.leader") :
                           isReady ? i18n.get("team.ready") : i18n.get("team.not_ready");

            i18n.send(player, "team.member_entry",
                    "player", name,
                    "status", status);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("create", "invite", "accept", "decline", "leave", "kick", "disband", "transfer", "list")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (List.of("invite", "kick", "transfer").contains(action)) {
                // Complete online player names
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
            } else if (List.of("accept", "decline").contains(action)) {
                // Complete team names
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
    public boolean isPlayerOnly() {
        return true;
    }
}
