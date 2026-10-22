package de.aetherion.core.moderation;

import de.aetherion.core.persist.AtomicYaml;

import net.kyori.adventure.text.Component;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ban.IpBanList;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network-wide IP bans from the raid menu. Every confirm writes one file into
 * {@code <shared>/moderation/bans/} (a pardon one into {@code pardons/}); every backend polls both folders,
 * keeps the banned IPs in memory, refuses them at pre-login, kicks them if online and mirrors them into
 * its own vanilla IP ban list. One file per action, so two moderators on two backends never overwrite each other.
 */
public final class RaidBans implements Listener {

    public static final String MESSAGE =
            "You are presumed to be part of a bot raid, If this was a wrongful ban, Please appeal in the discord.";
    private static final String SOURCE = "Aetherion raid menu";

    /** One banned player: who, from which IP, by whom, when. */
    public record Entry(String ip, String name, String uuid, String by, long at) {
    }

    private final JavaPlugin plugin;
    private final File bansDir;
    private final File pardonsDir;
    private final File log;
    private final Map<String, Entry> banned = new ConcurrentHashMap<>();
    private final Set<String> seen = new HashSet<>();
    private BukkitTask task;

    public RaidBans(JavaPlugin plugin, File moderationRoot) {
        this.plugin = plugin;
        this.bansDir = new File(moderationRoot, "bans");
        this.pardonsDir = new File(moderationRoot, "pardons");
        this.log = new File(plugin.getDataFolder(), "raid-bans.log");
        bansDir.mkdirs();
        pardonsDir.mkdirs();
    }

    public void start() {
        poll();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isBanned(String ip) {
        return ip != null && banned.containsKey(ip);
    }

    /** Bans every entry network-wide (this backend at once, the others within a second). */
    public void ban(List<Entry> entries, String by) {
        if (entries.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("by", by);
        yml.set("at", now);
        yml.set("reason", MESSAGE);
        int i = 0;
        for (Entry e : entries) {
            String key = "players." + i++;
            yml.set(key + ".ip", e.ip());
            yml.set(key + ".name", e.name());
            yml.set(key + ".uuid", e.uuid());
        }
        File file = new File(bansDir, now + "-" + Bukkit.getPort() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".yml");
        try {
            AtomicYaml.save(yml, file, plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().warning("Raid ban file not written (" + ex.getMessage() + "); banning on this backend only.");
        }
        for (Entry e : entries) {
            apply(new Entry(e.ip(), e.name(), e.uuid(), by, now));
            append("BAN " + e.name() + " " + e.uuid() + " " + e.ip() + " by " + by);
        }
        seen.add("b:" + file.getName());
    }

    /** Lifts a raid ban by player name or IP. Returns how many entries matched. */
    public int pardon(String nameOrIp, String by) {
        List<Entry> hits = new ArrayList<>();
        for (Entry e : banned.values()) {
            if (e.ip().equals(nameOrIp) || e.name().equalsIgnoreCase(nameOrIp)) {
                hits.add(e);
            }
        }
        if (hits.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("by", by);
        yml.set("at", now);
        yml.set("ips", hits.stream().map(Entry::ip).toList());
        File file = new File(pardonsDir, now + "-" + Bukkit.getPort() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".yml");
        try {
            AtomicYaml.save(yml, file, plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().warning("Raid pardon file not written: " + ex.getMessage());
        }
        for (Entry e : hits) {
            lift(e.ip());
            append("PARDON " + e.name() + " " + e.ip() + " by " + by);
        }
        seen.add("p:" + file.getName());
        return hits.size();
    }

    public List<Entry> entries() {
        return List.copyOf(banned.values());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        InetAddress address = event.getAddress();
        if (address != null && isBanned(address.getHostAddress())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(MESSAGE));
        }
    }

    private void poll() {
        // Bans and pardons in time order, so a later pardon wins over an earlier ban and the other way round.
        List<File> files = new ArrayList<>();
        addAll(files, bansDir);
        addAll(files, pardonsDir);
        files.sort((a, b) -> Long.compare(stamp(a), stamp(b)));
        for (File file : files) {
            boolean ban = file.getParentFile().equals(bansDir);
            String key = (ban ? "b:" : "p:") + file.getName();
            if (!seen.add(key)) {
                continue;
            }
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            String by = yml.getString("by", "?");
            long at = yml.getLong("at", stamp(file));
            if (ban) {
                ConfigurationSection players = yml.getConfigurationSection("players");
                if (players == null) {
                    continue;
                }
                for (String k : players.getKeys(false)) {
                    String ip = players.getString(k + ".ip", "");
                    if (!ip.isBlank()) {
                        apply(new Entry(ip, players.getString(k + ".name", "?"), players.getString(k + ".uuid", ""), by, at));
                    }
                }
            } else {
                for (String ip : yml.getStringList("ips")) {
                    lift(ip);
                }
            }
        }
    }

    private void apply(Entry entry) {
        banned.put(entry.ip(), entry);
        try {
            IpBanList list = Bukkit.getBanList(BanList.Type.IP);
            InetAddress address = InetAddress.getByName(entry.ip());
            if (!list.isBanned(address)) {
                list.addBan(address, MESSAGE, (java.util.Date) null, SOURCE + " (" + entry.by() + ")");
            }
        } catch (UnknownHostException | RuntimeException ex) {
            plugin.getLogger().warning("Vanilla IP ban failed for " + entry.ip() + ": " + ex.getMessage());
        }
        for (Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            InetAddress address = online.getAddress() == null ? null : online.getAddress().getAddress();
            if (address != null && entry.ip().equals(address.getHostAddress())) {
                online.kick(Component.text(MESSAGE));
            }
        }
    }

    private void lift(String ip) {
        banned.remove(ip);
        try {
            IpBanList list = Bukkit.getBanList(BanList.Type.IP);
            InetAddress address = InetAddress.getByName(ip);
            // Only our own vanilla ban: a /ban-ip someone set by hand stays (also when old pardons replay at start).
            org.bukkit.BanEntry<InetAddress> entry = list.getBanEntry(address);
            if (entry != null && entry.getSource() != null && entry.getSource().startsWith(SOURCE)) {
                list.pardon(address);
            }
        } catch (UnknownHostException | RuntimeException ex) {
            plugin.getLogger().warning("Vanilla IP pardon failed for " + ip + ": " + ex.getMessage());
        }
    }

    private void append(String line) {
        plugin.getDataFolder().mkdirs();
        try (PrintWriter out = new PrintWriter(new FileWriter(log, true))) {
            out.println(Instant.now() + " " + line);
        } catch (IOException ex) {
            plugin.getLogger().warning("raid-bans.log: " + ex.getMessage());
        }
        plugin.getLogger().info("[raid] " + line);
    }

    private static void addAll(List<File> into, File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files != null) {
            into.addAll(Arrays.asList(files));
        }
    }

    private static long stamp(File file) {
        String name = file.getName();
        int dash = name.indexOf('-');
        try {
            return Long.parseLong(dash > 0 ? name.substring(0, dash) : name.replace(".yml", ""));
        } catch (NumberFormatException ex) {
            return file.lastModified();
        }
    }
}
