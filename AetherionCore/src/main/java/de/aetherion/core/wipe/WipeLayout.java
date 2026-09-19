package de.aetherion.core.wipe;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Crafty path resolution for {@link BetaWipe} / {@link NetworkWipeWatch}.
 * Blank config keys preserve the historical layout:
 * {@code <server>/../.. /shared} and every folder under {@code <crafty>/servers/}.
 */
public final class WipeLayout {

    /** Documented production path; used only when {@code wipe.shared-root} is set to it. */
    public static final String DEFAULT_PRODUCTION_SHARED_ROOT = "/var/opt/minecraft/crafty/shared";

    private final JavaPlugin plugin;
    private final File serverRoot;
    private final String configuredSharedRoot;
    private final List<String> serverAllowlist;
    private final boolean dryRun;

    public WipeLayout(JavaPlugin plugin, File serverRoot) {
        this.plugin = plugin;
        this.serverRoot = serverRoot;
        FileConfiguration cfg = plugin.getConfig();
        this.configuredSharedRoot = cfg == null ? "" : nullToEmpty(cfg.getString("wipe.shared-root", ""));
        List<String> listed = cfg == null ? List.of() : cfg.getStringList("wipe.servers");
        this.serverAllowlist = listed == null ? List.of() : List.copyOf(listed);
        this.dryRun = cfg != null && cfg.getBoolean("wipe.dry-run", false);
    }

    public boolean dryRun() {
        return dryRun;
    }

    /**
     * Unresolved shared folder (does not mkdir). Empty config → parent of {@code servers/}.
     */
    public File sharedRootCandidate() {
        if (!configuredSharedRoot.isBlank()) {
            return new File(configuredSharedRoot.trim());
        }
        return deriveSharedRoot(serverRoot);
    }

    static File deriveSharedRoot(File serverRoot) {
        File servers = serverRoot == null ? null : serverRoot.getParentFile();
        File crafty = servers == null ? null : servers.getParentFile();
        if (crafty == null) {
            return null;
        }
        return new File(crafty, "shared");
    }

    /**
     * Same as the old hardcoded helper: return the directory, creating it when allowed.
     */
    public File sharedRoot(boolean createIfMissing) {
        File shared = sharedRootCandidate();
        if (shared == null) {
            return null;
        }
        if (shared.isDirectory()) {
            return shared;
        }
        if (!createIfMissing) {
            return null;
        }
        return shared.mkdirs() ? shared : null;
    }

    public File sharedWipeFlag(boolean createSharedIfMissing) {
        File shared = sharedRoot(createSharedIfMissing);
        if (shared == null) {
            return null;
        }
        return new File(new File(shared, "wipe"), BetaWipe.FLAG_NAME);
    }

    public File craftyServersDir() {
        File serversDir = serverRoot == null ? null : serverRoot.getParentFile();
        File crafty = serversDir == null ? null : serversDir.getParentFile();
        return crafty == null ? null : new File(crafty, "servers");
    }

    /**
     * Crafty backend folders to flag / peer-wipe. Empty allowlist = every directory
     * under {@code crafty/servers/} (historical production).
     */
    public List<File> peerServerFolders() {
        List<File> all = listServerFolders(craftyServersDir());
        if (serverAllowlist.isEmpty()) {
            return all;
        }
        List<File> matched = new ArrayList<>();
        for (String raw : serverAllowlist) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            File resolved = resolveAllowlistEntry(entry, all);
            if (resolved == null) {
                plugin.getLogger().warning("Wipe allowlist entry not found: " + entry);
                continue;
            }
            if (!containsPath(matched, resolved)) {
                matched.add(resolved);
            }
        }
        return matched;
    }

    public List<File> networkFlagFiles(File localFlag) {
        List<File> out = new ArrayList<>();
        File shared = sharedWipeFlag(!dryRun);
        if (shared != null) {
            out.add(shared);
        }
        for (File server : peerServerFolders()) {
            File core = new File(server, "plugins/AetherionCore/" + BetaWipe.FLAG_NAME);
            if (localFlag != null && core.getAbsolutePath().equalsIgnoreCase(localFlag.getAbsolutePath())) {
                continue;
            }
            out.add(core);
        }
        return out;
    }

    public String describe() {
        File shared = sharedRootCandidate();
        String servers = serverAllowlist.isEmpty()
                ? "(all crafty/servers)"
                : serverAllowlist.toString();
        return "shared-root=" + (shared == null ? "none" : shared.getAbsolutePath())
                + " servers=" + servers
                + " dry-run=" + dryRun;
    }

    private File resolveAllowlistEntry(String entry, List<File> all) {
        File asFile = new File(entry);
        if (asFile.isAbsolute()) {
            return asFile.isDirectory() ? asFile : null;
        }
        for (File server : all) {
            if (server.getName().equalsIgnoreCase(entry)) {
                return server;
            }
        }
        File servers = craftyServersDir();
        if (servers != null) {
            File relative = new File(servers, entry);
            if (relative.isDirectory()) {
                return relative;
            }
        }
        return null;
    }

    static List<File> listServerFolders(File servers) {
        List<File> out = new ArrayList<>();
        if (servers == null || !servers.isDirectory()) {
            return out;
        }
        File[] children = servers.listFiles();
        if (children == null) {
            return out;
        }
        for (File server : children) {
            if (server.isDirectory()) {
                out.add(server);
            }
        }
        return out;
    }

    private static boolean containsPath(List<File> files, File candidate) {
        String path = candidate.getAbsolutePath();
        for (File file : files) {
            if (file.getAbsolutePath().equalsIgnoreCase(path)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return describe();
    }
}
