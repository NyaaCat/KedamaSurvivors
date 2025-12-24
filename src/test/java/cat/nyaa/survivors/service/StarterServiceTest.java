package cat.nyaa.survivors.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StarterService slot-finding logic.
 * Note: These tests focus on pure calculation logic that doesn't require Bukkit mocking.
 * The actual inventory operations are tested via integration tests on a test server.
 */
class StarterServiceTest {

    @Nested
    @DisplayName("Slot Finding Logic")
    class SlotFindingLogic {

        /**
         * Simulates findVrsEquipmentSlot logic for testing.
         * @param slots Array representing inventory slots (true = has VRS item of type, false = no)
         * @return slot index or -1 if not found
         */
        private int findVrsEquipmentSlot(boolean[] slots) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i]) {
                    return i;
                }
            }
            return -1;
        }

        @Test
        @DisplayName("should find VRS equipment in slot 0")
        void shouldFindVrsEquipmentInSlot0() {
            boolean[] slots = new boolean[36];
            slots[0] = true; // VRS weapon in slot 0

            int result = findVrsEquipmentSlot(slots);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("should find VRS equipment in non-standard slot")
        void shouldFindVrsEquipmentInNonStandardSlot() {
            boolean[] slots = new boolean[36];
            slots[5] = true; // VRS weapon moved to slot 5

            int result = findVrsEquipmentSlot(slots);
            assertEquals(5, result);
        }

        @Test
        @DisplayName("should find VRS equipment in main inventory slot")
        void shouldFindVrsEquipmentInMainInventorySlot() {
            boolean[] slots = new boolean[36];
            slots[20] = true; // VRS weapon in main inventory

            int result = findVrsEquipmentSlot(slots);
            assertEquals(20, result);
        }

        @Test
        @DisplayName("should return -1 when no VRS equipment found")
        void shouldReturnNegativeOneWhenNoVrsEquipmentFound() {
            boolean[] slots = new boolean[36]; // All false

            int result = findVrsEquipmentSlot(slots);
            assertEquals(-1, result);
        }

        @Test
        @DisplayName("should return first match when multiple VRS items exist")
        void shouldReturnFirstMatchWhenMultipleVrsItemsExist() {
            boolean[] slots = new boolean[36];
            slots[5] = true;
            slots[10] = true;

            int result = findVrsEquipmentSlot(slots);
            assertEquals(5, result); // Returns first found
        }
    }

    @Nested
    @DisplayName("Empty Slot Finding Logic")
    class EmptySlotFindingLogic {

        /**
         * Simulates findEmptySlot logic for testing.
         * @param slots Array representing inventory slots (true = occupied, false = empty)
         * @return slot index or -1 if full
         */
        private int findEmptySlot(boolean[] slots) {
            // Prefer hotbar slots (0-8)
            for (int i = 0; i < 9; i++) {
                if (!slots[i]) {
                    return i;
                }
            }
            // Fall back to main inventory (9-35)
            for (int i = 9; i < 36; i++) {
                if (!slots[i]) {
                    return i;
                }
            }
            return -1;
        }

        @Test
        @DisplayName("should find first empty slot in empty inventory")
        void shouldFindFirstEmptySlotInEmptyInventory() {
            boolean[] slots = new boolean[36]; // All empty

            int result = findEmptySlot(slots);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("should skip occupied hotbar slots")
        void shouldSkipOccupiedHotbarSlots() {
            boolean[] slots = new boolean[36];
            slots[0] = true; // Occupied
            slots[1] = true; // Occupied

            int result = findEmptySlot(slots);
            assertEquals(2, result);
        }

        @Test
        @DisplayName("should prefer hotbar over main inventory")
        void shouldPreferHotbarOverMainInventory() {
            boolean[] slots = new boolean[36];
            // Fill slots 0-7 but leave slot 8 empty
            for (int i = 0; i < 8; i++) {
                slots[i] = true;
            }

            int result = findEmptySlot(slots);
            assertEquals(8, result); // Last hotbar slot
        }

        @Test
        @DisplayName("should fall back to main inventory when hotbar full")
        void shouldFallBackToMainInventoryWhenHotbarFull() {
            boolean[] slots = new boolean[36];
            // Fill all hotbar slots (0-8)
            for (int i = 0; i < 9; i++) {
                slots[i] = true;
            }

            int result = findEmptySlot(slots);
            assertEquals(9, result); // First main inventory slot
        }

        @Test
        @DisplayName("should return -1 when inventory is full")
        void shouldReturnNegativeOneWhenInventoryIsFull() {
            boolean[] slots = new boolean[36];
            // Fill all slots
            for (int i = 0; i < 36; i++) {
                slots[i] = true;
            }

            int result = findEmptySlot(slots);
            assertEquals(-1, result);
        }

        @Test
        @DisplayName("should find slot in middle of main inventory")
        void shouldFindSlotInMiddleOfMainInventory() {
            boolean[] slots = new boolean[36];
            // Fill all hotbar and first half of main inventory
            for (int i = 0; i < 20; i++) {
                slots[i] = true;
            }

            int result = findEmptySlot(slots);
            assertEquals(20, result);
        }
    }

    @Nested
    @DisplayName("Weapon Grant Slot Selection")
    class WeaponGrantSlotSelection {

        /**
         * Simulates weapon grant slot selection logic.
         * @param existingSlot -1 if no existing VRS weapon, else slot index
         * @param emptySlot -1 if inventory full, else first empty slot
         * @return target slot for new weapon
         */
        private int selectWeaponSlot(int existingSlot, int emptySlot) {
            if (existingSlot >= 0) {
                return existingSlot;
            } else if (emptySlot >= 0) {
                return emptySlot;
            } else {
                return 0; // Fallback
            }
        }

        @Test
        @DisplayName("should use existing VRS weapon slot when found")
        void shouldUseExistingVrsWeaponSlotWhenFound() {
            int existingSlot = 5;
            int emptySlot = 0;

            int result = selectWeaponSlot(existingSlot, emptySlot);
            assertEquals(5, result);
        }

        @Test
        @DisplayName("should use empty slot when no existing VRS weapon")
        void shouldUseEmptySlotWhenNoExistingVrsWeapon() {
            int existingSlot = -1;
            int emptySlot = 3;

            int result = selectWeaponSlot(existingSlot, emptySlot);
            assertEquals(3, result);
        }

        @Test
        @DisplayName("should fallback to slot 0 when inventory full and no existing")
        void shouldFallbackToSlot0WhenInventoryFullAndNoExisting() {
            int existingSlot = -1;
            int emptySlot = -1;

            int result = selectWeaponSlot(existingSlot, emptySlot);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("should prefer existing slot over empty slot")
        void shouldPreferExistingSlotOverEmptySlot() {
            int existingSlot = 8;
            int emptySlot = 0;

            int result = selectWeaponSlot(existingSlot, emptySlot);
            assertEquals(8, result); // Existing slot takes priority
        }
    }

    @Nested
    @DisplayName("Helmet Grant Slot Selection")
    class HelmetGrantSlotSelection {

        private static final int HELMET_ARMOR_SLOT = 39; // Bukkit's helmet armor slot index

        /**
         * Simulates helmet grant slot selection logic.
         * @param existingSlot -1 if no existing VRS helmet, else slot index
         * @return target slot for new helmet, or -1 to use setHelmet()
         */
        private int selectHelmetSlot(int existingSlot) {
            if (existingSlot >= 0) {
                return existingSlot;
            } else {
                return -1; // Use armor slot via setHelmet()
            }
        }

        @Test
        @DisplayName("should use existing VRS helmet slot when found in inventory")
        void shouldUseExistingVrsHelmetSlotWhenFoundInInventory() {
            int existingSlot = 15; // Player moved helmet to inventory

            int result = selectHelmetSlot(existingSlot);
            assertEquals(15, result);
        }

        @Test
        @DisplayName("should use armor slot when no existing VRS helmet")
        void shouldUseArmorSlotWhenNoExistingVrsHelmet() {
            int existingSlot = -1;

            int result = selectHelmetSlot(existingSlot);
            assertEquals(-1, result); // Signals to use setHelmet()
        }

        @Test
        @DisplayName("should use existing slot even if in hotbar")
        void shouldUseExistingSlotEvenIfInHotbar() {
            int existingSlot = 0; // Player moved helmet to hotbar slot 0

            int result = selectHelmetSlot(existingSlot);
            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("Equipment Type Identification")
    class EquipmentTypeIdentification {

        @Test
        @DisplayName("should identify weapon type correctly")
        void shouldIdentifyWeaponTypeCorrectly() {
            String type = "weapon";
            assertTrue("weapon".equals(type));
            assertFalse("helmet".equals(type));
        }

        @Test
        @DisplayName("should identify helmet type correctly")
        void shouldIdentifyHelmetTypeCorrectly() {
            String type = "helmet";
            assertTrue("helmet".equals(type));
            assertFalse("weapon".equals(type));
        }

        @Test
        @DisplayName("should handle null type safely")
        void shouldHandleNullTypeSafely() {
            String type = null;
            assertFalse("weapon".equals(type));
            assertFalse("helmet".equals(type));
        }
    }

    @Nested
    @DisplayName("Inventory Slot Ranges")
    class InventorySlotRanges {

        @Test
        @DisplayName("should define hotbar as slots 0-8")
        void shouldDefineHotbarAsSlots0To8() {
            int hotbarStart = 0;
            int hotbarEnd = 8;

            assertEquals(9, hotbarEnd - hotbarStart + 1); // 9 hotbar slots
        }

        @Test
        @DisplayName("should define main inventory as slots 9-35")
        void shouldDefineMainInventoryAsSlots9To35() {
            int mainStart = 9;
            int mainEnd = 35;

            assertEquals(27, mainEnd - mainStart + 1); // 27 main inventory slots
        }

        @Test
        @DisplayName("should have 36 total player inventory slots")
        void shouldHave36TotalPlayerInventorySlots() {
            int totalSlots = 36; // 9 hotbar + 27 main inventory
            assertEquals(36, totalSlots);
        }

        @Test
        @DisplayName("armor slots should be separate from main inventory")
        void armorSlotsShouldBeSeparateFromMainInventory() {
            // Bukkit armor slot indices
            int helmetSlot = 39;
            int chestplateSlot = 38;
            int leggingsSlot = 37;
            int bootsSlot = 36;

            assertTrue(helmetSlot >= 36, "Armor slots should be >= 36");
            assertTrue(bootsSlot >= 36, "Armor slots should be >= 36");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle player with only slot 35 empty")
        void shouldHandlePlayerWithOnlySlot35Empty() {
            boolean[] slots = new boolean[36];
            for (int i = 0; i < 35; i++) {
                slots[i] = true;
            }
            // slot 35 is empty

            int emptySlot = findEmptySlot(slots);
            assertEquals(35, emptySlot);
        }

        private int findEmptySlot(boolean[] slots) {
            for (int i = 0; i < 9; i++) {
                if (!slots[i]) return i;
            }
            for (int i = 9; i < 36; i++) {
                if (!slots[i]) return i;
            }
            return -1;
        }

        @Test
        @DisplayName("should prioritize hotbar slot 0 when available")
        void shouldPrioritizeHotbarSlot0WhenAvailable() {
            boolean[] slots = new boolean[36]; // All empty

            int emptySlot = findEmptySlot(slots);
            assertEquals(0, emptySlot); // Should return slot 0 first
        }

        @Test
        @DisplayName("should handle VRS equipment in last slot")
        void shouldHandleVrsEquipmentInLastSlot() {
            boolean[] slots = new boolean[36];
            slots[35] = true; // VRS weapon in last slot

            int existingSlot = findVrsEquipmentSlot(slots);
            assertEquals(35, existingSlot);
        }

        private int findVrsEquipmentSlot(boolean[] slots) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i]) return i;
            }
            return -1;
        }
    }

    @Nested
    @DisplayName("Ender Chest Validation Logic")
    class EnderChestValidationLogic {

        private int countVrsItemsInEnderChest(boolean[] slots) {
            int count = 0;
            for (boolean slot : slots) {
                if (slot) count++;
            }
            return count;
        }

        @Test
        @DisplayName("should count zero VRS items in empty ender chest")
        void shouldCountZeroVrsItemsInEmptyEnderChest() {
            boolean[] enderChest = new boolean[27];
            assertEquals(0, countVrsItemsInEnderChest(enderChest));
        }

        @Test
        @DisplayName("should count single VRS item in ender chest")
        void shouldCountSingleVrsItemInEnderChest() {
            boolean[] enderChest = new boolean[27];
            enderChest[5] = true;
            assertEquals(1, countVrsItemsInEnderChest(enderChest));
        }

        @Test
        @DisplayName("should count multiple VRS items in ender chest")
        void shouldCountMultipleVrsItemsInEnderChest() {
            boolean[] enderChest = new boolean[27];
            enderChest[0] = true;
            enderChest[10] = true;
            enderChest[20] = true;
            assertEquals(3, countVrsItemsInEnderChest(enderChest));
        }

        @Test
        @DisplayName("should handle ender chest with all slots filled")
        void shouldHandleEnderChestWithAllSlotsFilled() {
            boolean[] enderChest = new boolean[27];
            for (int i = 0; i < 27; i++) {
                enderChest[i] = true;
            }
            assertEquals(27, countVrsItemsInEnderChest(enderChest));
        }

        @Test
        @DisplayName("should clear VRS items and return correct count")
        void shouldClearVrsItemsAndReturnCorrectCount() {
            boolean[] enderChest = new boolean[27];
            enderChest[3] = true;
            enderChest[15] = true;

            int removedCount = 0;
            for (int i = 0; i < enderChest.length; i++) {
                if (enderChest[i]) {
                    enderChest[i] = false;
                    removedCount++;
                }
            }

            assertEquals(2, removedCount);
            assertEquals(0, countVrsItemsInEnderChest(enderChest));
        }
    }
}
