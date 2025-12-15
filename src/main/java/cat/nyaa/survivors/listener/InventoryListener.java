package cat.nyaa.survivors.listener;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.gui.GuiHolder;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StarterService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Set;

/**
 * Handles inventory-related events including GUI clicks and equipment protection.
 */
public class InventoryListener implements Listener {

    private final KedamaSurvivorsPlugin plugin;
    private final StateService state;
    private final StarterService starter;

    // Restricted modes where equipment cannot be modified and drops/pickups are prevented
    // All modes except LOBBY are restricted
    private static final Set<PlayerMode> RESTRICTED_MODES = Set.of(
            PlayerMode.READY,
            PlayerMode.COUNTDOWN,
            PlayerMode.IN_RUN,
            PlayerMode.COOLDOWN,
            PlayerMode.GRACE_EJECT,
            PlayerMode.DISCONNECTED
    );

    public InventoryListener(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.state = plugin.getStateService();
        this.starter = plugin.getStarterService();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();

        // Handle GUI clicks - cancel ALL interactions first (including number key swaps)
        if (holder instanceof GuiHolder guiHolder) {
            event.setCancelled(true);
            guiHolder.onClick(event);
            return;
        }

        // Protect specific slots during restricted modes (allow moving VRS items freely within player inventory)
        if (isInRestrictedMode(player)) {
            if (isProtectedSlot(event, player)) {
                event.setCancelled(true);
                return;
            }

            // Prevent shift-clicking VRS items into external inventories (shops, chests, etc.)
            if (isVrsItemTransferToExternal(event)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();

        // Cancel drags in GUIs
        if (holder instanceof GuiHolder) {
            event.setCancelled(true);
            return;
        }

        // Protect VRS equipment during restricted modes
        if (isInRestrictedMode(player)) {
            // Cancel if any protected slot is involved
            for (int slot : event.getRawSlots()) {
                if (isProtectedSlotNumber(slot, player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof GuiHolder guiHolder) {
            guiHolder.onClose(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Prevent ALL item drops during restricted modes (all modes except LOBBY)
        if (isInRestrictedMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // Prevent swapping VRS weapons during restricted modes
        if (isInRestrictedMode(player)) {
            ItemStack mainHand = event.getMainHandItem();
            ItemStack offHand = event.getOffHandItem();

            if (starter.isVrsEquipment(mainHand, "weapon") ||
                starter.isVrsEquipment(offHand, "weapon")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Prevent item pickup while GUI is open
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof GuiHolder) {
            event.setCancelled(true);
            return;
        }

        // Prevent ALL item pickups during restricted modes (all modes except LOBBY)
        if (isInRestrictedMode(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Checks if the player is in a restricted mode.
     */
    private boolean isInRestrictedMode(Player player) {
        return state.getPlayer(player.getUniqueId())
                .map(ps -> RESTRICTED_MODES.contains(ps.getMode()))
                .orElse(false);
    }

    /**
     * Checks if the click event involves a protected slot.
     */
    private boolean isProtectedSlot(InventoryClickEvent event, Player player) {
        // Slot 0 is main hand (weapon slot)
        // Armor slots are 36-39 (boots, leggings, chestplate, helmet)
        int slot = event.getSlot();

        if (event.getClickedInventory() instanceof PlayerInventory) {
            // Main hand slot (weapon)
            if (slot == 0) {
                return true;
            }
            // Helmet slot (39)
            if (slot == 39) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a raw slot number is protected.
     */
    private boolean isProtectedSlotNumber(int rawSlot, Player player) {
        // In player inventory view:
        // 0-8: hotbar (0 is main hand)
        // 36-39: armor slots
        return rawSlot == 0 || rawSlot == 39;
    }

    /**
     * Checks if the click event involves a VRS item.
     */
    private boolean isVrsItemClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        return starter.isVrsItem(currentItem) || starter.isVrsItem(cursorItem);
    }

    /**
     * Checks if a VRS item is being transferred to an external inventory.
     * This catches shift-click and number key transfers to non-player inventories.
     */
    private boolean isVrsItemTransferToExternal(InventoryClickEvent event) {
        // Only check if there's a non-player inventory open
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            // Player's own crafting grid (no external inventory open)
            return false;
        }

        // Check shift-click from player inventory with VRS item
        if (event.isShiftClick() && event.getClickedInventory() instanceof PlayerInventory) {
            ItemStack clickedItem = event.getCurrentItem();
            if (starter.isVrsItem(clickedItem)) {
                return true;
            }
        }

        // Check number key swap that would move VRS item to external inventory
        if (event.getClick() == ClickType.NUMBER_KEY) {
            // Number key pressed while clicking in top inventory - would swap with hotbar
            if (!(event.getClickedInventory() instanceof PlayerInventory)) {
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot >= 0) {
                    Player player = (Player) event.getWhoClicked();
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                    if (starter.isVrsItem(hotbarItem)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
