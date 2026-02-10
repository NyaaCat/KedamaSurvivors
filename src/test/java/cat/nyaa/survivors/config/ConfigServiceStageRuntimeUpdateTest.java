package cat.nyaa.survivors.config;

import cat.nyaa.survivors.util.ConfigException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceStageRuntimeUpdateTest {

    @Test
    @DisplayName("should update stage worlds when no duplicate assignment exists")
    void shouldUpdateStageWorldsWithoutDuplicate() {
        ConfigService config = new ConfigService(null);
        ConfigService.StageGroupConfig stage1 = stage("stage_1", List.of("Desert"));
        ConfigService.StageGroupConfig stage2 = stage("stage_2", List.of("Mines"));
        setStageGroups(config, new ArrayList<>(List.of(stage1, stage2)));

        boolean updated = config.setStageGroupWorldNames("stage_2", List.of("Nether", "End"));
        assertTrue(updated);
        assertEquals(List.of("Nether", "End"), config.getStageGroupById("stage_2").orElseThrow().worldNames);
    }

    @Test
    @DisplayName("should reject duplicate world when stage worlds are updated at runtime")
    void shouldRejectDuplicateWorldAtRuntimeUpdate() {
        ConfigService config = new ConfigService(null);
        ConfigService.StageGroupConfig stage1 = stage("stage_1", List.of("Desert"));
        ConfigService.StageGroupConfig stage2 = stage("stage_2", List.of("Mines"));
        setStageGroups(config, new ArrayList<>(List.of(stage1, stage2)));

        ConfigException ex = assertThrows(ConfigException.class,
                () -> config.setStageGroupWorldNames("stage_2", List.of("desert")));
        assertTrue(ex.getMessage().contains("Duplicate stage world assignment"));
    }

    @Test
    @DisplayName("should resolve stage group id case-insensitively")
    void shouldResolveStageGroupIdCaseInsensitive() {
        ConfigService config = new ConfigService(null);
        ConfigService.StageGroupConfig stage1 = stage("Stage_One", List.of("Desert"));
        setStageGroups(config, new ArrayList<>(List.of(stage1)));

        assertTrue(config.getStageGroupById("stage_one").isPresent());
    }

    private static ConfigService.StageGroupConfig stage(String id, List<String> worlds) {
        ConfigService.StageGroupConfig group = new ConfigService.StageGroupConfig();
        group.groupId = id;
        group.displayName = id;
        group.worldNames = new ArrayList<>(worlds);
        group.startEnemyLevel = 1;
        group.requiredBatteries = 1;
        group.clearRewardCoins = 0;
        group.clearRewardPermaScore = 0;
        return group;
    }

    private static void setStageGroups(ConfigService config, List<ConfigService.StageGroupConfig> groups) {
        try {
            Field field = ConfigService.class.getDeclaredField("stageGroups");
            field.setAccessible(true);
            field.set(config, groups);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
