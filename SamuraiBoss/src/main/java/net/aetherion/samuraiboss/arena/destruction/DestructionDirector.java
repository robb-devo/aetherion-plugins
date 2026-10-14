package net.aetherion.samuraiboss.arena.destruction;

import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.arena.Arena;
import net.aetherion.samuraiboss.arena.DecoManager;
import net.aetherion.samuraiboss.arena.dojo.BuildPlan;
import net.aetherion.samuraiboss.arena.dojo.DojoBlueprint;
import net.aetherion.samuraiboss.arena.dojo.El;
import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.Tf;
import net.aetherion.samuraiboss.render.fx.Palette;
import net.aetherion.samuraiboss.render.model.CubeDef;
import net.aetherion.samuraiboss.render.model.ModelInstance;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Inszeniert die Zerstörung des Dojos über die drei Stufen des Kampfes – und den magischen
 * Wiederaufbau danach.
 *
 * <ul>
 *   <li><b>Stufe 1</b> (Sturm): Shoji-Wände zerreißen zu Papierfetzen, Laternenköpfe stürzen,
 *       Banner werden vom Wind fortgerissen.</li>
 *   <li><b>Stufe 2</b> (Fallende Blüten): der Torii-Balken kracht herab, der Glockenturm stürzt
 *       ein (die Glocke schlägt ein letztes Mal), die Mauerdächer bröckeln, der Kirschbaum
 *       verliert all seine Blüten, die Brücke bricht.</li>
 *   <li><b>Ultimate</b> 天地一閃: alles Übrige wird auf Kopfhöhe durchtrennt, gleitet entlang der
 *       Schnittebene ab und explodiert – die Trümmer erstarren schwebend in der Luft.</li>
 * </ul>
 *
 * <p>Jeder entfernte Block wird gemerkt. Ein Teil davon wird als BlockDisplay (exakt an der
 * Stelle des Blocks, daher nahtlos) animiert – begrenzt durch {@code max-debris-displays}.
 * Liegen gebliebene/schwebende Trümmer werden beim Wiederaufbau wiederverwendet: Sie fliegen
 * genau an ihren Platz zurück und werden dort wieder zum echten Block.</p>
 */
public final class DestructionDirector {

    /** Ein Trümmerstück, das bis zum Wiederaufbau liegen/schweben bleibt. */
    private record Debris(BlockDisplay display, Transformation tf) {
    }

    private final Arena arena;
    private final Settings s;
    private final Sfx sfx;
    private final World world;
    private final Map<El, List<Long>> byEl = new EnumMap<>(El.class);
    /** Oberkante des geschützten Bodens pro Spalte (relativ) – dort landen Trümmer. */
    private final Map<Long, Integer> floor = new HashMap<>();
    private final Map<Long, Debris> resting = new HashMap<>();
    private final Set<Long> destroyed = new LinkedHashSet<>();
    private final List<DecoManager.Piece> decoTaken = new ArrayList<>();
    /** Flüchtige Displays (Papierfetzen, Blüten, Rüstungsteile …) mit Entfernungszeitpunkt. */
    private final Map<Display, Integer> loose = new HashMap<>();
    private final TreeMap<Integer, List<Runnable>> tasks = new TreeMap<>();
    private final BlockData air = Material.AIR.createBlockData();
    private final BlockData water = Material.WATER.createBlockData();
    private int tick;
    private int stage;

    public DestructionDirector(Arena arena, Settings s, Sfx sfx) {
        this.arena = arena;
        this.s = s;
        this.sfx = sfx;
        this.world = arena.world();
        for (Map.Entry<Long, BuildPlan.Placement> e : arena.plan.blocks().entrySet()) {
            BuildPlan.Placement p = e.getValue();
            long k = e.getKey();
            if (p.block().equals("minecraft:air")) continue;
            El el = p.element();
            if (el.protectedEl) {
                if (el == El.TREE_TRUNK) continue;
                long col = col(BuildPlan.kx(k), BuildPlan.kz(k));
                int top = BuildPlan.ky(k) + 1;
                floor.merge(col, top, Math::max);
                continue;
            }
            byEl.computeIfAbsent(el, x -> new ArrayList<>()).add(k);
        }
    }

    private static long col(int x, int z) {
        return ((long) (x + 4096) << 16) | (z + 4096);
    }

    /** Laufhöhe (relativ) in einer Spalte – Trümmer landen hier. */
    private int floorAt(int rx, int rz) {
        Integer f = floor.get(col(rx, rz));
        return f == null ? 0 : f;
    }

    public int stage() {
        return stage;
    }

    // =================================================================== Budget

    private int liveDisplays() {
        return resting.size() + decoTaken.size() + loose.size();
    }

    private boolean budget(int reserve) {
        return liveDisplays() + reserve < s.maxDebrisDisplays;
    }

    // =================================================================== Zeitplan

    private void at(int delay, Runnable r) {
        tasks.computeIfAbsent(tick + Math.max(0, delay), k -> new ArrayList<>()).add(r);
    }

    public void tick() {
        List<Runnable> now;
        while (!tasks.isEmpty() && tasks.firstKey() <= tick) {
            now = tasks.pollFirstEntry().getValue();
            for (Runnable r : now) {
                try {
                    r.run();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
        }
        if (!loose.isEmpty()) {
            Iterator<Map.Entry<Display, Integer>> it = loose.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Display, Integer> e = it.next();
                if (e.getValue() <= tick) {
                    if (e.getKey().isValid()) e.getKey().remove();
                    it.remove();
                }
            }
        }
        tick++;
    }

    // =================================================================== Blöcke brechen

    private enum Style {
        /** fällt (mit Taumeln) zu Boden und bleibt liegen */
        FALL,
        /** wird nach außen geschleudert, fällt und bleibt liegen */
        BURST,
        /** Papier: zerreißt in Fetzen, die davonwehen */
        SHRED,
        /** Blüten: werden klein, segeln zu Boden und vergehen */
        PETAL,
        /** Ultimate: Gleiten an der Schnittebene, Explosion, Erstarren */
        ULT
    }

    /**
     * Entfernt einen Block und animiert ihn optional als Trümmer.
     *
     * @param center Explosions-/Druckzentrum (relativ), für BURST/ULT
     */
    private void breakBlock(long key, Style style, boolean display, Vector3f center, float force) {
        if (destroyed.contains(key) && !resting.containsKey(key)) return;
        int rx = BuildPlan.kx(key), ry = BuildPlan.ky(key), rz = BuildPlan.kz(key);
        int x = arena.ox + rx, y = arena.oy + ry, z = arena.oz + rz;
        Debris old = resting.remove(key);
        BlockDisplay d;
        Transformation from;
        BlockData data;
        if (old != null) {
            // bereits als Trümmer vorhanden (z. B. liegt seit Stufe 2) – weiterverwenden
            d = old.display();
            from = old.tf();
            data = d.isValid() ? d.getBlock() : null;
            if (!d.isValid()) return;
        } else {
            Block b = world.getBlockAt(x, y, z);
            data = b.getBlockData();
            if (data.getMaterial().isAir()) {
                destroyed.add(key);
                return;
            }
            boolean wl = data instanceof Waterlogged w && w.isWaterlogged();
            b.setBlockData(wl ? water : air, false);
            destroyed.add(key);
            if (!display) {
                if (Rand.chance(style == Style.ULT ? 0.04 : 0.3)) dust(x, y, z, data, 0.25);
                return;
            }
            d = DisplayFactory.block(new Location(world, x, y, z), data, "debris", arena.id);
            d.setViewRange(2.5f);
            from = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
            DisplayFactory.snap(d, from);
        }
        animate(key, d, from, style, rx, ry, rz, center, force);
    }

    private void animate(long key, BlockDisplay d, Transformation from, Style style, int rx, int ry, int rz, Vector3f center, float force) {
        Vector3f c0 = Tf.boxCenter(from);
        Vector3f blockCenter = new Vector3f(rx + 0.5f, ry + 0.5f, rz + 0.5f);
        float size = from.getScale().x;
        switch (style) {
            case FALL -> {
                Vector3f drift = new Vector3f(Rand.signed(), 0, Rand.signed()).mul(0.9f);
                fallTo(key, d, c0, drift, rx, ry, rz, size, 1 + Rand.range(0, 3), true);
            }
            case BURST -> {
                Vector3f dir = new Vector3f(blockCenter).sub(center);
                dir.y = 0;
                if (dir.lengthSquared() < 0.01f) dir.set(Rand.signed(), 0, Rand.signed());
                dir.normalize().mul(force * (0.5f + Rand.f() * 0.8f));
                fallTo(key, d, c0, dir, rx, ry, rz, size, 1 + Rand.range(0, 2), true);
            }
            case SHRED -> {
                Vector3f dir = new Vector3f(blockCenter).sub(center);
                if (dir.lengthSquared() < 0.01f) dir.set(Rand.unit());
                dir.normalize();
                Quaternionf spin = new Quaternionf().rotateXYZ(Rand.signed() * 3f, Rand.signed() * 3f, Rand.signed() * 3f);
                Vector3f p1 = new Vector3f(c0).add(new Vector3f(dir).mul(2.5f + Rand.f() * 2f)).add(0, 1.2f + Rand.f(), 0);
                Vector3f p2 = new Vector3f(p1).add(new Vector3f(dir).mul(2f)).add(Rand.signed(), -2.5f - Rand.f() * 2f, Rand.signed());
                Vector3f shred = new Vector3f(0.55f, 0.06f, 0.4f);
                d.setBrightness(new Display.Brightness(14, 15));
                at(1, () -> lerp(d, Tf.box(p1, spin, shred), 7));
                at(8, () -> lerp(d, Tf.box(p2, new Quaternionf(spin).rotateX(2f), new Vector3f(shred).mul(0.4f)), 16));
                loose.put(d, tick + 26);
            }
            case PETAL -> {
                Vector3f p0 = new Vector3f(c0);
                float fl = floorAt(rx, rz) - ry;
                Vector3f drift = new Vector3f(Rand.signed() * 2f, 0, Rand.signed() * 2f);
                Vector3f mid = new Vector3f(p0).add(new Vector3f(drift).mul(0.5f)).add(0, (fl - 0.5f) * 0.45f, 0);
                Vector3f end = new Vector3f(p0).add(drift).add(0, fl - 0.45f, 0);
                Vector3f leaf = new Vector3f(0.38f, 0.06f, 0.3f);
                int t1 = 10 + Rand.range(0, 8);
                at(1, () -> lerp(d, Tf.box(mid, FxRot.random(), leaf), t1));
                at(1 + t1, () -> lerp(d, Tf.box(end, new Quaternionf().rotationY(Rand.f() * 6f), new Vector3f(leaf).mul(0.6f)), 14 + Rand.range(0, 6)));
                loose.put(d, tick + 1 + t1 + 30 + Rand.range(0, 40));
            }
            case ULT -> ultDebris(key, d, c0, blockCenter, center, force, size);
        }
    }

    /** Ballistischer Fall: kurzer Hüpfer, dann beschleunigt zu Boden; bleibt liegen (rest). */
    private void fallTo(long key, BlockDisplay d, Vector3f c0, Vector3f horiz, int rx, int ry, int rz, float size,
                        int delay, boolean rest) {
        float landRel = floorAt(rx + Math.round(horiz.x), rz + Math.round(horiz.z)) - ry; // relativ zur Blockecke
        float h = Math.max(0.2f, c0.y - (landRel + size * 0.5f));
        int tFall = (int) Math.max(5, Math.min(22, Math.sqrt(2 * (h + 0.4) / 0.08)));
        Quaternionf q1 = new Quaternionf().rotateXYZ(Rand.signed() * 0.7f, Rand.signed() * 1.2f, Rand.signed() * 0.7f);
        Quaternionf q2 = new Quaternionf().rotateXYZ(Rand.signed() * 1.6f, Rand.signed() * 2.5f, Rand.signed() * 1.6f);
        float yawRest = Rand.f() * 6.28f;
        Quaternionf qRest = new Quaternionf().rotationY(yawRest).rotateX(Rand.chance(0.3) ? Rand.signed() * 0.25f : 0f);
        Vector3f s3 = new Vector3f(size, size, size);
        Vector3f apex = new Vector3f(c0).add(new Vector3f(horiz).mul(0.25f)).add(0, 0.35f, 0);
        Vector3f midFall = new Vector3f(c0).add(new Vector3f(horiz).mul(0.65f)).add(0, -h * 0.36f + 0.2f, 0);
        Vector3f land = new Vector3f(c0).add(horiz);
        land.y = landRel + size * 0.5f - 0.06f;
        Transformation tRest = Tf.box(land, qRest, s3);
        at(delay, () -> lerp(d, Tf.box(apex, q1, s3), 4));
        int t2 = Math.max(2, Math.round(tFall * 0.6f));
        int t3 = Math.max(2, tFall - t2);
        at(delay + 4, () -> lerp(d, Tf.box(midFall, q2, s3), t2));
        at(delay + 4 + t2, () -> lerp(d, tRest, t3));
        if (rest) {
            resting.put(key, new Debris(d, tRest));
            int landT = delay + 4 + t2 + t3;
            if (Rand.chance(0.12)) at(landT, () -> dust(arena.ox + rx, arena.oy + ry, arena.oz + rz, d.getBlock(), 0.0));
        } else {
            loose.put(d, tick + delay + 4 + t2 + t3 + 8);
        }
    }

    /** Ultimate: Gleiten an der Schnittebene, Explosion, dann erstarrt schwebend (oder Fall). */
    private void ultDebris(long key, BlockDisplay d, Vector3f c0, Vector3f blockCenter, Vector3f center, float force, float size) {
        Vector3f s3 = new Vector3f(size, size, size);
        Quaternionf q0 = new Quaternionf(d.getTransformation().getLeftRotation());
        boolean upper = blockCenter.y >= 2f;
        // 1) Gleiten entlang der Schnittebene (nur der obere Teil)
        Vector3f slid = new Vector3f(c0);
        if (upper) slid.add(ultSlide);
        // erst im nächsten Tick: ein frisch gespawntes Display kann im selben Tick nicht interpolieren
        at(1, () -> lerp(d, Tf.box(slid, q0, s3), 5));
        // 2) Explosion
        Vector3f dir = new Vector3f(blockCenter).sub(center);
        dir.y = 0;
        float dist = dir.length();
        if (dist < 0.01f) dir.set(Rand.signed(), 0, Rand.signed());
        dir.normalize();
        float push = force * (0.35f + Rand.f() * 0.6f) * (1f + 6f / (dist + 6f));
        Vector3f out = new Vector3f(dir).mul(push).add(Rand.signed() * 0.8f, (upper ? 1.5f : 0.8f) + Rand.f() * 3.2f, Rand.signed() * 0.8f);
        Quaternionf q1 = new Quaternionf().rotateXYZ(Rand.signed() * 2.4f, Rand.signed() * 2.4f, Rand.signed() * 2.4f);
        Quaternionf q2 = new Quaternionf(q1).rotateXYZ(Rand.signed() * 0.7f, Rand.signed() * 0.7f, Rand.signed() * 0.7f);
        Vector3f fast = new Vector3f(slid).add(new Vector3f(out).mul(0.72f));
        Vector3f slow = new Vector3f(slid).add(out);
        int delay = 9 + Rand.range(0, 2);
        at(delay, () -> lerp(d, Tf.box(fast, q1, s3), 5));
        if (s.floatingDebris) {
            // langsam ausrollen und dann im Zeitstillstand erstarren
            Transformation frozen = Tf.box(slow, q2, s3);
            at(delay + 5, () -> lerp(d, frozen, 18 + Rand.range(0, 8)));
            resting.put(key, new Debris(d, frozen));
        } else {
            int rx = (int) Math.floor(blockCenter.x), ry = (int) Math.floor(blockCenter.y), rz = (int) Math.floor(blockCenter.z);
            at(delay + 5, () -> fallTo(key, d, fast, new Vector3f(out.x * 0.3f, 0, out.z * 0.3f), rx, ry, rz, size, 0, true));
        }
    }

    private Vector3f ultSlide = new Vector3f(0.35f, 0, 0);

    private static void lerp(Display d, Transformation t, int ticks) {
        if (d.isValid()) DisplayFactory.lerp(d, t, ticks);
    }

    private void dust(int x, int y, int z, BlockData data, double spread) {
        if (!s.particles || data == null) return;
        world.spawnParticle(Particle.BLOCK, x + 0.5, y + 0.5, z + 0.5, 6, 0.3 + spread, 0.3 + spread, 0.3 + spread, 0.05, data);
    }

    private void breakSound(int x, int y, int z, BlockData data) {
        Location l = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        String m = data.getMaterial().name();
        Sfx.Id id;
        if (m.contains("WOOL") || m.contains("CARPET") || m.contains("PANE") || m.contains("BANNER")) id = Sfx.Id.BREAK_PAPER;
        else if (m.contains("LEAVES")) id = Sfx.Id.PETALS;
        else if (m.contains("STONE") || m.contains("BRICK") || m.contains("ANDESITE") || m.contains("DEEPSLATE")
                || m.contains("CONCRETE") || m.contains("TERRACOTTA") || m.contains("TUFF") || m.contains("COPPER")) id = Sfx.Id.BREAK_STONE;
        else id = Sfx.Id.BREAK_WOOD;
        sfx.play(l, id, 0.7f, 0.9f + Rand.f() * 0.2f);
    }

    private List<Long> blocks(El el) {
        return byEl.getOrDefault(el, List.of());
    }

    /** Bricht alle Blöcke eines Bauteils, über 'spread' Ticks verteilt (sortiert nach 'order'). */
    private void breakElement(El el, Style style, float displayShare, Vector3f center, float force, int delay, int spread,
                              Comparator<Long> order) {
        List<Long> list = new ArrayList<>(blocks(el));
        if (order != null) list.sort(order);
        int n = list.size();
        int soundEvery = Math.max(1, n / 10);
        for (int i = 0; i < n; i++) {
            long k = list.get(i);
            int t = delay + (n <= 1 ? 0 : Math.round(spread * i / (float) (n - 1)));
            boolean disp = Rand.f() < displayShare;
            final int idx = i;
            at(t, () -> {
                boolean show = disp && budget(40);
                if (idx % soundEvery == 0) {
                    int x = arena.ox + BuildPlan.kx(k), y = arena.oy + BuildPlan.ky(k), z = arena.oz + BuildPlan.kz(k);
                    BlockData bd = world.getBlockAt(x, y, z).getBlockData();
                    if (!bd.getMaterial().isAir()) breakSound(x, y, z, bd);
                }
                breakBlock(k, style, show, center, force);
            });
        }
    }

    private static Comparator<Long> byHeightDesc() {
        return Comparator.comparingInt((Long k) -> -BuildPlan.ky(k));
    }

    private static Comparator<Long> byHeightAsc() {
        return Comparator.comparingInt(BuildPlan::ky);
    }

    private static Comparator<Long> byDistance(Vector3f c) {
        return Comparator.comparingDouble((Long k) -> Math.hypot(BuildPlan.kx(k) - c.x, BuildPlan.kz(k) - c.z));
    }

    // =================================================================== Deko

    private void decoFly(El el, int delay, Vector3f push, boolean rest, Sfx.Id landSound) {
        List<DecoManager.Piece> pieces = arena.deco.take(el);
        for (DecoManager.Piece p : pieces) {
            Display d = p.display();
            if (d == null || !d.isValid()) continue;
            decoTaken.add(p);
            Transformation t = p.transform();
            Vector3f c0 = d instanceof BlockDisplay ? Tf.boxCenter(t) : new Vector3f(t.getTranslation());
            Vector3f sc = new Vector3f(t.getScale());
            float fl = floorAt((int) Math.floor(p.spec().x), (int) Math.floor(p.spec().z)) - (float) p.spec().y;
            Vector3f mid = new Vector3f(c0).add(new Vector3f(push).mul(0.5f)).add(0, 0.6f, 0);
            Vector3f end = new Vector3f(c0).add(push);
            Quaternionf q1 = new Quaternionf(t.getLeftRotation()).rotateXYZ(Rand.signed() * 0.8f, Rand.signed() * 1.5f, Rand.signed() * 0.8f);
            Quaternionf q2 = new Quaternionf(q1).rotateXYZ(Rand.signed() * 1.2f, Rand.signed() * 1.2f, Rand.signed() * 1.2f);
            int dl = delay + Rand.range(0, 6);
            if (rest) {
                end.y = fl + Math.min(sc.y, 1.2f) * 0.35f;
                Quaternionf qr = new Quaternionf().rotationY(Rand.f() * 6.28f).rotateZ((float) (Math.PI / 2) * (Rand.chance(0.5) ? 1 : 0));
                at(dl, () -> lerp(d, place(d, mid, q1, sc), 5));
                at(dl + 5, () -> lerp(d, place(d, end, qr, sc), 12));
                if (landSound != null && Rand.chance(0.35)) at(dl + 17, () -> sfx.play(locOf(p, end), landSound, 1f, 0.8f + Rand.f() * 0.3f));
            } else {
                at(dl, () -> lerp(d, place(d, mid, q1, sc), 6));
                at(dl + 6, () -> lerp(d, place(d, end, q2, new Vector3f()), 16));
            }
        }
    }

    private Location locOf(DecoManager.Piece p, Vector3f rel) {
        return p.origin().clone().add(rel.x, rel.y, rel.z);
    }

    /** Block-Displays werden um ihren Mittelpunkt gedreht, Items/Texte sind schon mittig. */
    private static Transformation place(Display d, Vector3f center, Quaternionf q, Vector3f scale) {
        if (d instanceof BlockDisplay) return Tf.box(center, q, scale);
        return Tf.centered(center, q, scale);
    }

    // =================================================================== Stufen

    /** Stufe 1 – der Sturm reißt Papier, Laternenköpfe und Banner fort. */
    public void stage1() {
        if (stage >= 1) return;
        stage = 1;
        Vector3f hall = arena.plan.point("hall");
        hall.y = 3;
        breakElement(El.HALL_SHOJI, Style.SHRED, 1f, hall, 3f, 0, 14, byDistance(new Vector3f(0, 0, -22)));
        breakElement(El.LANTERN_TOP, Style.FALL, 1f, null, 0f, 4, 20, byHeightDesc());
        breakElement(El.NOBORI, Style.FALL, 1f, null, 0f, 2, 18, byHeightDesc());
        decoFly(El.NOBORI, 0, new Vector3f(6f, 2.5f, 2f), false, null);
        sfx.play(arena.point("hall"), Sfx.Id.BREAK_PAPER, 1.5f, 0.8f);
    }

    /** Stufe 2 – Torii, Glockenturm, Mauerdächer, Blüten, Brücke. */
    public void stage2() {
        if (stage >= 2) return;
        stage = 2;
        // Torii-Balken kracht herab
        breakElement(El.TORII_TOP, Style.FALL, 1f, null, 0f, 0, 6, byHeightAsc());
        decoFly(El.TORII_TOP, 2, new Vector3f(0, 0, 1.2f), true, Sfx.Id.LAND);
        at(14, () -> sfx.play(arena.loc(0.5, 1, 20.5), Sfx.Id.LAND, 1.5f, 0.7f));
        // Glockenturm stürzt ein – die Glocke schlägt ein letztes Mal
        Vector3f bell = arena.plan.point("bell");
        breakElement(El.BELL_TOWER, Style.BURST, 1f, bell, 2.2f, 16, 18, byHeightDesc());
        decoFly(El.BELL_TOWER, 20, new Vector3f(-1.5f, 0, -1.2f), true, Sfx.Id.CLANG);
        at(34, () -> {
            sfx.play(arena.point("bell"), Sfx.Id.BELL, 1.2f, 0.62f);
            sfx.play(arena.point("bell"), Sfx.Id.CLANG, 1.5f, 0.6f);
        });
        // Mauerdächer bröckeln ringsum (vom Tor aus)
        breakElement(El.WALL_CAP, Style.FALL, 0.28f, null, 0f, 10, 70,
                Comparator.comparingDouble((Long k) -> angleFromGate(BuildPlan.kx(k), BuildPlan.kz(k))));
        // der Kirschbaum verliert seine Blüten
        breakElement(El.TREE_LEAVES, Style.PETAL, 0.3f, null, 0f, 6, 80, byHeightDesc());
        decoFly(El.TREE_LEAVES, 6, new Vector3f(0, -4f, 0), false, null);
        // Brücke bricht in den Teich
        breakElement(El.BRIDGE, Style.FALL, 1f, null, 0f, 30, 10, byHeightAsc());
    }

    private static double angleFromGate(int x, int z) {
        // Winkel um den Hofmittelpunkt, 0 = Tor (Süden), wächst im Uhrzeigersinn
        double a = Math.atan2(x, z - 4.5);
        return Math.abs(a);
    }

    /**
     * Die Ultimate: alles Zerstörbare wird zerschnitten und explodiert.
     *
     * @param centerRel Explosionszentrum relativ zum Ursprung
     * @param slideDir  Richtung, in die der obere Teil an der Schnittebene abgleitet (horizontal)
     */
    public void ultimate(Vector3f centerRel, Vector3f slideDir) {
        stage = 3;
        ultSlide = new Vector3f(slideDir.x, 0, slideDir.z).normalize().mul(0.45f);
        List<Long> all = new ArrayList<>();
        for (Map.Entry<El, List<Long>> e : byEl.entrySet()) {
            for (long k : e.getValue()) {
                if (destroyed.contains(k) && !resting.containsKey(k)) continue;
                all.add(k);
            }
        }
        // bereits liegende Trümmer zählen schon zum Budget – der Rest wird nach Sichtbarkeit verteilt
        int free = Math.max(0, s.maxDebrisDisplays - liveDisplays());
        int fresh = 0;
        for (long k : all) if (!resting.containsKey(k)) fresh++;
        float share = fresh == 0 ? 0f : Math.min(1f, free / (float) fresh);
        for (long k : all) {
            boolean disp = resting.containsKey(k) || (Rand.f() < share * (isSurface(k) ? 1.35f : 0.5f));
            if (disp && !resting.containsKey(k) && !budget(1)) disp = false;
            breakBlock(k, Style.ULT, disp, centerRel, 9f);
        }
        // Deko der restlichen Bauteile
        for (El el : new El[]{El.HALL_ROOF, El.HALL_INTERIOR, El.WALL, El.GATE, El.TORII, El.LANTERN, El.GARDEN, El.FENCE,
                El.HALL_PILLAR, El.HALL_WALL}) {
            decoUlt(el, centerRel);
        }
        for (int i = 0; i < 6; i++) {
            Location l = arena.loc(centerRel.x + Rand.signed() * 18, 3 + Rand.f() * 6, centerRel.z + Rand.signed() * 20);
            at(9 + i, () -> sfx.play(l, Rand.chance(0.5) ? Sfx.Id.BREAK_WOOD : Sfx.Id.BREAK_STONE, 2f, 0.6f));
        }
    }

    private void decoUlt(El el, Vector3f centerRel) {
        for (DecoManager.Piece p : arena.deco.take(el)) {
            Display d = p.display();
            if (d == null || !d.isValid()) continue;
            decoTaken.add(p);
            Transformation t = p.transform();
            Vector3f c0 = d instanceof BlockDisplay ? Tf.boxCenter(t) : new Vector3f(t.getTranslation());
            Vector3f sc = new Vector3f(t.getScale());
            Vector3f world0 = new Vector3f((float) p.spec().x, (float) p.spec().y, (float) p.spec().z).add(c0);
            Vector3f dir = new Vector3f(world0).sub(centerRel);
            dir.y = 0;
            if (dir.lengthSquared() < 0.01f) dir.set(1, 0, 0);
            dir.normalize().mul(3f + Rand.f() * 4f).add(0, 2f + Rand.f() * 3f, 0);
            Quaternionf q = new Quaternionf(t.getLeftRotation()).rotateXYZ(Rand.signed() * 2f, Rand.signed() * 2f, Rand.signed() * 2f);
            Vector3f end = new Vector3f(c0).add(dir);
            at(9, () -> lerp(d, place(d, new Vector3f(c0).add(new Vector3f(dir).mul(0.7f)), q, sc), 5));
            at(14, () -> lerp(d, place(d, end, q, sc), 20));
        }
    }

    /** true, wenn der Block im Bauplan mindestens einen freien Nachbarn hat (sichtbar). */
    private boolean isSurface(long k) {
        int x = BuildPlan.kx(k), y = BuildPlan.ky(k), z = BuildPlan.kz(k);
        BuildPlan p = arena.plan;
        return !p.isSolidAt(x + 1, y, z) || !p.isSolidAt(x - 1, y, z) || !p.isSolidAt(x, y + 1, z)
                || !p.isSolidAt(x, y - 1, z) || !p.isSolidAt(x, y, z + 1) || !p.isSolidAt(x, y, z - 1);
    }

    // =================================================================== Teile des Bosses

    /**
     * Ein abgelöstes Modellteil (Helm, Banner, Rüstungsplatte) fliegt davon und bleibt liegen.
     * Es verschwindet beim Wiederaufbau.
     */
    public void flyOff(ModelInstance.PartState ps, Vector3f velocity, boolean rest, int lifeTicks) {
        if (ps == null || !budget(0)) return;
        Display d;
        if (ps.def().kind == CubeDef.Kind.ITEM && ps.item() != null) {
            ItemDisplay id = DisplayFactory.item(ps.origin(), ps.item(), ItemDisplay.ItemDisplayTransform.FIXED, "debris", arena.id);
            d = id;
        } else if (ps.block() != null) {
            d = DisplayFactory.block(ps.origin(), ps.block(), "debris", arena.id);
        } else {
            return;
        }
        d.setViewRange(2f);
        Transformation t = ps.transform();
        DisplayFactory.snap(d, t);
        boolean block = d instanceof BlockDisplay;
        Vector3f c0 = block ? Tf.boxCenter(t) : new Vector3f(t.getTranslation());
        Vector3f sc = new Vector3f(t.getScale());
        Quaternionf q0 = new Quaternionf(t.getLeftRotation());
        Vector3f apex = new Vector3f(c0).add(new Vector3f(velocity).mul(0.5f)).add(0, Math.max(0.6f, velocity.y), 0);
        Location o = ps.origin();
        int rx = (int) Math.floor(o.getX() - arena.ox + c0.x + velocity.x), rz = (int) Math.floor(o.getZ() - arena.oz + c0.z + velocity.z);
        float groundRel = (float) (arena.oy + floorAt(rx, rz) - o.getY());
        Vector3f land = new Vector3f(c0).add(velocity.x, 0, velocity.z);
        land.y = groundRel + Math.min(sc.y, 0.5f) * 0.5f;
        Quaternionf q1 = new Quaternionf(q0).rotateXYZ(Rand.signed() * 2f, Rand.signed() * 2f, Rand.signed() * 2f);
        Quaternionf q2 = new Quaternionf().rotationY(Rand.f() * 6.28f).rotateX((float) (Math.PI / 2) * (Rand.chance(0.5) ? 1 : 0));
        float fall = Math.max(0.5f, apex.y - land.y);
        int tFall = (int) Math.max(5, Math.min(20, Math.sqrt(2 * fall / 0.08)));
        at(1, () -> lerp(d, place(d, apex, q1, sc), 6));
        at(7, () -> lerp(d, place(d, land, q2, sc), tFall));
        if (rest) {
            loose.put(d, tick + Math.max(lifeTicks, 7 + tFall + 4));
            at(7 + tFall, () -> sfx.play(o.clone().add(land.x, land.y, land.z), Sfx.Id.CLANG, 0.6f, 1.1f + Rand.f() * 0.3f));
        } else {
            at(7 + tFall, () -> lerp(d, place(d, land, q2, new Vector3f()), 8));
            loose.put(d, tick + 7 + tFall + 9);
        }
    }

    // =================================================================== Wiederaufbau

    public boolean hasDamage() {
        return !destroyed.isEmpty() || !decoTaken.isEmpty();
    }

    /**
     * Erstellt den Wiederaufbau: Deko schwebt zurück, alle zerstörten Blöcke fliegen (getragen
     * von Blüten) an ihren Platz – liegende/schwebende Trümmer werden dabei wiederverwendet –,
     * zum Schluss ein exakter Abgleich mit dem Snapshot.
     */
    public RebuildJob rebuild(net.aetherion.samuraiboss.arena.BlockSnapshot snap, int perTick, int flight, Runnable onDone) {
        tasks.clear();
        // flüchtige Teile (Fetzen, Blüten, Rüstung) lösen sich auf
        for (Display d : loose.keySet()) {
            if (!d.isValid()) continue;
            Transformation t = d.getTransformation();
            DisplayFactory.lerp(d, d instanceof BlockDisplay ? Tf.shrinkBox(t, 0f)
                    : new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(), t.getRightRotation()), 10);
        }
        List<Display> fade = new ArrayList<>(loose.keySet());
        loose.clear();

        List<Long> order = new ArrayList<>(destroyed);
        Vector3f c = arena.plan.point("center");
        order.sort(Comparator.comparingInt((Long k) -> BuildPlan.ky(k))
                .thenComparingDouble(k -> Math.hypot(BuildPlan.kx(k) - c.x, BuildPlan.kz(k) - c.z)));
        List<FlyInJob.Target> targets = new ArrayList<>(order.size());
        for (long k : order) {
            int x = arena.ox + BuildPlan.kx(k), y = arena.oy + BuildPlan.ky(k), z = arena.oz + BuildPlan.kz(k);
            BlockData data = snap != null && snap.contains(x, y, z) ? snap.dataAt(x, y, z) : null;
            if (data == null) {
                BuildPlan.Placement p = arena.plan.get(BuildPlan.kx(k), BuildPlan.ky(k), BuildPlan.kz(k));
                if (p == null) continue;
                data = org.bukkit.Bukkit.createBlockData(p.block());
            }
            Debris deb = resting.remove(k);
            if (deb != null && deb.display().isValid()) {
                targets.add(new FlyInJob.Target(x, y, z, data, deb.display(), deb.tf()));
            } else {
                targets.add(new FlyInJob.Target(x, y, z, data, null, null));
            }
        }
        // übrige Trümmer ohne Ziel entfernen
        for (Debris d : resting.values()) if (d.display().isValid()) d.display().remove();
        resting.clear();
        destroyed.clear();
        List<DecoManager.Piece> deco = new ArrayList<>(decoTaken);
        decoTaken.clear();
        FlyInJob fly = new FlyInJob(world, arena.id, targets, perTick, flight, FlyInJob.Mode.FROM_SCATTER, l -> {
            if (Rand.chance(0.25)) sfx.play(l, Sfx.Id.REBUILD, 0.8f, 0.9f + Rand.f() * 0.5f);
            if (s.particles && Rand.chance(0.5)) world.spawnParticle(Particle.CHERRY_LEAVES, l, 2, 0.4, 0.3, 0.4, 0);
        }, null).withPetals(3, Palette.PINK_SOFT, Palette.PINK, Palette.PETAL_WHITE);
        return new RebuildJob(arena, fly, deco, fade, snap, s.restorePerTick, flight, onDone);
    }

    /** Sofort alles entfernen (Abbruch/Server-Stopp). Blöcke stellt der Aufrufer per Snapshot her. */
    public void clearAll() {
        tasks.clear();
        for (Display d : loose.keySet()) if (d.isValid()) d.remove();
        loose.clear();
        for (Debris d : resting.values()) if (d.display().isValid()) d.display().remove();
        resting.clear();
        for (DecoManager.Piece p : decoTaken) if (p.display() != null && p.display().isValid()) p.display().remove();
        decoTaken.clear();
        destroyed.clear();
    }

    public int debrisCount() {
        return liveDisplays();
    }

    /** Hilfsklasse für zufällige Drehungen (ohne FxContext). */
    private static final class FxRot {
        static Quaternionf random() {
            return new Quaternionf().rotateXYZ(Rand.f() * 6.28f, Rand.f() * 6.28f, Rand.f() * 6.28f);
        }
    }

    /** Für Tests/Statistik: Anzahl zerstörbarer Blöcke pro Bauteil. */
    public Map<El, Integer> counts() {
        Map<El, Integer> m = new EnumMap<>(El.class);
        for (Map.Entry<El, List<Long>> e : byEl.entrySet()) m.put(e.getKey(), e.getValue().size());
        return m;
    }

    @SuppressWarnings("unused")
    private static int wallSouth() {
        return DojoBlueprint.WALL_S;
    }
}
