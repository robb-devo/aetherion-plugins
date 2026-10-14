package net.aetherion.samuraiboss.arena;

import net.aetherion.samuraiboss.arena.destruction.FlyInJob;
import net.aetherion.samuraiboss.arena.dojo.BuildPlan;
import net.aetherion.samuraiboss.arena.dojo.El;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Baut ein Dojo: (1) Bereich über dem Boden räumen, (2) Fundament & Boden setzen,
 * (3) Gebäude – entweder sofort oder als Animation, bei der jeder Block aus der Erde schwebt.
 */
public final class BuildJob implements Job {

    private enum Phase {CLEAR, BASE, STRUCTURE, DONE}

    private final Arena arena;
    private final World world;
    private final boolean animated;
    private final int instantPerTick;
    private final int animPerTick;
    private final int flightTicks;
    private final Consumer<Location> onLand;
    private final Runnable onDone;

    private Phase phase = Phase.CLEAR;
    private int cx, cy, cz; // Cursor für das Räumen
    private final List<long[]> base = new ArrayList<>();
    private final List<long[]> structure = new ArrayList<>();
    private int baseCursor;
    private int structCursor;
    private FlyInJob fly;
    private final Map<String, BlockData> dataCache = new HashMap<>();

    public BuildJob(Arena arena, boolean animated, int instantPerTick, int animPerTick, int flightTicks,
                    Consumer<Location> onLand, Runnable onDone) {
        this.arena = arena;
        this.world = arena.world();
        this.animated = animated;
        this.instantPerTick = Math.max(500, instantPerTick);
        this.animPerTick = animPerTick;
        this.flightTicks = flightTicks;
        this.onLand = onLand;
        this.onDone = onDone;
        BuildPlan plan = arena.plan;
        cx = plan.minX;
        cy = plan.clearMinY;
        cz = plan.minZ;
        for (Map.Entry<Long, BuildPlan.Placement> e : plan.blocks().entrySet()) {
            long k = e.getKey();
            El el = e.getValue().element();
            boolean isBase = el.protectedEl && el != El.TREE_TRUNK || BuildPlan.ky(k) < 0;
            (isBase ? base : structure).add(new long[]{k});
        }
        // Struktur von unten nach oben, innerhalb einer Schicht von der Mitte nach außen
        structure.sort(Comparator.comparingInt((long[] a) -> BuildPlan.ky(a[0]))
                .thenComparingDouble(a -> Math.hypot(BuildPlan.kx(a[0]), BuildPlan.kz(a[0]) - 4)));
    }

    private BlockData data(String s) {
        return dataCache.computeIfAbsent(s, Bukkit::createBlockData);
    }

    @Override
    public boolean tick() {
        switch (phase) {
            case CLEAR -> clearStep();
            case BASE -> baseStep();
            case STRUCTURE -> structureStep();
            case DONE -> {
                return true;
            }
        }
        return phase == Phase.DONE;
    }

    private void clearStep() {
        BuildPlan plan = arena.plan;
        int budget = instantPerTick * 3, writes = 0;
        while (budget-- > 0 && writes < instantPerTick) {
            if (cx > plan.maxX) {
                phase = Phase.BASE;
                return;
            }
            BuildPlan.Placement p = plan.get(cx, cy, cz);
            if (p == null) {
                Block b = world.getBlockAt(arena.ox + cx, arena.oy + cy, arena.oz + cz);
                if (b.getType() != Material.AIR) {
                    b.setType(Material.AIR, false);
                    writes++;
                }
            }
            // weiter: y -> z -> x
            if (++cy > plan.clearMaxY) {
                cy = plan.clearMinY;
                if (++cz > plan.maxZ) {
                    cz = plan.minZ;
                    cx++;
                }
            }
        }
    }

    private void baseStep() {
        int n = 0;
        while (baseCursor < base.size() && n < instantPerTick) {
            place(base.get(baseCursor++)[0]);
            n++;
        }
        if (baseCursor >= base.size()) {
            phase = Phase.STRUCTURE;
            if (animated) {
                List<FlyInJob.Target> targets = new ArrayList<>(structure.size());
                for (long[] a : structure) {
                    long k = a[0];
                    BuildPlan.Placement p = arena.plan.blocks().get(k);
                    targets.add(new FlyInJob.Target(arena.ox + BuildPlan.kx(k), arena.oy + BuildPlan.ky(k), arena.oz + BuildPlan.kz(k),
                            data(p.block()), null, null));
                }
                fly = new FlyInJob(world, arena.id, targets, animPerTick, flightTicks, FlyInJob.Mode.FROM_BELOW, onLand, null);
            }
        }
    }

    private void structureStep() {
        if (fly != null) {
            if (fly.tick()) finish();
            return;
        }
        int n = 0;
        while (structCursor < structure.size() && n < instantPerTick) {
            place(structure.get(structCursor++)[0]);
            n++;
        }
        if (structCursor >= structure.size()) finish();
    }

    private void place(long k) {
        BuildPlan.Placement p = arena.plan.blocks().get(k);
        Block b = world.getBlockAt(arena.ox + BuildPlan.kx(k), arena.oy + BuildPlan.ky(k), arena.oz + BuildPlan.kz(k));
        BlockData d = data(p.block());
        if (!b.getBlockData().equals(d)) b.setBlockData(d, false);
    }

    private void finish() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        if (onDone != null) onDone.run();
    }

    @Override
    public void finishNow() {
        while (phase == Phase.CLEAR) clearStep();
        while (phase == Phase.BASE) baseStep();
        if (fly != null) fly.finishNow();
        else while (structCursor < structure.size()) place(structure.get(structCursor++)[0]);
        finish();
    }

    @Override
    public String describe() {
        return switch (phase) {
            case CLEAR -> "Bauplatz räumen…";
            case BASE -> "Fundament " + Math.round(100f * baseCursor / Math.max(1, base.size())) + "%";
            case STRUCTURE -> fly != null ? fly.describe() : "Gebäude " + Math.round(100f * structCursor / Math.max(1, structure.size())) + "%";
            case DONE -> "fertig";
        };
    }

    public float progress() {
        return switch (phase) {
            case CLEAR -> 0.05f;
            case BASE -> 0.05f + 0.15f * baseCursor / Math.max(1, base.size());
            case STRUCTURE -> 0.2f + 0.8f * (fly != null ? fly.progress() : structCursor / (float) Math.max(1, structure.size()));
            case DONE -> 1f;
        };
    }
}
