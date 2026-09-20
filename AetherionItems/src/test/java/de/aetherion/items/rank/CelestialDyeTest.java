package de.aetherion.items.rank;

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
        assertFalse(RankBadgeService.xpManagedGroups().contains("beta"));
        assertFalse(RankBadgeService.xpManagedGroups().contains("admin"));
        assertFalse(RankBadgeService.xpManagedGroups().contains("mvpplusplus"));
    }

    @Test
    void tabSortPutsMonkeyFirstAmongHomieCosmetics() {
        assertTrue(RankBadgeService.rankWeight("admin") > RankBadgeService.rankWeight("monkey"));
        assertTrue(RankBadgeService.rankWeight("monkey") > RankBadgeService.rankWeight("beta"));
        assertTrue(RankBadgeService.rankWeight("beta") > RankBadgeService.rankWeight("mvpplusplus"));
        assertTrue(RankBadgeService.rankWeight("monkey") > RankBadgeService.rankWeight("aetherion"));
    }

    @Test
    void storedPrefixesStaySplit() {
        String monkey = prefixOf("monkey");
        String beta = prefixOf("beta");
        assertTrue(monkey.contains(CelestialDye.hex(CelestialDye.PRIMARY)), monkey);
        assertFalse(monkey.contains("&#FF7AEE"), monkey);
        assertTrue(beta.contains("&#FF7AEE") || beta.contains("&#FFC15A"), beta);
        assertFalse(beta.contains(CelestialDye.hex(CelestialDye.PRIMARY)), beta);
    }

    @Test
    void tabPrefixDyeNeverAppliesCelestialToBeta() {
        String monkey = RankBadgeService.dyePrefixStatic("monkey");
        String beta = RankBadgeService.dyePrefixStatic("beta");
        assertTrue(monkey.contains(CelestialDye.hex(CelestialDye.PRIMARY)), monkey);
        assertFalse(monkey.contains("&#FF7AEE"), monkey);
        assertTrue(beta.contains("&#FF7AEE") || beta.contains("&#FFC15A"), beta);
        assertFalse(beta.contains(CelestialDye.hex(CelestialDye.PRIMARY)),
                "tabPrefix/chat/list must not paint Beta with #B2FFFF: " + beta);
        assertFalse(CelestialDye.isCelestialGroup("beta"));
    }

    private static String prefixOf(String group) {
        return RankBadgeService.RANKS.stream()
                .filter(rank -> rank.group().equals(group))
                .findFirst()
                .orElseThrow()
                .prefix();
    }
}
