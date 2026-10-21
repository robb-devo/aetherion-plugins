package de.aetherion.items.hud;

import net.kyori.adventure.text.format.TextColor;

/**
 * Colour code the pack's text shader decodes ({@code assets/minecraft/shaders/core/rendertype_text.vsh},
 * written by {@code AetherionItems/tools/gen_carrier.py}).
 *
 * <p>A HUD glyph is coloured {@code (76, 4 * code, b)}. Code 0..39 is the status frame, top left, moved down
 * {@code 2 * code} px; code 40..63 is the radar block, top right: {@code (code - 40) / 8} is how far it
 * steps down for the vanilla effect icons, {@code (code - 40) % 8} is 0 for the disc, 1 for a marker
 * ({@code b} = its y + 64) and 2..7 for a label row 66 + 4 * (part - 2) px under the radar's top.
 * Everywhere else {@code b} is an index into {@link #PALETTE}, the colour the glyph is drawn in.
 * The generator bakes the palette into the shader: re-run it after changing the list.
 */
public final class PanelPalette {

    // palette:start
    static final int[] PALETTE = {
            0xFFFFFF, 0xE0404E, 0x6DBB3A, 0x6A6474, 0x86CDEE, 0xF2D9CF, 0xB5A9BC, 0xFFC9CC,
            0xF2C94C, 0xF2C14E, 0xE0703A, 0xBFD9EC, 0xFFD86B, 0xFF5A4A, 0x8A1E16, 0xB08A3A,
            0xFFF3DA, 0xFFC0BA, 0xA77BEA, 0xFFE08A, 0xF5C542, 0x9EE6FF, 0x8FE3D2, 0xCFC7E6,
            0x8F86A0, 0x7FD3FF, 0xD9A441, 0x62D977, 0x4FA862, 0xFFF1DA
    };
    // palette:end

    public static final int WHITE = 0;
    public static final int HP_FILL = 1;
    public static final int HP_POISON = 2;
    public static final int HP_WITHER = 3;
    public static final int HP_FROZEN = 4;
    public static final int HP_GHOST = 5;
    public static final int HP_SLASH = 6;
    public static final int HP_MAX = 7;
    public static final int ABSORB = 8;
    public static final int ST_FILL = 9;
    public static final int ST_LOW = 10;
    public static final int ST_GUARD = 11;
    public static final int ST_PERFECT = 12;
    public static final int DENY = 13;
    public static final int DENY_DIM = 14;
    public static final int ST_IDLE = 15;
    public static final int LABEL = 16;
    public static final int LABEL_DENY = 17;
    public static final int XP_FILL = 18;
    public static final int LEVEL = 19;
    public static final int COIN = 20;
    public static final int SHARD = 21;
    public static final int DEF = 22;
    public static final int PLACE = 23;
    public static final int DIM = 24;
    public static final int WEATHER = 25;
    public static final int CD_FILL = 26;
    public static final int CD_READY = 27;
    public static final int CD_READY_DIM = 28;
    public static final int CD_LABEL = 29;

    private static final int MARK = 76;
    /** Codes of the radar block: 40 + 8 * state + part. */
    static final int RADAR_BASE = 40;
    static final int PART_DISC = 0;
    static final int PART_MARK = 1;
    static final int PART_LABEL = 2;

    private PanelPalette() {
    }

    /** Status frame glyph {@code 2 * step} px down, drawn in palette colour {@code tint}. */
    static TextColor frame(int step, int tint) {
        return TextColor.color(MARK, 4 * Math.max(0, Math.min(39, step)), tint);
    }

    /** Radar block glyph: {@code state} 0..2 (effect icon rows to clear), {@code part} as in the class doc. */
    static TextColor radar(int state, int part, int b) {
        int code = RADAR_BASE + 8 * Math.max(0, Math.min(2, state)) + Math.max(0, Math.min(7, part));
        return TextColor.color(MARK, 4 * code, Math.max(0, Math.min(255, b)));
    }
}
