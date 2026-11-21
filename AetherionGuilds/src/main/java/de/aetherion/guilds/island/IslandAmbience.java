package de.aetherion.guilds.island;

import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.MachineState;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The isle's own quiet sound bed, heard only by whoever stands on an island: wind now and then, a gull far off,
 * and close to a running machine a low hum. Every sound is sent to that one player (never broadcast), on the
 * ambient channel, so the vanilla sound slider turns it down. Runs once a second.
 */
public final class IslandAmbience {

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final Random random = new Random();
    private final Map<UUID, int[]> timers = new HashMap<>();
    private boolean enabled = true;

    public IslandAmbience(HostService hosts, StructureService structures, LogisticsService logistics) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void tick() {
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hosts.isIslandWorld(player.getWorld())) {
                timers.remove(player.getUniqueId());
                continue;
            }
            IslandHost host = hosts.at(player.getLocation());
            if (host == null) {
                continue;
            }
            // {wind, gull, hum} seconds until the next one
            int[] t = timers.computeIfAbsent(player.getUniqueId(), k -> new int[]{2 + random.nextInt(6), 8 + random.nextInt(12), 0});
            Location at = player.getLocation();
            if (--t[0] <= 0) {
                t[0] = 9 + random.nextInt(9);
                player.playSound(at, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.05f, 0.5f + random.nextFloat() * 0.2f);
            }
            if (--t[1] <= 0) {
                t[1] = 14 + random.nextInt(20);
                double angle = random.nextDouble() * Math.PI * 2;
                Location gull = at.clone().add(Math.cos(angle) * 14, 6 + random.nextInt(6), Math.sin(angle) * 14);
                player.playSound(gull, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.AMBIENT, 0.35f, 1.7f + random.nextFloat() * 0.25f);
                if (random.nextBoolean()) {
                    player.playSound(gull, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.AMBIENT, 0.25f, 1.85f);
                }
            }
            if (--t[2] <= 0) {
                t[2] = 4;
                PlacedStructure near = runningNear(host, at, 7.0);
                if (near != null) {
                    Location hum = new Location(at.getWorld(), near.x() + 0.5, near.y() + 1.5, near.z() + 0.5);
                    player.playSound(hum, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.22f, 0.55f);
                }
            }
        }
        timers.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private PlacedStructure runningNear(IslandHost host, Location at, double range) {
        PlacedStructure best = null;
        double bestDist = range * range;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type().router() || structure.building()) {
                continue;
            }
            double dx = structure.x() + 0.5 - at.getX();
            double dy = structure.y() + 1.0 - at.getY();
            double dz = structure.z() + 0.5 - at.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d >= bestDist) {
                continue;
            }
            if (logistics.status(structure) != MachineState.RUNNING) {
                continue;
            }
            best = structure;
            bestDist = d;
        }
        return best;
    }
}
