package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

/**
 * Service that validates player inventories during runs.
 * Uses round-robin checking to spread load across ticks.
 * Removes VRS equipment that doesn't match the player's current equipment level.
 */
public class InventoryValidationService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final StarterService starterService;

    // Round-robin index for player checking
    private int currentPlayerIndex = 0;

    // Task ID for the validation loop
    private int taskId = -1;

    public InventoryValidationService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.starterService = plugin.getStarterService();
    }

    /**
     * Starts the inventory validation loop.
     * Runs every tick, checking one player per tick.
     */
    public void start() {
        if (taskId != -1) {
            return; // Already running
        }

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::validateNextPlayer, 20, 1).getTaskId();

        if (config.isVerbose()) {
            plugin.getLogger().info("Inventory validation service started");
        }
    }

    /**
     * Stops the inventory validation loop.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        if (config.isVerbose()) {
            plugin.getLogger().info("Inventory validation service stopped");
        }
    }

    /**
     * Validates the next player in the round-robin queue.
     */
    private void validateNextPlayer() {
        // Get all players currently in a run
        List<UUID> inRunPlayers = getInRunPlayers();

        if (inRunPlayers.isEmpty()) {
            currentPlayerIndex = 0;
            return;
        }

        // Wrap around if index is out of bounds
        if (currentPlayerIndex >= inRunPlayers.size()) {
            currentPlayerIndex = 0;
        }

        UUID playerId = inRunPlayers.get(currentPlayerIndex);
        currentPlayerIndex++;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        Optional<PlayerState> playerStateOpt = state.getPlayer(playerId);
        if (playerStateOpt.isEmpty()) {
            return;
        }

        PlayerState playerState = playerStateOpt.get();
        validatePlayerInventory(player, playerState);
    }

    /**
     * Gets a list of all player UUIDs currently in a run.
     */
    private List<UUID> getInRunPlayers() {
        List<UUID> result = new ArrayList<>();

        for (PlayerState playerState : state.getAllPlayers()) {
            if (playerState.getMode() == PlayerMode.IN_RUN) {
                result.add(playerState.getUuid());
            }
        }

        return result;
    }

    /**
     * Validates a player's inventory and removes invalid VRS equipment.
     * Invalid equipment includes:
     * - VRS weapons with level different from player's current weapon level
     * - VRS helmets with level different from player's current helmet level
     * - VRS weapons with group different from player's current weapon group
     * - VRS helmets with group different from player's current helmet group
     */
    private void validatePlayerInventory(Player player, PlayerState playerState) {
        PlayerInventory inv = player.getInventory();
        int removedCount = 0;

        String expectedWeaponGroup = playerState.getWeaponGroup();
        int expectedWeaponLevel = playerState.getWeaponLevel();
        String expectedHelmetGroup = playerState.getHelmetGroup();
        int expectedHelmetLevel = playerState.getHelmetLevel();

        // Check all inventory slots (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (!starterService.isVrsItem(item)) {
                continue;
            }

            String itemType = starterService.getEquipmentType(item);
            String itemGroup = starterService.getEquipmentGroup(item);
            int itemLevel = starterService.getEquipmentLevel(item);

            boolean shouldRemove = false;

            if ("weapon".equals(itemType)) {
                // Check if weapon matches expected group and level
                if (!Objects.equals(itemGroup, expectedWeaponGroup) || itemLevel != expectedWeaponLevel) {
                    shouldRemove = true;
                }
            } else if ("helmet".equals(itemType)) {
                // Check if helmet matches expected group and level
                if (!Objects.equals(itemGroup, expectedHelmetGroup) || itemLevel != expectedHelmetLevel) {
                    shouldRemove = true;
                }
            }

            if (shouldRemove) {
                inv.setItem(i, null);
                removedCount++;

                if (config.isVerbose()) {
                    plugin.getLogger().info("[InventoryValidation] Removed invalid " + itemType +
                            " from " + player.getName() +
                            ": group=" + itemGroup + ", level=" + itemLevel +
                            " (expected: group=" + (itemType.equals("weapon") ? expectedWeaponGroup : expectedHelmetGroup) +
                            ", level=" + (itemType.equals("weapon") ? expectedWeaponLevel : expectedHelmetLevel) + ")");
                }
            }
        }

        // Check armor slots specifically
        ItemStack helmet = inv.getHelmet();
        if (helmet != null && starterService.isVrsItem(helmet)) {
            String itemGroup = starterService.getEquipmentGroup(helmet);
            int itemLevel = starterService.getEquipmentLevel(helmet);

            if (!Objects.equals(itemGroup, expectedHelmetGroup) || itemLevel != expectedHelmetLevel) {
                inv.setHelmet(null);
                removedCount++;

                if (config.isVerbose()) {
                    plugin.getLogger().info("[InventoryValidation] Removed invalid helmet from armor slot of " +
                            player.getName() +
                            ": group=" + itemGroup + ", level=" + itemLevel +
                            " (expected: group=" + expectedHelmetGroup + ", level=" + expectedHelmetLevel + ")");
                }
            }
        }

        // Log summary if items were removed (always log this, not just verbose)
        if (removedCount > 0) {
            plugin.getLogger().warning("[InventoryValidation] Removed " + removedCount +
                    " invalid VRS item(s) from " + player.getName());
        }
    }
}
