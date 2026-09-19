package de.aetherion.guilds.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Guild {

    public static final int BANK_MAX_SLOTS = 45;

    private final UUID id;
    private String name;
    private int plot;
    private boolean islandBuilt;
    private int islandLevel = 1;
    private int bankLevel;
    private long bankCoins;
    private final ItemStack[] bank = new ItemStack[BANK_MAX_SLOTS];
    private final Map<UUID, GuildRank> members = new ConcurrentHashMap<>();
    private final Map<UUID, Long> invites = new ConcurrentHashMap<>();
    private final List<QuarryMinion> minions = new ArrayList<>();

    public Guild(UUID id, String name, int plot) {
        this.id = id;
        this.name = name;
        this.plot = plot;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int plot() {
        return plot;
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

    public long bankCoins() {
        return Math.max(0L, bankCoins);
    }

    public void setBankCoins(long bankCoins) {
        this.bankCoins = Math.max(0L, bankCoins);
    }

    public int bankLevel() {
        return BankTiers.clamp(bankLevel);
    }

    public void setBankLevel(int bankLevel) {
        this.bankLevel = BankTiers.clamp(bankLevel);
    }

    public boolean canUpgradeBank() {
        return bankLevel() < BankTiers.MAX_LEVEL;
    }

    public long coinCap() {
        return BankTiers.coinCap(bankLevel());
    }

    public int bankSlots() {
        return BankTiers.itemSlots(bankLevel());
    }

    public ItemStack[] bank() {
        return bank;
    }

    public Map<UUID, GuildRank> members() {
        return members;
    }

    public Map<UUID, Long> invites() {
        return invites;
    }

    public List<QuarryMinion> minions() {
        return minions;
    }

    public GuildRank rank(UUID playerId) {
        return members.get(playerId);
    }

    public UUID leaderId() {
        for (Map.Entry<UUID, GuildRank> entry : members.entrySet()) {
            if (entry.getValue() == GuildRank.LEADER) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int onlineMembers() {
        int online = 0;
        for (UUID id : members.keySet()) {
            var player = org.bukkit.Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                online++;
            }
        }
        return online;
    }
}
