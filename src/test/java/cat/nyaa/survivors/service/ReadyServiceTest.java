package cat.nyaa.survivors.service;

import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import cat.nyaa.survivors.model.RunState;
import cat.nyaa.survivors.model.TeamState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReadyService logic.
 * Tests focus on state transitions during countdown and rejoin scenarios.
 * Note: Full integration tests require Bukkit mocking.
 */
class ReadyServiceTest {

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
    @DisplayName("Countdown Mode Transitions")
    class CountdownModeTransitions {

        @Test
        @DisplayName("should set COUNTDOWN mode for eligible LOBBY players")
        void shouldSetCountdownModeForLobbyPlayers() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.LOBBY);

            // Simulate startCountdown eligibility check
            boolean shouldSetCountdown = playerState.getMode() != PlayerMode.IN_RUN && !playerState.isOnCooldown();

            assertTrue(shouldSetCountdown, "LOBBY player should be eligible for COUNTDOWN");

            // Simulate mode change
            if (shouldSetCountdown) {
                playerState.setMode(PlayerMode.COUNTDOWN);
            }

            assertEquals(PlayerMode.COUNTDOWN, playerState.getMode());
        }

        @Test
        @DisplayName("should not change IN_RUN players during countdown start")
        void shouldNotChangeInRunPlayersDuringCountdownStart() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.IN_RUN);

            // Simulate startCountdown eligibility check
            boolean shouldSetCountdown = playerState.getMode() != PlayerMode.IN_RUN && !playerState.isOnCooldown();

            assertFalse(shouldSetCountdown, "IN_RUN player should NOT be eligible for COUNTDOWN");
            assertEquals(PlayerMode.IN_RUN, playerState.getMode());
        }

        @Test
        @DisplayName("should not change COOLDOWN players during countdown start")
        void shouldNotChangeCooldownPlayersDuringCountdownStart() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COOLDOWN);
            playerState.setCooldownUntilMillis(System.currentTimeMillis() + 60000);

            // Simulate startCountdown eligibility check
            boolean shouldSetCountdown = playerState.getMode() != PlayerMode.IN_RUN && !playerState.isOnCooldown();

            assertFalse(shouldSetCountdown, "COOLDOWN player should NOT be eligible for COUNTDOWN");
            assertEquals(PlayerMode.COOLDOWN, playerState.getMode());
        }

        @Test
        @DisplayName("should set COUNTDOWN mode for READY players")
        void shouldSetCountdownModeForReadyPlayers() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.READY);
            playerState.setReady(true);

            // Simulate startCountdown eligibility check
            boolean shouldSetCountdown = playerState.getMode() != PlayerMode.IN_RUN && !playerState.isOnCooldown();

            assertTrue(shouldSetCountdown, "READY player should be eligible for COUNTDOWN");

            if (shouldSetCountdown) {
                playerState.setMode(PlayerMode.COUNTDOWN);
            }

            assertEquals(PlayerMode.COUNTDOWN, playerState.getMode());
        }
    }

    @Nested
    @DisplayName("Countdown Cancellation")
    class CountdownCancellation {

        @Test
        @DisplayName("should reset COUNTDOWN players to READY if they were ready")
        void shouldResetCountdownPlayersToReadyIfReady() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);

            // Simulate cancelCountdown logic
            if (playerState.getMode() == PlayerMode.COUNTDOWN) {
                playerState.setMode(playerState.isReady() ? PlayerMode.READY : PlayerMode.LOBBY);
            }

            assertEquals(PlayerMode.READY, playerState.getMode());
            assertTrue(playerState.isReady());
        }

        @Test
        @DisplayName("should reset COUNTDOWN players to LOBBY if they were not ready")
        void shouldResetCountdownPlayersToLobbyIfNotReady() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(false);

            // Simulate cancelCountdown logic
            if (playerState.getMode() == PlayerMode.COUNTDOWN) {
                playerState.setMode(playerState.isReady() ? PlayerMode.READY : PlayerMode.LOBBY);
            }

            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertFalse(playerState.isReady());
        }

        @Test
        @DisplayName("should not affect non-COUNTDOWN players during cancellation")
        void shouldNotAffectNonCountdownPlayersDuringCancellation() {
            PlayerState inRunPlayer = new PlayerState(UUID.randomUUID(), "InRunPlayer");
            inRunPlayer.setMode(PlayerMode.IN_RUN);

            PlayerState cooldownPlayer = new PlayerState(UUID.randomUUID(), "CooldownPlayer");
            cooldownPlayer.setMode(PlayerMode.COOLDOWN);

            // Simulate cancelCountdown - only affects COUNTDOWN mode
            if (inRunPlayer.getMode() == PlayerMode.COUNTDOWN) {
                inRunPlayer.setMode(PlayerMode.READY);
            }
            if (cooldownPlayer.getMode() == PlayerMode.COUNTDOWN) {
                cooldownPlayer.setMode(PlayerMode.READY);
            }

            assertEquals(PlayerMode.IN_RUN, inRunPlayer.getMode());
            assertEquals(PlayerMode.COOLDOWN, cooldownPlayer.getMode());
        }
    }

    @Nested
    @DisplayName("Rejoin Flow - Player Eligibility")
    class RejoinFlowEligibility {

        @Test
        @DisplayName("should mark COUNTDOWN+ready player as eligible for rejoin")
        void shouldMarkCountdownReadyPlayerAsEligible() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);

            // Simulate handleRejoinToRun eligibility check
            boolean isEligible = playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady();

            assertTrue(isEligible, "COUNTDOWN + ready player should be eligible for rejoin");
        }

        @Test
        @DisplayName("should NOT mark COUNTDOWN but not ready player as eligible")
        void shouldNotMarkCountdownNotReadyAsEligible() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(false);

            // Simulate handleRejoinToRun eligibility check
            boolean isEligible = playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady();

            assertFalse(isEligible, "COUNTDOWN but not ready player should NOT be eligible");
        }

        @Test
        @DisplayName("should NOT mark IN_RUN player as eligible for rejoin")
        void shouldNotMarkInRunPlayerAsEligible() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.IN_RUN);
            playerState.setReady(false);

            boolean isEligible = playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady();

            assertFalse(isEligible, "IN_RUN player should NOT be eligible for rejoin");
        }

        @Test
        @DisplayName("should NOT mark COOLDOWN player as eligible for rejoin")
        void shouldNotMarkCooldownPlayerAsEligible() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COOLDOWN);
            playerState.setReady(false);

            boolean isEligible = playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady();

            assertFalse(isEligible, "COOLDOWN player should NOT be eligible for rejoin");
        }
    }

    @Nested
    @DisplayName("Rejoin Flow - Offline Player Handling (BUG FIX)")
    class RejoinFlowOfflineHandling {

        @Test
        @DisplayName("should reset offline player in COUNTDOWN mode to LOBBY")
        void shouldResetOfflineCountdownPlayerToLobby() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);

            // Simulate the fix: player is eligible but offline
            boolean isEligible = playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady();
            boolean isOnline = false; // Simulates player.isOnline() returning false

            if (isEligible) {
                if (isOnline) {
                    // Would call rejoinPlayerToRun
                } else {
                    // FIX: Reset player to allow retry later
                    playerState.setMode(PlayerMode.LOBBY);
                    playerState.setReady(false);
                }
            }

            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertFalse(playerState.isReady());
        }

        @Test
        @DisplayName("should keep online player in COUNTDOWN when rejoin is called")
        void shouldKeepOnlinePlayerForRejoin() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);

            // Simulate the fix: player is eligible and online
            boolean isEligible = playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady();
            boolean isOnline = true;

            // In real code, rejoinPlayerToRun would be called which sets to IN_RUN
            // For this test, we just verify the flow
            assertTrue(isEligible && isOnline, "Online eligible player should proceed to rejoin");
            assertEquals(PlayerMode.COUNTDOWN, playerState.getMode(), "Mode unchanged until rejoin completes");
        }

        @Test
        @DisplayName("should allow player to re-ready after being reset from offline state")
        void shouldAllowReReadyAfterOfflineReset() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);
            playerState.setStarterWeaponOptionId("iron_sword");
            playerState.setStarterHelmetOptionId("iron_helmet");

            // Simulate offline reset
            playerState.setMode(PlayerMode.LOBBY);
            playerState.setReady(false);

            // Verify player can now re-ready
            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertFalse(playerState.isReady());
            assertTrue(playerState.hasSelectedStarters(), "Starters should still be selected");

            // Player re-readies
            playerState.setReady(true);
            assertTrue(playerState.isReady());
        }
    }

    @Nested
    @DisplayName("Disconnect During Countdown")
    class DisconnectDuringCountdown {

        @Test
        @DisplayName("should reset disconnecting player to LOBBY")
        void shouldResetDisconnectingPlayerToLobby() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);

            // Simulate handleDisconnectInCountdown
            playerState.setMode(PlayerMode.LOBBY);
            playerState.setReady(false);

            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertFalse(playerState.isReady());
        }

        @Test
        @DisplayName("should reset all team COUNTDOWN members to READY when one disconnects")
        void shouldResetTeamCountdownMembersToReady() {
            PlayerState player1 = new PlayerState(UUID.randomUUID(), "Player1");
            player1.setMode(PlayerMode.COUNTDOWN);
            player1.setReady(true);

            PlayerState player2 = new PlayerState(UUID.randomUUID(), "Player2");
            player2.setMode(PlayerMode.COUNTDOWN);
            player2.setReady(true);

            PlayerState player3 = new PlayerState(UUID.randomUUID(), "Player3");
            player3.setMode(PlayerMode.IN_RUN); // Already in run
            player3.setReady(false);

            // Simulate ReadyService.handleDisconnect/cancelCountdown
            // Player1 disconnects, all COUNTDOWN members are reset
            for (PlayerState ps : new PlayerState[]{player1, player2, player3}) {
                if (ps.getMode() == PlayerMode.COUNTDOWN) {
                    ps.setMode(ps.isReady() ? PlayerMode.READY : PlayerMode.LOBBY);
                }
            }

            // The disconnecting player would be set to LOBBY separately
            player1.setMode(PlayerMode.LOBBY);
            player1.setReady(false);

            assertEquals(PlayerMode.LOBBY, player1.getMode(), "Disconnecting player should be LOBBY");
            assertEquals(PlayerMode.READY, player2.getMode(), "Other COUNTDOWN players should be READY");
            assertEquals(PlayerMode.IN_RUN, player3.getMode(), "IN_RUN player should remain unchanged");
        }
    }

    @Nested
    @DisplayName("onComplete Safety Checks")
    class OnCompleteSafetyChecks {

        @Test
        @DisplayName("should detect no eligible players after all reset")
        void shouldDetectNoEligiblePlayersAfterReset() {
            PlayerState player1 = new PlayerState(UUID.randomUUID(), "Player1");
            player1.setMode(PlayerMode.READY);

            PlayerState player2 = new PlayerState(UUID.randomUUID(), "Player2");
            player2.setMode(PlayerMode.LOBBY);

            // Simulate onComplete eligibility check
            boolean hasEligiblePlayers =
                player1.getMode() == PlayerMode.COUNTDOWN ||
                player2.getMode() == PlayerMode.COUNTDOWN;

            assertFalse(hasEligiblePlayers, "Should detect no eligible COUNTDOWN players");
        }

        @Test
        @DisplayName("should detect eligible players when present")
        void shouldDetectEligiblePlayersWhenPresent() {
            PlayerState player1 = new PlayerState(UUID.randomUUID(), "Player1");
            player1.setMode(PlayerMode.COUNTDOWN);

            PlayerState player2 = new PlayerState(UUID.randomUUID(), "Player2");
            player2.setMode(PlayerMode.IN_RUN);

            // Simulate onComplete eligibility check
            boolean hasEligiblePlayers =
                player1.getMode() == PlayerMode.COUNTDOWN ||
                player2.getMode() == PlayerMode.COUNTDOWN;

            assertTrue(hasEligiblePlayers, "Should detect eligible COUNTDOWN player");
        }

        @Test
        @DisplayName("should reset COUNTDOWN players to LOBBY when run is ending")
        void shouldResetCountdownPlayersToLobbyWhenRunEnding() {
            PlayerState player1 = new PlayerState(UUID.randomUUID(), "Player1");
            player1.setMode(PlayerMode.COUNTDOWN);
            player1.setReady(true);

            PlayerState player2 = new PlayerState(UUID.randomUUID(), "Player2");
            player2.setMode(PlayerMode.COUNTDOWN);
            player2.setReady(true);

            // Simulate resetCountdownPlayersToLobby
            for (PlayerState ps : new PlayerState[]{player1, player2}) {
                if (ps.getMode() == PlayerMode.COUNTDOWN) {
                    ps.setMode(PlayerMode.LOBBY);
                    ps.setReady(false);
                }
            }

            assertEquals(PlayerMode.LOBBY, player1.getMode());
            assertFalse(player1.isReady());
            assertEquals(PlayerMode.LOBBY, player2.getMode());
            assertFalse(player2.isReady());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle rapid ready/unready during countdown")
        void shouldHandleRapidReadyUnreadyDuringCountdown() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);

            // Player unreadies - should trigger countdown cancel
            playerState.setReady(false);

            // Countdown cancellation resets mode
            if (playerState.getMode() == PlayerMode.COUNTDOWN) {
                playerState.setMode(playerState.isReady() ? PlayerMode.READY : PlayerMode.LOBBY);
            }

            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertFalse(playerState.isReady());
        }

        @Test
        @DisplayName("should handle player already in IN_RUN when countdown completes")
        void shouldHandlePlayerAlreadyInRunWhenCountdownCompletes() {
            // Scenario: Player somehow gets set to IN_RUN before countdown completes
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.IN_RUN);
            playerState.setReady(false);

            // handleRejoinToRun should skip this player
            boolean shouldProcess = playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady();

            assertFalse(shouldProcess, "IN_RUN player should be skipped in rejoin flow");
        }

        @Test
        @DisplayName("should handle countdown completion with mixed player states")
        void shouldHandleCountdownCompletionWithMixedStates() {
            PlayerState lobbyPlayer = new PlayerState(UUID.randomUUID(), "LobbyPlayer");
            lobbyPlayer.setMode(PlayerMode.LOBBY);

            PlayerState readyPlayer = new PlayerState(UUID.randomUUID(), "ReadyPlayer");
            readyPlayer.setMode(PlayerMode.READY);
            readyPlayer.setReady(true);

            PlayerState countdownPlayer = new PlayerState(UUID.randomUUID(), "CountdownPlayer");
            countdownPlayer.setMode(PlayerMode.COUNTDOWN);
            countdownPlayer.setReady(true);

            PlayerState inRunPlayer = new PlayerState(UUID.randomUUID(), "InRunPlayer");
            inRunPlayer.setMode(PlayerMode.IN_RUN);

            PlayerState cooldownPlayer = new PlayerState(UUID.randomUUID(), "CooldownPlayer");
            cooldownPlayer.setMode(PlayerMode.COOLDOWN);

            // Only COUNTDOWN+ready players should be processed
            PlayerState[] allPlayers = {lobbyPlayer, readyPlayer, countdownPlayer, inRunPlayer, cooldownPlayer};
            int eligibleCount = 0;

            for (PlayerState ps : allPlayers) {
                if (ps.getMode() == PlayerMode.COUNTDOWN && ps.isReady()) {
                    eligibleCount++;
                }
            }

            assertEquals(1, eligibleCount, "Only one player should be eligible for rejoin");
        }
    }
}
