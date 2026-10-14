package net.aetherion.samuraiboss.boss;

/** Die drei Kampfphasen ("Formen") des Kensei. */
public enum Phase {
    /** 一の型 「静」 – Stille: Iaijutsu, das Schwert ruht in der Scheide. */
    SEI(1, "phase-1", "静"),
    /** 二の型 「嵐」 – Sturm: gezogenes Schwert, Geisterklingen, Tornado, Schattenklone. */
    ARASHI(2, "phase-2", "嵐"),
    /** 三の型 「散華」 – Fallende Blüten: Geisterform, Ultimate. */
    SANGE(3, "phase-3", "散華");

    public final int number;
    public final String messageKey;
    public final String kanji;

    Phase(int number, String messageKey, String kanji) {
        this.number = number;
        this.messageKey = messageKey;
        this.kanji = kanji;
    }
}
