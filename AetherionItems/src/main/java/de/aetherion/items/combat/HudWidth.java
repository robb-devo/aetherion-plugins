package de.aetherion.items.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Pixel widths for the shared ActionBar (vanilla default font, GUI scale 1).
 *
 * <p>The ActionBar is centred, so anything appended on the right drags the whole line left by half
 * its width. {@link #balance(int)} returns blank padding of the same width for the left edge: the
 * centre stays put and HP / defense never move when a cooldown comes or goes.
 *
 * <p>Advances are measured from the 1.21.1 client: ascii / accented / nonlatin_european bitmap pages
 * (opaque width + 1) and unifont 15.1.05 (glyph width / 2 + 1) for symbols those pages lack. They
 * matter twice: the text HUD's cooldown balance, and centring an {@link de.aetherion.core.api.ActionBarLane}
 * line above the glyph HUD (an error of e px moves the HUD by e/2 while that line shows).
 */
public final class HudWidth {

    /** Plain space advance. */
    public static final int SPACE = 4;
    /** {@code ▮ / ▯} cooldown cells (unifont). */
    static final int BAR_CELL = 3;
    /** {@code ✔} ready mark (nonlatin page). */
    static final int CHECK = 7;
    /** {@code ❤}. */
    static final int HEART = 8;

    private HudWidth() {
    }

    /** Width of a component tree, honouring inherited bold. */
    public static int of(Component component) {
        return of(component, false);
    }

    private static int of(Component component, boolean inheritedBold) {
        if (component == null) {
            return 0;
        }
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? inheritedBold : state == TextDecoration.State.TRUE;
        int width = 0;
        if (component instanceof TextComponent text) {
            width += of(text.content(), bold);
        }
        for (Component child : component.children()) {
            width += of(child, bold);
        }
        return width;
    }

    public static int of(String text, boolean bold) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int advance = advance(cp);
            width += advance + (bold && cp != ' ' && advance > 0 ? 1 : 0);
        }
        return width;
    }

    /** Blank text {@code px} wide (nearest whole space; residual ≤ 2px → ≤ 1px drift on screen). */
    public static Component balance(int px) {
        if (px <= 0) {
            return Component.empty();
        }
        int spaces = Math.max(1, Math.round(px / (float) SPACE));
        return Component.text(" ".repeat(spaces));
    }

    /** Spaces that pad {@code actualPx} up to {@code targetPx} (empty when already wide enough). */
    public static String padTo(int actualPx, int targetPx) {
        int missing = targetPx - actualPx;
        if (missing < SPACE / 2) {
            return "";
        }
        return " ".repeat(Math.max(1, Math.round(missing / (float) SPACE)));
    }

    private static int advance(int cp) {
        switch (cp) {
            case ' ':
                return SPACE;
            case '!': case '\'': case ',': case '.': case ':': case ';': case '|': case 'i':
                return 2;
            case '`': case 'l':
                return 3;
            case '"': case '(': case ')': case '*': case 'I': case '[': case ']': case 't': case '{': case '}':
                return 4;
            case '<': case '>': case 'f': case 'k':
                return 5;
            case '@': case '~':
                return 7;
            case 0x00B7: // ·
                return 2;
            case 0x25AE: case 0x25AF: // ▮ ▯
                return BAR_CELL;
            case 0x2714: // ✔
                return CHECK;
            case 0x2764: // ❤
                return HEART;
            case 0x2022: // •
                return 3;
            case 0x2196: case 0x2197: case 0x2198: case 0x2199: // ↖ ↗ ↘ ↙
                return 4;
            case 0x2191: case 0x2193: case 0x2502: // ↑ ↓ │
            case 0x25B2: case 0x25BC: case 0x25C6: case 0x25C7: // ▲ ▼ ◆ ◇
                return 6;
            case 0x00AB: case 0x00BB: case 0x25B6: case 0x25C0: // « » ▶ ◀
            case 0x1F6E1: // 🛡
                return 7;
            case 0x2026: case 0x2190: case 0x2192: // … ← →
            case 0x2605: case 0x2606: case 0x2620: case 0x2694: // ★ ☆ ☠ ⚔
            case 0x2726: case 0x2727: case 0x2728: // ✦ ✧ ✨
                return 8;
            case 0x2014: case 0x2500: case 0x2588: case 0x2593: // — ─ █ ▓
                return 9;
            default:
                return cp < 0x20 ? 0 : 6;
        }
    }
}
