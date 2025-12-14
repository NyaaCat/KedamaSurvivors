package cat.nyaa.survivors.gui;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.UpgradeService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for selecting equipment upgrades after level up.
 */
public class UpgradeGui extends GuiHolder {

    private static final int WEAPON_SLOT = 2;
    private static final int HELMET_SLOT = 6;
    private static final int GUI_SIZE = 9;

    private final KedamaSurvivorsPlugin plugin;
    private final I18nService i18n;
    private final PlayerState playerState;

    public UpgradeGui(KedamaSurvivorsPlugin plugin, Player player, PlayerState playerState) {
        super(player);
        this.plugin = plugin;
        this.i18n = plugin.getI18nService();
        this.playerState = playerState;

        createInventory();
    }

    private void createInventory() {
        String title = i18n.get("gui.upgrade_title");
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text(title));

        // Add weapon upgrade option
        ItemStack weaponItem = createWeaponUpgradeItem();
        inventory.setItem(WEAPON_SLOT, weaponItem);

        // Add helmet upgrade option
        ItemStack helmetItem = createHelmetUpgradeItem();
        inventory.setItem(HELMET_SLOT, helmetItem);

        // Fill empty slots with glass panes
        ItemStack filler = createFillerItem();
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i != WEAPON_SLOT && i != HELMET_SLOT) {
                inventory.setItem(i, filler);
            }
        }
    }

    private ItemStack createWeaponUpgradeItem() {
        UpgradeService upgradeService = plugin.getUpgradeService();

        boolean canUpgrade = upgradeService.canUpgradeWeapon(playerState);
        String nextInfo = upgradeService.getNextWeaponInfo(playerState);

        Material material = canUpgrade ? Material.DIAMOND_SWORD : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = i18n.get("gui.upgrade_weapon_item.name");
            meta.displayName(Component.text(name));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(i18n.get("gui.upgrade_current",
                    "group", playerState.getWeaponGroup(),
                    "level", playerState.getWeaponLevel())));
            lore.add(Component.empty());

            if (canUpgrade) {
                lore.add(Component.text(i18n.get("gui.upgrade_next", "info", nextInfo)));
                lore.add(Component.empty());
                lore.add(Component.text(i18n.get("gui.click_to_upgrade")));
            } else {
                lore.add(Component.text(i18n.get("gui.already_max")));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createHelmetUpgradeItem() {
        UpgradeService upgradeService = plugin.getUpgradeService();

        boolean canUpgrade = upgradeService.canUpgradeHelmet(playerState);
        String nextInfo = upgradeService.getNextHelmetInfo(playerState);

        Material material = canUpgrade ? Material.DIAMOND_HELMET : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = i18n.get("gui.upgrade_helmet_item.name");
            meta.displayName(Component.text(name));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(i18n.get("gui.upgrade_current",
                    "group", playerState.getHelmetGroup(),
                    "level", playerState.getHelmetLevel())));
            lore.add(Component.empty());

            if (canUpgrade) {
                lore.add(Component.text(i18n.get("gui.upgrade_next", "info", nextInfo)));
                lore.add(Component.empty());
                lore.add(Component.text(i18n.get("gui.click_to_upgrade")));
            } else {
                lore.add(Component.text(i18n.get("gui.already_max")));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createFillerItem() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        return filler;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot == WEAPON_SLOT) {
            UpgradeService upgradeService = plugin.getUpgradeService();
            upgradeService.processWeaponUpgrade(player, playerState);
        } else if (slot == HELMET_SLOT) {
            UpgradeService upgradeService = plugin.getUpgradeService();
            upgradeService.processHelmetUpgrade(player, playerState);
        }
    }

    @Override
    public String getGuiType() {
        return "upgrade";
    }
}
