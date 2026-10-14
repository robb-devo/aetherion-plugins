package de.aetherion.gnomon.ai;

import java.util.UUID;

/**
 * The next move and why. {@code spec == null} means "breathe": a quiet beat where it only looks.
 *
 * @param exploit the habit this move spends (null = honest aim)
 * @param sign    direction of the exploited habit (SIDE / ORBIT / TIMING)
 * @param sector  dial slice for CAMPER exploits, else -1
 */
public record Decision(MoveSpec spec, UUID target, Read exploit, int sign, int sector, double score, String why) {

    public boolean breath() {
        return spec == null;
    }

    public String id() {
        return spec == null ? "BREATH" : spec.id();
    }
}
