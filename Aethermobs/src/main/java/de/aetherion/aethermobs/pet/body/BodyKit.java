package de.aetherion.aethermobs.pet.body;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Anatomy templates. Every template builds bones + parts at size 1 (wild adult) with the feet of ground
 * creatures on y = 0 and the body centre of flyers / swimmers on y = 0, local +Z forward, +X left.
 *
 * <p>Part budget law: commons stay around 5-8 parts, rarity extras are opt-in, dragons may go to ~30.
 */
final class BodyKit {

    private BodyKit() {
    }

    private static final Map<Material, BlockData> DATA = new EnumMap<>(Material.class);

    static BlockData bd(Material material) {
        return DATA.computeIfAbsent(material, Material::createBlockData).clone();
    }

    /** Body colours: primary coat, secondary (legs / belly), tertiary (tail / details), accent, eyes. */
    record Pal(Material primary, Material secondary, Material tertiary, Material accent, Material eye) {
        static Pal of(Material primary, Material secondary, Material tertiary) {
            return new Pal(primary, secondary, tertiary, tertiary, Material.BLACK_CONCRETE);
        }

        static Pal of(Material primary, Material secondary, Material tertiary, Material accent) {
            return new Pal(primary, secondary, tertiary, accent, Material.BLACK_CONCRETE);
        }

        Pal eyes(Material eye) {
            return new Pal(primary, secondary, tertiary, accent, eye);
        }
    }

    enum Ears {
        NONE, POINTY, FLOPPY, TALL, ROUND, TUFT
    }

    enum Tail {
        NONE, THIN, BUSHY, CURL, STUB, HANG, FLUFF, LONG
    }

    /** Quadruped dimensions (blocks at size 1). */
    static final class Quad {
        float len = 0.58f, wid = 0.30f, hei = 0.30f;
        float legH = 0.30f, legT = 0.10f;
        float head = 0.72f, headUp = 0.06f, headFwd = 0.04f;
        float neckLen = 0f, neckPitch = 55f, neckT = 0.2f;
        float tailLen = 0.30f, tailT = 0.09f, tailPitch = 30f;
        Ears ears = Ears.NONE;
        Tail tail = Tail.THIN;
        boolean chestMane;

        Quad body(float len, float wid, float hei) {
            this.len = len;
            this.wid = wid;
            this.hei = hei;
            return this;
        }

        Quad legs(float h, float t) {
            this.legH = h;
            this.legT = t;
            return this;
        }

        Quad head(float scale, float up, float fwd) {
            this.head = scale;
            this.headUp = up;
            this.headFwd = fwd;
            return this;
        }

        Quad neck(float len, float pitch, float t) {
            this.neckLen = len;
            this.neckPitch = pitch;
            this.neckT = t;
            return this;
        }

        Quad tail(Tail tail, float len, float t, float pitch) {
            this.tail = tail;
            this.tailLen = len;
            this.tailT = t;
            this.tailPitch = pitch;
            return this;
        }

        Quad ears(Ears ears) {
            this.ears = ears;
            return this;
        }

        Quad mane() {
            this.chestMane = true;
            return this;
        }
    }

    /*
     * =========================================================
     * QUADRUPED
     * =========================================================
     */

    static Rig quad(Skeleton skeleton, Quad q, Pal p, ItemStack face) {
        Rig rig = new Rig(skeleton, false);
        Rig.Roles r = rig.roles();
        float bodyY = q.legH + q.hei / 2f;
        int body = rig.bone("body", 0, 0f, bodyY, 0f);
        r.body = body;
        r.bodyParts.add(rig.box(body, bd(p.primary()), 0f, 0f, 0f, q.wid, q.hei, q.len, Rig.CORE).shadow());
        if (q.chestMane) {
            rig.box(body, bd(p.secondary()), 0f, 0.01f, q.len * 0.28f, q.wid + 0.06f, q.hei + 0.06f, q.len * 0.38f, Rig.CORE);
        }

        int headParent = body;
        float headY = q.hei * 0.32f + q.headUp;
        float headZ = q.len / 2f + q.headFwd;
        if (q.neckLen > 0f) {
            int neck = rig.bone("neck", body, 0f, q.hei * 0.2f, q.len / 2f - q.neckT * 0.4f, q.neckPitch, 0f, 0f);
            r.neck = new int[]{neck};
            rig.box(neck, bd(p.primary()), 0f, 0f, q.neckLen / 2f, q.neckT, q.neckT, q.neckLen, Rig.CORE);
            int head = rig.bone("head", neck, 0f, 0f, q.neckLen, -q.neckPitch, 0f, 0f);
            r.head = head;
            rig.item(head, face, 0f, 0.02f, q.head * 0.14f, q.head, 0f, 0f, 0f, Rig.CORE);
            headParent = head;
        } else {
            int head = rig.bone("head", body, 0f, headY, headZ);
            r.head = head;
            rig.item(head, face, 0f, 0.0f, q.head * 0.12f, q.head, 0f, 0f, 0f, Rig.CORE);
            headParent = head;
        }
        ears(rig, headParent, q.ears, q.head, p.secondary());

        float lx = q.wid / 2f - q.legT / 2f;
        float lz = q.len / 2f - q.legT / 2f - 0.02f;
        float ly = -q.hei / 2f + 0.04f;
        float legBox = q.legH + 0.04f;
        int fl = rig.bone("leg_fl", body, lx, ly, lz);
        int fr = rig.bone("leg_fr", body, -lx, ly, lz);
        int bl = rig.bone("leg_bl", body, lx, ly, -lz);
        int br = rig.bone("leg_br", body, -lx, ly, -lz);
        for (int leg : new int[]{fl, fr, bl, br}) {
            rig.box(leg, bd(p.secondary()), 0f, -legBox / 2f, 0f, q.legT, legBox, q.legT, Rig.CORE);
        }
        r.legs = new int[]{fl, fr, bl, br};

        tail(rig, body, q.tail, q.len, q.hei, q.tailLen, q.tailT, q.tailPitch, p.tertiary());
        rig.top = q.legH + q.hei + Math.max(0.2f, q.head * 0.5f) + q.neckLen * 0.8f + 0.25f;
        rig.middle = bodyY;
        rig.bodyHalf = q.hei / 2f;
        return rig;
    }

    static void ears(Rig rig, int head, Ears ears, float headScale, Material m) {
        if (ears == Ears.NONE) {
            return;
        }
        float h = headScale * 0.5f;
        float x = h * 0.32f;
        float y = h * 0.5f;
        int el = rig.bone("ear_l", head, x, y, 0.02f);
        int er = rig.bone("ear_r", head, -x, y, 0.02f);
        rig.roles().ears = new int[]{el, er};
        switch (ears) {
            case POINTY -> {
                rig.box(el, bd(m), 0f, h * 0.14f, 0f, h * 0.22f, h * 0.30f, h * 0.10f, 0f, 0f, -10f, Rig.DETAIL);
                rig.box(er, bd(m), 0f, h * 0.14f, 0f, h * 0.22f, h * 0.30f, h * 0.10f, 0f, 0f, 10f, Rig.DETAIL);
            }
            case FLOPPY -> {
                rig.box(el, bd(m), h * 0.12f, -h * 0.12f, 0f, h * 0.10f, h * 0.36f, h * 0.24f, 0f, 0f, -24f, Rig.DETAIL);
                rig.box(er, bd(m), -h * 0.12f, -h * 0.12f, 0f, h * 0.10f, h * 0.36f, h * 0.24f, 0f, 0f, 24f, Rig.DETAIL);
            }
            case TALL -> {
                rig.box(el, bd(m), 0f, h * 0.42f, -0.01f, h * 0.18f, h * 0.84f, h * 0.08f, -8f, 0f, -6f, Rig.CORE);
                rig.box(er, bd(m), 0f, h * 0.42f, -0.01f, h * 0.18f, h * 0.84f, h * 0.08f, -8f, 0f, 6f, Rig.CORE);
            }
            case ROUND -> {
                rig.box(el, bd(m), 0f, h * 0.08f, 0f, h * 0.24f, h * 0.22f, h * 0.10f, Rig.DETAIL);
                rig.box(er, bd(m), 0f, h * 0.08f, 0f, h * 0.24f, h * 0.22f, h * 0.10f, Rig.DETAIL);
            }
            case TUFT -> {
                rig.box(el, bd(m), 0f, h * 0.16f, -0.02f, h * 0.12f, h * 0.30f, h * 0.10f, 0f, 0f, -18f, Rig.DETAIL);
                rig.box(er, bd(m), 0f, h * 0.16f, -0.02f, h * 0.12f, h * 0.30f, h * 0.10f, 0f, 0f, 18f, Rig.DETAIL);
            }
            default -> {
            }
        }
    }

    static void tail(Rig rig, int body, Tail tail, float len, float hei, float tailLen, float tailT, float pitch,
                     Material m) {
        if (tail == Tail.NONE) {
            return;
        }
        int t = rig.bone("tail", body, 0f, hei * 0.22f, -len / 2f + 0.02f, -pitch, 0f, 0f);
        switch (tail) {
            case BUSHY, FLUFF -> {
                float thick = tail == Tail.FLUFF ? tailT * 1.2f : tailT * 1.5f;
                rig.box(t, bd(m), 0f, 0f, -tailLen / 2f, thick, thick, tailLen, Rig.CORE);
            }
            case CURL -> {
                int t2 = rig.bone("tail2", t, 0f, 0f, -tailLen * 0.55f, -50f, 0f, 0f);
                rig.box(t, bd(m), 0f, 0f, -tailLen * 0.28f, tailT, tailT, tailLen * 0.56f, Rig.CORE);
                rig.box(t2, bd(m), 0f, 0f, -tailLen * 0.24f, tailT * 0.9f, tailT * 0.9f, tailLen * 0.5f, Rig.DETAIL);
                rig.roles().tail = new int[]{t, t2};
                return;
            }
            case STUB -> rig.box(t, bd(m), 0f, 0f, -tailT * 0.4f, tailT * 1.1f, tailT * 1.1f, tailT, Rig.DETAIL);
            case HANG -> rig.box(t, bd(m), 0f, 0f, -tailLen / 2f, tailT, tailT * 1.4f, tailLen, Rig.CORE);
            case LONG -> {
                int t2 = rig.bone("tail2", t, 0f, 0f, -tailLen * 0.5f);
                rig.box(t, bd(m), 0f, 0f, -tailLen * 0.25f, tailT, tailT, tailLen * 0.52f, Rig.CORE);
                rig.box(t2, bd(m), 0f, 0f, -tailLen * 0.25f, tailT * 0.8f, tailT * 0.8f, tailLen * 0.5f, Rig.DETAIL);
                rig.roles().tail = new int[]{t, t2};
                return;
            }
            default -> rig.box(t, bd(m), 0f, 0f, -tailLen / 2f, tailT, tailT, tailLen, Rig.CORE);
        }
        rig.roles().tail = new int[]{t};
    }

    /*
     * =========================================================
     * BIPED
     * =========================================================
     */

    static final class Bi {
        float legH = 0.32f, legT = 0.11f;
        float torsoW = 0.30f, torsoH = 0.36f, torsoD = 0.17f;
        float armL = 0.34f, armT = 0.10f, armPitch = 0f;
        float head = 0.72f;
        float hunch = 0f;
        Material legs, arms;

        Bi legs(float h, float t) {
            this.legH = h;
            this.legT = t;
            return this;
        }

        Bi torso(float w, float h, float d) {
            this.torsoW = w;
            this.torsoH = h;
            this.torsoD = d;
            return this;
        }

        Bi arms(float len, float t, float pitch) {
            this.armL = len;
            this.armT = t;
            this.armPitch = pitch;
            return this;
        }

        Bi head(float scale) {
            this.head = scale;
            return this;
        }

        Bi hunch(float deg) {
            this.hunch = deg;
            return this;
        }

        Bi colours(Material legs, Material arms) {
            this.legs = legs;
            this.arms = arms;
            return this;
        }
    }

    static Rig biped(Skeleton skeleton, Bi b, Pal p, ItemStack face) {
        Rig rig = new Rig(skeleton, false);
        Rig.Roles r = rig.roles();
        int hips = rig.bone("body", 0, 0f, b.legH, 0f, -b.hunch, 0f, 0f);
        r.body = hips;
        r.bodyParts.add(rig.box(hips, bd(p.primary()), 0f, b.torsoH / 2f, 0f, b.torsoW, b.torsoH, b.torsoD, Rig.CORE).shadow());
        int head = rig.bone("head", hips, 0f, b.torsoH, 0f, b.hunch * 0.6f, 0f, 0f);
        r.head = head;
        if (face != null) {
            rig.item(head, face, 0f, b.head * 0.25f, 0.0f, b.head, 0f, 0f, 0f, Rig.CORE);
        }
        Material armM = b.arms == null ? p.secondary() : b.arms;
        float ax = b.torsoW / 2f + b.armT / 2f;
        float ay = b.torsoH - b.armT / 2f;
        int al = rig.bone("arm_l", hips, ax, ay, 0f, b.armPitch, 0f, 0f);
        int ar = rig.bone("arm_r", hips, -ax, ay, 0f, b.armPitch, 0f, 0f);
        rig.box(al, bd(armM), 0f, -b.armL / 2f + b.armT / 2f, 0f, b.armT, b.armL, b.armT, Rig.CORE);
        rig.box(ar, bd(armM), 0f, -b.armL / 2f + b.armT / 2f, 0f, b.armT, b.armL, b.armT, Rig.CORE);
        r.arms = new int[]{al, ar};
        Material legM = b.legs == null ? p.secondary() : b.legs;
        float lx = b.legT / 2f + 0.008f;
        int ll = rig.bone("leg_l", 0, lx, b.legH, 0f);
        int lr = rig.bone("leg_r", 0, -lx, b.legH, 0f);
        rig.box(ll, bd(legM), 0f, -b.legH / 2f, 0f, b.legT, b.legH, b.legT, Rig.CORE);
        rig.box(lr, bd(legM), 0f, -b.legH / 2f, 0f, b.legT, b.legH, b.legT, Rig.CORE);
        r.legs = new int[]{ll, lr};
        rig.top = b.legH + b.torsoH + b.head * 0.5f + 0.3f;
        rig.middle = b.legH + b.torsoH * 0.5f;
        return rig;
    }

    /*
     * =========================================================
     * HOPPERS (rabbit / frog / squirrel / sack)
     * =========================================================
     */

    static Rig hopper(float bodyW, float bodyH, float bodyL, float head, float footL, Pal p, ItemStack face,
                      Ears ears, Tail tail, float tailLen) {
        Rig rig = new Rig(Skeleton.HOP, false);
        Rig.Roles r = rig.roles();
        float footH = 0.06f;
        float bodyY = footH + bodyH / 2f;
        int body = rig.bone("body", 0, 0f, bodyY, 0f);
        r.body = body;
        r.bodyParts.add(rig.box(body, bd(p.primary()), 0f, 0f, 0f, bodyW, bodyH, bodyL, -8f, 0f, 0f, Rig.CORE).shadow());
        int h = rig.bone("head", body, 0f, bodyH * 0.42f, bodyL * 0.42f);
        r.head = h;
        rig.item(h, face, 0f, head * 0.12f, head * 0.1f, head, 0f, 0f, 0f, Rig.CORE);
        ears(rig, h, ears, head, p.secondary());
        if (footL > 0f) {
            float fx = bodyW * 0.36f;
            int fl = rig.bone("leg_bl", body, fx, -bodyH / 2f, -bodyL * 0.18f);
            int fr = rig.bone("leg_br", body, -fx, -bodyH / 2f, -bodyL * 0.18f);
            rig.box(fl, bd(p.secondary()), 0f, -footH / 2f + 0.01f, footL * 0.25f, bodyW * 0.26f, footH + 0.04f, footL, Rig.CORE);
            rig.box(fr, bd(p.secondary()), 0f, -footH / 2f + 0.01f, footL * 0.25f, bodyW * 0.26f, footH + 0.04f, footL, Rig.CORE);
            r.legs = new int[]{fl, fr};
        }
        tail(rig, body, tail, bodyL, bodyH, tailLen, Math.max(0.08f, bodyW * 0.38f), 40f, p.tertiary());
        rig.top = footH + bodyH + head * 0.5f + 0.3f + (ears == Ears.TALL ? head * 0.4f : 0f);
        rig.middle = bodyY;
        rig.bodyHalf = bodyH / 2f;
        return rig;
    }

    /*
     * =========================================================
     * BIRDS + BATS
     * =========================================================
     */

    static Rig bird(Skeleton skeleton, float bodyW, float bodyH, float bodyL, float head, float span, float chord,
                    boolean twoSegment, float tailL, Pal p, ItemStack face) {
        Rig rig = new Rig(skeleton, false);
        Rig.Roles r = rig.roles();
        int body = rig.bone("body", 0, 0f, 0f, 0f);
        r.body = body;
        r.bodyParts.add(rig.box(body, bd(p.primary()), 0f, 0f, 0f, bodyW, bodyH, bodyL, -6f, 0f, 0f, Rig.CORE).shadow());
        int h = rig.bone("head", body, 0f, bodyH * 0.38f, bodyL * 0.46f);
        r.head = h;
        rig.item(h, face, 0f, head * 0.1f, head * 0.12f, head, 0f, 0f, 0f, Rig.CORE);
        float wx = bodyW / 2f - 0.01f;
        float inner = twoSegment ? span * 0.52f : span;
        int wl = rig.bone("wing_l", body, wx, bodyH * 0.22f, 0.02f);
        int wr = rig.bone("wing_r", body, -wx, bodyH * 0.22f, 0.02f);
        rig.box(wl, bd(p.secondary()), inner / 2f, 0f, -chord * 0.15f, inner, 0.035f, chord, Rig.CORE);
        rig.box(wr, bd(p.secondary()), -inner / 2f, 0f, -chord * 0.15f, inner, 0.035f, chord, Rig.CORE);
        if (twoSegment) {
            float outer = span - inner;
            int ol = rig.bone("wing_l2", wl, inner, 0f, 0f);
            int or = rig.bone("wing_r2", wr, -inner, 0f, 0f);
            rig.box(ol, bd(p.tertiary()), outer / 2f, 0f, -chord * 0.22f, outer, 0.03f, chord * 0.8f, Rig.CORE);
            rig.box(or, bd(p.tertiary()), -outer / 2f, 0f, -chord * 0.22f, outer, 0.03f, chord * 0.8f, Rig.CORE);
            r.wings = new int[]{wl, wr, ol, or};
        } else {
            r.wings = new int[]{wl, wr};
        }
        if (tailL > 0f) {
            int t = rig.bone("tail", body, 0f, -bodyH * 0.05f, -bodyL / 2f, 6f, 0f, 0f);
            rig.box(t, bd(p.tertiary()), 0f, 0f, -tailL / 2f, bodyW * 0.8f, 0.03f, tailL, Rig.CORE);
            r.tail = new int[]{t};
        }
        rig.top = bodyH / 2f + head * 0.5f + 0.35f;
        rig.middle = 0f;
        rig.bodyHalf = bodyH / 2f;
        return rig;
    }

    /*
     * =========================================================
     * INSECTS (bee, butterfly, fairy, allay)
     * =========================================================
     */

    static Rig insect(Skeleton skeleton, float bodyW, float bodyH, float bodyL, float head, Material body,
                      Material wing, float wingW, float wingL, boolean fourWings, ItemStack face) {
        Rig rig = new Rig(skeleton, false);
        Rig.Roles r = rig.roles();
        int b = rig.bone("body", 0, 0f, 0f, 0f);
        r.body = b;
        r.bodyParts.add(rig.box(b, bd(body), 0f, 0f, -bodyL * 0.1f, bodyW, bodyH, bodyL, -10f, 0f, 0f, Rig.CORE).shadow());
        int h = rig.bone("head", b, 0f, bodyH * 0.2f, bodyL * 0.42f);
        r.head = h;
        if (face != null) {
            rig.item(h, face, 0f, head * 0.08f, head * 0.12f, head, 0f, 0f, 0f, Rig.CORE);
        }
        int wl = rig.bone("wing_l", b, bodyW * 0.3f, bodyH * 0.5f, bodyL * 0.05f);
        int wr = rig.bone("wing_r", b, -bodyW * 0.3f, bodyH * 0.5f, bodyL * 0.05f);
        rig.box(wl, bd(wing), wingW / 2f, 0f, -wingL * 0.2f, wingW, 0.02f, wingL, 0f, -12f, 0f, Rig.CORE).view(0.5f);
        rig.box(wr, bd(wing), -wingW / 2f, 0f, -wingL * 0.2f, wingW, 0.02f, wingL, 0f, 12f, 0f, Rig.CORE).view(0.5f);
        if (fourWings) {
            int ol = rig.bone("wing_l2", b, bodyW * 0.3f, bodyH * 0.4f, -bodyL * 0.2f);
            int or = rig.bone("wing_r2", b, -bodyW * 0.3f, bodyH * 0.4f, -bodyL * 0.2f);
            rig.box(ol, bd(wing), wingW * 0.36f, 0f, -wingL * 0.3f, wingW * 0.72f, 0.02f, wingL * 0.7f, 0f, 24f, 0f, Rig.DETAIL).view(0.4f);
            rig.box(or, bd(wing), -wingW * 0.36f, 0f, -wingL * 0.3f, wingW * 0.72f, 0.02f, wingL * 0.7f, 0f, -24f, 0f, Rig.DETAIL).view(0.4f);
            r.wings = new int[]{wl, wr, ol, or};
        } else {
            r.wings = new int[]{wl, wr};
        }
        rig.top = bodyH / 2f + head * 0.5f + 0.35f;
        rig.middle = 0f;
        rig.bodyHalf = bodyH / 2f;
        return rig;
    }

    /*
     * =========================================================
     * FISH
     * =========================================================
     */

    static Rig fish(float bodyW, float bodyH, float bodyL, float head, float finH, Pal p, ItemStack face) {
        Rig rig = new Rig(Skeleton.SWIM, false);
        Rig.Roles r = rig.roles();
        int b = rig.bone("body", 0, 0f, 0f, 0f);
        r.body = b;
        r.bodyParts.add(rig.box(b, bd(p.primary()), 0f, 0f, -bodyL * 0.12f, bodyW, bodyH, bodyL, Rig.CORE).shadow());
        int h = rig.bone("head", b, 0f, 0f, bodyL * 0.38f);
        r.head = h;
        if (face != null) {
            rig.item(h, face, 0f, 0f, head * 0.1f, head, 0f, 0f, 0f, Rig.CORE);
        }
        int t = rig.bone("tail", b, 0f, 0f, -bodyL * 0.6f);
        rig.box(t, bd(p.tertiary()), 0f, 0f, -finH * 0.32f, 0.025f, finH, finH * 0.62f, Rig.CORE);
        r.tail = new int[]{t};
        rig.box(b, bd(p.secondary()), 0f, bodyH / 2f + finH * 0.16f, -bodyL * 0.15f, 0.02f, finH * 0.34f, bodyL * 0.5f, Rig.DETAIL);
        rig.top = bodyH / 2f + 0.45f;
        rig.middle = 0f;
        return rig;
    }

    /*
     * =========================================================
     * DRIFTERS / SPIRITS
     * =========================================================
     */

    static Rig spirit(Skeleton skeleton, Material core, float coreSize, boolean brightCore, ItemStack face, float head) {
        Rig rig = new Rig(skeleton, false);
        Rig.Roles r = rig.roles();
        int b = rig.bone("body", 0, 0f, 0f, 0f);
        r.body = b;
        Rig.Part c = rig.box(b, bd(core), 0f, 0f, 0f, coreSize, coreSize, coreSize, 0f, 45f, 0f, Rig.CORE).shadow();
        if (brightCore) {
            c.bright();
        }
        r.bodyParts.add(c);
        int h = rig.bone("head", b, 0f, coreSize * 0.25f, coreSize * 0.42f);
        r.head = h;
        if (face != null) {
            rig.item(h, face, 0f, 0f, head * 0.1f, head, 0f, 0f, 0f, Rig.CORE);
        }
        rig.top = coreSize * 0.6f + head * 0.4f + 0.3f;
        rig.middle = 0f;
        return rig;
    }

    /** Orbiting motes around the body (rarity presence). */
    static void orbit(Rig rig, int count, float radius, float y, Material block, ItemStack item, float scale, int tier) {
        int[] orbit = new int[count];
        int body = rig.roles().body < 0 ? 0 : rig.roles().body;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2.0 * i / count;
            int o = rig.bone("orbit_" + i, body, (float) Math.cos(a) * radius, y, (float) Math.sin(a) * radius);
            if (item != null) {
                rig.item(o, item, 0f, 0f, 0f, scale, 0f, 0f, 0f, tier).bright().view(0.35f);
            } else {
                rig.box(o, bd(block), 0f, 0f, 0f, scale, scale, scale, 30f, 45f, 0f, tier).bright().view(0.35f);
            }
            orbit[i] = o;
        }
        rig.roles().orbit = orbit;
    }

    /** Two small glowing eyes on a bone, facing +Z. */
    static void eyes(Rig rig, int bone, Material m, float x, float y, float z, float s, int tier) {
        rig.box(bone, bd(m), x, y, z, s, s, 0.02f, tier).bright();
        rig.box(bone, bd(m), -x, y, z, s, s, 0.02f, tier).bright();
    }

    /*
     * =========================================================
     * WESTERN DRAGON
     * =========================================================
     */

    record DragonPal(Material scale, Material belly, Material horn, Material membrane, Material spine, Material eye) {
    }

    /**
     * Four legs, two two-segment wings, a two-bone neck, articulated jaw and a five-segment tail.
     * Around 30 parts — spectacle, kept in check by LOD, view ranges and sleep.
     */
    static Rig dragon(DragonPal p, ItemStack face, boolean rich) {
        Rig rig = new Rig(Skeleton.DRAGON, true);
        Rig.Roles r = rig.roles();
        float legH = 0.42f;
        float len = 0.95f, wid = 0.46f, hei = 0.42f;
        float bodyY = legH + hei / 2f;
        int body = rig.bone("body", 0, 0f, bodyY, 0f);
        r.body = body;
        r.bodyParts.add(rig.box(body, bd(p.scale()), 0f, 0f, 0f, wid, hei, len, Rig.CORE).shadow().view(1.0f));
        rig.box(body, bd(p.belly()), 0f, -hei * 0.32f, 0.02f, wid * 0.82f, hei * 0.42f, len * 0.86f, Rig.DETAIL).view(0.6f);
        int chest = rig.bone("chest", body, 0f, 0.02f, len * 0.42f);
        r.chest = chest;
        rig.box(chest, bd(p.scale()), 0f, 0.02f, 0.08f, wid * 1.08f, hei * 1.1f, 0.36f, Rig.CORE).view(1.0f);

        int n1 = rig.bone("neck1", chest, 0f, hei * 0.28f, 0.22f, 38f, 0f, 0f);
        int n2 = rig.bone("neck2", n1, 0f, 0f, 0.3f, 14f, 0f, 0f);
        rig.box(n1, bd(p.scale()), 0f, 0f, 0.15f, 0.22f, 0.22f, 0.34f, Rig.CORE).view(1.0f);
        rig.box(n2, bd(p.scale()), 0f, 0f, 0.15f, 0.19f, 0.19f, 0.32f, Rig.CORE).view(1.0f);
        r.neck = new int[]{n1, n2};
        int head = rig.bone("head", n2, 0f, 0.02f, 0.3f, -52f, 0f, 0f);
        r.head = head;
        rig.item(head, face, 0f, 0.06f, 0.02f, 0.9f, 0f, 0f, 0f, Rig.CORE).view(1.0f);
        rig.box(head, bd(p.scale()), 0f, -0.02f, 0.3f, 0.26f, 0.13f, 0.28f, Rig.CORE).view(1.0f);
        int jaw = rig.bone("jaw", head, 0f, -0.12f, 0.12f);
        r.jaw = jaw;
        rig.box(jaw, bd(p.belly()), 0f, -0.02f, 0.18f, 0.22f, 0.07f, 0.32f, Rig.CORE).view(0.9f);
        rig.box(head, bd(p.horn()), 0.1f, 0.26f, -0.14f, 0.055f, 0.055f, 0.34f, -32f, 12f, 0f, Rig.CORE).view(1.0f);
        rig.box(head, bd(p.horn()), -0.1f, 0.26f, -0.14f, 0.055f, 0.055f, 0.34f, -32f, -12f, 0f, Rig.CORE).view(1.0f);
        eyes(rig, head, p.eye(), 0.115f, 0.1f, 0.245f, 0.05f, Rig.DETAIL);

        float lx = wid / 2f - 0.06f;
        float lz = len / 2f - 0.1f;
        float ly = -hei / 2f + 0.06f;
        int fl = rig.bone("leg_fl", body, lx, ly, lz);
        int fr = rig.bone("leg_fr", body, -lx, ly, lz);
        int bl = rig.bone("leg_bl", body, lx, ly, -lz);
        int br = rig.bone("leg_br", body, -lx, ly, -lz);
        float legBox = legH + 0.06f;
        for (int leg : new int[]{fl, fr, bl, br}) {
            rig.box(leg, bd(p.scale()), 0f, -legBox / 2f, 0.02f, 0.13f, legBox, 0.15f, Rig.CORE).view(0.8f);
        }
        r.legs = new int[]{fl, fr, bl, br};

        int[] tail = new int[5];
        int parent = body;
        float z = -len / 2f + 0.04f;
        float t = 0.28f;
        for (int i = 0; i < 5; i++) {
            int seg = rig.bone("tail" + i, parent, 0f, i == 0 ? hei * 0.1f : 0f, i == 0 ? z : -0.3f, i == 0 ? -10f : 4f, 0f, 0f);
            rig.box(seg, bd(i % 2 == 0 ? p.scale() : p.spine()), 0f, 0f, -0.15f, t, t * 0.92f, 0.32f, Rig.CORE).view(0.9f);
            tail[i] = seg;
            parent = seg;
            t *= 0.78f;
        }
        rig.box(tail[4], bd(p.horn()), 0f, 0f, -0.36f, 0.22f, 0.04f, 0.16f, 0f, 45f, 0f, Rig.DETAIL).view(0.6f);
        r.tail = tail;

        // Wings: arm bone carries a spar + inner membrane, the outer segment folds on its own joint.
        float wx = wid / 2f;
        int wl = rig.bone("wing_l", body, wx - 0.02f, hei * 0.42f, len * 0.22f);
        int wr = rig.bone("wing_r", body, -wx + 0.02f, hei * 0.42f, len * 0.22f);
        rig.box(wl, bd(p.horn()), 0.36f, 0.02f, 0f, 0.72f, 0.06f, 0.07f, Rig.CORE).view(1.0f);
        rig.box(wr, bd(p.horn()), -0.36f, 0.02f, 0f, 0.72f, 0.06f, 0.07f, Rig.CORE).view(1.0f);
        rig.box(wl, bd(p.membrane()), 0.36f, 0f, -0.3f, 0.7f, 0.025f, 0.58f, Rig.CORE).view(1.0f);
        rig.box(wr, bd(p.membrane()), -0.36f, 0f, -0.3f, 0.7f, 0.025f, 0.58f, Rig.CORE).view(1.0f);
        int ol = rig.bone("wing_l2", wl, 0.72f, 0.02f, 0f);
        int or = rig.bone("wing_r2", wr, -0.72f, 0.02f, 0f);
        rig.box(ol, bd(p.horn()), 0.3f, 0f, 0f, 0.62f, 0.05f, 0.06f, 0f, -14f, 0f, Rig.CORE).view(1.0f);
        rig.box(or, bd(p.horn()), -0.3f, 0f, 0f, 0.62f, 0.05f, 0.06f, 0f, 14f, 0f, Rig.CORE).view(1.0f);
        rig.box(ol, bd(p.membrane()), 0.28f, -0.01f, -0.26f, 0.58f, 0.022f, 0.5f, 0f, -10f, 0f, Rig.CORE).view(1.0f);
        rig.box(or, bd(p.membrane()), -0.28f, -0.01f, -0.26f, 0.58f, 0.022f, 0.5f, 0f, 10f, 0f, Rig.CORE).view(1.0f);
        r.wings = new int[]{wl, wr, ol, or};

        int spines = rich ? 4 : 3;
        int[] spikes = new int[spines];
        for (int i = 0; i < spines; i++) {
            float sz = len * 0.32f - i * (len * 0.7f / spines);
            int s = rig.bone("spike" + i, body, 0f, hei / 2f, sz);
            rig.box(s, bd(p.spine()), 0f, 0.05f, 0f, 0.05f, 0.14f - i * 0.015f, 0.14f, -32f, 0f, 0f, Rig.DETAIL).view(0.55f);
            spikes[i] = s;
        }
        r.spikes = spikes;
        rig.top = legH + hei + 0.95f;
        rig.middle = bodyY;
        return rig;
    }

    /*
     * =========================================================
     * WYRM (eastern dragon)
     * =========================================================
     */

    static Rig wyrm(DragonPal p, ItemStack face, int segments, Material mane) {
        Rig rig = new Rig(Skeleton.WYRM, true);
        Rig.Roles r = rig.roles();
        int head = rig.bone("head", 0, 0f, 0.1f, 0.55f);
        r.head = head;
        r.body = head;
        rig.item(head, face, 0f, 0.04f, 0.02f, 0.84f, 0f, 0f, 0f, Rig.CORE).view(1.0f);
        rig.box(head, bd(p.scale()), 0f, -0.04f, 0.27f, 0.24f, 0.12f, 0.26f, Rig.CORE).view(1.0f);
        int jaw = rig.bone("jaw", head, 0f, -0.12f, 0.1f);
        r.jaw = jaw;
        rig.box(jaw, bd(p.belly()), 0f, -0.02f, 0.17f, 0.2f, 0.06f, 0.28f, Rig.CORE).view(0.9f);
        rig.box(head, bd(p.horn()), 0.09f, 0.24f, -0.12f, 0.05f, 0.05f, 0.36f, -40f, 16f, 0f, Rig.CORE).view(1.0f);
        rig.box(head, bd(p.horn()), -0.09f, 0.24f, -0.12f, 0.05f, 0.05f, 0.36f, -40f, -16f, 0f, Rig.CORE).view(1.0f);
        rig.box(head, bd(mane), 0f, 0.12f, -0.2f, 0.3f, 0.2f, 0.12f, Rig.DETAIL).view(0.7f);
        rig.box(head, bd(p.horn()), 0.15f, -0.08f, 0.36f, 0.02f, 0.02f, 0.3f, -10f, 30f, 0f, Rig.DETAIL).view(0.5f);
        rig.box(head, bd(p.horn()), -0.15f, -0.08f, 0.36f, 0.02f, 0.02f, 0.3f, -10f, -30f, 0f, Rig.DETAIL).view(0.5f);
        eyes(rig, head, p.eye(), 0.11f, 0.09f, 0.235f, 0.045f, Rig.DETAIL);
        int[] segs = new int[segments];
        float t = 0.3f;
        for (int i = 0; i < segments; i++) {
            int s = rig.bone("seg" + i, 0, 0f, 0.1f, 0.3f - i * 0.3f);
            Material m = i % 3 == 1 ? p.spine() : p.scale();
            rig.box(s, bd(m), 0f, 0f, 0f, t, t * 0.9f, 0.34f, Rig.CORE).view(1.0f);
            if (i % 2 == 0 && i < segments - 2) {
                rig.box(s, bd(mane), 0f, t * 0.5f + 0.03f, 0f, 0.04f, 0.1f, 0.2f, Rig.DETAIL).view(0.5f);
            }
            segs[i] = s;
            t = Math.max(0.1f, t * 0.9f);
        }
        rig.box(segs[segments - 1], bd(p.membrane()), 0f, 0f, -0.28f, 0.02f, 0.3f, 0.3f, Rig.DETAIL).view(0.7f);
        r.segments = segs;
        r.bodyParts.add(rig.parts.get(0).shadow());
        int fl = rig.bone("leg_fl", segs[1], 0.14f, -0.1f, 0f);
        int fr = rig.bone("leg_fr", segs[1], -0.14f, -0.1f, 0f);
        int bl = rig.bone("leg_bl", segs[Math.min(segments - 1, segments / 2 + 1)], 0.12f, -0.08f, 0f);
        int br = rig.bone("leg_br", segs[Math.min(segments - 1, segments / 2 + 1)], -0.12f, -0.08f, 0f);
        for (int leg : new int[]{fl, fr, bl, br}) {
            rig.box(leg, bd(p.scale()), 0f, -0.1f, 0.04f, 0.07f, 0.2f, 0.08f, 25f, 0f, 0f, Rig.DETAIL).view(0.6f);
        }
        r.legs = new int[]{fl, fr, bl, br};
        rig.top = 0.9f;
        rig.middle = 0.1f;
        return rig;
    }
}
