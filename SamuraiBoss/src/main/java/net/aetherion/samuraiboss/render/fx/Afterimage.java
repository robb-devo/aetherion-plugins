package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.render.model.CubeDef;
import net.aetherion.samuraiboss.render.model.ModelInstance;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Nachbild: eine eingefrorene, leuchtende Kopie der aktuellen Pose, die langsam zerfällt.
 * Bei Sprints entsteht so eine Spur aus Geistern – "er war gleichzeitig überall".
 */
public final class Afterimage extends BaseFx {

    private final int end;

    /**
     * @param parts   Momentaufnahme des Modells
     * @param body    Material für Körperteile
     * @param blade   Material für die Klinge
     * @param hold    Ticks, die das Bild unverändert steht
     * @param fade    Ticks zum Zerfallen
     * @param drift   Verschiebung beim Zerfallen (Blöcke)
     */
    public Afterimage(FxContext c, List<ModelInstance.PartState> parts, BlockData body, BlockData blade, Color glow,
                      int hold, int fade, Vector3f drift) {
        super(c.fx);
        end = hold + fade + 2;
        for (ModelInstance.PartState ps : parts) {
            if (ps.def().kind == CubeDef.Kind.TEXT) continue;
            Display d;
            if (ps.def().kind == CubeDef.Kind.ITEM) {
                if (ps.item() == null) continue;
                ItemDisplay id = item(ps.origin(), ps.item());
                d = id;
            } else {
                d = block(ps.origin(), ps.def().hasTag("blade") ? blade : body);
            }
            if (d == null) break;
            d.setBrightness(new Display.Brightness(15, 15));
            if (glow != null) {
                d.setGlowColorOverride(glow);
                d.setGlowing(true);
            }
            Transformation t = ps.transform();
            snapAt(0, d, t);
            Transformation gone;
            if (ps.def().kind == CubeDef.Kind.BLOCK) {
                Transformation shrunk = Tf.shrinkBox(t, 0.0f);
                gone = new Transformation(new Vector3f(shrunk.getTranslation()).add(drift), new Quaternionf(shrunk.getLeftRotation()),
                        new Vector3f(), new Quaternionf());
            } else {
                gone = new Transformation(new Vector3f(t.getTranslation()).add(drift), new Quaternionf(t.getLeftRotation()),
                        new Vector3f(), new Quaternionf());
            }
            lerpAt(1 + hold, d, gone, fade);
            freeAt(end, d);
        }
    }

    @Override
    protected boolean update(int age) {
        return age <= end;
    }
}
