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
 * Pastes {@code exil_gesamt.schem} into {@code aetherion_exile} at the design origin
 * (clipboard origin / {@code //paste -o}). Console-safe, runs off the main thread.
 */
public final class ExileMapPaste {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private ExileMapPaste() {
    }

    public static boolean running() {
        return RUNNING.get();
    }

    public static void start(AetherionCore plugin, CommandSender sender) {
        if (plugin == null) {
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            tell(sender, "§cExile paste already running.");
            return;
        }
        String worldName = plugin.getConfig().getString("exile.world", "aetherion_exile");
        World world = Bukkit.getWorld(worldName);
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
        File schem = resolve(plugin, "exil_gesamt.schem");
        if (schem == null) {
            RUNNING.set(false);
            tell(sender, "§cMissing §fexil_gesamt.schem §7(FAWE/WorldEdit schematics/).");
            return;
        }
        tell(sender, "§8Exil §8» §7Pasting §fexil_gesamt §7into §f" + worldName + "§7 (void origin)…");
        plugin.getLogger().info("ExileMapPaste start file=" + schem.getAbsolutePath() + " world=" + worldName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long start = System.currentTimeMillis();
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schem);
                if (format == null) {
                    fail(plugin, sender, "Unknown schematic format.");
                    return;
                }
                Clipboard clipboard;
                try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
                    clipboard = reader.read();
                }
                BlockVector3 origin = clipboard.getOrigin();
                BlockVector3 dims = clipboard.getDimensions();
                BlockVector3 min = clipboard.getMinimumPoint();
                BlockVector3 max = clipboard.getMaximumPoint();
                plugin.getLogger().info("ExileMapPaste loaded size="
                        + dims.x() + "x" + dims.y() + "x" + dims.z()
                        + " origin=" + origin.x() + "," + origin.y() + "," + origin.z());
                int changed;
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(world))
                        .maxBlocks(-1)
                        .build()) {
                    try {
                        editSession.setFastMode(true);
                    } catch (Throwable ignored) {
                    }
                    // Strip leftover overworld shell around the island so it reads as void.
                    int pad = 48;
                    editSession.setBlocks(new CuboidRegion(
                            BlockVector3.at(min.x() - pad, world.getMinHeight(), min.z() - pad),
                            BlockVector3.at(max.x() + pad, world.getMaxHeight(), max.z() + pad)
                    ), BlockTypes.AIR.getDefaultState());
                    Operation op = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(origin)
                            .ignoreAirBlocks(false)
                            .copyEntities(false)
                            .copyBiomes(false)
                            .build();
                    Operations.complete(op);
                    changed = editSession.getBlockChangeCount();
                }
                long ms = System.currentTimeMillis() - start;
                String done = "ExileMapPaste done blocks=" + changed + " elapsed=" + ms + "ms";
                plugin.getLogger().info(done);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    RUNNING.set(false);
                    tell(sender, "§aExile island pasted §7(" + changed + " blocks, " + ms + "ms).");
                    applyWorldSettings(world);
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "ExileMapPaste failed", t);
                fail(plugin, sender, t.getMessage() == null ? "paste failed" : t.getMessage());
            }
        });
    }

    private static void fail(AetherionCore plugin, CommandSender sender, String why) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            RUNNING.set(false);
            tell(sender, "§cExile paste failed: §7" + why);
        });
    }

    private static void applyWorldSettings(World world) {
        if (world == null) {
            return;
        }
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
        world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(12400L);
        world.setStorm(false);
        world.setThundering(false);
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

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
