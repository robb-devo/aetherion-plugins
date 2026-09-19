package de.aetherion.pit.service;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.data.PitDataStore;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class PitLevelService {

    private final AetherionPit plugin;
    private final PitDataStore data;

    public PitLevelService(AetherionPit plugin, PitDataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    public void rewardKill(Player killer) {
        if (killer == null) {
            return;
        }
        int xp = plugin.getConfig().getInt("levels.kill-xp", 12);
        int gold = plugin.getConfig().getInt("economy.kill-gold", 8);
        addXp(killer, xp);
        addGold(killer, gold);
        killer.sendMessage("§c+§f" + xp + " XP §8· §6+" + gold + "g");
        plugin.xpHud().sync(killer);
    }

    public void onDeath(Player victim) {
        if (victim == null) {
            return;
        }
        int loss = plugin.getConfig().getInt("levels.death-xp-loss", 4);
        PitDataStore.Mutable m = data.mutable(victim.getUniqueId());
        m.xp = Math.max(0, m.xp - loss);
        plugin.xpHud().sync(victim);
    }

    public void addGold(Player player, int amount) {
        if (amount == 0) {
            return;
        }
        PitDataStore.Mutable m = data.mutable(player.getUniqueId());
        m.gold = Math.max(0, m.gold + amount);
        plugin.xpHud().sync(player);
    }

    public boolean spendGold(Player player, int amount) {
        PitDataStore.Mutable m = data.mutable(player.getUniqueId());
        if (m.gold < amount) {
            return false;
        }
        m.gold -= amount;
        plugin.xpHud().sync(player);
        return true;
    }

    public void addXp(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        PitDataStore.Mutable m = data.mutable(player.getUniqueId());
        int max = plugin.getConfig().getInt("levels.max-level", 50);
        m.xp += amount;
        int needed = xpForNext(m.level);
        while (m.level < max && m.xp >= needed) {
            m.xp -= needed;
            m.level++;
            needed = xpForNext(m.level);
            player.sendMessage("§6★ Pit Level §f" + m.level + "§6!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }
        plugin.xpHud().sync(player);
    }

    public int xpForNext(int level) {
        double base = plugin.getConfig().getDouble("levels.base-xp", 40);
        return (int) Math.max(20, Math.round(base * Math.pow(Math.max(1, level), 1.35)));
    }
}
