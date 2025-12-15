package cat.nyaa.survivors.merchant;

/**
 * Represents an item in a merchant's pool with weight and price.
 */
public class WeightedShopItem {

    private final String itemTemplateId;
    private final double weight;
    private final int price;

    public WeightedShopItem(String itemTemplateId, double weight, int price) {
        this.itemTemplateId = itemTemplateId;
        this.weight = weight;
        this.price = price;
    }

    /**
     * Gets the item template ID (references AdminConfigService item templates).
     */
    public String getItemTemplateId() {
        return itemTemplateId;
    }

    /**
     * Gets the weight for random selection.
     * Higher weight = higher chance of being selected.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Gets the price in coins.
     */
    public int getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return "WeightedShopItem{" +
                "itemTemplateId='" + itemTemplateId + '\'' +
                ", weight=" + weight +
                ", price=" + price +
                '}';
    }
}
