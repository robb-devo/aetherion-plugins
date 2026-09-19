package de.aetherion.items.world;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Soft-eject gate for the Colosseum until a player has started the Proctor escort.
 * Also draws a light pad marker at the arena center.
 */
public final class ColosseumGateService implements Listener, Runnable {

    private static final Particle.DustOptions PAD_RING =
            new Particle.DustOptions(Color.fromRGB(60, 230, 255), 1.05f);
    private static final Particle.DustOptions PAD_GLOW =
            new Particle.DustOptions(Color.fromRGB(255, 150, 80), 1.15f);

    private final JavaPlugin plugin;
    private final File file;
    private final Set<UUID> unlocked = new HashSet<>();
    /** Finished the one-time Proctor walk — may self-summon at the pad. */
    private final Set<UUID> taught = new HashSet<>();
    private final Set<UUID> ejectCooldown = new HashSet<>();

    private static volatile ColosseumGateService instance;

    public ColosseumGateService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "colosseum-unlock.yml");
        instance = this;
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 30L, 8L);
    }

    public static ColosseumGateService get() {
        return instance;
    }

    public boolean isUnlocked(Player player) {
        return player != null && unlocked.contains(player.getUniqueId());
    }

    public boolean isTaught(Player player) {
        return player != null && taught.contains(player.getUniqueId());
    }

    public void unlock(Player player) {
        if (player == null) {
            return;
        }
        if (unlocked.add(player.getUniqueId())) {
            save();
        }
        unlockHubTeleport(player);
    }

    /** Call once the Proctor spill demo finishes — enables self-altar. */
    public void markTaught(Player player) {
        if (player == null) {
            return;
        }
        boolean changed = unlocked.add(player.getUniqueId());
        changed |= taught.add(player.getUniqueId());
        if (changed) {
            save();
        }
        unlockHubTeleport(player);
    }

    /** Soft gate + Hub teleport unlock when the Proctor opens the ring. */
    private void unlockHubTeleport(Player player) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub == null || player == null) {
            return;
        }
        if (hub.unlockNew(player.getUniqueId(), "colosseum")) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.15f);
            player.sendMessage("§b✦ §eNew teleport: §fColosseum§e.");
            player.sendMessage("§7Open Aetherion Manager → Teleports to return to the ring.");
        }
    }

    @Override
    public void run() {
        World world = Bukkit.getWorld(ColosseumArena.WORLD);
        if (world == null) {
            return;
        }
        Location pad = ColosseumArena.bossSpawn(world).add(0, 1.05, 0);
        double spin = (System.currentTimeMillis() % 3000L) / 3000.0 * Math.PI * 2.0;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(pad) > 48 * 48) {
                continue;
            }
            for (int i = 0; i < 5; i++) {
                double ang = spin + (Math.PI * 2.0 * i / 5.0);
                Location rim = pad.clone().add(Math.cos(ang) * 0.48, 0.02, Math.sin(ang) * 0.48);
                player.spawnParticle(Particle.DUST, rim, 1, 0, 0, 0, 0, PAD_RING, true);
            }
            player.spawnParticle(Particle.DUST, pad, 1, 0.12, 0.04, 0.12, 0, PAD_GLOW, true);
            if (System.currentTimeMillis() % 1600L < 80L) {
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, pad, 1, 0.15, 0.06, 0.15, 0.002, null, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.hasPermission("aetherion.items.dev")) {
            return;
        }
        if (isUnlocked(player)) {
            return;
        }
        if (!ColosseumArena.contains(to)) {
            return;
        }
        // Already inside from previous tick without unlock — still eject.
        Location eject = ejectPoint(to);
        event.setTo(eject);
        UUID id = player.getUniqueId();
        if (ejectCooldown.add(id)) {
            player.sendMessage("§6Colosseum §8» §7The ring stays closed until the §6Proctor §7studies a Crypt vial with you.");
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.45f, 0.7f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> ejectCooldown.remove(id), 40L);
        }
    }

    private static Location ejectPoint(Location inside) {
        double dx = inside.getX() - ColosseumArena.PAD_X;
        double dz = inside.getZ() - ColosseumArena.PAD_Z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        double scale = (ColosseumArena.RADIUS + 2.5) / len;
        Location out = inside.clone();
        out.setX(ColosseumArena.PAD_X + dx * scale);
        out.setZ(ColosseumArena.PAD_Z + dz * scale);
        out.setY(Math.max(inside.getY(), ColosseumArena.PAD_Y));
        return out;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String raw : config.getStringList("unlocked")) {
            try {
                unlocked.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String raw : config.getStringList("taught")) {
            try {
                taught.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        // Legacy: anyone already unlocked before "taught" existed keeps self-summon.
        taught.addAll(unlocked);
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("unlocked", unlocked.stream().map(UUID::toString).sorted().toList());
        config.set("taught", taught.stream().map(UUID::toString).sorted().toList());
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save colosseum-unlock.yml: " + exception.getMessage());
        }
    }
}
