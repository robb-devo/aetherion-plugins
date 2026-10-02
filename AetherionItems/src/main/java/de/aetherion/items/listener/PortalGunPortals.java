package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Portal Gun (portal_gun) — clean dual-gate utility.
 * Left-click places the azure gate, right-click the amber gate. Walk through either to exit the other.
 * Frame + surface are BlockDisplays only; almost no particles.
 */
public final class PortalGunPortals implements Listener {

    private static final String ID = "portal_gun";
    private static final int FRAME_SEGS = 28;
    private static final float PORTAL_W = 1.15f;
    private static final float PORTAL_H = 2.05f;
    private static final float FRAME_T = 0.11f;
    private static final double PLACE_RANGE = 24.0;
    private static final long TELEPORT_COOLDOWN_MS = 450L;

    private static final Color AZURE = Color.fromRGB(80, 190, 255);
    private static final Color AMBER = Color.fromRGB(255, 150, 40);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    private static PortalGunPortals instance;

    private final JavaPlugin plugin;
    private final Map<UUID, Pair> pairs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportCd = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public PortalGunPortals(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public static void shutdown() {
        if (instance != null) {
            if (instance.ticker != null) {
                instance.ticker.cancel();
                instance.ticker = null;
            }
            for (Pair pair : instance.pairs.values()) {
                pair.clear();
            }
            instance.pairs.clear();
            instance = null;
        }
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!isPortalGun(stack)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        place(player, left ? Channel.AZURE : Channel.AMBER);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && Math.abs(event.getFrom().getYaw() - event.getTo().getYaw()) < 0.01
                && Math.abs(event.getFrom().getPitch() - event.getTo().getPitch()) < 0.01) {
            return;
        }
        tryTeleport(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Pair pair = pairs.remove(event.getPlayer().getUniqueId());
        if (pair != null) {
            pair.clear();
        }
        teleportCd.remove(event.getPlayer().getUniqueId());
    }

    private void place(Player player, Channel channel) {
        Aim aim = aim(player);
        if (aim == null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§7No surface in range."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.6f);
            return;
        }
        Pair pair = pairs.computeIfAbsent(player.getUniqueId(), id -> new Pair());
        Gate gate = channel == Channel.AZURE ? pair.azure : pair.amber;
        if (gate != null) {
            gate.clear();
        }
        gate = new Gate(aim.center, aim.normal, aim.right, aim.up, channel);
        gate.spawn();
        if (channel == Channel.AZURE) {
            pair.azure = gate;
            player.sendActionBar(net.kyori.adventure.text.Component.text("§bAzure gate set"));
            player.playSound(aim.center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.7f);
            player.playSound(aim.center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.6f);
        } else {
            pair.amber = gate;
            player.sendActionBar(net.kyori.adventure.text.Component.text("§6Amber gate set"));
            player.playSound(aim.center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.15f);
            player.playSound(aim.center, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 0.85f);
        }
        if (pair.azure != null && pair.amber != null) {
            player.sendMessage("§7Gates linked.");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.8f);
        }
    }

    private void tryTeleport(Player player, Location at) {
        Pair pair = pairs.get(player.getUniqueId());
        if (pair == null || pair.azure == null || pair.amber == null) {
            return;
        }
        if (!pair.azure.ready || !pair.amber.ready) {
            return;
        }
        Long next = teleportCd.get(player.getUniqueId());
        if (next != null && System.currentTimeMillis() < next) {
            return;
        }
        Gate from = null;
        Gate to = null;
        if (pair.azure.contains(at)) {
            from = pair.azure;
            to = pair.amber;
        } else if (pair.amber.contains(at)) {
            from = pair.amber;
            to = pair.azure;
        }
        if (from == null || to == null || from.world != at.getWorld() || to.world != at.getWorld()) {
            return;
        }
        teleportCd.put(player.getUniqueId(), System.currentTimeMillis() + TELEPORT_COOLDOWN_MS);

        Vector vel = player.getVelocity();
        Vector local = toLocal(from, vel);
        /* Exit facing out of the destination gate; invert the through-axis so you keep forward momentum. */
        Vector outVel = fromLocal(to, new Vector(local.getX(), local.getY(), -local.getZ()));
        if (outVel.lengthSquared() < 0.04) {
            outVel = to.normal.clone().multiply(0.45);
        }

        Location dest = to.center.clone().add(to.normal.clone().multiply(0.85));
        dest.setDirection(to.normal);
        dest.setPitch(Math.max(-35f, Math.min(35f, player.getLocation().getPitch())));
        player.teleport(dest);
        player.setVelocity(outVel);
        player.setFallDistance(0f);
        player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.45f);
        player.playSound(dest, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, from.channel == Channel.AZURE ? 1.7f : 1.1f);
    }

    private void tickAll() {
        for (Pair pair : pairs.values()) {
            if (pair.azure != null) {
                pair.azure.tick();
            }
            if (pair.amber != null) {
                pair.amber.tick();
            }
        }
    }

    private static Aim aim(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, PLACE_RANGE, FluidCollisionMode.NEVER, true);
        Location center;
        Vector normal;
        if (hit != null && hit.getHitPosition() != null && hit.getHitBlockFace() != null) {
            center = hit.getHitPosition().toLocation(world);
            normal = hit.getHitBlockFace().getDirection().normalize();
            center.add(normal.clone().multiply(0.12));
        } else {
            center = eye.clone().add(dir.clone().multiply(4.5));
            normal = dir.clone().multiply(-1).setY(0);
            if (normal.lengthSquared() < 0.01) {
                normal = new Vector(0, 0, 1);
            }
            normal.normalize();
        }
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(normal.dot(up)) > 0.92) {
            /* Floor/ceiling: stand the portal upright facing the player. */
            Vector face = player.getLocation().getDirection().clone().setY(0);
            if (face.lengthSquared() < 0.01) {
                face = new Vector(0, 0, 1);
            }
            normal = face.normalize();
            center.setY(player.getLocation().getY() + PORTAL_H * 0.55);
        }
        Vector right = normal.clone().crossProduct(up);
        if (right.lengthSquared() < 0.01) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        up = right.clone().crossProduct(normal).normalize();
        /* Keep portal center off the ground a bit when on walls. */
        if (hit != null && hit.getHitBlockFace() != BlockFace.UP && hit.getHitBlockFace() != BlockFace.DOWN) {
            double feet = player.getLocation().getY();
            if (center.getY() - feet < 0.9) {
                center.setY(feet + PORTAL_H * 0.55);
            }
        }
        return new Aim(flat(center), normal, right, up);
    }

    private static Vector toLocal(Gate gate, Vector world) {
        return new Vector(world.dot(gate.right), world.dot(gate.up), world.dot(gate.normal));
    }

    private static Vector fromLocal(Gate gate, Vector local) {
        return gate.right.clone().multiply(local.getX())
                .add(gate.up.clone().multiply(local.getY()))
                .add(gate.normal.clone().multiply(local.getZ()));
    }

    private static boolean isPortalGun(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String stamped = stack.getItemMeta().getPersistentDataContainer().get(
                AetherKeys.namespaced("aetherion", "test_gear"),
                PersistentDataType.STRING
        );
        return ID.equalsIgnoreCase(stamped);
    }

    private enum Channel { AZURE, AMBER }

    private static final class Aim {
        final Location center;
        final Vector normal;
        final Vector right;
        final Vector up;

        Aim(Location center, Vector normal, Vector right, Vector up) {
            this.center = center;
            this.normal = normal;
            this.right = right;
            this.up = up;
        }
    }

    private static final class Pair {
        Gate azure;
        Gate amber;

        void clear() {
            if (azure != null) {
                azure.clear();
                azure = null;
            }
            if (amber != null) {
                amber.clear();
                amber = null;
            }
        }
    }

    private final class Gate {
        final World world;
        final Location center;
        final Vector normal;
        final Vector right;
        final Vector up;
        final Channel channel;
        final Color glow;
        final Material frameMat;
        final Material fillMat;
        final List<BlockDisplay> frame = new ArrayList<>();
        final List<BlockDisplay> fill = new ArrayList<>();
        float open;
        float spin;
        int age;
        boolean ready;

        Gate(Location center, Vector normal, Vector right, Vector up, Channel channel) {
            this.world = center.getWorld();
            this.center = flat(center.clone());
            this.normal = normal.clone().normalize();
            this.right = right.clone().normalize();
            this.up = up.clone().normalize();
            this.channel = channel;
            this.glow = channel == Channel.AZURE ? AZURE : AMBER;
            this.frameMat = channel == Channel.AZURE ? Material.LIGHT_BLUE_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
            this.fillMat = channel == Channel.AZURE ? Material.CYAN_STAINED_GLASS : Material.YELLOW_STAINED_GLASS;
        }

        void spawn() {
            for (int i = 0; i < FRAME_SEGS; i++) {
                frame.add(spawnDisplay(center, frameMat.createBlockData(), glow, tiny()));
            }
            fill.add(spawnDisplay(center, fillMat.createBlockData(), glow, tiny()));
            fill.add(spawnDisplay(center, Material.WHITE_STAINED_GLASS.createBlockData(), glow, tiny()));
            pose(0.02f);
        }

        void tick() {
            age++;
            if (open < 1f) {
                open = Math.min(1f, open + 0.12f);
                if (open >= 1f) {
                    ready = true;
                }
            }
            spin += channel == Channel.AZURE ? 0.045f : -0.04f;
            pose(easeOut(open));
        }

        void pose(float scale) {
            Quaternionf orient = orient();
            /* Elliptical frame segments. */
            for (int i = 0; i < frame.size(); i++) {
                BlockDisplay tile = frame.get(i);
                if (tile == null || !tile.isValid()) {
                    continue;
                }
                double a = Math.PI * 2 * i / FRAME_SEGS;
                double x = Math.cos(a) * PORTAL_W * scale;
                double y = Math.sin(a) * PORTAL_H * scale;
                Location at = center.clone()
                        .add(right.clone().multiply(x))
                        .add(up.clone().multiply(y));
                tile.teleport(flat(at));
                float len = (float) (Math.PI * 2 * ((PORTAL_W + PORTAL_H) * 0.5) / FRAME_SEGS * 1.15 * scale);
                /* Tangent along the ellipse for segment orientation. */
                Vector tangent = right.clone().multiply(-Math.sin(a) * PORTAL_W)
                        .add(up.clone().multiply(Math.cos(a) * PORTAL_H)).normalize();
                Quaternionf rot = rotationTo(new Vector(0, 1, 0), tangent);
                /* Flatten segment into the portal plane. */
                rot.mul(orient);
                ease(tile, box(FRAME_T, Math.max(0.08f, len), FRAME_T * 0.85f, rot), 2);
            }
            /* Soft fill discs — counter-rotating, slightly offset on normal so they read as depth. */
            if (fill.size() >= 1 && fill.get(0) != null && fill.get(0).isValid()) {
                BlockDisplay disc = fill.get(0);
                Location at = center.clone().add(normal.clone().multiply(0.02));
                disc.teleport(flat(at));
                Quaternionf rot = new Quaternionf(orient()).rotateLocalZ(spin);
                ease(disc, oval(PORTAL_W * 0.88f * scale, PORTAL_H * 0.88f * scale, 0.04f, rot), 2);
            }
            if (fill.size() >= 2 && fill.get(1) != null && fill.get(1).isValid()) {
                BlockDisplay disc = fill.get(1);
                Location at = center.clone().add(normal.clone().multiply(0.05));
                disc.teleport(flat(at));
                Quaternionf rot = new Quaternionf(orient()).rotateLocalZ(-spin * 1.35f);
                ease(disc, oval(PORTAL_W * 0.62f * scale, PORTAL_H * 0.62f * scale, 0.03f, rot), 2);
            }
        }

        boolean contains(Location at) {
            if (at.getWorld() != world || open < 0.95f) {
                return false;
            }
            Vector rel = at.toVector().add(new Vector(0, 0.9, 0)).subtract(center.toVector());
            double n = rel.dot(normal);
            if (Math.abs(n) > 0.55) {
                return false;
            }
            double x = rel.dot(right) / PORTAL_W;
            double y = rel.dot(up) / PORTAL_H;
            return x * x + y * y <= 1.05;
        }

        Quaternionf orient() {
            /* Build basis: local +X = right, +Y = up, +Z = normal. */
            Vector3f r = new Vector3f((float) right.getX(), (float) right.getY(), (float) right.getZ());
            Vector3f u = new Vector3f((float) up.getX(), (float) up.getY(), (float) up.getZ());
            Vector3f n = new Vector3f((float) normal.getX(), (float) normal.getY(), (float) normal.getZ());
            return new Quaternionf().lookAlong(n, u);
        }

        void clear() {
            for (BlockDisplay d : frame) {
                discard(d);
            }
            frame.clear();
            for (BlockDisplay d : fill) {
                discard(d);
            }
            fill.clear();
            ready = false;
        }
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }

    private static Location flat(Location at) {
        Location out = at.clone();
        out.setYaw(0);
        out.setPitch(0);
        return out;
    }

    private static Quaternionf rotationTo(Vector from, Vector to) {
        Vector3f a = new Vector3f((float) from.getX(), (float) from.getY(), (float) from.getZ()).normalize();
        Vector3f b = new Vector3f((float) to.getX(), (float) to.getY(), (float) to.getZ()).normalize();
        return new Quaternionf().rotationTo(a, b);
    }

    private static Transformation tiny() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
    }

    private static Transformation box(float x, float y, float z, Quaternionf rot) {
        Quaternionf r = new Quaternionf(rot);
        Vector3f half = new Quaternionf(r).transform(new Vector3f(x / 2f, y / 2f, z / 2f));
        return new Transformation(half.negate(), r,
                new Vector3f(Math.max(0.001f, x), Math.max(0.001f, y), Math.max(0.001f, z)), new Quaternionf());
    }

    /** Flat oval in local XY of {@code rot} (thin on Z). */
    private static Transformation oval(float w, float h, float thick, Quaternionf rot) {
        Quaternionf r = new Quaternionf(rot);
        Vector3f half = new Quaternionf(r).transform(new Vector3f(w / 2f, h / 2f, thick / 2f));
        return new Transformation(half.negate(), r,
                new Vector3f(Math.max(0.001f, w), Math.max(0.001f, h), Math.max(0.001f, thick)), new Quaternionf());
    }

    private static void ease(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawnDisplay(Location at, BlockData data, Color glow, Transformation initial) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(flat(at.clone()), BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                spawned.setGlowing(true);
                spawned.setGlowColorOverride(glow);
                spawned.setTransformation(initial != null ? initial : tiny());
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
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
