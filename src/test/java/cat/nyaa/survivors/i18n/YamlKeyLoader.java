package cat.nyaa.survivors.i18n;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads and flattens YAML language files into dot-notation keys.
 */
class YamlKeyLoader {

    /**
     * Load all keys from YAML file in dot notation.
     * Example: info.xp_gained, admin.world.help.header
     */
    public Set<String> loadKeys(InputStream yamlStream) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(yamlStream);

        Set<String> keys = new HashSet<>();
        if (data != null) {
            flatten("", data, keys);
        }
        return keys;
    }

    /**
     * Recursively flatten nested YAML structure.
     */
    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> map, Set<String> keys) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                // Nested section - recurse
                flatten(key, (Map<String, Object>) value, keys);
            } else if (value instanceof String) {
                // Leaf node - actual message
                keys.add(key);
            } else if (value instanceof List) {
                // Lists in YAML (like gui.upgrade_weapon_item.lore)
                // Treat as a leaf node, add the key
                keys.add(key);
            }
        }
    }
}
