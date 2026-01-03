package cat.nyaa.survivors.gui;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.TeamState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for selecting a combat world before starting a run.
 * Only the team leader can make selections.
 */
public class WorldSelectionGui extends GuiHolder {

    public static final String GUI_TYPE = "world_selection";

    // Special slot for random world option
    private static final String RANDOM_OPTION_ID = "__random__";

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final ConfigService config;
    private final TeamState teamState;
    private final Map<Integer, String> slotToWorldName = new HashMap<>();

    public WorldSelectionGui(KedamaSurvivorsPlugin plugin, Player player, TeamState teamState) {
        super(player);
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.config = plugin.getConfigService();
        this.teamState = teamState;
        createInventory();
    }

    private void createInventory() {
        List<ConfigService.CombatWorldConfig> enabledWorlds = config.getEnabledCombatWorlds();
        // +1 for the random option
        int totalItems = enabledWorlds.size() + 1;
        int size = calculateInventorySize(totalItems);

        String title = i18n.get("gui.world_selection_title");
        this.inventory = Bukkit.createInventory(this, size, Component.text(title));

        // Add random option first
        ItemStack randomItem = createRandomItem();
        inventory.setItem(0, randomItem);
        slotToWorldName.put(0, RANDOM_OPTION_ID);

        // Add world options
        int slot = 1;
        for (ConfigService.CombatWorldConfig world : enabledWorlds) {
            ItemStack worldItem = createWorldItem(world);
            inventory.setItem(slot, worldItem);
            slotToWorldName.put(slot, world.name);
            slot++;
        }
    }

    private int calculateInventorySize(int itemCount) {
        int rows = (int) Math.ceil(itemCount / 9.0);
        return Math.max(9, Math.min(54, rows * 9));
    }

    private ItemStack createRandomItem() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Display name
            meta.displayName(Component.text(i18n.get("gui.world_random"))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            // Lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(i18n.get("gui.world_random_lore"))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            // Selection status
            String currentSelection = teamState.getSelectedWorldName();
            if (currentSelection == null) {
                lore.add(Component.text(i18n.get("gui.currently_selected"))
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(i18n.get("gui.click_to_select"))
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWorldItem(ConfigService.CombatWorldConfig world) {
        // Use grass block as default world icon
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Display name
            String displayName = world.displayName != null ? world.displayName : world.name;
            meta.displayName(Component.text(displayName)
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            // Lore
            List<Component> lore = new ArrayList<>();

            // Cost information
            if (world.cost > 0) {
                lore.add(Component.text(i18n.get("gui.world_cost", "cost", String.valueOf(world.cost)))
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(i18n.get("gui.world_free"))
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.empty());

            // Selection status
            String currentSelection = teamState.getSelectedWorldName();
            if (world.name.equals(currentSelection)) {
                lore.add(Component.text(i18n.get("gui.currently_selected"))
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(i18n.get("gui.click_to_select"))
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);

        // Verify the player is the team leader
        if (!teamState.isLeader(player.getUniqueId())) {
            i18n.send(player, "error.leader_only_world");
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        String worldName = slotToWorldName.get(slot);

        if (worldName == null) {
            return;
        }

        // Handle selection
        if (RANDOM_OPTION_ID.equals(worldName)) {
            // Random selection
            teamState.setSelectedWorldName(null);
            i18n.send(player, "starter.world_selected", "world", i18n.get("gui.world_random"));
        } else {
            // Specific world selection
            Optional<ConfigService.CombatWorldConfig> worldOpt = config.getCombatWorld(worldName);
            if (worldOpt.isEmpty()) {
                return;
            }

            ConfigService.CombatWorldConfig world = worldOpt.get();
            teamState.setSelectedWorldName(worldName);

            String displayName = world.displayName != null ? world.displayName : world.name;
            i18n.send(player, "starter.world_selected", "world", displayName);
        }

        player.closeInventory();
    }

    @Override
    public String getGuiType() {
        return GUI_TYPE;
    }
}
