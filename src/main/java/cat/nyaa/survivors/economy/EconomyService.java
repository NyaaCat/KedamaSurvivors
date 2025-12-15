package cat.nyaa.survivors.economy;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Handles all economy operations for the plugin.
 * Supports three modes: VAULT, INTERNAL, and ITEM.
 */
public class EconomyService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final I18nService i18n;

    private EconomyMode mode;
    private VaultHook vaultHook;
    private NamespacedKey coinNbtKey;

    public EconomyService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.i18n = plugin.getI18nService();
    }

    /**
     * Initializes the economy service.
     * Should be called after ConfigService is loaded.
     */
    public void initialize() {
        this.mode = config.getEconomyMode();
        this.coinNbtKey = new NamespacedKey(plugin, config.getCoinNbtTag());

        if (mode == EconomyMode.VAULT) {
            vaultHook = new VaultHook(plugin.getLogger());
            if (!vaultHook.setup()) {
                plugin.getLogger().warning("Vault economy mode configured but Vault not available. Falling back to INTERNAL mode.");
                mode = EconomyMode.INTERNAL;
            }
        }

        plugin.getLogger().info("Economy service initialized with mode: " + mode);
    }

    /**
     * Gets the current economy mode.
     */
    public EconomyMode getMode() {
        return mode;
    }

    /**
     * Gets a player's current balance.
     *
     * @param player the player
     * @return the balance
     */
    public int getBalance(Player player) {
        return switch (mode) {
            case VAULT -> (int) vaultHook.getBalance(player);
            case INTERNAL -> getInternalBalance(player);
            case ITEM -> countCoinItems(player);
        };
    }

    /**
     * Checks if a player has at least the specified amount.
     *
     * @param player the player
     * @param amount the amount to check
     * @return true if player has enough
     */
    public boolean hasBalance(Player player, int amount) {
        return getBalance(player) >= amount;
    }

    /**
     * Deducts an amount from a player's balance.
     *
     * @param player the player
     * @param amount the amount to deduct
     * @param reason the reason for the transaction (for logging)
     * @return true if successful
     */
    public boolean deduct(Player player, int amount, String reason) {
        if (amount <= 0) return true;
        if (!hasBalance(player, amount)) return false;

        boolean success = switch (mode) {
            case VAULT -> vaultHook.withdraw(player, amount);
            case INTERNAL -> deductInternalBalance(player, amount);
            case ITEM -> removeCoinItems(player, amount);
        };

        if (success && config.isVerbose()) {
            plugin.getLogger().info("Economy deduct: player=" + player.getName() +
                    " amount=" + amount + " reason=" + reason + " mode=" + mode);
        }

        return success;
    }

    /**
     * Adds an amount to a player's balance.
     *
     * @param player the player
     * @param amount the amount to add
     * @param reason the reason for the transaction (for logging)
     */
    public void add(Player player, int amount, String reason) {
        if (amount <= 0) return;

        switch (mode) {
            case VAULT -> vaultHook.deposit(player, amount);
            case INTERNAL -> addInternalBalance(player, amount);
            case ITEM -> grantCoinItems(player, amount);
        }

        if (config.isVerbose()) {
            plugin.getLogger().info("Economy add: player=" + player.getName() +
                    " amount=" + amount + " reason=" + reason + " mode=" + mode);
        }
    }

    /**
     * Creates a coin item stack for display purposes.
     *
     * @param amount the amount
     * @return the coin item stack
     */
    public ItemStack createCoinItem(int amount) {
        ItemStack coin = new ItemStack(config.getCoinMaterial(), amount);
        ItemMeta meta = coin.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(config.getCoinDisplayName()));
            if (config.getCoinCustomModelData() > 0) {
                meta.setCustomModelData(config.getCoinCustomModelData());
            }
            // Add NBT tag for identification
            meta.getPersistentDataContainer().set(coinNbtKey, PersistentDataType.BYTE, (byte) 1);
            coin.setItemMeta(meta);
        }
        return coin;
    }

    /**
     * Checks if an item is a valid coin item.
     *
     * @param item the item to check
     * @return true if it's a coin
     */
    public boolean isCoinItem(ItemStack item) {
        if (item == null || item.getType() != config.getCoinMaterial()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(coinNbtKey, PersistentDataType.BYTE);
    }

    // ==================== Internal Mode Methods ====================

    private int getInternalBalance(Player player) {
        Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
        return stateOpt.map(PlayerState::getBalance).orElse(0);
    }

    private boolean deductInternalBalance(Player player, int amount) {
        Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
        if (stateOpt.isEmpty()) return false;

        PlayerState playerState = stateOpt.get();
        int current = playerState.getBalance();
        if (current < amount) return false;

        playerState.setBalance(current - amount);
        return true;
    }

    private void addInternalBalance(Player player, int amount) {
        Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
        if (stateOpt.isEmpty()) return;

        PlayerState playerState = stateOpt.get();
        playerState.setBalance(playerState.getBalance() + amount);
    }

    // ==================== Item Mode Methods ====================

    private int countCoinItems(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCoinItem(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean removeCoinItems(Player player, int amount) {
        int remaining = amount;

        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (!isCoinItem(item)) continue;

            int itemAmount = item.getAmount();
            if (itemAmount <= remaining) {
                player.getInventory().setItem(i, null);
                remaining -= itemAmount;
            } else {
                item.setAmount(itemAmount - remaining);
                remaining = 0;
            }
        }

        return remaining == 0;
    }

    private void grantCoinItems(Player player, int amount) {
        ItemStack coin = createCoinItem(amount);
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(coin);

        if (!overflow.isEmpty()) {
            // Add to pending rewards if inventory is full
            Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
            stateOpt.ifPresent(ps -> {
                for (ItemStack item : overflow.values()) {
                    ps.addPendingReward(item);
                }
                i18n.send(player, "info.reward_overflow", "count", overflow.size());
            });
        }
    }

    /**
     * Gets the Vault hook (may be null if not using Vault mode).
     */
    public VaultHook getVaultHook() {
        return vaultHook;
    }
}
