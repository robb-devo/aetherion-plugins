package de.aetherion.core.moderation;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.persist.AtomicYaml;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is online on the whole network, for the raid menu. Each backend writes its players (name, IP, join time,
 * skin, staff flag) to {@code <shared>/moderation/online/<port>.yml} every two seconds; the menu reads all of
 * those files that are fresh. The join time is when the player came onto this backend, kept across reloads of
 * the file; a raid shows up as a block of new names at the end of the list.
 */
public final class RaidRoster implements Listener {

    public static final String PERMISSION = "aetherion.mod.raidban";
    private static final long STALE_MS = 15_000L;

    /** One online player somewhere on the network. */
    public record Seat(UUID uuid, String name, String ip, long joined, boolean staff, String server,
                       String skin, String skinSignature) {
    }

    private final JavaPlugin plugin;
    private final File onlineDir;
    private final File mine;
    private final Map<UUID, Long> joined = new HashMap<>();
    private BukkitTask task;

    public RaidRoster(JavaPlugin plugin, File moderationRoot) {
        this.plugin = plugin;
        this.onlineDir = new File(moderationRoot, "online");
        this.mine = new File(onlineDir, Bukkit.getPort() + ".yml");
        onlineDir.mkdirs();
    }

    public void start() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            joined.putIfAbsent(p.getUniqueId(), now);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::write, 40L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (mine.isFile() && !mine.delete()) {
            plugin.getLogger().fine("raid roster file not removed");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        joined.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        joined.remove(event.getPlayer().getUniqueId());
    }

    public static boolean isStaff(Player player) {
        return player.isOp() || player.hasPermission(PERMISSION) || player.hasPermission("aetherion.core.admin");
    }

    /** Everyone online on the network, oldest join first. Players on this backend are always live. */
    public List<Seat> network() {
        Map<UUID, Seat> all = new HashMap<>();
        long now = System.currentTimeMillis();
        File[] files = onlineDir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                if (file.equals(mine) || now - file.lastModified() > STALE_MS) {
                    continue;
                }
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                String server = yml.getString("server", file.getName().replace(".yml", ""));
                ConfigurationSection players = yml.getConfigurationSection("players");
                if (players == null) {
                    continue;
                }
                for (String key : players.getKeys(false)) {
                    ConfigurationSection s = players.getConfigurationSection(key);
                    if (s == null) {
                        continue;
                    }
                    try {
                        UUID id = UUID.fromString(key);
                        all.put(id, new Seat(id, s.getString("name", "?"), s.getString("ip", ""), s.getLong("joined"),
                                s.getBoolean("staff"), server, s.getString("skin", ""), s.getString("skin-sig", "")));
                    } catch (IllegalArgumentException ignored) {
                        // not a player key
                    }
                }
            }
        }
        for (Seat seat : local()) {
            all.put(seat.uuid(), seat);
        }
        List<Seat> out = new ArrayList<>(all.values());
        out.sort(Comparator.comparingLong(Seat::joined).thenComparing(Seat::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private List<Seat> local() {
        String server = serverName();
        long now = System.currentTimeMillis();
        List<Seat> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            InetSocketAddress address = p.getAddress();
            String ip = address == null || address.getAddress() == null ? "" : address.getAddress().getHostAddress();
            String skin = "";
            String sig = "";
            PlayerProfile profile = p.getPlayerProfile();
            for (ProfileProperty prop : profile.getProperties()) {
                if ("textures".equals(prop.getName())) {
                    skin = prop.getValue();
                    sig = prop.getSignature() == null ? "" : prop.getSignature();
                }
            }
            out.add(new Seat(p.getUniqueId(), p.getName(), ip, joined.getOrDefault(p.getUniqueId(), now), isStaff(p),
                    server, skin, sig));
        }
        return out;
    }

    private void write() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("server", serverName());
        for (Seat seat : local()) {
            String k = "players." + seat.uuid();
            yml.set(k + ".name", seat.name());
            yml.set(k + ".ip", seat.ip());
            yml.set(k + ".joined", seat.joined());
            yml.set(k + ".staff", seat.staff());
            yml.set(k + ".skin", seat.skin());
            yml.set(k + ".skin-sig", seat.skinSignature());
        }
        try {
            AtomicYaml.save(yml, mine, plugin.getLogger());
        } catch (IOException ex) {
            plugin.getLogger().fine("raid roster not written: " + ex.getMessage());
        }
    }

    static String serverName() {
        AetherionCore core = AetherionCore.get();
        if (core != null && core.link() != null) {
            return core.link().serverName();
        }
        return "port " + Bukkit.getPort();
    }
}
