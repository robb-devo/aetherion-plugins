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
 * Slow, readable chop tutorial next to the Forager.
 * Beats are spaced so speech, bar, and fall never hit at once.
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

        ForagingListener listener = plugin.listener();
        Block trunk = listener.findDemoTrunk(nearForager, 6);
        if (trunk == null) {
            RUNNING.remove(viewer.getUniqueId());
            viewer.sendMessage("§6Forager: §f...tree's shy. Just hit a glowing trunk with the axe.");
            unlockChop(viewer);
            return;
        }

        ForagingListener.TreeJob job = listener.createJobPublic(trunk, viewer, null);
        if (job == null || job.fellLog == null) {
            RUNNING.remove(viewer.getUniqueId());
            viewer.sendMessage("§6Forager: §fThat trunk's decorative. Find a living glow.");
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

        // --- Beat timeline (ticks) — CHOP text + swing land ON green ---
        //  0    soft glow only (no speech, no bar)
        // 55    "watch the glow"
        //100    first swing (no bar)
        //145    "this bar is the timing"
        //185    bar appears idle
        //215    bar crawls slowly
        //     … when green CHOP hits: swing + "CHOP!" + fall immediately
        //     bar stays green briefly, then hide
        // +70 after fall → "your turn" (+ unlock)

        glowTrunk(world, fellAt);

        later(plugin, viewer, 55L, () ->
                viewer.sendMessage("§6Forager: §fSee the glow on the trunk? That's your target."));

        later(plugin, viewer, 100L, () -> swingFx(world, foragerAt, fellAt));

        later(plugin, viewer, 145L, () ->
                viewer.sendMessage("§6Forager: §fThis bar is the timing. CHOP comes late — wait for green."));

        later(plugin, viewer, 185L, () -> {
            if (plugin.hud() == null) {
                finishDemo(plugin, viewer);
                return;
            }
            // Late window (~70%). Start marker at 0; crawl slowly.
            int zoneSize = 4;
            int zoneStart = Math.max(1, ForagingStrike.SIZE - zoneSize - 3);
            DemoPulse pulse = new DemoPulse(zoneStart, zoneSize);
            plugin.hud().striking(viewer, pulse.marker, pulse.zoneStart, pulse.zoneSize, false);

            new org.bukkit.scheduler.BukkitRunnable() {
                int steps;
                int hotFrames;
                boolean chopped;

                @Override
                public void run() {
                    if (!viewer.isOnline()) {
                        cleanup(plugin, viewer);
                        cancel();
                        return;
                    }
                    steps++;
                    // Advance every 3rd run → slow crawl (~6 ticks per step).
                    if (steps % 3 == 0 && !chopped && hotFrames == 0) {
                        pulse.advance();
                    }
                    boolean hot = pulse.hot();
                    plugin.hud().striking(viewer, pulse.marker, pulse.zoneStart, pulse.zoneSize, hot);

                    if (hot && !chopped) {
                        hotFrames++;
                        // Let the bar clearly read CHOP (~0.4s), then swing+fall on that same green.
                        if (hotFrames < 4) {
                            return;
                        }
                        chopped = true;
                        swingFx(world, foragerAt, fellAt);
                        if (world != null) {
                            world.playSound(fellAt, Sound.BLOCK_WOOD_BREAK, 0.75f, 0.85f);
                            world.spawnParticle(Particle.CRIT, fellAt, 10, 0.3, 0.3, 0.3, 0.04);
                        }
                        ForagingFx.success(viewer, fellAt);
                        viewer.sendMessage("§6Forager: §aCHOP!");
                        viewer.sendActionBar(net.kyori.adventure.text.Component.text(
                                "CHOP",
                                net.kyori.adventure.text.format.NamedTextColor.GREEN
                        ));
                        listener.markDemo(job, viewer);
                        listener.collapsePublic(job);
                        later(plugin, viewer, 70L, () ->
                                viewer.sendMessage("§6Forager: §fThat's the hit. Your turn — glowing trunk, wait for green CHOP."));
                        later(plugin, viewer, 100L, () ->
                                viewer.sendMessage("§6Forager: §fTen oak logs to §eEgon §fon the pier."));
                        later(plugin, viewer, 110L, () -> finishDemo(plugin, viewer));
                    }

                    if (chopped) {
                        hotFrames++;
                        if (hotFrames >= 14) {
                            plugin.hud().hide(viewer);
                            cancel();
                        }
                        return;
                    }

                    if (steps > 200) {
                        finishDemo(plugin, viewer);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 30L, 2L);
        });
    }

    private static void finishDemo(AetherionForaging plugin, Player viewer) {
        cleanup(plugin, viewer);
        unlockChop(viewer);
    }

    private static void unlockChop(Player player) {
        if (player == null) {
            return;
        }
        try {
            Class.forName("de.aetherion.quests.bridge.QuestProgressBridge")
                    .getMethod("unlockForagerChop", Player.class)
                    .invoke(null, player);
        } catch (Throwable ignored) {
        }
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
        world.spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.25, 0.35, 0.25, 0);
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.4f);
    }

    private static void swingFx(World world, Location from, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location origin = from != null ? from.clone().add(0, 1.2, 0) : at;
        world.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 0.85f);
        world.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.BLOCK, at, 8, 0.15, 0.15, 0.15, 0.02, Material.OAK_LOG.createBlockData());
        if (from != null) {
            Vector dir = at.toVector().subtract(from.toVector());
            if (dir.lengthSquared() > 0.01) {
                dir.normalize();
                Location mid = from.clone().add(0, 1.0, 0).add(dir.multiply(0.8));
                world.spawnParticle(Particle.CRIT, mid, 4, 0.08, 0.08, 0.08, 0.01);
            }
        }
    }

    private static final class DemoPulse {
        final int zoneStart;
        final int zoneSize;
        int marker;
        private int direction = 1;

        DemoPulse(int zoneStart, int zoneSize) {
            this.zoneStart = zoneStart;
            this.zoneSize = zoneSize;
            this.marker = 0;
        }

        void advance() {
            marker += direction;
            if (marker >= ForagingStrike.SIZE - 1) {
                marker = ForagingStrike.SIZE - 1;
                direction = -1;
            } else if (marker <= 0) {
                marker = 0;
                direction = 1;
            }
        }

        boolean hot() {
            return ForagingStrike.inZone(marker, zoneStart, zoneSize);
        }
    }
}
