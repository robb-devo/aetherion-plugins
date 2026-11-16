package de.aetherion.core.world;

import de.aetherion.core.api.WorldAccess;
import de.aetherion.core.api.WorldNews;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The world wire ({@link WorldAccess}): every backend appends its news to one shared folder and reads what the
 * others wrote, so a boss that falls on the exile shore is heard in the capital a second later.
 *
 * <p>Files, all under {@code <shared>/world/}:
 * <ul>
 *   <li>{@code wire-<yyyyMMdd>.log}: one line per event ({@link WorldNews#encode()}), appended with a single
 *       write so lines of different servers never mix. A week of days is kept.</li>
 *   <li>{@code tally-<port>.txt}: what <em>this</em> server has published so far, per kind and subject: how
 *       often, and the first time. One writer per file; the network count is the sum over all of them.</li>
 * </ul>
 * Nothing here touches the disk on the server thread after start-up: appends, the once-a-second read and the
 * tally files run on one small worker, and what it finds is handed back with a scheduler task.
 */
public final class WorldWire implements WorldAccess {

    /** Extra every published news carries: the network count of this kind and subject, this one included. */
    public static final String NTH = "nth";

    private static final String WIRE_PREFIX = "wire-";
    private static final String WIRE_SUFFIX = ".log";
    private static final String TALLY_PREFIX = "tally-";
    private static final String TALLY_SUFFIX = ".txt";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int KEEP_DAYS = 7;
    private static final int RECENT_CAP = 240;
    private static final int MAX_LINE_BYTES = 3500;
    private static final long BOOT_READ_BYTES = 256L * 1024L;
    private static final long POLL_READ_BYTES = 1024L * 1024L;

    /** How often something happened and the first time it did. */
    private record Mark(int count, WorldNews first) {
    }

    private record Hook(Plugin owner, Consumer<WorldNews> listener) {
    }

    private final JavaPlugin plugin;
    private final File dir;
    private final File tallyFile;
    private final Supplier<String> serverName;
    private final String boot = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xffffffffffL);
    private final List<Hook> hooks = new CopyOnWriteArrayList<>();
    /** Server thread only. */
    private final Deque<WorldNews> recent = new ArrayDeque<>();
    private final Map<String, Mark> own = new LinkedHashMap<>();
    private long seq;
    private long lastComplaint;
    /** Swapped whole by the worker; read on the server thread. */
    private volatile Map<String, Mark> others = Map.of();
    /** Worker only. */
    private final Map<String, Long> offsets = new HashMap<>();
    private final Map<String, byte[]> partial = new HashMap<>();
    private int polls;
    private ScheduledExecutorService io;

    public WorldWire(JavaPlugin plugin, File sharedRoot, Supplier<String> serverName) {
        this.plugin = plugin;
        this.dir = new File(sharedRoot != null ? sharedRoot : plugin.getDataFolder(), "world");
        this.tallyFile = new File(dir, TALLY_PREFIX + Bukkit.getPort() + TALLY_SUFFIX);
        this.serverName = serverName;
    }

    public File folder() {
        return dir;
    }

    public void start() {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("World wire: could not create " + dir.getPath() + " (news stay on this server).");
        }
        own.putAll(readTally(tallyFile));
        others = readOthers();
        // What the last two days brought, for boards and criers that start with a history.
        for (String name : List.of(wireName(-1), wireName(0))) {
            File file = new File(dir, name);
            long length = file.length();
            long from = Math.max(0L, length - BOOT_READ_BYTES);
            List<String> lines = readLines(name, file, from, length, from > 0L);
            for (String line : lines) {
                WorldNews news = WorldNews.decode(line);
                if (news != null) {
                    remember(news);
                }
            }
            offsets.put(name, length);
        }
        io = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "aetherion-world-wire");
            thread.setDaemon(true);
            return thread;
        });
        io.execute(this::prune);
        io.scheduleWithFixedDelay(this::poll, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        hooks.clear();
        ScheduledExecutorService worker = io;
        io = null;
        if (worker == null) {
            return;
        }
        worker.shutdown();
        try {
            if (!worker.awaitTermination(2L, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ WorldAccess

    @Override
    public WorldNews publish(String kind, String subject, Player actor, Map<String, String> data) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("world news needs a kind");
        }
        if (!Bukkit.isPrimaryThread()) {
            // Tallies and listeners belong to the server thread: hand it over instead of racing them.
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> publish(kind, subject, actor, data));
            }
            return null;
        }
        String topic = subject == null ? "" : subject;
        Map<String, String> extras = new LinkedHashMap<>();
        if (data != null) {
            extras.putAll(data);
        }
        extras.put(NTH, Integer.toString(count(kind, topic) + 1));
        WorldNews news = new WorldNews(boot + "-" + (++seq), System.currentTimeMillis(), serverName.get(), kind, topic,
                actor == null ? null : actor.getUniqueId(), actor == null ? "" : actor.getName(), extras);
        String line = news.encode();
        if (line.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES) {
            // Never let one oversized extra break the single-write promise: send it without extras.
            news = new WorldNews(news.id(), news.at(), news.server(), kind, topic, news.actor(), news.actorName(),
                    Map.of(NTH, extras.get(NTH)));
            line = news.encode();
        }
        String key = key(kind, topic);
        Mark before = own.get(key);
        own.put(key, new Mark(before == null ? 1 : before.count() + 1, before == null ? news : before.first()));
        remember(news);
        String tally = tallyText();
        String wireLine = line + "\n";
        ScheduledExecutorService worker = io;
        if (worker != null) {
            worker.execute(() -> write(wireLine, tally));
        }
        deliver(List.of(news));
        return news;
    }

    @Override
    public void listen(Plugin owner, Consumer<WorldNews> listener) {
        if (owner != null && listener != null) {
            hooks.add(new Hook(owner, listener));
        }
    }

    @Override
    public void unlisten(Plugin owner) {
        hooks.removeIf(hook -> hook.owner() == owner);
    }

    @Override
    public int count(String kind, String subject) {
        String key = key(kind, subject);
        Mark mine = own.get(key);
        Mark theirs = others.get(key);
        return (mine == null ? 0 : mine.count()) + (theirs == null ? 0 : theirs.count());
    }

    @Override
    public WorldNews first(String kind, String subject) {
        String key = key(kind, subject);
        Mark mine = own.get(key);
        Mark theirs = others.get(key);
        WorldNews a = mine == null ? null : mine.first();
        WorldNews b = theirs == null ? null : theirs.first();
        if (a == null || b == null) {
            return a != null ? a : b;
        }
        return a.at() <= b.at() ? a : b;
    }

    @Override
    public List<WorldNews> recent(String kind, int max) {
        List<WorldNews> out = new ArrayList<>();
        for (WorldNews news : recent) {
            if (kind == null || kind.equals(news.kind())) {
                out.add(news);
            }
        }
        int cut = Math.max(0, out.size() - Math.max(0, max));
        return cut == 0 ? out : new ArrayList<>(out.subList(cut, out.size()));
    }

    // ------------------------------------------------------------------ server thread

    private static String key(String kind, String subject) {
        return kind + "\u0001" + (subject == null ? "" : subject);
    }

    private void remember(WorldNews news) {
        recent.addLast(news);
        while (recent.size() > RECENT_CAP) {
            recent.removeFirst();
        }
    }

    private void deliver(List<WorldNews> batch) {
        for (WorldNews news : batch) {
            for (Hook hook : hooks) {
                if (!hook.owner().isEnabled()) {
                    continue;
                }
                try {
                    hook.listener().accept(news);
                } catch (Throwable throwable) {
                    long now = System.currentTimeMillis();
                    if (now - lastComplaint > 60_000L) {
                        lastComplaint = now;
                        plugin.getLogger().log(Level.WARNING, "World wire: a listener of " + hook.owner().getName()
                                + " failed on " + news.kind(), throwable);
                    }
                }
            }
        }
    }

    private String tallyText() {
        StringBuilder out = new StringBuilder();
        for (Mark mark : own.values()) {
            if (mark.first() != null) {
                out.append(mark.count()).append('\t').append(mark.first().encode()).append('\n');
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ worker

    /** Tally first, wire second: whoever reads the line already finds the count that includes it. */
    private void write(String wireLine, String tally) {
        try {
            File tmp = new File(dir, tallyFile.getName() + ".tmp");
            Files.writeString(tmp.toPath(), tally, StandardCharsets.UTF_8);
            de.aetherion.core.persist.AtomicYaml.replaceAtomically(tmp, tallyFile);
            Files.write(new File(dir, wireName(0)).toPath(), wireLine.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("World wire: could not write (" + exception.getMessage() + ")");
        }
    }

    private void poll() {
        try {
            List<WorldNews> fresh = new ArrayList<>();
            String today = wireName(0);
            String yesterday = wireName(-1);
            offsets.keySet().removeIf(name -> !name.equals(today) && !name.equals(yesterday));
            partial.keySet().removeIf(name -> !name.equals(today) && !name.equals(yesterday));
            for (String name : List.of(yesterday, today)) {
                File file = new File(dir, name);
                long length = file.length();
                long from = offsets.getOrDefault(name, 0L);
                if (length < from) {
                    from = 0L; // replaced or truncated: start over
                    partial.remove(name);
                }
                if (length == from) {
                    continue;
                }
                long to = Math.min(length, from + POLL_READ_BYTES);
                for (String line : readLines(name, file, from, to, false)) {
                    WorldNews news = WorldNews.decode(line);
                    if (news != null && !news.id().startsWith(boot + "-")) {
                        fresh.add(news);
                    }
                }
                offsets.put(name, to);
            }
            if (!fresh.isEmpty() || ++polls % 30 == 0) {
                others = readOthers();
            }
            if (!fresh.isEmpty() && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (WorldNews news : fresh) {
                        remember(news);
                    }
                    deliver(fresh);
                });
            }
        } catch (Throwable throwable) {
            // The worker must survive anything: a dead poll would silence the whole wire.
            plugin.getLogger().log(Level.FINE, "World wire poll", throwable);
        }
    }

    /**
     * Whole lines between two byte offsets. A line cut off at {@code to} waits in {@link #partial} for the next
     * read; {@code skipFirst} drops the first (probably half) line when reading from the middle of a file.
     */
    private List<String> readLines(String name, File file, long from, long to, boolean skipFirst) {
        List<String> out = new ArrayList<>();
        if (to <= from || !file.isFile()) {
            return out;
        }
        byte[] chunk = new byte[(int) (to - from)];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(from);
            raf.readFully(chunk);
        } catch (IOException exception) {
            return out;
        }
        byte[] head = partial.remove(name);
        byte[] bytes = chunk;
        if (head != null && head.length > 0) {
            bytes = new byte[head.length + chunk.length];
            System.arraycopy(head, 0, bytes, 0, head.length);
            System.arraycopy(chunk, 0, bytes, head.length, chunk.length);
        }
        int start = 0;
        boolean first = true;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != '\n') {
                continue;
            }
            if (!(first && skipFirst)) {
                out.add(new String(bytes, start, i - start, StandardCharsets.UTF_8));
            }
            first = false;
            start = i + 1;
        }
        if (start < bytes.length) {
            byte[] rest = new byte[bytes.length - start];
            System.arraycopy(bytes, start, rest, 0, rest.length);
            partial.put(name, rest);
        }
        return out;
    }

    private Map<String, Mark> readOthers() {
        Map<String, Mark> sum = new HashMap<>();
        File[] files = dir.listFiles((folder, name) -> name.startsWith(TALLY_PREFIX) && name.endsWith(TALLY_SUFFIX));
        if (files == null) {
            return sum;
        }
        for (File file : files) {
            if (file.getName().equals(tallyFile.getName())) {
                continue;
            }
            for (Map.Entry<String, Mark> entry : readTally(file).entrySet()) {
                Mark add = entry.getValue();
                sum.merge(entry.getKey(), add, (a, b) -> new Mark(a.count() + b.count(),
                        a.first().at() <= b.first().at() ? a.first() : b.first()));
            }
        }
        return sum;
    }

    private static Map<String, Mark> readTally(File file) {
        Map<String, Mark> out = new LinkedHashMap<>();
        if (!file.isFile()) {
            return out;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            return out;
        }
        for (String line : lines) {
            int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            WorldNews first = WorldNews.decode(line.substring(tab + 1));
            if (first == null) {
                continue;
            }
            try {
                out.put(key(first.kind(), first.subject()), new Mark(Integer.parseInt(line.substring(0, tab)), first));
            } catch (NumberFormatException ignored) {
                // a damaged line loses its count, never the file
            }
        }
        return out;
    }

    private void prune() {
        File[] files = dir.listFiles((folder, name) -> name.startsWith(WIRE_PREFIX) && name.endsWith(WIRE_SUFFIX));
        if (files == null) {
            return;
        }
        String oldest = wireName(-KEEP_DAYS);
        for (File file : files) {
            if (file.getName().compareTo(oldest) < 0 && !file.delete()) {
                plugin.getLogger().fine("World wire: old file stays: " + file.getName());
            }
        }
    }

    private static String wireName(int dayOffset) {
        return WIRE_PREFIX + LocalDate.now(ZoneOffset.UTC).plusDays(dayOffset).format(DAY) + WIRE_SUFFIX;
    }
}
