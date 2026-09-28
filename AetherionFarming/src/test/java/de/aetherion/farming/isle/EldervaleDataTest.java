package de.aetherion.farming.isle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data-layer checks against the shipped config.yml: plots, presets, mastery curve, prize values,
 * order persistence. Coordinates come from the Farming-Eldervale schematic at the live paste.
 */
class EldervaleDataTest {

    private static YamlConfiguration config;
    private static List<IslePlots.Plot> plots;

    @BeforeAll
    static void load() throws Exception {
        try (var in = EldervaleDataTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml on the classpath");
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        plots = IslePlots.parse(config.getConfigurationSection("farm-isle-plots.plots"), null);
    }

    @Test
    void allFifteenPlotsParse() {
        assertEquals(15, plots.size());
        long landmarks = plots.stream().filter(p -> p.kind() == IslePlots.Kind.LANDMARK).count();
        long water = plots.stream().filter(p -> p.kind() == IslePlots.Kind.WATER).count();
        long fields = plots.stream().filter(p -> p.kind() == IslePlots.Kind.FIELD).count();
        assertEquals(6, landmarks);
        assertEquals(3, water);
        assertEquals(6, fields);
        for (IslePlots.Plot plot : plots) {
            assertFalse(plot.circles().isEmpty(), plot.id());
            assertFalse(plot.name().isBlank(), plot.id());
        }
    }

    @Test
    void lookupOrderIsLandmarkWaterField() {
        int last = -1;
        for (IslePlots.Plot plot : plots) {
            int rank = plot.kind().ordinal();
            assertTrue(rank >= last, "sorted by kind");
            last = rank;
        }
    }

    @Test
    void knownSpotsLandInTheRightPlot() {
        // Jump-pad landing (live farm-island.island-exit) → the first step is a discovery.
        assertEquals("harvest_hall", id(-303.5, 483.5));
        assertEquals("harvest_hall", id(-301, 502));
        // Old Granary sits inside Windward Wheat — the landmark wins.
        assertEquals("old_granary", id(-388, 631));
        assertEquals("mirror_pond", id(-303, 616));
        assertEquals("heron_lake", id(-166, 642));
        assertEquals("sunwheat_rise", id(-193, 572));
        assertEquals("carrot_belt", id(-377, 675));
        assertEquals("potato_hollows", id(-270, 599));
        assertEquals("beet_terraces", id(-294, 467));
        // Hub farm far away: nothing.
        assertNull(IslePlots.first(plots, -211.5, 183.5));
    }

    @Test
    void everyPlotCircleSitsInsideTheFootprint() {
        ConfigurationSection footprint = config.getConfigurationSection("farm-isle-footprint");
        assertNotNull(footprint);
        double minX = footprint.getDouble("min-x");
        double maxX = footprint.getDouble("max-x");
        double minZ = footprint.getDouble("min-z");
        double maxZ = footprint.getDouble("max-z");
        for (IslePlots.Plot plot : plots) {
            for (IslePlots.Circle circle : plot.circles()) {
                assertTrue(circle.x() >= minX && circle.x() <= maxX, plot.id() + " x");
                assertTrue(circle.z() >= minZ && circle.z() <= maxZ, plot.id() + " z");
            }
        }
    }

    @Test
    void everyRoleHasAPresetInsideTheFootprint() {
        ConfigurationSection presets = config.getConfigurationSection("isle-cast.presets");
        assertNotNull(presets);
        ConfigurationSection footprint = config.getConfigurationSection("farm-isle-footprint");
        for (IsleRole role : IsleRole.values()) {
            ConfigurationSection preset = presets.getConfigurationSection(role.id());
            assertNotNull(preset, "preset for " + role.id());
            double x = preset.getDouble("x");
            double z = preset.getDouble("z");
            assertTrue(x >= footprint.getDouble("min-x") && x <= footprint.getDouble("max-x"), role.id());
            assertTrue(z >= footprint.getDouble("min-z") && z <= footprint.getDouble("max-z"), role.id());
            assertTrue(preset.getDouble("y") > 60 && preset.getDouble("y") < 200, role.id() + " y");
        }
    }

    @Test
    void masteryCurve() {
        assertEquals(0, CropMastery.tierFor(0));
        assertEquals(0, CropMastery.tierFor(249));
        assertEquals(1, CropMastery.tierFor(250));
        assertEquals(3, CropMastery.tierFor(4_000));
        assertEquals(CropMastery.MAX_TIER, CropMastery.tierFor(10_000_000));
        assertEquals(0.5d, CropMastery.fill(625), 1.0e-9);
        assertEquals(1.0d, CropMastery.fill(100_000), 1.0e-9);
        assertEquals(36.0d, CropMastery.fortuneAt(CropMastery.MAX_TIER), 1.0e-9);
    }

    @Test
    void prizeValueScalesWithWeight() {
        for (IsleCrop crop : IsleCrop.values()) {
            long light = PrizeCrops.value(crop, crop.minKg());
            long heavy = PrizeCrops.value(crop, crop.maxKg());
            assertEquals(192L, light, crop.name());
            assertEquals(512L, heavy, crop.name());
            assertTrue(PrizeCrops.value(crop, (crop.minKg() + crop.maxKg()) / 2) > light);
        }
    }

    @Test
    void orderRoundTripsThroughYaml() {
        HarvestOrders.Order order = new HarvestOrders.Order(IsleCrop.CARROT, HarvestOrders.Form.COMPRESSED,
                3, 1_234L, 180, "Heron Lake anglers");
        YamlConfiguration yaml = new YamlConfiguration();
        order.write(yaml.createSection("orders.0"));
        HarvestOrders.Order back = HarvestOrders.Order.read(yaml.getConfigurationSection("orders.0"));
        assertEquals(order, back);
        assertEquals("3 Compressed Carrot", back.want());
        assertNull(HarvestOrders.Order.read(null));
    }

    @Test
    void cropsMapBothWays() {
        for (IsleCrop crop : IsleCrop.values()) {
            assertEquals(crop, IsleCrop.fromBlock(crop.block()));
            assertEquals(crop, IsleCrop.from(crop.yield()));
            assertEquals(crop, IsleCrop.byId(crop.id()));
        }
        assertEquals(IsleCrop.WHEAT, IsleCrop.fromYield(org.bukkit.Material.WHEAT_SEEDS));
    }

    @Test
    void newLoopDefaultsArePresent() {
        assertTrue(config.getBoolean("harvest-rhythm.enabled"));
        assertTrue(config.getDouble("prize-crops.chance") > 0 && config.getDouble("prize-crops.chance") < 0.01);
        assertEquals(2, config.getInt("crop-mastery.isle-weight"));
        assertTrue(config.getLong("isle-events.interval-ticks") >= 20L * 120L);
        Map<String, Object> presets = config.getConfigurationSection("isle-cast.presets").getValues(false);
        assertEquals(IsleRole.values().length, presets.size());
    }

    @Test
    void clockFormatting() {
        assertEquals("0:05", IsleText.clock(5));
        assertEquals("4:05", IsleText.clock(245));
        assertEquals("1:02:03", IsleText.clock(3723));
        assertEquals("III", IsleText.roman(3));
    }

    private static String id(double x, double z) {
        IslePlots.Plot plot = IslePlots.first(plots, x, z);
        return plot == null ? null : plot.id();
    }
}
