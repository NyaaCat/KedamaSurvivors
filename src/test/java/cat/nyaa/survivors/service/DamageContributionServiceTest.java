package cat.nyaa.survivors.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DamageContributionService.
 * Tests damage tracking logic without Bukkit dependencies.
 */
class DamageContributionServiceTest {

    private DamageContributionService service;

    @BeforeEach
    void setUp() {
        // Create service without plugin dependency for pure unit tests
        service = new DamageContributionService(null);
    }

    @Nested
    @DisplayName("Damage Recording")
    class DamageRecording {

        @Test
        @DisplayName("should record damage for new mob")
        void shouldRecordDamageForNewMob() {
            UUID mobId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();

            service.recordDamage(mobId, playerId, 10.0);

            Map<UUID, Double> contributors = service.getContributors(mobId);
            assertEquals(1, contributors.size());
            assertEquals(10.0, contributors.get(playerId));
        }

        @Test
        @DisplayName("should accumulate damage from same player")
        void shouldAccumulateDamageFromSamePlayer() {
            UUID mobId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();

            service.recordDamage(mobId, playerId, 10.0);
            service.recordDamage(mobId, playerId, 15.0);
            service.recordDamage(mobId, playerId, 5.0);

            Map<UUID, Double> contributors = service.getContributors(mobId);
            assertEquals(30.0, contributors.get(playerId));
        }

        @Test
        @DisplayName("should track multiple players damaging same mob")
        void shouldTrackMultiplePlayersDamagingSameMob() {
            UUID mobId = UUID.randomUUID();
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            service.recordDamage(mobId, player1, 50.0);
            service.recordDamage(mobId, player2, 30.0);
            service.recordDamage(mobId, player3, 20.0);

            Map<UUID, Double> contributors = service.getContributors(mobId);
            assertEquals(3, contributors.size());
            assertEquals(50.0, contributors.get(player1));
            assertEquals(30.0, contributors.get(player2));
            assertEquals(20.0, contributors.get(player3));
        }

        @Test
        @DisplayName("should track damage to multiple mobs independently")
        void shouldTrackDamageToMultipleMobsIndependently() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();
            UUID player = UUID.randomUUID();

            service.recordDamage(mob1, player, 100.0);
            service.recordDamage(mob2, player, 50.0);

            assertEquals(100.0, service.getContributors(mob1).get(player));
            assertEquals(50.0, service.getContributors(mob2).get(player));
        }

        @Test
        @DisplayName("should ignore zero damage")
        void shouldIgnoreZeroDamage() {
            UUID mobId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();

            service.recordDamage(mobId, playerId, 0.0);

            assertTrue(service.getContributors(mobId).isEmpty());
        }

        @Test
        @DisplayName("should ignore negative damage")
        void shouldIgnoreNegativeDamage() {
            UUID mobId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();

            service.recordDamage(mobId, playerId, -10.0);

            assertTrue(service.getContributors(mobId).isEmpty());
        }

        @Test
        @DisplayName("should handle very small damage values")
        void shouldHandleVerySmallDamageValues() {
            UUID mobId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();

            service.recordDamage(mobId, playerId, 0.001);

            assertEquals(0.001, service.getContributors(mobId).get(playerId), 0.0001);
        }

        @Test
        @DisplayName("should handle large damage values without overflow")
        void shouldHandleLargeDamageValuesWithoutOverflow() {
            UUID mobId = UUID.randomUUID();
            UUID playerId = UUID.randomUUID();

            service.recordDamage(mobId, playerId, 1000000.0);
            service.recordDamage(mobId, playerId, 1000000.0);

            assertEquals(2000000.0, service.getContributors(mobId).get(playerId));
        }
    }

    @Nested
    @DisplayName("Mob Cleanup")
    class MobCleanup {

        @Test
        @DisplayName("should clear specific mob data")
        void shouldClearSpecificMobData() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();
            UUID player = UUID.randomUUID();

            service.recordDamage(mob1, player, 100.0);
            service.recordDamage(mob2, player, 50.0);

            service.clearMob(mob1);

            assertTrue(service.getContributors(mob1).isEmpty());
            assertFalse(service.getContributors(mob2).isEmpty());
        }

        @Test
        @DisplayName("should handle clearing non-existent mob gracefully")
        void shouldHandleClearingNonExistentMob() {
            UUID mobId = UUID.randomUUID();

            // Should not throw
            assertDoesNotThrow(() -> service.clearMob(mobId));
        }

        @Test
        @DisplayName("should clear all tracked data")
        void shouldClearAllTrackedData() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();
            UUID player = UUID.randomUUID();

            service.recordDamage(mob1, player, 100.0);
            service.recordDamage(mob2, player, 50.0);

            service.clearAll();

            assertEquals(0, service.getTrackedMobCount());
            assertTrue(service.getContributors(mob1).isEmpty());
            assertTrue(service.getContributors(mob2).isEmpty());
        }
    }

    @Nested
    @DisplayName("Get Contributors")
    class GetContributors {

        @Test
        @DisplayName("should return empty map for unknown mob")
        void shouldReturnEmptyMapForUnknownMob() {
            UUID unknownMob = UUID.randomUUID();

            Map<UUID, Double> contributors = service.getContributors(unknownMob);

            assertNotNull(contributors);
            assertTrue(contributors.isEmpty());
        }

        @Test
        @DisplayName("should return all contributors for a mob")
        void shouldReturnAllContributorsForMob() {
            UUID mobId = UUID.randomUUID();
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            service.recordDamage(mobId, player1, 25.0);
            service.recordDamage(mobId, player2, 75.0);

            Map<UUID, Double> contributors = service.getContributors(mobId);

            assertEquals(2, contributors.size());
            assertTrue(contributors.containsKey(player1));
            assertTrue(contributors.containsKey(player2));
        }
    }

    @Nested
    @DisplayName("Tracked Mob Count")
    class TrackedMobCount {

        @Test
        @DisplayName("should start with zero tracked mobs")
        void shouldStartWithZeroTrackedMobs() {
            assertEquals(0, service.getTrackedMobCount());
        }

        @Test
        @DisplayName("should track mob count correctly")
        void shouldTrackMobCountCorrectly() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();
            UUID mob3 = UUID.randomUUID();
            UUID player = UUID.randomUUID();

            assertEquals(0, service.getTrackedMobCount());

            service.recordDamage(mob1, player, 10.0);
            assertEquals(1, service.getTrackedMobCount());

            service.recordDamage(mob2, player, 10.0);
            service.recordDamage(mob3, player, 10.0);
            assertEquals(3, service.getTrackedMobCount());

            service.clearMob(mob1);
            assertEquals(2, service.getTrackedMobCount());

            service.clearAll();
            assertEquals(0, service.getTrackedMobCount());
        }

        @Test
        @DisplayName("should not increment count for same mob")
        void shouldNotIncrementCountForSameMob() {
            UUID mobId = UUID.randomUUID();
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            service.recordDamage(mobId, player1, 10.0);
            service.recordDamage(mobId, player2, 20.0);
            service.recordDamage(mobId, player1, 30.0);

            assertEquals(1, service.getTrackedMobCount());
        }
    }

    @Nested
    @DisplayName("Damage Contribution XP Calculation (Pure Logic)")
    class DamageContributionXpCalculation {

        @Test
        @DisplayName("should calculate correct contribution share amount (10%)")
        void shouldCalculateCorrectContributionShareAmount() {
            int baseXp = 100;
            double sharePercent = 0.10;

            int sharedAmount = (int) (baseXp * sharePercent);
            assertEquals(10, sharedAmount);
        }

        @Test
        @DisplayName("should truncate fractional XP to integer")
        void shouldTruncateFractionalXp() {
            int baseXp = 15;
            double sharePercent = 0.10;

            int sharedAmount = (int) (baseXp * sharePercent); // 1.5 -> 1
            assertEquals(1, sharedAmount);
        }

        @Test
        @DisplayName("should return zero when share would truncate to zero")
        void shouldReturnZeroWhenShareWouldTruncateToZero() {
            int baseXp = 5;
            double sharePercent = 0.10;

            int sharedAmount = (int) (baseXp * sharePercent); // 0.5 -> 0
            assertEquals(0, sharedAmount);
        }

        @Test
        @DisplayName("should handle high share percentage")
        void shouldHandleHighSharePercentage() {
            int baseXp = 100;
            double sharePercent = 0.50;

            int sharedAmount = (int) (baseXp * sharePercent);
            assertEquals(50, sharedAmount);
        }

        @Test
        @DisplayName("should return zero for disabled contribution (0%)")
        void shouldReturnZeroForDisabledContribution() {
            int baseXp = 100;
            double sharePercent = 0.0;

            int sharedAmount = (int) (baseXp * sharePercent);
            assertEquals(0, sharedAmount);
        }

        @Test
        @DisplayName("should return zero for zero base XP")
        void shouldReturnZeroForZeroBaseXp() {
            int baseXp = 0;
            double sharePercent = 0.10;

            int sharedAmount = (int) (baseXp * sharePercent);
            assertEquals(0, sharedAmount);
        }
    }

    @Nested
    @DisplayName("Multi-Player Scenarios")
    class MultiPlayerScenarios {

        @Test
        @DisplayName("should track complex multi-player damage scenario")
        void shouldTrackComplexMultiPlayerDamageScenario() {
            UUID mobId = UUID.randomUUID();
            UUID killer = UUID.randomUUID();
            UUID helper1 = UUID.randomUUID();
            UUID helper2 = UUID.randomUUID();

            // Simulate combat: multiple players deal damage over time
            service.recordDamage(mobId, helper1, 20.0);
            service.recordDamage(mobId, killer, 10.0);
            service.recordDamage(mobId, helper2, 15.0);
            service.recordDamage(mobId, killer, 25.0);
            service.recordDamage(mobId, helper1, 5.0);
            service.recordDamage(mobId, killer, 50.0); // Killing blow

            Map<UUID, Double> contributors = service.getContributors(mobId);

            assertEquals(3, contributors.size());
            assertEquals(85.0, contributors.get(killer)); // 10 + 25 + 50
            assertEquals(25.0, contributors.get(helper1)); // 20 + 5
            assertEquals(15.0, contributors.get(helper2));
        }

        @Test
        @DisplayName("should track multiple mobs being fought simultaneously")
        void shouldTrackMultipleMobsBeingFoughtSimultaneously() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();
            UUID mob3 = UUID.randomUUID();
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            // Player 1 fights mob 1 and 2
            service.recordDamage(mob1, player1, 30.0);
            service.recordDamage(mob2, player1, 20.0);

            // Player 2 fights mob 2 and 3
            service.recordDamage(mob2, player2, 40.0);
            service.recordDamage(mob3, player2, 50.0);

            // Verify mob 1: only player 1
            assertEquals(1, service.getContributors(mob1).size());
            assertEquals(30.0, service.getContributors(mob1).get(player1));

            // Verify mob 2: both players
            assertEquals(2, service.getContributors(mob2).size());
            assertEquals(20.0, service.getContributors(mob2).get(player1));
            assertEquals(40.0, service.getContributors(mob2).get(player2));

            // Verify mob 3: only player 2
            assertEquals(1, service.getContributors(mob3).size());
            assertEquals(50.0, service.getContributors(mob3).get(player2));

            assertEquals(3, service.getTrackedMobCount());
        }
    }
}
