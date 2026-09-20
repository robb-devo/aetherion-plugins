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
        assertEquals("[Beta]", CelestialDye.labelForGroup("beta"));
        assertTrue(CelestialDye.isCelestialGroup("monkey"));
        assertTrue(CelestialDye.isCelestialGroup("BETA"));
        assertFalse(CelestialDye.isCelestialGroup("admin"));
        assertEquals(7, CelestialDye.animationFrames("[Beta]").length);
        assertEquals(CelestialDye.prefixStatic("[Monkey]"), CelestialDye.monkeyPrefixStatic());
        assertEquals(CelestialDye.prefixStatic("[Beta]"), CelestialDye.betaPrefixStatic());
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
}
