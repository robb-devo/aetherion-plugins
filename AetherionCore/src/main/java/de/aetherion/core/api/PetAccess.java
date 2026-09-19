package de.aetherion.core.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Pet save/reload for dungeon network sync and DEV catalog helpers.
 * Implemented by AetherMobs.
 */
public interface PetAccess {

    void flushPlayer(UUID playerId);

    void reloadAfterImport(UUID playerId);

    void reequip(Player player);

    List<PetCatalogItem> pets();

    boolean spawnDevPet(Player player, String petId);

    boolean giveDevPet(Player player, String petId);

    int unlockAllPetsDev(Player player);

    ItemStack catchSphere(String id);

    ItemStack petExpTreat(int tier);
}
