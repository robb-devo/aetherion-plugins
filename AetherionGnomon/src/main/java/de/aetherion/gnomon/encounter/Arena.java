package de.aetherion.gnomon.encounter;

import de.aetherion.gnomon.math.Geo;
import de.aetherion.gnomon.render.Part;
import de.aetherion.gnomon.render.Parts;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The dial. A circle of floor the Gnomon owns: hour marks, a brass compass, an inner ring around its feet.
 * Pure displays (no world edits); it can draw itself in, turn, and fade.
 */
public final class Arena {

    private final World world;
    private final double cx;
    private final double cz;
    private final double floorY;
    private final double radius;
    private final double leaveRadius;

    private final List<Part> hours = new ArrayList<>();
    private final List<Part> rim = new ArrayList<>();
    private final List<Part> inner = new ArrayList<>();
    private double turn;
    private int revealed;

    public Arena(World world, double cx, double cz, double floorY, double radius, double leaveRadius) {
        this.world = world;
        this.cx = cx;
        this.cz = cz;
        this.floorY = floorY;
        this.radius = radius;
        this.leaveRadius = leaveRadius;
    }

    /**
     * Finds the walkable floor under {@code at}: the first solid block scanning down from a little above.
     *
     * @return floor surface Y (top of the block), or NaN if nothing solid within 24 blocks
     */
    public static double findFloor(Location at) {
        World w = at.getWorld();
        if (w == null) {
            return Double.NaN;
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int top = Math.min(w.getMaxHeight() - 1, at.getBlockY() + 2);
        int bottom = Math.max(w.getMinHeight(), at.getBlockY() - 24);
        for (int y = top; y >= bottom; y--) {
            Block b = w.getBlockAt(x, y, z);
            if (b.getType().isSolid()) {
                return y + 1.0;
            }
        }
        return Double.NaN;
    }

    public World world() {
        return world;
    }

    public double cx() {
        return cx;
    }

    public double cz() {
        return cz;
    }

    public double floorY() {
        return floorY;
    }

    public double radius() {
        return radius;
    }

    public double leaveRadius() {
        return leaveRadius;
    }

    public Location center() {
        return new Location(world, cx, floorY, cz);
    }

    public boolean inside(double x, double z) {
        return Geo.dist(x, z, cx, cz) <= radius;
    }

    public double distance(double x, double z) {
        return Geo.dist(x, z, cx, cz);
    }

    /** Clamps a point to stay {@code margin} inside the dial. */
    public double[] clamp(double x, double z, double margin) {
        double r = Geo.dist(x, z, cx, cz);
        double max = Math.max(1, radius - margin);
        if (r <= max) {
            return new double[]{x, z};
        }
        double f = max / r;
        return new double[]{cx + (x - cx) * f, cz + (z - cz) * f};
    }

    /* ================================================================== the dial */

    public void build(Parts parts) {
        for (int i = 0; i < 12; i++) {
            boolean cardinal = i % 3 == 0;
            hours.add(parts.spawn(cardinal ? Material.WAXED_CUT_COPPER : Material.CALCITE, cardinal ? 8 : 10));
        }
        for (int i = 0; i < 40; i++) {
            rim.add(parts.spawn(Material.WHITE_CONCRETE, 6));
        }
        for (int i = 0; i < 16; i++) {
            inner.add(parts.spawn(Material.CALCITE, 7));
        }
    }

    /** Draws the first {@code n} hour marks (intro: the dial writes itself). */
    public void reveal(int n, int interp) {
        revealed = Math.max(revealed, Math.min(12, n));
        pose(interp);
    }

    public void revealAll(int interp) {
        revealed = 12;
        pose(interp);
    }

    /** Turns the whole dial by {@code radians} (the hour changes). */
    public void turn(double radians, int interp) {
        turn += radians;
        pose(interp);
    }

    public void fade(int interp) {
        for (Part p : hours) {
            p.hide(interp);
        }
        for (Part p : rim) {
            p.hide(interp);
        }
        for (Part p : inner) {
            p.hide(interp);
        }
    }

    private void pose(int interp) {
        double markR = radius - 2.2;
        for (int i = 0; i < hours.size(); i++) {
            Part p = hours.get(i);
            if (i >= revealed) {
                p.hide(0);
                continue;
            }
            boolean cardinal = i % 3 == 0;
            double a = turn + i * Geo.TAU / 12 - Math.PI / 2;
            double len = cardinal ? 3.4 : 2.2;
            double w = cardinal ? 0.7 : 0.42;
            p.plate(cx + Math.cos(a) * markR, floorY + 0.012, cz + Math.sin(a) * markR, len, w, 0.03, a, interp);
        }
        if (revealed >= 12) {
            for (int i = 0; i < rim.size(); i++) {
                double a0 = turn + i * Geo.TAU / rim.size();
                double a1 = turn + (i + 1) * Geo.TAU / rim.size();
                double am = (a0 + a1) / 2;
                double chord = 2 * radius * Math.sin((a1 - a0) / 2) * 1.03;
                rim.get(i).plate(cx + Math.cos(am) * radius, floorY + 0.012, cz + Math.sin(am) * radius,
                        chord, 0.16, 0.02, am + Math.PI / 2, interp);
            }
            double ir = 3.5;
            for (int i = 0; i < inner.size(); i++) {
                double a0 = -turn + i * Geo.TAU / inner.size();
                double a1 = -turn + (i + 1) * Geo.TAU / inner.size();
                double am = (a0 + a1) / 2;
                double chord = 2 * ir * Math.sin((a1 - a0) / 2) * 0.7;
                inner.get(i).plate(cx + Math.cos(am) * ir, floorY + 0.012, cz + Math.sin(am) * ir,
                        chord, 0.12, 0.02, am + Math.PI / 2, interp);
            }
        }
    }
}
