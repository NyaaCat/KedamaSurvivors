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

        if (playerState.getMode() != PlayerMode.LOBBY) {
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

        if (playerState.getMode() != PlayerMode.LOBBY) {
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
     * Removes any existing VRS item of the same type first.
     * @param player The player to grant the item to
     * @param option The starter option config
     * @param type Either "weapon" or "helmet"
     */
    public void grantSingleStarterItem(Player player, ConfigService.StarterOptionConfig option, String type) {
        // Remove existing VRS item of this type
        removeVrsEquipment(player, type);

        // Create and grant new item
        ItemStack item = createEquipmentItem(option, type);
        if (item != null) {
            if ("weapon".equals(type)) {
                // Directly set in slot 0 (grantWeapon would remove again which we already did)
                player.getInventory().setItem(0, item);
            } else if ("helmet".equals(type)) {
                // Directly set helmet (grantHelmet would remove again which we already did)
                player.getInventory().setHelmet(item);
            }
            plugin.getLogger().info("Granted " + type + " to " + player.getName() + ": " + item.getType());
        } else {
            plugin.getLogger().warning("Failed to create " + type + " item for " + player.getName());
        }
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
     * Grants a weapon to the player's main hand.
     */
    private void grantWeapon(Player player, ItemStack weapon) {
        PlayerInventory inv = player.getInventory();

        // Remove any existing VRS weapon
        removeVrsEquipment(player, "weapon");

        // Set in main hand (slot 0)
        inv.setItem(0, weapon);
    }

    /**
     * Grants a helmet to the player's helmet slot.
     */
    private void grantHelmet(Player player, ItemStack helmet) {
        PlayerInventory inv = player.getInventory();

        // Remove any existing VRS helmet
        removeVrsEquipment(player, "helmet");

        // Set in helmet slot
        inv.setHelmet(helmet);
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
     *
     * @param player     The player to grant to
     * @param templateId The template ID (currently unused, using material lookup)
     * @param type       "weapon" or "helmet"
     * @param group      Equipment group
     * @param level      New equipment level
     * @return The granted item, or null if failed
     */
    public ItemStack grantUpgradeItem(Player player, String templateId, String type, String group, int level) {
        // Determine material based on level (simple tier system)
        Material material = getMaterialForLevel(type, level);

        ItemStack item = new ItemStack(material);
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

            // Add PDC markers
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

    // PDC key getters for external use
    public NamespacedKey getKeyVrsItem() { return keyVrsItem; }
    public NamespacedKey getKeyEquipmentType() { return keyEquipmentType; }
    public NamespacedKey getKeyEquipmentGroup() { return keyEquipmentGroup; }
    public NamespacedKey getKeyEquipmentLevel() { return keyEquipmentLevel; }
}
