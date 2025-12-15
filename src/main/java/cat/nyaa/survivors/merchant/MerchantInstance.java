package cat.nyaa.survivors.merchant;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime state for a spawned merchant.
 * Contains the merchant entity, current stock, and configuration.
 */
public class MerchantInstance {

    private final UUID instanceId;
    private final MerchantEntity entity;
    private final MerchantType type;
    private final MerchantBehavior behavior;
    private final String poolId;
    private final boolean limited;
    private final boolean showAllItems;
    private final String displayName;

    // Current stock (for multi-type merchants)
    private final List<WeightedShopItem> currentStock;

    // For single-type merchants
    private WeightedShopItem singleItem;

    // Timing
    private final long spawnTimeMillis;
    private long despawnTimeMillis;

    // Head item cycling (for multi-type)
    private int currentHeadItemIndex = 0;

    // Run association
    private UUID runId;

    public MerchantInstance(UUID instanceId, MerchantEntity entity, MerchantType type,
                           MerchantBehavior behavior, String poolId, boolean limited,
                           boolean showAllItems, String displayName) {
        this.instanceId = instanceId;
        this.entity = entity;
        this.type = type;
        this.behavior = behavior;
        this.poolId = poolId;
        this.limited = limited;
        this.showAllItems = showAllItems;
        this.displayName = displayName;
        this.currentStock = new ArrayList<>();
        this.spawnTimeMillis = System.currentTimeMillis();
    }

    /**
     * Gets the unique instance ID.
     */
    public UUID getInstanceId() {
        return instanceId;
    }

    /**
     * Gets the merchant entity.
     */
    public MerchantEntity getEntity() {
        return entity;
    }

    /**
     * Gets the merchant type.
     */
    public MerchantType getType() {
        return type;
    }

    /**
     * Gets the merchant behavior.
     */
    public MerchantBehavior getBehavior() {
        return behavior;
    }

    /**
     * Gets the item pool ID.
     */
    public String getPoolId() {
        return poolId;
    }

    /**
     * Checks if the merchant has limited stock.
     */
    public boolean isLimited() {
        return limited;
    }

    /**
     * Checks if this merchant shows all items from the pool (vs random selection).
     */
    public boolean isShowAllItems() {
        return showAllItems;
    }

    /**
     * Gets the display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the current stock of items.
     */
    public List<WeightedShopItem> getCurrentStock() {
        return new ArrayList<>(currentStock);
    }

    /**
     * Sets the current stock (for multi-type merchants).
     */
    public void setCurrentStock(List<WeightedShopItem> stock) {
        currentStock.clear();
        currentStock.addAll(stock);
    }

    /**
     * Gets the single item (for single-type merchants).
     */
    public WeightedShopItem getSingleItem() {
        return singleItem;
    }

    /**
     * Sets the single item (for single-type merchants).
     */
    public void setSingleItem(WeightedShopItem item) {
        this.singleItem = item;
    }

    /**
     * Removes an item from the stock (for limited merchants).
     *
     * @param item the item to remove
     * @return true if removed, false if not found
     */
    public boolean removeFromStock(WeightedShopItem item) {
        return currentStock.remove(item);
    }

    /**
     * Removes an item by template ID (for limited merchants).
     *
     * @param templateId the template ID to remove
     * @return the removed item, or null if not found
     */
    public WeightedShopItem removeFromStockByTemplateId(String templateId) {
        for (int i = 0; i < currentStock.size(); i++) {
            if (currentStock.get(i).getItemTemplateId().equals(templateId)) {
                return currentStock.remove(i);
            }
        }
        return null;
    }

    /**
     * Checks if the merchant is empty (no stock left).
     */
    public boolean isEmpty() {
        if (type == MerchantType.SINGLE) {
            return singleItem == null;
        }
        return currentStock.isEmpty();
    }

    /**
     * Cycles to the next head item (for multi-type merchants).
     * Updates the entity's head item display.
     *
     * @param itemResolver function to resolve template ID to ItemStack
     */
    public void cycleHeadItem(java.util.function.Function<String, ItemStack> itemResolver) {
        if (currentStock.isEmpty()) {
            entity.setHeadItem(null);
            return;
        }

        currentHeadItemIndex = (currentHeadItemIndex + 1) % currentStock.size();
        WeightedShopItem item = currentStock.get(currentHeadItemIndex);
        ItemStack headItem = itemResolver.apply(item.getItemTemplateId());
        entity.setHeadItem(headItem);
    }

    /**
     * Updates the head item to show the current selection.
     *
     * @param itemResolver function to resolve template ID to ItemStack
     */
    public void updateHeadItem(java.util.function.Function<String, ItemStack> itemResolver) {
        if (type == MerchantType.SINGLE && singleItem != null) {
            ItemStack headItem = itemResolver.apply(singleItem.getItemTemplateId());
            entity.setHeadItem(headItem);
        } else if (!currentStock.isEmpty()) {
            currentHeadItemIndex = Math.min(currentHeadItemIndex, currentStock.size() - 1);
            WeightedShopItem item = currentStock.get(currentHeadItemIndex);
            ItemStack headItem = itemResolver.apply(item.getItemTemplateId());
            entity.setHeadItem(headItem);
        } else {
            entity.setHeadItem(null);
        }
    }

    /**
     * Gets the spawn time in milliseconds.
     */
    public long getSpawnTimeMillis() {
        return spawnTimeMillis;
    }

    /**
     * Gets the despawn time in milliseconds.
     */
    public long getDespawnTimeMillis() {
        return despawnTimeMillis;
    }

    /**
     * Sets the despawn time in milliseconds.
     */
    public void setDespawnTimeMillis(long despawnTimeMillis) {
        this.despawnTimeMillis = despawnTimeMillis;
    }

    /**
     * Checks if this merchant should despawn based on time.
     */
    public boolean shouldDespawn() {
        return despawnTimeMillis > 0 && System.currentTimeMillis() >= despawnTimeMillis;
    }

    /**
     * Gets the associated run ID.
     */
    public UUID getRunId() {
        return runId;
    }

    /**
     * Sets the associated run ID.
     */
    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    /**
     * Gets the merchant's current location.
     */
    public Location getLocation() {
        return entity.getCurrentLocation();
    }

    /**
     * Removes the merchant entity from the world.
     */
    public void despawn() {
        entity.remove();
    }

    /**
     * Checks if the merchant is still valid (entity not removed).
     */
    public boolean isValid() {
        return entity.isValid();
    }
}
