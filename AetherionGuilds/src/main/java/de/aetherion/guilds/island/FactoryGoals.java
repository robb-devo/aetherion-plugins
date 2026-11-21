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

    /** Where a goal applies: both kinds of island, only personal ones (the stage ladder) or only guild ones. */
    public enum Scope {
        BOTH,
        PERSONAL,
        GUILD
    }

    public record Goal(String id, String title, String how, long reward, BiPredicate<FactoryGoals, IslandHost> done,
                       Scope scope) {
        public Goal(String id, String title, String how, long reward, BiPredicate<FactoryGoals, IslandHost> done) {
            this(id, title, how, reward, done, Scope.BOTH);
        }

        public boolean applies(IslandHost host) {
            return switch (scope) {
                case PERSONAL -> host != null && !host.isGuild();
                case GUILD -> host != null && host.isGuild();
                default -> true;
            };
        }
    }

    private final JavaPlugin plugin;
    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private final Map<String, Set<String>> rewarded = new HashMap<>();
    private boolean dirty;
    /** Personal islands: their stage (0..3) and how often they pressed by hand (DockService). */
    private java.util.function.ToIntFunction<IslandHost> stage = host -> 0;
    private java.util.function.ToIntFunction<IslandHost> pressed = host -> 0;

    public void attachStages(java.util.function.ToIntFunction<IslandHost> stage,
                             java.util.function.ToIntFunction<IslandHost> pressed) {
        if (stage != null) {
            this.stage = stage;
        }
        if (pressed != null) {
            this.pressed = pressed;
        }
    }

    int stage(IslandHost host) {
        return host == null || host.isGuild() ? -1 : stage.applyAsInt(host);
    }

    /*
     * Personal islands climb the stage ladder: the tour (Hub, first chain, the hand press, the Mill in the line,
     * the Dock, the first ship) is stage 0; then each stage's goals, its phase contract last. Guild islands keep
     * the Hub-tier ladder. The island bar, /island, the Hub, Production and the guide all show the first one not
     * done, so they always agree.
     */
    private final List<Goal> goals = List.of(
            new Goal("hub", "Found your Hub", "Build Tool → Hub, anywhere you like (unlocks machines)",
                    3_000L, (g, h) -> g.structures.hasWorkshop(h)),
            new Goal("first_chain", "First chain", "Belt quarry chute → Storage Hut",
                    2_000L, (g, h) -> g.sourceLines(h) >= 1),
            new Goal("press", "Press Compressed Cobble by hand", "Hub lectern → Hand Press (128 Cobblestone from storage)",
                    1_000L, (g, h) -> g.pressed.applyAsInt(h) > 0 || g.stage(h) >= 1
                    || g.structures.count(h, StructureType.MILL) > 0, Scope.PERSONAL),
            new Goal("mill_line", "Mill in the line", "Unlock → build → belt quarry → Mill → hut",
                    5_000L, (g, h) -> g.inLine(h, StructureType.MILL)),
            new Goal("dock", "Build your Dock", "Unlock it at the Hub (4 Compressed Cobble), then Build Tool → Dock",
                    3_000L, (g, h) -> g.structures.count(h, StructureType.DOCK) > 0 || g.stage(h) >= 1, Scope.PERSONAL),
            new Goal("first_ship", "Send your first ship", "Dock → First Shipment: 4 Compressed Cobble, then the airship",
                    1_000L, (g, h) -> g.stage(h) >= 1, Scope.PERSONAL),
            // stage 1, the Werkhof
            new Goal("two_quarries", "Run two quarries into your line", "Set down your second quarry and belt it into a line.",
                    8_000L, (g, h) -> g.sourceLines(h) >= 2),
            new Goal("split", "Split a line with a Splitter", "Unlock it at the Hub. Build Tool → Splitter onto a belt, then a second belt away from its side.",
                    10_000L, (g, h) -> g.pieceExits(h, StructureType.SPLITTER) >= 2),
            new Goal("loft", "Grow your Storage Hut into a Loft", "Hub lectern → Production → the hut → Upgrade.",
                    15_000L, (g, h) -> g.level(h, StructureType.STORAGE_HUT) >= 2),
            new Goal("hub2", "Raise your Hub to the Foreman's Hall", "Hub lectern → Hub tier. Then the Overflow Gate and Sorter can be unlocked.",
                    18_000L, (g, h) -> g.structures.hubLevel(h) >= 2, Scope.GUILD),
            new Goal("stage2", "Reach the Foreman's Hall", "Dock → Werkhof Order: Compressed Cobble and your trade's goods.",
                    18_000L, (g, h) -> g.stage(h) >= 2, Scope.PERSONAL),
            // stage 2, the Foreman's Hall
            new Goal("forge_line", "Forge in the line", "Unlock → build → belt Mill → Forge → hut",
                    8_000L, (g, h) -> g.inLine(h, StructureType.FORGE)),
            new Goal("compacted", "Compacted Cobble in storage", "Quarry → Mill → Forge → hut",
                    12_000L, (g, h) -> g.compactedInStorage(h) > 0L),
            new Goal("sort", "Sort goods with a Sorter", "Set a Sorter into a line, pick a good, give it a front and a side belt.",
                    20_000L, (g, h) -> g.sorterWorks(h)),
            new Goal("overflow", "Catch the spill with an Overflow Gate", "Front belt to one hut, side belt to another: the side fills when the front is full.",
                    25_000L, (g, h) -> g.pieceExits(h, StructureType.OVERFLOW) >= 2),
            new Goal("warehouse", "Grow a Warehouse", "Upgrade the Storage Loft once more.",
                    40_000L, (g, h) -> g.level(h, StructureType.STORAGE_HUT) >= 3),
            new Goal("stage3", "Reach the Grand Hub", "Dock → Foreman's Order: Compacted Cobble and your trade's goods.",
                    60_000L, (g, h) -> g.stage(h) >= 3, Scope.PERSONAL),
            // stage 3, the Grand Hub
            new Goal("long_line", "Build a line through 4 machines", "Quarry → piece → Mill → Forge → hut, or any line four stops long.",
                    60_000L, (g, h) -> g.longestLine(h) >= 4),
            new Goal("land", "Buy a parcel of land", "Island menu → Land: new ground next to yours.",
                    40_000L, (g, h) -> g.hosts.parcels(h).size() > LandService.initialParcels().size(), Scope.PERSONAL),
            new Goal("tier3", "Raise your Island Tier to 3", "Guild menu → Tier. More belts and one more of every machine.",
                    80_000L, (g, h) -> g.hosts.tier(h) >= 3, Scope.GUILD),
            new Goal("hub3", "Raise the Grand Hub", "Hub lectern → Hub tier. Every Mill and Forge runs twice as fast.",
                    100_000L, (g, h) -> g.structures.hubLevel(h) >= 3, Scope.GUILD));

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
            if (goal.applies(host) && !goal.done().test(this, host)) {
                return goal;
            }
        }
        return null;
    }

    public int doneCount(IslandHost host) {
        int n = 0;
        for (Goal goal : goals) {
            if (goal.applies(host) && goal.done().test(this, host)) {
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
                    if (goal.applies(host) && goal.done().test(this, host)) {
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
            if (!goal.applies(host) || got.contains(goal.id()) || !goal.done().test(this, host)) {
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
            de.aetherion.core.api.Vfx.puff(player.getLocation().add(0, 1.2, 0), Particle.END_ROD, 24, Math.max(0.6, 0.6), null);
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
