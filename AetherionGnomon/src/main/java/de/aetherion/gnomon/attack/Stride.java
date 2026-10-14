package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.Posture;
import de.aetherion.gnomon.encounter.Attack;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.math.Geo;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.List;

/**
 * RESET. It walks. While observing it paces around you and looks from player to player; otherwise it closes to
 * striking distance. Every footstep is telegraphed and hurts where it lands - the body itself is a hazard.
 */
public final class Stride extends Attack {

    private int maxTicks;
    private boolean observing;
    private int lookIndex;

    public Stride(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        Player f = e.player(d.target());
        if (f == null) {
            f = e.focus();
        }
        observing = e.brain().posture() == Posture.OBSERVE;
        double hx = e.rig().hubX();
        double hz = e.rig().hubZ();
        double tx;
        double tz;
        if (f == null) {
            tx = e.arena().cx();
            tz = e.arena().cz();
        } else {
            double fx = f.getLocation().getX();
            double fz = f.getLocation().getZ();
            double a = Geo.angle(hx - fx, hz - fz);
            if (observing) {
                // Pace around them: keep the distance, shift the angle.
                double r = Math.max(8, Geo.dist(hx, hz, fx, fz));
                double side = Math.random() < 0.5 ? 1 : -1;
                a += side * Math.toRadians(40);
                tx = fx + Math.cos(a) * r;
                tz = fz + Math.sin(a) * r;
            } else {
                tx = fx + Math.cos(a) * 7.5;
                tz = fz + Math.sin(a) * 7.5;
            }
        }
        double[] c = e.arena().clamp(tx, tz, 7);
        e.rig().moveTo(c[0], c[1]);
        if (f != null) {
            e.rig().face(f.getLocation().getX(), f.getLocation().getZ());
        }
        maxTicks = 80;
        Vector3d h = e.rig().hub();
        e.fx().sound(h.x, h.y, h.z, Sound.BLOCK_GRINDSTONE_USE, 0.8f, 0.5f);
    }

    @Override
    public boolean tick() {
        if (observing && t % 14 == 0) {
            List<Player> members = e.party().online();
            if (!members.isEmpty()) {
                Player p = members.get(lookIndex++ % members.size());
                e.rig().look(Encounter.head(p));
                Vector3d lens = e.rig().lensPos();
                e.fx().soundTo(p, lens.x, lens.y, lens.z, Sound.ITEM_SPYGLASS_USE, 0.5f, 1.3f);
            }
        } else if (!observing) {
            Player f = e.player(d.target());
            if (f != null) {
                e.rig().look(Encounter.head(f));
                e.rig().face(f.getLocation().getX(), f.getLocation().getZ());
            }
        }
        if (t > 20 && e.rig().settled()) {
            return true;
        }
        return t >= maxTicks;
    }

    @Override
    public boolean ownsLens() {
        return false;
    }

    @Override
    protected void cleanup() {
    }
}
