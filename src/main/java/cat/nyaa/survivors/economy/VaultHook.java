package cat.nyaa.survivors.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Optional Vault integration wrapper.
 * Only loaded if Vault is present on the server.
 */
public class VaultHook {

    private final Logger logger;
    private net.milkbowl.vault.economy.Economy economy;
    private boolean enabled = false;

    public VaultHook(Logger logger) {
        this.logger = logger;
    }

    /**
     * Attempts to hook into Vault economy.
     *
     * @return true if successfully hooked, false otherwise
     */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.info("Vault not found, Vault economy mode unavailable");
            return false;
        }

        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);

        if (rsp == null) {
            logger.warning("Vault found but no economy provider registered");
            return false;
        }

        economy = rsp.getProvider();
        enabled = true;
        logger.info("Hooked into Vault economy: " + economy.getName());
        return true;
    }

    /**
     * Checks if Vault hook is enabled.
     */
    public boolean isEnabled() {
        return enabled && economy != null;
    }

    /**
     * Gets a player's balance from Vault.
     */
    public double getBalance(Player player) {
        if (!isEnabled()) return 0;
        return economy.getBalance(player);
    }

    /**
     * Checks if a player has at least the specified amount.
     */
    public boolean has(Player player, double amount) {
        if (!isEnabled()) return false;
        return economy.has(player, amount);
    }

    /**
     * Withdraws an amount from a player's account.
     *
     * @return true if successful
     */
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled()) return false;
        net.milkbowl.vault.economy.EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Deposits an amount to a player's account.
     *
     * @return true if successful
     */
    public boolean deposit(Player player, double amount) {
        if (!isEnabled()) return false;
        net.milkbowl.vault.economy.EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Gets the currency name (plural).
     */
    public String getCurrencyNamePlural() {
        if (!isEnabled()) return "coins";
        return economy.currencyNamePlural();
    }

    /**
     * Gets the currency name (singular).
     */
    public String getCurrencyNameSingular() {
        if (!isEnabled()) return "coin";
        return economy.currencyNameSingular();
    }
}
