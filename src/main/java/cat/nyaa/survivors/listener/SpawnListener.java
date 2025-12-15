package cat.nyaa.survivors.listener;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.service.WorldService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Handles creature spawn events to block natural spawns in combat worlds.
 * Only allows VRS-spawned mobs (identified by vrs_mob tag) in combat worlds.
 */
public class SpawnListener implements Listener {

    private static final String VRS_MOB_TAG = "vrs_mob";

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final WorldService worldService;

    public SpawnListener(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.worldService = plugin.getWorldService();
    }

    /**
     * Blocks natural mob spawns in combat worlds.
     * Only VRS-spawned mobs (with vrs_mob tag) are allowed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Skip if feature is disabled
        if (!config.isBlockNaturalSpawns()) {
            return;
        }

        Entity entity = event.getEntity();
        String worldName = entity.getWorld().getName();

        // Only apply to combat worlds
        if (worldService.getWorldConfig(worldName).isEmpty()) {
            return;
        }

        // Allow VRS-spawned mobs (they have the vrs_mob tag)
        if (entity.getScoreboardTags().contains(VRS_MOB_TAG)) {
            return;
        }

        // Allow VRS merchants (armor stands with vrs_merchant tag)
        if (entity.getScoreboardTags().contains("vrs_merchant")) {
            return;
        }

        // Allow players (should never happen but just in case)
        if (entity instanceof Player) {
            return;
        }

        // Only allow COMMAND spawn reason (used by summon command)
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.COMMAND) {
            return;
        }

        // Block all other spawns (natural, spawner, breeding, spawn eggs, etc.)

        // Cancel the spawn
        event.setCancelled(true);

        // Log blocked spawns (only when verbose is enabled to avoid spam)
        //if (config.isVerbose()) {
        //    plugin.getLogger().info("[SpawnListener] Blocked " + entity.getType() + " in " +
        //            worldName + " (reason: " + reason + ", hasTag: " +
        //            entity.getScoreboardTags().contains(VRS_MOB_TAG) + ")");
        //}
    }
}
