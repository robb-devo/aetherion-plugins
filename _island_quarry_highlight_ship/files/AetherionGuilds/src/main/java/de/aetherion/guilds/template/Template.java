package de.aetherion.guilds.template;

import org.bukkit.Material;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed Sponge schematic (v2 or v3, as written by WorldEdit/FAWE and by the Aetherion prop toolkit).
 *
 * <p>Coordinates handed out by this class are <b>local</b>: relative to the paste anchor, unrotated. The
 * anchor is the schematic origin (WorldEdit's "where you stood"), so local = Offset + index. For every
 * Aetherion island template the anchor is the ground-layer centre (y 0 = ground, front = south).
 */
public final class Template {

    private final String id;
    private final int width;
    private final int height;
    private final int length;
    private final int offX;
    private final int offY;
    private final int offZ;
    private final String[] palette;
    private final boolean[] air;
    private final Material[] materials;
    private final int[] data;
    private final Map<Integer, String[]> signs;
    private final int nonAir;

    private Template(String id, int width, int height, int length, int offX, int offY, int offZ,
                     String[] palette, int[] data, Map<Integer, String[]> signs) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.length = length;
        this.offX = offX;
        this.offY = offY;
        this.offZ = offZ;
        this.palette = palette;
        this.data = data;
        this.signs = signs;
        this.air = new boolean[palette.length];
        this.materials = new Material[palette.length];
        for (int i = 0; i < palette.length; i++) {
            String state = palette[i] == null ? "minecraft:air" : palette[i];
            String name = StateRotator.name(state);
            air[i] = name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                    || name.equals("minecraft:void_air") || name.equals("minecraft:structure_void");
            Material material = Material.matchMaterial(name);
            materials[i] = material == null ? Material.AIR : material;
        }
        int count = 0;
        for (int index : data) {
            if (index >= 0 && index < air.length && !air[index]) {
                count++;
            }
        }
        this.nonAir = count;
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public int volume() {
        return data.length;
    }

    public int nonAirBlocks() {
        return nonAir;
    }

    public int minX() {
        return offX;
    }

    public int minY() {
        return offY;
    }

    public int minZ() {
        return offZ;
    }

    public int maxX() {
        return offX + width - 1;
    }

    public int maxY() {
        return offY + height - 1;
    }

    public int maxZ() {
        return offZ + length - 1;
    }

    String[] palette() {
        return palette;
    }

    int paletteAt(int index) {
        return data[index];
    }

    boolean isAirPalette(int paletteIndex) {
        return paletteIndex < 0 || paletteIndex >= air.length || air[paletteIndex];
    }

    Map<Integer, String[]> signs() {
        return signs;
    }

    Material materialOfPalette(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex >= materials.length || air[paletteIndex]) {
            return Material.AIR;
        }
        return materials[paletteIndex];
    }

    /** Local x of a data index. */
    int localX(int index) {
        return offX + index % width;
    }

    int localZ(int index) {
        return offZ + (index / width) % length;
    }

    int localY(int index) {
        return offY + index / (width * length);
    }

    /** Palette index at a local (unrotated) position, or -1 outside the box. */
    public int paletteAtLocal(int lx, int ly, int lz) {
        int x = lx - offX;
        int y = ly - offY;
        int z = lz - offZ;
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) {
            return -1;
        }
        return data[x + z * width + y * width * length];
    }

    /** Material of the template block at a local position; AIR outside or for air. */
    public Material materialAtLocal(int lx, int ly, int lz) {
        int p = paletteAtLocal(lx, ly, lz);
        if (p < 0 || air[p]) {
            return Material.AIR;
        }
        return materials[p];
    }

    private List<int[]> solidCells;

    /** Local coordinates {x, y, z} of every non-air cell (cached). */
    public List<int[]> solidCells() {
        List<int[]> cells = solidCells;
        if (cells == null) {
            cells = new java.util.ArrayList<>(nonAir);
            for (int i = 0; i < data.length; i++) {
                if (!isAirPalette(data[i])) {
                    cells.add(new int[]{localX(i), localY(i), localZ(i)});
                }
            }
            solidCells = cells;
        }
        return cells;
    }

    public String stateAtLocal(int lx, int ly, int lz) {
        int p = paletteAtLocal(lx, ly, lz);
        return p < 0 ? "minecraft:air" : palette[p];
    }

    /**
     * Horizontal footprint after rotating k quarter turns clockwise: {minX, minZ, maxX, maxZ} in rotated local
     * coordinates (still relative to the anchor).
     */
    public int[] rotatedFootprint(int k) {
        int[] a = StateRotator.rotateXZ(minX(), minZ(), k);
        int[] b = StateRotator.rotateXZ(maxX(), maxZ(), k);
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    // ------------------------------------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Template read(String id, InputStream in) throws IOException {
        Map<String, Object> root = NbtIn.readGzipRoot(in);
        Map<String, Object> schem = root;
        if (root.get("Schematic") instanceof Map<?, ?> inner) {
            schem = (Map<String, Object>) inner;
        }
        int width = num(schem.get("Width"));
        int height = num(schem.get("Height"));
        int length = num(schem.get("Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Schematic " + id + " has no size");
        }
        int[] offset = schem.get("Offset") instanceof int[] ints && ints.length >= 3 ? ints : new int[3];

        Map<String, Object> paletteTag;
        byte[] blockBytes;
        List<Object> blockEntities;
        if (schem.get("Blocks") instanceof Map<?, ?> blocks) {
            Map<String, Object> b = (Map<String, Object>) blocks;
            paletteTag = (Map<String, Object>) b.get("Palette");
            blockBytes = (byte[]) b.get("Data");
            blockEntities = b.get("BlockEntities") instanceof List<?> list ? (List<Object>) list : List.of();
        } else {
            paletteTag = (Map<String, Object>) schem.get("Palette");
            blockBytes = (byte[]) schem.get("BlockData");
            blockEntities = schem.get("BlockEntities") instanceof List<?> list ? (List<Object>) list : List.of();
        }
        if (paletteTag == null || blockBytes == null) {
            throw new IOException("Schematic " + id + " has no palette/block data");
        }
        int max = 0;
        for (Object value : paletteTag.values()) {
            max = Math.max(max, num(value));
        }
        String[] palette = new String[max + 1];
        for (Map.Entry<String, Object> entry : paletteTag.entrySet()) {
            palette[num(entry.getValue())] = entry.getKey();
        }
        int volume = width * height * length;
        int[] data = new int[volume];
        int pos = 0;
        int i = 0;
        while (i < volume && pos < blockBytes.length) {
            int value = 0;
            int shift = 0;
            while (true) {
                int b = blockBytes[pos++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0 || pos >= blockBytes.length) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new IOException("Bad varint in " + id);
                }
            }
            data[i++] = value;
        }
        Map<Integer, String[]> signs = new HashMap<>();
        for (Object entry : blockEntities) {
            if (!(entry instanceof Map<?, ?> be)) {
                continue;
            }
            Map<String, Object> tag = (Map<String, Object>) be;
            if (!(tag.get("Pos") instanceof int[] p) || p.length < 3) {
                continue;
            }
            Map<String, Object> body = tag.get("Data") instanceof Map<?, ?> d ? (Map<String, Object>) d : tag;
            String[] lines = signLines(body);
            if (lines != null && p[0] >= 0 && p[1] >= 0 && p[2] >= 0 && p[0] < width && p[1] < height && p[2] < length) {
                signs.put(p[0] + p[2] * width + p[1] * width * length, lines);
            }
        }
        return new Template(id, width, height, length, offset[0], offset[1], offset[2], palette, data, signs);
    }

    @SuppressWarnings("unchecked")
    private static String[] signLines(Map<String, Object> body) {
        if (body.get("front_text") instanceof Map<?, ?> front
                && ((Map<String, Object>) front).get("messages") instanceof List<?> messages) {
            String[] lines = new String[4];
            for (int i = 0; i < 4 && i < messages.size(); i++) {
                lines[i] = String.valueOf(messages.get(i));
            }
            return lines;
        }
        if (body.containsKey("Text1")) {
            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) {
                Object v = body.get("Text" + (i + 1));
                lines[i] = v == null ? "\"\"" : String.valueOf(v);
            }
            return lines;
        }
        return null;
    }

    private static int num(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
