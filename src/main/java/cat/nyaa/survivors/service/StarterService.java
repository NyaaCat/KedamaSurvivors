package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
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

        if (weaponOptionId == null || helmetOptionId == null) {
            i18n.send(player, "error.select_starters_first");
            return false;
        }

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
                    ": weapon=" + weaponOptionId + ", helmet=" + helmetOptionId);
            return false;
        }

        // Grant weapon
        ItemStack weapon = createEquipmentItem(weaponConfig, "weapon");
        if (weapon != null) {
            grantWeapon(player, weapon);
        }

        // Grant helmet
        ItemStack helmet = createEquipmentItem(helmetConfig, "helmet");
        if (helmet != null) {
            grantHelmet(player, helmet);
        }

        return true;
    }

    /**
     * Creates an equipment item with PDC markers.
     */
    private ItemStack createEquipmentItem(ConfigService.StarterOptionConfig option, String type) {
        Material material = option.displayMaterial;
        if (material == null) {
            material = "weapon".equals(type) ? Material.IRON_SWORD : Material.IRON_HELMET;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Set display name
            if (option.displayItemName != null) {
                meta.displayName(net.kyori.adventure.text.Component.text(
                        option.displayItemName.replace('&', '§')));
            }

            // Add PDC markers
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyVrsItem, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyEquipmentType, PersistentDataType.STRING, type);
            pdc.set(keyEquipmentGroup, PersistentDataType.STRING, option.group);
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

    // PDC key getters for external use
    public NamespacedKey getKeyVrsItem() { return keyVrsItem; }
    public NamespacedKey getKeyEquipmentType() { return keyEquipmentType; }
    public NamespacedKey getKeyEquipmentGroup() { return keyEquipmentGroup; }
    public NamespacedKey getKeyEquipmentLevel() { return keyEquipmentLevel; }
}
