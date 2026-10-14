package de.aetherion.core.network;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.CharacterTransport;
import de.aetherion.core.api.ExileArrivalEvent;
import de.aetherion.core.exile.BlockRig;
import de.aetherion.core.exile.CineCam;
import de.aetherion.core.exile.ExileProps;
import de.aetherion.core.exile.Say;
import de.aetherion.core.exile.Spline;
import de.aetherion.core.exile.VeilFog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The crossing. One continuous shot from inside the Veil to the Koenigskai:
 *
 * <pre>
 *  white      the screen is still the white the player left the Veil Quay in; the character settles meanwhile
 *  0-8 s      on the stern, the Ferryman at the bow, his lantern the only warm thing; he hums four notes
 *  8-15 s     the camera cranes out: the barge alone in the mist under the giant chain (the clip shot)
 *  16.5 s     the bell strikes once; the Veil tears open in front of the bow
 *  17-25 s    over the bow: the island in the evening backlight, the castle, one lit window
 *  25-41 s    down along the last chain: the cut moorings, the quay wall, the berth
 *  41-47.5 s  the barge sinks into its berth, the Ferryman fades, the lens settles into the player's eyes
 * </pre>
 *
 * The barge is the real berth barge copied into displays (the copy sinks back exactly onto its blocks), the
 * camera is one spectated display moved every tick on a C1 spline. Nothing persists; quit, kick or a crash
 * leaves the player on the quay in adventure mode.
 */
public final class ExileArrival implements Listener {

    /** Legacy tag of the old armor-stand camera (purged on boot). */
    public static final String TAG = "aetherion_exile_arrival";

    private static final int TP_TICKS = 3;

    // ------------------------------------------------------------------ authored against Runde 7 anchors
    /** Barge origin when docked: deck surface centre of the berth barge (ferry_box 156..170 / 70..77 / 11..17). */
    private static final double[] DOCK = {163.5, 72.0, 14.5};
    /** The barge itself inside the berth (stone rim excluded by material). */
    private static final int[] BARGE_BOX = {157, 70, 12, 169, 76, 16};
    /** Where the player stands when the lens hands back: on the deck at the stern, looking at the Ferryman's hut. */
    private static final double[] FEET = {167.5, 72.0, 14.5};
    private static final double[] FERRYMAN_SPOT = {160.5, 74.6, 7.5};
    /** The real chain ends at its top link here; the display chain carries it on up into the Veil. */
    private static final double[][] CHAIN_UP = {{199.5, 147.5, 0.5}, {232.0, 164.0, 0.5}, {270.0, 177.0, 0.5}, {336.0, 193.0, 0.5}};

    private static final double[] BT = {0.0, 8.0, 15.0, 18.0, 25.0, 31.0, 36.5, 41.0, 44.0, 47.5};
    private static final double[][] BP = {
            {305.0, 172.0, 15.0},
            {268.0, 160.0, 15.0},
            {232.0, 147.0, 14.8},
            {216.0, 140.0, 14.6},
            {200.0, 125.0, 14.5},
            {186.0, 104.0, 14.5},
            {174.0, 88.0, 14.5},
            {167.0, 79.5, 14.5},
            {163.5, 76.4, 14.5},
            DOCK
    };

    /** Camera keys: time, offset or absolute position, offset or absolute look target. */
    private record Key(double t, boolean camAbs, double[] cam, boolean lookAbs, double[] look) {
    }

    private static final Key[] KEYS = {
            new Key(0.0, false, new double[] {3.6, 1.7, -0.9}, false, new double[] {-14.0, 0.8, -0.4}),
            new Key(5.0, false, new double[] {3.2, 1.9, 0.9}, false, new double[] {-14.0, 1.2, 0.0}),
            new Key(8.5, false, new double[] {-2.0, 3.2, 9.5}, false, new double[] {1.5, 0.6, 0.0}),
            new Key(12.5, false, new double[] {-7.5, 3.8, 8.0}, false, new double[] {0.5, 0.8, 0.0}),
            new Key(15.5, false, new double[] {1.5, 4.2, 2.0}, false, new double[] {-30.0, -1.0, -4.0}),
            new Key(18.0, false, new double[] {-3.0, 5.0, 1.0}, true, new double[] {40.0, 150.0, -10.0}),
            new Key(24.0, false, new double[] {-8.0, 6.0, 0.0}, true, new double[] {-40.0, 172.0, 0.0}),
            new Key(30.0, false, new double[] {-6.0, 7.5, -4.0}, true, new double[] {110.0, 95.0, 0.0}),
            new Key(36.0, false, new double[] {4.0, 6.5, 7.0}, true, new double[] {150.0, 74.0, 6.0}),
            new Key(41.0, false, new double[] {6.5, 4.0, 2.5}, true, new double[] {150.0, 75.0, 12.0}),
            new Key(44.0, false, new double[] {3.2, 2.5, 1.4}, true, FERRYMAN_SPOT),
            new Key(47.5, false, new double[] {4.0, 1.62, 0.0}, true, FERRYMAN_SPOT)
    };

    private static final double TEAR_AT = 16.5;
    private static final double TITLE_AT = 18.5;
    private static final double FADE_FERRYMAN_AT = 45.2;
    private static final int RIDE_TICKS = (int) Math.round(47.5 * 20);
    private static final int PREROLL_MIN = 30;
    private static final int PREROLL_MAX = 220;

    /** Bell hymn (OriginBells): the Ferryman hums the first notes and loses the rest. */
    private static final float[] HYMN = {0.749f, 0.841f, 0.944f, 1.122f, 1.26f, 1.498f, 1.682f};

    private final AetherionCore plugin;
    private final ExileHost host;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private final Set<UUID> driving = ConcurrentHashMap.newKeySet();
    private final File storeFile;
    private final Spline barge;
    private final Spline camOff;
    private final Spline lookOff;

    ExileArrival(AetherionCore plugin, ExileHost host) {
        this.plugin = plugin;
        this.host = host;
        this.storeFile = new File(plugin.getDataFolder(), "exile-arrivals.yml");
        this.barge = new Spline(BT, BP);
        double[] kt = new double[KEYS.length];
        double[][] co = new double[KEYS.length][];
        double[][] lo = new double[KEYS.length][];
        for (int i = 0; i < KEYS.length; i++) {
            Key k = KEYS[i];
            kt[i] = k.t();
            Vector b = barge.at(k.t());
            co[i] = k.camAbs() ? minus(k.cam(), b) : k.cam();
            lo[i] = k.lookAbs() ? minus(k.look(), b) : k.look();
        }
        this.camOff = new Spline(kt, co);
        this.lookOff = new Spline(kt, lo);
        loadShown();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static double[] minus(double[] a, Vector b) {
        return new double[] {a[0] - b.getX(), a[1] - b.getY(), a[2] - b.getZ()};
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("exile.cutscene.enabled", true);
    }

    boolean hasSeen(UUID id) {
        return id != null && shown.contains(id);
    }

    /** First crossing always rides; later crossings ride (skippable) unless config turns that off. */
    boolean wantsRide(Player player, boolean byFerry) {
        if (player == null || !enabled()) {
            return false;
        }
        if (!hasSeen(player.getUniqueId())) {
            return true;
        }
        return byFerry && plugin.getConfig().getBoolean("exile.cutscene.replay-on-return", true);
    }

    /** Starts the crossing at once (the screen opens in the white the Veil Quay left it in). */
    void begin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            return;
        }
        if (!start(player)) {
            host.finishArrival(player, false, null);
        }
    }

    public void replay(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§cThe ferry is already under way.");
            return;
        }
        if (!start(player)) {
            player.sendMessage("§cExile world not loaded — no crossing.");
        }
    }

    public boolean isPlaying(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public void stop(Player player) {
        if (player == null) {
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s != null) {
            finish(player, s, false);
        }
    }

    // ------------------------------------------------------------------ session

    private final class Session {
        final UUID id;
        final GameMode prior;
        final boolean canSkip;
        CineCam cam;
        BlockRig rig;
        BlockRig chain;
        VeilFog fog;
        ExileProps.Ferryman ferryman;
        BukkitTask task;
        int preroll;
        int tick = -1;
        String line;
        int lineUntil;
        boolean torn;
        boolean titled;
        boolean faded;
        boolean ended;
        boolean quitting;

        Session(UUID id, GameMode prior, boolean canSkip) {
            this.id = id;
            this.prior = prior;
            this.canSkip = canSkip;
        }
    }

    private boolean start(Player player) {
        World world = host.exileWorld();
        if (world == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        boolean canSkip = hasSeen(id) || player.hasPermission("aetherion.core.admin");
        Session s = new Session(id, player.getGameMode(), canSkip);
        sessions.put(id, s);
        try {
            Vector b0 = barge.at(0.0);
            Location rootAt = new Location(world, b0.getX(), b0.getY(), b0.getZ());
            world.getChunkAt(rootAt).load();
            world.getChunkAt((int) Math.floor(DOCK[0]) >> 4, (int) Math.floor(DOCK[2]) >> 4).load();

            Location first = frameAt(world, 0.0);
            driving.add(id);
            s.cam = CineCam.start(plugin, player, first, TP_TICKS);
            driving.remove(id);
            s.cam.addWhite(4, 0x8C);

            s.rig = BlockRig.spawn(plugin, rootAt, TP_TICKS, List.of(player));
            Location dock = new Location(world, DOCK[0], DOCK[1], DOCK[2]);
            s.rig.copy(world, BARGE_BOX[0], BARGE_BOX[1], BARGE_BOX[2], BARGE_BOX[3], BARGE_BOX[4], BARGE_BOX[5],
                    ExileArrival::isBarge, true, dock);
            s.ferryman = ExileProps.ferryman(s.rig, new Vector3f(-3.9f, 0f, 0.75f));
            s.rig.hideStatic(player);

            Location chainAt = new Location(world, CHAIN_UP[0][0], CHAIN_UP[0][1], CHAIN_UP[0][2]);
            s.chain = BlockRig.spawn(plugin, chainAt, 0, List.of(player));
            List<Vector> pts = new ArrayList<>();
            for (double[] p : CHAIN_UP) {
                pts.add(new Vector(p[0] - CHAIN_UP[0][0], p[1] - CHAIN_UP[0][1], p[2] - CHAIN_UP[0][2]));
            }
            ExileProps.chain(s.chain, pts, 6.0, 3.0, Material.IRON_BLOCK);

            s.fog = VeilFog.corridor(plugin, world, List.of(player), false, 302, 222, 7.0, 16.0,
                            x -> fogLine(x) + 3.0, 74, 52, 0x2C, 0x4A, id.getLeastSignificantBits())
                    .with(VeilFog.corridor(plugin, world, List.of(player), false, 219, 207, 3.5, 14.0,
                            x -> fogLine(x) + 4.0, 90, 64, 0x60, 0x88, id.getMostSignificantBits()));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Exile crossing could not start for " + player.getName() + ": " + ex.getMessage());
            cleanup(s);
            sessions.remove(id);
            driving.remove(id);
            return false;
        }
        s.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, s), 1L, 1L);
        return true;
    }

    /** Barge line height at x (for hanging fog where the ferry passes). */
    private double fogLine(double x) {
        double best = BP[0][1];
        for (int i = 0; i + 1 < BP.length; i++) {
            double xa = BP[i][0];
            double xb = BP[i + 1][0];
            if (x <= xa && x >= xb) {
                double u = (xa - x) / Math.max(1e-6, xa - xb);
                return BP[i][1] + (BP[i + 1][1] - BP[i][1]) * u;
            }
            if (x > xa && i == 0) {
                return BP[0][1];
            }
        }
        return best;
    }

    private static boolean isBarge(BlockData data) {
        Material m = data.getMaterial();
        return m != Material.STONE_BRICKS && m != Material.POLISHED_ANDESITE && m != Material.STONE_BRICK_SLAB;
    }

    private Location frameAt(World world, double t) {
        Vector b = barge.at(t);
        Vector c = b.clone().add(camOff.at(t));
        Vector l = b.clone().add(lookOff.at(t));
        Location at = new Location(world, c.getX(), c.getY(), c.getZ());
        Vector dir = l.subtract(c);
        if (dir.lengthSquared() < 1.0e-6) {
            dir = new Vector(-1, 0, 0);
        }
        at.setDirection(dir);
        return at;
    }

    private void tick(Player player, Session s) {
        if (s.ended) {
            return;
        }
        if (!player.isOnline() || s.cam == null || !s.cam.valid()) {
            finish(player, s, false);
            return;
        }
        World world = s.cam.entity().getWorld();
        driving.add(s.id);
        try {
            s.cam.hold();
        } finally {
            driving.remove(s.id);
        }

        if (s.tick < 0) {
            // The white: hold still inside the Veil until the character has landed on this backend.
            s.preroll++;
            Location f = frameAt(world, 0.0);
            s.cam.frame(f);
            if (s.preroll % 4 == 0) {
                VeilFog.breathe(player, f, 5.0, 6, 3.6f);
            }
            if (s.preroll == 2) {
                sound(player, f, Sound.ITEM_ELYTRA_FLYING, 0.16f, 0.5f);
            }
            if (s.preroll >= PREROLL_MAX || (s.preroll >= PREROLL_MIN && settled(player))) {
                s.tick = 0;
                s.cam.clearWhite(36);
            }
            return;
        }

        int k = s.tick;
        double t = k / 20.0;
        Vector b = barge.at(t);
        s.rig.moveTo(new Location(world, b.getX(), b.getY(), b.getZ()));
        Location f = frameAt(world, t);
        s.cam.frame(f);
        cues(player, s, k, t, f);

        if (k % 25 == 0 && t < 44.0) {
            boolean left = (k / 25) % 2 == 0;
            s.rig.sway(left ? 1.1f : -1.1f, left ? 0.35f : -0.35f, 25);
        }
        if (k % 40 == 0 && s.ferryman != null && t < FADE_FERRYMAN_AT) {
            boolean push = (k / 40) % 2 == 0;
            Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(push ? 30 : 40));
            s.rig.pose(s.ferryman.pole, s.ferryman.poleRest, q, 38);
            Vector3f tip = q.transform(new Vector3f(0f, 3.4f, 0f));
            Vector3f lantern = new Vector3f(s.ferryman.poleRest).add(tip).add(-0.25f, -0.95f, -0.25f);
            s.rig.pose(s.ferryman.lantern, lantern, new Quaternionf(), 38);
        }
        if (k % 40 == 0) {
            s.rig.hideStatic(player);
        }
        if (s.line != null) {
            if (k <= s.lineUntil) {
                if (k % 20 == 0) {
                    player.sendActionBar(Component.text(s.line));
                }
            } else {
                s.line = null;
            }
        }
        if (s.canSkip && k % 60 == 30 && t < 40.0 && s.line == null) {
            player.sendActionBar(Component.text(Say.t(player, "§8Sneak to step off early", "§8Schleichen zum Aussteigen"), NamedTextColor.DARK_GRAY));
        }
        if (k >= RIDE_TICKS) {
            finish(player, s, true);
            return;
        }
        s.tick++;
    }

    private boolean settled(Player player) {
        NetworkRouter router = plugin.router();
        CharacterTransport transport = router == null ? null : router.transport();
        return transport == null || !transport.isArriving(player.getUniqueId());
    }

    private void line(Player player, Session s, String en, String de, double seconds) {
        s.line = (Say.german(player) ? "§8Fährmann §7· " : "§8Ferryman §7· ") + Say.t(player, en, de);
        s.lineUntil = s.tick + (int) (seconds * 20);
        player.sendActionBar(Component.text(s.line));
    }

    private void cues(Player player, Session s, int k, double t, Location f) {
        // weather of the Veil
        if (t < TEAR_AT + 0.5) {
            if (k % 3 == 0) {
                Location ahead = f.clone().add(f.getDirection().multiply(6));
                VeilFog.breathe(player, ahead, 7.0, 7, 4.0f);
            }
            if (k % 80 == 0) {
                sound(player, f, Sound.ITEM_ELYTRA_FLYING, 0.14f, 0.5f);
            }
        } else if (t < 30 && k % 6 == 0) {
            VeilFog.breathe(player, f.clone().add(f.getDirection().multiply(10)), 9.0, 2, 3.0f);
        }
        if (k % 31 == 7 && t < 46) {
            sound(player, f, Sound.BLOCK_CHAIN_STEP, 0.32f, 0.55f + (float) (Math.random() * 0.1));
        }
        if (k % 53 == 11 && t < 44) {
            sound(player, f, Sound.BLOCK_LADDER_STEP, 0.22f, 0.6f);
        }
        if (k % 37 == 3 && t < 45 && s.ferryman != null) {
            sound(player, f, Sound.BLOCK_LANTERN_STEP, 0.12f, 1.4f);
        }

        // the Ferryman hums the lullaby and loses it after four notes, then tries again and loses it sooner
        int[] hum = {48, 66, 84, 102, 150, 168, 186};
        int[] note = {0, 1, 2, 3, 0, 1, 2};
        for (int i = 0; i < hum.length; i++) {
            if (k == hum[i]) {
                sound(player, f, Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.45f, HYMN[note[i]]);
            }
        }

        switch (k) {
            case 20 -> line(player, s, "Hold the rail. The Veil doesn't like being crossed.",
                    "Halt dich an der Reling fest. Der Schleier mag es nicht, wenn man ihn quert.", 4.0);
            case 210 -> line(player, s, "First passenger in... a long while. I forget how long.",
                    "Der erste Fahrgast seit… einer langen Weile. Ich weiß nicht mehr, wie lange.", 4.0);
            case 290 -> line(player, s, "Listen. There's a bell over there.",
                    "Hör hin. Da drüben ist eine Glocke.", 2.5);
            default -> {
            }
        }

        if (!s.torn && t >= TEAR_AT) {
            s.torn = true;
            Location bell = f.clone().add(f.getDirection().multiply(12));
            sound(player, bell, Sound.BLOCK_BELL_USE, 1.0f, 0.5f);
            sound(player, bell, Sound.BLOCK_BELL_RESONATE, 0.9f, 0.6f);
            sound(player, f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 0.5f);
            s.fog.tear(f.getX() - 1.0, 26);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !s.ended) {
                    sound(player, player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.5f, 0.5f);
                }
            }, 30L);
        }
        if (!s.titled && t >= TITLE_AT) {
            s.titled = true;
            boolean de = Say.german(player);
            player.showTitle(Title.title(
                    Component.text(de ? "DAS EXIL" : "THE EXILE", NamedTextColor.WHITE, TextDecoration.BOLD),
                    Component.text(de ? "der alte Hof von Eldervale" : "the old court of Eldervale", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(1200), Duration.ofMillis(3600), Duration.ofMillis(1600))));
        }
        switch (k) {
            case 500 -> line(player, s, "Still a light up there. Somebody's still up.",
                    "Da oben brennt noch Licht. Irgendwer ist noch wach.", 4.0);
            case 640 -> line(player, s, "Sixteen moorings. One rope left. Ours.",
                    "Sechzehn Liegeplätze. Ein Tau ist übrig. Unseres.", 4.0);
            case 820 -> {
                sound(player, f, Sound.BLOCK_CHAIN_PLACE, 0.6f, 0.6f);
                line(player, s, "Mind the step. Some of the planks are only habit.",
                        "Pass auf die Stufe auf. Manche Planken sind nur noch Gewohnheit.", 4.0);
            }
            case 930 -> {
                sound(player, f, Sound.BLOCK_WOOD_PLACE, 0.8f, 0.55f);
                sound(player, f, Sound.BLOCK_CHAIN_FALL, 0.5f, 0.6f);
            }
            default -> {
            }
        }
        if (!s.faded && t >= FADE_FERRYMAN_AT && s.ferryman != null) {
            s.faded = true;
            List<org.bukkit.entity.Display> him = new ArrayList<>(s.ferryman.body);
            him.add(s.ferryman.pole);
            him.add(s.ferryman.lantern);
            s.rig.fade(him, 18);
            Location at = s.rig.location().clone().add(-3.9, 1.0, 0.75);
            player.spawnParticle(Particle.WHITE_ASH, at, 40, 0.3, 0.8, 0.3, 0.01);
            player.spawnParticle(Particle.END_ROD, at, 6, 0.2, 0.6, 0.2, 0.01);
            sound(player, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.6f);
        }
    }

    private static void sound(Player player, Location at, Sound sound, float volume, float pitch) {
        player.playSound(at, sound, SoundCategory.AMBIENT, volume, pitch);
    }

    // ------------------------------------------------------------------ ending

    private void finish(Player player, Session s, boolean completed) {
        if (s.ended) {
            return;
        }
        s.ended = true;
        sessions.remove(s.id);
        if (s.task != null) {
            s.task.cancel();
        }
        boolean online = player != null && player.isOnline();
        World world = host.exileWorld();
        Location feet = world == null ? host.spawnLocation()
                : new Location(world, FEET[0], FEET[1], FEET[2]);
        if (feet != null && world != null) {
            Location eye = feet.clone().add(0, 1.62, 0);
            eye.setDirection(new Vector(FERRYMAN_SPOT[0], FERRYMAN_SPOT[1], FERRYMAN_SPOT[2]).subtract(eye.toVector()));
            feet.setYaw(eye.getYaw());
            feet.setPitch(eye.getPitch());
        }
        if (online && s.rig != null) {
            s.rig.restoreStatic(player);
        }
        if (online) {
            driving.add(s.id);
            GameMode mode = restoreMode(s.prior);
            if (s.cam != null) {
                s.cam.end(feet, mode);
            } else {
                player.setGameMode(mode);
                if (feet != null) {
                    player.teleport(feet);
                }
            }
            driving.remove(s.id);
        } else if (s.cam != null) {
            s.cam.remove();
        }
        // one tick later the copy goes; the real barge is already back under it
        BlockRig rig = s.rig;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (rig != null) {
                rig.remove();
            }
        }, 2L);
        if (s.chain != null) {
            s.chain.remove();
        }
        if (s.fog != null) {
            s.fog.remove();
        }
        boolean reached = completed || s.tick >= (int) (TEAR_AT * 20);
        boolean first = !hasSeen(s.id);
        if (reached) {
            markShown(s.id);
        }
        if (online) {
            host.finishArrival(player, true, feet);
            if (!s.quitting && (completed || s.tick >= 0)) {
                player.sendMessage(Say.t(player,
                        "§8Exile §8» §7The Königskai. The chain ends here. §8(§f/exile return§8 takes the ferry back)",
                        "§8Exil §8» §7Der Königskai. Hier endet die Kette. §8(§f/exile return§8 nimmt die Fähre zurück)"));
                Bukkit.getPluginManager().callEvent(new ExileArrivalEvent(player, first && reached));
            }
        }
    }

    private static GameMode restoreMode(GameMode prior) {
        if (prior == GameMode.CREATIVE) {
            return GameMode.CREATIVE;
        }
        return GameMode.ADVENTURE;
    }

    private void cleanup(Session s) {
        if (s.cam != null) {
            s.cam.remove();
        }
        if (s.rig != null) {
            s.rig.remove();
        }
        if (s.chain != null) {
            s.chain.remove();
        }
        if (s.fog != null) {
            s.fog.remove();
        }
    }

    /** Boot hygiene: old armor-stand cameras, display rigs and Veil sheets from a crash. */
    void purgeRigs(World world) {
        if (world == null) {
            return;
        }
        for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
            if (stand.getScoreboardTags().contains(TAG)) {
                stand.remove();
            }
        }
        BlockRig.purge(plugin, world);
        VeilFog.purge(plugin, world);
    }

    public void shutdown() {
        for (Session s : List.copyOf(sessions.values())) {
            Player p = Bukkit.getPlayer(s.id);
            finish(p, s, false);
        }
    }

    // ------------------------------------------------------------------ shown store

    private void markShown(UUID id) {
        if (id == null || !shown.add(id)) {
            return;
        }
        saveShown();
    }

    private void loadShown() {
        shown.clear();
        if (!storeFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storeFile);
        for (String raw : yaml.getStringList("shown")) {
            try {
                shown.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveShown() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID id : shown) {
            list.add(id.toString());
        }
        yaml.set("shown", list);
        try {
            yaml.save(storeFile);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save exile-arrivals.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ guards

    /** Sneak while spectating = "let me off": skip when allowed, otherwise stay on the lens. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null || s.ended) {
            return;
        }
        event.setCancelled(true);
        if (s.canSkip && !driving.contains(s.id)) {
            Bukkit.getScheduler().runTask(plugin, () -> finish(event.getPlayer(), s, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null || s.ended || driving.contains(s.id)) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        // Something else wants this player elsewhere (a command, a transfer): end the ride cleanly first.
        if (event.getTo() != null && event.getTo().getWorld() != null
                && !event.getTo().getWorld().equals(event.getFrom().getWorld())) {
            finish(event.getPlayer(), s, false);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Quit / kick mid-ride: the saved player is an adventurer on the quay, never a spectator in the sky. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null) {
            s.quitting = true;
            finish(event.getPlayer(), s, false);
        }
    }
}
