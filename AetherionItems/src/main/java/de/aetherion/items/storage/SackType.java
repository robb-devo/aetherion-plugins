package de.aetherion.items.storage;

import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.economy.QuarryItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.inventory.ItemStack;

public enum SackType {

    RESOURCE(
            "resource_sack",
            "§aResource Sack",
            "§7Holds farmed, mined, and compressed resources."
    ),
    BOOSTER(
            "booster_sack",
            "§dBooster Sack",
            "§7Stores up to 64 of each booster type."
    );

    private final String itemId;
    private final String displayName;
    private final String loreLine;

    SackType(String itemId, String displayName, String loreLine) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.loreLine = loreLine;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public String loreLine() {
        return loreLine;
    }

    public boolean accepts(ItemStack stack, ItemManager items) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (SackItems.isSack(stack)) {
            return false;
        }
        return switch (this) {
            case RESOURCE -> isResource(stack, items);
            case BOOSTER -> items != null && items.getBoosterType(stack) != null;
        };
    }

    private static boolean isResource(ItemStack stack, ItemManager items) {
        if (CompressedResource.fromDrop(stack.getType()) != null) {
            return true;
        }
        if (items == null) {
            return false;
        }
        String id = items.getItemId(stack);
        return CompressedResource.byItemId(id) != null
                || QuarryItems.CORE_ID.equals(id)
                || QuarryItems.SHARD_ID.equals(id)
                || QuarryItems.COMPRESSOR_ID.equals(id)
                || QuarryItems.COMPACTOR_ID.equals(id)
                || (id != null && id.endsWith("_quarry"));
    }

    public static SackType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
