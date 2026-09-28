package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hollow Sun Collapse addon — blood-red storm cells copied from StormcallerTempest.
 * Same thunderhead / leader / return-stroke / scar feel; red palette; arena anchors around
 * the boss after STARFALL until death/abort. Does not touch Stormcaller Maul.
 */
public final class HollowBloodstorm {

    private static final int CADENCE = 8;
    private static final int INTRO = 8;
    private static final double CLOUD_HEIGHT = 7.0;
    private static final double RANGE = 10.0;
    private static final int BOLT_TICKS = 6;
    private static final int SCAR_TICKS = 24;
    private static final int CHARGE_TICKS = 8;
    private static final int MAX_CHARGES = 6;
    private static final int MAX_LIVE = 260;
    private static final int CELL_COUNT = 3;
    private static final float CELL_SCALE = 0.55f;
    private static final double STRIKE_POWER = 32.0;
    private static final float[] MAIN_FLICKER = {1.0f, 0.3f, 1.15f, 0.6f, 0.3f, 0.12f};
    private static final float[] FORK_FLICKER = {0.6f, 0.0f, 0.5f};

    private static final Color HALO = Color.fromRGB(255, 70, 40);
    private static final Color PALE = Color.fromRGB(255, 160, 110);
    private static final Color SCAR_GLOW = Color.fromRGB(255, 90, 50);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    /** Thunderhead puffs (Stormcaller layout), scaled down for small arena cells. */
    private static final float[][] PUFFS = {
            {0.0f, 0.0f, 0.0f, 3.8f, 1.0f, 3.4f, 3},
            {1.9f, 0.1f, 0.6f, 2.4f, 0.9f, 2.2f, 3},
            {-1.8f, 0.05f, -0.5f, 2.5f, 0.85f, 2.3f, 3},
            {0.4f, 0.1f, 1.9f, 2.2f, 0.8f, 2.1f, 3},
            {-0.5f, 0.1f, -1.9f, 2.3f, 0.8f, 2.0f, 3},
            {3.1f, 0.3f, -0.9f, 1.6f, 0.7f, 1.5f, 4},
            {-3.0f, 0.3f, 1.1f, 1.7f, 0.7f, 1.4f, 4},
            {0.3f, 0.8f, 0.2f, 2.6f, 0.9f, 2.4f, 5},
            {1.2f, 0.75f, -1.1f, 1.5f, 0.7f, 1.4f, 5},
            {-0.6f, 1.4f, -0.3f, 3.2f, 0.45f, 2.8f, 7}
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final List<Cell> cells = new ArrayList<>();
    private World world;
    private Location center;
    private double floorY;
    private double arenaRadius;
    private LivingEntity boss;
    private boolean active;
    private int age;

    public boolean isActive() {
        return active;
    }

    /** Spawn small red cells scattered around the Collapse arena. Idempotent. */
    public void start(LivingEntity boss, Location center, double floorY, double arenaRadius) {
        clear();
        if (boss == null || !boss.isValid() || center == null || center.getWorld() == null) {
            return;
        }
        this.boss = boss;
        this.world = center.getWorld();
        this.center = center.clone();
        this.floorY = floorY;
        this.arenaRadius = Math.max(6.0, arenaRadius);
        this.active = true;
        this.age = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < CELL_COUNT; i++) {
            double a = (Math.PI * 2 * i / CELL_COUNT) + random.nextDouble(-0.35, 0.35);
            double r = arenaRadius * random.nextDouble(0.28, 0.55);
            Location anchor = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            anchor.setY(floorY);
            cells.add(new Cell(anchor));
        }
    }

    /** Soft re-scatter so cells stay near the boss after folds without a hard follow. */
    public void reanchorNear(LivingEntity entity) {
        if (!active || entity == null || !entity.isValid() || world == null) {
            return;
        }
        Location feet = entity.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Cell cell : cells) {
            double a = random.nextDouble(Math.PI * 2);
            double r = arenaRadius * random.nextDouble(0.22, 0.5);
            Location want = feet.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            want.setY(floorY);
            cell.nudgeToward(want);
        }
    }

    public void tick(LivingEntity entity, List<Player> fighters) {
        if (!active || world == null) {
            return;
        }
        if (entity != null && entity.isValid()) {
            boss = entity;
        }
        age++;
        if (age % 100 == 0 && boss != null && boss.isValid()) {
            reanchorNear(boss);
        }
        for (Cell cell : cells) {
            cell.step(fighters);
        }
    }

    public void clear() {
        active = false;
        for (Cell cell : cells) {
            cell.clearAll();
        }
        cells.clear();
        age = 0;
        boss = null;
    }

    // ------------------------------------------------------------------ one arena cell (Stormcaller body, red + fixed anchor)

    private final class Cell {
        private final List<Puff> puffs = new ArrayList<>();
        private final List<Bolt> bolts = new ArrayList<>();
        private final List<Scar> scars = new ArrayList<>();
        private final List<Charge> charges = new ArrayList<>();
        private Location cloud;
        private double cloudHeight = CLOUD_HEIGHT;
        private Location floorAnchor;
        private Leader leader;
        private int strikes;
        private int tick;

        Cell(Location floor) {
            this.floorAnchor = floor.clone();
            this.floorAnchor.setYaw(0);
            this.floorAnchor.setPitch(0);
            cloudHeight = measureHeight();
            cloud = floorAnchor.clone().add(0, cloudHeight, 0);
            cloud.setYaw(0);
            cloud.setPitch(0);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (float[] spec : PUFFS) {
                float[] scaled = {
                        spec[0] * CELL_SCALE, spec[1] * CELL_SCALE, spec[2] * CELL_SCALE,
                        spec[3] * CELL_SCALE, spec[4] * CELL_SCALE, spec[5] * CELL_SCALE,
                        spec[6]
                };
                Material material = spec[6] >= 7 ? Material.RED_WOOL
                        : spec[6] >= 4 ? Material.ORANGE_WOOL : Material.GRAY_WOOL;
                BlockDisplay display = spawn(cloud, material, null, null);
                if (display != null) {
                    puffs.add(new Puff(display, scaled, (float) random.nextDouble(Math.PI * 2),
                            (float) random.nextDouble(-0.012, 0.012), (float) random.nextDouble(Math.PI * 2)));
                }
            }
            birthFlash();
        }

        void nudgeToward(Location want) {
            if (want == null) {
                return;
            }
            floorAnchor.add(want.toVector().subtract(floorAnchor.toVector()).multiply(0.45));
            floorAnchor.setY(floorY);
        }

        void step(List<Player> fighters) {
            tick++;
            followAnchor();

            poseCloud(grow(), 1.0, 0.0);
            if (tick > INTRO / 2) {
                rain();
            }
            if (tick > INTRO && tick % 10 == 4 && ThreadLocalRandom.current().nextDouble() < 0.55) {
                sheetFlash();
            }
            if (tick % 30 == 10) {
                world.playSound(cloud, Sound.WEATHER_RAIN_ABOVE, 0.28f, 0.65f);
            }
            int phase = tick % CADENCE;
            if (tick + 2 >= CADENCE && phase == CADENCE - 2) {
                leader = openLeader(fighters);
            }
            if (leader != null && phase == CADENCE - 1) {
                creepLeader(leader, 0.4, 0.75, true);
            }
            if (tick >= CADENCE && phase == 0) {
                strike(fighters, false);
            }

            tickBolts();
            tickScars();
            tickCharges();
        }

        private double grow() {
            double x = Math.min(1.0, tick / (double) INTRO);
            double c1 = 1.70158;
            double c3 = c1 + 1.0;
            return Math.max(0.02, 1.0 + c3 * Math.pow(x - 1.0, 3) + c1 * Math.pow(x - 1.0, 2));
        }

        private double measureHeight() {
            Location eye = floorAnchor.clone().add(0, 1.6, 0);
            RayTraceResult ceiling = world.rayTraceBlocks(eye, new Vector(0, 1, 0), CLOUD_HEIGHT + 1.5,
                    FluidCollisionMode.NEVER, true);
            if (ceiling == null || ceiling.getHitPosition() == null) {
                return CLOUD_HEIGHT;
            }
            double room = ceiling.getHitPosition().getY() - floorAnchor.getY() - 1.3;
            return Math.max(3.0, Math.min(CLOUD_HEIGHT, room));
        }

        private void followAnchor() {
            if (tick % 5 == 0) {
                cloudHeight = measureHeight();
            }
            Location want = floorAnchor.clone().add(0, cloudHeight, 0);
            cloud.add(want.toVector().subtract(cloud.toVector()).multiply(0.25));
            for (Puff puff : puffs) {
                if (puff.display.isValid()) {
                    puff.display.teleport(cloud);
                }
            }
        }

        private void poseCloud(double grow, double drift, double lift) {
            boolean churn = tick <= INTRO || tick % 2 == 0;
            for (Puff puff : puffs) {
                BlockDisplay display = puff.display;
                if (!display.isValid()) {
                    continue;
                }
                int level = tick < puff.litUntil ? 15 : puff.level;
                if (level != puff.shown) {
                    puff.shown = level;
                    display.setBrightness(new Display.Brightness(level, level));
                }
                if (!churn) {
                    continue;
                }
                float g = (float) grow;
                float breathe = 1f + 0.05f * (float) Math.sin(tick * 0.13 + puff.phase);
                float sx = puff.sx * g * breathe;
                float sy = puff.sy * g * (2f - breathe);
                float sz = puff.sz * g * breathe;
                Quaternionf rot = new Quaternionf().rotateY(puff.yaw + tick * puff.spin);
                Vector3f centerOff = new Vector3f(
                        (float) (puff.dx * g * drift),
                        (float) (puff.dy * g + lift),
                        (float) (puff.dz * g * drift));
                Vector3f half = new Quaternionf(rot).transform(new Vector3f(sx / 2f, sy / 2f, sz / 2f));
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(2);
                display.setTransformation(new Transformation(centerOff.sub(half), rot, new Vector3f(sx, sy, sz), new Quaternionf()));
            }
        }

        private void rain() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Particle.DustOptions ash = new Particle.DustOptions(Color.fromRGB(120, 20, 20), 0.7f);
            for (int i = 0; i < 2; i++) {
                world.spawnParticle(Particle.DUST,
                        cloud.clone().add(random.nextDouble(-1.4, 1.4), -0.55, random.nextDouble(-1.3, 1.3)),
                        1, 0, 0, 0, 0, ash);
            }
            if (tick % 3 == 0) {
                world.spawnParticle(Particle.LARGE_SMOKE,
                        cloud.clone().add(random.nextDouble(-1.8, 1.8), random.nextDouble(-0.3, 0.6), random.nextDouble(-1.7, 1.7)),
                        1, 0, 0, 0, 0.005);
            }
            if (tick % 2 == 0) {
                Location drop = cloud.clone().add(random.nextDouble(-1.3, 1.3), -0.6, random.nextDouble(-1.2, 1.2));
                RayTraceResult floor = world.rayTraceBlocks(drop, new Vector(0, -1, 0), cloudHeight + 4.0,
                        FluidCollisionMode.ALWAYS, true);
                if (floor != null && floor.getHitPosition() != null) {
                    world.spawnParticle(Particle.SMOKE, floor.getHitPosition().toLocation(world).add(0, 0.1, 0),
                            2, 0.12, 0.0, 0.12, 0.0);
                }
            }
        }

        private void birthFlash() {
            world.spawnParticle(Particle.FLASH, cloud, 1, 0, 0, 0, 0);
            world.playSound(cloud, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 0.55f);
            world.playSound(cloud, Sound.WEATHER_RAIN_ABOVE, 0.45f, 0.7f);
        }

        private void sheetFlash() {
            if (puffs.isEmpty()) {
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2; i++) {
                puffs.get(random.nextInt(puffs.size())).litUntil = tick + 1;
            }
        }

        private void lightCloud(Location root, boolean all) {
            for (Puff puff : puffs) {
                double dx = cloud.getX() + puff.dx - root.getX();
                double dz = cloud.getZ() + puff.dz - root.getZ();
                boolean near = all || dx * dx + dz * dz < 4.0;
                puff.litUntil = Math.max(puff.litUntil, tick + (near ? 2 : 1));
            }
        }

        private Location cloudRoot() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location root = cloud.clone().add(random.nextDouble(-1.0, 1.0), -0.45, random.nextDouble(-0.95, 0.95));
            root.setYaw(0);
            root.setPitch(0);
            return root;
        }

        private Leader openLeader(List<Player> fighters) {
            Location root = cloudRoot();
            Player target = pickTarget(fighters);
            Location end;
            if (target != null) {
                end = target.getLocation().add(0, 0.05, 0);
            } else {
                end = strayGround();
                if (end == null) {
                    return null;
                }
            }
            Leader next = new Leader(root, target, end, jag(root.toVector(), end.toVector(), 3, 0.22));
            creepLeader(next, 0.0, 0.4, false);
            world.playSound(root, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.3f, 1.4f);
            if (target != null) {
                world.playSound(target.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, 0.35f, 1.6f);
            }
            return next;
        }

        private Player pickTarget(List<Player> fighters) {
            if (fighters == null || fighters.isEmpty() || cloud == null) {
                return null;
            }
            List<Player> near = new ArrayList<>();
            double r2 = RANGE * RANGE;
            for (Player player : fighters) {
                if (player == null || !player.isValid() || player.isDead() || player.getWorld() != world) {
                    continue;
                }
                if (player.getLocation().distanceSquared(cloud) <= r2) {
                    near.add(player);
                }
            }
            if (near.isEmpty()) {
                return null;
            }
            return near.get(ThreadLocalRandom.current().nextInt(near.size()));
        }

        private Location strayGround() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location above = floorAnchor.clone().add(random.nextDouble(-5, 5), cloudHeight - 0.5, random.nextDouble(-5, 5));
            RayTraceResult floor = world.rayTraceBlocks(above, new Vector(0, -1, 0), cloudHeight + 6.0,
                    FluidCollisionMode.NEVER, true);
            if (floor == null || floor.getHitPosition() == null) {
                return floorAnchor.clone().add(0, 0.05, 0);
            }
            return floor.getHitPosition().toLocation(world).add(0, 0.05, 0);
        }

        private void creepLeader(Leader lead, double from, double to, boolean streamer) {
            Particle.DustOptions dim = new Particle.DustOptions(PALE, 0.55f);
            List<Vector> path = lead.path;
            int n = path.size() - 1;
            Vector tip = null;
            for (int i = 0; i < n; i++) {
                double t0 = i / (double) n;
                double t1 = (i + 1) / (double) n;
                if (t1 <= from || t0 >= to) {
                    continue;
                }
                Vector a = path.get(i);
                Vector b = path.get(i + 1);
                Vector seg = b.clone().subtract(a);
                double len = seg.length();
                for (double s = 0; s <= len; s += 0.45) {
                    double t = t0 + (t1 - t0) * (s / Math.max(1.0E-3, len));
                    if (t < from || t > to) {
                        continue;
                    }
                    Vector at = a.clone().add(seg.clone().multiply(s / Math.max(1.0E-3, len)));
                    world.spawnParticle(Particle.DUST, at.toLocation(world), 1, 0, 0, 0, 0, dim);
                    tip = at;
                }
            }
            if (tip != null) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, tip.toLocation(world), 3, 0.08, 0.08, 0.08, 0.03);
            }
            if (streamer && lead.target != null && lead.target.isValid()) {
                Location head = lead.target.getLocation().add(0, lead.target.getHeight(), 0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, head, 5, 0.12, 0.1, 0.12, 0.04);
                for (int i = 1; i <= 3; i++) {
                    world.spawnParticle(Particle.DUST, head.clone().add(0, i * 0.25, 0), 1, 0.03, 0, 0.03, 0, dim);
                }
            }
        }

        private void strike(List<Player> fighters, boolean heavy) {
            Leader lead = leader;
            leader = null;
            LivingEntity target = lead == null ? null : lead.target;
            if (target != null && (!target.isValid() || target.isDead())) {
                target = null;
            }
            if (target == null && lead != null && lead.target != null) {
                lead = null;
            }
            if (target == null && lead == null) {
                Player fresh = pickTarget(fighters);
                Location root = cloudRoot();
                Location end = fresh != null ? fresh.getLocation().add(0, 0.05, 0) : strayGround();
                if (end == null) {
                    return;
                }
                lead = new Leader(root, fresh, end, jag(root.toVector(), end.toVector(), 3, 0.22));
                target = fresh;
            }

            List<Vector> path = lead.path;
            Location end = lead.end;
            if (target != null) {
                end = target.getLocation().add(0, 0.05, 0);
                Vector shift = end.toVector().subtract(lead.end.toVector());
                int n = path.size() - 1;
                for (int i = 1; i <= n; i++) {
                    path.get(i).add(shift.clone().multiply(i / (double) n));
                }
            }

            boolean stray = target == null;
            boolean crowded = LIVE.size() > MAX_LIVE;
            float width = heavy ? 0.26f : stray ? 0.1f : 0.16f;
            int forkCount = crowded ? 0 : heavy ? 3 : stray ? 1 : 2;
            spawnBolt(lead.root, path, forks(path, forkCount), width);
            lightCloud(lead.root, heavy);
            strikes++;

            Location impact = end.clone();
            Block floorBlock = floorBelow(impact);
            world.spawnParticle(Particle.FLASH, impact.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.DUST, impact.clone().add(0, 0.3, 0), heavy ? 22 : 10, 0.35, 0.2, 0.35, 0,
                    new Particle.DustOptions(PALE, 1.2f));
            world.spawnParticle(Particle.FLAME, impact.clone().add(0, 0.4, 0), heavy ? 10 : 4, 0.35, 0.2, 0.35, 0.02);
            if (floorBlock != null) {
                world.spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.15, 0), heavy ? 44 : 22,
                        heavy ? 1.0 : 0.6, 0.1, heavy ? 1.0 : 0.6, 0.2, floorBlock.getBlockData());
            }

            float volume = stray ? 0.3f : 0.65f;
            world.playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, volume, 0.75f + ThreadLocalRandom.current().nextFloat() * 0.2f);
            world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_BLAST, volume * 0.7f, 0.45f);
            world.playSound(impact, Sound.ITEM_MACE_SMASH_GROUND, volume * 0.75f, stray ? 1.4f : 1.15f);
            if (strikes % 2 == 0) {
                Location roll = cloud.clone();
                float pitch = 0.5f + ThreadLocalRandom.current().nextFloat() * 0.2f;
                world.playSound(roll, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.28f, pitch);
            }

            if (!crowded && floorBlock != null) {
                Location floor = impact.clone();
                floor.setY(floorBlock.getY() + 1.0);
                floor.setYaw(0);
                floor.setPitch(0);
                scars.add(new Scar(floor, heavy, stray));
            }

            if (target instanceof Player hitPlayer && boss != null && boss.isValid()) {
                BossHits.hurt(hitPlayer, boss, STRIKE_POWER);
                hitPlayer.setFireTicks(0);
                if (charges.size() < MAX_CHARGES) {
                    charges.add(new Charge(hitPlayer));
                }
            }
        }

        private Block floorBelow(Location at) {
            RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 0.6, 0), new Vector(0, -1, 0), 2.6,
                    FluidCollisionMode.NEVER, true);
            return hit == null ? null : hit.getHitBlock();
        }

        private List<List<Vector>> forks(List<Vector> path, int count) {
            List<List<Vector>> out = new ArrayList<>();
            if (count <= 0 || path.size() < 6) {
                return out;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Vector end = path.get(path.size() - 1);
            for (int f = 0; f < count; f++) {
                Vector start = path.get(random.nextInt(2, path.size() - 3));
                Vector toward = end.clone().subtract(start);
                double remain = toward.length();
                if (remain < 1.0) {
                    continue;
                }
                toward.multiply(1.0 / remain);
                Vector dir = toward.add(perp(toward, random).multiply(random.nextDouble(0.7, 1.15))).normalize();
                Vector tip = start.clone().add(dir.multiply(remain * random.nextDouble(0.3, 0.45)));
                out.add(jag(start, tip, 2, 0.25));
            }
            return out;
        }

        private void spawnBolt(Location anchorAt, List<Vector> path, List<List<Vector>> forks, float width) {
            Location anchor = anchorAt.clone();
            anchor.setYaw(0);
            anchor.setPitch(0);
            Vector origin = anchor.toVector();
            Bolt bolt = new Bolt(width);
            addSegments(bolt.main, bolt.mainSegs, anchor, origin, path, width);
            float forkWidth = width * FORK_FLICKER[0];
            for (List<Vector> fork : forks) {
                addSegments(bolt.forks, bolt.forkSegs, anchor, origin, fork, forkWidth);
            }
            bolts.add(bolt);
        }

        private void addSegments(List<BlockDisplay> into, List<Vector[]> segs, Location anchor, Vector origin,
                                 List<Vector> path, float width) {
            for (int i = 0; i < path.size() - 1; i++) {
                Vector a = path.get(i).clone().subtract(origin);
                Vector b = path.get(i + 1).clone().subtract(origin);
                BlockDisplay display = spawn(anchor, Material.RED_CONCRETE, HALO, beam(a, b, width));
                if (display != null) {
                    into.add(display);
                    segs.add(new Vector[]{a, b});
                }
            }
        }

        private void tickBolts() {
            Iterator<Bolt> it = bolts.iterator();
            while (it.hasNext()) {
                Bolt bolt = it.next();
                bolt.age++;
                if (bolt.age >= BOLT_TICKS) {
                    removeAll(bolt.main);
                    removeAll(bolt.forks);
                    it.remove();
                    continue;
                }
                float main = MAIN_FLICKER[bolt.age];
                for (int i = 0; i < bolt.main.size(); i++) {
                    Vector[] seg = bolt.mainSegs.get(i);
                    snap(bolt.main.get(i), beam(seg[0], seg[1], bolt.width * main));
                }
                if (bolt.age >= FORK_FLICKER.length) {
                    removeAll(bolt.forks);
                } else {
                    float fork = FORK_FLICKER[bolt.age];
                    for (int i = 0; i < bolt.forks.size(); i++) {
                        Vector[] seg = bolt.forkSegs.get(i);
                        snap(bolt.forks.get(i), beam(seg[0], seg[1], Math.max(0.001f, bolt.width * fork)));
                    }
                }
            }
        }

        private void tickCharges() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Iterator<Charge> it = charges.iterator();
            while (it.hasNext()) {
                Charge charge = it.next();
                charge.age++;
                LivingEntity body = charge.body;
                if (!body.isValid() || body.isDead() || charge.age > CHARGE_TICKS) {
                    it.remove();
                    continue;
                }
                double w = Math.max(0.3, body.getWidth() * 0.55);
                Location at = body.getLocation().add(random.nextDouble(-w, w), random.nextDouble(0.1, body.getHeight()),
                        random.nextDouble(-w, w));
                world.spawnParticle(Particle.ELECTRIC_SPARK, at, 2, 0.05, 0.05, 0.05, 0.02);
                if (charge.age % 3 == 0) {
                    world.spawnParticle(Particle.FLAME, at, 1, 0, 0, 0, 0);
                }
            }
        }

        private void tickScars() {
            Iterator<Scar> it = scars.iterator();
            while (it.hasNext()) {
                Scar scar = it.next();
                if (!scar.step()) {
                    it.remove();
                }
            }
        }

        private final class Scar {
            final Location floor;
            final List<BlockDisplay> tiles = new ArrayList<>();
            final List<float[]> segs = new ArrayList<>();
            int age;

            Scar(Location floor, boolean heavy, boolean stray) {
                this.floor = floor;
                ThreadLocalRandom random = ThreadLocalRandom.current();
                int branches = heavy ? 7 : stray ? 3 : 5;
                int depth = heavy ? 4 : 3;
                int cap = heavy ? 30 : stray ? 8 : 20;
                double base = random.nextDouble(Math.PI * 2);
                for (int b = 0; b < branches; b++) {
                    double heading = base + b * Math.PI * 2 / branches + random.nextDouble(-0.35, 0.35);
                    grow(0, 0, heading, heavy ? 0.13 : 0.1, heavy ? 0.62 : 0.5, depth, 0, true, cap, random);
                }
                for (float[] seg : segs) {
                    BlockDisplay tile = spawn(floor, Material.ORANGE_CONCRETE, SCAR_GLOW, flat(seg, 0.001f));
                    tiles.add(tile);
                }
                Particle.DustOptions hot = new Particle.DustOptions(PALE, 0.8f);
                for (float[] seg : segs) {
                    world.spawnParticle(Particle.DUST, floor.clone().add(seg[2], 0.08, seg[3]), 1, 0, 0, 0, 0, hot);
                }
            }

            private void grow(double x, double z, double heading, double width, double len, int count, int depth0,
                              boolean fork, int cap, ThreadLocalRandom random) {
                for (int d = 0; d < count && segs.size() < cap; d++) {
                    heading += random.nextDouble(-0.55, 0.55);
                    double nx = x + Math.cos(heading) * len;
                    double nz = z + Math.sin(heading) * len;
                    segs.add(new float[]{(float) x, (float) z, (float) nx, (float) nz, (float) width, depth0 + d});
                    if (fork && d >= 1 && random.nextDouble() < 0.45) {
                        double side = random.nextBoolean() ? 1.0 : -1.0;
                        grow(nx, nz, heading + side * random.nextDouble(0.6, 1.1), width * 0.6, len * 0.7, 2,
                                depth0 + d + 1, false, cap, random);
                    }
                    x = nx;
                    z = nz;
                    len *= 0.8;
                    width *= 0.75;
                }
            }

            boolean step() {
                age++;
                if (age > SCAR_TICKS) {
                    removeAll(tiles);
                    return false;
                }
                for (int i = 0; i < tiles.size(); i++) {
                    BlockDisplay tile = tiles.get(i);
                    if (tile == null || !tile.isValid()) {
                        continue;
                    }
                    float[] seg = segs.get(i);
                    if (age == (int) seg[5] + 1) {
                        tile.setInterpolationDelay(0);
                        tile.setInterpolationDuration(1);
                        tile.setTransformation(flat(seg, 1f));
                    }
                    if (age == 5) {
                        tile.setBlock(Material.RED_CONCRETE.createBlockData());
                        tile.setGlowing(false);
                    } else if (age == 10) {
                        tile.setBlock(Material.BLACK_CONCRETE.createBlockData());
                    } else if (age == SCAR_TICKS - 6) {
                        tile.setInterpolationDelay(0);
                        tile.setInterpolationDuration(6);
                        tile.setTransformation(flat(seg, 0.02f));
                    }
                }
                if (age >= 2 && age <= 7 && !segs.isEmpty()) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 2; i++) {
                        float[] seg = segs.get(random.nextInt(segs.size()));
                        world.spawnParticle(Particle.FLAME, floor.clone().add(seg[2], 0.1, seg[3]), 1, 0, 0, 0, 0);
                    }
                }
                if (age == 11) {
                    world.spawnParticle(Particle.SMOKE, floor.clone().add(0, 0.1, 0), 5, 0.5, 0.02, 0.5, 0.01);
                }
                return true;
            }

            private Transformation flat(float[] seg, float scale) {
                float dx = seg[2] - seg[0];
                float dz = seg[3] - seg[1];
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                float w = Math.max(0.001f, seg[4] * scale);
                float l = Math.max(0.001f, (len + seg[4] * 0.5f) * Math.min(1f, scale * 4f));
                Quaternionf rot = new Quaternionf().rotateY((float) Math.atan2(dx, dz));
                Vector3f half = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, seg[4] * 0.25f));
                Vector3f at = new Vector3f(seg[0], 0.015f, seg[1]).sub(half);
                return new Transformation(at, rot, new Vector3f(w, 0.012f, l), new Quaternionf());
            }
        }

        void clearAll() {
            for (Puff puff : puffs) {
                discard(puff.display);
            }
            puffs.clear();
            for (Bolt bolt : bolts) {
                removeAll(bolt.main);
                removeAll(bolt.forks);
            }
            bolts.clear();
            for (Scar scar : scars) {
                removeAll(scar.tiles);
            }
            scars.clear();
            charges.clear();
            leader = null;
        }
    }

    // ------------------------------------------------------------------ shared geometry / displays

    private static List<Vector> jag(Vector a, Vector b, int depth, double rough) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Vector> points = new ArrayList<>();
        points.add(a.clone());
        points.add(b.clone());
        double amount = rough;
        for (int d = 0; d < depth; d++) {
            List<Vector> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i < points.size() - 1; i++) {
                Vector p = points.get(i);
                Vector q = points.get(i + 1);
                Vector seg = q.clone().subtract(p);
                double len = seg.length();
                Vector mid = p.clone().add(q).multiply(0.5);
                if (len > 1.0E-3) {
                    mid.add(perp(seg, random).multiply(random.nextDouble(-1.0, 1.0) * len * amount));
                }
                next.add(p);
                next.add(mid);
            }
            next.add(points.get(points.size() - 1));
            points = next;
            amount *= 0.6;
        }
        return points;
    }

    private static Vector perp(Vector axis, ThreadLocalRandom random) {
        Vector n = axis.clone().normalize();
        Vector r = new Vector(random.nextDouble(-1, 1), random.nextDouble(-1, 1), random.nextDouble(-1, 1));
        r.subtract(n.clone().multiply(r.dot(n)));
        if (r.lengthSquared() < 1.0E-4) {
            r = Math.abs(n.getY()) < 0.9 ? new Vector(0, 1, 0).crossProduct(n) : new Vector(1, 0, 0).crossProduct(n);
        }
        return r.normalize();
    }

    private static Transformation beam(Vector a, Vector b, float width) {
        Vector3f d = new Vector3f((float) (b.getX() - a.getX()), (float) (b.getY() - a.getY()), (float) (b.getZ() - a.getZ()));
        float len = d.length();
        float w = Math.max(0.001f, width);
        if (len < 1.0E-3f) {
            return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
        }
        d.div(len);
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), d);
        Vector3f start = new Vector3f((float) a.getX(), (float) a.getY(), (float) a.getZ()).sub(new Vector3f(d).mul(w * 0.4f));
        Vector3f off = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, w / 2f));
        return new Transformation(start.sub(off), rot, new Vector3f(w, len + w * 0.8f, w), new Quaternionf());
    }

    private static void snap(BlockDisplay display, Transformation transformation) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawn(Location at, Material material, Color glow, Transformation initial) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setPersistent(false);
                spawned.setBrightness(glow != null ? LIT : new Display.Brightness(3, 3));
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTransformation(initial != null ? initial : new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void removeAll(List<BlockDisplay> displays) {
        for (BlockDisplay display : displays) {
            discard(display);
        }
        displays.clear();
    }

    private static void discard(Display display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }

    private static final class Puff {
        final BlockDisplay display;
        final float dx;
        final float dy;
        final float dz;
        final float sx;
        final float sy;
        final float sz;
        final int level;
        final float yaw;
        final float spin;
        final float phase;
        int litUntil;
        int shown = -1;

        Puff(BlockDisplay display, float[] spec, float yaw, float spin, float phase) {
            this.display = display;
            this.dx = spec[0];
            this.dy = spec[1];
            this.dz = spec[2];
            this.sx = spec[3];
            this.sy = spec[4];
            this.sz = spec[5];
            this.level = (int) spec[6];
            this.yaw = yaw;
            this.spin = spin;
            this.phase = phase;
        }
    }

    private static final class Bolt {
        final List<BlockDisplay> main = new ArrayList<>();
        final List<Vector[]> mainSegs = new ArrayList<>();
        final List<BlockDisplay> forks = new ArrayList<>();
        final List<Vector[]> forkSegs = new ArrayList<>();
        final float width;
        int age = -1;

        Bolt(float width) {
            this.width = width;
        }
    }

    private static final class Leader {
        final Location root;
        final LivingEntity target;
        final Location end;
        final List<Vector> path;

        Leader(Location root, LivingEntity target, Location end, List<Vector> path) {
            this.root = root;
            this.target = target;
            this.end = end;
            this.path = path;
        }
    }

    private static final class Charge {
        final LivingEntity body;
        int age;

        Charge(LivingEntity body) {
            this.body = body;
        }
    }
}
