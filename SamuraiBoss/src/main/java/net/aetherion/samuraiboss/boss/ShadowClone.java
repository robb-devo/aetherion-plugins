package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.render.model.ModelInstance;
import net.aetherion.samuraiboss.render.model.Pose;
import net.aetherion.samuraiboss.render.model.Skin;
import org.bukkit.Location;

/**
 * Ein Schattenklon des Kensei: dasselbe Display-Modell, aber aus dunklem Glas mit violettem
 * Schimmer. Er hat KEINEN eigenen Animator – er spielt aufgezeichnete Posen des echten Bosses
 * nach (zeitversetzt, gedreht um einen Drehpunkt). Dadurch macht er exakt jede Bewegung nach,
 * nur später und von einer anderen Seite: "verzögerte Spiegelung".
 */
public final class ShadowClone {

    /** Ein aufgezeichneter Augenblick des Bosses. */
    public record Frame(Location loc, float yaw, Pose pose, boolean drawn) {
    }

    private final Fight fight;
    private final ModelInstance model;
    private float rootY = -2.8f;
    private float rootTarget = 0f;
    private boolean removed;
    private int age;

    public ShadowClone(Fight fight, Location at, float yaw) {
        this.fight = fight;
        Skin skin = fight.boss().skins.shadow;
        model = new ModelInstance(SamuraiModel.def(), skin, fight.arena().id, fight.settings().packEnabled, 1,
                c -> !skin.hides(c));
        model.spawn(at);
        model.setScale(SamuraiModel.BASE_SCALE * fight.settings().scale);
        model.setYaw(yaw);
        model.setViewRange(2.5f);
        model.setRootOffset(0, rootY, 0);
        fight.trackModel(model);
    }

    /** Zeigt einen Frame an einer (bereits umgerechneten) Position. */
    public void show(Location loc, float yaw, Pose pose, boolean drawn, int interp) {
        if (removed) return;
        age++;
        model.setTagHidden(SamuraiModel.T_KATANA_HAND, !drawn);
        model.setTagHidden(SamuraiModel.T_KATANA_SHEATHED, drawn);
        if (Math.abs(rootY - rootTarget) > 0.01f) rootY += (rootTarget - rootY) * 0.22f;
        else rootY = rootTarget;
        model.setRootOffset(0, rootY, 0);
        model.moveTo(loc);
        model.setYaw(yaw);
        model.update(pose, interp);
        model.tick();
    }

    /** Lässt den Klon wieder im Boden versinken (statt zu zerplatzen). */
    public void sink() {
        rootTarget = -3.2f;
    }

    public Location location() {
        return model.location();
    }

    public ModelInstance model() {
        return model;
    }

    public int age() {
        return age;
    }

    public void remove() {
        if (removed) return;
        removed = true;
        fight.untrackModel(model);
        model.remove();
    }

    public boolean isRemoved() {
        return removed;
    }
}
