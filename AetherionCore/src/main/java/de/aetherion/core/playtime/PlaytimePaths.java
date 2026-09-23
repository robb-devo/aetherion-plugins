package de.aetherion.core.playtime;

import de.aetherion.core.wipe.WipeLayout;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lifetime playtime directory. Crafty backends share {@code <shared>/playtime}
 * (outside world folders, and outside the beta-wipe {@code progress} / {@code transfer} trees).
 * A server without that shared root uses {@code plugins/AetherionCore/playtime}.
 */
public final class PlaytimePaths {

    public static final String DIRECTORY_NAME = "playtime";

    private PlaytimePaths() {
    }

    public static File resolve(JavaPlugin plugin) {
        String directory = plugin.getConfig().getString("playtime.directory", "");
        String sharedRoot = plugin.getConfig().getString("wipe.shared-root", "");
        File data = plugin.getDataFolder();
        File plugins = data == null ? null : data.getParentFile();
        File serverRoot = plugins == null ? new File(".") : plugins.getParentFile();
        if (serverRoot == null) {
            serverRoot = new File(".");
        }
        return resolve(directory, sharedRoot, WipeLayout.deriveSharedRoot(serverRoot), data);
    }

    /**
     * @param configuredDirectory {@code playtime.directory}; blank uses the shared or plugin folder
     * @param configuredSharedRoot {@code wipe.shared-root}; blank derives Crafty shared only if that directory exists
     * @param derivedSharedRoot candidate from the server path; used only when it already exists
     * @param pluginDataFolder {@code plugins/AetherionCore}
     */
    public static File resolve(String configuredDirectory, String configuredSharedRoot,
                               File derivedSharedRoot, File pluginDataFolder) {
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return new File(configuredDirectory.trim());
        }
        if (configuredSharedRoot != null && !configuredSharedRoot.isBlank()) {
            return new File(new File(configuredSharedRoot.trim()), DIRECTORY_NAME);
        }
        if (derivedSharedRoot != null && derivedSharedRoot.isDirectory()) {
            return new File(derivedSharedRoot, DIRECTORY_NAME);
        }
        File data = pluginDataFolder == null ? new File(".") : pluginDataFolder;
        return new File(data, DIRECTORY_NAME);
    }

    /** True when {@code candidate} is the store directory or a file inside it. */
    public static boolean isInside(File store, File candidate) {
        if (store == null || candidate == null) {
            return false;
        }
        Path storePath = absolute(store);
        Path filePath = absolute(candidate);
        return filePath.startsWith(storePath);
    }

    private static Path absolute(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        try {
            if (Files.exists(path)) {
                return path.toRealPath();
            }
        } catch (IOException ignored) {
            // Fall back to the normalized path when the file cannot be canonicalized.
        }
        return path;
    }
}
