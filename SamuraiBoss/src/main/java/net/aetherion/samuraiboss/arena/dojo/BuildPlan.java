package net.aetherion.samuraiboss.arena.dojo;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ergebnis des Dojo-Generators: welche Blöcke wohin (relativ zum Ursprung), zu welchem Bauteil
 * sie gehören, welche Deko-Displays es gibt und wichtige Punkte (Boss-Start, Glocke …).
 *
 * <p>Komplett unabhängig vom Server – deterministisch aus dem Ursprung erzeugt. Dadurch kennt
 * das Plugin die Bauteile jederzeit, ohne sie speichern zu müssen.</p>
 */
public final class BuildPlan {

    public record Placement(String block, El element) {
    }

    /** Relative Grenzen des Arena-Bereichs (inklusive). */
    public final int minX, minY, minZ, maxX, maxY, maxZ;
    /** Spielbereich innerhalb der Mauern (inklusive). */
    public final int innerMinX, innerMinZ, innerMaxX, innerMaxZ;

    private final Map<Long, Placement> blocks = new LinkedHashMap<>();
    private final List<DecoSpec> deco = new ArrayList<>();
    private final Map<String, Vector3f> points = new HashMap<>();
    /** Bereich, der vor dem Bauen geleert wird (über dem Boden). */
    public final int clearMinY, clearMaxY;

    public BuildPlan(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                     int innerMinX, int innerMinZ, int innerMaxX, int innerMaxZ, int clearMinY, int clearMaxY) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.innerMinX = innerMinX;
        this.innerMinZ = innerMinZ;
        this.innerMaxX = innerMaxX;
        this.innerMaxZ = innerMaxZ;
        this.clearMinY = clearMinY;
        this.clearMaxY = clearMaxY;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x + 1024) << 22) | ((long) (y + 1024) << 11) | (z + 1024);
    }

    public static int kx(long k) {
        return (int) ((k >> 22) & 0x7FF) - 1024;
    }

    public static int ky(long k) {
        return (int) ((k >> 11) & 0x7FF) - 1024;
    }

    public static int kz(long k) {
        return (int) (k & 0x7FF) - 1024;
    }

    public void set(int x, int y, int z, String block, El el) {
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return;
        if (!block.startsWith("minecraft:")) block = "minecraft:" + block;
        blocks.put(key(x, y, z), new Placement(block, el));
    }

    /** Setzt nur, wenn die Stelle noch frei ist (oder Luft). */
    public void setIfEmpty(int x, int y, int z, String block, El el) {
        Placement p = blocks.get(key(x, y, z));
        if (p == null || p.block.equals("minecraft:air")) set(x, y, z, block, el);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block, El el) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, block, el);
    }

    public void remove(int x, int y, int z) {
        blocks.remove(key(x, y, z));
    }

    public Placement get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public boolean isSolidAt(int x, int y, int z) {
        Placement p = get(x, y, z);
        return p != null && !p.block.equals("minecraft:air") && !p.block.contains("water");
    }

    public Map<Long, Placement> blocks() {
        return Collections.unmodifiableMap(blocks);
    }

    public void addDeco(DecoSpec d) {
        deco.add(d);
    }

    public List<DecoSpec> deco() {
        return Collections.unmodifiableList(deco);
    }

    public void point(String name, float x, float y, float z) {
        points.put(name, new Vector3f(x, y, z));
    }

    public Vector3f point(String name) {
        Vector3f v = points.get(name);
        return v == null ? new Vector3f() : new Vector3f(v);
    }

    public Map<String, Vector3f> points() {
        return Collections.unmodifiableMap(points);
    }

    public boolean insideWalls(double x, double z) {
        return x >= innerMinX && x <= innerMaxX + 1 && z >= innerMinZ && z <= innerMaxZ + 1;
    }
}
