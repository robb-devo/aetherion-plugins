package net.aetherion.samuraiboss.api;

import net.aetherion.samuraiboss.boss.Phase;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Der Kensei wechselt in eine neue Phase (Beginn der Zwischensequenz). */
public final class SamuraiPhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String arenaId;
    private final Phase from, to;

    public SamuraiPhaseChangeEvent(String arenaId, Phase from, Phase to) {
        this.arenaId = arenaId;
        this.from = from;
        this.to = to;
    }

    public String getArenaId() {
        return arenaId;
    }

    public Phase getFrom() {
        return from;
    }

    public Phase getTo() {
        return to;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
