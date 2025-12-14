package cat.nyaa.survivors.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Template for creating items with predefined properties.
 * Immutable after construction.
 */
public class ItemTemplate {

    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final int customModelData;
    private final boolean unbreakable;

    private ItemTemplate(Builder builder) {
        this.id = builder.id;
        this.material = builder.material;
        this.displayName = builder.displayName;
        this.lore = Collections.unmodifiableList(new ArrayList<>(builder.lore));
        this.customModelData = builder.customModelData;
        this.unbreakable = builder.unbreakable;
    }

    /**
     * Creates an ItemStack from this template.
     */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (displayName != null) {
                meta.setDisplayName(displayName.replace('&', '\u00A7'));
            }

            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(line.replace('&', '\u00A7'));
                }
                meta.setLore(coloredLore);
            }

            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            meta.setUnbreakable(unbreakable);

            item.setItemMeta(meta);
        }

        return item;
    }

    // Getters
    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public int getCustomModelData() { return customModelData; }
    public boolean isUnbreakable() { return unbreakable; }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private Material material = Material.STONE;
        private String displayName;
        private List<String> lore = new ArrayList<>();
        private int customModelData;
        private boolean unbreakable;

        private Builder(String id) {
            this.id = id;
        }

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder lore(List<String> lore) {
            this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
            return this;
        }

        public Builder addLore(String line) {
            this.lore.add(line);
            return this;
        }

        public Builder customModelData(int customModelData) {
            this.customModelData = customModelData;
            return this;
        }

        public Builder unbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            return this;
        }

        public ItemTemplate build() {
            return new ItemTemplate(this);
        }
    }
}
