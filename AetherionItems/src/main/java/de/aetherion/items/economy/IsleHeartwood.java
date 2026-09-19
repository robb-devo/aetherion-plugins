package de.aetherion.items.economy;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemStats;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Rare forage-isle-only heartwoods — hooks for rituals / quests / crafts later.
 * IDs: {@code isle_heartwood_<wood>}. Overworld woods only for now.
 */
public enum IsleHeartwood {

    OAK(Material.OAK_LOG, "oak", "Elderbark Heartwood", "§6", 200),
    BIRCH(Material.BIRCH_LOG, "birch", "Pale Heartwood", "§f", 210),
    SPRUCE(Material.SPRUCE_LOG, "spruce", "Frostpitch Heartwood", "§b", 220),
    JUNGLE(Material.JUNGLE_LOG, "jungle", "Canopy Amberwood", "§a", 230),
    ACACIA(Material.ACACIA_LOG, "acacia", "Savanna Thornwood", "§6", 240),
    DARK_OAK(Material.DARK_OAK_LOG, "dark_oak", "Thicket Nightbark", "§8", 250),
    MANGROVE(Material.MANGROVE_LOG, "mangrove", "Brine Rootwood", "§2", 260),
    CHERRY(Material.CHERRY_LOG, "cherry", "Blossom Heartwood", "§d", 270),
    BAMBOO(Material.BAMBOO_BLOCK, "bamboo", "Jade Culm Heartwood", "§a", 280);

    private final Material icon;
    private final String woodKey;
    private final String display;
    private final String color;
    private final int order;

    IsleHeartwood(Material icon, String woodKey, String display, String color, int order) {
        this.icon = icon;
        this.woodKey = woodKey;
        this.display = display;
        this.color = color;
        this.order = order;
    }

    public String itemId() {
        return "isle_heartwood_" + woodKey;
    }

    public String woodKey() {
        return woodKey;
    }

    public Material icon() {
        return icon;
    }

    public int order() {
        return order;
    }

    public String displayName() {
        return display;
    }

    public String color() {
        return color;
    }

    public String coloredName() {
        return color + display;
    }

    public static List<IsleHeartwood> contentOrdered() {
        List<IsleHeartwood> list = new ArrayList<>(List.of(values()));
        list.sort(Comparator.comparingInt(IsleHeartwood::order));
        return list;
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + "§l" + display);
            meta.setLore(List.of(
                    "§5✦ §dRARE §8· §7Forage Isle",
                    "",
                    "§7A rare heartwood that only grows",
                    "§7on Eldervale's forage isle.",
                    "§8Offer pairs at the Grove table.",
                    "",
                    "§8Aetherion Foraging"
            ));
            meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, itemId());
            ItemManager manager = AetherionItems.getInstance() == null
                    ? null : AetherionItems.getInstance().getItemManager();
            if (manager != null) {
                manager.applyItemData(meta, Rarity.RARE, new ItemStats());
            }
            de.aetherion.items.item.ItemPresentation.polish(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static IsleHeartwood fromWoodDrop(Material material) {
        if (material == null) {
            return null;
        }
        CompressedResource resource = CompressedResource.fromDrop(material);
        if (resource == null || !resource.isForagingDrop() || !resource.contentEnabled()) {
            return null;
        }
        return switch (resource) {
            case OAK_LOG -> OAK;
            case BIRCH_LOG -> BIRCH;
            case SPRUCE_LOG -> SPRUCE;
            case JUNGLE_LOG -> JUNGLE;
            case ACACIA_LOG -> ACACIA;
            case DARK_OAK_LOG -> DARK_OAK;
            case MANGROVE_LOG -> MANGROVE;
            case CHERRY_LOG -> CHERRY;
            case BAMBOO_BLOCK -> BAMBOO;
            default -> null;
        };
    }

    public static IsleHeartwood fromId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        for (IsleHeartwood wood : values()) {
            if (wood.itemId().equals(id)) {
                return wood;
            }
        }
        return null;
    }

    public static ItemStack fromItemId(String itemId) {
        IsleHeartwood wood = fromId(itemId);
        return wood == null ? null : wood.create();
    }
}
