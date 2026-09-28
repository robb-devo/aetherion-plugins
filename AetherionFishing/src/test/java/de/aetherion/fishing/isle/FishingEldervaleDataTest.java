package de.aetherion.fishing.isle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data-layer checks against the shipped config.yml: waters, species homes, shoal spots, presets,
 * Eldermaw circuits, rank curve, trophy values. Coordinates come from Fishing-Eldervale-Island.schem
 * at the live paste (−585 90 −649).
 */
class FishingEldervaleDataTest {

    private static YamlConfiguration config;
    private static List<Waters.Water> waters;
    private static double minX;
    private static double maxX;
    private static double minZ;
    private static double maxZ;

    @BeforeAll
    static void load() throws Exception {
        try (var in = FishingEldervaleDataTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml on the classpath");
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        waters = Waters.parse(config.getConfigurationSection("waters"), null);
        ConfigurationSection box = config.getConfigurationSection("fish-isle.footprint");
        assertNotNull(box);
        minX = box.getDouble("min-x");
        maxX = box.getDouble("max-x");
        minZ = box.getDouble("min-z");
        maxZ = box.getDouble("max-z");
    }

    private static boolean inFootprint(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static Waters.Water water(String id) {
        for (Waters.Water water : waters) {
            if (water.id().equals(id)) {
                return water;
            }
        }
        return null;
    }

    @Test
    void twelveWatersParse() {
        assertEquals(12, waters.size());
        assertEquals(7, waters.stream().filter(w -> w.kind() == Waters.Kind.LAKE).count());
        assertEquals(4, waters.stream().filter(w -> w.kind() == Waters.Kind.TARN).count());
        assertEquals(1, waters.stream().filter(w -> w.kind() == Waters.Kind.FOUNTAIN).count());
        for (Waters.Water water : waters) {
            assertFalse(water.circles().isEmpty(), water.id());
            assertFalse(water.name().isBlank(), water.id());
            for (Waters.Circle circle : water.circles()) {
                assertTrue(inFootprint(circle.x() - circle.radius(), circle.z() - circle.radius())
                        && inFootprint(circle.x() + circle.radius(), circle.z() + circle.radius()), water.id() + " inside footprint");
            }
        }
    }

    @Test
    void lookupOrderIsFountainTarnLake() {
        int last = -1;
        for (Waters.Water water : waters) {
            int rank = water.kind().ordinal();
            assertTrue(rank >= last, "sorted by kind");
            last = rank;
        }
    }

    @Test
    void everySpeciesLivesSomewhere() {
        Set<String> ids = new HashSet<>();
        for (Waters.Water water : waters) {
            ids.add(water.id());
        }
        for (Species species : Species.values()) {
            boolean anywhere = false;
            for (Waters.Water water : waters) {
                if (species.home(water) > 0.0d) {
                    anywhere = true;
                }
            }
            assertTrue(anywhere, species.id() + " has a home water");
            assertTrue(species.minKg() > 0.0d && species.maxKg() > species.minKg(), species.id() + " weight range");
            for (String home : species.homeIds()) {
                assertTrue(home.equals("LAKE") || home.equals("TARN") || ids.contains(home),
                        species.id() + " home '" + home + "' is a configured water");
            }
        }
    }

    @Test
    void speciesMixPerKind() {
        long legendary = java.util.Arrays.stream(Species.values()).filter(s -> s.rarity() == Species.Rarity.LEGENDARY).count();
        long rare = java.util.Arrays.stream(Species.values()).filter(s -> s.rarity() == Species.Rarity.RARE).count();
        assertEquals(18, Species.values().length);
        assertEquals(2, legendary);
        assertEquals(5, rare);
        // The Pale Ghost only on rainy nights; night-only species say so.
        assertEquals(0.0d, Species.PALE_GHOST.when(false, true));
        assertEquals(0.0d, Species.PALE_GHOST.when(true, false));
        assertTrue(Species.PALE_GHOST.when(true, true) > 0.0d);
        assertTrue(Species.LANTERN_EEL.when(true, false) > Species.LANTERN_EEL.when(false, false));
    }

    @Test
    void shoalSpotsSitOnLakeWaters() {
        ConfigurationSection spots = config.getConfigurationSection("shoals.spots");
        assertNotNull(spots);
        int total = 0;
        for (String id : spots.getKeys(false)) {
            Waters.Water water = water(id);
            assertNotNull(water, "shoal water " + id);
            assertEquals(Waters.Kind.LAKE, water.kind(), id);
            for (String raw : spots.getStringList(id)) {
                double[] v = LakeText.numbers(raw, 3);
                assertNotNull(v, raw);
                assertTrue(inFootprint(v[0], v[2]), raw);
                assertEquals(89.0d, v[1], raw);
                total++;
            }
        }
        assertEquals(24, total);
    }

    @Test
    void presetsLandingAndCircuitsInsideFootprint() {
        for (LakeRole role : LakeRole.values()) {
            ConfigurationSection preset = config.getConfigurationSection("isle-cast.presets." + role.id());
            assertNotNull(preset, "preset " + role.id());
            assertTrue(inFootprint(preset.getDouble("x"), preset.getDouble("z")), role.id());
        }
        ConfigurationSection landing = config.getConfigurationSection("fish-isle.landing");
        assertNotNull(landing);
        assertTrue(inFootprint(landing.getDouble("x"), landing.getDouble("z")));
        List<String> circuits = config.getStringList("lake-events.eldermaw.circuits");
        assertEquals(2, circuits.size());
        for (String raw : circuits) {
            double[] v = LakeText.numbers(raw, 3);
            assertNotNull(v);
            assertTrue(inFootprint(v[0] - v[2], v[1] - v[2]) && inFootprint(v[0] + v[2], v[1] + v[2]), raw);
        }
        assertEquals(6, config.getStringList("lake-events.bells").size());
    }

    @Test
    void rankCurveIsReachable() {
        for (int i = 1; i < AnglerLog.RANK_POINTS.length; i++) {
            assertTrue(AnglerLog.RANK_POINTS[i] > AnglerLog.RANK_POINTS[i - 1]);
        }
        assertTrue(AnglerLog.RANK_POINTS[AnglerLog.MAX_RANK] <= AnglerLog.maxPoints());
        assertEquals(0, AnglerLog.rankFor(0));
        assertEquals(AnglerLog.MAX_RANK, AnglerLog.rankFor(AnglerLog.maxPoints()));
    }

    @Test
    void trophyValuesClimbWithWeightAndRarity() {
        Species perch = Species.SILVER_PERCH;
        assertTrue(Trophies.value(perch, perch.maxKg()) > Trophies.value(perch, perch.minKg()));
        assertTrue(Trophies.value(Species.GOLDSCALE_EMPEROR, 25.0d) > Trophies.value(Species.MIRRORBACK_STURGEON, 70.0d));
        assertTrue(Trophies.isTrophy(Species.MIRRORBACK_STURGEON, 15.0d), "rares are always trophies");
        assertFalse(Trophies.isTrophy(perch, perch.minKg()));
        assertTrue(Trophies.isTrophy(perch, perch.maxKg()));
    }

    @Test
    void baitRecipesMakeSomething() {
        for (Bait bait : Bait.values()) {
            assertTrue(bait.charges() > 0, bait.id());
            assertFalse(bait.plain().isEmpty() && bait.custom().isEmpty(), bait.id() + " has ingredients");
            assertFalse(bait.effect().isEmpty(), bait.id());
        }
    }

}
