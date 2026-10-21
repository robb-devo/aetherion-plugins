package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Puts back the Borderlands terrain the 2026-10-14/15 quay pastes aired, from FAWE's disk history, and
 * clears what is left of Seraphine's arena beside the Veil Quay.
 * <p>
 * {@code restoremap}: for each block the given history entries changed, the state before the oldest of them is
 * the original map. It goes back only where the world is air now, west of {@code eastX} (the void gap and the
 * quay stay as they are), and never for {@code skip} materials (arena pieces).
 * {@code cleararena}: airs the given materials inside a box. Both start as a dry run that only reports.
 */
public final class ExileMapRestore {

    /** Console edits of 10-14/15 on {@code world}: quay clears + pastes at 495/620/680 (not the SaintClears). */
    public static final int[] QUAY_EDITS = {1, 2, 5, 6, 7, 8};

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private ExileMapRestore() {
    }

    /** {@code /exile restoremap <dry|apply> [eastX] [skip=a,b] [edits=1,2]}. */
    public static void restore(AetherionCore plugin, CommandSender sender, String[] args) {
        boolean apply = args.length >= 2 && "apply".equalsIgnoreCase(args[1]);
        int eastX = ExileSaintClear.BL_SAFE_WEST;
        List<String> skip = new ArrayList<>();
        int[] edits = QUAY_EDITS;
        for (int i = 2; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            if (a.startsWith("skip=")) {
                for (String s : a.substring(5).split(",")) {
                    if (!s.isBlank()) {
                        skip.add(s.trim());
                    }
                }
            } else if (a.startsWith("edits=")) {
                String[] parts = a.substring(6).split(",");
                edits = new int[parts.length];
                for (int k = 0; k < parts.length; k++) {
                    edits[k] = Integer.parseInt(parts[k].trim());
                }
            } else {
                eastX = Integer.parseInt(a);
            }
        }
        World world = Bukkit.getWorld("world");
        if (world == null || !start(sender)) {
            return;
        }
        final int east = eastX;
        final int[] entries = edits;
        tell(sender, "§8Exile §8» §7Map restore " + (apply ? "§aAPPLY" : "§edry run") + "§7, edits "
                + java.util.Arrays.toString(entries) + ", x < " + east + ", skip " + skip);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<BlockVector3, Integer> original = readOriginal(world, entries);
                Map<String, Integer> byMat = new TreeMap<>();
                Map<Integer, Integer> byBand = new TreeMap<>();
                int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, Integer.MIN_VALUE};
                int placed = 0;
                try (EditSession edit = session(world)) {
                    for (Map.Entry<BlockVector3, Integer> e : original.entrySet()) {
                        BlockVector3 p = e.getKey();
                        if (p.x() >= east) {
                            continue;
                        }
                        BlockState state = fromOrdinal(e.getValue());
                        if (state == null || state.getBlockType().getMaterial().isAir()) {
                            continue;
                        }
                        String id = state.getBlockType().id().replace("minecraft:", "");
                        if (matches(id, skip)) {
                            continue;
                        }
                        if (!edit.getBlock(p).getBlockType().getMaterial().isAir()) {
                            continue;
                        }
                        byMat.merge(id, 1, Integer::sum);
                        byBand.merge(Math.floorDiv(p.x(), 16) * 16, 1, Integer::sum);
                        grow(box, p);
                        if (apply) {
                            edit.setBlock(p, state);
                        }
                        placed++;
                    }
                }
                report(plugin, sender, (apply ? "restored " : "would restore ") + placed + " blocks of "
                        + original.size() + " changed", box, byMat, byBand);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "ExileMapRestore failed", t);
                tell(sender, "§cMap restore failed: §7" + t);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    /** {@code /exile cleararena <dry|apply> x1 z1 x2 z2 ymin ymax mat,mat}: air those materials in the box. */
    public static void clearArena(AetherionCore plugin, CommandSender sender, String[] args) {
        if (args.length < 9) {
            tell(sender, "§cUsage: /exile cleararena <dry|apply> x1 z1 x2 z2 ymin ymax mat,mat (substring match)");
            return;
        }
        boolean apply = "apply".equalsIgnoreCase(args[1]);
        int x1 = Integer.parseInt(args[2]);
        int z1 = Integer.parseInt(args[3]);
        int x2 = Integer.parseInt(args[4]);
        int z2 = Integer.parseInt(args[5]);
        int y1 = Integer.parseInt(args[6]);
        int y2 = Integer.parseInt(args[7]);
        List<String> mats = List.of(args[8].toLowerCase(Locale.ROOT).split(","));
        World world = Bukkit.getWorld("world");
        if (world == null || !start(sender)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<String, Integer> byMat = new TreeMap<>();
                Map<Integer, Integer> byBand = new TreeMap<>();
                int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, Integer.MIN_VALUE};
                int cleared = 0;
                BlockState air = BlockTypes.AIR.getDefaultState();
                try (EditSession edit = session(world)) {
                    for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                                BlockVector3 p = BlockVector3.at(x, y, z);
                                BlockState state = edit.getBlock(p);
                                if (state.getBlockType().getMaterial().isAir()) {
                                    continue;
                                }
                                String id = state.getBlockType().id().replace("minecraft:", "");
                                if (!matches(id, mats)) {
                                    continue;
                                }
                                byMat.merge(id, 1, Integer::sum);
                                byBand.merge(Math.floorDiv(x, 16) * 16, 1, Integer::sum);
                                grow(box, p);
                                if (apply) {
                                    edit.setBlock(p, air);
                                }
                                cleared++;
                            }
                        }
                    }
                }
                report(plugin, sender, (apply ? "cleared " : "would clear ") + cleared + " arena blocks",
                        box, byMat, byBand);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "ExileMapRestore cleararena failed", t);
                tell(sender, "§cArena clear failed: §7" + t);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    private static boolean start(CommandSender sender) {
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            tell(sender, "§cFastAsyncWorldEdit missing.");
            return false;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            tell(sender, "§cA map restore is already running.");
            return false;
        }
        return true;
    }

    private static EditSession session(World world) {
        EditSession edit = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world)).maxBlocks(-1).build();
        try {
            edit.setFastMode(true);
        } catch (Throwable ignored) {
        }
        return edit;
    }

    /** Position → block ordinal before the oldest of {@code entries} touched it (FAWE undo direction). */
    private static Map<BlockVector3, Integer> readOriginal(World world, int[] entries) throws Exception {
        Class<?> disk = Class.forName("com.fastasyncworldedit.core.history.DiskStorageHistory");
        Constructor<?> ctor = null;
        for (Constructor<?> c : disk.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 3 && p[1] == UUID.class && p[2] == int.class) {
                ctor = c;
                break;
            }
        }
        if (ctor == null) {
            throw new IllegalStateException("DiskStorageHistory(world, uuid, index) not found");
        }
        Method iterator = disk.getMethod("getIterator", boolean.class);
        Object weWorld = BukkitAdapter.adapt(world);
        int[] sorted = entries.clone();
        java.util.Arrays.sort(sorted);
        Map<BlockVector3, Integer> original = new HashMap<>();
        for (int index : sorted) {
            Object history = ctor.newInstance(weWorld, ExileFaweUndo.CONSOLE_HISTORY, index);
            Iterator<?> it = (Iterator<?>) iterator.invoke(history, false);
            while (it.hasNext()) {
                Object change = it.next();
                Class<?> type = change.getClass();
                if (!type.getSimpleName().equals("MutableBlockChange")) {
                    continue;
                }
                int x = field(type, "x").getInt(change);
                int y = field(type, "y").getInt(change);
                int z = field(type, "z").getInt(change);
                int ordinal = field(type, "ordinal").getInt(change);
                original.putIfAbsent(BlockVector3.at(x, y, z), ordinal);
            }
        }
        return original;
    }

    private static final Map<String, Field> FIELDS = new HashMap<>();

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = FIELDS.get(name);
        if (f == null) {
            f = type.getField(name);
            FIELDS.put(name, f);
        }
        return f;
    }

    private static Method getFromOrdinal;

    private static BlockState fromOrdinal(int ordinal) throws Exception {
        if (getFromOrdinal == null) {
            getFromOrdinal = BlockState.class.getMethod("getFromOrdinal", int.class);
        }
        return (BlockState) getFromOrdinal.invoke(null, ordinal);
    }

    private static boolean matches(String id, List<String> parts) {
        for (String s : parts) {
            if (!s.isBlank() && id.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private static void grow(int[] box, BlockVector3 p) {
        box[0] = Math.min(box[0], p.x());
        box[1] = Math.max(box[1], p.x());
        box[2] = Math.min(box[2], p.y());
        box[3] = Math.max(box[3], p.y());
        box[4] = Math.min(box[4], p.z());
        box[5] = Math.max(box[5], p.z());
    }

    private static void report(AetherionCore plugin, CommandSender sender, String head, int[] box,
                               Map<String, Integer> byMat, Map<Integer, Integer> byBand) {
        StringBuilder mats = new StringBuilder();
        byMat.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> mats.append(e.getKey()).append('=').append(e.getValue()).append(' '));
        String line = "ExileMapRestore " + head + " box x " + box[0] + ".." + box[1] + " y " + box[2] + ".."
                + box[3] + " z " + box[4] + ".." + box[5] + " | bands " + byBand + " | " + mats.toString().trim();
        plugin.getLogger().info(line);
        Bukkit.getScheduler().runTask(plugin, () -> tell(sender, "§a" + line));
    }

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
