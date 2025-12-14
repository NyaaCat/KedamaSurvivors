package cat.nyaa.survivors.model;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RunState.
 */
class RunStateTest {

    private UUID runId;
    private UUID teamId;
    private RunState runState;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        runState = new RunState(runId, teamId, "combat_world");
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        @DisplayName("should initialize with correct IDs")
        void shouldInitializeWithCorrectIds() {
            assertEquals(runId, runState.getRunId());
            assertEquals(teamId, runState.getTeamId());
            assertEquals("combat_world", runState.getWorldName());
        }

        @Test
        @DisplayName("should initialize with STARTING status")
        void shouldInitializeWithStartingStatus() {
            assertEquals(RunStatus.STARTING, runState.getStatus());
        }

        @Test
        @DisplayName("should have start time set on creation")
        void shouldHaveStartTimeSetOnCreation() {
            assertTrue(runState.getStartedAtMillis() > 0);
            assertTrue(runState.getStartedAtMillis() <= System.currentTimeMillis());
        }
    }

    @Nested
    @DisplayName("Participant Management")
    class ParticipantManagement {

        private UUID playerId;

        @BeforeEach
        void setUp() {
            playerId = UUID.randomUUID();
        }

        @Test
        @DisplayName("should add participant correctly")
        void shouldAddParticipantCorrectly() {
            runState.addParticipant(playerId);
            assertTrue(runState.isParticipant(playerId));
            assertTrue(runState.isAlive(playerId));
            assertEquals(1, runState.getParticipantCount());
        }

        @Test
        @DisplayName("should remove participant correctly")
        void shouldRemoveParticipantCorrectly() {
            runState.addParticipant(playerId);
            runState.removeParticipant(playerId);
            assertFalse(runState.isParticipant(playerId));
            assertFalse(runState.isAlive(playerId));
        }

        @Test
        @DisplayName("should handle multiple participants")
        void shouldHandleMultipleParticipants() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            runState.addParticipant(player1);
            runState.addParticipant(player2);
            runState.addParticipant(player3);

            assertEquals(3, runState.getParticipantCount());
            assertEquals(3, runState.getAliveCount());
        }
    }

    @Nested
    @DisplayName("Life/Death Tracking")
    class LifeDeathTracking {

        private UUID playerId;

        @BeforeEach
        void setUp() {
            playerId = UUID.randomUUID();
            runState.addParticipant(playerId);
        }

        @Test
        @DisplayName("should mark player as dead")
        void shouldMarkPlayerAsDead() {
            runState.markDead(playerId);
            assertFalse(runState.isAlive(playerId));
            assertTrue(runState.isParticipant(playerId)); // Still participant
        }

        @Test
        @DisplayName("should track death count")
        void shouldTrackDeathCount() {
            assertEquals(0, runState.getDeathCount(playerId));
            runState.markDead(playerId);
            assertEquals(1, runState.getDeathCount(playerId));
            runState.markAlive(playerId);
            runState.markDead(playerId);
            assertEquals(2, runState.getDeathCount(playerId));
        }

        @Test
        @DisplayName("should mark player as alive again")
        void shouldMarkPlayerAsAliveAgain() {
            runState.markDead(playerId);
            runState.markAlive(playerId);
            assertTrue(runState.isAlive(playerId));
        }

        @Test
        @DisplayName("should detect team wipe")
        void shouldDetectTeamWipe() {
            UUID player2 = UUID.randomUUID();
            runState.addParticipant(player2);

            assertFalse(runState.isTeamWiped());

            runState.markDead(playerId);
            assertFalse(runState.isTeamWiped());

            runState.markDead(player2);
            assertTrue(runState.isTeamWiped());
        }
    }

    @Nested
    @DisplayName("Statistics Tracking")
    class StatisticsTracking {

        @Test
        @DisplayName("should increment kills")
        void shouldIncrementKills() {
            assertEquals(0, runState.getTotalKills());
            runState.incrementKills();
            assertEquals(1, runState.getTotalKills());
            runState.addKills(5);
            assertEquals(6, runState.getTotalKills());
        }

        @Test
        @DisplayName("should track XP earned")
        void shouldTrackXpEarned() {
            assertEquals(0, runState.getTotalXpCollected());
            runState.addXpEarned(100);
            assertEquals(100, runState.getTotalXpCollected());
            runState.addXp(50);
            assertEquals(150, runState.getTotalXpCollected());
        }

        @Test
        @DisplayName("should track coins earned")
        void shouldTrackCoinsEarned() {
            assertEquals(0, runState.getTotalCoinsCollected());
            runState.addCoinEarned(10);
            assertEquals(10, runState.getTotalCoinsCollected());
            runState.addCoins(5);
            assertEquals(15, runState.getTotalCoinsCollected());
        }

        @Test
        @DisplayName("should track wave number")
        void shouldTrackWaveNumber() {
            assertEquals(0, runState.getWaveNumber());
            runState.incrementWave();
            assertEquals(1, runState.getWaveNumber());
        }
    }

    @Nested
    @DisplayName("Status Management")
    class StatusManagement {

        @Test
        @DisplayName("should transition to ACTIVE")
        void shouldTransitionToActive() {
            runState.start();
            assertEquals(RunStatus.ACTIVE, runState.getStatus());
            assertTrue(runState.isActive());
        }

        @Test
        @DisplayName("should transition to ENDING")
        void shouldTransitionToEnding() {
            runState.start();
            runState.end();
            assertEquals(RunStatus.ENDING, runState.getStatus());
            assertTrue(runState.isEnded());
            assertTrue(runState.getEndedAtMillis() > 0);
        }

        @Test
        @DisplayName("should transition to COMPLETED")
        void shouldTransitionToCompleted() {
            runState.start();
            runState.complete();
            assertEquals(RunStatus.COMPLETED, runState.getStatus());
            assertTrue(runState.isEnded());
        }
    }

    @Nested
    @DisplayName("Time Calculation")
    class TimeCalculation {

        @Test
        @DisplayName("should calculate elapsed seconds")
        void shouldCalculateElapsedSeconds() throws InterruptedException {
            // Give a small delay to ensure non-zero elapsed time
            Thread.sleep(100);
            assertTrue(runState.getElapsedSeconds() >= 0);
        }

        @Test
        @DisplayName("should format elapsed time as MM:SS")
        void shouldFormatElapsedTime() {
            String formatted = runState.getElapsedFormatted();
            assertTrue(formatted.matches("\\d{2}:\\d{2}"));
        }
    }

    @Nested
    @DisplayName("Average Level Calculation")
    class AverageLevelCalculation {

        @Test
        @DisplayName("should return zero for empty participants")
        void shouldReturnZeroForEmptyParticipants() {
            double avg = runState.getAveragePlayerLevel(uuid -> 5);
            assertEquals(0.0, avg);
        }

        @Test
        @DisplayName("should calculate average level correctly")
        void shouldCalculateAverageLevelCorrectly() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            runState.addParticipant(player1);
            runState.addParticipant(player2);

            // Player 1 has level 4, player 2 has level 6 = avg 5
            double avg = runState.getAveragePlayerLevel(uuid -> {
                if (uuid.equals(player1)) return 4;
                if (uuid.equals(player2)) return 6;
                return 0;
            });

            assertEquals(5.0, avg);
        }
    }

    @Nested
    @DisplayName("Enemy Tracking")
    class EnemyTracking {

        @Test
        @DisplayName("should add and remove enemies")
        void shouldAddAndRemoveEnemies() {
            UUID enemyId = UUID.randomUUID();
            runState.addEnemy(enemyId);
            assertTrue(runState.isEnemy(enemyId));
            assertEquals(1, runState.getActiveEnemyCount());

            runState.removeEnemy(enemyId);
            assertFalse(runState.isEnemy(enemyId));
            assertEquals(0, runState.getActiveEnemyCount());
        }

        @Test
        @DisplayName("should clear all enemies")
        void shouldClearAllEnemies() {
            runState.addEnemy(UUID.randomUUID());
            runState.addEnemy(UUID.randomUUID());
            runState.addEnemy(UUID.randomUUID());

            assertEquals(3, runState.getActiveEnemyCount());

            runState.clearEnemies();
            assertEquals(0, runState.getActiveEnemyCount());
        }
    }
}
