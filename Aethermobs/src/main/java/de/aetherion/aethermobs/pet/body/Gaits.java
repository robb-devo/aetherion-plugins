package de.aetherion.aethermobs.pet.body;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The gait library. Each skeleton poses its bones for one moment in time; the driver samples that moment at
 * gait extremes (keyframes) and the client interpolates in between, so a full walk cycle costs four writes
 * per moving part instead of twenty.
 */
final class Gaits {

    private Gaits() {
    }

    /** What the driver knows about the body at the sampled moment. */
    static final class Motion {
        float t;
        double phase;
        float speed;
        float vy;
        float turn;
        boolean moving;
        boolean equipped;
        boolean flying;
        boolean near;
        PetBody.Mode mode;
        int idle;
        float seed;
        /** 0..1 envelopes for one-shot actions. */
        float breath;
        float happy;
        float puff;
        float roll;
        float flex;
        float sit;
        float glide;
        boolean perched;
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    static Quaternionf euler(float pitch, float yaw, float roll) {
        return Rig.euler(pitch, yaw, roll);
    }

    private static void rot(Rig rig, int bone, float pitch, float yaw, float roll) {
        if (bone < 0 || bone >= rig.bones.size()) {
            return;
        }
        rig.bones.get(bone).rot.set(euler(pitch, yaw, roll));
    }

    private static void addRot(Rig rig, int bone, float pitch, float yaw, float roll) {
        if (bone < 0 || bone >= rig.bones.size()) {
            return;
        }
        rig.bones.get(bone).rot.mul(euler(pitch, yaw, roll));
    }

    private static void off(Rig rig, int bone, float x, float y, float z) {
        if (bone < 0 || bone >= rig.bones.size()) {
            return;
        }
        rig.bones.get(bone).off.set(x, y, z);
    }

    private static void scale(Rig rig, int bone, float x, float y, float z) {
        if (bone < 0 || bone >= rig.bones.size()) {
            return;
        }
        rig.bones.get(bone).scale.set(x, y, z);
    }

    private static float sin(double v) {
        return (float) Math.sin(v);
    }

    private static float cos(double v) {
        return (float) Math.cos(v);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int at(int[] a, int i) {
        return a != null && i < a.length ? a[i] : -1;
    }

    /** Reset all animated state, then pose for the given skeleton. */
    static void pose(Rig rig, Motion m) {
        for (Rig.Bone b : rig.bones) {
            b.reset();
        }
        rig.pitch = 0f;
        rig.roll = 0f;
        rig.lift = 0f;

        if (m.mode == PetBody.Mode.BALL) {
            ball(rig, m);
            return;
        }
        if (m.mode == PetBody.Mode.PADDLE) {
            paddle(rig, m);
            head(rig, m, 0.6f);
            return;
        }
        if (m.perched) {
            perch(rig, m);
            return;
        }
        switch (rig.skeleton) {
            case TROT -> trot(rig, m);
            case AMBLE -> amble(rig, m);
            case STRIDE -> stride(rig, m, false);
            case LUMBER -> stride(rig, m, true);
            case HOP -> hop(rig, m);
            case BOUNCE -> bounce(rig, m);
            case SCUTTLE -> scuttle(rig, m);
            case CRAWL -> crawl(rig, m);
            case SLITHER -> slither(rig, m);
            case WADDLE -> waddle(rig, m);
            case HOVER -> hover(rig, m);
            case FLUTTER -> flutter(rig, m);
            case FLAP -> flap(rig, m, false);
            case SOAR -> flap(rig, m, true);
            case DRIFT -> drift(rig, m);
            case HAUNT -> haunt(rig, m);
            case SWIM -> swim(rig, m);
            case JET -> jet(rig, m);
            case PORPOISE -> porpoise(rig, m);
            case DRAGON -> dragon(rig, m);
            case WYRM -> wyrm(rig, m);
        }
        orbit(rig, m);
    }

    /** Head yaw toward the look target plus a little life. */
    private static void head(Rig rig, Motion m, float weight) {
        Rig.Roles r = rig.roles();
        if (r.head < 0 || r.head == r.body) {
            return;
        }
        float look = clamp(rig.lookOffset, -70f, 70f) * weight;
        float tilt = sin(m.t * 0.031f + m.seed * 3f) * 6f;
        float nod = m.moving ? 0f : sin(m.t * 0.047f + m.seed) * 3f;
        addRot(rig, r.head, nod, look, tilt * 0.5f);
    }

    /**
     * Squash the main body about its feet. Bone-local scale keeps every part matrix a clean
     * rotation x axis-scale, which the client decomposes and interpolates without shearing.
     */
    private static void squashBody(Rig rig, float sxz, float sy) {
        int body = rig.roles().body;
        if (body < 0) {
            return;
        }
        Rig.Bone b = rig.bones.get(body);
        b.scale.set(sxz, sy, sxz);
        b.off.y -= rig.bodyHalf * (1f - sy);
    }

    /** Idle breathing on the main body bone. */
    private static void breathe(Rig rig, Motion m, float amount) {
        int body = rig.roles().body;
        float b = 1f + sin(m.t * 0.1f + m.seed) * amount;
        scale(rig, body, 1f + (b - 1f) * 0.6f, b, 1f);
    }

    /*
     * =========================================================
     * GROUND
     * =========================================================
     */

    private static void trot(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        if (m.moving) {
            boolean gallop = m.speed > 0.17f;
            float amp = clamp(18f + m.speed * 160f, 18f, gallop ? 46f : 36f);
            float a = sin(p);
            float b = sin(p + Math.PI);
            if (gallop) {
                // Rotary gallop: fronts together, backs together, body rocks.
                float f = sin(p);
                float k = sin(p + Math.PI * 0.6);
                rot(rig, at(r.legs, 0), f * amp, 0f, 0f);
                rot(rig, at(r.legs, 1), sin(p + 0.35) * amp, 0f, 0f);
                rot(rig, at(r.legs, 2), k * amp, 0f, 0f);
                rot(rig, at(r.legs, 3), sin(p + Math.PI * 0.6 + 0.35) * amp, 0f, 0f);
                rig.pitch = sin(p + Math.PI * 0.3) * 7f;
                rig.lift = Math.max(0f, sin(p)) * 0.07f;
            } else {
                rot(rig, at(r.legs, 0), a * amp, 0f, 0f);
                rot(rig, at(r.legs, 3), a * amp, 0f, 0f);
                rot(rig, at(r.legs, 1), b * amp, 0f, 0f);
                rot(rig, at(r.legs, 2), b * amp, 0f, 0f);
                rig.lift = Math.abs(cos(p)) * 0.035f;
            }
            addRot(rig, r.head, sin(p * 2) * 4f, 0f, 0f);
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], 0f, sin(p - i * 0.8) * (10f + i * 4f), 0f);
            }
            rig.roll = clamp(-m.turn * 1.2f, -10f, 10f);
        } else {
            breathe(rig, m, 0.025f);
            float wag = m.happy > 0f ? 28f * m.happy : 9f;
            float rate = m.happy > 0f ? 0.9f : 0.22f;
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], 0f, sin(m.t * rate - i * 0.7) * (wag + i * 4f), 0f);
            }
            if (m.sit > 0f) {
                // Nose up, rump down: hind legs fold forward under the belly, forelegs stay plumb.
                float s = m.sit;
                rig.pitch = 28f * s;
                rot(rig, at(r.legs, 0), -28f * s, 0f, 0f);
                rot(rig, at(r.legs, 1), -28f * s, 0f, 0f);
                rot(rig, at(r.legs, 2), 62f * s, 0f, 0f);
                rot(rig, at(r.legs, 3), 62f * s, 0f, 0f);
                for (int i = 0; i < r.tail.length; i++) {
                    addRot(rig, r.tail[i], 30f * s, 0f, 0f);
                }
                rig.lift = -0.06f * s * rig.size;
            }
            if (m.happy > 0f) {
                rig.lift += Math.max(0f, sin(m.t * 0.7f)) * 0.08f * m.happy;
            }
        }
        ears(rig, m);
        head(rig, m, 1f);
    }

    private static void amble(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        if (m.moving) {
            float amp = clamp(16f + m.speed * 120f, 16f, 30f);
            // Four-beat lateral sequence: BL, FL, BR, FR.
            rot(rig, at(r.legs, 2), sin(p) * amp, 0f, 0f);
            rot(rig, at(r.legs, 0), sin(p + Math.PI * 0.5) * amp, 0f, 0f);
            rot(rig, at(r.legs, 3), sin(p + Math.PI) * amp, 0f, 0f);
            rot(rig, at(r.legs, 1), sin(p + Math.PI * 1.5) * amp, 0f, 0f);
            rig.roll = sin(p) * 3.5f;
            rig.lift = Math.abs(sin(p * 2)) * 0.02f;
            addRot(rig, r.head, sin(p * 2) * 5f, 0f, 0f);
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], 0f, sin(p - i) * 8f, sin(p) * 6f);
            }
        } else {
            breathe(rig, m, 0.02f);
            // Graze: the head dips to the grass now and then (wild only).
            float graze = m.equipped ? 0f : Math.max(0f, sin(m.t * 0.012f + m.seed * 5f) - 0.55f) * 2.2f;
            if (graze > 0f && r.head >= 0) {
                addRot(rig, r.neck.length > 0 ? r.neck[0] : r.head, -38f * Math.min(1f, graze), 0f, 0f);
            }
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], 0f, sin(m.t * 0.16f - i) * 10f, 0f);
            }
        }
        head(rig, m, 0.8f);
    }

    private static void stride(Rig rig, Motion m, boolean heavy) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        float armRest = 0f;
        if (m.moving) {
            float amp = heavy ? clamp(14f + m.speed * 120f, 14f, 26f) : clamp(18f + m.speed * 170f, 18f, 40f);
            rot(rig, at(r.legs, 0), sin(p) * amp, 0f, 0f);
            rot(rig, at(r.legs, 1), sin(p + Math.PI) * amp, 0f, 0f);
            float arm = amp * (heavy ? 1.1f : 0.8f);
            rot(rig, at(r.arms, 0), sin(p + Math.PI) * arm + armRest, 0f, heavy ? -4f : 0f);
            rot(rig, at(r.arms, 1), sin(p) * arm + armRest, 0f, heavy ? 4f : 0f);
            rig.lift = Math.abs(cos(p)) * (heavy ? 0.045f : 0.03f);
            rig.roll = sin(p) * (heavy ? 5f : 2f);
            addRot(rig, r.body, 0f, sin(p) * (heavy ? 7f : 4f), 0f);
        } else {
            breathe(rig, m, heavy ? 0.03f : 0.02f);
            float sway = sin(m.t * 0.08f + m.seed) * 4f;
            rot(rig, at(r.arms, 0), sway, 0f, -3f);
            rot(rig, at(r.arms, 1), -sway, 0f, 3f);
            if (m.happy > 0f) {
                rot(rig, at(r.arms, 0), -150f * m.happy, 0f, -10f);
                rot(rig, at(r.arms, 1), -150f * m.happy, 0f, 10f);
                rig.lift = Math.max(0f, sin(m.t * 0.6f)) * 0.08f * m.happy;
            }
        }
        head(rig, m, 1f);
    }

    private static void hop(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        if (m.moving || m.happy > 0f) {
            double p = m.moving ? m.phase : m.t * 0.5;
            float h = m.moving ? clamp(0.06f + m.speed * 2.2f, 0.08f, 0.26f) : 0.12f * m.happy;
            float arc = (float) Math.abs(Math.sin(p * 0.5));
            rig.lift = arc * h;
            float c = cos(p);
            // Crouch at take-off and landing, stretch in the air.
            float stretch = 1f + (1f - c) * 0.05f - (c > 0.6f ? (c - 0.6f) * 0.3f : 0f);
            squashBody(rig, 2f - stretch, stretch);
            rig.pitch = sin(p) * 14f;
            rot(rig, at(r.legs, 0), -sin(p) * 45f - 10f, 0f, 0f);
            rot(rig, at(r.legs, 1), -sin(p) * 45f - 10f, 0f, 0f);
            for (int e : r.ears) {
                rot(rig, e, -sin(p) * 25f - 10f, 0f, 0f);
            }
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], sin(p - i) * 10f, 0f, 0f);
            }
        } else {
            breathe(rig, m, 0.035f);
            // Nose twitch / ear flicks / sack rustle.
            float twitch = Math.max(0f, sin(m.t * 0.21f + m.seed * 7f) - 0.85f) * 6f;
            addRot(rig, r.head, twitch * 6f, 0f, 0f);
            for (int i = 0; i < r.ears.length; i++) {
                rot(rig, r.ears[i], 0f, 0f, (i == 0 ? 1 : -1) * twitch * 14f);
            }
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], sin(m.t * 0.1f - i) * 6f, sin(m.t * 0.07f) * 8f, 0f);
            }
            if (r.head == r.body || r.legs.length == 0) {
                rig.roll = sin(m.t * 0.13f + m.seed) * 3f;
            }
        }
        head(rig, m, 0.8f);
    }

    private static void bounce(Rig rig, Motion m) {
        float h;
        double p;
        if (m.moving) {
            p = m.phase;
            h = clamp(0.1f + m.speed * 2.6f, 0.12f, 0.34f);
        } else {
            p = m.t * 0.16 + m.seed;
            h = m.happy > 0f ? 0.25f : 0.0f;
        }
        float arc = (float) Math.abs(Math.sin(p * 0.5));
        rig.lift = arc * h;
        float c = cos(p);
        float squish = h <= 0f ? sin(m.t * 0.12f) * 0.05f : (c > 0.4f ? (c - 0.4f) * 0.45f : -arc * 0.18f);
        squashBody(rig, 1f + squish, 1f - squish);
        int[] orbit = rig.roles().orbit;
        if (orbit.length > 0) {
            off(rig, orbit[0], 0f, (float) Math.abs(Math.sin(p * 0.5 + 1.3)) * h * 0.8f, 0f);
        }
    }

    private static void scuttle(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.moving ? m.phase : m.t * 0.04;
        float amp = m.moving ? clamp(16f + m.speed * 200f, 16f, 28f) : 3f;
        for (int i = 0; i < r.legs.length; i++) {
            // Alternating tripods: L0 R1 L2 step together, then R0 L1 R2.
            int pair = i / 2;
            int side = i % 2;
            double ph = p + ((pair + side) % 2) * Math.PI;
            float sideSign = side == 0 ? 1f : -1f;
            rot(rig, r.legs[i], 0f, sin(ph) * amp, sideSign * Math.max(0f, cos(ph)) * amp * 0.5f);
        }
        rig.lift = m.moving ? Math.abs(sin(p * 2)) * 0.015f : 0f;
        if (!m.moving) {
            breathe(rig, m, 0.03f);
        }
        head(rig, m, 0.5f);
    }

    private static void crawl(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        if (m.roll > 0f) {
            // Armadillo roll-up: everything tucks, the shell becomes a ball.
            float k = m.roll;
            for (int leg : r.legs) {
                scale(rig, leg, 1f - k, 1f - k, 1f - k);
            }
            scale(rig, r.head, 1f - k * 0.9f, 1f - k * 0.9f, 1f - k * 0.9f);
            off(rig, r.head, 0f, 0f, -0.18f * k);
            int body = r.body;
            scale(rig, body, 1f - 0.15f * k, 1f + 0.35f * k, 1f - 0.2f * k);
            if (body >= 0) {
                rig.bones.get(body).off.y += rig.bodyHalf * 0.35f * k;
            }
            return;
        }
        double p = m.moving ? m.phase : 0;
        float amp = m.moving ? 26f : 0f;
        rot(rig, at(r.legs, 0), sin(p) * amp, sin(p) * amp * 0.6f, 0f);
        rot(rig, at(r.legs, 3), sin(p) * amp, sin(p) * amp * 0.6f, 0f);
        rot(rig, at(r.legs, 1), sin(p + Math.PI) * amp, -sin(p + Math.PI) * amp * 0.6f, 0f);
        rot(rig, at(r.legs, 2), sin(p + Math.PI) * amp, -sin(p + Math.PI) * amp * 0.6f, 0f);
        float poke = m.moving ? (sin(p) * 0.5f + 0.5f) * 0.04f : Math.max(0f, sin(m.t * 0.05f + m.seed)) * 0.05f;
        off(rig, r.head, 0f, 0f, poke);
        rig.roll = m.moving ? sin(p) * 2f : 0f;
        head(rig, m, 0.7f);
    }

    private static void slither(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.moving ? m.phase : m.t * 0.05;
        float amp = m.moving ? 0.07f : 0.025f;
        for (int i = 0; i < r.segments.length; i++) {
            double ph = p - i * 0.95;
            off(rig, r.segments[i], sin(ph) * amp * (0.6f + i * 0.25f), Math.max(0f, sin(ph + Math.PI / 2)) * (m.moving ? 0.025f : 0f), 0f);
            rot(rig, r.segments[i], 0f, cos(ph) * 18f, 0f);
        }
        off(rig, r.head, sin(p + 0.9) * amp * 0.5f, Math.max(0f, sin(p * 0.5)) * 0.02f, 0f);
        head(rig, m, 0.6f);
    }

    private static void waddle(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.moving ? m.phase : m.t * 0.06;
        float amp = m.moving ? 22f : 0f;
        rot(rig, at(r.legs, 0), sin(p) * amp, 0f, 0f);
        rot(rig, at(r.legs, 1), sin(p + Math.PI) * amp, 0f, 0f);
        rig.roll = sin(p) * (m.moving ? 10f : 3f);
        rig.lift = m.moving ? Math.abs(sin(p)) * 0.04f : 0f;
        addRot(rig, r.head, 0f, 0f, -sin(p) * (m.moving ? 6f : 2f));
        rot(rig, at(r.arms, 0), sin(p + Math.PI) * 24f, 0f, -12f);
        rot(rig, at(r.arms, 1), sin(p) * 24f, 0f, 12f);
        if (!m.moving) {
            breathe(rig, m, 0.03f);
            if (m.happy > 0f) {
                rig.lift = Math.max(0f, sin(m.t * 0.7f)) * 0.1f * m.happy;
            }
        }
        head(rig, m, 0.5f);
    }

    /** On the owner's shoulder: wings folded (insects close theirs upright), belly on the shoulder. */
    private static void perch(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        rig.lift = rig.bodyHalf;
        switch (rig.skeleton) {
            case FLUTTER -> {
                rot(rig, at(r.wings, 0), 0f, 0f, 84f);
                rot(rig, at(r.wings, 1), 0f, 0f, -84f);
                rot(rig, at(r.wings, 2), 0f, 0f, 80f);
                rot(rig, at(r.wings, 3), 0f, 0f, -80f);
            }
            case HOVER -> {
                rot(rig, at(r.wings, 0), 0f, 28f, 4f);
                rot(rig, at(r.wings, 1), 0f, -28f, -4f);
            }
            default -> {
                rot(rig, at(r.wings, 0), 0f, 72f, -22f);
                rot(rig, at(r.wings, 1), 0f, -72f, 22f);
                rot(rig, at(r.wings, 2), 0f, 105f, 0f);
                rot(rig, at(r.wings, 3), 0f, -105f, 0f);
                for (int i = 0; i < r.tail.length; i++) {
                    rot(rig, r.tail[i], -14f, 0f, 0f);
                }
            }
        }
        breathe(rig, m, 0.03f);
        head(rig, m, 1f);
    }

    /** Ground pet in water: body sits low, legs paddle fast and small. */
    private static void paddle(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.t * 0.7;
        for (int i = 0; i < r.legs.length; i++) {
            rot(rig, r.legs[i], sin(p + i * Math.PI * 0.5) * 30f, 0f, 0f);
        }
        rig.pitch = 8f;
        for (int i = 0; i < r.tail.length; i++) {
            rot(rig, r.tail[i], 0f, sin(p * 0.5 - i) * 14f, 0f);
        }
    }

    private static void ears(Rig rig, Motion m) {
        int[] ears = rig.roles().ears;
        if (ears.length < 2) {
            return;
        }
        float flick = Math.max(0f, sin(m.t * 0.17f + m.seed * 11f) - 0.9f) * 10f;
        rot(rig, ears[0], m.moving ? -12f : 0f, 0f, flick * 20f);
        rot(rig, ears[1], m.moving ? -12f : 0f, 0f, -flick * 20f);
    }

    /*
     * =========================================================
     * AIR
     * =========================================================
     */

    private static void hover(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        boolean buzz = m.near && rig.skeleton == Skeleton.HOVER && rig.parts.size() < 12;
        float beat = buzz ? ((int) m.t % 4 < 2 ? 38f : -12f) : 16f;
        if (rig.roles().arms.length == 0 && r.wings.length >= 2 && !buzz) {
            beat = 14f + sin(m.t * 0.6f) * 10f;
        }
        rot(rig, at(r.wings, 0), 0f, 0f, beat);
        rot(rig, at(r.wings, 1), 0f, 0f, -beat);
        rig.pitch = m.moving ? -clamp(m.speed * 120f, 0f, 16f) : 0f;
        rig.roll = clamp(-m.turn * 1.5f, -18f, 18f);
        head(rig, m, 0.7f);
    }

    private static void flutter(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        float up = 68f, down = -12f;
        float w = (sin(p) * 0.5f + 0.5f);
        float a = down + (up - down) * w;
        rot(rig, at(r.wings, 0), 0f, 0f, a);
        rot(rig, at(r.wings, 1), 0f, 0f, -a);
        rot(rig, at(r.wings, 2), 0f, 0f, a * 0.8f);
        rot(rig, at(r.wings, 3), 0f, 0f, -a * 0.8f);
        rig.lift = cos(p) * 0.06f;
        rig.pitch = 12f + (m.moving ? -6f : 0f);
        rig.roll = clamp(-m.turn * 1.4f, -20f, 20f);
        head(rig, m, 0.4f);
    }

    private static void flap(Rig rig, Motion m, boolean soar) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        float glide = m.glide;
        float amp = soar ? 26f : 46f;
        float stroke = sin(p) * amp * (1f - glide) + (soar ? 8f : 6f) * glide;
        float dihedral = soar ? 6f : 10f;
        float inner = dihedral + stroke;
        float outer = sin(p - 0.9) * amp * 0.6f * (1f - glide) + (soar ? 4f : 0f);
        rot(rig, at(r.wings, 0), 0f, soar ? -4f : 0f, inner);
        rot(rig, at(r.wings, 1), 0f, soar ? 4f : 0f, -inner);
        rot(rig, at(r.wings, 2), 0f, 0f, outer);
        rot(rig, at(r.wings, 3), 0f, 0f, -outer);
        rig.lift = -sin(p) * 0.05f * (1f - glide);
        float bank = clamp(-m.turn * (soar ? 3.2f : 2f), soar ? -38f : -26f, soar ? 38f : 26f);
        rig.roll = bank;
        if (m.moving) {
            rig.pitch = -clamp(m.speed * 60f, 0f, 12f) + clamp(m.vy * 120f, -15f, 15f);
        } else {
            // Hovering in place: body upright-ish, quick shallow strokes.
            rig.pitch = 18f;
        }
        for (int i = 0; i < r.tail.length; i++) {
            rot(rig, r.tail[i], glide > 0.5f ? 0f : sin(p) * 6f, 0f, bank * 0.3f);
        }
        head(rig, m, 0.6f);
    }

    private static void drift(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        float t = m.t;
        rig.pitch = m.moving ? -clamp(m.speed * 90f, 0f, 14f) : 0f;
        rig.roll = clamp(-m.turn * 1.2f, -14f, 14f);
        for (int i = 0; i < r.tentacles.length; i++) {
            float s = sin(t * 0.11f - i * 1.3f + m.seed);
            rot(rig, r.tentacles[i], (m.moving ? -18f : 0f) + s * 12f, 0f, cos(t * 0.09f - i) * 8f);
        }
        if (r.wings.length >= 2) {
            float w = 20f + sin(t * 0.5f) * 22f;
            rot(rig, r.wings[0], 0f, 0f, w);
            rot(rig, r.wings[1], 0f, 0f, -w);
        }
        rot(rig, at(r.arms, 0), 30f + sin(t * 0.07f) * 10f, 0f, -8f);
        rot(rig, at(r.arms, 1), 30f + sin(t * 0.07f + 1f) * 10f, 0f, 8f);
        breathe(rig, m, 0.03f);
        head(rig, m, 0.9f);
    }

    private static void haunt(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        float t = m.t;
        rig.roll = clamp(-m.turn * 0.8f, -8f, 8f);
        for (int i = 0; i < r.tentacles.length; i++) {
            float s = sin(t * 0.13f - i * 1.1f);
            rot(rig, r.tentacles[i], s * 16f + (m.moving ? -14f : 0f), 0f, cos(t * 0.1f - i * 0.8f) * 10f);
        }
        // Wither: side heads look on their own.
        if (r.ears.length == 2 && r.tentacles.length == 0) {
            rot(rig, r.ears[0], sin(t * 0.04f) * 10f, 25f + sin(t * 0.027f + 1f) * 30f, 0f);
            rot(rig, r.ears[1], sin(t * 0.05f + 2f) * 10f, -25f + sin(t * 0.031f + 4f) * 30f, 0f);
        }
        for (int i = 0; i < r.tail.length; i++) {
            rot(rig, r.tail[i], sin(t * 0.08f) * 12f, sin(t * 0.06f) * 10f, 0f);
        }
        head(rig, m, 1f);
    }

    /** Orbiting motes spin around the body (blaze rods, crystals, petals). */
    private static void orbit(Rig rig, Motion m) {
        int[] orbit = rig.roles().orbit;
        if (orbit.length == 0 || rig.skeleton == Skeleton.BOUNCE) {
            return;
        }
        boolean screen = rig.skeleton == Skeleton.STRIDE && orbit.length == 1;
        if (screen) {
            Rig.Bone b = rig.bones.get(orbit[0]);
            b.off.set(0f, sin(m.t * 0.1f) * 0.03f, 0f);
            if (m.mode != PetBody.Mode.CAPTURED && Math.floorMod((int) m.t + (int) (m.seed * 100), 97) < 3) {
                b.off.add(0.03f, 0f, 0f); // glitch
            }
            return;
        }
        float spin = rig.skeleton == Skeleton.HAUNT ? 0.09f : 0.045f;
        float angle = m.t * spin + m.seed;
        Quaternionf q = new Quaternionf().rotateY(angle);
        for (int i = 0; i < orbit.length; i++) {
            Rig.Bone b = rig.bones.get(orbit[i]);
            Vector3f moved = q.transform(new Vector3f(b.pivot));
            moved.sub(b.pivot);
            moved.y += sin(m.t * 0.08f + i * 2.1f) * 0.05f;
            b.off.set(moved);
            b.rot.set(new Quaternionf().rotateY(angle * 2f));
        }
    }

    /*
     * =========================================================
     * WATER
     * =========================================================
     */

    private static void swim(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        float amp = m.moving ? clamp(18f + m.speed * 220f, 18f, 34f) : 10f;
        for (int i = 0; i < r.tail.length; i++) {
            rot(rig, r.tail[i], 0f, sin(p - i * 0.8) * amp * (1f + i * 0.3f), 0f);
        }
        addRot(rig, r.body, 0f, -sin(p) * amp * 0.18f, 0f);
        rig.pitch = clamp(m.vy * 140f, -25f, 25f);
        rig.roll = clamp(-m.turn * 1.2f, -14f, 14f);
        // Axolotl legs tuck while swimming, walk on the floor.
        for (int i = 0; i < r.legs.length; i++) {
            rot(rig, r.legs[i], m.moving ? -50f : sin(m.t * 0.2f + i) * 15f, 0f, 0f);
        }
        // Pufferfish puff / guardian spikes.
        if (r.spikes.length > 0) {
            if (r.spikes.length == 4) {
                float puff = 1f + m.puff * 0.55f;
                scale(rig, r.head, puff, puff, puff);
                for (int s : r.spikes) {
                    Rig.Bone b = rig.bones.get(s);
                    b.off.set(new Vector3f(b.pivot).mul(m.puff * 0.55f));
                    b.scale.set(1f + m.puff, 1f + m.puff, 1f + m.puff);
                }
            } else {
                float out = (sin(m.t * 0.05f + m.seed) * 0.5f + 0.5f) * 0.6f;
                for (int s : r.spikes) {
                    Rig.Bone b = rig.bones.get(s);
                    b.off.set(new Vector3f(b.pivot).mul(out * 0.35f));
                }
            }
        }
        head(rig, m, 0.3f);
    }

    private static void jet(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        // 0..pi: contract and thrust, pi..2pi: glide with tentacles trailing.
        float squeeze = Math.max(0f, sin(p));
        int n = r.tentacles.length;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n + Math.PI / n;
            float flare = 8f + (1f - squeeze) * 34f + sin(m.t * 0.3f + i) * 4f;
            Quaternionf q = new Quaternionf().rotateAxis(flare * DEG, (float) -Math.sin(a), 0f, (float) Math.cos(a));
            rig.bones.get(r.tentacles[i]).rot.set(q);
        }
        scale(rig, r.body, 1f - squeeze * 0.12f, 1f + squeeze * 0.14f, 1f - squeeze * 0.12f);
        rig.lift = sin(p) * 0.06f;
        rig.pitch = m.moving ? -20f : 0f;
        head(rig, m, 0f);
    }

    private static void porpoise(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        float amp = m.moving ? 1f : 0.4f;
        rig.pitch = sin(p) * 10f * amp;
        rig.lift = cos(p) * 0.07f * amp;
        for (int i = 0; i < r.tail.length; i++) {
            rot(rig, r.tail[i], sin(p - 1.2 - i * 0.6) * 26f * amp, 0f, 0f);
        }
        rig.roll = clamp(-m.turn * 1.6f, -22f, 22f);
        head(rig, m, 0.3f);
    }

    /** Equipped ground pet riding in its sphere: slow spin, soft bob. */
    private static void ball(Rig rig, Motion m) {
        rig.lift = sin(m.t * 0.1f) * 0.05f;
        Rig.Part ball = rig.roles().ball;
        if (ball != null) {
            rig.bones.get(0).rot.set(new Quaternionf().rotateY(m.t * 0.06f).rotateX(sin(m.t * 0.05f) * 0.25f));
        }
    }

    /*
     * =========================================================
     * DRAGONS
     * =========================================================
     */

    private static void dragon(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        double p = m.phase;
        float t = m.t;
        int wl = at(r.wings, 0), wr = at(r.wings, 1), ol = at(r.wings, 2), or = at(r.wings, 3);

        if (m.flying) {
            float glide = m.glide;
            float stroke = sin(p);
            float inner = 8f + stroke * 44f * (1f - glide) + glide * 4f;
            float outer = sin(p - 0.8) * 30f * (1f - glide) + glide * 2f;
            rot(rig, wl, 0f, -6f + stroke * 6f, inner);
            rot(rig, wr, 0f, 6f - stroke * 6f, -inner);
            rot(rig, ol, 0f, -4f, outer);
            rot(rig, or, 0f, 4f, -outer);
            for (int leg : r.legs) {
                rot(rig, leg, -62f, 0f, 0f);
            }
            rot(rig, at(r.neck, 0), -16f + sin(p) * 3f, 0f, 0f);
            rot(rig, at(r.neck, 1), 10f, 0f, 0f);
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], 4f + sin(p - i * 0.6) * 4f, sin(t * 0.09f - i * 0.75f) * 9f, 0f);
            }
            rig.lift = -stroke * 0.09f * (1f - glide);
            rig.pitch = m.moving ? -clamp(m.speed * 30f, 0f, 10f) + clamp(m.vy * 60f, -12f, 12f) : 8f;
            rig.roll = clamp(-m.turn * 1.8f, -24f, 24f);
        } else {
            // Ground: heavy four-beat walk with wings folded along the flanks.
            float fold = 1f - m.flex;
            rot(rig, wl, 0f, 92f * fold + 10f * m.flex, 30f * fold + 46f * m.flex);
            rot(rig, wr, 0f, -92f * fold - 10f * m.flex, -30f * fold - 46f * m.flex);
            rot(rig, ol, 0f, 150f * fold, -20f * fold + 10f * m.flex);
            rot(rig, or, 0f, -150f * fold, 20f * fold - 10f * m.flex);
            if (m.moving) {
                float amp = clamp(14f + m.speed * 100f, 14f, 28f);
                rot(rig, at(r.legs, 2), sin(p) * amp, 0f, 0f);
                rot(rig, at(r.legs, 0), sin(p + Math.PI * 0.5) * amp, 0f, 0f);
                rot(rig, at(r.legs, 3), sin(p + Math.PI) * amp, 0f, 0f);
                rot(rig, at(r.legs, 1), sin(p + Math.PI * 1.5) * amp, 0f, 0f);
                rig.roll = sin(p) * 3f;
                rig.lift = Math.abs(sin(p * 2)) * 0.025f;
                rot(rig, at(r.neck, 0), sin(p * 2) * 4f, sin(p) * 5f, 0f);
            } else {
                rot(rig, at(r.neck, 0), sin(t * 0.03f + m.seed) * 4f, sin(t * 0.021f) * 9f, 0f);
                rot(rig, at(r.neck, 1), sin(t * 0.04f) * 3f, 0f, 0f);
            }
            for (int i = 0; i < r.tail.length; i++) {
                rot(rig, r.tail[i], sin(t * 0.05f - i) * 3f, sin(t * 0.07f - i * 0.8f) * (6f + i * 2f), 0f);
            }
            int chest = r.chest;
            float b = 1f + sin(t * 0.07f + m.seed) * 0.035f;
            scale(rig, chest, b, b, 1f);
            for (int i = 0; i < r.spikes.length; i++) {
                rot(rig, r.spikes[i], sin(t * 0.07f - i) * 4f, 0f, 0f);
            }
        }
        // Breath / roar: head up, jaw wide.
        if (m.breath > 0f) {
            rot(rig, r.jaw, -34f * m.breath, 0f, 0f);
            addRot(rig, at(r.neck, 1), 12f * m.breath, 0f, 0f);
            addRot(rig, r.head, -8f * m.breath, 0f, 0f);
        } else {
            rot(rig, r.jaw, -Math.max(0f, sin(t * 0.04f + m.seed) - 0.92f) * 60f, 0f, 0f);
        }
        head(rig, m, 0.8f);
    }

    private static void wyrm(Rig rig, Motion m) {
        Rig.Roles r = rig.roles();
        float t = m.t;
        double w = m.moving ? m.phase : t * 0.08;
        float ampX = m.moving ? 0.12f : 0.07f;
        float ampY = m.moving ? 0.07f : 0.05f;
        int n = r.segments.length;
        for (int i = 0; i < n; i++) {
            double ph = w - i * 0.62;
            float k = 0.4f + i / (float) n;
            off(rig, r.segments[i], sin(ph) * ampX * k, sin(ph * 0.5 + 0.7) * ampY * k, 0f);
            rot(rig, r.segments[i], cos(ph * 0.5 + 0.7) * 10f * k, cos(ph) * 22f * k, 0f);
        }
        off(rig, r.head, sin(w + 0.62) * ampX * 0.3f, sin(w * 0.5 + 1.2) * ampY * 0.4f, 0f);
        for (int i = 0; i < r.legs.length; i++) {
            rot(rig, r.legs[i], sin(t * 0.18f + i * 1.7f) * 22f - 20f, 0f, 0f);
        }
        rig.roll = clamp(-m.turn * 1.4f, -16f, 16f);
        if (m.breath > 0f) {
            rot(rig, r.jaw, -32f * m.breath, 0f, 0f);
            addRot(rig, r.head, 12f * m.breath, 0f, 0f);
        } else {
            rot(rig, r.jaw, -Math.max(0f, sin(t * 0.05f + m.seed) - 0.9f) * 50f, 0f, 0f);
        }
        head(rig, m, 0.7f);
    }
}
