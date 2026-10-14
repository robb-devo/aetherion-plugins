package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.Tf;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Sich ausdehnender Ring am Boden (Druckwelle). Der Radius wächst linear von r0 auf r1 in
 * 'ticks' Ticks – die Fähigkeit fragt {@link #radius()} für Treffer ab.
 */
public final class Shockwave extends BaseFx {

    private final float r0, r1;
    private final int ticks;
    private final int end;

    public Shockwave(FxContext c, Location center, float r0, float r1, int ticks, float height, BlockData core, BlockData glow) {
        super(c.fx);
        this.r0 = r0;
        this.r1 = r1;
        this.ticks = ticks;
        this.end = ticks + 6;
        int segs = Math.max(14, Math.min(32, Math.round(r1 * 2.2f)));
        Location o = center.clone().add(0, 0.05, 0);
        for (int layer = 0; layer < 2; layer++) {
            for (int i = 0; i < segs; i++) {
                Display d = block(o, layer == 0 ? core : glow);
                if (d == null) break;
                d.setBrightness(new Display.Brightness(15, 15));
                float a = (float) (i * Math.PI * 2 / segs);
                Quaternionf rot = new Quaternionf().rotationY(-a + (float) (Math.PI / 2));
                float h = layer == 0 ? height * 0.35f : height;
                float w = layer == 0 ? 0.1f : 0.3f;
                snapAt(0, d, Tf.box(pos(a, r0, h), rot, new Vector3f(seg(r0, segs), h, w)));
                lerpAt(1, d, Tf.box(pos(a, r1, h), rot, new Vector3f(seg(r1, segs), h * 0.3f, w * 0.4f)), ticks);
                lerpAt(1 + ticks, d, Tf.box(pos(a, r1 * 1.05f, 0), rot, new Vector3f(seg(r1, segs), 0f, 0f)), 4);
                freeAt(end, d);
            }
        }
    }

    private static Vector3f pos(float a, float r, float h) {
        return new Vector3f((float) Math.cos(a) * r, h / 2f, (float) Math.sin(a) * r);
    }

    private static float seg(float r, int segs) {
        return (float) (2 * Math.PI * r / segs) * 1.1f;
    }

    /** Aktueller Radius (für Treffer). */
    public float radius() {
        float u = Math.min(1f, Math.max(0f, (age - 1) / (float) ticks));
        return r0 + (r1 - r0) * u;
    }

    public boolean expanding() {
        return age <= ticks + 1;
    }

    @Override
    protected boolean update(int age) {
        return age <= end;
    }
}
