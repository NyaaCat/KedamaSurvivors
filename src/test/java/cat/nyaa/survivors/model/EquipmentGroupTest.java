package cat.nyaa.survivors.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EquipmentGroup.
 */
class EquipmentGroupTest {

    private EquipmentGroup equipmentGroup;

    @BeforeEach
    void setUp() {
        equipmentGroup = EquipmentGroup.builder("test_weapon")
                .type(EquipmentType.WEAPON)
                .displayName("Test Weapon")
                .maxLevel(5)
                .build();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        @DisplayName("should initialize with correct id and type")
        void shouldInitializeWithCorrectIdAndType() {
            assertEquals("test_weapon", equipmentGroup.getGroupId());
            assertEquals(EquipmentType.WEAPON, equipmentGroup.getType());
        }

        @Test
        @DisplayName("should initialize with correct max level")
        void shouldInitializeWithCorrectMaxLevel() {
            assertEquals(5, equipmentGroup.getMaxLevel());
        }

        @Test
        @DisplayName("should support helmet type")
        void shouldSupportHelmetType() {
            EquipmentGroup helmetGroup = EquipmentGroup.builder("test_helmet")
                    .type(EquipmentType.HELMET)
                    .maxLevel(3)
                    .build();
            assertEquals(EquipmentType.HELMET, helmetGroup.getType());
        }

        @Test
        @DisplayName("should use groupId as display name when not set")
        void shouldUseGroupIdAsDisplayNameWhenNotSet() {
            EquipmentGroup group = EquipmentGroup.builder("my_group").build();
            assertEquals("my_group", group.getDisplayName());
        }

        @Test
        @DisplayName("should use custom display name when set")
        void shouldUseCustomDisplayNameWhenSet() {
            assertEquals("Test Weapon", equipmentGroup.getDisplayName());
        }
    }

    @Nested
    @DisplayName("Level Management")
    class LevelManagement {

        @Test
        @DisplayName("should add items to level pool via builder")
        void shouldAddItemsToLevelPoolViaBuilder() {
            ItemTemplate item1 = ItemTemplate.builder("item1").displayName("Stone Sword").build();
            ItemTemplate item2 = ItemTemplate.builder("item2").displayName("Iron Sword").build();

            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .addItem(1, item1)
                    .addItem(1, item2)
                    .build();

            List<ItemTemplate> level1Items = group.getPool(1);
            assertEquals(2, level1Items.size());
            assertTrue(level1Items.contains(item1));
            assertTrue(level1Items.contains(item2));
        }

        @Test
        @DisplayName("should return empty list for unpopulated level")
        void shouldReturnEmptyListForUnpopulatedLevel() {
            List<ItemTemplate> items = equipmentGroup.getPool(3);
            assertNotNull(items);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("should check if level has items")
        void shouldCheckIfLevelHasItems() {
            assertFalse(equipmentGroup.hasLevel(1));

            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .addItem(1, ItemTemplate.builder("item1").displayName("Sword").build())
                    .build();

            assertTrue(group.hasLevel(1));
            assertFalse(group.hasLevel(2));
        }

        @Test
        @DisplayName("should check if level is at max")
        void shouldCheckIfLevelIsAtMax() {
            assertFalse(equipmentGroup.isMaxLevel(4));
            assertTrue(equipmentGroup.isMaxLevel(5));
            assertTrue(equipmentGroup.isMaxLevel(6)); // Above max is also considered max
        }

        @Test
        @DisplayName("should get next level capped at max")
        void shouldGetNextLevelCappedAtMax() {
            assertEquals(2, equipmentGroup.getNextLevel(1));
            assertEquals(5, equipmentGroup.getNextLevel(4));
            assertEquals(5, equipmentGroup.getNextLevel(5)); // Already at max
            assertEquals(5, equipmentGroup.getNextLevel(10)); // Above max
        }

        @Test
        @DisplayName("should auto-update maxLevel from added items")
        void shouldAutoUpdateMaxLevelFromAddedItems() {
            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .maxLevel(3)
                    .addItem(5, ItemTemplate.builder("item1").displayName("Sword").build())
                    .build();

            // maxLevel should be updated to 5
            assertEquals(5, group.getMaxLevel());
        }
    }

    @Nested
    @DisplayName("Random Selection")
    class RandomSelection {

        @Test
        @DisplayName("should return null for empty level")
        void shouldReturnNullForEmptyLevel() {
            ItemTemplate result = equipmentGroup.getRandomItem(1, new Random());
            assertNull(result);
        }

        @Test
        @DisplayName("should return single item when only one exists")
        void shouldReturnSingleItemWhenOnlyOneExists() {
            ItemTemplate item = ItemTemplate.builder("item1").displayName("Sword").build();
            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .addItem(1, item)
                    .build();

            ItemTemplate result = group.getRandomItem(1, new Random());
            assertEquals(item, result);
        }

        @Test
        @DisplayName("should return an item from the pool")
        void shouldReturnAnItemFromThePool() {
            ItemTemplate item1 = ItemTemplate.builder("item1").displayName("Stone Sword").build();
            ItemTemplate item2 = ItemTemplate.builder("item2").displayName("Iron Sword").build();
            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .addItem(2, item1)
                    .addItem(2, item2)
                    .build();

            ItemTemplate result = group.getRandomItem(2, new Random());
            assertNotNull(result);
            assertTrue(result.equals(item1) || result.equals(item2));
        }
    }

    @Nested
    @DisplayName("First Item Selection")
    class FirstItemSelection {

        @Test
        @DisplayName("should return null for empty level")
        void shouldReturnNullForEmptyLevel() {
            ItemTemplate result = equipmentGroup.getFirstItem(1);
            assertNull(result);
        }

        @Test
        @DisplayName("should return first item deterministically")
        void shouldReturnFirstItemDeterministically() {
            ItemTemplate item1 = ItemTemplate.builder("item1").displayName("Stone Sword").build();
            ItemTemplate item2 = ItemTemplate.builder("item2").displayName("Iron Sword").build();
            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .addItem(1, item1)
                    .addItem(1, item2)
                    .build();

            // Should always return the first item
            assertEquals(item1, group.getFirstItem(1));
            assertEquals(item1, group.getFirstItem(1));
        }
    }

    @Nested
    @DisplayName("Available Levels")
    class AvailableLevels {

        @Test
        @DisplayName("should return empty set when no levels defined")
        void shouldReturnEmptySetWhenNoLevelsDefined() {
            assertTrue(equipmentGroup.getAvailableLevels().isEmpty());
        }

        @Test
        @DisplayName("should return all defined levels")
        void shouldReturnAllDefinedLevels() {
            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .addItem(1, ItemTemplate.builder("item1").displayName("Sword").build())
                    .addItem(3, ItemTemplate.builder("item3").displayName("Diamond Sword").build())
                    .addItem(5, ItemTemplate.builder("item5").displayName("Netherite Sword").build())
                    .build();

            var levels = group.getAvailableLevels();
            assertEquals(3, levels.size());
            assertTrue(levels.contains(1));
            assertTrue(levels.contains(3));
            assertTrue(levels.contains(5));
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should return unmodifiable pool list")
        void shouldReturnUnmodifiablePoolList() {
            EquipmentGroup group = EquipmentGroup.builder("weapons")
                    .addItem(1, ItemTemplate.builder("item1").displayName("Sword").build())
                    .build();

            List<ItemTemplate> pool = group.getPool(1);
            assertThrows(UnsupportedOperationException.class, () -> {
                pool.add(ItemTemplate.builder("item2").displayName("Another Sword").build());
            });
        }
    }
}
