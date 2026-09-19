package de.aetherion.mining.island;

import de.aetherion.mining.AetherionMining;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.EditSession;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;

/**
 * One-shot paste of Mining Eldervale Island south of the Origin slime pad.
 */
public final class EldervalePaste {

    private EldervalePaste() {
    }

    public static void paste(AetherionMining plugin, CommandSender sender) {
        String worldName = plugin.getConfig().getString("eldervale.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: " + worldName);
            return;
        }
        String schemName = plugin.getConfig().getString("eldervale.schematic", "Mining-Eldervale-Island.schem");
        File schem = resolveSchematic(plugin, schemName);
        if (schem == null || !schem.isFile()) {
            sender.sendMessage("§cSchematic not found: " + schemName);
            sender.sendMessage("§7Expected under plugins/WorldEdit/schematics/");
            return;
        }
        int x = plugin.getConfig().getInt("eldervale.paste.x", 22);
        int y = plugin.getConfig().getInt("eldervale.paste.y", 90);
        int z = plugin.getConfig().getInt("eldervale.paste.z", 580);
        boolean ignoreAir = plugin.getConfig().getBoolean("eldervale.ignore-air", true);

        sender.sendMessage("§ePasting Eldervale at §f" + x + " " + y + " " + z
                + " §e(ignoreAir=" + ignoreAir + ") — may lag a few seconds…");
        world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);

        Bukkit.getScheduler().runTask(plugin, () -> {
            long start = System.currentTimeMillis();
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schem);
                if (format == null) {
                    sender.sendMessage("§cUnknown schematic format.");
                    return;
                }
                Clipboard clipboard;
                try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
                    clipboard = reader.read();
                }
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(world))
                        .maxBlocks(-1)
                        .build()) {
                    Operation op = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(x, y, z))
                            .ignoreAirBlocks(ignoreAir)
                            .build();
                    Operations.complete(op);
                }
                long ms = System.currentTimeMillis() - start;
                sender.sendMessage("§aEldervale pasted in §f" + ms + "ms§a.");
                reloadHubPads(world, sender);
                sender.sendMessage("§7Return slime pad ready. Test bounce from §f22 55 305§7.");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Eldervale paste failed", t);
                sender.sendMessage("§cPaste failed: " + t.getMessage());
            }
        });
    }

    private static void reloadHubPads(World world, CommandSender sender) {
        org.bukkit.plugin.Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (hub == null || !hub.isEnabled()) {
            return;
        }
        try {
            Object pads = hub.getClass().getMethod("getLaunchPads").invoke(hub);
            if (pads == null) {
                return;
            }
            pads.getClass().getMethod("placeReturnPad", World.class).invoke(pads, world);
            pads.getClass().getMethod("reload").invoke(pads);
            sender.sendMessage("§7Hub jump pads reloaded.");
        } catch (ReflectiveOperationException ignored) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hubadmin reloadpads");
        }
    }

    private static File resolveSchematic(AetherionMining plugin, String schemName) {
        File we = new File(plugin.getDataFolder().getParentFile(), "WorldEdit/schematics/" + schemName);
        if (we.isFile()) {
            return we;
        }
        File local = new File(plugin.getDataFolder(), "schematics/" + schemName);
        if (local.isFile()) {
            return local;
        }
        return we;
    }
}
