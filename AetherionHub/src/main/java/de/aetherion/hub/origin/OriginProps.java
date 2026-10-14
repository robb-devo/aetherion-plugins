package de.aetherion.hub.origin;

import de.aetherion.hub.island.FaweIslandPaste;
import de.aetherion.hub.prop.PropCatalog;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The prop paste layer: the Hub's own prop schematics ({@link PropCatalog}) at pre-checked spots on Origin
 * ({@code origin-explore.yml → props}). Every spot was fitted against the map: nothing above the ground layer
 * collides, the ground layer sits on solid ground, roads are left alone, and you can walk up to it.
 *
 * <p>Before a paste the whole footprint is snapshotted to {@code origin-props/<spot>.yml}; {@code undo} puts back
 * exactly those blocks (newest first, so overlapping spots unwind cleanly). Pastes run through FAWE off the main
 * thread, one at a time. Includes the Fallen Anchor camp for Hesper Kael — the camp is ready; Hesper herself stays a
 * manual {@code /questnpc story} placement on the marked seat.
 */
public final class OriginProps {

    public record Spot(String id, String prop, int[] at, int rotate, String group, String name, String note, double[] view) {
    }

    private static final String STATE_FILE = "origin-props.yml";
    private static final int RESTORE_PER_TICK = 4000;

    private final OriginIsle isle;
    private final Map<String, Spot> spots = new LinkedHashMap<>();
    private final File stateFile;
    private final File snapFolder;
    private YamlConfiguration state = new YamlConfiguration();
    private double[] hesperSeat;
    private volatile boolean working;

    OriginProps(OriginIsle isle) {
        this.isle = isle;
        this.stateFile = new File(isle.plugin().getDataFolder(), STATE_FILE);
        this.snapFolder = new File(isle.plugin().getDataFolder(), "origin-props");
    }

    int load() {
        spots.clear();
        state = stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
        YamlConfiguration explore = isle.config().explore();
        hesperSeat = OriginConfig.nums(explore.getString("prop-settings.hesper-seat"));
        ConfigurationSection ps = explore.getConfigurationSection("props");
        if (ps == null) {
            return 0;
        }
        for (String id : ps.getKeys(false)) {
            ConfigurationSection s = ps.getConfigurationSection(id);
            int[] at = s == null ? null : OriginConfig.ints(s.getString("at"));
            String prop = s == null ? null : s.getString("prop");
            if (at == null || prop == null || PropCatalog.get(prop) == null) {
                continue;
            }
            int rot = Math.floorMod(s.getInt("rotate", 0), 360) / 90 * 90;
            spots.put(id.toLowerCase(Locale.ROOT), new Spot(id.toLowerCase(Locale.ROOT), prop, at, rot, s.getString("group", id),
                    s.getString("name", PropCatalog.get(prop).display()), s.getString("note", ""), OriginConfig.nums(s.getString("view"))));
        }
        return spots.size();
    }

    public Map<String, Spot> all() {
        return spots;
    }

    public boolean pasted(String id) {
        return id != null && state.isConfigurationSection("pasted." + id.toLowerCase(Locale.ROOT));
    }

    /** The prop group's spots are all pasted (used by curiosities that need a prop, e.g. the Fallen Anchor camp). */
    public boolean groupPasted(String group) {
        boolean any = false;
        for (Spot spot : spots.values()) {
            if (spot.group().equalsIgnoreCase(group)) {
                any = true;
                if (!pasted(spot.id())) {
                    return false;
                }
            }
        }
        return any;
    }

    public int pastedCount() {
        ConfigurationSection p = state.getConfigurationSection("pasted");
        return p == null ? 0 : p.getKeys(false).size();
    }

    public boolean busy() {
        return working;
    }

    public double[] hesperSeat() {
        return hesperSeat;
    }

    private List<Spot> resolve(String target, boolean wantPasted) {
        List<Spot> out = new ArrayList<>();
        String t = target == null ? "" : target.toLowerCase(Locale.ROOT);
        for (Spot spot : spots.values()) {
            boolean match = t.equals("all") || spot.id().equals(t) || spot.group().equalsIgnoreCase(t);
            if (match && pasted(spot.id()) == wantPasted) {
                out.add(spot);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ paste

    String paste(CommandSender sender, String target) {
        if (!FaweIslandPaste.fawePresent()) {
            return "§cFastAsyncWorldEdit is required for prop pastes.";
        }
        if (working) {
            return "§cA prop paste or undo is already running.";
        }
        World world = isle.config().world();
        if (world == null) {
            return "§cWorld not loaded.";
        }
        List<Spot> todo = resolve(target, false);
        if (todo.isEmpty()) {
            return spots.containsKey(target) || target.equals("all") || isGroup(target)
                    ? "§7Nothing to paste: §f" + target + " §7is already down. §8(/origin dev props undo " + target + ")"
                    : "§7Unknown spot. §8" + String.join(", ", groups());
        }
        PropCatalog.ensureExtracted(isle.plugin());
        List<Object[]> jobs = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int order = state.getInt("order", 0);
        for (Spot spot : todo) {
            File schem = PropCatalog.resolve(isle.plugin(), spot.prop());
            if (schem == null || !schem.isFile()) {
                skipped.add(spot.id() + " (no " + spot.prop() + ".schem)");
                continue;
            }
            FaweIslandPaste.Footprint box;
            try {
                box = FaweIslandPaste.footprintFromSchem(schem, spot.at()[0], spot.at()[1], spot.at()[2], spot.rotate(), world.getName());
            } catch (Throwable throwable) {
                skipped.add(spot.id() + " (" + throwable.getMessage() + ")");
                continue;
            }
            int tiles = snapshot(world, spot, box);
            if (tiles < 0) {
                skipped.add(spot.id() + " (snapshot failed)");
                continue;
            }
            String root = "pasted." + spot.id();
            state.set(root + ".prop", spot.prop());
            state.set(root + ".at", spot.at()[0] + " " + spot.at()[1] + " " + spot.at()[2]);
            state.set(root + ".rotate", spot.rotate());
            state.set(root + ".order", ++order);
            state.set(root + ".time", System.currentTimeMillis());
            state.set(root + ".box", box.minX() + " " + box.minY() + " " + box.minZ() + " " + box.maxX() + " " + box.maxY() + " " + box.maxZ());
            jobs.add(new Object[]{spot, schem});
            if (tiles > 0) {
                sender.sendMessage("§8  " + spot.id() + ": " + tiles + " container/sign block(s) in the box — undo restores the block, not its contents.");
            }
        }
        state.set("order", order);
        save();
        if (jobs.isEmpty()) {
            return "§cNothing pasted. §8" + String.join(", ", skipped);
        }
        working = true;
        FaweIslandPaste.runAsync(isle.plugin(), () -> {
            try {
                for (Object[] job : jobs) {
                    Spot spot = (Spot) job[0];
                    long waitUntil = System.currentTimeMillis() + 30_000L;
                    while (!FaweIslandPaste.tryBegin()) {
                        if (System.currentTimeMillis() > waitUntil) {
                            tell(sender, "§cGave up waiting for another paste: " + spot.id());
                            return;
                        }
                        Thread.sleep(100L);
                    }
                    FaweIslandPaste.paste(isle.plugin(), sender, "origin_prop_" + spot.id(), (File) job[1], world,
                            spot.at()[0], spot.at()[1], spot.at()[2], true, spot.rotate());
                }
                tell(sender, "§aProps §8· §7pasted §f" + jobs.size() + "§7. §8/origin dev props undo " + target + " §7takes them back.");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Throwable throwable) {
                isle.plugin().getLogger().warning("Origin props paste: " + throwable);
                tell(sender, "§cProp paste failed: " + throwable.getMessage());
            } finally {
                working = false;
            }
        });
        return "§7Pasting §f" + jobs.size() + " §7prop(s)…" + (skipped.isEmpty() ? "" : " §8skipped: " + String.join(", ", skipped));
    }

    /** Palette + run-length snapshot of the footprint. Returns the number of tile blocks in it, or -1. */
    private int snapshot(World world, Spot spot, FaweIslandPaste.Footprint box) {
        Map<String, Integer> palette = new LinkedHashMap<>();
        StringBuilder data = new StringBuilder();
        int tiles = 0;
        int last = -1;
        int run = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    Block block = world.getBlockAt(x, y, z);
                    String s = block.getBlockData().getAsString();
                    int idx = palette.computeIfAbsent(s, k -> palette.size());
                    if (idx == last) {
                        run++;
                    } else {
                        if (run > 0) {
                            data.append(run).append('*').append(last).append(',');
                        }
                        last = idx;
                        run = 1;
                    }
                    if (block.getType().isAir()) {
                        continue;
                    }
                    BlockState bs = block.getState(false);
                    if (bs instanceof TileState) {
                        tiles++;
                    }
                }
            }
        }
        if (run > 0) {
            data.append(run).append('*').append(last);
        }
        YamlConfiguration snap = new YamlConfiguration();
        snap.set("world", world.getName());
        snap.set("box", List.of(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
        snap.set("palette", new ArrayList<>(palette.keySet()));
        snap.set("data", data.toString());
        try {
            if (!snapFolder.isDirectory()) {
                snapFolder.mkdirs();
            }
            snap.save(new File(snapFolder, spot.id() + ".yml"));
        } catch (IOException exception) {
            isle.plugin().getLogger().warning("Origin props: snapshot " + spot.id() + ": " + exception.getMessage());
            return -1;
        }
        return tiles;
    }

    // ------------------------------------------------------------------ undo

    String undo(CommandSender sender, String target) {
        if (working) {
            return "§cA prop paste or undo is already running.";
        }
        World world = isle.config().world();
        if (world == null) {
            return "§cWorld not loaded.";
        }
        List<Spot> todo;
        if ("last".equalsIgnoreCase(target)) {
            todo = new ArrayList<>();
            Spot newest = null;
            int best = -1;
            for (Spot spot : resolve("all", true)) {
                int order = state.getInt("pasted." + spot.id() + ".order", 0);
                if (order > best) {
                    best = order;
                    newest = spot;
                }
            }
            if (newest != null) {
                todo.add(newest);
            }
        } else {
            todo = resolve(target, true);
        }
        if (todo.isEmpty()) {
            return "§7Nothing to undo for §f" + target + "§7.";
        }
        todo.sort((a, b) -> Integer.compare(state.getInt("pasted." + b.id() + ".order", 0), state.getInt("pasted." + a.id() + ".order", 0)));
        working = true;
        List<Spot> queue = new ArrayList<>(todo);
        new BukkitRunnable() {
            private int spotIndex;
            private List<String> palette;
            private int[] box;
            private String[] runs;
            private int runIndex;
            private int runLeft;
            private int runValue;
            private int cursor;
            private int restored;
            private final Map<String, BlockData> parsed = new HashMap<>();

            @Override
            public void run() {
                try {
                    int budget = RESTORE_PER_TICK;
                    while (budget > 0) {
                        if (palette == null) {
                            if (spotIndex >= queue.size()) {
                                finish();
                                return;
                            }
                            if (!open(queue.get(spotIndex))) {
                                close(queue.get(spotIndex), false);
                                spotIndex++;
                                continue;
                            }
                        }
                        if (runLeft == 0) {
                            if (runIndex >= runs.length) {
                                close(queue.get(spotIndex), true);
                                spotIndex++;
                                continue;
                            }
                            String r = runs[runIndex++];
                            if (r.isEmpty()) {
                                continue;
                            }
                            int star = r.indexOf('*');
                            runLeft = Integer.parseInt(r.substring(0, star));
                            runValue = Integer.parseInt(r.substring(star + 1));
                        }
                        int sx = box[3] - box[0] + 1;
                        int sz = box[5] - box[2] + 1;
                        int x = box[0] + cursor % sx;
                        int z = box[2] + (cursor / sx) % sz;
                        int y = box[1] + cursor / (sx * sz);
                        Block block = world.getBlockAt(x, y, z);
                        BlockData want = parsed.computeIfAbsent(palette.get(runValue), Bukkit::createBlockData);
                        if (!block.getBlockData().matches(want)) {
                            block.setBlockData(want, false);
                            restored++;
                        }
                        cursor++;
                        runLeft--;
                        budget--;
                    }
                } catch (Throwable throwable) {
                    isle.plugin().getLogger().warning("Origin props undo: " + throwable);
                    sender.sendMessage("§cProp undo failed: " + throwable.getMessage());
                    working = false;
                    cancel();
                }
            }

            private boolean open(Spot spot) {
                File file = new File(snapFolder, spot.id() + ".yml");
                if (!file.isFile()) {
                    sender.sendMessage("§c" + spot.id() + ": no snapshot on disk — forgetting the paste record only.");
                    return false;
                }
                YamlConfiguration snap = YamlConfiguration.loadConfiguration(file);
                List<Integer> b = snap.getIntegerList("box");
                if (b.size() < 6) {
                    return false;
                }
                box = new int[]{b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5)};
                palette = snap.getStringList("palette");
                runs = snap.getString("data", "").split(",");
                runIndex = 0;
                runLeft = 0;
                cursor = 0;
                parsed.clear();
                return true;
            }

            private void close(Spot spot, boolean done) {
                state.set("pasted." + spot.id(), null);
                File file = new File(snapFolder, spot.id() + ".yml");
                if (done && file.isFile() && !file.delete()) {
                    isle.plugin().getLogger().warning("Origin props: could not delete " + file.getName());
                }
                palette = null;
                save();
            }

            private void finish() {
                working = false;
                cancel();
                sender.sendMessage("§eProps §8· §7undone §f" + queue.size() + " §7spot(s), §f" + restored + " §7block(s) restored.");
            }
        }.runTaskTimer(isle.plugin(), 1L, 1L);
        return "§7Undoing §f" + todo.size() + " §7prop(s)…";
    }

    // ------------------------------------------------------------------ DEV helpers

    String tp(Player player, String id) {
        Spot spot = spots.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        Location to;
        String label;
        if (spot == null && "hesper".equalsIgnoreCase(id) && hesperSeat != null) {
            to = isle.config().location(hesperSeat);
            label = "Hesper's seat";
        } else if (spot != null) {
            double[] v = spot.view() != null ? spot.view() : new double[]{spot.at()[0] + 0.5d, spot.at()[1] + 1.0d, spot.at()[2] + 9.5d, 180.0d, 15.0d};
            to = isle.config().location(v);
            label = spot.name();
        } else {
            return "§7/origin dev props tp <spot|hesper>";
        }
        if (to == null) {
            return "§cWorld not loaded.";
        }
        isle.traversal().grace(player, 5_000L);
        player.teleport(to);
        if (spot != null) {
            show(player, spot);
        }
        return "§aTP §8· §f" + label + (spot == null ? "" : " §8(" + (pasted(spot.id()) ? "§apasted" : "§7not pasted") + "§8)");
    }

    /** Outlines a spot's footprint for 12 seconds (only the DEV sees it). */
    void show(Player player, Spot spot) {
        File schem = PropCatalog.resolve(isle.plugin(), spot.prop());
        World world = isle.config().world();
        if (schem == null || !schem.isFile() || world == null || !FaweIslandPaste.fawePresent()) {
            return;
        }
        FaweIslandPaste.Footprint box;
        try {
            box = FaweIslandPaste.footprintFromSchem(schem, spot.at()[0], spot.at()[1], spot.at()[2], spot.rotate(), world.getName());
        } catch (Throwable throwable) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(120, 255, 160), 1.2f);
        new BukkitRunnable() {
            private int runs;

            @Override
            public void run() {
                if (!player.isOnline() || runs++ >= 12) {
                    cancel();
                    return;
                }
                int y = Math.max(box.minY(), spot.at()[1]) + 1;
                for (int x = box.minX(); x <= box.maxX() + 1; x++) {
                    player.spawnParticle(Particle.DUST, x, y, box.minZ(), 1, 0, 0, 0, 0, dust);
                    player.spawnParticle(Particle.DUST, x, y, box.maxZ() + 1, 1, 0, 0, 0, 0, dust);
                }
                for (int z = box.minZ(); z <= box.maxZ() + 1; z++) {
                    player.spawnParticle(Particle.DUST, box.minX(), y, z, 1, 0, 0, 0, 0, dust);
                    player.spawnParticle(Particle.DUST, box.maxX() + 1, y, z, 1, 0, 0, 0, 0, dust);
                }
                player.spawnParticle(Particle.END_ROD, spot.at()[0] + 0.5, spot.at()[1] + 1.2, spot.at()[2] + 0.5, 3, 0.1, 0.4, 0.1, 0.0);
                if (hesperSeat != null && spot.group().equalsIgnoreCase("fallen_anchor")) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, hesperSeat[0], hesperSeat[1] + 0.2, hesperSeat[2], 6, 0.2, 0.6, 0.2, 0.0);
                }
            }
        }.runTaskTimer(isle.plugin(), 0L, 20L);
    }

    String dev(CommandSender sender, Player player, String[] parts) {
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        String arg = parts.length > 2 ? parts[2].toLowerCase(Locale.ROOT) : "";
        return switch (sub) {
            case "paste" -> arg.isEmpty() ? "§7/origin dev props paste <spot|group|all>" : paste(sender, arg);
            case "undo" -> arg.isEmpty() ? "§7/origin dev props undo <spot|group|all|last>" : undo(sender, arg);
            case "tp" -> tp(player, arg);
            case "show" -> {
                Spot spot = spots.get(arg);
                if (spot == null) {
                    yield "§7/origin dev props show <spot>";
                }
                show(player, spot);
                yield "§aOutline §8· §f" + spot.name() + " §8(12 s)";
            }
            case "list" -> {
                StringBuilder out = new StringBuilder("§6Prop spots §8· §7" + pastedCount() + "/" + spots.size() + " pasted");
                for (Spot spot : spots.values()) {
                    out.append("\n§8- ").append(pasted(spot.id()) ? "§a✔ " : "§7○ ").append("§f").append(spot.id())
                            .append(" §8").append(spot.prop()).append(" @ ").append(spot.at()[0]).append(' ').append(spot.at()[1]).append(' ')
                            .append(spot.at()[2]).append(" r").append(spot.rotate()).append(" §7").append(spot.group());
                }
                if (hesperSeat != null) {
                    out.append("\n§eHesper's seat §8· §f").append((int) Math.floor(hesperSeat[0])).append(' ').append((int) Math.floor(hesperSeat[1]))
                            .append(' ').append((int) Math.floor(hesperSeat[2])).append(" §8— /questnpc story → Hesper's anchor → right-click there");
                }
                yield out.toString();
            }
            default -> "§7/origin dev props <list|paste <spot|group|all>|undo <spot|group|all|last>|tp <spot|hesper>|show <spot>> §8· §7"
                    + pastedCount() + "/" + spots.size() + " pasted";
        };
    }

    List<String> groups() {
        List<String> out = new ArrayList<>();
        for (Spot spot : spots.values()) {
            if (!out.contains(spot.group())) {
                out.add(spot.group());
            }
        }
        return out;
    }

    private boolean isGroup(String target) {
        return groups().stream().anyMatch(g -> g.equalsIgnoreCase(target));
    }

    private void save() {
        try {
            state.save(stateFile);
        } catch (IOException exception) {
            isle.plugin().getLogger().warning("Origin: could not save " + STATE_FILE + ": " + exception.getMessage());
        }
    }

    private void tell(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(isle.plugin(), () -> sender.sendMessage(message));
    }
}
