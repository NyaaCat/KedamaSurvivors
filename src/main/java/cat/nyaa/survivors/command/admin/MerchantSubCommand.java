package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService.MerchantTemplateConfig;
import cat.nyaa.survivors.config.ConfigService.MerchantTradeConfig;
import cat.nyaa.survivors.i18n.I18nService;
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
 * Handles /vrs admin merchant subcommands for managing merchant templates and trades.
 */
public class MerchantSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final AdminConfigService adminConfig;

    public MerchantSubCommand(KedamaSurvivorsPlugin plugin) {
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
            case "template" -> handleTemplate(sender, args);
            case "trade" -> handleTrade(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "admin.merchant.help.header");
        i18n.send(sender, "admin.merchant.help.template_create");
        i18n.send(sender, "admin.merchant.help.template_delete");
        i18n.send(sender, "admin.merchant.help.template_list");
        i18n.send(sender, "admin.merchant.help.template_set_displayname");
        i18n.send(sender, "admin.merchant.help.trade_add");
        i18n.send(sender, "admin.merchant.help.trade_remove");
        i18n.send(sender, "admin.merchant.help.trade_list");
    }

    // ==================== Template Commands ====================

    private void handleTemplate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showHelp(sender);
            return;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "create" -> handleTemplateCreate(sender, args);
            case "delete" -> handleTemplateDelete(sender, args);
            case "list" -> handleTemplateList(sender);
            case "set" -> handleTemplateSet(sender, args);
            default -> showHelp(sender);
        }
    }

    private void handleTemplateSet(CommandSender sender, String[] args) {
        // /vrs admin merchant template set <property> <templateId> <value...>
        if (args.length < 5) {
            i18n.send(sender, "admin.merchant.help.template_set_displayname");
            return;
        }

        String property = args[2].toLowerCase();
        String templateId = args[3];

        Optional<MerchantTemplateConfig> templateOpt = adminConfig.getMerchantTemplate(templateId);
        if (templateOpt.isEmpty()) {
            i18n.send(sender, "admin.merchant.template_not_found", "templateId", templateId);
            return;
        }

        switch (property) {
            case "displayname" -> {
                String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                boolean success = adminConfig.setMerchantTemplateDisplayName(templateId, displayName);
                if (success) {
                    i18n.send(sender, "admin.merchant.displayname_set",
                            "templateId", templateId,
                            "displayName", displayName);
                }
            }
            default -> i18n.send(sender, "admin.merchant.help.template_set_displayname");
        }
    }

    private void handleTemplateCreate(CommandSender sender, String[] args) {
        // /vrs admin merchant template create <templateId> [displayName...]
        if (args.length < 3) {
            i18n.send(sender, "admin.merchant.help.template_create");
            return;
        }

        String templateId = args[2];
        String displayName = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : templateId;

        if (!isValidId(templateId)) {
            i18n.send(sender, "error.invalid_argument", "arg", templateId);
            return;
        }

        boolean success = adminConfig.createMerchantTemplate(templateId, displayName);
        if (success) {
            i18n.send(sender, "admin.merchant.template_created",
                    "templateId", templateId,
                    "displayName", displayName);
        } else {
            i18n.send(sender, "admin.merchant.template_exists", "templateId", templateId);
        }
    }

    private void handleTemplateDelete(CommandSender sender, String[] args) {
        // /vrs admin merchant template delete <templateId>
        if (args.length < 3) {
            i18n.send(sender, "admin.merchant.help.template_delete");
            return;
        }

        String templateId = args[2];

        Optional<MerchantTemplateConfig> templateOpt = adminConfig.getMerchantTemplate(templateId);
        if (templateOpt.isEmpty()) {
            i18n.send(sender, "admin.merchant.template_not_found", "templateId", templateId);
            return;
        }

        int tradeCount = templateOpt.get().trades.size();
        if (tradeCount > 0) {
            i18n.send(sender, "admin.merchant.template_has_trades", "count", tradeCount);
        }

        boolean success = adminConfig.deleteMerchantTemplate(templateId);
        if (success) {
            i18n.send(sender, "admin.merchant.template_deleted", "templateId", templateId);
        } else {
            i18n.send(sender, "admin.merchant.template_not_found", "templateId", templateId);
        }
    }

    private void handleTemplateList(CommandSender sender) {
        Collection<MerchantTemplateConfig> templates = adminConfig.getMerchantTemplates();

        i18n.send(sender, "admin.merchant.template_list_header");

        if (templates.isEmpty()) {
            i18n.send(sender, "admin.merchant.template_list_empty");
            return;
        }

        for (MerchantTemplateConfig template : templates) {
            i18n.send(sender, "admin.merchant.template_list_entry",
                    "templateId", template.templateId,
                    "displayName", template.displayName,
                    "tradeCount", template.trades.size());
        }
    }

    // ==================== Trade Commands ====================

    private void handleTrade(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showHelp(sender);
            return;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "add" -> handleTradeAdd(sender, args);
            case "remove" -> handleTradeRemove(sender, args);
            case "list" -> handleTradeList(sender, args);
            default -> showHelp(sender);
        }
    }

    private void handleTradeAdd(CommandSender sender, String[] args) {
        // /vrs admin merchant trade add <templateId> <costAmount> [maxUses]
        if (!(sender instanceof Player player)) {
            i18n.send(sender, "error.player_only");
            return;
        }

        if (args.length < 4) {
            i18n.send(sender, "admin.merchant.help.trade_add");
            return;
        }

        String templateId = args[2];
        int costAmount;
        int maxUses = 10; // default

        try {
            costAmount = Integer.parseInt(args[3]);
            if (costAmount < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[3]);
            return;
        }

        if (args.length > 4) {
            try {
                maxUses = Integer.parseInt(args[4]);
                if (maxUses < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                i18n.send(sender, "error.invalid_number", "value", args[4]);
                return;
            }
        }

        // Check template exists
        Optional<MerchantTemplateConfig> templateOpt = adminConfig.getMerchantTemplate(templateId);
        if (templateOpt.isEmpty()) {
            i18n.send(sender, "admin.merchant.template_not_found", "templateId", templateId);
            return;
        }

        // Get item in hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            i18n.send(sender, "admin.merchant.no_item_in_hand");
            return;
        }

        // Capture item and add trade
        String itemTemplateId = adminConfig.captureItemForMerchant(itemInHand, templateId);
        if (itemTemplateId == null) {
            i18n.send(sender, "error.generic");
            return;
        }

        // Create trade config
        MerchantTradeConfig trade = new MerchantTradeConfig();
        trade.resultItem = itemTemplateId;
        trade.resultAmount = itemInHand.getAmount();
        trade.costItem = "coin";
        trade.costAmount = costAmount;
        trade.maxUses = maxUses;

        boolean success = adminConfig.addMerchantTrade(templateId, trade);
        if (success) {
            i18n.send(sender, "admin.merchant.trade_added",
                    "templateId", templateId,
                    "item", itemInHand.getType().name(),
                    "cost", costAmount,
                    "maxUses", maxUses);
        } else {
            i18n.send(sender, "admin.merchant.template_not_found", "templateId", templateId);
        }
    }

    private void handleTradeRemove(CommandSender sender, String[] args) {
        // /vrs admin merchant trade remove <templateId> <index>
        if (args.length < 4) {
            i18n.send(sender, "admin.merchant.help.trade_remove");
            return;
        }

        String templateId = args[2];
        int index;

        try {
            index = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[3]);
            return;
        }

        Optional<MerchantTemplateConfig> templateOpt = adminConfig.getMerchantTemplate(templateId);
        if (templateOpt.isEmpty()) {
            i18n.send(sender, "admin.merchant.template_not_found", "templateId", templateId);
            return;
        }

        boolean success = adminConfig.removeMerchantTrade(templateId, index);
        if (success) {
            i18n.send(sender, "admin.merchant.trade_removed", "templateId", templateId, "index", index);
        } else {
            i18n.send(sender, "admin.merchant.trade_not_found", "index", index);
        }
    }

    private void handleTradeList(CommandSender sender, String[] args) {
        // /vrs admin merchant trade list <templateId>
        if (args.length < 3) {
            i18n.send(sender, "admin.merchant.help.trade_list");
            return;
        }

        String templateId = args[2];

        Optional<MerchantTemplateConfig> templateOpt = adminConfig.getMerchantTemplate(templateId);
        if (templateOpt.isEmpty()) {
            i18n.send(sender, "admin.merchant.template_not_found", "templateId", templateId);
            return;
        }

        MerchantTemplateConfig template = templateOpt.get();

        i18n.send(sender, "admin.merchant.trade_list_header", "templateId", templateId);

        if (template.trades.isEmpty()) {
            i18n.send(sender, "admin.merchant.trade_list_empty");
            return;
        }

        int index = 0;
        for (MerchantTradeConfig trade : template.trades) {
            i18n.send(sender, "admin.merchant.trade_list_entry",
                    "index", index,
                    "resultItem", trade.resultItem,
                    "resultAmount", trade.resultAmount,
                    "costAmount", trade.costAmount,
                    "maxUses", trade.maxUses);
            index++;
        }
    }

    // ==================== Tab Complete ====================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("template", "trade")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (action.equals("template")) {
                for (String sub : List.of("create", "delete", "list", "set")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            } else if (action.equals("trade")) {
                for (String sub : List.of("add", "remove", "list")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            String subAction = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            if (action.equals("template") && subAction.equals("set")) {
                // Property names for set
                if ("displayname".startsWith(partial)) {
                    completions.add("displayname");
                }
            } else if ((action.equals("template") && List.of("delete").contains(subAction)) ||
                    (action.equals("trade") && List.of("add", "remove", "list").contains(subAction))) {
                // Template ID completion for relevant commands
                for (MerchantTemplateConfig template : adminConfig.getMerchantTemplates()) {
                    if (template.templateId.toLowerCase().startsWith(partial)) {
                        completions.add(template.templateId);
                    }
                }
            }
        } else if (args.length == 4) {
            String action = args[0].toLowerCase();
            String subAction = args[1].toLowerCase();
            String partial = args[3].toLowerCase();

            // Template ID for template set
            if (action.equals("template") && subAction.equals("set")) {
                for (MerchantTemplateConfig template : adminConfig.getMerchantTemplates()) {
                    if (template.templateId.toLowerCase().startsWith(partial)) {
                        completions.add(template.templateId);
                    }
                }
            }
            // Cost amount suggestions for trade add
            if (action.equals("trade") && subAction.equals("add")) {
                completions.addAll(List.of("10", "25", "50", "100"));
            }
            // Index suggestions for trade remove
            if (action.equals("trade") && subAction.equals("remove")) {
                String templateId = args[2];
                Optional<MerchantTemplateConfig> templateOpt = adminConfig.getMerchantTemplate(templateId);
                if (templateOpt.isPresent()) {
                    for (int i = 0; i < templateOpt.get().trades.size(); i++) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
        } else if (args.length == 5) {
            String action = args[0].toLowerCase();
            String subAction = args[1].toLowerCase();

            // Max uses suggestions for trade add
            if (action.equals("trade") && subAction.equals("add")) {
                completions.addAll(List.of("1", "3", "5", "10"));
            }
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin";
    }

    // ==================== Utility Methods ====================

    private boolean isValidId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_]+");
    }
}
