package cat.nyaa.survivors.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateEngine.
 */
class TemplateEngineTest {

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TemplateEngine();
    }

    @Nested
    @DisplayName("Basic Expansion")
    class BasicExpansion {

        @Test
        @DisplayName("should return empty string for null template")
        void shouldReturnEmptyStringForNullTemplate() {
            assertEquals("", engine.expand(null, Map.of()));
        }

        @Test
        @DisplayName("should return original template when no context")
        void shouldReturnOriginalTemplateWhenNoContext() {
            String template = "Hello {player}!";
            assertEquals(template, engine.expand(template, null));
            assertEquals(template, engine.expand(template, Map.of()));
        }

        @Test
        @DisplayName("should expand single placeholder")
        void shouldExpandSinglePlaceholder() {
            String template = "Hello {player}!";
            Map<String, Object> context = Map.of("player", "TestPlayer");
            assertEquals("Hello TestPlayer!", engine.expand(template, context));
        }

        @Test
        @DisplayName("should expand multiple placeholders")
        void shouldExpandMultiplePlaceholders() {
            String template = "{player} gained {amount} XP!";
            Map<String, Object> context = Map.of(
                    "player", "TestPlayer",
                    "amount", 100
            );
            assertEquals("TestPlayer gained 100 XP!", engine.expand(template, context));
        }

        @Test
        @DisplayName("should keep unmatched placeholders")
        void shouldKeepUnmatchedPlaceholders() {
            String template = "Hello {player}, you have {coins} coins!";
            Map<String, Object> context = Map.of("player", "TestPlayer");
            assertEquals("Hello TestPlayer, you have {coins} coins!", engine.expand(template, context));
        }

        @Test
        @DisplayName("should expand same placeholder multiple times")
        void shouldExpandSamePlaceholderMultipleTimes() {
            String template = "{player} hit {player}!";
            Map<String, Object> context = Map.of("player", "TestPlayer");
            assertEquals("TestPlayer hit TestPlayer!", engine.expand(template, context));
        }
    }

    @Nested
    @DisplayName("Value Type Handling")
    class ValueTypeHandling {

        @Test
        @DisplayName("should handle integer values")
        void shouldHandleIntegerValues() {
            String template = "Level: {level}";
            Map<String, Object> context = Map.of("level", 42);
            assertEquals("Level: 42", engine.expand(template, context));
        }

        @Test
        @DisplayName("should handle double values")
        void shouldHandleDoubleValues() {
            String template = "Weight: {weight}";
            Map<String, Object> context = Map.of("weight", 3.14159);
            assertEquals("Weight: 3.14159", engine.expand(template, context));
        }

        @Test
        @DisplayName("should handle boolean values")
        void shouldHandleBooleanValues() {
            String template = "Enabled: {enabled}";
            Map<String, Object> context = Map.of("enabled", true);
            assertEquals("Enabled: true", engine.expand(template, context));
        }

        @Test
        @DisplayName("should keep placeholder when context value is null")
        void shouldKeepPlaceholderWhenContextValueIsNull() {
            String template = "Value: {value}";
            Map<String, Object> context = new HashMap<>();
            context.put("value", null);
            // null value means "not found", so placeholder is kept
            assertEquals("Value: {value}", engine.expand(template, context));
        }
    }

    @Nested
    @DisplayName("Context Builder")
    class ContextBuilderTests {

        @Test
        @DisplayName("should build context with set method")
        void shouldBuildContextWithSetMethod() {
            Map<String, Object> context = engine.context()
                    .set("player", "TestPlayer")
                    .set("level", 5)
                    .build();

            assertEquals("TestPlayer", context.get("player"));
            assertEquals(5, context.get("level"));
        }

        @Test
        @DisplayName("should expand from context builder")
        void shouldExpandFromContextBuilder() {
            String result = engine.context()
                    .set("player", "TestPlayer")
                    .set("xp", 100)
                    .expand("{player} gained {xp} XP!");

            assertEquals("TestPlayer gained 100 XP!", result);
        }

        @Test
        @DisplayName("should add level info placeholders")
        void shouldAddLevelInfoPlaceholders() {
            Map<String, Object> context = engine.context()
                    .levelInfo(5, 3, 4.5)
                    .build();

            assertEquals(5, context.get("level"));
            assertEquals(3, context.get("player_count"));
            assertEquals("4.5", context.get("avg_level"));
        }

        @Test
        @DisplayName("should add archetype placeholders")
        void shouldAddArchetypePlaceholders() {
            Map<String, Object> context = engine.context()
                    .archetype("zombie_basic", 1.5)
                    .build();

            assertEquals("zombie_basic", context.get("archetype"));
            assertEquals("1.50", context.get("weight"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty template")
        void shouldHandleEmptyTemplate() {
            assertEquals("", engine.expand("", Map.of("key", "value")));
        }

        @Test
        @DisplayName("should handle template with no placeholders")
        void shouldHandleTemplateWithNoPlaceholders() {
            String template = "Hello World!";
            assertEquals(template, engine.expand(template, Map.of("key", "value")));
        }

        @Test
        @DisplayName("should handle special regex characters in replacement")
        void shouldHandleSpecialRegexCharactersInReplacement() {
            // Note: $ is escaped by default to prevent command injection
            engine.setEscapingEnabled(false);
            String template = "Command: {cmd}";
            Map<String, Object> context = Map.of("cmd", "give $player item");
            assertEquals("Command: give $player item", engine.expand(template, context));
        }

        @Test
        @DisplayName("should handle adjacent placeholders")
        void shouldHandleAdjacentPlaceholders() {
            String template = "{first}{second}";
            Map<String, Object> context = Map.of("first", "A", "second", "B");
            assertEquals("AB", engine.expand(template, context));
        }
    }

    @Nested
    @DisplayName("Variable Escaping")
    class VariableEscaping {

        @Test
        @DisplayName("should escape semicolon by default")
        void shouldEscapeSemicolonByDefault() {
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "value;malicious");
            assertEquals("cmd value\\;malicious", engine.expand(template, context));
        }

        @Test
        @DisplayName("should escape ampersand by default")
        void shouldEscapeAmpersandByDefault() {
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "value&other");
            assertEquals("cmd value\\&other", engine.expand(template, context));
        }

        @Test
        @DisplayName("should escape pipe by default")
        void shouldEscapePipeByDefault() {
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "value|other");
            assertEquals("cmd value\\|other", engine.expand(template, context));
        }

        @Test
        @DisplayName("should escape backtick by default")
        void shouldEscapeBacktickByDefault() {
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "value`cmd`");
            assertEquals("cmd value\\`cmd\\`", engine.expand(template, context));
        }

        @Test
        @DisplayName("should escape dollar sign by default")
        void shouldEscapeDollarSignByDefault() {
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "value$var");
            assertEquals("cmd value\\$var", engine.expand(template, context));
        }

        @Test
        @DisplayName("should escape backslash by default")
        void shouldEscapeBackslashByDefault() {
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "path\\to\\file");
            assertEquals("cmd path\\\\to\\\\file", engine.expand(template, context));
        }

        @Test
        @DisplayName("should not escape when disabled")
        void shouldNotEscapeWhenDisabled() {
            engine.setEscapingEnabled(false);
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "value;malicious");
            assertEquals("cmd value;malicious", engine.expand(template, context));
        }

        @Test
        @DisplayName("should use custom escape chars")
        void shouldUseCustomEscapeChars() {
            engine.setEscapeChars(";");
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", "a;b$c");
            assertEquals("cmd a\\;b$c", engine.expand(template, context));
        }

        @Test
        @DisplayName("should handle multiple dangerous chars in value")
        void shouldHandleMultipleDangerousCharsInValue() {
            String template = "cmd {arg}";
            Map<String, Object> context = Map.of("arg", ";|&`$\\");
            assertEquals("cmd \\;\\|\\&\\`\\$\\\\", engine.expand(template, context));
        }
    }

    @Nested
    @DisplayName("Missing Placeholder Handling")
    class MissingPlaceholderHandling {

        @Test
        @DisplayName("should keep placeholder in KEEP mode (default)")
        void shouldKeepPlaceholderInKeepMode() {
            engine.setMissingPlaceholderMode(TemplateEngine.MissingPlaceholderMode.KEEP);
            String template = "Hello {missing}!";
            assertEquals("Hello {missing}!", engine.expand(template, Map.of()));
        }

        @Test
        @DisplayName("should replace with empty string in EMPTY mode")
        void shouldReplaceWithEmptyInEmptyMode() {
            engine.setMissingPlaceholderMode(TemplateEngine.MissingPlaceholderMode.EMPTY);
            String template = "Hello {missing}!";
            assertEquals("Hello !", engine.expand(template, Map.of()));
        }

        @Test
        @DisplayName("should keep placeholder in ERROR mode")
        void shouldKeepPlaceholderInErrorMode() {
            engine.setMissingPlaceholderMode(TemplateEngine.MissingPlaceholderMode.ERROR);
            String template = "Hello {missing}!";
            assertEquals("Hello {missing}!", engine.expand(template, Map.of()));
        }

        @Test
        @DisplayName("should handle mixed present and missing placeholders in EMPTY mode")
        void shouldHandleMixedPlaceholdersInEmptyMode() {
            engine.setMissingPlaceholderMode(TemplateEngine.MissingPlaceholderMode.EMPTY);
            String template = "{player} has {coins} coins";
            Map<String, Object> context = Map.of("player", "TestPlayer");
            assertEquals("TestPlayer has  coins", engine.expand(template, context));
        }

        @Test
        @DisplayName("should handle null mode by defaulting to KEEP")
        void shouldHandleNullMode() {
            engine.setMissingPlaceholderMode(null);
            String template = "Hello {missing}!";
            assertEquals("Hello {missing}!", engine.expand(template, Map.of()));
        }
    }
}
