package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.Lang;
import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.fx.Shapes;
import de.aetherion.gnomon.geom.Zone;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * BAIT. "The boss refuses the opening you always greed."
 *
 * <p>Same drop, same gold - but the lens does not dim and does not droop. It stays red and stares at you. A greedy
 * player who swings anyway gets a ground shockwave from the heart (jumpable, with its own warning ring). A player who
 * read the eye and held back out-thinks it: after the snap it staggers into a real window.
 */
public final class FalseOpening extends PlumbDrop {

    private int snapAt = -1;
    private int fireAt = -1;
    private Shapes.Ring ring;
    private boolean outplayed;
    private boolean fired;
    private double bx;
    private double bz;

    public FalseOpening(Encounter e, Decision d) {
        super(e, d, true);
    }

    @Override
    protected void onLie() {
        e.announceExploit(d, cx, cz);
        int snap = e.cfg().telegraph("false-opening-snap", 22);
        e.openFalse(d.target(), snap);
        snapAt = t + snap;
    }

    @Override
    protected boolean afterLanding() {
        if (!fired && ring == null && t >= snapAt) {
            Vector3d b = e.rig().bob();
            bx = b.x;
            bz = b.z;
            UUID target = d.target();
            boolean bit = e.falseBit();
            outplayed = e.brain().resolveFalseOpening(target, bit);
            Player p = e.player(target);
            if (bit && p != null) {
                e.fx().subtitle(p, Lang.falseOpening(p), 40);
            }
            int warn = Math.max(e.cfg().minCommit(), e.cfg().telegraph("trap-warning", 12));
            ring = e.shapes().ring(bx, bz, 1.6, 6.0, Shapes.Style.COMMITTED);
            ring.grow(0.1, 0);
            ring.grow(1.0, warn);
            fireAt = t + warn;
            e.rig().pendulum().pin(true);
            e.fx().sound(bx, e.floorY() + 1, bz, Sound.BLOCK_VAULT_DEACTIVATE, 2f, 0.5f);
            e.fx().sound(bx, e.floorY() + 1, bz, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.6f, 0.8f);
        }
        if (!fired && fireAt >= 0 && t >= fireAt) {
            fired = true;
            e.strike(new Zone.Ring(bx, bz, 1.6, 6.0), e.cfg().damage("trap-ring", 68), "trap", 20, true, 1.1);
            e.closeOpening();
            e.rig().pendulum().reel(e.rig().chainRest(), 0.5);
            e.rig().pendulum().kick(0, 0.9, 0);
            e.fx().ring(Particle.SONIC_BOOM, bx, e.floorY() + 0.6, bz, 4, 8);
            e.fx().sound(bx, e.floorY() + 1, bz, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
            e.fx().sound(bx, e.floorY() + 1, bz, Sound.BLOCK_BELL_USE, 2f, 0.6f);
            e.fx().shakeNear(bx, bz, 12, 14);
            if (ring != null) {
                ring.remove();
                ring = null;
            }
        }
        return fired && t >= fireAt + 6;
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        if (ring != null) {
            ring.remove();
        }
        if (!fired && landedAt >= 0) {
            e.closeOpening();
        }
        if (outplayed) {
            // Held back while it lied: the real window, earned.
            e.stagger();
        }
    }
}
