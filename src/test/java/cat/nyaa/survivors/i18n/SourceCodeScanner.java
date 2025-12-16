package cat.nyaa.survivors.i18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans Java source files for i18n string literal keys.
 */
class SourceCodeScanner {

    // Matches: i18n.send(xxx, "key.path", ...) or i18n.sendActionBar(...) or i18n.sendClickable(...)
    private static final Pattern SEND_PATTERN = Pattern.compile(
            "i18n\\.(send|sendActionBar|sendClickable)\\([^,]+,\\s*\"([a-z][a-z0-9_\\.]+)\""
    );

    // Matches: i18n.sendTitle(xxx, "title.key", "subtitle.key", ...)
    // Captures both title and subtitle keys
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "i18n\\.sendTitle\\([^,]+,\\s*\"([a-z][a-z0-9_\\.]+)\"(?:,\\s*\"([a-z][a-z0-9_\\.]+)\")?"
    );

    // Matches: i18n.get("key.path", ...) or i18n.getRaw(...) or i18n.getComponent(...) or i18n.format(...) or i18n.hasKey(...)
    private static final Pattern GET_PATTERN = Pattern.compile(
            "i18n\\.(get|getRaw|getComponent|format|hasKey)\\(\"([a-z][a-z0-9_\\.]+)\""
    );

    // Matches wrapper methods: notifyTeam(..., "key.path", ...) or notifyRunPlayers(..., "key.path")
    private static final Pattern WRAPPER_PATTERN = Pattern.compile(
            "(notifyTeam|notifyRunPlayers)\\([^,]+,\\s*\"([a-z][a-z0-9_\\.]+)\""
    );

    // Matches conditional/ternary key assignments that look like i18n keys
    // e.g.: condition ? "run.ended_wipe" : "run.ended"
    // Keys MUST contain at least one dot to be considered i18n keys (filters out simple strings like "yes"/"no")
    private static final Pattern CONDITIONAL_KEY_PATTERN = Pattern.compile(
            "\\?\\s*\"([a-z][a-z0-9_]*\\.[a-z0-9_.]+)\"\\s*:\\s*\"([a-z][a-z0-9_]*\\.[a-z0-9_.]+)\""
    );

    // Matches switch expression case patterns: case N -> "key" or case "str" -> "key"
    // e.g.: case 2 -> "killstreak.double";
    private static final Pattern SWITCH_CASE_PATTERN = Pattern.compile(
            "case\\s+[^-]+->\\s*\"([a-z][a-z0-9_]*\\.[a-z0-9_.]+)\""
    );

    // Matches switch expression default patterns: default -> "key"
    // e.g.: default -> "killstreak.generic";
    private static final Pattern SWITCH_DEFAULT_PATTERN = Pattern.compile(
            "default\\s*->\\s*\"([a-z][a-z0-9_]*\\.[a-z0-9_.]+)\""
    );

    // Matches string variable assignments that look like i18n keys
    // e.g.: String key = "error.not_found";
    // Captures variables containing "key" or "Key" in the name
    private static final Pattern STRING_VAR_KEY_PATTERN = Pattern.compile(
            "(?:String|var)\\s+\\w*[Kk]ey\\w*\\s*=\\s*\"([a-z][a-z0-9_]*\\.[a-z0-9_.]+)\""
    );

    // Matches return statements with i18n keys
    // e.g.: return "status.mode_lobby";
    // Excludes permission-like patterns (vrs.xxx which are Bukkit permissions)
    private static final Pattern RETURN_KEY_PATTERN = Pattern.compile(
            "return\\s+\"([a-z][a-z0-9_]*\\.[a-z0-9_.]+)\""
    );

    // Permission prefixes to exclude from i18n key detection
    private static final Set<String> PERMISSION_PREFIXES = Set.of("vrs.");

    /**
     * Scan source directory for all i18n key usages.
     *
     * @return Map of key -> locations where used
     */
    public Map<String, List<SourceLocation>> scanForI18nKeys(Path sourceRoot) throws IOException {
        Map<String, List<SourceLocation>> foundKeys = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> scanFile(file, foundKeys));
        }

        return foundKeys;
    }

    private void scanFile(Path file, Map<String, List<SourceLocation>> foundKeys) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean inBlockComment = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmedLine = line.trim();
                int lineNum = i + 1;

                // Skip line comments
                if (trimmedLine.startsWith("//")) continue;

                // Handle block comments
                if (trimmedLine.contains("/*")) inBlockComment = true;
                if (inBlockComment) {
                    if (trimmedLine.contains("*/")) inBlockComment = false;
                    continue;
                }

                // Extract keys from this line
                extractKeys(line, file, lineNum, foundKeys);
            }
        } catch (IOException e) {
            System.err.println("Failed to read: " + file);
        }
    }

    private void extractKeys(String line, Path file, int lineNum,
                             Map<String, List<SourceLocation>> foundKeys) {
        // Check send/sendActionBar/sendClickable patterns
        Matcher sendMatcher = SEND_PATTERN.matcher(line);
        while (sendMatcher.find()) {
            String key = sendMatcher.group(2);
            addKey(key, file, lineNum, foundKeys);
        }

        // Check get/getRaw/getComponent patterns
        Matcher getMatcher = GET_PATTERN.matcher(line);
        while (getMatcher.find()) {
            String key = getMatcher.group(2);
            addKey(key, file, lineNum, foundKeys);
        }

        // Check sendTitle pattern (can have 1 or 2 keys)
        Matcher titleMatcher = TITLE_PATTERN.matcher(line);
        while (titleMatcher.find()) {
            String titleKey = titleMatcher.group(1);
            if (titleKey != null) {
                addKey(titleKey, file, lineNum, foundKeys);
            }
            String subtitleKey = titleMatcher.group(2);
            if (subtitleKey != null) {
                addKey(subtitleKey, file, lineNum, foundKeys);
            }
        }

        // Check wrapper patterns (notifyTeam, notifyRunPlayers)
        Matcher wrapperMatcher = WRAPPER_PATTERN.matcher(line);
        while (wrapperMatcher.find()) {
            String key = wrapperMatcher.group(2);
            addKey(key, file, lineNum, foundKeys);
        }

        // Check conditional/ternary key patterns
        // Catches: condition ? "key1" : "key2" where both are likely i18n keys
        Matcher conditionalMatcher = CONDITIONAL_KEY_PATTERN.matcher(line);
        while (conditionalMatcher.find()) {
            String key1 = conditionalMatcher.group(1);
            String key2 = conditionalMatcher.group(2);
            addKey(key1, file, lineNum, foundKeys);
            addKey(key2, file, lineNum, foundKeys);
        }

        // Check switch expression case patterns
        Matcher switchCaseMatcher = SWITCH_CASE_PATTERN.matcher(line);
        while (switchCaseMatcher.find()) {
            String key = switchCaseMatcher.group(1);
            addKey(key, file, lineNum, foundKeys);
        }

        // Check switch expression default patterns
        Matcher switchDefaultMatcher = SWITCH_DEFAULT_PATTERN.matcher(line);
        while (switchDefaultMatcher.find()) {
            String key = switchDefaultMatcher.group(1);
            addKey(key, file, lineNum, foundKeys);
        }

        // Check string variable assignments containing "key" in name
        Matcher stringVarMatcher = STRING_VAR_KEY_PATTERN.matcher(line);
        while (stringVarMatcher.find()) {
            String key = stringVarMatcher.group(1);
            addKey(key, file, lineNum, foundKeys);
        }

        // Check return statements with i18n keys
        Matcher returnMatcher = RETURN_KEY_PATTERN.matcher(line);
        while (returnMatcher.find()) {
            String key = returnMatcher.group(1);
            addKey(key, file, lineNum, foundKeys);
        }
    }

    private void addKey(String key, Path file, int lineNum,
                        Map<String, List<SourceLocation>> foundKeys) {
        // Skip incomplete dynamic keys (ending with .)
        // These are typically used with string concatenation like "help.command." + name
        if (key.endsWith(".")) {
            return;
        }
        // Skip permission-like keys (e.g., vrs.admin, vrs.admin.config)
        // These are Bukkit permissions, not i18n keys
        if (isPermissionKey(key)) {
            return;
        }
        foundKeys.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new SourceLocation(file, lineNum));
    }

    /**
     * Checks if a key looks like a Bukkit permission rather than an i18n key.
     */
    private boolean isPermissionKey(String key) {
        for (String prefix : PERMISSION_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
