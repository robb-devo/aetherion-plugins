package net.aetherion.samuraiboss.render.model;

import org.joml.Vector3f;

/**
 * Ein Knochen der Modell-Hierarchie. Knochen selbst sind unsichtbar – sie sind nur
 * Drehpunkte, an denen Teile ({@link CubeDef}) hängen. Animationen drehen/verschieben Knochen,
 * alle Kinder bewegen sich automatisch mit (Arm -> Unterarm -> Hand -> Katana).
 */
public final class BoneDef {

    public final String name;
    public final int index;
    /** -1 = Wurzel. */
    public final int parent;
    /** Drehpunkt im Modellraum (Ruhepose), in Blöcken. */
    public final Vector3f pivot;
    /** Verschiebung relativ zum Eltern-Drehpunkt in Ruhe. */
    public final Vector3f restOffset;

    BoneDef(String name, int index, int parent, Vector3f pivot, Vector3f restOffset) {
        this.name = name;
        this.index = index;
        this.parent = parent;
        this.pivot = pivot;
        this.restOffset = restOffset;
    }
}
