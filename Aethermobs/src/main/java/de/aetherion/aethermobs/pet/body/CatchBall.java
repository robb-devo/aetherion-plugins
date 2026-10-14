package de.aetherion.aethermobs.pet.body;

import de.aetherion.core.AetherKeys;

import io.papermc.paper.entity.TeleportFlag;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The catch sphere as a real object: two shells (hinged lid) riding one root, a tiny physics loop for the
 * drop, bounces and settle, and the beats the ritual plays on it — open, absorb, snap shut, rock, shake,
 * click, burst. Odds and timing live in the ritual; this class only makes it look like a ball.
 *
 * <p>Models come from the Items resource pack (snowball CMD 3601-3611). Without the pack the shells fall back
 * to snowball sprites, which still read as a ball.
 */
public final class CatchBall {

    /** Whole sphere per tier (thrown projectile + miss bounce): common, rare, epic, legendary, beta. */
    public static final int CMD_WHOLE = 3601;
    public static final int CMD_BOTTOM = 3606;
    /** Lid per tier: 3607..3611. */
    public static final int CMD_TOP = 3607;

    private static final float SCALE = 0.62f;
    /** Model radius in blocks at scale 1 (11 px sphere). */
    private static final float RADIUS = 5.5f / 16f;
    private static final double GRAVITY = 0.055;

    private static final Set<CatchBall> LIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Plugin plugin;
    private final ItemDisplay root;
    private final ItemDisplay bottom;
    private final ItemDisplay top;
    private final Color tint;
    private final String sphereId;

    private final Vector velocity = new Vector();
    private boolean physics;
    private boolean resting;
    private boolean hovering;
    private double floorY = Double.NaN;
    private int bounces;

    private float lid;
    private float rockX;
    private float rockZ;
    private float spin;
    private float shrink = 1f;
    private BukkitRunnable loop;
    private boolean removed;

    private CatchBall(Plugin plugin, ItemDisplay root, ItemDisplay bottom, ItemDisplay top, Color tint, String sphereId) {
        this.plugin = plugin;
        this.root = root;
        this.bottom = bottom;
        this.top = top;
        this.tint = tint;
        this.sphereId = sphereId;
    }

    public static int tierIndex(String sphereId) {
        if (sphereId == null) {
            return 0;
        }
        return switch (sphereId.toLowerCase(Locale.ROOT)) {
            case "rare" -> 1;
            case "epic" -> 2;
            case "legendary" -> 3;
            case "beta" -> 4;
            default -> 0;
        };
    }

    public static Color tierColor(String sphereId) {
        return switch (tierIndex(sphereId)) {
            case 1 -> Color.fromRGB(90, 150, 255);
            case 2 -> Color.fromRGB(190, 90, 255);
            case 3 -> Color.fromRGB(255, 190, 40);
            case 4 -> Color.fromRGB(255, 90, 220);
            default -> Color.fromRGB(120, 230, 255);
        };
    }

    public static ItemStack model(int cmd) {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The whole-sphere look for a thrown projectile. */
    public static ItemStack thrownLook(String sphereId) {
        return model(CMD_WHOLE + tierIndex(sphereId));
    }

    public static CatchBall spawn(Plugin plugin, Location at, String sphereId) {
        World world = at.getWorld();
        Location spot = at.clone();
        spot.setYaw(0f);
        spot.setPitch(0f);
        int tier = tierIndex(sphereId);
        ItemDisplay root = world.spawn(spot, ItemDisplay.class, d -> {
            mark(d);
            d.setTeleportDuration(2);
            d.setShadowRadius(0.22f);
            d.setShadowStrength(0.85f);
        });
        ItemDisplay bottom = world.spawn(spot, ItemDisplay.class, d -> {
            mark(d);
            d.setItemStack(model(CMD_BOTTOM));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformationMatrix(new Matrix4f().scale(0.001f));
        });
        ItemDisplay top = world.spawn(spot, ItemDisplay.class, d -> {
            mark(d);
            d.setItemStack(model(CMD_TOP + tier));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformationMatrix(new Matrix4f().scale(0.001f));
        });
        root.addPassenger(bottom);
        root.addPassenger(top);
        CatchBall ball = new CatchBall(plugin, root, bottom, top, tierColor(sphereId), sphereId);
        LIVE.add(ball);
        ball.pose(1);
        ball.start();
        return ball;
    }

    private static void mark(Display d) {
        d.setPersistent(false);
        d.setSilent(true);
        d.setGravity(false);
        d.setInvulnerable(true);
        d.setViewRange(0.8f);
        d.setBillboard(Display.Billboard.FIXED);
        d.getPersistentDataContainer().set(AetherKeys.PET_ENTITY, PersistentDataType.BYTE, (byte) 1);
    }

    public static void removeAll() {
        for (CatchBall ball : LIVE.toArray(new CatchBall[0])) {
            ball.remove();
        }
        LIVE.clear();
    }

    public Location location() {
        return root.getLocation();
    }

    /** Centre of the sphere in the world. */
    public Location center() {
        return root.getLocation();
    }

    public Color tint() {
        return tint;
    }

    public boolean isResting() {
        return resting;
    }

    public boolean isValid() {
        return !removed && root.isValid();
    }

    /*
     * =========================================================
     * POSE
     * =========================================================
     */

    private Matrix4f base() {
        float s = SCALE * shrink;
        float contact = -RADIUS * s;
        return new Matrix4f()
                .translate(0f, contact, 0f)
                .rotateX((float) Math.toRadians(rockX))
                .rotateZ((float) Math.toRadians(rockZ))
                .translate(0f, -contact, 0f)
                .rotateY((float) Math.toRadians(spin));
    }

    private void pose(int ticks) {
        if (!isValid()) {
            return;
        }
        float s = SCALE * Math.max(0.001f, shrink);
        Matrix4f b = base();
        Matrix4f bot = new Matrix4f(b).scale(s);
        float hinge = -RADIUS * s;
        Matrix4f lidM = new Matrix4f(b)
                .translate(0f, 0f, hinge)
                .rotateX((float) Math.toRadians(-lid * 100f))
                .translate(0f, 0f, -hinge)
                .scale(s);
        write(bottom, bot, ticks);
        write(top, lidM, ticks);
    }

    private static void write(ItemDisplay d, Matrix4f m, int ticks) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, ticks));
        d.setTransformationMatrix(m);
    }

    public void lid(float open, int ticks) {
        this.lid = Math.max(0f, Math.min(1.1f, open));
        pose(ticks);
    }

    public void rock(float x, float z, int ticks) {
        this.rockX = x;
        this.rockZ = z;
        pose(ticks);
    }

    public void glow(Color color) {
        for (ItemDisplay d : new ItemDisplay[]{bottom, top}) {
            if (d.isValid()) {
                d.setGlowing(color != null);
                d.setGlowColorOverride(color);
                d.setBrightness(color != null ? new Display.Brightness(15, 15) : null);
            }
        }
    }

    /** A short glint: glow for {@code ticks}, then back to plain. */
    public void flash(Color color, int ticks) {
        glow(color);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isValid()) {
                    glow(null);
                }
            }
        }.runTaskLater(plugin, Math.max(1, ticks));
    }

    /*
     * =========================================================
     * PHYSICS
     * =========================================================
     */

    /** Toss: the ball pops off the target and falls, bounces and settles on its own. */
    public void toss(Vector initial) {
        velocity.copy(initial);
        physics = true;
        resting = false;
        bounces = 0;
        floorY = Double.NaN;
    }

    /** Freeze in mid-air (the lid opens at the top of the bounce). */
    public void hold() {
        physics = false;
        hovering = false;
        velocity.zero();
    }

    private void start() {
        loop = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) {
                    cancel();
                    return;
                }
                if (physics) {
                    step();
                } else if (hovering) {
                    Location at = root.getLocation();
                    at.setY(at.getY() + Math.sin(root.getTicksLived() * 0.15) * 0.008);
                    root.teleport(at, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                }
            }
        };
        loop.runTaskTimer(plugin, 1L, 1L);
    }

    private void step() {
        Location at = root.getLocation();
        World world = at.getWorld();
        float radius = RADIUS * SCALE * shrink;
        if (Double.isNaN(floorY) || velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 0.0004) {
            floorY = floorBelow(at, 6);
        }
        Block inside = at.getBlock();
        if (inside.getType() == Material.WATER) {
            // Spheres float: drift up to the surface and bob.
            velocity.multiply(0.6);
            velocity.setY(Math.min(0.06, velocity.getY() + 0.02));
            if (inside.getRelative(0, 1, 0).getType() != Material.WATER && at.getY() - inside.getY() > 0.75) {
                velocity.setY(0.0);
                settle();
            }
        } else if (Double.isNaN(floorY)) {
            // No ground in reach (sky catch): hang in the air where it is.
            velocity.multiply(0.5);
            if (Math.abs(velocity.getY()) < 0.03) {
                hovering = true;
                settle();
                return;
            }
            velocity.setY(velocity.getY() - GRAVITY * 0.5);
        } else {
            velocity.setY(velocity.getY() - GRAVITY);
        }
        Location next = at.clone().add(velocity);
        // Walls bounce the sphere back instead of letting it sink into them.
        if (!at.clone().add(velocity.getX(), 0.0, velocity.getZ()).getBlock().isPassable()) {
            velocity.setX(-velocity.getX() * 0.3);
            velocity.setZ(-velocity.getZ() * 0.3);
            next = at.clone().add(0.0, velocity.getY(), 0.0);
        }
        if (!Double.isNaN(floorY) && next.getY() - radius <= floorY) {
            next.setY(floorY + radius);
            double impact = -velocity.getY();
            velocity.setY(impact * 0.42);
            velocity.setX(velocity.getX() * 0.55);
            velocity.setZ(velocity.getZ() * 0.55);
            bounces++;
            if (impact > 0.08) {
                world.playSound(next, Sound.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.NEUTRAL, 0.55f, 1.7f - bounces * 0.1f);
                world.playSound(next, Sound.BLOCK_AMETHYST_BLOCK_STEP, SoundCategory.NEUTRAL, 0.5f, 1.3f);
                world.spawnParticle(Particle.DUST, next.clone().add(0, -radius, 0), 3, 0.1, 0.0, 0.1, 0.0,
                        new Particle.DustOptions(tint, 0.6f));
            }
            if (velocity.getY() < 0.05 || bounces >= 4) {
                velocity.zero();
                root.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                rock(0f, 0f, 3);
                settle();
                return;
            }
        }
        // Roll a little while moving.
        double horizontal = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        if (horizontal > 0.01) {
            spin += (float) (horizontal * 220.0);
            pose(2);
        }
        root.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    private void settle() {
        physics = false;
        resting = true;
    }

    /** Top of the first solid block within {@code depth} below, or NaN. */
    private static double floorBelow(Location at, int depth) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int y = at.getBlockY();
        for (int i = 0; i <= depth; i++) {
            Block block = world.getBlockAt(x, y - i, z);
            if (block.getType() == Material.WATER) {
                return Double.NaN;
            }
            if (!block.isPassable()) {
                double top = block.getBoundingBox().getMaxY();
                if (top <= block.getY()) {
                    top = block.getY() + 1.0;
                }
                if (top <= at.getY() + 0.6) {
                    return top;
                }
            }
        }
        return Double.NaN;
    }

    /*
     * =========================================================
     * FINALES
     * =========================================================
     */

    /** The click: seal flash, star burst, then the sphere flies home to the thrower. */
    public void seal(Color petColor, org.bukkit.entity.Player thrower) {
        if (!isValid()) {
            return;
        }
        Location at = center();
        World world = at.getWorld();
        glow(petColor);
        lid(0f, 1);
        rock(0f, 0f, 2);
        world.playSound(at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.NEUTRAL, 0.8f, 1.9f);
        world.spawnParticle(Particle.END_ROD, at, 10, 0.05, 0.05, 0.05, 0.09);
        world.spawnParticle(Particle.WAX_ON, at, 8, 0.25, 0.2, 0.25, 0.0);
        Particle.DustOptions dust = new Particle.DustOptions(petColor, 1.1f);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2.0 * i / 12.0;
            Vector out = new Vector(Math.cos(a), 0.35, Math.sin(a));
            world.spawnParticle(Particle.DUST, at.clone().add(out.multiply(0.5)), 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
        new BukkitRunnable() {
            int tick;
            Location from;

            @Override
            public void run() {
                if (!isValid()) {
                    cancel();
                    return;
                }
                tick++;
                if (tick == 12) {
                    glow(null);
                    from = center();
                }
                if (tick < 12) {
                    if (tick % 4 == 0) {
                        rock(0f, tick % 8 == 0 ? 6f : -6f, 2);
                    }
                    return;
                }
                int fly = tick - 12;
                if (thrower == null || !thrower.isOnline() || !thrower.getWorld().equals(from.getWorld()) || fly > 12) {
                    Location end = center();
                    end.getWorld().spawnParticle(Particle.END_ROD, end, 4, 0.05, 0.05, 0.05, 0.03);
                    if (thrower != null && thrower.isOnline()) {
                        thrower.playSound(thrower.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.9f);
                    }
                    remove();
                    cancel();
                    return;
                }
                double k = fly / 12.0;
                Location target = thrower.getLocation().add(0, 1.1, 0);
                Vector path = target.toVector().subtract(from.toVector());
                Location p = from.clone().add(path.multiply(k));
                p.add(0, Math.sin(k * Math.PI) * 1.2, 0);
                shrink = (float) (1.0 - k * 0.7);
                spin += 40f;
                pose(1);
                root.teleport(p, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                if (fly % 2 == 0) {
                    p.getWorld().spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(tint, 0.7f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** The break: violent shake, lid blows open, shards, then the shell fades. */
    public void burst(Runnable onOpen) {
        if (!isValid()) {
            if (onOpen != null) {
                onOpen.run();
            }
            return;
        }
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!isValid()) {
                    if (onOpen != null && tick < 5) {
                        onOpen.run();
                    }
                    cancel();
                    return;
                }
                tick++;
                if (tick <= 4) {
                    rock(tick % 2 == 0 ? 10f : -10f, tick % 2 == 0 ? -26f : 26f, 1);
                    return;
                }
                if (tick == 5) {
                    Location at = center();
                    World world = at.getWorld();
                    lid(1.1f, 1);
                    rock(0f, 0f, 2);
                    glow(Color.WHITE);
                    world.playSound(at, Sound.BLOCK_GLASS_BREAK, SoundCategory.NEUTRAL, 0.7f, 1.45f);
                    world.playSound(at, Sound.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.NEUTRAL, 0.7f, 1.3f);
                    world.spawnParticle(Particle.ITEM, at, 10, 0.12, 0.12, 0.12, 0.12, thrownLook(sphereId));
                    world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0);
                    world.spawnParticle(Particle.POOF, at, 4, 0.15, 0.15, 0.15, 0.02);
                    if (onOpen != null) {
                        onOpen.run();
                    }
                    return;
                }
                if (tick == 7) {
                    glow(null);
                }
                shrink = Math.max(0.001f, 1f - (tick - 5) / 8f);
                pose(1);
                if (tick >= 13) {
                    remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A missed throw: ricochet off what it hit, bounce, roll, then dissolve into sparkles. */
    public static void miss(Plugin plugin, Location at, Vector incoming, String sphereId,
                            org.bukkit.block.BlockFace face) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        Vector v = incoming == null ? new Vector() : incoming.clone();
        Location start = at.clone();
        if (face != null && face != org.bukkit.block.BlockFace.SELF) {
            Vector n = face.getDirection();
            v.subtract(n.clone().multiply(2.0 * v.dot(n)));
            start.add(n.multiply(0.22));
        }
        v.multiply(0.3);
        v.setY(Math.max(0.1, v.getY()));
        CatchBall ball = spawn(plugin, start, sphereId);
        ball.toss(v);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick++;
                if (!ball.isValid()) {
                    cancel();
                    return;
                }
                if (tick == 26) {
                    Location c = ball.center();
                    c.getWorld().spawnParticle(Particle.DUST, c, 6, 0.12, 0.12, 0.12, 0.0,
                            new Particle.DustOptions(ball.tint, 0.8f));
                    c.getWorld().spawnParticle(Particle.END_ROD, c, 2, 0.05, 0.05, 0.05, 0.02);
                    c.getWorld().playSound(c, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 0.35f, 1.8f);
                }
                if (tick > 26) {
                    ball.shrink = Math.max(0.001f, 1f - (tick - 26) / 6f);
                    ball.pose(1);
                }
                if (tick >= 33) {
                    ball.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        LIVE.remove(this);
        if (loop != null) {
            try {
                loop.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
        for (Entity e : new Entity[]{top, bottom, root}) {
            try {
                e.remove();
            } catch (Throwable ignored) {
            }
        }
    }
}
