package net.aetherion.samuraiboss.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Ein Kampf ist vollständig beendet (Dojo wiederhergestellt). */
public final class SamuraiFightEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String arenaId;
    private final boolean victory;

    public SamuraiFightEndEvent(String arenaId, boolean victory) {
        this.arenaId = arenaId;
        this.victory = victory;
    }

    public String getArenaId() {
        return arenaId;
    }

    /** true = der Boss wurde besiegt, false = Niederlage/Abbruch. */
    public boolean isVictory() {
        return victory;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
