package de.aetherion.foraging.island;

import de.aetherion.foraging.AetherionForaging;

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
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;

/**
 * Paste / clear Crystal Forage Isle. Plugin pastes are NOT in WorldEdit //undo —
 * use {@link #clear} to remove the last paste footprint.
 */
public final class ForageIslePaste {

    private ForageIslePaste() {
    }

    public static void paste(AetherionForaging plugin, CommandSender sender) {
        paste(plugin, sender, null);
    }

    /**
     * @param atPlayer if non-null, schematic AABB center is placed on the player's feet block.
     */
    public static void paste(AetherionForaging plugin, CommandSender sender, Location atPlayer) {
        String worldName = plugin.getConfig().getString("forage-isle.world", "world");
        World world = atPlayer != null && atPlayer.getWorld() != null
                ? atPlayer.getWorld()
                : Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: " + worldName);
            return;
        }
        String schemName = plugin.getConfig().getString("forage-isle.schematic", "Forage-Crystal-Isle.schem");
        File schem = resolveSchematic(plugin, schemName);
        if (schem == null || !schem.isFile()) {
            sender.sendMessage("§cSchematic not found: " + schemName);
            sender.sendMessage("§7Expected under plugins/WorldEdit/schematics/");
            return;
        }

        boolean ignoreAir = plugin.getConfig().getBoolean("forage-isle.ignore-air", true);
        int rotateY = normalizeRotateY(plugin.getConfig().getInt("forage-isle.rotate-y", 0));
        boolean centerOnPlayer = atPlayer != null;

        if (centerOnPlayer) {
            sender.sendMessage("§ePasting Forage Isle centered on you at §f"
                    + atPlayer.getBlockX() + " " + atPlayer.getBlockY() + " " + atPlayer.getBlockZ()
                    + " §e(rotateY=" + rotateY + ", ignoreAir=" + ignoreAir + ") — may lag…");
        } else {
            int x = plugin.getConfig().getInt("forage-isle.paste.x", 450);
            int y = plugin.getConfig().getInt("forage-isle.paste.y", 90);
            int z = plugin.getConfig().getInt("forage-isle.paste.z", -500);
            sender.sendMessage("§ePasting Forage Isle at config origin §f" + x + " " + y + " " + z
                    + " §e(rotateY=" + rotateY + ", ignoreAir=" + ignoreAir + ") — may lag…");
        }

        final Location centerTarget = atPlayer != null ? atPlayer.clone() : null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            long start = System.currentTimeMillis();
            try {
                Clipboard clipboard = readClipboard(schem);
                if (clipboard == null) {
                    sender.sendMessage("§cCould not read schematic.");
                    return;
                }

                int px;
                int py;
                int pz;
                if (centerTarget != null) {
                    BlockVector3 pasteAt = pasteOriginForCenter(
                            clipboard,
                            centerTarget.getBlockX(),
                            centerTarget.getBlockY(),
                            centerTarget.getBlockZ());
                    px = pasteAt.getBlockX();
                    py = pasteAt.getBlockY();
                    pz = pasteAt.getBlockZ();

                    plugin.getConfig().set("forage-isle.world", world.getName());
                    plugin.getConfig().set("forage-isle.paste.x", px);
                    plugin.getConfig().set("forage-isle.paste.y", py);
                    plugin.getConfig().set("forage-isle.paste.z", pz);
                    // Return pad around where you stood (island center).
                    int cx = centerTarget.getBlockX();
                    int cy = centerTarget.getBlockY();
                    int cz = centerTarget.getBlockZ();
                    plugin.getConfig().set("forage-isle.return-platform.min.x", cx - 2);
                    plugin.getConfig().set("forage-isle.return-platform.min.y", cy - 1);
                    plugin.getConfig().set("forage-isle.return-platform.min.z", cz - 2);
                    plugin.getConfig().set("forage-isle.return-platform.max.x", cx + 2);
                    plugin.getConfig().set("forage-isle.return-platform.max.y", cy - 1);
                    plugin.getConfig().set("forage-isle.return-platform.max.z", cz + 2);
                    plugin.saveConfig();

                    BlockVector3 dims = clipboard.getDimensions();
                    sender.sendMessage("§7Schem size §f" + dims.getBlockX() + "×" + dims.getBlockY()
                            + "×" + dims.getBlockZ() + " §7· WE paste-origin §f" + px + " " + py + " " + pz);
                } else {
                    px = plugin.getConfig().getInt("forage-isle.paste.x", 450);
                    py = plugin.getConfig().getInt("forage-isle.paste.y", 90);
                    pz = plugin.getConfig().getInt("forage-isle.paste.z", -500);
                }

                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(world))
                        .maxBlocks(-1)
                        .build()) {
                    // Large floating isles (500³+) must skip history or the main thread
                    // + Paper watchdog die mid-paste (OOM / NPE on flush).
                    editSession.setFastMode(true);
                    ClipboardHolder holder = new ClipboardHolder(clipboard);
                    if (rotateY != 0) {
                        holder.setTransform(new AffineTransform().rotateY(rotateY));
                    }
                    Operation op = holder
                            .createPaste(editSession)
                            .to(BlockVector3.at(px, py, pz))
                            .ignoreAirBlocks(ignoreAir)
                            .build();
                    Operations.complete(op);
                }
                long ms = System.currentTimeMillis() - start;
                if (centerTarget != null) {
                    sender.sendMessage("§aForage Isle pasted in §f" + ms + "ms§a — center at §f"
                            + centerTarget.getBlockX() + " " + centerTarget.getBlockY() + " "
                            + centerTarget.getBlockZ() + "§a.");
                } else {
                    sender.sendMessage("§aForage Isle pasted in §f" + ms + "ms§a at §f"
                            + px + " " + py + " " + pz + "§a.");
                }
                placeReturnPad(plugin, world);
                syncHubForagePads(plugin, world, sender);
                reloadHubPads(sender);
                sender.sendMessage("§7Clear anytime with §f/forage isle clear§7 (not WorldEdit //undo).");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Forage Isle paste failed", t);
                sender.sendMessage("§cPaste failed: " + t.getMessage());
            }
        });
    }

    /**
     * WorldEdit {@code .to(pasteAt)} places clipboard origin at pasteAt.
     * We want the schematic AABB center at (tx,ty,tz).
     */
    static BlockVector3 pasteOriginForCenter(Clipboard clipboard, int tx, int ty, int tz) {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        BlockVector3 origin = clipboard.getOrigin();
        int midX = (min.getBlockX() + max.getBlockX()) / 2;
        int midY = (min.getBlockY() + max.getBlockY()) / 2;
        int midZ = (min.getBlockZ() + max.getBlockZ()) / 2;
        return BlockVector3.at(
                tx + origin.getBlockX() - midX,
                ty + origin.getBlockY() - midY,
                tz + origin.getBlockZ() - midZ);
    }

    /**
     * Remove blocks that the schematic placed at the configured paste origin
     * (only non-air schem blocks — surrounding terrain stays). Also removes return pad.
     */
    public static void clear(AetherionForaging plugin, CommandSender sender) {
        String worldName = plugin.getConfig().getString("forage-isle.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: " + worldName);
            return;
        }
        String schemName = plugin.getConfig().getString("forage-isle.schematic", "Forage-Crystal-Isle.schem");
        File schem = resolveSchematic(plugin, schemName);
        if (schem == null || !schem.isFile()) {
            sender.sendMessage("§cSchematic not found — cannot compute clear footprint.");
            return;
        }
        int x = plugin.getConfig().getInt("forage-isle.paste.x", 450);
        int y = plugin.getConfig().getInt("forage-isle.paste.y", 90);
        int z = plugin.getConfig().getInt("forage-isle.paste.z", -500);
        int rotateY = normalizeRotateY(plugin.getConfig().getInt("forage-isle.rotate-y", 0));

        sender.sendMessage("§eClearing Forage Isle footprint at §f" + x + " " + y + " " + z
                + " §e(rotateY=" + rotateY + ")…");

        Bukkit.getScheduler().runTask(plugin, () -> {
            long start = System.currentTimeMillis();
            try {
                Clipboard clipboard = readClipboard(schem);
                if (clipboard == null) {
                    sender.sendMessage("§cCould not read schematic.");
                    return;
                }
                BlockVector3 pasteAt = BlockVector3.at(x, y, z);
                BlockVector3 origin = clipboard.getOrigin();
                AffineTransform transform = rotateY == 0
                        ? null
                        : new AffineTransform().rotateY(rotateY);
                int cleared = 0;
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(world))
                        .maxBlocks(-1)
                        .build()) {
                    editSession.setFastMode(true);
                    for (BlockVector3 pos : clipboard.getRegion()) {
                        var state = clipboard.getBlock(pos);
                        if (state.getBlockType().getMaterial().isAir()) {
                            continue;
                        }
                        BlockVector3 offset = pos.subtract(origin);
                        BlockVector3 worldPos;
                        if (transform == null) {
                            worldPos = pasteAt.add(offset);
                        } else {
                            var v = transform.apply(offset.toVector3()).round();
                            worldPos = pasteAt.add(BlockVector3.at(v.getX(), v.getY(), v.getZ()));
                        }
                        editSession.setBlock(worldPos, BlockTypes.AIR.getDefaultState());
                        cleared++;
                    }
                }
                clearReturnPad(plugin, world);
                long ms = System.currentTimeMillis() - start;
                sender.sendMessage("§aCleared §f" + cleared + "§a schem blocks (+ return pad) in §f" + ms + "ms§a.");
                sender.sendMessage("§7Stand at the island center, then §f/forage isle paste here§7.");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Forage Isle clear failed", t);
                sender.sendMessage("§cClear failed: " + t.getMessage());
            }
        });
    }

    private static int normalizeRotateY(int degrees) {
        int d = degrees % 360;
        if (d < 0) {
            d += 360;
        }
        // Snap to 90° steps — schem block rotates are only meaningful there.
        return ((d + 45) / 90) * 90 % 360;
    }

    private static Clipboard readClipboard(File schem) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(schem);
        if (format == null) {
            return null;
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
            return reader.read();
        }
    }

    private static void placeReturnPad(AetherionForaging plugin, World world) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("forage-isle.return-platform");
        if (s == null || world == null) {
            return;
        }
        int minX = s.getInt("min.x");
        int minY = s.getInt("min.y");
        int minZ = s.getInt("min.z");
        int maxX = s.getInt("max.x");
        int maxY = s.getInt("max.y");
        int maxZ = s.getInt("max.z");
        for (int bx = minX - 1; bx <= maxX + 1; bx++) {
            for (int bz = minZ - 1; bz <= maxZ + 1; bz++) {
                world.getBlockAt(bx, minY - 1, bz).setType(Material.STONE, false);
            }
        }
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    world.getBlockAt(bx, by, bz).setType(Material.SLIME_BLOCK, false);
                }
            }
        }
    }

    /**
     * Update hub pad targets from return-platform config without placing blocks
     * (keeps a player-built slime pad intact).
     */
    public static void syncPadsOnly(AetherionForaging plugin, CommandSender sender) {
        String worldName = plugin.getConfig().getString("forage-isle.world", "world");
        World world = Bukkit.getWorld(worldName);
        syncHubForagePads(plugin, world, sender);
        reloadHubPads(sender);
    }

    /**
     * Wire harbour ↔ forage return slime in AetherionHub island-pads config.
     */
    private static void syncHubForagePads(AetherionForaging plugin, World world, CommandSender sender) {
        Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (hub == null || !hub.isEnabled()) {
            return;
        }
        ConfigurationSection ret = plugin.getConfig().getConfigurationSection("forage-isle.return-platform");
        if (ret == null) {
            return;
        }
        int minX = ret.getInt("min.x");
        int minY = ret.getInt("min.y");
        int minZ = ret.getInt("min.z");
        int maxX = ret.getInt("max.x");
        int maxY = ret.getInt("max.y");
        int maxZ = ret.getInt("max.z");
        double landX = (minX + maxX) * 0.5 + 0.5;
        double landY = maxY + 1.0;
        double landZ = (minZ + maxZ) * 0.5 + 0.5;

        var cfg = hub.getConfig();
        String worldName = world != null ? world.getName() : "world";

        cfg.set("island-pads.pads.forage_to_origin.world", worldName);
        cfg.set("island-pads.pads.forage_to_origin.min.x", minX);
        cfg.set("island-pads.pads.forage_to_origin.min.y", minY);
        cfg.set("island-pads.pads.forage_to_origin.min.z", minZ);
        cfg.set("island-pads.pads.forage_to_origin.max.x", maxX);
        cfg.set("island-pads.pads.forage_to_origin.max.y", maxY);
        cfg.set("island-pads.pads.forage_to_origin.max.z", maxZ);
        cfg.set("island-pads.pads.forage_to_origin.require-stamped-blueprint", false);

        cfg.set("island-pads.pads.origin_to_forage.target.x", landX);
        cfg.set("island-pads.pads.origin_to_forage.target.y", landY);
        cfg.set("island-pads.pads.origin_to_forage.target.z", landZ);
        cfg.set("island-pads.pads.origin_to_forage.require-stamped-blueprint", false);

        ConfigurationSection origin = cfg.getConfigurationSection("island-pads.pads.origin_to_forage");
        if (origin != null) {
            int oMinX = origin.getInt("min.x", 436);
            int oMinY = origin.getInt("min.y", 55);
            int oMinZ = origin.getInt("min.z", -236);
            int oMaxX = origin.getInt("max.x", 440);
            int oMaxY = origin.getInt("max.y", 55);
            int oMaxZ = origin.getInt("max.z", -232);
            cfg.set("island-pads.pads.forage_to_origin.target.x", (oMinX + oMaxX) * 0.5 + 0.5);
            cfg.set("island-pads.pads.forage_to_origin.target.y", oMaxY + 1.0);
            cfg.set("island-pads.pads.forage_to_origin.target.z", (oMinZ + oMaxZ) * 0.5 + 0.5);
        }

        if (!cfg.isSet("island-pads.pads.forage_to_origin.arc-height")) {
            cfg.set("island-pads.pads.forage_to_origin.arc-height", 55);
            cfg.set("island-pads.pads.forage_to_origin.horiz-speed", 3.15);
            cfg.set("island-pads.pads.forage_to_origin.boost-ticks", 70);
            cfg.set("island-pads.pads.forage_to_origin.cooldown-ms", 1500);
        }
        if (!cfg.isSet("island-pads.pads.origin_to_forage.arc-height")) {
            cfg.set("island-pads.pads.origin_to_forage.arc-height", 55);
            cfg.set("island-pads.pads.origin_to_forage.horiz-speed", 3.15);
            cfg.set("island-pads.pads.origin_to_forage.boost-ticks", 70);
            cfg.set("island-pads.pads.origin_to_forage.cooldown-ms", 1500);
        }

        hub.saveConfig();
        sender.sendMessage("§7Hub pads linked: harbour ↔ forage slime §f"
                + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", landX, landY, landZ));
    }

    private static void clearReturnPad(AetherionForaging plugin, World world) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("forage-isle.return-platform");
        if (s == null || world == null) {
            return;
        }
        int minX = s.getInt("min.x") - 1;
        int minY = s.getInt("min.y") - 1;
        int minZ = s.getInt("min.z") - 1;
        int maxX = s.getInt("max.x") + 1;
        int maxY = s.getInt("max.y");
        int maxZ = s.getInt("max.z") + 1;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    world.getBlockAt(bx, by, bz).setType(Material.AIR, false);
                }
            }
        }
    }

    private static void reloadHubPads(CommandSender sender) {
        Plugin hub = Bukkit.getPluginManager().getPlugin("AetherionHub");
        if (hub == null || !hub.isEnabled()) {
            return;
        }
        try {
            Object pads = hub.getClass().getMethod("getLaunchPads").invoke(hub);
            if (pads != null) {
                pads.getClass().getMethod("reload").invoke(pads);
                sender.sendMessage("§7Hub island pads reloaded.");
            }
        } catch (ReflectiveOperationException ex) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hubadmin reloadpads");
        }
    }

    private static File resolveSchematic(AetherionForaging plugin, String schemName) {
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
