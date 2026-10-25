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
                foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer,
                        "Watch: glowing trunk, then hit again on green §aCHOP§f.",
                        "Schau: leuchtender Stamm, dann nochmal schlagen bei grünem §aCHOP§f.")));

        later(plugin, viewer, 100L, () -> swingFx(world, foragerAt, fellAt));


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
                    if (!chopped && listener.chopping(viewer)) {
                        // they went for a tree themselves: their own bar owns the screen
                        finishDemo(plugin, viewer);
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
                            de.aetherion.core.api.Vfx.puff(fellAt, Particle.CRIT, 10, Math.max(0.3, 0.3), null);
                        }
                        ForagingFx.success(viewer, fellAt);
                        foragerSay(viewer, "§aCHOP!");
                        viewer.sendActionBar(net.kyori.adventure.text.Component.text(
                                "CHOP",
                                net.kyori.adventure.text.format.NamedTextColor.GREEN
                        ));
                        listener.markDemo(job, viewer);
                        listener.collapsePublic(job);
                        later(plugin, viewer, 40L, () ->
                                foragerSay(viewer, de.aetherion.core.exile.Say.t(viewer,
                                        "Your turn. Ten oak for §eEgon§f on the pier.",
                                        "Du bist dran. Zehn Eiche für §eEgon§f am Pier.")));
                        later(plugin, viewer, 50L, () -> finishDemo(plugin, viewer));
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
