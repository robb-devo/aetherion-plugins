package de.aetherion.quests.talk;

import de.aetherion.quests.npc.LivingNpcProfile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * One visual language for everything that floats over a living NPC — nameplate, quest mark,
 * speech bubble, reply chips, quest card, ambient barks.
 * <p>
 * Ink panels (one dark blue-black at different strengths), the NPC's own name colour as the
 * only colour thread (refined from the 16 legacy colours), small-caps for secondary labels,
 * and one vertical rhythm so name, mark and voice always occupy the same slots.
 */
public final class TalkChrome {

    private TalkChrome() {
    }

    /* =====================================================================
     * Palette
     * ===================================================================== */

    public static final int INK_R = 16;
    public static final int INK_G = 16;
    public static final int INK_B = 24;
    public static final TextColor INK = TextColor.color(INK_R, INK_G, INK_B);

    /** Body copy. */
    public static final TextColor TEXT = TextColor.color(0xF2F2F7);
    /** Secondary labels (subtitles, descriptions). */
    public static final TextColor MUTED = TextColor.color(0xA4A7B7);
    /** Tertiary (previous line, keys, bullets). */
    public static final TextColor FAINT = TextColor.color(0x6B6E80);
    /** Quests: offers, titles, the "!" mark. */
    public static final TextColor QUEST = TextColor.color(0xF5C451);
    /** Ready to hand in. */
    public static final TextColor READY = TextColor.color(0x7CE8A0);
    /** In progress — quiet. */
    public static final TextColor PENDING = TextColor.color(0x8E92A6);
    public static final TextColor WARN = TextColor.color(0xF08C78);
    /** "Sure? Click again." */
    public static final TextColor AMBER = TextColor.color(0xF5B44A);
    /** Action-bar hints. */
    public static final TextColor HINT = TextColor.color(0xEED7A0);

    public static final int BUBBLE_ALPHA = 218;
    public static final int BARK_ALPHA = 200;
    public static final int CARD_ALPHA = 220;
    public static final int CHIP_ALPHA = 196;
    public static final int CHIP_FOCUS_ALPHA = 232;
    public static final int PLATE_ALPHA = 58;
    public static final int RIM_ALPHA = 118;

    /* =====================================================================
     * Rhythm — blocks above the NPC's feet / beside the NPC
     * ===================================================================== */

    /** TextDisplay pixel at scale 1. */
    public static final float PX = 0.025f;
    /** One text line incl. spacing, in pixels. */
    public static final int LINE_PX = 10;

    public static final double PLATE_Y = 2.35;
    public static final float PLATE_SCALE = 0.7f;
    /** Top edge of the two-line nameplate (~2.70). */
    public static final double PLATE_TOP = PLATE_Y + 2 * LINE_PX * PX * PLATE_SCALE;
    /** Quest mark sits one breath above the plate. */
    public static final double MARK_Y = PLATE_TOP + 0.10;
    /** Speech (talk bubble + ambient barks) owns the slot above the plate; the mark yields to it. */
    public static final double VOICE_Y = PLATE_TOP + 0.14;
    /** Guests carry their own nametag we don't know — keep the old, higher slot. */
    public static final double VOICE_Y_FREE = 2.92;

    /** Top edge shared by the reply column (right) and the quest card (left). */
    public static final double SIDE_TOP = 1.86;
    /** NPC centre → inner edge of the reply column / quest card. */
    public static final double SIDE_GAP = 0.36;
    /** How far the side panels stand out toward the listener. */
    public static final double SIDE_FORWARD = 0.92;
    /** Reply-to-reply pitch. */
    public static final double CHIP_STEP = 0.19;
    /** Side panels deal in from (and tuck back toward) the NPC by this much. */
    public static final double DEAL = 0.22;

    public static final float CHIP_SCALE = 0.5f;
    public static final float CHIP_FOCUS_SCALE = 0.54f;
    public static final float CARD_SCALE = 0.42f;
    public static final float BARK_SCALE = 0.5f;
    /** Accent rim thickness in text pixels. */
    public static final float RIM_PX = 1.25f;

    /* =====================================================================
     * Accent
     * ===================================================================== */

    /** The NPC's name colour, refined: same hue family, softer and readable on ink. */
    public static TextColor accent(NamedTextColor c) {
        if (c == null) {
            return QUEST;
        }
        if (c == NamedTextColor.GREEN) {
            return TextColor.color(0x6FE38A);
        }
        if (c == NamedTextColor.DARK_GREEN) {
            return TextColor.color(0x4FC46C);
        }
        if (c == NamedTextColor.AQUA) {
            return TextColor.color(0x64D8EA);
        }
        if (c == NamedTextColor.DARK_AQUA) {
            return TextColor.color(0x3BBAC3);
        }
        if (c == NamedTextColor.BLUE) {
            return TextColor.color(0x7E95FF);
        }
        if (c == NamedTextColor.DARK_BLUE) {
            return TextColor.color(0x6170E2);
        }
        if (c == NamedTextColor.LIGHT_PURPLE) {
            return TextColor.color(0xE08CF2);
        }
        if (c == NamedTextColor.DARK_PURPLE) {
            return TextColor.color(0xB46FE8);
        }
        if (c == NamedTextColor.YELLOW) {
            return TextColor.color(0xF3DD70);
        }
        if (c == NamedTextColor.GOLD) {
            return TextColor.color(0xF2B44A);
        }
        if (c == NamedTextColor.RED) {
            return TextColor.color(0xF07070);
        }
        if (c == NamedTextColor.DARK_RED) {
            return TextColor.color(0xD45452);
        }
        if (c == NamedTextColor.GRAY) {
            return TextColor.color(0xC0C4D0);
        }
        if (c == NamedTextColor.DARK_GRAY) {
            return TextColor.color(0x9297A8);
        }
        if (c == NamedTextColor.WHITE) {
            return TextColor.color(0xEDEDF4);
        }
        return TextColor.color(0x9297A8);
    }

    /** Accent for a living NPC (profile colour) or a guest speaker (its legacy name code). */
    public static TextColor accentOf(String npcId) {
        LivingNpcProfile profile = LivingNpcProfile.of(npcId);
        if (profile != null) {
            return accent(profile.nameColor());
        }
        return accent(legacyColor(GuestSpeakers.nameCode(npcId, "§6")));
    }

    /** First colour of a legacy code string ("§b", "§b§l" …); gold when none. */
    public static NamedTextColor legacyColor(String code) {
        if (code != null) {
            for (int i = 0; i + 1 < code.length(); i++) {
                if (code.charAt(i) != '§') {
                    continue;
                }
                NamedTextColor c = switch (Character.toLowerCase(code.charAt(i + 1))) {
                    case '0' -> NamedTextColor.BLACK;
                    case '1' -> NamedTextColor.DARK_BLUE;
                    case '2' -> NamedTextColor.DARK_GREEN;
                    case '3' -> NamedTextColor.DARK_AQUA;
                    case '4' -> NamedTextColor.DARK_RED;
                    case '5' -> NamedTextColor.DARK_PURPLE;
                    case '6' -> NamedTextColor.GOLD;
                    case '7' -> NamedTextColor.GRAY;
                    case '8' -> NamedTextColor.DARK_GRAY;
                    case '9' -> NamedTextColor.BLUE;
                    case 'a' -> NamedTextColor.GREEN;
                    case 'b' -> NamedTextColor.AQUA;
                    case 'c' -> NamedTextColor.RED;
                    case 'd' -> NamedTextColor.LIGHT_PURPLE;
                    case 'e' -> NamedTextColor.YELLOW;
                    case 'f' -> NamedTextColor.WHITE;
                    default -> null;
                };
                if (c != null) {
                    return c;
                }
            }
        }
        return NamedTextColor.GOLD;
    }

    /** Reply label colour at rest: the choice's meaning, softened to sit on ink. */
    public static TextColor soft(NamedTextColor c) {
        if (c == null || c == NamedTextColor.WHITE) {
            return TEXT;
        }
        if (c == NamedTextColor.GREEN) {
            return TextColor.color(0x8FE6A1);
        }
        if (c == NamedTextColor.RED) {
            return TextColor.color(0xF29595);
        }
        if (c == NamedTextColor.GRAY) {
            return TextColor.color(0xC4C7D3);
        }
        if (c == NamedTextColor.DARK_GRAY) {
            return TextColor.color(0x9093A4);
        }
        if (c == NamedTextColor.GOLD) {
            return TextColor.color(0xF4C96C);
        }
        if (c == NamedTextColor.YELLOW) {
            return TextColor.color(0xF5E38C);
        }
        if (c == NamedTextColor.AQUA) {
            return TextColor.color(0x8CE2EE);
        }
        return mix(accent(c), TEXT, 0.3);
    }

    /** Reply label colour under focus: same meaning, lifted. */
    public static TextColor bright(NamedTextColor c) {
        if (c == NamedTextColor.DARK_GRAY) {
            return TextColor.color(0xC4C7D3);
        }
        return mix(soft(c), TextColor.color(0xFFFFFF), 0.38);
    }

    public static TextColor mix(TextColor a, TextColor b, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        return TextColor.color(
                (int) Math.round(a.red() + (b.red() - a.red()) * k),
                (int) Math.round(a.green() + (b.green() - a.green()) * k),
                (int) Math.round(a.blue() + (b.blue() - a.blue()) * k));
    }

    /* =====================================================================
     * Panel colours
     * ===================================================================== */

    public static Color ink(int alpha) {
        return Color.fromARGB(clamp(alpha), INK_R, INK_G, INK_B);
    }

    /** Ink with a breath of {@code tint} mixed in (focus states, mark badges). */
    public static Color inkTint(TextColor tint, double amount, int alpha) {
        TextColor c = mix(INK, tint, amount);
        return Color.fromARGB(clamp(alpha), c.red(), c.green(), c.blue());
    }

    /** Opaque text colour that reads as the panel itself (bubble tail, untyped remainder). */
    public static TextColor inkText(TextColor accent) {
        return mix(TextColor.color(INK_R + 5, INK_G + 5, INK_B + 6), accent == null ? INK : accent, 0.06);
    }

    /** Thin name-colour edge around a framed panel. */
    public static Color rim(TextColor accent) {
        TextColor c = accent == null ? QUEST : accent;
        return Color.fromARGB(RIM_ALPHA, c.red(), c.green(), c.blue());
    }

    /** No rim (also no background quad at all). */
    public static Color none() {
        return Color.fromARGB(0, 0, 0, 0);
    }

    private static int clamp(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    /* =====================================================================
     * Type
     * ===================================================================== */

    private static final String SMALL_CAPS =
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍ"
                    + "ɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    /** "Supply Desk" → "ꜱᴜᴘᴘʟʏ ᴅᴇꜱᴋ". Umlauts stay lower-case (they sit at x-height anyway). */
    public static String smallCaps(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c >= 'a' && c <= 'z') {
                out.append(SMALL_CAPS.charAt(c - 'a'));
            } else if (c == 'ß') {
                out.append(SMALL_CAPS.charAt('s' - 'a')).append(SMALL_CAPS.charAt('s' - 'a'));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static boolean isSmallCap(char c) {
        return c != 'x' && SMALL_CAPS.indexOf(c) >= 0;
    }

    /** Pixel width of plain text in the default font (bold adds 1px per glyph). */
    public static int width(String plain, boolean bold) {
        if (plain == null) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < plain.length(); i++) {
            w += TalkText.advance(plain.charAt(i)) + (bold ? 1 : 0);
        }
        return w;
    }

    /** Pixel width of one legacy line, honouring §l / colour resets. */
    public static int legacyWidth(String legacyLine) {
        if (legacyLine == null) {
            return 0;
        }
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < legacyLine.length(); i++) {
            char c = legacyLine.charAt(i);
            if (c == '§' && i + 1 < legacyLine.length()) {
                char code = Character.toLowerCase(legacyLine.charAt(i + 1));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    bold = false;
                }
                i++;
                continue;
            }
            if (c == '\n') {
                continue;
            }
            w += TalkText.advance(c) + (bold ? 1 : 0);
        }
        return w;
    }

    /** Widest line of a legacy block. */
    public static int legacyBlockWidth(String legacy) {
        int max = 0;
        if (legacy == null) {
            return 0;
        }
        for (String line : legacy.split("\n", -1)) {
            max = Math.max(max, legacyWidth(line));
        }
        return max;
    }

    /* =====================================================================
     * Nameplate
     * ===================================================================== */

    /**
     * Two quiet lines: name in the accent, role in small caps. The talk bubble later floats
     * right above it, so the plate doubles as the speaker label — no second name in the bubble.
     */
    public static Component nameplate(String name, TextColor accent, String subtitle) {
        Component plate = Component.empty()
                .append(Component.text(" " + name + " ", accent, TextDecoration.BOLD));
        if (subtitle != null && !subtitle.isBlank()) {
            plate = plate.append(Component.newline())
                    .append(Component.text(" " + smallCaps(subtitle) + " ", MUTED));
        }
        return plate;
    }

    /** Shared base for every chrome display. */
    public static void base(TextDisplay d) {
        d.setBillboard(Display.Billboard.CENTER);
        d.setShadowed(true);
        d.setSeeThrough(false);
        d.setDefaultBackground(false);
        d.setBrightness(new Display.Brightness(15, 15));
    }

    /** Nameplate look (scale / view range stay with the marker manager). */
    public static void stylePlate(TextDisplay d) {
        base(d);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setLineWidth(220);
        d.setBackgroundColor(ink(PLATE_ALPHA));
        d.setTeleportDuration(2);
    }

    /* =====================================================================
     * Quest mark — a small badge, calm instead of blinking
     * ===================================================================== */

    public enum Mark {
        /** Has a job for you. */
        AVAILABLE,
        /** Hand it in. */
        READY,
        /** You're on it — come back later. */
        PENDING
    }

    public static Component markText(Mark mark) {
        return switch (mark) {
            case AVAILABLE -> Component.text(" ")
                    .append(Component.text("!", QUEST, TextDecoration.BOLD))
                    .append(Component.text(" "));
            case READY -> Component.text(" ")
                    .append(Component.text("?", READY, TextDecoration.BOLD))
                    .append(Component.text(" "));
            case PENDING -> Component.text(" ")
                    .append(Component.text("?", PENDING))
                    .append(Component.text(" "));
        };
    }

    public static Color markBackground(Mark mark) {
        return switch (mark) {
            case AVAILABLE -> inkTint(QUEST, 0.17, 196);
            case READY -> inkTint(READY, 0.17, 196);
            case PENDING -> ink(116);
        };
    }

    public static float markScale(Mark mark) {
        return mark == Mark.PENDING ? 0.78f : 1.0f;
    }

    /** Offers and hand-ins breathe; "in progress" stays still. */
    public static boolean markBobs(Mark mark) {
        return mark != Mark.PENDING;
    }

    public static final float MARK_BOB = 0.055f;

    public static void styleMark(TextDisplay d, Mark mark) {
        base(d);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setLineWidth(120);
        d.setBackgroundColor(markBackground(mark));
        d.setTeleportDuration(2);
    }

    /* =====================================================================
     * Motion
     * ===================================================================== */

    public static Transformation scaled(float s) {
        return scaled(s, 0f);
    }

    public static Transformation scaled(float s, float lift) {
        return new Transformation(new Vector3f(0f, lift, 0f), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    /** Interpolate to {@code to} over {@code ticks} (0 = snap). */
    public static void animate(Display d, Transformation to, int ticks) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, ticks));
        d.setTransformation(to);
    }
}
