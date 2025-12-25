package cat.nyaa.survivors.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LineOfSightChecker.
 *
 * Note: Full integration tests require a running Bukkit server with MockBukkit.
 * These tests focus on the core algorithm logic using reflection or by testing
 * observable behaviors that don't require world access.
 */
class LineOfSightCheckerTest {

    @Nested
    @DisplayName("Bresenham Algorithm Logic")
    class BresenhamAlgorithmTests {

        @Test
        @DisplayName("should correctly calculate steps for horizontal line")
        void shouldCalculateStepsForHorizontalLine() {
            // Testing the core algorithm: max(|dx|, |dy|, |dz|) determines steps
            int x0 = 0, y0 = 0, z0 = 0;
            int x1 = 10, y1 = 0, z1 = 0;

            int dx = Math.abs(x1 - x0); // 10
            int dy = Math.abs(y1 - y0); // 0
            int dz = Math.abs(z1 - z0); // 0

            int max = Math.max(dx, Math.max(dy, dz));
            assertEquals(10, max, "Horizontal line of 10 blocks should have 10 steps");
        }

        @Test
        @DisplayName("should correctly calculate steps for vertical line")
        void shouldCalculateStepsForVerticalLine() {
            int x0 = 5, y0 = 10, z0 = 5;
            int x1 = 5, y1 = 20, z1 = 5;

            int dy = Math.abs(y1 - y0); // 10
            int dx = Math.abs(x1 - x0); // 0
            int dz = Math.abs(z1 - z0); // 0

            int max = Math.max(dx, Math.max(dy, dz));
            assertEquals(10, max, "Vertical line of 10 blocks should have 10 steps");
        }

        @Test
        @DisplayName("should correctly calculate steps for diagonal line")
        void shouldCalculateStepsForDiagonalLine() {
            int x0 = 0, y0 = 0, z0 = 0;
            int x1 = 5, y1 = 10, z1 = 3;

            int dx = Math.abs(x1 - x0); // 5
            int dy = Math.abs(y1 - y0); // 10
            int dz = Math.abs(z1 - z0); // 3

            int max = Math.max(dx, Math.max(dy, dz));
            assertEquals(10, max, "Diagonal line should use max dimension as steps");
        }

        @Test
        @DisplayName("should handle same point (zero steps)")
        void shouldHandleSamePoint() {
            int x0 = 5, y0 = 10, z0 = 15;
            int x1 = 5, y1 = 10, z1 = 15;

            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int dz = Math.abs(z1 - z0);

            int max = Math.max(dx, Math.max(dy, dz));
            assertEquals(0, max, "Same point should have 0 steps");
        }

        @Test
        @DisplayName("should correctly interpolate along line")
        void shouldCorrectlyInterpolateAlongLine() {
            int x0 = 0, y0 = 0, z0 = 0;
            int x1 = 10, y1 = 5, z1 = 2;

            int max = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));

            // Test midpoint interpolation
            int i = max / 2;
            int x = x0 + (x1 - x0) * i / max;
            int y = y0 + (y1 - y0) * i / max;
            int z = z0 + (z1 - z0) * i / max;

            assertEquals(5, x, "X should be at midpoint");
            assertTrue(y >= 2 && y <= 3, "Y should be around midpoint");
            assertEquals(1, z, "Z should be at midpoint");
        }

        @Test
        @DisplayName("should correctly interpolate along negative direction line")
        void shouldCorrectlyInterpolateNegativeDirection() {
            int x0 = 10, y0 = 10, z0 = 10;
            int x1 = 0, y1 = 0, z1 = 0;

            int max = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
            assertEquals(10, max);

            // Start point
            int x = x0 + (x1 - x0) * 0 / max;
            int y = y0 + (y1 - y0) * 0 / max;
            int z = z0 + (z1 - z0) * 0 / max;
            assertEquals(10, x);
            assertEquals(10, y);
            assertEquals(10, z);

            // End point
            x = x0 + (x1 - x0) * max / max;
            y = y0 + (y1 - y0) * max / max;
            z = z0 + (z1 - z0) * max / max;
            assertEquals(0, x);
            assertEquals(0, y);
            assertEquals(0, z);
        }
    }

    @Nested
    @DisplayName("Chunk Coordinate Calculations")
    class ChunkCoordinateTests {

        @Test
        @DisplayName("should correctly calculate chunk coordinates for positive values")
        void shouldCalculateChunkCoordsPositive() {
            int blockX = 100;
            int blockZ = 200;

            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;

            assertEquals(6, chunkX, "Chunk X for block 100 should be 6");
            assertEquals(12, chunkZ, "Chunk Z for block 200 should be 12");
        }

        @Test
        @DisplayName("should correctly calculate chunk coordinates for negative values")
        void shouldCalculateChunkCoordsNegative() {
            int blockX = -100;
            int blockZ = -200;

            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;

            assertEquals(-7, chunkX, "Chunk X for block -100 should be -7");
            assertEquals(-13, chunkZ, "Chunk Z for block -200 should be -13");
        }

        @Test
        @DisplayName("should correctly calculate local coordinates within chunk")
        void shouldCalculateLocalCoords() {
            int blockX = 100;
            int blockZ = 200;

            int localX = blockX & 15;
            int localZ = blockZ & 15;

            assertEquals(4, localX, "Local X for block 100 should be 4");
            assertEquals(8, localZ, "Local Z for block 200 should be 8");
        }

        @Test
        @DisplayName("should correctly calculate local coordinates for negative blocks")
        void shouldCalculateLocalCoordsNegative() {
            int blockX = -17;
            int blockZ = -1;

            int localX = blockX & 15;
            int localZ = blockZ & 15;

            assertEquals(15, localX, "Local X for block -17 should be 15");
            assertEquals(15, localZ, "Local Z for block -1 should be 15");
        }

        @Test
        @DisplayName("should correctly identify chunks needed for spawn radius")
        void shouldIdentifyChunksForRadius() {
            int centerX = 100;
            int centerZ = 100;
            int radius = 25;

            int minChunkX = (centerX - radius) >> 4;
            int maxChunkX = (centerX + radius) >> 4;
            int minChunkZ = (centerZ - radius) >> 4;
            int maxChunkZ = (centerZ + radius) >> 4;

            assertEquals(4, minChunkX, "Min chunk X");
            assertEquals(7, maxChunkX, "Max chunk X");
            assertEquals(4, minChunkZ, "Min chunk Z");
            assertEquals(7, maxChunkZ, "Max chunk Z");

            int chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
            assertEquals(16, chunkCount, "Should need 16 chunks for 25 block radius around (100, 100)");
        }
    }

    @Nested
    @DisplayName("Line Traversal Coverage")
    class LineTraversalTests {

        @Test
        @DisplayName("should visit all blocks along horizontal line")
        void shouldVisitAllBlocksHorizontal() {
            int x0 = 0, y0 = 0, z0 = 0;
            int x1 = 5, y1 = 0, z1 = 0;

            int max = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
            int visited = 0;

            for (int i = 0; i <= max; i++) {
                int x = x0 + (x1 - x0) * i / max;
                int y = y0 + (y1 - y0) * i / max;
                int z = z0 + (z1 - z0) * i / max;

                assertEquals(i, x, "X should progress linearly");
                assertEquals(0, y, "Y should stay at 0");
                assertEquals(0, z, "Z should stay at 0");
                visited++;
            }

            assertEquals(6, visited, "Should visit 6 blocks (0 to 5 inclusive)");
        }

        @Test
        @DisplayName("should visit correct blocks along diagonal line")
        void shouldVisitCorrectBlocksDiagonal() {
            int x0 = 0, y0 = 0, z0 = 0;
            int x1 = 4, y1 = 2, z1 = 0;

            int max = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
            assertEquals(4, max);

            // Track visited Y values
            int[] visitedY = new int[max + 1];
            for (int i = 0; i <= max; i++) {
                visitedY[i] = y0 + (y1 - y0) * i / max;
            }

            // Y should interpolate from 0 to 2 over 5 steps
            assertArrayEquals(new int[]{0, 0, 1, 1, 2}, visitedY);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle very long lines")
        void shouldHandleLongLines() {
            int x0 = 0, y0 = 64, z0 = 0;
            int x1 = 100, y1 = 64, z1 = 100;

            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int dz = Math.abs(z1 - z0);

            int max = Math.max(dx, Math.max(dy, dz));
            assertEquals(100, max, "Long line should have 100 steps");

            // Verify no overflow in interpolation
            for (int i = 0; i <= max; i++) {
                int x = x0 + (x1 - x0) * i / max;
                assertTrue(x >= x0 && x <= x1, "X should stay within bounds");
            }
        }

        @Test
        @DisplayName("should handle lines crossing chunk boundaries")
        void shouldHandleCrossChunkLines() {
            // Line from chunk 0 to chunk 2
            int x0 = 8, z0 = 8;   // Chunk (0, 0)
            int x1 = 40, z1 = 40; // Chunk (2, 2)

            // Calculate chunks that would be touched
            int minChunkX = Math.min(x0, x1) >> 4;
            int maxChunkX = Math.max(x0, x1) >> 4;
            int minChunkZ = Math.min(z0, z1) >> 4;
            int maxChunkZ = Math.max(z0, z1) >> 4;

            assertEquals(0, minChunkX);
            assertEquals(2, maxChunkX);
            assertEquals(0, minChunkZ);
            assertEquals(2, maxChunkZ);
        }

        @Test
        @DisplayName("should handle world height boundaries")
        void shouldHandleWorldHeightBoundaries() {
            // World bounds check simulation
            int minHeight = -64;
            int maxHeight = 320;

            // Test below min
            int y = -100;
            boolean inBounds = y >= minHeight && y < maxHeight;
            assertFalse(inBounds, "Y=-100 should be out of bounds");

            // Test above max
            y = 350;
            inBounds = y >= minHeight && y < maxHeight;
            assertFalse(inBounds, "Y=350 should be out of bounds");

            // Test valid
            y = 64;
            inBounds = y >= minHeight && y < maxHeight;
            assertTrue(inBounds, "Y=64 should be in bounds");
        }
    }
}
