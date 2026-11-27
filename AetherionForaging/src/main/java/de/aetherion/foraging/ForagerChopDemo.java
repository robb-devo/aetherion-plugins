package de.aetherion.foraging;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Slow, readable chop tutorial next to the Forager (A-51: aim the fall, no timing bar).
 * Beats are spaced so speech, the lane and the fall never hit at once.
 * <p>
 * Watching is optional: chopping is open from the first tick (people go straight for a tree and only got
 * "watch first" errors), and a chop of the player's own ends the demo.
 */
public final class ForagerChopDemo {

    private static final java.util.Set<UUID> RUNNING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private ForagerChopDemo() {
    }

    public static boolean isRunning(Player player) {
        return player != null && RUNNING.contains(player.getUniqueId());
    }

    public static void play(Player viewer, Location nearForager) {
        if (viewer == null || nearForager == null || nearForager.getWorld() == null) {
            unlockChop(viewer);
            return;
        }
        AetherionForaging plugin = AetherionForaging.getInstance();
        if (plugin == null) {
            unlockChop(viewer);
            return;
        }
        if (!RUNNING.add(viewer.getUniqueId())) {
            return;
        }
        unlockChop(viewer);

        ForagingListener listener = plugin.listener();
        Block trunk = listener.findDemoTrunk(nearForager, 6);
        if (trunk == null) {
            RUNNING.remove(viewer.getUniqueId());
            foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer, "Just hit a glowing trunk with the axe.",
                    "Schlag einfach einen leuchtenden Stamm mit der Axt."));
            unlockChop(viewer);
            return;
        }

        ForagingListener.TreeJob job = listener.createJobPublic(trunk, viewer, null);
        if (job == null || job.fellLog == null) {
            RUNNING.remove(viewer.getUniqueId());
            foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer, "Find a glowing trunk and hit it with the axe.",
                    "Such einen leuchtenden Stamm und schlag ihn mit der Axt."));
            unlockChop(viewer);
            return;
        }

        Location fellBase = job.fellLocation();
        if (fellBase == null) {
            RUNNING.remove(viewer.getUniqueId());
            unlockChop(viewer);
            return;
        }
        final Location fellAt = fellBase.clone().add(0.5, 0.5, 0.5);
        final World world = fellAt.getWorld();
        final Location foragerAt = nearForager.clone();

        // --- Beat timeline (ticks), 2026-11-27: a fell is hits on the trunk ---
        //  0    soft glow only (no speech)
        // 55    "hit the trunk again and again"
        //100    first swing (notch bar 1/3)
        //130    "a glowing spot counts twice", swings 2 and 3 every 21 ticks
        // last  the tree falls away from the Forager
        // +40   "your turn" (+ unlock)

        glowTrunk(world, fellAt);

        later(plugin, viewer, 55L, () ->
                foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer,
                        "Watch: keep hitting the trunk. Every hit cuts the notch deeper, then down it comes.",
                        "Schau: immer wieder gegen den Stamm. Jeder Schlag macht die Kerbe tiefer, dann fällt er.")));

        later(plugin, viewer, 100L, () -> {
            swingFx(world, foragerAt, fellAt);
            if (plugin.hud() != null) {
                plugin.hud().demo(viewer, 1, 3);
            }
        });

        later(plugin, viewer, 130L, () -> {
            if (listener.chopping(viewer)) {
                finishDemo(plugin, viewer);
                return;
            }
            final org.bukkit.util.Vector dir = listener.demoDirection(job, foragerAt);
            final Location base = job.baseLocation();
            if (base == null || plugin.hud() == null) {
                finishDemo(plugin, viewer);
                return;
            }
            final int reach = job.reach();
            foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer,
                    "When a spot on the trunk glows §6gold§f, hit it: that hit counts twice. Better axes need fewer hits.",
                    "Leuchtet eine Stelle am Stamm §6golden§f, triff sie: der Schlag zählt doppelt. Bessere Äxte brauchen weniger Schläge."));
            new org.bukkit.scheduler.BukkitRunnable() {
                int ticks;

                @Override
                public void run() {
                    if (!viewer.isOnline()) {
                        cleanup(plugin, viewer);
                        cancel();
                        return;
                    }
                    if (listener.chopping(viewer)) {
                        // they went for a tree themselves: their own aim owns the screen
                        finishDemo(plugin, viewer);
                        cancel();
                        return;
                    }
                    ticks += 3;
                    if (ticks == 21) {
                        swingFx(world, foragerAt, fellAt);
                        plugin.hud().demo(viewer, 2, 3);
                    }
                    if (ticks < 63) {
                        return;
                    }
                    plugin.hud().demo(viewer, 3, 3);
                    cancel();
                    swingFx(world, foragerAt, fellAt);
                    if (world != null) {
                        world.playSound(fellAt, Sound.BLOCK_WOOD_BREAK, 0.75f, 0.85f);
                        de.aetherion.core.api.Vfx.puff(fellAt, Particle.CRIT, 10, Math.max(0.3, 0.3), null);
                    }
                    ForagingFx.success(viewer, fellAt);
                    foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer, "§aTimber!", "§aBaum fällt!"));
                    plugin.hud().hide(viewer);
                    listener.markDemo(job, viewer);
                    listener.fallPublic(job, dir);
                    later(plugin, viewer, 40L, () ->
                            foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer,
                                    "Your turn. Ten oak for §eEgon§f on the pier.",
                                    "Du bist dran. Zehn Eiche für §eEgon§f am Pier.")));
                    later(plugin, viewer, 50L, () -> finishDemo(plugin, viewer));
                }
            }.runTaskTimer(plugin, 0L, 3L);
        });
    }

    /** The lane and the gold ring, for the watching player only. */
    private static void drawLane(Player viewer, Location base, org.bukkit.util.Vector dir, int reach, int ticks) {
        Particle.DustOptions green = new Particle.DustOptions(org.bukkit.Color.fromRGB(110, 230, 90), 1.1f);
        Particle.DustOptions gold = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 240, 120), 1.6f);
        for (double d = 1.0d; d <= reach; d += 0.75d) {
            Location at = base.clone().add(dir.getX() * d, 0.15d, dir.getZ() * d);
            viewer.spawnParticle(Particle.DUST, at, 1, 0.04, 0.0, 0.04, 0.0, green);
        }
        double along = Math.max(2.0d, reach * 0.75d);
        Location ring = base.clone().add(dir.getX() * along, 0.12d, dir.getZ() * along);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0d + ticks * 0.08d;
            viewer.spawnParticle(Particle.DUST, ring.clone().add(Math.cos(a) * 1.1d, 0, Math.sin(a) * 1.1d), 1, 0, 0, 0, 0.0, gold);
        }
        viewer.spawnParticle(Particle.WAX_ON, ring.clone().add(0, 0.6, 0), 3, 0.25, 0.35, 0.25, 0.0);
    }

    private static void finishDemo(AetherionForaging plugin, Player viewer) {
        cleanup(plugin, viewer);
        unlockChop(viewer);
    }

    private static void unlockChop(Player player) {
        if (player == null) {
            return;
        }
        de.aetherion.core.api.QuestProgressAccess quests = de.aetherion.core.api.AetherServices.quests();
        if (quests != null) {
            quests.unlockForagerChop(player);
        }
    }

    /** Bubble + chat when Quests is up; plain chat fallback otherwise. */
    private static void foragerSay(Player player, String line) {
        if (player == null || line == null || line.isBlank()) {
            return;
        }
        de.aetherion.core.api.QuestProgressAccess quests = de.aetherion.core.api.AetherServices.quests();
        if (quests != null) {
            quests.npcLine(player, "lumberjack", "Forager", line);
            return;
        }
        player.sendMessage("§6Forager: §f" + line);
    }

    private static void later(AetherionForaging plugin, Player viewer, long delay, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline()) {
                task.run();
            } else {
                RUNNING.remove(viewer.getUniqueId());
            }
        }, delay);
    }

    /** Quit mid-demo: drop the running flag; chop stays locked until they finish a demo. */
    public static void releaseOnQuit(Player player) {
        if (player != null) {
            RUNNING.remove(player.getUniqueId());
        }
    }

    private static void cleanup(AetherionForaging plugin, Player viewer) {
        if (plugin != null && plugin.hud() != null) {
            plugin.hud().hide(viewer);
        }
        if (viewer != null) {
            RUNNING.remove(viewer.getUniqueId());
        }
    }

    private static void glowTrunk(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        de.aetherion.core.api.Vfx.puff(at, Particle.HAPPY_VILLAGER, 8, Math.max(0.25, 0.25), null);
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.4f);
    }

    private static void swingFx(World world, Location from, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location origin = from != null ? from.clone().add(0, 1.2, 0) : at;
        world.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 0.85f);
        de.aetherion.core.api.Vfx.puff(at, Particle.SWEEP_ATTACK, 1, Math.max(0, 0), null);
        de.aetherion.core.api.Vfx.puff(at, Particle.BLOCK, 8, Math.max(0.15, 0.15), Material.OAK_LOG.createBlockData());
        if (from != null) {
            Vector dir = at.toVector().subtract(from.toVector());
            if (dir.lengthSquared() > 0.01) {
                dir.normalize();
                Location mid = from.clone().add(0, 1.0, 0).add(dir.multiply(0.8));
                de.aetherion.core.api.Vfx.puff(mid, Particle.CRIT, 4, Math.max(0.08, 0.08), null);
            }
        }
    }
}
