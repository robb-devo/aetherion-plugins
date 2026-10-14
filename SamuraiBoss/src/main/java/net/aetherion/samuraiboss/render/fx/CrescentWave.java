package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.render.Tf;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Fliegende Windklinge (Kaze-giri): eine Sichel aus Licht, die flach über den Boden rast.
 * Die konvexe Seite zeigt in Flugrichtung. Bewegung über die Transformation (weich).
 */
public final class CrescentWave extends BaseFx {

    private record Seg(Display d, Vector3f local, Quaternionf rot, Vector3f size) {
    }

    private final List<Seg> segs = new ArrayList<>();
    private final Vector3f dir;
    private final float speed;
    private final float maxDist;
    private float dist;
    private final float yaw;
    private boolean done;
    private int endAt = Integer.MAX_VALUE;
    private final float height;

    /**
     * @param origin Startpunkt (Bodenhöhe)
     * @param mcYaw  Flugrichtung
     * @param width  Breite der Sichel (Blöcke)
     * @param height Höhe über dem Boden (Kniehöhe ~0.5)
     * @param speed  Blöcke pro Tick
     */
    public CrescentWave(FxContext c, Location origin, float mcYaw, float width, float height, float speed, float maxDist,
                        BlockData core, BlockData glow) {
        super(c.fx);
        this.yaw = mcYaw;
        this.speed = speed;
        this.maxDist = maxDist;
        this.height = height;
        double r = Math.toRadians(mcYaw);
        this.dir = new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r));
        Quaternionf base = new Quaternionf().rotationY((float) Math.toRadians(-mcYaw));
        if (c.pack && core == Palette.LIGHT) {
            Display d = item(origin, PackItems.item(c.settings, "slash-pink"));
            if (d != null) {
                d.setBrightness(new Display.Brightness(15, 15));
                // Sichel-Modell: Bogen um +Z (konvex nach vorne)
                segs.add(new Seg(d, new Vector3f(0, height, 0), base, new Vector3f(width, width, width)));
            }
        } else {
            int n = 9;
            float rad = width * 0.6f;
            for (int i = 0; i < n; i++) {
                float a = (float) Math.toRadians(-70 + 140f * (i + 0.5f) / n);
                // Bogen in lokaler XZ-Ebene, Scheitel bei +Z
                Vector3f local = new Vector3f((float) Math.sin(a) * rad, height, (float) Math.cos(a) * rad - rad * 0.7f);
                float prof = (float) Math.cos(a * 1.1);
                Quaternionf rot = new Quaternionf(base).rotateY(a);
                for (int layer = 0; layer < 2; layer++) {
                    Display d = block(origin, layer == 0 ? core : glow);
                    if (d == null) continue;
                    d.setBrightness(new Display.Brightness(15, 15));
                    Vector3f size = layer == 0 ? new Vector3f(rad * 0.36f, 0.04f, 0.09f * prof + 0.02f)
                            : new Vector3f(rad * 0.38f, 0.12f, 0.24f * prof + 0.03f);
                    segs.add(new Seg(d, base.transform(new Vector3f(local.x, 0, local.z)).add(0, height, 0),
                            new Quaternionf(rot), size));
                }
            }
        }
        for (Seg s : segs) {
            DisplayFactory.snap(s.d, tf(s, new Vector3f(), 0.2f));
        }
    }

    private org.bukkit.util.Transformation tf(Seg s, Vector3f offset, float scale) {
        if (s.size.x == s.size.y && s.size.y == s.size.z) {
            return Tf.centered(new Vector3f(s.local).add(offset), s.rot, s.size.x * scale);
        }
        return Tf.box(new Vector3f(s.local).add(offset), s.rot, new Vector3f(s.size).mul(scale));
    }

    /** Aktuelle Front (relativ zum Ursprung). */
    public Vector3f front() {
        return new Vector3f(dir).mul(dist);
    }

    public Vector3f direction() {
        return new Vector3f(dir);
    }

    public float distance() {
        return dist;
    }

    /** Welle verpuffen lassen (eigener Name – stop() gehört dem Effekt-Manager!). */
    public void vanish() {
        if (done) return;
        done = true;
        Vector3f off = new Vector3f(dir).mul(dist + 1.5f);
        for (Seg s : segs) DisplayFactory.lerp(s.d, tf(s, off, 0f), 5);
        endAt = age + 7;
    }

    @Override
    protected boolean update(int age) {
        if (age >= endAt) return false;
        if (done) return true;
        if (age >= 1 && age % 2 == 1) {
            dist = Math.min(maxDist, dist + speed * 2);
            Vector3f off = new Vector3f(dir).mul(dist);
            float grow = Math.min(1f, 0.35f + age * 0.12f);
            for (Seg s : segs) DisplayFactory.lerp(s.d, tf(s, off, grow), 2);
            if (dist >= maxDist) vanish();
        }
        return true;
    }

    public boolean isVanishing() {
        return done;
    }

    public float height() {
        return height;
    }

    public float yaw() {
        return yaw;
    }
}
