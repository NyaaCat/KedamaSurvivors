package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Handles /vrs status command for showing player status.
 */
public class StatusSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;
    private final StateService state;

    public StatusSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        PlayerState playerState = state.getOrCreatePlayer(player.getUniqueId(), player.getName());

        i18n.send(sender, "status.header");

        // Mode
        i18n.send(sender, "status.mode", "mode", formatMode(playerState.getMode()));

        // Team info
        Optional<TeamState> teamOpt = state.getPlayerTeam(player.getUniqueId());
        if (teamOpt.isPresent()) {
            TeamState team = teamOpt.get();
            String role = team.isLeader(player.getUniqueId()) ?
                    i18n.get("status.role_leader") : i18n.get("status.role_member");
            i18n.send(sender, "status.team",
                    "team", team.getName(),
                    "role", role,
                    "members", team.getMemberCount(),
                    "max", config.getMaxTeamSize());
        } else {
            i18n.send(sender, "status.no_team");
        }

        // Starter selections
        String weaponId = playerState.getStarterWeaponOptionId();
        String helmetId = playerState.getStarterHelmetOptionId();

        String weaponName = getStarterDisplayName(weaponId, true);
        String helmetName = getStarterDisplayName(helmetId, false);

        i18n.send(sender, "status.starters",
                "weapon", weaponName,
                "helmet", helmetName);

        // Ready state (only in lobby)
        if (playerState.getMode() == PlayerMode.LOBBY) {
            String readyStatus = playerState.isReady() ?
                    i18n.get("status.ready_yes") : i18n.get("status.ready_no");
            i18n.send(sender, "status.ready", "status", readyStatus);
        }

        // Run info (if in run)
        if (playerState.getMode() == PlayerMode.IN_RUN && playerState.getRunId() != null) {
            Optional<RunState> runOpt = state.getRun(playerState.getRunId());
            runOpt.ifPresent(run -> {
                i18n.send(sender, "status.run",
                        "world", run.getWorldName(),
                        "time", run.getElapsedFormatted(),
                        "kills", run.getTotalKills());
            });

            // Equipment levels
            i18n.send(sender, "status.equipment",
                    "weapon_level", playerState.getWeaponLevel(),
                    "helmet_level", playerState.getHelmetLevel(),
                    "xp", playerState.getXpProgress(),
                    "xp_required", playerState.getXpRequired());
        }

        // Cooldown info
        if (playerState.isOnCooldown()) {
            i18n.send(sender, "status.cooldown",
                    "seconds", playerState.getCooldownRemainingSeconds());
        }

        // Perma-score
        int permaScore = plugin.getScoreboardService().getPermaScore(player);
        i18n.send(sender, "status.perma_score", "score", permaScore);

        i18n.send(sender, "status.footer");
    }

    private String formatMode(PlayerMode mode) {
        return switch (mode) {
            case LOBBY -> i18n.get("status.mode_lobby");
            case READY -> i18n.get("status.mode_ready");
            case COUNTDOWN -> i18n.get("status.mode_countdown");
            case IN_RUN -> i18n.get("status.mode_in_run");
            case COOLDOWN -> i18n.get("status.mode_cooldown");
            case GRACE_EJECT -> i18n.get("status.mode_grace_eject");
            case DISCONNECTED -> i18n.get("status.mode_disconnected");
        };
    }

    private String getStarterDisplayName(String optionId, boolean isWeapon) {
        if (optionId == null) {
            return i18n.get("status.none_selected");
        }

        var options = isWeapon ? config.getStarterWeapons() : config.getStarterHelmets();
        return options.stream()
                .filter(opt -> opt.optionId.equals(optionId))
                .map(opt -> opt.displayName)
                .findFirst()
                .orElse(optionId);
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
