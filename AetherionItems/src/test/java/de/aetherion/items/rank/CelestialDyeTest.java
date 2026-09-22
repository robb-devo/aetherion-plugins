package de.aetherion.items.rank;

import de.aetherion.items.skill.AetherionLevel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelestialDyeTest {

    @Test
    void paletteAnchorsOnHypixelCelestial() {
        assertEquals(0xB2FFFF, CelestialDye.PRIMARY);
        int[] palette = CelestialDye.palette();
        assertEquals(7, palette.length);
        assertTrue(Arrays.stream(palette).anyMatch(c -> c == CelestialDye.PRIMARY),
                "palette must include #B2FFFF");
        for (int rgb : palette) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            assertTrue(g >= 220 && b >= 230, "shimmer stay ice-cyan, not rainbow: " + CelestialDye.hex(rgb));
            assertTrue(b >= r, "cyan, not pink/gold: " + CelestialDye.hex(rgb));
        }
    }

    @Test
    void citrusStaysYellowGreenOrange() {
        assertEquals(0xFFE14A, CitrusDye.PRIMARY);
        int[] palette = CitrusDye.palette();
        assertEquals(7, palette.length);
        assertTrue(Arrays.stream(palette).anyMatch(c -> c == CitrusDye.PRIMARY));
        for (int rgb : palette) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            assertTrue(r >= 180 && g >= 140 && b <= 120, "citrus, not cyan/pink: " + CitrusDye.hex(rgb));
            assertFalse(rgb == CelestialDye.PRIMARY);
            assertFalse(rgb == 0xFF7AEE);
        }
        assertTrue(CitrusDye.isCitrusGroup("citrus"));
        assertFalse(CitrusDye.isCitrusGroup("monkey"));
        assertFalse(CitrusDye.isCitrusGroup("beta"));
        assertFalse(CelestialDye.isCelestialGroup("citrus"));
        assertFalse(RainbowDye.isRainbowGroup("citrus"));
    }

    @Test
    void shimmerHitsPrimaryOnMonkeyLetter() {
        String frame = CelestialDye.shimmer("[Monkey]", 0);
        assertTrue(frame.contains(CelestialDye.hex(CelestialDye.PRIMARY)), frame);
        assertTrue(frame.startsWith("&#6EE8F2[&#6EE8F2M"), frame);
    }

    @Test
    void reusableLabels() {
        assertEquals("[Monkey]", CelestialDye.labelForGroup("monkey"));
        assertTrue(CelestialDye.isCelestialGroup("monkey"));
        assertFalse(CelestialDye.isCelestialGroup("BETA"), "Beta must not use CelestialDye");
        assertFalse(CelestialDye.isCelestialGroup("admin"));
        assertEquals(CelestialDye.prefixStatic("[Monkey]"), CelestialDye.monkeyPrefixStatic());
        assertEquals(CelestialDye.monkeyBadge(), CelestialDye.badgeForGroup("beta"));
        assertTrue(CelestialDye.badgeForGroup("beta").contains(CelestialDye.hex(CelestialDye.PRIMARY)));
        assertTrue(RainbowDye.isRainbowGroup("beta"));
        assertFalse(RainbowDye.isRainbowGroup("monkey"));
        String rainbow = RainbowDye.rainbow("[Beta]", 0);
        assertTrue(rainbow.contains("&#FF7AEE") || rainbow.contains("&#FFC15A"), rainbow);
        assertFalse(rainbow.contains(CelestialDye.hex(CelestialDye.PRIMARY)),
                "Beta rainbow must not be #B2FFFF celestial: " + rainbow);
        String monkey = CelestialDye.shimmer("[Monkey]", 0);
        assertTrue(monkey.contains(CelestialDye.hex(CelestialDye.PRIMARY)), monkey);
        assertFalse(monkey.contains("&#FF7AEE"), "Monkey must not use rainbow pink: " + monkey);
        String citrus = CitrusDye.shimmer("[Citrus]", 0);
        assertTrue(citrus.contains(CitrusDye.hex(CitrusDye.PRIMARY)), citrus);
        assertFalse(citrus.contains(CelestialDye.hex(CelestialDye.PRIMARY)), citrus);
        assertFalse(citrus.contains("&#FF7AEE"), citrus);
    }

    @Test
    void contentPermissionsNeverGrantFullDev() {
        for (String node : LuckPermsSilent.CONTENT_PERMISSIONS) {
            assertFalse("aetherion.dev".equalsIgnoreCase(node),
                    "CONTENT_PERMISSIONS must not include aetherion.dev");
        }
        String joined = String.join(",", LuckPermsSilent.CONTENT_PERMISSIONS).toLowerCase(Locale.ROOT);
        assertTrue(joined.contains("aetherion.dev.content"));
        assertTrue(joined.contains("aetherion.npc.editor"));
        assertTrue(joined.contains("aetherion.flight"));
        assertFalse(joined.contains("aetherion.dev,") || joined.endsWith("aetherion.dev"));
    }

    @Test
    void extrasAreNeverXpManaged() {
        for (String extra : RankBadgeService.extraGroups()) {
            assertTrue(RankBadgeService.isPermanentExtra(extra), extra);
            assertFalse(RankBadgeService.xpManagedGroups().contains(extra),
                    "XP sync must not manage ultra " + extra);
        }
        assertTrue(RankBadgeService.isPermanentExtra("owner"));
        assertTrue(RankBadgeService.xpManagedGroups().contains("adventurer"));
        assertTrue(RankBadgeService.xpManagedGroups().contains("aetherion"));
        assertFalse(RankBadgeService.xpManagedGroups().contains("monkey"));
        assertFalse(RankBadgeService.xpManagedGroups().contains("citrus"));
        assertFalse(RankBadgeService.xpManagedGroups().contains("beta"));
        assertFalse(RankBadgeService.xpManagedGroups().contains("admin"));
        assertFalse(RankBadgeService.xpManagedGroups().contains("mvpplusplus"));
    }

    @Test
    void tabSortPutsMonkeyFirstAmongHomieCosmetics() {
        assertTrue(RankBadgeService.rankWeight("admin") > RankBadgeService.rankWeight("monkey"));
        assertTrue(RankBadgeService.rankWeight("monkey") > RankBadgeService.rankWeight("citrus"));
        assertTrue(RankBadgeService.rankWeight("citrus") > RankBadgeService.rankWeight("beta"));
        assertTrue(RankBadgeService.rankWeight("beta") > RankBadgeService.rankWeight("mvpplusplus"));
        assertTrue(RankBadgeService.rankWeight("monkey") > RankBadgeService.rankWeight("aetherion"));
    }

    @Test
    void storedPrefixesStaySplit() {
        String monkey = prefixOf("monkey");
        String citrus = prefixOf("citrus");
        String beta = prefixOf("beta");
        assertTrue(monkey.contains(CelestialDye.hex(CelestialDye.PRIMARY)), monkey);
        assertFalse(monkey.contains("&#FF7AEE"), monkey);
        assertTrue(citrus.contains(CitrusDye.hex(CitrusDye.PRIMARY)), citrus);
        assertFalse(citrus.contains(CelestialDye.hex(CelestialDye.PRIMARY)), citrus);
        assertFalse(citrus.contains("&#FF7AEE"), citrus);
        assertTrue(beta.contains("&#FF7AEE") || beta.contains("&#FFC15A"), beta);
        assertFalse(beta.contains(CelestialDye.hex(CelestialDye.PRIMARY)), beta);
    }

    @Test
    void tabPrefixDyeNeverAppliesCelestialToBeta() {
        String monkey = RankBadgeService.dyePrefixStatic("monkey");
        String citrus = RankBadgeService.dyePrefixStatic("citrus");
        String beta = RankBadgeService.dyePrefixStatic("beta");
        assertTrue(monkey.contains(CelestialDye.hex(CelestialDye.PRIMARY)), monkey);
        assertFalse(monkey.contains("&#FF7AEE"), monkey);
        assertTrue(citrus.contains(CitrusDye.hex(CitrusDye.PRIMARY)), citrus);
        assertFalse(citrus.contains(CelestialDye.hex(CelestialDye.PRIMARY)), citrus);
        assertTrue(beta.contains("&#FF7AEE") || beta.contains("&#FFC15A"), beta);
        assertFalse(beta.contains(CelestialDye.hex(CelestialDye.PRIMARY)),
                "tabPrefix/chat/list must not paint Beta with #B2FFFF: " + beta);
        assertFalse(CelestialDye.isCelestialGroup("beta"));
        assertFalse(CelestialDye.isCelestialGroup("citrus"));
    }

    @Test
    void cosmeticSitsBeforeLevelAndDoesNotReplaceTitle() {
        String levelTag = "§7[4]";
        String levelTitle = AetherionLevel.coloredTitle(1);
        assertTrue(levelTitle.contains("Adventurer"), levelTitle);
        for (String group : new String[] {"citrus", "monkey", "beta"}) {
            String dye = RankBadgeService.dyePrefixStatic(group);
            String composed = RankBadgeService.composePrefix(group, levelTag, false);
            assertTrue(composed.startsWith(dye), composed);
            int dyeAt = composed.indexOf("&#");
            int levelAt = composed.indexOf("[4]");
            assertTrue(dyeAt >= 0 && dyeAt < levelAt, group + " dye must precede the level tag: " + composed);
            assertTrue(composed.endsWith("§f"), composed);
            assertFalse(composed.contains("Adventurer"),
                    group + " prefix must keep the level title separate: " + composed);
            assertFalse(dye.contains(levelTitle), dye);
        }
        assertEquals("", RankBadgeService.dyePrefixStatic(null));
        String plain = RankBadgeService.composePrefix(null, levelTag, false);
        assertTrue(plain.startsWith(levelTag), plain);
        assertFalse(plain.contains("&#"), plain);
    }

    private static String prefixOf(String group) {
        return RankBadgeService.RANKS.stream()
                .filter(rank -> rank.group().equals(group))
                .findFirst()
                .orElseThrow()
                .prefix();
    }
}
