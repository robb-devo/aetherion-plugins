package de.aetherion.fishing;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.entity.Player;

final class FishingStats {

    private final ActiveEquipmentStats equipment;

    FishingStats() {
        AetherionItems items = AetherionItems.getInstance();
        this.equipment = items == null ? null : new ActiveEquipmentStats(items.getItemManager());
    }

    double speed(Player player) {
        return stat(player, ItemCapability.FISHING_SPEED);
    }

    double catchBonus(Player player) {
        return stat(player, ItemCapability.FISHING_CATCH);
    }

    int waitTicks(Player player) {
        double seconds = 14.0d - speed(player) / 10.0d;
        int ticks = (int) Math.round(seconds * 20.0d);
        return Math.max(20, Math.min(20 * 14, ticks));
    }

    private double stat(Player player, ItemCapability capability) {
        if (equipment == null || player == null) {
            return 0.0d;
        }
        return Math.max(0.0d, equipment.getStat(player, capability));
    }
}
