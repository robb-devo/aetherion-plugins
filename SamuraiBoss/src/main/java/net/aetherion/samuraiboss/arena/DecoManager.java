package net.aetherion.samuraiboss.arena;

import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.arena.dojo.DecoSpec;
import net.aetherion.samuraiboss.arena.dojo.El;
import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.util.Keys;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verwaltet die Deko-Displays eines Dojos (Blütenwolken, Hängezweige, Banner, Glocke, Koi …).
 *
 * <p>Alle Deko-Entities sind <b>nicht persistent</b>: Entlädt sich ein Chunk, verschwinden sie;
 * dieser Manager setzt sie automatisch wieder ein, sobald der Chunk geladen ist. So entstehen
 * nie Duplikate oder verwaiste Entities – auch nicht nach einem Absturz.</p>
 */
public final class DecoManager {

    /** Zustand eines Deko-Teils zum Zeitpunkt der Zerstörung (für Trümmer). */
    public record Piece(DecoSpec spec, Location origin, Transformation transform, Display display) {
    }

    private final Arena arena;
    private final Settings settings;
    private final Map<String, Entity> live = new HashMap<>();
    private final Set<El> hidden = new HashSet<>();
    private final Map<String, DecoSpec> specs = new HashMap<>();
    private int tick;
    private boolean playersNear;
    private int bellTick = -1;
    private boolean enabled = true;

    public DecoManager(Arena arena, Settings settings) {
        this.arena = arena;
        this.settings = settings;
        for (DecoSpec d : arena.plan.deco()) specs.put(d.id, d);
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
        if (!e) despawnAll();
    }

    // ------------------------------------------------------------------ Spawnen

    /** Setzt alle fehlenden Deko-Teile ein (nur in geladenen Chunks). */
    public void ensure() {
        if (!enabled || !arena.built) return;
        World w = arena.world();
        if (w == null) return;
        for (DecoSpec d : specs.values()) {
            if (hidden.contains(d.element)) continue;
            Entity e = live.get(d.id);
            if (e != null && e.isValid()) continue;
            Location l = arena.loc(d.x, d.y, d.z);
            if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            Entity spawned = spawn(d, l);
            if (spawned != null) live.put(d.id, spawned);
        }
    }

    private Entity spawn(DecoSpec d, Location l) {
        switch (d.kind) {
            case BLOCK -> {
                BlockDisplay bd = DisplayFactory.block(l, Bukkit.createBlockData(d.block), "deco", arena.id);
                apply(bd, d, transformOf(d));
                return bd;
            }
            case ITEM -> {
                ItemStack st;
                if (settings.packEnabled && d.packKey != null) st = PackItems.item(settings, d.packKey);
                else {
                    Material m = Material.matchMaterial(d.block);
                    st = new ItemStack(m == null ? Material.IRON_SWORD : m);
                }
                ItemDisplay id = DisplayFactory.item(l, st, ItemDisplay.ItemDisplayTransform.FIXED, "deco", arena.id);
                apply(id, d, transformOf(d));
                return id;
            }
            case TEXT -> {
                TextDisplay td = DisplayFactory.text(l, "deco", arena.id);
                td.text(MiniMessage.miniMessage().deserialize(d.text));
                td.setBackgroundColor(Color.fromARGB(d.textBgArgb));
                td.setShadowed(d.textShadow);
                if (d.centerBillboard) td.setBillboard(Display.Billboard.CENTER);
                apply(td, d, transformOf(d));
                return td;
            }
            case INTERACTION -> {
                Interaction it = l.getWorld().spawn(l, Interaction.class, i -> {
                    i.setPersistent(false);
                    Keys.mark(i, "deco", arena.id);
                    i.addScoreboardTag("samuraiboss_" + d.id);
                    i.setInteractionWidth(d.width);
                    i.setInteractionHeight(d.height);
                    i.setResponsive(true);
                });
                return it;
            }
        }
        return null;
    }

    private void apply(Display disp, DecoSpec d, Transformation t) {
        disp.setViewRange(d.viewRange);
        if (d.fullBright) disp.setBrightness(new Display.Brightness(15, 15));
        DisplayFactory.snap(disp, t);
    }

    private Transformation transformOf(DecoSpec d) {
        if (settings.packEnabled && d.packKey != null && d.packT != null) {
            return new Transformation(new Vector3f(d.packT), new Quaternionf(d.packQ), new Vector3f(d.packS), new Quaternionf());
        }
        return new Transformation(new Vector3f(d.t), new Quaternionf(d.q), new Vector3f(d.s), new Quaternionf());
    }

    public void despawnAll() {
        for (Entity e : live.values()) if (e != null && e.isValid()) e.remove();
        live.clear();
    }

    // ------------------------------------------------------------------ Zerstörung

    /** Blendet ein Bauteil aus und liefert seine Teile als Vorlage für Trümmer. */
    public List<Piece> take(El el) {
        List<Piece> out = new ArrayList<>();
        hidden.add(el);
        for (DecoSpec d : specs.values()) {
            if (d.element != el) continue;
            Entity e = live.remove(d.id);
            if (e instanceof Display disp && e.isValid()) {
                out.add(new Piece(d, e.getLocation(), disp.getTransformation(), disp));
                // Das Display bleibt bestehen – der Aufrufer animiert/entfernt es.
            } else if (e != null && e.isValid()) {
                e.remove();
            }
        }
        return out;
    }

    public void hide(El el) {
        for (Piece p : take(el)) if (p.display() != null && p.display().isValid()) p.display().remove();
    }

    public boolean isHidden(El el) {
        return hidden.contains(el);
    }

    /** Alles wieder sichtbar (nach dem Wiederaufbau). */
    public void showAll() {
        hidden.clear();
        ensure();
    }

    // ------------------------------------------------------------------ Animation

    public void tick() {
        tick++;
        if (!enabled) return;
        if (tick % 40 == 0) ensure();
        if (tick % 20 == 0) playersNear = anyPlayerNear(56);
        if (!playersNear) return;
        for (Map.Entry<String, Entity> e : live.entrySet()) {
            if (!(e.getValue() instanceof Display disp) || !disp.isValid()) continue;
            DecoSpec d = specs.get(e.getKey());
            if (d == null || d.anim == null) continue;
            switch (d.anim) {
                case "sway" -> {
                    if ((tick + (int) (d.animPhase * 7)) % 40 == 0) sway(disp, d);
                }
                case "strand" -> {
                    if ((tick + (int) (d.animPhase * 5)) % 20 == 0) strand(disp, d);
                }
                case "flag" -> {
                    if ((tick + (int) d.animPhase * 3) % 16 == 0) flag(disp, d);
                }
                case "koi" -> {
                    if (tick % 3 == 0) koi(disp, d);
                }
                case "bell", "striker" -> bell(disp, d);
                default -> {
                }
            }
        }
    }

    private boolean anyPlayerNear(double r) {
        World w = arena.world();
        if (w == null) return false;
        Location c = arena.center();
        double r2 = r * r;
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(c) < r2) return true;
        return false;
    }

    private float time() {
        return tick / 20f;
    }

    private void sway(Display disp, DecoSpec d) {
        float t = time();
        float ax = (float) Math.sin(t * 0.7 + d.animPhase) * 0.05f;
        float az = (float) Math.cos(t * 0.55 + d.animPhase * 1.3) * 0.05f;
        Quaternionf wob = new Quaternionf().rotateXYZ(ax, 0, az);
        Vector3f half = new Vector3f(d.s).mul(0.5f);
        Vector3f center = new Vector3f(d.t).add(new Quaternionf(d.q).transform(new Vector3f(half)));
        Quaternionf q = new Quaternionf(wob).mul(d.q);
        Vector3f t0 = new Vector3f(center).sub(q.transform(new Vector3f(half)));
        DisplayFactory.lerp(disp, new Transformation(t0, q, new Vector3f(d.s), new Quaternionf()), 40);
    }

    private void strand(Display disp, DecoSpec d) {
        float t = time();
        float wind = 0.09f + 0.05f * (float) Math.sin(t * 0.13);
        float a = (float) Math.sin(t * 1.3 + d.animPhase) * wind;
        float b = (float) Math.cos(t * 0.9 + d.animPhase * 0.7) * wind * 0.6f;
        Quaternionf r = new Quaternionf().rotateXYZ(a, 0, b);
        Vector3f t0 = r.transform(new Vector3f(d.t));
        DisplayFactory.lerp(disp, new Transformation(t0, new Quaternionf(r).mul(d.q), new Vector3f(d.s), new Quaternionf()), 20);
    }

    private void flag(Display disp, DecoSpec d) {
        float t = time();
        float ang = (float) Math.sin(t * 1.7 + d.animPhase) * 0.12f + (float) Math.sin(t * 3.1 + d.animPhase) * 0.04f;
        // Drehpunkt = Bambusstange (0.85 Blöcke zur Seite)
        float side = d.x < 0 ? 1f : -1f;
        Vector3f pivot = new Vector3f(-side * 0.85f, 0, 0);
        Quaternionf r = new Quaternionf().rotationY(ang);
        Vector3f t0 = new Vector3f(d.t).sub(pivot);
        r.transform(t0).add(pivot);
        DisplayFactory.lerp(disp, new Transformation(t0, new Quaternionf(r).mul(d.q), new Vector3f(d.s), new Quaternionf()), 16);
    }

    private void koi(Display disp, DecoSpec d) {
        float t = time();
        float th = d.animPhase + t * 0.32f * (d.animPhase > 3 ? -1 : 1);
        float rx = 3.6f + (d.animPhase % 2) * 0.8f, rz = 3.0f;
        Vector3f pos = new Vector3f((float) Math.cos(th) * rx, 0, (float) Math.sin(th) * rz);
        // Blickrichtung = Tangente
        float dx = (float) -Math.sin(th) * rx, dz = (float) Math.cos(th) * rz;
        if (d.animPhase > 3) {
            dx = -dx;
            dz = -dz;
        }
        float yaw = (float) Math.atan2(dx, dz);
        Quaternionf r = new Quaternionf().rotationY(yaw);
        if (d.id.endsWith("_tail")) r.rotateY((float) Math.sin(t * 9 + d.animPhase) * 0.35f);
        Vector3f half = new Vector3f(d.s).mul(0.5f);
        Vector3f localCenter = new Vector3f(d.t).add(half); // Spezifikation ohne Drehung
        Vector3f worldCenter = new Vector3f(pos).add(new Quaternionf().rotationY(yaw).transform(localCenter));
        Vector3f t0 = new Vector3f(worldCenter).sub(r.transform(new Vector3f(half)));
        DisplayFactory.lerp(disp, new Transformation(t0, r, new Vector3f(d.s), new Quaternionf()), 3);
    }

    /** Glocke anschlagen (Animation des Klöppels + Nachschwingen der Glocke). */
    public void strikeBell() {
        bellTick = 0;
    }

    private void bell(Display disp, DecoSpec d) {
        if (bellTick < 0) return;
        if (d.anim.equals("striker")) {
            // Drehpunkt: Seilaufhängung 2.15 Blöcke über dem Klöppel
            Vector3f pivot = new Vector3f(0, 2.15f, 0);
            float ang;
            int dur;
            if (bellTick == 0) {
                ang = 0.55f;
                dur = 8;
            } else if (bellTick == 8) {
                ang = -0.16f;
                dur = 3;
            } else if (bellTick == 11) {
                ang = 0.06f;
                dur = 10;
            } else if (bellTick == 21) {
                ang = 0f;
                dur = 12;
            } else return;
            Quaternionf r = new Quaternionf().rotationZ(ang);
            Vector3f t0 = new Vector3f(d.t).sub(pivot);
            r.transform(t0).add(pivot);
            DisplayFactory.lerp(disp, new Transformation(t0, new Quaternionf(r).mul(d.q), new Vector3f(d.s), new Quaternionf()), dur);
        } else {
            Vector3f pivot = new Vector3f(0, 3.3f, 0);
            float ang;
            int dur;
            if (bellTick == 11) {
                ang = 0.07f;
                dur = 5;
            } else if (bellTick == 16) {
                ang = -0.045f;
                dur = 8;
            } else if (bellTick == 24) {
                ang = 0.02f;
                dur = 10;
            } else if (bellTick == 34) {
                ang = 0f;
                dur = 12;
            } else return;
            Quaternionf r = new Quaternionf().rotationZ(ang);
            Vector3f c = new Vector3f(d.t).sub(pivot);
            r.transform(c).add(pivot);
            DisplayFactory.lerp(disp, new Transformation(c, new Quaternionf(r).mul(d.q), new Vector3f(d.s), new Quaternionf()), dur);
        }
    }

    /** Am Ende jedes Ticks aufrufen (zählt die Glocken-Animation weiter). */
    public void postTick() {
        if (bellTick >= 0) {
            bellTick++;
            if (bellTick > 50) bellTick = -1;
        }
    }

    public boolean isBellEntity(Entity e) {
        return e.getScoreboardTags().contains("samuraiboss_bell_touch") && arena.id.equals(Keys.arena(e));
    }

    public int liveCount() {
        int n = 0;
        for (Entity e : live.values()) if (e != null && e.isValid()) n++;
        return n;
    }
}
