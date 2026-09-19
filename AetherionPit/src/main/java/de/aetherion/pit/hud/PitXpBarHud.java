package de.aetherion.pit.hud;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.data.PitDataStore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Puts Pit level into the vanilla XP bar (level number + progress to next). */
public final class PitXpBarHud {

    private final AetherionPit plugin;

    public PitXpBarHud(AetherionPit plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 40L);
    }

    public void sync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PitDataStore.Stats stats = plugin.data().of(player);
        int need = Math.max(1, plugin.levels().xpForNext(stats.level()));
        float progress = Math.min(0.999f, Math.max(0f, (float) stats.xp() / (float) need));
        player.setLevel(Math.max(0, stats.level()));
        player.setExp(progress);
    }

    private void tickAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.safeZone().isPitWorld(player.getWorld())) {
                sync(player);
            }
        }
    }
}
