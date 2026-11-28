package de.aetherion.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * A visitor's sandbox. Somebody who already has a character comes to the isle without it (Core guest pass); what
 * they do here is kept in this backend's own player files and never leaves. When a new visit starts, whatever an
 * earlier visit left in those files is cleared first, so every visit is a first morning.
 *
 * <p>The same goes for a newcomer the character ledger does not know (a first join, or the first one after their
 * character was wiped): what this server still holds under their name is a leftover of an earlier visit, never a
 * character. Without this, a wiped player would start their new first morning with the pockets of their last
 * visit.
 *
 * <p>This only ever touches files of <b>this</b> server. {@link #isolated()} proves that before anything is
 * cleared: if a player-data folder here is a link into a folder other backends read, visits are switched off
 * altogether rather than risk a real character.
 */
final class Sandbox implements Listener {

    private static final String[] SHARED_SUSPECTS = {"AetherionItems", "AetherionQuests", "AetherMobs", "AetherionHub"};
    private static final String[] PLAYER_FOLDERS = {"storage", "loadouts", "sacks", "pets", "players", "data"};

    private final TutorialPlugin plugin;
    private final boolean isolated;

    Sandbox(TutorialPlugin plugin) {
        this.plugin = plugin;
        this.isolated = checkIsolation();
    }

    /** True when every player file this class would clear really lives inside this server's own plugins folder. */
    boolean isolated() {
        return isolated;
    }

    private boolean checkIsolation() {
        try {
            Path plugins = Bukkit.getPluginsFolder().toPath().toRealPath();
            for (String name : SHARED_SUSPECTS) {
                Plugin other = Bukkit.getPluginManager().getPlugin(name);
                File folder = other != null ? other.getDataFolder() : new File(Bukkit.getPluginsFolder(), name);
                if (!folder.exists()) {
                    continue;
                }
                if (Files.isSymbolicLink(folder.toPath()) || !folder.toPath().toRealPath().startsWith(plugins)) {
                    plugin.getLogger().severe("Not a sandbox: " + folder.getPath() + " points outside this server.");
                    return false;
                }
                Path real = folder.toPath().toRealPath();
                for (String sub : PLAYER_FOLDERS) {
                    File child = new File(folder, sub);
                    if (!child.exists()) {
                        continue;
                    }
                    if (Files.isSymbolicLink(child.toPath()) || !child.toPath().toRealPath().startsWith(real)) {
                        plugin.getLogger().severe("Not a sandbox: " + child.getPath() + " points outside this server.");
                        return false;
                    }
                }
                File[] files = folder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (Files.isSymbolicLink(file.toPath())) {
                            plugin.getLogger().severe("Not a sandbox: " + file.getPath() + " is a link.");
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not prove this server's player files are its own", ex);
            return false;
        }
    }

    /**
     * A visit that has not begun yet (no step written) starts on a clean slate: cleared here, on the main thread,
     * before any plugin has loaded this player.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!isolated || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID id = event.getUniqueId();
        boolean visitor = plugin.store().visiting(id) && plugin.store().unbegun(id);
        // never somebody the ledger knows: their character is on its way here, Core has laid it out already
        boolean newcomer = !visitor && plugin.store().fresh(id) && !plugin.hasCharacter(id);
        if (!visitor && !newcomer) {
            return;
        }
        try {
            Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                clear(id, visitor ? "visitor" : "newcomer");
                return Boolean.TRUE;
            }).get(8, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not clear the sandbox of " + event.getName(), ex);
        }
    }

    /** Main thread, player offline: forget everything an earlier visit left on this server. */
    private void clear(UUID id, String who) {
        if (Bukkit.getPlayer(id) != null) {
            return;
        }
        int cleared = 0;
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items != null) {
                if (items.getSkills() != null) {
                    items.getSkills().wipePlayer(id);
                    cleared++;
                }
                if (items.getCoins() != null) {
                    items.getCoins().wipePlayer(id);
                    cleared++;
                }
                if (items.getShards() != null) {
                    items.getShards().wipePlayer(id);
                    cleared++;
                }
                if (items.progress() != null) {
                    items.progress().wipePlayer(id);
                    cleared++;
                }
                if (items.getCodex() != null) {
                    items.getCodex().wipePlayer(id);
                    cleared++;
                }
                if (items.recipeUnlocks() != null) {
                    items.recipeUnlocks().wipePlayer(id);
                    cleared++;
                }
                if (items.blueprintUnlocks() != null) {
                    items.blueprintUnlocks().wipePlayer(id);
                    cleared++;
                }
                if (items.xpBoost() != null) {
                    items.xpBoost().wipePlayer(id);
                    cleared++;
                }
                new de.aetherion.items.storage.LoadoutManager(items).wipePlayer(id);
                cleared++;
                delete(new File(items.getDataFolder(), "storage/" + id + ".yml"));
                delete(new File(items.getDataFolder(), "sacks/" + id + ".yml"));
            }
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Sandbox: Items side not cleared for " + id, ex);
        }
        try {
            de.aetherion.quests.AetherionQuests quests = de.aetherion.quests.AetherionQuests.getInstance();
            if (quests != null && quests.getPlayerQuestStorage() != null) {
                quests.getPlayerQuestStorage().wipePlayer(id);
                if (quests.getQuestManager() != null) {
                    quests.getQuestManager().forgetPlayer(id);
                }
                delete(new File(quests.getDataFolder(), "data/" + id + ".yml"));
                cleared++;
            }
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Sandbox: Quests side not cleared for " + id, ex);
        }
        Plugin mobs = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (mobs != null) {
            delete(new File(mobs.getDataFolder(), "pets/" + id + ".yml"));
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            File world = Bukkit.getWorlds().get(0).getWorldFolder();
            delete(new File(world, "playerdata/" + id + ".dat"));
            delete(new File(world, "playerdata/" + id + ".dat_old"));
            delete(new File(world, "stats/" + id + ".json"));
            delete(new File(world, "advancements/" + id + ".json"));
        }
        plugin.getLogger().info("[Morningbell] Sandbox cleared for " + who + " " + id + " (" + cleared + " stores).");
    }

    private void delete(File file) {
        try {
            if (file.isFile() && !Files.isSymbolicLink(file.toPath())) {
                Files.delete(file.toPath());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Sandbox: could not delete " + file.getPath() + ": " + ex.getMessage());
        }
    }
}
