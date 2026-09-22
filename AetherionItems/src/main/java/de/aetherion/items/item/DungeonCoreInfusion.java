package de.aetherion.items.item;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.dungeon.DungeonGearProgress;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Dungeon-core infusion used to live on the vanilla anvil result slot.
 * The anvil button now opens booster sockets; infusion stays available there.
 */
public final class DungeonCoreInfusion {

    private DungeonCoreInfusion() {
    }

    public static ItemStack infuse(ItemManager items, ItemStack weapon, ItemStack core) {
        if (items == null || weapon == null || core == null) {
            return null;
        }
        if (DungeonCore.isCore(weapon) && DungeonCore.canInfuse(items.getItemId(core))) {
            ItemStack swap = weapon;
            weapon = core;
            core = swap;
        }
        if (!DungeonCore.isCore(core) || !items.isAetherionItem(weapon) || core.getAmount() != 1) {
            return null;
        }
        String itemId = items.getItemId(weapon);
        if (!DungeonCore.canInfuse(itemId)) {
            return null;
        }
        int nextTier = DungeonCore.tier(weapon) + 1;
        if (nextTier > DungeonCore.MAX_TIER || DungeonCore.coreGrade(core) != nextTier) {
            return null;
        }
        ItemStack result = weapon.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }
        DungeonCore.writeTier(meta, nextTier);
        DungeonCore.applyInfusionRarity(meta, nextTier);
        if (itemId != null && itemId.toLowerCase().contains("shortbow")) {
            meta.getPersistentDataContainer().set(
                    ItemKeys.shortbowInterval(),
                    PersistentDataType.INTEGER,
                    DungeonCore.shortbowIntervalTicks(nextTier)
            );
        }
        if (itemId != null && itemId.toLowerCase().contains("longbow")) {
            meta.getPersistentDataContainer().set(
                    ItemKeys.longbowCharge(),
                    PersistentDataType.INTEGER,
                    DungeonCore.longbowChargeTicks(nextTier)
            );
        }
        DungeonCore.patchLore(meta, itemId, nextTier);
        result.setItemMeta(meta);
        DungeonGearProgress.ensure(result, items);
        ItemMeta polished = result.getItemMeta();
        if (polished != null) {
            ItemPresentation.polish(polished);
            result.setItemMeta(polished);
        }
        GearTooltip.finish(result, items, true);
        return result;
    }
}
