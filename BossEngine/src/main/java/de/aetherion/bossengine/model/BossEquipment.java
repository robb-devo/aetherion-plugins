package de.aetherion.bossengine.model;

import org.bukkit.Color;
import org.bukkit.Material;

public class BossEquipment {

    private final Material helmet;
    private final Material chestplate;
    private final Material leggings;
    private final Material boots;
    private final Material mainHand;
    private final Material offHand;
    private final float dropChance;
    private final Color leatherColor;

    public BossEquipment(
            Material helmet,
            Material chestplate,
            Material leggings,
            Material boots,
            Material mainHand,
            Material offHand,
            float dropChance,
            Color leatherColor
    ) {
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.dropChance = dropChance;
        this.leatherColor = leatherColor;
    }

    public static BossEquipment empty() {
        return new BossEquipment(
                Material.AIR,
                Material.AIR,
                Material.AIR,
                Material.AIR,
                Material.AIR,
                Material.AIR,
                0.0f,
                null
        );
    }

    public Material getHelmet() {
        return helmet;
    }

    public Material getChestplate() {
        return chestplate;
    }

    public Material getLeggings() {
        return leggings;
    }

    public Material getBoots() {
        return boots;
    }

    public Material getMainHand() {
        return mainHand;
    }

    public Material getOffHand() {
        return offHand;
    }

    public float getDropChance() {
        return dropChance;
    }

    public Color getLeatherColor() {
        return leatherColor;
    }
}
