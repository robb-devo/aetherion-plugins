package net.aetherion.samuraiboss.arena.destruction;

import net.aetherion.samuraiboss.arena.Job;
import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lässt Blöcke animiert an ihren Platz schweben – für den magischen Wiederaufbau nach dem
 * Kampf und die Bau-Animation des Dojos.
 *
 * <p>Ablauf pro Block: ein BlockDisplay erscheint an der Zielposition, aber mit Versatz
 * (unter der Erde / in der Luft verstreut / dort, wo ein Trümmerstück schwebt), fliegt in einem
 * Bogen heim und wird im Moment der Landung durch den echten Block ersetzt.</p>
 */
public final class FlyInJob implements Job {

    public enum Mode {
        /** Aus dem Boden aufsteigend (Bau-Animation). */
        FROM_BELOW,
        /** Aus zufälligen Richtungen über dem Dojo herbei (Wiederaufbau ohne Trümmer). */
        FROM_SCATTER
    }

    /** Ein Ziel. existing = bereits schwebendes Trümmer-Display (wird wiederverwendet). */
    public record Target(int x, int y, int z, BlockData data, Display existing, Transformation existingTransform) {
    }

    private static final class Flight {
        Display display;
        Target target;
        int launch;
        int mid;
        int land;
        Transformation midTf;
        /** Begleitendes Blütenblatt, das den Block "trägt" (optional). */
        BlockDisplay petal;
        Transformation petalMid, petalEnd;
    }

    private final World world;
    private final String arenaId;
    private final Deque<Target> pending;
    private final List<Flight> flying = new ArrayList<>();
    private final int perTick;
    private final int flightTicks;
    private final Mode mode;
    private final Runnable onDone;
    private final Consumer<Location> onLand;
    private final int total;
    private int tick;
    private int landed;
    private int launched;
    private boolean done;
    private BlockData[] petalData;
    private int petalEvery;

    public FlyInJob(World world, String arenaId, List<Target> orderedTargets, int perTick, int flightTicks, Mode mode,
                    Consumer<Location> onLand, Runnable onDone) {
        this.world = world;
        this.arenaId = arenaId;
        this.pending = new ArrayDeque<>(orderedTargets);
        this.total = orderedTargets.size();
        this.perTick = Math.max(1, perTick);
        this.flightTicks = Math.max(6, Math.min(59, flightTicks));
        this.mode = mode;
        this.onLand = onLand;
        this.onDone = onDone;
    }

    /**
     * Jeder n-te fliegende Block bekommt ein Blütenblatt, das ihn begleitet – als würden die
     * Blüten die Blöcke an ihren Platz zurücktragen.
     */
    public FlyInJob withPetals(int every, BlockData... colors) {
        this.petalEvery = Math.max(1, every);
        this.petalData = colors;
        return this;
    }

    @Override
    public boolean tick() {
        if (done) return true;
        // neue Blöcke starten
        for (int n = 0; n < perTick && !pending.isEmpty(); n++) launch(pending.poll());
        // Flüge fortschreiben
        Iterator<Flight> it = flying.iterator();
        while (it.hasNext()) {
            Flight f = it.next();
            if (f.display == null || !f.display.isValid()) {
                place(f.target);
                it.remove();
                continue;
            }
            if (tick == f.launch + 1) {
                DisplayFactory.lerp(f.display, f.midTf, f.mid - f.launch - 1);
                if (f.petal != null && f.petal.isValid()) DisplayFactory.lerp(f.petal, f.petalMid, f.mid - f.launch - 1);
            } else if (tick == f.mid) {
                DisplayFactory.lerp(f.display, identity(), f.land - f.mid);
                if (f.petal != null && f.petal.isValid()) DisplayFactory.lerp(f.petal, f.petalEnd, f.land - f.mid);
            } else if (tick >= f.land) {
                place(f.target);
                f.display.remove();
                if (f.petal != null && f.petal.isValid()) f.petal.remove();
                it.remove();
            }
        }
        tick++;
        if (pending.isEmpty() && flying.isEmpty()) {
            done = true;
            if (onDone != null) onDone.run();
        }
        return done;
    }

    private void launch(Target t) {
        Flight f = new Flight();
        f.target = t;
        f.launch = tick;
        int flight = flightTicks + Rand.range(-3, 3);
        f.mid = tick + 1 + Math.max(2, Math.round(flight * 0.62f));
        f.land = tick + 1 + flight;
        Location at = new Location(world, t.x(), t.y(), t.z());
        Transformation start;
        if (t.existing() != null && t.existing().isValid()) {
            f.display = t.existing();
            start = t.existingTransform() != null ? t.existingTransform() : f.display.getTransformation();
            // Bogen: von der aktuellen Lage aus etwas anheben, dann heim
            Vector3f s = new Vector3f(start.getTranslation());
            Vector3f mid = new Vector3f(s).mul(0.35f).add(0, 1.2f + Rand.f() * 1.5f, 0);
            f.midTf = new Transformation(mid, new Quaternionf(start.getLeftRotation()).slerp(new Quaternionf(), 0.6f),
                    new Vector3f(1f, 1f, 1f), new Quaternionf());
        } else {
            Vector3f off;
            Quaternionf rot = new Quaternionf().rotateXYZ(Rand.signed() * 2f, Rand.signed() * 3f, Rand.signed() * 2f);
            if (mode == Mode.FROM_BELOW) {
                off = new Vector3f(Rand.signed() * 1.5f, -5f - Rand.f() * 5f, Rand.signed() * 1.5f);
            } else {
                Vector3f dir = Rand.unit();
                dir.y = Math.abs(dir.y) * 0.6f + 0.4f;
                off = dir.normalize().mul(7f + Rand.f() * 8f);
            }
            start = new Transformation(off, rot, new Vector3f(0.25f, 0.25f, 0.25f), new Quaternionf());
            BlockDisplay bd = DisplayFactory.block(at, t.data(), "debris", arenaId);
            bd.setBrightness(new Display.Brightness(13, 15));
            DisplayFactory.snap(bd, start);
            f.display = bd;
            Vector3f mid = new Vector3f(off).mul(0.3f).add(0, mode == Mode.FROM_BELOW ? 1.6f : 1.4f + Rand.f(), 0);
            f.midTf = new Transformation(mid, new Quaternionf(rot).slerp(new Quaternionf(), 0.7f),
                    new Vector3f(0.95f, 0.95f, 0.95f), new Quaternionf());
        }
        launched++;
        if (petalData != null && petalData.length > 0 && launched % petalEvery == 0) attachPetal(f, at, start);
        flying.add(f);
    }

    /** Ein Blütenblatt fliegt direkt über dem Block mit und löst sich bei der Landung auf. */
    private void attachPetal(Flight f, Location at, Transformation blockStart) {
        BlockData pd = petalData[Rand.range(0, petalData.length - 1)];
        BlockDisplay p = DisplayFactory.block(at, pd, "debris", arenaId);
        p.setBrightness(new Display.Brightness(15, 15));
        Vector3f s0 = new Vector3f(blockStart.getTranslation()).add(0.5f, 1.15f, 0.5f);
        Vector3f s1 = new Vector3f(f.midTf.getTranslation()).add(0.5f, 1.2f, 0.5f);
        Vector3f s2 = new Vector3f(0.5f + Rand.signed() * 0.4f, 1.6f, 0.5f + Rand.signed() * 0.4f);
        Quaternionf r0 = new Quaternionf().rotateXYZ(Rand.signed() * 3, Rand.signed() * 3, Rand.signed() * 3);
        Quaternionf r1 = new Quaternionf().rotateXYZ(Rand.signed() * 3, Rand.signed() * 3, Rand.signed() * 3);
        Vector3f size = new Vector3f(0.32f, 0.04f, 0.24f);
        DisplayFactory.snap(p, net.aetherion.samuraiboss.render.Tf.box(s0, r0, size));
        f.petal = p;
        f.petalMid = net.aetherion.samuraiboss.render.Tf.box(s1, r1, size);
        f.petalEnd = net.aetherion.samuraiboss.render.Tf.box(s2, r0, new Vector3f());
    }

    private static Transformation identity() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf());
    }

    private void place(Target t) {
        Block b = world.getBlockAt(t.x(), t.y(), t.z());
        if (!b.getBlockData().equals(t.data())) b.setBlockData(t.data(), false);
        landed++;
        if (onLand != null && landed % 3 == 0) onLand.accept(b.getLocation().add(0.5, 0.5, 0.5));
    }

    @Override
    public void finishNow() {
        for (Flight f : flying) {
            place(f.target);
            if (f.display != null && f.display.isValid()) f.display.remove();
            if (f.petal != null && f.petal.isValid()) f.petal.remove();
        }
        flying.clear();
        while (!pending.isEmpty()) {
            Target t = pending.poll();
            place(t);
            if (t.existing() != null && t.existing().isValid()) t.existing().remove();
        }
        if (!done) {
            done = true;
            if (onDone != null) onDone.run();
        }
    }

    public float progress() {
        return total == 0 ? 1f : landed / (float) total;
    }

    @Override
    public String describe() {
        return (mode == Mode.FROM_BELOW ? "Bau-Animation " : "Wiederaufbau ") + Math.round(progress() * 100) + "% (" + landed + "/" + total + ")";
    }
}
