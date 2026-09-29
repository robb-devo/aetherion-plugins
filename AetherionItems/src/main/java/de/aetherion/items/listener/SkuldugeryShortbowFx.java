package de.aetherion.items.listener;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skuldugery's Shortbow visuals only; firing, damage, pierce and the Enderman pin stay in {@link ShortbowListener}.
 * Each shot flicks a fan of embers off the string, arrows leave a cinder wake that cools to ash,
 * hits leave a spinning calling card that burns away (gilt on crits), and pinned Endermen are
 * bound by closing ash rings while their teleport is sucked back into them.
 */
public final class SkuldugeryShortbowFx {

    static final String ITEM_ID = "skuldugery_shortbow";

    private static final Color EMBER = Color.fromRGB(255, 122, 46);
    private static final Color CINDER = Color.fromRGB(150, 58, 36);
    private static final Color ASH = Color.fromRGB(92, 84, 82);
    private static final Color GILT = Color.fromRGB(255, 206, 96);
    private static final int TRAIL_MAX_AGE = 40;
    private static final int CARD_TICKS = 16;
    private static final int CARD_GAP_TICKS = 12;
    private static final int MAX_CARDS = 10;
    private static final int SNARE_TICKS = 10;
    private static final int FIZZLE_GAP_TICKS = 8;
    private static final float CARD_W = 0.34f;
    private static final float CARD_H = 0.48f;
    private static final float CARD_T = 0.02f;
    private static final float PIP = 0.13f;
    private static final double[] SNARE_HEIGHTS = {0.35, 1.35, 2.35};

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final Map<UUID, Trail> trails = new HashMap<>();
    private final List<Card> cards = new ArrayList<>();
    private final Map<UUID, Long> cardGate = new HashMap<>();
    private final Map<UUID, Snare> snares = new HashMap<>();
    private final Map<UUID, Long> fizzleGate = new HashMap<>();

    /** Plugin disable: every calling card still in the air goes with it. */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    static boolean isSkuldugery(String itemId) {
        return ITEM_ID.equalsIgnoreCase(itemId == null ? "" : itemId);
    }

    void track(Arrow arrow, boolean crit) {
        trails.put(arrow.getUniqueId(), new Trail(arrow, arrow.getLocation().clone(), crit));
    }

    boolean isTracked(Arrow arrow) {
        return arrow != null && trails.containsKey(arrow.getUniqueId());
    }

    /** Release: an ember streak along every arrow of the volley, the fan arc between them, and a flicked-card snap. */
    void muzzle(Player player, List<Vector> directions, boolean crit) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector right = look.getCrossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Location tip = eye.clone().add(look.clone().multiply(0.9)).add(right.multiply(0.18)).add(0, -0.18, 0);

        for (Vector raw : directions) {
            Vector d = raw.clone().normalize();
            for (int k = 0; k < 4; k++) {
                Color hot = k == 0 && crit ? GILT : EMBER;
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip.clone().add(d.clone().multiply(0.35 + k * 0.35)),
                        1, 0.01, 0.01, 0.01, 0, new Particle.DustTransition(hot, ASH, 0.95f - k * 0.13f));
            }
        }
        if (directions.size() >= 2) {
            Vector first = directions.get(0).clone().normalize();
            Vector last = directions.get(directions.size() - 1).clone().normalize();
            Particle.DustTransition arc = new Particle.DustTransition(CINDER, ASH, 0.55f);
            for (int i = 0; i <= 6; i++) {
                Vector d = first.clone().multiply(1.0 - i / 6.0).add(last.clone().multiply(i / 6.0)).normalize();
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip.clone().add(d.multiply(0.8)), 1, 0, 0, 0, 0, arc);
            }
        }
        world.spawnParticle(Particle.SMOKE, tip, 2, 0.03, 0.03, 0.03, 0.01);
        world.spawnParticle(Particle.SMALL_FLAME, tip, 1, 0.02, 0.02, 0.02, 0.004);

        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.45f);
        world.playSound(eye, Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.75f);
        world.playSound(eye, Sound.BLOCK_CANDLE_EXTINGUISH, 0.3f, 1.35f);
        if (directions.size() >= 2) {
            world.playSound(eye, Sound.ITEM_CROSSBOW_LOADING_END, 0.3f, 1.9f);
        }
        if (crit) {
            world.playSound(eye, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.3f, 1.7f);
        }
    }

    /** Called every tick by {@link ShortbowListener#run()}. */
    void tick(long now) {
        tickTrails();
        tickCards();
        tickSnares();
        if (now % 100 == 0) {
            cardGate.values().removeIf(until -> until < now);
            fizzleGate.values().removeIf(until -> until < now);
        }
    }

    /** A hit (including each pierce): cinder burst at the arrowhead, and a calling card over the target. */
    void impactEntity(Arrow arrow, LivingEntity target, long now) {
        Trail trail = trails.get(arrow.getUniqueId());
        if (trail == null) {
            return;
        }
        World world = target.getWorld();
        Location at = arrow.getLocation();
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 6, 0.18, 0.18, 0.18, 0,
                new Particle.DustTransition(trail.crit ? GILT : EMBER, ASH, 0.9f));
        world.spawnParticle(Particle.ASH, at, 5, 0.25, 0.25, 0.25, 0);
        world.spawnParticle(Particle.SMOKE, at, 2, 0.08, 0.08, 0.08, 0.01);
        world.playSound(at, Sound.BLOCK_CANDLE_EXTINGUISH, 0.35f, 1.1f);

        if (trail.crit) {
            Location chest = target.getLocation().add(0, target.getHeight() * 0.55, 0);
            Particle.DustOptions gilt = new Particle.DustOptions(GILT, 0.8f);
            double r = Math.max(0.55, target.getWidth() * 0.9);
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2 * i / 14;
                world.spawnParticle(Particle.DUST, chest.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, gilt);
            }
            world.playSound(chest, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.55f, 1.25f);
        }

        Long gate = cardGate.get(target.getUniqueId());
        if ((gate != null && now < gate) || cards.size() >= MAX_CARDS) {
            return;
        }
        cardGate.put(target.getUniqueId(), now + CARD_GAP_TICKS);
        Location top = target.getLocation().add(0, target.getHeight() + 0.35, 0);
        top.setYaw(0);
        top.setPitch(0);
        Card card = Card.spawn(top, trail.crit);
        if (card != null) {
            cards.add(card);
            world.playSound(top, Sound.ITEM_BOOK_PAGE_TURN, 0.55f, 2.0f);
        }
    }

    /** Arrow buried in a block: the wick snuffs out. */
    void impactBlock(Arrow arrow) {
        Trail trail = trails.remove(arrow.getUniqueId());
        if (trail == null) {
            return;
        }
        World world = arrow.getWorld();
        Location at = arrow.getLocation();
        world.spawnParticle(Particle.SMOKE, at, 3, 0.06, 0.06, 0.06, 0.01);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 4, 0.12, 0.12, 0.12, 0,
                new Particle.DustTransition(EMBER, ASH, 0.7f));
        world.spawnParticle(Particle.ASH, at, 3, 0.15, 0.15, 0.15, 0);
        world.playSound(at, Sound.BLOCK_CANDLE_EXTINGUISH, 0.25f, 1.5f);
    }

    /** The Enderman pin, drawn: ash rings close in and the attempted teleport flows back into its body. */
    void snare(LivingEntity enderman) {
        boolean fresh = snares.put(enderman.getUniqueId(), new Snare(enderman)) == null;
        if (fresh) {
            Location at = enderman.getLocation().add(0, 1.4, 0);
            enderman.getWorld().playSound(at, Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.75f);
            enderman.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 0.45f);
        }
    }

    /** A cancelled Enderman teleport near a Skuldugery arrow snuffs out instead of blinking. */
    void fizzle(Entity enderman, long now) {
        if (!snares.containsKey(enderman.getUniqueId()) && !arrowNear(enderman, 24.0)) {
            return;
        }
        Long gate = fizzleGate.get(enderman.getUniqueId());
        if (gate != null && now < gate) {
            return;
        }
        fizzleGate.put(enderman.getUniqueId(), now + FIZZLE_GAP_TICKS);
        World world = enderman.getWorld();
        Location at = enderman.getLocation().add(0, 1.4, 0);
        world.spawnParticle(Particle.REVERSE_PORTAL, at, 10, 0.3, 0.9, 0.3, 0.02);
        world.spawnParticle(Particle.SMOKE, at, 6, 0.25, 0.7, 0.25, 0.01);
        world.spawnParticle(Particle.ASH, at, 6, 0.3, 0.8, 0.3, 0);
        world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.7f);
    }

    private boolean arrowNear(Entity entity, double range) {
        double max = range * range;
        for (Trail trail : trails.values()) {
            Arrow arrow = trail.arrow;
            if (arrow.isValid() && arrow.getWorld() == entity.getWorld()
                    && arrow.getLocation().distanceSquared(entity.getLocation()) <= max) {
                return true;
            }
        }
        return false;
    }

    private void tickTrails() {
        Iterator<Trail> it = trails.values().iterator();
        while (it.hasNext()) {
            Trail trail = it.next();
            Arrow arrow = trail.arrow;
            trail.age++;
            if (!arrow.isValid() || arrow.isInBlock() || trail.age > TRAIL_MAX_AGE
                    || arrow.getWorld() != trail.last.getWorld()) {
                it.remove();
                continue;
            }
            World world = arrow.getWorld();
            Location now = arrow.getLocation();
            Vector seg = now.toVector().subtract(trail.last.toVector());
            double len = seg.length();
            int points = Math.max(1, Math.min(7, (int) Math.ceil(len / 0.55)));
            Particle.DustTransition wake = new Particle.DustTransition(
                    trail.crit ? GILT : EMBER, ASH, trail.crit ? 0.95f : 0.75f);
            for (int i = 1; i <= points; i++) {
                Location p = trail.last.clone().add(seg.clone().multiply(i / (double) points));
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0.02, 0.02, 0.02, 0, wake);
            }
            if (trail.age % 2 == 0) {
                world.spawnParticle(Particle.ASH, now, 1, 0.05, 0.05, 0.05, 0);
                if (trail.crit) {
                    world.spawnParticle(Particle.SMALL_FLAME, now, 1, 0.02, 0.02, 0.02, 0);
                }
            }
            trail.last = now;
        }
    }

    private void tickCards() {
        Iterator<Card> it = cards.iterator();
        while (it.hasNext()) {
            Card card = it.next();
            if (!card.step()) {
                it.remove();
            }
        }
    }

    private void tickSnares() {
        Iterator<Snare> it = snares.values().iterator();
        while (it.hasNext()) {
            Snare snare = it.next();
            snare.age++;
            LivingEntity body = snare.body;
            if (!body.isValid() || body.isDead() || snare.age > SNARE_TICKS) {
                if (body.isValid()) {
                    Location at = body.getLocation().add(0, 1.2, 0);
                    body.getWorld().spawnParticle(Particle.ASH, at, 10, 0.35, 0.9, 0.35, 0);
                    body.getWorld().playSound(at, Sound.BLOCK_CHAIN_BREAK, 0.5f, 1.2f);
                }
                it.remove();
                continue;
            }
            World world = body.getWorld();
            Location feet = body.getLocation();
            double t = snare.age / (double) SNARE_TICKS;
            double radius = 1.05 - 0.5 * (1.0 - Math.pow(1.0 - t, 3));
            Particle.DustTransition bind = new Particle.DustTransition(CINDER, ASH, 0.9f);
            for (int ring = 0; ring < SNARE_HEIGHTS.length; ring++) {
                double turn = (ring % 2 == 0 ? 1 : -1) * snare.age * 0.35;
                for (int i = 0; i < 12; i++) {
                    double a = turn + Math.PI * 2 * i / 12;
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                            feet.clone().add(Math.cos(a) * radius, SNARE_HEIGHTS[ring], Math.sin(a) * radius),
                            1, 0, 0, 0, 0, bind);
                }
            }
            if (snare.age % 2 == 0) {
                Location core = feet.clone().add(0, 1.45, 0);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble(Math.PI * 2);
                    double d = 1.6 + random.nextDouble(1.2);
                    world.spawnParticle(Particle.PORTAL, core, 0,
                            Math.cos(a) * d, random.nextDouble(-0.9, 0.9), Math.sin(a) * d, 1.0);
                }
            }
        }
    }

    private static final class Trail {
        final Arrow arrow;
        final boolean crit;
        Location last;
        int age;

        Trail(Arrow arrow, Location last, boolean crit) {
            this.arrow = arrow;
            this.last = last;
            this.crit = crit;
        }
    }

    private static final class Snare {
        final LivingEntity body;
        int age;

        Snare(LivingEntity body) {
            this.body = body;
        }
    }

    /** A thin card with a diamond pip that protrudes through both faces, so it reads from either side. */
    private static final class Card {
        private static final Quaternionf PIP_TURN = new Quaternionf().rotateZ((float) Math.toRadians(45.0));

        final BlockDisplay face;
        final BlockDisplay pip;
        final Location base;
        final boolean gilt;
        final float spin0;
        int age;

        private Card(BlockDisplay face, BlockDisplay pip, Location base, boolean gilt) {
            this.face = face;
            this.pip = pip;
            this.base = base;
            this.gilt = gilt;
            this.spin0 = (float) ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        }

        static Card spawn(Location at, boolean gilt) {
            BlockDisplay face = display(at, gilt ? Material.GOLD_BLOCK : Material.SMOOTH_QUARTZ);
            BlockDisplay pip = display(at, gilt ? Material.BLACK_CONCRETE : Material.RED_CONCRETE);
            if (face == null || pip == null) {
                discard(face);
                discard(pip);
                return null;
            }
            return new Card(face, pip, at, gilt);
        }

        /** @return false once the card has burned away and been removed */
        boolean step() {
            age++;
            if (!face.isValid() || !pip.isValid() || age > CARD_TICKS) {
                burnOut();
                return false;
            }
            float scale;
            if (age <= 3) {
                double k = age / 3.0;
                scale = (float) (k * (1.0 + 0.15 * Math.sin(k * Math.PI)));
            } else if (age > CARD_TICKS - 4) {
                scale = (float) Math.max(0.01, (CARD_TICKS - age) / 4.0);
            } else {
                scale = 1.0f;
            }
            double rise = 0.9 * (1.0 - Math.pow(1.0 - age / (double) CARD_TICKS, 2));
            Quaternionf turn = new Quaternionf()
                    .rotateY(spin0 + age * 0.55f)
                    .rotateX((float) (0.25 * Math.sin(age * 0.6)));
            Vector3f center = new Vector3f(0f, (float) rise, 0f);
            pose(face, turn, center, CARD_W * scale, CARD_H * scale, CARD_T * scale);
            Quaternionf pipTurn = new Quaternionf(turn).mul(PIP_TURN);
            pose(pip, pipTurn, center, PIP * scale, PIP * scale, CARD_T * 1.8f * Math.max(0.2f, scale));

            if (age >= 9) {
                World world = base.getWorld();
                if (world != null) {
                    Location burn = base.clone().add(0, rise, 0);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, burn, 2, 0.12, 0.16, 0.12, 0,
                            new Particle.DustTransition(gilt ? GILT : EMBER, ASH, 0.6f));
                    if (age % 3 == 0) {
                        world.spawnParticle(Particle.SMALL_FLAME, burn, 1, 0.08, 0.1, 0.08, 0.002);
                    }
                }
            }
            return true;
        }

        private void burnOut() {
            World world = base.getWorld();
            if (world != null) {
                Location at = base.clone().add(0, 0.9, 0);
                world.spawnParticle(Particle.ASH, at, 10, 0.15, 0.15, 0.15, 0);
                world.spawnParticle(Particle.SMOKE, at, 3, 0.08, 0.08, 0.08, 0.01);
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 6, 0.18, 0.18, 0.18, 0,
                        new Particle.DustTransition(gilt ? GILT : EMBER, ASH, 0.7f));
                if (gilt) {
                    world.spawnParticle(Particle.SMALL_FLAME, at, 3, 0.1, 0.1, 0.1, 0.01);
                }
                world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.25f, 1.9f);
            }
            discard(face);
            discard(pip);
        }

        private static void pose(BlockDisplay display, Quaternionf rot, Vector3f center, float x, float y, float z) {
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(x / 2f, y / 2f, z / 2f));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(new Transformation(
                    new Vector3f(center).sub(half), new Quaternionf(rot), new Vector3f(x, y, z), new Quaternionf()));
        }

        private static BlockDisplay display(Location at, Material material) {
            World world = at.getWorld();
            if (world == null) {
                return null;
            }
            try {
                BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                    spawned.setBlock(material.createBlockData());
                    spawned.setPersistent(false);
                    spawned.setBrightness(new Display.Brightness(15, 15));
                    spawned.setTransformation(new Transformation(
                            new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
                });
                LIVE.add(display);
                return display;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void discard(BlockDisplay display) {
            if (display == null) {
                return;
            }
            LIVE.remove(display);
            if (display.isValid()) {
                display.remove();
            }
        }
    }
}
