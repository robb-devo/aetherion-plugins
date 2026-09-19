package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Permanent soft green sparks on the bottom stump of every tree near the Forager.
 * Nothing else — teaching cue only.
 */
public final class LumberjackHintParticles {

    private static final String NPC_ID = "lumberjack";
    private static final double ANCHOR_X = 454.5;
    private static final double ANCHOR_Y = 46.0;
    private static final double ANCHOR_Z = 216.5;

    /** ~30 block teaching circle around the Forager. */
    private static final int RADIUS = 30;
    private static final int RADIUS_SQUARED = RADIUS * RADIUS;
    private static final int VIEW_SQUARED = 48 * 48;
    private static final int MAX_MARKS = 80;

    private final AetherionQuests plugin;

    private BukkitTask sparkTask;
    private BukkitTask scanTask;
    private final List<Location> marks = new ArrayList<>();

    public LumberjackHintParticles(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (sparkTask != null) {
            return;
        }
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::rescan, 40L, 40L);
        sparkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spark, 40L, 4L);
    }

    public void shutdown() {
        if (sparkTask != null) {
            sparkTask.cancel();
            sparkTask = null;
        }
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        marks.clear();
    }

    private void rescan() {
        marks.clear();
        Location origin = origin();
        if (origin == null) {
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        boolean anyoneNear = false;
        for (Player player : world.getPlayers()) {
            if (player.getWorld() == world && player.getLocation().distanceSquared(origin) <= VIEW_SQUARED) {
                anyoneNear = true;
                break;
            }
        }
        if (!anyoneNear) {
            return;
        }

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int dx = -RADIUS; dx <= RADIUS && marks.size() < MAX_MARKS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS && marks.size() < MAX_MARKS; dz++) {
                if (dx * dx + dz * dz > RADIUS_SQUARED) {
                    continue;
                }
                // One stump mark per column — bottommost trunk only.
                Block stump = null;
                for (int dy = -8; dy <= 20; dy++) {
                    Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (!isLog(block)) {
                        continue;
                    }
                    if (isLog(block.getRelative(BlockFace.DOWN))) {
                        continue;
                    }
                    stump = block;
                    break;
                }
                if (stump != null) {
                    marks.add(stump.getLocation().add(0.5, 0.4, 0.5));
                }
            }
        }
    }

    private void spark() {
        if (marks.isEmpty()) {
            return;
        }
        Location origin = origin();
        if (origin == null) {
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        List<Player> viewers = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getWorld() == world && player.getLocation().distanceSquared(origin) <= VIEW_SQUARED) {
                viewers.add(player);
            }
        }
        if (viewers.isEmpty()) {
            return;
        }
        for (Location at : marks) {
            if (at.getWorld() != world) {
                continue;
            }
            Block block = world.getBlockAt(at.getBlockX(), (int) Math.floor(at.getY()), at.getBlockZ());
            if (!isLog(block)) {
                continue;
            }
            for (Player viewer : viewers) {
                viewer.spawnParticle(Particle.HAPPY_VILLAGER, at, 4, 0.22, 0.28, 0.22, 0.0);
                viewer.spawnParticle(Particle.COMPOSTER, at, 2, 0.16, 0.22, 0.16, 0.0);
            }
        }
    }

    private Location origin() {
        QuestNPC npc = QuestNPCRegistry.getNPC(NPC_ID);
        if (npc != null && npc.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(npc.getEntityId());
            if (entity != null && entity.isValid() && entity.getWorld() != null) {
                return entity.getLocation();
            }
        }
        NPCDataStorage storage = plugin.getNpcDataStorage();
        if (storage != null) {
            Location saved = storage.getSavedLocation(NPC_ID);
            if (saved != null && saved.getWorld() != null) {
                return saved;
            }
        }
        World best = null;
        int bestHits = -1;
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("aedun_") || name.startsWith("ae_dun") || name.contains("farm")) {
                continue;
            }
            int hits = 0;
            int ox = (int) Math.floor(ANCHOR_X);
            int oy = (int) Math.floor(ANCHOR_Y);
            int oz = (int) Math.floor(ANCHOR_Z);
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    for (int dy = -4; dy <= 10; dy++) {
                        Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                        if (isLog(block) && !isLog(block.getRelative(BlockFace.DOWN))) {
                            hits++;
                            break;
                        }
                    }
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = world;
            }
        }
        if (best != null) {
            return new Location(best, ANCHOR_X, ANCHOR_Y, ANCHOR_Z);
        }
        World fallback = Bukkit.getWorld("world");
        if (fallback == null && !Bukkit.getWorlds().isEmpty()) {
            fallback = Bukkit.getWorlds().get(0);
        }
        return fallback == null ? null : new Location(fallback, ANCHOR_X, ANCHOR_Y, ANCHOR_Z);
    }

    private static boolean isLog(Block block) {
        return block != null && Tag.LOGS.isTagged(block.getType());
    }
}
