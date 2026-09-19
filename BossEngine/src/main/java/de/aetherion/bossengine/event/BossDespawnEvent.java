package de.aetherion.bossengine.event;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.LeashAction;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BossDespawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Reason {
        COMMAND,
        LEASH,
        UNLOAD,
        PLUGIN_DISABLE,
        REPLACED
    }

    private final BossInstance instance;
    private final Reason reason;
    private final LeashAction leashAction;

    public BossDespawnEvent(BossInstance instance, Reason reason, LeashAction leashAction) {
        this.instance = instance;
        this.reason = reason;
        this.leashAction = leashAction;
    }

    public BossInstance getInstance() {
        return instance;
    }

    public Reason getReason() {
        return reason;
    }

    public LeashAction getLeashAction() {
        return leashAction;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
