package cat.nyaa.survivors.display;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.service.StateService;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages overhead display entities for players in combat.
 * Uses packet-based approach for lightweight performance.
 *
 * Display format: "职业 | Lv.X"
 * - 职业 = helmet group display name
 * - X = player's run level
 *
 * Visibility:
 * - Only visible to other players (not self)
 * - Only shown during IN_RUN mode
 */
public class PlayerDisplayService {

    // Default offset above player head (configurable via overheadDisplay.yOffset)
    private static final float DEFAULT_Y_OFFSET = 2.3f;

    // Entity data IDs for Display and TextDisplay (hardcoded to match NMS/protocol values)
    // Reference: https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata
    // Base Display data slots:
    private static final int DATA_ID_TRANSLATION = 11;      // Vector3 - position offset
    private static final int DATA_ID_SCALE = 12;            // Vector3 - scale
    private static final int DATA_ID_BILLBOARD = 15;        // Byte - billboard mode (0=FIXED, 1=VERTICAL, 2=HORIZONTAL, 3=CENTER)
    private static final int DATA_ID_VIEW_RANGE = 17;       // Float - view range multiplier (default 1.0)
    // TextDisplay data slots:
    private static final int DATA_ID_TEXT = 23;             // Text Component
    private static final int DATA_ID_LINE_WIDTH = 24;       // VarInt - line width (default 200)
    private static final int DATA_ID_BACKGROUND_COLOR = 25; // Int - ARGB background color
    private static final int DATA_ID_TEXT_OPACITY = 26;     // Byte - text opacity (-1 = 255 = fully opaque)
    private static final int DATA_ID_STYLE_FLAGS = 27;      // Byte - style flags (shadow, see-through, default_background, alignment)

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService config;
    private final StateService state;

    // Virtual entity ID management
    private final Map<UUID, Integer> playerToEntityId = new ConcurrentHashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);

    // Track which viewers have received spawn packets for which targets
    private final Map<UUID, Set<UUID>> viewerKnownEntities = new ConcurrentHashMap<>();

    // Task
    private BukkitTask tickTask;

    public PlayerDisplayService(KedamaSurvivorsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigService();
        this.state = plugin.getStateService();
    }

    /**
     * Initializes and starts the display service.
     */
    public void initialize() {
        startTicking();
        plugin.getLogger().info("PlayerDisplayService initialized");
    }

    /**
     * Starts the per-tick update task.
     */
    private void startTicking() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        // Run every tick on main thread to collect data, then process async
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, 1L, 1L);
    }

    /**
     * Called every tick to update display entities.
     * All operations run on main thread for thread safety with Bukkit API.
     */
    private void tickUpdate() {
        // Check if overhead display is enabled
        if (!config.isOverheadDisplayEnabled()) {
            // Clean up any existing displays if feature was disabled
            if (!playerToEntityId.isEmpty()) {
                cleanupStaleEntities(Collections.emptySet());
            }
            return;
        }

        // 1. Collect display data for all IN_RUN players
        Map<UUID, DisplayData> displayDataMap = collectDisplayData();

        if (displayDataMap.isEmpty()) {
            // Clean up any stale entities
            cleanupStaleEntities(Collections.emptySet());
            return;
        }

        // 2. Process viewers and create packets
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Optional<PlayerState> viewerStateOpt = state.getPlayer(viewer.getUniqueId());
            if (viewerStateOpt.isEmpty()) continue;

            // Only send to viewers in IN_RUN mode
            if (viewerStateOpt.get().getMode() != PlayerMode.IN_RUN) continue;

            List<Object> packets = new ArrayList<>();

            for (Map.Entry<UUID, DisplayData> entry : displayDataMap.entrySet()) {
                UUID targetId = entry.getKey();

                // Don't show player's own display to themselves
                if (targetId.equals(viewer.getUniqueId())) continue;

                Player target = Bukkit.getPlayer(targetId);
                if (target == null || !target.isOnline()) continue;

                // Check if they're in the same world
                if (!viewer.getWorld().equals(target.getWorld())) {
                    // Send destroy packet if viewer knew about this entity but is now in different world
                    sendDestroyIfKnown(viewer, targetId);
                    continue;
                }

                // Check visibility (distance-based, using render distance)
                if (!isVisible(viewer, target)) {
                    // Send destroy packet if viewer knew about this entity
                    sendDestroyIfKnown(viewer, targetId);
                    continue;
                }

                DisplayData data = entry.getValue();
                packets.addAll(createDisplayPackets(viewer, target, data));
            }

            // Send packets to this viewer
            if (!packets.isEmpty()) {
                sendPackets(viewer, packets);
            }
        }

        // Clean up entities for players who left IN_RUN mode
        cleanupStaleEntities(displayDataMap.keySet());
    }

    /**
     * Collects display data for all players in IN_RUN mode.
     */
    private Map<UUID, DisplayData> collectDisplayData() {
        Map<UUID, DisplayData> result = new HashMap<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<PlayerState> stateOpt = state.getPlayer(player.getUniqueId());
            if (stateOpt.isEmpty()) continue;

            PlayerState ps = stateOpt.get();
            if (ps.getMode() != PlayerMode.IN_RUN) continue;

            String className = getHelmetDisplayName(ps);
            int level = ps.getRunLevel();
            Location loc = player.getLocation();

            result.put(player.getUniqueId(), new DisplayData(className, level, loc));
        }

        return result;
    }

    /**
     * Gets the display name for a player's helmet group.
     */
    private String getHelmetDisplayName(PlayerState ps) {
        String helmetGroup = ps.getHelmetGroup();
        if (helmetGroup == null || helmetGroup.isEmpty()) {
            return "???";
        }

        // Look up display name from config
        var helmetGroups = config.getHelmetGroups();
        for (var group : helmetGroups.values()) {
            if (group.groupId.equals(helmetGroup)) {
                return group.displayName != null ? group.displayName : helmetGroup;
            }
        }

        return helmetGroup;
    }

    /**
     * Checks if a target player is visible to a viewer.
     */
    private boolean isVisible(Player viewer, Player target) {
        // Basic distance check (64 blocks is typical render distance for entities)
        double maxDistance = 64.0;
        return viewer.getLocation().distanceSquared(target.getLocation()) < maxDistance * maxDistance;
    }

    /**
     * Creates packets for displaying a text display above a player.
     */
    private List<Object> createDisplayPackets(Player viewer, Player target, DisplayData data) {
        List<Object> packets = new ArrayList<>();

        UUID targetId = target.getUniqueId();
        int entityId = playerToEntityId.computeIfAbsent(targetId, k -> entityIdCounter.incrementAndGet());

        // Check if viewer already knows about this entity
        Set<UUID> knownTargets = viewerKnownEntities.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        boolean isNew = knownTargets.add(targetId);

        Location loc = data.location();
        double x = loc.getX();
        double y = loc.getY() + config.getOverheadDisplayYOffset();
        double z = loc.getZ();

        if (isNew) {
            // Spawn packet for new entity
            ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                    entityId,
                    UUID.randomUUID(),
                    x, y, z,
                    0f, 0f, // pitch, yaw
                    EntityType.TEXT_DISPLAY,
                    0, // data
                    net.minecraft.world.phys.Vec3.ZERO,
                    0.0 // headYaw
            );
            packets.add(spawnPacket);
        }

        // Build display text: "职业 | Lv.X"
        Component displayText = Component.text(data.className(), NamedTextColor.GOLD)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("Lv." + data.level(), NamedTextColor.GREEN));

        // Metadata packet
        List<SynchedEntityData.DataValue<?>> dataValues = new ArrayList<>();

        // === Base Display entity data ===

        // Scale (index 12)
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_SCALE, EntityDataSerializers.VECTOR3, new Vector3f(1.0f, 1.0f, 1.0f)));

        // Billboard mode (index 15) - 3 = CENTER (always face player)
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_BILLBOARD, EntityDataSerializers.BYTE, (byte) 3));

        // View range (index 17) - multiplier for render distance (1.0 = 64 blocks default)
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_VIEW_RANGE, EntityDataSerializers.FLOAT, 1.0f));

        // === TextDisplay specific data ===

        // Text content (index 23)
        net.minecraft.network.chat.Component nmsText = PaperAdventure.asVanilla(displayText);
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_TEXT, EntityDataSerializers.COMPONENT, nmsText));

        // Line width (index 24) - default 200
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_LINE_WIDTH, EntityDataSerializers.INT, 200));

        // Background color (index 25) - transparent (ARGB 0)
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_BACKGROUND_COLOR, EntityDataSerializers.INT, 0));

        // Text opacity (index 26) - fully opaque (-1 = 255)
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_TEXT_OPACITY, EntityDataSerializers.BYTE, (byte) -1));

        // Style flags (index 27) - bit 0: shadow, bit 1: see_through, bit 2: default_background, bits 3-4: alignment
        byte flags = 0;
        flags |= 0x01; // Has shadow
        // Alignment is in bits 3-4: 0=CENTER, 1=LEFT, 2=RIGHT - default CENTER (0)
        dataValues.add(new SynchedEntityData.DataValue<>(DATA_ID_STYLE_FLAGS, EntityDataSerializers.BYTE, flags));

        ClientboundSetEntityDataPacket metadataPacket = new ClientboundSetEntityDataPacket(entityId, dataValues);
        packets.add(metadataPacket);

        // Teleport packet (update position)
        if (!isNew) {
            ClientboundTeleportEntityPacket teleportPacket = new ClientboundTeleportEntityPacket(
                    entityId,
                    new net.minecraft.world.entity.PositionMoveRotation(
                            new net.minecraft.world.phys.Vec3(x, y, z),
                            net.minecraft.world.phys.Vec3.ZERO,
                            0f, 0f
                    ),
                    Set.of(),
                    false
            );
            packets.add(teleportPacket);
        }

        return packets;
    }

    /**
     * Sends destroy packet if viewer knew about an entity.
     */
    private void sendDestroyIfKnown(Player viewer, UUID targetId) {
        Set<UUID> knownTargets = viewerKnownEntities.get(viewer.getUniqueId());
        if (knownTargets != null && knownTargets.remove(targetId)) {
            Integer entityId = playerToEntityId.get(targetId);
            if (entityId != null) {
                ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
                sendPacket(viewer, destroyPacket);
            }
        }
    }

    /**
     * Cleans up stale entities for players who are no longer in IN_RUN mode.
     */
    private void cleanupStaleEntities(Set<UUID> activeTargets) {
        // Find entities that should be removed
        Set<UUID> toRemove = new HashSet<>(playerToEntityId.keySet());
        toRemove.removeAll(activeTargets);

        if (toRemove.isEmpty()) return;

        // Send destroy packets to all viewers
        for (UUID targetId : toRemove) {
            Integer entityId = playerToEntityId.remove(targetId);
            if (entityId != null) {
                ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);

                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    Set<UUID> knownTargets = viewerKnownEntities.get(viewer.getUniqueId());
                    if (knownTargets != null && knownTargets.remove(targetId)) {
                        sendPacket(viewer, destroyPacket);
                    }
                }
            }
        }
    }

    /**
     * Sends packets to a player.
     */
    private void sendPackets(Player player, List<Object> packets) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;

        var connection = craftPlayer.getHandle().connection;
        for (Object packet : packets) {
            if (packet instanceof net.minecraft.network.protocol.Packet<?> nmsPacket) {
                connection.send(nmsPacket);
            }
        }
    }

    /**
     * Sends a single packet to a player.
     */
    private void sendPacket(Player player, Object packet) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        if (!(packet instanceof net.minecraft.network.protocol.Packet<?> nmsPacket)) return;

        craftPlayer.getHandle().connection.send(nmsPacket);
    }

    /**
     * Called when a player joins - clean up any stale state.
     */
    public void handlePlayerJoin(Player player) {
        // Nothing special needed - they'll be added on next tick if in IN_RUN mode
    }

    /**
     * Called when a player quits - clean up their display entities.
     */
    public void handlePlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();

        // Remove their entity ID
        Integer entityId = playerToEntityId.remove(playerId);

        // Remove viewer tracking
        viewerKnownEntities.remove(playerId);

        // Send destroy packets to all other viewers
        if (entityId != null) {
            ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entityId);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(playerId)) continue;

                Set<UUID> knownTargets = viewerKnownEntities.get(viewer.getUniqueId());
                if (knownTargets != null && knownTargets.remove(playerId)) {
                    sendPacket(viewer, destroyPacket);
                }
            }
        }
    }

    /**
     * Called when a player's mode changes - update their display visibility.
     */
    public void handleModeChange(Player player, PlayerMode oldMode, PlayerMode newMode) {
        if (newMode != PlayerMode.IN_RUN && oldMode == PlayerMode.IN_RUN) {
            // Player left IN_RUN mode - remove their display
            handlePlayerQuit(player); // Reuse quit logic
        }
        // If entering IN_RUN, they'll be picked up on the next tick
    }

    /**
     * Shuts down the display service.
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        // Send destroy packets for all entities
        for (Map.Entry<UUID, Integer> entry : playerToEntityId.entrySet()) {
            ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(entry.getValue());
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                sendPacket(viewer, destroyPacket);
            }
        }

        playerToEntityId.clear();
        viewerKnownEntities.clear();
    }

    // ==================== Data Classes ====================

    private record DisplayData(String className, int level, Location location) {}
}
