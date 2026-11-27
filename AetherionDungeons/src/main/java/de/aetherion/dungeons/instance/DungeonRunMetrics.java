package de.aetherion.dungeons.instance;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Measures every dungeon run (beta list A-06), so lag questions get numbers instead of guesses.
 *
 * <p>Per run: how long the floor took to build, every timed step (start, room opens, chests, boss spawn,
 * close), the server's tick times while the run was open, and the most mobs, entities and loaded chunks its
 * world held. One summary line per run goes to the log and to {@code run-metrics.csv}.
 *
 * <p>Any tick slower than {@code run-metrics.slow-tick-ms} is logged with what ran in it (joins, world loads,
 * our own steps), also when no run is open: the old freezes on this server happened outside the floors.
 *
 * <p>Everything runs on the server thread. With no run open the tick hook compares one number.
 */
public final class DungeonRunMetrics implements Listener {

    private static volatile DungeonRunMetrics active;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_HEADER = "ended,floor,world,party,result,seconds,build_ms,build_kind,"
            + "tick_avg_ms,tick_max_ms,ticks_over_50ms,ticks_over_100ms,mobs_max,entities_max,chunks_max,"
            + "tiles_max,slowest_step,slowest_step_ms,worst_tick_ms,worst_tick_during,steps";
    private static final int SAMPLE_TICKS = 100;
    private static final int MAX_MARKS = 8;
    private static final int RECENT = 12;

    private final JavaPlugin plugin;
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final Deque<String> recent = new ArrayDeque<>();
    private final List<String> marks = new ArrayList<>();
    private final boolean enabled;
    private final double slowTickMs;
    private final long logEveryMs;
    private final long armedAt;
    private int sampleCountdown = SAMPLE_TICKS;
    private long lastSlowLog;
    private int slowSuppressed;

    public DungeonRunMetrics(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("run-metrics.enabled", true);
        this.slowTickMs = Math.max(55.0, plugin.getConfig().getDouble("run-metrics.slow-tick-ms", 100.0));
        this.logEveryMs = Math.max(0L, plugin.getConfig().getLong("run-metrics.log-every-seconds", 60L)) * 1000L;
        // World loading at boot is slow by nature; slow-tick lines start one minute after enable.
        this.armedAt = System.currentTimeMillis() + 60_000L;
        active = this;
    }

    public static DungeonRunMetrics get() {
        return active;
    }

    // ------------------------------------------------------------------ hooks for the dungeon code

    /** Notes what ran in this tick, so a slow tick can say what it was. Server thread only; cheap. */
    public static void mark(String what) {
        DungeonRunMetrics metrics = active;
        if (metrics == null || !metrics.enabled || what == null || !Bukkit.isPrimaryThread()) {
            return;
        }
        if (metrics.marks.size() < MAX_MARKS && !metrics.marks.contains(what)) {
            metrics.marks.add(what);
        }
    }

    /** Runs {@code work}; its time is booked as {@code step} on the run in {@code world}, if one is open. */
    public static void time(World world, String step, Runnable work) {
        long started = System.nanoTime();
        try {
            work.run();
        } finally {
            record(world, step, (System.nanoTime() - started) / 1_000_000.0);
        }
    }

    /** {@link #time} for work that returns something. */
    public static <T> T timed(World world, String step, Supplier<T> work) {
        long started = System.nanoTime();
        try {
            return work.get();
        } finally {
            record(world, step, (System.nanoTime() - started) / 1_000_000.0);
        }
    }

    public static void record(World world, String step, double ms) {
        DungeonRunMetrics metrics = active;
        if (metrics == null || !metrics.enabled) {
            return;
        }
        mark(step);
        if (world == null) {
            return;
        }
        Run run = metrics.runs.get(world.getName());
        if (run != null) {
            run.step(step, ms);
        }
    }

    /** The floor is built and the party is about to go in. */
    public void begin(DungeonSession session, double buildMs, String buildKind, int party) {
        if (!enabled || session == null) {
            return;
        }
        Run run = new Run(session.worldName(), session.floorNumber(), party, buildMs, buildKind);
        runs.put(run.world, run);
        mark("build F" + run.floor);
        plugin.getLogger().info(String.format(Locale.ROOT, "[DungeonRun] F%d start %s · party %d · build %.0f ms (%s)",
                run.floor, run.world, party, buildMs, buildKind));
    }

    /** The run's world closed. {@code closeMs} is how long closing took on the server thread. */
    public void end(DungeonSession session, String result, double closeMs) {
        if (!enabled || session == null) {
            return;
        }
        Run run = runs.remove(session.worldName());
        if (run == null) {
            return;
        }
        run.step("close", closeMs);
        String line = run.summary(result);
        plugin.getLogger().info("[DungeonRun] " + line);
        recent.addFirst(LocalDateTime.now().format(STAMP) + " " + line);
        while (recent.size() > RECENT) {
            recent.removeLast();
        }
        appendCsv(run.csv(result));
    }

    /** Newest first: finished runs, then one line per open run. */
    public List<String> report() {
        List<String> out = new ArrayList<>();
        for (Run run : runs.values()) {
            out.add("open: " + run.progress());
        }
        out.addAll(recent);
        return out;
    }

    // ------------------------------------------------------------------ server events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        if (!enabled) {
            return;
        }
        double ms = event.getTickDuration();
        if (!runs.isEmpty()) {
            for (Run run : runs.values()) {
                run.tick(ms);
            }
            if (--sampleCountdown <= 0) {
                sampleCountdown = SAMPLE_TICKS;
                sampleAll();
            }
        }
        if (ms >= slowTickMs) {
            slowTick(ms);
        }
        if (!marks.isEmpty()) {
            marks.clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        mark("join " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        mark("quit " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        mark("world load " + event.getWorld().getName());
    }

    // ------------------------------------------------------------------ internals

    private void slowTick(double ms) {
        String during = marks.isEmpty() ? "nothing of ours" : String.join(", ", marks);
        for (Run run : runs.values()) {
            run.noteSlowTick(ms, during);
        }
        long now = System.currentTimeMillis();
        if (now < armedAt) {
            return;
        }
        if (now - lastSlowLog < 1000L) {
            slowSuppressed++;
            return;
        }
        lastSlowLog = now;
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "[SlowTick] slow tick %.0f ms · %d online",
                ms, Bukkit.getOnlinePlayers().size()));
        if (!runs.isEmpty()) {
            line.append(" · open runs");
            for (Run run : runs.values()) {
                line.append(" F").append(run.floor);
            }
        }
        line.append(" · during: ").append(during);
        if (slowSuppressed > 0) {
            line.append(" · ").append(slowSuppressed).append(" more slow tick(s) in the second before");
            slowSuppressed = 0;
        }
        plugin.getLogger().warning(line.toString());
    }

    private void sampleAll() {
        long now = System.currentTimeMillis();
        for (Run run : runs.values()) {
            World world = Bukkit.getWorld(run.world);
            if (world == null) {
                continue;
            }
            int mobs = 0;
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Player)) {
                    mobs++;
                }
            }
            run.sample(mobs, world.getEntityCount(), world.getChunkCount(), world.getTileEntityCount(),
                    world.getPlayerCount());
            if (logEveryMs > 0L && now - run.lastLog >= logEveryMs) {
                run.lastLog = now;
                plugin.getLogger().info("[DungeonRun] " + run.progress());
                run.resetWindow();
            }
        }
    }

    private void appendCsv(String row) {
        File file = new File(plugin.getDataFolder(), "run-metrics.csv");
        Runnable write = () -> {
            try {
                StringBuilder out = new StringBuilder();
                if (!file.isFile()) {
                    out.append(CSV_HEADER).append('\n');
                }
                out.append(row).append('\n');
                Files.writeString(file.toPath(), out, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not write run-metrics.csv: " + exception.getMessage());
            }
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run(); // shutting down: no scheduler, one short line
        }
    }

    private static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return (seconds / 60L) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60L);
    }

    private static String csvText(String text) {
        String clean = text == null ? "" : text.replace('"', '\'');
        return "\"" + clean + "\"";
    }

    private static final class Run {
        final String world;
        final int floor;
        final int party;
        final double buildMs;
        final String buildKind;
        final long startedAt = System.currentTimeMillis();
        long lastLog = startedAt;

        long ticks;
        double tickSum;
        double tickMax;
        int over50;
        int over100;

        long windowTicks;
        double windowSum;
        double windowMax;
        int windowOver50;

        int mobs;
        int entities;
        int chunks;
        int players;
        int mobsMax;
        int entitiesMax;
        int chunksMax;
        int tilesMax;

        double worstTick;
        String worstTickDuring = "";
        /** step → {count, total ms, max ms}, in first-seen order. */
        final Map<String, double[]> steps = new LinkedHashMap<>();

        Run(String world, int floor, int party, double buildMs, String buildKind) {
            this.world = world;
            this.floor = floor;
            this.party = party;
            this.buildMs = buildMs;
            this.buildKind = buildKind == null ? "" : buildKind;
        }

        void tick(double ms) {
            ticks++;
            tickSum += ms;
            if (ms > tickMax) {
                tickMax = ms;
            }
            if (ms > 50.0) {
                over50++;
                windowOver50++;
            }
            if (ms > 100.0) {
                over100++;
            }
            windowTicks++;
            windowSum += ms;
            if (ms > windowMax) {
                windowMax = ms;
            }
        }

        void noteSlowTick(double ms, String during) {
            if (ms > worstTick) {
                worstTick = ms;
                worstTickDuring = during;
            }
        }

        void step(String name, double ms) {
            double[] stat = steps.computeIfAbsent(name, key -> new double[3]);
            stat[0]++;
            stat[1] += ms;
            if (ms > stat[2]) {
                stat[2] = ms;
            }
        }

        void sample(int mobs, int entities, int chunks, int tiles, int players) {
            this.mobs = mobs;
            this.entities = entities;
            this.chunks = chunks;
            this.players = players;
            mobsMax = Math.max(mobsMax, mobs);
            entitiesMax = Math.max(entitiesMax, entities);
            chunksMax = Math.max(chunksMax, chunks);
            tilesMax = Math.max(tilesMax, tiles);
        }

        void resetWindow() {
            windowTicks = 0L;
            windowSum = 0.0;
            windowMax = 0.0;
            windowOver50 = 0;
        }

        String slowestStep() {
            String name = "build";
            double max = buildMs;
            for (Map.Entry<String, double[]> entry : steps.entrySet()) {
                if (entry.getValue()[2] > max) {
                    max = entry.getValue()[2];
                    name = entry.getKey();
                }
            }
            return name;
        }

        double slowestStepMs() {
            double max = buildMs;
            for (double[] stat : steps.values()) {
                max = Math.max(max, stat[2]);
            }
            return max;
        }

        String progress() {
            double avg = windowTicks == 0L ? 0.0 : windowSum / windowTicks;
            return String.format(Locale.ROOT,
                    "F%d %s · %s in · %d player(s) · ticks avg %.1f max %.0f ms, %d over 50 ms · mobs %d · entities %d · chunks %d",
                    floor, world, clock(System.currentTimeMillis() - startedAt), players, avg, windowMax, windowOver50,
                    mobs, entities, chunks);
        }

        String summary(String result) {
            double avg = ticks == 0L ? 0.0 : tickSum / ticks;
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                    "F%d %s after %s · party %d · build %.0f ms (%s) · ticks avg %.1f max %.0f ms, %d over 50 ms, %d over 100 ms"
                            + " · most mobs %d, entities %d, chunks %d · slowest step: %s %.0f ms",
                    floor, result, clock(System.currentTimeMillis() - startedAt), party, buildMs, buildKind, avg, tickMax,
                    over50, over100, mobsMax, entitiesMax, chunksMax, slowestStep(), slowestStepMs()));
            if (worstTick > 0.0) {
                line.append(String.format(Locale.ROOT, " · worst tick %.0f ms during %s", worstTick, worstTickDuring));
            }
            return line.toString();
        }

        String csv(String result) {
            double avg = ticks == 0L ? 0.0 : tickSum / ticks;
            StringBuilder stepText = new StringBuilder();
            for (Map.Entry<String, double[]> entry : steps.entrySet()) {
                double[] stat = entry.getValue();
                if (stepText.length() > 0) {
                    stepText.append("; ");
                }
                stepText.append(String.format(Locale.ROOT, "%s x%d total %.0f max %.0f",
                        entry.getKey(), (int) stat[0], stat[1], stat[2]));
            }
            return String.join(",",
                    LocalDateTime.now().format(STAMP),
                    String.valueOf(floor),
                    world,
                    String.valueOf(party),
                    csvText(result),
                    String.valueOf((System.currentTimeMillis() - startedAt) / 1000L),
                    String.format(Locale.ROOT, "%.0f", buildMs),
                    csvText(buildKind),
                    String.format(Locale.ROOT, "%.2f", avg),
                    String.format(Locale.ROOT, "%.0f", tickMax),
                    String.valueOf(over50),
                    String.valueOf(over100),
                    String.valueOf(mobsMax),
                    String.valueOf(entitiesMax),
                    String.valueOf(chunksMax),
                    String.valueOf(tilesMax),
                    csvText(slowestStep()),
                    String.format(Locale.ROOT, "%.0f", slowestStepMs()),
                    String.format(Locale.ROOT, "%.0f", worstTick),
                    csvText(worstTickDuring),
                    csvText(stepText.toString()));
        }
    }
}
