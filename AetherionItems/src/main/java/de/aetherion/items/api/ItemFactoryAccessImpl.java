package de.aetherion.items.api;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.economy.Signatures;
import de.aetherion.items.item.CustomItem;

import org.bukkit.entity.Player;
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
    public int boneGrafts(ItemStack item) {
        de.aetherion.items.manager.ItemManager items = manager();
        return items == null || !items.isAetherionItem(item) ? 0 : items.getItemStats(item).getBoneGrafts();
    }

    /** Heartforge: one more booster socket; boosters already socketed stay where they are. */
    @Override
    public boolean addBoneGraft(ItemStack item) {
        de.aetherion.items.manager.ItemManager items = manager();
        if (items == null || !items.isAetherionItem(item) || items.getBoosterType(item) != null) {
            return false;
        }
        de.aetherion.items.model.ItemStats stats = items.getItemStats(item);
        if (stats.getBoneGrafts() >= de.aetherion.items.core.BoosterLimits.MAX_GRAFTS) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        stats.setBoneGrafts(stats.getBoneGrafts() + 1);
        items.saveItemStats(meta, stats);
        item.setItemMeta(meta);
        items.refreshItemLore(item);
        return true;
    }

    private static de.aetherion.items.manager.ItemManager manager() {
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        return plugin == null ? null : plugin.getItemManager();
    }

    @Override
    public ItemStack dungeonWeaponSchematic(int floor) {
        return customItem == null ? null : customItem.createDungeonWeaponSchematic(floor);
    }

    @Override
    public ItemStack stampSignature(ItemStack item, String signatureId) {
        return Signatures.stamp(item, signatureId);
    }

    @Override
    public void noteSignature(Player player, String signatureId) {
        String codexId = Signatures.codexId(signatureId);
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        if (player == null || codexId == null || plugin == null || plugin.getCodex() == null) {
            return;
        }
        plugin.getCodex().addBlock(player, codexId);
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
