package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.core.SkyControl;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaLayout;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The arena comes apart in two set pieces (both real: the floor is really gone afterwards).
 *
 * <ul>
 *   <li><b>{@link Kind#BURN}</b> (60 %): the Corona, the outer ring, burns away. A glow sweeps once around it,
 *       sector by sector: cracks, then the stone turns to magma, then it crumbles into the void, still
 *       glowing. Two bars of warning before the first sector goes: get inside.</li>
 *   <li><b>{@link Kind#SHATTER}</b> (40 %): Helios contracts and a supernova wave rolls out along the floor
 *       (jump it). Behind it every second sector of the Course cracks and falls, and the seam between the
 *       Crown and the Course breaks: what is left of the Course floats as islands.</li>
 * </ul>
 */
final class ArenaBreak extends Attack {

    enum Kind { BURN, SHATTER }

    private static final Color HOT = Color.fromRGB(255, 120, 30);
    private static final Color WHITE = Color.fromRGB(255, 244, 214);

    private final HeliosScript h;
    private final Kind kind;
    private final List<Arena.Replica> falling = new ArrayList<>();
    private final List<Integer> fallStart = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private int warn;
    private int step;
    private Shapes.Ring wave;
    private float start;

    ArenaBreak(HeliosScript h, Kind kind) {
        super(h);
        this.h = h;
        this.kind = kind;
    }

    @Override
    public String id() {
        return kind == Kind.BURN ? "burn" : "shatter";
    }

    @Override
    public Family family() {
        return Family.ARENA;
    }

    @Override
    public void start() {
        warn = enc.tempo().ticks(8);
        step = Math.max(3, enc.tempo().ticks(0.5));
        Vector3f c = h.partyCentroid();
        start = HMath.angleOf(c.x, c.z) + HMath.PI;
        if (kind == Kind.BURN) {
            int n = ArenaLayout.SECTORS[2];
            int first = ArenaLayout.sectorOf(2, start);
            for (int i = 0; i < n; i++) {
                order.add(Math.floorMod(first + (i % 2 == 0 ? i / 2 : -(i + 1) / 2), n));
            }
            enc.score().play(Sound.BLOCK_FIRE_AMBIENT, 1f, 0.5f);
            enc.score().play(Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.5f);
            enc.sky().to(SkyControl.DUSK + 300f, 25f);
            for (Player p : enc.audience()) {
                p.sendActionBar(de.aetherion.bossengine.util.TextUtil.component(
                        "&6&lThe Corona is burning away! &eMove inward!"));
            }
        } else {
            for (int s = 1; s < ArenaLayout.SECTORS[1]; s += 2) {
                order.add(s);
            }
            wave = new Shapes.Ring(stage, g, stage.budget().scaled(44, 26), Material.WHITE_CONCRETE, WHITE, 15, true);
            wave.hide(new Vector3f(), 0);
            h.rig().ringScale(0.35f);
            enc.score().sweep(null, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.2f, 1.6f, 0.5f, enc.tempo().ticks(2), 3);
        }
    }

    @Override
    protected boolean tick() {
        return kind == Kind.BURN ? burn() : shatter();
    }

    /* ------------------------------------------------------------------ burn */

    private boolean burn() {
        Arena arena = enc.arena();
        // Warning: the whole ring cracks and glows for two bars.
        if (t < warn) {
            if (t % 10 == 0) {
                for (int s = 0; s < ArenaLayout.SECTORS[2]; s++) {
                    arena.crackSector(2, s, 0.2f + 0.5f * HMath.window(t, 0, warn), 120);
                }
                enc.score().play(Sound.BLOCK_FIRE_AMBIENT, 0.7f, 0.6f + 0.4f * HMath.window(t, 0, warn));
            }
            return false;
        }
        int local = t - warn;
        int idx = local / step;
        if (local % step == 0 && idx < order.size()) {
            int sector = order.get(idx);
            // Magma first: the stone glows before it goes.
            for (ArenaLayout.Cell c : arena.sectorCells(2, sector, true)) {
                arena.swap(c.dx(), c.dy(), c.dz(), Material.MAGMA_BLOCK);
            }
            Vector3f mid = HMath.ring(ArenaLayout.ringMid(2), ArenaLayout.sectorAngle(2, sector), 0.5f);
            enc.score().at(mid, Sound.BLOCK_LAVA_POP, 1f, 0.6f);
            enc.score().at(mid, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.5f);
        }
        int dropIdx = idx - 4;
        if (local % step == 0 && dropIdx >= 0 && dropIdx < order.size()) {
            int sector = order.get(dropIdx);
            List<ArenaLayout.Cell> cells = arena.sectorCells(2, sector, false);
            Arena.Replica rep = arena.replica(g, cells, true);
            rep.material(Material.MAGMA_BLOCK);
            rep.brightness(15);
            rep.pose(new Quaternionf(), new Vector3f(), 1f, 0);
            arena.revertTempsIn(cells);
            arena.remove(cells);
            arena.markSector(2, sector, Arena.SectorState.BURNED);
            falling.add(rep);
            fallStart.add(t);
            Vector3f mid = HMath.ring(ArenaLayout.ringMid(2), ArenaLayout.sectorAngle(2, sector), 0f);
            enc.score().at(mid, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.5f);
            enc.camera().shakeFrom(mid, 16f, 5);
        }
        animateFalling();
        if (dropIdx >= order.size() + 12) {
            arena.markRingGone(2);
            h.rubble(22, 34f, 46f);
            return true;
        }
        return false;
    }

    /* ------------------------------------------------------------------ shatter */

    private boolean shatter() {
        Arena arena = enc.arena();
        int contract = enc.tempo().ticks(2);
        Vector3f origin = new Vector3f(h.rig().center.x, 0f, h.rig().center.z);
        if (t == contract) {
            h.rig().ringScale(1.8f);
            enc.camera().flash(1, 6);
            enc.score().play(Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
            enc.score().play(Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
            enc.score().play(Sound.ITEM_TOTEM_USE, 0.6f, 0.5f);
            for (int s : order) {
                arena.crackSector(1, s, 0.4f, 200);
            }
        }
        if (t > contract) {
            float r = (t - contract) * 1.0f;
            if (r < 34f) {
                wave.flat(new Vector3f(origin).add(0f, 0.3f, 0f), r, 0.5f, 0.5f, 0f, 1);
                for (Player p : enc.fighters()) {
                    Vector3f f = stage.feet(p);
                    float d = HMath.horizontal(new Vector3f(f).sub(origin));
                    if (Math.abs(d - r) < 1.4f && f.y < 0.8f) {
                        Vector3f out = new Vector3f(f).sub(origin).normalize();
                        enc.hit(p, h.power("seismic", 90) * 0.6, "nova_wave", 10, new Vector(out.x * 0.9, 0.5, out.z * 0.9));
                    }
                }
            } else if (r < 35f) {
                wave.hide(origin, 3);
            }
        }
        int fallAt = contract + enc.tempo().ticks(4);
        if (t > contract && t < fallAt && t % 8 == 0) {
            float f = HMath.window(t, contract, fallAt);
            for (int s : order) {
                arena.crackSector(1, s, 0.4f + 0.6f * f, 200);
            }
            enc.score().play(Sound.BLOCK_DEEPSLATE_BREAK, 0.8f, 0.5f + f * 0.5f);
        }
        if (t == fallAt) {
            for (int s : order) {
                List<ArenaLayout.Cell> cells = arena.sectorCells(1, s, false);
                Arena.Replica rep = arena.replica(g, cells, true);
                rep.pose(new Quaternionf(), new Vector3f(), 1f, 0);
                arena.remove(cells);
                arena.markSector(1, s, Arena.SectorState.FALLEN);
                falling.add(rep);
                fallStart.add(t);
            }
            // The seam between the Crown and the Course breaks: the rest of the Course floats free.
            List<ArenaLayout.Cell> seam = new ArrayList<>();
            for (ArenaLayout.Cell c : arena.ringCells(1, false)) {
                if (c.radius() < ArenaLayout.RING_IN[1] + 0.5f) {
                    seam.add(c);
                }
            }
            arena.remove(seam);
            enc.score().play(Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
            enc.score().play(Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1.5f, 0.5f);
            enc.camera().shakeAll(14, 2);
            h.rig().ringScale(1f);
        }
        animateFalling();
        if (t == fallAt + 70) {
            h.rubble(10, 26f, 40f);
        }
        return t > fallAt + 70;
    }

    private void animateFalling() {
        for (int i = 0; i < falling.size(); i++) {
            int age = t - fallStart.get(i);
            if (age > 64) {
                continue;
            }
            Arena.Replica r = falling.get(i);
            float f = HMath.window(age, 0, 60);
            Vector3f pivot = r.pivot();
            Vector3f out = new Vector3f(pivot.x, 0f, pivot.z).normalize();
            Vector3f axis = new Vector3f(-out.z, 0f, out.x);
            Quaternionf tip = new Quaternionf().rotateAxis(HMath.inQuad(f) * 1.2f, axis.x, axis.y, axis.z);
            Vector3f offset = new Vector3f(out).mul(f * 5f).add(0f, -HMath.inQuad(f) * 40f, 0f);
            if (age % 2 == 0) {
                r.pose(tip, offset, 1f - 0.35f * f, 2);
            }
            if (age == 62) {
                r.pose(tip, offset, 0.001f, 0);
            }
        }
    }

    @Override
    protected void cleanup() {
        h.rig().ringScale(1f);
    }
}
