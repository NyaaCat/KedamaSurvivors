package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService.CombatWorldConfig;
import cat.nyaa.survivors.config.ConfigService.SpawnPointConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.service.AdminConfigService;
import cat.nyaa.survivors.service.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles /vrs admin world subcommands for managing combat worlds.
 */
public class WorldSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final AdminConfigService adminConfig;
    private final WorldService worldService;

    public WorldSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.adminConfig = plugin.getAdminConfigService();
        this.worldService = plugin.getWorldService();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            handleList(sender);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "set" -> handleSet(sender, args);
            case "enable" -> handleEnable(sender, args);
            case "disable" -> handleDisable(sender, args);
            case "addspawn" -> handleAddSpawn(sender, args);
            case "removespawn" -> handleRemoveSpawn(sender, args);
            case "listspawns" -> handleListSpawns(sender, args);
            case "clearspawns" -> handleClearSpawns(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "admin.world.help.header");
        i18n.send(sender, "admin.world.help.create");
        i18n.send(sender, "admin.world.help.delete");
        i18n.send(sender, "admin.world.help.list");
        i18n.send(sender, "admin.world.help.set_displayname");
        i18n.send(sender, "admin.world.help.set_weight");
        i18n.send(sender, "admin.world.help.set_bounds");
        i18n.send(sender, "admin.world.help.addspawn");
        i18n.send(sender, "admin.world.help.removespawn");
        i18n.send(sender, "admin.world.help.listspawns");
        i18n.send(sender, "admin.world.help.clearspawns");
        i18n.send(sender, "admin.world.help.enable");
        i18n.send(sender, "admin.world.help.disable");
    }

    private void handleCreate(CommandSender sender, String[] args) {
        // /vrs admin world create <name> [displayName]
        if (args.length < 2) {
            i18n.send(sender, "admin.world.help.create");
            return;
        }

        String name = args[1];
        String displayName = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;

        // Check if world exists in Bukkit
        World world = Bukkit.getWorld(name);
        if (world == null) {
            i18n.send(sender, "admin.world.world_not_loaded", "world", name);
            return;
        }

        // Default bounds based on world border or fixed values
        double minX = -500, maxX = 500, minZ = -500, maxZ = 500;

        boolean success = adminConfig.createWorld(name, displayName, 1.0, minX, maxX, minZ, maxZ);
        if (success) {
            i18n.send(sender, "admin.world.created",
                    "name", name,
                    "displayName", displayName != null ? displayName : name);
        } else {
            i18n.send(sender, "admin.world.exists", "name", name);
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        // /vrs admin world delete <name>
        if (args.length < 2) {
            i18n.send(sender, "admin.world.help.delete");
            return;
        }

        String name = args[1];

        boolean success = adminConfig.deleteWorld(name);
        if (success) {
            i18n.send(sender, "admin.world.deleted", "name", name);
        } else {
            i18n.send(sender, "admin.world.not_found", "name", name);
        }
    }

    private void handleList(CommandSender sender) {
        List<CombatWorldConfig> worlds = adminConfig.getCombatWorlds();

        i18n.send(sender, "admin.world.list_header");

        if (worlds.isEmpty()) {
            i18n.send(sender, "admin.world.list_empty");
            return;
        }

        for (CombatWorldConfig world : worlds) {
            boolean enabled = worldService.isWorldEnabled(world.name);
            String status = i18n.get(enabled ? "admin.world.status_enabled" : "admin.world.status_disabled");
            int spawnCount = world.spawnPoints.size();
            i18n.send(sender, "admin.world.list_entry",
                    "name", world.name,
                    "displayName", world.displayName,
                    "status", status,
                    "weight", world.weight,
                    "minX", world.minX,
                    "maxX", world.maxX,
                    "minZ", world.minZ,
                    "maxZ", world.maxZ,
                    "spawnCount", spawnCount);
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        // /vrs admin world set <property> <name> <value...>
        if (args.length < 4) {
            showHelp(sender);
            return;
        }

        String property = args[1].toLowerCase();
        String name = args[2];

        // Check world exists
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(name);
        if (worldOpt.isEmpty()) {
            i18n.send(sender, "admin.world.not_found", "name", name);
            return;
        }

        switch (property) {
            case "displayname" -> {
                if (args.length < 4) {
                    i18n.send(sender, "admin.world.help.set_displayname");
                    return;
                }
                String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                boolean success = adminConfig.setWorldDisplayName(name, displayName);
                if (success) {
                    i18n.send(sender, "admin.world.displayname_set", "name", name, "displayName", displayName);
                }
            }
            case "weight" -> {
                if (args.length < 4) {
                    i18n.send(sender, "admin.world.help.set_weight");
                    return;
                }
                try {
                    double weight = Double.parseDouble(args[3]);
                    if (weight <= 0) throw new NumberFormatException();
                    boolean success = adminConfig.setWorldWeight(name, weight);
                    if (success) {
                        i18n.send(sender, "admin.world.weight_set", "name", name, "weight", weight);
                    }
                } catch (NumberFormatException e) {
                    i18n.send(sender, "error.invalid_number", "value", args[3]);
                }
            }
            case "bounds" -> {
                if (args.length < 7) {
                    i18n.send(sender, "admin.world.help.set_bounds");
                    return;
                }
                try {
                    double minX = Double.parseDouble(args[3]);
                    double maxX = Double.parseDouble(args[4]);
                    double minZ = Double.parseDouble(args[5]);
                    double maxZ = Double.parseDouble(args[6]);
                    boolean success = adminConfig.setWorldBounds(name, minX, maxX, minZ, maxZ);
                    if (success) {
                        i18n.send(sender, "admin.world.bounds_set", "name", name,
                                "minX", minX, "maxX", maxX, "minZ", minZ, "maxZ", maxZ);
                    }
                } catch (NumberFormatException e) {
                    i18n.send(sender, "error.invalid_number", "value", "bounds");
                }
            }
            default -> showHelp(sender);
        }
    }

    private void handleEnable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "admin.world.help.enable");
            return;
        }

        String name = args[1];
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(name);
        if (worldOpt.isEmpty()) {
            i18n.send(sender, "admin.world.not_found", "name", name);
            return;
        }

        adminConfig.setWorldEnabled(name, true);
        worldService.enableWorld(name);
        i18n.send(sender, "admin.world.enabled", "name", name);
    }

    private void handleDisable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "admin.world.help.disable");
            return;
        }

        String name = args[1];
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(name);
        if (worldOpt.isEmpty()) {
            i18n.send(sender, "admin.world.not_found", "name", name);
            return;
        }

        adminConfig.setWorldEnabled(name, false);
        worldService.disableWorld(name);
        i18n.send(sender, "admin.world.disabled", "name", name);
    }

    private void handleAddSpawn(CommandSender sender, String[] args) {
        // /vrs admin world addspawn <name> - uses player's current location
        // /vrs admin world addspawn <name> <x> <y> <z> [yaw] [pitch]
        if (args.length < 2) {
            i18n.send(sender, "admin.world.help.addspawn");
            return;
        }

        String name = args[1];
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(name);
        if (worldOpt.isEmpty()) {
            i18n.send(sender, "admin.world.not_found", "name", name);
            return;
        }

        double x, y, z;
        Float yaw = null, pitch = null;

        if (args.length == 2) {
            // Use player's current location
            if (!(sender instanceof Player player)) {
                i18n.send(sender, "error.player_only");
                return;
            }
            Location loc = player.getLocation();
            x = loc.getX();
            y = loc.getY();
            z = loc.getZ();
            yaw = loc.getYaw();
            pitch = loc.getPitch();
        } else if (args.length >= 5) {
            // Use provided coordinates
            try {
                x = Double.parseDouble(args[2]);
                y = Double.parseDouble(args[3]);
                z = Double.parseDouble(args[4]);
                if (args.length >= 6) yaw = Float.parseFloat(args[5]);
                if (args.length >= 7) pitch = Float.parseFloat(args[6]);
            } catch (NumberFormatException e) {
                i18n.send(sender, "error.invalid_number", "value", "coordinates");
                return;
            }
        } else {
            i18n.send(sender, "admin.world.help.addspawn");
            return;
        }

        boolean success = adminConfig.addWorldSpawnPoint(name, x, y, z, yaw, pitch);
        if (success) {
            int index = worldOpt.get().spawnPoints.size(); // New index after add
            i18n.send(sender, "admin.world.spawn_added", "name", name,
                    "index", index,
                    "x", String.format("%.1f", x),
                    "y", String.format("%.1f", y),
                    "z", String.format("%.1f", z));
        }
    }

    private void handleRemoveSpawn(CommandSender sender, String[] args) {
        // /vrs admin world removespawn <name> <index>
        if (args.length < 3) {
            i18n.send(sender, "admin.world.help.removespawn");
            return;
        }

        String name = args[1];
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(name);
        if (worldOpt.isEmpty()) {
            i18n.send(sender, "admin.world.not_found", "name", name);
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            i18n.send(sender, "error.invalid_number", "value", args[2]);
            return;
        }

        // Convert to 0-based index
        int zeroBasedIndex = index - 1;
        boolean success = adminConfig.removeWorldSpawnPoint(name, zeroBasedIndex);
        if (success) {
            i18n.send(sender, "admin.world.spawn_removed", "name", name, "index", index);
        } else {
            i18n.send(sender, "admin.world.spawn_index_invalid", "name", name, "index", index);
        }
    }

    private void handleListSpawns(CommandSender sender, String[] args) {
        // /vrs admin world listspawns <name>
        if (args.length < 2) {
            i18n.send(sender, "admin.world.help.listspawns");
            return;
        }

        String name = args[1];
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(name);
        if (worldOpt.isEmpty()) {
            i18n.send(sender, "admin.world.not_found", "name", name);
            return;
        }

        List<SpawnPointConfig> spawns = adminConfig.getWorldSpawnPoints(name);
        i18n.send(sender, "admin.world.spawns_header", "name", name, "count", spawns.size());

        if (spawns.isEmpty()) {
            i18n.send(sender, "admin.world.spawns_empty");
            return;
        }

        for (int i = 0; i < spawns.size(); i++) {
            SpawnPointConfig sp = spawns.get(i);
            String yawPitch = "";
            if (sp.yaw != null && sp.pitch != null) {
                yawPitch = String.format(" yaw=%.1f pitch=%.1f", sp.yaw, sp.pitch);
            }
            i18n.send(sender, "admin.world.spawn_entry",
                    "index", i + 1,
                    "x", String.format("%.1f", sp.x),
                    "y", String.format("%.1f", sp.y),
                    "z", String.format("%.1f", sp.z),
                    "extra", yawPitch);
        }
    }

    private void handleClearSpawns(CommandSender sender, String[] args) {
        // /vrs admin world clearspawns <name>
        if (args.length < 2) {
            i18n.send(sender, "admin.world.help.clearspawns");
            return;
        }

        String name = args[1];
        Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(name);
        if (worldOpt.isEmpty()) {
            i18n.send(sender, "admin.world.not_found", "name", name);
            return;
        }

        boolean success = adminConfig.clearWorldSpawnPoints(name);
        if (success) {
            i18n.send(sender, "admin.world.spawns_cleared", "name", name);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("create", "delete", "list", "set", "enable", "disable",
                    "addspawn", "removespawn", "listspawns", "clearspawns")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (action.equals("set")) {
                for (String prop : List.of("displayname", "weight", "bounds")) {
                    if (prop.startsWith(partial)) {
                        completions.add(prop);
                    }
                }
            } else if (action.equals("create")) {
                // Suggest Bukkit worlds not already configured
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase().startsWith(partial) &&
                            adminConfig.getWorld(world.getName()).isEmpty()) {
                        completions.add(world.getName());
                    }
                }
            } else if (List.of("delete", "enable", "disable", "addspawn", "removespawn",
                    "listspawns", "clearspawns").contains(action)) {
                // Suggest configured worlds
                for (CombatWorldConfig world : adminConfig.getCombatWorlds()) {
                    if (world.name.toLowerCase().startsWith(partial)) {
                        completions.add(world.name);
                    }
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            String partial = args[2].toLowerCase();

            if (action.equals("set")) {
                // Suggest configured worlds
                for (CombatWorldConfig world : adminConfig.getCombatWorlds()) {
                    if (world.name.toLowerCase().startsWith(partial)) {
                        completions.add(world.name);
                    }
                }
            } else if (action.equals("removespawn")) {
                // Suggest spawn point indices
                String worldName = args[1];
                Optional<CombatWorldConfig> worldOpt = adminConfig.getWorld(worldName);
                if (worldOpt.isPresent()) {
                    int count = worldOpt.get().spawnPoints.size();
                    for (int i = 1; i <= count; i++) {
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
        return "vrs.admin.world";
    }
}
