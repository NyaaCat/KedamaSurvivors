package cat.nyaa.survivors.scoreboard;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.economy.EconomyService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages scoreboard display for players in runs.
 * Shows sidebar with player stats and manages perma-score objective.
 */
public class ScoreboardService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;
    private final EconomyService economy;

    private final ScoreboardManager scoreboardManager;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    // Dirty-flag tracking for incremental updates
    private final Map<UUID, Map<Integer, String>> previousLines = new ConcurrentHashMap<>();

    // Cached player balances (updated on main thread before async scoreboard build)
    private final Map<UUID, Integer> cachedBalances = new ConcurrentHashMap<>();

    // Async executor for building scoreboard lines
    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VRS-ScoreboardBuilder");
        t.setDaemon(true);
        return t;
    });

    private int taskId = -1;

    public ScoreboardService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.economy = plugin.getEconomyService();
        this.scoreboardManager = Bukkit.getScoreboardManager();
    }

    /**
     * Starts the scoreboard update task.
     */
    public void start() {
        if (!config.isScoreboardEnabled()) return;

        // Register perma-score objective on main scoreboard
        registerPermaScoreObjective();

        // Start update task
        int interval = config.getScoreboardUpdateInterval();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllScoreboards, 20L, interval).getTaskId();

        plugin.getLogger().info("ScoreboardService started with update interval: " + interval + " ticks");
    }

    /**
     * Stops the scoreboard update task.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Shutdown async executor
        asyncExecutor.shutdown();

        // Clear all player scoreboards
        for (UUID playerId : playerScoreboards.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.setScoreboard(scoreboardManager.getMainScoreboard());
            }
        }
        playerScoreboards.clear();
        previousLines.clear();
        cachedBalances.clear();
    }

    /**
     * Registers the perma-score objective on the main scoreboard.
     */
    private void registerPermaScoreObjective() {
        Scoreboard main = scoreboardManager.getMainScoreboard();
        String objName = config.getPermaScoreObjectiveName();

        Objective obj = main.getObjective(objName);
        if (obj == null) {
            obj = main.registerNewObjective(objName, Criteria.DUMMY, config.getPermaScoreDisplayName());
        }
    }

    /**
     * Updates the perma-score for a player.
     */
    public void updatePermaScore(Player player, int score) {
        Scoreboard main = scoreboardManager.getMainScoreboard();
        String objName = config.getPermaScoreObjectiveName();

        Objective obj = main.getObjective(objName);
        if (obj != null) {
            obj.getScore(player.getName()).setScore(score);
        }
    }

    /**
     * Gets the perma-score for a player.
     */
    public int getPermaScore(Player player) {
        Scoreboard main = scoreboardManager.getMainScoreboard();
        String objName = config.getPermaScoreObjectiveName();

        Objective obj = main.getObjective(objName);
        if (obj != null) {
            return obj.getScore(player.getName()).getScore();
        }
        return 0;
    }

    /**
     * Sets up the sidebar scoreboard for a player in a run.
     */
    public void setupSidebar(Player player) {
        if (!config.isScoreboardEnabled()) return;

        UUID playerId = player.getUniqueId();

        // Create new scoreboard for this player
        Scoreboard board = scoreboardManager.getNewScoreboard();

        // Create sidebar objective
        Objective sidebar = board.registerNewObjective("vrs_sidebar", Criteria.DUMMY,
                i18n.getComponent("scoreboard.title"));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        playerScoreboards.put(playerId, board);
        player.setScoreboard(board);

        // Initial update
        updatePlayerSidebar(player);
    }

    /**
     * Removes the sidebar scoreboard for a player.
     */
    public void removeSidebar(Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard board = playerScoreboards.remove(playerId);
        previousLines.remove(playerId);
        cachedBalances.remove(playerId);

        if (board != null) {
            player.setScoreboard(scoreboardManager.getMainScoreboard());
        }
    }

    /**
     * Updates all player scoreboards asynchronously.
     * Builds scoreboard lines on async thread, applies changes on main thread.
     */
    private void updateAllScoreboards() {
        Set<UUID> playerIds = new HashSet<>(playerScoreboards.keySet());

        // Cache balances on main thread (EconomyService.getBalance() may access Bukkit API)
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                cachedBalances.put(playerId, economy.getBalance(player));
            }
        }

        asyncExecutor.submit(() -> {
            Map<UUID, Map<Integer, String>> newLinesMap = new HashMap<>();

            for (UUID playerId : playerIds) {
                Map<Integer, String> lines = buildSidebarLines(playerId);
                if (lines != null) {
                    newLinesMap.put(playerId, lines);
                }
            }

            // Apply changes on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Map.Entry<UUID, Map<Integer, String>> entry : newLinesMap.entrySet()) {
                    UUID playerId = entry.getKey();
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        applyLineChanges(playerId, entry.getValue());
                    } else {
                        playerScoreboards.remove(playerId);
                        previousLines.remove(playerId);
                    }
                }
            });
        });
    }

    /**
     * Builds sidebar lines for a player (can run on async thread).
     * Returns map of score -> line text.
     */
    private Map<Integer, String> buildSidebarLines(UUID playerId) {
        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return null;

        PlayerState playerState = playerStateOpt.get();
        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        Optional<RunState> runOpt = state.getPlayerRun(playerId);

        Map<Integer, String> lines = new LinkedHashMap<>();
        int score = 15;

        boolean inRun = runOpt.isPresent() && runOpt.get().isActive();

        if (inRun) {
            // =============== IN-RUN SCOREBOARD ===============
            RunState run = runOpt.get();

            // Player level (run progression level)
            lines.put(score--, i18n.get("scoreboard.player_level", "level", playerState.getRunLevel()));

            // Weapon level
            lines.put(score--, i18n.get("scoreboard.weapon_level", "level", playerState.getWeaponLevel()));

            // Helmet level
            lines.put(score--, i18n.get("scoreboard.helmet_level", "level", playerState.getHelmetLevel()));

            // Empty line
            lines.put(score--, " ");

            // XP bar
            String xpBar = buildXpBar(playerState);
            lines.put(score--, i18n.get("scoreboard.xp", "bar", xpBar, "percent", getXpPercent(playerState)));

            // Upgrade countdown (only when pending and not both at max)
            if (playerState.isUpgradePending() &&
                    !(playerState.isWeaponAtMax() && playerState.isHelmetAtMax())) {
                int remainingSeconds = playerState.getUpgradeRemainingSeconds();
                if (remainingSeconds > 0) {
                    lines.put(score--, i18n.get("scoreboard.upgrade_countdown", "seconds", remainingSeconds));
                }
            }

            // Coins: Total (+current run)
            int totalBalance = cachedBalances.getOrDefault(playerId, 0);
            int runCoins = run.getTotalCoinsCollected();
            lines.put(score--, i18n.get("scoreboard.coins", "total", totalBalance, "run", runCoins));

            // Empty line
            lines.put(score--, "  ");

            // Perma score
            lines.put(score--, i18n.get("scoreboard.perma_score", "amount", formatNumber(playerState.getPermaScore())));

            // Team info
            if (teamOpt.isPresent()) {
                TeamState team = teamOpt.get();
                lines.put(score--, i18n.get("scoreboard.team",
                        "name", team.getName(),
                        "count", team.getMemberCount(),
                        "max", config.getMaxTeamSize()));
            }

            // Run time
            lines.put(score--, i18n.get("scoreboard.time", "time", run.getElapsedFormatted()));

        } else {
            // =============== LOBBY SCOREBOARD ===============

            // Coins: Total only (no run in lobby)
            int totalBalance = cachedBalances.getOrDefault(playerId, 0);
            lines.put(score--, i18n.get("scoreboard.coins_lobby", "total", totalBalance));

            // Perma score
            lines.put(score--, i18n.get("scoreboard.perma_score", "amount", formatNumber(playerState.getPermaScore())));

            // Empty line
            lines.put(score--, " ");

            // Team info
            if (teamOpt.isPresent()) {
                TeamState team = teamOpt.get();
                lines.put(score--, i18n.get("scoreboard.team",
                        "name", team.getName(),
                        "count", team.getMemberCount(),
                        "max", config.getMaxTeamSize()));
            } else {
                // No team - show hint
                lines.put(score--, i18n.get("scoreboard.no_team"));
            }

            // Empty line
            lines.put(score--, "  ");

            // Player mode/status
            String statusKey = switch (playerState.getMode()) {
                case LOBBY -> "scoreboard.status_lobby";
                case READY -> "scoreboard.status_ready";
                case COUNTDOWN -> "scoreboard.status_countdown";
                case COOLDOWN -> "scoreboard.status_cooldown";
                default -> "scoreboard.status_lobby";
            };
            lines.put(score--, i18n.get(statusKey));

            // Selected starters (if any)
            if (playerState.getStarterWeaponOptionId() != null) {
                lines.put(score--, i18n.get("scoreboard.starter_weapon_selected"));
            }
            if (playerState.getStarterHelmetOptionId() != null) {
                lines.put(score--, i18n.get("scoreboard.starter_helmet_selected"));
            }
        }

        return lines;
    }

    /**
     * Applies line changes to a player's scoreboard (must run on main thread).
     * Only updates lines that have changed for efficiency.
     */
    private void applyLineChanges(UUID playerId, Map<Integer, String> newLines) {
        Scoreboard board = playerScoreboards.get(playerId);
        if (board == null) return;

        Objective sidebar = board.getObjective("vrs_sidebar");
        if (sidebar == null) return;

        Map<Integer, String> oldLines = previousLines.getOrDefault(playerId, Map.of());

        // Remove lines that changed or were removed
        for (Map.Entry<Integer, String> old : oldLines.entrySet()) {
            String newLine = newLines.get(old.getKey());
            if (!old.getValue().equals(newLine)) {
                board.resetScores(old.getValue());
            }
        }

        // Add/update changed lines
        for (Map.Entry<Integer, String> entry : newLines.entrySet()) {
            String oldLine = oldLines.get(entry.getKey());
            if (!entry.getValue().equals(oldLine)) {
                sidebar.getScore(entry.getValue()).setScore(entry.getKey());
            }
        }

        previousLines.put(playerId, newLines);
    }

    /**
     * Updates the sidebar for a specific player (synchronous, for immediate updates).
     * Content varies based on player mode (lobby vs in-run).
     */
    public void updatePlayerSidebar(Player player) {
        UUID playerId = player.getUniqueId();
        Map<Integer, String> lines = buildSidebarLines(playerId);
        if (lines != null) {
            applyLineChanges(playerId, lines);
        }
    }

    private String buildXpBar(PlayerState playerState) {
        int progress = playerState.getXpProgress();
        int required = playerState.getXpRequired();

        if (required <= 0) required = 100;

        int filled = (progress * 5) / required;
        filled = Math.min(5, Math.max(0, filled));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            bar.append(i < filled ? "\u25B0" : "\u25B1"); // ▰ filled, ▱ empty
        }
        return bar.toString();
    }

    private int getXpPercent(PlayerState playerState) {
        int progress = playerState.getXpProgress();
        int required = playerState.getXpRequired();

        if (required <= 0) return 0;
        return Math.min(100, (progress * 100) / required);
    }

    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }

    /**
     * Gets the player's custom scoreboard, or null if not set up.
     */
    public Scoreboard getPlayerScoreboard(UUID playerId) {
        return playerScoreboards.get(playerId);
    }

    /**
     * Checks if a player has a sidebar set up.
     */
    public boolean hasSidebar(UUID playerId) {
        return playerScoreboards.containsKey(playerId);
    }
}
