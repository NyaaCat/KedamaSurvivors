package cat.nyaa.survivors.i18n;

import cat.nyaa.survivors.KedamaSurvivorsPlugin;
import cat.nyaa.survivors.config.ConfigService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles internationalization and message formatting.
 * Supports color codes (§ and &) and placeholders ({key}).
 */
public class I18nService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .build();

    private final KedamaSurvivorsPlugin plugin;
    private final ConfigService configService;
    private final Map<String, String> messages = new HashMap<>();

    public I18nService(KedamaSurvivorsPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    /**
     * Loads the language file based on config settings.
     */
    public void loadLanguage() {
        messages.clear();

        String language = configService.getLanguage();
        File langFile = new File(plugin.getDataFolder(), "lang/" + language + ".yml");

        // Save default if not exists
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + language + ".yml", false);
        }

        // Load from file
        YamlConfiguration langConfig;
        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        } else {
            // Fallback to embedded resource
            InputStream stream = plugin.getResource("lang/" + language + ".yml");
            if (stream == null) {
                plugin.getLogger().warning("Language file not found: " + language + ".yml");
                return;
            }
            langConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        // Flatten nested keys
        loadSection(langConfig, "");

        plugin.getLogger().info("Loaded " + messages.size() + " messages for language: " + language);
    }

    private void loadSection(YamlConfiguration config, String prefix) {
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                messages.put(key, config.getString(key));
            }
        }
    }

    /**
     * Gets a raw message by key.
     */
    public String getRaw(String key) {
        return messages.getOrDefault(key, key);
    }

    /**
     * Gets a message with color codes parsed.
     */
    public String get(String key) {
        return parseColors(getRaw(key));
    }

    /**
     * Gets a message with placeholders replaced and color codes parsed.
     */
    public String get(String key, Object... args) {
        String message = getRaw(key);
        message = replacePlaceholders(message, args);
        return parseColors(message);
    }

    /**
     * Gets a message as a Component.
     */
    public Component getComponent(String key) {
        return LEGACY_SERIALIZER.deserialize(get(key));
    }

    /**
     * Gets a message as a Component with placeholders.
     */
    public Component getComponent(String key, Object... args) {
        return LEGACY_SERIALIZER.deserialize(get(key, args));
    }

    /**
     * Formats a message with named placeholders.
     */
    public String format(String key, Map<String, Object> placeholders) {
        String message = getRaw(key);
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return parseColors(message);
    }

    /**
     * Sends a prefixed message to a command sender.
     */
    public void send(CommandSender sender, String key) {
        sender.sendMessage(LEGACY_SERIALIZER.deserialize(
                configService.getPrefix() + get(key)));
    }

    /**
     * Sends a prefixed message with placeholders.
     */
    public void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(LEGACY_SERIALIZER.deserialize(
                configService.getPrefix() + get(key, args)));
    }

    /**
     * Sends a clickable message.
     */
    public void sendClickable(CommandSender sender, String key, String command) {
        Component message = LEGACY_SERIALIZER.deserialize(
                configService.getPrefix() + get(key));
        message = message.clickEvent(ClickEvent.runCommand(command));
        sender.sendMessage(message);
    }

    /**
     * Sends a clickable message with placeholders.
     */
    public void sendClickable(CommandSender sender, String key, String command, Object... args) {
        Component message = LEGACY_SERIALIZER.deserialize(
                configService.getPrefix() + get(key, args));
        message = message.clickEvent(ClickEvent.runCommand(command));
        sender.sendMessage(message);
    }

    /**
     * Sends an actionbar message.
     */
    public void sendActionBar(Player player, String key) {
        player.sendActionBar(getComponent(key));
    }

    /**
     * Sends an actionbar message with placeholders.
     */
    public void sendActionBar(Player player, String key, Object... args) {
        player.sendActionBar(getComponent(key, args));
    }

    /**
     * Sends a title message.
     */
    public void sendTitle(Player player, String titleKey, String subtitleKey,
                          int fadeIn, int stay, int fadeOut) {
        Component title = titleKey != null ? getComponent(titleKey) : Component.empty();
        Component subtitle = subtitleKey != null ? getComponent(subtitleKey) : Component.empty();
        player.showTitle(net.kyori.adventure.title.Title.title(
                title,
                subtitle,
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }

    /**
     * Sends a title message with placeholders.
     */
    public void sendTitle(Player player, String titleKey, String subtitleKey,
                          int fadeIn, int stay, int fadeOut, Object... args) {
        Component title = titleKey != null ? getComponent(titleKey, args) : Component.empty();
        Component subtitle = subtitleKey != null ? getComponent(subtitleKey, args) : Component.empty();
        player.showTitle(net.kyori.adventure.title.Title.title(
                title,
                subtitle,
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }

    // ==================== Internal Methods ====================

    private String parseColors(String message) {
        if (message == null) return "";
        // Convert & to § for color codes
        return message.replace('&', '§');
    }

    private String replacePlaceholders(String message, Object... args) {
        if (args == null || args.length == 0) return message;

        // Support both indexed and named placeholders
        // Indexed: {0}, {1}, etc. using positional args
        // Named: {player}, {amount}, etc. using paired args (key, value, key, value...)

        // First, try indexed replacement
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }

        // Then, try named replacement if args are paired
        if (args.length >= 2 && args.length % 2 == 0) {
            for (int i = 0; i < args.length; i += 2) {
                if (args[i] instanceof String key) {
                    message = message.replace("{" + key + "}", String.valueOf(args[i + 1]));
                }
            }
        }

        return message;
    }

    /**
     * Checks if a message key exists.
     */
    public boolean hasKey(String key) {
        return messages.containsKey(key);
    }

    // ==================== MessageKey Overloads ====================

    /**
     * Gets a raw message by MessageKey.
     */
    public String getRaw(MessageKey key) {
        return getRaw(key.getKey());
    }

    /**
     * Gets a message with color codes parsed.
     */
    public String get(MessageKey key) {
        return get(key.getKey());
    }

    /**
     * Gets a message with placeholders replaced and color codes parsed.
     */
    public String get(MessageKey key, Object... args) {
        return get(key.getKey(), args);
    }

    /**
     * Gets a message as a Component.
     */
    public Component getComponent(MessageKey key) {
        return getComponent(key.getKey());
    }

    /**
     * Gets a message as a Component with placeholders.
     */
    public Component getComponent(MessageKey key, Object... args) {
        return getComponent(key.getKey(), args);
    }

    /**
     * Sends a prefixed message to a command sender.
     */
    public void send(CommandSender sender, MessageKey key) {
        send(sender, key.getKey());
    }

    /**
     * Sends a prefixed message with placeholders.
     */
    public void send(CommandSender sender, MessageKey key, Object... args) {
        send(sender, key.getKey(), args);
    }

    /**
     * Sends a clickable message.
     */
    public void sendClickable(CommandSender sender, MessageKey key, String command) {
        sendClickable(sender, key.getKey(), command);
    }

    /**
     * Sends a clickable message with placeholders.
     */
    public void sendClickable(CommandSender sender, MessageKey key, String command, Object... args) {
        sendClickable(sender, key.getKey(), command, args);
    }

    /**
     * Sends an actionbar message.
     */
    public void sendActionBar(Player player, MessageKey key) {
        sendActionBar(player, key.getKey());
    }

    /**
     * Sends an actionbar message with placeholders.
     */
    public void sendActionBar(Player player, MessageKey key, Object... args) {
        sendActionBar(player, key.getKey(), args);
    }

    /**
     * Sends a title message.
     */
    public void sendTitle(Player player, MessageKey titleKey, MessageKey subtitleKey,
                          int fadeIn, int stay, int fadeOut) {
        sendTitle(player,
                titleKey != null ? titleKey.getKey() : null,
                subtitleKey != null ? subtitleKey.getKey() : null,
                fadeIn, stay, fadeOut);
    }

    /**
     * Sends a title message with placeholders.
     */
    public void sendTitle(Player player, MessageKey titleKey, MessageKey subtitleKey,
                          int fadeIn, int stay, int fadeOut, Object... args) {
        sendTitle(player,
                titleKey != null ? titleKey.getKey() : null,
                subtitleKey != null ? subtitleKey.getKey() : null,
                fadeIn, stay, fadeOut, args);
    }

    /**
     * Checks if a MessageKey exists.
     */
    public boolean hasKey(MessageKey key) {
        return hasKey(key.getKey());
    }
}
