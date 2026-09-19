package de.aetherion.aethermobs.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public final class PetCaughtEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String petId;

    public PetCaughtEvent(Player player, String petId) {
        super(player);
        this.petId = petId == null ? "" : petId;
    }

    public String getPetId() {
        return petId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
