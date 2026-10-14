package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.boss.ability.Ability;
import net.aetherion.samuraiboss.boss.ability.AfterimageChain;
import net.aetherion.samuraiboss.boss.ability.BladeRain;
import net.aetherion.samuraiboss.boss.ability.Issen;
import net.aetherion.samuraiboss.boss.ability.Kazekiri;
import net.aetherion.samuraiboss.boss.ability.Kesagiri;
import net.aetherion.samuraiboss.boss.ability.MultiIssen;
import net.aetherion.samuraiboss.boss.ability.SakuraTornado;
import net.aetherion.samuraiboss.boss.ability.ShadowClones;
import net.aetherion.samuraiboss.boss.ability.ThousandBlades;
import net.aetherion.samuraiboss.boss.ability.Utsusemi;
import net.aetherion.samuraiboss.util.MathUtil;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die "KI" des Kensei – bewusst einfach und lesbar:
 * <ol>
 *   <li>Ziel wählen (bleibt einige Sekunden beim selben, bevorzugt wer ihn zuletzt getroffen hat)</li>
 *   <li>Abstand halten: herangehen, umkreisen, gelegentlich zurückweichen</li>
 *   <li>Ist die globale Pause vorbei, eine Fähigkeit nach Gewichtung auswählen
 *       (Gewicht hängt von Phase und Abstand ab, eigene Abklingzeiten, keine direkte Wiederholung)</li>
 *   <li>Wer ihn bedrängt (viele Treffer in kurzer Zeit), bekommt 空蝉 Utsusemi zu spüren</li>
 * </ol>
 */
public final class BossBrain {

    private final Fight f;
    private final SamuraiBoss boss;
    private final Map<String, Ability> abilities = new LinkedHashMap<>();
    private final Map<String, Integer> readyAt = new HashMap<>();
    private final Deque<Integer> recentHits = new ArrayDeque<>();
    private final Map<UUID, Integer> lastHitBy = new HashMap<>();
    /** Fähigkeiten, die in der aktuellen Phase schon gezeigt wurden (Neues wird bevorzugt). */
    private final java.util.Set<String> shownThisPhase = new java.util.HashSet<>();
    private Phase shownPhase;
    private Ability current;
    private String last;
    private int tick;
    private int globalReadyAt;
    private LivingEntity target;
    private int targetUntil;
    private int nextMoveAt;
    private int strafe = 1;

    public BossBrain(Fight f) {
        this.f = f;
        this.boss = f.boss();
        add(new Issen(f));
        add(new Kesagiri(f, false));
        add(new Kesagiri(f, true));
        add(new Kazekiri(f));
        add(new Utsusemi(f));
        add(new MultiIssen(f, false));
        add(new MultiIssen(f, true));
        add(new ThousandBlades(f));
        add(new SakuraTornado(f));
        add(new ShadowClones(f));
        add(new BladeRain(f));
        add(new AfterimageChain(f));
        // zu Beginn nicht gleich die großen Fähigkeiten
        for (Ability a : abilities.values()) readyAt.put(a.id, a.cooldown() / 3);
    }

    private void add(Ability a) {
        abilities.put(a.id, a);
    }

    public Map<String, Ability> abilities() {
        return abilities;
    }

    public Ability current() {
        return current;
    }

    public LivingEntity target() {
        return target;
    }

    /** Pause, bevor die nächste Fähigkeit gewählt wird (z. B. nach einer Zwischensequenz). */
    public void delay(int ticks) {
        globalReadyAt = Math.max(globalReadyAt, tick + ticks);
    }

    /** Laufende Fähigkeit abbrechen (Phasenwechsel, Tod, Kampfende). */
    public void cancel() {
        if (current != null) {
            try {
                current.cancel();
            } catch (RuntimeException ex) {
                f.plugin().getLogger().warning("Fehler beim Abbrechen von " + current.id + ": " + ex);
            }
            current = null;
        }
        boss.stopAction(4);
    }

    /** Wird vom Kampf bei jedem Spielertreffer gemeldet. */
    public void onHit(Player p) {
        recentHits.addLast(tick);
        lastHitBy.put(p.getUniqueId(), tick);
    }

    // =================================================================== Tick

    public void tick() {
        tick++;
        while (!recentHits.isEmpty() && recentHits.peekFirst() < tick - 50) recentHits.pollFirst();

        if (current != null) {
            boolean running;
            try {
                running = current.tick();
            } catch (RuntimeException ex) {
                f.plugin().getLogger().log(java.util.logging.Level.WARNING, "Fähigkeit " + current.id + " abgebrochen", ex);
                current.cancel();
                running = false;
            }
            if (!running) {
                current = null;
                int min = f.settings().globalCooldownMin, max = f.settings().globalCooldownMax;
                float k = switch (f.phase()) {
                    case SEI -> 1f;
                    case ARASHI -> 0.85f;
                    case SANGE -> f.isRuin() ? 0.6f : 0.7f;
                };
                globalReadyAt = tick + Math.round(Rand.range(min, max) * k);
            }
            return;
        }

        selectTarget();
        if (target == null) {
            boss.stopWalking();
            boss.lookAt(null);
            return;
        }
        boss.lookAt(target);
        Location bl = boss.location();
        Location tl = target.getLocation();
        double dist = MathUtil.distXZ(bl, tl);

        // Konter: wer ihn zu sehr bedrängt, schlägt in Blüten
        Ability uts = abilities.get("utsusemi");
        if (recentHits.size() >= 6 && dist < 4.5 && tick >= readyAt.getOrDefault("utsusemi", 0) && tick >= globalReadyAt - 10) {
            recentHits.clear();
            if (Rand.chance(0.6)) {
                start(uts);
                return;
            }
            readyAt.put("utsusemi", tick + 60);
        }

        if (tick >= globalReadyAt) {
            Ability a = pick(dist);
            if (a != null) {
                start(a);
                return;
            }
        }
        move(bl, tl, dist);
    }

    private void start(Ability a) {
        boss.stopWalking();
        current = a;
        last = a.id;
        shownThisPhase.add(a.id);
        readyAt.put(a.id, tick + a.cooldown());
        try {
            a.begin(target);
        } catch (RuntimeException ex) {
            f.plugin().getLogger().log(java.util.logging.Level.WARNING, "Fähigkeit " + a.id + " konnte nicht starten", ex);
            current = null;
        }
    }

    /** Erzwingt eine Fähigkeit (Debug-Befehl). */
    public boolean force(String id) {
        Ability a = abilities.get(id);
        if (a == null) return false;
        cancel();
        selectTarget();
        if (target == null) return false;
        start(a);
        return true;
    }

    private Ability pick(double dist) {
        if (shownPhase != f.phase()) {
            shownPhase = f.phase();
            shownThisPhase.clear();
        }
        List<Ability> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Ability a : abilities.values()) {
            if (a.id.equals("utsusemi")) continue; // nur als Konter
            if (tick < readyAt.getOrDefault(a.id, 0)) continue;
            int w = a.weight(f.phase(), dist);
            if (w <= 0) continue;
            if (a.id.equals(last)) w = Math.max(1, w / 3);
            // Was in dieser Phase noch nicht zu sehen war, kommt bevorzugt (jede Phase zeigt ihr Repertoire)
            if (!shownThisPhase.contains(a.id)) w *= 3;
            pool.add(a);
            weights.add(w);
            total += w;
        }
        if (total == 0) return null;
        int r = Rand.range(0, total - 1);
        for (int i = 0; i < pool.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    // =================================================================== Ziel & Bewegung

    private void selectTarget() {
        List<LivingEntity> ts = f.targets();
        if (ts.isEmpty()) {
            target = null;
            return;
        }
        boolean valid = target != null && target.isValid() && !target.isDead() && ts.contains(target);
        if (valid && tick < targetUntil) return;
        // Bevorzugt: wer ihn zuletzt getroffen hat (Ehre dem Herausforderer), sonst der Nächste
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Location bl = boss.location();
        for (LivingEntity e : ts) {
            double d = MathUtil.distXZ(bl, e.getLocation());
            Integer hitAt = lastHitBy.get(e.getUniqueId());
            double score = d;
            if (hitAt != null && tick - hitAt < 100) score -= 6;
            if (e == target) score -= 2; // etwas Treue
            score += Rand.f() * 3;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        target = best;
        targetUntil = tick + 100 + Rand.range(0, 80);
    }

    private void move(Location bl, Location tl, double dist) {
        boss.face(tl);
        if (tick < nextMoveAt) return;
        double want = switch (f.phase()) {
            case SEI -> 4.5;
            case ARASHI -> 3.8;
            case SANGE -> 3.4;
        };
        double speed = switch (f.phase()) {
            case SEI -> 0.85;
            case ARASHI -> 1.1;
            case SANGE -> 1.25;
        };
        if (dist > want + 1.5) {
            // herangehen
            Vector dir = tl.toVector().subtract(bl.toVector()).setY(0).normalize();
            Location goal = tl.clone().subtract(dir.multiply(want - 0.5));
            boss.walkTo(f.clampDash(bl, goal), speed);
            nextMoveAt = tick + 8;
        } else if (dist < 2.2 && Rand.chance(0.5)) {
            // einen Schritt zurück
            Vector away = bl.toVector().subtract(tl.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = MathUtil.dirOfYaw(boss.yaw()).multiply(-1);
            Location goal = bl.clone().add(away.normalize().multiply(2.5));
            boss.walkTo(f.clampDash(bl, goal), speed * 0.8);
            nextMoveAt = tick + 14;
        } else {
            // seitlich umkreisen
            if (Rand.chance(0.25)) strafe = -strafe;
            float a = MathUtil.yawTo(tl, bl) + strafe * (25f + Rand.f() * 20f);
            Location goal = tl.clone().add(MathUtil.dirOfYaw(a).multiply(want));
            boss.walkTo(f.clampDash(bl, goal), speed * 0.7);
            nextMoveAt = tick + 20 + Rand.range(0, 15);
        }
    }

    public int readyIn(String id) {
        return Math.max(0, readyAt.getOrDefault(id, 0) - tick);
    }
}
