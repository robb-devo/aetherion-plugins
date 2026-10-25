package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The machine itself, read straight from /proc and /sys every two seconds: CPU per core, memory, disks, network,
 * temperatures, RAID, and every process (so the servers can be shown next to everything else that eats CPU).
 */
public final class HostMonitor {
    private static final Path PROC = Path.of("/proc");
    private static final int TCK = 100;
    private static final long PAGE = 4096;

    public record Proc(long pid, String name, String user, double cpu, long rss, int threads, long startTicks) {}

    private final Helm h;
    private long[] prevCpu;
    private long[][] prevCores;
    private long prevAt;
    private Map<String, long[]> prevNet = new HashMap<>();
    private Map<String, long[]> prevDisk = new HashMap<>();
    private Map<Long, Long> prevProcTicks = new HashMap<>();
    private volatile Map<Long, Proc> procs = Map.of();
    private volatile ObjectNode summary = Json.obj();
    private volatile ObjectNode detail = Json.obj();
    private volatile ObjectNode statics = Json.obj();
    private long staticsAt;
    private final Map<Integer, String> users = new HashMap<>();
    private final boolean linux = Files.isDirectory(PROC.resolve("self"));

    public HostMonitor(Helm h) {
        this.h = h;
    }

    public boolean available() {
        return linux;
    }

    public ObjectNode summary() {
        return summary;
    }

    public ObjectNode detail() {
        ObjectNode o = detail.deepCopy();
        o.set("static", statics);
        return o;
    }

    public Proc proc(long pid) {
        return procs.get(pid);
    }

    public List<Proc> topProcesses(int n) {
        List<Proc> l = new ArrayList<>(procs.values());
        l.sort((a, b) -> Double.compare(b.cpu() * 4 + b.rss() / 4e9, a.cpu() * 4 + a.rss() / 4e9));
        return l.subList(0, Math.min(n, l.size()));
    }

    // ---------------------------------------------------------------- sampling

    public synchronized void sample() {
        if (!linux) return;
        long now = System.currentTimeMillis();
        double dt = prevAt == 0 ? 0 : (now - prevAt) / 1000.0;
        ObjectNode s = Json.obj();
        ObjectNode d = Json.obj();
        try {
            if (now - staticsAt > 3600_000) { statics = readStatics(); staticsAt = now; }
            cpu(s, d);
            mem(s);
            load(s);
            net(s, d, dt);
            disks(s, d, dt);
            filesystems(s, d);
            temps(d);
            processes(dt);
            s.put("uptime", (long) (Double.parseDouble(read("/proc/uptime").split(" ")[0]) * 1000));
            s.put("cores", statics.path("threads").asInt());
            s.put("procs", procs.size());
        } catch (Exception e) {
            s.put("error", e.toString());
        }
        prevAt = now;
        summary = s;
        d.setAll(s);
        detail = d;
    }

    private void cpu(ObjectNode s, ObjectNode d) throws IOException {
        List<String> lines = Files.readAllLines(PROC.resolve("stat"));
        List<long[]> cores = new ArrayList<>();
        long[] total = null;
        for (String l : lines) {
            if (!l.startsWith("cpu")) continue;
            String[] p = l.trim().split("\\s+");
            long[] v = new long[8];
            for (int i = 0; i < 8 && i + 1 < p.length; i++) v[i] = Long.parseLong(p[i + 1]);
            if (p[0].equals("cpu")) total = v; else cores.add(v);
        }
        if (total == null) return;
        if (prevCpu != null) {
            double[] r = usage(prevCpu, total);
            s.put("cpu", r[0]);
            s.put("iowait", r[1]);
            s.put("steal", r[2]);
            ArrayNode per = d.putArray("perCore");
            for (int i = 0; i < cores.size() && prevCores != null && i < prevCores.length; i++) per.add(round(usage(prevCores[i], cores.get(i))[0]));
        }
        prevCpu = total;
        prevCores = cores.toArray(new long[0][]);
    }

    /** {busy, iowait, steal} as fractions of the interval. Fields: user nice system idle iowait irq softirq steal. */
    private static double[] usage(long[] a, long[] b) {
        long ta = 0, tb = 0;
        for (int i = 0; i < 8; i++) { ta += a[i]; tb += b[i]; }
        double dt = Math.max(1, tb - ta);
        double idle = (b[3] - a[3]) + (b[4] - a[4]);
        return new double[]{round(1 - idle / dt), round((b[4] - a[4]) / dt), round((b[7] - a[7]) / dt)};
    }

    private void mem(ObjectNode s) throws IOException {
        Map<String, Long> m = new HashMap<>();
        for (String l : Files.readAllLines(PROC.resolve("meminfo"))) {
            String[] p = l.split(":\\s+");
            if (p.length == 2) m.put(p[0], Long.parseLong(p[1].replace(" kB", "").trim()) * 1024);
        }
        long total = m.getOrDefault("MemTotal", 0L);
        long avail = m.getOrDefault("MemAvailable", 0L);
        s.put("memTotal", total);
        s.put("memUsed", total - avail);
        s.put("memCached", m.getOrDefault("Cached", 0L) + m.getOrDefault("Buffers", 0L));
        s.put("swapTotal", m.getOrDefault("SwapTotal", 0L));
        s.put("swapUsed", m.getOrDefault("SwapTotal", 0L) - m.getOrDefault("SwapFree", 0L));
    }

    private void load(ObjectNode s) throws IOException {
        String[] p = read("/proc/loadavg").split(" ");
        ArrayNode a = s.putArray("load");
        for (int i = 0; i < 3; i++) a.add(Double.parseDouble(p[i]));
    }

    private void net(ObjectNode s, ObjectNode d, double dt) throws IOException {
        long rxAll = 0, txAll = 0;
        double rxRate = 0, txRate = 0;
        Map<String, long[]> cur = new HashMap<>();
        ArrayNode ifs = d.putArray("interfaces");
        List<String> lines = Files.readAllLines(PROC.resolve("net/dev"));
        for (String l : lines.subList(Math.min(2, lines.size()), lines.size())) {
            String[] kv = l.split(":", 2);
            if (kv.length < 2) continue;
            String name = kv[0].trim();
            if (name.equals("lo") || name.startsWith("veth") || name.startsWith("docker") || name.startsWith("br-")) continue;
            String[] p = kv[1].trim().split("\\s+");
            long rx = Long.parseLong(p[0]), tx = Long.parseLong(p[8]);
            cur.put(name, new long[]{rx, tx});
            rxAll += rx;
            txAll += tx;
            long[] prev = prevNet.get(name);
            ObjectNode o = ifs.addObject();
            o.put("name", name);
            o.put("rxTotal", rx);
            o.put("txTotal", tx);
            if (prev != null && dt > 0) {
                double r = (rx - prev[0]) / dt, t = (tx - prev[1]) / dt;
                o.put("rx", Math.max(0, r));
                o.put("tx", Math.max(0, t));
                rxRate += Math.max(0, r);
                txRate += Math.max(0, t);
            }
        }
        prevNet = cur;
        s.put("rx", Math.round(rxRate));
        s.put("tx", Math.round(txRate));
        s.put("rxTotal", rxAll);
        s.put("txTotal", txAll);
    }

    private void disks(ObjectNode s, ObjectNode d, double dt) throws IOException {
        Map<String, long[]> cur = new HashMap<>();
        double rAll = 0, wAll = 0, busyMax = 0;
        ArrayNode arr = d.putArray("disks");
        for (String l : Files.readAllLines(PROC.resolve("diskstats"))) {
            String[] p = l.trim().split("\\s+");
            if (p.length < 14) continue;
            String name = p[2];
            if (!name.matches("sd[a-z]+|nvme\\d+n\\d+|vd[a-z]+|xvd[a-z]+|md\\d+")) continue;
            long rs = Long.parseLong(p[5]), ws = Long.parseLong(p[9]), busy = Long.parseLong(p[12]);
            cur.put(name, new long[]{rs, ws, busy});
            ObjectNode o = arr.addObject();
            o.put("name", name);
            long[] prev = prevDisk.get(name);
            if (prev != null && dt > 0) {
                double r = (rs - prev[0]) * 512 / dt, w = (ws - prev[1]) * 512 / dt;
                double b = Math.min(1, (busy - prev[2]) / (dt * 1000));
                o.put("read", Math.round(r));
                o.put("write", Math.round(w));
                o.put("busy", round(b));
                if (!name.startsWith("md")) { rAll += r; wAll += w; }
                busyMax = Math.max(busyMax, b);
            }
        }
        prevDisk = cur;
        s.put("diskRead", Math.round(rAll));
        s.put("diskWrite", Math.round(wAll));
        s.put("diskBusy", round(busyMax));
    }

    private void filesystems(ObjectNode s, ObjectNode d) throws IOException {
        ArrayNode arr = d.putArray("filesystems");
        Set<String> devs = new HashSet<>();
        long rootTotal = 0, rootUsed = 0;
        for (String l : Files.readAllLines(PROC.resolve("mounts"))) {
            String[] p = l.split(" ");
            if (p.length < 3 || !p[2].matches("ext[234]|xfs|btrfs|zfs|f2fs") || !devs.add(p[0])) continue;
            String mount = p[1].replace("\\040", " ");
            try {
                FileStore fs = Files.getFileStore(Path.of(mount));
                long total = fs.getTotalSpace(), free = fs.getUsableSpace();
                ObjectNode o = arr.addObject();
                o.put("mount", mount);
                o.put("device", p[0]);
                o.put("type", p[2]);
                o.put("total", total);
                o.put("used", total - fs.getUnallocatedSpace());
                o.put("free", free);
                if (mount.equals("/") || rootTotal == 0) { rootTotal = total; rootUsed = total - fs.getUnallocatedSpace(); }
            } catch (Exception ignored) {
                // unreadable mount
            }
        }
        s.put("diskTotal", rootTotal);
        s.put("diskUsed", rootUsed);
    }

    private void temps(ObjectNode d) {
        ArrayNode arr = d.putArray("temps");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of("/sys/class/hwmon"))) {
            for (Path hw : ds) {
                String chip = readOr(hw.resolve("name"), "?");
                try (DirectoryStream<Path> ts = Files.newDirectoryStream(hw, "temp*_input")) {
                    for (Path t : ts) {
                        String base = t.getFileName().toString().replace("_input", "");
                        double c = Long.parseLong(readOr(t, "0")) / 1000.0;
                        if (c <= 0 || c > 150) continue;
                        ObjectNode o = arr.addObject();
                        o.put("chip", chip);
                        o.put("label", readOr(hw.resolve(base + "_label"), base));
                        o.put("c", c);
                        String crit = readOr(hw.resolve(base + "_crit"), null);
                        if (crit != null) o.put("crit", Long.parseLong(crit) / 1000.0);
                    }
                }
            }
        } catch (Exception ignored) {
            // virtual machines usually have none
        }
    }

    private void processes(double dt) {
        Map<Long, Proc> out = new HashMap<>();
        Map<Long, Long> ticks = new HashMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(PROC, p -> p.getFileName().toString().matches("\\d+"))) {
            for (Path p : ds) {
                try {
                    String stat = Files.readString(p.resolve("stat"));
                    int open = stat.indexOf('('), close = stat.lastIndexOf(')');
                    String name = stat.substring(open + 1, close);
                    String[] f = stat.substring(close + 2).split(" ");
                    long pid = Long.parseLong(p.getFileName().toString());
                    long t = Long.parseLong(f[11]) + Long.parseLong(f[12]);
                    ticks.put(pid, t);
                    Long prev = prevProcTicks.get(pid);
                    double cpu = prev == null || dt <= 0 ? 0 : (t - prev) / (dt * TCK);
                    long rss = Long.parseLong(f[21]) * PAGE;
                    out.put(pid, new Proc(pid, name, null, round(cpu), rss, Integer.parseInt(f[17]), Long.parseLong(f[19])));
                } catch (Exception ignored) {
                    // process exited mid-read
                }
            }
        } catch (IOException ignored) {
            return;
        }
        // owner names only for the processes anyone will look at
        List<Proc> top = new ArrayList<>(out.values());
        top.sort((a, b) -> Double.compare(b.cpu() + b.rss() / 8e9, a.cpu() + a.rss() / 8e9));
        for (Proc pr : top.subList(0, Math.min(40, top.size()))) out.put(pr.pid(), withUser(pr));
        prevProcTicks = ticks;
        procs = out;
    }

    private Proc withUser(Proc p) {
        try {
            for (String l : Files.readAllLines(PROC.resolve(p.pid() + "/status"))) {
                if (l.startsWith("Uid:")) {
                    int uid = Integer.parseInt(l.split("\\s+")[1]);
                    return new Proc(p.pid(), p.name(), userName(uid), p.cpu(), p.rss(), p.threads(), p.startTicks());
                }
            }
        } catch (Exception ignored) {
            // gone
        }
        return p;
    }

    private String userName(int uid) {
        if (users.isEmpty()) {
            try {
                for (String l : Files.readAllLines(Path.of("/etc/passwd"))) {
                    String[] p = l.split(":");
                    if (p.length > 2) users.put(Integer.parseInt(p[2]), p[0]);
                }
            } catch (Exception ignored) {
                // no passwd
            }
        }
        return users.getOrDefault(uid, String.valueOf(uid));
    }

    // ---------------------------------------------------------------- static facts

    private ObjectNode readStatics() {
        ObjectNode o = Json.obj();
        try {
            Set<String> phys = new HashSet<>();
            String model = null;
            int threads = 0;
            double mhz = 0;
            String physId = "0";
            for (String l : Files.readAllLines(PROC.resolve("cpuinfo"))) {
                if (l.startsWith("model name") && model == null) model = l.split(":", 2)[1].trim();
                if (l.startsWith("processor")) threads++;
                if (l.startsWith("physical id")) physId = l.split(":", 2)[1].trim();
                if (l.startsWith("core id")) phys.add(physId + ":" + l.split(":", 2)[1].trim());
                if (l.startsWith("cpu MHz")) mhz = Math.max(mhz, Double.parseDouble(l.split(":", 2)[1].trim()));
            }
            o.put("cpuModel", model);
            o.put("threads", threads);
            o.put("cores", phys.isEmpty() ? threads : phys.size());
            if (mhz > 0) o.put("mhz", Math.round(mhz));
        } catch (Exception ignored) {
            // keep partial facts
        }
        o.put("hostname", readOr(Path.of("/proc/sys/kernel/hostname"), "?"));
        o.put("kernel", readOr(Path.of("/proc/sys/kernel/osrelease"), "?"));
        try {
            for (String l : Files.readAllLines(Path.of("/etc/os-release"))) {
                if (l.startsWith("PRETTY_NAME=")) o.put("os", l.substring(12).replace("\"", ""));
            }
        } catch (Exception ignored) {
            // unknown distro
        }
        o.put("vendor", readOr(Path.of("/sys/class/dmi/id/sys_vendor"), null));
        o.put("product", readOr(Path.of("/sys/class/dmi/id/product_name"), null));
        o.put("board", readOr(Path.of("/sys/class/dmi/id/board_name"), null));
        o.put("java", System.getProperty("java.version"));
        ArrayNode disks = o.putArray("disks");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of("/sys/block"))) {
            for (Path b : ds) {
                String n = b.getFileName().toString();
                if (!n.matches("sd[a-z]+|nvme\\d+n\\d+|vd[a-z]+|xvd[a-z]+")) continue;
                ObjectNode dk = disks.addObject();
                dk.put("name", n);
                dk.put("model", readOr(b.resolve("device/model"), null));
                dk.put("size", Long.parseLong(readOr(b.resolve("size"), "0")) * 512);
                dk.put("ssd", "0".equals(readOr(b.resolve("queue/rotational"), "1")));
            }
        } catch (Exception ignored) {
            // no sysfs
        }
        ArrayNode nics = o.putArray("nics");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of("/sys/class/net"))) {
            for (Path n : ds) {
                String name = n.getFileName().toString();
                if (name.equals("lo") || name.startsWith("veth") || name.startsWith("docker") || name.startsWith("br-")) continue;
                ObjectNode ni = nics.addObject();
                ni.put("name", name);
                String sp = readOr(n.resolve("speed"), null);
                if (sp != null && !sp.startsWith("-")) ni.put("speed", Long.parseLong(sp));
                ni.put("state", readOr(n.resolve("operstate"), "?"));
            }
        } catch (Exception ignored) {
            // no sysfs
        }
        ArrayNode raid = o.putArray("raid");
        try {
            List<String> md = Files.readAllLines(PROC.resolve("mdstat"));
            for (int i = 0; i < md.size(); i++) {
                String l = md.get(i);
                if (!l.matches("md\\d+ : .*")) continue;
                String[] p = l.split("\\s+");
                ObjectNode r = raid.addObject();
                r.put("name", p[0]);
                r.put("state", p[2]);
                r.put("level", p.length > 3 ? p[3] : "?");
                ArrayNode devs = r.putArray("devices");
                for (int k = 4; k < p.length; k++) devs.add(p[k].replaceAll("\\[\\d+]", ""));
                if (i + 1 < md.size()) {
                    String next = md.get(i + 1);
                    int a = next.lastIndexOf('['), b = next.lastIndexOf(']');
                    if (a >= 0 && b > a) {
                        String map = next.substring(a + 1, b);
                        r.put("map", map);
                        r.put("degraded", map.contains("_"));
                    }
                }
                if (i + 2 < md.size() && md.get(i + 2).contains("recovery") || i + 2 < md.size() && md.get(i + 2).contains("resync")) {
                    r.put("sync", md.get(i + 2).trim());
                }
            }
        } catch (Exception ignored) {
            // no software raid
        }
        return o;
    }

    // ---------------------------------------------------------------- utils

    private static String read(String p) throws IOException {
        return Files.readString(Path.of(p)).trim();
    }

    private static String readOr(Path p, String def) {
        try {
            String s = Files.readString(p).trim();
            return s.isEmpty() ? def : s;
        } catch (Exception e) {
            return def;
        }
    }

    static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    public ArrayNode processesJson(int n) {
        ArrayNode a = Json.arr();
        Map<Long, String> labels = new LinkedHashMap<>();
        for (Network.Srv s : h.net.servers.values()) if (s.pid() > 0) labels.put(s.pid(), s.def.id());
        long hz = TCK;
        double boot = 0;
        try { boot = System.currentTimeMillis() / 1000.0 - Double.parseDouble(read("/proc/uptime").split(" ")[0]); } catch (Exception ignored) {}
        for (Proc p : topProcesses(n)) {
            ObjectNode o = a.addObject();
            o.put("pid", p.pid());
            o.put("name", p.name());
            if (p.user() != null) o.put("user", p.user());
            o.put("cpu", p.cpu());
            o.put("rss", p.rss());
            o.put("threads", p.threads());
            if (boot > 0) o.put("started", (long) ((boot + p.startTicks() / (double) hz) * 1000));
            String srv = labels.get(p.pid());
            if (srv != null) o.put("server", srv);
        }
        return a;
    }
}
