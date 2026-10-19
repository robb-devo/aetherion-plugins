package de.aetherion.items.combat.system;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

import org.bukkit.entity.Player;

/**
 * Screen glyphs from the pack font {@code aetherion:combat} (resourcepack/tools/gen_combat_glyphs.py),
 * flashed through the title slot for a few ticks: the "mod" layer on top of particles and sounds.
 * The callout text stays on ActionBarLane; the title carries only the picture. Off with
 * {@code feedback.screen-glyphs} or intensity 0. Without the pack the client draws nothing (private-use chars).
 */
final class CombatGlyphs {

    static final Key FONT = Key.key("aetherion", "combat");

    enum Glyph {
        PARRY('', 1, 6, 8),
        DODGE('', 0, 4, 6),
        WINDED('', 2, 14, 10),
        PERIL('', 0, 10, 4),
        FINISHER('', 0, 4, 6),
        RIPOSTE('', 0, 4, 6),
        GUARD('', 0, 3, 5);

        final char ch;
        final int in;
        final int stay;
        final int out;

        Glyph(char ch, int in, int stay, int out) {
            this.ch = ch;
            this.in = in;
            this.stay = stay;
            this.out = out;
        }
    }

    private CombatGlyphs() {
    }

    static void flash(CombatSystem system, Player player, Glyph glyph) {
        CombatConfig cfg = system.config();
        if (!cfg.screenGlyphs || cfg.intensity <= 0.0 || player == null || !player.isOnline()) {
            return;
        }
        Component picture = Component.text(String.valueOf(glyph.ch)).font(FONT);
        player.showTitle(Title.title(picture, Component.empty(),
                Title.Times.times(Ticks.duration(glyph.in), Ticks.duration(glyph.stay), Ticks.duration(glyph.out))));
    }
}
