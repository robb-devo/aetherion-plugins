package net.aetherion.samuraiboss.render.model;

import io.papermc.paper.entity.TeleportFlag;
import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.Xf;
import net.aetherion.samuraiboss.util.MathUtil;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Ein lebendes Modell in der Welt.
 *
 * <p>Aufbau: ein unsichtbarer <b>Anker</b> (ItemDisplay ohne Item) trägt alle Teile als
 * Passagiere. Bewegt sich das Modell, wird nur der Anker teleportiert (1 Paket); der Client
 * zieht die Teile weich mit. Die Pose (Arme, Beine, Katana …) steckt komplett in den
 * Transformationen der Teile und wird vom Client zwischen zwei Updates interpoliert.</p>
 *
 * <p>Pro Update werden nur Teile verschickt, die sich wirklich sichtbar verändert haben.</p>
 */
public final class ModelInstance {

    /** Momentaufnahme eines Teils in Weltkoordinaten (für Nachbilder, Trümmer, Klone). */
    public record PartState(CubeDef def, Location origin, Transformation transform,
                            BlockData block, ItemStack item) {
    }

    private static final class Part {
        final CubeDef def;
        Display entity;
        final Vector3f t = new Vector3f();
        final Quaternionf q = new Quaternionf();
        final Vector3f s = new Vector3f();
        boolean sent;
        boolean sentHidden;
        boolean detached;
        Display.Brightness brightness;
        boolean glowing;

        Part(CubeDef def) {
            this.def = def;
        }
    }

    public final ModelDef def;
    private final String arenaId;
    private final boolean packMode;
    private final int maxLod;
    private Skin skin;
    private ItemDisplay anchor;
    private Location location;
    private final List<Part> parts = new ArrayList<>();
    private final Xf[] boneWorld;
    private final Xf rootXf = new Xf();
    private float yaw;
    private float scale = 1f;
    private final Vector3f rootOffset = new Vector3f();
    private final Set<String> runtimeHidden = new HashSet<>();
    private Display.Brightness worldBrightness = new Display.Brightness(12, 15);
    private int flashTicks;
    private boolean removed;
    private boolean allHidden;
    private boolean dirtyAll = true;
    private float viewRange = 1.5f;
    private final Predicate<CubeDef> spawnFilter;

    public ModelInstance(ModelDef def, Skin skin, String arenaId, boolean packMode, int maxLod) {
        this(def, skin, arenaId, packMode, maxLod, null);
    }

    /**
     * @param spawnFilter nur Teile spawnen, für die der Filter true liefert (z. B. Schattenklone
     *                    ohne die Teile, die ihr Skin ohnehin versteckt) – null = alle
     */
    public ModelInstance(ModelDef def, Skin skin, String arenaId, boolean packMode, int maxLod,
                         Predicate<CubeDef> spawnFilter) {
        this.def = def;
        this.skin = skin;
        this.arenaId = arenaId;
        this.packMode = packMode;
        this.maxLod = maxLod;
        this.spawnFilter = spawnFilter;
        this.boneWorld = new Xf[def.boneCount()];
        for (int i = 0; i < boneWorld.length; i++) boneWorld[i] = new Xf();
    }

    // ------------------------------------------------------------------ Lebenszyklus

    public void spawn(Location loc) {
        this.location = loc.clone();
        this.location.setYaw(0f);
        this.location.setPitch(0f);
        anchor = DisplayFactory.anchor(location, "anchor", arenaId, 2);
        for (CubeDef c : def.cubes) {
            if (c.lod > maxLod) continue;
            if (c.variant == CubeDef.Variant.PACK && !packMode) continue;
            if (c.variant == CubeDef.Variant.VANILLA && packMode) continue;
            if (spawnFilter != null && !spawnFilter.test(c)) continue;
            Part p = new Part(c);
            p.entity = spawnPart(c);
            if (p.entity == null) continue;
            anchor.addPassenger(p.entity);
            parts.add(p);
        }
        dirtyAll = true;
    }

    private Display spawnPart(CubeDef c) {
        Display d;
        switch (c.kind) {
            case BLOCK -> d = DisplayFactory.block(location, skin.block(c.material), "model", arenaId);
            case ITEM -> {
                ItemStack st = skin.item(c.material);
                if (st == null) return null;
                d = DisplayFactory.item(location, st, ItemDisplay.ItemDisplayTransform.FIXED, "model", arenaId);
            }
            case TEXT -> {
                TextDisplay td = DisplayFactory.text(location, "model", arenaId);
                td.text(c.text);
                td.setShadowed(c.textShadow);
                d = td;
            }
            default -> throw new IllegalStateException();
        }
        d.setViewRange(viewRange);
        return d;
    }

    /** Entfernt alle Entities des Modells. */
    public void remove() {
        removed = true;
        for (Part p : parts) {
            if (p.entity != null && p.entity.isValid()) p.entity.remove();
        }
        parts.clear();
        if (anchor != null && anchor.isValid()) anchor.remove();
        anchor = null;
    }

    public boolean isRemoved() {
        return removed;
    }

    /** Prüft, ob Anker/Teile noch existieren (z. B. nach Chunk-Entladung) und baut sie notfalls neu auf. */
    public void ensureValid() {
        if (removed || location == null) return;
        boolean ok = anchor != null && anchor.isValid();
        if (ok) {
            for (Part p : parts) {
                if (p.entity == null || !p.entity.isValid()) {
                    ok = false;
                    break;
                }
            }
        }
        if (ok) return;
        Location l = location.clone();
        for (Part p : parts) if (p.entity != null && p.entity.isValid()) p.entity.remove();
        if (anchor != null && anchor.isValid()) anchor.remove();
        List<Part> old = new ArrayList<>(parts);
        parts.clear();
        anchor = DisplayFactory.anchor(l, "anchor", arenaId, 2);
        for (Part o : old) {
            Part p = new Part(o.def);
            p.detached = o.detached;
            p.entity = spawnPart(o.def);
            if (p.entity == null) continue;
            anchor.addPassenger(p.entity);
            parts.add(p);
        }
        dirtyAll = true;
    }

    // ------------------------------------------------------------------ Platzierung

    public Location location() {
        return location.clone();
    }

    /** Bewegt das ganze Modell (weich, über die Teleport-Interpolation des Ankers). */
    public void moveTo(Location loc) {
        if (anchor == null) return;
        Location l = loc.clone();
        l.setYaw(0f);
        l.setPitch(0f);
        if (location != null && location.getWorld() == l.getWorld() && location.distanceSquared(l) < 1e-6) return;
        location = l;
        anchor.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /** Setzt die Teleport-Interpolation des Ankers (0 = Sprung, 1-3 = weich). */
    public void setMoveSmoothing(int ticks) {
        if (anchor != null) anchor.setTeleportDuration(MathUtil.clamp(ticks, 0, 59));
    }

    public float yaw() {
        return yaw;
    }

    public void setYaw(float mcYaw) {
        if (Math.abs(MathUtil.wrapDeg(mcYaw - yaw)) > 1e-3f) {
            this.yaw = MathUtil.wrapDeg(mcYaw);
            dirtyAll = true;
        }
    }

    public float scale() {
        return scale;
    }

    public void setScale(float s) {
        if (Math.abs(s - scale) > 1e-4f) {
            scale = s;
            dirtyAll = true;
        }
    }

    /** Zusätzlicher Versatz des Modells relativ zum Anker (in Blöcken, Weltachsen), z. B. Schweben. */
    public void setRootOffset(float x, float y, float z) {
        if (rootOffset.distanceSquared(x, y, z) > 1e-8f) {
            rootOffset.set(x, y, z);
            dirtyAll = true;
        }
    }

    public void setViewRange(float range) {
        this.viewRange = range;
        for (Part p : parts) if (p.entity != null) p.entity.setViewRange(range);
    }

    // ------------------------------------------------------------------ Aussehen

    public Skin skin() {
        return skin;
    }

    /** Wechselt den Skin: Materialien, Sichtbarkeit, Leuchten. */
    public void setSkin(Skin newSkin) {
        this.skin = newSkin;
        for (Part p : parts) {
            if (p.entity == null || !p.entity.isValid()) continue;
            if (p.entity instanceof BlockDisplay bd) {
                BlockData want = newSkin.block(p.def.material);
                if (!bd.getBlock().equals(want)) bd.setBlock(want);
            } else if (p.entity instanceof ItemDisplay id && p.def.kind == CubeDef.Kind.ITEM) {
                ItemStack st = newSkin.item(p.def.material);
                if (st != null) id.setItemStack(st);
            }
        }
        dirtyAll = true;
    }

    /** Blendet alle Teile mit diesem Tag zur Laufzeit aus/ein (z. B. "katana_hand"). */
    public void setTagHidden(String tag, boolean hidden) {
        boolean changed = hidden ? runtimeHidden.add(tag) : runtimeHidden.remove(tag);
        if (changed) dirtyAll = true;
    }

    public boolean isTagHidden(String tag) {
        return runtimeHidden.contains(tag);
    }

    /** Licht der Umgebung (wird regelmäßig vom Boss gesetzt). */
    public void setWorldBrightness(int block, int sky) {
        Display.Brightness b = new Display.Brightness(MathUtil.clamp(block, 0, 15), MathUtil.clamp(sky, 0, 15));
        if (!b.equals(worldBrightness)) {
            worldBrightness = b;
            dirtyAll = true;
        }
    }

    /** Kurzes Aufhellen (Treffer-Feedback). */
    public void flash(int ticks) {
        if (flashTicks <= 0) dirtyAll = true;
        flashTicks = Math.max(flashTicks, ticks);
    }

    public void tick() {
        if (flashTicks > 0) {
            flashTicks--;
            if (flashTicks == 0) dirtyAll = true;
        }
    }

    /** Blendet das GANZE Modell aus/ein (z. B. Auflösen in Blütenblätter). */
    public void setAllHidden(boolean hidden) {
        if (hidden != allHidden) {
            allHidden = hidden;
            dirtyAll = true;
        }
    }

    public boolean isAllHidden() {
        return allHidden;
    }

    private boolean isHidden(Part p) {
        if (allHidden) return true;
        if (p.detached) return true;
        if (skin.hides(p.def)) return true;
        for (String t : p.def.tags) if (runtimeHidden.contains(t)) return true;
        return false;
    }

    // ------------------------------------------------------------------ Pose anwenden

    /** Berechnet Weltlagen aller Knochen (relativ zum Anker) für eine Pose. */
    private void solve(Pose pose) {
        rootXf.t.set(rootOffset);
        rootXf.q.set(MathUtil.yawRotation(yaw));
        rootXf.s = scale;
        ModelMath.solve(def, pose, rootXf, boneWorld);
    }

    /**
     * Wendet eine Pose an und schickt geänderte Teile an die Clients.
     *
     * @param interp Interpolationsdauer in Ticks (sollte dem Update-Abstand entsprechen)
     */
    public void update(Pose pose, int interp) {
        if (removed || anchor == null) return;
        solve(pose);
        boolean force = dirtyAll;
        dirtyAll = false;
        Vector3f nt = new Vector3f();
        Quaternionf nq = new Quaternionf();
        Vector3f ns = new Vector3f();
        for (Part p : parts) {
            Display e = p.entity;
            if (e == null || !e.isValid()) continue;
            boolean hidden = isHidden(p);
            if (hidden) {
                if (!p.sentHidden) {
                    DisplayFactory.snap(e, DisplayFactory.HIDDEN);
                    p.sentHidden = true;
                    p.sent = false;
                }
                continue;
            }
            ModelMath.cube(p.def, boneWorld[p.def.bone], nt, nq, ns);

            boolean changed = !p.sent
                    || nt.distanceSquared(p.t) > 0.0012f * 0.0012f
                    || Math.abs(Math.abs(nq.dot(p.q)) - 1f) > 2e-6f
                    || ns.distanceSquared(p.s) > 0.0008f * 0.0008f;
            if (changed) {
                Transformation tr = new Transformation(new Vector3f(nt), new Quaternionf(nq), new Vector3f(ns), new Quaternionf());
                if (!p.sent) DisplayFactory.snap(e, tr); // war versteckt/neu -> nicht aus dem Nichts heranlerpen
                else DisplayFactory.lerp(e, tr, interp);
                p.t.set(nt);
                p.q.set(nq);
                p.s.set(ns);
                p.sent = true;
                p.sentHidden = false;
            }
            // Helligkeit / Leuchten
            Display.Brightness want;
            if (p.def.emissive || flashTicks > 0) want = new Display.Brightness(15, 15);
            else if (skin.brightness != null) want = skin.brightness;
            else want = worldBrightness;
            if (!want.equals(p.brightness)) {
                e.setBrightness(want);
                p.brightness = want;
            }
            boolean glow = skin.glows(p.def);
            if (glow != p.glowing || force) {
                e.setGlowing(glow);
                if (glow) e.setGlowColorOverride(skin.glow);
                p.glowing = glow;
            }
        }
    }

    // ------------------------------------------------------------------ Abfragen

    /** Weltlage eines Knochens (relativ zum Anker) nach dem letzten update(). */
    public Xf boneXf(String bone) {
        return boneWorld[def.bone(bone)].copy();
    }

    /** Absolute Weltposition eines Punktes im lokalen Raum eines Knochens (Pixel). */
    public Location bonePoint(String bone, float px, float py, float pz) {
        Vector3f v = new Vector3f(px, py, pz).mul(ModelBuilder.PX);
        boneWorld[def.bone(bone)].apply(v, v);
        return location.clone().add(v.x, v.y, v.z);
    }

    /** Richtung einer lokalen Achse eines Knochens in Weltkoordinaten (normalisiert). */
    public Vector3f boneDir(String bone, float dx, float dy, float dz) {
        Vector3f v = new Vector3f(dx, dy, dz);
        boneWorld[def.bone(bone)].q.transform(v);
        return v.normalize();
    }

    /** Aktueller Weltzustand aller sichtbaren Teile, gefiltert. */
    public List<PartState> snapshot(Predicate<CubeDef> filter) {
        if (location == null) return Collections.emptyList();
        List<PartState> out = new ArrayList<>();
        for (Part p : parts) {
            if (!p.sent || isHidden(p)) continue;
            if (filter != null && !filter.test(p.def)) continue;
            Transformation tr = new Transformation(new Vector3f(p.t), new Quaternionf(p.q), new Vector3f(p.s), new Quaternionf());
            BlockData bd = p.entity instanceof BlockDisplay b ? b.getBlock() : null;
            ItemStack it = p.entity instanceof ItemDisplay i ? i.getItemStack() : null;
            out.add(new PartState(p.def, location.clone(), tr, bd, it));
        }
        return out;
    }

    /**
     * Löst ein Teil ab (z. B. ein Rüstungsteil, das abfällt): Das Teil wird im Modell versteckt,
     * zurückgegeben wird sein letzter Weltzustand, damit der Aufrufer ein Trümmerstück
     * an exakt derselben Stelle erzeugen kann.
     */
    public PartState detach(String cubeId) {
        for (Part p : parts) {
            if (!p.def.id.equals(cubeId)) continue;
            if (p.detached || !p.sent) {
                p.detached = true;
                dirtyAll = true;
                return null;
            }
            Transformation tr = new Transformation(new Vector3f(p.t), new Quaternionf(p.q), new Vector3f(p.s), new Quaternionf());
            BlockData bd = p.entity instanceof BlockDisplay b ? b.getBlock() : null;
            ItemStack it = p.entity instanceof ItemDisplay i ? i.getItemStack() : null;
            p.detached = true;
            dirtyAll = true;
            return new PartState(p.def, location.clone(), tr, bd, it);
        }
        return null;
    }

    /** Macht alle abgelösten Teile wieder sichtbar. */
    public void reattachAll() {
        for (Part p : parts) p.detached = false;
        dirtyAll = true;
    }

    public List<String> partIdsWithTag(String tag) {
        List<String> ids = new ArrayList<>();
        for (Part p : parts) if (p.def.hasTag(tag)) ids.add(p.def.id);
        return ids;
    }

    public int partCount() {
        return parts.size();
    }

    /** Alle Entities (Anker + Teile) – z. B. für Sichtbarkeits-Tricks. */
    public List<Display> entities() {
        List<Display> l = new ArrayList<>();
        if (anchor != null) l.add(anchor);
        for (Part p : parts) if (p.entity != null) l.add(p.entity);
        return l;
    }
}
