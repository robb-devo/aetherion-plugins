package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A world Nihil spat back out, splashed across the island: a disc of that world's ground (painted
 * client-side, so nobody can get stuck in it) with a few of its plants standing in it, and that
 * world's rules while you stand inside. Deserts and the Nether burn, snow freezes, the deep dark
 * blinds, the End makes you float, water drags you in, forests cut you when you move.
 *
 * <p>The same disc, painted in the missing-texture checkerboard, is a patch of world with no rules
 * left at all: it simply hurts.
 */
final class Spill {

    enum Kind { HEAT, FROST, DARK, DRIFT, MIRE, THORN, UNMADE }

    interface Hurt {
        boolean hurt(Player player, double power, Vector3f from, String key, int gateTicks);
    }

    static Kind kindOf(Serpent.World w) {
        return switch (w.name()) {
            case "desert", "badlands", "crimson forest", "basalt deltas" -> Kind.HEAT;
            case "snowy plains", "ice spikes" -> Kind.FROST;
            case "deep dark" -> Kind.DARK;
            case "the end", "warped forest" -> Kind.DRIFT;
            case "ocean", "swamp" -> Kind.MIRE;
            default -> Kind.THORN;
        };
    }

    private final WeFx fx;
    final Kind kind;
    final Vector3f center;
    final float radius;
    private final int life;
    private final Color tint;
    private final List<BlockDisplay> plants = new ArrayList<>();
    private final List<Vector3f> plantAt = new ArrayList<>();
    private final Map<UUID, Location> last = new HashMap<>();
    private final String key;
    private int age;

    /**
     * @param floorY world Y of the ground blocks to paint
     */
    Spill(WeFx fx, FakeBlocks paint, Serpent.World world, Kind kind, Vector3f center, float radius, int life, int floorY, String key) {
        this.fx = fx;
        this.kind = kind;
        this.center = new Vector3f(center.x, 0f, center.z);
        this.radius = radius;
        this.life = life;
        this.key = key;
        this.tint = world == null ? Color.fromRGB(255, 0, 220) : world.tint();
        Location c = fx.at(this.center);
        int r = (int) Math.ceil(radius);
        BlockData ground = world == null ? null : world.top().createBlockData();
        BlockData magenta = Material.MAGENTA_CONCRETE.createBlockData();
        BlockData black = Material.BLACK_CONCRETE.createBlockData();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                // A ragged edge: the splash thins out toward the rim.
                if (d > radius || d > radius - 1.4 && rnd.nextFloat() < 0.45f) {
                    continue;
                }
                int x = c.getBlockX() + dx;
                int z = c.getBlockZ() + dz;
                BlockData data = kind == Kind.UNMADE ? (((x + z) & 1) == 0 ? magenta : black) : ground;
                paint.show(x, floorY, z, data, life);
            }
        }
        if (world != null && world.feature() != null && kind != Kind.UNMADE) {
            int n = 3 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble(WeMath.TAU);
                double rr = Math.sqrt(rnd.nextDouble()) * (radius - 1.2);
                Vector3f at = new Vector3f(this.center.x + (float) (Math.sin(a) * rr), 0f, this.center.z + (float) (Math.cos(a) * rr));
                BlockDisplay d = fx.block(world.feature(), null, 15);
                WeFx.push(d, WeFx.box(new Vector3f(at).add(0f, 0.05f, 0f), new Vector3f(0.05f)), 0);
                plants.add(d);
                plantAt.add(at);
            }
        }
    }

    /** @return false once the spill has soaked away */
    boolean tick(List<Player> targets, Hurt hurt) {
        age++;
        if (age == 2) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < plants.size(); i++) {
                float h = 0.9f + rnd.nextFloat() * 0.8f;
                Vector3f at = plantAt.get(i);
                WeFx.push(plants.get(i), WeFx.box(new Vector3f(at).add(0f, h * 0.5f, 0f), new Vector3f(h * 0.8f, h, h * 0.8f),
                        new Quaternionf().rotateY(rnd.nextFloat() * 3f)), 8);
            }
        }
        if (age == life - 12) {
            for (int i = 0; i < plants.size(); i++) {
                WeFx.push(plants.get(i), WeFx.gone(plantAt.get(i)), 12);
            }
        }
        if (age % 6 == 0) {
            ambience();
        }
        if (age % 5 == 0) {
            for (Player p : targets) {
                Vector3f pp = fx.stage(p.getLocation());
                if (WeMath.horizontal(pp, center) > radius || pp.y < -2.5f || pp.y > 3.5f) {
                    last.remove(p.getUniqueId());
                    continue;
                }
                affect(p, pp, hurt);
            }
        }
        return age < life;
    }

    private void affect(Player p, Vector3f pp, Hurt hurt) {
        switch (kind) {
            case HEAT -> {
                p.setFireTicks(Math.max(p.getFireTicks(), 50));
                hurt.hurt(p, 7, center, key, 30);
            }
            case FROST -> {
                p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 60, p.getFreezeTicks() + 26));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 2, false, false, false));
                if (p.getFreezeTicks() >= p.getMaxFreezeTicks()) {
                    hurt.hurt(p, 7, center, key, 30);
                }
            }
            case DARK -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, 0, false, false, false));
                hurt.hurt(p, 7, center, key, 36);
            }
            case DRIFT -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 14, 1, false, false, false));
                hurt.hurt(p, 5, center, key, 40);
            }
            case MIRE -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3, false, false, false));
                Vector3f in = new Vector3f(center).sub(pp);
                in.y = 0f;
                if (in.lengthSquared() > 0.5f) {
                    in.normalize(0.12f);
                    Vector v = p.getVelocity();
                    p.setVelocity(new Vector(v.getX() + in.x, v.getY(), v.getZ() + in.z));
                }
                hurt.hurt(p, 5, center, key, 40);
            }
            case THORN -> {
                Location now = p.getLocation();
                Location before = last.put(p.getUniqueId(), now);
                if (before != null && before.getWorld() == now.getWorld() && before.distanceSquared(now) > 0.04) {
                    p.getWorld().playSound(now, Sound.BLOCK_SWEET_BERRY_BUSH_BREAK, 0.8f, 0.6f);
                    hurt.hurt(p, 6, center, key, 24);
                }
            }
            case UNMADE -> hurt.hurt(p, 8, center, key, 30);
            default -> {
            }
        }
    }

    private void ambience() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(WeMath.TAU);
        double rr = Math.sqrt(r.nextDouble()) * radius;
        Vector3f at = new Vector3f(center.x + (float) (Math.sin(a) * rr), 0.3f, center.z + (float) (Math.cos(a) * rr));
        switch (kind) {
            case HEAT -> fx.particle(Particle.FLAME, at, 3, 0.3, 0.02);
            case FROST -> fx.particle(Particle.SNOWFLAKE, at, 4, 0.4, 0.02);
            case DARK -> fx.particle(Particle.SCULK_SOUL, at, 1, 0.3, 0.02);
            case DRIFT -> fx.particle(Particle.END_ROD, at, 2, 0.3, 0.03);
            case MIRE -> fx.particle(Particle.SPLASH, at, 8, 0.4, 0.1);
            case THORN -> fx.dust(at, tint, 1.2f, 3, 0.4);
            case UNMADE -> fx.dust(at, (r.nextBoolean() ? Color.fromRGB(248, 0, 248) : Color.BLACK), 1.6f, 3, 0.4);
            default -> {
            }
        }
    }

    boolean contains(Vector3f p) {
        return WeMath.horizontal(p, center) <= radius;
    }

    void remove() {
        for (BlockDisplay d : plants) {
            fx.kill(d);
        }
        plants.clear();
    }
}
