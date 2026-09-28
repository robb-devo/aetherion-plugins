package de.aetherion.bossengine.helios.encounter;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The people in one instance and everything needed to hand them back unchanged: where they came from,
 * their game mode and flight flags. The same record is mirrored into each player's own data
 * ({@link #KEY_RETURN}), so even if the journal is lost a crashed fight still sends everyone home on
 * their next login.
 */
public final class Participants {

    public static final class Member {
        final UUID id;
        final String name;
        final Location returnTo;
        final GameMode mode;
        final boolean allowFlight;
        boolean echo;
        boolean gone;
        /** Last stage point this member stood on solid ground (void rescue target). */
        final Vector3f lastGround = new Vector3f();
        int airTicks;

        Member(Player p) {
            this.id = p.getUniqueId();
            this.name = p.getName();
            this.returnTo = p.getLocation().clone();
            this.mode = p.getGameMode();
            this.allowFlight = p.getAllowFlight();
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public boolean echo() {
            return echo;
        }

        public boolean gone() {
            return gone;
        }

        public Vector3f lastGround() {
            return lastGround;
        }
    }

    public static NamespacedKey KEY_RETURN;
    public static NamespacedKey KEY_MODE;
    public static NamespacedKey KEY_FLIGHT;

    private final Map<UUID, Member> members = new LinkedHashMap<>();

    public static void keys(Plugin plugin) {
        KEY_RETURN = new NamespacedKey(plugin, "helios_return");
        KEY_MODE = new NamespacedKey(plugin, "helios_mode");
        KEY_FLIGHT = new NamespacedKey(plugin, "helios_flight");
    }

    public Member add(Player p) {
        Member m = new Member(p);
        members.put(p.getUniqueId(), m);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(KEY_RETURN, PersistentDataType.STRING, serialize(m.returnTo));
        pdc.set(KEY_MODE, PersistentDataType.STRING, m.mode.name());
        pdc.set(KEY_FLIGHT, PersistentDataType.BYTE, (byte) (m.allowFlight ? 1 : 0));
        return m;
    }

    public Member get(UUID id) {
        return members.get(id);
    }

    public Member get(Player p) {
        return p == null ? null : members.get(p.getUniqueId());
    }

    public boolean contains(Player p) {
        Member m = get(p);
        return m != null && !m.gone;
    }

    public Collection<Member> all() {
        return members.values();
    }

    public List<UUID> ids() {
        return new ArrayList<>(members.keySet());
    }

    /** Online members still in the fight (echoes included). */
    public List<Player> present() {
        List<Player> out = new ArrayList<>();
        for (Member m : members.values()) {
            if (m.gone) {
                continue;
            }
            Player p = Bukkit.getPlayer(m.id);
            if (p != null && p.isOnline()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Online, alive, not an echo. */
    public List<Player> alive() {
        List<Player> out = new ArrayList<>();
        for (Member m : members.values()) {
            if (m.gone || m.echo) {
                continue;
            }
            Player p = Bukkit.getPlayer(m.id);
            if (p != null && p.isOnline() && !p.isDead()) {
                out.add(p);
            }
        }
        return out;
    }

    public int count() {
        return members.size();
    }

    /**
     * Sends a member home: previous game mode and flight, their return point, and clears their record.
     * Safe to call twice.
     */
    public static void sendHome(Player p, Member m, Location fallback, boolean teleport) {
        if (p == null) {
            return;
        }
        GameMode mode = m != null ? m.mode : readMode(p);
        boolean flight = m != null ? m.allowFlight : readFlight(p);
        Location to = m != null ? m.returnTo : readReturn(p);
        if (to == null || to.getWorld() == null) {
            to = fallback;
        }
        if (p.isDead()) {
            p.spigot().respawn();
        }
        if (mode != null && p.getGameMode() != mode) {
            p.setGameMode(mode);
        }
        p.setAllowFlight(flight || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        p.setFallDistance(0f);
        p.setFireTicks(0);
        if (teleport && to != null) {
            p.teleport(to);
        }
        clear(p);
    }

    public static boolean hasRecord(Player p) {
        return p.getPersistentDataContainer().has(KEY_RETURN, PersistentDataType.STRING);
    }

    public static void clear(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(KEY_RETURN);
        pdc.remove(KEY_MODE);
        pdc.remove(KEY_FLIGHT);
    }

    private static GameMode readMode(Player p) {
        String raw = p.getPersistentDataContainer().get(KEY_MODE, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    private static boolean readFlight(Player p) {
        Byte b = p.getPersistentDataContainer().get(KEY_FLIGHT, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    private static Location readReturn(Player p) {
        return parse(p.getPersistentDataContainer().get(KEY_RETURN, PersistentDataType.STRING));
    }

    public static String serialize(Location l) {
        return String.format(Locale.ROOT, "%s,%.3f,%.3f,%.3f,%.1f,%.1f",
                l.getWorld() == null ? "" : l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static Location parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] p = raw.split(",");
        if (p.length < 4) {
            return null;
        }
        World w = Bukkit.getWorld(p[0].trim());
        if (w == null) {
            return null;
        }
        try {
            Location l = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            if (p.length >= 6) {
                l.setYaw(Float.parseFloat(p[4]));
                l.setPitch(Float.parseFloat(p[5]));
            }
            return l;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
