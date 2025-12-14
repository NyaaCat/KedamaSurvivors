package cat.nyaa.survivors.scoreboard;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Manages scoreboard display for players in runs.
 * Shows sidebar with player stats and manages perma-score objective.
 */
public class ScoreboardService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;

    private final ScoreboardManager scoreboardManager;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    private int taskId = -1;

    public ScoreboardService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
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

        // Clear all player scoreboards
        for (UUID playerId : playerScoreboards.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.setScoreboard(scoreboardManager.getMainScoreboard());
            }
        }
        playerScoreboards.clear();
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

        if (board != null) {
            player.setScoreboard(scoreboardManager.getMainScoreboard());
        }
    }

    /**
     * Updates all player scoreboards.
     */
    private void updateAllScoreboards() {
        for (UUID playerId : new HashSet<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                updatePlayerSidebar(player);
            } else {
                playerScoreboards.remove(playerId);
            }
        }
    }

    /**
     * Updates the sidebar for a specific player.
     * Content varies based on player mode (lobby vs in-run).
     */
    public void updatePlayerSidebar(Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard board = playerScoreboards.get(playerId);
        if (board == null) return;

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        Optional<TeamState> teamOpt = state.getPlayerTeam(playerId);
        Optional<RunState> runOpt = state.getPlayerRun(playerId);

        Objective sidebar = board.getObjective("vrs_sidebar");
        if (sidebar == null) return;

        // Clear old entries
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        // Build sidebar lines (scores are used for ordering, higher = top)
        int score = 15;

        boolean inRun = runOpt.isPresent() && runOpt.get().isActive();

        if (inRun) {
            // =============== IN-RUN SCOREBOARD ===============

            // Player level (run progression level)
            String levelLine = i18n.get("scoreboard.player_level", "level", playerState.getRunLevel());
            sidebar.getScore(levelLine).setScore(score--);

            // Weapon level
            String weaponLine = i18n.get("scoreboard.weapon_level", "level", playerState.getWeaponLevel());
            sidebar.getScore(weaponLine).setScore(score--);

            // Helmet level
            String helmetLine = i18n.get("scoreboard.helmet_level", "level", playerState.getHelmetLevel());
            sidebar.getScore(helmetLine).setScore(score--);

            // Empty line
            sidebar.getScore(" ").setScore(score--);

            // XP bar
            String xpBar = buildXpBar(playerState);
            String xpLine = i18n.get("scoreboard.xp", "bar", xpBar, "percent", getXpPercent(playerState));
            sidebar.getScore(xpLine).setScore(score--);

            // Upgrade countdown (only when pending and not both at max)
            if (playerState.isUpgradePending() &&
                    !(playerState.isWeaponAtMax() && playerState.isHelmetAtMax())) {
                int remainingSeconds = playerState.getUpgradeRemainingSeconds();
                if (remainingSeconds > 0) {
                    String upgradeLine = i18n.get("scoreboard.upgrade_countdown", "seconds", remainingSeconds);
                    sidebar.getScore(upgradeLine).setScore(score--);
                }
            }

            // Coins earned this run
            RunState run = runOpt.get();
            String coinsLine = i18n.get("scoreboard.coins", "amount", run.getTotalCoinsCollected());
            sidebar.getScore(coinsLine).setScore(score--);

            // Empty line
            sidebar.getScore("  ").setScore(score--);

            // Perma score
            String permaLine = i18n.get("scoreboard.perma_score", "amount", formatNumber(playerState.getPermaScore()));
            sidebar.getScore(permaLine).setScore(score--);

            // Team info
            if (teamOpt.isPresent()) {
                TeamState team = teamOpt.get();
                String teamLine = i18n.get("scoreboard.team",
                        "name", team.getName(),
                        "count", team.getMemberCount(),
                        "max", config.getMaxTeamSize());
                sidebar.getScore(teamLine).setScore(score--);
            }

            // Run time
            String timeLine = i18n.get("scoreboard.time", "time", run.getElapsedFormatted());
            sidebar.getScore(timeLine).setScore(score--);

        } else {
            // =============== LOBBY SCOREBOARD ===============

            // Perma score
            String permaLine = i18n.get("scoreboard.perma_score", "amount", formatNumber(playerState.getPermaScore()));
            sidebar.getScore(permaLine).setScore(score--);

            // Empty line
            sidebar.getScore(" ").setScore(score--);

            // Team info
            if (teamOpt.isPresent()) {
                TeamState team = teamOpt.get();
                String teamLine = i18n.get("scoreboard.team",
                        "name", team.getName(),
                        "count", team.getMemberCount(),
                        "max", config.getMaxTeamSize());
                sidebar.getScore(teamLine).setScore(score--);
            } else {
                // No team - show hint
                String noTeamLine = i18n.get("scoreboard.no_team");
                sidebar.getScore(noTeamLine).setScore(score--);
            }

            // Empty line
            sidebar.getScore("  ").setScore(score--);

            // Player mode/status
            String statusKey = switch (playerState.getMode()) {
                case LOBBY -> "scoreboard.status_lobby";
                case READY -> "scoreboard.status_ready";
                case COUNTDOWN -> "scoreboard.status_countdown";
                case COOLDOWN -> "scoreboard.status_cooldown";
                default -> "scoreboard.status_lobby";
            };
            String statusLine = i18n.get(statusKey);
            sidebar.getScore(statusLine).setScore(score--);

            // Selected starters (if any)
            if (playerState.getStarterWeaponOptionId() != null) {
                String weaponSelected = i18n.get("scoreboard.starter_weapon_selected");
                sidebar.getScore(weaponSelected).setScore(score--);
            }
            if (playerState.getStarterHelmetOptionId() != null) {
                String helmetSelected = i18n.get("scoreboard.starter_helmet_selected");
                sidebar.getScore(helmetSelected).setScore(score--);
            }
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
