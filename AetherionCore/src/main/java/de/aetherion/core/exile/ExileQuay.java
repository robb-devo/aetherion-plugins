package de.aetherion.core.exile;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CharacterTransport;
import de.aetherion.core.api.ExilePlaces;
import de.aetherion.core.api.StoryAccess;
import de.aetherion.core.network.ExileLink;
import de.aetherion.core.network.NetworkRouter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Veil Quay: the islet Robb pastes behind the Borderlands ({@code schematics/veil_quay.schem}).
 *
 * <p>Everything here is relative to the paste origin (the block Robb stood in, {@code /exile setquay}).
 * Boarding = ring the ferry bell, sneak on the deck, or ask the Ferryman. The departure is the first shot of
 * the crossing: the lens leaves the player's eyes, the very barge they stood on lifts out of its berth and
 * glides into the Veil until the screen is white; the hop to mmo-c happens inside that white and the
 * crossing on the exile side opens in it.
 *
 * <p>The barge hangs on a cable: from a gantry over the stern end of the berth it runs out over the berth and
 * sinks into the Veil, two trolleys ride it and four slings carry the barge; on the way out the passenger's own
 * barge, trolleys and slings follow the cable. Fog comes up as soon as a passenger who may cross steps aboard.
 * Asked in person ("Cross the Veil"), the Ferryman walks the passenger down the gangway himself, takes the bow, and
 * rides along into the fog ({@link ExileFerryEscort}).
 *
 * <p>The anchors are authored with the ferry pointing west. {@code exile.quay.facing: east} means the islet was
 * pasted turned 180° around the origin block (the ferry points east, away from the main island); every anchor
 * goes through {@link #at}, {@link #block}, {@link #wx} / {@link #wz} so both layouts work.
 */
public final class ExileQuay implements Listener {

    // ---- relative anchors (local schem frame: x east, z south; the ferry points west) -------------
    public static final double[] LANDING = {0.5, 0.0, 0.5, 90.0};
    public static final double[] GANGWAY = {-39.5, 0.0, 0.5, 90.0};
    public static final double[] FERRYMAN = {-38.5, 0.0, -3.5, -90.0};
    public static final int[] BELL = {-39, 2, 4};
    /** Feet block of someone standing on the barge deck. */
    public static final int[] DECK = {-54, -1, -2, -42, 0, 2};
    /** The barge blocks (copied from the Exile berth, block for block). */
    public static final int[] BARGE = {-54, -3, -2, -42, 3, 2};
    /** Deck-surface centre of the docked barge: the rig origin. */
    public static final double[] ROOT = {-47.5, -1.0, 0.5};
    public static final double[] VEIL_LAMP = {-59.5, 18.5, 9.5};
    /** The Ferryman's walk from his spot to the bow (feet): off his spot, the gangway head, the stern, the bow post. */
    private static final double[][] WALK = {{-40.0, 0.0, -1.6}, {-41.4, 0.0, 0.5}, {-43.4, -1.0, 0.5}, {-51.6, -1.0, -0.6}};
    /** His place at the bow while the barge goes, relative to the rig origin (authored: the bow is west). */
    private static final Vector BOW_POST = new Vector(-4.1, 0.0, -1.1);
    private static final double WALK_SPEED = 0.14;
    /**
     * The ferry cable (authored, centre line z 0.5): off the gantry pulley over the stern end of the berth, out
     * over the berth and down into the Veil past where the barge whites out.
     */
    private static final double[][] CABLE = {
            {-40.5, 9.4}, {-62.0, 7.2}, {-77.0, 2.6}, {-97.0, -5.0}, {-118.0, -12.0}, {-142.0, -18.5}
    };
    /** Trolleys ride the cable this far fore and aft of the barge's middle; slings go down to both gunwales. */
    private static final double SLING_SPREAD = 4.6;
    private static final double SLING_SIDE = 2.3;
    private static final double SLING_FOOT = 0.8;
    static final String CABLE_TAG = "aetherion_quay_cable";
    public static final double[] POCKET = {-8.5, 0.0, 10.5};

    private static final double[] DT = {0.0, 1.5, 3.0, 5.0, 7.0, 9.5, 12.0};
    private static final double[][] DP = {
            {-47.5, -1.0, 0.5},
            {-47.5, 1.8, 0.5},
            {-50.5, 2.7, 0.5},
            {-62.0, 2.2, 0.5},
            {-77.0, -3.5, 0.5},
            {-97.0, -12.0, 0.5},
            {-118.0, -20.0, 0.5}
    };
    private static final int SEND_AT = 170;
    private static final int GIVE_UP_AT = SEND_AT + 260;

    private final AetherionCore plugin;
    private final ExileLink link;
    private final Spline path = new Spline(DT, DP);
    private final Map<UUID, Departure> departing = new ConcurrentHashMap<>();
    private final Set<UUID> onDeck = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastRefusal = new HashMap<>();
    private final Set<UUID> driving = ConcurrentHashMap.newKeySet();
    private World world;
    private int[] origin;
    /** True when the islet was pasted turned 180° ({@code exile.quay.facing: east}). */
    private boolean turned;
    private VeilFog curtain;
    private BukkitTask ambience;
    /**
     * Persistent: gantry, cable, the docked barge's trolleys and slings (purged and rebuilt each placement). By UUID:
     * a chunk that unloads and loads again hands out new entity objects.
     */
    private final Set<UUID> rigging = ConcurrentHashMap.newKeySet();
    /** The docked trolleys and slings only (hidden for a passenger whose own set goes with them). */
    private final List<UUID> slings = new ArrayList<>();
    /** Fog that came up when a passenger who may cross stepped aboard. */
    private final Map<UUID, VeilFog> deckFog = new ConcurrentHashMap<>();
    private final Map<UUID, Walk> walks = new ConcurrentHashMap<>();

    public ExileQuay(AetherionCore plugin, ExileLink link) {
        this.plugin = plugin;
        this.link = link;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::load, 60L);
    }

    // ------------------------------------------------------------------ placement

    private void load() {
        String worldName = plugin.getConfig().getString("exile.quay.world", "");
        String raw = plugin.getConfig().getString("exile.quay.origin", "");
        if (worldName == null || worldName.isBlank() || raw == null || raw.isBlank()) {
            plugin.getLogger().info("Veil Quay not placed (exile.quay.world / origin empty). /exile setquay after the paste.");
            ExilePlaces.setQuayBoard(null);
            ExilePlaces.setQuayFerryman(null);
            return;
        }
        World w = Bukkit.getWorld(worldName);
        int[] o = parse(raw);
        turned = turnedIn(plugin);
        if (w == null || o == null) {
            plugin.getLogger().warning("Veil Quay: world '" + worldName + "' not loaded or origin '" + raw + "' unreadable.");
            return;
        }
        place(w, o);
    }

    /** {@code exile.quay.facing: east} = the islet sits turned 180° (ferry sails east, away from the main island). */
    public static boolean turnedIn(AetherionCore plugin) {
        return "east".equalsIgnoreCase(plugin.getConfig().getString("exile.quay.facing", "west").trim());
    }

    private static int[] parse(String raw) {
        String[] p = raw.trim().replace(",", " ").split("\\s+");
        if (p.length < 3) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Wire anchors after an automated paste (config already written). */
    public void setOrigin(World w, int[] o) {
        if (w == null || o == null || o.length < 3) {
            return;
        }
        turned = turnedIn(plugin);
        place(w, o);
    }

    /** {@code /exile setquay}: the admin stands on the block where they pasted the islet. */
    public void setHere(Player admin) {
        Location at = admin.getLocation();
        int[] o = {at.getBlockX(), at.getBlockY(), at.getBlockZ()};
        plugin.getConfig().set("exile.quay.world", at.getWorld().getName());
        plugin.getConfig().set("exile.quay.origin", o[0] + " " + o[1] + " " + o[2]);
        plugin.saveConfig();
        place(at.getWorld(), o);
        Block bell = block(BELL);
        boolean bellOk = bell != null && bell.getType() == Material.BELL;
        admin.sendMessage("§8Veil Quay §8» §aPlaced at §f" + at.getWorld().getName() + " " + o[0] + " " + o[1] + " " + o[2]
                + (bellOk ? " §7(bell found — paste lines up)" : " §c(no ferry bell at the expected spot — stand on the paste origin, no //rotate)"));
    }

    private void place(World w, int[] o) {
        this.world = w;
        this.origin = o;
        ExilePlaces.setQuayBoard(at(GANGWAY));
        ExilePlaces.setQuayFerryman(at(FERRYMAN));
        // chunks of the islet, the curtain and the cable, then the persistent Veil curtain and ferry rigging
        // (purged and rebuilt each boot)
        int cxA = bx(-150) >> 4;
        int cxB = bx(12) >> 4;
        for (int cx = Math.min(cxA, cxB); cx <= Math.max(cxA, cxB); cx++) {
            for (int cz = (o[2] - 40) >> 4; cz <= (o[2] + 40) >> 4; cz++) {
                w.getChunkAt(cx, cz).load();
            }
        }
        if (curtain != null) {
            curtain.remove();
        }
        // what this run hung at an earlier origin (a turn moves the islet), then whatever is left near this one
        for (UUID id : rigging) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        purgeCurtainNear();
        curtain = VeilFog.corridor(plugin, w, null, true, Math.max(wx(-74), wx(-104)), Math.min(wx(-74), wx(-104)),
                6.0, wz(0.5), o[1] - 7.0, 78, 50, 0x26, 0x4C, 0x5E11L);
        try {
            rig();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Veil Quay: ferry cable not hung (" + ex + ")");
        }
        if (ambience != null) {
            ambience.cancel();
        }
        ambience = Bukkit.getScheduler().runTaskTimer(plugin, this::ambience, 40L, 10L);
        plugin.getLogger().info("Veil Quay placed at " + w.getName() + " " + o[0] + " " + o[1] + " " + o[2]);
    }

    private void purgeCurtainNear() {
        Location c = at(new double[] {-88, -6, 0.5});
        if (c == null) {
            return;
        }
        for (var e : c.getWorld().getNearbyEntities(c, 40, 40, 50)) {
            if (e.getScoreboardTags().contains(VeilFog.TAG)) {
                e.remove();
            }
        }
        // the cable reaches from the gantry (x -40) to x -142; an old placement may have hung it elsewhere
        Location g = at(new double[] {-90, 0, 0.5});
        for (var e : g.getWorld().getNearbyEntities(g, 140, 60, 60)) {
            if (e.getScoreboardTags().contains(CABLE_TAG)) {
                e.remove();
            }
        }
        rigging.clear();
        slings.clear();
    }

    // ------------------------------------------------------------------ the ferry cable

    /** Cable height (authored y) above authored x: straight runs between the {@link #CABLE} points. */
    private static double cableY(double x) {
        if (x >= CABLE[0][0]) {
            return CABLE[0][1];
        }
        for (int i = 1; i < CABLE.length; i++) {
            if (x >= CABLE[i][0]) {
                double t = (x - CABLE[i - 1][0]) / (CABLE[i][0] - CABLE[i - 1][0]);
                return CABLE[i - 1][1] + (CABLE[i][1] - CABLE[i - 1][1]) * t;
            }
        }
        return CABLE[CABLE.length - 1][1];
    }

    private Location rel(double x, double y, double z) {
        return new Location(world, wx(x), origin[1] + y, wz(z));
    }

    /** Gantry, cable and the docked barge's trolleys and slings, for everyone (persistent, tagged). */
    private void rig() {
        List<Entity> made = new ArrayList<>();
        BlockData post = Material.STRIPPED_DARK_OAK_LOG.createBlockData();
        BlockData iron = Material.POLISHED_DEEPSLATE.createBlockData();
        BlockData rope = Material.BLACK_CONCRETE.createBlockData();
        for (double z : new double[] {-3.2, 4.2}) {
            made.add(bar(rel(-40.5, -0.2, z), rel(-40.5, 10.1, z), post, 0.5f, null, true));
        }
        made.add(bar(rel(-40.5, 10.1, -3.5), rel(-40.5, 10.1, 4.5), post, 0.5f, null, true));
        for (double z : new double[] {-3.2, 4.2}) {
            double inward = z < 0 ? 1.4 : -1.4;
            made.add(bar(rel(-40.5, 7.6, z), rel(-40.5, 10.0, z + inward), Material.DARK_OAK_PLANKS.createBlockData(), 0.2f, null, true));
        }
        made.add(bar(rel(-40.5, 9.05, 0.5), rel(-40.5, 9.75, 0.5), iron, 0.7f, null, true));
        BlockDisplay lamp = world.spawn(rel(-40.5, 9.2, -2.3), BlockDisplay.class, d -> {
            dressRigging(d, true, null);
            d.setBlock(Material.SOUL_LANTERN.createBlockData("[hanging=true]"));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(-0.25f, -0.25f, -0.25f), new Quaternionf(), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
        });
        made.add(lamp);
        for (int i = 0; i + 1 < CABLE.length; i++) {
            made.add(bar(rel(CABLE[i][0], CABLE[i][1], 0.5), rel(CABLE[i + 1][0], CABLE[i + 1][1], 0.5), rope, 0.13f, null, true));
        }
        for (BlockDisplay d : slingSet(ROOT[0], ROOT[1], null, true)) {
            made.add(d);
            slings.add(d.getUniqueId());
        }
        for (Entity e : made) {
            rigging.add(e.getUniqueId());
        }
    }

    /** Two trolleys on the cable over a barge whose deck middle is at authored {@code (x, y)}, four slings down. */
    private List<BlockDisplay> slingSet(double x, double y, Player viewer, boolean persistent) {
        List<BlockDisplay> out = new ArrayList<>();
        BlockData trolley = Material.IRON_BLOCK.createBlockData();
        BlockData chain = Material.CHAIN.createBlockData("[axis=y]");
        for (double dx : new double[] {SLING_SPREAD, -SLING_SPREAD}) {
            double tx = x + dx;
            double ty = cableY(tx);
            out.add(bar(rel(tx, ty - 0.3, 0.5), rel(tx, ty + 0.25, 0.5), trolley, 0.5f, viewer, persistent));
            for (double side : new double[] {-SLING_SIDE, SLING_SIDE}) {
                out.add(bar(rel(tx, ty - 0.3, 0.5), rel(tx, y + SLING_FOOT, 0.5 + side), chain, 1.0f, viewer, persistent));
            }
        }
        return out;
    }

    /** Moves a set from {@link #slingSet} to a barge whose deck middle is at authored {@code (x, y)}. */
    private void moveSlings(List<BlockDisplay> set, double x, double y, int ticks) {
        int i = 0;
        for (double dx : new double[] {SLING_SPREAD, -SLING_SPREAD}) {
            double tx = x + dx;
            double ty = cableY(tx);
            if (i + 2 >= set.size()) {
                return;
            }
            place(set.get(i++), rel(tx, ty - 0.3, 0.5), rel(tx, ty + 0.25, 0.5), 0.5f, ticks);
            for (double side : new double[] {-SLING_SIDE, SLING_SIDE}) {
                place(set.get(i++), rel(tx, ty - 0.3, 0.5), rel(tx, y + SLING_FOOT, 0.5 + side), 1.0f, ticks);
            }
        }
    }

    private void dressRigging(Display d, boolean persistent, Player viewer) {
        d.setPersistent(persistent);
        d.addScoreboardTag(CABLE_TAG);
        d.setViewRange(3.0f);
        d.setTeleportDuration(3);
        if (viewer != null) {
            d.setVisibleByDefault(false);
        }
    }

    /** A box of {@code w} by {@code w} from {@code a} to {@code b}, its axis on the line (chains: {@code w} = 1). */
    private BlockDisplay bar(Location a, Location b, BlockData data, float w, Player viewer, boolean persistent) {
        BlockDisplay d = world.spawn(a, BlockDisplay.class, bd -> {
            dressRigging(bd, persistent, viewer);
            bd.setBlock(data);
            bd.setTransformation(barShape(a, b, w));
        });
        if (viewer != null) {
            viewer.showEntity(plugin, d);
        }
        return d;
    }

    private static void place(BlockDisplay d, Location a, Location b, float w, int ticks) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.teleport(a);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(barShape(a, b, w));
    }

    private static Transformation barShape(Location a, Location b, float w) {
        Vector3f dir = new Vector3f((float) (b.getX() - a.getX()), (float) (b.getY() - a.getY()), (float) (b.getZ() - a.getZ()));
        float len = Math.max(0.001f, dir.length());
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), dir.normalize());
        Vector3f t = q.transform(new Vector3f(-w / 2f, 0f, -w / 2f));
        return new Transformation(t, q, new Vector3f(w, len, w), new Quaternionf());
    }

    /**
     * A chunk coming back with Veil sheets or rigging this run did not hang (saved by an earlier placement, before
     * the islet was turned or re-pasted): they go.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!placed() || !event.getWorld().equals(world)) {
            return;
        }
        for (Entity e : event.getEntities()) {
            if (e.getScoreboardTags().contains(CABLE_TAG) && e.isPersistent() && !rigging.contains(e.getUniqueId())) {
                e.remove();
            } else if (e.getScoreboardTags().contains(VeilFog.TAG) && e.isPersistent() && (curtain == null || !curtain.owns(e))) {
                e.remove();
            }
        }
    }

    /** True when the ferry bell sits where the anchors say (the islet lines up with the recorded origin). */
    public boolean bellInPlace() {
        Block bell = block(BELL);
        return bell != null && bell.getType() == Material.BELL;
    }

    public boolean placed() {
        return world != null && origin != null;
    }

    public World world() {
        return world;
    }

    /** Paste origin (east landing, feet), or null when not placed. */
    public int[] origin() {
        return origin == null ? null : origin.clone();
    }

    /** True when the islet sits turned 180° (the ferry sails east). */
    public boolean turned() {
        return turned;
    }

    /*
     * Authored frame → world. Turned 180° around the origin block, a block offset d lands at -d, and a point inside a
     * block mirrors with it: x → origin + 1 - x (same for z).
     */
    private double wx(double rel) {
        return turned ? origin[0] + 1.0 - rel : origin[0] + rel;
    }

    private double wz(double rel) {
        return turned ? origin[2] + 1.0 - rel : origin[2] + rel;
    }

    private int bx(int rel) {
        return turned ? origin[0] - rel : origin[0] + rel;
    }

    private int bz(int rel) {
        return turned ? origin[2] - rel : origin[2] + rel;
    }

    /** An authored direction (no position) in world terms. */
    private Vector dir(Vector rel) {
        return turned ? new Vector(-rel.getX(), rel.getY(), -rel.getZ()) : rel.clone();
    }

    private Location at(double[] rel) {
        if (!placed()) {
            return null;
        }
        Location l = new Location(world, wx(rel[0]), origin[1] + rel[1], wz(rel[2]));
        if (rel.length > 3) {
            l.setYaw((float) (rel[3] + (turned ? 180.0 : 0.0)));
        }
        return l;
    }

    private Block block(int[] rel) {
        if (!placed()) {
            return null;
        }
        return world.getBlockAt(bx(rel[0]), origin[1] + rel[1], bz(rel[2]));
    }

    /** Within {@code r} blocks of the quay (same world). */
    public boolean near(Player p, double r) {
        Location g = at(GANGWAY);
        return g != null && p.getWorld().equals(g.getWorld()) && p.getLocation().distanceSquared(g) <= r * r;
    }

    private boolean onDeck(Location l) {
        if (!placed() || !l.getWorld().equals(world)) {
            return false;
        }
        int x = turned ? origin[0] - l.getBlockX() : l.getBlockX() - origin[0];
        int y = l.getBlockY() - origin[1];
        int z = turned ? origin[2] - l.getBlockZ() : l.getBlockZ() - origin[2];
        return x >= DECK[0] && x <= DECK[3] && y >= DECK[1] && y <= DECK[4] && z >= DECK[2] && z <= DECK[5];
    }

    // ------------------------------------------------------------------ ambience (everyone)

    private void ambience() {
        if (!placed()) {
            return;
        }
        Location g = at(GANGWAY);
        boolean anyone = false;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(g) < 110 * 110) {
                anyone = true;
                break;
            }
        }
        if (!anyone) {
            return;
        }
        Location deck = at(new double[] {-48, 0.5, 0.5});
        for (UUID id : deckFog.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !departing.containsKey(id)) {
                VeilFog.breathe(p, deck, 7.0, 7, 3.5f);
            }
        }
        Location veil = at(new double[] {-76, -2, 0.5});
        Particle.DustOptions mist = new Particle.DustOptions(Color.fromRGB(226, 228, 238), 4.0f);
        for (int i = 0; i < 6; i++) {
            de.aetherion.core.api.Vfx.puff(veil.clone().add((Math.random() - 0.5) * 10, (Math.random() - 0.3) * 16,
                    (Math.random() - 0.5) * 50), Particle.DUST, 1, Math.max(0, 0), mist);
        }
        Location lamp = at(VEIL_LAMP);
        de.aetherion.core.api.Vfx.puff(lamp, Particle.SOUL_FIRE_FLAME, 2, Math.max(0.25, 0.25), null);
        de.aetherion.core.api.Vfx.puff(at(new double[] {-60, 4, 0.5}), Particle.WHITE_ASH, 6, Math.max(6, 5), null);
    }

    // ------------------------------------------------------------------ boarding

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBell(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || !placed()) {
            return;
        }
        Block b = event.getClickedBlock();
        Block bell = block(BELL);
        if (b == null || bell == null || !b.equals(bell)) {
            return;
        }
        // let the bell ring for everyone; the ferry answers
        Bukkit.getScheduler().runTaskLater(plugin, () -> board(event.getPlayer(), "bell"), 6L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() || !placed()) {
            return;
        }
        Player p = event.getPlayer();
        if (departing.containsKey(p.getUniqueId())) {
            return;
        }
        if (onDeck(p.getLocation())) {
            board(p, "deck");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!placed() || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player p = event.getPlayer();
        boolean now = onDeck(event.getTo());
        if (now && onDeck.add(p.getUniqueId())) {
            String block = link.isExileServer() ? "" : refusal(p);
            if (block == null) {
                if (!walks.containsKey(p.getUniqueId())) {
                    p.sendActionBar(Component.text(Say.t(p, "Aboard the King's Ferry · ring the bell or sneak to cast off",
                            "An Bord der Königsfähre · Glocke läuten oder schleichen zum Ablegen"), NamedTextColor.GRAY));
                }
                p.playSound(p.getLocation(), Sound.BLOCK_WOOD_STEP, SoundCategory.BLOCKS, 0.6f, 0.6f);
                if (!departing.containsKey(p.getUniqueId())) {
                    fogUp(p);
                }
            } else if (!block.isEmpty()) {
                p.sendActionBar(Component.text(block, NamedTextColor.GRAY));
            }
        } else if (!now && onDeck.remove(p.getUniqueId()) && !departing.containsKey(p.getUniqueId())) {
            VeilFog fog = deckFog.remove(p.getUniqueId());
            if (fog != null) {
                Bukkit.getScheduler().runTaskLater(plugin, fog::remove, 30L);
            }
        }
    }

    /** Null when this player may cross; else the line the Ferryman answers with. */
    public String refusal(Player p) {
        if (p.hasPermission("aetherion.core.admin")) {
            return null;
        }
        StoryAccess story = AetherServices.story();
        if (story == null) {
            return null;
        }
        try {
            return story.ferryBlock(p);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Start the crossing from the quay ({@code via}: bell / deck / talk / command).
     *
     * @return true when the departure began
     */
    public boolean board(Player p, String via) {
        if (p == null || !p.isOnline() || !placed()) {
            return false;
        }
        UUID id = p.getUniqueId();
        if (departing.containsKey(id)) {
            return true;
        }
        if (link.isExileServer()) {
            return false;
        }
        if (!near(p, 48)) {
            p.sendMessage(Say.t(p, "§8Ferry §8» §7The ferry leaves from the Veil Quay, at the end of the King's Road.",
                    "§8Fähre §8» §7Die Fähre legt am Schleierkai ab, am Ende der Königsstraße."));
            return false;
        }
        String no = refusal(p);
        if (no != null) {
            long now = System.currentTimeMillis();
            Long last = lastRefusal.get(id);
            if (last == null || now - last > 2500L) {
                lastRefusal.put(id, now);
                p.sendMessage(Say.t(p, "§8Ferryman §8» §7", "§8Fährmann §8» §7") + no);
                p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 0.5f, 0.5f);
            }
            return false;
        }
        NetworkRouter router = plugin.router();
        CharacterTransport transport = router == null ? null : router.transport();
        if (transport == null) {
            p.sendMessage("§cThe ferry can't cast off on this backend (no character transport).");
            return false;
        }
        if (transport.isHeld(id) || p.isDead()) {
            return false;
        }
        if (walks.containsKey(id)) {
            return true;
        }
        if ("talk".equals(via) && walk(p)) {
            return true;
        }
        depart(p, null);
        return true;
    }

    /** The Veil comes up around the barge for this passenger (kept for the departure). */
    private void fogUp(Player p) {
        if (deckFog.containsKey(p.getUniqueId())) {
            return;
        }
        VeilFog fog = VeilFog.corridor(plugin, world, List.of(p), false, Math.max(wx(-38), wx(-70)), Math.min(wx(-38), wx(-70)),
                4.0, wz(0.5), origin[1] + 1.5, 24, 9, 0x16, 0x30, p.getUniqueId().getMostSignificantBits());
        deckFog.put(p.getUniqueId(), fog);
        p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.12f, 0.45f);
        VeilFog.breathe(p, at(new double[] {-48, 0.5, 0.5}), 7.0, 14, 3.5f);
    }

    // ------------------------------------------------------------------ the Ferryman's walk

    private final class Walk {
        final UUID id;
        final ExileFerryEscort escort;
        final List<Location> path = new ArrayList<>();
        int seg;
        int tick;
        int waited;
        int arrivedAt = -1;
        int boardAt = -1;
        float yaw;
        double y;
        BukkitTask task;

        Walk(UUID id, ExileFerryEscort escort) {
            this.id = id;
            this.escort = escort;
        }
    }

    /** "Cross the Veil" in person: he walks the passenger down to the ferry and takes the bow. False: no walk here. */
    private boolean walk(Player p) {
        ExileFerryEscort escort = ExileFerryEscort.start(plugin, p, at(FERRYMAN));
        if (escort == null) {
            return false;
        }
        Walk w = new Walk(p.getUniqueId(), escort);
        Location start = escort.at();
        w.path.add(start);
        for (double[] step : WALK) {
            w.path.add(at(step));
        }
        w.yaw = start.getYaw();
        w.y = start.getY();
        walks.put(w.id, w);
        p.sendActionBar(Component.text(Say.t(p, "§8Ferryman §7· Follow me. Mind the planks.",
                "§8Fährmann §7· Folg mir. Pass auf die Planken auf.")));
        p.playSound(start, Sound.BLOCK_LADDER_STEP, SoundCategory.NEUTRAL, 0.6f, 0.7f);
        w.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> walkTick(p, w), 1L, 1L);
        return true;
    }

    private void walkTick(Player p, Walk w) {
        if (!p.isOnline() || !p.getWorld().equals(world) || departing.containsKey(w.id)) {
            endWalk(w, false);
            return;
        }
        w.tick++;
        Location me = w.escort.at();
        double gap = p.getLocation().distance(me);
        if (gap > 26) {
            endWalk(w, true);
            return;
        }
        if (w.arrivedAt < 0) {
            if (gap > 5.5) {
                // he waits and looks back for the passenger
                w.yaw = approach(w.yaw, yawTo(me, p.getLocation()), 12f);
                me.setYaw(w.yaw);
                me.setPitch(0f);
                w.escort.moveTo(me);
                if (w.waited++ % 70 == 20) {
                    p.sendActionBar(Component.text(Say.t(p, "§8Ferryman §7· This way.", "§8Fährmann §7· Hier entlang.")));
                }
                return;
            }
            w.waited = 0;
            double left = WALK_SPEED;
            Location pos = me.clone();
            while (left > 0 && w.seg + 1 < w.path.size()) {
                Location target = w.path.get(w.seg + 1);
                double dx = target.getX() - pos.getX();
                double dz = target.getZ() - pos.getZ();
                double d = Math.hypot(dx, dz);
                if (d <= left) {
                    pos.setX(target.getX());
                    pos.setZ(target.getZ());
                    left -= d;
                    w.seg++;
                    continue;
                }
                pos.add(dx / d * left, 0, dz / d * left);
                w.yaw = approach(w.yaw, yawOf(dx, dz), 14f);
                left = 0;
            }
            double ground = ground(pos, w.path.get(Math.min(w.seg + 1, w.path.size() - 1)).getY());
            w.y += Math.max(-0.22, Math.min(0.22, ground - w.y));
            pos.setY(w.y);
            pos.setYaw(w.yaw);
            pos.setPitch(0f);
            w.escort.moveTo(pos);
            if (w.seg + 1 >= w.path.size()) {
                w.arrivedAt = w.tick;
                p.sendActionBar(Component.text(Say.t(p, "§8Ferryman §7· Step aboard. Hold the rail.",
                        "§8Fährmann §7· Komm an Bord. Halt dich an der Reling fest.")));
            }
            return;
        }
        // at the bow: he watches the passenger come aboard, then casts off once the fog is up
        if (w.tick % 6 == 0) {
            w.yaw = approach(w.yaw, onDeck(p.getLocation()) ? (float) (90.0 + (turned ? 180.0 : 0.0)) : yawTo(me, p.getLocation()), 10f);
            me.setYaw(w.yaw);
            w.escort.moveTo(me);
        }
        if (w.boardAt < 0 && onDeck(p.getLocation())) {
            fogUp(p);
            w.boardAt = w.tick + 30;
        }
        if (w.boardAt >= 0 && w.tick >= w.boardAt) {
            walks.remove(w.id);
            w.task.cancel();
            depart(p, w.escort);
            return;
        }
        if (w.tick - w.arrivedAt > 1200) {
            endWalk(w, true);
        }
    }

    private void endWalk(Walk w, boolean tell) {
        walks.remove(w.id);
        if (w.task != null) {
            w.task.cancel();
        }
        w.escort.end();
        Player p = Bukkit.getPlayer(w.id);
        if (tell && p != null && p.isOnline()) {
            p.sendActionBar(Component.text(Say.t(p, "§8Ferryman §7· I'll be at my post. The ferry waits.",
                    "§8Fährmann §7· Ich bin auf meinem Posten. Die Fähre wartet.")));
        }
    }

    /** Feet height on whatever stands near {@code around} (the pier, the gangway, the deck). */
    private double ground(Location at, double around) {
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        int by = (int) Math.floor(around + 0.5);
        for (int dy : new int[] {0, -1, 1, -2}) {
            Block below = world.getBlockAt(bx, by + dy - 1, bz);
            Block feet = world.getBlockAt(bx, by + dy, bz);
            if (!below.isPassable() && feet.isPassable()) {
                return below.getBoundingBox().getMaxY();
            }
        }
        return around;
    }

    private static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float yawTo(Location from, Location to) {
        return yawOf(to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    private static float approach(float from, float to, float step) {
        float d = ((to - from) % 360f + 540f) % 360f - 180f;
        return Math.abs(d) <= step ? to : from + Math.signum(d) * step;
    }

    // ------------------------------------------------------------------ the departure shot

    private final class Departure {
        final UUID id;
        final GameMode prior;
        CineCam cam;
        BlockRig rig;
        VeilFog fog;
        ExileFerryEscort escort;
        List<BlockDisplay> slings = List.of();
        BukkitTask task;
        Location fromEye;
        int tick;
        boolean sent;
        boolean ended;

        Departure(UUID id, GameMode prior) {
            this.id = id;
            this.prior = prior;
        }
    }

    private void depart(Player p, ExileFerryEscort escort) {
        Departure d = new Departure(p.getUniqueId(), p.getGameMode());
        d.escort = escort;
        departing.put(d.id, d);
        try {
            d.fromEye = p.getEyeLocation();
            Location root = at(ROOT);
            d.rig = BlockRig.spawn(plugin, root, 3, List.of(p));
            d.rig.copy(world, bx(BARGE[0]), origin[1] + BARGE[1], bz(BARGE[2]),
                    bx(BARGE[3]), origin[1] + BARGE[4], bz(BARGE[5]), ExileQuay::isBarge, true, root);
            d.rig.hideStatic(p);
            driving.add(d.id);
            d.cam = CineCam.start(plugin, p, d.fromEye, 3);
            driving.remove(d.id);
            // the sheets sink with the ferry line: authored x = -66 at the deck, 20 blocks lower at -122
            d.fog = VeilFog.corridor(plugin, world, List.of(p), false, Math.max(wx(-66), wx(-122)),
                    Math.min(wx(-66), wx(-122)), 5.0, wz(0.5),
                    x -> origin[1] + 2.0 + ((turned ? origin[0] + 1.0 - x : x - origin[0]) + 66.0) * (20.0 / 56.0),
                    70, 50, 0x34, 0x5C, d.id.getLeastSignificantBits());
            // the fog that came up on the deck goes along (or comes up now, rung from the pier)
            fogUp(p);
            VeilFog deck = deckFog.remove(d.id);
            if (deck != null) {
                d.fog.with(deck);
            }
            // the passenger's own trolleys and slings ride the cable with their barge; the docked ones hide
            for (UUID id : slings) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    p.hideEntity(plugin, e);
                }
            }
            d.slings = slingSet(ROOT[0], ROOT[1], p, false);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Veil Quay departure failed to start for " + p.getName() + ": " + ex.getMessage());
            end(p, d, true);
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.7f, 0.7f);
        p.sendActionBar(Component.text(d.escort != null
                ? Say.t(p, "§8Ferryman §7· Hold the rail. Here comes the fog.", "§8Fährmann §7· Halt dich fest. Jetzt kommt der Nebel.")
                : Say.t(p, "§8Ferryman §7· Hold the rail. I'll meet you on the other side.",
                        "§8Fährmann §7· Halt dich fest. Wir sehen uns auf der anderen Seite.")));
        d.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(p, d), 1L, 1L);
    }

    private static boolean isBarge(BlockData data) {
        Material m = data.getMaterial();
        return m != Material.STONE_BRICKS && m != Material.POLISHED_ANDESITE && m != Material.STONE_BRICK_SLAB
                && m != Material.STONE && m != Material.AIR;
    }

    /** First-person spot on the stern, relative to the rig origin (same as the exile side's opening frame). */
    private static final Vector STERN_EYE = new Vector(3.6, 1.7, -0.9);
    private static final Vector LOOK_AHEAD = new Vector(-14.0, 0.6, -0.4);

    private void tick(Player p, Departure d) {
        if (d.ended) {
            return;
        }
        if (!p.isOnline()) {
            end(p, d, false);
            return;
        }
        driving.add(d.id);
        try {
            d.cam.hold();
        } finally {
            driving.remove(d.id);
        }
        int k = d.tick++;
        double t = k / 20.0;
        Vector rel = path.at(t);
        Location root = new Location(world, wx(rel.getX()), origin[1] + rel.getY(), wz(rel.getZ()));
        d.rig.moveTo(root);
        moveSlings(d.slings, rel.getX(), rel.getY(), 3);
        if (d.escort != null) {
            Location bow = new Location(world, wx(rel.getX() + BOW_POST.getX()), origin[1] + rel.getY() + BOW_POST.getY(),
                    wz(rel.getZ() + BOW_POST.getZ()), (float) (90.0 + (turned ? 180.0 : 0.0)), 8f);
            d.escort.moveTo(bow);
        }

        Vector eye = root.toVector().add(dir(STERN_EYE));
        Vector look = root.toVector().add(dir(LOOK_AHEAD));
        double blend = Math.min(1.0, t / 1.6);
        blend = blend * blend * (3 - 2 * blend);
        Vector fromEye = d.fromEye.toVector();
        Vector camPos = fromEye.clone().multiply(1 - blend).add(eye.clone().multiply(blend));
        Vector fromLook = fromEye.clone().add(d.fromEye.getDirection().multiply(10));
        Vector camLook = fromLook.multiply(1 - blend).add(look.clone().multiply(blend));
        Location frame = new Location(world, camPos.getX(), camPos.getY(), camPos.getZ());
        frame.setDirection(camLook.subtract(camPos));
        d.cam.frame(frame);

        if (k % 25 == 0 && k > 30) {
            boolean left = (k / 25) % 2 == 0;
            d.rig.sway(left ? 1.0f : -1.0f, left ? 0.3f : -0.3f, 25);
        }
        if (k % 40 == 0) {
            d.rig.hideStatic(p);
        }
        if (k == 30) {
            p.playSound(frame, Sound.BLOCK_CHAIN_PLACE, SoundCategory.AMBIENT, 0.6f, 0.55f);
            p.playSound(frame, Sound.BLOCK_LADDER_STEP, SoundCategory.AMBIENT, 0.4f, 0.5f);
        }
        if (k == 70 || k == 120) {
            p.playSound(frame, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.18f, 0.5f);
        }
        if (k % 31 == 9) {
            p.playSound(frame, Sound.BLOCK_CHAIN_STEP, SoundCategory.AMBIENT, 0.3f, 0.55f);
        }
        if (k > 80 && k % 3 == 0) {
            VeilFog.breathe(p, frame.clone().add(frame.getDirection().multiply(5)), 6.0, k > 120 ? 9 : 5, 4.0f);
        }
        if (k >= 110 && k <= 170 && (k - 110) % 15 == 0) {
            d.cam.addWhite(1, 0x80);
        }
        if (k == 150) {
            p.playSound(frame, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.AMBIENT, 0.4f, 0.749f);
        }
        if (k == SEND_AT && !d.sent) {
            d.sent = true;
            d.cam.addWhite(2, 0xC0);
            boolean owned = link.send(p);
            if (!owned) {
                end(p, d, true);
            }
        }
        if (k >= GIVE_UP_AT) {
            // The proxy never moved them: the transport took the snapshot back. Back on the quay, nothing lost.
            p.sendMessage(Say.t(p, "§8Ferryman §8» §7The fog said no. Try again in a moment.",
                    "§8Fährmann §8» §7Der Nebel hat nein gesagt. Versuch's gleich noch mal."));
            end(p, d, true);
        }
    }

    private void end(Player p, Departure d, boolean putBack) {
        if (d.ended) {
            return;
        }
        d.ended = true;
        departing.remove(d.id);
        if (d.task != null) {
            d.task.cancel();
        }
        if (p != null && p.isOnline() && d.rig != null) {
            d.rig.restoreStatic(p);
        }
        if (d.rig != null) {
            d.rig.remove();
        }
        if (d.fog != null) {
            d.fog.remove();
        }
        dropRide(p, d);
        Location back = at(GANGWAY);
        GameMode mode = d.prior == GameMode.SPECTATOR ? GameMode.SURVIVAL : d.prior;
        if (p != null && p.isOnline()) {
            driving.add(d.id);
            try {
                if (d.cam != null) {
                    d.cam.end(putBack ? back : null, mode);
                }
                if (putBack && back != null && p.getLocation().distanceSquared(back) > 4) {
                    p.teleport(back);
                }
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    p.setGameMode(mode);
                }
            } finally {
                driving.remove(d.id);
            }
        } else if (d.cam != null) {
            d.cam.remove();
        }
    }

    // ------------------------------------------------------------------ guards

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (departing.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Departure d = departing.get(event.getPlayer().getUniqueId());
        if (d == null || d.ended || driving.contains(d.id)
                || event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * The hop itself ends the session here. Whatever this backend saves for the player (Paper playerdata) must
     * be a survivor on the quay, not a spectator over the void.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        onDeck.remove(p.getUniqueId());
        Walk walk = walks.get(p.getUniqueId());
        if (walk != null) {
            endWalk(walk, false);
        }
        VeilFog fog = deckFog.remove(p.getUniqueId());
        if (fog != null) {
            fog.remove();
        }
        Departure d = departing.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        driving.add(d.id);
        try {
            if (d.cam != null) {
                d.cam.end(at(LANDING), d.prior == GameMode.SPECTATOR ? GameMode.SURVIVAL : d.prior);
            }
        } finally {
            driving.remove(d.id);
        }
        d.ended = true;
        departing.remove(d.id);
        if (d.task != null) {
            d.task.cancel();
        }
        if (d.rig != null) {
            d.rig.remove();
        }
        if (d.fog != null) {
            d.fog.remove();
        }
        dropRide(p, d);
    }

    /** The passenger's own trolleys and slings go, the docked ones and the quay's Ferryman come back. */
    private void dropRide(Player p, Departure d) {
        for (BlockDisplay b : d.slings) {
            b.remove();
        }
        d.slings = List.of();
        if (p != null && p.isOnline()) {
            for (UUID id : slings) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    p.showEntity(plugin, e);
                }
            }
        }
        if (d.escort != null) {
            d.escort.end();
            d.escort = null;
        }
    }

    /** Back from the exile: off the ferry onto the pier, facing the way home (the Borderlands lie west). */
    public void landHere(Player p) {
        Location g = at(GANGWAY);
        if (g == null) {
            return;
        }
        boolean causeway = ExileCauseway.laid(plugin);
        // Turned, both the landing and the causeway lie west of the gangway; as authored the causeway leaves the
        // pier head (west) and the landing is east.
        g.setYaw(turned || causeway ? 90f : -90f);
        p.teleport(g);
        p.setFallDistance(0f);
        p.playSound(g, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.7f, 0.55f);
        p.playSound(g, Sound.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 0.6f, 0.5f);
        de.aetherion.core.api.Vfx.puff(g.clone().add(turned ? 6 : -6, 1, 0), Particle.WHITE_ASH, 30, Math.max(4, 3), null);
        String home = turned
                ? Say.t(p, "The Veil Quay. The way home runs west, past the King's Arch.",
                        "Der Schleierkai. Der Weg nach Hause führt nach Westen, durch den Königsbogen.")
                : causeway
                ? Say.t(p, "The Veil Quay. The causeway home leaves past the Veil lamp, west.",
                        "Der Schleierkai. Der Damm nach Hause beginnt an der Schleierlampe, im Westen.")
                : Say.t(p, "The Veil Quay. /spawn takes you home.",
                        "Der Schleierkai. /spawn bringt dich nach Hause.");
        p.sendActionBar(Component.text(home, NamedTextColor.GRAY));
    }

    public boolean isDeparting(Player p) {
        return p != null && departing.containsKey(p.getUniqueId());
    }

    public void shutdown() {
        for (Walk w : List.copyOf(walks.values())) {
            endWalk(w, false);
        }
        for (Departure d : List.copyOf(departing.values())) {
            end(Bukkit.getPlayer(d.id), d, true);
        }
        for (VeilFog fog : deckFog.values()) {
            fog.remove();
        }
        deckFog.clear();
        if (ambience != null) {
            ambience.cancel();
        }
    }

    /** Admin readout. */
    public String describe() {
        if (!placed()) {
            return "§7Veil Quay: §cnot placed §8(paste veil_quay.schem, stand on the origin, /exile setquay)";
        }
        return "§7Veil Quay: §f" + world.getName() + " " + origin[0] + " " + origin[1] + " " + origin[2]
                + " §8· §7ferry sails §f" + (turned ? "east" : "west") + " §8· §7departing §f" + departing.size();
    }
}
