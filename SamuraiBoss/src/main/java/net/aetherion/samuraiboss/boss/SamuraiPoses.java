package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.render.anim.PoseIK;
import net.aetherion.samuraiboss.render.anim.PoseSpec;
import net.aetherion.samuraiboss.render.model.ModelDef;
import net.aetherion.samuraiboss.util.MathUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static net.aetherion.samuraiboss.render.anim.PoseIK.v;

/**
 * Die Schlüsselposen des Kensei. Arme werden per inverser Kinematik platziert
 * ("Hand hierhin, Klinge dorthin"), Beine und Rumpf direkt über Winkel.
 *
 * <p>Konventionen (Grad): Rx negativ = Glied nach vorne, Rumpf/Kopf Rx positiv = nach vorne
 * neigen; Ry positiv = nach links (+X) drehen; Oberschenkel rechts Rz negativ = nach außen.</p>
 */
public final class SamuraiPoses {

    public final PoseSpec stand, standBreath, walkA, walkB;
    public final PoseSpec iaiReady, iaiDraw, iaiEnd, chiburi, notoStart, notoEnd;
    public final PoseSpec kamae, kamaeBreath, jodan, kesaEnd, gyakuEnd, yokoWind, yokoEnd;
    public final PoseSpec thrustWind, thrustEnd, waki, raiseSky, pointBlade;
    public final PoseSpec seiza, seizaBow, bow, kneelExhausted, plantSword, crouchLeap, airPose;
    public final PoseSpec spinA, spinB, hurt, drawSlowStart, drawSlowMid;
    public final PoseSpec floatIdle, deathKneel, deathBow;

    private final ModelDef def;

    public SamuraiPoses(ModelDef def) {
        this.def = def;

        // ------------------------------------------------------------------ Stehen (Scheide)
        stand = legs(PoseSpec.of(), 0.35f, -4, 6, -3, 5)
                .r("torso", 3, 0, 0).r("neck", 2, 0, 0).r("head", 1, 0, 0)
                .r("arm_r", -6, 0, -6).r("forearm_r", -16, 0, 0).r("hand_r", 0, 0, 0);
        leftOnSaya(stand);
        standBreath = stand.with().r("torso", 1.6f, 0, 0).r("neck", 3, 0, 0).p("hips", 0, -0.25f, 0);
        leftOnSaya(standBreath);

        // Gehen (Kontaktposen) – linke Hand bleibt an der Scheide
        walkA = PoseSpec.of().p("hips", 0, -0.6f, 0)
                .r("thigh_r", -24, 0, -3).r("shin_r", 8, 0, 0).r("foot_r", 10, 0, 0)
                .r("thigh_l", 18, 0, 3).r("shin_l", 22, 0, 0).r("foot_l", -8, 0, 0)
                .r("hips", 0, 6, 0).r("torso", 5, -8, 0).r("head", -2, 2, 0)
                .r("arm_r", 14, 0, -6).r("forearm_r", -18, 0, 0);
        leftOnSaya(walkA);
        walkB = walkA.mirrored();
        // Spiegeln würde die linke Hand von der Scheide nehmen -> neu setzen, rechten Arm schwingen
        walkB.r("arm_r", -22, 0, -6).r("forearm_r", -26, 0, 0).r("hand_r", 0, 0, 0)
                .r("hips", 0, -6, 0).r("torso", 5, 8, 0).r("head", -2, -2, 0);
        leftOnSaya(walkB);

        // ------------------------------------------------------------------ Iaijutsu
        // Bereitschaft: tief, rechte Hand am Griff in der Scheide, linke Hand öffnet die Scheide
        iaiReady = PoseSpec.of().p("hips", 0, -2.6f, 0.4f).r("hips", 0, -16, 0)
                .r("thigh_r", -38, 6, -4).r("shin_r", 44, 0, 0).r("foot_r", -6, 0, 0)
                .r("thigh_l", 16, 0, 5).r("shin_l", 38, 0, 0).r("foot_l", -20, 0, 0)
                .r("torso", 16, 12, 0).r("neck", -6, -4, 0).r("head", -8, -6, 0)
                .r("saya", 0, 0, 0);
        handOnSheathedHilt(iaiReady);
        leftAtKoiguchi(iaiReady);

        // Ziehen (Zwischenpose): Klinge halb heraus, Arm quer vor dem Körper
        iaiDraw = iaiReady.with().p("hips", 0, -2.8f, 0.8f).r("hips", 0, -6, 0).r("torso", 12, 2, 0)
                .r("saya", 0, -10, 0);
        PoseIK.oneHand(def, iaiDraw, v(0.5f, 17.0f, 9.5f), v(-0.35f, 0.05f, 1f), v(0, 1, 0), v(0.2f, -1, -0.6f));
        leftAtKoiguchi(iaiDraw);

        // Ende des Nukitsuke (horizontaler Ziehschnitt nach rechts)
        iaiEnd = PoseSpec.of().p("hips", 0, -3.2f, 1.2f).r("hips", 0, -24, 0)
                .r("thigh_r", -52, 10, -6).r("shin_r", 58, 0, 0).r("foot_r", -6, 0, 0)
                .r("thigh_l", 26, 0, 6).r("shin_l", 36, 0, 0).r("foot_l", -22, 0, 0)
                .r("torso", 10, -14, 0).r("neck", -4, 6, 0).r("head", -6, 10, 0)
                .r("saya", 0, -18, 0);
        PoseIK.oneHand(def, iaiEnd, v(-14.0f, 20.0f, 6.5f), v(-1f, 0.04f, 0.25f), v(0, 0.25f, -1f), v(0.3f, -1f, -0.2f));
        leftAtKoiguchi(iaiEnd);

        // Chiburi – Blut von der Klinge schleudern
        chiburi = iaiEnd.with().r("torso", 6, -6, 0).r("head", -4, 4, 0);
        PoseIK.oneHand(def, chiburi, v(-10.5f, 15.5f, 7.0f), v(-0.55f, -0.75f, 0.35f), v(0, 0.3f, -1f), v(0.5f, -1f, -0.3f));
        leftAtKoiguchi(chiburi);

        // Nōtō – Klinge zurück in die Scheide (Spitze am Koiguchi)
        notoStart = stand.with().p("hips", 0, -1.4f, 0).r("torso", 8, 10, 0).r("head", 6, 4, 0)
                .r("thigh_r", -16, 0, -4).r("shin_r", 22, 0, 0);
        leftAtKoiguchi(notoStart);
        bladeAcrossToSaya(notoStart);
        notoEnd = stand.with().p("hips", 0, -1.0f, 0).r("torso", 6, 4, 0).r("head", 3, 0, 0);
        handOnSheathedHilt(notoEnd);
        leftAtKoiguchi(notoEnd);

        // ------------------------------------------------------------------ Gezogenes Schwert
        // Chūdan-no-kamae: beidhändig, Spitze auf Kehlenhöhe des Gegners
        kamae = PoseSpec.of().p("hips", 0, -1.3f, 0).r("hips", 0, -10, 0)
                .r("thigh_r", -20, 4, -4).r("shin_r", 20, 0, 0).r("foot_r", 0, 0, 0)
                .r("thigh_l", 14, 0, 4).r("shin_l", 22, 0, 0).r("foot_l", -14, 0, 0)
                .r("torso", 6, 8, 0).r("neck", 0, 1, 0).r("head", -2, 1, 0);
        PoseIK.twoHand(def, kamae, v(-0.6f, 15.8f, 8.4f), v(0.05f, 0.42f, 1f), v(0, 1, -0.2f),
                v(-0.6f, -1, -0.2f), v(0.7f, -1, -0.3f));
        kamaeBreath = kamae.with().p("hips", 0, -1.5f, 0).r("torso", 4.5f, 8, 0);
        PoseIK.twoHand(def, kamaeBreath, v(-0.6f, 15.6f, 8.4f), v(0.05f, 0.44f, 1f), v(0, 1, -0.2f),
                v(-0.6f, -1, -0.2f), v(0.7f, -1, -0.3f));

        // Jōdan – Schwert über dem Kopf (Ausholen)
        jodan = kamae.with().p("hips", 0, -0.8f, 0).r("torso", -6, 10, 0).r("head", 2, -2, 0);
        PoseIK.twoHand(def, jodan, v(-1.0f, 34.0f, 3.5f), v(0.15f, 0.55f, -1f), v(0, -0.5f, -1f),
                v(-1, 0.2f, 0), v(1, 0.2f, 0));

        // Kesagiri-Ende: diagonal von oben rechts nach unten links durchgezogen
        kesaEnd = PoseSpec.of().p("hips", 0, -3.0f, 1.0f).r("hips", 0, 18, 0)
                .r("thigh_r", -46, 0, -6).r("shin_r", 50, 0, 0).r("foot_r", -4, 0, 0)
                .r("thigh_l", 24, 0, 6).r("shin_l", 34, 0, 0).r("foot_l", -20, 0, 0)
                .r("torso", 22, 22, 0).r("neck", -8, -6, 0).r("head", -10, -8, 0);
        PoseIK.twoHand(def, kesaEnd, v(5.0f, 11.5f, 7.5f), v(0.6f, -0.55f, 0.6f), v(-0.6f, 0.5f, 0.6f),
                v(-0.8f, -0.6f, -0.4f), v(0.9f, -0.6f, -0.4f));

        // Gyaku-Kesa (aufsteigend) Ende: Klinge oben links
        gyakuEnd = kamae.with().p("hips", 0, -0.6f, 0.6f).r("hips", 0, 14, 0).r("torso", -4, 22, -4).r("head", 0, -10, 0);
        PoseIK.twoHand(def, gyakuEnd, v(5.5f, 30.0f, 6.0f), v(0.55f, 0.75f, -0.1f), v(-0.5f, 0.2f, -0.8f),
                v(-0.6f, -1, -0.4f), v(0.8f, -0.4f, -0.6f));

        // Yokogiri (horizontal): ausholen rechts, Ende links
        yokoWind = kamae.with().r("hips", 0, -30, 0).r("torso", 4, -30, 0).r("head", 0, 28, 0);
        PoseIK.twoHand(def, yokoWind, v(-9.0f, 19.0f, -1.0f), v(-0.85f, 0.2f, -0.45f), v(0, 1, 0),
                v(-0.3f, -1, -0.3f), v(0.6f, -1, 0.4f));
        yokoEnd = kamae.with().p("hips", 0, -2.4f, 0.8f).r("hips", 0, 28, 0).r("torso", 8, 34, 0).r("head", -4, -30, 0)
                .r("thigh_r", -40, 0, -6).r("shin_r", 44, 0, 0);
        PoseIK.twoHand(def, yokoEnd, v(8.5f, 18.0f, 4.0f), v(0.85f, -0.05f, -0.5f), v(0, 1, 0),
                v(-0.5f, -1, 0.3f), v(0.4f, -1, -0.6f));

        // Tsuki (Stich)
        thrustWind = kamae.with().p("hips", 0, -1.6f, -0.6f).r("torso", 2, 10, 0);
        PoseIK.twoHand(def, thrustWind, v(-1.2f, 18.5f, 2.5f), v(0.05f, 0.05f, 1f), v(0, 1, 0),
                v(-0.6f, -1, -0.3f), v(0.7f, -1, -0.3f));
        thrustEnd = kamae.with().p("hips", 0, -2.8f, 3.0f).r("thigh_r", -50, 0, -4).r("shin_r", 48, 0, 0)
                .r("thigh_l", 30, 0, 4).r("shin_l", 20, 0, 0).r("torso", 18, 6, 0).r("head", -14, -2, 0);
        PoseIK.twoHand(def, thrustEnd, v(-0.4f, 19.0f, 13.5f), v(0.0f, -0.02f, 1f), v(0, 1, 0),
                v(-0.6f, -1, -0.1f), v(0.6f, -1, -0.1f));

        // Waki-gamae: Schwert tief rechts, Spitze nach hinten (Geisterhaltung)
        waki = PoseSpec.of().p("hips", 0, -1.4f, 0).r("hips", 0, -22, 0)
                .r("thigh_r", -16, 0, -5).r("shin_r", 18, 0, 0)
                .r("thigh_l", 18, 0, 5).r("shin_l", 24, 0, 0).r("foot_l", -12, 0, 0)
                .r("torso", 6, -18, 0).r("head", -2, 20, 0);
        PoseIK.twoHand(def, waki, v(-6.5f, 13.0f, 1.5f), v(-0.35f, -0.45f, -1f), v(0.3f, 1, -0.2f),
                v(-0.6f, -1, 0.2f), v(0.6f, -1, 0.5f));

        // Klinge in den Himmel (Beschwörung der Geisterklingen)
        raiseSky = PoseSpec.of().p("hips", 0, -0.4f, 0)
                .r("thigh_r", -6, 0, -6).r("thigh_l", 4, 0, 6).r("torso", -8, 0, 0).r("neck", -6, 0, 0).r("head", -14, 0, 0);
        PoseIK.twoHand(def, raiseSky, v(-0.6f, 37.5f, 1.8f), v(0.02f, 1f, 0.08f), v(0, 0, -1f),
                v(-1, 0.1f, 0.2f), v(1, 0.1f, 0.2f));

        // Klinge auf ein Ziel richten (einhändig, Arm gestreckt)
        pointBlade = PoseSpec.of().p("hips", 0, -0.8f, 0).r("hips", 0, -14, 0)
                .r("thigh_r", -14, 0, -5).r("shin_r", 12, 0, 0).r("thigh_l", 10, 0, 5).r("shin_l", 14, 0, 0)
                .r("torso", 2, -12, 0).r("head", 0, 12, 0);
        PoseIK.oneHand(def, pointBlade, v(-4.5f, 22.0f, 11.0f), v(0.05f, 0.08f, 1f), v(0, 1, 0), v(-0.4f, -1, -0.2f));
        pointBlade.r("arm_l", 20, 0, 18).r("forearm_l", -30, 0, 0).r("hand_l", 0, 0, 0);

        // ------------------------------------------------------------------ Knien & Verbeugen
        seiza = PoseSpec.of().p("hips", 0, -8.6f, -1.5f)
                .r("thigh_r", -84, 0, -4).r("shin_r", 168, 0, 0).r("foot_r", 0, 0, 0)
                .r("thigh_l", -84, 0, 4).r("shin_l", 168, 0, 0).r("foot_l", 0, 0, 0)
                .r("torso", 2, 0, 0).r("head", 4, 0, 0);
        handsOnThighs(seiza);
        seizaBow = seiza.with().r("torso", 34, 0, 0).r("neck", 10, 0, 0).r("head", 14, 0, 0);
        handsOnThighs(seizaBow);

        bow = stand.with().r("torso", 28, 0, 0).r("neck", 6, 0, 0).r("head", 10, 0, 0).p("hips", 0, -0.2f, -0.6f);
        leftOnSaya(bow);
        bow.r("arm_r", 18, 0, -4).r("forearm_r", -8, 0, 0);

        kneelExhausted = PoseSpec.of().p("hips", 0, -6.0f, 0)
                .r("thigh_r", -88, 0, -6).r("shin_r", 92, 0, 0).r("foot_r", -2, 0, 0)
                .r("thigh_l", 8, 0, 6).r("shin_l", 96, 0, 0).r("foot_l", -28, 0, 0)
                .r("torso", 28, -6, 0).r("neck", 6, 0, 0).r("head", 12, 0, 0);
        PoseIK.twoHand(def, kneelExhausted, v(-0.8f, 13.5f, 9.0f), v(0.0f, -1f, 0.18f), v(0, 0, 1f),
                v(-1, -0.2f, -0.2f), v(1, -0.2f, -0.2f));

        plantSword = PoseSpec.of().p("hips", 0, -0.5f, 0)
                .r("thigh_r", -6, 0, -7).r("thigh_l", 4, 0, 7).r("torso", 6, 0, 0).r("head", 6, 0, 0);
        PoseIK.twoHand(def, plantSword, v(-0.6f, 17.0f, 7.5f), v(0.0f, -1f, 0.1f), v(0, 0, 1f),
                v(-1, -0.3f, -0.1f), v(1, -0.3f, -0.1f));

        crouchLeap = PoseSpec.of().p("hips", 0, -4.5f, 0.6f)
                .r("thigh_r", -64, 0, -6).r("shin_r", 92, 0, 0).r("foot_r", -24, 0, 0)
                .r("thigh_l", -40, 0, 6).r("shin_l", 86, 0, 0).r("foot_l", -40, 0, 0)
                .r("torso", 30, 0, 0).r("head", -18, 0, 0);
        handOnSheathedHilt(crouchLeap);
        leftAtKoiguchi(crouchLeap);

        airPose = PoseSpec.of().p("hips", 0, 0, 0)
                .r("thigh_r", -30, 0, -8).r("shin_r", 60, 0, 0).r("thigh_l", 10, 0, 8).r("shin_l", 70, 0, 0)
                .r("torso", 8, 0, 0).r("head", 10, 0, 0);
        handOnSheathedHilt(airPose);
        leftAtKoiguchi(airPose);

        drawSlowStart = airPose.with().r("torso", 10, 14, 0).r("head", 6, -10, 0);
        handOnSheathedHilt(drawSlowStart);
        leftAtKoiguchi(drawSlowStart);
        drawSlowMid = airPose.with().r("torso", 6, 4, 0).r("saya", 0, -12, 0);
        PoseIK.oneHand(def, drawSlowMid, v(1.0f, 18.5f, 9.0f), v(-0.45f, 0.08f, 1f), v(0, 1, 0), v(0.2f, -1, -0.6f));
        leftAtKoiguchi(drawSlowMid);

        // ------------------------------------------------------------------ Wirbel
        spinA = kamae.with().r("hips", 0, -60, 0).r("torso", 6, -40, 0).p("hips", 0, -1.8f, 0);
        PoseIK.twoHand(def, spinA, v(-11.0f, 19.0f, -2.0f), v(-0.9f, 0.05f, -0.4f), v(0, 1, 0),
                v(-0.2f, -1, -0.4f), v(0.7f, -1, 0.3f));
        spinB = kamae.with().r("hips", 0, 60, 0).r("torso", 6, 40, 0).p("hips", 0, -1.8f, 0);
        PoseIK.twoHand(def, spinB, v(11.0f, 19.0f, -2.0f), v(0.9f, 0.05f, -0.4f), v(0, 1, 0),
                v(-0.6f, -1, 0.3f), v(0.2f, -1, -0.4f));

        hurt = kamae.with().r("torso", -10, -6, 4).r("head", -12, 6, 0).p("hips", 0, -1.0f, -1.2f);
        PoseIK.twoHand(def, hurt, v(-0.8f, 17.0f, 6.0f), v(0.1f, 0.7f, 0.7f), v(0, 1, -0.6f),
                v(-0.6f, -1, -0.2f), v(0.7f, -1, -0.3f));

        // ------------------------------------------------------------------ Geist & Tod
        floatIdle = waki.with().p("hips", 0, 0, 0)
                .r("thigh_r", -10, 0, -4).r("shin_r", 26, 0, 0).r("thigh_l", 6, 0, 4).r("shin_l", 34, 0, 0);
        PoseIK.twoHand(def, floatIdle, v(-6.5f, 13.0f, 1.5f), v(-0.35f, -0.45f, -1f), v(0.3f, 1, -0.2f),
                v(-0.6f, -1, 0.2f), v(0.6f, -1, 0.5f));

        deathKneel = seiza.with().r("torso", 6, 0, 0).r("head", -6, 0, 0);
        PoseIK.oneHand(def, deathKneel, v(-5.5f, 7.5f, 6.0f), v(0.25f, -0.08f, 1f), v(0, 1, 0), v(-1, -0.4f, 0));
        deathKneel.r("arm_l", -24, 0, 8).r("forearm_l", -40, 0, 0).r("hand_l", 0, 0, 0);
        deathBow = deathKneel.with().r("torso", 40, 0, 0).r("neck", 12, 0, 0).r("head", 16, 0, 0);
        PoseIK.oneHand(def, deathBow, v(-5.5f, 6.2f, 7.5f), v(0.25f, -0.08f, 1f), v(0, 1, 0), v(-1, -0.4f, 0));
    }

    // ------------------------------------------------------------------ Bausteine

    /** Beine in leichter Grätsche mit gebeugten Knien. */
    private static PoseSpec legs(PoseSpec p, float drop, float thighR, float shinR, float thighL, float shinL) {
        return p.p("hips", 0, -drop, 0)
                .r("thigh_r", thighR, 0, -5).r("shin_r", shinR, 0, 0).r("foot_r", -(thighR + shinR), 0, 0)
                .r("thigh_l", thighL, 0, 5).r("shin_l", shinL, 0, 0).r("foot_l", -(thighL + shinL), 0, 0);
    }

    /** Lokaler Punkt (Pixel) im Raum der Scheide, inkl. ihrer Vor-Drehung. */
    private Vector3f sayaPoint(PoseSpec p, float alongPx) {
        // Ruhe-Vordrehung der Scheide (wie im Modell): (-17, -9, 0)
        Vector3f local = MathUtil.euler(-17, -9, 0).transform(new Vector3f(0, 0, alongPx));
        return PoseIK.pointOnBone(def, p, "saya", local);
    }

    /** Achse der Scheide in Weltraum (vom Mund zur Spitze) und "oben" der Scheide. */
    private Quaternionf sayaWorldRot(PoseSpec p) {
        Quaternionf boneRot = PoseIK.boneRotation(def, p, "saya");
        return boneRot.mul(MathUtil.euler(-17, -9, 0));
    }

    /** Linke Hand ruht auf der Scheide nahe dem Koiguchi. */
    private void leftOnSaya(PoseSpec p) {
        Vector3f t = sayaPoint(p, -1.0f).add(0.2f, 0.9f, 0.3f);
        Quaternionf rot = sayaWorldRot(p).mul(MathUtil.euler(0, 0, 70));
        PoseIK.reach(def, p, "l", t, rot, v(1f, -0.25f, -1.1f));
    }

    /** Linke Hand umgreift den Scheidenmund (Daumen am Stichblatt). */
    private void leftAtKoiguchi(PoseSpec p) {
        Vector3f t = sayaPoint(p, -0.6f).add(0.1f, 0.4f, 0f);
        Quaternionf rot = sayaWorldRot(p).mul(MathUtil.euler(0, 0, 80));
        PoseIK.reach(def, p, "l", t, rot, v(1f, -0.3f, -1f));
    }

    /**
     * Rechte Hand am Griff des eingesteckten Schwerts – EXAKT so ausgerichtet wie das Schwert in
     * der Scheide, damit beim Ziehen das Hand-Katana nahtlos an dessen Stelle erscheint.
     */
    private void handOnSheathedHilt(PoseSpec p) {
        Quaternionf saya = sayaWorldRot(p);
        // Klinge in der Scheide zeigt zur Spitze (-Z der Scheide), Schneide nach oben -> Rücken nach unten
        Vector3f dir = saya.transform(new Vector3f(0, 0, -1));
        Vector3f up = saya.transform(new Vector3f(0, -1, 0));
        // Griff-Mitte des Hand-Katanas liegt 0 px vom Knochen; Stichblatt bei +3.2 px.
        // Die eingesteckte Klinge: Stichblatt am Mund (+0.2), Griffmitte bei +3.2 Richtung Griff.
        Vector3f grip = sayaPoint(p, 3.4f);
        PoseIK.oneHand(def, p, grip, dir, up, v(-0.6f, -1f, -0.4f));
    }

    /**
     * Klassisches Nōtō: Die rechte Hand ist vorne rechts, die Klinge liegt quer über dem Körper
     * und zeigt mit der Spitze auf den Scheidenmund (Schneide nach oben).
     */
    private void bladeAcrossToSaya(PoseSpec p) {
        Vector3f mouth = sayaPoint(p, 0f);
        Vector3f grip = v(-6.5f, 19.5f, 12.5f);
        Vector3f dir = new Vector3f(mouth).sub(grip).normalize();
        PoseIK.oneHand(def, p, grip, dir, v(0, -1, 0), v(-1f, -0.6f, -0.2f));
    }

    /** Klingenspitze am Scheidenmund, Klinge entlang der Scheidenachse (zum Einstecken). */
    private void bladeTipIntoSaya(PoseSpec p, float fraction) {
        Quaternionf saya = sayaWorldRot(p);
        Vector3f dir = saya.transform(new Vector3f(0, 0, -1));
        Vector3f up = saya.transform(new Vector3f(0, -1, 0));
        // Klingenlänge (Griffmitte bis Spitze) ~ 17,3 px; Spitze soll am Mund liegen
        float len = 17.3f * fraction;
        Vector3f mouth = sayaPoint(p, 0f);
        Vector3f grip = new Vector3f(mouth).sub(new Vector3f(dir).mul(len));
        PoseIK.oneHand(def, p, grip, dir, up, v(-1f, -0.2f, -0.3f));
    }

    private void handsOnThighs(PoseSpec p) {
        Vector3f kneeR = PoseIK.pointOnBone(def, p, "shin_r", v(0, 0.5f, 0));
        Vector3f kneeL = PoseIK.pointOnBone(def, p, "shin_l", v(0, 0.5f, 0));
        Quaternionf flat = MathUtil.euler(-80, 0, 0);
        PoseIK.reach(def, p, "r", new Vector3f(kneeR).add(0.3f, 2.4f, -3.0f), flat, v(-1, -0.2f, -0.4f));
        PoseIK.reach(def, p, "l", new Vector3f(kneeL).add(-0.3f, 2.4f, -3.0f), flat, v(1, -0.2f, -0.4f));
    }
}
