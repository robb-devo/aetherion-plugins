package net.aetherion.samuraiboss.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sakuragi Jinsai wurde besiegt. Enthält den Schaden pro Spieler (für eigene Belohnungen). */
public final class SamuraiBossDefeatEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String arenaId;
    private final List<Player> players;
    private final Map<UUID, Double> damage;

    public SamuraiBossDefeatEvent(String arenaId, List<Player> players, Map<UUID, Double> damage) {
        this.arenaId = arenaId;
        this.players = players;
        this.damage = damage;
    }

    public String getArenaId() {
        return arenaId;
    }

    /** Spieler, die beim Sieg noch im Kampf waren. */
    public List<Player> getPlayers() {
        return players;
    }

    /** Verursachter Schaden je Spieler (UUID) über den ganzen Kampf. */
    public Map<UUID, Double> getDamage() {
        return damage;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
