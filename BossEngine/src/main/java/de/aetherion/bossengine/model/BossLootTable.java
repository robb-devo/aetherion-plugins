package de.aetherion.bossengine.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BossLootTable {

    private final boolean damageBased;
    private final double participationThresholdPercent;
    private final List<LootEntry> killerBonus;
    private final List<LootEntry> shared;
    private final List<LootEntry> perDamager;
    private final List<LootEntry> topDamagerLoot;
    private final int topDamagerCount;
    private final int experience;
    private final Map<Integer, List<LootEntry>> rankLoot;

    public BossLootTable(
            boolean damageBased,
            double participationThresholdPercent,
            List<LootEntry> killerBonus,
            List<LootEntry> shared,
            List<LootEntry> perDamager,
            List<LootEntry> topDamagerLoot,
            int topDamagerCount,
            int experience,
            Map<Integer, List<LootEntry>> rankLoot
    ) {
        this.damageBased = damageBased;
        this.participationThresholdPercent = participationThresholdPercent;
        this.killerBonus = List.copyOf(killerBonus == null ? List.of() : killerBonus);
        this.shared = List.copyOf(shared == null ? List.of() : shared);
        this.perDamager = List.copyOf(perDamager == null ? List.of() : perDamager);
        this.topDamagerLoot = List.copyOf(topDamagerLoot == null ? List.of() : topDamagerLoot);
        this.topDamagerCount = Math.max(0, topDamagerCount);
        this.experience = Math.max(0, experience);
        this.rankLoot = Map.copyOf(rankLoot == null ? Map.of() : rankLoot);
    }

    public static BossLootTable empty() {
        return new BossLootTable(true, 0, List.of(), List.of(), List.of(), List.of(), 0, 0, Map.of());
    }

    public boolean isDamageBased() {
        return damageBased;
    }

    public double getParticipationThresholdPercent() {
        return participationThresholdPercent;
    }

    public List<LootEntry> getKillerBonus() {
        return killerBonus;
    }

    public List<LootEntry> getShared() {
        return shared;
    }

    public List<LootEntry> getPerDamager() {
        return perDamager;
    }

    public List<LootEntry> getTopDamagerLoot() {
        return topDamagerLoot;
    }

    public int getTopDamagerCount() {
        return topDamagerCount;
    }

    public int getExperience() {
        return experience;
    }

    public List<LootEntry> getRankLoot(int rank) {
        return rankLoot.getOrDefault(rank, List.of());
    }

    public List<LootEntry> allEntries() {
        List<LootEntry> all = new ArrayList<>();
        all.addAll(killerBonus);
        all.addAll(shared);
        all.addAll(perDamager);
        all.addAll(topDamagerLoot);
        rankLoot.values().forEach(all::addAll);
        return all;
    }
}
