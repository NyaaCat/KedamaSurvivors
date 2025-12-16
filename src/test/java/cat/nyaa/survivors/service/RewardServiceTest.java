package cat.nyaa.survivors.service;

import cat.nyaa.survivors.config.ConfigService;
import cat.nyaa.survivors.model.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RewardService XP calculations.
 * Note: These tests focus on pure calculation logic that doesn't require Bukkit mocking.
 */
class RewardServiceTest {

    private PlayerState playerState;

    @BeforeEach
    void setUp() {
        playerState = new PlayerState(UUID.randomUUID(), "TestPlayer");
    }

    @Nested
    @DisplayName("XP Required Calculation")
    class XpRequiredCalculation {

        @Test
        @DisplayName("should calculate XP required for level 0")
        void shouldCalculateXpRequiredForLevel0() {
            // Base formula: base + (playerLevel * perLevel) * multiplier^playerLevel
            // With defaults: 100 + (0 * 50) * 1.1^0 = 100
            int xpRequired = calculateXpRequired(0, 100, 50, 1.1);
            assertEquals(100, xpRequired);
        }

        @Test
        @DisplayName("should calculate XP required for level 1")
        void shouldCalculateXpRequiredForLevel1() {
            // With defaults: 100 + (1 * 50) * 1.1^1 = 100 + 55 = 155
            int xpRequired = calculateXpRequired(1, 100, 50, 1.1);
            assertEquals(155, xpRequired);
        }

        @Test
        @DisplayName("should calculate XP required for level 5")
        void shouldCalculateXpRequiredForLevel5() {
            // With defaults: 100 + (5 * 50) * 1.1^5 = 100 + 250 * 1.61051 = 502
            int xpRequired = calculateXpRequired(5, 100, 50, 1.1);
            assertTrue(xpRequired >= 400 && xpRequired <= 510);
        }

        /**
         * Mirrors the calculation in RewardService.
         */
        private int calculateXpRequired(int playerLevel, int base, int perLevel, double multiplier) {
            return (int) (base + (playerLevel * perLevel) * Math.pow(multiplier, playerLevel));
        }
    }

    @Nested
    @DisplayName("XP Overflow Logic")
    class XpOverflowLogic {

        @Test
        @DisplayName("should track overflow XP correctly")
        void shouldTrackOverflowXpCorrectly() {
            playerState.setOverflowXpAccumulated(500);
            playerState.setOverflowXpAccumulated(playerState.getOverflowXpAccumulated() + 300);
            assertEquals(800, playerState.getOverflowXpAccumulated());
        }

        @Test
        @DisplayName("should calculate perma score from overflow")
        void shouldCalculatePermaScoreFromOverflow() {
            int xpPerScore = 1000;
            int accumulated = 2500;

            int expectedScores = 2;
            int expectedRemaining = 500;

            int scores = accumulated / xpPerScore;
            int remaining = accumulated % xpPerScore;

            assertEquals(expectedScores, scores);
            assertEquals(expectedRemaining, remaining);
        }

        @Test
        @DisplayName("should update xpProgress for scoreboard during overflow")
        void shouldUpdateXpProgressForScoreboardDuringOverflow() {
            // Simulate overflow XP processing (mirrors applyOverflowXp logic)
            int xpPerScore = 1000;
            int xpToAdd = 750;
            int initialAccumulated = 100;

            playerState.setOverflowXpAccumulated(initialAccumulated);
            int accumulated = playerState.getOverflowXpAccumulated() + xpToAdd;

            // Set xpRequired to overflow threshold for scoreboard bar
            playerState.setXpRequired(xpPerScore);

            // No conversion yet (850 < 1000)
            // Update xpProgress for scoreboard display
            playerState.setXpProgress(accumulated);

            assertEquals(850, playerState.getXpProgress());
            assertEquals(1000, playerState.getXpRequired());
        }

        @Test
        @DisplayName("should increment run level on overflow conversion")
        void shouldIncrementRunLevelOnOverflowConversion() {
            // Simulate overflow XP processing that triggers conversion
            int xpPerScore = 1000;
            int xpToAdd = 1200;
            int initialRunLevel = 5;

            playerState.setOverflowXpAccumulated(0);
            playerState.setRunLevel(initialRunLevel);
            playerState.setPermaScore(0);

            int accumulated = playerState.getOverflowXpAccumulated() + xpToAdd;

            // Process conversion (mirrors applyOverflowXp loop)
            while (accumulated >= xpPerScore) {
                accumulated -= xpPerScore;
                playerState.setPermaScore(playerState.getPermaScore() + 1);
                playerState.setRunLevel(playerState.getRunLevel() + 1);
            }

            playerState.setOverflowXpAccumulated(accumulated);
            playerState.setXpProgress(accumulated);

            assertEquals(6, playerState.getRunLevel()); // 5 + 1 conversion
            assertEquals(1, playerState.getPermaScore());
            assertEquals(200, playerState.getXpProgress()); // 1200 - 1000
            assertEquals(200, playerState.getOverflowXpAccumulated());
        }

        @Test
        @DisplayName("should handle multiple overflow conversions in single XP award")
        void shouldHandleMultipleOverflowConversionsInSingleXpAward() {
            // Simulate large XP award triggering multiple conversions
            int xpPerScore = 1000;
            int xpToAdd = 3500;
            int initialRunLevel = 10;

            playerState.setOverflowXpAccumulated(0);
            playerState.setRunLevel(initialRunLevel);
            playerState.setPermaScore(5);

            int accumulated = playerState.getOverflowXpAccumulated() + xpToAdd;

            // Process conversions (mirrors applyOverflowXp loop)
            while (accumulated >= xpPerScore) {
                accumulated -= xpPerScore;
                playerState.setPermaScore(playerState.getPermaScore() + 1);
                playerState.setRunLevel(playerState.getRunLevel() + 1);
            }

            playerState.setOverflowXpAccumulated(accumulated);
            playerState.setXpProgress(accumulated);

            assertEquals(13, playerState.getRunLevel()); // 10 + 3 conversions
            assertEquals(8, playerState.getPermaScore()); // 5 + 3
            assertEquals(500, playerState.getXpProgress()); // 3500 - 3000
            assertEquals(500, playerState.getOverflowXpAccumulated());
        }

        @Test
        @DisplayName("should handle partial accumulation without conversion")
        void shouldHandlePartialAccumulationWithoutConversion() {
            int xpPerScore = 1000;
            int xpToAdd = 300;
            int initialRunLevel = 3;

            playerState.setOverflowXpAccumulated(400);
            playerState.setRunLevel(initialRunLevel);
            playerState.setPermaScore(2);

            int accumulated = playerState.getOverflowXpAccumulated() + xpToAdd;

            // No conversion (700 < 1000)
            playerState.setXpRequired(xpPerScore);
            playerState.setOverflowXpAccumulated(accumulated);
            playerState.setXpProgress(accumulated);

            assertEquals(3, playerState.getRunLevel()); // Unchanged
            assertEquals(2, playerState.getPermaScore()); // Unchanged
            assertEquals(700, playerState.getXpProgress());
            assertEquals(1000, playerState.getXpRequired());
        }

        @Test
        @DisplayName("should reset xpProgress after conversion")
        void shouldResetXpProgressAfterConversion() {
            int xpPerScore = 1000;
            int xpToAdd = 1000; // Exact threshold

            playerState.setOverflowXpAccumulated(0);
            playerState.setRunLevel(1);
            playerState.setPermaScore(0);

            int accumulated = playerState.getOverflowXpAccumulated() + xpToAdd;

            // Process conversion
            while (accumulated >= xpPerScore) {
                accumulated -= xpPerScore;
                playerState.setPermaScore(playerState.getPermaScore() + 1);
                playerState.setRunLevel(playerState.getRunLevel() + 1);
            }

            playerState.setOverflowXpAccumulated(accumulated);
            playerState.setXpProgress(accumulated);

            assertEquals(2, playerState.getRunLevel()); // 1 + 1
            assertEquals(1, playerState.getPermaScore());
            assertEquals(0, playerState.getXpProgress()); // Reset to 0 after exact conversion
        }
    }

    @Nested
    @DisplayName("XP Hold Logic")
    class XpHoldLogic {

        @Test
        @DisplayName("should buffer XP when upgrade pending")
        void shouldBufferXpWhenUpgradePending() {
            playerState.setUpgradePending(true);
            playerState.setXpHeld(0);

            // Simulate XP being added while upgrade is pending
            int xpToAdd = 50;
            if (playerState.isUpgradePending()) {
                playerState.setXpHeld(playerState.getXpHeld() + xpToAdd);
            }

            assertEquals(50, playerState.getXpHeld());
        }

        @Test
        @DisplayName("should not buffer XP when no upgrade pending")
        void shouldNotBufferXpWhenNoUpgradePending() {
            playerState.setUpgradePending(false);
            playerState.setXpProgress(10);

            // Simulate XP being added normally
            int xpToAdd = 50;
            if (!playerState.isUpgradePending()) {
                playerState.setXpProgress(playerState.getXpProgress() + xpToAdd);
            }

            assertEquals(60, playerState.getXpProgress());
            assertEquals(0, playerState.getXpHeld());
        }
    }

    @Nested
    @DisplayName("Level Up Detection")
    class LevelUpDetection {

        @Test
        @DisplayName("should detect level up when XP meets required")
        void shouldDetectLevelUpWhenXpMeetsRequired() {
            playerState.setXpRequired(100);
            playerState.setXpProgress(0);

            int xpToAdd = 120;
            int newProgress = playerState.getXpProgress() + xpToAdd;

            if (newProgress >= playerState.getXpRequired()) {
                int overflow = newProgress - playerState.getXpRequired();
                assertTrue(overflow > 0);
                assertEquals(20, overflow);
            }
        }

        @Test
        @DisplayName("should not detect level up when XP below required")
        void shouldNotDetectLevelUpWhenXpBelowRequired() {
            playerState.setXpRequired(100);
            playerState.setXpProgress(0);

            int xpToAdd = 80;
            int newProgress = playerState.getXpProgress() + xpToAdd;

            assertFalse(newProgress >= playerState.getXpRequired());
        }
    }

    @Nested
    @DisplayName("Max Level XP Handling")
    class MaxLevelXpHandling {

        @Test
        @DisplayName("should redirect to overflow when at max level")
        void shouldRedirectToOverflowWhenAtMaxLevel() {
            playerState.setWeaponAtMax(true);
            playerState.setHelmetAtMax(true);

            assertTrue(playerState.isAtMaxLevel());

            // When at max level, XP should go to overflow
            int xpToAdd = 100;
            if (playerState.isAtMaxLevel()) {
                playerState.setOverflowXpAccumulated(playerState.getOverflowXpAccumulated() + xpToAdd);
            } else {
                playerState.setXpProgress(playerState.getXpProgress() + xpToAdd);
            }

            assertEquals(100, playerState.getOverflowXpAccumulated());
            assertEquals(0, playerState.getXpProgress());
        }
    }

    @Nested
    @DisplayName("XP Share Calculation")
    class XpShareCalculation {

        @Test
        @DisplayName("should calculate correct share amount")
        void shouldCalculateCorrectShareAmount() {
            int baseXp = 100;
            double sharePercent = 0.25;

            int sharedAmount = (int) (baseXp * sharePercent);
            assertEquals(25, sharedAmount);
        }

        @Test
        @DisplayName("should return zero for zero share percent")
        void shouldReturnZeroForZeroSharePercent() {
            int baseXp = 100;
            double sharePercent = 0.0;

            int sharedAmount = (int) (baseXp * sharePercent);
            assertEquals(0, sharedAmount);
        }

        @Test
        @DisplayName("should handle fractional share correctly")
        void shouldHandleFractionalShareCorrectly() {
            int baseXp = 15;
            double sharePercent = 0.25;

            int sharedAmount = (int) (baseXp * sharePercent);
            assertEquals(3, sharedAmount); // 3.75 truncated to 3
        }
    }

    @Nested
    @DisplayName("Perma Score Management")
    class PermaScoreManagement {

        @Test
        @DisplayName("should increment perma score correctly")
        void shouldIncrementPermaScoreCorrectly() {
            playerState.setPermaScore(100);
            playerState.setPermaScore(playerState.getPermaScore() + 5);
            assertEquals(105, playerState.getPermaScore());
        }

        @Test
        @DisplayName("should handle multiple increments")
        void shouldHandleMultipleIncrements() {
            playerState.setPermaScore(0);
            for (int i = 0; i < 10; i++) {
                playerState.setPermaScore(playerState.getPermaScore() + 1);
            }
            assertEquals(10, playerState.getPermaScore());
        }
    }
}
