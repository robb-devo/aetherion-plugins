package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Puts the Veil Quay's mooring chain back: every block of {@code veil_quay.schem} west of the walkable deck (the
 * chain and its pier stub) goes back where the world is air now. The Seraphine clears cut it across the void gap.
 * With {@code apply} it also lays the causeway again from its saved start (air only, recorded as before).
 * <p>
 * {@code /exile repairquay [apply]}: without {@code apply} it only reports.
 */
public final class ExileQuayRepair {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private ExileQuayRepair() {
    }

    public static void run(AetherionCore plugin, CommandSender sender, boolean apply) {
        String worldName = plugin.getConfig().getString("exile.quay.world", "world");
        String[] o = plugin.getConfig().getString("exile.quay.origin", "").trim().split("\\s+");
        World world = Bukkit.getWorld(worldName);
        if (world == null || o.length < 3) {
            tell(sender, "§cNo Veil Quay recorded (exile.quay.world / origin).");
            return;
        }
        int ox;
        int oy;
        int oz;
        try {
            ox = Integer.parseInt(o[0]);
            oy = Integer.parseInt(o[1]);
            oz = Integer.parseInt(o[2]);
        } catch (NumberFormatException ex) {
            tell(sender, "§cBad exile.quay.origin.");
            return;
        }
        File schem = ExileQuayPaste.resolve(plugin, "veil_quay.schem");
        if (schem == null || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            tell(sender, "§cNeeds FastAsyncWorldEdit and veil_quay.schem.");
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            tell(sender, "§cA quay repair is already running.");
            return;
        }
        int deckWest = ox + ExileQuayPaste.DECK_WEST;
        boolean turned = ExileQuay.turnedIn(plugin);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schem);
                Clipboard clipboard;
                try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
                    clipboard = reader.read();
                }
                BlockVector3 co = clipboard.getOrigin();
                Map<String, Integer> byMat = new TreeMap<>();
                int missing = 0;
                int standing = 0;
                try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(world)).maxBlocks(-1).build()) {
                    for (BlockVector3 p : clipboard.getRegion()) {
                        BlockVector3 rel = p.subtract(co);
                        if (rel.x() >= ExileQuayPaste.DECK_WEST) {
                            continue;
                        }
                        // turned 180°: the chain hangs east; its blocks (iron, slabs, bricks) read the same either way
                        BlockVector3 at = turned
                                ? BlockVector3.at(ox - rel.x(), oy + rel.y(), oz - rel.z())
                                : BlockVector3.at(ox + rel.x(), oy + rel.y(), oz + rel.z());
                        BaseBlock block = clipboard.getFullBlock(p);
                        if (block.getBlockType().getMaterial().isAir()) {
                            continue;
                        }
                        if (!edit.getBlock(at).getBlockType().getMaterial().isAir()) {
                            standing++;
                            continue;
                        }
                        missing++;
                        byMat.merge(block.getBlockType().id().replace("minecraft:", ""), 1, Integer::sum);
                        if (apply) {
                            edit.setBlock(at, block);
                        }
                    }
                }
                String line = "ExileQuayRepair " + (apply ? "put back " : "would put back ") + missing
                        + " chain blocks (" + standing + " still there) " + (turned ? "east of x=" + (ox - ExileQuayPaste.DECK_WEST)
                        : "west of x=" + deckWest) + " " + byMat;
                plugin.getLogger().info(line);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    RUNNING.set(false);
                    tell(sender, "§a" + line);
                    if (apply) {
                        ExileCauseway.relay(plugin, sender);
                    } else {
                        tell(sender, "§7Dry run. §f/exile repairquay apply §7also lays the causeway again.");
                    }
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "ExileQuayRepair failed", t);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    RUNNING.set(false);
                    tell(sender, "§cQuay repair failed: §7" + t);
                });
            }
        });
    }

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
