package cat.nyaa.survivors.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template engine for expanding command templates with placeholders.
 * Supports placeholders in the format {placeholder_name}.
 */
public class TemplateEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    /**
     * Expands a command template with the given context.
     *
     * @param template the command template with placeholders
     * @param context  map of placeholder names to values
     * @return the expanded command string
     */
    public String expand(String template, Map<String, Object> context) {
        if (template == null) return "";
        if (context == null || context.isEmpty()) return template;

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = context.get(placeholder);
            String replacement = value != null ? String.valueOf(value) : matcher.group(0);
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
     * Expands and executes a command as the console.
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
         * Expands and executes a command as console.
         */
        public void executeAsConsole(String template) {
            engine.executeAsConsole(template, context);
        }

        /**
         * Expands and executes a command as a player.
         */
        public void executeAsPlayer(String template, Player player) {
            engine.executeAsPlayer(template, context, player);
        }
    }
}
