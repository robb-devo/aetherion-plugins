package de.aetherion.bossengine.event;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BossPhaseChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BossInstance instance;
    private final BossPhase previousPhase;
    private final BossPhase nextPhase;
    private boolean cancelled;

    public BossPhaseChangeEvent(
            BossInstance instance,
            BossPhase previousPhase,
            BossPhase nextPhase
    ) {
        this.instance = instance;
        this.previousPhase = previousPhase;
        this.nextPhase = nextPhase;
    }

    public BossInstance getInstance() {
        return instance;
    }

    public BossPhase getPreviousPhase() {
        return previousPhase;
    }

    public BossPhase getNextPhase() {
        return nextPhase;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
