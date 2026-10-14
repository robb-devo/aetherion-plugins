package net.aetherion.samuraiboss.render.model;

import net.aetherion.samuraiboss.util.MathUtil;
import net.kyori.adventure.text.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Baukasten für Modelle – ähnlich wie Blockbench, nur im Code.
 *
 * <p>Alle Koordinaten werden in <b>Pixeln</b> (1/16 Block) angegeben und im Modellraum der
 * Ruhepose: Füße bei y=0, Blickrichtung +Z, linke Körperseite = +X. Teile werden einem Knochen
 * zugeordnet und bewegen sich mit ihm.</p>
 *
 * <pre>
 * b.bone("arm_r", "torso", -5, 24.5f, 0);
 * b.block("arm_r", "sleeve_r", "cloth_dark", -7.5f, 17.5f, -1.8f, -4f, 24.5f, 1.8f).lod(0);
 * </pre>
 */
public final class ModelBuilder {

    public static final float PX = 1f / 16f;

    private final String name;
    private final List<BoneDef> bones = new ArrayList<>();
    private final Map<String, Integer> boneIndex = new HashMap<>();
    private final List<CubeBuilder> cubes = new ArrayList<>();
    private final Set<String> cubeIds = new HashSet<>();
    /** Optionale Vor-Drehung aller Teile eines Knochens (um seinen Drehpunkt). */
    private final Map<Integer, Quaternionf> restRot = new HashMap<>();
    private int lastBone = -1;

    public ModelBuilder(String name) {
        this.name = name;
    }

    /** Neuer Knochen. Eltern müssen vorher angelegt sein. pivot in Pixeln. */
    public ModelBuilder bone(String bone, String parent, float px, float py, float pz) {
        if (boneIndex.containsKey(bone)) throw new IllegalArgumentException("Knochen doppelt: " + bone);
        int p = -1;
        Vector3f parentPivot = new Vector3f();
        if (parent != null) {
            Integer pi = boneIndex.get(parent);
            if (pi == null) throw new IllegalArgumentException("Eltern-Knochen fehlt: " + parent);
            p = pi;
            parentPivot.set(bones.get(p).pivot);
        }
        Vector3f pivot = new Vector3f(px, py, pz).mul(PX);
        Vector3f rest = new Vector3f(pivot).sub(parentPivot);
        BoneDef def = new BoneDef(bone, bones.size(), p, pivot, rest);
        boneIndex.put(bone, def.index);
        bones.add(def);
        lastBone = def.index;
        return this;
    }

    /**
     * Dreht alle Teile des zuletzt angelegten Knochens in Ruhe um seinen Drehpunkt
     * (z. B. abstehende Schutzplatten). Die Teile werden "ungedreht" definiert.
     * Kind-Knochen sind davon NICHT betroffen.
     */
    public ModelBuilder restRot(float rx, float ry, float rz) {
        if (lastBone < 0) throw new IllegalStateException("restRot() ohne Knochen");
        restRot.put(lastBone, MathUtil.euler(rx, ry, rz));
        return this;
    }

    /** Quader aus einem Block-Material, von (x1,y1,z1) bis (x2,y2,z2) in Pixeln. */
    public CubeBuilder block(String bone, String id, String material,
                             float x1, float y1, float z1, float x2, float y2, float z2) {
        CubeBuilder c = new CubeBuilder(bone, id, CubeDef.Kind.BLOCK, material);
        c.from.set(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)).mul(PX);
        c.size.set(Math.abs(x2 - x1), Math.abs(y2 - y1), Math.abs(z2 - z1)).mul(PX);
        add(c);
        return c;
    }

    /** Quader über Mittelpunkt + Größe (Pixel). */
    public CubeBuilder blockC(String bone, String id, String material,
                              float cx, float cy, float cz, float sx, float sy, float sz) {
        return block(bone, id, material, cx - sx / 2f, cy - sy / 2f, cz - sz / 2f, cx + sx / 2f, cy + sy / 2f, cz + sz / 2f);
    }

    /**
     * Item-Teil (Resource-Pack-Modell). Mittelpunkt in Pixeln, Größe = Skalierung des
     * Item-Modells in Blöcken (1 = normales Item-Modell).
     */
    public CubeBuilder item(String bone, String id, String material,
                            float cx, float cy, float cz, float scale) {
        CubeBuilder c = new CubeBuilder(bone, id, CubeDef.Kind.ITEM, material);
        c.center.set(cx, cy, cz).mul(PX);
        c.size.set(scale, scale, scale);
        add(c);
        return c;
    }

    /** Text-Teil (z. B. Kanji auf dem Rückenbanner). Mittelpunkt in Pixeln, Text-Skalierung. */
    public CubeBuilder text(String bone, String id, Component text, float cx, float cy, float cz, float scale) {
        CubeBuilder c = new CubeBuilder(bone, id, CubeDef.Kind.TEXT, "text");
        c.center.set(cx, cy, cz).mul(PX);
        c.size.set(scale, scale, scale);
        c.text = text;
        add(c);
        return c;
    }

    private void add(CubeBuilder c) {
        if (!boneIndex.containsKey(c.bone)) throw new IllegalArgumentException("Knochen fehlt: " + c.bone + " (Teil " + c.id + ")");
        if (!cubeIds.add(c.id)) throw new IllegalArgumentException("Teil-ID doppelt: " + c.id);
        cubes.add(c);
    }

    public ModelDef build() {
        List<CubeDef> out = new ArrayList<>();
        for (CubeBuilder c : cubes) out.add(c.toDef());
        return new ModelDef(name, bones, out, boneIndex);
    }

    // ------------------------------------------------------------------------------------

    public final class CubeBuilder {
        final String bone;
        final String id;
        final CubeDef.Kind kind;
        final String material;
        final Vector3f from = new Vector3f();
        final Vector3f center = new Vector3f();
        final Vector3f size = new Vector3f();
        /** Aufsummierte starre Transformation (Drehungen um beliebige Punkte) im Modellraum. */
        final Quaternionf rot = new Quaternionf();
        final Vector3f accT = new Vector3f();
        int lod = 0;
        final Set<String> tags = new HashSet<>();
        CubeDef.Variant variant = CubeDef.Variant.ANY;
        boolean emissive = false;
        Component text;
        boolean textShadow = false;

        CubeBuilder(String bone, String id, CubeDef.Kind kind, String material) {
            this.bone = bone;
            this.id = id;
            this.kind = kind;
            this.material = material;
        }

        /** Dreht das Teil um einen Punkt (Pixel) mit Euler-Winkeln (Grad, Rz*Ry*Rx). */
        public CubeBuilder rot(float px, float py, float pz, float rx, float ry, float rz) {
            // acc' = T(p) * R * T(-p) * acc   (mehrere Drehungen um verschiedene Punkte möglich)
            Vector3f p = new Vector3f(px, py, pz).mul(PX);
            Quaternionf q = MathUtil.euler(rx, ry, rz);
            Vector3f t = new Vector3f(accT).sub(p);
            q.transform(t);
            accT.set(t.add(p));
            q.mul(rot, rot);
            return this;
        }

        /** Dreht um den eigenen Mittelpunkt. */
        public CubeBuilder rotC(float rx, float ry, float rz) {
            // aktueller (bereits transformierter) Mittelpunkt
            Vector3f c = kind == CubeDef.Kind.BLOCK
                    ? new Vector3f(from).add(new Vector3f(size).mul(0.5f))
                    : new Vector3f(center);
            rot.transform(c).add(accT).div(PX);
            return rot(c.x, c.y, c.z, rx, ry, rz);
        }

        public CubeBuilder lod(int lod) {
            this.lod = lod;
            return this;
        }

        public CubeBuilder tag(String... t) {
            for (String s : t) tags.add(s);
            return this;
        }

        public CubeBuilder vanillaOnly() {
            this.variant = CubeDef.Variant.VANILLA;
            return this;
        }

        public CubeBuilder packOnly() {
            this.variant = CubeDef.Variant.PACK;
            return this;
        }

        public CubeBuilder emissive() {
            this.emissive = true;
            return this;
        }

        public CubeBuilder shadow() {
            this.textShadow = true;
            return this;
        }

        CubeDef toDef() {
            int bi = boneIndex.get(bone);
            Vector3f bonePivot = bones.get(bi).pivot;
            Vector3f localS = new Vector3f(size);
            Quaternionf localQ = new Quaternionf(rot);
            // Ursprung des Displays (Block: Ecke "from", Item/Text: Mittelpunkt) durch die
            // aufsummierte Transformation schicken und in den Knochenraum verschieben.
            Vector3f origin = new Vector3f(kind == CubeDef.Kind.BLOCK ? from : center);
            rot.transform(origin).add(accT);
            if (kind == CubeDef.Kind.TEXT) {
                // Text-Displays haben ihren Ursprung unten-mittig: ein halbe Zeile nach unten,
                // damit "center" wirklich die Mitte der Schriftzeichen ist.
                Vector3f off = new Vector3f(0f, -0.13f * size.y, 0f);
                rot.transform(off);
                origin.add(off);
            }
            Vector3f localT = origin.sub(bonePivot);
            Quaternionf rr = restRot.get(bi);
            if (rr != null) {
                rr.transform(localT);
                rr.mul(localQ, localQ);
            }
            return new CubeDef(id, bi, kind, localT, localQ, localS, material, lod, Set.copyOf(tags),
                    variant, emissive, text, textShadow);
        }
    }
}
