package net.aetherion.samuraiboss.render.anim;

import net.aetherion.samuraiboss.render.model.ModelDef;
import net.aetherion.samuraiboss.util.Ease;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baukasten für Animations-Clips.
 *
 * <pre>
 * Clip c = new ClipBuilder(model, "kesagiri", 18)
 *     .bone("arm_r")
 *         .rot(0,   0, 0, 0)
 *         .rot(6, -150, 20, -30, Ease.OUT_CUBIC)
 *         .rot(10,  40, -10, 20, Ease.IN_EXPO)
 *     .bone("torso").rot(...).pos(...)
 *     .event(10, "hit")
 *     .build();
 * </pre>
 *
 * Zeit in Ticks, Drehung in Grad (Rz*Ry*Rx), Position in Pixeln relativ zur Ruhelage.
 * Die Kurve (Ease) eines Keyframes gilt für den Weg VOM vorherigen Keyframe ZU diesem.
 */
public final class ClipBuilder {

    private record RKey(float t, float x, float y, float z, Ease e) {
    }

    private record SKey(float t, float v, Ease e) {
    }

    private record PKey(float t, PoseSpec spec, Ease e) {
    }

    private final List<PKey> poseKeys = new ArrayList<>();

    private static final float[] ZERO = {0f, 0f, 0f};

    private final ModelDef model;
    private final String name;
    private final float length;
    private boolean loop;
    private boolean hold;
    private int sampleInterval = 2;
    private final Map<Integer, List<RKey>> rot = new HashMap<>();
    private final Map<Integer, List<RKey>> pos = new HashMap<>();
    private final Map<Integer, List<SKey>> scl = new HashMap<>();
    private final List<Clip.Event> events = new ArrayList<>();
    private int current = -1;

    public ClipBuilder(ModelDef model, String name, float lengthTicks) {
        this.model = model;
        this.name = name;
        this.length = lengthTicks;
    }

    public ClipBuilder loop() {
        this.loop = true;
        return this;
    }

    public ClipBuilder hold() {
        this.hold = true;
        return this;
    }

    /** 1 = jeden Tick abtasten (für schnelle Schläge). */
    public ClipBuilder fast() {
        this.sampleInterval = 1;
        return this;
    }

    public ClipBuilder bone(String bone) {
        this.current = model.bone(bone);
        return this;
    }

    public ClipBuilder rot(float t, float x, float y, float z) {
        return rot(t, x, y, z, Ease.SMOOTH);
    }

    public ClipBuilder rot(float t, float x, float y, float z, Ease e) {
        check();
        rot.computeIfAbsent(current, k -> new ArrayList<>()).add(new RKey(t, x, y, z, e));
        return this;
    }

    public ClipBuilder pos(float t, float x, float y, float z) {
        return pos(t, x, y, z, Ease.SMOOTH);
    }

    public ClipBuilder pos(float t, float x, float y, float z, Ease e) {
        check();
        pos.computeIfAbsent(current, k -> new ArrayList<>()).add(new RKey(t, x, y, z, e));
        return this;
    }

    public ClipBuilder scale(float t, float s) {
        return scale(t, s, Ease.SMOOTH);
    }

    public ClipBuilder scale(float t, float s, Ease e) {
        check();
        scl.computeIfAbsent(current, k -> new ArrayList<>()).add(new SKey(t, s, e));
        return this;
    }

    /**
     * Ganze Schlüsselpose als Keyframe. Alle Knochen, die in IRGENDEINER Pose dieses Clips
     * vorkommen, bekommen hier einen Key (nicht genannte = Ruhe).
     */
    public ClipBuilder pose(float t, PoseSpec spec) {
        return pose(t, spec, Ease.SMOOTH);
    }

    public ClipBuilder pose(float t, PoseSpec spec, Ease e) {
        poseKeys.add(new PKey(t, spec, e));
        return this;
    }

    public ClipBuilder event(float t, String name) {
        events.add(new Clip.Event(t, name));
        return this;
    }

    private void check() {
        if (current < 0) throw new IllegalStateException("Zuerst bone(...) wählen (Clip " + name + ")");
    }

    public Clip build() {
        if (!poseKeys.isEmpty()) {
            java.util.Set<String> rBones = new java.util.LinkedHashSet<>();
            java.util.Set<String> pBones = new java.util.LinkedHashSet<>();
            java.util.Set<String> sBones = new java.util.LinkedHashSet<>();
            for (PKey k : poseKeys) {
                rBones.addAll(k.spec.rot.keySet());
                pBones.addAll(k.spec.pos.keySet());
                sBones.addAll(k.spec.scale.keySet());
            }
            for (PKey k : poseKeys) {
                for (String bn : rBones) {
                    if (!model.hasBone(bn)) throw new IllegalArgumentException("Unbekannter Knochen in Pose: " + bn + " (Clip " + name + ")");
                    float[] v = k.spec.rot.getOrDefault(bn, ZERO);
                    rot.computeIfAbsent(model.bone(bn), x -> new ArrayList<>()).add(new RKey(k.t, v[0], v[1], v[2], k.e));
                }
                for (String bn : pBones) {
                    float[] v = k.spec.pos.getOrDefault(bn, ZERO);
                    pos.computeIfAbsent(model.bone(bn), x -> new ArrayList<>()).add(new RKey(k.t, v[0], v[1], v[2], k.e));
                }
                for (String bn : sBones) {
                    float v = k.spec.scale.getOrDefault(bn, 1f);
                    scl.computeIfAbsent(model.bone(bn), x -> new ArrayList<>()).add(new SKey(k.t, v, k.e));
                }
            }
            poseKeys.clear();
        }
        Clip.BoneTrack[] tracks = new Clip.BoneTrack[model.boneCount()];
        for (int b = 0; b < tracks.length; b++) {
            List<RKey> r = rot.get(b);
            List<RKey> p = pos.get(b);
            List<SKey> s = scl.get(b);
            if (r == null && p == null && s == null) continue;
            Clip.BoneTrack tr = new Clip.BoneTrack();
            if (r != null) {
                r.sort(Comparator.comparingDouble(RKey::t));
                tr.rT = new float[r.size()];
                tr.rV = new float[r.size()][];
                tr.rE = new Ease[r.size()];
                for (int i = 0; i < r.size(); i++) {
                    RKey k = r.get(i);
                    tr.rT[i] = k.t;
                    tr.rV[i] = new float[]{k.x, k.y, k.z};
                    tr.rE[i] = k.e;
                }
            }
            if (p != null) {
                p.sort(Comparator.comparingDouble(RKey::t));
                tr.pT = new float[p.size()];
                tr.pV = new float[p.size()][];
                tr.pE = new Ease[p.size()];
                for (int i = 0; i < p.size(); i++) {
                    RKey k = p.get(i);
                    tr.pT[i] = k.t;
                    tr.pV[i] = new float[]{k.x, k.y, k.z};
                    tr.pE[i] = k.e;
                }
            }
            if (s != null) {
                s.sort(Comparator.comparingDouble(SKey::t));
                tr.sT = new float[s.size()];
                tr.sV = new float[s.size()];
                tr.sE = new Ease[s.size()];
                for (int i = 0; i < s.size(); i++) {
                    SKey k = s.get(i);
                    tr.sT[i] = k.t;
                    tr.sV[i] = k.v;
                    tr.sE[i] = k.e;
                }
            }
            tracks[b] = tr;
        }
        events.sort(Comparator.comparingDouble(Clip.Event::time));
        return new Clip(name, length, loop, hold, sampleInterval, tracks, List.copyOf(events));
    }
}
