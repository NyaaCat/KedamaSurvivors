package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ItemTemplateConfig;
import cat.nyaa.survivors.gui.StarterHelmetGui;
import cat.nyaa.survivors.gui.StarterWeaponGui;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Service for managing starter equipment selection and granting.
 */
public class StarterService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;

    // PDC keys for marking VRS equipment
    private final NamespacedKey keyVrsItem;
    private final NamespacedKey keyEquipmentType;
    private final NamespacedKey keyEquipmentGroup;
    private final NamespacedKey keyEquipmentLevel;

    public StarterService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();

        // Initialize PDC keys
        this.keyVrsItem = new NamespacedKey(plugin, "vrs_item");
        this.keyEquipmentType = new NamespacedKey(plugin, "equipment_type");
        this.keyEquipmentGroup = new NamespacedKey(plugin, "equipment_group");
        this.keyEquipmentLevel = new NamespacedKey(plugin, "equipment_level");
    }

    /**
     * Opens the weapon selection GUI for a player.
     */
    public void openWeaponGui(Player player) {
        PlayerState playerState = state.getOrCreatePlayer(player.getUniqueId(), player.getName());

        // Allow selection in lobby or cooldown (after death)
        if (playerState.getMode() != PlayerMode.LOBBY && playerState.getMode() != PlayerMode.COOLDOWN) {
            i18n.send(player, "error.not_in_lobby");
            return;
        }

        StarterWeaponGui gui = new StarterWeaponGui(plugin, player, playerState);
        gui.open();
    }

    /**
     * Opens the helmet selection GUI for a player.
     */
    public void openHelmetGui(Player player) {
        PlayerState playerState = state.getOrCreatePlayer(player.getUniqueId(), player.getName());

        // Allow selection in lobby or cooldown (after death)
        if (playerState.getMode() != PlayerMode.LOBBY && playerState.getMode() != PlayerMode.COOLDOWN) {
            i18n.send(player, "error.not_in_lobby");
            return;
        }

        if (config.isRequireWeaponFirst() && playerState.getStarterWeaponOptionId() == null) {
            i18n.send(player, "error.weapon_first");
            return;
        }

        StarterHelmetGui gui = new StarterHelmetGui(plugin, player, playerState);
        gui.open();
    }

    /**
     * Grants the selected starter equipment to a player.
     * Called when a run starts.
     * @return true if equipment was granted successfully
     */
    public boolean grantStarterEquipment(Player player, PlayerState playerState) {
        String weaponOptionId = playerState.getStarterWeaponOptionId();
        String helmetOptionId = playerState.getStarterHelmetOptionId();

        plugin.getLogger().info("Granting starters for " + player.getName() +
            " - weapon=" + weaponOptionId + ", helmet=" + helmetOptionId);

        if (weaponOptionId == null || helmetOptionId == null) {
            plugin.getLogger().warning("Starter selection incomplete for " + player.getName() +
                " - weapon=" + weaponOptionId + ", helmet=" + helmetOptionId);
            i18n.send(player, "error.select_starters_first");
            return false;
        }

        // Debug: Log available starters
        plugin.getLogger().info("Available weapons: " + config.getStarterWeapons().size() +
            ", helmets: " + config.getStarterHelmets().size());

        // Find weapon config
        ConfigService.StarterOptionConfig weaponConfig = config.getStarterWeapons().stream()
                .filter(w -> w.optionId.equals(weaponOptionId))
                .findFirst()
                .orElse(null);

        // Find helmet config
        ConfigService.StarterOptionConfig helmetConfig = config.getStarterHelmets().stream()
                .filter(h -> h.optionId.equals(helmetOptionId))
                .findFirst()
                .orElse(null);

        if (weaponConfig == null || helmetConfig == null) {
            plugin.getLogger().warning("Invalid starter selection for " + player.getName() +
                    ": weapon=" + weaponOptionId + " (found=" + (weaponConfig != null) + ")" +
                    ", helmet=" + helmetOptionId + " (found=" + (helmetConfig != null) + ")");
            return false;
        }

        plugin.getLogger().info("Found configs - weapon: " + weaponConfig.optionId +
            " (template=" + weaponConfig.templateId + "), helmet: " + helmetConfig.optionId +
            " (template=" + helmetConfig.templateId + ")");

        // Grant weapon
        ItemStack weapon = createEquipmentItem(weaponConfig, "weapon");
        if (weapon != null) {
            grantWeapon(player, weapon);
            plugin.getLogger().info("Granted weapon to " + player.getName() + ": " + weapon.getType());
        } else {
            plugin.getLogger().warning("Failed to create weapon item for " + player.getName());
        }

        // Grant helmet
        ItemStack helmet = createEquipmentItem(helmetConfig, "helmet");
        if (helmet != null) {
            grantHelmet(player, helmet);
            plugin.getLogger().info("Granted helmet to " + player.getName() + ": " + helmet.getType());
        } else {
            plugin.getLogger().warning("Failed to create helmet item for " + player.getName());
        }

        return true;
    }

    /**
     * Grants a single starter item immediately (for GUI selection).
     * Removes any existing VRS item of the same type first, but respects slot location.
     * @param player The player to grant the item to
     * @param option The starter option config
     * @param type Either "weapon" or "helmet"
     */
    public void grantSingleStarterItem(Player player, ConfigService.StarterOptionConfig option, String type) {
        ItemStack item = createEquipmentItem(option, type);
        if (item == null) {
            plugin.getLogger().warning("Failed to create " + type + " item for " + player.getName());
            return;
        }

        // Find existing VRS equipment slot before removing (player may have moved it)
        int existingSlot = findVrsEquipmentSlot(player, type);

        // Remove existing VRS equipment of this type
        removeVrsEquipment(player, type);

        if ("weapon".equals(type)) {
            // Place new weapon in same slot, or find empty slot
            if (existingSlot >= 0) {
                player.getInventory().setItem(existingSlot, item);
            } else {
                int emptySlot = findEmptySlot(player);
                if (emptySlot >= 0) {
                    player.getInventory().setItem(emptySlot, item);
                } else {
                    player.getInventory().setItem(0, item);
                }
            }
        } else if ("helmet".equals(type)) {
            // Place new helmet in same slot, or use armor slot
            if (existingSlot >= 0) {
                player.getInventory().setItem(existingSlot, item);
            } else {
                player.getInventory().setHelmet(item);
            }
        }

        plugin.getLogger().info("Granted " + type + " to " + player.getName() + ": " + item.getType());
    }

    /**
     * Creates an equipment item with PDC markers.
     * First tries to use the templateId to get a full NBT item from AdminConfigService,
     * then falls back to creating a basic item from displayMaterial.
     */
    private ItemStack createEquipmentItem(ConfigService.StarterOptionConfig option, String type) {
        ItemStack item = null;

        // Try to get item from template first
        if (option.templateId != null && !option.templateId.isEmpty()) {
            Optional<ItemTemplateConfig> templateOpt =
                plugin.getAdminConfigService().getItemTemplate(option.templateId);
            if (templateOpt.isPresent()) {
                item = templateOpt.get().toItemStack();
                plugin.getLogger().info("Created starter item from template: " + option.templateId);
            } else {
                plugin.getLogger().warning("Item template not found: " + option.templateId +
                    " for starter option: " + option.optionId);
            }
        }

        // Fallback to displayMaterial if template not found
        if (item == null) {
            Material material = option.displayMaterial;
            if (material == null) {
                material = "weapon".equals(type) ? Material.IRON_SWORD : Material.IRON_HELMET;
            }
            item = new ItemStack(material);
            plugin.getLogger().info("Created fallback starter item: " + material +
                " for option: " + option.optionId);

            // Set display name from config
            if (option.displayItemName != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(net.kyori.adventure.text.Component.text(
                        option.displayItemName.replace('&', '§')));
                    item.setItemMeta(meta);
                }
            }
        }

        // Add PDC markers to identify as VRS equipment
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyVrsItem, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyEquipmentType, PersistentDataType.STRING, type);
            pdc.set(keyEquipmentGroup, PersistentDataType.STRING, option.group != null ? option.group : "default");
            pdc.set(keyEquipmentLevel, PersistentDataType.INTEGER, option.level);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Grants a weapon to the player, respecting existing VRS weapon location.
     * If player has an existing VRS weapon (possibly moved), replaces it in-place.
     * Otherwise finds an empty slot, preferring hotbar.
     */
    private void grantWeapon(Player player, ItemStack weapon) {
        PlayerInventory inv = player.getInventory();

        // Find existing VRS weapon slot first (player may have moved it)
        int existingSlot = findVrsEquipmentSlot(player, "weapon");

        // Remove any existing VRS weapon
        removeVrsEquipment(player, "weapon");

        if (existingSlot >= 0) {
            // Replace in the same slot where VRS weapon was found
            inv.setItem(existingSlot, weapon);
        } else {
            // No existing VRS weapon - find an empty slot
            int emptySlot = findEmptySlot(player);
            if (emptySlot >= 0) {
                inv.setItem(emptySlot, weapon);
            } else {
                // Inventory full - place in slot 0 with warning
                inv.setItem(0, weapon);
                plugin.getLogger().warning("Player " + player.getName() +
                    " had full inventory, weapon placed in slot 0");
            }
        }
    }

    /**
     * Grants a helmet to the player, respecting existing VRS helmet location.
     * If player has an existing VRS helmet (possibly moved from armor slot), replaces it in-place.
     * Otherwise uses the armor helmet slot.
     */
    private void grantHelmet(Player player, ItemStack helmet) {
        PlayerInventory inv = player.getInventory();

        // Find existing VRS helmet slot first (player may have moved it from armor slot)
        int existingSlot = findVrsEquipmentSlot(player, "helmet");

        // Remove any existing VRS helmet
        removeVrsEquipment(player, "helmet");

        if (existingSlot >= 0) {
            // Replace in the same slot where VRS helmet was found
            inv.setItem(existingSlot, helmet);
        } else {
            // No existing VRS helmet - use armor helmet slot
            inv.setHelmet(helmet);
        }
    }

    /**
     * Finds the slot containing VRS equipment of a specific type.
     * Scans main inventory slots (0-35) to find equipment even if player moved it.
     * @param player The player to search
     * @param type Equipment type ("weapon" or "helmet")
     * @return slot index, or -1 if not found
     */
    private int findVrsEquipmentSlot(Player player, String type) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isVrsEquipment(item, type)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first empty slot, preferring hotbar (0-8), then main inventory (9-35).
     * @param player The player to search
     * @return slot index, or -1 if inventory is full
     */
    private int findEmptySlot(Player player) {
        PlayerInventory inv = player.getInventory();
        // Prefer hotbar slots (0-8)
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType().isAir()) {
                return i;
            }
        }
        // Fall back to main inventory (9-35)
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes VRS equipment of a specific type from the player.
     */
    public void removeVrsEquipment(Player player, String type) {
        PlayerInventory inv = player.getInventory();

        // Check all inventory slots
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isVrsEquipment(item, type)) {
                inv.setItem(i, null);
            }
        }

        // Check armor slots for helmet
        if ("helmet".equals(type)) {
            ItemStack helmet = inv.getHelmet();
            if (isVrsEquipment(helmet, type)) {
                inv.setHelmet(null);
            }
        }
    }

    /**
     * Removes all VRS equipment from the player.
     */
    public void removeAllVrsEquipment(Player player) {
        removeVrsEquipment(player, "weapon");
        removeVrsEquipment(player, "helmet");
    }

    /**
     * Checks if an item is VRS equipment of a specific type.
     */
    public boolean isVrsEquipment(ItemStack item, String type) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(keyVrsItem, PersistentDataType.BYTE)) {
            return false;
        }

        if (type == null) {
            return true; // Any VRS item
        }

        String itemType = pdc.get(keyEquipmentType, PersistentDataType.STRING);
        return type.equals(itemType);
    }

    /**
     * Checks if an item is any VRS equipment.
     */
    public boolean isVrsItem(ItemStack item) {
        return isVrsEquipment(item, null);
    }

    /**
     * Gets the equipment group from a VRS item.
     */
    public String getEquipmentGroup(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(keyEquipmentGroup, PersistentDataType.STRING);
    }

    /**
     * Gets the equipment level from a VRS item.
     */
    public int getEquipmentLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer()
                .get(keyEquipmentLevel, PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    /**
     * Gets the equipment type from a VRS item.
     */
    public String getEquipmentType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(keyEquipmentType, PersistentDataType.STRING);
    }

    /**
     * Grants an upgraded equipment item to a player.
     * Used by UpgradeService when player levels up.
     * First tries to use the templateId to get a full NBT item from AdminConfigService,
     * then falls back to creating a basic item from material tier.
     *
     * @param player     The player to grant to
     * @param templateId The template ID for the item
     * @param type       "weapon" or "helmet"
     * @param group      Equipment group
     * @param level      New equipment level
     * @return The granted item, or null if failed
     */
    public ItemStack grantUpgradeItem(Player player, String templateId, String type, String group, int level) {
        ItemStack item = null;

        // Try to get item from template first (preserves full NBT)
        if (templateId != null && !templateId.isEmpty()) {
            Optional<ItemTemplateConfig> templateOpt =
                plugin.getAdminConfigService().getItemTemplate(templateId);
            if (templateOpt.isPresent()) {
                item = templateOpt.get().toItemStack();
                plugin.getLogger().info("Created upgrade item from template: " + templateId);
            } else {
                plugin.getLogger().warning("Item template not found for upgrade: " + templateId);
            }
        }

        // Fallback to material-based creation if template not found
        if (item == null) {
            Material material = getMaterialForLevel(type, level);
            item = new ItemStack(material);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Set display name based on group and level
                String displayName = getDisplayName(type, group, level);
                meta.displayName(net.kyori.adventure.text.Component.text(displayName));

                // Add lore with level info
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                lore.add(net.kyori.adventure.text.Component.text(
                        i18n.get("item.level_indicator", "level", level)));
                meta.lore(lore);
                item.setItemMeta(meta);
            }

            plugin.getLogger().info("Created fallback upgrade item: " + material);
        }

        // Add PDC markers (always applied, even for template items)
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyVrsItem, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyEquipmentType, PersistentDataType.STRING, type);
            pdc.set(keyEquipmentGroup, PersistentDataType.STRING, group);
            pdc.set(keyEquipmentLevel, PersistentDataType.INTEGER, level);
            item.setItemMeta(meta);
        }

        // Grant to appropriate slot
        if ("weapon".equals(type)) {
            grantWeapon(player, item);
        } else if ("helmet".equals(type)) {
            grantHelmet(player, item);
        }

        return item;
    }

    /**
     * Gets the material for an equipment type at a given level.
     */
    private Material getMaterialForLevel(String type, int level) {
        if ("weapon".equals(type)) {
            if (level >= 5) return Material.NETHERITE_SWORD;
            if (level >= 4) return Material.DIAMOND_SWORD;
            if (level >= 3) return Material.GOLDEN_SWORD;
            if (level >= 2) return Material.IRON_SWORD;
            return Material.STONE_SWORD;
        } else {
            if (level >= 5) return Material.NETHERITE_HELMET;
            if (level >= 4) return Material.DIAMOND_HELMET;
            if (level >= 3) return Material.GOLDEN_HELMET;
            if (level >= 2) return Material.IRON_HELMET;
            return Material.LEATHER_HELMET;
        }
    }

    /**
     * Gets the display name for equipment.
     */
    private String getDisplayName(String type, String group, int level) {
        ConfigService.EquipmentGroupConfig groupConfig;
        if ("weapon".equals(type)) {
            groupConfig = config.getWeaponGroups().get(group);
        } else {
            groupConfig = config.getHelmetGroups().get(group);
        }

        String groupName = groupConfig != null ? groupConfig.displayName : group;
        return "§6" + groupName + " §7Lv." + level;
    }

    /**
     * Regrants equipment to a player based on their tracked state.
     * Used when a player loses their equipment but needs it back.
     *
     * @param player      The player to regrant to
     * @param playerState The player's state with tracked equipment info
     */
    public void regrantEquipmentFromState(Player player, PlayerState playerState) {
        String weaponGroup = playerState.getWeaponGroup();
        int weaponLevel = playerState.getWeaponLevel();
        String helmetGroup = playerState.getHelmetGroup();
        int helmetLevel = playerState.getHelmetLevel();

        // Regrant weapon if tracked
        if (weaponGroup != null && weaponLevel > 0) {
            // Check if player already has weapon
            if (!isVrsEquipment(player.getInventory().getItemInMainHand(), "weapon")) {
                grantUpgradeItem(player, null, "weapon", weaponGroup, weaponLevel);
                plugin.getLogger().info("Regranted weapon to " + player.getName() +
                    ": group=" + weaponGroup + ", level=" + weaponLevel);
            }
        }

        // Regrant helmet if tracked
        if (helmetGroup != null && helmetLevel > 0) {
            // Check if player already has helmet
            if (!isVrsEquipment(player.getInventory().getHelmet(), "helmet")) {
                grantUpgradeItem(player, null, "helmet", helmetGroup, helmetLevel);
                plugin.getLogger().info("Regranted helmet to " + player.getName() +
                    ": group=" + helmetGroup + ", level=" + helmetLevel);
            }
        }
    }

    /**
     * Checks if a player has their tracked equipment.
     * Returns true if both weapon and helmet match the tracked state.
     */
    public boolean hasTrackedEquipment(Player player, PlayerState playerState) {
        // Check weapon
        String weaponGroup = playerState.getWeaponGroup();
        int weaponLevel = playerState.getWeaponLevel();
        if (weaponGroup != null && weaponLevel > 0) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (!isVrsEquipment(weapon, "weapon")) {
                return false;
            }
            String itemGroup = getEquipmentGroup(weapon);
            int itemLevel = getEquipmentLevel(weapon);
            if (!weaponGroup.equals(itemGroup) || weaponLevel != itemLevel) {
                return false;
            }
        }

        // Check helmet
        String helmetGroup = playerState.getHelmetGroup();
        int helmetLevel = playerState.getHelmetLevel();
        if (helmetGroup != null && helmetLevel > 0) {
            ItemStack helmet = player.getInventory().getHelmet();
            if (!isVrsEquipment(helmet, "helmet")) {
                return false;
            }
            String itemGroup = getEquipmentGroup(helmet);
            int itemLevel = getEquipmentLevel(helmet);
            if (!helmetGroup.equals(itemGroup) || helmetLevel != itemLevel) {
                return false;
            }
        }

        return true;
    }

    // PDC key getters for external use
    public NamespacedKey getKeyVrsItem() { return keyVrsItem; }
    public NamespacedKey getKeyEquipmentType() { return keyEquipmentType; }
    public NamespacedKey getKeyEquipmentGroup() { return keyEquipmentGroup; }
    public NamespacedKey getKeyEquipmentLevel() { return keyEquipmentLevel; }
}
