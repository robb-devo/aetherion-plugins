package de.aetherion.core.api;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Particles are accents, not the effect: one filter on every particle packet a player receives, from any plugin
 * (bosses, skills, weapons, world ambience). Bursts are cut to {@code scale} of their count (at least 1, at most
 * {@code max-per-burst}), each player gets at most {@code budget-per-tick} particles a tick (the rest is dropped),
 * and nothing is forced past the vanilla 32-block range unless {@code long-range} is on. Shapes drawn from single
 * particles (rings, lines) keep every point until the budget runs out; display entities and the pack carry the look.
 *
 * <p>Needs ProtocolLib; the only class that touches it, so Core runs without it.
 */
public final class ParticleDiet {

    private final double scale;
    private final int maxPerBurst;
    private final int budget;
    private final boolean longRange;
    /** Per player: {tick, particles sent this tick}. Packets go out on more than one thread. */
    private final Map<UUID, long[]> spent = new ConcurrentHashMap<>();

    private ParticleDiet(ConfigurationSection cfg) {
        this.scale = Math.max(0.05, Math.min(1.0, cfg == null ? 0.35 : cfg.getDouble("scale", 0.35)));
        this.maxPerBurst = Math.max(1, cfg == null ? 10 : cfg.getInt("max-per-burst", 10));
        this.budget = Math.max(1, cfg == null ? 60 : cfg.getInt("budget-per-tick", 60));
        this.longRange = cfg != null && cfg.getBoolean("long-range", false);
    }

    /** Registers the filter. Call once ProtocolLib is enabled. */
    public static void install(JavaPlugin plugin, ConfigurationSection cfg) {
        ParticleDiet diet = new ParticleDiet(cfg);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGH,
                PacketType.Play.Server.WORLD_PARTICLES) {
            @Override
            public void onPacketSending(PacketEvent event) {
                diet.filter(event);
            }
        });
        plugin.getLogger().info("Particle diet on: x" + diet.scale + ", max " + diet.maxPerBurst + " a burst, "
                + diet.budget + " a tick per player" + (diet.longRange ? "" : ", 32-block range"));
    }

    private void filter(PacketEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        PacketContainer packet = event.getPacket();
        int count = packet.getIntegers().read(0);
        // count 0 is one particle whose offsets are its velocity: leave it as it is.
        int cut = count <= 0 ? 0 : Math.max(1, Math.min(maxPerBurst, (int) Math.round(count * scale)));
        long tick = Bukkit.getCurrentTick();
        long[] used = spent.computeIfAbsent(event.getPlayer().getUniqueId(), id -> new long[] {tick, 0L});
        synchronized (used) {
            if (used[0] != tick) {
                used[0] = tick;
                used[1] = 0L;
            }
            int cost = Math.max(1, cut);
            if (used[1] + cost > budget) {
                event.setCancelled(true);
                return;
            }
            used[1] += cost;
        }
        if (cut != count) {
            packet.getIntegers().write(0, cut);
        }
        if (!longRange && packet.getBooleans().size() > 0 && packet.getBooleans().read(0)) {
            packet.getBooleans().write(0, false);
        }
    }
}
