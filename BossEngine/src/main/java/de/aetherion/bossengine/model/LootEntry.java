package de.aetherion.bossengine.model;

import org.bukkit.Material;

public class LootEntry {

    private final LootSource source;
    private final String itemId;
    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final double chance;
    private final boolean scaleWithDamage;

    public LootEntry(
            LootSource source,
            String itemId,
            Material material,
            int minAmount,
            int maxAmount,
            double chance,
            boolean scaleWithDamage
    ) {
        this.source = source == null ? LootSource.VANILLA : source;
        this.itemId = itemId;
        this.material = material;
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.chance = Math.max(0, Math.min(1.0, chance));
        this.scaleWithDamage = scaleWithDamage;
    }

    public LootSource getSource() {
        return source;
    }

    public String getItemId() {
        return itemId;
    }

    public Material getMaterial() {
        return material;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public double getChance() {
        return chance;
    }

    public boolean isScaleWithDamage() {
        return scaleWithDamage;
    }
}
