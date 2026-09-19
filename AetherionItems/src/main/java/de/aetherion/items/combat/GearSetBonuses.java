package de.aetherion.items.combat;

import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.manager.StatProvider;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.entity.Player;

public final class GearSetBonuses implements StatProvider {

    private final ItemManager itemManager;

    private GearSetBonuses(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    public static void register(ItemManager itemManager) {
        ActiveEquipmentStats.registerProvider(new GearSetBonuses(itemManager));
    }

    @Override
    public double getStat(Player player, ItemCapability capability) {
        if (player == null || capability == null) {
            return 0.0;
        }
        return switch (capability) {
            case UNDEAD_DAMAGE, UNDEAD_RESIST ->
                    UndeadCombat.wearingFullSet(player, itemManager, "rotten_") ? 10.0 : 0.0;
            case CRIT_CHANCE ->
                    UndeadCombat.wearingFullSet(player, itemManager, "bone_") ? 4.0 : 0.0;
            case DEFENSE ->
                    UndeadCombat.wearingFullSet(player, itemManager, "ironhide_") ? 20.0 : 0.0;
            case HARVEST_SPREAD ->
                    UndeadCombat.wearingFullSet(player, itemManager, "farming_") ? 10.0 : 0.0;
            case SPREAD ->
                    UndeadCombat.wearingFullSet(player, itemManager, "foraging_") ? 6.0 : 0.0;
            case FISHING_SPEED ->
                    UndeadCombat.wearingFullSet(player, itemManager, "fishing_") ? 8.0 : 0.0;
            case FISHING_CATCH ->
                    UndeadCombat.wearingFullSet(player, itemManager, "fishing_") ? 12.0 : 0.0;
            default -> 0.0;
        };
    }
}
