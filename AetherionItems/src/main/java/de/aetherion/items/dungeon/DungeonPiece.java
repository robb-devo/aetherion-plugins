package de.aetherion.items.dungeon;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;

public enum DungeonPiece {

    HELMET("helmet", "Helmet"),
    CHESTPLATE("chestplate", "Chestplate"),
    LEGGINGS("leggings", "Leggings"),
    BOOTS("boots", "Boots");

    private final String id;
    private final String display;

    DungeonPiece(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public Material leather() {
        return switch (this) {
            case HELMET -> Material.LEATHER_HELMET;
            case CHESTPLATE -> Material.LEATHER_CHESTPLATE;
            case LEGGINGS -> Material.LEATHER_LEGGINGS;
            case BOOTS -> Material.LEATHER_BOOTS;
        };
    }

    public Material iron() {
        return switch (this) {
            case HELMET -> Material.IRON_HELMET;
            case CHESTPLATE -> Material.IRON_CHESTPLATE;
            case LEGGINGS -> Material.IRON_LEGGINGS;
            case BOOTS -> Material.IRON_BOOTS;
        };
    }

    public Material chainmail() {
        return switch (this) {
            case HELMET -> Material.CHAINMAIL_HELMET;
            case CHESTPLATE -> Material.CHAINMAIL_CHESTPLATE;
            case LEGGINGS -> Material.CHAINMAIL_LEGGINGS;
            case BOOTS -> Material.CHAINMAIL_BOOTS;
        };
    }

    public Material gold() {
        return switch (this) {
            case HELMET -> Material.GOLDEN_HELMET;
            case CHESTPLATE -> Material.GOLDEN_CHESTPLATE;
            case LEGGINGS -> Material.GOLDEN_LEGGINGS;
            case BOOTS -> Material.GOLDEN_BOOTS;
        };
    }

    public static DungeonPiece fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        if (id.endsWith("_helmet")) {
            return HELMET;
        }
        if (id.endsWith("_chestplate")) {
            return CHESTPLATE;
        }
        if (id.endsWith("_leggings")) {
            return LEGGINGS;
        }
        if (id.endsWith("_boots")) {
            return BOOTS;
        }
        return null;
    }

    public ItemStack worn(PlayerInventory inventory) {
        if (inventory == null) {
            return null;
        }
        return switch (this) {
            case HELMET -> inventory.getHelmet();
            case CHESTPLATE -> inventory.getChestplate();
            case LEGGINGS -> inventory.getLeggings();
            case BOOTS -> inventory.getBoots();
        };
    }

    public void wear(PlayerInventory inventory, ItemStack item) {
        if (inventory == null) {
            return;
        }
        switch (this) {
            case HELMET -> inventory.setHelmet(item);
            case CHESTPLATE -> inventory.setChestplate(item);
            case LEGGINGS -> inventory.setLeggings(item);
            case BOOTS -> inventory.setBoots(item);
        }
    }
}
