package net.aetherion.samuraiboss.render.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unveränderliche Definition eines Modells: Knochen (topologisch sortiert, Eltern immer vor
 * Kindern) und sichtbare Teile.
 */
public final class ModelDef {

    public final String name;
    public final List<BoneDef> bones;
    public final List<CubeDef> cubes;
    private final Map<String, Integer> boneIndex;

    ModelDef(String name, List<BoneDef> bones, List<CubeDef> cubes, Map<String, Integer> boneIndex) {
        this.name = name;
        this.bones = Collections.unmodifiableList(bones);
        this.cubes = Collections.unmodifiableList(cubes);
        this.boneIndex = boneIndex;
    }

    public int bone(String name) {
        Integer i = boneIndex.get(name);
        if (i == null) throw new IllegalArgumentException("Unbekannter Knochen: " + name);
        return i;
    }

    public boolean hasBone(String name) {
        return boneIndex.containsKey(name);
    }

    public int boneCount() {
        return bones.size();
    }
}
