package cat.nyaa.survivors.economy;

/**
 * Defines the economy mode for coin handling.
 */
public enum EconomyMode {
    /**
     * Use Vault economy API for universal economy integration.
     * Falls back to INTERNAL if Vault is not installed.
     */
    VAULT,

    /**
     * Use plugin-internal per-player persistent balance storage.
     * This is the default mode.
     */
    INTERNAL,

    /**
     * Use item-based coins (physical items in inventory).
     * Coins are identified by material and optional NBT tag.
     */
    ITEM
}
