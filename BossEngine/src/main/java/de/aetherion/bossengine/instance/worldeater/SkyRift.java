package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.BlockDisplay;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The tear in the sky Nihil comes through: a jagged vertical slit that shows the starfield of
 * the void behind the world, even in broad daylight.
 *
 * <p>At the site it is made of real end gateway blocks, placed only inside
 * {@link SiteLayout#RIFT}, so a rebuild always erases it. Anywhere else it is a stack of display
 * slabs (black core, violet rim) and the world is never touched.
 */
final class SkyRift {

    private final WeFx fx;
    private final SiteTerrain terrain;
    private final List<int[]> cells = new ArrayList<>();
    private final List<float[]> rows = new ArrayList<>();
    private final List<BlockDisplay> slabs = new ArrayList<>();
    private final Vector3f center;
    private int shown;

    /**
     * @param center stage-space center of the tear
     * @param height rows, top to bottom
     * @param maxHalf widest half-width
     */
    SkyRift(WeFx fx, SiteTerrain terrain, Vector3f center, int height, float maxHalf) {
        this.fx = fx;
        this.terrain = terrain;
        this.center = new Vector3f(center);
        Location c = fx.at(center);
        int cx = c.getBlockX();
        int cy = c.getBlockY();
        int cz = c.getBlockZ();
        for (int i = 0; i < height; i++) {
            float t = (i + 0.5f) / height;
            int dy = i - height / 2;
            float half = maxHalf * (float) Math.pow(Math.sin(t * WeMath.PI), 0.8) + 0.35f;
            float offset = (WeMath.noise2(i / 4.5f, 0.5f, 71) * 2f - 1f) * 1.7f;
            rows.add(new float[]{dy, offset, half});
            for (int x = Math.round(offset - half); x <= Math.round(offset + half); x++) {
                cells.add(new int[]{cx + x, cy + dy, cz, Math.abs(dy) * 10 + Math.abs(x - Math.round(offset)) * 24});
            }
        }
        // Opens from the middle out: the tear runs up and down first, then widens.
        cells.sort(Comparator.comparingInt(a -> a[3]));
        if (terrain == null) {
            for (float[] r : rows) {
                BlockDisplay core = fx.block(Material.BLACK_CONCRETE, null, 15);
                BlockDisplay rim = fx.block(Material.PURPLE_STAINED_GLASS, WeProps.VOID, 15);
                slabs.add(core);
                slabs.add(rim);
            }
        }
    }

    Vector3f center() {
        return new Vector3f(center);
    }

    /** Opens (or closes) the tear to {@code fraction} of its full size. */
    void open(float fraction) {
        fraction = WeMath.clamp01(fraction);
        if (terrain != null) {
            int want = Math.round(cells.size() * fraction);
            while (shown < want) {
                place(cells.get(shown++));
            }
            while (shown > want) {
                int[] c = cells.get(--shown);
                terrain.clear(c[0], c[1], c[2]);
            }
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            float[] r = rows.get(i);
            float rowT = 1f - Math.abs(r[0]) / (rows.size() * 0.5f + 1f);
            float f = WeMath.clamp01((fraction - (1f - rowT) * 0.6f) / 0.4f);
            float half = r[2] * f;
            Vector3f a = new Vector3f(center.x + r[1] - half, center.y + r[0], center.z);
            Vector3f b = new Vector3f(center.x + r[1] + half, center.y + r[0], center.z);
            BlockDisplay core = slabs.get(i * 2);
            BlockDisplay rim = slabs.get(i * 2 + 1);
            if (f <= 0.01f) {
                WeFx.push(core, WeFx.gone(new Vector3f(a).add(b).mul(0.5f)), 3);
                WeFx.push(rim, WeFx.gone(new Vector3f(a).add(b).mul(0.5f)), 3);
                continue;
            }
            Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
            WeFx.push(core, WeFx.box(mid, new Vector3f(half * 2f + 0.05f, 1.02f, 0.4f)), 3);
            WeFx.push(rim, WeFx.box(mid, new Vector3f(half * 2f + 0.7f, 1.2f, 0.25f)), 3);
        }
    }

    private void place(int[] c) {
        if (!SiteLayout.RIFT.contains(c[0], c[1], c[2])) {
            return;
        }
        World w = terrain.world();
        if (w == null) {
            return;
        }
        Block b = w.getBlockAt(c[0], c[1], c[2]);
        b.setType(Material.END_GATEWAY, false);
        if (b.getState() instanceof EndGateway gateway) {
            // Old enough that no beam shows: only the starfield.
            gateway.setAge(2400L);
            gateway.update(true, false);
        }
    }

    /** A random point on the tear (for particles and sound sources). */
    Vector3f somePoint(java.util.Random r) {
        if (rows.isEmpty()) {
            return center();
        }
        float[] row = rows.get(r.nextInt(rows.size()));
        return new Vector3f(center.x + row[1] + (r.nextFloat() * 2f - 1f) * row[2], center.y + row[0], center.z);
    }

    void remove() {
        if (terrain != null) {
            for (int i = 0; i < shown; i++) {
                int[] c = cells.get(i);
                terrain.clear(c[0], c[1], c[2]);
            }
            shown = 0;
        }
        for (BlockDisplay d : slabs) {
            fx.kill(d);
        }
        slabs.clear();
    }
}
