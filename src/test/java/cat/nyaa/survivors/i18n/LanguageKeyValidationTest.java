package cat.nyaa.survivors.i18n;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all i18n keys used in code exist in the language YAML file.
 * <p>
 * This test ensures:
 * 1. All string literal keys in source code exist in YAML
 * 2. YAML file is valid and parseable
 * 3. Required sections exist
 * 4. Reports unused YAML keys (defined but never used in code)
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
    @DisplayName("Missing Keys Detection")
    class MissingKeysValidation {

        @Test
        @DisplayName("All string literal i18n keys should exist in YAML")
        void allStringLiteralKeysExistInYaml() {
            StringBuilder errorMsg = new StringBuilder();
            List<String> missingKeys = new ArrayList<>();

            for (Map.Entry<String, List<SourceLocation>> entry : sourceKeys.entrySet()) {
                String key = entry.getKey();
                if (!yamlKeys.contains(key)) {
                    missingKeys.add(key);
                    errorMsg.append(String.format("\n  Key: %s\n", key));
                    for (SourceLocation loc : entry.getValue()) {
                        errorMsg.append(String.format("    - %s\n", loc));
                    }
                }
            }

            if (!missingKeys.isEmpty()) {
                // Sort for consistent output
                missingKeys.sort(String::compareTo);
                fail(String.format(
                        "\n\n" +
                        "╔════════════════════════════════════════════════════════════════╗\n" +
                        "║         MISSING LANGUAGE KEYS IN zh_CN.yml (%d keys)          ║\n" +
                        "╚════════════════════════════════════════════════════════════════╝\n" +
                        "%s\n" +
                        "Add these keys to src/main/resources/lang/zh_CN.yml",
                        missingKeys.size(),
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
    @DisplayName("Unused Keys Detection")
    class UnusedKeysValidation {

        @Test
        @DisplayName("Report all unused YAML keys")
        void reportUnusedYamlKeys() {
            // Find unused YAML keys (defined but not used in code)
            Set<String> unusedKeys = new TreeSet<>(yamlKeys);  // TreeSet for sorted output
            unusedKeys.removeAll(sourceKeys.keySet());

            // Group unused keys by prefix for better readability
            Map<String, List<String>> groupedUnused = unusedKeys.stream()
                    .collect(Collectors.groupingBy(
                            key -> {
                                int dotIndex = key.indexOf('.');
                                return dotIndex > 0 ? key.substring(0, dotIndex) : key;
                            },
                            TreeMap::new,  // Sorted map
                            Collectors.toList()
                    ));

            StringBuilder report = new StringBuilder();
            report.append("\n\n");
            report.append("╔════════════════════════════════════════════════════════════════╗\n");
            report.append(String.format("║           UNUSED YAML KEYS REPORT (%d keys)                    ║\n", unusedKeys.size()));
            report.append("╚════════════════════════════════════════════════════════════════╝\n\n");

            if (unusedKeys.isEmpty()) {
                report.append("✓ All YAML keys are used in source code!\n");
            } else {
                report.append("The following keys are defined in zh_CN.yml but NOT used in source code:\n");
                report.append("(These may be safe to remove, or the scanner may have missed some usages)\n\n");

                for (Map.Entry<String, List<String>> group : groupedUnused.entrySet()) {
                    report.append(String.format("── %s (%d keys) ──\n", group.getKey(), group.getValue().size()));
                    for (String key : group.getValue()) {
                        report.append(String.format("    - %s\n", key));
                    }
                    report.append("\n");
                }
            }

            report.append("════════════════════════════════════════════════════════════════\n");

            // Print the report (always passes - informational only)
            System.out.println(report);

            // This test always passes - it's informational
            assertTrue(true);
        }

        @Test
        @DisplayName("Warn if too many unused keys (potential dead code)")
        void warnOnExcessiveUnusedKeys() {
            Set<String> unusedKeys = new HashSet<>(yamlKeys);
            unusedKeys.removeAll(sourceKeys.keySet());

            // Calculate percentage of unused keys
            double unusedPercent = (double) unusedKeys.size() / yamlKeys.size() * 100;

            // Print warning if more than 30% are unused
            if (unusedPercent > 30) {
                System.out.printf("\n⚠ WARNING: %.1f%% of YAML keys (%d/%d) are unused.\n",
                        unusedPercent, unusedKeys.size(), yamlKeys.size());
                System.out.println("Consider reviewing and cleaning up unused keys.\n");
            }

            // This test always passes - it's a warning only
            assertTrue(true);
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
                    "upgrade.", "gui.", "status.",
                    "killstreak."  // New section for kill streak messages
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
        @DisplayName("Report comprehensive i18n usage statistics")
        void reportStatistics() {
            int totalUsages = sourceKeys.values().stream()
                    .mapToInt(List::size)
                    .sum();

            Set<String> unusedKeys = new HashSet<>(yamlKeys);
            unusedKeys.removeAll(sourceKeys.keySet());

            double coveragePercent = (double) (yamlKeys.size() - unusedKeys.size()) / yamlKeys.size() * 100;

            StringBuilder report = new StringBuilder();
            report.append("\n\n");
            report.append("╔════════════════════════════════════════════════════════════════╗\n");
            report.append("║              LANGUAGE KEY VALIDATION SUMMARY                   ║\n");
            report.append("╚════════════════════════════════════════════════════════════════╝\n\n");

            report.append(String.format("  YAML keys defined:            %d\n", yamlKeys.size()));
            report.append(String.format("  Unique keys used in code:     %d\n", sourceKeys.size()));
            report.append(String.format("  Total key usages:             %d\n", totalUsages));
            report.append(String.format("  Unused YAML keys:             %d\n", unusedKeys.size()));
            report.append(String.format("  Coverage:                     %.1f%%\n", coveragePercent));
            report.append("\n");

            // Most frequently used keys
            report.append("  Top 10 most used keys:\n");
            sourceKeys.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .limit(10)
                    .forEach(e -> report.append(String.format("    - %s (%d usages)\n",
                            e.getKey(), e.getValue().size())));

            report.append("\n════════════════════════════════════════════════════════════════\n");

            System.out.println(report);

            // This test always passes - it's for reporting
            assertTrue(true);
        }
    }
}
