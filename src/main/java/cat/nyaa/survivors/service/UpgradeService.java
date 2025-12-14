package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.EquipmentGroupConfig;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles equipment upgrade logic and chat-based selection.
 */
public class UpgradeService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .build();

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

    // ==================== Chat-based Upgrade Selection ====================

    /**
     * Processes an upgrade choice from command or auto-selection.
     *
     * @param player The player making the choice
     * @param choice "power" for weapon upgrade, "defense" for helmet upgrade
     */
    public void processUpgradeChoice(Player player, String choice) {
        Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
        if (stateOpt.isEmpty()) return;

        PlayerState playerState = stateOpt.get();

        if (!playerState.isUpgradePending()) {
            i18n.send(player, "upgrade.not_pending");
            return;
        }

        // Clear deadline and suggested upgrade
        playerState.setUpgradeDeadlineMillis(0);
        playerState.setSuggestedUpgrade(null);

        if ("power".equals(choice)) {
            processWeaponUpgrade(player, playerState);
        } else if ("defense".equals(choice)) {
            processHelmetUpgrade(player, playerState);
        }

        // Update scoreboard to remove countdown
        plugin.getScoreboardService().updatePlayerSidebar(player);
    }

    /**
     * Processes auto-selection when timeout expires.
     * Uses the pre-selected suggested upgrade.
     */
    public void processAutoUpgrade(Player player, PlayerState playerState) {
        String suggested = playerState.getSuggestedUpgrade();
        if (suggested == null) {
            // Fallback: random selection
            suggested = ThreadLocalRandom.current().nextBoolean() ? "power" : "defense";
        }

        // Notify player
        if ("power".equals(suggested)) {
            if (playerState.isWeaponAtMax()) {
                i18n.send(player, "upgrade.auto_selected_power_max",
                        "amount", config.getMaxLevelPermaScoreReward(),
                        "perma_name", config.getPermaScoreDisplayName());
            } else {
                i18n.send(player, "upgrade.auto_selected_power");
            }
        } else {
            if (playerState.isHelmetAtMax()) {
                i18n.send(player, "upgrade.auto_selected_defense_max",
                        "amount", config.getMaxLevelPermaScoreReward(),
                        "perma_name", config.getPermaScoreDisplayName());
            } else {
                i18n.send(player, "upgrade.auto_selected_defense");
            }
        }

        processUpgradeChoice(player, suggested);
    }

    /**
     * Sends the upgrade prompt messages to a player.
     * Called when upgrade becomes available and on reminders.
     *
     * @param player      The player to send prompts to
     * @param playerState The player's state
     * @param isReminder  True if this is a reminder, false if initial prompt
     */
    public void sendUpgradePrompt(Player player, PlayerState playerState, boolean isReminder) {
        String suggested = playerState.getSuggestedUpgrade();
        boolean weaponMax = playerState.isWeaponAtMax();
        boolean helmetMax = playerState.isHelmetAtMax();
        int permaReward = config.getMaxLevelPermaScoreReward();
        String permaName = config.getPermaScoreDisplayName();

        // Send header (only on initial prompt)
        if (!isReminder) {
            i18n.send(player, "upgrade.prompt_header");
        } else {
            // Send reminder with countdown
            int seconds = playerState.getUpgradeRemainingSeconds();
            i18n.send(player, "upgrade.reminder", "seconds", seconds);
        }

        // Determine message keys based on suggested and max level states
        String powerKey;
        String defenseKey;

        if (weaponMax) {
            powerKey = "power".equals(suggested) ? "upgrade.prompt_power_max_highlight" : "upgrade.prompt_power_max";
        } else {
            powerKey = "power".equals(suggested) ? "upgrade.prompt_power_highlight" : "upgrade.prompt_power";
        }

        if (helmetMax) {
            defenseKey = "defense".equals(suggested) ? "upgrade.prompt_defense_max_highlight" : "upgrade.prompt_defense_max";
        } else {
            defenseKey = "defense".equals(suggested) ? "upgrade.prompt_defense_highlight" : "upgrade.prompt_defense";
        }

        // Build and send clickable messages
        String powerText = weaponMax
                ? i18n.get(powerKey, "amount", permaReward, "perma_name", permaName)
                : i18n.get(powerKey);
        String defenseText = helmetMax
                ? i18n.get(defenseKey, "amount", permaReward, "perma_name", permaName)
                : i18n.get(defenseKey);

        Component powerComponent = LEGACY_SERIALIZER.deserialize(i18n.getPrefix() + powerText)
                .clickEvent(ClickEvent.runCommand("/vrs upgrade power"));
        Component defenseComponent = LEGACY_SERIALIZER.deserialize(i18n.getPrefix() + defenseText)
                .clickEvent(ClickEvent.runCommand("/vrs upgrade defense"));

        player.sendMessage(powerComponent);
        player.sendMessage(defenseComponent);
    }

    /**
     * Determines the suggested upgrade for a player.
     * Respects max level states: if one is max, suggest the other.
     * If both available, random selection.
     *
     * @param playerState The player's state
     * @return "power" or "defense"
     */
    public String determineSuggestedUpgrade(PlayerState playerState) {
        boolean weaponMax = playerState.isWeaponAtMax();
        boolean helmetMax = playerState.isHelmetAtMax();

        if (weaponMax && !helmetMax) {
            return "defense";
        } else if (helmetMax && !weaponMax) {
            return "power";
        } else {
            // Both available or both max - random
            return ThreadLocalRandom.current().nextBoolean() ? "power" : "defense";
        }
    }

    // ==================== Core Upgrade Processing ====================

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
