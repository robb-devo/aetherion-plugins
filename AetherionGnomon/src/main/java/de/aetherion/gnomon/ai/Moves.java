package de.aetherion.gnomon.ai;

import de.aetherion.gnomon.ai.MoveSpec.Family;

import java.util.ArrayList;
import java.util.List;

/**
 * The authored move table: what each move answers. These are the defaults; {@code config.yml moves.*} overrides
 * base / cooldown / read weights per move without touching code.
 */
public final class Moves {

    public static final String STOMP = "STOMP";
    public static final String FEINT = "FEINT";
    public static final String HOUR_HAND = "HOUR_HAND";
    public static final String PLUMB_DROP = "PLUMB_DROP";
    public static final String FALSE_OPENING = "FALSE_OPENING";
    public static final String SURVEY_LINE = "SURVEY_LINE";
    public static final String STRIDE = "STRIDE";
    public static final String INTERDICT = "INTERDICT";
    public static final String RECITAL = "RECITAL";

    private Moves() {
    }

    public static List<MoveSpec> defaults() {
        List<MoveSpec> list = new ArrayList<>();

        // Pin the ground where you are - or where it expects you to dodge to.
        list.add(MoveSpec.of(STOMP, Family.PUNISH).base(1.0).cooldown(30)
                .read(Read.SIDE, 0.6).read(Read.ORBIT, 0.35).read(Read.HUGGER, 0.25).read(Read.TIMING, 0.2)
                .posture(Posture.PRESS, 0.25).posture(Posture.OBSERVE, 0.2)
                .range(3, 15).build());

        // Leg over you, eye on your dodge spot. Only once it is sure which way you go.
        list.add(MoveSpec.of(FEINT, Family.BAIT).base(0.35).cooldown(150)
                .read(Read.SIDE, 1.4)
                .posture(Posture.PREY, 0.7)
                .range(3, 14).requires(Read.SIDE).noTeaching().build());

        // The needle's shadow sweeps the dial - against the way you circle.
        list.add(MoveSpec.of(HOUR_HAND, Family.ZONE).base(0.7).cooldown(240)
                .read(Read.ORBIT, 1.0).read(Read.KITER, 0.45).read(Read.CAMPER, 0.3)
                .posture(Posture.DENY, 0.55)
                .range(4, 26).build());

        // The heart drops under the hub. Then it glows: the honest opening.
        list.add(MoveSpec.of(PLUMB_DROP, Family.PUNISH).base(0.6).cooldown(170)
                .read(Read.HUGGER, 1.3)
                .posture(Posture.PRESS, 0.15).posture(Posture.OBSERVE, 0.3)
                .crowd(1.1, 0).range(0, 9).build());

        // Gold glow, but the eye stays on you. A trap for the greedy.
        list.add(MoveSpec.of(FALSE_OPENING, Family.BAIT).base(0.1).cooldown(400)
                .read(Read.GREEDY, 1.6).read(Read.HUGGER, 0.3)
                .posture(Posture.PREY, 0.5)
                .crowd(0.4, 0).range(0, 12).requires(Read.GREEDY).noTeaching().build());

        // The lens draws a line to you and the floor splits along it.
        list.add(MoveSpec.of(SURVEY_LINE, Family.PUNISH).base(0.55).cooldown(110)
                .read(Read.KITER, 1.3).read(Read.SIDE, 0.35)
                .posture(Posture.PRESS, 0.1)
                .crowd(0, 0.8).range(8, 30).build());

        // Walk. Reposition, give a breath, keep the body honest.
        list.add(MoveSpec.of(STRIDE, Family.RESET).base(0.4).cooldown(60)
                .read(Read.KITER, 0.3)
                .posture(Posture.OBSERVE, 0.7)
                .range(0, 40).build());

        // Ink floods your slice and walls rise across your route.
        list.add(MoveSpec.of(INTERDICT, Family.ZONE).base(0.0).cooldown(360)
                .read(Read.CAMPER, 1.4).read(Read.ORBIT, 0.9).read(Read.KITER, 0.5)
                .posture(Posture.DENY, 0.8)
                .range(4, 30).noTeaching().build());

        // Phase II signature: it replays your own path as a trail of eruptions.
        list.add(MoveSpec.of(RECITAL, Family.EXECUTE).base(0.45).cooldown(420).phase(2)
                .read(Read.ORBIT, 0.8).read(Read.CAMPER, 0.6).read(Read.SIDE, 0.3)
                .posture(Posture.PREY, 0.6).posture(Posture.DENY, 0.3)
                .range(0, 40).noTeaching().build());

        return list;
    }
}
