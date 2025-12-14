package cat.nyaa.survivors.config;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.util.ConfigException;
import org.bukkit.Material;
import org.bukkit.Sound;
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
    private String prefix;
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

    // Spawning limits
    private int spawnTickInterval;
    private boolean spawningEnabled;
    private int targetMobsPerPlayer;
    private int maxSpawnsPerPlayerPerTick;
    private int maxSpawnsPerTick;
    private int maxCommandsPerTick;
    private double mobCountRadius;

    // Spawn positioning
    private double minSpawnDistance;
    private double maxSpawnDistance;
    private int maxSampleAttempts;

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

    // Rewards
    private boolean xpShareEnabled;
    private double xpShareRadius;
    private double xpSharePercent;
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

    // Teleport commands
    private String prepCommand;
    private String enterCommand;
    private String respawnCommand;

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

    // Merchant config
    private boolean merchantsEnabled;
    private int merchantSpawnInterval;
    private int merchantLifetime;
    private double merchantMinDistance;
    private double merchantMaxDistance;

    public ConfigService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or reloads the configuration.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
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
        loadRewards();
        loadProgression();
        loadScoreboard();
        loadPersistence();
        loadTeleport();
        loadTemplates();
        loadCombatWorlds();
        loadStarterOptions();
        loadEquipmentPools();
        loadEnemyArchetypes();
        loadMerchants();
    }

    private void loadPluginSettings() {
        language = config.getString("plugin.language", "zh_CN");
        prefix = config.getString("plugin.prefix", "§8[§6武§e生§8] §7");
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
    }

    private void loadSpawning() {
        spawnTickInterval = config.getInt("spawning.loop.tickInterval", 20);
        spawningEnabled = config.getBoolean("spawning.loop.enabled", true);

        targetMobsPerPlayer = config.getInt("spawning.limits.targetMobsPerPlayer", 10);
        maxSpawnsPerPlayerPerTick = config.getInt("spawning.limits.maxSpawnsPerPlayerPerTick", 3);
        maxSpawnsPerTick = config.getInt("spawning.limits.maxSpawnsPerTick", 20);
        maxCommandsPerTick = config.getInt("spawning.limits.maxCommandsPerTick", 50);
        mobCountRadius = config.getDouble("spawning.limits.mobCountRadius", 30.0);

        minSpawnDistance = config.getDouble("spawning.positioning.minSpawnDistance", 8.0);
        maxSpawnDistance = config.getDouble("spawning.positioning.maxSpawnDistance", 25.0);
        maxSampleAttempts = config.getInt("spawning.positioning.maxSampleAttempts", 10);

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

    private void loadRewards() {
        xpShareEnabled = config.getBoolean("rewards.xpShare.enabled", true);
        xpShareRadius = config.getDouble("rewards.xpShare.radius", 20.0);
        xpSharePercent = config.getDouble("rewards.xpShare.sharePercent", 0.25);

        coinMaterial = parseMaterial(config.getString("rewards.coin.material", "EMERALD"));
        coinCustomModelData = config.getInt("rewards.coin.customModelData", 0);
        coinDisplayName = config.getString("rewards.coin.displayName", "§e金币");
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
        prepCommand = config.getString("teleport.prepCommand", "tp ${player} world 0 64 0");
        enterCommand = config.getString("teleport.enterCommand", "tp ${player} ${world} ${x} ${y} ${z} ${yaw} ${pitch}");
        respawnCommand = config.getString("teleport.respawnCommand", "tp ${player} ${world} ${x} ${y} ${z}");
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

            ConfigurationSection rewards = archSection.getConfigurationSection("rewards");
            if (rewards != null) {
                arch.xpBase = rewards.getInt("xpBase", 10);
                arch.xpPerLevel = rewards.getInt("xpPerLevel", 5);
                arch.coinBase = rewards.getInt("coinBase", 1);
                arch.coinPerLevel = rewards.getInt("coinPerLevel", 1);
                arch.permaScoreChance = rewards.getDouble("permaScoreChance", 0.01);
            }

            enemyArchetypes.put(archetypeId, arch);
        }
    }

    private void loadMerchants() {
        merchantsEnabled = config.getBoolean("merchants.enabled", true);
        merchantSpawnInterval = config.getInt("merchants.spawn.intervalSeconds", 120);
        merchantLifetime = config.getInt("merchants.spawn.lifetimeSeconds", 60);
        merchantMinDistance = config.getDouble("merchants.spawn.minDistanceFromPlayers", 20.0);
        merchantMaxDistance = config.getDouble("merchants.spawn.maxDistanceFromPlayers", 50.0);
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

    // ==================== Getters ====================

    public String getLanguage() { return language; }
    public String getPrefix() { return prefix; }
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

    public int getSpawnTickInterval() { return spawnTickInterval; }
    public boolean isSpawningEnabled() { return spawningEnabled; }
    public int getTargetMobsPerPlayer() { return targetMobsPerPlayer; }
    public int getMaxSpawnsPerPlayerPerTick() { return maxSpawnsPerPlayerPerTick; }
    public int getMaxSpawnsPerTick() { return maxSpawnsPerTick; }
    public int getMaxCommandsPerTick() { return maxCommandsPerTick; }
    public double getMobCountRadius() { return mobCountRadius; }

    public double getMinSpawnDistance() { return minSpawnDistance; }
    public double getMaxSpawnDistance() { return maxSpawnDistance; }
    public int getMaxSampleAttempts() { return maxSampleAttempts; }

    public double getLevelSamplingRadius() { return levelSamplingRadius; }
    public double getAvgLevelMultiplier() { return avgLevelMultiplier; }
    public double getPlayerCountMultiplier() { return playerCountMultiplier; }
    public int getLevelOffset() { return levelOffset; }
    public int getMinEnemyLevel() { return minEnemyLevel; }
    public int getMaxEnemyLevel() { return maxEnemyLevel; }
    public boolean isTimeScalingEnabled() { return timeScalingEnabled; }
    public int getTimeStepSeconds() { return timeStepSeconds; }
    public int getLevelPerTimeStep() { return levelPerTimeStep; }

    public boolean isXpShareEnabled() { return xpShareEnabled; }
    public double getXpShareRadius() { return xpShareRadius; }
    public double getXpSharePercent() { return xpSharePercent; }
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

    public String getPrepCommand() { return prepCommand; }
    public String getEnterCommand() { return enterCommand; }
    public String getRespawnCommand() { return respawnCommand; }

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

    // ==================== Config Data Classes ====================

    public static class CombatWorldConfig {
        public String name;
        public String displayName;
        public boolean enabled;
        public double weight;
        public double minX, maxX, minZ, maxZ;
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
        public int xpBase;
        public int xpPerLevel;
        public int coinBase;
        public int coinPerLevel;
        public double permaScoreChance;
    }
}
