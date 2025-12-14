package cat.nyaa.survivors.gui;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI for selecting starter weapon.
 */
public class StarterWeaponGui extends GuiHolder {

    public static final String GUI_TYPE = "starter_weapon";

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;
    private final PlayerState playerState;
    private final Map<Integer, String> slotToOptionId = new HashMap<>();

    public StarterWeaponGui(KedamaSurvivorsPlugin plugin, Player player, PlayerState playerState) {
        super(player);
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.config = plugin.getConfigService();
        this.playerState = playerState;
        createInventory();
    }

    private void createInventory() {
        List<ConfigService.StarterOptionConfig> weapons = config.getStarterWeapons();
        int size = calculateInventorySize(weapons.size());

        String title = i18n.get("gui.starter_weapon_title");
        this.inventory = Bukkit.createInventory(this, size, Component.text(title));

        // Populate weapons
        int slot = 0;
        for (ConfigService.StarterOptionConfig weapon : weapons) {
            ItemStack displayItem = createDisplayItem(weapon);
            inventory.setItem(slot, displayItem);
            slotToOptionId.put(slot, weapon.optionId);
            slot++;
        }
    }

    private int calculateInventorySize(int itemCount) {
        // Round up to nearest row of 9
        int rows = (int) Math.ceil(itemCount / 9.0);
        return Math.max(9, Math.min(54, rows * 9));
    }

    private ItemStack createDisplayItem(ConfigService.StarterOptionConfig option) {
        Material material = option.displayMaterial != null ? option.displayMaterial : Material.IRON_SWORD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Display name
            String name = option.displayItemName != null ? option.displayItemName : option.displayName;
            meta.displayName(Component.text(i18n.get("gui.item_prefix") + name));

            // Lore
            List<Component> lore = new ArrayList<>();
            if (option.displayItemLore != null) {
                for (String line : option.displayItemLore) {
                    lore.add(Component.text(line.replace('&', '§')));
                }
            }

            // Add selection hint
            String currentSelection = playerState.getStarterWeaponOptionId();
            if (option.optionId.equals(currentSelection)) {
                lore.add(Component.empty());
                lore.add(Component.text(i18n.get("gui.currently_selected")));
            } else {
                lore.add(Component.empty());
                lore.add(Component.text(i18n.get("gui.click_to_select")));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        String optionId = slotToOptionId.get(slot);

        if (optionId == null) {
            return;
        }

        // Find the weapon config
        ConfigService.StarterOptionConfig selected = config.getStarterWeapons().stream()
                .filter(w -> w.optionId.equals(optionId))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            return;
        }

        // Update selection
        playerState.setStarterWeaponOptionId(optionId);
        i18n.send(player, "starter.weapon_selected", "weapon", selected.displayName);

        // Close GUI
        player.closeInventory();

        // Auto-open helmet GUI if configured
        if (config.isAutoOpenHelmetGui() && playerState.getStarterHelmetOptionId() == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getStarterService().openHelmetGui(player);
            }, 1L);
        }
    }

    @Override
    public String getGuiType() {
        return GUI_TYPE;
    }
}
