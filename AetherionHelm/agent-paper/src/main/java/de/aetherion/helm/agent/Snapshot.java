package de.aetherion.helm.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.InetSocketAddress;
import java.util.Arrays;

/** Builds the periodic messages. Runs on the main thread, so it only reads cheap counters. */
final class Snapshot {
    private Snapshot() {}

    static JsonObject stats() {
        JsonObject o = new JsonObject();
        o.addProperty("players", Bukkit.getOnlinePlayers().size());
        o.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        JsonArray tps = new JsonArray();
        for (double t : Bukkit.getTPS()) tps.add(Math.round(Math.min(20, t) * 100) / 100.0);
        o.add("tps", tps);
        long[] ticks = Bukkit.getTickTimes();
        if (ticks != null && ticks.length > 0) {
            double[] ms = new double[ticks.length];
            for (int i = 0; i < ticks.length; i++) ms[i] = ticks[i] / 1_000_000.0;
            double[] sorted = ms.clone();
            Arrays.sort(sorted);
            double sum = 0;
            for (double v : ms) sum += v;
            o.addProperty("mspt", round(sum / ms.length));
            o.addProperty("msptP95", round(sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95))]));
            o.addProperty("msptMax", round(sorted[sorted.length - 1]));
            // newest first in Paper's ring: reorder to oldest → newest from the current tick
            int cur = Bukkit.getCurrentTick() % ticks.length;
            JsonArray recent = new JsonArray();
            for (int i = 1; i <= Math.min(60, ticks.length); i++) {
                int idx = Math.floorMod(cur - Math.min(60, ticks.length) + i, ticks.length);
                recent.add(round(ms[idx]));
            }
            o.add("ticks", recent);
        }
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        o.addProperty("heapUsed", heap.getUsed());
        o.addProperty("heapCommitted", heap.getCommitted());
        o.addProperty("heapMax", heap.getMax());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            o.addProperty("cpu", round4(Math.max(0, os.getProcessCpuLoad())));
        }
        o.addProperty("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        o.addProperty("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        long gcCount = 0, gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        o.addProperty("gcCount", gcCount);
        o.addProperty("gcTime", gcTime);
        int chunks = 0, entities = 0, tiles = 0;
        JsonArray worlds = new JsonArray();
        for (World w : Bukkit.getWorlds()) {
            JsonObject x = new JsonObject();
            x.addProperty("name", w.getName());
            x.addProperty("env", w.getEnvironment().name().toLowerCase());
            x.addProperty("chunks", w.getChunkCount());
            x.addProperty("entities", w.getEntityCount());
            x.addProperty("tiles", w.getTileEntityCount());
            x.addProperty("players", w.getPlayerCount());
            x.addProperty("time", w.getTime());
            x.addProperty("storm", w.hasStorm());
            chunks += w.getChunkCount();
            entities += w.getEntityCount();
            tiles += w.getTileEntityCount();
            worlds.add(x);
        }
        o.addProperty("chunks", chunks);
        o.addProperty("entities", entities);
        o.addProperty("tiles", tiles);
        o.add("worlds", worlds);
        return o;
    }

    static JsonArray players() {
        JsonArray a = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("name", p.getName());
            Location l = p.getLocation();
            o.addProperty("world", l.getWorld() == null ? "?" : l.getWorld().getName());
            o.addProperty("x", Math.round(l.getX()));
            o.addProperty("y", Math.round(l.getY()));
            o.addProperty("z", Math.round(l.getZ()));
            o.addProperty("gamemode", p.getGameMode().name().toLowerCase());
            o.addProperty("health", Math.round(p.getHealth() * 10) / 10.0);
            AttributeInstance mh = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            o.addProperty("maxHealth", mh == null ? 20 : mh.getValue());
            o.addProperty("food", p.getFoodLevel());
            o.addProperty("level", p.getLevel());
            o.addProperty("ping", p.getPing());
            o.addProperty("op", p.isOp());
            o.addProperty("flying", p.isFlying());
            long idle = p.getIdleDuration().toSeconds();
            if (idle >= 300) o.addProperty("afk", idle);
            if (p.getClientBrandName() != null) o.addProperty("brand", p.getClientBrandName());
            o.addProperty("locale", p.locale().toString());
            InetSocketAddress addr = p.getAddress();
            if (addr != null && addr.getAddress() != null) o.addProperty("ip", addr.getAddress().getHostAddress());
            a.add(o);
        }
        return a;
    }

    static JsonObject info() {
        JsonObject o = new JsonObject();
        o.addProperty("software", Bukkit.getName() + " " + Bukkit.getVersion());
        o.addProperty("mc", Bukkit.getMinecraftVersion());
        o.addProperty("java", System.getProperty("java.version"));
        o.addProperty("jvm", System.getProperty("java.vm.name"));
        o.addProperty("cpus", Runtime.getRuntime().availableProcessors());
        JsonArray args = new JsonArray();
        ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(args::add);
        o.add("jvmArgs", args);
        o.addProperty("viewDistance", Bukkit.getViewDistance());
        o.addProperty("simulationDistance", Bukkit.getSimulationDistance());
        o.addProperty("onlineMode", Bukkit.getOnlineMode());
        o.addProperty("whitelist", Bukkit.hasWhitelist());
        o.addProperty("motd", PlainTextComponentSerializer.plainText().serialize(Bukkit.motd()));
        o.addProperty("startedAt", System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getUptime());
        JsonArray plugins = new JsonArray();
        Plugin[] all = Bukkit.getPluginManager().getPlugins();
        Arrays.sort(all, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (Plugin p : all) {
            JsonObject x = new JsonObject();
            x.addProperty("name", p.getName());
            x.addProperty("version", p.getPluginMeta().getVersion());
            x.addProperty("enabled", p.isEnabled());
            if (!p.getPluginMeta().getAuthors().isEmpty()) x.addProperty("authors", String.join(", ", p.getPluginMeta().getAuthors()));
            String d = p.getPluginMeta().getDescription();
            if (d != null) x.addProperty("description", d.length() > 140 ? d.substring(0, 140) + "…" : d);
            plugins.add(x);
        }
        o.add("plugins", plugins);
        JsonArray worlds = new JsonArray();
        for (World w : Bukkit.getWorlds()) {
            JsonObject x = new JsonObject();
            x.addProperty("name", w.getName());
            x.addProperty("env", w.getEnvironment().name().toLowerCase());
            x.addProperty("difficulty", w.getDifficulty().name().toLowerCase());
            x.addProperty("border", Math.round(w.getWorldBorder().getSize()));
            x.addProperty("pvp", w.getPVP());
            worlds.add(x);
        }
        o.add("worlds", worlds);
        return o;
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
