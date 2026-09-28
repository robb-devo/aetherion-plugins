package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The mirror game's pieces, as small hazards that run alongside the scheduler:
 * a synchronized slash from each of the four Heralds, the punishing nova of a false one, and the glass
 * shards a broken reflection leaves behind.
 */
final class MirrorVolley {

    private static final Color AMBER = Color.fromRGB(255, 176, 60);
    private static final Color WHITE = Color.fromRGB(255, 244, 214);

    private MirrorVolley() {
    }

    static void strike(HeraldScript s, HeraldRig rig, Player target, boolean real) {
        s.spawnHazard(new Slash(s, rig, target));
    }

    static void nova(HeraldScript s, Vector3f at, double power) {
        s.spawnHazard(new Nova(s, at, power));
    }

    static void shatter(HeraldScript s, HeraldRig rig) {
        s.spawnHazard(new Shards(s, rig.chestPoint()));
        rig.visible(false);
    }

    /** One beat of warning (a fan of hairlines toward the target), then a short-range cut. */
    private static final class Slash extends Attack {
        private final HeraldScript s;
        private final HeraldRig rig;
        private final Vector3f aim;
        private final Shapes.Line[] fan = new Shapes.Line[3];
        private int tell;

        Slash(HeraldScript s, HeraldRig rig, Player target) {
            super(s);
            this.s = s;
            this.rig = rig;
            this.aim = stage.feet(target);
        }

        @Override
        public String id() {
            return "mirror_slash";
        }

        @Override
        public Family family() {
            return Family.GROUND;
        }

        @Override
        public void start() {
            tell = enc.tempo().ticks(1);
            float yaw = HMath.angleOf(aim.x - rig.root.x, aim.z - rig.root.z);
            for (int i = 0; i < fan.length; i++) {
                fan[i] = new Shapes.Line(stage, g, Material.ORANGE_STAINED_GLASS, AMBER);
                Vector3f tip = new Vector3f(rig.root).add(HMath.ring(5.5f, yaw + (i - 1) * 0.5f, 0.05f));
                fan[i].set(new Vector3f(rig.root).add(0f, 0.05f, 0f), tip, 0.04f, 3);
            }
            rig.pose(HeraldRig.Pose.slashWindup(), 0.3f);
            enc.score().at(new Vector3f(rig.root).add(0f, 2f, 0f), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
        }

        @Override
        protected boolean tick() {
            if (t == tell) {
                rig.snap(HeraldRig.Pose.slash());
                float yaw = HMath.angleOf(aim.x - rig.root.x, aim.z - rig.root.z);
                enc.score().at(rig.root, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                for (Player p : enc.fighters()) {
                    Vector3f f = stage.feet(p);
                    Vector3f d = new Vector3f(f).sub(rig.root);
                    d.y = 0f;
                    float dist = d.length();
                    if (dist < 5.5f && Math.abs(HMath.wrap(HMath.angleOf(d.x, d.z) - yaw)) < 0.6f) {
                        enc.hit(p, s.power("blink", 70) * 0.8, "mirror_slash", 10, HeraldScript.away(rig.root, f, 0.8));
                    }
                }
                for (Shapes.Line l : fan) {
                    l.hide(rig.root, 3);
                }
            }
            if (t == tell + 6) {
                rig.pose(HeraldRig.Pose.guard(), 0.2f);
            }
            return t > tell + 8;
        }
    }

    /** A flat ring of light that bursts out of a false Herald. Jump it or be thrown. */
    private static final class Nova extends Attack {
        private final Vector3f at;
        private final double power;
        private Shapes.Ring ring;

        Nova(HeraldScript s, Vector3f at, double power) {
            super(s);
            this.at = new Vector3f(at);
            this.power = power;
        }

        @Override
        public String id() {
            return "mirror_nova";
        }

        @Override
        public Family family() {
            return Family.GROUND;
        }

        @Override
        public void start() {
            ring = new Shapes.Ring(stage, g, 18, Material.WHITE_CONCRETE, WHITE, 15, true);
            ring.flat(new Vector3f(at).add(0f, 0.4f, 0f), 0.5f, 0.2f, 0.5f, 0f, 0);
            enc.score().at(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
            enc.score().at(at, Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
        }

        @Override
        protected boolean tick() {
            float r = 0.5f + t * 0.75f;
            ring.flat(new Vector3f(at).add(0f, 0.4f, 0f), r, 0.2f, 0.5f, t * 0.1f, 1);
            for (Player p : enc.fighters()) {
                Vector3f f = stage.feet(p);
                float d = HMath.horizontal(new Vector3f(f).sub(at));
                if (Math.abs(d - r) < 0.8f && f.y - at.y < 0.8f) {
                    enc.hit(p, power, "mirror_nova", 10, HeraldScript.away(at, f, 1.0));
                }
            }
            if (t == 10) {
                ring.hide(at, 4);
            }
            return t > 12;
        }
    }

    /** A broken reflection: glass shards burst out, tumble and fall. */
    private static final class Shards extends Attack {
        private final Vector3f at;
        private final List<BlockDisplay> shards = new ArrayList<>();
        private final List<Vector3f> vel = new ArrayList<>();
        private final List<Vector3f> pos = new ArrayList<>();

        Shards(HeraldScript s, Vector3f at) {
            super(s);
            this.at = new Vector3f(at);
        }

        @Override
        public String id() {
            return "mirror_shards";
        }

        @Override
        public Family family() {
            return Family.SKY;
        }

        @Override
        public void start() {
            int n = stage.budget().scaled(10, 5);
            for (int i = 0; i < n; i++) {
                BlockDisplay d = g.block(Material.WHITE_STAINED_GLASS.createBlockData(), WHITE, 15, false);
                if (d == null) {
                    break;
                }
                shards.add(d);
                pos.add(new Vector3f(at));
                vel.add(new Vector3f(HMath.hash(i, 3) - 0.5f, HMath.hash(i, 7) * 0.6f + 0.1f, HMath.hash(i, 11) - 0.5f).mul(0.8f));
            }
            enc.score().at(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
            enc.score().at(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
        }

        @Override
        protected boolean tick() {
            if (t % 2 == 0) {
                for (int i = 0; i < shards.size(); i++) {
                    Vector3f v = vel.get(i);
                    Vector3f p = pos.get(i);
                    p.add(new Vector3f(v).mul(2f));
                    v.y -= 0.08f;
                    Quaternionf rot = new Quaternionf().rotateXYZ(t * 0.3f + i, t * 0.2f, i);
                    stage.push(shards.get(i), HeliosStage.cube(p, 0.35f * (1f - t / 30f), rot), 2);
                }
            }
            return t > 28;
        }
    }
}
