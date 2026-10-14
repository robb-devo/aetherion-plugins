package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.render.anim.Clip;
import net.aetherion.samuraiboss.render.anim.ClipBuilder;
import net.aetherion.samuraiboss.render.anim.PoseSpec;
import net.aetherion.samuraiboss.render.model.ModelDef;
import net.aetherion.samuraiboss.util.Ease;

/**
 * Alle Animations-Clips des Kensei, zusammengesetzt aus den Schlüsselposen.
 *
 * <p>Ereignisse (Strings), die der Boss auswertet:</p>
 * <ul>
 *   <li><b>draw</b> – Katana wechselt aus der Scheide in die Hand (Sichtbarkeit tauschen)</li>
 *   <li><b>sheathe</b> – Katana zurück in die Scheide</li>
 *   <li><b>click</b> – das berühmte "Chin" beim Einrasten (Iai-Schaden kommt genau hier!)</li>
 *   <li><b>hit</b>, <b>hit2</b>, <b>hit3</b> – Trefferzeitpunkte</li>
 *   <li><b>cut</b> – Ziehschnitt-Moment, <b>windup</b>, <b>summon</b>, <b>launch</b>, <b>plant</b>, <b>jump</b>, <b>flick</b>, <b>open</b></li>
 * </ul>
 */
public final class SamuraiAnimations {

    public final SamuraiPoses p;

    // Basis-Schleifen
    public final Clip idleSheathed, idleDrawn, idleGhost, walkSheathed, walkDrawn, glideGhost, seizaIdle, exhaustedIdle;
    // Iaijutsu
    public final Clip iaiReady, iaiStrike, iaiFinish, kesaIai;
    // gezogenes Schwert
    public final Clip drawSlow, kesagiri, tsubame, yokogiri, thrust, spinSlash, raiseSky, pointBlade, plantSword, hurt;
    // Bewegung / Kino
    public final Clip riseFromSeiza, bow, seizaBow, crouchLeap, air, ultDraw, kneelExhausted, getUp, death, sheatheFromKamae,
            ghostRise, quickDash;

    public SamuraiAnimations(ModelDef def) {
        p = new SamuraiPoses(def);

        // ================================================================= Schleifen
        idleSheathed = new ClipBuilder(def, "idle_sheathed", 64).loop()
                .pose(0, p.stand).pose(32, p.standBreath, Ease.SMOOTH).pose(64, p.stand, Ease.SMOOTH).build();
        idleDrawn = new ClipBuilder(def, "idle_drawn", 50).loop()
                .pose(0, p.kamae).pose(25, p.kamaeBreath, Ease.SMOOTH).pose(50, p.kamae, Ease.SMOOTH).build();
        PoseSpec floatUp = p.floatIdle.with().p("hips", 0, 0.6f, 0).addR("torso", -2, 0, 0).addR("head", -3, 0, 0);
        idleGhost = new ClipBuilder(def, "idle_ghost", 70).loop()
                .pose(0, p.floatIdle).pose(35, floatUp, Ease.SMOOTH).pose(70, p.floatIdle, Ease.SMOOTH).build();

        walkSheathed = new ClipBuilder(def, "walk_sheathed", 22).loop()
                .pose(0, p.walkA).pose(5.5f, passing(p.walkA, p.walkB, "_l"), Ease.LINEAR)
                .pose(11, p.walkB, Ease.LINEAR).pose(16.5f, passing(p.walkB, p.walkA, "_r"), Ease.LINEAR)
                .pose(22, p.walkA, Ease.LINEAR).build();
        PoseSpec wdA = withLegs(p.kamae, p.walkA), wdB = withLegs(p.kamae, p.walkB);
        walkDrawn = new ClipBuilder(def, "walk_drawn", 20).loop()
                .pose(0, wdA).pose(5, passing(wdA, wdB, "_l"), Ease.LINEAR).pose(10, wdB, Ease.LINEAR)
                .pose(15, passing(wdB, wdA, "_r"), Ease.LINEAR).pose(20, wdA, Ease.LINEAR).build();
        PoseSpec glide = p.floatIdle.with().addR("torso", 10, 0, 0).addR("hair1", 22, 0, 0).addR("hair2", 18, 0, 0);
        glideGhost = new ClipBuilder(def, "glide_ghost", 30).loop()
                .pose(0, glide).pose(15, glide.with().p("hips", 0, 0.5f, 0), Ease.SMOOTH).pose(30, glide, Ease.SMOOTH).build();
        seizaIdle = new ClipBuilder(def, "seiza_idle", 70).loop()
                .pose(0, p.seiza).pose(35, p.seiza.with().addR("torso", -1.5f, 0, 0).p("hips", 0, -8.4f, -1.5f), Ease.SMOOTH)
                .pose(70, p.seiza, Ease.SMOOTH).build();
        exhaustedIdle = new ClipBuilder(def, "exhausted_idle", 36).loop()
                .pose(0, p.kneelExhausted).pose(18, p.kneelExhausted.with().addR("torso", 5, 0, 0).addR("head", 4, 0, 0), Ease.SMOOTH)
                .pose(36, p.kneelExhausted, Ease.SMOOTH).build();

        // ================================================================= Iaijutsu
        iaiReady = new ClipBuilder(def, "iai_ready", 16).hold()
                .pose(0, p.stand).pose(10, p.iaiReady, Ease.OUT_CUBIC).pose(16, p.iaiReady)
                .event(9, "open").build();
        iaiStrike = new ClipBuilder(def, "iai_strike", 14).fast().hold()
                .pose(0, p.iaiReady).pose(2, p.iaiDraw, Ease.IN_QUAD).pose(5, p.iaiEnd, Ease.OUT_EXPO).pose(14, p.iaiEnd)
                .event(1, "draw").event(4, "cut").build();
        iaiFinish = new ClipBuilder(def, "iai_finish", 34)
                .pose(0, p.iaiEnd).pose(7, p.chiburi, Ease.OUT_BACK).pose(17, p.notoStart, Ease.IN_OUT_CUBIC)
                .pose(25, p.notoEnd, Ease.IN_QUAD).pose(34, p.stand, Ease.SMOOTH)
                .event(6, "flick").event(23, "sheathe").event(25, "click").build();
        kesaIai = new ClipBuilder(def, "kesa_iai", 24).fast().hold()
                .pose(0, p.stand).pose(4, p.iaiReady, Ease.OUT_QUAD).pose(6, p.iaiDraw, Ease.IN_QUAD)
                .pose(11, p.jodan, Ease.OUT_CUBIC).pose(14, p.kesaEnd, Ease.IN_EXPO).pose(24, p.kesaEnd)
                .event(5, "draw").event(9, "windup").event(13, "hit").build();

        // ================================================================= gezogenes Schwert
        drawSlow = new ClipBuilder(def, "draw_slow", 44)
                .pose(0, p.stand).pose(12, p.iaiReady, Ease.SMOOTH).pose(30, p.iaiDraw, Ease.IN_OUT_CUBIC)
                .pose(36, p.iaiEnd, Ease.OUT_EXPO).pose(44, p.kamae, Ease.SMOOTH)
                .event(13, "draw").event(34, "cut").build();
        sheatheFromKamae = new ClipBuilder(def, "sheathe_from_kamae", 28)
                .pose(0, p.kamae).pose(6, p.chiburi, Ease.OUT_BACK).pose(15, p.notoStart, Ease.IN_OUT_CUBIC)
                .pose(22, p.notoEnd, Ease.IN_QUAD).pose(28, p.stand, Ease.SMOOTH)
                .event(5, "flick").event(20, "sheathe").event(22, "click").build();
        kesagiri = new ClipBuilder(def, "kesagiri", 24).fast()
                .pose(0, p.kamae).pose(8, p.jodan, Ease.OUT_CUBIC).pose(11, p.kesaEnd, Ease.IN_EXPO)
                .pose(17, p.kesaEnd).pose(24, p.kamae, Ease.SMOOTH)
                .event(5, "windup").event(10, "hit").build();
        tsubame = new ClipBuilder(def, "tsubame", 28).fast()
                .pose(0, p.kamae).pose(6, p.jodan, Ease.OUT_CUBIC).pose(9, p.kesaEnd, Ease.IN_EXPO)
                .pose(12, p.kesaEnd).pose(15, p.gyakuEnd, Ease.OUT_EXPO).pose(21, p.gyakuEnd).pose(28, p.kamae, Ease.SMOOTH)
                .event(4, "windup").event(8, "hit").event(14, "hit2").build();
        yokogiri = new ClipBuilder(def, "yokogiri", 20).fast()
                .pose(0, p.kamae).pose(7, p.yokoWind, Ease.OUT_CUBIC).pose(10, p.yokoEnd, Ease.IN_EXPO)
                .pose(14, p.yokoEnd).pose(20, p.kamae, Ease.SMOOTH)
                .event(4, "windup").event(9, "hit").build();
        thrust = new ClipBuilder(def, "thrust", 20).fast()
                .pose(0, p.kamae).pose(7, p.thrustWind, Ease.OUT_CUBIC).pose(10, p.thrustEnd, Ease.OUT_EXPO)
                .pose(14, p.thrustEnd).pose(20, p.kamae, Ease.SMOOTH)
                .event(5, "windup").event(9, "hit").build();
        spinSlash = new ClipBuilder(def, "spin_slash", 26).fast()
                .pose(0, p.kamae).pose(4, p.spinA, Ease.OUT_QUAD).pose(22, p.spinA).pose(26, p.kamae, Ease.SMOOTH)
                .bone("root").rot(0, 0, 0, 0).rot(4, 0, 0, 0).rot(10, 0, -180, 0, Ease.LINEAR).rot(16, 0, -360, 0, Ease.LINEAR)
                .rot(22, 0, -540, 0, Ease.LINEAR).rot(26, 0, -720, 0, Ease.OUT_QUAD)
                .event(7, "hit").event(13, "hit2").event(19, "hit3").build();
        raiseSky = new ClipBuilder(def, "raise_sky", 24).hold()
                .pose(0, p.kamae).pose(14, p.raiseSky, Ease.OUT_BACK).pose(24, p.raiseSky)
                .event(13, "summon").build();
        pointBlade = new ClipBuilder(def, "point_blade", 12).hold()
                .pose(0, p.raiseSky).pose(7, p.pointBlade, Ease.OUT_EXPO).pose(12, p.pointBlade)
                .event(6, "launch").build();
        plantSword = new ClipBuilder(def, "plant_sword", 26).hold()
                .pose(0, p.kamae).pose(9, p.raiseSky, Ease.OUT_CUBIC).pose(14, p.plantSword, Ease.IN_EXPO).pose(26, p.plantSword)
                .event(13, "plant").build();
        hurt = new ClipBuilder(def, "hurt", 10)
                .pose(0, p.kamae).pose(3, p.hurt, Ease.OUT_EXPO).pose(10, p.kamae, Ease.SMOOTH).build();
        quickDash = new ClipBuilder(def, "quick_dash", 10).fast()
                .pose(0, p.kamae).pose(3, p.thrustEnd, Ease.OUT_EXPO).pose(10, p.kamae, Ease.SMOOTH).build();

        // ================================================================= Kino
        riseFromSeiza = new ClipBuilder(def, "rise_from_seiza", 44)
                .pose(0, p.seiza).pose(16, p.kneelExhausted.with().r("arm_r", -6, 0, -6).r("forearm_r", -20, 0, 0).r("hand_r", 0, 0, 0)
                        .r("torso", 18, 0, 0), Ease.SMOOTH)
                .pose(32, p.stand.with().p("hips", 0, -1.2f, 0), Ease.SMOOTH).pose(44, p.stand, Ease.SMOOTH).build();
        bow = new ClipBuilder(def, "bow", 40)
                .pose(0, p.stand).pose(14, p.bow, Ease.SMOOTH).pose(26, p.bow).pose(40, p.stand, Ease.SMOOTH).build();
        seizaBow = new ClipBuilder(def, "seiza_bow", 44)
                .pose(0, p.seiza).pose(16, p.seizaBow, Ease.SMOOTH).pose(30, p.seizaBow).pose(44, p.seiza, Ease.SMOOTH).build();
        crouchLeap = new ClipBuilder(def, "crouch_leap", 12).hold()
                .pose(0, p.stand).pose(9, p.crouchLeap, Ease.OUT_CUBIC).pose(12, p.crouchLeap)
                .event(10, "jump").build();
        air = new ClipBuilder(def, "air", 40).loop()
                .pose(0, p.airPose).pose(20, p.airPose.with().p("hips", 0, 0.4f, 0).addR("torso", -2, 0, 0), Ease.SMOOTH)
                .pose(40, p.airPose, Ease.SMOOTH).build();
        // Die Ultimate: absurd langsames Ziehen, dann ein einziger Schnitt
        ultDraw = new ClipBuilder(def, "ult_draw", 70).hold()
                .pose(0, p.airPose).pose(12, p.drawSlowStart, Ease.SMOOTH).pose(60, p.drawSlowMid, Ease.IN_CUBIC)
                .pose(62, p.iaiEnd, Ease.OUT_EXPO).pose(70, p.iaiEnd)
                .event(13, "draw").event(61, "cut").build();
        kneelExhausted = new ClipBuilder(def, "kneel_exhausted", 22).hold()
                .pose(0, p.kamae).pose(16, p.kneelExhausted, Ease.OUT_BACK).pose(22, p.kneelExhausted)
                .event(15, "plant").build();
        getUp = new ClipBuilder(def, "get_up", 24)
                .pose(0, p.kneelExhausted).pose(14, p.plantSword, Ease.SMOOTH).pose(24, p.kamae, Ease.SMOOTH).build();
        ghostRise = new ClipBuilder(def, "ghost_rise", 50)
                .pose(0, p.seiza).pose(20, p.seizaBow, Ease.SMOOTH).pose(40, p.raiseSky, Ease.OUT_CUBIC).pose(50, p.floatIdle, Ease.SMOOTH)
                .event(1, "draw").build();
        death = new ClipBuilder(def, "death", 90).hold()
                .pose(0, p.kamae).pose(10, p.hurt, Ease.OUT_EXPO).pose(28, p.kneelExhausted, Ease.SMOOTH)
                .pose(52, p.deathKneel, Ease.SMOOTH).pose(80, p.deathBow, Ease.SMOOTH).pose(90, p.deathBow)
                .event(9, "crack").event(27, "plant").event(80, "bowed").build();
    }

    /** Ersetzt die Beine einer Pose durch die einer anderen (Gehen mit gezogenem Schwert). */
    private static PoseSpec withLegs(PoseSpec upper, PoseSpec legs) {
        PoseSpec out = upper.with();
        for (String b : new String[]{"thigh_r", "shin_r", "foot_r", "thigh_l", "shin_l", "foot_l"}) {
            float[] v = legs.rotations().get(b);
            if (v != null) out.r(b, v[0], v[1], v[2]);
        }
        float[] hp = legs.positions().get("hips");
        float[] up = upper.positions().get("hips");
        float y = (hp != null ? hp[1] : 0) + (up != null ? up[1] * 0.5f : 0);
        out.p("hips", 0, y, 0);
        return out;
    }

    /** Zwischenpose beim Gehen: Schwungbein (side) angehoben, Knie gebeugt. */
    private static PoseSpec passing(PoseSpec a, PoseSpec b, String side) {
        PoseSpec m = a.with();
        for (String bone : new String[]{"thigh_r", "shin_r", "foot_r", "thigh_l", "shin_l", "foot_l", "arm_r", "forearm_r", "hips", "torso"}) {
            float[] va = a.rotations().get(bone), vb = b.rotations().get(bone);
            if (va == null || vb == null) continue;
            m.r(bone, (va[0] + vb[0]) / 2, (va[1] + vb[1]) / 2, (va[2] + vb[2]) / 2);
        }
        float[] t = m.rotations().get("thigh" + side), s = m.rotations().get("shin" + side);
        if (t != null) m.r("thigh" + side, t[0] - 14, t[1], t[2]);
        if (s != null) m.r("shin" + side, s[0] + 30, s[1], s[2]);
        float[] hp = a.positions().get("hips");
        m.p("hips", 0, (hp != null ? hp[1] : 0) + 0.45f, 0);
        return m;
    }
}
