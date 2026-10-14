package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.Rand;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Alles, was Effekte brauchen: Manager, Einstellungen, Sounds – und die Unterscheidung
 * "Vanilla-Blöcke" vs. "Resource-Pack-Modelle" für wiederkehrende Elemente wie Blütenblätter.
 */
public final class FxContext {

    public final FxManager fx;
    public final Settings settings;
    public final Sfx sfx;
    public final World world;
    public final boolean pack;
    private final ItemStack petalItem;
    private final ItemStack petalWhiteItem;

    public FxContext(FxManager fx, Settings settings, Sfx sfx, World world) {
        this.fx = fx;
        this.settings = settings;
        this.sfx = sfx;
        this.world = world;
        this.pack = settings.packEnabled;
        this.petalItem = pack ? PackItems.item(settings, "petal") : null;
        this.petalWhiteItem = pack ? PackItems.item(settings, "petal-white") : null;
    }

    public int count(int wanted) {
        return fx.count(wanted);
    }

    // ------------------------------------------------------------------ Blütenblätter

    /** Erzeugt ein Blütenblatt-Display (zunächst unsichtbar) – Block-Flocke oder Pack-Modell. */
    public Display petal(BaseFx owner, Location at, int variant) {
        Display d;
        if (pack) {
            d = owner.item(at, (variant % 3 == 2 ? petalWhiteItem : petalItem).clone());
        } else {
            d = owner.block(at, switch (variant % 3) {
                case 0 -> Palette.PINK_SOFT;
                case 1 -> Palette.PINK;
                default -> Palette.PETAL_WHITE;
            });
        }
        if (d != null) d.setBrightness(new Display.Brightness(14, 15));
        return d;
    }

    /** Transformation eines Blütenblatts mit Mittelpunkt c, Drehung r und Größe s (Blöcke). */
    public Transformation petalTf(Vector3f c, Quaternionf r, float s) {
        if (pack) return Tf.centered(c, r, s * 1.6f);
        return Tf.box(c, r, new Vector3f(s, s * 0.12f, s * 0.75f));
    }

    public static Quaternionf randomRot() {
        return new Quaternionf().rotateXYZ(Rand.f() * 6.28f, Rand.f() * 6.28f, Rand.f() * 6.28f);
    }

    // ------------------------------------------------------------------ Kanji

    /** Kanji-Text: mit Pack in der Pinsel-Schrift, sonst in der Standardschrift. */
    public Component kanji(String text, int rgb) {
        Component c = Component.text(text).color(TextColor.color(rgb));
        if (pack) c = c.font(Key.key(settings.packFont));
        return c;
    }

    /** Höhe einer Textzeile bei Skalierung 1 (Blöcke): Standardschrift ≈ 0,25, Pinsel-Schrift des Packs 0,4. */
    public float glyphHeight() {
        return pack ? 0.4f : 0.25f;
    }

    /** TextDisplay-Skalierung, damit ein Kanji etwa 'blocks' Blöcke hoch erscheint. */
    public float kanjiScale(float blocks) {
        return blocks / glyphHeight();
    }

    /** Vertikaler Versatz, damit ein Kanji-Display (mit Skalierung 'scale') um seinen Mittelpunkt zentriert ist. */
    public float kanjiCenterOffset(float scale) {
        return -0.5f * glyphHeight() * scale;
    }

    // ------------------------------------------------------------------ Partikel (nur als Würze)

    public void particles(Particle p, Location l, int count, double spread, double speed) {
        if (!settings.particles) return;
        world.spawnParticle(p, l, count, spread, spread, spread, speed);
    }

    public void cherry(Location l, int count, double spread) {
        if (!settings.particles) return;
        world.spawnParticle(Particle.CHERRY_LEAVES, l, count, spread, spread * 0.6, spread, 0.0);
    }
}
