package de.aetherion.items.economy;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class QuarryItems {

    public static final String CORE_ID = "quarry_core";
    public static final String SHARD_ID = "quarry_core_shard";
    public static final String COMPRESSOR_ID = "quarry_compressor";
    public static final String COMPACTOR_ID = "quarry_compactor";

    private QuarryItems() {
    }

    public static NamespacedKey quarryTypeKey() {
        return new NamespacedKey("aetherionguilds", "quarry_item");
    }

    public static ItemStack shard() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, SHARD_ID, Rarity.UNCOMMON);
            meta.setDisplayName("§dQuarry Core Shard");
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                    "§7✦ §aUNCOMMON",
                    "",
                    "§7A splinter of a Quarry Core.",
                    "§7Four shards craft one Core.",
                    "",
                    "§820% drop from world bosses."
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack core() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, CORE_ID, Rarity.EPIC);
            meta.setDisplayName("§dQuarry Core");
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                    "§7✦ §5EPIC",
                    "",
                    "§7The heart of an Island Quarry.",
                    "§7Craft with 8 Compressed items",
                    "§7around this core.",
                    "",
                    "§8Made from 4 Core Shards."
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack compressor() {
        ItemStack item = new ItemStack(Material.PISTON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, COMPRESSOR_ID, Rarity.RARE);
            meta.setDisplayName("§6Quarry Mill");
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    "§7Install on an island quarry.",
                    "§7Crafts Compressed while it runs.",
                    "§7Leftover under 128 stays raw.",
                    "",
                    "§8Auto-crafts, then collect."
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack compactor() {
        ItemStack item = new ItemStack(Material.BLAST_FURNACE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, COMPACTOR_ID, Rarity.EPIC);
            meta.setDisplayName("§bQuarry Forge");
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                    "§7✦ §5EPIC",
                    "",
                    "§7Install on an island quarry.",
                    "§7Crafts Compacted while it runs.",
                    "§7If not enough for Compacted,",
                    "§7leftover becomes Compressed.",
                    "",
                    "§8Auto-crafts, then collect."
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack quarry(CompressedResource resource) {
        ItemStack item = resource.compactedHash() != null && !resource.compactedHash().isBlank()
                ? TexturedHeads.hashed(resource.compactedHash())
                : new ItemStack(resource.compactedBlock());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            tag(meta, resource.quarryItemId(), Rarity.RARE);
            meta.getPersistentDataContainer().set(quarryTypeKey(), PersistentDataType.STRING, resource.quarryTypeId());
            meta.setDisplayName("§6" + resource.quarryDisplay());
            meta.setEnchantmentGlintOverride(true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                    "§7✦ §bRARE",
                    "",
                    "§7Place on your private or guild island.",
                    "§7Produces §f" + resource.prettyName(),
                    "§7while the server is online.",
                    resource.quarryBlurb(),
                    "",
                    "§8Click it to collect or upgrade."
            ));
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack fromId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return switch (itemId) {
            case CORE_ID -> core();
            case SHARD_ID -> shard();
            case COMPRESSOR_ID -> compressor();
            case COMPACTOR_ID -> compactor();
            default -> {
                for (CompressedResource resource : CompressedResource.values()) {
                    if (resource.hasQuarry()
                            && (resource.quarryItemId().equals(itemId) || resource.quarryTypeId().equals(itemId))) {
                        yield quarry(resource);
                    }
                }
                yield null;
            }
        };
    }

    private static void tag(ItemMeta meta, String itemId, Rarity rarity) {
        ItemManager manager = AetherionItems.getInstance().getItemManager();
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, itemId);
        manager.applyItemData(meta, rarity, new ItemStats());
    }
}
