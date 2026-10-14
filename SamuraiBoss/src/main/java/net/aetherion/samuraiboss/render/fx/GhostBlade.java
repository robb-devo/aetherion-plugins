package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.render.Tf;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Geisterklinge: ein schwebendes, leuchtendes Katana (aus mehreren Displays oder einem
 * Pack-Modell), das frei positioniert und gedreht werden kann. Lokale Achse: +Z = Klinge.
 * Wird von "Tausend Klingen" und dem Klingenregen gesteuert.
 */
public final class GhostBlade extends BaseFx {

    private record Part(Display d, Vector3f center, Vector3f size, boolean item) {
    }

    private final List<Part> parts = new ArrayList<>();
    private final float scale;
    private final Vector3f pos = new Vector3f();
    private final Quaternionf rot = new Quaternionf();
    private boolean dead;
    private int killAt = Integer.MAX_VALUE;

    public GhostBlade(FxContext c, Location origin, float scale, boolean shadow) {
        super(c.fx);
        this.scale = scale;
        Color glow = shadow ? Color.fromRGB(140, 70, 210) : Color.fromRGB(255, 150, 185);
        if (c.pack && !shadow) {
            Display d = item(origin, PackItems.item(c.settings, "katana-ghost"));
            if (d != null) {
                d.setBrightness(new Display.Brightness(15, 15));
                parts.add(new Part(d, new Vector3f(0, 0, 0), new Vector3f(1f, 1f, 1f), true));
            }
        } else {
            BlockData core = shadow ? Palette.PURPLE : Palette.LIGHT;
            BlockData shell = shadow ? Palette.BLACK_GLASS : Palette.PINK_GLASS;
            BlockData guard = shadow ? Palette.PURPLE_GLASS : Palette.MAGENTA_GLASS;
            add(origin, shell, new Vector3f(0, 0, 0.62f), new Vector3f(0.05f, 0.13f, 1.12f), glow);
            add(origin, core, new Vector3f(0, -0.03f, 0.62f), new Vector3f(0.02f, 0.04f, 1.1f), null);
            add(origin, guard, new Vector3f(0, 0, 0.04f), new Vector3f(0.17f, 0.17f, 0.03f), null);
            add(origin, shell, new Vector3f(0, 0, -0.16f), new Vector3f(0.06f, 0.06f, 0.33f), null);
        }
    }

    private void add(Location o, BlockData data, Vector3f center, Vector3f size, Color glow) {
        Display d = block(o, data);
        if (d == null) return;
        d.setBrightness(new Display.Brightness(15, 15));
        if (glow != null) {
            d.setGlowColorOverride(glow);
            d.setGlowing(true);
        }
        parts.add(new Part(d, center, size, false));
    }

    /** Setzt Lage (relativ zum Ursprung) und Ausrichtung; interp = Interpolationsticks (0 = sofort). */
    public void pose(Vector3f p, Quaternionf r, float sizeMul, int interp) {
        pos.set(p);
        rot.set(r);
        for (Part part : parts) {
            Transformation t;
            float s = scale * sizeMul;
            if (part.item) {
                // Pack-Modell hat seinen Ursprung in der Griffmitte; das Stichblatt liegt 3 px (0,1875 Blöcke) davor.
                // Die Geisterklinge soll am Stichblatt "hängen" – also um diese Strecke zurückversetzen.
                Vector3f c = r.transform(new Vector3f(0, 0, -0.1875f * s)).add(p);
                t = Tf.centered(c, new Quaternionf(r), s);
            } else {
                Vector3f c = r.transform(new Vector3f(part.center).mul(s)).add(p);
                t = Tf.box(c, r, new Vector3f(part.size).mul(s));
            }
            if (interp <= 0) DisplayFactory.snap(part.d, t);
            else DisplayFactory.lerp(part.d, t, interp);
        }
    }

    public Vector3f position() {
        return new Vector3f(pos);
    }

    public Quaternionf rotation() {
        return new Quaternionf(rot);
    }

    /** Spitze der Klinge (relativ). */
    public Vector3f tip() {
        return rot.transform(new Vector3f(0, 0, 1.2f * scale)).add(pos);
    }

    /** Löst die Klinge in "dt" Ticks auf und beendet den Effekt. */
    public void dissolve(int dt) {
        if (dead) return;
        dead = true;
        for (Part part : parts) {
            Transformation t = part.item ? Tf.centered(rot.transform(new Vector3f(0, 0, -0.1875f * scale)).add(pos), rot, 0f)
                    : Tf.box(rot.transform(new Vector3f(part.center).mul(scale)).add(pos), rot, new Vector3f());
            DisplayFactory.lerp(part.d, t, dt);
        }
        killAt = age + dt + 1;
    }

    public boolean isDead() {
        return dead;
    }

    @Override
    protected boolean update(int age) {
        return age < killAt;
    }
}
