package cat.nyaa.survivors.gui;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ItemTemplateConfig;
import cat.nyaa.survivors.economy.EconomyService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.merchant.MerchantInstance;
import cat.nyaa.survivors.merchant.WeightedShopItem;
import cat.nyaa.survivors.service.AdminConfigService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Shop GUI for multi-type merchants.
 * Displays items with prices and allows purchasing.
 */
public class MerchantShopGui extends GuiHolder {

    public static final String GUI_TYPE = "merchant_shop";
    private static final int ROWS = 6;
    private static final int WALLET_SLOT = 53;  // Last slot in row 6

    private final KedamaSurvivorsPlugin plugin;
    private final MerchantInstance merchant;
    private final I18nService i18n;
    private final EconomyService economy;
    private final AdminConfigService adminConfig;

    // Map slot index to shop item
    private final Map<Integer, WeightedShopItem> slotToItem = new HashMap<>();

    public MerchantShopGui(Player player, KedamaSurvivorsPlugin plugin, MerchantInstance merchant) {
        super(player);
        this.plugin = plugin;
        this.merchant = merchant;
        this.i18n = plugin.getI18nService();
        this.economy = plugin.getEconomyService();
        this.adminConfig = plugin.getAdminConfigService();

        createInventory();
    }

    private void createInventory() {
        String title = i18n.get("merchant.shop_title");
        inventory = Bukkit.createInventory(this, ROWS * 9, Component.text(title));

        populateItems();
        updateWalletIndicator();
    }

    private void populateItems() {
        slotToItem.clear();

        List<WeightedShopItem> stock = merchant.getCurrentStock();
        int slot = 0;

        for (WeightedShopItem shopItem : stock) {
            if (slot >= 45) break;  // Leave last row for wallet indicator

            ItemStack displayItem = createDisplayItem(shopItem);
            if (displayItem != null) {
                inventory.setItem(slot, displayItem);
                slotToItem.put(slot, shopItem);
            }
            slot++;
        }
    }

    private ItemStack createDisplayItem(WeightedShopItem shopItem) {
        Optional<ItemTemplateConfig> templateOpt = adminConfig.getItemTemplate(shopItem.getItemTemplateId());
        if (templateOpt.isEmpty()) {
            return null;
        }

        ItemStack item = templateOpt.get().toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Add price lore
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        // Add blank line and price line
        lore.add(Component.empty());
        String priceLine = i18n.get("merchant.price_line", "price", shopItem.getPrice());
        lore.add(Component.text(priceLine));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void updateWalletIndicator() {
        int balance = economy.getBalance(player);

        ItemStack walletItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = walletItem.getItemMeta();
        if (meta != null) {
            String walletName = i18n.get("merchant.wallet_name");
            meta.displayName(Component.text(walletName).color(NamedTextColor.GOLD));

            String loreLine = i18n.get("merchant.wallet_lore", "balance", balance);
            meta.lore(Collections.singletonList(Component.text(loreLine)));

            walletItem.setItemMeta(meta);
        }

        inventory.setItem(WALLET_SLOT, walletItem);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        // Check if clicked on a shop item
        WeightedShopItem shopItem = slotToItem.get(slot);
        if (shopItem == null) {
            return;
        }

        // Check balance
        int price = shopItem.getPrice();
        if (!economy.hasBalance(player, price)) {
            i18n.send(player, "merchant.not_enough_coins", "price", price);
            return;
        }

        // Resolve the item
        Optional<ItemTemplateConfig> templateOpt = adminConfig.getItemTemplate(shopItem.getItemTemplateId());
        if (templateOpt.isEmpty()) {
            i18n.send(player, "error.generic");
            return;
        }

        ItemStack purchaseItem = templateOpt.get().toItemStack();

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            i18n.send(player, "info.reward_overflow", "count", 1);
            return;
        }

        // Deduct coins and give item
        if (economy.deduct(player, price, "merchant_purchase")) {
            player.getInventory().addItem(purchaseItem);
            i18n.send(player, "merchant.purchase_success", "price", price);

            // Update wallet display
            updateWalletIndicator();

            // Handle limited stock
            if (merchant.isLimited()) {
                merchant.removeFromStock(shopItem);

                if (merchant.isEmpty()) {
                    // Close GUI and despawn merchant
                    player.closeInventory();
                    merchant.despawn();
                    i18n.send(player, "merchant.merchant_left");
                } else {
                    // Refresh the GUI
                    refreshInventory();
                }
            }
        } else {
            i18n.send(player, "merchant.not_enough_coins", "price", price);
        }
    }

    /**
     * Refreshes the inventory display after a purchase.
     */
    public void refreshInventory() {
        // Clear item slots
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, null);
        }

        // Repopulate
        populateItems();
        updateWalletIndicator();
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Nothing special needed on close
    }

    @Override
    public String getGuiType() {
        return GUI_TYPE;
    }

    /**
     * Gets the associated merchant instance.
     */
    public MerchantInstance getMerchant() {
        return merchant;
    }
}
