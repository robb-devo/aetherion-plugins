package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Clears the old Seraphine (Hanging Saint) arena footprint and pastes {@code veil_quay.schem}
 * at the given origin (feet / paste origin — not {@code -o}). Writes {@code exile.quay.*}.
 */
public final class ExileQuayPaste {

    /**
     * Just past the Borderlands cliff. Walkable deck reaches {@link #DECK_WEST} of this origin;
     * the mooring chain hangs further west into void — do not air that gap (Borderlands lives there).
     */
    public static final int DEFAULT_X = 600;
    public static final int DEFAULT_Y = 51;
    public static final int DEFAULT_Z = 322;

    /** West edge of the walkable deck, relative to the origin (the pier head by the Veil lamp). */
    public static final int DECK_WEST = -64;

    /** Pad clear slack west of the deck edge — not the old −130 that ate the cliff approach. */
    private static final int PAD_CLEAR_WEST_SLACK = 8;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private ExileQuayPaste() {
    }

    public static void start(AetherionCore plugin, CommandSender sender, String worldName, int ox, int oy, int oz) {
        if (plugin == null) {
            return;
        }
        // The clear boxes run from the safe line east to the origin: an origin west of it would flip them
        // and air everything between the origin and the Borderlands cliff.
        if (ox + DECK_WEST <= ExileSaintClear.BL_SAFE_WEST) {
            tell(sender, "§cVeil Quay origin x=" + ox + " is too far west: the deck would sit on the Borderlands"
                    + " (needs x > " + (ExileSaintClear.BL_SAFE_WEST - DECK_WEST) + "). Nothing changed.");
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            tell(sender, "§cVeil Quay paste already running.");
            return;
        }
        World world = Bukkit.getWorld(worldName == null || worldName.isBlank() ? "world" : worldName);
        if (world == null) {
            RUNNING.set(false);
            tell(sender, "§cWorld not loaded: §f" + worldName);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null
                && Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            RUNNING.set(false);
            tell(sender, "§cFastAsyncWorldEdit / WorldEdit required.");
            return;
        }
        File schem = resolve(plugin, "veil_quay.schem");
        if (schem == null) {
            RUNNING.set(false);
            tell(sender, "§cMissing §fveil_quay.schem §7under FAWE/WorldEdit schematics/.");
            return;
        }
        // Previous paste origin (e.g. 495) — wipe that pad so Seraphine leftovers + old quay go away.
        int[] prev = parseOrigin(plugin.getConfig().getString("exile.quay.origin", ""));
        tell(sender, "§8Veil Quay §8» §7Clearing old pad + pasting quay further out at §f"
                + world.getName() + " " + ox + " " + oy + " " + oz + "§7…");
        plugin.getLogger().info("ExileQuayPaste start " + schem.getAbsolutePath()
                + " -> " + world.getName() + " " + ox + "," + oy + "," + oz
                + (prev != null ? " prev=" + prev[0] + "," + prev[1] + "," + prev[2] : ""));
        final World w = world;
        final int[] previous = prev;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long start = System.currentTimeMillis();
            try {
                try (EditSession clear = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(w))
                        .maxBlocks(-1)
                        .build()) {
                    try {
                        clear.setFastMode(true);
                    } catch (Throwable ignored) {
                    }
                    // Wipe previous quay pad only (deck footprint + slack). Never the cliff approach.
                    if (previous != null
                            && (previous[0] != ox || previous[1] != oy || previous[2] != oz)) {
                        int prevWest = padClearWest(previous[0]);
                        if (previous[0] + 25 > prevWest) {
                            clear.setBlocks(new CuboidRegion(
                                    BlockVector3.at(prevWest, previous[1] - 30, previous[2] - 45),
                                    BlockVector3.at(previous[0] + 25, previous[1] + 80, previous[2] + 45)
                            ), BlockTypes.AIR.getDefaultState());
                        }
                    }
                    // Seraphine leftovers: /exile clearsaint only — do NOT air 485→545 here.
                    // That box was eating the last Borderlands cliff dress every re-paste.
                    int padWest = padClearWest(ox);
                    clear.setBlocks(new CuboidRegion(
                            BlockVector3.at(padWest, oy - 25, oz - 40),
                            BlockVector3.at(ox + 20, oy + 55, oz + 40)
                    ), BlockTypes.AIR.getDefaultState());
                }
                ClipboardFormat format = ClipboardFormats.findByFile(schem);
                if (format == null) {
                    fail(plugin, sender, "Unknown schematic format.");
                    return;
                }
                Clipboard clipboard;
                try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
                    clipboard = reader.read();
                }
                int changed;
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(w))
                        .maxBlocks(-1)
                        .build()) {
                    try {
                        editSession.setFastMode(true);
                    } catch (Throwable ignored) {
                    }
                    // Relative paste at feet (NOT //paste -o).
                    Operation op = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(ox, oy, oz))
                            .ignoreAirBlocks(true)
                            .copyEntities(false)
                            .copyBiomes(false)
                            .build();
                    Operations.complete(op);
                    changed = editSession.getBlockChangeCount();
                }
                long ms = System.currentTimeMillis() - start;
                plugin.getLogger().info("ExileQuayPaste done blocks=" + changed + " elapsed=" + ms + "ms");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getConfig().set("exile.quay.world", w.getName());
                    plugin.getConfig().set("exile.quay.origin", ox + " " + oy + " " + oz);
                    plugin.saveConfig();
                    ExileQuay quay = plugin.exileQuay();
                    if (quay != null) {
                        quay.setOrigin(w, new int[] {ox, oy, oz});
                    }
                    RUNNING.set(false);
                    tell(sender, "§aVeil Quay pasted §7(" + changed + " blocks, " + ms + "ms) at §f"
                            + w.getName() + " " + ox + " " + oy + " " + oz
                            + "§a. Pad only (Borderlands untouched). §7/exile clearsaint for Seraphine leftovers.");
                    // the pad clear took the causeway with it: lay it again from where it left the cliff
                    ExileCauseway.relay(plugin, sender);
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "ExileQuayPaste failed", t);
                fail(plugin, sender, t.getMessage() == null ? "paste failed" : t.getMessage());
            }
        });
    }

    private static void fail(AetherionCore plugin, CommandSender sender, String why) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            RUNNING.set(false);
            tell(sender, "§cVeil Quay paste failed: §7" + why);
        });
    }

    private static File resolve(AetherionCore plugin, String name) {
        File plugins = plugin.getDataFolder().getParentFile();
        File[] candidates = {
                new File(plugins, "FastAsyncWorldEdit/schematics/" + name),
                new File(plugins, "WorldEdit/schematics/" + name),
                new File(plugin.getDataFolder(), "schematics/" + name)
        };
        for (File file : candidates) {
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    /** West X of the pad clear: deck edge + slack, never west of {@link ExileSaintClear#BL_SAFE_WEST}. */
    static int padClearWest(int originX) {
        int deckWest = originX + DECK_WEST - PAD_CLEAR_WEST_SLACK;
        return Math.max(ExileSaintClear.BL_SAFE_WEST, deckWest);
    }

    private static int[] parseOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] p = raw.trim().replace(",", " ").split("\\s+");
        if (p.length < 3) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
