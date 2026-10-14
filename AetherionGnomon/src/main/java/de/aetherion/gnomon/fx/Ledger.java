package de.aetherion.gnomon.fx;

import de.aetherion.gnomon.math.Geo;
import de.aetherion.gnomon.render.Part;
import de.aetherion.gnomon.render.Parts;

import org.bukkit.Color;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The floor remembers. Every participant's route is drawn as glowing chalk (what the Gnomon observed); in Phase II the
 * whole ledger turns to ink; at death it lifts off the floor and dissolves upward.
 */
public final class Ledger {

    private static final double WIDTH = 0.11;
    private static final double THICK = 0.015;
    private static final double Y = 0.02;

    private static final class Seg {
        final UUID owner;
        final Part part;
        final double ax;
        final double az;
        final double bx;
        final double bz;
        boolean ink;
        int flareUntil;

        Seg(UUID owner, Part part, double ax, double az, double bx, double bz) {
            this.owner = owner;
            this.part = part;
            this.ax = ax;
            this.az = az;
            this.bx = bx;
            this.bz = bz;
        }
    }

    private final Parts parts;
    private final double floorY;
    private final int max;
    private final int perPlayer;
    private final Deque<Seg> segs = new ArrayDeque<>();
    private final Map<UUID, double[]> last = new HashMap<>();
    private boolean inkMode;
    private boolean frozen;
    private int clock;

    public Ledger(Parts parts, double floorY, int max, int perPlayer) {
        this.parts = parts;
        this.floorY = floorY;
        this.max = Math.max(10, max);
        this.perPlayer = Math.max(5, perPlayer);
    }

    public int size() {
        return segs.size();
    }

    /** New route point for a player: draws a chalk (or ink) line from their previous point. */
    public void point(UUID owner, double x, double z) {
        if (frozen) {
            return;
        }
        double[] prev = last.put(owner, new double[]{x, z});
        if (prev == null) {
            return;
        }
        double len = Geo.dist(prev[0], prev[1], x, z);
        if (len < 0.3 || len > 9) {
            return;
        }
        Material m = inkMode ? Material.NETHER_WART_BLOCK : Material.WHITE_CONCRETE;
        Part p = parts.spawn(m, inkMode ? 9 : 12);
        if (p == null) {
            return;
        }
        Seg s = new Seg(owner, p, prev[0], prev[1], x, z);
        s.ink = inkMode;
        draw(s, 0);
        segs.addLast(s);
        trim(owner);
    }

    /** Lifts the pen (respawn / teleport) so no line jumps across the arena. */
    public void lift(UUID owner) {
        last.remove(owner);
    }

    private void draw(Seg s, int interp) {
        double cx = (s.ax + s.bx) / 2;
        double cz = (s.az + s.bz) / 2;
        double len = Geo.dist(s.ax, s.az, s.bx, s.bz) + WIDTH;
        double a = Geo.angle(s.bx - s.ax, s.bz - s.az);
        s.part.plate(cx, floorY + Y, cz, len, WIDTH, THICK, a, interp);
    }

    private void trim(UUID owner) {
        int mine = 0;
        for (Seg s : segs) {
            if (s.owner.equals(owner)) {
                mine++;
            }
        }
        if (mine > perPlayer) {
            for (Iterator<Seg> it = segs.iterator(); it.hasNext(); ) {
                Seg s = it.next();
                if (s.owner.equals(owner)) {
                    parts.remove(s.part);
                    it.remove();
                    break;
                }
            }
        }
        while (segs.size() > max) {
            Seg s = segs.removeFirst();
            parts.remove(s.part);
        }
    }

    /** A tell: this player's trail flares - "this is what I saw you do". */
    public void flare(UUID owner, int ticks) {
        for (Seg s : segs) {
            if (s.owner.equals(owner)) {
                s.part.brightness(15);
                s.part.glow(s.ink ? Color.fromRGB(0xC0, 0x20, 0x30) : Color.WHITE);
                s.flareUntil = clock + ticks;
            }
        }
    }

    /** Forgotten: this player's trail dims a little. */
    public void dim(UUID owner) {
        for (Seg s : segs) {
            if (s.owner.equals(owner) && !s.ink) {
                s.part.brightness(6);
                s.flareUntil = clock + 60;
            }
        }
    }

    public void tick() {
        clock++;
        if (clock % 5 != 0) {
            return;
        }
        for (Seg s : segs) {
            if (s.flareUntil > 0 && clock >= s.flareUntil) {
                s.flareUntil = 0;
                s.part.glow(null);
                s.part.brightness(s.ink ? 9 : 12);
            }
        }
    }

    /**
     * Turns up to {@code count} chalk segments into ink (oldest first). Returns how many changed - lets the transition
     * spread the ink over several ticks.
     */
    public int inkSome(int count) {
        inkMode = true;
        int done = 0;
        for (Seg s : segs) {
            if (done >= count) {
                break;
            }
            if (!s.ink) {
                s.ink = true;
                s.part.block(Material.NETHER_WART_BLOCK.createBlockData());
                s.part.brightness(15);
                s.flareUntil = clock + 20;
                done++;
            }
        }
        return done;
    }

    /** Death: every line rises and shrinks to nothing over {@code ticks}. */
    public void ascend(int ticks) {
        frozen = true;
        for (Seg s : segs) {
            double cx = (s.ax + s.bx) / 2;
            double cz = (s.az + s.bz) / 2;
            double a = Geo.angle(s.bx - s.ax, s.bz - s.az);
            double rise = 4 + Math.abs((cx * 13 + cz * 7) % 5);
            s.part.glow(null);
            s.part.brightness(15);
            s.part.box(new Vector3d(cx, floorY + rise, cz), new Quaternionf().rotationY((float) -a), 0.0, 0.0, 0.0, ticks);
        }
    }

    /** Route points currently drawn for a player, oldest first (for marks / debugging). */
    public List<double[]> route(UUID owner) {
        List<double[]> out = new ArrayList<>();
        for (Seg s : segs) {
            if (s.owner.equals(owner)) {
                out.add(new double[]{s.bx, s.bz});
            }
        }
        return out;
    }

    public void clear() {
        for (Seg s : segs) {
            parts.remove(s.part);
        }
        segs.clear();
        last.clear();
    }
}
