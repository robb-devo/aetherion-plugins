package de.aetherion.quests.talk;

import de.aetherion.quests.AetherionQuests;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Optional resource-pack chrome for the talk UX: font {@code aetherion:talk}
 * (bubble tail, click icons, emote bubbles). Additive pack assets only —
 * {@code assets/aetherion/font/talk.json} + {@code textures/font/talk/*}.
 * <p>
 * Off by default ({@code talk-ux.pack-glyphs: false}): without the pack a client would
 * show empty boxes, so every glyph has a plain-unicode fallback.
 */
public final class TalkGlyphs {

    private static final Key FONT = Key.key("aetherion", "talk");

    private TalkGlyphs() {
    }

    public static boolean enabled() {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("talk-ux.pack-glyphs", false);
    }

    private static Component glyph(char c) {
        return Component.text(String.valueOf(c), NamedTextColor.WHITE).font(FONT);
    }

    /** Speech tail under the bubble. */
    public static Component tail() {
        if (enabled()) {
            return glyph('');
        }
        return Component.text("▼", TextColor.color(22, 22, 30));
    }

    /** Pointer in front of the hovered reply. */
    public static Component chevron() {
        if (enabled()) {
            return glyph('').append(Component.text(" "));
        }
        return Component.text("▶ ", NamedTextColor.YELLOW);
    }

    /** "Left-click" hint icon (empty without the pack — the text says it anyway). */
    public static Component leftClick() {
        return enabled() ? glyph('').append(Component.text(" ")) : Component.empty();
    }

    public static Component scroll() {
        return enabled() ? glyph('').append(Component.text(" ")) : Component.empty();
    }

    /** Floating emote above an NPC's head. */
    public static Component emote(String symbol) {
        if (enabled()) {
            char c = switch (symbol) {
                case "!" -> '';
                case "?" -> '';
                case "…" -> '';
                case "♥" -> '';
                case "♪" -> '';
                default -> 0;
            };
            if (c != 0) {
                return glyph(c);
            }
        }
        NamedTextColor color = switch (symbol) {
            case "!" -> NamedTextColor.YELLOW;
            case "?" -> NamedTextColor.AQUA;
            case "♥" -> NamedTextColor.RED;
            case "♪" -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.WHITE;
        };
        return Component.text(symbol, color, TextDecoration.BOLD);
    }
}
