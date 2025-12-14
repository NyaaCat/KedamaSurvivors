package cat.nyaa.survivors.command;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.admin.EquipmentSubCommand;
import cat.nyaa.survivors.command.admin.SpawnerSubCommand;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.scoreboard.ScoreboardService;
import cat.nyaa.survivors.service.ReadyService;
import cat.nyaa.survivors.service.StateService;
import cat.nyaa.survivors.service.WorldService;
import cat.nyaa.survivors.util.TemplateEngine;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles /vrs admin commands for server administration.
 */
public class AdminSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;
    private final ReadyService readyService;
    private final ScoreboardService scoreboardService;
    private final WorldService worldService;
    private final TemplateEngine templateEngine;

    // Nested subcommand handlers
    private EquipmentSubCommand equipmentSubCommand;
    private SpawnerSubCommand spawnerSubCommand;

    public AdminSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.readyService = plugin.getReadyService();
        this.scoreboardService = plugin.getScoreboardService();
        this.worldService = plugin.getWorldService();
        this.templateEngine = plugin.getTemplateEngine();
    }

    /**
     * Lazily initializes nested subcommands. Called after AdminConfigService is ready.
     */
    private void initSubCommands() {
        if (equipmentSubCommand == null) {
            equipmentSubCommand = new EquipmentSubCommand(plugin);
        }
        if (spawnerSubCommand == null) {
            spawnerSubCommand = new SpawnerSubCommand(plugin);
        }
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showAdminHelp(sender);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "status" -> showStatus(sender);
            case "endrun" -> handleEndRun(sender, args);
            case "forcestart" -> handleForceStart(sender, args);
            case "kick" -> handleKick(sender, args);
            case "reset" -> handleReset(sender, args);
            case "setperma" -> handleSetPerma(sender, args);
            case "join" -> handleJoin(sender, args);
            case "world" -> handleWorld(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "equipment" -> {
                initSubCommands();
                equipmentSubCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "spawner" -> {
                initSubCommands();
                spawnerSubCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> i18n.send(sender, "error.unknown_command", "command", action);
        }
    }

    private void showAdminHelp(CommandSender sender) {
        i18n.send(sender, "admin.help.header");
        i18n.send(sender, "admin.help.status");
        i18n.send(sender, "admin.help.endrun");
        i18n.send(sender, "admin.help.forcestart");
        i18n.send(sender, "admin.help.kick");
        i18n.send(sender, "admin.help.reset");
        i18n.send(sender, "admin.help.setperma");
        i18n.send(sender, "admin.help.join");
        i18n.send(sender, "admin.help.world");
        i18n.send(sender, "admin.help.debug");
        i18n.send(sender, "admin.help.equipment");
        i18n.send(sender, "admin.help.spawner");
    }

    private void showStatus(CommandSender sender) {
        int playerCount = state.getAllPlayers().size();
        int teamCount = state.getActiveTeamCount();
        int runCount = state.getActiveRunCount();
        int inRunCount = state.getPlayersInRunCount();

        i18n.send(sender, "admin.status.header");
        i18n.send(sender, "admin.status.players", "count", playerCount);
        i18n.send(sender, "admin.status.teams", "count", teamCount);
        i18n.send(sender, "admin.status.runs", "count", runCount);
        i18n.send(sender, "admin.status.in_run", "count", inRunCount);
    }

    private void handleEndRun(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // End all runs or specify team
            if (state.getActiveRunCount() == 0) {
                i18n.send(sender, "admin.no_active_runs");
                return;
            }

            // End all runs
            for (RunState run : state.getAllRuns()) {
                if (run.isActive()) {
                    state.endRun(run.getRunId());
                }
            }
            i18n.send(sender, "admin.all_runs_ended");
            return;
        }

        String teamName = args[1];
        Optional<TeamState> teamOpt = state.findTeamByName(teamName);

        if (teamOpt.isEmpty()) {
            i18n.send(sender, "error.team_not_found", "team", teamName);
            return;
        }

        TeamState team = teamOpt.get();
        Optional<RunState> runOpt = state.getTeamRun(team.getTeamId());

        if (runOpt.isEmpty()) {
            i18n.send(sender, "admin.team_not_in_run", "team", teamName);
            return;
        }

        state.endRun(runOpt.get().getRunId());
        i18n.send(sender, "admin.run_ended", "team", teamName);
    }

    private void handleForceStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "admin.specify_team");
            return;
        }

        String teamName = args[1];
        Optional<TeamState> teamOpt = state.findTeamByName(teamName);

        if (teamOpt.isEmpty()) {
            i18n.send(sender, "error.team_not_found", "team", teamName);
            return;
        }

        TeamState team = teamOpt.get();

        if (state.getTeamRun(team.getTeamId()).isPresent()) {
            i18n.send(sender, "admin.team_already_in_run", "team", teamName);
            return;
        }

        // Force start the run via ReadyService
        readyService.forceStart(team);
        i18n.send(sender, "admin.force_start", "team", teamName);
        plugin.getLogger().info("Admin force-started run for team: " + teamName);
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "error.specify_player");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        Optional<PlayerState> playerState = state.getPlayer(target.getUniqueId());
        if (playerState.isEmpty()) {
            i18n.send(sender, "admin.player_not_in_game", "player", playerName);
            return;
        }

        // Remove from team
        state.removePlayerFromTeam(target.getUniqueId());

        // Reset player state
        playerState.get().resetAll();

        i18n.send(sender, "admin.player_kicked", "player", playerName);
        i18n.send(target, "admin.you_were_kicked");
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "error.specify_player");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        Optional<PlayerState> playerState = state.getPlayer(target.getUniqueId());
        if (playerState.isEmpty()) {
            i18n.send(sender, "admin.player_not_in_game", "player", playerName);
            return;
        }

        playerState.get().resetAll();
        i18n.send(sender, "admin.player_reset", "player", playerName);
    }

    private void handleSetPerma(CommandSender sender, String[] args) {
        if (args.length < 3) {
            i18n.send(sender, "admin.setperma_usage");
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
        playerState.setPermaScore(amount);

        // Update scoreboard
        scoreboardService.updatePermaScore(target, amount);

        i18n.send(sender, "admin.perma_set", "player", playerName, "amount", amount);
    }

    // ==================== Join Switch ====================

    private void handleJoin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Show current status
            boolean enabled = config.isJoinEnabled();
            String status = i18n.get(enabled ? "admin.join_status_enabled" : "admin.join_status_disabled");
            i18n.send(sender, "admin.join_status", "status", status);
            return;
        }

        String subAction = args[1].toLowerCase();
        switch (subAction) {
            case "enable" -> {
                config.setJoinEnabled(true);
                i18n.send(sender, "admin.join_enabled");
                plugin.getLogger().info("Admin enabled game join");
            }
            case "disable" -> {
                config.setJoinEnabled(false);
                i18n.send(sender, "admin.join_disabled", "count", 0, "seconds", 0);
                plugin.getLogger().info("Admin disabled game join");
            }
            default -> i18n.send(sender, "error.invalid_argument", "arg", subAction);
        }
    }

    // ==================== World Management ====================

    private void handleWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Show world list
            showWorldList(sender);
            return;
        }

        String subAction = args[1].toLowerCase();
        switch (subAction) {
            case "list" -> showWorldList(sender);
            case "enable" -> {
                if (args.length < 3) {
                    i18n.send(sender, "error.world_not_found", "world", "");
                    return;
                }
                String worldName = args[2];
                if (!worldService.getWorldConfig(worldName).isPresent()) {
                    i18n.send(sender, "admin.world_not_found", "world", worldName);
                    return;
                }
                worldService.enableWorld(worldName);
                i18n.send(sender, "admin.world_enabled", "world", worldName);
            }
            case "disable" -> {
                if (args.length < 3) {
                    i18n.send(sender, "error.world_not_found", "world", "");
                    return;
                }
                String worldName = args[2];
                if (!worldService.getWorldConfig(worldName).isPresent()) {
                    i18n.send(sender, "admin.world_not_found", "world", worldName);
                    return;
                }
                worldService.disableWorld(worldName);
                i18n.send(sender, "admin.world_disabled", "world", worldName);
            }
            default -> i18n.send(sender, "error.invalid_argument", "arg", subAction);
        }
    }

    private void showWorldList(CommandSender sender) {
        i18n.send(sender, "admin.world_list_header");
        for (var worldConfig : config.getCombatWorlds()) {
            boolean enabled = worldService.isWorldEnabled(worldConfig.name);
            String status = i18n.get(enabled ? "admin.world_status_enabled" : "admin.world_status_disabled");
            i18n.send(sender, "admin.world_list_item",
                    "name", worldConfig.name,
                    "status", status,
                    "weight", worldConfig.weight);
        }
    }

    // ==================== Debug Commands ====================

    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "admin.debug.header");
            return;
        }

        String subAction = args[1].toLowerCase();
        switch (subAction) {
            case "player" -> debugPlayer(sender, args);
            case "perf" -> debugPerf(sender);
            case "templates" -> debugTemplates(sender, args);
            case "run" -> debugRun(sender, args);
            default -> i18n.send(sender, "error.invalid_argument", "arg", subAction);
        }
    }

    private void debugPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            i18n.send(sender, "error.specify_player");
            return;
        }

        String playerName = args[2];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        Optional<PlayerState> playerStateOpt = state.getPlayer(target.getUniqueId());
        if (playerStateOpt.isEmpty()) {
            i18n.send(sender, "admin.debug.player_not_found");
            return;
        }

        PlayerState ps = playerStateOpt.get();
        i18n.send(sender, "debug.player_header", "player", playerName);
        sender.sendMessage("§7UUID: §f" + ps.getUuid());
        sender.sendMessage("§7Mode: §f" + ps.getMode());
        sender.sendMessage("§7Team: §f" + (ps.getTeamId() != null ? ps.getTeamId() : "none"));
        sender.sendMessage("§7Run: §f" + (ps.getRunId() != null ? ps.getRunId() : "none"));
        sender.sendMessage("§7Weapon: §f" + ps.getWeaponGroup() + " Lv." + ps.getWeaponLevel());
        sender.sendMessage("§7Helmet: §f" + ps.getHelmetGroup() + " Lv." + ps.getHelmetLevel());
        sender.sendMessage("§7XP: §f" + ps.getXpProgress() + "/" + ps.getXpRequired() + " (held: " + ps.getXpHeld() + ")");
        sender.sendMessage("§7Perma Score: §f" + ps.getPermaScore());
        sender.sendMessage("§7Ready: §f" + ps.isReady());
        sender.sendMessage("§7Cooldown: §f" + (ps.isOnCooldown() ? ps.getCooldownRemainingSeconds() + "s" : "none"));
        sender.sendMessage("§7Invulnerable: §f" + (ps.isInvulnerable() ? "yes" : "no"));
        sender.sendMessage("§7Starter Selections: §fweapon=" + ps.getStarterWeaponOptionId() + ", helmet=" + ps.getStarterHelmetOptionId());
    }

    private void debugPerf(CommandSender sender) {
        i18n.send(sender, "debug.perf_header");
        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);

        sender.sendMessage("§7Players tracked: §f" + state.getAllPlayers().size());
        sender.sendMessage("§7Teams: §f" + state.getActiveTeamCount());
        sender.sendMessage("§7Active runs: §f" + state.getActiveRunCount());
        sender.sendMessage("§7Memory: §f" + usedMem + "MB / " + maxMem + "MB");
        sender.sendMessage("§7TPS: §f" + String.format("%.1f", Bukkit.getTPS()[0]));
    }

    private void debugTemplates(CommandSender sender, String[] args) {
        if (args.length < 4) {
            i18n.send(sender, "admin.debug.template_usage");
            return;
        }

        String templateName = args[2];
        String playerName = args[3];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            i18n.send(sender, "error.player_not_found", "player", playerName);
            return;
        }

        // Build a test context
        Map<String, Object> context = templateEngine.context()
                .player(target)
                .location(target.getLocation())
                .set("level", 5)
                .set("archetype", "test_archetype")
                .build();

        // Expand the template name as if it were a template
        String result = templateEngine.expand(templateName, context);
        i18n.send(sender, "admin.debug.template_result", "result", result);
    }

    private void debugRun(CommandSender sender, String[] args) {
        if (args.length < 3) {
            // List all runs
            i18n.send(sender, "admin.debug.run_list_header");
            for (RunState run : state.getAllRuns()) {
                Optional<TeamState> teamOpt = state.getTeam(run.getTeamId());
                String teamName = teamOpt.map(TeamState::getName).orElse("unknown");
                i18n.send(sender, "admin.debug.run_list_entry",
                        "id", run.getRunId().toString().substring(0, 8),
                        "team", teamName,
                        "world", run.getWorldName(),
                        "status", run.getStatus().name());
            }
            return;
        }

        String runIdOrList = args[2];
        if (runIdOrList.equalsIgnoreCase("list")) {
            debugRun(sender, new String[]{"debug", "run"}); // recurse without arg
            return;
        }

        // Try to find run by partial ID
        RunState foundRun = null;
        for (RunState run : state.getAllRuns()) {
            if (run.getRunId().toString().startsWith(runIdOrList)) {
                foundRun = run;
                break;
            }
        }

        if (foundRun == null) {
            i18n.send(sender, "admin.debug.run_not_found");
            return;
        }

        sender.sendMessage("§7========== §6Run: " + foundRun.getRunId().toString().substring(0, 8) + " §7==========");
        sender.sendMessage("§7Status: §f" + foundRun.getStatus());
        sender.sendMessage("§7World: §f" + foundRun.getWorldName());
        sender.sendMessage("§7Team: §f" + foundRun.getTeamId());
        sender.sendMessage("§7Participants: §f" + foundRun.getParticipants().size());
        sender.sendMessage("§7Alive: §f" + foundRun.getAlivePlayers().size());
        sender.sendMessage("§7Elapsed: §f" + foundRun.getElapsedFormatted());
        sender.sendMessage("§7Total Kills: §f" + foundRun.getTotalKills());
        sender.sendMessage("§7Total XP: §f" + foundRun.getTotalXpCollected());
        sender.sendMessage("§7Total Coins: §f" + foundRun.getTotalCoinsCollected());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("status", "endrun", "forcestart", "kick", "reset", "setperma", "join", "world", "debug", "equipment", "spawner")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length >= 2) {
            String action = args[0].toLowerCase();
            // Delegate to nested subcommands for equipment and spawner
            if (action.equals("equipment")) {
                initSubCommands();
                return equipmentSubCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
            } else if (action.equals("spawner")) {
                initSubCommands();
                return spawnerSubCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }

        if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (List.of("kick", "reset", "setperma").contains(action)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
            } else if (List.of("endrun", "forcestart").contains(action)) {
                for (TeamState team : state.getAllTeams()) {
                    if (team.getName().toLowerCase().startsWith(partial)) {
                        completions.add(team.getName());
                    }
                }
            } else if (action.equals("join")) {
                for (String sub : List.of("enable", "disable")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            } else if (action.equals("world")) {
                for (String sub : List.of("enable", "disable", "list")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            } else if (action.equals("debug")) {
                for (String sub : List.of("player", "perf", "templates", "run")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            String subAction = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            if (action.equals("world") && List.of("enable", "disable").contains(subAction)) {
                for (var worldConfig : config.getCombatWorlds()) {
                    if (worldConfig.name.toLowerCase().startsWith(partial)) {
                        completions.add(worldConfig.name);
                    }
                }
            } else if (action.equals("debug") && subAction.equals("player")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
            } else if (action.equals("debug") && subAction.equals("run")) {
                completions.add("list");
                for (RunState run : state.getAllRuns()) {
                    String shortId = run.getRunId().toString().substring(0, 8);
                    if (shortId.startsWith(partial)) {
                        completions.add(shortId);
                    }
                }
            }
        } else if (args.length == 4) {
            String action = args[0].toLowerCase();
            String subAction = args[1].toLowerCase();
            String partial = args[3].toLowerCase();

            if (action.equals("debug") && subAction.equals("templates")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
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
}
