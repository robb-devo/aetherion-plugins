package de.aetherion.items.api;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.item.CustomItem;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Typed factory for dungeon loot / guild mill output.
 */
public final class ItemFactoryAccessImpl implements ItemFactoryAccess {

    private final CustomItem customItem;

    public ItemFactoryAccessImpl(CustomItem customItem) {
        this.customItem = customItem;
    }

    @Override
    public ItemStack create(String itemId) {
        return customItem == null ? null : customItem.createById(itemId);
    }

    @Override
    public ItemStack compressed(String resourceName) {
        CompressedResource resource = resource(resourceName);
        return resource == null ? null : resource.compressed();
    }

    @Override
    public ItemStack compacted(String resourceName) {
        CompressedResource resource = resource(resourceName);
        return resource == null ? null : resource.compacted();
    }

    @Override
    public ItemStack randomCompressed(boolean compacted) {
        CompressedResource[] values = CompressedResource.values();
        if (values.length == 0) {
            return null;
        }
        CompressedResource pick = values[ThreadLocalRandom.current().nextInt(values.length)];
        return compacted ? pick.compacted() : pick.compressed();
    }

    @Override
    public String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(AetherKeys.ITEM_ID, PersistentDataType.STRING);
    }

    @Override
    public ItemStack dungeonWeaponSchematic(int floor) {
        return customItem == null ? null : customItem.createDungeonWeaponSchematic(floor);
    }

    private static CompressedResource resource(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            return null;
        }
        try {
            return CompressedResource.valueOf(resourceName.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
