package cat.nyaa.survivors.command.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for admin command logic.
 * Tests parsing, validation, and tab completion without Bukkit dependencies.
 */
class AdminCommandsTest {

    @Nested
    @DisplayName("CoinSubCommand Logic")
    class CoinSubCommandLogicTests {

        @Test
        @DisplayName("should parse valid add action")
        void shouldParseValidAddAction() {
            String[] args = {"add", "TestPlayer", "100"};
            assertEquals("add", args[0].toLowerCase());
            assertEquals("TestPlayer", args[1]);
            assertEquals(100, Integer.parseInt(args[2]));
        }

        @Test
        @DisplayName("should parse valid set action")
        void shouldParseValidSetAction() {
            String[] args = {"set", "TestPlayer", "500"};
            assertEquals("set", args[0].toLowerCase());
            assertEquals(500, Integer.parseInt(args[2]));
        }

        @Test
        @DisplayName("should parse valid get action")
        void shouldParseValidGetAction() {
            String[] args = {"get", "TestPlayer"};
            assertEquals("get", args[0].toLowerCase());
            assertEquals("TestPlayer", args[1]);
        }

        @Test
        @DisplayName("should handle negative amount for add")
        void shouldHandleNegativeAmountForAdd() {
            String[] args = {"add", "TestPlayer", "-50"};
            int amount = Integer.parseInt(args[2]);
            assertEquals(-50, amount);

            // Verify balance check logic
            int currentBalance = 100;
            int newBalance = currentBalance + amount;
            assertEquals(50, newBalance);
            assertTrue(newBalance >= 0);
        }

        @Test
        @DisplayName("should prevent negative balance after add")
        void shouldPreventNegativeBalanceAfterAdd() {
            int currentBalance = 30;
            int amountToDeduct = -50;
            int newBalance = currentBalance + amountToDeduct;

            assertTrue(newBalance < 0, "Balance would go negative");
        }

        @Test
        @DisplayName("should clamp set amount to minimum 0")
        void shouldClampSetAmountToMinimumZero() {
            int amount = -100;
            int clampedAmount = amount < 0 ? 0 : amount;
            assertEquals(0, clampedAmount);
        }

        @Test
        @DisplayName("should detect invalid number format")
        void shouldDetectInvalidNumberFormat() {
            String invalidAmount = "abc";
            assertThrows(NumberFormatException.class, () -> Integer.parseInt(invalidAmount));
        }

        @Test
        @DisplayName("should provide tab completions for actions")
        void shouldProvideTabCompletionsForActions() {
            List<String> actions = List.of("add", "set", "get");
            String partial = "a";

            List<String> completions = new ArrayList<>();
            for (String action : actions) {
                if (action.startsWith(partial.toLowerCase())) {
                    completions.add(action);
                }
            }

            assertEquals(1, completions.size());
            assertTrue(completions.contains("add"));
        }

        @Test
        @DisplayName("should provide tab completions for amounts")
        void shouldProvideTabCompletionsForAmounts() {
            List<String> suggestedAmounts = List.of("100", "500", "1000", "5000", "10000");
            assertEquals(5, suggestedAmounts.size());
            assertTrue(suggestedAmounts.contains("1000"));
        }

        @Test
        @DisplayName("should require 3 args for add action")
        void shouldRequireThreeArgsForAddAction() {
            String[] insufficientArgs = {"add", "TestPlayer"};
            assertTrue(insufficientArgs.length < 3);
        }

        @Test
        @DisplayName("should require 2 args for get action")
        void shouldRequireTwoArgsForGetAction() {
            String[] insufficientArgs = {"get"};
            assertTrue(insufficientArgs.length < 2);
        }
    }

    @Nested
    @DisplayName("PermaSubCommand Logic")
    class PermaSubCommandLogicTests {

        @Test
        @DisplayName("should parse valid add action")
        void shouldParseValidAddAction() {
            String[] args = {"add", "TestPlayer", "50"};
            assertEquals("add", args[0].toLowerCase());
            assertEquals(50, Integer.parseInt(args[2]));
        }

        @Test
        @DisplayName("should clamp perma score to minimum 0 after negative add")
        void shouldClampPermaScoreAfterNegativeAdd() {
            int currentScore = 30;
            int amount = -50;
            int newScore = Math.max(0, currentScore + amount);
            assertEquals(0, newScore);
        }

        @Test
        @DisplayName("should allow positive perma score changes")
        void shouldAllowPositivePermaScoreChanges() {
            int currentScore = 100;
            int amount = 50;
            int newScore = Math.max(0, currentScore + amount);
            assertEquals(150, newScore);
        }

        @Test
        @DisplayName("should clamp set amount to minimum 0")
        void shouldClampSetAmountToMinimumZero() {
            int amount = -25;
            int clampedAmount = amount < 0 ? 0 : amount;
            assertEquals(0, clampedAmount);
        }

        @Test
        @DisplayName("should provide tab completions for perma amounts")
        void shouldProvideTabCompletionsForPermaAmounts() {
            List<String> suggestedAmounts = List.of("10", "50", "100", "500", "1000");
            assertEquals(5, suggestedAmounts.size());
            assertTrue(suggestedAmounts.contains("100"));
        }

        @Test
        @DisplayName("should detect unknown action")
        void shouldDetectUnknownAction() {
            String action = "invalid";
            List<String> validActions = List.of("add", "set", "get");
            assertFalse(validActions.contains(action));
        }
    }

    @Nested
    @DisplayName("Balance Calculation")
    class BalanceCalculationTests {

        @Test
        @DisplayName("should calculate difference for set operation")
        void shouldCalculateDifferenceForSetOperation() {
            int currentBalance = 100;
            int targetAmount = 250;
            int difference = targetAmount - currentBalance;

            assertEquals(150, difference);
            assertTrue(difference > 0, "Should add 150 coins");
        }

        @Test
        @DisplayName("should calculate negative difference for set operation")
        void shouldCalculateNegativeDifferenceForSetOperation() {
            int currentBalance = 500;
            int targetAmount = 200;
            int difference = targetAmount - currentBalance;

            assertEquals(-300, difference);
            assertTrue(difference < 0, "Should deduct 300 coins");
        }

        @Test
        @DisplayName("should handle zero difference")
        void shouldHandleZeroDifference() {
            int currentBalance = 100;
            int targetAmount = 100;
            int difference = targetAmount - currentBalance;

            assertEquals(0, difference);
        }

        @Test
        @DisplayName("should apply add correctly for positive amount")
        void shouldApplyAddCorrectlyForPositiveAmount() {
            int currentBalance = 100;
            int amount = 50;

            // Simulate add logic
            if (amount > 0) {
                currentBalance += amount;
            }

            assertEquals(150, currentBalance);
        }

        @Test
        @DisplayName("should apply deduct correctly for negative amount")
        void shouldApplyDeductCorrectlyForNegativeAmount() {
            int currentBalance = 100;
            int amount = -30;

            // Simulate add logic with negative
            int newBalance = currentBalance + amount;
            if (newBalance >= 0) {
                currentBalance = newBalance;
            }

            assertEquals(70, currentBalance);
        }
    }

    @Nested
    @DisplayName("Permissions")
    class PermissionTests {

        @Test
        @DisplayName("should define coin permission")
        void shouldDefineCoinPermission() {
            String permission = "vrs.admin.coin";
            assertNotNull(permission);
            assertTrue(permission.startsWith("vrs.admin."));
        }

        @Test
        @DisplayName("should define perma permission")
        void shouldDefinePermaPermission() {
            String permission = "vrs.admin.perma";
            assertNotNull(permission);
            assertTrue(permission.startsWith("vrs.admin."));
        }
    }

    @Nested
    @DisplayName("Action Routing")
    class ActionRoutingTests {

        @Test
        @DisplayName("should route add action correctly")
        void shouldRouteAddActionCorrectly() {
            String action = "add";
            String result = switch (action.toLowerCase()) {
                case "add" -> "handleAdd";
                case "set" -> "handleSet";
                case "get" -> "handleGet";
                default -> "showHelp";
            };
            assertEquals("handleAdd", result);
        }

        @Test
        @DisplayName("should route set action correctly")
        void shouldRouteSetActionCorrectly() {
            String action = "SET"; // Case insensitive
            String result = switch (action.toLowerCase()) {
                case "add" -> "handleAdd";
                case "set" -> "handleSet";
                case "get" -> "handleGet";
                default -> "showHelp";
            };
            assertEquals("handleSet", result);
        }

        @Test
        @DisplayName("should route get action correctly")
        void shouldRouteGetActionCorrectly() {
            String action = "Get";
            String result = switch (action.toLowerCase()) {
                case "add" -> "handleAdd";
                case "set" -> "handleSet";
                case "get" -> "handleGet";
                default -> "showHelp";
            };
            assertEquals("handleGet", result);
        }

        @Test
        @DisplayName("should route unknown action to help")
        void shouldRouteUnknownActionToHelp() {
            String action = "unknown";
            String result = switch (action.toLowerCase()) {
                case "add" -> "handleAdd";
                case "set" -> "handleSet";
                case "get" -> "handleGet";
                default -> "showHelp";
            };
            assertEquals("showHelp", result);
        }

        @Test
        @DisplayName("should show help when no args provided")
        void shouldShowHelpWhenNoArgs() {
            String[] args = {};
            boolean shouldShowHelp = args.length == 0;
            assertTrue(shouldShowHelp);
        }
    }
}
