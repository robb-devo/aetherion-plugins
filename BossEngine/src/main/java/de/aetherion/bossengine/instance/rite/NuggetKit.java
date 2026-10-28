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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * McNugget, "Terror of the Coop". A farmyard disaster, not a fire elemental: he hatches out of a giant egg,
 * wears a tiny crown, lobs eggs the size of a cow that land as fried eggs, and panics in a tornado of his own
 * feathers. Straw, shell, yolk and feathers; no flames anywhere.
 */
final class NuggetKit extends RiteKit {

    private static final Color YOLK = Color.fromRGB(255, 212, 50);
    private static final Color GOLDEN = Color.fromRGB(255, 190, 40);
    private static final Color STRAW = Color.fromRGB(226, 196, 98);
    private static final double SPLAT = 3.0;

    private final List<Egg> eggs = new ArrayList<>();
    private final List<List<BlockDisplay>> nests = new ArrayList<>();
    private boolean crowned;
    private double fullScale = -1;

    NuggetKit(BossInstance instance) {
        super(instance);
    }

    /* ================================================================== brain */

    @Override
    protected double contactReach() {
        return 2.4;
    }

    /** A chicken body has no melee goal: he lunges and pecks with the template's attack damage. */
    @Override
    protected void contact(LivingEntity boss, Player target) {
        Vector to = target.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (to.lengthSquared() > 0.01) {
            boss.setVelocity(to.normalize().multiply(0.35).setY(0.18));
        }
        // Yellow "!": the peck lands half a second after the lunge, on the white beat (BossGuard).
        instance.guard().melee(target, instance.getAttackDamage());
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1.1f, 0.5f);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.7f);
    }

    /* ================================================================== look */

    @Override
    protected void dress(LivingEntity boss) {
        if (crowned) {
            return;
        }
        crowned = true;
        Location at = boss.getLocation();
        wear(stage.item(at, Material.GOLDEN_HELMET, null, null, 15),
                Stage.v(0, 0.7, 0.18), Stage.rot(0, -0.1, 0.22), 0.3f, false);
    }

    @Override
    protected void ambient(LivingEntity boss, int age) {
        Location feet = boss.getLocation();
        if (age % 14 == 0) {
            Stage.fx(feet.clone().add(0, boss.getHeight() * 0.6, 0), Particle.ITEM, 1, 0.4, 0.02, new ItemStack(Material.FEATHER));
        }
        if (age % 8 == 0 && boss.getVelocity().lengthSquared() > 0.002) {
            Location floor = Stage.ground(feet);
            Stage.fx(floor.clone().add(0, 0.1, 0), Particle.BLOCK, 4, 0.5, 0.02, 0.5, 0, Stage.floor(floor));
        }
    }

    /* ================================================================== arrival: the egg hatches */

    private final List<BlockDisplay> shell = new ArrayList<>();
    private final List<BlockDisplay> nest = new ArrayList<>();

    /** Layers of the egg: centre height and width, two turned boxes each. */
    private static final double[][] EGG = {
            {0.3, 1.5}, {0.85, 2.1}, {1.45, 2.25}, {2.05, 2.0}, {2.6, 1.45}, {3.0, 0.75}
    };

    @Override
    protected int arrivalTicks() {
        return 64;
    }

    @Override
    protected void arrivalStart(LivingEntity boss, Location at) {
        fullScale = AttributeUtil.getBase(boss, AttributeUtil.scale(), 2.8);
        AttributeUtil.setBase(boss, AttributeUtil.scale(), 0.3);
        Location floor = Stage.ground(at);
        for (int i = 0; i < 10; i++) {
            nest.add(stage.block(floor, Material.HAY_BLOCK, null, null, -1));
        }
        for (int i = 0; i < EGG.length * 2; i++) {
            shell.add(stage.block(floor, Material.END_STONE, null, null, 14));
        }
        Stage.sound(floor, Sound.ENTITY_CHICKEN_AMBIENT, 1.4f, 0.5f);
        Stage.sound(floor, Sound.BLOCK_GRASS_PLACE, 1.0f, 0.6f);
    }

    @Override
    protected void arrivalTick(LivingEntity boss, Location at, int t) {
        Location floor = Stage.ground(at);
        if (t == 2) {
            for (int i = 0; i < nest.size(); i++) {
                double a = Math.PI * 2 * i / nest.size();
                Stage.push(nest.get(i), Stage.cube(Stage.v(Math.cos(a) * 1.6, 0.18, Math.sin(a) * 1.6),
                        Stage.rot(-a, 0.25, 0.15), 1.1f, 0.36f, 0.7f), 8);
            }
            egg(0.0, 0.0, 12);
            Stage.sound(floor, Sound.BLOCK_SNIFFER_EGG_PLOP, 1.4f, 0.6f);
        }
        if (t >= 16 && t < 44 && t % 2 == 0) {
            double strength = (t - 16) / 28.0;
            double wobble = Math.sin(t * 0.9) * (0.06 + strength * 0.16);
            egg(wobble, Math.cos(t * 0.7) * strength * 0.08, 2);
        }
        if (t == 22 || t == 30 || t == 38) {
            Stage.sound(floor, Sound.BLOCK_SNIFFER_EGG_CRACK, 1.3f, 0.7f + t * 0.01f);
            Stage.fx(floor.clone().add(0, 1.6, 0), Particle.BLOCK, 10, 0.6, 0.6, 0.6, 0, Material.END_STONE.createBlockData());
            Stage.fx(floor.clone().add(0, 2.2, 0), Particle.CLOUD, 3, 0.4, 0.02);
        }
        if (t == 44) {
            hatch(floor);
            Stage.sound(floor, Sound.BLOCK_SNIFFER_EGG_HATCH, 1.5f, 0.8f);
            Stage.sound(floor, Sound.ENTITY_CHICKEN_EGG, 1.4f, 0.5f);
            Stage.fx(floor.clone().add(0, 1.4, 0), Particle.CLOUD, 30, 1.0, 0.08);
            Stage.fx(floor.clone().add(0, 1.6, 0), Particle.ITEM, 30, 0.8, 0.18, new ItemStack(Material.FEATHER));
        }
        if (t >= 44 && t <= 56 && fullScale > 0) {
            double u = (t - 44) / 12.0;
            // grows past full size and settles back: a pop, not a fade
            double s = 0.3 + (fullScale - 0.3) * (Stage.ease(u) + Math.sin(u * Math.PI) * 0.18);
            AttributeUtil.setBase(boss, AttributeUtil.scale(), s);
        }
        if (t == 57) {
            AttributeUtil.setBase(boss, AttributeUtil.scale(), fullScale);
            Stage.sound(at, Sound.ENTITY_CHICKEN_HURT, 1.6f, 0.45f);
            Stage.sound(at, Sound.ENTITY_CHICKEN_AMBIENT, 1.6f, 0.5f);
        }
        if (t == 50) {
            for (BlockDisplay d : nest) {
                Stage.push(d, Stage.gone(Stage.v(0, 0.1, 0)), 12);
            }
        }
    }

    private void egg(double rollX, double rollZ, int interp) {
        Quaternionf tilt = new Quaternionf().rotateX((float) rollX).rotateZ((float) rollZ);
        for (int i = 0; i < EGG.length; i++) {
            float w = (float) EGG[i][1];
            Vector3f c = tilt.transform(Stage.v(0, EGG[i][0], 0));
            Stage.push(shell.get(i * 2), Stage.cube(c, new Quaternionf(tilt), w, 0.62f, w), interp);
            Stage.push(shell.get(i * 2 + 1), Stage.cube(c, new Quaternionf(tilt).rotateY((float) (Math.PI / 4)), w, 0.62f, w), interp);
        }
    }

    private void hatch(Location floor) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < shell.size(); i++) {
            BlockDisplay d = shell.get(i);
            double y = EGG[i / 2][0];
            double a = rng.nextDouble(Math.PI * 2);
            float w = (float) (EGG[i / 2][1] * 0.45);
            Stage.Toss toss = stage.toss(d, true, w, Stage.v(Math.cos(a) * 0.4, y, Math.sin(a) * 0.4),
                    Stage.v(Math.cos(a) * rng.nextDouble(0.2, 0.36), rng.nextDouble(0.25, 0.45), Math.sin(a) * rng.nextDouble(0.2, 0.36)));
            toss.rest = 18;
        }
        shell.clear();
    }

    @Override
    protected void arrivalEnd(LivingEntity boss, Location at) {
        for (BlockDisplay d : nest) {
            stage.remove(d);
        }
        nest.clear();
        titleCard(boss, "&6&lMcNugget", "Terror of the Coop", "Schrecken des Hühnerstalls");
    }

    /* ================================================================== signature: egg bombardment */

    @Override
    protected int cooldown(boolean late) {
        return late ? 200 : 280;
    }

    @Override
    protected int begin(LivingEntity boss, List<Player> near, boolean late) {
        Location at = boss.getLocation();
        clearNests();
        eggs.clear();
        List<Player> far = new ArrayList<>(near);
        far.sort(Comparator.comparingDouble((Player p) -> p.getLocation().distanceSquared(at)).reversed());
        int count = Math.min(late ? 3 : 1, far.size());
        Location beak = at.clone().add(0, boss.getHeight() * 0.8, 0);
        for (int i = 0; i < count; i++) {
            Location land = Stage.ground(far.get(i).getLocation());
            boolean golden = late && i == count - 1;
            layNest(land);
            Egg egg = new Egg(beak, land, golden, 24);
            eggs.add(egg);
            stage.play(egg);
        }
        Stage.sound(at, Sound.ENTITY_CHICKEN_EGG, 1.4f, 0.5f);
        Stage.sound(at, Sound.ENTITY_CHICKEN_AMBIENT, 1.2f, 0.6f);
        cue(near, "&eEgg Barrage &7· get out of the nest", "&eEierhagel &7· raus aus dem Nest");
        return 26;
    }

    /** A ring of straw where an egg will come down. */
    private void layNest(Location land) {
        List<BlockDisplay> ring = new ArrayList<>();
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vector3f c = Stage.v(Math.cos(a) * SPLAT, 0.05, Math.sin(a) * SPLAT);
            BlockDisplay d = stage.block(land, Material.HAY_BLOCK, Stage.cube(c, Stage.yaw(-a), 0.01f, 0.01f, 0.01f), null, 13);
            ring.add(d);
            double yaw = -a;
            int delay = 1 + i % 3;
            stage.play(t -> {
                if (t == delay) {
                    Stage.push(d, Stage.cube(c, Stage.yaw(yaw).rotateZ(0.2f), 0.4f, 0.1f, 1.5f), 6);
                }
                return t >= delay;
            });
        }
        nests.add(ring);
    }

    private void clearNests() {
        for (List<BlockDisplay> ring : nests) {
            for (BlockDisplay d : ring) {
                stage.remove(d);
            }
        }
        nests.clear();
    }

    @Override
    protected void charge(LivingEntity boss, int left, boolean late) {
        if (left % 6 == 0) {
            Stage.sound(boss.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 0.8f, 0.8f + (26 - left) * 0.03f);
        }
    }

    @Override
    protected void flash(LivingEntity boss) {
        for (List<BlockDisplay> ring : nests) {
            for (BlockDisplay d : ring) {
                if (Stage.alive(d)) {
                    d.setBlock(Material.WHITE_WOOL.createBlockData());
                }
            }
        }
        Stage.sound(boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.9f);
    }

    @Override
    protected void strike(LivingEntity boss, boolean late) {
        for (Egg egg : eggs) {
            splat(egg.land, egg.golden);
            hitCircle(boss, egg.land, SPLAT, late ? 22 : 18, 0.75);
            egg.done = true;
        }
        eggs.clear();
        clearNests();
    }

    @Override
    protected void cancelMove() {
        for (Egg egg : eggs) {
            egg.done = true;
        }
        eggs.clear();
        clearNests();
    }

    /** A cow-sized egg on a high lob; lands exactly when the windup runs out. */
    private final class Egg implements Stage.Anim {
        final Location from;
        final Location land;
        final boolean golden;
        final int flight;
        ItemDisplay d;
        boolean done;

        Egg(Location from, Location land, boolean golden, int flight) {
            this.from = from.clone();
            this.land = land.clone();
            this.golden = golden;
            this.flight = flight;
        }

        @Override
        public boolean tick(int t) {
            if (done) {
                return true;
            }
            if (t == 1) {
                d = stage.item(from, Material.EGG, Stage.model(Stage.v(0, 0, 0), new Quaternionf(), 0.4f),
                        golden ? GOLDEN : null, 15);
                return false;
            }
            double u = Math.min(1.0, (t - 1) / (double) flight);
            Vector3f to = Stage.offset(from, land);
            double apex = 7.0 + to.length() * 0.15;
            Vector3f p = new Vector3f(to).mul((float) u).add(0, (float) (4 * apex * u * (1 - u)), 0);
            Stage.push(d, Stage.model(p, Stage.rot(t * 0.3, t * 0.45, 0), golden ? 3.2f : 2.4f), 1);
            return u >= 1.0 && t > flight + 6;
        }

        @Override
        public void end() {
            stage.remove(d);
        }
    }

    /** Where an egg lands it lands as a fried egg, and stays a moment. */
    private void splat(Location at, boolean golden) {
        BlockDisplay white1 = stage.block(at, Material.WHITE_CONCRETE, null, null, 15);
        BlockDisplay white2 = stage.block(at, Material.WHITE_CONCRETE, null, null, 15);
        BlockDisplay yolk = stage.block(at, golden ? Material.GOLD_BLOCK : Material.YELLOW_CONCRETE, null, null, 15);
        double yaw = ThreadLocalRandom.current().nextDouble(Math.PI);
        stage.play(t -> {
            if (t == 1) {
                Stage.push(white1, Stage.cube(Stage.v(0, 0.02, 0), Stage.yaw(yaw), 4.2f, 0.03f, 3.4f), 3);
                Stage.push(white2, Stage.cube(Stage.v(0.3, 0.025, -0.2), Stage.yaw(yaw + 0.9), 3.2f, 0.03f, 2.8f), 3);
                Stage.push(yolk, Stage.cube(Stage.v(0.1, 0.12, 0.1), Stage.yaw(yaw + 0.4), 1.3f, 0.22f, 1.3f), 3);
            } else if (t == 50) {
                for (BlockDisplay d : List.of(white1, white2, yolk)) {
                    Stage.push(d, Stage.gone(Stage.v(0, 0.02, 0)), 12);
                }
            } else if (t >= 63) {
                stage.remove(white1);
                stage.remove(white2);
                stage.remove(yolk);
                return true;
            }
            return false;
        });
        Stage.fx(at.clone().add(0, 0.5, 0), Particle.ITEM, 18, 0.8, 0.2, new ItemStack(Material.EGG));
        Stage.fx(at.clone().add(0, 0.4, 0), Particle.CLOUD, 12, 1.2, 0.06);
        Stage.fx(at.clone().add(0, 0.8, 0), Particle.ITEM, 8, 1.0, 0.12, new ItemStack(Material.FEATHER));
        Stage.dustRing(at, SPLAT, YOLK, 1.4f, 0.6);
        Stage.sound(at, Sound.BLOCK_SNIFFER_EGG_CRACK, 1.5f, 0.55f);
        Stage.sound(at, Sound.ENTITY_CHICKEN_HURT, 1.0f, 0.6f);
        Stage.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        if (golden) {
            Stage.fx(at.clone().add(0, 1.0, 0), Particle.WAX_ON, 30, 1.6, 0.0);
            Stage.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 1.4f);
        }
    }

    /* ================================================================== EGG_SHOT + LEAP */

    @Override
    public void shot(Location from, Vector direction, Entity projectile) {
        Stage.fx(from, Particle.CLOUD, 4, 0.15, 0.02);
        Stage.fx(from, Particle.ITEM, 3, 0.2, 0.08, new ItemStack(Material.FEATHER));
        if (projectile == null || !projectile.isValid()) {
            return;
        }
        ItemDisplay d = stage.item(projectile.getLocation(), Material.EGG,
                Stage.model(Stage.v(0, 0.1, 0), new Quaternionf(), 1.4f), null, 15);
        if (d == null) {
            return;
        }
        projectile.addPassenger(d);
        stage.play(t -> {
            if (!projectile.isValid() || t > 100) {
                Location at = Stage.alive(d) ? d.getLocation() : projectile.getLocation();
                Stage.fx(at, Particle.ITEM, 10, 0.3, 0.12, new ItemStack(Material.EGG));
                Stage.fx(at, Particle.CLOUD, 3, 0.2, 0.02);
                stage.remove(d);
                return true;
            }
            Stage.push(d, Stage.model(Stage.v(0, 0.1, 0), Stage.rot(t * 0.5, t * 0.7, 0), 1.4f), 1);
            return false;
        });
    }

    @Override
    public void leapOff(Location feet) {
        Location floor = Stage.ground(feet);
        Stage.fx(floor.clone().add(0, 0.3, 0), Particle.ITEM, 12, 0.6, 0.15, new ItemStack(Material.FEATHER));
        Stage.fx(floor.clone().add(0, 0.2, 0), Particle.BLOCK, 12, 0.6, 0.05, 0.6, 0, Material.HAY_BLOCK.createBlockData());
        Stage.fx(floor.clone().add(0, 0.2, 0), Particle.CLOUD, 6, 0.6, 0.03);
        Stage.sound(floor, Sound.ENTITY_CHICKEN_HURT, 1.0f, 0.7f);
    }

    @Override
    public void slam(Location centre, double radius) {
        Location floor = Stage.ground(centre);
        Stage.fx(floor.clone().add(0, 0.3, 0), Particle.ITEM, 24, radius * 0.4, 0.12, new ItemStack(Material.FEATHER));
        Stage.ring(floor, radius, 0.6, Particle.CLOUD, null, 0.2);
        Stage.sound(floor, Sound.ENTITY_CHICKEN_HURT, 1.2f, 0.5f);
    }

    @Override
    public void meteor(Location ground) {
        splat(ground, false);
    }

    /* ================================================================== panic: a tornado of feathers */

    private final List<Display> panic = new ArrayList<>();

    @Override
    public void transitionStart(Location at) {
        Location floor = Stage.ground(at);
        clearPanic();
        Stage.sound(floor, Sound.ENTITY_CHICKEN_HURT, 1.6f, 1.4f);
        Stage.sound(floor, Sound.ENTITY_CHICKEN_HURT, 1.6f, 0.6f);
        Stage.fx(floor.clone().add(0, 1.5, 0), Particle.ITEM, 40, 1.0, 0.2, new ItemStack(Material.FEATHER));
        for (int i = 0; i < 18; i++) {
            panic.add(stage.item(floor, Material.FEATHER, null, null, 15));
        }
        for (int i = 0; i < 6; i++) {
            panic.add(stage.block(floor, Material.HAY_BLOCK, null, null, -1));
        }
    }

    @Override
    public void transitionTick(Location at, int tick, int duration) {
        double u = tick / (double) Math.max(1, duration);
        Location floor = Stage.ground(at);
        Location anchor = floor.clone();
        anchor.setYaw(0f);
        anchor.setPitch(0f);
        for (int i = 0; i < panic.size(); i++) {
            Display d = panic.get(i);
            if (!Stage.alive(d)) {
                continue;
            }
            Stage.move(d, anchor, 2);
            if (i < 18) {
                double a = tick * (0.25 + u * 0.35) + i * Math.PI * 2 / 18 * 2.3;
                double y = 0.4 + ((i * 0.37 + tick * 0.08) % 4.2);
                double r = 1.5 + y * 0.45;
                Stage.push(d, Stage.model(Stage.v(Math.cos(a) * r, y, Math.sin(a) * r),
                        Stage.rot(-a, tick * 0.3 + i, tick * 0.2), 0.9f), 2);
            } else {
                int k = i - 18;
                double a = Math.PI * 2 * k / 6 + 0.4;
                // whack-a-mole: one bale up at a time, round and round
                boolean up = ((tick / 5) % 6) == k;
                Vector3f base = Stage.v(Math.cos(a) * 4.0, up ? 0 : -1.1, Math.sin(a) * 4.0);
                Stage.push(d, Stage.post(base, Stage.yaw(-a), 1.0f, 1.0f, 1.0f), 3);
                if (up && tick % 5 == 0) {
                    Location spot = floor.clone().add(Math.cos(a) * 4.0, 0.5, Math.sin(a) * 4.0);
                    Stage.fx(spot, Particle.BLOCK, 8, 0.4, 0.2, 0.4, 0, Material.HAY_BLOCK.createBlockData());
                    Stage.sound(spot, Sound.BLOCK_GRASS_BREAK, 0.8f, 0.8f);
                }
            }
        }
        int beep = u < 0.5 ? 10 : u < 0.8 ? 6 : 3;
        if (tick % beep == 0) {
            Stage.sound(floor, Sound.BLOCK_NOTE_BLOCK_BIT, 0.9f, 0.8f + (float) u * 1.1f);
        }
        if (tick % 9 == 0) {
            Stage.sound(floor, Sound.ENTITY_CHICKEN_HURT, 0.9f, 0.9f + (float) u * 0.6f);
        }
    }

    @Override
    public void transitionEnd(Location at) {
        Location floor = Stage.ground(at);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Display d : panic) {
            if (d instanceof ItemDisplay feather && Stage.alive(feather)) {
                double a = rng.nextDouble(Math.PI * 2);
                Stage.Toss toss = stage.toss(feather, false, 0.9f, Stage.v(Math.cos(a) * 1.5, 2.0, Math.sin(a) * 1.5),
                        Stage.v(Math.cos(a) * 0.45, 0.25, Math.sin(a) * 0.45));
                toss.gravity = 0.02;
                toss.drag = 0.9;
                toss.rest = 12;
                toss.spin = 0.25f;
            } else {
                if (Stage.alive(d)) {
                    Stage.fx(d.getLocation().add(0, 0.5, 0), Particle.BLOCK, 12, 0.5, 0.3, 0.5, 0, Material.HAY_BLOCK.createBlockData());
                }
                stage.remove(d);
            }
        }
        panic.clear();
        Stage.sound(floor, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f, 1.9f);
        Stage.sound(floor, Sound.ENTITY_CHICKEN_AMBIENT, 1.6f, 0.5f);
        Stage.fx(floor.clone().add(0, 1.2, 0), Particle.CLOUD, 24, 1.4, 0.06);
    }

    private void clearPanic() {
        for (Display d : panic) {
            stage.remove(d);
        }
        panic.clear();
    }

    /* ================================================================== death: pop */

    private ItemDisplay deathCrown;
    private Location deathAt;

    @Override
    protected void deathStart(Location core, LivingEntity body) {
        deathAt = Stage.ground(body != null ? body.getLocation() : core);
        double h = body != null ? body.getHeight() : 2.0;
        deathCrown = stage.item(deathAt, Material.GOLDEN_HELMET,
                Stage.model(Stage.v(0, h + 0.2, 0.4), Stage.rot(0, -0.1, 0.22), 0.85f), null, 15);
    }

    @Override
    protected void deathTick(Location core, LivingEntity body, int tick, int duration, double startScale) {
        if (deathAt == null) {
            return;
        }
        boolean alive = body != null && body.isValid() && !body.isDead();
        if (tick < 82) {
            double inflate = startScale * (1.0 + Math.min(1.15, tick / 55.0));
            if (alive) {
                AttributeUtil.setBase(body, AttributeUtil.scale(), inflate + Math.sin(tick * 0.9) * 0.08 * (tick / 82.0));
            }
            if (Stage.alive(deathCrown) && alive) {
                Stage.push(deathCrown, Stage.model(Stage.v(0, body.getHeight() + 0.2, 0.4),
                        Stage.rot(0, -0.1, 0.22 + Math.sin(tick * 0.5) * 0.15), 0.85f), 2);
            }
            if (tick % 3 == 0) {
                Stage.fx(core, Particle.ITEM, 2, 0.6, 0.08, new ItemStack(Material.FEATHER));
            }
            if (tick % 8 == 0) {
                Stage.sound(core, Sound.ENTITY_CHICKEN_AMBIENT, 0.9f, 0.55f + tick * 0.008f);
            }
            int gap = tick < 50 ? 12 : tick < 70 ? 6 : 3;
            if (tick % gap == 0) {
                Stage.sound(core, Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 1.2f + tick * 0.008f);
            }
        }
        if (tick == 82) {
            Stage.sound(core, Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 1.9f);
            Stage.sound(core, Sound.ENTITY_CHICKEN_DEATH, 1.4f, 0.6f);
            Stage.sound(core, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
            Stage.fx(core, Particle.CLOUD, 40, 1.2, 0.08);
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < 30; i++) {
                ItemDisplay f = stage.item(deathAt, Material.FEATHER, null, null, 15);
                double a = rng.nextDouble(Math.PI * 2);
                double up = rng.nextDouble(0.2, 0.6);
                Stage.Toss toss = stage.toss(f, false, 0.8f, Stage.offset(deathAt, core),
                        Stage.v(Math.cos(a) * rng.nextDouble(0.25, 0.55), up, Math.sin(a) * rng.nextDouble(0.25, 0.55)));
                toss.gravity = 0.018;
                toss.drag = 0.92;
                toss.spin = 0.2f;
                toss.rest = 6;
            }
            if (alive) {
                AttributeUtil.setBase(body, AttributeUtil.scale(), 0.35);
            }
            if (Stage.alive(deathCrown)) {
                // the crown is all that comes down
                Stage.push(deathCrown, Stage.model(Stage.v(0, 3.4, 0), Stage.rot(1.2, 0.4, 0.6), 0.85f), 6);
            }
        }
        if (tick == 88 && Stage.alive(deathCrown)) {
            Stage.push(deathCrown, Stage.model(Stage.v(0, 0.22, 0), Stage.rot(2.0, 0.2, 0.35), 0.85f), 9);
        }
        if (tick == 97) {
            Stage.sound(deathAt, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 1.6f);
            Stage.fx(deathAt.clone().add(0, 0.3, 0), Particle.WAX_ON, 8, 0.4, 0.0);
        }
    }
}
