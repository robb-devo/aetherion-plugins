package de.aetherion.core.wipe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Full player-progress reset. Runs at STARTUP, before other plugins load YAML.
 * World templates, NPCs, recipes and area configs stay.
 */
public final class BetaWipe {

    public static final String FLAG_NAME = "pending-beta-wipe";

    private static final String[] VANILLA_PLAYER_FOLDERS = {
            "playerdata", "stats", "advancements"
    };

    private final JavaPlugin plugin;
    private final File flag;
    private final File pluginsFolder;
    private final File serverRoot;

    public BetaWipe(JavaPlugin plugin) {
        this.plugin = plugin;
        this.flag = new File(plugin.getDataFolder(), FLAG_NAME);
        this.pluginsFolder = plugin.getDataFolder().getParentFile();
        this.serverRoot = pluginsFolder == null ? new File(".") : pluginsFolder.getParentFile();
    }

    public File flagFile() {
        return flag;
    }

    public void markPending() {
        writeFlag(flag);
        // Network: flag every Crafty backend + shared wipe dir so mmo-d wipes + NetworkWipeWatch stops it.
        int peers = 0;
        for (File remote : networkFlagFiles()) {
            writeFlag(remote);
            peers++;
        }
        plugin.getLogger().warning("Network wipe flags written to " + peers + " path(s) (peers + shared).");
        wipeSharedTransfer();
    }

    public boolean isPending() {
        if (flag.isFile()) {
            return true;
        }
        File shared = sharedWipeFlag();
        return shared != null && shared.isFile();
    }

    public void clearPending() {
        deleteFlag(flag);
        File shared = sharedWipeFlag();
        if (shared != null) {
            deleteFlag(shared);
        }
    }

    private void writeFlag(File target) {
        if (target == null) {
            return;
        }
        File folder = target.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create wipe flag folder: " + folder);
            return;
        }
        try {
            Files.writeString(target.toPath(), "beta\n");
            plugin.getLogger().warning("Wipe flag written: " + target.getAbsolutePath());
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not write wipe flag " + target + ": " + exception.getMessage());
        }
    }

    private void deleteFlag(File target) {
        if (target != null && target.isFile() && !target.delete()) {
            plugin.getLogger().warning("Could not remove wipe flag: " + target.getAbsolutePath());
        }
    }

    private File sharedWipeFlag() {
        File sharedRoot = sharedRoot();
        if (sharedRoot == null) {
            return null;
        }
        return new File(new File(sharedRoot, "wipe"), FLAG_NAME);
    }

    private File sharedRoot() {
        // /var/opt/minecraft/crafty/shared  (sibling of servers/)
        // serverRoot = .../crafty/servers/<uuid>
        File servers = serverRoot == null ? null : serverRoot.getParentFile();
        File crafty = servers == null ? null : servers.getParentFile();
        if (crafty == null) {
            return null;
        }
        File shared = new File(crafty, "shared");
        return shared.isDirectory() || shared.mkdirs() ? shared : null;
    }

    private List<File> networkFlagFiles() {
        List<File> out = new ArrayList<>();
        File shared = sharedWipeFlag();
        if (shared != null) {
            out.add(shared);
        }
        File serversDir = serverRoot == null ? null : serverRoot.getParentFile();
        File crafty = serversDir == null ? null : serversDir.getParentFile();
        File servers = crafty == null ? null : new File(crafty, "servers");
        if (servers == null || !servers.isDirectory()) {
            return out;
        }
        File[] children = servers.listFiles();
        if (children == null) {
            return out;
        }
        for (File server : children) {
            if (!server.isDirectory()) {
                continue;
            }
            File core = new File(server, "plugins/AetherionCore/" + FLAG_NAME);
            if (!core.getAbsolutePath().equalsIgnoreCase(flag.getAbsolutePath())) {
                out.add(core);
            }
        }
        return out;
    }

    private void wipeSharedTransfer() {
        File shared = sharedRoot();
        if (shared == null) {
            plugin.getLogger().warning("Shared root missing — cannot wipe crafty/shared progress.");
            return;
        }
        plugin.getLogger().warning("Wiping shared progress at " + shared.getAbsolutePath());
        int removed = deleteChildren(new File(shared, "transfer"));
        removed += deleteChildren(new File(shared, "progress"));
        // Explicit nested folders (junction targets under progress/AetherionItems etc.)
        File progress = new File(shared, "progress");
        removed += deleteChildren(new File(progress, "AetherionItems"));
        removed += deleteChildren(new File(progress, "AetherMobs"));
        removed += deleteChildren(new File(progress, "AetherionQuests"));
        for (String child : List.of("loadouts", "storage", "sacks", "pets", "quests", "coins.yml",
                "skills.yml", "progress.yml", "shards.yml", "player-ranks.yml")) {
            removed += deleteRecursively(new File(progress, child));
            removed += deleteRecursively(new File(new File(progress, "AetherionItems"), child));
        }
        plugin.getLogger().warning("Shared transfer/progress wipe removed " + removed + " files.");
        removed = wipePeerPlayerData();
        if (removed > 0) {
            plugin.getLogger().warning("Eager peer player-data wipe removed " + removed + " files.");
        }
    }

    private int wipePeerPlayerData() {
        File serversDir = serverRoot == null ? null : serverRoot.getParentFile();
        File crafty = serversDir == null ? null : serversDir.getParentFile();
        File servers = crafty == null ? null : new File(crafty, "servers");
        if (servers == null || !servers.isDirectory()) {
            return 0;
        }
        File[] children = servers.listFiles();
        if (children == null) {
            return 0;
        }
        int removed = 0;
        File localItems = new File(pluginsFolder, "AetherionItems");
        for (File server : children) {
            if (!server.isDirectory()) {
                continue;
            }
            File items = new File(server, "plugins/AetherionItems");
            if (items.isDirectory() && !items.getAbsolutePath().equalsIgnoreCase(localItems.getAbsolutePath())) {
                for (String file : List.of("coins.yml", "shards.yml", "skills.yml", "progress.yml",
                        "recipe_unlocks.yml", "player-ranks.yml", "market.yml",
                        "xp-boosts.yml", "codex.yml", "unlocked_blueprints.yml",
                        "areas.yml", "colosseum-unlock.yml")) {
                    removed += deleteRecursively(new File(items, file));
                }
                for (String folder : List.of("storage", "loadouts", "sacks")) {
                    removed += deleteChildren(new File(items, folder));
                }
            }
            File mobs = new File(server, "plugins/AetherMobs/pets");
            removed += deleteChildren(mobs);
            File quests = new File(server, "plugins/AetherionQuests");
            removed += deleteChildren(new File(quests, "players"));
            removed += deleteChildren(new File(quests, "data"));
            removed += deleteChildren(new File(server, "plugins/AetherionDungeons/transfer-snapshots"));
        }
        return removed;
    }

    public int run() {
        Logger log = plugin.getLogger();
        log.warning("Beta wipe: resetting player data to zero.");
        int removed = 0;

        removed += wipePlugin("AetherionItems",
                List.of("coins.yml", "shards.yml", "skills.yml", "progress.yml",
                        "recipe_unlocks.yml", "player-ranks.yml", "market.yml",
                        "xp-boosts.yml", "codex.yml", "voided-455.yml",
                        "unlocked_blueprints.yml", "areas.yml", "colosseum-unlock.yml"),
                List.of("storage", "loadouts", "sacks"));
        removed += wipePlugin("AetherionHub", List.of("players.yml"), List.of("players"));
        removed += wipePlugin("AetherionQuests", List.of("players.yml"), List.of("players", "data"));
        removed += wipePlugin("AetherMobs", List.of(), List.of("pets"));
        removed += wipePlugin("AetherionBeta", List.of(), List.of("players"));
        removed += wipePlugin("AetherionGuilds",
                List.of("guilds.yml", "personal_islands.yml", "friends.yml"),
                List.of());
        removed += wipePlugin("AetherionDungeons", List.of(), List.of("transfer-snapshots"));
        removed += wipePlugin("Essentials", List.of(), List.of("userdata"));
        removed += wipePlugin("EssentialsX", List.of(), List.of("userdata"));

        for (File world : worldFolders()) {
            String name = world.getName().toLowerCase(Locale.ROOT);
            if (isIslandWorld(name)) {
                log.warning("Deleting island world " + world.getName());
                removed += deleteRecursively(world);
                continue;
            }
            for (String folder : VANILLA_PLAYER_FOLDERS) {
                removed += deleteChildren(new File(world, folder));
            }
        }

        log.warning("Beta wipe finished. Removed " + removed + " files.");
        return removed;
    }

    private int wipePlugin(String name, List<String> files, List<String> folders) {
        File data = new File(pluginsFolder, name);
        if (!data.isDirectory()) {
            return 0;
        }
        int removed = 0;
        for (String file : files) {
            removed += deleteRecursively(new File(data, file));
        }
        for (String folder : folders) {
            removed += deleteChildren(new File(data, folder));
        }
        return removed;
    }

    private boolean isIslandWorld(String name) {
        for (String island : islandWorldNames()) {
            if (name.equals(island.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return name.equals("aether_guilds")
                || name.equals("aether_islands")
                || name.startsWith("aether_guild")
                || name.startsWith("aether_island");
    }

    private List<String> islandWorldNames() {
        List<String> names = new ArrayList<>();
        File config = new File(pluginsFolder, "AetherionGuilds/config.yml");
        if (!config.isFile()) {
            return names;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
        addName(names, yaml.getString("island-world"));
        addName(names, yaml.getString("personal-island-world"));
        return names;
    }

    private static void addName(List<String> names, String value) {
        if (value != null && !value.isBlank()) {
            names.add(value.trim());
        }
    }

    private List<File> worldFolders() {
        List<File> worlds = new ArrayList<>();
        File root = serverRoot == null ? new File(".") : serverRoot;
        File[] children = root.listFiles();
        if (children == null) {
            return worlds;
        }
        for (File child : children) {
            if (child.isDirectory() && new File(child, "level.dat").isFile()) {
                worlds.add(child);
            }
        }
        return worlds;
    }

    private int deleteChildren(File folder) {
        if (folder == null || !folder.isDirectory()) {
            return 0;
        }
        int removed = 0;
        File[] children = folder.listFiles();
        if (children == null) {
            return 0;
        }
        for (File child : children) {
            removed += deleteRecursively(child);
        }
        return removed;
    }

    private int deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        int removed = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    removed += deleteRecursively(child);
                }
            }
        }
        if (file.delete()) {
            return removed + 1;
        }
        plugin.getLogger().warning("Could not delete " + file.getAbsolutePath());
        return removed;
    }
}
