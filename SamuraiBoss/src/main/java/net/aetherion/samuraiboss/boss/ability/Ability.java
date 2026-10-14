package net.aetherion.samuraiboss.boss.ability;

import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.boss.Phase;
import net.aetherion.samuraiboss.boss.SamuraiBoss;
import net.aetherion.samuraiboss.render.fx.FxContext;
import net.aetherion.samuraiboss.util.Hits;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Sequence;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Grundlage aller Fähigkeiten. Eine Fähigkeit ist eine Choreografie ({@link Sequence}):
 * Telegrafie, Animation, Effekte und Trefferabfrage sauber getaktet.
 *
 * <p>Jede Fähigkeit trifft ein Ziel pro Ausführung nur einmal pro "Treffer-Gruppe" (z. B. pro
 * Linie), damit nichts ungewollt mehrfach Schaden macht.</p>
 */
public abstract class Ability {

    protected final Fight f;
    protected final SamuraiBoss boss;
    protected final FxContext fx;
    protected final Sfx sfx;
    protected final Settings s;
    public final String id;
    protected Sequence seq;
    protected LivingEntity target;
    private final Set<String> hitKeys = new HashSet<>();
    private boolean done = true;

    protected Ability(Fight f, String id) {
        this.f = f;
        this.boss = f.boss();
        this.fx = f.fx();
        this.sfx = f.sfx();
        this.s = f.settings();
        this.id = id;
    }

    /** Gewichtung für die Auswahl (0 = jetzt nicht möglich). dist = Abstand zum Ziel. */
    public abstract int weight(Phase phase, double dist);

    /** Baut die Choreografie. target ist gesetzt. */
    protected abstract Sequence build();

    public int cooldown() {
        return s.cooldown(id);
    }

    public final void begin(LivingEntity target) {
        this.target = target;
        hitKeys.clear();
        done = false;
        seq = build();
    }

    /** @return false, wenn fertig. */
    public boolean tick() {
        if (done) return false;
        if (!seq.tick()) done = true;
        return !done;
    }

    public void cancel() {
        if (done) return;
        done = true;
        if (seq != null) seq.stop();
        onCancel();
    }

    /** Aufräumen bei Abbruch (Effekte entfernen, Klone löschen …). */
    protected void onCancel() {
    }

    public boolean isDone() {
        return done;
    }

    // ================================================================== Helfer: Ziele

    protected List<LivingEntity> targets() {
        return f.targets();
    }

    protected boolean targetValid() {
        return target != null && target.isValid() && !target.isDead() && f.targets().contains(target);
    }

    /** Aktuelle Position des Ziels oder (falls weg) ein Punkt vor dem Boss. */
    protected Location targetLoc() {
        if (targetValid()) return target.getLocation();
        Vector d = MathUtil.dirOfYaw(boss.yaw()).multiply(5);
        return boss.location().add(d);
    }

    // ================================================================== Helfer: Schaden

    /**
     * Fügt Schaden zu – jedes Ziel höchstens einmal pro Gruppe.
     *
     * @return Anzahl getroffener Ziele
     */
    protected int damage(String group, List<LivingEntity> victims, String dmgKey, Location from, double knock, double up) {
        int n = 0;
        for (LivingEntity e : victims) {
            String key = group + ":" + e.getUniqueId();
            if (!hitKeys.add(key)) continue;
            f.hurt(e, s.damage(dmgKey), from, knock, up);
            n++;
        }
        return n;
    }

    protected List<LivingEntity> inLine(Location a, Location b, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : targets()) if (Hits.inLine(e, a, b, r, -0.5, 3.0)) out.add(e);
        return out;
    }

    protected List<LivingEntity> inCircle(Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : targets()) if (Hits.inCircle(e, c, r, -0.5, 3.2)) out.add(e);
        return out;
    }

    protected List<LivingEntity> inCone(Location o, float yaw, float half, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : targets()) if (Hits.inCone(e, o, yaw, half, r, -0.5, 3.2)) out.add(e);
        return out;
    }

    protected boolean alreadyHit(String group, UUID id) {
        return hitKeys.contains(group + ":" + id);
    }

    // ================================================================== Helfer: Raum

    protected Location ground(Location l) {
        return f.ground(l);
    }

    /** Endpunkt eines Sprints: im Kampfbereich und nicht durch Hindernisse. */
    protected Location dashEnd(Location from, Location to) {
        return f.clampDash(from, to);
    }

    /** Punkt in Richtung des Ziels, um 'beyond' Blöcke über das Ziel hinaus verlängert. */
    protected Location through(Location from, Location to, double beyond, double maxLen) {
        Vector d = to.toVector().subtract(from.toVector());
        d.setY(0);
        double len = d.length();
        if (len < 0.01) d = MathUtil.dirOfYaw(boss.yaw());
        else d.multiply(1.0 / len);
        double total = Math.min(maxLen, len + beyond);
        Location end = from.clone().add(d.multiply(total));
        end.setY(from.getY());
        return end;
    }
}
