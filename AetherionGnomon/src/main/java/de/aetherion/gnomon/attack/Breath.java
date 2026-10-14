package de.aetherion.gnomon.attack;

import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.encounter.Attack;
import de.aetherion.gnomon.encounter.Encounter;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.List;

/**
 * A quiet beat. No attack. The hub settles, the needle rests, and the lens goes from face to face. Silence is part of
 * the score: it makes the next sound land.
 */
public final class Breath extends Attack {

    private int length;
    private int index;

    public Breath(Encounter e, Decision d) {
        super(e, d);
    }

    @Override
    public void start() {
        length = Math.max(20, e.cfg().breathTicks());
        e.rig().crouch(0.5);
        e.rig().needleRest();
        Vector3d h = e.rig().hub();
        e.fx().sound(h.x, h.y, h.z, Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.7f, 0.5f);
    }

    @Override
    public boolean tick() {
        if (t % 12 == 0) {
            List<Player> members = e.party().online();
            if (!members.isEmpty()) {
                Player p = members.get(index++ % members.size());
                e.rig().look(Encounter.head(p));
            }
        }
        return t >= length;
    }

    @Override
    public boolean ownsLens() {
        return false;
    }

    @Override
    protected void cleanup() {
        e.rig().crouch(0);
    }
}
