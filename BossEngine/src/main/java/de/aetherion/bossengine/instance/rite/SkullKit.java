package de.aetherion.bossengine.instance.rite;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.util.AttributeUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skuldugery, "The Ash Archer": a Sun Guard who ran and kept burning. His fire is soot and embers, not a flame
 * sphere: he climbs out of a funeral pyre, carries a smouldering quiver, drops arrows the size of a man out of
 * the sky that stay stuck and burning, and rings himself with them when he turns. Char, ember, ash and bone.
 */
final class SkullKit extends RiteKit {

    private static final Color EMBER = Color.fromRGB(255, 118, 28);
    private static final Color CHAR = Color.fromRGB(34, 28, 26);
    private static final Color SOOT = Color.fromRGB(70, 64, 60);
    private static final Color SUN = Color.fromRGB(255, 206, 90);
    private static final double SCORCH = 2.2;
    /** Arrow sprites point up-right in their item texture. */
    private static final Vector3f TIP = new Vector3f(0.7071f, 0.7071f, 0f);

    private final List<Location> marks = new ArrayList<>();
    private final List<List<BlockDisplay>> scorches = new ArrayList<>();
    private boolean quiver;

    SkullKit(BossInstance instance) {
        super(instance);
    }

    /* ================================================================== look */

    @Override
    protected void dress(LivingEntity boss) {
        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            eq.setChestplate(guard(Material.LEATHER_CHESTPLATE, TrimPattern.SENTRY));
            eq.setBoots(guard(Material.LEATHER_BOOTS, TrimPattern.SENTRY));
            eq.setChestplateDropChance(0f);
            eq.setBootsDropChance(0f);
        }
        if (!quiver) {
            quiver = true;
            Location at = boss.getLocation();
            Follow box = wear(stage.block(at, Material.SPRUCE_LOG, null, null, -1),
                    Stage.v(0.08, 1.0, -0.2), Stage.rot(0, -0.3, 0.15), 1f, true);
            if (box != null) {
                box.sx = 0.2f;
                box.sy = 0.5f;
                box.sz = 0.2f;
            }
            for (int i = 0; i < 3; i++) {
                double side = 0.03 + i * 0.05;
                wear(stage.item(at, Material.ARROW, null, null, -1),
                        Stage.v(side, 1.38 + i * 0.03, -0.27), arrowUp(-0.3, 0.15 + i * 0.06), 0.42f, false);
            }
        }
    }

    private static ItemStack guard(Material material, TrimPattern pattern) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(0x2B2420));
            stack.setItemMeta(meta);
        }
        if (stack.getItemMeta() instanceof ArmorMeta meta) {
            meta.setTrim(new ArmorTrim(TrimMaterial.GOLD, pattern));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** An arrow sprite turned to point straight up, then leaned back by {@code pitch} and sideways by {@code roll}. */
    private static Quaternionf arrowUp(double pitch, double roll) {
        return new Quaternionf().rotateX((float) pitch).rotateZ((float) roll).rotateZ((float) (Math.PI / 4));
    }

    /** Rotation that points the arrow sprite's tip along {@code dir}. */
    private static Quaternionf arrowAlong(Vector3f dir) {
        Vector3f d = new Vector3f(dir).normalize();
        return new Quaternionf().rotationTo(TIP, d);
    }

    @Override
    protected void ambient(LivingEntity boss, int age) {
        Location feet = boss.getLocation();
        double h = boss.getHeight();
        if (age % 3 == 0) {
            Location tip = bodyPoint(feet, 0.08, h * 0.78, -0.3);
            Stage.fx(tip, Particle.SMALL_FLAME, 1, 0.05, 0.005);
        }
        if (age % 4 == 0) {
            Stage.fx(feet.clone().add(0, h * 0.5, 0), Particle.WHITE_ASH, 3, 0.6, 0.0);
        }
        if (age % 18 == 0) {
            Stage.fx(bodyPoint(feet, 0, h * 0.85, -0.1), Particle.CAMPFIRE_COSY_SMOKE, 1, 0.05, 0.01);
        }
        if (age % 40 == 0) {
            Stage.fx(feet.clone().add(0, h * 0.4, 0), Particle.LAVA, 1, 0.3, 0.0);
        }
    }

    /* ================================================================== arrival: out of the pyre */

    private final List<Display> pyre = new ArrayList<>();

    @Override
    protected int arrivalTicks() {
        return 66;
    }

    @Override
    protected void arrivalStart(LivingEntity boss, Location at) {
        place(boss, at.clone().add(0, -2.6, 0));
        Location floor = Stage.ground(at);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // six logs crossed in three layers, two bones leaning in; every piece flies in from the dark
        for (int layer = 0; layer < 3; layer++) {
            for (int k = 0; k < 2; k++) {
                double yaw = layer % 2 == 0 ? 0 : Math.PI / 2;
                double off = k == 0 ? -0.45 : 0.45;
                Vector3f c = layer % 2 == 0 ? Stage.v(off, 0.22 + layer * 0.4, 0) : Stage.v(0, 0.22 + layer * 0.4, off);
                double a = rng.nextDouble(Math.PI * 2);
                Vector3f from = new Vector3f(c).add((float) (Math.cos(a) * 6), 4f, (float) (Math.sin(a) * 6));
                Quaternionf q = Stage.yaw(yaw).rotateX((float) (Math.PI / 2));
                BlockDisplay log = stage.block(floor, Material.DARK_OAK_LOG, Stage.cube(from, q, 0.38f, 2.4f, 0.38f), null, -1);
                pyre.add(log);
                int delay = 2 + (layer * 2 + k) * 3;
                stage.play(t -> {
                    if (t == delay) {
                        Stage.push(log, Stage.cube(c, q, 0.38f, 2.4f, 0.38f), 7);
                    }
                    if (t == delay + 7) {
                        Stage.sound(floor, Sound.BLOCK_WOOD_PLACE, 0.9f, 0.6f);
                    }
                    return t >= delay + 7;
                });
            }
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            Vector3f c = Stage.v(Math.cos(a) * 0.95, 0.55, Math.sin(a) * 0.95);
            Quaternionf q = arrowAlong(Stage.v(-Math.cos(a) * 0.4, 1, -Math.sin(a) * 0.4));
            ItemDisplay bone = stage.item(floor, Material.BONE, Stage.model(new Vector3f(c).add(0, 6, 0), q, 0.01f), null, -1);
            pyre.add(bone);
            int delay = 18 + k * 2;
            stage.play(t -> {
                if (t == delay) {
                    Stage.push(bone, Stage.model(c, q, 1.1f), 6);
                }
                return t >= delay;
            });
        }
        Stage.sound(floor, Sound.ENTITY_SKELETON_AMBIENT, 1.2f, 0.5f);
    }

    @Override
    protected void arrivalTick(LivingEntity boss, Location at, int t) {
        Location floor = Stage.ground(at);
        if (t == 28) {
            Stage.sound(floor, Sound.ITEM_FIRECHARGE_USE, 1.4f, 0.6f);
            Stage.sound(floor, Sound.BLOCK_FIRE_AMBIENT, 1.4f, 0.6f);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + 0.3;
                Vector3f c = Stage.v(Math.cos(a) * 0.35, 0.25, Math.sin(a) * 0.35);
                BlockDisplay fire = stage.block(floor, Material.CAMPFIRE, Stage.cube(c, Stage.yaw(a), 0.01f, 0.01f, 0.01f), null, 15);
                pyre.add(fire);
                Stage.push(fire, Stage.cube(c.add(0, 0.25f, 0), Stage.yaw(a), 1.3f, 1.4f, 1.3f), 6);
            }
            Stage.fx(floor.clone().add(0, 1.0, 0), Particle.FLAME, 30, 0.8, 0.06);
        }
        if (t > 28 && t % 2 == 0) {
            Stage.fx(floor.clone().add(0, 1.6, 0), Particle.CAMPFIRE_SIGNAL_SMOKE, 2, 0.3, 0.04);
            Stage.fx(floor.clone().add(0, 1.0, 0), Particle.LAVA, 1, 0.5, 0.0);
        }
        if (t >= 32 && t <= 52) {
            double u = Stage.ease((t - 32) / 20.0);
            place(boss, at.clone().add(0, -2.6 * (1.0 - u), 0));
            for (int i = 0; i < 3; i++) {
                double a = t * 0.5 + i * Math.PI * 2 / 3;
                double y = (t - 32) * 0.15;
                Stage.fx(floor.clone().add(Math.cos(a) * 1.2, y, Math.sin(a) * 1.2), Particle.SMALL_FLAME, 2, 0.05, 0.01);
            }
            Stage.fx(floor.clone().add(0, 1.4, 0), Particle.WHITE_ASH, 12, 1.4, 0.0);
            if (t == 36) {
                Stage.sound(floor, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
                Stage.sound(floor, Sound.ENTITY_SKELETON_AMBIENT, 1.4f, 0.45f);
            }
        }
        if (t == 54) {
            place(boss, at);
            // the reveal: one burning arrow straight up, bursting into embers high above
            Location bow = at.clone().add(0, boss.getHeight() * 0.8, 0);
            ItemDisplay arrow = stage.item(bow, Material.ARROW, Stage.model(Stage.v(0, 0, 0), arrowAlong(Stage.v(0, 1, 0)), 2.0f), EMBER, 15);
            stage.play(k -> {
                if (k == 2) {
                    Stage.push(arrow, Stage.model(Stage.v(0, 22, 0), arrowAlong(Stage.v(0, 1, 0)), 2.0f), 9);
                }
                if (k > 1 && k < 11) {
                    Stage.fx(bow.clone().add(0, (k - 1) * 2.4, 0), Particle.FLAME, 3, 0.1, 0.01);
                }
                if (k == 11) {
                    Location sky = bow.clone().add(0, 22, 0);
                    stage.remove(arrow);
                    Stage.fx(sky, Particle.LAVA, 30, 1.6, 0.0);
                    Stage.fx(sky, Particle.FLAME, 40, 1.4, 0.08);
                    Stage.fx(sky, Particle.LARGE_SMOKE, 16, 1.2, 0.03);
                    Stage.sound(sky, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 2.0f, 0.6f);
                    return true;
                }
                return false;
            });
            Stage.sound(at, Sound.ENTITY_SKELETON_SHOOT, 1.5f, 0.6f);
            Stage.sound(at, Sound.ITEM_CROSSBOW_SHOOT, 1.2f, 0.5f);
        }
        if (t == 56) {
            for (Display d : pyre) {
                Stage.push(d, Stage.gone(Stage.v(0, 0.1, 0)), 10);
            }
            Stage.fx(floor.clone().add(0, 0.6, 0), Particle.LARGE_SMOKE, 20, 0.8, 0.03);
        }
    }

    @Override
    protected void arrivalEnd(LivingEntity boss, Location at) {
        for (Display d : pyre) {
            stage.remove(d);
        }
        pyre.clear();
        titleCard(boss, "&6&lSkuldugery", "The Ash Archer", "Der Aschebogen");
    }

    /* ================================================================== signature: ash rain */

    @Override
    protected int cooldown(boolean late) {
        return late ? 220 : 300;
    }

    @Override
    protected int begin(LivingEntity boss, List<Player> near, boolean late) {
        marks.clear();
        clearScorches();
        int want = late ? 5 : 3;
        for (Player p : near) {
            if (marks.size() < want) {
                marks.add(Stage.ground(p.getLocation()));
            }
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        while (marks.size() < want) {
            Location base = near.get(rng.nextInt(near.size())).getLocation();
            marks.add(Stage.ground(base.add(rng.nextDouble(-4.5, 4.5), 0, rng.nextDouble(-4.5, 4.5))));
        }
        for (Location m : marks) {
            scorch(m, late ? 20 : 24);
        }
        Location at = boss.getLocation();
        Stage.sound(at, Sound.ITEM_CROSSBOW_LOADING_END, 1.2f, 0.6f);
        Stage.sound(at, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.7f);
        cue(near, "&6Ash Rain &7· out of the scorch marks", "&6Ascheregen &7· raus aus den Brandflecken");
        return late ? 20 : 24;
    }

    /** A charred octagon spreading where an arrow will come down. */
    private void scorch(Location m, int windup) {
        List<BlockDisplay> parts = new ArrayList<>();
        parts.add(stage.block(m, Material.BLACK_CONCRETE, null, null, 2));
        parts.add(stage.block(m, Material.BLACK_CONCRETE, null, null, 2));
        parts.add(stage.block(m, Material.MAGMA_BLOCK, null, null, 12));
        scorches.add(parts);
        float d = (float) (SCORCH * 2);
        stage.play(t -> {
            if (t == 2) {
                Stage.push(parts.get(0), Stage.cube(Stage.v(0, 0.015, 0), new Quaternionf(), d, 0.02f, d), windup);
                Stage.push(parts.get(1), Stage.cube(Stage.v(0, 0.02, 0), Stage.yaw(Math.PI / 4), d, 0.02f, d), windup);
                Stage.push(parts.get(2), Stage.cube(Stage.v(0, 0.025, 0), Stage.yaw(Math.PI / 8), 0.7f, 0.02f, 0.7f), windup);
            }
            return t >= 2;
        });
    }

    private void clearScorches() {
        for (List<BlockDisplay> parts : scorches) {
            for (BlockDisplay d : parts) {
                stage.remove(d);
            }
        }
        scorches.clear();
    }

    private boolean white;

    @Override
    protected void charge(LivingEntity boss, int left, boolean late) {
        Location at = boss.getLocation();
        if (left % 2 == 0) {
            // the bow aims up: signal smoke climbs off him
            Stage.fx(at.clone().add(0, boss.getHeight() + 0.4, 0), Particle.CAMPFIRE_SIGNAL_SMOKE, 1, 0.1, 0.03);
            for (Location m : marks) {
                Stage.dustRing(m, SCORCH, white ? WHITE : EMBER, white ? 1.8f : 1.1f, 0.55);
                Stage.fx(m.clone().add(0, 0.2, 0), Particle.SMALL_FLAME, 1, 1.0, 0.0);
            }
        }
        if (left == 1) {
            white = false;
        }
    }

    @Override
    protected void flash(LivingEntity boss) {
        white = true;
        Stage.sound(boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.9f);
        Stage.sound(boss.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.4f, 0.5f);
    }

    @Override
    protected void strike(LivingEntity boss, boolean late) {
        white = false;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<List<BlockDisplay>> parts = new ArrayList<>(scorches);
        scorches.clear();
        for (int i = 0; i < marks.size(); i++) {
            Location m = marks.get(i);
            List<BlockDisplay> scorch = i < parts.size() ? parts.get(i) : List.of();
            Vector3f from = Stage.v(rng.nextDouble(-3, 3), 18, rng.nextDouble(-3, 3));
            stage.play(new Volley(m, from, scorch, late ? 24 : 20, boss));
        }
        marks.clear();
    }

    @Override
    protected void cancelMove() {
        marks.clear();
        clearScorches();
        white = false;
    }

    /** One man-sized burning arrow out of the sky; damage lands with it, then it stays stuck and smouldering. */
    private final class Volley implements Stage.Anim {
        final Location at;
        final Vector3f from;
        final List<BlockDisplay> scorch;
        final double power;
        final LivingEntity boss;
        ItemDisplay arrow;
        BlockDisplay fire;
        Quaternionf q;

        Volley(Location at, Vector3f from, List<BlockDisplay> scorch, double power, LivingEntity boss) {
            this.at = at.clone();
            this.from = from;
            this.scorch = scorch;
            this.power = power;
            this.boss = boss;
        }

        @Override
        public boolean tick(int t) {
            if (t == 1) {
                q = arrowAlong(new Vector3f(from).negate());
                arrow = stage.item(at, Material.ARROW, Stage.model(from, q, 3.4f), EMBER, 15);
                return false;
            }
            if (t == 2) {
                Vector3f stuck = new Vector3f(from).normalize().mul(1.1f);
                Stage.push(arrow, Stage.model(stuck, q, 3.4f), 4);
            }
            if (t >= 2 && t <= 5) {
                Vector3f p = new Vector3f(from).mul(1f - (t - 1) / 4f);
                Location trail = at.clone().add(p.x, p.y, p.z);
                Stage.fx(trail, Particle.FLAME, 4, 0.15, 0.01);
                Stage.fx(trail, Particle.SMOKE, 2, 0.1, 0.01);
            }
            if (t == 6) {
                Stage.fx(at.clone().add(0, 0.3, 0), Particle.FLAME, 16, 0.8, 0.05);
                Stage.fx(at.clone().add(0, 0.4, 0), Particle.LAVA, 6, 0.6, 0.0);
                Stage.fx(at.clone().add(0, 0.8, 0), Particle.WHITE_ASH, 24, 1.2, 0.0);
                Stage.fx(at.clone().add(0, 0.4, 0), Particle.LARGE_SMOKE, 6, 0.6, 0.02);
                Stage.fx(at.clone().add(0, 0.2, 0), Particle.BLOCK, 16, 0.8, 0.1, 0.8, 0, Stage.floor(at));
                Stage.sound(at, Sound.ENTITY_ARROW_HIT, 1.2f, 0.5f);
                Stage.sound(at, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.8f);
                Stage.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
                if (boss != null && boss.isValid() && power > 0) {
                    hitCircle(boss, at, SCORCH, power, 0.0);
                }
                fire = stage.block(at, Material.CAMPFIRE, Stage.cube(Stage.v(0, 0.2, 0), Stage.yaw(t), 0.01f, 0.01f, 0.01f), null, 15);
                Stage.push(fire, Stage.cube(Stage.v(0, 0.3, 0), Stage.yaw(0.7), 1.1f, 0.6f, 1.1f), 3);
            }
            if (t > 6 && t < 40 && t % 4 == 0) {
                Stage.fx(at.clone().add(0, 0.8, 0), Particle.CAMPFIRE_COSY_SMOKE, 1, 0.1, 0.01);
            }
            if (t == 40) {
                Stage.push(arrow, Stage.model(new Vector3f(from).normalize().mul(-0.6f), q, 3.4f), 12);
                Stage.push(fire, Stage.gone(Stage.v(0, 0.1, 0)), 10);
                for (BlockDisplay d : scorch) {
                    Stage.push(d, Stage.gone(Stage.v(0, 0.02, 0)), 14);
                }
                Stage.fx(at.clone().add(0, 0.6, 0), Particle.LARGE_SMOKE, 4, 0.3, 0.02);
            }
            return t >= 55;
        }

        @Override
        public void end() {
            stage.remove(arrow);
            stage.remove(fire);
            for (BlockDisplay d : scorch) {
                stage.remove(d);
            }
        }
    }

    /* ================================================================== ARROW_SHOT */

    @Override
    public void shot(Location from, Vector direction, Entity projectile) {
        Stage.fx(from, Particle.SMALL_FLAME, 6, 0.12, 0.02);
        Stage.fx(from, Particle.WHITE_ASH, 6, 0.2, 0.0);
        if (projectile == null) {
            return;
        }
        stage.play(t -> {
            if (!projectile.isValid() || projectile.isOnGround() || t > 40) {
                if (projectile.isValid()) {
                    Stage.fx(projectile.getLocation(), Particle.LARGE_SMOKE, 2, 0.1, 0.01);
                }
                return true;
            }
            Location p = projectile.getLocation();
            Stage.fx(p, Particle.SMALL_FLAME, 1, 0.02, 0.0);
            if (t % 2 == 0) {
                Stage.fx(p, Particle.SMOKE, 1, 0.02, 0.0);
            }
            return false;
        });
    }

    @Override
    public void meteor(Location ground) {
        Vector3f from = Stage.v(2, 16, -2);
        stage.play(new Volley(Stage.ground(ground), from, List.of(), 0, instance.getEntity()));
    }

    @Override
    public void slam(Location centre, double radius) {
        Location floor = Stage.ground(centre);
        Stage.ring(floor, radius, 0.6, Particle.SMALL_FLAME, null, 0.2);
        Stage.fx(floor.clone().add(0, 0.6, 0), Particle.WHITE_ASH, 30, radius * 0.4, 0.0);
        Stage.sound(floor, Sound.ENTITY_SKELETON_HURT, 1.0f, 0.5f);
    }

    /* ================================================================== the deserter's pyre (phase change) */

    private final List<Display[]> ring = new ArrayList<>();
    private Location ringAt;

    @Override
    public void transitionStart(Location at) {
        clearRing();
        ringAt = Stage.ground(at);
        Stage.sound(ringAt, Sound.ENTITY_WITHER_SKELETON_HURT, 1.0f, 0.55f);
        Stage.sound(ringAt, Sound.ENTITY_SKELETON_SHOOT, 1.4f, 0.4f);
    }

    @Override
    public void transitionTick(Location at, int tick, int duration) {
        if (ringAt == null) {
            ringAt = Stage.ground(at);
        }
        double u = tick / (double) Math.max(1, duration);
        int n = 10;
        int every = Math.max(2, (int) (duration * 0.45 / n));
        if (tick % every == 0 && ring.size() < n) {
            dropRingArrow(ring.size(), n, duration - tick);
        }
        double r = 6.2 - 3.6 * u;
        if (tick % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = tick * 0.4 + i * Math.PI * 2 / 3;
                double y = (tick % 30) * 0.12;
                Stage.fx(ringAt.clone().add(Math.cos(a) * 1.3, y, Math.sin(a) * 1.3), Particle.SMALL_FLAME, 1, 0.03, 0.01);
            }
            Stage.fx(ringAt.clone().add(0, 1.8, 0), Particle.WHITE_ASH, 6, 1.0, 0.0);
            Stage.dustRing(ringAt, r, CHAR, 2.0f, 0.6);
        }
        if (tick % 12 == 0) {
            Stage.sound(ringAt, Sound.BLOCK_FIRE_AMBIENT, 1.2f, 0.6f + (float) u);
        }
    }

    /** One arrow of the ring: falls, sticks, lights; then the ring creeps inward until the blast. */
    private void dropRingArrow(int index, int n, int left) {
        double a = Math.PI * 2 * index / n;
        Vector3f stand = Stage.v(Math.cos(a) * 6.2, 0, Math.sin(a) * 6.2);
        Vector3f close = Stage.v(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
        Vector3f sky = new Vector3f(stand).add(0, 14, 0);
        Quaternionf q = arrowAlong(Stage.v(-Math.cos(a) * 0.25, -1, -Math.sin(a) * 0.25));
        ItemDisplay arrow = stage.item(ringAt, Material.ARROW, Stage.model(sky, q, 2.6f), EMBER, 15);
        BlockDisplay fire = stage.block(ringAt, Material.CAMPFIRE, Stage.cube(stand, Stage.yaw(a), 0.01f, 0.01f, 0.01f), null, 15);
        ring.add(new Display[]{arrow, fire});
        int creep = Math.max(6, left - 6);
        stage.play(t -> {
            if (t == 2) {
                Stage.push(arrow, Stage.model(new Vector3f(stand).add(0, 0.9f, 0), q, 2.6f), 3);
            }
            if (t == 5) {
                Stage.push(fire, Stage.cube(new Vector3f(stand).add(0, 0.3f, 0), Stage.yaw(a), 1.0f, 0.6f, 1.0f), 3);
                Location spot = ringAt.clone().add(stand.x, 0.3, stand.z);
                Stage.fx(spot, Particle.FLAME, 8, 0.3, 0.03);
                Stage.sound(spot, Sound.ENTITY_ARROW_HIT, 1.0f, 0.5f);
            }
            if (t == 7) {
                Stage.push(arrow, Stage.model(new Vector3f(close).add(0, 0.9f, 0), q, 2.6f), creep);
                Stage.push(fire, Stage.cube(new Vector3f(close).add(0, 0.3f, 0), Stage.yaw(a), 1.0f, 0.6f, 1.0f), creep);
            }
            if (t > 5 && t % 6 == 0) {
                Stage.fx(ringAt.clone().add(stand.x, 1.6, stand.z), Particle.CAMPFIRE_SIGNAL_SMOKE, 1, 0.05, 0.02);
            }
            return t >= 7 + creep || !Stage.alive(arrow);
        });
    }

    @Override
    public void transitionBlast(Location centre, double radius) {
        Location floor = ringAt != null ? ringAt : Stage.ground(centre);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Display[] pair : ring) {
            if (Stage.alive(pair[0])) {
                Location p = pair[0].getLocation();
                Stage.fx(p.clone().add(0, 0.6, 0), Particle.LAVA, 3, 0.2, 0.0);
            }
            stage.remove(pair[0]);
            stage.remove(pair[1]);
        }
        ring.clear();
        double reach = Math.max(2.0, radius);
        stage.play(t -> {
            double r = 1.0 + t * (reach / 8.0);
            Stage.ring(floor, r, 0.7, Particle.LARGE_SMOKE, null, 0.3);
            Stage.ring(floor, r, 1.0, Particle.FLAME, null, 0.2);
            return t >= 8;
        });
        Stage.fx(floor.clone().add(0, 1.2, 0), Particle.LAVA, 24, 1.4, 0.0);
        Stage.fx(floor.clone().add(0, 1.4, 0), Particle.WHITE_ASH, 60, 2.4, 0.0);
        for (int i = 0; i < 8; i++) {
            ItemDisplay bone = stage.item(floor, Material.BONE, null, null, -1);
            double a = rng.nextDouble(Math.PI * 2);
            stage.toss(bone, false, 0.8f, Stage.v(0, 1.2, 0), Stage.v(Math.cos(a) * 0.35, 0.4, Math.sin(a) * 0.35)).rest = 20;
        }
        Stage.sound(floor, Sound.ENTITY_BLAZE_DEATH, 0.8f, 0.8f);
        Stage.sound(floor, Sound.ITEM_FIRECHARGE_USE, 1.1f, 0.5f);
        Stage.sound(floor, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f);
    }

    @Override
    public void transitionEnd(Location at) {
        clearRing();
        ringAt = null;
    }

    private void clearRing() {
        for (Display[] pair : ring) {
            stage.remove(pair[0]);
            stage.remove(pair[1]);
        }
        ring.clear();
    }

    /* ================================================================== death: the ash keeps the bones */

    private Location deathAt;
    private ItemDisplay insignia;

    @Override
    protected void deathStart(Location core, LivingEntity body) {
        deathAt = Stage.ground(body != null ? body.getLocation() : core);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // his quiver empties: the arrows fly out and stick around him, burning
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4 + rng.nextDouble(-0.3, 0.3);
            Vector3f land = Stage.v(Math.cos(a) * 2.6, 0.7, Math.sin(a) * 2.6);
            Quaternionf q = arrowAlong(Stage.v(Math.cos(a) * 0.4, -1, Math.sin(a) * 0.4));
            ItemDisplay arrow = stage.item(deathAt, Material.ARROW, Stage.model(Stage.offset(deathAt, core), q, 1.6f), EMBER, 15);
            stage.play(t -> {
                if (t == 2) {
                    Stage.push(arrow, Stage.model(land, q, 1.6f), 8);
                }
                if (t > 10 && t % 5 == 0) {
                    Stage.fx(deathAt.clone().add(land.x, 1.3, land.z), Particle.SMALL_FLAME, 2, 0.05, 0.01);
                }
                if (t == 100) {
                    Stage.push(arrow, Stage.gone(land), 10);
                }
                return t >= 112;
            });
        }
    }

    @Override
    protected void deathTick(Location core, LivingEntity body, int tick, int duration, double startScale) {
        if (deathAt == null) {
            return;
        }
        boolean alive = body != null && body.isValid() && !body.isDead();
        if (tick < 88) {
            if (tick % 2 == 0) {
                Stage.fx(core, Particle.SMALL_FLAME, 3, 0.4, 0.01);
                Stage.fx(core, Particle.WHITE_ASH, 6, 0.8, 0.0);
            }
            if (tick % 6 == 0) {
                Stage.sound(core, Sound.BLOCK_FIRE_AMBIENT, 0.9f, 0.6f);
            }
        }
        if (tick == 56) {
            Stage.sound(core, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.5f);
            Location chest = core.clone();
            insignia = stage.item(deathAt, Material.SUNFLOWER,
                    Stage.model(Stage.offset(deathAt, chest).add(0, 0.2f, 0), Stage.yaw(0), 0.01f), SUN, 15);
            Stage.push(insignia, Stage.model(Stage.offset(deathAt, chest).add(0, 0.2f, 0), Stage.yaw(0), 0.9f), 6);
        }
        if (tick > 62 && tick < 88 && Stage.alive(insignia)) {
            double u = (tick - 62) / 26.0;
            Vector3f p = Stage.offset(deathAt, core).mul((float) (1 - u)).add(0, (float) (0.15 + 0.2 * (1 - u)), 0);
            Stage.push(insignia, Stage.model(p, Stage.rot(tick * 0.12, u * Math.PI / 2, 0), 0.9f), 2);
        }
        if (tick == 88) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < 10; i++) {
                ItemDisplay bone = stage.item(deathAt, Material.BONE, null, null, -1);
                double a = rng.nextDouble(Math.PI * 2);
                stage.toss(bone, false, 0.9f, Stage.offset(deathAt, core),
                        Stage.v(Math.cos(a) * rng.nextDouble(0.15, 0.35), rng.nextDouble(0.2, 0.45), Math.sin(a) * rng.nextDouble(0.15, 0.35))).rest = 18;
            }
            for (int i = 0; i < 5; i++) {
                BlockDisplay chunk = stage.block(deathAt, Material.BONE_BLOCK, null, null, -1);
                double a = rng.nextDouble(Math.PI * 2);
                stage.toss(chunk, true, 0.4f, Stage.offset(deathAt, core),
                        Stage.v(Math.cos(a) * 0.25, 0.3, Math.sin(a) * 0.25)).rest = 16;
            }
            Stage.fx(core, Particle.WHITE_ASH, 60, 1.4, 0.0);
            Stage.fx(core, Particle.LARGE_SMOKE, 20, 0.8, 0.04);
            Stage.fx(core.clone().add(0, 0.5, 0), Particle.CAMPFIRE_COSY_SMOKE, 6, 0.6, 0.02);
            Stage.sound(core, Sound.ENTITY_SKELETON_DEATH, 1.2f, 0.5f);
            Stage.sound(core, Sound.BLOCK_BONE_BLOCK_BREAK, 1.3f, 0.6f);
            if (alive) {
                AttributeUtil.setBase(body, AttributeUtil.scale(), 0.35);
            }
        }
        if (tick > 88 && tick % 4 == 0) {
            Stage.fx(deathAt.clone().add(0, 0.6, 0), Particle.CAMPFIRE_COSY_SMOKE, 1, 0.4, 0.01);
            if (Stage.alive(insignia) && tick % 8 == 0) {
                Stage.fx(deathAt.clone().add(0, 0.3, 0), Particle.WAX_ON, 2, 0.3, 0.0);
            }
        }
        if (alive && tick < 88) {
            double u = Math.min(1.0, tick / 88.0);
            AttributeUtil.setBase(body, AttributeUtil.scale(), Math.max(0.35, startScale * (1.0 - u * 0.4)));
        }
        if (tick == duration - 10) {
            Stage.push(insignia, Stage.gone(Stage.v(0, 0.1, 0)), 8);
        }
        if (tick == 2) {
            Stage.dust(core, SOOT, 2.0f, 10, 0.6);
        }
    }
}
