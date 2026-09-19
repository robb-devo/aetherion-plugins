package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.skill.PetSkillManager;
import de.aetherion.items.AetherionItems;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActivePetManager implements Listener {

    private final AetherMobs plugin;

    private final Map<UUID, PetEntity> activePets =
            new HashMap<>();

    private final Map<UUID, Long> lastTeleportRefresh =
            new ConcurrentHashMap<>();

    public ActivePetManager(
            AetherMobs plugin
    ) {
        this.plugin = plugin;
    }

    public void equip(
            Player player,
            PetInstance pet
    ) {

        if (player == null
                || pet == null) {

            return;
        }

        /*
         * Remove the currently active pet first.
         *
         * This makes switching pets seamless.
         */
        removeActivePet(
                player
        );

        Location spawnLocation =
                getPetSpawnLocation(
                        player
                );

        /*
         * Equipped pets receive their owner
         * directly. This keeps them completely
         * separate from wild-pet behaviour.
         */
        PetEntity petEntity =
                new PetEntity(
                        pet,
                        player
                );

        petEntity.spawn(
                spawnLocation
        );

        /*
         * Only register the entity if it actually
         * spawned successfully.
         */
        if (petEntity.isSpawned()) {

            activePets.put(
                    player.getUniqueId(),
                    petEntity
            );

            PetSkillManager skillManager =
                    plugin.getPetSkillManager();

            if (skillManager != null) {

                skillManager.activate(
                        player,
                        pet
                );
            }

            /*
             * Refresh all player stats affected
             * by the newly equipped pet.
             */
            refreshPlayerStats(
                    player
            );
        }
    }

    public void unequip(
            Player player
    ) {

        if (player == null) {
            return;
        }

        removeActivePet(
                player
        );

        /*
         * Refresh all player stats after the
         * pet has been unequipped.
         */
        refreshPlayerStats(
                player
        );
    }

    private void removeActivePet(
            Player player
    ) {

        PetEntity activePet =
                activePets.remove(
                        player.getUniqueId()
                );

        if (activePet != null) {

            PetSkillManager skillManager =
                    plugin.getPetSkillManager();

            if (skillManager != null) {

                skillManager.deactivate(
                        player,
                        activePet.getPetInstance()
                );
            }

            activePet.remove();
        }
    }

    /*
     * =========================================================
     * PLAYER STAT REFRESH
     * =========================================================
     *
     * AetherionItems owns the actual player-stat systems.
     *
     * AetherMobs only tells AetherionItems that the active
     * pet changed and the affected stats need refreshing.
     *
     * Currently:
     *
     * - Health
     * - Mining Power
     *
     * are refreshed here.
     *
     */

    private void refreshPlayerStats(
            Player player
    ) {

        if (player == null) {
            return;
        }

        AetherionItems itemsPlugin =
                AetherionItems.getInstance();

        if (itemsPlugin == null) {
            return;
        }

        /*
         * =====================================================
         * HEALTH
         * =====================================================
         */

        if (itemsPlugin.getHealthListener() != null) {

            itemsPlugin.getHealthListener()
                    .refreshHealth(
                            player
                    );
        }

        /*
         * =====================================================
         * MINING POWER
         * =====================================================
         */

        de.aetherion.core.api.HarvestAccess harvest = de.aetherion.core.api.AetherServices.harvest();
        if (harvest != null) {
            harvest.refreshMiningPower(player);
        } else if (itemsPlugin.getHarvestListener() != null) {
            itemsPlugin.getHarvestListener().refreshMiningPower(player);
        }
    }

    public PetEntity getActivePet(
            Player player
    ) {

        if (player == null) {
            return null;
        }

        return activePets.get(
                player.getUniqueId()
        );
    }

    public boolean hasActivePet(
            Player player
    ) {

        PetEntity pet =
                getActivePet(
                        player
                );

        return pet != null
                && pet.isSpawned();
    }

    private Location getPetSpawnLocation(
            Player player
    ) {

        Location location =
                player.getLocation()
                        .clone();

        /*
         * Spawn slightly behind the player.
         *
         * PetEntity will handle the final
         * follow/hover positioning.
         */
        location.add(
                0,
                0,
                1.5
        );

        return location;
    }

    @EventHandler
    public void onJoin(
            PlayerJoinEvent event
    ) {

        Player player =
                event.getPlayer();

        /*
         * AetherMobs loads the collection in its
         * own join listener. Wait one tick so the
         * collection is available before restoring
         * the active pet.
         */
        new BukkitRunnable() {

            @Override
            public void run() {

                if (!player.isOnline()) {
                    return;
                }

                restoreEquippedPet(
                        player
                );
            }

        }.runTaskLater(
                plugin,
                1L
        );
    }

    @EventHandler
    public void onRespawn(
            PlayerRespawnEvent event
    ) {

        Player player =
                event.getPlayer();

        /*
         * Remove the old entity immediately.
         */
        removeActivePet(
                player
        );

        /*
         * Recreate the equipped pet after
         * the respawn has completed.
         */
        new BukkitRunnable() {

            @Override
            public void run() {

                if (!player.isOnline()) {
                    return;
                }

                restoreEquippedPet(
                        player
                );
            }

        }.runTaskLater(
                plugin,
                2L
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(
            PlayerTeleportEvent event
    ) {

        Location from =
                event.getFrom();

        Location to =
                event.getTo();

        if (to == null
                || to.getWorld() == null) {

            return;
        }

        boolean worldChange =
                from.getWorld() == null
                        || !from.getWorld()
                        .equals(
                                to.getWorld()
                        );

        if (!worldChange
                && from.distanceSquared(to) < 9.0) {

            return;
        }

        refreshAfterMove(
                event.getPlayer(),
                worldChange
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(
            PlayerChangedWorldEvent event
    ) {

        refreshAfterMove(
                event.getPlayer(),
                true
        );
    }

    private void refreshAfterMove(
            Player player,
            boolean worldChange
    ) {

        if (player == null
                || !player.isOnline()) {

            return;
        }

        PetInstance equipped =
                equippedOf(
                        player
                );

        if (equipped == null) {

            return;
        }

        UUID id =
                player.getUniqueId();

        long now =
                System.currentTimeMillis();

        Long last =
                lastTeleportRefresh.get(
                        id
                );

        if (!worldChange
                && last != null
                && now - last < 250L) {

            return;
        }

        lastTeleportRefresh.put(
                id,
                now
        );

        removeActivePet(
                player
        );

        new BukkitRunnable() {

            @Override
            public void run() {

                if (!player.isOnline()) {
                    return;
                }

                restoreEquippedPet(
                        player
                );
            }

        }.runTaskLater(
                plugin,
                worldChange ? 10L : 3L
        );
    }

    private PetInstance equippedOf(
            Player player
    ) {

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        return collection == null
                ? null
                : collection.getEquippedPet();
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {

        /*
         * Entity cleanup only.
         *
         * The equipped state remains saved in
         * PlayerPetCollection.
         */
        removeActivePet(
                event.getPlayer()
        );
        lastTeleportRefresh.remove(event.getPlayer().getUniqueId());
    }

    private void restoreEquippedPet(
            Player player
    ) {

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        PetInstance equippedPet =
                collection.getEquippedPet();

        if (equippedPet == null) {
            return;
        }

        equip(
                player,
                equippedPet
        );
    }

    public void removeAll() {

        for (PetEntity pet :
                activePets.values()) {

            if (pet != null) {
                pet.remove();
            }
        }

        activePets.clear();
    }
}