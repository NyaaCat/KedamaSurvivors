package cat.nyaa.survivors.service;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.config.ItemTemplateConfig;
import cat.nyaa.survivors.economy.EconomyService;
import cat.nyaa.survivors.gui.MerchantShopGui;
import cat.nyaa.survivors.i18n.I18nService;
import cat.nyaa.survivors.merchant.*;
import cat.nyaa.survivors.model.RunState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages merchant spawning, interactions, and lifecycle.
 * Supports both multi-type (shop GUI) and single-type (direct purchase) merchants.
 * Supports both fixed and wandering merchant behaviors.
 */
public class MerchantService {

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;
    private final I18nService i18n;
    private final AdminConfigService adminConfig;

    // Active merchants by instance ID
    private final Map<UUID, MerchantInstance> activeMerchants = new ConcurrentHashMap<>();

    // Per-run merchant tracking
    private final Map<UUID, Set<UUID>> runMerchants = new ConcurrentHashMap<>();

    // Merchant spawn task per run
    private final Map<UUID, Integer> runSpawnTasks = new ConcurrentHashMap<>();

    // Global tasks
    private int despawnCheckerTaskId = -1;
    private int headItemCycleTaskId = -1;

    public MerchantService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
        this.i18n = plugin.getI18nService();
        this.adminConfig = plugin.getAdminConfigService();
    }

    /**
     * Starts the merchant service.
     */
    public void start() {
        if (!config.isMerchantsEnabled()) {
            return;
        }

        // Start despawn checker every second
        despawnCheckerTaskId = Bukkit.getScheduler().runTaskTimer(
                plugin, this::checkDespawns, 20, 20
        ).getTaskId();

        // Start head item cycle task (every 10 seconds by default)
        int cycleInterval = config.getMerchantHeadItemCycleInterval();
        if (cycleInterval > 0) {
            headItemCycleTaskId = Bukkit.getScheduler().runTaskTimer(
                    plugin, this::cycleHeadItems, cycleInterval, cycleInterval
            ).getTaskId();
        }

        plugin.getLogger().info("Merchant service started");
    }

    /**
     * Stops the merchant service.
     */
    public void stop() {
        // Cancel global tasks
        if (despawnCheckerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(despawnCheckerTaskId);
            despawnCheckerTaskId = -1;
        }
        if (headItemCycleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(headItemCycleTaskId);
            headItemCycleTaskId = -1;
        }

        // Cancel all spawn tasks
        for (int taskId : runSpawnTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        runSpawnTasks.clear();

        // Clear all merchants
        clearAllMerchants();
    }

    /**
     * Starts merchant spawning for a run.
     */
    public void startForRun(RunState run) {
        if (!config.isMerchantsEnabled()) {
            return;
        }

        UUID runId = run.getRunId();
        if (runSpawnTasks.containsKey(runId)) {
            return;
        }

        // Initialize merchant set for this run
        runMerchants.put(runId, ConcurrentHashMap.newKeySet());

        // Schedule periodic wandering merchant spawning
        int intervalTicks = config.getMerchantSpawnInterval() * 20;
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Optional<RunState> runOpt = state.getRun(runId);
            runOpt.ifPresent(this::trySpawnWanderingMerchant);
        }, intervalTicks, intervalTicks).getTaskId();

        runSpawnTasks.put(runId, taskId);
    }

    /**
     * Stops merchant spawning for a run and clears its merchants.
     */
    public void stopForRun(UUID runId) {
        Integer taskId = runSpawnTasks.remove(runId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        clearMerchantsForRun(runId);
        runMerchants.remove(runId);
    }

    // ==================== Merchant Spawning ====================

    /**
     * Attempts to spawn a wandering merchant for a run.
     */
    private void trySpawnWanderingMerchant(RunState run) {
        if (!run.isActive()) return;

        // Check spawn chance
        double spawnChance = config.getMerchantSpawnChance();
        if (ThreadLocalRandom.current().nextDouble() > spawnChance) {
            return;
        }

        // Find spawn location
        Location spawnLoc = sampleWanderingLocation(run);
        if (spawnLoc == null) {
            return;
        }

        // Get configured pool - wandering merchants require explicit pool configuration
        String poolId = config.getWanderingMerchantPoolId();
        if (poolId == null || poolId.isEmpty()) {
            // No pool configured, wandering merchants disabled
            return;
        }

        Optional<MerchantItemPool> poolOpt = adminConfig.getMerchantPool(poolId);
        if (poolOpt.isEmpty()) {
            plugin.getLogger().warning("Wandering merchant pool '" + poolId + "' not found");
            return;
        }

        MerchantItemPool pool = poolOpt.get();
        if (pool.getItems().isEmpty()) {
            plugin.getLogger().warning("Wandering merchant pool '" + poolId + "' is empty");
            return;
        }

        // Parse configured merchant type (default: SINGLE)
        String typeStr = config.getWanderingMerchantType();
        MerchantType type = "multi".equalsIgnoreCase(typeStr) ? MerchantType.MULTI : MerchantType.SINGLE;

        // Spawn the merchant
        MerchantInstance merchant = spawnMerchant(
                spawnLoc,
                type,
                MerchantBehavior.WANDERING,
                pool,
                config.isMerchantLimited(),
                i18n.get("merchant.shop_nametag")
        );

        if (merchant != null) {
            // Set despawn time
            int minStay = config.getMerchantMinStaySeconds();
            int maxStay = config.getMerchantMaxStaySeconds();
            int stayTime = minStay + ThreadLocalRandom.current().nextInt(maxStay - minStay + 1);
            merchant.setDespawnTimeMillis(System.currentTimeMillis() + stayTime * 1000L);
            merchant.setRunId(run.getRunId());

            // Track per-run
            Set<UUID> merchants = runMerchants.get(run.getRunId());
            if (merchants != null) {
                merchants.add(merchant.getInstanceId());
            }

            // Spawn particles
            if (config.isMerchantSpawnParticles()) {
                spawnLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc, 20, 0.5, 0.5, 0.5, 0);
            }

            // Notify players
            notifyRunPlayers(run, "merchant.merchant_arrived");
        }
    }

    /**
     * Spawns a merchant at the given location.
     */
    public MerchantInstance spawnMerchant(Location location, MerchantType type, MerchantBehavior behavior,
                                          MerchantItemPool pool, boolean limited, String displayName) {
        UUID instanceId = UUID.randomUUID();

        // Create entity
        MerchantEntity entity = new MerchantEntity(instanceId);

        // Determine initial head item
        ItemStack headItem = null;
        WeightedShopItem singleItem = null;
        List<WeightedShopItem> stock = new ArrayList<>();

        if (type == MerchantType.SINGLE) {
            singleItem = pool.selectSingle();
            if (singleItem != null) {
                headItem = resolveItemStack(singleItem.getItemTemplateId());
                // Update display name to show item name and price
                String itemName = getItemDisplayName(singleItem.getItemTemplateId());
                displayName = i18n.get("merchant.single_nametag",
                        "item_name", itemName,
                        "price", singleItem.getPrice());
            }
        } else {
            // Multi type - select random or all items
            int minItems = config.getMerchantMinItems();
            int maxItems = config.getMerchantMaxItems();
            if (minItems <= 0 || maxItems <= 0) {
                stock = pool.getAllItems();
            } else {
                stock = pool.selectRandom(minItems, maxItems);
            }
            if (!stock.isEmpty()) {
                headItem = resolveItemStack(stock.get(0).getItemTemplateId());
            }
        }

        // Spawn the entity
        entity.spawn(location, headItem, displayName);
        entity.startAnimation(plugin);

        // Create instance
        MerchantInstance instance = new MerchantInstance(
                instanceId, entity, type, behavior,
                pool.getPoolId(), limited, displayName
        );

        if (type == MerchantType.SINGLE) {
            instance.setSingleItem(singleItem);
        } else {
            instance.setCurrentStock(stock);
        }

        // Register
        activeMerchants.put(instanceId, instance);

        plugin.getLogger().info("Spawned " + type + " merchant at " + formatLocation(location));

        return instance;
    }

    /**
     * Spawns a fixed merchant at the given location.
     */
    public MerchantInstance spawnFixedMerchant(Location location, MerchantType type, String poolId,
                                                boolean limited, String displayName) {
        Optional<MerchantItemPool> poolOpt = adminConfig.getMerchantPool(poolId);
        if (poolOpt.isEmpty()) {
            return null;
        }

        MerchantInstance merchant = spawnMerchant(location, type, MerchantBehavior.FIXED,
                poolOpt.get(), limited, displayName);

        // Fixed merchants don't despawn on time
        if (merchant != null) {
            merchant.setDespawnTimeMillis(0);
        }

        return merchant;
    }

    // ==================== Merchant Interaction ====================

    /**
     * Handles player interaction with a merchant.
     */
    public void handleInteract(Player player, UUID entityId) {
        MerchantInstance merchant = findMerchantByEntityId(entityId);
        if (merchant == null) {
            return;
        }

        if (merchant.getType() == MerchantType.MULTI) {
            handleMultiInteract(player, merchant);
        } else {
            handleSingleInteract(player, merchant);
        }
    }

    private void handleMultiInteract(Player player, MerchantInstance merchant) {
        // Open shop GUI
        MerchantShopGui gui = new MerchantShopGui(player, plugin, merchant);
        gui.open();
    }

    private void handleSingleInteract(Player player, MerchantInstance merchant) {
        WeightedShopItem item = merchant.getSingleItem();
        if (item == null) {
            return;
        }

        EconomyService economy = plugin.getEconomyService();
        int price = item.getPrice();

        // Check balance
        if (!economy.hasBalance(player, price)) {
            i18n.send(player, "merchant.not_enough_coins", "price", price);
            return;
        }

        // Resolve item
        ItemStack purchaseItem = resolveItemStack(item.getItemTemplateId());
        if (purchaseItem == null) {
            i18n.send(player, "error.generic");
            return;
        }

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            i18n.send(player, "info.reward_overflow", "count", 1);
            return;
        }

        // Deduct and give
        if (economy.deduct(player, price, "merchant_purchase")) {
            player.getInventory().addItem(purchaseItem);
            i18n.send(player, "merchant.purchase_success", "price", price);

            // Handle limited
            if (merchant.isLimited()) {
                despawnMerchant(merchant.getInstanceId());
                i18n.send(player, "merchant.merchant_left");
            }
        }
    }

    // ==================== Despawn Management ====================

    /**
     * Checks for merchants that should be despawned.
     */
    private void checkDespawns() {
        List<UUID> toRemove = new ArrayList<>();

        for (MerchantInstance merchant : activeMerchants.values()) {
            if (!merchant.isValid() || merchant.shouldDespawn()) {
                toRemove.add(merchant.getInstanceId());
            }
        }

        for (UUID id : toRemove) {
            despawnMerchant(id);
        }
    }

    /**
     * Despawns a merchant by instance ID.
     */
    public void despawnMerchant(UUID instanceId) {
        MerchantInstance merchant = activeMerchants.remove(instanceId);
        if (merchant == null) {
            return;
        }

        // Despawn particles
        Location loc = merchant.getLocation();
        if (loc != null && config.isMerchantDespawnParticles()) {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.5, 0.5, 0.5, 0);
        }

        // Remove from run tracking
        if (merchant.getRunId() != null) {
            Set<UUID> merchants = runMerchants.get(merchant.getRunId());
            if (merchants != null) {
                merchants.remove(instanceId);
            }
        }

        merchant.despawn();
    }

    /**
     * Clears all merchants for a specific run.
     */
    public void clearMerchantsForRun(UUID runId) {
        Set<UUID> merchants = runMerchants.get(runId);
        if (merchants == null) return;

        for (UUID merchantId : new ArrayList<>(merchants)) {
            despawnMerchant(merchantId);
        }
        merchants.clear();
    }

    /**
     * Clears all merchants globally.
     */
    public void clearAllMerchants() {
        for (UUID merchantId : new ArrayList<>(activeMerchants.keySet())) {
            despawnMerchant(merchantId);
        }
        activeMerchants.clear();

        // Also clear any untracked merchants by tag
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(MerchantEntity.MERCHANT_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    // ==================== Head Item Cycling ====================

    /**
     * Cycles head items for all multi-type merchants.
     */
    private void cycleHeadItems() {
        for (MerchantInstance merchant : activeMerchants.values()) {
            if (merchant.getType() == MerchantType.MULTI && merchant.isValid()) {
                merchant.cycleHeadItem(this::resolveItemStack);
            }
        }
    }

    // ==================== Location Sampling ====================

    /**
     * Samples a location for wandering merchant spawning.
     */
    private Location sampleWanderingLocation(RunState run) {
        List<UUID> alivePlayers = new ArrayList<>(run.getAlivePlayers());
        if (alivePlayers.isEmpty()) return null;

        UUID targetId = alivePlayers.get(ThreadLocalRandom.current().nextInt(alivePlayers.size()));
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) return null;

        Location playerLoc = target.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return null;

        double minDist = config.getMerchantMinDistance();
        double maxDist = config.getMerchantMaxDistance();

        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;
            int y = world.getHighestBlockYAt((int) x, (int) z);

            Location candidate = new Location(world, x, y + 1, z);

            if (isValidMerchantLocation(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Checks if a location is valid for merchant spawning.
     */
    private boolean isValidMerchantLocation(Location loc) {
        if (loc.getWorld() == null) return false;

        // Check passable at feet and head
        if (!loc.getBlock().isPassable()) return false;
        if (!loc.clone().add(0, 1, 0).getBlock().isPassable()) return false;

        // Check solid ground
        if (!loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) return false;

        return true;
    }

    // ==================== Helper Methods ====================

    private MerchantInstance findMerchantByEntityId(UUID entityId) {
        for (MerchantInstance merchant : activeMerchants.values()) {
            if (merchant.getEntity().getEntityId().equals(entityId)) {
                return merchant;
            }
        }
        return null;
    }

    private ItemStack resolveItemStack(String templateId) {
        Optional<ItemTemplateConfig> templateOpt = adminConfig.getItemTemplate(templateId);
        return templateOpt.map(ItemTemplateConfig::toItemStack).orElse(null);
    }

    private String getItemDisplayName(String templateId) {
        Optional<ItemTemplateConfig> templateOpt = adminConfig.getItemTemplate(templateId);
        if (templateOpt.isEmpty()) {
            return templateId;
        }
        ItemStack item = templateOpt.get().toItemStack();
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        return item.getType().name();
    }

    private void notifyRunPlayers(RunState run, String messageKey) {
        for (UUID playerId : run.getParticipants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                i18n.send(player, messageKey);
            }
        }
    }

    private String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    // ==================== Public Accessors ====================

    /**
     * Checks if an entity is a VRS merchant.
     */
    public boolean isMerchant(Entity entity) {
        return entity.getScoreboardTags().contains(MerchantEntity.MERCHANT_TAG);
    }

    /**
     * Gets the count of active merchants for a run.
     */
    public int getMerchantCount(UUID runId) {
        Set<UUID> merchants = runMerchants.get(runId);
        return merchants != null ? merchants.size() : 0;
    }

    /**
     * Gets all active merchants.
     */
    public Collection<MerchantInstance> getActiveMerchants() {
        return Collections.unmodifiableCollection(activeMerchants.values());
    }

    /**
     * Gets a merchant by instance ID.
     */
    public Optional<MerchantInstance> getMerchant(UUID instanceId) {
        return Optional.ofNullable(activeMerchants.get(instanceId));
    }

    // ==================== Persistence ====================

    /**
     * Data class for persisting fixed merchants.
     */
    public static class FixedMerchantData {
        public String worldName;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String type;
        public String poolId;
        public boolean limited;
        public String displayName;

        public FixedMerchantData() {}

        public static FixedMerchantData fromMerchant(MerchantInstance merchant) {
            FixedMerchantData data = new FixedMerchantData();
            Location loc = merchant.getLocation();
            if (loc != null && loc.getWorld() != null) {
                data.worldName = loc.getWorld().getName();
                data.x = loc.getX();
                data.y = loc.getY();
                data.z = loc.getZ();
                data.yaw = loc.getYaw();
                data.pitch = loc.getPitch();
            }
            data.type = merchant.getType().name();
            data.poolId = merchant.getPoolId();
            data.limited = merchant.isLimited();
            data.displayName = merchant.getDisplayName();
            return data;
        }

        public Location toLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    /**
     * Gets all fixed merchants for persistence.
     */
    public List<FixedMerchantData> getFixedMerchantsData() {
        List<FixedMerchantData> data = new ArrayList<>();
        for (MerchantInstance merchant : activeMerchants.values()) {
            if (merchant.getBehavior() == MerchantBehavior.FIXED && merchant.isValid()) {
                data.add(FixedMerchantData.fromMerchant(merchant));
            }
        }
        return data;
    }

    /**
     * Loads fixed merchants from persisted data.
     * Should be called after the service starts.
     */
    public void loadFixedMerchants(List<FixedMerchantData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        int loaded = 0;
        int failed = 0;

        for (FixedMerchantData data : dataList) {
            Location loc = data.toLocation();
            if (loc == null) {
                plugin.getLogger().warning("Failed to load merchant: world '" + data.worldName + "' not found");
                failed++;
                continue;
            }

            MerchantType type;
            try {
                type = MerchantType.valueOf(data.type);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Failed to load merchant: invalid type '" + data.type + "'");
                failed++;
                continue;
            }

            MerchantInstance merchant = spawnFixedMerchant(loc, type, data.poolId, data.limited, data.displayName);
            if (merchant != null) {
                loaded++;
            } else {
                plugin.getLogger().warning("Failed to spawn merchant at " + formatLocation(loc) +
                        " - pool '" + data.poolId + "' may not exist");
                failed++;
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " fixed merchants" +
                (failed > 0 ? " (" + failed + " failed)" : ""));
    }
}
