package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ConfigService.EquipmentGroupConfig;
import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
import cat.nyaa.survivors.config.ConfigService.StarterOptionConfig;
import cat.nyaa.survivors.config.ConfigService.CombatWorldConfig;
import cat.nyaa.survivors.config.ConfigService.MerchantTemplateConfig;
import cat.nyaa.survivors.config.ConfigService.MerchantTradeConfig;
import cat.nyaa.survivors.config.ItemTemplateConfig;
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

        if (needsMigration) {
            plugin.getLogger().info("Data files not found, migrating from config.yml...");
            migrateFromConfig();
        } else {
            loadEquipmentGroups();
            loadArchetypes();
            loadStarters();
            loadWorlds();
        }

        loadItemTemplates();
        loadMerchants();

        plugin.getLogger().info("Loaded " + weaponGroups.size() + " weapon groups, " +
                helmetGroups.size() + " helmet groups, " +
                itemTemplates.size() + " item templates, " +
                archetypes.size() + " archetypes, " +
                merchantTemplates.size() + " merchant templates");
    }

    /**
     * Migrates existing config.yml data to dedicated data files.
     */
    private void migrateFromConfig() {
        // Migrate weapon groups
        weaponGroups.clear();
        weaponGroups.putAll(configService.getWeaponGroups());

        // Migrate helmet groups
        helmetGroups.clear();
        helmetGroups.putAll(configService.getHelmetGroups());

        // Migrate archetypes
        archetypes.clear();
        archetypes.putAll(configService.getEnemyArchetypes());

        // Migrate starters
        starterWeapons.clear();
        starterWeapons.addAll(configService.getStarterWeapons());

        starterHelmets.clear();
        starterHelmets.addAll(configService.getStarterHelmets());

        // Migrate combat worlds
        combatWorlds.clear();
        combatWorlds.addAll(configService.getCombatWorlds());

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
     * Creates a new enemy archetype.
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
        config.xpBase = 10;
        config.xpPerLevel = 5;
        config.coinBase = 1;
        config.coinPerLevel = 1;
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
     * Sets rewards for an archetype.
     */
    public boolean setArchetypeRewards(String id, int xpBase, int xpPerLevel, int coinBase, int coinPerLevel, double permaChance) {
        EnemyArchetypeConfig config = archetypes.get(id);
        if (config == null) {
            return false;
        }

        config.xpBase = xpBase;
        config.xpPerLevel = xpPerLevel;
        config.coinBase = coinBase;
        config.coinPerLevel = coinPerLevel;
        config.permaScoreChance = permaChance;

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
        updateConfigService();
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
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : yaml.getKeys(false)) {
                map.put(key, yaml.get(key));
            }

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

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            EnemyArchetypeConfig config = new EnemyArchetypeConfig();
            config.archetypeId = id;
            config.enemyType = section.getString("entityType", "minecraft:zombie");
            config.weight = section.getDouble("weight", 1.0);
            config.spawnCommands = new ArrayList<>(section.getStringList("spawnCommands"));

            ConfigurationSection rewards = section.getConfigurationSection("rewards");
            if (rewards != null) {
                config.xpBase = rewards.getInt("xpBase", 10);
                config.xpPerLevel = rewards.getInt("xpPerLevel", 5);
                config.coinBase = rewards.getInt("coinBase", 1);
                config.coinPerLevel = rewards.getInt("coinPerLevel", 1);
                config.permaScoreChance = rewards.getDouble("permaScoreChance", 0.01);
            }

            archetypes.put(id, config);
        }
    }

    private void saveArchetypes() {
        File file = dataPath.resolve("archetypes.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.options().header("Auto-generated by KedamaSurvivors\nUse in-game commands: /vrs admin spawner ...\n");

        for (EnemyArchetypeConfig config : archetypes.values()) {
            String id = config.archetypeId;
            yaml.set(id + ".entityType", config.enemyType);
            yaml.set(id + ".weight", config.weight);
            yaml.set(id + ".spawnCommands", config.spawnCommands);
            yaml.set(id + ".rewards.xpBase", config.xpBase);
            yaml.set(id + ".rewards.xpPerLevel", config.xpPerLevel);
            yaml.set(id + ".rewards.coinBase", config.coinBase);
            yaml.set(id + ".rewards.coinPerLevel", config.coinPerLevel);
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

        File file = dataPath.resolve("starters.yml").toFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadStarterList(yaml.getMapList("weapons"), starterWeapons);
        loadStarterList(yaml.getMapList("helmets"), starterHelmets);
    }

    @SuppressWarnings("unchecked")
    private void loadStarterList(List<Map<?, ?>> list, List<StarterOptionConfig> target) {
        for (Map<?, ?> map : list) {
            StarterOptionConfig opt = new StarterOptionConfig();
            opt.optionId = (String) map.get("optionId");
            opt.displayName = (String) map.get("displayName");
            opt.templateId = (String) map.get("templateId");
            opt.group = (String) map.get("group");
            Object levelVal = map.get("level");
            opt.level = levelVal instanceof Number ? ((Number) levelVal).intValue() : 1;

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

            Map<String, Double> bounds = new LinkedHashMap<>();
            bounds.put("minX", world.minX);
            bounds.put("maxX", world.maxX);
            bounds.put("minZ", world.minZ);
            bounds.put("maxZ", world.maxZ);
            map.put("spawnBounds", bounds);

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
        configService.updateWeaponGroups(weaponGroups);
        configService.updateHelmetGroups(helmetGroups);
        configService.updateEnemyArchetypes(archetypes);
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
}
