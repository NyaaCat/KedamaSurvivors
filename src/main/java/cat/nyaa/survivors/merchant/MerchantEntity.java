package cat.nyaa.survivors.merchant;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Handles the visual representation of a merchant using an invisible armor stand.
 * The merchant floats and spins slowly for visual appeal.
 */
public class MerchantEntity {

    public static final String MERCHANT_TAG = "vrs_merchant";

    // Animation constants (can be made configurable)
    private static final float DEFAULT_ROTATION_SPEED = 3.0f;  // degrees per tick
    private static final double DEFAULT_BOB_SPEED = 0.015;
    private static final double DEFAULT_BOB_HEIGHT = 0.15;

    private final UUID entityId;
    private ArmorStand armorStand;
    private Location baseLocation;
    private int animationTaskId = -1;

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
     * Spawns the merchant armor stand at the given location.
     *
     * @param location    the spawn location
     * @param headItem    the item to display on the head (for 3D preview)
     * @param displayName the name tag to show above the merchant
     * @return this entity for chaining
     */
    public MerchantEntity spawn(Location location, ItemStack headItem, String displayName) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location must have a world");
        }

        this.baseLocation = location.clone();

        armorStand = world.spawn(location, ArmorStand.class, stand -> {
            // Make invisible but with visible name
            stand.setVisible(false);
            stand.setSmall(true);
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

            // Set head item for 3D preview
            if (headItem != null) {
                stand.getEquipment().setHelmet(headItem.clone());
            }
        });

        return this;
    }

    /**
     * Starts the animation task (spinning and floating).
     *
     * @param plugin the plugin to schedule the task
     */
    public void startAnimation(Plugin plugin) {
        if (animationTaskId != -1) {
            return;  // Already running
        }

        animationTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (armorStand == null || armorStand.isDead()) {
                stopAnimation();
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

            // Apply to armor stand
            Location newLoc = baseLocation.clone();
            newLoc.setY(baseLocation.getY() + bobOffset);
            newLoc.setYaw(rotation);

            armorStand.teleport(newLoc);
        }, 0L, 1L).getTaskId();
    }

    /**
     * Stops the animation task.
     */
    public void stopAnimation() {
        if (animationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(animationTaskId);
            animationTaskId = -1;
        }
    }

    /**
     * Sets the item displayed on the merchant's head.
     *
     * @param item the item to display
     */
    public void setHeadItem(ItemStack item) {
        if (armorStand != null && !armorStand.isDead()) {
            armorStand.getEquipment().setHelmet(item != null ? item.clone() : null);
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
     * Removes the merchant entity from the world.
     */
    public void remove() {
        stopAnimation();
        if (armorStand != null && !armorStand.isDead()) {
            armorStand.remove();
        }
        armorStand = null;
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
