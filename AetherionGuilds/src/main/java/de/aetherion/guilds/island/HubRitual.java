package de.aetherion.guilds.island;

import de.aetherion.guilds.structure.PlacedStructure;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The moment the first Workshop stands: the factory has a heart. A bell tolls three times, a beam of light
 * climbs out of the roof, sparks ring the yard, and the player is told — once, plainly — what the Hub does
 * from now on. About four seconds, then the board over the Workshop lights up.
 */
public final class HubRitual {

    private final JavaPlugin plugin;

    public HubRitual(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, PlacedStructure hub) {
        World world = Bukkit.getWorld(hub.world());
        if (world == null) {
            return;
        }
        Location base = new Location(world, hub.x() + 0.5, hub.y() + 1.0, hub.z() + 0.5);
        Location top = new Location(world, hub.x() + 0.5, hub.maxY() + 1.0, hub.z() + 0.5);
        for (int i = 0; i < 3; i++) {
            int toll = i;
            later(i * 16L, () -> {
                world.playSound(top, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.2f, 0.7f + toll * 0.1f);
                world.playSound(top, Sound.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 0.6f, 0.8f);
            });
        }
        // the beam climbs for three seconds
        for (int t = 0; t < 60; t += 2) {
            int step = t;
            later(t, () -> {
                double height = Math.min(24.0, step * 0.45);
                for (double y = 0; y < height; y += 0.6) {
                    world.spawnParticle(Particle.END_ROD, top.getX(), top.getY() + y, top.getZ(), 1, 0.04, 0.0, 0.04, 0.0);
                }
                if (step % 10 == 0) {
                    ring(world, base, 3.5 + step / 20.0);
                }
            });
        }
        later(30L, () -> world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1f, 1.2f));
        later(48L, () -> {
            world.spawnParticle(Particle.FIREWORK, top, 60, 1.5, 1.0, 1.5, 0.12);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, top, 40, 1.2, 0.8, 1.2, 0.3);
            world.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.9f, 1.1f);
            if (!player.isOnline()) {
                return;
            }
            player.sendTitle("§6§l✦ THE HUB IS FOUNDED ✦", "§7your factory runs from its lectern now", 8, 80, 20);
            player.sendMessage("");
            player.sendMessage("§6§l  ✦ Your Hub stands.");
            player.sendMessage("  §7Its §flectern §7is the factory desk from now on:");
            player.sendMessage("  §f· Build §7— Mill, Depot, Splitter and more");
            player.sendMessage("  §f· Belt Layer §7— lay as many belts as you like");
            player.sendMessage("  §f· Upgrades §7and §6Coin Shortcuts");
            player.sendMessage("  §7The board over it shows every line on your island.");
            player.sendMessage("");
        });
    }

    private static void ring(World world, Location center, double radius) {
        for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2 / 28;
            world.spawnParticle(Particle.WAX_OFF, center.getX() + Math.cos(a) * radius, center.getY() + 0.2,
                    center.getZ() + Math.sin(a) * radius, 1, 0, 0.05, 0, 0.0);
        }
    }

    private void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, ticks));
    }
}
