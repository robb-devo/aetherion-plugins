package de.aetherion.quests.talk;

import de.aetherion.quests.AetherionQuests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen cards: a big card for a quest or milestone that floats in front of the player's eyes, and a small toast
 * at the top for a step done.
 *
 * <p>Look. The big card is a crest with the moment's item turning in it, a fan of light rising behind it, a ribbon
 * with the header across a framed plate with a point at the bottom, the title, and the rewards as slots with their
 * item and a number that counts up (other rows as a list with an icon). The toast is a slim plate with a badge on
 * its left edge (a check for "done", a gem for "new") and a bar along the bottom that runs down while it stays.
 * Every shape is a {@code " "} text display: its background is a 5 x 11 text px quad, scaled to any size and turned
 * about the view axis (diamonds, rays); text and items sit on top. Pieces spawn back to front, so the client draws
 * the translucent ones in that order.
 *
 * <p>How it stays on screen: every piece is a display entity standing at the player's eye with billboard CENTER.
 * A display's transformation is applied after the billboard turn, in camera space (local +Z faces the viewer), so
 * a translation of {@code (x, y, -depth)} keeps it {@code depth} blocks in front of the camera however the head
 * turns; the client does that every frame. Only the position comes from the server: when the eye moves, the pieces
 * move to it, a little ahead by the player's speed and ping, so walking barely drags the card. Nothing rides the
 * player (a player with passengers can't be teleported by plugins).
 *
 * <p>The card is drawn with depth like any other display, so a wall or an NPC closer than the card would cover it.
 * Every tick the space in front is probed across the whole card (nine rays: corners, edges, centre; blocks, block
 * and item displays by their drawn box, bodies, the NPC the player is talking to); when something is nearer, the
 * whole card moves closer and shrinks by the same factor, so on screen it keeps its exact size and place.
 *
 * <p>Visible to the owner only, never saved, tagged like the talk UI so the janitors clean up after a crash.
 * Off by default ({@code hud-cards.enabled}). One card at a time per player, the rest wait their turn; a big card
 * holds new talk lines back until it is gone, and a left-click (after a moment) sends it away early.
 *
 * <p>{@code hud-cards.big-stay}: a big card stays until the player sends it away (left-click or sneak) and says so
 * in its last line; it only leaves by itself after three minutes, so a forgotten card never blocks a talk for good.
 */
public final class HudCards implements Listener {

    public enum Kind {
        /** Small, at the top: a step done. */
        TOAST,
        /** Big, just above the middle: a quest done, a milestone. */
        BIG
    }

    /** One body row: legacy text and an optional item icon. "+250 Coins" rows with an icon become reward slots. */
    public record Row(String text, ItemStack icon) {
        public static Row of(String text) {
            return new Row(text, null);
        }
    }

    /**
     * {@code header}: on the ribbon (big) or the badge mark (toast, null = ✔). {@code title}: the big line (big) or
     * the bold first line (toast). {@code rows}: body rows (big) or detail lines (toast). {@code footer}: a muted
     * last line such as "Next: …" (may be null). {@code summary}: legacy chat line written once a big card has gone
     * (null = none); its hover shows the whole card. {@code emblem}: the item turning in a big card's crest (null =
     * the first block among the row icons, else a nether star).
     */
    public record Card(Kind kind, String header, String title, TextColor accent, List<Row> rows, String footer,
                       String summary, ItemStack emblem) {
        public Card(Kind kind, String header, String title, TextColor accent, List<Row> rows, String footer,
                    String summary) {
            this(kind, header, title, accent, rows, footer, summary, null);
        }
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final float PX = TalkChrome.PX;
    private static final float COLLAPSED = 0.01f;
    private static final byte INVISIBLE_TEXT = 12;
    /** "+250 Coins": the number counts up when its slot comes in. */
    private static final Pattern COUNT = Pattern.compile("^((?:§.)*)\\+(\\d{1,9})(.*)$");
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor BLACK = TextColor.color(0x000000);

    /* Layout, in card px (1 card px = PX × the card's scale; y up, the card's centre at 0). */
    private static final float RIBBON_H = 17f;
    private static final float SLOT = 24f;
    private static final float TILE_GAP = 4f;
    private static final float NAME_SCALE = 0.82f;
    private static final float FOOT_SCALE = 0.9f;
    /** The rays, degrees clockwise from straight up; even ones are long. */
    private static final int[] RAYS = {-64, -48, -32, -16, 0, 16, 32, 48, 64};

    /* Depth toward the viewer, in blocks: the order the layers cover each other. */
    private static final float Z_HALO = 0.0004f;
    private static final float Z_TAIL = 0.0012f;
    private static final float Z_FRAME = 0.0016f;
    private static final float Z_PLATE = 0.0020f;
    private static final float Z_INNER = 0.0024f;
    private static final float Z_DETAIL = 0.0032f;
    private static final float Z_STUD = 0.0036f;
    private static final float Z_FOLD = 0.0040f;
    private static final float Z_BAND = 0.0044f;
    private static final float Z_TEXT = 0.0052f;
    private static final float Z_CREST = 0.0056f;
    /** Items have depth of their own: they stand well in front (and are pulled in, so they land on their spot). */
    private static final float Z_ICON = 0.05f;
    private static final float Z_EMBLEM = 0.09f;

    private enum Form { RECT, TEXT, ITEM }

    /* What the timeline does with a piece. */
    private static final int FRAME = 0;
    private static final int RAY = 1;
    private static final int HALO = 2;
    private static final int CREST = 3;
    private static final int EMBLEM = 4;
    private static final int TITLE = 5;
    private static final int TILE = 6;
    private static final int TILE_ICON = 7;
    private static final int TILE_TEXT = 8;
    private static final int ROW = 9;
    private static final int ROW_ICON = 10;
    private static final int FOOTER = 11;
    private static final int BADGE = 12;
    private static final int BAR = 13;
    private static final int BODY = 14;

    private static HudCards instance;

    private final AetherionQuests plugin;
    private final Map<UUID, ArrayDeque<Card>> queues = new HashMap<>();
    private final Map<UUID, Shown> active = new HashMap<>();
    private final Map<UUID, Motion> motion = new HashMap<>();
    private final Map<UUID, Long> clickUsed = new HashMap<>();
    private BukkitTask ticker;
    private long tick;

    private boolean enabled;
    private double depth;
    private double bigY;
    private double toastY;
    private float bigScale;
    private float titleScale;
    private float toastScale;
    private int bigHold;
    private int toastHold;
    /** Big cards wait for the player (left-click or sneak) instead of running on a timer. */
    private boolean bigStay;
    /** A big card that nobody sends away still leaves after this long. */
    private static final int STAY_CAP = 20 * 180;

    private static final class Motion {
        Vector last;
        Vector vel = new Vector();
    }

    private static final class Piece {
        final Display display;
        final Form form;
        final int role;
        final int index;
        /** Rest centre in card px; a ray: its inner end; the toast bar: its left end. */
        final float cx;
        final float cy;
        /** RECT: size in card px. TEXT: w = scale (card px per text px). ITEM: w = size in card px. */
        final float w;
        final float h;
        /** RECT: turn in degrees, counter-clockwise on screen. */
        final float deg;
        final float z;
        /** TEXT: text block height in text px (lines × 10). */
        final int textH;
        /** RECT: its background at rest. */
        final Color color;
        float factor = 1f;
        float dx;
        float dy;
        float spin;
        float stretch = 1f;

        Piece(Display display, Form form, int role, int index, float cx, float cy, float w, float h, float deg,
              float z, int textH, Color color) {
            this.display = display;
            this.form = form;
            this.role = role;
            this.index = index;
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.deg = deg;
            this.z = z;
            this.textH = textH;
            this.color = color;
        }
    }

    /** A reward slot's number. */
    private static final class Tile {
        final Piece amount;
        final String prefix;
        final long target;
        long from = -1;

        Tile(Piece amount, String prefix, long target) {
            this.amount = amount;
            this.prefix = prefix;
            this.target = target;
        }
    }

    private static final class Shown {
        final Card card;
        final UUID owner;
        final List<Piece> pieces = new ArrayList<>();
        final List<Tile> tiles = new ArrayList<>();
        Piece title;
        String titleText = "";
        TextColor titleInk = TalkChrome.TEXT;
        Piece bar;
        int rows;
        boolean footer;
        float scale;
        double centerY;
        /** The whole card's size factor and lift (blocks): the pop in, the drift out. */
        float pop = COLLAPSED;
        float lift;
        long born;
        long revealEnd;
        long endAt;
        long closeAt = -1;
        /** Pulled in front of something near (1 = the configured depth); size shrinks with it, so nothing moves on screen. */
        float near = 1f;
        /** How far the card reaches from its centre, in card px (the probe's rays span it). */
        float halfW = 90f;
        float halfH = 60f;
        long lastProbe = -10;
        Location lastAt;
        boolean shimmer;

        Shown(Card card, UUID owner) {
            this.card = card;
            this.owner = owner;
        }
    }

    private HudCards(AetherionQuests plugin) {
        this.plugin = plugin;
        reload();
    }

    public static HudCards start(AetherionQuests plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new HudCards(plugin);
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, instance::tickAll, 1L, 1L);
        return instance;
    }

    public static HudCards get() {
        return instance;
    }

    public void reload() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("hud-cards.enabled", false);
        depth = clamp(cfg.getDouble("hud-cards.depth", 2.0), 0.8, 4.0);
        bigY = clamp(cfg.getDouble("hud-cards.big-y", 0.30), -1.0, 1.5);
        toastY = clamp(cfg.getDouble("hud-cards.toast-y", 0.98), -1.0, 1.8);
        bigScale = (float) clamp(cfg.getDouble("hud-cards.big-scale", 0.22), 0.08, 0.8);
        titleScale = (float) clamp(cfg.getDouble("hud-cards.title-scale", 0.37), 0.1, 1.2);
        toastScale = (float) clamp(cfg.getDouble("hud-cards.toast-scale", 0.24), 0.08, 0.8);
        bigHold = (int) clamp(cfg.getInt("hud-cards.big-hold-ticks", 80), 30, 400);
        toastHold = (int) clamp(cfg.getInt("hud-cards.toast-hold-ticks", 56), 20, 300);
        bigStay = cfg.getBoolean("hud-cards.big-stay", false);
        plugin.getLogger().info("Screen cards " + (enabled ? "on" : "off") + (bigStay ? ", big cards stay until closed" : "") + ".");
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (Shown sh : List.copyOf(active.values())) {
            kill(sh);
        }
        active.clear();
        queues.clear();
        if (instance == this) {
            instance = null;
        }
    }

    /* =====================================================================
     * API
     * ===================================================================== */

    /** Cards are on here and for this player (QA / stress bots keep the chat lines). */
    public boolean enabledFor(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return false;
        }
        TalkUx talk = TalkUx.get();
        return talk == null || !talk.isBot(player);
    }

    /** Queue a card. False = cards are off for this player: say it the old way. */
    public boolean show(Player player, Card card) {
        if (card == null || !enabledFor(player)) {
            return false;
        }
        card = wrapped(journal(card));
        if (bigStay && card.kind() == Kind.BIG) {
            card = withCloseHint(player, card);
        }
        UUID id = player.getUniqueId();
        queues.computeIfAbsent(id, k -> new ArrayDeque<>()).addLast(card);
        Shown now = active.get(id);
        if (now != null && card.kind() == Kind.BIG && now.card.kind() == Kind.TOAST && now.closeAt < 0) {
            // a toast never keeps a big card waiting
            now.endAt = Math.min(now.endAt, tick + 1);
        }
        return true;
    }

    /** Stay mode: the card's last line says how it goes away. */
    private static Card withCloseHint(Player player, Card c) {
        String hint = de.aetherion.core.exile.Say.t(player, "§8Sneak or left-click to close",
                "§8Schleichen oder Linksklick: schließen");
        String footer = c.footer() == null || c.footer().isBlank() ? hint : c.footer() + "\n" + hint;
        return new Card(c.kind(), c.header(), c.title(), c.accent(), c.rows(), footer, c.summary(), c.emblem());
    }

    /** While the journal is on, Manager and Nether Star read as the journal, texts other plugins hand in included. */
    private static Card journal(Card c) {
        if (!de.aetherion.core.api.JournalText.enabled()) {
            return c;
        }
        List<Row> rows = null;
        if (c.rows() != null) {
            rows = new ArrayList<>(c.rows().size());
            for (Row row : c.rows()) {
                rows.add(row == null ? null : new Row(de.aetherion.core.api.JournalText.t(row.text()), row.icon()));
            }
        }
        return new Card(c.kind(), de.aetherion.core.api.JournalText.t(c.header()),
                de.aetherion.core.api.JournalText.t(c.title()), c.accent(), rows,
                de.aetherion.core.api.JournalText.t(c.footer()), de.aetherion.core.api.JournalText.t(c.summary()),
                c.emblem());
    }

    /** Long lines wrap (a toast's rows and footer, a big card's footer), so no card grows wider than it reads. */
    private static Card wrapped(Card c) {
        boolean big = c.kind() == Kind.BIG;
        List<Row> rows = c.rows() == null ? List.of() : c.rows();
        List<Row> out = new ArrayList<>();
        for (Row row : rows) {
            if (row == null || row.text() == null) {
                continue;
            }
            if (big) {
                out.add(row);
            } else {
                for (String part : TalkText.wrap(row.text(), 200).split("\n", -1)) {
                    out.add(Row.of(part));
                }
            }
        }
        String footer = c.footer() == null || c.footer().isBlank() ? null : TalkText.wrap(c.footer(), big ? 230 : 200);
        return new Card(c.kind(), c.header(), c.title(), c.accent(), out, footer, c.summary(), c.emblem());
    }

    private static String[] footerLines(Card c) {
        return c.footer() == null ? new String[0] : c.footer().split("\n", -1);
    }

    /** A big card is up (or waiting): new talk lines hold back until it is gone. */
    public static boolean blocking(Player player) {
        HudCards cards = instance;
        if (cards == null || player == null) {
            return false;
        }
        Shown sh = cards.active.get(player.getUniqueId());
        if (sh != null && sh.card.kind() == Kind.BIG && sh.closeAt < 0) {
            return true;
        }
        ArrayDeque<Card> queue = cards.queues.get(player.getUniqueId());
        if (queue != null) {
            for (Card c : queue) {
                if (c.kind() == Kind.BIG) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Send the big card away now (a click). True = there was one to send away. */
    public static boolean dismiss(Player player) {
        HudCards cards = instance;
        if (cards == null || player == null) {
            return false;
        }
        Shown sh = cards.active.get(player.getUniqueId());
        if (sh == null || sh.card.kind() != Kind.BIG || sh.closeAt >= 0 || cards.tick - sh.born < 12) {
            return false;
        }
        sh.endAt = cards.tick;
        return true;
    }

    /** This tick's left-click already went to a card (talk must not use it too). */
    public static boolean clickUsed(Player player) {
        HudCards cards = instance;
        if (cards == null || player == null) {
            return false;
        }
        Long at = cards.clickUsed.get(player.getUniqueId());
        return at != null && cards.tick - at <= 1;
    }

    /* =====================================================================
     * Tick
     * ===================================================================== */

    private void tickAll() {
        tick++;
        if (active.isEmpty() && queues.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(queues.keySet())) {
            if (active.containsKey(id)) {
                continue;
            }
            ArrayDeque<Card> queue = queues.get(id);
            Card next = queue == null ? null : queue.pollFirst();
            if (queue != null && queue.isEmpty()) {
                queues.remove(id);
            }
            Player player = Bukkit.getPlayer(id);
            if (next == null || player == null || !player.isOnline()) {
                continue;
            }
            Shown sh = open(player, next);
            if (sh != null) {
                active.put(id, sh);
            }
        }
        for (Shown sh : List.copyOf(active.values())) {
            Player player = Bukkit.getPlayer(sh.owner);
            if (player == null || !player.isOnline()) {
                kill(sh);
                active.remove(sh.owner);
                continue;
            }
            try {
                follow(player, sh);
                probe(player, sh);
                step(player, sh);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Card error: " + ex.getMessage());
                kill(sh);
                active.remove(sh.owner);
            }
        }
    }

    /** Move every piece to the eye, ahead by speed × (ping + a tick of interpolation); nothing to send when still. */
    private void follow(Player player, Shown sh) {
        Location eye = player.getEyeLocation();
        Motion m = motion.computeIfAbsent(player.getUniqueId(), k -> new Motion());
        Vector now = eye.toVector();
        if (m.last != null) {
            Vector delta = now.clone().subtract(m.last);
            if (delta.lengthSquared() > 16.0) {
                m.vel = new Vector();
            } else {
                m.vel = m.vel.multiply(0.5).add(delta.multiply(0.5));
            }
        }
        m.last = now;
        // the server already sees the player half a ping late, the card arrives half a ping later and then glides in
        double lead = Math.max(1.0, Math.min(6.0, player.getPing() / 50.0 + 1.5));
        Location at = eye.clone().add(m.vel.clone().multiply(lead));
        if (sh.lastAt != null && sh.lastAt.getWorld() == at.getWorld() && sh.lastAt.distanceSquared(at) < 1.0E-6) {
            return;
        }
        sh.lastAt = at;
        for (Piece p : sh.pieces) {
            if (p.display.isValid() && p.display.getWorld() == at.getWorld()) {
                p.display.teleport(at);
            }
        }
    }

    /**
     * Something nearer than the card anywhere across it (a wall, a house, a machine's props, the NPC in front):
     * bring the card in front of it. Translation and scale shrink by the same factor, so it keeps its size and
     * place on screen, and a pull that wasn't needed costs nothing. Nine rays span the card (corners, edges,
     * centre), so a corner or the side of a building can't cut into it. Block and item displays (the islands'
     * buildings and props are made of them) count by their drawn box, bodies by their hitbox. Runs every tick and
     * pulls in within a tick; it lets go slowly.
     */
    private void probe(Player player, Shown sh) {
        if (sh.closeAt >= 0) {
            return;
        }
        sh.lastProbe = tick;
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return;
        }
        double yaw = Math.toRadians(eye.getYaw());
        double pitch = Math.toRadians(eye.getPitch());
        Vector forward = new Vector(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vector right = new Vector(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vector up = new Vector(-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch));
        double unit = PX * sh.scale;
        double hw = sh.halfW * unit;
        double hh = sh.halfH * unit;
        Vector origin = eye.toVector();
        List<Vector> lines = new ArrayList<>(9);
        for (int ix = -1; ix <= 1; ix++) {
            for (int iy = -1; iy <= 1; iy++) {
                lines.add(forward.clone().multiply(depth).add(up.clone().multiply(sh.centerY + iy * hh))
                        .add(right.clone().multiply(ix * hw)));
            }
        }
        double k = 1.0;
        for (Vector line : lines) {
            double full = line.length();
            Vector dir = line.clone().multiply(1.0 / full);
            RayTraceResult wall = world.rayTraceBlocks(eye, dir, full + 0.35, FluidCollisionMode.NEVER, true);
            if (wall != null && wall.getHitBlock() != null) {
                double d = wall.getHitPosition().distance(origin);
                k = Math.min(k, (d - Math.min(0.3, d * 0.25)) / full);
            }
        }
        double reach = depth + Math.max(hw, hh) + Math.abs(sh.centerY) + 0.6;
        Entity vehicle = player.getVehicle();
        for (Entity e : world.getNearbyEntities(eye, reach, reach, reach)) {
            if (e == player || e == vehicle || e instanceof TextDisplay || e.getScoreboardTags().contains(TalkUx.TAG)
                    || player.getPassengers().contains(e)) {
                continue;
            }
            BoundingBox box = e instanceof Display d ? drawnBox(d) : e.getBoundingBox();
            if (box == null || small(box)) {
                continue;
            }
            for (Vector line : lines) {
                double full = line.length();
                RayTraceResult hit = box.rayTrace(origin, line, full + 0.35);
                if (hit != null) {
                    double d = hit.getHitPosition().distance(origin);
                    k = Math.min(k, (d - Math.min(0.25, d * 0.25)) / full);
                }
            }
        }
        // the NPC being talked to (a packet NPC has no body on the server)
        TalkUx talk = TalkUx.get();
        Location npc = talk == null ? null : talk.npcNear(player);
        if (npc != null && npc.getWorld() == world) {
            Vector centre = lines.get(4);
            double full = centre.length();
            Vector to = npc.toVector().add(new Vector(0, 1.1, 0)).subtract(origin);
            double along = to.dot(forward);
            if (along > 0.0 && along < full + 0.6) {
                double off = to.clone().subtract(forward.clone().multiply(along)).length();
                if (off < 1.1 + Math.max(hw, hh) * 0.5) {
                    k = Math.min(k, (along - 0.6) / full);
                }
            }
        }
        float kk = (float) clamp(k, 0.12, 1.0);
        if (Math.abs(kk - sh.near) < 0.04 && !(kk >= 0.999f && sh.near < 0.999f)) {
            return;
        }
        boolean closer = kk < sh.near;
        sh.near = kk;
        moveAll(sh, closer ? 1 : 4);
    }

    /** Too small to hide a card behind (goods on a belt, a dropped item, sparks). */
    private static boolean small(BoundingBox box) {
        return box.getWidthX() < 0.5 && box.getHeight() < 0.5 && box.getWidthZ() < 0.5;
    }

    /**
     * Where a block or item display is drawn, as a box in the world: its model's cube through the transformation
     * and the entity's own turn (an item shows smaller on the ground or in a hand than as a block).
     */
    private static BoundingBox drawnBox(Display d) {
        if (!(d instanceof BlockDisplay) && !(d instanceof ItemDisplay)) {
            return null;
        }
        float size = 1f;
        if (d instanceof ItemDisplay item) {
            size = switch (item.getItemDisplayTransform()) {
                case GROUND -> 0.5f;
                case THIRDPERSON_LEFTHAND, THIRDPERSON_RIGHTHAND -> 0.55f;
                case FIRSTPERSON_LEFTHAND, FIRSTPERSON_RIGHTHAND -> 0.7f;
                default -> 1f;
            };
        }
        Transformation t = d.getTransformation();
        Matrix4f m = new Matrix4f().translation(t.getTranslation()).rotate(t.getLeftRotation()).scale(t.getScale())
                .rotate(t.getRightRotation());
        Location at = d.getLocation();
        Quaternionf turn = new Quaternionf().rotationYXZ((float) Math.toRadians(-at.getYaw()),
                (float) Math.toRadians(at.getPitch()), 0f);
        float lo = d instanceof BlockDisplay ? 0f : -0.5f * size;
        float span = d instanceof BlockDisplay ? 1f : size;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        Vector3f v = new Vector3f();
        for (int i = 0; i < 8; i++) {
            v.set(lo + span * (i & 1), lo + span * ((i >> 1) & 1), lo + span * ((i >> 2) & 1));
            m.transformPosition(v);
            turn.transform(v);
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }
        return new BoundingBox(at.getX() + minX, at.getY() + minY, at.getZ() + minZ,
                at.getX() + maxX, at.getY() + maxY, at.getZ() + maxZ);
    }

    private void step(Player player, Shown sh) {
        long t = tick - sh.born;
        if (sh.closeAt >= 0) {
            if (tick - sh.closeAt >= 5) {
                kill(sh);
                active.remove(sh.owner);
                if (sh.card.kind() == Kind.BIG) {
                    summary(player, sh.card);
                }
            }
            return;
        }
        if (tick >= sh.endAt) {
            close(player, sh);
            return;
        }
        if (sh.card.kind() == Kind.BIG) {
            stepBig(player, sh, t);
        } else {
            stepToast(sh, t);
        }
    }

    /**
     * The big card's entrance: the frame pops, the crest drops in and flashes, the rays grow from the middle out,
     * the item comes and turns, the title fades up, the slots pop one by one with their numbers counting, list rows
     * slide in, then the footer. Once all is in, a glint runs over the title and the rays breathe.
     */
    private void stepBig(Player player, Shown sh, long t) {
        if (t == 1) {
            sh.pop = 1.06f;
            sh.lift = 0f;
            moveAll(sh, 4);
        } else if (t == 5) {
            sh.pop = 1f;
            moveAll(sh, 3);
        }
        if (t == 3) {
            for (Piece p : sh.pieces) {
                if (p.role == CREST) {
                    p.factor = 1.22f;
                    p.dy = 0f;
                    move(sh, p, 3);
                }
            }
        } else if (t == 6) {
            for (Piece p : sh.pieces) {
                if (p.role == CREST) {
                    p.factor = 1f;
                    move(sh, p, 3);
                } else if (p.role == HALO) {
                    p.factor = 1.3f;
                    move(sh, p, 2);
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.MASTER, 0.7f, 1.25f);
        } else if (t == 9) {
            for (Piece p : sh.pieces) {
                if (p.role == HALO) {
                    p.factor = 1f;
                    move(sh, p, 10);
                }
            }
        }
        if (t >= 7 && t <= 7 + RAYS.length / 2) {
            int order = (int) (t - 7);
            for (Piece p : sh.pieces) {
                if (p.role == RAY && p.index == order) {
                    p.stretch = 1f;
                    move(sh, p, 5);
                    tint(p, p.color, 5);
                }
            }
        }
        if (t == 8 || t == 11) {
            for (Piece p : sh.pieces) {
                if (p.role == EMBLEM) {
                    p.factor = t == 8 ? 1.2f : 1f;
                    p.dy = 0f;
                    move(sh, p, 3);
                }
            }
        }
        if (t >= 16 && (t - 16) % 10 == 0) {
            for (Piece p : sh.pieces) {
                if (p.role == EMBLEM) {
                    p.spin = (float) ((p.spin + Math.PI / 3.0) % (Math.PI * 2.0));
                    move(sh, p, 10);
                }
            }
        }
        if (t == 9 && sh.title != null) {
            sh.title.dy = 0f;
            move(sh, sh.title, 6);
            fade(sh.title, 255, 6);
        }
        for (int i = 0; i < sh.tiles.size(); i++) {
            long at = 12L + i * 5L;
            if (t == at) {
                for (Piece p : sh.pieces) {
                    if (p.index != i) {
                        continue;
                    }
                    if (p.role == TILE) {
                        p.factor = 1.14f;
                        move(sh, p, 3);
                    } else if (p.role == TILE_ICON) {
                        p.factor = 1.28f;
                        move(sh, p, 3);
                    } else if (p.role == TILE_TEXT) {
                        move(sh, p, 3);
                        fade(p, 255, 3);
                    }
                }
                Tile tile = sh.tiles.get(i);
                tile.from = tick;
                setAmount(tile, 0L);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER,
                        0.35f, (float) Math.min(2.0, 1.05 + i * 0.14));
            } else if (t == at + 3) {
                for (Piece p : sh.pieces) {
                    if (p.index == i && (p.role == TILE || p.role == TILE_ICON)) {
                        p.factor = 1f;
                        move(sh, p, 3);
                    }
                }
            }
        }
        for (Tile tile : sh.tiles) {
            if (tile.from >= 0 && tick - tile.from <= 8) {
                double k = Math.min(1.0, (tick - tile.from) / 8.0);
                setAmount(tile, Math.round(tile.target * (1.0 - Math.pow(1.0 - k, 3))));
            }
        }
        long rowsAt = 12L + sh.tiles.size() * 5L;
        for (int i = 0; i < sh.rows; i++) {
            if (t != rowsAt + i * 4L) {
                continue;
            }
            for (Piece p : sh.pieces) {
                if (p.index != i) {
                    continue;
                }
                if (p.role == ROW) {
                    p.dx = 0f;
                    move(sh, p, 4);
                    fade(p, 255, 4);
                } else if (p.role == ROW_ICON) {
                    p.factor = 1f;
                    move(sh, p, 4);
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.3f, 1.6f);
        }
        if (sh.footer && t == rowsAt + sh.rows * 4L + 2L) {
            for (Piece p : sh.pieces) {
                if (p.role == FOOTER) {
                    fade(p, 255, 5);
                }
            }
        }
        long glint = sh.revealEnd;
        if (sh.title != null && t >= glint && t <= glint + 16) {
            ((TextDisplay) sh.title.display).text(titleText(sh, (t - glint) / 16.0));
        }
        if (t > glint + 16 && (t - glint) % 12 == 0) {
            // the rays breathe: long and short take turns
            sh.shimmer = !sh.shimmer;
            for (Piece p : sh.pieces) {
                if (p.role == RAY) {
                    boolean bright = (p.index % 2 == 0) != sh.shimmer;
                    int a = (int) Math.round(p.color.getAlpha() * (bright ? 1.0 : 0.5));
                    tint(p, Color.fromARGB(a, p.color.getRed(), p.color.getGreen(), p.color.getBlue()), 12);
                }
            }
        }
    }

    /** The toast drops in, its badge pops, the bar along its bottom runs down while it stays. */
    private void stepToast(Shown sh, long t) {
        if (t == 1) {
            sh.pop = 1.04f;
            sh.lift = 0f;
            moveAll(sh, 4);
            for (Piece p : sh.pieces) {
                if (p.role == BODY) {
                    fade(p, 255, 4);
                }
            }
        } else if (t == 5) {
            sh.pop = 1f;
            moveAll(sh, 3);
        }
        if (t == 3 || t == 6) {
            for (Piece p : sh.pieces) {
                if (p.role == BADGE) {
                    p.factor = t == 3 ? 1.25f : 1f;
                    move(sh, p, 3);
                }
            }
        }
        boolean stays = bigStay && sh.card.kind() == Kind.BIG;
        if (sh.bar != null && !stays && t >= 6 && (t - 6) % 5 == 0) {
            long total = Math.max(1L, sh.endAt - (sh.born + 6L));
            sh.bar.stretch = (float) clamp((sh.endAt - tick) / (double) total, 0.0, 1.0);
            move(sh, sh.bar, 5);
        }
    }

    /* =====================================================================
     * Build
     * ===================================================================== */

    private Shown open(Player player, Card card) {
        Shown sh = new Shown(card, player.getUniqueId());
        sh.born = tick;
        boolean big = card.kind() == Kind.BIG;
        sh.scale = big ? bigScale : toastScale;
        sh.centerY = big ? bigY : toastY;
        sh.lift = big ? -0.05f : 0.08f;
        Location eye = player.getEyeLocation();
        if (big) {
            buildBig(sh, eye);
        } else {
            buildToast(sh, eye);
        }
        if (sh.pieces.isEmpty()) {
            return null;
        }
        // back to front: the client draws them in the order it learns of them
        for (Piece p : sh.pieces) {
            applyNow(sh, p);
            player.showEntity(plugin, p.display);
        }
        if (big) {
            sh.revealEnd = 12L + sh.tiles.size() * 5L + sh.rows * 4L + 4L;
            sh.endAt = tick + sh.revealEnd + (bigStay ? STAY_CAP : bigHold);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.MASTER, 0.9f, 1.0f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.75f, 1.05f);
        } else {
            // a toast with more to read stays a little longer
            int extra = 14 * Math.max(0, card.rows().size() + footerLines(card).length - 1);
            sh.revealEnd = 6L;
            sh.endAt = tick + sh.revealEnd + toastHold + extra;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.55f, 1.55f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 0.6f, 1.3f);
        }
        return sh;
    }

    /**
     * Crest, rays, ribbon, plate, title, slots, list, footer. Measured down from the plate's top edge ({@code yd}),
     * then centred: the crest's top and the plate's point are as far above and below the card's centre.
     */
    private void buildBig(Shown sh, Location eye) {
        Card c = sh.card;
        TextColor accent = accent(c);
        float ts = titleScale / sh.scale;
        List<Row> tiles = new ArrayList<>();
        List<Row> list = new ArrayList<>();
        for (Row row : c.rows()) {
            if (tiles.size() < 5 && hasIcon(row) && COUNT.matcher(row.text()).matches()) {
                tiles.add(row);
            } else {
                list.add(row);
            }
        }
        String header = TalkText.strip(c.header() == null ? "" : c.header()).toUpperCase(Locale.ROOT);
        String[] titleLines = TalkText.strip(TalkText.wrap("§l" + TalkText.strip(c.title() == null ? "" : c.title()), 150))
                .split("\n", -1);
        float titleW = 0f;
        for (String line : titleLines) {
            titleW = Math.max(titleW, TalkChrome.width(line, true) * ts);
        }
        int n = tiles.size();
        float[] tileW = new float[n];
        String[] tileName = new String[n];
        String[] tilePrefix = new String[n];
        long[] tileTarget = new long[n];
        float tilesW = 0f;
        for (int i = 0; i < n; i++) {
            Matcher m = COUNT.matcher(tiles.get(i).text());
            if (!m.matches()) {
                continue;
            }
            tilePrefix[i] = m.group(1);
            tileTarget[i] = Long.parseLong(m.group(2));
            tileName[i] = TalkText.strip(m.group(3)).trim();
            float amountW = TalkChrome.width("+" + m.group(2), true);
            float nameW = TalkChrome.width(tileName[i], false) * NAME_SCALE;
            tileW[i] = Math.max(32f, Math.max(amountW + 10f, nameW + 10f));
            tilesW += tileW[i] + (i > 0 ? TILE_GAP : 0f);
        }
        float listW = 0f;
        for (Row row : list) {
            listW = Math.max(listW, TalkChrome.legacyWidth(row.text()) + (hasIcon(row) ? 13f : 0f));
        }
        String[] foot = footerLines(c);
        float footW = 0f;
        for (String line : foot) {
            footW = Math.max(footW, TalkChrome.legacyWidth(line) * FOOT_SCALE);
        }
        float inner = Math.max(150f, Math.max(titleW + 10f, Math.max(tilesW, Math.max(listW + 4f, footW))));
        inner = Math.max(inner, TalkChrome.width(header, true) - 6f);
        float pw = inner + 26f;
        float rw = pw + 22f;
        float rh = RIBBON_H;

        float y = rh / 2f + 9f;
        float titleH = (titleLines.length * 10f - 2f) * ts;
        float titleCy = y + titleH / 2f;
        y += titleH + 7f;
        float divY = y;
        y += 8f;
        float slotCy = 0f;
        float amountCy = 0f;
        float nameCy = 0f;
        if (n > 0) {
            slotCy = y + SLOT / 2f;
            amountCy = y + SLOT + 7f;
            nameCy = amountCy + 9f;
            y = nameCy + 7f;
        }
        float listTop = y;
        y += 12f * list.size() + (list.isEmpty() ? 0f : 3f);
        float footCy = 0f;
        if (foot.length > 0) {
            float footH = (foot.length * 10f - 2f) * FOOT_SCALE;
            footCy = y + 2f + footH / 2f;
            y = footCy + footH / 2f + 7f;
        }
        float ph = y + 5f;
        float crestYd = -(rh / 2f + 20f);
        // card y (up) of a yd (down from the plate's top): mid - yd
        float mid = ((crestYd - 23f) + (ph + 13f)) / 2f;
        float crestY = mid - crestYd;
        // the card's reach for the probe: ribbon tails at the sides, crest and rays above, the point below
        sh.halfW = rw / 2f + 19f;
        sh.halfH = ((ph + 13f) - (crestYd - 23f)) / 2f + 12f;

        // rays: a fan rising behind the crest, long and short
        for (int i = 0; i < RAYS.length; i++) {
            int order = Math.abs(i - RAYS.length / 2);
            if (i % 2 == 0) {
                ray(sh, eye, order, crestY, 22f, 26f, 3.2f, RAYS[i], argb(accent, 125));
                ray(sh, eye, order, crestY, 48f, 18f, 2.0f, RAYS[i], argb(accent, 58));
            } else {
                ray(sh, eye, order, crestY, 22f, 20f, 2.0f, RAYS[i], argb(accent, 72));
            }
        }
        TextColor halo = TalkChrome.mix(accent, WHITE, 0.25);
        diamond(sh, eye, HALO, 0, 0f, crestY, 74f, Z_HALO, argb(halo, 30));
        diamond(sh, eye, HALO, 1, 0f, crestY, 58f, Z_HALO + 0.0004f, argb(halo, 58));
        // ribbon tails, behind the plate
        Color tail = argb(TalkChrome.mix(accent, BLACK, 0.5), 255);
        for (int side = -1; side <= 1; side += 2) {
            rect(sh, eye, FRAME, 0, side * (rw / 2f + 7f), mid - 6f, 24f, 12f, 0f, Z_TAIL, tail);
        }
        // plate: frame, body, the point below, a fine inner line
        Color frame = argb(TalkChrome.mix(accent, TalkChrome.INK, 0.38), 255);
        float plateY = mid - ph / 2f;
        float pointY = mid - ph;
        rect(sh, eye, FRAME, 0, 0f, plateY, pw + 3f, ph + 3f, 0f, Z_FRAME, frame);
        diamond(sh, eye, FRAME, 0, 0f, pointY, 30f, Z_FRAME, frame);
        Color ink = TalkChrome.ink(246);
        rect(sh, eye, FRAME, 0, 0f, plateY, pw, ph, 0f, Z_PLATE, ink);
        diamond(sh, eye, FRAME, 0, 0f, pointY, 26f, Z_PLATE, ink);
        rect(sh, eye, FRAME, 0, 0f, plateY - 1f, pw - 8f, ph - 10f, 0f, Z_INNER,
                argb(TalkChrome.mix(accent, TalkChrome.INK, 0.74), 255));
        rect(sh, eye, FRAME, 0, 0f, plateY - 1f, pw - 10f, ph - 12f, 0f, Z_INNER + 0.0004f,
                Color.fromARGB(255, 22, 21, 32));
        Color gold = argb(accent, 255);
        for (int side = -1; side <= 1; side += 2) {
            diamond(sh, eye, FRAME, 0, side * pw / 2f, pointY, 8f, Z_STUD, gold);
        }
        diamond(sh, eye, FRAME, 0, 0f, mid - (ph + 9f), 6f, Z_STUD, gold);
        // folds where the band turns back into its tails
        Color fold = argb(TalkChrome.mix(accent, BLACK, 0.68), 255);
        for (int side = -1; side <= 1; side += 2) {
            diamond(sh, eye, FRAME, 0, side * (rw / 2f - 4f), mid - rh / 2f, 10f, Z_FOLD, fold);
        }
        // the band: pointed ends, a lit top edge and a shaded bottom edge, the header on it
        Color band = argb(TalkChrome.mix(accent, BLACK, 0.1), 255);
        rect(sh, eye, FRAME, 0, 0f, mid, rw, rh, 0f, Z_BAND, band);
        for (int side = -1; side <= 1; side += 2) {
            diamond(sh, eye, FRAME, 0, side * rw / 2f, mid, rh, Z_BAND, band);
        }
        rect(sh, eye, FRAME, 0, 0f, mid + rh / 2f - 1.5f, rw, 3f, 0f, Z_BAND + 0.0004f,
                argb(TalkChrome.mix(accent, WHITE, 0.4), 255));
        rect(sh, eye, FRAME, 0, 0f, mid - rh / 2f + 1.25f, rw, 2.5f, 0f, Z_BAND + 0.0004f,
                argb(TalkChrome.mix(accent, BLACK, 0.38), 255));
        text(sh, eye, FRAME, 0, 0f, mid - 0.3f,
                Component.text(header, TalkChrome.mix(accent, BLACK, 0.83), TextDecoration.BOLD), 1, 1f, Z_TEXT,
                false, TextDisplay.TextAlignment.CENTER, (byte) -1);
        // crest and its item
        diamond(sh, eye, CREST, 0, 0f, crestY, 47f, Z_CREST, argb(TalkChrome.mix(accent, WHITE, 0.35), 255));
        diamond(sh, eye, CREST, 0, 0f, crestY, 43f, Z_CREST + 0.0004f, argb(TalkChrome.mix(accent, BLACK, 0.3), 255));
        diamond(sh, eye, CREST, 0, 0f, crestY, 37f, Z_CREST + 0.0008f, Color.fromARGB(255, 26, 24, 38));
        ItemStack emblem = emblem(c);
        item(sh, eye, EMBLEM, 0, 0f, crestY, emblem.getType().isBlock() ? 27f : 23f, emblem, Z_EMBLEM);
        // title
        sh.titleText = String.join("\n", titleLines);
        sh.titleInk = TalkChrome.mix(accent, WHITE, 0.82);
        sh.title = text(sh, eye, TITLE, 0, 0f, mid - titleCy, titleText(sh, -1.0), titleLines.length, ts, Z_TEXT,
                true, TextDisplay.TextAlignment.CENTER, INVISIBLE_TEXT);
        // divider
        Color line = argb(TalkChrome.mix(accent, TalkChrome.INK, 0.3), 255);
        rect(sh, eye, FRAME, 0, -30f, mid - divY, 44f, 1f, 0f, Z_DETAIL, line);
        rect(sh, eye, FRAME, 0, 30f, mid - divY, 44f, 1f, 0f, Z_DETAIL, line);
        diamond(sh, eye, FRAME, 0, 0f, mid - divY, 6f, Z_DETAIL, gold);
        // reward slots
        Color rim = argb(TalkChrome.mix(accent, TalkChrome.INK, 0.55), 255);
        Color slot = Color.fromARGB(255, 31, 29, 44);
        float x = -tilesW / 2f;
        for (int i = 0; i < n; i++) {
            float tx = x + tileW[i] / 2f;
            rect(sh, eye, TILE, i, tx, mid - slotCy, SLOT + 2f, SLOT + 2f, 0f, Z_DETAIL, rim);
            rect(sh, eye, TILE, i, tx, mid - slotCy, SLOT, SLOT, 0f, Z_DETAIL + 0.0004f, slot);
            ItemStack icon = tiles.get(i).icon();
            item(sh, eye, TILE_ICON, i, tx, mid - slotCy, icon.getType().isBlock() ? 20f : 17f, icon, Z_ICON);
            String prefix = tilePrefix[i] == null ? "" : tilePrefix[i];
            Piece amount = text(sh, eye, TILE_TEXT, i, tx, mid - amountCy, amountText(prefix, tileTarget[i]), 1, 1f,
                    Z_TEXT, true, TextDisplay.TextAlignment.CENTER, INVISIBLE_TEXT);
            text(sh, eye, TILE_TEXT, i, tx, mid - nameCy,
                    Component.text(tileName[i] == null ? "" : tileName[i], TalkChrome.MUTED), 1, NAME_SCALE, Z_TEXT,
                    true, TextDisplay.TextAlignment.CENTER, INVISIBLE_TEXT);
            if (amount != null) {
                sh.tiles.add(new Tile(amount, prefix, tileTarget[i]));
            }
            x += tileW[i] + TILE_GAP;
        }
        // other rows: icon and text, the block centred
        float left = -listW / 2f;
        for (int i = 0; i < list.size(); i++) {
            Row row = list.get(i);
            float ry = mid - (listTop + 6f + i * 12f);
            float tx = left;
            if (hasIcon(row)) {
                item(sh, eye, ROW_ICON, i, left + 5f, ry, 10f, row.icon(), Z_ICON);
                tx = left + 13f;
            }
            float w = TalkChrome.legacyWidth(row.text());
            text(sh, eye, ROW, i, tx + w / 2f, ry, Component.text("", TalkChrome.TEXT).append(LEGACY.deserialize(row.text())),
                    1, 1f, Z_TEXT, true, TextDisplay.TextAlignment.LEFT, INVISIBLE_TEXT);
        }
        sh.rows = list.size();
        if (foot.length > 0) {
            Component body = Component.empty();
            for (int i = 0; i < foot.length; i++) {
                if (i > 0) {
                    body = body.append(Component.newline());
                }
                body = body.append(Component.text("", TalkChrome.MUTED).append(LEGACY.deserialize(foot[i])));
            }
            text(sh, eye, FOOTER, 0, 0f, mid - footCy, body, foot.length, FOOT_SCALE, Z_TEXT, true,
                    TextDisplay.TextAlignment.CENTER, INVISIBLE_TEXT);
            sh.footer = true;
        }
        // where everything starts before its turn
        for (Piece p : sh.pieces) {
            switch (p.role) {
                case RAY -> {
                    p.stretch = 0f;
                    if (p.display instanceof TextDisplay td) {
                        td.setBackgroundColor(Color.fromARGB(0, p.color.getRed(), p.color.getGreen(), p.color.getBlue()));
                    }
                }
                case HALO, TILE, TILE_ICON, ROW_ICON -> p.factor = 0f;
                case CREST, EMBLEM -> {
                    p.factor = 0f;
                    p.dy = 16f;
                }
                case TITLE -> p.dy = -3f;
                case ROW -> p.dx = -8f;
                default -> {
                }
            }
        }
    }

    /** Frame, plate, the bar along the bottom, the badge on the left edge, the lines. */
    private void buildToast(Shown sh, Location eye) {
        Card c = sh.card;
        TextColor accent = c.accent() == null ? TalkChrome.READY : c.accent();
        List<Component> lines = new ArrayList<>();
        String title = TalkText.strip(c.title() == null ? "" : c.title());
        lines.add(Component.text(title, TalkChrome.TEXT, TextDecoration.BOLD));
        int w = TalkChrome.width(title, true);
        for (Row row : c.rows()) {
            lines.add(Component.text("", TalkChrome.MUTED).append(LEGACY.deserialize(row.text())));
            w = Math.max(w, TalkChrome.legacyWidth(row.text()));
        }
        for (String foot : footerLines(c)) {
            String plain = TalkText.strip(foot);
            int cut = plain.indexOf(':');
            if (cut > 0 && cut <= 14) {
                lines.add(Component.text(plain.substring(0, cut + 1), TalkChrome.mix(accent, TalkChrome.MUTED, 0.35))
                        .append(Component.text(plain.substring(cut + 1), TalkChrome.MUTED)));
            } else {
                lines.add(Component.text(plain, TalkChrome.MUTED));
            }
            w = Math.max(w, TalkChrome.width(plain, false));
        }
        int n = lines.size();
        float ph = n * 10f + 7f;
        float pw = w + 34f;
        sh.halfW = pw / 2f + 9f; // the badge sticks out on the left
        sh.halfH = ph / 2f + 2f;
        float left = -pw / 2f;
        rect(sh, eye, FRAME, 0, 0f, 0f, pw + 2f, ph + 2f, 0f, 0f, argb(TalkChrome.mix(accent, TalkChrome.INK, 0.62), 255));
        rect(sh, eye, FRAME, 0, 0f, 0f, pw, ph, 0f, 0.0004f, TalkChrome.ink(232));
        sh.bar = rect(sh, eye, BAR, 0, left, -ph / 2f + 0.75f, pw, 1.5f, 0f, 0.0008f, argb(accent, 230));
        float bx = left + 3f;
        diamond(sh, eye, BADGE, 0, bx, 0f, 23f, 0.0012f, argb(TalkChrome.mix(accent, BLACK, 0.45), 255));
        diamond(sh, eye, BADGE, 0, bx, 0f, 19f, 0.0016f, argb(accent, 255));
        TextColor markInk = TalkChrome.mix(accent, BLACK, 0.82);
        Color mark = argb(markInk, 255);
        String glyph = toastGlyph(c);
        if ("✔".equals(glyph)) {
            stroke(sh, eye, bx - 3.6f, 0.2f, bx - 1.2f, -2.4f, 2.3f, mark);
            stroke(sh, eye, bx - 1.2f, -2.4f, bx + 3.8f, 3.0f, 2.3f, mark);
        } else if ("✦".equals(glyph)) {
            diamond(sh, eye, BADGE, 0, bx, 0f, 10f, 0.002f, mark);
            diamond(sh, eye, BADGE, 0, bx, 0f, 5f, 0.0024f, argb(TalkChrome.mix(accent, WHITE, 0.55), 255));
        } else {
            text(sh, eye, BADGE, 0, bx + 0.5f, 0.5f, Component.text(glyph, markInk, TextDecoration.BOLD), 1, 1f, 0.002f,
                    false, TextDisplay.TextAlignment.CENTER, (byte) -1);
        }
        Component body = Component.empty();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                body = body.append(Component.newline());
            }
            body = body.append(lines.get(i));
        }
        text(sh, eye, BODY, 0, left + 17f + w / 2f, 0f, body, n, 1f, 0.002f, true, TextDisplay.TextAlignment.LEFT,
                INVISIBLE_TEXT);
        for (Piece p : sh.pieces) {
            if (p.role == BADGE) {
                p.factor = 0f;
            }
        }
    }

    /** The title, with a glint over it at {@code at} (0..1 left to right; outside = none). */
    private Component titleText(Shown sh, double at) {
        String text = sh.titleText;
        int letters = text.replace("\n", "").length();
        double pos = at < 0 ? -100.0 : -2.0 + at * (letters + 4.0);
        Component out = Component.empty();
        int i = 0;
        for (int k = 0; k < text.length(); k++) {
            char ch = text.charAt(k);
            if (ch == '\n') {
                out = out.append(Component.newline());
                continue;
            }
            double glow = Math.max(0.0, 1.0 - Math.abs(i - pos) / 2.2);
            out = out.append(Component.text(String.valueOf(ch), TalkChrome.mix(sh.titleInk, WHITE, glow * 0.95),
                    TextDecoration.BOLD));
            i++;
        }
        return out;
    }

    private static Component amountText(String prefix, long value) {
        return LEGACY.deserialize(prefix + "§l+" + value);
    }

    private static void setAmount(Tile tile, long value) {
        if (tile.amount.display instanceof TextDisplay td && td.isValid()) {
            td.text(amountText(tile.prefix, value));
        }
    }

    /** The item in the crest: the card's own, else the first block among the row icons, else a nether star. */
    private static ItemStack emblem(Card c) {
        if (c.emblem() != null && !c.emblem().getType().isAir()) {
            return c.emblem();
        }
        ItemStack first = null;
        for (Row row : c.rows()) {
            if (!hasIcon(row)) {
                continue;
            }
            if (row.icon().getType().isBlock()) {
                return row.icon();
            }
            if (first == null) {
                first = row.icon();
            }
        }
        return first != null ? first : new ItemStack(Material.NETHER_STAR);
    }

    private static boolean hasIcon(Row row) {
        return row != null && row.icon() != null && !row.icon().getType().isAir();
    }

    /** A toast's mark: ✔ for something done, or the card's own (its {@code header}, e.g. ✦ for something new). */
    private static String toastGlyph(Card c) {
        return c.header() == null || c.header().isBlank() ? "✔" : TalkText.strip(c.header());
    }

    private static TextColor accent(Card c) {
        return c.accent() == null ? TalkChrome.QUEST : c.accent();
    }

    private static Color argb(TextColor c, int alpha) {
        return Color.fromARGB(Math.max(0, Math.min(255, alpha)), c.red(), c.green(), c.blue());
    }

    /* =====================================================================
     * Pieces
     * ===================================================================== */

    /** A plain shape: a " " text display whose background is the shape. */
    private Piece rect(Shown sh, Location eye, int role, int index, float cx, float cy, float w, float h, float deg,
                       float z, Color color) {
        TextDisplay td = spawnText(eye, d -> {
            TalkChrome.base(d);
            d.setShadowed(false);
            d.setLineWidth(400);
            d.setBackgroundColor(color);
            d.text(Component.text(" "));
        });
        if (td == null) {
            return null;
        }
        Piece p = new Piece(td, Form.RECT, role, index, cx, cy, w, h, deg, z, 10, color);
        sh.pieces.add(p);
        return p;
    }

    private Piece diamond(Shown sh, Location eye, int role, int index, float cx, float cy, float diag, float z,
                          Color color) {
        float side = diag / (float) Math.sqrt(2.0);
        return rect(sh, eye, role, index, cx, cy, side, side, 45f, z, color);
    }

    /** A bar shining out of the crest: from radius {@code r0}, {@code deg} clockwise from straight up. */
    private void ray(Shown sh, Location eye, int order, float crestY, float r0, float len, float thick, int deg,
                     Color color) {
        double a = Math.toRadians(deg);
        rect(sh, eye, RAY, order, (float) Math.sin(a) * r0, crestY + (float) Math.cos(a) * r0, thick, len, -deg, 0f,
                color);
    }

    /** A stroke from one point to another (the badge's check). */
    private void stroke(Shown sh, Location eye, float x1, float y1, float x2, float y2, float thick, Color color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        rect(sh, eye, BADGE, 0, (x1 + x2) / 2f, (y1 + y2) / 2f, thick, len + thick * 0.9f,
                (float) Math.toDegrees(Math.atan2(-dx, dy)), 0.002f, color);
    }

    /** Text centred on (cx, cy) (its glyphs, not its line spacing). */
    private Piece text(Shown sh, Location eye, int role, int index, float cx, float cy, Component text, int lines,
                       float scale, float z, boolean shadow, TextDisplay.TextAlignment align, byte opacity) {
        TextDisplay td = spawnText(eye, d -> {
            TalkChrome.base(d);
            d.setShadowed(shadow);
            d.setAlignment(align);
            d.setLineWidth(1000);
            d.setBackgroundColor(TalkChrome.none());
            d.setTextOpacity(opacity);
            d.text(text);
        });
        if (td == null) {
            return null;
        }
        Piece p = new Piece(td, Form.TEXT, role, index, cx, cy, scale, 0f, 0f, z, Math.max(1, lines) * 10, null);
        sh.pieces.add(p);
        return p;
    }

    private Piece item(Shown sh, Location eye, int role, int index, float cx, float cy, float size, ItemStack stack,
                       float z) {
        ItemStack icon = stack.clone();
        icon.setAmount(1);
        ItemDisplay id = spawn(eye, ItemDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        });
        if (id == null) {
            return null;
        }
        Piece p = new Piece(id, Form.ITEM, role, index, cx, cy, size, size, 0f, z, 0, null);
        sh.pieces.add(p);
        return p;
    }

    /* =====================================================================
     * Motion
     * ===================================================================== */

    /**
     * The piece's transformation now. A shape's background spans X -2..3, Y 0..11 text px about the display's
     * origin: scale it, turn it about the view axis, and shift it so its centre lands on the spot. Text is placed by
     * its glyph box (X centre 0.5 px, Y centre lines × 5 + 1 px). Items sit on their centre, a little nearer.
     */
    private Transformation at(Shown sh, Piece p) {
        float unit = PX * sh.scale;
        float g = sh.pop;
        float f = g * p.factor;
        float ay = (float) sh.centerY + sh.lift;
        float az = (float) -depth + p.z;
        Vector3f t;
        Quaternionf left;
        Vector3f s;
        if (p.form == Form.RECT) {
            float w = p.w;
            float h = p.h;
            float cx = p.cx + p.dx;
            float cy = p.cy + p.dy;
            double a = Math.toRadians(p.deg);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            if (p.role == RAY) {
                // grows out from its inner end
                h = p.h * p.stretch;
                cx += -sin * h / 2f;
                cy += cos * h / 2f;
            } else if (p.role == BAR) {
                // runs down toward its left end
                w = p.w * p.stretch;
                cx += w / 2f;
            }
            float sx = Math.max(1.0E-4f, w * unit * f / (5f * PX));
            float sy = Math.max(1.0E-4f, h * unit * f / (11f * PX));
            float lx = 0.5f * PX * sx;
            float ly = 5.5f * PX * sy;
            t = new Vector3f(cx * unit * g - (cos * lx - sin * ly), ay + cy * unit * g - (sin * lx + cos * ly), az);
            left = new Quaternionf().rotationZ((float) a);
            s = new Vector3f(sx, sy, 1f);
        } else if (p.form == Form.TEXT) {
            float sc = Math.max(1.0E-4f, p.w * unit * f / PX);
            t = new Vector3f((p.cx + p.dx) * unit * g - 0.5f * PX * sc,
                    ay + (p.cy + p.dy) * unit * g - (p.textH / 2f + 1f) * PX * sc, az);
            left = new Quaternionf();
            s = new Vector3f(sc, sc, sc);
        } else {
            // nearer than the card: place and size shrink by the same ratio, so it lands on its spot on screen
            float q = (float) ((depth - p.z) / depth);
            float sc = Math.max(1.0E-4f, p.w * unit * f * q);
            t = new Vector3f((p.cx + p.dx) * unit * g * q, (ay + (p.cy + p.dy) * unit * g) * q, az);
            left = new Quaternionf().rotationY(p.spin);
            s = new Vector3f(sc, sc, sc);
        }
        float k = sh.near;
        if (k < 0.999f) {
            t.mul(k);
            s.mul(k);
        }
        return new Transformation(t, left, s, new Quaternionf());
    }

    private void move(Shown sh, Piece p, int ticks) {
        if (p != null && p.display.isValid()) {
            TalkChrome.animate(p.display, at(sh, p), ticks);
        }
    }

    private void moveAll(Shown sh, int ticks) {
        for (Piece p : sh.pieces) {
            move(sh, p, ticks);
        }
    }

    private void applyNow(Shown sh, Piece p) {
        p.display.setInterpolationDelay(0);
        p.display.setInterpolationDuration(0);
        p.display.setTransformation(at(sh, p));
    }

    /** Text opacity, eased over {@code ticks} (the client blends it like the transformation). */
    private static void fade(Piece p, int opacity, int ticks) {
        if (p != null && p.display instanceof TextDisplay td && td.isValid()) {
            td.setInterpolationDelay(0);
            td.setInterpolationDuration(Math.max(0, ticks));
            td.setTextOpacity((byte) Math.max(0, Math.min(255, opacity)));
        }
    }

    /** A shape's background colour, eased over {@code ticks}. */
    private static void tint(Piece p, Color color, int ticks) {
        if (p != null && color != null && p.display instanceof TextDisplay td && td.isValid()) {
            td.setInterpolationDelay(0);
            td.setInterpolationDuration(Math.max(0, ticks));
            td.setBackgroundColor(color);
        }
    }

    private void close(Player player, Shown sh) {
        sh.closeAt = tick;
        sh.pop = COLLAPSED;
        sh.lift = sh.card.kind() == Kind.BIG ? 0.07f : 0.09f;
        moveAll(sh, 4);
        if (sh.card.kind() == Kind.BIG) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, SoundCategory.MASTER, 0.6f, 1.1f);
        }
    }

    /** The one chat line a big card leaves behind, its hover the whole card. */
    private void summary(Player player, Card c) {
        if (c.summary() == null || c.summary().isBlank()) {
            return;
        }
        Component hover = Component.text(TalkText.strip(c.header() == null ? "" : c.header()), accent(c));
        hover = hover.append(Component.newline())
                .append(Component.text(TalkText.strip(c.title() == null ? "" : c.title()), TalkChrome.TEXT, TextDecoration.BOLD));
        for (Row row : c.rows()) {
            hover = hover.append(Component.newline()).append(Component.text("", TalkChrome.TEXT).append(LEGACY.deserialize(row.text())));
        }
        if (c.footer() != null) {
            hover = hover.append(Component.newline()).append(Component.text("", TalkChrome.MUTED)
                    .append(LEGACY.deserialize(c.footer().replace("\n", " "))));
        }
        String more = de.aetherion.quests.lang.LangPack.ui(player, "talk_ux.card_details", "[Details]");
        player.sendMessage(Component.text("", NamedTextColor.GRAY).append(LEGACY.deserialize(c.summary()))
                .append(Component.text("  " + more, TalkChrome.FAINT))
                .hoverEvent(HoverEvent.showText(hover)));
    }

    /* =====================================================================
     * Events
     * ===================================================================== */

    /** Left-click sends a big card away (after it has been up for a moment). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (dismiss(event.getPlayer())) {
            clickUsed.put(event.getPlayer().getUniqueId(), tick);
        }
    }

    /** Stay mode: sneaking sends a big card away too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (bigStay && event.isSneaking()) {
            dismiss(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Shown sh = active.remove(id);
        if (sh != null) {
            kill(sh);
        }
        queues.remove(id);
        motion.remove(id);
        clickUsed.remove(id);
    }

    /** Another world: the pieces stay behind, so this card ends at once (its chat line still comes). */
    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Shown sh = active.remove(player.getUniqueId());
        motion.remove(player.getUniqueId());
        if (sh != null) {
            kill(sh);
            if (sh.card.kind() == Kind.BIG) {
                summary(player, sh.card);
            }
        }
    }

    /* =====================================================================
     * Entities
     * ===================================================================== */

    private void kill(Shown sh) {
        for (Piece p : sh.pieces) {
            killQuiet(p.display);
        }
    }

    private static void killQuiet(Entity e) {
        if (e != null && e.isValid()) {
            e.remove();
        }
    }

    private TextDisplay spawnText(Location at, java.util.function.Consumer<TextDisplay> setup) {
        return spawn(at, TextDisplay.class, setup);
    }

    private <T extends Entity> T spawn(Location at, Class<T> type, java.util.function.Consumer<T> setup) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            return world.spawn(at, type, e -> {
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.addScoreboardTag(TalkUx.TAG);
                e.setSilent(true);
                if (e instanceof Display display) {
                    display.setTeleportDuration(2);
                    display.setViewRange(0.25f);
                    display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                            new Vector3f(COLLAPSED, COLLAPSED, COLLAPSED), new AxisAngle4f()));
                }
                setup.accept(e);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Card spawn failed: " + ex.getMessage());
            return null;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
