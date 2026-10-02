package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Portal Gun — two linked, Portal-style gates.
 *
 * <p>Controls (unchanged): Left = Azure, Right = Amber, Sneak + L/R clears that gate only.
 *
 * <p>How it stays clean:
 * <ul>
 *   <li><b>Look:</b> each gate is a static oval (glowing rim, glass membrane, and a dark
 *       "depth" core once linked). Every display sits on ONE anchor location and only its
 *       transformation changes, so open / link / close are client-side interpolations — no
 *       teleporting blocks, no spin, no jitter.</li>
 *   <li><b>Placement:</b> the oval is fitted flush onto the surface and nudged off floors,
 *       edges and the other gate (Portal-style bump), so wall, floor and ceiling gates are
 *       always valid or cleanly refused.</li>
 *   <li><b>Travel:</b> checked every tick from measured motion (not on block-change moves),
 *       so walking into a wall gate, dropping into a floor gate or jumping into a ceiling
 *       gate is reliable. Momentum maps through the pair (soft-capped), pitch is never
 *       touched, and yaw turns only as much as the gate pair turns.</li>
 * </ul>
 */
public final class PortalGunPortals implements Listener {

    private static final String ID = "portal_gun";
    private static final NamespacedKey TEST_GEAR = AetherKeys.namespaced("aetherion", "test_gear");

    /* ---------- gate shape (half extents: A = across, B = long axis) ---------- */
    private static final double WALL_A = 0.50;
    private static final double WALL_B = 0.98;
    private static final double FLAT_A = 0.50;
    private static final double FLAT_B = 0.86;

    /* ---------- visuals ---------- */
    private static final int RIM_SEGS = 24;
    private static final float RIM_W = 0.08f;
    private static final float RIM_Z0 = 0.010f;
    private static final float RIM_Z1 = 0.064f;
    private static final int FILL_STRIPS = 9;
    private static final float FILL_Z0 = 0.036f;
    private static final float FILL_Z1 = 0.042f;
    private static final int RING_STRIPS = 9;
    private static final float RING_Z0 = 0.012f;
    private static final float RING_Z1 = 0.020f;
    private static final int CORE_STRIPS = 7;
    private static final float CORE_Z0 = 0.022f;
    private static final float CORE_Z1 = 0.030f;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    /* ---------- placement ---------- */
    private static final double PLACE_RANGE = 56.0;
    private static final long CLICK_DEBOUNCE_MS = 120L;
    /** Fit samples in unit-ellipse space (outline, inner ring, centre). */
    private static final double[][] SAMPLES = buildSamples();
    /** In-plane bump offsets (across, along), nearest first. */
    private static final double[][] NUDGES = buildNudges();

    /* ---------- travel ---------- */
    private static final long PASS_COOLDOWN_MS = 280L;
    private static final double EXIT_MAX = 0.88;
    private static final double WALL_EXIT_MIN = 0.24;
    private static final double FLOOR_EXIT_MIN = 0.52;
    private static final double CEIL_EXIT_MIN = 0.06;
    private static final double TANGENT_KEEP = 0.6;
    private static final double TRACK_RADIUS_SQ = 14.0 * 14.0;

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    private static PortalGunPortals instance;

    private final JavaPlugin plugin;
    private final Map<UUID, Pair> pairs = new HashMap<>();
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final List<Gate> closing = new ArrayList<>();
    private BukkitTask ticker;

    public PortalGunPortals(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public static void shutdown() {
        if (instance != null) {
            if (instance.ticker != null) {
                instance.ticker.cancel();
                instance.ticker = null;
            }
            for (Pair pair : instance.pairs.values()) {
                pair.discard();
            }
            instance.pairs.clear();
            for (Gate gate : instance.closing) {
                gate.discard();
            }
            instance.closing.clear();
            instance.tracks.clear();
            instance = null;
        }
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    /* ======================================================================
     * Input
     * ==================================================================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        Player player = event.getPlayer();
        if (!isPortalGun(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return; // swallow the off-hand echo so nothing else fires
        }
        /* One click can arrive as BLOCK + AIR in the same tick — act once. */
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < CLICK_DEBOUNCE_MS) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);

        Channel channel = left ? Channel.AZURE : Channel.AMBER;
        if (player.isSneaking()) {
            clearGate(player, channel);
        } else {
            place(player, channel);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pair pair = pairs.remove(id);
        if (pair != null) {
            pair.discard();
        }
        tracks.remove(id);
        lastClick.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        /* Any foreign teleport resets motion tracking so we never read it as speed. */
        Track track = tracks.get(event.getPlayer().getUniqueId());
        if (track != null && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            track.last = null;
            track.vel.zero();
            track.prev.zero();
        }
    }

    /* ======================================================================
     * Place / clear
     * ==================================================================== */

    private void place(Player player, Channel channel) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        Vector muzzle = muzzle(player);
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, PLACE_RANGE, FluidCollisionMode.NEVER, true);

        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS,
                0.45f, channel.pitch + 0.25f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS,
                0.18f, 2.0f);

        if (hit == null || hit.getHitBlockFace() == null || hit.getHitPosition() == null) {
            tracer(world, muzzle, eye.toVector().add(dir.clone().multiply(12.0)), channel, 0.35f);
            hint(player, "§7No surface in range");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.35f, 0.6f);
            return;
        }

        UUID id = player.getUniqueId();
        Pair pair = pairs.get(id);
        Gate current = pair == null ? null : pair.get(channel);
        Gate other = pair == null ? null : pair.get(channel.other());
        Vector hitPos = hit.getHitPosition();
        tracer(world, muzzle, hitPos, channel, 0.5f);

        Frame frame = solve(player, world, hit, other);
        if (frame == null) {
            fizzle(world, hitPos, hit.getHitBlockFace());
            hint(player, "§7No room for a gate there");
            return;
        }
        if (current != null && current.sameSpot(frame)) {
            return; // clicked the gate that is already there — nothing to redo
        }

        if (pair == null) {
            pair = new Pair();
            pairs.put(id, pair);
        }
        if (current != null) {
            closeGate(current, false);
        }
        Gate gate = new Gate(channel, frame);
        gate.build(true);
        pair.set(channel, gate);
        pair.linkShown = false;

        Location at = frame.center.toLocation(world);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.5f, channel.pitch);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.55f, channel.pitch);
        gate.burst(16, 1.08, 0.85f);
        hint(player, pair.status());
    }

    private void clearGate(Player player, Channel channel) {
        UUID id = player.getUniqueId();
        Pair pair = pairs.get(id);
        Gate gate = pair == null ? null : pair.get(channel);
        if (gate == null) {
            hint(player, "§8No " + channel.label.toLowerCase() + " gate");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.3f, 0.7f);
            return;
        }
        pair.set(channel, null);
        unlink(pair);
        closeGate(gate, false);
        hint(player, pair.status());
        if (pair.empty()) {
            pairs.remove(id);
        }
    }

    private void closeGate(Gate gate, boolean fizzle) {
        gate.collapse();
        closing.add(gate);
        Location at = gate.center.toLocation(gate.world);
        gate.world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.PLAYERS, 0.45f,
                gate.channel.pitch + 0.2f);
        if (fizzle) {
            gate.world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.3f, 1.8f);
            gate.world.spawnParticle(Particle.SMOKE, gate.point(0, 0, 0.1).toLocation(gate.world),
                    6, gate.a * 0.4, gate.b * 0.4, 0.05, 0.01);
        }
    }

    private void unlink(Pair pair) {
        pair.linkShown = false;
        if (pair.azure != null) {
            pair.azure.setDepth(false);
        }
        if (pair.amber != null) {
            pair.amber.setDepth(false);
        }
    }

    /* ======================================================================
     * Placement solver
     * ==================================================================== */

    private static Frame solve(Player player, World world, RayTraceResult hit, Gate other) {
        BlockFace face = hit.getHitBlockFace();
        Vector normal = new Vector(face.getModX(), face.getModY(), face.getModZ());
        Kind kind = normal.getY() > 0.5 ? Kind.FLOOR : normal.getY() < -0.5 ? Kind.CEILING : Kind.WALL;
        Vector up = kind == Kind.WALL ? new Vector(0, 1, 0) : flatAxis(player);
        Vector right = up.clone().crossProduct(normal).normalize();
        double a = kind == Kind.WALL ? WALL_A : FLAT_A;
        double b = kind == Kind.WALL ? WALL_B : FLAT_B;
        Vector base = hit.getHitPosition().clone();

        for (double[] nudge : NUDGES) {
            Vector c = base.clone()
                    .add(right.clone().multiply(nudge[0]))
                    .add(up.clone().multiply(nudge[1]));
            if (!fits(world, c, normal, right, up, a, b, other)) {
                continue;
            }
            if (kind == Kind.WALL) {
                /* Settle onto the floor when aimed near it — gates stand, they don't hover. */
                for (int i = 1; i <= 5; i++) {
                    Vector lower = c.clone().subtract(up.clone().multiply(0.125));
                    if (!fits(world, lower, normal, right, up, a, b, other)) {
                        break;
                    }
                    c = lower;
                }
            }
            return new Frame(kind, world, c, normal, right, up, a, b);
        }
        return null;
    }

    private static boolean fits(World world, Vector c, Vector n, Vector r, Vector u, double a, double b, Gate other) {
        Vector behind = n.clone().multiply(-0.07);
        Vector front = n.clone().multiply(0.07);
        for (double[] s : SAMPLES) {
            Vector p = c.clone().add(r.clone().multiply(s[0] * a)).add(u.clone().multiply(s[1] * b));
            if (!solid(world, p.clone().add(behind))) {
                return false;
            }
            if (solid(world, p.clone().add(front))) {
                return false;
            }
        }
        /* A traveller needs room to step out right in front of the centre. */
        if (solid(world, c.clone().add(n.clone().multiply(0.45)))) {
            return false;
        }
        if (other != null && other.world == world && other.normal.dot(n) > 0.99
                && Math.abs(c.clone().subtract(other.center).dot(n)) < 0.08) {
            for (double[] s : SAMPLES) {
                Vector p = c.clone().add(r.clone().multiply(s[0] * a)).add(u.clone().multiply(s[1] * b));
                if (other.inside(p, 1.06)) {
                    return false;
                }
            }
            Vector rel = other.center.clone().subtract(c);
            double x = rel.dot(r) / a;
            double y = rel.dot(u) / b;
            if (x * x + y * y <= 1.0) {
                return false;
            }
        }
        return true;
    }

    /** Floor / ceiling gates run along your view, snapped to the grid when you're close to it. */
    private static Vector flatAxis(Player player) {
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0e-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        double yaw = Math.toDegrees(Math.atan2(-look.getX(), look.getZ()));
        double snapped = Math.round(yaw / 90.0) * 90.0;
        if (Math.abs(wrap(yaw - snapped)) < 25.0) {
            double rad = Math.toRadians(snapped);
            look = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        }
        return look.normalize();
    }

    private static boolean solid(World world, Vector p) {
        int bx = floor(p.getX());
        int by = floor(p.getY());
        int bz = floor(p.getZ());
        if (by < world.getMinHeight() || by >= world.getMaxHeight()) {
            return false;
        }
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return false;
        }
        Block block = world.getBlockAt(bx, by, bz);
        if (block.isPassable()) {
            return false;
        }
        double lx = p.getX() - bx;
        double ly = p.getY() - by;
        double lz = p.getZ() - bz;
        for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
            if (box.contains(lx, ly, lz)) {
                return true;
            }
        }
        return false;
    }

    /* ======================================================================
     * Tick: animation, upkeep, travel
     * ==================================================================== */

    private void tick() {
        closing.removeIf(Gate::tickClose);

        Iterator<Map.Entry<UUID, Pair>> it = pairs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pair> entry = it.next();
            Pair pair = entry.getValue();
            for (Channel channel : Channel.values()) {
                Gate gate = pair.get(channel);
                if (gate == null) {
                    continue;
                }
                if (!gate.tick()) {
                    /* Surface broke or got covered — the gate fizzles like in Portal. */
                    pair.set(channel, null);
                    unlink(pair);
                    closeGate(gate, true);
                    Player owner = plugin.getServer().getPlayer(entry.getKey());
                    if (owner != null) {
                        hint(owner, "§7" + channel.label + " gate lost its surface");
                    }
                }
            }
            if (pair.linked() && !pair.linkShown) {
                pair.linkShown = true;
                showLink(pair, plugin.getServer().getPlayer(entry.getKey()));
            }
            if (pair.empty()) {
                it.remove();
            }
        }

        tickTravel();
    }

    private void showLink(Pair pair, Player owner) {
        for (Gate gate : new Gate[]{pair.azure, pair.amber}) {
            gate.setDepth(true);
            gate.burst(22, 1.1, 1.0f);
            Location at = gate.center.toLocation(gate.world);
            gate.world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, gate.channel.pitch);
        }
        if (owner != null) {
            owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.3f, 1.9f);
            hint(owner, pair.status());
        }
    }

    private void tickTravel() {
        List<Gate[]> links = new ArrayList<>(4);
        for (Pair pair : pairs.values()) {
            if (pair.linked()) {
                links.add(new Gate[]{pair.azure, pair.amber});
                links.add(new Gate[]{pair.amber, pair.azure});
            }
        }
        if (links.isEmpty()) {
            tracks.clear();
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (player.isDead() || player.isInsideVehicle() || player.getGameMode() == GameMode.SPECTATOR) {
                tracks.remove(id);
                continue;
            }
            Location loc = player.getLocation();
            World world = loc.getWorld();
            Vector feet = loc.toVector();
            boolean near = false;
            for (Gate[] link : links) {
                if (link[0].world == world && link[0].center.distanceSquared(feet) < TRACK_RADIUS_SQ) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                tracks.remove(id);
                continue;
            }
            Track track = tracks.computeIfAbsent(id, k -> new Track());
            track.observe(feet);

            if (track.lock != null) {
                if (!track.lock.open() || !track.lock.zone(player, feet, 0.3)) {
                    if (++track.clearTicks >= 2) {
                        track.lock = null;
                    }
                } else {
                    track.clearTicks = 0;
                }
            }
            if (now < track.nextPass) {
                continue;
            }
            Vector ahead = feet.clone().add(track.vel);
            for (Gate[] link : links) {
                Gate from = link[0];
                if (from == track.lock || from.world != world) {
                    continue;
                }
                if (from.zone(player, feet, 0.0) || from.zone(player, ahead, 0.0)) {
                    pass(player, from, link[1], track, now);
                    break;
                }
            }
        }
    }

    private void pass(Player player, Gate from, Gate to, Track track, long now) {
        World world = to.world;
        double h = player.getHeight();
        double hw = player.getWidth() * 0.5;
        Location loc = player.getLocation();
        Vector feet = loc.toVector();

        /* Momentum in the entry frame (strongest recent push into the gate). */
        Vector vin = track.intoward(from.normal);
        double lx = vin.dot(from.right);
        double ly = vin.dot(from.up);
        double inSpeed = Math.max(0.0, -vin.dot(from.normal));
        double min = to.kind == Kind.FLOOR ? FLOOR_EXIT_MIN : to.kind == Kind.CEILING ? CEIL_EXIT_MIN : WALL_EXIT_MIN;
        double outN = Math.min(EXIT_MAX, Math.max(min, inSpeed));
        /* Through a portal left/right mirror (you turned around), up stays up. */
        Vector out = to.right.clone().multiply(-lx * TANGENT_KEEP)
                .add(to.up.clone().multiply(ly * TANGENT_KEEP))
                .add(to.normal.clone().multiply(outN));

        /* Yaw follows the gate pair only when it matters; pitch is never touched. */
        float yaw = loc.getYaw();
        float delta = 0f;
        if (to.kind == Kind.WALL) {
            if (from.kind == Kind.WALL) {
                delta = (float) wrap(yawOf(to.normal) - yawOf(from.normal.clone().multiply(-1)));
            } else {
                delta = (float) wrap(yawOf(to.normal) - yaw);
            }
        }
        if (to.kind == Kind.FLOOR) {
            /* Pop up and step off the gate instead of dropping straight back in. */
            double rad = Math.toRadians(yaw + delta);
            out.add(new Vector(-Math.sin(rad), 0, Math.cos(rad)).multiply(0.16));
        }
        if (out.length() > EXIT_MAX + 0.16) {
            out.normalize().multiply(EXIT_MAX + 0.16);
        }

        /* Exit spot: same relative offset for wall→wall, centred otherwise. */
        Vector dest;
        switch (to.kind) {
            case WALL -> {
                double side = 0.0;
                if (from.kind == Kind.WALL) {
                    Vector mid = feet.clone().add(new Vector(0, h * 0.5, 0)).subtract(from.center);
                    side = clamp(-mid.dot(from.right), -(to.a - 0.3), to.a - 0.3);
                }
                dest = to.center.clone()
                        .add(to.normal.clone().multiply(hw + 0.1))
                        .add(to.right.clone().multiply(side));
                dest.setY(to.center.getY() - to.b + 0.02);
            }
            case FLOOR -> dest = to.center.clone().add(new Vector(0, 0.02, 0));
            default -> dest = to.center.clone().subtract(new Vector(0, h + 0.06, 0));
        }
        dest = clearance(world, dest, to, hw, h);
        if (dest == null) {
            deny(player, from, to, track, now);
            return;
        }

        Location target = dest.toLocation(world, yaw + delta, loc.getPitch());
        boolean ok = player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.Relative.YAW, TeleportFlag.Relative.PITCH);
        if (!ok) {
            track.nextPass = now + 500L;
            return;
        }
        player.setVelocity(out);
        player.setFallDistance(0f);
        track.teleported(dest, out);
        track.lock = to;
        track.clearTicks = 0;
        track.nextPass = now + PASS_COOLDOWN_MS;

        Location in = from.center.toLocation(from.world);
        Location exit = to.center.toLocation(world);
        from.world.playSound(in, Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 0.28f, 1.7f);
        world.playSound(exit, Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 0.32f, 1.45f);
        player.playSound(dest.toLocation(world), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.18f, 1.9f);
        from.burst(10, 1.02, 0.7f);
        to.burst(14, 1.04, 0.8f);
    }

    /** Finds a spot where the traveller's box is free, nudging out of the gate if needed. */
    private static Vector clearance(World world, Vector feet, Gate to, double hw, double h) {
        double[] lifts = to.kind == Kind.CEILING
                ? new double[]{0.0, -0.25, -0.5, -0.75}
                : new double[]{0.0, 0.06, 0.25, 0.5, 0.8};
        double[] outs = {0.0, 0.2, 0.4};
        for (double push : outs) {
            for (double lift : lifts) {
                Vector p = feet.clone().add(to.normal.clone().multiply(push)).add(new Vector(0, lift, 0));
                BoundingBox box = new BoundingBox(p.getX() - hw, p.getY(), p.getZ() - hw,
                        p.getX() + hw, p.getY() + h, p.getZ() + hw).expand(-0.01);
                if (!world.hasCollisionsIn(box)) {
                    return p;
                }
            }
        }
        return null;
    }

    private void deny(Player player, Gate from, Gate to, Track track, long now) {
        Location at = to.center.toLocation(to.world);
        to.world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.3f, 1.8f);
        to.world.spawnParticle(Particle.SMOKE, to.point(0, 0, 0.1).toLocation(to.world), 5,
                to.a * 0.3, to.b * 0.3, 0.05, 0.01);
        player.setVelocity(from.normal.clone().multiply(0.22).add(new Vector(0, 0.08, 0)));
        track.lock = from;
        track.clearTicks = 0;
        track.nextPass = now + 700L;
        hint(player, "§7Exit gate is blocked");
    }

    /* ======================================================================
     * FX helpers
     * ==================================================================== */

    private static Vector muzzle(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        double rad = Math.toRadians(eye.getYaw());
        Vector side = new Vector(-Math.cos(rad), 0, -Math.sin(rad));
        return eye.toVector().add(dir.multiply(0.6)).add(side.multiply(0.28)).add(new Vector(0, -0.22, 0));
    }

    private static void tracer(World world, Vector from, Vector to, Channel channel, float size) {
        Vector span = to.clone().subtract(from);
        double len = span.length();
        if (len < 0.3) {
            return;
        }
        int steps = (int) Math.min(110, Math.ceil(len / 0.5));
        Particle.DustOptions dust = new Particle.DustOptions(channel.glow, size);
        for (int i = 1; i <= steps; i++) {
            Vector p = from.clone().add(span.clone().multiply(i / (double) steps));
            world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0, 0, 0, 0, dust, true);
        }
    }

    private static void fizzle(World world, Vector at, BlockFace face) {
        Vector p = at.clone().add(new Vector(face.getModX(), face.getModY(), face.getModZ()).multiply(0.1));
        Location loc = p.toLocation(world);
        world.spawnParticle(Particle.SMOKE, loc, 6, 0.12, 0.12, 0.12, 0.01);
        world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.3f, 1.9f);
    }

    private static void hint(Player player, String legacy) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(legacy));
    }

    private static boolean isPortalGun(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String stamped = stack.getItemMeta().getPersistentDataContainer().get(TEST_GEAR, PersistentDataType.STRING);
        return ID.equalsIgnoreCase(stamped);
    }

    /* ======================================================================
     * Model
     * ==================================================================== */

    private enum Kind { WALL, FLOOR, CEILING }

    private enum Channel {
        AZURE("Azure", "§b", Color.fromRGB(70, 185, 255),
                Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS, Material.BLUE_CONCRETE, 1.6f),
        AMBER("Amber", "§6", Color.fromRGB(255, 150, 40),
                Material.ORANGE_CONCRETE, Material.ORANGE_STAINED_GLASS, Material.BROWN_CONCRETE, 1.15f);

        final String label;
        final String code;
        final Color glow;
        final Material rim;
        final Material fill;
        final Material ring;
        final float pitch;

        Channel(String label, String code, Color glow, Material rim, Material fill, Material ring, float pitch) {
            this.label = label;
            this.code = code;
            this.glow = glow;
            this.rim = rim;
            this.fill = fill;
            this.ring = ring;
            this.pitch = pitch;
        }

        Channel other() {
            return this == AZURE ? AMBER : AZURE;
        }
    }

    private static final class Frame {
        final Kind kind;
        final World world;
        final Vector center;
        final Vector normal;
        final Vector right;
        final Vector up;
        final double a;
        final double b;

        Frame(Kind kind, World world, Vector center, Vector normal, Vector right, Vector up, double a, double b) {
            this.kind = kind;
            this.world = world;
            this.center = center;
            this.normal = normal;
            this.right = right;
            this.up = up;
            this.a = a;
            this.b = b;
        }
    }

    private static final class Pair {
        Gate azure;
        Gate amber;
        boolean linkShown;

        Gate get(Channel channel) {
            return channel == Channel.AZURE ? azure : amber;
        }

        void set(Channel channel, Gate gate) {
            if (channel == Channel.AZURE) {
                azure = gate;
            } else {
                amber = gate;
            }
        }

        boolean linked() {
            return azure != null && amber != null && azure.open() && amber.open();
        }

        boolean empty() {
            return azure == null && amber == null;
        }

        /** Portal-crosshair style status: ◉ placed, ○ missing, ━━ linked. */
        String status() {
            String l = azure != null ? "§b◉" : "§8○";
            String r = amber != null ? "§6◉" : "§8○";
            String mid = azure != null && amber != null ? " §7━━ " : " §8· · ";
            return l + mid + r;
        }

        void discard() {
            if (azure != null) {
                azure.discard();
                azure = null;
            }
            if (amber != null) {
                amber.discard();
                amber = null;
            }
        }
    }

    /** Per-player motion, measured from real positions (server-side player velocity lies). */
    private static final class Track {
        Vector last;
        Vector vel = new Vector();
        Vector prev = new Vector();
        Gate lock;
        int clearTicks;
        long nextPass;

        void observe(Vector feet) {
            if (last == null) {
                last = feet.clone();
                return;
            }
            Vector delta = feet.clone().subtract(last);
            last = feet.clone();
            if (delta.lengthSquared() > 25.0) {
                vel.zero();
                prev.zero();
                return;
            }
            prev = vel.clone();
            vel.multiply(0.35).add(delta.multiply(0.65));
        }

        Vector intoward(Vector normal) {
            return vel.dot(normal) <= prev.dot(normal) ? vel.clone() : prev.clone();
        }

        void teleported(Vector feet, Vector out) {
            last = feet.clone();
            vel = out.clone();
            prev = out.clone();
        }
    }

    private static final class Gate {
        final Channel channel;
        final Kind kind;
        final World world;
        final Vector center;
        final Vector normal;
        final Vector right;
        final Vector up;
        final double a;
        final double b;
        final Location origin;
        final Quaternionf basis;
        final List<BlockDisplay> rim = new ArrayList<>(RIM_SEGS);
        final List<BlockDisplay> fill = new ArrayList<>(FILL_STRIPS);
        final List<BlockDisplay> ring = new ArrayList<>(RING_STRIPS);
        final List<BlockDisplay> core = new ArrayList<>(CORE_STRIPS);
        int age;
        boolean depth;
        int closeTimer = -1;

        Gate(Channel channel, Frame f) {
            this.channel = channel;
            this.kind = f.kind;
            this.world = f.world;
            this.center = f.center.clone();
            this.normal = f.normal.clone();
            this.right = f.right.clone();
            this.up = f.up.clone();
            this.a = f.a;
            this.b = f.b;
            Location o = center.clone().add(normal.clone().multiply(0.002)).toLocation(world);
            o.setYaw(0f);
            o.setPitch(0f);
            this.origin = o;
            this.basis = new Matrix3f(
                    (float) right.getX(), (float) right.getY(), (float) right.getZ(),
                    (float) up.getX(), (float) up.getY(), (float) up.getZ(),
                    (float) normal.getX(), (float) normal.getY(), (float) normal.getZ()
            ).getNormalizedRotation(new Quaternionf());
        }

        boolean open() {
            return closeTimer < 0 && age >= 3;
        }

        boolean sameSpot(Frame f) {
            return f.kind == kind && f.world == world && f.normal.dot(normal) > 0.99
                    && f.center.distanceSquared(center) < 0.09;
        }

        /* ---------- visuals ---------- */

        void build(boolean animate) {
            float k = animate ? 0.02f : 1f;
            for (int i = 0; i < RIM_SEGS; i++) {
                add(rim, spawn(origin, channel.rim, channel.glow, rimTf(i, k)));
            }
            for (int j = 0; j < FILL_STRIPS; j++) {
                add(fill, spawn(origin, channel.fill, null, stripTf(j, FILL_STRIPS, a * 0.97, b * 0.97, FILL_Z0, FILL_Z1, k)));
            }
            float dk = depth ? k : 0.001f;
            for (int j = 0; j < RING_STRIPS; j++) {
                add(ring, spawn(origin, channel.ring, null, stripTf(j, RING_STRIPS, a * 0.97, b * 0.97, RING_Z0, RING_Z1, dk)));
            }
            for (int j = 0; j < CORE_STRIPS; j++) {
                add(core, spawn(origin, Material.BLACK_CONCRETE, null,
                        stripTf(j, CORE_STRIPS, a * 0.6, b * 0.7, CORE_Z0, CORE_Z1, dk)));
            }
        }

        void pose(float k, int ticks) {
            for (int i = 0; i < rim.size(); i++) {
                ease(rim.get(i), rimTf(i, k), ticks);
            }
            for (int j = 0; j < fill.size(); j++) {
                ease(fill.get(j), stripTf(j, FILL_STRIPS, a * 0.97, b * 0.97, FILL_Z0, FILL_Z1, k), ticks);
            }
            poseDepth(depth ? k : 0.001f, ticks);
        }

        void poseDepth(float k, int ticks) {
            for (int j = 0; j < ring.size(); j++) {
                ease(ring.get(j), stripTf(j, RING_STRIPS, a * 0.97, b * 0.97, RING_Z0, RING_Z1, k), ticks);
            }
            for (int j = 0; j < core.size(); j++) {
                ease(core.get(j), stripTf(j, CORE_STRIPS, a * 0.6, b * 0.7, CORE_Z0, CORE_Z1, k), ticks);
            }
        }

        void setDepth(boolean on) {
            if (depth == on) {
                return;
            }
            depth = on;
            if (closeTimer < 0 && age >= 6) {
                poseDepth(on ? 1f : 0.001f, on ? 8 : 5);
            } /* else: the pending open-pose picks it up */
        }

        Transformation rimTf(int i, float k) {
            double t0 = Math.PI * 2.0 * i / RIM_SEGS;
            double t1 = Math.PI * 2.0 * (i + 1) / RIM_SEGS;
            double x0 = a * Math.cos(t0);
            double y0 = b * Math.sin(t0);
            double x1 = a * Math.cos(t1);
            double y1 = b * Math.sin(t1);
            double len = Math.hypot(x1 - x0, y1 - y0) * 1.14 + RIM_W * 0.3;
            float roll = (float) Math.atan2(y1 - y0, x1 - x0);
            return box(basis,
                    (float) ((x0 + x1) * 0.5 * k), (float) ((y0 + y1) * 0.5 * k), (RIM_Z0 + RIM_Z1) * 0.5f,
                    (float) (len * k), RIM_W * k, RIM_Z1 - RIM_Z0, roll);
        }

        Transformation stripTf(int j, int count, double ea, double eb, float z0, float z1, float k) {
            double w = 2.0 * ea / count;
            double xc = -ea + w * (j + 0.5);
            double u = xc / ea;
            double hh = eb * Math.sqrt(Math.max(0.0, 1.0 - u * u));
            return box(basis,
                    (float) (xc * k), 0f, (z0 + z1) * 0.5f,
                    (float) (w * 1.02 * k), (float) (2.0 * hh * k), z1 - z0, 0f);
        }

        /** Idle life, upkeep. Returns false when the surface is gone. */
        boolean tick() {
            age++;
            if (age == 2) {
                pose(1.1f, 4);   // pop open
            } else if (age == 6) {
                pose(1f, 3);     // settle
            }
            boolean loaded = world.isChunkLoaded(floor(center.getX()) >> 4, floor(center.getZ()) >> 4);
            if (!loaded) {
                return true;
            }
            if (age % 10 == 0) {
                if (!solid(world, center.clone().subtract(normal.clone().multiply(0.07)))
                        || solid(world, center.clone().add(normal.clone().multiply(0.07)))) {
                    return false;
                }
            }
            if (age % 20 == 0 && !visualsIntact()) {
                discard();
                build(false);
            }
            ambient();
            return true;
        }

        /** Returns true once fully collapsed and removed. */
        boolean tickClose() {
            if (closeTimer < 0) {
                discard();
                return true;
            }
            if (--closeTimer <= 0) {
                discard();
                return true;
            }
            return false;
        }

        void collapse() {
            closeTimer = 5;
            for (int i = 0; i < rim.size(); i++) {
                ease(rim.get(i), rimTf(i, 0.02f), 4);
            }
            for (int j = 0; j < fill.size(); j++) {
                ease(fill.get(j), stripTf(j, FILL_STRIPS, a * 0.97, b * 0.97, FILL_Z0, FILL_Z1, 0.02f), 4);
            }
            poseDepth(0.001f, 3);
        }

        void ambient() {
            if (age < 6) {
                return;
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            if (age % 5 == 0) {
                double t = rnd.nextDouble(Math.PI * 2.0);
                Vector p = point(Math.cos(t) * a, Math.sin(t) * b, 0.08);
                world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(channel.glow, 0.7f));
            }
            if (depth && age % 13 == 0) {
                double t = rnd.nextDouble(Math.PI * 2.0);
                Vector p = point(Math.cos(t) * a * 0.92, Math.sin(t) * b * 0.92, 0.09);
                Vector in = point(0, 0, 0.09).subtract(p).normalize();
                world.spawnParticle(Particle.END_ROD, p.toLocation(world), 0,
                        in.getX(), in.getY(), in.getZ(), 0.025);
            }
        }

        void burst(int n, double scale, float size) {
            Particle.DustOptions dust = new Particle.DustOptions(channel.glow, size);
            for (int i = 0; i < n; i++) {
                double t = Math.PI * 2.0 * i / n;
                Vector p = point(Math.cos(t) * a * scale, Math.sin(t) * b * scale, 0.1);
                world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0, 0, 0, 0, dust, true);
            }
        }

        boolean visualsIntact() {
            if (rim.isEmpty()) {
                return false;
            }
            for (BlockDisplay d : rim) {
                if (d == null || !d.isValid()) {
                    return false;
                }
            }
            BlockDisplay f = fill.isEmpty() ? null : fill.get(0);
            return f != null && f.isValid();
        }

        void discard() {
            for (List<BlockDisplay> list : Arrays.asList(rim, fill, ring, core)) {
                for (BlockDisplay d : list) {
                    PortalGunPortals.discard(d);
                }
                list.clear();
            }
        }

        /* ---------- geometry ---------- */

        Vector point(double x, double y, double z) {
            return center.clone()
                    .add(right.clone().multiply(x))
                    .add(up.clone().multiply(y))
                    .add(normal.clone().multiply(z));
        }

        boolean inside(Vector p, double scale) {
            Vector rel = p.clone().subtract(center);
            if (Math.abs(rel.dot(normal)) > 0.2) {
                return false;
            }
            double x = rel.dot(right) / (a * scale);
            double y = rel.dot(up) / (b * scale);
            return x * x + y * y <= 1.0;
        }

        /** Is the player's body in this gate's pass zone? (slack widens it) */
        boolean zone(Player player, Vector feet, double slack) {
            double h = player.getHeight();
            double hw = player.getWidth() * 0.5;
            switch (kind) {
                case WALL -> {
                    Vector mid = feet.clone().add(new Vector(0, h * 0.5, 0)).subtract(center);
                    double n = mid.dot(normal);
                    if (n < -0.3 || n > hw + 0.12 + slack) {
                        return false;
                    }
                    if (Math.abs(mid.dot(right)) > a - 0.18 + slack) {
                        return false;
                    }
                    double feetY = mid.dot(up) - h * 0.5;
                    return feetY >= -b - 0.4 - slack && feetY + h <= b + 0.5 + slack;
                }
                case FLOOR -> {
                    Vector rel = feet.clone().subtract(center);
                    double n = rel.dot(normal);
                    if (n < -0.4 || n > 0.14 + slack) {
                        return false;
                    }
                    return ellipse(rel, 0.12 - slack);
                }
                default -> {
                    Vector rel = feet.clone().add(new Vector(0, h, 0)).subtract(center);
                    double n = rel.dot(normal);
                    if (n < -0.4 || n > 0.14 + slack) {
                        return false;
                    }
                    return ellipse(rel, 0.12 - slack);
                }
            }
        }

        private boolean ellipse(Vector rel, double margin) {
            double x = rel.dot(right) / Math.max(0.1, a - margin);
            double y = rel.dot(up) / Math.max(0.1, b - margin);
            return x * x + y * y <= 1.0;
        }
    }

    /* ======================================================================
     * Display plumbing
     * ==================================================================== */

    private static void add(List<BlockDisplay> list, BlockDisplay display) {
        if (display != null) {
            list.add(display);
        }
    }

    /** Box of size (sx, sy, sz) centred on local point (cx, cy, cz), rolled in-plane. */
    private static Transformation box(Quaternionf basis, float cx, float cy, float cz,
                                      float sx, float sy, float sz, float roll) {
        sx = Math.max(0.0005f, sx);
        sy = Math.max(0.0005f, sy);
        sz = Math.max(0.0005f, sz);
        Quaternionf rot = new Quaternionf(basis).rotateZ(roll);
        Vector3f centre = basis.transform(new Vector3f(cx, cy, cz));
        Vector3f half = rot.transform(new Vector3f(sx * 0.5f, sy * 0.5f, sz * 0.5f));
        return new Transformation(centre.sub(half), rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private static void ease(BlockDisplay display, Transformation tf, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(tf);
    }

    private static BlockDisplay spawn(Location at, Material material, Color glow, Transformation tf) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(material.createBlockData());
                d.setPersistent(false);
                d.setBrightness(LIT);
                d.setViewRange(1.6f);
                d.setShadowRadius(0f);
                d.setShadowStrength(0f);
                d.setTeleportDuration(0);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(0);
                if (glow != null) {
                    d.setGlowing(true);
                    d.setGlowColorOverride(glow);
                }
                d.setTransformation(tf);
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

    /* ======================================================================
     * Math
     * ==================================================================== */

    private static double[][] buildSamples() {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            double t = Math.PI * 2.0 * i / 14;
            out.add(new double[]{Math.cos(t) * 0.96, Math.sin(t) * 0.96});
        }
        for (int i = 0; i < 6; i++) {
            double t = Math.PI * 2.0 * i / 6 + 0.3;
            out.add(new double[]{Math.cos(t) * 0.55, Math.sin(t) * 0.55});
        }
        out.add(new double[]{0.0, 0.0});
        return out.toArray(new double[0][]);
    }

    private static double[][] buildNudges() {
        List<double[]> out = new ArrayList<>();
        for (int ix = -4; ix <= 4; ix++) {
            for (int iy = -5; iy <= 5; iy++) {
                out.add(new double[]{ix * 0.25, iy * 0.25});
            }
        }
        out.sort((p, q) -> Double.compare(p[0] * p[0] * 1.3 + p[1] * p[1], q[0] * q[0] * 1.3 + q[1] * q[1]));
        return out.toArray(new double[0][]);
    }

    private static double yawOf(Vector v) {
        return Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }

    private static double wrap(double deg) {
        deg %= 360.0;
        if (deg >= 180.0) {
            deg -= 360.0;
        } else if (deg < -180.0) {
            deg += 360.0;
        }
        return deg;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
