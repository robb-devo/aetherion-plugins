package de.aetherion.items.skill;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mirrors Aetherion account level onto the vanilla XP bar (number + fill).
 * Vanilla orb XP is cancelled so the bar stays honest.
 */
public final class AetherionXpBarSync implements Listener, Runnable {

    private final JavaPlugin plugin;
    private final SkillService skills;

    public AetherionXpBarSync(JavaPlugin plugin, SkillService skills) {
        this.plugin = plugin;
        this.skills = skills;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 40L, 40L);
    }

    public void sync(Player player) {
        if (player == null || !player.isOnline() || skills == null) {
            return;
        }
        long xp = skills.accountXp(player);
        int level = AetherionLevel.of(xp);
        float progress;
        if (level >= AetherionLevel.MAX_LEVEL) {
            progress = 1.0f;
        } else {
            long into = AetherionLevel.intoLevel(xp);
            long need = AetherionLevel.xpToNext(level);
            progress = need <= 0L ? 0f : Math.min(0.999f, (float) into / (float) need);
        }
        player.setLevel(level);
        player.setExp(progress);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sync(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sync(event.getPlayer()), 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaXp(PlayerExpChangeEvent event) {
        // Keep the bar as Aetherion progress — orbs would fight the sync.
        event.setAmount(0);
        plugin.getServer().getScheduler().runTask(plugin, () -> sync(event.getPlayer()));
    }
}
