package de.aetherion.aethermobs.pet;

import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;

import java.util.EnumMap;
import java.util.Map;

public class PetDefinition {

    private final String id;
    private final String displayName;
    private final ItemCapability coreStat;
    private ItemCapability signatureStat;

    private final Map<Rarity, PetRarityConfig> rarityConfigs;

    /*
     * =========================================================
     * SPAWN TYPE
     * =========================================================
     *
     * Existing pets default to SURFACE.
     *
     * This keeps the current spawn behaviour completely intact.
     */

    private PetSpawnType spawnType;
    private PetHabitat habitat;
    private double spawnWeight;
    private int minWaterDepth;
    private int maxWaterDepth;
    private DungeonAura dungeonAura;
    private boolean randomCoreStat;
    private boolean shopExclusive;

    public enum DungeonAura {
        NONE,
        ALL,
        DAMAGE,
        BOW
    }

    public PetDefinition(
            String id,
            String displayName,
            ItemCapability coreStat
    ) {

        this.id = id;
        this.displayName = displayName;
        this.coreStat = coreStat;

        this.rarityConfigs =
                new EnumMap<>(
                        Rarity.class
                );

        this.spawnType =
                PetSpawnType.SURFACE;

        this.habitat =
                PetHabitat.ANY;

        this.spawnWeight =
                10.0;

        this.minWaterDepth =
                0;

        this.maxWaterDepth =
                0;

        this.dungeonAura =
                DungeonAura.NONE;

        this.randomCoreStat =
                false;

        this.shopExclusive =
                false;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemCapability getCoreStat() {
        return coreStat;
    }

    public ItemCapability getSignatureStat() {
        return signatureStat;
    }

    public void setSignatureStat(ItemCapability signatureStat) {
        this.signatureStat = signatureStat;
    }

    /*
     * =========================================================
     * SPAWN TYPE
     * =========================================================
     */

    public PetSpawnType getSpawnType() {
        return spawnType;
    }

    public void setSpawnType(
            PetSpawnType spawnType
    ) {

        this.spawnType =
                spawnType == null
                        ? PetSpawnType.SURFACE
                        : spawnType;
    }

    public PetHabitat getHabitat() {

        return habitat == null
                ? PetHabitat.ANY
                : habitat;
    }

    public void setHabitat(
            PetHabitat habitat
    ) {

        this.habitat =
                habitat == null
                        ? PetHabitat.ANY
                        : habitat;
    }

    public int getMinWaterDepth() {
        return minWaterDepth;
    }

    public int getMaxWaterDepth() {
        return maxWaterDepth;
    }

    public void setWaterDepth(
            int min,
            int max
    ) {

        this.minWaterDepth =
                Math.max(
                        0,
                        min
                );

        this.maxWaterDepth =
                Math.max(
                        this.minWaterDepth,
                        max
                );
    }

    public boolean isDeepAquatic() {

        return spawnType == PetSpawnType.AQUATIC
                && minWaterDepth > 0;
    }

    public double getSpawnWeight() {
        return spawnWeight;
    }

    public void setSpawnWeight(
            double spawnWeight
    ) {

        this.spawnWeight =
                Math.max(
                        0.0,
                        spawnWeight
                );
    }

    public DungeonAura getDungeonAura() {
        return dungeonAura == null
                ? DungeonAura.NONE
                : dungeonAura;
    }

    public void setDungeonAura(
            DungeonAura dungeonAura
    ) {
        this.dungeonAura =
                dungeonAura == null
                        ? DungeonAura.NONE
                        : dungeonAura;
        if (this.dungeonAura != DungeonAura.NONE) {
            this.spawnType = PetSpawnType.DUNGEON;
        }
    }

    public boolean isPercentBonus() {
        return getDungeonAura() != DungeonAura.NONE;
    }

    public boolean isDungeonPet() {
        return spawnType == PetSpawnType.DUNGEON
                || getDungeonAura() != DungeonAura.NONE;
    }

    public boolean rollsRandomCoreStat() {
        return randomCoreStat;
    }

    public void setRandomCoreStat(boolean randomCoreStat) {
        this.randomCoreStat = randomCoreStat;
    }

    public boolean isShopExclusive() {
        return shopExclusive;
    }

    public void setShopExclusive(boolean shopExclusive) {
        this.shopExclusive = shopExclusive;
        if (shopExclusive) {
            this.spawnWeight = 0.0;
        }
    }

    /*
     * =========================================================
     * RARITY
     * =========================================================
     */

    public void addRarityConfig(
            PetRarityConfig config
    ) {

        rarityConfigs.put(
                config.getRarity(),
                config
        );
    }

    public PetRarityConfig getRarityConfig(
            Rarity rarity
    ) {

        return rarityConfigs.get(
                rarity
        );
    }

    public Map<Rarity, PetRarityConfig> getRarityConfigs() {
        return rarityConfigs;
    }

    public boolean hasRarity(
            Rarity rarity
    ) {

        return rarityConfigs.containsKey(
                rarity
        );
    }
}