package cat.nyaa.survivors.config;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration data class for item templates with full NBT support.
 * Uses Bukkit's built-in ItemStack serialization to preserve all item data.
 */
public class ItemTemplateConfig {

    private String templateId;
    private String groupId;
    private int level;
    private Map<String, Object> serializedItem;

    public ItemTemplateConfig() {
        // Default constructor for YAML loading
    }

    public ItemTemplateConfig(String templateId, String groupId, int level, Map<String, Object> serializedItem) {
        this.templateId = templateId;
        this.groupId = groupId;
        this.level = level;
        this.serializedItem = serializedItem;
    }

    /**
     * Creates an ItemTemplateConfig from an ItemStack.
     *
     * @param item       the ItemStack to serialize
     * @param templateId the unique template ID
     * @param groupId    the equipment group this belongs to
     * @param level      the equipment level
     * @return a new ItemTemplateConfig
     */
    public static ItemTemplateConfig fromItemStack(ItemStack item, String templateId, String groupId, int level) {
        Map<String, Object> serialized = item.serialize();
        return new ItemTemplateConfig(templateId, groupId, level, serialized);
    }

    /**
     * Creates an ItemStack from this template.
     *
     * @return the deserialized ItemStack, or null if deserialization fails
     */
    public ItemStack toItemStack() {
        if (serializedItem == null || serializedItem.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserialize(serializedItem);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts this config to a Map for YAML serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateId", templateId);
        map.put("groupId", groupId);
        map.put("level", level);
        map.put("serializedItem", serializedItem);
        return map;
    }

    /**
     * Creates an ItemTemplateConfig from a Map (YAML deserialization).
     */
    @SuppressWarnings("unchecked")
    public static ItemTemplateConfig fromMap(Map<String, Object> map) {
        ItemTemplateConfig config = new ItemTemplateConfig();
        config.templateId = (String) map.get("templateId");
        config.groupId = (String) map.get("groupId");
        Object levelObj = map.get("level");
        config.level = levelObj instanceof Number ? ((Number) levelObj).intValue() : 1;
        config.serializedItem = (Map<String, Object>) map.get("serializedItem");
        return config;
    }

    // Getters and setters

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Map<String, Object> getSerializedItem() {
        return serializedItem;
    }

    public void setSerializedItem(Map<String, Object> serializedItem) {
        this.serializedItem = serializedItem;
    }

    @Override
    public String toString() {
        return "ItemTemplateConfig{" +
                "templateId='" + templateId + '\'' +
                ", groupId='" + groupId + '\'' +
                ", level=" + level +
                '}';
    }
}
