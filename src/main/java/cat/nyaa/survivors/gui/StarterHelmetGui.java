package cat.nyaa.survivors.gui;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ItemTemplateConfig;
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
import java.util.Optional;

/**
 * GUI for selecting starter helmet.
 */
public class StarterHelmetGui extends GuiHolder {

    public static final String GUI_TYPE = "starter_helmet";

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;
    private final PlayerState playerState;
    private final Map<Integer, String> slotToOptionId = new HashMap<>();

    public StarterHelmetGui(KedamaSurvivorsPlugin plugin, Player player, PlayerState playerState) {
        super(player);
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.config = plugin.getConfigService();
        this.playerState = playerState;
        createInventory();
    }

    private void createInventory() {
        List<ConfigService.StarterOptionConfig> helmets = config.getStarterHelmets();
        int size = calculateInventorySize(helmets.size());

        String title = i18n.get("gui.starter_helmet_title");
        this.inventory = Bukkit.createInventory(this, size, Component.text(title));

        // Populate helmets
        int slot = 0;
        for (ConfigService.StarterOptionConfig helmet : helmets) {
            ItemStack displayItem = createDisplayItem(helmet);
            inventory.setItem(slot, displayItem);
            slotToOptionId.put(slot, helmet.optionId);
            slot++;
        }
    }

    private int calculateInventorySize(int itemCount) {
        int rows = (int) Math.ceil(itemCount / 9.0);
        return Math.max(9, Math.min(54, rows * 9));
    }

    private ItemStack createDisplayItem(ConfigService.StarterOptionConfig option) {
        ItemStack item = null;

        // Try to get actual NBT item from template
        if (option.templateId != null && !option.templateId.isEmpty()) {
            Optional<ItemTemplateConfig> templateOpt =
                plugin.getAdminConfigService().getItemTemplate(option.templateId);
            if (templateOpt.isPresent()) {
                item = templateOpt.get().toItemStack();
            }
        }

        // Fallback to displayMaterial if template not found
        if (item == null) {
            Material material = option.displayMaterial != null ? option.displayMaterial : Material.IRON_HELMET;
            item = new ItemStack(material);
            // Set basic display name for fallback items
            ItemMeta fallbackMeta = item.getItemMeta();
            if (fallbackMeta != null) {
                String name = option.displayItemName != null ? option.displayItemName : option.displayName;
                fallbackMeta.displayName(Component.text(i18n.get("gui.item_prefix") + name));

                // Add display lore if configured
                if (option.displayItemLore != null) {
                    List<Component> fallbackLore = new ArrayList<>();
                    for (String line : option.displayItemLore) {
                        fallbackLore.add(Component.text(line.replace('&', '§')));
                    }
                    fallbackMeta.lore(fallbackLore);
                }
                item.setItemMeta(fallbackMeta);
            }
        }

        // Add selection hint lore (append to existing lore)
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();

            lore.add(Component.empty());
            String currentSelection = playerState.getStarterHelmetOptionId();
            if (option.optionId.equals(currentSelection)) {
                lore.add(Component.text(i18n.get("gui.currently_selected")));
            } else {
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

        ConfigService.StarterOptionConfig selected = config.getStarterHelmets().stream()
                .filter(h -> h.optionId.equals(optionId))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            return;
        }

        playerState.setStarterHelmetOptionId(optionId);

        // Grant the helmet immediately
        plugin.getStarterService().grantSingleStarterItem(player, selected, "helmet");

        i18n.send(player, "starter.helmet_selected", "helmet", selected.displayName);

        player.closeInventory();

        // Notify player if both selections are complete
        if (playerState.hasSelectedStarters()) {
            i18n.send(player, "starter.selection_complete");
        }
    }

    @Override
    public String getGuiType() {
        return GUI_TYPE;
    }
}
