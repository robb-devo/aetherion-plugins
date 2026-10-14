package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.render.Tf;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Leuchtende Schnitt-Sichel: Ein Bogen aus dünnen, tangentialen Segmenten (in der Mitte dick,
 * an den Enden spitz). Er erscheint in Schwungrichtung Segment für Segment, dehnt sich kurz
 * aus und verblasst. Kern weiß, Hülle rosa (bzw. violett für Schatten).
 *
 * <p>Die Ebene der Sichel wird über plane festgelegt: lokale X/Z-Ebene = Schnittebene,
 * Winkel 0 = lokale +X-Achse. Mit Resource Pack: ein einzelnes Sichel-Modell.</p>
 */
public final class SlashArc extends BaseFx {

    private final int end;

    public SlashArc(FxContext c, Location center, Quaternionf plane, float radius, float startDeg, float sweepDeg,
                    float thickness, int appearTicks, int fadeTicks, BlockData core, BlockData glow) {
        super(c.fx);
        if (c.pack && core == Palette.LIGHT) {
            // Ein Sichel-Item, das per Skalierung "aufzieht" (Modell liegt in der XZ-Ebene, Bogen 180° ab +X)
            ItemDisplay d = item(center, PackItems.item(c.settings, glow == Palette.PURPLE_GLASS ? "slash" : "slash-pink"));
            end = appearTicks + fadeTicks + 2;
            if (d != null) {
                d.setBrightness(new Display.Brightness(15, 15));
                float mid = startDeg + sweepDeg / 2f;
                Quaternionf rot = new Quaternionf(plane).rotateY((float) Math.toRadians(-mid + 90));
                if (sweepDeg < 0) rot.rotateZ((float) Math.PI);
                float s = radius * 2.2f;
                snapAt(0, d, Tf.centered(new Vector3f(), rot, new Vector3f(s * 0.3f, s * 0.3f, s * 0.3f)));
                lerpAt(1, d, Tf.centered(new Vector3f(), rot, new Vector3f(s, s, s)), Math.max(1, appearTicks));
                lerpAt(1 + appearTicks, d, Tf.centered(new Vector3f(), rot, new Vector3f(s * 1.15f, s * 0.2f, s * 1.15f)), fadeTicks);
                freeAt(end, d);
            }
            return;
        }
        int n = Math.max(6, Math.min(16, Math.round(Math.abs(sweepDeg) / 13f)));
        float segAngle = sweepDeg / n;
        float segLen = (float) (2 * Math.PI * radius * Math.abs(segAngle) / 360f) * 1.12f;
        end = appearTicks + fadeTicks + 3;
        for (int i = 0; i < n; i++) {
            float a = (float) Math.toRadians(startDeg + segAngle * (i + 0.5f));
            float profile = (float) Math.sin(Math.PI * (i + 0.5f) / n);
            float w = Math.max(0.03f, thickness * profile);
            Vector3f pos = plane.transform(new Vector3f((float) Math.cos(a) * radius, 0, (float) Math.sin(a) * radius));
            Quaternionf rot = new Quaternionf(plane).rotateY(-a + (float) (Math.PI / 2));
            Vector3f posOut = plane.transform(new Vector3f((float) Math.cos(a) * radius * 1.12f, 0, (float) Math.sin(a) * radius * 1.12f));
            int appear = Math.round(appearTicks * i / (float) n);
            for (int layer = 0; layer < 2; layer++) {
                Display d = block(center, layer == 0 ? core : glow);
                if (d == null) continue;
                d.setBrightness(new Display.Brightness(15, 15));
                float lw = layer == 0 ? w * 0.45f : w;
                float lh = layer == 0 ? 0.035f : 0.09f;
                float ll = layer == 0 ? segLen : segLen * 1.06f;
                snapAt(0, d, Tf.box(pos, rot, new Vector3f(0.001f, 0.001f, 0.001f)));
                // erscheinen (dünn -> volle Breite)
                lerpAt(Math.max(1, appear), d, Tf.box(pos, rot, new Vector3f(ll, lh, lw)), 2);
                // verblassen: dehnt sich minimal nach außen, wird hauchdünn
                lerpAt(appearTicks + 2, d, Tf.box(posOut, rot, new Vector3f(ll * 0.9f, lh * 0.4f, 0.0f)), fadeTicks);
                freeAt(end, d);
            }
        }
    }

    @Override
    protected boolean update(int age) {
        return age <= end;
    }

    /** Ebene für einen Schnitt: Yaw des Bosses + Neigung (0 = waagrecht, 90 = senkrecht) um die Blickachse. */
    public static Quaternionf plane(float mcYaw, float tiltDeg) {
        return new Quaternionf().rotationY((float) Math.toRadians(-mcYaw)).rotateZ((float) Math.toRadians(tiltDeg));
    }
}
