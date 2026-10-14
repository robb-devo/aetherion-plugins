package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.ai.Brain;
import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.Read;
import de.aetherion.gnomon.encounter.Attack;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.encounter.Hazard;
import de.aetherion.gnomon.fx.Shapes;
import de.aetherion.gnomon.geom.Zone;
import de.aetherion.gnomon.math.Geo;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * ZONE / DENY. "The arena responds because the boss decided to deny your corner."
 *
 * <ul>
 *   <li>Camper: ink floods the slice of the dial you keep returning to and stays there, burning.</li>
 *   <li>Orbiter / kiter: a wall of blackstone rises across your route, just ahead of where you are circling.</li>
 * </ul>
 * Both outlive the move (hazards) and are given back cleanly: the wall is journaled real blocks, the ink is displays.
 */
public final class Interdict extends Attack {

    private enum Mode {
        INK, WALL
    }

    private Mode mode;
    private Shapes.Sector sector;
    private Shapes.Band wallLine;
    private int windup;
    private boolean handedOff;
    // ink
    private double from;
    private double span;
    // wall
    private double wx1;
    private double wz1;
    private double wx2;
    private double wz2;

    public Interdict(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        Player f = e.player(d.target());
        if (f == null) {
            f = e.focus();
        }
        windup = e.windup(e.cfg().telegraph("interdict", 30));
        mode = d.exploit() == Read.CAMPER ? Mode.INK : Mode.WALL;
        if (mode == Mode.WALL && f == null) {
            mode = Mode.INK;
        }
        double cx = e.arena().cx();
        double cz = e.arena().cz();
        if (mode == Mode.INK) {
            int s = d.sector();
            if (s < 0 && f != null) {
                s = Brain.sectorOf(Geo.angle(f.getLocation().getX() - cx, f.getLocation().getZ() - cz));
            }
            s = Math.max(0, s);
            span = Geo.TAU / 8;
            from = s * span;
            sector = e.shapes().sector(cx, cz, from, span, 5.0, e.arena().radius(), Shapes.Style.FORMING);
            sector.grow(0.05, 0);
            sector.grow(1.0, windup);
            double mid = from + span / 2;
            e.rig().needle(Math.toRadians(40), mid + Math.PI);
            e.rig().look(new Vector3d(cx + Math.cos(mid) * 14, e.floorY(), cz + Math.sin(mid) * 14));
            e.fx().sound(cx + Math.cos(mid) * 14, e.floorY(), cz + Math.sin(mid) * 14, Sound.BLOCK_SCULK_CATALYST_BLOOM, 2f, 0.5f);
        } else {
            double hx = e.rig().hubX();
            double hz = e.rig().hubZ();
            double px = f.getLocation().getX();
            double pz = f.getLocation().getZ();
            int sign = d.exploit() == Read.ORBIT ? d.sign() : e.brain().sign(f.getUniqueId(), Read.ORBIT);
            if (sign == 0) {
                sign = Math.random() < 0.5 ? 1 : -1;
            }
            double r = Math.max(5, Geo.dist(px, pz, hx, hz));
            double a = Geo.angle(px - hx, pz - hz) + sign * (5.5 / r);
            double r1 = Math.max(4.0, r - 3.5);
            double r2 = r + 3.5;
            wx1 = hx + Math.cos(a) * r1;
            wz1 = hz + Math.sin(a) * r1;
            wx2 = hx + Math.cos(a) * r2;
            wz2 = hz + Math.sin(a) * r2;
            double[] c2 = e.arena().clamp(wx2, wz2, 0.5);
            wx2 = c2[0];
            wz2 = c2[1];
            double ang = Geo.angle(wx2 - wx1, wz2 - wz1);
            double len = Geo.dist(wx1, wz1, wx2, wz2);
            wallLine = e.shapes().band(wx1, wz1, ang, 0, len, 0.6, Shapes.Style.FORMING);
            wallLine.grow(0.05, 0);
            wallLine.grow(1.0, windup);
            e.rig().look(new Vector3d((wx1 + wx2) / 2, e.floorY(), (wz1 + wz2) / 2));
            e.rig().needle(Math.toRadians(30), a + Math.PI);
        }
        if (d.exploit() != null) {
            e.announceExploit(d, cx, cz);
        }
    }

    @Override
    public boolean tick() {
        if (t == windup - 8) {
            if (sector != null) {
                sector.style(Shapes.Style.COMMITTED);
            }
            if (wallLine != null) {
                wallLine.style(Shapes.Style.COMMITTED);
            }
            Vector3d h = e.rig().hub();
            e.fx().sound(h.x, h.y, h.z, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1.6f, 0.5f);
        }
        if (t >= windup && !handedOff) {
            handedOff = true;
            if (mode == Mode.INK) {
                sector.style(Shapes.Style.INK);
                e.addHazard(new InkPool(e, sector, new Zone.Sector(e.arena().cx(), e.arena().cz(), from, from + span, 5.0, e.arena().radius()), 170));
                sector = null;
            } else {
                Wall wall = new Wall(e, wx1, wz1, wx2, wz2, 220);
                e.addHazard(wall);
                if (wallLine != null) {
                    wallLine.remove();
                    wallLine = null;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    protected void cleanup() {
        if (sector != null) {
            sector.remove();
        }
        if (wallLine != null) {
            wallLine.remove();
        }
        e.rig().needleRest();
    }

    /* ================================================================== hazards */

    /** Burning ink on one slice of the dial. */
    static final class InkPool implements Hazard {
        private final Encounter e;
        private final Shapes.Sector shape;
        private final Zone zone;
        private final int life;
        private int t;

        InkPool(Encounter e, Shapes.Sector shape, Zone zone, int life) {
            this.e = e;
            this.shape = shape;
            this.zone = zone;
            this.life = life;
            e.fx().sound(zone.originX(), e.floorY(), zone.originZ(), Sound.ENTITY_GLOW_SQUID_SQUIRT, 2f, 0.5f);
        }

        @Override
        public boolean tick() {
            t++;
            if (t % 20 == 0) {
                e.strike(zone, e.cfg().damage("interdict-tick", 14), "ink", 18, false, 0);
            }
            if (t % 4 == 0) {
                double a = Math.random() * Geo.TAU;
                double r = Math.random();
                double x = zone.originX() + Math.cos(a) * r * 6;
                double z = zone.originZ() + Math.sin(a) * r * 6;
                if (zone.contains(x, z)) {
                    e.fx().particle(Particle.SQUID_INK, x, e.floorY() + 0.2, z, 2, 0.3, 0.01);
                }
            }
            return t >= life;
        }

        @Override
        public void end() {
            shape.remove();
        }
    }

    /** A blackstone wall across a route. Real blocks, journaled; only ever placed into air and never on a player. */
    static final class Wall implements Hazard {
        private static final BlockData BODY = Material.POLISHED_BLACKSTONE_BRICKS.createBlockData();
        private static final BlockData CAP = Material.GILDED_BLACKSTONE.createBlockData();

        private final Encounter e;
        private final List<List<Block>> rows = new ArrayList<>();
        private final List<Block> placed = new ArrayList<>();
        private final int life;
        private final double x1;
        private final double z1;
        private final double x2;
        private final double z2;
        private int t;
        private boolean ended;

        Wall(Encounter e, double x1, double z1, double x2, double z2, int life) {
            this.e = e;
            this.life = life;
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            World w = e.world();
            int floor = (int) Math.floor(e.floorY());
            double len = Geo.dist(x1, z1, x2, z2);
            List<int[]> columns = new ArrayList<>();
            int steps = Math.max(1, (int) Math.ceil(len));
            for (int i = 0; i <= steps; i++) {
                double k = i / (double) steps;
                int bx = (int) Math.floor(x1 + (x2 - x1) * k);
                int bz = (int) Math.floor(z1 + (z2 - z1) * k);
                boolean dup = false;
                for (int[] c : columns) {
                    if (c[0] == bx && c[1] == bz) {
                        dup = true;
                        break;
                    }
                }
                if (!dup && !occupied(bx, bz)) {
                    columns.add(new int[]{bx, bz});
                }
            }
            for (int y = 0; y < 3; y++) {
                List<Block> row = new ArrayList<>();
                for (int[] c : columns) {
                    row.add(w.getBlockAt(c[0], floor + y, c[1]));
                }
                rows.add(row);
            }
            // The rising line shoves whoever stands on it (columns under players were skipped).
            for (Player p : e.party().online()) {
                double d = Geo.distToSegment(p.getLocation().getX(), p.getLocation().getZ(), x1, z1, x2, z2);
                if (d < 1.2) {
                    double ang = Geo.angle(x2 - x1, z2 - z1) + Math.PI / 2;
                    double side = Math.signum((p.getLocation().getX() - x1) * Math.cos(ang) + (p.getLocation().getZ() - z1) * Math.sin(ang));
                    if (side == 0) {
                        side = 1;
                    }
                    e.hurt(p, e.cfg().damage("interdict-tick", 14) * 2, "wall", 20,
                            new Vector(Math.cos(ang) * side * 0.8, 0.4, Math.sin(ang) * side * 0.8));
                }
            }
        }

        private boolean occupied(int bx, int bz) {
            for (Player p : e.party().audience()) {
                double px = p.getLocation().getX();
                double pz = p.getLocation().getZ();
                if (px > bx - 0.35 && px < bx + 1.35 && pz > bz - 0.35 && pz < bz + 1.35) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean tick() {
            if (t < rows.size() * 2 && t % 2 == 0) {
                int i = t / 2;
                List<Block> row = rows.get(i);
                List<Block> safe = new ArrayList<>();
                for (Block b : row) {
                    if (!occupied(b.getX(), b.getZ())) {
                        safe.add(b);
                    }
                }
                placed.addAll(e.blocks().placeAll(safe, i == rows.size() - 1 ? CAP : BODY));
                double mx = (x1 + x2) / 2;
                double mz = (z1 + z2) / 2;
                e.fx().sound(mx, e.floorY() + i, mz, Sound.BLOCK_DEEPSLATE_PLACE, 1.6f, 0.6f);
                e.fx().dust(mx, e.floorY() + 0.2, mz, BODY, 20, 1.5);
                if (i == 0) {
                    e.fx().shakeNear(mx, mz, 10, 8);
                }
            }
            t++;
            return t >= life;
        }

        @Override
        public void end() {
            if (ended) {
                return;
            }
            ended = true;
            for (Block b : placed) {
                e.fx().dust(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, BODY, 6, 0.3);
            }
            if (!placed.isEmpty()) {
                Block b = placed.get(0);
                e.fx().sound(b.getX(), b.getY(), b.getZ(), Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.6f);
            }
            e.blocks().restore(placed);
            placed.clear();
        }
    }
}
