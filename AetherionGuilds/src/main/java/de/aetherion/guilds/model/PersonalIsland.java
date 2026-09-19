package de.aetherion.guilds.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PersonalIsland {

    private final UUID ownerId;
    private int plot;
    private IslandBiome biome = IslandBiome.PLAINS;
    private boolean islandBuilt;
    private int islandLevel = 1;
    private final List<QuarryMinion> minions = new ArrayList<>();

    public PersonalIsland(UUID ownerId, int plot, IslandBiome biome) {
        this.ownerId = ownerId;
        this.plot = plot;
        this.biome = biome == null ? IslandBiome.PLAINS : biome;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int plot() {
        return plot;
    }

    public void setPlot(int plot) {
        this.plot = Math.max(0, plot);
    }

    public IslandBiome biome() {
        return biome == null ? IslandBiome.PLAINS : biome;
    }

    public void setBiome(IslandBiome biome) {
        this.biome = biome == null ? IslandBiome.PLAINS : biome;
    }

    public boolean islandBuilt() {
        return islandBuilt;
    }

    public void setIslandBuilt(boolean islandBuilt) {
        this.islandBuilt = islandBuilt;
    }

    public int islandLevel() {
        return IslandTiers.clamp(islandLevel);
    }

    public void setIslandLevel(int islandLevel) {
        this.islandLevel = IslandTiers.clamp(islandLevel);
    }

    public boolean canUpgradeIsland() {
        return islandLevel() < IslandTiers.MAX_LEVEL;
    }

    public List<QuarryMinion> minions() {
        return minions;
    }
}
