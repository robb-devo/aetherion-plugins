package de.aetherion.bossengine.music;

import org.bukkit.Sound;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Open Note Block Studio song (NBS) loader. Supports the modern header
 * (first length short = 0, then version byte). Tempo is stored as
 * {@code ticks_per_second * 100} — we never assume 10 or 20 tps.
 */
public final class NbsSong {

    public record Note(int tick, Sound sound, int key, float velocity) {
    }

    private final List<Note> notes;
    private final double ticksPerSecond;
    private final int lengthTicks;
    private final String title;

    private NbsSong(List<Note> notes, double ticksPerSecond, int lengthTicks, String title) {
        this.notes = List.copyOf(notes);
        this.ticksPerSecond = ticksPerSecond;
        this.lengthTicks = lengthTicks;
        this.title = title == null ? "" : title;
    }

    public static NbsSong load(InputStream in, Logger log) {
        if (in == null) {
            return empty();
        }
        try {
            byte[] raw = in.readAllBytes();
            return parse(raw, log);
        } catch (IOException ex) {
            if (log != null) {
                log.warning("[Music] Failed to read NBS: " + ex.getMessage());
            }
            return empty();
        }
    }

    public static NbsSong empty() {
        return new NbsSong(List.of(), 10.0, 0, "");
    }

    public List<Note> notes() {
        return notes;
    }

    public double ticksPerSecond() {
        return ticksPerSecond;
    }

    public int lengthTicks() {
        return lengthTicks;
    }

    public String title() {
        return title;
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    private static NbsSong parse(byte[] raw, Logger log) throws IOException {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(raw));
        int first = readUnsignedShort(data);
        int version = 0;
        int vanillaInstruments = 16;
        int length;
        if (first == 0) {
            version = data.readUnsignedByte();
            vanillaInstruments = data.readUnsignedByte();
            length = version >= 1 ? readUnsignedShort(data) : 0;
        } else {
            length = first;
        }
        int layerCount = readUnsignedShort(data);
        String title = readString(data);
        readString(data); // author
        readString(data); // original author
        readString(data); // description
        int tempoRaw = readUnsignedShort(data);
        double tps = tempoRaw <= 0 ? 10.0 : tempoRaw / 100.0;

        data.readBoolean(); // auto-save
        data.readUnsignedByte(); // auto-save duration
        data.readUnsignedByte(); // time signature
        data.readInt(); // minutes spent
        data.readInt(); // left clicks
        data.readInt(); // right clicks
        data.readInt(); // blocks added
        data.readInt(); // blocks removed
        readString(data); // midi/schematic name
        if (version >= 4) {
            data.readUnsignedByte(); // loop on/off
            data.readUnsignedByte(); // max loop count
            readUnsignedShort(data); // loop start
        }

        List<Note> notes = new ArrayList<>();
        int tick = -1;
        while (true) {
            int jumpTicks = readUnsignedShort(data);
            if (jumpTicks == 0) {
                break;
            }
            tick += jumpTicks;
            int layer = -1;
            while (true) {
                int jumpLayers = readUnsignedShort(data);
                if (jumpLayers == 0) {
                    break;
                }
                layer += jumpLayers;
                int instrument = data.readUnsignedByte();
                int key = data.readUnsignedByte();
                float velocity = 0.8f;
                if (version >= 4) {
                    velocity = Math.max(1, Math.min(100, data.readUnsignedByte())) / 100f;
                    data.readUnsignedByte(); // panning
                    readShort(data); // fine pitch
                }
                Sound sound = instrumentSound(instrument, vanillaInstruments);
                if (sound == null) {
                    continue;
                }
                // NBS key 0–87; note-block playable band is F#3–F#5 → key 33–57 → note 0–24.
                int note = Math.max(0, Math.min(24, key - 33));
                notes.add(new Note(tick, sound, note, velocity));
            }
        }
        // Layer + custom-instrument tails are ignored for playback.
        if (length <= 0 && !notes.isEmpty()) {
            length = notes.get(notes.size() - 1).tick() + 1;
        }
        if (log != null) {
            log.info("[Music] NBS v" + version + " \"" + title + "\" notes=" + notes.size()
                    + " len=" + length + " tps=" + tps + " layers=" + layerCount);
        }
        return new NbsSong(notes, tps, length, title);
    }

    private static Sound instrumentSound(int id, int vanillaInstruments) {
        if (id >= vanillaInstruments) {
            return Sound.BLOCK_NOTE_BLOCK_HARP;
        }
        return switch (id) {
            case 0 -> Sound.BLOCK_NOTE_BLOCK_HARP;
            case 1 -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case 2 -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
            case 3 -> Sound.BLOCK_NOTE_BLOCK_SNARE;
            case 4 -> Sound.BLOCK_NOTE_BLOCK_HAT;
            case 5 -> Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case 6 -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case 7 -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case 8 -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case 9 -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case 10 -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case 11 -> Sound.BLOCK_NOTE_BLOCK_COW_BELL;
            case 12 -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case 13 -> Sound.BLOCK_NOTE_BLOCK_BIT;
            case 14 -> Sound.BLOCK_NOTE_BLOCK_BANJO;
            case 15 -> Sound.BLOCK_NOTE_BLOCK_PLING;
            default -> Sound.BLOCK_NOTE_BLOCK_HARP;
        };
    }

    private static int readUnsignedShort(DataInputStream data) throws IOException {
        int lo = data.readUnsignedByte();
        int hi = data.readUnsignedByte();
        return lo | (hi << 8);
    }

    private static short readShort(DataInputStream data) throws IOException {
        int lo = data.readUnsignedByte();
        int hi = data.readUnsignedByte();
        return (short) (lo | (hi << 8));
    }

    private static String readString(DataInputStream data) throws IOException {
        int len = Integer.reverseBytes(data.readInt());
        if (len <= 0) {
            return "";
        }
        byte[] buf = data.readNBytes(len);
        return new String(buf, StandardCharsets.UTF_8);
    }
}
