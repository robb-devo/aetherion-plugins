package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PLASMA RINGS. Rings of plasma roll out from under Helios across the floor, one per beat, each with a
 * gap. Two kinds, told apart by colour and by sound:
 *
 * <ul>
 *   <li><b>Amber, low</b> (knee height, a low horn): jump it.</li>
 *   <li><b>Cyan, high</b> (head height, a high glassy ping): duck under it (sneak).</li>
 * </ul>
 * Or run through the gap. The ring glows brighter on the star's heartbeat.
 */
final class PlasmaRings extends Attack {

    enum Kind { LOW, HIGH }

    private static final Color AMBER = Color.fromRGB(255, 160, 40);
    private static final Color CYAN = Color.fromRGB(80, 230, 255);
    private static final float SPEED = 0.42f;
    private static final float MAX_R = 34f;

    private final HeliosScript h;
    private final int count;
    private final Kind fixed;
    private final List<Wave> waves = new ArrayList<>();
    private final Vector3f origin = new Vector3f();
    private int launched;
    private int nextAt;
    private final int seed = ThreadLocalRandom.current().nextInt(2);

    private final class Wave {
        final Kind kind;
        final Shapes.Ring ring;
        final float gapFrom;
        final float gapTo;
        float r = 1.5f;
        boolean done;

        Wave(Kind kind, float gapAt) {
            this.kind = kind;
            this.ring = new Shapes.Ring(stage, g, stage.budget().scaled(36, 20),
                    kind == Kind.LOW ? Material.ORANGE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS,
                    kind == Kind.LOW ? AMBER : CYAN, 15, true);
            this.gapFrom = gapAt - 0.45f;
            this.gapTo = gapAt + 0.45f;
        }

        float y() {
            return kind == Kind.LOW ? 0.45f : 2.0f;
        }
    }

    /**
     * @param count how many rings (one per beat)
     * @param fixed force every ring to this kind, or null to alternate
     */
    PlasmaRings(HeliosScript h, int count, Kind fixed) {
        super(h);
        this.h = h;
        this.count = Math.max(1, count);
        this.fixed = fixed;
    }

    @Override
    public String id() {
        return "plasma";
    }

    @Override
    public Family family() {
        return Family.GROUND;
    }

    @Override
    public void start() {
        origin.set(h.rig().center.x, 0f, h.rig().center.z);
        nextAt = enc.tempo().ticksUntil(0);
        h.rig().ringScale(0.8f);
    }

    @Override
    protected boolean tick() {
        if (launched < count && t >= nextAt) {
            // Alternate jump / duck, starting with either.
            Kind kind = fixed != null ? fixed : (launched + seed) % 2 == 0 ? Kind.LOW : Kind.HIGH;
            Wave w = new Wave(kind, ThreadLocalRandom.current().nextFloat() * HMath.TAU);
            waves.add(w);
            launched++;
            nextAt = t + Math.max(4, enc.tempo().ticks(1));
            if (kind == Kind.LOW) {
                enc.score().at(origin, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.6f);
                enc.score().at(origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
            } else {
                enc.score().at(new Vector3f(origin).add(0f, 2f, 0f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.9f);
                enc.score().at(new Vector3f(origin).add(0f, 2f, 0f), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 2f);
            }
        }
        boolean alive = false;
        float glow = 0.2f + 0.1f * enc.star().beat();
        for (Wave w : waves) {
            if (w.done) {
                continue;
            }
            alive = true;
            w.r += SPEED;
            float tall = w.kind == Kind.LOW ? 0.9f : 0.8f;
            w.ring.pose(new Vector3f(origin).add(0f, w.y(), 0f), new org.joml.Quaternionf(), w.r, 0.35f + glow, tall,
                    0f, w.gapFrom, w.gapTo, 1);
            hurt(w);
            if (w.r > MAX_R) {
                w.ring.hide(new Vector3f(origin).add(0f, w.y(), 0f), 3);
                w.done = true;
            }
        }
        return launched >= count && !alive && t > nextAt;
    }

    private void hurt(Wave w) {
        double power = h.power("plasma", 50);
        for (Player p : enc.fighters()) {
            Vector3f f = stage.feet(p);
            float d = HMath.horizontal(new Vector3f(f).sub(origin));
            if (Math.abs(d - w.r) > 0.55f) {
                continue;
            }
            float a = HMath.angleOf(f.x - origin.x, f.z - origin.z);
            float off = Math.abs(HMath.wrap(a - (w.gapFrom + w.gapTo) * 0.5f));
            if (off < (w.gapTo - w.gapFrom) * 0.5f) {
                continue;
            }
            boolean hit;
            if (w.kind == Kind.LOW) {
                hit = f.y < 0.85f;
            } else {
                double height = p.getBoundingBox().getHeight();
                hit = f.y + height > 1.6f && f.y < 2.4f;
            }
            if (hit) {
                Vector3f out = new Vector3f(f).sub(origin);
                out.y = 0f;
                out.normalize();
                enc.hit(p, power, "plasma", 10, new Vector(out.x * 0.6, 0.35, out.z * 0.6));
            }
        }
    }

    @Override
    protected void cleanup() {
        h.rig().ringScale(1f);
    }
}
