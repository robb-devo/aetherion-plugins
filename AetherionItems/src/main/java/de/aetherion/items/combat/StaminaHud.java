package de.aetherion.items.combat;

import de.aetherion.core.api.AetherServices;
import de.aetherion.items.combat.system.CombatSystem;

import net.kyori.adventure.text.format.TextColor;

import org.bukkit.entity.Player;

/**
 * Stamina row for {@link PlayerHud}: left column, directly above the DEF row (or above HP when there is
 * no DEF). Same frame and well as a cooldown row, so it reads as part of the HUD rather than a new widget.
 * <ul>
 *   <li>amber fill, star socket: normal</li>
 *   <li>steel fill, shield socket: guard window open (gold while the perfect window lasts)</li>
 *   <li>red frame, cross socket: a move was denied, or winded at 0</li>
 * </ul>
 * Hidden when the kit is off or the bar has been full for a moment out of combat. The fill moves in eighths
 * and carries no number, so regen repaints the action bar a handful of times instead of every HUD paint
 * (PlayerHud only skips identical bars, and every repaint overwrites direct action-bar messages).
 */
public final class StaminaHud {

    private static final TextColor FILL = TextColor.color(0xF2C14E);
    private static final TextColor FILL_LOW = TextColor.color(0xE0703A);
    private static final TextColor FILL_GUARD = TextColor.color(0xBFD9EC);
    private static final TextColor FILL_PERFECT = TextColor.color(0xFFD86B);
    private static final TextColor FILL_DENY = TextColor.color(0xFF5A4A);
    private static final TextColor FILL_DENY_DIM = TextColor.color(0x8A1E16);
    private static final TextColor FILL_PULSE = TextColor.color(0xFFFFFF);
    private static final TextColor LABEL = TextColor.color(0xFFF3DA);
    private static final TextColor LABEL_DENY = TextColor.color(0xFFC0BA);
    private static final int STEPS = 8;

    private StaminaHud() {
    }

    /** What the HUD shows this paint, or null for no row. Label is already localized. */
    public record Bar(double fraction, State state, String label, boolean flash) {
        public Bar(double fraction, State state, String label) {
            this(fraction, state, label, false);
        }
    }

    public enum State { NORMAL, GUARD, PERFECT, DENIED, WINDED }

    public static Bar bar(Player player) {
        CombatSystem.Gauge gauge = CombatSystem.gauge(player);
        if (gauge == null) {
            return null;
        }
        boolean de = AetherServices.german(player);
        State state;
        String label;
        if (gauge.exhausted()) {
            state = State.WINDED;
            label = de ? "ERSCHOPFT" : "WINDED";
        } else if (gauge.denied()) {
            state = State.DENIED;
            label = de ? "AUSDAUER" : "STAMINA";
        } else if (gauge.perfect()) {
            state = State.PERFECT;
            label = de ? "PARADE" : "PARRY";
        } else if (gauge.guarding()) {
            state = State.GUARD;
            label = de ? "BLOCK" : "GUARD";
        } else {
            state = State.NORMAL;
            label = de ? "AUSDAUER" : "STAMINA";
        }
        // Spend = one white flash of the fill; winded = the red fill blinks dark/bright until it clears.
        boolean flash = state == State.WINDED ? gauge.blink() : gauge.pulse();
        return new Bar(quantize(gauge.fraction()), state, label, flash);
    }

    /** Text-HUD fallback: "⚡ ▮▮▮▮▯▯▯▯" in the same colours, for players on the plain action bar. */
    public static net.kyori.adventure.text.Component text(Bar bar) {
        if (bar == null) {
            return null;
        }
        TextColor tint = switch (bar.state()) {
            case GUARD -> FILL_GUARD;
            case PERFECT -> FILL_PERFECT;
            case DENIED -> FILL_DENY;
            case WINDED -> bar.flash() ? FILL_DENY : FILL_DENY_DIM;
            case NORMAL -> bar.flash() ? FILL_PULSE : bar.fraction() < 0.25 ? FILL_LOW : FILL;
        };
        int on = (int) Math.ceil(bar.fraction() * STEPS);
        net.kyori.adventure.text.TextComponent.Builder out = net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text("⚡ ", tint))
                .append(net.kyori.adventure.text.Component.text("▮".repeat(on), tint))
                .append(net.kyori.adventure.text.Component.text("▯".repeat(STEPS - on), TextColor.color(0x555555)));
        if (bar.state() == State.WINDED) {
            out.append(net.kyori.adventure.text.Component.text(" " + bar.label(), FILL_DENY));
        }
        return out.build();
    }

    /** Full stays full; anything else rounds down to the step, but never to empty while some is left. */
    static double quantize(double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        if (f >= 0.999) {
            return 1.0;
        }
        double q = Math.floor(f * STEPS) / STEPS;
        return q <= 0.0 && f > 0.0 ? 1.0 / (STEPS * 4.0) : q;
    }

    /** Draws the row with the same geometry as a cooldown row, mirrored into the left column. */
    static void draw(HudGlyphs.Pen pen, Bar bar, int row) {
        if (row < 0 || row >= HudGlyphs.ROWS) {
            return;
        }
        draw(pen, bar, row, HudGlyphs.LEFT_X);
    }

    /** Same row at any x (status panel). */
    public static void draw(HudGlyphs.Pen pen, Bar bar, int row, int x) {
        if (bar == null) {
            return;
        }
        boolean bad = bar.state() == State.DENIED || bar.state() == State.WINDED;
        HudGlyphs.Frame frame = bad ? HudGlyphs.Frame.COOLDOWN_DENY : HudGlyphs.Frame.COOLDOWN;
        TextColor tint = switch (bar.state()) {
            case GUARD -> FILL_GUARD;
            case PERFECT -> FILL_PERFECT;
            case DENIED -> FILL_DENY;
            case WINDED -> bar.flash() ? FILL_DENY : FILL_DENY_DIM;
            case NORMAL -> bar.flash() ? FILL_PULSE : bar.fraction() < 0.25 ? FILL_LOW : FILL;
        };
        int well = x + HudGlyphs.CD_WELL;
        int px = (int) Math.round(bar.fraction() * HudGlyphs.WELL);
        if (bar.fraction() > 0.0 && px == 0) {
            px = 1;
        }
        pen.at(x).frame(row, frame);
        if (bar.state() == State.WINDED && px == 0) {
            px = HudGlyphs.WELL;
        }
        if (px > 0) {
            pen.at(well).fill(row, px, tint);
        }
        String label = HudGlyphs.fit(HudGlyphs.label(bar.label()), HudGlyphs.WELL - 2);
        pen.at(well + 1).text(row, label, bad ? LABEL_DENY : LABEL);
        HudGlyphs.Icon icon = switch (bar.state()) {
            case GUARD, PERFECT -> HudGlyphs.Icon.SHIELD;
            case DENIED, WINDED -> HudGlyphs.Icon.CROSS;
            case NORMAL -> HudGlyphs.Icon.STAR;
        };
        pen.at(x + HudGlyphs.CD_SOCKET + HudGlyphs.ICON_INSET).icon(row, icon);
    }
}
