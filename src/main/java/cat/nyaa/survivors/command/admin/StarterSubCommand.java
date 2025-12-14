package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService.EquipmentGroupConfig;
import cat.nyaa.survivors.config.ConfigService.StarterOptionConfig;
import cat.nyaa.survivors.config.ItemTemplateConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.EquipmentType;
import cat.nyaa.survivors.service.AdminConfigService;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles /vrs admin starter subcommands for managing starter options.
 */
public class StarterSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final AdminConfigService adminConfig;

    public StarterSubCommand(KedamaSurvivorsPlugin plugin) {
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
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender, args);
            case "set" -> handleSet(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "admin.starter.help.header");
        i18n.send(sender, "admin.starter.help.create");
        i18n.send(sender, "admin.starter.help.delete");
        i18n.send(sender, "admin.starter.help.list");
        i18n.send(sender, "admin.starter.help.set_displayname");
        i18n.send(sender, "admin.starter.help.set_template");
        i18n.send(sender, "admin.starter.help.set_group");
        i18n.send(sender, "admin.starter.help.set_level");
    }

    private void handleCreate(CommandSender sender, String[] args) {
        // /vrs admin starter create <weapon|helmet> <optionId> <templateId> <group> <level> [displayName]
        if (args.length < 6) {
            i18n.send(sender, "admin.starter.help.create");
            return;
        }

        EquipmentType type = parseEquipmentType(args[1]);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        String optionId = args[2];
        String templateId = args[3];
        String group = args[4];
        int level;

        try {
            level = Integer.parseInt(args[5]);
            if (level < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            i18n.send(sender, "admin.equipment.invalid_level");
            return;
        }

        String displayName = args.length > 6 ? String.join(" ", java.util.Arrays.copyOfRange(args, 6, args.length)) : null;

        if (!isValidId(optionId)) {
            i18n.send(sender, "error.invalid_argument", "arg", optionId);
            return;
        }

        // Validate template exists
        Optional<ItemTemplateConfig> templateOpt = adminConfig.getItemTemplate(templateId);
        if (templateOpt.isEmpty()) {
            i18n.send(sender, "admin.starter.template_not_found", "templateId", templateId);
            return;
        }

        // Validate group exists
        Optional<EquipmentGroupConfig> groupOpt = adminConfig.getEquipmentGroup(type, group);
        if (groupOpt.isEmpty()) {
            i18n.send(sender, "admin.equipment.group_not_found", "groupId", group);
            return;
        }

        boolean success = adminConfig.createStarterOption(type, optionId, displayName, templateId, group, level);
        if (success) {
            i18n.send(sender, "admin.starter.created",
                    "type", args[1],
                    "optionId", optionId,
                    "displayName", displayName != null ? displayName : optionId,
                    "templateId", templateId,
                    "group", group,
                    "level", level);
        } else {
            i18n.send(sender, "admin.starter.exists", "optionId", optionId);
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        // /vrs admin starter delete <weapon|helmet> <optionId>
        if (args.length < 3) {
            i18n.send(sender, "admin.starter.help.delete");
            return;
        }

        EquipmentType type = parseEquipmentType(args[1]);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        String optionId = args[2];

        boolean success = adminConfig.deleteStarterOption(type, optionId);
        if (success) {
            i18n.send(sender, "admin.starter.deleted", "type", args[1], "optionId", optionId);
        } else {
            i18n.send(sender, "admin.starter.not_found", "optionId", optionId);
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        // /vrs admin starter list [weapon|helmet]
        String typeFilter = args.length > 1 ? args[1].toLowerCase() : null;

        if (typeFilter == null || typeFilter.equals("weapon")) {
            listStarters(sender, EquipmentType.WEAPON, "weapon");
        }
        if (typeFilter == null || typeFilter.equals("helmet")) {
            listStarters(sender, EquipmentType.HELMET, "helmet");
        }
    }

    private void listStarters(CommandSender sender, EquipmentType type, String typeName) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ?
                adminConfig.getStarterWeapons() : adminConfig.getStarterHelmets();

        i18n.send(sender, "admin.starter.list_header", "type", typeName);

        if (starters.isEmpty()) {
            i18n.send(sender, "admin.starter.list_empty");
            return;
        }

        for (StarterOptionConfig opt : starters) {
            i18n.send(sender, "admin.starter.list_entry",
                    "optionId", opt.optionId,
                    "displayName", opt.displayName,
                    "templateId", opt.templateId,
                    "group", opt.group,
                    "level", opt.level);
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        // /vrs admin starter set <property> <weapon|helmet> <optionId> <value...>
        if (args.length < 5) {
            showHelp(sender);
            return;
        }

        String property = args[1].toLowerCase();
        EquipmentType type = parseEquipmentType(args[2]);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        String optionId = args[3];

        // Check option exists
        Optional<StarterOptionConfig> optOpt = adminConfig.getStarterOption(type, optionId);
        if (optOpt.isEmpty()) {
            i18n.send(sender, "admin.starter.not_found", "optionId", optionId);
            return;
        }

        switch (property) {
            case "displayname" -> {
                if (args.length < 5) {
                    i18n.send(sender, "admin.starter.help.set_displayname");
                    return;
                }
                String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                boolean success = adminConfig.setStarterDisplayName(type, optionId, displayName);
                if (success) {
                    i18n.send(sender, "admin.starter.displayname_set", "optionId", optionId, "displayName", displayName);
                }
            }
            case "template" -> {
                if (args.length < 5) {
                    i18n.send(sender, "admin.starter.help.set_template");
                    return;
                }
                String templateId = args[4];
                // Validate template exists
                if (adminConfig.getItemTemplate(templateId).isEmpty()) {
                    i18n.send(sender, "admin.starter.template_not_found", "templateId", templateId);
                    return;
                }
                boolean success = adminConfig.setStarterTemplate(type, optionId, templateId);
                if (success) {
                    i18n.send(sender, "admin.starter.template_set", "optionId", optionId, "templateId", templateId);
                }
            }
            case "group" -> {
                if (args.length < 5) {
                    i18n.send(sender, "admin.starter.help.set_group");
                    return;
                }
                String group = args[4];
                // Validate group exists
                if (adminConfig.getEquipmentGroup(type, group).isEmpty()) {
                    i18n.send(sender, "admin.equipment.group_not_found", "groupId", group);
                    return;
                }
                boolean success = adminConfig.setStarterGroup(type, optionId, group);
                if (success) {
                    i18n.send(sender, "admin.starter.group_set", "optionId", optionId, "group", group);
                }
            }
            case "level" -> {
                if (args.length < 5) {
                    i18n.send(sender, "admin.starter.help.set_level");
                    return;
                }
                try {
                    int level = Integer.parseInt(args[4]);
                    if (level < 1) throw new NumberFormatException();
                    boolean success = adminConfig.setStarterLevel(type, optionId, level);
                    if (success) {
                        i18n.send(sender, "admin.starter.level_set", "optionId", optionId, "level", level);
                    }
                } catch (NumberFormatException e) {
                    i18n.send(sender, "error.invalid_number", "value", args[4]);
                }
            }
            default -> showHelp(sender);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("create", "delete", "list", "set")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (action.equals("set")) {
                for (String prop : List.of("displayname", "template", "group", "level")) {
                    if (prop.startsWith(partial)) {
                        completions.add(prop);
                    }
                }
            } else {
                for (String type : List.of("weapon", "helmet")) {
                    if (type.startsWith(partial)) {
                        completions.add(type);
                    }
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            String partial = args[2].toLowerCase();

            if (action.equals("set")) {
                // Type for set command
                for (String type : List.of("weapon", "helmet")) {
                    if (type.startsWith(partial)) {
                        completions.add(type);
                    }
                }
            } else if (List.of("delete").contains(action)) {
                // Option IDs
                EquipmentType type = parseEquipmentType(args[1]);
                if (type != null) {
                    List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ?
                            adminConfig.getStarterWeapons() : adminConfig.getStarterHelmets();
                    for (StarterOptionConfig opt : starters) {
                        if (opt.optionId.toLowerCase().startsWith(partial)) {
                            completions.add(opt.optionId);
                        }
                    }
                }
            } else if (action.equals("create")) {
                // Suggest a placeholder for optionId
                completions.add("<optionId>");
            }
        } else if (args.length == 4) {
            String action = args[0].toLowerCase();
            String partial = args[3].toLowerCase();

            if (action.equals("set")) {
                // Option IDs for set command
                EquipmentType type = parseEquipmentType(args[2]);
                if (type != null) {
                    List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ?
                            adminConfig.getStarterWeapons() : adminConfig.getStarterHelmets();
                    for (StarterOptionConfig opt : starters) {
                        if (opt.optionId.toLowerCase().startsWith(partial)) {
                            completions.add(opt.optionId);
                        }
                    }
                }
            } else if (action.equals("create")) {
                // Suggest item templates
                for (String templateId : adminConfig.getItemTemplates().keySet()) {
                    if (templateId.toLowerCase().startsWith(partial)) {
                        completions.add(templateId);
                    }
                }
            }
        } else if (args.length == 5) {
            String action = args[0].toLowerCase();
            String partial = args[4].toLowerCase();

            if (action.equals("create")) {
                // Suggest equipment groups
                EquipmentType type = parseEquipmentType(args[1]);
                if (type != null) {
                    for (EquipmentGroupConfig group : adminConfig.getEquipmentGroups(type)) {
                        if (group.groupId.toLowerCase().startsWith(partial)) {
                            completions.add(group.groupId);
                        }
                    }
                }
            } else if (action.equals("set")) {
                String property = args[1].toLowerCase();
                if (property.equals("template")) {
                    for (String templateId : adminConfig.getItemTemplates().keySet()) {
                        if (templateId.toLowerCase().startsWith(partial)) {
                            completions.add(templateId);
                        }
                    }
                } else if (property.equals("group")) {
                    EquipmentType type = parseEquipmentType(args[2]);
                    if (type != null) {
                        for (EquipmentGroupConfig group : adminConfig.getEquipmentGroups(type)) {
                            if (group.groupId.toLowerCase().startsWith(partial)) {
                                completions.add(group.groupId);
                            }
                        }
                    }
                }
            }
        } else if (args.length == 6 && args[0].equalsIgnoreCase("create")) {
            // Suggest level numbers
            completions.addAll(List.of("1", "2", "3", "4", "5"));
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin.starter";
    }

    private EquipmentType parseEquipmentType(String type) {
        return switch (type.toLowerCase()) {
            case "weapon", "weapons" -> EquipmentType.WEAPON;
            case "helmet", "helmets" -> EquipmentType.HELMET;
            default -> null;
        };
    }

    private boolean isValidId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_]+");
    }
}
