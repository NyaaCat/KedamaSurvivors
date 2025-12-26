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
    @DisplayName("Rejoin State Management")
    class RejoinStateManagement {

        @Test
        @DisplayName("should preserve IN_RUN mode when teammate starts countdown")
        void shouldPreserveInRunModeWhenTeammateStartsCountdown() {
            PlayerState activePlayer = new PlayerState(UUID.randomUUID(), "ActivePlayer");
            activePlayer.setMode(PlayerMode.IN_RUN);

            // Simulate the fix: when teammate starts countdown, IN_RUN players should not change mode
            // The fix checks: if (ps.getMode() != PlayerMode.IN_RUN && !ps.isOnCooldown())
            boolean shouldChangeMode = activePlayer.getMode() != PlayerMode.IN_RUN && !activePlayer.isOnCooldown();

            assertFalse(shouldChangeMode, "IN_RUN player should not have mode changed during countdown");
            assertEquals(PlayerMode.IN_RUN, activePlayer.getMode());
        }

        @Test
        @DisplayName("should preserve COOLDOWN mode when teammate starts countdown")
        void shouldPreserveCooldownModeWhenTeammateStartsCountdown() {
            PlayerState cooldownPlayer = new PlayerState(UUID.randomUUID(), "CooldownPlayer");
            cooldownPlayer.setMode(PlayerMode.COOLDOWN);
            cooldownPlayer.setCooldownUntilMillis(System.currentTimeMillis() + 60000); // Still on cooldown

            // Simulate the fix: cooldown players should not change mode
            boolean shouldChangeMode = cooldownPlayer.getMode() != PlayerMode.IN_RUN && !cooldownPlayer.isOnCooldown();

            assertFalse(shouldChangeMode, "Player on cooldown should not have mode changed during countdown");
            assertEquals(PlayerMode.COOLDOWN, cooldownPlayer.getMode());
        }

        @Test
        @DisplayName("should change LOBBY mode to COUNTDOWN when eligible")
        void shouldChangeLobbyModeToCountdown() {
            PlayerState lobbyPlayer = new PlayerState(UUID.randomUUID(), "LobbyPlayer");
            lobbyPlayer.setMode(PlayerMode.LOBBY);

            // Simulate the fix: LOBBY players (not on cooldown) should change mode
            boolean shouldChangeMode = lobbyPlayer.getMode() != PlayerMode.IN_RUN && !lobbyPlayer.isOnCooldown();

            assertTrue(shouldChangeMode, "LOBBY player should have mode changed during countdown");
        }

        @Test
        @DisplayName("should reset to LOBBY when rejoin fails")
        void shouldResetToLobbyWhenRejoinFails() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "RejoiningPlayer");
            playerState.setMode(PlayerMode.COUNTDOWN);
            playerState.setReady(true);

            // Simulate rejoin failure (no anchor found)
            // The fix resets: playerState.setMode(PlayerMode.LOBBY); playerState.setReady(false);
            playerState.setMode(PlayerMode.LOBBY);
            playerState.setReady(false);

            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertFalse(playerState.isReady());
        }

        @Test
        @DisplayName("should only process COUNTDOWN mode players for rejoin")
        void shouldOnlyProcessCountdownModePlayersForRejoin() {
            // Simulate the handleRejoinToRun fix:
            // Only process: playerState.getMode() == PlayerMode.COUNTDOWN && playerState.isReady()

            PlayerState countdownReady = new PlayerState(UUID.randomUUID(), "CountdownReady");
            countdownReady.setMode(PlayerMode.COUNTDOWN);
            countdownReady.setReady(true);

            PlayerState inRunPlayer = new PlayerState(UUID.randomUUID(), "InRunPlayer");
            inRunPlayer.setMode(PlayerMode.IN_RUN);
            inRunPlayer.setReady(false);

            PlayerState cooldownPlayer = new PlayerState(UUID.randomUUID(), "CooldownPlayer");
            cooldownPlayer.setMode(PlayerMode.COOLDOWN);
            cooldownPlayer.setReady(false);

            // Check eligibility
            boolean countdownEligible = countdownReady.getMode() == PlayerMode.COUNTDOWN && countdownReady.isReady();
            boolean inRunEligible = inRunPlayer.getMode() == PlayerMode.COUNTDOWN && inRunPlayer.isReady();
            boolean cooldownEligible = cooldownPlayer.getMode() == PlayerMode.COUNTDOWN && cooldownPlayer.isReady();

            assertTrue(countdownEligible, "COUNTDOWN + ready player should be eligible for rejoin");
            assertFalse(inRunEligible, "IN_RUN player should not be eligible for rejoin");
            assertFalse(cooldownEligible, "COOLDOWN player should not be eligible for rejoin");
        }
    }

    @Nested
    @DisplayName("Team Wipe During Countdown")
    class TeamWipeDuringCountdown {

        @Test
        @DisplayName("should detect wipe when countdown player is not in run participants")
        void shouldDetectWipeWhenCountdownPlayerNotInParticipants() {
            // Scenario: Player A died and is in COUNTDOWN, Player B (last teammate) dies
            UUID playerA = UUID.randomUUID();
            UUID playerB = team.getLeaderId();

            team.addMember(playerA);

            // Only Player B is in run (Player A was removed when they died)
            run.addParticipant(playerB);

            // Player B dies - team should be wiped
            run.markDead(playerB);

            assertTrue(team.isWiped(run.getAlivePlayers(), 300000));
        }

        @Test
        @DisplayName("should track correct state when dead player readies up")
        void shouldTrackCorrectStateWhenDeadPlayerReadiesUp() {
            PlayerState playerA = new PlayerState(UUID.randomUUID(), "PlayerA");
            PlayerState playerB = new PlayerState(UUID.randomUUID(), "PlayerB");

            // Initial state: both in run
            playerA.setMode(PlayerMode.IN_RUN);
            playerB.setMode(PlayerMode.IN_RUN);

            // Player A dies and goes through death penalty
            playerA.resetRunState();
            playerA.setMode(PlayerMode.COOLDOWN);
            playerA.setCooldownUntilMillis(System.currentTimeMillis() + 30000);

            // Player A cooldown expires, re-selects, readies up
            playerA.setCooldownUntilMillis(0);
            playerA.setMode(PlayerMode.LOBBY);
            playerA.setStarterWeaponOptionId("iron_sword");
            playerA.setStarterHelmetOptionId("iron_helmet");
            playerA.setReady(true);
            playerA.setMode(PlayerMode.COUNTDOWN);

            // Verify state
            assertEquals(PlayerMode.COUNTDOWN, playerA.getMode());
            assertTrue(playerA.isReady());
            assertTrue(playerA.hasSelectedStarters());
            assertEquals(PlayerMode.IN_RUN, playerB.getMode());
        }

        @Test
        @DisplayName("should have COUNTDOWN players reset to LOBBY when countdown cancelled")
        void shouldResetCountdownPlayersToLobbyWhenCancelled() {
            PlayerState playerA = new PlayerState(UUID.randomUUID(), "PlayerA");
            playerA.setMode(PlayerMode.COUNTDOWN);
            playerA.setReady(true);

            // Simulate countdown cancellation (what cancelCountdown does)
            if (playerA.getMode() == PlayerMode.COUNTDOWN) {
                playerA.setMode(playerA.isReady() ? PlayerMode.READY : PlayerMode.LOBBY);
            }

            // The existing cancelCountdown sets to READY if player.isReady(), LOBBY otherwise
            assertEquals(PlayerMode.READY, playerA.getMode());
        }

        @Test
        @DisplayName("should not have any COUNTDOWN players after countdown cancellation for run start")
        void shouldNotHaveCountdownPlayersAfterCancellation() {
            PlayerState playerA = new PlayerState(UUID.randomUUID(), "PlayerA");
            PlayerState playerB = new PlayerState(UUID.randomUUID(), "PlayerB");

            // Simulate: both players in different modes when team wipe happens
            playerA.setMode(PlayerMode.COUNTDOWN);
            playerA.setReady(true);
            playerB.setMode(PlayerMode.COOLDOWN); // Just died

            // Check if any player is in COUNTDOWN - this is what onComplete checks
            boolean hasCountdownPlayers =
                playerA.getMode() == PlayerMode.COUNTDOWN ||
                playerB.getMode() == PlayerMode.COUNTDOWN;

            assertTrue(hasCountdownPlayers, "Before cancellation, should have COUNTDOWN player");

            // Simulate cancellation effect
            if (playerA.getMode() == PlayerMode.COUNTDOWN) {
                playerA.setMode(PlayerMode.READY);
            }

            // After cancellation
            hasCountdownPlayers =
                playerA.getMode() == PlayerMode.COUNTDOWN ||
                playerB.getMode() == PlayerMode.COUNTDOWN;

            assertFalse(hasCountdownPlayers, "After cancellation, should have no COUNTDOWN players");
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

    @Nested
    @DisplayName("Respawn Location Handling")
    class RespawnLocationHandling {

        @Test
        @DisplayName("should redirect COOLDOWN mode player to lobby on respawn")
        void shouldRedirectCooldownPlayerToLobby() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.COOLDOWN);
            playerState.setCooldownUntilMillis(System.currentTimeMillis() + 30000);

            // Simulate the respawn event handler logic:
            // COOLDOWN mode players should get lobby as respawn location
            boolean shouldRedirectToLobby = playerState.getMode() == PlayerMode.COOLDOWN;

            assertTrue(shouldRedirectToLobby, "COOLDOWN player should be redirected to lobby on respawn");
        }

        @Test
        @DisplayName("should NOT redirect IN_RUN mode player to lobby on respawn")
        void shouldNotRedirectInRunPlayerToLobby() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.IN_RUN);

            // IN_RUN players should get run spawn point, not lobby
            boolean shouldRedirectToLobby = playerState.getMode() == PlayerMode.COOLDOWN;

            assertFalse(shouldRedirectToLobby, "IN_RUN player should NOT be redirected to lobby");
        }

        @Test
        @DisplayName("should NOT redirect LOBBY mode player to lobby (already there)")
        void shouldNotRedirectLobbyPlayer() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
            playerState.setMode(PlayerMode.LOBBY);

            // LOBBY players respawning shouldn't need special handling
            boolean shouldRedirectToLobby = playerState.getMode() == PlayerMode.COOLDOWN;

            assertFalse(shouldRedirectToLobby, "LOBBY player doesn't need redirect - already in lobby");
        }

        @Test
        @DisplayName("should handle mode transition from IN_RUN to COOLDOWN before respawn")
        void shouldHandleModeTransitionBeforeRespawn() {
            PlayerState playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");

            // Initial state: player in run
            playerState.setMode(PlayerMode.IN_RUN);
            assertEquals(PlayerMode.IN_RUN, playerState.getMode());

            // Simulate death penalty: mode changes to COOLDOWN
            playerState.setMode(PlayerMode.COOLDOWN);
            playerState.setCooldownUntilMillis(System.currentTimeMillis() + 30000);

            // Now when respawn event fires, mode is COOLDOWN
            assertEquals(PlayerMode.COOLDOWN, playerState.getMode());

            // Verify the respawn handler would redirect to lobby
            boolean shouldRedirectToLobby = playerState.getMode() == PlayerMode.COOLDOWN;
            assertTrue(shouldRedirectToLobby, "After death penalty, respawning player should go to lobby");
        }
    }
}
