package net.aetherion.samuraiboss.arena.dojo;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Beschreibung einer Deko-Display-Entity des Dojos (Blütenwolken, hängende Zweige, Banner-Stoff,
 * Kanji, Tempelglocke, Koi …). Bewusst ohne Bukkit-Typen, damit der Bauplan auch ohne Server
 * erzeugt (und z. B. als Vorschau gerendert) werden kann.
 *
 * <p>Position (x,y,z) ist relativ zum Arena-Ursprung; die Transformation relativ zur Position.</p>
 */
public final class DecoSpec {

    public enum Kind {BLOCK, ITEM, TEXT, INTERACTION}

    public final String id;
    public final Kind kind;
    public final double x, y, z;
    public final Vector3f t;
    public final Quaternionf q;
    public final Vector3f s;
    /** BLOCK: Blockdaten-String. ITEM: Vanilla-Material (z. B. "IRON_SWORD"). */
    public final String block;
    /** Optionaler Resource-Pack-Schlüssel (custom_model_data), ersetzt das Vanilla-Item. */
    public final String packKey;
    /** TEXT: MiniMessage-Text. */
    public final String text;
    public final int textBgArgb;
    public final boolean textShadow;
    public final boolean centerBillboard;
    public final boolean fullBright;
    public final El element;
    /** Animation: null, "sway", "strand", "koi", "flag", "bell". */
    public final String anim;
    public final float animPhase;
    public final float viewRange;
    /** Für Interaktion: Breite/Höhe. */
    public final float width, height;
    /** Optionale Transformation, wenn statt des Vanilla-Items das Pack-Modell gezeigt wird (sonst null). */
    public final Vector3f packT;
    public final Quaternionf packQ;
    public final Vector3f packS;

    private DecoSpec(Builder b) {
        id = b.id;
        kind = b.kind;
        x = b.x;
        y = b.y;
        z = b.z;
        t = b.t;
        q = b.q;
        s = b.s;
        block = b.block;
        packKey = b.packKey;
        text = b.text;
        textBgArgb = b.textBg;
        textShadow = b.textShadow;
        centerBillboard = b.centerBillboard;
        fullBright = b.fullBright;
        element = b.element;
        anim = b.anim;
        animPhase = b.animPhase;
        viewRange = b.viewRange;
        width = b.width;
        height = b.height;
        packT = b.packT;
        packQ = b.packQ;
        packS = b.packS;
    }

    public static Builder block(String id, El el, double x, double y, double z, String block) {
        Builder b = new Builder(id, Kind.BLOCK, el, x, y, z);
        b.block = block;
        return b;
    }

    public static Builder item(String id, El el, double x, double y, double z, String material) {
        Builder b = new Builder(id, Kind.ITEM, el, x, y, z);
        b.block = material;
        return b;
    }

    public static Builder text(String id, El el, double x, double y, double z, String miniMessage) {
        Builder b = new Builder(id, Kind.TEXT, el, x, y, z);
        b.text = miniMessage;
        return b;
    }

    public static Builder interaction(String id, El el, double x, double y, double z, float w, float h) {
        Builder b = new Builder(id, Kind.INTERACTION, el, x, y, z);
        b.width = w;
        b.height = h;
        return b;
    }

    public static final class Builder {
        final String id;
        final Kind kind;
        final El element;
        final double x, y, z;
        Vector3f t = new Vector3f();
        Quaternionf q = new Quaternionf();
        Vector3f s = new Vector3f(1, 1, 1);
        String block, packKey, text;
        int textBg = 0;
        boolean textShadow, centerBillboard, fullBright;
        String anim;
        float animPhase;
        float viewRange = 1.5f;
        float width, height;
        Vector3f packT, packS;
        Quaternionf packQ;

        Builder(String id, Kind kind, El element, double x, double y, double z) {
            this.id = id;
            this.kind = kind;
            this.element = element;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** Block-Quader mit Mittelpunkt (relativ zur Position), Drehung und Größe. */
        public Builder box(Vector3f center, Quaternionf rot, Vector3f size) {
            Vector3f half = rot.transform(new Vector3f(size).mul(0.5f));
            this.t = new Vector3f(center).sub(half);
            this.q = new Quaternionf(rot);
            this.s = new Vector3f(size);
            return this;
        }

        /** Item/Text: Mittelpunkt + Drehung + Skalierung. */
        public Builder at(Vector3f center, Quaternionf rot, Vector3f scale) {
            this.t = new Vector3f(center);
            this.q = new Quaternionf(rot);
            this.s = new Vector3f(scale);
            return this;
        }

        public Builder pack(String key) {
            this.packKey = key;
            return this;
        }

        /** Lage für das Pack-Modell (Item/Text: Mittelpunkt + Drehung + Skalierung). */
        public Builder packAt(Vector3f center, Quaternionf rot, Vector3f scale) {
            this.packT = new Vector3f(center);
            this.packQ = new Quaternionf(rot);
            this.packS = new Vector3f(scale);
            return this;
        }

        public Builder bg(int argb) {
            this.textBg = argb;
            return this;
        }

        public Builder shadow() {
            this.textShadow = true;
            return this;
        }

        public Builder billboard() {
            this.centerBillboard = true;
            return this;
        }

        public Builder bright() {
            this.fullBright = true;
            return this;
        }

        public Builder anim(String type, float phase) {
            this.anim = type;
            this.animPhase = phase;
            return this;
        }

        public Builder range(float r) {
            this.viewRange = r;
            return this;
        }

        public DecoSpec build() {
            return new DecoSpec(this);
        }
    }
}
