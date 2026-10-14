package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Die ständig durch den Hof wehenden Kirschblüten – echte Display-Entities, keine Partikel.
 * Dadurch können sie mitten in der Luft <b>einfrieren</b> (Zeitstillstand), als Sturm um den
 * Boss kreisen, alle gleichzeitig fallen oder in den Himmel steigen.
 */
public final class PetalField extends BaseFx {

    public enum Mode {
        /** sanft mit dem Wind fallen */
        DRIFT,
        /** in der Luft erstarrt */
        FROZEN,
        /** wilder Wirbel um einen Mittelpunkt */
        STORM,
        /** langsam aufsteigen (Phase 3) */
        RISE,
        /** alle fallen schnell zu Boden und bleiben liegen */
        SETTLE
    }

    private final class P {
        Display d;
        final Vector3f from = new Vector3f(), to = new Vector3f();
        final Quaternionf qFrom = new Quaternionf(), qTo = new Quaternionf();
        int start, dur = 1;
        float size;
        int variant;
        int restUntil = -1;
        boolean needSpawn;
        float orbit; // Winkel im Sturm
        float orbitR;
        float orbitY;
    }

    private final FxContext c;
    private final List<P> petals = new ArrayList<>();
    private Mode mode = Mode.DRIFT;
    private final Vector3f stormCenter = new Vector3f();
    private float stormRadius = 7f;
    private boolean stopped;

    // Bereich relativ zum Anker
    private final float minX, maxX, minZ, maxZ, topY;
    private final float treeX, treeZ;

    public PetalField(FxContext c, Location anchor, int count, float minX, float maxX, float minZ, float maxZ,
                      float topY, float treeX, float treeZ) {
        super(c.fx);
        this.c = c;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.topY = topY;
        this.treeX = treeX;
        this.treeZ = treeZ;
        int n = c.count(count);
        for (int i = 0; i < n; i++) {
            P p = new P();
            p.variant = i;
            p.size = 0.12f + Rand.f() * 0.09f;
            p.d = c.petal(this, anchor, i);
            if (p.d == null) break;
            spawnPoint(p.to, true);
            p.from.set(p.to);
            p.qTo.set(FxContext.randomRot());
            p.qFrom.set(p.qTo);
            DisplayFactory.snap(p.d, c.petalTf(p.to, p.qTo, p.size));
            p.start = Rand.range(0, 30);
            p.dur = 1;
            petals.add(p);
        }
    }

    private void spawnPoint(Vector3f out, boolean anywhereHeight) {
        if (Rand.chance(0.55)) {
            // unter der Krone des Baumes
            float a = Rand.f() * MathUtil.TAU, r = Rand.f() * 8f;
            out.set(treeX + (float) Math.cos(a) * r, anywhereHeight ? Rand.range(1f, 9f) : Rand.range(7f, 9.5f), treeZ + (float) Math.sin(a) * r);
        } else {
            out.set(Rand.range(minX, maxX), anywhereHeight ? Rand.range(1f, topY) : Rand.range(topY - 3f, topY), Rand.range(minZ, maxZ));
        }
    }

    public Mode mode() {
        return mode;
    }

    // ------------------------------------------------------------------ Steuerung

    /** Alle Blätter erstarren dort, wo sie gerade (für die Spieler sichtbar) sind. */
    public void freeze() {
        for (P p : petals) {
            Vector3f pos = current(p);
            Quaternionf rot = currentRot(p);
            p.from.set(pos);
            p.to.set(pos);
            p.qFrom.set(rot);
            p.qTo.set(rot);
            if (p.d != null && p.d.isValid()) DisplayFactory.snap(p.d, c.petalTf(pos, rot, p.size));
        }
        mode = Mode.FROZEN;
    }

    /** Aus der Erstarrung: alle Blätter werden von einem Punkt weggesprengt, danach neuer Modus. */
    public void burst(Vector3f centerRel, float force, Mode next) {
        for (P p : petals) {
            Vector3f dir = new Vector3f(p.to).sub(centerRel);
            if (dir.lengthSquared() < 0.01f) dir.set(Rand.unit());
            dir.normalize();
            Vector3f target = new Vector3f(p.to).add(dir.mul(force * (0.6f + Rand.f() * 0.8f))).add(0, Rand.f() * force * 0.3f, 0);
            clampArea(target);
            move(p, target, FxContext.randomRot(), 8 + Rand.range(0, 6));
        }
        mode = next;
    }

    public void setMode(Mode m) {
        this.mode = m;
        if (m == Mode.SETTLE) {
            for (P p : petals) {
                Vector3f pos = current(p);
                Vector3f ground = new Vector3f(pos.x + Rand.signed() * 0.6f, 0.04f, pos.z + Rand.signed() * 0.6f);
                // flach liegend
                Quaternionf flat = new Quaternionf().rotationY(Rand.f() * 6.28f);
                move(p, ground, flat, Math.max(6, (int) (pos.y * 2.5f)) + Rand.range(0, 8));
                p.restUntil = Integer.MAX_VALUE;
            }
        } else {
            for (P p : petals) p.restUntil = -1;
        }
    }

    public void storm(Vector3f centerRel, float radius) {
        stormCenter.set(centerRel);
        stormRadius = radius;
        for (P p : petals) {
            Vector3f pos = current(p);
            p.orbit = (float) Math.atan2(pos.z - centerRel.z, pos.x - centerRel.x);
            p.orbitR = radius * (0.35f + Rand.f() * 0.9f);
            p.orbitY = 0.4f + Rand.f() * 5f;
        }
        mode = Mode.STORM;
    }

    public void moveStormCenter(Vector3f centerRel) {
        stormCenter.set(centerRel);
    }

    /** Liefert die Blätter (z. B. für den Mond-Ring in Phase 3). */
    public int size() {
        return petals.size();
    }

    // ------------------------------------------------------------------ Ticks

    @Override
    protected boolean update(int age) {
        if (stopped) return false;
        for (P p : petals) {
            if (p.d == null || !p.d.isValid()) continue;
            if (mode == Mode.FROZEN) continue;
            if (p.needSpawn) {
                // Respawn: zuerst unsichtbar an neue Stelle, im nächsten Tick einblenden
                p.needSpawn = false;
                Vector3f top = new Vector3f();
                if (mode == Mode.RISE) top.set(Rand.range(minX, maxX), 0.1f, Rand.range(minZ, maxZ));
                else spawnPoint(top, false);
                p.from.set(top);
                p.to.set(top);
                DisplayFactory.snap(p.d, c.petalTf(top, p.qTo, 0f));
                p.start = age;
                p.dur = 1;
                continue;
            }
            if (age < p.start + p.dur) continue;
            if (p.restUntil >= 0 && age < p.restUntil) continue;
            if (p.restUntil >= 0 && mode == Mode.DRIFT) {
                // hat am Boden gelegen -> neu oben erscheinen
                p.restUntil = -1;
                p.needSpawn = true;
                continue;
            }
            next(p, age);
        }
        return true;
    }

    private void next(P p, int age) {
        Vector3f pos = new Vector3f(p.to);
        switch (mode) {
            case DRIFT -> {
                float t = age / 20f;
                float windX = 0.55f + 0.35f * Rand.noise(t * 0.05f, 3);
                float windZ = 0.25f * Rand.noise(t * 0.07f, 7);
                int dur = 14 + Rand.range(0, 8);
                float dt = dur / 20f;
                Vector3f next = new Vector3f(pos).add(windX * dt + Rand.signed() * 0.35f, -(0.32f + Rand.f() * 0.22f) * dt * 2f,
                        windZ * dt + Rand.signed() * 0.35f);
                if (next.y <= 0.05f) {
                    next.y = 0.04f;
                    Quaternionf flat = new Quaternionf().rotationY(Rand.f() * 6.28f);
                    move(p, next, flat, dur);
                    p.restUntil = age + dur + 40 + Rand.range(0, 60);
                    return;
                }
                if (next.x > maxX + 4 || next.x < minX - 4 || next.z > maxZ + 4 || next.z < minZ - 4) {
                    p.needSpawn = true;
                    p.start = age;
                    p.dur = 1;
                    return;
                }
                move(p, next, tumble(p.qTo), dur);
            }
            case STORM -> {
                p.orbit += 0.55f + 0.25f * (3f / Math.max(1f, p.orbitR));
                p.orbitY += Rand.signed() * 0.4f;
                p.orbitY = MathUtil.clamp(p.orbitY, 0.3f, 7f);
                Vector3f next = new Vector3f(stormCenter.x + (float) Math.cos(p.orbit) * p.orbitR, stormCenter.y + p.orbitY,
                        stormCenter.z + (float) Math.sin(p.orbit) * p.orbitR);
                move(p, next, tumble(p.qTo), 4);
            }
            case RISE -> {
                int dur = 16 + Rand.range(0, 8);
                float dt = dur / 20f;
                Vector3f next = new Vector3f(pos).add(Rand.signed() * 0.5f, (0.55f + Rand.f() * 0.4f) * dt * 2f, Rand.signed() * 0.5f);
                if (next.y > topY + 6f) {
                    p.needSpawn = true;
                    p.start = age;
                    p.dur = 1;
                    return;
                }
                move(p, next, tumble(p.qTo), dur);
            }
            default -> {
            }
        }
    }

    private void move(P p, Vector3f target, Quaternionf rot, int dur) {
        Vector3f cur = current(p);
        Quaternionf cr = currentRot(p);
        p.from.set(cur);
        p.qFrom.set(cr);
        p.to.set(target);
        p.qTo.set(rot);
        p.start = age;
        p.dur = Math.max(1, dur);
        if (p.d != null && p.d.isValid()) DisplayFactory.lerp(p.d, c.petalTf(target, rot, p.size), p.dur);
    }

    private Quaternionf tumble(Quaternionf q) {
        return new Quaternionf(q).rotateXYZ(Rand.signed() * 1.4f, Rand.signed() * 1.1f, Rand.signed() * 1.4f);
    }

    private Vector3f current(P p) {
        float u = MathUtil.clamp((age - p.start) / (float) p.dur, 0f, 1f);
        return new Vector3f(p.from).lerp(p.to, u);
    }

    private Quaternionf currentRot(P p) {
        float u = MathUtil.clamp((age - p.start) / (float) p.dur, 0f, 1f);
        return new Quaternionf(p.qFrom).slerp(p.qTo, u);
    }

    private void clampArea(Vector3f v) {
        v.x = MathUtil.clamp(v.x, minX - 3, maxX + 3);
        v.z = MathUtil.clamp(v.z, minZ - 3, maxZ + 3);
        v.y = MathUtil.clamp(v.y, 0.1f, topY + 4);
    }

    public void end() {
        stopped = true;
    }
}
