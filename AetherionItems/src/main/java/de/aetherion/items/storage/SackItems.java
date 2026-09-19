package de.aetherion.items.storage;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public final class SackItems {

    private SackItems() {
    }

    public static ItemStack create(SackType type) {
        return create(type, UUID.randomUUID());
    }

    public static ItemStack create(SackType type, UUID sackId) {
        if (type == null) {
            return null;
        }
        ItemStack item = new ItemStack(type == SackType.BOOSTER ? Material.ENDER_CHEST : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(type.displayName());
        meta.setLore(List.of(
                type.loreLine(),
                type == SackType.BOOSTER ? "§8Insert All · click a type to take 1." : "§8Right-click to open.",
                "§8Crafted storage — keeps inventory clean."
        ));
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, type.itemId());
        meta.getPersistentDataContainer().set(ItemKeys.sackId(), PersistentDataType.STRING, sackId.toString());
        meta.getPersistentDataContainer().set(ItemKeys.sackType(), PersistentDataType.STRING, type.name());
        ItemManager manager = AetherionItems.getInstance() == null ? null : AetherionItems.getInstance().getItemManager();
        if (manager != null) {
            manager.applyItemData(meta, Rarity.UNCOMMON, new ItemStats());
        }
        de.aetherion.items.item.ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSack(ItemStack item) {
        return typeOf(item) != null;
    }

    public static SackType typeOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String typed = meta.getPersistentDataContainer().get(ItemKeys.sackType(), PersistentDataType.STRING);
        SackType type = SackType.fromId(typed);
        if (type != null) {
            return type;
        }
        String id = meta.getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        if ("resource_sack".equals(id)) {
            return SackType.RESOURCE;
        }
        if ("booster_sack".equals(id)) {
            return SackType.BOOSTER;
        }
        return null;
    }

    public static UUID idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(ItemKeys.sackId(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ItemStack ensureId(ItemStack item) {
        if (!isSack(item)) {
            return item;
        }
        if (idOf(item) != null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(ItemKeys.sackId(), PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }
}
