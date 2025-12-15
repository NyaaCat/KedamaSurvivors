package cat.nyaa.survivors.merchant;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Handles the visual representation of a merchant using an invisible armor stand for hitbox
 * and an ItemDisplay for the floating item preview.
 * The merchant floats and spins slowly for visual appeal.
 */
public class MerchantEntity {

    public static final String MERCHANT_TAG = "vrs_merchant";

    // Animation constants (can be made configurable)
    private static final float DEFAULT_ROTATION_SPEED = 3.0f;  // degrees per tick
    private static final double DEFAULT_BOB_SPEED = 0.015;
    private static final double DEFAULT_BOB_HEIGHT = 0.15;
    private static final float ITEM_DISPLAY_SCALE = 0.7f;  // Scale for item display

    private final UUID entityId;
    private ArmorStand armorStand;
    private ItemDisplay itemDisplay;
    private Location baseLocation;

    // Animation state
    private float rotation = 0;
    private double bobOffset = 0;
    private int bobDirection = 1;  // 1 = up, -1 = down

    // Animation settings
    private float rotationSpeed = DEFAULT_ROTATION_SPEED;
    private double bobSpeed = DEFAULT_BOB_SPEED;
    private double bobHeight = DEFAULT_BOB_HEIGHT;

    public MerchantEntity(UUID entityId) {
        this.entityId = entityId;
    }

    /**
     * Spawns the merchant at the given location.
     * Uses an ArmorStand for hitbox/interaction and an ItemDisplay for the item preview.
     *
     * @param location    the spawn location
     * @param headItem    the item to display (for 3D preview)
     * @param displayName the name tag to show above the merchant
     * @return this entity for chaining
     */
    public MerchantEntity spawn(Location location, ItemStack headItem, String displayName) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location must have a world");
        }

        this.baseLocation = location.clone();

        // Spawn invisible ArmorStand for hitbox and name tag
        armorStand = world.spawn(baseLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            stand.setBasePlate(false);
            stand.setArms(false);

            // Name tag
            stand.setCustomName(displayName);
            stand.setCustomNameVisible(true);

            // Tag for identification
            stand.addScoreboardTag(MERCHANT_TAG);
            stand.addScoreboardTag("vrs_merchant_uuid:" + entityId.toString());
        });

        // Spawn ItemDisplay for the floating item preview (at chest height of armor stand)
        Location displayLoc = baseLocation.clone().add(0, 1.2, 0);
        itemDisplay = world.spawn(displayLoc, ItemDisplay.class, display -> {
            if (headItem != null) {
                display.setItemStack(headItem.clone());
            }
            display.setBillboard(Display.Billboard.CENTER);  // Always face player

            // Scale transformation
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),  // translation
                    new AxisAngle4f(0, 0, 1, 0),  // left rotation
                    new Vector3f(ITEM_DISPLAY_SCALE, ITEM_DISPLAY_SCALE, ITEM_DISPLAY_SCALE),  // scale
                    new AxisAngle4f(0, 0, 1, 0)   // right rotation
            ));

            // Tag for identification and cleanup
            display.addScoreboardTag(MERCHANT_TAG);
            display.addScoreboardTag("vrs_merchant_uuid:" + entityId.toString());
        });

        return this;
    }

    /**
     * Starts the animation task (spinning and floating).
     *
     * @param plugin the plugin to schedule the task
     * @deprecated Animation is now handled centrally by MerchantService
     */
    @Deprecated
    public void startAnimation(Plugin plugin) {
        // Animation now handled centrally by MerchantService.animateAllMerchants()
    }

    /**
     * Stops the animation task.
     *
     * @deprecated Animation is now handled centrally by MerchantService
     */
    @Deprecated
    public void stopAnimation() {
        // Animation now handled centrally by MerchantService
    }

    /**
     * Updates the animation state for a single frame.
     * Called by MerchantService's centralized animation loop.
     */
    public void updateAnimation() {
        if (armorStand == null || armorStand.isDead()) {
            return;
        }

        // Update rotation
        rotation = (rotation + rotationSpeed) % 360;

        // Update bob offset
        bobOffset += bobDirection * bobSpeed;
        if (bobOffset >= bobHeight) {
            bobDirection = -1;
        } else if (bobOffset <= -bobHeight) {
            bobDirection = 1;
        }

        // Apply to armor stand (rotation for name tag facing)
        Location newLoc = baseLocation.clone();
        newLoc.setY(baseLocation.getY() + bobOffset);
        newLoc.setYaw(rotation);
        armorStand.teleport(newLoc);

        // Apply bob to item display (billboard handles facing, no rotation needed)
        if (itemDisplay != null && !itemDisplay.isDead()) {
            Location displayLoc = baseLocation.clone().add(0, 1.2 + bobOffset, 0);
            itemDisplay.teleport(displayLoc);
        }
    }

    /**
     * Sets the item displayed by the merchant.
     *
     * @param item the item to display
     */
    public void setHeadItem(ItemStack item) {
        if (itemDisplay != null && !itemDisplay.isDead()) {
            itemDisplay.setItemStack(item != null ? item.clone() : null);
        }
    }

    /**
     * Updates the display name of the merchant.
     *
     * @param displayName the new display name
     */
    public void setDisplayName(String displayName) {
        if (armorStand != null && !armorStand.isDead()) {
            armorStand.setCustomName(displayName);
        }
    }

    /**
     * Removes the merchant entities from the world.
     */
    public void remove() {
        stopAnimation();
        if (armorStand != null && !armorStand.isDead()) {
            armorStand.remove();
        }
        armorStand = null;

        if (itemDisplay != null && !itemDisplay.isDead()) {
            itemDisplay.remove();
        }
        itemDisplay = null;
    }

    /**
     * Checks if the entity is still valid (not removed).
     */
    public boolean isValid() {
        return armorStand != null && !armorStand.isDead();
    }

    /**
     * Gets the unique ID of this merchant entity.
     */
    public UUID getEntityId() {
        return entityId;
    }

    /**
     * Gets the underlying armor stand entity.
     */
    public ArmorStand getArmorStand() {
        return armorStand;
    }

    /**
     * Gets the base location of the merchant.
     */
    public Location getBaseLocation() {
        return baseLocation != null ? baseLocation.clone() : null;
    }

    /**
     * Sets animation parameters.
     */
    public void setAnimationParams(float rotationSpeed, double bobSpeed, double bobHeight) {
        this.rotationSpeed = rotationSpeed;
        this.bobSpeed = bobSpeed;
        this.bobHeight = bobHeight;
    }

    /**
     * Gets the armor stand's current location.
     */
    public Location getCurrentLocation() {
        return armorStand != null ? armorStand.getLocation() : null;
    }
}
