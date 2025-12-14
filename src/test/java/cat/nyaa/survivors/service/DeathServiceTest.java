package cat.nyaa.survivors.service;

import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeathService logic.
 * Note: Full integration tests require Bukkit mocking.
 */
class DeathServiceTest {

    private UUID teamId;
    private UUID runId;
    private TeamState team;
    private RunState run;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        runId = UUID.randomUUID();
        team = new TeamState(teamId, "TestTeam", UUID.randomUUID());
        run = new RunState(runId, teamId, "test_world");
    }

    @Nested
    @DisplayName("Team Wipe Detection")
    class TeamWipeDetection {

        @Test
        @DisplayName("should detect wipe when run has no alive players")
        void shouldDetectWipeWhenNoAlivePlayers() {
            // No one alive in run
            assertTrue(run.getAlivePlayers().isEmpty());
            assertTrue(team.isWiped(run.getAlivePlayers(), 300000));
        }

        @Test
        @DisplayName("should not detect wipe when team member is alive in run")
        void shouldNotDetectWipeWhenMemberAlive() {
            UUID memberId = team.getLeaderId();
            run.addParticipant(memberId);

            assertFalse(team.isWiped(run.getAlivePlayers(), 300000));
        }

        @Test
        @DisplayName("should detect wipe when all members are dead in run")
        void shouldDetectWipeWhenAllDead() {
            UUID leader = team.getLeaderId();
            UUID member2 = UUID.randomUUID();
            team.addMember(member2);

            run.addParticipant(leader);
            run.addParticipant(member2);

            // Kill both
            run.markDead(leader);
            run.markDead(member2);

            assertTrue(team.isWiped(run.getAlivePlayers(), 300000));
        }

        @Test
        @DisplayName("should not detect wipe when one member remains alive")
        void shouldNotDetectWipeWhenOneAlive() {
            UUID leader = team.getLeaderId();
            UUID member2 = UUID.randomUUID();
            team.addMember(member2);

            run.addParticipant(leader);
            run.addParticipant(member2);

            // Kill only one
            run.markDead(leader);

            assertFalse(team.isWiped(run.getAlivePlayers(), 300000));
        }
    }

    @Nested
    @DisplayName("Run Participant State")
    class RunParticipantState {

        @Test
        @DisplayName("should track death count")
        void shouldTrackDeathCount() {
            UUID playerId = UUID.randomUUID();
            run.addParticipant(playerId);

            assertEquals(0, run.getDeathCount(playerId));

            run.markDead(playerId);
            assertEquals(1, run.getDeathCount(playerId));

            run.markAlive(playerId);
            run.markDead(playerId);
            assertEquals(2, run.getDeathCount(playerId));
        }

        @Test
        @DisplayName("should correctly track alive status")
        void shouldCorrectlyTrackAliveStatus() {
            UUID playerId = UUID.randomUUID();
            run.addParticipant(playerId);

            assertTrue(run.isAlive(playerId));
            assertEquals(1, run.getAliveCount());

            run.markDead(playerId);
            assertFalse(run.isAlive(playerId));
            assertEquals(0, run.getAliveCount());

            run.markAlive(playerId);
            assertTrue(run.isAlive(playerId));
            assertEquals(1, run.getAliveCount());
        }

        @Test
        @DisplayName("should return alive players set")
        void shouldReturnAlivePlayersSet() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            run.addParticipant(player1);
            run.addParticipant(player2);

            Set<UUID> alive = run.getAlivePlayers();
            assertEquals(2, alive.size());
            assertTrue(alive.contains(player1));
            assertTrue(alive.contains(player2));

            run.markDead(player1);
            alive = run.getAlivePlayers();
            assertEquals(1, alive.size());
            assertFalse(alive.contains(player1));
            assertTrue(alive.contains(player2));
        }
    }

    @Nested
    @DisplayName("Player State Reset")
    class PlayerStateReset {

        @Test
        @DisplayName("should reset all run state on death")
        void shouldResetRunStateOnDeath() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");

            // Set up some state
            state.setWeaponGroup("sword");
            state.setWeaponLevel(3);
            state.setHelmetGroup("armor");
            state.setHelmetLevel(2);
            state.setXpProgress(50);
            state.setXpHeld(25);
            state.setUpgradePending(true);
            state.setOverflowXpAccumulated(100);
            state.setRunId(UUID.randomUUID());

            // Reset
            state.resetRunState();

            // Verify all run state is cleared
            assertNull(state.getWeaponGroup());
            assertEquals(0, state.getWeaponLevel());
            assertNull(state.getHelmetGroup());
            assertEquals(0, state.getHelmetLevel());
            assertEquals(0, state.getXpProgress());
            assertEquals(0, state.getXpHeld());
            assertFalse(state.isUpgradePending());
            assertEquals(0, state.getOverflowXpAccumulated());
            assertNull(state.getRunId());
        }

        @Test
        @DisplayName("should preserve perma-score on reset")
        void shouldPreservePermaScoreOnReset() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");
            state.setPermaScore(500);

            state.resetRunState();

            assertEquals(500, state.getPermaScore());
        }

        @Test
        @DisplayName("should reset starter selections on reset for re-selection after death")
        void shouldResetStarterSelectionsOnReset() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");
            state.setStarterWeaponOptionId("iron_sword");
            state.setStarterHelmetOptionId("iron_helmet");

            state.resetRunState();

            // Starter selections should be reset so player can re-select after death
            assertNull(state.getStarterWeaponOptionId());
            assertNull(state.getStarterHelmetOptionId());
        }
    }

    @Nested
    @DisplayName("Cooldown Management")
    class CooldownManagement {

        @Test
        @DisplayName("should correctly check cooldown status")
        void shouldCorrectlyCheckCooldownStatus() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");

            assertFalse(state.isOnCooldown());

            state.setCooldownUntilMillis(System.currentTimeMillis() + 60000);
            assertTrue(state.isOnCooldown());

            state.setCooldownUntilMillis(System.currentTimeMillis() - 1000);
            assertFalse(state.isOnCooldown());
        }

        @Test
        @DisplayName("should calculate remaining cooldown seconds")
        void shouldCalculateRemainingCooldownSeconds() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");

            state.setCooldownUntilMillis(System.currentTimeMillis() + 30000);
            long remaining = state.getCooldownRemainingSeconds();

            assertTrue(remaining > 25 && remaining <= 30);
        }

        @Test
        @DisplayName("should return zero for expired cooldown")
        void shouldReturnZeroForExpiredCooldown() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");

            state.setCooldownUntilMillis(System.currentTimeMillis() - 5000);
            assertEquals(0, state.getCooldownRemainingSeconds());
        }
    }

    @Nested
    @DisplayName("Invulnerability")
    class Invulnerability {

        @Test
        @DisplayName("should correctly check invulnerability status")
        void shouldCorrectlyCheckInvulnerabilityStatus() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");

            assertFalse(state.isInvulnerable());

            state.setInvulnerableUntilMillis(System.currentTimeMillis() + 3000);
            assertTrue(state.isInvulnerable());

            state.setInvulnerableUntilMillis(System.currentTimeMillis() - 1000);
            assertFalse(state.isInvulnerable());
        }
    }

    @Nested
    @DisplayName("Disconnect Grace Period")
    class DisconnectGracePeriod {

        @Test
        @DisplayName("should detect within grace period")
        void shouldDetectWithinGracePeriod() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");

            assertFalse(state.isWithinGracePeriod(30000));

            state.setDisconnectedAtMillis(System.currentTimeMillis() - 10000);
            assertTrue(state.isWithinGracePeriod(30000)); // 10s < 30s grace

            state.setDisconnectedAtMillis(System.currentTimeMillis() - 60000);
            assertFalse(state.isWithinGracePeriod(30000)); // 60s > 30s grace
        }

        @Test
        @DisplayName("should return false for zero disconnect time")
        void shouldReturnFalseForZeroDisconnectTime() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");
            state.setDisconnectedAtMillis(0);

            assertFalse(state.isWithinGracePeriod(30000));
        }
    }

    @Nested
    @DisplayName("Death With Living Teammates")
    class DeathWithLivingTeammates {

        @Test
        @DisplayName("should remove player from run but not trigger wipe when teammates alive")
        void shouldRemoveFromRunButNotWipe() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            team.addMember(player1);
            team.addMember(player2);

            run.addParticipant(player1);
            run.addParticipant(player2);

            // Player 1 dies - should be marked dead but team not wiped
            run.markDead(player1);
            run.removeParticipant(player1);

            assertFalse(run.isAlive(player1));
            assertTrue(run.isAlive(player2));
            assertEquals(1, run.getAliveCount());
            assertFalse(team.isWiped(run.getAlivePlayers(), 300000));
        }

        @Test
        @DisplayName("should allow re-selection after run state reset")
        void shouldAllowReSelectionAfterReset() {
            PlayerState state = new PlayerState(UUID.randomUUID(), "TestPlayer");

            // Player in run with starters selected
            state.setMode(PlayerMode.IN_RUN);
            state.setStarterWeaponOptionId("iron_sword");
            state.setStarterHelmetOptionId("iron_helmet");
            state.setWeaponGroup("sword");
            state.setHelmetGroup("armor");
            assertTrue(state.hasSelectedStarters());

            // Death penalty resets run state
            state.resetRunState();
            state.setMode(PlayerMode.COOLDOWN);

            // Verify starters are cleared for re-selection
            assertNull(state.getStarterWeaponOptionId());
            assertNull(state.getStarterHelmetOptionId());
            assertFalse(state.hasSelectedStarters());
            assertEquals(PlayerMode.COOLDOWN, state.getMode());
        }

        @Test
        @DisplayName("should track alive count correctly after death removal")
        void shouldTrackAliveCountAfterRemoval() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            run.addParticipant(player1);
            run.addParticipant(player2);
            run.addParticipant(player3);

            assertEquals(3, run.getAliveCount());

            // First death
            run.markDead(player1);
            run.removeParticipant(player1);
            assertEquals(2, run.getAliveCount());

            // Second death
            run.markDead(player2);
            run.removeParticipant(player2);
            assertEquals(1, run.getAliveCount());

            // Last player still alive - not wiped
            assertTrue(run.isAlive(player3));
        }
    }

    @Nested
    @DisplayName("Multi-player Scenarios")
    class MultiPlayerScenarios {

        @Test
        @DisplayName("should handle sequential deaths correctly")
        void shouldHandleSequentialDeathsCorrectly() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            team.addMember(player1);
            team.addMember(player2);
            team.addMember(player3);

            run.addParticipant(player1);
            run.addParticipant(player2);
            run.addParticipant(player3);

            assertEquals(3, run.getAliveCount());
            assertFalse(team.isWiped(run.getAlivePlayers(), 300000));

            run.markDead(player1);
            assertEquals(2, run.getAliveCount());
            assertFalse(team.isWiped(run.getAlivePlayers(), 300000));

            run.markDead(player2);
            assertEquals(1, run.getAliveCount());
            assertFalse(team.isWiped(run.getAlivePlayers(), 300000));

            run.markDead(player3);
            assertEquals(0, run.getAliveCount());
            assertTrue(team.isWiped(run.getAlivePlayers(), 300000));
        }

        @Test
        @DisplayName("should handle respawn and death cycle")
        void shouldHandleRespawnAndDeathCycle() {
            UUID player = UUID.randomUUID();
            run.addParticipant(player);

            // Initial state
            assertTrue(run.isAlive(player));
            assertEquals(0, run.getDeathCount(player));

            // First death
            run.markDead(player);
            assertFalse(run.isAlive(player));
            assertEquals(1, run.getDeathCount(player));

            // Respawn
            run.markAlive(player);
            assertTrue(run.isAlive(player));
            assertEquals(1, run.getDeathCount(player)); // Death count stays

            // Second death
            run.markDead(player);
            assertFalse(run.isAlive(player));
            assertEquals(2, run.getDeathCount(player));
        }
    }
}
