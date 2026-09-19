package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Ground dust along a fixed harbour path for Egon's first wood loop:
 * Egon → (300,63,-379) → (328,63,-379) → Forager (outbound),
 * reverse when delivering logs back.
 */
public final class TutorialQuestTrail {

    private static final double ARRIVED = 4.0;
    private static final double SPACING = 1.15;
    private static final double MAX_AHEAD = 56.0;

    /** Harbour path corners (same world as Egon / Forager). */
    private static final double[][] CORNERS = {
            {300.5, 63.15, -379.5},
            {328.5, 63.15, -379.5},
            {337.5, 66.15, -368.5}
    };

    private final AetherionQuests plugin;
    private final QuestManager questManager;
    private final Particle.DustOptions dust =
            new Particle.DustOptions(Color.fromRGB(255, 214, 90), 1.05f);

    private BukkitTask task;
    private int phase;

    public TutorialQuestTrail(AetherionQuests plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 30L, 8L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        phase = (phase + 1) % 6;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            List<Location> path = pathFor(player);
            if (path == null || path.size() < 2) {
                continue;
            }
            drawFromPlayer(player, path);
        }
    }

    private List<Location> pathFor(Player player) {
        if (questManager == null) {
            return null;
        }
        Quest tracked = questManager.getTrackedQuest(player);
        if (tracked == null) {
            return null;
        }
        String id = tracked.getId();
        World world = player.getWorld();
        if (world == null) {
            return null;
        }
        Location egon = npcLocation("egon");
        Location forager = npcLocation("lumberjack");
        if (egon == null || forager == null) {
            return null;
        }
        if (!world.equals(egon.getWorld()) || !world.equals(forager.getWorld())) {
            return null;
        }

        List<Location> path = new ArrayList<>(4);
        if ("welcome_aboard".equalsIgnoreCase(id)) {
            // Egon → corners → Forager
            path.add(ground(egon));
            for (double[] c : CORNERS) {
                path.add(new Location(world, c[0], c[1], c[2]));
            }
            path.add(ground(forager));
            return path;
        }
        if ("gather_wood".equalsIgnoreCase(id)) {
            // Forager → corners (reverse) → Egon
            path.add(ground(forager));
            for (int i = CORNERS.length - 1; i >= 0; i--) {
                double[] c = CORNERS[i];
                path.add(new Location(world, c[0], c[1], c[2]));
            }
            path.add(ground(egon));
            return path;
        }
        return null;
    }

    private void drawFromPlayer(Player player, List<Location> path) {
        Location feet = player.getLocation();
        int nearest = nearestIndex(feet, path);
        double drawn = 0.0;
        Location cursor = path.get(nearest).clone();
        // Start a bit ahead of the nearest node so the trail leads forward.
        for (int i = nearest; i < path.size() - 1; i++) {
            Location a = i == nearest ? cursor : path.get(i);
            Location b = path.get(i + 1);
            Vector delta = b.toVector().subtract(a.toVector());
            double len = delta.length();
            if (len < 0.01) {
                continue;
            }
            Vector step = delta.normalize().multiply(SPACING);
            int steps = Math.max(1, (int) Math.ceil(len / SPACING));
            Location point = a.clone();
            for (int s = 0; s < steps; s++) {
                if (drawn > MAX_AHEAD) {
                    return;
                }
                if ((s + phase) % 2 == 0) {
                    player.spawnParticle(Particle.DUST, point, 1, 0.03, 0.02, 0.03, 0.0, dust);
                }
                point.add(step);
                drawn += SPACING;
            }
        }
        Location end = path.get(path.size() - 1);
        if (phase % 3 == 0 && feet.distanceSquared(end) > ARRIVED * ARRIVED) {
            player.spawnParticle(Particle.END_ROD, end.clone().add(0, 0.35, 0), 1, 0.1, 0.15, 0.1, 0.0);
        }
    }

    private static int nearestIndex(Location from, List<Location> path) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            Location p = path.get(i);
            if (!from.getWorld().equals(p.getWorld())) {
                continue;
            }
            double d = from.distanceSquared(p);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        // If already past a node toward the end, nudge forward one.
        if (best < path.size() - 1 && bestDist < ARRIVED * ARRIVED) {
            return best + 1;
        }
        return best;
    }

    private static Location ground(Location npc) {
        return new Location(npc.getWorld(), npc.getX(), npc.getY() + 0.15, npc.getZ());
    }

    private Location npcLocation(String npcId) {
        QuestNPC npc = QuestNPCRegistry.getNPC(npcId);
        if (npc != null && npc.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(npc.getEntityId());
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity.getLocation();
            }
        }
        if (plugin == null || plugin.getNpcDataStorage() == null) {
            return null;
        }
        NPCDataStorage storage = plugin.getNpcDataStorage();
        return storage.getSavedLocation(npcId);
    }
}
