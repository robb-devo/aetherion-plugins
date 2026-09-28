package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;

import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * What the fight broke keeps circling the arena: rubble of the burned Corona (and later the Course)
 * on slow orbits just outside the rim, some of it still glowing. Purely visual, cheap, and it keeps
 * the arena reading as a wreck in progress. At the end it all falls into the dying heart.
 */
final class DebrisField {

    private static final Material[] RUBBLE = {
            Material.DEEPSLATE_TILES, Material.POLISHED_BLACKSTONE, Material.BLACKSTONE, Material.MAGMA_BLOCK,
            Material.DEEPSLATE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE, Material.OCHRE_FROGLIGHT
    };

    private final HeliosStage stage;
    private final HeliosStage.Group g;
    private final List<Rock> rocks = new ArrayList<>();
    private int t;
    private float collapse;
    private final Vector3f pull = new Vector3f();

    private static final class Rock {
        BlockDisplay d;
        float r;
        float a;
        float y;
        float size;
        float speed;
        float spin;
    }

    DebrisField(HeliosStage stage) {
        this.stage = stage;
        this.g = stage.group();
    }

    /** Adds {@code n} pieces between radius {@code from} and {@code to}. */
    void add(int n, float from, float to) {
        int count = stage.budget().scaled(n, Math.max(2, n / 3));
        for (int i = 0; i < count; i++) {
            int k = rocks.size();
            Material m = RUBBLE[(int) (HMath.hash(k, 3) * RUBBLE.length) % RUBBLE.length];
            BlockDisplay d = g.block(m.createBlockData(), null, m == Material.MAGMA_BLOCK || m == Material.OCHRE_FROGLIGHT ? 15 : 9, false);
            if (d == null) {
                break;
            }
            Rock r = new Rock();
            r.d = d;
            r.r = HMath.lerp(from, to, HMath.hash(k, 5));
            r.a = HMath.hash(k, 7) * HMath.TAU;
            r.y = (HMath.hash(k, 11) - 0.35f) * 14f;
            r.size = 0.6f + HMath.hash(k, 13) * 1.8f;
            r.speed = (0.0015f + HMath.hash(k, 17) * 0.002f) * (HMath.hash(k, 19) > 0.5f ? 1f : -1f);
            r.spin = (HMath.hash(k, 23) - 0.5f) * 0.05f;
            stage.push(d, HeliosStage.gone(HMath.ring(r.r, r.a, r.y)), 0);
            rocks.add(r);
        }
    }

    void collapse(Vector3f toward, float f) {
        pull.set(toward);
        collapse = HMath.clamp01(f);
    }

    void tick() {
        t++;
        if (t % 2 != 0) {
            return;
        }
        for (Rock r : rocks) {
            r.a += r.speed * 2f * (1f + collapse * 12f);
            Vector3f at = HMath.ring(r.r, r.a, r.y + 0.4f * (float) Math.sin(t * 0.02f + r.a * 3f));
            if (collapse > 0f) {
                at.lerp(pull, HMath.inCubic(collapse));
            }
            Quaternionf rot = new Quaternionf().rotateXYZ(t * r.spin, t * r.spin * 0.7f, r.a);
            stage.push(r.d, HeliosStage.cube(at, r.size * (1f - 0.9f * collapse), rot), HeliosStage.SMOOTH_2);
        }
    }

    void clear() {
        g.clear();
        rocks.clear();
    }
}
