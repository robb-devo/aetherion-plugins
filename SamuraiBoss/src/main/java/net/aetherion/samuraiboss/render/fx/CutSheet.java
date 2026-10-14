package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.MathUtil;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Die "Schnittlinie" eines Iai-Hiebs: eine hauchdünne, senkrechte Lichtwand entlang der Strecke
 * a -> b, die schlagartig aufleuchtet, hoch schießt und dann zu einer feinen Linie zerfällt.
 * Optional zweigeteilt (die Hälften gleiten auseinander – "der Raum ist zerschnitten").
 */
public final class CutSheet extends BaseFx {

    private final int end;

    public CutSheet(FxContext c, Location a, Location b, float height, int life, BlockData core, BlockData glow, boolean split) {
        super(c.fx);
        Vector3f dir = MathUtil.delta(a, b);
        float len = dir.length();
        Quaternionf rot = MathUtil.lookAlongX(new Vector3f(dir.x, 0, dir.z));
        float tilt = (float) Math.atan2(dir.y, Math.hypot(dir.x, dir.z));
        rot.rotateZ(tilt);
        Vector3f mid = new Vector3f(dir).mul(0.5f).add(0, height / 2f, 0);
        end = life + 2;
        Display coreD = block(a, core);
        Display glowD = block(a, glow);
        if (coreD != null) {
            coreD.setBrightness(new Display.Brightness(15, 15));
            snapAt(0, coreD, Tf.box(mid, rot, new Vector3f(len, 0.01f, 0.01f)));
            lerpAt(1, coreD, Tf.box(mid, rot, new Vector3f(len * 1.02f, height, 0.035f)), 2);
            lerpAt(3, coreD, Tf.box(mid, rot, new Vector3f(len * 1.05f, height * 0.06f, 0.02f)), Math.max(2, life - 4));
            freeAt(end, coreD);
        }
        if (glowD != null) {
            glowD.setBrightness(new Display.Brightness(15, 15));
            snapAt(0, glowD, Tf.box(mid, rot, new Vector3f(len, 0.01f, 0.01f)));
            lerpAt(1, glowD, Tf.box(mid, rot, new Vector3f(len * 1.04f, height * 1.15f, 0.16f)), 2);
            lerpAt(3, glowD, Tf.box(mid, rot, new Vector3f(len * 1.08f, 0.0f, 0.0f)), Math.max(2, life - 3));
            freeAt(end, glowD);
        }
        if (split) {
            // Zwei Hälften einer weißen Ebene gleiten an der Schnittlinie auseinander
            Vector3f perp = rot.transform(new Vector3f(0, 0, 1));
            for (int s = -1; s <= 1; s += 2) {
                Display h = block(a, Palette.WHITE_GLASS);
                if (h == null) continue;
                h.setBrightness(new Display.Brightness(15, 15));
                Vector3f m0 = new Vector3f(mid).add(new Vector3f(perp).mul(s * 0.05f));
                Vector3f m1 = new Vector3f(mid).add(new Vector3f(perp).mul(s * 0.45f)).add(0, s * 0.15f, 0);
                snapAt(1, h, Tf.box(m0, rot, new Vector3f(len, height * 0.8f, 0.02f)));
                lerpAt(2, h, Tf.box(m1, rot, new Vector3f(len * 1.1f, 0.0f, 0.0f)), Math.max(3, life - 2));
                freeAt(end, h);
            }
        }
    }

    @Override
    protected boolean update(int age) {
        return age <= end;
    }
}
