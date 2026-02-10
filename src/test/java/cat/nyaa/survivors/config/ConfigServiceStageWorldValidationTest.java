package cat.nyaa.survivors.config;

import cat.nyaa.survivors.util.ConfigException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceStageWorldValidationTest {

    @Test
    @DisplayName("should allow unique world assignments across stage groups")
    void shouldAllowUniqueWorldAssignmentsAcrossStageGroups() {
        Map<String, String> assigned = new HashMap<>();

        ConfigService.validateAndRegisterStageWorlds(assigned, "stage_1", List.of("Desert", "MinesV1"));
        ConfigService.validateAndRegisterStageWorlds(assigned, "stage_2", List.of("Nether", "Pirate"));

        assertEquals("stage_1", assigned.get("desert"));
        assertEquals("stage_1", assigned.get("minesv1"));
        assertEquals("stage_2", assigned.get("nether"));
        assertEquals("stage_2", assigned.get("pirate"));
    }

    @Test
    @DisplayName("should reject duplicated world across stage groups")
    void shouldRejectDuplicatedWorldAcrossStageGroups() {
        Map<String, String> assigned = new HashMap<>();
        ConfigService.validateAndRegisterStageWorlds(assigned, "stage_1", List.of("Desert"));

        ConfigException ex = assertThrows(ConfigException.class, () ->
                ConfigService.validateAndRegisterStageWorlds(assigned, "stage_2", List.of("Desert")));
        assertTrue(ex.getMessage().contains("Duplicate stage world assignment"));
    }

    @Test
    @DisplayName("should reject duplicated world by case-insensitive match")
    void shouldRejectDuplicatedWorldByCaseInsensitiveMatch() {
        Map<String, String> assigned = new HashMap<>();
        ConfigService.validateAndRegisterStageWorlds(assigned, "stage_1", List.of("Desert"));

        ConfigException ex = assertThrows(ConfigException.class, () ->
                ConfigService.validateAndRegisterStageWorlds(assigned, "stage_2", List.of("desert")));
        assertTrue(ex.getMessage().contains("Duplicate stage world assignment"));
    }

    @Test
    @DisplayName("should reject duplicated world inside the same stage group")
    void shouldRejectDuplicatedWorldInsideSameStageGroup() {
        Map<String, String> assigned = new HashMap<>();

        ConfigException ex = assertThrows(ConfigException.class, () ->
                ConfigService.validateAndRegisterStageWorlds(assigned, "stage_1", List.of("Desert", "desert")));
        assertTrue(ex.getMessage().contains("Duplicate stage world assignment"));
    }
}
