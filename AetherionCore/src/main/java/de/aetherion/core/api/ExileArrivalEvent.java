package de.aetherion.core.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The ferry has docked at the Koenigskai and the player has their body back (mmo-c). Quests listens to point
 * the soft compass at the Ferryman / the next door. {@code firstTime} = the full first crossing just ended.
 */
public final class ExileArrivalEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final boolean firstTime;

    public ExileArrivalEvent(Player player, boolean firstTime) {
        this.player = player;
        this.firstTime = firstTime;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isFirstTime() {
        return firstTime;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
