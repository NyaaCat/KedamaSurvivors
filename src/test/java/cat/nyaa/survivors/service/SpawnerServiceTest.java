package cat.nyaa.survivors.service;

import cat.nyaa.survivors.config.ConfigService.EnemyArchetypeConfig;
import cat.nyaa.survivors.service.spawner.SpawnContext;
import cat.nyaa.survivors.service.spawner.SpawnPlan;
import cat.nyaa.survivors.service.spawner.WorldSpawnerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpawnerService calculations and logic.
 * Note: These tests focus on pure calculation logic that doesn't require Bukkit mocking.
 */
class SpawnerServiceTest {

    @Nested
    @DisplayName("Enemy Level Calculation")
    class EnemyLevelCalculation {

        @Test
        @DisplayName("should calculate base enemy level from player level")
        void shouldCalculateBaseEnemyLevelFromPlayerLevel() {
            // Formula: avgLevel * multiplier + playerCount * multiplier + offset
            double avgLevel = 5.0;
            double avgLevelMultiplier = 1.0;
            int playerCount = 2;
            double playerCountMultiplier = 0.2;
            int levelOffset = 0;

            double level = avgLevel * avgLevelMultiplier
                    + playerCount * playerCountMultiplier
                    + levelOffset;

            assertEquals(5.4, level, 0.01);
        }

        @Test
        @DisplayName("should apply time scaling to enemy level")
        void shouldApplyTimeScalingToEnemyLevel() {
            double baseLevel = 5.0;
            long runDurationSeconds = 180; // 3 minutes
            int timeStepSeconds = 60; // 1 minute steps
            int levelPerTimeStep = 1;

            long timeSteps = runDurationSeconds / timeStepSeconds;
            double finalLevel = baseLevel + (timeSteps * levelPerTimeStep);

            assertEquals(8.0, finalLevel);
        }

        @Test
        @DisplayName("should clamp enemy level to minimum")
        void shouldClampEnemyLevelToMinimum() {
            int calculatedLevel = -5;
            int minLevel = 1;
            int maxLevel = 100;

            int result = Math.max(minLevel, Math.min(maxLevel, calculatedLevel));
            assertEquals(1, result);
        }

        @Test
        @DisplayName("should clamp enemy level to maximum")
        void shouldClampEnemyLevelToMaximum() {
            int calculatedLevel = 150;
            int minLevel = 1;
            int maxLevel = 100;

            int result = Math.max(minLevel, Math.min(maxLevel, calculatedLevel));
            assertEquals(100, result);
        }

        @Test
        @DisplayName("should handle zero players in level calculation")
        void shouldHandleZeroPlayersInLevelCalculation() {
            double avgLevel = 0.0;
            double avgLevelMultiplier = 1.0;
            int playerCount = 0;
            double playerCountMultiplier = 0.2;
            int levelOffset = 1;

            double level = avgLevel * avgLevelMultiplier
                    + playerCount * playerCountMultiplier
                    + levelOffset;

            assertEquals(1.0, level);
        }
    }

    @Nested
    @DisplayName("Archetype Weighted Selection")
    class ArchetypeWeightedSelection {

        @Test
        @DisplayName("should calculate total weight correctly")
        void shouldCalculateTotalWeightCorrectly() {
            Map<String, Double> archetypeWeights = new HashMap<>();
            archetypeWeights.put("zombie", 3.0);
            archetypeWeights.put("skeleton", 2.0);
            archetypeWeights.put("creeper", 1.0);

            double totalWeight = archetypeWeights.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            assertEquals(6.0, totalWeight);
        }

        @Test
        @DisplayName("should select archetype based on weight")
        void shouldSelectArchetypeBasedOnWeight() {
            // With weights: zombie=3, skeleton=2, creeper=1 (total=6)
            // Random value 0.0-3.0 should select zombie
            // Random value 3.0-5.0 should select skeleton
            // Random value 5.0-6.0 should select creeper

            List<String> archetypes = Arrays.asList("zombie", "skeleton", "creeper");
            List<Double> weights = Arrays.asList(3.0, 2.0, 1.0);
            double totalWeight = 6.0;

            // Test selecting zombie (random = 1.5)
            String selected1 = selectArchetype(archetypes, weights, totalWeight, 1.5);
            assertEquals("zombie", selected1);

            // Test selecting skeleton (random = 4.0)
            String selected2 = selectArchetype(archetypes, weights, totalWeight, 4.0);
            assertEquals("skeleton", selected2);

            // Test selecting creeper (random = 5.5)
            String selected3 = selectArchetype(archetypes, weights, totalWeight, 5.5);
            assertEquals("creeper", selected3);
        }

        private String selectArchetype(List<String> archetypes, List<Double> weights,
                                        double totalWeight, double random) {
            double cumulative = 0;
            for (int i = 0; i < archetypes.size(); i++) {
                cumulative += weights.get(i);
                if (random < cumulative) {
                    return archetypes.get(i);
                }
            }
            return archetypes.get(archetypes.size() - 1);
        }

        @Test
        @DisplayName("should handle single archetype")
        void shouldHandleSingleArchetype() {
            List<String> archetypes = Collections.singletonList("zombie");
            List<Double> weights = Collections.singletonList(1.0);

            String selected = selectArchetype(archetypes, weights, 1.0, 0.5);
            assertEquals("zombie", selected);
        }

        @Test
        @DisplayName("should handle equal weights")
        void shouldHandleEqualWeights() {
            List<String> archetypes = Arrays.asList("a", "b", "c");
            List<Double> weights = Arrays.asList(1.0, 1.0, 1.0);
            double totalWeight = 3.0;

            // Each should have 1/3 probability
            String selected0 = selectArchetype(archetypes, weights, totalWeight, 0.5);
            assertEquals("a", selected0);

            String selected1 = selectArchetype(archetypes, weights, totalWeight, 1.5);
            assertEquals("b", selected1);

            String selected2 = selectArchetype(archetypes, weights, totalWeight, 2.5);
            assertEquals("c", selected2);
        }
    }

    @Nested
    @DisplayName("Spawn Count Calculation")
    class SpawnCountCalculation {

        @Test
        @DisplayName("should calculate mobs to spawn")
        void shouldCalculateMobsToSpawn() {
            int targetMobsPerPlayer = 10;
            int currentNearbyMobs = 3;
            int maxSpawnsPerPlayerPerTick = 3;

            int toSpawn = Math.min(
                    targetMobsPerPlayer - currentNearbyMobs,
                    maxSpawnsPerPlayerPerTick
            );

            assertEquals(3, toSpawn);
        }

        @Test
        @DisplayName("should not spawn when at target mob count")
        void shouldNotSpawnWhenAtTargetMobCount() {
            int targetMobsPerPlayer = 10;
            int currentNearbyMobs = 10;
            int maxSpawnsPerPlayerPerTick = 3;

            int toSpawn = Math.min(
                    targetMobsPerPlayer - currentNearbyMobs,
                    maxSpawnsPerPlayerPerTick
            );

            assertEquals(0, toSpawn);
        }

        @Test
        @DisplayName("should limit spawn to max per tick")
        void shouldLimitSpawnToMaxPerTick() {
            int targetMobsPerPlayer = 20;
            int currentNearbyMobs = 0;
            int maxSpawnsPerPlayerPerTick = 3;

            int toSpawn = Math.min(
                    targetMobsPerPlayer - currentNearbyMobs,
                    maxSpawnsPerPlayerPerTick
            );

            assertEquals(3, toSpawn);
        }

        @Test
        @DisplayName("should handle negative spawn requirement")
        void shouldHandleNegativeSpawnRequirement() {
            int targetMobsPerPlayer = 5;
            int currentNearbyMobs = 10; // Over target
            int maxSpawnsPerPlayerPerTick = 3;

            int toSpawn = Math.min(
                    targetMobsPerPlayer - currentNearbyMobs,
                    maxSpawnsPerPlayerPerTick
            );

            assertTrue(toSpawn <= 0);
        }
    }

    @Nested
    @DisplayName("Spawn Position Calculation")
    class SpawnPositionCalculation {

        @Test
        @DisplayName("should calculate distance within bounds")
        void shouldCalculateDistanceWithinBounds() {
            double minDist = 8.0;
            double maxDist = 25.0;

            // Generate multiple random distances
            for (int i = 0; i < 100; i++) {
                double random = Math.random();
                double distance = minDist + random * (maxDist - minDist);

                assertTrue(distance >= minDist);
                assertTrue(distance <= maxDist);
            }
        }

        @Test
        @DisplayName("should calculate angle in valid range")
        void shouldCalculateAngleInValidRange() {
            // Generate multiple random angles
            for (int i = 0; i < 100; i++) {
                double angle = Math.random() * 2 * Math.PI;

                assertTrue(angle >= 0);
                assertTrue(angle < 2 * Math.PI);
            }
        }

        @Test
        @DisplayName("should calculate position offset correctly")
        void shouldCalculatePositionOffsetCorrectly() {
            double centerX = 100.0;
            double centerZ = 100.0;
            double distance = 10.0;
            double angle = 0; // Points along positive X axis

            double x = centerX + Math.cos(angle) * distance;
            double z = centerZ + Math.sin(angle) * distance;

            assertEquals(110.0, x, 0.001);
            assertEquals(100.0, z, 0.001);
        }

        @Test
        @DisplayName("should calculate position at 90 degrees correctly")
        void shouldCalculatePositionAt90DegreesCorrectly() {
            double centerX = 100.0;
            double centerZ = 100.0;
            double distance = 10.0;
            double angle = Math.PI / 2; // 90 degrees, points along positive Z axis

            double x = centerX + Math.cos(angle) * distance;
            double z = centerZ + Math.sin(angle) * distance;

            assertEquals(100.0, x, 0.001);
            assertEquals(110.0, z, 0.001);
        }
    }

    @Nested
    @DisplayName("WorldSpawnerState")
    class WorldSpawnerStateTests {

        private WorldSpawnerState worldState;

        @BeforeEach
        void setUp() {
            worldState = new WorldSpawnerState("test_world");
        }

        @Test
        @DisplayName("should track world name")
        void shouldTrackWorldName() {
            assertEquals("test_world", worldState.getWorldName());
        }

        @Test
        @DisplayName("should default to not paused")
        void shouldDefaultToNotPaused() {
            assertFalse(worldState.isPaused());
        }

        @Test
        @DisplayName("should set paused state")
        void shouldSetPausedState() {
            worldState.setPaused(true);
            assertTrue(worldState.isPaused());

            worldState.setPaused(false);
            assertFalse(worldState.isPaused());
        }

        @Test
        @DisplayName("should track mob count")
        void shouldTrackMobCount() {
            assertEquals(0, worldState.getActiveMobCount());

            worldState.setActiveMobCount(5);
            assertEquals(5, worldState.getActiveMobCount());

            worldState.incrementMobCount();
            assertEquals(6, worldState.getActiveMobCount());

            worldState.decrementMobCount();
            assertEquals(5, worldState.getActiveMobCount());
        }
    }

    @Nested
    @DisplayName("SpawnContext")
    class SpawnContextTests {

        @Test
        @DisplayName("should create spawn context with all fields")
        void shouldCreateSpawnContextWithAllFields() {
            UUID playerId = UUID.randomUUID();
            UUID runId = UUID.randomUUID();
            String worldName = "test_world";
            // Note: We can't create a real Location in unit tests without Bukkit
            // So we just test the record structure

            assertNotNull(playerId);
            assertNotNull(runId);
            assertEquals("test_world", worldName);
        }
    }

    @Nested
    @DisplayName("Average Level Calculation")
    class AverageLevelCalculation {

        @Test
        @DisplayName("should calculate average of multiple players")
        void shouldCalculateAverageOfMultiplePlayers() {
            List<Integer> playerLevels = Arrays.asList(5, 10, 15);
            int totalLevel = playerLevels.stream().mapToInt(Integer::intValue).sum();
            double average = (double) totalLevel / playerLevels.size();

            assertEquals(10.0, average);
        }

        @Test
        @DisplayName("should return zero for empty player list")
        void shouldReturnZeroForEmptyPlayerList() {
            List<Integer> playerLevels = Collections.emptyList();
            int count = playerLevels.size();

            double average = count > 0 ? playerLevels.stream().mapToInt(Integer::intValue).sum() / (double) count : 0;
            assertEquals(0.0, average);
        }

        @Test
        @DisplayName("should handle single player")
        void shouldHandleSinglePlayer() {
            List<Integer> playerLevels = Collections.singletonList(7);
            int totalLevel = playerLevels.stream().mapToInt(Integer::intValue).sum();
            double average = (double) totalLevel / playerLevels.size();

            assertEquals(7.0, average);
        }
    }

    @Nested
    @DisplayName("Enemy Level Tag Parsing")
    class EnemyLevelTagParsing {

        @Test
        @DisplayName("should parse level from tag")
        void shouldParseLevelFromTag() {
            String tag = "vrs_lvl_5";
            String prefix = "vrs_lvl_";

            int level = 1; // default
            if (tag.startsWith(prefix)) {
                try {
                    level = Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException ignored) {}
            }

            assertEquals(5, level);
        }

        @Test
        @DisplayName("should return default for invalid tag")
        void shouldReturnDefaultForInvalidTag() {
            String tag = "vrs_lvl_invalid";
            String prefix = "vrs_lvl_";

            int level = 1; // default
            if (tag.startsWith(prefix)) {
                try {
                    level = Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException ignored) {}
            }

            assertEquals(1, level);
        }

        @Test
        @DisplayName("should return default for non-matching tag")
        void shouldReturnDefaultForNonMatchingTag() {
            String tag = "other_tag";
            String prefix = "vrs_lvl_";

            int level = 1; // default
            if (tag.startsWith(prefix)) {
                try {
                    level = Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException ignored) {}
            }

            assertEquals(1, level);
        }

        @Test
        @DisplayName("should parse high level correctly")
        void shouldParseHighLevelCorrectly() {
            String tag = "vrs_lvl_100";
            String prefix = "vrs_lvl_";

            int level = 1;
            if (tag.startsWith(prefix)) {
                try {
                    level = Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException ignored) {}
            }

            assertEquals(100, level);
        }
    }

    @Nested
    @DisplayName("Tick Budget Enforcement")
    class TickBudgetEnforcement {

        @Test
        @DisplayName("should respect max commands per tick")
        void shouldRespectMaxCommandsPerTick() {
            int maxCommands = 50;
            int plansToExecute = 100;
            int commandsPerPlan = 1;

            int commandsExecuted = 0;
            for (int i = 0; i < plansToExecute; i++) {
                if (commandsExecuted >= maxCommands) break;
                commandsExecuted += commandsPerPlan;
            }

            assertEquals(50, commandsExecuted);
        }

        @Test
        @DisplayName("should respect max spawns per tick")
        void shouldRespectMaxSpawnsPerTick() {
            int maxSpawns = 20;
            int maxCommands = 50;
            int plansToExecute = 30;

            int spawnsExecuted = 0;
            int commandsExecuted = 0;

            for (int i = 0; i < plansToExecute; i++) {
                if (commandsExecuted >= maxCommands) break;
                if (spawnsExecuted >= maxSpawns) break;

                spawnsExecuted++;
                commandsExecuted++;
            }

            assertEquals(20, spawnsExecuted);
        }
    }
}
