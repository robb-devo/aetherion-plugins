package de.aetherion.mining.veins;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sparse dig-zone snapshot: only blocks written by {@link VeinsDigZones}.
 * Hub is never included. Restore = identical structure/ores for the 24h reset.
 */
public final class VeinsDigSnapshot {

    private static final int MAGIC = 0xAE7D1601;
    private static final int VERSION = 1;

    private VeinsDigSnapshot() {
    }

    public static File file(File dataFolder) {
        return new File(dataFolder, "veins-dig-snapshot.bin.gz");
    }

    public static void save(File dataFolder, List<Entry> entries) throws IOException {
        if (dataFolder == null) {
            return;
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Cannot create " + dataFolder);
        }
        File target = file(dataFolder);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(target))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeInt(entry.x);
                out.writeInt(entry.y);
                out.writeInt(entry.z);
                out.writeUTF(entry.data.getAsString());
            }
        }
    }

    public static int restore(World world, File dataFolder, CommandSender progress) {
        File target = file(dataFolder);
        if (world == null || !target.isFile()) {
            return -1;
        }
        int written = 0;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(target))))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != MAGIC || version != VERSION) {
                if (progress != null) {
                    progress.sendMessage("§cDig snapshot version mismatch — re-paint with §f/deepmines zones force§c.");
                }
                return -1;
            }
            int count = in.readInt();
            if (progress != null) {
                progress.sendMessage("§eRestoring dig snapshot (§f" + count + "§e blocks)…");
            }
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String asString = in.readUTF();
                BlockData data = org.bukkit.Bukkit.createBlockData(asString);
                Block block = world.getBlockAt(x, y, z);
                if (!block.getBlockData().matches(data)) {
                    block.setBlockData(data, false);
                    written++;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            if (progress != null) {
                progress.sendMessage("§cDig snapshot restore failed: " + e.getMessage());
            }
            return -1;
        }
        if (progress != null) {
            progress.sendMessage("§aDig snapshot restored (§f" + written + "§a blocks changed). Hub untouched.");
        }
        return written;
    }

    public static boolean exists(File dataFolder) {
        return file(dataFolder).isFile();
    }

    public static final class Entry {
        public final int x;
        public final int y;
        public final int z;
        public final BlockData data;

        public Entry(int x, int y, int z, BlockData data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }

        public static Entry of(Block block) {
            return new Entry(block.getX(), block.getY(), block.getZ(), block.getBlockData().clone());
        }

        public static Entry of(int x, int y, int z, Material material) {
            return new Entry(x, y, z, material.createBlockData());
        }
    }

    public static List<Entry> newBuffer() {
        return new ArrayList<>(8192);
    }
}
