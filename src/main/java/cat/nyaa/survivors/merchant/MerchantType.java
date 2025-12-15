package cat.nyaa.survivors.merchant;

/**
 * Defines the type of merchant.
 */
public enum MerchantType {
    /**
     * Multi-item shop merchant.
     * Opens a GUI with multiple items for sale.
     * Items can be limited (disappear on purchase) or unlimited.
     * Can show random selection or full inventory.
     */
    MULTI,

    /**
     * Single-item merchant.
     * Has only one item selected from a weighted pool.
     * Purchase directly on interaction.
     * Can despawn on purchase (limited) or stay (unlimited).
     */
    SINGLE
}
