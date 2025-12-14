package cat.nyaa.survivors.i18n;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all i18n keys used in code exist in the language YAML file.
 * <p>
 * This test ensures:
 * 1. All string literal keys in source code exist in YAML
 * 2. YAML file is valid and parseable
 * 3. Required sections exist
 */
@DisplayName("Language Key Validation")
class LanguageKeyValidationTest {

    private static Set<String> yamlKeys;
    private static Map<String, List<SourceLocation>> sourceKeys;
    private static YamlKeyLoader yamlLoader;
    private static SourceCodeScanner sourceScanner;

    @BeforeAll
    static void loadLanguageData() throws Exception {
        yamlLoader = new YamlKeyLoader();
        sourceScanner = new SourceCodeScanner();

        // Load YAML keys from resources
        InputStream yamlStream = LanguageKeyValidationTest.class
                .getResourceAsStream("/lang/zh_CN.yml");
        assertNotNull(yamlStream, "Language file zh_CN.yml not found in resources");
        yamlKeys = yamlLoader.loadKeys(yamlStream);

        // Scan source code for string literal keys
        Path sourceRoot = Paths.get("src/main/java");
        if (!sourceRoot.toFile().exists()) {
            // Fallback for different working directories
            sourceRoot = Paths.get("../../src/main/java").toAbsolutePath().normalize();
        }
        assertTrue(sourceRoot.toFile().exists(), "Source root not found: " + sourceRoot);
        sourceKeys = sourceScanner.scanForI18nKeys(sourceRoot);
    }

    @Nested
    @DisplayName("String Literal Validation")
    class StringLiteralValidation {

        @Test
        @DisplayName("All string literal i18n keys should exist in YAML")
        void allStringLiteralKeysExistInYaml() {
            StringBuilder errorMsg = new StringBuilder();
            int missingCount = 0;

            for (Map.Entry<String, List<SourceLocation>> entry : sourceKeys.entrySet()) {
                String key = entry.getKey();
                if (!yamlKeys.contains(key)) {
                    missingCount++;
                    errorMsg.append(String.format("\nKey: %s\n", key));
                    for (SourceLocation loc : entry.getValue()) {
                        errorMsg.append(String.format("  - %s\n", loc));
                    }
                }
            }

            if (missingCount > 0) {
                fail(String.format(
                        "\n\nMissing i18n keys in zh_CN.yml (%d):\n%s",
                        missingCount,
                        errorMsg
                ));
            }
        }

        @Test
        @DisplayName("Should find string literal keys in source code")
        void sourceCodeContainsI18nKeys() {
            assertFalse(sourceKeys.isEmpty(),
                    "Expected to find i18n string literal keys in source code");
            assertTrue(sourceKeys.size() >= 10,
                    "Expected at least 10 unique string literal keys in source code, found: " + sourceKeys.size());
        }
    }

    @Nested
    @DisplayName("YAML Structure Validation")
    class YamlStructureValidation {

        @Test
        @DisplayName("YAML file should be valid and parseable")
        void yamlFileIsValid() {
            assertNotNull(yamlKeys, "YAML keys should be loaded");
            assertFalse(yamlKeys.isEmpty(), "YAML should contain at least one key");
        }

        @Test
        @DisplayName("YAML should contain expected number of keys")
        void yamlContainsExpectedKeys() {
            // Based on analysis, zh_CN.yml has ~200+ keys
            assertTrue(yamlKeys.size() >= 200,
                    String.format("Expected at least 200 YAML keys, found %d", yamlKeys.size()));
        }

        @Test
        @DisplayName("YAML should contain all major sections")
        void yamlContainsMajorSections() {
            List<String> requiredPrefixes = Arrays.asList(
                    "prefix",
                    "info.", "error.", "success.",
                    "team.", "admin.", "help.",
                    "upgrade.", "gui.", "status."
            );

            List<String> missingSections = requiredPrefixes.stream()
                    .filter(prefix -> yamlKeys.stream()
                            .noneMatch(k -> k.equals(prefix) || k.startsWith(prefix)))
                    .toList();

            assertTrue(missingSections.isEmpty(),
                    "Missing YAML sections: " + missingSections);
        }

        @Test
        @DisplayName("Prefix key should exist in YAML")
        void prefixKeyExists() {
            assertTrue(yamlKeys.contains("prefix"),
                    "The 'prefix' key must exist in the language file");
        }
    }

    @Nested
    @DisplayName("Coverage Statistics")
    class CoverageStats {

        @Test
        @DisplayName("Report i18n usage statistics")
        void reportStatistics() {
            System.out.println("\n=== Language Key Validation Statistics ===");
            System.out.println("YAML keys defined: " + yamlKeys.size());
            System.out.println("Unique string literal keys found: " + sourceKeys.size());

            int totalUsages = sourceKeys.values().stream()
                    .mapToInt(List::size)
                    .sum();
            System.out.println("Total string literal usages: " + totalUsages);

            // Find unused YAML keys (defined but not used in code)
            Set<String> unusedKeys = new java.util.HashSet<>(yamlKeys);
            unusedKeys.removeAll(sourceKeys.keySet());

            if (!unusedKeys.isEmpty() && unusedKeys.size() < 100) {
                System.out.println("\nUnused YAML keys (" + unusedKeys.size() + "):");
                unusedKeys.stream().sorted().limit(30)
                        .forEach(k -> System.out.println("  - " + k));
                if (unusedKeys.size() > 30) {
                    System.out.println("  ... and " + (unusedKeys.size() - 30) + " more");
                }
            }
            System.out.println("==========================================\n");

            // This test always passes - it's just for reporting
            assertTrue(true);
        }
    }
}
