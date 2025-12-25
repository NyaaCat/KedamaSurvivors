package cat.nyaa.survivors.util;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;

/**
 * Async-safe line-of-sight checker using ChunkSnapshot.
 * Performs 3D Bresenham line traversal to check if path is blocked.
 *
 * <p>Usage:
 * <ol>
 *   <li>Create checker on main thread via {@link #createForRadius(Location, double)}</li>
 *   <li>Use {@link #hasLineOfSight(Location, Location)} from any thread</li>
 * </ol>
 */
public class LineOfSightChecker {

    private final Map<ChunkKey, ChunkSnapshot> snapshots;
    private final int minHeight;
    private final int maxHeight;

    private record ChunkKey(int x, int z) {}

    private LineOfSightChecker(Map<ChunkKey, ChunkSnapshot> snapshots, int minHeight, int maxHeight) {
        this.snapshots = snapshots;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    /**
     * Creates a checker with snapshots for all chunks within radius of center.
     * Must be called from main thread.
     *
     * @param center The center location
     * @param radius The radius in blocks to capture chunks for
     * @return A new LineOfSightChecker instance
     */
    public static LineOfSightChecker createForRadius(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location must have a valid world");
        }

        Map<ChunkKey, ChunkSnapshot> snapshots = new HashMap<>();

        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radiusBlocks = (int) Math.ceil(radius);

        int minChunkX = (centerX - radiusBlocks) >> 4;
        int maxChunkX = (centerX + radiusBlocks) >> 4;
        int minChunkZ = (centerZ - radiusBlocks) >> 4;
        int maxChunkZ = (centerZ + radiusBlocks) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    ChunkSnapshot snapshot = world.getChunkAt(cx, cz).getChunkSnapshot();
                    snapshots.put(new ChunkKey(cx, cz), snapshot);
                }
            }
        }

        return new LineOfSightChecker(snapshots, world.getMinHeight(), world.getMaxHeight());
    }

    /**
     * Checks if there's a clear line-of-sight between two points.
     * Safe to call from async thread.
     *
     * @param from The starting location
     * @param to The target location
     * @return true if there's a clear line-of-sight, false if blocked
     */
    public boolean hasLineOfSight(Location from, Location to) {
        int x0 = from.getBlockX(), y0 = from.getBlockY(), z0 = from.getBlockZ();
        int x1 = to.getBlockX(), y1 = to.getBlockY(), z1 = to.getBlockZ();

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);

        int max = Math.max(dx, Math.max(dy, dz));
        if (max == 0) return true;

        // Sample at intervals along the line using linear interpolation
        for (int i = 0; i <= max; i++) {
            int x = x0 + (x1 - x0) * i / max;
            int y = y0 + (y1 - y0) * i / max;
            int z = z0 + (z1 - z0) * i / max;

            if (isBlockOpaque(x, y, z)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the block at the given coordinates is opaque (blocks light/view).
     */
    private boolean isBlockOpaque(int x, int y, int z) {
        // Check world height bounds
        if (y < minHeight || y >= maxHeight) {
            return false;
        }

        ChunkKey key = new ChunkKey(x >> 4, z >> 4);
        ChunkSnapshot snapshot = snapshots.get(key);
        if (snapshot == null) {
            // Unloaded chunk - treat as blocked to be safe
            return true;
        }

        int localX = x & 15;
        int localZ = z & 15;

        BlockData blockData = snapshot.getBlockData(localX, y, localZ);
        return blockData.getMaterial().isOccluding();
    }

    /**
     * Returns the number of chunks captured in this checker.
     * Useful for debugging and testing.
     */
    public int getChunkCount() {
        return snapshots.size();
    }
}
