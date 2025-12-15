package cat.nyaa.survivors.merchant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A pool of weighted items for merchant stock selection.
 */
public class MerchantItemPool {

    private final String poolId;
    private final List<WeightedShopItem> items;

    public MerchantItemPool(String poolId) {
        this.poolId = poolId;
        this.items = new ArrayList<>();
    }

    public MerchantItemPool(String poolId, List<WeightedShopItem> items) {
        this.poolId = poolId;
        this.items = new ArrayList<>(items);
    }

    /**
     * Gets the pool ID.
     */
    public String getPoolId() {
        return poolId;
    }

    /**
     * Gets all items in the pool (unmodifiable).
     */
    public List<WeightedShopItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Gets the number of items in the pool.
     */
    public int size() {
        return items.size();
    }

    /**
     * Checks if the pool is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Adds an item to the pool.
     */
    public void addItem(WeightedShopItem item) {
        items.add(item);
    }

    /**
     * Removes an item by index.
     *
     * @param index the index to remove
     * @return the removed item, or null if index invalid
     */
    public WeightedShopItem removeItem(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.remove(index);
    }

    /**
     * Selects a single item using weighted random selection.
     *
     * @return the selected item, or null if pool is empty
     */
    public WeightedShopItem selectSingle() {
        if (items.isEmpty()) {
            return null;
        }

        double totalWeight = items.stream().mapToDouble(WeightedShopItem::getWeight).sum();
        if (totalWeight <= 0) {
            // Fall back to uniform random if all weights are zero
            return items.get(ThreadLocalRandom.current().nextInt(items.size()));
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;

        for (WeightedShopItem item : items) {
            cumulative += item.getWeight();
            if (roll < cumulative) {
                return item;
            }
        }

        // Should not reach here, but return last item as fallback
        return items.get(items.size() - 1);
    }

    /**
     * Selects multiple unique items using weighted random selection.
     *
     * @param minCount minimum number of items to select
     * @param maxCount maximum number of items to select
     * @return list of selected items (may be fewer than minCount if pool is smaller)
     */
    public List<WeightedShopItem> selectRandom(int minCount, int maxCount) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // Determine how many items to select
        int actualMax = Math.min(maxCount, items.size());
        int actualMin = Math.min(minCount, actualMax);
        int count = actualMin + ThreadLocalRandom.current().nextInt(actualMax - actualMin + 1);

        if (count >= items.size()) {
            // Return all items if we need more than available
            return new ArrayList<>(items);
        }

        // Weighted selection without replacement
        List<WeightedShopItem> available = new ArrayList<>(items);
        List<WeightedShopItem> selected = new ArrayList<>();

        while (selected.size() < count && !available.isEmpty()) {
            double totalWeight = available.stream().mapToDouble(WeightedShopItem::getWeight).sum();

            if (totalWeight <= 0) {
                // Uniform random fallback
                int idx = ThreadLocalRandom.current().nextInt(available.size());
                selected.add(available.remove(idx));
                continue;
            }

            double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
            double cumulative = 0;

            for (int i = 0; i < available.size(); i++) {
                cumulative += available.get(i).getWeight();
                if (roll < cumulative) {
                    selected.add(available.remove(i));
                    break;
                }
            }
        }

        return selected;
    }

    /**
     * Gets all items (for full shop display).
     *
     * @return list of all items in the pool
     */
    public List<WeightedShopItem> getAllItems() {
        return new ArrayList<>(items);
    }

    @Override
    public String toString() {
        return "MerchantItemPool{" +
                "poolId='" + poolId + '\'' +
                ", itemCount=" + items.size() +
                '}';
    }
}
