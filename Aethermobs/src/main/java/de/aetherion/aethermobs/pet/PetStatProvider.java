package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.skill.PetSkill;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.aethermobs.model.PetStats;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class PetStatProvider implements StatProvider {

    private final AetherMobs plugin;

    public PetStatProvider(
            AetherMobs plugin
    ) {
        this.plugin = plugin;
    }

    @Override
    public double getStat(
            Player player,
            ItemCapability capability
    ) {

        if (player == null
                || capability == null) {

            return 0.0;
        }

        PetInstance equippedPet =
                equipped(player);

        if (equippedPet == null) {
            return 0.0;
        }

        if (equippedPet.getDefinition() != null
                && equippedPet.getDefinition().isPercentBonus()) {
            return 0.0;
        }

        PetStats stats =
                equippedPet.getStats();

        if (stats == null) {
            return 0.0;
        }

        double value =
                stats.getScaledTotalStat(
                        capability,
                        equippedPet.getLevel()
                );

        if (capability == ItemCapability.PET_CATCH_RATE) {
            PetSkill skill = PetSkill.fromPet(equippedPet);
            if (skill == PetSkill.TROPICAL_REEF
                    || skill == PetSkill.BLOOM_CHARM) {
                value +=
                        PetSkill.tropicalCatchBonus(
                                equippedPet.getLevel()
                        );
            }
        }

        return value;
    }

    @Override
    public double getMultiplier(
            Player player,
            ItemCapability capability
    ) {

        if (player == null
                || capability == null
                || !DungeonWorlds.isDungeon(player.getWorld())) {
            return 1.0;
        }

        PetInstance equippedPet =
                equipped(player);

        if (equippedPet == null
                || equippedPet.getDefinition() == null
                || !equippedPet.getDefinition().isPercentBonus()) {
            return 1.0;
        }

        PetStats stats =
                equippedPet.getStats();

        if (stats == null) {
            return 1.0;
        }

        double percent =
                stats.getScaledCoreValue(
                        equippedPet.getLevel()
                );

        if (percent <= 0.0) {
            return 1.0;
        }

        double multiplier =
                1.0 + (percent / 100.0);

        return switch (equippedPet.getDefinition().getDungeonAura()) {

            case ALL ->
                    multiplier;

            case DAMAGE ->
                    capability == ItemCapability.DAMAGE
                            ? multiplier
                            : 1.0;

            case BOW ->
                    capability == ItemCapability.DAMAGE
                            && holdingBow(player)
                            ? multiplier
                            : 1.0;

            case NONE ->
                    1.0;
        };
    }

    private PetInstance equipped(
            Player player
    ) {

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        if (collection == null) {
            return null;
        }

        return collection.getEquippedPet();
    }

    private boolean holdingBow(
            Player player
    ) {

        ItemStack item =
                player.getInventory()
                        .getItemInMainHand();

        if (item == null
                || item.getType()
                .isAir()) {
            return false;
        }

        Material type =
                item.getType();

        if (type == Material.BOW
                || type == Material.CROSSBOW) {
            return true;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        String id =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(
                                new NamespacedKey(
                                        "aetherion",
                                        "item"
                                ),
                                PersistentDataType.STRING
                        );

        return id != null
                && (id.equalsIgnoreCase("skuldugery_shortbow")
                || id.equalsIgnoreCase("shortbow"));
    }
}
