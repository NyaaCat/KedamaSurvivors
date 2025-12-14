package cat.nyaa.survivors.model;

import java.util.*;

/**
 * Represents a group of equipment with leveled progression.
 * Each level contains a pool of items to choose from.
 * Immutable after construction.
 */
public class EquipmentGroup {

    private final String groupId;
    private final EquipmentType type;
    private final String displayName;
    private final int maxLevel;
    private final Map<Integer, List<ItemTemplate>> levelPools;

    private EquipmentGroup(Builder builder) {
        this.groupId = builder.groupId;
        this.type = builder.type;
        this.displayName = builder.displayName;
        this.maxLevel = builder.maxLevel;

        // Deep copy the level pools
        Map<Integer, List<ItemTemplate>> pools = new HashMap<>();
        for (Map.Entry<Integer, List<ItemTemplate>> entry : builder.levelPools.entrySet()) {
            pools.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.levelPools = Collections.unmodifiableMap(pools);
    }

    /**
     * Gets the item pool for a specific level.
     * @param level the level (1-indexed)
     * @return list of items at this level, or empty list if level doesn't exist
     */
    public List<ItemTemplate> getPool(int level) {
        return levelPools.getOrDefault(level, Collections.emptyList());
    }

    /**
     * Gets a random item from the specified level's pool.
     * @param level the level (1-indexed)
     * @param random the random instance to use
     * @return a random item, or null if level has no items
     */
    public ItemTemplate getRandomItem(int level, Random random) {
        List<ItemTemplate> pool = getPool(level);
        if (pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Gets the first item from the specified level's pool.
     * Useful for deterministic starter selection.
     * @param level the level (1-indexed)
     * @return the first item, or null if level has no items
     */
    public ItemTemplate getFirstItem(int level) {
        List<ItemTemplate> pool = getPool(level);
        if (pool.isEmpty()) return null;
        return pool.get(0);
    }

    /**
     * Checks if the given level is the maximum level.
     */
    public boolean isMaxLevel(int level) {
        return level >= maxLevel;
    }

    /**
     * Gets the next level, capped at max.
     */
    public int getNextLevel(int currentLevel) {
        return Math.min(currentLevel + 1, maxLevel);
    }

    /**
     * Checks if a level exists in this group.
     */
    public boolean hasLevel(int level) {
        return levelPools.containsKey(level) && !levelPools.get(level).isEmpty();
    }

    /**
     * Gets all available levels.
     */
    public Set<Integer> getAvailableLevels() {
        return levelPools.keySet();
    }

    // Getters
    public String getGroupId() { return groupId; }
    public EquipmentType getType() { return type; }
    public String getDisplayName() { return displayName; }
    public int getMaxLevel() { return maxLevel; }

    public static Builder builder(String groupId) {
        return new Builder(groupId);
    }

    public static class Builder {
        private final String groupId;
        private EquipmentType type = EquipmentType.WEAPON;
        private String displayName;
        private int maxLevel = 1;
        private final Map<Integer, List<ItemTemplate>> levelPools = new HashMap<>();

        private Builder(String groupId) {
            this.groupId = groupId;
        }

        public Builder type(EquipmentType type) {
            this.type = type;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder maxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
            return this;
        }

        public Builder addLevel(int level, List<ItemTemplate> items) {
            levelPools.put(level, new ArrayList<>(items));
            if (level > maxLevel) {
                maxLevel = level;
            }
            return this;
        }

        public Builder addItem(int level, ItemTemplate item) {
            levelPools.computeIfAbsent(level, k -> new ArrayList<>()).add(item);
            if (level > maxLevel) {
                maxLevel = level;
            }
            return this;
        }

        public EquipmentGroup build() {
            if (displayName == null) {
                displayName = groupId;
            }
            return new EquipmentGroup(this);
        }
    }
}
