package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.EquipmentGroupConfig;
import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
import cat.nyaa.survivors.config.ConfigService.StarterOptionConfig;
import cat.nyaa.survivors.config.ConfigService.CombatWorldConfig;
import cat.nyaa.survivors.config.ConfigService.SpawnPointConfig;
import cat.nyaa.survivors.config.ConfigService.MerchantTemplateConfig;
import cat.nyaa.survivors.config.ConfigService.MerchantTradeConfig;
import cat.nyaa.survivors.config.ItemTemplateConfig;
import cat.nyaa.survivors.merchant.MerchantItemPool;
import cat.nyaa.survivors.merchant.WeightedShopItem;
import cat.nyaa.survivors.model.EquipmentType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Manages runtime-configurable data with persistence.
 * Handles equipment groups, item templates, enemy archetypes, starter options, worlds, and merchants.
 */
public class AdminConfigService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService configService;

    // Data directories
    private Path dataPath;
    private Path equipmentPath;
    private Path itemsPath;

    // In-memory storage
    private final Map<String, EquipmentGroupConfig> weaponGroups = new LinkedHashMap<>();
    private final Map<String, EquipmentGroupConfig> helmetGroups = new LinkedHashMap<>();
    private final Map<String, ItemTemplateConfig> itemTemplates = new LinkedHashMap<>();
    private final Map<String, EnemyArchetypeConfig> archetypes = new LinkedHashMap<>();
    private final List<StarterOptionConfig> starterWeapons = new ArrayList<>();
    private final List<StarterOptionConfig> starterHelmets = new ArrayList<>();
    private final List<CombatWorldConfig> combatWorlds = new ArrayList<>();
    private final Map<String, MerchantTemplateConfig> merchantTemplates = new LinkedHashMap<>();
    private final Map<String, MerchantItemPool> merchantPools = new LinkedHashMap<>();

    public AdminConfigService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.configService = plugin.getConfigService();
    }

    /**
     * Initializes directories and loads all data.
     */
    public void initialize() {
        initializeDirectories();
        loadAll();
    }

    private void initializeDirectories() {
        dataPath = plugin.getDataFolder().toPath().resolve("data");
        equipmentPath = dataPath.resolve("equipment");
        itemsPath = dataPath.resolve("items");

        try {
            Files.createDirectories(dataPath);
            Files.createDirectories(equipmentPath);
            Files.createDirectories(itemsPath);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create data directories", e);
        }
    }

    /**
     * Loads all data from files. Migrates from config.yml if data files don't exist.
     */
    public void loadAll() {
        boolean needsMigration = !Files.exists(equipmentPath.resolve("weapons.yml"));

        plugin.getLogger().info("[AdminConfigService] loadAll() - needsMigration=" + needsMigration +
            ", weaponsYmlPath=" + equipmentPath.resolve("weapons.yml"));

        if (needsMigration) {
            plugin.getLogger().info("Data files not found, migrating from config.yml...");
            migrateFromConfig();
        } else {
            plugin.getLogger().info("[AdminConfigService] Loading from data files (not migrating)");
            loadEquipmentGroups();
            loadArchetypes();
            loadStarters();
            loadWorlds();
        }

        loadItemTemplates();
        loadMerchants();
        loadMerchantPools();

        // Sync loaded data to ConfigService for runtime use
        updateConfigService();

        plugin.getLogger().info("Loaded " + weaponGroups.size() + " weapon groups, " +
                helmetGroups.size() + " helmet groups, " +
                itemTemplates.size() + " item templates, " +
                archetypes.size() + " archetypes, " +
                starterWeapons.size() + " starter weapons, " +
                starterHelmets.size() + " starter helmets, " +
                combatWorlds.size() + " combat worlds, " +
                merchantTemplates.size() + " merchant templates, " +
                merchantPools.size() + " merchant pools");
    }

    /**
     * Migrates existing config.yml data to dedicated data files.
     */
    private void migrateFromConfig() {
        plugin.getLogger().info("[AdminConfigService] Starting migration from config.yml...");

        // Migrate weapon groups
        weaponGroups.clear();
        weaponGroups.putAll(configService.getWeaponGroups());
        plugin.getLogger().info("[AdminConfigService] Migrated " + weaponGroups.size() + " weapon groups");

        // Migrate helmet groups
        helmetGroups.clear();
        helmetGroups.putAll(configService.getHelmetGroups());
        plugin.getLogger().info("[AdminConfigService] Migrated " + helmetGroups.size() + " helmet groups");

        // Migrate archetypes
        archetypes.clear();
        archetypes.putAll(configService.getEnemyArchetypes());
        plugin.getLogger().info("[AdminConfigService] Migrated " + archetypes.size() + " archetypes");

        // For starters: check if starters.yml already exists with content
        // This allows users to pre-configure starters.yml before first run
        File existingStartersFile = dataPath.resolve("starters.yml").toFile();
        File rootStartersFile = new File(plugin.getDataFolder(), "starters.yml");

        boolean loadedFromExisting = false;
        if (existingStartersFile.exists()) {
            YamlConfiguration existingYaml = YamlConfiguration.loadConfiguration(existingStartersFile);
            List<Map<?, ?>> existingWeapons = existingYaml.getMapList("weapons");
            List<Map<?, ?>> existingHelmets = existingYaml.getMapList("helmets");
            if (!existingWeapons.isEmpty() || !existingHelmets.isEmpty()) {
                plugin.getLogger().info("[AdminConfigService] Found existing starters.yml with content, loading from there");
                loadStarterList(existingWeapons, starterWeapons, "weapon");
                loadStarterList(existingHelmets, starterHelmets, "helmet");
                loadedFromExisting = true;
            }
        } else if (rootStartersFile.exists()) {
            // Check root folder as fallback
            YamlConfiguration existingYaml = YamlConfiguration.loadConfiguration(rootStartersFile);
            List<Map<?, ?>> existingWeapons = existingYaml.getMapList("weapons");
            List<Map<?, ?>> existingHelmets = existingYaml.getMapList("helmets");
            if (!existingWeapons.isEmpty() || !existingHelmets.isEmpty()) {
                plugin.getLogger().info("[AdminConfigService] Found starters.yml in root folder with content, loading from there");
                loadStarterList(existingWeapons, starterWeapons, "weapon");
                loadStarterList(existingHelmets, starterHelmets, "helmet");
                loadedFromExisting = true;
            }
        }

        if (!loadedFromExisting) {
            // Migrate starters from config.yml (likely empty)
            starterWeapons.clear();
            List<StarterOptionConfig> configWeapons = configService.getStarterWeapons();
            starterWeapons.addAll(configWeapons);
            plugin.getLogger().info("[AdminConfigService] Migrated " + starterWeapons.size() + " starter weapons from config.yml");
            for (StarterOptionConfig w : starterWeapons) {
                plugin.getLogger().info("[AdminConfigService] Migrated weapon: optionId=" + w.optionId +
                    ", group=" + w.group + ", level=" + w.level);
            }

            starterHelmets.clear();
            List<StarterOptionConfig> configHelmets = configService.getStarterHelmets();
            starterHelmets.addAll(configHelmets);
            plugin.getLogger().info("[AdminConfigService] Migrated " + starterHelmets.size() + " starter helmets from config.yml");
            for (StarterOptionConfig h : starterHelmets) {
                plugin.getLogger().info("[AdminConfigService] Migrated helmet: optionId=" + h.optionId +
                    ", group=" + h.group + ", level=" + h.level);
            }
        }

        // Migrate combat worlds
        combatWorlds.clear();
        combatWorlds.addAll(configService.getCombatWorlds());
        plugin.getLogger().info("[AdminConfigService] Migrated " + combatWorlds.size() + " combat worlds");

        // Save all migrated data
        saveAll();
        plugin.getLogger().info("Migration complete.");
    }

    // ==================== Equipment Group Operations ====================

    /**
     * Creates a new equipment group.
     */
    public boolean createEquipmentGroup(EquipmentType type, String groupId, String displayName) {
        Map<String, EquipmentGroupConfig> groups = type == EquipmentType.WEAPON ? weaponGroups : helmetGroups;

        if (groups.containsKey(groupId)) {
            return false;
        }

        EquipmentGroupConfig config = new EquipmentGroupConfig();
        config.groupId = groupId;
        config.displayName = displayName != null ? displayName : groupId;
        config.levelItems = new LinkedHashMap<>();

        groups.put(groupId, config);
        saveEquipmentGroups();
        updateConfigService();
        return true;
    }

    /**
     * Deletes an equipment group and its associated item templates.
     */
    public boolean deleteEquipmentGroup(EquipmentType type, String groupId) {
        Map<String, EquipmentGroupConfig> groups = type == EquipmentType.WEAPON ? weaponGroups : helmetGroups;

        if (!groups.containsKey(groupId)) {
            return false;
        }

        // Remove associated item templates
        List<String> toRemove = new ArrayList<>();
        for (ItemTemplateConfig template : itemTemplates.values()) {
            if (groupId.equals(template.getGroupId())) {
                toRemove.add(template.getTemplateId());
            }
        }
        for (String templateId : toRemove) {
            deleteItemTemplate(templateId);
        }

        groups.remove(groupId);
        saveEquipmentGroups();
        updateConfigService();
        return true;
    }

    /**
     * Gets all equipment groups of a specific type.
     */
    public Collection<EquipmentGroupConfig> getEquipmentGroups(EquipmentType type) {
        return type == EquipmentType.WEAPON ? weaponGroups.values() : helmetGroups.values();
    }

    /**
     * Gets a specific equipment group.
     */
    public Optional<EquipmentGroupConfig> getEquipmentGroup(EquipmentType type, String groupId) {
        Map<String, EquipmentGroupConfig> groups = type == EquipmentType.WEAPON ? weaponGroups : helmetGroups;
        return Optional.ofNullable(groups.get(groupId));
    }

    // ==================== Item Template Operations ====================

    /**
     * Adds an item to an equipment group's level pool.
     *
     * @return the generated template ID, or null if failed
     */
    public String addItemToGroup(EquipmentType type, String groupId, int level, ItemStack item) {
        Map<String, EquipmentGroupConfig> groups = type == EquipmentType.WEAPON ? weaponGroups : helmetGroups;
        EquipmentGroupConfig group = groups.get(groupId);

        if (group == null) {
            return null;
        }

        // Generate unique template ID
        String templateId = groupId + "_" + item.getType().name().toLowerCase() + "_" + level + "_" + System.currentTimeMillis();

        // Create item template
        ItemTemplateConfig template = ItemTemplateConfig.fromItemStack(item, templateId, groupId, level);
        itemTemplates.put(templateId, template);

        // Add to group's level list
        group.levelItems.computeIfAbsent(level, k -> new ArrayList<>()).add(templateId);

        // Save changes
        saveItemTemplate(template);
        saveEquipmentGroups();
        updateConfigService();

        return templateId;
    }

    /**
     * Removes an item from an equipment group's level pool.
     *
     * @return the removed template ID, or null if not found
     */
    public String removeItemFromGroup(EquipmentType type, String groupId, int level, int index) {
        Map<String, EquipmentGroupConfig> groups = type == EquipmentType.WEAPON ? weaponGroups : helmetGroups;
        EquipmentGroupConfig group = groups.get(groupId);

        if (group == null) {
            return null;
        }

        List<String> levelItems = group.levelItems.get(level);
        if (levelItems == null || index < 0 || index >= levelItems.size()) {
            return null;
        }

        String templateId = levelItems.remove(index);
        deleteItemTemplate(templateId);

        // Clean up empty level
        if (levelItems.isEmpty()) {
            group.levelItems.remove(level);
        }

        saveEquipmentGroups();
        updateConfigService();

        return templateId;
    }

    /**
     * Gets all items in a group's level.
     */
    public List<ItemTemplateConfig> getItemsInGroup(EquipmentType type, String groupId, int level) {
        Map<String, EquipmentGroupConfig> groups = type == EquipmentType.WEAPON ? weaponGroups : helmetGroups;
        EquipmentGroupConfig group = groups.get(groupId);

        if (group == null) {
            return Collections.emptyList();
        }

        List<String> templateIds = group.levelItems.get(level);
        if (templateIds == null) {
            return Collections.emptyList();
        }

        List<ItemTemplateConfig> items = new ArrayList<>();
        for (String templateId : templateIds) {
            ItemTemplateConfig template = itemTemplates.get(templateId);
            if (template != null) {
                items.add(template);
            }
        }
        return items;
    }

    /**
     * Gets an item template by ID.
     */
    public Optional<ItemTemplateConfig> getItemTemplate(String templateId) {
        return Optional.ofNullable(itemTemplates.get(templateId));
    }

    // ==================== Archetype Operations ====================

    /**
     * Creates a new enemy archetype with new chance-based reward format.
     */
    public boolean createArchetype(String id, String entityType, double weight) {
        if (archetypes.containsKey(id)) {
            return false;
        }

        EnemyArchetypeConfig config = new EnemyArchetypeConfig();
        config.archetypeId = id;
        config.enemyType = entityType;
        config.weight = weight;
        config.spawnCommands = new ArrayList<>();

        // New format defaults
        config.minSpawnLevel = 1;
        config.xpAmount = 10;
        config.xpChance = 1.0;
        config.coinAmount = 1;
        config.coinChance = 1.0;
        config.permaScoreAmount = 1;
        config.permaScoreChance = 0.01;

        archetypes.put(id, config);
        saveArchetypes();
        updateConfigService();
        return true;
    }

    /**
     * Deletes an enemy archetype.
     */
    public boolean deleteArchetype(String id) {
        if (!archetypes.containsKey(id)) {
            return false;
        }

        archetypes.remove(id);
        saveArchetypes();
        updateConfigService();
        return true;
    }

    /**
     * Gets all archetypes.
     */
    public Collection<EnemyArchetypeConfig> getArchetypes() {
        return archetypes.values();
    }

    /**
     * Gets a specific archetype.
     */
    public Optional<EnemyArchetypeConfig> getArchetype(String id) {
        return Optional.ofNullable(archetypes.get(id));
    }

    /**
     * Adds a spawn command to an archetype.
     */
    public boolean addSpawnCommand(String archetypeId, String command) {
        EnemyArchetypeConfig config = archetypes.get(archetypeId);
        if (config == null) {
            return false;
        }

        if (config.spawnCommands == null) {
            config.spawnCommands = new ArrayList<>();
        }
        config.spawnCommands.add(command);
        saveArchetypes();
        updateConfigService();
        return true;
    }

    /**
     * Removes a spawn command from an archetype.
     */
    public boolean removeSpawnCommand(String archetypeId, int index) {
        EnemyArchetypeConfig config = archetypes.get(archetypeId);
        if (config == null || config.spawnCommands == null) {
            return false;
        }

        if (index < 0 || index >= config.spawnCommands.size()) {
            return false;
        }

        config.spawnCommands.remove(index);
        saveArchetypes();
        updateConfigService();
        return true;
    }

    /**
     * Sets rewards for an archetype using new chance-based format.
     */
    public boolean setArchetypeRewards(String id, int xpAmount, double xpChance,
                                       int coinAmount, double coinChance,
                                       int permaScoreAmount, double permaScoreChance) {
        EnemyArchetypeConfig config = archetypes.get(id);
        if (config == null) {
            return false;
        }

        config.xpAmount = xpAmount;
        config.xpChance = Math.max(0.0, Math.min(1.0, xpChance));
        config.coinAmount = coinAmount;
        config.coinChance = Math.max(0.0, Math.min(1.0, coinChance));
        config.permaScoreAmount = permaScoreAmount;
        config.permaScoreChance = Math.max(0.0, Math.min(1.0, permaScoreChance));

        saveArchetypes();
        updateConfigService();
        return true;
    }

    /**
     * Sets the minimum spawn level for an archetype.
     */
    public boolean setArchetypeMinSpawnLevel(String id, int minSpawnLevel) {
        EnemyArchetypeConfig config = archetypes.get(id);
        if (config == null) {
            return false;
        }

        config.minSpawnLevel = Math.max(1, minSpawnLevel);
        saveArchetypes();
        updateConfigService();
        return true;
    }

    /**
     * Sets the allowed worlds for an archetype.
     * @param id the archetype ID
     * @param worlds list of world names, or a list containing "any" for all worlds
     * @return true if successful, false if archetype not found
     */
    public boolean setArchetypeAllowedWorlds(String id, List<String> worlds) {
        EnemyArchetypeConfig config = archetypes.get(id);
        if (config == null) {
            return false;
        }

        if (worlds == null || worlds.isEmpty()) {
            config.allowedWorlds = List.of("any");
        } else {
            config.allowedWorlds = new ArrayList<>(worlds);
        }
        saveArchetypes();
        updateConfigService();
        return true;
    }

    // ==================== Persistence ====================

    /**
     * Saves all data to files.
     */
    public void saveAll() {
        saveEquipmentGroups();
        saveArchetypes();
        saveStarters();
        saveWorlds();
        // Item templates are saved individually
    }

    /**
     * Reloads all data from files.
     */
    public void reload() {
        loadAll();
        // updateConfigService() is called inside loadAll()
    }

    private void loadEquipmentGroups() {
        weaponGroups.clear();
        helmetGroups.clear();

        loadEquipmentGroupFile("weapons.yml", weaponGroups);
        loadEquipmentGroupFile("helmets.yml", helmetGroups);
    }

    private void loadEquipmentGroupFile(String filename, Map<String, EquipmentGroupConfig> target) {
        File file = equipmentPath.resolve(filename).toFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String groupId : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(groupId);
            if (section == null) continue;

            EquipmentGroupConfig config = new EquipmentGroupConfig();
            config.groupId = groupId;
            config.displayName = section.getString("displayName", groupId);
            config.levelItems = new LinkedHashMap<>();

            ConfigurationSection levelsSection = section.getConfigurationSection("levels");
            if (levelsSection != null) {
                for (String levelKey : levelsSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelKey);
                        List<String> items = levelsSection.getStringList(levelKey);
                        config.levelItems.put(level, new ArrayList<>(items));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            target.put(groupId, config);
        }
    }

    private void saveEquipmentGroups() {
        saveEquipmentGroupFile("weapons.yml", weaponGroups);
        saveEquipmentGroupFile("helmets.yml", helmetGroups);
    }

    private void saveEquipmentGroupFile(String filename, Map<String, EquipmentGroupConfig> groups) {
        File file = equipmentPath.resolve(filename).toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("Auto-generated by KedamaSurvivors\nUse in-game commands: /vrs admin equipment ...\n");

        for (EquipmentGroupConfig config : groups.values()) {
            yaml.set(config.groupId + ".displayName", config.displayName);
            if (config.levelItems != null) {
                for (Map.Entry<Integer, List<String>> entry : config.levelItems.entrySet()) {
                    yaml.set(config.groupId + ".levels." + entry.getKey(), entry.getValue());
                }
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + filename, e);
        }
    }

    /**
     * Recursively converts a ConfigurationSection to a Map.
     * This is needed because Bukkit's YamlConfiguration returns MemorySection
     * objects for nested structures, which cannot be cast to Map directly.
     */
    private Map<String, Object> convertSectionToMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                result.put(key, convertSectionToMap((ConfigurationSection) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private void loadItemTemplates() {
        itemTemplates.clear();

        if (!Files.exists(itemsPath)) {
            return;
        }

        try (Stream<Path> files = Files.list(itemsPath)) {
            files.filter(p -> p.toString().endsWith(".yml"))
                    .forEach(this::loadItemTemplateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load item templates", e);
        }
    }

    private void loadItemTemplateFile(Path path) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
            Map<String, Object> map = convertSectionToMap(yaml);

            ItemTemplateConfig config = ItemTemplateConfig.fromMap(map);
            if (config.getTemplateId() != null) {
                itemTemplates.put(config.getTemplateId(), config);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load item template: " + path.getFileName(), e);
        }
    }

    private void saveItemTemplate(ItemTemplateConfig template) {
        File file = itemsPath.resolve(template.getTemplateId() + ".yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("Item Template: " + template.getTemplateId() + "\n" +
                "Group: " + template.getGroupId() + "\n" +
                "Level: " + template.getLevel() + "\n");

        Map<String, Object> map = template.toMap();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save item template: " + template.getTemplateId(), e);
        }
    }

    private void deleteItemTemplate(String templateId) {
        itemTemplates.remove(templateId);
        Path file = itemsPath.resolve(templateId + ".yml");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete item template file: " + templateId, e);
        }
    }

    private void loadArchetypes() {
        archetypes.clear();

        File file = dataPath.resolve("archetypes.yml").toFile();
        if (!file.exists()) {
            return;
        }

        boolean needsResave = false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            EnemyArchetypeConfig config = new EnemyArchetypeConfig();
            config.archetypeId = id;
            config.enemyType = section.getString("entityType", "minecraft:zombie");
            config.weight = section.getDouble("weight", 1.0);
            config.spawnCommands = new ArrayList<>(section.getStringList("spawnCommands"));

            // Load minSpawnLevel (level gating)
            config.minSpawnLevel = section.getInt("minSpawnLevel", 1);

            // Load allowedWorlds (world restriction)
            List<String> worldList = section.getStringList("allowedWorlds");
            if (worldList.isEmpty()) {
                config.allowedWorlds = List.of("any"); // Default: spawn in any world
            } else {
                config.allowedWorlds = new ArrayList<>(worldList);
            }

            ConfigurationSection rewards = section.getConfigurationSection("rewards");
            if (rewards != null) {
                // Check for new format (xpAmount) vs legacy format (xpBase)
                if (rewards.contains("xpAmount")) {
                    // New chance-based format
                    config.xpAmount = rewards.getInt("xpAmount", 10);
                    config.xpChance = rewards.getDouble("xpChance", 1.0);
                    config.coinAmount = rewards.getInt("coinAmount", 1);
                    config.coinChance = rewards.getDouble("coinChance", 1.0);
                    config.permaScoreAmount = rewards.getInt("permaScoreAmount", 1);
                    config.permaScoreChance = rewards.getDouble("permaScoreChance", 0.01);
                } else if (rewards.contains("xpBase")) {
                    // Legacy format - migrate to new format
                    int xpBase = rewards.getInt("xpBase", 10);
                    int coinBase = rewards.getInt("coinBase", 1);
                    double permaChance = rewards.getDouble("permaScoreChance", 0.01);

                    // Migrate: use base values as fixed amounts with 100% chance
                    config.xpAmount = xpBase;
                    config.xpChance = 1.0;
                    config.coinAmount = coinBase;
                    config.coinChance = 1.0;
                    config.permaScoreAmount = 1;
                    config.permaScoreChance = permaChance;

                    plugin.getLogger().info("Migrated archetype '" + id + "' to new reward format");
                    needsResave = true;
                }
            }

            archetypes.put(id, config);

            // Debug: log loaded archetype with minSpawnLevel
            if (configService.isVerbose()) {
                plugin.getLogger().info("[AdminConfigService] Loaded archetype: " + id +
                        ", minSpawnLevel=" + config.minSpawnLevel +
                        ", allowedWorlds=" + config.allowedWorlds);
            }
        }

        // Auto-save migrated data
        if (needsResave) {
            saveArchetypes();
        }
    }

    private void saveArchetypes() {
        File file = dataPath.resolve("archetypes.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("""
            Auto-generated by KedamaSurvivors
            Use in-game commands: /vrs admin spawner ...

            minSpawnLevel: Minimum enemy level required to spawn this archetype
            allowedWorlds: List of combat world names where this archetype can spawn
              - Use "any" to allow spawning in all combat worlds (default)
              - Use specific world names to restrict spawning
            Rewards use chance-based fixed values (no level scaling):
              xpAmount + xpChance, coinAmount + coinChance, permaScoreAmount + permaScoreChance
            """);

        for (EnemyArchetypeConfig config : archetypes.values()) {
            String id = config.archetypeId;
            yaml.set(id + ".entityType", config.enemyType);
            yaml.set(id + ".weight", config.weight);
            yaml.set(id + ".minSpawnLevel", config.minSpawnLevel);
            yaml.set(id + ".allowedWorlds", config.allowedWorlds);
            yaml.set(id + ".spawnCommands", config.spawnCommands);
            yaml.set(id + ".rewards.xpAmount", config.xpAmount);
            yaml.set(id + ".rewards.xpChance", config.xpChance);
            yaml.set(id + ".rewards.coinAmount", config.coinAmount);
            yaml.set(id + ".rewards.coinChance", config.coinChance);
            yaml.set(id + ".rewards.permaScoreAmount", config.permaScoreAmount);
            yaml.set(id + ".rewards.permaScoreChance", config.permaScoreChance);
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save archetypes.yml", e);
        }
    }

    private void loadStarters() {
        starterWeapons.clear();
        starterHelmets.clear();

        // Primary location: data/starters.yml
        File file = dataPath.resolve("starters.yml").toFile();
        plugin.getLogger().info("[AdminConfigService] loadStarters() - checking " + file.getAbsolutePath() +
            ", exists=" + file.exists());

        // Fallback: check root plugin folder for starters.yml
        if (!file.exists()) {
            File rootFile = new File(plugin.getDataFolder(), "starters.yml");
            if (rootFile.exists()) {
                plugin.getLogger().info("[AdminConfigService] Found starters.yml in root folder, using that");
                file = rootFile;
            }
        }

        if (!file.exists()) {
            plugin.getLogger().warning("[AdminConfigService] starters.yml not found! Expected at: " +
                dataPath.resolve("starters.yml"));
            plugin.getLogger().warning("[AdminConfigService] Use '/vrs admin starter create' to add starters, " +
                "or create data/starters.yml manually");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> weaponsList = yaml.getMapList("weapons");
        List<Map<?, ?>> helmetsList = yaml.getMapList("helmets");
        plugin.getLogger().info("[AdminConfigService] Found " + weaponsList.size() + " weapons and " +
            helmetsList.size() + " helmets in " + file.getName());

        loadStarterList(weaponsList, starterWeapons, "weapon");
        loadStarterList(helmetsList, starterHelmets, "helmet");
    }

    @SuppressWarnings("unchecked")
    private void loadStarterList(List<Map<?, ?>> list, List<StarterOptionConfig> target, String typeName) {
        for (Map<?, ?> map : list) {
            StarterOptionConfig opt = new StarterOptionConfig();
            opt.optionId = (String) map.get("optionId");
            opt.displayName = (String) map.get("displayName");
            opt.templateId = (String) map.get("templateId");
            opt.group = (String) map.get("group");
            Object levelVal = map.get("level");
            opt.level = levelVal instanceof Number ? ((Number) levelVal).intValue() : 1;

            // Log loaded starter for debugging
            plugin.getLogger().info("Loaded starter " + typeName + ": optionId=" + opt.optionId +
                ", group=" + opt.group + ", level=" + opt.level);

            // Warn if group is missing - upgrades will fail
            if (opt.group == null || opt.group.isEmpty()) {
                plugin.getLogger().warning("Starter " + typeName + " '" + opt.optionId +
                    "' has no 'group' configured! Player upgrades will fail. " +
                    "Use '/vrs admin starter set group' to fix.");
            }

            Map<String, Object> displayItem = (Map<String, Object>) map.get("displayItem");
            if (displayItem != null) {
                String materialName = (String) displayItem.get("material");
                if (materialName != null) {
                    try {
                        opt.displayMaterial = org.bukkit.Material.valueOf(materialName);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                opt.displayItemName = (String) displayItem.get("name");
                opt.displayItemLore = (List<String>) displayItem.get("lore");
            }

            target.add(opt);
        }
    }

    private void saveStarters() {
        File file = dataPath.resolve("starters.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("Auto-generated by KedamaSurvivors\nStarter selection options\n");

        yaml.set("weapons", starterListToMaps(starterWeapons));
        yaml.set("helmets", starterListToMaps(starterHelmets));

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save starters.yml", e);
        }
    }

    private List<Map<String, Object>> starterListToMaps(List<StarterOptionConfig> starters) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (StarterOptionConfig opt : starters) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("optionId", opt.optionId);
            map.put("displayName", opt.displayName);
            map.put("templateId", opt.templateId);
            map.put("group", opt.group);
            map.put("level", opt.level);

            if (opt.displayMaterial != null) {
                Map<String, Object> displayItem = new LinkedHashMap<>();
                displayItem.put("material", opt.displayMaterial.name());
                displayItem.put("name", opt.displayItemName);
                displayItem.put("lore", opt.displayItemLore);
                map.put("displayItem", displayItem);
            }

            list.add(map);
        }
        return list;
    }

    private void loadWorlds() {
        combatWorlds.clear();

        File file = dataPath.resolve("worlds.yml").toFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = yaml.getMapList("worlds");

        for (Map<?, ?> map : list) {
            CombatWorldConfig world = new CombatWorldConfig();
            world.name = (String) map.get("name");
            Object displayNameVal = map.get("displayName");
            world.displayName = displayNameVal != null ? (String) displayNameVal : world.name;
            Object enabledVal = map.get("enabled");
            world.enabled = enabledVal == null || (Boolean) enabledVal;
            Object weightVal = map.get("weight");
            world.weight = weightVal != null ? ((Number) weightVal).doubleValue() : 1.0;

            @SuppressWarnings("unchecked")
            Map<String, Number> bounds = (Map<String, Number>) map.get("spawnBounds");
            if (bounds != null) {
                world.minX = bounds.getOrDefault("minX", -500).doubleValue();
                world.maxX = bounds.getOrDefault("maxX", 500).doubleValue();
                world.minZ = bounds.getOrDefault("minZ", -500).doubleValue();
                world.maxZ = bounds.getOrDefault("maxZ", 500).doubleValue();
            }

            // Load spawn points list
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> spawnPointsList = (List<Map<?, ?>>) map.get("spawnPoints");
            if (spawnPointsList != null) {
                for (Map<?, ?> spawnMap : spawnPointsList) {
                    SpawnPointConfig sp = new SpawnPointConfig();
                    Number x = (Number) spawnMap.get("x");
                    Number y = (Number) spawnMap.get("y");
                    Number z = (Number) spawnMap.get("z");
                    Number yaw = (Number) spawnMap.get("yaw");
                    Number pitch = (Number) spawnMap.get("pitch");
                    if (x != null) sp.x = x.doubleValue();
                    if (y != null) sp.y = y.doubleValue();
                    if (z != null) sp.z = z.doubleValue();
                    if (yaw != null) sp.yaw = yaw.floatValue();
                    if (pitch != null) sp.pitch = pitch.floatValue();
                    world.spawnPoints.add(sp);
                }
            }

            combatWorlds.add(world);
        }
    }

    private void saveWorlds() {
        File file = dataPath.resolve("worlds.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("Auto-generated by KedamaSurvivors\nCombat world definitions\n");

        List<Map<String, Object>> list = new ArrayList<>();
        for (CombatWorldConfig world : combatWorlds) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", world.name);
            map.put("displayName", world.displayName);
            map.put("enabled", world.enabled);
            map.put("weight", world.weight);
            map.put("cost", world.cost);

            Map<String, Double> bounds = new LinkedHashMap<>();
            bounds.put("minX", world.minX);
            bounds.put("maxX", world.maxX);
            bounds.put("minZ", world.minZ);
            bounds.put("maxZ", world.maxZ);
            map.put("spawnBounds", bounds);

            // Save spawn points list
            if (!world.spawnPoints.isEmpty()) {
                List<Map<String, Number>> spawnPointsList = new ArrayList<>();
                for (SpawnPointConfig sp : world.spawnPoints) {
                    Map<String, Number> spawnMap = new LinkedHashMap<>();
                    spawnMap.put("x", sp.x);
                    spawnMap.put("y", sp.y);
                    spawnMap.put("z", sp.z);
                    if (sp.yaw != null) spawnMap.put("yaw", sp.yaw);
                    if (sp.pitch != null) spawnMap.put("pitch", sp.pitch);
                    spawnPointsList.add(spawnMap);
                }
                map.put("spawnPoints", spawnPointsList);
            }

            list.add(map);
        }

        yaml.set("worlds", list);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save worlds.yml", e);
        }
    }

    /**
     * Updates ConfigService with current data for runtime use.
     */
    private void updateConfigService() {
        plugin.getLogger().info("[AdminConfigService] updateConfigService() - syncing " +
            starterWeapons.size() + " weapons, " + starterHelmets.size() + " helmets to ConfigService");
        for (StarterOptionConfig w : starterWeapons) {
            plugin.getLogger().info("[AdminConfigService] Syncing weapon: optionId=" + w.optionId +
                ", group=" + w.group + ", level=" + w.level);
        }
        for (StarterOptionConfig h : starterHelmets) {
            plugin.getLogger().info("[AdminConfigService] Syncing helmet: optionId=" + h.optionId +
                ", group=" + h.group + ", level=" + h.level);
        }

        configService.updateWeaponGroups(weaponGroups);
        configService.updateHelmetGroups(helmetGroups);
        configService.updateEnemyArchetypes(archetypes);
        configService.updateCombatWorlds(combatWorlds);
        configService.updateStarters(starterWeapons, starterHelmets);
    }

    // ==================== Equipment Group Set Operations ====================

    /**
     * Sets the display name for an equipment group.
     */
    public boolean setEquipmentGroupDisplayName(EquipmentType type, String groupId, String displayName) {
        Map<String, EquipmentGroupConfig> groups = type == EquipmentType.WEAPON ? weaponGroups : helmetGroups;
        EquipmentGroupConfig group = groups.get(groupId);
        if (group == null) {
            return false;
        }
        group.displayName = displayName;
        saveEquipmentGroups();
        updateConfigService();
        return true;
    }

    // ==================== Archetype Set Operations ====================

    /**
     * Sets the weight for an archetype.
     */
    public boolean setArchetypeWeight(String id, double weight) {
        EnemyArchetypeConfig config = archetypes.get(id);
        if (config == null) {
            return false;
        }
        config.weight = weight;
        saveArchetypes();
        updateConfigService();
        return true;
    }

    /**
     * Sets the entity type for an archetype.
     */
    public boolean setArchetypeEntityType(String id, String entityType) {
        EnemyArchetypeConfig config = archetypes.get(id);
        if (config == null) {
            return false;
        }
        config.enemyType = entityType;
        saveArchetypes();
        updateConfigService();
        return true;
    }

    // ==================== World Operations ====================

    /**
     * Creates a new combat world configuration.
     */
    public boolean createWorld(String name, String displayName, double weight, double minX, double maxX, double minZ, double maxZ) {
        // Check if world already exists
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                return false;
            }
        }

        CombatWorldConfig config = new CombatWorldConfig();
        config.name = name;
        config.displayName = displayName != null ? displayName : name;
        config.enabled = true;
        config.weight = weight;
        config.minX = minX;
        config.maxX = maxX;
        config.minZ = minZ;
        config.maxZ = maxZ;

        combatWorlds.add(config);
        saveWorlds();
        configService.updateCombatWorlds(combatWorlds);
        return true;
    }

    /**
     * Deletes a combat world configuration.
     */
    public boolean deleteWorld(String name) {
        boolean removed = combatWorlds.removeIf(w -> w.name.equals(name));
        if (removed) {
            saveWorlds();
            configService.updateCombatWorlds(combatWorlds);
        }
        return removed;
    }

    /**
     * Sets the display name for a world.
     */
    public boolean setWorldDisplayName(String name, String displayName) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                world.displayName = displayName;
                saveWorlds();
                configService.updateCombatWorlds(combatWorlds);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the weight for a world.
     */
    public boolean setWorldWeight(String name, double weight) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                world.weight = weight;
                saveWorlds();
                configService.updateCombatWorlds(combatWorlds);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the spawn bounds for a world.
     */
    public boolean setWorldBounds(String name, double minX, double maxX, double minZ, double maxZ) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                world.minX = minX;
                world.maxX = maxX;
                world.minZ = minZ;
                world.maxZ = maxZ;
                saveWorlds();
                configService.updateCombatWorlds(combatWorlds);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the selection cost for a world.
     */
    public boolean setWorldCost(String name, int cost) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                world.cost = cost;
                saveWorlds();
                configService.updateCombatWorlds(combatWorlds);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the enabled status for a world.
     */
    public boolean setWorldEnabled(String name, boolean enabled) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                world.enabled = enabled;
                saveWorlds();
                configService.updateCombatWorlds(combatWorlds);
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a spawn point to a world.
     */
    public boolean addWorldSpawnPoint(String name, double x, double y, double z, Float yaw, Float pitch) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                SpawnPointConfig sp = new SpawnPointConfig(x, y, z, yaw, pitch);
                world.spawnPoints.add(sp);
                saveWorlds();
                configService.updateCombatWorlds(combatWorlds);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a spawn point from a world by index.
     */
    public boolean removeWorldSpawnPoint(String name, int index) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                if (index >= 0 && index < world.spawnPoints.size()) {
                    world.spawnPoints.remove(index);
                    saveWorlds();
                    configService.updateCombatWorlds(combatWorlds);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Clears all spawn points from a world.
     */
    public boolean clearWorldSpawnPoints(String name) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                world.spawnPoints.clear();
                saveWorlds();
                configService.updateCombatWorlds(combatWorlds);
                return true;
            }
        }
        return false;
    }

    /**
     * Gets spawn points for a world.
     */
    public List<SpawnPointConfig> getWorldSpawnPoints(String name) {
        for (CombatWorldConfig world : combatWorlds) {
            if (world.name.equals(name)) {
                return Collections.unmodifiableList(world.spawnPoints);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Gets a world configuration by name.
     */
    public Optional<CombatWorldConfig> getWorld(String name) {
        return combatWorlds.stream().filter(w -> w.name.equals(name)).findFirst();
    }

    // ==================== Starter Operations ====================

    /**
     * Creates a new starter option.
     */
    public boolean createStarterOption(EquipmentType type, String optionId, String displayName, String templateId, String group, int level) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ? starterWeapons : starterHelmets;

        // Check if option already exists
        for (StarterOptionConfig opt : starters) {
            if (opt.optionId.equals(optionId)) {
                return false;
            }
        }

        StarterOptionConfig config = new StarterOptionConfig();
        config.optionId = optionId;
        config.displayName = displayName != null ? displayName : optionId;
        config.templateId = templateId;
        config.group = group;
        config.level = level;

        starters.add(config);
        saveStarters();
        configService.updateStarters(starterWeapons, starterHelmets);
        return true;
    }

    /**
     * Deletes a starter option.
     */
    public boolean deleteStarterOption(EquipmentType type, String optionId) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ? starterWeapons : starterHelmets;
        boolean removed = starters.removeIf(opt -> opt.optionId.equals(optionId));
        if (removed) {
            saveStarters();
            configService.updateStarters(starterWeapons, starterHelmets);
        }
        return removed;
    }

    /**
     * Gets a starter option by ID.
     */
    public Optional<StarterOptionConfig> getStarterOption(EquipmentType type, String optionId) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ? starterWeapons : starterHelmets;
        return starters.stream().filter(opt -> opt.optionId.equals(optionId)).findFirst();
    }

    /**
     * Sets the display name for a starter option.
     */
    public boolean setStarterDisplayName(EquipmentType type, String optionId, String displayName) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ? starterWeapons : starterHelmets;
        for (StarterOptionConfig opt : starters) {
            if (opt.optionId.equals(optionId)) {
                opt.displayName = displayName;
                saveStarters();
                configService.updateStarters(starterWeapons, starterHelmets);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the template for a starter option.
     */
    public boolean setStarterTemplate(EquipmentType type, String optionId, String templateId) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ? starterWeapons : starterHelmets;
        for (StarterOptionConfig opt : starters) {
            if (opt.optionId.equals(optionId)) {
                opt.templateId = templateId;
                saveStarters();
                configService.updateStarters(starterWeapons, starterHelmets);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the group for a starter option.
     */
    public boolean setStarterGroup(EquipmentType type, String optionId, String group) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ? starterWeapons : starterHelmets;
        for (StarterOptionConfig opt : starters) {
            if (opt.optionId.equals(optionId)) {
                opt.group = group;
                saveStarters();
                configService.updateStarters(starterWeapons, starterHelmets);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the level for a starter option.
     */
    public boolean setStarterLevel(EquipmentType type, String optionId, int level) {
        List<StarterOptionConfig> starters = type == EquipmentType.WEAPON ? starterWeapons : starterHelmets;
        for (StarterOptionConfig opt : starters) {
            if (opt.optionId.equals(optionId)) {
                opt.level = level;
                saveStarters();
                configService.updateStarters(starterWeapons, starterHelmets);
                return true;
            }
        }
        return false;
    }

    // ==================== Merchant Set Operations ====================

    /**
     * Sets the display name for a merchant template.
     */
    public boolean setMerchantTemplateDisplayName(String templateId, String displayName) {
        MerchantTemplateConfig config = merchantTemplates.get(templateId);
        if (config == null) {
            return false;
        }
        config.displayName = displayName;
        saveMerchants();
        return true;
    }

    // ==================== Getters for read access ====================

    public Map<String, EquipmentGroupConfig> getWeaponGroups() {
        return Collections.unmodifiableMap(weaponGroups);
    }

    public Map<String, EquipmentGroupConfig> getHelmetGroups() {
        return Collections.unmodifiableMap(helmetGroups);
    }

    public Map<String, ItemTemplateConfig> getItemTemplates() {
        return Collections.unmodifiableMap(itemTemplates);
    }

    public List<StarterOptionConfig> getStarterWeapons() {
        return Collections.unmodifiableList(starterWeapons);
    }

    public List<StarterOptionConfig> getStarterHelmets() {
        return Collections.unmodifiableList(starterHelmets);
    }

    public List<CombatWorldConfig> getCombatWorlds() {
        return Collections.unmodifiableList(combatWorlds);
    }

    // ==================== Merchant Operations ====================

    /**
     * Gets all merchant templates.
     */
    public Collection<MerchantTemplateConfig> getMerchantTemplates() {
        return Collections.unmodifiableCollection(merchantTemplates.values());
    }

    /**
     * Gets a merchant template by ID.
     */
    public Optional<MerchantTemplateConfig> getMerchantTemplate(String templateId) {
        return Optional.ofNullable(merchantTemplates.get(templateId));
    }

    /**
     * Gets a random merchant template.
     */
    public Optional<MerchantTemplateConfig> getRandomMerchantTemplate() {
        if (merchantTemplates.isEmpty()) {
            return Optional.empty();
        }
        List<MerchantTemplateConfig> templates = new ArrayList<>(merchantTemplates.values());
        return Optional.of(templates.get(new Random().nextInt(templates.size())));
    }

    /**
     * Creates a new merchant template.
     */
    public boolean createMerchantTemplate(String templateId, String displayName) {
        if (merchantTemplates.containsKey(templateId)) {
            return false;
        }

        MerchantTemplateConfig config = new MerchantTemplateConfig();
        config.templateId = templateId;
        config.displayName = displayName != null ? displayName : templateId;
        config.trades = new ArrayList<>();

        merchantTemplates.put(templateId, config);
        saveMerchants();
        return true;
    }

    /**
     * Deletes a merchant template.
     */
    public boolean deleteMerchantTemplate(String templateId) {
        if (!merchantTemplates.containsKey(templateId)) {
            return false;
        }

        merchantTemplates.remove(templateId);
        saveMerchants();
        return true;
    }

    /**
     * Adds a trade to a merchant template.
     */
    public boolean addMerchantTrade(String templateId, MerchantTradeConfig trade) {
        MerchantTemplateConfig config = merchantTemplates.get(templateId);
        if (config == null) {
            return false;
        }

        config.trades.add(trade);
        saveMerchants();
        return true;
    }

    /**
     * Removes a trade from a merchant template by index.
     */
    public boolean removeMerchantTrade(String templateId, int index) {
        MerchantTemplateConfig config = merchantTemplates.get(templateId);
        if (config == null) {
            return false;
        }

        if (index < 0 || index >= config.trades.size()) {
            return false;
        }

        config.trades.remove(index);
        saveMerchants();
        return true;
    }

    /**
     * Captures an item from hand for use in merchant trades.
     * Stores the item as a template in data/items/.
     *
     * @return the generated template ID, or null if failed
     */
    public String captureItemForMerchant(ItemStack item, String merchantTemplateId) {
        // Generate unique template ID
        String templateId = "merchant_" + merchantTemplateId + "_" + item.getType().name().toLowerCase() + "_" + System.currentTimeMillis();

        // Create item template (without equipment group association)
        ItemTemplateConfig template = ItemTemplateConfig.fromItemStack(item, templateId, null, 0);
        itemTemplates.put(templateId, template);

        // Save the template
        saveItemTemplate(template);

        return templateId;
    }

    private void loadMerchants() {
        merchantTemplates.clear();

        File file = dataPath.resolve("merchants.yml").toFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection templatesSection = yaml.getConfigurationSection("templates");
        if (templatesSection == null) {
            return;
        }

        for (String templateId : templatesSection.getKeys(false)) {
            ConfigurationSection section = templatesSection.getConfigurationSection(templateId);
            if (section == null) continue;

            MerchantTemplateConfig config = new MerchantTemplateConfig();
            config.templateId = templateId;
            config.displayName = section.getString("displayName", templateId);

            List<Map<?, ?>> tradesList = section.getMapList("trades");
            for (Map<?, ?> tradeMap : tradesList) {
                MerchantTradeConfig trade = new MerchantTradeConfig();
                trade.resultItem = (String) tradeMap.get("resultItem");
                Object resultAmount = tradeMap.get("resultAmount");
                trade.resultAmount = resultAmount instanceof Number ? ((Number) resultAmount).intValue() : 1;
                trade.costItem = (String) tradeMap.get("costItem");
                Object costAmount = tradeMap.get("costAmount");
                trade.costAmount = costAmount instanceof Number ? ((Number) costAmount).intValue() : 1;
                Object maxUses = tradeMap.get("maxUses");
                trade.maxUses = maxUses instanceof Number ? ((Number) maxUses).intValue() : 10;
                config.trades.add(trade);
            }

            merchantTemplates.put(templateId, config);
        }
    }

    private void saveMerchants() {
        File file = dataPath.resolve("merchants.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("Auto-generated by KedamaSurvivors\nUse in-game commands: /vrs admin merchant ...\n");

        for (MerchantTemplateConfig config : merchantTemplates.values()) {
            String prefix = "templates." + config.templateId;
            yaml.set(prefix + ".displayName", config.displayName);

            List<Map<String, Object>> tradesList = new ArrayList<>();
            for (MerchantTradeConfig trade : config.trades) {
                Map<String, Object> tradeMap = new LinkedHashMap<>();
                tradeMap.put("resultItem", trade.resultItem);
                tradeMap.put("resultAmount", trade.resultAmount);
                tradeMap.put("costItem", trade.costItem);
                tradeMap.put("costAmount", trade.costAmount);
                tradeMap.put("maxUses", trade.maxUses);
                tradesList.add(tradeMap);
            }
            yaml.set(prefix + ".trades", tradesList);
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save merchants.yml", e);
        }
    }

    // ==================== Merchant Pool Operations ====================

    /**
     * Creates a new merchant item pool.
     */
    public boolean createMerchantPool(String poolId) {
        if (merchantPools.containsKey(poolId)) {
            return false;
        }

        merchantPools.put(poolId, new MerchantItemPool(poolId));
        saveMerchantPools();
        return true;
    }

    /**
     * Deletes a merchant item pool.
     */
    public boolean deleteMerchantPool(String poolId) {
        if (!merchantPools.containsKey(poolId)) {
            return false;
        }

        merchantPools.remove(poolId);
        saveMerchantPools();
        return true;
    }

    /**
     * Gets a merchant pool by ID.
     */
    public Optional<MerchantItemPool> getMerchantPool(String poolId) {
        return Optional.ofNullable(merchantPools.get(poolId));
    }

    /**
     * Gets a random merchant pool.
     */
    public Optional<MerchantItemPool> getRandomMerchantPool() {
        if (merchantPools.isEmpty()) {
            return Optional.empty();
        }
        List<MerchantItemPool> pools = new ArrayList<>(merchantPools.values());
        return Optional.of(pools.get(new Random().nextInt(pools.size())));
    }

    /**
     * Gets all merchant pools.
     */
    public Collection<MerchantItemPool> getMerchantPools() {
        return Collections.unmodifiableCollection(merchantPools.values());
    }

    /**
     * Adds an item to a merchant pool.
     *
     * @param poolId the pool ID
     * @param templateId the item template ID
     * @param price the price in coins
     * @param weight the weight for selection
     * @return true if added successfully
     */
    public boolean addItemToPool(String poolId, String templateId, int price, double weight) {
        MerchantItemPool pool = merchantPools.get(poolId);
        if (pool == null) {
            return false;
        }

        WeightedShopItem item = new WeightedShopItem(templateId, weight, price);
        pool.addItem(item);
        saveMerchantPools();
        return true;
    }

    /**
     * Removes an item from a merchant pool by index.
     *
     * @param poolId the pool ID
     * @param index the item index
     * @return the removed item, or null if not found
     */
    public WeightedShopItem removeItemFromPool(String poolId, int index) {
        MerchantItemPool pool = merchantPools.get(poolId);
        if (pool == null) {
            return null;
        }

        WeightedShopItem removed = pool.removeItem(index);
        if (removed != null) {
            saveMerchantPools();
        }
        return removed;
    }

    /**
     * Captures an item from player hand and adds it to a merchant pool.
     *
     * @param item the item to capture
     * @param poolId the pool to add to
     * @param price the price
     * @param weight the weight
     * @return the generated template ID, or null if failed
     */
    public String captureItemToPool(ItemStack item, String poolId, int price, double weight) {
        MerchantItemPool pool = merchantPools.get(poolId);
        if (pool == null) {
            return null;
        }

        // Generate unique template ID
        String templateId = "pool_" + poolId + "_" + item.getType().name().toLowerCase() + "_" + System.currentTimeMillis();

        // Create item template (without equipment group association)
        ItemTemplateConfig template = ItemTemplateConfig.fromItemStack(item, templateId, null, 0);
        itemTemplates.put(templateId, template);

        // Save the template
        saveItemTemplate(template);

        // Add to pool
        WeightedShopItem shopItem = new WeightedShopItem(templateId, weight, price);
        pool.addItem(shopItem);
        saveMerchantPools();

        return templateId;
    }

    private void loadMerchantPools() {
        merchantPools.clear();

        File file = dataPath.resolve("merchant_pools.yml").toFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection poolsSection = yaml.getConfigurationSection("pools");
        if (poolsSection == null) {
            return;
        }

        for (String poolId : poolsSection.getKeys(false)) {
            ConfigurationSection section = poolsSection.getConfigurationSection(poolId);
            if (section == null) continue;

            MerchantItemPool pool = new MerchantItemPool(poolId);

            List<Map<?, ?>> itemsList = section.getMapList("items");
            for (Map<?, ?> itemMap : itemsList) {
                String templateId = (String) itemMap.get("templateId");
                Object weightObj = itemMap.get("weight");
                double weight = weightObj instanceof Number ? ((Number) weightObj).doubleValue() : 1.0;
                Object priceObj = itemMap.get("price");
                int price = priceObj instanceof Number ? ((Number) priceObj).intValue() : 10;

                pool.addItem(new WeightedShopItem(templateId, weight, price));
            }

            merchantPools.put(poolId, pool);
        }
    }

    private void saveMerchantPools() {
        File file = dataPath.resolve("merchant_pools.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("Auto-generated by KedamaSurvivors\nMerchant item pools for weighted selection\nUse in-game commands: /vrs admin merchant pool ...\n");

        for (MerchantItemPool pool : merchantPools.values()) {
            String prefix = "pools." + pool.getPoolId();

            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (WeightedShopItem item : pool.getItems()) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("templateId", item.getItemTemplateId());
                itemMap.put("weight", item.getWeight());
                itemMap.put("price", item.getPrice());
                itemsList.add(itemMap);
            }
            yaml.set(prefix + ".items", itemsList);
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save merchant_pools.yml", e);
        }
    }
}
