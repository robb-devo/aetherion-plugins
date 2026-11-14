package de.aetherion.bossengine.hush;

import de.aetherion.bossengine.helios.encounter.Participants;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The people in one belfry and what is needed to hand them back unchanged: where they came from, their
 * game mode and flight flag. The record is mirrored into each player's own data, so a crashed fight
 * still sends everyone home on their next login.
 */
final class HushParty {

    static final class Member {
        final UUID id;
        final String name;
        final Location returnTo;
        final GameMode mode;
        final boolean allowFlight;
        boolean gone;
        /** Dead and waiting on the floor's edge as a spectator until the fight ends or the lullaby holds. */
        boolean down;
        int deaths;
        /** 0..100: how far the dark has got into this player. */
        double dread;
        /** Encounter clock until which this member cannot be hit. */
        int graceUntil;
        /** Last arena point on solid ground. */
        double groundX;
        double groundZ;
        int lastHintAt = -10_000;

        Member(Player p) {
            this.id = p.getUniqueId();
            this.name = p.getName();
            this.returnTo = p.getLocation().clone();
            this.mode = p.getGameMode();
            this.allowFlight = p.getAllowFlight();
        }
    }

    private static NamespacedKey keyReturn;
    private static NamespacedKey keyMode;
    private static NamespacedKey keyFlight;

    private final Map<UUID, Member> members = new LinkedHashMap<>();

    static void keys(Plugin plugin) {
        keyReturn = new NamespacedKey(plugin, "hush_return");
        keyMode = new NamespacedKey(plugin, "hush_mode");
        keyFlight = new NamespacedKey(plugin, "hush_flight");
    }

    Member add(Player p) {
        Member m = new Member(p);
        members.put(p.getUniqueId(), m);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(keyReturn, PersistentDataType.STRING, Participants.serialize(m.returnTo));
        pdc.set(keyMode, PersistentDataType.STRING, m.mode.name());
        pdc.set(keyFlight, PersistentDataType.BYTE, (byte) (m.allowFlight ? 1 : 0));
        return m;
    }

    Member get(Player p) {
        return p == null ? null : members.get(p.getUniqueId());
    }

    boolean contains(Player p) {
        Member m = get(p);
        return m != null && !m.gone;
    }

    Collection<Member> all() {
        return members.values();
    }

    int count() {
        return members.size();
    }

    List<UUID> activeIds() {
        List<UUID> out = new ArrayList<>();
        for (Member m : members.values()) {
            if (!m.gone) {
                out.add(m.id);
            }
        }
        return out;
    }

    /** Online members still in the belfry (the fallen included). */
    List<Player> present() {
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

    /** Online, alive, standing. */
    List<Player> standing() {
        List<Player> out = new ArrayList<>();
        for (Member m : members.values()) {
            if (m.gone || m.down) {
                continue;
            }
            Player p = Bukkit.getPlayer(m.id);
            if (p != null && p.isOnline() && !p.isDead()) {
                out.add(p);
            }
        }
        return out;
    }

    /* ================================================================== the way home */

    /** Previous game mode and flight, the return point, and the record is cleared. Safe to call twice. */
    static void sendHome(Player p, Member m, Location fallback, boolean teleport) {
        if (p == null) {
            return;
        }
        GameMode mode = m != null ? m.mode : readMode(p);
        boolean flight = m != null ? m.allowFlight : readFlight(p);
        Location to = m != null ? m.returnTo
                : Participants.parse(p.getPersistentDataContainer().get(keyReturn, PersistentDataType.STRING));
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
        p.resetPlayerTime();
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
        if (teleport && to != null) {
            p.teleport(to);
        }
        clear(p);
    }

    static boolean hasRecord(Player p) {
        return keyReturn != null && p.getPersistentDataContainer().has(keyReturn, PersistentDataType.STRING);
    }

    static void clear(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(keyReturn);
        pdc.remove(keyMode);
        pdc.remove(keyFlight);
    }

    private static GameMode readMode(Player p) {
        String raw = p.getPersistentDataContainer().get(keyMode, PersistentDataType.STRING);
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
        Byte b = p.getPersistentDataContainer().get(keyFlight, PersistentDataType.BYTE);
        return b != null && b == 1;
    }
}
