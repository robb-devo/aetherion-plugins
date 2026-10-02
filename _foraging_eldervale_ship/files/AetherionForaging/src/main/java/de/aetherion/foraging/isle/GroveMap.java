package de.aetherion.foraging.isle;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Location;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Where am I on Foraging Eldervale? Answered from a district grid baked offline from the schematic
 * ({@code forage-districts.bin}, 8×8×8 cells, ~7 KB), so it costs an array read — no block scans, no
 * chunk access. That's what finally lets TAB, weather and loot know the district while the old
 * material-sniffing habitat sense stays off for FPS.
 *
 * <p>The grid is stored in world coordinates for the paste it was baked from. If the isle is re-pasted
 * somewhere else with the same rotation, lookups shift by the difference; another rotation turns the
 * grid off (landmark boxes still work) and says so in the log.
 */
public final class GroveMap {

    private final AetherionForaging plugin;
    private byte[] cells = new byte[0];
    private int x0;
    private int y0;
    private int z0;
    private int cellXZ = 8;
    private int cellY = 8;
    private int nx;
    private int ny;
    private int nz;
    private int bakedPasteX;
    private int bakedPasteY;
    private int bakedPasteZ;
    private int bakedRotation;
    private int shiftX;
    private int shiftY;
    private int shiftZ;
    private boolean usable;
    private String status = "not loaded";

    public GroveMap(AetherionForaging plugin) {
        this.plugin = plugin;
        load();
        realign();
    }

    private void load() {
        try (InputStream raw = plugin.getResource("forage-districts.bin")) {
            if (raw == null) {
                status = "forage-districts.bin missing from the jar";
                return;
            }
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
                byte[] magic = in.readNBytes(4);
                if (magic.length != 4 || magic[0] != 'A' || magic[1] != 'E' || magic[2] != 'F' || magic[3] != 'D') {
                    status = "bad district grid header";
                    return;
                }
                int version = in.readUnsignedByte();
                if (version != 1) {
                    status = "district grid v" + version + " unknown";
                    return;
                }
                x0 = in.readInt();
                y0 = in.readInt();
                z0 = in.readInt();
                cellXZ = in.readUnsignedByte();
                cellY = in.readUnsignedByte();
                nx = in.readUnsignedShort();
                ny = in.readUnsignedShort();
                nz = in.readUnsignedShort();
                bakedPasteX = in.readInt();
                bakedPasteY = in.readInt();
                bakedPasteZ = in.readInt();
                bakedRotation = in.readUnsignedShort();
                cells = in.readNBytes(nx * ny * nz);
                if (cells.length != nx * ny * nz) {
                    status = "district grid truncated";
                    cells = new byte[0];
                    return;
                }
                status = "loaded " + nx + "×" + ny + "×" + nz;
            }
        } catch (IOException ex) {
            status = "district grid unreadable: " + ex.getMessage();
        }
    }

    /** Re-reads the paste origin / rotation from config.yml and shifts (or disables) the grid. */
    public void realign() {
        if (cells.length == 0) {
            usable = false;
            return;
        }
        var cfg = plugin.getConfig();
        int rotation = Math.floorMod(cfg.getInt("forage-isle.rotate-y", 180), 360);
        if (rotation != bakedRotation) {
            usable = false;
            status = "grid baked for rotate-y " + bakedRotation + ", isle is " + rotation + " — districts from landmarks only";
            plugin.getLogger().warning("Grove map: " + status);
            return;
        }
        shiftX = cfg.getInt("forage-isle.paste.x", bakedPasteX) - bakedPasteX;
        shiftY = cfg.getInt("forage-isle.paste.y", bakedPasteY) - bakedPasteY;
        shiftZ = cfg.getInt("forage-isle.paste.z", bakedPasteZ) - bakedPasteZ;
        usable = true;
        status = "loaded " + nx + "×" + ny + "×" + nz + (shiftX != 0 || shiftY != 0 || shiftZ != 0
                ? " · shifted " + shiftX + "," + shiftY + "," + shiftZ : "");
    }

    public String status() {
        return status;
    }

    /** Grid district only (no landmark override). Null outside the baked volume or in open air. */
    public Grove gridAt(double x, double y, double z) {
        if (!usable) {
            return null;
        }
        int cx = Math.floorDiv((int) Math.floor(x) - shiftX - x0, cellXZ);
        int cy = Math.floorDiv((int) Math.floor(y) - shiftY - y0, cellY);
        int cz = Math.floorDiv((int) Math.floor(z) - shiftZ - z0, cellXZ);
        if (cx < 0 || cy < 0 || cz < 0 || cx >= nx || cy >= ny || cz >= nz) {
            return null;
        }
        return Grove.byCode(cells[(cy * nz + cz) * nx + cx]);
    }

    /** Is the grid live (loaded and aligned with the current paste)? */
    public boolean usable() {
        return usable;
    }
}
