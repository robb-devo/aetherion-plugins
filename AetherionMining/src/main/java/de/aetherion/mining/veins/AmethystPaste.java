package de.aetherion.mining.veins;

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
 * Pastes the hand-built Amethyst Mines schematic into {@code aether_veins}.
 * This replaces the old procedural Deep Mines dig world.
 */
public final class AmethystPaste {

    private AmethystPaste() {
    }

    public static boolean paste(AetherionMining plugin, World world, CommandSender sender) {
        if (plugin == null || world == null) {
            return false;
        }
        String schemName = plugin.getConfig().getString("veins.schematic", "amethyst-mines.schem");
        File schem = resolveSchematic(plugin, schemName);
        if (schem == null || !schem.isFile()) {
            message(sender, "§cAmethyst schematic not found: " + schemName);
            message(sender, "§7Expected under plugins/WorldEdit/schematics/ or FastAsyncWorldEdit/schematics/");
            return false;
        }
        int x = plugin.getConfig().getInt("veins.paste.x", 0);
        int y = plugin.getConfig().getInt("veins.paste.y", 0);
        int z = plugin.getConfig().getInt("veins.paste.z", 0);
        boolean ignoreAir = plugin.getConfig().getBoolean("veins.ignore-air", false);

        message(sender, "§dPasting Amethyst Mines at §f" + x + " " + y + " " + z
                + " §d(ignoreAir=" + ignoreAir + ")…");
        world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);

        long start = System.currentTimeMillis();
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schem);
            if (format == null) {
                message(sender, "§cUnknown schematic format.");
                return false;
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
            message(sender, "§aAmethyst Mines pasted §7(" + ms + "ms) from §f" + schem.getName());
            plugin.getLogger().info("Amethyst Mines pasted into " + world.getName()
                    + " at " + x + "," + y + "," + z + " from " + schem.getAbsolutePath());
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Amethyst Mines paste failed", ex);
            message(sender, "§cPaste failed: " + ex.getMessage());
            return false;
        }
    }

    private static void message(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }

    private static File resolveSchematic(AetherionMining plugin, String name) {
        File plugins = plugin.getDataFolder().getParentFile();
        File[] candidates = {
                new File(plugins, "WorldEdit/schematics/" + name),
                new File(plugins, "FastAsyncWorldEdit/schematics/" + name),
                new File(plugin.getDataFolder(), "schematics/" + name),
                new File(Bukkit.getWorldContainer(), name)
        };
        for (File file : candidates) {
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
