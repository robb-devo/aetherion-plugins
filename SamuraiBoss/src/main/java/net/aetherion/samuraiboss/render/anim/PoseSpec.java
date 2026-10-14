package net.aetherion.samuraiboss.render.anim;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Eine benannte Schlüsselpose: Euler-Drehungen (Grad) und Verschiebungen (Pixel) für
 * einzelne Knochen. Nicht genannte Knochen stehen in Ruhe (0).
 *
 * <pre>
 * PoseSpec STANCE = PoseSpec.of()
 *     .r("thigh_r", -18, 0, -4)
 *     .p("hips", 0, -1.2f, 0);
 * PoseSpec WINDUP = STANCE.with().r("arm_r", -150, 20, -30);
 * </pre>
 */
public final class PoseSpec {

    final Map<String, float[]> rot = new LinkedHashMap<>();
    final Map<String, float[]> pos = new LinkedHashMap<>();
    final Map<String, Float> scale = new LinkedHashMap<>();

    private PoseSpec() {
    }

    public static PoseSpec of() {
        return new PoseSpec();
    }

    /** Kopie zum Abwandeln. */
    public PoseSpec with() {
        PoseSpec p = new PoseSpec();
        for (Map.Entry<String, float[]> e : rot.entrySet()) p.rot.put(e.getKey(), e.getValue().clone());
        for (Map.Entry<String, float[]> e : pos.entrySet()) p.pos.put(e.getKey(), e.getValue().clone());
        p.scale.putAll(scale);
        return p;
    }

    public PoseSpec r(String bone, float x, float y, float z) {
        rot.put(bone, new float[]{x, y, z});
        return this;
    }

    /** Addiert auf eine vorhandene Drehung. */
    public PoseSpec addR(String bone, float x, float y, float z) {
        float[] v = rot.getOrDefault(bone, new float[3]);
        rot.put(bone, new float[]{v[0] + x, v[1] + y, v[2] + z});
        return this;
    }

    public PoseSpec p(String bone, float x, float y, float z) {
        pos.put(bone, new float[]{x, y, z});
        return this;
    }

    public PoseSpec s(String bone, float s) {
        scale.put(bone, s);
        return this;
    }

    /** Spiegelt rechts <-> links (für symmetrische Bewegungen). */
    public PoseSpec mirrored() {
        PoseSpec p = new PoseSpec();
        for (Map.Entry<String, float[]> e : rot.entrySet()) {
            float[] v = e.getValue();
            p.rot.put(swap(e.getKey()), new float[]{v[0], -v[1], -v[2]});
        }
        for (Map.Entry<String, float[]> e : pos.entrySet()) {
            float[] v = e.getValue();
            p.pos.put(swap(e.getKey()), new float[]{-v[0], v[1], v[2]});
        }
        for (Map.Entry<String, Float> e : scale.entrySet()) p.scale.put(swap(e.getKey()), e.getValue());
        return p;
    }

    private static String swap(String bone) {
        if (bone.endsWith("_r")) return bone.substring(0, bone.length() - 2) + "_l";
        if (bone.endsWith("_l")) return bone.substring(0, bone.length() - 2) + "_r";
        return bone;
    }

    public Map<String, float[]> rotations() {
        return rot;
    }

    public Map<String, float[]> positions() {
        return pos;
    }
}
