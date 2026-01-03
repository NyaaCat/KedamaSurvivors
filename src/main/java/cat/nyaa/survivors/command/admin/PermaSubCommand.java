package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.scoreboard.ScoreboardService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /vrs admin perma commands for managing player permanent scores.
 *
 * Commands:
 * - /vrs admin perma add <player> <amount>  - Add perma score (amount can be negative)
 * - /vrs admin perma set <player> <amount>  - Set perma score (min 0)
 * - /vrs admin perma get <player>           - Get player's perma score
 */
public class PermaSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final StateService state;
    private final ScoreboardService scoreboardService;

    public PermaSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.scoreboardService = plugin.getScoreboardService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add" -> handleAdd(sender, args);
            case "set" -> handleSet(sender, args);
            case "get" -> handleGet(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "admin.perma.help");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        // /vrs admin perma add <player> <amount>
        if (args.length < 3) {
            i18n.send(sender, "admin.perma.usage_add");
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
        int currentScore = playerState.getPermaScore();
        int newScore = Math.max(0, currentScore + amount); // Ensure minimum 0

        playerState.setPermaScore(newScore);

        // Update scoreboard
        scoreboardService.updatePermaScore(target, newScore);

        i18n.send(sender, "admin.perma.added",
                "player", playerName,
                "amount", amount,
                "total", newScore);
    }

    private void handleSet(CommandSender sender, String[] args) {
        // /vrs admin perma set <player> <amount>
        if (args.length < 3) {
            i18n.send(sender, "admin.perma.usage_set");
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

        // Ensure amount is not negative
        if (amount < 0) {
            amount = 0;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        PlayerState playerState = state.getOrCreatePlayer(target.getUniqueId(), target.getName());
        playerState.setPermaScore(amount);

        // Update scoreboard
        scoreboardService.updatePermaScore(target, amount);

        i18n.send(sender, "admin.perma.set",
                "player", playerName,
                "amount", amount);
    }

    private void handleGet(CommandSender sender, String[] args) {
        // /vrs admin perma get <player>
        if (args.length < 2) {
            i18n.send(sender, "admin.perma.usage_get");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        PlayerState playerState = state.getOrCreatePlayer(target.getUniqueId(), target.getName());
        int score = playerState.getPermaScore();

        i18n.send(sender, "admin.perma.get",
                "player", playerName,
                "amount", score);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : List.of("add", "set", "get")) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            // Player names
            String partial = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            if (action.equals("add") || action.equals("set")) {
                // Suggest some common amounts
                completions.addAll(List.of("10", "50", "100", "500", "1000"));
            }
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin.perma";
    }
}
