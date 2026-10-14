package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Wipes Seraphine / Hanging Saint leftovers at the old Borderlands pad without eating
 * Borderlands terrain (hard floor at {@link #BL_SAFE_WEST}).
 */
public final class ExileSaintClear {

    /**
     * Never clear west of this X (pad clamps / placequay). Borderlands cliff dress lives west.
     * Walkable Veil Quay deck at origin 600 starts ~x=536 — do not air that either.
     */
    public static final int BL_SAFE_WEST = 520;

    /** East end of the void-gap block clear — just west of the walkable pier head. */
    private static final int VOID_GAP_EAST = 535;

    private ExileSaintClear() {
    }

    public static void clear(AetherionCore plugin, CommandSender sender, String worldName) {
        if (plugin == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName == null || worldName.isBlank() ? "world" : worldName);
        if (world == null) {
            tell(sender, "§cWorld not loaded.");
            return;
        }
        tell(sender, "§8Seraphine §8» §7Clearing void-gap leftovers §fx="
                + BL_SAFE_WEST + "–" + VOID_GAP_EAST + "§7 + entity sweep…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final int changed;
                try (EditSession edit = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(BukkitAdapter.adapt(world))
                        .maxBlocks(-1)
                        .build()) {
                    try {
                        edit.setFastMode(true);
                    } catch (Throwable ignored) {
                    }
                    // Void gap only: east of Borderlands approach, west of Veil Quay deck.
                    // Never touch x<520 (BL) or x>535 (pier / quay).
                    edit.setBlocks(new CuboidRegion(
                            BlockVector3.at(BL_SAFE_WEST, 25, 270),
                            BlockVector3.at(VOID_GAP_EAST, 180, 390)
                    ), BlockTypes.AIR.getDefaultState());
                    changed = edit.getBlockChangeCount();
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int removed = 0;
                    for (Entity e : world.getEntities()) {
                        if (e instanceof Player) {
                            continue;
                        }
                        Location at = e.getLocation();
                        if (!nearPad(at.getBlockX(), at.getBlockZ())) {
                            continue;
                        }
                        String name = "";
                        if (e instanceof LivingEntity living && living.customName() != null) {
                            name = PlainTextComponentSerializer.plainText()
                                    .serialize(living.customName()).toLowerCase(Locale.ROOT);
                        }
                        String type = e.getType().name().toLowerCase(Locale.ROOT);
                        boolean kill = name.contains("seraph") || name.contains("saint")
                                || name.contains("hanging")
                                || type.contains("display")
                                || type.contains("armor_stand")
                                || type.contains("interaction")
                                || type.contains("item_frame")
                                || type.contains("painting");
                        if (kill) {
                            e.remove();
                            removed++;
                        }
                    }
                    tell(sender, "§aSeraphine leftovers cleared §7(" + changed + " gap blocks, "
                            + removed + " entities). Borderlands west of §f"
                            + BL_SAFE_WEST + "§7 and quay east of §f" + VOID_GAP_EAST + " §7untouched.");
                    plugin.getLogger().info("ExileSaintClear done blocks=" + changed
                            + " entities=" + removed);
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "ExileSaintClear failed", t);
                Bukkit.getScheduler().runTask(plugin, () ->
                        tell(sender, "§cSeraphine clear failed: §7" + t.getMessage()));
            }
        });
    }

    /** Entity sweep: cliff face through pier — displays only, no block edits. */
    private static boolean nearPad(int x, int z) {
        return x >= 480 && x <= 630 && z >= 260 && z <= 400;
    }

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
