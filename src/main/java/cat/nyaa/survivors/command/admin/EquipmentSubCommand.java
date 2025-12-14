package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService.EquipmentGroupConfig;
import cat.nyaa.survivors.config.ItemTemplateConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.EquipmentType;
import cat.nyaa.survivors.service.AdminConfigService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Handles /vrs admin equipment subcommands for managing equipment groups and items.
 */
public class EquipmentSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final AdminConfigService adminConfig;

    public EquipmentSubCommand(KedamaSurvivorsPlugin plugin) {
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
            case "group" -> handleGroup(sender, args);
            case "item" -> handleItem(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "admin.equipment.help.header");
        i18n.send(sender, "admin.equipment.help.group_create");
        i18n.send(sender, "admin.equipment.help.group_delete");
        i18n.send(sender, "admin.equipment.help.group_list");
        i18n.send(sender, "admin.equipment.help.item_add");
        i18n.send(sender, "admin.equipment.help.item_remove");
        i18n.send(sender, "admin.equipment.help.item_list");
    }

    // ==================== Group Commands ====================

    private void handleGroup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showHelp(sender);
            return;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "create" -> handleGroupCreate(sender, args);
            case "delete" -> handleGroupDelete(sender, args);
            case "list" -> handleGroupList(sender, args);
            default -> showHelp(sender);
        }
    }

    private void handleGroupCreate(CommandSender sender, String[] args) {
        // /vrs admin equipment group create <type> <groupId> [displayName]
        if (args.length < 4) {
            i18n.send(sender, "admin.equipment.help.group_create");
            return;
        }

        String typeStr = args[2].toLowerCase();
        String groupId = args[3];
        String displayName = args.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)) : null;

        EquipmentType type = parseEquipmentType(typeStr);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        if (!isValidId(groupId)) {
            i18n.send(sender, "error.invalid_argument", "arg", groupId);
            return;
        }

        boolean success = adminConfig.createEquipmentGroup(type, groupId, displayName);
        if (success) {
            i18n.send(sender, "admin.equipment.group_created",
                    "type", typeStr,
                    "groupId", groupId,
                    "displayName", displayName != null ? displayName : groupId);
        } else {
            i18n.send(sender, "admin.equipment.group_exists", "groupId", groupId);
        }
    }

    private void handleGroupDelete(CommandSender sender, String[] args) {
        // /vrs admin equipment group delete <type> <groupId>
        if (args.length < 4) {
            i18n.send(sender, "admin.equipment.help.group_delete");
            return;
        }

        String typeStr = args[2].toLowerCase();
        String groupId = args[3];

        EquipmentType type = parseEquipmentType(typeStr);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        // Get item count for warning
        Optional<EquipmentGroupConfig> groupOpt = adminConfig.getEquipmentGroup(type, groupId);
        if (groupOpt.isEmpty()) {
            i18n.send(sender, "admin.equipment.group_not_found", "groupId", groupId);
            return;
        }

        int itemCount = groupOpt.get().levelItems != null ?
                groupOpt.get().levelItems.values().stream().mapToInt(List::size).sum() : 0;
        if (itemCount > 0) {
            i18n.send(sender, "admin.equipment.group_has_items", "count", itemCount);
        }

        boolean success = adminConfig.deleteEquipmentGroup(type, groupId);
        if (success) {
            i18n.send(sender, "admin.equipment.group_deleted", "type", typeStr, "groupId", groupId);
        } else {
            i18n.send(sender, "admin.equipment.group_not_found", "groupId", groupId);
        }
    }

    private void handleGroupList(CommandSender sender, String[] args) {
        // /vrs admin equipment group list [type]
        String typeFilter = args.length > 2 ? args[2].toLowerCase() : null;

        if (typeFilter == null || typeFilter.equals("weapon")) {
            listGroups(sender, EquipmentType.WEAPON, "weapon");
        }
        if (typeFilter == null || typeFilter.equals("helmet")) {
            listGroups(sender, EquipmentType.HELMET, "helmet");
        }
    }

    private void listGroups(CommandSender sender, EquipmentType type, String typeName) {
        Collection<EquipmentGroupConfig> groups = adminConfig.getEquipmentGroups(type);

        i18n.send(sender, "admin.equipment.group_list_header", "type", typeName);

        if (groups.isEmpty()) {
            i18n.send(sender, "admin.equipment.group_list_empty");
            return;
        }

        for (EquipmentGroupConfig group : groups) {
            int levels = group.levelItems != null ? group.levelItems.size() : 0;
            int items = group.levelItems != null ?
                    group.levelItems.values().stream().mapToInt(List::size).sum() : 0;

            i18n.send(sender, "admin.equipment.group_list_entry",
                    "groupId", group.groupId,
                    "displayName", group.displayName,
                    "levels", levels,
                    "items", items);
        }
    }

    // ==================== Item Commands ====================

    private void handleItem(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showHelp(sender);
            return;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "add" -> handleItemAdd(sender, args);
            case "remove" -> handleItemRemove(sender, args);
            case "list" -> handleItemList(sender, args);
            default -> showHelp(sender);
        }
    }

    private void handleItemAdd(CommandSender sender, String[] args) {
        // /vrs admin equipment item add <type> <groupId> <level>
        if (!(sender instanceof Player player)) {
            i18n.send(sender, "error.player_only");
            return;
        }

        if (args.length < 5) {
            i18n.send(sender, "admin.equipment.help.item_add");
            return;
        }

        String typeStr = args[2].toLowerCase();
        String groupId = args[3];
        int level;

        try {
            level = Integer.parseInt(args[4]);
            if (level < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            i18n.send(sender, "admin.equipment.invalid_level");
            return;
        }

        EquipmentType type = parseEquipmentType(typeStr);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            i18n.send(sender, "admin.equipment.no_item_in_hand");
            return;
        }

        String templateId = adminConfig.addItemToGroup(type, groupId, level, itemInHand);
        if (templateId != null) {
            i18n.send(sender, "admin.equipment.item_added",
                    "templateId", templateId,
                    "groupId", groupId,
                    "level", level);
        } else {
            i18n.send(sender, "admin.equipment.group_not_found", "groupId", groupId);
        }
    }

    private void handleItemRemove(CommandSender sender, String[] args) {
        // /vrs admin equipment item remove <type> <groupId> <level> <index>
        if (args.length < 6) {
            i18n.send(sender, "admin.equipment.help.item_remove");
            return;
        }

        String typeStr = args[2].toLowerCase();
        String groupId = args[3];
        int level;
        int index;

        try {
            level = Integer.parseInt(args[4]);
            index = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[4] + " or " + args[5]);
            return;
        }

        EquipmentType type = parseEquipmentType(typeStr);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        String removedId = adminConfig.removeItemFromGroup(type, groupId, level, index);
        if (removedId != null) {
            i18n.send(sender, "admin.equipment.item_removed",
                    "templateId", removedId,
                    "groupId", groupId,
                    "level", level);
        } else {
            i18n.send(sender, "admin.equipment.item_not_found", "index", index);
        }
    }

    private void handleItemList(CommandSender sender, String[] args) {
        // /vrs admin equipment item list <type> <groupId> [level]
        if (args.length < 4) {
            i18n.send(sender, "admin.equipment.help.item_list");
            return;
        }

        String typeStr = args[2].toLowerCase();
        String groupId = args[3];
        Integer level = args.length > 4 ? parseIntOrNull(args[4]) : null;

        EquipmentType type = parseEquipmentType(typeStr);
        if (type == null) {
            i18n.send(sender, "admin.equipment.invalid_type");
            return;
        }

        Optional<EquipmentGroupConfig> groupOpt = adminConfig.getEquipmentGroup(type, groupId);
        if (groupOpt.isEmpty()) {
            i18n.send(sender, "admin.equipment.group_not_found", "groupId", groupId);
            return;
        }

        EquipmentGroupConfig group = groupOpt.get();

        if (level != null) {
            listItemsAtLevel(sender, type, groupId, level);
        } else {
            // List all levels
            if (group.levelItems == null || group.levelItems.isEmpty()) {
                i18n.send(sender, "admin.equipment.item_list_empty");
                return;
            }

            for (int lvl : group.levelItems.keySet()) {
                listItemsAtLevel(sender, type, groupId, lvl);
            }
        }
    }

    private void listItemsAtLevel(CommandSender sender, EquipmentType type, String groupId, int level) {
        List<ItemTemplateConfig> items = adminConfig.getItemsInGroup(type, groupId, level);

        i18n.send(sender, "admin.equipment.item_list_header", "groupId", groupId, "level", level);

        if (items.isEmpty()) {
            i18n.send(sender, "admin.equipment.item_list_empty");
            return;
        }

        int index = 0;
        for (ItemTemplateConfig item : items) {
            ItemStack stack = item.toItemStack();
            String material = stack != null ? stack.getType().name() : "UNKNOWN";

            i18n.send(sender, "admin.equipment.item_list_entry",
                    "index", index,
                    "templateId", item.getTemplateId(),
                    "material", material);
            index++;
        }
    }

    // ==================== Tab Complete ====================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("group", "item")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (action.equals("group")) {
                for (String sub : List.of("create", "delete", "list")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            } else if (action.equals("item")) {
                for (String sub : List.of("add", "remove", "list")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            }
        } else if (args.length == 3) {
            String partial = args[2].toLowerCase();
            for (String type : List.of("weapon", "helmet")) {
                if (type.startsWith(partial)) {
                    completions.add(type);
                }
            }
        } else if (args.length == 4) {
            String typeStr = args[2].toLowerCase();
            String partial = args[3].toLowerCase();
            EquipmentType type = parseEquipmentType(typeStr);

            if (type != null) {
                for (EquipmentGroupConfig group : adminConfig.getEquipmentGroups(type)) {
                    if (group.groupId.toLowerCase().startsWith(partial)) {
                        completions.add(group.groupId);
                    }
                }
            }
        } else if (args.length == 5) {
            String action = args[0].toLowerCase();
            String subAction = args[1].toLowerCase();

            if ((action.equals("item") && List.of("add", "remove", "list").contains(subAction)) ||
                    (action.equals("group") && subAction.equals("delete"))) {
                // Level numbers
                String typeStr = args[2].toLowerCase();
                String groupId = args[3];
                EquipmentType type = parseEquipmentType(typeStr);

                if (type != null) {
                    Optional<EquipmentGroupConfig> groupOpt = adminConfig.getEquipmentGroup(type, groupId);
                    if (groupOpt.isPresent() && groupOpt.get().levelItems != null) {
                        String partial = args[4].toLowerCase();
                        for (int level : groupOpt.get().levelItems.keySet()) {
                            String levelStr = String.valueOf(level);
                            if (levelStr.startsWith(partial)) {
                                completions.add(levelStr);
                            }
                        }
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin.capture";
    }

    // ==================== Utility Methods ====================

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

    private Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
