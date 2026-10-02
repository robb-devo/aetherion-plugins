package de.aetherion.guilds.template;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tick-spread template paster. Every island starter, structure and project stage goes through here, a few
 * thousand blocks per tick, bottom layer first, without physics (plants/lanterns never pop mid-paste).
 *
 * <p>Modes: {@link Mode#SKIP_AIR} (normal), {@link Mode#ONLY_REPLACEABLE} (non-destructive: only fills air and
 * plants, used for quarry housings around existing builds) and {@link Mode#CLEAR_MATCHING} (removes the cells
 * that still hold the template's block; used for deconstruct and project-stage transitions).
 */
public final class PasteService {

    public enum Mode {
        SKIP_AIR,
        ONLY_REPLACEABLE,
        CLEAR_MATCHING
    }

    public static final class Job {
        private final Template template;
        private final World world;
        private final int ax;
        private final int ay;
        private final int az;
        private final int rot;
        private final Mode mode;
        private final boolean reveal;
        private final Template keepMask;
        private final Consumer<Job> onDone;
        private final BlockData[] dataCache;
        private final boolean[] dataFailed;
        private Job next;
        private int cursor;
        private int placed;
        private boolean finished;
        private Block lastPlaced;
        private BlockData lastData;

        private Job(Template template, World world, int ax, int ay, int az, int rot, Mode mode, boolean reveal,
                    Template keepMask, Consumer<Job> onDone) {
            this.template = template;
            this.world = world;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.rot = Math.floorMod(rot, 4);
            this.mode = mode;
            this.reveal = reveal;
            this.keepMask = keepMask;
            this.onDone = onDone;
            this.dataCache = new BlockData[template.palette().length];
            this.dataFailed = new boolean[template.palette().length];
        }

        public boolean finished() {
            return finished;
        }

        public int placed() {
            return placed;
        }

        public Template template() {
            return template;
        }

        public Location center() {
            return new Location(world, ax + 0.5, ay + 1, az + 0.5);
        }
    }

    private final JavaPlugin plugin;
    private final Deque<Job> queue = new ArrayDeque<>();
    private final Set<String> warned = new HashSet<>();

    public PasteService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int pending() {
        return queue.size();
    }

    public Job paste(Template template, World world, int ax, int ay, int az, int rot, Mode mode, boolean reveal,
                     Consumer<Job> onDone) {
        if (template == null || world == null) {
            return null;
        }
        Job job = new Job(template, world, ax, ay, az, rot, mode, reveal, null, onDone);
        queue.addLast(job);
        return job;
    }

    /**
     * Swap one template for another at the same anchor: cells the old one placed and the new one leaves empty are
     * cleared (only while they still hold the old block), then the new one is pasted.
     */
    public Job transition(Template from, Template to, World world, int ax, int ay, int az, int rot, boolean reveal,
                          Consumer<Job> onDone) {
        if (to == null || world == null) {
            return null;
        }
        Job paste = new Job(to, world, ax, ay, az, rot, Mode.SKIP_AIR, reveal, null, onDone);
        if (from == null) {
            queue.addLast(paste);
            return paste;
        }
        Job clear = new Job(from, world, ax, ay, az, rot, Mode.CLEAR_MATCHING, false, to, null);
        clear.next = paste;
        queue.addLast(clear);
        return paste;
    }

    /** Remove whatever of the template is still standing (deconstruct). */
    public Job clear(Template template, World world, int ax, int ay, int az, int rot, Consumer<Job> onDone) {
        if (template == null || world == null) {
            return null;
        }
        Job job = new Job(template, world, ax, ay, az, rot, Mode.CLEAR_MATCHING, false, null, onDone);
        queue.addLast(job);
        return job;
    }

    public void tick() {
        if (queue.isEmpty()) {
            return;
        }
        int budget = Math.max(200, plugin.getConfig().getInt("paste-blocks-per-tick", 3000)) * 8;
        while (budget > 0 && !queue.isEmpty()) {
            Job job = queue.peekFirst();
            budget = work(job, budget);
            if (job.cursor >= job.template.volume()) {
                queue.pollFirst();
                job.finished = true;
                if (job.next != null) {
                    queue.addFirst(job.next);
                }
                if (job.onDone != null) {
                    try {
                        job.onDone.accept(job);
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Paste callback failed for " + job.template.id() + ": "
                                + exception.getMessage());
                    }
                }
            }
        }
    }

    private int work(Job job, int budget) {
        Template t = job.template;
        int volume = t.volume();
        int minY = job.world.getMinHeight();
        int maxY = job.world.getMaxHeight();
        int placedThisTick = 0;
        while (budget > 0 && job.cursor < volume) {
            int index = job.cursor++;
            int p = t.paletteAt(index);
            if (t.isAirPalette(p)) {
                budget -= 1;
                continue;
            }
            budget -= 8;
            int lx = t.localX(index);
            int ly = t.localY(index);
            int lz = t.localZ(index);
            int wy = job.ay + ly;
            if (wy < minY || wy >= maxY) {
                continue;
            }
            int[] r = StateRotator.rotateXZ(lx, lz, job.rot);
            Block block = job.world.getBlockAt(job.ax + r[0], wy, job.az + r[1]);
            switch (job.mode) {
                case CLEAR_MATCHING -> {
                    if (job.keepMask != null && job.keepMask.materialAtLocal(lx, ly, lz) != Material.AIR) {
                        continue;
                    }
                    if (block.getType() == t.materialOfPalette(p)) {
                        block.setType(Material.AIR, false);
                    }
                    continue;
                }
                case ONLY_REPLACEABLE -> {
                    if (!replaceable(block)) {
                        continue;
                    }
                }
                default -> {
                }
            }
            BlockData data = data(job, p);
            if (data == null) {
                continue;
            }
            block.setBlockData(data, false);
            job.placed++;
            placedThisTick++;
            String[] sign = t.signs().get(index);
            if (sign != null) {
                applySign(block, sign);
            }
            if (job.reveal && placedThisTick % 40 == 1) {
                job.world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.6, 0.5), 5,
                        0.3, 0.3, 0.3, 0.05, data);
            }
            job.lastPlaced = block;
            job.lastData = data;
        }
        if (job.reveal && placedThisTick > 0 && job.lastPlaced != null && Bukkit.getCurrentTick() % 3 == 0) {
            Sound sound;
            try {
                sound = job.lastData.getSoundGroup().getPlaceSound();
            } catch (RuntimeException exception) {
                sound = Sound.BLOCK_STONE_PLACE;
            }
            job.world.playSound(job.lastPlaced.getLocation(), sound, 0.7f, 0.8f + (float) Math.random() * 0.4f);
        }
        return budget;
    }

    private BlockData data(Job job, int p) {
        BlockData cached = job.dataCache[p];
        if (cached != null || job.dataFailed[p]) {
            return cached;
        }
        String state = StateRotator.rotate(job.template.palette()[p], job.rot);
        try {
            cached = Bukkit.createBlockData(state);
        } catch (IllegalArgumentException exception) {
            job.dataFailed[p] = true;
            if (warned.add(job.template.id() + "|" + state)) {
                plugin.getLogger().warning("Template " + job.template.id() + ": unknown block state " + state
                        + " (skipped).");
            }
            return null;
        }
        job.dataCache[p] = cached;
        return cached;
    }

    private static void applySign(Block block, String[] lines) {
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        SignSide side = sign.getSide(Side.FRONT);
        for (int i = 0; i < 4 && i < lines.length; i++) {
            side.line(i, parse(lines[i]));
        }
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private static Component parse(String json) {
        if (json == null || json.isBlank() || json.equals("\"\"")) {
            return Component.empty();
        }
        try {
            return GsonComponentSerializer.gson().deserialize(json);
        } catch (RuntimeException exception) {
            String plain = json.startsWith("\"") && json.endsWith("\"") && json.length() >= 2
                    ? json.substring(1, json.length() - 1)
                    : json;
            return Component.text(plain);
        }
    }

    public static boolean replaceable(Block block) {
        Material type = block.getType();
        if (type.isAir()) {
            return true;
        }
        if (Tag.FLOWERS.isTagged(type) || Tag.SAPLINGS.isTagged(type)) {
            return true;
        }
        return switch (type) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, SNOW, MOSS_CARPET, VINE, GLOW_LICHEN,
                 SWEET_BERRY_BUSH, BROWN_MUSHROOM, RED_MUSHROOM, HANGING_ROOTS, NETHER_SPROUTS -> true;
            default -> false;
        };
    }
}
