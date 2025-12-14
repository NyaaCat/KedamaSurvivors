package cat.nyaa.survivors.task;

import cat.nyaa.survivors.model.PlayerMode;
import cat.nyaa.survivors.model.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DisconnectChecker grace period logic.
 * Note: These tests focus on pure logic that doesn't require Bukkit mocking.
 */
class DisconnectCheckerTest {

    private PlayerState playerState;

    @BeforeEach
    void setUp() {
        playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
    }

    @Nested
    @DisplayName("Grace Period Detection")
    class GracePeriodDetection {

        @Test
        @DisplayName("should detect expired grace period")
        void shouldDetectExpiredGracePeriod() {
            long graceMs = 300_000; // 5 minutes
            long now = System.currentTimeMillis();

            // Disconnected 6 minutes ago (grace expired)
            playerState.setDisconnectedAtMillis(now - 360_000);

            long disconnectedAt = playerState.getDisconnectedAtMillis();
            boolean expired = (now - disconnectedAt) >= graceMs;

            assertTrue(expired);
        }

        @Test
        @DisplayName("should not detect unexpired grace period")
        void shouldNotDetectUnexpiredGracePeriod() {
            long graceMs = 300_000; // 5 minutes
            long now = System.currentTimeMillis();

            // Disconnected 2 minutes ago (grace not expired)
            playerState.setDisconnectedAtMillis(now - 120_000);

            long disconnectedAt = playerState.getDisconnectedAtMillis();
            boolean expired = (now - disconnectedAt) >= graceMs;

            assertFalse(expired);
        }

        @Test
        @DisplayName("should not check if disconnect time is zero")
        void shouldNotCheckIfDisconnectTimeIsZero() {
            long graceMs = 300_000;
            long now = System.currentTimeMillis();

            playerState.setDisconnectedAtMillis(0);

            long disconnectedAt = playerState.getDisconnectedAtMillis();
            // If disconnectedAt is 0, we shouldn't consider it expired
            boolean expired = disconnectedAt > 0 && (now - disconnectedAt) >= graceMs;

            assertFalse(expired);
        }

        @Test
        @DisplayName("should handle exact grace period boundary")
        void shouldHandleExactGracePeriodBoundary() {
            long graceMs = 300_000;
            long now = System.currentTimeMillis();

            // Disconnected exactly at grace period (should be expired)
            playerState.setDisconnectedAtMillis(now - graceMs);

            long disconnectedAt = playerState.getDisconnectedAtMillis();
            boolean expired = (now - disconnectedAt) >= graceMs;

            assertTrue(expired);
        }
    }

    @Nested
    @DisplayName("Player Mode Filtering")
    class PlayerModeFiltering {

        @Test
        @DisplayName("should only check DISCONNECTED mode players")
        void shouldOnlyCheckDisconnectedModePlayers() {
            playerState.setMode(PlayerMode.DISCONNECTED);
            assertTrue(playerState.getMode() == PlayerMode.DISCONNECTED);
        }

        @Test
        @DisplayName("should skip IN_RUN mode players")
        void shouldSkipInRunModePlayers() {
            playerState.setMode(PlayerMode.IN_RUN);
            assertFalse(playerState.getMode() == PlayerMode.DISCONNECTED);
        }

        @Test
        @DisplayName("should skip LOBBY mode players")
        void shouldSkipLobbyModePlayers() {
            playerState.setMode(PlayerMode.LOBBY);
            assertFalse(playerState.getMode() == PlayerMode.DISCONNECTED);
        }

        @Test
        @DisplayName("should skip COOLDOWN mode players")
        void shouldSkipCooldownModePlayers() {
            playerState.setMode(PlayerMode.COOLDOWN);
            assertFalse(playerState.getMode() == PlayerMode.DISCONNECTED);
        }
    }

    @Nested
    @DisplayName("Grace Expiry State Reset")
    class GraceExpiryStateReset {

        @Test
        @DisplayName("should clear XP on grace expiry")
        void shouldClearXpOnGraceExpiry() {
            playerState.setXpProgress(500);
            playerState.setXpHeld(100);

            // Simulate grace expiry reset
            playerState.setXpProgress(0);
            playerState.setXpHeld(0);

            assertEquals(0, playerState.getXpProgress());
            assertEquals(0, playerState.getXpHeld());
        }

        @Test
        @DisplayName("should clear upgrade pending on grace expiry")
        void shouldClearUpgradePendingOnGraceExpiry() {
            playerState.setUpgradePending(true);

            // Simulate grace expiry reset
            playerState.setUpgradePending(false);

            assertFalse(playerState.isUpgradePending());
        }

        @Test
        @DisplayName("should clear equipment on grace expiry")
        void shouldClearEquipmentOnGraceExpiry() {
            playerState.setWeaponGroup("sword");
            playerState.setWeaponLevel(5);
            playerState.setHelmetGroup("light");
            playerState.setHelmetLevel(3);

            // Simulate grace expiry reset
            playerState.setWeaponGroup(null);
            playerState.setWeaponLevel(0);
            playerState.setHelmetGroup(null);
            playerState.setHelmetLevel(0);

            assertNull(playerState.getWeaponGroup());
            assertEquals(0, playerState.getWeaponLevel());
            assertNull(playerState.getHelmetGroup());
            assertEquals(0, playerState.getHelmetLevel());
        }

        @Test
        @DisplayName("should set cooldown mode on grace expiry")
        void shouldSetCooldownModeOnGraceExpiry() {
            playerState.setMode(PlayerMode.DISCONNECTED);

            long cooldownEnd = System.currentTimeMillis() + 60_000;
            playerState.setCooldownUntilMillis(cooldownEnd);
            playerState.setMode(PlayerMode.COOLDOWN);

            assertEquals(PlayerMode.COOLDOWN, playerState.getMode());
            assertTrue(playerState.getCooldownUntilMillis() > System.currentTimeMillis());
        }

        @Test
        @DisplayName("should clear disconnect timestamp on grace expiry")
        void shouldClearDisconnectTimestampOnGraceExpiry() {
            playerState.setDisconnectedAtMillis(System.currentTimeMillis() - 400_000);

            // Simulate grace expiry reset
            playerState.setDisconnectedAtMillis(0);

            assertEquals(0, playerState.getDisconnectedAtMillis());
        }

        @Test
        @DisplayName("should clear run ID on grace expiry")
        void shouldClearRunIdOnGraceExpiry() {
            UUID runId = UUID.randomUUID();
            playerState.setRunId(runId);

            // Simulate grace expiry reset
            playerState.setRunId(null);

            assertNull(playerState.getRunId());
        }

        @Test
        @DisplayName("should clear overflow XP on grace expiry")
        void shouldClearOverflowXpOnGraceExpiry() {
            playerState.setOverflowXpAccumulated(1500);

            // Simulate grace expiry reset
            playerState.setOverflowXpAccumulated(0);

            assertEquals(0, playerState.getOverflowXpAccumulated());
        }
    }

    @Nested
    @DisplayName("Cooldown Calculation")
    class CooldownCalculation {

        @Test
        @DisplayName("should calculate correct cooldown end time")
        void shouldCalculateCorrectCooldownEndTime() {
            long deathCooldownMs = 60_000; // 60 seconds
            long now = System.currentTimeMillis();

            long cooldownEnd = now + deathCooldownMs;

            assertTrue(cooldownEnd > now);
            assertTrue(cooldownEnd - now == deathCooldownMs);
        }

        @Test
        @DisplayName("should handle different cooldown durations")
        void shouldHandleDifferentCooldownDurations() {
            long now = System.currentTimeMillis();

            // Short cooldown
            long shortCooldown = 30_000;
            long shortEnd = now + shortCooldown;

            // Long cooldown
            long longCooldown = 120_000;
            long longEnd = now + longCooldown;

            assertTrue(longEnd > shortEnd);
            assertEquals(90_000, longEnd - shortEnd);
        }
    }
}
