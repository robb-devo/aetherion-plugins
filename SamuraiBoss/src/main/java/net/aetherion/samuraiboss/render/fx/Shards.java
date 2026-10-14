package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Splitter-Explosion (Glas, Licht, Stahl): kleine Würfel fliegen mit "Schwerkraft" davon,
 * drehen sich und schrumpfen. Für zerbrechende Kanji, Klingen, Treffer.
 */
public final class Shards extends BaseFx {

    private final int end;

    public Shards(FxContext c, Location center, int count, float speed, float size, int life, BlockData... mats) {
        super(c.fx);
        int n = c.count(count);
        end = life + 2;
        int k1 = Math.max(2, life / 3), k2 = Math.max(2, life / 3), k3 = Math.max(2, life - k1 - k2);
        for (int i = 0; i < n; i++) {
            Display d = block(center, mats[i % mats.length]);
            if (d == null) break;
            d.setBrightness(new Display.Brightness(15, 15));
            float s = size * (0.5f + Rand.f());
            Vector3f sz = new Vector3f(s, s * (0.3f + Rand.f() * 0.6f), s * (0.4f + Rand.f() * 0.6f));
            Vector3f v = Rand.unit().mul(speed * (0.5f + Rand.f() * 0.7f));
            v.y = Math.abs(v.y) * 0.8f + speed * 0.2f;
            Vector3f p1 = new Vector3f(v);
            Vector3f p2 = new Vector3f(v).mul(1.6f).add(0, -0.4f, 0);
            Vector3f p3 = new Vector3f(v).mul(2.0f).add(0, -1.6f, 0);
            Quaternionf r0 = FxContext.randomRot();
            snapAt(0, d, Tf.box(new Vector3f(), r0, new Vector3f(sz).mul(0.5f)));
            lerpAt(1, d, Tf.box(p1, FxContext.randomRot(), sz), k1);
            lerpAt(1 + k1, d, Tf.box(p2, FxContext.randomRot(), new Vector3f(sz).mul(0.8f)), k2);
            lerpAt(1 + k1 + k2, d, Tf.box(p3, FxContext.randomRot(), new Vector3f()), k3);
            freeAt(end, d);
        }
    }

    @Override
    protected boolean update(int age) {
        return age <= end;
    }
}
