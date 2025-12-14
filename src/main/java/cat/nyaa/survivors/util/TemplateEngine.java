package cat.nyaa.survivors.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template engine for expanding command templates with placeholders.
 * Supports placeholders in the format {placeholder_name}.
 */
public class TemplateEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final String DEFAULT_ESCAPE_CHARS = ";&|`$\\";

    /**
     * Mode for handling missing placeholders.
     */
    public enum MissingPlaceholderMode {
        /** Keep the placeholder as-is (default) */
        KEEP,
        /** Replace with empty string */
        EMPTY,
        /** Log a warning and keep the placeholder */
        ERROR
    }

    private boolean escapingEnabled = true;
    private String escapeChars = DEFAULT_ESCAPE_CHARS;
    private MissingPlaceholderMode missingPlaceholderMode = MissingPlaceholderMode.KEEP;
    private Logger logger;
    private CommandQueue commandQueue;

    /**
     * Sets whether variable escaping is enabled.
     * When enabled, dangerous characters in placeholder values are escaped.
     */
    public void setEscapingEnabled(boolean enabled) {
        this.escapingEnabled = enabled;
    }

    /**
     * Sets the characters to escape in placeholder values.
     */
    public void setEscapeChars(String escapeChars) {
        this.escapeChars = escapeChars != null ? escapeChars : DEFAULT_ESCAPE_CHARS;
    }

    /**
     * Sets the mode for handling missing placeholders.
     */
    public void setMissingPlaceholderMode(MissingPlaceholderMode mode) {
        this.missingPlaceholderMode = mode != null ? mode : MissingPlaceholderMode.KEEP;
    }

    /**
     * Sets the logger for error messages.
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Sets the command queue for rate-limited command execution.
     */
    public void setCommandQueue(CommandQueue commandQueue) {
        this.commandQueue = commandQueue;
    }

    /**
     * Escapes dangerous characters in a value to prevent command injection.
     */
    private String escapeValue(String value) {
        if (value == null || !escapingEnabled || escapeChars.isEmpty()) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (escapeChars.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Expands a command template with the given context.
     *
     * @param template the command template with placeholders
     * @param context  map of placeholder names to values
     * @return the expanded command string
     */
    public String expand(String template, Map<String, Object> context) {
        if (template == null) return "";
        // Only skip processing if context is empty AND mode is KEEP (default behavior)
        if ((context == null || context.isEmpty()) && missingPlaceholderMode == MissingPlaceholderMode.KEEP) {
            return template;
        }

        Map<String, Object> safeContext = context != null ? context : Map.of();
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = safeContext.get(placeholder);
            String replacement;

            if (value != null) {
                // Escape the value to prevent command injection
                replacement = escapeValue(String.valueOf(value));
            } else {
                // Handle missing placeholder based on mode
                switch (missingPlaceholderMode) {
                    case EMPTY:
                        replacement = "";
                        break;
                    case ERROR:
                        if (logger != null) {
                            logger.warning("Missing placeholder in template: {" + placeholder + "}");
                        }
                        replacement = matcher.group(0);
                        break;
                    case KEEP:
                    default:
                        replacement = matcher.group(0);
                        break;
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Creates a context builder for fluent context construction.
     */
    public ContextBuilder context() {
        return new ContextBuilder(this);
    }

    /**
     * Expands and executes a command as the console immediately.
     *
     * @param template the command template
     * @param context  the placeholder context
     */
    public void executeAsConsole(String template, Map<String, Object> context) {
        String command = expand(template, context);
        if (!command.isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    /**
     * Expands and queues a command for rate-limited execution as console.
     * Falls back to immediate execution if no command queue is configured.
     *
     * @param template the command template
     * @param context  the placeholder context
     */
    public void queueAsConsole(String template, Map<String, Object> context) {
        String command = expand(template, context);
        if (!command.isEmpty()) {
            if (commandQueue != null) {
                commandQueue.queue(command);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }

    /**
     * Expands and executes a command as a player.
     *
     * @param template the command template
     * @param context  the placeholder context
     * @param player   the player to execute as
     */
    public void executeAsPlayer(String template, Map<String, Object> context, Player player) {
        String command = expand(template, context);
        if (!command.isEmpty()) {
            player.performCommand(command);
        }
    }

    /**
     * Builder for creating placeholder contexts.
     */
    public static class ContextBuilder {
        private final TemplateEngine engine;
        private final Map<String, Object> context = new HashMap<>();

        private ContextBuilder(TemplateEngine engine) {
            this.engine = engine;
        }

        /**
         * Adds a generic placeholder.
         */
        public ContextBuilder set(String key, Object value) {
            context.put(key, value);
            return this;
        }

        /**
         * Adds player-related placeholders.
         * Adds: player, player_uuid, player_x, player_y, player_z, player_world
         */
        public ContextBuilder player(Player player) {
            if (player != null) {
                context.put("player", player.getName());
                context.put("player_uuid", player.getUniqueId().toString());
                Location loc = player.getLocation();
                context.put("player_x", String.format("%.2f", loc.getX()));
                context.put("player_y", String.format("%.2f", loc.getY()));
                context.put("player_z", String.format("%.2f", loc.getZ()));
                context.put("player_world", loc.getWorld().getName());
            }
            return this;
        }

        /**
         * Adds location-related placeholders.
         * Adds: x, y, z, world
         */
        public ContextBuilder location(Location location) {
            if (location != null) {
                context.put("x", String.format("%.2f", location.getX()));
                context.put("y", String.format("%.2f", location.getY()));
                context.put("z", String.format("%.2f", location.getZ()));
                if (location.getWorld() != null) {
                    context.put("world", location.getWorld().getName());
                }
            }
            return this;
        }

        /**
         * Adds spawn-related placeholders with a prefix.
         * Adds: {prefix}_x, {prefix}_y, {prefix}_z
         */
        public ContextBuilder locationWithPrefix(String prefix, Location location) {
            if (location != null) {
                context.put(prefix + "_x", String.format("%.2f", location.getX()));
                context.put(prefix + "_y", String.format("%.2f", location.getY()));
                context.put(prefix + "_z", String.format("%.2f", location.getZ()));
                if (location.getWorld() != null) {
                    context.put(prefix + "_world", location.getWorld().getName());
                }
            }
            return this;
        }

        /**
         * Adds level-related placeholders.
         * Adds: level, player_count, avg_level
         */
        public ContextBuilder levelInfo(int level, int playerCount, double avgLevel) {
            context.put("level", level);
            context.put("player_count", playerCount);
            context.put("avg_level", String.format("%.1f", avgLevel));
            return this;
        }

        /**
         * Adds enemy archetype placeholders.
         * Adds: archetype, weight
         */
        public ContextBuilder archetype(String archetypeId, double weight) {
            context.put("archetype", archetypeId);
            context.put("weight", String.format("%.2f", weight));
            return this;
        }

        /**
         * Returns the built context map.
         */
        public Map<String, Object> build() {
            return new HashMap<>(context);
        }

        /**
         * Expands a template with this context.
         */
        public String expand(String template) {
            return engine.expand(template, context);
        }

        /**
         * Expands and executes a command as console immediately.
         */
        public void executeAsConsole(String template) {
            engine.executeAsConsole(template, context);
        }

        /**
         * Expands and queues a command for rate-limited execution as console.
         */
        public void queueAsConsole(String template) {
            engine.queueAsConsole(template, context);
        }

        /**
         * Expands and executes a command as a player.
         */
        public void executeAsPlayer(String template, Player player) {
            engine.executeAsPlayer(template, context, player);
        }
    }
}
