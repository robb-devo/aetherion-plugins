package de.aetherion.quests.dialog;

/**
 * One pacing knob for every spoken NPC line — DialogManager intros,
 * completed chatter, and {@code LivingNpcProfile.say} queues.
 */
public final class DialogPace {

    /**
     * Ticks between consecutive dialogue lines (~2.0s at 20 TPS).
     * Keep this the only place that defines speech rhythm.
     */
    public static final long LINE_GAP_TICKS = 40L;

    private DialogPace() {
    }
}
