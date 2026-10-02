package de.aetherion.hub.origin;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * District-aware softlight for Origin (DEV). Same idea as {@code /hubadmin softlight}: invisible LIGHT blocks where
 * the walkable surface is dark, but limited to the island (never under it), shaped by the districts, skipping the
 * ones listed in {@code softlight.exclude} (the Borderlands stay grim), and every block it places is recorded in
 * {@code origin-softlight.yml} so {@code /origin dev softlight undo} can take exactly those back out.
 */
public final class OriginSoftLight {

    private static final int COLUMNS_PER_TICK = 40;
    private static final int ISLAND_FLOOR_Y = 50;

    private final OriginIsle isle;
    private final File file;
    private final List<int[]> record = new ArrayList<>();
    private BukkitTask task;
    private String running;
    private boolean loaded;

    OriginSoftLight(OriginIsle isle) {
        this.isle = isle;
        this.file = new File(isle.plugin().getDataFolder(), "origin-softlight.yml");
    }

    public boolean busy() {
        return task != null;
    }

    public String runningLabel() {
        return running;
    }

    public int recorded() {
        loadRecord();
        return record.size();
    }

    /** target: a district id, "all" (every district except the excluded ones) or "here" (radius 48 around sender). */
    public String run(CommandSender sender, String target, int[] around) {
        if (task != null) {
            return "§cSoftlight is already running (§f" + running + "§c). §7/origin dev softlight cancel";
        }
        World world = isle.config().world();
        if (world == null) {
            return "§cOrigin world '" + isle.config().worldName() + "' isn't loaded.";
        }
        loadRecord();
        YamlConfiguration raw = isle.config().raw();
        int step = Math.max(3, Math.min(16, raw.getInt("softlight.step", 5)));
        int minLight = Math.max(0, Math.min(14, raw.getInt("softlight.min-light", 7)));
        int level = Math.max(1, Math.min(15, raw.getInt("softlight.level", 12)));
        Set<String> exclude = new HashSet<>();
        for (String id : raw.getStringList("softlight.exclude")) {
            exclude.add(id.toLowerCase(Locale.ROOT));
        }

        List<int[]> columns = new ArrayList<>();
        String key = target == null ? "all" : target.toLowerCase(Locale.ROOT);
        if (key.equals("here") && around != null) {
            int r = Math.max(8, Math.min(96, around[2]));
            int[] fp = isle.config().footprint();
            for (int x = around[0] - r; x <= around[0] + r; x += step) {
                for (int z = around[1] - r; z <= around[1] + r; z += step) {
                    if (x >= fp[0] && x <= fp[1] && z >= fp[2] && z <= fp[3]) {
                        columns.add(new int[]{x, z});
                    }
                }
            }
        } else if (key.equals("all")) {
            int[] fp = isle.config().footprint();
            for (int x = fp[0]; x <= fp[1]; x += step) {
                for (int z = fp[2]; z <= fp[3]; z += step) {
                    columns.add(new int[]{x, z});
                }
            }
        } else {
            OriginConfig.District district = isle.config().district(key);
            if (district == null) {
                return "§cUnknown district '" + target + "'. §7Try: all, here, " + String.join(", ", isle.config().districts().keySet());
            }
            Set<Long> seen = new HashSet<>();
            for (double[] c : district.circles()) {
                int r = (int) Math.ceil(c[2]);
                int cx = (int) Math.floor(c[0]);
                int cz = (int) Math.floor(c[1]);
                for (int x = Math.floorDiv(cx - r, step) * step; x <= cx + r; x += step) {
                    for (int z = Math.floorDiv(cz - r, step) * step; z <= cz + r; z += step) {
                        double dx = x - c[0];
                        double dz = z - c[1];
                        if (dx * dx + dz * dz <= c[2] * c[2] && seen.add(((long) x << 32) ^ (z & 0xffffffffL))) {
                            columns.add(new int[]{x, z});
                        }
                    }
                }
            }
        }
        if (columns.isEmpty()) {
            return "§7Nothing to scan.";
        }
        running = key;
        int[] placed = {0};
        int[] index = {0};
        int total = columns.size();
        sender.sendMessage("§eOrigin softlight §8· §f" + key + " §8· §7" + total + " columns, step " + step
                + ", min light " + minLight + ", level " + level + (exclude.isEmpty() ? "" : " §8· §7skipping " + exclude));
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), () -> {
            int end = Math.min(index[0] + COLUMNS_PER_TICK, total);
            for (int i = index[0]; i < end; i++) {
                int[] xz = columns.get(i);
                column(world, xz[0], xz[1], minLight, level, exclude, placed);
            }
            index[0] = end;
            if (index[0] >= total) {
                finish(sender, "§aOrigin softlight done §8· §f" + placed[0] + " §alights placed §8· §7undo: /origin dev softlight undo");
            } else if (index[0] % (COLUMNS_PER_TICK * 50) < COLUMNS_PER_TICK) {
                sender.sendMessage("§7Softlight… §f" + (index[0] * 100 / total) + "% §8(§f" + placed[0] + " §8placed)");
            }
        }, 1L, 1L);
        return null;
    }

    private void finish(CommandSender sender, String line) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running = null;
        saveRecord();
        if (sender != null && line != null) {
            sender.sendMessage(line);
        }
    }

    private void column(World world, int x, int z, int minLight, int level, Set<String> exclude, int[] placed) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.getChunkAt(x >> 4, z >> 4);
        }
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (surface < ISLAND_FLOOR_Y - 4) {
            return;
        }
        OriginConfig.District district = null;
        for (OriginConfig.District d : isle.config().districts().values()) {
            if (d.contains(x + 0.5d, surface + 1.0d, z + 0.5d)) {
                district = d;
                break;
            }
        }
        if (district != null && exclude.contains(district.id())) {
            return;
        }
        int top = Math.min(world.getMaxHeight() - 2, surface + 8);
        tryPlace(world, x, surface + 1, z, minLight, level, placed);
        for (int y = ISLAND_FLOOR_Y; y <= top; y += 4) {
            tryPlace(world, x, y, z, minLight, level, placed);
        }
    }

    private void tryPlace(World world, int x, int y, int z, int minLight, int level, int[] placed) {
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type == Material.LIGHT || (!type.isAir())) {
            return;
        }
        if (block.getLightFromSky() >= 12 && block.getLightFromBlocks() == 0) {
            return;
        }
        if (block.getLightLevel() > minLight) {
            return;
        }
        // Walkable only: something to stand on right below (1-2 blocks) — never mid-air, never under the island.
        Material below = world.getBlockAt(x, y - 1, z).getType();
        Material below2 = world.getBlockAt(x, y - 2, z).getType();
        if (!below.isSolid() && !below2.isSolid()) {
            return;
        }
        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light data) {
            data.setLevel(level);
            block.setBlockData(data, false);
        }
        record.add(new int[]{x, y, z});
        placed[0]++;
    }

    /** Removes every LIGHT block this tool placed (and only those that are still LIGHT). */
    public String undo(CommandSender sender) {
        if (task != null) {
            finish(null, null);
        }
        loadRecord();
        World world = isle.config().world();
        if (world == null) {
            return "§cOrigin world isn't loaded.";
        }
        if (record.isEmpty()) {
            return "§7No Origin softlight on record.";
        }
        int total = record.size();
        int[] removed = {0};
        running = "undo";
        // Entries leave the record only once they are handled, so a cancel/stop mid-undo keeps the rest undoable.
        task = Bukkit.getScheduler().runTaskTimer(isle.plugin(), () -> {
            for (int n = 0; n < 400 && !record.isEmpty(); n++) {
                int[] p = record.remove(record.size() - 1);
                if (!world.isChunkLoaded(p[0] >> 4, p[2] >> 4)) {
                    world.getChunkAt(p[0] >> 4, p[2] >> 4);
                }
                Block block = world.getBlockAt(p[0], p[1], p[2]);
                if (block.getType() == Material.LIGHT) {
                    block.setType(Material.AIR, false);
                    removed[0]++;
                }
            }
            if (record.isEmpty()) {
                finish(sender, "§aOrigin softlight removed §8· §f" + removed[0] + " §alights.");
            }
        }, 1L, 1L);
        return "§eRemoving §f" + total + " §erecorded lights…";
    }

    /** Stops a running pass (plugin disable / DEV). Placed lights stay recorded. */
    public void cancel() {
        if (task != null) {
            finish(null, null);
        }
    }

    public String cancelCommand() {
        if (task == null) {
            return "§7Nothing running.";
        }
        finish(null, null);
        return "§eSoftlight cancelled §8· §7what was placed stays recorded (undo still works).";
    }

    // ------------------------------------------------------------------ record

    private void loadRecord() {
        if (loaded) {
            return;
        }
        loaded = true;
        record.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("placed")) {
            int[] p = OriginConfig.ints(raw);
            if (p != null) {
                record.add(p);
            }
        }
    }

    private void saveRecord() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> out = new ArrayList<>(record.size());
        for (int[] p : record) {
            out.add(p[0] + " " + p[1] + " " + p[2]);
        }
        yaml.set("world", isle.config().worldName());
        yaml.set("placed", out);
        try {
            yaml.save(file);
        } catch (IOException exception) {
            isle.plugin().getLogger().warning("Could not save origin-softlight.yml: " + exception.getMessage());
        }
    }
}
