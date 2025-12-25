package cat.nyaa.survivors.config;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.economy.EconomyMode;
import cat.nyaa.survivors.util.ConfigException;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles loading and accessing plugin configuration.
 * Supports hot reload without server restart.
 */
public class ConfigService {

    private final KedamaSurvivorsPlugin plugin;
    private FileConfiguration config;

    // Cached config values
    private String language;
    private boolean verbose;

    // Join switch
    private volatile boolean joinEnabled;
    private int graceEjectSeconds;
    private int graceWarningInterval;

    // Ready & Countdown
    private int countdownSeconds;
    private boolean showActionBar;
    private boolean showTitle;
    private Sound countdownSound;
    private float countdownSoundPitch;
    private Sound teleportSound;

    // Cooldown
    private int deathCooldownSeconds;
    private int quitCooldownSeconds;
    private boolean showCooldownBar;
    private int displayUpdateTicks;

    // Disconnect
    private int disconnectGraceSeconds;
    private int disconnectCheckIntervalTicks;
    private boolean notifyTeamOnDisconnect;
    private boolean notifyGraceExpired;

    // Teams
    private int maxTeamSize;
    private int inviteExpirySeconds;

    // Respawn
    private int respawnInvulnerabilitySeconds;
    private boolean canDealDamageDuringInvul;
    private boolean pvpEnabled;

    // Spawning limits
    private int spawnTickInterval;
    private boolean spawningEnabled;
    private boolean blockNaturalSpawns;
    private int targetMobsPerPlayer;
    private int maxSpawnsPerPlayerPerTick;
    private int maxSpawnsPerTick;
    private int maxCommandsPerTick;
    private double mobCountRadius;

    // Spawn positioning
    private double minSpawnDistance;
    private double maxSpawnDistance;
    private int maxSampleAttempts;
    private int spawnVerticalRange;

    // Enemy level calculation
    private double levelSamplingRadius;
    private double avgLevelMultiplier;
    private double playerCountMultiplier;
    private int levelOffset;
    private int minEnemyLevel;
    private int maxEnemyLevel;
    private boolean timeScalingEnabled;
    private int timeStepSeconds;
    private int levelPerTimeStep;

    // Economy
    private EconomyMode economyMode;
    private String coinNbtTag;

    // Rewards
    private boolean xpShareEnabled;
    private double xpShareRadius;
    private double xpSharePercent;
    private boolean damageContributionEnabled;
    private double damageContributionPercent;
    private Material coinMaterial;
    private int coinCustomModelData;
    private String coinDisplayName;

    // Progression
    private int baseXpRequired;
    private int xpPerLevelIncrease;
    private double xpMultiplierPerLevel;
    private int weaponLevelWeight;
    private int helmetLevelWeight;
    private boolean overflowEnabled;
    private int overflowXpPerPermaScore;
    private boolean overflowNotifyPlayer;
    private String maxLevelBehaviorMode;
    private int maxLevelPermaScoreReward;

    // Upgrade selection
    private int upgradeTimeoutSeconds;
    private int upgradeReminderIntervalSeconds;
    private int upgradeBothMaxPermaReward;

    // Scoreboard
    private boolean scoreboardEnabled;
    private String scoreboardTitle;
    private int scoreboardUpdateInterval;
    private String permaScoreObjectiveName;
    private String permaScoreDisplayName;

    // Persistence
    private int saveIntervalSeconds;
    private boolean saveOnQuit;
    private boolean saveOnRunEnd;
    private String itemsPath;
    private String runtimePath;

    // Teleport settings
    private String lobbyWorld;
    private double lobbyX;
    private double lobbyY;
    private double lobbyZ;
    private String prepCommand;
    private String enterCommand;
    private String respawnCommand;
    private double teamSpawnOffsetRange;

    // Template settings
    private boolean escapingEnabled;
    private String escapeChars;
    private String missingPlaceholderMode;

    // Combat worlds
    private List<CombatWorldConfig> combatWorlds;

    // Starter options
    private boolean requireWeaponFirst;
    private boolean autoOpenHelmetGui;
    private List<StarterOptionConfig> starterWeapons;
    private List<StarterOptionConfig> starterHelmets;

    // Equipment pools
    private Map<String, EquipmentGroupConfig> weaponGroups;
    private Map<String, EquipmentGroupConfig> helmetGroups;

    // Enemy archetypes
    private Map<String, EnemyArchetypeConfig> enemyArchetypes;

    // Feedback settings
    private String rewardDisplayMode;
    private boolean rewardStackingEnabled;
    private int rewardStackingTimeoutSeconds;
    private String upgradeReminderDisplayMode;
    private int upgradeReminderFlashIntervalTicks;

    // Sound configs (null = disabled)
    private SoundConfig soundXpGained;
    private SoundConfig soundCoinGained;
    private SoundConfig soundPermaScoreGained;
    private SoundConfig soundUpgradeAvailable;
    private SoundConfig soundUpgradeSelected;
    private SoundConfig soundKillReward;
    // Game event sounds
    private SoundConfig soundCountdownTick;
    private SoundConfig soundTeleport;
    private SoundConfig soundDeath;
    private SoundConfig soundRunStart;

    // Merchant config
    private boolean merchantsEnabled;
    private int merchantSpawnInterval;
    private int merchantLifetime;
    private double merchantMinDistance;
    private double merchantMaxDistance;
    private int merchantHeadItemCycleInterval;
    private double merchantSpawnChance;
    private boolean merchantLimited;
    private int merchantMinStaySeconds;
    private int merchantMaxStaySeconds;
    private boolean merchantSpawnParticles;
    private boolean merchantDespawnParticles;
    private int merchantMinItems;
    private int merchantMaxItems;
    private boolean merchantShowAllItems;
    private float merchantRotationSpeed;
    private double merchantBobHeight;
    private double merchantBobSpeed;
    private String wanderingMerchantPoolId;
    private String wanderingMerchantType;
    private int wanderingMerchantMaxCount;

    public ConfigService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or reloads the configuration.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();

        // Upgrade config with any missing entries from defaults
        upgradeConfigFile();

        plugin.reloadConfig();
        config = plugin.getConfig();

        loadPluginSettings();
        loadJoinSwitch();
        loadReadyCountdown();
        loadCooldown();
        loadDisconnect();
        loadTeams();
        loadRespawn();
        loadSpawning();
        loadEconomy();
        loadRewards();
        loadProgression();
        loadUpgrade();
        loadScoreboard();
        loadPersistence();
        loadTeleport();
        loadTemplates();
        loadCombatWorlds();
        loadStarterOptions();
        loadEquipmentPools();
        loadEnemyArchetypes();
        loadMerchants();
        loadFeedback();
    }

    private void loadPluginSettings() {
        language = config.getString("plugin.language", "zh_CN");
        verbose = config.getBoolean("plugin.verbose", false);
    }

    private void loadJoinSwitch() {
        joinEnabled = config.getBoolean("joinSwitch.enabled", true);
        graceEjectSeconds = config.getInt("joinSwitch.graceEjectSeconds", 60);
        graceWarningInterval = config.getInt("joinSwitch.graceWarningInterval", 15);
    }

    private void loadReadyCountdown() {
        countdownSeconds = config.getInt("ready.countdownSeconds", 5);
        showActionBar = config.getBoolean("ready.showActionBar", true);
        showTitle = config.getBoolean("ready.showTitle", true);
        countdownSound = parseSound(config.getString("ready.countdownSound", "BLOCK_NOTE_BLOCK_PLING"));
        countdownSoundPitch = (float) config.getDouble("ready.countdownSoundPitch", 1.0);
        teleportSound = parseSound(config.getString("ready.teleportSound", "ENTITY_ENDERMAN_TELEPORT"));
    }

    private void loadCooldown() {
        deathCooldownSeconds = config.getInt("cooldown.deathCooldownSeconds", 60);
        quitCooldownSeconds = config.getInt("cooldown.quitCooldownSeconds", 30);
        showCooldownBar = config.getBoolean("cooldown.showCooldownBar", true);
        displayUpdateTicks = config.getInt("cooldown.displayUpdateTicks", 20);
    }

    private void loadDisconnect() {
        disconnectGraceSeconds = config.getInt("disconnect.graceSeconds", 300);
        disconnectCheckIntervalTicks = config.getInt("disconnect.checkIntervalTicks", 200);
        notifyTeamOnDisconnect = config.getBoolean("disconnect.notifyTeam", true);
        notifyGraceExpired = config.getBoolean("disconnect.notifyGraceExpired", true);
    }

    private void loadTeams() {
        maxTeamSize = config.getInt("teams.maxSize", 5);
        inviteExpirySeconds = config.getInt("teams.inviteExpirySeconds", 60);
    }

    private void loadRespawn() {
        respawnInvulnerabilitySeconds = config.getInt("respawn.invulnerabilitySeconds", 3);
        canDealDamageDuringInvul = config.getBoolean("respawn.canDealDamageDuringInvul", false);
        pvpEnabled = config.getBoolean("respawn.pvp", false);
    }

    private void loadSpawning() {
        spawnTickInterval = config.getInt("spawning.loop.tickInterval", 20);
        spawningEnabled = config.getBoolean("spawning.loop.enabled", true);
        blockNaturalSpawns = config.getBoolean("spawning.loop.blockNaturalSpawns", true);

        targetMobsPerPlayer = config.getInt("spawning.limits.targetMobsPerPlayer", 10);
        maxSpawnsPerPlayerPerTick = config.getInt("spawning.limits.maxSpawnsPerPlayerPerTick", 3);
        maxSpawnsPerTick = config.getInt("spawning.limits.maxSpawnsPerTick", 20);
        maxCommandsPerTick = config.getInt("spawning.limits.maxCommandsPerTick", 50);
        mobCountRadius = config.getDouble("spawning.limits.mobCountRadius", 30.0);

        minSpawnDistance = config.getDouble("spawning.positioning.minSpawnDistance", 8.0);
        maxSpawnDistance = config.getDouble("spawning.positioning.maxSpawnDistance", 25.0);
        maxSampleAttempts = config.getInt("spawning.positioning.maxSampleAttempts", 10);
        spawnVerticalRange = config.getInt("spawning.positioning.verticalRange", 10);

        levelSamplingRadius = config.getDouble("spawning.levelCalculation.levelSamplingRadius", 50.0);
        avgLevelMultiplier = config.getDouble("spawning.levelCalculation.avgLevelMultiplier", 1.0);
        playerCountMultiplier = config.getDouble("spawning.levelCalculation.playerCountMultiplier", 0.2);
        levelOffset = config.getInt("spawning.levelCalculation.levelOffset", 0);
        minEnemyLevel = config.getInt("spawning.levelCalculation.minLevel", 1);
        maxEnemyLevel = config.getInt("spawning.levelCalculation.maxLevel", 100);
        timeScalingEnabled = config.getBoolean("spawning.levelCalculation.timeScaling.enabled", true);
        timeStepSeconds = config.getInt("spawning.levelCalculation.timeScaling.timeStepSeconds", 60);
        levelPerTimeStep = config.getInt("spawning.levelCalculation.timeScaling.levelPerStep", 1);
    }

    private void loadEconomy() {
        String modeStr = config.getString("economy.mode", "INTERNAL").toUpperCase();
        try {
            economyMode = EconomyMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid economy mode: " + modeStr + ", defaulting to INTERNAL");
            economyMode = EconomyMode.INTERNAL;
        }
        coinNbtTag = config.getString("economy.coin.nbtTag", "vrs_coin");
    }

    private void loadRewards() {
        xpShareEnabled = config.getBoolean("rewards.xpShare.enabled", true);
        xpShareRadius = config.getDouble("rewards.xpShare.radius", 20.0);
        xpSharePercent = config.getDouble("rewards.xpShare.sharePercent", 0.25);

        damageContributionEnabled = config.getBoolean("rewards.damageContribution.enabled", true);
        damageContributionPercent = config.getDouble("rewards.damageContribution.sharePercent", 0.10);

        coinMaterial = parseMaterial(config.getString("economy.coin.material", "EMERALD"));
        coinCustomModelData = config.getInt("economy.coin.customModelData", 0);
        coinDisplayName = config.getString("economy.coin.displayName", "§e金币");
    }

    private void loadProgression() {
        baseXpRequired = config.getInt("progression.baseXpRequired", 100);
        xpPerLevelIncrease = config.getInt("progression.xpPerLevelIncrease", 50);
        xpMultiplierPerLevel = config.getDouble("progression.xpMultiplierPerLevel", 1.1);
        weaponLevelWeight = config.getInt("progression.weaponLevelWeight", 1);
        helmetLevelWeight = config.getInt("progression.helmetLevelWeight", 1);

        overflowEnabled = config.getBoolean("progression.overflow.enabled", true);
        overflowXpPerPermaScore = config.getInt("progression.overflow.xpPerPermaScore", 1000);
        overflowNotifyPlayer = config.getBoolean("progression.overflow.notifyPlayer", true);

        maxLevelBehaviorMode = config.getString("progression.maxLevelBehavior.mode", "GRANT_PERMA_SCORE");
        maxLevelPermaScoreReward = config.getInt("progression.maxLevelBehavior.permaScoreReward", 10);
    }

    private void loadUpgrade() {
        upgradeTimeoutSeconds = config.getInt("upgrade.timeoutSeconds", 30);
        upgradeReminderIntervalSeconds = config.getInt("upgrade.reminderIntervalSeconds", 5);
        upgradeBothMaxPermaReward = config.getInt("upgrade.bothMaxPermaReward", 10);
    }

    private void loadScoreboard() {
        scoreboardEnabled = config.getBoolean("scoreboard.enabled", true);
        scoreboardTitle = config.getString("scoreboard.title", "§6§l武 生");
        scoreboardUpdateInterval = config.getInt("scoreboard.updateInterval", 10);

        permaScoreObjectiveName = config.getString("economy.permaScore.objectiveName", "vrs_perma");
        permaScoreDisplayName = config.getString("economy.permaScore.displayName", "永久积分");
    }

    private void loadPersistence() {
        saveIntervalSeconds = config.getInt("persistence.saveIntervalSeconds", 300);
        saveOnQuit = config.getBoolean("persistence.saveOnQuit", true);
        saveOnRunEnd = config.getBoolean("persistence.saveOnRunEnd", true);
        itemsPath = config.getString("persistence.paths.items", "data/items");
        runtimePath = config.getString("persistence.paths.runtime", "data/runtime");
    }

    private void loadTeleport() {
        lobbyWorld = config.getString("teleport.lobbyWorld", "world");
        lobbyX = config.getDouble("teleport.lobbyX", 0);
        lobbyY = config.getDouble("teleport.lobbyY", 64);
        lobbyZ = config.getDouble("teleport.lobbyZ", 0);
        prepCommand = config.getString("teleport.prepCommand", "");
        enterCommand = config.getString("teleport.enterCommand", "");
        respawnCommand = config.getString("teleport.respawnCommand", "");
        teamSpawnOffsetRange = config.getDouble("teleport.teamSpawnOffsetRange", 2.0);
    }

    private void loadTemplates() {
        escapingEnabled = config.getBoolean("templates.escaping.enabled", true);
        escapeChars = config.getString("templates.escaping.escapeChars", ";&|`$\\");
        missingPlaceholderMode = config.getString("templates.missingPlaceholder.mode", "ERROR");
    }

    private void loadCombatWorlds() {
        combatWorlds = new ArrayList<>();
        List<Map<?, ?>> worldList = config.getMapList("worlds.list");

        for (Map<?, ?> worldMap : worldList) {
            CombatWorldConfig world = new CombatWorldConfig();
            world.name = (String) worldMap.get("name");
            Object displayNameVal = worldMap.get("displayName");
            world.displayName = displayNameVal != null ? (String) displayNameVal : world.name;
            Object enabledVal = worldMap.get("enabled");
            world.enabled = enabledVal != null ? (Boolean) enabledVal : true;
            Object weightVal = worldMap.get("weight");
            world.weight = weightVal != null ? ((Number) weightVal).doubleValue() : 1.0;

            @SuppressWarnings("unchecked")
            Map<String, Number> bounds = (Map<String, Number>) worldMap.get("spawnBounds");
            if (bounds != null) {
                world.minX = bounds.getOrDefault("minX", -500).doubleValue();
                world.maxX = bounds.getOrDefault("maxX", 500).doubleValue();
                world.minZ = bounds.getOrDefault("minZ", -500).doubleValue();
                world.maxZ = bounds.getOrDefault("maxZ", 500).doubleValue();
            }

            combatWorlds.add(world);
        }
    }

    private void loadStarterOptions() {
        requireWeaponFirst = config.getBoolean("starterSelection.requireWeaponFirst", true);
        autoOpenHelmetGui = config.getBoolean("starterSelection.autoOpenHelmetGui", true);

        starterWeapons = loadStarterList("starterSelection.weapons");
        starterHelmets = loadStarterList("starterSelection.helmets");
    }

    private List<StarterOptionConfig> loadStarterList(String path) {
        List<StarterOptionConfig> options = new ArrayList<>();
        List<Map<?, ?>> list = config.getMapList(path);

        for (Map<?, ?> map : list) {
            StarterOptionConfig opt = new StarterOptionConfig();
            opt.optionId = (String) map.get("optionId");
            opt.displayName = (String) map.get("displayName");
            opt.templateId = (String) map.get("templateId");
            opt.group = (String) map.get("group");
            Object levelVal = map.get("level");
            opt.level = levelVal != null ? ((Number) levelVal).intValue() : 1;

            @SuppressWarnings("unchecked")
            Map<String, Object> displayItem = (Map<String, Object>) map.get("displayItem");
            if (displayItem != null) {
                opt.displayMaterial = parseMaterial((String) displayItem.get("material"));
                opt.displayItemName = (String) displayItem.get("name");
                @SuppressWarnings("unchecked")
                List<String> lore = (List<String>) displayItem.get("lore");
                opt.displayItemLore = lore != null ? lore : Collections.emptyList();
            }

            options.add(opt);
        }

        return options;
    }

    private void loadEquipmentPools() {
        weaponGroups = loadEquipmentGroups("equipmentPools.weapons");
        helmetGroups = loadEquipmentGroups("equipmentPools.helmets");
    }

    private Map<String, EquipmentGroupConfig> loadEquipmentGroups(String path) {
        Map<String, EquipmentGroupConfig> groups = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null) return groups;

        for (String groupId : section.getKeys(false)) {
            ConfigurationSection groupSection = section.getConfigurationSection(groupId);
            if (groupSection == null) continue;

            EquipmentGroupConfig group = new EquipmentGroupConfig();
            group.groupId = groupId;
            group.displayName = groupSection.getString("displayName", groupId);
            group.levelItems = new HashMap<>();

            ConfigurationSection levelsSection = groupSection.getConfigurationSection("levels");
            if (levelsSection != null) {
                for (String levelKey : levelsSection.getKeys(false)) {
                    int level = Integer.parseInt(levelKey);
                    List<String> items = levelsSection.getStringList(levelKey);
                    group.levelItems.put(level, items);
                }
            }

            groups.put(groupId, group);
        }

        return groups;
    }

    private void loadEnemyArchetypes() {
        enemyArchetypes = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("spawning.archetypes");

        if (section == null) return;

        for (String archetypeId : section.getKeys(false)) {
            ConfigurationSection archSection = section.getConfigurationSection(archetypeId);
            if (archSection == null) continue;

            EnemyArchetypeConfig arch = new EnemyArchetypeConfig();
            arch.archetypeId = archetypeId;
            arch.enemyType = archSection.getString("enemyType", "minecraft:zombie");
            arch.weight = archSection.getDouble("weight", 1.0);
            arch.spawnCommands = archSection.getStringList("spawnCommands");

            // Load minSpawnLevel (level gating)
            arch.minSpawnLevel = archSection.getInt("minSpawnLevel", 1);

            ConfigurationSection rewards = archSection.getConfigurationSection("rewards");
            if (rewards != null) {
                // Check for new format (xpAmount) vs legacy format (xpBase)
                if (rewards.contains("xpAmount")) {
                    // New chance-based format
                    arch.xpAmount = rewards.getInt("xpAmount", 10);
                    arch.xpChance = rewards.getDouble("xpChance", 1.0);
                    arch.coinAmount = rewards.getInt("coinAmount", 1);
                    arch.coinChance = rewards.getDouble("coinChance", 1.0);
                    arch.permaScoreAmount = rewards.getInt("permaScoreAmount", 1);
                    arch.permaScoreChance = rewards.getDouble("permaScoreChance", 0.01);
                } else if (rewards.contains("xpBase")) {
                    // Legacy format - migrate to new format
                    int xpBase = rewards.getInt("xpBase", 10);
                    int coinBase = rewards.getInt("coinBase", 1);
                    double permaChance = rewards.getDouble("permaScoreChance", 0.01);

                    // Migrate: use base values as fixed amounts with 100% chance
                    arch.xpAmount = xpBase;
                    arch.xpChance = 1.0;
                    arch.coinAmount = coinBase;
                    arch.coinChance = 1.0;
                    arch.permaScoreAmount = 1;
                    arch.permaScoreChance = permaChance;

                    plugin.getLogger().info("Migrated archetype '" + archetypeId + "' to new reward format");
                }
            }

            enemyArchetypes.put(archetypeId, arch);
        }
    }

    private void loadFeedback() {
        // Reward display settings
        rewardDisplayMode = config.getString("feedback.rewards.displayMode", "ACTIONBAR").toUpperCase();
        rewardStackingEnabled = config.getBoolean("feedback.rewards.stacking.enabled", true);
        rewardStackingTimeoutSeconds = config.getInt("feedback.rewards.stacking.timeoutSeconds", 3);

        // Upgrade reminder settings
        upgradeReminderDisplayMode = config.getString("feedback.upgradeReminder.displayMode", "CHAT").toUpperCase();
        upgradeReminderFlashIntervalTicks = config.getInt("feedback.upgradeReminder.flashIntervalTicks", 10);

        // Sound effects (null = disabled)
        soundXpGained = parseSoundConfig(config.getString("feedback.sounds.xpGained", "minecraft:entity.experience_orb.pickup 0.5 1.2"));
        soundCoinGained = parseSoundConfig(config.getString("feedback.sounds.coinGained", "minecraft:entity.item.pickup 0.6 1.0"));
        soundPermaScoreGained = parseSoundConfig(config.getString("feedback.sounds.permaScoreGained", "minecraft:entity.player.levelup 0.8 1.0"));
        soundUpgradeAvailable = parseSoundConfig(config.getString("feedback.sounds.upgradeAvailable", "minecraft:block.note_block.pling 1.0 1.2"));
        soundUpgradeSelected = parseSoundConfig(config.getString("feedback.sounds.upgradeSelected", "minecraft:entity.player.levelup 1.0 1.0"));
        soundKillReward = parseSoundConfig(config.getString("feedback.sounds.killReward", ""));
        // Game event sounds
        soundCountdownTick = parseSoundConfig(config.getString("feedback.sounds.countdownTick", "minecraft:block.note_block.pling 0.8 1.0"));
        soundTeleport = parseSoundConfig(config.getString("feedback.sounds.teleport", "minecraft:entity.enderman.teleport 0.8 1.0"));
        soundDeath = parseSoundConfig(config.getString("feedback.sounds.death", "minecraft:entity.wither.death 0.5 1.0"));
        soundRunStart = parseSoundConfig(config.getString("feedback.sounds.runStart", "minecraft:entity.player.levelup 1.0 1.0"));
    }

    private void loadMerchants() {
        merchantsEnabled = config.getBoolean("merchants.enabled", true);
        merchantSpawnInterval = config.getInt("merchants.wandering.spawnIntervalSeconds", 120);
        merchantLifetime = config.getInt("merchants.wandering.stayTime.maxSeconds", 120);
        merchantMinDistance = config.getDouble("merchants.wandering.distance.min", 20.0);
        merchantMaxDistance = config.getDouble("merchants.wandering.distance.max", 50.0);
        merchantHeadItemCycleInterval = config.getInt("merchants.display.headItemCycleIntervalTicks", 200);
        merchantSpawnChance = config.getDouble("merchants.wandering.spawnChance", 0.5);
        merchantLimited = config.getBoolean("merchants.stock.limited", true);
        merchantMinStaySeconds = config.getInt("merchants.wandering.stayTime.minSeconds", 60);
        merchantMaxStaySeconds = config.getInt("merchants.wandering.stayTime.maxSeconds", 120);
        merchantSpawnParticles = config.getBoolean("merchants.wandering.particles.spawn", true);
        merchantDespawnParticles = config.getBoolean("merchants.wandering.particles.despawn", true);
        merchantMinItems = config.getInt("merchants.stock.minItems", 3);
        merchantMaxItems = config.getInt("merchants.stock.maxItems", 6);
        merchantShowAllItems = config.getBoolean("merchants.stock.showAllItems", false);
        merchantRotationSpeed = (float) config.getDouble("merchants.display.rotationSpeed", 3.0);
        merchantBobHeight = config.getDouble("merchants.display.bobHeight", 0.15);
        merchantBobSpeed = config.getDouble("merchants.display.bobSpeed", 0.01);
        wanderingMerchantPoolId = config.getString("merchants.wandering.poolId", "");
        wanderingMerchantType = config.getString("merchants.wandering.type", "single");
        wanderingMerchantMaxCount = config.getInt("merchants.wandering.maxCount", 3);
    }

    // ==================== Utility Methods ====================

    private Sound parseSound(String name) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + name + ", using default");
            return Sound.BLOCK_NOTE_BLOCK_PLING;
        }
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material: " + name + ", using STONE");
            return Material.STONE;
        }
    }

    /**
     * Parses sound config from "sound_name volume pitch" format.
     * Returns null if empty or invalid.
     */
    private SoundConfig parseSoundConfig(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            String[] parts = value.trim().split("\\s+");
            String sound = parts[0];
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            return new SoundConfig(sound, volume, pitch);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid sound config: " + value + ", sound will be disabled");
            return null;
        }
    }

    // ==================== Getters ====================

    public String getLanguage() { return language; }
    public boolean isVerbose() { return verbose; }

    public boolean isJoinEnabled() { return joinEnabled; }
    public void setJoinEnabled(boolean enabled) { this.joinEnabled = enabled; }
    public int getGraceEjectSeconds() { return graceEjectSeconds; }
    public int getGraceWarningInterval() { return graceWarningInterval; }

    public int getCountdownSeconds() { return countdownSeconds; }
    public boolean isShowActionBar() { return showActionBar; }
    public boolean isShowTitle() { return showTitle; }
    public Sound getCountdownSound() { return countdownSound; }
    public float getCountdownSoundPitch() { return countdownSoundPitch; }
    public Sound getTeleportSound() { return teleportSound; }

    public int getDeathCooldownSeconds() { return deathCooldownSeconds; }
    public long getDeathCooldownMs() { return deathCooldownSeconds * 1000L; }
    public int getQuitCooldownSeconds() { return quitCooldownSeconds; }
    public boolean isShowCooldownBar() { return showCooldownBar; }
    public int getDisplayUpdateTicks() { return displayUpdateTicks; }

    public int getDisconnectGraceSeconds() { return disconnectGraceSeconds; }
    public long getDisconnectGraceMs() { return disconnectGraceSeconds * 1000L; }
    public int getDisconnectCheckIntervalTicks() { return disconnectCheckIntervalTicks; }
    public boolean isNotifyTeamOnDisconnect() { return notifyTeamOnDisconnect; }
    public boolean isNotifyGraceExpired() { return notifyGraceExpired; }

    public int getMaxTeamSize() { return maxTeamSize; }
    public int getInviteExpirySeconds() { return inviteExpirySeconds; }
    public long getInviteExpiryMs() { return inviteExpirySeconds * 1000L; }

    public int getRespawnInvulnerabilitySeconds() { return respawnInvulnerabilitySeconds; }
    public long getRespawnInvulnerabilityMs() { return respawnInvulnerabilitySeconds * 1000L; }
    public boolean isCanDealDamageDuringInvul() { return canDealDamageDuringInvul; }
    public boolean isPvpEnabled() { return pvpEnabled; }

    public int getSpawnTickInterval() { return spawnTickInterval; }
    public boolean isSpawningEnabled() { return spawningEnabled; }
    public boolean isBlockNaturalSpawns() { return blockNaturalSpawns; }
    public int getTargetMobsPerPlayer() { return targetMobsPerPlayer; }
    public int getMaxSpawnsPerPlayerPerTick() { return maxSpawnsPerPlayerPerTick; }
    public int getMaxSpawnsPerTick() { return maxSpawnsPerTick; }
    public int getMaxCommandsPerTick() { return maxCommandsPerTick; }
    public double getMobCountRadius() { return mobCountRadius; }

    public double getMinSpawnDistance() { return minSpawnDistance; }
    public double getMaxSpawnDistance() { return maxSpawnDistance; }
    public int getMaxSampleAttempts() { return maxSampleAttempts; }
    public int getSpawnVerticalRange() { return spawnVerticalRange; }

    public double getLevelSamplingRadius() { return levelSamplingRadius; }
    public double getAvgLevelMultiplier() { return avgLevelMultiplier; }
    public double getPlayerCountMultiplier() { return playerCountMultiplier; }
    public int getLevelOffset() { return levelOffset; }
    public int getMinEnemyLevel() { return minEnemyLevel; }
    public int getMaxEnemyLevel() { return maxEnemyLevel; }
    public boolean isTimeScalingEnabled() { return timeScalingEnabled; }
    public int getTimeStepSeconds() { return timeStepSeconds; }
    public int getLevelPerTimeStep() { return levelPerTimeStep; }

    public EconomyMode getEconomyMode() { return economyMode; }
    public String getCoinNbtTag() { return coinNbtTag; }

    public boolean isXpShareEnabled() { return xpShareEnabled; }
    public double getXpShareRadius() { return xpShareRadius; }
    public double getXpSharePercent() { return xpSharePercent; }
    public boolean isDamageContributionEnabled() { return damageContributionEnabled; }
    public double getDamageContributionPercent() { return damageContributionPercent; }
    public Material getCoinMaterial() { return coinMaterial; }
    public int getCoinCustomModelData() { return coinCustomModelData; }
    public String getCoinDisplayName() { return coinDisplayName; }

    public int getBaseXpRequired() { return baseXpRequired; }
    public int getXpPerLevelIncrease() { return xpPerLevelIncrease; }
    public double getXpMultiplierPerLevel() { return xpMultiplierPerLevel; }
    public int getWeaponLevelWeight() { return weaponLevelWeight; }
    public int getHelmetLevelWeight() { return helmetLevelWeight; }
    public boolean isOverflowEnabled() { return overflowEnabled; }
    public int getOverflowXpPerPermaScore() { return overflowXpPerPermaScore; }
    public boolean isOverflowNotifyPlayer() { return overflowNotifyPlayer; }
    public String getMaxLevelBehaviorMode() { return maxLevelBehaviorMode; }
    public int getMaxLevelPermaScoreReward() { return maxLevelPermaScoreReward; }

    public int getUpgradeTimeoutSeconds() { return upgradeTimeoutSeconds; }
    public long getUpgradeTimeoutMs() { return upgradeTimeoutSeconds * 1000L; }
    public int getUpgradeReminderIntervalSeconds() { return upgradeReminderIntervalSeconds; }
    public int getUpgradeBothMaxPermaReward() { return upgradeBothMaxPermaReward; }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public String getScoreboardTitle() { return scoreboardTitle; }
    public int getScoreboardUpdateInterval() { return scoreboardUpdateInterval; }
    public String getPermaScoreObjectiveName() { return permaScoreObjectiveName; }
    public String getPermaScoreDisplayName() { return permaScoreDisplayName; }

    public int getSaveIntervalSeconds() { return saveIntervalSeconds; }
    public boolean isSaveOnQuit() { return saveOnQuit; }
    public boolean isSaveOnRunEnd() { return saveOnRunEnd; }
    public String getItemsPath() { return itemsPath; }
    public String getRuntimePath() { return runtimePath; }

    public String getLobbyWorld() { return lobbyWorld; }
    public double getLobbyX() { return lobbyX; }
    public double getLobbyY() { return lobbyY; }
    public double getLobbyZ() { return lobbyZ; }
    public org.bukkit.Location getLobbyLocation() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(lobbyWorld);
        if (world == null) world = org.bukkit.Bukkit.getWorlds().get(0);
        return new org.bukkit.Location(world, lobbyX, lobbyY, lobbyZ);
    }
    public String getPrepCommand() { return prepCommand; }
    public String getEnterCommand() { return enterCommand; }
    public String getRespawnCommand() { return respawnCommand; }
    public double getTeamSpawnOffsetRange() { return teamSpawnOffsetRange; }

    public boolean isEscapingEnabled() { return escapingEnabled; }
    public String getEscapeChars() { return escapeChars; }
    public String getMissingPlaceholderMode() { return missingPlaceholderMode; }

    public List<CombatWorldConfig> getCombatWorlds() { return combatWorlds; }
    public boolean isRequireWeaponFirst() { return requireWeaponFirst; }
    public boolean isAutoOpenHelmetGui() { return autoOpenHelmetGui; }
    public List<StarterOptionConfig> getStarterWeapons() { return starterWeapons; }
    public List<StarterOptionConfig> getStarterHelmets() { return starterHelmets; }
    public Map<String, EquipmentGroupConfig> getWeaponGroups() { return weaponGroups; }
    public Map<String, EquipmentGroupConfig> getHelmetGroups() { return helmetGroups; }
    public Map<String, EnemyArchetypeConfig> getEnemyArchetypes() { return enemyArchetypes; }

    public boolean isMerchantsEnabled() { return merchantsEnabled; }
    public int getMerchantSpawnInterval() { return merchantSpawnInterval; }
    public int getMerchantLifetime() { return merchantLifetime; }
    public double getMerchantMinDistance() { return merchantMinDistance; }
    public double getMerchantMaxDistance() { return merchantMaxDistance; }
    public int getMerchantHeadItemCycleInterval() { return merchantHeadItemCycleInterval; }
    public double getMerchantSpawnChance() { return merchantSpawnChance; }
    public boolean isMerchantLimited() { return merchantLimited; }
    public int getMerchantMinStaySeconds() { return merchantMinStaySeconds; }
    public int getMerchantMaxStaySeconds() { return merchantMaxStaySeconds; }
    public boolean isMerchantSpawnParticles() { return merchantSpawnParticles; }
    public boolean isMerchantDespawnParticles() { return merchantDespawnParticles; }
    public int getMerchantMinItems() { return merchantMinItems; }
    public int getMerchantMaxItems() { return merchantMaxItems; }
    public boolean isMerchantShowAllItems() { return merchantShowAllItems; }
    public float getMerchantRotationSpeed() { return merchantRotationSpeed; }
    public double getMerchantBobHeight() { return merchantBobHeight; }
    public double getMerchantBobSpeed() { return merchantBobSpeed; }
    public String getWanderingMerchantPoolId() { return wanderingMerchantPoolId; }
    public String getWanderingMerchantType() { return wanderingMerchantType; }
    public int getWanderingMerchantMaxCount() { return wanderingMerchantMaxCount; }

    // Feedback getters
    public String getRewardDisplayMode() { return rewardDisplayMode; }
    public boolean isRewardStackingEnabled() { return rewardStackingEnabled; }
    public int getRewardStackingTimeoutSeconds() { return rewardStackingTimeoutSeconds; }
    public long getRewardStackingTimeoutMs() { return rewardStackingTimeoutSeconds * 1000L; }
    public String getUpgradeReminderDisplayMode() { return upgradeReminderDisplayMode; }
    public int getUpgradeReminderFlashIntervalTicks() { return upgradeReminderFlashIntervalTicks; }

    // Sound getters (nullable)
    public SoundConfig getSoundXpGained() { return soundXpGained; }
    public SoundConfig getSoundCoinGained() { return soundCoinGained; }
    public SoundConfig getSoundPermaScoreGained() { return soundPermaScoreGained; }
    public SoundConfig getSoundUpgradeAvailable() { return soundUpgradeAvailable; }
    public SoundConfig getSoundUpgradeSelected() { return soundUpgradeSelected; }
    public SoundConfig getSoundKillReward() { return soundKillReward; }
    // Game event sound getters
    public SoundConfig getSoundCountdownTick() { return soundCountdownTick; }
    public SoundConfig getSoundTeleport() { return soundTeleport; }
    public SoundConfig getSoundDeath() { return soundDeath; }
    public SoundConfig getSoundRunStart() { return soundRunStart; }

    // Sound getters as strings (for command interface)
    public String getSoundXpGainedStr() { return formatSoundConfig(soundXpGained); }
    public String getSoundCoinGainedStr() { return formatSoundConfig(soundCoinGained); }
    public String getSoundPermaScoreGainedStr() { return formatSoundConfig(soundPermaScoreGained); }
    public String getSoundUpgradeAvailableStr() { return formatSoundConfig(soundUpgradeAvailable); }
    public String getSoundUpgradeSelectedStr() { return formatSoundConfig(soundUpgradeSelected); }
    public String getSoundKillRewardStr() { return formatSoundConfig(soundKillReward); }
    public String getSoundCountdownTickStr() { return formatSoundConfig(soundCountdownTick); }
    public String getSoundTeleportStr() { return formatSoundConfig(soundTeleport); }
    public String getSoundDeathStr() { return formatSoundConfig(soundDeath); }
    public String getSoundRunStartStr() { return formatSoundConfig(soundRunStart); }

    /**
     * Formats a SoundConfig back to string format "sound volume pitch".
     */
    private String formatSoundConfig(SoundConfig sound) {
        if (sound == null || sound.sound() == null || sound.sound().isEmpty()) {
            return "";
        }
        return sound.sound() + " " + sound.volume() + " " + sound.pitch();
    }

    // ==================== Runtime Update Methods ====================

    /**
     * Updates weapon groups from AdminConfigService. Called when admin commands modify data.
     */
    public void updateWeaponGroups(Map<String, EquipmentGroupConfig> groups) {
        this.weaponGroups.clear();
        this.weaponGroups.putAll(groups);
    }

    /**
     * Updates helmet groups from AdminConfigService. Called when admin commands modify data.
     */
    public void updateHelmetGroups(Map<String, EquipmentGroupConfig> groups) {
        this.helmetGroups.clear();
        this.helmetGroups.putAll(groups);
    }

    /**
     * Updates enemy archetypes from AdminConfigService. Called when admin commands modify data.
     */
    public void updateEnemyArchetypes(Map<String, EnemyArchetypeConfig> archetypes) {
        this.enemyArchetypes.clear();
        this.enemyArchetypes.putAll(archetypes);
    }

    /**
     * Updates combat worlds from AdminConfigService. Called when admin commands modify data.
     */
    public void updateCombatWorlds(List<CombatWorldConfig> worlds) {
        this.combatWorlds.clear();
        this.combatWorlds.addAll(worlds);
    }

    /**
     * Updates starter options from AdminConfigService. Called when admin commands modify data.
     */
    public void updateStarters(List<StarterOptionConfig> weapons, List<StarterOptionConfig> helmets) {
        this.starterWeapons.clear();
        this.starterWeapons.addAll(weapons);
        this.starterHelmets.clear();
        this.starterHelmets.addAll(helmets);
    }

    // ==================== Runtime Setters ====================

    // Teleport settings
    public void setLobbyWorld(String world) { this.lobbyWorld = world; }
    public void setLobbyX(double x) { this.lobbyX = x; }
    public void setLobbyY(double y) { this.lobbyY = y; }
    public void setLobbyZ(double z) { this.lobbyZ = z; }
    public void setPrepCommand(String command) { this.prepCommand = command; }
    public void setEnterCommand(String command) { this.enterCommand = command; }
    public void setRespawnCommand(String command) { this.respawnCommand = command; }
    public void setTeamSpawnOffsetRange(double range) { this.teamSpawnOffsetRange = range; }

    // Timings
    public void setDeathCooldownSeconds(int seconds) { this.deathCooldownSeconds = seconds; }
    public void setRespawnInvulnerabilitySeconds(int seconds) { this.respawnInvulnerabilitySeconds = seconds; }
    public void setDisconnectGraceSeconds(int seconds) { this.disconnectGraceSeconds = seconds; }
    public void setCountdownSeconds(int seconds) { this.countdownSeconds = seconds; }

    // Spawning
    public void setMinSpawnDistance(double distance) { this.minSpawnDistance = distance; }
    public void setMaxSpawnDistance(double distance) { this.maxSpawnDistance = distance; }
    public void setMaxSampleAttempts(int attempts) { this.maxSampleAttempts = attempts; }
    public void setSpawnTickInterval(int interval) { this.spawnTickInterval = interval; }
    public void setTargetMobsPerPlayer(int target) { this.targetMobsPerPlayer = target; }
    public void setMaxSpawnsPerTick(int max) { this.maxSpawnsPerTick = max; }

    // Rewards
    public void setXpShareEnabled(boolean enabled) { this.xpShareEnabled = enabled; }
    public void setXpShareRadius(double radius) { this.xpShareRadius = radius; }
    public void setXpSharePercent(double percent) { this.xpSharePercent = percent; }
    public void setDamageContributionEnabled(boolean enabled) { this.damageContributionEnabled = enabled; }
    public void setDamageContributionPercent(double percent) { this.damageContributionPercent = percent; }
    public void setOverflowEnabled(boolean enabled) { this.overflowEnabled = enabled; }
    public void setOverflowXpPerPermaScore(int xp) { this.overflowXpPerPermaScore = xp; }
    public void setOverflowNotifyPlayer(boolean notify) { this.overflowNotifyPlayer = notify; }
    public void setMaxLevelPermaScoreReward(int reward) { this.maxLevelPermaScoreReward = reward; }
    public void setPermaScoreDisplayName(String name) { this.permaScoreDisplayName = name; }

    // Progression
    public void setBaseXpRequired(int value) { this.baseXpRequired = value; }
    public void setXpPerLevelIncrease(int value) { this.xpPerLevelIncrease = value; }
    public void setXpMultiplierPerLevel(double value) { this.xpMultiplierPerLevel = value; }
    public void setWeaponLevelWeight(int value) { this.weaponLevelWeight = value; }
    public void setHelmetLevelWeight(int value) { this.helmetLevelWeight = value; }

    // Teams
    public void setMaxTeamSize(int size) { this.maxTeamSize = size; }
    public void setInviteExpirySeconds(int seconds) { this.inviteExpirySeconds = seconds; }

    // Merchants
    public void setMerchantsEnabled(boolean enabled) { this.merchantsEnabled = enabled; }
    public void setMerchantSpawnInterval(int interval) { this.merchantSpawnInterval = interval; }
    public void setMerchantLifetime(int lifetime) { this.merchantLifetime = lifetime; }
    public void setMerchantMinDistance(double distance) { this.merchantMinDistance = distance; }
    public void setMerchantMaxDistance(double distance) { this.merchantMaxDistance = distance; }
    public void setMerchantHeadItemCycleInterval(int interval) { this.merchantHeadItemCycleInterval = interval; }
    public void setMerchantSpawnChance(double chance) { this.merchantSpawnChance = chance; }
    public void setMerchantLimited(boolean limited) { this.merchantLimited = limited; }
    public void setMerchantMinStaySeconds(int seconds) { this.merchantMinStaySeconds = seconds; }
    public void setMerchantMaxStaySeconds(int seconds) { this.merchantMaxStaySeconds = seconds; }
    public void setMerchantSpawnParticles(boolean spawn) { this.merchantSpawnParticles = spawn; }
    public void setMerchantDespawnParticles(boolean despawn) { this.merchantDespawnParticles = despawn; }
    public void setMerchantMinItems(int min) { this.merchantMinItems = min; }
    public void setMerchantMaxItems(int max) { this.merchantMaxItems = max; }
    public void setMerchantShowAllItems(boolean showAll) { this.merchantShowAllItems = showAll; }
    public void setMerchantRotationSpeed(float speed) { this.merchantRotationSpeed = speed; }
    public void setMerchantBobHeight(double height) { this.merchantBobHeight = height; }
    public void setMerchantBobSpeed(double speed) { this.merchantBobSpeed = speed; }
    public void setWanderingMerchantPoolId(String poolId) { this.wanderingMerchantPoolId = poolId; }
    public void setWanderingMerchantType(String type) { this.wanderingMerchantType = type; }
    public void setWanderingMerchantMaxCount(int maxCount) { this.wanderingMerchantMaxCount = maxCount; }

    // Upgrade
    public void setUpgradeTimeoutSeconds(int seconds) { this.upgradeTimeoutSeconds = seconds; }
    public void setUpgradeReminderIntervalSeconds(int seconds) { this.upgradeReminderIntervalSeconds = seconds; }

    // Scoreboard
    public void setScoreboardEnabled(boolean enabled) { this.scoreboardEnabled = enabled; }
    public void setScoreboardTitle(String title) { this.scoreboardTitle = title; }
    public void setScoreboardUpdateInterval(int interval) { this.scoreboardUpdateInterval = interval; }

    // Feedback
    public void setRewardDisplayMode(String mode) { this.rewardDisplayMode = mode.toUpperCase(); }
    public void setRewardStackingEnabled(boolean enabled) { this.rewardStackingEnabled = enabled; }
    public void setRewardStackingTimeoutSeconds(int seconds) { this.rewardStackingTimeoutSeconds = seconds; }
    public void setUpgradeReminderDisplayMode(String mode) { this.upgradeReminderDisplayMode = mode.toUpperCase(); }
    public void setUpgradeReminderFlashIntervalTicks(int ticks) { this.upgradeReminderFlashIntervalTicks = ticks; }

    // Sound setters (using string format "sound volume pitch")
    public void setSoundXpGained(String sound) { this.soundXpGained = parseSoundConfig(sound); }
    public void setSoundCoinGained(String sound) { this.soundCoinGained = parseSoundConfig(sound); }
    public void setSoundPermaScoreGained(String sound) { this.soundPermaScoreGained = parseSoundConfig(sound); }
    public void setSoundUpgradeAvailable(String sound) { this.soundUpgradeAvailable = parseSoundConfig(sound); }
    public void setSoundUpgradeSelected(String sound) { this.soundUpgradeSelected = parseSoundConfig(sound); }
    public void setSoundKillReward(String sound) { this.soundKillReward = parseSoundConfig(sound); }
    public void setSoundCountdownTick(String sound) { this.soundCountdownTick = parseSoundConfig(sound); }
    public void setSoundTeleport(String sound) { this.soundTeleport = parseSoundConfig(sound); }
    public void setSoundDeath(String sound) { this.soundDeath = parseSoundConfig(sound); }
    public void setSoundRunStart(String sound) { this.soundRunStart = parseSoundConfig(sound); }

    // ==================== Config Save ====================

    /**
     * Saves current configuration values to config.yml.
     * Called after runtime changes via /vrs admin config set.
     */
    public void saveConfig() {
        // Teleport settings
        config.set("teleport.lobbyWorld", lobbyWorld);
        config.set("teleport.lobbyX", lobbyX);
        config.set("teleport.lobbyY", lobbyY);
        config.set("teleport.lobbyZ", lobbyZ);
        config.set("teleport.prepCommand", prepCommand);
        config.set("teleport.enterCommand", enterCommand);
        config.set("teleport.respawnCommand", respawnCommand);
        config.set("teleport.teamSpawnOffsetRange", teamSpawnOffsetRange);

        // Timings
        config.set("cooldown.deathCooldownSeconds", deathCooldownSeconds);
        config.set("respawn.invulnerabilitySeconds", respawnInvulnerabilitySeconds);
        config.set("disconnect.graceSeconds", disconnectGraceSeconds);
        config.set("ready.countdownSeconds", countdownSeconds);

        // Spawning
        config.set("spawning.positioning.minSpawnDistance", minSpawnDistance);
        config.set("spawning.positioning.maxSpawnDistance", maxSpawnDistance);
        config.set("spawning.positioning.maxSampleAttempts", maxSampleAttempts);
        config.set("spawning.loop.tickInterval", spawnTickInterval);
        config.set("spawning.limits.targetMobsPerPlayer", targetMobsPerPlayer);
        config.set("spawning.limits.maxSpawnsPerTick", maxSpawnsPerTick);

        // Rewards
        config.set("rewards.xpShare.enabled", xpShareEnabled);
        config.set("rewards.xpShare.radius", xpShareRadius);
        config.set("rewards.xpShare.sharePercent", xpSharePercent);
        config.set("rewards.damageContribution.enabled", damageContributionEnabled);
        config.set("rewards.damageContribution.sharePercent", damageContributionPercent);
        config.set("progression.overflow.enabled", overflowEnabled);
        config.set("progression.overflow.xpPerPermaScore", overflowXpPerPermaScore);
        config.set("progression.overflow.notifyPlayer", overflowNotifyPlayer);
        config.set("progression.maxLevelBehavior.permaScoreReward", maxLevelPermaScoreReward);
        config.set("economy.permaScore.displayName", permaScoreDisplayName);

        // Progression
        config.set("progression.baseXpRequired", baseXpRequired);
        config.set("progression.xpPerLevelIncrease", xpPerLevelIncrease);
        config.set("progression.xpMultiplierPerLevel", xpMultiplierPerLevel);
        config.set("progression.weaponLevelWeight", weaponLevelWeight);
        config.set("progression.helmetLevelWeight", helmetLevelWeight);

        // Teams
        config.set("teams.maxSize", maxTeamSize);
        config.set("teams.inviteExpirySeconds", inviteExpirySeconds);

        // Merchants
        config.set("merchants.enabled", merchantsEnabled);
        config.set("merchants.wandering.spawnIntervalSeconds", merchantSpawnInterval);
        config.set("merchants.wandering.stayTime.maxSeconds", merchantLifetime);
        config.set("merchants.wandering.distance.min", merchantMinDistance);
        config.set("merchants.wandering.distance.max", merchantMaxDistance);
        config.set("merchants.display.headItemCycleIntervalTicks", merchantHeadItemCycleInterval);
        config.set("merchants.wandering.spawnChance", merchantSpawnChance);
        config.set("merchants.stock.limited", merchantLimited);
        config.set("merchants.wandering.stayTime.minSeconds", merchantMinStaySeconds);
        config.set("merchants.wandering.particles.spawn", merchantSpawnParticles);
        config.set("merchants.wandering.particles.despawn", merchantDespawnParticles);
        config.set("merchants.stock.minItems", merchantMinItems);
        config.set("merchants.stock.maxItems", merchantMaxItems);
        config.set("merchants.stock.showAllItems", merchantShowAllItems);
        config.set("merchants.display.rotationSpeed", merchantRotationSpeed);
        config.set("merchants.display.bobHeight", merchantBobHeight);
        config.set("merchants.display.bobSpeed", merchantBobSpeed);
        config.set("merchants.wandering.poolId", wanderingMerchantPoolId);
        config.set("merchants.wandering.type", wanderingMerchantType);
        config.set("merchants.wandering.maxCount", wanderingMerchantMaxCount);

        // Upgrade
        config.set("upgrade.timeoutSeconds", upgradeTimeoutSeconds);
        config.set("upgrade.reminderIntervalSeconds", upgradeReminderIntervalSeconds);

        // Scoreboard
        config.set("scoreboard.enabled", scoreboardEnabled);
        config.set("scoreboard.title", scoreboardTitle);
        config.set("scoreboard.updateInterval", scoreboardUpdateInterval);

        // Feedback
        config.set("feedback.rewards.displayMode", rewardDisplayMode);
        config.set("feedback.rewards.stacking.enabled", rewardStackingEnabled);
        config.set("feedback.rewards.stacking.timeoutSeconds", rewardStackingTimeoutSeconds);
        config.set("feedback.upgradeReminder.displayMode", upgradeReminderDisplayMode);
        config.set("feedback.upgradeReminder.flashIntervalTicks", upgradeReminderFlashIntervalTicks);

        // Sounds
        config.set("feedback.sounds.xpGained", formatSoundConfig(soundXpGained));
        config.set("feedback.sounds.coinGained", formatSoundConfig(soundCoinGained));
        config.set("feedback.sounds.permaScoreGained", formatSoundConfig(soundPermaScoreGained));
        config.set("feedback.sounds.upgradeAvailable", formatSoundConfig(soundUpgradeAvailable));
        config.set("feedback.sounds.upgradeSelected", formatSoundConfig(soundUpgradeSelected));
        config.set("feedback.sounds.killReward", formatSoundConfig(soundKillReward));
        config.set("feedback.sounds.countdownTick", formatSoundConfig(soundCountdownTick));
        config.set("feedback.sounds.teleport", formatSoundConfig(soundTeleport));
        config.set("feedback.sounds.death", formatSoundConfig(soundDeath));
        config.set("feedback.sounds.runStart", formatSoundConfig(soundRunStart));

        try {
            config.save(new java.io.File(plugin.getDataFolder(), "config.yml"));
            plugin.getLogger().info("Configuration saved to config.yml");
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save config.yml: " + e.getMessage());
        }
    }

    // ==================== Config Upgrade ====================

    /**
     * Upgrades config.yml with any missing entries from defaults.
     * Called before loading to ensure all new config keys are available.
     */
    private void upgradeConfigFile() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return; // Will be created by saveDefaultConfig
        }

        ConfigUpgradeService upgradeService = new ConfigUpgradeService(plugin.getLogger());
        try (java.io.InputStream defaultStream = plugin.getResource("config.yml")) {
            int added = upgradeService.upgradeFile(configFile, defaultStream);
            if (added > 0) {
                plugin.getLogger().info("Upgraded config.yml with " + added + " new entries");
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Failed to upgrade config.yml: " + e.getMessage());
        }
    }

    // ==================== Config Data Classes ====================

    public static class CombatWorldConfig {
        public String name;
        public String displayName;
        public boolean enabled;
        public double weight;
        public double minX, maxX, minZ, maxZ;

        // List of spawn points - randomly selected during run start
        public List<SpawnPointConfig> spawnPoints = new ArrayList<>();

        /**
         * Gets a random spawn point from the configured list.
         * @param world The world to create the Location in
         * @return A random spawn Location, or null if no spawn points configured
         */
        public Location getRandomSpawnPoint(World world) {
            if (spawnPoints.isEmpty()) return null;
            SpawnPointConfig sp = spawnPoints.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(spawnPoints.size()));
            Location loc = new Location(world, sp.x, sp.y, sp.z);
            if (sp.yaw != null) loc.setYaw(sp.yaw);
            if (sp.pitch != null) loc.setPitch(sp.pitch);
            return loc;
        }

        /**
         * Checks if this world has spawn points configured.
         */
        public boolean hasSpawnPoints() {
            return !spawnPoints.isEmpty();
        }
    }

    /**
     * Configuration for a spawn point within a combat world.
     */
    public static class SpawnPointConfig {
        public double x;
        public double y;
        public double z;
        public Float yaw;
        public Float pitch;

        public SpawnPointConfig() {}

        public SpawnPointConfig(double x, double y, double z, Float yaw, Float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public static class StarterOptionConfig {
        public String optionId;
        public String displayName;
        public String templateId;
        public String group;
        public int level;
        public Material displayMaterial;
        public String displayItemName;
        public List<String> displayItemLore;
    }

    public static class EquipmentGroupConfig {
        public String groupId;
        public String displayName;
        public Map<Integer, List<String>> levelItems;

        public int getMaxLevel() {
            return levelItems.keySet().stream().mapToInt(i -> i).max().orElse(1);
        }

        public boolean hasNextLevel(int currentLevel) {
            return levelItems.containsKey(currentLevel + 1);
        }

        public List<String> getItemsAtLevel(int level) {
            return levelItems.getOrDefault(level, Collections.emptyList());
        }
    }

    public static class EnemyArchetypeConfig {
        public String archetypeId;
        public String enemyType;
        public double weight;
        public List<String> spawnCommands;

        // Level gating - archetype only spawns when enemy level >= minSpawnLevel
        public int minSpawnLevel = 1;

        // World restriction - limits which combat worlds this archetype can spawn in
        // "any" means spawn in any combat world (default behavior)
        public List<String> allowedWorlds = List.of("any");

        // Chance-based fixed rewards (no level scaling)
        public int xpAmount = 10;
        public double xpChance = 1.0;        // 0-1, probability to award XP
        public int coinAmount = 1;
        public double coinChance = 1.0;      // 0-1, probability to award coins
        public int permaScoreAmount = 1;
        public double permaScoreChance = 0.01; // 0-1, probability to award perma score

        /**
         * Checks if this archetype is allowed to spawn in the given world.
         * @param worldName the world name to check
         * @return true if the archetype can spawn in this world
         */
        public boolean isAllowedInWorld(String worldName) {
            if (allowedWorlds == null || allowedWorlds.isEmpty()) {
                return true; // Empty list means any world (backward compatibility)
            }
            for (String allowed : allowedWorlds) {
                if ("any".equalsIgnoreCase(allowed)) {
                    return true;
                }
                if (allowed.equalsIgnoreCase(worldName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Configuration for a merchant template.
     */
    public static class MerchantTemplateConfig {
        public String templateId;
        public String displayName;
        public List<MerchantTradeConfig> trades = new ArrayList<>();
    }

    /**
     * Configuration for a single merchant trade.
     */
    public static class MerchantTradeConfig {
        /** Result item - Material name (e.g., GOLDEN_APPLE) or item template ID */
        public String resultItem;
        /** Amount of result item */
        public int resultAmount = 1;
        /** Cost item - Material name (e.g., GOLD_NUGGET) */
        public String costItem;
        /** Amount of cost item required */
        public int costAmount = 1;
        /** Maximum uses before trade locks */
        public int maxUses = 10;
    }

    /**
     * Configuration for a sound effect with volume and pitch.
     * Uses vanilla Minecraft sound format (e.g., minecraft:entity.experience_orb.pickup).
     */
    public record SoundConfig(String sound, float volume, float pitch) {
        /**
         * Plays this sound to a player if configured.
         */
        public void play(org.bukkit.entity.Player player) {
            if (sound != null && !sound.isEmpty() && player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }
}
