package de.aetherion.bossengine.telemetry;

import de.aetherion.bossengine.event.BossDeathEvent;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.instance.DamageTracker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Progression telemetry (design §3.4 "Telemetry first", prerequisite for the L5 boss-HP re-band).
 * <p>
 * Read-only: listens to {@link BossDeathEvent} at MONITOR and appends one row per paid boss death to
 * {@code plugins/BossEngine/fight-log.csv}. Never touches loot, the instance or the fight.
 * Sandbox / Test Arena kills and non-finale script acts do not fire the event, so they are not logged.
 * <p>
 * Columns:
 * <ul>
 *   <li>{@code fight_seconds} — first player hit → death (not spawn → death).</li>
 *   <li>{@code party_size} — players who dealt any damage; {@code party_5pct} — players with ≥5% share.</li>
 *   <li>{@code top_share} — top damager's share of total damage (0..1); {@code top_dps} / {@code party_dps}.</li>
 *   <li>{@code base_hp} — the template's YAML max-health; {@code boss_max_hp} is the live HP after the
 *       open-world raid scale ({@code raid_players} nearby at spawn, +35% HP each extra, see BossManager).</li>
 * </ul>
 * A file with an older header is moved aside to {@code fight-log.<n>.csv} before the first new row.
 */
public final class BossFightLog implements Listener {

    static final String FILE_NAME = "fight-log.csv";
    static final String HEADER = "logged_at,boss_id,boss_max_hp,fight_seconds,party_size,party_5pct,"
            + "top_player,top_share,top_dps,party_dps,total_damage,killer,world,base_hp,raid_players";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JavaPlugin plugin;
    private final File file;
    private final Object lock = new Object();

    public BossFightLog(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossDeath(BossDeathEvent event) {
        try {
            String row = buildRow(event);
            if (row == null) {
                return;
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(row));
            } else {
                append(row);
            }
        } catch (RuntimeException exception) {
            // Telemetry must never break a boss payout.
            plugin.getLogger().log(Level.WARNING, "[FightLog] Could not log boss death.", exception);
        }
    }

    private String buildRow(BossDeathEvent event) {
        BossInstance instance = event.getInstance();
        if (instance == null || instance.getTemplate() == null) {
            return null;
        }
        DamageTracker tracker = instance.getDamageTracker();
        Map<UUID, Double> damage = event.getDamageMap();
        Map<UUID, Double> shares = event.getShareMap();

        long now = System.currentTimeMillis();
        long firstHit = tracker == null ? 0L : tracker.firstHitMillis();
        double seconds = firstHit > 0L ? Math.max(0.0, (now - firstHit) / 1000.0) : 0.0;

        double total = 0.0;
        for (double value : damage.values()) {
            total += value;
        }
        int partySize = 0;
        int party5 = 0;
        UUID top = null;
        double topDamage = 0.0;
        for (Map.Entry<UUID, Double> entry : damage.entrySet()) {
            double dealt = entry.getValue() == null ? 0.0 : entry.getValue();
            if (dealt <= 0.0) {
                continue;
            }
            partySize++;
            Double share = shares.get(entry.getKey());
            if (share != null && share >= 0.05) {
                party5++;
            }
            if (top == null || dealt > topDamage) {
                top = entry.getKey();
                topDamage = dealt;
            }
        }
        double topShare = total > 0.0 ? topDamage / total : 0.0;
        double topDps = seconds > 0.0 ? topDamage / seconds : 0.0;
        double partyDps = seconds > 0.0 ? total / seconds : 0.0;

        Player killer = event.getKiller();
        Location spawn = instance.getSpawnLocation();
        String world = spawn != null && spawn.getWorld() != null ? spawn.getWorld().getName() : "";

        return String.join(",",
                csv(OffsetDateTime.now().format(TIME)),
                csv(instance.getTemplate().getId()),
                num(instance.getCombatMaxHealth()),
                num(seconds),
                Integer.toString(partySize),
                Integer.toString(party5),
                csv(name(top)),
                String.format(Locale.ROOT, "%.4f", topShare),
                num(topDps),
                num(partyDps),
                num(total),
                csv(killer == null ? "" : killer.getName()),
                csv(world),
                num(instance.getTemplate().getAttributes() == null ? 0.0
                        : instance.getTemplate().getAttributes().getMaxHealth()),
                Integer.toString(instance.getRaidPlayers())
        );
    }

    private void append(String row) {
        synchronized (lock) {
            try {
                File folder = file.getParentFile();
                if (folder != null && !folder.exists()) {
                    folder.mkdirs();
                }
                rotateIfOldHeader();
                boolean fresh = !file.exists() || file.length() == 0L;
                String text = (fresh ? HEADER + System.lineSeparator() : "") + row + System.lineSeparator();
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[FightLog] Could not write " + FILE_NAME, exception);
            }
        }
    }

    /** P5: keep columns aligned — an older header goes to fight-log.<n>.csv, the new file starts clean. */
    private void rotateIfOldHeader() throws IOException {
        if (!file.exists() || file.length() == 0L) {
            return;
        }
        String first;
        try (java.io.BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            first = reader.readLine();
        }
        if (HEADER.equals(first)) {
            return;
        }
        int n = 1;
        File moved;
        do {
            moved = new File(file.getParentFile(), "fight-log." + n + ".csv");
            n++;
        } while (moved.exists());
        Files.move(file.toPath(), moved.toPath());
    }

    private static String name(UUID id) {
        if (id == null) {
            return "";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        String name = player.getName();
        return name == null ? id.toString() : name;
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String csv(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.indexOf(',') < 0 && raw.indexOf('"') < 0 && raw.indexOf('\n') < 0) {
            return raw;
        }
        return '"' + raw.replace("\"", "\"\"") + '"';
    }
}
