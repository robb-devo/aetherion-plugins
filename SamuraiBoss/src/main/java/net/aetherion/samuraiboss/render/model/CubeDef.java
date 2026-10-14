package net.aetherion.samuraiboss.render.model;

import net.kyori.adventure.text.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Ein sichtbares Teil eines Modells (wird später zu genau EINEM Display-Entity).
 *
 * <p>Die Geometrie ist bereits in den lokalen Raum des Knochens umgerechnet:
 * <code>localT</code> ist der Punkt, an dem der Ursprung des Displays liegt (bei Blöcken die
 * Ecke (0,0,0) des Einheitswürfels, bei Items/Text die Mitte), <code>localQ</code> die Drehung,
 * <code>localS</code> die Größe in Blöcken.</p>
 */
public final class CubeDef {

    public enum Kind {BLOCK, ITEM, TEXT}

    /** Welche Variante: immer, nur ohne Resource Pack, nur mit Resource Pack. */
    public enum Variant {ANY, VANILLA, PACK}

    public final String id;
    public final int bone;
    public final Kind kind;
    public final Vector3f localT;
    public final Quaternionf localQ;
    public final Vector3f localS;
    /** Material-Schlüssel – wird über den aktiven {@link Skin} zu BlockData/ItemStack aufgelöst. */
    public final String material;
    /** 0 = Grundform, 1 = Details, 2 = feine Details (Klone/Nachbilder lassen höhere Stufen weg). */
    public final int lod;
    public final Set<String> tags;
    public final Variant variant;
    /** Leuchtet immer (volle Helligkeit), z. B. Augen oder Klingen-Glanz. */
    public final boolean emissive;
    /** Nur für Text-Teile. */
    public final Component text;
    public final boolean textShadow;

    CubeDef(String id, int bone, Kind kind, Vector3f localT, Quaternionf localQ, Vector3f localS,
            String material, int lod, Set<String> tags, Variant variant, boolean emissive,
            Component text, boolean textShadow) {
        this.id = id;
        this.bone = bone;
        this.kind = kind;
        this.localT = localT;
        this.localQ = localQ;
        this.localS = localS;
        this.material = material;
        this.lod = lod;
        this.tags = tags;
        this.variant = variant;
        this.emissive = emissive;
        this.text = text;
        this.textShadow = textShadow;
    }

    public boolean hasTag(String t) {
        return tags.contains(t);
    }
}
