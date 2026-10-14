package de.aetherion.gnomon.fx;

import de.aetherion.gnomon.math.Geo;
import de.aetherion.gnomon.render.Part;
import de.aetherion.gnomon.render.Parts;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Floor telegraphs ("shadows"). Every shape is drawn with the exact geometry its hit test uses.
 *
 * <p>Language: a {@link Style#FORMING} shadow is dark glass inside a chalk outline - it grows as the windup runs, and
 * when the fill touches the outline the hit lands. {@link Style#COMMITTED} turns it crimson: decided, no more
 * tracking. {@link Style#FEINT} is pale. {@link Style#GOLD} marks openings. {@link Style#INK} is lingering denial.
 */
public final class Shapes {

    public enum Style {
        FORMING(Material.BLACK_STAINED_GLASS, 10, Material.WHITE_CONCRETE, 13),
        COMMITTED(Material.RED_STAINED_GLASS, 15, Material.RED_CONCRETE, 15),
        FEINT(Material.LIGHT_GRAY_STAINED_GLASS, 6, Material.LIGHT_GRAY_STAINED_GLASS, 8),
        GOLD(Material.YELLOW_STAINED_GLASS, 15, Material.GOLD_BLOCK, 15),
        INK(Material.BLACK_CONCRETE, 3, Material.NETHER_WART_BLOCK, 11),
        CHALK(Material.WHITE_STAINED_GLASS, 13, Material.WHITE_CONCRETE, 14);

        final Material fill;
        final int fillLight;
        final Material line;
        final int lineLight;

        Style(Material fill, int fillLight, Material line, int lineLight) {
            this.fill = fill;
            this.fillLight = fillLight;
            this.line = line;
            this.lineLight = lineLight;
        }
    }

    private static final double FILL_Y = 0.03;
    private static final double LINE_Y = 0.055;
    private static final double LINE_W = 0.14;
    private static final double THIN = 0.02;

    private final Parts parts;
    private final double floorY;
    private final List<Shape> live = new ArrayList<>();

    public Shapes(Parts parts, double floorY) {
        this.parts = parts;
        this.floorY = floorY;
    }

    public void clearAll() {
        for (Shape s : new ArrayList<>(live)) {
            s.remove();
        }
        live.clear();
    }

    public int count() {
        return live.size();
    }

    /* ================================================================== base */

    public abstract class Shape {
        protected final List<Part> fills = new ArrayList<>();
        protected final List<Part> lines = new ArrayList<>();
        protected Style style;
        protected double grow = 1.0;
        private boolean removed;

        Shape(Style style) {
            this.style = style;
            live.add(this);
        }

        protected Part fillPart() {
            Part p = parts.spawn(style.fill, style.fillLight);
            if (p != null) {
                fills.add(p);
            }
            return p;
        }

        protected Part linePart() {
            Part p = parts.spawn(style.line, style.lineLight);
            if (p != null) {
                lines.add(p);
            }
            return p;
        }

        public Style style() {
            return style;
        }

        public void style(Style next) {
            if (next == style || removed) {
                return;
            }
            style = next;
            for (Part p : fills) {
                p.block(next.fill.createBlockData());
                p.brightness(next.fillLight);
            }
            for (Part p : lines) {
                p.block(next.line.createBlockData());
                p.brightness(next.lineLight);
            }
        }

        /** Fill fraction 0..1 (the timing tell), reached over {@code interp} ticks. */
        public void grow(double fraction, int interp) {
            grow = Math.max(0, Math.min(1, fraction));
            redraw(interp);
        }

        public abstract void redraw(int interp);

        public boolean removed() {
            return removed;
        }

        public void remove() {
            if (removed) {
                return;
            }
            removed = true;
            for (Part p : fills) {
                parts.remove(p);
            }
            for (Part p : lines) {
                parts.remove(p);
            }
            fills.clear();
            lines.clear();
            live.remove(this);
        }
    }

    /* ================================================================== circle */

    public final class Circle extends Shape {
        private double x;
        private double z;
        private final double r;
        private final boolean outline;

        Circle(double x, double z, double r, Style style, boolean outline, int fillSquares) {
            super(style);
            this.x = x;
            this.z = z;
            this.r = r;
            this.outline = outline;
            for (int i = 0; i < fillSquares; i++) {
                fillPart();
            }
            if (outline) {
                for (int i = 0; i < 16; i++) {
                    linePart();
                }
            }
            redraw(0);
        }

        public double x() {
            return x;
        }

        public double z() {
            return z;
        }

        public double r() {
            return r;
        }

        public void move(double nx, double nz, int interp) {
            x = nx;
            z = nz;
            redraw(interp);
        }

        @Override
        public void redraw(int interp) {
            // Inscribed squares (corners on the circle) at even rotations: a sunburst that never pokes out.
            double side = r * Math.sqrt(2) * grow;
            int n = fills.size();
            for (int i = 0; i < n; i++) {
                double a = i * (Math.PI / 2) / n;
                fills.get(i).plate(x, floorY + FILL_Y + i * 0.002, z, side, side, THIN, a, interp);
            }
            if (outline) {
                for (int i = 0; i < lines.size(); i++) {
                    double a0 = i * Geo.TAU / lines.size();
                    double a1 = (i + 1) * Geo.TAU / lines.size();
                    double mx = x + Math.cos((a0 + a1) / 2) * r;
                    double mz = z + Math.sin((a0 + a1) / 2) * r;
                    double chord = 2 * r * Math.sin((a1 - a0) / 2) * 1.04;
                    double tangent = (a0 + a1) / 2 + Math.PI / 2;
                    lines.get(i).plate(mx, floorY + LINE_Y, mz, chord, LINE_W, THIN, tangent, interp);
                }
            }
        }
    }

    public Circle circle(double x, double z, double r, Style style) {
        return new Circle(x, z, r, style, true, 4);
    }

    /** Cheap circle (2 fill squares, no outline) for many simultaneous markers. */
    public Circle dot(double x, double z, double r, Style style) {
        return new Circle(x, z, r, style, false, 2);
    }

    /* ================================================================== ring */

    public final class Ring extends Shape {
        private final double x;
        private final double z;
        private final double inner;
        private final double outer;

        Ring(double x, double z, double inner, double outer, Style style, int segments) {
            super(style);
            this.x = x;
            this.z = z;
            this.inner = inner;
            this.outer = outer;
            for (int i = 0; i < segments; i++) {
                fillPart();
            }
            redraw(0);
        }

        @Override
        public void redraw(int interp) {
            double mid = (inner + outer) / 2;
            double width = (outer - inner) * grow;
            int n = fills.size();
            for (int i = 0; i < n; i++) {
                double a0 = i * Geo.TAU / n;
                double a1 = (i + 1) * Geo.TAU / n;
                double am = (a0 + a1) / 2;
                double chord = 2 * mid * Math.sin((a1 - a0) / 2) * 1.08;
                fills.get(i).plate(x + Math.cos(am) * mid, floorY + FILL_Y, z + Math.sin(am) * mid,
                        chord, Math.max(0.05, width), THIN, am + Math.PI / 2, interp);
            }
        }
    }

    public Ring ring(double x, double z, double inner, double outer, Style style) {
        int segments = (int) Math.max(12, Math.min(28, Math.round(outer * 3)));
        return new Ring(x, z, inner, outer, style, segments);
    }

    /* ================================================================== line / hand */

    /** A band from A along {@code angle} for {@code length}, {@code halfWidth} each side. Grows from A. */
    public final class Band extends Shape {
        private double ax;
        private double az;
        private double angle;
        private double from;
        private double length;
        private final double halfWidth;

        Band(double ax, double az, double angle, double from, double length, double halfWidth, Style style) {
            super(style);
            this.ax = ax;
            this.az = az;
            this.angle = angle;
            this.from = from;
            this.length = length;
            this.halfWidth = halfWidth;
            fillPart();
            linePart();
            linePart();
            redraw(0);
        }

        public double angle() {
            return angle;
        }

        public void set(double ax, double az, double angle, double from, double length, int interp) {
            this.ax = ax;
            this.az = az;
            this.angle = angle;
            this.from = from;
            this.length = length;
            redraw(interp);
        }

        public void angle(double angle, int interp) {
            this.angle = angle;
            redraw(interp);
        }

        @Override
        public void redraw(int interp) {
            if (fills.isEmpty() || lines.size() < 2) {
                return;
            }
            double ux = Math.cos(angle);
            double uz = Math.sin(angle);
            double len = length * grow;
            double cx = ax + ux * (from + len / 2);
            double cz = az + uz * (from + len / 2);
            fills.get(0).plate(cx, floorY + FILL_Y, cz, Math.max(0.01, len), halfWidth * 2, THIN, angle, interp);
            double fx = ax + ux * (from + length / 2);
            double fz = az + uz * (from + length / 2);
            double px = -uz * halfWidth;
            double pz = ux * halfWidth;
            lines.get(0).plate(fx + px, floorY + LINE_Y, fz + pz, length, LINE_W, THIN, angle, interp);
            lines.get(1).plate(fx - px, floorY + LINE_Y, fz - pz, length, LINE_W, THIN, angle, interp);
        }
    }

    public Band band(double ax, double az, double angle, double from, double length, double halfWidth, Style style) {
        return new Band(ax, az, angle, from, length, halfWidth, style);
    }

    /* ================================================================== sector */

    public final class Sector extends Shape {
        private final double x;
        private final double z;
        private final double from;
        private final double span;
        private final double inner;
        private final double outer;

        Sector(double x, double z, double from, double span, double inner, double outer, Style style) {
            super(style);
            this.x = x;
            this.z = z;
            this.from = from;
            this.span = span;
            this.inner = inner;
            this.outer = outer;
            int wedges = Math.max(4, (int) Math.round(span / Math.toRadians(7.5)));
            for (int i = 0; i < wedges; i++) {
                fillPart();
            }
            for (int i = 0; i < 2 + 6; i++) {
                linePart();
            }
            redraw(0);
        }

        @Override
        public void redraw(int interp) {
            int n = fills.size();
            double reach = inner + (outer - inner) * grow;
            for (int i = 0; i < n; i++) {
                double a = from + (i + 0.5) * span / n;
                double mid = (inner + reach) / 2;
                double width = 2 * reach * Math.sin(span / n / 2) * 1.1;
                fills.get(i).plate(x + Math.cos(a) * mid, floorY + FILL_Y, z + Math.sin(a) * mid,
                        Math.max(0.01, reach - inner), width, THIN, a, interp);
            }
            // two radial edges + 6 outer chords
            if (lines.size() < 8) {
                return;
            }
            for (int e = 0; e < 2; e++) {
                double a = from + e * span;
                double mid = (inner + outer) / 2;
                lines.get(e).plate(x + Math.cos(a) * mid, floorY + LINE_Y, z + Math.sin(a) * mid,
                        outer - inner, LINE_W, THIN, a, interp);
            }
            for (int i = 0; i < 6; i++) {
                double a0 = from + i * span / 6;
                double a1 = from + (i + 1) * span / 6;
                double am = (a0 + a1) / 2;
                double chord = 2 * outer * Math.sin((a1 - a0) / 2) * 1.04;
                lines.get(2 + i).plate(x + Math.cos(am) * outer, floorY + LINE_Y, z + Math.sin(am) * outer,
                        chord, LINE_W, THIN, am + Math.PI / 2, interp);
            }
        }
    }

    public Sector sector(double x, double z, double from, double span, double inner, double outer, Style style) {
        return new Sector(x, z, from, span, inner, outer, style);
    }

    /* ================================================================== marks */

    /** A chevron pointing along {@code angle}: "it expects you to go here". */
    public final class Chevron extends Shape {
        private double x;
        private double z;
        private double angle;
        private final double size;

        Chevron(double x, double z, double angle, double size, Style style) {
            super(style);
            this.x = x;
            this.z = z;
            this.angle = angle;
            this.size = size;
            linePart();
            linePart();
            redraw(0);
        }

        public void move(double nx, double nz, double nangle, int interp) {
            x = nx;
            z = nz;
            angle = nangle;
            redraw(interp);
        }

        @Override
        public void redraw(int interp) {
            double s = size * grow;
            for (int k = 0; k < lines.size() && k < 2; k++) {
                double arm = angle + (k == 0 ? 1 : -1) * Math.toRadians(140);
                double mx = x + Math.cos(arm) * s * 0.5;
                double mz = z + Math.sin(arm) * s * 0.5;
                lines.get(k).plate(mx, floorY + LINE_Y + 0.01, mz, Math.max(0.01, s), 0.22, THIN, arm, interp);
            }
        }
    }

    public Chevron chevron(double x, double z, double angle, double size, Style style) {
        return new Chevron(x, z, angle, size, style);
    }
}
