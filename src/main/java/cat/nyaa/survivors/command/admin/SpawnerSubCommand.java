package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.service.AdminConfigService;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Handles /vrs admin spawner subcommands for managing enemy archetypes.
 */
public class SpawnerSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final AdminConfigService adminConfig;

    public SpawnerSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.adminConfig = plugin.getAdminConfigService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "archetype" -> handleArchetype(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "admin.spawner.help.header");
        i18n.send(sender, "admin.spawner.help.archetype_create");
        i18n.send(sender, "admin.spawner.help.archetype_delete");
        i18n.send(sender, "admin.spawner.help.archetype_list");
        i18n.send(sender, "admin.spawner.help.archetype_addcmd");
        i18n.send(sender, "admin.spawner.help.archetype_removecmd");
        i18n.send(sender, "admin.spawner.help.archetype_reward");
    }

    // ==================== Archetype Commands ====================

    private void handleArchetype(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showHelp(sender);
            return;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "create" -> handleArchetypeCreate(sender, args);
            case "delete" -> handleArchetypeDelete(sender, args);
            case "list" -> handleArchetypeList(sender, args);
            case "addcommand" -> handleArchetypeAddCommand(sender, args);
            case "removecommand" -> handleArchetypeRemoveCommand(sender, args);
            case "reward" -> handleArchetypeReward(sender, args);
            default -> showHelp(sender);
        }
    }

    private void handleArchetypeCreate(CommandSender sender, String[] args) {
        // /vrs admin spawner archetype create <id> <entityType> [weight]
        if (args.length < 4) {
            i18n.send(sender, "admin.spawner.help.archetype_create");
            return;
        }

        String id = args[2];
        String entityType = args[3];
        double weight = 1.0;

        if (args.length > 4) {
            try {
                weight = Double.parseDouble(args[4]);
                if (weight <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                i18n.send(sender, "admin.spawner.invalid_weight");
                return;
            }
        }

        if (!isValidId(id)) {
            i18n.send(sender, "error.invalid_argument", "arg", id);
            return;
        }

        // Normalize entity type
        if (!entityType.contains(":")) {
            entityType = "minecraft:" + entityType.toLowerCase();
        }

        boolean success = adminConfig.createArchetype(id, entityType, weight);
        if (success) {
            i18n.send(sender, "admin.spawner.archetype_created",
                    "id", id,
                    "entityType", entityType,
                    "weight", weight);
        } else {
            i18n.send(sender, "admin.spawner.archetype_exists", "id", id);
        }
    }

    private void handleArchetypeDelete(CommandSender sender, String[] args) {
        // /vrs admin spawner archetype delete <id>
        if (args.length < 3) {
            i18n.send(sender, "admin.spawner.help.archetype_delete");
            return;
        }

        String id = args[2];

        boolean success = adminConfig.deleteArchetype(id);
        if (success) {
            i18n.send(sender, "admin.spawner.archetype_deleted", "id", id);
        } else {
            i18n.send(sender, "admin.spawner.archetype_not_found", "id", id);
        }
    }

    private void handleArchetypeList(CommandSender sender, String[] args) {
        // /vrs admin spawner archetype list
        Collection<EnemyArchetypeConfig> archetypes = adminConfig.getArchetypes();

        i18n.send(sender, "admin.spawner.archetype_list_header");

        if (archetypes.isEmpty()) {
            i18n.send(sender, "admin.spawner.archetype_list_empty");
            return;
        }

        for (EnemyArchetypeConfig config : archetypes) {
            int cmdCount = config.spawnCommands != null ? config.spawnCommands.size() : 0;
            i18n.send(sender, "admin.spawner.archetype_list_entry",
                    "id", config.archetypeId,
                    "entityType", config.enemyType,
                    "weight", config.weight,
                    "cmdCount", cmdCount);
        }
    }

    private void handleArchetypeAddCommand(CommandSender sender, String[] args) {
        // /vrs admin spawner archetype addcommand <id> <command...>
        if (args.length < 4) {
            i18n.send(sender, "admin.spawner.help.archetype_addcmd");
            return;
        }

        String id = args[2];
        String command = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));

        boolean success = adminConfig.addSpawnCommand(id, command);
        if (success) {
            i18n.send(sender, "admin.spawner.command_added", "id", id);
        } else {
            i18n.send(sender, "admin.spawner.archetype_not_found", "id", id);
        }
    }

    private void handleArchetypeRemoveCommand(CommandSender sender, String[] args) {
        // /vrs admin spawner archetype removecommand <id> <index>
        if (args.length < 4) {
            i18n.send(sender, "admin.spawner.help.archetype_removecmd");
            return;
        }

        String id = args[2];
        int index;

        try {
            index = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[3]);
            return;
        }

        boolean success = adminConfig.removeSpawnCommand(id, index);
        if (success) {
            i18n.send(sender, "admin.spawner.command_removed", "id", id, "index", index);
        } else {
            i18n.send(sender, "admin.spawner.command_invalid_index", "index", index);
        }
    }

    private void handleArchetypeReward(CommandSender sender, String[] args) {
        // /vrs admin spawner archetype reward <id> <xpBase> <xpPerLevel> <coinBase> <coinPerLevel> <permaChance>
        if (args.length < 8) {
            i18n.send(sender, "admin.spawner.help.archetype_reward");
            return;
        }

        String id = args[2];

        try {
            int xpBase = Integer.parseInt(args[3]);
            int xpPerLevel = Integer.parseInt(args[4]);
            int coinBase = Integer.parseInt(args[5]);
            int coinPerLevel = Integer.parseInt(args[6]);
            double permaChance = Double.parseDouble(args[7]);

            boolean success = adminConfig.setArchetypeRewards(id, xpBase, xpPerLevel, coinBase, coinPerLevel, permaChance);
            if (success) {
                i18n.send(sender, "admin.spawner.rewards_set", "id", id);
                i18n.send(sender, "admin.spawner.rewards_info",
                        "xpBase", xpBase,
                        "xpPerLevel", xpPerLevel,
                        "coinBase", coinBase,
                        "coinPerLevel", coinPerLevel,
                        "permaChance", permaChance);
            } else {
                i18n.send(sender, "admin.spawner.archetype_not_found", "id", id);
            }
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", "one of the arguments");
        }
    }

    // ==================== Tab Complete ====================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("archetype".startsWith(partial)) {
                completions.add("archetype");
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (String sub : List.of("create", "delete", "list", "addcommand", "removecommand", "reward")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 3) {
            String subAction = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            // For most subcommands, suggest archetype IDs
            if (List.of("delete", "addcommand", "removecommand", "reward").contains(subAction)) {
                for (EnemyArchetypeConfig config : adminConfig.getArchetypes()) {
                    if (config.archetypeId.toLowerCase().startsWith(partial)) {
                        completions.add(config.archetypeId);
                    }
                }
            }
        } else if (args.length == 4) {
            String subAction = args[1].toLowerCase();

            if (subAction.equals("create")) {
                // Suggest common entity types
                String partial = args[3].toLowerCase();
                for (String entity : List.of("zombie", "skeleton", "spider", "creeper", "enderman", "witch", "phantom", "blaze", "wither_skeleton")) {
                    if (entity.startsWith(partial)) {
                        completions.add(entity);
                    }
                }
            } else if (subAction.equals("removecommand")) {
                // Suggest command indices
                String archetypeId = args[2];
                Optional<EnemyArchetypeConfig> configOpt = adminConfig.getArchetype(archetypeId);
                if (configOpt.isPresent() && configOpt.get().spawnCommands != null) {
                    String partial = args[3].toLowerCase();
                    for (int i = 0; i < configOpt.get().spawnCommands.size(); i++) {
                        String idx = String.valueOf(i);
                        if (idx.startsWith(partial)) {
                            completions.add(idx);
                        }
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin.spawner";
    }

    // ==================== Utility Methods ====================

    private boolean isValidId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_]+");
    }
}
