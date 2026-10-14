package de.aetherion.gnomon.world;

import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Temporary world edits with a crash journal. Only ever replaces air (never a build), remembers what it placed,
 * writes the journal before the world changes, and restores on end / plugin enable / {@code /gnomon cleanup}.
 */
public final class TempBlocks {

    private record Placed(String world, int x, int y, int z, String original, String placed) {
        String key() {
            return world + ";" + x + ";" + y + ";" + z;
        }
    }

    private final File journal;
    private final Logger log;
    private final Map<String, Placed> placed = new LinkedHashMap<>();

    public TempBlocks(File journal, Logger log) {
        this.journal = journal;
        this.log = log;
    }

    public boolean owns(Block b) {
        return placed.containsKey(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ());
    }

    public int size() {
        return placed.size();
    }

    /** Places a batch; returns the blocks actually changed. Journal is written first. */
    public List<Block> placeAll(List<Block> blocks, BlockData data) {
        List<Placed> batch = new ArrayList<>();
        List<Block> changed = new ArrayList<>();
        for (Block b : blocks) {
            if (b == null || !b.getType().isAir()) {
                continue;
            }
            Placed p = new Placed(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(),
                    b.getBlockData().getAsString(), data.getAsString());
            if (placed.containsKey(p.key())) {
                continue;
            }
            batch.add(p);
            changed.add(b);
        }
        if (batch.isEmpty()) {
            return changed;
        }
        for (Placed p : batch) {
            placed.put(p.key(), p);
        }
        save();
        for (Block b : changed) {
            b.setBlockData(data, false);
        }
        return changed;
    }

    /** Restores a set of blocks we placed (e.g. one wall). */
    public void restore(List<Block> blocks) {
        boolean any = false;
        for (Block b : blocks) {
            Placed p = placed.remove(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ());
            if (p != null) {
                restoreOne(p);
                any = true;
            }
        }
        if (any) {
            save();
        }
    }

    public void restoreAll() {
        if (placed.isEmpty()) {
            return;
        }
        for (Placed p : new ArrayList<>(placed.values())) {
            restoreOne(p);
        }
        placed.clear();
        save();
    }

    private void restoreOne(Placed p) {
        World w = Bukkit.getWorld(p.world());
        if (w == null) {
            return;
        }
        Block b = w.getBlockAt(p.x(), p.y(), p.z());
        String now = b.getBlockData().getAsString();
        // Only undo our own block. If someone changed it since, leave their change alone.
        if (!now.equals(p.placed()) && !b.getType().isAir()) {
            log.info("[Gnomon] Temp block at " + p.key() + " changed since placement (" + now + "); left as is.");
            return;
        }
        try {
            b.setBlockData(Bukkit.createBlockData(p.original()), false);
        } catch (IllegalArgumentException e) {
            b.setType(org.bukkit.Material.AIR, false);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> rows = new ArrayList<>();
        for (Placed p : placed.values()) {
            rows.add(p.world() + ";" + p.x() + ";" + p.y() + ";" + p.z() + ";" + p.original() + ";" + p.placed());
        }
        y.set("placed", rows);
        try {
            AtomicYaml.save(y, journal, log);
        } catch (IOException e) {
            log.warning("[Gnomon] Could not write temp-block journal: " + e.getMessage());
        }
    }

    /**
     * Startup recovery: undo anything a crash left behind. Entries for unloaded worlds stay in the journal.
     *
     * @return restored count
     */
    public static int recover(File journal, Logger log) {
        AtomicYaml.recoverTemp(journal, log);
        if (!journal.isFile()) {
            return 0;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(journal);
        List<String> rows = y.getStringList("placed");
        if (rows.isEmpty()) {
            return 0;
        }
        List<String> keep = new ArrayList<>();
        int restored = 0;
        for (String row : rows) {
            // world;x;y;z;original;placed - block data strings never contain ';'
            String[] parts = row.split(";", 6);
            if (parts.length < 6) {
                continue;
            }
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) {
                keep.add(row);
                continue;
            }
            try {
                Block b = w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (b.getBlockData().getAsString().equals(parts[5]) || b.getType().isAir()) {
                    b.setBlockData(Bukkit.createBlockData(parts[4]), false);
                    restored++;
                }
            } catch (RuntimeException e) {
                log.warning("[Gnomon] Bad journal row '" + row + "': " + e.getMessage());
            }
        }
        y.set("placed", keep);
        try {
            AtomicYaml.save(y, journal, log);
        } catch (IOException e) {
            log.warning("[Gnomon] Could not rewrite temp-block journal: " + e.getMessage());
        }
        return restored;
    }
}
