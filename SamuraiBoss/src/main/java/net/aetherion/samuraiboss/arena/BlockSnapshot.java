package net.aetherion.samuraiboss.arena;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Exakte Kopie eines Quader-Bereichs: jede Position mit ihrer vollständigen BlockData.
 *
 * <p>Speicherformat (GZIP): Palette (eindeutige Block-Strings) + pro Block ein Palettenindex.
 * Ein 55×33×63-Bereich (~115.000 Blöcke) braucht so nur wenige KB auf der Platte.</p>
 *
 * <p>Tile-Entities (Schilder, Kisten …) werden zusätzlich als BlockState im Speicher gehalten
 * und beim Wiederherstellen zurückgeschrieben. (Nach einem Server-Absturz wird der Block selbst
 * exakt wiederhergestellt, Container-Inhalte nur, solange der Server lief.)</p>
 */
public final class BlockSnapshot {

    private static final int MAGIC = 0x53425331; // "SBS1"

    public final String worldName;
    public final int minX, minY, minZ, sx, sy, sz;
    private final List<String> palette;
    private final int[] indices;
    private transient BlockData[] paletteData;
    private final transient Map<Integer, BlockState> tiles = new HashMap<>();

    private BlockSnapshot(String worldName, int minX, int minY, int minZ, int sx, int sy, int sz,
                          List<String> palette, int[] indices) {
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = palette;
        this.indices = indices;
    }

    public int size() {
        return indices.length;
    }

    public int index(int x, int y, int z) {
        return ((x - minX) * sz + (z - minZ)) * sy + (y - minY);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x < minX + sx && y >= minY && y < minY + sy && z >= minZ && z < minZ + sz;
    }

    public int x(int i) {
        return minX + i / (sy * sz);
    }

    public int z(int i) {
        return minZ + (i / sy) % sz;
    }

    public int y(int i) {
        return minY + i % sy;
    }

    /** BlockData an einer Stelle (Haupt-Thread, nutzt eine Palette-Cache). */
    public BlockData data(int i) {
        if (paletteData == null) {
            paletteData = new BlockData[palette.size()];
            for (int p = 0; p < palette.size(); p++) {
                try {
                    paletteData[p] = Bukkit.createBlockData(palette.get(p));
                } catch (IllegalArgumentException ex) {
                    paletteData[p] = Bukkit.createBlockData("minecraft:air");
                }
            }
        }
        return paletteData[indices[i]];
    }

    public BlockData dataAt(int x, int y, int z) {
        return contains(x, y, z) ? data(index(x, y, z)) : null;
    }

    public String stringAt(int x, int y, int z) {
        return contains(x, y, z) ? palette.get(indices[index(x, y, z)]) : null;
    }

    public boolean isAir(int i) {
        String s = palette.get(indices[i]);
        return s.equals("minecraft:air") || s.equals("minecraft:cave_air") || s.equals("minecraft:void_air");
    }

    public BlockState tileAt(int i) {
        return tiles.get(i);
    }

    // ------------------------------------------------------------------ Aufnehmen

    /**
     * Nimmt den Bereich auf. Muss auf dem Haupt-Thread laufen (lädt Chunks bei Bedarf).
     * Die eigentliche Auswertung läuft über ChunkSnapshots und ist schnell.
     */
    public static BlockSnapshot capture(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        int[] idx = new int[sx * sy * sz];
        List<String> palette = new ArrayList<>();
        Map<BlockData, Integer> lookup = new HashMap<>();
        Map<Long, ChunkSnapshot> chunks = new HashMap<>();
        BlockSnapshot snap = new BlockSnapshot(world.getName(), minX, minY, minZ, sx, sy, sz, palette, idx);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int cx = x >> 4, cz = z >> 4;
                long ck = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
                ChunkSnapshot cs = chunks.get(ck);
                if (cs == null) {
                    cs = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                    chunks.put(ck, cs);
                }
                for (int y = minY; y <= maxY; y++) {
                    BlockData bd = cs.getBlockData(x & 15, y, z & 15);
                    Integer p = lookup.get(bd);
                    if (p == null) {
                        p = palette.size();
                        palette.add(bd.getAsString());
                        lookup.put(bd, p);
                    }
                    int i = snap.index(x, y, z);
                    idx[i] = p;
                }
            }
        }
        // Tile-Entities zusätzlich merken
        for (ChunkSnapshot cs : chunks.values()) {
            org.bukkit.Chunk ch = world.getChunkAt(cs.getX(), cs.getZ());
            for (BlockState st : ch.getTileEntities()) {
                int x = st.getX(), y = st.getY(), z = st.getZ();
                if (!snap.contains(x, y, z)) continue;
                if (st instanceof TileState) snap.tiles.put(snap.index(x, y, z), st);
            }
        }
        return snap;
    }

    // ------------------------------------------------------------------ Vergleichen & Schreiben

    /** true, wenn die Welt an Index i vom Snapshot abweicht. */
    public boolean differs(World w, int i) {
        Block b = w.getBlockAt(x(i), y(i), z(i));
        return !b.getBlockData().equals(data(i));
    }

    /** Stellt einen einzelnen Block exakt wieder her (ohne Physik). */
    public void restore(World w, int i) {
        Block b = w.getBlockAt(x(i), y(i), z(i));
        BlockData want = data(i);
        if (!b.getBlockData().equals(want)) b.setBlockData(want, false);
        BlockState tile = tiles.get(i);
        if (tile != null) {
            try {
                tile.update(true, false);
            } catch (RuntimeException ignored) {
                // z. B. wenn sich der Blocktyp geändert hat – Block selbst ist bereits korrekt
            }
        }
    }

    // ------------------------------------------------------------------ Datei

    public void save(File file) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        file.getParentFile().mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))))) {
            out.writeInt(MAGIC);
            out.writeUTF(worldName);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);
            out.writeInt(sx);
            out.writeInt(sy);
            out.writeInt(sz);
            out.writeInt(palette.size());
            for (String s : palette) out.writeUTF(s);
            boolean wide = palette.size() > 0xFFFF;
            out.writeBoolean(wide);
            // Lauflängen-Kodierung (gleiche Indizes in Folge)
            int i = 0;
            while (i < indices.length) {
                int v = indices[i];
                int run = 1;
                while (i + run < indices.length && indices[i + run] == v && run < 0xFFFF) run++;
                out.writeShort(run);
                if (wide) out.writeInt(v);
                else out.writeShort(v);
                i += run;
            }
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static BlockSnapshot load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            if (in.readInt() != MAGIC) throw new IOException("Kein SamuraiBoss-Snapshot: " + file);
            String world = in.readUTF();
            int minX = in.readInt(), minY = in.readInt(), minZ = in.readInt();
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            int pc = in.readInt();
            List<String> palette = new ArrayList<>(pc);
            for (int p = 0; p < pc; p++) palette.add(in.readUTF());
            boolean wide = in.readBoolean();
            int[] idx = new int[sx * sy * sz];
            int i = 0;
            while (i < idx.length) {
                int run = in.readUnsignedShort();
                int v = wide ? in.readInt() : in.readUnsignedShort();
                for (int k = 0; k < run && i < idx.length; k++) idx[i++] = v;
            }
            return new BlockSnapshot(world, minX, minY, minZ, sx, sy, sz, palette, idx);
        }
    }
}
