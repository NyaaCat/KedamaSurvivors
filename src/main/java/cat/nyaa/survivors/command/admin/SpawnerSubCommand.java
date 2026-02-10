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
        i18n.send(sender, "admin.spawner.help.archetype_set_weight");
        i18n.send(sender, "admin.spawner.help.archetype_set_entitytype");
        i18n.send(sender, "admin.spawner.help.archetype_set_minspawnlevel");
        i18n.send(sender, "admin.spawner.help.archetype_set_worlds");
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
            case "set" -> handleArchetypeSet(sender, args);
            default -> showHelp(sender);
        }
    }

    private void handleArchetypeSet(CommandSender sender, String[] args) {
        // /vrs admin spawner archetype set <property> <id> <value>
        if (args.length < 5) {
            showHelp(sender);
            return;
        }

        String property = args[2].toLowerCase();
        String id = args[3];

        Optional<EnemyArchetypeConfig> configOpt = adminConfig.getArchetype(id);
        if (configOpt.isEmpty()) {
            i18n.send(sender, "admin.spawner.archetype_not_found", "id", id);
            return;
        }

        switch (property) {
            case "weight" -> {
                try {
                    double weight = Double.parseDouble(args[4]);
                    if (weight <= 0) throw new NumberFormatException();
                    boolean success = adminConfig.setArchetypeWeight(id, weight);
                    if (success) {
                        i18n.send(sender, "admin.spawner.weight_set", "id", id, "weight", weight);
                    }
                } catch (NumberFormatException e) {
                    i18n.send(sender, "admin.spawner.invalid_weight");
                }
            }
            case "entitytype" -> {
                String entityType = args[4];
                if (!entityType.contains(":")) {
                    entityType = "minecraft:" + entityType.toLowerCase();
                }
                boolean success = adminConfig.setArchetypeEntityType(id, entityType);
                if (success) {
                    i18n.send(sender, "admin.spawner.entitytype_set", "id", id, "entityType", entityType);
                }
            }
            case "minspawnlevel" -> {
                try {
                    int minLevel = Integer.parseInt(args[4]);
                    if (minLevel < 1) throw new NumberFormatException();
                    boolean success = adminConfig.setArchetypeMinSpawnLevel(id, minLevel);
                    if (success) {
                        i18n.send(sender, "admin.spawner.minspawnlevel_set", "id", id, "level", minLevel);
                    }
                } catch (NumberFormatException e) {
                    i18n.send(sender, "admin.spawner.invalid_level");
                }
            }
            case "worlds" -> {
                // Parse comma-separated and/or space-separated world list or "any"
                List<String> worlds = parseWorldArguments(args, 4);
                boolean success = adminConfig.setArchetypeAllowedWorlds(id, worlds);
                if (success) {
                    i18n.send(sender, "admin.spawner.worlds_set", "id", id, "worlds", String.join(", ", worlds));
                }
            }
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
            String worlds = config.allowedWorlds != null ? String.join(",", config.allowedWorlds) : "any";
            i18n.send(sender, "admin.spawner.archetype_list_entry",
                    "id", config.archetypeId,
                    "entityType", config.enemyType,
                    "weight", config.weight,
                    "minLevel", config.minSpawnLevel,
                    "cmdCount", cmdCount,
                    "worlds", worlds);
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
        // /vrs admin spawner archetype reward <id> <xpAmount> <xpChance> <coinAmount> <coinChance> <permaAmount> <permaChance>
        if (args.length < 9) {
            i18n.send(sender, "admin.spawner.help.archetype_reward");
            return;
        }

        String id = args[2];

        try {
            int xpAmount = Integer.parseInt(args[3]);
            double xpChance = Double.parseDouble(args[4]);
            int coinAmount = Integer.parseInt(args[5]);
            double coinChance = Double.parseDouble(args[6]);
            int permaAmount = Integer.parseInt(args[7]);
            double permaChance = Double.parseDouble(args[8]);

            // Validate chances are 0-1
            if (xpChance < 0 || xpChance > 1 || coinChance < 0 || coinChance > 1 ||
                permaChance < 0 || permaChance > 1) {
                i18n.send(sender, "admin.spawner.invalid_chance");
                return;
            }

            boolean success = adminConfig.setArchetypeRewards(id, xpAmount, xpChance,
                    coinAmount, coinChance, permaAmount, permaChance);
            if (success) {
                i18n.send(sender, "admin.spawner.rewards_set", "id", id);
                i18n.send(sender, "admin.spawner.rewards_info_new",
                        "xpAmount", xpAmount,
                        "xpChance", String.format("%.0f%%", xpChance * 100),
                        "coinAmount", coinAmount,
                        "coinChance", String.format("%.0f%%", coinChance * 100),
                        "permaAmount", permaAmount,
                        "permaChance", String.format("%.1f%%", permaChance * 100));
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
            for (String sub : List.of("create", "delete", "list", "addcommand", "removecommand", "reward", "set")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 3) {
            String subAction = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            if (subAction.equals("set")) {
                // Property names for set
                for (String prop : List.of("weight", "entitytype", "minspawnlevel", "worlds")) {
                    if (prop.startsWith(partial)) {
                        completions.add(prop);
                    }
                }
            } else if (List.of("delete", "addcommand", "removecommand", "reward").contains(subAction)) {
                // For most subcommands, suggest archetype IDs
                for (EnemyArchetypeConfig config : adminConfig.getArchetypes()) {
                    if (config.archetypeId.toLowerCase().startsWith(partial)) {
                        completions.add(config.archetypeId);
                    }
                }
            }
        } else if (args.length == 4) {
            String subAction = args[1].toLowerCase();
            String partial = args[3].toLowerCase();

            if (subAction.equals("create")) {
                // Suggest common entity types
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
                    for (int i = 0; i < configOpt.get().spawnCommands.size(); i++) {
                        String idx = String.valueOf(i);
                        if (idx.startsWith(partial)) {
                            completions.add(idx);
                        }
                    }
                }
            } else if (subAction.equals("set")) {
                // Suggest archetype IDs for set command
                for (EnemyArchetypeConfig config : adminConfig.getArchetypes()) {
                    if (config.archetypeId.toLowerCase().startsWith(partial)) {
                        completions.add(config.archetypeId);
                    }
                }
            }
        } else if (args.length >= 5) {
            String subAction = args[1].toLowerCase();
            String property = args[2].toLowerCase();
            String partial = args[args.length - 1].toLowerCase();

            if (subAction.equals("set") && property.equals("entitytype")) {
                // Suggest common entity types
                for (String entity : List.of("zombie", "skeleton", "spider", "creeper", "enderman", "witch", "phantom", "blaze", "wither_skeleton")) {
                    if (entity.startsWith(partial)) {
                        completions.add(entity);
                    }
                }
            } else if (subAction.equals("set") && property.equals("worlds")) {
                // Suggest "any" and configured combat world names
                if ("any".startsWith(partial)) {
                    completions.add("any");
                }
                for (var worldConfig : plugin.getConfigService().getCombatWorlds()) {
                    if (worldConfig.name.toLowerCase().startsWith(partial)) {
                        completions.add(worldConfig.name);
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

    private List<String> parseWorldArguments(String[] args, int startIndex) {
        List<String> worlds = new ArrayList<>();

        for (int i = startIndex; i < args.length; i++) {
            String part = args[i];
            for (String token : part.split(",")) {
                String world = token.trim();
                if (world.isEmpty()) continue;
                if ("any".equalsIgnoreCase(world)) {
                    return List.of("any");
                }
                worlds.add(world);
            }
        }

        if (worlds.isEmpty()) {
            return List.of("any");
        }
        return worlds;
    }
}
