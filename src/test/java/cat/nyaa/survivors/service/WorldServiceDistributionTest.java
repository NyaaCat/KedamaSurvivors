package cat.nyaa.survivors.service;

import cat.nyaa.survivors.config.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldServiceDistributionTest {

    @Test
    @DisplayName("should always prefer zero-player worlds when available")
    void shouldPreferZeroPlayerWorlds() {
        List<WorldService.WorldLoadMetric> metrics = List.of(
                metric("busy_a", 4, 6, 1.0),
                metric("empty_a", 0, 2, 1.0),
                metric("empty_b", 0, 8, 1.0)
        );

        for (int i = 0; i < 20; i++) {
            int selected = WorldService.selectDistributedIndex(metrics, new Random(i));
            assertTrue(selected == 1 || selected == 2, "selected index=" + selected);
        }
    }

    @Test
    @DisplayName("should use spawn-point/player ratio when all worlds have players")
    void shouldUseCapacityRatioWhenAllWorldsBusy() {
        List<WorldService.WorldLoadMetric> metrics = List.of(
                metric("crowded", 10, 4, 1.0), // 4 / 11 ~= 0.36
                metric("roomy", 2, 6, 1.0)     // 6 / 3  = 2.00
        );

        int selected = WorldService.selectDistributedIndex(metrics, new FixedRandom(0.95));
        assertEquals(1, selected);
    }

    @Test
    @DisplayName("should respect world weight inside the same load level")
    void shouldRespectWeightWhenLoadIsSame() {
        List<WorldService.WorldLoadMetric> metrics = List.of(
                metric("low_weight", 0, 4, 1.0),
                metric("high_weight", 0, 4, 4.0)
        );

        // High random roll should fall into the second world's larger score segment.
        int selected = WorldService.selectDistributedIndex(metrics, new FixedRandom(0.95));
        assertEquals(1, selected);
    }

    private static WorldService.WorldLoadMetric metric(String worldName, int players, int spawnPoints, double weight) {
        ConfigService.CombatWorldConfig world = new ConfigService.CombatWorldConfig();
        world.name = worldName;
        world.weight = weight;
        return new WorldService.WorldLoadMetric(world, players, spawnPoints, weight);
    }

    private static class FixedRandom extends Random {
        private final double value;

        private FixedRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }
}
