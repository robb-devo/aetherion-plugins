package de.aetherion.gnomon.encounter;

import de.aetherion.gnomon.fx.Fx;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.WeatherType;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Who is in the fight, and their private sky. Joining (walking onto the dial in survival/adventure) gives a player the
 * night sky and the sealed edge; leaving, dying, quitting or changing worlds gives everything back.
 */
public final class Party {

    /** Set while a player wears the fight's sky; lets a crash / relog give it back. */
    public static final NamespacedKey MARK = new NamespacedKey("aetheriongnomon", "in_fight");

    private final Arena arena;
    private final boolean seal;
    private final boolean night;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private Consumer<Player> onJoin;
    private BiConsumer<UUID, String> onLeave;
    private boolean open = true;

    public Party(Arena arena, boolean seal, boolean night) {
        this.arena = arena;
        this.seal = seal;
        this.night = night;
    }

    public void onJoin(Consumer<Player> callback) {
        this.onJoin = callback;
    }

    public void onLeave(BiConsumer<UUID, String> callback) {
        this.onLeave = callback;
    }

    /** No new members (death cinematic). */
    public void close() {
        open = false;
    }

    public boolean contains(Player p) {
        return p != null && members.containsKey(p.getUniqueId());
    }

    public boolean contains(UUID id) {
        return members.containsKey(id);
    }

    public int size() {
        return members.size();
    }

    public Collection<UUID> ids() {
        return new ArrayList<>(members.keySet());
    }

    public String name(UUID id) {
        return members.get(id);
    }

    /** Members currently online in the arena world. */
    public List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (UUID id : members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getWorld() == arena.world()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Everyone who should see/hear the fight: members plus spectators/admins on the dial. */
    public List<Player> audience() {
        List<Player> out = new ArrayList<>();
        for (Player p : arena.world().getPlayers()) {
            if (arena.distance(p.getLocation().getX(), p.getLocation().getZ()) <= arena.leaveRadius()) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean eligible(Player p) {
        GameMode m = p.getGameMode();
        return !p.isDead() && (m == GameMode.SURVIVAL || m == GameMode.ADVENTURE);
    }

    public void tick() {
        // joins
        if (open) {
            for (Player p : arena.world().getPlayers()) {
                if (members.containsKey(p.getUniqueId()) || !eligible(p)) {
                    continue;
                }
                double d = arena.distance(p.getLocation().getX(), p.getLocation().getZ());
                double dy = Math.abs(p.getLocation().getY() - arena.floorY());
                if (d <= arena.radius() + 1 && dy < 14) {
                    add(p);
                }
            }
        }
        // leaves
        for (UUID id : new ArrayList<>(members.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                remove(id, null);
                continue;
            }
            if (p.getWorld() != arena.world() || p.isDead() || !eligible(p)
                    || arena.distance(p.getLocation().getX(), p.getLocation().getZ()) > arena.leaveRadius()) {
                remove(id, p);
            }
        }
    }

    private void add(Player p) {
        members.put(p.getUniqueId(), p.getName());
        apply(p);
        if (onJoin != null) {
            onJoin.accept(p);
        }
    }

    /** Removes a member and gives their sky back ({@code p} may be null when they already left the server). */
    public void remove(UUID id, Player p) {
        String name = members.remove(id);
        if (name == null) {
            return;
        }
        if (p != null) {
            restore(p);
        }
        if (onLeave != null) {
            onLeave.accept(id, name);
        }
    }

    public static boolean marked(Player p) {
        return p != null && p.getPersistentDataContainer().has(MARK, PersistentDataType.BYTE);
    }

    private void apply(Player p) {
        p.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) 1);
        if (night) {
            p.setPlayerTime(18000L, false);
            p.setPlayerWeather(WeatherType.CLEAR);
        }
        if (seal) {
            WorldBorder b = Bukkit.createWorldBorder();
            b.setCenter(arena.cx(), arena.cz());
            b.setSize((arena.radius() + 4) * 2);
            b.setWarningDistance(4);
            b.setWarningTime(0);
            p.setWorldBorder(b);
        }
    }

    public static void restore(Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        p.getPersistentDataContainer().remove(MARK);
        p.resetPlayerTime();
        p.resetPlayerWeather();
        if (p.getWorldBorder() != null) {
            p.setWorldBorder(null);
        }
        Fx.clearEffects(p);
    }

    public void releaseAll() {
        for (UUID id : new ArrayList<>(members.keySet())) {
            remove(id, Bukkit.getPlayer(id));
        }
    }
}
