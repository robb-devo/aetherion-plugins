package net.aetherion.samuraiboss.render.model;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Eine Pose = für jeden Knochen eine lokale Verschiebung (Blöcke, im Eltern-Raum),
 * eine lokale Drehung und eine gleichmäßige Skalierung – jeweils relativ zur Ruhepose.
 */
public final class Pose {

    public final Vector3f[] pos;
    public final Quaternionf[] rot;
    public final float[] scale;

    public Pose(int bones) {
        pos = new Vector3f[bones];
        rot = new Quaternionf[bones];
        scale = new float[bones];
        for (int i = 0; i < bones; i++) {
            pos[i] = new Vector3f();
            rot[i] = new Quaternionf();
            scale[i] = 1f;
        }
    }

    public int size() {
        return pos.length;
    }

    public Pose reset() {
        for (int i = 0; i < pos.length; i++) {
            pos[i].zero();
            rot[i].identity();
            scale[i] = 1f;
        }
        return this;
    }

    public Pose set(Pose o) {
        for (int i = 0; i < pos.length; i++) {
            pos[i].set(o.pos[i]);
            rot[i].set(o.rot[i]);
            scale[i] = o.scale[i];
        }
        return this;
    }

    public Pose copy() {
        return new Pose(pos.length).set(this);
    }

    /** this = mix(this, o, w) pro Knochen (w=1 -> o). */
    public void blend(Pose o, int bone, float w) {
        if (w <= 0f) return;
        if (w >= 1f) {
            pos[bone].set(o.pos[bone]);
            rot[bone].set(o.rot[bone]);
            scale[bone] = o.scale[bone];
            return;
        }
        pos[bone].lerp(o.pos[bone], w);
        rot[bone].slerp(o.rot[bone], w);
        scale[bone] += (o.scale[bone] - scale[bone]) * w;
    }
}
