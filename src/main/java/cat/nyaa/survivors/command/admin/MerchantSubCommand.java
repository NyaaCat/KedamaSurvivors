package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.MerchantTemplateConfig;
import cat.nyaa.survivors.config.ConfigService.MerchantTradeConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.merchant.MerchantBehavior;
import cat.nyaa.survivors.merchant.MerchantInstance;
import cat.nyaa.survivors.merchant.MerchantItemPool;
import cat.nyaa.survivors.merchant.MerchantType;
import cat.nyaa.survivors.merchant.WeightedShopItem;
import cat.nyaa.survivors.service.AdminConfigService;
import cat.nyaa.survivors.service.MerchantService;
import org.bukkit.Location;
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
    private final MerchantService merchantService;
    private final ConfigService configService;

    public MerchantSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.adminConfig = plugin.getAdminConfigService();
        this.merchantService = plugin.getMerchantService();
        this.configService = plugin.getConfigService();
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
            case "pool" -> handlePool(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            case "despawn" -> handleDespawn(sender, args);
            case "active" -> handleActive(sender, args);
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
        i18n.send(sender, "admin.merchant.help.pool_create");
        i18n.send(sender, "admin.merchant.help.pool_delete");
        i18n.send(sender, "admin.merchant.help.pool_additem");
        i18n.send(sender, "admin.merchant.help.pool_removeitem");
        i18n.send(sender, "admin.merchant.help.pool_list");
        i18n.send(sender, "admin.merchant.help.spawn");
        i18n.send(sender, "admin.merchant.help.despawn");
        i18n.send(sender, "admin.merchant.help.active");
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

    // ==================== Pool Commands ====================

    private void handlePool(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showHelp(sender);
            return;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "create" -> handlePoolCreate(sender, args);
            case "delete" -> handlePoolDelete(sender, args);
            case "additem" -> handlePoolAddItem(sender, args);
            case "removeitem" -> handlePoolRemoveItem(sender, args);
            case "list" -> handlePoolList(sender, args);
            default -> showHelp(sender);
        }
    }

    private void handlePoolCreate(CommandSender sender, String[] args) {
        // /vrs admin merchant pool create <poolId>
        if (args.length < 3) {
            i18n.send(sender, "admin.merchant.help.pool_create");
            return;
        }

        String poolId = args[2];

        if (!isValidId(poolId)) {
            i18n.send(sender, "error.invalid_argument", "arg", poolId);
            return;
        }

        boolean success = adminConfig.createMerchantPool(poolId);
        if (success) {
            i18n.send(sender, "admin.merchant.pool_created", "poolId", poolId);
        } else {
            i18n.send(sender, "admin.merchant.pool_exists", "poolId", poolId);
        }
    }

    private void handlePoolDelete(CommandSender sender, String[] args) {
        // /vrs admin merchant pool delete <poolId>
        if (args.length < 3) {
            i18n.send(sender, "admin.merchant.help.pool_delete");
            return;
        }

        String poolId = args[2];

        boolean success = adminConfig.deleteMerchantPool(poolId);
        if (success) {
            i18n.send(sender, "admin.merchant.pool_deleted", "poolId", poolId);
        } else {
            i18n.send(sender, "admin.merchant.pool_not_found", "poolId", poolId);
        }
    }

    private void handlePoolAddItem(CommandSender sender, String[] args) {
        // /vrs admin merchant pool additem <poolId> <price> [weight]
        if (!(sender instanceof Player player)) {
            i18n.send(sender, "error.player_only");
            return;
        }

        if (args.length < 4) {
            i18n.send(sender, "admin.merchant.help.pool_additem");
            return;
        }

        String poolId = args[2];
        int price;
        double weight = 1.0; // default weight

        try {
            price = Integer.parseInt(args[3]);
            if (price < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[3]);
            return;
        }

        if (args.length > 4) {
            try {
                weight = Double.parseDouble(args[4]);
                if (weight <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                i18n.send(sender, "error.invalid_number", "value", args[4]);
                return;
            }
        }

        // Check pool exists
        Optional<MerchantItemPool> poolOpt = adminConfig.getMerchantPool(poolId);
        if (poolOpt.isEmpty()) {
            i18n.send(sender, "admin.merchant.pool_not_found", "poolId", poolId);
            return;
        }

        // Get item in hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            i18n.send(sender, "admin.merchant.no_item_in_hand");
            return;
        }

        // Capture item and add to pool
        String templateId = adminConfig.captureItemToPool(itemInHand, poolId, price, weight);
        if (templateId != null) {
            i18n.send(sender, "admin.merchant.pool_item_added",
                    "poolId", poolId,
                    "price", price,
                    "weight", weight);
        } else {
            i18n.send(sender, "error.generic");
        }
    }

    private void handlePoolRemoveItem(CommandSender sender, String[] args) {
        // /vrs admin merchant pool removeitem <poolId> <index>
        if (args.length < 4) {
            i18n.send(sender, "admin.merchant.help.pool_removeitem");
            return;
        }

        String poolId = args[2];
        int index;

        try {
            index = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[3]);
            return;
        }

        // Convert to 0-based index
        int zeroBasedIndex = index - 1;
        if (zeroBasedIndex < 0) {
            i18n.send(sender, "error.invalid_number", "value", args[3]);
            return;
        }

        WeightedShopItem removed = adminConfig.removeItemFromPool(poolId, zeroBasedIndex);
        if (removed != null) {
            i18n.send(sender, "admin.merchant.pool_item_removed", "poolId", poolId);
        } else {
            i18n.send(sender, "admin.merchant.pool_not_found", "poolId", poolId);
        }
    }

    private void handlePoolList(CommandSender sender, String[] args) {
        // /vrs admin merchant pool list [poolId]
        if (args.length < 3) {
            // List all pools
            Collection<MerchantItemPool> pools = adminConfig.getMerchantPools();

            i18n.send(sender, "admin.merchant.pool_list_header");

            if (pools.isEmpty()) {
                i18n.send(sender, "admin.merchant.pool_list_empty");
                return;
            }

            for (MerchantItemPool pool : pools) {
                i18n.send(sender, "admin.merchant.pool_list_entry",
                        "poolId", pool.getPoolId(),
                        "itemCount", pool.getItems().size());
            }
        } else {
            // List items in a specific pool
            String poolId = args[2];

            Optional<MerchantItemPool> poolOpt = adminConfig.getMerchantPool(poolId);
            if (poolOpt.isEmpty()) {
                i18n.send(sender, "admin.merchant.pool_not_found", "poolId", poolId);
                return;
            }

            MerchantItemPool pool = poolOpt.get();
            List<WeightedShopItem> items = pool.getItems();

            i18n.send(sender, "admin.merchant.pool_items_header", "poolId", poolId);

            if (items.isEmpty()) {
                i18n.send(sender, "admin.merchant.pool_items_empty");
                return;
            }

            int index = 1; // 1-based for display
            for (WeightedShopItem item : items) {
                i18n.send(sender, "admin.merchant.pool_items_entry",
                        "index", index,
                        "templateId", item.getItemTemplateId(),
                        "price", item.getPrice(),
                        "weight", item.getWeight());
                index++;
            }
        }
    }

    // ==================== Spawn Commands ====================

    private void handleSpawn(CommandSender sender, String[] args) {
        // /vrs admin merchant spawn <poolId> <multi|single> [limited|unlimited] [all|random]
        if (!(sender instanceof Player player)) {
            i18n.send(sender, "error.player_only");
            return;
        }

        if (args.length < 3) {
            i18n.send(sender, "admin.merchant.help.spawn");
            return;
        }

        String poolId = args[1];
        String typeArg = args[2].toLowerCase();

        // Parse merchant type
        MerchantType type;
        if (typeArg.equals("multi")) {
            type = MerchantType.MULTI;
        } else if (typeArg.equals("single")) {
            type = MerchantType.SINGLE;
        } else {
            i18n.send(sender, "admin.merchant.invalid_type", "type", typeArg);
            return;
        }

        // Check pool exists and has items
        Optional<MerchantItemPool> poolOpt = adminConfig.getMerchantPool(poolId);
        if (poolOpt.isEmpty()) {
            i18n.send(sender, "admin.merchant.pool_not_found", "poolId", poolId);
            return;
        }

        MerchantItemPool pool = poolOpt.get();
        if (pool.getItems().isEmpty()) {
            i18n.send(sender, "admin.merchant.pool_empty", "poolId", poolId);
            return;
        }

        // Parse limited flag (optional, defaults to config value)
        boolean limited = configService.isMerchantLimited();
        // Parse showAllItems flag (optional, defaults to false for fixed merchants)
        boolean showAllItems = false;

        // Parse optional flags (can appear in any order after type)
        for (int i = 3; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            switch (arg) {
                case "limited" -> limited = true;
                case "unlimited" -> limited = false;
                case "all" -> showAllItems = true;
                case "random" -> showAllItems = false;
            }
        }

        // Get spawn location (player's location)
        Location location = player.getLocation();

        // Generate display name
        String displayName = type == MerchantType.MULTI ? "§6商人" : pool.getItems().get(0).getItemTemplateId();

        // Spawn the merchant
        MerchantInstance merchant = merchantService.spawnFixedMerchant(location, type, poolId, limited, showAllItems, displayName);

        if (merchant != null) {
            String shortId = merchant.getInstanceId().toString().substring(0, 8);
            i18n.send(sender, "admin.merchant.spawned",
                    "type", type.name(),
                    "id", shortId,
                    "poolId", poolId);
        } else {
            i18n.send(sender, "error.generic");
        }
    }

    private void handleDespawn(CommandSender sender, String[] args) {
        // /vrs admin merchant despawn [radius]
        if (!(sender instanceof Player player)) {
            i18n.send(sender, "error.player_only");
            return;
        }

        double radius = 5.0; // default radius
        if (args.length > 1) {
            try {
                radius = Double.parseDouble(args[1]);
                if (radius <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                i18n.send(sender, "error.invalid_number", "value", args[1]);
                return;
            }
        }

        Location playerLoc = player.getLocation();

        // Find nearest merchant within radius
        MerchantInstance nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (MerchantInstance merchant : merchantService.getActiveMerchants()) {
            Location merchantLoc = merchant.getLocation();
            if (merchantLoc != null && merchantLoc.getWorld() == playerLoc.getWorld()) {
                double distance = merchantLoc.distance(playerLoc);
                if (distance <= radius && distance < nearestDistance) {
                    nearest = merchant;
                    nearestDistance = distance;
                }
            }
        }

        if (nearest == null) {
            i18n.send(sender, "admin.merchant.no_nearby_merchant");
            return;
        }

        String shortId = nearest.getInstanceId().toString().substring(0, 8);
        merchantService.despawnMerchant(nearest.getInstanceId());
        i18n.send(sender, "admin.merchant.despawned", "id", shortId);
    }

    private void handleActive(CommandSender sender, String[] args) {
        // /vrs admin merchant active
        Collection<MerchantInstance> merchants = merchantService.getActiveMerchants();

        i18n.send(sender, "admin.merchant.active_header", "count", merchants.size());

        if (merchants.isEmpty()) {
            i18n.send(sender, "admin.merchant.active_empty");
            return;
        }

        for (MerchantInstance merchant : merchants) {
            Location loc = merchant.getLocation();
            String worldName = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "?";
            int x = loc != null ? loc.getBlockX() : 0;
            int y = loc != null ? loc.getBlockY() : 0;
            int z = loc != null ? loc.getBlockZ() : 0;

            String shortId = merchant.getInstanceId().toString().substring(0, 8);

            i18n.send(sender, "admin.merchant.active_entry",
                    "id", shortId,
                    "type", merchant.getType().name(),
                    "behavior", merchant.getBehavior().name(),
                    "poolId", merchant.getPoolId() != null ? merchant.getPoolId() : "-",
                    "world", worldName,
                    "x", x,
                    "y", y,
                    "z", z);
        }
    }

    // ==================== Tab Complete ====================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("template", "trade", "pool", "spawn", "despawn", "active")) {
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
            } else if (action.equals("pool")) {
                for (String sub : List.of("create", "delete", "additem", "removeitem", "list")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            } else if (action.equals("spawn")) {
                // Pool ID completion for spawn command
                for (MerchantItemPool pool : adminConfig.getMerchantPools()) {
                    if (pool.getPoolId().toLowerCase().startsWith(partial)) {
                        completions.add(pool.getPoolId());
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
            } else if (action.equals("pool") && List.of("delete", "additem", "removeitem", "list").contains(subAction)) {
                // Pool ID completion for pool commands
                for (MerchantItemPool pool : adminConfig.getMerchantPools()) {
                    if (pool.getPoolId().toLowerCase().startsWith(partial)) {
                        completions.add(pool.getPoolId());
                    }
                }
            } else if (action.equals("spawn")) {
                // Merchant type completion for spawn command
                for (String type : List.of("multi", "single")) {
                    if (type.startsWith(partial)) {
                        completions.add(type);
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
            // Price suggestions for pool additem
            if (action.equals("pool") && subAction.equals("additem")) {
                completions.addAll(List.of("10", "25", "50", "100"));
            }
            // Index suggestions for pool removeitem (1-based)
            if (action.equals("pool") && subAction.equals("removeitem")) {
                String poolId = args[2];
                Optional<MerchantItemPool> poolOpt = adminConfig.getMerchantPool(poolId);
                if (poolOpt.isPresent()) {
                    for (int i = 1; i <= poolOpt.get().getItems().size(); i++) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
            // Limited/unlimited and all/random completion for spawn command
            if (action.equals("spawn")) {
                for (String opt : List.of("limited", "unlimited", "all", "random")) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
        } else if (args.length == 5) {
            String action = args[0].toLowerCase();
            String subAction = args[1].toLowerCase();
            String partial = args[4].toLowerCase();

            // Max uses suggestions for trade add
            if (action.equals("trade") && subAction.equals("add")) {
                completions.addAll(List.of("1", "3", "5", "10"));
            }
            // Weight suggestions for pool additem
            if (action.equals("pool") && subAction.equals("additem")) {
                completions.addAll(List.of("0.5", "1.0", "2.0", "5.0"));
            }
            // Additional limited/unlimited and all/random completion for spawn command
            if (action.equals("spawn")) {
                for (String opt : List.of("limited", "unlimited", "all", "random")) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
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
