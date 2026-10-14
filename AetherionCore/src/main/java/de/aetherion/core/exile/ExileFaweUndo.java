package de.aetherion.core.exile;

import de.aetherion.core.AetherionCore;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Best-effort FAWE disk-history undo (console RCON actor). Used to restore Borderlands
 * after an oversized quay clear.
 *
 * <p>FAWE {@code DiskStorageHistory#undo} takes an {@link Actor}, not an EditSession.
 */
public final class ExileFaweUndo {

    /** FAWE history folder from the RCON/console paste that cleared Borderlands. */
    public static final UUID CONSOLE_HISTORY = UUID.fromString("a233eb4b-4cab-42cd-9fd9-7e7b9a3f74be");

    private ExileFaweUndo() {
    }

    public static int undo(AetherionCore plugin, CommandSender sender, String worldName, UUID actor, int times) {
        if (plugin == null || times <= 0) {
            return 0;
        }
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            tell(sender, "§cFastAsyncWorldEdit missing.");
            return 0;
        }
        World world = Bukkit.getWorld(worldName == null ? "world" : worldName);
        if (world == null) {
            tell(sender, "§cWorld not loaded.");
            return 0;
        }
        UUID who = actor == null ? CONSOLE_HISTORY : actor;
        File histDir = new File(plugin.getDataFolder().getParentFile(),
                "FastAsyncWorldEdit/history/" + world.getName() + "/" + who);
        if (!histDir.isDirectory()) {
            tell(sender, "§cNo FAWE history at §f" + histDir.getPath());
            return 0;
        }
        List<Integer> indices = listIndices(histDir);
        if (indices.isEmpty()) {
            tell(sender, "§cFAWE history folder empty.");
            return 0;
        }
        // Newest first — LIFO like //undo.
        indices.sort(Comparator.reverseOrder());
        int done = 0;
        try {
            Class<?> disk = Class.forName("com.fastasyncworldedit.core.history.DiskStorageHistory");
            Constructor<?> ctorIdx = null;
            for (Constructor<?> c : disk.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 3 && p[1] == UUID.class && (p[2] == int.class || p[2] == Integer.class)) {
                    ctorIdx = c;
                    break;
                }
            }
            if (ctorIdx == null) {
                tell(sender, "§cFAWE DiskStorageHistory(world,uuid,index) not found.");
                return 0;
            }
            Method undoActor = null;
            for (Method m : disk.getMethods()) {
                if (!"undo".equals(m.getName()) || m.getParameterCount() != 1) {
                    continue;
                }
                if (Actor.class.isAssignableFrom(m.getParameterTypes()[0])
                        || m.getParameterTypes()[0].getName().endsWith(".Actor")) {
                    undoActor = m;
                    break;
                }
            }
            if (undoActor == null) {
                tell(sender, "§cFAWE history.undo(Actor) not found.");
                return 0;
            }
            Object weWorld = BukkitAdapter.adapt(world);
            Actor faweActor = BukkitAdapter.adapt(
                    sender != null ? sender : Bukkit.getConsoleSender());
            int limit = Math.min(times, indices.size());
            for (int i = 0; i < limit; i++) {
                int index = indices.get(i);
                Object history = ctorIdx.newInstance(weWorld, who, index);
                undoActor.invoke(history, faweActor);
                done++;
                plugin.getLogger().info("ExileFaweUndo applied index=" + index + " actor=" + who);
            }
            tell(sender, "§aFAWE undo ×" + done + " §7(indices " + indices.subList(0, done) + ") for §f" + who);
            plugin.getLogger().info("ExileFaweUndo done ×" + done + " actor=" + who + " world=" + world.getName());
        } catch (Throwable t) {
            Throwable root = t.getCause() != null ? t.getCause() : t;
            plugin.getLogger().log(Level.SEVERE, "ExileFaweUndo failed", root);
            tell(sender, "§cFAWE undo failed: §7" + root.getMessage());
        }
        return done;
    }

    private static List<Integer> listIndices(File histDir) {
        List<Integer> out = new ArrayList<>();
        File[] files = histDir.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".bd")) {
                continue;
            }
            try {
                out.add(Integer.parseInt(n.substring(0, n.length() - 3)));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static void tell(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }
}
