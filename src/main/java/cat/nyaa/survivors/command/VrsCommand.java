package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Main command handler for /vrs.
 * Dispatches to subcommands based on first argument.
 */
public class VrsCommand implements CommandExecutor, TabCompleter {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public VrsCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        registerSubCommands();
    }

    private void registerSubCommands() {
        // Player commands
        subCommands.put("starter", new StarterSubCommand(plugin));
        subCommands.put("team", new TeamSubCommand(plugin));
        subCommands.put("ready", new ReadySubCommand(plugin));
        subCommands.put("quit", new QuitSubCommand(plugin));
        subCommands.put("status", new StatusSubCommand(plugin));

        // Admin commands
        subCommands.put("admin", new AdminSubCommand(plugin));
        subCommands.put("reload", new ReloadSubCommand(plugin));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subName = args[0].toLowerCase();
        SubCommand sub = subCommands.get(subName);

        if (sub == null) {
            i18n.send(sender, "error.unknown_command", "command", subName);
            return true;
        }

        // Check permission
        String permission = sub.getPermission();
        if (permission != null && !sender.hasPermission(permission)) {
            i18n.send(sender, "error.no_permission");
            return true;
        }

        // Check if player-only
        if (sub.isPlayerOnly() && !(sender instanceof Player)) {
            i18n.send(sender, "error.player_only");
            return true;
        }

        // Execute with remaining args
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        sub.execute(sender, subArgs);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            // Complete subcommand name
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();

            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                String name = entry.getKey();
                SubCommand sub = entry.getValue();

                // Check permission
                String permission = sub.getPermission();
                if (permission != null && !sender.hasPermission(permission)) {
                    continue;
                }

                if (name.startsWith(partial)) {
                    completions.add(name);
                }
            }

            return completions;
        }

        if (args.length > 1) {
            String subName = args[0].toLowerCase();
            SubCommand sub = subCommands.get(subName);

            if (sub != null) {
                String permission = sub.getPermission();
                if (permission == null || sender.hasPermission(permission)) {
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return sub.tabComplete(sender, subArgs);
                }
            }
        }

        return Collections.emptyList();
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "help.header");

        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            String name = entry.getKey();
            SubCommand sub = entry.getValue();

            // Check permission
            String permission = sub.getPermission();
            if (permission != null && !sender.hasPermission(permission)) {
                continue;
            }

            i18n.send(sender, "help.command." + name);
        }

        i18n.send(sender, "help.footer");
    }
}
