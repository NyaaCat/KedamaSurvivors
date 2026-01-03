package cat.nyaa.survivors.command.admin;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.command.SubCommand;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles /vrs admin config subcommands for runtime configuration adjustments.
 */
public class ConfigSubCommand implements SubCommand {

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;

    // Config property definitions
    private final Map<String, ConfigProperty<?>> properties;

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

        String action = args[0].toLowerCase();

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
        ConfigProperty<?> property = properties.get(propertyName);

        if (property == null) {
            i18n.send(sender, "admin.config.property_not_found", "property", propertyName);
            return;
        }

        // For string properties, join all remaining args
        String valueStr;
        if (property instanceof StringProperty) {
            valueStr = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        } else {
            valueStr = args[2];
        }

        try {
            property.setFromString(valueStr);
            config.saveConfig();  // Persist to file immediately
            i18n.send(sender, "admin.config.property_set",
                    "property", propertyName,
                    "value", valueStr);
        } catch (NumberFormatException e) {
            i18n.send(sender, "admin.config.invalid_value",
                    "value", valueStr,
                    "type", property.getTypeName());
        } catch (IllegalArgumentException e) {
            i18n.send(sender, "admin.config.invalid_value",
                    "value", valueStr,
                    "type", property.getTypeName());
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        String category = args.length > 1 ? args[1].toLowerCase() : null;

        i18n.send(sender, "admin.config.list_header");

        // Group properties by category
        Map<String, List<String>> categories = new java.util.HashMap<>();
        categories.put("teleport", List.of("lobbyWorld", "lobbyX", "lobbyY", "lobbyZ", "prepCommand", "enterCommand", "respawnCommand"));
        categories.put("timing", List.of("deathCooldownSeconds", "respawnInvulnerabilitySeconds", "disconnectGraceSeconds", "countdownSeconds"));
        categories.put("spawning", List.of("minSpawnDistance", "maxSpawnDistance", "maxSampleAttempts", "spawnTickInterval", "targetMobsPerPlayer", "targetMobsPerPlayerIncreasePerLevel", "targetMobsPerPlayerMax", "maxSpawnsPerTick"));
        categories.put("rewards", List.of("xpShareEnabled", "xpShareRadius", "xpSharePercent",
                "damageContributionEnabled", "damageContributionPercent",
                "overflowEnabled", "overflowXpPerPermaScore", "overflowNotifyPlayer",
                "maxLevelPermaScoreReward", "permaScoreDisplayName",
                "scoreMultiplier", "scoreMultiplierEnabled", "scoreMultiplierAffectsPerma"));
        categories.put("progression", List.of("baseXpRequired", "xpPerLevelIncrease", "xpMultiplierPerLevel", "weaponLevelWeight", "helmetLevelWeight"));
        categories.put("teams", List.of("maxTeamSize", "inviteExpirySeconds"));
        categories.put("merchants", List.of("merchantsEnabled", "merchantSpawnInterval", "merchantLifetime",
                "merchantMinDistance", "merchantMaxDistance", "merchantSpawnChance",
                "merchantLimited", "merchantMinStaySeconds", "merchantMaxStaySeconds",
                "merchantSpawnParticles", "merchantDespawnParticles",
                "merchantMinItems", "merchantMaxItems", "merchantShowAllItems",
                "merchantRotationSpeed", "merchantBobHeight", "merchantBobSpeed",
                "merchantHeadItemCycleInterval",
                "wanderingMerchantPoolId", "wanderingMerchantType", "wanderingMerchantMaxCount"));
        categories.put("upgrade", List.of("upgradeTimeoutSeconds", "upgradeReminderIntervalSeconds"));
        categories.put("scoreboard", List.of("scoreboardEnabled", "scoreboardTitle", "scoreboardUpdateInterval"));
        categories.put("worldSelection", List.of("worldSelectionEnabled", "autoOpenWorldGui", "defaultTrackingRadius"));
        categories.put("overheadDisplay", List.of("overheadDisplayEnabled", "overheadDisplayYOffset", "overheadDisplayUpdateTicks"));
        categories.put("feedback", List.of("rewardDisplayMode", "rewardStackingEnabled", "rewardStackingTimeoutSeconds",
                "upgradeReminderDisplayMode", "upgradeReminderFlashIntervalTicks",
                "soundXpGained", "soundCoinGained", "soundPermaScoreGained",
                "soundUpgradeAvailable", "soundUpgradeSelected", "soundKillReward",
                "soundCountdownTick", "soundTeleport", "soundDeath", "soundRunStart"));

        if (category != null && categories.containsKey(category)) {
            // Show specific category
            for (String propName : categories.get(category)) {
                ConfigProperty<?> prop = properties.get(propName);
                if (prop != null) {
                    i18n.send(sender, "admin.config.list_entry",
                            "property", propName,
                            "value", String.valueOf(prop.get()),
                            "type", prop.getTypeName());
                }
            }
        } else {
            // Show categories
            i18n.send(sender, "admin.config.categories",
                    "categories", String.join(", ", categories.keySet()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : List.of("get", "set", "list")) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (action.equals("get") || action.equals("set")) {
                for (String propName : properties.keySet()) {
                    if (propName.toLowerCase().startsWith(partial)) {
                        completions.add(propName);
                    }
                }
            } else if (action.equals("list")) {
                for (String cat : List.of("teleport", "timing", "spawning", "rewards", "progression", "teams", "merchants", "upgrade", "scoreboard", "worldSelection", "overheadDisplay", "feedback")) {
                    if (cat.startsWith(partial)) {
                        completions.add(cat);
                    }
                }
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            String propName = args[1];

            if (action.equals("set")) {
                ConfigProperty<?> prop = properties.get(propName);
                if (prop instanceof BooleanProperty) {
                    completions.addAll(List.of("true", "false"));
                }
            }
        }

        return completions;
    }

    @Override
    public String getPermission() {
        return "vrs.admin.config";
    }

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
