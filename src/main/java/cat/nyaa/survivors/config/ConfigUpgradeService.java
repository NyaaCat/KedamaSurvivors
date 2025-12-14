package cat.nyaa.survivors.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for upgrading configuration and language files with missing entries.
 * Preserves existing user values while adding new defaults from embedded resources.
 */
public class ConfigUpgradeService {

    private final Logger logger;

    public ConfigUpgradeService(Logger logger) {
        this.logger = logger;
    }

    /**
     * Upgrades a configuration file by adding missing keys from defaults.
     * Preserves all existing user values.
     *
     * @param userFile      the user's configuration file
     * @param defaultStream input stream to the default configuration resource
     * @return the number of keys added, or -1 if an error occurred
     */
    public int upgradeFile(File userFile, InputStream defaultStream) {
        if (defaultStream == null) {
            logger.warning("Default configuration stream is null, cannot upgrade");
            return -1;
        }

        if (!userFile.exists()) {
            // No user file - nothing to upgrade, defaults will be copied by plugin
            return 0;
        }

        try {
            YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

            List<String> addedKeys = new ArrayList<>();

            // Iterate through all keys in defaults (including nested paths)
            for (String key : defaultConfig.getKeys(true)) {
                // Skip section keys - only add leaf values
                if (defaultConfig.isConfigurationSection(key)) {
                    continue;
                }

                // If user config doesn't have this key, add it
                if (!userConfig.contains(key)) {
                    Object value = defaultConfig.get(key);
                    userConfig.set(key, value);
                    addedKeys.add(key);
                }
            }

            // Save if changes were made
            if (!addedKeys.isEmpty()) {
                userConfig.save(userFile);
                logger.info("Config upgrade: Added " + addedKeys.size() + " missing entries to " + userFile.getName());

                // Log individual keys at fine level
                for (String key : addedKeys) {
                    logger.fine("  + " + key);
                }
            }

            return addedKeys.size();

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to upgrade config file: " + userFile.getName(), e);
            return -1;
        }
    }

    /**
     * Upgrades a configuration file, getting defaults from plugin resources.
     *
     * @param userFile        the user's configuration file
     * @param resourcePath    the path to the default resource
     * @param resourceLoader  class loader to load resources from
     * @return the number of keys added, or -1 if an error occurred
     */
    public int upgradeFromResource(File userFile, String resourcePath, ClassLoader resourceLoader) {
        try (InputStream defaultStream = resourceLoader.getResourceAsStream(resourcePath)) {
            if (defaultStream == null) {
                logger.warning("Default resource not found: " + resourcePath);
                return -1;
            }
            return upgradeFile(userFile, defaultStream);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read default resource: " + resourcePath, e);
            return -1;
        }
    }
}
