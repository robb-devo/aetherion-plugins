package de.aetherion.guilds.template;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal named-binary-tag reader (gzip). Enough for Sponge v2/v3 schematics: compounds become
 * {@code Map<String, Object>}, lists {@code List<Object>}, arrays stay primitive arrays.
 */
final class NbtIn {

    private static final int MAX_DEPTH = 64;

    private NbtIn() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readGzipRoot(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            int type = in.readUnsignedByte();
            if (type != 10) {
                throw new IOException("NBT root is not a compound (" + type + ")");
            }
            in.readUTF();
            return (Map<String, Object>) payload(in, 10, 0);
        }
    }

    private static Object payload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting too deep");
        }
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                int len = in.readInt();
                byte[] bytes = new byte[Math.max(0, len)];
                in.readFully(bytes);
                return bytes;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int inner = in.readUnsignedByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, Math.min(len, 1 << 16)));
                for (int i = 0; i < len; i++) {
                    list.add(payload(in, inner, depth + 1));
                }
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int inner = in.readUnsignedByte();
                    if (inner == 0) {
                        return map;
                    }
                    String name = in.readUTF();
                    map.put(name, payload(in, inner, depth + 1));
                }
            }
            case 11: {
                int len = in.readInt();
                int[] ints = new int[Math.max(0, len)];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = in.readInt();
                }
                return ints;
            }
            case 12: {
                int len = in.readInt();
                long[] longs = new long[Math.max(0, len)];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = in.readLong();
                }
                return longs;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }
}
