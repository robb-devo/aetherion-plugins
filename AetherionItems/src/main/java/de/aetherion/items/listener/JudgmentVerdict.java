package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Judgment Staff (judgment_staff) — Verdict.
 * The staff lays a golden seal on the floor where the caster looks and binds it with a braided beam; four spectral
 * swords hang over the seal and circle it, and a halo crowns the caster. While the lock follows the look, the seal
 * tightens and burns from gold to crimson tile by tile like a clock running out. For the last beat a bell tolls three
 * times, the seal flickers white and a thin sentence line drops out of the sky. Then the verdict: a pillar of light
 * drives down onto the seal, the swords plunge into the floor around it, the seal bursts outward as a ring of light
 * and the gavel falls. Tracking, timing, radius, damage and knockback match the old beam.
 */
public final class JudgmentVerdict {

    private static final int SEEK = 50;
    private static final int PULSE = 12;
    private static final int FIRE = 8;
    private static final int LINGER = 14;
    private static final double RANGE = 28.0;
    private static final int SEAL = 12;
    private static final int SPOKES = 4;
    private static final int SWORDS = 4;
    private static final int[] TOLLS = {SEEK - PULSE + 1, SEEK - 6, SEEK - 1};
    private static final float PILLAR = 24f;
    private static final float SWORD_SCALE = 1.7f;
    /** Sword sprite tip, below the display origin once the blade points down. */
    private static final float SWORD_TIP = 0.64f * SWORD_SCALE;

    private static final Color GOLD = Color.fromRGB(255, 205, 90);
    private static final Color RADIANT = Color.fromRGB(255, 246, 215);
    private static final Color VERDICT = Color.fromRGB(215, 30, 45);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final BlockData GOLD_TILE = Material.GOLD_BLOCK.createBlockData();
    private static final BlockData RED_TILE = Material.RED_CONCRETE.createBlockData();
    private static final BlockData WHITE_TILE = Material.WHITE_CONCRETE.createBlockData();

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final World world;
    private final double boom;
    private final Runnable done;
    private final List<BlockDisplay> seal = new ArrayList<>();
    private final BlockData[] sealShown = new BlockData[SEAL];
    private final List<BlockDisplay> spokes = new ArrayList<>();
    private final List<ItemDisplay> swords = new ArrayList<>();
    private BlockDisplay pillarOuter;
    private BlockDisplay pillarCore;
    private Location focus;
    private double spin;
    private double radius = 2.7;
    private int tick;
    private boolean released;

    /** Plugin disable: removes every seal tile, sword and pillar still standing. */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    /** {@code done} runs once, when the beam would have released its lock before (fire tick 8). */
    static void cast(JavaPlugin plugin, Player player, double boom, Runnable done) {
        new JudgmentVerdict(plugin, player, boom, done).start();
    }

    private JudgmentVerdict(JavaPlugin plugin, Player player, double boom, Runnable done) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.boom = boom;
        this.done = done;
    }

    private void start() {
        focus = rayAim();
        for (int i = 0; i < SEAL; i++) {
            seal.add(spawnBlock(GOLD_TILE, null));
            sealShown[i] = GOLD_TILE;
        }
        for (int i = 0; i < SPOKES; i++) {
            spokes.add(spawnBlock(GOLD_TILE, null));
        }
        for (int i = 0; i < SWORDS; i++) {
            ItemDisplay sword = spawnSword();
            if (sword != null) {
                swords.add(sword);
            }
        }
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f);
        world.playSound(focus, Sound.BLOCK_BELL_RESONATE, 0.35f, 1.6f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                    release();
                    clearAll();
                    cancel();
                    return;
                }
                if (step()) {
                    clearAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean step() {
        tick++;
        Location from = staffTip();
        if (tick <= SEEK) {
            seek(from);
            return false;
        }
        int ft = tick - SEEK;
        if (ft == 1) {
            verdict(from);
        } else if (ft <= FIRE) {
            double k = (ft - 1) / (double) (FIRE - 1);
            drawBeam(from, focus, mix(VERDICT, RADIANT, k), (float) (2.0 - ft * 0.15), false);
        }
        aftermath(ft);
        if (ft >= FIRE) {
            release();
        }
        return ft >= FIRE + LINGER;
    }

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    // ------------------------------------------------------------------ lock

    private void seek(Location from) {
        focus = lerp(focus, rayAim(), 0.18);
        double t = tick / (double) SEEK;
        boolean pulsing = tick > SEEK - PULSE;
        Color color = mix(GOLD, VERDICT, Math.pow(t, 1.35));
        if (pulsing && tick % 2 == 0) {
            color = tick % 4 == 0 ? VERDICT : RADIANT;
        }
        drawBeam(from, focus, color, pulsing ? 1.9f : 1.0f + (float) t * 0.6f, tick % 2 == 0);
        poseSeal(t, pulsing);
        if (tick % 2 == 0) {
            halo(color);
        }
        if (pulsing && tick % 2 == 0) {
            sentence();
        }

        if (tick % 8 == 0) {
            world.playSound(focus, Sound.BLOCK_BEACON_AMBIENT, 0.35f, 0.8f + (float) t * 0.7f);
        }
        if (pulsing && tick % 3 == 0) {
            world.playSound(focus, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 0.8f + (float) (t - 0.76) * 3.0f);
        }
        for (int i = 0; i < TOLLS.length; i++) {
            if (tick == TOLLS[i]) {
                toll(i);
            }
        }
        if (tick == SEEK) {
            pillarOuter = spawnPillar(Material.YELLOW_STAINED_GLASS.createBlockData(), null, 2.6f);
            pillarCore = spawnPillar(WHITE_TILE, GOLD, 0.9f);
        }
    }

    private void toll(int index) {
        float pitch = 0.62f + index * 0.09f;
        world.playSound(focus, Sound.BLOCK_BELL_USE, 1.0f, pitch);
        if (player.getLocation().distanceSquared(focus) > 100.0) {
            world.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.5f, pitch);
        }
        if (index == TOLLS.length - 1) {
            world.playSound(focus, Sound.BLOCK_BELL_RESONATE, 0.7f, 0.9f);
        }
        radius += 0.25;
        drawRing(focus, radius + 0.4, 28, new Particle.DustOptions(RADIANT, 1.2f));
    }

    /**
     * Ring tiles tighten and turn crimson one after another; spokes counter-rotate; swords circle overhead and sink.
     * During the pulse the ring flickers white on the beat.
     */
    private void poseSeal(double t, boolean pulsing) {
        radius += ((2.7 - 0.7 * t) - radius) * 0.3;
        spin += 0.05 + 0.07 * t;
        float length = (float) (Math.PI * 2 * radius / SEAL * 0.62);
        for (int i = 0; i < seal.size(); i++) {
            BlockDisplay tile = seal.get(i);
            if (tile == null || !tile.isValid()) {
                continue;
            }
            BlockData want = t > 0.35 + 0.5 * i / SEAL ? RED_TILE : GOLD_TILE;
            if (pulsing && tick % 4 == 0) {
                want = WHITE_TILE;
            }
            if (want != sealShown[i]) {
                sealShown[i] = want;
                tile.setBlock(want);
            }
            tile.teleport(focus);
            ease(tile, tile(spin + Math.PI * 2 * i / SEAL, radius, length, 0.2f, 0.04f, 0.06f), 2);
        }
        BlockData spokeData = t > 0.85 ? RED_TILE : GOLD_TILE;
        for (int i = 0; i < spokes.size(); i++) {
            BlockDisplay spoke = spokes.get(i);
            if (spoke == null || !spoke.isValid()) {
                continue;
            }
            if (tick == 43) {
                spoke.setBlock(spokeData);
            }
            spoke.teleport(focus);
            ease(spoke, spoke(-spin * 1.3 + i * Math.PI / 2, radius * 0.82, 0.09f), 2);
        }
        double height = 6.2 - 2.6 * t + (pulsing ? 0.15 * Math.sin(tick * 1.3) : 0.0);
        for (int i = 0; i < swords.size(); i++) {
            ItemDisplay sword = swords.get(i);
            if (!sword.isValid()) {
                continue;
            }
            double a = -spin * 0.7 + i * Math.PI / 2 + Math.PI / 4;
            sword.teleport(focus);
            easeItem(sword, sword(a, radius * 0.72, (float) height), 2);
            if (tick % 3 == i % 3) {
                Location tip = focus.clone().add(Math.cos(a) * radius * 0.72, height - SWORD_TIP, Math.sin(a) * radius * 0.72);
                world.spawnParticle(Particle.DUST, tip, 1, 0, 0, 0, 0, new Particle.DustOptions(RADIANT, 0.7f));
            }
        }
    }

    private void halo(Color color) {
        Location crown = player.getEyeLocation().add(0, 0.62, 0);
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.7f);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + tick * 0.08;
            world.spawnParticle(Particle.DUST, crown.clone().add(Math.cos(a) * 0.38, 0, Math.sin(a) * 0.38), 1,
                    0, 0, 0, 0, dust);
        }
    }

    /** A thin line of light dropped out of the sky onto the seal: the sentence is about to be carried out. */
    private void sentence() {
        Particle.DustOptions dust = new Particle.DustOptions(RADIANT, 0.8f);
        for (double y = 0.4; y <= 14.0; y += 0.7) {
            world.spawnParticle(Particle.DUST, focus.clone().add(0, y, 0), 1, 0, 0, 0, 0, dust);
        }
        world.spawnParticle(Particle.END_ROD, focus.clone().add(0, 14.0, 0), 0, 0, -1, 0, 0.4);
    }

    // ------------------------------------------------------------------ verdict

    private void verdict(Location from) {
        drawBeam(from, focus, VERDICT, 2.8f, true);

        if (pillarOuter != null) {
            ease(pillarOuter, pillar(2.6f, PILLAR, -0.1f), 2);
        }
        if (pillarCore != null) {
            ease(pillarCore, pillar(0.9f, PILLAR, -0.1f), 2);
        }
        for (int i = 0; i < swords.size(); i++) {
            double a = -spin * 0.7 + i * Math.PI / 2 + Math.PI / 4;
            easeItem(swords.get(i), sword(a, 1.35, SWORD_TIP - 0.35f), 2);
        }
        float length = (float) (Math.PI * 2 * 6.5 / SEAL * 0.62 * 1.8);
        for (int i = 0; i < seal.size(); i++) {
            BlockDisplay tile = seal.get(i);
            if (tile == null || !tile.isValid()) {
                continue;
            }
            tile.setBlock(WHITE_TILE);
            tile.setGlowing(true);
            tile.setGlowColorOverride(GOLD);
            ease(tile, tile(spin + Math.PI * 2 * i / SEAL, 6.5, length, 0.3f, 0.02f, 0.06f), 5);
        }
        for (BlockDisplay spoke : spokes) {
            ease(spoke, spoke(0, 0.001, 0.001f), 2);
        }

        Location c = focus.clone().add(0, 0.3, 0);
        world.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLASH, c.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, 0.2, 0), 2, 0.6, 0.3, 0.6, 0);
        world.spawnParticle(Particle.END_ROD, c, 40, 0.8, 0.4, 0.8, 0.05);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, c, 40, 1.0, 0.3, 1.0, 0.55);
        world.spawnParticle(Particle.DUST, c, 30, 1.5, 0.4, 1.5, 0, new Particle.DustOptions(GOLD, 1.6f));
        world.spawnParticle(Particle.BLOCK, c, 50, 1.6, 0.2, 1.6, 0.2, floorData());

        world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 1.05f, 0.5f);
        world.playSound(focus, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        world.playSound(focus, Sound.BLOCK_BELL_USE, 1.3f, 0.5f);
        world.playSound(focus, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.6f);
        world.playSound(from, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.7f);

        for (Entity entity : world.getNearbyEntities(focus, 5.5, 4.5, 5.5)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            ScriptedHits.run(() -> living.damage(boom, player));
            Vector away = living.getLocation().toVector().subtract(focus.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(0, 1, 0);
            } else {
                away.normalize();
            }
            living.setVelocity(away.multiply(2.2).setY(1.05));
            Location head = living.getLocation().add(0, living.getHeight() + 0.2, 0);
            world.spawnParticle(Particle.END_ROD, head, 0, 0, 1, 0, 0.3);
            world.spawnParticle(Particle.DUST, head, 8, 0.25, 0.35, 0.25, 0, new Particle.DustOptions(VERDICT, 1.2f));
        }
        player.sendMessage("§6✦ Verdict §7delivered.");
    }

    private void aftermath(int ft) {
        if (ft >= 2 && ft <= 9) {
            double k = (ft - 1) / 8.0;
            double ease = 1.0 - Math.pow(1.0 - k, 3);
            drawRing(focus, 1.0 + ease * 5.5, 40, new Particle.DustOptions(mix(RADIANT, GOLD, k), (float) (1.6 - k * 0.7)));
        }
        if (ft >= 1 && ft <= 6) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 4; i++) {
                Location at = focus.clone().add(random.nextDouble(-0.9, 0.9), random.nextDouble(2.0, PILLAR),
                        random.nextDouble(-0.9, 0.9));
                world.spawnParticle(Particle.END_ROD, at, 0, 0, -1, 0, 0.6);
            }
        }
        if (ft == 3) {
            if (pillarOuter != null) {
                ease(pillarOuter, pillar(0.001f, PILLAR, -0.1f), 6);
            }
            if (pillarCore != null) {
                ease(pillarCore, pillar(0.001f, PILLAR, -0.1f), 6);
            }
        }
        if (ft == 6) {
            float length = (float) (Math.PI * 2 * 7.0 / SEAL * 0.62 * 1.8);
            for (int i = 0; i < seal.size(); i++) {
                ease(seal.get(i), tile(spin + Math.PI * 2 * i / SEAL, 7.0, length, 0.001f, 0.001f, 0.06f), 3);
            }
        }
        if (ft == 10) {
            removeAll(seal);
            removeAll(spokes);
            discard(pillarOuter);
            discard(pillarCore);
            pillarOuter = null;
            pillarCore = null;
        }
        if (ft > 2 && ft % 4 == 0) {
            for (int i = 0; i < swords.size(); i++) {
                double a = -spin * 0.7 + i * Math.PI / 2 + Math.PI / 4;
                Location hilt = focus.clone().add(Math.cos(a) * 1.35, SWORD_TIP * 1.6, Math.sin(a) * 1.35);
                world.spawnParticle(Particle.END_ROD, hilt, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        if (ft == FIRE + LINGER - 5) {
            for (int i = 0; i < swords.size(); i++) {
                double a = -spin * 0.7 + i * Math.PI / 2 + Math.PI / 4;
                Transformation sunk = sword(a, 1.35, -0.2f);
                easeItem(swords.get(i), new Transformation(sunk.getTranslation(), sunk.getLeftRotation(),
                        new Vector3f(0.001f, 0.001f, 0.001f), sunk.getRightRotation()), 5);
            }
            world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ aim + drawing

    private Location staffTip() {
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.6));
    }

    private Location rayAim() {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = world.rayTraceBlocks(eye, eye.getDirection(), RANGE);
        Location at;
        if (hit != null && hit.getHitPosition() != null) {
            at = hit.getHitPosition().toLocation(world).add(0, 0.2, 0);
        } else {
            at = eye.clone().add(eye.getDirection().normalize().multiply(RANGE));
        }
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    private static Location lerp(Location from, Location to, double t) {
        if (from == null) {
            return to.clone();
        }
        if (to == null || from.getWorld() != to.getWorld()) {
            return from.clone();
        }
        return from.clone().add(
                (to.getX() - from.getX()) * t,
                (to.getY() - from.getY()) * t,
                (to.getZ() - from.getZ()) * t
        );
    }

    private BlockData floorData() {
        Block block = focus.clone().add(0, -0.4, 0).getBlock();
        return block.getType().isSolid() ? block.getBlockData() : Material.QUARTZ_BLOCK.createBlockData();
    }

    /** Staff → seal: a colored core with two pale strands braided around it. */
    private void drawBeam(Location from, Location to, Color color, float size, boolean braid) {
        Vector delta = to.toVector().subtract(from.toVector());
        double len = delta.length();
        if (len < 0.1) {
            return;
        }
        Vector dir = delta.multiply(1.0 / len);
        Vector side = dir.getCrossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-4) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        Vector up = side.getCrossProduct(dir).normalize();
        Particle.DustOptions core = new Particle.DustOptions(color, size);
        Particle.DustOptions strand = new Particle.DustOptions(RADIANT, 0.55f);
        int steps = Math.min(56, (int) (len / 0.5));
        for (int i = 1; i <= steps; i++) {
            double d = i * 0.5;
            Location p = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, core);
            if (braid) {
                double a = d * 1.9 - tick * 0.45;
                for (int s = 0; s < 2; s++) {
                    double ang = a + s * Math.PI;
                    Location q = p.clone().add(side.clone().multiply(Math.cos(ang) * 0.2))
                            .add(up.clone().multiply(Math.sin(ang) * 0.2));
                    world.spawnParticle(Particle.DUST, q, 1, 0, 0, 0, 0, strand);
                }
            }
            if (i % 7 == 0) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    private void drawRing(Location center, double r, int points, Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * r, 0.08, Math.sin(a) * r), 1,
                    0, 0, 0, 0, dust);
        }
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    // ------------------------------------------------------------------ transforms

    /** A flat ring tile at {@code angle}, long side along the ring. */
    private static Transformation tile(double angle, double r, float length, float width, float thick, float y) {
        Quaternionf rot = new Quaternionf().rotateY((float) -angle);
        Vector3f center = new Vector3f((float) (Math.cos(angle) * r), y, (float) (Math.sin(angle) * r));
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(width / 2f, thick / 2f, length / 2f));
        return new Transformation(center.sub(half), rot, new Vector3f(width, thick, length), new Quaternionf());
    }

    /** A flat spoke from the seal's center out to {@code r} at {@code angle}. */
    private static Transformation spoke(double angle, double r, float width) {
        Quaternionf rot = new Quaternionf().rotateY((float) (Math.PI / 2 - angle));
        Vector3f off = new Quaternionf(rot).transform(new Vector3f(-width / 2f, 0f, 0f)).add(0f, 0.07f, 0f);
        return new Transformation(off, rot, new Vector3f(width, 0.03f, (float) Math.max(0.001, r)), new Quaternionf());
    }

    /** A blade-down sword at {@code angle} around the seal, flat side facing out, centered {@code height} up. */
    private static Transformation sword(double angle, double r, float height) {
        Quaternionf rot = new Quaternionf().rotateY((float) (Math.PI / 2 - angle)).rotateZ((float) (-Math.PI * 0.75));
        Vector3f at = new Vector3f((float) (Math.cos(angle) * r), height, (float) (Math.sin(angle) * r));
        return new Transformation(at, rot, new Vector3f(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE), new Quaternionf());
    }

    /** A column of light standing on the seal; {@code height} 0 parks it, invisible, at the top. */
    private static Transformation pillar(float width, float height, float base) {
        float w = Math.max(0.001f, width);
        float h = Math.max(0.01f, height);
        float y = height <= 0.01f ? PILLAR : base;
        return new Transformation(new Vector3f(-w / 2f, y, -w / 2f), new Quaternionf(), new Vector3f(w, h, w),
                new Quaternionf());
    }

    // ------------------------------------------------------------------ displays

    private BlockDisplay spawnBlock(BlockData data, Color glow) {
        try {
            BlockDisplay display = world.spawn(focus, BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setTeleportDuration(2);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private BlockDisplay spawnPillar(BlockData data, Color glow, float width) {
        BlockDisplay display = spawnBlock(data, glow);
        if (display != null) {
            display.setTransformation(pillar(width, 0f, 0f));
        }
        return display;
    }

    private ItemDisplay spawnSword() {
        try {
            ItemDisplay display = world.spawn(focus, ItemDisplay.class, spawned -> {
                spawned.setItemStack(new ItemStack(Material.GOLDEN_SWORD));
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setTeleportDuration(2);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(GOLD);
                spawned.setTransformation(new Transformation(new Vector3f(0f, 6.2f, 0f), new Quaternionf(),
                        new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ease(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static void easeItem(ItemDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private void clearAll() {
        removeAll(seal);
        removeAll(spokes);
        for (ItemDisplay sword : swords) {
            discard(sword);
        }
        swords.clear();
        discard(pillarOuter);
        discard(pillarCore);
        pillarOuter = null;
        pillarCore = null;
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
}
