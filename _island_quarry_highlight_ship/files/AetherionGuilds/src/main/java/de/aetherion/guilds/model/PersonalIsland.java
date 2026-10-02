package de.aetherion.guilds.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PersonalIsland {

    private final UUID ownerId;
    private int plot;
    private IslandBiome biome = IslandBiome.PLAINS;
    private boolean islandBuilt;
    private int islandLevel = 1;
    private final List<QuarryMinion> minions = new ArrayList<>();
    /** Authored starter id (island highlight), or null for a classic biome pad. */
    private String starter;
    /** Owned land parcels (16x16, see LandService), packed x/z keys. */
    private final Set<Long> parcels = ConcurrentHashMap.newKeySet();

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

    public String starter() {
        return starter;
    }

    public void setStarter(String starter) {
        this.starter = starter == null || starter.isBlank() ? null : starter;
    }

    public Set<Long> parcels() {
        return parcels;
    }
}
