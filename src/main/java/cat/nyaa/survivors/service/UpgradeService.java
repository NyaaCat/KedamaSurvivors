package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.EquipmentGroupConfig;
import cat.nyaa.survivors.gui.UpgradeGui;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles equipment upgrade logic and GUI.
 */
public class UpgradeService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;
    private final StarterService starter;

    public UpgradeService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
        this.starter = plugin.getStarterService();
    }

    /**
     * Shows the upgrade selection GUI to a player.
     */
    public void showUpgradeGui(Player player) {
        Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
        if (stateOpt.isEmpty()) return;

        PlayerState playerState = stateOpt.get();

        // Create and open the upgrade GUI
        UpgradeGui gui = new UpgradeGui(plugin, player, playerState);
        gui.open();
    }

    /**
     * Processes a weapon upgrade choice.
     */
    public void processWeaponUpgrade(Player player, PlayerState playerState) {
        String group = playerState.getWeaponGroup();
        int currentLevel = playerState.getWeaponLevel();

        EquipmentGroupConfig groupConfig = config.getWeaponGroups().get(group);
        if (groupConfig == null) {
            i18n.send(player, "error.invalid_group");
            return;
        }

        if (!groupConfig.hasNextLevel(currentLevel)) {
            // At max level - grant perma-score reward
            handleMaxLevelUpgrade(player, playerState, "weapon");
            return;
        }

        int nextLevel = currentLevel + 1;
        List<String> templates = groupConfig.getItemsAtLevel(nextLevel);
        if (templates.isEmpty()) {
            i18n.send(player, "error.no_items_at_level");
            return;
        }

        // Select random item from level pool
        String templateId = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));

        // Grant the new weapon
        ItemStack newWeapon = starter.grantUpgradeItem(player, templateId, "weapon", group, nextLevel);
        if (newWeapon != null) {
            playerState.setWeaponLevel(nextLevel);

            // Check if now at max level
            if (!groupConfig.hasNextLevel(nextLevel)) {
                playerState.setWeaponAtMax(true);
            }

            i18n.send(player, "upgrade.weapon_upgraded", "level", nextLevel);
        }

        // Resolve held XP
        plugin.getRewardService().resolveHeldXp(player, playerState);

        // Close the GUI
        player.closeInventory();
    }

    /**
     * Processes a helmet upgrade choice.
     */
    public void processHelmetUpgrade(Player player, PlayerState playerState) {
        String group = playerState.getHelmetGroup();
        int currentLevel = playerState.getHelmetLevel();

        EquipmentGroupConfig groupConfig = config.getHelmetGroups().get(group);
        if (groupConfig == null) {
            i18n.send(player, "error.invalid_group");
            return;
        }

        if (!groupConfig.hasNextLevel(currentLevel)) {
            // At max level - grant perma-score reward
            handleMaxLevelUpgrade(player, playerState, "helmet");
            return;
        }

        int nextLevel = currentLevel + 1;
        List<String> templates = groupConfig.getItemsAtLevel(nextLevel);
        if (templates.isEmpty()) {
            i18n.send(player, "error.no_items_at_level");
            return;
        }

        // Select random item from level pool
        String templateId = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));

        // Grant the new helmet
        ItemStack newHelmet = starter.grantUpgradeItem(player, templateId, "helmet", group, nextLevel);
        if (newHelmet != null) {
            playerState.setHelmetLevel(nextLevel);

            // Check if now at max level
            if (!groupConfig.hasNextLevel(nextLevel)) {
                playerState.setHelmetAtMax(true);
            }

            i18n.send(player, "upgrade.helmet_upgraded", "level", nextLevel);
        }

        // Resolve held XP
        plugin.getRewardService().resolveHeldXp(player, playerState);

        // Close the GUI
        player.closeInventory();
    }

    /**
     * Handles upgrade choice when already at max level.
     */
    private void handleMaxLevelUpgrade(Player player, PlayerState playerState, String slot) {
        String mode = config.getMaxLevelBehaviorMode();
        int reward = config.getMaxLevelPermaScoreReward();

        if ("GRANT_PERMA_SCORE".equals(mode)) {
            playerState.setPermaScore(playerState.getPermaScore() + reward);
            plugin.getScoreboardService().updatePermaScore(player, playerState.getPermaScore());
            i18n.send(player, "upgrade.max_level_reward", "amount", reward, "slot", slot);
        } else if ("NOTHING".equals(mode)) {
            i18n.send(player, "upgrade.already_max_level", "slot", slot);
        }

        // Still resolve held XP
        plugin.getRewardService().resolveHeldXp(player, playerState);
        player.closeInventory();
    }

    /**
     * Checks if a player can upgrade their weapon.
     */
    public boolean canUpgradeWeapon(PlayerState playerState) {
        if (playerState.isWeaponAtMax()) {
            // Can still "upgrade" for perma-score if configured
            return "GRANT_PERMA_SCORE".equals(config.getMaxLevelBehaviorMode());
        }

        String group = playerState.getWeaponGroup();
        EquipmentGroupConfig groupConfig = config.getWeaponGroups().get(group);
        return groupConfig != null && groupConfig.hasNextLevel(playerState.getWeaponLevel());
    }

    /**
     * Checks if a player can upgrade their helmet.
     */
    public boolean canUpgradeHelmet(PlayerState playerState) {
        if (playerState.isHelmetAtMax()) {
            // Can still "upgrade" for perma-score if configured
            return "GRANT_PERMA_SCORE".equals(config.getMaxLevelBehaviorMode());
        }

        String group = playerState.getHelmetGroup();
        EquipmentGroupConfig groupConfig = config.getHelmetGroups().get(group);
        return groupConfig != null && groupConfig.hasNextLevel(playerState.getHelmetLevel());
    }

    /**
     * Gets the display name for the next weapon level.
     */
    public String getNextWeaponInfo(PlayerState playerState) {
        if (playerState.isWeaponAtMax()) {
            return i18n.get("upgrade.max_level");
        }

        String group = playerState.getWeaponGroup();
        int nextLevel = playerState.getWeaponLevel() + 1;

        EquipmentGroupConfig groupConfig = config.getWeaponGroups().get(group);
        if (groupConfig != null) {
            return groupConfig.displayName + " Lv." + nextLevel;
        }
        return "Lv." + nextLevel;
    }

    /**
     * Gets the display name for the next helmet level.
     */
    public String getNextHelmetInfo(PlayerState playerState) {
        if (playerState.isHelmetAtMax()) {
            return i18n.get("upgrade.max_level");
        }

        String group = playerState.getHelmetGroup();
        int nextLevel = playerState.getHelmetLevel() + 1;

        EquipmentGroupConfig groupConfig = config.getHelmetGroups().get(group);
        if (groupConfig != null) {
            return groupConfig.displayName + " Lv." + nextLevel;
        }
        return "Lv." + nextLevel;
    }
}
