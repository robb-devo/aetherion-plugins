package de.aetherion.bossengine.instance.rite;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bridge Troll, the rich one, "Keeper of the Toll". Everything is money: he leaks coins when he walks, swings a
 * fan of gold, rolls a giant coin down the road at you and in the storm rides a vortex of his own fortune.
 * Gold, chain and anvil; nothing glows blue, nothing burns.
 */
final class TrollKit extends RiteKit {

    private static final Color GOLD = Color.fromRGB(255, 204, 64);
    private static final Color AMBER = Color.fromRGB(214, 150, 40);
    private static final double SWEEP_HIT = 3.4;

    private final List<Display> coinsOnFloor = new ArrayList<>();
    private final List<BlockDisplay> fan = new ArrayList<>();
    private final List<ItemDisplay> ring = new ArrayList<>();
    private Location lastCoin;
    private boolean sack;
    private int casts;
    private Move move = Move.NONE;
    private float sweepYaw;
    private Location rollFrom;
    private Vector rollDir;
    private Coin coin;
    private Vortex vortex;
    private SlamRing slamRing;

    private enum Move { NONE, SWEEP, SPIN, ROLL }

    TrollKit(BossInstance instance) {
        super(instance);
    }

    /* ================================================================== look */

    @Override
    protected void dress(LivingEntity boss) {
        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            eq.setHelmet(rich(Material.GOLDEN_HELMET, TrimPattern.SNOUT));
            eq.setChestplate(rich(Material.GOLDEN_CHESTPLATE, TrimPattern.SNOUT));
            eq.setBoots(rich(Material.GOLDEN_BOOTS, TrimPattern.RIB));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setBootsDropChance(0f);
        }
        if (!sack) {
            sack = true;
            Location at = boss.getLocation();
            Follow bag = wear(stage.block(at, Material.BROWN_WOOL, null, null, -1),
                    Stage.v(0, 1.02, -0.36), Stage.rot(0, -0.2, 0), 1f, true);
            if (bag != null) {
                bag.sx = 0.5f;
                bag.sy = 0.56f;
                bag.sz = 0.36f;
            }
            Follow knot = wear(stage.block(at, Material.CHAIN, null, null, -1),
                    Stage.v(0, 1.36, -0.34), Stage.rot(0, 0, Math.PI / 2), 1f, true);
            if (knot != null) {
                knot.sx = 0.5f;
                knot.sy = 0.5f;
                knot.sz = 0.5f;
            }
            wear(stage.item(at, Material.GOLD_NUGGET, null, null, 15),
                    Stage.v(0.08, 1.4, -0.38), Stage.rot(0.4, 0, 0.6), 0.32f, false);
            wear(stage.item(at, Material.GOLD_INGOT, null, null, 15),
                    Stage.v(-0.1, 1.38, -0.36), Stage.rot(-0.5, 0.2, -0.4), 0.3f, false);
        }
    }

    private static ItemStack rich(Material material, TrimPattern pattern) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof ArmorMeta meta) {
            meta.setTrim(new ArmorTrim(TrimMaterial.EMERALD, pattern));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    protected void ambient(LivingEntity boss, int age) {
        Location feet = boss.getLocation();
        if (age % 6 == 0) {
            Stage.fx(feet.clone().add(0, boss.getHeight() * 0.6, 0), Particle.WAX_ON, 1, 0.5, 0.0);
        }
        if (age % 12 == 0 && (lastCoin == null || lastCoin.getWorld() != feet.getWorld()
                || lastCoin.distanceSquared(feet) > 1.0)) {
            lastCoin = feet.clone();
            leakCoin(boss);
        }
    }

    /** He leaks money where he walks: a coin drops off the sack, bounces, lies there a while. */
    private void leakCoin(LivingEntity boss) {
        coinsOnFloor.removeIf(d -> !Stage.alive(d));
        if (coinsOnFloor.size() > 9) {
            return;
        }
        Location floor = Stage.ground(boss.getLocation());
        Location hip = bodyPoint(boss.getLocation(), 0.2, 0, -0.4);
        Vector3f start = Stage.offset(floor, hip).add(0, (float) (boss.getHeight() * 0.45), 0);
        ItemDisplay d = stage.item(floor, Material.GOLD_NUGGET, null, null, 15);
        coinsOnFloor.add(d);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Stage.Toss toss = stage.toss(d, false, 0.5f, start,
                Stage.v(rng.nextDouble(-0.08, 0.08), 0.12, rng.nextDouble(-0.08, 0.08)));
        toss.rest = 70;
        toss.onLand = () -> Stage.sound(floor, Sound.ITEM_ARMOR_EQUIP_GOLD, 0.35f, 1.7f);
    }

    /* ================================================================== arrival: it rains money, then him */

    private final List<Display> pile = new ArrayList<>();

    @Override
    protected int arrivalTicks() {
        return 62;
    }

    @Override
    protected void arrivalStart(LivingEntity boss, Location at) {
        place(boss, at.clone().add(0, 26, 0));
        Location floor = Stage.ground(at);
        pile.add(stage.block(floor, Material.GOLD_BLOCK, null, null, 15));
        pile.add(stage.block(floor, Material.RAW_GOLD_BLOCK, null, null, 15));
        pile.add(stage.block(floor, Material.GOLD_BLOCK, null, null, 15));
        Stage.sound(floor, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);
    }

    @Override
    protected void arrivalTick(LivingEntity boss, Location at, int t) {
        Location floor = Stage.ground(at);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (t <= 38) {
            for (int i = 0; i < 2; i++) {
                double a = rng.nextDouble(Math.PI * 2);
                double r = rng.nextDouble(0, 1.8);
                Vector3f land = Stage.v(Math.cos(a) * r, 0, Math.sin(a) * r);
                Vector3f sky = new Vector3f(land).add(0, (float) rng.nextDouble(9, 13), 0);
                ItemDisplay coin = stage.item(floor, rng.nextInt(5) == 0 ? Material.GOLD_INGOT : Material.GOLD_NUGGET,
                        Stage.model(sky, Stage.rot(a, 0, 0), 0.55f), null, 15);
                pile.add(coin);
                double yaw = a;
                stage.play(k -> {
                    if (k == 2) {
                        Stage.push(coin, Stage.model(land, Stage.rot(yaw, Math.PI / 2, 0), 0.55f), 7);
                    }
                    if (k == 9 && rng.nextInt(3) == 0) {
                        Stage.sound(floor, Sound.ITEM_ARMOR_EQUIP_GOLD, 0.4f, 1.5f + (float) rng.nextDouble(0.4));
                    }
                    return k >= 9;
                });
            }
            if (t % 4 == 0) {
                Stage.fx(floor.clone().add(0, 0.3, 0), Particle.WAX_ON, 3, 1.2, 0.0);
            }
        }
        if (t == 4) {
            Stage.push(pile.get(0), Stage.cube(Stage.v(0, 0.2, 0), Stage.yaw(Math.PI / 4), 2.4f, 0.4f, 2.4f), 32);
        }
        if (t == 10) {
            Stage.push(pile.get(1), Stage.cube(Stage.v(0, 0.45, 0), Stage.yaw(0.3), 1.5f, 0.7f, 1.5f), 26);
        }
        if (t == 16) {
            Stage.push(pile.get(2), Stage.cube(Stage.v(0.1, 0.8, -0.1), Stage.yaw(1.1), 0.8f, 0.5f, 0.8f), 22);
        }
        if (t == 40) {
            Stage.sound(floor, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.4f, 0.5f);
        }
        if (t >= 40 && t <= 47) {
            double u = Stage.easeIn((t - 40) / 7.0);
            place(boss, at.clone().add(0, 26 * (1.0 - u), 0));
        }
        if (t == 48) {
            place(boss, at);
            Stage.sound(floor, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
            Stage.sound(floor, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
            Stage.sound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.7f);
            for (Display d : pile) {
                stage.remove(d);
            }
            pile.clear();
            coinBurst(floor, 26, 0.32, 0.5);
            Stage.fx(floor.clone().add(0, 0.4, 0), Particle.BLOCK, 50, 1.4, 0.2, 1.4, 0, Material.GOLD_BLOCK.createBlockData());
            Stage.fx(floor.clone().add(0, 0.2, 0), Particle.DUST_PILLAR, 40, 2.0, 0.1, 2.0, 0, Stage.floor(floor));
        }
    }

    @Override
    protected void arrivalEnd(LivingEntity boss, Location at) {
        titleCard(boss, "&6&lBridge Troll", "Keeper of the Toll", "Hüter des Zolls");
        Stage.sound(at, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);
    }

    /** Coins flung out in a ring from {@code floor}. */
    private void coinBurst(Location floor, int count, double speed, double lift) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + rng.nextDouble(-0.1, 0.1);
            double s = speed * rng.nextDouble(0.7, 1.3);
            ItemDisplay d = stage.item(floor, rng.nextInt(4) == 0 ? Material.GOLD_INGOT : Material.GOLD_NUGGET, null, null, 15);
            Stage.Toss toss = stage.toss(d, false, 0.55f, Stage.v(0, 0.5, 0),
                    Stage.v(Math.cos(a) * s, lift * rng.nextDouble(0.7, 1.2), Math.sin(a) * s));
            toss.rest = 26;
        }
    }

    /* ================================================================== signature: sweep / spin / the rolling coin */

    @Override
    protected int cooldown(boolean late) {
        return late ? 200 : 260;
    }

    @Override
    protected int begin(LivingEntity boss, List<Player> near, boolean late) {
        Location at = boss.getLocation();
        casts++;
        if (casts % 3 == 0) {
            return beginRoll(boss, near, late);
        }
        Player target = near.stream()
                .min(Comparator.comparingDouble((Player p) -> p.getLocation().distanceSquared(at))).orElse(near.get(0));
        Vector to = target.getLocation().toVector().subtract(at.toVector()).setY(0);
        if (to.lengthSquared() < 0.01) {
            to = at.getDirection().setY(0);
        }
        sweepYaw = (float) Math.atan2(to.getZ(), to.getX());
        Stage.sound(at, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.3f, 0.5f);
        Stage.sound(at, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);
        if (late) {
            move = Move.SPIN;
            layRing(Stage.ground(at), 5.0, GOLD);
            cue(near, "&6Gold Rush &7· get out of his reach", "&6Goldrausch &7· raus aus seiner Reichweite");
            return 18;
        }
        move = Move.SWEEP;
        layFan(Stage.ground(at));
        cue(near, "&6Toll Sweep &7· don't stand in front of him", "&6Zollhieb &7· nicht vor ihm stehen");
        return 22;
    }

    @Override
    protected void charge(LivingEntity boss, int left, boolean late) {
        if (left % 5 == 0) {
            Stage.fx(boss.getLocation().add(0, boss.getHeight() * 0.7, 0), Particle.WAX_ON, 4, 0.6, 0.0);
        }
        if (move == Move.ROLL && coin != null && left % 6 == 0) {
            Stage.sound(rollFrom, Sound.BLOCK_GRINDSTONE_USE, 0.5f, 0.6f);
        }
    }

    @Override
    protected void flash(LivingEntity boss) {
        for (BlockDisplay d : fan) {
            if (Stage.alive(d)) {
                d.setBlock(Material.WHITE_CONCRETE.createBlockData());
            }
        }
        for (ItemDisplay d : ring) {
            Stage.glow(d, WHITE);
        }
        if (rollStrip != null && Stage.alive(rollStrip)) {
            rollStrip.setBlock(Material.WHITE_CONCRETE.createBlockData());
        }
        Stage.sound(boss.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.9f);
    }

    @Override
    protected void strike(LivingEntity boss, boolean late) {
        switch (move) {
            case SWEEP -> sweep(boss);
            case SPIN -> spin(boss, true);
            case ROLL -> roll(boss);
            default -> {
            }
        }
        move = Move.NONE;
    }

    @Override
    protected void cancelMove() {
        clearTells();
        if (coin != null) {
            coin.done = true;
            coin = null;
        }
        move = Move.NONE;
    }

    private void clearTells() {
        for (BlockDisplay d : fan) {
            stage.remove(d);
        }
        fan.clear();
        for (ItemDisplay d : ring) {
            stage.remove(d);
        }
        ring.clear();
        if (rollStrip != null) {
            stage.remove(rollStrip);
            rollStrip = null;
        }
    }

    private Vector front() {
        return new Vector(Math.cos(sweepYaw), 0, Math.sin(sweepYaw));
    }

    /** Seven gold planks fanned out in front of him: the toll gate he is about to swing through. */
    private void layFan(Location floor) {
        clearTells();
        for (int i = 0; i < 7; i++) {
            double a = sweepYaw + Math.toRadians(-54 + i * 18);
            Quaternionf q = Stage.yaw(-a + Math.PI / 2);
            Matrix4f start = new Matrix4f().translation(0f, 0.03f, 0f).rotate(q).scale(0.3f, 0.03f, 0.2f).translate(-0.5f, -0.5f, 0f);
            Matrix4f full = new Matrix4f().translation(0f, 0.03f, 0f).rotate(q).scale(0.3f, 0.03f, 6.2f).translate(-0.5f, -0.5f, 0f);
            BlockDisplay d = stage.block(floor, Material.GOLD_BLOCK, start, null, 15);
            fan.add(d);
            int delay = 1 + Math.abs(i - 3);
            stage.play(t -> {
                if (t == delay) {
                    Stage.push(d, full, 8);
                }
                return t >= delay;
            });
        }
    }

    /** A ring of coins laid flat around him, turning slowly. */
    private void layRing(Location floor, double radius, Color glow) {
        clearTells();
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vector3f p = Stage.v(Math.cos(a) * radius, 0.06, Math.sin(a) * radius);
            ItemDisplay d = stage.item(floor, Material.GOLD_NUGGET, Stage.model(p, Stage.rot(a, Math.PI / 2, 0), 0.01f), glow, 15);
            ring.add(d);
            double base = a;
            stage.play(t -> {
                if (!Stage.alive(d)) {
                    return true;
                }
                if (t == 2) {
                    Stage.push(d, Stage.model(p, Stage.rot(base, Math.PI / 2, 0), 0.75f), 5);
                } else if (t > 6 && t % 3 == 0) {
                    Stage.push(d, Stage.model(p, Stage.rot(base + t * 0.25, Math.PI / 2, 0), 0.75f), 3);
                }
                return t > 60;
            });
        }
    }

    private void sweep(LivingEntity boss) {
        Location at = boss.getLocation();
        Location floor = Stage.ground(at);
        Vector front = front();
        clearTells();
        // the swing: a fan of coins off the axe, sweep arcs through the cone
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Vector3f hand = Stage.v(front.getX() * 0.8, boss.getHeight() * 0.55, front.getZ() * 0.8);
        for (int i = 0; i < 14; i++) {
            double a = sweepYaw + Math.toRadians(-60 + i * (120.0 / 13));
            double s = rng.nextDouble(0.34, 0.48);
            ItemDisplay d = stage.item(floor, i % 4 == 0 ? Material.GOLD_INGOT : Material.GOLD_NUGGET, null, null, 15);
            Stage.Toss toss = stage.toss(d, false, 0.6f, hand, Stage.v(Math.cos(a) * s, 0.22, Math.sin(a) * s));
            toss.rest = 34;
        }
        for (int i = 0; i < 5; i++) {
            double a = sweepYaw + Math.toRadians(-50 + i * 25);
            Location p = at.clone().add(Math.cos(a) * 2.6, boss.getHeight() * 0.45, Math.sin(a) * 2.6);
            Stage.fx(p, Particle.SWEEP_ATTACK, 1, 0, 0);
        }
        Location hit = floor.clone().add(front.clone().multiply(3.0));
        Stage.fx(hit.clone().add(0, 0.2, 0), Particle.BLOCK, 26, 1.6, 0.1, 1.6, 0, Stage.floor(hit));
        Stage.fx(hit.clone().add(0, 0.6, 0), Particle.ITEM, 16, 1.2, 0.18, new ItemStack(Material.GOLD_NUGGET));
        Stage.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
        Stage.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.8f);
        Stage.sound(at, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.7f);
        for (Player p : near(boss, 7.5)) {
            if (p.getLocation().distanceSquared(hit) <= SWEEP_HIT * SWEEP_HIT) {
                toll(boss, p, 22);
            }
        }
    }

    /** Gold Rush: the full circle of coins, then a second swing half a beat later. */
    private void spin(LivingEntity boss, boolean first) {
        Location at = boss.getLocation();
        Location floor = Stage.ground(at);
        clearTells();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Vector3f hand = Stage.v(0, boss.getHeight() * 0.5, 0);
        for (int i = 0; i < 22; i++) {
            double a = Math.PI * 2 * i / 22 + rng.nextDouble(-0.08, 0.08);
            double s = rng.nextDouble(0.36, 0.5);
            ItemDisplay d = stage.item(floor, i % 5 == 0 ? Material.GOLD_INGOT : Material.GOLD_NUGGET, null, null, 15);
            stage.toss(d, false, 0.6f, hand, Stage.v(Math.cos(a) * s, 0.2, Math.sin(a) * s)).rest = 30;
        }
        for (int i = 0; i < 6; i++) {
            double a = sweepYaw + i * Math.PI / 3;
            Stage.fx(at.clone().add(Math.cos(a) * 2.4, boss.getHeight() * 0.45, Math.sin(a) * 2.4), Particle.SWEEP_ATTACK, 1, 0, 0);
        }
        Stage.fx(floor.clone().add(0, 0.2, 0), Particle.DUST_PILLAR, 40, 2.6, 0.1, 2.6, 0, Stage.floor(floor));
        Stage.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
        Stage.sound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.5f);
        Stage.sound(at, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.6f);
        for (Player p : near(boss, 5.0)) {
            toll(boss, p, 26);
        }
        if (!first) {
            return;
        }
        layRing(floor, 5.0, WHITE);
        stage.play(t -> {
            LivingEntity body = instance.getEntity();
            if (body == null || !body.isValid() || instance.isTransitioning() || !instance.isAlive()) {
                clearTells();
                return true;
            }
            if (t >= 12) {
                spin(body, false);
                return true;
            }
            return false;
        });
    }

    private void toll(LivingEntity boss, Player p, double power) {
        Vector push = p.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (push.lengthSquared() > 0.01) {
            p.setVelocity(push.normalize().multiply(1.2).setY(0.45));
        }
        BossHits.hurt(p, boss, instance.scaleDamage(power));
    }

    /* ------------------------------------------------------------------ the rolling coin */

    private static final double ROLL_LENGTH = 18.0;
    private static final double COIN_R = 1.4;
    private BlockDisplay rollStrip;

    private int beginRoll(LivingEntity boss, List<Player> near, boolean late) {
        move = Move.ROLL;
        Location at = boss.getLocation();
        Player target = near.get(ThreadLocalRandom.current().nextInt(near.size()));
        Vector dir = target.getLocation().toVector().subtract(at.toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = at.getDirection().setY(0);
        }
        rollDir = dir.normalize();
        rollFrom = Stage.ground(at.clone().add(rollDir.clone().multiply(1.6)));
        clearTells();
        double yaw = Math.atan2(rollDir.getZ(), rollDir.getX());
        Quaternionf q = Stage.yaw(-yaw + Math.PI / 2);
        rollStrip = stage.block(rollFrom, Material.GOLD_BLOCK,
                new Matrix4f().translation(0f, 0.03f, 0f).rotate(q).scale(2.4f, 0.03f, 0.3f).translate(-0.5f, -0.5f, 0f), null, 13);
        BlockDisplay strip = rollStrip;
        stage.play(t -> {
            if (t == 2) {
                Stage.push(strip, new Matrix4f().translation(0f, 0.03f, 0f).rotate(q)
                        .scale(2.4f, 0.03f, (float) ROLL_LENGTH).translate(-0.5f, -0.5f, 0f), 12);
            }
            return t >= 2;
        });
        coin = new Coin(rollFrom, rollDir);
        stage.play(coin);
        Stage.sound(at, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.4f, 0.5f);
        Stage.sound(at, Sound.ENTITY_PIGLIN_BRUTE_AMBIENT, 1.2f, 0.5f);
        cue(near, "&6Pay the Toll &7· step off the gold road", "&6Zoll fällig &7· runter von der Goldstraße");
        return late ? 20 : 24;
    }

    private void roll(LivingEntity boss) {
        if (rollStrip != null) {
            BlockDisplay strip = rollStrip;
            rollStrip = null;
            stage.play(t -> {
                if (t == 26) {
                    Stage.push(strip, Stage.gone(Stage.v(0, 0.03, 0)), 10);
                }
                if (t >= 37) {
                    stage.remove(strip);
                    return true;
                }
                return false;
            });
        }
        if (coin != null) {
            coin.go(boss);
            coin = null;
        }
        Stage.sound(rollFrom, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
    }

    /** A giant gold coin: four turned plates and an emblem. Stands up during the tell, rolls on go(). */
    private final class Coin implements Stage.Anim {
        final Location from;
        final Vector dir;
        final Quaternionf face;
        final List<BlockDisplay> plates = new ArrayList<>();
        final Set<UUID> hit = new HashSet<>();
        LivingEntity roller;
        int goAt = -1;
        int t;
        boolean done;

        Coin(Location from, Vector dir) {
            this.from = from.clone();
            this.dir = dir.clone();
            this.face = Stage.yaw(Math.atan2(-dir.getZ(), dir.getX()));
        }

        void go(LivingEntity boss) {
            roller = boss;
            goAt = t;
        }

        @Override
        public boolean tick(int tick) {
            t = tick;
            if (done) {
                return true;
            }
            if (tick == 1) {
                for (int i = 0; i < 4; i++) {
                    plates.add(stage.block(from, Material.GOLD_BLOCK, null, null, 15));
                }
                plates.add(stage.block(from, Material.RAW_GOLD_BLOCK, null, null, 15));
                return false;
            }
            if (goAt < 0) {
                double rise = Math.min(1.0, (tick - 1) / 10.0);
                pose(0, Stage.ease(rise), tick <= 2 ? 0 : 2);
                return tick > 80;
            }
            int s = tick - goAt;
            double dist = s * 0.62;
            if (dist <= ROLL_LENGTH) {
                pose(dist, 1.0, 1);
                Location centre = from.clone().add(dir.clone().multiply(dist));
                if (s % 2 == 0) {
                    Stage.fx(centre.clone().add(0, 0.15, 0), Particle.BLOCK, 6, 0.4, 0.05, 0.4, 0, Stage.floor(centre));
                    Stage.fx(centre.clone().add(0, 0.4, 0), Particle.WAX_ON, 2, 0.4, 0.0);
                }
                if (s % 4 == 0) {
                    Stage.sound(centre, Sound.BLOCK_GRINDSTONE_USE, 0.8f, 0.5f);
                }
                if (roller != null) {
                    hitAlong(centre);
                }
                return false;
            }
            int after = s - (int) Math.ceil(ROLL_LENGTH / 0.62);
            if (after == 1) {
                Stage.sound(from.clone().add(dir.clone().multiply(ROLL_LENGTH)), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.9f);
                topple(8);
            } else if (after == 22) {
                for (BlockDisplay d : plates) {
                    Stage.push(d, Stage.gone(Stage.offset(from, from.clone().add(dir.clone().multiply(ROLL_LENGTH)))), 10);
                }
            }
            return after >= 34;
        }

        private void pose(double dist, double rise, int interp) {
            Vector3f c = Stage.v(dir.getX() * dist, COIN_R * rise, dir.getZ() * dist);
            float roll = (float) (-dist / COIN_R);
            float size = (float) (COIN_R * 2 * Math.max(0.05, rise));
            for (int i = 0; i < 4; i++) {
                Quaternionf q = new Quaternionf(face).rotateZ(roll + (float) (i * Math.PI / 8));
                Stage.push(plates.get(i), Stage.cube(c, q, size, size, 0.3f), interp);
            }
            Quaternionf e = new Quaternionf(face).rotateZ(roll + (float) (Math.PI / 4));
            Stage.push(plates.get(4), Stage.cube(c, e, size * 0.42f, size * 0.42f, 0.38f), interp);
        }

        private void topple(int interp) {
            Vector3f c = Stage.v(dir.getX() * ROLL_LENGTH, 0.17, dir.getZ() * ROLL_LENGTH);
            for (int i = 0; i < 4; i++) {
                Quaternionf q = new Quaternionf(face).rotateX((float) (Math.PI / 2)).rotateZ((float) (i * Math.PI / 8));
                Stage.push(plates.get(i), Stage.cube(c, q, (float) (COIN_R * 2), (float) (COIN_R * 2), 0.3f), interp);
            }
            Quaternionf e = new Quaternionf(face).rotateX((float) (Math.PI / 2)).rotateZ((float) (Math.PI / 4));
            Stage.push(plates.get(4), Stage.cube(c, e, (float) (COIN_R * 0.84), (float) (COIN_R * 0.84), 0.38f), interp);
        }

        private void hitAlong(Location centre) {
            for (Player p : near(roller, ROLL_LENGTH + 6)) {
                if (hit.contains(p.getUniqueId())) {
                    continue;
                }
                Location l = p.getLocation();
                double dx = l.getX() - centre.getX();
                double dz = l.getZ() - centre.getZ();
                if (dx * dx + dz * dz > (COIN_R + 0.3) * (COIN_R + 0.3) || Math.abs(l.getY() - centre.getY()) > 3.0) {
                    continue;
                }
                hit.add(p.getUniqueId());
                Vector side = new Vector(-dir.getZ(), 0, dir.getX());
                if (side.dot(new Vector(dx, 0, dz)) < 0) {
                    side.multiply(-1);
                }
                p.setVelocity(side.multiply(1.1).add(dir.clone().multiply(0.5)).setY(0.5));
                BossHits.hurt(p, roller, instance.scaleDamage(22));
                Stage.sound(l, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.6f);
            }
        }

        @Override
        public void end() {
            for (BlockDisplay d : plates) {
                stage.remove(d);
            }
            plates.clear();
        }
    }

    /* ================================================================== SLAM_LEAP */

    @Override
    public void leapOff(Location feet) {
        Location floor = Stage.ground(feet);
        Stage.fx(floor.clone().add(0, 0.2, 0), Particle.BLOCK, 18, 0.8, 0.1, 0.8, 0, Stage.floor(floor));
        coinBurst(floor, 6, 0.18, 0.35);
        Stage.sound(floor, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 0.8f);
    }

    @Override
    public void slamTell(Location centre, double radius) {
        if (slamRing == null || slamRing.dead) {
            slamRing = new SlamRing(Stage.ground(centre), radius);
            stage.play(slamRing);
        } else {
            slamRing.retarget(Stage.ground(centre));
        }
    }

    @Override
    public void slam(Location centre, double radius) {
        Location floor = Stage.ground(centre);
        if (slamRing != null) {
            slamRing.dead = true;
            slamRing = null;
        }
        coinBurst(floor, 16, 0.3, 0.55);
        Stage.fx(floor.clone().add(0, 0.2, 0), Particle.DUST_PILLAR, 50, radius * 0.5, 0.1, radius * 0.5, 0, Stage.floor(floor));
        Stage.fx(floor.clone().add(0, 0.5, 0), Particle.BLOCK, 30, 1.2, 0.2, 1.2, 0, Material.GOLD_BLOCK.createBlockData());
        Stage.dustRing(floor, radius, GOLD, 1.6f, 0.5);
        Stage.sound(floor, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        Stage.sound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.7f);
    }

    private final class SlamRing implements Stage.Anim {
        Location floor;
        final double radius;
        final List<ItemDisplay> coins = new ArrayList<>();
        boolean dead;

        SlamRing(Location floor, double radius) {
            this.floor = floor.clone();
            this.radius = radius;
        }

        void retarget(Location to) {
            floor = to.clone();
        }

        @Override
        public boolean tick(int t) {
            if (t == 1) {
                for (int i = 0; i < 14; i++) {
                    coins.add(stage.item(floor, Material.GOLD_NUGGET, null, null, 15));
                }
            }
            for (int i = 0; i < coins.size(); i++) {
                double a = Math.PI * 2 * i / coins.size() + t * 0.08;
                Location p = floor.clone().add(Math.cos(a) * radius, 0.06, Math.sin(a) * radius);
                Stage.move(coins.get(i), p, 2);
                Stage.push(coins.get(i), Stage.model(Stage.v(0, 0, 0), Stage.rot(a, Math.PI / 2, 0), 0.8f), 2);
            }
            return dead || t > 90;
        }

        @Override
        public void end() {
            for (ItemDisplay d : coins) {
                if (Stage.alive(d)) {
                    Location at = d.getLocation();
                    Stage.fx(at.clone().add(0, 0.3, 0), Particle.ITEM, 3, 0.1, 0.15, new ItemStack(Material.GOLD_NUGGET));
                }
                stage.remove(d);
            }
            coins.clear();
            dead = true;
        }
    }

    /* ================================================================== storm: a vortex of his own fortune */

    @Override
    public void transitionStart(Location at) {
        Location floor = Stage.ground(at);
        Stage.sound(floor, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.5f, 0.45f);
        Stage.sound(floor, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.4f, 0.5f);
        coinBurst(floor, 18, 0.22, 0.7);
        if (vortex != null) {
            vortex.dead = true;
        }
        vortex = new Vortex();
        stage.play(vortex);
    }

    @Override
    public void stormWalk(Location feet, int tick) {
        if (tick % 3 == 0) {
            Location floor = Stage.ground(feet);
            Stage.fx(floor.clone().add(0, 0.2, 0), Particle.BLOCK, 6, 0.5, 0.05, 0.5, 0, Stage.floor(floor));
            Stage.fx(floor.clone().add(0, 0.5, 0), Particle.ITEM, 2, 0.3, 0.12, new ItemStack(Material.GOLD_NUGGET));
        }
    }

    @Override
    public void transitionTick(Location at, int tick, int duration) {
        if (vortex != null) {
            vortex.progress = tick / (double) Math.max(1, duration);
        }
        if (tick % 10 == 0) {
            Stage.sound(at, Sound.BLOCK_CHAIN_PLACE, 0.5f, 0.5f + (float) (tick / (double) duration) * 0.4f);
        }
    }

    @Override
    public void stormStrike(Location at) {
        Location top = at.clone().add(0, 16, 0);
        for (int i = 0; i < 9; i++) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Vector3f sky = Stage.v(rng.nextDouble(-0.5, 0.5), 16 + i * 1.2, rng.nextDouble(-0.5, 0.5));
            ItemDisplay d = stage.item(at, Material.GOLD_INGOT, Stage.model(sky, Stage.rot(rng.nextDouble(6), 0.3, 0.8), 0.7f), GOLD, 15);
            int delay = i;
            stage.play(t -> {
                if (t == 2 + delay / 3) {
                    Stage.push(d, Stage.model(Stage.v(0, 1.0, 0), Stage.rot(1.0 + delay, 1.2, 0.4), 0.7f), 4);
                }
                if (t >= 7 + delay / 3) {
                    stage.remove(d);
                    return true;
                }
                return false;
            });
        }
        Stage.fx(at.clone().add(0, 1.0, 0), Particle.FLASH, 1, 0, 0);
        Stage.fx(at.clone().add(0, 1.0, 0), Particle.WAX_ON, 30, 1.2, 0.0);
        Stage.fx(at.clone().add(0, 1.0, 0), Particle.ITEM, 20, 0.8, 0.2, new ItemStack(Material.GOLD_NUGGET));
        Stage.sound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.4f);
        Stage.sound(at, Sound.BLOCK_ANVIL_LAND, 0.9f, 1.2f);
        Stage.sound(top, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.5f);
    }

    @Override
    public void transitionEnd(Location at) {
        if (vortex != null) {
            vortex.scatter();
            vortex = null;
        }
    }

    @Override
    public void transitionBlast(Location centre, double radius) {
        Location floor = Stage.ground(centre);
        coinBurst(floor, 30, 0.55, 0.45);
        Stage.sound(floor, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.4f);
        Stage.sound(floor, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
        Stage.sound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.5f);
        double reach = Math.min(radius, 16.0);
        stage.play(t -> {
            double r = 1.5 + t * (reach / 10.0);
            Stage.ring(floor, r, 1.0, Particle.BLOCK, Stage.floor(floor), 0.2);
            Stage.dustRing(floor, r, t < 4 ? WHITE : GOLD, 1.8f, 1.1);
            return t >= 10;
        });
    }

    /** Two tilted rings of coins and a crown of ingots orbiting him for the whole storm. */
    private final class Vortex implements Stage.Anim {
        final List<ItemDisplay> coins = new ArrayList<>();
        double progress;
        boolean dead;

        @Override
        public boolean tick(int t) {
            LivingEntity boss = instance.getEntity();
            if (dead || boss == null || !boss.isValid()) {
                return true;
            }
            Location feet = boss.getLocation();
            Location anchor = feet.clone();
            anchor.setYaw(0f);
            anchor.setPitch(0f);
            if (t == 1) {
                for (int i = 0; i < 30; i++) {
                    coins.add(stage.item(anchor, i < 24 ? Material.GOLD_NUGGET : Material.GOLD_INGOT, null, null, 15));
                }
            }
            double hover = progress > 0.38 && progress < 0.82 ? 1.0 : progress >= 0.82 ? 0.3 : 0.6;
            double h = boss.getHeight();
            for (int i = 0; i < coins.size(); i++) {
                ItemDisplay d = coins.get(i);
                double a;
                double r;
                double y;
                double tilt;
                if (i < 12) {
                    a = t * 0.22 + i * Math.PI * 2 / 12;
                    r = 2.3 + hover * 0.9;
                    y = h * 0.35;
                    tilt = 0.35;
                } else if (i < 24) {
                    a = -t * 0.18 + i * Math.PI * 2 / 12;
                    r = 3.0 + hover * 1.1;
                    y = h * 0.7;
                    tilt = -0.3;
                } else {
                    a = t * 0.1 + i * Math.PI * 2 / 6;
                    r = 0.7;
                    y = h + 0.5 + 0.12 * Math.sin(t * 0.3 + i);
                    tilt = 0;
                }
                double cy = y + Math.sin(a) * r * tilt;
                Vector3f p = Stage.v(Math.cos(a) * r, cy, Math.sin(a) * r);
                Stage.move(d, anchor, 2);
                Stage.push(d, Stage.model(p, Stage.rot(-a, 0.3, t * 0.2), i < 24 ? 0.6f : 0.5f), 2);
            }
            if (t % 4 == 0) {
                Stage.fx(feet.clone().add(0, h * 0.5, 0), Particle.WAX_ON, 3, 1.6, 0.0);
            }
            return false;
        }

        void scatter() {
            dead = true;
            LivingEntity boss = instance.getEntity();
            Location floor = Stage.ground(boss != null ? boss.getLocation() : instance.getSpawnLocation());
            coinBurst(floor, 20, 0.4, 0.5);
        }

        @Override
        public void end() {
            for (ItemDisplay d : coins) {
                stage.remove(d);
            }
            coins.clear();
        }
    }

    /* ================================================================== death: buried in his own gold */

    private final List<Display> heap = new ArrayList<>();
    private ItemDisplay crown;
    private Location deathAt;

    @Override
    protected void deathStart(Location core, LivingEntity body) {
        deathAt = Stage.ground(body != null ? body.getLocation() : core);
        if (body != null && body.getEquipment() != null) {
            body.getEquipment().setHelmet(new ItemStack(Material.AIR));
        }
        crown = stage.item(deathAt, rich(Material.GOLDEN_HELMET, TrimPattern.SNOUT),
                Stage.model(Stage.v(0, 3.1, 0), new Quaternionf(), 0.8f), null, 15);
    }

    @Override
    protected void deathTick(Location core, LivingEntity body, int tick, int duration, double startScale) {
        if (deathAt == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (tick < 36) {
            if (tick % 2 == 0) {
                ItemDisplay d = stage.item(deathAt, rng.nextInt(4) == 0 ? Material.GOLD_INGOT : Material.GOLD_NUGGET, null, null, 15);
                double a = rng.nextDouble(Math.PI * 2);
                stage.toss(d, false, 0.55f, Stage.offset(deathAt, core),
                        Stage.v(Math.cos(a) * 0.25, 0.3, Math.sin(a) * 0.25)).rest = 50;
            }
            if (Stage.alive(crown)) {
                Stage.push(crown, Stage.model(Stage.offset(deathAt, core).add(0, 1.5f, 0),
                        Stage.yaw(tick * 0.15), 0.8f), 2);
            }
            if (tick % 6 == 0) {
                Stage.sound(core, Sound.ENTITY_PIGLIN_BRUTE_AMBIENT, 0.6f, 0.7f);
                Stage.sound(core, Sound.ITEM_ARMOR_EQUIP_GOLD, 0.6f, 1.4f);
            }
        }
        if (tick == 38) {
            Stage.sound(deathAt, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.4f);
            Stage.sound(deathAt, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f);
            coinBurst(deathAt, 24, 0.3, 0.5);
            heap.add(stage.block(deathAt, Material.GOLD_BLOCK, null, null, 15));
            heap.add(stage.block(deathAt, Material.RAW_GOLD_BLOCK, null, null, 15));
            heap.add(stage.block(deathAt, Material.GOLD_BLOCK, null, null, 15));
            Stage.fx(deathAt.clone().add(0, 0.4, 0), Particle.DUST_PILLAR, 50, 2.0, 0.1, 2.0, 0, Stage.floor(deathAt));
        }
        if (tick == 39) {
            Stage.push(heap.get(0), Stage.cube(Stage.v(0, 0.3, 0), Stage.yaw(0.6), 3.0f, 0.6f, 3.0f), 6);
            Stage.push(heap.get(1), Stage.cube(Stage.v(0, 0.75, 0), Stage.yaw(-0.2), 2.0f, 0.9f, 2.0f), 8);
            Stage.push(heap.get(2), Stage.cube(Stage.v(0.2, 1.3, 0.1), Stage.yaw(0.9), 1.1f, 0.6f, 1.1f), 10);
            if (Stage.alive(crown)) {
                // the helmet pops off and comes to rest on top of the pile
                Stage.push(crown, Stage.model(Stage.v(0.3, 4.6, 0.2), Stage.rot(0.7, 0.6, 0.3), 0.8f), 8);
            }
        }
        if (tick == 48 && Stage.alive(crown)) {
            Stage.push(crown, Stage.model(Stage.v(0.2, 1.85, 0.1), Stage.rot(1.4, 0.25, 0.15), 0.8f), 8);
        }
        if (tick == 56) {
            Stage.sound(deathAt, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.9f);
        }
        if (tick == duration - 12) {
            for (Display d : heap) {
                Stage.push(d, Stage.gone(Stage.v(0, 0.05, 0)), 10);
            }
            Stage.push(crown, Stage.gone(Stage.v(0, 0.1, 0)), 10);
        }
        if (tick > 38 && tick % 5 == 0) {
            Stage.fx(deathAt.clone().add(0, 1.2, 0), Particle.WAX_ON, 3, 1.2, 0.0);
        }
    }
}
