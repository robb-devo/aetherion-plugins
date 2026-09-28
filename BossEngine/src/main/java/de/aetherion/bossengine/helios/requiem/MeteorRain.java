package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.world.ArenaLayout;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * METEOR RAIN. The burned-out Corona's rubble is hauled up into the sky and thrown back down. Each
 * meteor gets its own red floor marker two beats before it lands (a ring that tightens onto its point)
 * and trails a hot line; they land one after another on the beat, near the players and in between.
 */
final class MeteorRain extends Attack {

    private static final Color HOT = Color.fromRGB(255, 110, 30);
    private static final Material[] RUBBLE = {Material.DEEPSLATE_TILES, Material.BLACKSTONE, Material.MAGMA_BLOCK, Material.CHISELED_POLISHED_BLACKSTONE};

    private final HeliosScript h;
    private final int count;
    private final int every;
    private final List<Rock> rocks = new ArrayList<>();

    private final class Rock {
        final Vector3f to;
        final Vector3f from;
        final int launch;
        final int land;
        final BlockDisplay body;
        final Shapes.Ring marker;
        final Shapes.Line trail;
        final float size;
        boolean done;

        Rock(Vector3f to, int launch, int land, int i) {
            this.to = to;
            this.launch = launch;
            this.land = land;
            float side = HMath.hash(i, 5) * HMath.TAU;
            this.from = new Vector3f(to).add(HMath.ring(14f, side, 30f));
            this.size = 1.2f + HMath.hash(i, 9) * 0.9f;
            Material m = RUBBLE[i % RUBBLE.length];
            this.body = g.block(m.createBlockData(), HOT, m == Material.MAGMA_BLOCK ? 15 : 11, true);
            this.marker = new Shapes.Ring(stage, g, 14, Material.RED_STAINED_GLASS, HOT, 15, true);
            this.trail = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, HOT);
            stage.push(body, HeliosStage.gone(from), 0);
            marker.hide(to, 0);
            trail.hide(from, 0);
        }
    }

    MeteorRain(HeliosScript h, int count, double everyBeats) {
        super(h);
        this.h = h;
        this.count = Math.max(1, count);
        this.every = Math.max(3, enc.tempo().ticks(everyBeats));
    }

    @Override
    public String id() {
        return "meteors";
    }

    @Override
    public Family family() {
        return Family.SKY;
    }

    @Override
    public void start() {
        List<Player> f = enc.fighters();
        int warn = enc.tempo().ticks(2);
        int first = enc.tempo().ticksUntil(0);
        for (int i = 0; i < count; i++) {
            Vector3f to;
            if (!f.isEmpty() && i % 2 == 0) {
                to = stage.feet(f.get(ThreadLocalRandom.current().nextInt(f.size())));
            } else {
                float r = ArenaLayout.RING_IN[0] + ThreadLocalRandom.current().nextFloat() * 13f;
                to = HMath.ring(r, ThreadLocalRandom.current().nextFloat() * HMath.TAU, 0f);
            }
            to.y = 0.05f;
            int land = first + warn + i * every;
            rocks.add(new Rock(to, land - warn, land, i));
        }
        enc.score().play(Sound.BLOCK_BASALT_BREAK, 1f, 0.5f);
        enc.score().play(Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.5f);
    }

    @Override
    protected boolean tick() {
        boolean alive = false;
        for (Rock r : rocks) {
            if (r.done) {
                continue;
            }
            alive = true;
            if (t < r.launch) {
                continue;
            }
            float f = HMath.window(t, r.launch, r.land);
            if (t == r.launch) {
                r.marker.flat(r.to, 3.2f, 0.1f, 0.04f, 0f, 0);
                r.marker.flat(r.to, 1.4f, 0.14f, 0.04f, 0f, r.land - r.launch);
                enc.score().at(r.to, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.6f);
            }
            if (t < r.land) {
                Vector3f at = new Vector3f(r.from).lerp(r.to, HMath.inQuad(f));
                Vector3f back = new Vector3f(r.from).lerp(r.to, HMath.inQuad(Math.max(0f, f - 0.15f)));
                stage.push(r.body, HeliosStage.cube(at, r.size, new Quaternionf().rotateXYZ(t * 0.25f, t * 0.17f, 0f)), 1);
                r.trail.set(back, at, 0.3f * r.size, 1);
                if (t % 6 == 0) {
                    enc.score().at(at, Sound.ENTITY_BLAZE_BURN, 0.6f, 0.5f);
                }
                continue;
            }
            // Impact.
            r.done = true;
            stage.push(r.body, HeliosStage.gone(r.to), 3);
            r.trail.hide(r.to, 2);
            r.marker.hide(r.to, 4);
            enc.score().at(r.to, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
            enc.score().at(r.to, Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.5f);
            enc.camera().shakeFrom(r.to, 12f, 6);
            stage.blockDust(new Vector3f(r.to).add(0f, 0.5f, 0f), Material.BLACKSTONE, 30, 1.6);
            for (int k = 0; k < 5; k++) {
                float a = HMath.hash(k, (int) r.to.x) * HMath.TAU;
                enc.arena().crackLine(r.to, new Vector3f(r.to).add(HMath.ring(3.5f, a, 0f)), 0.7f, 120);
            }
            double power = h.power("meteors", 70);
            for (Player p : enc.fighters()) {
                Vector3f pf = stage.feet(p);
                if (HMath.horizontal(new Vector3f(pf).sub(r.to)) < 2.8f && pf.y < 3f) {
                    enc.hit(p, power, "meteor", 10, h.away(r.to, pf, 0.8));
                }
            }
        }
        return !alive;
    }
}
