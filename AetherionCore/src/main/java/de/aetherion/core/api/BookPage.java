package de.aetherion.core.api;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * The Manager's book page in one inventory title: the page glyph ({@code }), then a caption under
 * every tile, written in the vanilla font shifted down to the caption rows. Pack fonts
 * {@code aetherion:mgr_caption_1..3} (tools/gen_gui_chrome.py writes them) are the default ascii, accented and
 * nonlatin pages with their ascent lowered to GUI y 37, 73 and 109: just under the tiles on rows 0, 2 and 4.
 * Colour carries the state: ink = open, faded = locked, amber = opens next.
 */
public final class BookPage {

    public enum State { OPEN, LOCKED, NEXT }

    public record Caption(int slot, String text, State state) {
    }

    private static final Key[] FONTS = {
            Key.key("aetherion", "mgr_caption_1"),
            Key.key("aetherion", "mgr_caption_2"),
            Key.key("aetherion", "mgr_caption_3"),
    };
    private static final TextColor INK = TextColor.color(0x3B2A1A);
    private static final TextColor FADED = TextColor.color(0x9C8B6E);
    private static final TextColor AMBER = TextColor.color(0xA8590A);
    /**
     * Cursor after the page glyph: the title starts at GUI x 8, {@code } moves it 48 left, and the 256 px page
     * advances 257 (its last column carries a 1-alpha pin so the client measures the full width).
     */
    private static final int AFTER_PAGE = 8 - 48 + 257;
    /** Captions stay on the parchment. */
    private static final int MIN_X = 3;
    private static final int MAX_X = 173;

    private BookPage() {
    }

    /** The page drawn by glyph {@code page} (a 256 px book page with the pin column), captions under its tiles. */
    public static Component title(char page, List<Caption> captions) {
        TextComponent.Builder out = Component.text().append(Component.text("" + page, NamedTextColor.WHITE));
        int cursor = AFTER_PAGE;
        for (Caption caption : captions) {
            int row = caption.slot() / 9;
            int width = TextWidth.legacy(caption.text());
            if (width == TextWidth.UNKNOWN || width <= 0 || row % 2 != 0 || row / 2 >= FONTS.length) {
                continue;
            }
            int centre = 16 + 18 * (caption.slot() % 9);
            int left = Math.max(MIN_X, Math.min(MAX_X - (width - 1), centre - (width - 1) / 2));
            out.append(Component.text(space(left - cursor)));
            out.append(Component.text(caption.text(), colour(caption.state())).font(FONTS[row / 2]));
            cursor = left + width;
        }
        return out.build();
    }

    private static TextColor colour(State state) {
        return switch (state) {
            case OPEN -> INK;
            case LOCKED -> FADED;
            case NEXT -> AMBER;
        };
    }

    /** Pack space glyphs (minecraft:default): {@code ..} = -1..-256, {@code ..} = +1..+256. */
    public static String space(int pixels) {
        StringBuilder out = new StringBuilder();
        char base = pixels < 0 ? '' : '';
        int left = Math.abs(pixels);
        for (int bit = 8; bit >= 0; bit--) {
            int step = 1 << bit;
            while (left >= step) {
                out.append((char) (base + bit));
                left -= step;
            }
        }
        return out.toString();
    }
}
