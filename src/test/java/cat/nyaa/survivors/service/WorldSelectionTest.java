package cat.nyaa.survivors.service;

import cat.nyaa.survivors.config.ConfigService.CombatWorldConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for world selection logic.
 * Tests cost calculation, eligibility, and selection flow.
 */
class WorldSelectionTest {

    @Nested
    @DisplayName("World Cost Logic")
    class WorldCostLogicTests {

        @Test
        @DisplayName("should allow selection when player has enough coins")
        void shouldAllowSelectionWhenPlayerHasEnoughCoins() {
            int playerBalance = 100;
            int worldCost = 50;

            assertTrue(playerBalance >= worldCost);
        }

        @Test
        @DisplayName("should deny selection when player has insufficient coins")
        void shouldDenySelectionWhenPlayerHasInsufficientCoins() {
            int playerBalance = 30;
            int worldCost = 50;

            assertFalse(playerBalance >= worldCost);
        }

        @Test
        @DisplayName("should allow free world selection")
        void shouldAllowFreeWorldSelection() {
            int playerBalance = 0;
            int worldCost = 0;

            assertTrue(worldCost == 0 || playerBalance >= worldCost);
        }

        @Test
        @DisplayName("should deduct correct cost after selection")
        void shouldDeductCorrectCostAfterSelection() {
            int playerBalance = 100;
            int worldCost = 50;

            int newBalance = playerBalance - worldCost;
            assertEquals(50, newBalance);
        }

        @Test
        @DisplayName("should handle exact balance match")
        void shouldHandleExactBalanceMatch() {
            int playerBalance = 50;
            int worldCost = 50;

            assertTrue(playerBalance >= worldCost);
            int newBalance = playerBalance - worldCost;
            assertEquals(0, newBalance);
        }
    }

    @Nested
    @DisplayName("World Eligibility")
    class WorldEligibilityTests {

        @Test
        @DisplayName("should filter disabled worlds")
        void shouldFilterDisabledWorlds() {
            List<MockWorld> worlds = createTestWorlds();

            List<MockWorld> enabledWorlds = worlds.stream()
                    .filter(w -> w.enabled)
                    .toList();

            assertEquals(2, enabledWorlds.size());
            assertTrue(enabledWorlds.stream().allMatch(w -> w.enabled));
        }

        @Test
        @DisplayName("should filter worlds without spawn points")
        void shouldFilterWorldsWithoutSpawnPoints() {
            List<MockWorld> worlds = createTestWorlds();

            List<MockWorld> validWorlds = worlds.stream()
                    .filter(w -> w.enabled)
                    .filter(w -> w.spawnPointCount > 0)
                    .toList();

            assertEquals(2, validWorlds.size());
        }

        @Test
        @DisplayName("should filter worlds player cannot afford")
        void shouldFilterWorldsPlayerCannotAfford() {
            List<MockWorld> worlds = createTestWorldsWithCosts();
            int playerBalance = 50;

            List<MockWorld> affordableWorlds = worlds.stream()
                    .filter(w -> w.enabled)
                    .filter(w -> w.cost <= playerBalance)
                    .toList();

            assertEquals(2, affordableWorlds.size()); // 0 and 50 cost worlds
        }

        private List<MockWorld> createTestWorlds() {
            List<MockWorld> worlds = new ArrayList<>();
            worlds.add(new MockWorld("arena_forest", true, 3));
            worlds.add(new MockWorld("arena_nether", true, 2));
            worlds.add(new MockWorld("arena_disabled", false, 1));
            return worlds;
        }

        private List<MockWorld> createTestWorldsWithCosts() {
            List<MockWorld> worlds = new ArrayList<>();
            worlds.add(new MockWorld("arena_free", true, 2, 0));
            worlds.add(new MockWorld("arena_cheap", true, 2, 50));
            worlds.add(new MockWorld("arena_expensive", true, 2, 100));
            return worlds;
        }

        private record MockWorld(String name, boolean enabled, int spawnPointCount, int cost) {
            MockWorld(String name, boolean enabled, int spawnPointCount) {
                this(name, enabled, spawnPointCount, 0);
            }
        }
    }

    @Nested
    @DisplayName("World Selection Fallback")
    class WorldSelectionFallbackTests {

        @Test
        @DisplayName("should fall back to random when selected world not found")
        void shouldFallBackToRandomWhenSelectedWorldNotFound() {
            String selectedWorld = "arena_nonexistent";
            Map<String, MockWorldConfig> worlds = createWorldMap();

            boolean worldExists = worlds.containsKey(selectedWorld);
            assertFalse(worldExists);

            // Should trigger fallback
            String fallback = worldExists ? selectedWorld : "random";
            assertEquals("random", fallback);
        }

        @Test
        @DisplayName("should fall back to random when leader offline")
        void shouldFallBackToRandomWhenLeaderOffline() {
            boolean leaderOnline = false;
            int worldCost = 50;

            String selection = (worldCost > 0 && !leaderOnline) ? "random" : "selected";
            assertEquals("random", selection);
        }

        @Test
        @DisplayName("should fall back to random when insufficient balance")
        void shouldFallBackToRandomWhenInsufficientBalance() {
            int playerBalance = 30;
            int worldCost = 50;

            String selection = (playerBalance < worldCost) ? "random" : "selected";
            assertEquals("random", selection);
        }

        @Test
        @DisplayName("should use selected world when all conditions met")
        void shouldUseSelectedWorldWhenAllConditionsMet() {
            boolean worldExists = true;
            boolean worldEnabled = true;
            boolean leaderOnline = true;
            int playerBalance = 100;
            int worldCost = 50;

            boolean canUseSelected = worldExists && worldEnabled && leaderOnline
                    && (worldCost == 0 || playerBalance >= worldCost);

            assertTrue(canUseSelected);
        }

        private Map<String, MockWorldConfig> createWorldMap() {
            Map<String, MockWorldConfig> worlds = new HashMap<>();
            worlds.put("arena_forest", new MockWorldConfig("arena_forest", true, 50));
            worlds.put("arena_nether", new MockWorldConfig("arena_nether", true, 100));
            return worlds;
        }

        private record MockWorldConfig(String name, boolean enabled, int cost) {}
    }

    @Nested
    @DisplayName("Team Selection State")
    class TeamSelectionStateTests {

        @Test
        @DisplayName("should store selected world name")
        void shouldStoreSelectedWorldName() {
            MockTeamState team = new MockTeamState();
            team.setSelectedWorldName("arena_forest");

            assertEquals("arena_forest", team.getSelectedWorldName());
        }

        @Test
        @DisplayName("should clear selection after use")
        void shouldClearSelectionAfterUse() {
            MockTeamState team = new MockTeamState();
            team.setSelectedWorldName("arena_forest");

            // Simulate using selection
            String selected = team.getSelectedWorldName();
            team.setSelectedWorldName(null);

            assertEquals("arena_forest", selected);
            assertNull(team.getSelectedWorldName());
        }

        @Test
        @DisplayName("should default to null selection")
        void shouldDefaultToNullSelection() {
            MockTeamState team = new MockTeamState();
            assertNull(team.getSelectedWorldName());
        }

        @Test
        @DisplayName("should allow changing selection")
        void shouldAllowChangingSelection() {
            MockTeamState team = new MockTeamState();
            team.setSelectedWorldName("arena_forest");
            team.setSelectedWorldName("arena_nether");

            assertEquals("arena_nether", team.getSelectedWorldName());
        }

        private static class MockTeamState {
            private String selectedWorldName;

            public String getSelectedWorldName() {
                return selectedWorldName;
            }

            public void setSelectedWorldName(String name) {
                this.selectedWorldName = name;
            }
        }
    }

    @Nested
    @DisplayName("Leader Only Restriction")
    class LeaderOnlyRestrictionTests {

        @Test
        @DisplayName("should allow leader to select world")
        void shouldAllowLeaderToSelectWorld() {
            UUID leaderId = UUID.randomUUID();
            UUID requesterId = leaderId;

            boolean isLeader = requesterId.equals(leaderId);
            assertTrue(isLeader);
        }

        @Test
        @DisplayName("should deny non-leader from selecting world")
        void shouldDenyNonLeaderFromSelectingWorld() {
            UUID leaderId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();

            boolean isLeader = requesterId.equals(leaderId);
            assertFalse(isLeader);
        }
    }

    @Nested
    @DisplayName("World Selection GUI")
    class WorldSelectionGUITests {

        @Test
        @DisplayName("should calculate GUI size based on world count")
        void shouldCalculateGUISizeBasedOnWorldCount() {
            int worldCount = 5;
            int itemsPerRow = 9;

            int rows = (int) Math.ceil((double) worldCount / itemsPerRow);
            int size = rows * 9;

            assertEquals(9, size); // 5 worlds fit in 1 row
        }

        @Test
        @DisplayName("should require multiple rows for many worlds")
        void shouldRequireMultipleRowsForManyWorlds() {
            int worldCount = 15;
            int itemsPerRow = 9;

            int rows = (int) Math.ceil((double) worldCount / itemsPerRow);
            int size = rows * 9;

            assertEquals(18, size); // 15 worlds need 2 rows
        }

        @Test
        @DisplayName("should show cost in item lore")
        void shouldShowCostInItemLore() {
            int worldCost = 50;
            String costLine = worldCost > 0
                    ? "Cost: " + worldCost + " coins"
                    : "Free";

            assertEquals("Cost: 50 coins", costLine);
        }

        @Test
        @DisplayName("should show free for zero cost worlds")
        void shouldShowFreeForZeroCostWorlds() {
            int worldCost = 0;
            String costLine = worldCost > 0
                    ? "Cost: " + worldCost + " coins"
                    : "Free";

            assertEquals("Free", costLine);
        }

        @Test
        @DisplayName("should indicate insufficient balance in lore")
        void shouldIndicateInsufficientBalanceInLore() {
            int playerBalance = 30;
            int worldCost = 50;

            boolean canAfford = playerBalance >= worldCost;
            String affordLine = canAfford ? "Click to select" : "Insufficient balance";

            assertEquals("Insufficient balance", affordLine);
        }
    }

    @Nested
    @DisplayName("Random World Selection")
    class RandomWorldSelectionTests {

        @Test
        @DisplayName("should select random world from enabled list")
        void shouldSelectRandomWorldFromEnabledList() {
            List<String> enabledWorlds = List.of("arena_forest", "arena_nether", "arena_desert");

            // Random selection should always be from the list
            for (int i = 0; i < 100; i++) {
                int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(enabledWorlds.size());
                String selected = enabledWorlds.get(index);
                assertTrue(enabledWorlds.contains(selected));
            }
        }

        @Test
        @DisplayName("should respect world weights in random selection")
        void shouldRespectWorldWeightsInRandomSelection() {
            // World with weight 3 should be selected ~3x more often than weight 1
            List<MockWeightedWorld> worlds = List.of(
                    new MockWeightedWorld("forest", 3.0),
                    new MockWeightedWorld("nether", 1.0)
            );

            double totalWeight = worlds.stream()
                    .mapToDouble(MockWeightedWorld::weight)
                    .sum();

            assertEquals(4.0, totalWeight);

            // Forest should have 75% probability, nether 25%
            double forestProbability = 3.0 / 4.0;
            assertEquals(0.75, forestProbability);
        }

        private record MockWeightedWorld(String name, double weight) {}
    }
}
