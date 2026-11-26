package de.aetherion.pit.hub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Floating elven scrolls instead of plain text boxes (2026-11-26). A scroll is two wooden rollers, a sheet of
 * parchment (item displays with the hub models) and the text in front of it, one copy per language. It hangs rolled
 * up and unrolls when somebody comes close, sways a little, and rolls up again when nobody is around.
 * Round 4: the anchor is the middle of the sheet, at eye height, and every scroll uses the light parchment.
 */
public final class HubScrolls implements Listener {

    public static final String TAG = "ae_hub_scroll";

    public enum Style { LIGHT, DARK }

    /** Title plus body lines. Plain colours only; the body may use other fonts (runes). */
    public record Text(Component title, List<Component> lines) {
    }

    private static final float TITLE_SCALE = 0.9f;
    private static final float BODY_SCALE = 0.6f;
    private static final float LINE = 0.25f;          // one text line at scale 1, in blocks
    private static final float PX = 0.025f;           // one font pixel at scale 1
    private static final float PAD_X = 0.24f;
    private static final float PAD_TOP = 0.11f;
    private static final float GAP = 0.05f;
    private static final float PAD_BOTTOM = 0.13f;
    private static final float ROLL = 0.07f;          // roller radius
    private static final int OPEN_TICKS = 14;
    private static final int CLOSE_TICKS = 22;
    private static final double OPEN_RADIUS = 12;
    private static final double CLOSE_RADIUS = 16;

    private final Plugin plugin;
    private final Map<String, Scroll> scrolls = new LinkedHashMap<>();
    private long tick;

    private final class Scroll {
        final String id;
        final Location at;
        final Style style;
        Text en;
        Text de;
        float width;
        float height;
        float titleH;
        float bodyH;
        float bodyScale = BODY_SCALE;
        ItemDisplay top;
        ItemDisplay paper;
        ItemDisplay bottom;
        TextDisplay titleEn;
        TextDisplay bodyEn;
        TextDisplay titleDe;
        TextDisplay bodyDe;
        boolean open;
        long busyUntil;
        int swaySign = 1;

        Scroll(String id, Location at, Style style, Text en, Text de) {
            this.id = id;
            this.at = at.clone();
            this.style = style;
            this.en = en;
            this.de = de;
            layout();
        }

        void layout() {
            int titlePx = Math.max(width(en.title(), true), width(de.title(), true));
            int bodyPx = 0;
            int lines = Math.max(en.lines().size(), de.lines().size());
            for (Component c : en.lines()) {
                bodyPx = Math.max(bodyPx, width(c, false));
            }
            for (Component c : de.lines()) {
                bodyPx = Math.max(bodyPx, width(c, false));
            }
            // the width table is an estimate: 8 % more and a little extra keeps every line inside the paper
            float tw = titlePx * PX * TITLE_SCALE * 1.08f;
            float bw = bodyPx * PX * bodyScale * 1.08f;
            width = Math.max(1.5f, Math.max(tw, bw) + 2 * PAD_X + 0.08f);
            titleH = LINE * TITLE_SCALE;
            bodyH = lines * LINE * bodyScale;
            height = PAD_TOP + titleH + (lines > 0 ? GAP + bodyH : 0) + PAD_BOTTOM;
        }

        boolean alive() {
            return valid(top) && valid(paper) && valid(bottom) && valid(titleEn) && valid(bodyEn)
                    && valid(titleDe) && valid(bodyDe);
        }

        void remove() {
            for (Entity e : new Entity[]{top, paper, bottom, titleEn, bodyEn, titleDe, bodyDe}) {
                if (e != null && e.isValid()) {
                    e.remove();
                }
            }
            top = paper = bottom = null;
            titleEn = bodyEn = titleDe = bodyDe = null;
        }

        void spawn() {
            remove();
            World w = at.getWorld();
            int paperModel = style == Style.DARK ? HubModels.SCROLL_PAPER_DARK : HubModels.SCROLL_PAPER;
            top = item(w, HubModels.SCROLL_ROLLER);
            paper = item(w, paperModel);
            bottom = item(w, HubModels.SCROLL_ROLLER);
            titleEn = text(w, en.title(), false);
            bodyEn = text(w, joinLines(en.lines()), false);
            titleDe = text(w, de.title(), true);
            bodyDe = text(w, joinLines(de.lines()), true);
            open = false;
            apply(0.02f, 0, 0f);
        }

        /**
         * k = how far unrolled (0..1), sway in degrees around the anchor. The anchor is the middle of the sheet (eye
         * height on the hub): unrolling pushes both rollers apart from there.
         */
        void apply(float k, int duration, float swayDeg) {
            Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(swayDeg));
            float rs = 1.25f;
            float h = Math.max(0.02f, height * k);
            float half = h / 2f;
            set(top, new Vector3f(0, half + ROLL, 0), q, new Vector3f(width, rs, rs), duration);
            set(paper, new Vector3f(0, 0, 0), q, new Vector3f(width, h, 1), duration);
            set(bottom, new Vector3f(0, -half - ROLL, 0), q, new Vector3f(width, rs, rs), duration);
            float titleBottom = half - (PAD_TOP + titleH) * k;
            float bodyBottom = titleBottom - (GAP + bodyH) * k;
            float tk = Math.max(0.001f, k);
            Vector3f ts = new Vector3f(TITLE_SCALE, TITLE_SCALE * tk, TITLE_SCALE);
            Vector3f bs = new Vector3f(bodyScale, bodyScale * tk, bodyScale);
            set(titleEn, new Vector3f(0, titleBottom, 0.035f), q, ts, duration);
            set(titleDe, new Vector3f(0, titleBottom, 0.035f), q, ts, duration);
            set(bodyEn, new Vector3f(0, bodyBottom, 0.035f), q, bs, duration);
            set(bodyDe, new Vector3f(0, bodyBottom, 0.035f), q, bs, duration);
        }

        private void set(Display d, Vector3f t, Quaternionf q, Vector3f s, int duration) {
            if (d == null || !d.isValid()) {
                return;
            }
            Vector3f rt = new Vector3f(t).rotate(q);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(duration);
            d.setTransformation(new Transformation(rt, new Quaternionf(q), s, new Quaternionf()));
        }

        private ItemDisplay item(World w, int code) {
            return w.spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
                d.setItemStack(HubModels.item(code));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(1.0f);
            });
        }

        private TextDisplay text(World w, Component c, boolean germanCopy) {
            TextDisplay display = w.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.addScoreboardTag(TAG);
                d.text(c);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setLineWidth(1000);
                d.setShadowed(style == Style.DARK);
                d.setSeeThrough(false);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(0.6f);
            });
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (HubHolograms.german(player) == germanCopy) {
                    player.showEntity(plugin, display);
                }
            }
            return display;
        }
    }

    public HubScrolls(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }

    public void put(String id, Location at, Style style, Text en, Text de) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        Scroll old = scrolls.remove(id);
        if (old != null) {
            old.remove();
        }
        scrolls.put(id, new Scroll(id, at, style, en, de));
    }

    public boolean has(String id) {
        return scrolls.containsKey(id);
    }

    public void text(String id, Text en, Text de) {
        Scroll s = scrolls.get(id);
        if (s == null || (s.en.equals(en) && s.de.equals(de))) {
            return;
        }
        s.en = en;
        s.de = de;
        s.layout();
        if (s.alive()) {
            s.titleEn.text(en.title());
            s.bodyEn.text(joinLines(en.lines()));
            s.titleDe.text(de.title());
            s.bodyDe.text(joinLines(de.lines()));
            s.apply(s.open ? 1f : 0.02f, 6, 0f);
        }
    }

    public void removeAll() {
        scrolls.values().forEach(Scroll::remove);
        scrolls.clear();
    }

    private void tick() {
        tick += 5;
        for (Scroll s : scrolls.values()) {
            World w = s.at.getWorld();
            if (w == null || !w.isChunkLoaded(s.at.getBlockX() >> 4, s.at.getBlockZ() >> 4)) {
                continue;
            }
            if (!s.alive()) {
                s.spawn();
            }
            double near = nearest(s.at);
            if (!s.open && near < OPEN_RADIUS * OPEN_RADIUS) {
                s.open = true;
                s.busyUntil = tick + OPEN_TICKS + 2;
                s.apply(1f, OPEN_TICKS, 0f);
                w.playSound(s.at, Sound.ITEM_BOOK_PAGE_TURN, 0.55f, 0.85f);
                w.spawnParticle(Particle.WAX_OFF, s.at, 6,
                        s.width / 3, s.height / 3, 0.05, 0.6);
            } else if (s.open && near > CLOSE_RADIUS * CLOSE_RADIUS) {
                s.open = false;
                s.busyUntil = tick + CLOSE_TICKS + 2;
                s.apply(0.02f, CLOSE_TICKS, 0f);
            } else if (tick >= s.busyUntil && tick % 60 == 0) {
                s.swaySign = -s.swaySign;
                s.apply(s.open ? 1f : 0.02f, 60, 1.6f * s.swaySign);
            }
        }
    }

    private static double nearest(Location at) {
        double best = Double.MAX_VALUE;
        for (Player p : at.getWorld().getPlayers()) {
            best = Math.min(best, p.getLocation().distanceSquared(at));
        }
        return best;
    }

    private void refresh(Player player) {
        if (!player.isOnline()) {
            return;
        }
        boolean de = HubHolograms.german(player);
        for (Scroll s : scrolls.values()) {
            show(player, s.titleEn, !de);
            show(player, s.bodyEn, !de);
            show(player, s.titleDe, de);
            show(player, s.bodyDe, de);
        }
    }

    private void show(Player player, Entity e, boolean visible) {
        if (!valid(e)) {
            return;
        }
        if (visible) {
            player.showEntity(plugin, e);
        } else {
            player.hideEntity(plugin, e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(player), 5L);
    }

    @EventHandler
    public void onLocale(PlayerLocaleChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> refresh(player));
    }

    // ------------------------------------------------------------------ text metrics

    private static boolean valid(Entity e) {
        return e != null && e.isValid();
    }

    static Component joinLines(List<Component> lines) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out = out.append(Component.newline());
            }
            out = out.append(lines.get(i));
        }
        return out;
    }

    static int width(Component c, boolean bold) {
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        int best = 0;
        for (String line : plain.split("\n")) {
            best = Math.max(best, width(line, bold));
        }
        return best;
    }

    static int width(String s, boolean bold) {
        int w = 0;
        for (char ch : s.toCharArray()) {
            int cw;
            if (ch == 'i' || ch == '!' || ch == '.' || ch == ',' || ch == ':' || ch == ';' || ch == '|' || ch == '\'') {
                cw = 2;
            } else if (ch == 'l' || ch == '`') {
                cw = 3;
            } else if (ch == 'I' || ch == 't' || ch == '[' || ch == ']' || ch == ' ') {
                cw = 4;
            } else if (ch == 'f' || ch == 'k' || ch == '<' || ch == '>' || ch == '*') {
                cw = 5;
            } else if (ch > 0x2000) {
                cw = 9;
            } else {
                cw = 6;
            }
            w += cw + (bold ? 1 : 0);
        }
        return w;
    }

    /** All scroll ids, for /dev. */
    public List<String> ids() {
        return new ArrayList<>(scrolls.keySet());
    }
}
