package de.aetherion.bossengine.event;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.instance.BossInstance;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before a boss entity is created. Quest / Lexikon plugins can cancel.
 */
public class BossSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BossInstance instance;
    private final Location location;
    private final SpawnCause cause;
    private final Player initiator;
    private boolean cancelled;

    public BossSpawnEvent(
            BossInstance instance,
            Location location,
            SpawnCause cause,
            Player initiator
    ) {
        this.instance = instance;
        this.location = location.clone();
        this.cause = cause;
        this.initiator = initiator;
    }

    public BossInstance getInstance() {
        return instance;
    }

    public Location getLocation() {
        return location.clone();
    }

    public SpawnCause getCause() {
        return cause;
    }

    public Player getInitiator() {
        return initiator;
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
