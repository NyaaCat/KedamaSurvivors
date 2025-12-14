package cat.nyaa.survivors.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlayerState.
 */
class PlayerStateTest {

    private UUID playerId;
    private PlayerState playerState;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        playerState = new PlayerState(playerId, "TestPlayer");
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        @DisplayName("should initialize with correct UUID and name")
        void shouldInitializeWithCorrectUuidAndName() {
            assertEquals(playerId, playerState.getUuid());
            assertEquals("TestPlayer", playerState.getName());
        }

        @Test
        @DisplayName("should initialize in LOBBY mode")
        void shouldInitializeInLobbyMode() {
            assertEquals(PlayerMode.LOBBY, playerState.getMode());
        }

        @Test
        @DisplayName("should update name")
        void shouldUpdateName() {
            playerState.setName("NewName");
            assertEquals("NewName", playerState.getName());
        }
    }

    @Nested
    @DisplayName("Player Level Calculation")
    class PlayerLevelCalculation {

        @Test
        @DisplayName("should calculate player level as sum of weapon and helmet levels")
        void shouldCalculatePlayerLevel() {
            playerState.setWeaponLevel(3);
            playerState.setHelmetLevel(2);
            assertEquals(5, playerState.getPlayerLevel());
        }

        @Test
        @DisplayName("should return 0 when levels not set")
        void shouldReturnZeroWhenLevelsNotSet() {
            assertEquals(0, playerState.getPlayerLevel());
        }
    }

    @Nested
    @DisplayName("Max Level Tracking")
    class MaxLevelTracking {

        @Test
        @DisplayName("should not be at max level initially")
        void shouldNotBeAtMaxLevelInitially() {
            assertFalse(playerState.isAtMaxLevel());
        }

        @Test
        @DisplayName("should be at max level when both weapon and helmet are maxed")
        void shouldBeAtMaxLevelWhenBothMaxed() {
            playerState.setWeaponAtMax(true);
            playerState.setHelmetAtMax(true);
            assertTrue(playerState.isAtMaxLevel());
        }

        @Test
        @DisplayName("should not be at max level when only weapon is maxed")
        void shouldNotBeAtMaxLevelWhenOnlyWeaponMaxed() {
            playerState.setWeaponAtMax(true);
            playerState.setHelmetAtMax(false);
            assertFalse(playerState.isAtMaxLevel());
        }
    }

    @Nested
    @DisplayName("Cooldown Management")
    class CooldownManagement {

        @Test
        @DisplayName("should not be on cooldown initially")
        void shouldNotBeOnCooldownInitially() {
            assertFalse(playerState.isOnCooldown());
        }

        @Test
        @DisplayName("should be on cooldown when cooldownUntilMillis is in future")
        void shouldBeOnCooldownWhenInFuture() {
            playerState.setCooldownUntilMillis(System.currentTimeMillis() + 10000);
            assertTrue(playerState.isOnCooldown());
        }

        @Test
        @DisplayName("should not be on cooldown when cooldownUntilMillis is in past")
        void shouldNotBeOnCooldownWhenInPast() {
            playerState.setCooldownUntilMillis(System.currentTimeMillis() - 1000);
            assertFalse(playerState.isOnCooldown());
        }

        @Test
        @DisplayName("should calculate remaining cooldown seconds")
        void shouldCalculateRemainingCooldownSeconds() {
            playerState.setCooldownUntilMillis(System.currentTimeMillis() + 5500);
            long remaining = playerState.getCooldownRemainingSeconds();
            assertTrue(remaining >= 4 && remaining <= 6);
        }
    }

    @Nested
    @DisplayName("Invulnerability")
    class Invulnerability {

        @Test
        @DisplayName("should not be invulnerable initially")
        void shouldNotBeInvulnerableInitially() {
            assertFalse(playerState.isInvulnerable());
        }

        @Test
        @DisplayName("should be invulnerable when invulnerableUntilMillis is in future")
        void shouldBeInvulnerableWhenInFuture() {
            playerState.setInvulnerableUntilMillis(System.currentTimeMillis() + 3000);
            assertTrue(playerState.isInvulnerable());
        }
    }

    @Nested
    @DisplayName("Disconnect Grace Period")
    class DisconnectGracePeriod {

        @Test
        @DisplayName("should not be within grace period when not disconnected")
        void shouldNotBeWithinGracePeriodWhenNotDisconnected() {
            assertFalse(playerState.isWithinGracePeriod(300000));
        }

        @Test
        @DisplayName("should be within grace period when recently disconnected")
        void shouldBeWithinGracePeriodWhenRecentlyDisconnected() {
            playerState.setDisconnectedAtMillis(System.currentTimeMillis() - 1000);
            assertTrue(playerState.isWithinGracePeriod(300000)); // 5 min grace
        }

        @Test
        @DisplayName("should not be within grace period when disconnected too long ago")
        void shouldNotBeWithinGracePeriodWhenDisconnectedTooLongAgo() {
            playerState.setDisconnectedAtMillis(System.currentTimeMillis() - 400000);
            assertFalse(playerState.isWithinGracePeriod(300000)); // 5 min grace
        }
    }

    @Nested
    @DisplayName("Starter Selection")
    class StarterSelection {

        @Test
        @DisplayName("should not have selected starters initially")
        void shouldNotHaveSelectedStartersInitially() {
            assertFalse(playerState.hasSelectedStarters());
        }

        @Test
        @DisplayName("should have selected starters when both weapon and helmet selected")
        void shouldHaveSelectedStartersWhenBothSelected() {
            playerState.setStarterWeaponOptionId("sword_basic");
            playerState.setStarterHelmetOptionId("helmet_basic");
            assertTrue(playerState.hasSelectedStarters());
        }

        @Test
        @DisplayName("should not have selected starters when only weapon selected")
        void shouldNotHaveSelectedStartersWhenOnlyWeaponSelected() {
            playerState.setStarterWeaponOptionId("sword_basic");
            assertFalse(playerState.hasSelectedStarters());
        }
    }

    @Nested
    @DisplayName("State Reset")
    class StateReset {

        @Test
        @DisplayName("should reset run state")
        void shouldResetRunState() {
            // Set up some state
            playerState.setXpProgress(50);
            playerState.setXpHeld(100);
            playerState.setUpgradePending(true);
            playerState.setWeaponLevel(3);
            playerState.setHelmetLevel(2);
            playerState.setWeaponAtMax(true);
            playerState.setRunId(UUID.randomUUID());

            // Reset
            playerState.resetRunState();

            // Verify reset
            assertEquals(0, playerState.getXpProgress());
            assertEquals(0, playerState.getXpHeld());
            assertFalse(playerState.isUpgradePending());
            assertEquals(0, playerState.getWeaponLevel());
            assertEquals(0, playerState.getHelmetLevel());
            assertFalse(playerState.isWeaponAtMax());
            assertNull(playerState.getRunId());
        }

        @Test
        @DisplayName("should reset all state")
        void shouldResetAllState() {
            // Set up some state
            playerState.setMode(PlayerMode.IN_RUN);
            playerState.setReady(true);
            playerState.setStarterWeaponOptionId("sword");
            playerState.setCooldownUntilMillis(System.currentTimeMillis() + 5000);
            playerState.setXpProgress(50);

            // Reset
            playerState.resetAll();

            // Verify reset
            assertEquals(PlayerMode.LOBBY, playerState.getMode());
            assertFalse(playerState.isReady());
            assertNull(playerState.getStarterWeaponOptionId());
            assertEquals(0, playerState.getCooldownUntilMillis());
            assertEquals(0, playerState.getXpProgress());
        }
    }
}
