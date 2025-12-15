package cat.nyaa.survivors.merchant;

/**
 * Defines the spawn behavior of a merchant.
 */
public enum MerchantBehavior {
    /**
     * Fixed merchant that stays at a configured location permanently.
     * Location can be set via admin command using current player position.
     */
    FIXED,

    /**
     * Wandering merchant that spawns periodically near players.
     * Uses global wandering merchant configuration.
     * Only spawns in active combat maps.
     * Has configurable spawn interval, chance, and stay duration.
     */
    WANDERING
}
