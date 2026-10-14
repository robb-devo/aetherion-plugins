package net.aetherion.samuraiboss.render.anim;

import net.aetherion.samuraiboss.render.model.ModelDef;
import net.aetherion.samuraiboss.render.model.Pose;
import net.aetherion.samuraiboss.util.Ease;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spielt Clips auf zwei Ebenen ab:
 * <ul>
 *   <li><b>Basis</b> (Schleife): Stehen, Gehen, Kampfhaltung …</li>
 *   <li><b>Aktion</b> (einmalig): Schläge, Ziehen, Verbeugen … überschreibt nur die Knochen,
 *       die der Clip tatsächlich animiert.</li>
 * </ul>
 * Jeder Wechsel wird automatisch weich überblendet (von der zuletzt gezeigten Pose aus).
 */
public final class Animator {

    /** Wird für Clip-Ereignisse aufgerufen (z. B. "hit" -> Schaden austeilen). */
    @FunctionalInterface
    public interface Listener {
        void onEvent(String event);
    }

    private final ModelDef def;
    private final int n;

    private Clip base;
    private float baseTime;
    private float baseSpeed = 1f;

    private Clip action;
    private float actionTime;
    private float actionSpeed = 1f;
    private Listener actionListener;
    private int actionBlendOut = 4;
    private boolean actionFinished;

    private final Pose last;
    private Pose transitionFrom;
    private int transitionTicks;
    private int transitionAge;

    private float timeScale = 1f;

    private final Quaternionf qTmp = new Quaternionf();
    private final Vector3f vTmp = new Vector3f();
    private final float[] sTmp = new float[1];

    public Animator(ModelDef def) {
        this.def = def;
        this.n = def.boneCount();
        this.last = new Pose(n);
    }

    // --------------------------------------------------------------- Steuerung

    /** Wechselt die Basis-Schleife (z. B. idle -> walk). */
    public void playBase(Clip clip, int blendTicks) {
        playBase(clip, blendTicks, 1f);
    }

    public void playBase(Clip clip, int blendTicks, float speed) {
        this.baseSpeed = speed;
        if (clip == base) return;
        startTransition(blendTicks);
        base = clip;
        baseTime = 0f;
    }

    public Clip base() {
        return base;
    }

    /** Spielt eine einmalige Aktion ab. */
    public void play(Clip clip, int blendIn, int blendOut, float speed, Listener listener) {
        startTransition(blendIn);
        action = clip;
        actionTime = 0f;
        actionSpeed = speed;
        actionListener = listener;
        actionBlendOut = blendOut;
        actionFinished = false;
        // Ereignisse bei t=0 sofort feuern
        fireEvents(-1f, 0f);
    }

    public void play(Clip clip, int blendIn, int blendOut) {
        play(clip, blendIn, blendOut, 1f, null);
    }

    public void play(Clip clip, Listener listener) {
        play(clip, 3, 4, 1f, listener);
    }

    /** Beendet die aktuelle Aktion (z. B. gehaltene Haltung) mit Überblendung. */
    public void stopAction(int blendTicks) {
        if (action == null) return;
        startTransition(blendTicks);
        action = null;
        actionListener = null;
    }

    public boolean isActionPlaying() {
        return action != null && !actionFinished;
    }

    /** true, solange eine Aktion läuft ODER gehalten wird. */
    public boolean hasAction() {
        return action != null;
    }

    public Clip action() {
        return action;
    }

    public float actionTime() {
        return actionTime;
    }

    public void setTimeScale(float s) {
        this.timeScale = Math.max(0f, s);
    }

    public float timeScale() {
        return timeScale;
    }

    /** Empfohlener Update-Abstand: 1 bei schnellen Clips, sonst 2. */
    public int recommendedInterval() {
        if (action != null && !actionFinished && action.sampleInterval == 1 && timeScale > 0.5f) return 1;
        return 2;
    }

    private void startTransition(int ticks) {
        if (ticks <= 0) {
            transitionFrom = null;
            return;
        }
        transitionFrom = last.copy();
        transitionTicks = ticks;
        transitionAge = 0;
    }

    // --------------------------------------------------------------- Zeit

    /** Einen Tick weiter. */
    public void tick() {
        float dt = timeScale;
        if (base != null) {
            baseTime += dt * baseSpeed;
            if (base.loop && base.length > 0) baseTime %= base.length;
        }
        if (action != null && !actionFinished) {
            float before = actionTime;
            actionTime += dt * actionSpeed;
            fireEvents(before, actionTime);
            if (actionTime >= action.length) {
                actionTime = action.length;
                if (action.loop) {
                    actionTime = 0f;
                } else if (action.hold) {
                    actionFinished = true; // bleibt stehen
                } else {
                    actionFinished = true;
                    Clip ended = action;
                    action = null;
                    actionListener = null;
                    startTransition(actionBlendOut);
                    if (ended != null) onActionEnded(ended);
                }
            }
        }
        if (transitionFrom != null) {
            transitionAge += Math.max(1, Math.round(dt));
            if (transitionAge >= transitionTicks) transitionFrom = null;
        }
    }

    private void onActionEnded(Clip clip) {
        // Platz für Statistik/Debug
    }

    private void fireEvents(float from, float to) {
        if (action == null || actionListener == null) return;
        Listener l = actionListener;
        for (Clip.Event e : action.events) {
            if (e.time() > from && e.time() <= to) {
                try {
                    l.onEvent(e.name());
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                }
                if (action == null || actionListener != l) return; // Listener hat neue Aktion gestartet
            }
        }
    }

    // --------------------------------------------------------------- Abtasten

    /** Schreibt die aktuelle Pose nach out. */
    public void sample(Pose out) {
        out.reset();
        for (int b = 0; b < n; b++) {
            boolean set = false;
            if (action != null && action.affects(b)) {
                action.sample(b, actionTime, qTmp, vTmp, sTmp);
                set = true;
            } else if (base != null && base.affects(b)) {
                base.sample(b, baseTime, qTmp, vTmp, sTmp);
                set = true;
            }
            if (set) {
                out.rot[b].set(qTmp);
                out.pos[b].set(vTmp);
                out.scale[b] = sTmp[0];
            }
        }
        if (transitionFrom != null && transitionTicks > 0) {
            float w = Ease.SMOOTH.apply(Math.min(1f, (transitionAge + 0.5f) / transitionTicks));
            // out = mix(from, out, w)
            for (int b = 0; b < n; b++) {
                Pose f = transitionFrom;
                vTmp.set(f.pos[b]).lerp(out.pos[b], w);
                qTmp.set(f.rot[b]).slerp(out.rot[b], w);
                float s = f.scale[b] + (out.scale[b] - f.scale[b]) * w;
                out.pos[b].set(vTmp);
                out.rot[b].set(qTmp);
                out.scale[b] = s;
            }
        }
        last.set(out);
    }

    /** Die zuletzt ausgegebene Pose (z. B. für Schattenklone). */
    public Pose lastPose() {
        return last;
    }
}
