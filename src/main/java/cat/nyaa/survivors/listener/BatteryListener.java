package cat.nyaa.survivors.listener;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.service.BatteryService;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.UUID;

/**
 * Handles player interactions with battery entities.
 */
public class BatteryListener implements Listener {

    private final BatteryService batteryService;

    public BatteryListener(KedamaSurvivorsPlugin plugin) {
        this.batteryService = plugin.getBatteryService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) {
            return;
        }

        if (!armorStand.getScoreboardTags().contains("vrs_battery")) {
            return;
        }

        event.setCancelled(true);

        UUID batteryId = BatteryService.extractBatteryId(armorStand);
        if (batteryId == null) {
            return;
        }

        batteryService.handleInteract(event.getPlayer(), batteryId);
    }
}
