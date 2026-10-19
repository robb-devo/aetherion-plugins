package de.aetherion.items.hud;

import net.kyori.adventure.text.format.TextColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Colour code the pack's text shader decodes ({@code assets/minecraft/shaders/core/rendertype_text.vsh}).
 *
 * <p>An anchored glyph is coloured {@code (76, 32 + 4 * code, b)}: code 0..15 is a status-panel row and
 * {@code b} indexes {@link #PALETTE}; code 16 is the radar body, code 17 a radar marker with {@code b} = its
 * y offset + 128. {@code AetherionItems/tools/gen_panel.py} bakes the palette below into the shader, so the
 * list is append-only: re-run the generator after adding a colour.
 */
public final class PanelPalette {

    // palette:start
    static final int[] PALETTE = {
            0xFFFFFF, 0xD93A40, 0x6DBB3A, 0x5A5462, 0x86CDEE, 0xF2D9CF, 0xB5A9BC, 0xFFC9CC,
            0xF2C94C, 0xCFF5EF, 0xD9A441, 0xFF5A4A, 0x62D977, 0x4FA862, 0xFFF1DA, 0xFFC2BA,
            0xF2C14E, 0xE0703A, 0xBFD9EC, 0xFFD86B, 0x8A1E16, 0xFFF3DA, 0xFFC0BA, 0xA77BEA,
            0xFFE08A, 0xF5C542, 0x9EE6FF, 0xCFC7D9, 0x8F86A0, 0x7FD3FF
    };
    // palette:end

    public static final TextColor XP_FILL = TextColor.color(0xA77BEA);
    public static final TextColor LEVEL = TextColor.color(0xFFE08A);
    public static final TextColor COIN = TextColor.color(0xF5C542);
    public static final TextColor SHARD = TextColor.color(0x9EE6FF);
    public static final TextColor PLACE = TextColor.color(0xCFC7D9);
    public static final TextColor DIM = TextColor.color(0x8F86A0);
    public static final TextColor WEATHER = TextColor.color(0x7FD3FF);

    private static final int MARK = 76;
    private static final Map<Integer, Integer> INDEX = new HashMap<>();

    static {
        for (int i = 0; i < PALETTE.length; i++) {
            INDEX.putIfAbsent(PALETTE[i], i);
        }
    }

    private PanelPalette() {
    }

    /** Status-panel row {@code row} (0..15) in {@code tint}. */
    public static TextColor panel(int row, TextColor tint) {
        return TextColor.color(MARK, 32 + 4 * Math.max(0, Math.min(15, row)), index(tint));
    }

    public static TextColor radarBody(TextColor tint) {
        return TextColor.color(MARK, 96, index(tint));
    }

    /** Radar marker drawn {@code yOffset} px below the radar's top edge. */
    public static TextColor radarMark(int yOffset) {
        return TextColor.color(MARK, 100, Math.max(0, Math.min(255, 128 + yOffset)));
    }

    /** Exact entry, else the nearest one. */
    static int index(TextColor tint) {
        int rgb = tint == null ? 0xFFFFFF : tint.value();
        Integer hit = INDEX.get(rgb);
        if (hit != null) {
            return hit;
        }
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int p = PALETTE[i];
            long dr = (p >> 16 & 255) - (rgb >> 16 & 255);
            long dg = (p >> 8 & 255) - (rgb >> 8 & 255);
            long db = (p & 255) - (rgb & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
