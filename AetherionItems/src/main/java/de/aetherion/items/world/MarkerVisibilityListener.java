package de.aetherion.items.world;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class MarkerVisibilityListener implements Listener {

    private final AreaService areas;
    private final AnimalZoneService animals;
    private final MobZoneService mobs;
    private final PetHabitatZoneService petHabitats;

    public MarkerVisibilityListener(
            AreaService areas,
            AnimalZoneService animals,
            MobZoneService mobs,
            PetHabitatZoneService petHabitats
    ) {
        this.areas = areas;
        this.animals = animals;
        this.mobs = mobs;
        this.petHabitats = petHabitats;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reveal(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        reveal(event.getPlayer());
    }

    private void reveal(Player player) {
        areas.revealToLater(player);
        animals.revealToLater(player);
        mobs.revealToLater(player);
        if (petHabitats != null) {
            petHabitats.revealToLater(player);
        }
    }
}
