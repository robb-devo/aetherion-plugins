package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.helios.star.DyingStar;
import de.aetherion.bossengine.helios.world.Arena;
import de.aetherion.bossengine.helios.world.ArenaLayout;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PLATFORM FALL. The star flings a rock from its debris belt at the floor under the party. Where it
 * will land, the sector cracks beat by beat (the vanilla break overlay, per block, packet-only) and
 * a low groan climbs. On the last beat the rock hits, the sector breaks off for real and tumbles into
 * the void, tipping outward. Stay on it and you fall with it (the star will drag you back up, hard).
 * Some beats later the sector rises out of the depths again and locks back into the ring.
 */
final class PlatformFall extends Attack {

    private final HeraldScript s;
    private final List<int[]> sectors = new ArrayList<>();
    private final List<Arena.Replica> replicas = new ArrayList<>();
    private final List<List<ArenaLayout.Cell>> cells = new ArrayList<>();
    private int crack;
    private int dropAt;
    private int regrowAt;
    private int riseLen;
    private DyingStar.Taken rock;
    private final Vector3f rockFrom = new Vector3f();
    private final Vector3f rockTo = new Vector3f();

    PlatformFall(HeraldScript s) {
        super(s);
        this.s = s;
    }

    @Override
    public String id() {
        return "platform";
    }

    @Override
    public Family family() {
        return Family.ARENA;
    }

    @Override
    public void start() {
        crack = enc.tempo().ticks(Math.max(2, s.cfgInt("platform.crack-beats", 4)));
        dropAt = crack;
        riseLen = 40;
        regrowAt = dropAt + Math.max(60, s.cfgInt("platform.regrow-seconds", 14) * 20);
        pickSectors();
        if (sectors.isEmpty()) {
            return;
        }
        int[] first = sectors.get(0);
        rockTo.set(HMath.ring(ArenaLayout.ringMid(first[0]), ArenaLayout.sectorAngle(first[0], first[1]), 0.3f));
        rock = enc.star().take();
        if (rock != null) {
            rockFrom.set(rock.at());
            g.displays().add(rock.display());
        }
        enc.score().at(rockTo, Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.5f);
        enc.score().sweep(rockTo, Sound.BLOCK_GRINDSTONE_USE, 0.5f, 1f, 0.5f, 0.9f, crack, 4);
    }

    /** One sector under the busiest part of the party, plus one more below half health. */
    private void pickSectors() {
        List<Player> f = enc.fighters();
        int count = s.instance().healthPercent() < 50 ? 2 : 1;
        List<int[]> candidates = new ArrayList<>();
        for (Player p : f) {
            Vector3f at = stage.feet(p);
            int ring = ArenaLayout.ringOf(HMath.horizontal(at));
            if (ring < 1) {
                continue;
            }
            int sector = ArenaLayout.sectorOf(ring, HMath.angleOf(at.x, at.z));
            candidates.add(new int[]{ring, sector});
        }
        if (candidates.isEmpty()) {
            int ring = 1 + ThreadLocalRandom.current().nextInt(2);
            candidates.add(new int[]{ring, ThreadLocalRandom.current().nextInt(ArenaLayout.SECTORS[ring])});
        }
        java.util.Collections.shuffle(candidates);
        for (int[] c : candidates) {
            if (sectors.size() >= count) {
                break;
            }
            if (enc.arena().state(c[0], c[1]) != Arena.SectorState.INTACT) {
                continue;
            }
            boolean dup = false;
            for (int[] o : sectors) {
                dup |= o[0] == c[0] && o[1] == c[1];
            }
            if (!dup) {
                sectors.add(c);
            }
        }
    }

    @Override
    protected boolean tick() {
        if (sectors.isEmpty()) {
            return true;
        }
        Arena arena = enc.arena();
        if (t < dropAt) {
            float f = HMath.window(t, 0, dropAt);
            if (enc.tempo().onBeat() || t == 0) {
                for (int[] sec : sectors) {
                    arena.crackSector(sec[0], sec[1], 0.15f + 0.85f * f, 200);
                }
                enc.score().at(rockTo, Sound.BLOCK_DEEPSLATE_BREAK, 0.8f, 0.5f + f * 0.4f);
                enc.camera().shakeFrom(rockTo, 14f, 3);
            }
            flyRock(f);
        }
        if (t == dropAt) {
            drop();
        }
        if (t > dropAt && t < dropAt + 60) {
            float f = HMath.window(t, dropAt, dropAt + 60);
            for (Arena.Replica r : replicas) {
                Vector3f pivot = r.pivot();
                Vector3f out = new Vector3f(pivot.x, 0f, pivot.z).normalize();
                Vector3f axis = new Vector3f(-out.z, 0f, out.x);
                Quaternionf tip = new Quaternionf().rotateAxis(HMath.inQuad(f) * 1.4f, axis.x, axis.y, axis.z);
                Vector3f offset = new Vector3f(out).mul(f * 4f).add(0f, -HMath.inQuad(f) * 38f, 0f);
                r.pose(tip, offset, 1f - 0.3f * f, 2);
            }
        }
        if (t == dropAt + 60) {
            for (Arena.Replica r : replicas) {
                r.pose(new Quaternionf(), new Vector3f(0f, -60f, 0f), 0.01f, 0);
            }
        }
        if (t == regrowAt) {
            for (Arena.Replica r : replicas) {
                r.pose(new Quaternionf().rotateX(0.4f), new Vector3f(0f, -30f, 0f), 1f, 0);
            }
            enc.score().sweep(rockTo, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1f, 0.6f, 1.2f, riseLen, 8);
        }
        if (t > regrowAt && t < regrowAt + riseLen) {
            float f = HMath.outCubic(HMath.window(t, regrowAt, regrowAt + riseLen));
            for (Arena.Replica r : replicas) {
                r.pose(new Quaternionf().rotateX(0.4f * (1f - f)), new Vector3f(0f, -30f * (1f - f), 0f), 1f, 2);
            }
        }
        if (t == regrowAt + riseLen) {
            for (int i = 0; i < sectors.size(); i++) {
                int[] sec = sectors.get(i);
                arena.restore(cells.get(i));
                arena.markSector(sec[0], sec[1], Arena.SectorState.INTACT);
            }
            enc.score().at(rockTo, Sound.BLOCK_STONE_PLACE, 1f, 0.6f);
            enc.score().at(rockTo, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.2f);
            return true;
        }
        return false;
    }

    private void flyRock(float f) {
        if (rock == null || rock.display() == null) {
            return;
        }
        // The rock waits in the belt, then falls along an arc on the last beat.
        float launch = HMath.window((int) (f * 100), 70, 100);
        Vector3f mid = new Vector3f(rockFrom).add(rockTo).mul(0.5f).add(0f, 6f, 0f);
        Vector3f at = HMath.bezier(rockFrom, mid, rockTo, HMath.inQuad(launch), new Vector3f());
        Quaternionf rot = new Quaternionf().rotateXYZ(t * 0.2f, t * 0.13f, 0f);
        stage.push(rock.display(), HeliosStage.cube(at, rock.size() * (1f + launch * 0.6f), rot), 2);
        if (launch > 0f) {
            HeliosStage.material(rock.display(), Material.MAGMA_BLOCK);
            HeliosStage.brightness(rock.display(), 15);
        }
    }

    private void drop() {
        Arena arena = enc.arena();
        for (int[] sec : sectors) {
            List<ArenaLayout.Cell> c = arena.sectorCells(sec[0], sec[1], false);
            cells.add(c);
            replicas.add(arena.replica(g, c, false));
            arena.remove(c);
            arena.markSector(sec[0], sec[1], Arena.SectorState.FALLEN);
        }
        for (Arena.Replica r : replicas) {
            r.pose(new Quaternionf(), new Vector3f(), 1f, 0);
        }
        if (rock != null && rock.display() != null) {
            stage.push(rock.display(), HeliosStage.gone(rockTo), 2);
        }
        enc.score().at(rockTo, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        enc.score().at(rockTo, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.7f);
        enc.score().at(rockTo, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1f, 0.5f);
        enc.camera().shakeFrom(rockTo, 22f, 12);
        stage.blockDust(new Vector3f(rockTo).add(0f, 0.5f, 0f), Material.DEEPSLATE, 40, 2.5);
        double power = s.power("platform", 80);
        for (Player p : enc.fighters()) {
            if (stage.feet(p).distance(rockTo) < 3.2f) {
                enc.hit(p, power, "platform", 20, HeraldScript.away(rockTo, stage.feet(p), 0.7));
            }
        }
    }

    @Override
    protected void cleanup() {
        // Cut early (death, abort): put the floor back now so nobody is left over a hole.
        if (!cells.isEmpty() && t < regrowAt + riseLen) {
            for (int i = 0; i < sectors.size() && i < cells.size(); i++) {
                int[] sec = sectors.get(i);
                enc.arena().restore(cells.get(i));
                enc.arena().markSector(sec[0], sec[1], Arena.SectorState.INTACT);
            }
        }
    }
}
