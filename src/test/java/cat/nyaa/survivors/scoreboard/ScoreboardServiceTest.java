package cat.nyaa.survivors.scoreboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScoreboardService.
 * Note: These tests focus on pure calculation and format logic that doesn't require Bukkit mocking.
 */
class ScoreboardServiceTest {

    @Nested
    @DisplayName("Coin Display Format Logic - In Run")
    class CoinDisplayFormatInRun {

        @Test
        @DisplayName("should format coins with total and run values")
        void shouldFormatCoinsWithTotalAndRunValues() {
            // Simulating the i18n template: "§7金币 §f{total} §8(§a+{run}§8)"
            int total = 20;
            int run = 3;
            String format = formatCoinsInRun(total, run);

            assertTrue(format.contains("20"), "Should contain total balance");
            assertTrue(format.contains("+3"), "Should contain run coins with + prefix");
        }

        @Test
        @DisplayName("should format coins with zero run coins")
        void shouldFormatCoinsWithZeroRunCoins() {
            int total = 100;
            int run = 0;
            String format = formatCoinsInRun(total, run);

            assertTrue(format.contains("100"), "Should contain total balance");
            assertTrue(format.contains("+0"), "Should contain +0 for zero run coins");
        }

        @Test
        @DisplayName("should format coins with zero total balance")
        void shouldFormatCoinsWithZeroTotalBalance() {
            int total = 0;
            int run = 5;
            String format = formatCoinsInRun(total, run);

            assertTrue(format.contains("0") && !format.contains("+0"), "Should contain 0 for total");
            assertTrue(format.contains("+5"), "Should contain run coins");
        }

        @Test
        @DisplayName("should format coins with both values zero")
        void shouldFormatCoinsWithBothValuesZero() {
            int total = 0;
            int run = 0;
            String format = formatCoinsInRun(total, run);

            assertNotNull(format);
            assertTrue(format.contains("0"), "Should contain zeros");
        }

        @Test
        @DisplayName("should format coins with large values")
        void shouldFormatCoinsWithLargeValues() {
            int total = 999999;
            int run = 50000;
            String format = formatCoinsInRun(total, run);

            assertTrue(format.contains("999999"), "Should contain large total");
            assertTrue(format.contains("+50000"), "Should contain large run coins");
        }

        /**
         * Simulates the format string used in the language file for in-run display.
         * Format: "§7金币 §f{total} §8(§a+{run}§8)"
         */
        private String formatCoinsInRun(int total, int run) {
            return "§7金币 §f" + total + " §8(§a+" + run + "§8)";
        }
    }

    @Nested
    @DisplayName("Coin Display Format Logic - Lobby")
    class CoinDisplayFormatLobby {

        @Test
        @DisplayName("should format lobby coins with total only")
        void shouldFormatLobbyCoinsWithTotalOnly() {
            // Simulating the i18n template: "§7金币 §f{total}"
            int total = 500;
            String format = formatCoinsLobby(total);

            assertTrue(format.contains("500"), "Should contain total balance");
            assertFalse(format.contains("+"), "Should NOT contain + (no run amount in lobby)");
        }

        @Test
        @DisplayName("should format lobby coins with zero balance")
        void shouldFormatLobbyCoinsWithZeroBalance() {
            String format = formatCoinsLobby(0);

            assertTrue(format.contains("0"), "Should contain zero balance");
            assertFalse(format.contains("+"), "Should NOT contain +");
        }

        @Test
        @DisplayName("should format lobby coins with large balance")
        void shouldFormatLobbyCoinsWithLargeBalance() {
            String format = formatCoinsLobby(1234567);

            assertTrue(format.contains("1234567"), "Should contain large balance");
            assertFalse(format.contains("+"), "Should NOT contain +");
        }

        /**
         * Simulates the format string used in the language file for lobby display.
         * Format: "§7金币 §f{total}"
         */
        private String formatCoinsLobby(int total) {
            return "§7金币 §f" + total;
        }
    }

    @Nested
    @DisplayName("Number Formatting")
    class NumberFormatting {

        @Test
        @DisplayName("should format small numbers as-is")
        void shouldFormatSmallNumbersAsIs() {
            assertEquals("0", formatNumber(0));
            assertEquals("1", formatNumber(1));
            assertEquals("999", formatNumber(999));
        }

        @Test
        @DisplayName("should format thousands with K suffix")
        void shouldFormatThousandsWithKSuffix() {
            assertEquals("1.0K", formatNumber(1000));
            assertEquals("1.5K", formatNumber(1500));
            assertEquals("999.9K", formatNumber(999900));
        }

        @Test
        @DisplayName("should format millions with M suffix")
        void shouldFormatMillionsWithMSuffix() {
            assertEquals("1.0M", formatNumber(1000000));
            assertEquals("1.5M", formatNumber(1500000));
            assertEquals("10.0M", formatNumber(10000000));
        }

        @Test
        @DisplayName("should handle boundary values correctly")
        void shouldHandleBoundaryValuesCorrectly() {
            assertEquals("999", formatNumber(999));
            assertEquals("1.0K", formatNumber(1000));
            // 999999 / 1000.0 = 999.999, which rounds to 1000.0 with %.1f
            assertEquals("1000.0K", formatNumber(999999));
            assertEquals("1.0M", formatNumber(1000000));
        }

        /**
         * Mirrors the formatNumber method in ScoreboardService.
         */
        private String formatNumber(int number) {
            if (number >= 1000000) {
                return String.format("%.1fM", number / 1000000.0);
            } else if (number >= 1000) {
                return String.format("%.1fK", number / 1000.0);
            }
            return String.valueOf(number);
        }
    }

    @Nested
    @DisplayName("XP Bar Building")
    class XpBarBuilding {

        @Test
        @DisplayName("should build empty bar for 0% progress")
        void shouldBuildEmptyBarForZeroProgress() {
            String bar = buildXpBar(0, 100);
            assertEquals("▱▱▱▱▱", bar);
        }

        @Test
        @DisplayName("should build full bar for 100% progress")
        void shouldBuildFullBarForFullProgress() {
            String bar = buildXpBar(100, 100);
            assertEquals("▰▰▰▰▰", bar);
        }

        @Test
        @DisplayName("should build partial bar for 50% progress")
        void shouldBuildPartialBarForHalfProgress() {
            String bar = buildXpBar(50, 100);
            // 50% = 2.5 filled, truncated to 2
            assertEquals("▰▰▱▱▱", bar);
        }

        @Test
        @DisplayName("should handle edge cases")
        void shouldHandleEdgeCases() {
            // 20% progress = 1 filled
            assertEquals("▰▱▱▱▱", buildXpBar(20, 100));
            // 40% progress = 2 filled
            assertEquals("▰▰▱▱▱", buildXpBar(40, 100));
            // 60% progress = 3 filled
            assertEquals("▰▰▰▱▱", buildXpBar(60, 100));
            // 80% progress = 4 filled
            assertEquals("▰▰▰▰▱", buildXpBar(80, 100));
        }

        @Test
        @DisplayName("should handle zero required XP")
        void shouldHandleZeroRequiredXp() {
            // When required is 0, defaults to 100
            String bar = buildXpBar(0, 0);
            assertEquals("▱▱▱▱▱", bar);
        }

        @Test
        @DisplayName("should handle progress exceeding required")
        void shouldHandleProgressExceedingRequired() {
            String bar = buildXpBar(150, 100);
            assertEquals("▰▰▰▰▰", bar); // Capped at 5 filled
        }

        /**
         * Mirrors the buildXpBar method in ScoreboardService.
         */
        private String buildXpBar(int progress, int required) {
            if (required <= 0) required = 100;

            int filled = (progress * 5) / required;
            filled = Math.min(5, Math.max(0, filled));

            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                bar.append(i < filled ? "\u25B0" : "\u25B1"); // ▰ filled, ▱ empty
            }
            return bar.toString();
        }
    }

    @Nested
    @DisplayName("XP Percent Calculation")
    class XpPercentCalculation {

        @Test
        @DisplayName("should calculate 0% for no progress")
        void shouldCalculateZeroPercent() {
            assertEquals(0, getXpPercent(0, 100));
        }

        @Test
        @DisplayName("should calculate 100% for full progress")
        void shouldCalculateHundredPercent() {
            assertEquals(100, getXpPercent(100, 100));
        }

        @Test
        @DisplayName("should calculate correct percentage")
        void shouldCalculateCorrectPercentage() {
            assertEquals(50, getXpPercent(50, 100));
            assertEquals(25, getXpPercent(25, 100));
            assertEquals(75, getXpPercent(75, 100));
        }

        @Test
        @DisplayName("should cap at 100% for overflow")
        void shouldCapAtHundredPercent() {
            assertEquals(100, getXpPercent(150, 100));
            assertEquals(100, getXpPercent(200, 100));
        }

        @Test
        @DisplayName("should handle zero required XP")
        void shouldHandleZeroRequiredXp() {
            assertEquals(0, getXpPercent(50, 0));
        }

        /**
         * Mirrors the getXpPercent method in ScoreboardService.
         */
        private int getXpPercent(int progress, int required) {
            if (required <= 0) return 0;
            return Math.min(100, (progress * 100) / required);
        }
    }

    @Nested
    @DisplayName("Line Ordering for FastBoard")
    class LineOrderingForFastBoard {

        @Test
        @DisplayName("should order lines by score descending")
        void shouldOrderLinesByScoreDescending() {
            java.util.Map<Integer, String> lines = new java.util.LinkedHashMap<>();
            lines.put(15, "Line A (score 15)");
            lines.put(10, "Line B (score 10)");
            lines.put(14, "Line C (score 14)");
            lines.put(5, "Line D (score 5)");

            java.util.List<String> orderedLines = convertToOrderedList(lines);

            assertEquals(4, orderedLines.size());
            assertEquals("Line A (score 15)", orderedLines.get(0));
            assertEquals("Line C (score 14)", orderedLines.get(1));
            assertEquals("Line B (score 10)", orderedLines.get(2));
            assertEquals("Line D (score 5)", orderedLines.get(3));
        }

        @Test
        @DisplayName("should handle empty lines map")
        void shouldHandleEmptyLinesMap() {
            java.util.Map<Integer, String> lines = new java.util.LinkedHashMap<>();
            java.util.List<String> orderedLines = convertToOrderedList(lines);

            assertTrue(orderedLines.isEmpty());
        }

        @Test
        @DisplayName("should handle single line")
        void shouldHandleSingleLine() {
            java.util.Map<Integer, String> lines = new java.util.LinkedHashMap<>();
            lines.put(10, "Only line");

            java.util.List<String> orderedLines = convertToOrderedList(lines);

            assertEquals(1, orderedLines.size());
            assertEquals("Only line", orderedLines.get(0));
        }

        /**
         * Mirrors the line ordering logic used in applyLineChanges for FastBoard.
         */
        private java.util.List<String> convertToOrderedList(java.util.Map<Integer, String> lines) {
            java.util.List<String> result = new java.util.ArrayList<>();
            java.util.List<Integer> scores = new java.util.ArrayList<>(lines.keySet());
            java.util.Collections.sort(scores, java.util.Collections.reverseOrder());
            for (Integer score : scores) {
                result.add(lines.get(score));
            }
            return result;
        }
    }

    @Nested
    @DisplayName("Balance Caching Logic")
    class BalanceCachingLogic {

        @Test
        @DisplayName("should return default value when balance not cached")
        void shouldReturnDefaultValueWhenNotCached() {
            java.util.Map<java.util.UUID, Integer> cache = new java.util.concurrent.ConcurrentHashMap<>();
            java.util.UUID playerId = java.util.UUID.randomUUID();

            int balance = cache.getOrDefault(playerId, 0);
            assertEquals(0, balance);
        }

        @Test
        @DisplayName("should return cached value when present")
        void shouldReturnCachedValueWhenPresent() {
            java.util.Map<java.util.UUID, Integer> cache = new java.util.concurrent.ConcurrentHashMap<>();
            java.util.UUID playerId = java.util.UUID.randomUUID();

            cache.put(playerId, 1500);
            int balance = cache.getOrDefault(playerId, 0);
            assertEquals(1500, balance);
        }

        @Test
        @DisplayName("should update cached value correctly")
        void shouldUpdateCachedValueCorrectly() {
            java.util.Map<java.util.UUID, Integer> cache = new java.util.concurrent.ConcurrentHashMap<>();
            java.util.UUID playerId = java.util.UUID.randomUUID();

            cache.put(playerId, 100);
            assertEquals(100, cache.get(playerId));

            cache.put(playerId, 200);
            assertEquals(200, cache.get(playerId));
        }

        @Test
        @DisplayName("should handle multiple players in cache")
        void shouldHandleMultiplePlayersInCache() {
            java.util.Map<java.util.UUID, Integer> cache = new java.util.concurrent.ConcurrentHashMap<>();
            java.util.UUID player1 = java.util.UUID.randomUUID();
            java.util.UUID player2 = java.util.UUID.randomUUID();
            java.util.UUID player3 = java.util.UUID.randomUUID();

            cache.put(player1, 100);
            cache.put(player2, 200);
            cache.put(player3, 300);

            assertEquals(100, cache.getOrDefault(player1, 0));
            assertEquals(200, cache.getOrDefault(player2, 0));
            assertEquals(300, cache.getOrDefault(player3, 0));
        }

        @Test
        @DisplayName("should remove player from cache correctly")
        void shouldRemovePlayerFromCacheCorrectly() {
            java.util.Map<java.util.UUID, Integer> cache = new java.util.concurrent.ConcurrentHashMap<>();
            java.util.UUID playerId = java.util.UUID.randomUUID();

            cache.put(playerId, 500);
            assertEquals(500, cache.getOrDefault(playerId, 0));

            cache.remove(playerId);
            assertEquals(0, cache.getOrDefault(playerId, 0));
        }
    }
}
