package de.aetherion.guilds.island;

import de.aetherion.core.persist.AtomicYaml;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * The factory ladder after the first chain: one clear "next" at a time, each a small step that makes the line
 * longer, wider or smarter (a mill in the line, a second quarry, a split, a sorter, an overflow...). Every goal
 * is read straight from the island (do it in any order; the bar always shows the first one not yet done), and
 * finishing one gives a short beat and a few coins. Shown on the island bar, the Build and Production pages and
 * by Hollis ("What's next?").
 */
public final class FactoryGoals {

    public record Goal(String id, String title, String how, long reward, BiPredicate<FactoryGoals, IslandHost> done) {
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final Map<String, Set<String>> rewarded = new HashMap<>();
    private boolean dirty;

    /*
     * The first five are the guided line Hollis walks (quarry → Hub → Mill → Forge → Compacted Cobble in the hut);
     * the island bar, /island, the Hub, Production and Hollis all show the first one not done, so they always agree.
     */
    private final List<Goal> goals = List.of(
            new Goal("first_chain", "First chain", "Belt quarry chute → Storage Hut",
                    2_000L, (g, h) -> g.sourceLines(h) >= 1),
            new Goal("hub", "Found your Hub", "Build Tool → Hub (unlocks machines)",
                    3_000L, (g, h) -> g.structures.hasWorkshop(h)),
            new Goal("mill_line", "Mill in the line", "Unlock → build → belt quarry → Mill → hut",
                    5_000L, (g, h) -> g.inLine(h, StructureType.MILL)),
            new Goal("forge_line", "Forge in the line", "Unlock → build → belt Mill → Forge → hut",
                    8_000L, (g, h) -> g.inLine(h, StructureType.FORGE)),
            new Goal("compacted", "Compacted Cobble in storage", "Quarry → Mill → Forge → hut",
                    12_000L, (g, h) -> g.compactedInStorage(h) > 0L),
            new Goal("two_quarries", "Run two quarries into your line", "Set down a second quarry and belt it into the same line.",
                    8_000L, (g, h) -> g.sourceLines(h) >= 2),
            new Goal("split", "Split a line with a Splitter", "Unlock it at the Hub. Build Tool → Splitter onto a belt, then a second belt away from its side.",
                    10_000L, (g, h) -> g.pieceExits(h, StructureType.SPLITTER) >= 2),
            new Goal("loft", "Grow your Storage Hut into a Loft", "Right-click the hut → Upgrade.",
                    15_000L, (g, h) -> g.level(h, StructureType.STORAGE_HUT) >= 2),
            new Goal("hub2", "Raise your Hub to the Foreman's Hall", "Hub lectern → Hub tier. Then the Overflow Gate and Sorter can be unlocked.",
                    18_000L, (g, h) -> g.structures.hubLevel(h) >= 2),
            new Goal("sort", "Sort goods with a Sorter", "Set a Sorter into a line, pick a good, give it a front and a side belt.",
                    20_000L, (g, h) -> g.sorterWorks(h)),
            new Goal("overflow", "Catch the spill with an Overflow Gate", "Front belt to one hut, side belt to another: the side fills when the front is full.",
                    25_000L, (g, h) -> g.pieceExits(h, StructureType.OVERFLOW) >= 2),
            new Goal("long_line", "Build a line through 4 machines", "Quarry → piece → Mill → Forge → hut, or any line four stops long.",
                    60_000L, (g, h) -> g.longestLine(h) >= 4),
            new Goal("tier3", "Raise your Island Tier to 3", "Island menu → Tier. More belts and one more of every machine.",
                    80_000L, (g, h) -> g.hosts.tier(h) >= 3),
            new Goal("hub3", "Raise the Grand Hub", "Hub lectern → Hub tier. Every Mill and Forge runs twice as fast.",
                    100_000L, (g, h) -> g.structures.hubLevel(h) >= 3),
            new Goal("warehouse", "Grow a Warehouse", "Right-click the Storage Loft → Upgrade.",
                    120_000L, (g, h) -> g.level(h, StructureType.STORAGE_HUT) >= 3));

    /** Goals whose moment someone else stages with its own title (the first chain and Hub, Hollis' finale). */
    private BiPredicate<IslandHost, String> titleOwned = (host, id) -> false;

    public void attachTitleOwner(BiPredicate<IslandHost, String> titleOwned) {
        this.titleOwned = titleOwned == null ? (host, id) -> false : titleOwned;
    }

    private final IslandFiles files;

    public FactoryGoals(JavaPlugin plugin, HostService hosts, StructureService structures, LogisticsService logistics,
                        IslandFiles files) {
        this.plugin = plugin;
        this.files = files;
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
        load();
    }

    public List<Goal> all() {
        return goals;
    }

    /** The first goal not done yet, or null when the ladder is climbed. */
    public Goal next(IslandHost host) {
        for (Goal goal : goals) {
            if (!goal.done().test(this, host)) {
                return goal;
            }
        }
        return null;
    }

    public int doneCount(IslandHost host) {
        int n = 0;
        for (Goal goal : goals) {
            if (goal.done().test(this, host)) {
                n++;
            }
        }
        return n;
    }

    /** Every few seconds for a member on their island: newly done goals get their beat (once per island). */
    public void check(Player player, IslandHost host) {
        Set<String> got = rewarded.get(host.key());
        if (got == null) {
            got = new HashSet<>();
            rewarded.put(host.key(), got);
            dirty = true;
            if (doneCount(host) > 2) {
                // an island that was already running before the ladder existed: no burst of old beats
                for (Goal goal : goals) {
                    if (goal.done().test(this, host)) {
                        got.add(goal.id());
                    }
                }
                Goal next = next(host);
                if (next != null) {
                    player.sendMessage("§b➤ Factory goals are here. §7Next: §f" + next.title() + " §8— §7" + next.how());
                }
                return;
            }
        }
        for (Goal goal : goals) {
            if (got.contains(goal.id()) || !goal.done().test(this, host)) {
                continue;
            }
            got.add(goal.id());
            dirty = true;
            hosts.refund(player, host, goal.reward());
            Goal next = next(host);
            if (!goal.id().equals("first_chain") && !goal.id().equals("hub") && !titleOwned.test(host, goal.id())) {
                // (the first chain gets Hollis' own title, the Hub its ritual, the tour's finale its own)
                player.sendTitle("§b✦ " + goal.title() + " §b✦", next == null ? "§7Every factory goal done."
                        : "§7Next: §f" + next.title(), 8, 60, 16);
            }
            player.sendMessage("§b✦ Goal done: §f" + goal.title() + " §8(§6+" + GuildFormat.compact(goal.reward())
                    + " " + hosts.fundsLabel(host) + "§8)");
            if (next != null) {
                player.sendMessage("§7Next: §f" + next.title() + " §8— §7" + next.how());
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 24, 0.6, 0.8, 0.6, 0.06);
            return; // one beat at a time
        }
    }

    // ------------------------------------------------------------------------------------------------
    // reading the island
    // ------------------------------------------------------------------------------------------------

    int sourceLines(IslandHost host) {
        int n = 0;
        for (LogisticsService.Route route : logistics.routes(host)) {
            if (route.to() != null && route.from().type() == StructureType.QUARRY_HOUSING) {
                n++;
            }
        }
        return n;
    }

    /** Compacted goods (any quarry type) sitting in the island's Storage Huts and Depots. */
    long compactedInStorage(IslandHost host) {
        long have = 0L;
        for (QuarryType type : QuarryType.values()) {
            have += structures.stock(host, Res.of(type, Res.Form.COMPACTED));
        }
        return have;
    }

    /** Is this goal done on the island right now (by id; false for an unknown id)? */
    public boolean done(IslandHost host, String id) {
        for (Goal goal : goals) {
            if (goal.id().equals(id)) {
                return goal.done().test(this, host);
            }
        }
        return false;
    }

    /** A machine of this type that something feeds and whose output reaches another machine. */
    boolean inLine(IslandHost host, StructureType type) {
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() != type || logistics.feeding(structure) == 0) {
                continue;
            }
            LogisticsService.Route out = logistics.routeFrom(structure);
            if (out != null && out.to() != null) {
                return true;
            }
        }
        return false;
    }

    int pieceExits(IslandHost host, StructureType type) {
        int best = 0;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() != type || logistics.feeding(structure) == 0) {
                continue;
            }
            int live = 0;
            for (LogisticsService.Route route : logistics.exits(structure)) {
                if (route.to() != null) {
                    live++;
                }
            }
            best = Math.max(best, live);
        }
        return best;
    }

    boolean sorterWorks(IslandHost host) {
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() != StructureType.SORTER || structure.filter() == null || logistics.feeding(structure) == 0) {
                continue;
            }
            boolean front = false;
            boolean side = false;
            for (LogisticsService.Route route : logistics.exits(structure)) {
                if (route.to() != null) {
                    front |= route.front();
                    side |= !route.front();
                }
            }
            if (front && side) {
                return true;
            }
        }
        return false;
    }

    int level(IslandHost host, StructureType type) {
        int best = 0;
        for (PlacedStructure structure : structures.of(host)) {
            if (structure.type() == type) {
                best = Math.max(best, structure.level());
            }
        }
        return best;
    }

    /** Most machines one quarry's goods pass through on the way (quarry and final store not counted twice). */
    int longestLine(IslandHost host) {
        int best = 0;
        for (LogisticsService.Route route : logistics.routes(host)) {
            if (route.from().type() == StructureType.QUARRY_HOUSING && route.to() != null) {
                best = Math.max(best, 1 + depth(route.to(), new HashSet<>()));
            }
        }
        return best;
    }

    private int depth(PlacedStructure node, Set<UUID> seen) {
        if (!seen.add(node.id()) || seen.size() > 32) {
            return 0;
        }
        int best = 0;
        for (LogisticsService.Route route : logistics.exits(node)) {
            if (route.to() != null) {
                best = Math.max(best, depth(route.to(), seen));
            }
        }
        seen.remove(node.id());
        return 1 + best;
    }

    // ------------------------------------------------------------------------------------------------
    // persistence (which beats were given, so rebuilding a line doesn't pay twice)
    // ------------------------------------------------------------------------------------------------

    public void forget(IslandHost host) {
        if (rewarded.remove(host.key()) != null) {
            dirty = true;
        }
    }

    private void load() {
        if (files.splitting()) {
            YamlConfiguration old = files.legacy("island_goals.yml");
            if (old != null) {
                for (String key : old.getKeys(false)) {
                    rewarded.put(key, upgrade(new HashSet<>(old.getStringList(key))));
                }
            }
            dirty = true;
            saveIfDirty();
            return;
        }
        for (String key : files.keys()) {
            YamlConfiguration doc = files.doc(key);
            if (doc.isList("goals.rewarded")) {
                rewarded.put(key, upgrade(new HashSet<>(doc.getStringList("goals.rewarded"))));
            }
        }
    }

    private static Set<String> upgrade(Set<String> got) {
        if (got.contains("workshop")) {
            got.add("hub"); // renamed goal, already paid
        }
        return got;
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        for (Map.Entry<String, Set<String>> entry : rewarded.entrySet()) {
            files.replace(entry.getKey(), "goals").set("rewarded", List.copyOf(entry.getValue()));
        }
        files.flush();
        dirty = false;
    }
}
