package cat.nyaa.survivors.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Base class for custom GUI inventory holders.
 * Allows identification of plugin GUIs and handling of click events.
 */
public abstract class GuiHolder implements InventoryHolder {

    protected final Player player;
    protected Inventory inventory;

    protected GuiHolder(Player player) {
        this.player = player;
    }

    /**
     * Gets the player viewing this GUI.
     */
    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Handles a click event in this GUI.
     * @param event the click event
     */
    public abstract void onClick(InventoryClickEvent event);

    /**
     * Called when the GUI is closed.
     * @param event the close event
     */
    public void onClose(InventoryCloseEvent event) {
        // Default: do nothing
    }

    /**
     * Opens this GUI for the player.
     */
    public void open() {
        if (inventory != null) {
            player.openInventory(inventory);
        }
    }

    /**
     * Gets the unique type identifier for this GUI.
     */
    public abstract String getGuiType();
}
