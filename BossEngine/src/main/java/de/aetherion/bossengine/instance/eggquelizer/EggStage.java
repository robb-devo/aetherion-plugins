package de.aetherion.bossengine.instance.eggquelizer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The EggStage: a floating round concert floor with a speaker network around it.
 *
 * <ul>
 *   <li>Floor (r 19): dark concrete with distance rings every five blocks and a lane guide under
 *       every wall speaker, so the shape of every attack is printed on the floor before it happens.
 *       A fried-egg center pad, because of course.</li>
 *   <li>8 WALL stacks at r 17 (lane blasts), 4 TWEETER towers at r 16 (treble drops),
 *       4 floor SUBS at r 9 (vertical launch). 16 installations, one cable loop between them.</li>
 * </ul>
 *
 * <p>Safety (SaintStage pattern): only ever places into air, records every block it placed and
 * strikes exactly those. A chunk marker lets a crashed fight's leftovers be struck by layout.
 */
final class EggStage {

    static final int FLOOR_R = 19;
    static final float WALL_R = 17f;
    static final float TOWER_R = 16f;
    static final float SUB_R = 9f;
    static final int TOWER_H = 7;
    static final float PLAY_R = 15.5f;

    private static final String MARKER = "egg_stage";

    record Cell(int x, int y, int z, Material m, float order) {
    }

    private final Location center;
    private final World world;
    private final List<Cell> layout;
    private final List<Block> placed = new ArrayList<>();
    private final List<Material> placedAs = new ArrayList<>();
    private int raiseCursor;
    private boolean built;

    final List<EggSpeaker> speakers = new ArrayList<>();
    final List<EggSpeaker> walls = new ArrayList<>();
    final List<EggSpeaker> subs = new ArrayList<>();
    final List<EggSpeaker> tweeters = new ArrayList<>();
    /** Cable i runs from loop node i to loop node i+1. */
    private final List<BlockDisplay> cables = new ArrayList<>();
    private final List<BlockDisplay> risers = new ArrayList<>();
    private EggFx fx;

    private EggStage(Location center) {
        this.center = center;
        this.world = center.getWorld();
        this.layout = layout();
        planNetwork();
    }

    /* ================================================================== claim */

    /** Strike any crashed leftover here, then plan a fresh stage at the first clear height. */
    static EggStage claim(Plugin plugin, Location spawn) {
        Location leftover = readMarker(plugin, spawn);
        if (leftover != null) {
            new EggStage(leftover).strikeByLayout();
            clearMarker(plugin, spawn);
        }
        Location c = snap(spawn);
        int top = c.getWorld().getMaxHeight() - 16;
        Location pick = c.clone().add(0, 4, 0);
        for (int lift = 0; lift <= 90 && c.getBlockY() + lift < top; lift += 3) {
            Location probe = c.clone().add(0, lift, 0);
            if (clearance(probe) >= 0.94) {
                pick = probe;
                break;
            }
        }
        EggStage stage = new EggStage(pick);
        stage.writeMarker(plugin);
        return stage;
    }

    private static double clearance(Location c) {
        World w = c.getWorld();
        int total = 0;
        int air = 0;
        int r = FLOOR_R + 1;
        for (int x = -r; x <= r; x += 2) {
            for (int z = -r; z <= r; z += 2) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                for (int y = -5; y <= 9; y += 2) {
                    total++;
                    if (w.getBlockAt(c.getBlockX() + x, c.getBlockY() + y, c.getBlockZ() + z).getType().isAir()) {
                        air++;
                    }
                }
            }
        }
        return total == 0 ? 0 : air / (double) total;
    }

    private static Location snap(Location at) {
        return new Location(at.getWorld(), at.getBlockX() + 0.5, at.getBlockY(), at.getBlockZ() + 0.5);
    }

    private void writeMarker(Plugin plugin) {
        Chunk chunk = center.getChunk();
        chunk.getPersistentDataContainer().set(new NamespacedKey(plugin, MARKER), PersistentDataType.STRING,
                center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ());
    }

    private static void clearMarker(Plugin plugin, Location at) {
        at.getChunk().getPersistentDataContainer().remove(new NamespacedKey(plugin, MARKER));
    }

    private static Location readMarker(Plugin plugin, Location at) {
        String raw = at.getChunk().getPersistentDataContainer().get(new NamespacedKey(plugin, MARKER), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            String[] p = raw.split(",");
            return new Location(at.getWorld(), Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]), Integer.parseInt(p[2]) + 0.5);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    Location center() {
        return center.clone();
    }

    boolean built() {
        return built;
    }

    /* ================================================================== raise / strike */

    /** Places the next slice. Returns progress 0..1. */
    float raiseStep(int perTick) {
        int end = Math.min(layout.size(), raiseCursor + perTick);
        Cell last = null;
        for (int i = raiseCursor; i < end; i++) {
            Cell c = layout.get(i);
            Block b = world.getBlockAt(center.getBlockX() + c.x(), center.getBlockY() + c.y(), center.getBlockZ() + c.z());
            if (b.getType().isAir()) {
                b.setType(c.m(), false);
                placed.add(b);
                placedAs.add(c.m());
            }
            last = c;
        }
        raiseCursor = end;
        if (last != null) {
            Location l = new Location(world, center.getBlockX() + last.x() + 0.5, center.getBlockY() + last.y() + 1.0, center.getBlockZ() + last.z() + 0.5);
            world.playSound(l, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.BLOCKS, 1.2f, 0.5f + raiseCursor / (float) layout.size());
            world.spawnParticle(Particle.CLOUD, l, 3, 0.4, 0.2, 0.4, 0.01);
        }
        if (raiseCursor >= layout.size()) {
            built = true;
            return 1f;
        }
        return raiseCursor / (float) layout.size();
    }

    /** End of show: the stage comes down (animated when the plugin can still schedule). */
    void strike(Plugin plugin, int delayTicks) {
        boolean animated = plugin != null && plugin.isEnabled() && delayTicks >= 0;
        if (!animated || placed.isEmpty()) {
            removeRig();
            clearMarker(plugin, center);
        }
        if (placed.isEmpty()) {
            return;
        }
        List<Block> blocks = new ArrayList<>(placed);
        List<Material> mats = new ArrayList<>(placedAs);
        placed.clear();
        placedAs.clear();
        if (!animated) {
            for (int i = blocks.size() - 1; i >= 0; i--) {
                if (blocks.get(i).getType() == mats.get(i)) {
                    blocks.get(i).setType(Material.AIR, false);
                }
            }
            return;
        }
        int[] cursor = {blocks.size() - 1};
        int perTick = Math.max(180, blocks.size() / 30);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!plugin.isEnabled()) {
                task.cancel();
                return;
            }
            if (cursor[0] == blocks.size() - 1) {
                // The dead network stays on stage until the set comes down.
                removeRig();
                catchFallers();
            }
            int stop = Math.max(-1, cursor[0] - perTick);
            Block lastBlock = null;
            for (int i = cursor[0]; i > stop; i--) {
                Block b = blocks.get(i);
                if (b.getType() == mats.get(i)) {
                    b.setType(Material.AIR, false);
                    lastBlock = b;
                }
            }
            cursor[0] = stop;
            if (lastBlock != null) {
                Location l = lastBlock.getLocation().add(0.5, 0.5, 0.5);
                world.playSound(l, Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1f, 0.6f);
                world.spawnParticle(Particle.CLOUD, l, 4, 0.5, 0.3, 0.5, 0.02);
            }
            if (cursor[0] < 0) {
                // Struck clean. (A server stop mid-strike keeps the marker: the next claim cleans up.)
                clearMarker(plugin, center);
                task.cancel();
            }
        }, Math.max(1, delayTicks), 1L);
    }

    private void strikeByLayout() {
        for (int i = layout.size() - 1; i >= 0; i--) {
            Cell c = layout.get(i);
            Block b = world.getBlockAt(center.getBlockX() + c.x(), center.getBlockY() + c.y(), center.getBlockZ() + c.z());
            if (b.getType() == c.m()) {
                b.setType(Material.AIR, false);
            }
        }
    }

    private void catchFallers() {
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - center.getX();
            double dz = l.getZ() - center.getZ();
            if (dx * dx + dz * dz < 30 * 30 && Math.abs(l.getY() - center.getY()) < 20) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 25, 0, false, false, true));
            }
        }
    }

    /* ================================================================== network */

    private void planNetwork() {
        // Loop order by angle; walls every 45 deg, tweeters and subs fill the gaps.
        int loop = 0;
        for (int k = 0; k < 8; k++) {
            float a = k * EggMath.PI / 4f;
            Vector3f base = EggMath.flat(a, WALL_R).add(0f, 1f, 0f);
            EggSpeaker w = new EggSpeaker(EggSpeaker.Kind.WALL, loop++, base, a + EggMath.PI, 0f,
                    EggMath.flat(a, WALL_R - 1.2f).add(0f, 0.06f, 0f));
            walls.add(w);
            speakers.add(w);
            float g = a + EggMath.PI / 8f;
            if (k % 2 == 0) {
                Vector3f top = EggMath.flat(g, TOWER_R).add(0f, TOWER_H, 0f);
                EggSpeaker t = new EggSpeaker(EggSpeaker.Kind.TWEETER, loop++, top, g + EggMath.PI, 0.32f,
                        EggMath.flat(g, TOWER_R - 1.6f).add(0f, 0.06f, 0f));
                tweeters.add(t);
                speakers.add(t);
            } else {
                Vector3f floor = EggMath.flat(g, SUB_R).add(0f, 0.0f, 0f);
                EggSpeaker s = new EggSpeaker(EggSpeaker.Kind.SUB, loop++, floor, 0f, 0f,
                        EggMath.flat(g, SUB_R + 1.6f).add(0f, 0.06f, 0f));
                subs.add(s);
                speakers.add(s);
            }
        }
    }

    EggSpeaker node(int loopIndex) {
        int n = speakers.size();
        return speakers.get(((loopIndex % n) + n) % n);
    }

    int size() {
        return speakers.size();
    }

    void spawnRig(EggFx fx) {
        this.fx = fx;
        for (EggSpeaker s : speakers) {
            s.spawn(fx);
        }
        for (int i = 0; i < speakers.size(); i++) {
            EggSpeaker a = speakers.get(i);
            EggSpeaker b = node(i + 1);
            BlockDisplay d = fx.block(Material.BLACK_CONCRETE, null, 6);
            EggFx.push(d, EggFx.beam(a.port, b.port, 0.18f, 0.1f), 0);
            cables.add(d);
        }
        for (EggSpeaker t : tweeters) {
            BlockDisplay d = fx.block(Material.BLACK_CONCRETE, null, 6);
            Vector3f up = new Vector3f(t.port.x, TOWER_H - 0.1f, t.port.z);
            EggFx.push(d, EggFx.beam(t.port, up, 0.16f), 0);
            risers.add(d);
        }
    }

    boolean rigIntact() {
        for (EggSpeaker s : speakers) {
            if (!s.intact()) {
                return false;
            }
        }
        for (BlockDisplay d : cables) {
            if (d == null || !d.isValid()) {
                return false;
            }
        }
        return true;
    }

    void removeRig() {
        for (EggSpeaker s : speakers) {
            s.remove();
        }
        for (BlockDisplay d : cables) {
            EggFx.kill(d);
        }
        for (BlockDisplay d : risers) {
            EggFx.kill(d);
        }
        cables.clear();
        risers.clear();
    }

    /** Light up cable i (node i to node i+1) while a signal runs on it. */
    void cableLit(int i, Color color) {
        if (cables.isEmpty()) {
            return;
        }
        BlockDisplay d = cables.get(((i % cables.size()) + cables.size()) % cables.size());
        if (d == null || !d.isValid()) {
            return;
        }
        if (color == null) {
            d.setBlock(Material.BLACK_CONCRETE.createBlockData());
            EggFx.glow(d, null);
            EggFx.light(d, 6);
        } else {
            d.setBlock(Material.WHITE_CONCRETE.createBlockData());
            EggFx.glow(d, color);
            EggFx.light(d, 15);
        }
    }

    void tickSpeakers() {
        for (EggSpeaker s : speakers) {
            s.tick();
        }
    }

    /* ================================================================== layout */

    private static List<Cell> layout() {
        List<Cell> cells = new ArrayList<>();
        int r = FLOOR_R;
        for (int x = -r - 2; x <= r + 2; x++) {
            for (int z = -r - 2; z <= r + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r + 0.5) {
                    cells.add(new Cell(x, -1, z, floorMaterial(x, z, d), (float) d));
                    for (int k = 2; k <= 5; k++) {
                        double kr = keel(k);
                        if (d <= kr) {
                            cells.add(new Cell(x, -k, z, k == 2 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE, (float) d - k * 0.01f));
                        }
                    }
                } else if (d <= r + 1.5) {
                    // Rim: one block, so nobody strolls off the edge by accident.
                    cells.add(new Cell(x, -1, z, Material.POLISHED_BLACKSTONE_BRICKS, (float) d));
                    cells.add(new Cell(x, 0, z, (Math.round(Math.toDegrees(Math.atan2(x, z))) % 15 == 0)
                            ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICK_WALL, (float) d + 0.2f));
                }
            }
        }
        // Wall pedestals.
        for (int k = 0; k < 8; k++) {
            float a = k * EggMath.PI / 4f;
            int cx = Math.round((float) Math.sin(a) * WALL_R);
            int cz = Math.round((float) Math.cos(a) * WALL_R);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cells.add(new Cell(cx + dx, 0, cz + dz, dx == 0 && dz == 0 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS, WALL_R + 0.3f));
                }
            }
        }
        // Tweeter towers.
        for (int k = 0; k < 4; k++) {
            float g = k * EggMath.PI / 2f + EggMath.PI / 8f;
            int cx = Math.round((float) Math.sin(g) * TOWER_R);
            int cz = Math.round((float) Math.cos(g) * TOWER_R);
            for (int y = 0; y < TOWER_H; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean corner = dx != 0 && dz != 0;
                        Material m = y == TOWER_H - 1 ? Material.POLISHED_BLACKSTONE
                                : corner ? Material.POLISHED_BLACKSTONE_BRICKS
                                : y % 3 == 1 ? Material.GILDED_BLACKSTONE : Material.BLACKSTONE;
                        cells.add(new Cell(cx + dx, y, cz + dz, m, TOWER_R + 1f + y * 0.4f));
                    }
                }
            }
        }
        cells.sort(Comparator.comparingDouble(Cell::order));
        return cells;
    }

    private static double keel(int depth) {
        return switch (depth) {
            case 2 -> FLOOR_R - 1.5;
            case 3 -> FLOOR_R - 5.0;
            case 4 -> FLOOR_R - 10.0;
            default -> FLOOR_R - 15.0;
        };
    }

    private static Material floorMaterial(int x, int z, double d) {
        // Fried egg center: the egg's home.
        if (d <= 1.6) {
            return Material.YELLOW_CONCRETE;
        }
        if (d <= 3.4) {
            return Material.WHITE_CONCRETE;
        }
        // Distance rings: shockwave timing reads off the floor.
        for (int ring = 5; ring <= 15; ring += 5) {
            if (Math.abs(d - ring) < 0.5) {
                return Material.LIGHT_GRAY_CONCRETE;
            }
        }
        // Lane guides under every wall stack.
        double ang = Math.atan2(x, z);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0;
            double diff = Math.abs(Math.atan2(Math.sin(ang - a), Math.cos(ang - a)));
            if (diff < Math.PI / 2 && d * Math.sin(diff) < 0.55 && d > 3.4) {
                return Material.BLACK_CONCRETE;
            }
        }
        return Material.GRAY_CONCRETE;
    }
}
