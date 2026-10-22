package de.aetherion.core.exile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Cuts the old overworld out of {@code aetherion_exile} so the island floats in void. Runs from
 * {@code AetherionCore.onLoad} (worlds not loaded yet) when {@code plugins/AetherionCore/exile-void-trim.armed} exists,
 * once: the file becomes {@code .done} or {@code .aborted}.
 * <p>
 * The island was pasted into a fresh void world (Runde 7, generator AetherionCore); the overworld only came later, when
 * the world lost its generator and every chunk not generated yet grew vanilla terrain, right up to the island's edge.
 * A chunk that holds island blocks was generated (void) by the paste itself, so it can never carry natural bedrock.
 * Every chunk that does (bedrock at the bottom, or not fully generated) is therefore pure overworld and is deleted from
 * region/, entities/ and poi/, also where it touches the island; with {@code generator: AetherionCore} for the world
 * it comes back empty. Built chunks are never touched. Aborts without changing anything when nothing is built near
 * the spawn, or on a format it can't read. Changed region files are copied (whole ones moved) to
 * {@code <world>.void-backup-<time>} next to the world first.
 */
public final class ExileVoidTrim {

    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final String[] DIRS = {"region", "entities", "poi"};

    private enum Kind { EMPTY, BUILT, TERRAIN }

    private ExileVoidTrim() {
    }

    /** @return a one-line result for the log; never throws. */
    public static String run(File world, int spawnX, int spawnZ, Logger log) {
        try {
            return trim(world, spawnX >> 4, spawnZ >> 4, log);
        } catch (IOException | RuntimeException ex) {
            return "aborted, nothing changed: " + ex;
        }
    }

    private static String trim(File world, int spawnCx, int spawnCz, Logger log) throws IOException {
        File regionDir = new File(world, "region");
        File[] regions = regionDir.listFiles((d, n) -> REGION.matcher(n).matches());
        if (regions == null || regions.length == 0) {
            return "aborted: no region files in " + regionDir;
        }
        Map<Long, Kind> chunks = new HashMap<>();
        for (File file : regions) {
            Matcher m = REGION.matcher(file.getName());
            m.matches();
            classify(file, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), chunks);
        }
        int built = 0;
        int terrain = 0;
        List<String> edge = new ArrayList<>();
        for (Map.Entry<Long, Kind> e : chunks.entrySet()) {
            if (e.getValue() == Kind.BUILT) {
                built++;
            } else if (e.getValue() == Kind.TERRAIN) {
                terrain++;
                int cx = (int) (e.getKey() >> 32);
                int cz = (int) (long) e.getKey();
                if (touchesBuilt(chunks, cx, cz)) {
                    edge.add(cx + "," + cz);
                }
            }
        }
        boolean nearSpawn = false;
        for (int dx = -3; dx <= 3 && !nearSpawn; dx++) {
            for (int dz = -3; dz <= 3 && !nearSpawn; dz++) {
                nearSpawn = chunks.get(key(spawnCx + dx, spawnCz + dz)) == Kind.BUILT;
            }
        }
        if (!nearSpawn || built < 4) {
            return "aborted, nothing changed: no island near the spawn (" + built + " built chunks)";
        }
        if (terrain == 0) {
            return "nothing to trim: " + built + " built chunks, no terrain left";
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backup = new File(world.getParentFile(), world.getName() + ".void-backup-" + stamp);
        int files = 0;
        for (String dir : DIRS) {
            File[] list = new File(world, dir).listFiles((d, n) -> REGION.matcher(n).matches());
            if (list == null) {
                continue;
            }
            for (File file : list) {
                Matcher m = REGION.matcher(file.getName());
                m.matches();
                if (strip(file, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), chunks,
                        new File(backup, dir))) {
                    files++;
                }
            }
        }
        log.info("[ExileVoid] backup of the changed region files: " + backup.getPath());
        if (!edge.isEmpty()) {
            log.info("[ExileVoid] terrain chunks right beside the island (gone too, in the backup): "
                    + (edge.size() > 40 ? edge.subList(0, 40) + " … +" + (edge.size() - 40) : edge));
        }
        return "trimmed " + terrain + " terrain chunks (" + files + " region files, " + edge.size()
                + " of them beside the island), kept " + built + " built chunks";
    }

    private static boolean touchesBuilt(Map<Long, Kind> chunks, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (chunks.get(key(cx + dx, cz + dz)) == Kind.BUILT) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static void classify(File file, int rx, int rz, Map<Long, Kind> out) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 8192) {
                return;
            }
            byte[] header = new byte[4096];
            raf.readFully(header);
            for (int i = 0; i < 1024; i++) {
                int sector = ((header[i * 4] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8) | (header[i * 4 + 2] & 0xFF);
                if (sector == 0) {
                    continue;
                }
                long cKey = key(rx * 32 + (i & 31), rz * 32 + (i >> 5));
                Map<String, Object> root = readChunk(raf, sector, file, rx * 32 + (i & 31), rz * 32 + (i >> 5));
                out.put(cKey, kind(root));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Kind kind(Map<String, Object> root) {
        Object status = root.get("Status");
        if (status instanceof String s && !s.endsWith("full")) {
            return Kind.TERRAIN; // half-generated edge of the old world
        }
        boolean any = false;
        Object sections = root.get("sections");
        if (sections instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> section)) {
                    continue;
                }
                // Natural bedrock sits at the bottom of the world; the island is built far above section 0.
                boolean low = section.get("Y") instanceof Integer y && y <= 0;
                Object states = section.get("block_states");
                if (!(states instanceof Map<?, ?> st) || !(st.get("palette") instanceof List<?> palette)) {
                    continue;
                }
                for (Object entry : palette) {
                    Object name = entry instanceof Map<?, ?> e ? ((Map<String, Object>) e).get("Name") : null;
                    if (!(name instanceof String n) || AIR.contains(n)) {
                        continue;
                    }
                    if (low && n.equals("minecraft:bedrock")) {
                        return Kind.TERRAIN;
                    }
                    any = true;
                }
            }
        }
        return any ? Kind.BUILT : Kind.EMPTY;
    }

    private static Map<String, Object> readChunk(RandomAccessFile raf, int sector, File file, int cx, int cz)
            throws IOException {
        raf.seek((long) sector * 4096L);
        int length = raf.readInt();
        int type = raf.readUnsignedByte();
        byte[] data;
        if ((type & 0x80) != 0) {
            File external = new File(file.getParentFile(), "c." + cx + "." + cz + ".mcc");
            data = Files.readAllBytes(external.toPath());
            type &= 0x7F;
        } else {
            if (length <= 1 || length > 1 << 24) {
                throw new IOException("bad chunk length " + length + " in " + file.getName());
            }
            data = new byte[length - 1];
            raf.readFully(data);
        }
        InputStream in = switch (type) {
            case 1 -> new GZIPInputStream(new ByteArrayInputStream(data));
            case 2 -> new InflaterInputStream(new ByteArrayInputStream(data));
            case 3 -> new ByteArrayInputStream(data);
            default -> throw new IOException("chunk compression " + type + " in " + file.getName() + " (only zlib/gzip/none)");
        };
        try (DataInputStream nbt = new DataInputStream(in)) {
            int tag = nbt.readUnsignedByte();
            if (tag != 10) {
                throw new IOException("chunk root is not a compound in " + file.getName());
            }
            nbt.skipBytes(nbt.readUnsignedShort());
            return compound(nbt);
        }
    }

    private static Map<String, Object> compound(DataInputStream in) throws IOException {
        Map<String, Object> map = new HashMap<>();
        while (true) {
            int tag = in.readUnsignedByte();
            if (tag == 0) {
                return map;
            }
            String name = string(in);
            Object value = payload(in, tag);
            if (value != null) {
                map.put(name, value);
            }
        }
    }

    /** Only compounds, lists, strings and bytes (section Y) are kept; other numbers and arrays are skipped. */
    private static Object payload(DataInputStream in, int tag) throws IOException {
        switch (tag) {
            case 1 -> {
                return (int) in.readByte();
            }
            case 2 -> skip(in, 2);
            case 3, 5 -> skip(in, 4);
            case 4, 6 -> skip(in, 8);
            case 7 -> skip(in, in.readInt());
            case 8 -> {
                return string(in);
            }
            case 9 -> {
                int type = in.readUnsignedByte();
                int n = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, Math.min(n, 4096)));
                for (int i = 0; i < n; i++) {
                    Object v = payload(in, type);
                    if (v != null) {
                        list.add(v);
                    }
                }
                return list;
            }
            case 10 -> {
                return compound(in);
            }
            case 11 -> skip(in, in.readInt() * 4L);
            case 12 -> skip(in, in.readInt() * 8L);
            default -> throw new IOException("unknown NBT tag " + tag);
        }
        return null;
    }

    private static String string(DataInputStream in) throws IOException {
        byte[] b = new byte[in.readUnsignedShort()];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void skip(DataInputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            int s = in.skipBytes((int) Math.min(left, Integer.MAX_VALUE));
            if (s <= 0) {
                throw new EOFException();
            }
            left -= s;
        }
    }

    /** Drops the terrain chunks of one region file. True when the file was changed or moved to the backup. */
    private static boolean strip(File file, int rx, int rz, Map<Long, Kind> chunks, File backupDir) throws IOException {
        List<Integer> drop = new ArrayList<>();
        int present = 0;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 8192) {
                return false;
            }
            byte[] header = new byte[4096];
            raf.readFully(header);
            for (int i = 0; i < 1024; i++) {
                if ((header[i * 4] | header[i * 4 + 1] | header[i * 4 + 2]) == 0) {
                    continue;
                }
                present++;
                if (chunks.get(key(rx * 32 + (i & 31), rz * 32 + (i >> 5))) == Kind.TERRAIN) {
                    drop.add(i);
                }
            }
        }
        if (drop.isEmpty()) {
            return false;
        }
        backupDir.mkdirs();
        File saved = new File(backupDir, file.getName());
        if (drop.size() == present) {
            Files.move(file.toPath(), saved.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(file.toPath(), saved.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                byte[] zero = new byte[4];
                for (int i : drop) {
                    raf.seek(i * 4L);
                    raf.write(zero);
                    raf.seek(4096L + i * 4L);
                    raf.write(zero);
                }
            }
        }
        for (int i : drop) {
            File external = new File(file.getParentFile(), "c." + (rx * 32 + (i & 31)) + "." + (rz * 32 + (i >> 5)) + ".mcc");
            if (external.isFile()) {
                Files.move(external.toPath(), new File(backupDir, external.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return true;
    }
}
