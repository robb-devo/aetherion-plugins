package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;

import com.destroystokyo.paper.ParticleBuilder;
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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
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

/**
 * Skyreaver Spools — AoT / Spider-Man swing cable.
 *
 * <p>Controls: click a surface = latch · click while latched = gas boost (never retargets) ·
 * sneak = cut (momentum kept). Left / right only tint the cable. Rope only pulls in the air —
 * on the ground the line goes slack and leaves your walk alone.
 *
 * <p>Why it feels right:
 * <ul>
 *   <li><b>Own physics state.</b> Server-side player velocity is unreliable, so the rig keeps its
 *       own velocity and drives the client with it every tick (velocity only — never a teleport,
 *       never a camera change). Latency is compensated by predicting where the client already is.</li>
 *   <li><b>True pendulum.</b> Position-based rope constraint (inextensible, can go slack), gravity,
 *       light air drag, a spool that takes up slack while you're under the latch, and a small
 *       bottom-of-arc pump so swings stay alive. Look only bends the swing along the tangent.</li>
 *   <li><b>Clean chains.</b> Cut with sneak, then latch the next point — gas never steals your cable.
 *       Cutting keeps momentum and a short release carry prevents the vanilla air-drag "death"
 *       before the next cable.</li>
 *   <li><b>Clean visuals.</b> One thin string (4 bars for sag) anchored at the latch, animated only via
 *       transformation interpolation; a small glowing claw marks the latch; a player-only reticle dot
 *       shows where a click would latch (no dot = click is a gas boost).</li>
 * </ul>
 */
@SuppressWarnings("deprecation") // Player#isOnGround is client-reported; fine for feel logic
public final class SkyreaverSpoolsListener implements Listener {

    private static final String ID = "skyreaver_spools";
    private static final NamespacedKey TEST_GEAR = AetherKeys.namespaced("aetherion", "test_gear");

    /* ---------- reach ---------- */
    private static final double RANGE = 64.0;
    private static final double MIN_ROPE = 3.0;
    private static final double MAX_ROPE = 62.0;
    /** Rope attaches at the harness (chest height). */
    private static final double BODY_Y = 1.05;

    /* ---------- swing physics (blocks / tick) ---------- */
    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.992;
    private static final double MAX_SPEED = 2.3;
    /** Most the rope may correct in one tick — soft catch, never a yank. */
    private static final double MAX_PULL = 0.75;
    /** Fresh latch: the spool snaps the rope to this share of the distance (pulls you into the arc). */
    private static final double SNAP_RATIO = 0.86;
    private static final double RETARGET_SNAP = 0.95;
    private static final int SNAP_TICKS = 5;
    /** Longest reel-in on latch (blocks) — keeps long cables snappy, not violent. */
    private static final double MAX_SNAP = 3.0;
    /** Kick along the swing tangent when latching from (near) standstill. */
    private static final double LAUNCH = 0.38;
    /** Small speed gift on every mid-air retarget — chains accelerate. */
    private static final double CHAIN_BONUS = 0.09;
    /** How fast the look bends the swing direction (0..1 per tick). */
    private static final double TURN = 0.085;
    /** Look-steer acceleration along the tangent. */
    private static final double STEER = 0.011;
    /** Bottom-of-arc energy so swings don't die. */
    private static final double PUMP = 0.016;
    private static final double PUMP_MIN = 0.30;
    /** Winch resistance on free inward travel (stops you zipping into the latch). */
    private static final double WINCH_BRAKE = 0.22;

    /* ---------- gas ---------- */
    private static final double GAS = 0.6;
    private static final long GAS_CD_MS = 300L;
    /** Free-air gas bursts after a cut; refills on latch or landing. */
    private static final int AIR_GAS = 2;

    /* ---------- release ---------- */
    private static final int CARRY_TICKS = 26;
    private static final double CARRY_DRAG = 0.988;
    private static final double RELEASE_POP = 0.1;
    private static final long GRACE_MS = 6000L;

    /* ---------- misc ---------- */
    private static final long CLICK_DEBOUNCE_MS = 90L;
    private static final int CABLE_SEGS = 4;
    private static final Color LEFT_TINT = Color.fromRGB(255, 70, 90);
    private static final Color RIGHT_TINT = Color.fromRGB(70, 200, 255);
    private static final Color RETICLE = Color.fromRGB(235, 245, 255);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final CopyOnWriteArrayList<Display> LIVE = new CopyOnWriteArrayList<>();
    private static SkyreaverSpoolsListener instance;

    private final JavaPlugin plugin;
    private final boolean serverFlight;
    private final Map<UUID, Rig> rigs = new HashMap<>();
    private final List<Fade> fading = new ArrayList<>();
    private BukkitTask ticker;
    private long tickNo;

    public SkyreaverSpoolsListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.serverFlight = plugin.getServer().getAllowFlight();
        instance = this;
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public static void shutdown() {
        if (instance != null) {
            if (instance.ticker != null) {
                instance.ticker.cancel();
                instance.ticker = null;
            }
            for (Rig rig : instance.rigs.values()) {
                if (rig.swing != null) {
                    rig.swing.discard();
                    rig.swing = null;
                }
            }
            instance.rigs.clear();
            for (Fade fade : instance.fading) {
                fade.discard();
            }
            instance.fading.clear();
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
        if (!isSkyreaver(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        Rig rig = rig(player);
        long now = System.currentTimeMillis();
        /* A single click can arrive as BLOCK + AIR in one tick — act once. */
        if (now - rig.lastClick < CLICK_DEBOUNCE_MS) {
            return;
        }
        rig.lastClick = now;

        if (player.isSneaking()) {
            cut(player, rig, true);
            return;
        }
        /* While latched: every click is gas. Never retarget / never cut from click. */
        if (rig.swing != null) {
            gas(player, rig, now);
            return;
        }
        Latch latch = aim(player);
        if (latch != null) {
            attach(player, rig, latch, left);
        } else {
            gas(player, rig, now);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && isSkyreaver(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Rig rig = rigs.get(player.getUniqueId());
        if (rig == null) {
            return;
        }
        if (rig.swing != null || rig.carry > 0 || System.currentTimeMillis() < rig.graceUntil) {
            event.setCancelled(true);
            player.setFallDistance(0f);
            rig.graceUntil = 0L;
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Rig rig = rigs.get(event.getPlayer().getUniqueId());
        if (rig != null && rig.swing != null) {
            cut(event.getPlayer(), rig, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        /* Portals, pearls, /spawn… drop the cable quietly and never fight their velocity. */
        Rig rig = rigs.get(event.getPlayer().getUniqueId());
        if (rig != null) {
            drop(event.getPlayer(), rig);
            rig.last = null;
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Rig rig = rigs.get(event.getEntity().getUniqueId());
        if (rig != null) {
            drop(event.getEntity(), rig);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Rig rig = rigs.remove(event.getPlayer().getUniqueId());
        if (rig != null && rig.swing != null) {
            rig.swing.discard();
            rig.swing = null;
        }
    }

    /* ======================================================================
     * Actions
     * ==================================================================== */

    private void attach(Player player, Rig rig, Latch hit, boolean leftHand) {
        /* Click-while-latched is gas-only now — attach is always a fresh cable. */
        if (rig.swing != null) {
            retract(player, rig, rig.swing);
            rig.swing = null;
        }
        boolean onGround = player.isOnGround();
        boolean chain = rig.carry > 0 || !onGround;
        double lead = lead(player);
        Vector v = rig.vel.clone();
        Vector body = player.getLocation().toVector().add(new Vector(0, BODY_Y, 0)).add(v.clone().multiply(lead));
        Vector rel = body.clone().subtract(hit.point);
        double dist = rel.length();
        if (dist < 0.6) {
            return;
        }
        Vector out = rel.clone().multiply(1.0 / dist);

        /* Where "into the arc" is: forward along the swing tangent, never straight at the wall. */
        Vector look = player.getEyeLocation().getDirection();
        Vector launchDir = tangent(look.clone().setY(0), out);
        if (launchDir.lengthSquared() < 0.01) {
            launchDir = tangent(look, out);
        }
        if (launchDir.lengthSquared() < 0.01) {
            launchDir = tangent(new Vector(0, -1, 0), out);
        }
        if (launchDir.lengthSquared() < 1.0e-4) {
            launchDir = new Vector(0, 0, 0);
        } else {
            launchDir.normalize();
        }

        Swing swing;
        if (onGround) {
            /* Ground latch: plant the claw only. No pull until you leave the floor. */
            swing = new Swing(hit, dist, leftHand, 1.0);
            swing.snapLeft = 0;
            swing.snapStep = 0;
            swing.snapTarget = dist;
            rig.swing = swing;
            rig.carry = 0;
            rig.airGas = AIR_GAS;
            rig.floatTicks = 0;
            rig.stall = 0;
            rig.driving = false;
            swing.spawn();
            swing.render(hand(player, v, lead + 1.0, leftHand));
            hint(player, "§e✦ Locked §7— jump to swing · sneak to cut");
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 0.45f,
                    leftHand ? 1.5f : 1.65f);
            world.playSound(hit.point.toLocation(world), Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.55f, 1.35f);
            return;
        }

        double speed = v.length();
        if (speed > 0.22) {
            /* Redirect current speed onto the new arc — this is what makes chains feel godlike. */
            Vector vt = tangent(v, out);
            Vector dir = vt.lengthSquared() > 0.0025 ? vt.normalize() : launchDir.clone();
            Vector blend = dir.multiply(0.7).add(launchDir.clone().multiply(0.3));
            if (blend.lengthSquared() > 1.0e-4) {
                blend.normalize();
                double keep = chain ? speed : speed * 0.95;
                v = blend.clone().multiply(keep + (chain ? CHAIN_BONUS : LAUNCH * 0.5));
            }
        } else {
            v.add(launchDir.clone().multiply(LAUNCH));
        }
        cap(v);

        swing = new Swing(hit, dist, leftHand, chain ? RETARGET_SNAP : SNAP_RATIO);
        swing.spawn();
        rig.swing = swing;
        rig.carry = 0;
        rig.airGas = AIR_GAS;
        rig.floatTicks = 0;
        rig.stall = 0;
        rig.vel = v;
        rig.driving = true;
        player.setVelocity(v);
        player.setFallDistance(0f);
        swing.render(hand(player, v, lead + 1.0, leftHand));

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 0.5f,
                leftHand ? 1.5f : 1.65f);
        world.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.4f, 1.35f);
        if (chain && speed > 0.6) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.25f, 1.4f);
        }
    }

    private void gas(Player player, Rig rig, long now) {
        if (now < rig.gasReadyAt) {
            return;
        }
        if (rig.swing == null) {
            if (player.isOnGround()) {
                hint(player, "§7No surface in range");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.35f, 0.6f);
                rig.gasReadyAt = now + 250L;
                return;
            }
            if (rig.airGas <= 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.25f, 0.7f);
                hint(player, "§8Gas empty §7— latch or land to refill");
                rig.gasReadyAt = now + 250L;
                return;
            }
            rig.airGas--;
        }
        rig.gasReadyAt = now + GAS_CD_MS;

        Vector look = player.getEyeLocation().getDirection();
        Vector v = rig.vel.clone();
        if (v.getY() < -0.3) {
            v.setY(v.getY() * 0.6); // gas also catches a hard fall a little
        }
        v.add(look.clone().multiply(GAS)).add(new Vector(0, 0.05, 0));
        cap(v);
        rig.vel = v;
        rig.driving = true;
        player.setVelocity(v);
        player.setFallDistance(0f);
        if (rig.swing == null) {
            rig.carry = CARRY_TICKS;
            rig.graceUntil = now + GRACE_MS;
        }

        World world = player.getWorld();
        Location puff = player.getLocation().add(0, 0.9, 0).subtract(look.clone().multiply(0.6));
        world.spawnParticle(Particle.SMALL_GUST, puff, 1, 0.05, 0.05, 0.05, 0);
        world.spawnParticle(Particle.CLOUD, puff, 5, 0.12, 0.1, 0.12, 0.02);
        world.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.45f, 1.7f);
        world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 0.22f, 1.8f);
    }

    /** Sneak / sneak-click: cut and keep every bit of momentum. */
    private void cut(Player player, Rig rig, boolean feedback) {
        Swing swing = rig.swing;
        if (swing == null) {
            return;
        }
        rig.swing = null;
        retract(player, rig, swing);
        boolean air = !player.isOnGround();
        Vector v = rig.vel.clone();
        if (feedback && air && v.length() > 0.45 && v.getY() > -0.15) {
            /* Release at the top of the arc: a little flick up and along — the Spider-Man pop. */
            Vector dir = v.clone().normalize();
            v.add(dir.multiply(RELEASE_POP)).add(new Vector(0, 0.07, 0));
            cap(v);
        }
        rig.vel = v;
        if (air) {
            rig.carry = CARRY_TICKS;
            rig.driving = true;
            player.setVelocity(v);
        } else {
            rig.carry = 0;
            rig.driving = false;
        }
        rig.graceUntil = System.currentTimeMillis() + GRACE_MS;
        player.setFallDistance(0f);
        if (feedback) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 0.55f, 1.55f);
        }
    }

    /** Silent removal for teleports / death — never touches velocity. */
    private void drop(Player player, Rig rig) {
        if (rig.swing != null) {
            retract(player, rig, rig.swing);
            rig.swing = null;
        }
        rig.carry = 0;
        rig.driving = false;
        rig.vel.zero();
    }

    private void retract(Player player, Rig rig, Swing swing) {
        Vector handRel = hand(player, rig.vel, lead(player) + 1.0, swing.left).subtract(swing.latch);
        fading.add(swing.retract(handRel));
    }

    /* ======================================================================
     * Tick
     * ==================================================================== */

    private void tick() {
        tickNo++;
        fading.removeIf(Fade::step);
        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean holding = isSkyreaver(player.getInventory().getItemInMainHand());
            Rig rig = rigs.get(player.getUniqueId());
            if (rig == null) {
                if (!holding) {
                    continue;
                }
                rig = new Rig();
                rigs.put(player.getUniqueId(), rig);
            }
            rig.holding = holding;
        }

        Iterator<Map.Entry<UUID, Rig>> it = rigs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Rig> entry = it.next();
            Rig rig = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                if (rig.swing != null) {
                    rig.swing.discard();
                }
                it.remove();
                continue;
            }
            if (player.isDead()) {
                drop(player, rig);
                continue;
            }
            tickRig(player, rig, now);
            if (!rig.holding && !rig.active(now)) {
                it.remove();
            }
        }
    }

    private void tickRig(Player player, Rig rig, long now) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        Vector feet = loc.toVector();
        Vector measured = rig.last == null || rig.lastWorld != world
                ? new Vector() : feet.clone().subtract(rig.last);
        if (measured.lengthSquared() > 36.0) {
            measured.zero();
        }
        rig.last = feet.clone();
        rig.lastWorld = world;

        boolean onGround = player.isOnGround();
        rig.groundTicks = onGround ? rig.groundTicks + 1 : 0;
        if (rig.groundTicks >= 3) {
            rig.graceUntil = 0L;
            rig.airGas = AIR_GAS;
        }

        /* If something the server can't see is holding the player (cobweb, mob, lag), trust reality. */
        if (rig.driving && rig.vel.length() > 0.35 && measured.length() < rig.vel.length() * 0.2) {
            if (++rig.stall >= 7) {
                rig.vel = measured.clone();
                rig.stall = 0;
                if (rig.carry > 0) {
                    rig.carry = 0;
                    rig.driving = false;
                }
            }
        } else {
            rig.stall = 0;
        }

        if (rig.swing != null) {
            swingTick(player, rig, rig.swing, feet, measured, onGround);
        } else if (rig.carry > 0) {
            carryTick(player, rig, onGround);
        } else {
            rig.driving = false;
            rig.vel.multiply(0.35).add(measured.clone().multiply(0.65));
        }

        if (rig.holding && tickNo % 2 == 0) {
            reticle(player, rig);
        }
    }

    private void swingTick(Player player, Rig rig, Swing s, Vector feet, Vector measured, boolean onGround) {
        s.age++;
        if (player.getWorld() != s.world || player.isInsideVehicle() || player.isGliding()
                || player.getGameMode() == GameMode.SPECTATOR) {
            drop(player, rig);
            return;
        }
        if (player.isInWater() || player.isInLava()) {
            cut(player, rig, false);
            return;
        }
        if (s.age % 5 == 0 && !s.latchHolds()) {
            hint(player, "§7Latch lost");
            cut(player, rig, true);
            return;
        }

        double lead = lead(player);
        Vector v = rig.vel.clone();
        Vector body = feet.clone().add(new Vector(0, BODY_Y, 0)).add(v.clone().multiply(lead));
        Vector rel = body.clone().subtract(s.latch);
        double dist = Math.max(1.0e-3, rel.length());
        if (dist > MAX_ROPE + 10.0) {
            cut(player, rig, false);
            return;
        }

        /* Ground: cable is decoration only — never drag the player. Keep rope slack to feet. */
        if (onGround) {
            s.rope = Math.max(MIN_ROPE, dist + 0.15);
            s.snapLeft = 0;
            rig.driving = false;
            rig.vel.multiply(0.25).add(measured.clone().multiply(0.75));
            s.render(hand(player, measured, lead + 1.0, s.left));
            return;
        }

        if (dist > s.rope + 6.0) {
            s.rope = dist - 2.0; // knocked far out: soften instead of yanking back
        }
        Vector out = rel.clone().multiply(1.0 / dist);

        /* 1) gravity + light air */
        v.setY(v.getY() - GRAVITY);
        v.multiply(AIR_DRAG);

        /* 2) spool: snap after latch, then take up slack while under the latch */
        if (s.snapLeft > 0) {
            s.rope = Math.max(s.snapTarget, s.rope - s.snapStep);
            if (--s.snapLeft == 0) {
                /* Reel done: bleed off half the inward rush so you swing, not slam. */
                double inward = -v.dot(out);
                if (inward > 0) {
                    v.add(out.clone().multiply(inward * 0.5));
                }
            }
        }
        boolean below = out.getY() < -0.2;
        if (below && dist < s.rope - 0.02) {
            s.rope = Math.max(MIN_ROPE, dist + 0.02);
            double inward = -v.dot(out);
            if (inward > 0) {
                v.add(out.clone().multiply(inward * WINCH_BRAKE));
            }
        }

        /* 3) look steers the tangent only */
        if (dist >= s.rope * 0.9) {
            steer(player, v, out, below);
        }

        /* 4) rope constraint (position based, inextensible, may go slack) */
        Vector next = body.clone().add(v);
        Vector d = next.clone().subtract(s.latch);
        double nd = d.length();
        if (nd > s.rope) {
            Vector corr = s.latch.clone().add(d.multiply(s.rope / nd)).subtract(next);
            double c = corr.length();
            if (c > MAX_PULL) {
                corr.multiply(MAX_PULL / c);
            }
            v.add(corr);
        }

        /* 5) world */
        collide(player, v, lead);
        cap(v);
        floatGuard(player, rig, s, v, measured, false);

        player.setVelocity(v);
        rig.driving = true;
        rig.vel = v;
        player.setFallDistance(0f);

        /* Whoosh through the bottom of a fast arc (only the swinger hears it). */
        double speed = rig.vel.length();
        if (below && -out.getY() > 0.95 && speed > 0.9 && s.age - s.lastWhoosh > 14) {
            s.lastWhoosh = s.age;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS,
                    0.3f, (float) Math.min(1.2, 0.55 + speed * 0.2));
        }

        s.render(hand(player, rig.vel, lead + 1.0, s.left));
    }

    /** Short low-drag glide after a cut / air gas, easing into vanilla air physics. */
    private void carryTick(Player player, Rig rig, boolean onGround) {
        if (onGround || player.isInWater() || player.isInLava() || player.isGliding()
                || player.isInsideVehicle() || player.isFlying()) {
            rig.carry = 0;
            rig.driving = false;
            return;
        }
        Vector v = rig.vel.clone();
        double t = 1.0 - rig.carry / (double) CARRY_TICKS;
        double drag = CARRY_DRAG - (CARRY_DRAG - 0.91) * t * t;
        v.setY((v.getY() - GRAVITY) * 0.98);
        v.setX(v.getX() * drag);
        v.setZ(v.getZ() * drag);
        boolean bumped = collide(player, v, lead(player));
        cap(v);
        player.setVelocity(v);
        rig.vel = v;
        rig.driving = true;
        double flat = Math.hypot(v.getX(), v.getZ());
        if (--rig.carry <= 0 || bumped || flat < 0.2) {
            rig.carry = 0;
            rig.driving = false;
        }
    }

    private static void steer(Player player, Vector v, Vector out, boolean below) {
        Vector look = player.getEyeLocation().getDirection();
        Vector aim = tangent(look, out);
        if (aim.lengthSquared() < 0.09) {
            Vector flat = look.clone().setY(0);
            if (flat.lengthSquared() > 1.0e-4) {
                aim = tangent(flat.normalize(), out);
            }
        }
        if (aim.lengthSquared() < 1.0e-4) {
            return;
        }
        aim.normalize();
        double radial = v.dot(out);
        Vector vt = v.clone().subtract(out.clone().multiply(radial));
        double sp = vt.length();
        if (sp > 0.08) {
            Vector dir = vt.clone().multiply(1.0 / sp);
            if (dir.dot(aim) > -0.3) {
                Vector bent = dir.multiply(1.0 - TURN).add(aim.clone().multiply(TURN));
                if (bent.lengthSquared() > 1.0e-6) {
                    vt = bent.normalize().multiply(sp);
                }
            }
            if (below && sp > PUMP_MIN) {
                double bottom = -out.getY();
                vt.add(vt.clone().multiply(PUMP * bottom * bottom / sp));
            }
        }
        vt.add(aim.multiply(STEER));
        v.copy(vt.add(out.clone().multiply(radial)));
    }

    /**
     * Keeps a hanging player from tripping the vanilla "floating too long" kick on servers
     * without allow-flight: a tiny rope-settle dip every few seconds of perfect stillness.
     */
    private void floatGuard(Player player, Rig rig, Swing s, Vector v, Vector measured, boolean onGround) {
        if (serverFlight || player.getAllowFlight() || onGround || measured.getY() < -0.04) {
            rig.floatTicks = 0;
            return;
        }
        if (++rig.floatTicks > 54) {
            rig.floatTicks = 0;
            v.setY(Math.min(v.getY(), -0.1));
            /* Let the rope give 0.12 for one tick, then reel it straight back. */
            if (s.snapLeft <= 0) {
                s.snapTarget = s.rope;
                s.snapStep = 0.02;
                s.snapLeft = 6;
            }
            s.rope += 0.12;
        }
    }

    /** Player-only dot where a click would latch. No dot = the click is a gas boost. */
    private void reticle(Player player, Rig rig) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return;
        }
        RayTraceResult hit = world.rayTraceBlocks(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlockFace() == null || hit.getHitPosition() == null) {
            return;
        }
        Vector at = hit.getHitPosition().clone().add(hit.getHitBlockFace().getDirection().multiply(0.06));
        if (rig.swing != null && at.distanceSquared(rig.swing.latch) < 2.25) {
            return;
        }
        double dist = at.distance(eye.toVector());
        float size = (float) Math.min(3.2, 0.55 + dist * 0.035);
        new ParticleBuilder(Particle.DUST)
                .location(at.toLocation(world))
                .count(1)
                .offset(0, 0, 0)
                .extra(0)
                .color(RETICLE, size)
                .receivers(player)
                .force(true)
                .spawn();
    }

    /* ======================================================================
     * Helpers
     * ==================================================================== */

    private Rig rig(Player player) {
        return rigs.computeIfAbsent(player.getUniqueId(), id -> new Rig());
    }

    private static Latch aim(Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        RayTraceResult hit = world.rayTraceBlocks(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null || hit.getHitBlock() == null) {
            return null;
        }
        Vector point = hit.getHitPosition().clone();
        if (hit.getHitBlockFace() != null) {
            point.add(hit.getHitBlockFace().getDirection().multiply(0.04));
        }
        return new Latch(world, point, hit.getHitBlock());
    }

    /** Predicted client-side hand point (the client is ahead of what the server knows). */
    private static Vector hand(Player player, Vector vel, double lead, boolean left) {
        Location loc = player.getLocation();
        double rad = Math.toRadians(loc.getYaw());
        Vector fwd = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        Vector side = new Vector(-Math.cos(rad), 0, -Math.sin(rad));
        return loc.toVector()
                .add(vel.clone().multiply(lead))
                .add(new Vector(0, 1.2, 0))
                .add(side.multiply(left ? -0.34 : 0.34))
                .add(fwd.multiply(0.28));
    }

    /** How many ticks the client is ahead of the position the server has. */
    private static double lead(Player player) {
        return Math.min(3.0, Math.max(0, player.getPing()) / 50.0);
    }

    /** Axis-separated sweep against the world; zeroes blocked components. True if a wall was hit. */
    private static boolean collide(Player player, Vector v, double lead) {
        World world = player.getWorld();
        BoundingBox box = player.getBoundingBox().clone().expand(-0.02);
        if (lead > 0.05) {
            BoundingBox ahead = box.clone().shift(v.clone().multiply(lead));
            if (!world.hasCollisionsIn(ahead)) {
                box = ahead;
            }
        }
        boolean wall = false;
        if (v.getY() != 0) {
            if (world.hasCollisionsIn(box.clone().expandDirectional(0, v.getY(), 0))) {
                v.setY(0);
            } else {
                box.shift(0, v.getY(), 0);
            }
        }
        if (v.getX() != 0) {
            if (world.hasCollisionsIn(box.clone().expandDirectional(v.getX(), 0, 0))) {
                v.setX(0);
                wall = true;
            } else {
                box.shift(v.getX(), 0, 0);
            }
        }
        if (v.getZ() != 0 && world.hasCollisionsIn(box.clone().expandDirectional(0, 0, v.getZ()))) {
            v.setZ(0);
            wall = true;
        }
        return wall;
    }

    private static Vector tangent(Vector v, Vector axis) {
        return v.clone().subtract(axis.clone().multiply(v.dot(axis)));
    }

    private static void cap(Vector v) {
        double len = v.length();
        if (len > MAX_SPEED) {
            v.multiply(MAX_SPEED / len);
        }
    }

    private static void hint(Player player, String legacy) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(legacy));
    }

    private static boolean isSkyreaver(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String stamped = stack.getItemMeta().getPersistentDataContainer().get(TEST_GEAR, PersistentDataType.STRING);
        return ID.equalsIgnoreCase(stamped);
    }

    /* ======================================================================
     * State
     * ==================================================================== */

    private static final class Rig {
        Swing swing;
        Vector vel = new Vector();
        Vector last;
        World lastWorld;
        boolean driving;
        boolean holding;
        int carry;
        int groundTicks;
        int floatTicks;
        int stall;
        int airGas = AIR_GAS;
        long gasReadyAt;
        long graceUntil;
        long lastClick;

        boolean active(long now) {
            return swing != null || carry > 0 || now < graceUntil;
        }
    }

    private static final class Latch {
        final World world;
        final Vector point;
        final Block block;

        Latch(World world, Vector point, Block block) {
            this.world = world;
            this.point = point;
            this.block = block;
        }
    }

    /** Displays that finish an animation, then vanish. */
    private static final class Fade {
        final List<Display> displays;
        int ticks;

        Fade(List<Display> displays, int ticks) {
            this.displays = displays;
            this.ticks = ticks;
        }

        boolean step() {
            if (--ticks > 0) {
                return false;
            }
            discard();
            return true;
        }

        void discard() {
            for (Display display : displays) {
                SkyreaverSpoolsListener.discard(display);
            }
            displays.clear();
        }
    }

    private static final class Swing {
        final World world;
        final Vector latch;
        final Location anchorLoc;
        final int bx;
        final int by;
        final int bz;
        final boolean left;
        final Color tint;
        final Material wire;
        final BlockDisplay[] seg = new BlockDisplay[CABLE_SEGS];
        BlockDisplay claw;
        double rope;
        double snapTarget;
        double snapStep;
        int snapLeft;
        int age;
        int lastWhoosh = -100;
        float shot;
        boolean landed;

        Swing(Latch hit, double dist, boolean left, double snapRatio) {
            this.world = hit.world;
            this.latch = hit.point.clone();
            Location anchor = latch.toLocation(world);
            anchor.setYaw(0f);
            anchor.setPitch(0f);
            this.anchorLoc = anchor;
            this.bx = hit.block.getX();
            this.by = hit.block.getY();
            this.bz = hit.block.getZ();
            this.left = left;
            this.tint = left ? LEFT_TINT : RIGHT_TINT;
            this.wire = left ? Material.RED_CONCRETE : Material.LIGHT_BLUE_CONCRETE;
            this.rope = Math.max(MIN_ROPE, Math.min(MAX_ROPE, dist));
            /* Reel-in never faster than the rope can pull (no velocity pile-up on long cables). */
            double reel = Math.min(rope * (1.0 - snapRatio), MAX_SNAP);
            this.snapTarget = Math.max(MIN_ROPE, rope - reel);
            this.snapLeft = SNAP_TICKS;
            this.snapStep = (rope - snapTarget) / SNAP_TICKS;
        }

        void spawn() {
            for (int i = 0; i < CABLE_SEGS; i++) {
                seg[i] = spawnDisplay(anchorLoc, wire, null, point(new Vector3f()));
            }
            claw = spawnDisplay(anchorLoc, wire, tint, point(new Vector3f()));
        }

        boolean latchHolds() {
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                return true;
            }
            Block block = world.getBlockAt(bx, by, bz);
            return !block.isEmpty() && !block.isPassable();
        }

        /** Draws the string from the (predicted) hand to the latch; sags when slack. */
        void render(Vector hand) {
            if (shot < 1f) {
                shot = Math.min(1f, shot + 0.5f);
            }
            Vector toLatch = latch.clone().subtract(hand);
            double full = toLatch.length();
            Vector end = hand.clone().add(toLatch.clone().multiply(shot));
            double span = full * shot;
            double slack = Math.max(0.0, rope - full);
            double sag = shot < 1f ? 0.0 : Math.min(2.6, Math.sqrt(0.375 * span * slack));
            Vector mid = hand.clone().add(end).multiply(0.5);
            mid.setY(mid.getY() - sag);
            float th = (float) (0.028 + Math.min(0.03, span * 0.0006));

            Vector prev = hand;
            for (int i = 0; i < CABLE_SEGS; i++) {
                double t = (i + 1) / (double) CABLE_SEGS;
                Vector next = bezier(hand, mid, end, t);
                ease(seg[i], bar(prev.clone().subtract(latch), next.clone().subtract(latch), th), 1);
                prev = next;
            }
            if (shot >= 1f && !landed) {
                landed = true;
                ease(claw, clawTf(0.17f), 3);
                Location at = anchorLoc.clone();
                world.spawnParticle(Particle.CRIT, at, 6, 0.05, 0.05, 0.05, 0.18);
                world.spawnParticle(Particle.DUST, at, 4, 0.08, 0.08, 0.08, 0,
                        new Particle.DustOptions(tint, 0.9f), true);
                world.playSound(at, Sound.BLOCK_TRIPWIRE_ATTACH, SoundCategory.PLAYERS, 0.9f, 1.25f);
                world.playSound(at, Sound.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.6f, 1.7f);
            }
        }

        /** Cable zips back into the spool, claw pops off. */
        Fade retract(Vector handRel) {
            Vector3f to = new Vector3f((float) handRel.getX(), (float) handRel.getY(), (float) handRel.getZ());
            List<Display> list = new ArrayList<>(CABLE_SEGS + 1);
            for (BlockDisplay d : seg) {
                ease(d, point(new Vector3f(to)), 3);
                if (d != null) {
                    list.add(d);
                }
            }
            ease(claw, point(new Vector3f()), 3);
            if (claw != null) {
                list.add(claw);
            }
            return new Fade(list, 4);
        }

        void discard() {
            for (BlockDisplay d : seg) {
                SkyreaverSpoolsListener.discard(d);
            }
            SkyreaverSpoolsListener.discard(claw);
            claw = null;
        }

        private static Vector bezier(Vector a, Vector m, Vector b, double t) {
            double u = 1.0 - t;
            return a.clone().multiply(u * u).add(m.clone().multiply(2.0 * u * t)).add(b.clone().multiply(t * t));
        }
    }

    /* ======================================================================
     * Display plumbing
     * ==================================================================== */

    /** Thin square bar from a to b (relative to the display origin). Stable at any angle. */
    private static Transformation bar(Vector a, Vector b, float th) {
        Vector3f s = new Vector3f((float) a.getX(), (float) a.getY(), (float) a.getZ());
        Vector3f d = new Vector3f((float) (b.getX() - a.getX()), (float) (b.getY() - a.getY()), (float) (b.getZ() - a.getZ()));
        float len = d.length();
        if (len < 1.0e-4f) {
            return point(s);
        }
        d.div(len);
        float pitch = (float) Math.acos(Math.max(-1f, Math.min(1f, d.y)));
        float yaw = (float) Math.atan2(d.x, d.z);
        Quaternionf rot = new Quaternionf().rotationY(yaw).rotateX(pitch);
        float l = len + th * 0.8f;
        Vector3f start = new Vector3f(s).sub(new Vector3f(d).mul(th * 0.4f));
        Vector3f off = rot.transform(new Vector3f(th * 0.5f, 0f, th * 0.5f));
        return new Transformation(start.sub(off), rot, new Vector3f(th, l, th), new Quaternionf());
    }

    private static Transformation clawTf(float size) {
        Quaternionf rot = new Quaternionf().rotationXYZ(0.6155f, 0.7854f, 0f);
        Vector3f off = rot.transform(new Vector3f(size * 0.5f, size * 0.5f, size * 0.5f));
        return new Transformation(off.negate(), rot, new Vector3f(size, size, size), new Quaternionf());
    }

    private static Transformation point(Vector3f at) {
        return new Transformation(at, new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
    }

    private static void ease(BlockDisplay display, Transformation tf, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(tf);
    }

    private static BlockDisplay spawnDisplay(Location at, Material material, Color glow, Transformation tf) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(material.createBlockData());
                d.setPersistent(false);
                d.setBrightness(LIT);
                d.setViewRange(2.0f);
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
}
