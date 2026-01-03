package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.economy.EconomyService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /vrs admin coin commands for managing player coins.
 *
 * Commands:
 * - /vrs admin coin add <player> <amount>  - Add coins (amount can be negative)
 * - /vrs admin coin set <player> <amount>  - Set coins (min 0)
 * - /vrs admin coin get <player>           - Get player's coin balance
 */
public class CoinSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final EconomyService economy;
    private final StateService state;

    public CoinSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.economy = plugin.getEconomyService();
        this.state = plugin.getStateService();
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
        i18n.send(sender, "admin.coin.help");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        // /vrs admin coin add <player> <amount>
        if (args.length < 3) {
            i18n.send(sender, "admin.coin.usage_add");
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

        // Get current balance
        int currentBalance = economy.getBalance(target);
        int newBalance = currentBalance + amount;

        // Ensure balance doesn't go below 0
        if (newBalance < 0) {
            i18n.send(sender, "admin.coin.insufficient");
            return;
        }

        // Apply the change
        if (amount > 0) {
            economy.add(target, amount, "admin_add");
        } else if (amount < 0) {
            economy.deduct(target, -amount, "admin_add");
        }

        // Get updated balance
        int updatedBalance = economy.getBalance(target);
        i18n.send(sender, "admin.coin.added",
                "player", playerName,
                "amount", amount,
                "balance", updatedBalance);
    }

    private void handleSet(CommandSender sender, String[] args) {
        // /vrs admin coin set <player> <amount>
        if (args.length < 3) {
            i18n.send(sender, "admin.coin.usage_set");
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

        // Get current balance and calculate difference
        int currentBalance = economy.getBalance(target);
        int difference = amount - currentBalance;

        // Apply the change
        if (difference > 0) {
            economy.add(target, difference, "admin_set");
        } else if (difference < 0) {
            economy.deduct(target, -difference, "admin_set");
        }

        i18n.send(sender, "admin.coin.set",
                "player", playerName,
                "amount", amount);
    }

    private void handleGet(CommandSender sender, String[] args) {
        // /vrs admin coin get <player>
        if (args.length < 2) {
            i18n.send(sender, "admin.coin.usage_get");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        int balance = economy.getBalance(target);
        i18n.send(sender, "admin.coin.get",
                "player", playerName,
                "balance", balance);
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
                completions.addAll(List.of("100", "500", "1000", "5000", "10000"));
            }
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin.coin";
    }
}
