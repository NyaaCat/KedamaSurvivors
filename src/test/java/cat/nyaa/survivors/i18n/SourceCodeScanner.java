package cat.nyaa.survivors.i18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    }

    private void addKey(String key, Path file, int lineNum,
                        Map<String, List<SourceLocation>> foundKeys) {
        // Skip incomplete dynamic keys (ending with .)
        // These are typically used with string concatenation like "help.command." + name
        if (key.endsWith(".")) {
            return;
        }
        foundKeys.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new SourceLocation(file, lineNum));
    }
}
