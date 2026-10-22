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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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
        // chunks of the islet + the curtain, then the persistent Veil curtain (purged and rebuilt each boot)
        int cxA = bx(-130) >> 4;
        int cxB = bx(12) >> 4;
        for (int cx = Math.min(cxA, cxB); cx <= Math.max(cxA, cxB); cx++) {
            for (int cz = (o[2] - 40) >> 4; cz <= (o[2] + 40) >> 4; cz++) {
                w.getChunkAt(cx, cz).load();
            }
        }
        if (curtain != null) {
            curtain.remove();
        }
        purgeCurtainNear();
        curtain = VeilFog.corridor(plugin, w, null, true, Math.max(wx(-74), wx(-104)), Math.min(wx(-74), wx(-104)),
                6.0, wz(0.5), o[1] - 7.0, 78, 50, 0x26, 0x4C, 0x5E11L);
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
            String block = refusal(p);
            if (block == null) {
                p.sendActionBar(Component.text(Say.t(p, "Aboard the King's Ferry · ring the bell or sneak to cast off",
                        "An Bord der Königsfähre · Glocke läuten oder schleichen zum Ablegen"), NamedTextColor.GRAY));
                p.playSound(p.getLocation(), Sound.BLOCK_WOOD_STEP, SoundCategory.BLOCKS, 0.6f, 0.6f);
            } else {
                p.sendActionBar(Component.text(block, NamedTextColor.GRAY));
            }
        } else if (!now) {
            onDeck.remove(p.getUniqueId());
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
        depart(p);
        return true;
    }

    // ------------------------------------------------------------------ the departure shot

    private final class Departure {
        final UUID id;
        final GameMode prior;
        CineCam cam;
        BlockRig rig;
        VeilFog fog;
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

    private void depart(Player p) {
        Departure d = new Departure(p.getUniqueId(), p.getGameMode());
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
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Veil Quay departure failed to start for " + p.getName() + ": " + ex.getMessage());
            end(p, d, true);
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.7f, 0.7f);
        p.sendActionBar(Component.text(Say.t(p, "§8Ferryman §7· Hold the rail. I'll meet you on the other side.",
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
        for (Departure d : List.copyOf(departing.values())) {
            end(Bukkit.getPlayer(d.id), d, true);
        }
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
