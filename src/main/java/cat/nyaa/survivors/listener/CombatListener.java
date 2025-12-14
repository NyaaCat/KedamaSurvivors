package cat.nyaa.survivors.listener;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.RewardService;
import cat.nyaa.survivors.service.StateService;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles combat-related events including VRS mob deaths and invulnerability.
 */
public class CombatListener implements Listener {

    private static final String VRS_MOB_TAG = "vrs_mob";
    private static final Pattern LEVEL_TAG_PATTERN = Pattern.compile("vrs_lvl_(\\d+)");

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final I18nService i18n;
    private final StateService state;

    public CombatListener(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.i18n = plugin.getI18nService();
        this.state = plugin.getStateService();
    }

    /**
     * Handles damage prevention for PVP and invulnerability.
     * PVP damage is set to 0 (not cancelled) to allow other plugins to process the event.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Handle PVP damage prevention when victim is a player
        if (event.getEntity() instanceof Player victim) {
            if (!config.isPvpEnabled()) {
                // Check for player damager (direct or via projectile)
                Player damager = getDamagerPlayer(event);
                if (damager != null && !damager.equals(victim)) {
                    // Set damage to 0 instead of cancelling (allows other plugins to process)
                    event.setDamage(0);
                    return;
                }

                // Check for AreaEffectCloud (lingering potions)
                Entity damagerEntity = event.getDamager();
                if (damagerEntity instanceof AreaEffectCloud cloud) {
                    ProjectileSource source = cloud.getSource();
                    if (source instanceof Player thrower && !thrower.equals(victim)) {
                        event.setDamage(0);
                        return;
                    }
                }
            }
        }

        // Handle invulnerability - prevent invulnerable players from dealing damage
        Player damager = getDamagerPlayer(event);
        if (damager == null) return;

        Optional<PlayerState> playerStateOpt = state.getPlayer(damager.getUniqueId());
        if (playerStateOpt.isEmpty()) return;

        PlayerState playerState = playerStateOpt.get();

        // Check if invulnerable player cannot deal damage
        if (!config.isCanDealDamageDuringInvul() && playerState.isInvulnerable()) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles VRS mob death events - awards XP, coins, and perma-score.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if this is a VRS mob
        if (!isVrsMob(entity)) return;

        // Parse enemy level from tags
        int enemyLevel = parseEnemyLevel(entity);

        // Parse archetype from tags
        String archetypeId = parseArchetype(entity);

        // Find the killer
        Player killer = entity.getKiller();
        if (killer == null) {
            killer = getLastDamager(entity);
        }

        if (killer == null) {
            // No valid killer - just clean up drops
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }

        Optional<PlayerState> killerStateOpt = state.getPlayer(killer.getUniqueId());
        if (killerStateOpt.isEmpty() || killerStateOpt.get().getMode() != PlayerMode.IN_RUN) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }

        // Get reward service and process rewards
        RewardService rewardService = plugin.getRewardService();
        if (rewardService != null) {
            rewardService.processKillReward(killer, archetypeId, enemyLevel, entity.getLocation());
        }

        // Clear drops - rewards are direct to inventory
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /**
     * Checks if an entity is a VRS mob.
     */
    private boolean isVrsMob(Entity entity) {
        return entity.getScoreboardTags().contains(VRS_MOB_TAG);
    }

    /**
     * Parses the enemy level from scoreboard tags.
     * Looks for tags in format: vrs_lvl_N
     */
    private int parseEnemyLevel(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            Matcher matcher = LEVEL_TAG_PATTERN.matcher(tag);
            if (matcher.matches()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return config.getMinEnemyLevel();
    }

    /**
     * Parses the archetype ID from scoreboard tags.
     * Looks for tags in format: vrs_arch_ARCHETYPE_ID
     */
    private String parseArchetype(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("vrs_arch_")) {
                return tag.substring(9);
            }
        }
        // Return first configured archetype as fallback
        var archetypes = config.getEnemyArchetypes();
        if (!archetypes.isEmpty()) {
            return archetypes.keySet().iterator().next();
        }
        return "default";
    }

    /**
     * Extracts the player who dealt damage from an entity damage event.
     */
    private Player getDamagerPlayer(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }

        return null;
    }

    /**
     * Gets the last player who damaged an entity (for kill attribution).
     */
    private Player getLastDamager(LivingEntity entity) {
        // Bukkit's getKiller() usually handles this, but as a fallback
        // we can check the last damage cause
        if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
            return getDamagerPlayer(event);
        }
        return null;
    }
}
