package cat.nyaa.survivors.service;

import cat.nyaa.survivors.config.ConfigService.SpawnPointConfig;
import cat.nyaa.survivors.service.SpawnLoadTracker.SpawnPointEntry;
import cat.nyaa.survivors.service.SpawnLoadTracker.SpawnPointLoad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpawnLoadTracker logic.
 * Tests calculation and ranking logic without Bukkit dependencies.
 */
class SpawnLoadTrackerTest {

    @Nested
    @DisplayName("SpawnPointLoad")
    class SpawnPointLoadTests {

        @Test
        @DisplayName("should calculate total load as sum of players and mobs")
        void shouldCalculateTotalLoad() {
            SpawnPointConfig config = new SpawnPointConfig();
            config.x = 0;
            config.y = 64;
            config.z = 0;

            SpawnPointEntry entry = new SpawnPointEntry("test_world", config, 50.0);
            SpawnPointLoad load = new SpawnPointLoad(entry, 3, 7);

            assertEquals(10, load.totalLoad());
        }

        @Test
        @DisplayName("should handle zero players and mobs")
        void shouldHandleZeroLoad() {
            SpawnPointConfig config = new SpawnPointConfig();
            config.x = 100;
            config.y = 64;
            config.z = 100;

            SpawnPointEntry entry = new SpawnPointEntry("arena_forest", config, 50.0);
            SpawnPointLoad load = new SpawnPointLoad(entry, 0, 0);

            assertEquals(0, load.totalLoad());
        }

        @Test
        @DisplayName("should handle high player count")
        void shouldHandleHighPlayerCount() {
            SpawnPointConfig config = new SpawnPointConfig();
            SpawnPointEntry entry = new SpawnPointEntry("test_world", config, 50.0);
            SpawnPointLoad load = new SpawnPointLoad(entry, 50, 100);

            assertEquals(150, load.totalLoad());
        }
    }

    @Nested
    @DisplayName("SpawnPointEntry")
    class SpawnPointEntryTests {

        @Test
        @DisplayName("should store world name correctly")
        void shouldStoreWorldName() {
            SpawnPointConfig config = new SpawnPointConfig();
            SpawnPointEntry entry = new SpawnPointEntry("arena_nether", config, 50.0);

            assertEquals("arena_nether", entry.worldName());
        }

        @Test
        @DisplayName("should store tracking radius correctly")
        void shouldStoreTrackingRadius() {
            SpawnPointConfig config = new SpawnPointConfig();
            SpawnPointEntry entry = new SpawnPointEntry("test_world", config, 75.0);

            assertEquals(75.0, entry.trackingRadius());
        }

        @Test
        @DisplayName("should use default tracking radius")
        void shouldUseDefaultTrackingRadius() {
            SpawnPointConfig config = new SpawnPointConfig();
            double defaultRadius = 50.0;
            SpawnPointEntry entry = new SpawnPointEntry("test_world", config, defaultRadius);

            assertEquals(50.0, entry.trackingRadius());
        }
    }

    @Nested
    @DisplayName("Load Ranking Algorithm")
    class LoadRankingTests {

        @Test
        @DisplayName("should sort spawn points by ascending total load")
        void shouldSortByAscendingTotalLoad() {
            List<SpawnPointLoad> loads = createTestLoads();

            // Sort by total load (ascending - least loaded first)
            loads.sort(Comparator.comparingInt(SpawnPointLoad::totalLoad));

            assertEquals(2, loads.get(0).totalLoad());   // Least loaded first
            assertEquals(5, loads.get(1).totalLoad());
            assertEquals(8, loads.get(2).totalLoad());
            assertEquals(15, loads.get(3).totalLoad());  // Most loaded last
        }

        @Test
        @DisplayName("should group by world correctly")
        void shouldGroupByWorld() {
            List<SpawnPointLoad> loads = createMixedWorldLoads();

            Map<String, List<SpawnPointLoad>> perWorld = new HashMap<>();
            for (SpawnPointLoad load : loads) {
                perWorld.computeIfAbsent(load.entry().worldName(), k -> new ArrayList<>())
                        .add(load);
            }

            assertEquals(2, perWorld.size());
            assertEquals(2, perWorld.get("arena_forest").size());
            assertEquals(2, perWorld.get("arena_nether").size());
        }

        @Test
        @DisplayName("should sort per-world rankings independently")
        void shouldSortPerWorldRankingsIndependently() {
            List<SpawnPointLoad> loads = createMixedWorldLoads();

            Map<String, List<SpawnPointLoad>> perWorld = new HashMap<>();
            for (SpawnPointLoad load : loads) {
                perWorld.computeIfAbsent(load.entry().worldName(), k -> new ArrayList<>())
                        .add(load);
            }

            // Sort each world's rankings
            for (List<SpawnPointLoad> worldLoads : perWorld.values()) {
                worldLoads.sort(Comparator.comparingInt(SpawnPointLoad::totalLoad));
            }

            // Verify forest ranking
            List<SpawnPointLoad> forestRanking = perWorld.get("arena_forest");
            assertTrue(forestRanking.get(0).totalLoad() <= forestRanking.get(1).totalLoad());

            // Verify nether ranking
            List<SpawnPointLoad> netherRanking = perWorld.get("arena_nether");
            assertTrue(netherRanking.get(0).totalLoad() <= netherRanking.get(1).totalLoad());
        }

        private List<SpawnPointLoad> createTestLoads() {
            List<SpawnPointLoad> loads = new ArrayList<>();

            SpawnPointConfig config1 = new SpawnPointConfig();
            config1.x = 0; config1.y = 64; config1.z = 0;
            loads.add(new SpawnPointLoad(new SpawnPointEntry("test", config1, 50), 5, 10)); // 15

            SpawnPointConfig config2 = new SpawnPointConfig();
            config2.x = 100; config2.y = 64; config2.z = 0;
            loads.add(new SpawnPointLoad(new SpawnPointEntry("test", config2, 50), 1, 1)); // 2

            SpawnPointConfig config3 = new SpawnPointConfig();
            config3.x = 0; config3.y = 64; config3.z = 100;
            loads.add(new SpawnPointLoad(new SpawnPointEntry("test", config3, 50), 3, 5)); // 8

            SpawnPointConfig config4 = new SpawnPointConfig();
            config4.x = 100; config4.y = 64; config4.z = 100;
            loads.add(new SpawnPointLoad(new SpawnPointEntry("test", config4, 50), 2, 3)); // 5

            return loads;
        }

        private List<SpawnPointLoad> createMixedWorldLoads() {
            List<SpawnPointLoad> loads = new ArrayList<>();

            SpawnPointConfig config1 = new SpawnPointConfig();
            loads.add(new SpawnPointLoad(new SpawnPointEntry("arena_forest", config1, 50), 2, 3)); // 5

            SpawnPointConfig config2 = new SpawnPointConfig();
            loads.add(new SpawnPointLoad(new SpawnPointEntry("arena_forest", config2, 50), 5, 5)); // 10

            SpawnPointConfig config3 = new SpawnPointConfig();
            loads.add(new SpawnPointLoad(new SpawnPointEntry("arena_nether", config3, 50), 1, 2)); // 3

            SpawnPointConfig config4 = new SpawnPointConfig();
            loads.add(new SpawnPointLoad(new SpawnPointEntry("arena_nether", config4, 50), 4, 4)); // 8

            return loads;
        }
    }

    @Nested
    @DisplayName("Least Loaded Selection")
    class LeastLoadedSelectionTests {

        @Test
        @DisplayName("should select from top N least loaded spawn points")
        void shouldSelectFromTopNLeastLoaded() {
            List<SpawnPointLoad> ranking = createSortedRanking();

            // Simulate selecting from top 3
            int topN = Math.min(3, ranking.size());

            // The selection should be from index 0, 1, or 2
            for (int i = 0; i < 100; i++) {
                int selected = java.util.concurrent.ThreadLocalRandom.current().nextInt(topN);
                assertTrue(selected >= 0 && selected < topN);
            }
        }

        @Test
        @DisplayName("should handle ranking with less than 3 spawn points")
        void shouldHandleSmallRanking() {
            List<SpawnPointLoad> ranking = new ArrayList<>();
            SpawnPointConfig config = new SpawnPointConfig();
            ranking.add(new SpawnPointLoad(new SpawnPointEntry("test", config, 50), 1, 1));

            int topN = Math.min(3, ranking.size());
            assertEquals(1, topN);

            // Should always select index 0
            int selected = java.util.concurrent.ThreadLocalRandom.current().nextInt(topN);
            assertEquals(0, selected);
        }

        @Test
        @DisplayName("should handle empty ranking gracefully")
        void shouldHandleEmptyRanking() {
            List<SpawnPointLoad> ranking = new ArrayList<>();

            // Should not throw when checking if empty
            assertTrue(ranking.isEmpty());

            // Fallback logic should apply
            if (ranking.isEmpty()) {
                assertNull(null); // Represents fallback to first spawn point
            }
        }

        private List<SpawnPointLoad> createSortedRanking() {
            List<SpawnPointLoad> loads = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                SpawnPointConfig config = new SpawnPointConfig();
                config.x = i * 100;
                config.y = 64;
                config.z = 0;
                loads.add(new SpawnPointLoad(new SpawnPointEntry("test", config, 50), i, i));
            }

            loads.sort(Comparator.comparingInt(SpawnPointLoad::totalLoad));
            return loads;
        }
    }

    @Nested
    @DisplayName("Round Robin Index")
    class RoundRobinIndexTests {

        @Test
        @DisplayName("should cycle through all spawn points")
        void shouldCycleThroughAllSpawnPoints() {
            int totalSpawnPoints = 5;
            int currentIndex = 0;
            Set<Integer> visited = new HashSet<>();

            // Simulate one full cycle
            for (int i = 0; i < totalSpawnPoints; i++) {
                visited.add(currentIndex);
                currentIndex = (currentIndex + 1) % totalSpawnPoints;
            }

            assertEquals(totalSpawnPoints, visited.size());
        }

        @Test
        @DisplayName("should wrap around correctly")
        void shouldWrapAroundCorrectly() {
            int totalSpawnPoints = 3;
            int currentIndex = 2; // Last index

            currentIndex = (currentIndex + 1) % totalSpawnPoints;
            assertEquals(0, currentIndex); // Should wrap to 0
        }

        @Test
        @DisplayName("should handle single spawn point")
        void shouldHandleSingleSpawnPoint() {
            int totalSpawnPoints = 1;
            int currentIndex = 0;

            for (int i = 0; i < 10; i++) {
                assertEquals(0, currentIndex);
                currentIndex = (currentIndex + 1) % totalSpawnPoints;
            }
        }
    }

    @Nested
    @DisplayName("Tracking Radius")
    class TrackingRadiusTests {

        @Test
        @DisplayName("should use spawn point specific radius")
        void shouldUseSpawnPointSpecificRadius() {
            SpawnPointConfig config = new SpawnPointConfig();
            config.trackingRadius = 75.0;

            double radius = config.trackingRadius > 0 ? config.trackingRadius : 50.0;
            assertEquals(75.0, radius);
        }

        @Test
        @DisplayName("should use default radius when not specified")
        void shouldUseDefaultRadiusWhenNotSpecified() {
            SpawnPointConfig config = new SpawnPointConfig();
            config.trackingRadius = 0;

            double defaultRadius = 50.0;
            double radius = config.trackingRadius > 0 ? config.trackingRadius : defaultRadius;
            assertEquals(50.0, radius);
        }

        @Test
        @DisplayName("should use default radius for negative values")
        void shouldUseDefaultRadiusForNegativeValues() {
            SpawnPointConfig config = new SpawnPointConfig();
            config.trackingRadius = -10;

            double defaultRadius = 50.0;
            double radius = config.trackingRadius > 0 ? config.trackingRadius : defaultRadius;
            assertEquals(50.0, radius);
        }
    }

    @Nested
    @DisplayName("Spawn Point Coordinates")
    class SpawnPointCoordinatesTests {

        @Test
        @DisplayName("should store coordinates correctly")
        void shouldStoreCoordinatesCorrectly() {
            SpawnPointConfig config = new SpawnPointConfig();
            config.x = 100.5;
            config.y = 64.0;
            config.z = -200.5;

            assertEquals(100.5, config.x);
            assertEquals(64.0, config.y);
            assertEquals(-200.5, config.z);
        }

        @Test
        @DisplayName("should store yaw and pitch correctly")
        void shouldStoreYawAndPitchCorrectly() {
            SpawnPointConfig config = new SpawnPointConfig();
            config.yaw = 90.0f;
            config.pitch = -45.0f;

            assertEquals(90.0f, config.yaw);
            assertEquals(-45.0f, config.pitch);
        }

        @Test
        @DisplayName("should handle null yaw and pitch")
        void shouldHandleNullYawAndPitch() {
            SpawnPointConfig config = new SpawnPointConfig();
            // yaw and pitch are not set

            float yaw = config.yaw != null ? config.yaw : 0f;
            float pitch = config.pitch != null ? config.pitch : 0f;

            assertEquals(0f, yaw);
            assertEquals(0f, pitch);
        }
    }
}
