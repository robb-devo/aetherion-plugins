package de.aetherion.core.exile;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Caedmon's sword in the crater, as a prop: block displays instead of the old block build that stood in the middle
 * of the Hollow Sun's arena (it was in the way and its top was taken for the arena floor). The vial wakes it:
 * the blade trembles, tears itself out of the ground, pushes everyone back and climbs into the sky until it flares
 * into the star that comes down as the Hollow Sun. When the fight is over it falls back and plants itself again.
 * Nothing persistent: the displays are rebuilt when the chunk loads, and only while the sword is planted.
 */
final class HollowSword {

    enum State { PLANTED, RISING, AWAY, FALLING }

    private record Part(BlockData block, float x0, float y0, float x1, float y1, float depth) {
    }

    /* The old build, slimmer: blade sunk a little into the ground, gold guard, blackstone grip, gold pommel. */
    private static final List<Part> PARTS = List.of(
            new Part(Material.IRON_BLOCK.createBlockData(), -0.70f, -0.6f, 0.70f, 6.0f, 0.30f),
            new Part(Material.SMOOTH_STONE.createBlockData(), -0.18f, 0.2f, 0.18f, 5.7f, 0.34f),
            new Part(Material.GOLD_BLOCK.createBlockData(), -2.60f, 6.0f, 2.60f, 6.6f, 0.55f),
            new Part(Material.RAW_GOLD_BLOCK.createBlockData(), -3.20f, 5.95f, -2.50f, 6.65f, 0.70f),
            new Part(Material.RAW_GOLD_BLOCK.createBlockData(), 2.50f, 5.95f, 3.20f, 6.65f, 0.70f),
            new Part(Material.POLISHED_BLACKSTONE.createBlockData(), -0.24f, 6.6f, 0.24f, 9.4f, 0.48f),
            new Part(Material.GOLD_BLOCK.createBlockData(), -0.45f, 9.4f, 0.45f, 10.3f, 0.90f)
    );

    static final float HEIGHT = 10.3f;
    private static final double RISE = 46.0;
    private static final int TREMBLE = 24;
    private static final int CLIMB = 46;

    private final Plugin plugin;
    private final Location base;
    private final Consumer<Entity> mark;
    private final List<BlockDisplay> parts = new ArrayList<>();
    private State state = State.PLANTED;
    private long goneSince;

    HollowSword(Plugin plugin, Location base, Consumer<Entity> mark) {
        this.plugin = plugin;
        this.base = base.clone();
        this.mark = mark;
    }

    State state() {
        return state;
    }

    /** Chunk load / boot: the planted sword stands; while it is up there, nothing does. */
    void build() {
        clear();
        if (state == State.PLANTED) {
            spawn(base);
            pose(base, new Quaternionf(), 0);
        }
    }

    void clear() {
        for (BlockDisplay d : parts) {
            if (d.isValid()) {
                d.remove();
            }
        }
        parts.clear();
    }

    private void spawn(Location at) {
        World w = at.getWorld();
        for (Part p : PARTS) {
            BlockDisplay d = w.spawn(at, BlockDisplay.class, e -> {
                mark.accept(e);
                e.setBlock(p.block());
                e.setTeleportDuration(2);
                e.setViewRange(1.6f);
                e.setShadowRadius(0f);
                e.setGlowColorOverride(Color.fromRGB(255, 190, 70));
            });
            parts.add(d);
        }
    }

    /** Every part turned by {@code q} around the blade's ground point, the rig standing at {@code at}. */
    private void pose(Location at, Quaternionf q, int interp) {
        for (int i = 0; i < parts.size() && i < PARTS.size(); i++) {
            BlockDisplay d = parts.get(i);
            Part p = PARTS.get(i);
            float sx = p.x1() - p.x0();
            float sy = p.y1() - p.y0();
            Vector3f t = new Vector3f(p.x0(), p.y0(), -p.depth() / 2f);
            q.transform(t);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformation(new Transformation(t, new Quaternionf(q), new Vector3f(sx, sy, p.depth()), new Quaternionf()));
            if (!d.getLocation().equals(at)) {
                d.teleport(at);
            }
        }
    }

    private void glow(boolean on) {
        for (BlockDisplay d : parts) {
            d.setGlowing(on);
            d.setBrightness(on ? new Display.Brightness(15, 15) : null);
        }
    }

    /**
     * The vial took: tremble, rip free, push the crowd back, climb and flare. {@code onTop} runs at the flare,
     * when the sword is gone (that is when the boss is called).
     */
    void rise(Runnable onTop) {
        if (state != State.PLANTED) {
            return;
        }
        state = State.RISING;
        if (parts.isEmpty()) {
            spawn(base);
        }
        World w = base.getWorld();
        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int k = t[0]++;
            if (parts.isEmpty() || !parts.get(0).isValid()) {
                // chunk went away mid-show: skip to the flare
                task.cancel();
                clear();
                state = State.AWAY;
                goneSince = System.currentTimeMillis();
                onTop.run();
                return;
            }
            if (k < TREMBLE) {
                if (k % 2 == 0) {
                    float a = (float) Math.toRadians(1.5 + k * 0.12);
                    Quaternionf q = new Quaternionf().rotateZ((k % 4 == 0 ? a : -a)).rotateX((k % 8 < 4 ? a : -a) * 0.6f);
                    pose(base, q, 2);
                    w.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.1, 0), 10, 0.8, 0.05, 0.4, 0,
                            Material.COARSE_DIRT.createBlockData());
                }
                if (k % 6 == 0) {
                    w.playSound(base, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.HOSTILE, 0.8f, 0.4f + k * 0.01f);
                }
                if (k == 10) {
                    glow(true);
                    w.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.0f, 0.6f);
                }
                return;
            }
            if (k == TREMBLE) {
                w.playSound(base, Sound.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.HOSTILE, 1.2f, 0.6f);
                w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.6f, 0.7f);
                w.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 0.5, 0), 3, 0.6, 0.2, 0.6, 0);
                w.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.2, 0), 60, 1.6, 0.2, 1.6, 0.2,
                        Material.COARSE_DIRT.createBlockData());
                shove(w);
            }
            int c = k - TREMBLE;
            if (c <= CLIMB) {
                double p = c / (double) CLIMB;
                double y = RISE * p * p * p;
                Location at = base.clone().add(0, y, 0);
                float spin = (float) (p * p * Math.PI * 6);
                pose(at, new Quaternionf().rotateY(spin), 2);
                if (k % 2 == 0) {
                    w.spawnParticle(Particle.FLAME, at.clone().add(0, 1.0, 0), 6, 0.25, 0.8, 0.25, 0.01);
                    w.spawnParticle(Particle.END_ROD, at.clone().add(0, 4.0, 0), 2, 0.2, 2.0, 0.2, 0.0);
                }
                if (c % 10 == 0) {
                    w.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.0f, 0.6f + (float) p);
                }
                return;
            }
            task.cancel();
            Location top = base.clone().add(0, RISE + HEIGHT / 2.0, 0);
            w.spawnParticle(Particle.FLASH, top, 2, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, top, 40, 0.4, 0.4, 0.4, 0.25);
            w.playSound(top, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 0.7f, 1.4f);
            clear();
            state = State.AWAY;
            goneSince = System.currentTimeMillis();
            onTop.run();
        }, 1L, 1L);
    }

    /** The blade tearing free throws everyone near it back to the crater floor's edge of the blast. */
    private void shove(World w) {
        for (Player p : w.getPlayers()) {
            Vector d = p.getLocation().toVector().subtract(base.toVector());
            double dy = d.getY();
            d.setY(0);
            double r = d.length();
            if (r > 9 || dy < -3 || dy > 8) {
                continue;
            }
            Vector push = r < 0.3 ? new Vector(1, 0, 0) : d.normalize();
            p.setVelocity(push.multiply(1.25 - r * 0.06).setY(0.5));
        }
    }

    /**
     * Called every second with "is the Hollow Sun awake". A boss woken some other way (command, restart) takes the
     * sword too; once the fight has been over for a few seconds it comes back down.
     */
    void tick(BooleanSupplier awake) {
        switch (state) {
            case PLANTED -> {
                if (awake.getAsBoolean()) {
                    clear();
                    state = State.AWAY;
                    goneSince = System.currentTimeMillis();
                }
            }
            case AWAY -> {
                if (awake.getAsBoolean()) {
                    goneSince = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - goneSince > 6000L && chunkLoaded()) {
                    fall();
                }
            }
            default -> {
            }
        }
    }

    private boolean chunkLoaded() {
        return base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4);
    }

    private void fall() {
        state = State.FALLING;
        World w = base.getWorld();
        Location start = base.clone().add(0, 38, 0);
        spawn(start);
        glow(true);
        pose(start, new Quaternionf(), 0);
        w.playSound(base, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 0.6f, 1.6f);
        int[] t = {0};
        int ticks = 14;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int k = ++t[0];
            if (parts.isEmpty() || !parts.get(0).isValid()) {
                task.cancel();
                clear();
                state = State.PLANTED;
                return;
            }
            if (k < ticks) {
                double p = k / (double) ticks;
                Location at = base.clone().add(0, 38 * (1 - p * p), 0);
                pose(at, new Quaternionf(), 1);
                w.spawnParticle(Particle.END_ROD, at.clone().add(0, HEIGHT, 0), 1, 0.1, 0.4, 0.1, 0);
                return;
            }
            task.cancel();
            pose(base, new Quaternionf(), 1);
            glow(false);
            state = State.PLANTED;
            w.playSound(base, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.0f, 0.5f);
            w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.4f, 1.2f);
            w.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.2, 0), 50, 1.4, 0.2, 1.4, 0.15,
                    Material.COARSE_DIRT.createBlockData());
            w.spawnParticle(Particle.FLAME, base.clone().add(0, 0.4, 0), 20, 1.2, 0.1, 1.2, 0.02);
        }, 1L, 1L);
    }
}
