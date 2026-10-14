package net.aetherion.samuraiboss.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Ein Kampf gegen Sakuragi Jinsai hat begonnen (für andere Plugins, z. B. Statistiken). */
public final class SamuraiFightStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String arenaId;
    private final List<Player> players;

    public SamuraiFightStartEvent(String arenaId, List<Player> players) {
        this.arenaId = arenaId;
        this.players = players;
    }

    public String getArenaId() {
        return arenaId;
    }

    /** Spieler, die beim Start im Dojo waren. */
    public List<Player> getPlayers() {
        return players;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
