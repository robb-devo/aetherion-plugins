package de.aetherion.quests.chest;

import de.aetherion.quests.AetherionQuests;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;

import java.util.ArrayList;
import java.util.List;

import static de.aetherion.quests.chest.ExploreChestKind.COMMON;
import static de.aetherion.quests.chest.ExploreChestKind.EPIC;
import static de.aetherion.quests.chest.ExploreChestKind.LEGENDARY;
import static de.aetherion.quests.chest.ExploreChestKind.MYTHIC;
import static de.aetherion.quests.chest.ExploreChestKind.RARE;
import static de.aetherion.quests.chest.ExploreChestKind.UNCOMMON;

/**
 * The authored exploration-chest scatter on the Origin map, Common → Mythic.
 * <p>
 * Rarity follows the journey: Commons beside the early roads (harbour → Meres → Capital, and the
 * roads out to every skill), Uncommons a step off them, Rare and up hidden or high, the Mythic by a
 * landmark you climb to. Each spot was measured on the live map: the feet block is free, the ground
 * under it is a full block, the block above is air, it is reachable on foot, and it keeps clear of
 * NPCs, tale chests, skyway pads and updrafts, the Colosseum floor, the dojo and the ferry path.
 * <p>
 * A spot is placed once, on the first start that finds its ground as surveyed (±1 block), and is
 * then recorded in {@code explore-chests.yml}: a chest an admin removes never comes back by itself.
 * {@code /aquest chests scatter} puts missing ones back, {@code /aquest chests unscatter} takes the
 * whole scatter out again.
 */
public final class ExploreChestScatter {

    private record Spot(String id, ExploreChestKind kind, int x, int y, int z, BlockFace face) {
    }

    /** An existing hand-placed chest that sits on the starter path and reads too rich for it. */
    private record Retier(String id, int x, int y, int z, ExploreChestKind from, ExploreChestKind to) {
    }

    /** One step of a scatter run, done once its chunk is loaded. */
    private record Job(int x, int z, Runnable work) {
    }

    private static final Spot[] SPOTS = {
            // Common — beside the early roads, in plain sight, tucked against a bush, a wall or a rock.
            new Spot("harbour_south_road", COMMON, 262, 64, -284, BlockFace.WEST),
            new Spot("harbour_west_road", COMMON, 155, 63, -425, BlockFace.SOUTH),
            new Spot("seawatch_road", COMMON, 397, 65, -447, BlockFace.WEST),
            new Spot("meres_road", COMMON, 246, 59, -145, BlockFace.EAST),
            new Spot("meres_west_road", COMMON, 147, 55, -83, BlockFace.SOUTH),
            new Spot("hive_road", COMMON, 292, 61, -100, BlockFace.WEST),
            new Spot("capital_east_gate", COMMON, 89, 57, -77, BlockFace.NORTH),
            new Spot("capital_west_road", COMMON, -74, 58, 1, BlockFace.WEST),
            new Spot("mine_road", COMMON, 22, 57, 152, BlockFace.NORTH),
            new Spot("farm_road", COMMON, -140, 61, 117, BlockFace.WEST),
            new Spot("colosseum_road", COMMON, -226, 55, -108, BlockFace.SOUTH),
            new Spot("bloom_road", COMMON, -117, 59, -449, BlockFace.SOUTH),
            new Spot("crossing_road", COMMON, -248, 61, -295, BlockFace.EAST),
            new Spot("vex_road", COMMON, 104, 58, 56, BlockFace.EAST),

            // Uncommon — a step off the road: a shore, a hollow, a library court, a ridge shelf.
            new Spot("north_strand", UNCOMMON, 294, 64, -549, BlockFace.WEST),
            new Spot("ore_ridge", UNCOMMON, 88, 105, -335, BlockFace.EAST),
            new Spot("great_mere", UNCOMMON, 217, 54, -175, BlockFace.NORTH),
            new Spot("golden_blooms", UNCOMMON, 370, 66, -114, BlockFace.SOUTH),
            new Spot("great_library", UNCOMMON, -54, 58, 93, BlockFace.NORTH),
            new Spot("mine_hill", UNCOMMON, -21, 72, 222, BlockFace.WEST),
            new Spot("starbell_hollow", UNCOMMON, -177, 59, 100, BlockFace.SOUTH),
            new Spot("ringside", UNCOMMON, -314, 56, -172, BlockFace.WEST),
            new Spot("bloom_east", UNCOMMON, 42, 83, -555, BlockFace.SOUTH),
            new Spot("lily_lake", UNCOMMON, -421, 57, -350, BlockFace.SOUTH),
            new Spot("scar_rim", UNCOMMON, 104, 58, 147, BlockFace.WEST),

            // Rare — out of sight of every road.
            new Spot("clucksworth_hidden", RARE, -255, 61, 203, BlockFace.NORTH),
            new Spot("colosseum_hidden", RARE, -377, 66, -93, BlockFace.SOUTH),
            new Spot("old_shrine", RARE, -288, 70, -514, BlockFace.EAST),

            // Epic — high or walled in, by a landmark.
            new Spot("frostcap", EPIC, 205, 97, -173, BlockFace.EAST),
            new Spot("the_ribcage", EPIC, 285, 63, 141, BlockFace.SOUTH),
            new Spot("twin_crags", EPIC, -404, 94, 167, BlockFace.SOUTH),

            // Legendary — below Wildwatch Bluff, over the wilds.
            new Spot("wildwatch_bluff", LEGENDARY, -171, 94, -275, BlockFace.SOUTH),

            // Mythic — on the shoulder under Crag Peak (the Crag Updraft gets you there).
            new Spot("crag_peak", MYTHIC, -400, 134, 25, BlockFace.NORTH),
    };

    private static final Retier[] RETIERS = {
            // Ten blocks from the first spawn, and at the Quartermaster's elbow: the first chests a new player opens.
            new Retier("retier_spawn_pier", 288, 68, -394, RARE, UNCOMMON),
            new Retier("retier_qm_forge", 174, 64, -365, RARE, UNCOMMON),
    };

    /** Same fingerprint as the island cast: three harbour blocks only the Origin map has. */
    private static final int[][] FINGERPRINT = {{316, 62, -382}, {318, 62, -382}, {322, 62, -382}};
    private static final Material[] FINGERPRINT_TYPES = {Material.COARSE_DIRT, Material.JUNGLE_PLANKS, Material.GRANITE};

    private final AetherionQuests plugin;
    private final ExploreChestService chests;
    private boolean running;

    public ExploreChestScatter(AetherionQuests plugin, ExploreChestService chests) {
        this.plugin = plugin;
        this.chests = chests;
    }

    public static int spotCount() {
        return SPOTS.length;
    }

    /** Boot: place every spot that was never placed before. */
    public void autoSeed() {
        String result = run(false);
        if (result != null && !result.isBlank()) {
            plugin.getLogger().info("Explore chests: " + result.replaceAll("§.", ""));
        }
    }

    /**
     * @param restore also put back spots that were placed before and are missing now
     * @return a status line ("" when there's nothing to do here)
     */
    public String run(boolean restore) {
        if (running) {
            return "§7Scatter already running.";
        }
        World world = Bukkit.getWorld("world");
        if (world == null || dungeonBackend()) {
            return "";
        }
        if (!originMap(world)) {
            return "§7Not the Origin map here — nothing scattered.";
        }
        List<Job> todo = new ArrayList<>();
        List<String> placed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Spot spot : SPOTS) {
            String at = chests.seededAt(spot.id());
            if (at != null && !restore) {
                continue;
            }
            todo.add(new Job(spot.x(), spot.z(), () -> {
                if (at != null) {
                    Block was = ExploreChestService.blockAt(at);
                    if (was != null && chests.isExploreChest(was)) {
                        return; // still there
                    }
                }
                Block target = ground(world, spot);
                if (target == null) {
                    skipped.add(spot.id());
                    return;
                }
                chests.seed(spot.id(), target, spot.kind(), spot.face());
                placed.add(spot.id());
            }));
        }
        for (Retier retier : RETIERS) {
            if (chests.seededAt(retier.id()) != null && !restore) {
                continue;
            }
            todo.add(new Job(retier.x(), retier.z(), () -> {
                Block block = world.getBlockAt(retier.x(), retier.y(), retier.z());
                if (chests.kindOf(block) == retier.from()) {
                    chests.retier(block, retier.to());
                    placed.add(retier.id());
                }
                // Gone or already changed by hand: either way, never touch it again.
                chests.markSeeded(retier.id(), ExploreChestService.keyOf(block));
            }));
        }
        if (todo.isEmpty()) {
            return restore ? "§7Every scatter chest is in place." : "";
        }
        running = true;
        // Chunks load async first, a few ticks apart, so a cold start never stalls.
        int[] left = {todo.size()};
        for (int i = 0; i < todo.size(); i++) {
            Job job = todo.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> world.getChunkAtAsync(job.x() >> 4, job.z() >> 4).thenAccept(loaded -> {
                try {
                    job.work().run();
                } catch (Throwable throwable) {
                    plugin.getLogger().warning("Explore chests: scatter step failed: " + throwable);
                }
                if (--left[0] == 0) {
                    finish(placed, skipped);
                }
            }), 4L * i);
        }
        return "§aScattering §f" + todo.size() + "§a chest job(s)…";
    }

    /** Takes the scatter back out (spots stay marked, so a restart doesn't bring them back) and undoes the re-tiers. */
    public String undo() {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            return "§cNo world 'world' here.";
        }
        int removed = 0;
        int restored = 0;
        for (Spot spot : SPOTS) {
            String at = chests.seededAt(spot.id());
            Block block = at == null ? null : ExploreChestService.blockAt(at);
            if (block != null && chests.kindOf(block) == spot.kind()) {
                chests.drop(block);
                removed++;
            }
        }
        for (Retier retier : RETIERS) {
            Block block = world.getBlockAt(retier.x(), retier.y(), retier.z());
            if (chests.seededAt(retier.id()) != null && chests.kindOf(block) == retier.to()) {
                chests.retier(block, retier.from());
                restored++;
            }
        }
        return "§eRemoved §f" + removed + "§e scatter chest(s), restored §f" + restored + "§e re-tier(s). "
                + "§7/aquest chests scatter §eputs them back.";
    }

    private void finish(List<String> placed, List<String> skipped) {
        running = false;
        chests.save();
        plugin.getLogger().info("Explore chests: scattered " + placed.size() + (placed.isEmpty() ? "" : " " + placed)
                + (skipped.isEmpty() ? "" : " · skipped " + skipped + " (ground changed or occupied)"));
    }

    /** The spot's feet block if the ground is still as surveyed (±1 for small edits since), else null. */
    private static Block ground(World world, Spot spot) {
        for (int dy : new int[] {0, 1, -1}) {
            Block at = world.getBlockAt(spot.x(), spot.y() + dy, spot.z());
            if (fits(at)) {
                return at;
            }
        }
        return null;
    }

    private static boolean fits(Block at) {
        Material type = at.getType();
        boolean free = type.isAir() || (at.isReplaceable() && !at.isLiquid() && !(at.getBlockData() instanceof Bisected));
        if (!free) {
            return false;
        }
        Block below = at.getRelative(BlockFace.DOWN);
        Block above = at.getRelative(BlockFace.UP);
        return below.getType().isSolid() && below.getType().isOccluding() && above.getType().isAir();
    }

    private static boolean dungeonBackend() {
        org.bukkit.plugin.Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        return dungeons != null && dungeons.isEnabled()
                && "dungeon".equalsIgnoreCase(dungeons.getConfig().getString("role", "hub"));
    }

    private static boolean originMap(World world) {
        int match = 0;
        for (int i = 0; i < FINGERPRINT.length; i++) {
            int[] p = FINGERPRINT[i];
            if (world.getBlockAt(p[0], p[1], p[2]).getType() == FINGERPRINT_TYPES[i]) {
                match++;
            }
        }
        return match >= 2;
    }
}
