package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.service.BatteryService;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles /vrs admin config subcommands for runtime configuration adjustments.
 */
public class ConfigSubCommand implements SubCommand {

    private static final List<String> STAGE_DYNAMIC_FIELDS = List.of(
            "displayName",
            "worlds",
            "startEnemyLevel",
            "requiredBatteries",
            "clearRewardCoins",
            "clearRewardPermaScore"
    );

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;

    // Config property definitions
    private final Map<String, ConfigProperty<?>> properties;
    private final Map<String, List<String>> categories;

    public ConfigSubCommand(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.config = plugin.getConfigService();

        // Define all configurable properties
        this.properties = Map.ofEntries(
                // Teleport settings
                entry("lobbyWorld", new StringProperty(config::getLobbyWorld, config::setLobbyWorld)),
                entry("lobbyX", new DoubleProperty(config::getLobbyX, config::setLobbyX)),
                entry("lobbyY", new DoubleProperty(config::getLobbyY, config::setLobbyY)),
                entry("lobbyZ", new DoubleProperty(config::getLobbyZ, config::setLobbyZ)),
                entry("prepCommand", new StringProperty(config::getPrepCommand, config::setPrepCommand)),
                entry("enterCommand", new StringProperty(config::getEnterCommand, config::setEnterCommand)),
                entry("respawnCommand", new StringProperty(config::getRespawnCommand, config::setRespawnCommand)),

                // Timings
                entry("deathCooldownSeconds", new IntProperty(config::getDeathCooldownSeconds, config::setDeathCooldownSeconds)),
                entry("respawnInvulnerabilitySeconds", new IntProperty(config::getRespawnInvulnerabilitySeconds, config::setRespawnInvulnerabilitySeconds)),
                entry("disconnectGraceSeconds", new IntProperty(config::getDisconnectGraceSeconds, config::setDisconnectGraceSeconds)),
                entry("countdownSeconds", new IntProperty(config::getCountdownSeconds, config::setCountdownSeconds)),

                // Spawning
                entry("minSpawnDistance", new DoubleProperty(config::getMinSpawnDistance, config::setMinSpawnDistance)),
                entry("maxSpawnDistance", new DoubleProperty(config::getMaxSpawnDistance, config::setMaxSpawnDistance)),
                entry("maxSampleAttempts", new IntProperty(config::getMaxSampleAttempts, config::setMaxSampleAttempts)),
                entry("spawnTickInterval", new IntProperty(config::getSpawnTickInterval, config::setSpawnTickInterval)),
                entry("targetMobsPerPlayer", new IntProperty(config::getTargetMobsPerPlayer, config::setTargetMobsPerPlayer)),
                entry("targetMobsPerPlayerIncreasePerLevel", new DoubleProperty(config::getTargetMobsPerPlayerIncreasePerLevel, config::setTargetMobsPerPlayerIncreasePerLevel)),
                entry("targetMobsPerPlayerMax", new IntProperty(config::getTargetMobsPerPlayerMax, config::setTargetMobsPerPlayerMax)),
                entry("maxSpawnsPerTick", new IntProperty(config::getMaxSpawnsPerTick, config::setMaxSpawnsPerTick)),

                // Rewards
                entry("xpShareEnabled", new BooleanProperty(config::isXpShareEnabled, config::setXpShareEnabled)),
                entry("xpShareRadius", new DoubleProperty(config::getXpShareRadius, config::setXpShareRadius)),
                entry("xpSharePercent", new DoubleProperty(config::getXpSharePercent, config::setXpSharePercent)),
                entry("damageContributionEnabled", new BooleanProperty(config::isDamageContributionEnabled, config::setDamageContributionEnabled)),
                entry("damageContributionPercent", new DoubleProperty(config::getDamageContributionPercent, config::setDamageContributionPercent)),
                entry("overflowEnabled", new BooleanProperty(config::isOverflowEnabled, config::setOverflowEnabled)),
                entry("overflowXpPerPermaScore", new IntProperty(config::getOverflowXpPerPermaScore, config::setOverflowXpPerPermaScore)),
                entry("overflowNotifyPlayer", new BooleanProperty(config::isOverflowNotifyPlayer, config::setOverflowNotifyPlayer)),
                entry("maxLevelPermaScoreReward", new IntProperty(config::getMaxLevelPermaScoreReward, config::setMaxLevelPermaScoreReward)),
                entry("permaScoreDisplayName", new StringProperty(config::getPermaScoreDisplayName, config::setPermaScoreDisplayName)),
                entry("scoreMultiplier", new IntProperty(config::getScoreMultiplier, config::setScoreMultiplier)),
                entry("scoreMultiplierEnabled", new BooleanProperty(config::isScoreMultiplierEnabled, config::setScoreMultiplierEnabled)),
                entry("scoreMultiplierAffectsPerma", new BooleanProperty(config::isScoreMultiplierAffectsPerma, config::setScoreMultiplierAffectsPerma)),

                // Stage progression
                entry("finalStageBonusCoins", new IntProperty(config::getFinalStageBonusCoins, config::setFinalStageBonusCoins)),
                entry("finalStageBonusPermaScore", new IntProperty(config::getFinalStageBonusPermaScore, config::setFinalStageBonusPermaScore)),

                // Battery objective
                entry("batteryEnabled", new BooleanProperty(config::isBatteryEnabled, config::setBatteryEnabled)),
                entry("batterySpawnIntervalSeconds", new IntProperty(config::getBatterySpawnIntervalSeconds, config::setBatterySpawnIntervalSeconds)),
                entry("batterySpawnChance", new DoubleProperty(config::getBatterySpawnChance, config::setBatterySpawnChance)),
                entry("batteryMinDistanceFromPlayers", new DoubleProperty(config::getBatteryMinDistanceFromPlayers, config::setBatteryMinDistanceFromPlayers)),
                entry("batteryMaxDistanceFromPlayers", new DoubleProperty(config::getBatteryMaxDistanceFromPlayers, config::setBatteryMaxDistanceFromPlayers)),
                entry("batteryVerticalRange", new IntProperty(config::getBatteryVerticalRange, config::setBatteryVerticalRange)),
                entry("batteryChargeRadius", new DoubleProperty(config::getBatteryChargeRadius, config::setBatteryChargeRadius)),
                entry("batteryBaseChargePercentPerSecond", new DoubleProperty(config::getBatteryBaseChargePercentPerSecond, config::setBatteryBaseChargePercentPerSecond)),
                entry("batteryExtraPlayerChargePercentPerSecond", new DoubleProperty(config::getBatteryExtraPlayerChargePercentPerSecond, config::setBatteryExtraPlayerChargePercentPerSecond)),
                entry("batteryProgressUpdateTicks", new IntProperty(config::getBatteryProgressUpdateTicks, config::setBatteryProgressUpdateTicks)),
                entry("batteryDisplayMaterial", new StringProperty(config::getBatteryDisplayMaterialName, config::setBatteryDisplayMaterial)),
                entry("batteryDisplayCustomModelData", new IntProperty(config::getBatteryDisplayCustomModelData, config::setBatteryDisplayCustomModelData)),
                entry("batteryDisplayName", new StringProperty(config::getBatteryDisplayName, config::setBatteryDisplayName)),
                entry("batteryShowRangeParticles", new BooleanProperty(config::isBatteryShowRangeParticles, config::setBatteryShowRangeParticles)),
                entry("batterySurgeEnabled", new BooleanProperty(config::isBatterySurgeEnabled, config::setBatterySurgeEnabled)),
                entry("batterySurgeIntervalSeconds", new IntProperty(config::getBatterySurgeIntervalSeconds, config::setBatterySurgeIntervalSeconds)),
                entry("batterySurgeMobCount", new IntProperty(config::getBatterySurgeMobCount, config::setBatterySurgeMobCount)),
                entry("batteryChargeCompleteRewardBurstEnabled",
                        new BooleanProperty(config::isBatteryChargeCompleteRewardBurstEnabled, config::setBatteryChargeCompleteRewardBurstEnabled)),
                entry("batteryChargeCompleteRewardPoolId",
                        new StringProperty(config::getBatteryChargeCompleteRewardPoolId, config::setBatteryChargeCompleteRewardPoolId)),
                entry("batteryChargeCompleteRewardBaseCount",
                        new IntProperty(config::getBatteryChargeCompleteRewardBaseCount, config::setBatteryChargeCompleteRewardBaseCount)),
                entry("batteryChargeCompleteRewardBurstTicks",
                        new IntProperty(config::getBatteryChargeCompleteRewardBurstTicks, config::setBatteryChargeCompleteRewardBurstTicks)),
                entry("batteryChargeCompleteRewardScatterRadius",
                        new DoubleProperty(config::getBatteryChargeCompleteRewardScatterRadius, config::setBatteryChargeCompleteRewardScatterRadius)),
                entry("batteryChargeCompleteRewardLifetimeSeconds",
                        new IntProperty(config::getBatteryChargeCompleteRewardLifetimeSeconds, config::setBatteryChargeCompleteRewardLifetimeSeconds)),
                entry("batteryChargeCompleteSpawnSuppressionEnabled",
                        new BooleanProperty(config::isBatteryChargeCompleteSpawnSuppressionEnabled, config::setBatteryChargeCompleteSpawnSuppressionEnabled)),
                entry("batteryChargeCompleteSpawnSuppressionSeconds",
                        new IntProperty(config::getBatteryChargeCompleteSpawnSuppressionSeconds, config::setBatteryChargeCompleteSpawnSuppressionSeconds)),
                entry("batteryChargeCompleteSpawnSuppressionRadius",
                        new DoubleProperty(config::getBatteryChargeCompleteSpawnSuppressionRadius, config::setBatteryChargeCompleteSpawnSuppressionRadius)),
                entry("batteryChargeCompleteSuppressParticipants",
                        new BooleanProperty(config::isBatteryChargeCompleteSuppressParticipants, config::setBatteryChargeCompleteSuppressParticipants)),
                entry("batteryChargeCompleteFireworkEnabled",
                        new BooleanProperty(config::isBatteryChargeCompleteFireworkEnabled, config::setBatteryChargeCompleteFireworkEnabled)),
                entry("batteryChargeCompleteSound",
                        new StringProperty(config::getBatteryChargeCompleteSoundStr, config::setBatteryChargeCompleteSound)),

                // Progression
                entry("baseXpRequired", new IntProperty(config::getBaseXpRequired, config::setBaseXpRequired)),
                entry("xpPerLevelIncrease", new IntProperty(config::getXpPerLevelIncrease, config::setXpPerLevelIncrease)),
                entry("xpMultiplierPerLevel", new DoubleProperty(config::getXpMultiplierPerLevel, config::setXpMultiplierPerLevel)),
                entry("weaponLevelWeight", new IntProperty(config::getWeaponLevelWeight, config::setWeaponLevelWeight)),
                entry("helmetLevelWeight", new IntProperty(config::getHelmetLevelWeight, config::setHelmetLevelWeight)),

                // Teams
                entry("maxTeamSize", new IntProperty(config::getMaxTeamSize, config::setMaxTeamSize)),
                entry("inviteExpirySeconds", new IntProperty(config::getInviteExpirySeconds, config::setInviteExpirySeconds)),

                // Merchants
                entry("merchantsEnabled", new BooleanProperty(config::isMerchantsEnabled, config::setMerchantsEnabled)),
                entry("merchantSpawnInterval", new IntProperty(config::getMerchantSpawnInterval, config::setMerchantSpawnInterval)),
                entry("merchantLifetime", new IntProperty(config::getMerchantLifetime, config::setMerchantLifetime)),
                entry("merchantMinDistance", new DoubleProperty(config::getMerchantMinDistance, config::setMerchantMinDistance)),
                entry("merchantMaxDistance", new DoubleProperty(config::getMerchantMaxDistance, config::setMerchantMaxDistance)),
                entry("merchantSpawnChance", new DoubleProperty(config::getMerchantSpawnChance, config::setMerchantSpawnChance)),
                entry("merchantLimited", new BooleanProperty(config::isMerchantLimited, config::setMerchantLimited)),
                entry("merchantMinStaySeconds", new IntProperty(config::getMerchantMinStaySeconds, config::setMerchantMinStaySeconds)),
                entry("merchantMaxStaySeconds", new IntProperty(config::getMerchantMaxStaySeconds, config::setMerchantMaxStaySeconds)),
                entry("merchantSpawnParticles", new BooleanProperty(config::isMerchantSpawnParticles, config::setMerchantSpawnParticles)),
                entry("merchantDespawnParticles", new BooleanProperty(config::isMerchantDespawnParticles, config::setMerchantDespawnParticles)),
                entry("merchantMinItems", new IntProperty(config::getMerchantMinItems, config::setMerchantMinItems)),
                entry("merchantMaxItems", new IntProperty(config::getMerchantMaxItems, config::setMerchantMaxItems)),
                entry("merchantShowAllItems", new BooleanProperty(config::isMerchantShowAllItems, config::setMerchantShowAllItems)),
                entry("merchantRotationSpeed", new FloatProperty(config::getMerchantRotationSpeed, config::setMerchantRotationSpeed)),
                entry("merchantBobHeight", new DoubleProperty(config::getMerchantBobHeight, config::setMerchantBobHeight)),
                entry("merchantBobSpeed", new DoubleProperty(config::getMerchantBobSpeed, config::setMerchantBobSpeed)),
                entry("merchantHeadItemCycleInterval", new IntProperty(config::getMerchantHeadItemCycleInterval, config::setMerchantHeadItemCycleInterval)),
                entry("wanderingMerchantPoolId", new StringProperty(config::getWanderingMerchantPoolId, config::setWanderingMerchantPoolId)),
                entry("wanderingMerchantType", new StringProperty(config::getWanderingMerchantType, config::setWanderingMerchantType)),
                entry("wanderingMerchantMaxCount", new IntProperty(config::getWanderingMerchantMaxCount, config::setWanderingMerchantMaxCount)),

                // Upgrade
                entry("upgradeTimeoutSeconds", new IntProperty(config::getUpgradeTimeoutSeconds, config::setUpgradeTimeoutSeconds)),
                entry("upgradeReminderIntervalSeconds", new IntProperty(config::getUpgradeReminderIntervalSeconds, config::setUpgradeReminderIntervalSeconds)),

                // Scoreboard
                entry("scoreboardEnabled", new BooleanProperty(config::isScoreboardEnabled, config::setScoreboardEnabled)),
                entry("scoreboardTitle", new StringProperty(config::getScoreboardTitle, config::setScoreboardTitle)),
                entry("scoreboardUpdateInterval", new IntProperty(config::getScoreboardUpdateInterval, config::setScoreboardUpdateInterval)),

                // World Selection
                entry("worldSelectionEnabled", new BooleanProperty(config::isWorldSelectionEnabled, config::setWorldSelectionEnabled)),
                entry("autoOpenWorldGui", new BooleanProperty(config::isAutoOpenWorldGui, config::setAutoOpenWorldGui)),
                entry("defaultTrackingRadius", new DoubleProperty(config::getDefaultTrackingRadius, config::setDefaultTrackingRadius)),

                // Overhead Display
                entry("overheadDisplayEnabled", new BooleanProperty(config::isOverheadDisplayEnabled, config::setOverheadDisplayEnabled)),
                entry("overheadDisplayYOffset", new DoubleProperty(config::getOverheadDisplayYOffset, config::setOverheadDisplayYOffset)),
                entry("overheadDisplayUpdateTicks", new IntProperty(config::getOverheadDisplayUpdateTicks, config::setOverheadDisplayUpdateTicks)),

                // Feedback - display modes
                entry("rewardDisplayMode", new StringProperty(config::getRewardDisplayMode, config::setRewardDisplayMode)),
                entry("rewardStackingEnabled", new BooleanProperty(config::isRewardStackingEnabled, config::setRewardStackingEnabled)),
                entry("rewardStackingTimeoutSeconds", new IntProperty(config::getRewardStackingTimeoutSeconds, config::setRewardStackingTimeoutSeconds)),
                entry("upgradeReminderDisplayMode", new StringProperty(config::getUpgradeReminderDisplayMode, config::setUpgradeReminderDisplayMode)),
                entry("upgradeReminderFlashIntervalTicks", new IntProperty(config::getUpgradeReminderFlashIntervalTicks, config::setUpgradeReminderFlashIntervalTicks)),

                // Feedback - sounds (format: "sound volume pitch")
                entry("soundXpGained", new StringProperty(config::getSoundXpGainedStr, config::setSoundXpGained)),
                entry("soundCoinGained", new StringProperty(config::getSoundCoinGainedStr, config::setSoundCoinGained)),
                entry("soundPermaScoreGained", new StringProperty(config::getSoundPermaScoreGainedStr, config::setSoundPermaScoreGained)),
                entry("soundUpgradeAvailable", new StringProperty(config::getSoundUpgradeAvailableStr, config::setSoundUpgradeAvailable)),
                entry("soundUpgradeSelected", new StringProperty(config::getSoundUpgradeSelectedStr, config::setSoundUpgradeSelected)),
                entry("soundKillReward", new StringProperty(config::getSoundKillRewardStr, config::setSoundKillReward)),
                entry("soundCountdownTick", new StringProperty(config::getSoundCountdownTickStr, config::setSoundCountdownTick)),
                entry("soundTeleport", new StringProperty(config::getSoundTeleportStr, config::setSoundTeleport)),
                entry("soundDeath", new StringProperty(config::getSoundDeathStr, config::setSoundDeath)),
                entry("soundRunStart", new StringProperty(config::getSoundRunStartStr, config::setSoundRunStart))
        );

        this.categories = createCategories();
    }

    private Map<String, List<String>> createCategories() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("teleport", List.of("lobbyWorld", "lobbyX", "lobbyY", "lobbyZ", "prepCommand", "enterCommand", "respawnCommand"));
        map.put("timing", List.of("deathCooldownSeconds", "respawnInvulnerabilitySeconds", "disconnectGraceSeconds", "countdownSeconds"));
        map.put("spawning", List.of("minSpawnDistance", "maxSpawnDistance", "maxSampleAttempts", "spawnTickInterval", "targetMobsPerPlayer", "targetMobsPerPlayerIncreasePerLevel", "targetMobsPerPlayerMax", "maxSpawnsPerTick"));
        map.put("rewards", List.of("xpShareEnabled", "xpShareRadius", "xpSharePercent",
                "damageContributionEnabled", "damageContributionPercent",
                "overflowEnabled", "overflowXpPerPermaScore", "overflowNotifyPlayer",
                "maxLevelPermaScoreReward", "permaScoreDisplayName",
                "scoreMultiplier", "scoreMultiplierEnabled", "scoreMultiplierAffectsPerma"));
        map.put("stage", List.of("finalStageBonusCoins", "finalStageBonusPermaScore"));
        map.put("battery", List.of("batteryEnabled", "batterySpawnIntervalSeconds", "batterySpawnChance",
                "batteryMinDistanceFromPlayers", "batteryMaxDistanceFromPlayers", "batteryVerticalRange",
                "batteryChargeRadius", "batteryBaseChargePercentPerSecond", "batteryExtraPlayerChargePercentPerSecond",
                "batteryProgressUpdateTicks", "batteryDisplayMaterial", "batteryDisplayCustomModelData",
                "batteryDisplayName", "batteryShowRangeParticles", "batterySurgeEnabled",
                "batterySurgeIntervalSeconds", "batterySurgeMobCount",
                "batteryChargeCompleteRewardBurstEnabled", "batteryChargeCompleteRewardPoolId",
                "batteryChargeCompleteRewardBaseCount", "batteryChargeCompleteRewardBurstTicks",
                "batteryChargeCompleteRewardScatterRadius", "batteryChargeCompleteRewardLifetimeSeconds",
                "batteryChargeCompleteSpawnSuppressionEnabled", "batteryChargeCompleteSpawnSuppressionSeconds",
                "batteryChargeCompleteSpawnSuppressionRadius", "batteryChargeCompleteSuppressParticipants",
                "batteryChargeCompleteFireworkEnabled", "batteryChargeCompleteSound"));
        map.put("progression", List.of("baseXpRequired", "xpPerLevelIncrease", "xpMultiplierPerLevel", "weaponLevelWeight", "helmetLevelWeight"));
        map.put("teams", List.of("maxTeamSize", "inviteExpirySeconds"));
        map.put("merchants", List.of("merchantsEnabled", "merchantSpawnInterval", "merchantLifetime",
                "merchantMinDistance", "merchantMaxDistance", "merchantSpawnChance",
                "merchantLimited", "merchantMinStaySeconds", "merchantMaxStaySeconds",
                "merchantSpawnParticles", "merchantDespawnParticles",
                "merchantMinItems", "merchantMaxItems", "merchantShowAllItems",
                "merchantRotationSpeed", "merchantBobHeight", "merchantBobSpeed",
                "merchantHeadItemCycleInterval",
                "wanderingMerchantPoolId", "wanderingMerchantType", "wanderingMerchantMaxCount"));
        map.put("upgrade", List.of("upgradeTimeoutSeconds", "upgradeReminderIntervalSeconds"));
        map.put("scoreboard", List.of("scoreboardEnabled", "scoreboardTitle", "scoreboardUpdateInterval"));
        map.put("worldSelection", List.of("worldSelectionEnabled", "autoOpenWorldGui", "defaultTrackingRadius"));
        map.put("overheadDisplay", List.of("overheadDisplayEnabled", "overheadDisplayYOffset", "overheadDisplayUpdateTicks"));
        map.put("feedback", List.of("rewardDisplayMode", "rewardStackingEnabled", "rewardStackingTimeoutSeconds",
                "upgradeReminderDisplayMode", "upgradeReminderFlashIntervalTicks",
                "soundXpGained", "soundCoinGained", "soundPermaScoreGained",
                "soundUpgradeAvailable", "soundUpgradeSelected", "soundKillReward",
                "soundCountdownTick", "soundTeleport", "soundDeath", "soundRunStart"));
        return map;
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return Map.entry(key, value);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "get" -> handleGet(sender, args);
            case "set" -> handleSet(sender, args);
            case "list" -> handleList(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        i18n.send(sender, "admin.config.help.header");
        i18n.send(sender, "admin.config.help.get");
        i18n.send(sender, "admin.config.help.set");
        i18n.send(sender, "admin.config.help.list");
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            i18n.send(sender, "admin.config.help.get");
            return;
        }

        String propertyName = args[1];
        if (isStageDynamicProperty(propertyName)) {
            handleGetStageProperty(sender, propertyName);
            return;
        }

        ConfigProperty<?> property = properties.get(propertyName);

        if (property == null) {
            i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
            return;
        }

        Object value = property.get();
        i18n.send(sender, "admin.config.property_value",
                "property", propertyName,
                "value", String.valueOf(value),
                "type", property.getTypeName());
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            i18n.send(sender, "admin.config.help.set");
            return;
        }

        String propertyName = args[1];

        if (isStageDynamicProperty(propertyName)) {
            handleSetStageProperty(sender, propertyName, Arrays.copyOfRange(args, 2, args.length));
            return;
        }

        ConfigProperty<?> property = properties.get(propertyName);

        if (property == null) {
            i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
            return;
        }

        // For string properties, join all remaining args
        String valueStr;
        if (property instanceof StringProperty) {
            valueStr = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        } else {
            valueStr = args[2];
        }

        try {
            property.setFromString(valueStr);
            config.saveConfig(); // Persist to file immediately
            applyHotUpdate(propertyName);
            i18n.send(sender, "admin.config.property_set",
                    "property", propertyName,
                    "value", valueStr);
        } catch (IllegalArgumentException e) {
            i18n.send(sender, "admin.config.invalid_value",
                    "value", valueStr,
                    "type", property.getTypeName());
        }
    }

    private void handleGetStageProperty(CommandSender sender, String propertyName) {
        Optional<StagePropertyRef> refOpt = parseStagePropertyRef(propertyName);
        if (refOpt.isEmpty()) {
            i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
            return;
        }

        StagePropertyRef ref = refOpt.get();
        Optional<ConfigService.StageGroupConfig> groupOpt = config.getStageGroupById(ref.groupId());
        if (groupOpt.isEmpty()) {
            i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
            return;
        }

        ConfigService.StageGroupConfig group = groupOpt.get();
        Object value;
        String type;

        switch (ref.field()) {
            case "displayname" -> {
                value = group.displayName;
                type = "string";
            }
            case "worlds" -> {
                value = group.worldNames == null ? "" : String.join(", ", group.worldNames);
                type = "list";
            }
            case "startenemylevel" -> {
                value = group.startEnemyLevel;
                type = "int";
            }
            case "requiredbatteries" -> {
                value = group.requiredBatteries;
                type = "int";
            }
            case "clearrewardcoins" -> {
                value = group.clearRewardCoins;
                type = "int";
            }
            case "clearrewardpermascore" -> {
                value = group.clearRewardPermaScore;
                type = "int";
            }
            default -> {
                i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
                return;
            }
        }

        i18n.send(sender, "admin.config.property_value",
                "property", propertyName,
                "value", String.valueOf(value),
                "type", type);
    }

    private void handleSetStageProperty(CommandSender sender, String propertyName, String[] valueArgs) {
        Optional<StagePropertyRef> refOpt = parseStagePropertyRef(propertyName);
        if (refOpt.isEmpty()) {
            i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
            return;
        }
        if (valueArgs.length == 0) {
            i18n.send(sender, "admin.config.help.set");
            return;
        }

        StagePropertyRef ref = refOpt.get();
        String valueDisplay;

        try {
            boolean updated;
            switch (ref.field()) {
                case "displayname" -> {
                    valueDisplay = String.join(" ", valueArgs);
                    updated = config.setStageGroupDisplayName(ref.groupId(), valueDisplay);
                }
                case "worlds" -> {
                    List<String> worlds = parseWorldList(valueArgs);
                    valueDisplay = worlds.isEmpty() ? "any" : String.join(", ", worlds);
                    updated = config.setStageGroupWorldNames(ref.groupId(), worlds);
                }
                case "startenemylevel" -> {
                    int value = parsePositiveInt(valueArgs[0]);
                    valueDisplay = String.valueOf(value);
                    updated = config.setStageGroupStartEnemyLevel(ref.groupId(), value);
                }
                case "requiredbatteries" -> {
                    int value = parsePositiveInt(valueArgs[0]);
                    valueDisplay = String.valueOf(value);
                    updated = config.setStageGroupRequiredBatteries(ref.groupId(), value);
                }
                case "clearrewardcoins" -> {
                    int value = parseNonNegativeInt(valueArgs[0]);
                    valueDisplay = String.valueOf(value);
                    updated = config.setStageGroupClearRewardCoins(ref.groupId(), value);
                }
                case "clearrewardpermascore" -> {
                    int value = parseNonNegativeInt(valueArgs[0]);
                    valueDisplay = String.valueOf(value);
                    updated = config.setStageGroupClearRewardPermaScore(ref.groupId(), value);
                }
                default -> {
                    i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
                    return;
                }
            }

            if (!updated) {
                i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
                return;
            }

            config.saveConfig();
            applyHotUpdate(propertyName);
            i18n.send(sender, "admin.config.property_set",
                    "property", propertyName,
                    "value", valueDisplay);
        } catch (IllegalArgumentException e) {
            i18n.send(sender, "admin.config.invalid_value",
                    "value", String.join(" ", valueArgs),
                    "type", "stage value");
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                sender.sendMessage("§c" + e.getMessage());
            }
        } catch (RuntimeException e) {
            i18n.send(sender, "admin.config.invalid_value",
                    "value", String.join(" ", valueArgs),
                    "type", "stage value");
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                sender.sendMessage("§c" + e.getMessage());
            }
        }
    }

    private void applyHotUpdate(String propertyName) {
        String lower = propertyName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("battery")) {
            BatteryService batteryService = plugin.getBatteryService();
            if (batteryService != null) {
                batteryService.reloadRuntimeConfig();
            }
        }
    }

    private int parsePositiveInt(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException("Value must be >= 1");
        }
        return parsed;
    }

    private int parseNonNegativeInt(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException("Value must be >= 0");
        }
        return parsed;
    }

    private List<String> parseWorldList(String[] valueArgs) {
        List<String> worlds = new ArrayList<>();
        for (String arg : valueArgs) {
            for (String token : arg.split(",")) {
                String world = token.trim();
                if (world.isEmpty()) continue;

                if ("any".equalsIgnoreCase(world)
                        || "none".equalsIgnoreCase(world)
                        || "empty".equalsIgnoreCase(world)
                        || "[]".equals(world)) {
                    return new ArrayList<>();
                }

                worlds.add(world);
            }
        }
        return worlds;
    }

    private void handleList(CommandSender sender, String[] args) {
        i18n.send(sender, "admin.config.list_header");

        if (args.length <= 1) {
            i18n.send(sender, "admin.config.categories",
                    "categories", String.join(", ", getListCategories()));
            return;
        }

        Set<String> requested = new LinkedHashSet<>();
        for (int i = 1; i < args.length; i++) {
            requested.add(args[i].toLowerCase(Locale.ROOT));
        }

        boolean displayed = false;
        if (requested.contains("all")) {
            for (String category : categories.keySet()) {
                if (displayCategory(sender, category)) {
                    displayed = true;
                }
            }
        } else {
            for (String category : requested) {
                if (displayCategory(sender, category)) {
                    displayed = true;
                }
            }
        }

        if (!displayed) {
            i18n.send(sender, "admin.config.categories",
                    "categories", String.join(", ", getListCategories()));
        }
    }

    private boolean displayCategory(CommandSender sender, String category) {
        List<String> categoryProps = categories.get(category);
        if (categoryProps == null) {
            return false;
        }

        for (String propName : categoryProps) {
            ConfigProperty<?> prop = properties.get(propName);
            if (prop != null) {
                i18n.send(sender, "admin.config.list_entry",
                        "property", propName,
                        "value", String.valueOf(prop.get()),
                        "type", prop.getTypeName());
            }
        }

        if ("stage".equals(category)) {
            listStageGroups(sender);
        }
        return true;
    }

    private void listStageGroups(CommandSender sender) {
        List<ConfigService.StageGroupConfig> groups = config.getStageGroups();
        if (groups == null || groups.isEmpty()) {
            i18n.send(sender, "admin.config.list_entry",
                    "property", "stage.groups",
                    "value", "(empty)",
                    "type", "list");
            return;
        }

        for (ConfigService.StageGroupConfig group : groups) {
            String prefix = "stage." + group.groupId;
            i18n.send(sender, "admin.config.list_entry",
                    "property", prefix + ".displayName",
                    "value", String.valueOf(group.displayName),
                    "type", "string");
            i18n.send(sender, "admin.config.list_entry",
                    "property", prefix + ".worlds",
                    "value", group.worldNames == null ? "" : String.join(", ", group.worldNames),
                    "type", "list");
            i18n.send(sender, "admin.config.list_entry",
                    "property", prefix + ".startEnemyLevel",
                    "value", String.valueOf(group.startEnemyLevel),
                    "type", "int");
            i18n.send(sender, "admin.config.list_entry",
                    "property", prefix + ".requiredBatteries",
                    "value", String.valueOf(group.requiredBatteries),
                    "type", "int");
            i18n.send(sender, "admin.config.list_entry",
                    "property", prefix + ".clearRewardCoins",
                    "value", String.valueOf(group.clearRewardCoins),
                    "type", "int");
            i18n.send(sender, "admin.config.list_entry",
                    "property", prefix + ".clearRewardPermaScore",
                    "value", String.valueOf(group.clearRewardPermaScore),
                    "type", "int");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String action : List.of("get", "set", "list")) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            String partial = args[1].toLowerCase(Locale.ROOT);

            if (action.equals("get") || action.equals("set")) {
                for (String propName : properties.keySet()) {
                    if (propName.toLowerCase(Locale.ROOT).startsWith(partial)) {
                        completions.add(propName);
                    }
                }
                for (String stagePropName : getStageDynamicPropertyNames()) {
                    if (stagePropName.toLowerCase(Locale.ROOT).startsWith(partial)) {
                        completions.add(stagePropName);
                    }
                }
            } else if (action.equals("list")) {
                for (String category : getListCategories()) {
                    if (category.startsWith(partial)) {
                        completions.add(category);
                    }
                }
            }
        } else {
            String action = args[0].toLowerCase(Locale.ROOT);

            if (action.equals("set")) {
                String propName = args[1];
                if (isStageDynamicProperty(propName)) {
                    String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
                    if (propName.toLowerCase(Locale.ROOT).endsWith(".worlds")) {
                        if ("any".startsWith(partial)) {
                            completions.add("any");
                        }
                        for (var worldConfig : plugin.getConfigService().getCombatWorlds()) {
                            if (worldConfig.name.toLowerCase(Locale.ROOT).startsWith(partial)) {
                                completions.add(worldConfig.name);
                            }
                        }
                    }
                } else if (args.length == 3) {
                    ConfigProperty<?> prop = properties.get(propName);
                    if (prop instanceof BooleanProperty) {
                        completions.addAll(List.of("true", "false"));
                    }
                }
            } else if (action.equals("list")) {
                String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
                Set<String> used = new LinkedHashSet<>();
                for (int i = 1; i < args.length - 1; i++) {
                    used.add(args[i].toLowerCase(Locale.ROOT));
                }

                for (String category : getListCategories()) {
                    if (!used.contains(category) && category.startsWith(partial)) {
                        completions.add(category);
                    }
                }
            }
        }

        return completions;
    }

    private List<String> getListCategories() {
        List<String> result = new ArrayList<>(categories.keySet());
        result.add("all");
        return result;
    }

    private List<String> getStageDynamicPropertyNames() {
        List<String> props = new ArrayList<>();
        List<ConfigService.StageGroupConfig> groups = config.getStageGroups();
        if (groups == null) {
            return props;
        }
        for (ConfigService.StageGroupConfig group : groups) {
            String prefix = "stage." + group.groupId + ".";
            for (String field : STAGE_DYNAMIC_FIELDS) {
                props.add(prefix + field);
            }
        }
        return props;
    }

    private boolean isStageDynamicProperty(String propertyName) {
        return propertyName != null && propertyName.toLowerCase(Locale.ROOT).startsWith("stage.");
    }

    private Optional<StagePropertyRef> parseStagePropertyRef(String propertyName) {
        if (!isStageDynamicProperty(propertyName)) {
            return Optional.empty();
        }

        String remaining = propertyName.substring("stage.".length());
        int lastDot = remaining.lastIndexOf('.');
        if (lastDot <= 0 || lastDot >= remaining.length() - 1) {
            return Optional.empty();
        }

        String groupId = remaining.substring(0, lastDot);
        String field = remaining.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        return Optional.of(new StagePropertyRef(groupId, field));
    }

    @Override
    public String getPermission() {
        return "vrs.admin.config";
    }

    private record StagePropertyRef(String groupId, String field) {}

    // ==================== Property Type Classes ====================

    private interface ConfigProperty<T> {
        T get();
        void setFromString(String value);
        String getTypeName();
    }

    private static class StringProperty implements ConfigProperty<String> {
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        StringProperty(Supplier<String> getter, Consumer<String> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public String get() { return getter.get(); }

        @Override
        public void setFromString(String value) { setter.accept(value); }

        @Override
        public String getTypeName() { return "string"; }
    }

    private static class IntProperty implements ConfigProperty<Integer> {
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;

        IntProperty(Supplier<Integer> getter, Consumer<Integer> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public Integer get() { return getter.get(); }

        @Override
        public void setFromString(String value) {
            setter.accept(Integer.parseInt(value));
        }

        @Override
        public String getTypeName() { return "int"; }
    }

    private static class DoubleProperty implements ConfigProperty<Double> {
        private final Supplier<Double> getter;
        private final Consumer<Double> setter;

        DoubleProperty(Supplier<Double> getter, Consumer<Double> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public Double get() { return getter.get(); }

        @Override
        public void setFromString(String value) {
            setter.accept(Double.parseDouble(value));
        }

        @Override
        public String getTypeName() { return "double"; }
    }

    private static class BooleanProperty implements ConfigProperty<Boolean> {
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;

        BooleanProperty(Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public Boolean get() { return getter.get(); }

        @Override
        public void setFromString(String value) {
            if (value.equalsIgnoreCase("true") || value.equals("1")) {
                setter.accept(true);
            } else if (value.equalsIgnoreCase("false") || value.equals("0")) {
                setter.accept(false);
            } else {
                throw new IllegalArgumentException("Invalid boolean: " + value);
            }
        }

        @Override
        public String getTypeName() { return "boolean"; }
    }

    private static class FloatProperty implements ConfigProperty<Float> {
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;

        FloatProperty(Supplier<Float> getter, Consumer<Float> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public Float get() { return getter.get(); }

        @Override
        public void setFromString(String value) {
            setter.accept(Float.parseFloat(value));
        }

        @Override
        public String getTypeName() { return "float"; }
    }
}
