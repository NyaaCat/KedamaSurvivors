package cat.nyaa.survivors.listener;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.service.MerchantService;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

/**
 * Handles player interactions with merchant armor stands.
 */
public class MerchantListener implements Listener {

    private final KedamaSurvivorsPlugin plugin;
    private final MerchantService merchantService;

    public MerchantListener(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.merchantService = plugin.getMerchantService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) {
            return;
        }

        // Check if this is a VRS merchant
        if (!armorStand.getScoreboardTags().contains("vrs_merchant")) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();

        // Get merchant UUID from armor stand's persistent data
        java.util.UUID merchantId = getMerchantId(armorStand);
        if (merchantId == null) {
            return;
        }

        merchantService.handleInteract(player, merchantId);
    }

    /**
     * Extracts merchant UUID from the armor stand's scoreboard tags.
     * The UUID is stored as a tag in format "vrs_merchant_uuid:<uuid>".
     */
    private java.util.UUID getMerchantId(ArmorStand armorStand) {
        for (String tag : armorStand.getScoreboardTags()) {
            if (tag.startsWith("vrs_merchant_uuid:")) {
                String uuidStr = tag.substring("vrs_merchant_uuid:".length());
                try {
                    return java.util.UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
