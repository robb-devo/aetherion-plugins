package de.aetherion.hub.origin;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Boat;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The living bay of Anker Harbour: the supply skyship <i>Halcyon</i> that drifts in over the
 * north rim, anchors above the water and lowers a crate to a skiff; fishing skiffs rowing their
 * rounds; bobbing channel buoys; and the dock crane swinging crates onto the cargo punt.
 * <p>
 * Pure Hub-side scenery: non-persistent display entities, two boats with villager rowers, no
 * blocks touched. Everything sleeps (and is removed) while nobody is near the harbour; positions
 * come from {@code origin.yml → bay} and were checked against the live map (open water, clear
 * sky lanes). Players can't board, break or trade with any of it.
 */
public final class OriginBay implements Listener {

    static final String TAG = "aeth_origin_bay";

    private final OriginIsle isle;

    private boolean enabled;
    private double[] centre;
    private double wakeRadius = 170.0d;
    private Courier courier;
    private final List<Skiff> skiffs = new ArrayList<>();
    private final List<Buoy> buoys = new ArrayList<>();
    private Crane crane;
    /** Players near the harbour (refreshed every 20 ticks). */
    private final List<Player> watchers = new ArrayList<>();
    private boolean awake;
    private int sleepTicks;
    private long tick;

    OriginBay(OriginIsle isle) {
        this.isle = isle;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        shutdown();
        purgeStale();
        ConfigurationSection s = isle.config().raw().getConfigurationSection("bay");
        enabled = s != null && s.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }
        centre = OriginConfig.nums(s.getString("centre", "300.5 61 -430.5"));
        wakeRadius = s.getDouble("wake-radius", 170.0d);
        ConfigurationSection cs = s.getConfigurationSection("courier");
        if (cs != null && cs.getBoolean("enabled", true)) {
            courier = new Courier(cs);
        }
        ConfigurationSection ss = s.getConfigurationSection("skiffs");
        if (ss != null) {
            double surface = s.getDouble("surface-y", 60.72d);
            for (String id : ss.getKeys(false)) {
                List<double[]> loop = new ArrayList<>();
                for (String raw : ss.getStringList(id)) {
                    double[] p = OriginConfig.nums(raw);
                    if (p != null && p.length >= 2) {
                        loop.add(p);
                    }
                }
                if (loop.size() >= 3) {
                    skiffs.add(new Skiff(id, loop, surface));
                }
            }
        }
        for (String raw : s.getStringList("buoys")) {
            String[] parts = raw.trim().split("\\s+");
            double[] p = parts.length >= 3 ? OriginConfig.nums(parts[0] + " " + parts[1] + " " + parts[2]) : null;
            if (p != null) {
                buoys.add(new Buoy(p, parts.length >= 4 ? parts[3] : "red"));
            }
        }
        // "dock-crane" (part 2, moved clear of the moored sloop) wins over the first "crane" spot:
        // a live origin.yml keeps old values for existing keys, so the move needs a new key.
        ConfigurationSection crs = s.getConfigurationSection("dock-crane");
        if (crs == null) {
            crs = s.getConfigurationSection("crane");
        }
        if (crs != null && crs.getBoolean("enabled", true)) {
            double[] base = OriginConfig.nums(crs.getString("base"));
            if (base != null) {
                crane = new Crane(crs, base);
            }
        }
    }

    void shutdown() {
        if (courier != null) {
            courier.despawn();
        }
        for (Skiff skiff : skiffs) {
            skiff.despawn();
        }
        for (Buoy buoy : buoys) {
            buoy.despawn();
        }
        if (crane != null) {
            crane.despawn();
        }
        courier = null;
        skiffs.clear();
        buoys.clear();
        crane = null;
        awake = false;
        watchers.clear();
    }

    /** Removes leftovers of an earlier run (reloads, crashes) in loaded chunks. */
    private void purgeStale() {
        World world = isle.config().world();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(TAG)) {
                entity.remove();
            }
        }
    }

    boolean ours(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    // ------------------------------------------------------------------ router hooks

    /** Every 20 ticks from the Origin loop: who is close enough to deserve a living harbour. */
    void scan(List<Player> onIsle) {
        if (!enabled || centre == null) {
            return;
        }
        watchers.clear();
        double r2 = wakeRadius * wakeRadius;
        for (Player player : onIsle) {
            Location at = player.getLocation();
            double dx = at.getX() - centre[0];
            double dz = at.getZ() - centre[2];
            if (dx * dx + dz * dz <= r2) {
                watchers.add(player);
            }
        }
        if (!watchers.isEmpty()) {
            sleepTicks = 0;
            awake = true;
        } else if (awake && (sleepTicks += 20) >= 1200) {
            // A minute with nobody around: tidy everything away until someone walks back.
            awake = false;
            if (courier != null) {
                courier.despawn();
                courier.reset();
            }
            for (Skiff skiff : skiffs) {
                skiff.despawn();
            }
            for (Buoy buoy : buoys) {
                buoy.despawn();
            }
            if (crane != null) {
                crane.despawn();
            }
        }
        if (courier != null) {
            courier.maybeSchedule();
        }
    }

    /** Every tick from the Origin loop. */
    void tick() {
        if (!enabled || !awake) {
            return;
        }
        tick++;
        World world = isle.config().world();
        if (world == null) {
            return;
        }
        if (courier != null) {
            safe("courier", () -> courier.tick(world));
        }
        for (Skiff skiff : skiffs) {
            safe("skiff", () -> skiff.tick(world));
        }
        if (tick % 20 == 0) {
            for (Buoy buoy : buoys) {
                safe("buoy", () -> buoy.tick(world));
            }
        }
        if (crane != null) {
            safe("crane", () -> crane.tick(world));
        }
    }

    private void safe(String what, Runnable run) {
        try {
            run.run();
        } catch (Throwable throwable) {
            if (tick % 200 == 0) {
                isle.plugin().getLogger().warning("Origin bay (" + what + "): " + throwable);
            }
        }
    }

    // ------------------------------------------------------------------ DEV

    String dev(String arg) {
        if (!enabled) {
            return "§cBay disabled in origin.yml (bay.enabled).";
        }
        awake = true;
        sleepTicks = 0;
        return switch (arg) {
            case "arrive", "courier" -> courier == null ? "§cNo courier configured." : courier.forceArrival();
            case "depart" -> courier == null ? "§cNo courier configured." : courier.forceDeparture();
            case "reset" -> {
                start();
                yield "§aBay rebuilt from origin.yml.";
            }
            default -> "§6Bay §8· §7courier " + (courier == null ? "§8none" : courier.state()) + " §8· §7skiffs " + skiffs.size()
                    + " §8· §7buoys " + buoys.size() + " §8· §7crane " + (crane == null ? "§8none" : crane.phaseName())
                    + " §8· §7watchers " + watchers.size() + " §8· §e/origin dev bay <arrive|depart|reset>";
        };
    }

    /** Short status for the journal / NPC hints: "anchored", "inbound", "away". */
    public String courierState() {
        return courier == null ? "away" : courier.publicState();
    }

    // ------------------------------------------------------------------ protection

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEnter(VehicleEnterEvent event) {
        if (ours(event.getVehicle()) && event.getEntered() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExit(VehicleExitEvent event) {
        if (ours(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (ours(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (ours(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (ours(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (ours(event.getRightClicked())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (event.getRightClicked() instanceof Villager) {
                OriginText.bar(player, "§7The rower nods, but doesn't stop rowing.");
            }
        }
    }

    // ------------------------------------------------------------------ shared helpers

    private List<Player> near(Location at, double radius) {
        List<Player> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Player player : watchers) {
            if (player.isOnline() && player.getWorld() == at.getWorld() && player.getLocation().distanceSquared(at) <= r2) {
                out.add(player);
            }
        }
        return out;
    }

    /**
     * Plays a sound for everyone within {@code radius}. Far listeners hear it from a point 12 blocks
     * toward the source (still directional, quieter with distance) — vanilla attenuation would cut a
     * skyship horn off long before the pier.
     */
    private void sound(Location at, Sound sound, float volume, float pitch, double radius) {
        for (Player player : near(at, radius)) {
            if (!isle.profiles().get(player).ambience) {
                continue;
            }
            Location ear = player.getLocation();
            double d = ear.distance(at);
            if (d <= 14.0d) {
                player.playSound(at, sound, SoundCategory.AMBIENT, volume, pitch);
                continue;
            }
            Vector toward = at.toVector().subtract(ear.toVector()).multiply(12.0d / d);
            float vol = (float) Math.max(0.3d, volume * Math.min(1.0d, 40.0d / d));
            player.playSound(ear.clone().add(toward), sound, SoundCategory.AMBIENT, vol, pitch);
        }
    }

    private void particles(Location at, Particle particle, int count, double spread, double speed, double radius) {
        List<Player> to = new ArrayList<>();
        for (Player player : near(at, radius)) {
            if (isle.profiles().get(player).particles) {
                to.add(player);
            }
        }
        if (!to.isEmpty()) {
            new com.destroystokyo.paper.ParticleBuilder(particle).location(at).count(count).offset(spread, spread * 0.4d, spread)
                    .extra(speed).receivers(to).force(true).spawn();
        }
    }

    private static boolean loaded(World world, double x, double z) {
        return world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    private static BlockData block(String raw) {
        try {
            return Bukkit.createBlockData(raw);
        } catch (IllegalArgumentException exception) {
            return Material.OAK_PLANKS.createBlockData();
        }
    }

    private static BlockDisplay display(World world, Location at, String blockState, float viewRange, int light) {
        return world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(block(blockState));
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setViewRange(viewRange);
            d.setShadowRadius(0.0f);
            if (light > 0) {
                d.setBrightness(new Display.Brightness(light, light));
            }
        });
    }

    /** Yaw-rotated box: centre {@code c}, size {@code s}, optional extra rotation applied in the model frame. */
    static Transformation box(float yawDeg, float cx, float cy, float cz, float sx, float sy, float sz, Quaternionf extra) {
        Quaternionf yaw = new Quaternionf().rotateY((float) Math.toRadians(-yawDeg));
        Quaternionf rot = extra == null ? new Quaternionf(yaw) : new Quaternionf(yaw).mul(extra);
        Vector3f half = new Vector3f(-sx / 2.0f, -sy / 2.0f, -sz / 2.0f);
        if (extra != null) {
            extra.transform(half);
        }
        Vector3f t = new Vector3f(cx, cy, cz).add(half);
        yaw.transform(t);
        return new Transformation(t, rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private static void morph(Display display, Transformation to, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(to);
    }

    private static void remove(Entity entity) {
        if (entity != null && entity.isValid()) {
            for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
                passenger.remove();
            }
            entity.remove();
        }
    }

    /** MC yaw of a heading (yaw 0 = +Z). */
    private static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float approachYaw(float from, float to, float maxStep) {
        float d = wrap(to - from);
        if (Math.abs(d) <= maxStep) {
            return to;
        }
        return wrap(from + Math.signum(d) * maxStep);
    }

    private static float wrap(float deg) {
        float d = deg % 360.0f;
        if (d > 180.0f) {
            d -= 360.0f;
        }
        if (d < -180.0f) {
            d += 360.0f;
        }
        return d;
    }

    // ================================================================== the courier skyship

    /** One rigid model part: block state, centre and size in the ship frame (+Z = bow), light, role. */
    private record Part(String block, float cx, float cy, float cz, float sx, float sy, float sz, int light, int role) {
    }

    private static final int STATIC = 0;
    private static final int BLADE_A = 1;
    private static final int BLADE_B = 2;

    private static List<Part> courierModel() {
        List<Part> m = new ArrayList<>();
        String hull = "minecraft:dark_oak_planks";
        String trim = "minecraft:stripped_spruce_log[axis=z]";
        String canvas = "minecraft:white_wool";
        String band = "minecraft:red_wool";
        // gondola hull
        m.add(new Part(hull, 0f, 0.6f, 0f, 4.6f, 1.6f, 10.0f, 0, STATIC));
        m.add(new Part(hull, 0f, 0.7f, 5.9f, 3.4f, 1.4f, 1.8f, 0, STATIC));
        m.add(new Part(hull, 0f, 0.85f, 7.2f, 2.0f, 1.1f, 0.9f, 0, STATIC));
        m.add(new Part(hull, 0f, 0.65f, -5.6f, 4.0f, 1.5f, 1.4f, 0, STATIC));
        m.add(new Part("minecraft:stripped_dark_oak_log[axis=z]", 0f, -0.35f, 0.5f, 0.9f, 0.5f, 13.0f, 0, STATIC));
        m.add(new Part("minecraft:spruce_planks", 0f, 1.45f, 0f, 4.2f, 0.1f, 10.6f, 0, STATIC));
        m.add(new Part(trim, 2.25f, 1.85f, 0f, 0.15f, 0.55f, 10.2f, 0, STATIC));
        m.add(new Part(trim, -2.25f, 1.85f, 0f, 0.15f, 0.55f, 10.2f, 0, STATIC));
        // cabin
        m.add(new Part("minecraft:spruce_planks", 0f, 2.4f, -3.6f, 3.0f, 1.9f, 2.8f, 0, STATIC));
        m.add(new Part(hull, 0f, 3.45f, -3.6f, 3.4f, 0.25f, 3.2f, 0, STATIC));
        m.add(new Part("minecraft:glass", 0f, 2.6f, -2.18f, 2.2f, 0.7f, 0.05f, 0, STATIC));
        // rigging posts deck → envelope
        for (float x : new float[]{-1.9f, 1.9f}) {
            for (float z : new float[]{-3.8f, 3.8f}) {
                m.add(new Part("minecraft:stripped_spruce_log[axis=y]", x, 5.05f, z, 0.25f, 7.1f, 0.25f, 0, STATIC));
            }
        }
        // cargo on deck
        m.add(new Part("minecraft:barrel[facing=up]", 1.2f, 1.95f, 2.5f, 0.9f, 0.9f, 0.9f, 0, STATIC));
        m.add(new Part("minecraft:barrel[facing=north]", -1.15f, 1.9f, 3.2f, 0.8f, 0.8f, 0.8f, 0, STATIC));
        m.add(new Part("minecraft:hay_block", 0.9f, 1.85f, 0.4f, 0.7f, 0.7f, 0.7f, 0, STATIC));
        // canvas wings + lights
        m.add(new Part(canvas, 3.45f, 1.9f, -0.8f, 2.3f, 0.08f, 3.0f, 0, STATIC));
        m.add(new Part(canvas, -3.45f, 1.9f, -0.8f, 2.3f, 0.08f, 3.0f, 0, STATIC));
        for (float x : new float[]{-2.3f, 2.3f}) {
            for (float z : new float[]{-4.8f, 4.8f}) {
                m.add(new Part("minecraft:lantern[hanging=true]", x, 1.0f, z, 0.6f, 0.6f, 0.6f, 15, STATIC));
            }
        }
        m.add(new Part("minecraft:sea_lantern", 0f, 1.25f, 7.75f, 0.35f, 0.35f, 0.35f, 15, STATIC));
        // stern flag
        m.add(new Part("minecraft:stripped_spruce_log[axis=y]", 0f, 3.9f, -4.95f, 0.1f, 2.0f, 0.1f, 0, STATIC));
        m.add(new Part(band, 0f, 4.55f, -5.5f, 0.05f, 0.7f, 1.0f, 0, STATIC));
        // envelope
        m.add(new Part(canvas, 0f, 11.5f, 0f, 6.0f, 4.6f, 15.0f, 0, STATIC));
        m.add(new Part(canvas, 0f, 14.15f, 0f, 4.4f, 0.7f, 13.0f, 0, STATIC));
        m.add(new Part(canvas, 0f, 8.85f, 0f, 4.4f, 0.7f, 13.0f, 0, STATIC));
        m.add(new Part(canvas, 3.35f, 11.5f, 0f, 0.7f, 3.2f, 13.0f, 0, STATIC));
        m.add(new Part(canvas, -3.35f, 11.5f, 0f, 0.7f, 3.2f, 13.0f, 0, STATIC));
        m.add(new Part(canvas, 0f, 11.5f, 8.0f, 4.6f, 3.6f, 1.0f, 0, STATIC));
        m.add(new Part(canvas, 0f, 11.5f, 8.85f, 2.8f, 2.2f, 0.8f, 0, STATIC));
        m.add(new Part(canvas, 0f, 11.5f, -8.0f, 4.6f, 3.6f, 1.0f, 0, STATIC));
        m.add(new Part(canvas, 0f, 11.5f, -8.85f, 2.8f, 2.2f, 0.8f, 0, STATIC));
        m.add(new Part(band, 0f, 11.5f, 3.5f, 6.08f, 4.68f, 0.6f, 0, STATIC));
        m.add(new Part(band, 0f, 11.5f, -3.5f, 6.08f, 4.68f, 0.6f, 0, STATIC));
        // tail fins
        m.add(new Part(band, 0f, 14.0f, -8.4f, 0.2f, 2.6f, 2.2f, 0, STATIC));
        m.add(new Part(band, 0f, 11.5f, -8.7f, 6.6f, 0.2f, 1.8f, 0, STATIC));
        // propeller (hub + two spinning blades)
        m.add(new Part("minecraft:iron_block", 0f, 1.0f, -6.55f, 0.4f, 0.4f, 0.4f, 0, STATIC));
        m.add(new Part("minecraft:spruce_planks", 0f, 1.0f, -6.8f, 0.3f, 2.6f, 0.12f, 0, BLADE_A));
        m.add(new Part("minecraft:spruce_planks", 0f, 1.0f, -6.8f, 0.3f, 2.6f, 0.12f, 0, BLADE_B));
        return m;
    }

    private enum CourierState { IDLE, ARRIVING, HOLDING, DEPARTING }

    private final class Courier {
        private final List<double[]> pathIn;
        private final List<double[]> pathOut;
        private final double[] drop;
        private final long everyTicks;
        private final long firstDelayTicks;
        private final int holdTicks;
        private final double cruise;

        private CourierState state = CourierState.IDLE;
        private long clock;
        private long nextArrival = -1L;
        private double s;
        private int held;
        private float yaw;
        private float drawnYaw = Float.NaN;
        private float spin;
        private double ropeLen;
        private double ropeTarget;
        private boolean crateDown;
        private boolean crateHanded;
        private boolean horned;

        private BlockDisplay root;
        private final List<BlockDisplay> parts = new ArrayList<>();
        private final List<Part> model = courierModel();
        private BlockDisplay rope;
        private BlockDisplay crate;

        Courier(ConfigurationSection cs) {
            pathIn = spline(points(cs.getStringList("path-in")));
            pathOut = spline(points(cs.getStringList("path-out")));
            drop = OriginConfig.nums(cs.getString("drop", "300.5 61.2 -464.5"));
            everyTicks = Math.max(2, cs.getLong("every-minutes", 6L)) * 1200L;
            firstDelayTicks = Math.max(5, cs.getLong("first-delay-seconds", 20L)) * 20L;
            holdTicks = (int) Math.max(30, cs.getLong("hold-seconds", 110L)) * 20;
            cruise = Math.max(0.05d, Math.min(0.6d, cs.getDouble("speed", 0.22d)));
        }

        private List<double[]> points(List<String> raw) {
            List<double[]> out = new ArrayList<>();
            for (String line : raw) {
                double[] p = OriginConfig.nums(line);
                if (p != null && p.length >= 3) {
                    out.add(p);
                }
            }
            return out;
        }

        String state() {
            return state.name().toLowerCase() + (state == CourierState.IDLE && nextArrival >= 0
                    ? " (next in " + Math.max(0, (nextArrival - clock) / 20) + "s)" : "");
        }

        String publicState() {
            return switch (state) {
                case ARRIVING -> "inbound";
                case HOLDING -> "anchored";
                case DEPARTING -> "leaving";
                default -> "away";
            };
        }

        void reset() {
            state = CourierState.IDLE;
            nextArrival = -1L;
        }

        /** Every 20 ticks: queue the next arrival; a newcomer at the harbour gets one soon. */
        void maybeSchedule() {
            if (state != CourierState.IDLE || pathIn.size() < 2) {
                return;
            }
            boolean newcomer = false;
            for (Player player : watchers) {
                if (!isle.profiles().get(player).flag("bay_courier")) {
                    newcomer = true;
                    break;
                }
            }
            long soonest = clock + firstDelayTicks;
            if (nextArrival < 0) {
                nextArrival = newcomer ? soonest : clock + Math.min(everyTicks, firstDelayTicks * 3);
            } else if (newcomer && nextArrival > soonest + 400) {
                nextArrival = soonest;
            }
        }

        String forceArrival() {
            despawn();
            state = CourierState.IDLE;
            nextArrival = clock + 20;
            return "§aThe Halcyon is on her way in §8(about " + (int) (length(pathIn) / (cruise * 20.0d)) + "s)§a.";
        }

        String forceDeparture() {
            if (state != CourierState.HOLDING) {
                return "§7The Halcyon isn't anchored right now §8(" + state() + ")§7.";
            }
            held = holdTicks;
            return "§aThe Halcyon casts off.";
        }

        void tick(World world) {
            clock++;
            switch (state) {
                case IDLE -> {
                    if (nextArrival >= 0 && clock >= nextArrival) {
                        state = CourierState.ARRIVING;
                        s = 0.0d;
                        held = 0;
                        horned = false;
                        crateDown = false;
                        crateHanded = false;
                        ropeLen = 0.0d;
                        ropeTarget = 0.0d;
                    }
                }
                case ARRIVING -> {
                    double total = length(pathIn);
                    double remaining = total - s;
                    double v = cruise * Math.max(0.16d, Math.min(1.0d, remaining / 45.0d));
                    s = Math.min(total, s + v);
                    fly(world, pathIn, s, true);
                    if (s >= total - 1.0e-3) {
                        state = CourierState.HOLDING;
                        held = 0;
                        anchored(world);
                    }
                }
                case HOLDING -> hold(world);
                case DEPARTING -> {
                    double total = length(pathOut);
                    double v = cruise * Math.max(0.12d, Math.min(1.0d, 0.12d + s / 50.0d));
                    s = Math.min(total, s + v);
                    if (!fly(world, pathOut, s, false) || s >= total - 1.0e-3) {
                        despawn();
                        state = CourierState.IDLE;
                        nextArrival = clock + everyTicks;
                    }
                }
            }
        }

        /** Moves the ship along a path; spawns it lazily in loaded chunks. False when it left the loaded world. */
        private boolean fly(World world, List<double[]> path, double at, boolean arriving) {
            double[] p = sample(path, at);
            double[] ahead = sample(path, Math.min(length(path), at + 3.0d));
            if (!loaded(world, p[0], p[2])) {
                if (root != null) {
                    despawn();
                }
                return !(!arriving && root == null && at > 8.0d);
            }
            double dx = ahead[0] - p[0];
            double dz = ahead[2] - p[2];
            if (dx * dx + dz * dz > 1.0e-4) {
                float target = yawOf(dx, dz);
                yaw = root == null ? target : approachYaw(yaw, target, 1.6f);
            }
            if (root == null || !root.isValid()) {
                spawn(world, new Location(world, p[0], p[1], p[2]));
            }
            if (clock % 4 == 0) {
                root.teleport(new Location(world, p[0], p[1], p[2]), TeleportFlag.EntityState.RETAIN_PASSENGERS);
                pose(4);
                sounds(world, p, true);
            }
            if (clock % 2 == 0) {
                spinBlades(2);
            }
            return true;
        }

        private void hold(World world) {
            if (root == null || !root.isValid()) {
                double[] end = pathIn.get(pathIn.size() - 1);
                if (!loaded(world, end[0], end[2])) {
                    return;
                }
                spawn(world, new Location(world, end[0], end[1], end[2]));
            }
            held++;
            double[] end = pathIn.get(pathIn.size() - 1);
            if (clock % 4 == 0) {
                double bob = Math.sin(clock * Math.PI * 2.0d / 120.0d) * 0.22d;
                root.teleport(new Location(world, end[0], end[1] + bob, end[2]), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            if (held % 2 == 0 && held < 60) {
                spinBlades(2); // winding down
            }
            Location at = root.getLocation();
            if (!horned && held >= 10) {
                horned = true;
                sound(at, Sound.ITEM_GOAT_HORN_SOUND_1, 3.2f, 0.82f, 190.0d);
                Location wharf = new Location(world, 293.5d, 65.0d, -293.5d);
                sound(wharf, Sound.BLOCK_BELL_USE, 1.6f, 0.9f, 140.0d);
                for (Player player : near(at, 150.0d)) {
                    OriginProfile profile = isle.profiles().get(player);
                    if (profile.mark("bay_courier") && !isle.quiet(player)) {
                        OriginText.bar(player, "§b⛵ The Halcyon §8· §7the supply skyship is in. Watch the crate come down.");
                    }
                }
            }
            if (held == 40) {
                particles(at.clone().add(0, 0.5, 0), Particle.CLOUD, 30, 2.5d, 0.02d, 120.0d);
                sound(at, Sound.BLOCK_CHAIN_PLACE, 1.4f, 0.6f, 80.0d);
            }
            // Lower the crate once the skiff waits under the keel (or late in the hold regardless).
            if (!crateDown && drop != null && held > 80 && (skiffWaiting() || held > holdTicks - 900)) {
                crateDown = true;
                ropeTarget = Math.max(2.0d, at.getY() - 0.6d - (drop[1] + 0.95d));
                sound(at, Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.8f, 80.0d);
            }
            if (crateDown && !crateHanded && ropeLen < ropeTarget && held % 4 == 0) {
                ropeLen = Math.min(ropeTarget, ropeLen + 0.9d);
                drawRope(4);
                if (held % 12 == 0) {
                    sound(at, Sound.BLOCK_CHAIN_STEP, 0.9f, 0.7f, 60.0d);
                }
                if (ropeLen >= ropeTarget) {
                    handCrate(world);
                }
            }
            if (crateHanded && ropeLen > 0.0d && held % 4 == 0) {
                ropeLen = Math.max(0.0d, ropeLen - 1.4d);
                drawRope(4);
            }
            if (clock % 100 == 0) {
                particles(at.clone().add(0, 1.0, -6.6), Particle.CAMPFIRE_COSY_SMOKE, 2, 0.2d, 0.01d, 90.0d);
                sound(at, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.6f, 0.5f, 50.0d);
            }
            if (held >= holdTicks && ropeLen <= 0.0d) {
                if (!crateHanded) {
                    handCrate(world);
                }
                state = CourierState.DEPARTING;
                s = 0.0d;
                sound(at, Sound.ITEM_GOAT_HORN_SOUND_1, 2.6f, 1.0f, 190.0d);
                particles(at.clone().add(0, 0.5, 0), Particle.CLOUD, 40, 3.0d, 0.03d, 120.0d);
            }
        }

        private boolean skiffWaiting() {
            for (Skiff skiff : skiffs) {
                if (skiff.waitingForCrate) {
                    return true;
                }
            }
            return false;
        }

        private void handCrate(World world) {
            crateHanded = true;
            Skiff taker = null;
            for (Skiff skiff : skiffs) {
                if (skiff.waitingForCrate) {
                    taker = skiff;
                    break;
                }
            }
            if (crate != null) {
                Location at = crate.getLocation();
                crate.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
                if (taker != null) {
                    taker.takeCrate(world);
                    sound(at, Sound.BLOCK_BARREL_CLOSE, 1.0f, 0.8f, 60.0d);
                } else if (drop != null) {
                    Location splash = new Location(world, drop[0], drop[1], drop[2]);
                    particles(splash, Particle.SPLASH, 30, 0.6d, 0.1d, 80.0d);
                    sound(splash, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.8f, 70.0d);
                }
            }
        }

        private void anchored(World world) {
            if (root == null) {
                return;
            }
            Location at = root.getLocation();
            particles(at.clone().add(0, -0.4, 0), Particle.CLOUD, 25, 2.0d, 0.02d, 120.0d);
        }

        private void sounds(World world, double[] p, boolean moving) {
            if (clock % 40 == 0) {
                Location at = new Location(world, p[0], p[1], p[2]);
                sound(at, Sound.ENTITY_PHANTOM_FLAP, 1.4f, 0.5f, 90.0d);
                if (ThreadLocalRandom.current().nextDouble() < 0.3d) {
                    sound(at, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.7f, 0.5f, 60.0d);
                }
            }
        }

        private void spawn(World world, Location at) {
            despawn();
            root = display(world, at, "minecraft:air", 4.0f, 0);
            root.setTeleportDuration(4);
            for (Part part : model) {
                BlockDisplay d = display(world, at, part.block(), 4.0f, part.light());
                parts.add(d);
                root.addPassenger(d);
            }
            rope = display(world, at, "minecraft:chain[axis=y]", 4.0f, 0);
            crate = display(world, at, "minecraft:barrel[facing=up]", 4.0f, 0);
            root.addPassenger(rope);
            root.addPassenger(crate);
            drawnYaw = Float.NaN;
            pose(0);
            drawRope(0);
        }

        /** Re-applies the yaw to every part when it changed noticeably. */
        private void pose(int ticks) {
            if (!Float.isNaN(drawnYaw) && Math.abs(wrap(yaw - drawnYaw)) < 0.8f) {
                return;
            }
            drawnYaw = yaw;
            for (int i = 0; i < parts.size() && i < model.size(); i++) {
                Part p = model.get(i);
                if (p.role() == STATIC) {
                    morph(parts.get(i), box(yaw, p.cx(), p.cy(), p.cz(), p.sx(), p.sy(), p.sz(), null), ticks);
                }
            }
            spinBlades(ticks);
        }

        private void spinBlades(int ticks) {
            spin = (spin + 47.0f) % 360.0f;
            for (int i = 0; i < parts.size() && i < model.size(); i++) {
                Part p = model.get(i);
                if (p.role() == BLADE_A || p.role() == BLADE_B) {
                    float roll = spin + (p.role() == BLADE_B ? 90.0f : 0.0f);
                    Quaternionf extra = new Quaternionf().rotateZ((float) Math.toRadians(roll));
                    morph(parts.get(i), box(yaw, p.cx(), p.cy(), p.cz(), p.sx(), p.sy(), p.sz(), extra), ticks);
                }
            }
        }

        private void drawRope(int ticks) {
            float len = (float) Math.max(0.001d, ropeLen);
            morph(rope, new Transformation(new Vector3f(-0.5f, -0.6f - len, -0.5f), new Quaternionf(), new Vector3f(1.0f, len, 1.0f),
                    new Quaternionf()), ticks);
            if (!crateHanded) {
                morph(crate, new Transformation(new Vector3f(-0.45f, -0.6f - len - 0.9f, -0.45f), new Quaternionf(),
                        new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()), ticks);
            }
        }

        void despawn() {
            for (BlockDisplay d : parts) {
                remove(d);
            }
            parts.clear();
            remove(rope);
            remove(crate);
            rope = null;
            crate = null;
            remove(root);
            root = null;
        }
    }

    // ------------------------------------------------------------------ path helpers (Catmull-Rom, sampled ~1 block)

    private static List<double[]> spline(List<double[]> pts) {
        List<double[]> out = new ArrayList<>();
        if (pts.size() < 2) {
            out.addAll(pts);
            return withLengths(out);
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] p0 = pts.get(Math.max(0, i - 1));
            double[] p1 = pts.get(i);
            double[] p2 = pts.get(i + 1);
            double[] p3 = pts.get(Math.min(pts.size() - 1, i + 2));
            double seg = Math.sqrt(sq(p2[0] - p1[0]) + sq(p2[1] - p1[1]) + sq(p2[2] - p1[2]));
            int steps = Math.max(2, (int) Math.ceil(seg));
            for (int k = 0; k < steps; k++) {
                double t = k / (double) steps;
                out.add(new double[]{cr(p0[0], p1[0], p2[0], p3[0], t), cr(p0[1], p1[1], p2[1], p3[1], t), cr(p0[2], p1[2], p2[2], p3[2], t), 0});
            }
        }
        double[] last = pts.get(pts.size() - 1);
        out.add(new double[]{last[0], last[1], last[2], 0});
        return withLengths(out);
    }

    private static List<double[]> withLengths(List<double[]> pts) {
        double acc = 0.0d;
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            if (p.length < 4) {
                p = new double[]{p[0], p[1], p[2], 0};
                pts.set(i, p);
            }
            if (i > 0) {
                double[] q = pts.get(i - 1);
                acc += Math.sqrt(sq(p[0] - q[0]) + sq(p[1] - q[1]) + sq(p[2] - q[2]));
            }
            p[3] = acc;
        }
        return pts;
    }

    private static double length(List<double[]> path) {
        return path.isEmpty() ? 0.0d : path.get(path.size() - 1)[3];
    }

    private static double[] sample(List<double[]> path, double s) {
        if (path.isEmpty()) {
            return new double[]{0, 0, 0};
        }
        if (s <= 0.0d) {
            return path.get(0);
        }
        for (int i = 1; i < path.size(); i++) {
            double[] b = path.get(i);
            if (b[3] >= s) {
                double[] a = path.get(i - 1);
                double span = b[3] - a[3];
                double t = span <= 1.0e-9 ? 1.0d : (s - a[3]) / span;
                return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
            }
        }
        return path.get(path.size() - 1);
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5d * ((2.0d * p1) + (-p0 + p2) * t + (2.0d * p0 - 5.0d * p1 + 4.0d * p2 - p3) * t2 + (-p0 + 3.0d * p1 - 3.0d * p2 + p3) * t3);
    }

    private static double sq(double v) {
        return v * v;
    }

    // ================================================================== fishing skiffs

    private final class Skiff {
        private final String id;
        private final List<double[]> loop;
        private final double surface;
        private final double speed = 0.12d;
        private Boat boat;
        private Villager rower;
        private BlockDisplay cargo;
        private int seg;
        private double t;
        private float yaw = Float.NaN;
        private int pause;
        private int fishing;
        private Location bobber;
        private ItemDisplay catchFx;
        private int catchTicks;
        private boolean waitingForCrate;
        private int carryTicks;

        Skiff(String id, List<double[]> loop, double surface) {
            this.id = id;
            this.loop = loop;
            this.surface = surface;
            this.seg = Math.abs(id.hashCode()) % loop.size();
        }

        private double[] vertex(int i) {
            return loop.get(((i % loop.size()) + loop.size()) % loop.size());
        }

        void tick(World world) {
            double[] a = vertex(seg);
            double[] b = vertex(seg + 1);
            double x = a[0] + (b[0] - a[0]) * t;
            double z = a[1] + (b[1] - a[1]) * t;
            if (!loaded(world, x, z)) {
                despawn();
                return;
            }
            if (boat == null || !boat.isValid() || rower == null || !rower.isValid()) {
                spawn(world, x, z);
            }
            float target = yawOf(b[0] - a[0], b[1] - a[1]);
            yaw = Float.isNaN(yaw) ? target : approachYaw(yaw, target, 4.0f);
            double bob = Math.sin((tick + id.hashCode()) * 0.08d) * 0.035d;
            if (waitingForCrate) {
                if (courier == null || courier.state != CourierState.HOLDING || courier.crateHanded) {
                    waitingForCrate = false;
                }
            } else if (pause > 0) {
                pause--;
                fishingTick(world, x, z);
            } else {
                t += speed / Math.max(0.5d, Math.hypot(b[0] - a[0], b[1] - a[1]));
                if (t >= 1.0d) {
                    t = 0.0d;
                    seg = (seg + 1) % loop.size();
                    arrived(world, vertex(seg));
                }
            }
            if (boat != null) {
                boat.teleport(new Location(world, x, surface + bob, z, yaw, 0.0f), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            if (catchFx != null) {
                catchTick(world, x, z);
            }
            if (cargo != null && carryTicks > 0 && --carryTicks == 0) {
                remove(cargo);
                cargo = null;
                if (boat != null) {
                    sound(boat.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.7f, 1.1f, 40.0d);
                }
            }
        }

        private void arrived(World world, double[] v) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            // The skiff that passes under the anchored Halcyon waits there for the crate.
            if (courier != null && courier.state == CourierState.HOLDING && !courier.crateHanded && courier.drop != null
                    && Math.hypot(v[0] - courier.drop[0], v[1] - courier.drop[2]) < 4.0d && !anyWaiting()) {
                waitingForCrate = true;
                return;
            }
            if (rng.nextDouble() < 0.35d) {
                pause = 160 + rng.nextInt(140);
                fishing = 0;
                bobber = null;
            }
        }

        private boolean anyWaiting() {
            for (Skiff other : skiffs) {
                if (other.waitingForCrate) {
                    return true;
                }
            }
            return false;
        }

        private void fishingTick(World world, double x, double z) {
            fishing++;
            if (boat == null) {
                return;
            }
            double side = Math.toRadians(yaw + 90.0d);
            if (bobber == null) {
                bobber = new Location(world, x - Math.sin(side) * 3.5d, surface + 0.3d, z + Math.cos(side) * 3.5d);
            }
            if (fishing == 8) {
                if (rower != null) {
                    rower.swingMainHand();
                }
                sound(bobber, Sound.ENTITY_FISHING_BOBBER_THROW, 0.6f, 0.9f, 32.0d);
            }
            if (fishing > 12 && fishing % 30 == 0) {
                particles(bobber, Particle.FISHING, 3, 0.15d, 0.0d, 36.0d);
            }
            if (pause == 40) {
                particles(bobber, Particle.SPLASH, 18, 0.3d, 0.1d, 40.0d);
                sound(bobber, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.7f, 1.0f, 36.0d);
            }
            if (pause == 20 && catchFx == null) {
                if (rower != null) {
                    rower.swingMainHand();
                }
                sound(bobber, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.7f, 1.0f, 36.0d);
                Material fish = switch (ThreadLocalRandom.current().nextInt(5)) {
                    case 0, 1 -> Material.COD;
                    case 2, 3 -> Material.SALMON;
                    default -> Material.TROPICAL_FISH;
                };
                catchFx = world.spawn(bobber, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(fish));
                    d.setPersistent(false);
                    d.addScoreboardTag(TAG);
                    d.setTeleportDuration(3);
                    d.setViewRange(1.0f);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
                });
                catchTicks = 0;
            }
        }

        private void catchTick(World world, double x, double z) {
            catchTicks++;
            if (bobber == null || catchTicks > 18) {
                if (catchFx != null) {
                    sound(catchFx.getLocation(), Sound.ENTITY_COD_FLOP, 0.6f, 1.1f, 30.0d);
                }
                remove(catchFx);
                catchFx = null;
                return;
            }
            if (catchTicks % 3 == 0) {
                double k = catchTicks / 18.0d;
                double px = bobber.getX() + (x - bobber.getX()) * k;
                double pz = bobber.getZ() + (z - bobber.getZ()) * k;
                double py = surface + 0.3d + Math.sin(k * Math.PI) * 2.6d;
                catchFx.teleport(new Location(world, px, py, pz, (float) (catchTicks * 40), 0.0f));
            }
        }

        void takeCrate(World world) {
            waitingForCrate = false;
            if (boat == null || !boat.isValid()) {
                return;
            }
            Location at = boat.getLocation();
            cargo = world.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(block("minecraft:barrel[facing=up]"));
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
                d.setViewRange(2.0f);
                d.setTransformation(new Transformation(new Vector3f(-0.4f, 0.1f, -0.4f), new Quaternionf(), new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
            });
            if (!boat.addPassenger(cargo)) {
                remove(cargo);
                cargo = null;
                return;
            }
            carryTicks = 20 * 70;
            particles(at.clone().add(0, 0.6, 0), Particle.SPLASH, 12, 0.5d, 0.1d, 50.0d);
        }

        private void spawn(World world, double x, double z) {
            despawn();
            Location at = new Location(world, x, surface, z, Float.isNaN(yaw) ? 0.0f : yaw, 0.0f);
            boat = world.spawn(at, Boat.class, b -> {
                b.setPersistent(false);
                b.addScoreboardTag(TAG);
                b.setGravity(false);
                b.setInvulnerable(true);
                try {
                    b.setBoatType(Boat.Type.SPRUCE);
                } catch (Throwable ignored) {
                    // newer servers split boat kinds into entity types; the default boat is fine
                }
            });
            rower = world.spawn(at, Villager.class, v -> {
                v.setPersistent(false);
                v.addScoreboardTag(TAG);
                v.setAI(false);
                v.setSilent(true);
                v.setInvulnerable(true);
                v.setCollidable(false);
                v.setRemoveWhenFarAway(false);
                v.setProfession(Villager.Profession.FISHERMAN);
                v.setVillagerType(Villager.Type.SWAMP);
                v.setVillagerLevel(2);
                v.getEquipment().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
            });
            boat.addPassenger(rower);
        }

        void despawn() {
            remove(catchFx);
            catchFx = null;
            remove(cargo);
            cargo = null;
            remove(rower);
            rower = null;
            remove(boat);
            boat = null;
            waitingForCrate = false;
        }
    }

    // ================================================================== channel buoys

    private final class Buoy {
        private final double[] at;
        private final String colour;
        private BlockDisplay body;
        private BlockDisplay band;
        private BlockDisplay lamp;
        private boolean up;

        Buoy(double[] at, String colour) {
            this.at = at;
            this.colour = colour.toLowerCase(java.util.Locale.ROOT);
        }

        void tick(World world) {
            if (!loaded(world, at[0], at[2])) {
                despawn();
                return;
            }
            if (body == null || !body.isValid()) {
                Location l = new Location(world, at[0], at[1], at[2]);
                String paint = switch (colour) {
                    case "green" -> "minecraft:green_concrete";
                    case "yellow" -> "minecraft:yellow_concrete";
                    default -> "minecraft:red_concrete";
                };
                body = display(world, l, paint, 2.0f, 0);
                band = display(world, l, "minecraft:white_concrete", 2.0f, 0);
                lamp = display(world, l, "minecraft:lantern[hanging=false]", 2.0f, 15);
                up = false;
                place(0);
            }
            up = !up;
            place(20);
            if (isle.night() && ThreadLocalRandom.current().nextDouble() < 0.5d) {
                Location glow = new Location(world, at[0], at[1] + 1.4d, at[2]);
                List<Player> to = near(glow, 64.0d);
                if (!to.isEmpty()) {
                    new com.destroystokyo.paper.ParticleBuilder(Particle.DUST).location(glow).count(2).offset(0.08, 0.08, 0.08)
                            .data(new Particle.DustOptions("green".equals(colour) ? Color.fromRGB(120, 255, 140) : Color.fromRGB(255, 120, 100), 1.2f))
                            .receivers(to).force(true).spawn();
                }
            }
        }

        private void place(int ticks) {
            float lift = up ? 0.12f : -0.06f;
            float tilt = (float) Math.toRadians(up ? 4.0d : -3.0d);
            Quaternionf rock = new Quaternionf().rotateX(tilt);
            morph(body, box(0.0f, 0.0f, -0.05f + lift, 0.0f, 0.75f, 0.95f, 0.75f, rock), ticks);
            morph(band, box(0.0f, 0.0f, 0.18f + lift, 0.0f, 0.78f, 0.16f, 0.78f, rock), ticks);
            morph(lamp, box(0.0f, 0.0f, 0.62f + lift, 0.03f, 0.42f, 0.42f, 0.42f, rock), ticks);
        }

        void despawn() {
            remove(body);
            remove(band);
            remove(lamp);
            body = null;
            band = null;
            lamp = null;
        }
    }

    // ================================================================== the dock crane

    private final class Crane {
        private final double[] base;
        private final float height;
        private final float pickYaw;
        private final float dropYaw;
        private final float pickR;
        private final float dropR;
        private final float pickDrop;
        private final float dropDrop;
        private final float liftDrop = 2.2f;
        private final float jibLen;
        private final double[] punt;
        private final List<double[]> pile = new ArrayList<>();

        private final List<BlockDisplay> fixed = new ArrayList<>();
        private BlockDisplay head;
        private BlockDisplay jib;
        private BlockDisplay counter;
        private BlockDisplay weight;
        private BlockDisplay trolley;
        private BlockDisplay rope;
        private BlockDisplay load;
        private BlockDisplay lamp;
        private Boat puntBoat;
        private Villager puntHand;

        private float yaw;
        private float radius;
        private float drop;
        private boolean loaded = true;
        private int phase;
        private int timer;

        Crane(ConfigurationSection cs, double[] base) {
            this.base = base;
            this.height = (float) cs.getDouble("height", 10.0d);
            double[] pick = OriginConfig.nums(cs.getString("pick"));
            double[] dropAt = OriginConfig.nums(cs.getString("drop"));
            if (pick == null) {
                pick = new double[]{base[0] - 4.0d, base[1], base[2]};
            }
            if (dropAt == null) {
                dropAt = new double[]{base[0] + 6.0d, base[1] - 2.0d, base[2] - 7.0d};
            }
            this.pickYaw = yawOf(pick[0] - base[0], pick[2] - base[2]);
            this.dropYaw = yawOf(dropAt[0] - base[0], dropAt[2] - base[2]);
            this.pickR = (float) Math.max(2.0d, Math.hypot(pick[0] - base[0], pick[2] - base[2]));
            this.dropR = (float) Math.max(2.0d, Math.hypot(dropAt[0] - base[0], dropAt[2] - base[2]));
            // rope length so the 0.8 load rests on the target surface
            this.pickDrop = (float) (base[1] + height + 0.15d - 0.8d - pick[1]);
            this.dropDrop = (float) (base[1] + height + 0.15d - 0.8d - dropAt[1]);
            this.punt = OriginConfig.nums(cs.getString("punt"));
            for (String raw : cs.getStringList("pile")) {
                double[] p = OriginConfig.nums(raw);
                if (p != null && p.length >= 3) {
                    pile.add(p);
                }
            }
            this.jibLen = Math.max(pickR, dropR) + 0.6f;
            this.yaw = pickYaw;
            this.radius = pickR;
            this.drop = pickDrop;
        }

        String phaseName() {
            return head == null ? "asleep" : "phase " + phase;
        }

        void tick(World world) {
            if (!loaded(world, base[0], base[2])) {
                despawn();
                return;
            }
            if (head == null || !head.isValid()) {
                spawn(world);
            }
            if (puntBoat != null && puntBoat.isValid() && tick % 4 == 0 && punt != null) {
                double bob = Math.sin(tick * 0.07d) * 0.04d;
                puntBoat.teleport(new Location(world, punt[0], punt[1] + bob, punt[2], (float) (punt.length > 3 ? punt[3] : 0.0d), 0.0f),
                        TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            Location at = new Location(world, base[0], base[1], base[2]);
            if (near(at, 64.0d).isEmpty()) {
                return; // frozen mid-motion while nobody watches
            }
            if (tick % 5 != 0) {
                return;
            }
            timer++;
            switch (phase) {
                case 0 -> step(at, yaw, radius, liftDrop, 6, 1, Sound.BLOCK_CHAIN_STEP);                // hoist
                case 1 -> swing(at, dropYaw, dropR, 12, 2);                                              // swing out
                case 2 -> step(at, yaw, radius, dropDrop, 7, 3, Sound.BLOCK_CHAIN_STEP);                // lower
                case 3 -> {                                                                              // release
                    if (timer == 1) {
                        loaded = false;
                        draw(5);
                        Location tip = tip(at);
                        sound(tip, Sound.BLOCK_WOOD_PLACE, 0.9f, 0.7f, 40.0d);
                        particles(tip, Particle.SPLASH, 8, 0.4d, 0.05d, 40.0d);
                        if (puntHand != null && puntHand.isValid()) {
                            puntHand.swingMainHand();
                        }
                    }
                    if (timer >= 6) {
                        next(4);
                    }
                }
                case 4 -> step(at, yaw, radius, liftDrop, 5, 5, Sound.BLOCK_CHAIN_STEP);                // hoist empty
                case 5 -> swing(at, pickYaw, pickR, 12, 6);                                              // swing back
                case 6 -> step(at, yaw, radius, pickDrop, 6, 7, Sound.BLOCK_CHAIN_STEP);                // lower to the pile
                case 7 -> {                                                                              // hook a new crate
                    if (timer == 4) {
                        loaded = true;
                        draw(3);
                        sound(tip(at), Sound.BLOCK_CHAIN_HIT, 0.8f, 1.0f, 40.0d);
                    }
                    if (timer >= 10) {
                        next(0);
                    }
                }
                default -> next(0);
            }
        }

        private void next(int phase) {
            this.phase = phase;
            this.timer = 0;
        }

        private void step(Location at, float y, float r, float targetDrop, int steps, int nextPhase, Sound cue) {
            float delta = (targetDrop - drop) / Math.max(1, steps - timer + 1);
            drop += delta;
            draw(5);
            if (timer % 3 == 1) {
                sound(tip(at), cue, 0.5f, 0.8f, 36.0d);
            }
            if (timer >= steps) {
                drop = targetDrop;
                draw(5);
                next(nextPhase);
            }
        }

        private void swing(Location at, float targetYaw, float targetR, int steps, int nextPhase) {
            int left = Math.max(1, steps - timer + 1);
            yaw = wrap(yaw + wrap(targetYaw - yaw) / left);
            radius += (targetR - radius) / left;
            draw(5);
            if (timer == 1) {
                sound(at.clone().add(0, height, 0), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.7f, 0.55f, 48.0d);
            }
            if (timer >= steps) {
                yaw = targetYaw;
                radius = targetR;
                draw(5);
                next(nextPhase);
            }
        }

        private Location tip(Location at) {
            double rad = Math.toRadians(yaw);
            return at.clone().add(-Math.sin(rad) * radius, height + 0.15d - drop, Math.cos(rad) * radius);
        }

        private void spawn(World world) {
            despawn();
            Location at = new Location(world, base[0], base[1], base[2]);
            float h = height;
            // fixed: plinth, mast, two braces, a crate pile at the pick spot
            add(world, at, "minecraft:stone_bricks", box(0f, 0f, 0.3f, 0f, 1.7f, 0.6f, 1.7f, null));
            add(world, at, "minecraft:stripped_dark_oak_log[axis=y]", box(0f, 0f, 0.6f + (h - 0.6f) / 2f, 0f, 0.7f, h - 0.6f, 0.7f, null));
            add(world, at, "minecraft:dark_oak_planks", box(0f, 0.75f, 2.1f, 0f, 0.22f, 3.6f, 0.22f, new Quaternionf().rotateZ((float) Math.toRadians(-14))));
            add(world, at, "minecraft:dark_oak_planks", box(0f, -0.75f, 2.1f, 0f, 0.22f, 3.6f, 0.22f, new Quaternionf().rotateZ((float) Math.toRadians(14))));
            // crate pile beside the pick spot (absolute cells from origin.yml, checked free on the map)
            for (double[] c : pile) {
                float rx = (float) (c[0] - base[0]);
                float ry = (float) (c[1] - base[1]);
                float rz = (float) (c[2] - base[2]);
                add(world, at, c.length > 3 && c[3] > 0 ? "minecraft:barrel[facing=east]" : "minecraft:barrel[facing=up]",
                        box(0f, rx, ry + 0.4f, rz, 0.8f, 0.8f, 0.8f, null));
            }
            // moving: head, jib, counter-jib, counterweight, trolley, rope, load, tip lamp
            head = display(world, at, "minecraft:spruce_planks", 2.5f, 0);
            jib = display(world, at, "minecraft:dark_oak_planks", 2.5f, 0);
            counter = display(world, at, "minecraft:dark_oak_planks", 2.5f, 0);
            weight = display(world, at, "minecraft:cobblestone", 2.5f, 0);
            trolley = display(world, at, "minecraft:iron_block", 2.5f, 0);
            rope = display(world, at, "minecraft:chain[axis=y]", 2.5f, 0);
            load = display(world, at, "minecraft:barrel[facing=up]", 2.5f, 0);
            lamp = display(world, at, "minecraft:lantern[hanging=true]", 2.5f, 15);
            draw(0);
            if (punt != null && loaded(world, punt[0], punt[2])) {
                Location p = new Location(world, punt[0], punt[1], punt[2], (float) (punt.length > 3 ? punt[3] : 0.0d), 0.0f);
                puntBoat = world.spawn(p, Boat.class, b -> {
                    b.setPersistent(false);
                    b.addScoreboardTag(TAG);
                    b.setGravity(false);
                    b.setInvulnerable(true);
                });
                puntHand = world.spawn(p, Villager.class, v -> {
                    v.setPersistent(false);
                    v.addScoreboardTag(TAG);
                    v.setAI(false);
                    v.setSilent(true);
                    v.setInvulnerable(true);
                    v.setCollidable(false);
                    v.setProfession(Villager.Profession.MASON);
                    v.setVillagerType(Villager.Type.TAIGA);
                });
                puntBoat.addPassenger(puntHand);
            }
        }

        private void add(World world, Location at, String state, Transformation t) {
            BlockDisplay d = display(world, at, state, 2.5f, 0);
            d.setTransformation(t);
            fixed.add(d);
        }

        private void draw(int ticks) {
            float h = height;
            morph(head, box(yaw, 0f, h + 0.4f, 0f, 1.2f, 0.8f, 1.2f, null), ticks);
            morph(jib, box(yaw, 0f, h + 0.6f, jibLen / 2f, 0.5f, 0.45f, jibLen, null), ticks);
            morph(counter, box(yaw, 0f, h + 0.6f, -1.6f, 0.5f, 0.45f, 3.2f, null), ticks);
            morph(weight, box(yaw, 0f, h + 0.3f, -3.0f, 1.3f, 1.2f, 1.3f, null), ticks);
            morph(trolley, box(yaw, 0f, h + 0.25f, radius, 0.5f, 0.25f, 0.6f, null), ticks);
            float d = Math.max(0.05f, drop);
            morph(rope, box(yaw, 0f, h + 0.15f - d / 2f, radius, 1.0f, d, 1.0f, null), ticks);
            float s = loaded ? 0.8f : 0.001f;
            morph(load, box(yaw, 0f, h + 0.15f - d - 0.4f, radius, s, s, s, null), ticks);
            morph(lamp, box(yaw, 0f, h + 0.05f, jibLen - 0.25f, 0.5f, 0.5f, 0.5f, null), ticks);
        }

        void despawn() {
            for (BlockDisplay d : fixed) {
                remove(d);
            }
            fixed.clear();
            remove(head);
            remove(jib);
            remove(counter);
            remove(weight);
            remove(trolley);
            remove(rope);
            remove(load);
            remove(lamp);
            head = null;
            remove(puntHand);
            remove(puntBoat);
            puntHand = null;
            puntBoat = null;
        }
    }
}
