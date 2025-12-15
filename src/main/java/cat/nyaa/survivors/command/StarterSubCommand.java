package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /vrs starter commands for selecting starter equipment.
 */
public class StarterSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;
    private final StateService state;

    public StarterSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        PlayerState playerState = state.getOrCreatePlayer(player.getUniqueId(), player.getName());

        // Must be in lobby or cooldown to select starters
        if (playerState.getMode() != PlayerMode.LOBBY && playerState.getMode() != PlayerMode.COOLDOWN) {
            i18n.send(sender, "error.not_in_lobby");
            return;
        }

        if (args.length == 0) {
            // No args: open weapon GUI directly (helmet will auto-open if configured)
            handleWeaponSelection(player, playerState, args);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "weapon" -> handleWeaponSelection(player, playerState, args);
            case "helmet" -> handleHelmetSelection(player, playerState, args);
            case "status" -> showStatus(player, playerState);
            case "clear" -> clearSelections(player, playerState);
            default -> i18n.send(sender, "error.unknown_command", "command", action);
        }
    }

    private void handleWeaponSelection(Player player, PlayerState playerState, String[] args) {
        if (args.length < 2) {
            // Open GUI for weapon selection
            plugin.getStarterService().openWeaponGui(player);
            return;
        }

        String optionId = args[1];
        var weapons = config.getStarterWeapons();
        var selected = weapons.stream()
                .filter(w -> w.optionId.equalsIgnoreCase(optionId))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            i18n.send(player, "error.invalid_option", "option", optionId);
            return;
        }

        playerState.setStarterWeaponOptionId(optionId);

        // Grant the weapon immediately
        plugin.getStarterService().grantSingleStarterItem(player, selected, "weapon");

        i18n.send(player, "starter.weapon_selected", "weapon", selected.displayName);

        // Auto-open helmet GUI if configured
        if (config.isAutoOpenHelmetGui() && playerState.getStarterHelmetOptionId() == null) {
            plugin.getStarterService().openHelmetGui(player);
        }
    }

    private void handleHelmetSelection(Player player, PlayerState playerState, String[] args) {
        // Check if weapon must be selected first
        if (config.isRequireWeaponFirst() && playerState.getStarterWeaponOptionId() == null) {
            i18n.send(player, "error.weapon_first");
            return;
        }

        if (args.length < 2) {
            // Open GUI for helmet selection
            plugin.getStarterService().openHelmetGui(player);
            return;
        }

        String optionId = args[1];
        var helmets = config.getStarterHelmets();
        var selected = helmets.stream()
                .filter(h -> h.optionId.equalsIgnoreCase(optionId))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            i18n.send(player, "error.invalid_option", "option", optionId);
            return;
        }

        playerState.setStarterHelmetOptionId(optionId);

        // Grant the helmet immediately
        plugin.getStarterService().grantSingleStarterItem(player, selected, "helmet");

        i18n.send(player, "starter.helmet_selected", "helmet", selected.displayName);
    }

    private void showAvailableWeapons(Player player) {
        i18n.send(player, "starter.weapon_header");
        for (var weapon : config.getStarterWeapons()) {
            i18n.sendClickable(player, "starter.weapon_option",
                    "/vrs starter weapon " + weapon.optionId,
                    "name", weapon.displayName,
                    "id", weapon.optionId);
        }
    }

    private void showAvailableHelmets(Player player) {
        i18n.send(player, "starter.helmet_header");
        for (var helmet : config.getStarterHelmets()) {
            i18n.sendClickable(player, "starter.helmet_option",
                    "/vrs starter helmet " + helmet.optionId,
                    "name", helmet.displayName,
                    "id", helmet.optionId);
        }
    }

    private void showStatus(Player player, PlayerState playerState) {
        String weaponId = playerState.getStarterWeaponOptionId();
        String helmetId = playerState.getStarterHelmetOptionId();

        String weaponName = weaponId != null
                ? config.getStarterWeapons().stream()
                    .filter(w -> w.optionId.equals(weaponId))
                    .map(w -> w.displayName)
                    .findFirst().orElse(weaponId)
                : i18n.get("starter.none");

        String helmetName = helmetId != null
                ? config.getStarterHelmets().stream()
                    .filter(h -> h.optionId.equals(helmetId))
                    .map(h -> h.displayName)
                    .findFirst().orElse(helmetId)
                : i18n.get("starter.none");

        i18n.send(player, "starter.status",
                "weapon", weaponName,
                "helmet", helmetName);
    }

    private void clearSelections(Player player, PlayerState playerState) {
        playerState.setStarterWeaponOptionId(null);
        playerState.setStarterHelmetOptionId(null);
        i18n.send(player, "starter.cleared");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("weapon", "helmet", "status", "clear")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if ("weapon".equals(action)) {
                for (var weapon : config.getStarterWeapons()) {
                    if (weapon.optionId.toLowerCase().startsWith(partial)) {
                        completions.add(weapon.optionId);
                    }
                }
            } else if ("helmet".equals(action)) {
                for (var helmet : config.getStarterHelmets()) {
                    if (helmet.optionId.toLowerCase().startsWith(partial)) {
                        completions.add(helmet.optionId);
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
