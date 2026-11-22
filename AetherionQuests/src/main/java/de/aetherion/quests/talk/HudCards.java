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
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen cards: a big card for a quest or milestone that floats in front of the player's eyes, and a small toast
 * at the top for a step done.
 *
 * <p>How it stays on screen: every piece is a display entity standing at the player's eye with billboard CENTER.
 * A display's transformation is applied after the billboard turn, in camera space (local +Z faces the viewer), so
 * a translation of {@code (x, y, -depth)} keeps it {@code depth} blocks in front of the camera however the head
 * turns; the client does that every frame. Only the position comes from the server: each tick the pieces move to
 * the eye, a little ahead by the player's speed and ping, so walking barely drags the card. Nothing rides the
 * player (a player with passengers can't be teleported by plugins).
 *
 * <p>The card is drawn with depth like any other display, so a wall or an NPC closer than the card would cover it.
 * Every other tick the space in front is probed (blocks along the card's line of sight, and the NPC the player is
 * talking to); when something is nearer, the whole card moves closer and shrinks by the same factor, so on screen
 * it keeps its exact size and place.
 *
 * <p>Visible to the owner only, never saved, tagged like the talk UI so the janitors clean up after a crash.
 * Off by default ({@code hud-cards.enabled}). One card at a time per player, the rest wait their turn; a big card
 * holds new talk lines back until it is gone, and a left-click (after a moment) sends it away early.
 */
public final class HudCards implements Listener {

    public enum Kind {
        /** Small, at the top: a step done. */
        TOAST,
        /** Big, just above the middle: a quest done, a milestone. */
        BIG
    }

    /** One body row: legacy text and an optional item icon at its left. */
    public record Row(String text, ItemStack icon) {
        public static Row of(String text) {
            return new Row(text, null);
        }
    }

    /**
     * {@code header}: small caps over the title (big cards). {@code title}: the big line (big) or the bold first
     * line (toast). {@code rows}: body rows (big) or the detail line (toast). {@code footer}: a muted last line
     * such as "Next: …" (may be null). {@code summary}: legacy chat line written once a big card has gone (null =
     * none); its hover shows the whole card.
     */
    public record Card(Kind kind, String header, String title, TextColor accent, List<Row> rows, String footer,
                       String summary) {
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final float PX = TalkChrome.PX;
    private static final int LINE = TalkChrome.LINE_PX;
    private static final float COLLAPSED = 0.01f;
    private static final float RIM_DEPTH = 0.012f;
    private static final byte INVISIBLE_TEXT = 12;
    /** "+250 Coins": the number counts up when the row comes in. */
    private static final Pattern COUNT = Pattern.compile("^((?:§.)*)\\+(\\d{1,9})(.*)$");

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

    private static final class Motion {
        Vector last;
        Vector vel = new Vector();
    }

    private static final class Piece {
        final Display display;
        /** Rest translation relative to the card centre, in blocks at full size. */
        final float x;
        final float y;
        final float z;
        final float scale;
        float factor = COLLAPSED;
        float lift;

        Piece(Display display, float x, float y, float z, float scale) {
            this.display = display;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
        }
    }

    private static final class Shown {
        final Card card;
        final UUID owner;
        Piece body;
        Piece rim;
        Piece title;
        final List<Piece> icons = new ArrayList<>();
        final List<Integer> iconRow = new ArrayList<>();
        float scale;
        double centerY;
        int lines;
        int widthPx;
        int titleRows;
        int titleLines;
        int firstBodyRow;
        float rimSx = 1f;
        float rimSy = 1f;
        int revealed = -1;
        boolean footerShown;
        long born;
        long revealEnd;
        long endAt;
        long closeAt = -1;
        final Map<Integer, Long> countFrom = new HashMap<>();
        /** Pulled in front of something near (1 = the configured depth); size shrinks with it, so nothing moves on screen. */
        float near = 1f;
        long lastProbe = -10;

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
        bigScale = (float) clamp(cfg.getDouble("hud-cards.big-scale", 0.25), 0.08, 0.8);
        titleScale = (float) clamp(cfg.getDouble("hud-cards.title-scale", 0.42), 0.1, 1.2);
        toastScale = (float) clamp(cfg.getDouble("hud-cards.toast-scale", 0.24), 0.08, 0.8);
        bigHold = (int) clamp(cfg.getInt("hud-cards.big-hold-ticks", 80), 30, 400);
        toastHold = (int) clamp(cfg.getInt("hud-cards.toast-hold-ticks", 56), 20, 300);
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
        card = wrapped(card);
        UUID id = player.getUniqueId();
        queues.computeIfAbsent(id, k -> new ArrayDeque<>()).addLast(card);
        Shown now = active.get(id);
        if (now != null && card.kind() == Kind.BIG && now.card.kind() == Kind.TOAST && now.closeAt < 0) {
            // a toast never keeps a big card waiting
            now.endAt = Math.min(now.endAt, tick + 1);
        }
        return true;
    }

    /** Long lines wrap (a toast's rows and footer, a big card's footer), so no card grows wider than it reads. */
    private static Card wrapped(Card c) {
        boolean big = c.kind() == Kind.BIG;
        List<Row> rows = c.rows() == null ? List.of() : c.rows();
        if (!big) {
            List<Row> out = new ArrayList<>();
            for (Row row : rows) {
                if (row == null || row.text() == null) {
                    continue;
                }
                for (String part : TalkText.wrap(row.text(), 200).split("\n", -1)) {
                    out.add(Row.of(part));
                }
            }
            rows = out;
        } else {
            List<Row> out = new ArrayList<>();
            for (Row row : rows) {
                if (row != null && row.text() != null) {
                    out.add(row);
                }
            }
            rows = out;
        }
        String footer = c.footer() == null || c.footer().isBlank() ? null : TalkText.wrap(c.footer(), big ? 230 : 200);
        return new Card(c.kind(), c.header(), c.title(), c.accent(), rows, footer, c.summary());
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

    /** Move every piece to the eye, ahead by speed × (ping + a tick of interpolation). */
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
        for (Piece p : pieces(sh)) {
            if (p.display.isValid() && p.display.getWorld() == at.getWorld()) {
                p.display.teleport(at);
            }
        }
    }

    /**
     * Something nearer than the card along its line of sight (a wall, the NPC in front): bring the card in front of
     * it. Translation and scale shrink by the same factor, so it keeps its size and place on screen.
     */
    private void probe(Player player, Shown sh) {
        if (tick - sh.lastProbe < 2 || sh.closeAt >= 0) {
            return;
        }
        sh.lastProbe = tick;
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return;
        }
        Vector forward = eye.getDirection().normalize();
        Vector up = new Vector(0, 1, 0).subtract(forward.clone().multiply(forward.getY()));
        up = up.lengthSquared() < 1.0E-4 ? new Vector() : up.normalize();
        Vector line = forward.clone().multiply(depth).add(up.multiply(sh.centerY));
        double full = line.length();
        Vector dir = line.clone().normalize();
        double room = full;
        RayTraceResult wall = world.rayTraceBlocks(eye, dir, full + 0.35, FluidCollisionMode.NEVER, true);
        if (wall != null && wall.getHitBlock() != null) {
            room = Math.min(room, wall.getHitPosition().distance(eye.toVector()) - 0.3);
        }
        TalkUx talk = TalkUx.get();
        Location npc = talk == null ? null : talk.npcNear(player);
        if (npc != null && npc.getWorld() == world) {
            Vector to = npc.toVector().add(new Vector(0, 1.1, 0)).subtract(eye.toVector());
            double along = to.dot(forward);
            if (along > 0.0 && along < full + 0.6) {
                double off = to.clone().subtract(forward.clone().multiply(along)).length();
                if (off < 1.1) {
                    room = Math.min(room, along - 0.6);
                }
            }
        }
        float k = (float) clamp(room / full, 0.22, 1.0);
        if (Math.abs(k - sh.near) < 0.05 && !(k >= 0.999f && sh.near < 0.999f)) {
            return;
        }
        sh.near = k;
        for (Piece p : pieces(sh)) {
            if (p.display.isValid()) {
                TalkChrome.animate(p.display, at(sh, p), 3);
            }
        }
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
        Card c = sh.card;
        if (c.kind() == Kind.BIG) {
            if (t == 1) {
                pose(sh, sh.body, 1.08f, 0f, 4);
                pose(sh, sh.rim, 1.08f, 0f, 4);
            } else if (t == 3) {
                pose(sh, sh.title, 1.12f, 0f, 4);
            } else if (t == 5) {
                pose(sh, sh.body, 1f, 0f, 3);
                pose(sh, sh.rim, 1f, 0f, 3);
                glow(sh, true);
            } else if (t == 7) {
                pose(sh, sh.title, 1f, 0f, 3);
            } else if (t == 10) {
                glow(sh, false);
            }
            int rows = c.rows().size();
            for (int i = sh.revealed + 1; i < rows; i++) {
                long at = 10L + i * 5L;
                if (t < at) {
                    break;
                }
                sh.revealed = i;
                if (COUNT.matcher(c.rows().get(i).text()).matches()) {
                    sh.countFrom.put(i, tick);
                }
                popIcon(sh, i);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER,
                        0.35f, (float) Math.min(2.0, 1.05 + i * 0.12));
            }
            if (!sh.footerShown && c.footer() != null && t >= 12L + rows * 5L) {
                sh.footerShown = true;
            }
            if (t % 5 == 0 || !sh.countFrom.isEmpty() || t == 12L + rows * 5L) {
                render(sh, t);
            }
            if (!sh.countFrom.isEmpty()) {
                sh.countFrom.entrySet().removeIf(e -> tick - e.getValue() > 8);
            }
            spin(sh, t);
        } else {
            if (t == 1) {
                pose(sh, sh.body, 1.05f, 0f, 4);
                pose(sh, sh.rim, 1.05f, 0f, 4);
            } else if (t == 5) {
                pose(sh, sh.body, 1f, 0f, 3);
                pose(sh, sh.rim, 1f, 0f, 3);
            }
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
        // Box: rows and widest row in text pixels.
        int width = 0;
        int lines = 0;
        if (big) {
            String header = header(card);
            width = Math.max(width, TalkChrome.width(header, false) + 8);
            lines++;
            String title = card.title() == null ? "" : TalkText.strip(card.title());
            String wrapped = TalkText.wrap("§f§l" + title, 210);
            sh.titleLines = Math.max(1, wrapped.split("\n", -1).length);
            float ratio = titleScale / sh.scale;
            for (String l : wrapped.split("\n", -1)) {
                width = Math.max(width, Math.round((TalkChrome.legacyWidth(l) + 8) * ratio));
            }
            sh.titleRows = Math.max(1, (int) Math.ceil(sh.titleLines * ratio + 0.15));
            lines += sh.titleRows;
            lines++; // breath under the title
            sh.firstBodyRow = lines;
            for (Row row : card.rows()) {
                width = Math.max(width, rowWidth(row) + 8);
                lines++;
            }
            if (card.footer() != null) {
                lines++;
                for (String foot : footerLines(card)) {
                    lines++;
                    width = Math.max(width, TalkChrome.legacyWidth(foot) + 8);
                }
            }
        } else {
            width = Math.max(width, TalkChrome.legacyWidth("§l" + toastGlyph(card) + " " + TalkText.strip(card.title())) + 10);
            lines = 1;
            sh.firstBodyRow = 1;
            for (Row row : card.rows()) {
                width = Math.max(width, TalkChrome.legacyWidth(row.text()) + 10);
                lines++;
            }
            for (String foot : footerLines(card)) {
                width = Math.max(width, TalkChrome.legacyWidth(foot) + 10);
                lines++;
            }
        }
        sh.lines = Math.max(1, lines);
        sh.widthPx = Math.max(60, width);
        float w = sh.widthPx + 1f;
        float h = sh.lines * (float) LINE;
        sh.rimSx = (w + 2f * TalkChrome.RIM_PX) / w;
        sh.rimSy = (h + 2f * TalkChrome.RIM_PX) / h;

        Location eye = player.getEyeLocation();
        float d = (float) depth;
        TextColor accent = accent(card);
        TextDisplay body = spawnText(eye, td -> {
            TalkChrome.base(td);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setLineWidth(600);
            td.setBackgroundColor(TalkChrome.ink(big ? 226 : TalkChrome.CARD_ALPHA));
        });
        TextDisplay rim = spawnText(eye, td -> {
            TalkChrome.base(td);
            td.setShadowed(false);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setLineWidth(600);
            td.setTextOpacity(INVISIBLE_TEXT);
            td.setBackgroundColor(TalkChrome.rim(accent));
        });
        if (body == null || rim == null) {
            killQuiet(body);
            killQuiet(rim);
            return null;
        }
        sh.body = new Piece(body, 0f, 0f, -d, sh.scale);
        sh.rim = new Piece(rim, 0f, 0f, -d - RIM_DEPTH, sh.scale);
        if (big) {
            TextDisplay title = spawnText(eye, td -> {
                TalkChrome.base(td);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setLineWidth(600);
                td.setBackgroundColor(TalkChrome.none());
            });
            if (title != null) {
                // the title band: rows 1..titleRows from the top, the title centred in it
                float bandBottomPx = (sh.lines - 1 - sh.titleRows) * (float) LINE;
                float titleHeightPx = sh.titleLines * LINE * (titleScale / sh.scale);
                float bottomPx = bandBottomPx + (sh.titleRows * LINE - titleHeightPx) / 2f;
                float centerPx = bottomPx + titleHeightPx / 2f - sh.lines * LINE / 2f;
                sh.title = new Piece(title, 0f, centerPx * PX * sh.scale, -d + 0.004f, titleScale);
                title.text(titleText(card));
            }
            for (int i = 0; i < card.rows().size(); i++) {
                Row row = card.rows().get(i);
                if (row.icon() == null || row.icon().getType().isAir()) {
                    continue;
                }
                int r = sh.firstBodyRow + i;
                float rowCenterPx = (sh.lines - 1 - r) * (float) LINE + LINE / 2f - sh.lines * LINE / 2f;
                float leftPx = -rowWidth(row) / 2f + 7f;
                boolean block = row.icon().getType().isBlock();
                float size = LINE * PX * sh.scale * (block ? 1.55f : 1.2f);
                ItemStack icon = row.icon().clone();
                icon.setAmount(1);
                ItemDisplay item = spawn(eye, ItemDisplay.class, id -> {
                    id.setBillboard(Display.Billboard.CENTER);
                    id.setBrightness(new Display.Brightness(15, 15));
                    id.setItemStack(icon);
                    id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                });
                if (item != null) {
                    sh.icons.add(new Piece(item, leftPx * PX * sh.scale, rowCenterPx * PX * sh.scale, -d + 0.006f, size));
                    sh.iconRow.add(i);
                }
            }
        }
        float entry = big ? -0.06f : 0.10f;
        sh.body.lift = entry;
        sh.rim.lift = entry;
        render(sh, 0);
        for (Piece p : pieces(sh)) {
            applyNow(sh, p);
            player.showEntity(plugin, p.display);
        }
        sh.revealEnd = big ? 14L + card.rows().size() * 5L : 6L;
        // a toast with more to read stays a little longer
        int extra = big ? 0 : 14 * Math.max(0, card.rows().size() + footerLines(card).length - 1);
        sh.endAt = tick + sh.revealEnd + (big ? bigHold : toastHold + extra);
        if (big) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.MASTER, 0.9f, 1.0f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.75f, 1.05f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.55f, 1.55f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 0.6f, 1.3f);
        }
        return sh;
    }

    private void render(Shown sh, long t) {
        if (sh.body == null || !sh.body.display.isValid()) {
            return;
        }
        Component text = panelText(sh, t);
        ((TextDisplay) sh.body.display).text(text);
        ((TextDisplay) sh.rim.display).text(text);
    }

    private Component panelText(Shown sh, long t) {
        Card c = sh.card;
        TextColor accent = accent(c);
        TextColor ink = TalkChrome.inkText(accent);
        List<Component> rows = new ArrayList<>();
        if (c.kind() == Kind.BIG) {
            boolean twinkle = t > 0 && t < 48 && (t / 5) % 2 == 1;
            String star = twinkle ? "✧" : "✦";
            String header = TalkChrome.smallCaps(c.header() == null ? "" : TalkText.strip(c.header()));
            rows.add(Component.text(star + "  ", accent)
                    .append(Component.text(header, TalkChrome.mix(accent, TalkChrome.TEXT, 0.25)))
                    .append(Component.text("  " + star, accent)));
            for (int i = 0; i < sh.titleRows + 1; i++) {
                rows.add(Component.text(" "));
            }
            for (int i = 0; i < c.rows().size(); i++) {
                Row row = c.rows().get(i);
                String pad = row.icon() != null && !row.icon().getType().isAir() ? "    " : "";
                if (i > sh.revealed) {
                    rows.add(Component.text(pad + hidden(row.text()), ink));
                } else {
                    rows.add(Component.text(pad).append(counted(sh, i, row.text(), ink)));
                }
            }
            if (c.footer() != null) {
                rows.add(Component.text(" "));
                for (String foot : footerLines(c)) {
                    rows.add(sh.footerShown
                            ? Component.text("", TalkChrome.MUTED).append(LEGACY.deserialize(foot))
                            : Component.text(hidden(foot), ink));
                }
            }
        } else {
            rows.add(Component.text(toastGlyph(c) + " ", c.header() == null ? TalkChrome.READY : accent(c))
                    .append(Component.text(TalkText.strip(c.title() == null ? "" : c.title()), TalkChrome.TEXT,
                            TextDecoration.BOLD)));
            for (Row row : c.rows()) {
                rows.add(Component.text("", TalkChrome.MUTED).append(LEGACY.deserialize(row.text())));
            }
            for (String foot : footerLines(c)) {
                rows.add(Component.text("", TalkChrome.FAINT).append(LEGACY.deserialize(foot)));
            }
        }
        Component out = Component.empty();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                out = out.append(Component.newline());
            }
            out = out.append(Component.text(" ")).append(rows.get(i)).append(Component.text(" "));
        }
        return out;
    }

    /** "+250 Coins" counting up for a few ticks after its row came in; the box never changes width. */
    private Component counted(Shown sh, int index, String legacy, TextColor ink) {
        Long from = sh.countFrom.get(index);
        Matcher m = COUNT.matcher(legacy);
        if (from == null || !m.matches()) {
            return Component.text("", TalkChrome.TEXT).append(LEGACY.deserialize(legacy));
        }
        long target = Long.parseLong(m.group(2));
        double k = Math.min(1.0, (tick - from) / 6.0);
        double eased = 1.0 - Math.pow(1.0 - k, 3);
        long value = Math.round(target * eased);
        String digits = String.valueOf(value);
        String full = m.group(2);
        String pad = full.length() > digits.length() ? "0".repeat(full.length() - digits.length()) : "";
        return Component.text("", TalkChrome.TEXT)
                .append(LEGACY.deserialize(m.group(1) + "+"))
                .append(Component.text(pad, ink))
                .append(LEGACY.deserialize(m.group(1) + digits + m.group(3)));
    }

    private Component titleText(Card c) {
        String title = c.title() == null ? "" : TalkText.strip(c.title());
        String wrapped = TalkText.wrap(title, 210);
        return Component.text(wrapped, TalkChrome.mix(accent(c), TextColor.color(0xFFFFFF), 0.35), TextDecoration.BOLD);
    }

    /** A toast's mark: ✔ for something done, or the card's own (its {@code header}, e.g. ✦ for something new). */
    private static String toastGlyph(Card c) {
        return c.header() == null || c.header().isBlank() ? "✔" : TalkText.strip(c.header());
    }

    private static String header(Card c) {
        return "✦  " + TalkChrome.smallCaps(c.header() == null ? "" : TalkText.strip(c.header())) + "  ✦";
    }

    private static int rowWidth(Row row) {
        boolean icon = row.icon() != null && !row.icon().getType().isAir();
        return TalkChrome.legacyWidth(row.text()) + (icon ? 16 : 0);
    }

    /** Same glyphs (so the same box) with every colour taken out: drawn in the panel's own ink. */
    private static String hidden(String legacy) {
        return legacy == null ? "" : legacy.replaceAll("(?i)§[0-9a-fr]", "");
    }

    private static TextColor accent(Card c) {
        return c.accent() == null ? TalkChrome.QUEST : c.accent();
    }

    /* =====================================================================
     * Motion
     * ===================================================================== */

    private List<Piece> pieces(Shown sh) {
        List<Piece> out = new ArrayList<>(4 + sh.icons.size());
        if (sh.rim != null) {
            out.add(sh.rim);
        }
        if (sh.body != null) {
            out.add(sh.body);
        }
        if (sh.title != null) {
            out.add(sh.title);
        }
        out.addAll(sh.icons);
        return out;
    }

    /** The piece's transformation at its current factor / lift, pulled in by {@code near}. */
    private Transformation at(Shown sh, Piece p) {
        Transformation raw = rawAt(sh, p);
        float k = sh.near;
        if (k >= 0.999f) {
            return raw;
        }
        return new Transformation(new Vector3f(raw.getTranslation()).mul(k), new Quaternionf(raw.getLeftRotation()),
                new Vector3f(raw.getScale()).mul(k), new Quaternionf(raw.getRightRotation()));
    }

    private Transformation rawAt(Shown sh, Piece p) {
        float f = p.factor;
        float y0 = (float) sh.centerY + p.lift;
        if (p == sh.body || p == sh.rim) {
            float s = sh.scale * f;
            float bottom = y0 - sh.lines * LINE * PX * s / 2f;
            if (p == sh.rim) {
                return new Transformation(
                        new Vector3f(0f, bottom - TalkChrome.RIM_PX * PX * s, p.z),
                        new AxisAngle4f(),
                        new Vector3f(s * sh.rimSx, s * sh.rimSy, s),
                        new AxisAngle4f());
            }
            return new Transformation(new Vector3f(0f, bottom, p.z), new AxisAngle4f(),
                    new Vector3f(s, s, s), new AxisAngle4f());
        }
        // Title and icons sit at a spot of the panel: that spot scales with the panel's own factor.
        float panel = sh.body == null ? 1f : sh.body.factor;
        float cx = p.x * panel;
        float cy = y0 + p.y * panel;
        if (p == sh.title) {
            float s = p.scale * f;
            float bottom = cy - sh.titleLines * LINE * PX * s / 2f;
            return new Transformation(new Vector3f(cx, bottom, p.z), new AxisAngle4f(),
                    new Vector3f(s, s, s), new AxisAngle4f());
        }
        float s = p.scale * f;
        return new Transformation(new Vector3f(cx, cy, p.z), new AxisAngle4f(spinOf(p), 0f, 1f, 0f),
                new Vector3f(s, s, s), new AxisAngle4f());
    }

    private final Map<Piece, Float> spins = new HashMap<>();

    private float spinOf(Piece p) {
        Float a = spins.get(p);
        return a == null ? 0f : a;
    }

    private void pose(Shown sh, Piece p, float factor, float lift, int ticks) {
        if (sh == null || p == null) {
            return;
        }
        p.factor = factor;
        p.lift = lift;
        TalkChrome.animate(p.display, at(sh, p), ticks);
        if (p == sh.body) {
            // title and icons hang on the panel: keep them in step
            if (sh.title != null) {
                TalkChrome.animate(sh.title.display, at(sh, sh.title), ticks);
            }
            for (Piece icon : sh.icons) {
                TalkChrome.animate(icon.display, at(sh, icon), ticks);
            }
        }
    }

    private void applyNow(Shown sh, Piece p) {
        p.display.setInterpolationDelay(0);
        p.display.setInterpolationDuration(0);
        p.display.setTransformation(at(sh, p));
    }

    private void popIcon(Shown sh, int row) {
        for (int i = 0; i < sh.icons.size(); i++) {
            if (sh.iconRow.get(i) != row) {
                continue;
            }
            Piece icon = sh.icons.get(i);
            icon.factor = 1.22f;
            TalkChrome.animate(icon.display, at(sh, icon), 3);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (icon.display.isValid() && sh.closeAt < 0) {
                    icon.factor = 1f;
                    TalkChrome.animate(icon.display, at(sh, icon), 4);
                }
            }, 3L);
        }
    }

    /** Block icons turn slowly once they are in. */
    private void spin(Shown sh, long t) {
        if (t % 10 != 0) {
            return;
        }
        for (int i = 0; i < sh.icons.size(); i++) {
            Piece icon = sh.icons.get(i);
            if (sh.iconRow.get(i) > sh.revealed || !icon.display.isValid()
                    || !(icon.display instanceof ItemDisplay item) || item.getItemStack() == null
                    || !item.getItemStack().getType().isBlock()) {
                continue;
            }
            float a = spinOf(icon) + (float) (Math.PI / 3.0);
            if (a > Math.PI * 2) {
                a -= (float) (Math.PI * 2);
            }
            spins.put(icon, a);
            TalkChrome.animate(icon.display, at(sh, icon), 10);
        }
    }

    /** The rim flares up as the card lands, then settles. */
    private void glow(Shown sh, boolean on) {
        if (sh.rim == null || !sh.rim.display.isValid()) {
            return;
        }
        TextColor a = accent(sh.card);
        TextColor lit = TalkChrome.mix(a, TextColor.color(0xFFFFFF), 0.3);
        TextDisplay rim = (TextDisplay) sh.rim.display;
        rim.setInterpolationDelay(0);
        rim.setInterpolationDuration(on ? 2 : 12);
        rim.setBackgroundColor(on ? Color.fromARGB(235, lit.red(), lit.green(), lit.blue()) : TalkChrome.rim(a));
    }

    private void close(Player player, Shown sh) {
        sh.closeAt = tick;
        float lift = sh.card.kind() == Kind.BIG ? 0.07f : 0.09f;
        if (sh.body != null) {
            pose(sh, sh.body, COLLAPSED, lift, 4);
        }
        if (sh.rim != null) {
            pose(sh, sh.rim, COLLAPSED, lift, 4);
        }
        if (sh.title != null) {
            sh.title.factor = COLLAPSED;
            sh.title.lift = lift;
            TalkChrome.animate(sh.title.display, at(sh, sh.title), 4);
        }
        for (Piece icon : sh.icons) {
            icon.factor = COLLAPSED;
            icon.lift = lift;
            TalkChrome.animate(icon.display, at(sh, icon), 3);
        }
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
        for (Piece p : pieces(sh)) {
            killQuiet(p.display);
            spins.remove(p);
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
