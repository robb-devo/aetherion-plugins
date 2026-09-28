package de.aetherion.bossengine.helios.requiem;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.Attack;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * GRAVITY TETHER (singularity only). Helios hunts alongside the hole: it swings its rings shut, reads a
 * name off the party and binds that player to the black hole.
 *
 * <pre>
 *   MARK   (1 beat)  a violet hairline runs from the core to the chosen player; a low note only they hear,
 *                    a ring closes around their feet. The one farthest from the hole is chosen first.
 *   BIND   (3 beats) the hairline becomes a chain of dark light that follows them. The hole hauls on them
 *                    much harder (a sprint outward just about holds; walking does not). It burns once
 *                    halfway through. Helios holds the other end from its mask, rings spinning.
 *   SNAP             the chain breaks with a crack of thunder; whoever is still inside the horizon takes
 *                    the horizon's burn as usual.
 * </pre>
 * Below 15 % (or enraged) it binds two players at once when it can.
 */
final class GravityTether extends Attack {

    private static final Color VIOLET = Color.fromRGB(170, 80, 255);

    private final HeliosScript h;
    private final List<Player> bound = new ArrayList<>();
    private final List<Shapes.Line> marks = new ArrayList<>();
    private final List<Shapes.Beam> chains = new ArrayList<>();
    private final List<Shapes.Ring> collars = new ArrayList<>();
    /** Helios holds the other end: a hairline from its mask to each bound player. */
    private final List<Shapes.Line> reins = new ArrayList<>();
    private int mark;
    private int bind;

    GravityTether(HeliosScript h) {
        super(h);
        this.h = h;
    }

    @Override
    public String id() {
        return "tether";
    }

    @Override
    public Family family() {
        return Family.BODY;
    }

    @Override
    public void start() {
        mark = enc.tempo().ticks(1);
        bind = enc.tempo().ticks(3);
        List<Player> f = new ArrayList<>(enc.fighters());
        // Farthest from the hole first: nobody gets to hide at the edge.
        f.sort((a, b) -> Float.compare(dist(b), dist(a)));
        int want = (h.instance().healthPercent() < 15.0 || enc.enraged()) ? 2 : 1;
        for (int i = 0; i < Math.min(want, f.size()); i++) {
            bound.add(f.get(i));
        }
        for (Player p : bound) {
            Vector3f chest = chest(p);
            Shapes.Line l = new Shapes.Line(stage, g, Material.PURPLE_STAINED_GLASS, VIOLET);
            l.set(Singularity.CENTER, chest, 0.04f, 0);
            marks.add(l);
            Shapes.Beam b = new Shapes.Beam(stage, g, Material.CRYING_OBSIDIAN, Material.PURPLE_STAINED_GLASS, VIOLET);
            b.hide(0);
            chains.add(b);
            Shapes.Ring c = new Shapes.Ring(stage, g, 12, Material.PURPLE_STAINED_GLASS, VIOLET, 15, true);
            Vector3f feet = stage.feet(p);
            c.flat(new Vector3f(feet.x, 0.06f, feet.z), 2.2f, 0.1f, 0.04f, 0f, 0);
            collars.add(c);
            Shapes.Line r = new Shapes.Line(stage, g, Material.MAGENTA_STAINED_GLASS, VIOLET);
            r.set(h.rig().maskPoint(), chest, 0.02f, 0);
            reins.add(r);
            enc.score().to(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            enc.score().to(p, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.9f, 1.3f);
            p.sendActionBar(TextUtil.component("&5&lHelios binds you to the hole. &dSprint outward!"));
        }
        enc.score().at(h.rig().center, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.5f);
        h.rig().ringScale(0.55f);
        h.rig().spin(2.2f);
    }

    private float dist(Player p) {
        Vector3f f = stage.feet(p);
        return HMath.horizontal(new Vector3f(f).sub(Singularity.CENTER));
    }

    private Vector3f chest(Player p) {
        return stage.feet(p).add(0f, 1.1f, 0f);
    }

    @Override
    protected boolean tick() {
        Singularity sing = h.singularity();
        if (sing == null || bound.isEmpty()) {
            return true;
        }
        int snap = mark + bind;
        for (int i = 0; i < bound.size(); i++) {
            Player p = bound.get(i);
            boolean alive = p.isValid() && !p.isDead() && enc.fighters().contains(p);
            if (!alive) {
                sing.tether(p, 0f);
                chains.get(i).hide(2);
                marks.get(i).hide(Singularity.CENTER, 2);
                collars.get(i).hide(Singularity.CENTER, 2);
                reins.get(i).hide(h.rig().maskPoint(), 2);
                continue;
            }
            Vector3f chest = chest(p);
            Vector3f feet = stage.feet(p);
            if (t < snap) {
                reins.get(i).set(h.rig().maskPoint(), chest, t < mark ? 0.02f : 0.05f, HeliosStage.SMOOTH_1);
            } else if (t == snap) {
                reins.get(i).hide(chest, 3);
            }
            if (t < mark) {
                // The collar closes on the beat: step out of nothing, it follows you.
                float f = HMath.window(t, 0, mark);
                marks.get(i).set(Singularity.CENTER, chest, 0.04f + 0.04f * f, HeliosStage.SMOOTH_1);
                collars.get(i).flat(new Vector3f(feet.x, 0.06f, feet.z), HMath.lerp(2.2f, 0.7f, f), 0.1f, 0.04f, t * 0.2f, HeliosStage.SMOOTH_1);
            } else if (t == mark) {
                marks.get(i).hide(chest, 2);
                collars.get(i).hide(feet, 3);
                chains.get(i).set(Singularity.CENTER, chest, 0.35f, 0);
                sing.tether(p, (float) enc.config().d("helios.tether.pull", 0.03));
                enc.score().at(feet, Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.5f);
                enc.score().at(feet, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.4f);
                enc.camera().shake(p, 10, 2);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
            } else if (t < snap) {
                float beat = enc.star() == null ? 0f : enc.star().beat();
                chains.get(i).set(Singularity.CENTER, chest, 0.3f + 0.12f * beat, 1);
                if (t - mark == bind / 2) {
                    // One burn halfway through: the pull is the threat, not a damage-over-time drain.
                    enc.hit(p, h.power("tether", 14), "tether", 18, null);
                    enc.score().to(p, Sound.BLOCK_CHAIN_HIT, 1f, 0.6f);
                }
            }
        }
        if (t >= mark && t < snap) {
            // Helios stares its prey down.
            Player first = bound.get(0);
            if (first.isValid()) {
                h.rig().lookAt(chest(first), 0.25f);
            }
            if (enc.tempo().onBeat()) {
                enc.score().at(Singularity.CENTER, Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.5f);
                h.rig().heartSize(1.3f);
            } else if (t % 6 == 0) {
                h.rig().heartSize(1f);
            }
        }
        if (t == snap) {
            for (int i = 0; i < bound.size(); i++) {
                Player p = bound.get(i);
                sing.tether(p, 0f);
                Shapes.Beam b = chains.get(i);
                b.set(b.from(), b.to(), 0.8f, 1);
                if (p.isValid()) {
                    enc.score().at(stage.feet(p), Sound.BLOCK_CHAIN_BREAK, 1.3f, 0.6f);
                    enc.score().at(stage.feet(p), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.4f);
                }
            }
            enc.camera().flash(1, 3);
            h.rig().ringScale(1f);
            h.rig().spin(1f);
            h.rig().heartSize(1f);
        }
        if (t == snap + 2) {
            for (Shapes.Beam b : chains) {
                b.thin(3);
            }
        }
        if (t == snap + 6) {
            for (Shapes.Beam b : chains) {
                b.hide(2);
            }
        }
        return t >= snap + 8;
    }

    @Override
    protected void cleanup() {
        Singularity sing = h.singularity();
        for (Player p : bound) {
            if (sing != null) {
                sing.tether(p, 0f);
            }
        }
        h.rig().ringScale(1f);
        h.rig().spin(1f);
        h.rig().heartSize(1f);
    }

    @Override
    public int recovery() {
        return enc.tempo().ticks(1);
    }
}
