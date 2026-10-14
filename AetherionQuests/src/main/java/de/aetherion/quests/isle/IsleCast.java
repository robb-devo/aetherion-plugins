package de.aetherion.quests.isle;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.data.NPCDataStorage;
import de.aetherion.quests.npc.QuestNPCRegistry;
import de.aetherion.quests.service.QuestNPCSpawnService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Origin island's wider cast, seeded once.
 * <p>
 * Every NPC below exists in the registry (voice, look, quest or flavour) but had no standing spot on
 * the 1000×1000 Origin map. Each seat was picked on the real terrain: standable, reachable from the
 * Capital plaza, flat 3×3, 5+ blocks from any other NPC, off the tutorial trail and outside the pier
 * cage. A seat is used only if the NPC has no saved placement yet, and only once — remove an NPC
 * afterwards and it stays removed ({@code island-cast.yml} remembers). Moving one with an anchor wins
 * over the seat, as always.
 * <p>
 * {@code /questnpc seed} (status) · {@code /questnpc seed run} · {@code /questnpc seed undo}
 */
public final class IsleCast {

    public record Seat(String npcId, double x, double y, double z, float yaw, String group) {
    }

    private static Seat seat(String id, double x, double y, double z, float yaw, String group) {
        return new Seat(id, x, y, z, yaw, group);
    }

    /** Curated standing spots (feet block, yaw). Groups are only labels for /questnpc seed. */
    public static final List<Seat> SEATS = List.of(
            // Anker Harbour — workers at their stations, all outside the pier cage. Nobody stands in the
            // spawn → Egon → market sightline: Egon is the only "talk to me first" on the arrival pier.
            // Quill (tallyman) and Haul work the cargo crane at the pier head together, 4 blocks apart on
            // purpose: one crew, one beat at the end of the pier (Hub origin.yml → bay.cargo-crane).
            seat("harbourmaster", 323.5, 63, -383.5, 45f, "harbour"),
            seat("crane_hand", 327.5, 63, -383.5, -143f, "harbour"),
            seat("lighthouse_keeper", 266.5, 63, -461.5, -90f, "harbour"),
            seat("shanty_busker", 236.5, 64, -366.5, -139f, "harbour"),
            seat("lamp_lighter", 262.5, 63, -386.5, 0f, "harbour"),
            seat("town_crier", 187.5, 64, -432.5, 162f, "harbour"),
            seat("street_sweeper", 210.5, 64, -404.5, -90f, "harbour"),
            seat("stall_crumb", 238.5, 64, -399.5, 0f, "harbour"),
            seat("stall_tack", 202.5, 64, -462.5, 0f, "harbour"),
            seat("stall_brine", 252.5, 63, -450.5, 0f, "harbour"),
            seat("dock_whisper", 254.5, 64, -462.5, -177f, "harbour"),
            seat("bait_theory", 260.5, 63, -426.5, -171f, "harbour"),
            seat("dock_scaler", 266.5, 63, -365.5, -124f, "harbour"),
            seat("fry_gossip", 206.5, 64, -359.5, 135f, "harbour"),
            seat("rivet", 287.5, 62, -394.5, -90f, "harbour"),
            seat("dockhand", 278.5, 63, -427.5, -95f, "harbour"),
            // The Capital
            seat("bench_cynic", 14.5, 58, -80.5, 74f, "capital"),
            seat("dust", -29.5, 58, -19.5, -90f, "capital"),
            seat("rook", -151.5, 58, 27.5, 135f, "capital"),
            seat("larder", 15.5, 58, 108.5, 58f, "capital"),
            seat("lost_tourist", 9.5, 58, -67.5, 132f, "capital"),
            seat("bookie", 6.5, 58, 79.5, 114f, "capital"),
            seat("painter", 64.5, 58, -48.5, -31f, "capital"),
            // The wider island
            seat("quarry_broker", 40.5, 57, 174.5, 59f, "southwood"),
            seat("pet_scout", -240.5, 59, 147.5, -79f, "clucksworth"),
            seat("chicken_keeper", -261.5, 61, 170.5, -52f, "clucksworth"),
            seat("sphere_proctor", -329.5, 62, -131.5, 72f, "colosseum"),
            seat("ore_ledger", 125.5, 103, -295.5, -49f, "ridge"),
            seat("slag_poet", 80.5, 67, -351.5, 36f, "ridge"),
            seat("timber_clerk", 338.5, 65, -317.5, -126f, "eastwood"),
            seat("mushroom_forager", 353.5, 67, -354.5, -141f, "eastwood"),
            seat("beekeeper", 290.5, 61, -44.5, -17f, "eastwood"),
            seat("gate_warden", 168.5, 58, 87.5, 153f, "borderlands"),
            seat("ash_scout", 141.5, 62, 118.5, -52f, "borderlands"),
            seat("tollkeeper", -261.5, 56, -258.5, 106f, "whisperwood"),
            seat("hermit", -409.5, 58, -238.5, 121f, "whisperwood")
    );

    /** Three blocks of the harbour boardwalk: only the Origin map gets seeded (never a dungeon or test world). */
    private static final int[][] FINGERPRINT = {{316, 62, -382}, {318, 62, -382}, {322, 62, -382}};
    private static final Material[] FINGERPRINT_TYPES = {Material.COARSE_DIRT, Material.JUNGLE_PLANKS, Material.GRANITE};

    private final AetherionQuests plugin;
    private final File file;
    private final YamlConfiguration state;
    private final Set<String> seeded = new LinkedHashSet<>();
    private boolean running;

    public IsleCast(AetherionQuests plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "island-cast.yml");
        this.state = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        seeded.addAll(state.getStringList("seeded"));
    }

    /** ~13 s after enable: after NPC restore (100) and the uniqueness sweep (200). */
    public void autoSeed() {
        if (!plugin.getConfig().getBoolean("island-cast.auto-place", true)) {
            return;
        }
        String result = seed(false);
        if (result != null && !result.isBlank()) {
            plugin.getLogger().info("Island cast: " + result);
        }
    }

    private World world() {
        return Bukkit.getWorld(plugin.getConfig().getString("island-cast.world", "world"));
    }

    private static boolean dungeonBackend() {
        org.bukkit.plugin.Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        return dungeons != null && dungeons.isEnabled()
                && "dungeon".equalsIgnoreCase(dungeons.getConfig().getString("role", "hub"));
    }

    private boolean originMap(World world) {
        int match = 0;
        for (int i = 0; i < FINGERPRINT.length; i++) {
            int[] p = FINGERPRINT[i];
            if (world.getBlockAt(p[0], p[1], p[2]).getType() == FINGERPRINT_TYPES[i]) {
                match++;
            }
        }
        return match >= 2;
    }

    /**
     * Places every seat whose NPC has no saved spot and was never seeded before. Spawns are spread
     * over ticks (chunks load async first) so a cold start never stalls.
     *
     * @param retrySeeded also re-place NPCs seeded before but missing now (DEV "seed run all")
     */
    public String seed(boolean retrySeeded) {
        if (running) {
            return "§7Seeding already running.";
        }
        World world = world();
        NPCDataStorage storage = plugin.getNpcDataStorage();
        if (world == null || storage == null || dungeonBackend()) {
            return "";
        }
        if (!originMap(world)) {
            return "§7Not the Origin map here — nothing seeded.";
        }
        relocateMoved(world, storage);
        List<Seat> todo = new ArrayList<>();
        for (Seat seat : SEATS) {
            if (QuestNPCRegistry.getNPC(seat.npcId()) == null || storage.hasSavedNPC(seat.npcId())) {
                continue;
            }
            if (seeded.contains(seat.npcId()) && !retrySeeded) {
                continue;
            }
            todo.add(seat);
        }
        if (todo.isEmpty()) {
            return "";
        }
        running = true;
        List<String> placed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int[] left = {todo.size()};
        for (int i = 0; i < todo.size(); i++) {
            Seat seat = todo.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> world.getChunkAtAsync(((int) Math.floor(seat.x())) >> 4,
                    ((int) Math.floor(seat.z())) >> 4).thenAccept(chunk -> {
                        try {
                            if (place(world, seat)) {
                                placed.add(seat.npcId());
                            } else {
                                skipped.add(seat.npcId());
                            }
                        } catch (Throwable throwable) {
                            skipped.add(seat.npcId());
                            plugin.getLogger().warning("Island cast: " + seat.npcId() + " failed: " + throwable);
                        }
                        if (--left[0] == 0) {
                            finish(placed, skipped);
                        }
                    }), 6L * i);
        }
        return "§aSeeding §f" + todo.size() + "§a NPC(s) over the next " + Math.max(1, todo.size() * 6 / 20) + "s…";
    }

    /** Seats that moved in a later pass: an NPC still standing on an old seat walks over to the new one. */
    private static final Object[][] MOVED = {
            {"crane_hand", 324.5, 63.0, -383.5},
            // harbour elevate: the crane went to the pier head, and Quill off Egon's back to tally beside it
            {"crane_hand", 314.5, 63.0, -394.5},
            {"harbourmaster", 287.5, 63.0, -380.5}
    };

    private void relocateMoved(World world, NPCDataStorage storage) {
        for (Object[] m : MOVED) {
            String id = (String) m[0];
            Location saved = storage.getSavedLocation(id);
            if (saved == null || saved.getWorld() == null || !saved.getWorld().equals(world)
                    || Math.abs(saved.getX() - (double) m[1]) > 2.0d || Math.abs(saved.getZ() - (double) m[3]) > 2.0d) {
                continue;
            }
            for (Seat seat : SEATS) {
                if (!seat.npcId().equals(id)) {
                    continue;
                }
                // Chunk first (async), and only onto ground that is still standable on the live map.
                world.getChunkAtAsync(((int) Math.floor(seat.x())) >> 4, ((int) Math.floor(seat.z())) >> 4).thenAccept(chunk -> {
                    double y = groundNear(world, seat);
                    if (Double.isNaN(y)) {
                        plugin.getLogger().warning("Island cast: " + id + " stays on its old seat — the new one at "
                                + (int) Math.floor(seat.x()) + " " + (int) Math.floor(seat.y()) + " " + (int) Math.floor(seat.z())
                                + " isn't standable any more.");
                        return;
                    }
                    new QuestNPCSpawnService().spawnNPC(id, new Location(world, seat.x(), y, seat.z(), seat.yaw(), 0.0f));
                    plugin.getLogger().info("Island cast: " + id + " moved to its new seat.");
                });
            }
        }
    }

    private void finish(List<String> placed, List<String> skipped) {
        running = false;
        save();
        plugin.getLogger().info("Island cast: placed " + placed.size() + (placed.isEmpty() ? "" : " " + placed)
                + (skipped.isEmpty() ? "" : " · skipped " + skipped + " (ground changed or occupied)"));
    }

    private boolean place(World world, Seat seat) {
        NPCDataStorage storage = plugin.getNpcDataStorage();
        if (storage == null || storage.hasSavedNPC(seat.npcId())) {
            return false;
        }
        double y = groundNear(world, seat);
        if (Double.isNaN(y) || crowded(seat)) {
            return false;
        }
        Location at = new Location(world, seat.x(), y, seat.z(), seat.yaw(), 0.0f);
        boolean ok = new QuestNPCSpawnService().spawnNPC(seat.npcId(), at) != null;
        if (ok) {
            seeded.add(seat.npcId());
        }
        return ok;
    }

    /** The seat's feet level if the ground is still there (±1 for small edits since the survey), else NaN. */
    private static double groundNear(World world, Seat seat) {
        int bx = (int) Math.floor(seat.x());
        int bz = (int) Math.floor(seat.z());
        int by = (int) Math.floor(seat.y());
        for (int dy : new int[]{0, 1, -1}) {
            Block feet = world.getBlockAt(bx, by + dy, bz);
            Block head = feet.getRelative(0, 1, 0);
            Block floor = feet.getRelative(0, -1, 0);
            if (feet.isPassable() && head.isPassable() && !feet.isLiquid() && floor.getType().isSolid()) {
                return by + dy;
            }
        }
        return Double.NaN;
    }

    /** Another saved NPC within 3 blocks of the seat (someone placed by hand in the meantime). */
    private boolean crowded(Seat seat) {
        NPCDataStorage storage = plugin.getNpcDataStorage();
        for (String id : QuestNPCRegistry.getAll().keySet()) {
            if (id.equalsIgnoreCase(seat.npcId()) || !storage.hasSavedNPC(id)) {
                continue;
            }
            Location other = storage.getSavedLocation(id);
            if (other != null && other.getWorld() != null && other.getWorld().getName().equals(plugin.getConfig().getString("island-cast.world", "world"))
                    && Math.abs(other.getX() - seat.x()) < 3.0d && Math.abs(other.getZ() - seat.z()) < 3.0d
                    && Math.abs(other.getY() - seat.y()) < 4.0d) {
                return true;
            }
        }
        return false;
    }

    /** Forgets every NPC this class placed (entity + saved spot). Seeds stay remembered: auto-seed won't re-add them. */
    public String undo() {
        int n = 0;
        QuestNPCSpawnService spawn = new QuestNPCSpawnService();
        for (String id : new ArrayList<>(seeded)) {
            if (spawn.forget(id)) {
                n++;
            }
        }
        save();
        return "§eRemoved §f" + n + "§e seeded NPC(s). §7They stay off until §f/questnpc seed run all§7.";
    }

    public String status() {
        NPCDataStorage storage = plugin.getNpcDataStorage();
        int standing = 0;
        List<String> waiting = new ArrayList<>();
        for (Seat seat : SEATS) {
            boolean saved = storage != null && storage.hasSavedNPC(seat.npcId());
            if (saved) {
                standing++;
            } else if (!seeded.contains(seat.npcId())) {
                waiting.add(seat.npcId());
            }
        }
        return "§6Island cast §8· §7" + SEATS.size() + " seats · §a" + standing + " standing§7 · " + seeded.size() + " seeded by us"
                + (waiting.isEmpty() ? "" : " · §ewaiting: §f" + String.join(", ", waiting))
                + " §8· §e/questnpc seed <run [all]|undo|list>";
    }

    public String list() {
        StringBuilder out = new StringBuilder("§6Island cast seats:");
        String group = "";
        for (Seat seat : SEATS) {
            if (!seat.group().equals(group)) {
                group = seat.group();
                out.append("\n§e").append(group.toUpperCase(Locale.ROOT)).append(" §8·");
            }
            out.append(" §f").append(seat.npcId()).append("§8@§7").append((int) seat.x()).append(",").append((int) seat.y())
                    .append(",").append((int) seat.z());
        }
        return out.toString();
    }

    private void save() {
        state.set("seeded", new ArrayList<>(seeded));
        try {
            state.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("island-cast.yml: " + exception.getMessage());
        }
    }
}
