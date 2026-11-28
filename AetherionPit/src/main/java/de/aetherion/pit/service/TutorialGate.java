package de.aetherion.pit.service;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.network.ServerNames;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Where "play" leads from the lobby: the main world, or first Morningbell, the tutorial isle.
 *
 * <p>A player who has no character yet starts on the isle; one who left it half-way goes back to it; everybody
 * else goes to the main world as before. The decision is read from two folders every backend shares: the
 * tutorial store ({@code <shared>/tutorial/<uuid>.yml}, written here and by AetherionTutorial) and the character
 * ledger. "Has a character" means: the transfer ledger knows them, or the main world's Quests has a file for
 * them. A veteran is never sent to the isle as a newcomer: their character would be taken there.
 *
 * <p>{@code /tutorial} in the lobby is the other way in: a visit, for players who do have a character. The isle
 * lets a visitor in without it (Core guest pass), nothing of it is touched.
 */
public final class TutorialGate {

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String isle;
    private final File store;
    private final File ledger;
    private final File mainQuests;
    /** Tries to reach the isle that did not take the player off this server (it is down): time of the last one. */
    private final Map<UUID, long[]> failed = new ConcurrentHashMap<>();

    public TutorialGate(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("tutorial.enabled", false);
        String server = plugin.getConfig().getString("tutorial.server", ServerNames.TUTORIAL);
        this.isle = server == null || server.isBlank() ? ServerNames.TUTORIAL : server.trim().toLowerCase(Locale.ROOT);
        File transfer = ServerNames.snapshotDir(AetherionCore.get());
        File shared = transfer.getParentFile();
        this.store = dir("tutorial.store-dir", new File(shared, "tutorial"));
        this.ledger = dir("tutorial.ledger-dir", new File(transfer, "ledger"));
        this.mainQuests = dir("tutorial.main-quests-dir", new File(shared, "progress/AetherionQuests/players"));
        if (enabled) {
            plugin.getLogger().info("Tutorial gate on: newcomers start on " + isle + " (store " + store.getAbsolutePath() + ").");
        }
    }

    private File dir(String key, File fallback) {
        String configured = plugin.getConfig().getString(key, "");
        return configured == null || configured.isBlank() ? fallback : new File(configured.trim());
    }

    public boolean enabled() {
        return enabled;
    }

    public String isleServer() {
        return isle;
    }

    /** True when this player's "play" leads to the isle; writes the newcomer's ticket when it does. */
    public boolean toIsle(Player player) {
        if (!enabled || player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (givenUp(id)) {
            return false;
        }
        YamlConfiguration entry = read(id);
        String state = entry.getString("state", "new").toLowerCase(Locale.ROOT);
        boolean guest = entry.getBoolean("guest", false);
        if (state.equals("in_progress") && !guest) {
            return true;
        }
        if (state.equals("in_progress")) {
            // a visit that was left half-way simply ends: "play" means the main world
            entry.set("state", "done");
            entry.set("guest", false);
            write(id, entry);
            return false;
        }
        if (state.equals("graduating") || hasCharacter(id)) {
            return false;
        }
        // a first morning (or the first one after a wipe)
        entry.set("state", "in_progress");
        entry.set("step", "");
        entry.set("guest", false);
        entry.set("name", player.getName());
        entry.set("started", System.currentTimeMillis());
        entry.set("seconds", 0L);
        entry.set("flags", new ArrayList<String>());
        write(id, entry);
        return true;
    }

    /**
     * {@code /tutorial}: a newcomer simply goes (as with "play"); somebody with a character gets a visitor's
     * ticket. False when the isle is switched off here.
     */
    public boolean visit(Player player) {
        if (!enabled || player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        failed.remove(id);
        if (toIsle(player)) {
            return true;
        }
        YamlConfiguration entry = read(id);
        entry.set("state", "in_progress");
        entry.set("guest", true);
        entry.set("step", "");
        entry.set("name", player.getName());
        entry.set("started", System.currentTimeMillis());
        entry.set("seconds", 0L);
        entry.set("flags", new ArrayList<String>());
        write(id, entry);
        return true;
    }

    /**
     * The isle did not take the player (still in the lobby a few seconds after the connect). A ticket that was
     * never used is taken back, so the main world does not send them to an isle that is closed; after the second
     * miss within ten minutes "play" leads straight to the main world for a while.
     *
     * @return true when the caller should now send them to the main world instead
     */
    public boolean missed(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long[] seen = failed.compute(id, (k, old) -> old == null || now - old[1] > 600_000L
                ? new long[] {1L, now} : new long[] {old[0] + 1L, now});
        YamlConfiguration entry = read(id);
        String step = entry.getString("step", "");
        if (entry.getString("state", "new").equalsIgnoreCase("in_progress") && (step == null || step.isBlank())) {
            if (entry.getBoolean("guest", false) || seen[0] >= 2L) {
                entry.set("state", entry.getBoolean("completed-once", false) ? "done" : "new");
                entry.set("guest", false);
                write(id, entry);
            }
        }
        return seen[0] >= 2L && !entry.getBoolean("guest", false);
    }

    private boolean givenUp(UUID id) {
        long[] seen = failed.get(id);
        return seen != null && seen[0] >= 2L && System.currentTimeMillis() - seen[1] < 600_000L;
    }

    private boolean hasCharacter(UUID id) {
        return new File(ledger, id + ".yml").isFile() || new File(mainQuests, id + ".yml").isFile();
    }

    private YamlConfiguration read(UUID id) {
        YamlConfiguration yaml = new YamlConfiguration();
        File file = new File(store, id + ".yml");
        if (file.isFile()) {
            try {
                yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not read the tutorial state of " + id, ex);
            }
        }
        return yaml;
    }

    private void write(UUID id, YamlConfiguration yaml) {
        if (!store.isDirectory() && !store.mkdirs()) {
            plugin.getLogger().warning("Could not create " + store.getAbsolutePath());
            return;
        }
        File target = new File(store, id + ".yml");
        File temp = new File(store, id + ".yml.tmp");
        try {
            Files.writeString(temp.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException notAtomic) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write the tutorial state of " + id, ex);
        }
    }
}
