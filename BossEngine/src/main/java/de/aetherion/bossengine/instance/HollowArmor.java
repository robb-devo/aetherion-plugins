package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hollow Sun's war-plate. There is no body inside, only the plates, floating where a knight
 * would stand, around the star that burns in the open chest. Each plate chases its place with its
 * own lag, so the armor breathes, trails a swing and settles after it; the right gauntlet holds the
 * mace wherever the move puts it. The form sets how the plates sit: snug in the Main Sequence,
 * pushed apart around a swollen star as a Red Giant, crushed in close and trembling in the
 * Collapse. A broken guard drops every plate to the floor; they fly back home when it is over.
 * Block displays only: it needs no pack.
 */
final class HollowArmor {

    /** The arrival puts the plates on in this order, one beat each. */
    enum Group { LEGS, WAIST, TORSO, HEAD }

    private enum Kind { PLATE, TRIM, VISOR, LEFT_HAND, RIGHT_HAND }

    /** Offsets are for a 2.4-block body; x is toward its right hand. */
    private static final float BODY = 2.4f;
    private static final float GRAVITY = 0.06f;

    private final class Plate {
        final Group group;
        final Kind kind;
        final Vector3f rest;
        final Vector3f size;
        final Quaternionf tilt;
        final float lag;
        final int index;
        BlockDisplay display;
        final Vector pos = new Vector();
        final Vector vel = new Vector();
        final Quaternionf rot = new Quaternionf();
        final Vector3f spin = new Vector3f();
        Transformation sent;
        boolean shown;
        boolean placed;

        Plate(Group group, Kind kind, float x, float y, float z, float sx, float sy, float sz, Quaternionf tilt, float lag) {
            this.group = group;
            this.kind = kind;
            this.rest = new Vector3f(x, y, z);
            this.size = new Vector3f(sx, sy, sz);
            this.tilt = tilt;
            this.lag = lag;
            this.index = plates.size();
        }

        boolean upper() {
            return rest.y > 0.9f;
        }
    }

    private final BossKeys keys;
    private final UUID owner;
    private final List<Plate> plates = new ArrayList<>();
    private Material plate = Material.POLISHED_BLACKSTONE;
    private Material trim = Material.GOLD_BLOCK;
    private Material visor = Material.SHROOMLIGHT;
    private Color glow = Color.fromRGB(255, 196, 64);
    private float spread = 1f;
    private float tremble;
    private boolean collapsed;
    private double floorY;
    private int clock;

    HollowArmor(BossKeys keys, UUID owner) {
        this.keys = keys;
        this.owner = owner;
        Quaternionf none = new Quaternionf();
        // Legs: greaves.
        add(Group.LEGS, Kind.PLATE, 0.21f, 0.38f, 0f, 0.24f, 0.62f, 0.28f, none, 0.34f);
        add(Group.LEGS, Kind.PLATE, -0.21f, 0.38f, 0f, 0.24f, 0.62f, 0.28f, none, 0.34f);
        // Waist: faulds, angled out over the hips.
        add(Group.WAIST, Kind.PLATE, 0.24f, 0.98f, 0.12f, 0.3f, 0.36f, 0.1f, new Quaternionf().rotateX(0.18f), 0.4f);
        add(Group.WAIST, Kind.PLATE, -0.24f, 0.98f, 0.12f, 0.3f, 0.36f, 0.1f, new Quaternionf().rotateX(0.18f), 0.4f);
        // Torso: the breastplate is two halves that leave the chest open on the star, a backplate, pauldrons, gauntlets.
        add(Group.TORSO, Kind.PLATE, 0.32f, 1.5f, 0.17f, 0.34f, 0.78f, 0.12f, new Quaternionf().rotateY(0.38f), 0.5f);
        add(Group.TORSO, Kind.PLATE, -0.32f, 1.5f, 0.17f, 0.34f, 0.78f, 0.12f, new Quaternionf().rotateY(-0.38f), 0.5f);
        add(Group.TORSO, Kind.PLATE, 0f, 1.52f, -0.28f, 0.78f, 0.86f, 0.12f, none, 0.5f);
        add(Group.TORSO, Kind.PLATE, 0.6f, 1.93f, 0f, 0.42f, 0.28f, 0.56f, new Quaternionf().rotateZ(0.38f), 0.44f);
        add(Group.TORSO, Kind.PLATE, -0.6f, 1.93f, 0f, 0.42f, 0.28f, 0.56f, new Quaternionf().rotateZ(-0.38f), 0.44f);
        add(Group.TORSO, Kind.TRIM, 0.6f, 2.08f, 0f, 0.3f, 0.06f, 0.5f, new Quaternionf().rotateZ(0.38f), 0.44f);
        add(Group.TORSO, Kind.TRIM, -0.6f, 2.08f, 0f, 0.3f, 0.06f, 0.5f, new Quaternionf().rotateZ(-0.38f), 0.44f);
        add(Group.TORSO, Kind.RIGHT_HAND, 0.66f, 1.12f, 0.18f, 0.26f, 0.36f, 0.26f, none, 0.62f);
        add(Group.TORSO, Kind.LEFT_HAND, -0.66f, 1.12f, 0.18f, 0.26f, 0.36f, 0.26f, none, 0.48f);
        // Head: gorget, helm with a burning visor slit, a crest.
        add(Group.HEAD, Kind.TRIM, 0f, 1.98f, 0f, 0.66f, 0.12f, 0.46f, none, 0.56f);
        add(Group.HEAD, Kind.PLATE, 0f, 2.27f, 0.02f, 0.58f, 0.6f, 0.62f, none, 0.6f);
        add(Group.HEAD, Kind.VISOR, 0f, 2.3f, 0.34f, 0.46f, 0.08f, 0.03f, none, 0.6f);
        add(Group.HEAD, Kind.TRIM, 0f, 2.66f, -0.06f, 0.1f, 0.26f, 0.56f, none, 0.5f);
    }

    private void add(Group group, Kind kind, float x, float y, float z, float sx, float sy, float sz, Quaternionf tilt, float lag) {
        plates.add(new Plate(group, kind, x, y, z, sx, sy, sz, tilt, lag));
    }

    /* ------------------------------------------------------------------ lifecycle */

    /** Spawns every plate, hidden unless {@code dressed}; they come out of {@code core} when shown. */
    void spawn(Location core, boolean dressed) {
        remove();
        World world = core.getWorld();
        if (world == null) {
            return;
        }
        collapsed = false;
        for (Plate p : plates) {
            p.display = world.spawn(flat(core), BlockDisplay.class, d -> {
                d.setBlock(materialOf(p).createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(2);
                d.setInterpolationDuration(2);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f), new Quaternionf()));
                d.setViewRange(2.0f);
                d.setShadowRadius(0f);
                d.setPersistent(false);
                d.setGravity(false);
                if (p.kind == Kind.VISOR) {
                    d.setGlowing(true);
                    d.setGlowColorOverride(glow);
                }
                keys.tagBeamFx(d, owner);
            });
            p.pos.copy(core.toVector());
            p.vel.zero();
            p.sent = null;
            p.placed = false;
            p.shown = dressed;
        }
    }

    boolean spawned() {
        for (Plate p : plates) {
            if (p.display != null && p.display.isValid()) {
                return true;
            }
        }
        return false;
    }

    boolean intact() {
        for (Plate p : plates) {
            if (p.display == null || !p.display.isValid()) {
                return false;
            }
        }
        return true;
    }

    void remove() {
        for (Plate p : plates) {
            if (p.display != null && p.display.isValid()) {
                p.display.remove();
            }
            p.display = null;
        }
    }

    /** Puts one group of plates on: they fly out of {@code from} (the star) to their places. */
    void show(Group group, Location from) {
        for (Plate p : plates) {
            if (p.group == group && !p.shown) {
                p.shown = true;
                p.pos.copy(from.toVector());
                p.sent = null;
            }
        }
    }

    void showAll() {
        for (Plate p : plates) {
            p.shown = true;
        }
    }

    /** Center of a group's plates right now (sparks, sounds). */
    Location centerOf(Group group, World world) {
        Vector sum = new Vector();
        int n = 0;
        for (Plate p : plates) {
            if (p.group == group) {
                sum.add(p.pos);
                n++;
            }
        }
        return n == 0 ? null : sum.multiply(1.0 / n).toLocation(world);
    }

    /* ------------------------------------------------------------------ looks */

    /** The metal of a form: plate, trim, visor and the visor's glow. */
    void metal(Material plateMat, Material trimMat, Material visorMat, Color visorGlow, float spreadMul, float trembleAmt) {
        boolean changed = plateMat != plate || trimMat != trim || visorMat != visor;
        plate = plateMat;
        trim = trimMat;
        visor = visorMat;
        glow = visorGlow;
        spread = spreadMul;
        tremble = trembleAmt;
        if (!changed) {
            return;
        }
        for (Plate p : plates) {
            if (p.display != null && p.display.isValid()) {
                p.display.setBlock(materialOf(p).createBlockData());
                if (p.kind == Kind.VISOR) {
                    p.display.setGlowColorOverride(glow);
                }
            }
        }
    }

    private Material materialOf(Plate p) {
        return switch (p.kind) {
            case TRIM -> trim;
            case VISOR -> visor;
            default -> plate;
        };
    }

    /* ------------------------------------------------------------------ the broken guard */

    /** Every plate drops to the floor and lies there until {@link #reassemble}. */
    void collapse(double floor) {
        if (collapsed) {
            return;
        }
        collapsed = true;
        floorY = floor;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Plate p : plates) {
            double a = r.nextDouble(Math.PI * 2);
            double out = r.nextDouble(0.04, 0.14);
            p.vel.setX(Math.cos(a) * out).setY(r.nextDouble(0.05, 0.2)).setZ(Math.sin(a) * out);
            p.spin.set((float) r.nextDouble(-0.25, 0.25), (float) r.nextDouble(-0.2, 0.2), (float) r.nextDouble(-0.25, 0.25));
        }
    }

    /** The plates rise off the floor and fly back to their places. */
    void reassemble() {
        collapsed = false;
        for (Plate p : plates) {
            p.vel.zero();
        }
    }

    boolean collapsed() {
        return collapsed;
    }

    /* ------------------------------------------------------------------ motion */

    /**
     * One frame.
     *
     * @param feet   where the body stands; its yaw is the facing
     * @param height the body's height (scale included)
     * @param scale  extra rig scale (folding)
     * @param pitch  lean in degrees, forward positive
     * @param grip   where the right gauntlet holds the mace, or null to rest
     * @param raise  0..1: both arms up (casting, the slam wind-up)
     */
    void tick(Location feet, double height, double scale, float pitch, Location grip, double raise) {
        clock++;
        World world = feet.getWorld();
        if (world == null) {
            return;
        }
        float s = (float) (height / BODY * scale);
        Quaternionf face = new Quaternionf().rotateY((float) -Math.toRadians(feet.getYaw()));
        Quaternionf lean = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        Vector3f pivot = new Vector3f(0f, 0.95f * s, 0f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Plate p : plates) {
            if (p.display == null || !p.display.isValid()) {
                continue;
            }
            if (!p.shown) {
                push(p, p.pos, new Quaternionf(), 0.001f);
                continue;
            }
            if (collapsed) {
                fall(p, s);
                continue;
            }
            Vector3f local = new Vector3f(-p.rest.x, p.rest.y, p.rest.z);
            if (p.upper() && p.kind != Kind.RIGHT_HAND) {
                local.x *= spread;
                local.z *= spread;
            }
            if (p.kind == Kind.LEFT_HAND) {
                // Raised: the free hand comes up beside the helm.
                local.lerp(new Vector3f(0.42f, 2.25f, 0.3f), (float) raise);
            } else if (p.upper() && p.rest.y > 1.8f) {
                local.y += 0.12f * (float) raise;
            }
            local.y += (float) Math.sin(clock * 0.12 + p.index * 0.7) * 0.025f;
            local.mul(s);
            Quaternionf rot = new Quaternionf(face);
            if (p.upper()) {
                local.sub(pivot);
                lean.transform(local);
                local.add(pivot);
                rot.mul(lean);
            }
            rot.mul(p.tilt);
            Vector target;
            if (p.kind == Kind.RIGHT_HAND && grip != null) {
                target = grip.toVector();
            } else {
                Vector3f w = face.transform(local);
                target = feet.toVector().add(new Vector(w.x, w.y, w.z));
            }
            if (tremble > 0f) {
                target.add(new Vector(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).multiply(tremble * s));
            }
            if (!p.placed) {
                p.pos.copy(target);
                p.placed = true;
            } else {
                p.pos.add(target.clone().subtract(p.pos).multiply(p.lag));
            }
            p.rot.set(rot);
            push(p, p.pos, p.rot, s);
        }
    }

    private void fall(Plate p, float s) {
        double rest = floorY + p.size.y * s * 0.3;
        if (p.pos.getY() > rest || p.vel.getY() > 0) {
            p.vel.setY(p.vel.getY() - GRAVITY);
            p.pos.add(p.vel);
            p.rot.rotateX(p.spin.x).rotateY(p.spin.y).rotateZ(p.spin.z);
            if (p.pos.getY() <= rest && p.vel.getY() < 0) {
                p.pos.setY(rest);
                p.vel.zero();
                // Lies flat-ish where it fell.
                p.rot.set(new Quaternionf().rotateY(p.rot.y * 3f).rotateX(1.45f));
            }
        }
        push(p, p.pos, p.rot, s);
    }

    private void push(Plate p, Vector pos, Quaternionf rot, float s) {
        BlockDisplay d = p.display;
        Location at = new Location(d.getWorld(), pos.getX(), pos.getY(), pos.getZ());
        if (d.getLocation().toVector().distanceSquared(pos) > 1.0E-6) {
            d.teleport(at);
        }
        Vector3f size = s <= 0.002f ? new Vector3f(0.001f) : new Vector3f(p.size).mul(s);
        Vector3f corner = new Quaternionf(rot).transform(new Vector3f(size).mul(-0.5f));
        Transformation t = new Transformation(corner, new Quaternionf(rot), size, new Quaternionf());
        if (p.sent != null && p.sent.getScale().distanceSquared(size) < 1.0E-6f
                && Math.abs(p.sent.getLeftRotation().dot(t.getLeftRotation())) > 0.99999f) {
            return;
        }
        p.sent = t;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(t);
    }

    private static Location flat(Location at) {
        Location out = at.clone();
        out.setYaw(0f);
        out.setPitch(0f);
        return out;
    }
}
