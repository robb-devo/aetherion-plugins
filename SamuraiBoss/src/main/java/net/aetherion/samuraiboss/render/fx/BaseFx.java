package net.aetherion.samuraiboss.render.fx;

import net.aetherion.samuraiboss.render.DisplayFactory;
import net.aetherion.samuraiboss.render.DisplayPool;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Grundlage für Effekte. Kümmert sich um
 * <ul>
 *   <li>das Alter des Effekts (Ticks seit Start)</li>
 *   <li>das Ausleihen/Zurückgeben von Displays aus dem Pool</li>
 *   <li>"Keyframes" pro Display: bei Alter X Transformation setzen/interpolieren/freigeben</li>
 * </ul>
 *
 * <p>Wichtig für weiche Animationen: Ein Display, das in Tick N erscheint, kann frühestens ab
 * Tick N+1 interpoliert werden. Deshalb setzen Effekte im ersten Tick einen Startzustand per
 * {@link #snapAt} und animieren ab dem nächsten Tick mit {@link #lerpAt}.</p>
 */
public abstract class BaseFx implements Fx {

    protected final FxManager fx;
    protected final DisplayPool pool;
    protected int age;
    private final List<Display> owned = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private int stepIndex;
    private boolean stepsDirty;
    private boolean stopped;

    private record Step(int age, int order, Display d, Consumer<Display> action) {
    }

    private int orderCounter;

    protected BaseFx(FxManager fx) {
        this.fx = fx;
        this.pool = fx.pool();
    }

    /** Wird jeden Tick mit dem aktuellen Alter aufgerufen. false = fertig. */
    protected abstract boolean update(int age);

    @Override
    public final boolean tick() {
        if (stopped) return false;
        runSteps(age);
        boolean alive = update(age);
        runSteps(age); // Schritte, die update() für "jetzt" geplant hat
        age++;
        return alive;
    }

    private void runSteps(int now) {
        if (stepsDirty) {
            steps.subList(0, stepIndex).clear();
            stepIndex = 0;
            steps.sort(Comparator.comparingInt(Step::age).thenComparingInt(Step::order));
            stepsDirty = false;
        }
        while (stepIndex < steps.size() && steps.get(stepIndex).age <= now) {
            Step s = steps.get(stepIndex++);
            if (s.d == null || s.d.isValid()) {
                try {
                    s.action.accept(s.d);
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void stop() {
        if (stopped) return;
        stopped = true;
        for (Display d : owned) pool.release(d);
        owned.clear();
        steps.clear();
        onStop();
    }

    /** Für Unterklassen: zusätzliche Aufräumarbeiten. */
    protected void onStop() {
    }

    public boolean isStopped() {
        return stopped;
    }

    public int age() {
        return age;
    }

    // ------------------------------------------------------------- Displays leihen

    protected BlockDisplay block(Location loc, BlockData data) {
        BlockDisplay d = pool.block(loc, data);
        if (d != null) owned.add(d);
        return d;
    }

    protected ItemDisplay item(Location loc, ItemStack stack) {
        ItemDisplay d = pool.item(loc, stack, ItemDisplay.ItemDisplayTransform.FIXED);
        if (d != null) owned.add(d);
        return d;
    }

    protected TextDisplay text(Location loc) {
        TextDisplay d = pool.text(loc);
        if (d != null) owned.add(d);
        return d;
    }

    /** Gibt ein einzelnes Display vorzeitig zurück. */
    protected void free(Display d) {
        if (d != null && owned.remove(d)) pool.release(d);
    }

    protected List<Display> owned() {
        return owned;
    }

    // ------------------------------------------------------------- Zeitplan

    protected void at(int when, Display d, Consumer<Display> action) {
        if (d == null) return;
        steps.add(new Step(when, orderCounter++, d, action));
        stepsDirty = true;
    }

    /** Aktion ohne Display zu einem Zeitpunkt. */
    protected void at(int when, Runnable r) {
        steps.add(new Step(when, orderCounter++, null, x -> r.run()));
        stepsDirty = true;
    }

    protected void snapAt(int when, Display d, Transformation t) {
        at(when, d, x -> DisplayFactory.snap(x, t));
    }

    protected void lerpAt(int when, Display d, Transformation t, int dur) {
        at(when, d, x -> DisplayFactory.lerp(x, t, dur));
    }

    protected void freeAt(int when, Display d) {
        at(when, d, this::free);
    }

    protected void glowAt(int when, Display d, Color c) {
        at(when, d, x -> {
            x.setGlowColorOverride(c);
            x.setGlowing(c != null);
        });
    }

    protected void blockAt(int when, BlockDisplay d, BlockData data) {
        if (d == null) return;
        at(when, d, x -> ((BlockDisplay) x).setBlock(data));
    }

    protected static void fullBright(Display d) {
        if (d != null) d.setBrightness(new Display.Brightness(15, 15));
    }
}
