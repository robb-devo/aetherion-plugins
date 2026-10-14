package de.aetherion.gnomon.rig;

import de.aetherion.gnomon.math.Ease;
import de.aetherion.gnomon.math.Geo;
import de.aetherion.gnomon.math.Spring;
import de.aetherion.gnomon.render.Part;
import de.aetherion.gnomon.render.Parts;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.function.Consumer;

/**
 * The Gnomon's body: an octagonal hub on three two-bone needle legs with procedural gait, a lens that looks first,
 * a needle that casts the Hour Hand, and a plumb bob on a chain that is both its heart and its hitbox.
 *
 * <p>Built from ~33 block displays posed by transformation only. Body motion is spring-driven (weight), the eye is
 * snappy (intent), the bob is simulated (secondary motion).
 */
public final class Tripod {

    /** Rig dimensions and feel. */
    public record Spec(double hubHeight, double hipRadius, double thigh, double shin, double stance,
                       double stepThreshold, int stepTicks, double stepLift, double maxSpeed, double turnSpeedDeg,
                       double bobRestHeight, double gravity, double damping, int updateInterval) {
    }

    private static final double[] LEG_OFFSETS = {Math.toRadians(60), Math.toRadians(180), Math.toRadians(300)};
    private static final int CHAIN_SEGMENTS = 6;
    private static final double SPIKE = 1.5;

    private final Spec spec;
    private final double floorY;
    private final Parts parts;

    // ---- state
    private final Spring hub = new Spring(0.16);
    /** Hub as drawn: spring position plus the plant dip (fast, so impacts read). */
    private final Vector3d hubDrawn = new Vector3d();
    private final Vector3d hubGoal = new Vector3d();
    private final Spring.Scalar yaw = new Spring.Scalar(0.25, 0);
    private double yawGoal;
    private final Spring.Scalar height;
    private final Spring.Scalar dip = new Spring.Scalar(0.35, 0);
    private final Spring lean = new Spring(0.2);
    private final Vector3d leanGoal = new Vector3d();
    private double crouch;
    private double rear;
    private boolean gait = true;
    private final Leg[] legs = new Leg[3];
    private final Pendulum bob;
    private double chainRest;

    private final Spring look = new Spring(0.5);
    private final Vector3d lookGoal = new Vector3d();
    private final Spring.Scalar needleTilt = new Spring.Scalar(0.2, Math.toRadians(14));
    private final Spring.Scalar needleAngle = new Spring.Scalar(0.2, 0);
    private double needleTiltGoal = Math.toRadians(14);
    private double needleAngleGoal = Math.PI;
    private double needleLength = 7.0;
    private double lensScale = 1.0;
    private double hubScale = 1.0;
    private boolean beam;
    private double gazeSize = 1.4;
    private int freeze;
    private int clock;
    private Consumer<Vector3d> onAutoPlant;
    private java.util.function.BiConsumer<Vector3d, Integer> onStepStart;
    private boolean posed;
    private boolean vanished;

    // ---- parts
    private Part body;
    private Part body45;
    private Part collar;
    private Part rimA;
    private Part rimB;
    private Part iris;
    private Part gaze;
    private Part beamPart;
    private Part needle;
    private final Part[] chain = new Part[CHAIN_SEGMENTS];
    private Part bobBody;
    private Part bobCap;
    private Part bobTip;
    private final Part[] thighs = new Part[3];
    private final Part[] shins = new Part[3];
    private final Part[] knees = new Part[3];
    private final Part[] spikes = new Part[3];
    private final Part[] ferrules = new Part[3];

    private Material irisMaterial = Material.WHITE_CONCRETE;
    private Material gazeMaterial = Material.WHITE_STAINED_GLASS;
    private boolean bobGold;
    private boolean lensDim;

    public Tripod(Spec spec, Parts parts, double floorY, double x, double z, double startYaw) {
        this.spec = spec;
        this.parts = parts;
        this.floorY = floorY;
        this.height = new Spring.Scalar(0.12, spec.hubHeight());
        this.bob = new Pendulum(spec.gravity(), spec.damping());
        hub.maxSpeed(spec.maxSpeed());
        yaw.snap(startYaw);
        yawGoal = startYaw;
        Vector3d start = new Vector3d(x, floorY + spec.hubHeight(), z);
        hub.snap(start);
        hubDrawn.set(start);
        hubGoal.set(x, 0, z);
        for (int i = 0; i < 3; i++) {
            legs[i] = new Leg(i, LEG_OFFSETS[i]);
            legs[i].foot.set(ideal(i, x, z, startYaw));
        }
        chainRest = Math.max(1.5, spec.hubHeight() - 0.8 - spec.bobRestHeight());
        bob.place(new Vector3d(x, start.y - 0.8, z), chainRest, floorY);
        look.snap(new Vector3d(x + Math.cos(startYaw) * 10, floorY + 1.5, z + Math.sin(startYaw) * 10));
        lookGoal.set(look.pos());
        build();
    }

    private void build() {
        if (parts.world() == null) {
            return; // headless (simulation / tests)
        }
        body = parts.spawn(Material.POLISHED_BLACKSTONE_BRICKS);
        body45 = parts.spawn(Material.POLISHED_BLACKSTONE_BRICKS);
        collar = parts.spawn(Material.WAXED_CUT_COPPER);
        rimA = parts.spawn(Material.WHITE_STAINED_GLASS, 15);
        rimB = parts.spawn(Material.WHITE_STAINED_GLASS, 15);
        iris = parts.spawn(irisMaterial, 15);
        gaze = parts.spawn(gazeMaterial, 15);
        beamPart = parts.spawn(Material.WHITE_STAINED_GLASS, 15);
        needle = parts.spawn(Material.END_ROD, 15);
        for (int i = 0; i < CHAIN_SEGMENTS; i++) {
            chain[i] = parts.spawn(Material.CHAIN);
        }
        bobBody = parts.spawn(Material.LODESTONE);
        bobCap = parts.spawn(Material.WAXED_CUT_COPPER);
        bobTip = parts.spawn(dripstone(), -1);
        for (int i = 0; i < 3; i++) {
            thighs[i] = parts.spawn(Material.POLISHED_BLACKSTONE);
            shins[i] = parts.spawn(Material.POLISHED_DEEPSLATE);
            knees[i] = parts.spawn(Material.WAXED_CUT_COPPER);
            ferrules[i] = parts.spawn(Material.WAXED_CUT_COPPER);
            spikes[i] = parts.spawn(dripstone(), -1);
        }
    }

    private static BlockData dripstone() {
        try {
            return Bukkit.createBlockData("minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]");
        } catch (IllegalArgumentException e) {
            return Material.POINTED_DRIPSTONE.createBlockData();
        }
    }

    /* ================================================================== queries */

    public Vector3d hub() {
        return new Vector3d(hubDrawn);
    }

    public double hubX() {
        return hubDrawn.x;
    }

    public double hubZ() {
        return hubDrawn.z;
    }

    public double yaw() {
        return yaw.value();
    }

    public Vector3d bob() {
        return new Vector3d(bob.pos());
    }

    public Pendulum pendulum() {
        return bob;
    }

    public double floorY() {
        return floorY;
    }

    public Leg leg(int i) {
        return legs[i];
    }

    /** Hip and knee of a leg as last solved (for tests / debugging). */
    public Vector3d[] legJoints(int i) {
        Leg leg = legs[i];
        double a = yaw.value() + leg.offset;
        Vector3d hip = new Vector3d(hubDrawn.x + Math.cos(a) * spec.hipRadius() * hubScale, hubDrawn.y - 0.55 * hubScale,
                hubDrawn.z + Math.sin(a) * spec.hipRadius() * hubScale);
        Vector3d ankle = new Vector3d(leg.foot.x, leg.foot.y + SPIKE, leg.foot.z);
        double oa = Geo.angle(ankle.x - hubDrawn.x, ankle.z - hubDrawn.z);
        Vector3d knee = new Vector3d();
        Leg.solve(hip, ankle, spec.thigh() * hubScale, spec.shin() * hubScale, new Vector3d(Math.cos(oa), 0.85, Math.sin(oa)), knee);
        return new Vector3d[]{hip, knee, ankle};
    }

    public Vector3d lensPos() {
        Vector3d h = hubDrawn;
        double y = yaw.value();
        return new Vector3d(h.x + Math.cos(y) * 1.45 * hubScale, h.y + 0.25, h.z + Math.sin(y) * 1.45 * hubScale);
    }

    /** Where the lens is looking on the floor (the gaze spot). */
    public Vector3d gazeSpot() {
        Vector3d lens = lensPos();
        Vector3d dir = new Vector3d(look.pos()).sub(lens);
        if (dir.y < -0.05) {
            double t = (floorY - lens.y) / dir.y;
            return new Vector3d(lens).add(dir.mul(t));
        }
        Vector3d p = new Vector3d(look.pos());
        p.y = floorY;
        return p;
    }

    /** Which leg would naturally reach (x, z): the one whose rest direction points most at it. */
    public int legToward(double x, double z) {
        double a = Geo.angle(x - hubX(), z - hubZ());
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            double diff = Geo.angleDiff(a, yaw.value() + legs[i].offset);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    /** Max horizontal distance a foot can land from the hub's floor point right now. */
    public double reach() {
        double hipDrop = Math.max(1, hubDrawn.y - 0.55 - floorY);
        double len = spec.thigh() + spec.shin() - 0.6;
        return spec.hipRadius() + Math.sqrt(Math.max(1, len * len - hipDrop * hipDrop));
    }

    /** Pulls (x, z) inside reach of the hub. Telegraphs must use the clamped point. */
    public Vector3d clampToReach(double x, double z) {
        double r = Geo.dist(x, z, hubX(), hubZ());
        double max = reach() - 0.5;
        if (r <= max || r < 1e-6) {
            return new Vector3d(x, floorY, z);
        }
        double f = max / r;
        return new Vector3d(hubX() + (x - hubX()) * f, floorY, hubZ() + (z - hubZ()) * f);
    }

    /* ================================================================== commands */

    public void moveTo(double x, double z) {
        hubGoal.set(x, 0, z);
    }

    public Vector3d hubGoal() {
        return new Vector3d(hubGoal);
    }

    public void face(double x, double z) {
        if (Geo.dist(x, z, hubX(), hubZ()) > 0.5) {
            yawGoal = Geo.angle(x - hubX(), z - hubZ());
        }
    }

    public void faceAngle(double angle) {
        yawGoal = angle;
    }

    public void look(Vector3d point) {
        lookGoal.set(point);
    }

    public void look(double x, double y, double z) {
        lookGoal.set(x, y, z);
    }

    /** Snaps the eye at once (a tell: it moved first). */
    public void snapLook(Vector3d point) {
        lookGoal.set(point);
        look.vel().zero();
        look.pos().lerp(point, 0.75);
    }

    public void lean(double dx, double dz) {
        leanGoal.set(dx, 0, dz);
    }

    public void crouch(double blocks) {
        crouch = blocks;
    }

    public void rear(double blocks) {
        rear = blocks;
    }

    public void dip(double impulse) {
        dip.kick(-impulse);
    }

    public void gait(boolean on) {
        gait = on;
    }

    public void freeze(int ticks) {
        freeze = Math.max(freeze, ticks);
    }

    public void beam(boolean on) {
        beam = on;
    }

    public void gazeSize(double size) {
        gazeSize = size;
    }

    public void needle(double tiltRad, double angle) {
        needleTiltGoal = tiltRad;
        needleAngleGoal = angle;
    }

    public void needleRest() {
        needle(Math.toRadians(14), yaw.value() + Math.PI);
    }

    public double needleAngle() {
        return needleAngle.value();
    }

    public void needleLength(double length) {
        needleLength = length;
    }

    public void lensScale(double s) {
        lensScale = s;
    }

    public void hubScale(double s) {
        hubScale = s;
    }

    public void onAutoPlant(Consumer<Vector3d> callback) {
        this.onAutoPlant = callback;
    }

    /** Called when the gait lifts a foot: (landing point, ticks until it lands). */
    public void onStepStart(java.util.function.BiConsumer<Vector3d, Integer> callback) {
        this.onStepStart = callback;
    }

    public void iris(Material m) {
        irisMaterial = m;
        if (iris != null) {
            iris.block(m.createBlockData());
        }
    }

    public void gazeColor(Material m) {
        gazeMaterial = m;
        if (gaze != null) {
            gaze.block(m.createBlockData());
        }
        if (beamPart != null) {
            beamPart.block(m.createBlockData());
        }
    }

    public void lensDim(boolean dim) {
        lensDim = dim;
        if (rimA == null) {
            return;
        }
        int b = dim ? 3 : 15;
        rimA.brightness(b);
        rimB.brightness(b);
        iris.brightness(dim ? 2 : 15);
        gaze.brightness(dim ? 4 : 15);
    }

    public void bobGold(boolean gold, Color glow) {
        bobGold = gold;
        if (bobBody == null) {
            return;
        }
        bobBody.block((gold ? Material.GOLD_BLOCK : Material.LODESTONE).createBlockData());
        bobBody.brightness(gold ? 15 : -1);
        bobBody.glow(gold ? glow : null);
        bobCap.glow(gold ? glow : null);
    }

    public void lensCracked() {
        if (rimA == null) {
            return;
        }
        rimA.block(Material.GRAY_STAINED_GLASS.createBlockData());
        rimB.block(Material.BLACK_STAINED_GLASS.createBlockData());
        iris.block(Material.BLACK_CONCRETE.createBlockData());
        lensDim(true);
        beam = false;
        gazeSize = 0;
    }

    /** Raise a leg and hover its foot over {@code hover} (attack windup). */
    public void holdLeg(int i, Vector3d hover) {
        legs[i].hold(hover);
    }

    /** Drive a leg's foot down onto {@code target} in {@code ticks}. */
    public void slamLeg(int i, Vector3d target, int ticks, Consumer<Vector3d> onLand) {
        Vector3d t = clampToReach(target.x, target.z);
        t.y = floorY;
        legs[i].slam(t, ticks, onLand);
    }

    /** Put a foot down gently where it is (feints, releases). */
    public void releaseLeg(int i, int ticks) {
        Leg leg = legs[i];
        if (leg.mode == Leg.Mode.HELD || leg.mode == Leg.Mode.SLAM) {
            Vector3d down = new Vector3d(leg.foot.x, floorY, leg.foot.z);
            Vector3d t = clampToReach(down.x, down.z);
            leg.step(t, ticks, 0.0, null);
        }
    }

    /** Death: feet slide outward and the leg gives up. */
    public void limp(int i, double slide, int ticks) {
        Leg leg = legs[i];
        double a = Geo.angle(leg.foot.x - hubX(), leg.foot.z - hubZ());
        Vector3d t = new Vector3d(leg.foot.x + Math.cos(a) * slide, floorY, leg.foot.z + Math.sin(a) * slide);
        leg.step(t, ticks, 0.6, null);
        leg.mode = Leg.Mode.STEPPING;
    }

    public boolean anyLegBusy() {
        for (Leg l : legs) {
            if (l.mode != Leg.Mode.PLANTED) {
                return true;
            }
        }
        return false;
    }

    public boolean settled() {
        return !anyLegBusy() && Geo.dist(hubX(), hubZ(), hubGoal.x, hubGoal.z) < 0.6;
    }

    /* ================================================================== tick */

    public void tick() {
        clock++;
        if (freeze > 0) {
            freeze--;
            return;
        }
        double y = yaw.stepAngle(yawGoal, Math.toRadians(spec.turnSpeedDeg()));

        // Weight shift: a raised leg lowers the hub and leans it away.
        int raised = 0;
        double shiftX = 0;
        double shiftZ = 0;
        for (Leg leg : legs) {
            if (leg.mode == Leg.Mode.HELD || leg.mode == Leg.Mode.STEPPING) {
                raised++;
                double a = Geo.angle(leg.foot.x - hubX(), leg.foot.z - hubZ());
                shiftX -= Math.cos(a) * 0.35;
                shiftZ -= Math.sin(a) * 0.35;
            }
        }
        Vector3d leanNow = lean.step(new Vector3d(leanGoal.x + shiftX, 0, leanGoal.z + shiftZ));
        double breathe = Math.sin(clock * 0.06) * 0.12;
        double h = height.step(spec.hubHeight() - crouch + rear - raised * 0.35 + breathe);
        Vector3d target = new Vector3d(hubGoal.x + leanNow.x, floorY + h, hubGoal.z + leanNow.z);
        Vector3d hp = hub.step(target);
        double d = dip.step(0);
        hubDrawn.set(hp.x, hp.y + d, hp.z);

        // Gait (plant callbacks fire from inside advance()).
        if (gait) {
            stepGait(y);
        }
        for (Leg leg : legs) {
            leg.advance();
        }

        // Pendulum.
        Vector3d anchor = new Vector3d(hubDrawn.x, hubDrawn.y - 0.8 * hubScale, hubDrawn.z);
        bob.step(anchor);

        // Eye.
        look.step(lookGoal);
        needleTilt.step(needleTiltGoal);
        needleAngle.stepAngle(needleAngleGoal, Math.toRadians(6));

        if (vanished) {
            return;
        }
        if (clock % Math.max(1, spec.updateInterval()) == 0 || !posed) {
            pose(spec.updateInterval() + 1);
            posed = true;
        }
    }

    /** Folds every part to nothing over {@code interp} ticks and stops posing (end of the death). */
    public void vanish(int interp) {
        if (vanished) {
            return;
        }
        vanished = true;
        Part[] all = {body, body45, collar, rimA, rimB, iris, gaze, beamPart, needle, bobBody, bobCap, bobTip};
        for (Part p : all) {
            if (p != null) {
                p.hide(interp);
            }
        }
        for (Part[] group : new Part[][]{chain, thighs, shins, knees, spikes, ferrules}) {
            for (Part p : group) {
                if (p != null) {
                    p.hide(interp);
                }
            }
        }
    }

    private void stepGait(double y) {
        int stepping = 0;
        for (Leg leg : legs) {
            if (leg.mode == Leg.Mode.STEPPING) {
                stepping++;
            }
        }
        Vector3d vel = hub.vel();
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        int dur = (int) Math.round(Ease.clamp(spec.stepTicks() - speed * 30, 7, spec.stepTicks()));
        Leg worst = null;
        double worstDist = 0;
        for (Leg leg : legs) {
            if (leg.mode != Leg.Mode.PLANTED) {
                continue;
            }
            Vector3d ideal = ideal(leg.index, hubX(), hubZ(), y);
            double dist = Geo.dist(leg.foot.x, leg.foot.z, ideal.x, ideal.z);
            if (dist > worstDist) {
                worstDist = dist;
                worst = leg;
            }
        }
        if (worst == null || worstDist < spec.stepThreshold()) {
            return;
        }
        boolean emergency = worstDist > spec.stepThreshold() * 1.7;
        if (stepping >= 1 && !(emergency && stepping < 2)) {
            return;
        }
        // Land where it will want to be when the step ends (predict the hub).
        double px = hubX() + vel.x * dur;
        double pz = hubZ() + vel.z * dur;
        Vector3d to = ideal(worst.index, px, pz, y);
        to = clampToReach(to.x, to.z);
        if (onStepStart != null) {
            onStepStart.accept(new Vector3d(to), dur);
        }
        worst.step(to, dur, spec.stepLift(), landed -> {
            dip(0.18);
            if (onAutoPlant != null) {
                onAutoPlant.accept(landed);
            }
        });
    }

    private Vector3d ideal(int i, double hx, double hz, double y) {
        double a = y + LEG_OFFSETS[i];
        return new Vector3d(hx + Math.cos(a) * spec.stance(), floorY, hz + Math.sin(a) * spec.stance());
    }

    /* ================================================================== pose */

    private void pose(int interp) {
        if (body == null) {
            // headless (simulation / tests): state only
            return;
        }
        Vector3d h = hubDrawn;
        double y = yaw.value();
        double s = hubScale;
        Quaternionf yawQ = new Quaternionf().rotationY((float) -y);

        // Hub: two rotated bodies make an octagon, a brass collar between them.
        body.box(h, yawQ, 2.6 * s, 1.5 * s, 2.6 * s, interp);
        body45.box(h, new Quaternionf(yawQ).rotateY((float) (Math.PI / 4)), 2.6 * s, 1.5 * s, 2.6 * s, interp);
        collar.box(new Vector3d(h.x, h.y - 0.15 * s, h.z), new Quaternionf(yawQ).rotateY((float) (Math.PI / 8)),
                3.25 * s, 0.32 * s, 3.25 * s, interp);

        // Lens: octagonal rim (two squares), diamond iris, a gaze spot on the floor where it looks.
        Vector3d lens = lensPos();
        Vector3d dir = new Vector3d(look.pos()).sub(lens);
        if (dir.lengthSquared() < 1e-6) {
            dir.set(Math.cos(y), 0, Math.sin(y));
        }
        dir.normalize();
        Quaternionf lq = new Quaternionf().rotationTo(1f, 0f, 0f, (float) dir.x, (float) dir.y, (float) dir.z);
        double ls = lensScale * s;
        rimA.box(lens, lq, 0.24 * ls, 1.75 * ls, 1.75 * ls, interp);
        rimB.box(lens, new Quaternionf(lq).rotateX((float) (Math.PI / 4)), 0.22 * ls, 1.75 * ls, 1.75 * ls, interp);
        Vector3d irisPos = new Vector3d(lens).add(dir.x * 0.16 * ls, dir.y * 0.16 * ls, dir.z * 0.16 * ls);
        iris.box(irisPos, new Quaternionf(lq).rotateX((float) (Math.PI / 4)), 0.28 * ls, 0.62 * ls, 0.62 * ls, interp);

        Vector3d spot = gazeSpot();
        if (gazeSize > 0.05 && lensScale > 0.2) {
            gaze.plate(spot.x, floorY + 0.045, spot.z, gazeSize, gazeSize, 0.02, Math.PI / 4, interp);
        } else {
            gaze.hide(interp);
        }
        if (beam && lensScale > 0.2) {
            beamPart.bone(lens, new Vector3d(spot.x, floorY + 0.05, spot.z), 0.07, interp);
        } else {
            beamPart.hide(interp);
        }

        // Needle (the gnomon): leans away from where its shadow should fall.
        double tilt = needleTilt.value();
        double na = needleAngle.value();
        Vector3d base = new Vector3d(h.x, h.y + 0.7 * s, h.z);
        Vector3d tip = new Vector3d(base.x + Math.sin(tilt) * Math.cos(na) * needleLength,
                base.y + Math.cos(tilt) * needleLength,
                base.z + Math.sin(tilt) * Math.sin(na) * needleLength);
        if (needleLength > 0.05) {
            needle.bone(base, tip, 1.7, interp);
        } else {
            needle.hide(interp);
        }

        // Chain + bob.
        Vector3d anchor = new Vector3d(h.x, h.y - 0.8 * s, h.z);
        Vector3d bp = bob.pos();
        Vector3d up = new Vector3d(anchor).sub(bp);
        double chainLen = up.length();
        if (chainLen < 1e-4) {
            up.set(0, 1, 0);
            chainLen = 1e-4;
        }
        up.div(chainLen);
        Vector3d chainBottom = new Vector3d(bp).add(up.x * 0.8, up.y * 0.8, up.z * 0.8);
        Vector3d along = new Vector3d(anchor).sub(chainBottom);
        double usable = along.length();
        for (int i = 0; i < CHAIN_SEGMENTS; i++) {
            if (usable < 0.2) {
                chain[i].hide(interp);
                continue;
            }
            Vector3d a = new Vector3d(chainBottom).add(new Vector3d(along).mul(i / (double) CHAIN_SEGMENTS));
            Vector3d b = new Vector3d(chainBottom).add(new Vector3d(along).mul((i + 1) / (double) CHAIN_SEGMENTS));
            chain[i].bone(a, b, 1.0, interp);
        }
        Quaternionf bq = new Quaternionf().rotationTo(0f, 1f, 0f, (float) up.x, (float) up.y, (float) up.z);
        bobBody.box(bp, bq, 1.25, 1.25, 1.25, interp);
        bobCap.box(new Vector3d(bp).add(up.x * 0.72, up.y * 0.72, up.z * 0.72), bq, 0.85, 0.3, 0.85, interp);
        bobTip.box(new Vector3d(bp).sub(up.x * 1.15, up.y * 1.15, up.z * 1.15), bq, 1.6, 1.05, 1.6, interp);

        // Legs.
        for (Leg leg : legs) {
            double a = y + leg.offset;
            leg.hip.set(h.x + Math.cos(a) * spec.hipRadius() * s, h.y - 0.55 * s, h.z + Math.sin(a) * spec.hipRadius() * s);
            Vector3d ankle = new Vector3d(leg.foot.x, leg.foot.y + SPIKE, leg.foot.z);
            double oa = Geo.angle(ankle.x - h.x, ankle.z - h.z);
            Vector3d pole = new Vector3d(Math.cos(oa), 0.85, Math.sin(oa));
            Leg.solve(leg.hip, ankle, spec.thigh() * s, spec.shin() * s, pole, leg.knee);
            int i = leg.index;
            thighs[i].bone(leg.hip, leg.knee, 0.55 * s, interp);
            shins[i].bone(leg.knee, ankle, 0.42 * s, interp);
            knees[i].box(leg.knee, yawQ, 0.8 * s, 0.8 * s, 0.8 * s, interp);
            ferrules[i].box(ankle, yawQ, 0.62 * s, 0.34 * s, 0.62 * s, interp);
            spikes[i].bone(leg.foot, ankle, 1.6 * s, interp);
        }
    }

    /** Hub scale animation for intro / death (0 = folded away). Re-poses at once. */
    public void forcePose() {
        pose(spec.updateInterval() + 1);
    }

    /** Teleports the internal state (no interpolation) - intro setup. */
    public void placeHub(double x, double yAbs, double z) {
        hub.snap(new Vector3d(x, yAbs, z));
        hubDrawn.set(x, yAbs, z);
        hubGoal.set(x, 0, z);
        height.snap(yAbs - floorY);
        bob.place(new Vector3d(x, yAbs - 0.8, z), Math.max(0.6, Math.min(chainRest, yAbs - 0.8 - floorY - 0.8)), floorY);
    }

    public void heightTarget(double blocksAboveFloor) {
        // Uses rear/crouch relative to spec height so callers can animate the absolute height.
        rear = blocksAboveFloor - spec.hubHeight();
        crouch = 0;
    }

    public double chainRest() {
        return chainRest;
    }

    public Spec spec() {
        return spec;
    }

    public boolean bobGold() {
        return bobGold;
    }

    public boolean lensDimmed() {
        return lensDim;
    }
}
