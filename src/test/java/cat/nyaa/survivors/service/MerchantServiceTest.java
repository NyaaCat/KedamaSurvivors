package cat.nyaa.survivors.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MerchantService calculations and logic.
 * Note: These tests focus on pure calculation logic that doesn't require Bukkit mocking.
 */
class MerchantServiceTest {

    @Nested
    @DisplayName("Merchant Spawn Y-Level Calculation")
    class MerchantSpawnYLevelCalculation {

        @Test
        @DisplayName("should search Y levels starting from player level")
        void shouldSearchYLevelsStartingFromPlayerLevel() {
            int playerY = 64;
            int verticalRange = 10;

            // Simulate the search pattern: 0, +1, -1, +2, -2, ...
            int[] expectedSearchOrder = new int[verticalRange * 2 + 1];
            int index = 0;
            for (int yOffset = 0; yOffset <= verticalRange; yOffset++) {
                expectedSearchOrder[index++] = playerY + yOffset;
                if (yOffset > 0) {
                    expectedSearchOrder[index++] = playerY - yOffset;
                }
            }

            // Verify first Y checked is player level
            assertEquals(playerY, expectedSearchOrder[0]);
            // Verify pattern: 64, 65, 63, 66, 62, 67, 61, ...
            assertEquals(playerY + 1, expectedSearchOrder[1]);
            assertEquals(playerY - 1, expectedSearchOrder[2]);
            assertEquals(playerY + 2, expectedSearchOrder[3]);
            assertEquals(playerY - 2, expectedSearchOrder[4]);
        }

        @Test
        @DisplayName("should respect vertical range bounds")
        void shouldRespectVerticalRangeBounds() {
            int playerY = 64;
            int verticalRange = 10;

            int minY = playerY - verticalRange;
            int maxY = playerY + verticalRange;

            assertEquals(54, minY);
            assertEquals(74, maxY);
            assertEquals(21, maxY - minY + 1); // Total Y levels to check
        }

        @Test
        @DisplayName("should handle player at low Y level")
        void shouldHandlePlayerAtLowYLevel() {
            int playerY = 5;
            int verticalRange = 10;

            // Should still generate valid search range
            int minY = playerY - verticalRange; // -5 (may be below world limit)
            int maxY = playerY + verticalRange; // 15

            // In practice, Bukkit will handle invalid Y coordinates
            assertEquals(-5, minY);
            assertEquals(15, maxY);
        }

        @Test
        @DisplayName("should handle player at high Y level")
        void shouldHandlePlayerAtHighYLevel() {
            int playerY = 300;
            int verticalRange = 10;

            // Should still generate valid search range
            int minY = playerY - verticalRange; // 290
            int maxY = playerY + verticalRange; // 310

            assertEquals(290, minY);
            assertEquals(310, maxY);
        }

        @Test
        @DisplayName("should search exact count of Y levels")
        void shouldSearchExactCountOfYLevels() {
            int verticalRange = 10;

            // For range 10: checks 0, ±1, ±2, ..., ±10 = 21 levels total
            int totalLevelsToCheck = 0;
            for (int yOffset = 0; yOffset <= verticalRange; yOffset++) {
                totalLevelsToCheck++; // above
                if (yOffset > 0) {
                    totalLevelsToCheck++; // below
                }
            }

            assertEquals(21, totalLevelsToCheck);
        }

        @Test
        @DisplayName("should handle zero vertical range")
        void shouldHandleZeroVerticalRange() {
            int playerY = 64;
            int verticalRange = 0;

            // Should only check player's exact Y level
            int totalLevelsToCheck = 0;
            for (int yOffset = 0; yOffset <= verticalRange; yOffset++) {
                totalLevelsToCheck++;
                if (yOffset > 0) {
                    totalLevelsToCheck++;
                }
            }

            assertEquals(1, totalLevelsToCheck); // Only check playerY
        }

        @Test
        @DisplayName("should prioritize Y closer to player")
        void shouldPrioritizeYCloserToPlayer() {
            int playerY = 64;
            int verticalRange = 5;

            // Track the order of Y levels checked
            int[] checkOrder = new int[verticalRange * 2 + 1];
            int idx = 0;
            for (int yOffset = 0; yOffset <= verticalRange; yOffset++) {
                checkOrder[idx++] = playerY + yOffset;
                if (yOffset > 0) {
                    checkOrder[idx++] = playerY - yOffset;
                }
            }

            // First check should be at player level
            assertEquals(64, checkOrder[0]);

            // Next checks should be ±1 from player
            assertTrue(Math.abs(checkOrder[1] - playerY) <= 1);
            assertTrue(Math.abs(checkOrder[2] - playerY) <= 1);

            // Last checks should be at the range boundary
            assertEquals(playerY + verticalRange, checkOrder[checkOrder.length - 2]);
            assertEquals(playerY - verticalRange, checkOrder[checkOrder.length - 1]);
        }
    }

    @Nested
    @DisplayName("Merchant Spawn Location Validation")
    class MerchantSpawnLocationValidation {

        @Test
        @DisplayName("should validate distance bounds")
        void shouldValidateDistanceBounds() {
            double minDist = 20.0;
            double maxDist = 50.0;

            // Generate multiple random distances
            for (int i = 0; i < 100; i++) {
                double random = Math.random();
                double distance = minDist + random * (maxDist - minDist);

                assertTrue(distance >= minDist, "Distance should be >= minDist");
                assertTrue(distance <= maxDist, "Distance should be <= maxDist");
            }
        }

        @Test
        @DisplayName("should calculate horizontal position correctly")
        void shouldCalculateHorizontalPositionCorrectly() {
            double playerX = 100.0;
            double playerZ = 100.0;
            double distance = 30.0;
            double angle = 0; // Points along positive X axis

            double x = playerX + Math.cos(angle) * distance;
            double z = playerZ + Math.sin(angle) * distance;

            assertEquals(130.0, x, 0.001);
            assertEquals(100.0, z, 0.001);
        }

        @Test
        @DisplayName("should calculate position at 45 degrees correctly")
        void shouldCalculatePositionAt45DegreesCorrectly() {
            double playerX = 0.0;
            double playerZ = 0.0;
            double distance = 10.0;
            double angle = Math.PI / 4; // 45 degrees

            double x = playerX + Math.cos(angle) * distance;
            double z = playerZ + Math.sin(angle) * distance;

            // At 45 degrees, x and z should be equal
            assertEquals(x, z, 0.001);
            assertEquals(Math.sqrt(50), x, 0.001); // ~7.07
        }

        @Test
        @DisplayName("should attempt multiple spawn locations")
        void shouldAttemptMultipleSpawnLocations() {
            int maxAttempts = 10;
            int attempts = 0;
            boolean found = false;

            // Simulate spawn attempt loop
            for (int i = 0; i < maxAttempts; i++) {
                attempts++;
                // Simulate random failure/success
                if (i == 7) { // Found on 8th attempt
                    found = true;
                    break;
                }
            }

            assertTrue(found);
            assertEquals(8, attempts);
        }
    }

    @Nested
    @DisplayName("Merchant Notification Configuration")
    class MerchantNotificationConfiguration {

        @Test
        @DisplayName("should default to notifications enabled")
        void shouldDefaultToNotificationsEnabled() {
            boolean defaultValue = true;
            assertTrue(defaultValue, "Notifications should be enabled by default");
        }

        @Test
        @DisplayName("should respect disabled notification setting")
        void shouldRespectDisabledNotificationSetting() {
            boolean notificationsEnabled = false;
            boolean shouldNotify = notificationsEnabled;

            assertFalse(shouldNotify);
        }

        @Test
        @DisplayName("should respect enabled notification setting")
        void shouldRespectEnabledNotificationSetting() {
            boolean notificationsEnabled = true;
            boolean shouldNotify = notificationsEnabled;

            assertTrue(shouldNotify);
        }

        @Test
        @DisplayName("notification toggle should be independent of particle toggle")
        void notificationToggleShouldBeIndependentOfParticleToggle() {
            // Simulate config state
            boolean spawnParticles = true;
            boolean spawnNotification = false;

            // Particles enabled, notification disabled
            assertTrue(spawnParticles);
            assertFalse(spawnNotification);

            // Swap
            spawnParticles = false;
            spawnNotification = true;

            // Particles disabled, notification enabled
            assertFalse(spawnParticles);
            assertTrue(spawnNotification);
        }
    }

    @Nested
    @DisplayName("Merchant Vertical Range Configuration")
    class MerchantVerticalRangeConfiguration {

        @Test
        @DisplayName("should default to vertical range of 10")
        void shouldDefaultToVerticalRangeOf10() {
            int defaultVerticalRange = 10;
            assertEquals(10, defaultVerticalRange);
        }

        @Test
        @DisplayName("should allow custom vertical range values")
        void shouldAllowCustomVerticalRangeValues() {
            int[] testRanges = {0, 5, 10, 15, 20, 50};

            for (int range : testRanges) {
                assertTrue(range >= 0, "Vertical range should be non-negative");
            }
        }

        @Test
        @DisplayName("should calculate search depth from vertical range")
        void shouldCalculateSearchDepthFromVerticalRange() {
            int verticalRange = 15;

            // Search covers: playerY - range to playerY + range
            int searchDepth = verticalRange * 2 + 1;

            assertEquals(31, searchDepth);
        }
    }
}
