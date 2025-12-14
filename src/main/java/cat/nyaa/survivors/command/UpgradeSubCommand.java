package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import cat.nyaa.survivors.service.UpgradeService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Handles /vrs upgrade <power|defense> command.
 * Used for chat-based upgrade selection during combat.
 */
public class UpgradeSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final StateService state;

    public UpgradeSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;

        // Validate player state
        Optional<PlayerState> playerStateOpt = state.getPlayer(player.getUniqueId());
        if (playerStateOpt.isEmpty()) {
            i18n.send(sender, "error.not_in_game");
            return;
        }

        PlayerState playerState = playerStateOpt.get();

        // Must be in run
        if (playerState.getMode() != PlayerMode.IN_RUN) {
            i18n.send(sender, "error.not_in_run");
            return;
        }

        // Must have pending upgrade
        if (!playerState.isUpgradePending()) {
            i18n.send(sender, "upgrade.not_pending");
            return;
        }

        // Check arguments
        if (args.length == 0) {
            i18n.send(sender, "upgrade.invalid_choice");
            return;
        }

        String choice = args[0].toLowerCase();
        if (!choice.equals("power") && !choice.equals("defense")) {
            i18n.send(sender, "upgrade.invalid_choice");
            return;
        }

        // Process the upgrade choice
        UpgradeService upgradeService = plugin.getUpgradeService();
        if (upgradeService != null) {
            upgradeService.processUpgradeChoice(player, choice);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Arrays.asList("power", "defense").stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }
}
