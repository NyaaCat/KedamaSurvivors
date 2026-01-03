package cat.nyaa.survivors.display;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages overhead display entities for players in combat.
 * Uses real TextDisplay entities for reliable visibility.
 *
 * Display format: "职业 | Lv.X"
 * - 职业 = helmet group display name
 * - X = player's run level
 *
 * Visibility:
 * - Only visible to other players (not self)
 * - Only shown during IN_RUN mode
 */
public class PlayerDisplayService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;

    // Map player UUID to their overhead TextDisplay entity
    private final Map<UUID, TextDisplay> playerDisplays = new ConcurrentHashMap<>();

    // Track last known data to avoid unnecessary updates
    private final Map<UUID, DisplayData> lastDisplayData = new ConcurrentHashMap<>();

    // Task
    private BukkitTask tickTask;

    public PlayerDisplayService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
    }

    /**
     * Initializes and starts the display service.
     */
    public void initialize() {
        startTicking();
        plugin.getLogger().info("PlayerDisplayService initialized");
    }

    /**
     * Starts the per-tick update task.
     */
    private void startTicking() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        // Run every tick on main thread
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, 1L, 1L);
    }

    /**
     * Called every tick to update display entities.
     */
    private void tickUpdate() {
        // Check if overhead display is enabled
        if (!config.isOverheadDisplayEnabled()) {
            // Clean up any existing displays if feature was disabled
            if (!playerDisplays.isEmpty()) {
                removeAllDisplays();
            }
            return;
        }

        // Collect current IN_RUN players
        Set<UUID> activePlayerIds = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
            if (stateOpt.isEmpty()) continue;

            PlayerState ps = stateOpt.get();
            if (ps.getMode() != PlayerMode.IN_RUN) continue;

            activePlayerIds.add(player.getUniqueId());

            // Get display data
            String className = getHelmetDisplayName(ps);
            int level = ps.getRunLevel();
            Location playerLoc = player.getLocation();

            DisplayData newData = new DisplayData(className, level);
            DisplayData oldData = lastDisplayData.get(player.getUniqueId());

            TextDisplay display = playerDisplays.get(player.getUniqueId());

            // Check if display needs to be recreated
            boolean needsRecreate = display == null || !display.isValid();

            // Also recreate if player changed worlds
            if (!needsRecreate && display != null && !display.getWorld().equals(player.getWorld())) {
                // Player switched worlds - remove old display and create new one
                display.remove();
                playerDisplays.remove(player.getUniqueId());
                needsRecreate = true;
            }

            if (needsRecreate) {
                // Create new display entity
                display = createDisplayEntity(player, className, level);
                if (display != null) {
                    playerDisplays.put(player.getUniqueId(), display);
                    lastDisplayData.put(player.getUniqueId(), newData);
                }
            } else {
                // Update position
                Location displayLoc = getDisplayLocation(playerLoc);
                display.teleport(displayLoc);

                // Update text if changed
                if (!newData.equals(oldData)) {
                    display.text(buildDisplayText(className, level));
                    lastDisplayData.put(player.getUniqueId(), newData);
                }
            }

            // Update visibility for all viewers
            if (display != null && display.isValid()) {
                updateVisibility(player, display);
            }
        }

        // Remove displays for players no longer in IN_RUN mode
        Set<UUID> toRemove = new HashSet<>(playerDisplays.keySet());
        toRemove.removeAll(activePlayerIds);

        for (UUID playerId : toRemove) {
            removeDisplay(playerId);
        }
    }

    /**
     * Creates a TextDisplay entity above a player.
     * Visible to everyone by default, hidden from owner only.
     */
    private TextDisplay createDisplayEntity(Player player, String className, int level) {
        Location spawnLoc = getDisplayLocation(player.getLocation());

        try {
            TextDisplay display = player.getWorld().spawn(spawnLoc, TextDisplay.class, entity -> {
                // Set text content
                entity.text(buildDisplayText(className, level));

                // Billboard mode - always face the viewer
                entity.setBillboard(Display.Billboard.CENTER);

                // Visual settings - transparent background with shadow
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setShadowed(true);
                entity.setSeeThrough(false);
                entity.setDefaultBackground(false);

                // View range - 1.0 means default (64 blocks * entityDistanceScaling)
                entity.setViewRange(1.0f);

                // Enable smooth teleport interpolation (1 tick = smooth following)
                entity.setTeleportDuration(1);

                // Make persistent = false so it doesn't save to world
                entity.setPersistent(false);

                // Add scoreboard tag for identification
                entity.addScoreboardTag("vrs_player_display");
                entity.addScoreboardTag("vrs_owner_" + player.getUniqueId());
            });

            // Hide from owner only - everyone else sees it by default
            player.hideEntity(plugin, display);

            plugin.getLogger().info("Created TextDisplay for player " + player.getName() + " at " + spawnLoc);
            return display;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create TextDisplay for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Builds the display text component.
     */
    private Component buildDisplayText(String className, int level) {
        return Component.text(className, NamedTextColor.GOLD)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("Lv." + level, NamedTextColor.GREEN));
    }

    /**
     * Calculates the location for the display entity above a player.
     */
    private Location getDisplayLocation(Location playerLoc) {
        return playerLoc.clone().add(0, config.getOverheadDisplayYOffset(), 0);
    }

    /**
     * Updates visibility of a display entity.
     * Just ensures owner cannot see their own display.
     */
    private void updateVisibility(Player owner, TextDisplay display) {
        // Ensure owner still cannot see their own display
        // (in case they reconnected or something)
        if (!owner.canSee(display)) {
            return; // Already hidden
        }
        owner.hideEntity(plugin, display);
    }

    /**
     * Gets the display name for a player's helmet group.
     */
    private String getHelmetDisplayName(PlayerState ps) {
        String helmetGroup = ps.getHelmetGroup();
        if (helmetGroup == null || helmetGroup.isEmpty()) {
            return "???";
        }

        // Look up display name from config
        var helmetGroups = config.getHelmetGroups();
        for (var group : helmetGroups.values()) {
            if (group.groupId.equals(helmetGroup)) {
                return group.displayName != null ? group.displayName : helmetGroup;
            }
        }

        return helmetGroup;
    }

    /**
     * Removes display entity for a specific player.
     */
    private void removeDisplay(UUID playerId) {
        TextDisplay display = playerDisplays.remove(playerId);
        lastDisplayData.remove(playerId);

        if (display != null) {
            if (display.isValid()) {
                display.remove();
                plugin.getLogger().info("Removed TextDisplay for player " + playerId);
            }
        }
    }

    /**
     * Removes all display entities.
     */
    private void removeAllDisplays() {
        for (TextDisplay display : playerDisplays.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        playerDisplays.clear();
        lastDisplayData.clear();
    }

    /**
     * Called when a player joins - clean up any stale state.
     */
    public void handlePlayerJoin(Player player) {
        // Nothing special needed - they'll be added on next tick if in IN_RUN mode
    }

    /**
     * Called when a player quits - clean up their display entities.
     */
    public void handlePlayerQuit(Player player) {
        removeDisplay(player.getUniqueId());
    }

    /**
     * Called when a player's mode changes - update their display visibility.
     */
    public void handleModeChange(Player player, PlayerMode oldMode, PlayerMode newMode) {
        if (newMode != PlayerMode.IN_RUN && oldMode == PlayerMode.IN_RUN) {
            // Player left IN_RUN mode - remove their display
            removeDisplay(player.getUniqueId());
        }
        // If entering IN_RUN, they'll be picked up on the next tick
    }

    /**
     * Shuts down the display service.
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        removeAllDisplays();

        // Also clean up any orphaned display entities in all worlds
        for (var world : Bukkit.getWorlds()) {
            for (var entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains("vrs_player_display")) {
                    entity.remove();
                }
            }
        }
    }

    // ==================== Data Classes ====================

    private record DisplayData(String className, int level) {}
}
