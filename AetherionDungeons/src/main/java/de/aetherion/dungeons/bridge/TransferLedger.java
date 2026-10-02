package de.aetherion.dungeons.bridge;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Per-player transfer ledger in the shared transfer folder ({@code ledger/<uuid>.yml}).
 * <p>
 * It answers two questions every backend needs on join:
 * <ul>
 *     <li><b>Who holds this character right now?</b> {@code holder} is either a server name
 *     (the character lives in that backend's playerdata) or {@code snapshot:<id>} (the character
 *     is packed in a pending snapshot file).</li>
 *     <li><b>Was this snapshot already applied?</b> {@code applied} keeps the last snapshot ids,
 *     so a reconnect with the same snapshot never applies twice.</li>
 * </ul>
 * Writes are atomic (temp + rename). Cross-server writes do not overlap in practice: the leaving
 * server writes before Connect, the arriving server writes after the claim.
 */
public final class TransferLedger {

    public static final String SNAPSHOT_PREFIX = "snapshot:";
    private static final int APPLIED_KEEP = 24;

    private final Plugin plugin;
    private final File dir;

    public TransferLedger(Plugin plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[Transfer] Could not create ledger dir: " + dir.getAbsolutePath());
        }
    }

    public synchronized String holder(UUID id) {
        return read(id).getString("holder", "");
    }

    public synchronized String lastSentId(UUID id) {
        return read(id).getString("last-sent-id", "");
    }

    public synchronized String lastSentTo(UUID id) {
        return read(id).getString("last-sent-to", "");
    }

    public synchronized boolean wasApplied(UUID id, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return false;
        }
        return read(id).getStringList("applied").contains(snapshotId);
    }

    /** This backend's playerdata was already reconciled with v6 (legacy leftovers adopted once). */
    public synchronized boolean isAdopted(UUID id, String here) {
        return here != null && read(id).getStringList("adopted").contains(here.toLowerCase(Locale.ROOT));
    }

    public void markAdopted(UUID id, String here) {
        if (here == null || here.isBlank()) {
            return;
        }
        update(id, yaml -> addAdopted(yaml, here));
    }

    private static void addAdopted(YamlConfiguration yaml, String here) {
        List<String> adopted = new ArrayList<>(yaml.getStringList("adopted"));
        String key = here.toLowerCase(Locale.ROOT);
        if (!adopted.contains(key)) {
            adopted.add(key);
            yaml.set("adopted", adopted);
        }
    }

    public synchronized int missingKicks(UUID id) {
        return read(id).getInt("missing-kicks", 0);
    }

    public void markSent(UUID id, String snapshotId, String toServer, String reason, String here) {
        update(id, yaml -> {
            long now = System.currentTimeMillis();
            yaml.set("holder", SNAPSHOT_PREFIX + snapshotId);
            yaml.set("holder-at", now);
            yaml.set("last-sent-id", snapshotId);
            yaml.set("last-sent-to", toServer == null ? "" : toServer.toLowerCase(Locale.ROOT));
            yaml.set("last-sent-from", here);
            yaml.set("last-sent-reason", reason);
            yaml.set("last-sent-at", now);
        });
    }

    public void markApplied(UUID id, String snapshotId, String here) {
        update(id, yaml -> {
            long now = System.currentTimeMillis();
            yaml.set("holder", here);
            yaml.set("holder-at", now);
            yaml.set("last-applied-id", snapshotId);
            yaml.set("last-applied-on", here);
            yaml.set("last-applied-at", now);
            yaml.set("missing-kicks", 0);
            addAdopted(yaml, here);
            List<String> applied = new ArrayList<>(yaml.getStringList("applied"));
            if (snapshotId != null && !snapshotId.isBlank() && !applied.contains(snapshotId)) {
                applied.add(snapshotId);
            }
            while (applied.size() > APPLIED_KEEP) {
                applied.remove(0);
            }
            yaml.set("applied", applied);
        });
    }

    /** The character stays in (or went back to) this backend's own playerdata. */
    public void markHolder(UUID id, String here) {
        update(id, yaml -> {
            yaml.set("holder", here);
            yaml.set("holder-at", System.currentTimeMillis());
            addAdopted(yaml, here);
        });
    }

    public void noteMissingKick(UUID id) {
        update(id, yaml -> yaml.set("missing-kicks", yaml.getInt("missing-kicks", 0) + 1));
    }

    private synchronized void update(UUID id, Consumer<YamlConfiguration> change) {
        if (id == null || change == null || dir == null) {
            return;
        }
        File file = fileFor(id);
        YamlConfiguration yaml = read(id);
        yaml.set("uuid", id.toString());
        change.accept(yaml);
        try {
            de.aetherion.core.persist.AtomicYaml.save(yaml, file, plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[Transfer] Ledger write failed for " + id, ex);
        }
    }

    private YamlConfiguration read(UUID id) {
        if (id == null || dir == null) {
            return new YamlConfiguration();
        }
        File file = fileFor(id);
        de.aetherion.core.persist.AtomicYaml.recoverTemp(file, plugin.getLogger());
        if (!file.isFile()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private File fileFor(UUID id) {
        return new File(dir, id + ".yml");
    }
}
